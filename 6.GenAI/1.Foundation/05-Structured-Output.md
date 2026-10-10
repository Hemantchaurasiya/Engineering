# Spring AI Structured Output — Effective Notes

> Covers: **Structured Output**, **Schema Validation & Self-Correction**, **Provider-Native Structured Output**, and **Output Converters**.
> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

**Part A — Structured Output basics**
1. [What is structured output?](#1-what-is-structured-output)
2. [Typed response with `.entity()`](#2-typed-response-with-entity)
3. [Generic types (List, Map)](#3-generic-types-list-map)
4. [Getting the full response with `.responseEntity()`](#4-getting-the-full-response-with-responseentity)
5. [Reliability switches overview](#5-reliability-switches-overview)

**Part B — Schema validation**
1. [`validateSchema()` and the self-correcting loop](#6-validateschema-and-the-self-correcting-loop)
2. [Customizing `StructuredOutputValidationAdvisor`](#7-customizing-structuredoutputvalidationadvisor)

**Part C — Provider-native structured output**
1. [`useProviderStructuredOutput()`](#8-useproviderstructuredoutput)
2. [How support is detected, supported models, why off by default](#9-how-support-is-detected-supported-models-why-off-by-default)
3. [Known limitations (schema, Ollama, OpenAI arrays)](#10-known-limitations)
4. [Enabling globally](#11-enabling-globally)
5. [Provider built-in JSON mode](#12-provider-built-in-json-mode)
6. [Combining both switches](#13-combining-both-switches)

**Part D — Output converters**
1. [`StructuredOutputConverter` API](#14-structuredoutputconverter-api)
2. [Role of `getJsonSchema()`](#15-role-of-getjsonschema)
3. [Available converters](#16-available-converters)
4. [BeanOutputConverter](#17-beanoutputconverter)
5. [MapOutputConverter](#18-mapoutputconverter)
6. [ListOutputConverter](#19-listoutputconverter)
7. [Custom converters (lenient JSON)](#20-custom-converters-lenient-json)
8. [Non-JSON formats (YAML, CSV)](#21-non-json-formats-yaml-csv)

**Part E — Revision**
1. [Cheat sheet](#22-cheat-sheet)
2. [Common mistakes](#23-common-mistakes)
3. [Interview quick Q&A](#24-interview-quick-qa)

---

# Part A — Structured Output basics

## 1. What is structured output?

**Key Points**
- LLMs are **text-in, text-out**. Your code needs **typed objects** (records/classes) to route, save, or branch on results.
- **Structured output** = steer the model to produce text that follows a **schema**, then **parse it into a Java type**.
- In Spring AI you do this with `.entity(...)` on `ChatClient`.
- Works on **every model** Spring AI supports (nothing provider-specific in the default way).

**Use Cases**
- Extract invoice fields, parse resumes, classify support tickets, create a task list, sentiment + score.

**Where to Use**
- Whenever **code (not a human)** will read the AI answer.

**Problem Solved**
- No manual JSON parsing. The AI text becomes a normal domain object.

**Java Example**
```java
record TicketInfo(String category, String priority, String summary) {}

TicketInfo info = chatClient.prompt()
    .user("Classify this ticket: 'App crashes when I upload a PDF'")
    .call()
    .entity(TicketInfo.class);

if ("HIGH".equals(info.priority())) {
    // route to on-call team
}
```

---

## 2. Typed response with `.entity()`

**Key Points**
- Instead of `.content()` (raw String), end the call with `.entity(YourType.class)`.
- Behind the scenes Spring AI does **3 things**:
  1. **Schema generator** turns your record into a **JSON schema**.
  2. The schema is **added to the prompt's system context** (as instructions).
  3. The model's JSON answer goes to a **type converter** that parses it into your record.
- **`.entity()` works only with `.call()`**. It is **not available with `.stream()`** (needs the complete response).
- **No guarantee by default.** The model is *asked* to follow the schema, not *forced*. It may:
  - add an extra field,
  - skip a required field,
  - wrap JSON in text → the parser **throws**.

**Flow**
```
Your record → JSON schema → added to prompt → LLM → JSON text → converter → your record
```

**Use Cases / Where to Use**
- Any normal extraction/classification task.

**Problem Solved**
- Typed results with one line of code.

**Java Example**
```java
record ActorsFilms(String actor, List<String> movies) {}

ActorsFilms films = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorsFilms.class);

films.actor();     // "Tom Hanks"
films.movies();    // ["Forrest Gump", "Cast Away", ...]
```

---

## 3. Generic types (List, Map)

**Key Points**
- `.entity(Class)` is only for **concrete classes**.
- For `List<T>`, `Map<K,V>` etc. use **`ParameterizedTypeReference`** (note the `{}` — it creates an anonymous subclass so Java keeps the generic type).

**Use Cases**
- Extract many items: list of products, list of action items.

**Where to Use**
- When the answer is a collection.

**Problem Solved**
- Java erases generics at runtime; `ParameterizedTypeReference` keeps the type info.

**Java Example**
```java
List<ActorsFilms> films = chatClient.prompt()
    .user("Generate filmographies for three random actors.")
    .call()
    .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {});
```
> Warning: with **OpenAI native structured output**, a top-level `List<T>` fails. See Section 10.

---

## 4. Getting the full response with `.responseEntity()`

**Key Points**
- `.entity()` returns **only the parsed object**.
- Need metadata too (token usage, observability)? Use **`.responseEntity()`**.
- Returns `ResponseEntity<ChatResponse, T>` → `.entity()` gives your object, `.response()` gives `ChatResponse`.
- Same overloads as `.entity()`: `Class`, `ParameterizedTypeReference`, custom converter, and the `EntityParamSpec` consumer.

**Use Cases**
- Save the object **and** log token cost in one call.

**Where to Use**
- Production code with cost tracking or audit.

**Problem Solved**
- Avoids calling the model twice (double cost) to get entity + metadata.

**Java Example**
```java
ResponseEntity<ChatResponse, ActorsFilms> result = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .responseEntity(ActorsFilms.class);

ActorsFilms films = result.entity();
ChatResponse raw = result.response();
long totalTokens = raw.getMetadata().getUsage().getTotalTokens();
```

---

## 5. Reliability switches overview

**Key Points**
- Every `.entity()` / `.responseEntity()` accepts an optional `Consumer<EntityParamSpec>` with **two independent, combinable switches**:

| Switch | Side | What it does |
|---|---|---|
| `validateSchema()` | **Response side** (after) | Validates the answer; if wrong, **retries** with the error message |
| `useProviderStructuredOutput()` | **Request side** (before) | Sends the schema to the provider's API so the provider **enforces** it |

- Structured output is **best effort**. Use these switches when correctness matters.
- Streaming is **not supported** with either `.entity()` or `validateSchema()`.

**Problem Solved**
- LLMs sometimes return broken or wrong-shaped JSON. These switches reduce failures a lot.

**Java Example**
```java
ActorsFilms films = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorsFilms.class, spec -> spec
        .useProviderStructuredOutput()
        .validateSchema());
```

---

# Part B — Schema validation

## 6. `validateSchema()` and the self-correcting loop

**Key Points**
- Turns on an **automatic retry loop**:
  1. Model responds.
  2. Spring AI **validates** the response against the JSON schema of your type.
  3. **Pass** → you get your typed object.
  4. **Fail** → the **specific error** (e.g., "missing required field `actor`", "expected `array`, got `string`") is **added to the user prompt** and the call is **re-issued**.
  5. Up to **3 attempts by default**.
- The retry is **not blind**: the model sees what was wrong and can fix it.
- Powered by **`StructuredOutputValidationAdvisor`** (a recursive advisor), **auto-registered** when you call `validateSchema()`. No other config needed.
- **Streaming not supported** (needs the complete response).
- Token usage is **accumulated across all attempts**, so `ChatResponse` shows the **total** usage of all retries.

```
Call model → validate → OK? → return object
                     └─ NO → add error to prompt → call again (max 3)
```

**Use Cases**
- Extraction pipelines, data entry automation, anything saved to DB.

**Where to Use**
- When malformed output would break downstream code.

**Problem Solved**
- Stops random JSON failures without writing your own retry loop.

**Trade-off**
- Retries = **more latency and more tokens** (cost).

**Java Example**
```java
ActorsFilms films = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorsFilms.class, spec -> spec.validateSchema());
```

---

## 7. Customizing `StructuredOutputValidationAdvisor`

**Key Points**
- Defaults: **3 attempts**, default `JsonMapper`.
- To customize (more attempts, pre-supplied schema, different mapper) **build your own advisor** and register it. An explicitly registered advisor **replaces** the auto-registered one.
- Configure using **either**:
  - `outputType(...)` — schema derived from the type, **or**
  - `outputJsonSchema(...)` — a ready schema string.
  - These two are **mutually exclusive**.
- Key behaviors:
  - Derives schema from type, or accepts a schema string.
  - Validates using JSON Schema **DRAFT_2020_12**.
  - Retries on failure (default 3).
  - Adds the validation error to the prompt on retry.
  - Accumulates token usage across attempts.
  - Optional custom `JsonMapper`.

**Use Cases**
- Critical flows where you want 5 attempts; custom converters with their own schema.

**Where to Use**
- `@Configuration` ChatClient setup.

**Problem Solved**
- Tune retry behavior to your reliability vs cost needs.

**Java Example**
```java
// Option 1: schema from type, more attempts
var validationAdvisor = StructuredOutputValidationAdvisor.builder()
    .outputType(ActorsFilms.class)
    .maxRepeatAttempts(5)
    .build();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(validationAdvisor)
    .build();

// Option 2: pre-supplied schema (from a converter)
var validationAdvisor2 = StructuredOutputValidationAdvisor.builder()
    .outputJsonSchema(myConverter.getJsonSchema())
    .build();
```

---

# Part C — Provider-native structured output

## 8. `useProviderStructuredOutput()`

**Key Points**
- Default approach = **response-side**: schema is added to the prompt as **text**, model is *asked* to comply, parsed afterward.
- Provider-native = **request-side constraint**: schema is sent to the provider **as an API field**. The provider **enforces** it — invalid output **cannot be produced**.
- Provider examples: OpenAI Structured Outputs, Anthropic structured output extension, Gemini `responseSchema`, Mistral `response_format`.
- What changes on the wire:
  - System prompt **no longer has** the JSON format instruction (fewer tokens, cleaner).
  - Schema is sent as an **API-level field**.
  - Provider runtime enforces it.
- Benefits: **higher reliability**, **cleaner prompts**, **better performance** (model can optimize internally).

**Use Cases**
- Strict schemas, high-volume extraction, enterprise pipelines.

**Where to Use**
- When you need stronger guarantees than prompt instructions and your provider/model supports it.

**Problem Solved**
- Removes most "model ignored my format" errors at the source.

**Java Example**
```java
ActorsFilms films = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorsFilms.class, spec -> spec.useProviderStructuredOutput());
```

---

## 9. How support is detected, supported models, why off by default

**Key Points**
- **Detection**: Spring AI checks if the model's chat options implement **`StructuredOutputChatOptions`**.
  - **Not implemented** → the flag is **silently ignored** and it falls back to prompt-based default. (No error — be careful, you may think it is on when it is not.)
- **Supported (as of Spring AI 2.0)**:

| Provider | Models |
|---|---|
| OpenAI | GPT-4o and later (JSON Schema support) |
| Anthropic | Claude 3.5 Sonnet and later |
| Google GenAI | Gemini 1.5 Pro and later |
| Mistral AI | Mistral Small and later (JSON Schema support) |
| Ollama | Models with JSON Schema support (model-specific) |

- The same `.useProviderStructuredOutput()` call works whichever provider is configured.
- **Off by default** because support varies across models; older/non-supporting models may **reject** the request. The prompt-based default works everywhere.
- **Always test with your exact model version.**

**Use Cases / Where to Use**
- Deciding when it is safe to switch it on.

**Problem Solved**
- Explains why it is a switch and not a default.

**Java Example**
```java
// Works the same with any wired provider
@Service
class ExtractionService {
    private final ChatClient chatClient;   // could be OpenAI, Anthropic, Gemini...

    ExtractionService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    ActorsFilms extract(String text) {
        return chatClient.prompt()
            .user(text)
            .call()
            .entity(ActorsFilms.class, spec -> spec.useProviderStructuredOutput());
    }
}
```

---

## 10. Known limitations

### 10.1 Partial JSON Schema support
- Even supported providers accept only **part** of JSON Schema. Common problems: `$ref`, **deeply nested arrays**, `allOf` / `anyOf` / `oneOf`, **regex patterns**, **recursive types**.
- Shape drift from this is exactly what `validateSchema()` catches.
- **Tip**: keep your records **simple and flat**.

### 10.2 Ollama: model-specific instability
- Not all Ollama models honor the schema constraint.
- Models with **reasoning/"thinking" mode** (e.g., `qwen3:8b`, `qwen3.5:9b`, newer Qwen variants) may return their **reasoning text** instead of JSON → deserialization error like:
  ```
  StreamReadException: Unrecognized token 'The': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
  ```
- Fixes:
  - Use a different model (e.g., `llama3.1:latest`), or
  - Fall back to the default prompt-based approach, or
  - Combine `useProviderStructuredOutput()` + `validateSchema()` (auto-retry).

```java
ActorsFilms films = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorsFilms.class, spec -> spec
        .useProviderStructuredOutput()
        .validateSchema());
```

### 10.3 OpenAI: no top-level array
- OpenAI Structured Outputs API **does not accept a top-level JSON array**.
- `List<T>` with native output on OpenAI → **API error**.

```java
// DOES NOT WORK with OpenAI native structured output
List<ActorsFilms> films = chatClient.prompt()
    .user("Generate filmographies for Tom Hanks and Bill Murray.")
    .call()
    .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {},
            spec -> spec.useProviderStructuredOutput());   // fails
```

**Fix 1 — wrap the list in a container record**
```java
record FilmographyList(List<ActorsFilms> films) {}

FilmographyList result = chatClient.prompt()
    .user("Generate filmographies for Tom Hanks and Bill Murray.")
    .call()
    .entity(FilmographyList.class, spec -> spec.useProviderStructuredOutput());

List<ActorsFilms> films = result.films();
```

**Fix 2 — use the default prompt-based approach (no native output)**
```java
List<ActorsFilms> films = chatClient.prompt()
    .user("Generate filmographies for Tom Hanks and Bill Murray.")
    .call()
    .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {});
```

**Problem Solved**: avoids common production errors.

---

## 11. Enabling globally

**Key Points**
- `useProviderStructuredOutput()` is a **per-call** switch.
- To enable for **every call**, use advisor param **`AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`**:
  - **per request** with `.advisors(...)`, or
  - **default** on the builder with `.defaultAdvisors(...)`.

**Use Cases**
- A dedicated "extraction" ChatClient where every call needs native output.

**Where to Use**
- `@Configuration` for a special-purpose ChatClient bean.

**Problem Solved**
- Avoids repeating `spec -> spec.useProviderStructuredOutput()` everywhere.

**Java Example**
```java
// Per request
ActorsFilms films = chatClient.prompt()
    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorsFilms.class);

// Globally on the builder
@Bean
ChatClient chatClient(ChatClient.Builder builder) {
    return builder
        .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .build();
}
```

---

## 12. Provider built-in JSON mode

**Key Points**
- **Independent** of `useProviderStructuredOutput()`: some providers have **their own config properties** for JSON output.

| Provider | Property | Notes |
|---|---|---|
| OpenAI | `spring.ai.openai.chat.response-format` | `JSON_OBJECT` (just valid JSON) or `JSON_SCHEMA` (with your schema) |
| Ollama | `spring.ai.ollama.chat.format` | Only accepted value currently: `json` |
| Mistral AI | `spring.ai.mistralai.chat.response-format` | `{ "type": "json_object" }` = JSON mode; `{ "type": "json_schema" }` + schema = native structured output |

**Use Cases**
- You want JSON for **all calls** of an app through configuration, with no code changes.

**Where to Use**
- `application.properties` / `application.yml`.

**Problem Solved**
- Quick global JSON mode via config.

**Config Example**
```properties
# application.properties
spring.ai.ollama.chat.format=json
# spring.ai.openai.chat.response-format=...   (check docs for exact value format of your version)
```
> `JSON_OBJECT` only guarantees **valid JSON**, not your **shape**. `JSON_SCHEMA` guarantees the **shape**.

---

## 13. Combining both switches

**Key Points**
- They solve **different problems**:
  - `useProviderStructuredOutput()` → **reduces** the chance of bad output (request-side).
  - `validateSchema()` → **catches leftover** bad output and fixes it (response-side).
- Use **both** when downstream code **cannot tolerate shape drift**.
- Especially useful for provider edge cases and **reasoning-model quirks** (e.g., Ollama reasoning models).

**Where to Use**
- Critical production pipelines: payments, compliance, data ingestion.

**Problem Solved**
- Highest practical reliability for structured output.

**Java Example**
```java
ActorsFilms films = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorsFilms.class, spec -> spec
        .useProviderStructuredOutput()
        .validateSchema());
```

---

# Part D — Output converters

## 14. `StructuredOutputConverter` API

**Key Points**
- `.entity()` is built on top of **`StructuredOutputConverter`**. Most apps never touch it directly.
- Use it directly when you need to:
  - parse output that built-in converters **reject** (e.g., JSON in **markdown code fences**),
  - produce **non-JSON** formats (YAML, CSV),
  - use a converter directly with the **low-level `ChatModel`** API.
- A converter works **before and after** the LLM call:
  - **Before**: adds **format instructions** to the prompt.
  - **After**: **parses** the text output into your type.
- It is **best effort** — no guarantee. Combine with schema validation.
- **Not used for tool calling** (tool calling already gives structured output).

```java
public interface StructuredOutputConverter<T> extends Converter<String, T>, FormatProvider {
    default String getJsonSchema() {
        return NO_JSON_SCHEMA;   // "" if not available
    }
}

public interface FormatProvider {
    String getFormat();          // instructions sent to the model
}
```

- `FormatProvider.getFormat()` → tells the model **how** to answer. Example instructions:
  ```
  Your response should be in JSON format.
  The data structure for the JSON should match this Java class: java.util.HashMap
  Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
  ```
- `Converter<String, T>` → **text → your type**.
- Usually, format is added at the end of the user text via a `{format}` placeholder in `PromptTemplate`.

**Problem Solved**: Full control over how output is requested and parsed.

**Java Example (low-level pattern)**
```java
StructuredOutputConverter<ActorsFilms> converter = new BeanOutputConverter<>(ActorsFilms.class);

String template = """
    Generate the filmography of 5 movies for {actor}.
    {format}
    """;

Prompt prompt = PromptTemplate.builder()
    .template(template)
    .variables(Map.of("actor", "Tom Hanks", "format", converter.getFormat()))
    .build()
    .create();

String text = chatModel.call(prompt).getResult().getOutput().getText();
ActorsFilms result = converter.convert(text);
```

---

## 15. Role of `getJsonSchema()`

**Key Points**
- Added in **2.0** as a **default method** on `StructuredOutputConverter`.
- It is the **bridge** that lets a converter work with **`useProviderStructuredOutput()`** and **`validateSchema()`**.
- **Implement it** (usually delegate to `BeanOutputConverter`) → **both switches work**.
- **Leave default** (`""`) → **both switches become no-ops** for that converter.

| Your converter | `getJsonSchema()` | Switches |
|---|---|---|
| Delegates to `BeanOutputConverter` | returns schema | Work |
| Default (not overridden) | `""` | Ignored |

**Use Cases**
- Custom JSON converters that still want validation/native output.

**Problem Solved**
- Custom converters do not lose reliability features.

---

## 16. Available converters

| Converter | What it does |
|---|---|
| `AbstractConversionServiceOutputConverter<T>` | Base class with a pre-configured `GenericConversionService`. **No default** `FormatProvider`. |
| `AbstractMessageOutputConverter<T>` | Base class with a pre-configured `MessageConverter`. **No default** `FormatProvider`. |
| `BeanOutputConverter<T>` | Java class or `ParameterizedTypeReference` → instructs model to produce JSON matching a **DRAFT_2020_12 JSON Schema** from your class; uses a `JsonMapper` to deserialize into your object. |
| `MapOutputConverter` | Instructs RFC8259-compliant JSON; converts to `Map<String, Object>`. Extends `AbstractMessageOutputConverter`. |
| `ListOutputConverter` | Instructs **comma-delimited** list; converts to `List` via `ConversionService`. Extends `AbstractConversionServiceOutputConverter`. |

**Choosing**
- Your own class/record → `BeanOutputConverter` (or just `.entity(Class)`).
- Unknown/dynamic keys → `MapOutputConverter`.
- Simple list of strings/values → `ListOutputConverter`.

---

## 17. BeanOutputConverter

**Key Points**
- Most used converter. `.entity(Class)` uses it internally.
- Works with records and normal classes, and `ParameterizedTypeReference` for generics.
- **Property ordering**: use Jackson's **`@JsonPropertyOrder`** to control the order of properties in the generated schema (works for records and classes).

**Use Cases**
- Typed extraction of structured data.

**Where to Use**
- Default choice for structured output.

**Problem Solved**
- Schema generation + parsing with no manual code.

**Java Example — high-level**
```java
record ActorsFilms(String actor, List<String> movies) {}

ActorsFilms actorsFilms = ChatClient.create(chatModel).prompt()
    .user(u -> u.text("Generate the filmography of 5 movies for {actor}.")
                .param("actor", "Tom Hanks"))
    .call()
    .entity(ActorsFilms.class);
```

**Java Example — low-level**
```java
BeanOutputConverter<ActorsFilms> beanOutputConverter =
    new BeanOutputConverter<>(ActorsFilms.class);

String format = beanOutputConverter.getFormat();
String actor = "Tom Hanks";

String template = """
    Generate the filmography of 5 movies for {actor}.
    {format}
    """;

Generation generation = chatModel.call(
    PromptTemplate.builder()
        .template(template)
        .variables(Map.of("actor", actor, "format", format))
        .build()
        .create()).getResult();

ActorsFilms actorsFilms = beanOutputConverter.convert(generation.getOutput().getText());
```

**Property ordering**
```java
@JsonPropertyOrder({"actor", "movies"})
record ActorsFilms(String actor, List<String> movies) {}
```

**Generic types — high-level**
```java
List<ActorsFilms> actorsFilms = ChatClient.create(chatModel).prompt()
    .user("Generate the filmography of 5 movies for Tom Hanks and Bill Murray.")
    .call()
    .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {});
```

**Generic types — low-level**
```java
BeanOutputConverter<List<ActorsFilms>> outputConverter = new BeanOutputConverter<>(
    new ParameterizedTypeReference<List<ActorsFilms>>() { });

String format = outputConverter.getFormat();
String template = """
    Generate the filmography of 5 movies for Tom Hanks and Bill Murray.
    {format}
    """;

Prompt prompt = PromptTemplate.builder()
    .template(template)
    .variables(Map.of("format", format))
    .build()
    .create();

Generation generation = chatModel.call(prompt).getResult();
List<ActorsFilms> actorsFilms = outputConverter.convert(generation.getOutput().getText());
```

---

## 18. MapOutputConverter

**Key Points**
- Returns `Map<String, Object>` — good when **keys are not fixed** or you do not want a class.
- Instructs the model for **RFC8259-compliant JSON**.
- Values are `Object` → you cast them yourself (less type-safe).

**Use Cases**
- Quick prototypes, dynamic fields, one-off scripts.

**Where to Use**
- When defining a record is overkill.

**Problem Solved**
- Flexible output with no extra class.

**Java Example — high-level**
```java
Map<String, Object> result = ChatClient.create(chatModel).prompt()
    .user(u -> u.text("Provide me a List of {subject}")
                .param("subject", "an array of numbers from 1 to 9 under they key name 'numbers'"))
    .call()
    .entity(new ParameterizedTypeReference<Map<String, Object>>() {});
```

**Java Example — low-level**
```java
MapOutputConverter mapOutputConverter = new MapOutputConverter();

String format = mapOutputConverter.getFormat();
String template = """
    Provide me a List of {subject}
    {format}
    """;

Prompt prompt = PromptTemplate.builder().template(template)
    .variables(Map.of(
        "subject", "an array of numbers from 1 to 9 under they key name 'numbers'",
        "format", format))
    .build().create();

Generation generation = chatModel.call(prompt).getResult();
Map<String, Object> result = mapOutputConverter.convert(generation.getOutput().getText());
```

---

## 19. ListOutputConverter

**Key Points**
- Asks the model for a **comma-delimited list** → returns `List<String>` (or converts via `ConversionService`).
- Very simple and light (small output).
- Needs a `ConversionService` (e.g., `new DefaultConversionService()`).
- Passed directly into `.entity(...)`.

**Use Cases**
- Tags, keywords, flavors, ideas, simple lists.

**Where to Use**
- When you need a plain list of simple values.

**Problem Solved**
- No need for a wrapper record for simple lists.

**Java Example — high-level**
```java
List<String> flavors = ChatClient.create(chatModel).prompt()
    .user(u -> u.text("List five {subject}")
                .param("subject", "ice cream flavors"))
    .call()
    .entity(new ListOutputConverter(new DefaultConversionService()));
```

**Java Example — low-level**
```java
ListOutputConverter listOutputConverter = new ListOutputConverter(new DefaultConversionService());

String format = listOutputConverter.getFormat();
String template = """
    List five {subject}
    {format}
    """;

Prompt prompt = PromptTemplate.builder().template(template)
    .variables(Map.of("subject", "ice cream flavors", "format", format))
    .build().create();

Generation generation = chatModel.call(prompt).getResult();
List<String> list = listOutputConverter.convert(generation.getOutput().getText());
```

---

## 20. Custom converters (lenient JSON)

**Key Points**
- `BeanOutputConverter` is **strict**: response must be parseable JSON.
- Models often wrap JSON in **markdown fences**:
  ````
  Here's the filmography:
  ```json
  { "actor": "Tom Hanks", "movies": ["Forrest Gump", "Cast Away"] }
  ```
  ````
  → `BeanOutputConverter` **throws** (it hits the "H" of "Here's").
- Fix: **custom converter** that strips fences, extracts JSON, then **delegates** to `BeanOutputConverter`.
- Because it **delegates `getJsonSchema()`**, both `validateSchema()` and `useProviderStructuredOutput()` **still work**.
- Pass the converter instance to `.entity(...)` instead of a class.

**Use Cases**
- Models that add prose or fences around JSON (common with chatty or local models).

**Where to Use**
- Production apps using models that do not always return clean JSON.

**Problem Solved**
- Parsing errors from fenced or prefixed JSON.

**Java Example**
```java
public class LenientJsonOutputConverter<T> implements StructuredOutputConverter<T> {

    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    private final BeanOutputConverter<T> delegate;

    public LenientJsonOutputConverter(Class<T> targetType) {
        this.delegate = new BeanOutputConverter<>(targetType);
    }

    @Override public String getFormat()     { return delegate.getFormat(); }
    @Override public String getJsonSchema() { return delegate.getJsonSchema(); }   // keeps switches working

    @Override
    public T convert(String source) {
        var matcher = FENCE.matcher(source);
        String json = matcher.find() ? matcher.group(1).trim() : source.trim();
        return delegate.convert(json);
    }
}

// Usage
ActorsFilms films = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(new LenientJsonOutputConverter<>(ActorsFilms.class));
```

---

## 21. Non-JSON formats (YAML, CSV)

**Key Points**
- For formats JSON cannot cover (**YAML** for config generators, **CSV** for data extraction), implement `StructuredOutputConverter` **from scratch**:
  - write your own `getFormat()` (prompt instructions),
  - write your own `convert(...)` (parser).
- **Leave `getJsonSchema()` at default** → both reliability switches are **skipped**; the normal prompt-based path runs.

**Use Cases**
- Generate YAML config, CSV rows for spreadsheets, custom text formats.

**Where to Use**
- Integration with systems that need non-JSON data.

**Problem Solved**
- Structured output beyond JSON.

**Java Example (simple CSV → List<List<String>>)**
```java
public class CsvOutputConverter implements StructuredOutputConverter<List<List<String>>> {

    @Override
    public String getFormat() {
        return """
            Respond ONLY with CSV rows. No header, no explanations, no code fences.
            Separate values with commas, one record per line.
            """;
    }

    @Override
    public List<List<String>> convert(String source) {
        return source.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .map(line -> Arrays.stream(line.split(",")).map(String::trim).toList())
            .toList();
    }
    // getJsonSchema() left as default -> switches do nothing
}

// Usage
List<List<String>> rows = chatClient.prompt()
    .user("Give me 3 books with title,author")
    .call()
    .entity(new CsvOutputConverter());
```
> This simple split does not handle commas inside quotes. Use a CSV library for real data.

---

# Part E — Revision

## 22. Cheat sheet

| You need | Use |
|---|---|
| Default, works on every provider | `.entity(Type.class)` |
| `List<T>`, `Map<K,V>` | `.entity(new ParameterizedTypeReference<...>() {})` |
| Don't fail on bad output | `.entity(Type.class, spec -> spec.validateSchema())` |
| Provider enforces schema | `.entity(Type.class, spec -> spec.useProviderStructuredOutput())` |
| Both (request constraint + retry) | `spec.useProviderStructuredOutput().validateSchema()` |
| Entity + token usage/metadata | `.responseEntity(...)` |
| Native output on all calls | `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` |
| More retry attempts | `StructuredOutputValidationAdvisor.builder().maxRepeatAttempts(5)` |
| Model wraps JSON in fences | Custom `StructuredOutputConverter` |
| YAML / CSV | Custom converter, leave `getJsonSchema()` default |
| Simple list | `ListOutputConverter` |
| Dynamic keys | `MapOutputConverter` |
| Streaming | **Not supported** with `.entity()` |

```java
// Most common production pattern
ActorsFilms films = chatClient.prompt()
    .user("...")
    .call()
    .entity(ActorsFilms.class, spec -> spec
        .useProviderStructuredOutput()
        .validateSchema());
```

---

## 23. Common mistakes

1. Using `.entity()` with `.stream()` → **not supported**. Use `.call()`.
2. Trusting default `.entity()` as **guaranteed** → it is best effort. Add `validateSchema()` for important flows.
3. Using `List<T>` with **OpenAI native structured output** → API error. Wrap in a container record.
4. Enabling `useProviderStructuredOutput()` on a model that does not support it → **silently ignored** (no error), so you may think it is working.
5. Using complex schemas (`$ref`, `anyOf`, recursion, deep nesting) with native output → partial support. Keep records simple.
6. Using **Ollama reasoning models** (e.g., Qwen thinking variants) with native output alone → plain text reasoning breaks parsing. Combine with `validateSchema()` or use another model.
7. Forgetting retries cost **extra tokens and time** with `validateSchema()`.
8. Writing a custom converter but **not delegating `getJsonSchema()`** → both switches become no-ops.
9. Calling the model twice (once for entity, once for metadata) → use `.responseEntity()`.
10. Forgetting `{format}` placeholder when using converters at the **low-level `ChatModel`** API → model gets no format instructions.
11. Using `.entity(Class)` for generics → type info lost. Use `ParameterizedTypeReference`.
12. Using the structured output converter for **tool calling** → not needed; tool calling is already structured.
13. Setting both `outputType` and `outputJsonSchema` on the validation advisor → they are **mutually exclusive**.
14. Not testing with the **exact model version** in use.

---

## 24. Interview quick Q&A

**Q1. What is structured output in Spring AI?**
Making the model produce text that follows a schema, and parsing it into a typed Java object using `.entity(...)`.

**Q2. What happens behind the scenes in `.entity()`?**
Schema generator makes a JSON schema from your type → schema added to the prompt → model's JSON goes through a converter into your type.

**Q3. Does `.entity()` guarantee the structure?**
No. It is best effort. The model may add or skip fields or wrap JSON in prose, and parsing throws.

**Q4. Does `.entity()` work with streaming?**
No. It is `.call()` only because typed parsing needs the full response. `validateSchema()` also needs the full response.

**Q5. How to return `List<T>`?**
`.entity(new ParameterizedTypeReference<List<T>>() {})`.

**Q6. What does `validateSchema()` do?**
Validates the response against the JSON schema. On failure it adds the error to the prompt and retries, up to 3 attempts by default.

**Q7. Which class powers `validateSchema()`?**
`StructuredOutputValidationAdvisor`, auto-registered. You can register your own to change attempts/mapper/schema (it replaces the auto one).

**Q8. `outputType` vs `outputJsonSchema`?**
Either derive schema from a Java type, or supply a schema string. Mutually exclusive.

**Q9. What does `useProviderStructuredOutput()` do?**
Sends the schema to the provider as an API-level constraint, so the provider enforces it instead of relying on prompt text.

**Q10. How does Spring AI know a model supports native output?**
Checks if the chat options implement `StructuredOutputChatOptions`. If not, the flag is silently ignored.

**Q11. Why is native structured output off by default?**
Support differs across providers/models; older models may reject the request. Prompt-based works everywhere.

**Q12. OpenAI top-level array problem?**
OpenAI native structured output rejects top-level arrays. Wrap the list in a container record or use the default prompt-based approach.

**Q13. Problem with Ollama reasoning models?**
They may return reasoning text instead of JSON → deserialization error. Use another model, fall back to default, or combine with `validateSchema()`.

**Q14. How to enable native output for all calls?**
`AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` as a default advisor on the builder (or per request).

**Q15. What is the role of `getJsonSchema()`?**
It connects a converter to both switches. Implement it → switches work. Leave default → they become no-ops.

**Q16. `StructuredOutputConverter` interfaces?**
Extends `Converter<String, T>` (text → type) and `FormatProvider` (`getFormat()` instructions for the model).

**Q17. Name the built-in converters.**
`BeanOutputConverter`, `MapOutputConverter`, `ListOutputConverter`, plus base classes `AbstractConversionServiceOutputConverter` and `AbstractMessageOutputConverter`.

**Q18. How to control property order in the schema?**
Jackson `@JsonPropertyOrder` on the record/class.

**Q19. Model returns JSON inside markdown fences. What to do?**
Write a custom converter that strips fences and delegates to `BeanOutputConverter` (also delegate `getJsonSchema()`).

**Q20. How to support YAML or CSV?**
Implement `StructuredOutputConverter` yourself (`getFormat()` + `convert()`); leave `getJsonSchema()` default so the switches are skipped.

**Q21. How to get token usage along with the entity?**
Use `.responseEntity(...)` → `result.response().getMetadata().getUsage()`.

**Q22. Is the converter used for tool calling?**
No. Tool calling already gives structured output by default.
