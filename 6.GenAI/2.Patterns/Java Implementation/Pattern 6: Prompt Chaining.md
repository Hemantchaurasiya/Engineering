# Pattern 6: Prompt Chaining

This is the first true "workflow" pattern. Instead of one mega-prompt asking the model to do everything at once, you decompose the task into a sequence of focused LLM calls, where each step's output becomes the next step's input. Each call is simpler and more reliable than one giant call trying to do it all — and you can validate or transform data between steps using regular Java code.

## What's happening

Each LLM call is plain Java: a method that takes input, calls `ChatClient`, returns output. You wire them together with normal method composition — no special framework magic needed. Because each step's output type is a regular Java object (often using `.entity()` from Pattern 2), you can insert validation, branching, or early-exit logic between steps using ordinary `if` statements — something a single giant prompt can't give you.

---

## Code

Use case: turn a rough outline into a polished blog post, via 3 sequential, focused calls.

```java
package com.example.genai.pattern6;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

public record KeyPoints(java.util.List<String> points) {}

@Service
public class BlogPostChainService {

    private final ChatClient chatClient;

    public BlogPostChainService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String generateBlogPost(String topic) {

        // Step 1: extract structured key points to write about
        KeyPoints keyPoints = chatClient.prompt()
                .user("List 4-5 key points worth covering in a blog post about: " + topic)
                .call()
                .entity(KeyPoints.class);

        // --- gate check: plain Java, no LLM call needed ---
        if (keyPoints.points().size() < 3) {
            throw new IllegalStateException("Not enough substance to write about: " + topic);
        }

        // Step 2: draft the post from those points
        String draft = chatClient.prompt()
                .user("""
                      Write a draft blog post covering these points:
                      %s
                      Keep it informative but conversational, ~300 words.
                      """.formatted(String.join("\n- ", keyPoints.points())))
                .call()
                .content();

        // Step 3: polish tone, fix flow — operates only on step 2's output
        String polished = chatClient.prompt()
                .user("""
                      Improve this draft's tone, flow, and clarity.
                      Keep the same structure and length. Return only the improved text.

                      Draft:
                      %s
                      """.formatted(draft))
                .call()
                .content();

        return polished;
    }
}
```

```java
@RestController
@RequestMapping("/api/chain")
public class BlogPostController {

    private final BlogPostChainService chainService;

    public BlogPostController(BlogPostChainService chainService) {
        this.chainService = chainService;
    }

    @GetMapping("/blog-post")
    public String generate(@RequestParam String topic) {
        return chainService.generateBlogPost(topic);
    }
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/chain/blog-post?topic=why%20vector%20databases%20matter"
```

---

## Why chain instead of one big prompt?

| One mega-prompt | Chained calls |
|-----------------|---------------|
| Model juggles extraction + drafting + polishing all at once | Each call has a single, narrow job — higher accuracy per step |
| No way to validate mid-process | Gate checks between steps with regular Java |
| Hard to debug which part went wrong | Each step's output is independently inspectable/loggable |
| Can't swap models per step | Step 1 could use a cheap/fast model, step 3 a stronger one |

---

## When to use this pattern

Anthropic's own guidance applies directly here: use prompt chaining when a task can be cleanly decomposed into fixed subtasks — the latency/cost tradeoff (multiple LLM calls instead of one) is worth it specifically because each step becomes more accurate and verifiable. Don't chain when a single well-crafted prompt reliably does the job — added complexity isn't free.