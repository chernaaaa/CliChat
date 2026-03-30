package ru.hse.clichat.cli;

import ru.hse.clichat.network.GrpcChatNode;
import ru.hse.clichat.network.MessageListener;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class ConsoleInterface implements MessageListener {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private GrpcChatNode chatNode;
    private final Scanner scanner;
    private volatile boolean isRunning = true;

    public ConsoleInterface() {
        this.scanner = new Scanner(System.in);
    }

    public void setChatNode(GrpcChatNode chatNode) {
        this.chatNode = chatNode;
    }

    public void startReading() {
        Thread readerThread = new Thread(() -> {
            while (isRunning && scanner.hasNextLine()) {
                String input = scanner.nextLine();
                if ("/exit".equalsIgnoreCase(input.trim())) {
                    System.out.println("Выход из чата...");
                    if (chatNode != null) {
                        chatNode.disconnect();
                    }
                    isRunning = false;
                    System.exit(0);
                    break;
                }
                if (chatNode != null && !input.isBlank()) {
                    chatNode.sendMessage(input);
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
    }

    @Override
    public void onMessageReceived(String sender, long timestamp, String text) {
        String formattedTime = TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp));
        System.out.printf("[%s] %s: %s%n", formattedTime, sender, text);
    }

    @Override
    public void onSystemMessage(String text) {
        System.out.println(">>> [СИСТЕМА]: " + text);
    }
}
