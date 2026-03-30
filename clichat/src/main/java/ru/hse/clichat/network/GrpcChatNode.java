package ru.hse.clichat.network;

public interface GrpcChatNode {
    void sendMessage(String text);
    void disconnect();
}
