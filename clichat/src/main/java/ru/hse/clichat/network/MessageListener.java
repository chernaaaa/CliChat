package ru.hse.clichat.network;

public interface MessageListener {
    void onMessageReceived(String sender, long timestamp, String text);
    void onSystemMessage(String text);
}
