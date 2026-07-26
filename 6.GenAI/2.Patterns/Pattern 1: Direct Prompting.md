# Pattern 1: Direct Prompting (Basic LLM Call)

This is the foundation everything else builds on: your app sends a prompt to the model through Spring AI's `ChatClient`, and gets a response back. No memory, no tools, no chaining — just a single round trip.

## What's happening

A user sends a prompt. Your controller passes it straight to `ChatClient` — Spring AI's fluent API over any LLM provider. `ChatClient` builds the request, sends it to the provider (OpenAI, Anthropic, Ollama, etc.), and hands back the plain text response. No state, no tools, no retries — it's the "hello world" of GenAI engineering, but everything else you'll learn is built on top of this single call.

---

## Setup

### `pom.xml` (Spring Boot 3.4.x, Java 21, Spring AI 1.0.x — the current GA line)

```xml
<properties>
    <java.version>21</java.version>
    <spring-ai.version>1.0.0</spring-ai.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### `application.yml`

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.7
```

---

## Code

Spring Boot auto-configures a `ChatClient.Builder` bean for you — just inject it.

```java
package com.example.genai.pattern1;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/direct")
public class DirectPromptController {

    private final ChatClient chatClient;

    // Spring AI auto-configures a ChatClient.Builder bean wired to your provider
    public DirectPromptController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("You are a concise, helpful assistant.")
                .build();
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}
```

That's it — three real lines of logic:

- `prompt()` starts a fluent request builder.
- `.user(question)` sets the user message.
- `.call().content()` executes synchronously and extracts the text.

---

## Try it

```bash
curl "http://localhost:8080/api/direct/ask?question=What%20is%20a%20vector%20embedding%3F"
```

---

## Why this matters as a foundation

Every later pattern is a variation on this same three-step shape (`prompt → call → content`):

- Routing picks which `ChatClient` config to call.
- Chaining feeds one call's `.content()` into the next call's `.user()`.
- RAG stuffs retrieved context into `.user()` before the call.
- Tool calling adds `.tools(...)` to the builder.
- Agents wrap this loop with planning and self-correction.