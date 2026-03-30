package ru.hse.clichat.network;

/**
 * Интерфейс для получения уведомлений о входящих сообщениях.
 * Реализуется компонентами пользовательского интерфейса.
 */
public interface MessageListener {
    /**
     * Вызывается при получении сообщения от собеседника.
     *
     * @param sender имя отправителя
     * @param timestamp время отправки в миллисекундах (Unix timestamp)
     * @param text текст сообщения
     */
    void onMessageReceived(String sender, long timestamp, String text);
    
    /**
     * Вызывается для отображения системных сообщений
     * (подключение, отключение, ошибки и т.д.).
     *
     * @param text текст системного сообщения
     */
    void onSystemMessage(String text);
}
