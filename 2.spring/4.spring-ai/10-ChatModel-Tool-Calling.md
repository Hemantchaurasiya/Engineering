# Spring AI ChatModel Tool Calling — Effective Notes

> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

1. [What is ChatModel tool calling?](#1-what-is-chatmodel-tool-calling)
2. [When to use ChatModel directly](#2-when-to-use-chatmodel-directly)
3. [No internal tool execution (very important)](#3-no-internal-tool-execution-very-important)
4. [Passing tools to ChatModel](#4-passing-tools-to-chatmodel)
5. [Default tools (on the model)](#5-default-tools-on-the-model)
6. [Override semantics (replace, not append)](#6-override-semantics-replace-not-append)
7. [Driving the loop manually: blocking](#7-driving-the-loop-manually-blocking)
8. [Driving the loop manually: streaming](#8-driving-the-loop-manually-streaming)
9. [Tool context](#9-tool-context)
10. [Return direct](#10-return-direct)
11. [ChatModel vs ChatClient (tools comparison)](#11-chatmodel-vs-chatclient-tools-comparison)
12. [Quick cheat sheet](#12-quick-cheat-sheet)
13. [Common mistakes](#13-common-mistakes)
14. [Interview quick Q&A](#14-interview-quick-qa)

---

## 1. What is ChatModel tool calling?

**Key Points**
- `ChatModel` = the **low-level request/response interface** to an AI provider.
- With tools: it **accepts tool definitions**, **sends them to the model**, and **returns the model's response**.
- If the response contains **tool call requests**, they are **NOT executed automatically**. **You (the caller) must execute them.**
- For most apps, **`ChatClient` is recommended**: it runs the loop through `ToolCallingAdvisor`, works with memory/observability advisors, and supports auto-configuration extension points.
- This page is for people who **deliberately** want the **lower-level** path.

**Use Cases**
- Custom workflow engines, non-Spring agent frameworks, infrastructure libraries.

**Where to Use**
- When you need full control over every step of the request/response cycle.

**Problem Solved**
- No hidden behavior: you decide when and how tools run (approvals, logging, custom orchestration).

**Java Example**
```java
ToolCallback[] tools = ToolCallbacks.from(new DateTimeTools());

ChatOptions chatOptions = ToolCallingChatOptions.builder()
    .toolCallbacks(tools)
    .build();

ChatResponse response = chatModel.call(new Prompt("What day is tomorrow?", chatOptions));
// response may contain a tool call REQUEST. Nothing has been executed yet.
```

---

## 2. When to use ChatModel directly

**Key Points**
- `ChatModel` is the right choice when:
  1. You **do not want the advisor chain** (no memory advisor, no observability advisor, no `ToolCallingAdvisor`).
  2. You are integrating tool calling into a **custom orchestrator** (domain-specific workflow engine, non-Spring agent framework) that **owns its own loop**.
  3. You are **building infrastructure on top of Spring AI** (e.g., a custom `ChatClient` implementation, or a library with its own higher-level API).
- For **everything else** (chat apps, RAG, agentic workflows, tool-heavy assistants) → use **`ChatClient`**.

| Situation | Use |
|---|---|
| Normal chatbot / assistant | `ChatClient` |
| RAG | `ChatClient` |
| Agent with many tools | `ChatClient` |
| Custom orchestrator with its own loop | `ChatModel` |
| Building a library/framework on Spring AI | `ChatModel` |
| No advisors wanted at all | `ChatModel` |

**Problem Solved**
- Helps you pick the right level of abstraction and avoid extra work.

---

## 3. No internal tool execution (very important)

**Key Points**
- `ChatModel.call(prompt)` and `ChatModel.stream(prompt)` return the **raw model response**, including any tool call requests, **without executing them**.
- **Your job**: execute the requested tools, then **call the model again** with the results.
- **Spring AI 1.x vs 2.0**:
  - **1.x**: each `ChatModel` had its **own internal tool-execution loop**.
  - **2.0**: that internal loop is **removed**. Use `ChatClient` (auto-registers `ToolCallingAdvisor`) or write the loop yourself.
- See *Upgrading Tool Calling from 1.x to 2.0* for migration steps.

**Use Cases / Where to Use**
- Anyone migrating from 1.x code that relied on `ChatModel` running tools automatically.

**Problem Solved**
- Explains the most common 2.0 surprise: "my tool never ran".

**Java Example**
```java
// 2.0 behavior
ChatResponse response = chatModel.call(prompt);

if (response.hasToolCalls()) {
    // The tool has NOT run. You must run it (see Section 7).
}
```

---

## 4. Passing tools to ChatModel

**Key Points**
- Tools are passed through **`ToolCallingChatOptions.toolCallbacks(...)`**.
- Accepts a **`List<ToolCallback>`** or **`ToolCallback[]`**.
- Use **`ToolCallbacks.from(...)`** to turn **`@Tool`-annotated objects** into callbacks.
- This **sends tool definitions** to the model. If the model wants a tool, the response has the **call request**; you run it yourself.

**Use Cases**
- Per-request tools for a specific question.

**Where to Use**
- Any direct `ChatModel` call that needs tools.

**Problem Solved**
- Gives a standard way to attach tools without `ChatClient`.

**Java Example**
```java
ChatModel chatModel = ...;
ToolCallback[] tools = ToolCallbacks.from(new DateTimeTools());

ChatOptions chatOptions = ToolCallingChatOptions.builder()
    .toolCallbacks(tools)
    .build();

Prompt prompt = new Prompt("What day is tomorrow?", chatOptions);
ChatResponse response = chatModel.call(prompt);
```

---

## 5. Default tools (on the model)

**Key Points**
- Some `ChatModel` builders accept **default options**. Tools set there apply to **every request** through that model instance (unless overridden).
- Convenient for tools that should always be available.
- **Danger**: risky or destructive tools should usually be added **per request**, not as defaults.

**Use Cases**
- Always-on safe tools like `getCurrentDateTime`.

**Problem Solved**
- Avoid repeating the same safe tools on every call.

**Java Example**
```java
ToolCallback[] dateTimeTools = ToolCallbacks.from(new DateTimeTools());

ChatModel chatModel = OllamaChatModel.builder()
    .ollamaApi(OllamaApi.builder().build())
    .options(ToolCallingChatOptions.builder()
        .toolCallbacks(dateTimeTools)
        .build())
    .build();
```

---

## 6. Override semantics (replace, not append)

**Key Points**
- If tools exist in **both** model default options and per-request options → the **per-request list REPLACES the defaults completely**. Not appended.
- Example: 5 default tools + 1 runtime tool → the model sees **only the 1 runtime tool**.
- To use **defaults + an extra tool**, put the defaults **explicitly** into the runtime options.
- **This is specific to `ChatModel`.** In `ChatClient`, `.tools(...)` **appends** to `.defaultTools(...)`.

| API | Defaults + per-call tools |
|---|---|
| `ChatModel` | Per-request **replaces** defaults |
| `ChatClient` | Per-call **appends** to defaults |

**Problem Solved**
- Avoids the bug where default tools "disappear" for one request.

**Java Example**
```java
ChatModel chatModel = OllamaChatModel.builder()
    .options(ToolCallingChatOptions.builder()
        .toolCallbacks(defaultTools)      // 5 default tools
        .build())
    .build();

// Replaces defaults: model sees ONLY otherTool
ChatOptions runtimeOptions = ToolCallingChatOptions.builder()
    .toolCallbacks(otherTool)             // 1 tool
    .build();
chatModel.call(new Prompt("...", runtimeOptions));

// Defaults + extra tool: combine explicitly
List<ToolCallback> combined = new ArrayList<>(Arrays.asList(defaultTools));
combined.add(otherTool);

ChatOptions combinedOptions = ToolCallingChatOptions.builder()
    .toolCallbacks(combined)
    .build();
chatModel.call(new Prompt("...", combinedOptions));
```

---

## 7. Driving the loop manually: blocking

**Key Points**
- When the model returns tool calls: **execute them, then call the model again** with the results. Repeat until no tool calls. This is what `ToolCallingAdvisor` does automatically for `ChatClient`.
- Key components:

| Component | Job |
|---|---|
| `ToolCallingManager` | Executes tool calls. Default `DefaultToolCallingManager` is **auto-configured by Spring Boot**. Without Boot: `ToolCallingManager.builder().build()` |
| `response.hasToolCalls()` | `true` if the model asked for at least one tool |
| `toolCallingManager.executeToolCalls(prompt, response)` | Finds each tool, runs it, returns `ToolExecutionResult` |
| `result.returnDirect()` | `true` if **all** called tools have `returnDirect = true` |
| `result.conversationHistory()` | Original messages + assistant tool-call request + tool responses. Use as the **next prompt's messages** |

**Loop steps**
```
call model → hasToolCalls? 
   yes → executeToolCalls → (returnDirect? stop) → new Prompt(history) → call model again
   no  → final answer
```

**Use Cases**
- Approval before running a tool, custom logging, limits, tracing in your own orchestrator.

**Problem Solved**
- Full control of the loop when you do not use `ChatClient`.

**Java Example**
```java
ChatModel chatModel = ...;
ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

ToolCallback[] tools = ToolCallbacks.from(new WeatherTools());
ChatOptions chatOptions = ToolCallingChatOptions.builder()
    .toolCallbacks(tools)
    .build();

Prompt prompt = new Prompt("What is the weather in Amsterdam and Paris?", chatOptions);
ChatResponse response = chatModel.call(prompt);

while (response.hasToolCalls()) {
    // <-- you can add approval/logging here before running tools
    ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);

    if (result.returnDirect()) {
        // returnDirect=true: stop, do not send back to the model
        return result.conversationHistory();
    }

    prompt = new Prompt(result.conversationHistory(), chatOptions);
    response = chatModel.call(prompt);
}

String finalAnswer = response.getResult().getOutput().getText();
```

---

## 8. Driving the loop manually: streaming

**Key Points**
- Streaming returns **chunks**. For each iteration you must **collect all chunks into one response** before checking for tool calls.
- Use an aggregator (`MessageAggregator` in this example) to combine chunks, and keep **forwarding raw chunks** to a subscriber (e.g., an **SSE** endpoint) at the same time using `doOnNext`.
- Flow per iteration:
  1. Stream the model response, **forward chunks** to the UI, **aggregate** into one `ChatResponse`.
  2. No tool calls → **break** (done).
  3. Tool calls → `executeToolCalls(...)`. If `returnDirect()` → break. Otherwise make a **new prompt** with the history and loop.
- For `ChatClient`-driven streaming with full advisor support, see *ToolCallingAdvisor: User-Controlled Streaming*.

**Use Cases**
- Chat UI that shows text live and still runs tools between turns.

**Problem Solved**
- Live streaming together with tool execution, without `ChatClient`.

**Java Example**
```java
ChatModel chatModel = ...;
ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

ToolCallback[] tools = ToolCallbacks.from(new WeatherTools());
ChatOptions chatOptions = ToolCallingChatOptions.builder()
    .toolCallbacks(tools)
    .build();

Prompt prompt = new Prompt("What is the weather in Amsterdam and Paris?", chatOptions);

while (true) {
    AtomicReference<ChatResponse> aggregated = new AtomicReference<>();

    new MessageAggregator().aggregate(
        chatModel.stream(prompt).doOnNext(chunk -> forwardToSse(chunk)),   // forward live
        aggregated::set                                                    // keep full response
    ).blockLast();

    ChatResponse response = aggregated.get();
    if (!response.hasToolCalls()) {
        break;
    }

    ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
    if (result.returnDirect()) {
        break;
    }
    prompt = new Prompt(result.conversationHistory(), chatOptions);
}
```
> `blockLast()` blocks the thread. Do not run this on a reactive event-loop thread; use a bounded elastic scheduler or a normal servlet thread.

---

## 9. Tool context

**Key Points**
- **Tool context** = non-model data (tenant ID, user ID) passed to tools. It is **not sent to the model**.
- Works the same as in `ChatClient`. Set via **`ToolCallingChatOptions.toolContext(...)`**.
- Default + runtime `toolContext` → **merged** (unlike `toolCallbacks`, which are **replaced**). **Runtime entries win** for the same key.

| Option | Default + runtime behavior |
|---|---|
| `toolCallbacks` | **Replaced** by runtime |
| `toolContext` | **Merged**, runtime wins on conflicts |

**Use Cases**
- Multi-tenant tools, user-specific permissions.

**Problem Solved**
- Gives tools needed data without exposing it to the model.

**Java Example**
```java
ChatOptions chatOptions = ToolCallingChatOptions.builder()
    .toolCallbacks(ToolCallbacks.from(new CustomerTools()))
    .toolContext(Map.of("tenantId", "acme"))
    .build();

Prompt prompt = new Prompt("Tell me about customer 42", chatOptions);
chatModel.call(prompt);
```
Tool side:
```java
class CustomerTools {
    @Tool(description = "Retrieve customer information")
    Customer getCustomerInfo(Long id, ToolContext toolContext) {
        return repo.findById(id, toolContext.getContext().get("tenantId"));
    }
}
```

---

## 10. Return direct

**Key Points**
- `returnDirect` flags are **honored by `ToolCallingManager.executeToolCalls(...)`**.
- After execution, check **`ToolExecutionResult.returnDirect()`**:
  - `true` → **skip the next model call**, return the tool result to the caller.
- If the model asked for **several tools** in one iteration, `returnDirect()` is `true` **only if ALL** called tools have `returnDirect = true`.
- You must **write the check yourself** in manual loops (`ChatClient` does it for you).

**Use Cases**
- Tool output is the final answer (e.g., RAG retrieval); saves a model call and time.

**Problem Solved**
- Avoids extra latency and cost when the model would only repeat the tool output.

**Java Example**
```java
ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);

if (result.returnDirect()) {
    // Skip the next model call, return tool result to the caller
    return result.conversationHistory();
}
```

---

## 11. ChatModel vs ChatClient (tools comparison)

| Point | `ChatModel` | `ChatClient` |
|---|---|---|
| Tool loop | **You write it** | Automatic (`ToolCallingAdvisor`) |
| Passing tools | `ToolCallingChatOptions.toolCallbacks(...)` | `.tools(...)` / `.defaultTools(...)` |
| Default + per-call tools | Per-request **replaces** defaults | Per-call **appends** to defaults |
| Tool context | `ToolCallingChatOptions.toolContext(...)` (merged) | `.toolContext(...)` (merged) |
| Return direct | You check `result.returnDirect()` | Handled for you |
| Advisors (memory, observability) | None | Supported |
| Best for | Custom orchestrators, infrastructure | Almost all applications |

---

## 12. Quick cheat sheet

```java
// 1. Make tools
ToolCallback[] tools = ToolCallbacks.from(new MyTools());

// 2. Options
ChatOptions options = ToolCallingChatOptions.builder()
    .toolCallbacks(tools)
    .toolContext(Map.of("tenantId", "acme"))
    .build();

// 3. Loop
ToolCallingManager manager = ToolCallingManager.builder().build();
Prompt prompt = new Prompt("question", options);
ChatResponse response = chatModel.call(prompt);

while (response.hasToolCalls()) {
    ToolExecutionResult result = manager.executeToolCalls(prompt, response);
    if (result.returnDirect()) return result.conversationHistory();
    prompt = new Prompt(result.conversationHistory(), options);
    response = chatModel.call(prompt);
}
String answer = response.getResult().getOutput().getText();
```

| Need | Use |
|---|---|
| Convert `@Tool` objects | `ToolCallbacks.from(obj)` |
| Attach tools | `ToolCallingChatOptions.builder().toolCallbacks(...)` |
| Check model asked for a tool | `response.hasToolCalls()` |
| Run requested tools | `toolCallingManager.executeToolCalls(prompt, response)` |
| Next prompt messages | `result.conversationHistory()` |
| Skip next model call | `result.returnDirect()` |
| Stream + forward chunks | `MessageAggregator` + `doOnNext` |
| Extra data for tools | `toolContext(...)` |

---

## 13. Common mistakes

1. Expecting `ChatModel.call()` to **run tools automatically** (that was **1.x**; removed in **2.0**).
2. Forgetting the **`while (response.hasToolCalls())`** loop → the model never gets tool results.
3. Building the next prompt **without `result.conversationHistory()`** → model loses the tool call/response messages.
4. Forgetting to pass **`chatOptions` again** in the next `Prompt` → tools are not sent on later calls.
5. Assuming per-request tools **append** to defaults → on `ChatModel` they **replace** them.
6. Putting **risky tools as defaults** on the model → available on every request.
7. Ignoring **`result.returnDirect()`** → extra model call even when the tool was meant to return directly.
8. In streaming, checking tool calls on **single chunks** instead of the **aggregated** response.
9. Using **`blockLast()`** on a reactive event-loop thread → blocks it.
10. No **loop limit** in a manual loop → possible infinite tool loops. Add your own max-iterations counter (the default call limits belong to `ToolCallingManager`; check your version).
11. Using `ChatModel` for a normal chatbot → you re-build memory, observability, and loop logic that `ChatClient` already gives you.
12. Forgetting that **`toolCallbacks` are replaced** but **`toolContext` is merged** → inconsistent expectations.

---

## 14. Interview quick Q&A

**Q1. Does `ChatModel` run tools automatically in Spring AI 2.0?**
No. It returns the raw response with tool call requests. The caller runs the tools and calls the model again.

**Q2. What changed from 1.x?**
In 1.x each `ChatModel` had an internal tool-execution loop. In 2.0 it is removed; use `ChatClient` (`ToolCallingAdvisor`) or write the loop.

**Q3. When should you use `ChatModel` directly?**
No advisor chain wanted, a custom orchestrator that owns the loop, or building infrastructure on top of Spring AI.

**Q4. How do you pass tools to `ChatModel`?**
`ToolCallingChatOptions.builder().toolCallbacks(...)`, using `ToolCallbacks.from(...)` for `@Tool` objects.

**Q5. What happens when tools are set in both default and per-request options?**
The per-request list replaces the defaults entirely. To keep defaults, add them explicitly to the runtime list.

**Q6. How is this different from `ChatClient`?**
In `ChatClient`, per-call `.tools(...)` appends to `.defaultTools(...)`.

**Q7. Which class executes tool calls?**
`ToolCallingManager` (`DefaultToolCallingManager` is auto-configured by Boot).

**Q8. How do you know the model wants a tool?**
`response.hasToolCalls()`.

**Q9. What does `executeToolCalls` return?**
A `ToolExecutionResult` with `conversationHistory()` (original messages + tool call request + tool responses) and `returnDirect()`.

**Q10. What do you use for the next prompt?**
`new Prompt(result.conversationHistory(), chatOptions)`.

**Q11. What does `returnDirect()` mean here?**
`true` if all called tools have `returnDirect = true`; then skip the next model call and return the result.

**Q12. How to handle streaming with tools?**
Aggregate each iteration's chunks (`MessageAggregator`), forward raw chunks to the UI, check `hasToolCalls()` on the aggregated response, execute tools, and loop.

**Q13. How does tool context behave with defaults?**
Merged with runtime entries taking precedence, unlike `toolCallbacks` which are replaced.

**Q14. Why are default tools on a model risky?**
They go to every request through that model instance, including risky or destructive tools. Add those per request.
