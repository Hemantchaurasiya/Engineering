# Pattern 15: Memory

Without memory, every request to your `ChatClient` is amnesia — the model has zero awareness of anything said before. Memory comes in two flavors: short-term (the current conversation's message history, so the model remembers what you just said) and long-term (durable facts that persist across sessions — preferences, history, profile info — retrieved like RAG, but about the user/conversation rather than documents).

## What's happening

Short-term memory is just the conversation's recent message window, stored per `conversationId` and automatically re-injected into every call — Spring AI's `MessageChatMemoryAdvisor` handles this transparently. Long-term memory is different: it's specific facts worth keeping forever (a user's stated preferences, recurring constraints), extracted out of conversations and stored in a vector store, retrieved by similarity exactly like RAG (Pattern 4) — except the "documents" are facts about the user, not company docs.

---

## Code

### Short-term memory

#### Setup

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
```

```java
@Configuration
public class MemoryConfig {

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)  // JdbcChatMemoryRepository for persistence
                .maxMessages(20)                              // sliding window
                .build();
    }
}
```

```java
package com.example.genai.pattern15;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/memory")
public class ConversationalMemoryController {

    private final ChatClient chatClient;

    public ConversationalMemoryController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message,
                        @RequestParam String conversationId) {
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/memory/chat?message=My%20name%20is%20Priya&conversationId=session-1"
curl "http://localhost:8080/api/memory/chat?message=What%27s%20my%20name%3F&conversationId=session-1"
```

```text
Your name is Priya
```

The `conversationId` is your partitioning key — different users/sessions get fully isolated histories, all transparently managed by the advisor.

---

## Long-term memory

```java
package com.example.genai.pattern15;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LongTermMemoryService {

    private final VectorStore vectorStore;

    public LongTermMemoryService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // Call this after a conversation to persist durable facts worth remembering
    public void remember(String userId, String fact) {
        vectorStore.add(List.of(new Document(
                UUID.randomUUID().toString(),
                fact,
                Map.of("userId", userId, "type", "long_term_fact"))));
    }

    // Call this before answering, to recall relevant facts about this user
    public List<String> recall(String userId, String currentMessage) {
        var results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(currentMessage)
                        .topK(3)
                        .filterExpression("userId == '" + userId + "'")
                        .build());
        return results.stream().map(Document::getText).toList();
    }
}
```

```java
@RestController
@RequestMapping("/api/memory")
public class LongTermMemoryController {

    private final ChatClient chatClient;
    private final LongTermMemoryService longTermMemory;

    public LongTermMemoryController(ChatClient.Builder builder, LongTermMemoryService longTermMemory) {
        this.chatClient = builder.build();
        this.longTermMemory = longTermMemory;
    }

    @GetMapping("/chat-with-recall")
    public String chatWithRecall(@RequestParam String userId, @RequestParam String message) {

        List<String> relevantFacts = longTermMemory.recall(userId, message);

        String contextualPrompt = relevantFacts.isEmpty()
                ? message
                : """
                  Known facts about this user:
                  %s

                  User says: %s
                  """.formatted(String.join("\n", relevantFacts), message);

        String response = chatClient.prompt(contextualPrompt).call().content();

        // Optionally: ask the model whether this exchange contains a durable fact worth saving
        // (a simple heuristic version is shown — production systems often use a dedicated
        // extraction call, similar to Pattern 2's structured output)
        if (message.toLowerCase().contains("i prefer") || message.toLowerCase().contains("i am")) {
            longTermMemory.remember(userId, message);
        }

        return response;
    }
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/memory/chat-with-recall?userId=u123&message=I%20prefer%20concise%20answers%20with%20no%20fluff"
```

Weeks later, in a brand new conversation:

```bash
curl "http://localhost:8080/api/memory/chat-with-recall?userId=u123&message=Explain%20how%20Kubernetes%20works"
```

The model recalls the preference and answers tersely, unprompted.

---

## Short-term vs. long-term

|  | Short-term (ChatMemory) | Long-term (vector-backed) |
|---|---|---|
| **Scope** | One conversation thread | Across all sessions, forever |
| **Storage** | Recent message window (sliding) | Selectively extracted durable facts |
| **Retrieval** | Always included, in order | Similarity-searched, only what's relevant |
| **Spring AI mechanism** | `ChatMemory` + `MessageChatMemoryAdvisor` | `VectorStore` (same interface as RAG) |

---

## Production notes

- Don't dump everything into long-term memory — extract selectively (ideally via a dedicated structured-output call, like Pattern 2, asking "is there a durable fact worth remembering here?") rather than naive keyword matching as shown above; naive heuristics both over- and under-capture.
- `JdbcChatMemoryRepository` (or Redis-backed equivalents) is what you want in production for short-term memory — in-memory storage loses everything on restart and doesn't work across horizontally scaled instances.
- Always cap the message window (`maxMessages`) — unbounded history grows token cost linearly with conversation length and eventually exceeds context limits.