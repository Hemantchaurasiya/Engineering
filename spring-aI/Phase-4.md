# Phase 4: Embeddings and Vector Operations - Complete Guide

## 4.1 Understanding Embeddings

### Vector Embeddings Concepts

**What are Embeddings?**
Embeddings are numerical representations of text, images, or other data in high-dimensional space. Think of them as coordinates that capture semantic meaning - similar concepts cluster together in this space.

**Key Properties:**
- **Dimensionality**: Typically 1536 dimensions for OpenAI's text-embedding-ada-002
- **Semantic Similarity**: Similar meanings have similar vectors
- **Mathematical Operations**: Can perform arithmetic on embeddings

### Distance Metrics Deep Dive

```java
@Component
public class VectorSimilarityCalculator {
    
    /**
     * Cosine Similarity - Most common for text embeddings
     * Range: [-1, 1] where 1 = identical, 0 = orthogonal, -1 = opposite
     */
    public double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Euclidean Distance - Geometric distance in n-dimensional space
     * Lower values indicate higher similarity
     */
    public double euclideanDistance(float[] vectorA, float[] vectorB) {
        double sum = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            sum += Math.pow(vectorA[i] - vectorB[i], 2);
        }
        return Math.sqrt(sum);
    }
    
    /**
     * Dot Product - Simple multiplication and sum
     * Higher values indicate higher similarity
     */
    public double dotProduct(float[] vectorA, float[] vectorB) {
        double result = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            result += vectorA[i] * vectorB[i];
        }
        return result;
    }
}
```

### Real-World Use Cases for Embeddings

```java
@Service
public class EmbeddingUseCase {
    
    // 1. Content Recommendation
    public List<Article> findSimilarArticles(String articleContent, List<Article> allArticles) {
        float[] queryEmbedding = embeddingModel.embed(articleContent).getResult().getOutput();
        
        return allArticles.stream()
            .map(article -> {
                float[] articleEmbedding = embeddingModel.embed(article.getContent()).getResult().getOutput();
                double similarity = calculateCosineSimilarity(queryEmbedding, articleEmbedding);
                return new SimilarityScore(article, similarity);
            })
            .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
            .limit(5)
            .map(SimilarityScore::getArticle)
            .collect(Collectors.toList());
    }
    
    // 2. Semantic Search
    public List<Product> semanticProductSearch(String query, List<Product> products) {
        float[] queryVector = embeddingModel.embed(query).getResult().getOutput();
        
        return products.stream()
            .filter(product -> {
                String productText = product.getName() + " " + product.getDescription();
                float[] productVector = embeddingModel.embed(productText).getResult().getOutput();
                return calculateCosineSimilarity(queryVector, productVector) > 0.7; // Similarity threshold
            })
            .collect(Collectors.toList());
    }
    
    // 3. Content Clustering
    public Map<String, List<Document>> clusterDocuments(List<Document> documents) {
        Map<Document, float[]> docEmbeddings = documents.stream()
            .collect(Collectors.toMap(
                doc -> doc,
                doc -> embeddingModel.embed(doc.getContent()).getResult().getOutput()
            ));
        
        // Simple clustering based on similarity threshold
        Map<String, List<Document>> clusters = new HashMap<>();
        int clusterIndex = 0;
        
        for (Document doc : documents) {
            boolean assigned = false;
            for (Map.Entry<String, List<Document>> cluster : clusters.entrySet()) {
                Document representative = cluster.getValue().get(0);
                double similarity = calculateCosineSimilarity(
                    docEmbeddings.get(doc), 
                    docEmbeddings.get(representative)
                );
                
                if (similarity > 0.8) { // High similarity threshold for clustering
                    cluster.getValue().add(doc);
                    assigned = true;
                    break;
                }
            }
            
            if (!assigned) {
                clusters.put("Cluster_" + clusterIndex++, new ArrayList<>(List.of(doc)));
            }
        }
        
        return clusters;
    }
}
```

## 4.2 Embedding Model Integration

### Complete Spring AI Configuration

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-ada-002
          # or text-embedding-3-small, text-embedding-3-large
    azure:
      openai:
        endpoint: ${AZURE_OPENAI_ENDPOINT}
        api-key: ${AZURE_OPENAI_API_KEY}
        embedding:
          options:
            deployment-name: text-embedding-ada-002
    vertex:
      ai:
        project-id: ${GOOGLE_CLOUD_PROJECT_ID}
        location: us-central1
        embedding:
          model: textembedding-gecko@003

# Custom embedding configurations
app:
  embedding:
    cache:
      enabled: true
      ttl: 3600 # Cache embeddings for 1 hour
    batch-size: 100
    retry:
      max-attempts: 3
      backoff-delay: 1000
```

### Multi-Provider Embedding Service

```java
@Configuration
public class EmbeddingConfiguration {
    
    @Bean
    @Primary
    @ConditionalOnProperty(value = "spring.ai.openai.api-key")
    public EmbeddingModel openAiEmbeddingModel(OpenAiEmbeddingProperties properties) {
        return new OpenAiEmbeddingModel(
            new OpenAiApi(properties.getApiKey()),
            MetadataMode.EMBED,
            OpenAiEmbeddingOptions.builder()
                .withModel("text-embedding-ada-002")
                .build()
        );
    }
    
    @Bean
    @ConditionalOnProperty(value = "spring.ai.azure.openai.endpoint")
    public EmbeddingModel azureOpenAiEmbeddingModel(AzureOpenAiEmbeddingProperties properties) {
        return new AzureOpenAiEmbeddingModel(
            new AzureOpenAiApi(properties.getEndpoint(), properties.getApiKey()),
            MetadataMode.EMBED,
            AzureOpenAiEmbeddingOptions.builder()
                .withDeploymentName(properties.getEmbedding().getOptions().getDeploymentName())
                .build()
        );
    }
    
    @Bean
    @ConditionalOnProperty(value = "spring.ai.vertex.ai.project-id")
    public EmbeddingModel vertexAiEmbeddingModel(VertexAiEmbeddingProperties properties) {
        return new VertexAiEmbeddingModel(
            VertexAI.newBuilder()
                .setProjectId(properties.getProjectId())
                .setLocation(properties.getLocation())
                .build(),
            VertexAiEmbeddingOptions.builder()
                .withModel(properties.getEmbedding().getModel())
                .build()
        );
    }
}
```

### Advanced Embedding Service with Caching

```java
@Service
public class AdvancedEmbeddingService {
    
    private final EmbeddingModel embeddingModel;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    
    @Value("${app.embedding.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${app.embedding.cache.ttl:3600}")
    private long cacheTtl;
    
    @Value("${app.embedding.batch-size:100}")
    private int batchSize;
    
    public AdvancedEmbeddingService(EmbeddingModel embeddingModel, 
                                 RedisTemplate<String, Object> redisTemplate,
                                 MeterRegistry meterRegistry) {
        this.embeddingModel = embeddingModel;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Get embedding with caching support
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public EmbeddingResponse getEmbedding(String text) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            if (cacheEnabled) {
                String cacheKey = "embedding:" + DigestUtils.sha256Hex(text);
                EmbeddingResponse cached = (EmbeddingResponse) redisTemplate.opsForValue().get(cacheKey);
                
                if (cached != null) {
                    meterRegistry.counter("embedding.cache.hit").increment();
                    return cached;
                }
                
                EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
                redisTemplate.opsForValue().set(cacheKey, response, Duration.ofSeconds(cacheTtl));
                meterRegistry.counter("embedding.cache.miss").increment();
                
                return response;
            }
            
            return embeddingModel.embedForResponse(List.of(text));
            
        } finally {
            sample.stop(Timer.builder("embedding.request.duration").register(meterRegistry));
        }
    }
    
    /**
     * Batch embedding processing with rate limiting
     */
    @Async
    public CompletableFuture<List<EmbeddingResponse>> getBatchEmbeddings(List<String> texts) {
        List<CompletableFuture<EmbeddingResponse>> futures = new ArrayList<>();
        
        // Process in batches to avoid rate limits
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            
            CompletableFuture<EmbeddingResponse> batchFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    // Add delay between batches to respect rate limits
                    if (i > 0) {
                        Thread.sleep(1000); // 1 second delay between batches
                    }
                    return embeddingModel.embedForResponse(batch);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Batch processing interrupted", e);
                }
            });
            
            futures.add(batchFuture);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }
    
    /**
     * Find most similar embeddings using efficient search
     */
    public List<SimilarityResult> findMostSimilar(String queryText, 
                                                List<String> candidateTexts, 
                                                int topK, 
                                                double threshold) {
        EmbeddingResponse queryResponse = getEmbedding(queryText);
        float[] queryVector = queryResponse.getResult().getOutput();
        
        return candidateTexts.parallelStream()
            .map(candidate -> {
                EmbeddingResponse candidateResponse = getEmbedding(candidate);
                float[] candidateVector = candidateResponse.getResult().getOutput();
                double similarity = calculateCosineSimilarity(queryVector, candidateVector);
                return new SimilarityResult(candidate, similarity);
            })
            .filter(result -> result.similarity() >= threshold)
            .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
            .limit(topK)
            .collect(Collectors.toList());
    }
    
    private double calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    public record SimilarityResult(String text, double similarity) {}
}
```

### Custom Embedding Model Integration

```java
@Component
public class HuggingFaceEmbeddingModel implements EmbeddingModel {
    
    private final WebClient webClient;
    private final String apiKey;
    private final String modelName;
    
    public HuggingFaceEmbeddingModel(@Value("${huggingface.api-key}") String apiKey,
                                   @Value("${huggingface.model:sentence-transformers/all-MiniLM-L6-v2}") String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.webClient = WebClient.builder()
            .baseUrl("https://api-inference.huggingface.co/pipeline/feature-extraction/")
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
    }
    
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions().stream()
            .map(Document::getContent)
            .collect(Collectors.toList());
        
        try {
            HuggingFaceRequest hfRequest = new HuggingFaceRequest(inputs, true, true);
            
            float[][] embeddings = webClient.post()
                .uri(modelName)
                .bodyValue(hfRequest)
                .retrieve()
                .bodyToMono(float[][].class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            List<Embedding> embeddingList = new ArrayList<>();
            for (int i = 0; i < embeddings.length; i++) {
                embeddingList.add(new Embedding(embeddings[i], i));
            }
            
            return new EmbeddingResponse(embeddingList);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get embeddings from Hugging Face", e);
        }
    }
    
    @Override
    public EmbeddingResponse call(EmbeddingRequest request, EmbeddingOptions options) {
        // Hugging Face doesn't support additional options in this simple implementation
        return call(request);
    }
    
    record HuggingFaceRequest(List<String> inputs, boolean options_wait_for_model, boolean options_use_cache) {}
}
```

## 4.3 Vector Database Integration

### Complete Pinecone Integration

```java
@Configuration
@ConditionalOnProperty(value = "pinecone.api-key")
public class PineconeConfiguration {
    
    @Bean
    public PineconeVectorStore pineconeVectorStore(
            @Value("${pinecone.api-key}") String apiKey,
            @Value("${pinecone.environment}") String environment,
            @Value("${pinecone.index-name}") String indexName,
            EmbeddingModel embeddingModel) {
        
        return new PineconeVectorStore(
            PineconeVectorStoreConfig.builder()
                .withApiKey(apiKey)
                .withEnvironment(environment)
                .withIndexName(indexName)
                .withMetricType(PineconeVectorStoreConfig.MetricType.COSINE)
                .withTopK(10)
                .build(),
            embeddingModel
        );
    }
}

@Service
public class PineconeVectorService {
    
    private final PineconeVectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    
    public PineconeVectorService(PineconeVectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }
    
    /**
     * Advanced document ingestion with metadata
     */
    public void ingestDocuments(List<Document> documents) {
        // Enrich documents with metadata
        List<Document> enrichedDocs = documents.stream()
            .map(doc -> {
                Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                metadata.put("ingestion_timestamp", Instant.now().toString());
                metadata.put("content_length", doc.getContent().length());
                metadata.put("content_hash", DigestUtils.sha256Hex(doc.getContent()));
                
                return new Document(doc.getContent(), metadata);
            })
            .collect(Collectors.toList());
        
        // Batch insert for better performance
        vectorStore.add(enrichedDocs);
    }
    
    /**
     * Advanced similarity search with filters
     */
    public List<Document> advancedSearch(String query, SearchFilters filters) {
        Map<String, Object> filterMetadata = new HashMap<>();
        
        if (filters.getDateRange() != null) {
            filterMetadata.put("created_after", filters.getDateRange().getStart().toString());
            filterMetadata.put("created_before", filters.getDateRange().getEnd().toString());
        }
        
        if (filters.getContentType() != null) {
            filterMetadata.put("content_type", filters.getContentType());
        }
        
        if (filters.getSource() != null) {
            filterMetadata.put("source", filters.getSource());
        }
        
        return vectorStore.similaritySearch(
            SearchRequest.query(query)
                .withTopK(filters.getTopK())
                .withSimilarityThreshold(filters.getSimilarityThreshold())
                .withFilterExpression(buildFilterExpression(filterMetadata))
        );
    }
    
    /**
     * Hybrid search combining vector and metadata filtering
     */
    public SearchResults hybridSearch(HybridSearchRequest request) {
        // First, perform vector search
        List<Document> vectorResults = vectorStore.similaritySearch(
            SearchRequest.query(request.getQuery())
                .withTopK(request.getTopK() * 2) // Get more results for re-ranking
                .withSimilarityThreshold(request.getVectorThreshold())
        );
        
        // Then apply additional business logic filtering
        List<Document> filteredResults = vectorResults.stream()
            .filter(doc -> applyBusinessRules(doc, request.getBusinessFilters()))
            .collect(Collectors.toList());
        
        // Re-rank based on combined score
        List<ScoredDocument> rerankedResults = rerank(filteredResults, request);
        
        return new SearchResults(
            rerankedResults.stream().limit(request.getTopK()).collect(Collectors.toList()),
            vectorResults.size(),
            filteredResults.size()
        );
    }
    
    private List<ScoredDocument> rerank(List<Document> documents, HybridSearchRequest request) {
        return documents.stream()
            .map(doc -> {
                double vectorScore = calculateVectorScore(doc, request.getQuery());
                double metadataScore = calculateMetadataScore(doc, request.getMetadataWeights());
                double businessScore = calculateBusinessScore(doc, request.getBusinessFilters());
                
                double combinedScore = 
                    vectorScore * request.getVectorWeight() +
                    metadataScore * request.getMetadataWeight() +
                    businessScore * request.getBusinessWeight();
                
                return new ScoredDocument(doc, combinedScore);
            })
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .collect(Collectors.toList());
    }
    
    public record ScoredDocument(Document document, double score) {}
    public record SearchResults(List<ScoredDocument> results, int totalVectorMatches, int totalFilteredMatches) {}
}
```

### PostgreSQL with pgvector Integration

```sql
-- Database setup
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    embedding VECTOR(1536),  -- OpenAI embedding dimension
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for efficient similarity search
CREATE INDEX ON documents USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX ON documents USING gin (metadata);
CREATE INDEX ON documents (created_at);
```

```java
@Configuration
@ConditionalOnProperty(value = "app.vector-store.type", havingValue = "postgresql")
public class PostgreSQLVectorConfiguration {
    
    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return new PgVectorStore(
            jdbcTemplate,
            embeddingModel,
            PgVectorStore.PgVectorStoreConfig.builder()
                .withSchemaName("public")
                .withVectorTableName("documents")
                .withMetadataTableName("document_metadata")
                .withDimensions(1536)
                .withDistanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .withRemoveExistingVectorStoreTable(false)
                .build()
        );
    }
}

@Repository
public class AdvancedPgVectorRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    
    public AdvancedPgVectorRepository(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }
    
    /**
     * Advanced similarity search with SQL optimization
     */
    public List<DocumentWithScore> findSimilarDocuments(String query, 
                                                       int limit, 
                                                       double threshold,
                                                       Map<String, Object> metadataFilters) {
        EmbeddingResponse queryEmbedding = embeddingModel.embedForResponse(List.of(query));
        float[] vector = queryEmbedding.getResult().getOutput();
        
        StringBuilder sql = new StringBuilder("""
            SELECT id, content, metadata, 
                   (1 - (embedding <=> ?::vector)) as similarity_score
            FROM documents 
            WHERE (1 - (embedding <=> ?::vector)) > ?
            """);
        
        List<Object> params = new ArrayList<>();
        params.add(Arrays.toString(vector));
        params.add(Arrays.toString(vector));
        params.add(threshold);
        
        // Add metadata filters
        if (metadataFilters != null && !metadataFilters.isEmpty()) {
            for (Map.Entry<String, Object> filter : metadataFilters.entrySet()) {
                sql.append(" AND metadata->>'").append(filter.getKey()).append("' = ?");
                params.add(filter.getValue().toString());
            }
        }
        
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        params.add(Arrays.toString(vector));
        params.add(limit);
        
        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> 
            new DocumentWithScore(
                rs.getLong("id"),
                rs.getString("content"),
                parseMetadata(rs.getString("metadata")),
                rs.getDouble("similarity_score")
            )
        );
    }
    
    /**
     * Batch upsert with conflict resolution
     */
    @Transactional
    public void batchUpsert(List<DocumentEmbedding> documents) {
        String sql = """
            INSERT INTO documents (content, embedding, metadata) 
            VALUES (?, ?::vector, ?::jsonb)
            ON CONFLICT (content_hash) 
            DO UPDATE SET 
                embedding = EXCLUDED.embedding,
                metadata = EXCLUDED.metadata,
                updated_at = CURRENT_TIMESTAMP
            """;
        
        List<Object[]> batchArgs = documents.stream()
            .map(doc -> new Object[]{
                doc.content(),
                Arrays.toString(doc.embedding()),
                objectMapper.writeValueAsString(doc.metadata())
            })
            .collect(Collectors.toList());
        
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }
    
    /**
     * Get vector store statistics
     */
    public VectorStoreStats getStatistics() {
        String sql = """
            SELECT 
                COUNT(*) as total_documents,
                AVG(array_length(string_to_array(trim(both '[]' from embedding::text), ','), 1)) as avg_dimensions,
                MIN(created_at) as oldest_document,
                MAX(created_at) as newest_document
            FROM documents
            """;
        
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> 
            new VectorStoreStats(
                rs.getLong("total_documents"),
                rs.getDouble("avg_dimensions"),
                rs.getTimestamp("oldest_document").toLocalDateTime(),
                rs.getTimestamp("newest_document").toLocalDateTime()
            )
        );
    }
    
    public record DocumentWithScore(Long id, String content, Map<String, Object> metadata, double score) {}
    public record DocumentEmbedding(String content, float[] embedding, Map<String, Object> metadata) {}
    public record VectorStoreStats(long totalDocuments, double avgDimensions, LocalDateTime oldest, LocalDateTime newest) {}
}
```

## 4.4 Document Processing

### Advanced Document Loaders

```java
@Service
public class UniversalDocumentLoader {
    
    private final Map<String, DocumentLoader> loaders;
    
    public UniversalDocumentLoader() {
        this.loaders = Map.of(
            ".pdf", new PdfDocumentLoader(),
            ".docx", new WordDocumentLoader(),
            ".txt", new TextDocumentLoader(),
            ".html", new HtmlDocumentLoader(),
            ".md", new MarkdownDocumentLoader(),
            ".json", new JsonDocumentLoader(),
            ".csv", new CsvDocumentLoader()
        );
    }
    
    public List<Document> loadDocument(String filePath) throws IOException {
        String extension = getFileExtension(filePath);
        DocumentLoader loader = loaders.get(extension.toLowerCase());
        
        if (loader == null) {
            throw new UnsupportedOperationException("Unsupported file type: " + extension);
        }
        
        return loader.load(filePath);
    }
    
    public List<Document> loadDocuments(List<String> filePaths) {
        return filePaths.parallelStream()
            .flatMap(path -> {
                try {
                    return loadDocument(path).stream();
                } catch (IOException e) {
                    log.error("Failed to load document: {}", path, e);
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());
    }
}

@Component
public class AdvancedPdfLoader implements DocumentLoader {
    
    @Override
    public List<Document> load(String filePath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            List<Document> documents = new ArrayList<>();
            
            // Extract text page by page for better chunking
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                pdfStripper.setStartPage(page);
                pdfStripper.setEndPage(page);
                
                String pageText = pdfStripper.getText(document);
                
                if (!pageText.trim().isEmpty()) {
                    Map<String, Object> metadata = createMetadata(filePath, page, document);
                    documents.add(new Document(pageText, metadata));
                }
            }
            
            return documents;
        }
    }
    
    private Map<String, Object> createMetadata(String filePath, int pageNumber, PDDocument document) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", filePath);
        metadata.put("page_number", pageNumber);
        metadata.put("total_pages", document.getNumberOfPages());
        metadata.put("document_type", "pdf");
        metadata.put("file_size", new File(filePath).length());
        
        // Extract PDF metadata
        PDDocumentInformation info = document.getDocumentInformation();
        if (info != null) {
            metadata.put("title", info.getTitle());
            metadata.put("author", info.getAuthor());
            metadata.put("subject", info.getSubject());
            metadata.put("creation_date", info.getCreationDate());
        }
        
        return metadata;
    }
}
```

### Intelligent Text Splitting

```java
@Service
public class IntelligentTextSplitter {
    
    private final EmbeddingModel embeddingModel;
    
    @Value("${app.text-splitter.chunk-size:1000}")
    private int defaultChunkSize;
    
    @Value("${app.text-splitter.chunk-overlap:100}")
    private int defaultOverlap;
    
    @Value("${app.text-splitter.semantic-threshold:0.8}")
    private double semanticThreshold;
    
    public IntelligentTextSplitter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    /**
     * Semantic chunking based on embedding similarity
     */
    public List<Document> semanticSplit(String text, Map<String, Object> metadata) {
        // First, split by natural boundaries (paragraphs, sentences)
        List<String> sentences = splitIntoSentences(text);
        
        if (sentences.size() <= 1) {
            return List.of(new Document(text, metadata));
        }
        
        // Calculate embeddings for each sentence
        Map<String, float[]> sentenceEmbeddings = sentences.parallelStream()
            .collect(Collectors.toConcurrentMap(
                sentence -> sentence,
                sentence -> embeddingModel.embed(sentence).getResult().getOutput()
            ));
        
        List<Document> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        float[] currentChunkEmbedding = null;
        
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            float[] sentenceEmbedding = sentenceEmbeddings.get(sentence);
            
            // Check if we should start a new chunk
            if (currentChunkEmbedding != null) {
                double similarity = calculateCosineSimilarity(currentChunkEmbedding, sentenceEmbedding);
                
                if (similarity < semanticThreshold || 
                    currentChunk.length() + sentence.length() > defaultChunkSize) {
                    
                    // Finalize current chunk
                    finalizeChunk(chunks, currentChunk.toString(), metadata);
                    currentChunk = new StringBuilder();
                    currentChunkEmbedding = null;
                }
            }
            
            // Add sentence to current chunk
            if (currentChunk.length() > 0) {
                currentChunk.append(" ");
            }
            currentChunk.append(sentence);
            
            // Update chunk embedding (weighted average)
            if (currentChunkEmbedding == null) {
                currentChunkEmbedding = Arrays.copyOf(sentenceEmbedding, sentenceEmbedding.length);
            } else {
                for (int j = 0; j < currentChunkEmbedding.length; j++) {
                    currentChunkEmbedding[j] = (currentChunkEmbedding[j] + sentenceEmbedding[j]) / 2.0f;
                }
            }
        }
        
        // Don't forget the last chunk
        if (currentChunk.length() > 0) {
            finalizeChunk(chunks, currentChunk.toString(), metadata);
        }
        
        return chunks;
    }
    
    /**
     * Recursive character splitting with overlap
     */
    public List<Document> recursiveCharacterSplit(String text, Map<String, Object> metadata) {
        List<String> separators = Arrays.asList("\n\n", "\n", " ", "");
        return recursiveCharacterSplitHelper(text, separators, 0, metadata);
    }
    
    private List<Document> recursiveCharacterSplitHelper(String text, List<String> separators, 
                                                        int separatorIndex, Map<String, Object> metadata) {
        if (text.length() <= defaultChunkSize) {
            return List.of(new Document(text, metadata));
        }
        
        if (separatorIndex >= separators.size()) {
            // Fallback to character-level splitting
            return characterLevelSplit(text, metadata);
        }
        
        String separator = separators.get(separatorIndex);
        List<String> splits = Arrays.asList(text.split(Pattern.quote(separator)));
        
        List<Document> documents = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        
        for (String split : splits) {
            String potentialChunk = currentChunk.length() > 0 ? 
                currentChunk + separator + split : split;
            
            if (potentialChunk.length() <= defaultChunkSize) {
                if (currentChunk.length() > 0) {
                    currentChunk.append(separator);
                }
                currentChunk.append(split);
            } else {
                if (currentChunk.length() > 0) {
                    documents.add(new Document(currentChunk.toString(), metadata));
                    
                    // Add overlap from the end of the current chunk
                    String overlap = getOverlapText(currentChunk.toString());
                    currentChunk = new StringBuilder(overlap);
                }
                
                // If the split is still too long, recursively split it
                if (split.length() > defaultChunkSize) {
                    documents.addAll(recursiveCharacterSplitHelper(split, separators, 
                        separatorIndex + 1, metadata));
                } else {
                    if (currentChunk.length() > 0 && !currentChunk.toString().equals(overlap)) {
                        currentChunk.append(separator);
                    }
                    currentChunk.append(split);
                }
            }
        }
        
        if (currentChunk.length() > 0) {
            documents.add(new Document(currentChunk.toString(), metadata));
        }
        
        return documents;
    }
    
    /**
     * Token-aware splitting for LLM compatibility
     */
    public List<Document> tokenAwareSplit(String text, Map<String, Object> metadata, int maxTokens) {
        List<Document> documents = new ArrayList<>();
        String[] sentences = text.split("(?<=\\.|\\!|\\?|\\n)\\s+");
        
        StringBuilder currentChunk = new StringBuilder();
        int currentTokenCount = 0;
        
        for (String sentence : sentences) {
            int sentenceTokens = estimateTokenCount(sentence);
            
            if (currentTokenCount + sentenceTokens > maxTokens && currentChunk.length() > 0) {
                // Create document with token count metadata
                Map<String, Object> chunkMetadata = new HashMap<>(metadata);
                chunkMetadata.put("token_count", currentTokenCount);
                chunkMetadata.put("character_count", currentChunk.length());
                
                documents.add(new Document(currentChunk.toString().trim(), chunkMetadata));
                
                // Start new chunk with overlap
                String overlap = getTokenAwareOverlap(currentChunk.toString(), maxTokens / 10);
                currentChunk = new StringBuilder(overlap);
                currentTokenCount = estimateTokenCount(overlap);
            }
            
            if (currentChunk.length() > 0) {
                currentChunk.append(" ");
            }
            currentChunk.append(sentence);
            currentTokenCount += sentenceTokens;
        }
        
        if (currentChunk.length() > 0) {
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            chunkMetadata.put("token_count", currentTokenCount);
            chunkMetadata.put("character_count", currentChunk.length());
            documents.add(new Document(currentChunk.toString().trim(), chunkMetadata));
        }
        
        return documents;
    }
    
    private List<String> splitIntoSentences(String text) {
        // Use OpenNLP or similar for better sentence detection
        return Arrays.stream(text.split("(?<=[.!?])\\s+"))
            .filter(s -> !s.trim().isEmpty())
            .collect(Collectors.toList());
    }
    
    private void finalizeChunk(List<Document> chunks, String chunkText, Map<String, Object> originalMetadata) {
        Map<String, Object> chunkMetadata = new HashMap<>(originalMetadata);
        chunkMetadata.put("chunk_index", chunks.size());
        chunkMetadata.put("chunk_size", chunkText.length());
        chunks.add(new Document(chunkText, chunkMetadata));
    }
    
    private String getOverlapText(String text) {
        if (text.length() <= defaultOverlap) {
            return text;
        }
        return text.substring(text.length() - defaultOverlap);
    }
    
    private String getTokenAwareOverlap(String text, int maxOverlapTokens) {
        String[] words = text.split("\\s+");
        StringBuilder overlap = new StringBuilder();
        
        for (int i = Math.max(0, words.length - maxOverlapTokens); i < words.length; i++) {
            if (overlap.length() > 0) {
                overlap.append(" ");
            }
            overlap.append(words[i]);
        }
        
        return overlap.toString();
    }
    
    private int estimateTokenCount(String text) {
        // Rough estimation: 1 token ≈ 4 characters for English
        return Math.max(1, text.length() / 4);
    }
    
    private List<Document> characterLevelSplit(String text, Map<String, Object> metadata) {
        List<Document> documents = new ArrayList<>();
        
        for (int i = 0; i < text.length(); i += defaultChunkSize - defaultOverlap) {
            int end = Math.min(i + defaultChunkSize, text.length());
            String chunk = text.substring(i, end);
            
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            chunkMetadata.put("chunk_method", "character_level");
            chunkMetadata.put("start_position", i);
            chunkMetadata.put("end_position", end);
            
            documents.add(new Document(chunk, chunkMetadata));
        }
        
        return documents;
    }
    
    private double calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

### Web Content Scraping with Embedding

```java
@Service
public class WebContentEmbeddingService {
    
    private final WebClient webClient;
    private final EmbeddingModel embeddingModel;
    private final IntelligentTextSplitter textSplitter;
    
    public WebContentEmbeddingService(EmbeddingModel embeddingModel, 
                                    IntelligentTextSplitter textSplitter) {
        this.embeddingModel = embeddingModel;
        this.textSplitter = textSplitter;
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    }
    
    /**
     * Scrape and process web content
     */
    public List<Document> scrapeAndEmbed(String url) {
        try {
            String html = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            // Parse HTML and extract content
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            
            // Remove unwanted elements
            doc.select("script, style, nav, footer, aside, .advertisement").remove();
            
            String title = doc.title();
            String mainContent = doc.select("main, article, .content, #content").text();
            
            if (mainContent.isEmpty()) {
                mainContent = doc.body().text();
            }
            
            Map<String, Object> metadata = createWebMetadata(url, doc);
            
            // Split content intelligently
            return textSplitter.semanticSplit(mainContent, metadata);
            
        } catch (Exception e) {
            log.error("Failed to scrape content from {}", url, e);
            return List.of();
        }
    }
    
    /**
     * Batch process multiple URLs
     */
    @Async
    public CompletableFuture<List<Document>> batchScrapeAndEmbed(List<String> urls) {
        List<CompletableFuture<List<Document>>> futures = urls.stream()
            .map(url -> CompletableFuture.supplyAsync(() -> {
                try {
                    // Add delay to avoid overwhelming servers
                    Thread.sleep(1000);
                    return scrapeAndEmbed(url);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return List.<Document>of();
                }
            }))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .flatMap(future -> future.join().stream())
                .collect(Collectors.toList()));
    }
    
    private Map<String, Object> createWebMetadata(String url, org.jsoup.nodes.Document doc) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", url);
        metadata.put("source_type", "web");
        metadata.put("title", doc.title());
        metadata.put("scraped_at", Instant.now().toString());
        
        // Extract meta tags
        Elements metaTags = doc.select("meta");
        for (org.jsoup.nodes.Element meta : metaTags) {
            String name = meta.attr("name");
            String property = meta.attr("property");
            String content = meta.attr("content");
            
            if (!name.isEmpty() && !content.isEmpty()) {
                metadata.put("meta_" + name, content);
            } else if (!property.isEmpty() && !content.isEmpty()) {
                metadata.put("meta_" + property, content);
            }
        }
        
        return metadata;
    }
}
```

## Real-World Implementation Examples

### E-commerce Product Recommendation System

```java
@Service
public class ProductRecommendationService {
    
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    
    public ProductRecommendationService(EmbeddingModel embeddingModel, VectorStore vectorStore) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }
    
    /**
     * Index all products with their embeddings
     */
    @PostConstruct
    public void indexProducts() {
        List<Product> products = productRepository.findAll();
        
        List<Document> productDocuments = products.stream()
            .map(this::convertProductToDocument)
            .collect(Collectors.toList());
        
        vectorStore.add(productDocuments);
        log.info("Indexed {} products", products.size());
    }
    
    /**
     * Find similar products based on description
     */
    public List<Product> findSimilarProducts(Long productId, int limit) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        
        String searchQuery = buildProductSearchQuery(product);
        
        List<Document> similarDocs = vectorStore.similaritySearch(
            SearchRequest.query(searchQuery)
                .withTopK(limit + 1) // +1 to exclude the product itself
                .withSimilarityThreshold(0.7)
                .withFilterExpression("category = '" + product.getCategory() + "'")
        );
        
        return similarDocs.stream()
            .map(doc -> Long.valueOf(doc.getMetadata().get("product_id").toString()))
            .filter(id -> !id.equals(productId)) // Exclude the original product
            .limit(limit)
            .map(productRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }
    
    /**
     * Personalized recommendations based on user behavior
     */
    public List<Product> getPersonalizedRecommendations(Long userId, int limit) {
        UserProfile profile = buildUserProfile(userId);
        
        String userQuery = profile.getInterests().stream()
            .collect(Collectors.joining(" "));
        
        List<Document> recommendations = vectorStore.similaritySearch(
            SearchRequest.query(userQuery)
                .withTopK(limit * 2) // Get more to filter and diversify
                .withSimilarityThreshold(0.6)
        );
        
        // Apply business rules and diversification
        return diversifyRecommendations(recommendations, profile)
            .stream()
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    private Document convertProductToDocument(Product product) {
        String content = String.format("%s %s %s %s",
            product.getName(),
            product.getDescription(),
            product.getCategory(),
            String.join(" ", product.getTags())
        );
        
        Map<String, Object> metadata = Map.of(
            "product_id", product.getId(),
            "category", product.getCategory(),
            "price", product.getPrice(),
            "rating", product.getAverageRating(),
            "brand", product.getBrand()
        );
        
        return new Document(content, metadata);
    }
    
    private String buildProductSearchQuery(Product product) {
        return String.format("%s %s category:%s brand:%s",
            product.getName(),
            product.getDescription(),
            product.getCategory(),
            product.getBrand()
        );
    }
    
    private UserProfile buildUserProfile(Long userId) {
        // Analyze user's purchase history, browsing behavior, ratings, etc.
        List<Order> orders = orderRepository.findByUserId(userId);
        List<String> interests = orders.stream()
            .flatMap(order -> order.getItems().stream())
            .map(item -> item.getProduct().getCategory())
            .distinct()
            .collect(Collectors.toList());
        
        return new UserProfile(userId, interests);
    }
    
    private List<Product> diversifyRecommendations(List<Document> documents, UserProfile profile) {
        // Implement diversification algorithm to avoid showing too many similar products
        Map<String, List<Document>> byCategory = documents.stream()
            .collect(Collectors.groupingBy(doc -> doc.getMetadata().get("category").toString()));
        
        List<Product> diversified = new ArrayList<>();
        
        // Round-robin selection from different categories
        int maxPerCategory = Math.max(1, documents.size() / byCategory.size());
        
        for (Map.Entry<String, List<Document>> entry : byCategory.entrySet()) {
            entry.getValue().stream()
                .limit(maxPerCategory)
                .forEach(doc -> {
                    Long productId = Long.valueOf(doc.getMetadata().get("product_id").toString());
                    productRepository.findById(productId).ifPresent(diversified::add);
                });
        }
        
        return diversified;
    }
    
    @Data
    @AllArgsConstructor
    public static class UserProfile {
        private Long userId;
        private List<String> interests;
    }
}
```

### Document Management System

```java
@RestController
@RequestMapping("/api/documents")
public class DocumentManagementController {
    
    private final DocumentManagementService documentService;
    
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "category", required = false) String category) {
        
        try {
            DocumentUploadResponse response = documentService.processAndStoreDocument(file, tags, category);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new DocumentUploadResponse(null, "Failed to process document: " + e.getMessage()));
        }
    }
    
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> searchDocuments(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "threshold", defaultValue = "0.7") double threshold,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) List<String> tags) {
        
        SearchFilters filters = SearchFilters.builder()
            .topK(limit)
            .similarityThreshold(threshold)
            .category(category)
            .tags(tags)
            .build();
        
        SearchResponse response = documentService.searchDocuments(query, filters);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{documentId}/similar")
    public ResponseEntity<List<DocumentSummary>> findSimilarDocuments(
            @PathVariable String documentId,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        
        List<DocumentSummary> similar = documentService.findSimilarDocuments(documentId, limit);
        return ResponseEntity.ok(similar);
    }
}

@Service
@Transactional
public class DocumentManagementService {
    
    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final UniversalDocumentLoader documentLoader;
    private final IntelligentTextSplitter textSplitter;
    
    public DocumentUploadResponse processAndStoreDocument(MultipartFile file, 
                                                        List<String> tags, 
                                                        String category) throws IOException {
        
        // Save file temporarily
        String tempFilePath = saveTemporaryFile(file);
        
        try {
            // Load and process document
            List<Document> documents = documentLoader.loadDocument(tempFilePath);
            
            // Create document entity
            DocumentEntity docEntity = new DocumentEntity();
            docEntity.setOriginalFilename(file.getOriginalFilename());
            docEntity.setContentType(file.getContentType());
            docEntity.setFileSize(file.getSize());
            docEntity.setCategory(category);
            docEntity.setTags(tags != null ? tags : List.of());
            docEntity.setUploadedAt(LocalDateTime.now());
            docEntity = documentRepository.save(docEntity);
            
            // Process and split documents
            List<Document> processedDocs = documents.stream()
                .flatMap(doc -> {
                    Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                    metadata.put("document_id", docEntity.getId());
                    metadata.put("original_filename", file.getOriginalFilename());
                    metadata.put("category", category);
                    metadata.put("tags", tags);
                    
                    return textSplitter.semanticSplit(doc.getContent(), metadata).stream();
                })
                .collect(Collectors.toList());
            
            // Store in vector database
            vectorStore.add(processedDocs);
            
            // Update document entity with chunk count
            docEntity.setChunkCount(processedDocs.size());
            documentRepository.save(docEntity);
            
            return new DocumentUploadResponse(docEntity.getId(), "Document processed successfully");
            
        } finally {
            // Clean up temporary file
            Files.deleteIfExists(Paths.get(tempFilePath));
        }
    }
    
    public SearchResponse searchDocuments(String query, SearchFilters filters) {
        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.query(query)
                .withTopK(filters.getTopK())
                .withSimilarityThreshold(filters.getSimilarityThreshold())
                .withFilterExpression(buildFilterExpression(filters))
        );
        
        // Group results by document ID
        Map<String, List<Document>> byDocument = results.stream()
            .collect(Collectors.groupingBy(doc -> 
                doc.getMetadata().get("document_id").toString()));
        
        List<DocumentSearchResult> searchResults = byDocument.entrySet().stream()
            .map(entry -> {
                String documentId = entry.getKey();
                List<Document> chunks = entry.getValue();
                
                DocumentEntity docEntity = documentRepository.findById(documentId).orElse(null);
                if (docEntity == null) return null;
                
                // Get the most relevant chunk
                Document bestChunk = chunks.get(0);
                
                return new DocumentSearchResult(
                    documentId,
                    docEntity.getOriginalFilename(),
                    bestChunk.getContent().substring(0, Math.min(200, bestChunk.getContent().length())) + "...",
                    docEntity.getCategory(),
                    docEntity.getTags(),
                    chunks.size()
                );
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        return new SearchResponse(searchResults, results.size());
    }
    
    private String buildFilterExpression(SearchFilters filters) {
        List<String> conditions = new ArrayList<>();
        
        if (filters.getCategory() != null) {
            conditions.add("category = '" + filters.getCategory() + "'");
        }
        
        if (filters.getTags() != null && !filters.getTags().isEmpty()) {
            String tagConditions = filters.getTags().stream()
                .map(tag -> "'" + tag + "' = ANY(tags)")
                .collect(Collectors.joining(" OR "));
            conditions.add("(" + tagConditions + ")");
        }
        
        return conditions.isEmpty() ? "" : String.join(" AND ", conditions);
    }
    
    @Data
    @Builder
    public static class SearchFilters {
        private int topK;
        private double similarityThreshold;
        private String category;
        private List<String> tags;
    }
    
    public record DocumentUploadResponse(String documentId, String message) {}
    public record SearchResponse(List<DocumentSearchResult> results, int totalChunks) {}
    public record DocumentSearchResult(String documentId, String filename, String preview, 
                                     String category, List<String> tags, int relevantChunks) {}
}
```

This completes Phase 4 with comprehensive coverage of embeddings and vector operations. The examples include:

1. **Core embedding concepts** with similarity calculations
2. **Multi-provider embedding integration** (OpenAI, Azure, Vertex AI, Hugging Face)
3. **Advanced vector database operations** with Pinecone and PostgreSQL
4. **Intelligent document processing** with semantic chunking
5. **Real-world applications** like product recommendations and document management

**Key takeaways from Phase 4:**
- Embeddings transform text into semantic vectors for similarity comparison
- Different distance metrics serve different use cases
- Vector databases enable efficient similarity search at scale
- Intelligent chunking preserves semantic coherence
- Caching and batch processing optimize performance and costs

Ready to move on to **Phase 5: Retrieval Augmented Generation (RAG)**? This will build on the embedding foundation to create powerful question-answering systems!