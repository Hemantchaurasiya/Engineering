# Pattern 8: Parallelization

Sequential chaining is wasteful when subtasks don't depend on each other. Parallelization fires off multiple LLM calls concurrently and aggregates the results — either **sectioning** (split one task into independent pieces, run them all at once) or **voting** (run the same task multiple times and aggregate for consensus, improving reliability on judgment calls).

## What's happening

Each independent LLM call runs in its own thread, and you wait for all to complete before aggregating. Java 21's virtual threads make this cheap and simple — no need for a reactive stack just to parallelize a handful of LLM calls. Total latency becomes roughly the slowest single call, not the sum of all of them.

---

## Code

### 1. Sectioning — split a document review into independent parallel checks

```java
package com.example.genai.pattern8;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;

public record ReviewResult(String dimension, String feedback) {}

@Service
public class ParallelReviewService {

    private final ChatClient chatClient;
    // Virtual-thread executor: cheap, blocking-style code, no reactive complexity
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ParallelReviewService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public List<ReviewResult> reviewDocument(String document) {

        record Check(String dimension, String instruction) {}

        List<Check> checks = List.of(
                new Check("grammar", "Review this text for grammar and spelling issues only."),
                new Check("clarity", "Review this text for clarity and readability only."),
                new Check("factual_accuracy", "Review this text for any factual claims that seem questionable."),
                new Check("tone", "Review this text's tone — is it appropriate for a professional audience?")
        );

        // Fan out: submit all calls concurrently
        List<CompletableFuture<ReviewResult>> futures = checks.stream()
                .map(check -> CompletableFuture.supplyAsync(() -> {
                    String feedback = chatClient.prompt()
                            .user(check.instruction() + "\n\nText:\n" + document)
                            .call()
                            .content();
                    return new ReviewResult(check.dimension(), feedback);
                }, executor))
                .toList();

        // Fan in: wait for all to complete, then collect
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }
}
```

### 2. Voting — run the same judgment call 3 times, aggregate for reliability

```java
package com.example.genai.pattern8;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public enum ModerationVerdict { SAFE, UNSAFE }

@Service
public class VotingModerationService {

    private final ChatClient chatClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public VotingModerationService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("You are a content moderator. Classify content strictly.")
                .build();
    }

    public ModerationVerdict moderate(String content) {

        int voteCount = 3;

        List<CompletableFuture<ModerationVerdict>> votes = java.util.stream.IntStream.range(0, voteCount)
                .mapToObj(i -> CompletableFuture.supplyAsync(() ->
                        chatClient.prompt()
                                .user("Classify this content as SAFE or UNSAFE: " + content)
                                .call()
                                .entity(ModerationVerdict.class),
                        executor))
                .toList();

        Map<ModerationVerdict, Long> tally = votes.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        // Majority wins; ties or any UNSAFE vote could be treated conservatively
        return tally.getOrDefault(ModerationVerdict.UNSAFE, 0L) >= 2
                ? ModerationVerdict.UNSAFE
                : ModerationVerdict.SAFE;
    }
}
```

### 3. Controller

```java
@RestController
@RequestMapping("/api/parallel")
public class ParallelController {

    private final ParallelReviewService reviewService;

    public ParallelController(ParallelReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/review")
    public List<ReviewResult> review(@RequestBody String document) {
        return reviewService.reviewDocument(document);
    }
}
```

---

## Sectioning vs. voting — when to use which

|  | Sectioning | Voting |
|---|---|---|
| **Goal** | Speed — do independent things at once | Reliability — reduce variance on one judgment |
| **Calls** | Different prompts/instructions | Same prompt, run N times |
| **Aggregation** | Concatenate/combine distinct results | Majority vote or averaging |
| **Good for** | Multi-dimensional reviews, multi-source summarization | Moderation, safety checks, ambiguous classifications |

---

## Production notes

- Watch your rate limits — fanning out 10+ concurrent calls to the same provider can trip per-minute token/request limits. Cap concurrency with a bounded executor or semaphore for production traffic.
- Virtual threads make this code look synchronous and simple, but each one still makes a real network call — failures need handling (`CompletableFuture` exceptions propagate on `.join()`, so wrap with `.exceptionally()` or `.handle()` if partial results should still return).
- Voting is most valuable when the task has genuine ambiguity (e.g. borderline moderation calls) — for tasks the model gets right consistently, voting just triples your cost for no accuracy gain.