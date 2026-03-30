package ru.hse.clichat.network;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import ru.hse.clichat.grpc.ChatMessage;
import ru.hse.clichat.grpc.ChatServiceGrpc;

import java.io.IOException;

public class ChatServer implements GrpcChatNode {
    private final int port;
    private final String username;
    private final MessageListener listener;
    private Server server;
    private StreamObserver<ChatMessage> clientObserver;

    public ChatServer(int port, String username, MessageListener listener) {
        this.port = port;
        this.username = username;
        this.listener = listener;
    }

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
        if (clientObserver != null) {
            ChatMessage msg = ChatMessage.newBuilder()
                    .setSenderName(username)
                    .setTimestamp(System.currentTimeMillis())
                    .setText(text)
                    .build();
            clientObserver.onNext(msg);
        } else {
            listener.onSystemMessage("Нет активного подключения для отправки сообщения.");
        }
    }

    @Override
    public void disconnect() {
        if (clientObserver != null) {
            try {
                clientObserver.onCompleted();
            } catch (Exception ignored) {}
        }
        if (server != null) {
            server.shutdown();
        }
    }

    private class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
        @Override
        public StreamObserver<ChatMessage> chatStream(StreamObserver<ChatMessage> responseObserver) {
            clientObserver = responseObserver;
            listener.onSystemMessage("Клиент подключился!");

            return new StreamObserver<>() {
                @Override
                public void onNext(ChatMessage value) {
                    listener.onMessageReceived(value.getSenderName(), value.getTimestamp(), value.getText());
                }

                @Override
                public void onError(Throwable t) {
                    listener.onSystemMessage("Связь с клиентом разорвана.");
                    clientObserver = null;
                }

                @Override
                public void onCompleted() {
                    listener.onSystemMessage("Клиент отключился.");
                    clientObserver = null;
                }
            };
        }
    }
}
