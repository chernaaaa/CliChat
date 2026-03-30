# Архитектура P2P чата CliChat

## 1. Требования к приложению

Что мы реализуем в рамках задания:
1. **Режимы работы:** Приложение умеет быть и сервером (ждет подключений), и клиентом (подключается к другому пиру).
2. **Запуск:** При старте обязательно указываем свое имя (`--username`). Если хотим к кому-то подключиться, передаем адрес и порт (`--peer`, `--port`). Если адрес не передали, просто запускаемся как сервер и ждем собеседника.
3. **Сеть:** Соединение устанавливается напрямую (peer-to-peer). Сообщения можно отправлять и получать в любой момент времени асинхронно.
4. **Интерфейс:** Обычная консоль.
5. **Формат сообщений:** При выводе на экран сообщение должно содержать имя отправителя, время и сам текст. Примерно так: `[2026-03-30 15:45:00] Alice: Привет!`.

---

## 2. Выбор технологий

* **Язык:** `Java 21`. Мы выбрали Java, потому что в ней удобно работать с многопоточностью (нам нужно одновременно слушать сеть и читать ввод с клавиатуры), плюс мы с ней хорошо знакомы.
* **Сетевой протокол:** `gRPC`. Как и рекомендовалось в задании, решили взять его. Для чата отлично подходит механизм Bidirectional Streaming — мы открываем один канал и гоняем по нему сообщения в обе стороны.
* **Сборка:** `Gradle`. С ним проще всего настроить генерацию gRPC-классов из `.proto` файлов и прикрутить CI/CD.
* **Тесты:** `JUnit 5` и `Mockito` — стандартный и понятный набор для юнит-тестов.
* **Диаграммы:** `Mermaid.js`, чтобы хранить схемы прямо в Markdown-файле, и они красиво отображались на GitHub/GitLab.

---

## 3. Диаграммы

### 3.1. Диаграмма компонент

```mermaid
flowchart TD
    User((Пользователь))

    subgraph App ["P2P Chat Application"]
        CLI["CLI Parser"]
        UI["Console UI"]
        Core["Chat Orchestrator"]
        
        subgraph Network ["Network Layer (gRPC)"]
            Server["gRPC Server"]
            Client["gRPC Client"]
        end
    end

    User -->|Запуск с аргументами| CLI
    User -->|Ввод сообщений| UI
    UI -->|Вывод сообщений| User
    
    CLI -->|Конфигурация| Core
    UI <-->|Передача/Получение текста| Core
    
    Core <-->|Управление сервером| Server
    Core <-->|Управление клиентом| Client
    
    Server <-->|gRPC Bidirectional Stream| Client
```

**Ответственность компонент:**
* **CLI Parser:** Читает аргументы при запуске и понимает, кто мы — сервер или клиент.
* **Console UI:** Крутится в отдельном потоке. Ждет, пока пользователь что-то напечатает, и красиво выводит то, что пришло по сети.
* **Chat Orchestrator:** Главный класс, который связывает интерфейс и сеть.
* **Network Layer:** Вся работа с gRPC. В зависимости от аргументов запускает либо серверную часть, либо клиентскую.

### 3.2. Диаграмма классов

```mermaid
classDiagram
    class ChatApplication {
        +main(args: String[])
        -startServerMode(port: int, username: String)
        -startClientMode(host: String, port: int, username: String)
    }

    class CommandLineArgs {
        +String username
        +String peerHost
        +Integer peerPort
        +boolean isServer()
    }

    class ConsoleInterface {
        -String username
        -MessageListener listener
        +startReading()
        +displayMessage(sender: String, timestamp: long, text: String)
        +displaySystemMessage(text: String)
    }

    class GrpcChatNode {
        <<interface>>
        +sendMessage(text: String)
        +disconnect()
    }

    class ChatServer {
        -Server grpcServer
        +start(port: int)
    }

    class ChatClient {
        -ManagedChannel channel
        +connect(host: String, port: int)
    }

    ChatApplication --> CommandLineArgs : parses
    ChatApplication --> ConsoleInterface : initializes
    ChatApplication --> ChatServer : creates (if server)
    ChatApplication --> ChatClient : creates (if client)
    
    ChatServer ..|> GrpcChatNode
    ChatClient ..|> GrpcChatNode
    
    ConsoleInterface --> GrpcChatNode : sends input
```

**Кратко по классам:**
* `ChatApplication`: Точка входа в программу.
* `CommandLineArgs`: Простой класс, где лежат распарсенные аргументы запуска.
* `ConsoleInterface`: Отвечает за консоль. Метод `startReading()` бесконечно ждет ввода с клавиатуры.
* `GrpcChatNode`: Общий интерфейс. Интерфейсу (UI) не нужно знать, сервер мы сейчас или клиент — он просто вызывает `sendMessage()`.
* `ChatServer` и `ChatClient`: Сама реализация сети под капотом gRPC.

---

## 4. Распределение задач

Чтобы не мешать друг другу и не ловить постоянные конфликты при слиянии веток, мы разделили работу на две независимые части.

### Алина (Сеть и инфраструктура)
* Настройка пустого проекта, `build.gradle`, добавление плагинов для gRPC.
* Настройка линтера (Checkstyle) и CI/CD (GitHub Actions), чтобы код проверялся автоматически при пуше.
* Написание `chat.proto` (описание того, как будут выглядеть сообщения на уровне сети).
* Реализация `ChatServer` и `ChatClient` (вся логика gRPC).

### Соня (Логика, консоль и тесты)
* Парсинг аргументов командной строки.
* Написание `ConsoleInterface` (чтение с клавиатуры в отдельном потоке, форматирование времени и имени).
* Связка всего вместе в `ChatApplication`.
* Написание юнит-тестов и плана тестирования.

### Что делаем вместе
* Ревьюим код друг друга в Pull Request'ах.
* Пишем документацию (этот файл и README).
* Проводим ручное тестирование готового чата.
