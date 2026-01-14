# Phase 1: Prerequisites and Foundation - Detailed Study Guide
*Duration: Week 1-2 (20-30 hours total)*

---

## 1.1 Java Fundamentals Review (Week 1: 10-12 hours)

### Core Java 17+ Features

#### **Records and Pattern Matching**

**Concept Deep Dive:**
Records in Java provide a concise way to create data carrier classes with immutable data.

**Key Learning Points:**
- Automatic generation of constructor, getters, equals(), hashCode(), toString()
- Compact constructor syntax
- Record patterns in switch expressions (Java 19+)
- When to use Records vs Classes

**Practical Exercise:**
```java
// Create a ChatMessage record for Spring AI
public record ChatMessage(
    String role,
    String content,
    LocalDateTime timestamp,
    Map<String, Object> metadata
) {
    // Compact constructor with validation
    public ChatMessage {
        Objects.requireNonNull(role, "Role cannot be null");
        Objects.requireNonNull(content, "Content cannot be null");
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
    
    // Static factory methods
    public static ChatMessage userMessage(String content) {
        return new ChatMessage("user", content, null, null);
    }
    
    public static ChatMessage assistantMessage(String content) {
        return new ChatMessage("assistant", content, null, null);
    }
}
```

#### **Sealed Classes**

**Concept Deep Dive:**
Sealed classes restrict which classes can extend or implement them, providing better control over inheritance hierarchies.

**Key Learning Points:**
- Controlling inheritance hierarchies
- Pattern matching with sealed classes
- Exhaustive switch statements
- Use cases in domain modeling

**Practical Exercise:**
```java
// AI Response types using sealed classes
public sealed interface AIResponse 
    permits TextResponse, ImageResponse, ErrorResponse {
}

public record TextResponse(String content, int tokenCount) implements AIResponse {}

public record ImageResponse(String url, String prompt, int width, int height) implements AIResponse {}

public record ErrorResponse(String message, String errorCode) implements AIResponse {}

// Usage with pattern matching
public String processResponse(AIResponse response) {
    return switch (response) {
        case TextResponse(var content, var tokens) -> 
            "Generated text: " + content + " (tokens: " + tokens + ")";
        case ImageResponse(var url, var prompt, var w, var h) -> 
            "Generated image: " + url + " (" + w + "x" + h + ")";
        case ErrorResponse(var message, var code) -> 
            "Error " + code + ": " + message;
    };
}
```

#### **Text Blocks**

**Concept Deep Dive:**
Text blocks provide a cleaner way to write multi-line strings, especially useful for AI prompts and JSON.

**Practical Exercise:**
```java
public class AIPromptTemplates {
    
    public static final String SYSTEM_PROMPT = """
        You are a helpful AI assistant specialized in software development.
        Your responses should be:
        - Accurate and technically correct
        - Well-structured and easy to understand
        - Include practical examples when appropriate
        
        Current context: Spring AI integration
        """;
    
    public static final String JSON_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "response": {
              "type": "string",
              "description": "The AI-generated response"
            },
            "confidence": {
              "type": "number",
              "minimum": 0,
              "maximum": 1
            },
            "metadata": {
              "type": "object",
              "properties": {
                "model": {"type": "string"},
                "tokens": {"type": "integer"}
              }
            }
          },
          "required": ["response", "confidence"]
        }
        """;
}
```

#### **Stream API Advanced Usage**

**Key Learning Points:**
- Advanced collectors
- Parallel streams performance considerations
- Custom collectors
- Stream debugging techniques

**Practical Exercise:**
```java
public class AIDataProcessor {
    
    // Group chat messages by conversation and count tokens
    public Map<String, IntSummaryStatistics> analyzeConversations(
            List<ChatMessage> messages) {
        return messages.stream()
            .collect(groupingBy(
                ChatMessage::conversationId,
                summarizingInt(msg -> countTokens(msg.content()))
            ));
    }
    
    // Find most relevant documents using parallel streams
    public List<Document> findRelevantDocuments(
            String query, 
            List<Document> documents, 
            double threshold) {
        return documents.parallelStream()
            .filter(doc -> calculateRelevance(query, doc) > threshold)
            .sorted((d1, d2) -> Double.compare(
                calculateRelevance(query, d2),
                calculateRelevance(query, d1)
            ))
            .limit(10)
            .collect(toList());
    }
    
    // Custom collector for AI response aggregation
    public static Collector<AIResponse, ?, ResponseSummary> summarizeResponses() {
        return Collector.of(
            ResponseSummary::new,
            ResponseSummary::accumulate,
            ResponseSummary::combine,
            ResponseSummary::finalize
        );
    }
}
```

#### **CompletableFuture and Async Programming**

**Key Learning Points:**
- Async composition patterns
- Error handling in async flows
- Thread pool management
- Combining multiple async operations

**Practical Exercise:**
```java
@Service
public class AsyncAIService {
    
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final Executor aiExecutor;
    
    // Async chat completion
    public CompletableFuture<String> generateResponseAsync(String prompt) {
        return CompletableFuture
            .supplyAsync(() -> chatModel.call(prompt), aiExecutor)
            .exceptionally(throwable -> {
                log.error("Error generating response", throwable);
                return "I apologize, but I encountered an error. Please try again.";
            });
    }
    
    // Parallel processing of multiple prompts
    public CompletableFuture<List<String>> generateMultipleResponses(List<String> prompts) {
        List<CompletableFuture<String>> futures = prompts.stream()
            .map(this::generateResponseAsync)
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }
    
    // Combining chat and embedding operations
    public CompletableFuture<EnrichedResponse> generateEnrichedResponse(String query) {
        CompletableFuture<String> chatFuture = generateResponseAsync(query);
        CompletableFuture<List<Double>> embeddingFuture = 
            CompletableFuture.supplyAsync(() -> embeddingModel.embed(query), aiExecutor);
        
        return chatFuture.thenCombine(embeddingFuture, 
            (response, embedding) -> new EnrichedResponse(response, embedding));
    }
}
```

---

## 1.2 Spring Boot Advanced Concepts (Week 1-2: 8-10 hours)

### Auto-configuration Deep Dive

#### **Understanding Auto-configuration Mechanism**

**Key Learning Points:**
- @EnableAutoConfiguration annotation
- Auto-configuration classes structure
- Conditional annotations usage
- Configuration properties binding

**Practical Exercise - Custom AI Auto-configuration:**
```java
// AI Configuration Properties
@ConfigurationProperties(prefix = "spring.ai")
@Data
public class AIConfigurationProperties {
    private OpenAI openai = new OpenAI();
    private Azure azure = new Azure();
    private Embedding embedding = new Embedding();
    
    @Data
    public static class OpenAI {
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-3.5-turbo";
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 3;
    }
    
    @Data
    public static class Azure {
        private String endpoint;
        private String apiKey;
        private String deploymentName;
        private String apiVersion = "2023-12-01-preview";
    }
    
    @Data
    public static class Embedding {
        private String model = "text-embedding-ada-002";
        private int dimensions = 1536;
        private boolean cache = true;
    }
}

// Auto-configuration Class
@Configuration
@EnableConfigurationProperties(AIConfigurationProperties.class)
@ConditionalOnClass({ChatModel.class, EmbeddingModel.class})
public class AIAutoConfiguration {
    
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.openai", name = "api-key")
    @ConditionalOnMissingBean
    public OpenAIChatModel openAIChatModel(AIConfigurationProperties properties) {
        return OpenAIChatModel.builder()
            .apiKey(properties.getOpenai().getApiKey())
            .baseUrl(properties.getOpenai().getBaseUrl())
            .model(properties.getOpenai().getModel())
            .timeout(properties.getOpenai().getTimeout())
            .maxRetries(properties.getOpenai().getMaxRetries())
            .build();
    }
    
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.azure", name = "endpoint")
    @ConditionalOnMissingBean
    public AzureOpenAIChatModel azureOpenAIChatModel(AIConfigurationProperties properties) {
        return AzureOpenAIChatModel.builder()
            .endpoint(properties.getAzure().getEndpoint())
            .apiKey(properties.getAzure().getApiKey())
            .deploymentName(properties.getAzure().getDeploymentName())
            .apiVersion(properties.getAzure().getApiVersion())
            .build();
    }
    
    @Bean
    @ConditionalOnBean(ChatModel.class)
    public AIService aiService(ChatModel chatModel, 
                              @Autowired(required = false) EmbeddingModel embeddingModel) {
        return new AIService(chatModel, embeddingModel);
    }
}
```

### Spring WebFlux and Reactive Programming

#### **Reactive Streams Fundamentals**

**Key Learning Points:**
- Publisher, Subscriber, Subscription, Processor interfaces
- Hot vs Cold streams
- Backpressure handling
- Scheduler types and thread management

**Practical Exercise:**
```java
@RestController
@RequestMapping("/api/ai")
public class ReactiveAIController {
    
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    
    // Streaming chat completions
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestParam String message) {
        return Flux.fromStream(chatModel.stream(message))
            .map(chunk -> ServerSentEvent.<String>builder()
                .event("message")
                .data(chunk)
                .build())
            .delayElements(Duration.ofMillis(50)) // Simulate streaming delay
            .onErrorResume(error -> 
                Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("Error: " + error.getMessage())
                    .build()));
    }
    
    // Batch processing with backpressure
    @PostMapping("/embeddings/batch")
    public Flux<EmbeddingResult> batchEmbeddings(@RequestBody Flux<String> texts) {
        return texts
            .buffer(10) // Process in batches of 10
            .delayElements(Duration.ofMillis(100)) // Rate limiting
            .flatMap(batch -> 
                Mono.fromCallable(() -> embeddingModel.embedAll(batch))
                    .subscribeOn(Schedulers.boundedElastic()))
            .flatMapIterable(embeddings -> embeddings)
            .map(embedding -> new EmbeddingResult(embedding.getVector(), embedding.getMetadata()));
    }
    
    // Combining reactive streams
    @PostMapping("/analyze")
    public Mono<AnalysisResult> analyzeText(@RequestBody String text) {
        Mono<String> chatResponse = Mono.fromCallable(() -> chatModel.call(text))
            .subscribeOn(Schedulers.boundedElastic());
            
        Mono<List<Double>> embedding = Mono.fromCallable(() -> embeddingModel.embed(text))
            .subscribeOn(Schedulers.boundedElastic());
            
        return Mono.zip(chatResponse, embedding)
            .map(tuple -> new AnalysisResult(tuple.getT1(), tuple.getT2()));
    }
}
```

### Spring Security Fundamentals

#### **Security Configuration for AI APIs**

**Practical Exercise:**
```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class AISecurityConfiguration {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ai/public/**").permitAll()
                .requestMatchers("/api/ai/chat/**").hasRole("USER")
                .requestMatchers("/api/ai/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation("https://your-auth-provider.com");
    }
    
    // Rate limiting for AI API calls
    @Bean
    public RateLimitingFilter rateLimitingFilter() {
        return new RateLimitingFilter();
    }
}

// Custom rate limiting filter
public class RateLimitingFilter implements Filter {
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String userId = extractUserId(httpRequest);
        
        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(userId, 
            key -> RateLimiter.create(10.0)); // 10 requests per second
        
        if (rateLimiter.tryAcquire()) {
            chain.doFilter(request, response);
        } else {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(429);
            httpResponse.getWriter().write("Rate limit exceeded");
        }
    }
}
```

---

## 1.3 AI and ML Basic Concepts (Week 2: 8-10 hours)

### Machine Learning Fundamentals

#### **Understanding ML Paradigms**

**Key Concepts to Master:**
1. **Supervised Learning**: Learning with labeled data
2. **Unsupervised Learning**: Finding patterns in unlabeled data
3. **Reinforcement Learning**: Learning through interaction and rewards

**Practical Understanding Exercise:**
```java
public class MLConceptsDemo {
    
    // Supervised Learning Example: Text Classification
    public class TextClassifier {
        // In Spring AI context, this would be handled by the AI model
        public String classifyText(String text) {
            // Categories: POSITIVE, NEGATIVE, NEUTRAL
            return chatModel.call("Classify the sentiment of this text: " + text);
        }
    }
    
    // Unsupervised Learning Example: Document Clustering
    public class DocumentClusterer {
        public Map<Integer, List<String>> clusterDocuments(List<String> documents) {
            // Get embeddings for all documents
            List<List<Double>> embeddings = documents.stream()
                .map(embeddingModel::embed)
                .toList();
            
            // Cluster based on similarity (simplified k-means concept)
            return performClustering(embeddings, documents);
        }
    }
}
```

### Natural Language Processing (NLP)

#### **Core NLP Concepts**

**Key Learning Areas:**
- **Tokenization**: Breaking text into meaningful units
- **Part-of-Speech Tagging**: Identifying grammatical roles
- **Named Entity Recognition**: Identifying people, places, organizations
- **Sentiment Analysis**: Determining emotional tone
- **Language Translation**: Converting between languages

**Spring AI Context Understanding:**
```java
@Service
public class NLPService {
    
    private final ChatModel chatModel;
    
    // Tokenization understanding through AI
    public List<String> explainTokenization(String text) {
        String prompt = """
            Explain how the following text would be tokenized for AI processing:
            "%s"
            
            Show the individual tokens and explain the process.
            """.formatted(text);
            
        return Arrays.asList(chatModel.call(prompt).split("\\n"));
    }
    
    // Named Entity Recognition
    public Map<String, List<String>> extractEntities(String text) {
        String prompt = """
            Extract named entities from the following text and categorize them:
            "%s"
            
            Return in JSON format with categories: PERSON, ORGANIZATION, LOCATION, DATE, MONEY
            """.formatted(text);
            
        String response = chatModel.call(prompt);
        // Parse JSON response (simplified)
        return parseEntitiesFromResponse(response);
    }
    
    // Sentiment Analysis
    public SentimentResult analyzeSentiment(String text) {
        String prompt = """
            Analyze the sentiment of the following text:
            "%s"
            
            Provide:
            1. Overall sentiment (POSITIVE, NEGATIVE, NEUTRAL)
            2. Confidence score (0-1)
            3. Key phrases that influenced the sentiment
            """.formatted(text);
            
        String response = chatModel.call(prompt);
        return parseSentimentResponse(response);
    }
}
```

### Large Language Models (LLMs)

#### **Transformer Architecture Understanding**

**Key Concepts:**
- **Self-Attention Mechanism**: How models focus on relevant parts
- **Encoder-Decoder Architecture**: Input processing and output generation
- **Position Encoding**: Understanding sequence order
- **Multi-Head Attention**: Parallel attention mechanisms

**Practical Understanding:**
```java
public class LLMConceptsDemo {
    
    // Understanding context windows
    public class ContextWindowManager {
        private static final int MAX_TOKENS = 4096; // Example for GPT-3.5
        
        public String manageContext(List<ChatMessage> conversation) {
            int totalTokens = conversation.stream()
                .mapToInt(msg -> estimateTokens(msg.content()))
                .sum();
                
            if (totalTokens > MAX_TOKENS) {
                // Truncate older messages while preserving system message
                return truncateConversation(conversation);
            }
            
            return buildContextFromMessages(conversation);
        }
        
        private int estimateTokens(String text) {
            // Rough estimation: 1 token ≈ 4 characters for English
            return text.length() / 4;
        }
    }
    
    // Understanding temperature and parameters
    public class GenerationParameters {
        public AIResponse generateWithParameters(String prompt, 
                                               double temperature, 
                                               int maxTokens, 
                                               double topP) {
            // These parameters control:
            // - temperature: creativity/randomness (0.0 = deterministic, 1.0 = very creative)
            // - maxTokens: maximum response length
            // - topP: nucleus sampling (probability mass)
            
            return chatModel.call(prompt, temperature, maxTokens, topP);
        }
    }
}
```

#### **Prompt Engineering Fundamentals**

**Key Techniques:**
- **Zero-shot Prompting**: Task without examples
- **Few-shot Prompting**: Task with examples
- **Chain-of-Thought**: Step-by-step reasoning
- **Role Playing**: Assigning specific personas

**Practical Exercise:**
```java
@Component
public class PromptEngineeringTemplates {
    
    // Zero-shot prompt template
    public String zeroShotPrompt(String task, String input) {
        return """
            Task: %s
            
            Input: %s
            
            Please provide a clear and accurate response.
            """.formatted(task, input);
    }
    
    // Few-shot prompt template
    public String fewShotPrompt(String task, List<Example> examples, String input) {
        StringBuilder prompt = new StringBuilder("Task: " + task + "\n\n");
        
        examples.forEach(example -> 
            prompt.append("Input: ").append(example.input())
                  .append("\nOutput: ").append(example.output())
                  .append("\n\n"));
        
        prompt.append("Input: ").append(input).append("\nOutput: ");
        
        return prompt.toString();
    }
    
    // Chain-of-thought prompt
    public String chainOfThoughtPrompt(String problem) {
        return """
            Let's solve this step by step.
            
            Problem: %s
            
            Step 1: Identify what we need to find
            Step 2: Break down the problem into smaller parts
            Step 3: Solve each part
            Step 4: Combine the results
            Step 5: Verify the answer
            
            Please work through each step carefully.
            """.formatted(problem);
    }
    
    // Role-based prompt
    public String roleBasedPrompt(String role, String context, String question) {
        return """
            You are a %s with extensive experience in the field.
            
            Context: %s
            
            Question: %s
            
            Please provide a professional response based on your expertise.
            """.formatted(role, context, question);
    }
}

record Example(String input, String output) {}
```

---

## Phase 1 Practical Assignments

### Assignment 1: Advanced Java Features Implementation (Week 1)

**Task**: Create a comprehensive AI message handling system using modern Java features.

**Requirements:**
1. Use Records for immutable data structures
2. Implement Sealed classes for type hierarchy
3. Use Text blocks for prompt templates
4. Implement async processing with CompletableFuture
5. Use advanced Stream operations

**Deliverable**: Working application with test cases

### Assignment 2: Spring Boot Auto-configuration (Week 2)

**Task**: Create a custom auto-configuration for an AI service.

**Requirements:**
1. Custom configuration properties
2. Conditional bean creation
3. Integration with Spring Boot's configuration system
4. Documentation for configuration options

### Assignment 3: Reactive AI Service (Week 2)

**Task**: Build a reactive web service for AI interactions.

**Requirements:**
1. WebFlux endpoints with streaming responses
2. Proper error handling
3. Backpressure management
4. Security integration

---

## Study Schedule and Checkpoints

### Week 1 Schedule:
- **Days 1-2**: Java Records, Sealed Classes, Text Blocks (4 hours)
- **Days 3-4**: Stream API Advanced, CompletableFuture (4 hours)
- **Days 5-6**: Spring Boot Auto-configuration (3 hours)
- **Day 7**: Assignment 1 completion

### Week 2 Schedule:
- **Days 1-2**: WebFlux and Reactive Programming (4 hours)
- **Days 3-4**: Spring Security for AI APIs (3 hours)
- **Days 5-6**: AI/ML Concepts, LLM Understanding (4 hours)
- **Day 7**: Assignments 2 and 3 completion

### Knowledge Checkpoints:
- [ ] Can create and use Records effectively
- [ ] Understands Sealed classes and pattern matching
- [ ] Proficient with async programming patterns
- [ ] Can create custom Spring Boot auto-configurations
- [ ] Understands reactive programming with WebFlux
- [ ] Knows AI/ML fundamental concepts
- [ ] Can engineer effective prompts

### Resources for Phase 1:
1. **Java 17+ Documentation**: Oracle JDK documentation
2. **Spring Boot Reference**: Official Spring Boot docs
3. **WebFlux Guide**: Spring WebFlux documentation
4. **ML Basics**: Andrew Ng's Machine Learning Course
5. **LLM Papers**: "Attention Is All You Need", GPT papers

This completes Phase 1 foundation. Once you've mastered these concepts, you'll be ready for Phase 2 where we dive into Spring AI core framework!