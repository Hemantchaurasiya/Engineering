# Phase 11: Real-world Applications (Week 21-22)

Welcome to the practical application phase! Here we'll build real-world reactive applications using everything you've learned.

## 11.1 Web Applications with WebFlux

### Setting Up Spring WebFlux

First, let's create a reactive web application:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-r2dbc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.r2dbc</groupId>
        <artifactId>r2dbc-h2</artifactId>
    </dependency>
</dependencies>
```

### Reactive Controllers

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Get single user
    @GetMapping("/{id}")
    public Mono<ResponseEntity<User>> getUserById(@PathVariable String id) {
        return userService.findById(id)
            .map(user -> ResponseEntity.ok(user))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // Get all users with streaming response
    @GetMapping(produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<User> getAllUsers() {
        return userService.findAll()
            .delayElements(Duration.ofMillis(100)); // Simulate processing delay
    }

    // Create user
    @PostMapping
    public Mono<ResponseEntity<User>> createUser(@RequestBody @Valid User user) {
        return userService.save(user)
            .map(savedUser -> ResponseEntity.status(HttpStatus.CREATED).body(savedUser))
            .onErrorResume(DuplicateKeyException.class, 
                e -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build()));
    }

    // Update user
    @PutMapping("/{id}")
    public Mono<ResponseEntity<User>> updateUser(@PathVariable String id, @RequestBody User user) {
        return userService.update(id, user)
            .map(updatedUser -> ResponseEntity.ok(updatedUser))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // Delete user
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable String id) {
        return userService.deleteById(id)
            .then(Mono.just(ResponseEntity.noContent().<Void>build()))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
```

### Server-Sent Events (SSE)

```java
@RestController
public class EventController {

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamEvents() {
        return Flux.interval(Duration.ofSeconds(1))
            .map(sequence -> ServerSentEvent.<String>builder()
                .id(String.valueOf(sequence))
                .event("periodic-event")
                .data("Event " + sequence + " at " + LocalTime.now())
                .build())
            .doOnCancel(() -> System.out.println("Client disconnected"))
            .onBackpressureDrop();
    }

    @GetMapping(path = "/notifications/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Notification>> userNotifications(@PathVariable String userId) {
        return notificationService.getNotificationStream(userId)
            .map(notification -> ServerSentEvent.<Notification>builder()
                .id(notification.getId())
                .event("notification")
                .data(notification)
                .build())
            .onErrorResume(e -> Flux.just(
                ServerSentEvent.<Notification>builder()
                    .event("error")
                    .data(new Notification("error", "Connection error"))
                    .build()
            ));
    }
}
```

### WebSocket Support

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }
}

@Controller
public class ChatController {

    private final Sinks.Many<ChatMessage> chatSink = Sinks.many()
        .multicast()
        .onBackpressureBuffer();

    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessage message) {
        message.setTimestamp(Instant.now());
        chatSink.tryEmitNext(message);
    }

    @GetMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatMessage> getChatStream() {
        return chatSink.asFlux()
            .onBackpressureBuffer(1000)
            .doOnCancel(() -> System.out.println("Chat client disconnected"));
    }
}
```

### Database Integration (R2DBC)

```java
// Entity
@Table("users")
public class User {
    @Id
    private String id;
    private String username;
    private String email;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // constructors, getters, setters
}

// Repository
public interface UserRepository extends ReactiveCrudRepository<User, String> {
    
    Flux<User> findByUsernameContaining(String username);
    
    @Query("SELECT * FROM users WHERE email = :email")
    Mono<User> findByEmail(String email);
    
    @Query("SELECT * FROM users WHERE created_at > :date ORDER BY created_at DESC")
    Flux<User> findRecentUsers(LocalDateTime date);
}

// Service
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Mono<User> findById(String id) {
        return userRepository.findById(id)
            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found: " + id)));
    }

    public Flux<User> findAll() {
        return userRepository.findAll()
            .onErrorResume(e -> {
                log.error("Error fetching users", e);
                return Flux.empty();
            });
    }

    public Mono<User> save(User user) {
        user.setId(UUID.randomUUID().toString());
        user.setCreatedAt(LocalDateTime.now());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        return userRepository.save(user)
            .onErrorMap(DuplicateKeyException.class, 
                e -> new UserAlreadyExistsException("User already exists"));
    }

    public Mono<User> update(String id, User userUpdate) {
        return userRepository.findById(id)
            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found: " + id)))
            .map(existingUser -> {
                existingUser.setUsername(userUpdate.getUsername());
                existingUser.setEmail(userUpdate.getEmail());
                existingUser.setUpdatedAt(LocalDateTime.now());
                return existingUser;
            })
            .flatMap(userRepository::save);
    }

    public Mono<Void> deleteById(String id) {
        return userRepository.existsById(id)
            .flatMap(exists -> {
                if (exists) {
                    return userRepository.deleteById(id);
                } else {
                    return Mono.error(new UserNotFoundException("User not found: " + id));
                }
            });
    }
}
```

## 11.2 Microservices Communication

### Reactive HTTP Clients

```java
@Service
public class ExternalApiClient {

    private final WebClient webClient;

    public ExternalApiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.external-service.com")
            .defaultHeader("User-Agent", "MyApp/1.0")
            .build();
    }

    public Mono<ApiResponse> fetchData(String id) {
        return webClient
            .get()
            .uri("/data/{id}", id)
            .retrieve()
            .onStatus(HttpStatus::is4xxClientError, response -> 
                Mono.error(new ClientException("Client error: " + response.statusCode())))
            .onStatus(HttpStatus::is5xxServerError, response -> 
                Mono.error(new ServerException("Server error: " + response.statusCode())))
            .bodyToMono(ApiResponse.class)
            .timeout(Duration.ofSeconds(10))
            .retry(3);
    }

    public Flux<DataItem> fetchAllData() {
        return webClient
            .get()
            .uri("/data")
            .retrieve()
            .bodyToFlux(DataItem.class)
            .onBackpressureBuffer(1000);
    }

    public Mono<PostResponse> postData(PostRequest request) {
        return webClient
            .post()
            .uri("/data")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(PostResponse.class)
            .onErrorMap(WebClientResponseException.class, this::handleWebClientError);
    }

    private RuntimeException handleWebClientError(WebClientResponseException ex) {
        return switch (ex.getStatusCode().value()) {
            case 400 -> new BadRequestException("Invalid request: " + ex.getResponseBodyAsString());
            case 401 -> new UnauthorizedException("Unauthorized");
            case 404 -> new NotFoundException("Resource not found");
            case 429 -> new RateLimitException("Rate limit exceeded");
            default -> new ExternalServiceException("External service error: " + ex.getMessage());
        };
    }
}
```

### Circuit Breakers

```java
@Component
public class CircuitBreakerService {

    private final CircuitBreaker circuitBreaker;
    private final ExternalApiClient apiClient;

    public CircuitBreakerService(ExternalApiClient apiClient) {
        this.apiClient = apiClient;
        this.circuitBreaker = CircuitBreaker.ofDefaults("externalApi");
        
        // Configure circuit breaker
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.info("Circuit breaker state transition: {}", event));
    }

    public Mono<String> callExternalService(String id) {
        return Mono.fromCallable(() -> 
            circuitBreaker.executeSupplier(() -> 
                apiClient.fetchData(id).block()))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(CallNotPermittedException.class, 
                e -> Mono.just("Fallback response due to circuit breaker"))
            .onErrorResume(Exception.class, 
                e -> Mono.just("Fallback response due to error"));
    }
}
```

### Retry and Timeout Patterns

```java
@Service
public class ResilientApiService {

    private final WebClient webClient;

    public Mono<ApiResponse> robustApiCall(String endpoint) {
        return webClient
            .get()
            .uri(endpoint)
            .retrieve()
            .bodyToMono(ApiResponse.class)
            .timeout(Duration.ofSeconds(5))
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10))
                .filter(throwable -> throwable instanceof ConnectException 
                    || throwable instanceof TimeoutException))
            .onErrorResume(TimeoutException.class, 
                e -> Mono.error(new ServiceUnavailableException("Service timeout")))
            .onErrorResume(ConnectException.class, 
                e -> Mono.error(new ServiceUnavailableException("Connection failed")));
    }

    public Mono<String> callWithExponentialBackoff(String data) {
        return Mono.fromCallable(() -> processData(data))
            .subscribeOn(Schedulers.boundedElastic())
            .retryWhen(Retry.exponentialBackoff(5, Duration.ofMillis(100))
                .maxBackoff(Duration.ofSeconds(30))
                .jitter(0.1)
                .filter(this::isRetryableException)
                .doBeforeRetry(retrySignal -> 
                    log.warn("Retrying call, attempt: {}", retrySignal.totalRetries() + 1)));
    }

    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof ConnectException
            || throwable instanceof SocketTimeoutException
            || (throwable instanceof WebClientResponseException we 
                && we.getStatusCode().is5xxServerError());
    }
}
```

### Event-driven Architectures

```java
// Event Publisher
@Component
public class EventPublisher {

    private final Sinks.Many<DomainEvent> eventSink = Sinks.many()
        .multicast()
        .onBackpressureBuffer();

    public void publishEvent(DomainEvent event) {
        eventSink.tryEmitNext(event);
    }

    public Flux<DomainEvent> getEventStream() {
        return eventSink.asFlux();
    }
}

// Event Listeners
@Component
public class OrderEventHandler {

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        processOrderCreation(event)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                result -> log.info("Order processed: {}", result),
                error -> log.error("Error processing order", error)
            );
    }

    private Mono<String> processOrderCreation(OrderCreatedEvent event) {
        return Mono.just(event)
            .flatMap(this::validateOrder)
            .flatMap(this::calculateTotals)
            .flatMap(this::updateInventory)
            .flatMap(this::sendNotification);
    }
}
```

## 11.3 Streaming Data Processing

### Kafka Integration

```java
@Configuration
public class KafkaConfig {

    @Bean
    public ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate() {
        Map<String, Object> props = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
            ConsumerConfig.GROUP_ID_CONFIG, "reactive-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        
        return new ReactiveKafkaConsumerTemplate<>(
            ReceiverOptions.create(props));
    }

    @Bean
    public ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate() {
        Map<String, Object> props = Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        
        return new ReactiveKafkaProducerTemplate<>(
            SenderOptions.create(props));
    }
}

@Service
public class MessageProcessor {

    private final ReactiveKafkaConsumerTemplate<String, String> consumer;
    private final ReactiveKafkaProducerTemplate<String, String> producer;

    public void startProcessing() {
        consumer
            .receiveAutoAck()
            .concatMap(record -> processMessage(record.value())
                .map(result -> new ProducerRecord<>("processed-topic", record.key(), result)))
            .as(producer::send)
            .subscribe(
                result -> log.info("Message processed and sent: {}", result),
                error -> log.error("Error processing message", error)
            );
    }

    private Mono<String> processMessage(String message) {
        return Mono.just(message)
            .map(String::toUpperCase)
            .delayElement(Duration.ofMillis(100))
            .onErrorResume(e -> Mono.just("ERROR: " + message));
    }
}
```

### Real-time Analytics

```java
@Service
public class RealTimeAnalytics {

    public Flux<Analytics> computeRealTimeMetrics(Flux<Event> eventStream) {
        return eventStream
            .window(Duration.ofMinutes(1))
            .flatMap(window -> 
                window.groupBy(Event::getType)
                    .flatMap(groupedFlux -> 
                        groupedFlux.reduce(new Analytics(groupedFlux.key()), 
                            this::aggregateEvent)))
            .onBackpressureBuffer(1000);
    }

    public Flux<TrendingItem> detectTrending(Flux<UserAction> actions) {
        return actions
            .window(Duration.ofMinutes(5), Duration.ofMinutes(1))
            .flatMap(window -> 
                window.groupBy(UserAction::getItemId)
                    .flatMap(groupedActions -> 
                        groupedActions.count()
                            .map(count -> new TrendingItem(groupedActions.key(), count))))
            .filter(item -> item.getCount() > 100)
            .sort(Comparator.comparing(TrendingItem::getCount).reversed())
            .take(10);
    }

    public Flux<Alert> monitorAnomalies(Flux<Metric> metrics) {
        return metrics
            .buffer(Duration.ofMinutes(5))
            .filter(buffer -> !buffer.isEmpty())
            .map(this::calculateStatistics)
            .flatMap(stats -> 
                metrics.filter(metric -> isAnomaly(metric, stats))
                    .map(metric -> new Alert("Anomaly detected", metric)));
    }

    private Analytics aggregateEvent(Analytics analytics, Event event) {
        analytics.incrementCount();
        analytics.updateAverage(event.getValue());
        return analytics;
    }

    private Statistics calculateStatistics(List<Metric> metrics) {
        double mean = metrics.stream().mapToDouble(Metric::getValue).average().orElse(0);
        double stdDev = calculateStandardDeviation(metrics, mean);
        return new Statistics(mean, stdDev);
    }

    private boolean isAnomaly(Metric metric, Statistics stats) {
        return Math.abs(metric.getValue() - stats.getMean()) > 2 * stats.getStdDev();
    }
}
```

### Event Sourcing Patterns

```java
// Event Store
@Repository
public class EventStore {

    private final R2dbcEntityTemplate template;

    public Mono<Void> saveEvent(DomainEvent event) {
        return template.insert(EventEntity.from(event))
            .then();
    }

    public Flux<DomainEvent> getEventsForAggregate(String aggregateId) {
        return template.select(EventEntity.class)
            .matching(query(where("aggregate_id").is(aggregateId)))
            .all()
            .map(EventEntity::toDomainEvent);
    }

    public Flux<DomainEvent> getEventsSince(Instant timestamp) {
        return template.select(EventEntity.class)
            .matching(query(where("created_at").greaterThan(timestamp)))
            .all()
            .map(EventEntity::toDomainEvent);
    }
}

// Aggregate Root
public class OrderAggregate {

    private String id;
    private List<DomainEvent> uncommittedEvents = new ArrayList<>();

    public static Mono<OrderAggregate> fromEvents(Flux<DomainEvent> events) {
        return events.reduce(new OrderAggregate(), OrderAggregate::apply);
    }

    public OrderAggregate apply(DomainEvent event) {
        return switch (event) {
            case OrderCreatedEvent e -> applyOrderCreated(e);
            case OrderUpdatedEvent e -> applyOrderUpdated(e);
            case OrderCancelledEvent e -> applyOrderCancelled(e);
            default -> this;
        };
    }

    public void createOrder(CreateOrderCommand command) {
        // Business logic validation
        var event = new OrderCreatedEvent(command.getOrderId(), command.getItems());
        addEvent(event);
    }

    private void addEvent(DomainEvent event) {
        apply(event);
        uncommittedEvents.add(event);
    }

    public Flux<DomainEvent> getUncommittedEvents() {
        return Flux.fromIterable(uncommittedEvents);
    }
}

// Event Sourced Repository
@Repository
public class OrderRepository {

    private final EventStore eventStore;

    public Mono<OrderAggregate> findById(String orderId) {
        return OrderAggregate.fromEvents(
            eventStore.getEventsForAggregate(orderId));
    }

    public Mono<Void> save(OrderAggregate aggregate) {
        return aggregate.getUncommittedEvents()
            .flatMap(eventStore::saveEvent)
            .then();
    }
}
```

## Practical Exercises

1. **Build a Reactive Chat Application**
   - Implement WebSocket-based real-time chat
   - Add user presence detection
   - Implement message history with R2DBC

2. **Create a Monitoring Dashboard**
   - Stream system metrics via SSE
   - Implement real-time alerting
   - Add circuit breaker status monitoring

3. **Implement Event-Driven Order Processing**
   - Create order workflow with event sourcing
   - Implement saga pattern for distributed transactions
   - Add real-time order tracking

## Key Takeaways

- WebFlux provides non-blocking web stack for reactive applications
- R2DBC enables reactive database access
- Circuit breakers and retry patterns improve resilience
- Event-driven architectures enable loose coupling
- Streaming data processing handles real-time analytics
- Event sourcing provides audit trail and temporal queries

Next week in Phase 12, we'll explore advanced topics including RxJava comparison, Reactor Netty, and integration patterns!