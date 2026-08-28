# Pattern 3: Advanced RAG

[← Back to index](README.md)

## 1. What is Advanced RAG?

Advanced RAG is Naive RAG **plus a set of targeted fixes inserted around the same
retrieve-then-generate core**, grouped into three stages:

- **Pre-retrieval optimizations** — improve the *query* before it ever hits the vector
  store (e.g. rewrite a sloppy user query into a clean search query).
- **Retrieval optimizations** — improve *how* chunks are fetched (e.g. retrieve more
  candidates than you need).
- **Post-retrieval optimizations** — improve *what gets sent to the LLM* after
  retrieval (e.g. re-rank candidates and drop the weak ones before building the prompt).

Nothing here is a new "type" of RAG — it's the same pipeline from Pattern 1, with three
extra stations bolted on. Full versions of each station get their own deep-dive pattern
later in this series; this pattern's job is to show them working **together**.

## 2. What problem does it solve?

Pattern 2 catalogued five failure modes. Advanced RAG directly answers three of them:

| Naive RAG failure mode (Pattern 2) | Advanced RAG fix |
|---|---|
| ① Lexical mismatch | **Query rewriting** — an LLM call normalizes phrasing into the vocabulary likely used in the docs, before embedding. |
| ③ Irrelevant top-k | **Over-retrieve, then re-rank** — fetch top-20 candidates instead of top-4, then keep only the best 4 after re-ranking. |
| ④ No relevance ranking | **LLM-scored re-ranking** — score each candidate chunk against the query directly, not just raw embedding distance. |

The other two failure modes are deliberately left for later patterns — Advanced RAG is
a specific, well-known upgrade step, not "fix everything at once."

## 3. Realistic production scenario

**Company:** the same SaaS support bot from Pattern 2.

**Problem, concretely:** the diagnostic harness in Pattern 2 flagged
*"terminate my subscription"* as `low_relevance_top_k` — the user's word choice doesn't
match the docs' word choice ("cancel your plan").

**Goal:** without redesigning the whole system, add:

1. A query-rewriting step so "terminate my subscription" becomes a search query aligned
   with how the docs are actually written.
2. A wider initial retrieval (top-20) so the right chunk is almost certainly somewhere
   in the candidate pool.
3. A re-ranking step that scores those 20 candidates properly and keeps only the best 4
   for the final prompt.

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    Q[Raw user question:<br/>"terminate my subscription"] --> QR[Pre-retrieval:<br/>LLM query rewriter]
    QR --> Q2["Rewritten query:<br/>'cancel subscription plan policy'"]
    Q2 --> EMB[Embed rewritten query]
    EMB --> WR[Retrieval:<br/>wide fetch, top-20]
    D[(Vector Store)] --> WR
    WR --> RR[Post-retrieval:<br/>re-rank all 20 by relevance]
    RR --> TOP[Keep top-4 after re-ranking]
    TOP --> P[Build prompt: context + original question]
    P --> L[OllamaChatModel]
    L --> A[Grounded answer]

    style QR fill:#d4edda,stroke:#155724
    style WR fill:#d4edda,stroke:#155724
    style RR fill:#d4edda,stroke:#155724
```

## 5. Request-to-response walkthrough

1. **User asks:** *"terminate my subscription — how do I do that and will I get money
   back?"*
2. **Pre-retrieval — query rewriting:** a small, cheap LLM call reformulates this into a
   clean, doc-aligned search query.
3. **Retrieval — wide fetch:** the rewritten query is embedded and used to fetch the
   top-20 most similar chunks. Recall matters more than precision here.
4. **Post-retrieval — re-ranking:** each of the 20 candidate chunks is scored against
   the *original* user question; the top 4 by this new score are kept.
5. **Prompt assembly:** those 4 re-ranked chunks become the context, paired with the
   user's original question.
6. **Generation:** the LLM answers using the now-much-more-relevant context.
7. **Answer returned**, along with which stage contributed which chunk, for
   observability.

## 6. Why this pattern is appropriate here

- **Query rewriting is nearly free** compared to the cost of wrong answers, and fixes
  vocabulary mismatch without changing the documents or embedding model.
- **Over-retrieve-then-re-rank is a well-established, low-risk upgrade**: it doesn't
  change your vector store, chunking strategy, or embedding model.
- **Re-ranking catches what embedding similarity alone misses**: it's a sharper,
  more relevant signal exactly at the stage where it matters most.
- **When this is *not* enough:** if a single question genuinely needs facts merged from
  multiple documents, you need Multi-Hop RAG or Modular RAG (Patterns 23, 4).

## 7. Production-quality implementation (Java / Spring AI)

```java
package com.acme.rag.advanced;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Advanced RAG -- query rewriting + wide retrieval + re-ranking for a support bot.
 *
 * Adds three stages around the same retrieve-then-generate core from Pattern 1/2:
 *   1. Pre-retrieval: LLM rewrites the user's query into doc-aligned phrasing.
 *   2. Retrieval: fetch a wide candidate pool (top-20) instead of top-4.
 *   3. Post-retrieval: re-rank candidates against the ORIGINAL question, keep top-4.
 */
public final class AdvancedRagApp {

    record RagConfig(
            String embeddingModel, String llmModel, double llmTemperature,
            int chunkSize, int chunkOverlap, int wideK, int finalK, Path persistPath) {

        static RagConfig defaults() {
            return new RagConfig("nomic-embed-text", "llama3.1", 0.0, 800, 100, 20, 4,
                    Path.of("./vectorstore_support_docs_v2.json"));
        }
    }

    /** Pre-retrieval stage: normalize user phrasing into doc-aligned search terms. */
    static final class QueryRewriter {
        private static final String SYSTEM = """
                Rewrite the user's question into a short, keyword-rich search query suitable
                for a vector database of product documentation. Preserve the user's intent
                exactly, but prefer formal/product terminology over casual phrasing
                (e.g. 'terminate' -> 'cancel', 'get money back' -> 'refund').
                Return ONLY the rewritten query, nothing else.
                """;
        private final ChatClient chatClient;

        QueryRewriter(ChatClient chatClient) { this.chatClient = chatClient; }

        String rewrite(String question) {
            try {
                String rewritten = chatClient.prompt().system(SYSTEM).user(question)
                        .call().content().trim();
                return rewritten.isEmpty() ? question : rewritten;
            } catch (Exception ex) {
                System.err.println("Query rewrite failed, falling back to original: " + ex);
                return question;
            }
        }
    }

    /**
     * Post-retrieval stage: score each candidate chunk's relevance to the ORIGINAL
     * question using the LLM as a judge, and keep the top finalK.
     *
     * In a high-throughput production system, swap this for a dedicated cross-encoder
     * model for lower latency and cost -- this LLM-as-judge version needs no extra
     * dependency beyond Ollama and keeps the example self-contained. Pattern 20
     * (Re-Ranking) covers the cross-encoder version in depth.
     */
    static final class SimpleReRanker {
        private static final String SYSTEM = """
                Rate how relevant the passage is for answering the question, on a scale
                from 0 to 10. Respond with ONLY the number, nothing else.
                """;
        private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");
        private final ChatClient chatClient;

        SimpleReRanker(ChatClient chatClient) { this.chatClient = chatClient; }

        double score(String question, String passage) {
            try {
                String raw = chatClient.prompt().system(SYSTEM)
                        .user("Question: " + question + "\n\nPassage:\n" + passage)
                        .call().content();
                Matcher m = NUMBER.matcher(raw);
                return m.find() ? Double.parseDouble(m.group()) : 0.0;
            } catch (Exception ex) {
                System.err.println("Re-rank scoring failed for a passage, scoring as 0: " + ex);
                return 0.0;
            }
        }

        List<Document> rerank(String question, List<Document> candidates, int keep) {
            List<Entry<Document, Double>> scored = candidates.stream()
                    .map(d -> new SimpleEntry<>(d, score(question, d.getText())))
                    .sorted(Comparator.comparingDouble((Entry<Document, Double> e) -> e.getValue()).reversed())
                    .collect(Collectors.toList());

            System.out.println("Re-rank scores: " + scored.stream()
                    .map(e -> e.getKey().getMetadata().getOrDefault("source", "?") + "=" + e.getValue())
                    .collect(Collectors.joining(", ")));

            return scored.stream().limit(keep).map(Entry::getKey).collect(Collectors.toList());
        }
    }

    static final class AdvancedRagSupportBot {
        private static final String ANSWER_SYSTEM = """
                You are a customer support assistant for Acme SaaS. Answer using ONLY the
                context below. If the context does not contain the answer, say so explicitly.

                Context:
                {context}
                """;

        private final RagConfig config;
        private final SimpleVectorStore vectorStore;
        private final ChatClient chatClient;
        private final QueryRewriter rewriter;
        private final SimpleReRanker reranker;
        private final PromptTemplate promptTemplate;

        AdvancedRagSupportBot(RagConfig config, OllamaEmbeddingModel embeddingModel,
                               OllamaChatModel chatModel, Path sourceDir) {
            this.config = config;
            this.chatClient = ChatClient.create(chatModel);
            this.rewriter = new QueryRewriter(chatClient);
            this.reranker = new SimpleReRanker(chatClient);
            this.promptTemplate = new PromptTemplate(ANSWER_SYSTEM);
            this.vectorStore = buildOrLoad(embeddingModel, sourceDir);
        }

        private SimpleVectorStore buildOrLoad(OllamaEmbeddingModel embeddingModel, Path sourceDir) {
            SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
            if (Files.exists(config.persistPath())) {
                store.load(config.persistPath().toFile());
                return store;
            }
            List<Document> docs;
            try (var files = Files.list(sourceDir)) {
                docs = files.filter(p -> p.toString().endsWith(".md")).sorted()
                        .map(p -> {
                            try {
                                return new Document(Files.readString(p),
                                        Map.of("source", p.getFileName().toString()));
                            } catch (IOException e) { throw new UncheckedIOException(e); }
                        }).collect(Collectors.toList());
            } catch (IOException e) { throw new UncheckedIOException(e); }
            TokenTextSplitter splitter = new TokenTextSplitter(
                    config.chunkSize(), config.chunkOverlap(), 5, 10000, true);
            List<Document> chunks = splitter.apply(docs);
            store.add(chunks);
            store.save(config.persistPath().toFile());
            return store;
        }

        record AskResult(String answer, String rewrittenQuery, List<String> sources) {}

        AskResult ask(String question) {
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("Question must not be empty.");
            }

            String rewrittenQuery = rewriter.rewrite(question);
            System.out.println("Rewrote query: " + question + " -> " + rewrittenQuery);

            List<Document> candidates;
            try {
                candidates = vectorStore.similaritySearch(
                        SearchRequest.builder().query(rewrittenQuery).topK(config.wideK()).build());
            } catch (Exception ex) {
                System.err.println("Wide retrieval failed: " + ex);
                candidates = List.of();
            }

            if (candidates.isEmpty()) {
                return new AskResult("I couldn't find anything relevant to that question.",
                        rewrittenQuery, List.of());
            }

            List<Document> topChunks = reranker.rerank(question, candidates, config.finalK());

            String context = topChunks.stream()
                    .map(d -> "[" + d.getMetadata().getOrDefault("source", "unknown") + "]\n" + d.getText())
                    .collect(Collectors.joining("\n\n---\n\n"));

            String answer;
            try {
                String systemMessage = promptTemplate.render(Map.of("context", context));
                answer = chatClient.prompt().system(systemMessage).user(question).call().content();
            } catch (Exception ex) {
                System.err.println("Generation failed: " + ex);
                answer = "Sorry, something went wrong answering that question.";
            }

            List<String> sources = topChunks.stream()
                    .map(d -> String.valueOf(d.getMetadata().getOrDefault("source", "unknown")))
                    .collect(Collectors.toList());

            return new AskResult(answer, rewrittenQuery, sources);
        }
    }

    private static void writeSampleDocs(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("billing.md"), """
                ## Cancelling Your Plan
                You can cancel your plan any time from Account Settings > Billing.
                Cancellation takes effect at the end of the current billing cycle.

                ## Refund Policy
                Refund eligibility depends on cancellation timing:
                - Cancel within 7 days of purchase: full refund
                - Cancel mid-cycle after 7 days: no partial refund, access continues until cycle end
                - Annual plans cancelled within 30 days: prorated refund
                """);
        Files.writeString(dir.resolve("api_keys.md"), """
                ## API Key Retention on Downgrade
                Downgrading your plan does not delete existing API keys. Keys remain active
                but are subject to the rate limits of your new plan tier.
                """);
        Files.writeString(dir.resolve("unrelated.md"), """
                ## Setting Up Two-Factor Authentication
                Enable 2FA from Account Settings > Security using an authenticator app.
                """);
    }

    public static void main(String[] args) throws IOException {
        RagConfig config = RagConfig.defaults();
        Path sampleDir = Path.of("./sample_support_docs_v2");
        writeSampleDocs(sampleDir);

        OllamaApi ollamaApi = OllamaApi.builder().baseUrl("http://localhost:11434").build();
        OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder().model(config.embeddingModel()).build())
                .build();
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder().model(config.llmModel())
                        .temperature(config.llmTemperature()).build())
                .build();

        AdvancedRagSupportBot bot = new AdvancedRagSupportBot(config, embeddingModel, chatModel, sampleDir);

        List<String> questions = List.of(
                "terminate my subscription -- how do I do that and will I get money back?",
                "if I downgrade will I lose my api keys");

        for (String q : questions) {
            AdvancedRagSupportBot.AskResult result = bot.ask(q);
            System.out.println("\nQ: " + q);
            System.out.println("  rewritten query: " + result.rewrittenQuery());
            System.out.println("  sources used: " + result.sources());
            System.out.println("A: " + result.answer());
        }
    }
}
```

### Key production details worth noting

- **Re-ranking judges relevance against the *original* question, not the rewritten
  one.** The rewrite is a retrieval aid; final relevance should be judged by what the
  user actually asked.
- **LLM-as-re-ranker is shown for simplicity and zero extra dependencies**, but it adds
  `wideK` extra LLM calls per query — in production at scale, swap this for a
  dedicated cross-encoder model; Pattern 20 covers that version.
- **Graceful degradation:** if the rewriter throws, we fall back to the original
  question rather than failing the whole request.
- **`wideK` >> `finalK`** is the core mechanical idea of this pattern: retrieval
  optimizes for recall, re-ranking optimizes for precision — two different jobs, two
  different stages, on purpose.

---
[← Back to index](README.md)
