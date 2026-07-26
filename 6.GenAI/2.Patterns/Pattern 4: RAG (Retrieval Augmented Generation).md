# Pattern 4: RAG (Retrieval Augmented Generation)

This is the big one — the pattern that lets an LLM answer questions about your private data without retraining it. The model doesn't know your company's docs, so before calling it, you retrieve the most relevant chunks from a vector store and stuff them into the prompt as context. The model then answers grounded in that context instead of guessing.

## What's happening

Two separate pipelines:

### Ingestion (offline)

- Load documents
- Split into chunks
- Embed each chunk into a vector
- Store in a vector database

### Query (runtime)

- Embed the user's question
- Find the most similar stored chunks
- Inject them into the prompt as context
- Call the LLM

Spring AI gives you both halves: `VectorStore` for storage/retrieval, and `QuestionAnswerAdvisor` to automate the query-time augmentation so you don't hand-write the "stuff context into prompt" logic.

---

## Setup

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>

<!-- SimpleVectorStore for dev/demo; swap for pgvector/Pinecone/Redis in production -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-advisors-vector-store</artifactId>
</dependency>
```

```java
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // In-memory, dev-friendly. For production, use
        // PgVectorStore / RedisVectorStore / etc. — same interface.
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
```

---

## Code

### 1. Ingestion service — runs once to populate the store

```java
package com.example.genai.pattern4;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ingest(Resource fileResource) {
        // 1. Load raw content (Tika handles PDF, Word, HTML, etc.)
        TikaDocumentReader reader = new TikaDocumentReader(fileResource);
        List<Document> rawDocs = reader.get();

        // 2. Split into manageable, embeddable chunks
        TokenTextSplitter splitter = new TokenTextSplitter(
                800,   // target chunk size in tokens
                350,   // min chunk size to keep
                5,     // min keyword count
                10000, // max chunks
                true   // keep separators
        );
        List<Document> chunks = splitter.apply(rawDocs);

        // 3. Embed + store — VectorStore calls the EmbeddingModel internally
        vectorStore.add(chunks);
    }
}
```

### 2. Query controller — the easy way, using `QuestionAnswerAdvisor`

```java
package com.example.genai.pattern4;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final ChatClient chatClient;

    public RagController(ChatClient.Builder builder, VectorStore vectorStore) {
        var qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(4)              // retrieve top 4 most similar chunks
                        .similarityThreshold(0.75)
                        .build())
                .build();

        this.chatClient = builder
                .defaultAdvisors(qaAdvisor)   // auto-injects retrieved context every call
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

`QuestionAnswerAdvisor` does the embed-query → similarity-search → prompt-augmentation steps automatically before every call — that's the whole RAG loop in about 10 lines.

### 3. The manual version

Useful when you want control over the augmentation prompt (e.g. citing sources).

```java
@GetMapping("/ask-manual")
public String askManual(@RequestParam String question) {

    var results = vectorStore.similaritySearch(
            SearchRequest.builder().query(question).topK(4).build());

    String context = results.stream()
            .map(Document::getText)
            .collect(java.util.stream.Collectors.joining("\n---\n"));

    String prompt = """
            Answer the question using ONLY the context below.
            If the answer isn't in the context, say you don't know.

            Context:
            %s

            Question: %s
            """.formatted(context, question);

    return chatClient.prompt(prompt).call().content();
}
```

---

## Try it

```bash
curl -X POST "http://localhost:8080/api/ingest" -F "file=@company-handbook.pdf"

curl "http://localhost:8080/api/rag/ask?question=What%20is%20our%20parental%20leave%20policy%3F"
```

---

## Production notes

- Chunk size matters a lot: too small loses context, too large dilutes relevance and burns tokens. 500–1000 tokens with slight overlap is a common starting point — tune against your own eval set.
- `SimpleVectorStore` is in-memory only — fine for prototypes, but use `PgVectorStore`, `Redis`, `Pinecone`, or similar for anything persistent or at scale. Swapping is a one-line `@Bean` change since they all implement `VectorStore`.
- `similarityThreshold` filters out weakly-related chunks instead of always forcing in the top K — important for "I don't know" honesty instead of hallucinated answers from irrelevant context.
- This is "naive RAG." Advanced variants — re-ranking, hybrid search, query rewriting, multi-hop retrieval — build on this same foundation and are worth their own deep dive once the basics click.