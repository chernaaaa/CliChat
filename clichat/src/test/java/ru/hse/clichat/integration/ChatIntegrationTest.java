package ru.hse.clichat.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.hse.clichat.network.ChatClient;
import ru.hse.clichat.network.ChatServer;
import ru.hse.clichat.network.MessageListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ChatIntegrationTest {

    private static final int TEST_PORT = 9999;
    private static final String SERVER_USERNAME = "ServerUser";
    private static final String CLIENT_USERNAME = "ClientUser";
    
    private ChatServer server;
    private ChatClient client;
    private TestMessageListener serverListener;
    private TestMessageListener clientListener;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        serverListener = new TestMessageListener();
        clientListener = new TestMessageListener();
        
        server = new ChatServer(TEST_PORT, SERVER_USERNAME, serverListener);
        server.start();
        
        Thread.sleep(500);
        
        client = new ChatClient("localhost", TEST_PORT, CLIENT_USERNAME, clientListener);
        client.connect();
        
        Thread.sleep(500);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.disconnect();
        }
        if (server != null) {
            server.disconnect();
        }
    }

    @Test
    void testClientToServerMessageDelivery() throws InterruptedException {
        String testMessage = "Hello from client!";
        CountDownLatch latch = new CountDownLatch(1);
        serverListener.setOnMessageReceived(() -> latch.countDown());

        client.sendMessage(testMessage);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Сообщение не было получено сервером вовремя");
        assertEquals(1, serverListener.getReceivedMessages().size());
        ReceivedMessage msg = serverListener.getReceivedMessages().get(0);
        assertEquals(CLIENT_USERNAME, msg.sender);
        assertEquals(testMessage, msg.text);
    }

    @Test
    void testServerToClientMessageDelivery() throws InterruptedException {
        String testMessage = "Hello from server!";
        CountDownLatch latch = new CountDownLatch(1);
        clientListener.setOnMessageReceived(() -> latch.countDown());

        server.sendMessage(testMessage);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Сообщение не было получено клиентом вовремя");
        assertEquals(1, clientListener.getReceivedMessages().size());
        ReceivedMessage msg = clientListener.getReceivedMessages().get(0);
        assertEquals(SERVER_USERNAME, msg.sender);
        assertEquals(testMessage, msg.text);
    }

    @Test
    void testBidirectionalMessageExchange() throws InterruptedException {
        CountDownLatch clientLatch = new CountDownLatch(2);
        CountDownLatch serverLatch = new CountDownLatch(2);
        clientListener.setOnMessageReceived(() -> clientLatch.countDown());
        serverListener.setOnMessageReceived(() -> serverLatch.countDown());

        client.sendMessage("Message 1 from client");
        server.sendMessage("Message 1 from server");
        client.sendMessage("Message 2 from client");
        server.sendMessage("Message 2 from server");

        assertTrue(clientLatch.await(3, TimeUnit.SECONDS), "Клиент не получил все сообщения");
        assertTrue(serverLatch.await(3, TimeUnit.SECONDS), "Сервер не получил все сообщения");
        
        assertEquals(2, clientListener.getReceivedMessages().size());
        assertEquals(2, serverListener.getReceivedMessages().size());
    }

    @Test
    void testClientDisconnect() throws InterruptedException {
        CountDownLatch disconnectLatch = new CountDownLatch(1);
        serverListener.setOnSystemMessage((msg) -> {
            if (msg.contains("отключился")) {
                disconnectLatch.countDown();
            }
        });

        client.disconnect();

        assertTrue(disconnectLatch.await(3, TimeUnit.SECONDS), "Сервер не обнаружил отключение клиента");
    }

    @Test
    void testMultipleConnectionsRejection() throws InterruptedException, IOException {
        TestMessageListener secondClientListener = new TestMessageListener();
        CountDownLatch errorLatch = new CountDownLatch(1);
        secondClientListener.setOnSystemMessage((msg) -> {
            if (msg.contains("разорвана")) {
                errorLatch.countDown();
            }
        });

        ChatClient secondClient = new ChatClient("localhost", TEST_PORT, "SecondClient", secondClientListener);
        secondClient.connect();
        Thread.sleep(500);
        secondClient.sendMessage("This should not be delivered");

        assertTrue(errorLatch.await(3, TimeUnit.SECONDS), "Второе подключение не было отклонено");
        
        CountDownLatch firstClientLatch = new CountDownLatch(1);
        serverListener.setOnMessageReceived(() -> firstClientLatch.countDown());
        client.sendMessage("First client still works");
        assertTrue(firstClientLatch.await(2, TimeUnit.SECONDS), "Первый клиент перестал работать");
        
        secondClient.disconnect();
    }

    @Test
    void testMessageTimestampsAreRecent() throws InterruptedException {
        long beforeSend = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);
        serverListener.setOnMessageReceived(() -> latch.countDown());

        client.sendMessage("Test timestamp");
        latch.await(2, TimeUnit.SECONDS);
        long afterReceive = System.currentTimeMillis();

        assertEquals(1, serverListener.getReceivedMessages().size());
        ReceivedMessage msg = serverListener.getReceivedMessages().get(0);
        assertTrue(msg.timestamp >= beforeSend && msg.timestamp <= afterReceive,
                "Timestamp не в ожидаемом диапазоне");
    }

    @Test
    void testEmptyMessageNotSent() throws InterruptedException {
        serverListener.setOnMessageReceived(() -> fail("Пустое сообщение не должно было быть отправлено"));

        client.sendMessage("");
        
        Thread.sleep(1000);
        assertEquals(0, serverListener.getReceivedMessages().size());
    }

    private static class ReceivedMessage {
        String sender;
        long timestamp;
        String text;

        ReceivedMessage(String sender, long timestamp, String text) {
            this.sender = sender;
            this.timestamp = timestamp;
            this.text = text;
        }
    }

    private static class TestMessageListener implements MessageListener {
        private final List<ReceivedMessage> receivedMessages = new ArrayList<>();
        private final List<String> systemMessages = new ArrayList<>();
        private Runnable onMessageCallback;
        private java.util.function.Consumer<String> onSystemMessageCallback;

        @Override
        public synchronized void onMessageReceived(String sender, long timestamp, String text) {
            receivedMessages.add(new ReceivedMessage(sender, timestamp, text));
            if (onMessageCallback != null) {
                onMessageCallback.run();
            }
        }

        @Override
        public synchronized void onSystemMessage(String text) {
            systemMessages.add(text);
            if (onSystemMessageCallback != null) {
                onSystemMessageCallback.accept(text);
            }
        }

        public synchronized List<ReceivedMessage> getReceivedMessages() {
            return new ArrayList<>(receivedMessages);
        }

        public synchronized List<String> getSystemMessages() {
            return new ArrayList<>(systemMessages);
        }

        public void setOnMessageReceived(Runnable callback) {
            this.onMessageCallback = callback;
        }

        public void setOnSystemMessage(java.util.function.Consumer<String> callback) {
            this.onSystemMessageCallback = callback;
        }
    }
}
