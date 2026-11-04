# Spring AI Advisors API — Effective Notes

> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

1. [What is the Advisors API?](#1-what-is-the-advisors-api)
2. [Registering advisors (defaultAdvisors vs runtime)](#2-registering-advisors)
3. [Core components](#3-core-components)
4. [Advisor chain and request/response flow](#4-advisor-chain-and-requestresponse-flow)
5. [Advisor order (stack behavior)](#5-advisor-order-stack-behavior)
6. [API overview (interfaces)](#6-api-overview-interfaces)
7. [Implementing a custom advisor](#7-implementing-a-custom-advisor)
8. [Example: Logging advisor](#8-example-logging-advisor)
9. [Example: Re-Reading (Re2) advisor](#9-example-re-reading-re2-advisor)
10. [Built-in advisors](#10-built-in-advisors)
11. [Streaming vs non-streaming advisors](#11-streaming-vs-non-streaming-advisors)
12. [Advisor context (sharing state)](#12-advisor-context-sharing-state)
13. [Best practices](#13-best-practices)
14. [Breaking API changes (version history)](#14-breaking-api-changes-version-history)
15. [Quick cheat sheet](#15-quick-cheat-sheet)
16. [Common mistakes](#16-common-mistakes)
17. [Interview quick Q&A](#17-interview-quick-qa)

---

## 1. What is the Advisors API?

**Key Points**
- Advisors **intercept, modify, and enhance** every AI call made through `ChatClient`.
- Think of them like **servlet filters / Spring AOP / interceptors**, but for AI requests and responses.
- Main benefits:
  - **Encapsulate repeated GenAI patterns** (memory, RAG, safety, logging).
  - **Transform data** going to and coming from the LLM.
  - **Portable** across different models and use cases.
- Advisors also join the **Observability** stack, so you get metrics and traces for them.

**Use Cases**
- Chat memory, RAG (search your own docs), logging, content safety, prompt improvement, tool calling, caching, rate limiting.

**Where to Use**
- Any logic that should apply to **many AI calls**, not just one.

**Problem Solved**
- Without advisors you copy the same code (add history, search docs, log, filter) into every controller/service. Advisors keep that logic in one reusable place.

**Java Example**
```java
// Idea: ChatClient + advisors = reusable AI behavior
var chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(new SimpleLoggerAdvisor())
    .build();
```

---

## 2. Registering advisors

**Key Points**
- **Recommended**: register at **build time** with `defaultAdvisors(...)` on the builder.
- **Runtime parameters** (like conversation ID) are passed per call using `.advisors(a -> a.param(...))`.
- You can also add advisors per request with `.advisors(...)`.
- `ChatMemory.CONVERSATION_ID` is passed at runtime so memory knows which conversation to use.

**Use Cases**
- Memory + RAG for every call (defaults), with a different `conversationId` per user (runtime).

**Where to Use**
- `@Configuration` class for defaults; controller/service for runtime params.

**Problem Solved**
- Separates **what is always on** (config) from **what changes per request** (params).

**Java Example**
```java
ChatMemory chatMemory = ...;      // your chat memory store
VectorStore vectorStore = ...;    // your vector store

var chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build(), // chat-memory advisor
        QuestionAnswerAdvisor.builder(vectorStore).build()    // RAG advisor
    )
    .build();

var conversationId = "678";

String response = chatClient.prompt()
    // set advisor parameters at runtime
    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
    .user(userText)
    .call()
    .content();
```

---

## 3. Core components

**Key Points**
| Component | Purpose |
|---|---|
| `CallAdvisor` + `CallAdvisorChain` | **Non-streaming** (normal `call()`) |
| `StreamAdvisor` + `StreamAdvisorChain` | **Streaming** (`stream()`, uses `Flux`) |
| `ChatClientRequest` | Represents the **unsealed Prompt** request (you can modify it) |
| `ChatClientResponse` | Represents the chat completion response |
| **Advise context** | A map inside request/response used to **share state** across the chain |

- Key methods:
  - `adviseCall()` / `adviseStream()` — do the work.
  - `getOrder()` — decides position in chain.
  - `getName()` — unique name of the advisor.
- What an advisor can do in `adviseCall` / `adviseStream`:
  - Look at the Prompt data.
  - Change or add to the Prompt.
  - Call the **next** advisor in the chain.
  - **Block** the request (do not call next).
  - Look at or change the response.
  - **Throw exceptions** to signal errors.

**Use Cases**
- Understanding which interface to implement for your need.

**Where to Use**
- When designing a custom advisor.

**Problem Solved**
- Clear separation of sync and reactive flows, and a standard way to pass data.

**Java Example**
```java
// Skeleton: what an advisor can do
public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    // 1. examine/modify request
    // 2. call next advisor:  chain.nextCall(request)
    // 3. examine/modify response
    // 4. return response
    return chain.nextCall(request);
}
```

---

## 4. Advisor chain and request/response flow

**Key Points**
- The **Advisor Chain** is created by Spring AI. It runs advisors **one by one**, sorted by `getOrder()` (**lower value runs first**).
- The **last advisor** is added automatically by the framework. It sends the request to the **Chat Model (LLM)**.
- Flow step by step:
  1. Spring AI builds a `ChatClientRequest` from your Prompt + an **empty advisor context**.
  2. Each advisor processes (and may change) the request.
  3. An advisor can **block** the request by **not calling next**. Then it **must fill the response itself**.
  4. The final framework advisor sends the request to the Chat Model.
  5. The model's response goes **back through the chain** and becomes `ChatClientResponse` (with the shared context).
  6. Each advisor can process/modify the response.
  7. The final `ChatClientResponse` goes back to the client (completion is extracted).

```
Request  →  Advisor A → Advisor B → Advisor C → [Chat Model]
Response ←  Advisor A ← Advisor B ← Advisor C ←
```

**Use Cases**
- **Block**: a safety advisor refuses a harmful question without paying for an LLM call.
- **Cache**: return a saved answer without calling the LLM.

**Where to Use**
- Cost saving, security, caching, validation.

**Problem Solved**
- Control and customize the AI call pipeline without changing application code.

**Java Example (blocking advisor idea)**
```java
public class BlockBadWordsAdvisor implements CallAdvisor {

    @Override public String getName() { return "BlockBadWordsAdvisor"; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String text = request.prompt().getUserMessage().getText();
        if (text.toLowerCase().contains("password dump")) {
            // DO NOT call chain.nextCall(...) -> LLM is never called.
            // We are now responsible for building the response.
            ChatResponse blocked = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("Request blocked."))))
                .build();
            return ChatClientResponse.builder()
                .chatResponse(blocked)
                .context(request.context())
                .build();
        }
        return chain.nextCall(request);
    }
}
```

---

## 5. Advisor order (stack behavior)

**Key Points**
- Order is decided by `getOrder()`.
- **Lower value = executes first** (higher priority).
- The chain works like a **stack**:
  - First advisor processes the **request first**.
  - But it processes the **response last**.
- Use constants:
  - `Ordered.HIGHEST_PRECEDENCE` (`Integer.MIN_VALUE`) → **first** on request, **last** on response.
  - `Ordered.LOWEST_PRECEDENCE` (`Integer.MAX_VALUE`) → **last** on request, **first** on response.
- **Same order value** → execution order **not guaranteed**. Always give different values.
- Need to be first on **both** input and output? Use **two advisors** with different order values and share state via the **advisor context**.

**Visual (order values 0, 100, 200)**
```
Request :  A(0) → B(100) → C(200) → LLM
Response:  A(0) ← B(100) ← C(200) ← LLM
```
So A is first for request and last for response.

**Use Cases**
- Memory must run **before** RAG so RAG can search using history.
- Logger near the end to log the **final** prompt; or at the start to log the **original** request.
- Safety advisor first so bad input stops early.

**Where to Use**
- Every time you have 2+ advisors.

**Problem Solved**
- Wrong order gives wrong results (e.g., RAG searching without chat history, logger not showing modified prompt).

**Java Example**
```java
public class FirstAdvisor implements CallAdvisor {
    @Override public String getName() { return "FirstAdvisor"; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; } // runs first on request

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        System.out.println("A: before");
        ChatClientResponse res = chain.nextCall(req);
        System.out.println("A: after");   // prints LAST
        return res;
    }
}
```

---

## 6. API overview (interfaces)

**Key Points**
- Package: `org.springframework.ai.chat.client.advisor.api`.
- All advisors extend `Ordered`, so they have `getOrder()`.

```java
public interface Advisor extends Ordered {
    String getName();
}

public interface CallAdvisor extends Advisor {
    ChatClientResponse adviseCall(
        ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain);
}

public interface StreamAdvisor extends Advisor {
    Flux<ChatClientResponse> adviseStream(
        ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain);
}

public interface CallAdvisorChain extends AdvisorChain {
    ChatClientResponse nextCall(ChatClientRequest chatClientRequest);   // go to next advisor
    List<CallAdvisor> getCallAdvisors();                                // all advisors in chain
}

public interface StreamAdvisorChain extends AdvisorChain {
    Flux<ChatClientResponse> nextStream(ChatClientRequest chatClientRequest);
    List<StreamAdvisor> getStreamAdvisors();
}
```

**Easy memory trick**
- `CallAdvisor` → `adviseCall` → `chain.nextCall(...)` → returns `ChatClientResponse`.
- `StreamAdvisor` → `adviseStream` → `chain.nextStream(...)` → returns `Flux<ChatClientResponse>`.

**Use Cases / Where to Use**
- When writing your own advisor.

**Problem Solved**
- Gives a clear contract to plug custom logic into the AI pipeline.

---

## 7. Implementing a custom advisor

**Key Points**
- Implement `CallAdvisor` (non-streaming), `StreamAdvisor` (streaming), or **both**.
- Always do these 3 things:
  1. Provide `getName()` (unique name).
  2. Provide `getOrder()`.
  3. In `adviseCall` / `adviseStream`: do **before** work → call **next** → do **after** work.
- Easier option: `BaseAdvisor` gives two simple hooks: `before(request, chain)` and `after(response, chain)` (used in the Re2 example below).
- Do **not** forget to call `nextCall` / `nextStream`, unless you want to **block** on purpose.

**Use Cases**
- Add timing metrics, auditing, prompt rewrite, guardrails, caching, PII masking.

**Where to Use**
- Any custom rule your company needs around AI calls.

**Problem Solved**
- Plug your own logic into AI calls in a clean, reusable, testable class.

**Java Example (timing advisor)**
```java
public class TimingAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TimingAdvisor.class);

    @Override public String getName() { return "TimingAdvisor"; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 100; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);     // go to next advisor
        log.info("AI call took {} ms", System.currentTimeMillis() - start);
        return response;
    }
}
```

---

## 8. Example: Logging advisor

**Key Points**
- **Observes only**: logs request before `next`, logs response after `next`. Does **not** modify anything.
- Implements **both** `CallAdvisor` and `StreamAdvisor` → works for sync and streaming.
- Order is `0`, name is class name.
- For streaming it uses **`ChatClientMessageAggregator`**:
  - Combines the `Flux` of chunks into **one** `ChatClientResponse` so you can log the **whole** answer.
  - It is **read-only** — you **cannot** change the response with it.

**Use Cases**
- Debug final prompts, audit AI usage, troubleshoot strange answers.

**Where to Use**
- Dev/test; carefully in production (do not log secrets/PII).

**Problem Solved**
- Shows exactly what was sent to and received from the model.

**Java Example**
```java
public class SimpleLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SimpleLoggerAdvisor.class);

    @Override
    public String getName() {
        return this.getClass().getSimpleName();      // unique name
    }

    @Override
    public int getOrder() {
        return 0;                                     // lower runs first
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest,
                                         CallAdvisorChain callAdvisorChain) {
        logRequest(chatClientRequest);                                          // before
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        logResponse(response);                                                  // after
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
                                                 StreamAdvisorChain streamAdvisorChain) {
        logRequest(chatClientRequest);
        Flux<ChatClientResponse> responses = streamAdvisorChain.nextStream(chatClientRequest);
        // join all chunks into one response, then log it (read-only)
        return new ChatClientMessageAggregator()
                .aggregateChatClientResponse(responses, this::logResponse);
    }

    private void logRequest(ChatClientRequest request) {
        logger.debug("request: {}", request);
    }

    private void logResponse(ChatClientResponse response) {
        logger.debug("response: {}", response);
    }
}
```

---

## 9. Example: Re-Reading (Re2) advisor

**Key Points**
- **Re2 technique** (from the paper *"Re-Reading Improves Reasoning in Large Language Models"*): repeat the question inside the prompt so the model reads it twice.
- Prompt format:
  ```
  {Input_Query}
  Read the question again: {Input_Query}
  ```
- Implemented with **`BaseAdvisor`**:
  - `before(...)` → changes the user message (augments it).
  - `after(...)` → here just returns the response unchanged.
- Uses `PromptTemplate` to render the template, and `request.mutate()` to create a **new modified request** (requests are not edited in place).
- Order can be set using `withOrder(...)`.

**Use Cases**
- Improve answers for **reasoning / logic / math** questions without changing app code.
- Any "prompt improvement" strategy that you want to apply automatically.

**Where to Use**
- When answer quality matters more than a few extra input tokens.

**Problem Solved**
- Better reasoning from the LLM with a tiny prompt change, applied in one place only.

**Cost note**: the question is sent twice, so **input tokens increase**.

**Java Example**
```java
public class ReReadingAdvisor implements BaseAdvisor {

    private static final String DEFAULT_RE2_ADVISE_TEMPLATE = """
            {re2_input_query}
            Read the question again: {re2_input_query}
            """;

    private final String re2AdviseTemplate;
    private int order = 0;

    public ReReadingAdvisor() {
        this(DEFAULT_RE2_ADVISE_TEMPLATE);
    }

    public ReReadingAdvisor(String re2AdviseTemplate) {
        this.re2AdviseTemplate = re2AdviseTemplate;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String augmentedUserText = PromptTemplate.builder()
            .template(this.re2AdviseTemplate)
            .variables(Map.of("re2_input_query",
                    chatClientRequest.prompt().getUserMessage().getText()))
            .build()
            .render();

        return chatClientRequest.mutate()                                    // new request
            .prompt(chatClientRequest.prompt().augmentUserMessage(augmentedUserText))
            .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;      // no change to response
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public ReReadingAdvisor withOrder(int order) {
        this.order = order;
        return this;
    }
}

// Usage
String answer = chatClient.prompt()
    .advisors(new ReReadingAdvisor())
    .user("If a train travels 60 km in 45 minutes, what is its speed in km/h?")
    .call()
    .content();
```

---

## 10. Built-in advisors

### 10.1 Chat Memory advisors

| Advisor | How it works | Best for |
|---|---|---|
| `MessageChatMemoryAdvisor` | Loads memory and adds it as a **list of messages** in the prompt. Keeps the real conversation structure. **Not all models support this.** | Normal chatbots |
| `VectorStoreChatMemoryAdvisor` | Gets memory from a **VectorStore** and adds it into the **system text** of the prompt. | Large history; search only relevant parts |

**Use Cases**: support chat, personal assistants, long-running conversations.
**Problem Solved**: LLMs are stateless; these advisors bring history back into the prompt.

```java
// Message-based memory
var advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

String reply = chatClient.prompt()
    .advisors(advisorSpec -> advisorSpec
        .advisors(advisor)
        .param(ChatMemory.CONVERSATION_ID, "user-42"))
    .user("Remember: my favourite language is Java")
    .call()
    .content();
```

### 10.2 Question Answering (RAG) advisors

| Advisor | Notes |
|---|---|
| `QuestionAnswerAdvisor` | Uses a **vector store** to answer questions. Implements **Naive RAG**. Simple and quick to start. |
| `RetrievalAugmentationAdvisor` | Builds common RAG flows from building blocks in `org.springframework.ai.rag`, following the **Modular RAG architecture**. More flexible and advanced. |

**Use Cases**: chat with company docs, HR/policy bot, product manual Q&A, internal knowledge base.
**Problem Solved**: model does not know your private/latest data → advisor fetches relevant chunks and adds them to the prompt (reduces made-up answers).

```java
String answer = chatClient.prompt()
    .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
    .user("What is our leave policy for new joiners?")
    .call()
    .content();
```

### 10.3 Reasoning advisor

- `ReReadingAdvisor` — Re2 technique to improve reasoning (see Section 9).

### 10.4 Tool calling advisor

**Key Points**
- `ToolCallingAdvisor` handles the **tool calling loop** inside the advisor chain.
- **Auto-registered** by `ChatClient` (unless disabled), so tools added at runtime by another advisor also work.
- Runs the tools the model asks for, sends results back, and **repeats until no more tool calls** are needed.
- Implements `ToolAdvisor` (marker interface) → prevents a **second** `ToolCallingAdvisor` from being auto-registered.

**Use Cases**: DB lookups, API calls, calculations, creating tickets.
**Problem Solved**: model gets live data and can act, without you writing the loop.

```java
class OrderTools {
    @Tool(description = "Get order status by order id")
    String getOrderStatus(String orderId) {
        return "Order " + orderId + " is SHIPPED";
    }
}

String reply = chatClient.prompt()
    .user("Where is my order 1001?")
    .tools(new OrderTools())          // ToolCallingAdvisor auto-registered
    .call()
    .content();
```

### 10.5 Content safety advisor

- `SafeGuardAdvisor` — simple advisor to stop the model from producing **harmful or inappropriate** content.

**Use Cases**: public chatbots, kids/education apps, customer-facing bots.
**Problem Solved**: basic content protection without custom code.

```java
var chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(new SafeGuardAdvisor(List.of("forbidden-word-1", "forbidden-word-2")))
    .build();
```
> Check the Javadoc for the exact constructor/builder in your Spring AI version.

---

## 11. Streaming vs non-streaming advisors

**Key Points**
- **Non-streaming** (`CallAdvisor`): works with the **complete** request and response.
- **Streaming** (`StreamAdvisor`): works with **continuous streams** using reactive `Flux`.
- In streaming advisors, the "before" part runs before `chain.nextStream(...)`, the "after" part runs on each response item after.
- Use `publishOn(Schedulers.boundedElastic())` if your "before" logic may **block**, so you do not block a reactive thread.
- `ChatClientMessageAggregator` joins stream chunks into one response (read-only), handy for logging.

**Use Cases**
- Streaming chat UI with memory/logging/safety still working.

**Where to Use**
- Any app using `.stream()`. If your advisor must work for both `call()` and `stream()`, implement **both** interfaces.

**Problem Solved**
- Same advisor logic works for both sync and reactive flows.

**Java Example**
```java
@Override
public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
                                             StreamAdvisorChain chain) {

    return Mono.just(chatClientRequest)
        .publishOn(Schedulers.boundedElastic())     // safe place for blocking work
        .map(request -> {
            // "before" section (can run on blocking or non-blocking threads)
            return request;
        })
        .flatMapMany(request -> chain.nextStream(request))   // call next advisor
        .map(response -> {
            // "after" section (runs on each streamed item)
            return response;
        });
}
```

---

## 12. Advisor context (sharing state)

**Key Points**
- `ChatClientRequest` and `ChatClientResponse` both hold an **advise context** (a Map).
- Use it to **share data between advisors** (e.g., RAG advisor stores retrieved documents; your app reads them from `chatClientResponse().context()`).
- **Context map is immutable** (since 1.0 M3). Do **not** try to `put()` into it.
  - Use `updateContext(...)` → creates a **new unmodifiable map** with updated values.
- Useful for the "first on input and output" case: two advisors, different orders, share state via context.

**Use Cases**
- Pass a start time from "before" advisor to "after" advisor.
- Pass retrieved RAG documents to the final response.
- Pass tenantId/userId across advisors.

**Where to Use**
- When one advisor needs to tell another advisor something.

**Problem Solved**
- Advisors stay independent but can still cooperate.

**Java Example**
```java
// Reading context from the final response (e.g., RAG documents)
ChatClientResponse resp = chatClient.prompt()
    .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
    .user("What is our refund policy?")
    .call()
    .chatClientResponse();

Map<String, Object> ctx = resp.context();        // read-only view
ctx.forEach((k, v) -> System.out.println(k + " -> " + v));

// Creating a modified request with extra context (inside an advisor)
ChatClientRequest updated = request.mutate()
    .context("startTime", System.nanoTime())     // builds a NEW request with new context
    .build();
```
> The exact context helper methods can differ by version; the rule to remember is: **context is immutable, create a new one to change it.**

---

## 13. Best practices

1. **Keep advisors focused** — one job each (single responsibility). Easier to test and reuse.
2. **Use advise context** to share state between advisors when needed.
3. **Implement both** streaming and non-streaming versions for maximum flexibility.
4. **Think about order carefully** — data flow depends on it (memory → RAG → logger, etc.).
5. Register advisors at **build time** with `defaultAdvisors()`; pass only changing values (like conversation ID) at runtime.
6. Give different order values — same values give **unpredictable** order.
7. Do not log sensitive data in production.
8. Do not forget `chain.nextCall()` / `chain.nextStream()` unless blocking on purpose.

**Recommended order example**
```
SafeGuard (very early) → Memory → RAG → ReRead → Logger → [ToolCalling / LLM]
```

---

## 14. Breaking API changes (version history)

| Version | Change |
|---|---|
| **1.0 M2** | Separate `RequestAdvisor` (before model call) and `ResponseAdvisor` (after). Context map was a **separate method argument** and was **mutable**. |
| **1.0 M3** | Replaced by `CallAroundAdvisor` and `StreamAroundAdvisor`. `StreamResponseMode` **removed**. Context map moved **inside** `AdvisedRequest` / `AdvisedResponse` and became **immutable** (use `updateContext`). |
| **1.0.0** | `CallAroundAdvisor` → **`CallAdvisor`**, `StreamAroundAdvisor` → **`StreamAdvisor`**, `CallAroundAdvisorChain` → **`CallAdvisorChain`**, `StreamAroundAdvisorChain` → **`StreamAdvisorChain`**. `AdvisedRequest` → **`ChatClientRequest`**, `AdvisedResponse` → **`ChatClientResponse`**. |

**Why it matters**
- Old blog posts and tutorials may use the **old names**. If code does not compile, check the version and use the 1.0.0 names.

---

## 15. Quick cheat sheet

```java
// Register defaults
ChatClient.builder(chatModel)
    .defaultAdvisors(memoryAdvisor, ragAdvisor, new SimpleLoggerAdvisor())
    .build();

// Runtime param (conversation id)
chatClient.prompt()
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))
    .user(text).call().content();

// Add advisor for one request only
chatClient.prompt().advisors(new ReReadingAdvisor()).user(text).call().content();

// Custom advisor skeleton
public class MyAdvisor implements CallAdvisor, StreamAdvisor {
    public String getName() { return "MyAdvisor"; }
    public int getOrder() { return 100; }
    public ChatClientResponse adviseCall(ChatClientRequest r, CallAdvisorChain c) { return c.nextCall(r); }
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest r, StreamAdvisorChain c) { return c.nextStream(r); }
}
```

| Need | Use |
|---|---|
| Remember conversation | `MessageChatMemoryAdvisor` |
| Memory from big history (search) | `VectorStoreChatMemoryAdvisor` |
| Simple RAG | `QuestionAnswerAdvisor` |
| Advanced/modular RAG | `RetrievalAugmentationAdvisor` |
| Better reasoning | `ReReadingAdvisor` |
| Run tools in a loop | `ToolCallingAdvisor` (auto) |
| Block harmful content | `SafeGuardAdvisor` |
| Debug prompts | `SimpleLoggerAdvisor` |
| Run first | order near `HIGHEST_PRECEDENCE` |
| Run last | order near `LOWEST_PRECEDENCE` |

---

## 16. Common mistakes

1. **Forgetting `nextCall()` / `nextStream()`** → request never reaches the LLM (unless you meant to block).
2. **Same order value** for 2 advisors → random order.
3. **Wrong order** (RAG before memory) → search ignores conversation history.
4. Thinking "lowest order = last" → actually **lowest order = first on request, last on response**.
5. Implementing only `CallAdvisor` and then using `.stream()` → advisor is **skipped for streaming** (implement both).
6. Trying to **modify** the response inside `ChatClientMessageAggregator` → it is **read-only**.
7. Trying to **mutate the context map** → it is **immutable**; create a new one.
8. Using old class names (`CallAroundAdvisor`, `AdvisedRequest`) from old tutorials → use 1.0.0 names.
9. Doing blocking work in a streaming advisor without `publishOn(Schedulers.boundedElastic())`.
10. Forgetting `CONVERSATION_ID` for memory advisors → `IllegalArgumentException`.
11. Registering advisors per request when they should be defaults → repeated code.
12. Assuming `MessageChatMemoryAdvisor` works for every model → not all models support it.

---

## 17. Interview quick Q&A

**Q1. What is the Advisors API?**
A Spring AI mechanism to intercept, modify, and enhance `ChatClient` requests and responses, like filters/AOP for AI calls.

**Q2. Which interfaces do I implement for a custom advisor?**
`CallAdvisor` (non-streaming), `StreamAdvisor` (streaming), or both. Optionally `BaseAdvisor` with `before`/`after`.

**Q3. What do `getOrder()` and `getName()` do?**
`getOrder()` sets position in the chain; `getName()` gives a unique name.

**Q4. Which advisor runs first: order 0 or order 100?**
Order 0 (lower value first) for the request. But it runs **last** for the response (stack behavior).

**Q5. Why is the chain called "stack-like"?**
The first advisor wraps all others: first in on the request, last out on the response.

**Q6. How to be first on both input and output?**
Use two advisors with different orders and share state through the advisor context.

**Q7. What happens if two advisors have the same order?**
Execution order between them is not guaranteed.

**Q8. Can an advisor block a request?**
Yes, by not calling the next advisor. Then it must create the response itself.

**Q9. What does the last advisor in the chain do?**
It is added by the framework and sends the request to the Chat Model.

**Q10. `MessageChatMemoryAdvisor` vs `VectorStoreChatMemoryAdvisor`?**
First adds history as messages (keeps structure; model must support it). Second retrieves relevant memory from a vector store and adds it to the system text.

**Q11. `QuestionAnswerAdvisor` vs `RetrievalAugmentationAdvisor`?**
First = Naive RAG, simple. Second = Modular RAG using building blocks from `org.springframework.ai.rag`, more flexible.

**Q12. What is the Re2 advisor?**
It repeats the user question in the prompt ("Read the question again: ...") to improve reasoning.

**Q13. What does `ToolCallingAdvisor` do?**
Runs the tool-calling loop: executes tools the model requests, sends results back, repeats until no more tool calls. Auto-registered by `ChatClient`.

**Q14. What is `ChatClientMessageAggregator`?**
A utility that joins a `Flux` of responses into one `ChatClientResponse` for observing the full answer. Read-only.

**Q15. Is the advisor context mutable?**
No (since 1.0 M3). Use `updateContext` to create a new unmodifiable map.

**Q16. What changed in 1.0.0 for advisors?**
`CallAroundAdvisor` → `CallAdvisor`, `StreamAroundAdvisor` → `StreamAdvisor`, chains renamed similarly, `AdvisedRequest/Response` → `ChatClientRequest/Response`.

**Q17. Do advisors support observability?**
Yes, they participate in the observability stack (metrics and traces).
