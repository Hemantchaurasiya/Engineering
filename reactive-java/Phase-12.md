# Phase 12: Advanced Topics and Ecosystem (Week 23-24)

Welcome to the final phase of your reactive programming journey! Here we'll explore advanced concepts, compare different reactive libraries, and master the broader ecosystem.

## 12.1 RxJava Comparison

### Core Similarities

Both Project Reactor and RxJava implement the Reactive Streams specification and share similar concepts:

```java
// Project Reactor
Flux.just(1, 2, 3)
    .map(x -> x * 2)
    .filter(x -> x > 2)
    .subscribe(System.out::println);

// RxJava
Observable.just(1, 2, 3)
    .map(x -> x * 2)
    .filter(x -> x > 2)
    .subscribe(System.out::println);
```

### Key Differences

| Feature | Project Reactor | RxJava |
|---------|----------------|---------|
| **Core Types** | `Mono<T>`, `Flux<T>` | `Single<T>`, `Maybe<T>`, `Observable<T>`, `Flowable<T>` |
| **Backpressure** | Built-in for all streams | `Observable` (no backpressure), `Flowable` (with backpressure) |
| **Integration** | Native Spring integration | Broader Android/Java ecosystem |
| **Error Handling** | More structured approach | More operators but complex |
| **Performance** | Optimized for server-side | Optimized for client-side |

### Detailed Comparison

```java
// Project Reactor - Single value or empty
Mono<String> mono = Mono.just("Hello")
    .map(String::toUpperCase)
    .onErrorReturn("DEFAULT");

// RxJava - Equivalent using Single
Single<String> single = Single.just("Hello")
    .map(String::toUpperCase)
    .onErrorReturn("DEFAULT");

// RxJava - Using Maybe for optional values
Maybe<String> maybe = Maybe.just("Hello")
    .map(String::toUpperCase)
    .onErrorReturn("DEFAULT");
```

```java
// Project Reactor - Multiple values with backpressure
Flux<Integer> flux = Flux.range(1, 1000)
    .onBackpressureBuffer(100)
    .publishOn(Schedulers.parallel())
    .map(i -> heavyComputation(i));

// RxJava - Using Flowable for backpressure support
Flowable<Integer> flowable = Flowable.range(1, 1000)
    .onBackpressureBuffer(100)
    .observeOn(Schedulers.computation())
    .map(i -> heavyComputation(i));

// RxJava - Using Observable (no backpressure)
Observable<Integer> observable = Observable.range(1, 100)
    .observeOn(Schedulers.computation())
    .map(i -> lightComputation(i));
```

### Advanced Operator Comparisons

```java
// Combining streams - Project Reactor
Flux<String> combined = Flux.combineLatest(
    flux1, flux2, flux3,
    (a, b, c) -> a + b + c
);

// Combining streams - RxJava
Observable<String> combined = Observable.combineLatest(
    observable1, observable2, observable3,
    (a, b, c) -> a + b + c
);
```

### Migration Strategies

```java
// Migration Helper Class
public class ReactorToRxJavaAdapter {

    // Convert Mono to Single
    public static <T> Single<T> toSingle(Mono<T> mono) {
        return Single.fromPublisher(mono.toProcessor());
    }

    // Convert Single to Mono
    public static <T> Mono<T> toMono(Single<T> single) {
        return Mono.fromDirect(single.toFlowable());
    }

    // Convert Flux to Flowable
    public static <T> Flowable<T> toFlowable(Flux<T> flux) {
        return Flowable.fromPublisher(flux);
    }

    // Convert Flowable to Flux
    public static <T> Flux<T> toFlux(Flowable<T> flowable) {
        return Flux.from(flowable);
    }
}

// Usage example
@Service
public class HybridReactiveService {

    private final ReactorService reactorService;
    private final RxJavaService rxJavaService;

    public Mono<Result> processData(String input) {
        // Use RxJava service and convert result
        Single<ProcessedData> rxResult = rxJavaService.process(input);
        Mono<ProcessedData> reactorResult = ReactorToRxJavaAdapter.toMono(rxResult);
        
        // Continue with Reactor
        return reactorResult
            .flatMap(reactorService::furtherProcess);
    }
}
```

### When to Use Each

**Choose Project Reactor when:**
- Building Spring Boot applications
- Server-side reactive applications
- Need tight integration with Spring ecosystem
- Want unified backpressure handling
- Building microservices

**Choose RxJava when:**
- Android development
- Legacy Java applications
- Need fine-grained control over backpressure
- Working with existing RxJava codebase
- Client-side applications

## 12.2 Reactor Netty

### HTTP Server/Client

```java
// Basic HTTP Server
public class ReactorNettyServer {

    public static void main(String[] args) {
        HttpServer server = HttpServer.create()
            .port(8080)
            .route(routes -> 
                routes.GET("/hello/{name}", (request, response) -> 
                    response.sendString(
                        Mono.just("Hello " + request.param("name") + "!")
                    )
                )
                .POST("/echo", (request, response) -> 
                    response.sendString(
                        request.receive()
                            .aggregate()
                            .asString()
                            .map(body -> "Echo: " + body)
                    )
                )
                .GET("/stream", (request, response) -> 
                    response.sendString(
                        Flux.interval(Duration.ofSeconds(1))
                            .map(i -> "Data " + i + "\n")
                    )
                )
            );

        server.bindNow()
            .onDispose()
            .block();
    }
}
```

```java
// Advanced HTTP Server with middleware
@Component
public class AdvancedHttpServer {

    public void startServer() {
        HttpServer.create()
            .port(8080)
            .doOnConnection(this::configureConnection)
            .handle(this::handleRequest)
            .bindNow();
    }

    private void configureConnection(Connection connection) {
        connection.addHandlerLast(new LoggingHandler(LogLevel.INFO));
    }

    private Mono<Void> handleRequest(HttpServerRequest request, HttpServerResponse response) {
        return switch (request.path()) {
            case "/health" -> handleHealthCheck(response);
            case "/api/data" -> handleApiData(request, response);
            case "/upload" -> handleFileUpload(request, response);
            default -> response.status(HttpResponseStatus.NOT_FOUND).send();
        };
    }

    private Mono<Void> handleHealthCheck(HttpServerResponse response) {
        return response
            .header("Content-Type", "application/json")
            .sendString(Mono.just("{\"status\":\"UP\"}"));
    }

    private Mono<Void> handleApiData(HttpServerRequest request, HttpServerResponse response) {
        return response
            .header("Content-Type", "application/json")
            .sendString(
                dataService.fetchData()
                    .map(this::toJson)
            );
    }

    private Mono<Void> handleFileUpload(HttpServerRequest request, HttpServerResponse response) {
        return request.receive()
            .aggregate()
            .flatMap(fileService::saveFile)
            .then(response.status(HttpResponseStatus.CREATED).send());
    }
}
```

```java
// HTTP Client
@Service
public class ReactorNettyClient {

    private final HttpClient httpClient;

    public ReactorNettyClient() {
        this.httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(10))
                    .addHandlerLast(new WriteTimeoutHandler(10)));
    }

    public Mono<String> getData(String url) {
        return httpClient
            .get()
            .uri(url)
            .response((response, byteBuf) -> {
                if (response.status().code() == 200) {
                    return byteBuf.asString();
                } else {
                    return Mono.error(new HttpException(
                        "HTTP " + response.status().code()));
                }
            });
    }

    public Mono<String> postData(String url, String data) {
        return httpClient
            .post()
            .uri(url)
            .send(ByteBufFlux.fromString(Mono.just(data)))
            .response((response, byteBuf) -> 
                byteBuf.asString().single());
    }

    public Flux<String> streamData(String url) {
        return httpClient
            .get()
            .uri(url)
            .response((response, byteBuf) -> 
                byteBuf.asString()
                    .split("\n")
                    .filter(line -> !line.isEmpty()));
    }
}
```

### TCP Server/Client

```java
// TCP Server
@Service
public class TcpEchoServer {

    public void start() {
        TcpServer.create()
            .port(8081)
            .handle((inbound, outbound) -> 
                outbound.send(
                    inbound.receive()
                        .asString()
                        .map(data -> "Echo: " + data)
                        .map(ByteBuf::copiedBuffer)
                )
            )
            .bindNow()
            .onDispose()
            .block();
    }
}

// TCP Client
@Service
public class TcpClient {

    public Mono<String> sendMessage(String host, int port, String message) {
        return reactor.netty.tcp.TcpClient.create()
            .host(host)
            .port(port)
            .connect()
            .flatMap(connection -> 
                connection.outbound()
                    .sendString(Mono.just(message))
                    .then()
                    .thenMany(connection.inbound().receive().asString())
                    .single()
                    .doFinally(signalType -> connection.dispose())
            );
    }
}
```

### Performance Tuning

```java
@Configuration
public class NettyConfiguration {

    @Bean
    public HttpClient optimizedHttpClient() {
        return HttpClient.create()
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.SO_REUSEADDR, true)
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .responseTimeout(Duration.ofSeconds(30))
            .metrics(true, Function.identity())
            .compress(true)
            .keepAlive(true)
            .wiretap(true); // Only for debugging
    }

    @Bean
    public HttpServer optimizedHttpServer() {
        return HttpServer.create()
            .option(ChannelOption.SO_BACKLOG, 1024)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .metrics(true, Function.identity())
            .accessLog(true)
            .compress(true);
    }
}
```

## 12.3 Integration Patterns

### Spring Boot Integration

```java
// Complete Spring Boot Reactive Application
@SpringBootApplication
public class ReactiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveApplication.class, args);
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
            .option(DRIVER, "h2")
            .option(PROTOCOL, "mem")
            .option(DATABASE, "testdb")
            .build());
    }

    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }
}

// Configuration Properties
@ConfigurationProperties(prefix = "app.reactive")
@Data
public class ReactiveProperties {
    private int bufferSize = 1000;
    private Duration timeout = Duration.ofSeconds(30);
    private Retry retry = new Retry();

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private Duration delay = Duration.ofSeconds(1);
    }
}
```

### Security in Reactive Applications

```java
@EnableWebFluxSecurity
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/public/**").permitAll()
                .pathMatchers("/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(OAuth2ResourceServerSpec::jwt)
            .csrf().disable()
            .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation("https://auth-server.com");
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
            .map(user -> User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(user.getRoles().toArray(new String[0]))
                .build());
    }
}

// Reactive Security Service
@Service
public class ReactiveSecurityService {

    public Mono<User> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
            .cast(JwtAuthenticationToken.class)
            .map(JwtAuthenticationToken::getToken)
            .map(jwt -> jwt.getClaimAsString("sub"))
            .flatMap(userService::findById);
    }

    public Mono<Boolean> hasPermission(String resource, String action) {
        return getCurrentUser()
            .flatMap(user -> permissionService.checkPermission(user.getId(), resource, action));
    }
}
```

### Caching Strategies

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return RedisCacheManager.builder(redisConnectionFactory())
            .cacheDefaults(cacheConfiguration())
            .build();
    }

    private RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}

@Service
public class CachedReactiveService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final Map<String, Mono<String>> cache = new ConcurrentHashMap<>();

    // Manual caching with Reactor
    public Mono<String> getCachedData(String key) {
        return cache.computeIfAbsent(key, k -> 
            loadDataFromDatabase(k)
                .cache(Duration.ofMinutes(10))
        );
    }

    // Redis-based reactive caching
    public Mono<User> getCachedUser(String userId) {
        String cacheKey = "user:" + userId;
        
        return redisTemplate.opsForValue()
            .get(cacheKey)
            .cast(User.class)
            .switchIfEmpty(
                loadUserFromDatabase(userId)
                    .flatMap(user -> 
                        redisTemplate.opsForValue()
                            .set(cacheKey, user, Duration.ofMinutes(30))
                            .thenReturn(user)
                    )
            );
    }

    // Cache-aside pattern with automatic expiration
    public Mono<List<Product>> getCachedProducts(String category) {
        return Mono.fromCallable(() -> "products:" + category)
            .flatMap(cacheKey -> 
                redisTemplate.opsForValue()
                    .get(cacheKey)
                    .cast(new ParameterizedTypeReference<List<Product>>() {})
                    .switchIfEmpty(
                        productRepository.findByCategory(category)
                            .collectList()
                            .flatMap(products -> 
                                redisTemplate.opsForValue()
                                    .set(cacheKey, products, Duration.ofHours(1))
                                    .thenReturn(products)
                            )
                    )
            );
    }

    // Write-through caching
    public Mono<User> updateUser(User user) {
        return userRepository.save(user)
            .flatMap(savedUser -> {
                String cacheKey = "user:" + savedUser.getId();
                return redisTemplate.opsForValue()
                    .set(cacheKey, savedUser, Duration.ofMinutes(30))
                    .thenReturn(savedUser);
            });
    }

    // Cache invalidation
    public Mono<Void> invalidateUserCache(String userId) {
        return redisTemplate.delete("user:" + userId).then();
    }
}
```

### Monitoring and Observability

```java
@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Bean
    public ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
            .observationHandler(new DefaultMeterObservationHandler(meterRegistry()))
            .observationHandler(new ObservationTextPublisher());
        return registry;
    }
}

@Service
public class ObservableReactiveService {

    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;
    private final Counter requestCounter;
    private final Timer responseTimer;

    public ObservableReactiveService(MeterRegistry meterRegistry, 
                                   ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
        this.requestCounter = Counter.builder("api.requests")
            .description("Total API requests")
            .register(meterRegistry);
        this.responseTimer = Timer.builder("api.response.time")
            .description("API response time")
            .register(meterRegistry);
    }

    public Mono<ApiResponse> processRequest(ApiRequest request) {
        return Mono.just(request)
            .doOnSubscribe(subscription -> requestCounter.increment())
            .transformDeferred(source -> 
                Timer.Sample.start(meterRegistry)
                    .stop(responseTimer)
                    .observeOn(Schedulers.boundedElastic())
                    .then(source))
            .flatMap(this::performBusinessLogic)
            .name("api.process.request")
            .metrics()
            .checkpoint("After business logic processing")
            .tap(Micrometer.observation(observationRegistry)
                .lowCardinalityKeyValue("method", "processRequest"));
    }

    public Flux<DataPoint> streamData() {
        return Flux.interval(Duration.ofSeconds(1))
            .map(i -> new DataPoint("metric" + i, i))
            .doOnNext(dataPoint -> 
                Gauge.builder("stream.data.current")
                    .description("Current stream data value")
                    .register(meterRegistry, dataPoint, DataPoint::getValue))
            .name("data.stream")
            .metrics();
    }
}
```

### Advanced Error Handling Patterns

```java
@Component
public class ErrorHandlingService {

    // Circuit breaker with fallback
    public Mono<String> resilientCall(String input) {
        return Mono.fromCallable(() -> externalService.call(input))
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(5))
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .filter(throwable -> isRetryable(throwable)))
            .onErrorResume(TimeoutException.class, 
                e -> Mono.just("Timeout fallback"))
            .onErrorResume(CircuitBreakerOpenException.class, 
                e -> Mono.just("Circuit breaker fallback"))
            .onErrorResume(Exception.class, 
                e -> Mono.just("General fallback"));
    }

    // Bulkhead pattern
    public Mono<String> isolatedOperation(String input) {
        Scheduler dedicatedScheduler = Schedulers.newBoundedElastic(
            5, 100, "isolated-operation");
        
        return Mono.fromCallable(() -> heavyOperation(input))
            .subscribeOn(dedicatedScheduler)
            .timeout(Duration.ofSeconds(10))
            .doFinally(signalType -> dedicatedScheduler.dispose());
    }

    // Saga pattern for distributed transactions
    public Mono<Void> executeSaga(SagaContext context) {
        return Mono.just(context)
            .flatMap(this::step1)
            .flatMap(this::step2)
            .flatMap(this::step3)
            .onErrorResume(throwable -> 
                compensateSteps(context, throwable));
    }

    private Mono<Void> compensateSteps(SagaContext context, Throwable error) {
        return Flux.fromIterable(context.getCompensationActions())
            .reverse()
            .flatMap(action -> action.execute()
                .onErrorResume(e -> {
                    log.error("Compensation failed", e);
                    return Mono.empty();
                }))
            .then(Mono.error(error));
    }
}
```

## Best Practices Summary

### 1. Code Organization
```java
// Good: Separate concerns
@Service
public class UserService {
    
    private final UserRepository repository;
    private final EmailService emailService;
    private final CacheService cacheService;
    
    // Business logic methods
}

// Bad: Everything in controller
@RestController
public class UserController {
    // Don't put business logic here
}
```

### 2. Error Handling
```java
// Good: Structured error handling
public Mono<User> findUser(String id) {
    return repository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
        .onErrorMap(DataAccessException.class, 
            e -> new ServiceException("Database error", e));
}

// Bad: Generic error handling
public Mono<User> findUser(String id) {
    return repository.findById(id)
        .onErrorReturn(new User()); // Loses error information
}
```

### 3. Resource Management
```java
// Good: Proper disposal
public class StreamProcessor {
    private final Disposable subscription;
    
    @PostConstruct
    public void start() {
        subscription = dataStream
            .subscribe(this::processData);
    }
    
    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
```

### 4. Testing
```java
// Good: Comprehensive testing
@Test
public void testUserCreation() {
    StepVerifier.create(userService.createUser(validUser))
        .expectNext(savedUser)
        .verifyComplete();
    
    StepVerifier.create(userService.createUser(invalidUser))
        .expectError(ValidationException.class)
        .verify();
}
```

## Performance Optimization Checklist

✅ **Avoid blocking calls** in reactive chains  
✅ **Use appropriate schedulers** for different operations  
✅ **Implement proper backpressure handling**  
✅ **Cache frequently accessed data**  
✅ **Monitor and measure performance**  
✅ **Use connection pooling** for external services  
✅ **Implement circuit breakers** for resilience  
✅ **Optimize database queries** with proper indexing  

## Congratulations! 🎉

You've completed the comprehensive **Reactive Programming in Java - Complete Mastery Guide**!

### What You've Mastered:

- ✅ Reactive programming fundamentals and principles
- ✅ Project Reactor core concepts and operators
- ✅ Advanced patterns and error handling
- ✅ Threading and backpressure management
- ✅ Testing reactive applications
- ✅ Hot vs Cold publishers and Sinks
- ✅ Context and Reactive Streams
- ✅ Performance optimization techniques
- ✅ Real-world application patterns
- ✅ Advanced ecosystem integration

### Next Steps:

1. **Practice**: Build more complex reactive applications
2. **Contribute**: Participate in open-source reactive projects
3. **Stay Updated**: Follow reactive programming communities
4. **Teach Others**: Share your knowledge with the community
5. **Specialize**: Dive deeper into specific areas like Reactor Netty or RxJava

### Resources for Continued Learning:

- **Official Documentation**: [Project Reactor Reference](https://projectreactor.io/docs)
- **Community**: [Reactor Community](https://github.com/reactor/reactor)
- **Spring WebFlux**: [Spring Framework Documentation](https://spring.io/reactive)
- **Conferences**: Attend reactive programming talks and workshops

You're now equipped to build highly scalable, resilient, and performant reactive applications in Java! 🚀