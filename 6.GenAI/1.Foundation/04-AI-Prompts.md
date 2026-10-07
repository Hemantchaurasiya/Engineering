# Spring AI Prompts — Effective Notes

> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

1. [What is a Prompt?](#1-what-is-a-prompt)
2. [Where Prompts sit in Spring AI (JDBC analogy)](#2-where-prompts-sit-in-spring-ai-jdbc-analogy)
3. [Prompt class](#3-prompt-class)
4. [Prompt convenience methods](#4-prompt-convenience-methods)
5. [Message interface](#5-message-interface)
6. [Roles and MessageType](#6-roles-and-messagetype)
7. [PromptTemplate](#7-prompttemplate)
8. [TemplateRenderer](#8-templaterenderer)
9. [PromptTemplate interfaces (String / Message / Prompt actions)](#9-prompttemplate-interfaces)
10. [Example: simple PromptTemplate](#10-example-simple-prompttemplate)
11. [Example: roles with SystemPromptTemplate](#11-example-roles-with-systempprompttemplate)
12. [Custom template renderer (JSON-safe delimiters)](#12-custom-template-renderer)
13. [Prompts from Resource files](#13-prompts-from-resource-files)
14. [Prompt engineering](#14-prompt-engineering)
15. [Components of an effective prompt](#15-components-of-an-effective-prompt)
16. [Simple prompt techniques](#16-simple-prompt-techniques)
17. [Advanced prompt techniques](#17-advanced-prompt-techniques)
18. [Tokens](#18-tokens)
19. [Quick cheat sheet](#19-quick-cheat-sheet)
20. [Common mistakes](#20-common-mistakes)
21. [Interview quick Q&A](#21-interview-quick-qa)

---

## 1. What is a Prompt?

**Key Points**
- A **prompt** is the input that guides an AI model to produce a specific output.
- How you **design and word** the prompt strongly changes the answer quality.
- Prompts have grown from **simple strings** → strings with **placeholders** (like `USER:`) → **structured messages with roles** (system, user, assistant, tool).

**Use Cases**
- Every AI feature: chat, summary, code generation, classification, extraction.

**Where to Use**
- Anywhere your app talks to an AI model.

**Problem Solved**
- Gives a clear way to tell the model **what to do, how to behave, and what data to use**.

**Java Example**
```java
// Simplest form: a plain string prompt
String answer = chatModel.call("Explain Spring Boot in 2 lines");
```

---

## 2. Where Prompts sit in Spring AI (JDBC analogy)

**Key Points**
- Handling prompts is like managing the **View in Spring MVC**: a text with **placeholders** filled at runtime.
- Another analogy: a **SQL statement with placeholders** (`?`).
- Layers (think JDBC):

| Spring AI | JDBC analogy | Role |
|---|---|---|
| `ChatModel` | Core JDBC library | Low-level call to the AI model |
| `ChatClient` | `JdbcClient` | High-level, fluent API built on `ChatModel` |
| Advisors | Extra features on top | History, extra documents (RAG), agentic behavior |

**Use Cases**
- Helps you decide: use `ChatModel` for low-level control, `ChatClient` for everyday work.

**Where to Use**
- Choosing the right abstraction level for your feature.

**Problem Solved**
- Easy mental model for the Spring AI structure.

**Java Example**
```java
// Low level (like plain JDBC)
ChatResponse r1 = chatModel.call(new Prompt("Hello"));

// High level (like JdbcClient)
String r2 = ChatClient.create(chatModel).prompt("Hello").call().content();
```

---

## 3. Prompt class

**Key Points**
- `Prompt` = **container** of:
  - an **ordered list of `Message` objects**, and
  - request **`ChatOptions`** (model name, temperature, etc.).
- `ChatModel.call(Prompt)` takes a `Prompt` and returns a `ChatResponse`.
- Each message has its own **role**, so one prompt can contain system rules, user questions, past AI answers, and tool results.

```java
public class Prompt implements ModelRequest<List<Message>> {
    private final List<Message> messages;
    private ChatOptions chatOptions;
}
```

**Use Cases**
- Multi-turn conversations, prompts with rules + question, prompts with tool results.

**Where to Use**
- When using `ChatModel` directly, or `ChatClient.prompt(Prompt)`.

**Problem Solved**
- Organizes complex conversations into clear, role-based parts instead of one big string.

**Java Example**
```java
Prompt prompt = new Prompt(
    List.of(
        new SystemMessage("You are a Java mentor. Keep answers short."),
        new UserMessage("What is a record in Java?")
    ),
    ChatOptions.builder().temperature(0.3).build()
);

ChatResponse response = chatModel.call(prompt);
String text = response.getResult().getOutput().getText();
```

---

## 4. Prompt convenience methods

**Key Points**
- Helpers to get messages **by role**:

| Method | Returns |
|---|---|
| `getUserMessage()` | **Last** user message (or empty `UserMessage` if none) |
| `getSystemMessage()` | **First** system message (or empty `SystemMessage` if none) |
| `getLastUserOrToolResponseMessage()` | Last user **or** tool-response message (useful for conversation continuity) |
| `getUserMessages()` | **All** user messages, in order |
| `getSystemMessages()` | **All** system messages, in order |

- Very useful in multi-turn chats and **custom advisors**.

**Use Cases**
- A logging/RAG advisor needs the latest user question.
- Read the system rules for debugging.

**Where to Use**
- Custom advisors, prompt inspection, testing.

**Problem Solved**
- No manual looping and filtering of the messages list.

**Java Example**
```java
String lastQuestion = prompt.getUserMessage().getText();
String rules = prompt.getSystemMessage().getText();
List<Message> allUserMsgs = prompt.getUserMessages();
```

---

## 5. Message interface

**Key Points**
- `Message` holds: **text content**, **metadata** (key-value map), and a **`MessageType`** (role).
- `Content` interface: `getContent()` and `getMetadata()`.
- `Message extends Content` and adds `getMessageType()`.
- **Multimodal** messages also implement `MediaContent` → `getMedia()` returns images/audio/etc. (`Media` objects).
- Implementations: `UserMessage`, `SystemMessage`, `AssistantMessage`, `ToolResponseMessage`.

```java
public interface Content {
    String getContent();
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
- Text chat, image + text questions, tagging messages with extra info (userId, traceId).

**Where to Use**
- When you create prompts by hand or write advisors.

**Problem Solved**
- One standard shape for every kind of message across all AI providers.

**Java Example**
```java
Message userMessage = new UserMessage("Summarize this text: ...");
MessageType type = userMessage.getMessageType();          // USER
Map<String, Object> meta = userMessage.getMetadata();     // extra info
```

---

## 6. Roles and MessageType

**Key Points**
- Four roles, shown by the enum `MessageType`: `USER`, `ASSISTANT`, `SYSTEM`, `TOOL`.

| Role | Meaning | Example |
|---|---|---|
| **System** | Rules for AI behavior and style. Like instructions before the chat starts. | "You are a bank assistant. Be formal." |
| **User** | The user's question/command. | "How do I reset my PIN?" |
| **Assistant** | AI's earlier replies. Keeps conversation flow. Can also carry a **tool call request** (calculate, fetch data...). | "Sure, here are the steps..." |
| **Tool / Function** | Returns the **result of a tool call** back to the model. | `{"orderStatus":"SHIPPED"}` |

```java
public enum MessageType {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
    TOOL("tool");
}
```

**Use Cases**
- System: set tone, safety rules, output format.
- Assistant: replay history so the model remembers.
- Tool: give live data to the model after it asks for it.

**Where to Use**
- Any multi-turn chat or tool-calling flow.

**Problem Solved**
- The model clearly knows **who said what** and what each part is for → better, more consistent answers.

**Java Example**
```java
List<Message> messages = List.of(
    new SystemMessage("You are a polite Java tutor."),
    new UserMessage("What is a record?"),
    new AssistantMessage("A record is a compact class for immutable data."),
    new UserMessage("Give me a small example.")       // follow-up uses history
);

Prompt prompt = new Prompt(messages);
String reply = chatModel.call(prompt).getResult().getOutput().getText();
```

---

## 7. PromptTemplate

**Key Points**
- `PromptTemplate` builds **structured prompts with placeholders** like `{topic}`.
- Uses a **`TemplateRenderer`** to replace placeholders.
- Default renderer: **`StTemplateRenderer`** (StringTemplate engine). Placeholder syntax: `{name}`.
- You can change the delimiters (e.g., `<name>`).
- `PromptTemplate` implements `PromptTemplateActions` and `PromptTemplateMessageActions`.

**Use Cases**
- Reusable prompts: "Translate {text} to {language}".
- Same prompt, different user data.

**Where to Use**
- Any prompt with dynamic data.

**Problem Solved**
- No messy string concatenation; clean, safe, reusable prompts.

**Java Example**
```java
PromptTemplate template = new PromptTemplate("Translate '{text}' to {language}");
String finalText = template.render(Map.of("text", "Good morning", "language", "Hindi"));
```

---

## 8. TemplateRenderer

**Key Points**
- `TemplateRenderer` does the **actual substitution** of variables into the template string.
- It is a `BiFunction<String, Map<String, Object>, String>`.
- Implementations:
  - **`StTemplateRenderer`** — default (StringTemplate).
  - **`NoOpTemplateRenderer`** — does nothing; use when the string is **already complete** (no placeholders).
  - **Your own** — implement the interface for custom logic.

```java
public interface TemplateRenderer extends BiFunction<String, Map<String, Object>, String> {
    @Override
    String apply(String template, Map<String, Object> variables);
}
```

**Use Cases**
- Prompt contains `{ }` JSON → use `NoOp` or change delimiters.
- Need a different template engine (Mustache, Freemarker) → write a custom renderer.

**Where to Use**
- Advanced prompt customization.

**Problem Solved**
- Template syntax conflicts and engine lock-in.

**Java Example**
```java
// Custom renderer example (very simple {{name}} replacement)
TemplateRenderer simpleRenderer = (template, vars) -> {
    String result = template;
    for (var e : vars.entrySet()) {
        result = result.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue()));
    }
    return result;
};

PromptTemplate pt = PromptTemplate.builder()
    .renderer(simpleRenderer)
    .template("Hello {{name}}, explain {{topic}}")
    .build();

String text = pt.render(Map.of("name", "Hemant", "topic", "Spring AI"));
```

---

## 9. PromptTemplate interfaces

**Key Points**
- Three interfaces show three ways to create prompts (you may not use all, but good to know):

### 9.1 `PromptTemplateStringActions` → produce a **String**
```java
public interface PromptTemplateStringActions {
    String render();                              // no placeholders / no data
    String render(Map<String, Object> model);     // fill placeholders
}
```

### 9.2 `PromptTemplateMessageActions` → produce a **Message**
```java
public interface PromptTemplateMessageActions {
    Message createMessage();                              // static message
    Message createMessage(List<Media> mediaList);         // text + media
    Message createMessage(Map<String, Object> model);     // dynamic content
}
```

### 9.3 `PromptTemplateActions` → produce a **Prompt** (extends String actions)
```java
public interface PromptTemplateActions extends PromptTemplateStringActions {
    Prompt create();
    Prompt create(ChatOptions modelOptions);
    Prompt create(Map<String, Object> model);
    Prompt create(Map<String, Object> model, ChatOptions modelOptions);
}
```

| Interface | Output | Pick when |
|---|---|---|
| StringActions | `String` | Need only final text |
| MessageActions | `Message` | Building a multi-message prompt (e.g., system + user) |
| Actions | `Prompt` | Need a ready prompt (with options) to pass to `ChatModel` |

**Use Cases**
- String: log or test the final text.
- Message: mix system and user messages manually.
- Prompt: pass directly to `chatModel.call(...)`.

**Where to Use**
- When using `ChatModel` directly (low level).

**Problem Solved**
- Flexible output type from the same template.

**Java Example**
```java
PromptTemplate pt = new PromptTemplate("Explain {topic} in {lines} lines");
Map<String, Object> vars = Map.of("topic", "Spring Boot", "lines", 3);

String text = pt.render(vars);                                        // String
Message msg = pt.createMessage(vars);                                 // Message
Prompt prompt = pt.create(vars, ChatOptions.builder().temperature(0.2).build()); // Prompt
```

---

## 10. Example: simple PromptTemplate

**Key Points**
- Create template → `create(Map)` → pass `Prompt` to `chatModel.call(...)`.

**Use Cases**
- Joke generator, translation, quick Q&A with variables.

**Where to Use**
- Simple one-shot generation.

**Problem Solved**
- Clean variable substitution without string concatenation.

**Java Example**
```java
PromptTemplate promptTemplate = new PromptTemplate("Tell me a {adjective} joke about {topic}");

Prompt prompt = promptTemplate.create(Map.of("adjective", adjective, "topic", topic));

return chatModel.call(prompt).getResult();
```

---

## 11. Example: roles with SystemPromptTemplate

**Key Points**
- `SystemPromptTemplate` creates a **system role message** with placeholders.
- Combine with `UserMessage` into a `Prompt`.
- Flow: build user message → build system message from template → make `Prompt` → `chatModel.call`.

**Use Cases**
- Personality/voice control: "Your name is {name}, reply in the style of {voice}".
- Multi-tenant bots (different company name per tenant).

**Where to Use**
- When system rules change **per request or per user**.

**Problem Solved**
- Dynamic system instructions without hardcoding.

**Java Example**
```java
String userText = """
    Tell me about three famous pirates from the Golden Age of Piracy and why they did.
    Write at least a sentence for each pirate.
    """;

Message userMessage = new UserMessage(userText);

String systemText = """
  You are a helpful AI assistant that helps people find information.
  Your name is {name}
  You should reply to the user's request with your name and also in the style of a {voice}.
  """;

SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemText);
Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", name, "voice", voice));

Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

List<Generation> response = chatModel.call(prompt).getResults();
```
> Tip: Many providers prefer the **system message first** in the list. Consider `List.of(systemMessage, userMessage)`.

---

## 12. Custom template renderer

**Key Points**
- Default placeholder syntax is `{}`.
- If your prompt contains **JSON** (which also uses `{}`), you get a clash.
- Fix: change delimiters, e.g. `<` and `>`.
- Pass the renderer using `PromptTemplate.builder().renderer(...)`.
- You can also write your own `TemplateRenderer` implementation.

**Use Cases**
- Prompt contains a JSON example/schema.
- Prompt contains code with braces (Java, JS).

**Where to Use**
- Structured-output prompts, code-generation prompts.

**Problem Solved**
- Prevents template errors like "variable not found" for JSON braces.

**Java Example**
```java
PromptTemplate promptTemplate = PromptTemplate.builder()
    .renderer(StTemplateRenderer.builder()
        .startDelimiterToken('<')
        .endDelimiterToken('>')
        .build())
    .template("""
            Tell me the names of 5 movies whose soundtrack was composed by <composer>.
            """)
    .build();

String prompt = promptTemplate.render(Map.of("composer", "John Williams"));

// JSON-safe example
PromptTemplate jsonSafe = PromptTemplate.builder()
    .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
    .template("""
            Extract data for <name>. Reply in this JSON:
            {"name": "string", "age": 0}
            """)
    .build();
```

---

## 13. Prompts from Resource files

**Key Points**
- Spring AI supports Spring's `Resource` abstraction.
- Put long prompts in files (like `classpath:/prompts/system-message.st`) and load them.
- Pass the `Resource` directly to `SystemPromptTemplate` (or `PromptTemplate`).
- `.st` = StringTemplate file (any text file works).

**Use Cases**
- Long system prompts, prompts maintained by non-developers/prompt engineers.
- Version-controlling prompts, different prompts per environment.

**Where to Use**
- Production apps with many or long prompts.

**Problem Solved**
- Keeps Java code clean; prompts can be edited **without code changes**; easier reviews in Git.

**Java Example**
```java
@Component
public class SupportService {

    @Value("classpath:/prompts/system-message.st")
    private Resource systemResource;

    private final ChatModel chatModel;

    public SupportService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String ask(String userText) {
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("company", "Acme Bank"));

        Prompt prompt = new Prompt(List.of(systemMessage, new UserMessage(userText)));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
```
`src/main/resources/prompts/system-message.st`
```
You are a support assistant for {company}.
Be polite and short.
```

---

## 14. Prompt engineering

**Key Points**
- **Prompt engineering** = designing prompts to get better AI output.
- Quality and structure of the prompt strongly affect results.
- Even small wording changes matter. Example from research: starting with **"Take a deep breath and work on this problem step by step"** improved problem solving.
- The AI community **shares and compares prompts**; learn from them.
- It is a **continuous** challenge because AI models change fast.

**Use Cases**
- Improving accuracy of summaries, classification, extraction, code generation.
- Reducing wrong/made-up answers.

**Where to Use**
- Before adding complex code or bigger models: **try a better prompt first**.

**Problem Solved**
- Better output **without** changing the model or paying for a bigger one.

**Java Example**
```java
String weak = "Solve: 17 * 24";
String better = "Take a deep breath and work on this problem step by step. Solve: 17 * 24";

String answer = chatClient.prompt(better).call().content();
```

---

## 15. Components of an effective prompt

**Key Points**
| Component | Meaning | Example |
|---|---|---|
| **Instructions** | Clear, direct commands, like speaking to a person | "Summarize in 3 bullet points." |
| **External Context** | Background info or guidance for the answer | Company policy text, product details |
| **User Input** | The user's actual question | "Can I return my order?" |
| **Output Indicator** | Desired format (e.g., JSON) | "Reply only in JSON." |

- **Output Indicator warning**: The AI may not follow strictly. It might add "Here is your JSON:" before the JSON, or make JSON-like but **invalid** output.
  - Fix: use `entity()` / `BeanOutputConverter` / structured output + validation.
- **Give examples** of expected question and answer format (few-shot). This makes answers more accurate.

**Use Cases**
- RAG prompts (context + question), extraction prompts (instructions + output format).

**Where to Use**
- Every production prompt should have these 4 parts.

**Problem Solved**
- Vague prompts → vague answers. Structure removes guesswork.

**Java Example**
```java
String template = """
    Instructions: Answer using only the context below. If not found, say "I don't know".
    Context: {context}
    Question: {question}
    Output: Reply in one short paragraph.
    """;

String answer = chatClient.prompt()
    .user(u -> u.text(template)
        .param("context", "Refunds are allowed within 7 days of delivery.")
        .param("question", "Can I return an item after 10 days?"))
    .call()
    .content();
```

---

## 16. Simple prompt techniques

| Technique | What it does | Use case | Example prompt |
|---|---|---|---|
| **Text Summarization** | Shrinks long text, keeps main ideas | Long emails, reports, tickets | "Summarize in 3 bullets: {text}" |
| **Question Answering** | Finds specific answers inside given text | Policy bots, doc Q&A | "From this text, answer: {question}" |
| **Text Classification** | Puts text into predefined categories | Ticket routing, sentiment, spam | "Classify as BILLING, TECH or OTHER: {text}" |
| **Conversation** | Back-and-forth chat | Support bots, assistants | System + user + assistant history |
| **Code Generation** | Creates code from natural language | Boilerplate, SQL, unit tests | "Write a Java method that ..." |

**Problem Solved**: These cover most day-to-day AI features with small prompt changes.

**Java Example**
```java
// Classification
String category = chatClient.prompt()
    .system("Classify the ticket as BILLING, TECH or OTHER. Reply with one word only.")
    .user("My card was charged twice for the same order")
    .call()
    .content();

// Summarization
String summary = chatClient.prompt()
    .user(u -> u.text("Summarize in 3 bullet points: {text}").param("text", longText))
    .call()
    .content();
```

---

## 17. Advanced prompt techniques

| Technique | Simple meaning | Use case |
|---|---|---|
| **Zero-shot** | Give **no examples**; model uses its general knowledge | Easy, common tasks |
| **Few-shot** | Give **a few examples** in the prompt to show the pattern | Custom formats, special labels |
| **Chain-of-Thought (CoT)** | Ask the model to **think step by step** before the final answer | Math, logic, multi-step reasoning |
| **ReAct (Reason + Act)** | Model **reasons**, then **chooses an action** (like calling a tool), then reasons again | Agents, tool calling |
| **Microsoft Guidance** | Structured framework to build and optimize prompts | Teams that need a systematic approach |

> Note: The source document describes Chain-of-Thought as "linking multiple responses to keep context". In most references, CoT means asking the model to **reason step by step**. Both ideas help the AI keep a clear line of thought.

**Problem Solved**
- Better accuracy on harder tasks without fine-tuning the model.

**Java Example**
```java
// Few-shot classification
String fewShot = """
    Classify sentiment as POSITIVE or NEGATIVE.

    Text: "Great service, very fast!"  -> POSITIVE
    Text: "Worst app ever, keeps crashing" -> NEGATIVE
    Text: "{input}" ->
    """;

String label = chatClient.prompt()
    .user(u -> u.text(fewShot).param("input", "Delivery was late but support helped me"))
    .call()
    .content();

// Chain-of-thought style
String cot = chatClient.prompt()
    .user("A train goes 60 km in 45 minutes. What is its speed in km/h? Think step by step, then give the final answer.")
    .call()
    .content();
```
> ReAct in Spring AI is usually done with **tool calling** (`.tools(...)`): model reasons → asks for a tool → gets the result → continues.

---

## 18. Tokens

**Key Points**
- **Token** = a piece of a word. Models convert words → tokens (input), and tokens → words (output).
- Rule of thumb: **1 token ≈ 3/4 of a word**. Example: Shakespeare's complete works (~900,000 words) ≈ **1.2 million tokens**.
- **Tokenization** = breaking text into tokens.
- Try the **OpenAI Tokenizer UI** to see it.

**Why tokens matter**
| Topic | Meaning |
|---|---|
| **Billing** | Providers charge by tokens. **Input (prompt) + output (response)** both count. Shorter prompts = cheaper. |
| **Model limits** | Each model has a max token limit (**context window**). Examples from the doc: GPT-3 = 4K; Claude 2 and Llama 2 = 100K; some research models up to 1M. |
| **Context window** | Input beyond the limit is **not processed**. Send only the **minimal useful** information. (Asking about "Hamlet"? Don't send all of Shakespeare.) |
| **Response metadata** | Response includes **token usage** → track cost and usage. |

> The numbers above are from the document and may be old; newer models have much larger limits.

**Use Cases**
- Cost control, deciding how much chat history/RAG context to send, per-user usage tracking.

**Where to Use**
- Chat memory limits, RAG chunk size, billing dashboards.

**Problem Solved**
- Avoids **high bills** and **"context too long"** errors.

**Java Example**
```java
ChatResponse response = chatClient.prompt()
    .user("Explain Spring Boot starters")
    .call()
    .chatResponse();

Usage usage = response.getMetadata().getUsage();
System.out.println("Prompt tokens    : " + usage.getPromptTokens());
System.out.println("Completion tokens: " + usage.getCompletionTokens());
System.out.println("Total tokens     : " + usage.getTotalTokens());

// Rough estimate: words / 0.75
int estimatedTokens = (int) (userText.split("\\s+").length / 0.75);
```

---

## 19. Quick cheat sheet

```java
// Plain string
chatModel.call("Hello");

// Prompt with messages + options
new Prompt(List.of(new SystemMessage("..."), new UserMessage("...")),
           ChatOptions.builder().temperature(0.2).build());

// Template -> String / Message / Prompt
PromptTemplate pt = new PromptTemplate("Tell me a {adj} joke about {topic}");
pt.render(vars);          // String
pt.createMessage(vars);   // Message
pt.create(vars);          // Prompt

// System template
new SystemPromptTemplate("You are {name}").createMessage(Map.of("name", "Max"));

// Custom delimiters
PromptTemplate.builder()
    .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
    .template("... <var> ...").build();

// Prompt from file
@Value("classpath:/prompts/system-message.st") Resource res;
new SystemPromptTemplate(res);
```

| Need | Use |
|---|---|
| Container for messages + options | `Prompt` |
| Last user question | `prompt.getUserMessage()` |
| Placeholders in text | `PromptTemplate` |
| Placeholders in system rules | `SystemPromptTemplate` |
| JSON in prompt | Custom delimiters or `NoOpTemplateRenderer` |
| Long prompts | `Resource` file (`.st`) |
| Show AI the format | Few-shot examples |
| Hard reasoning | Chain-of-thought |
| Cost tracking | Response metadata (token usage) |

---

## 20. Common mistakes

1. **JSON in template with `{}`** → clash with placeholders. Change delimiters or use `NoOpTemplateRenderer`.
2. Missing a variable in the map → render error. Make sure every placeholder has a value.
3. **Hardcoding long prompts** inside Java code → move to `Resource` files.
4. **Trusting output format** ("reply in JSON") blindly → AI may add extra text or invalid JSON. Use `entity()`/structured output + validation.
5. Sending **too much context** → high cost and context-limit errors.
6. Forgetting that **both input and output tokens** are billed.
7. **No system message** for rules → inconsistent tone/behavior.
8. Putting **user input directly into the system prompt** without care → prompt injection risk. Keep user text in user messages.
9. Not giving **examples** when the format is custom → poor results (use few-shot).
10. Confusing `getUserMessage()` (returns the **last** user message) with `getSystemMessage()` (returns the **first** system message).
11. Not tracking token usage in production → surprise bills.
12. Never testing prompt changes → small word changes can change quality a lot; compare versions.

---

## 21. Interview quick Q&A

**Q1. What is a Prompt in Spring AI?**
A container of an ordered list of `Message` objects plus `ChatOptions`, sent to the model through `ChatModel`/`ChatClient`.

**Q2. What roles exist in Spring AI messages?**
`SYSTEM`, `USER`, `ASSISTANT`, `TOOL` (enum `MessageType`).

**Q3. Role of the system message?**
It guides the AI's behavior and response style, like instructions given before the chat starts.

**Q4. What is the assistant role used for?**
Holds the AI's earlier replies (keeps conversation flow). It can also carry **tool call requests**.

**Q5. What is the tool role?**
Returns the result of a tool call back to the model.

**Q6. What is `PromptTemplate`?**
A class that builds prompts from a template with placeholders, using a `TemplateRenderer`.

**Q7. Default template engine and syntax?**
`StTemplateRenderer` (StringTemplate), with `{variable}` syntax.

**Q8. How to avoid clash with JSON braces?**
Use custom delimiters (like `<` `>`), or `NoOpTemplateRenderer` if there is nothing to replace.

**Q9. What is `NoOpTemplateRenderer`?**
A renderer that does nothing; for already complete strings.

**Q10. `render()` vs `createMessage()` vs `create()`?**
`render()` → String, `createMessage()` → Message, `create()` → Prompt.

**Q11. How to load a prompt from a file?**
Inject a Spring `Resource` (e.g., `@Value("classpath:/prompts/system-message.st")`) and pass it to `SystemPromptTemplate`/`PromptTemplate`.

**Q12. `ChatModel` vs `ChatClient` (JDBC analogy)?**
`ChatModel` = core JDBC (low level). `ChatClient` = `JdbcClient` (higher level, with advisors for memory, RAG, agentic behavior).

**Q13. What are the 4 parts of an effective prompt?**
Instructions, external context, user input, output indicator.

**Q14. Why is the output indicator tricky?**
The model may not follow the format exactly (extra text, invalid JSON).

**Q15. Zero-shot vs few-shot?**
Zero-shot = no examples. Few-shot = a few examples in the prompt to show the pattern.

**Q16. What is ReAct?**
Reason + Act: the model reasons about the input, then chooses an action (like a tool call), then continues.

**Q17. What is a token and why does it matter?**
A piece of a word (≈ 3/4 of a word). It affects billing (input + output), model limits, and the context window.

**Q18. What is a context window?**
The maximum tokens a model can process at once. Input above it is not processed, so send only the minimal useful info.

**Q19. What does `getUserMessage()` return?**
The **last** user message, or an empty `UserMessage` if none exists.

**Q20. Where do you find token usage of a response?**
In `ChatResponse` metadata (`getMetadata().getUsage()`).
