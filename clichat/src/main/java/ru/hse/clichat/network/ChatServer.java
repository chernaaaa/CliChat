package ru.hse.clichat.network;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import ru.hse.clichat.grpc.ChatMessage;
import ru.hse.clichat.grpc.ChatServiceGrpc;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Серверная часть P2P чата на основе gRPC.
 * Принимает одно входящее подключение и обменивается сообщениями с клиентом.
 * Попытки множественных подключений автоматически отклоняются.
 * 
 * <p>Thread-safe: использует AtomicReference для безопасного доступа
 * из разных потоков (UI и gRPC threads).
 */
public class ChatServer implements GrpcChatNode {
    private final int port;
    private final String username;
    private final MessageListener listener;
    private Server server;
    private final AtomicReference<StreamObserver<ChatMessage>> clientObserver = new AtomicReference<>();

    /**
     * Создает серверный узел чата.
     *
     * @param port порт для прослушивания входящих подключений
     * @param username имя пользователя (отправителя сообщений)
     * @param listener слушатель для обработки входящих сообщений
     */
    public ChatServer(int port, String username, MessageListener listener) {
        this.port = port;
        this.username = username;
        this.listener = listener;
    }

    /**
     * Запускает gRPC сервер и начинает прослушивание входящих подключений.
     * Регистрирует shutdown hook для корректного завершения при выходе из приложения.
     *
     * @throws IOException если не удалось запустить сервер на указанном порту
     */
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new ChatServiceImpl())
                .build()
                .start();
        listener.onSystemMessage("Сервер запущен на порту " + port + ". Ожидание подключения...");
        
        Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect));
    }

    @Override
    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        StreamObserver<ChatMessage> observer = clientObserver.get();
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
        StreamObserver<ChatMessage> observer = clientObserver.getAndSet(null);
        if (observer != null) {
            try {
                observer.onCompleted();
            } catch (Exception ignored) {}
        }
        if (server != null) {
            try {
                server.shutdown();
                server.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                server.shutdownNow();
            }
        }
    }

    private class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
        @Override
        public StreamObserver<ChatMessage> chatStream(StreamObserver<ChatMessage> responseObserver) {
            if (!clientObserver.compareAndSet(null, responseObserver)) {
                listener.onSystemMessage("ВНИМАНИЕ: Попытка второго подключения отклонена!");
                responseObserver.onError(new IllegalStateException("Сервер уже занят другим клиентом"));
                return new StreamObserver<>() {
                    @Override
                    public void onNext(ChatMessage value) {}
                    @Override
                    public void onError(Throwable t) {}
                    @Override
                    public void onCompleted() {}
                };
            }
            
            listener.onSystemMessage("Клиент подключился!");

            return new StreamObserver<>() {
                @Override
                public void onNext(ChatMessage value) {
                    listener.onMessageReceived(value.getSenderName(), value.getTimestamp(), value.getText());
                }

                @Override
                public void onError(Throwable t) {
                    listener.onSystemMessage("Связь с клиентом разорвана.");
                    clientObserver.set(null);
                }

                @Override
                public void onCompleted() {
                    listener.onSystemMessage("Клиент отключился.");
                    clientObserver.set(null);
                }
            };
        }
    }
}
