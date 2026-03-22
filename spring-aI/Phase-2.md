# Phase 2: Spring AI Core Framework - Complete Guide

## 2.1 Spring AI Introduction and Setup

### Project Setup and Dependencies

#### Maven Configuration

Create a complete `pom.xml` for Spring AI project:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>spring-ai-demo</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
        <spring-ai.version>1.0.0-M3</spring-ai.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- Spring AI BOM and Core Dependencies -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- OpenAI Integration -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        </dependency>

        <!-- Embedding Model Support -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-transformers-spring-boot-starter</artifactId>
        </dependency>

        <!-- Vector Store Support -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
        </dependency>

        <!-- Additional Dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
        <repository>
            <id>spring-snapshots</id>
            <name>Spring Snapshots</name>
            <url>https://repo.spring.io/snapshot</url>
            <releases>
                <enabled>false</enabled>
            </releases>
        </repository>
    </repositories>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

#### Gradle Configuration (Alternative)

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.example'
version = '1.0.0'
sourceCompatibility = '17'

repositories {
    mavenCentral()
    maven { url 'https://repo.spring.io/milestone' }
    maven { url 'https://repo.spring.io/snapshot' }
}

ext {
    set('springAiVersion', '1.0.0-M3')
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter'
    implementation 'org.springframework.ai:spring-ai-transformers-spring-boot-starter'
    implementation 'org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.ai:spring-ai-bom:${springAiVersion}"
    }
}
```

### Core Architecture Understanding

#### Spring AI Auto-configuration Deep Dive

Spring AI uses sophisticated auto-configuration to set up AI models and related beans automatically.

```java
// Custom configuration to understand auto-configuration
@Configuration
@EnableConfigurationProperties({OpenAiConnectionProperties.class})
public class SpringAiConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.openai", name = "api-key")
    public OpenAiApi openAiApi(OpenAiConnectionProperties properties) {
        return new OpenAiApi(properties.getApiKey());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        return new OpenAiChatModel(openAiApi, OpenAiChatOptions.builder()
                .withModel("gpt-3.5-turbo")
                .withTemperature(0.7f)
                .build());
    }
    
    // Configuration debugging
    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        
        // Log all Spring AI beans
        String[] beanNames = context.getBeanNamesForType(ChatModel.class);
        log.info("Registered ChatModel beans: {}", Arrays.toString(beanNames));
        
        beanNames = context.getBeanNamesForType(EmbeddingModel.class);
        log.info("Registered EmbeddingModel beans: {}", Arrays.toString(beanNames));
    }
}
```

#### Bean Registration Process

```java
@Component
public class SpringAiBeanInspector implements ApplicationContextAware {
    
    private ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @PostConstruct
    public void inspectBeans() {
        // Inspect ChatModel registrations
        Map<String, ChatModel> chatModels = applicationContext.getBeansOfType(ChatModel.class);
        chatModels.forEach((name, bean) -> {
            System.out.printf("ChatModel Bean: %s -> %s%n", name, bean.getClass().getSimpleName());
        });
        
        // Inspect EmbeddingModel registrations  
        Map<String, EmbeddingModel> embeddingModels = applicationContext.getBeansOfType(EmbeddingModel.class);
        embeddingModels.forEach((name, bean) -> {
            System.out.printf("EmbeddingModel Bean: %s -> %s%n", name, bean.getClass().getSimpleName());
        });
        
        // Inspect VectorStore registrations
        Map<String, VectorStore> vectorStores = applicationContext.getBeansOfType(VectorStore.class);
        vectorStores.forEach((name, bean) -> {
            System.out.printf("VectorStore Bean: %s -> %s%n", name, bean.getClass().getSimpleName());
        });
    }
}
```

## 2.2 AI Model Abstraction Layer

### ChatModel Interface Deep Dive

The `ChatModel` interface is the core abstraction for conversational AI models.

```java
// Understanding the ChatModel interface
public interface ChatModel extends Model<Prompt, ChatResponse> {
    
    // Synchronous call - blocks until response
    default String call(String message) {
        return call(new Prompt(message)).getResult().getOutput().getContent();
    }
    
    // Full prompt-response cycle
    ChatResponse call(Prompt prompt);
    
    // Streaming response (if supported)
    default Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }
}
```

#### Practical ChatModel Implementation Examples

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final ChatModel chatModel;
    
    public ChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    // Simple chat endpoint
    @PostMapping("/simple")
    public ResponseEntity<String> simpleChat(@RequestBody String message) {
        try {
            String response = chatModel.call(message);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }
    
    // Advanced chat with options
    @PostMapping("/advanced")
    public ResponseEntity<ChatResponse> advancedChat(@RequestBody ChatRequest request) {
        
        // Build prompt with system and user messages
        Message systemMessage = new SystemMessage(request.getSystemPrompt());
        Message userMessage = new UserMessage(request.getUserMessage());
        
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage),
                OpenAiChatOptions.builder()
                        .withModel("gpt-4")
                        .withTemperature(request.getTemperature())
                        .withMaxTokens(request.getMaxTokens())
                        .build());
        
        ChatResponse response = chatModel.call(prompt);
        return ResponseEntity.ok(response);
    }
    
    // Streaming chat endpoint
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamingChat(@RequestParam String message) {
        
        return chatModel.stream(new Prompt(message))
                .map(chatResponse -> {
                    String content = chatResponse.getResult().getOutput().getContent();
                    return ServerSentEvent.<String>builder()
                            .data(content)
                            .build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("complete")
                        .data("[DONE]")
                        .build()));
    }
}

// Request/Response DTOs
@Data
public class ChatRequest {
    private String systemPrompt = "You are a helpful assistant.";
    private String userMessage;
    private Float temperature = 0.7f;
    private Integer maxTokens = 1000;
}
```

### EmbeddingModel Interface Deep Dive

The `EmbeddingModel` interface handles vector representations of text.

```java
@Service
public class EmbeddingService {
    
    private final EmbeddingModel embeddingModel;
    
    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    // Single text embedding
    public List<Double> embedSingleText(String text) {
        EmbeddingRequest request = new EmbeddingRequest(List.of(text), 
                EmbeddingOptions.EMPTY);
        
        EmbeddingResponse response = embeddingModel.call(request);
        return response.getResults().get(0).getOutput();
    }
    
    // Batch text embedding
    public List<List<Double>> embedBatchTexts(List<String> texts) {
        EmbeddingRequest request = new EmbeddingRequest(texts, 
                EmbeddingOptions.EMPTY);
        
        EmbeddingResponse response = embeddingModel.call(request);
        return response.getResults().stream()
                .map(result -> result.getOutput())
                .collect(Collectors.toList());
    }
    
    // Semantic similarity calculation
    public double calculateSimilarity(String text1, String text2) {
        List<Double> embedding1 = embedSingleText(text1);
        List<Double> embedding2 = embedSingleText(text2);
        
        return cosineSimilarity(embedding1, embedding2);
    }
    
    // Cosine similarity implementation
    private double cosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += Math.pow(vectorA.get(i), 2);
            normB += Math.pow(vectorB.get(i), 2);
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}

// REST endpoint for embedding operations
@RestController
@RequestMapping("/api/embeddings")
public class EmbeddingController {
    
    private final EmbeddingService embeddingService;
    
    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }
    
    @PostMapping("/embed")
    public ResponseEntity<List<Double>> embedText(@RequestBody String text) {
        List<Double> embedding = embeddingService.embedSingleText(text);
        return ResponseEntity.ok(embedding);
    }
    
    @PostMapping("/similarity")
    public ResponseEntity<Double> calculateSimilarity(@RequestBody SimilarityRequest request) {
        double similarity = embeddingService.calculateSimilarity(
                request.getText1(), request.getText2());
        return ResponseEntity.ok(similarity);
    }
    
    @PostMapping("/batch")
    public ResponseEntity<List<List<Double>>> embedBatch(@RequestBody List<String> texts) {
        List<List<Double>> embeddings = embeddingService.embedBatchTexts(texts);
        return ResponseEntity.ok(embeddings);
    }
}

@Data
public class SimilarityRequest {
    private String text1;
    private String text2;
}
```

### ImageModel Interface Deep Dive

The `ImageModel` interface handles image generation and manipulation.

```java
@Service
public class ImageGenerationService {
    
    private final ImageModel imageModel;
    
    public ImageGenerationService(ImageModel imageModel) {
        this.imageModel = imageModel;
    }
    
    // Generate single image
    public String generateImage(String prompt) {
        ImagePrompt imagePrompt = new ImagePrompt(prompt);
        ImageResponse response = imageModel.call(imagePrompt);
        
        return response.getResults().get(0).getOutput().getUrl();
    }
    
    // Generate image with advanced options
    public ImageGenerationResult generateImageAdvanced(ImageGenerationRequest request) {
        ImageOptions options = OpenAiImageOptions.builder()
                .withQuality(request.getQuality())
                .withN(request.getNumberOfImages())
                .withWidth(request.getWidth())
                .withHeight(request.getHeight())
                .build();
        
        ImagePrompt imagePrompt = new ImagePrompt(request.getPrompt(), options);
        ImageResponse response = imageModel.call(imagePrompt);
        
        List<String> imageUrls = response.getResults().stream()
                .map(result -> result.getOutput().getUrl())
                .collect(Collectors.toList());
        
        return new ImageGenerationResult(imageUrls, response.getMetadata());
    }
}

@RestController
@RequestMapping("/api/images")
public class ImageController {
    
    private final ImageGenerationService imageGenerationService;
    
    public ImageController(ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
    }
    
    @PostMapping("/generate")
    public ResponseEntity<String> generateImage(@RequestBody String prompt) {
        try {
            String imageUrl = imageGenerationService.generateImage(prompt);
            return ResponseEntity.ok(imageUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating image: " + e.getMessage());
        }
    }
    
    @PostMapping("/generate-advanced")
    public ResponseEntity<ImageGenerationResult> generateImageAdvanced(
            @RequestBody ImageGenerationRequest request) {
        
        ImageGenerationResult result = imageGenerationService.generateImageAdvanced(request);
        return ResponseEntity.ok(result);
    }
}

// DTOs
@Data
public class ImageGenerationRequest {
    private String prompt;
    private String quality = "standard";
    private Integer numberOfImages = 1;
    private Integer width = 1024;
    private Integer height = 1024;
}

@Data
public class ImageGenerationResult {
    private List<String> imageUrls;
    private Map<String, Object> metadata;
    
    public ImageGenerationResult(List<String> imageUrls, Map<String, Object> metadata) {
        this.imageUrls = imageUrls;
        this.metadata = metadata;
    }
}
```

## 2.3 Spring AI Configuration

### Application Properties Configuration

Create comprehensive `application.yml`:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com
      chat:
        options:
          model: gpt-3.5-turbo
          temperature: 0.7
          max-tokens: 1000
          top-p: 1.0
          frequency-penalty: 0.0
          presence-penalty: 0.0
      image:
        options:
          model: dall-e-3
          quality: standard
          size: 1024x1024
          style: vivid
      embedding:
        options:
          model: text-embedding-ada-002
          
    retry:
      max-attempts: 3
      backoff:
        initial-interval: 1000
        multiplier: 2.0
        max-interval: 10000
        
    timeout:
      connection: 30s
      read: 60s
      write: 60s

# Logging configuration
logging:
  level:
    org.springframework.ai: DEBUG
    org.springframework.web.client.RestTemplate: DEBUG
    
# Actuator configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

### Programmatic Configuration

Advanced configuration using Java config:

```java
@Configuration
@EnableConfigurationProperties({OpenAiConnectionProperties.class, CustomAiProperties.class})
public class SpringAiAdvancedConfiguration {
    
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    
    // Custom ChatModel with advanced configuration
    @Bean
    @Primary
    public OpenAiChatModel customChatModel() {
        OpenAiApi openAiApi = new OpenAiApi(apiKey);
        
        return new OpenAiChatModel(openAiApi, 
                OpenAiChatOptions.builder()
                        .withModel("gpt-4-turbo-preview")
                        .withTemperature(0.7f)
                        .withMaxTokens(2000)
                        .withTopP(0.9f)
                        .withFrequencyPenalty(0.1f)
                        .withPresencePenalty(0.1f)
                        .build());
    }
    
    // Custom EmbeddingModel configuration
    @Bean
    public OpenAiEmbeddingModel customEmbeddingModel() {
        OpenAiApi openAiApi = new OpenAiApi(apiKey);
        
        return new OpenAiEmbeddingModel(openAiApi,
                OpenAiEmbeddingOptions.builder()
                        .withModel("text-embedding-ada-002")
                        .withUser("custom-user-id")
                        .build());
    }
    
    // Conditional beans based on profiles
    @Bean
    @Profile("development")
    @ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "true")
    public ChatModel mockChatModel() {
        return new MockChatModel();
    }
    
    // Custom RestTemplate for OpenAI calls
    @Bean
    @Qualifier("openAiRestTemplate")
    public RestTemplate openAiRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Add retry template
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(1000, 2.0, 10000)
                .retryOn(ResourceAccessException.class)
                .build();
        
        restTemplate.getInterceptors().add(new RetryInterceptor(retryTemplate));
        
        // Add logging interceptor
        restTemplate.getInterceptors().add(new LoggingInterceptor());
        
        return restTemplate;
    }
}

// Custom properties class
@ConfigurationProperties(prefix = "app.ai")
@Data
public class CustomAiProperties {
    private boolean mockEnabled = false;
    private int maxRetries = 3;
    private Duration timeout = Duration.ofSeconds(30);
    private Map<String, String> modelMappings = new HashMap<>();
}

// Mock implementation for testing
public class MockChatModel implements ChatModel {
    
    @Override
    public ChatResponse call(Prompt prompt) {
        String mockResponse = "This is a mock response to: " + 
                prompt.getInstructions().get(0).getContent();
        
        Generation generation = new Generation(mockResponse);
        return new ChatResponse(List.of(generation));
    }
    
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }
}
```

### Environment-Specific Configuration

```java
// Development configuration
@Configuration
@Profile("development")
public class DevelopmentAiConfiguration {
    
    @Bean
    @Primary
    public ChatModel developmentChatModel() {
        // Use cheaper model for development
        return new OpenAiChatModel(new OpenAiApi(apiKey),
                OpenAiChatOptions.builder()
                        .withModel("gpt-3.5-turbo")
                        .withTemperature(0.5f)
                        .withMaxTokens(500)
                        .build());
    }
}

// Production configuration  
@Configuration
@Profile("production")
public class ProductionAiConfiguration {
    
    @Bean
    @Primary
    public ChatModel productionChatModel() {
        // Use more powerful model for production
        return new OpenAiChatModel(new OpenAiApi(apiKey),
                OpenAiChatOptions.builder()
                        .withModel("gpt-4-turbo")
                        .withTemperature(0.7f)
                        .withMaxTokens(2000)
                        .build());
    }
    
    @Bean
    public ChatModelCircuitBreaker chatModelCircuitBreaker(ChatModel chatModel) {
        return new ChatModelCircuitBreaker(chatModel);
    }
}

// Circuit breaker implementation
public class ChatModelCircuitBreaker implements ChatModel {
    
    private final ChatModel delegate;
    private final CircuitBreaker circuitBreaker;
    
    public ChatModelCircuitBreaker(ChatModel delegate) {
        this.delegate = delegate;
        this.circuitBreaker = CircuitBreaker.ofDefaults("chatModel");
    }
    
    @Override
    public ChatResponse call(Prompt prompt) {
        return circuitBreaker.executeSupplier(() -> delegate.call(prompt));
    }
    
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return circuitBreaker.executeSupplier(() -> delegate.stream(prompt));
    }
}
```

## Real-World Use Cases and Examples

### Use Case 1: Customer Support Chatbot

```java
@Service
public class CustomerSupportService {
    
    private final ChatModel chatModel;
    
    public CustomerSupportService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    public String handleCustomerQuery(String customerQuery, String customerContext) {
        String systemPrompt = """
            You are a helpful customer support assistant for an e-commerce company.
            
            Guidelines:
            - Be polite and professional
            - Provide accurate information
            - If you don't know something, say so
            - Always offer to escalate to human support if needed
            
            Customer Context: %s
            """.formatted(customerContext);
        
        Message systemMessage = new SystemMessage(systemPrompt);
        Message userMessage = new UserMessage(customerQuery);
        
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
        ChatResponse response = chatModel.call(prompt);
        
        return response.getResult().getOutput().getContent();
    }
}
```

### Use Case 2: Document Similarity Service

```java
@Service  
public class DocumentSimilarityService {
    
    private final EmbeddingModel embeddingModel;
    
    public DocumentSimilarityService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    public List<DocumentSimilarity> findSimilarDocuments(String queryText, 
                                                        List<String> documents, 
                                                        double threshold) {
        List<Double> queryEmbedding = embedText(queryText);
        
        return documents.stream()
                .map(doc -> {
                    List<Double> docEmbedding = embedText(doc);
                    double similarity = cosineSimilarity(queryEmbedding, docEmbedding);
                    return new DocumentSimilarity(doc, similarity);
                })
                .filter(ds -> ds.getSimilarity() >= threshold)
                .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
                .collect(Collectors.toList());
    }
    
    private List<Double> embedText(String text) {
        EmbeddingRequest request = new EmbeddingRequest(List.of(text), EmbeddingOptions.EMPTY);
        EmbeddingResponse response = embeddingModel.call(request);
        return response.getResults().get(0).getOutput();
    }
    
    // Implement cosineSimilarity method...
}

@Data
@AllArgsConstructor
public class DocumentSimilarity {
    private String document;
    private double similarity;
}
```

This completes Phase 2 of the Spring AI mastery roadmap. You now have a solid understanding of:

1. **Project Setup**: Complete Maven/Gradle configuration with proper Spring AI BOM
2. **Core Architecture**: Understanding auto-configuration and bean registration
3. **Model Abstractions**: Deep dive into ChatModel, EmbeddingModel, and ImageModel interfaces
4. **Configuration**: Both property-based and programmatic configuration approaches
5. **Real-world Examples**: Practical implementations for customer support and document similarity

Ready to move to Phase 3 (Chat Models and Conversations) when you are!