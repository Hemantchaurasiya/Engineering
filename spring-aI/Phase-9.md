# Phase 9: Spring AI Observability and Monitoring - Complete Guide

## 9.1 Metrics and Monitoring

### Spring Boot Actuator Integration

Spring Boot Actuator provides production-ready features for monitoring your Spring AI applications.

#### Basic Actuator Setup

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info,env
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

#### Custom Health Indicators for AI Services

```java
@Component
public class OpenAIHealthIndicator implements HealthIndicator {
    
    private final ChatModel chatModel;
    private final MeterRegistry meterRegistry;
    private final Timer healthCheckTimer;
    
    public OpenAIHealthIndicator(ChatModel chatModel, MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.meterRegistry = meterRegistry;
        this.healthCheckTimer = Timer.builder("ai.health.check")
            .description("AI service health check duration")
            .register(meterRegistry);
    }
    
    @Override
    public Health health() {
        return healthCheckTimer.recordCallable(() -> {
            try {
                // Simple health check with minimal token usage
                Prompt prompt = new Prompt("Reply with 'OK' only");
                ChatResponse response = chatModel.call(prompt);
                
                String responseText = response.getResult().getOutput().getContent();
                
                if (responseText != null && responseText.trim().equalsIgnoreCase("OK")) {
                    return Health.up()
                        .withDetail("service", "OpenAI")
                        .withDetail("status", "healthy")
                        .withDetail("lastCheck", Instant.now())
                        .withDetail("tokensUsed", response.getMetadata().getUsage().getTotalTokens())
                        .build();
                } else {
                    return Health.down()
                        .withDetail("service", "OpenAI")
                        .withDetail("reason", "Unexpected response")
                        .withDetail("response", responseText)
                        .build();
                }
            } catch (Exception e) {
                meterRegistry.counter("ai.health.check.failures").increment();
                return Health.down()
                    .withDetail("service", "OpenAI")
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
            }
        });
    }
}
```

#### Vector Store Health Indicator

```java
@Component
public class VectorStoreHealthIndicator implements HealthIndicator {
    
    private final VectorStore vectorStore;
    private final MeterRegistry meterRegistry;
    
    public VectorStoreHealthIndicator(VectorStore vectorStore, MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.meterRegistry = meterRegistry;
    }
    
    @Override
    public Health health() {
        try {
            // Test vector store with a simple similarity search
            List<Document> results = vectorStore.similaritySearch(
                SearchRequest.query("health check").withTopK(1)
            );
            
            boolean isHealthy = results != null;
            
            if (isHealthy) {
                return Health.up()
                    .withDetail("vectorStore", vectorStore.getClass().getSimpleName())
                    .withDetail("status", "healthy")
                    .withDetail("documentsFound", results.size())
                    .build();
            } else {
                return Health.down()
                    .withDetail("vectorStore", vectorStore.getClass().getSimpleName())
                    .withDetail("reason", "No results returned")
                    .build();
            }
        } catch (Exception e) {
            meterRegistry.counter("vectorstore.health.check.failures").increment();
            return Health.down()
                .withDetail("vectorStore", vectorStore.getClass().getSimpleName())
                .withDetail("error", e.getMessage())
                .withException(e)
                .build();
        }
    }
}
```

#### Custom Metrics Creation

```java
@Component
public class AIMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Counter chatRequestCounter;
    private final Timer chatResponseTimer;
    private final Gauge activeConversationsGauge;
    private final DistributionSummary tokenUsageSummary;
    private final AtomicLong activeConversations = new AtomicLong(0);
    
    public AIMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.chatRequestCounter = Counter.builder("ai.chat.requests.total")
            .description("Total number of chat requests")
            .register(meterRegistry);
            
        this.chatResponseTimer = Timer.builder("ai.chat.response.duration")
            .description("Chat response duration")
            .register(meterRegistry);
            
        this.activeConversationsGauge = Gauge.builder("ai.conversations.active")
            .description("Number of active conversations")
            .register(meterRegistry, activeConversations, AtomicLong::get);
            
        this.tokenUsageSummary = DistributionSummary.builder("ai.tokens.usage")
            .description("Token usage per request")
            .baseUnit("tokens")
            .register(meterRegistry);
    }
    
    public void incrementChatRequests(String model, String status) {
        chatRequestCounter.increment(
            Tags.of(
                "model", model,
                "status", status
            )
        );
    }
    
    public Timer.Sample startChatTimer(String model) {
        return Timer.start(meterRegistry)
            .tag("model", model);
    }
    
    public void recordTokenUsage(int tokens, String model, String type) {
        tokenUsageSummary.record(tokens, 
            Tags.of(
                "model", model,
                "type", type // "input", "output", "total"
            )
        );
    }
    
    public void incrementActiveConversations() {
        activeConversations.incrementAndGet();
    }
    
    public void decrementActiveConversations() {
        activeConversations.decrementAndGet();
    }
}
```

#### Comprehensive AI Service with Metrics

```java
@Service
public class MonitoredChatService {
    
    private final ChatModel chatModel;
    private final AIMetricsCollector metricsCollector;
    private final MeterRegistry meterRegistry;
    
    public MonitoredChatService(ChatModel chatModel, 
                               AIMetricsCollector metricsCollector,
                               MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.metricsCollector = metricsCollector;
        this.meterRegistry = meterRegistry;
    }
    
    public Mono<ChatResponse> chatAsync(String message, String conversationId) {
        return Mono.fromCallable(() -> {
            String modelName = extractModelName(chatModel);
            Timer.Sample sample = metricsCollector.startChatTimer(modelName);
            
            try {
                metricsCollector.incrementActiveConversations();
                
                Prompt prompt = new Prompt(new UserMessage(message));
                ChatResponse response = chatModel.call(prompt);
                
                // Record metrics
                recordResponseMetrics(response, modelName, "success");
                metricsCollector.incrementChatRequests(modelName, "success");
                
                return response;
                
            } catch (Exception e) {
                metricsCollector.incrementChatRequests(modelName, "error");
                meterRegistry.counter("ai.chat.errors", 
                    "model", modelName, 
                    "error", e.getClass().getSimpleName()
                ).increment();
                throw e;
            } finally {
                sample.stop(chatResponseTimer);
                metricsCollector.decrementActiveConversations();
            }
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    private void recordResponseMetrics(ChatResponse response, String modelName, String status) {
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            Usage usage = response.getMetadata().getUsage();
            
            metricsCollector.recordTokenUsage(
                usage.getPromptTokens(), modelName, "input"
            );
            metricsCollector.recordTokenUsage(
                usage.getGenerationTokens(), modelName, "output"
            );
            metricsCollector.recordTokenUsage(
                usage.getTotalTokens(), modelName, "total"
            );
        }
    }
    
    private String extractModelName(ChatModel chatModel) {
        // Extract model name from ChatModel implementation
        return chatModel.getClass().getSimpleName();
    }
}
```

## 9.2 Logging and Tracing

### Structured Logging

#### Custom Logging Configuration

```java
@Configuration
@EnableConfigurationProperties(AILoggingProperties.class)
public class AILoggingConfiguration {
    
    @Bean
    public AIRequestLogger aiRequestLogger(AILoggingProperties properties) {
        return new AIRequestLogger(properties);
    }
}

@ConfigurationProperties(prefix = "ai.logging")
@Data
public class AILoggingProperties {
    private boolean enabled = true;
    private boolean logRequests = true;
    private boolean logResponses = true;
    private boolean logTokenUsage = true;
    private boolean maskSensitiveData = true;
    private int maxContentLength = 1000;
}
```

#### Structured Request/Response Logger

```java
@Component
public class AIRequestLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(AIRequestLogger.class);
    private final ObjectMapper objectMapper;
    private final AILoggingProperties properties;
    
    public AIRequestLogger(AILoggingProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }
    
    public void logChatRequest(String conversationId, Prompt prompt, String modelName) {
        if (!properties.isEnabled() || !properties.isLogRequests()) {
            return;
        }
        
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("event", "ai_chat_request");
            logData.put("conversationId", conversationId);
            logData.put("modelName", modelName);
            logData.put("timestamp", Instant.now().toString());
            logData.put("messageCount", prompt.getInstructions().size());
            
            if (properties.isLogRequests()) {
                String content = prompt.getInstructions().stream()
                    .map(this::extractMessageContent)
                    .collect(Collectors.joining(" "));
                    
                logData.put("content", truncateAndMask(content));
            }
            
            logger.info("AI Request: {}", objectMapper.writeValueAsString(logData));
            
        } catch (Exception e) {
            logger.warn("Failed to log AI request", e);
        }
    }
    
    public void logChatResponse(String conversationId, ChatResponse response, 
                               Duration duration, String modelName) {
        if (!properties.isEnabled() || !properties.isLogResponses()) {
            return;
        }
        
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("event", "ai_chat_response");
            logData.put("conversationId", conversationId);
            logData.put("modelName", modelName);
            logData.put("timestamp", Instant.now().toString());
            logData.put("duration", duration.toMillis());
            logData.put("success", true);
            
            if (response.getResult() != null) {
                String content = response.getResult().getOutput().getContent();
                logData.put("responseContent", truncateAndMask(content));
                logData.put("finishReason", response.getResult().getMetadata().getFinishReason());
            }
            
            if (properties.isLogTokenUsage() && response.getMetadata() != null) {
                Usage usage = response.getMetadata().getUsage();
                if (usage != null) {
                    Map<String, Object> tokenUsage = new HashMap<>();
                    tokenUsage.put("promptTokens", usage.getPromptTokens());
                    tokenUsage.put("generationTokens", usage.getGenerationTokens());
                    tokenUsage.put("totalTokens", usage.getTotalTokens());
                    logData.put("tokenUsage", tokenUsage);
                }
            }
            
            logger.info("AI Response: {}", objectMapper.writeValueAsString(logData));
            
        } catch (Exception e) {
            logger.warn("Failed to log AI response", e);
        }
    }
    
    public void logChatError(String conversationId, String modelName, 
                            Exception error, Duration duration) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("event", "ai_chat_error");
            logData.put("conversationId", conversationId);
            logData.put("modelName", modelName);
            logData.put("timestamp", Instant.now().toString());
            logData.put("duration", duration.toMillis());
            logData.put("success", false);
            logData.put("errorType", error.getClass().getSimpleName());
            logData.put("errorMessage", error.getMessage());
            
            logger.error("AI Error: {}", objectMapper.writeValueAsString(logData), error);
            
        } catch (Exception e) {
            logger.error("Failed to log AI error", e);
        }
    }
    
    private String extractMessageContent(Message message) {
        if (message instanceof UserMessage) {
            return ((UserMessage) message).getContent();
        } else if (message instanceof SystemMessage) {
            return ((SystemMessage) message).getContent();
        }
        return message.toString();
    }
    
    private String truncateAndMask(String content) {
        if (content == null) return null;
        
        // Truncate if too long
        if (content.length() > properties.getMaxContentLength()) {
            content = content.substring(0, properties.getMaxContentLength()) + "...";
        }
        
        // Mask sensitive data if enabled
        if (properties.isMaskSensitiveData()) {
            content = maskSensitiveInformation(content);
        }
        
        return content;
    }
    
    private String maskSensitiveInformation(String content) {
        // Mask email addresses
        content = content.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "***@***.***");
        
        // Mask phone numbers (basic pattern)
        content = content.replaceAll("\\b\\d{3}-\\d{3}-\\d{4}\\b", "***-***-****");
        
        // Mask credit card numbers (basic pattern)
        content = content.replaceAll("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4}\\b", "**** **** **** ****");
        
        return content;
    }
}
```

### Distributed Tracing

#### OpenTelemetry Integration

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-jaeger</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```java
@Configuration
public class TracingConfiguration {
    
    @Bean
    public OpenTelemetry openTelemetry() {
        return OpenTelemetryConfigBuilder.create()
            .setServiceName("spring-ai-application")
            .setServiceVersion("1.0.0")
            .build();
    }
    
    @Bean
    public AITracer aiTracer(OpenTelemetry openTelemetry) {
        return new AITracer(openTelemetry.getTracer("ai-operations"));
    }
}
```

#### Custom AI Tracer

```java
@Component
public class AITracer {
    
    private final Tracer tracer;
    
    public AITracer(Tracer tracer) {
        this.tracer = tracer;
    }
    
    public <T> T traceChatCall(String operationName, String modelName, 
                               String conversationId, Callable<T> operation) {
        Span span = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("ai.model.name", modelName)
            .setAttribute("ai.conversation.id", conversationId)
            .setAttribute("ai.operation.type", "chat")
            .startSpan();
            
        try (Scope scope = span.makeCurrent()) {
            T result = operation.call();
            
            if (result instanceof ChatResponse) {
                ChatResponse response = (ChatResponse) result;
                addResponseAttributes(span, response);
            }
            
            span.setStatus(StatusCode.OK);
            return result;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }
    
    public <T> T traceEmbeddingCall(String operationName, String modelName, 
                                   int documentCount, Callable<T> operation) {
        Span span = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("ai.model.name", modelName)
            .setAttribute("ai.operation.type", "embedding")
            .setAttribute("ai.document.count", documentCount)
            .startSpan();
            
        try (Scope scope = span.makeCurrent()) {
            T result = operation.call();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }
    
    public <T> T traceVectorSearch(String operationName, String query, 
                                  int topK, Callable<T> operation) {
        Span span = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("ai.operation.type", "vector_search")
            .setAttribute("ai.search.query", truncateString(query, 100))
            .setAttribute("ai.search.top_k", topK)
            .startSpan();
            
        try (Scope scope = span.makeCurrent()) {
            T result = operation.call();
            
            if (result instanceof List) {
                span.setAttribute("ai.search.results_count", ((List<?>) result).size());
            }
            
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }
    
    private void addResponseAttributes(Span span, ChatResponse response) {
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            Usage usage = response.getMetadata().getUsage();
            span.setAttribute("ai.tokens.prompt", usage.getPromptTokens());
            span.setAttribute("ai.tokens.completion", usage.getGenerationTokens());
            span.setAttribute("ai.tokens.total", usage.getTotalTokens());
        }
        
        if (response.getResult() != null && response.getResult().getMetadata() != null) {
            span.setAttribute("ai.finish_reason", 
                response.getResult().getMetadata().getFinishReason());
        }
    }
    
    private String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}
```

## 9.3 Cost and Usage Tracking

### Token Usage Monitoring

```java
@Service
public class TokenUsageTracker {
    
    private final MeterRegistry meterRegistry;
    private final TokenCostCalculator costCalculator;
    
    // Counters for different token types
    private final Counter promptTokenCounter;
    private final Counter completionTokenCounter;
    private final Counter totalTokenCounter;
    
    // Cost tracking
    private final DistributionSummary costPerRequest;
    
    public TokenUsageTracker(MeterRegistry meterRegistry, TokenCostCalculator costCalculator) {
        this.meterRegistry = meterRegistry;
        this.costCalculator = costCalculator;
        
        this.promptTokenCounter = Counter.builder("ai.tokens.prompt")
            .description("Number of prompt tokens used")
            .register(meterRegistry);
            
        this.completionTokenCounter = Counter.builder("ai.tokens.completion")
            .description("Number of completion tokens generated")
            .register(meterRegistry);
            
        this.totalTokenCounter = Counter.builder("ai.tokens.total")
            .description("Total number of tokens used")
            .register(meterRegistry);
            
        this.costPerRequest = DistributionSummary.builder("ai.cost.per_request")
            .description("Cost per AI request in USD")
            .baseUnit("usd")
            .register(meterRegistry);
    }
    
    public void trackUsage(ChatResponse response, String modelName, String userId) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return;
        }
        
        Usage usage = response.getMetadata().getUsage();
        Tags tags = Tags.of(
            "model", modelName,
            "user", userId
        );
        
        // Track token counts
        promptTokenCounter.increment(tags, usage.getPromptTokens());
        completionTokenCounter.increment(tags, usage.getGenerationTokens());
        totalTokenCounter.increment(tags, usage.getTotalTokens());
        
        // Calculate and track cost
        double cost = costCalculator.calculateCost(modelName, usage);
        costPerRequest.record(cost, tags);
        
        // Store detailed usage record
        storeUsageRecord(new UsageRecord(
            UUID.randomUUID().toString(),
            userId,
            modelName,
            usage.getPromptTokens(),
            usage.getGenerationTokens(),
            usage.getTotalTokens(),
            cost,
            Instant.now()
        ));
    }
    
    private void storeUsageRecord(UsageRecord record) {
        // Store in database for detailed analysis
        // This could be async to avoid impacting response times
        CompletableFuture.runAsync(() -> {
            // Save to database
        });
    }
    
    @Data
    @AllArgsConstructor
    public static class UsageRecord {
        private String id;
        private String userId;
        private String modelName;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private double cost;
        private Instant timestamp;
    }
}
```

#### Token Cost Calculator

```java
@Component
public class TokenCostCalculator {
    
    private final Map<String, ModelPricing> pricingMap;
    
    public TokenCostCalculator() {
        this.pricingMap = initializePricing();
    }
    
    public double calculateCost(String modelName, Usage usage) {
        ModelPricing pricing = pricingMap.get(modelName.toLowerCase());
        if (pricing == null) {
            // Default pricing or throw exception
            return 0.0;
        }
        
        double promptCost = (usage.getPromptTokens() / 1000.0) * pricing.getPromptPricePer1kTokens();
        double completionCost = (usage.getGenerationTokens() / 1000.0) * pricing.getCompletionPricePer1kTokens();
        
        return promptCost + completionCost;
    }
    
    private Map<String, ModelPricing> initializePricing() {
        Map<String, ModelPricing> pricing = new HashMap<>();
        
        // OpenAI GPT-4 pricing (as of 2024)
        pricing.put("gpt-4", new ModelPricing(0.03, 0.06));
        pricing.put("gpt-4-turbo", new ModelPricing(0.01, 0.03));
        pricing.put("gpt-3.5-turbo", new ModelPricing(0.0015, 0.002));
        
        // Add more models as needed
        
        return pricing;
    }
    
    @Data
    @AllArgsConstructor
    private static class ModelPricing {
        private double promptPricePer1kTokens;
        private double completionPricePer1kTokens;
    }
}
```

### Rate Limiting and Quotas

```java
@Component
public class AIRateLimiter {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final AIRateLimitProperties properties;
    private final MeterRegistry meterRegistry;
    
    public AIRateLimiter(RedisTemplate<String, String> redisTemplate,
                        AIRateLimitProperties properties,
                        MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }
    
    public boolean isAllowed(String userId, String operation) {
        String key = generateRateLimitKey(userId, operation);
        
        try {
            String script = """
                local key = KEYS[1]
                local limit = tonumber(ARGV[1])
                local window = tonumber(ARGV[2])
                local current = redis.call('incr', key)
                if current == 1 then
                    redis.call('expire', key, window)
                end
                return current
                """;
            
            Long current = redisTemplate.execute(
                (RedisConnection connection) -> {
                    Jedis jedis = (Jedis) connection.getNativeConnection();
                    return (Long) jedis.eval(script, 
                        Collections.singletonList(key),
                        Arrays.asList(
                            String.valueOf(properties.getRequestsPerMinute()),
                            String.valueOf(60)
                        )
                    );
                }
            );
            
            boolean allowed = current != null && current <= properties.getRequestsPerMinute();
            
            // Record metrics
            meterRegistry.counter("ai.rate_limit.checks",
                "user", userId,
                "operation", operation,
                "allowed", String.valueOf(allowed)
            ).increment();
            
            if (!allowed) {
                meterRegistry.counter("ai.rate_limit.exceeded",
                    "user", userId,
                    "operation", operation
                ).increment();
            }
            
            return allowed;
            
        } catch (Exception e) {
            // If Redis fails, allow the request (fail open)
            meterRegistry.counter("ai.rate_limit.errors").increment();
            return true;
        }
    }
    
    public TokenBucket getTokenBucket(String userId) {
        // Implement token bucket algorithm for more sophisticated rate limiting
        return new TokenBucket(
            properties.getTokensPerMinute(),
            Duration.ofMinutes(1),
            userId
        );
    }
    
    private String generateRateLimitKey(String userId, String operation) {
        return String.format("rate_limit:%s:%s:%d", 
            userId, operation, System.currentTimeMillis() / 60000);
    }
}

@ConfigurationProperties(prefix = "ai.rate-limit")
@Data
public class AIRateLimitProperties {
    private int requestsPerMinute = 60;
    private int tokensPerMinute = 10000;
    private boolean enabled = true;
    private List<String> exemptUsers = new ArrayList<>();
}
```

### Circuit Breaker Implementation

```java
@Component
public class AICircuitBreaker {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    
    public AICircuitBreaker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(createCircuitBreakerConfig());
        
        // Register metrics for circuit breaker events
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
            circuitBreaker.getEventPublisher().onEvent(event -> {
                meterRegistry.counter("ai.circuit_breaker.events",
                    "name", circuitBreaker.getName(),
                    "type", event.getEventType().toString()
                ).increment();
            });
        });
    }
    
    public <T> T executeWithCircuitBreaker(String name, Supplier<T> operation) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
        
        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException e) {
            meterRegistry.counter("ai.circuit_breaker.rejected", "name", name).increment();
            throw new ServiceUnavailableException("AI service temporarily unavailable: " + name);
        }
    }
    
    private CircuitBreakerConfig createCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(30))
            .build();
    }
    
    public CircuitBreakerRegistry getRegistry() {
        return circuitBreakerRegistry;
    }
}
```

### Complete REST Controller with Observability

```java
@RestController
@RequestMapping("/api/chat")
public class MonitoredChatController {
    
    private final ChatModel chatModel;
    private final AIMetricsCollector metricsCollector;
    private final AIRequestLogger requestLogger;
    private final AITracer aiTracer;
    private final TokenUsageTracker usageTracker;
    private final AIRateLimiter rateLimiter;
    private final AICircuitBreaker circuitBreaker;
    
    public MonitoredChatController(ChatModel chatModel,
                                  AIMetricsCollector metricsCollector,
                                  AIRequestLogger requestLogger,
                                  AITracer aiTracer,
                                  TokenUsageTracker usageTracker,
                                  AIRateLimiter rateLimiter,
                                  AICircuitBreaker circuitBreaker) {
        this.chatModel = chatModel;
        this.metricsCollector = metricsCollector;
        this.requestLogger = requestLogger;
        this.aiTracer = aiTracer;
        this.usageTracker = usageTracker;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
    }
    
    @PostMapping("/message")
    public ResponseEntity<ChatResponseDto> sendMessage(
            @RequestBody @Valid ChatRequestDto request,
            @RequestHeader(value = "X-User-ID", required = true) String userId,
            @RequestHeader(value = "X-Conversation-ID", required = false) String conversationId) {
        
        // Generate conversation ID if not provided
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
        }
        
        // Check rate limiting
        if (!rateLimiter.isAllowed(userId, "chat")) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ChatResponseDto("Rate limit exceeded. Please try again later.", conversationId));
        }
        
        return aiTracer.traceChatCall("chat_completion", "gpt-4", conversationId, () -> {
            return circuitBreaker.executeWithCircuitBreaker("openai-chat", () -> {
                Instant startTime = Instant.now();
                String modelName = extractModelName();
                
                // Log request
                Prompt prompt = new Prompt(new UserMessage(request.getMessage()));
                requestLogger.logChatRequest(conversationId, prompt, modelName);
                
                try {
                    // Execute chat call
                    ChatResponse response = chatModel.call(prompt);
                    Duration duration = Duration.between(startTime, Instant.now());
                    
                    // Log successful response
                    requestLogger.logChatResponse(conversationId, response, duration, modelName);
                    
                    // Track usage and metrics
                    usageTracker.trackUsage(response, modelName, userId);
                    metricsCollector.incrementChatRequests(modelName, "success");
                    
                    // Create response DTO
                    ChatResponseDto responseDto = new ChatResponseDto(
                        response.getResult().getOutput().getContent(),
                        conversationId,
                        createMetadataDto(response)
                    );
                    
                    return ResponseEntity.ok(responseDto);
                    
                } catch (Exception e) {
                    Duration duration = Duration.between(startTime, Instant.now());
                    requestLogger.logChatError(conversationId, modelName, e, duration);
                    metricsCollector.incrementChatRequests(modelName, "error");
                    
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ChatResponseDto("An error occurred processing your request.", conversationId));
                }
            });
        });
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("timestamp", Instant.now());
        health.put("version", getClass().getPackage().getImplementationVersion());
        
        // Add circuit breaker status
        Map<String, String> circuitBreakers = new HashMap<>();
        circuitBreaker.getRegistry().getAllCircuitBreakers().forEach(cb -> {
            circuitBreakers.put(cb.getName(), cb.getState().toString());
        });
        health.put("circuitBreakers", circuitBreakers);
        
        return ResponseEntity.ok(health);
    }
    
    private String extractModelName() {
        // Extract model name from configuration or ChatModel
        return "gpt-4"; // This should come from configuration
    }
    
    private MetadataDto createMetadataDto(ChatResponse response) {
        if (response.getMetadata() == null) {
            return null;
        }
        
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        
        return new MetadataDto(
            usage.getPromptTokens(),
            usage.getGenerationTokens(),
            usage.getTotalTokens()
        );
    }
}

// DTOs
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatRequestDto {
    @NotBlank(message = "Message cannot be empty")
    @Size(max = 4000, message = "Message too long")
    private String message;
    
    private Map<String, Object> context;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponseDto {
    private String response;
    private String conversationId;
    private MetadataDto metadata;
    
    public ChatResponseDto(String response, String conversationId) {
        this.response = response;
        this.conversationId = conversationId;
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetadataDto {
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
}
```

### Advanced Monitoring Dashboard Configuration

```java
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    
    private final MeterRegistry meterRegistry;
    private final TokenUsageTracker usageTracker;
    private final AICircuitBreaker circuitBreaker;
    
    public MonitoringController(MeterRegistry meterRegistry,
                               TokenUsageTracker usageTracker,
                               AICircuitBreaker circuitBreaker) {
        this.meterRegistry = meterRegistry;
        this.usageTracker = usageTracker;
        this.circuitBreaker = circuitBreaker;
    }
    
    @GetMapping("/metrics/summary")
    public ResponseEntity<MetricsSummaryDto> getMetricsSummary() {
        MetricsSummaryDto summary = new MetricsSummaryDto();
        
        // Get chat request metrics
        Counter chatRequests = meterRegistry.find("ai.chat.requests.total").counter();
        summary.setTotalChatRequests(chatRequests != null ? (long) chatRequests.count() : 0);
        
        // Get token usage metrics
        Counter totalTokens = meterRegistry.find("ai.tokens.total").counter();
        summary.setTotalTokensUsed(totalTokens != null ? (long) totalTokens.count() : 0);
        
        // Get response time metrics
        Timer responseTimer = meterRegistry.find("ai.chat.response.duration").timer();
        if (responseTimer != null) {
            summary.setAverageResponseTime(responseTimer.mean(TimeUnit.MILLISECONDS));
            summary.setMaxResponseTime(responseTimer.max(TimeUnit.MILLISECONDS));
        }
        
        // Get active conversations
        Gauge activeConversations = meterRegistry.find("ai.conversations.active").gauge();
        summary.setActiveConversations(activeConversations != null ? (int) activeConversations.value() : 0);
        
        // Get error rate
        Counter errors = meterRegistry.find("ai.chat.errors").counter();
        summary.setErrorRate(calculateErrorRate(chatRequests, errors));
        
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/metrics/detailed")
    public ResponseEntity<Map<String, Object>> getDetailedMetrics(
            @RequestParam(defaultValue = "1h") String timeRange) {
        
        Map<String, Object> metrics = new HashMap<>();
        
        // Token usage by model
        Map<String, Long> tokensByModel = new HashMap<>();
        meterRegistry.find("ai.tokens.total")
            .tagKeys("model")
            .counters()
            .forEach(counter -> {
                String model = counter.getId().getTag("model");
                tokensByModel.put(model, (long) counter.count());
            });
        metrics.put("tokensByModel", tokensByModel);
        
        // Request count by status
        Map<String, Long> requestsByStatus = new HashMap<>();
        meterRegistry.find("ai.chat.requests.total")
            .tagKeys("status")
            .counters()
            .forEach(counter -> {
                String status = counter.getId().getTag("status");
                requestsByStatus.put(status, (long) counter.count());
            });
        metrics.put("requestsByStatus", requestsByStatus);
        
        // Circuit breaker states
        Map<String, String> circuitBreakerStates = new HashMap<>();
        circuitBreaker.getRegistry().getAllCircuitBreakers().forEach(cb -> {
            circuitBreakerStates.put(cb.getName(), cb.getState().toString());
        });
        metrics.put("circuitBreakerStates", circuitBreakerStates);
        
        return ResponseEntity.ok(metrics);
    }
    
    @GetMapping("/usage/report")
    public ResponseEntity<UsageReportDto> getUsageReport(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "24h") String timeRange) {
        
        // This would typically query a database for detailed usage records
        UsageReportDto report = new UsageReportDto();
        
        // Calculate time range
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(parseDuration(timeRange));
        
        // Get metrics for the time range
        // In a real implementation, you'd query stored usage records
        report.setPeriodStart(startTime);
        report.setPeriodEnd(endTime);
        report.setTotalRequests(getTotalRequestsInPeriod(startTime, endTime, userId));
        report.setTotalTokens(getTotalTokensInPeriod(startTime, endTime, userId));
        report.setTotalCost(getTotalCostInPeriod(startTime, endTime, userId));
        
        return ResponseEntity.ok(report);
    }
    
    private double calculateErrorRate(Counter totalRequests, Counter errors) {
        if (totalRequests == null || errors == null || totalRequests.count() == 0) {
            return 0.0;
        }
        return (errors.count() / totalRequests.count()) * 100;
    }
    
    private Duration parseDuration(String timeRange) {
        // Simple parser for time ranges like "1h", "24h", "7d"
        if (timeRange.endsWith("h")) {
            int hours = Integer.parseInt(timeRange.substring(0, timeRange.length() - 1));
            return Duration.ofHours(hours);
        } else if (timeRange.endsWith("d")) {
            int days = Integer.parseInt(timeRange.substring(0, timeRange.length() - 1));
            return Duration.ofDays(days);
        }
        return Duration.ofHours(1); // default
    }
    
    // These would be implemented to query actual stored data
    private long getTotalRequestsInPeriod(Instant start, Instant end, String userId) {
        // Query database for actual usage data
        return 0;
    }
    
    private long getTotalTokensInPeriod(Instant start, Instant end, String userId) {
        // Query database for actual usage data
        return 0;
    }
    
    private double getTotalCostInPeriod(Instant start, Instant end, String userId) {
        // Query database for actual usage data
        return 0.0;
    }
}

// Monitoring DTOs
@Data
public class MetricsSummaryDto {
    private long totalChatRequests;
    private long totalTokensUsed;
    private double averageResponseTime;
    private double maxResponseTime;
    private int activeConversations;
    private double errorRate;
    private Instant timestamp = Instant.now();
}

@Data
public class UsageReportDto {
    private Instant periodStart;
    private Instant periodEnd;
    private long totalRequests;
    private long totalTokens;
    private double totalCost;
    private Map<String, Long> requestsByModel = new HashMap<>();
    private Map<String, Long> tokensByModel = new HashMap<>();
    private Map<String, Double> costByModel = new HashMap<>();
}
```

### Grafana Dashboard Configuration

```yaml
# grafana-dashboard.json (simplified structure)
dashboard:
  title: "Spring AI Monitoring Dashboard"
  panels:
    - title: "Chat Requests per Minute"
      type: "graph"
      targets:
        - expr: "rate(ai_chat_requests_total[5m])"
          legendFormat: "{{model}} - {{status}}"
    
    - title: "Token Usage"
      type: "graph" 
      targets:
        - expr: "rate(ai_tokens_total[5m])"
          legendFormat: "{{model}} - {{type}}"
    
    - title: "Response Time Distribution"
      type: "graph"
      targets:
        - expr: "histogram_quantile(0.95, rate(ai_chat_response_duration_bucket[5m]))"
          legendFormat: "95th percentile"
        - expr: "histogram_quantile(0.50, rate(ai_chat_response_duration_bucket[5m]))"
          legendFormat: "50th percentile"
    
    - title: "Error Rate"
      type: "singlestat"
      targets:
        - expr: "rate(ai_chat_errors[5m]) / rate(ai_chat_requests_total[5m]) * 100"
    
    - title: "Cost per Hour"
      type: "graph"
      targets:
        - expr: "rate(ai_cost_per_request[1h])"
          legendFormat: "{{model}}"
    
    - title: "Circuit Breaker States"
      type: "table"
      targets:
        - expr: "ai_circuit_breaker_events"
          legendFormat: "{{name}} - {{type}}"
```

### Alerting Configuration

```java
@Component
public class AIAlertingService {
    
    private final MeterRegistry meterRegistry;
    private final NotificationService notificationService;
    private final AlertingProperties alertingProperties;
    
    @Scheduled(fixedRate = 60000) // Check every minute
    public void checkAlerts() {
        checkErrorRateAlert();
        checkResponseTimeAlert();
        checkCostAlert();
        checkCircuitBreakerAlert();
    }
    
    private void checkErrorRateAlert() {
        Counter totalRequests = meterRegistry.find("ai.chat.requests.total").counter();
        Counter errors = meterRegistry.find("ai.chat.errors").counter();
        
        if (totalRequests != null && errors != null) {
            double errorRate = (errors.count() / totalRequests.count()) * 100;
            
            if (errorRate > alertingProperties.getErrorRateThreshold()) {
                notificationService.sendAlert(
                    AlertLevel.CRITICAL,
                    "High Error Rate",
                    String.format("AI service error rate is %.2f%%, exceeding threshold of %.2f%%", 
                        errorRate, alertingProperties.getErrorRateThreshold())
                );
            }
        }
    }
    
    private void checkResponseTimeAlert() {
        Timer responseTimer = meterRegistry.find("ai.chat.response.duration").timer();
        
        if (responseTimer != null) {
            double avgResponseTime = responseTimer.mean(TimeUnit.MILLISECONDS);
            
            if (avgResponseTime > alertingProperties.getResponseTimeThreshold()) {
                notificationService.sendAlert(
                    AlertLevel.WARNING,
                    "High Response Time",
                    String.format("Average response time is %.2fms, exceeding threshold of %.2fms",
                        avgResponseTime, alertingProperties.getResponseTimeThreshold())
                );
            }
        }
    }
    
    private void checkCostAlert() {
        // Check if hourly cost exceeds threshold
        DistributionSummary costSummary = meterRegistry.find("ai.cost.per_request").summary();
        
        if (costSummary != null) {
            double totalCost = costSummary.totalAmount();
            
            if (totalCost > alertingProperties.getCostThreshold()) {
                notificationService.sendAlert(
                    AlertLevel.WARNING,
                    "High Cost Alert",
                    String.format("Total cost is $%.2f, exceeding threshold of $%.2f",
                        totalCost, alertingProperties.getCostThreshold())
                );
            }
        }
    }
    
    private void checkCircuitBreakerAlert() {
        // Check for open circuit breakers
        // Implementation would check circuit breaker states and alert if any are open
    }
}

@ConfigurationProperties(prefix = "ai.alerting")
@Data
public class AlertingProperties {
    private double errorRateThreshold = 5.0; // 5%
    private double responseTimeThreshold = 10000; // 10 seconds
    private double costThreshold = 100.0; // $100
    private boolean enabled = true;
}

public enum AlertLevel {
    INFO, WARNING, CRITICAL
}

@Service
public class NotificationService {
    
    public void sendAlert(AlertLevel level, String title, String message) {
        // Implementation would send alerts via email, Slack, etc.
        System.err.printf("[%s] %s: %s%n", level, title, message);
    }
}
```

## Real-World Use Cases and Examples

### Use Case 1: E-commerce Chatbot Monitoring

```java
@Service
public class EcommerceChatService {
    
    private final ChatModel chatModel;
    private final AITracer tracer;
    private final ProductService productService;
    private final OrderService orderService;
    
    @Timed(name = "ecommerce.chat.duration", description = "E-commerce chat response time")
    @Counted(name = "ecommerce.chat.requests", description = "E-commerce chat requests")
    public ChatResponse handleCustomerQuery(String query, String customerId) {
        
        return tracer.traceChatCall("ecommerce_chat", "gpt-4", customerId, () -> {
            
            // Classify intent
            String intent = classifyIntent(query);
            
            // Track intent metrics
            Metrics.counter("ecommerce.chat.intents", "type", intent).increment();
            
            // Handle different intents with specific monitoring
            switch (intent) {
                case "product_inquiry":
                    return handleProductInquiry(query, customerId);
                case "order_status":
                    return handleOrderStatus(query, customerId);
                case "support":
                    return handleSupportQuery(query, customerId);
                default:
                    return handleGenericQuery(query, customerId);
            }
        });
    }
    
    @Timed(name = "ecommerce.product.inquiry.duration")
    private ChatResponse handleProductInquiry(String query, String customerId) {
        // Add product-specific context
        List<Product> relevantProducts = productService.searchProducts(query);
        
        Metrics.counter("ecommerce.product.inquiries").increment();
        Metrics.gauge("ecommerce.product.results", relevantProducts.size());
        
        // Create enhanced prompt with product data
        String enhancedPrompt = createProductInquiryPrompt(query, relevantProducts);
        
        return chatModel.call(new Prompt(enhancedPrompt));
    }
    
    // Additional methods...
}
```

### Use Case 2: Document Analysis Service Monitoring

```java
@Service
public class DocumentAnalysisService {
    
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final AITracer tracer;
    
    @Timed(name = "document.analysis.duration")
    public AnalysisResult analyzeDocument(MultipartFile document, String analysisType) {
        
        return tracer.traceEmbeddingCall("document_analysis", "text-embedding-ada-002", 1, () -> {
            
            // Extract text from document
            Timer.Sample extractionTimer = Timer.start();
            String content = extractTextFromDocument(document);
            extractionTimer.stop(Metrics.timer("document.extraction.duration"));
            
            // Track document metrics
            Metrics.counter("document.analysis.requests", 
                "type", analysisType,
                "format", getFileExtension(document.getOriginalFilename())
            ).increment();
            
            Metrics.gauge("document.size.bytes", document.getSize());
            Metrics.gauge("document.content.length", content.length());
            
            // Create embeddings
            List<Double> embeddings = embeddingModel.embed(content);
            
            // Find similar documents
            List<Document> similarDocs = vectorStore.similaritySearch(
                SearchRequest.query(content).withTopK(5)
            );
            
            Metrics.gauge("document.similar.found", similarDocs.size());
            
            // Generate analysis
            String analysisPrompt = createAnalysisPrompt(content, analysisType, similarDocs);
            ChatResponse analysis = chatModel.call(new Prompt(analysisPrompt));
            
            return new AnalysisResult(
                document.getOriginalFilename(),
                analysisType,
                analysis.getResult().getOutput().getContent(),
                similarDocs.size(),
                Instant.now()
            );
        });
    }
    
    // Helper methods...
}
```

This comprehensive Phase 9 guide covers all aspects of observability and monitoring for Spring AI applications, including metrics collection, structured logging, distributed tracing, cost tracking, rate limiting, circuit breakers, and real-world monitoring scenarios. The examples show how to implement production-ready monitoring that helps you understand performance, costs, and system health of your AI-powered applications.