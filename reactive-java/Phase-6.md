# Phase 6: Testing Reactive Code - Complete Implementation Guide

## 6.1 StepVerifier - The Heart of Reactive Testing

### Basic StepVerifier Usage

StepVerifier is Reactor's primary testing tool that allows you to define expectations about what your reactive streams should emit.

```java
import reactor.test.StepVerifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.Test;
import java.time.Duration;

public class BasicStepVerifierTests {
    
    @Test
    public void testSimpleFlux() {
        Flux<String> source = Flux.just("foo", "bar", "baz");
        
        StepVerifier.create(source)
            .expectNext("foo")
            .expectNext("bar")
            .expectNext("baz")
            .verifyComplete();
    }
    
    @Test
    public void testMono() {
        Mono<String> source = Mono.just("hello");
        
        StepVerifier.create(source)
            .expectNext("hello")
            .verifyComplete();
    }
    
    @Test
    public void testEmptyMono() {
        Mono<String> source = Mono.empty();
        
        StepVerifier.create(source)
            .verifyComplete();
    }
    
    @Test
    public void testMultipleExpectations() {
        Flux<Integer> source = Flux.range(1, 5);
        
        StepVerifier.create(source)
            .expectNext(1, 2, 3, 4, 5)  // Multiple values at once
            .verifyComplete();
    }
    
    @Test
    public void testWithPredicate() {
        Flux<Integer> source = Flux.range(1, 3);
        
        StepVerifier.create(source)
            .expectNextMatches(i -> i > 0)  // Custom predicate
            .expectNextMatches(i -> i == 2)
            .expectNextMatches(i -> i < 10)
            .verifyComplete();
    }
}
```

### Advanced Expectation Methods

```java
public class AdvancedStepVerifierTests {
    
    @Test
    public void testExpectNextCount() {
        Flux<Integer> source = Flux.range(1, 100);
        
        StepVerifier.create(source)
            .expectNextCount(100)  // Don't care about values, just count
            .verifyComplete();
    }
    
    @Test
    public void testExpectNextSequence() {
        Flux<String> source = Flux.just("a", "b", "c", "d");
        
        StepVerifier.create(source)
            .expectNextSequence(Arrays.asList("a", "b", "c", "d"))
            .verifyComplete();
    }
    
    @Test
    public void testThenConsumeWhile() {
        Flux<Integer> source = Flux.range(1, 10);
        
        StepVerifier.create(source)
            .expectNext(1, 2, 3)
            .thenConsumeWhile(i -> i < 8)  // Consume 4, 5, 6, 7
            .expectNext(8, 9, 10)
            .verifyComplete();
    }
    
    @Test
    public void testExpectRecordedMatches() {
        Flux<String> source = Flux.just("apple", "banana", "cherry");
        
        StepVerifier.create(source)
            .recordWith(ArrayList::new)  // Start recording
            .expectNextCount(3)
            .expectRecordedMatches(recorded -> {
                return recorded.size() == 3 &&
                       recorded.contains("apple") &&
                       recorded.stream().allMatch(s -> s.length() > 4);
            })
            .verifyComplete();
    }
    
    @Test
    public void testConditionalExpectations() {
        Flux<Integer> source = Flux.range(1, 5)
            .filter(i -> i % 2 == 0);  // Only even numbers
        
        StepVerifier.create(source)
            .expectNext(2)
            .expectNext(4)
            .verifyComplete();
    }
}
```

## 6.2 Time-Based Testing

### Virtual Time Testing

One of the most powerful features of StepVerifier is virtual time testing, which allows you to test time-based operations without waiting.

```java
public class TimeBasedTests {
    
    @Test
    public void testDelayWithVirtualTime() {
        StepVerifier.withVirtualTime(() -> 
            Mono.just("hello").delayElement(Duration.ofDays(1))
        )
        .expectSubscription()
        .expectNoEvent(Duration.ofDays(1))  // Nothing should happen for 1 day
        .expectNext("hello")
        .verifyComplete();
    }
    
    @Test
    public void testIntervalWithVirtualTime() {
        StepVerifier.withVirtualTime(() ->
            Flux.interval(Duration.ofMinutes(1))
                .take(3)
        )
        .expectSubscription()
        .expectNoEvent(Duration.ofMinutes(1))
        .expectNext(0L)
        .thenAwait(Duration.ofMinutes(1))
        .expectNext(1L)
        .thenAwait(Duration.ofMinutes(1))
        .expectNext(2L)
        .verifyComplete();
    }
    
    @Test
    public void testTimeoutWithVirtualTime() {
        StepVerifier.withVirtualTime(() ->
            Mono.never()
                .timeout(Duration.ofSeconds(10))
        )
        .expectSubscription()
        .expectNoEvent(Duration.ofSeconds(10))
        .expectError(TimeoutException.class)
        .verify();
    }
    
    @Test
    public void testComplexTimingScenario() {
        StepVerifier.withVirtualTime(() ->
            Flux.interval(Duration.ofSeconds(1))
                .take(5)
                .concatWith(Flux.just(100L, 200L).delayElements(Duration.ofSeconds(2)))
        )
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(1))
        .expectNext(0L)
        .thenAwait(Duration.ofSeconds(1))
        .expectNext(1L)
        .thenAwait(Duration.ofSeconds(1))
        .expectNext(2L)
        .thenAwait(Duration.ofSeconds(1))
        .expectNext(3L)
        .thenAwait(Duration.ofSeconds(1))
        .expectNext(4L)
        .thenAwait(Duration.ofSeconds(2))
        .expectNext(100L)
        .thenAwait(Duration.ofSeconds(2))
        .expectNext(200L)
        .verifyComplete();
    }
}
```

### Real-Time vs Virtual Time

```java
public class TimeComparisonTests {
    
    @Test
    public void realTimeTest() {
        // This test takes actual time to run
        long startTime = System.currentTimeMillis();
        
        StepVerifier.create(
            Mono.just("delayed").delayElement(Duration.ofMillis(100))
        )
        .expectNext("delayed")
        .verifyComplete();
        
        long endTime = System.currentTimeMillis();
        assertTrue(endTime - startTime >= 100);  // Actually waited
    }
    
    @Test
    public void virtualTimeTest() {
        // This test runs instantly
        long startTime = System.currentTimeMillis();
        
        StepVerifier.withVirtualTime(() ->
            Mono.just("delayed").delayElement(Duration.ofHours(1))
        )
        .expectSubscription()
        .expectNoEvent(Duration.ofHours(1))
        .expectNext("delayed")
        .verifyComplete();
        
        long endTime = System.currentTimeMillis();
        assertTrue(endTime - startTime < 1000);  // Ran almost instantly
    }
}
```

## 6.3 Error Testing

### Testing Different Error Scenarios

```java
public class ErrorTestingExamples {
    
    @Test
    public void testSimpleError() {
        Flux<String> errorFlux = Flux.error(new RuntimeException("Test error"));
        
        StepVerifier.create(errorFlux)
            .expectError(RuntimeException.class)
            .verify();
    }
    
    @Test
    public void testErrorWithMessage() {
        Mono<String> errorMono = Mono.error(new IllegalArgumentException("Invalid input"));
        
        StepVerifier.create(errorMono)
            .expectErrorMatches(throwable ->
                throwable instanceof IllegalArgumentException &&
                throwable.getMessage().equals("Invalid input")
            )
            .verify();
    }
    
    @Test
    public void testErrorAfterValues() {
        Flux<Integer> source = Flux.just(1, 2, 3)
            .concatWith(Flux.error(new RuntimeException("Failed after values")));
        
        StepVerifier.create(source)
            .expectNext(1, 2, 3)
            .expectError(RuntimeException.class)
            .verify();
    }
    
    @Test
    public void testErrorRecovery() {
        Flux<String> source = Flux.just("a", "b")
            .concatWith(Flux.error(new RuntimeException("Error")))
            .onErrorReturn("recovered");
        
        StepVerifier.create(source)
            .expectNext("a", "b", "recovered")
            .verifyComplete();
    }
    
    @Test
    public void testRetryScenario() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        Mono<String> source = Mono.fromCallable(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Attempt " + attempt + " failed");
            }
            return "Success on attempt " + attempt;
        })
        .retry(2);
        
        StepVerifier.create(source)
            .expectNext("Success on attempt 3")
            .verifyComplete();
        
        assertEquals(3, attempts.get());
    }
}
```

## 6.4 Backpressure Testing

### Testing Backpressure Scenarios from Phase 5

```java
public class BackpressureTestingExamples {
    
    @Test
    public void testBufferBackpressure() {
        Flux<Integer> source = Flux.range(1, 10)
            .onBackpressureBuffer(3);
        
        StepVerifier.create(source, 0)  // Start with 0 demand
            .expectSubscription()
            .thenRequest(2)
            .expectNext(1, 2)
            .thenRequest(3)
            .expectNext(3, 4, 5)
            .thenRequest(10)  // Request more than remaining
            .expectNext(6, 7, 8, 9, 10)
            .verifyComplete();
    }
    
    @Test
    public void testDropBackpressure() {
        AtomicInteger droppedCount = new AtomicInteger(0);
        
        Flux<Integer> source = Flux.range(1, 100)
            .onBackpressureDrop(item -> droppedCount.incrementAndGet());
        
        StepVerifier.create(source, 5)  // Limited demand
            .expectNextCount(5)
            .thenCancel()
            .verify();
        
        // Some items should have been dropped
        assertTrue(droppedCount.get() > 0);
    }
    
    @Test
    public void testLatestBackpressure() {
        Flux<Integer> source = Flux.range(1, 10)
            .onBackpressureLatest();
        
        StepVerifier.create(source, 1)  // Very limited demand
            .expectNext(1)
            .thenAwait(Duration.ofMillis(100))  // Let items accumulate
            .thenRequest(1)
            .expectNext(10)  // Should get the latest item
            .verifyComplete();
    }
    
    @Test
    public void testErrorBackpressure() {
        Flux<Integer> source = Flux.range(1, 1000)
            .onBackpressureError();
        
        StepVerifier.create(source, 10)  // Limited demand
            .expectNextCount(10)
            .expectError(IllegalStateException.class)  // Backpressure error
            .verify();
    }
}
```

## 6.5 TestPublisher - Creating Test Data Sources

### Basic TestPublisher Usage

```java
public class TestPublisherExamples {
    
    @Test
    public void testBasicTestPublisher() {
        TestPublisher<String> testPublisher = TestPublisher.create();
        
        StepVerifier.create(testPublisher.flux())
            .then(() -> testPublisher.emit("first"))
            .expectNext("first")
            .then(() -> testPublisher.emit("second"))
            .expectNext("second")
            .then(() -> testPublisher.complete())
            .verifyComplete();
    }
    
    @Test
    public void testTestPublisherWithError() {
        TestPublisher<String> testPublisher = TestPublisher.create();
        
        StepVerifier.create(testPublisher.flux())
            .then(() -> testPublisher.emit("value1", "value2"))
            .expectNext("value1", "value2")
            .then(() -> testPublisher.error(new RuntimeException("Test error")))
            .expectError(RuntimeException.class)
            .verify();
    }
    
    @Test
    public void testColdTestPublisher() {
        TestPublisher<Integer> testPublisher = TestPublisher.createCold();
        
        // Each subscriber gets the full sequence
        Flux<Integer> flux = testPublisher.flux();
        
        StepVerifier.create(flux)
            .then(() -> testPublisher.emit(1, 2, 3))
            .expectNext(1, 2, 3)
            .then(() -> testPublisher.complete())
            .verifyComplete();
        
        // Second subscriber also gets full sequence
        StepVerifier.create(flux)
            .expectNext(1, 2, 3)
            .verifyComplete();
    }
    
    @Test
    public void testNonCompliantTestPublisher() {
        // Create a publisher that can violate reactive streams spec for testing
        TestPublisher<String> testPublisher = TestPublisher.createNoncompliant(
            TestPublisher.Violation.ALLOW_NULL
        );
        
        StepVerisher.create(testPublisher.flux())
            .then(() -> testPublisher.emit("valid", null, "another"))
            .expectNext("valid")
            .expectNext((String) null)  // Normally not allowed
            .expectNext("another")
            .then(() -> testPublisher.complete())
            .verifyComplete();
    }
}
```

### Advanced TestPublisher Scenarios

```java
public class AdvancedTestPublisherExamples {
    
    @Test
    public void testBackpressureWithTestPublisher() {
        TestPublisher<Integer> testPublisher = TestPublisher.create();
        
        StepVerifier.create(testPublisher.flux(), 0)  // No initial demand
            .expectSubscription()
            .then(() -> {
                // Try to emit without demand - should be buffered
                testPublisher.emit(1, 2, 3);
            })
            .thenRequest(2)
            .expectNext(1, 2)
            .thenRequest(1)
            .expectNext(3)
            .then(() -> testPublisher.complete())
            .verifyComplete();
    }
    
    @Test
    public void testConditionalEmission() {
        TestPublisher<String> testPublisher = TestPublisher.create();
        AtomicBoolean shouldEmit = new AtomicBoolean(false);
        
        StepVerifier.create(testPublisher.flux())
            .then(() -> {
                if (shouldEmit.get()) {
                    testPublisher.emit("conditional");
                }
            })
            .expectNoEvent(Duration.ofMillis(100))  // Nothing emitted yet
            .then(() -> {
                shouldEmit.set(true);
                testPublisher.emit("now emitted");
            })
            .expectNext("now emitted")
            .then(() -> testPublisher.complete())
            .verifyComplete();
    }
    
    @Test
    public void testMultipleSubscribers() {
        TestPublisher<Integer> testPublisher = TestPublisher.create();
        Flux<Integer> flux = testPublisher.flux().share();  // Hot flux
        
        // First subscriber
        StepVerifier subscriber1 = StepVerifier.create(flux)
            .expectNext(1, 2)
            .expectComplete();
        
        // Second subscriber
        StepVerifier subscriber2 = StepVerifier.create(flux)
            .expectNext(1, 2)
            .expectComplete();
        
        // Emit to both subscribers
        testPublisher.emit(1, 2);
        testPublisher.complete();
        
        // Verify both
        subscriber1.verify();
        subscriber2.verify();
    }
}
```

## 6.6 Integration Testing Patterns

### Testing Service Layers

```java
// Service class to test
@Service
public class ReactiveUserService {
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    
    public ReactiveUserService(UserRepository userRepository, 
                              NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }
    
    public Mono<User> createUser(User user) {
        return userRepository.save(user)
            .flatMap(savedUser -> 
                notificationService.sendWelcomeEmail(savedUser.getEmail())
                    .thenReturn(savedUser)
            )
            .onErrorMap(DataIntegrityViolationException.class, 
                       ex -> new UserAlreadyExistsException("User already exists"));
    }
    
    public Flux<User> findAllUsers() {
        return userRepository.findAll()
            .timeout(Duration.ofSeconds(5))
            .onErrorReturn(new User("error", "Error loading users"));
    }
}

// Test class
@ExtendWith(MockitoExtension.class)
public class ReactiveUserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private NotificationService notificationService;
    
    @InjectMocks
    private ReactiveUserService userService;
    
    @Test
    public void shouldCreateUserSuccessfully() {
        // Given
        User inputUser = new User("john", "john@example.com");
        User savedUser = new User("john", "john@example.com");
        savedUser.setId(1L);
        
        when(userRepository.save(inputUser)).thenReturn(Mono.just(savedUser));
        when(notificationService.sendWelcomeEmail("john@example.com"))
            .thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(userService.createUser(inputUser))
            .expectNext(savedUser)
            .verifyComplete();
        
        verify(userRepository).save(inputUser);
        verify(notificationService).sendWelcomeEmail("john@example.com");
    }
    
    @Test
    public void shouldHandleUserAlreadyExistsError() {
        // Given
        User inputUser = new User("john", "john@example.com");
        when(userRepository.save(inputUser))
            .thenReturn(Mono.error(new DataIntegrityViolationException("Duplicate")));
        
        // When & Then
        StepVerifier.create(userService.createUser(inputUser))
            .expectError(UserAlreadyExistsException.class)
            .verify();
    }
    
    @Test
    public void shouldHandleTimeoutInFindAll() {
        // Given
        when(userRepository.findAll())
            .thenReturn(Flux.never());  // Never emits, causes timeout
        
        // When & Then
        StepVerifier.create(userService.findAllUsers())
            .expectNext(new User("error", "Error loading users"))
            .verifyComplete();
    }
    
    @Test
    public void shouldPropagateNotificationFailure() {
        // Given
        User inputUser = new User("john", "john@example.com");
        User savedUser = new User("john", "john@example.com");
        savedUser.setId(1L);
        
        when(userRepository.save(inputUser)).thenReturn(Mono.just(savedUser));
        when(notificationService.sendWelcomeEmail("john@example.com"))
            .thenReturn(Mono.error(new RuntimeException("Email service down")));
        
        // When & Then
        StepVerifier.create(userService.createUser(inputUser))
            .expectError(RuntimeException.class)
            .verify();
    }
}
```

### Testing WebFlux Controllers

```java
@WebFluxTest(UserController.class)
public class UserControllerTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    @MockBean
    private ReactiveUserService userService;
    
    @Test
    public void shouldCreateUser() {
        // Given
        User inputUser = new User("john", "john@example.com");
        User savedUser = new User("john", "john@example.com");
        savedUser.setId(1L);
        
        when(userService.createUser(any(User.class)))
            .thenReturn(Mono.just(savedUser));
        
        // When & Then
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Mono.just(inputUser), User.class)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(User.class)
            .value(user -> {
                assertEquals("john", user.getName());
                assertEquals("john@example.com", user.getEmail());
                assertNotNull(user.getId());
            });
    }
    
    @Test
    public void shouldHandleValidationError() {
        // Given
        User invalidUser = new User("", "invalid-email");
        
        // When & Then
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Mono.just(invalidUser), User.class)
            .exchange()
            .expectStatus().isBadRequest();
    }
    
    @Test
    public void shouldStreamUsers() {
        // Given
        Flux<User> userStream = Flux.just(
            new User("alice", "alice@example.com"),
            new User("bob", "bob@example.com")
        ).delayElements(Duration.ofMillis(100));
        
        when(userService.findAllUsers()).thenReturn(userStream);
        
        // When & Then
        webTestClient.get()
            .uri("/api/users/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectBodyList(User.class)
            .hasSize(2)
            .contains(new User("alice", "alice@example.com"))
            .contains(new User("bob", "bob@example.com"));
    }
}
```

## 6.7 Performance and Load Testing

### Testing Performance Characteristics

```java
public class PerformanceTests {
    
    @Test
    public void testThroughput() {
        int itemCount = 1_000_000;
        
        Flux<Integer> source = Flux.range(1, itemCount)
            .map(i -> i * 2)
            .filter(i -> i % 3 == 0)
            .publishOn(Schedulers.parallel());
        
        long startTime = System.currentTimeMillis();
        
        StepVerifier.create(source)
            .expectNextCount(itemCount / 3)  // Every 3rd item passes filter
            .verifyComplete();
        
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Processed " + itemCount + " items in " + duration + "ms");
        
        // Assert reasonable performance
        assertTrue(duration < 5000, "Processing took too long: " + duration + "ms");
    }
    
    @Test
    public void testMemoryUsage() {
        // Test that buffering doesn't cause memory issues
        Flux<byte[]> source = Flux.range(1, 1000)
            .map(i -> new byte[1024])  // 1KB per item
            .onBackpressureBuffer(100)  // Limited buffer
            .publishOn(Schedulers.boundedElastic());
        
        StepVerifier.create(source)
            .thenConsumeWhile(bytes -> {
                // Simulate slow processing
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                return true;
            })
            .verifyComplete();
        
        // If we reach here without OutOfMemoryError, test passes
        assertTrue(true);
    }
    
    @Test
    public void testConcurrency() {
        int concurrentStreams = 10;
        int itemsPerStream = 1000;
        
        List<Flux<Integer>> streams = IntStream.range(0, concurrentStreams)
            .mapToObj(streamId -> 
                Flux.range(streamId * itemsPerStream, itemsPerStream)
                    .subscribeOn(Schedulers.parallel())
                    .map(i -> i * 2)
            )
            .collect(Collectors.toList());
        
        Flux<Integer> merged = Flux.merge(streams);
        
        StepVerifier.create(merged)
            .expectNextCount(concurrentStreams * itemsPerStream)
            .verifyComplete();
    }
}
```

## 6.8 Common Testing Patterns and Best Practices

### Parameterized Tests for Reactive Streams

```java
public class ParameterizedReactiveTests {
    
    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1000})
    public void testFluxWithDifferentSizes(int size) {
        Flux<Integer> source = Flux.range(1, size);
        
        StepVerifier.create(source)
            .expectNextCount(size)
            .verifyComplete();
    }
    
    @ParameterizedTest
    @EnumSource(BackpressureStrategy.class)
    public void testDifferentBackpressureStrategies(BackpressureStrategy strategy) {
        Flux<Integer> source = Flux.range(1, 100);
        
        Flux<Integer> processedSource = switch (strategy) {
            case BUFFER -> source.onBackpressureBuffer(50);
            case DROP -> source.onBackpressureDrop();
            case LATEST -> source.onBackpressureLatest();
            case ERROR -> source.onBackpressureError();
        };
        
        // Test each strategy appropriately
        StepVerifier.StepVerifierOptions options = StepVerifier.StepVerifierOptions
            .create()
            .initialRequest(10);
            
        if (strategy == BackpressureStrategy.ERROR) {
            StepVerifier.create(processedSource, options)
                .expectNextCount(10)
                .expectError()
                .verify();
        } else {
            StepVerifier.create(processedSource, options)
                .thenRequest(Long.MAX_VALUE)
                .expectNextCount(100)
                .verifyComplete();
        }
    }
    
    enum BackpressureStrategy {
        BUFFER, DROP, LATEST, ERROR
    }
}
```

### Testing Custom Operators

```java
public class CustomOperatorTests {
    
    // Custom operator from Phase 9
    public static <T> Function<Flux<T>, Flux<T>> logOnNext(String prefix) {
        return flux -> flux.doOnNext(item -> 
            System.out.println(prefix + ": " + item));
    }
    
    @Test
    public void testCustomLogOperator() {
        Flux<String> source = Flux.just("a", "b", "c")
            .transform(logOnNext("TEST"));
        
        StepVerifier.create(source)
            .expectNext("a", "b", "c")
            .verifyComplete();
    }
    
    // Custom retry operator with exponential backoff
    public static <T> Function<Flux<T>, Flux<T>> retryWithBackoff(int maxRetries) {
        return flux -> flux.retryWhen(
            Retry.backoff(maxRetries, Duration.ofMillis(100))
                 .maxBackoff(Duration.ofSeconds(1))
        );
    }
    
    @Test
    public void testCustomRetryOperator() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        Flux<String> source = Flux.fromCallable(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Attempt " + attempt);
            }
            return "Success";
        })
        .transform(retryWithBackoff(5));
        
        StepVerifier.withVirtualTime(() -> source)
            .expectSubscription()
            .thenAwait(Duration.ofMillis(300))  // Account for backoff delays
            .expectNext("Success")
            .verifyComplete();
            
        assertEquals(3, attempts.get());
    }
}
```

## Key Testing Principles

1. **Test Behavior, Not Implementation**: Focus on what your streams emit, when they complete, and how they handle errors

2. **Use Virtual Time**: For time-based operations, always use `withVirtualTime()` to make tests fast and deterministic

3. **Test Edge Cases**: Empty streams, single items, errors, cancellation scenarios

4. **Verify Side Effects**: Use `verify()` on mocks to ensure side effects happened as expected

5. **Test Backpressure**: Explicitly test different demand scenarios and backpressure strategies

6. **Separate Unit and Integration Tests**: Use `TestPublisher` for unit tests, `WebTestClient` for integration tests

Key highlights of Phase 6:

StepVerifier Mastery: Complete coverage from basic expectations to advanced patterns
Virtual Time Testing: Critical for testing time-based operations without waiting
Error Scenario Testing: Comprehensive error handling and recovery testing
Backpressure Testing: Building

Backpressure Testing: Building on your Phase 5 knowledge to properly test all backpressure strategies
TestPublisher: Creating controllable test data sources for complex scenarios
Integration Testing: Testing service layers and WebFlux controllers
Performance Testing: Load testing and memory usage validation
Custom Operator Testing: How to test your own reactive operators

Key Testing Patterns You've Learned:

Expectation Chaining: Building complex test scenarios step by step
Demand Control: Testing with specific request patterns to validate backpressure
Virtual Time: Testing hours/days of time-based operations in milliseconds
Error Scenarios: Comprehensive error testing including recovery patterns
Mock Integration: Properly testing reactive services with mocked dependencies

Best Practices Established:

Always use withVirtualTime() for time-based operations
Test both happy path and error scenarios
Explicitly test backpressure behavior with controlled demand
Use TestPublisher for complex emission scenarios
Separate unit tests (StepVerifier) from integration tests (WebTestClient)
Test performance characteristics and memory usage

## Next Steps

In **Phase 7**, we'll explore Hot vs Cold Publishers and the modern Sinks API, building on the testing knowledge you've gained here to properly test different publisher behaviors.