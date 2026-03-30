package ru.hse.clichat.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleInterfaceTest {

    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
    private final PrintStream standardOut = System.out;
    private ConsoleInterface consoleInterface;

    @BeforeEach
    public void setUp() {
        System.setOut(new PrintStream(outputStreamCaptor));
        consoleInterface = new ConsoleInterface();
    }

    @AfterEach
    public void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    void testOnMessageReceivedFormatting() {
        long timestamp = 1711800000000L; // 2024-03-30 12:00:00 UTC
        String sender = "Alice";
        String text = "Hello, world!";

        consoleInterface.onMessageReceived(sender, timestamp, text);

        String output = outputStreamCaptor.toString().trim();
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        String expectedTime = formatter.format(Instant.ofEpochMilli(timestamp));

        String expectedOutput = String.format("[%s] %s: %s", expectedTime, sender, text);
        assertTrue(output.contains(expectedOutput), 
                "Ожидалось: " + expectedOutput + ", но было: " + output);
    }

    @Test
    void testOnSystemMessageFormatting() {
        String sysMsg = "Сервер запущен";
        
        consoleInterface.onSystemMessage(sysMsg);
        
        String output = outputStreamCaptor.toString().trim();
        assertTrue(output.contains(">>> [СИСТЕМА]: " + sysMsg));
    }
}
