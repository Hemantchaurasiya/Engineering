## Phase 11: Advanced Patterns and Customization (Week 15-16)

## 11.1 Custom Model Integration
### Local Model Deployment with Ollama
Ollama allows you to run LLMs locally, which is perfect for development, privacy-sensitive applications, or cost optimization.

```java
// application.yml
/*
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama2
          temperature: 0.7
          top-p: 0.9
          num-predict: 1000
        enabled: true
      embedding:
        options:
          model: nomic-embed-text
        enabled: true
*/

@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.ollama.chat", name = "enabled", havingValue = "true")
    public OllamaChatModel ollamaChatModel(OllamaProperties properties) {
        return OllamaChatModel.builder()
            .withBaseUrl(properties.getBaseUrl())
            .withModel(properties.getChat().getOptions().getModel())
            .withTemperature(properties.getChat().getOptions().getTemperature())
            .withTopP(properties.getChat().getOptions().getTopP())
            .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.ollama.embedding", name = "enabled", havingValue = "true")
    public OllamaEmbeddingModel ollamaEmbeddingModel(OllamaProperties properties) {
        return OllamaEmbeddingModel.builder()
            .withBaseUrl(properties.getBaseUrl())
            .withModel(properties.getEmbedding().getOptions().getModel())
            .build();
    }
}

@ConfigurationProperties(prefix = "spring.ai.ollama")
@Data
public class OllamaProperties {
    private String baseUrl = "http://localhost:11434";
    private ChatProperties chat = new ChatProperties();
    private EmbeddingProperties embedding = new EmbeddingProperties();

    @Data
    public static class ChatProperties {
        private boolean enabled = true;
        private OptionsProperties options = new OptionsProperties();
    }

    @Data
    public static class EmbeddingProperties {
        private boolean enabled = true;
        private OptionsProperties options = new OptionsProperties();
    }

    @Data
    public static class OptionsProperties {
        private String model = "llama2";
        private Double temperature = 0.7;
        private Double topP = 0.9;
        private Integer numPredict = 1000;
    }
}

@Service
@Slf4j
public class LocalModelService {

    private final OllamaChatModel chatModel;
    private final OllamaEmbeddingModel embeddingModel;

    public LocalModelService(OllamaChatModel chatModel, OllamaEmbeddingModel embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    public ChatResponse generateResponse(String prompt) {
        try {
            log.info("Generating response for prompt: {}", prompt.substring(0, Math.min(prompt.length(), 100)));
            
            ChatRequest request = ChatRequest.builder()
                .withMessages(List.of(new UserMessage(prompt)))
                .withOptions(OllamaOptions.create()
                    .withTemperature(0.7f)
                    .withTopP(0.9f)
                    .withNumPredict(1000))
                .build();

            ChatResponse response = chatModel.call(request);
            log.info("Response generated successfully");
            return response;
            
        } catch (Exception e) {
            log.error("Error generating response: ", e);
            throw new RuntimeException("Failed to generate response", e);
        }
    }

    public List<Double> generateEmbedding(String text) {
        try {
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), 
                OllamaOptions.create().withModel("nomic-embed-text"));
            
            EmbeddingResponse response = embeddingModel.call(request);
            return response.getResults().get(0).getOutput();
            
        } catch (Exception e) {
            log.error("Error generating embedding: ", e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    public Flux<ChatResponse> streamResponse(String prompt) {
        try {
            Prompt streamPrompt = new Prompt(prompt, OllamaOptions.create()
                .withTemperature(0.7f)
                .withStream(true));
            
            return chatModel.stream(streamPrompt);
            
        } catch (Exception e) {
            log.error("Error streaming response: ", e);
            return Flux.error(new RuntimeException("Failed to stream response", e));
        }
    }
}

@RestController
@RequestMapping("/api/local-ai")
@Slf4j
public class LocalAIController {

    private final LocalModelService localModelService;

    public LocalAIController(LocalModelService localModelService) {
        this.localModelService = localModelService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        ChatResponse response = localModelService.generateResponse(request.getPrompt());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/embedding")
    public ResponseEntity<List<Double>> embedding(@RequestBody EmbeddingRequest request) {
        List<Double> embedding = localModelService.generateEmbedding(request.getText());
        return ResponseEntity.ok(embedding);
    }

    @PostMapping("/stream")
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request) {
        return localModelService.streamResponse(request.getPrompt())
            .map(response -> ServerSentEvent.builder(response.getResult().getOutput().getContent())
                .id(UUID.randomUUID().toString())
                .event("message")
                .build())
            .onErrorResume(throwable -> {
                log.error("Error in streaming: ", throwable);
                return Flux.just(ServerSentEvent.builder("Error occurred")
                    .event("error")
                    .build());
            });
    }
}

// Data classes
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class ChatRequest {
    private String prompt;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class EmbeddingRequest {
    private String text;
}
```

### Custom REST API Integration
Sometimes you need to integrate with custom AI models deployed via REST APIs. Here's how to create a flexible integration:

```java
@Component
public class CustomRestChatModel implements ChatModel {

    private final WebClient webClient;
    private final CustomModelProperties properties;
    private final RetryTemplate retryTemplate;

    public CustomRestChatModel(WebClient.Builder webClientBuilder, 
                              CustomModelProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

        this.retryTemplate = RetryTemplate.builder()
            .maxAttempts(properties.getMaxRetries())
            .exponentialBackoff(properties.getRetryDelay(), 2, properties.getMaxRetryDelay())
            .retryOn(WebClientResponseException.class)
            .build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return retryTemplate.execute(context -> {
            CustomModelRequest request = buildRequest(prompt);
            
            CustomModelResponse response = webClient.post()
                .uri(properties.getChatEndpoint())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> 
                    clientResponse.bodyToMono(String.class)
                        .map(body -> new RuntimeException("API Error: " + body)))
                .bodyToMono(CustomModelResponse.class)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .block();

            return convertToChatResponse(response, prompt.getInstructions());
        });
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        CustomModelRequest request = buildRequest(prompt);
        request.setStream(true);

        return webClient.post()
            .uri(properties.getStreamEndpoint())
            .bodyValue(request)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(String.class)
            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
            .filter(line -> !line.trim().isEmpty() && line.startsWith("data: "))
            .map(line -> line.substring(6)) // Remove "data: " prefix
            .filter(data -> !"[DONE]".equals(data))
            .map(this::parseStreamResponse)
            .map(response -> convertToChatResponse(response, prompt.getInstructions()));
    }

    private CustomModelRequest buildRequest(Prompt prompt) {
        CustomModelRequest request = new CustomModelRequest();
        request.setModel(properties.getModelName());
        request.setMessages(convertMessages(prompt.getInstructions()));
        request.setTemperature(extractTemperature(prompt));
        request.setMaxTokens(extractMaxTokens(prompt));
        request.setTopP(extractTopP(prompt));
        return request;
    }

    private List<CustomMessage> convertMessages(List<Message> messages) {
        return messages.stream()
            .map(message -> {
                CustomMessage customMessage = new CustomMessage();
                if (message instanceof UserMessage) {
                    customMessage.setRole("user");
                } else if (message instanceof AssistantMessage) {
                    customMessage.setRole("assistant");
                } else if (message instanceof SystemMessage) {
                    customMessage.setRole("system");
                }
                customMessage.setContent(message.getContent());
                return customMessage;
            })
            .collect(Collectors.toList());
    }

    private ChatResponse convertToChatResponse(CustomModelResponse response, List<Message> originalMessages) {
        AssistantMessage assistantMessage = new AssistantMessage(response.getChoices().get(0).getMessage().getContent());
        
        ChatResponse.Builder builder = ChatResponse.builder()
            .withGenerations(List.of(new Generation(assistantMessage)));

        // Add usage metadata if available
        if (response.getUsage() != null) {
            builder.withMetadata("usage", Map.of(
                "promptTokens", response.getUsage().getPromptTokens(),
                "completionTokens", response.getUsage().getCompletionTokens(),
                "totalTokens", response.getUsage().getTotalTokens()
            ));
        }

        return builder.build();
    }

    private CustomModelResponse parseStreamResponse(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, CustomModelResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse stream response", e);
        }
    }

    private Double extractTemperature(Prompt prompt) {
        return prompt.getOptions() instanceof CustomChatOptions ? 
            ((CustomChatOptions) prompt.getOptions()).getTemperature() : 
            properties.getDefaultTemperature();
    }

    private Integer extractMaxTokens(Prompt prompt) {
        return prompt.getOptions() instanceof CustomChatOptions ? 
            ((CustomChatOptions) prompt.getOptions()).getMaxTokens() : 
            properties.getDefaultMaxTokens();
    }

    private Double extractTopP(Prompt prompt) {
        return prompt.getOptions() instanceof CustomChatOptions ? 
            ((CustomChatOptions) prompt.getOptions()).getTopP() : 
            properties.getDefaultTopP();
    }
}

@ConfigurationProperties(prefix = "spring.ai.custom-model")
@Data
public class CustomModelProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName = "custom-llm-v1";
    private String chatEndpoint = "/v1/chat/completions";
    private String streamEndpoint = "/v1/chat/completions";
    private int timeoutSeconds = 60;
    private int maxRetries = 3;
    private long retryDelay = 1000L; // milliseconds
    private long maxRetryDelay = 10000L; // milliseconds
    private double defaultTemperature = 0.7;
    private int defaultMaxTokens = 1000;
    private double defaultTopP = 0.9;
}

// Request/Response DTOs
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class CustomModelRequest {
    private String model;
    private List<CustomMessage> messages;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Boolean stream = false;
}

@Data
class CustomMessage {
    private String role;
    private String content;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class CustomModelResponse {
    private List<CustomChoice> choices;
    private CustomUsage usage;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class CustomChoice {
    private CustomMessage message;
    private CustomDelta delta; // For streaming responses
    private String finishReason;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class CustomDelta {
    private String content;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class CustomUsage {
    @JsonProperty("prompt_tokens")
    private Integer promptTokens;
    
    @JsonProperty("completion_tokens")
    private Integer completionTokens;
    
    @JsonProperty("total_tokens")
    private Integer totalTokens;
}

// Custom options class
@Data
public class CustomChatOptions implements ChatOptions {
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private List<String> stopSequences;

    @Override
    public Double getTemperature() {
        return temperature;
    }

    @Override
    public Double getTopP() {
        return topP;
    }

    @Override
    public Integer getTopK() {
        return null; // Not supported by this model
    }

    public static CustomChatOptions create() {
        return new CustomChatOptions();
    }

    public CustomChatOptions withTemperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    public CustomChatOptions withMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public CustomChatOptions withTopP(Double topP) {
        this.topP = topP;
        return this;
    }

    public CustomChatOptions withStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
        return this;
    }
}

// Health check for custom model
@Component
public class CustomModelHealthIndicator implements HealthIndicator {

    private final CustomRestChatModel customModel;
    private final CustomModelProperties properties;

    public CustomModelHealthIndicator(CustomRestChatModel customModel, 
                                    CustomModelProperties properties) {
        this.customModel = customModel;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            // Simple health check prompt
            Prompt healthCheckPrompt = new Prompt("Hello", CustomChatOptions.create()
                .withMaxTokens(10)
                .withTemperature(0.1));

            ChatResponse response = customModel.call(healthCheckPrompt);
            
            if (response != null && !response.getResults().isEmpty()) {
                return Health.up()
                    .withDetail("model", properties.getModelName())
                    .withDetail("baseUrl", properties.getBaseUrl())
                    .withDetail("responseTime", "< 5s")
                    .build();
            } else {
                return Health.down()
                    .withDetail("reason", "Empty response from model")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("model", properties.getModelName())
                .build();
        }
    }
}
```

## 11.2 Model Switching and Routing
### Dynamic Model Selection
This is crucial for production systems where you need to route requests to different models based on various criteria:

```java
@Service
@Slf4j
public class ModelRoutingService {

    private final Map<String, ChatModel> availableModels;
    private final ModelSelectionStrategy selectionStrategy;
    private final ModelHealthMonitor healthMonitor;
    private final MeterRegistry meterRegistry;

    public ModelRoutingService(List<ChatModel> chatModels,
                              ModelSelectionStrategy selectionStrategy,
                              ModelHealthMonitor healthMonitor,
                              MeterRegistry meterRegistry) {
        this.availableModels = createModelMap(chatModels);
        this.selectionStrategy = selectionStrategy;
        this.healthMonitor = healthMonitor;
        this.meterRegistry = meterRegistry;
    }

    public ChatResponse routeAndCall(RouteablePrompt prompt) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String selectedModelId = selectionStrategy.selectModel(prompt, availableModels);
            ChatModel selectedModel = availableModels.get(selectedModelId);

            if (selectedModel == null) {
                throw new ModelNotFoundException("Model not found: " + selectedModelId);
            }

            if (!healthMonitor.isHealthy(selectedModelId)) {
                selectedModelId = selectionStrategy.selectFallbackModel(prompt, availableModels);
                selectedModel = availableModels.get(selectedModelId);
                
                if (selectedModel == null || !healthMonitor.isHealthy(selectedModelId)) {
                    throw new ModelUnavailableException("No healthy models available");
                }
            }

            log.info("Routing prompt to model: {}", selectedModelId);
            
            ChatResponse response = selectedModel.call(prompt.getPrompt());
            
            // Record metrics
            meterRegistry.counter("model.requests", "model", selectedModelId, "status", "success").increment();
            sample.stop(Timer.builder("model.response.time")
                .tag("model", selectedModelId)
                .register(meterRegistry));

            return response;
            
        } catch (Exception e) {
            meterRegistry.counter("model.requests", "model", "unknown", "status", "error").increment();
            log.error("Error routing prompt: ", e);
            throw new ModelRoutingException("Failed to route and execute prompt", e);
        }
    }

    public Flux<ChatResponse> routeAndStream(RouteablePrompt prompt) {
        String selectedModelId = selectionStrategy.selectModel(prompt, availableModels);
        ChatModel selectedModel = availableModels.get(selectedModelId);

        if (selectedModel == null) {
            return Flux.error(new ModelNotFoundException("Model not found: " + selectedModelId));
        }

        return selectedModel.stream(prompt.getPrompt())
            .doOnSubscribe(subscription -> log.info("Starting stream with model: {}", selectedModelId))
            .doOnNext(response -> meterRegistry.counter("model.stream.messages", "model", selectedModelId).increment())
            .doOnError(error -> meterRegistry.counter("model.stream.errors", "model", selectedModelId).increment());
    }

    private Map<String, ChatModel> createModelMap(List<ChatModel> chatModels) {
        Map<String, ChatModel> modelMap = new HashMap<>();
        
        for (ChatModel model : chatModels) {
            String modelId = determineModelId(model);
            modelMap.put(modelId, model);
        }
        
        return modelMap;
    }

    private String determineModelId(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return "openai";
        } else if (model instanceof OllamaChatModel) {
            return "ollama";
        } else if (model instanceof CustomRestChatModel) {
            return "custom";
        } else {
            return model.getClass().getSimpleName().toLowerCase();
        }
    }
}

@Component
public class ModelSelectionStrategy {

    private final ModelRoutingProperties properties;
    private final Random random = new Random();

    public ModelSelectionStrategy(ModelRoutingProperties properties) {
        this.properties = properties;
    }

    public String selectModel(RouteablePrompt prompt, Map<String, ChatModel> availableModels) {
        RouteablePrompt.RoutingCriteria criteria = prompt.getRoutingCriteria();

        // Strategy 1: Explicit model preference
        if (criteria.getPreferredModel() != null && 
            availableModels.containsKey(criteria.getPreferredModel())) {
            return criteria.getPreferredModel();
        }

        // Strategy 2: Task-based routing
        String taskBasedModel = selectByTaskType(criteria.getTaskType());
        if (taskBasedModel != null && availableModels.containsKey(taskBasedModel)) {
            return taskBasedModel;
        }

        // Strategy 3: Performance-based routing
        String performanceBasedModel = selectByPerformanceRequirements(criteria);
        if (performanceBasedModel != null && availableModels.containsKey(performanceBasedModel)) {
            return performanceBasedModel;
        }

        // Strategy 4: Cost-based routing
        String costBasedModel = selectByCostConstraints(criteria.getCostConstraint());
        if (costBasedModel != null && availableModels.containsKey(costBasedModel)) {
            return costBasedModel;
        }

        // Strategy 5: Load balancing
        return loadBalanceSelection(availableModels);
    }

    public String selectFallbackModel(RouteablePrompt prompt, Map<String, ChatModel> availableModels) {
        List<String> fallbackOrder = properties.getFallbackOrder();
        
        for (String modelId : fallbackOrder) {
            if (availableModels.containsKey(modelId)) {
                return modelId;
            }
        }
        
        // Last resort: any available model
        return availableModels.keySet().iterator().next();
    }

    private String selectByTaskType(TaskType taskType) {
        return switch (taskType) {
            case CHAT -> properties.getTaskMappings().getChat();
            case CODE_GENERATION -> properties.getTaskMappings().getCodeGeneration();
            case TEXT_ANALYSIS -> properties.getTaskMappings().getTextAnalysis();
            case CREATIVE_WRITING -> properties.getTaskMappings().getCreativeWriting();
            case TRANSLATION -> properties.getTaskMappings().getTranslation();
            case SUMMARIZATION -> properties.getTaskMappings().getSummarization();
            default -> null;
        };
    }

    private String selectByPerformanceRequirements(RouteablePrompt.RoutingCriteria criteria) {
        if (criteria.getMaxLatencyMs() != null && criteria.getMaxLatencyMs() < 2000) {
            return properties.getPerformanceTiers().getFast();
        } else if (criteria.getQualityRequirement() == QualityRequirement.HIGH) {
            return properties.getPerformanceTiers().getHighQuality();
        }
        return null;
    }

    private String selectByCostConstraints(CostConstraint costConstraint) {
        return switch (costConstraint) {
            case LOW -> properties.getCostTiers().getLow();
            case MEDIUM -> properties.getCostTiers().getMedium();
            case HIGH -> properties.getCostTiers().getHigh();
            default -> null;
        };
    }

    private String loadBalanceSelection(Map<String, ChatModel> availableModels) {
        List<String> modelIds = new ArrayList<>(availableModels.keySet());
        return modelIds.get(random.nextInt(modelIds.size()));
    }
}

@Data
public class RouteablePrompt {
    private final Prompt prompt;
    private final RoutingCriteria routingCriteria;

    public RouteablePrompt(String text, RoutingCriteria criteria) {
        this.prompt = new Prompt(text);
        this.routingCriteria = criteria;
    }

    public RouteablePrompt(Prompt prompt, RoutingCriteria criteria) {
        this.prompt = prompt;
        this.routingCriteria = criteria;
    }

    @Data
    @Builder
    public static class RoutingCriteria {
        private String preferredModel;
        private TaskType taskType;
        private QualityRequirement qualityRequirement;
        private CostConstraint costConstraint;
        private Integer maxLatencyMs;
        private String userId;
        private String sessionId;
        private Map<String, Object> metadata;

        public static RoutingCriteriaBuilder builder() {
            return new RoutingCriteriaBuilder();
        }
    }
}

public enum TaskType {
    CHAT,
    CODE_GENERATION,
    TEXT_ANALYSIS,
    CREATIVE_WRITING,
    TRANSLATION,
    SUMMARIZATION,
    QUESTION_ANSWERING,
    DATA_EXTRACTION
}

public enum QualityRequirement {
    LOW,
    MEDIUM,
    HIGH,
    PREMIUM
}

public enum CostConstraint {
    LOW,
    MEDIUM,
    HIGH,
    UNLIMITED
}

@Component
@Slf4j
public class ModelHealthMonitor {

    private final Map<String, ModelHealth> healthStatus = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @PostConstruct
    public void startHealthChecks() {
        scheduler.scheduleAtFixedRate(this::performHealthChecks, 0, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    public boolean isHealthy(String modelId) {
        ModelHealth health = healthStatus.get(modelId);
        return health != null && health.isHealthy();
    }

    public ModelHealth getHealth(String modelId) {
        return healthStatus.getOrDefault(modelId, ModelHealth.unknown());
    }

    public void recordSuccess(String modelId, long responseTimeMs) {
        ModelHealth health = healthStatus.computeIfAbsent(modelId, k -> new ModelHealth(k));
        health.recordSuccess(responseTimeMs);
    }

    public void recordFailure(String modelId, Exception error) {
        ModelHealth health = healthStatus.computeIfAbsent(modelId, k -> new ModelHealth(k));
        health.recordFailure(error);
    }

    private void performHealthChecks() {
        healthStatus.forEach((modelId, health) -> {
            // Update health status based on recent performance
            health.updateHealthStatus();
        });
    }

    @Data
    public static class ModelHealth {
        private final String modelId;
        private boolean healthy = true;
        private long lastSuccessTime = System.currentTimeMillis();
        private long lastFailureTime = 0;
        private int consecutiveFailures = 0;
        private double avgResponseTime = 0;
        private String lastError;

        public ModelHealth(String modelId) {
            this.modelId = modelId;
        }

        public static ModelHealth unknown() {
            ModelHealth health = new ModelHealth("unknown");
            health.healthy = false;
            return health;
        }

        public void recordSuccess(long responseTimeMs) {
            this.lastSuccessTime = System.currentTimeMillis();
            this.consecutiveFailures = 0;
            this.avgResponseTime = (avgResponseTime * 0.8) + (responseTimeMs * 0.2); // Exponential moving average
            this.healthy = true;
        }

        public void recordFailure(Exception error) {
            this.lastFailureTime = System.currentTimeMillis();
            this.consecutiveFailures++;
            this.lastError = error.getMessage();
            
            // Mark as unhealthy after 3 consecutive failures
            if (consecutiveFailures >= 3) {
                this.healthy = false;
            }
        }

        public void updateHealthStatus() {
            long now = System.currentTimeMillis();
            
            // If no recent activity, mark as unknown
            if (now - lastSuccessTime > 300000 && now - lastFailureTime > 300000) { // 5 minutes
                this.healthy = false;
            }
        }
    }
}

@ConfigurationProperties(prefix = "spring.ai.routing")
@Data
public class ModelRoutingProperties {
    private List<String> fallbackOrder = List.of("openai", "ollama", "custom");
    private TaskMappings taskMappings = new TaskMappings();
    private PerformanceTiers performanceTiers = new PerformanceTiers();
    private CostTiers costTiers = new CostTiers();

    @Data
    public static class TaskMappings {
        private String chat = "openai";
        private String codeGeneration = "openai";
        private String textAnalysis = "ollama";
        private String creativeWriting = "openai";
        private String translation = "openai";
        private String summarization = "ollama";
    }

    @Data
    public static class PerformanceTiers {
        private String fast = "ollama";
        private String balanced = "openai";
        private String highQuality = "openai";
    }

    @Data
    public static class CostTiers {
        private String low = "ollama";
        private String medium = "openai";
        private String high = "openai";
    }
}

// Usage example in a controller
@RestController
@RequestMapping("/api/smart-chat")
@Slf4j
public class SmartChatController {

    private final ModelRoutingService routingService;

    public SmartChatController(ModelRoutingService routingService) {
        this.routingService = routingService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> smartChat(@RequestBody SmartChatRequest request) {
        RouteablePrompt.RoutingCriteria criteria = RouteablePrompt.RoutingCriteria.builder()
            .taskType(request.getTaskType())
            .qualityRequirement(request.getQualityRequirement())
            .costConstraint(request.getCostConstraint())
            .maxLatencyMs(request.getMaxLatencyMs())
            .userId(request.getUserId())
            .build();

        RouteablePrompt routeablePrompt = new RouteablePrompt(request.getMessage(), criteria);
        ChatResponse response = routingService.routeAndCall(routeablePrompt);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stream")
    public Flux<ServerSentEvent<String>> smartStream(@RequestBody SmartChatRequest request) {
        RouteablePrompt.RoutingCriteria criteria = RouteablePrompt.RoutingCriteria.builder()
            .taskType(request.getTaskType())
            .preferredModel(request.getPreferredModel())
            .build();

        RouteablePrompt routeablePrompt = new RouteablePrompt(request.getMessage(), criteria);
        
        return routingService.routeAndStream(routeablePrompt)
            .map(response -> ServerSentEvent.builder(response.getResult().getOutput().getContent())
                .id(UUID.randomUUID().toString())
                .event("message")
                .build());
    }

    @GetMapping("/models/health")
    public ResponseEntity<Map<String, Object>> getModelsHealth() {
        // Implementation for health endpoint
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
}

@Data
class SmartChatRequest {
    private String message;
    private TaskType taskType = TaskType.CHAT;
    private QualityRequirement qualityRequirement = QualityRequirement.MEDIUM;
    private CostConstraint costConstraint = CostConstraint.MEDIUM;
    private Integer maxLatencyMs;
    private String preferredModel;
    private String userId;
}
```

## A/B Testing Framework for Models
Now let's implement an A/B testing system to compare model performance:

```java
@Service
@Slf4j
public class ModelABTestingService {

    private final Map<String, ChatModel> availableModels;
    private final ABTestConfigurationService configService;
    private final ABTestResultRepository resultRepository;
    private final MeterRegistry meterRegistry;
    private final Random random = new SecureRandom();

    public ModelABTestingService(Map<String, ChatModel> availableModels,
                                ABTestConfigurationService configService,
                                ABTestResultRepository resultRepository,
                                MeterRegistry meterRegistry) {
        this.availableModels = availableModels;
        this.configService = configService;
        this.resultRepository = resultRepository;
        this.meterRegistry = meterRegistry;
    }

    public ABTestResult executeTest(ABTestRequest request) {
        ABTestConfiguration config = configService.getActiveTest(request.getTestId());
        
        if (config == null || !config.isActive()) {
            throw new ABTestNotFoundException("No active test found with id: " + request.getTestId());
        }

        String selectedVariant = selectVariant(config, request.getUserId());
        String modelId = config.getVariantModelMapping().get(selectedVariant);
        
        ChatModel selectedModel = availableModels.get(modelId);
        if (selectedModel == null) {
            throw new ModelNotFoundException("Model not found: " + modelId);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            ChatResponse response = selectedModel.call(new Prompt(request.getPrompt()));
            
            long responseTime = sample.stop(Timer.builder("abtest.response.time")
                .tag("test", request.getTestId())
                .tag("variant", selectedVariant)
                .tag("model", modelId)
                .register(meterRegistry)).longValue();

            ABTestResult result = ABTestResult.builder()
                .testId(request.getTestId())
                .userId(request.getUserId())
                .variant(selectedVariant)
                .modelId(modelId)
                .prompt(request.getPrompt())
                .response(response.getResult().getOutput().getContent())
                .responseTime(responseTime)
                .timestamp(Instant.now())
                .build();

            resultRepository.save(result);
            
            meterRegistry.counter("abtest.requests", 
                "test", request.getTestId(), 
                "variant", selectedVariant,
                "model", modelId).increment();

            return result;
            
        } catch (Exception e) {
            meterRegistry.counter("abtest.errors", 
                "test", request.getTestId(), 
                "variant", selectedVariant,
                "model", modelId).increment();
            
            log.error("Error executing A/B test: ", e);
            throw new ABTestExecutionException("Failed to execute A/B test", e);
        }
    }

    public CompletableFuture<ParallelABTestResult> executeParallelTest(ABTestRequest request) {
        ABTestConfiguration config = configService.getActiveTest(request.getTestId());
        
        if (config == null || !config.isActive()) {
            return CompletableFuture.failedFuture(
                new ABTestNotFoundException("No active test found with id: " + request.getTestId()));
        }

        List<CompletableFuture<ABTestResult>> futures = config.getVariantModelMapping().entrySet()
            .stream()
            .map(entry -> CompletableFuture.supplyAsync(() -> {
                try {
                    String variant = entry.getKey();
                    String modelId = entry.getValue();
                    ChatModel model = availableModels.get(modelId);
                    
                    Timer.Sample sample = Timer.start(meterRegistry);
                    ChatResponse response = model.call(new Prompt(request.getPrompt()));
                    long responseTime = sample.stop(Timer.builder("abtest.parallel.response.time")
                        .tag("test", request.getTestId())
                        .tag("variant", variant)
                        .tag("model", modelId)
                        .register(meterRegistry)).longValue();

                    return ABTestResult.builder()
                        .testId(request.getTestId())
                        .userId(request.getUserId())
                        .variant(variant)
                        .modelId(modelId)
                        .prompt(request.getPrompt())
                        .response(response.getResult().getOutput().getContent())
                        .responseTime(responseTime)
                        .timestamp(Instant.now())
                        .build();
                        
                } catch (Exception e) {
                    log.error("Error in parallel test execution: ", e);
                    throw new RuntimeException(e);
                }
            }))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<ABTestResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                
                // Save all results
                results.forEach(resultRepository::save);
                
                return ParallelABTestResult.builder()
                    .testId(request.getTestId())
                    .userId(request.getUserId())
                    .results(results)
                    .timestamp(Instant.now())
                    .build();
            });
    }

    private String selectVariant(ABTestConfiguration config, String userId) {
        if (config.getTrafficSplitStrategy() == TrafficSplitStrategy.USER_HASH) {
            return selectVariantByUserHash(config, userId);
        } else if (config.getTrafficSplitStrategy() == TrafficSplitStrategy.RANDOM) {
            return selectVariantRandomly(config);
        } else {
            return selectVariantWeighted(config);
        }
    }

    private String selectVariantByUserHash(ABTestConfiguration config, String userId) {
        int hash = Math.abs(userId.hashCode());
        double normalizedHash = (hash % 10000) / 10000.0;
        
        double cumulative = 0.0;
        for (Map.Entry<String, Double> entry : config.getTrafficSplit().entrySet()) {
            cumulative += entry.getValue();
            if (normalizedHash <= cumulative) {
                return entry.getKey();
            }
        }
        
        // Fallback to first variant
        return config.getTrafficSplit().keySet().iterator().next();
    }

    private String selectVariantRandomly(ABTestConfiguration config) {
        List<String> variants = new ArrayList<>(config.getTrafficSplit().keySet());
        return variants.get(random.nextInt(variants.size()));
    }

    private String selectVariantWeighted(ABTestConfiguration config) {
        double randomValue = random.nextDouble();
        double cumulative = 0.0;
        
        for (Map.Entry<String, Double> entry : config.getTrafficSplit().entrySet()) {
            cumulative += entry.getValue();
            if (randomValue <= cumulative) {
                return entry.getKey();
            }
        }
        
        // Fallback to first variant
        return config.getTrafficSplit().keySet().iterator().next();
    }
}

@Entity
@Table(name = "ab_test_configurations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ABTestConfiguration {

    @Id
    private String testId;
    
    private String name;
    private String description;
    
    @ElementCollection
    @CollectionTable(name = "ab_test_traffic_split", joinColumns = @JoinColumn(name = "test_id"))
    @MapKeyColumn(name = "variant")
    @Column(name = "percentage")
    private Map<String, Double> trafficSplit;
    
    @ElementCollection
    @CollectionTable(name = "ab_test_variant_models", joinColumns = @JoinColumn(name = "test_id"))
    @MapKeyColumn(name = "variant")
    @Column(name = "model_id")
    private Map<String, String> variantModelMapping;
    
    @Enumerated(EnumType.STRING)
    private TrafficSplitStrategy trafficSplitStrategy = TrafficSplitStrategy.WEIGHTED;
    
    private boolean active = true;
    
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    
    @ElementCollection
    @CollectionTable(name = "ab_test_success_metrics", joinColumns = @JoinColumn(name = "test_id"))
    @Column(name = "metric")
    private Set<String> successMetrics;
    
    private double minimumSampleSize = 100;
    private double confidenceLevel = 0.95;
}

public enum TrafficSplitStrategy {
    RANDOM,
    USER_HASH,
    WEIGHTED
}

@Entity
@Table(name = "ab_test_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ABTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String testId;
    private String userId;
    private String variant;
    private String modelId;
    
    @Column(columnDefinition = "TEXT")
    private String prompt;
    
    @Column(columnDefinition = "TEXT")
    private String response;
    
    private Long responseTime;
    private Instant timestamp;
    
    // User feedback (can be updated later)
    private Integer rating;
    private String feedback;
    private Boolean thumbsUp;
    
    // Calculated metrics
    private Integer tokenCount;
    private Double cost;
    
    @ElementCollection
    @CollectionTable(name = "ab_test_result_metrics", joinColumns = @JoinColumn(name = "result_id"))
    @MapKeyColumn(name = "metric_name")
    @Column(name = "metric_value")
    private Map<String, Double> customMetrics;
}

@Data
@Builder
public class ParallelABTestResult {
    private String testId;
    private String userId;
    private List<ABTestResult> results;
    private Instant timestamp;
    
    public ABTestResult getBestResult() {
        return results.stream()
            .min(Comparator.comparing(ABTestResult::getResponseTime))
            .orElse(null);
    }
    
    public Map<String, Long> getResponseTimeComparison() {
        return results.stream()
            .collect(Collectors.toMap(
                ABTestResult::getVariant,
                ABTestResult::getResponseTime
            ));
    }
}

@Service
public class ABTestAnalyticsService {

    private final ABTestResultRepository resultRepository;
    private final StatisticalTestService statisticalTestService;

    public ABTestAnalyticsService(ABTestResultRepository resultRepository,
                                 StatisticalTestService statisticalTestService) {
        this.resultRepository = resultRepository;
        this.statisticalTestService = statisticalTestService;
    }

    public ABTestAnalysis analyzeTest(String testId) {
        List<ABTestResult> results = resultRepository.findByTestId(testId);
        
        if (results.isEmpty()) {
            throw new InsufficientDataException("No results found for test: " + testId);
        }

        Map<String, List<ABTestResult>> resultsByVariant = results.stream()
            .collect(Collectors.groupingBy(ABTestResult::getVariant));

        Map<String, VariantStatistics> variantStats = resultsByVariant.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> calculateVariantStatistics(entry.getValue())
            ));

        StatisticalSignificance significance = statisticalTestService
            .calculateSignificance(resultsByVariant);

        return ABTestAnalysis.builder()
            .testId(testId)
            .totalSamples(results.size())
            .variantStatistics(variantStats)
            .statisticalSignificance(significance)
            .recommendations(generateRecommendations(variantStats, significance))
            .build();
    }

    private VariantStatistics calculateVariantStatistics(List<ABTestResult> results) {
        DoubleSummaryStatistics responseTimeStats = results.stream()
            .mapToDouble(r -> r.getResponseTime().doubleValue())
            .summaryStatistics();

        double avgRating = results.stream()
            .filter(r -> r.getRating() != null)
            .mapToInt(ABTestResult::getRating)
            .average()
            .orElse(0.0);

        long thumbsUpCount = results.stream()
            .filter(r -> Boolean.TRUE.equals(r.getThumbsUp()))
            .count();

        return VariantStatistics.builder()
            .sampleSize(results.size())
            .avgResponseTime(responseTimeStats.getAverage())
            .minResponseTime(responseTimeStats.getMin())
            .maxResponseTime(responseTimeStats.getMax())
            .stdDevResponseTime(calculateStdDev(results))
            .avgRating(avgRating)
            .thumbsUpRate(thumbsUpCount / (double) results.size())
            .build();
    }

    private double calculateStdDev(List<ABTestResult> results) {
        double mean = results.stream()
            .mapToDouble(r -> r.getResponseTime().doubleValue())
            .average()
            .orElse(0.0);

        double variance = results.stream()
            .mapToDouble(r -> Math.pow(r.getResponseTime() - mean, 2))
            .average()
            .orElse(0.0);

        return Math.sqrt(variance);
    }

    private List<String> generateRecommendations(Map<String, VariantStatistics> stats,
                                               StatisticalSignificance significance) {
        List<String> recommendations = new ArrayList<>();

        if (significance.isSignificant()) {
            String winner = significance.getWinningVariant();
            recommendations.add("Variant '" + winner + "' shows statistically significant better performance");
            recommendations.add("Recommend rolling out variant '" + winner + "' to 100% of traffic");
        } else {
            recommendations.add("No statistically significant difference found between variants");
            recommendations.add("Consider running the test longer to gather more data");
        }

        return recommendations;
    }
}

@Data
@Builder
public class ABTestAnalysis {
    private String testId;
    private int totalSamples;
    private Map<String, VariantStatistics> variantStatistics;
    private StatisticalSignificance statisticalSignificance;
    private List<String> recommendations;
}

@Data
@Builder
public class VariantStatistics {
    private int sampleSize;
    private double avgResponseTime;
    private double minResponseTime;
    private double maxResponseTime;
    private double stdDevResponseTime;
    private double avgRating;
    private double thumbsUpRate;
}

// REST Controller for A/B Testing
@RestController
@RequestMapping("/api/ab-test")
@Slf4j
public class ABTestController {

    private final ModelABTestingService testingService;
    private final ABTestAnalyticsService analyticsService;
    private final ABTestConfigurationService configService;

    public ABTestController(ModelABTestingService testingService,
                           ABTestAnalyticsService analyticsService,
                           ABTestConfigurationService configService) {
        this.testingService = testingService;
        this.analyticsService = analyticsService;
        this.configService = configService;
    }

    @PostMapping("/execute")
    public ResponseEntity<ABTestResult> executeTest(@RequestBody ABTestRequest request) {
        ABTestResult result = testingService.executeTest(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/parallel")
    public CompletableFuture<ResponseEntity<ParallelABTestResult>> executeParallelTest(
            @RequestBody ABTestRequest request) {
        return testingService.executeParallelTest(request)
            .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/analysis/{testId}")
    public ResponseEntity<ABTestAnalysis> getTestAnalysis(@PathVariable String testId) {
        ABTestAnalysis analysis = analyticsService.analyzeTest(testId);
        return ResponseEntity.ok(analysis);
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> submitFeedback(@RequestBody ABTestFeedback feedback) {
        // Update test result with user feedback
        configService.updateResultWithFeedback(feedback);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/config/{testId}")
    public ResponseEntity<ABTestConfiguration> getTestConfig(@PathVariable String testId) {
        ABTestConfiguration config = configService.getTestConfiguration(testId);
        return ResponseEntity.ok(config);
    }
}

@Data
public class ABTestRequest {
    private String testId;
    private String userId;
    private String prompt;
    private Map<String, Object> metadata;
}

@Data
public class ABTestFeedback {
    private String resultId;
    private Integer rating;
    private String feedback;
    private Boolean thumbsUp;
}
```

## 11.3 Advanced Customization
### Custom Auto-configuration
Let's create sophisticated auto-configuration that adapts based on environment and conditions:

```java
@Configuration
@EnableConfigurationProperties({
    AdvancedAIProperties.class,
    ModelPerformanceProperties.class,
    SecurityProperties.class
})
@ConditionalOnClass({ChatModel.class, EmbeddingModel.class})
@AutoConfigureAfter({WebMvcAutoConfiguration.class, SecurityAutoConfiguration.class})
@Slf4j
public class AdvancedSpringAIAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.advanced", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AIModelOrchestrator aiModelOrchestrator(
            List<ChatModel> chatModels,
            Optional<List<EmbeddingModel>> embeddingModels,
            AdvancedAIProperties properties,
            MeterRegistry meterRegistry) {
        
        log.info("Creating AIModelOrchestrator with {} chat models", chatModels.size());
        
        return AIModelOrchestrator.builder()
            .chatModels(chatModels)
            .embeddingModels(embeddingModels.orElse(Collections.emptyList()))
            .properties(properties)
            .meterRegistry(meterRegistry)
            .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.caching", name = "enabled", havingValue = "true")
    public IntelligentCacheManager intelligentCacheManager(
            AdvancedAIProperties properties,
            @Qualifier("cacheManager") Optional<CacheManager> cacheManager) {
        
        return new IntelligentCacheManager(
            cacheManager.orElse(new ConcurrentMapCacheManager()),
            properties.getCaching());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AIPerformanceMonitor aiPerformanceMonitor(
            MeterRegistry meterRegistry,
            ModelPerformanceProperties performanceProperties) {
        
        return new AIPerformanceMonitor(meterRegistry, performanceProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.security", name = "input-validation", havingValue = "true", matchIfMissing = true)
    public InputValidationService inputValidationService(SecurityProperties securityProperties) {
        return new InputValidationService(securityProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(InputValidationService.class)
    public AISecurityInterceptor aiSecurityInterceptor(InputValidationService validationService) {
        return new AISecurityInterceptor(validationService);
    }

    @Configuration
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "spring.ai.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class WebConfiguration implements WebMvcConfigurer {

        @Autowired(required = false)
        private AISecurityInterceptor aiSecurityInterceptor;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            if (aiSecurityInterceptor != null) {
                registry.addInterceptor(aiSecurityInterceptor)
                    .addPathPatterns("/api/ai/**", "/api/chat/**");
            }
        }
    }

    @Configuration
    @ConditionalOnClass({ReactiveWebServerFactory.class})
    @ConditionalOnProperty(prefix = "spring.ai.reactive", name = "enabled", havingValue = "true")
    static class ReactiveConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ReactiveAIModelOrchestrator reactiveAIModelOrchestrator(
                List<ChatModel> chatModels,
                AdvancedAIProperties properties) {
            
            return new ReactiveAIModelOrchestrator(chatModels, properties);
        }

        @Bean
        public RouterFunction<ServerResponse> aiRoutes(ReactiveAIModelOrchestrator orchestrator) {
            return RouterFunctions.route()
                .POST("/api/reactive/chat", orchestrator::handleChatRequest)
                .POST("/api/reactive/stream", orchestrator::handleStreamRequest)
                .build();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "spring.ai.batch", name = "enabled", havingValue = "true")
    static class BatchConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public BatchAIProcessor batchAIProcessor(
                AIModelOrchestrator orchestrator,
                AdvancedAIProperties properties) {
            
            return new BatchAIProcessor(orchestrator, properties.getBatch());
        }

        @Bean
        @ConditionalOnMissingBean
        public TaskExecutor batchTaskExecutor(AdvancedAIProperties properties) {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(properties.getBatch().getCorePoolSize());
            executor.setMaxPoolSize(properties.getBatch().getMaxPoolSize());
            executor.setQueueCapacity(properties.getBatch().getQueueCapacity());
            executor.setThreadNamePrefix("ai-batch-");
            executor.initialize();
            return executor;
        }
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.cost-tracking", name = "enabled", havingValue = "true")
    public CostTrackingService costTrackingService(AdvancedAIProperties properties) {
        return new CostTrackingService(properties.getCostTracking());
    }
}

@ConfigurationProperties(prefix = "spring.ai.advanced")
@Data
@Validated
public class AdvancedAIProperties {

    private boolean enabled = true;
    
    @Valid
    private CachingProperties caching = new CachingProperties();
    
    @Valid
    private BatchProperties batch = new BatchProperties();
    
    @Valid
    private CostTrackingProperties costTracking = new CostTrackingProperties();
    
    @Valid
    private RateLimitingProperties rateLimiting = new RateLimitingProperties();
    
    private Map<String, ModelConfig> models = new HashMap<>();

    @Data
    public static class CachingProperties {
        private boolean enabled = true;
        private Duration ttl = Duration.ofHours(1);
        private int maxSize = 1000;
        private String strategy = "LRU";
        private Set<String> cacheableOperations = Set.of("embedding", "simple-chat");
    }

    @Data
    public static class BatchProperties {
        private boolean enabled = false;
        private int corePoolSize = 5;
        private int maxPoolSize = 10;
        private int queueCapacity = 100;
        private int batchSize = 10;
        private Duration flushInterval = Duration.ofSeconds(5);
    }

    @Data
    public static class CostTrackingProperties {
        private boolean enabled = false;
        private Map<String, Double> modelCosts = new HashMap<>();
        private Duration reportingInterval = Duration.ofMinutes(15);
        private boolean persistResults = true;
    }

    @Data
    public static class RateLimitingProperties {
        private boolean enabled = false;
        private int requestsPerSecond = 10;
        private int requestsPerMinute = 100;
        private int requestsPerHour = 1000;
        private String strategy = "TOKEN_BUCKET";
    }

    @Data
    public static class ModelConfig {
        private String type;
        private Map<String, Object> properties = new HashMap<>();
        private boolean enabled = true;
        private int priority = 0;
        private Set<String> supportedOperations = new HashSet<>();
    }
}

@Service
@Slf4j
public class AIModelOrchestrator {

    private final List<ChatModel> chatModels;
    private final List<EmbeddingModel> embeddingModels;
    private final AdvancedAIProperties properties;
    private final MeterRegistry meterRegistry;
    private final IntelligentCacheManager cacheManager;

    @Builder
    public AIModelOrchestrator(List<ChatModel> chatModels,
                              List<EmbeddingModel> embeddingModels,
                              AdvancedAIProperties properties,
                              MeterRegistry meterRegistry,
                              IntelligentCacheManager cacheManager) {
        this.chatModels = chatModels;
        this.embeddingModels = embeddingModels;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.cacheManager = cacheManager;
    }

    public ChatResponse orchestrateChat(String prompt, OrchestrationContext context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Check cache first
            String cacheKey = generateCacheKey(prompt, context);
            if (cacheManager != null && properties.getCaching().isEnabled()) {
                ChatResponse cached = cacheManager.getCachedResponse(cacheKey);
                if (cached != null) {
                    meterRegistry.counter("ai.cache.hits", "operation", "chat").increment();
                    return cached;
                }
            }

            // Select best model based on context
            ChatModel selectedModel = selectOptimalChatModel(context);
            
            // Execute with monitoring
            ChatResponse response = selectedModel.call(new Prompt(prompt));
            
            // Cache the response
            if (cacheManager != null && shouldCache(context)) {
                cacheManager.cacheResponse(cacheKey, response);
            }
            
            // Record metrics
            sample.stop(Timer.builder("ai.orchestration.time")
                .tag("operation", "chat")
                .tag("model", getModelName(selectedModel))
                .register(meterRegistry));

            return response;
            
        } catch (Exception e) {
            meterRegistry.counter("ai.orchestration.errors", "operation", "chat").increment();
            log.error("Error in chat orchestration: ", e);
            throw new OrchestrationException("Failed to orchestrate chat", e);
        }
    }

    public List<Float> orchestrateEmbedding(List<String> texts, OrchestrationContext context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            EmbeddingModel selectedModel = selectOptimalEmbeddingModel(context);
            
            EmbeddingRequest request = new EmbeddingRequest(texts, null);
            EmbeddingResponse response = selectedModel.call(request);
            
            sample.stop(Timer.builder("ai.orchestration.time")
                .tag("operation", "embedding")
                .tag("model", getModelName(selectedModel))
                .register(meterRegistry));

            return response.getResults().get(0).getOutput();
            
        } catch (Exception e) {
            meterRegistry.counter("ai.orchestration.errors", "operation", "embedding").increment();
            log.error("Error in embedding orchestration: ", e);
            throw new OrchestrationException("Failed to orchestrate embedding", e);
        }
    }

    private ChatModel selectOptimalChatModel(OrchestrationContext context) {
        return chatModels.stream()
            .filter(model -> isModelSuitable(model, context))
            .min((m1, m2) -> compareModels(m1, m2, context))
            .orElseThrow(() -> new NoSuitableModelException("No suitable chat model found"));
    }

    private EmbeddingModel selectOptimalEmbeddingModel(OrchestrationContext context) {
        return embeddingModels.stream()
            .filter(model -> isModelSuitable(model, context))
            .findFirst()
            .orElseThrow(() -> new NoSuitableModelException("No suitable embedding model found"));
    }

    private boolean isModelSuitable(Object model, OrchestrationContext context) {
        String modelName = getModelName(model);
        AdvancedAIProperties.ModelConfig config = properties.getModels().get(modelName);
        
        if (config != null && !config.isEnabled()) {
            return false;
        }
        
        // Add more sophisticated suitability checks
        return true;
    }

    private int compareModels(ChatModel m1, ChatModel m2, OrchestrationContext context) {
        String model1Name = getModelName(m1);
        String model2Name = getModelName(m2);
        
        AdvancedAIProperties.ModelConfig config1 = properties.getModels().get(model1Name);
        AdvancedAIProperties.ModelConfig config2 = properties.getModels().get(model2Name);
        
        int priority1 = config1 != null ? config1.getPriority() : 0;
        int priority2 = config2 != null ? config2.getPriority() : 0;
        
        return Integer.compare(priority2, priority1); // Higher priority first
    }

    private String getModelName(Object model) {
        return model.getClass().getSimpleName().toLowerCase();
    }

    private String generateCacheKey(String prompt, OrchestrationContext context) {
        return DigestUtils.md5Hex(prompt + context.toString());
    }

    private boolean shouldCache(OrchestrationContext context) {
        return properties.getCaching().getCacheableOperations().contains(context.getOperation());
    }
}

@Component
@Slf4j
public class IntelligentCacheManager {

    private final CacheManager cacheManager;
    private final AdvancedAIProperties.CachingProperties properties;
    private final Cache responseCache;

    public IntelligentCacheManager(CacheManager cacheManager, 
                                  AdvancedAIProperties.CachingProperties properties) {
        this.cacheManager = cacheManager;
        this.properties = properties;
        this.responseCache = cacheManager.getCache("ai-responses");
    }

    @Cacheable(value = "ai-responses", key = "#cacheKey", unless = "#result == null")
    public ChatResponse getCachedResponse(String cacheKey) {
        return responseCache != null ? responseCache.get(cacheKey, ChatResponse.class) : null;
    }

    @CachePut(value = "ai-responses", key = "#cacheKey")
    public ChatResponse cacheResponse(String cacheKey, ChatResponse response) {
        log.debug("Caching response for key: {}", cacheKey);
        return response;
    }

    @CacheEvict(value = "ai-responses", key = "#cacheKey")
    public void evictCachedResponse(String cacheKey) {
        log.debug("Evicting cached response for key: {}", cacheKey);
    }

    @CacheEvict(value = "ai-responses", allEntries = true)
    public void clearCache() {
        log.info("Clearing all cached AI responses");
    }

    public boolean isCacheableOperation(String operation) {
        return properties.getCacheableOperations().contains(operation);
    }
}

@Component
public class InputValidationService {

    private final SecurityProperties securityProperties;
    private final Set<String> blockedPatterns;
    private final Pattern promptInjectionPattern;

    public InputValidationService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.blockedPatterns = initializeBlockedPatterns();
        this.promptInjectionPattern = Pattern.compile(
            "(?i)(ignore|disregard|forget|override)\\s+(previous|above|all)\\s+(instructions|rules|prompts)",
            Pattern.CASE_INSENSITIVE
        );
    }

    public ValidationResult validateInput(String input) {
        ValidationResult result = ValidationResult.builder()
            .valid(true)
            .sanitizedInput(input)
            .build();

        // Check for prompt injection
        if (containsPromptInjection(input)) {
            result.setValid(false);
            result.getViolations().add("Potential prompt injection detected");
        }

        // Check for blocked content
        for (String pattern : blockedPatterns) {
            if (input.toLowerCase().contains(pattern.toLowerCase())) {
                result.setValid(false);
                result.getViolations().add("Blocked content detected: " + pattern);
            }
        }

        // Length validation
        if (input.length() > securityProperties.getMaxInputLength()) {
            result.setValid(false);
            result.getViolations().add("Input exceeds maximum length");
        }

        // Sanitize input if needed
        if (securityProperties.isSanitizeInput()) {
            result.setSanitizedInput(sanitizeInput(input));
        }

        return result;
    }

    private boolean containsPromptInjection(String input) {
        return promptInjectionPattern.matcher(input).find();
    }

    private String sanitizeInput(String input) {
        // Remove potentially dangerous HTML/script content
        return input.replaceAll("<script[^>]*>.*?</script>", "")
                   .replaceAll("<[^>]*>", "")
                   .replaceAll("javascript:", "")
                   .replaceAll("vbscript:", "")
                   .trim();
    }

    private Set<String> initializeBlockedPatterns() {
        return Set.of(
            "password", "ssn", "social security", "credit card",
            "hack", "exploit", "malware", "virus"
        );
    }
}

@Component
public class AISecurityInterceptor implements HandlerInterceptor {

    private final InputValidationService validationService;
    private final RateLimiter rateLimiter;

    public AISecurityInterceptor(InputValidationService validationService) {
        this.validationService = validationService;
        this.rateLimiter = RateLimiter.create(10.0); // 10 requests per second
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                           Object handler) throws Exception {
        
        // Rate limiting
        if (!rateLimiter.tryAcquire()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            return false;
        }

        // Validate input for POST requests
        if ("POST".equals(request.getMethod())) {
            String body = getRequestBody(request);
            if (body != null) {
                ValidationResult validation = validationService.validateInput(body);
                if (!validation.isValid()) {
                    response.setStatus(HttpStatus.BAD_REQUEST.value());
                    response.getWriter().write("{\"error\":\"Input validation failed: " + 
                        String.join(", ", validation.getViolations()) + "\"}");
                    return false;
                }
            }
        }

        return true;
    }

    private String getRequestBody(HttpServletRequest request) {
        try {
            return request.getReader().lines()
                .collect(Collectors.joining(System.lineSeparator()));
        } catch (Exception e) {
            return null;
        }
    }
}

@Service
@Slf4j
public class CostTrackingService {

    private final AdvancedAIProperties.CostTrackingProperties properties;
    private final Map<String, Double> usageCosts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public CostTrackingService(AdvancedAIProperties.CostTrackingProperties properties) {
        this.properties = properties;
        if (properties.isEnabled()) {
            startPeriodicReporting();
        }
    }

    public void trackUsage(String modelName, int tokenCount) {
        if (!properties.isEnabled()) return;

        Double costPerToken = properties.getModelCosts().get(modelName);
        if (costPerToken != null) {
            double cost = tokenCount * costPerToken;
            usageCosts.merge(modelName, cost, Double::sum);
            log.debug("Tracked usage for {}: {} tokens, ${}", modelName, tokenCount, cost);
        }
    }

    public Map<String, Double> getCurrentCosts() {
        return new HashMap<>(usageCosts);
    }

    public double getTotalCost() {
        return usageCosts.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private void startPeriodicReporting() {
        scheduler.scheduleAtFixedRate(this::reportCosts, 
            properties.getReportingInterval().toSeconds(),
            properties.getReportingInterval().toSeconds(),
            TimeUnit.SECONDS);
    }

    private void reportCosts() {
        if (!usageCosts.isEmpty()) {
            log.info("AI Usage Costs Report: {}", usageCosts);
            log.info("Total Cost: ${}", getTotalCost());
            
            if (properties.isPersistResults()) {
                // Persist to database or external system
                persistCostReport();
            }
        }
    }

    private void persistCostReport() {
        // Implementation for persisting cost data
        log.debug("Persisting cost report to storage");
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}

// Custom exceptions
class OrchestrationException extends RuntimeException {
    public OrchestrationException(String message, Throwable cause) {
        super(message, cause);
    }
}

class NoSuitableModelException extends RuntimeException {
    public NoSuitableModelException(String message) {
        super(message);
    }
}

// Data classes
@Data
@Builder
public class OrchestrationContext {
    private String operation;
    private String userId;
    private Map<String, Object> metadata;
    private QualityRequirement qualityRequirement;
    private CostConstraint costConstraint;
    private Integer maxLatencyMs;
}

@Data
@Builder
public class ValidationResult {
    private boolean valid;
    private String sanitizedInput;
    @Builder.Default
    private List<String> violations = new ArrayList<>();
}

@ConfigurationProperties(prefix = "spring.ai.security")
@Data
public class SecurityProperties {
    private boolean inputValidation = true;
    private boolean sanitizeInput = true;
    private int maxInputLength = 10000;
    private Set<String> blockedPatterns = new HashSet<>();
    private boolean enableRateLimiting = true;
    private int rateLimitPerSecond = 10;
}
```

### Custom Interceptors and Response Transformers
Now let's create sophisticated interceptors and transformers for advanced customization:

```java
// Main interceptor interface
public interface AIModelInterceptor {
    
    default InterceptorResult preProcess(InterceptorContext context) {
        return InterceptorResult.proceed();
    }
    
    default InterceptorResult postProcess(InterceptorContext context, Object result) {
        return InterceptorResult.proceed(result);
    }
    
    default void onError(InterceptorContext context, Exception error) {
        // Default implementation does nothing
    }
    
    int getOrder();
}

@Component
@Order(1)
@Slf4j
public class LoggingInterceptor implements AIModelInterceptor {

    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public LoggingInterceptor(MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public InterceptorResult preProcess(InterceptorContext context) {
        log.info("AI Request - Model: {}, Operation: {}, User: {}", 
            context.getModelName(), 
            context.getOperation(),
            context.getUserId());
        
        if (log.isDebugEnabled()) {
            try {
                String promptJson = objectMapper.writeValueAsString(context.getPrompt());
                log.debug("Prompt details: {}", promptJson);
            } catch (Exception e) {
                log.debug("Failed to serialize prompt for logging", e);
            }
        }
        
        context.setAttribute("startTime", System.currentTimeMillis());
        meterRegistry.counter("ai.requests", 
            "model", context.getModelName(),
            "operation", context.getOperation()).increment();
        
        return InterceptorResult.proceed();
    }

    @Override
    public InterceptorResult postProcess(InterceptorContext context, Object result) {
        Long startTime = (Long) context.getAttribute("startTime");
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            
            meterRegistry.timer("ai.response.time",
                "model", context.getModelName(),
                "operation", context.getOperation())
                .record(duration, TimeUnit.MILLISECONDS);
            
            log.info("AI Response completed in {}ms", duration);
        }
        
        return InterceptorResult.proceed(result);
    }

    @Override
    public void onError(InterceptorContext context, Exception error) {
        log.error("AI Request failed - Model: {}, Operation: {}, Error: {}", 
            context.getModelName(), 
            context.getOperation(),
            error.getMessage(), error);
        
        meterRegistry.counter("ai.errors",
            "model", context.getModelName(),
            "operation", context.getOperation(),
            "error", error.getClass().getSimpleName()).increment();
    }

    @Override
    public int getOrder() {
        return 1;
    }
}

@Component
@Order(2)
@Slf4j
public class ContentFilteringInterceptor implements AIModelInterceptor {

    private final ContentFilterService contentFilterService;
    private final MeterRegistry meterRegistry;

    public ContentFilteringInterceptor(ContentFilterService contentFilterService,
                                     MeterRegistry meterRegistry) {
        this.contentFilterService = contentFilterService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public InterceptorResult preProcess(InterceptorContext context) {
        String promptText = extractPromptText(context.getPrompt());
        
        ContentFilterResult filterResult = contentFilterService.filterInput(promptText);
        
        if (filterResult.isBlocked()) {
            log.warn("Content filtered - blocking request. Reasons: {}", filterResult.getReasons());
            meterRegistry.counter("ai.content.blocked", "type", "input").increment();
            
            return InterceptorResult.block("Content blocked: " + String.join(", ", filterResult.getReasons()));
        }
        
        if (filterResult.isModified()) {
            log.info("Input content was modified by filter");
            context.setPrompt(createModifiedPrompt(context.getPrompt(), filterResult.getFilteredText()));
            meterRegistry.counter("ai.content.modified", "type", "input").increment();
        }
        
        return InterceptorResult.proceed();
    }

    @Override
    public InterceptorResult postProcess(InterceptorContext context, Object result) {
        if (result instanceof ChatResponse) {
            ChatResponse response = (ChatResponse) result;
            String responseText = response.getResult().getOutput().getContent();
            
            ContentFilterResult filterResult = contentFilterService.filterOutput(responseText);
            
            if (filterResult.isBlocked()) {
                log.warn("Response content blocked");
                meterRegistry.counter("ai.content.blocked", "type", "output").increment();
                return InterceptorResult.block("Response content was blocked by safety filters");
            }
            
            if (filterResult.isModified()) {
                log.info("Response content was modified by filter");
                ChatResponse modifiedResponse = createModifiedResponse(response, filterResult.getFilteredText());
                meterRegistry.counter("ai.content.modified", "type", "output").increment();
                return InterceptorResult.proceed(modifiedResponse);
            }
        }
        
        return InterceptorResult.proceed(result);
    }

    private String extractPromptText(Object prompt) {
        if (prompt instanceof Prompt) {
            return ((Prompt) prompt).getInstructions().stream()
                .map(Message::getContent)
                .collect(Collectors.joining(" "));
        }
        return prompt.toString();
    }

    private Object createModifiedPrompt(Object originalPrompt, String filteredText) {
        // Create a new prompt with filtered content
        return new Prompt(filteredText);
    }

    private ChatResponse createModifiedResponse(ChatResponse original, String filteredText) {
        AssistantMessage modifiedMessage = new AssistantMessage(filteredText);
        return ChatResponse.builder()
            .withGenerations(List.of(new Generation(modifiedMessage)))
            .withMetadata(original.getMetadata())
            .build();
    }

    @Override
    public int getOrder() {
        return 2;
    }
}

@Component
@Order(3)
@Slf4j
public class RateLimitingInterceptor implements AIModelInterceptor {

    private final Map<String, RateLimiter> userRateLimiters = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> modelRateLimiters = new ConcurrentHashMap<>();
    private final RateLimitingProperties properties;
    private final MeterRegistry meterRegistry;

    public RateLimitingInterceptor(RateLimitingProperties properties, 
                                  MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public InterceptorResult preProcess(InterceptorContext context) {
        if (!properties.isEnabled()) {
            return InterceptorResult.proceed();
        }

        // User-based rate limiting
        String userId = context.getUserId();
        if (userId != null) {
            RateLimiter userLimiter = userRateLimiters.computeIfAbsent(userId,
                k -> RateLimiter.create(properties.getUserRequestsPerSecond()));
            
            if (!userLimiter.tryAcquire()) {
                log.warn("Rate limit exceeded for user: {}", userId);
                meterRegistry.counter("ai.rate_limit.exceeded", "type", "user").increment();
                return InterceptorResult.block("Rate limit exceeded for user");
            }
        }

        // Model-based rate limiting
        String modelName = context.getModelName();
        Double modelRateLimit = properties.getModelRateLimit(modelName);
        if (modelRateLimit != null) {
            RateLimiter modelLimiter = modelRateLimiters.computeIfAbsent(modelName,
                k -> RateLimiter.create(modelRateLimit));
            
            if (!modelLimiter.tryAcquire()) {
                log.warn("Rate limit exceeded for model: {}", modelName);
                meterRegistry.counter("ai.rate_limit.exceeded", "type", "model").increment();
                return InterceptorResult.block("Rate limit exceeded for model");
            }
        }

        return InterceptorResult.proceed();
    }

    @Override
    public int getOrder() {
        return 3;
    }
}

@Component
@Order(100)
@Slf4j
public class ResponseEnhancementInterceptor implements AIModelInterceptor {

    private final ResponseEnhancementService enhancementService;
    private final ObjectMapper objectMapper;

    public ResponseEnhancementInterceptor(ResponseEnhancementService enhancementService,
                                        ObjectMapper objectMapper) {
        this.enhancementService = enhancementService;
        this.objectMapper = objectMapper;
    }

    @Override
    public InterceptorResult postProcess(InterceptorContext context, Object result) {
        if (result instanceof ChatResponse) {
            ChatResponse response = (ChatResponse) result;
            
            try {
                EnhancedResponse enhanced = enhancementService.enhance(response, context);
                return InterceptorResult.proceed(enhanced);
            } catch (Exception e) {
                log.error("Failed to enhance response", e);
                // Return original response if enhancement fails
                return InterceptorResult.proceed(result);
            }
        }
        
        return InterceptorResult.proceed(result);
    }

    @Override
    public int getOrder() {
        return 100;
    }
}

// Interceptor orchestration service
@Service
@Slf4j
public class InterceptorChainService {

    private final List<AIModelInterceptor> interceptors;

    public InterceptorChainService(List<AIModelInterceptor> interceptors) {
        this.interceptors = interceptors.stream()
            .sorted(Comparator.comparing(AIModelInterceptor::getOrder))
            .collect(Collectors.toList());
        
        log.info("Initialized interceptor chain with {} interceptors", this.interceptors.size());
        this.interceptors.forEach(interceptor -> 
            log.debug("Loaded interceptor: {} (order: {})", 
                interceptor.getClass().getSimpleName(), 
                interceptor.getOrder()));
    }

    public <T> T executeWithInterceptors(InterceptorContext context, 
                                       Supplier<T> execution) {
        
        // Pre-processing phase
        for (AIModelInterceptor interceptor : interceptors) {
            try {
                InterceptorResult result = interceptor.preProcess(context);
                if (result.isBlocked()) {
                    throw new InterceptorBlockedException(result.getBlockReason());
                }
                if (result.hasModifiedInput()) {
                    context.setPrompt(result.getModifiedInput());
                }
            } catch (InterceptorBlockedException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error in pre-processing interceptor: {}", 
                    interceptor.getClass().getSimpleName(), e);
                interceptor.onError(context, e);
            }
        }

        T result;
        try {
            // Execute the main operation
            result = execution.get();
        } catch (Exception e) {
            // Notify all interceptors about the error
            interceptors.forEach(interceptor -> interceptor.onError(context, e));
            throw e;
        }

        // Post-processing phase (in reverse order)
        List<AIModelInterceptor> reversedInterceptors = new ArrayList<>(interceptors);
        Collections.reverse(reversedInterceptors);

        Object processedResult = result;
        for (AIModelInterceptor interceptor : reversedInterceptors) {
            try {
                InterceptorResult interceptorResult = interceptor.postProcess(context, processedResult);
                if (interceptorResult.isBlocked()) {
                    throw new InterceptorBlockedException(interceptorResult.getBlockReason());
                }
                if (interceptorResult.hasModifiedResult()) {
                    processedResult = interceptorResult.getModifiedResult();
                }
            } catch (InterceptorBlockedException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error in post-processing interceptor: {}", 
                    interceptor.getClass().getSimpleName(), e);
                interceptor.onError(context, e);
            }
        }

        return (T) processedResult;
    }
}

// Response transformer interfaces and implementations
public interface ResponseTransformer<T> {
    T transform(T response, TransformationContext context);
    boolean supports(Object response);
    int getOrder();
}

@Component
@Order(1)
public class MetadataEnrichmentTransformer implements ResponseTransformer<ChatResponse> {

    private final MeterRegistry meterRegistry;

    public MetadataEnrichmentTransformer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ChatResponse transform(ChatResponse response, TransformationContext context) {
        Map<String, Object> enrichedMetadata = new HashMap<>(response.getMetadata());
        
        // Add processing metadata
        enrichedMetadata.put("processedAt", Instant.now().toString());
        enrichedMetadata.put("processingTimeMs", context.getProcessingTime());
        enrichedMetadata.put("modelUsed", context.getModelName());
        enrichedMetadata.put("transformationId", UUID.randomUUID().toString());
        
        // Add token count if available
        if (response.getMetadata().containsKey("usage")) {
            enrichedMetadata.put("tokenUsage", response.getMetadata().get("usage"));
        }
        
        return ChatResponse.builder()
            .withGenerations(response.getResults())
            .withMetadata(enrichedMetadata)
            .build();
    }

    @Override
    public boolean supports(Object response) {
        return response instanceof ChatResponse;
    }

    @Override
    public int getOrder() {
        return 1;
    }
}

@Component
@Order(2)
public class MarkdownFormattingTransformer implements ResponseTransformer<ChatResponse> {

    @Override
    public ChatResponse transform(ChatResponse response, TransformationContext context) {
        if (!context.shouldFormatAsMarkdown()) {
            return response;
        }

        String originalContent = response.getResult().getOutput().getContent();
        String formattedContent = formatAsMarkdown(originalContent);
        
        AssistantMessage formattedMessage = new AssistantMessage(formattedContent);
        
        return ChatResponse.builder()
            .withGenerations(List.of(new Generation(formattedMessage)))
            .withMetadata(response.getMetadata())
            .build();
    }

    private String formatAsMarkdown(String content) {
        // Simple markdown formatting
        return content
            .replaceAll("(?m)^\\d+\\.", "1.")  // Normalize numbered lists
            .replaceAll("(?m)^-", "*")         // Normalize bullet lists
            .replaceAll("(?m)^([A-Z][^:]+):", "**$1:**") // Bold headings
            .trim();
    }

    @Override
    public boolean supports(Object response) {
        return response instanceof ChatResponse;
    }

    @Override
    public int getOrder() {
        return 2;
    }
}

@Component
@Order(3)
public class LanguageTranslationTransformer implements ResponseTransformer<ChatResponse> {

    private final TranslationService translationService;

    public LanguageTranslationTransformer(TranslationService translationService) {
        this.translationService = translationService;
    }

    @Override
    public ChatResponse transform(ChatResponse response, TransformationContext context) {
        String targetLanguage = context.getTargetLanguage();
        if (targetLanguage == null || "en".equals(targetLanguage)) {
            return response;
        }

        String originalContent = response.getResult().getOutput().getContent();
        String translatedContent = translationService.translate(originalContent, targetLanguage);
        
        AssistantMessage translatedMessage = new AssistantMessage(translatedContent);
        
        Map<String, Object> metadata = new HashMap<>(response.getMetadata());
        metadata.put("translatedTo", targetLanguage);
        metadata.put("originalContent", originalContent);
        
        return ChatResponse.builder()
            .withGenerations(List.of(new Generation(translatedMessage)))
            .withMetadata(metadata)
            .build();
    }

    @Override
    public boolean supports(Object response) {
        return response instanceof ChatResponse;
    }

    @Override
    public int getOrder() {
        return 3;
    }
}

// Supporting classes
@Data
@Builder
public class InterceptorContext {
    private String modelName;
    private String operation;
    private String userId;
    private Object prompt;
    private Map<String, Object> attributes = new HashMap<>();
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    public void setPrompt(Object prompt) {
        this.prompt = prompt;
    }
}

@Data
@Builder
public class InterceptorResult {
    private boolean blocked;
    private String blockReason;
    private boolean hasModifiedInput;
    private Object modifiedInput;
    private boolean hasModifiedResult;
    private Object modifiedResult;
    
    public static InterceptorResult proceed() {
        return InterceptorResult.builder().blocked(false).build();
    }
    
    public static InterceptorResult proceed(Object result) {
        return InterceptorResult.builder()
            .blocked(false)
            .hasModifiedResult(true)
            .modifiedResult(result)
            .build();
    }
    
    public static InterceptorResult block(String reason) {
        return InterceptorResult.builder()
            .blocked(true)
            .blockReason(reason)
            .build();
    }
    
    public static InterceptorResult modifyInput(Object modifiedInput) {
        return InterceptorResult.builder()
            .blocked(false)
            .hasModifiedInput(true)
            .modifiedInput(modifiedInput)
            .build();
    }
}

@Data
@Builder
public class TransformationContext {
    private String modelName;
    private String userId;
    private long processingTime;
    private boolean formatAsMarkdown;
    private String targetLanguage;
    private Map<String, Object> customProperties = new HashMap<>();
    
    public boolean shouldFormatAsMarkdown() {
        return formatAsMarkdown;
    }
}

// Exception classes
public class InterceptorBlockedException extends RuntimeException {
    public InterceptorBlockedException(String message) {
        super(message);
    }
}

// Service implementations
@Service
@Slf4j
public class ContentFilterService {

    private final Set<String> prohibitedWords;
    private final Pattern sensitiveDataPattern;
    private final Pattern harmfulContentPattern;

    public ContentFilterService() {
        this.prohibitedWords = initializeProhibitedWords();
        this.sensitiveDataPattern = Pattern.compile(
            "(?i)(password|ssn|social\\s+security|credit\\s+card|api\\s+key|token)",
            Pattern.CASE_INSENSITIVE);
        this.harmfulContentPattern = Pattern.compile(
            "(?i)(hack|exploit|malware|virus|attack|illegal|harmful)",
            Pattern.CASE_INSENSITIVE);
    }

    public ContentFilterResult filterInput(String input) {
        ContentFilterResult result = ContentFilterResult.builder()
            .originalText(input)
            .filteredText(input)
            .blocked(false)
            .modified(false)
            .build();

        // Check for prohibited content
        if (containsProhibitedContent(input)) {
            result.setBlocked(true);
            result.getReasons().add("Contains prohibited content");
            return result;
        }

        // Check for sensitive data
        if (containsSensitiveData(input)) {
            result.setBlocked(true);
            result.getReasons().add("Contains sensitive data");
            return result;
        }

        // Filter harmful content
        String filtered = filterHarmfulContent(input);
        if (!filtered.equals(input)) {
            result.setModified(true);
            result.setFilteredText(filtered);
            result.getReasons().add("Harmful content filtered");
        }

        return result;
    }

    public ContentFilterResult filterOutput(String output) {
        ContentFilterResult result = ContentFilterResult.builder()
            .originalText(output)
            .filteredText(output)
            .blocked(false)
            .modified(false)
            .build();

        // Less strict filtering for output
        if (containsExplicitProhibitedContent(output)) {
            result.setBlocked(true);
            result.getReasons().add("Output contains prohibited content");
        }

        return result;
    }

    private boolean containsProhibitedContent(String text) {
        String lowerText = text.toLowerCase();
        return prohibitedWords.stream().anyMatch(lowerText::contains);
    }

    private boolean containsSensitiveData(String text) {
        return sensitiveDataPattern.matcher(text).find();
    }

    private boolean containsExplicitProhibitedContent(String text) {
        // More lenient check for output
        return text.toLowerCase().contains("explicit_harmful_content");
    }

    private String filterHarmfulContent(String text) {
        return harmfulContentPattern.matcher(text)
            .replaceAll(matchResult -> "[FILTERED]");
    }

    private Set<String> initializeProhibitedWords() {
        return Set.of(
            "violence", "hate", "discrimination", "explicit"
        );
    }
}

@Service
public class ResponseEnhancementService {

    private final SentimentAnalysisService sentimentService;
    private final FactCheckingService factCheckService;
    private final ObjectMapper objectMapper;

    public ResponseEnhancementService(SentimentAnalysisService sentimentService,
                                    FactCheckingService factCheckService,
                                    ObjectMapper objectMapper) {
        this.sentimentService = sentimentService;
        this.factCheckService = factCheckService;
        this.objectMapper = objectMapper;
    }

    public EnhancedResponse enhance(ChatResponse response, InterceptorContext context) {
        String content = response.getResult().getOutput().getContent();
        
        // Analyze sentiment
        SentimentResult sentiment = sentimentService.analyze(content);
        
        // Check facts (for factual content)
        FactCheckResult factCheck = factCheckService.checkFacts(content);
        
        // Create enhanced metadata
        Map<String, Object> enhancedMetadata = new HashMap<>(response.getMetadata());
        enhancedMetadata.put("sentiment", sentiment);
        enhancedMetadata.put("factCheck", factCheck);
        enhancedMetadata.put("enhancedAt", Instant.now().toString());
        
        return EnhancedResponse.builder()
            .originalResponse(response)
            .enhancedContent(content)
            .sentimentAnalysis(sentiment)
            .factCheckResults(factCheck)
            .confidence(calculateOverallConfidence(sentiment, factCheck))
            .metadata(enhancedMetadata)
            .build();
    }

    private double calculateOverallConfidence(SentimentResult sentiment, FactCheckResult factCheck) {
        return (sentiment.getConfidence() + factCheck.getConfidence()) / 2.0;
    }
}

@Service
public class TranslationService {
    
    // Mock implementation - in real scenario, integrate with Google Translate, Azure Translator, etc.
    public String translate(String text, String targetLanguage) {
        // This is a mock implementation
        // In production, you would integrate with actual translation services
        return "[Translated to " + targetLanguage + "] " + text;
    }
}

// Enhanced response orchestration service
@Service
@Slf4j
public class EnhancedAIService {

    private final AIModelOrchestrator orchestrator;
    private final InterceptorChainService interceptorService;
    private final List<ResponseTransformer<ChatResponse>> transformers;

    public EnhancedAIService(AIModelOrchestrator orchestrator,
                           InterceptorChainService interceptorService,
                           List<ResponseTransformer<ChatResponse>> transformers) {
        this.orchestrator = orchestrator;
        this.interceptorService = interceptorService;
        this.transformers = transformers.stream()
            .sorted(Comparator.comparing(ResponseTransformer::getOrder))
            .collect(Collectors.toList());
    }

    public ChatResponse processRequest(EnhancedAIRequest request) {
        InterceptorContext context = InterceptorContext.builder()
            .modelName(request.getPreferredModel())
            .operation("chat")
            .userId(request.getUserId())
            .prompt(request.getPrompt())
            .build();

        return interceptorService.executeWithInterceptors(context, () -> {
            // Execute the main AI operation
            OrchestrationContext orchestrationContext = OrchestrationContext.builder()
                .operation("chat")
                .userId(request.getUserId())
                .qualityRequirement(request.getQualityRequirement())
                .costConstraint(request.getCostConstraint())
                .maxLatencyMs(request.getMaxLatencyMs())
                .build();

            ChatResponse response = orchestrator.orchestrateChat(
                request.getPrompt(), orchestrationContext);

            // Apply transformations
            return applyTransformations(response, request);
        });
    }

    public Flux<ChatResponse> processStreamingRequest(EnhancedAIRequest request) {
        return Flux.defer(() -> {
            try {
                ChatResponse response = processRequest(request);
                return Flux.just(response);
            } catch (Exception e) {
                return Flux.error(e);
            }
        });
    }

    private ChatResponse applyTransformations(ChatResponse response, EnhancedAIRequest request) {
        TransformationContext transformContext = TransformationContext.builder()
            .modelName(request.getPreferredModel())
            .userId(request.getUserId())
            .processingTime(System.currentTimeMillis())
            .formatAsMarkdown(request.isFormatAsMarkdown())
            .targetLanguage(request.getTargetLanguage())
            .build();

        ChatResponse transformedResponse = response;
        for (ResponseTransformer<ChatResponse> transformer : transformers) {
            if (transformer.supports(transformedResponse)) {
                try {
                    transformedResponse = transformer.transform(transformedResponse, transformContext);
                } catch (Exception e) {
                    log.error("Error applying transformer {}: {}", 
                        transformer.getClass().getSimpleName(), e.getMessage(), e);
                    // Continue with previous response if transformation fails
                }
            }
        }

        return transformedResponse;
    }
}

// Data classes
@Data
@Builder
public class ContentFilterResult {
    private String originalText;
    private String filteredText;
    private boolean blocked;
    private boolean modified;
    @Builder.Default
    private List<String> reasons = new ArrayList<>();
}

@Data
@Builder
public class EnhancedResponse {
    private ChatResponse originalResponse;
    private String enhancedContent;
    private SentimentResult sentimentAnalysis;
    private FactCheckResult factCheckResults;
    private double confidence;
    private Map<String, Object> metadata;
}

@Data
@Builder
public class SentimentResult {
    private String sentiment; // POSITIVE, NEGATIVE, NEUTRAL
    private double confidence;
    private Map<String, Double> scores; // detailed sentiment scores
}

@Data
@Builder
public class FactCheckResult {
    private boolean factual;
    private double confidence;
    private List<String> claims;
    private List<String> sources;
}

@Data
@Builder
public class EnhancedAIRequest {
    private String prompt;
    private String userId;
    private String preferredModel;
    private QualityRequirement qualityRequirement;
    private CostConstraint costConstraint;
    private Integer maxLatencyMs;
    private boolean formatAsMarkdown;
    private String targetLanguage;
    private Map<String, Object> customParameters = new HashMap<>();
}

// Configuration properties for interceptors
@ConfigurationProperties(prefix = "spring.ai.rate-limiting")
@Data
public class RateLimitingProperties {
    private boolean enabled = false;
    private double userRequestsPerSecond = 10.0;
    private Map<String, Double> modelRateLimits = new HashMap<>();
    
    public Double getModelRateLimit(String modelName) {
        return modelRateLimits.get(modelName);
    }
}

// Mock services for demonstration
@Service
public class SentimentAnalysisService {
    
    public SentimentResult analyze(String text) {
        // Mock implementation
        return SentimentResult.builder()
            .sentiment("POSITIVE")
            .confidence(0.85)
            .scores(Map.of("positive", 0.7, "negative", 0.1, "neutral", 0.2))
            .build();
    }
}

@Service
public class FactCheckingService {
    
    public FactCheckResult checkFacts(String text) {
        // Mock implementation
        return FactCheckResult.builder()
            .factual(true)
            .confidence(0.90)
            .claims(List.of("Claim 1", "Claim 2"))
            .sources(List.of("Source 1", "Source 2"))
            .build();
    }
}

// REST Controller showcasing the enhanced AI service
@RestController
@RequestMapping("/api/enhanced-ai")
@Slf4j
public class EnhancedAIController {

    private final EnhancedAIService enhancedAIService;

    public EnhancedAIController(EnhancedAIService enhancedAIService) {
        this.enhancedAIService = enhancedAIService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> enhancedChat(@RequestBody EnhancedAIRequest request) {
        try {
            ChatResponse response = enhancedAIService.processRequest(request);
            return ResponseEntity.ok(response);
        } catch (InterceptorBlockedException e) {
            return ResponseEntity.badRequest()
                .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing enhanced AI request", e);
            return ResponseEntity.internalServerError()
                .body(createErrorResponse("Internal server error"));
        }
    }

    @PostMapping("/stream")
    public Flux<ServerSentEvent<String>> enhancedStream(@RequestBody EnhancedAIRequest request) {
        return enhancedAIService.processStreamingRequest(request)
            .map(response -> ServerSentEvent.builder(response.getResult().getOutput().getContent())
                .id(UUID.randomUUID().toString())
                .event("message")
                .build())
            .onErrorResume(throwable -> {
                log.error("Error in enhanced streaming", throwable);
                return Flux.just(ServerSentEvent.builder("Error occurred")
                    .event("error")
                    .build());
            });
    }

    private ChatResponse createErrorResponse(String errorMessage) {
        AssistantMessage errorMsg = new AssistantMessage("Error: " + errorMessage);
        return ChatResponse.builder()
            .withGenerations(List.of(new Generation(errorMsg)))
            .withMetadata(Map.of("error", true, "timestamp", Instant.now().toString()))
            .build();
    }
}
```

Now let's create some real-world use cases and examples to demonstrate these advanced patterns:

```java
// Example 1: Enterprise Content Generation Platform
@RestController
@RequestMapping("/api/enterprise/content")
@Slf4j
public class EnterpriseContentController {

    private final EnhancedAIService aiService;
    private final ContentApprovalService approvalService;
    private final BrandGuidelinesService brandService;

    public EnterpriseContentController(EnhancedAIService aiService,
                                     ContentApprovalService approvalService,
                                     BrandGuidelinesService brandService) {
        this.aiService = aiService;
        this.approvalService = approvalService;
        this.brandService = brandService;
    }

    @PostMapping("/generate")
    public ResponseEntity<ContentGenerationResponse> generateContent(
            @RequestBody ContentGenerationRequest request) {
        
        // Validate brand guidelines compliance
        BrandGuidelinesCheck guidelinesCheck = brandService.checkCompliance(request);
        if (!guidelinesCheck.isCompliant()) {
            return ResponseEntity.badRequest()
                .body(ContentGenerationResponse.error("Content request violates brand guidelines: " 
                    + String.join(", ", guidelinesCheck.getViolations())));
        }

        // Build enhanced AI request with enterprise-specific parameters
        EnhancedAIRequest aiRequest = EnhancedAIRequest.builder()
            .prompt(buildEnterprisePrompt(request))
            .userId(request.getUserId())
            .preferredModel(selectModelForContentType(request.getContentType()))
            .qualityRequirement(QualityRequirement.HIGH)
            .costConstraint(request.getCostConstraint())
            .formatAsMarkdown(request.isMarkdownOutput())
            .targetLanguage(request.getTargetLanguage())
            .customParameters(Map.of(
                "brandGuidelines", guidelinesCheck.getGuidelines(),
                "contentType", request.getContentType(),
                "audience", request.getTargetAudience()
            ))
            .build();

        ChatResponse aiResponse = aiService.processRequest(aiRequest);
        
        // Create content for approval workflow
        ContentApprovalItem approvalItem = ContentApprovalItem.builder()
            .content(aiResponse.getResult().getOutput().getContent())
            .contentType(request.getContentType())
            .requesterId(request.getUserId())
            .generatedAt(Instant.now())
            .metadata(aiResponse.getMetadata())
            .build();

        String approvalId = approvalService.submitForApproval(approvalItem);

        return ResponseEntity.ok(ContentGenerationResponse.builder()
            .content(aiResponse.getResult().getOutput().getContent())
            .approvalId(approvalId)
            .status(ContentStatus.PENDING_APPROVAL)
            .metadata(aiResponse.getMetadata())
            .brandComplianceScore(guidelinesCheck.getComplianceScore())
            .build());
    }

    @GetMapping("/approval/{approvalId}")
    public ResponseEntity<ContentApprovalStatus> getApprovalStatus(@PathVariable String approvalId) {
        ContentApprovalStatus status = approvalService.getApprovalStatus(approvalId);
        return ResponseEntity.ok(status);
    }

    private String buildEnterprisePrompt(ContentGenerationRequest request) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Generate ").append(request.getContentType())
                    .append(" content for ").append(request.getTargetAudience())
                    .append(".\n\n");
        
        promptBuilder.append("Topic: ").append(request.getTopic()).append("\n");
        promptBuilder.append("Tone: ").append(request.getTone()).append("\n");
        promptBuilder.append("Length: ").append(request.getTargetLength()).append(" words\n\n");
        
        if (request.getKeyPoints() != null && !request.getKeyPoints().isEmpty()) {
            promptBuilder.append("Key points to include:\n");
            request.getKeyPoints().forEach(point -> 
                promptBuilder.append("- ").append(point).append("\n"));
            promptBuilder.append("\n");
        }
        
        promptBuilder.append("Additional requirements: ").append(request.getAdditionalRequirements());
        
        return promptBuilder.toString();
    }

    private String selectModelForContentType(ContentType contentType) {
        return switch (contentType) {
            case BLOG_POST, ARTICLE -> "openai"; // High quality for long-form content
            case SOCIAL_MEDIA -> "ollama"; // Fast for short content
            case TECHNICAL_DOCUMENTATION -> "openai"; // Accuracy for technical content
            case MARKETING_COPY -> "openai"; // Creativity for marketing
            default -> "openai";
        };
    }
}

// Example 2: Multi-tenant AI Customer Support System
@RestController
@RequestMapping("/api/support/ai")
@Slf4j
public class AICustomerSupportController {

    private final EnhancedAIService aiService;
    private final CustomerContextService contextService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final TicketingService ticketingService;

    public AICustomerSupportController(EnhancedAIService aiService,
                                     CustomerContextService contextService,
                                     KnowledgeBaseService knowledgeBaseService,
                                     TicketingService ticketingService) {
        this.aiService = aiService;
        this.contextService = contextService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ticketingService = ticketingService;
    }

    @PostMapping("/chat")
    public ResponseEntity<SupportChatResponse> handleSupportChat(
            @RequestBody SupportChatRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        
        try {
            // Get customer context
            CustomerContext customerContext = contextService.getCustomerContext(
                request.getCustomerId(), tenantId);
            
            // Search knowledge base for relevant information
            List<KnowledgeBaseItem> relevantArticles = knowledgeBaseService
                .searchRelevantContent(request.getMessage(), tenantId, 5);
            
            // Build context-aware prompt
            String contextualPrompt = buildSupportPrompt(request, customerContext, relevantArticles);
            
            // Configure AI request for support scenario
            EnhancedAIRequest aiRequest = EnhancedAIRequest.builder()
                .prompt(contextualPrompt)
                .userId("support-ai-" + tenantId)
                .preferredModel(selectSupportModel(request.getComplexity()))
                .qualityRequirement(QualityRequirement.HIGH)
                .costConstraint(CostConstraint.MEDIUM)
                .maxLatencyMs(5000) // Quick response for support
                .customParameters(Map.of(
                    "tenantId", tenantId,
                    "customerId", request.getCustomerId(),
                    "supportTier", customerContext.getSupportTier(),
                    "previousTickets", customerContext.getRecentTicketCount()
                ))
                .build();

            ChatResponse aiResponse = aiService.processRequest(aiRequest);
            String responseContent = aiResponse.getResult().getOutput().getContent();
            
            // Analyze if the response requires human escalation
            EscalationAnalysis escalation = analyzeForEscalation(request, aiResponse, customerContext);
            
            // Create support ticket if needed
            String ticketId = null;
            if (escalation.shouldCreateTicket()) {
                ticketId = ticketingService.createTicket(SupportTicket.builder()
                    .customerId(request.getCustomerId())
                    .tenantId(tenantId)
                    .subject(extractSubject(request.getMessage()))
                    .description(request.getMessage())
                    .aiResponse(responseContent)
                    .priority(escalation.getPriority())
                    .category(escalation.getCategory())
                    .build());
            }

            return ResponseEntity.ok(SupportChatResponse.builder()
                .response(responseContent)
                .confidence(extractConfidence(aiResponse))
                .escalationRequired(escalation.shouldEscalate())
                .ticketId(ticketId)
                .suggestedActions(escalation.getSuggestedActions())
                .knowledgeBaseArticles(relevantArticles.stream()
                    .map(article -> article.getTitle())
                    .collect(Collectors.toList()))
                .responseMetadata(aiResponse.getMetadata())
                .build());
                
        } catch (Exception e) {
            log.error("Error in AI customer support chat", e);
            return ResponseEntity.internalServerError()
                .body(SupportChatResponse.error("Sorry, I'm having trouble right now. " +
                    "Please try again or contact human support."));
        }
    }

    @PostMapping("/analyze-sentiment")
    public ResponseEntity<CustomerSentimentResponse> analyzeFeedback(
            @RequestBody CustomerFeedbackRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        
        EnhancedAIRequest aiRequest = EnhancedAIRequest.builder()
            .prompt("Analyze the sentiment and key themes in this customer feedback: " + request.getFeedback())
            .userId("sentiment-analyzer-" + tenantId)
            .preferredModel("openai")
            .qualityRequirement(QualityRequirement.HIGH)
            .build();

        ChatResponse aiResponse = aiService.processRequest(aiRequest);
        
        // Parse sentiment analysis from response
        SentimentAnalysisResult sentiment = parseSentimentFromResponse(aiResponse);
        
        return ResponseEntity.ok(CustomerSentimentResponse.builder()
            .overallSentiment(sentiment.getSentiment())
            .confidence(sentiment.getConfidence())
            .keyThemes(sentiment.getThemes())
            .urgencyLevel(sentiment.getUrgencyLevel())
            .recommendedActions(generateActionRecommendations(sentiment))
            .build());
    }

    private String buildSupportPrompt(SupportChatRequest request, 
                                    CustomerContext customerContext,
                                    List<KnowledgeBaseItem> relevantArticles) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a helpful customer support AI assistant. ");
        prompt.append("Provide accurate, friendly, and helpful responses.\n\n");
        
        prompt.append("Customer Information:\n");
        prompt.append("- Support Tier: ").append(customerContext.getSupportTier()).append("\n");
        prompt.append("- Account Status: ").append(customerContext.getAccountStatus()).append("\n");
        prompt.append("- Recent Tickets: ").append(customerContext.getRecentTicketCount()).append("\n\n");
        
        if (!relevantArticles.isEmpty()) {
            prompt.append("Relevant Knowledge Base Articles:\n");
            relevantArticles.forEach(article -> 
                prompt.append("- ").append(article.getTitle()).append(": ")
                      .append(article.getSummary()).append("\n"));
            prompt.append("\n");
        }
        
        prompt.append("Customer Question: ").append(request.getMessage()).append("\n\n");
        prompt.append("Please provide a helpful response. If you cannot solve the issue completely, ");
        prompt.append("suggest next steps or mention that the customer may need human assistance.");
        
        return prompt.toString();
    }

    private String selectSupportModel(SupportComplexity complexity) {
        return switch (complexity) {
            case LOW -> "ollama"; // Fast for simple queries
            case MEDIUM -> "openai"; // Balanced for most cases
            case HIGH -> "openai"; // Best quality for complex issues
        };
    }

    private EscalationAnalysis analyzeForEscalation(SupportChatRequest request, 
                                                   ChatResponse response,
                                                   CustomerContext context) {
        // Simple heuristic analysis - in production, use ML models
        boolean shouldEscalate = request.getMessage().toLowerCase().contains("cancel") ||
                                request.getMessage().toLowerCase().contains("refund") ||
                                context.getSupportTier().equals("PREMIUM") ||
                                context.getRecentTicketCount() > 3;
        
        return EscalationAnalysis.builder()
            .shouldEscalate(shouldEscalate)
            .shouldCreateTicket(shouldEscalate || request.getMessage().length() > 500)
            .priority(determinePriority(request, context))
            .category(determineCategory(request.getMessage()))
            .suggestedActions(generateSuggestedActions(request, context))
            .build();
    }

    // Additional helper methods...
    private double extractConfidence(ChatResponse response) {
        Map<String, Object> metadata = response.getMetadata();
        if (metadata.containsKey("confidence")) {
            return (Double) metadata.get("confidence");
        }
        return 0.8; // Default confidence
    }

    private String extractSubject(String message) {
        return message.length() > 50 ? message.substring(0, 47) + "..." : message;
    }

    private Priority determinePriority(SupportChatRequest request, CustomerContext context) {
        if (context.getSupportTier().equals("PREMIUM")) return Priority.HIGH;
        if (request.getMessage().toLowerCase().contains("urgent")) return Priority.HIGH;
        if (context.getRecentTicketCount() > 2) return Priority.MEDIUM;
        return Priority.LOW;
    }

    private String determineCategory(String message) {
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("billing") || lowerMessage.contains("payment")) return "BILLING";
        if (lowerMessage.contains("technical") || lowerMessage.contains("error")) return "TECHNICAL";
        if (lowerMessage.contains("account") || lowerMessage.contains("login")) return "ACCOUNT";
        return "GENERAL";
    }

    private List<String> generateSuggestedActions(SupportChatRequest request, CustomerContext context) {
        List<String> actions = new ArrayList<>();
        
        if (context.getSupportTier().equals("PREMIUM")) {
            actions.add("Prioritize response");
            actions.add("Assign senior agent if escalated");
        }
        
        if (context.getRecentTicketCount() > 2) {
            actions.add("Review customer history");
            actions.add("Consider proactive outreach");
        }
        
        return actions;
    }

    private SentimentAnalysisResult parseSentimentFromResponse(ChatResponse response) {
        // In a real implementation, you'd parse the structured response
        // For demo purposes, returning mock data
        return SentimentAnalysisResult.builder()
            .sentiment("NEGATIVE")
            .confidence(0.85)
            .themes(List.of("billing_issue", "poor_service"))
            .urgencyLevel("HIGH")
            .build();
    }

    private List<String> generateActionRecommendations(SentimentAnalysisResult sentiment) {
        List<String> recommendations = new ArrayList<>();
        
        if ("NEGATIVE".equals(sentiment.getSentiment())) {
            recommendations.add("Immediate follow-up required");
            recommendations.add("Consider compensation offer");
        }
        
        if ("HIGH".equals(sentiment.getUrgencyLevel())) {
            recommendations.add("Escalate to senior management");
            recommendations.add("Personal phone call recommended");
        }
        
        return recommendations;
    }
}

// Example 3: Intelligent Document Processing System
@RestController
@RequestMapping("/api/document/ai")
@Slf4j
public class IntelligentDocumentController {

    private final EnhancedAIService aiService;
    private final DocumentExtractionService extractionService;
    private final WorkflowService workflowService;

    public IntelligentDocumentController(EnhancedAIService aiService,
                                       DocumentExtractionService extractionService,
                                       WorkflowService workflowService) {
        this.aiService = aiService;
        this.extractionService = extractionService;
        this.workflowService = workflowService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<DocumentAnalysisResponse> analyzeDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("analysisType") DocumentAnalysisType analysisType,
            @RequestParam(value = "workflowId", required = false) String workflowId) {
        
        try {
            // Extract text from document
            DocumentExtractionResult extraction = extractionService.extractContent(file);
            
            if (!extraction.isSuccessful()) {
                return ResponseEntity.badRequest()
                    .body(DocumentAnalysisResponse.error("Failed to extract content from document"));
            }

            // Build analysis prompt based on type
            String analysisPrompt = buildAnalysisPrompt(analysisType, extraction);
            
            // Configure AI request for document analysis
            EnhancedAIRequest aiRequest = EnhancedAIRequest.builder()
                .prompt(analysisPrompt)
                .userId("document-analyzer")
                .preferredModel("openai") // Use high-quality model for document analysis
                .qualityRequirement(QualityRequirement.HIGH)
                .costConstraint(CostConstraint.HIGH) // Accuracy over cost for documents
                .maxLatencyMs(30000) // Allow more time for complex analysis
                .customParameters(Map.of(
                    "documentType", extraction.getDocumentType(),
                    "pageCount", extraction.getPageCount(),
                    "analysisType", analysisType.name()
                ))
                .build();

            ChatResponse aiResponse = aiService.processRequest(aiRequest);
            
            // Parse structured results from AI response
            DocumentAnalysisResult analysisResult = parseAnalysisResult(aiResponse, analysisType);
            
            // Start workflow if specified
            String workflowInstanceId = null;
            if (workflowId != null) {
                workflowInstanceId = workflowService.startWorkflow(workflowId, 
                    WorkflowContext.builder()
                        .documentId(extraction.getDocumentId())
                        .analysisResult(analysisResult)
                        .originalFileName(file.getOriginalFilename())
                        .build());
            }

            return ResponseEntity.ok(DocumentAnalysisResponse.builder()
                .documentId(extraction.getDocumentId())
                .analysisType(analysisType)
                .extractedText(extraction.getExtractedText())
                .analysisResult(analysisResult)
                .confidence(analysisResult.getOverallConfidence())
                .workflowInstanceId(workflowInstanceId)
                .processingTime(System.currentTimeMillis() - extraction.getProcessingStartTime())
                .metadata(Map.of(
                    "fileSize", file.getSize(),
                    "mimeType", file.getContentType(),
                    "pageCount", extraction.getPageCount()
                ))
                .build());
                
        } catch (Exception e) {
            log.error("Error analyzing document", e);
            return ResponseEntity.internalServerError()
                .body(DocumentAnalysisResponse.error("Failed to analyze document: " + e.getMessage()));
        }
    }

    @PostMapping("/classify")
    public ResponseEntity<DocumentClassificationResponse> classifyDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categories") List<String> possibleCategories) {
        
        try {
            DocumentExtractionResult extraction = extractionService.extractContent(file);
            
            String classificationPrompt = buildClassificationPrompt(extraction.getExtractedText(), possibleCategories);
            
            EnhancedAIRequest aiRequest = EnhancedAIRequest.builder()
                .prompt(classificationPrompt)
                .userId("document-classifier")
                .preferredModel("openai")
                .qualityRequirement(QualityRequirement.HIGH)
                .build();

            ChatResponse aiResponse = aiService.processRequest(aiRequest);
            DocumentClassification classification = parseClassificationResult(aiResponse);

            return ResponseEntity.ok(DocumentClassificationResponse.builder()
                .documentId(extraction.getDocumentId())
                .predictedCategory(classification.getCategory())
                .confidence(classification.getConfidence())
                .alternativeCategories(classification.getAlternatives())
                .reasoningExplanation(classification.getExplanation())
                .build());
                
        } catch (Exception e) {
            log.error("Error classifying document", e);
            return ResponseEntity.internalServerError()
                .body(DocumentClassificationResponse.error("Classification failed"));
        }
    }

    private String buildAnalysisPrompt(DocumentAnalysisType analysisType, DocumentExtractionResult extraction) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Analyze the following document content:\n\n");
        prompt.append(extraction.getExtractedText()).append("\n\n");
        
        switch (analysisType) {
            case SENTIMENT_ANALYSIS -> {
                prompt.append("Provide a detailed sentiment analysis including:\n");
                prompt.append("1. Overall sentiment (positive/negative/neutral)\n");
                prompt.append("2. Confidence score (0-1)\n");
                prompt.append("3. Key emotional themes\n");
                prompt.append("4. Supporting evidence from the text\n");
            }
            case KEY_INFORMATION_EXTRACTION -> {
                prompt.append("Extract key information including:\n");
                prompt.append("1. Important dates and deadlines\n");
                prompt.append("2. Names and entities\n");
                prompt.append("3. Financial figures and amounts\n");
                prompt.append("4. Action items or requirements\n");
                prompt.append("5. Contact information\n");
            }
            case LEGAL_COMPLIANCE_CHECK -> {
                prompt.append("Analyze for legal compliance including:\n");
                prompt.append("1. Regulatory requirements mentioned\n");
                prompt.append("2. Compliance risks identified\n");
                prompt.append("3. Required actions or approvals\n");
                prompt.append("4. Potential legal issues\n");
            }
            case SUMMARY_GENERATION -> {
                prompt.append("Create a comprehensive summary including:\n");
                prompt.append("1. Executive summary (2-3 sentences)\n");
                prompt.append("2. Key points and main topics\n");
                prompt.append("3. Important details and facts\n");
                prompt.append("4. Conclusions or recommendations\n");
            }
        }
        
        prompt.append("\nProvide the analysis in a structured JSON format.");
        return prompt.toString();
    }

    private String buildClassificationPrompt(String extractedText, List<String> categories) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Classify the following document into one of these categories:\n");
        categories.forEach(category -> prompt.append("- ").append(category).append("\n"));
        prompt.append("\nDocument content:\n");
        prompt.append(extractedText).append("\n\n");
        prompt.append("Respond with:\n");
        prompt.append("1. Primary category\n");
        prompt.append("2. Confidence score (0-1)\n");
        prompt.append("3. Alternative categories (if applicable)\n");
        prompt.append("4. Brief explanation of classification reasoning\n");
        
        return prompt.toString();
    }

    private DocumentAnalysisResult parseAnalysisResult(ChatResponse response, DocumentAnalysisType type) {
        // In production, implement proper JSON parsing
        String content = response.getResult().getOutput().getContent();
        
        return DocumentAnalysisResult.builder()
            .analysisType(type)
            .rawResult(content)
            .overallConfidence(0.85)
            .keyFindings(extractKeyFindings(content))
            .structuredData(parseStructuredData(content))
            .build();
    }

    private DocumentClassification parseClassificationResult(ChatResponse response) {
        // Mock parsing - implement proper JSON parsing in production
        return DocumentClassification.builder()
            .category("CONTRACT")
            .confidence(0.92)
            .alternatives(List.of("LEGAL_DOCUMENT", "AGREEMENT"))
            .explanation("Document contains contractual terms and legal language")
            .build();
    }

    private List<String> extractKeyFindings(String content) {
        // Simple extraction - use NLP in production
        return Arrays.stream(content.split("\n"))
            .filter(line -> line.trim().startsWith("-") || line.trim().startsWith("•"))
            .map(String::trim)
            .collect(Collectors.toList());
    }

    private Map<String, Object> parseStructuredData(String content) {
        // Parse structured data from AI response
        Map<String, Object> data = new HashMap<>();
        data.put("analyzed", true);
        data.put("timestamp", Instant.now().toString());
        return data;
    }
}

// Supporting Data Classes
@Data
@Builder
public class ContentGenerationRequest {
    private String userId;
    private ContentType contentType;
    private String topic;
    private String tone;
    private int targetLength;
    private List<String> keyPoints;
    private String additionalRequirements;
    private String targetAudience;
    private String targetLanguage;
    private boolean markdownOutput;
    private CostConstraint costConstraint;
}

@Data
@Builder
public class ContentGenerationResponse {
    private String content;
    private String approvalId;
    private ContentStatus status;
    private Map<String, Object> metadata;
    private double brandComplianceScore;
    private String error;
    
    public static ContentGenerationResponse error(String message) {
        return ContentGenerationResponse.builder()
            .status(ContentStatus.ERROR)
            .error(message)
            .build();
    }
}

@Data
@Builder
public class SupportChatRequest {
    private String customerId;
    private String message;
    private SupportComplexity complexity = SupportComplexity.MEDIUM;
    private String sessionId;
    private List<String> previousMessages;
}

@Data
@Builder
public class SupportChatResponse {
    private String response;
    private double confidence;
    private boolean escalationRequired;
    private String ticketId;
    private List<String> suggestedActions;
    private List<String> knowledgeBaseArticles;
    private Map<String, Object> responseMetadata;
    private String error;
    
    public static SupportChatResponse error(String message) {
        return SupportChatResponse.builder()
            .error(message)
            .response(message)
            .confidence(0.0)
            .build();
    }
}

@Data
@Builder
public class DocumentAnalysisResponse {
    private String documentId;
    private DocumentAnalysisType analysisType;
    private String extractedText;
    private DocumentAnalysisResult analysisResult;
    private double confidence;
    private String workflowInstanceId;
    private long processingTime;
    private Map<String, Object> metadata;
    private String error;
    
    public static DocumentAnalysisResponse error(String message) {
        return DocumentAnalysisResponse.builder()
            .error(message)
            .confidence(0.0)
            .build();
    }
}

// Enums
public enum ContentType {
    BLOG_POST,
    ARTICLE,
    SOCIAL_MEDIA,
    TECHNICAL_DOCUMENTATION,
    MARKETING_COPY,
    EMAIL,
    PRESS_RELEASE
}

public enum ContentStatus {
    GENERATED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    ERROR
}

public enum SupportComplexity {
    LOW,
    MEDIUM,
    HIGH
}

public enum DocumentAnalysisType {
    SENTIMENT_ANALYSIS,
    KEY_INFORMATION_EXTRACTION,
    LEGAL_COMPLIANCE_CHECK,
    SUMMARY_GENERATION,
    CLASSIFICATION
}

public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

// Service Classes
@Service
public class ContentApprovalService {
    
    public String submitForApproval(ContentApprovalItem item) {
        // Generate approval ID and start approval workflow
        return "APPROVAL-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    public ContentApprovalStatus getApprovalStatus(String approvalId) {
        // Mock implementation
        return ContentApprovalStatus.builder()
            .approvalId(approvalId)
            .status("PENDING")
            .submittedAt(Instant.now())
            .build();
    }
}

@Service
public class BrandGuidelinesService {
    
    public BrandGuidelinesCheck checkCompliance(ContentGenerationRequest request) {
        // Mock compliance check
        return BrandGuidelinesCheck.builder()
            .compliant(true)
            .complianceScore(0.95)
            .guidelines(Map.of("tone", "professional", "audience", "business"))
            .violations(List.of())
            .build();
    }
}

@Service
public class CustomerContextService {
    
    public CustomerContext getCustomerContext(String customerId, String tenantId) {
        // Mock customer context
        return CustomerContext.builder()
            .customerId(customerId)
            .supportTier("STANDARD")
            .accountStatus("ACTIVE")
            .recentTicketCount(1)
            .preferredLanguage("en")
            .build();
    }
}

@Service
public class KnowledgeBaseService {
    
    public List<KnowledgeBaseItem> searchRelevantContent(String query, String tenantId, int limit) {
        // Mock knowledge base search
        return List.of(
            KnowledgeBaseItem.builder()
                .id("KB-001")
                .title("How to reset your password")
                .summary("Step-by-step guide for password reset")
                .relevanceScore(0.85)
                .build()
        );
    }
}

@Service
public class TicketingService {
    
    public String createTicket(SupportTicket ticket) {
        // Create support ticket
        return "TICKET-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

@Service
public class DocumentExtractionService {
    
    public DocumentExtractionResult extractContent(MultipartFile file) {
        // Mock document extraction
        return DocumentExtractionResult.builder()
            .documentId("DOC-" + UUID.randomUUID().toString().substring(0, 8))
            .successful(true)
            .extractedText("Sample extracted text content...")
            .documentType(determineDocumentType(file.getOriginalFilename()))
            .pageCount(1)
            .processingStartTime(System.currentTimeMillis())
            .build();
    }
    
    private String determineDocumentType(String filename) {
        if (filename.endsWith(".pdf")) return "PDF";
        if (filename.endsWith(".docx")) return "WORD";
        if (filename.endsWith(".txt")) return "TEXT";
        return "UNKNOWN";
    }
}

@Service
public class WorkflowService {
    
    public String startWorkflow(String workflowId, WorkflowContext context) {
        // Start document processing workflow
        return "WORKFLOW-INSTANCE-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

// Data Transfer Objects
@Data
@Builder
public class CustomerContext {
    private String customerId;
    private String supportTier;
    private String accountStatus;
    private int recentTicketCount;
    private String preferredLanguage;
}

@Data
@Builder
public class KnowledgeBaseItem {
    private String id;
    private String title;
    private String summary;
    private double relevanceScore;
}

@Data
@Builder
public class SupportTicket {
    private String customerId;
    private String tenantId;
    private String subject;
    private String description;
    private String aiResponse;
    private Priority priority;
    private String category;
}

@Data
@Builder
public class EscalationAnalysis {
    private boolean shouldEscalate;
    private boolean shouldCreateTicket;
    private Priority priority;
    private String category;
    private List<String> suggestedActions;
}

@Data
@Builder
public class DocumentExtractionResult {
    private String documentId;
    private boolean successful;
    private String extractedText;
    private String documentType;
    private int pageCount;
    private long processingStartTime;
}

@Data
@Builder
public class DocumentAnalysisResult {
    private DocumentAnalysisType analysisType;
    private String rawResult;
    private double overallConfidence;
    private List<String> keyFindings;
    private Map<String, Object> structuredData;
}

@Data
@Builder
public class DocumentClassification {
    private String category;
    private double confidence;
    private List<String> alternatives;
    private String explanation;
}

@Data
@Builder
public class BrandGuidelinesCheck {
    private boolean compliant;
    private double complianceScore;
    private Map<String, String> guidelines;
    private List<String> violations;
}

@Data
@Builder
public class ContentApprovalItem {
    private String content;
    private ContentType contentType;
    private String requesterId;
    private Instant generatedAt;
    private Map<String, Object> metadata;
}

@Data
@Builder
public class ContentApprovalStatus {
    private String approvalId;
    private String status;
    private Instant submittedAt;
}

// Example configuration file (application.yml)
/*
spring:
  ai:
    advanced:
      enabled: true
      caching:
        enabled: true
        ttl: PT1H
        max-size: 1000
        cacheable-operations: 
          - embedding
          - simple-chat
          - classification
      batch:
        enabled: true
        core-pool-size: 5
        max-pool-size: 10
        queue-capacity: 100
        batch-size: 10
        flush-interval: PT5S
      cost-tracking:
        enabled: true
        model-costs:
          openai: 0.002
          ollama: 0.0001
        reporting-interval: PT15M
      models:
        openai:
          type: chat
          enabled: true
          priority: 10
          supported-operations:
            - chat
            - embedding
            - analysis
        ollama:
          type: chat
          enabled: true
          priority: 5
          supported-operations:
            - chat
            - simple-analysis
    security:
      input-validation: true
      sanitize-input: true
      max-input-length: 10000
      enable-rate-limiting: true
      rate-limit-per-second: 10
    rate-limiting:
      enabled: true
      user-requests-per-second: 5.0
      model-rate-limits:
        openai: 10.0
        ollama: 20.0
    routing:
      fallback-order:
        - openai
        - ollama
        - custom
      task-mappings:
        chat: openai
        code-generation: openai
        text-analysis: ollama
        creative-writing: openai
      performance-tiers:
        fast: ollama
        balanced: openai
        high-quality: openai
      cost-tiers:
        low: ollama
        medium: openai
        high: openai
*/
```

Phase 11 Summary
Congratulations! You've now mastered the advanced patterns and customization techniques in Spring AI. Let me summarize what we've covered in Phase 11:
11.1 Custom Model Integration ✅
Key Concepts Mastered:

Ollama Integration: Local model deployment for privacy and cost optimization
Custom REST API Integration: Flexible integration with any AI model via REST APIs
Model Serving Optimization: Performance tuning and resource management

Real-world Applications:

Privacy-sensitive applications (healthcare, finance)
Cost optimization for high-volume applications
Custom model fine-tuning and deployment

11.2 Model Switching and Routing ✅
Key Concepts Mastered:

Dynamic Model Selection: Intelligent routing based on task type, performance, and cost
Health Monitoring: Real-time model health tracking and automatic failover
A/B Testing Framework: Complete system for comparing model performance
Load Balancing: Distribution of requests across multiple models

Real-world Applications:

Production systems requiring high availability
Cost optimization through intelligent routing
Performance optimization based on use case
Data-driven model selection decisions

11.3 Advanced Customization ✅
Key Concepts Mastered:

Custom Auto-configuration: Sophisticated Spring Boot configuration
Interceptor Chains: Pre and post-processing of requests/responses
Response Transformers: Content enhancement and formatting
Security Integration: Input validation, content filtering, rate limiting

Real-world Applications:

Enterprise-grade security and compliance
Multi-tenant architectures
Content moderation and safety
Performance monitoring and analytics

Real-World Use Cases Implemented:

Enterprise Content Generation Platform 📝

Brand guidelines compliance
Multi-stage approval workflows
Quality assurance integration


Multi-tenant AI Customer Support System 🎧

Context-aware responses
Automatic escalation logic
Sentiment analysis integration


Intelligent Document Processing System 📄

Multi-format document analysis
Workflow automation
Compliance checking



Best Practices You've Learned:
🔧 Configuration Management

Environment-specific configurations
Feature toggles for gradual rollouts
Performance tuning parameters

🛡️ Security & Compliance

Input validation and sanitization
Content filtering and moderation
Rate limiting and abuse prevention

📊 Monitoring & Observability

Comprehensive metrics collection
Performance tracking
Cost monitoring and optimization

🚀 Production Readiness

Health checks and failover mechanisms
Caching strategies for performance
Graceful error handling