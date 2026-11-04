# Spring AI ChatClient API — Effective Notes

> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

1. [What is ChatClient?](#1-what-is-chatclient)
2. [Prompt, Messages and Options](#2-prompt-messages-and-options)
3. [Creating a ChatClient](#3-creating-a-chatclient)
4. [Multiple ChatClients (same model type)](#4-multiple-chatclients-same-model-type)
5. [ChatClients for different model types](#5-chatclients-for-different-model-types)
6. [Multiple OpenAI-compatible endpoints](#6-multiple-openai-compatible-endpoints)
7. [Fluent API: three prompt() methods](#7-fluent-api-three-prompt-methods)
8. [Responses: String, ChatResponse, Entity](#8-responses-string-chatresponse-entity)
9. [EntityParamSpec (reliability switches)](#9-entityparamspec-reliability-switches)
10. [Streaming responses](#10-streaming-responses)
11. [Prompt templates and TemplateRenderer](#11-prompt-templates-and-templaterenderer)
12. [call() vs stream() return values](#12-call-vs-stream-return-values)
13. [Message metadata](#13-message-metadata)
14. [Defaults and runtime override](#14-defaults-and-runtime-override)
15. [mutate() — copy a client](#15-mutate--copy-a-client)
16. [Advisors](#16-advisors)
17. [Logging with SimpleLoggerAdvisor](#17-logging-with-simpleloggeradvisor)
18. [Tool calling](#18-tool-calling)
19. [Chat memory](#19-chat-memory)
20. [Implementation notes: imperative vs reactive](#20-implementation-notes-imperative-vs-reactive)
21. [Quick cheat sheet](#21-quick-cheat-sheet)
22. [Common mistakes](#22-common-mistakes)
23. [Interview quick Q&A](#23-interview-quick-qa)

---

## 1. What is ChatClient?

**Key Points**
- `ChatClient` is the main Spring AI class to talk to an AI model.
- It uses a **fluent API** (method chaining): `prompt().user(...).call().content()`.
- It supports both **synchronous** (`call()`) and **streaming** (`stream()`) styles.
- It helps you build a `Prompt`, which is a list of messages sent to the model.
- Think of it like `RestClient` / `WebClient`, but for AI models.

**Use Cases**
- Chatbots, Q&A endpoints, text summarizer, email writer, code explainer.
- Converting free text into Java objects (data extraction).

**Where to Use**
- Any Spring Boot service (controller, service layer) that needs AI output.

**Problem Solved**
- Without it, you write raw HTTP calls, JSON mapping, and prompt-building code for every AI provider. `ChatClient` hides this and gives one clean API for many providers.

**Java Example**
```java
@RestController
class MyController {

    private final ChatClient chatClient;

    public MyController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/ai")
    String generation(String userInput) {
        return this.chatClient.prompt()
            .user(userInput)   // set user message
            .call()            // synchronous call
            .content();        // get response as String
    }
}
```

---

## 2. Prompt, Messages and Options

**Key Points**
- A **Prompt** = collection of **messages** + optional **options**.
- Two main message types:
  - **User message**: what the user types.
  - **System message**: instruction from your app to guide the model (role, tone, rules).
- Messages can have **placeholders** (like `{voice}`) replaced at runtime.
- **Options** control the model: model name, `temperature` (randomness/creativity), etc.
- Low temperature (e.g., 0.2) = more stable answers. High (e.g., 0.9) = more creative.

**Use Cases**
- System message: "You are a banking support assistant. Never share account numbers."
- Temperature low for data extraction, high for story writing.

**Where to Use**
- Anywhere you want to control *how* the AI behaves, not just *what* it answers.

**Problem Solved**
- Gives control over AI behavior and style, and makes outputs more predictable.

**Java Example**
```java
String answer = chatClient.prompt()
    .system("You are a polite banking assistant. Keep answers short.")
    .user("How do I reset my debit card PIN?")
    .options(ChatOptions.builder().temperature(0.2).build())
    .call()
    .content();
```

---

## 3. Creating a ChatClient

**Key Points**
- `ChatClient` is built using `ChatClient.Builder`.
- **Option A (easiest)**: Spring Boot **autoconfigures** a `ChatClient.Builder` bean. Just inject it.
- **Option B**: Create it by code: `ChatClient.create(chatModel)` or `ChatClient.builder(chatModel).build()`.
- The autoconfigured builder is **prototype-scoped**: each injection gets a **new** builder. So changing one does not affect another.
- Autoconfigured builder also gives you **observability** and **customizers** support.

**Use Cases**
- Single AI provider app (just OpenAI or just Anthropic).

**Where to Use**
- Constructor of your controller/service. Prefer building once and reusing (client is thread-safe to reuse).

**Problem Solved**
- No manual wiring of the model, observability and customizers.

**Java Example**
```java
// Option A: autoconfigured builder
@Service
class SummaryService {
    private final ChatClient chatClient;

    SummaryService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    String summarize(String text) {
        return chatClient.prompt()
            .user("Summarize in 3 lines: " + text)
            .call()
            .content();
    }
}

// Option B: programmatic
ChatClient client = ChatClient.create(chatModel);
```

---

## 4. Multiple ChatClients (same model type)

**Key Points**
- Sometimes you need **many clients** using the **same** model but with **different setup** (different system prompt, tools, advisors).
- Because `ChatClient.Builder` is **prototype-scoped**, you can inject it in many `@Bean` methods and each gets its own builder.

**Use Cases**
- One client for "support bot", another for "SQL helper", another for "translator".

**Where to Use**
- `@Configuration` class that defines multiple `ChatClient` beans.

**Problem Solved**
- Avoids repeating system prompt in every call and avoids one global client with mixed behavior.

**Java Example**
```java
@Configuration
class ChatClientConfig {

    @Bean
    ChatClient defaultChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ChatClient customChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("You are a helpful assistant.").build();
    }
}
```
> Tip: When you have 2 beans of the same type `ChatClient`, inject using `@Qualifier("beanName")`.

---

## 5. ChatClients for different model types

**Key Points**
- Use case: you use **OpenAI and Anthropic** (different `ChatModel`s) in one app.
- **Mistake to avoid**: `ChatClient.create(chatModel)` or `ChatClient.builder(chatModel)` **bypasses** the autoconfigured builder. Then **observability** and `ChatClientBuilderCustomizer` beans are **ignored**.
- **Correct way**: inject `ChatClientBuilderConfigurer` and call `configurer.configure(builder)`. It applies all customizers and wires observability.
- With multiple `ChatModel` beans, Spring gets **ambiguous** for the autoconfigured builder. Fix by marking one `ChatClient` (and maybe one `ChatModel`) as `@Primary`, or define your own `ChatClient.Builder` bean.
- Use `@Qualifier` at injection points to choose which client you want.

**Use Cases**
- Strong model for hard reasoning, cheaper/faster model for simple tasks.
- **Fallback**: if provider A is down, use provider B.
- A/B testing models.
- Let users choose their model.
- Specialized models (one for code, another for creative text).

**Where to Use**
- Cost optimization, high availability, model comparison features.

**Problem Solved**
- Vendor lock-in, single point of failure, and overpaying for simple tasks.

**Java Example**
```java
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient openAiChatClient(OpenAiChatModel chatModel,
            ChatClientBuilderConfigurer configurer,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatClientObservationConvention> chatClientObservationConvention,
            ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
            ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder) {
        return buildChatClient(chatModel, configurer, observationRegistry,
                chatClientObservationConvention, advisorObservationConvention, toolCallingAdvisorBuilder);
    }

    @Bean
    public ChatClient anthropicChatClient(AnthropicChatModel chatModel,
            ChatClientBuilderConfigurer configurer,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatClientObservationConvention> chatClientObservationConvention,
            ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
            ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder) {
        return buildChatClient(chatModel, configurer, observationRegistry,
                chatClientObservationConvention, advisorObservationConvention, toolCallingAdvisorBuilder);
    }

    private ChatClient buildChatClient(ChatModel chatModel,
            ChatClientBuilderConfigurer configurer,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatClientObservationConvention> chatClientObservationConvention,
            ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
            ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder) {

        ChatClient.Builder builder = ChatClient.builder(chatModel,
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
                chatClientObservationConvention.getIfUnique(),
                advisorObservationConvention.getIfUnique(),
                toolCallingAdvisorBuilder.getIfAvailable());

        return configurer.configure(builder).build();   // applies customizers + observability
    }
}

// Using them with @Qualifier
@Service
class AiRouterService {
    private final ChatClient openAi;
    private final ChatClient anthropic;

    AiRouterService(@Qualifier("openAiChatClient") ChatClient openAi,
                    @Qualifier("anthropicChatClient") ChatClient anthropic) {
        this.openAi = openAi;
        this.anthropic = anthropic;
    }

    // Simple fallback example
    String ask(String question) {
        try {
            return openAi.prompt(question).call().content();
        } catch (Exception ex) {
            return anthropic.prompt(question).call().content();
        }
    }
}
```

---

## 6. Multiple OpenAI-compatible endpoints

**Key Points**
- Many providers (Groq, etc.) copy the OpenAI API format.
- You can create **many `OpenAiChatModel` objects** with different `baseUrl`, `apiKey`, `model`, `temperature`.
- Then wrap each with `ChatClient.builder(model).build()`.
- Keep API keys in **environment variables**, never in code.
- Note: this manual way skips autoconfigured observability (see Section 5).

**Use Cases**
- Compare answers from Llama3 on Groq vs GPT-4 on OpenAI.
- Use a cheaper provider for bulk work.

**Where to Use**
- Multi-provider platforms, benchmarking tools, cost-routing layer.

**Problem Solved**
- Use many providers with the same code, no new SDK for each.

**Java Example**
```java
@Service
public class MultiModelService {

    private static final Logger logger = LoggerFactory.getLogger(MultiModelService.class);

    public void multiClientFlow() {
        try {
            OpenAiChatModel groqModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                    .baseUrl("https://api.groq.com/openai/v1")
                    .apiKey(System.getenv("GROQ_API_KEY"))
                    .model("llama3-70b-8192")
                    .temperature(0.5)
                    .build())
                .build();

            OpenAiChatModel gpt4Model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                    .baseUrl("https://api.openai.com")
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .model("gpt-4")
                    .temperature(0.7)
                    .build())
                .build();

            String prompt = "What is the capital of France?";

            String groqResponse = ChatClient.builder(groqModel).build().prompt(prompt).call().content();
            String gpt4Response = ChatClient.builder(gpt4Model).build().prompt(prompt).call().content();

            logger.info("Groq response: {}", groqResponse);
            logger.info("GPT-4 response: {}", gpt4Response);
        } catch (Exception e) {
            logger.error("Error in multi-client flow", e);
        }
    }
}
```

---

## 7. Fluent API: three prompt() methods

**Key Points**
- `prompt()` — no argument. Start building: `.system(...)`, `.user(...)`, `.options(...)`, `.advisors(...)`, `.tools(...)`.
- `prompt(Prompt prompt)` — pass a ready `Prompt` object created with the non-fluent API.
- `prompt(String content)` — shortcut. The string becomes the **user message**.

| Method | When to use |
|---|---|
| `prompt()` | Need system + user + options + advisors |
| `prompt(Prompt)` | You already have a `Prompt` object (e.g., built elsewhere) |
| `prompt(String)` | Quick one-line question |

**Use Cases**
- Quick test, full custom request, reusing a pre-built prompt object.

**Where to Use**
- Pick the one that matches how much control you need.

**Problem Solved**
- Flexible entry points: simple things stay simple, complex things stay possible.

**Java Example**
```java
// 1) Full control
chatClient.prompt()
    .system("Answer like a senior Java developer")
    .user("What is a record?")
    .call().content();

// 2) Using a Prompt object
Prompt prompt = new Prompt(new UserMessage("What is a record?"));
chatClient.prompt(prompt).call().content();

// 3) Shortcut (user message only)
chatClient.prompt("What is a record?").call().content();
```

---

## 8. Responses: String, ChatResponse, Entity

### 8.1 `content()` → plain String
- Easiest. Returns only the text answer.
- Use for simple chat endpoints.

### 8.2 `chatResponse()` → full `ChatResponse`
**Key Points**
- Contains the answer **plus metadata**: token usage, finish reason, multiple `Generation`s.
- **Token** ≈ 3/4 of a word. Hosted models charge by tokens, so tracking tokens = tracking cost.

**Use Cases**: cost tracking, usage dashboards, billing per user, debugging.

**Java Example**
```java
ChatResponse chatResponse = chatClient.prompt()
    .user("Tell me a joke")
    .call()
    .chatResponse();

String text = chatResponse.getResult().getOutput().getText();
Integer totalTokens = chatResponse.getMetadata().getUsage().getTotalTokens();
```

### 8.3 `chatClientResponse()` → `ChatClientResponse`
**Key Points**
- Gives `ChatResponse` **+ execution context** (data added by advisors).
- Example: in **RAG**, you can see which documents were retrieved.

**Use Cases**: show "sources used" in a RAG answer, debugging advisors.

**Java Example**
```java
ChatClientResponse resp = chatClient.prompt()
    .user("What is our refund policy?")
    .call()
    .chatClientResponse();

ChatResponse chatResponse = resp.chatResponse();
Map<String, Object> context = resp.context();   // advisor data, e.g. retrieved docs
```

### 8.4 `entity()` → Java object
**Key Points**
- Maps the AI text answer (JSON) to a **Java record/class**.
- For generic types like `List<T>`, use `ParameterizedTypeReference`.
- Other overloads: with `StructuredOutputConverter`, and with `Consumer<EntityParamSpec>`.

**Use Cases**
- Extract structured data: invoice details from text, resume parsing, sentiment + score, classification.

**Where to Use**
- When another piece of code (not a human) will read the AI answer.

**Problem Solved**
- No manual JSON parsing; type-safe results.

**Java Example**
```java
record ActorFilms(String actor, List<String> movies) {}

ActorFilms actorFilms = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorFilms.class);

List<ActorFilms> list = chatClient.prompt()
    .user("Generate the filmography of 5 movies for Tom Hanks and Bill Murray.")
    .call()
    .entity(new ParameterizedTypeReference<List<ActorFilms>>() {});
```

### 8.5 `responseEntity()` → both `ChatResponse` + entity
**Key Points**
- Returns a `ResponseEntity` holding **the full ChatResponse (metadata)** and **the mapped entity** in **one call**.
- Better than calling twice (which costs double tokens).

**Use Cases**: need the object **and** token usage together (e.g., save result + log cost).

**Java Example**
```java
ResponseEntity<ChatResponse, ActorFilms> result = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .responseEntity(ActorFilms.class);

ChatResponse meta = result.response();
ActorFilms data = result.entity();
```

**Summary Table**

| Method | Returns | Use when |
|---|---|---|
| `content()` | `String` | Only text needed |
| `chatResponse()` | `ChatResponse` | Need tokens/metadata |
| `chatClientResponse()` | `ChatClientResponse` | Need advisor context (RAG docs) |
| `entity(...)` | Java type | Need structured object |
| `responseEntity(...)` | Response + Java type | Need both |

---

## 9. EntityParamSpec (reliability switches)

**Key Points**
- Optional second argument to every `entity()` / `responseEntity()` overload: `Consumer<EntityParamSpec>`.
- Two independent switches (can combine):
  - `validateSchema()` — checks JSON against the entity schema. On failure, **retries automatically** with error feedback to the model.
  - `useProviderStructuredOutput()` — sends the schema to the provider as an **API-level constraint** instead of plain prompt text (stronger guarantee, only for supported providers).

**Use Cases**
- Production extraction pipelines where bad JSON breaks things.

**Where to Use**
- Anywhere AI output feeds directly into business logic, DB, or another service.

**Problem Solved**
- LLMs sometimes return broken or wrong-shaped JSON. These switches reduce failures and avoid manual retry code.

**Java Example**
```java
ActorFilms actorFilms = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorFilms.class, spec -> spec
        .useProviderStructuredOutput()
        .validateSchema());
```
> Check the *Structured Output* reference for supported providers and limits.

---

## 10. Streaming responses

**Key Points**
- `stream()` gives a **`Flux`** (reactive stream). Tokens arrive **as they are generated**.
- Options: `Flux<String> content()`, `Flux<ChatResponse> chatResponse()`, `Flux<ChatClientResponse> chatClientResponse()`.
- **No direct `entity()` for streaming yet.** Workaround: collect the whole stream, then use `BeanOutputConverter` to convert.
- Streaming needs the **reactive stack** (e.g., `spring-boot-starter-webflux`).

**Use Cases**
- ChatGPT-like typing effect in UI, long answers, reducing "time to first word".

**Where to Use**
- Chat UIs, SSE (Server-Sent Events) endpoints, long text generation.

**Problem Solved**
- Users do not wait 10–30 seconds staring at a blank screen.

**Java Example**
```java
// Simple streaming
Flux<String> output = chatClient.prompt()
    .user("Tell me a joke")
    .stream()
    .content();

// SSE endpoint
@GetMapping(value = "/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<String> stream(@RequestParam String q) {
    return chatClient.prompt().user(q).stream().content();
}

// Stream -> entity workaround
var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<ActorsFilms>>() {});

Flux<String> flux = chatClient.prompt()
    .user(u -> u.text("""
                Generate the filmography for a random actor.
                {format}
              """)
        .param("format", converter.getFormat()))
    .stream()
    .content();

String content = String.join("", flux.collectList().block());
List<ActorsFilms> actorFilms = converter.convert(content);
```

---

## 11. Prompt templates and TemplateRenderer

**Key Points**
- Write prompt text with **variables** like `{composer}`; fill them with `.param("composer", "value")`.
- Works for both `user` and `system` text.
- Internally uses `PromptTemplate` + a `TemplateRenderer`.
- **Default renderer**: `StTemplateRenderer` (StringTemplate engine). Variables use `{ }`.
- `NoOpTemplateRenderer` = no template processing at all.
- **Problem with JSON**: `{ }` conflicts with JSON in prompts. Fix: change delimiters, e.g., `< >`.
- `.templateRenderer(...)` on ChatClient only affects text you set via `.user()` / `.system()`. It does **not** change templates inside advisors like `QuestionAnswerAdvisor` (they have their own customization).

**Use Cases**
- Reusable prompts: "Translate {text} to {language}".
- Prompts that include JSON examples.

**Where to Use**
- Any prompt with dynamic user data.

**Problem Solved**
- No messy string concatenation; cleaner and safer prompts.

**Java Example**
```java
// Default {} variables
String answer = ChatClient.create(chatModel).prompt()
    .user(u -> u
        .text("Tell me the names of 5 movies whose soundtrack was composed by {composer}")
        .param("composer", "John Williams"))
    .call()
    .content();

// Custom delimiters < > (good when prompt contains JSON)
String answer2 = ChatClient.create(chatModel).prompt()
    .user(u -> u
        .text("Tell me the names of 5 movies whose soundtrack was composed by <composer>")
        .param("composer", "John Williams"))
    .templateRenderer(StTemplateRenderer.builder()
        .startDelimiterToken('<')
        .endDelimiterToken('>')
        .build())
    .call()
    .content();
```

---

## 12. call() vs stream() return values

**Key Points**
- `call()` = synchronous (blocking). `stream()` = reactive streaming.
- **Important**: `call()` itself **does not call the AI model**. It only chooses the mode. The real model call happens when you invoke `content()`, `chatResponse()`, `entity()`, `responseEntity()` etc.
- Same idea for `stream()`.

| After `call()` | After `stream()` |
|---|---|
| `String content()` | `Flux<String> content()` |
| `ChatResponse chatResponse()` | `Flux<ChatResponse> chatResponse()` |
| `ChatClientResponse chatClientResponse()` | `Flux<ChatClientResponse> chatClientResponse()` |
| `entity(...)`, `responseEntity(...)` | (no entity support yet) |

**Use Cases / Where to Use**
- Choose `call()` for REST APIs returning full answer; `stream()` for live UI.

**Problem Solved**
- One API shape for both blocking and non-blocking styles.

**Java Example**
```java
var spec = chatClient.prompt().user("Hello").call();  // NO model call yet
String text = spec.content();                         // model is called HERE
```

---

## 13. Message metadata

**Key Points**
- You can attach **key-value metadata** to user and system messages.
- Metadata is extra info about the message (messageId, userId, priority, version...).
- Can be read later using `message.getMetadata()` — useful inside **advisors** or when checking conversation history.
- **Validation rules** (throws `IllegalArgumentException`):
  - key cannot be null or empty
  - value cannot be null
  - in a `Map`, no null keys or values
- You can also set **default metadata** at builder level.

**Use Cases**
- Tracing: attach `requestId`/`userId` and read it in a logging advisor.
- Auditing, priority-based handling, tenant info in multi-tenant apps.

**Where to Use**
- Custom advisors, audit logs, analytics.

**Problem Solved**
- Carry context with a message without putting it inside the prompt text.

**Java Example**
```java
// Individual entries
String response = chatClient.prompt()
    .user(u -> u.text("What's the weather like?")
        .metadata("messageId", "msg-123")
        .metadata("userId", "user-456")
        .metadata("priority", "high"))
    .call()
    .content();

// Many at once
Map<String, Object> userMetadata = Map.of(
    "messageId", "msg-123",
    "userId", "user-456",
    "timestamp", System.currentTimeMillis()
);
chatClient.prompt()
    .user(u -> u.text("What's the weather like?").metadata(userMetadata))
    .call()
    .content();

// System message metadata
chatClient.prompt()
    .system(s -> s.text("You are a helpful assistant.")
        .metadata("version", "1.0"))
    .user("Tell me a joke")
    .call()
    .content();

// Defaults at builder level
@Bean
ChatClient chatClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem(s -> s.text("You are a helpful assistant")
            .metadata("assistantType", "general")
            .metadata("version", "1.0"))
        .defaultUser(u -> u.text("Default user context")
            .metadata("sessionId", "default-session"))
        .build();
}
```

---

## 14. Defaults and runtime override

**Key Points**
- Set common settings **once** at builder level using `default...` methods. Then runtime code only passes the user text.
- Defaults list:
  - `defaultOptions(ChatOptions)` — model/temperature etc.
  - `defaultTools(Object...)` — tools for every request (`ToolCallback`, `ToolCallbackProvider`, or POJO with `@Tool`).
  - `defaultToolContext(Map)` — shared context for tools.
  - `defaultSystem(...)` / `defaultUser(...)` — default texts (String, Resource, or Consumer).
  - `defaultTemplateRenderer(...)`
  - `defaultAdvisors(...)`
- **Override at runtime** using the same method name **without** `default`: `options()`, `tools()`, `toolContext()`, `messages()`, `system()`, `user()`, `templateRenderer()`, `advisors()`.
- Defaults can have **parameters** (like `{voice}`) filled at runtime.

**Use Cases**
- Company-wide assistant personality.
- Always-on logging/memory advisors.
- Per-request voice/language/tenant parameters.

**Where to Use**
- `@Configuration` class that builds the `ChatClient` bean.

**Problem Solved**
- Removes repeated code from every controller/service call; keeps config in one place.

**Java Example**
```java
@Configuration
class Config {
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("You are a friendly chat bot that answers question in the voice of a {voice}")
            .build();
    }
}

@RestController
class AIController {
    private final ChatClient chatClient;

    AIController(ChatClient chatClient) { this.chatClient = chatClient; }

    @GetMapping("/ai")
    Map<String, String> completion(
            @RequestParam(defaultValue = "Tell me a joke") String message,
            String voice) {
        return Map.of("completion",
            chatClient.prompt()
                .system(sp -> sp.param("voice", voice))  // fill the default template
                .user(message)
                .call()
                .content());
    }
}
```

---

## 15. mutate() — copy a client

**Key Points**
- `mutate()` creates a **new builder** copying existing settings.
  - `ChatClient.mutate()` → builder with the client's **default** settings.
  - `ChatClientRequestSpec.mutate()` → builder with the **request's current** settings.
- Then you change what you need and `.build()`.

**Use Cases**
- Base client + small variations (same advisors, different system prompt).
- Derive a "premium" client from a "standard" one.

**Where to Use**
- When many clients share most config and differ in 1–2 settings.

**Problem Solved**
- Avoid copy-pasting the full configuration.

**Java Example**
```java
ChatClient baseClient = builder
    .defaultAdvisors(new SimpleLoggerAdvisor())
    .defaultSystem("You are a helpful assistant")
    .build();

// New client keeps the logger advisor, changes only system prompt
ChatClient translatorClient = baseClient.mutate()
    .defaultSystem("You are a translator. Translate everything to Hindi.")
    .build();
```

---

## 16. Advisors

**Key Points**
- Advisors **intercept** the request/response and can **modify or enrich** them. Like **servlet filters / Spring AOP**, but for AI calls.
- Common context to add:
  - **Your own data** (RAG) — model has not seen it; added context gets priority.
  - **Conversation history** — chat model API is **stateless**; history must be sent every time.
- Configure via `AdvisorSpec`: `param(k, v)`, `params(map)`, `advisors(...)`.
- **Order matters**: advisors run in the order added; each passes changes to the next.
- Examples: `MessageChatMemoryAdvisor` (history), `QuestionAnswerAdvisor` (RAG), `SimpleLoggerAdvisor` (logs), `ToolCallingAdvisor` (tools).
- `ChatMemory.CONVERSATION_ID` **must** be passed with `.param()` on every call using a memory advisor, or you get `IllegalArgumentException`.

**Use Cases**
- Chat with memory, RAG over company docs, logging, guardrails, caching, safety filters.

**Where to Use**
- Cross-cutting behavior you want in many AI calls without touching each call.

**Problem Solved**
- Keeps controller code clean; adds memory/RAG/logging in a reusable way.

**Java Example**
```java
String answer = ChatClient.builder(chatModel)
    .build()
    .prompt()
    .advisors(a -> a
        .advisors(
            MessageChatMemoryAdvisor.builder(chatMemory).build(),   // 1st: add history
            QuestionAnswerAdvisor.builder(vectorStore).build()      // 2nd: search using question + history
        )
        .param(ChatMemory.CONVERSATION_ID, conversationId))         // REQUIRED for memory
    .user(userText)
    .call()
    .content();
```
Flow: Memory advisor adds history → QA advisor searches vector store (using question + history) → final prompt goes to model.

---

## 17. Logging with SimpleLoggerAdvisor

**Key Points**
- Logs the **request and response** of ChatClient. Good for debugging.
- Add it **toward the end** of the advisor chain (so it logs the final prompt after other advisors changed it).
- Enable DEBUG logging for the advisor package:
  ```properties
  logging.level.org.springframework.ai.chat.client.advisor=DEBUG
  ```
- Customize what is logged using constructor: `requestToString`, `responseToString`, `order`.
- **Warning**: Do not log sensitive data (PII, secrets) in production.

**Use Cases**
- Debug "why did the AI answer like this?" — see the actual final prompt.
- Audit during development.

**Where to Use**
- Dev/test environments; carefully in prod.

**Problem Solved**
- AI calls are a black box; this shows exactly what was sent and received.

**Java Example**
```java
ChatResponse response = ChatClient.create(chatModel).prompt()
    .advisors(new SimpleLoggerAdvisor())
    .user("Tell me a joke?")
    .call()
    .chatResponse();

// Custom logger
SimpleLoggerAdvisor customLogger = new SimpleLoggerAdvisor(
    request -> "Custom request: " + request.prompt().getUserMessage(),
    response -> "Custom response: " + response.getResult(),
    0
);
```
For observability (metrics/traces), see Spring AI Observability guide.

---

## 18. Tool calling

**Key Points**
- **Tool calling** lets the model ask your Java code to run a function (get date, query DB, call API).
- `ChatClient` **always auto-registers** a `ToolCallingAdvisor` (default order `Ordered.HIGHEST_PRECEDENCE + 300`). This also handles tools added dynamically by another advisor.
- Register tools with `.tools(...)` per request or `defaultTools(...)` globally.
- **Disable auto-registration**:
  - Globally: `spring.ai.chat.client.tool-calling.enabled=false` (tools still sent to model, but calls are **not executed automatically**).
  - Per call: `.advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))`.
  - Or give your own `ToolCallingAdvisor` / any `ToolAdvisor` — auto-registration is then skipped automatically.
- **Customize**:
  - Property `spring.ai.chat.client.tool-calling.advisor-order` — position in chain. Must be **lower** than advisors that should run **inside** the tool loop (repeat each iteration) and **higher** than advisors that should run **once** per request.
  - Declare a `ToolCallingAdvisor.Builder<?>` bean (e.g., custom `ToolCallingManager`). Your bean wins.
  - Without Spring Boot, pass a configured builder to `ChatClient.builder(...)`.
- **`ToolAdvisor`** = marker interface saying "this chain already handles tools", so no second advisor is added.
- **User-controlled execution**: disable auto-registration and drive the loop yourself (useful to push each step to UI).

**Use Cases**
- "What is the status of order 123?" → tool queries DB.
- Date/time, weather, currency conversion, ticket creation, calculators.
- Human approval before running risky tools (user-controlled loop).

**Where to Use**
- When the AI needs **live or private data**, or must **perform actions**.

**Problem Solved**
- Models do not know current/private data and cannot act. Tools bridge that gap safely.

**Java Example**
```java
// Tool class
class DateTimeTools {
    @Tool(description = "Get the current date and time")
    String getCurrentDateTime() {
        return LocalDateTime.now().toString();
    }
}

// Use it (ToolCallingAdvisor auto-registered)
String response = ChatClient.builder(chatModel)
    .build()
    .prompt("What day is tomorrow?")
    .tools(new DateTimeTools())
    .call()
    .content();

// Disable for ONE call (you control the loop)
chatClient.prompt("What day is tomorrow?")
    .tools(new DateTimeTools())
    .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
    .call()
    .content();

// Custom builder bean
@Bean
ToolCallingAdvisor.Builder<?> toolCallingAdvisorBuilder(ToolCallingManager myToolCallingManager) {
    return ToolCallingAdvisor.builder()
        .toolCallingManager(myToolCallingManager)
        .advisorOrder(Ordered.LOWEST_PRECEDENCE);
}

// Without Spring Boot
ToolCallingManager customManager = ToolCallingManager.builder().build();

ChatClient client = ChatClient
    .builder(chatModel, observationRegistry, null, null,
             ToolCallingAdvisor.builder().toolCallingManager(customManager))
    .build();
```
```properties
# application.properties
spring.ai.chat.client.tool-calling.enabled=true
spring.ai.chat.client.tool-calling.advisor-order=0
```

---

## 19. Chat memory

**Key Points**
- AI model APIs are **stateless** — they forget previous messages. You must send history every time.
- `ChatMemory` stores conversation messages: add, get, clear.
- Built-in implementation: **`MessageWindowChatMemory`**:
  - Keeps a **window** of last messages (default **20**).
  - Older messages are removed when limit is crossed.
  - **System messages are preserved**.
  - If a new system message is added, **older system messages are removed**.
- Storage is done by **`ChatMemoryRepository`**: `InMemoryChatMemoryRepository`, `JdbcChatMemoryRepository`, `CassandraChatMemoryRepository`, `Neo4jChatMemoryRepository`, `MongoChatMemoryRepository`, `RedisChatMemoryRepository`.
- **`ChatMemory.CONVERSATION_ID` is required** on every call: `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))`. No default. Missing → `IllegalArgumentException`.
- **`MemoryAdvisor`** is a marker interface (extended by `BaseChatMemoryAdvisor`). `DefaultChatClient` uses it to detect memory advisors during auto-registration. Custom memory advisors should implement it.

**Use Cases**
- Multi-turn chatbot ("what about the second one?" needs earlier context).
- Customer support with conversation continuity per user/session.

**Where to Use**
- Any conversational feature. Pick the repository by need: In-memory (dev/test), JDBC/Redis/Mongo (production, restart-safe).

**Problem Solved**
- Model forgetting context; token cost explosion (window limits history size).

**Java Example**
```java
// Build memory (window of last 20 messages)
ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(new InMemoryChatMemoryRepository())
    .maxMessages(20)
    .build();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
    .build();

// Each call MUST pass a conversation id
String reply = chatClient.prompt()
    .user("My name is Hemant")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "user-42-session-1"))
    .call()
    .content();

String reply2 = chatClient.prompt()
    .user("What is my name?")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "user-42-session-1"))
    .call()
    .content();   // can answer because memory is used
```
> Use a different `CONVERSATION_ID` per user/session, otherwise users will see each other's history.

---

## 20. Implementation notes: imperative vs reactive

**Key Points**
- ChatClient mixes **imperative** and **reactive** models (unusual).
- **Streaming → Reactive stack only.** Imperative apps must add `spring-boot-starter-webflux`.
- **Non-streaming → Servlet stack only.** Reactive apps must add `spring-boot-starter-web` and expect some **blocking** calls.
- Customizing HTTP behavior of a model: configure **both** `RestClient` (non-streaming) **and** `WebClient` (streaming).
- **Tool calling is blocking** (imperative). Result: Micrometer observations can be **partial/disconnected** (ChatClient span and tool-calling span not linked; the first stays incomplete).
- Built-in advisors: **blocking** for normal calls, **non-blocking** for streaming. The Reactor `Scheduler` for streaming advisors is configurable on each Advisor's builder.

**Use Cases / Where to Use**
- Deciding dependencies and thread model for your AI service.

**Problem Solved**
- Avoids runtime surprises: "why is my reactive app blocking?" or "why streaming does not work?".

**Maven example**
```xml
<!-- Needed for streaming -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- Needed for non-streaming (call()) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

---

## 21. Quick cheat sheet

```java
// Simple
chatClient.prompt("Hi").call().content();

// System + user + options
chatClient.prompt()
    .system("You are ...")
    .user("Question")
    .options(ChatOptions.builder().temperature(0.2).build())
    .call().content();

// Template
chatClient.prompt()
    .user(u -> u.text("Translate {text}").param("text", "hello"))
    .call().content();

// Entity
chatClient.prompt().user("...").call().entity(MyRecord.class);

// Entity + safety
chatClient.prompt().user("...").call()
    .entity(MyRecord.class, s -> s.useProviderStructuredOutput().validateSchema());

// Tokens
chatClient.prompt().user("...").call().chatResponse()
    .getMetadata().getUsage().getTotalTokens();

// Stream
chatClient.prompt().user("...").stream().content();

// Memory
chatClient.prompt().user("...")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))
    .call().content();

// Tools
chatClient.prompt().user("...").tools(new MyTools()).call().content();
```

| Need | Use |
|---|---|
| Just text | `content()` |
| Token/cost info | `chatResponse()` |
| RAG sources | `chatClientResponse()` |
| Java object | `entity()` |
| Object + metadata | `responseEntity()` |
| Typing effect | `stream()` |
| Remember chat | `MessageChatMemoryAdvisor` + `CONVERSATION_ID` |
| Private data | `QuestionAnswerAdvisor` (RAG) |
| Live data / actions | Tools |
| Debug prompt | `SimpleLoggerAdvisor` |
| Many models | `ChatClientBuilderConfigurer` + `@Qualifier` / `@Primary` |

---

## 22. Common mistakes

1. Using `ChatClient.create(chatModel)` with multiple models → **loses observability and customizers**. Use `ChatClientBuilderConfigurer`.
2. Not setting `@Primary` when multiple `ChatModel`/`ChatClient` beans exist → **ambiguity error**.
3. Forgetting `ChatMemory.CONVERSATION_ID` → `IllegalArgumentException`.
4. Using one shared conversation ID for all users → **data leak** between users.
5. Putting JSON in a prompt without changing delimiters → `{}` clashes with template variables.
6. Thinking `call()` triggers the model → it does not; `content()` / `entity()` etc. do.
7. Calling the model twice (once for `entity()`, once for `chatResponse()`) → use `responseEntity()`; saves tokens.
8. Trying streaming without WebFlux → streaming only works on reactive stack.
9. Logging prompts/responses with sensitive data in production.
10. Wrong advisor order (e.g., RAG before memory) → search misses conversation context.
11. Expecting `.templateRenderer()` on ChatClient to change `QuestionAnswerAdvisor`'s internal templates → it won't.
12. Disabling tool-calling auto-registration and forgetting to execute tools yourself → tools never run.

---

## 23. Interview quick Q&A

**Q1. What is ChatClient?**
A fluent API in Spring AI to talk to AI models, supporting sync (`call`) and streaming (`stream`).

**Q2. Difference between system and user message?**
User message = user's input. System message = app's instruction to guide model behavior.

**Q3. Why is `ChatClient.Builder` prototype-scoped?**
So each injection gets a separate builder; configuring one client does not affect others.

**Q4. How do you use two different AI providers in one app?**
Define one `ChatClient` bean per `ChatModel` using `ChatClientBuilderConfigurer`, mark one `@Primary`, inject with `@Qualifier`.

**Q5. Why not `ChatClient.create(chatModel)` for multiple models?**
It bypasses autoconfigured builder → observability and `ChatClientBuilderCustomizer` are ignored.

**Q6. `chatResponse()` vs `chatClientResponse()`?**
`chatResponse()` = model answer + metadata. `chatClientResponse()` = that + advisor execution context (e.g., RAG documents).

**Q7. What do `validateSchema()` and `useProviderStructuredOutput()` do?**
First validates JSON and retries with error feedback. Second sends the schema as a provider API constraint.

**Q8. Does `call()` invoke the model?**
No. It only selects sync mode. The model is invoked by `content()`, `chatResponse()`, `entity()`, `responseEntity()` etc.

**Q9. How does ChatClient remember conversation?**
Using `ChatMemory` + `MessageChatMemoryAdvisor`; model API is stateless. `CONVERSATION_ID` is required on each call.

**Q10. What does `MessageWindowChatMemory` do?**
Keeps last N messages (default 20); evicts older ones; keeps system messages; new system message replaces old ones.

**Q11. How to avoid JSON conflict in prompt templates?**
Use `StTemplateRenderer` with different delimiters like `<` and `>`.

**Q12. What is an Advisor?**
An interceptor that modifies/enriches the prompt and response (memory, RAG, logging, tools). Order of advisors matters.

**Q13. How to stop auto tool execution?**
Property `spring.ai.chat.client.tool-calling.enabled=false`, or per call `AdvisorParams.toolCallingAdvisorAutoRegister(false)`, or provide your own `ToolAdvisor`.

**Q14. Why do streaming needs WebFlux?**
Streaming is supported only on the reactive stack; imperative apps must include WebFlux.

**Q15. Why are tool-calling observations partial?**
Tool calling is imperative/blocking, so ChatClient spans and tool-calling spans are not connected.
