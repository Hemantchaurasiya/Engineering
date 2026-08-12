# Pattern 17: Semantic Caching

A normal cache only matches identical keys — but **"What's the capital of France?"** and **"Tell me France's capital city"** mean the same thing and should hit the same cache entry, even though the strings differ completely. Semantic caching embeds the incoming query, searches past query embeddings for a near-identical match, and returns the cached response instead of calling the LLM at all — cutting cost and latency on repeated or rephrased questions.

---

# What's happening

This reuses the exact same **VectorStore** infrastructure from **RAG (Pattern 4)** — but instead of storing document chunks, you store past queries as embeddings, with the response text in metadata. On a new query, you do a similarity search against this cache first; if something above a very high threshold (e.g. **0.95+**) comes back, you return the cached response and skip the LLM call entirely. Otherwise you call the model normally and write the new pair into the cache for next time.

---

# Code

## SemanticCacheService.java

```java
package com.example.genai.pattern17;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SemanticCacheService {

    // High threshold deliberately: cache hits should only fire on near-identical
    // meaning, not loosely related queries — false hits return wrong answers.
    private static final double CACHE_SIMILARITY_THRESHOLD = 0.96;

    private final VectorStore cacheStore;

    public SemanticCacheService(VectorStore cacheStore) {
        this.cacheStore = cacheStore;
    }

    public Optional<String> checkCache(String query) {
        List<Document> matches = cacheStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(1)
                        .similarityThreshold(CACHE_SIMILARITY_THRESHOLD)
                        .build());

        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable((String) matches.get(0).getMetadata().get("response"));
    }

    public void store(String query, String response) {
        cacheStore.add(List.of(new Document(
                UUID.randomUUID().toString(),
                query,                                    // embedded for similarity search
                Map.of("response", response,               // the actual cached answer
                        "cachedAt", java.time.Instant.now().toString()))));
    }
}
```

---

## CachedChatController.java

```java
package com.example.genai.pattern17;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cached")
public class CachedChatController {

    private final ChatClient chatClient;
    private final SemanticCacheService cache;

    public CachedChatController(ChatClient.Builder builder, SemanticCacheService cache) {
        this.chatClient = builder.build();
        this.cache = cache;
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {

        // 1. Check cache first — skip the LLM entirely on a hit
        var cached = cache.checkCache(question);
        if (cached.isPresent()) {
            return cached.get();   // free, sub-millisecond response
        }

        // 2. Cache miss — call the LLM normally
        String response = chatClient.prompt()
                .user(question)
                .call()
                .content();

        // 3. Store for next time
        cache.store(question, response);

        return response;
    }
}
```

---

# Try it

```bash
curl "http://localhost:8080/api/cached/ask?question=What%20is%20the%20capital%20of%20France%3F"
# Cache miss — calls LLM, ~800ms, costs tokens

curl "http://localhost:8080/api/cached/ask?question=Tell%20me%20France%27s%20capital%20city"
# Cache hit — different wording, same meaning, ~20ms, zero LLM cost
```

---

# Production notes

- Threshold tuning is the whole game. Too low and you'll return cached answers to genuinely different questions (dangerous for anything factual or time-sensitive). Too high and you barely get any cache hits at all. Start around **0.95–0.97** and validate against real query logs before loosening.

- Don't cache time-sensitive or personalized queries — **"what's the weather today"** or **"what's in my cart"** should never hit a semantic cache, since the "same" question has a different correct answer depending on when/who asked. Gate caching to genuinely stable Q&A (FAQs, documentation lookups, static factual queries).

- Add a TTL/expiry — even stable-seeming answers (pricing, policies, product specs) go stale. Store a **cachedAt** timestamp (as shown above) and treat hits older than your freshness window as misses.

- Separate cache store from your RAG vector store — mixing document chunks and cached query/response pairs in one collection makes both retrieval paths noisier; keep them as distinct **VectorStore** beans/collections.

- For very high traffic, an exact-match cache (e.g. Redis keyed on a hash of the normalized query) layered in front of the semantic cache catches identical repeats even faster, with semantic caching catching the rephrased ones behind it.