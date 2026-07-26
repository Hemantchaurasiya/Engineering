# Pattern 16: Streaming Responses

Waiting for a full multi-paragraph response before showing anything feels slow and broken to users — they're used to ChatGPT-style token-by-token rendering. Spring AI's `ChatClient` supports this natively via `.stream()`, returning a reactive `Flux<String>` that emits text chunks as the model generates them, which Spring WebFlux can pipe straight out as Server-Sent Events.

## What's happening

`.stream()` is a drop-in replacement for `.call()` that returns reactively instead of blocking — same `prompt().user(...)` builder, different terminal operation. The `Flux<String>` emits each text delta as the underlying provider sends it over its own streaming connection (typically SSE under the hood from OpenAI/Anthropic), and Spring WebFlux forwards each emission straight to your client as it arrives.

---

## Setup

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

---

## Code

### 1. Basic text streaming endpoint

```java
package com.example.genai.pattern16;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/stream")
public class StreamingController {

    private final ChatClient chatClient;

    public StreamingController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();   // Flux<String> instead of String
    }
}
```

---

## Try it

```bash
curl -N "http://localhost:8080/api/stream/chat?message=Explain%20the%20CAP%20theorem%20in%20detail"
```

The `-N` flag disables curl's output buffering so you see chunks arrive incrementally rather than all at once.

---

### 2. Streaming the full ChatResponse

Useful when you need metadata — token usage, finish reason — alongside the text, not just raw strings.

```java
import org.springframework.ai.chat.model.ChatResponse;

@GetMapping(value = "/chat-detailed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chatDetailed(@RequestParam String message) {
    Flux<ChatResponse> responseFlux = chatClient.prompt()
            .user(message)
            .stream()
            .chatResponse();

    return responseFlux.map(response ->
            response.getResult().getOutput().getText());
}
```

---

### 3. Streaming combined with memory (Pattern 15) and tools (Pattern 5) — they compose normally

```java
@GetMapping(value = "/chat-with-memory", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chatWithMemory(@RequestParam String message,
                                    @RequestParam String conversationId) {
    return chatClient.prompt()
            .user(message)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .stream()
            .content();
}
```

---

### 4. Frontend consumption (vanilla JS, EventSource or fetch with a reader)

```javascript
const response = await fetch('/api/stream/chat?message=' + encodeURIComponent(userMessage));
const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    const chunk = decoder.decode(value);
    appendToChatUI(chunk);   // render incrementally as it arrives
}
```

---

## Production notes

- Streaming + tool calling has a wrinkle: when the model decides to call a tool mid-generation, the stream effectively pauses while the tool executes (no useful tokens to show during that gap) before resuming — your UI should handle that with a "thinking" / "using a tool" indicator rather than treating a pause as a stall.
- Backpressure matters in WebFlux — if your client is slower than the model's token rate (rare, but possible on instrumented/heavy frontends), `Flux` backpressure handles it natively without extra code, unlike imperative SSE solutions where you'd need to manage it manually.
- Error handling: wrap with `.onErrorResume()` to emit a graceful fallback chunk rather than silently dropping the connection if the provider call fails mid-stream.
- Streaming `.entity()` (structured output, Pattern 2) isn't directly supported the same way — you can't meaningfully parse partial JSON token-by-token. For structured output, stick to `.call()`; reserve `.stream()` for free-text generation.