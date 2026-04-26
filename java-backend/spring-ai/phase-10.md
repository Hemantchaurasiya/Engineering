# Phase 10: Spring AI Security and Production Readiness

## 10.1 Security Best Practices

### API Key Management

#### External Secret Management

**HashiCorp Vault Integration:**

```java
@Configuration
@EnableConfigurationProperties(VaultProperties.class)
public class VaultSecretConfig {
    
    @Bean
    public VaultTemplate vaultTemplate() {
        VaultEndpoint vaultEndpoint = new VaultEndpoint();
        vaultEndpoint.setHost("your-vault-host");
        vaultEndpoint.setPort(8200);
        vaultEndpoint.setScheme("https");
        
        ClientAuthentication clientAuth = new TokenAuthentication("vault-token");
        return new VaultTemplate(vaultEndpoint, clientAuth);
    }
    
    @Bean
    public OpenAiChatModel openAiChatModel(VaultTemplate vaultTemplate) {
        VaultResponse response = vaultTemplate.read("secret/openai");
        String apiKey = (String) response.getData().get("api-key");
        
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(OpenAiChatOptions.DEFAULT_MODEL)
            .build();
    }
}
```

**AWS Secrets Manager Integration:**

```java
@Configuration
public class AwsSecretsConfig {
    
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }
    
    @Bean
    public OpenAiChatModel openAiChatModel(SecretsManagerClient secretsClient) {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
            .secretId("prod/spring-ai/openai-key")
            .build();
        
        GetSecretValueResponse response = secretsClient.getSecretValue(request);
        String apiKey = response.secretString();
        
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName("gpt-4")
            .build();
    }
}
```

**Azure Key Vault Integration:**

```java
@Configuration
public class AzureKeyVaultConfig {
    
    @Bean
    public SecretClient secretClient() {
        return new SecretClientBuilder()
            .vaultUrl("https://your-vault.vault.azure.net/")
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
    }
    
    @Bean
    public AzureOpenAiChatModel azureOpenAiChatModel(SecretClient secretClient) {
        KeyVaultSecret secret = secretClient.getSecret("azure-openai-key");
        String apiKey = secret.getValue();
        
        return AzureOpenAiChatModel.builder()
            .apiKey(apiKey)
            .endpoint("https://your-resource.openai.azure.com/")
            .deploymentName("gpt-4")
            .build();
    }
}
```

#### Key Rotation Strategies

**Automatic Key Rotation Service:**

```java
@Service
@Slf4j
public class ApiKeyRotationService {
    
    private final SecretsManagerClient secretsClient;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void rotateApiKeys() {
        try {
            // Check if rotation is needed
            if (shouldRotateKey()) {
                String newKey = generateNewApiKey();
                updateSecretInVault(newKey);
                
                // Graceful transition
                scheduleKeyTransition(newKey);
                
                log.info("API key rotation completed successfully");
            }
        } catch (Exception e) {
            log.error("Failed to rotate API key", e);
            // Send alert to monitoring system
            eventPublisher.publishEvent(new KeyRotationFailedEvent(e.getMessage()));
        }
    }
    
    private boolean shouldRotateKey() {
        // Check key age, usage patterns, security events
        String lastRotation = redisTemplate.opsForValue()
            .get("api-key:last-rotation");
        
        if (lastRotation == null) return true;
        
        LocalDateTime lastRotationTime = LocalDateTime.parse(lastRotation);
        return ChronoUnit.DAYS.between(lastRotationTime, LocalDateTime.now()) >= 30;
    }
    
    private void scheduleKeyTransition(String newKey) {
        // Implement gradual transition
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(300000); // 5 minutes grace period
                activateNewKey(newKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
```

#### Environment-Specific Configurations

**Multi-Environment Configuration:**

```java
@Configuration
@Profile("production")
public class ProductionSecurityConfig {
    
    @Bean
    @Primary
    public OpenAiChatModel productionChatModel() {
        return OpenAiChatModel.builder()
            .apiKey("${spring.ai.openai.api-key}")
            .modelName("gpt-4")
            .maxTokens(2000)
            .temperature(0.7)
            .build();
    }
    
    @Bean
    public SecurityFilter aiSecurityFilter() {
        return new SecurityFilter();
    }
}

@Configuration
@Profile("development")
public class DevelopmentSecurityConfig {
    
    @Bean
    public OpenAiChatModel developmentChatModel() {
        return OpenAiChatModel.builder()
            .apiKey("${spring.ai.openai.dev-api-key}")
            .modelName("gpt-3.5-turbo")
            .maxTokens(1000)
            .temperature(0.5)
            .build();
    }
}
```

### Input Validation and Sanitization

#### Prompt Injection Prevention

**Input Sanitization Service:**

```java
@Service
@Slf4j
public class InputSanitizationService {
    
    private final List<Pattern> suspiciousPatterns;
    private final ContentModerationClient moderationClient;
    
    public InputSanitizationService() {
        this.suspiciousPatterns = initializeSuspiciousPatterns();
    }
    
    public SanitizedInput sanitizeAndValidate(String userInput) {
        // Step 1: Basic sanitization
        String sanitized = basicSanitization(userInput);
        
        // Step 2: Prompt injection detection
        PromptInjectionResult injectionResult = detectPromptInjection(sanitized);
        
        // Step 3: Content moderation
        ModerationResult moderationResult = moderateContent(sanitized);
        
        // Step 4: Build result
        return SanitizedInput.builder()
            .originalInput(userInput)
            .sanitizedInput(sanitized)
            .isValid(injectionResult.isSafe() && moderationResult.isSafe())
            .violations(mergeViolations(injectionResult, moderationResult))
            .riskScore(calculateRiskScore(injectionResult, moderationResult))
            .build();
    }
    
    private String basicSanitization(String input) {
        return input
            .replaceAll("<script[^>]*>.*?</script>", "")
            .replaceAll("(?i)\\b(ignore|disregard|forget)\\s+(previous|above|all)\\s+(instructions?|prompts?)\\b", "[FILTERED]")
            .replaceAll("(?i)\\b(system|assistant|user)\\s*:", "[FILTERED]:")
            .trim();
    }
    
    private PromptInjectionResult detectPromptInjection(String input) {
        List<String> violations = new ArrayList<>();
        int riskScore = 0;
        
        for (Pattern pattern : suspiciousPatterns) {
            Matcher matcher = pattern.matcher(input.toLowerCase());
            if (matcher.find()) {
                violations.add("Suspicious pattern detected: " + pattern.pattern());
                riskScore += 10;
            }
        }
        
        // Advanced ML-based detection
        if (containsAdvancedInjection(input)) {
            violations.add("Advanced injection pattern detected");
            riskScore += 50;
        }
        
        return new PromptInjectionResult(violations.isEmpty(), violations, riskScore);
    }
    
    private List<Pattern> initializeSuspiciousPatterns() {
        return Arrays.asList(
            Pattern.compile("\\b(ignore|disregard|forget)\\s+(previous|above|all)\\b"),
            Pattern.compile("\\b(system|assistant)\\s*:\\s*\\w+"),
            Pattern.compile("\\[\\s*(system|assistant|user)\\s*\\]"),
            Pattern.compile("\\b(jailbreak|dan|do anything now)\\b"),
            Pattern.compile("\\b(act as if|pretend to be|roleplay as)\\b.*\\b(admin|root|system)\\b")
        );
    }
}
```

**Advanced Content Filtering:**

```java
@Component
public class AdvancedContentFilter {
    
    private final OpenAiModerationModel moderationModel;
    private final CustomClassificationModel classificationModel;
    
    public ContentFilterResult filterContent(String content, ContentContext context) {
        // Multi-layer filtering approach
        List<FilterResult> results = new ArrayList<>();
        
        // Layer 1: OpenAI Moderation API
        results.add(checkWithOpenAiModeration(content));
        
        // Layer 2: Custom business rules
        results.add(applyBusinessRules(content, context));
        
        // Layer 3: Domain-specific filtering
        results.add(applyDomainSpecificRules(content, context));
        
        // Layer 4: Context-aware filtering
        results.add(applyContextualFiltering(content, context));
        
        return aggregateFilterResults(results);
    }
    
    private FilterResult checkWithOpenAiModeration(String content) {
        try {
            ModerationRequest request = ModerationRequest.builder()
                .input(content)
                .build();
            
            ModerationResponse response = moderationModel.moderate(request);
            
            return FilterResult.builder()
                .passed(!response.getResults().get(0).isFlagged())
                .categories(response.getResults().get(0).getCategories())
                .scores(response.getResults().get(0).getCategoryScores())
                .build();
        } catch (Exception e) {
            log.error("Moderation API failed", e);
            // Fail securely - reject when moderation fails
            return FilterResult.builder()
                .passed(false)
                .error("Moderation service unavailable")
                .build();
        }
    }
    
    private FilterResult applyBusinessRules(String content, ContentContext context) {
        BusinessRuleEngine ruleEngine = new BusinessRuleEngine();
        
        // Example business rules
        ruleEngine.addRule(new PIIDetectionRule())
                  .addRule(new CompetitorMentionRule())
                  .addRule(new ConfidentialInfoRule())
                  .addRule(new ComplianceRule(context.getIndustry()));
        
        return ruleEngine.evaluate(content, context);
    }
}
```

#### Data Privacy Protection

**PII Detection and Anonymization:**

```java
@Service
public class PIIProtectionService {
    
    private final List<PIIDetector> detectors;
    private final EncryptionService encryptionService;
    
    public PIIProtectionResult protectPII(String content) {
        Map<PIIType, List<PIIMatch>> detectedPII = detectAllPII(content);
        String anonymizedContent = anonymizeContent(content, detectedPII);
        
        return PIIProtectionResult.builder()
            .originalContent(content)
            .anonymizedContent(anonymizedContent)
            .detectedPII(detectedPII)
            .hasPersonalData(!detectedPII.isEmpty())
            .build();
    }
    
    private Map<PIIType, List<PIIMatch>> detectAllPII(String content) {
        Map<PIIType, List<PIIMatch>> allMatches = new HashMap<>();
        
        for (PIIDetector detector : detectors) {
            List<PIIMatch> matches = detector.detect(content);
            if (!matches.isEmpty()) {
                allMatches.put(detector.getType(), matches);
            }
        }
        
        return allMatches;
    }
    
    private String anonymizeContent(String content, Map<PIIType, List<PIIMatch>> piiMatches) {
        String result = content;
        
        for (Map.Entry<PIIType, List<PIIMatch>> entry : piiMatches.entrySet()) {
            for (PIIMatch match : entry.getValue()) {
                String replacement = generateReplacement(entry.getKey(), match);
                result = result.replace(match.getValue(), replacement);
            }
        }
        
        return result;
    }
    
    @Component
    public static class EmailDetector implements PIIDetector {
        private static final Pattern EMAIL_PATTERN = 
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
        
        @Override
        public List<PIIMatch> detect(String content) {
            List<PIIMatch> matches = new ArrayList<>();
            Matcher matcher = EMAIL_PATTERN.matcher(content);
            
            while (matcher.find()) {
                matches.add(new PIIMatch(
                    matcher.group(),
                    matcher.start(),
                    matcher.end(),
                    0.95 // confidence score
                ));
            }
            
            return matches;
        }
        
        @Override
        public PIIType getType() {
            return PIIType.EMAIL;
        }
    }
}
```

## 10.2 Production Deployment

### Containerization

#### Docker Image Optimization

**Multi-stage Dockerfile:**

```dockerfile
# Multi-stage build for optimal image size
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Download dependencies first (better layer caching)
RUN ./mvnw dependency:go-offline -B

# Build the application
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine AS runtime

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/sh -D appuser

WORKDIR /app

# Copy only the jar file from builder stage
COPY --from=builder /app/target/spring-ai-app.jar ./app.jar

# Set proper ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Resource Allocation Configuration:**

```yaml
# docker-compose.yml for local development
version: '3.8'

services:
  spring-ai-app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_AI_OPENAI_API_KEY=${OPENAI_API_KEY}
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/springai
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '0.5'
        reservations:
          memory: 512M
          cpus: '0.25'
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: springai
      POSTGRES_USER: springai
      POSTGRES_PASSWORD: springai123
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U springai"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
  redis_data:
```

### Cloud Deployment

#### AWS Deployment with ECS Fargate

**ECS Task Definition:**

```java
@Configuration
public class AwsDeploymentConfig {
    
    @Bean
    @Profile("aws")
    public CloudWatchMetricRegistry cloudWatchMetricRegistry() {
        return CloudWatchMetricRegistry.builder(CloudWatchConfig.DEFAULT)
            .cloudWatchClient(CloudWatchClient.builder()
                .region(Region.US_EAST_1)
                .build())
            .namespace("SpringAI/Production")
            .build();
    }
    
    @Bean
    @Profile("aws")
    public HealthIndicator ecsHealthIndicator() {
        return new AbstractHealthIndicator() {
            @Override
            protected void doHealthCheck(Health.Builder builder) throws Exception {
                // Custom ECS health check logic
                builder.up()
                    .withDetail("ecs-task", "running")
                    .withDetail("availability-zone", getAvailabilityZone())
                    .withDetail("instance-id", getInstanceId());
            }
        };
    }
}
```

**Terraform Configuration for AWS:**

```hcl
# main.tf
resource "aws_ecs_cluster" "spring_ai_cluster" {
  name = "spring-ai-production"
  
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  
  capacity_providers = ["FARGATE"]
}

resource "aws_ecs_task_definition" "spring_ai_task" {
  family                   = "spring-ai-app"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn           = aws_iam_role.ecs_task_role.arn
  
  container_definitions = jsonencode([
    {
      name  = "spring-ai-app"
      image = "${aws_ecr_repository.spring_ai_repo.repository_url}:latest"
      
      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]
      
      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "production,aws"
        }
      ]
      
      secrets = [
        {
          name      = "SPRING_AI_OPENAI_API_KEY"
          valueFrom = aws_secretsmanager_secret.openai_key.arn
        }
      ]
      
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.spring_ai_logs.name
          awslogs-region        = "us-east-1"
          awslogs-stream-prefix = "ecs"
        }
      }
      
      healthCheck = {
        command = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
        interval = 30
        timeout = 5
        retries = 3
        startPeriod = 60
      }
    }
  ])
}
```

#### Azure Spring Apps Deployment

**Azure Configuration:**

```java
@Configuration
@Profile("azure")
public class AzureDeploymentConfig {
    
    @Bean
    public ApplicationInsightsTelemetryClient telemetryClient() {
        TelemetryConfiguration config = TelemetryConfiguration.getActive();
        config.setInstrumentationKey("${azure.application-insights.instrumentation-key}");
        return new TelemetryClient(config);
    }
    
    @Bean
    public AzureOpenAiChatModel azureOpenAiChatModel(
            @Value("${spring.ai.azure.openai.api-key}") String apiKey,
            @Value("${spring.ai.azure.openai.endpoint}") String endpoint) {
        
        return AzureOpenAiChatModel.builder()
            .apiKey(apiKey)
            .endpoint(endpoint)
            .deploymentName("gpt-4")
            .temperature(0.7)
            .maxTokens(2000)
            .build();
    }
    
    @EventListener
    public void handleAzureEvents(ApplicationReadyEvent event) {
        telemetryClient().trackEvent("ApplicationStarted");
    }
}
```

#### Load Balancing Strategies

**Custom Load Balancer Configuration:**

```java
@Configuration
public class LoadBalancingConfig {
    
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    @Bean
    public IRule ribbonRule() {
        // Custom rule for AI service load balancing
        return new WeightedResponseTimeRule();
    }
    
    @Component
    public class AIServiceLoadBalancer {
        
        private final List<AIServiceInstance> instances;
        private final CircuitBreakerRegistry circuitBreakerRegistry;
        
        public AIServiceInstance selectInstance(String modelType, int estimatedTokens) {
            return instances.stream()
                .filter(instance -> instance.supportsModel(modelType))
                .filter(instance -> instance.hasCapacity(estimatedTokens))
                .filter(instance -> isHealthy(instance))
                .min(Comparator.comparing(AIServiceInstance::getCurrentLoad))
                .orElseThrow(() -> new NoAvailableInstanceException("No healthy instances available"));
        }
        
        private boolean isHealthy(AIServiceInstance instance) {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry
                .circuitBreaker(instance.getId());
            return circuitBreaker.getState() == CircuitBreaker.State.CLOSED;
        }
    }
}
```

### Auto-scaling Configuration

**Kubernetes HPA Configuration:**

```yaml
# k8s-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-ai-app
  labels:
    app: spring-ai-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: spring-ai-app
  template:
    metadata:
      labels:
        app: spring-ai-app
    spec:
      containers:
      - name: spring-ai-app
        image: your-registry/spring-ai-app:latest
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE
          value: "*"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 15
---
apiVersion: v1
kind: Service
metadata:
  name: spring-ai-service
spec:
  selector:
    app: spring-ai-app
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: spring-ai-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: spring-ai-app
  minReplicas: 2
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  - type: Pods
    pods:
      metric:
        name: ai_requests_per_second
      target:
        type: AverageValue
        averageValue: "30"
```

## 10.3 Error Handling and Resilience

### Retry Mechanisms

**Advanced Retry Configuration:**

```java
@Configuration
public class ResilienceConfig {
    
    @Bean
    public RetryTemplate aiServiceRetryTemplate() {
        return RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(1000, 2, 10000)
            .retryOn(ResourceAccessException.class)
            .retryOn(HttpServerErrorException.class)
            .build();
    }
    
    @Bean
    public Retry aiServiceRetry() {
        return Retry.of("ai-service", RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .retryExceptions(AIServiceException.class, HttpServerErrorException.class)
            .ignoreExceptions(IllegalArgumentException.class)
            .build());
    }
    
    @Component
    public class ResilientAIService {
        
        private final OpenAiChatModel chatModel;
        private final RetryTemplate retryTemplate;
        private final Retry retry;
        
        @Retryable(
            value = {AIServiceException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
        )
        public ChatResponse generateResponse(String prompt) {
            return retryTemplate.execute(context -> {
                log.info("Attempt {} for prompt: {}", 
                        context.getRetryCount() + 1, prompt);
                
                return chatModel.call(new Prompt(prompt));
            });
        }
        
        @Recover
        public ChatResponse recover(AIServiceException ex, String prompt) {
            log.error("All retry attempts failed for prompt: {}", prompt, ex);
            return createFallbackResponse(prompt, ex);
        }
        
        // Resilience4j annotation approach
        @Retry(name = "ai-service")
        @CircuitBreaker(name = "ai-service")
        @TimeLimiter(name = "ai-service")
        public CompletableFuture<ChatResponse> generateResponseAsync(String prompt) {
            return CompletableFuture.supplyAsync(() -> {
                return chatModel.call(new Prompt(prompt));
            });
        }
    }
}
```

### Circuit Breaker Patterns

**Advanced Circuit Breaker Implementation:**

```java
@Component
public class AIServiceCircuitBreaker {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final OpenAiChatModel primaryModel;
    private final AnthropicChatModel fallbackModel;
    
    public AIServiceCircuitBreaker() {
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .build()
        );
    }
    
    public ChatResponse callWithCircuitBreaker(String prompt, String modelName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry
            .circuitBreaker(modelName);
        
        Supplier<ChatResponse> decoratedSupplier = CircuitBreaker
            .decorateSupplier(circuitBreaker, () -> {
                switch (modelName) {
                    case "openai":
                        return primaryModel.call(new Prompt(prompt));
                    case "anthropic":
                        return fallbackModel.call(new Prompt(prompt));
                    default:
                        throw new IllegalArgumentException("Unknown model: " + modelName);
                }
            });
        
        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is open for model: {}", modelName);
            return handleCircuitBreakerOpen(prompt, modelName);
        }
    }
    
    private ChatResponse handleCircuitBreakerOpen(String prompt, String modelName) {
        // Implement fallback strategy
        if ("openai".equals(modelName)) {
            log.info("Falling back to Anthropic model");
            return callWithCircuitBreaker(prompt, "anthropic");
        } else {
            // Last resort - cached response or error
            return getCachedResponseOrError(prompt);
        }
    }
    
    @EventListener
    public void handleCircuitBreakerEvents(CircuitBreakerOnStateTransitionEvent event) {
        log.info("Circuit breaker {} transitioned from {} to {}", 
                event.getCircuitBreakerName(),
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState());
        
        // Send metrics to monitoring system
        sendCircuitBreakerMetrics(event);
    }
}
```

### Graceful Degradation

**Fallback Strategies Implementation:**

```java
@Service
public class GracefulDegradationService {
    
    private final List<ChatModel> modelHierarchy;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;
    
    public ChatResponse generateResponseWithFallback(String prompt, RequestContext context) {
        // Strategy 1: Try primary models in order
        for (ChatModel model : modelHierarchy) {
            try {
                        ChatResponse response = model.call(new Prompt(prompt));
                if (isValidResponse(response)) {
                    return response;
                }
            } catch (Exception e) {
                log.warn("Model {} failed, trying next fallback", model.getClass().getSimpleName(), e);
                continue;
            }
        }
        
        // Strategy 2: Try cached responses
        ChatResponse cachedResponse = getCachedSimilarResponse(prompt, context);
        if (cachedResponse != null) {
            log.info("Using cached fallback response");
            return enhanceWithFallbackMetadata(cachedResponse, "cached");
        }
        
        // Strategy 3: Template-based responses
        ChatResponse templateResponse = generateTemplateResponse(prompt, context);
        if (templateResponse != null) {
            log.info("Using template-based fallback response");
            return enhanceWithFallbackMetadata(templateResponse, "template");
        }
        
        // Strategy 4: Last resort - minimal service
        return createMinimalServiceResponse(context);
    }
    
    private ChatResponse getCachedSimilarResponse(String prompt, RequestContext context) {
        Cache cache = cacheManager.getCache("ai-responses");
        if (cache == null) return null;
        
        // Use semantic similarity to find cached responses
        List<String> cachedKeys = getCachedKeys(cache);
        
        return cachedKeys.stream()
            .map(key -> new AbstractMap.SimpleEntry<>(key, cache.get(key, ChatResponse.class)))
            .filter(entry -> entry.getValue() != null)
            .filter(entry -> calculateSimilarity(prompt, entry.getKey()) > 0.8)
            .max(Comparator.comparing(entry -> calculateSimilarity(prompt, entry.getKey())))
            .map(AbstractMap.SimpleEntry::getValue)
            .orElse(null);
    }
    
    private ChatResponse generateTemplateResponse(String prompt, RequestContext context) {
        TemplateEngine templateEngine = new TemplateEngine();
        
        // Classify the request type
        RequestType requestType = classifyRequest(prompt);
        
        switch (requestType) {
            case GREETING:
                return templateEngine.generateGreeting(context);
            case FAQ:
                return templateEngine.generateFAQResponse(extractFAQTopic(prompt));
            case ERROR_HELP:
                return templateEngine.generateErrorHelp(context);
            default:
                return null;
        }
    }
    
    @Component
    public static class OfflineCapabilityService {
        
        private final Map<String, PrecomputedResponse> offlineResponses;
        
        @PostConstruct
        public void initializeOfflineResponses() {
            // Load pre-computed responses for common queries
            loadCommonResponses();
            loadFAQResponses();
            loadErrorResponses();
        }
        
        public ChatResponse getOfflineResponse(String prompt) {
            String normalizedPrompt = normalizePrompt(prompt);
            PrecomputedResponse precomputed = offlineResponses.get(normalizedPrompt);
            
            if (precomputed != null) {
                return ChatResponse.builder()
                    .result(new Generation(precomputed.getContent()))
                    .resultMetadata(ChatResponseMetadata.builder()
                        .finishReason("offline")
                        .build())
                    .build();
            }
            
            return null;
        }
    }
}

## Real-World Use Cases and Examples

### Use Case 1: Enterprise Chatbot Security

```java
@RestController
@RequestMapping("/api/chat")
@Slf4j
public class SecureChatController {
    
    private final InputSanitizationService sanitizationService;
    private final RateLimitingService rateLimitingService;
    private final AuditService auditService;
    private final GracefulDegradationService degradationService;
    
    @PostMapping("/secure")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ChatResponse> secureChat(
            @RequestBody @Valid ChatRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        try {
            // Step 1: Rate limiting check
            String userId = authentication.getName();
            if (!rateLimitingService.isAllowed(userId)) {
                auditService.logRateLimitExceeded(userId, httpRequest.getRemoteAddr());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(createRateLimitResponse());
            }
            
            // Step 2: Input sanitization and validation
            SanitizedInput sanitizedInput = sanitizationService
                .sanitizeAndValidate(request.getMessage());
            
            if (!sanitizedInput.isValid()) {
                auditService.logSecurityViolation(userId, sanitizedInput.getViolations());
                return ResponseEntity.badRequest()
                    .body(createSecurityViolationResponse(sanitizedInput));
            }
            
            // Step 3: Build secure context
            RequestContext context = RequestContext.builder()
                .userId(userId)
                .sessionId(request.getSessionId())
                .ipAddress(httpRequest.getRemoteAddr())
                .userAgent(httpRequest.getHeader("User-Agent"))
                .timestamp(Instant.now())
                .build();
            
            // Step 4: Generate response with fallbacks
            ChatResponse response = degradationService
                .generateResponseWithFallback(sanitizedInput.getSanitizedInput(), context);
            
            // Step 5: Audit successful interaction
            auditService.logSuccessfulInteraction(userId, request.getMessage(), 
                response.getResult().getOutput().getContent(), context);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            auditService.logSystemError(authentication.getName(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse());
        }
    }
}
```

### Use Case 2: Multi-Tenant SaaS Security

```java
@Service
public class MultiTenantSecurityService {
    
    private final TenantConfigurationService tenantConfigService;
    private final TenantResourceManager resourceManager;
    
    public ChatResponse processRequestForTenant(String tenantId, String prompt, 
                                               TenantContext context) {
        
        // Get tenant-specific configuration
        TenantConfiguration config = tenantConfigService.getConfiguration(tenantId);
        
        // Apply tenant-specific security policies
        SecurityPolicy policy = config.getSecurityPolicy();
        if (!policy.isPromptAllowed(prompt, context)) {
            throw new SecurityException("Prompt violates tenant security policy");
        }
        
        // Check tenant resource limits
        if (!resourceManager.hasAvailableQuota(tenantId, calculateTokens(prompt))) {
            throw new QuotaExceededException("Tenant has exceeded usage quota");
        }
        
        // Use tenant-specific model configuration
        ChatModel model = getTenantModel(tenantId, config);
        
        try {
            // Track usage for billing
            resourceManager.trackUsage(tenantId, prompt.length());
            
            ChatResponse response = model.call(new Prompt(prompt));
            
            // Apply tenant-specific response filtering
            return applyTenantResponseFiltering(response, config);
            
        } finally {
            // Clean up tenant-specific resources
            cleanupTenantResources(tenantId, context);
        }
    }
    
    private ChatModel getTenantModel(String tenantId, TenantConfiguration config) {
        // Different tenants might use different models based on their plan
        switch (config.getServiceTier()) {
            case PREMIUM:
                return createPremiumModel(config);
            case STANDARD:
                return createStandardModel(config);
            case BASIC:
                return createBasicModel(config);
            default:
                throw new IllegalStateException("Unknown service tier");
        }
    }
}
```

### Use Case 3: Healthcare AI with HIPAA Compliance

```java
@Service
@Slf4j
public class HIPAACompliantAIService {
    
    private final PHIDetectionService phiDetectionService;
    private final EncryptionService encryptionService;
    private final AuditTrailService auditTrailService;
    private final ConsentManagementService consentService;
    
    @PreAuthorize("hasRole('HEALTHCARE_PROVIDER')")
    public ChatResponse processHealthcareQuery(String query, PatientContext patientContext, 
                                             ProviderContext providerContext) {
        
        // Step 1: Verify consent
        if (!consentService.hasValidConsent(patientContext.getPatientId(), 
                                          ConsentType.AI_PROCESSING)) {
            throw new ConsentRequiredException("Patient consent required for AI processing");
        }
        
        // Step 2: Detect and handle PHI
        PHIAnalysisResult phiResult = phiDetectionService.analyzePHI(query);
        
        if (phiResult.containsPHI()) {
            // Log PHI access
            auditTrailService.logPHIAccess(
                providerContext.getProviderId(),
                patientContext.getPatientId(),
                phiResult.getDetectedPHI(),
                "AI_QUERY_PROCESSING"
            );
            
            // Encrypt PHI for secure processing
            String encryptedQuery = encryptionService.encryptPHI(query, 
                patientContext.getEncryptionKey());
            
            // Use HIPAA-compliant processing
            return processWithHIPAACompliance(encryptedQuery, patientContext, providerContext);
        }
        
        return processNonPHIQuery(query, providerContext);
    }
    
    private ChatResponse processWithHIPAACompliance(String encryptedQuery, 
                                                   PatientContext patientContext,
                                                   ProviderContext providerContext) {
        
        // Use specialized healthcare model with privacy constraints
        HealthcareAIModel model = createHIPAACompliantModel();
        
        try {
            // Decrypt for processing (in secure memory)
            String decryptedQuery = encryptionService.decrypt(encryptedQuery, 
                patientContext.getEncryptionKey());
            
            // Add healthcare-specific prompt engineering
            String enhancedPrompt = addHealthcareContext(decryptedQuery, patientContext);
            
            ChatResponse response = model.call(new Prompt(enhancedPrompt));
            
            // Remove any inadvertent PHI from response
            ChatResponse sanitizedResponse = removePHIFromResponse(response, patientContext);
            
            // Audit trail
            auditTrailService.logHealthcareAIInteraction(
                providerContext.getProviderId(),
                patientContext.getPatientId(),
                decryptedQuery,
                sanitizedResponse.getResult().getOutput().getContent(),
                Instant.now()
            );
            
            return sanitizedResponse;
            
        } finally {
            // Ensure secure cleanup
            secureMemoryCleanup();
        }
    }
    
    @Component
    public static class PHIDetectionService {
        
        private final List<PHIPattern> phiPatterns;
        
        public PHIDetectionService() {
            this.phiPatterns = initializeHIPAAPHIPatterns();
        }
        
        public PHIAnalysisResult analyzePHI(String text) {
            List<PHIMatch> detectedPHI = new ArrayList<>();
            
            for (PHIPattern pattern : phiPatterns) {
                List<PHIMatch> matches = pattern.findMatches(text);
                detectedPHI.addAll(matches);
            }
            
            return PHIAnalysisResult.builder()
                .originalText(text)
                .detectedPHI(detectedPHI)
                .containsPHI(!detectedPHI.isEmpty())
                .riskLevel(calculateRiskLevel(detectedPHI))
                .build();
        }
        
        private List<PHIPattern> initializeHIPAAPHIPatterns() {
            return Arrays.asList(
                new SSNPattern(),
                new MedicalRecordNumberPattern(),
                new InsuranceNumberPattern(),
                new DateOfBirthPattern(),
                new PhoneNumberPattern(),
                new EmailPattern(),
                new AddressPattern(),
                new BiometricPattern()
            );
        }
    }
}
```

### Use Case 4: Financial Services with SOX Compliance

```java
@Service
public class SOXCompliantFinancialAIService {
    
    private final FinancialDataClassificationService dataClassifier;
    private final RegulatoryComplianceService complianceService;
    private final FinancialAuditService auditService;
    
    @PreAuthorize("hasAnyRole('FINANCIAL_ANALYST', 'COMPLIANCE_OFFICER')")
    public ChatResponse processFinancialQuery(String query, FinancialContext context) {
        
        // Step 1: Classify data sensitivity
        DataClassification classification = dataClassifier.classify(query);
        
        if (classification.isRegulated()) {
            return processRegulatedFinancialData(query, context, classification);
        }
        
        return processStandardFinancialQuery(query, context);
    }
    
    private ChatResponse processRegulatedFinancialData(String query, 
                                                      FinancialContext context,
                                                      DataClassification classification) {
        
        // Ensure user has proper authorization for this data classification
        if (!hasRequiredAuthorization(context.getUserId(), classification)) {
            auditService.logUnauthorizedAccess(context.getUserId(), classification);
            throw new UnauthorizedAccessException("Insufficient privileges for regulated data");
        }
        
        // Use SOX-compliant processing
        try {
            // Create immutable audit trail entry
            AuditTrailEntry auditEntry = AuditTrailEntry.builder()
                .userId(context.getUserId())
                .timestamp(Instant.now())
                .action("FINANCIAL_AI_QUERY")
                .dataClassification(classification)
                .query(hashSensitiveData(query))
                .ipAddress(context.getIpAddress())
                .build();
            
            auditService.createImmutableAuditEntry(auditEntry);
            
            // Use specialized financial model with compliance constraints
            FinancialAIModel model = createSOXCompliantModel(classification);
            
            // Add compliance-specific prompt engineering
            String complianceEnhancedPrompt = addComplianceContext(query, context);
            
            ChatResponse response = model.call(new Prompt(complianceEnhancedPrompt));
            
            // Validate response against regulatory requirements
            ComplianceValidationResult validationResult = 
                complianceService.validateResponse(response, classification);
            
            if (!validationResult.isCompliant()) {
                auditService.logComplianceViolation(context.getUserId(), 
                    validationResult.getViolations());
                return createComplianceErrorResponse(validationResult);
            }
            
            // Log successful compliant interaction
            auditService.logCompliantInteraction(auditEntry, response);
            
            return response;
            
        } catch (Exception e) {
            auditService.logSystemError(context.getUserId(), e, classification);
            throw new FinancialProcessingException("Error processing regulated financial data", e);
        }
    }
}
```

## Production Monitoring and Alerting

```java
@Configuration
@EnableScheduling
public class ProductionMonitoringConfig {
    
    @Bean
    public MeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
    
    @Component
    @Slf4j
    public static class AIServiceMonitor {
        
        private final MeterRegistry meterRegistry;
        private final Counter requestCounter;
        private final Timer responseTimer;
        private final Gauge activeConnections;
        
        public AIServiceMonitor(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            this.requestCounter = Counter.builder("ai_requests_total")
                .description("Total AI requests")
                .tag("model", "unknown")
                .register(meterRegistry);
                
            this.responseTimer = Timer.builder("ai_response_duration")
                .description("AI response duration")
                .register(meterRegistry);
                
            this.activeConnections = Gauge.builder("ai_active_connections")
                .description("Active AI connections")
                .register(meterRegistry, this, AIServiceMonitor::getActiveConnections);
        }
        
        @EventListener
        public void handleAIRequest(AIRequestEvent event) {
            requestCounter.increment(
                Tags.of(
                    "model", event.getModelName(),
                    "status", event.getStatus().toString(),
                    "user_tier", event.getUserTier()
                )
            );
            
            if (event.getStatus() == RequestStatus.ERROR) {
                sendAlert("AI request failed", event);
            }
        }
        
        @EventListener
        public void handleHighLatency(HighLatencyEvent event) {
            if (event.getDuration().compareTo(Duration.ofSeconds(10)) > 0) {
                sendCriticalAlert("High latency detected", event);
            }
        }
        
        @Scheduled(fixedRate = 30000) // Every 30 seconds
        public void checkSystemHealth() {
            double errorRate = calculateErrorRate();
            if (errorRate > 0.05) { // 5% error rate threshold
                sendAlert("High error rate detected: " + errorRate, null);
            }
            
            long activeConnections = getActiveConnections();
            if (activeConnections > 1000) {
                sendAlert("High connection count: " + activeConnections, null);
            }
        }
        
        private void sendCriticalAlert(String message, Object event) {
            // Integrate with your alerting system (PagerDuty, Slack, etc.)
            log.error("CRITICAL ALERT: {}", message);
            // Implementation for your alerting system
        }
    }
}
```

This comprehensive guide covers Phase 10 with detailed security implementations, production deployment strategies, and real-world use cases. Each example includes proper error handling, monitoring, and compliance considerations that are essential for production environments.

Key takeaways from Phase 10:

1. **Security is layered** - Input sanitization, API key management, and PII protection work together
2. **Production deployment requires multiple strategies** - Containerization, cloud deployment, and auto-scaling
3. **Resilience is critical** - Circuit breakers, retries, and graceful degradation prevent system failures
4. **Compliance varies by industry** - HIPAA, SOX, and other regulations require specific implementations
5. **Monitoring is essential** - Comprehensive metrics and alerting prevent issues from becoming outages

Would you like me to continue with **Phase 11: Advanced Patterns and Customization**?