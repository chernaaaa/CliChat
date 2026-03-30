package ru.hse.clichat;

import ru.hse.clichat.cli.CommandLineArgs;
import ru.hse.clichat.cli.ConsoleInterface;
import ru.hse.clichat.network.ChatClient;
import ru.hse.clichat.network.ChatServer;

import java.io.IOException;

public class Main {
    private static final int DEFAULT_SERVER_PORT = 8080;

    public static void main(String[] args) {
        try {
            CommandLineArgs parsedArgs = CommandLineArgs.parse(args);
            ConsoleInterface consoleUi = new ConsoleInterface();

            if (parsedArgs.isServer()) {
                // Режим сервера
                ChatServer server = new ChatServer(DEFAULT_SERVER_PORT, parsedArgs.getUsername(), consoleUi);
                consoleUi.setChatNode(server);
                
                server.start();
                consoleUi.startReading();
                
                Thread.currentThread().join();
            } else {
                // Режим клиента
                ChatClient client = new ChatClient(
                        parsedArgs.getPeerHost(), 
                        parsedArgs.getPeerPort(), 
                        parsedArgs.getUsername(), 
                        consoleUi
                );
                consoleUi.setChatNode(client);
                
                client.connect();
                consoleUi.startReading();
                
                Thread.currentThread().join();
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Ошибка аргументов: " + e.getMessage());
            System.err.println("Использование:");
            System.err.println("  Как сервер: java -jar app.jar --username <Имя>");
            System.err.println("  Как клиент: java -jar app.jar --username <Имя> --peer <IP> --port <Порт>");
        } catch (IOException | InterruptedException e) {
            System.err.println("Критическая ошибка: " + e.getMessage());
        }
    }
}
