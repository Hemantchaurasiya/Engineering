# Spring AI Chat Model API — Effective Notes

> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

1. [What is the Chat Model API?](#1-what-is-the-chat-model-api)
2. [Big picture: how all classes fit](#2-big-picture-how-all-classes-fit)
3. [ChatModel](#3-chatmodel)
4. [StreamingChatModel](#4-streamingchatmodel)
5. [Prompt](#5-prompt)
6. [Message, Content and MessageType](#6-message-content-and-messagetype)
7. [ChatOptions](#7-chatoptions)
8. [Start-up vs runtime options (override rules)](#8-start-up-vs-runtime-options-override-rules)
9. [Request flow (convert input / convert output)](#9-request-flow-convert-input--convert-output)
10. [ChatResponse](#10-chatresponse)
11. [Generation](#11-generation)
12. [Available implementations](#12-available-implementations)
13. [ChatModel vs ChatClient](#13-chatmodel-vs-chatclient)
14. [Quick cheat sheet](#14-quick-cheat-sheet)
15. [Common mistakes](#15-common-mistakes)
16. [Interview quick Q&A](#16-interview-quick-qa)

---

## 1. What is the Chat Model API?

**Key Points**
- It lets you add **AI chat completion** to your app using pre-trained language models (like GPT).
- How it works:
  1. You send a **prompt** (or part of a conversation).
  2. The model **continues** it based on its training.
  3. The **response** comes back to your app. You show it to the user or use it for more processing.
- Design goals: **simple** and **portable**. You can **switch AI models with minimal code changes**.
- This matches Spring's idea of **modularity and interchangeability** (like `DataSource` or `JdbcTemplate`: same code, different database).
- Companion classes unify communication:
  - **`Prompt`** → input.
  - **`ChatResponse`** → output.
- It handles the hard parts: **request preparation** and **response parsing**.

**Use Cases**
- Chatbots, text generation, summarizers, assistants, code helpers.

**Where to Use**
- Any Spring app that needs AI chat, especially when you may change provider later (cost, quality, privacy).

**Problem Solved**
- Each AI provider has its own SDK, request format, and response format. This API gives **one common interface** so you avoid **vendor lock-in**.

**Java Example**
```java
@Service
class HelloAiService {
    private final ChatModel chatModel;      // same code for OpenAI, Anthropic, Ollama...

    HelloAiService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    String hello() {
        return chatModel.call("Say hello in one line");
    }
}
```

---

## 2. Big picture: how all classes fit

```
   Your code
      |
      v
   Prompt (messages + ChatOptions)
      |
      v
   ChatModel.call(prompt)  /  StreamingChatModel.stream(prompt)
      |            [convert input -> provider format]
      v
   AI Provider (OpenAI, Anthropic, Ollama, ...)
      |            [convert output -> standard format]
      v
   ChatResponse
      |-- ChatResponseMetadata  (tokens, model info)
      |-- List<Generation>
              |-- AssistantMessage (the answer text)
              |-- ChatGenerationMetadata
```

| Class | Role |
|---|---|
| `ChatModel` | Sync call (wait for full answer) |
| `StreamingChatModel` | Streaming call (`Flux`) |
| `Prompt` | Input: messages + options |
| `Message` | One piece of the conversation with a role |
| `ChatOptions` | Settings (model, temperature, max tokens...) |
| `ChatResponse` | Output: results + metadata |
| `Generation` | One answer inside the response |

---

## 3. ChatModel

**Key Points**
- Main interface for **synchronous** (blocking) chat calls.
- Extends `Model<Prompt, ChatResponse>` and `StreamingChatModel` (so every `ChatModel` can also **stream**).
- Two ways to call:
  - `String call(String message)` → **default** method, **simple** (String in, String out). Good for first tests.
  - `ChatResponse call(Prompt prompt)` → **real-world** use. Takes a `Prompt`, returns a rich `ChatResponse`.

```java
public interface ChatModel extends Model<Prompt, ChatResponse>, StreamingChatModel {
    default String call(String message) {...}

    @Override
    ChatResponse call(Prompt prompt);
}
```

**Use Cases**
- Simple Q&A, direct low-level control, writing your own framework on top.

**Where to Use**
- When you need full control (messages, options, metadata). For everyday work, `ChatClient` is easier (see Section 13).

**Problem Solved**
- A common "call the AI" method for all providers.

**Java Example**
```java
// 1) Simple String call
String answer = chatModel.call("What is Spring Boot?");

// 2) Prompt call (recommended for real apps)
Prompt prompt = new Prompt(List.of(
    new SystemMessage("You are a Java mentor. Be short."),
    new UserMessage("What is a record?")
));

ChatResponse response = chatModel.call(prompt);
String text = response.getResult().getOutput().getText();
```

---

## 4. StreamingChatModel

**Key Points**
- For **streaming** answers: you receive the response **piece by piece** as the model generates it.
- Uses reactive **`Flux`** (Project Reactor).
- Two ways:
  - `Flux<String> stream(String message)` → default, simple text chunks.
  - `Flux<ChatResponse> stream(Prompt prompt)` → full `ChatResponse` chunks (with metadata).
- Needs the **reactive stack** (e.g., `spring-boot-starter-webflux`) for streaming endpoints.

```java
public interface StreamingChatModel extends StreamingModel<Prompt, ChatResponse> {
    default Flux<String> stream(String message) {...}

    @Override
    Flux<ChatResponse> stream(Prompt prompt);
}
```

**Use Cases**
- ChatGPT-like typing effect, long answers, live UI updates.

**Where to Use**
- Web/mobile chat UIs, Server-Sent Events (SSE) endpoints.

**Problem Solved**
- Users do not wait 10–30 seconds for a blank screen; they see text as it arrives.

**Java Example**
```java
// Simple text stream
Flux<String> chunks = chatModel.stream("Explain dependency injection");

// Streaming endpoint (SSE)
@GetMapping(value = "/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<String> stream(@RequestParam String q) {
    return chatModel.stream(q);
}

// Full ChatResponse chunks
Flux<ChatResponse> responses = chatModel.stream(new Prompt("Explain Spring Boot"));
responses.subscribe(r -> System.out.print(r.getResult().getOutput().getText()));
```

---

## 5. Prompt

**Key Points**
- `Prompt` is a **`ModelRequest`**. It holds:
  - a **list of `Message`** objects, and
  - optional **model request options** (`ChatOptions`).
- Methods:
  - `getInstructions()` → the **messages** list.
  - `getOptions()` → the `ChatOptions`.

```java
public class Prompt implements ModelRequest<List<Message>> {
    private final List<Message> messages;
    private ChatOptions modelOptions;

    @Override public ChatOptions getOptions() {...}
    @Override public List<Message> getInstructions() {...}
}
```

**Use Cases**
- Multi-turn chats, system rules + user question, per-request model settings.

**Where to Use**
- Every real `ChatModel` call.

**Problem Solved**
- One container for everything the model needs, in a provider-independent way.

**Java Example**
```java
Prompt prompt = new Prompt(
    List.of(
        new SystemMessage("You are a polite banking assistant."),
        new UserMessage("How do I block my card?")
    ),
    ChatOptions.builder().temperature(0.2).build()
);

ChatResponse response = chatModel.call(prompt);
```
> More on prompts and roles: see the *Prompts* notes.

---

## 6. Message, Content and MessageType

**Key Points**
- `Message` holds: **text**, **metadata** (map), and a **`MessageType`** (the role).
- Interfaces:
  - `Content` → `getText()` and `getMetadata()`.
  - `Message extends Content` → adds `getMessageType()`.
  - `MediaContent extends Content` → adds `getMedia()` for **multimodal** (image/audio) messages.
- `MessageType` = the **role** in the conversation (system, user, assistant, tool). Despite the word "type", it means **role**, not a message format.
- Example: OpenAI uses roles like system, user, function, assistant.
- For models **without special roles**, `UserMessage` is the standard category (user questions/instructions).
- Note: this page shows `getText()`. Some older docs show `getContent()`. Use the one in your Spring AI version.

```java
public interface Content {
    String getText();
    Map<String, Object> getMetadata();
}

public interface Message extends Content {
    MessageType getMessageType();
}

public interface MediaContent extends Content {
    Collection<Media> getMedia();
}
```

**Use Cases**
- Text chat, image + text questions, extra info on messages (userId, traceId).

**Where to Use**
- Building prompts manually, reading responses, writing advisors.

**Problem Solved**
- Standard message shape for all providers; the model knows who said what.

**Java Example**
```java
Message system = new SystemMessage("Answer like a senior developer.");
Message user = new UserMessage("Explain @Transactional");

system.getMessageType();   // SYSTEM
user.getText();            // "Explain @Transactional"
user.getMetadata();        // Map of extra info
```

---

## 7. ChatOptions

**Key Points**
- `ChatOptions` = **settings sent to the AI model**. It extends `ModelOptions`.
- It defines a few **portable** options (work across providers):

| Option | Meaning | Easy explanation |
|---|---|---|
| `getModel()` | Model name | Which model to use |
| `getTemperature()` | Randomness | Low = stable answers, high = creative |
| `getMaxTokens()` | Max output length | Limits answer size (and cost) |
| `getTopP()` | Nucleus sampling | Only pick from the most likely words (cumulative probability) |
| `getTopK()` | Top-K sampling | Only pick from the K most likely words |
| `getFrequencyPenalty()` | Penalty for repeated words | Reduces repetition |
| `getPresencePenalty()` | Penalty for already-present topics | Encourages new topics |
| `getStopSequences()` | Stop text(s) | Model stops when it produces these |
| `mutate()` | Copy as a builder | Make a changed copy |

```java
public interface ChatOptions extends ModelOptions {
    String getModel();
    Double getFrequencyPenalty();
    Integer getMaxTokens();
    Double getPresencePenalty();
    List<String> getStopSequences();
    Double getTemperature();
    Integer getTopK();
    Double getTopP();
    ChatOptions.Builder<?> mutate();
}
```

- **Provider-specific** options also exist. Example: OpenAI has `logitBias`, `seed`, `user`. Use the provider's own options class (e.g., `OpenAiChatOptions`).
- Tip: change **temperature OR topP** (not both aggressively) for predictable behavior.

**Use Cases**
- Low temperature for extraction/code; high for creative writing; `maxTokens` to control cost; `stopSequences` to cut output.

**Where to Use**
- Per request (in `Prompt`) or as defaults (in properties/config).

**Problem Solved**
- Control answer style, length, and cost with a standard API.

**Java Example**
```java
// Portable options
ChatOptions options = ChatOptions.builder()
    .model("gpt-4o")
    .temperature(0.2)
    .maxTokens(300)
    .build();

Prompt prompt = new Prompt("Summarize Spring Boot in 3 lines", options);

// Provider-specific options (OpenAI example)
OpenAiChatOptions openAiOptions = OpenAiChatOptions.builder()
    .model("gpt-4o")
    .temperature(0.2)
    .seed(42)                 // provider-specific
    .build();

ChatResponse response = chatModel.call(new Prompt("Hello", openAiOptions));

// Copy and change using mutate()
ChatOptions stricter = options.mutate().temperature(0.0).build();
```

---

## 8. Start-up vs runtime options (override rules)

**Key Points**
- Two levels of configuration:
  1. **Start-up options**: set when the `ChatModel` is created (from `application.properties` or builder). These are **defaults**.
  2. **Runtime options**: inside the `Prompt` for **one request**.
- **Important rule for `ChatModel`**: runtime options in the `Prompt` **completely override** the start-up options (full replacement, **not merging**).
  - So the prompt must contain a **full set** of options.
  - Or pass **`null`** options in the `Prompt` → the model uses its **defaults**.
- **`ChatClient` is friendlier**: it supports a **"delta" customizer**. You change only what you need per request and the rest stays default.

| API | Per-request option behavior |
|---|---|
| `ChatModel.call(prompt)` | Prompt options **fully replace** model defaults |
| `ChatClient.prompt().options(...)` | You give a **delta**; defaults still apply for the rest |

**Use Cases**
- Default temperature 0.7 for the app, but one endpoint needs 0.0 for data extraction.

**Where to Use**
- Anywhere you override settings per request.

**Problem Solved**
- Avoids surprise: "I only changed temperature, why did my other settings disappear?" (when using `ChatModel` directly).

**Java Example**
```properties
# Start-up (default) options
spring.ai.openai.chat.options.model=gpt-4o
spring.ai.openai.chat.options.temperature=0.7
```
```java
// Runtime override with ChatModel: give the FULL set you want
Prompt prompt = new Prompt(
    "Extract the invoice number from: ...",
    OpenAiChatOptions.builder()
        .model("gpt-4o")          // repeat what you want to keep
        .temperature(0.0)         // override
        .maxTokens(200)
        .build()
);
chatModel.call(prompt);

// Use model defaults: null options
chatModel.call(new Prompt("Hello", (ChatOptions) null));

// ChatClient: delta override only
chatClient.prompt()
    .user("Extract the invoice number from: ...")
    .options(ChatOptions.builder().temperature(0.0))   // only temperature changes
    .call()
    .content();
```
> The exact way to pass options in `ChatClient` (builder vs built object) can change by version. Check Javadoc.

---

## 9. Request flow (convert input / convert output)

**Key Points**
- Steps in one chat call:
  1. **Start-up configuration** — `ChatModel` is created with default `ChatOptions`.
  2. **Runtime configuration** — the `Prompt` may carry runtime options that **fully override** the start-up options.
  3. **Input processing ("Convert Input")** — your messages/options are converted into the **provider's native format**.
  4. The provider/model runs.
  5. **Output processing ("Convert Output")** — the provider's response is converted into the standard **`ChatResponse`**.
- This conversion step is what makes **switching providers easy**: your code only sees `Prompt` and `ChatResponse`.

```
Prompt (standard) --convert input--> Provider request (native)
Provider response (native) --convert output--> ChatResponse (standard)
```

**Use Cases / Where to Use**
- Understanding how provider switching works; debugging "why did the provider receive this?".

**Problem Solved**
- Hides provider differences (JSON shapes, role names, parameter names).

---

## 10. ChatResponse

**Key Points**
- `ChatResponse` holds the **AI model's output**.
- It contains:
  - **`List<Generation>`** — one or more answers for a single prompt.
  - **`ChatResponseMetadata`** — info about the response (token usage, model name, etc.).
- Methods:
  - `getResults()` → list of all `Generation`s.
  - `getResult()` → the **first** generation (shortcut, widely used).
  - `getMetadata()` → response metadata.

```java
public class ChatResponse implements ModelResponse<Generation> {
    private final ChatResponseMetadata chatResponseMetadata;
    private final List<Generation> generations;

    @Override public ChatResponseMetadata getMetadata() {...}
    @Override public List<Generation> getResults() {...}
}
```

**Use Cases**
- Show the answer, track token cost, read finish reason, compare multiple candidates (if the provider supports it).

**Where to Use**
- After every `ChatModel.call(prompt)`.

**Problem Solved**
- One standard response type for all providers, including usage metadata.

**Java Example**
```java
ChatResponse response = chatModel.call(new Prompt("Tell me a joke"));

// Answer text
String text = response.getResult().getOutput().getText();

// Multiple generations (if available)
for (Generation g : response.getResults()) {
    System.out.println(g.getOutput().getText());
}

// Token usage (cost tracking)
Usage usage = response.getMetadata().getUsage();
System.out.println("Total tokens: " + usage.getTotalTokens());
```

---

## 11. Generation

**Key Points**
- `Generation` = **one output** from the model + its metadata.
- Extends `ModelResult<AssistantMessage>`.
- Contains:
  - **`AssistantMessage`** — the actual answer (via `getOutput()`). It may also contain tool call requests.
  - **`ChatGenerationMetadata`** — extra info about this generation (e.g., why it stopped; via `getMetadata()`).
- One `ChatResponse` can have **many** `Generation`s.

```java
public class Generation implements ModelResult<AssistantMessage> {
    private final AssistantMessage assistantMessage;
    private ChatGenerationMetadata chatGenerationMetadata;

    @Override public AssistantMessage getOutput() {...}
    @Override public ChatGenerationMetadata getMetadata() {...}
}
```

**Use Cases**
- Read answer text, check finish reason, read tool call requests.

**Where to Use**
- Inside `ChatResponse` handling code.

**Problem Solved**
- Keeps each answer and its own metadata together.

**Java Example**
```java
Generation generation = chatModel.call(new Prompt("Hi")).getResult();

AssistantMessage message = generation.getOutput();
String text = message.getText();

ChatGenerationMetadata meta = generation.getMetadata();
String finishReason = meta.getFinishReason();      // e.g., "STOP"
```

---

## 12. Available implementations

**Key Points**
- All providers use the **same** `ChatModel` / `StreamingChatModel` interfaces → easy to **switch** while keeping client code the same.
- Implementations and features listed in the document:

| Provider | Features |
|---|---|
| **OpenAI** Chat Completion | streaming, multi-modality, function (tool) calling |
| **Ollama** Chat Completion | streaming, multi-modality, function calling |
| **Amazon Bedrock** | (listed without features) |
| **Mistral AI** Chat Completion | streaming, function calling |
| **Anthropic** Chat Completion | streaming, function calling |

- Detailed comparison is in the "Chat Models Comparison" section of the official docs.
- The Chat Model API is built on the **Spring AI Generic Model API** (chat-specific abstractions on top).

**Use Cases**
- Cloud model for quality (OpenAI/Anthropic), **Ollama** for local/private models, **Bedrock** for AWS-centered companies.

**Where to Use**
- Pick the provider via a **starter dependency + properties**; code stays the same.

**Problem Solved**
- No lock-in; compare and replace providers with small config changes.

**Java Example**
```xml
<!-- Change only the starter + properties to switch provider -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```
```java
// Same code works for any provider
@RestController
class ChatController {
    private final ChatModel chatModel;     // OpenAI, Anthropic, Ollama... decided by config

    ChatController(ChatModel chatModel) { this.chatModel = chatModel; }

    @GetMapping("/chat")
    String chat(@RequestParam String q) {
        return chatModel.call(q);
    }
}
```
> Starter artifact names change between Spring AI versions. Check the official docs for your version.

---

## 13. ChatModel vs ChatClient

| Point | `ChatModel` | `ChatClient` |
|---|---|---|
| Level | **Low level** (like core JDBC) | **High level** (like `JdbcClient`) |
| API style | `call(Prompt)` | Fluent: `prompt().user().call().content()` |
| Options override | Prompt options **fully replace** defaults | **Delta** customizer; defaults remain |
| Extras | Basic | Advisors (memory, RAG), templates, `entity()` mapping, tools |
| Use when | Full control, building your own abstraction | Everyday app development |

**Java Example**
```java
// Low level
String a = chatModel.call(new Prompt("Hello")).getResult().getOutput().getText();

// High level
String b = ChatClient.create(chatModel).prompt().user("Hello").call().content();
```

---

## 14. Quick cheat sheet

```java
// Simple
String text = chatModel.call("Hello");

// Prompt + options
Prompt prompt = new Prompt(
    List.of(new SystemMessage("..."), new UserMessage("...")),
    ChatOptions.builder().temperature(0.2).maxTokens(300).build());
ChatResponse response = chatModel.call(prompt);

// Read answer
String answer = response.getResult().getOutput().getText();

// Metadata / tokens
long tokens = response.getMetadata().getUsage().getTotalTokens();

// Streaming
Flux<String> flux = chatModel.stream("Tell me a story");
Flux<ChatResponse> flux2 = chatModel.stream(prompt);
```

| Need | Use |
|---|---|
| Quick test | `chatModel.call(String)` |
| Full control | `chatModel.call(Prompt)` |
| Streaming | `chatModel.stream(...)` |
| Settings | `ChatOptions` |
| Answer text | `response.getResult().getOutput().getText()` |
| Token usage | `response.getMetadata().getUsage()` |
| Multiple answers | `response.getResults()` |
| Provider-specific setting | Provider's own options class (e.g., `OpenAiChatOptions`) |
| Use model defaults | Pass `null` options in `Prompt` |

---

## 15. Common mistakes

1. Assuming prompt options **merge** with model defaults → with `ChatModel` they **fully replace**. Provide the full set or use `null`.
2. Using only `call(String)` in real apps → you lose messages, options, and metadata. Use `call(Prompt)`.
3. Confusing `MessageType` with a **format** → it means the **role**.
4. Assuming every model supports roles like `system` → for models without roles, `UserMessage` is used as the standard.
5. Calling `chatModel.stream()` without the **reactive stack** or without subscribing → nothing happens (Flux is lazy).
6. Ignoring `ChatResponse` metadata → you miss token usage and cost info.
7. Using provider-specific options in shared code → hurts portability. Prefer portable `ChatOptions` where possible.
8. Reading only `getResult()` when you asked for multiple generations → use `getResults()`.
9. Expecting `getText()` or `getContent()` to exist in all versions → check your Spring AI version.
10. Setting very high `temperature` for extraction/code tasks → unstable output.
11. Not setting `maxTokens` → unexpectedly long (and costly) answers.
12. Forgetting that switching providers also needs **new API keys and model names** in config.

---

## 16. Interview quick Q&A

**Q1. What is the Chat Model API?**
A simple, portable Spring AI interface to talk to chat/completion AI models and switch providers with minimal code changes.

**Q2. What is `ChatModel`?**
The main interface for synchronous chat calls. It has `call(String)` (simple) and `call(Prompt)` returning `ChatResponse`.

**Q3. What is `StreamingChatModel`?**
Interface for streaming responses with `Flux`: `stream(String)` returns `Flux<String>`, `stream(Prompt)` returns `Flux<ChatResponse>`.

**Q4. Does `ChatModel` also support streaming?**
Yes. `ChatModel` extends `StreamingChatModel`.

**Q5. What does `Prompt` contain?**
A list of `Message` objects and optional `ChatOptions`.

**Q6. What is `MessageType`?**
The role of a message (system, user, assistant, tool), not a text format.

**Q7. Which message type is used if the model has no special roles?**
`UserMessage`.

**Q8. What is `MediaContent`?**
An interface for multimodal messages; gives a collection of `Media` (images, audio, etc.).

**Q9. Name some portable `ChatOptions`.**
model, temperature, maxTokens, topP, topK, frequencyPenalty, presencePenalty, stopSequences.

**Q10. Provider-specific options example?**
OpenAI: `logitBias`, `seed`, `user`.

**Q11. How do start-up and runtime options work in `ChatModel`?**
Start-up options are defaults. Runtime options in the `Prompt` completely override them. Use `null` options to use defaults.

**Q12. How does `ChatClient` handle per-request options?**
With a delta customizer, so you override only what you need.

**Q13. What happens in "Convert Input" and "Convert Output"?**
Input: Spring AI objects → provider-native request. Output: provider response → standard `ChatResponse`.

**Q14. What does `ChatResponse` contain?**
A list of `Generation`s and `ChatResponseMetadata`.

**Q15. What is a `Generation`?**
One output of the model: an `AssistantMessage` plus `ChatGenerationMetadata`.

**Q16. Why can one `ChatResponse` have many `Generation`s?**
Because a single prompt can produce multiple candidate outputs.

**Q17. Which providers are listed as implementations?**
OpenAI, Ollama, Amazon Bedrock, Mistral AI, Anthropic.

**Q18. How does this API avoid vendor lock-in?**
Same interfaces and standard classes (`Prompt`, `ChatResponse`) for all providers; provider differences are handled inside implementations.

**Q19. `ChatModel` vs `ChatClient`?**
`ChatModel` is low-level (like core JDBC). `ChatClient` is high-level (like `JdbcClient`) with fluent API, advisors, and entity mapping.

**Q20. Where do you find token usage?**
`chatResponse.getMetadata().getUsage()`.
