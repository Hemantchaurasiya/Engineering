# Spring AI Tool Search Tool — Effective Notes

> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

1. [Why Tool Search? (the problem)](#1-why-tool-search-the-problem)
2. [What is ToolSearchToolCallingAdvisor?](#2-what-is-toolsearchtoolcallingadvisor)
3. [How it works (step by step flow)](#3-how-it-works-step-by-step-flow)
4. [When to use / when NOT to use](#4-when-to-use--when-not-to-use)
5. [Installation](#5-installation)
6. [Quick start (manual wiring)](#6-quick-start-manual-wiring)
7. [Session scoping](#7-session-scoping)
8. [Search strategies (ToolIndex)](#8-search-strategies-toolindex)
9. [VectorToolIndex (semantic)](#9-vectortoolindex-semantic)
10. [LuceneToolIndex (keyword)](#10-lucenetoolindex-keyword)
11. [RegexToolIndex (pattern)](#11-regextoolindex-pattern)
12. [Builder configuration options](#12-builder-configuration-options)
13. [ToolIndex API (custom index)](#13-toolindex-api-custom-index)
14. [Index eviction strategies](#14-index-eviction-strategies)
15. [Spring Boot auto-configuration](#15-spring-boot-auto-configuration)
16. [Configuration properties reference](#16-configuration-properties-reference)
17. [Example configurations](#17-example-configurations)
18. [Quick cheat sheet](#18-quick-cheat-sheet)
19. [Common mistakes](#19-common-mistakes)
20. [Interview quick Q&A](#20-interview-quick-qa)

---

## 1. Why Tool Search? (the problem)

**Key Points**
- AI agents connect to more services (Slack, GitHub, Jira, MCP servers) → **tool library grows fast**.
- A typical multi-server setup can have **50+ tools using 55,000+ tokens before the conversation even starts**.
- Models also pick the wrong tool more often when there are **30+ similarly named tools**.
- Two bad results:
  1. **High cost** (tokens on every request).
  2. **Lower accuracy** (confusion between similar tools).

**Use Cases**
- Enterprise agent with GitHub + Jira + Slack + DB + internal APIs tools.

**Problem Solved (preview)**
- Tool Search sends tool definitions **only when needed**, not all upfront.

**Simple example**
```
Without tool search: 80 tool definitions sent on EVERY request (~55K tokens)
With tool search   : 1 search tool sent; only 2-3 relevant tools added when needed
```

---

## 2. What is ToolSearchToolCallingAdvisor?

**Key Points**
- A **drop-in replacement** for the default `ToolCallingAdvisor`.
- Uses **progressive tool disclosure**: tool definitions are shown to the model **step by step, on demand**, not all at once.
- Benchmarks (OpenAI, Anthropic, Gemini): **34–64% token reduction** while still allowing access to big tool catalogs.
- It **extends `ToolCallingAdvisor`** and overrides the **initialization hook** and **per-iteration hooks** of the loop.
- Gives the model a built-in tool called **`toolSearchTool`** to find other tools by natural-language query.

**Use Cases**
- Big tool catalogs, multi-MCP-server apps.

**Where to Use**
- Any `ChatClient` with many tools.

**Problem Solved**
- Lower token cost + better tool choice accuracy.

**Think of it like**
- A **library search desk**: instead of putting all books on your table, you ask the desk "I need something about X" and get only the relevant books.

---

## 3. How it works (step by step flow)

**Key Points**
1. **Indexing** — at session start, **all registered tools are indexed** in the configured `ToolIndex`. **No tool definitions are sent to the model.**
2. **Initial request** — the first LLM request contains **only the `toolSearchTool` definition**.
3. **Discovery call** — when the model needs a capability, it calls `toolSearchTool` with a **natural-language query**.
4. **Search & expand** — `ToolIndex` finds matching tools; their definitions are **added to the conversation** for the next iteration.
5. **Tool invocation** — the model, now knowing the tool, makes a **normal tool call**.
6. **Tool execution** — `ToolCallingManager` runs the discovered tool and returns the result.
7. **Response** — the model gives the final answer using the result.
- The indexed tool set is **scoped per session** → concurrent conversations have **isolated indexes**.

```
User: "Help me plan what to wear in Amsterdam"
 → Model sees only [toolSearchTool]
 → Model: toolSearchTool("weather forecast")
 → Index returns: getWeather tool definition
 → Model: getWeather(city="Amsterdam")
 → Result → final answer
```

**Trade-off**: extra model round trip (search step) in exchange for fewer tokens per request.

---

## 4. When to use / when NOT to use

| Use Tool Search when | Stick with default `ToolCallingAdvisor` when |
|---|---|
| **10+ tools** registered | Small tool library (**under 10**) |
| Tool definitions use **> 10K tokens** per request | **All tools are used in every session** |
| **Multi-server MCP** with a big catalog | Tool definitions are **very small** (search round trips cost more than they save) |
| Tool selection accuracy problems with big tool sets | |

**Problem Solved**
- Helps you decide: do not add complexity for small tool sets.

---

## 5. Installation

**Key Points**
- **Option A (simplest)**: Spring Boot starter (includes **Lucene** and **auto-configuration**).
- **Option B**: library directly, for manual configuration.

**Maven**
```xml
<!-- Option A: starter -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-tool-search-advisor</artifactId>
</dependency>

<!-- Option B: library only -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tool-search-advisor</artifactId>
</dependency>
```

---

## 6. Quick start (manual wiring)

**Key Points**
- Steps:
  1. Create a **`ToolIndex`** bean (semantic, keyword, or regex).
  2. Build the advisor with `ToolSearchToolCallingAdvisor.builder()`.
  3. Register it with `ChatClient` as a **default advisor**. Tools are indexed but **not sent up front**.
  4. Send a request **with a session ID** in the advisor context.
- `.maxResults(5)` → at most 5 tools returned per search.

**Java Example**
```java
// 1. Configure a ToolIndex
@Bean
ToolIndex toolIndex(VectorStore vectorStore) {
    return new VectorToolIndex(vectorStore);
}

// 2. Build the advisor
var toolSearchAdvisor = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .maxResults(5)
    .build();

// 3. Register with ChatClient (tools indexed, NOT sent to LLM up front)
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultTools(new MyTools())
    .defaultAdvisors(toolSearchAdvisor)
    .build();

// 4. Make a request with a session ID
String answer = chatClient.prompt("Help me plan what to wear today in Amsterdam")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "user-42-session"))
    .call()
    .content();
```

---

## 7. Session scoping

**Key Points**
- Tools are indexed **per session**. The **session ID** decides which index a request uses → supports **multi-tenant** and **multi-conversation isolation**.
- The caller **must send a session ID with every request**.
- Default key in advisor context: **`ChatMemory.CONVERSATION_ID`**.
- Different key (e.g., `tenantId`, `userId`)? Change it with `sessionIdKeyName(...)` or property.
- If you already use **memory advisors** with conversation IDs (e.g., `MessageChatMemoryAdvisor`), the same key is already there → **session scoping comes for free**.

**Use Cases**
- SaaS app with many tenants; each user chat has its own discovered tools.

**Problem Solved**
- Tools discovered in one conversation do not leak into another.

**Java Example**
```java
// Default key
chatClient.prompt()
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "user-42-session"))
    .user("...")
    .call()
    .content();

// Custom key
var advisor = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .sessionIdKeyName("tenantId")
    .build();

chatClient.prompt()
    .advisors(a -> a.param("tenantId", "acme"))
    .user("...")
    .call()
    .content();
```

---

## 8. Search strategies (ToolIndex)

**Key Points**
- `ToolIndex` is the interface that hides the search method. Three built-in strategies:

| Strategy | Class | Best for |
|---|---|---|
| **Semantic** | `VectorToolIndex` | Natural language, fuzzy matching, new phrasings — caller describes **what they need**, not the tool name |
| **Keyword** | `LuceneToolIndex` | Exact-term matching, fast, known vocabulary |
| **Regex** | `RegexToolIndex` | Tool **name patterns** (e.g., `get_*_data`); lightweight, no dependencies |

**Easy choice**
- Tool names are clean and consistent → **Regex**.
- Need fast keyword search without embeddings → **Lucene**.
- Best matching quality for natural language → **Vector** (needs a `VectorStore` and embedding model).

---

## 9. VectorToolIndex (semantic)

**Key Points**
- Uses **embedding similarity search**.
- Tool **name + description** are embedded when indexed. The `toolSearchTool` query is embedded too, and the **top-K** matches are returned.
- Needs a **`VectorStore` bean** (e.g., `spring-ai-starter-vector-store-pgvector`).

**Use Cases**
- Model says "send a message to my team" → finds `slack_post_message` even though words differ.

**Where to Use**
- Large, varied tool catalogs; many MCP servers.

**Problem Solved**
- Finds tools by **meaning**, not exact words.

**Java Example**
```java
@Bean
ToolIndex vectorToolIndex(VectorStore vectorStore) {
    return new VectorToolIndex(vectorStore);
}
```

---

## 10. LuceneToolIndex (keyword)

**Key Points**
- Uses **Apache Lucene** keyword search. **Fast**, **no embedding model** needed.
- **Minimum score threshold** (default **0.25**): hits below are **silently dropped**.
  - **Raise** → more selective. **Lower** → more permissive.
- Lucene is **bundled in the starter**.

**Use Cases**
- Tools with clear, distinct keywords ("invoice", "refund", "ticket").

**Where to Use**
- When you want good speed and no extra infrastructure.

**Problem Solved**
- Quick keyword matching without vector store cost.

**Java Example**
```java
@Bean
ToolIndex luceneToolIndex() {
    return new LuceneToolIndex();          // default minimum score 0.25
    // return new LuceneToolIndex(0.4f);   // stricter threshold
}
```

---

## 11. RegexToolIndex (pattern)

**Key Points**
- Matches **regex patterns against tool names**.
- Useful when names follow a **strict convention** (e.g., `get_*`, `database`).
- **Zero extra dependencies**.
- It is the **default** when no `tool-index-type` is set.

**Use Cases**
- Internal tools named like `crm_get_customer`, `crm_update_customer`.

**Where to Use**
- Simple setups, tests, naming-convention-based catalogs.

**Problem Solved**
- Lightweight discovery with no extra setup.

**Java Example**
```java
@Bean
ToolIndex regexToolIndex() {
    return new RegexToolIndex();
}
```

---

## 12. Builder configuration options

**Key Points**
- `ToolSearchToolCallingAdvisor.Builder` extends `ToolCallingAdvisor.Builder` (so it also has inherited options) and adds:

| Option | Meaning | Default |
|---|---|---|
| `toolIndex(ToolIndex)` | Search implementation | **Required** |
| `maxResults(Integer)` | Max tool references per `toolSearchTool` call. `null` = model decides (tool description hints at 5) | `null` |
| `systemMessageSuffix(String)` | Custom text added to the system message to teach the model how to use `toolSearchTool` | Built-in template |
| `referenceToolNameAccumulation(boolean)` | `true`: keep tools found in **all earlier** searches. `false`: only the **latest turn** (including parallel searches in it) | `true` |
| `sessionIdKeyName(String)` | Advisor context key for the session ID | `ChatMemory.CONVERSATION_ID` |
| `evictionStrategy(...)` | When to free session indexes | `LruEvictionStrategy(1000)` |

**Tips**
- `referenceToolNameAccumulation = true` (default): safer, tools found earlier stay available. Costs a bit more tokens as the conversation grows.
- `false`: fewer tokens, but the model may need to search again.

**Java Example**
```java
var advisor = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .maxResults(5)
    .referenceToolNameAccumulation(true)
    .sessionIdKeyName("tenantId")
    .systemMessageSuffix("Use toolSearchTool to find tools before calling them.")
    .build();
```

---

## 13. ToolIndex API (custom index)

**Key Points**
- `ToolIndex` and companion types (`ToolSearchRequest`, `ToolSearchResponse`, `ToolReference`) are in module **`spring-ai-tool-search-tool`**, package `org.springframework.ai.tool.toolsearch`. Built-in implementations are there too.
- **Every operation is scoped by `sessionId`.**
- Implement it yourself for **custom search**: e.g., **database-backed catalog with role-based filtering**, or **cached remote tool registry**.

```java
public interface ToolIndex {

    void indexTool(String sessionId, ToolReference toolReference);

    /** Default implementation loops over indexTool. */
    void indexTools(String sessionId, List<ToolReference> toolReferences);

    ToolSearchResponse search(ToolSearchRequest request);

    void clearIndex(String sessionId);
}
```

**Dependency**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tool-search-tool</artifactId>
</dependency>
```

**Use Cases**
- Show tools only if the user has the right **role** (security + fewer tokens).

**Java Example (idea only)**
```java
public class RoleAwareToolIndex implements ToolIndex {

    private final ToolIndex delegate;       // e.g., LuceneToolIndex
    private final RoleService roleService;

    public RoleAwareToolIndex(ToolIndex delegate, RoleService roleService) {
        this.delegate = delegate;
        this.roleService = roleService;
    }

    @Override
    public void indexTool(String sessionId, ToolReference ref) {
        delegate.indexTool(sessionId, ref);
    }

    @Override
    public void indexTools(String sessionId, List<ToolReference> refs) {
        refs.forEach(r -> indexTool(sessionId, r));
    }

    @Override
    public ToolSearchResponse search(ToolSearchRequest request) {
        ToolSearchResponse response = delegate.search(request);
        // filter results using roleService here (check ToolSearchResponse API in your version)
        return response;
    }

    @Override
    public void clearIndex(String sessionId) {
        delegate.clearIndex(sessionId);
    }
}
```
> This is a sketch. The exact fields of `ToolSearchRequest` / `ToolSearchResponse` are not in your document, so check the Javadoc before building it.

---

## 14. Index eviction strategies

**Key Points**
- Each session index **uses memory**. `ToolIndexEvictionStrategy` decides **when to free** it.
- Default: **`LruEvictionStrategy(1000)`** → keep up to **1,000** active sessions; evict the **least recently used** when over the cap.
- Release a session **early** with `advisor.evictSession(sessionId)` (e.g., on **logout**).
- Eviction is checked **lazily on each request** — **no background thread**.

| Strategy | Behavior |
|---|---|
| `LruEvictionStrategy(maxSessions)` (default) | Evict least-recently-used when sessions exceed the cap |
| `NeverEvictStrategy.INSTANCE` | Never evict automatically; only when you call `evictSession()` |
| `AlwaysEvictStrategy.INSTANCE` | Clear the session index **before every request** (full re-index each turn). For tests or tool sets that change every request |
| `TtlEvictionStrategy(duration)` | Evict sessions **idle longer than** the TTL |
| `CompositeEvictionStrategy(...)` | Combine strategies; evict if **any** says evict |

**Use Cases**
- Chat apps with many users: TTL + LRU cap to bound memory.
- Tools change per request: `AlwaysEvictStrategy`.

**Problem Solved**
- Prevents memory growth from many sessions.

**Java Example**
```java
// Default: LRU cap of 1000 sessions
var advisor = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .build();

// Never evict (you manage lifetime)
var neverEvict = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .evictionStrategy(NeverEvictStrategy.INSTANCE)
    .build();

// Always evict (testing)
var alwaysEvict = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .evictionStrategy(AlwaysEvictStrategy.INSTANCE)
    .build();

// LRU with custom cap
var lru = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .evictionStrategy(new LruEvictionStrategy(200))
    .build();

// Evict idle sessions after 30 minutes
var ttl = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .evictionStrategy(new TtlEvictionStrategy(Duration.ofMinutes(30)))
    .build();

// Combine: TTL + LRU
var combined = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .evictionStrategy(new CompositeEvictionStrategy(
        new TtlEvictionStrategy(Duration.ofMinutes(30)),
        new LruEvictionStrategy(200)))
    .build();

// Release a session early (e.g., on logout)
toolSearchAdvisor.evictSession("user-42-session");
```

---

## 15. Spring Boot auto-configuration

**Key Points**
- Starter `spring-ai-starter-tool-search-advisor` gives **zero-boilerplate** setup. Enable with one property:
  ```properties
  spring.ai.chat.client.tool-search-advisor.enabled=true
  ```
- When enabled, auto-config:
  1. Registers a `ToolSearchToolCallingAdvisor.Builder` bean typed as `ToolCallingAdvisor.Builder<?>`. It **replaces the default** `ToolCallingAdvisor` (because the default builder has `@ConditionalOnMissingBean`). **No ChatClient code changes needed.**
  2. **Auto-registers a `ToolIndex` bean** unless you define your own.
- **`tool-index-type`** chooses the index:

| Value | Implementation | Needs |
|---|---|---|
| `regex` (default) | `RegexToolIndex` | Nothing extra |
| `lucene` | `LuceneToolIndex` | `lucene-core` on classpath (bundled in starter) |
| `vector` | `VectorToolIndex` | A `VectorStore` bean |

- **Your own `ToolIndex` bean always wins** (`@ConditionalOnMissingBean`).

**Problem Solved**
- Turn on tool search without touching existing code.

**Java Example**
```java
// Existing code stays the same. Just enable the property.
@Service
class AssistantService {
    private final ChatClient chatClient;

    AssistantService(ChatClient.Builder builder, MyTools tools) {
        this.chatClient = builder.defaultTools(tools).build();   // advisor auto-applied
    }

    String ask(String q, String sessionId) {
        return chatClient.prompt(q)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))   // don't forget this
            .call()
            .content();
    }
}
```

---

## 16. Configuration properties reference

Prefix: `spring.ai.chat.client.tool-search-advisor.`

| Property | Meaning | Default |
|---|---|---|
| `enabled` | Enable advisor (replaces default `ToolCallingAdvisor`) | `false` |
| `tool-index-type` | `regex`, `lucene`, or `vector` | `regex` |
| `max-results` | Max tool references per search call (`null` = built-in default) | `null` |
| `system-message-suffix` | Custom prompt suffix (`null` = built-in) | `null` |
| `reference-tool-name-accumulation` | `true`: accumulate tool names over all searches; `false`: only latest turn | `true` |
| `session-id-key-name` | Advisor context key for session ID | `chat_memory_conversation_id` |
| `advisor-order` | Position in advisor chain | `HIGHEST_PRECEDENCE + 300` |
| `eviction.lru-max-sessions` | Max active sessions for LRU | `1000` |
| `eviction.ttl` | Idle session TTL. If set, **composite LRU+TTL** is used. Duration string like `30m`, `1h` | `null` |
| `lucene.min-score-threshold` | Min Lucene score for a hit (when `tool-index-type=lucene`) | `0.25` |

---

## 17. Example configurations

**Lucene with custom threshold and TTL eviction**
```properties
spring.ai.chat.client.tool-search-advisor.enabled=true
spring.ai.chat.client.tool-search-advisor.tool-index-type=lucene
spring.ai.chat.client.tool-search-advisor.lucene.min-score-threshold=0.4
spring.ai.chat.client.tool-search-advisor.eviction.ttl=30m
```

**Vector search (needs a `VectorStore` bean)**
```properties
spring.ai.chat.client.tool-search-advisor.enabled=true
spring.ai.chat.client.tool-search-advisor.tool-index-type=vector
```

**Custom session-ID key for multi-tenant**
```properties
spring.ai.chat.client.tool-search-advisor.enabled=true
spring.ai.chat.client.tool-search-advisor.tool-index-type=vector
spring.ai.chat.client.tool-search-advisor.session-id-key-name=tenantId
```

---

## 18. Quick cheat sheet

```java
// Manual
var advisor = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(new LuceneToolIndex())
    .maxResults(5)
    .evictionStrategy(new TtlEvictionStrategy(Duration.ofMinutes(30)))
    .build();

ChatClient client = ChatClient.builder(chatModel)
    .defaultTools(new MyTools())
    .defaultAdvisors(advisor)
    .build();

client.prompt("...")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "session-1"))   // required
    .call().content();
```

| Need | Use |
|---|---|
| Enable quickly | `spring.ai.chat.client.tool-search-advisor.enabled=true` |
| Natural language tool search | `vector` / `VectorToolIndex` |
| Fast keyword search, no embeddings | `lucene` / `LuceneToolIndex` |
| Name-pattern search, no deps | `regex` / `RegexToolIndex` (default) |
| Limit tools per search | `maxResults(n)` |
| Different session key | `sessionIdKeyName("tenantId")` |
| Bound memory | LRU / TTL / Composite eviction |
| Free session on logout | `advisor.evictSession(id)` |
| Custom search logic | Implement `ToolIndex` |

| Strategy | Setup | Speed | Match quality |
|---|---|---|---|
| Regex | Easiest | Fast | Name patterns only |
| Lucene | Easy | Fast | Keywords |
| Vector | Needs VectorStore + embeddings | Slower | Best for meaning |

---

## 19. Common mistakes

1. **No session ID** in the request → the advisor cannot find the right tool index. Always send it (default key `ChatMemory.CONVERSATION_ID`).
2. Using Tool Search with **very few tools** (under 10) → extra search round trips cost more than they save.
3. Choosing **`vector`** type **without a `VectorStore` bean** → startup/config failure.
4. **Lucene threshold too high** → relevant tools silently dropped (model cannot find tools). Too low → noisy results.
5. **Regex index with messy names** → patterns miss tools. Use consistent naming or switch to Lucene/Vector.
6. **Same session ID for all users** → shared index and mixed context. Use per-user/per-conversation IDs.
7. Never evicting sessions (`NeverEvictStrategy`) in a busy app → **memory growth**.
8. **`AlwaysEvictStrategy` in production** → re-indexing on every request (slow). Use for tests only.
9. Forgetting that **`toolSearchTool` adds an extra model round trip**.
10. Setting `reference-tool-name-accumulation=false` and then expecting earlier discovered tools to remain available.
11. Using a **different session key** in memory vs advisor (e.g., `userId` vs `conversationId`) without setting `sessionIdKeyName`.
12. Registering a **second tool advisor** (only one `ToolAdvisor` allowed; the tool search advisor already replaces the default).

---

## 20. Interview quick Q&A

**Q1. Why do we need Tool Search?**
Large tool libraries waste tokens (50+ tools can use 55K+ tokens before the chat starts) and reduce tool selection accuracy (30+ similar tools).

**Q2. What is `ToolSearchToolCallingAdvisor`?**
A replacement for the default `ToolCallingAdvisor` that uses progressive tool disclosure: it sends only a `toolSearchTool` first and adds relevant tool definitions on demand.

**Q3. How much does it save?**
Benchmarks across OpenAI, Anthropic, and Gemini show 34–64% token reduction.

**Q4. Walk through the flow.**
Index all tools → send only `toolSearchTool` → model calls it with a natural-language query → index returns matches → definitions added → model calls the real tool → `ToolCallingManager` executes → final answer.

**Q5. What does the advisor extend and override?**
It extends `ToolCallingAdvisor` and overrides the initialization and per-iteration hooks of the loop.

**Q6. When should you use it?**
10+ tools, 10K+ tokens of definitions, big MCP catalogs, or tool-selection accuracy problems. Not for small tool sets.

**Q7. What is session scoping?**
Tool indexes are per session, using a session ID from the advisor context (default `ChatMemory.CONVERSATION_ID`). It isolates conversations and tenants.

**Q8. How to change the session key?**
`sessionIdKeyName("tenantId")` or property `session-id-key-name`.

**Q9. Name the three `ToolIndex` strategies.**
`VectorToolIndex` (semantic), `LuceneToolIndex` (keyword), `RegexToolIndex` (pattern). Regex is the default.

**Q10. What does `maxResults` do?**
Limits tool references returned per `toolSearchTool` call. `null` lets the model decide (hint of 5).

**Q11. What does `referenceToolNameAccumulation` do?**
`true` (default): remember tools found in all earlier searches. `false`: only the latest turn.

**Q12. What is the default eviction strategy?**
`LruEvictionStrategy(1000)`: keeps 1,000 sessions, evicts least recently used.

**Q13. List the eviction strategies.**
LRU, Never, Always, TTL, Composite.

**Q14. How is eviction evaluated?**
Lazily on each request, no background thread.

**Q15. How to free a session immediately?**
`advisor.evictSession(sessionId)`, for example on logout.

**Q16. How to enable it in Spring Boot?**
`spring.ai.chat.client.tool-search-advisor.enabled=true` with the starter. It registers a `ToolCallingAdvisor.Builder<?>` bean that replaces the default and auto-registers a `ToolIndex`.

**Q17. What if I define my own `ToolIndex` bean?**
It takes precedence; auto-configuration backs off (`@ConditionalOnMissingBean`).

**Q18. What is the default `tool-index-type`?**
`regex`.

**Q19. What does the Lucene `min-score-threshold` do?**
Hits below the score (default 0.25) are silently dropped. Raise for stricter, lower for broader results.

**Q20. How would you build a role-based tool catalog?**
Implement `ToolIndex` yourself (database-backed catalog with role filtering) and register it as a bean.

**Q21. What is the trade-off of tool search?**
Fewer tokens and better accuracy, but an extra round trip for discovery.
