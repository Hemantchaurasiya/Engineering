# Spring AI Chat Memory — Effective Notes

> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

1. [Why chat memory? (LLMs are stateless)](#1-why-chat-memory-llms-are-stateless)
2. [ChatMemory vs ChatMemoryRepository](#2-chatmemory-vs-chatmemoryrepository)
3. [Chat Memory vs Chat History](#3-chat-memory-vs-chat-history)
4. [Quick start (auto-configuration)](#4-quick-start-auto-configuration)
5. [Working with Conversation IDs](#5-working-with-conversation-ids)
6. [MessageWindowChatMemory](#6-messagewindowchatmemory)
7. [Turn-boundary eviction](#7-turn-boundary-eviction)
8. [InMemoryChatMemoryRepository](#8-inmemorychatmemoryrepository)
9. [JdbcChatMemoryRepository](#9-jdbcchatmemoryrepository)
10. [CassandraChatMemoryRepository](#10-cassandrachatmemoryrepository)
11. [Neo4jChatMemoryRepository](#11-neo4jchatmemoryrepository)
12. [CosmosDBChatMemoryRepository](#12-cosmosdbchatmemoryrepository)
13. [MongoChatMemoryRepository](#13-mongochatmemoryrepository)
14. [RedisChatMemoryRepository](#14-redischatmemoryrepository)
15. [Which repository to choose?](#15-which-repository-to-choose)
16. [Memory in ChatClient (advisors)](#16-memory-in-chatclient-advisors)
17. [VectorStoreChatMemoryAdvisor and custom template](#17-vectorstorechatmemoryadvisor-and-custom-template)
18. [Memory in ChatModel (manual)](#18-memory-in-chatmodel-manual)
19. [Quick cheat sheet](#19-quick-cheat-sheet)
20. [Common mistakes](#20-common-mistakes)
21. [Interview quick Q&A](#21-interview-quick-qa)

---

## 1. Why chat memory? (LLMs are stateless)

**Key Points**
- LLMs **do not remember** previous interactions. Every request is **independent**.
- If you say "My name is Hemant" and later ask "What is my name?", the model does not know, unless you **send the earlier messages again**.
- Spring AI **chat memory** stores and retrieves messages so you can send the right context with each request.

**Use Cases**
- Chatbots, support assistants, multi-step wizards, "what about the second option?" follow-ups.

**Where to Use**
- Any multi-turn conversation feature.

**Problem Solved**
- The model forgetting context between requests.

**Java Example**
```java
// Without memory: second call has no idea about the first
chatClient.prompt().user("My name is Hemant").call().content();
chatClient.prompt().user("What is my name?").call().content();   // model does not know
```

---

## 2. ChatMemory vs ChatMemoryRepository

**Key Points**
- Two layers (split of responsibility):

| Layer | Job |
|---|---|
| **`ChatMemory`** | **Decides WHICH messages to keep and WHEN to remove them** (the strategy) |
| **`ChatMemoryRepository`** | **Only stores and retrieves** messages (the storage). Nothing else. |

- Strategy examples for `ChatMemory`:
  - keep the **last N messages**,
  - keep messages for a **time period**,
  - keep messages up to a **token limit**.
- Storage examples for `ChatMemoryRepository`: in-memory, JDBC, Cassandra, Neo4j, Mongo, Redis.
- Like Spring Data: **repository = DB access**, **service = business rules**.

**Use Cases**
- Change storage (in-memory → Redis) **without** changing your window strategy, and vice versa.

**Where to Use**
- Designing memory for your chatbot.

**Problem Solved**
- Clean separation: eviction rules and storage are independent and replaceable.

**Java Example**
```java
// Strategy (ChatMemory) + Storage (ChatMemoryRepository)
ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)   // storage
    .maxMessages(10)                               // strategy: last 10 messages
    .build();
```

---

## 3. Chat Memory vs Chat History

**Key Points**
| Term | Meaning |
|---|---|
| **Chat Memory** | What the LLM **retains and uses** for context in the current conversation (usually a **limited** part). |
| **Chat History** | The **entire** conversation: **all** messages exchanged. |

- `ChatMemory` is designed for **memory**, **not** for storing full **history**.
- Need a complete record (audit, compliance, show old chats in UI)? Use a **different approach**, like **Spring Data** for efficient storage and retrieval of full history.

**Use Cases**
- Memory: last 20 messages sent to the model.
- History: full chat saved for audit and user "chat history" screen.

**Where to Use**
- Decide early: do you need only context, or also a permanent record?

**Problem Solved**
- Stops you from using `ChatMemory` as an audit log (it evicts old messages).

**Java Example**
```java
// History: save EVERY message yourself with Spring Data
@Entity
class ChatHistoryEntry {
    @Id @GeneratedValue Long id;
    String conversationId;
    String role;
    @Column(length = 4000) String text;
    Instant createdAt;
}

interface ChatHistoryRepository extends JpaRepository<ChatHistoryEntry, Long> {
    List<ChatHistoryEntry> findByConversationIdOrderByCreatedAtAsc(String conversationId);
}
// Memory (ChatMemory) is separate and only holds what the model needs.
```

---

## 4. Quick start (auto-configuration)

**Key Points**
- Spring AI **auto-configures** a `ChatMemory` bean.
- Defaults:
  - Repository: **`InMemoryChatMemoryRepository`**.
  - Memory type: **`MessageWindowChatMemory`**.
- If **another repository** is already configured (Cassandra, JDBC, Neo4j...), Spring AI uses **that** instead.
- Just inject it.

**Use Cases**
- Prototype quickly with zero config.

**Where to Use**
- Dev/demo; switch repository dependency for production.

**Problem Solved**
- No manual wiring to start.

**Java Example**
```java
@Service
class ChatService {
    private final ChatMemory chatMemory;

    ChatService(ChatMemory chatMemory) {   // auto-configured bean
        this.chatMemory = chatMemory;
    }
}
```

---

## 5. Working with Conversation IDs

**Key Points**
- **Every memory operation uses a conversation ID.** It tells memory **which conversation** to read, append, or delete.
- Must be provided **explicitly** (no default).
- The conversation ID is the **only thing that separates conversations**. Wrong ID = mixed or lost conversations.
- **Multi-user apps**: ID must be **unique per user** (and per conversation if a user can have many).
- **Best practices**:
  1. Distinct ID per user (and per conversation).
  2. **Derive the ID on the server** from user/session. Do not trust a client-sent or fixed shared value.
  3. When **listing or deleting** conversations, only touch IDs that **belong to the current user**.

**Use Cases**
- Multi-tenant SaaS chatbot, multiple chat tabs per user.

**Where to Use**
- Every call that uses memory.

**Problem Solved**
- Prevents **data leaks between users** and mixed-up histories (a security issue).

**Java Example**
```java
// Build the conversation ID on the SERVER from user + session
String conversationId = currentUser.getId() + ":" + httpSession.getId();

String reply = chatClient.prompt()
    .user(userInput)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
    .call()
    .content();

// Per-user chat listing (never list all IDs globally)
List<String> myConversations = conversationRepo.findIdsByUserId(currentUser.getId());
```

---

## 6. MessageWindowChatMemory

**Key Points**
- Keeps a **sliding window** of the latest messages, up to `maxMessages`.
- **Default window size = 20** messages.
- When the limit is crossed, **older messages are evicted**.
- **`SystemMessage`s are always preserved** (not evicted).
- It is the **default memory type** in auto-configuration.

**Use Cases**
- Normal chatbots where recent context is enough.

**Where to Use**
- Default choice; tune `maxMessages` for your cost/quality balance.

**Problem Solved**
- Stops memory (and token cost) from growing forever; keeps recent context.

**Java Example**
```java
MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
    .maxMessages(10)
    .build();
```
Window example (maxMessages = 4):
```
Messages: S, U1, A1, U2, A2, U3, A3
Result  : S, U2, A2, U3, A3  (older turn removed; System kept)
```
> Rough idea only; real count follows turn-boundary rules (next section).

---

## 7. Turn-boundary eviction

**Key Points**
- When evicting, `MessageWindowChatMemory` removes **whole turns**, **never cuts a turn in the middle**.
- A **turn** = starts at a `UserMessage` + all following assistant replies, tool calls, and tool responses, up to the **next** `UserMessage`.
- If the cut point lands on a non-user message (e.g., an assistant reply in the middle of a tool-call exchange), the cut moves **forward to the next `UserMessage`**, so the kept window **always starts at a complete turn**.
- So `maxMessages` is an **upper bound**. The real count may be **lower**.
- **Danger**: if `maxMessages` is **smaller than one complete turn** (e.g., a long multi-step tool exchange), **all non-system messages may be evicted** until a new `UserMessage` arrives.
- **Tip**: set `maxMessages` big enough for **at least one typical turn**.

```
Turn = [User] → [Assistant(tool call)] → [Tool response] → [Assistant answer]
Never keep half of this. Keep all or remove all.
```

**Use Cases**
- Tool-calling chats where one turn may contain many messages.

**Where to Use**
- Choosing `maxMessages` for tool-heavy apps.

**Problem Solved**
- Prevents broken context (e.g., a tool response without its tool call), which can cause provider errors or confused answers.

**Java Example**
```java
// Tool-heavy bot: one turn may be 6+ messages. Use a generous window.
ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .maxMessages(30)
    .build();
```

---

## 8. InMemoryChatMemoryRepository

**Key Points**
- Stores messages in a **`ConcurrentHashMap`** inside the JVM.
- **Default** repository if nothing else is configured (auto-configured bean).
- Data is **lost on restart**, and **not shared** between multiple app instances.

**Use Cases**
- Local development, tests, demos, single-node short-lived chats.

**Where to Use**
- Not for production if you need persistence or multiple instances.

**Problem Solved**
- Zero-setup storage for getting started.

**Java Example**
```java
// Auto-configured
@Autowired
ChatMemoryRepository chatMemoryRepository;

// Or manual
ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
```

---

## 9. JdbcChatMemoryRepository

**Key Points**
- Stores messages in a **relational database** using JDBC. **Persistent.**
- Messages returned **oldest → newest** (correct order for LLMs). Order kept by a **`sequence_id`** column.
- Each message has a **creation timestamp**, available in message metadata under key `JdbcChatMemoryRepository.CONVERSATION_TS` (a `java.time.Instant`). Preserved when saved again.
- **Supported DBs**: PostgreSQL, MySQL/MariaDB, SQL Server, HSQLDB, Oracle.
- **Dialect auto-detected** from the JDBC URL via `JdbcChatMemoryRepositoryDialect.from(DataSource)`. For other DBs, implement `JdbcChatMemoryRepositoryDialect` (SQL for select, insert, delete).
- **Limitation**: **tool call messages are NOT supported.** `AssistantMessage` with tool calls and `ToolResponseMessage` are **silently filtered out** on save. For tool calling, consider the **Spring AI Session** project with the JDBC session store.
- **Table**: `SPRING_AI_CHAT_MEMORY` (auto-created).
- **2.0.0 change**: new `sequence_id` column for ordering; the timestamp column is now shown in metadata. **Upgrading apps must add the new column** (see Upgrade Notes).

**Dependency**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
```

**Config properties**
| Property | Meaning | Default |
|---|---|---|
| `spring.ai.chat.memory.repository.jdbc.initialize-schema` | When to create schema: `embedded`, `always`, `never` | `embedded` |
| `spring.ai.chat.memory.repository.jdbc.schema` | Schema script location (supports `classpath:` and `@@platform@@`) | `classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-@@platform@@.sql` |
| `spring.ai.chat.memory.repository.jdbc.platform` | Platform for `@@platform@@` placeholder | auto-detected |

**Schema initialization**
- By default, runs **only for embedded DBs** (H2, HSQL, Derby).
- `always` → always create. `never` → use Flyway/Liquibase for production.

```properties
spring.ai.chat.memory.repository.jdbc.initialize-schema=embedded   # default
spring.ai.chat.memory.repository.jdbc.initialize-schema=always
spring.ai.chat.memory.repository.jdbc.initialize-schema=never      # with Flyway/Liquibase
spring.ai.chat.memory.repository.jdbc.schema=classpath:/custom/path/schema-mysql.sql
```

**Use Cases**
- Enterprise apps already on PostgreSQL/MySQL/Oracle that need persistent chat memory with ACID safety.

**Where to Use**
- Most Spring Boot enterprise backends (you already have a DB, so no extra infrastructure).

**Problem Solved**
- Memory survives restarts and works across multiple app instances.

**Java Example**
```java
// Auto-configured
@Autowired
JdbcChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();

// Manual creation
ChatMemoryRepository repo = JdbcChatMemoryRepository.builder()
    .jdbcTemplate(jdbcTemplate)
    .dialect(new PostgresChatMemoryRepositoryDialect())
    .build();

// Custom dialect for another DB
ChatMemoryRepository custom = JdbcChatMemoryRepository.builder()
    .jdbcTemplate(jdbcTemplate)
    .dialect(new MyCustomDbDialect())
    .build();

// Reading message timestamps (e.g., for UI)
List<Message> messages = chatMemory.get(conversationId);
for (Message message : messages) {
    Instant createdAt = (Instant) message.getMetadata().get(JdbcChatMemoryRepository.CONVERSATION_TS);
}
```

---

## 10. CassandraChatMemoryRepository

**Key Points**
- Stores messages in **Apache Cassandra**. Good for **availability, durability, scale**, and **TTL**.
- Uses a **time-series schema** → keeps record of **all past chat windows** → useful for **governance and auditing**.
- **Recommended**: set a TTL (example in the doc: three years).
- Messages returned **oldest → newest** by timestamp.
- **Limitation**: **tool call messages not supported** (silently filtered out). Consider Spring AI Session + JDBC session store.
- **Table**: `ai_chat_memory` (auto-created).
- Disable schema creation: `spring.ai.chat.memory.repository.cassandra.initialize-schema=false` (as written in the doc's "Schema Initialization" part; the property table lists `spring.ai.chat.memory.cassandra.initialize-schema`. Check your version).

**Dependency**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-cassandra</artifactId>
</dependency>
```

**Config properties**
| Property | Meaning | Default |
|---|---|---|
| `spring.cassandra.contactPoints` | Host(s) for cluster discovery | `127.0.0.1` |
| `spring.cassandra.port` | Native protocol port | `9042` |
| `spring.cassandra.localDatacenter` | Datacenter | `datacenter1` |
| `spring.ai.chat.memory.cassandra.time-to-live` | TTL for messages | (not set) |
| `spring.ai.chat.memory.cassandra.keyspace` | Keyspace | `springframework` |
| `spring.ai.chat.memory.cassandra.messages-column` | Messages column name | `springframework` |
| `spring.ai.chat.memory.cassandra.table` | Table name | `ai_chat_memory` |
| `spring.ai.chat.memory.cassandra.initialize-schema` | Create schema on startup | `true` |

**Use Cases**
- Large-scale, multi-region apps; compliance needs with auto-expiry.

**Where to Use**
- When you already run Cassandra and need scale + TTL.

**Problem Solved**
- Highly available, scalable chat memory with automatic expiry.

**Java Example**
```java
@Autowired
CassandraChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();

// Manual
ChatMemoryRepository repo = CassandraChatMemoryRepository
    .create(CassandraChatMemoryRepositoryConfig.builder().withCqlSession(cqlSession));
```

---

## 11. Neo4jChatMemoryRepository

**Key Points**
- Stores messages as **nodes and relationships** in a **graph database** (Neo4j).
- Messages returned **oldest → newest** by **message index**.
- **No schema initialization** needed. **Indexes** for conversation IDs and message indices are created automatically (also for custom labels).
- **Label rule**: must start with a letter or underscore, and contain only letters, digits, underscores. Invalid label → `IllegalArgumentException` at startup.
- Stores tool calls, tool responses, media, and metadata as separate nodes (labels below).

**Dependency**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-neo4j</artifactId>
</dependency>
```

**Config properties**
| Property (prefix `spring.ai.chat.memory.repository.neo4j.`) | Meaning | Default |
|---|---|---|
| `session-label` | Label for conversation session nodes | `Session` |
| `message-label` | Label for message nodes | `Message` |
| `tool-call-label` | Label for tool call nodes | `ToolCall` |
| `metadata-label` | Label for message metadata nodes | `Metadata` |
| `tool-response-label` | Label for tool response nodes | `ToolResponse` |
| `media-label` | Label for media nodes | `Media` |

**Use Cases**
- Apps that want to **analyze conversation structure** with graph queries (connections between sessions, messages, tools).

**Where to Use**
- When Neo4j is already part of your stack.

**Problem Solved**
- Persistent memory that also supports graph-based analysis.

**Java Example**
```java
@Autowired
Neo4jChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();

// Manual
ChatMemoryRepository repo = Neo4jChatMemoryRepository.builder()
    .driver(driver)
    .build();
```

---

## 12. CosmosDBChatMemoryRepository

**Key Points**
- Available as an **external module** maintained by the **Azure Cosmos DB team**.
- Not part of core Spring AI. See that module's documentation.

**Use Cases / Where to Use**
- Azure-based systems using Cosmos DB.

**Problem Solved**
- Memory storage for Azure-native stacks.

---

## 13. MongoChatMemoryRepository

**Key Points**
- Stores messages in **MongoDB** (flexible, document-oriented).
- Messages returned **oldest → newest** by timestamp (same order for all repositories).
- **Limitation**: **tool call messages not supported** (silently filtered out). Consider Spring AI Session + JDBC session store.
- **Collection**: `ai_chat_memory` (auto-created if missing).
- Can expire old messages with **TTL**.

**Dependency**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-mongodb</artifactId>
</dependency>
```

**Config properties**
| Property (prefix `spring.ai.chat.memory.repository.mongo.`) | Meaning | Default |
|---|---|---|
| `create-indices` | Create/recreate indices on startup. **Changing TTL drops and recreates the TTL index.** | `false` |
| `ttl` | TTL in **seconds**. If not set, stored forever. | `0` |

**Use Cases**
- Apps already on MongoDB; flexible message metadata.

**Where to Use**
- Document-based stacks needing persistent memory with expiry.

**Problem Solved**
- Persistent memory without adding a relational DB.

**Java Example**
```java
@Autowired
MongoChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();

// Manual
ChatMemoryRepository repo = MongoChatMemoryRepository.builder()
    .mongoTemplate(mongoTemplate)
    .build();
```
```properties
spring.ai.chat.memory.repository.mongo.create-indices=true
spring.ai.chat.memory.repository.mongo.ttl=86400      # 1 day, in seconds
```

---

## 14. RedisChatMemoryRepository

**Key Points**
- Uses **Redis Stack** (needs **Redis Query Engine** and **RedisJSON**) for **fast, low-latency** memory with optional **TTL** and advanced queries.
- Stores messages as **JSON documents** and creates a **search index**.
- Messages returned **oldest → newest** by timestamp.
- Also implements **`AdvancedRedisChatMemoryRepository`** for extended queries: by **type**, **content**, **time range**, **metadata**, plus **custom Redis queries**.
- **Requirements**: Redis Stack **7.0+**, **Jedis** client (included).
- Schema (search index) is auto-created if missing. Disable with `initialize-schema=false`.
- To query **custom metadata fields** efficiently, define metadata fields (`tag` = exact match, `text` = full-text search, `numeric` = range queries).

**Dependency**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-redis</artifactId>
</dependency>
```

**Config properties (prefix `spring.ai.chat.memory.repository.redis.`)**
| Property | Meaning | Default |
|---|---|---|
| `host` | Redis host | `localhost` |
| `port` | Redis port | `6379` |
| `username` | ACL username | none |
| `password` | Password | none |
| `index-name` | Search index name | `chat-memory-idx` |
| `key-prefix` | Key prefix | `chat-memory:` |
| `time-to-live` | TTL (e.g., `24h`, `30d`) | no expiration |
| `initialize-schema` | Create schema on startup | `true` |
| `max-conversation-ids` | Max conversation IDs returned | `1000` |
| `max-messages-per-conversation` | Max messages returned per conversation | `1000` |

**Use Cases**
- High-traffic chat apps needing speed and auto-expiry (e.g., 24h sessions).
- Search past messages by content/type/time.

**Where to Use**
- When latency matters and you already use Redis Stack.

**Problem Solved**
- Fast memory with built-in expiry and rich search.

**Java Example**
```java
@Autowired
RedisChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();

// Manual creation
RedisClient jedisClient = RedisClient.builder().hostAndPort("localhost", 6379).build();

ChatMemoryRepository repo = RedisChatMemoryRepository.builder()
    .jedisClient(jedisClient)
    .indexName("my-chat-index")
    .keyPrefix("my-chat:")
    .timeToLive(Duration.ofHours(24))
    .build();

// Advanced queries
AdvancedRedisChatMemoryRepository advancedRepo = (AdvancedRedisChatMemoryRepository) chatMemoryRepository;

List<MessageWithConversation> userMessages = advancedRepo.findByType(MessageType.USER, 100);
List<MessageWithConversation> results = advancedRepo.findByContent("Spring AI", 50);
List<MessageWithConversation> recent = advancedRepo.findByTimeRange(
    conversationId, Instant.now().minus(Duration.ofHours(1)), Instant.now(), 100);
List<MessageWithConversation> priority = advancedRepo.findByMetadata("priority", "high", 50);
List<MessageWithConversation> custom = advancedRepo.executeQuery("@type:USER @content:Redis", 100);
```
```properties
# Index custom metadata fields
spring.ai.chat.memory.repository.redis.metadata-fields[0].name=priority
spring.ai.chat.memory.repository.redis.metadata-fields[0].type=tag
spring.ai.chat.memory.repository.redis.metadata-fields[1].name=score
spring.ai.chat.memory.repository.redis.metadata-fields[1].type=numeric
spring.ai.chat.memory.repository.redis.metadata-fields[2].name=category
spring.ai.chat.memory.repository.redis.metadata-fields[2].type=tag
```

---

## 15. Which repository to choose?

| Repository | Persistent | Best for | Tool call messages | Notes |
|---|---|---|---|---|
| **InMemory** | No | Dev, tests, demos | Stored (in memory) | Lost on restart; not shared across instances |
| **JDBC** | Yes | Enterprise apps with relational DB | **Filtered out** | Supports 5 DBs; `sequence_id` ordering; timestamps in metadata |
| **Cassandra** | Yes | Huge scale, audit, TTL | **Filtered out** | Time-series schema; keeps past windows |
| **Neo4j** | Yes | Graph analysis | Stored as nodes (has tool call/response labels) | Auto indexes |
| **MongoDB** | Yes | Document-style stacks | **Filtered out** | TTL in seconds |
| **Redis** | Yes (Redis Stack) | Fast, low latency, TTL, searching messages | Not stated in docs | Needs Redis Stack 7.0+ |
| **CosmosDB** | Yes | Azure stacks | See module docs | External module |

> The document explicitly says JDBC, Cassandra, and Mongo **filter out tool call messages**. For Neo4j, labels for tool calls exist, so it handles them. For InMemory and Redis the document does not say, so check before relying on it.

**Quick decision**
- Just learning → **InMemory**
- Enterprise with SQL DB → **JDBC**
- Need speed + expiry → **Redis**
- Huge scale/audit → **Cassandra**
- Using tools + need to store all messages → look at **Spring AI Session** project (JDBC session store)

---

## 16. Memory in ChatClient (advisors)

**Key Points**
- With `ChatClient`, you attach a `ChatMemory` through **advisors**. Spring AI manages the memory for you.
- Two built-in memory advisors:
  - **`MessageChatMemoryAdvisor`**: gets history from `ChatMemory` and adds it to the prompt as a **collection of messages**.
  - **`VectorStoreChatMemoryAdvisor`**: gets history from a **`VectorStore`** and adds it to the **system message as plain text**.
- **`ChatMemory.CONVERSATION_ID` is REQUIRED** on every call with a memory advisor. Missing it → **`IllegalArgumentException`**. **No default ID.**
- **Current limitation**: intermediate messages in **tool calls** (between app and LLM) are **not stored** in memory. To store them, use **User Controlled Tool Execution**.

**Use Cases**
- Chatbots with the fluent API; support assistants.

**Where to Use**
- Register advisor as a **default** in config; pass the conversation ID per call.

**Problem Solved**
- Automatic history handling without manual message management.

**Java Example**
```java
ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
    .build();

String conversationId = "007";

String answer = chatClient.prompt()
    .user("Do I have license to code?")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
    .call()
    .content();

// Full Spring Boot style
@Configuration
class AiConfig {
    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
    }
}
```

**Message advisor vs Vector advisor**

| Point | `MessageChatMemoryAdvisor` | `VectorStoreChatMemoryAdvisor` |
|---|---|---|
| Source | `ChatMemory` | `VectorStore` |
| Added as | List of **messages** | **Plain text in system message** |
| Best for | Normal chats | Long histories; searching relevant past parts |

---

## 17. VectorStoreChatMemoryAdvisor and custom template

**Key Points**
- It uses a **default template** to add retrieved memory into the **system message**.
- Customize with **`.promptTemplate(...)`** on the advisor builder.
- Your template **must contain two placeholders**:
  1. **`instructions`** → receives the **original system message**.
  2. **`long_term_memory`** → receives the **retrieved conversation memory**.
- Can use any `TemplateRenderer` (default: `StPromptTemplate` based on StringTemplate).
- **Do not mix up**:
  - Advisor's `PromptTemplate` → how **retrieved memory is merged** with the system message.
  - `ChatClient.templateRenderer()` → how the **initial user/system prompt text** is rendered **before** the advisor runs.

**Use Cases**
- Control how memory appears: e.g., "Use this past info only if relevant".

**Where to Use**
- Long-term memory with a vector store.

**Problem Solved**
- Custom wording and placement of retrieved memory in the system prompt.

**Java Example**
```java
PromptTemplate memoryTemplate = new PromptTemplate("""
    {instructions}

    Use the previous conversation below only if it is relevant:
    {long_term_memory}
    """);

var advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
    .promptTemplate(memoryTemplate)
    .build();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(advisor)
    .build();
```
> Builder method names can differ by version. Check Javadoc.

---

## 18. Memory in ChatModel (manual)

**Key Points**
- Using `ChatModel` directly (no `ChatClient`)? **You manage memory yourself.**
- Steps for each turn:
  1. `chatMemory.add(conversationId, userMessage)`.
  2. `chatModel.call(new Prompt(chatMemory.get(conversationId)))`.
  3. `chatMemory.add(conversationId, response.getResult().getOutput())` (save the assistant reply).
- Forgetting step 3 means the model never sees its own earlier answers.

**Use Cases**
- Custom frameworks, learning how memory works, special control over messages.

**Where to Use**
- Low-level integration; for most apps `ChatClient` + advisor is easier.

**Problem Solved**
- Memory support when not using `ChatClient`.

**Java Example**
```java
ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
String conversationId = "007";

// First interaction
UserMessage userMessage1 = new UserMessage("My name is James Bond");
chatMemory.add(conversationId, userMessage1);
ChatResponse response1 = chatModel.call(new Prompt(chatMemory.get(conversationId)));
chatMemory.add(conversationId, response1.getResult().getOutput());

// Second interaction
UserMessage userMessage2 = new UserMessage("What is my name?");
chatMemory.add(conversationId, userMessage2);
ChatResponse response2 = chatModel.call(new Prompt(chatMemory.get(conversationId)));
chatMemory.add(conversationId, response2.getResult().getOutput());

// Response contains "James Bond"
```

---

## 19. Quick cheat sheet

```java
// 1. Memory (strategy) + repository (storage)
ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(20)
    .build();

// 2. Attach to ChatClient
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
    .build();

// 3. Every call needs conversation ID
chatClient.prompt()
    .user("Hello")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
    .call().content();

// 4. Manual API
chatMemory.add(conversationId, message);
List<Message> msgs = chatMemory.get(conversationId);
```

| Need | Use |
|---|---|
| Quick start | Auto-configured `ChatMemory` (in-memory + window of 20) |
| Persistent, SQL | `JdbcChatMemoryRepository` |
| Fast + TTL + search | `RedisChatMemoryRepository` |
| Huge scale + audit | `CassandraChatMemoryRepository` |
| Documents | `MongoChatMemoryRepository` |
| Graph | `Neo4jChatMemoryRepository` |
| Full audit history | Spring Data (separate), not `ChatMemory` |
| History as messages | `MessageChatMemoryAdvisor` |
| History from vector store | `VectorStoreChatMemoryAdvisor` |
| Tool calls stored | Spring AI Session project / user-controlled tool execution |

---

## 20. Common mistakes

1. Forgetting `ChatMemory.CONVERSATION_ID` → `IllegalArgumentException`.
2. Using a **fixed or shared** conversation ID → users see each other's data (**security issue**).
3. Trusting a **client-sent** conversation ID → derive it on the **server**.
4. Using `ChatMemory` as the **full history/audit log** → old messages get evicted. Use Spring Data for history.
5. Using `InMemoryChatMemoryRepository` in production → lost on restart, not shared across instances.
6. Expecting **tool call messages** to be stored in JDBC/Cassandra/Mongo → they are **silently filtered out**.
7. Setting `maxMessages` **smaller than one turn** (tool-heavy chats) → all non-system messages may be evicted.
8. Thinking `maxMessages` is the exact count → it is an **upper bound** because of turn-boundary eviction.
9. Upgrading JDBC to 2.0.0 without adding the **`sequence_id`** column → ordering/schema problems.
10. Leaving `initialize-schema=embedded` in production with a real DB → schema not auto-created. Use `always` or migration tools (`never` with Flyway/Liquibase).
11. Using Neo4j **invalid label names** (spaces, dashes) → `IllegalArgumentException` at startup.
12. Mongo TTL: changing the value **drops and recreates** the TTL index.
13. Redis: using plain Redis without **Redis Stack** (Query Engine + RedisJSON) → repository will not work.
14. Mixing up advisor `.promptTemplate()` (merges memory) with `ChatClient.templateRenderer()` (renders initial prompt).
15. Manual `ChatModel` memory: forgetting to **save the assistant reply** back to memory.
16. Listing/deleting conversations across **all** IDs instead of the current user's IDs.

---

## 21. Interview quick Q&A

**Q1. Why do we need chat memory?**
LLMs are stateless. Memory stores earlier messages so they can be sent with the next request.

**Q2. `ChatMemory` vs `ChatMemoryRepository`?**
`ChatMemory` decides which messages to keep/remove (strategy). `ChatMemoryRepository` only stores and retrieves messages (storage).

**Q3. Chat memory vs chat history?**
Memory = context the LLM uses (limited). History = every message ever exchanged. Use Spring Data for full history.

**Q4. What is auto-configured by default?**
A `ChatMemory` bean using `MessageWindowChatMemory` and `InMemoryChatMemoryRepository` (unless another repository is configured).

**Q5. What is the default window size?**
20 messages.

**Q6. Are system messages evicted?**
No. `MessageWindowChatMemory` always preserves `SystemMessage`s.

**Q7. What is turn-boundary eviction?**
Eviction removes whole turns (a user message plus the following assistant/tool messages). The window always starts at a complete turn.

**Q8. Is `maxMessages` exact?**
No, it is an upper bound; the real count may be lower.

**Q9. What if `maxMessages` is smaller than one turn?**
All non-system messages may be evicted until a new user message is added.

**Q10. What is a conversation ID and why does it matter?**
It scopes a conversation. Messages are stored/read per ID. It must be unique per user (and conversation) and derived on the server.

**Q11. What happens without `CONVERSATION_ID`?**
`IllegalArgumentException`. No default exists.

**Q12. Which repositories exist?**
InMemory, JDBC, Cassandra, Neo4j, MongoDB, Redis, plus CosmosDB (external module).

**Q13. Which databases does JDBC support?**
PostgreSQL, MySQL/MariaDB, SQL Server, HSQLDB, Oracle (dialect abstraction; extendable).

**Q14. How does JDBC keep message order?**
By the `sequence_id` column (added in 2.0.0). Messages are returned oldest to newest.

**Q15. How to read message creation time with JDBC?**
From message metadata key `JdbcChatMemoryRepository.CONVERSATION_TS` (an `Instant`).

**Q16. Which repositories do not support tool call messages?**
JDBC, Cassandra, and Mongo silently filter them out. Use Spring AI Session (JDBC session store) if you need them.

**Q17. When to use Redis repository?**
When you need low latency, TTL, and advanced search (by content, type, time, metadata). Needs Redis Stack 7.0+.

**Q18. Why is Cassandra good for audit?**
Its time-series schema keeps all past chat windows; combine with TTL (e.g., three years).

**Q19. `MessageChatMemoryAdvisor` vs `VectorStoreChatMemoryAdvisor`?**
First adds history as a list of messages. Second retrieves from a vector store and appends it as plain text to the system message.

**Q20. What placeholders does the vector advisor template need?**
`instructions` (original system message) and `long_term_memory` (retrieved memory).

**Q21. Are tool call intermediate messages stored in ChatClient memory?**
Not currently. Use User Controlled Tool Execution if you need them.

**Q22. How to use memory with `ChatModel` directly?**
Manually: `add` user message → `call(new Prompt(get(id)))` → `add` the assistant reply.

**Q23. How to control JDBC schema creation in production?**
`spring.ai.chat.memory.repository.jdbc.initialize-schema` = `embedded` (default), `always`, or `never` (use Flyway/Liquibase).
