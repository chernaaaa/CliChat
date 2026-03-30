package ru.hse.clichat.network;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import ru.hse.clichat.grpc.ChatMessage;
import ru.hse.clichat.grpc.ChatServiceGrpc;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Клиентская часть P2P чата на основе gRPC.
 * Подключается к серверу и обменивается сообщениями через bidirectional stream.
 * 
 * <p>Использует timeout для установки соединения (10 секунд) и keep-alive
 * для автоматического обнаружения разорванных соединений.
 * 
 * <p>Thread-safe: использует AtomicReference для безопасного доступа
 * из разных потоков (UI и gRPC threads).
 */
public class ChatClient implements GrpcChatNode {
    private static final int CONNECTION_TIMEOUT_SECONDS = 10;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;
    
    private final String host;
    private final int port;
    private final String username;
    private final MessageListener listener;
    
    private ManagedChannel channel;
    private final AtomicReference<StreamObserver<ChatMessage>> requestObserver = new AtomicReference<>();

    /**
     * Создает клиентский узел чата.
     *
     * @param host адрес сервера для подключения
     * @param port порт сервера
     * @param username имя пользователя (отправителя сообщений)
     * @param listener слушатель для обработки входящих сообщений
     */
    public ChatClient(String host, int port, String username, MessageListener listener) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.listener = listener;
    }

    /**
     * Устанавливает соединение с сервером и открывает bidirectional stream.
     * Если сервер недоступен, выбросится исключение через 10 секунд (timeout).
     */
    public void connect() {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .build();

        ChatServiceGrpc.ChatServiceStub stub = ChatServiceGrpc.newStub(channel)
                .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        StreamObserver<ChatMessage> observer = stub.chatStream(new StreamObserver<>() {
            @Override
            public void onNext(ChatMessage value) {
                listener.onMessageReceived(value.getSenderName(), value.getTimestamp(), value.getText());
            }

            @Override
            public void onError(Throwable t) {
                listener.onSystemMessage("Связь с сервером разорвана: " + t.getMessage());
                requestObserver.set(null);
            }

            @Override
            public void onCompleted() {
                listener.onSystemMessage("Сервер завершил соединение.");
                requestObserver.set(null);
            }
        });
        
        requestObserver.set(observer);
        listener.onSystemMessage("Успешно подключено к " + host + ":" + port);
    }

    @Override
    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        StreamObserver<ChatMessage> observer = requestObserver.get();
        if (observer != null) {
            ChatMessage msg = ChatMessage.newBuilder()
                    .setSenderName(username)
                    .setTimestamp(System.currentTimeMillis())
                    .setText(text)
                    .build();
            observer.onNext(msg);
        } else {
            listener.onSystemMessage("Нет активного подключения для отправки сообщения.");
        }
    }

    @Override
    public void disconnect() {
        StreamObserver<ChatMessage> observer = requestObserver.getAndSet(null);
        if (observer != null) {
            try {
                observer.onCompleted();
            } catch (Exception ignored) {}
        }
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown();
                if (!channel.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                    channel.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
