# Spring AI Vector Databases — Effective Notes

> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

1. [What is a vector database?](#1-what-is-a-vector-database)
2. [RAG in one picture](#2-rag-in-one-picture)
3. [VectorStoreRetriever (read-only)](#3-vectorstoreretriever-read-only)
4. [VectorStore (read + write)](#4-vectorstore-read--write)
5. [SearchRequest (topK, threshold, filter)](#5-searchrequest-topk-threshold-filter)
6. [Document, embeddings and EmbeddingModel](#6-document-embeddings-and-embeddingmodel)
7. [Schema initialization](#7-schema-initialization)
8. [Batching strategy (TokenCountBatchingStrategy)](#8-batching-strategy-tokencountbatchingstrategy)
9. [Auto-truncation with batching](#9-auto-truncation-with-batching)
10. [Spring Boot auto-config and custom batching](#10-spring-boot-auto-config-and-custom-batching)
11. [VectorStore implementations](#11-vectorstore-implementations)
12. [Writing to a vector store](#12-writing-to-a-vector-store)
13. [Reading from a vector store](#13-reading-from-a-vector-store)
14. [Separating read and write access](#14-separating-read-and-write-access)
15. [RAG with VectorStoreRetriever](#15-rag-with-vectorstoreretriever)
16. [Metadata filters](#16-metadata-filters)
17. [Partitioning a shared index](#17-partitioning-a-shared-index)
18. [Deleting documents](#18-deleting-documents)
19. [Document versioning use case](#19-document-versioning-use-case)
20. [Delete performance and error handling](#20-delete-performance-and-error-handling)
21. [Quick cheat sheet](#21-quick-cheat-sheet)
22. [Common mistakes](#22-common-mistakes)
23. [Interview quick Q&A](#23-interview-quick-qa)

---

## 1. What is a vector database?

**Key Points**
- A **vector database** stores **vectors** (arrays of numbers, called **embeddings**) and finds **similar** ones.
- Normal database: **exact match** ("name = 'Hemant'").
- Vector database: **similarity search** ("find things **like** this").
- Give it a **query vector** → it returns the **most similar vectors**.
- It is the key part of **AI apps that use your own data**.
- The vector database **only stores and searches** embeddings. It does **not create** them. The **`EmbeddingModel`** creates embeddings. (Many `VectorStore`s call the `EmbeddingModel` for you when you `add` documents.)

**Use Cases**
- Chat with company documents, semantic search ("refund rules" finds "return policy"), recommendations, duplicate detection.

**Where to Use**
- Any app that must give the AI **your private or latest data** (RAG).

**Problem Solved**
- LLMs do not know your data. A vector DB finds the **relevant pieces** so you send only those to the model (not everything).

**Simple example**
```
"How do I get my money back?"  →  finds the chunk "Refunds are allowed within 7 days..."
(No exact word match needed. Meaning is matched.)
```

---

## 2. RAG in one picture

**Key Points**
- **RAG = Retrieval Augmented Generation.**
- Steps:
  1. **Load**: put your data into the vector DB (as embeddings).
  2. **Ask**: user sends a question.
  3. **Retrieve**: find similar documents.
  4. **Augment**: add those documents to the prompt as **context**.
  5. **Generate**: the AI answers using the context.

```
Your data → [chunks] → embeddings → Vector DB
User question → embedding → similarity search → top documents
Top documents + question → LLM → answer
```

**Problem Solved**
- Fewer made-up answers, answers from **your** data, no need to retrain the model.

---

## 3. VectorStoreRetriever (read-only)

**Key Points**
- A **read-only** interface that gives only **retrieval**.
- It is a **`@FunctionalInterface`** (can be a lambda).
- Follows the **principle of least privilege**: components that only search cannot add or delete.
- Methods:
  - `List<Document> similaritySearch(SearchRequest request)`
  - `default List<Document> similaritySearch(String query)` (builds a `SearchRequest` with the query)

```java
@FunctionalInterface
public interface VectorStoreRetriever {

    List<Document> similaritySearch(SearchRequest request);

    default List<Document> similaritySearch(String query) {
        return this.similaritySearch(SearchRequest.builder().query(query).build());
    }
}
```

**Use Cases**
- RAG services, search endpoints, any read-only component.

**Problem Solved**
- Stops read-only code from accidentally modifying the store (safer and cleaner design).

**Java Example**
```java
List<Document> docs = retriever.similaritySearch("What is the refund policy?");
```

---

## 4. VectorStore (read + write)

**Key Points**
- `VectorStore` **extends** `VectorStoreRetriever` and `DocumentWriter`. Adds **write** operations.

```java
public interface VectorStore extends DocumentWriter, VectorStoreRetriever {

    default String getName() { return this.getClass().getSimpleName(); }

    void add(List<Document> documents);
    void delete(List<String> idList);
    void delete(Filter.Expression filterExpression);
    default void delete(String filterExpression) { ... }

    default <T> Optional<T> getNativeClient() { return Optional.empty(); }
}
```

| Method | Meaning |
|---|---|
| `add(List<Document>)` | Add documents (embeddings are computed) |
| `delete(List<String> ids)` | Delete by IDs |
| `delete(Filter.Expression)` | Delete by filter object |
| `delete(String)` | Delete by filter text |
| `similaritySearch(...)` | Search (from the retriever) |
| `getNativeClient()` | Access the **provider's own client** for special features (optional) |

**Use Cases**
- Indexing jobs (write), admin tools (delete), native features (via `getNativeClient`).

**Problem Solved**
- One simple API for **many** vector databases (portable).

---

## 5. SearchRequest (topK, threshold, filter)

**Key Points**
- `SearchRequest` describes **how to search**. Created with `SearchRequest.builder()`.

| Field | Meaning | Default |
|---|---|---|
| `query` | Search text | `""` |
| `topK` | **Max** documents to return (K nearest neighbors, KNN) | **4** (`DEFAULT_TOP_K`) |
| `similarityThreshold` | Minimum similarity, **0 to 1** (closer to 1 = more similar). Only documents **above** the value are returned | **0.0** (accept all) |
| `filterExpression` | Filter on **metadata** (like SQL `where`) | `null` |

- Builder rules:
  - `query` cannot be null.
  - `topK >= 0`.
  - `similarityThreshold` must be in **[0, 1]** (else error).
- Builder helpers: `similarityThresholdAll()` (= 0.0), `SearchRequest.from(original)` (copy).
- `filterExpression` has **two forms**:
  1. **`Filter.Expression`** object (fluent DSL).
  2. **String** (ANTLR4-based text), e.g. `country == 'UK' && year >= 2020 && isActive == true`.
- The filter works **only on metadata** key-value pairs of a `Document`, not on the content text.

**Use Cases**
- topK=3 for short context; threshold 0.75 to drop weak matches; filter by tenant/country/year.

**Problem Solved**
- Control quality, size, and scope of search results.

**Java Example**
```java
SearchRequest request = SearchRequest.builder()
    .query("refund rules")
    .topK(5)
    .similarityThreshold(0.7)
    .filterExpression("country == 'UK' && year >= 2020")
    .build();

List<Document> docs = vectorStore.similaritySearch(request);
```

---

## 6. Document, embeddings and EmbeddingModel

**Key Points**
- To store data, wrap it in a **`Document`**: **text content** + **metadata** (key-value pairs like filename, country, year).
- When inserted, the text is turned into a **`float[]`** called a **vector embedding** by an **embedding model**.
- Examples of embedding models: Word2Vec, GloVe, BERT, OpenAI `text-embedding-ada-002`.
- Use the **`EmbeddingModel`** (Spring AI) to create embeddings. Pick an embedding model that **matches** your higher-level AI model (e.g., OpenAI chat → `OpenAiEmbeddingModel`).
- Spring Boot starters auto-configure an `EmbeddingModel` bean.
- Important: **use the same embedding model for writing and searching**. Mixing models gives wrong results.

**Java Example**
```java
Document doc = new Document(
    "Refunds are allowed within 7 days of delivery.",
    Map.of("country", "IN", "type", "policy", "year", 2025));

vectorStore.add(List.of(doc));   // embedding computed + stored
```

---

## 7. Schema initialization

**Key Points**
- Some vector stores need their **backend schema** (tables/indexes) created before use.
- It is **NOT done by default**. You must **opt in**:
  - constructor/builder boolean argument, **or**
  - with Spring Boot: the store's **`initialize-schema`** property = `true`.
- The exact property name **depends on the store** (check that store's page).

**Where to Use**
- Dev/test: `true`. Production: usually create schema with **migration tools** (Flyway/Liquibase) and keep it `false`.

**Problem Solved**
- Avoids "table not found" errors, and avoids surprise schema changes in production.

**Java/Config Example**
```properties
# Example (name differs by store; this one is for PgVector)
spring.ai.vectorstore.pgvector.initialize-schema=true
```

---

## 8. Batching strategy (TokenCountBatchingStrategy)

**Key Points**
- Embedding models have a **max token limit per request** (context window). Embedding too many tokens in one call → **errors or truncated embeddings**.
- Spring AI **splits large document lists into smaller batches** that fit the limit.
- Benefits: avoids token-limit errors, better performance, better use of API rate limits.
- Interface: `BatchingStrategy` with one method: `List<List<Document>> batch(List<Document> documents)`.
- **Default**: **`TokenCountBatchingStrategy`**:
  - Default upper limit: OpenAI's max input tokens = **8191**.
  - **Reserve percentage** default **10%** (buffer).
  - Formula: `actualMaxInputTokenCount = originalMaxInputTokenCount * (1 - RESERVE_PERCENTAGE)`
    → `8191 * 0.9 ≈ 7371`.
  - Estimates tokens per document, groups into batches under the limit.
  - **Throws an exception if ONE document exceeds the limit.**
- Customize with: **encoding type**, **max input token count**, **reserve percentage** (and optionally content formatter + metadata mode).
- Uses `JTokkitTokenCountEstimator` (encoding `CL100K_BASE`) by default. You can pass your **own `TokenCountEstimator`**.
- Defaults for the short constructor: `Document.DEFAULT_CONTENT_FORMATTER` and `MetadataMode.NONE`.
- Max token count should be **<= your embedding model's context window**.

**Use Cases**
- Loading thousands of documents in one job.

**Problem Solved**
- "Too many tokens" failures and silently truncated embeddings.

**Java Example**
```java
@Configuration
public class EmbeddingConfig {
    @Bean
    public BatchingStrategy customTokenCountBatchingStrategy() {
        return new TokenCountBatchingStrategy(
            EncodingType.CL100K_BASE,  // tokenizer encoding
            8000,                      // max input tokens
            0.1                        // 10% reserve
        );
    }
}

// Custom estimator + full constructor
TokenCountEstimator customEstimator = new YourCustomTokenCountEstimator();
TokenCountBatchingStrategy strategy = new TokenCountBatchingStrategy(
    customEstimator,
    8000,                                   // maxInputTokenCount
    0.1,                                    // reservePercentage
    Document.DEFAULT_CONTENT_FORMATTER,
    MetadataMode.NONE
);
```
> The bean is **automatically used** by `EmbeddingModel` implementations and replaces the default.

---

## 9. Auto-truncation with batching

**Key Points**
- Some embedding models (e.g., **Vertex AI text embedding**) have **`auto_truncate`**:
  - **ON**: too-long text is **silently cut** and processing continues.
  - **OFF**: **explicit error** for too-long input.
- With auto-truncation, set the batching strategy's max token count **much higher** than the model's real limit. Reason: `TokenCountBatchingStrategy` throws `IllegalArgumentException` if one document exceeds its limit. A very high limit means that check never fails, and the **model** truncates instead.
- Example: model limit 20,000, batching limit **132,900** (artificially high).
- **Best practices**:
  - Use **at least 5–10x** the model's real limit.
  - **Monitor logs** for truncation warnings (not all models log them).
  - Think about **silent truncation** effect on embedding quality.
  - **Test** with sample documents.
  - **Document** this non-standard setup for maintainers.
- **Warning**: truncation can lose important information at the **end of long documents**. If all content must be embedded, **split documents into smaller chunks first**.

**Use Cases**
- Very long, messy documents where you accept truncation.

**Problem Solved**
- Avoids exceptions from the batching strategy when the model can handle truncation.

**Java Example**
```java
@Configuration
public class AutoTruncationEmbeddingConfig {

    @Bean
    public VertexAiTextEmbeddingModel vertexAiEmbeddingModel(
            VertexAiEmbeddingConnectionDetails connectionDetails) {

        VertexAiTextEmbeddingOptions options = VertexAiTextEmbeddingOptions.builder()
                .model(VertexAiTextEmbeddingOptions.DEFAULT_MODEL_NAME)
                .autoTruncate(true)            // enable auto-truncation
                .build();

        return new VertexAiTextEmbeddingModel(connectionDetails, options);
    }

    @Bean
    public BatchingStrategy batchingStrategy() {
        return new TokenCountBatchingStrategy(
                EncodingType.CL100K_BASE,
                132900,                        // artificially high limit
                0.1);
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                   EmbeddingModel embeddingModel,
                                   BatchingStrategy batchingStrategy) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            // other properties omitted
            .build();
    }
}
```

---

## 10. Spring Boot auto-config and custom batching

**Key Points**
- With Boot auto-configuration, **define a `BatchingStrategy` bean** to override the default. It then replaces the default for **all vector stores**.
- You can also write your **own `BatchingStrategy` implementation** (a custom class) and expose it as a bean.
- All Spring AI vector stores are configured to use **`TokenCountBatchingStrategy`** by default.

**Java Example**
```java
@Bean
public BatchingStrategy customBatchingStrategy() {
    return new TokenCountBatchingStrategy(EncodingType.CL100K_BASE, 132900, 0.1);
}

// Fully custom
@Configuration
public class EmbeddingConfig {
    @Bean
    public BatchingStrategy customBatchingStrategy() {
        return new CustomBatchingStrategy();
    }
}
```

---

## 11. VectorStore implementations

**Key Points**
- Available implementations: Azure Vector Search, Apache Cassandra, Chroma, Elasticsearch, GemFire, MariaDB, Milvus, MongoDB Atlas, Neo4j, OpenSearch, Oracle, **PgVector (PostgreSQL)**, Pinecone, Qdrant, Redis, Typesense, Weaviate, S3 Vector Store, **SimpleVectorStore**.
- **Azure Cosmos DB** is an **external module** maintained by the Azure Cosmos DB team.
- **`SimpleVectorStore`** = in-memory, **testing and demos only, NOT for production**.
- More may be added. You can request one on GitHub or contribute a PR.

| Need | Good choice (simple guide) |
|---|---|
| Already on PostgreSQL | PgVector |
| Already on Redis / MongoDB / Elasticsearch / Neo4j / Oracle | That store's vector support |
| Dedicated vector DB | Pinecone, Qdrant, Weaviate, Milvus, Chroma |
| Quick test / demo | SimpleVectorStore |

**Problem Solved**
- Same `VectorStore` API for all, so you can switch stores with small changes.

---

## 12. Writing to a vector store

**Key Points**
- Usually a **batch-like job**: load data → make `Document`s → call `vectorStore.add(...)`.
- The store computes embeddings and saves content + embedding.
- Example uses **`JsonReader`** to read chosen JSON fields, split them into pieces, and pass them on.
- Real projects also use **text splitters** (chunking) so each chunk is small and focused.

**Use Cases**
- Nightly indexing of product catalog, loading PDFs/FAQs.

**Java Example**
```java
@Autowired
VectorStore vectorStore;

void load(String sourceFile) {
    JsonReader jsonReader = new JsonReader(new FileSystemResource(sourceFile),
            "price", "name", "shortDescription", "description", "tags");
    List<Document> documents = jsonReader.get();
    this.vectorStore.add(documents);
}
```

---

## 13. Reading from a vector store

**Key Points**
- When a user asks a question → **similarity search** → documents are "**stuffed**" into the prompt as context.
- Use `VectorStore` or the more focused `VectorStoreRetriever`.
- Options: **topK** (how many) and **similarity threshold** (how close).

**Java Example**
```java
@Autowired
VectorStoreRetriever retriever;   // could also be VectorStore

String question = "<question from user>";
List<Document> similarDocuments = retriever.similaritySearch(question);

SearchRequest request = SearchRequest.builder()
    .query(question)
    .topK(5)                    // top 5 results
    .similarityThreshold(0.7)   // only similarity >= 0.7
    .build();

List<Document> filteredDocuments = retriever.similaritySearch(request);
```

---

## 14. Separating read and write access

**Key Points**
- Inject **`VectorStore`** only where you need **write** access (indexing).
- Inject **`VectorStoreRetriever`** where you only need **search**.
- Benefits: clear code, more **maintainable and secure** (mutation is limited to components that need it).

**Java Example**
```java
@Service
class DocumentIndexer {                       // write access
    private final VectorStore vectorStore;

    DocumentIndexer(VectorStore vectorStore) { this.vectorStore = vectorStore; }

    public void indexDocuments(List<Document> documents) {
        vectorStore.add(documents);
    }
}

@Service
class DocumentRetriever {                     // read-only
    private final VectorStoreRetriever retriever;

    DocumentRetriever(VectorStoreRetriever retriever) { this.retriever = retriever; }

    public List<Document> findSimilar(String query) {
        return retriever.similaritySearch(query);
    }
}
```

**Benefits of `VectorStoreRetriever`**
1. **Separation of concerns** (read vs write).
2. **Interface segregation** (clients not exposed to mutation methods).
3. **Functional interface** (lambdas/method references).
4. **Reduced dependencies**.

---

## 15. RAG with VectorStoreRetriever

**Key Points**
- Retrieval and generation are **separate steps**: `retriever` finds documents, the chat model answers.
- Build a **context string** from the documents and add it to the prompt.
- In the document's example, `Document::getContent` and `chatModel.generate(prompt)` are used. In newer Spring AI versions, the text getter is `getText()` and chat calls use `chatModel.call(...)`. Check your version.
- For production, use **`QuestionAnswerAdvisor`** (simple RAG) or **`RetrievalAugmentationAdvisor`** (modular RAG) with `ChatClient` instead of writing this by hand (see Advisors notes).

**Java Example**
```java
@Service
public class RagService {

    private final VectorStoreRetriever retriever;
    private final ChatModel chatModel;

    public RagService(VectorStoreRetriever retriever, ChatModel chatModel) {
        this.retriever = retriever;
        this.chatModel = chatModel;
    }

    public String generateResponse(String userQuery) {
        List<Document> relevantDocs = retriever.similaritySearch(userQuery);

        String context = relevantDocs.stream()
            .map(Document::getText)                  // older versions: getContent()
            .collect(Collectors.joining("\n\n"));

        String prompt = "Context information:\n" + context + "\n\nUser query: " + userQuery;
        return chatModel.call(prompt);               // older docs show generate(prompt)
    }
}

// Filtered retrieval service
public List<Document> findSimilarDocumentsWithFilters(String query, String country) {
    SearchRequest request = SearchRequest.builder()
        .query(query)
        .topK(5)
        .filterExpression("country == '" + country + "'")   // see the injection warning in Section 16
        .build();
    return retriever.similaritySearch(request);
}
```

---

## 16. Metadata filters

**Key Points**
- Filter **results by metadata**, in two ways.

### 16.1 Filter string (SQL-like)
```
"country == 'BG'"
"genre == 'drama' && year >= 2020"
"genre in ['comedy', 'documentary', 'drama']"
```

### 16.2 `Filter.Expression` with `FilterExpressionBuilder`
```java
FilterExpressionBuilder b = new FilterExpressionBuilder();
Expression expression = b.eq("country", "BG").build();
```

### Operators
| Type | Operators |
|---|---|
| Compare | `==` (EQUALS), `!=` (NE), `>` (GT), `>=` (GE), `<` (LT), `<=` (LE) |
| Math | `+` (PLUS), `-` (MINUS) |
| Combine | `AND` / `and` / `&&`, `OR` / `or` / `\|\|` |
| Set | `IN` / `in`, `NIN` / `nin` |
| Negate | `NOT` / `not` |
| Null check | `IS NULL`, `IS NOT NULL` (**not implemented in all stores yet**) |

**Java Example**
```java
Expression exp1 = b.and(b.eq("genre", "drama"), b.gte("year", 2020)).build();

Expression exp2 = b.and(
    b.in("genre", "drama", "documentary"),
    b.not(b.lt("year", 2020))
).build();

Expression exp3 = b.and(b.isNull("year")).build();
Expression exp4 = b.and(b.isNotNull("year")).build();
```

> **Security warning (not in your document)**: building filter strings by **joining user input** (like `"country == '" + country + "'"`) can allow **filter injection** (a user sends `x' || country != 'x`) and read other groups' data. **Prefer `FilterExpressionBuilder`** with values, or **validate/escape** inputs. Take tenant/group IDs from the **server-side session**, not from request text.

**Use Cases**
- Search only in one country, one year range, one document type, one tenant.

**Problem Solved**
- Narrow results to the right subset before similarity ranking.

---

## 17. Partitioning a shared index

**Key Points**
- One vector store often holds **many logical groups** (customers, projects, knowledge bases).
- `VectorStore` works over the **whole index**. A **filter** selects the group.
- Approach:
  1. **Tag every document** with group metadata **when writing** (e.g., `group = groupId`).
  2. **Apply the same filter** on searches **and** deletes.
- Practices:
  - Add the identifying metadata to **every** document at write time.
  - Apply the filter **consistently** on all searches and deletes so an operation only touches the intended group.

**Use Cases**
- Multi-tenant RAG: one index, many customers, no data mixing.

**Where to Use**
- SaaS apps with tenant-specific knowledge.

**Problem Solved**
- Keeps tenants separate without one index per tenant.

**Java Example**
```java
// Write: tag with the group
Document document = new Document(content, Map.of("group", groupId));
vectorStore.add(List.of(document));

// Search: only that group
List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
    .query(query)
    .filterExpression("group == '" + groupId + "'")
    .build());

// Delete: same group (safer builder version)
Filter.Expression scoped = new FilterExpressionBuilder().eq("group", groupId).build();
vectorStore.delete(scoped);
```
> **Safety tip**: a missed filter on even one search leaks data across groups. Put the filter in **one shared helper/service** so developers cannot forget it.

---

## 18. Deleting documents

**Key Points**
- Three ways:

| Method | When |
|---|---|
| `delete(List<String> idList)` | You know exact IDs. IDs that do not exist are **ignored**. |
| `delete(Filter.Expression)` | Delete by metadata criteria (object form) |
| `delete(String filterExpression)` | Same, using text form (converted to `Filter.Expression` internally) |

**Java Example**
```java
// By ID
Document document = new Document("The World is Big", Map.of("country", "Netherlands"));
vectorStore.add(List.of(document));
vectorStore.delete(List.of(document.getId()));

// By Filter.Expression
Document bgDocument = new Document("The World is Big", Map.of("country", "Bulgaria"));
Document nlDocument = new Document("The World is Big", Map.of("country", "Netherlands"));
vectorStore.add(List.of(bgDocument, nlDocument));

Filter.Expression filterExpression = new Filter.Expression(
    Filter.ExpressionType.EQ,
    new Filter.Key("country"),
    new Filter.Value("Bulgaria"));
vectorStore.delete(filterExpression);

// Verify
SearchRequest request = SearchRequest.builder()
    .query("World")
    .filterExpression("country == 'Bulgaria'")
    .build();
List<Document> results = vectorStore.similaritySearch(request);   // empty

// By String filter
vectorStore.delete("country == 'Bulgaria'");
```

---

## 19. Document versioning use case

**Key Points**
- Scenario: upload a **new version** of a document and remove the **old** one.
- Put `docId`, `version`, `lastUpdated` in metadata. Delete the old version by filter, then add the new version.
- Order in the document: **delete old first, then add new**.
- **Safer order (my note)**: if the add fails after deleting, you lose the document. Consider **add new first, then delete old** (or use a transaction/retry if the store supports it). With add-first you may briefly see both versions in search.

**Use Cases**
- Policy documents, manuals, product pages that change over time.

**Problem Solved**
- Avoids stale answers from old versions.

**Java Example**
```java
Document documentV1 = new Document("AI and Machine Learning Best Practices",
    Map.of("docId", "AIML-001", "version", "1.0", "lastUpdated", "2024-01-01"));
vectorStore.add(List.of(documentV1));

Document documentV2 = new Document("AI and Machine Learning Best Practices - Updated",
    Map.of("docId", "AIML-001", "version", "2.0", "lastUpdated", "2024-02-01"));

// Delete old version (object form)
Filter.Expression deleteOldVersion = new Filter.Expression(
    Filter.ExpressionType.AND,
    new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("docId"), new Filter.Value("AIML-001")),
    new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("version"), new Filter.Value("1.0")));
vectorStore.delete(deleteOldVersion);

vectorStore.add(List.of(documentV2));

// Verify only v2 exists
SearchRequest request = SearchRequest.builder()
    .query("AI and Machine Learning")
    .filterExpression("docId == 'AIML-001'")
    .build();
List<Document> results = vectorStore.similaritySearch(request);

// Same delete with string filter
vectorStore.delete("docId == 'AIML-001' AND version == '1.0'");
vectorStore.add(List.of(documentV2));
```

---

## 20. Delete performance and error handling

**Key Points**
- **Error handling**: all delete methods may throw exceptions. **Wrap deletes in try-catch** (e.g., invalid filter expression).
- **Performance**:
  - Delete by **ID list** is generally **faster** when you know the IDs.
  - **Filter-based delete** may need to **scan the index** (depends on the store).
  - **Batch large deletions** to avoid overloading the system.
  - Use **filter expressions** when deleting by properties (instead of first collecting IDs).

**Java Example**
```java
try {
    vectorStore.delete("country == 'Bulgaria'");
} catch (Exception e) {
    logger.error("Invalid filter expression", e);
}

// Batch deleting many IDs (my example)
List<String> ids = ...;                      // thousands of IDs
for (int i = 0; i < ids.size(); i += 500) {
    vectorStore.delete(ids.subList(i, Math.min(i + 500, ids.size())));
}
```

---

## 21. Quick cheat sheet

```java
// Write
vectorStore.add(List.of(new Document("text", Map.of("tenant", "acme"))));

// Search (read-only)
List<Document> docs = retriever.similaritySearch(
    SearchRequest.builder()
        .query("refund rules")
        .topK(5)
        .similarityThreshold(0.7)
        .filterExpression(new FilterExpressionBuilder().eq("tenant", "acme").build())
        .build());

// Delete
vectorStore.delete(List.of(id));
vectorStore.delete("tenant == 'acme'");
```

| Need | Use |
|---|---|
| Search only | `VectorStoreRetriever` |
| Add/delete/search | `VectorStore` |
| How many results | `topK` (default 4) |
| Drop weak matches | `similarityThreshold` (0 to 1) |
| Filter by metadata | `filterExpression` (string or `Filter.Expression`) |
| Create embeddings | `EmbeddingModel` |
| Many docs at once | `BatchingStrategy` (`TokenCountBatchingStrategy`) |
| Model truncates long text | Very high batching limit (5–10x) + chunk docs |
| Multi-tenant index | Tag with metadata + filter on search and delete |
| Replace old doc version | Filter-delete old version, add new |
| Test only | `SimpleVectorStore` |
| Create tables | `initialize-schema=true` (store-specific property) |

---

## 22. Common mistakes

1. Thinking the vector DB **creates embeddings** → the **`EmbeddingModel`** does.
2. Using **different embedding models** for writing and searching → bad results.
3. Using **`SimpleVectorStore` in production** → in-memory, testing only.
4. Forgetting **`initialize-schema`** → "table not found" (not created by default).
5. Embedding a **huge list in one call** → token limit errors. Use batching.
6. **One document bigger than the limit** → `TokenCountBatchingStrategy` throws. Split documents into chunks first.
7. Using **auto-truncation** without raising the batching limit → exception from batching. Raising it too much and ignoring truncation → lost information at the end of documents.
8. **Similarity threshold outside 0 to 1** → error. Also, threshold 0.99 may return nothing.
9. **No tenant filter** on a search or delete in a shared index → **data leak** or deleting the wrong group's data.
10. **Building filters by joining user input** → filter injection risk. Use `FilterExpressionBuilder` and server-side IDs.
11. Filtering on **content text** → filters work **only on metadata**.
12. Using `IS NULL` / `IS NOT NULL` without checking store support → **not implemented in all stores**.
13. Deleting a big set in one call → batch large deletes.
14. Using filter-delete everywhere on big indexes → may **scan the index**. Delete by IDs when you can.
15. **Delete-then-add** versioning without safety → document missing if add fails.
16. Injecting full **`VectorStore`** into read-only classes → use **`VectorStoreRetriever`**.
17. Trusting `Document::getContent` / `chatModel.generate` in older examples → API names differ by version.
18. Forgetting that **`topK` default is 4** → may be too few or too many for your prompt size.

---

## 23. Interview quick Q&A

**Q1. What is a vector database?**
A database that stores embeddings and returns the most similar ones to a query vector (similarity search), instead of exact matches.

**Q2. Does the vector database create embeddings?**
No. It stores and searches them. The `EmbeddingModel` creates embeddings (the `VectorStore` usually calls it when you `add`).

**Q3. What is RAG?**
Retrieve similar documents from the vector store, add them to the prompt as context, and let the model answer using them.

**Q4. `VectorStore` vs `VectorStoreRetriever`?**
`VectorStoreRetriever` is read-only (similarity search). `VectorStore` extends it and adds `add` and `delete`.

**Q5. Why use `VectorStoreRetriever`?**
Least privilege, interface segregation, simpler dependencies, and it is a functional interface.

**Q6. What does `SearchRequest` contain?**
`query`, `topK` (default 4), `similarityThreshold` (0 to 1, default 0), and an optional `filterExpression`.

**Q7. What does the similarity threshold do?**
Only documents with similarity above the value are returned (e.g., 0.75).

**Q8. What do metadata filters work on?**
Only the metadata key-value pairs of a `Document`.

**Q9. Two ways to write a filter?**
A SQL-like string (`country == 'UK' && year >= 2020`) or a `Filter.Expression` built with `FilterExpressionBuilder`.

**Q10. Which operators exist?**
`== != > >= < <=`, `+ -`, `AND/OR`, `IN/NIN`, `NOT`, `IS NULL / IS NOT NULL` (null checks not in all stores).

**Q11. Why is schema not initialized by default?**
It is opt-in for safety. Set the store's `initialize-schema` property (or constructor flag) to `true`.

**Q12. Why do we need batching?**
Embedding models have token limits. Batching splits documents into batches that fit, avoiding errors and truncation.

**Q13. How does `TokenCountBatchingStrategy` compute the limit?**
Default max 8191 tokens with a 10% reserve: `max * (1 - reserve)`. It throws if one document exceeds the limit.

**Q14. How to handle auto-truncating embedding models?**
Set the batching limit much higher (5–10x) than the model's real limit so the model truncates instead of the strategy throwing. Watch for lost content; chunk documents when all content matters.

**Q15. How to override the default batching strategy in Boot?**
Declare a `BatchingStrategy` bean.

**Q16. Is `SimpleVectorStore` production ready?**
No. In-memory, for tests and demos only.

**Q17. How do you share one index across tenants?**
Tag every document with group metadata when writing, and apply the same filter on every search and delete.

**Q18. How can you delete documents?**
By ID list, by `Filter.Expression`, or by string filter. Missing IDs are ignored.

**Q19. How to replace a document version?**
Delete the old version with a filter (`docId` and `version`) and add the new one. Consider add-first for safety.

**Q20. Delete performance tips?**
IDs are usually faster; filters may scan the index; batch large deletes; wrap in try-catch.

**Q21. What is `getNativeClient()` for?**
Optional access to the provider's own client for features not covered by the common API.

**Q22. Which stores are supported?**
Azure, Cassandra, Chroma, Elasticsearch, GemFire, MariaDB, Milvus, MongoDB Atlas, Neo4j, OpenSearch, Oracle, PgVector, Pinecone, Qdrant, Redis, Typesense, Weaviate, S3, SimpleVectorStore (Cosmos DB as an external module).
