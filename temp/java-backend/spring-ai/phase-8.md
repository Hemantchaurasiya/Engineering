# Phase 8: Streaming and Reactive Programming with Spring AI - Complete Guide

## 8.1 Streaming Responses

### Understanding Streaming in AI Context

Streaming responses allow AI models to return partial results as they're generated, providing a better user experience by showing immediate feedback instead of waiting for the complete response.

#### Key Benefits:
- **Improved UX**: Users see responses being generated in real-time
- **Lower Perceived Latency**: Faster time to first token
- **Better Resource Utilization**: Prevents timeouts for long responses
- **Interactive Experience**: Similar to ChatGPT's typing effect

### Server-Sent Events (SSE) Implementation

#### 1. Basic SSE Chat Controller

```java
@RestController
@RequestMapping("/api/chat")
@CrossOrigin
public class StreamingChatController {
    
    private final ChatModel chatModel;
    private final ConversationService conversationService;
    
    public StreamingChatController(ChatModel chatModel, 
                                 ConversationService conversationService) {
        this.chatModel = chatModel;
        this.conversationService = conversationService;
    }
    
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String message,
                               @RequestParam(required = false) String conversationId) {
        
        SseEmitter emitter = new SseEmitter(30000L); // 30 second timeout
        
        CompletableFuture.runAsync(() -> {
            try {
                // Get conversation context
                List<Message> history = conversationService.getHistory(conversationId);
                
                // Prepare messages with context
                List<Message> messages = new ArrayList<>(history);
                messages.add(new UserMessage(message));
                
                // Create prompt with streaming enabled
                Prompt prompt = new Prompt(messages, 
                    OpenAiChatOptions.builder()
                        .withStream(true)
                        .withModel("gpt-4")
                        .withTemperature(0.7f)
                        .build());
                
                // Stream the response
                Flux<ChatResponse> responseFlux = chatModel.stream(prompt);
                
                StringBuilder fullResponse = new StringBuilder();
                
                responseFlux
                    .doOnNext(response -> {
                        try {
                            String content = response.getResult().getOutput().getContent();
                            if (content != null) {
                                fullResponse.append(content);
                                
                                // Send chunk to client
                                ChatChunk chunk = new ChatChunk(
                                    content, 
                                    false, 
                                    response.getMetadata()
                                );
                                emitter.send(SseEmitter.event()
                                    .name("code-suggestion")
                                    .data(new CodeSuggestion(content, analysis.getLanguage())));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("analysis-complete")
                                .data("Analysis completed"));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();
                    
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        
        return ResponseEntity.ok(emitter);
    }
    
    private String buildCodeAnalysisPrompt(String code, CodeAnalysis analysis) {
        return String.format(
            "Analyze this %s code and provide suggestions for improvement:\n\n%s\n\n" +
            "Code metrics:\n- Lines: %d\n- Complexity: %d\n- Issues found: %d\n\n" +
            "Focus on: performance, readability, best practices, and potential bugs. " +
            "Provide specific, actionable suggestions.",
            analysis.getLanguage(),
            code,
            analysis.getLineCount(),
            analysis.getComplexity(),
            analysis.getIssueCount()
        );
    }
}
```

### 3. Collaborative Document Editor with AI

```java
@Component
public class CollaborativeAIEditor {
    
    private final ChatModel chatModel;
    private final SimpMessagingTemplate messagingTemplate;
    private final DocumentService documentService;
    
    public CollaborativeAIEditor(ChatModel chatModel, 
                               SimpMessagingTemplate messagingTemplate,
                               DocumentService documentService) {
        this.chatModel = chatModel;
        this.messagingTemplate = messagingTemplate;
        this.documentService = documentService;
    }
    
    @EventListener
    public void handleDocumentEditEvent(DocumentEditEvent event) {
        // Stream AI suggestions based on document changes
        CompletableFuture.runAsync(() -> {
            streamDocumentSuggestions(event.getDocumentId(), 
                                    event.getChanges(), 
                                    event.getUserId());
        });
    }
    
    private void streamDocumentSuggestions(String documentId, 
                                         List<DocumentChange> changes, 
                                         String userId) {
        try {
            Document document = documentService.getDocument(documentId);
            String context = buildDocumentContext(document, changes);
            
            String prompt = String.format(
                "Based on the recent changes to this document, provide suggestions for:\n" +
                "1. Content improvement\n2. Grammar and style\n3. Structure optimization\n\n%s",
                context
            );
            
            chatModel.stream(new Prompt(prompt))
                .buffer(3) // Group responses
                .doOnNext(responses -> {
                    String suggestion = responses.stream()
                        .map(r -> r.getResult().getOutput().getContent())
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining());
                    
                    if (!suggestion.isEmpty()) {
                        DocumentSuggestion docSuggestion = new DocumentSuggestion(
                            suggestion,
                            SuggestionType.AI_GENERATED,
                            userId,
                            Instant.now()
                        );
                        
                        // Broadcast to all document collaborators
                        messagingTemplate.convertAndSend(
                            "/topic/document/" + documentId + "/suggestions",
                            docSuggestion
                        );
                    }
                })
                .doOnComplete(() -> {
                    // Notify completion
                    messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/ai-status",
                        new AIStatus("suggestions_complete", userId)
                    );
                })
                .subscribe();
                
        } catch (Exception e) {
            messagingTemplate.convertAndSend(
                "/topic/document/" + documentId + "/ai-status",
                new AIStatus("suggestions_error", userId, e.getMessage())
            );
        }
    }
    
    @MessageMapping("/document/{documentId}/ai-request")
    public void handleAIRequest(@DestinationVariable String documentId,
                               AIDocumentRequest request) {
        
        CompletableFuture.runAsync(() -> {
            try {
                Document document = documentService.getDocument(documentId);
                String prompt = buildAIPrompt(document, request);
                
                messagingTemplate.convertAndSend(
                    "/topic/document/" + documentId + "/ai-status",
                    new AIStatus("processing", request.getUserId())
                );
                
                StringBuilder responseBuilder = new StringBuilder();
                
                chatModel.stream(new Prompt(prompt))
                    .doOnNext(response -> {
                        String content = response.getResult().getOutput().getContent();
                        if (content != null) {
                            responseBuilder.append(content);
                            
                            // Stream partial responses
                            messagingTemplate.convertAndSend(
                                "/topic/document/" + documentId + "/ai-response",
                                new AIPartialResponse(
                                    request.getRequestId(),
                                    content,
                                    false
                                )
                            );
                        }
                    })
                    .doOnComplete(() -> {
                        // Send final response
                        messagingTemplate.convertAndSend(
                            "/topic/document/" + documentId + "/ai-response",
                            new AIPartialResponse(
                                request.getRequestId(),
                                responseBuilder.toString(),
                                true
                            )
                        );
                        
                        messagingTemplate.convertAndSend(
                            "/topic/document/" + documentId + "/ai-status",
                            new AIStatus("completed", request.getUserId())
                        );
                    })
                    .doOnError(error -> {
                        messagingTemplate.convertAndSend(
                            "/topic/document/" + documentId + "/ai-status",
                            new AIStatus("error", request.getUserId(), error.getMessage())
                        );
                    })
                    .subscribe();
                    
            } catch (Exception e) {
                messagingTemplate.convertAndSend(
                    "/topic/document/" + documentId + "/ai-status",
                    new AIStatus("error", request.getUserId(), e.getMessage())
                );
            }
        });
    }
}
```

## Advanced Streaming Patterns

### 1. Multi-Stream Aggregation

```java
@Service
public class MultiStreamAggregationService {
    
    private final ChatModel chatModel;
    private final List<ChatModel> modelPool;
    
    public MultiStreamAggregationService(ChatModel chatModel, 
                                       List<ChatModel> modelPool) {
        this.chatModel = chatModel;
        this.modelPool = modelPool;
    }
    
    public Flux<AggregatedResponse> processWithMultipleModels(String query) {
        List<Flux<ChatResponse>> modelStreams = modelPool.stream()
            .map(model -> model.stream(new Prompt(query)))
            .collect(Collectors.toList());
        
        return Flux.combineLatest(modelStreams, responses -> {
            List<String> contents = Arrays.stream(responses)
                .map(ChatResponse.class::cast)
                .map(r -> r.getResult().getOutput().getContent())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
            return new AggregatedResponse(contents, Instant.now());
        })
        .sample(Duration.ofMillis(500)) // Emit every 500ms
        .distinctUntilChanged(); // Only emit if content changed
    }
    
    public Flux<ConsensusResponse> generateConsensusResponse(String query) {
        // Get responses from multiple models
        List<Mono<String>> responseMethods = modelPool.stream()
            .map(model -> 
                model.stream(new Prompt(query))
                    .collectList()
                    .map(responses -> 
                        responses.stream()
                            .map(r -> r.getResult().getOutput().getContent())
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining())
                    )
            )
            .collect(Collectors.toList());
        
        return Mono.zip(responseMethods, responses -> {
            List<String> allResponses = Arrays.stream(responses)
                .map(String.class::cast)
                .collect(Collectors.toList());
            
            // Use a master model to create consensus
            String consensusPrompt = String.format(
                "Based on these AI responses to the query '%s', create a consensus answer:\n\n%s",
                query,
                String.join("\n---\n", allResponses)
            );
            
            return consensusPrompt;
        })
        .flatMapMany(consensusPrompt -> 
            chatModel.stream(new Prompt(consensusPrompt)))
        .map(response -> new ConsensusResponse(
            response.getResult().getOutput().getContent(),
            modelPool.size(),
            Instant.now()
        ));
    }
}
```

### 2. Adaptive Streaming with Quality Control

```java
@Service
public class AdaptiveStreamingService {
    
    private final ChatModel chatModel;
    private final QualityAssessmentService qualityService;
    private final MetricsService metricsService;
    
    public AdaptiveStreamingService(ChatModel chatModel,
                                  QualityAssessmentService qualityService,
                                  MetricsService metricsService) {
        this.chatModel = chatModel;
        this.qualityService = qualityService;
        this.metricsService = metricsService;
    }
    
    public Flux<QualityControlledResponse> streamWithQualityControl(String query) {
        return chatModel.stream(new Prompt(query))
            .bufferTimeout(5, Duration.ofSeconds(1)) // Buffer for quality assessment
            .flatMap(this::assessAndAdaptQuality)
            .onBackpressureDrop(response -> 
                metricsService.incrementCounter("streaming.quality.dropped"));
    }
    
    private Flux<QualityControlledResponse> assessAndAdaptQuality(
            List<ChatResponse> responses) {
        
        String content = responses.stream()
            .map(r -> r.getResult().getOutput().getContent())
            .filter(Objects::nonNull)
            .collect(Collectors.joining());
        
        QualityScore score = qualityService.assessQuality(content);
        
        if (score.getScore() < 0.6) {
            // Low quality - trigger regeneration
            String improvedPrompt = qualityService.improvePrompt(content);
            
            return chatModel.stream(new Prompt(improvedPrompt))
                .map(response -> new QualityControlledResponse(
                    response.getResult().getOutput().getContent(),
                    score,
                    true // regenerated
                ));
        } else {
            return Flux.just(new QualityControlledResponse(
                content,
                score,
                false
            ));
        }
    }
    
    public Flux<AdaptiveResponse> streamWithAdaptiveBatching(
            Flux<String> queries,
            StreamingConfiguration config) {
        
        return queries
            .bufferTimeout(
                config.getBatchSize(),
                Duration.ofMillis(config.getBatchTimeoutMs())
            )
            .flatMap(batch -> processBatchAdaptively(batch, config))
            .onErrorResume(this::handleAdaptiveError);
    }
    
    private Flux<AdaptiveResponse> processBatchAdaptively(
            List<String> queries,
            StreamingConfiguration config) {
        
        if (queries.size() == 1) {
            // Single query - use streaming
            return chatModel.stream(new Prompt(queries.get(0)))
                .map(response -> new AdaptiveResponse(
                    response.getResult().getOutput().getContent(),
                    ProcessingMode.STREAMING
                ));
        } else {
            // Multiple queries - use batch processing
            return Flux.fromIterable(queries)
                .flatMap(query -> 
                    chatModel.call(new Prompt(query)))
                .map(response -> new AdaptiveResponse(
                    response.getResult().getOutput().getContent(),
                    ProcessingMode.BATCH
                ));
        }
    }
    
    private Flux<AdaptiveResponse> handleAdaptiveError(Throwable error) {
        metricsService.incrementCounter("streaming.adaptive.errors");
        
        return Flux.just(new AdaptiveResponse(
            "Processing temporarily unavailable. Please try again.",
            ProcessingMode.FALLBACK
        ));
    }
}
```

## Performance Optimization and Best Practices

### 1. Stream Optimization Service

```java
@Service
public class StreamOptimizationService {
    
    private final ChatModel chatModel;
    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    
    public StreamOptimizationService(ChatModel chatModel,
                                   CacheManager cacheManager,
                                   MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.cacheManager = cacheManager;
        this.meterRegistry = meterRegistry;
    }
    
    public Flux<OptimizedResponse> streamWithCaching(String query) {
        String cacheKey = generateCacheKey(query);
        
        // Check cache first
        return Mono.fromSupplier(() -> getCachedResponse(cacheKey))
            .flatMapMany(cachedResponse -> {
                if (cachedResponse != null) {
                    meterRegistry.counter("streaming.cache.hits").increment();
                    return Flux.just(cachedResponse);
                } else {
                    meterRegistry.counter("streaming.cache.misses").increment();
                    return streamAndCache(query, cacheKey);
                }
            });
    }
    
    private Flux<OptimizedResponse> streamAndCache(String query, String cacheKey) {
        StringBuilder fullResponse = new StringBuilder();
        
        return chatModel.stream(new Prompt(query))
            .doOnNext(response -> {
                String content = response.getResult().getOutput().getContent();
                if (content != null) {
                    fullResponse.append(content);
                }
            })
            .map(response -> new OptimizedResponse(
                response.getResult().getOutput().getContent(),
                ResponseSource.STREAMING
            ))
            .doOnComplete(() -> {
                // Cache the complete response
                OptimizedResponse completeResponse = new OptimizedResponse(
                    fullResponse.toString(),
                    ResponseSource.STREAMING
                );
                cacheResponse(cacheKey, completeResponse);
            });
    }
    
    private OptimizedResponse getCachedResponse(String cacheKey) {
        Cache cache = cacheManager.getCache("ai-responses");
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null) {
                OptimizedResponse cached = (OptimizedResponse) wrapper.get();
                return new OptimizedResponse(
                    cached.getContent(),
                    ResponseSource.CACHED
                );
            }
        }
        return null;
    }
    
    private void cacheResponse(String cacheKey, OptimizedResponse response) {
        Cache cache = cacheManager.getCache("ai-responses");
        if (cache != null) {
            cache.put(cacheKey, response);
        }
    }
    
    public Flux<OptimizedResponse> streamWithConnectionPooling(List<String> queries) {
        // Use connection pooling for multiple concurrent requests
        return Flux.fromIterable(queries)
            .flatMap(query -> 
                chatModel.stream(new Prompt(query))
                    .collectList()
                    .map(responses -> {
                        String content = responses.stream()
                            .map(r -> r.getResult().getOutput().getContent())
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining());
                        
                        return new OptimizedResponse(content, ResponseSource.POOLED);
                    }),
                5 // Concurrency level
            )
            .doOnNext(response -> 
                meterRegistry.timer("streaming.pooled.duration").record(() -> {}))
            .onBackpressureBuffer(100);
    }
    
    private String generateCacheKey(String query) {
        return DigestUtils.md5Hex(query.toLowerCase().trim());
    }
}
```

### 2. Monitoring and Metrics

```java
@Component
public class StreamingMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Timer streamingTimer;
    private final Counter errorCounter;
    private final Gauge activeStreams;
    
    private final AtomicLong activeStreamCount = new AtomicLong(0);
    
    public StreamingMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.streamingTimer = Timer.builder("ai.streaming.duration")
            .description("Time taken for streaming responses")
            .register(meterRegistry);
        
        this.errorCounter = Counter.builder("ai.streaming.errors")
            .description("Number of streaming errors")
            .register(meterRegistry);
        
        this.activeStreams = Gauge.builder("ai.streaming.active")
            .description("Number of active streams")
            .register(meterRegistry, this, StreamingMetricsCollector::getActiveStreamCount);
    }
    
    public <T> Flux<T> wrapWithMetrics(Flux<T> stream, String operationType) {
        return stream
            .doOnSubscribe(subscription -> {
                activeStreamCount.incrementAndGet();
                meterRegistry.counter("ai.streaming.started", "type", operationType)
                    .increment();
            })
            .doOnNext(item -> {
                meterRegistry.counter("ai.streaming.chunks", "type", operationType)
                    .increment();
            })
            .doOnComplete(() -> {
                activeStreamCount.decrementAndGet();
                meterRegistry.counter("ai.streaming.completed", "type", operationType)
                    .increment();
            })
            .doOnError(error -> {
                activeStreamCount.decrementAndGet();
                errorCounter.increment(Tags.of(
                    "type", operationType,
                    "error", error.getClass().getSimpleName()
                ));
            })
            .doOnCancel(() -> {
                activeStreamCount.decrementAndGet();
                meterRegistry.counter("ai.streaming.cancelled", "type", operationType)
                    .increment();
            });
    }
    
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordDuration(Timer.Sample sample, String operationType) {
        sample.stop(Timer.builder("ai.streaming.operation.duration")
            .tag("type", operationType)
            .register(meterRegistry));
    }
    
    public double getActiveStreamCount() {
        return activeStreamCount.get();
    }
    
    @EventListener
    public void handleStreamingEvent(StreamingEvent event) {
        meterRegistry.counter("ai.streaming.events", 
            "event", event.getType(),
            "source", event.getSource())
            .increment();
    }
}
```

## Testing Streaming Applications

### 1. Integration Tests for Streaming

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
class StreamingIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private WebTestClient webTestClient;
    
    @MockBean
    private ChatModel chatModel;
    
    @Test
    void testSSEStreaming() {
        // Mock streaming response
        Flux<ChatResponse> mockResponse = Flux.fromIterable(
            Arrays.asList("Hello", " ", "World", "!")
        ).map(content -> createMockChatResponse(content));
        
        when(chatModel.stream(any(Prompt.class))).thenReturn(mockResponse);
        
        webTestClient.get()
            .uri("/api/chat/stream?message=Hello")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM)
            .returnResult(String.class)
            .getResponseBody()
            .take(4) // Expect 4 chunks
            .collectList()
            .block()
            .forEach(chunk -> {
                assertThat(chunk).contains("data:");
            });
    }
    
    @Test
    void testWebSocketStreaming() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(
            new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())))
        );
        
        StompSessionHandler sessionHandler = new TestStompSessionHandler();
        
        StompSession stompSession = stompClient.connect(
            "ws://localhost:" + port + "/ws", sessionHandler
        ).get(10, TimeUnit.SECONDS);
        
        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        
        stompSession.subscribe("/topic/chat/responses", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }
            
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedMessages.add((String) payload);
                latch.countDown();
            }
        });
        
        stompSession.send("/app/chat", "Test message");
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertFalse(receivedMessages.isEmpty());
    }
    
    @Test
    void testStreamingWithBackpressure() {
        Flux<ChatResponse> slowResponse = Flux.interval(Duration.ofMillis(100))
            .take(10)
            .map(i -> createMockChatResponse("Chunk " + i));
        
        when(chatModel.stream(any(Prompt.class))).thenReturn(slowResponse);
        
        webTestClient.get()
            .uri("/api/chat/stream?message=Test")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .take(Duration.ofSeconds(2))
            .collectList()
            .block()
            .size(); // Should handle backpressure appropriately
    }
    
    private ChatResponse createMockChatResponse(String content) {
        Generation generation = new Generation(new AssistantMessage(content));
        ChatResponse.ChatResponseBuilder builder = ChatResponse.builder()
            .withGeneration(generation);
        return builder.build();
    }
}
```

This comprehensive guide covers Phase 8 of Spring AI mastery, focusing on streaming and reactive programming. The examples demonstrate real-world implementations including:

1. **Server-Sent Events (SSE)** with error recovery and monitoring
2. **WebFlux integration** with backpressure handling
3. **WebSocket support** for bidirectional communication
4. **Event-driven architecture** with message queues
5. **Real-world use cases** like customer support and code assistance
6. **Advanced patterns** including multi-stream aggregation and adaptive streaming
7. **Performance optimization** with caching and connection pooling
8. **Comprehensive testing strategies**

Each code example includes proper error handling, monitoring, and production-ready patterns. The implementation demonstrates how to build scalable, responsive AI applications using Spring AI's streaming capabilities.

Would you like me to proceed with Phase 9 (Observability and Monitoring) or would you prefer to dive deeper into any specific aspect of streaming and reactive programming?
                                    .name("chunk")
                                    .data(chunk));
                            }
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            // Save complete conversation
                            conversationService.saveMessage(conversationId, 
                                new UserMessage(message));
                            conversationService.saveMessage(conversationId, 
                                new AssistantMessage(fullResponse.toString()));
                            
                            // Send completion event
                            emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(new ChatComplete(fullResponse.toString())));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();
                    
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
}
```

#### 2. Enhanced SSE with Error Recovery

```java
@Component
public class RobustStreamingService {
    
    private final ChatModel chatModel;
    private final MeterRegistry meterRegistry;
    
    public RobustStreamingService(ChatModel chatModel, MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.meterRegistry = meterRegistry;
    }
    
    public SseEmitter createStreamWithErrorRecovery(String message, 
                                                   StreamingOptions options) {
        
        SseEmitter emitter = new SseEmitter(options.getTimeout());
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Handle client disconnect
        emitter.onCompletion(() -> {
            sample.stop(Timer.builder("ai.stream.duration")
                .tag("status", "completed")
                .register(meterRegistry));
        });
        
        emitter.onTimeout(() -> {
            sample.stop(Timer.builder("ai.stream.duration")
                .tag("status", "timeout")
                .register(meterRegistry));
            meterRegistry.counter("ai.stream.timeouts").increment();
        });
        
        emitter.onError(throwable -> {
            sample.stop(Timer.builder("ai.stream.duration")
                .tag("status", "error")
                .register(meterRegistry));
            meterRegistry.counter("ai.stream.errors").increment();
        });
        
        CompletableFuture.runAsync(() -> {
            streamWithRetry(message, emitter, options, 0);
        });
        
        return emitter;
    }
    
    private void streamWithRetry(String message, SseEmitter emitter, 
                               StreamingOptions options, int retryCount) {
        try {
            Prompt prompt = createPromptWithOptions(message, options);
            
            chatModel.stream(prompt)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                    .filter(this::isRetryableException))
                .timeout(Duration.ofSeconds(30))
                .doOnNext(response -> sendChunkSafely(response, emitter))
                .doOnComplete(() -> emitter.complete())
                .doOnError(error -> handleStreamError(error, emitter, message, options, retryCount))
                .subscribe();
                
        } catch (Exception e) {
            handleStreamError(e, emitter, message, options, retryCount);
        }
    }
    
    private void sendChunkSafely(ChatResponse response, SseEmitter emitter) {
        try {
            String content = response.getResult().getOutput().getContent();
            if (content != null && !content.isEmpty()) {
                emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(new StreamChunk(content, System.currentTimeMillis())));
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
    
    private void handleStreamError(Throwable error, SseEmitter emitter, 
                                 String message, StreamingOptions options, int retryCount) {
        if (retryCount < 3 && isRetryableException(error)) {
            // Retry with exponential backoff
            CompletableFuture.delayedExecutor(
                Duration.ofSeconds((long) Math.pow(2, retryCount)).toMillis(),
                TimeUnit.MILLISECONDS
            ).execute(() -> streamWithRetry(message, emitter, options, retryCount + 1));
        } else {
            emitter.completeWithError(error);
        }
    }
    
    private boolean isRetryableException(Throwable error) {
        return error instanceof HttpServerErrorException ||
               error instanceof ResourceAccessException ||
               error instanceof TimeoutException;
    }
}
```

#### 3. Client-Side JavaScript for SSE

```javascript
class StreamingChatClient {
    constructor(baseUrl) {
        this.baseUrl = baseUrl;
        this.eventSource = null;
    }
    
    startStreaming(message, conversationId, onChunk, onComplete, onError) {
        const url = `${this.baseUrl}/api/chat/stream?message=${encodeURIComponent(message)}&conversationId=${conversationId}`;
        
        this.eventSource = new EventSource(url);
        
        this.eventSource.addEventListener('chunk', (event) => {
            const chunk = JSON.parse(event.data);
            onChunk(chunk.content);
        });
        
        this.eventSource.addEventListener('complete', (event) => {
            const complete = JSON.parse(event.data);
            onComplete(complete.fullResponse);
            this.eventSource.close();
        });
        
        this.eventSource.addEventListener('error', (event) => {
            onError(event);
            this.eventSource.close();
        });
        
        // Auto-reconnect logic
        this.eventSource.onerror = (event) => {
            if (this.eventSource.readyState === EventSource.CLOSED) {
                setTimeout(() => {
                    if (this.shouldReconnect) {
                        this.startStreaming(message, conversationId, onChunk, onComplete, onError);
                    }
                }, 5000);
            }
        };
    }
    
    stop() {
        this.shouldReconnect = false;
        if (this.eventSource) {
            this.eventSource.close();
        }
    }
}
```

### WebFlux Integration

#### 1. Reactive Chat Controller

```java
@RestController
@RequestMapping("/api/reactive/chat")
public class ReactiveChatController {
    
    private final ChatModel chatModel;
    private final ConversationRepository conversationRepository;
    
    public ReactiveChatController(ChatModel chatModel, 
                                ConversationRepository conversationRepository) {
        this.chatModel = chatModel;
        this.conversationRepository = conversationRepository;
    }
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamResponse>> streamChat(
            @RequestBody ChatRequest request) {
        
        return Mono.fromSupplier(() -> createPrompt(request))
            .flatMapMany(prompt -> {
                Flux<ChatResponse> chatFlux = chatModel.stream(prompt);
                
                return chatFlux
                    .map(this::mapToChatStreamResponse)
                    .map(response -> ServerSentEvent.<ChatStreamResponse>builder()
                        .event("chat-chunk")
                        .data(response)
                        .build())
                    .concatWith(Mono.just(ServerSentEvent.<ChatStreamResponse>builder()
                        .event("chat-complete")
                        .data(ChatStreamResponse.complete())
                        .build()));
            })
            .doOnNext(sse -> saveConversationAsync(request))
            .onErrorResume(this::handleStreamingError);
    }
    
    @PostMapping("/batch-stream")
    public Flux<BatchStreamResponse> processBatchStreaming(
            @RequestBody Flux<ChatRequest> requests) {
        
        return requests
            .flatMap(request -> 
                Mono.fromSupplier(() -> createPrompt(request))
                    .flatMapMany(prompt -> chatModel.stream(prompt))
                    .collectList()
                    .map(responses -> new BatchStreamResponse(
                        request.getId(), 
                        combineResponses(responses)
                    ))
            )
            .buffer(Duration.ofSeconds(1)) // Batch responses every second
            .flatMapIterable(batch -> batch);
    }
    
    private ChatStreamResponse mapToChatStreamResponse(ChatResponse response) {
        return ChatStreamResponse.builder()
            .content(response.getResult().getOutput().getContent())
            .finishReason(response.getResult().getMetadata().getFinishReason())
            .usage(response.getMetadata().getUsage())
            .timestamp(Instant.now())
            .build();
    }
    
    private Flux<ServerSentEvent<ChatStreamResponse>> handleStreamingError(Throwable error) {
        ChatStreamResponse errorResponse = ChatStreamResponse.builder()
            .error(error.getMessage())
            .timestamp(Instant.now())
            .build();
            
        return Flux.just(ServerSentEvent.<ChatStreamResponse>builder()
            .event("chat-error")
            .data(errorResponse)
            .build());
    }
}
```

#### 2. Advanced Reactive Processing with Backpressure

```java
@Service
public class ReactiveAIProcessingService {
    
    private final ChatModel chatModel;
    private final ApplicationEventPublisher eventPublisher;
    
    public ReactiveAIProcessingService(ChatModel chatModel, 
                                     ApplicationEventPublisher eventPublisher) {
        this.chatModel = chatModel;
        this.eventPublisher = eventPublisher;
    }
    
    public Flux<ProcessingResult> processWithBackpressure(
            Flux<ProcessingRequest> requests) {
        
        return requests
            .onBackpressureBuffer(1000, 
                request -> eventPublisher.publishEvent(new BufferOverflowEvent(request)))
            .flatMap(this::processRequest, 5) // Concurrency of 5
            .onErrorContinue(this::handleProcessingError);
    }
    
    private Mono<ProcessingResult> processRequest(ProcessingRequest request) {
        return Mono.fromSupplier(() -> createPrompt(request.getMessage()))
            .flatMapMany(prompt -> chatModel.stream(prompt))
            .reduce(new StringBuilder(), (acc, response) -> {
                String content = response.getResult().getOutput().getContent();
                if (content != null) {
                    acc.append(content);
                }
                return acc;
            })
            .map(result -> new ProcessingResult(
                request.getId(), 
                result.toString(), 
                ProcessingStatus.COMPLETED
            ))
            .timeout(Duration.ofMinutes(2))
            .doOnSuccess(result -> eventPublisher.publishEvent(
                new ProcessingCompletedEvent(result)))
            .onErrorReturn(new ProcessingResult(
                request.getId(), 
                "Processing failed", 
                ProcessingStatus.FAILED
            ));
    }
    
    public Flux<AggregatedResult> processWithAggregation(
            Flux<ProcessingRequest> requests,
            Duration windowDuration) {
        
        return requests
            .window(windowDuration)
            .flatMap(window -> 
                window.collectList()
                    .flatMap(this::processRequestBatch)
            );
    }
    
    private Mono<AggregatedResult> processRequestBatch(List<ProcessingRequest> batch) {
        return Flux.fromIterable(batch)
            .flatMap(request -> processRequest(request))
            .collectList()
            .map(results -> new AggregatedResult(
                batch.size(),
                results.stream()
                    .mapToLong(r -> r.getProcessingTime())
                    .average()
                    .orElse(0.0),
                results
            ));
    }
    
    private void handleProcessingError(Throwable error, Object request) {
        eventPublisher.publishEvent(new ProcessingErrorEvent(error, request));
    }
}
```

## 8.2 Real-time Applications

### WebSocket Integration

#### 1. WebSocket Configuration

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final AIWebSocketHandler aiWebSocketHandler;
    
    public WebSocketConfig(AIWebSocketHandler aiWebSocketHandler) {
        this.aiWebSocketHandler = aiWebSocketHandler;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(aiWebSocketHandler, "/ws/chat")
            .setAllowedOrigins("*")
            .addInterceptors(new HttpSessionHandshakeInterceptor());
    }
}
```

#### 2. AI WebSocket Handler

```java
@Component
public class AIWebSocketHandler extends TextWebSocketHandler {
    
    private final ChatModel chatModel;
    private final SessionManager sessionManager;
    private final ConversationService conversationService;
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    public AIWebSocketHandler(ChatModel chatModel, 
                            SessionManager sessionManager,
                            ConversationService conversationService) {
        this.chatModel = chatModel;
        this.sessionManager = sessionManager;
        this.conversationService = conversationService;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionManager.createSession(sessionId);
        
        // Send welcome message
        WebSocketMessage welcomeMessage = new WebSocketMessage(
            "system",
            "Connected to AI Assistant. How can I help you?",
            Instant.now()
        );
        
        sendMessage(session, welcomeMessage);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) 
            throws Exception {
        
        try {
            ChatMessage chatMessage = objectMapper.readValue(
                message.getPayload(), ChatMessage.class);
            
            String conversationId = sessionManager.getConversationId(session.getId());
            
            // Process message asynchronously
            CompletableFuture.runAsync(() -> 
                processMessageAndStream(session, chatMessage, conversationId));
                
        } catch (Exception e) {
            handleError(session, e);
        }
    }
    
    private void processMessageAndStream(WebSocketSession session, 
                                       ChatMessage message, 
                                       String conversationId) {
        try {
            // Get conversation history
            List<Message> history = conversationService.getHistory(conversationId);
            history.add(new UserMessage(message.getContent()));
            
            Prompt prompt = new Prompt(history, 
                OpenAiChatOptions.builder()
                    .withStream(true)
                    .withTemperature(0.7f)
                    .build());
            
            StringBuilder fullResponse = new StringBuilder();
            
            chatModel.stream(prompt)
                .doOnNext(response -> {
                    String content = response.getResult().getOutput().getContent();
                    if (content != null) {
                        fullResponse.append(content);
                        
                        WebSocketMessage wsMessage = new WebSocketMessage(
                            "assistant-chunk",
                            content,
                            Instant.now()
                        );
                        
                        sendMessage(session, wsMessage);
                    }
                })
                .doOnComplete(() -> {
                    // Save conversation
                    conversationService.saveMessage(conversationId, 
                        new UserMessage(message.getContent()));
                    conversationService.saveMessage(conversationId, 
                        new AssistantMessage(fullResponse.toString()));
                    
                    WebSocketMessage completeMessage = new WebSocketMessage(
                        "assistant-complete",
                        fullResponse.toString(),
                        Instant.now()
                    );
                    
                    sendMessage(session, completeMessage);
                })
                .doOnError(error -> handleError(session, error))
                .subscribe();
                
        } catch (Exception e) {
            handleError(session, e);
        }
    }
    
    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            logger.error("Error sending WebSocket message", e);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) 
            throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        sessionManager.closeSession(sessionId);
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) 
            throws Exception {
        handleError(session, exception);
    }
    
    private void handleError(WebSocketSession session, Throwable error) {
        WebSocketMessage errorMessage = new WebSocketMessage(
            "error",
            "An error occurred: " + error.getMessage(),
            Instant.now()
        );
        
        sendMessage(session, errorMessage);
    }
}
```

#### 3. Session Management Service

```java
@Service
public class SessionManager {
    
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final ConversationService conversationService;
    
    public SessionManager(ConversationService conversationService) {
        this.conversationService = conversationService;
    }
    
    public void createSession(String sessionId) {
        String conversationId = generateConversationId();
        SessionInfo sessionInfo = new SessionInfo(
            sessionId,
            conversationId,
            Instant.now(),
            new AtomicLong(0)
        );
        
        sessions.put(sessionId, sessionInfo);
        conversationService.createConversation(conversationId);
    }
    
    public String getConversationId(String sessionId) {
        SessionInfo session = sessions.get(sessionId);
        if (session != null) {
            session.getMessageCount().incrementAndGet();
            return session.getConversationId();
        }
        return null;
    }
    
    public void closeSession(String sessionId) {
        SessionInfo session = sessions.remove(sessionId);
        if (session != null) {
            // Optionally archive conversation
            conversationService.archiveConversation(session.getConversationId());
        }
    }
    
    public void broadcastToAll(WebSocketMessage message) {
        sessions.values().forEach(session -> {
            // Implementation for broadcasting
        });
    }
    
    private String generateConversationId() {
        return "conv_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    @Scheduled(fixedRate = 300000) // Clean up every 5 minutes
    public void cleanupInactiveSessions() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(30));
        
        sessions.entrySet().removeIf(entry -> 
            entry.getValue().getCreatedAt().isBefore(cutoff));
    }
}
```

### Event-driven Architecture

#### 1. AI Event Publisher

```java
@Service
public class AIEventService {
    
    private final ApplicationEventPublisher eventPublisher;
    private final ChatModel chatModel;
    
    public AIEventService(ApplicationEventPublisher eventPublisher, 
                         ChatModel chatModel) {
        this.eventPublisher = eventPublisher;
        this.chatModel = chatModel;
    }
    
    @EventListener
    @Async
    public void handleUserMessageEvent(UserMessageEvent event) {
        try {
            Prompt prompt = new Prompt(event.getMessage());
            
            chatModel.stream(prompt)
                .doOnNext(response -> {
                    AIResponseEvent responseEvent = new AIResponseEvent(
                        event.getConversationId(),
                        response.getResult().getOutput().getContent(),
                        false // not complete
                    );
                    eventPublisher.publishEvent(responseEvent);
                })
                .doOnComplete(() -> {
                    AIResponseCompleteEvent completeEvent = new AIResponseCompleteEvent(
                        event.getConversationId(),
                        event.getUserId()
                    );
                    eventPublisher.publishEvent(completeEvent);
                })
                .subscribe();
                
        } catch (Exception e) {
            AIErrorEvent errorEvent = new AIErrorEvent(
                event.getConversationId(),
                e.getMessage(),
                event.getUserId()
            );
            eventPublisher.publishEvent(errorEvent);
        }
    }
    
    @EventListener
    public void handleAIResponseEvent(AIResponseEvent event) {
        // Broadcast to WebSocket clients
        webSocketBroadcastService.broadcastToConversation(
            event.getConversationId(),
            new WebSocketMessage("ai-response", event.getContent(), Instant.now())
        );
    }
    
    @EventListener
    public void handleAIErrorEvent(AIErrorEvent event) {
        // Log error and notify user
        logger.error("AI Error in conversation {}: {}", 
            event.getConversationId(), event.getErrorMessage());
            
        webSocketBroadcastService.broadcastToUser(
            event.getUserId(),
            new WebSocketMessage("error", event.getErrorMessage(), Instant.now())
        );
    }
}
```

#### 2. Message Queue Integration

```java
@Component
public class AIMessageQueueProcessor {
    
    private final ChatModel chatModel;
    private final RabbitTemplate rabbitTemplate;
    
    public AIMessageQueueProcessor(ChatModel chatModel, RabbitTemplate rabbitTemplate) {
        this.chatModel = chatModel;
        this.rabbitTemplate = rabbitTemplate;
    }
    
    @RabbitListener(queues = "ai.processing.queue")
    public void processAIRequest(AIProcessingMessage message) {
        try {
            Prompt prompt = new Prompt(message.getPrompt());
            
            chatModel.stream(prompt)
                .collectList()
                .subscribe(responses -> {
                    String fullResponse = responses.stream()
                        .map(response -> response.getResult().getOutput().getContent())
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining());
                    
                    AIProcessingResult result = new AIProcessingResult(
                        message.getId(),
                        fullResponse,
                        ProcessingStatus.COMPLETED
                    );
                    
                    rabbitTemplate.convertAndSend(
                        "ai.results.exchange",
                        message.getReplyTo(),
                        result
                    );
                });
                
        } catch (Exception e) {
            AIProcessingResult errorResult = new AIProcessingResult(
                message.getId(),
                "Processing failed: " + e.getMessage(),
                ProcessingStatus.FAILED
            );
            
            rabbitTemplate.convertAndSend(
                "ai.results.exchange",
                message.getReplyTo(),
                errorResult
            );
        }
    }
    
    @RabbitListener(queues = "ai.batch.processing.queue")
    public void processBatchAIRequest(List<AIProcessingMessage> messages) {
        Flux.fromIterable(messages)
            .flatMap(this::processMessage)
            .collectList()
            .subscribe(results -> {
                rabbitTemplate.convertAndSend(
                    "ai.batch.results.exchange",
                    "batch.results",
                    results
                );
            });
    }
    
    private Mono<AIProcessingResult> processMessage(AIProcessingMessage message) {
        return Mono.fromSupplier(() -> new Prompt(message.getPrompt()))
            .flatMapMany(prompt -> chatModel.stream(prompt))
            .collectList()
            .map(responses -> {
                String content = responses.stream()
                    .map(r -> r.getResult().getOutput().getContent())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining());
                
                return new AIProcessingResult(
                    message.getId(),
                    content,
                    ProcessingStatus.COMPLETED
                );
            })
            .onErrorReturn(new AIProcessingResult(
                message.getId(),
                "Processing failed",
                ProcessingStatus.FAILED
            ));
    }
}
```

## Real-World Use Cases and Examples

### 1. Live Customer Support Chat

```java
@RestController
@RequestMapping("/api/support")
public class LiveSupportController {
    
    private final ChatModel chatModel;
    private final SupportTicketService ticketService;
    
    @GetMapping(value = "/chat/{ticketId}/stream", 
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSupportResponse(@PathVariable String ticketId,
                                          @RequestParam String message) {
        
        SseEmitter emitter = new SseEmitter(60000L);
        
        CompletableFuture.runAsync(() -> {
            try {
                // Get ticket context
                SupportTicket ticket = ticketService.getTicket(ticketId);
                
                // Create context-aware prompt
                String systemPrompt = buildSupportSystemPrompt(ticket);
                List<Message> messages = Arrays.asList(
                    new SystemMessage(systemPrompt),
                    new UserMessage(message)
                );
                
                Prompt prompt = new Prompt(messages);
                
                chatModel.stream(prompt)
                    .doOnNext(response -> {
                        String content = response.getResult().getOutput().getContent();
                        if (content != null) {
                            try {
                                emitter.send(SseEmitter.event()
                                    .name("support-response")
                                    .data(new SupportResponse(content, ticket.getPriority())));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                    })
                    .doOnComplete(() -> {
                        ticketService.addAIInteraction(ticketId, message);
                        emitter.complete();
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();
                    
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
    
    private String buildSupportSystemPrompt(SupportTicket ticket) {
        return String.format(
            "You are a customer support assistant for %s. " +
            "Customer: %s (Priority: %s). " +
            "Issue: %s. " +
            "Previous interactions: %d. " +
            "Be helpful, professional, and provide specific solutions.",
            ticket.getProductName(),
            ticket.getCustomerName(),
            ticket.getPriority(),
            ticket.getDescription(),
            ticket.getInteractionCount()
        );
    }
}
```

### 2. Real-time Code Assistant

```java
@RestController
@RequestMapping("/api/code-assistant")
public class CodeAssistantController {
    
    private final ChatModel chatModel;
    private final CodeAnalysisService codeAnalysisService;
    
    @PostMapping(value = "/analyze-stream", 
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamCodeAnalysis(
            @RequestBody CodeAnalysisRequest request) {
        
        SseEmitter emitter = new SseEmitter(120000L); // 2 minutes
        
        CompletableFuture.runAsync(() -> {
            try {
                // Analyze code structure first
                CodeAnalysis analysis = codeAnalysisService.analyze(request.getCode());
                
                // Send initial analysis
                emitter.send(SseEmitter.event()
                    .name("analysis-start")
                    .data(analysis.getSummary()));
                
                // Stream AI suggestions
                String prompt = buildCodeAnalysisPrompt(request.getCode(), analysis);
                
                chatModel.stream(new Prompt(prompt))
                    .doOnNext(response -> {
                        String content = response.getResult().getOutput().getContent();
                        if (content != null) {
                            try {
                                emitter.send(SseEmitter.event()