package ru.hse.clichat.network;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import ru.hse.clichat.grpc.ChatMessage;
import ru.hse.clichat.grpc.ChatServiceGrpc;

public class ChatClient implements GrpcChatNode {
    private final String host;
    private final int port;
    private final String username;
    private final MessageListener listener;
    
    private ManagedChannel channel;
    private StreamObserver<ChatMessage> requestObserver;

    public ChatClient(String host, int port, String username, MessageListener listener) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.listener = listener;
    }

    public void connect() {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        ChatServiceGrpc.ChatServiceStub stub = ChatServiceGrpc.newStub(channel);

        requestObserver = stub.chatStream(new StreamObserver<>() {
            @Override
            public void onNext(ChatMessage value) {
                listener.onMessageReceived(value.getSenderName(), value.getTimestamp(), value.getText());
            }

            @Override
            public void onError(Throwable t) {
                listener.onSystemMessage("Связь с сервером разорвана: " + t.getMessage());
                disconnect();
            }

            @Override
            public void onCompleted() {
                listener.onSystemMessage("Сервер завершил соединение.");
                disconnect();
            }
        });

        listener.onSystemMessage("Успешно подключено к " + host + ":" + port);
    }

    @Override
    public void sendMessage(String text) {
        if (requestObserver != null) {
            ChatMessage msg = ChatMessage.newBuilder()
                    .setSenderName(username)
                    .setTimestamp(System.currentTimeMillis())
                    .setText(text)
                    .build();
            requestObserver.onNext(msg);
        }
    }

    @Override
    public void disconnect() {
        if (requestObserver != null) {
            try {
                requestObserver.onCompleted();
            } catch (Exception ignored) {}
        }
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
}
