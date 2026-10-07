# Spring AI Tool Calling — Effective Notes

> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

**Part A — Basics**
1. [What is tool calling? (agent loop)](#1-what-is-tool-calling-agent-loop)
2. [Architecture overview (Spring AI 2.0)](#2-architecture-overview-spring-ai-20)
3. [Quick start](#3-quick-start)
4. [Three ways to define tools](#4-three-ways-to-define-tools)
5. [Declarative: @Tool and @ToolParam](#5-declarative-tool-and-toolparam)
6. [Programmatic: MethodToolCallback](#6-programmatic-methodtoolcallback)
7. [Programmatic: FunctionToolCallback](#7-programmatic-functiontoolcallback)
8. [ToolCallback beans](#8-toolcallback-beans)
9. [Passing tools to ChatClient (per call vs default)](#9-passing-tools-to-chatclient-per-call-vs-default)

**Part B — The loop**
10. [The tool calling loop (ToolCallingAdvisor)](#10-the-tool-calling-loop-toolcallingadvisor)
11. [Memory and the tool loop](#11-memory-and-the-tool-loop)
12. [Scaling to hundreds of tools (ToolSearch advisor)](#12-scaling-to-hundreds-of-tools)

**Part C — MCP**
13. [Consuming MCP server tools](#13-consuming-mcp-server-tools)
14. [Exposing Spring tools as an MCP server](#14-exposing-spring-tools-as-an-mcp-server)
15. [Combining local and MCP tools](#15-combining-local-and-mcp-tools)

**Part D — Advanced control**
16. [Tool argument augmentation](#16-tool-argument-augmentation)
17. [User-controlled tool execution](#17-user-controlled-tool-execution)
18. [Extending the loop: custom ToolAdvisor](#18-extending-the-loop-custom-tooladvisor)

**Part E — Specification reference**
19. [ToolCallback](#19-toolcallback)
20. [ToolDefinition](#20-tooldefinition)
21. [JSON schema (descriptions, required/optional)](#21-json-schema)
22. [ToolContext](#22-toolcontext)
23. [Return direct](#23-return-direct)
24. [Result conversion](#24-result-conversion)
25. [Method tool limitations](#25-method-tool-limitations)
26. [Exception handling](#26-exception-handling)
27. [Tool call limits](#27-tool-call-limits)
28. [Tool resolution (ToolCallbackResolver)](#28-tool-resolution-toolcallbackresolver)
29. [Observability and logging](#29-observability-and-logging)
30. [ChatModel tool calling (low level)](#30-chatmodel-tool-calling-low-level)

**Part F — Revision**
31. [Cheat sheet](#31-cheat-sheet)
32. [Common mistakes](#32-common-mistakes)
33. [Interview quick Q&A](#33-interview-quick-qa)

---

# Part A — Basics

## 1. What is tool calling? (agent loop)

**Key Points**
- **Tool calling** = the AI model can **ask your app to run a function** and then use the result.
- A model that only generates text = **chatbot**. A model that can **find info, take action, and loop until the goal is done** = **agent**.
- Two main purposes:
  - **Information retrieval**: weather, customer records, latest news, DB/web/file/search data.
  - **Taking action**: send an email, book a flight, update a record, start a workflow.
- **Very important**: the **application owns the tool logic**, not the model.
  - The model only **requests** a tool call and gives **arguments**.
  - Your app **executes** the tool and **returns the result**.
  - The model **never gets direct access** to your APIs → a key **security** point.

```
User question
   ↓
Model: "call getWeather(city=Paris)"   ← request only
   ↓
Your app runs the tool → result
   ↓
Result sent back to model → final answer
```

**Use Cases**
- "What is my order status?" (DB lookup), "Book a meeting" (action), "Weather in Paris" (API).

**Where to Use**
- When the AI needs **live/private data** or must **do something** in your system.

**Problem Solved**
- Models do not know current/private data and cannot act. Tools fix both, safely.

**Java Example**
```java
class OrderTools {
    @Tool(description = "Get the status of an order by its id")
    String getOrderStatus(String orderId) {
        return orderService.status(orderId);   // YOUR code runs, not the model
    }
}
```

---

## 2. Architecture overview (Spring AI 2.0)

**Key Points**
- In 2.0, the tool loop is a **first-class part of the `ChatClient` advisor chain**.
- Flow:
  1. You **define tools** and pass them to `ChatClient`.
  2. `ChatClient` **auto-registers `ToolCallingAdvisor`** which drives the loop.
  3. The **model decides** which tools to call; **`ToolCallingManager` executes** them; loop continues until the model answers **without** tool calls.
  4. Other advisors (memory, observability, retries, custom) combine with the loop using **advisor ordering**.
- This **replaces** the per-`ChatModel` tool loops of Spring AI **1.x**.
- Calling `ChatModel` directly is still possible for low-level cases (see Section 30).
- Check the *Chat Model Comparisons* page to see which models support tool calling.

**Where to Use / Problem Solved**
- Tool loop is **composable** with memory, retries, logging, etc. instead of hidden inside each model class.

---

## 3. Quick start

**Key Points**
- Annotate a method with **`@Tool`**.
- Pass the object with **`.tools(...)`**.
- Spring AI does the full round trip automatically.
- Example: "set an alarm 10 minutes from now" → model calls `getCurrentDateTime()`, then `setAlarm(time)`.

**Use Cases**
- Date/time, alarms, reminders, simple utilities.

**Problem Solved**
- Tool use with minimal code.

**Java Example**
```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;

class DateTimeTools {

    @Tool(description = "Get the current date and time in the user's timezone")
    String getCurrentDateTime() {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }

    @Tool(description = "Set a user alarm for the given time, provided in ISO-8601 format")
    void setAlarm(String time) {
        LocalDateTime alarmTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
        System.out.println("Alarm set for " + alarmTime);
    }
}

String response = ChatClient.create(chatModel)
        .prompt("Can you set an alarm 10 minutes from now?")
        .tools(new DateTimeTools())
        .call()
        .content();
```

---

## 4. Three ways to define tools

| Style | Use when |
|---|---|
| **Declarative `@Tool`** | You own the method; want the least code |
| **`MethodToolCallback`** | Need programmatic control over a method-based tool (dynamic registration) |
| **`FunctionToolCallback`** | Expose a `Function` / `Supplier` / `Consumer` / `BiFunction` lambda or method reference |

- All three produce **`ToolCallback`** objects that go through the **same loop**.
- You can **mix styles freely**.

---

## 5. Declarative: @Tool and @ToolParam

**Key Points**
- `@Tool` can be on **any method**: public, package-private, private; static or instance.
- `@Tool` attributes:

| Attribute | Meaning |
|---|---|
| `name` | Tool name. Default = method name. Must be **unique** in the tool set. |
| `description` | What the tool does and **when to use it**. **Strongly recommended**; without it the model has no guidance. |
| `returnDirect` | Return result **directly to the caller** (skip the model). See Section 23. |
| `resultConverter` | Custom `ToolCallResultConverter`. See Section 24. |

- `@ToolParam` describes **each parameter** (`description`, `required`).
- **All parameters are required by default.** Make optional with `@ToolParam(required = false)` or `@Nullable`.
- **AOT / GraalVM native image**: the class with `@Tool` methods must be a **Spring bean** (`@Component`), or annotate with `@RegisterReflection(memberCategories = MemberCategory.INVOKE_DECLARED_METHODS)`.

**Use Cases**
- Most day-to-day tools (DB lookups, API calls).

**Where to Use**
- Default choice when you own the code.

**Problem Solved**
- Describes tools to the model with almost no boilerplate.

**Java Example**
```java
@Component
class WeatherTools {

    @Tool(name = "get_weather", description = "Get the weather for a city at a specific time")
    public String getWeather(
            @ToolParam(description = "City name") String city,
            @ToolParam(description = "Time in ISO-8601 format", required = false) String at) {
        return weatherService.fetch(city, at);
    }
}
```

---

## 6. Programmatic: MethodToolCallback

**Key Points**
- For **dynamic registration**: you do not control the source class, or you build the definition at **runtime**.
- You give: a **`ToolDefinition`**, the **`Method`**, and the **`toolObject`**.
- `toolObject` is **required for instance methods**, optional for static methods.

**Use Cases**
- Tools from a third-party class; tools created from config.

**Problem Solved**
- Expose methods without adding annotations to them.

**Java Example**
```java
Method method = ReflectionUtils.findMethod(WeatherTools.class, "getWeather", String.class);

MethodToolCallback callback = MethodToolCallback.builder()
    .toolDefinition(ToolDefinitions.builder(method)
        .description("Get the current weather for a given city")
        .build())
    .toolMethod(method)
    .toolObject(new WeatherTools())
    .build();
```

---

## 7. Programmatic: FunctionToolCallback

**Key Points**
- For tools backed by a **`Function`, `Supplier`, `Consumer`, or `BiFunction`** (lambdas and method references).
- Factory takes a **name** and the **function**. Builder sets **description** and **input type** (used to generate the JSON schema).

**Use Cases**
- Wrap existing services quickly: `weatherService::getWeather`.

**Problem Solved**
- Make any functional object a tool without a new class.

**Java Example**
```java
record WeatherRequest(String location, String unit) {}

FunctionToolCallback callback = FunctionToolCallback.builder("currentWeather", weatherService::getWeather)
    .description("Get the weather in location")
    .inputType(WeatherRequest.class)
    .build();
```

---

## 8. ToolCallback beans

**Key Points**
- Spring AI **auto-discovers `ToolCallback` beans** and exposes them via `ToolCallbackResolver` (lookup by name).
- Define a tool **once as a `@Bean`**, inject where needed.
- **2.0 change**: the old **`SpringBeanToolCallbackResolver`** pattern (bare `Function` beans resolved by name via `toolNames(...)`) is **removed**. Tools must be **explicit `ToolCallback` beans**.

**Use Cases**
- Shared tools used by many ChatClients/services.

**Problem Solved**
- One central definition; no copy-paste.

**Java Example**
```java
@Configuration(proxyBeanMethods = false)
class WeatherToolsConfig {

    @Bean
    ToolCallback currentWeather(WeatherService weatherService) {
        return FunctionToolCallback.builder("currentWeather", weatherService::getWeather)
            .description("Get the weather in location")
            .inputType(WeatherRequest.class)
            .build();
    }
}

@Autowired ToolCallback currentWeather;

ChatClient.create(chatModel)
    .prompt("What's the weather in Copenhagen?")
    .tools(currentWeather)
    .call()
    .content();
```

---

## 9. Passing tools to ChatClient (per call vs default)

**Key Points**
- Two methods:
  - **`.tools(...)`** → **per call** (only this request).
  - **`.defaultTools(...)`** on the builder → **every request** from this client.
- Both accept a **mix**: `@Tool` POJOs, `ToolCallback`, `ToolCallbackProvider`, arrays/collections of those.
- **Per-call `.tools()` appends** to defaults (does **not** replace). Final list = **defaults + call tools**.
- **Safety**: default tools are shared on every request. **Risky/destructive tools should be added per call**, not as defaults.
- Tools set through `ToolCallingChatOptions.toolCallbacks(...)` on the `ChatModel` are **overridden** by the request-level tool list.

**Use Cases**
- Defaults: safe read-only tools (time, search). Per call: "delete record", "send money".

**Problem Solved**
- Control which tools the model can use in which situation.

**Java Example**
```java
// Per call
chatClient.prompt("Cancel order 55")
    .tools(new AdminTools())          // risky tool only here
    .call()
    .content();

// Defaults
ChatClient client = ChatClient.builder(chatModel)
    .defaultTools(new WeatherTools(), currentWeather)   // safe tools
    .build();
```

---

# Part B — The loop

## 10. The tool calling loop (ToolCallingAdvisor)

**Key Points**
- `.call()` or `.stream()` → request flows through the **advisor chain**.
- **`ToolCallingAdvisor`** (auto-registered by `DefaultChatClient`) owns the loop:
  1. Sends request to the model **with all tool definitions**.
  2. Model decides whether to call tools.
  3. If tool calls exist, the advisor: passes them to **`ToolCallingManager`** (finds the `ToolCallback` and runs it) → **appends results** to history → **sends history back** to the model.
  4. Repeats until the model answers **without tool calls** → returned to the caller.
- Works for **blocking and streaming**.
- It is a **recursive advisor** (re-enters the downstream chain each iteration). Same pattern as **structured-output validation retries**.
- **Exactly one `ToolAdvisor`** allowed in the chain. A second one fails with an error.

```
Request → [advisors] → Model → tool calls? 
              ↑            ↓ yes
              └── results ← ToolCallingManager runs tools
                           ↓ no
                       Final answer
```

**Use Cases**
- Multi-step tasks: get time → compute → set alarm.

**Problem Solved**
- You do not write the "call model, run tool, call again" loop.

---

## 11. Memory and the tool loop

**Key Points**
- **Where you put `MessageChatMemoryAdvisor` relative to `ToolCallingAdvisor`** decides what memory stores.
- `ToolCallingAdvisor` default order = `HIGHEST_PRECEDENCE + 300`.

### 11.1 Outside the loop (default)
- `MessageChatMemoryAdvisor` default order = `HIGHEST_PRECEDENCE + 200` (lower number → **outside** the loop).
- It:
  - loads history **once** before the loop,
  - saves **only final user + final assistant** messages after the loop,
  - **never sees** tool call requests or tool responses.
- **Safe default**, works with **every** `ChatMemoryRepository`. Same as 1.x behavior.

```java
var chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())  // outside by default
    .build();
```

### 11.2 Inside the loop
- Give the memory advisor an **order greater than** `ToolCallingAdvisor.DEFAULT_ORDER`.
- Memory then stores the **full tool transcript**, so the model can reason about what tools were already tried and their results.
- To avoid **duplicate writes**, `ToolCallingAdvisor`'s internal conversation history must be **disabled**:
  - **Auto-registered advisor**: automatic (`DefaultChatClient` detects a `MemoryAdvisor` inside the loop).
  - **Manually built advisor**: call `.disableInternalConversationHistory()` yourself.
- Tool call **limits still see the full turn history**, so limits keep working.

```java
var chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
    .order(BaseAdvisor.HIGHEST_PRECEDENCE + 400)   // inside (after) ToolCallingAdvisor
    .build();

var chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(chatMemoryAdvisor)
    .build();
```

### 11.3 Backend compatibility
- Repository must be able to store `ToolResponseMessage` and tool call messages.
- As of 2.0, safe **inside the loop**:
  - `InMemoryChatMemoryRepository`
  - `RedisChatMemoryRepository`
  - `Neo4jChatMemoryRepository`
- For **JDBC with full tool message support** (plus event-sourced history, turn-aware compaction, multi-agent branch isolation): **`spring-ai-session`** community project (planned for **Spring AI 2.1**).

| Position | Stores tool messages? | Repository support needed |
|---|---|---|
| Outside (default) | No | Any |
| Inside | Yes | InMemory, Redis, Neo4j |

**Use Cases**
- Inside: long agent sessions where the model must remember earlier tool results.
- Outside: simple chatbots.

**Problem Solved**
- Control how much tool history is remembered without breaking storage.

---

## 12. Scaling to hundreds of tools

**Key Points**
- Default advisor sends **all tool definitions on every request**.
- At **30+ tools** (or many MCP servers → hundreds of tools): **context bloat, worse accuracy, higher token cost**.
- **`ToolSearchToolCallingAdvisor`** = drop-in replacement using **progressive tool disclosure**:
  - Indexes the full tool set **once per session**.
  - Sends only a built-in **`toolSearchTool`**; the model **searches** for needed tools by natural language.
  - Only **discovered** tools are included in later requests.
- Enable with one property.

**Use Cases**
- Big enterprise tool catalogs, multi-MCP-server setups.

**Problem Solved**
- Fewer tokens, better tool selection.

**Config Example**
```properties
spring.ai.chat.client.tool-search-advisor.enabled=true
```

---

# Part C — MCP

## 13. Consuming MCP server tools

**Key Points**
- **MCP (Model Context Protocol)** = standard way for AI apps to use **tools, resources, prompts** from remote servers.
- Spring AI works **both ways**: **consume** MCP tools and **expose** your tools.
- To consume: add the **MCP client starter**, configure connections.
- Auto-config connects to servers, discovers tools, exposes them as **`SyncMcpToolCallbackProvider`** (or `AsyncMcpToolCallbackProvider` for async client).
- **MCP providers are NOT auto-registered with ChatClient** on purpose: listing tools would force a **network call to every MCP server at startup**. You **wire them explicitly**.
- Turn off tool callback auto-config: `spring.ai.mcp.client.toolcallback.enabled=false`.

**Use Cases**
- Use ready-made tools (web search, file system, GitHub, DB) from MCP servers.

**Problem Solved**
- Reuse tools across apps and languages without writing adapters.

**Java Example**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```
```properties
spring.ai.mcp.client.stdio.connections.my-server.command=npx
spring.ai.mcp.client.stdio.connections.my-server.args=-y,@modelcontextprotocol/server-everything
```
```java
@Autowired SyncMcpToolCallbackProvider mcpTools;

// Once, as defaults
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultTools(mcpTools)
    .build();

// Or per call
chatClient.prompt()
    .user("Search the web for the latest Spring AI release notes")
    .tools(mcpTools)
    .call()
    .content();
```

---

## 14. Exposing Spring tools as an MCP server

**Key Points**
- Use **`@McpTool`** (instead of `@Tool`) and **`@McpToolParam`**.
- Add the **MCP server starter**. Auto-config scans `@McpTool` beans, generates JSON schemas, registers them.
- See MCP Server Boot Starter docs for transport, security, observability.

**Use Cases**
- Let other AI apps (e.g., desktop assistants, other agents) call your company's services.

**Problem Solved**
- Share your business tools with any MCP client.

**Java Example**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```
```java
@Component
public class WeatherTools {

    @McpTool(description = "Get the current weather for a given city")
    public String getWeather(
            @McpToolParam(description = "City name") String city) {
        return weatherService.fetch(city);
    }
}
```

---

## 15. Combining local and MCP tools

**Key Points**
- Local `@Tool` methods and MCP tools share the **same `ToolCallback`** interface. Model and advisor **do not distinguish** them.
- `.tools(...)` and `.defaultTools(...)` accept both together.
- **Watch out**:
  - **Name conflicts**: `DefaultMcpToolNamePrefixGenerator` fixes duplicates **between MCP servers**, but **not** between a local tool and an MCP tool. Rename one, or use **`McpToolFilter`** to drop the remote one.
  - **Restrict exposure**: MCP tools come from external sources. Use an **`McpToolFilter`** bean to select tools by **server, name, or description** and limit risk from untrusted servers.

**Java Example**
```java
chatClient.prompt()
    .tools(new LocalTools(), mcpTools)
    .call()
    .content();
```

---

# Part D — Advanced control

## 16. Tool argument augmentation

**Key Points**
- Adds **extra arguments** to a tool's input schema **without changing the tool code**.
- The model sees the augmented schema and fills extra fields. Your **consumer** receives them. The **original tool receives only its normal arguments**.
- Common uses:
  - **Inner thinking / reasoning** (why this tool was called).
  - **Memory enhancement** (extract insights for long-term memory).
  - **Analytics & tracking** (intent, usage patterns).
  - **Multi-agent coordination** (agent IDs, signals).
- Components:
  - `AugmentedToolCallbackProvider<T>` — wraps tool objects/providers.
  - `AugmentedToolCallback<T>` — wraps one `ToolCallback`.
  - `AugmentedArgumentEvent<T>` — has `toolDefinition()`, `rawInput()`, `arguments()`.
  - `ToolInputSchemaAugmenter` — low-level schema utility.
- `removeExtraArgumentsAfterProcessing` (default **true**): if true, extra args are **not** passed to the original tool. Set **false** only if the tool can ignore extra fields.

**Problem Solved**
- Collect reasoning/metadata from the model for logs and audits with **zero changes** to existing tools.

**Java Example**
```java
public record AgentThinking(
    @ToolParam(description = "Your reasoning for calling this tool", required = true)
    String innerThought,

    @ToolParam(description = "Confidence level (low, medium, high)", required = false)
    String confidence
) {}

AugmentedToolCallbackProvider<AgentThinking> provider = AugmentedToolCallbackProvider
    .<AgentThinking>builder()
    .toolObject(new WeatherTools())
    .argumentType(AgentThinking.class)
    .argumentConsumer(event -> {
        AgentThinking thinking = event.arguments();
        log.info("Tool: {} | Reasoning: {}", event.toolDefinition().name(), thinking.innerThought());
    })
    .removeExtraArgumentsAfterProcessing(true)
    .build();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultTools(provider)
    .build();
```

---

## 17. User-controlled tool execution

**Key Points**
- Auto loop covers most cases. Take control when you need to:
  - **Gate** execution on an **external approval**.
  - **Stream progress** to SSE/WebSocket.
  - Apply **conditional logic** between turns.
  - **Stop** the loop by a side signal.
- Opt out **per call**: `AdvisorParams.toolCallingAdvisorAutoRegister(false)`.
- Then you must: detect tool calls in `ChatResponse` (`hasToolCalls()`) and execute with **`ToolCallingManager.executeToolCalls(...)`**, then call the model again with updated history.
- Disable **globally**: `spring.ai.chat.client.tool-calling.enabled=false`.
- **Warning**: driving the loop yourself **bypasses observability, advisor composition, and the single-ToolAdvisor rule**. Usually a **custom advisor inside the loop** (Section 18) is simpler and more composable.
- Streaming version: forward each chunk `Flux` while aggregating with `ChatClientMessageAggregator` (see User-Controlled Streaming docs).

**Problem Solved**
- Human-in-the-loop approvals and fine control.

**Java Example**
```java
ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

ToolCallback[] tools = ToolCallbacks.from(new WeatherTools());
ChatOptions chatOptions = ToolCallingChatOptions.builder().toolCallbacks(tools).build();

String question = "What is the weather in Amsterdam and Paris?";

ChatClientResponse response = chatClient.prompt()
    .user(question)
    .options(chatOptions)
    .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))   // no auto loop
    .call()
    .chatClientResponse();

Prompt prompt = new Prompt(List.of(new UserMessage(question)), chatOptions);

while (response.chatResponse() != null && response.chatResponse().hasToolCalls()) {
    // <-- approval / logging / UI update can go HERE
    ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response.chatResponse());
    prompt = new Prompt(result.conversationHistory(), chatOptions);
    response = chatClient.prompt()
        .messages(result.conversationHistory())
        .options(chatOptions)
        .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
        .call()
        .chatClientResponse();
}
```

---

## 18. Extending the loop: custom ToolAdvisor

**Key Points**
- The loop is **not a black box**. `ToolCallingAdvisor` has **hook methods**.
- **`ToolAdvisor`** = **marker interface**. A custom tool advisor must implement it so `DefaultChatClient` recognizes it, enforces the **single-advisor** rule, and uses it **instead of the default**.
- Hooks (each has a call and a stream version):

| Hook | When it runs |
|---|---|
| `doInitializeLoop` / `doInitializeLoopStream` | **Once**, before the first iteration |
| `doBeforeCall` / `doBeforeStream` | **Before each** iteration |
| `doAfterCall` / `doAfterStream` | **After each** iteration |
| `doFinalizeLoop` / `doFinalizeLoopStream` | **Once**, after the loop ends |

- `ToolSearchToolCallingAdvisor` uses `doInitializeLoop` (index tools, adjust system message) and `doBeforeCall` (inject only discovered tools).
- **Use cases**:
  - **Approval gate** for destructive tools.
  - **Observability events** per tool call.
  - **Budget control** (count tokens/LLM calls; abort over limit).
  - **Custom tool resolution** from dynamic sources.
- **Auto-configuration**: register your own **`ToolCallingAdvisor.Builder<?>`** bean in an auto-configuration that runs **before** `ChatClientAutoConfiguration`. The default builder bean is `@ConditionalOnMissingBean`, so yours wins.

**Java Example**
```java
@AutoConfiguration(beforeName = "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration")
@ConditionalOnProperty(prefix = "my.advisor", name = "enabled", havingValue = "true")
public class MyToolAdvisorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ToolCallingAdvisor.Builder<?> toolCallingAdvisorBuilder(ToolCallingManager toolCallingManager) {
        return MyCustomToolCallingAdvisor.builder()
            .toolCallingManager(toolCallingManager);
    }
}
```
> `MyCustomToolCallingAdvisor` would extend `ToolCallingAdvisor` and override the hooks you need (for example `doBeforeCall` to run an approval check). Check the *ToolCallingAdvisor* page for exact hook signatures.

---

# Part E — Specification reference

## 19. ToolCallback

**Key Points**
- Models a tool: **definition** (what the model sees) + **execution logic**.
- Built-in: `MethodToolCallback`, `FunctionToolCallback`. Implement it **yourself** for full control (e.g., proxy a remote tool source like MCP does).

```java
public interface ToolCallback {
    ToolDefinition getToolDefinition();                 // what the model sees
    ToolMetadata getToolMetadata();                     // e.g., return-direct
    String call(String toolInput);                      // run with JSON input
    String call(String toolInput, ToolContext toolContext);
}
```

---

## 20. ToolDefinition

**Key Points**
- The **contract the model sees**: `name`, `description`, `inputSchema` (JSON schema).
- Build with `ToolDefinition.builder()`.
- For method tools: `ToolDefinitions.from(method)` generates it. `@Tool` name/description override method-name defaults.

```java
ToolDefinition toolDefinition = ToolDefinition.builder()
    .name("currentWeather")
    .description("Get the weather in location")
    .inputSchema("""
        {
            "type": "object",
            "properties": {
                "location": { "type": "string" },
                "unit": { "type": "string", "enum": ["C", "F"] }
            },
            "required": ["location", "unit"]
        }
    """)
    .build();
```

---

## 21. JSON schema

**Key Points**
- Spring AI's `JsonSchemaGenerator` creates the schema from method/function parameters.
- **Parameter descriptions** (Spring AI's annotation has highest precedence):
  - `@ToolParam(description = "...")` — Spring AI
  - `@JsonClassDescription` / `@JsonPropertyDescription` — Jackson
  - `@Schema(description = "...")` — Swagger
- **Required vs optional**: everything is **required by default**. Make optional via (in order): `@ToolParam(required=false)`, `@JsonProperty(required=false)`, `@Schema(required=false)`, `@Nullable`.
- **Why it matters**: if you mark a parameter **required** but the model **cannot know** its value, it will **make one up** (hallucinate). Set required correctly to reduce hallucinations.

**Java Example**
```java
class CustomerTools {

    @Tool(description = "Update customer information")
    void updateCustomerInfo(
            Long id,
            String name,
            @ToolParam(description = "Email address, RFC 5322 format", required = false) String email) {
        // ...
    }
}
```

---

## 22. ToolContext

**Key Points**
- Pass **non-model data** (tenant ID, user ID, request scope) to tools.
- The data is **never sent to the model**. It goes **directly to the tool** at invocation time.
- Flow: request carries tool definitions + context → only **definitions** go to the model → model asks for a tool → Spring AI calls the tool and **hands the context directly** → result goes back to the model → final answer.
- If `toolContext` is set both as **default** and at **call site**, the maps are **merged**; **runtime values win**.

**Use Cases**
- Multi-tenant apps, user-specific permissions, passing auth info safely.

**Problem Solved**
- Keeps **sensitive or irrelevant data away from the model** (security + fewer tokens) while tools still get it.

**Java Example**
```java
class CustomerTools {

    @Tool(description = "Retrieve customer information")
    Customer getCustomerInfo(Long id, ToolContext toolContext) {
        return customerRepository.findById(id, toolContext.getContext().get("tenantId"));
    }
}

String response = ChatClient.create(chatModel)
        .prompt("Tell me more about the customer with ID 42")
        .tools(new CustomerTools())
        .toolContext(Map.of("tenantId", "acme"))     // not shown to the model
        .call()
        .content();
```

---

## 23. Return direct

**Key Points**
- Default: tool result goes **back to the model** to continue.
- With **`returnDirect = true`**: result **skips the model** and goes **straight to the caller**.
- Good when the tool output **is the final answer** (e.g., RAG retrieval) and another model round trip only adds latency.
- If the model requests **multiple tool calls** in one round, `returnDirect` is honored **only if ALL called tools** have `returnDirect = true`. Otherwise results go back to the model.
- Programmatic tools: set via `ToolMetadata`.

**Java Example**
```java
@Tool(description = "Retrieve customer information", returnDirect = true)
Customer getCustomerInfo(Long id) { ... }

ToolMetadata toolMetadata = ToolMetadata.builder()
    .returnDirect(true)
    .build();
```

---

## 24. Result conversion

**Key Points**
- Tool results must become a **String** for the model.
- **`ToolCallResultConverter`** does it: `String convert(Object result, Type returnType)`.
- Default: **`DefaultToolCallResultConverter`** (Jackson JSON).
- Custom: set on `@Tool(resultConverter = ...)` or via `ToolMetadata`.

**Use Cases**
- Shorten big results, hide fields, custom text format.

**Java Example**
```java
public class ShortCustomerConverter implements ToolCallResultConverter {
    @Override
    public String convert(Object result, Type returnType) {
        Customer c = (Customer) result;
        return c.name() + " (" + c.city() + ")";     // only what the model needs
    }
}

@Tool(description = "Retrieve customer information", resultConverter = ShortCustomerConverter.class)
Customer getCustomerInfo(Long id) { ... }
```

---

## 25. Method tool limitations

These types are **not supported** as parameters or return values for `MethodToolCallback`:
- `Optional` → use `@Nullable` or `@ToolParam(required = false)`.
- Async types: `CompletableFuture`, `Future`.
- Reactive types: `Flow`, `Mono`, `Flux`.
- Functional types: `Function`, `Supplier`, `Consumer`.

**Why**: they cannot be serialized to the model and have no schema the model can understand.

```java
// BAD
@Tool(description = "Find user") Mono<User> findUser(Long id) { ... }

// GOOD (block or fetch the value inside the tool)
@Tool(description = "Find user") User findUser(Long id) { return userService.findUser(id).block(); }
```

---

## 26. Exception handling

**Key Points**
- When a tool throws, it is wrapped in **`ToolExecutionException`** and given to **`ToolExecutionExceptionProcessor`**.
- The processor can: **send an error message back to the model** (so it can recover/apologize) or **re-throw** for the caller.
- **Default** `DefaultToolExecutionExceptionProcessor`:
  - Sends the message of any **`RuntimeException`** back to the model.
  - **Always re-throws** checked exceptions and `Error`s.
- Property: `spring.ai.tools.throw-exception-on-error` (default **false**): `true` = throw all tool errors to the caller; `false` = send to model.
- Or replace the bean.
- Your own `ToolCallback`: throw **`ToolExecutionException`** from `call()` on failure.
- **Security tip**: error messages go to the model, so do not include secrets or stack traces in exception messages.

**Java Example**
```java
@Bean
ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
    return new DefaultToolExecutionExceptionProcessor(true);   // throw errors to caller
}
```
```properties
spring.ai.tools.throw-exception-on-error=false
```

---

## 27. Tool call limits

**Key Points**
- `DefaultToolCallingManager` limits tool calls **per turn** to stop **runaway loops**.
- Defaults apply even with no config: **40 calls per tool**, **150 total calls**.
- Builder options: `maxCallsPerTool(n)`, `maxCallsPerTool("name", n)`, `excludeToolFromLimit("name")`, `maxTotalToolCalls(n)`, `onLimitExceeded(...)`, `unlimitedCallsPerTool()`, `unlimitedTotalToolCalls()`.
- **Counting**:
  - Per **turn** only: counts `ToolResponseMessage`s **from the last `UserMessage` onward**. Memory history replayed outside the loop does **not** inflate counts.
  - Counts are **recomputed** every call from `prompt.getInstructions()`; nothing cached → one `ToolCallingManager` is **safe to share** across concurrent requests.
  - Disabling the advisor's internal history does **not** affect counting.
- **When exceeded** (`ToolCallLimitBehavior`):
  - **`THROW`** (default): throws `ToolCallLimitExceededException` (has tool name — `null` for total limit —, the limit, and the partial `ToolExecutionResult`). `ToolCallingAdvisor` catches it and returns **one `Generation`** as the final response with finish reason `ToolCallLimitExceededException.FINISH_REASON`; earlier successful calls are kept under metadata key `METADATA_PARTIAL_TOOL_RESPONSES`.
  - **`RETURN_ERROR_RESPONSE`**: skips the call and returns an **error `ToolResponse`** to the model, so the conversation **continues**. Best also when calling `ToolCallingManager.executeToolCalls(...)` directly.

| Property | Meaning | Default |
|---|---|---|
| `spring.ai.tools.limits.max-calls-per-tool-default` | Max calls to one tool per turn (`-1` disables) | `40` |
| `spring.ai.tools.limits.max-calls-per-tool.<name>` | Per-tool override (`-1` exempts) | — |
| `spring.ai.tools.limits.excluded-tools` | Tools exempt from per-tool limit | — |
| `spring.ai.tools.limits.max-total-tool-calls` | Max calls across all tools per turn (`-1` disables) | `150` |
| `spring.ai.tools.limits.on-limit-exceeded` | `THROW` or `RETURN_ERROR_RESPONSE` | `THROW` |

**Use Cases**
- Protect against infinite loops and **cost explosions** (e.g., model keeps calling a paid search API).

**Java Example**
```java
ToolCallingManager toolCallingManager = ToolCallingManager.builder()
    .maxCallsPerTool(40)
    .maxCallsPerTool("search", 10)                  // stricter for expensive tool
    .excludeToolFromLimit("getCurrentWeather")
    .maxTotalToolCalls(150)
    .onLimitExceeded(ToolCallLimitBehavior.THROW)   // or RETURN_ERROR_RESPONSE
    .build();
```

---

## 28. Tool resolution (ToolCallbackResolver)

**Key Points**
- Usually tools are passed explicitly (`.tools`, `.defaultTools`). For **name-based lookup at runtime**, Spring AI uses **`ToolCallbackResolver`**: `ToolCallback resolve(String toolName)`.
- Default: **`StaticToolCallbackResolver`**, auto-configured with all `ToolCallback` beans + tools from `ToolCallbackProvider` beans (**MCP providers excluded** to avoid eager listing).
- Replace with a custom bean (database-backed, classpath-scanned, remote).
- Used internally by `ToolCallingManager` for both advisor-controlled and user-controlled execution.
- **Resolution fallback** (`spring.ai.tools.resolution.fallback.enabled`, default **false**): if `true`, a tool the model names that was **not attached to the request** may still be **resolved by name and executed**. Builder: `.resolutionFallbackEnabled(true)`.
- **Security warning**: enabling fallback makes **every tool reachable through the resolver** executable whenever the model names it, **including risky/destructive tools** that were not attached. Enable only if you fully control what the resolver exposes.

**Java Example**
```java
@Bean
ToolCallbackResolver toolCallbackResolver(List<ToolCallback> toolCallbacks) {
    return new StaticToolCallbackResolver(toolCallbacks);
}
```
```properties
spring.ai.tools.resolution.fallback.enabled=false   # keep false unless you really need it
```

---

## 29. Observability and logging

**Key Points**
- **Micrometer observations** under the name **`spring.ai.tool`**. Each captures: tool name + definition metadata, **execution duration**, **tracing context** (if a `Tracer` exists).
- Exporting tool **arguments and results** as span attributes is **off by default** (sensitive data). See *Tool Call Arguments and Result Data* to enable.
- **Logging**: main operations log at **DEBUG**.

```properties
logging.level.org.springframework.ai=DEBUG
```

**Use Cases**
- Measure slow tools, trace a request across tool calls, debug wrong tool usage.

---

## 30. ChatModel tool calling (low level)

**Key Points**
- You can drive **`ChatModel` directly** (no `ChatClient`, no `ToolCallingAdvisor`) for full control of request/response.
- **Important 2.0 change**: the **per-`ChatModel` internal tool loop of 1.x was removed**.
  - Calling `ChatModel` with tools **sends tool definitions** and returns the model's response.
  - Tool calls in that response are **NOT executed automatically**.
  - You **drive the loop yourself**, or use `ChatClient` (auto-registers `ToolCallingAdvisor`).
- See *ChatModel Tool Calling* docs for the API and default-vs-runtime tool precedence.

**Where to Use**
- Custom frameworks and very low-level needs. For normal apps, use `ChatClient`.

---

# Part F — Revision

## 31. Cheat sheet

```java
// Define
class MyTools {
    @Tool(description = "...") String doThing(@ToolParam(description = "...") String x) { ... }
}

// Use per call
chatClient.prompt("...").tools(new MyTools()).call().content();

// Defaults
ChatClient.builder(chatModel).defaultTools(new MyTools(), mcpTools).build();

// Context (not sent to the model)
chatClient.prompt("...").tools(new MyTools()).toolContext(Map.of("tenantId", "acme")).call().content();

// Disable auto loop for one call
.advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
```

| Need | Use |
|---|---|
| Simplest tool | `@Tool` + `@ToolParam` |
| Tool from lambda/method ref | `FunctionToolCallback` |
| Tool built at runtime from a method | `MethodToolCallback` |
| Share tool across app | `ToolCallback` `@Bean` |
| Always-on tools | `.defaultTools(...)` |
| Risky tools | `.tools(...)` per call only |
| Pass tenant/user data safely | `ToolContext` |
| Skip model after tool | `returnDirect = true` |
| Custom result text | `ToolCallResultConverter` |
| Handle tool errors | `ToolExecutionExceptionProcessor` |
| Stop runaway loops | Tool call limits |
| Hundreds of tools | `ToolSearchToolCallingAdvisor` |
| Remote tools | MCP client starter |
| Expose your tools | `@McpTool` + MCP server starter |
| Approval step | User-controlled execution or custom `ToolAdvisor` |
| Capture model reasoning | Tool argument augmentation |
| Store tool messages in memory | Memory advisor **inside** loop + Redis/Neo4j/InMemory |

---

## 32. Common mistakes

1. Writing **no `description`** on `@Tool` → model does not know when to use it.
2. Marking a parameter **required** when the model cannot know it → **hallucinated values**.
3. Adding **risky tools as defaults** → available on every request. Add per call.
4. Using **unsupported types** (`Optional`, `Mono`, `Future`, `Function`) in tool methods.
5. **Duplicate tool names** (local vs MCP) → conflicts. Rename or filter with `McpToolFilter`.
6. Registering **MCP providers expecting auto-wiring** → they are **not** auto-registered; inject and pass them.
7. Using bare **`Function` beans + `toolNames(...)`** from 1.x → removed in 2.0. Use `ToolCallback` beans.
8. Expecting `ChatModel.call()` to **run tools** in 2.0 → it does not. Use `ChatClient` or drive the loop yourself.
9. Registering **two `ToolAdvisor`s** → error. Only one allowed.
10. Putting memory advisor **inside the loop** with a repository that cannot store tool messages (e.g., JDBC, Cassandra, Mongo) → tool messages lost/filtered.
11. Forgetting that **`@Tool` class must be a Spring bean** (or registered for reflection) for **GraalVM native**.
12. Enabling **resolution fallback** casually → lets the model run tools you did not attach.
13. Putting **secrets in exception messages** → they go back to the model.
14. Sending **sensitive IDs through the prompt** instead of **`ToolContext`**.
15. Setting `returnDirect = true` on only **some** tools in a multi-tool round → ignored; results go to the model.
16. Ignoring **tool call limits** → runaway cost. Tune per-tool limits for expensive tools.
17. **Too many tools** (30+) in one request → worse accuracy and high token cost; use tool search.
18. Fully **opting out of the loop** when a **custom advisor** would do → you lose observability and composition.

---

## 33. Interview quick Q&A

**Q1. What is tool calling?**
The model requests that the app run a function with given arguments; the app runs it and returns the result so the model can continue. It is the base of agentic AI.

**Q2. Who executes the tool: the model or the app?**
The app. The model never gets direct access to your APIs (a key security point).

**Q3. Two main purposes of tools?**
Information retrieval and taking action.

**Q4. What is `ToolCallingAdvisor`?**
An auto-registered recursive advisor that runs the loop: send to model, execute tool calls via `ToolCallingManager`, append results, repeat until no tool calls.

**Q5. What is `ToolCallingManager`?**
Finds the matching `ToolCallback` and executes tool calls (also enforces call limits).

**Q6. Three ways to define tools?**
`@Tool`, `MethodToolCallback`, `FunctionToolCallback`.

**Q7. What does `@Tool(returnDirect = true)` do?**
Returns the tool result straight to the caller, skipping the model (only honored if all tools in that round are return-direct).

**Q8. Are parameters required by default?**
Yes. Use `@ToolParam(required = false)`, `@Nullable`, or the Jackson/Swagger equivalents to make them optional.

**Q9. Why is the required flag important?**
A wrongly required parameter makes the model invent a value (hallucination).

**Q10. `.tools()` vs `.defaultTools()`?**
`.tools()` = per call. `.defaultTools()` = every request. Per-call tools are added to defaults, not replacing them.

**Q11. What is `ToolContext`?**
A way to pass non-model data (tenant ID, user ID) directly to tools. It is never sent to the model.

**Q12. Where does memory advisor go relative to the tool loop?**
Outside by default (stores only final user/assistant messages). Inside (order > `ToolCallingAdvisor` default) to store tool messages.

**Q13. Which repositories support tool messages in 2.0?**
InMemory, Redis, Neo4j. For JDBC with full support use the `spring-ai-session` project.

**Q14. How to handle 100+ tools?**
Enable `ToolSearchToolCallingAdvisor` (progressive tool disclosure) with `spring.ai.chat.client.tool-search-advisor.enabled=true`.

**Q15. How do MCP tools get into ChatClient?**
Inject `SyncMcpToolCallbackProvider` (or async) and pass it via `.tools()` / `.defaultTools()`. It is not auto-registered, to avoid startup network calls.

**Q16. How to expose your own tools via MCP?**
Use `@McpTool` / `@McpToolParam` and the MCP server starter.

**Q17. What if a local and MCP tool share a name?**
Rename one or use `McpToolFilter`. The MCP prefix generator only handles duplicates between MCP servers.

**Q18. What is tool argument augmentation?**
Adding extra fields (e.g., `innerThought`) to a tool's schema; a consumer reads them while the original tool gets only its normal args.

**Q19. How to take control of the loop?**
`AdvisorParams.toolCallingAdvisorAutoRegister(false)` per call, or `spring.ai.chat.client.tool-calling.enabled=false` globally; then run `ToolCallingManager.executeToolCalls(...)` yourself. Prefer a custom `ToolAdvisor` when possible.

**Q20. What is `ToolAdvisor`?**
A marker interface for custom tool advisors; `DefaultChatClient` enforces exactly one in the chain.

**Q21. Name the hook methods in `ToolCallingAdvisor`.**
`doInitializeLoop`, `doBeforeCall`, `doAfterCall`, `doFinalizeLoop` (and stream versions).

**Q22. What happens when a tool throws?**
Wrapped in `ToolExecutionException`; default processor sends `RuntimeException` messages to the model and rethrows checked exceptions/Errors. Change with `spring.ai.tools.throw-exception-on-error`.

**Q23. What are default tool call limits?**
40 per tool and 150 total per turn.

**Q24. `THROW` vs `RETURN_ERROR_RESPONSE`?**
`THROW` ends with a limit-exceeded response (partial results saved in metadata). `RETURN_ERROR_RESPONSE` tells the model the limit was hit so the conversation continues.

**Q25. What is the resolution fallback and why is it risky?**
Lets a tool known to the resolver but not attached to the request be executed by name. Risky because it can expose destructive tools; keep it off unless you control the resolver.

**Q26. Types not allowed in method tools?**
`Optional`, `CompletableFuture`/`Future`, `Flow`/`Mono`/`Flux`, and `Function`/`Supplier`/`Consumer`.

**Q27. What changed from 1.x to 2.0?**
Tool loop moved into `ChatClient` advisor chain (`ToolCallingAdvisor`); the per-`ChatModel` loop and `SpringBeanToolCallbackResolver` bare-`Function`-bean pattern were removed.

**Q28. Does `ChatModel.call()` run tools in 2.0?**
No. It returns the tool calls; you must run them yourself or use `ChatClient`.
