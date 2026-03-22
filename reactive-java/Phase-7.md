# Phase 7: Hot vs Cold Publishers - Complete Implementation Guide

## 7.1 Understanding Cold vs Hot Publishers

### Cold Publishers - The Default Behavior

Cold publishers are **lazy** and **unicast**. Each subscription gets its own independent execution of the data source.

#### Characteristics of Cold Publishers:
- **Lazy**: Nothing happens until subscription
- **Unicast**: Each subscriber gets its own stream
- **Reproducible**: Same sequence for every subscriber
- **Independent**: Subscribers don't affect each other

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ColdPublisherExamples {
    
    @Test
    public void demonstrateColdBehavior() {
        AtomicInteger counter = new AtomicInteger(0);
        
        // Cold publisher - creates new sequence for each subscriber
        Flux<Integer> coldFlux = Flux.range(1, 3)
            .doOnSubscribe(sub -> System.out.println("Subscribed! Counter: " + counter.incrementAndGet()))
            .map(i -> {
                System.out.println("Processing: " + i + " (Counter: " + counter.get() + ")");
                return i * 10;
            });
        
        System.out.println("=== First Subscriber ===");
        coldFlux.subscribe(value -> System.out.println("Subscriber 1: " + value));
        
        System.out.println("\n=== Second Subscriber ===");
        coldFlux.subscribe(value -> System.out.println("Subscriber 2: " + value));
        
        // Output shows counter increments for each subscription
        // Each subscriber gets independent execution
    }
    
    @Test
    public void coldPublisherWithDelay() {
        Flux<Long> coldInterval = Flux.interval(Duration.ofSeconds(1))
            .take(3)
            .doOnNext(i -> System.out.println("Emitted: " + i + " at " + System.currentTimeMillis()));
        
        System.out.println("Creating first subscriber...");
        coldInterval.subscribe(value -> 
            System.out.println("Subscriber 1 received: " + value + " at " + System.currentTimeMillis()));
        
        // Wait 5 seconds then add second subscriber
        try { Thread.sleep(5000); } catch (InterruptedException e) {}
        
        System.out.println("Creating second subscriber...");
        coldInterval.subscribe(value -> 
            System.out.println("Subscriber 2 received: " + value + " at " + System.currentTimeMillis()));
        
        // Second subscriber gets its own interval starting from 0
        try { Thread.sleep(5000); } catch (InterruptedException e) {}
    }
    
    @Test
    public void coldMonoExample() {
        AtomicInteger callCount = new AtomicInteger(0);
        
        Mono<String> coldMono = Mono.fromCallable(() -> {
            int count = callCount.incrementAndGet();
            System.out.println("Expensive computation executed: " + count);
            return "Result from call #" + count;
        });
        
        // Each subscription triggers the computation
        coldMono.subscribe(result -> System.out.println("Subscriber 1: " + result));
        coldMono.subscribe(result -> System.out.println("Subscriber 2: " + result));
        coldMono.subscribe(result -> System.out.println("Subscriber 3: " + result));
        
        // callCount will be 3 - computed for each subscriber
    }
}
```

### Hot Publishers - Shared Execution

Hot publishers are **eager** and **multicast**. They emit values regardless of subscribers and share the same stream.

#### Characteristics of Hot Publishers:
- **Eager**: Starts emitting immediately (or when connected)
- **Multicast**: All subscribers share the same stream
- **Live**: Late subscribers may miss early values
- **Shared**: All subscribers see the same emissions

```java
public class HotPublisherExamples {
    
    @Test
    public void demonstrateHotBehavior() {
        // Convert cold to hot using share()
        Flux<Long> hotFlux = Flux.interval(Duration.ofSeconds(1))
            .take(5)
            .doOnNext(i -> System.out.println("Emitted: " + i + " at " + System.currentTimeMillis()))
            .share(); // Makes it hot!
        
        System.out.println("Starting first subscriber...");
        hotFlux.subscribe(value -> 
            System.out.println("Subscriber 1: " + value + " at " + System.currentTimeMillis()));
        
        // Wait 3 seconds, then add second subscriber
        try { Thread.sleep(3000); } catch (InterruptedException e) {}
        
        System.out.println("Starting second subscriber (late)...");
        hotFlux.subscribe(value -> 
            System.out.println("Subscriber 2: " + value + " at " + System.currentTimeMillis()));
        
        // Second subscriber will miss values 0, 1, 2 and only get 3, 4
        try { Thread.sleep(3000); } catch (InterruptedException e) {}
    }
    
    @Test
    public void publishExample() {
        ConnectableFlux<Long> connectableFlux = Flux.interval(Duration.ofMillis(500))
            .take(5)
            .doOnNext(i -> System.out.println("Source emitted: " + i))
            .publish(); // Returns ConnectableFlux
        
        // Subscribe but nothing happens yet
        connectableFlux.subscribe(value -> System.out.println("Subscriber 1: " + value));
        connectableFlux.subscribe(value -> System.out.println("Subscriber 2: " + value));
        
        System.out.println("Subscribers added, but no emissions yet...");
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        System.out.println("Connecting now...");
        connectableFlux.connect(); // Start emitting to all subscribers
        
        try { Thread.sleep(3000); } catch (InterruptedException e) {}
    }
    
    @Test
    public void replayExample() {
        // Replay last 2 items to new subscribers
        Flux<Integer> replayFlux = Flux.range(1, 5)
            .delayElements(Duration.ofSeconds(1))
            .doOnNext(i -> System.out.println("Emitted: " + i))
            .replay(2) // Buffer last 2 items
            .autoConnect(); // Auto-connect on first subscription
        
        // First subscriber gets all items
        replayFlux.subscribe(value -> System.out.println("Early subscriber: " + value));
        
        try { Thread.sleep(3000); } catch (InterruptedException e) {}
        
        // Late subscriber gets replayed items (4, 5) plus any new ones
        replayFlux.subscribe(value -> System.out.println("Late subscriber: " + value));
        
        try { Thread.sleep(3000); } catch (InterruptedException e) {}
    }
}
```

## 7.2 ConnectableFlux - Controlled Hot Publishers

ConnectableFlux provides fine-grained control over when hot publishers start emitting.

```java
public class ConnectableFluxExamples {
    
    @Test
    public void basicConnectableFlux() {
        ConnectableFlux<String> connectableFlux = Flux.just("A", "B", "C", "D")
            .delayElements(Duration.ofSeconds(1))
            .doOnNext(item -> System.out.println("Emitting: " + item))
            .publish();
        
        // Add subscribers before connecting
        connectableFlux.subscribe(item -> System.out.println("Subscriber 1: " + item));
        
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        connectableFlux.subscribe(item -> System.out.println("Subscriber 2: " + item));
        
        // Both subscribers are ready, now connect to start emission
        Disposable connection = connectableFlux.connect();
        
        try { Thread.sleep(6000); } catch (InterruptedException e) {}
        
        // Can disconnect to stop emission
        connection.dispose();
        System.out.println("Disconnected");
    }
    
    @Test
    public void autoConnectExample() {
        Flux<String> autoConnectFlux = Flux.just("X", "Y", "Z")
            .delayElements(Duration.ofMillis(500))
            .doOnNext(item -> System.out.println("Auto-emitting: " + item))
            .publish()
            .autoConnect(2); // Auto-connect when 2 subscribers
        
        System.out.println("Adding first subscriber...");
        autoConnectFlux.subscribe(item -> System.out.println("Subscriber 1: " + item));
        
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        System.out.println("Still no emission...");
        
        System.out.println("Adding second subscriber...");
        autoConnectFlux.subscribe(item -> System.out.println("Subscriber 2: " + item));
        
        // Now it auto-connects and starts emitting
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
    }
    
    @Test
    public void refCountExample() {
        Flux<Long> refCountFlux = Flux.interval(Duration.ofMillis(300))
            .doOnNext(i -> System.out.println("Source: " + i))
            .publish()
            .refCount(2); // Connect when 2 subscribers, disconnect when < 2
        
        System.out.println("Adding subscriber 1...");
        Disposable sub1 = refCountFlux.subscribe(i -> System.out.println("Sub1: " + i));
        
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        System.out.println("Adding subscriber 2... (should start emission)");
        Disposable sub2 = refCountFlux.subscribe(i -> System.out.println("Sub2: " + i));
        
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        System.out.println("Disposing subscriber 1... (should continue)");
        sub1.dispose();
        
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        System.out.println("Disposing subscriber 2... (should stop emission)");
        sub2.dispose();
        
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        System.out.println("All subscribers gone, emission stopped");
    }
}
```

## 7.3 Sinks - The Modern Approach

Sinks are the modern replacement for Processors (which are deprecated). They provide a safe way to programmatically emit values.

### Sinks.Many - Multiple Values

```java
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

public class SinksManyExamples {
    
    @Test
    public void basicMulticastSink() {
        // Creates a hot publisher that multicasts to all subscribers
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        
        Flux<String> flux = sink.asFlux();
        
        // Add subscribers
        flux.subscribe(item -> System.out.println("Subscriber 1: " + item));
        flux.subscribe(item -> System.out.println("Subscriber 2: " + item));
        
        // Emit values
        sink.tryEmitNext("Hello");
        sink.tryEmitNext("World");
        sink.tryEmitNext("!");
        sink.tryEmitComplete();
        
        // Both subscribers receive all values
    }
    
    @Test
    public void unicastSink() {
        // Only supports one subscriber
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        
        Flux<String> flux = sink.asFlux();
        
        // First subscriber works fine
        flux.subscribe(item -> System.out.println("Subscriber 1: " + item));
        
        // Second subscriber will cause an error
        try {
            flux.subscribe(item -> System.out.println("Subscriber 2: " + item));
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
        
        sink.tryEmitNext("Only for first subscriber");
        sink.tryEmitComplete();
    }
    
    @Test
    public void replaySink() {
        // Replay all previous values to new subscribers
        Sinks.Many<String> sink = Sinks.many().replay().all();
        
        Flux<String> flux = sink.asFlux();
        
        // Emit some values before any subscribers
        sink.tryEmitNext("Early 1");
        sink.tryEmitNext("Early 2");
        
        // First subscriber gets replayed values
        flux.subscribe(item -> System.out.println("Subscriber 1: " + item));
        
        // Emit more values
        sink.tryEmitNext("Live 1");
        sink.tryEmitNext("Live 2");
        
        // Second subscriber gets ALL previous values plus live ones
        flux.subscribe(item -> System.out.println("Subscriber 2: " + item));
        
        sink.tryEmitNext("Final");
        sink.tryEmitComplete();
    }
    
    @Test
    public void limitedReplaySink() {
        // Replay only last N items
        Sinks.Many<Integer> sink = Sinks.many().replay().limit(3);
        
        Flux<Integer> flux = sink.asFlux();
        
        // Emit many values
        for (int i = 1; i <= 10; i++) {
            sink.tryEmitNext(i);
        }
        
        // Late subscriber only gets last 3 values (8, 9, 10)
        flux.subscribe(item -> System.out.println("Late subscriber: " + item));
        
        // Emit more
        sink.tryEmitNext(11);
        sink.tryEmitNext(12);
        
        // Another late subscriber gets last 3: (10, 11, 12)
        flux.subscribe(item -> System.out.println("Very late subscriber: " + item));
        
        sink.tryEmitComplete();
    }
    
    @Test
    public void timeBasedReplaySink() {
        // Replay items within time window
        Sinks.Many<String> sink = Sinks.many().replay().limit(Duration.ofSeconds(2));
        
        Flux<String> flux = sink.asFlux();
        
        sink.tryEmitNext("Old message");
        
        try { Thread.sleep(3000); } catch (InterruptedException e) {}
        
        sink.tryEmitNext("Recent message 1");
        sink.tryEmitNext("Recent message 2");
        
        // Subscriber only gets messages from last 2 seconds
        flux.subscribe(item -> System.out.println("Time-based subscriber: " + item));
        
        sink.tryEmitComplete();
    }
}
```

### Sinks.One - Single Value

```java
public class SinksOneExamples {
    
    @Test
    public void basicSinksOne() {
        Sinks.One<String> sink = Sinks.one();
        
        Mono<String> mono = sink.asMono();
        
        // Add subscribers before emission
        mono.subscribe(value -> System.out.println("Subscriber 1: " + value));
        mono.subscribe(value -> System.out.println("Subscriber 2: " + value));
        
        // Emit value - all subscribers receive it
        Sinks.EmitResult result = sink.tryEmitValue("Hello World");
        System.out.println("Emit result: " + result);
        
        // Late subscribers also get the value
        mono.subscribe(value -> System.out.println("Late subscriber: " + value));
    }
    
    @Test
    public void sinksOneWithError() {
        Sinks.One<String> sink = Sinks.one();
        
        Mono<String> mono = sink.asMono();
        
        mono.subscribe(
            value -> System.out.println("Success: " + value),
            error -> System.err.println("Error: " + error.getMessage())
        );
        
        // Emit error instead of value
        sink.tryEmitError(new RuntimeException("Something went wrong"));
    }
    
    @Test
    public void sinksOneEmitOnce() {
        Sinks.One<String> sink = Sinks.one();
        
        // First emit succeeds
        Sinks.EmitResult result1 = sink.tryEmitValue("First");
        System.out.println("First emit: " + result1);
        
        // Second emit fails - can only emit once
        Sinks.EmitResult result2 = sink.tryEmitValue("Second");
        System.out.println("Second emit: " + result2);
        
        sink.asMono().subscribe(value -> System.out.println("Received: " + value));
    }
    
    @Test
    public void sinksOneWithRetry() {
        Sinks.One<String> sink = Sinks.one();
        
        // Use emitValue for retry logic
        try {
            sink.emitValue("Hello", Sinks.EmitFailureHandler.FAIL_FAST);
            System.out.println("Successfully emitted");
        } catch (Exception e) {
            System.err.println("Failed to emit: " + e.getMessage());
        }
        
        sink.asMono().subscribe(value -> System.out.println("Received: " + value));
    }
}
```

## 7.4 Advanced Sink Patterns

### Thread-Safe Emission with Error Handling

```java
public class AdvancedSinkPatterns {
    
    @Test
    public void threadSafeEmission() {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        Flux<String> flux = sink.asFlux();
        
        flux.subscribe(item -> System.out.println("Received: " + item + " on " + Thread.currentThread().getName()));
        
        // Multiple threads emitting
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        for (int i = 0; i < 3; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < 5; j++) {
                    Sinks.EmitResult result = sink.tryEmitNext("Thread-" + threadId + "-Item-" + j);
                    if (result != Sinks.EmitResult.OK) {
                        System.err.println("Failed to emit: " + result);
                    }
                    try { Thread.sleep(100); } catch (InterruptedException e) {}
                }
            });
        }
        
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        sink.tryEmitComplete();
        executor.shutdown();
    }
    
    @Test
    public void emitWithRetryHandler() {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE);
        
        // Custom retry handler
        Sinks.EmitFailureHandler retryHandler = (signalType, emitResult) -> {
            if (emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                System.out.println("Retrying emission due to serialization failure");
                return true; // Retry
            }
            return false; // Don't retry
        };
        
        Flux<String> flux = sink.asFlux();
        flux.subscribe(item -> {
            // Slow subscriber
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            System.out.println("Processed: " + item);
        });
        
        // Fast emission with retry handling
        for (int i = 0; i < 10; i++) {
            try {
                sink.emitNext("Item " + i, retryHandler);
            } catch (Exception e) {
                System.err.println("Failed to emit after retries: " + e.getMessage());
            }
        }
        
        sink.tryEmitComplete();
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
    }
}
```

### Event Bus Pattern with Sinks

```java
public class EventBusExample {
    
    static class EventBus {
        private final Sinks.Many<Object> sink;
        private final Flux<Object> eventStream;
        
        public EventBus() {
            this.sink = Sinks.many().multicast().onBackpressureBuffer();
            this.eventStream = sink.asFlux().share();
        }
        
        public void publish(Object event) {
            sink.tryEmitNext(event);
        }
        
        public <T> Flux<T> subscribe(Class<T> eventType) {
            return eventStream
                .filter(eventType::isInstance)
                .cast(eventType);
        }
        
        public void shutdown() {
            sink.tryEmitComplete();
        }
    }
    
    static class OrderCreated {
        final String orderId;
        OrderCreated(String orderId) { this.orderId = orderId; }
        public String toString() { return "OrderCreated: " + orderId; }
    }
    
    static class PaymentProcessed {
        final String paymentId;
        PaymentProcessed(String paymentId) { this.paymentId = paymentId; }
        public String toString() { return "PaymentProcessed: " + paymentId; }
    }
    
    @Test
    public void eventBusExample() {
        EventBus eventBus = new EventBus();
        
        // Subscribe to specific event types
        eventBus.subscribe(OrderCreated.class)
                .subscribe(event -> System.out.println("Order handler: " + event));
        
        eventBus.subscribe(PaymentProcessed.class)
                .subscribe(event -> System.out.println("Payment handler: " + event));
        
        // Subscribe to all events
        eventBus.eventStream
                .subscribe(event -> System.out.println("All events logger: " + event));
        
        // Publish events
        eventBus.publish(new OrderCreated("ORDER-123"));
        eventBus.publish(new PaymentProcessed("PAY-456"));
        eventBus.publish(new OrderCreated("ORDER-789"));
        eventBus.publish("Unknown event type");
        
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        eventBus.shutdown();
    }
}
```

## 7.5 Testing Hot vs Cold Publishers

### Testing Cold Publishers

```java
public class ColdPublisherTests {
    
    @Test
    public void testColdBehaviorWithStepVerifier() {
        AtomicInteger executionCount = new AtomicInteger(0);
        
        Flux<String> coldFlux = Flux.fromCallable(() -> {
            executionCount.incrementAndGet();
            return "computed";
        })
        .repeat(2); // Will be "computed" 3 times total
        
        // First subscription
        StepVerifier.create(coldFlux)
            .expectNext("computed", "computed", "computed")
            .verifyComplete();
        
        // Second subscription - should execute again
        StepVerifier.create(coldFlux)
            .expectNext("computed", "computed", "computed")
            .verifyComplete();
        
        // Should have executed 6 times total (2 subscriptions × 3 emissions each)
        assertEquals(6, executionCount.get());
    }
    
    @Test
    public void testColdPublisherIndependence() {
        Flux<Long> coldInterval = Flux.interval(Duration.ofMillis(100))
            .take(3);
        
        // Both subscribers get independent intervals
        StepVerifier.withVirtualTime(() -> coldInterval)
            .expectSubscription()
            .thenAwait(Duration.ofMillis(100))
            .expectNext(0L)
            .thenAwait(Duration.ofMillis(100))
            .expectNext(1L)
            .thenAwait(Duration.ofMillis(100))
            .expectNext(2L)
            .verifyComplete();
    }
}
```

### Testing Hot Publishers

```java
public class HotPublisherTests {
    
    @Test
    public void testShareBehavior() {
        AtomicInteger executionCount = new AtomicInteger(0);
        
        Flux<String> hotFlux = Flux.fromCallable(() -> {
            executionCount.incrementAndGet();
            return "shared";
        })
        .repeat(2)
        .share(); // Make it hot
        
        // Multiple subscribers share the same execution
        StepVerifier.create(hotFlux)
            .expectNext("shared", "shared", "shared")
            .verifyComplete();
        
        // Should have executed only once regardless of subscribers
        assertEquals(3, executionCount.get());
    }
    
    @Test
    public void testConnectableFluxWithTesting() {
        ConnectableFlux<Integer> connectableFlux = Flux.range(1, 3)
            .publish();
        
        // Create step verifier but don't start yet
        StepVerifier.Step<Integer> step = StepVerifier.create(connectableFlux)
            .expectNext(1, 2, 3)
            .expectComplete();
        
        // Connect to start emission
        connectableFlux.connect();
        
        // Now verify
        step.verify();
    }
    
    @Test
    public void testSinkEmission() {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        
        StepVerifier.create(sink.asFlux())
            .then(() -> sink.tryEmitNext("first"))
            .expectNext("first")
            .then(() -> sink.tryEmitNext("second"))
            .expectNext("second")
            .then(() -> sink.tryEmitComplete())
            .verifyComplete();
    }
    
    @Test
    public void testLateSubscriberMissesValues() {
        TestPublisher<String> testPublisher = TestPublisher.create();
        Flux<String> hotFlux = testPublisher.flux().share();
        
        // Start emitting before subscriber
        testPublisher.emit("missed1", "missed2");
        
        // Late subscriber misses earlier values
        StepVerifier.create(hotFlux)
            .then(() -> testPublisher.emit("received"))
            .expectNext("received")
            .then(() -> testPublisher.complete())
            .verifyComplete();
    }
}
```

### Testing Replay Behavior

```java
public class ReplayTests {
    
    @Test
    public void testReplayAllBehavior() {
        Sinks.Many<String> sink = Sinks.many().replay().all();
        Flux<String> replayFlux = sink.asFlux();
        
        // Emit before any subscribers
        sink.tryEmitNext("early1");
        sink.tryEmitNext("early2");
        
        // Late subscriber gets replayed values
        StepVerifier.create(replayFlux)
            .expectNext("early1", "early2")
            .then(() -> sink.tryEmitNext("live"))
            .expectNext("live")
            .then(() -> sink.tryEmitComplete())
            .verifyComplete();
    }
    
    @Test
    public void testLimitedReplay() {
        Flux<Integer> replayFlux = Flux.range(1, 10)
            .replay(3) // Only last 3 items
            .autoConnect();
        
        // First subscriber triggers the source and gets all items
        StepVerifier.create(replayFlux)
            .expectNext(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            .verifyComplete();
        
        // Late subscriber only gets last 3 items
        StepVerifier.create(replayFlux)
            .expectNext(8, 9, 10)
            .verifyComplete();
    }
}
```

## 7.6 When to Use Hot vs Cold Publishers

### Decision Matrix

| Use Case | Cold Publisher | Hot Publisher |
|----------|----------------|---------------|
| **Database Queries** | ✅ Each subscriber gets fresh data | ❌ Would share stale results |
| **File Processing** | ✅ Each subscriber processes independently | ❌ Could cause conflicts |
| **REST API Calls** | ✅ Each subscriber gets independent call | ⚠️ Only if caching is desired |
| **Live Data Streams** | ❌ Each subscriber would create new stream | ✅ Share live events |
| **WebSocket Events** | ❌ Would create multiple connections | ✅ Share single connection |
| **Sensor Readings** | ❌ Can't replay physical events | ✅ Multiple consumers of live data |
| **User Interface Events** | ❌ Events can't be replayed | ✅ Multiple handlers for same events |
| **Calculations** | ✅ Reproducible results | ⚠️ Only if expensive and cacheable |

### Best Practices

```java
public class BestPracticesExamples {
    
    // ✅ Good: Cold for independent operations
    public Mono<User> fetchUser(Long userId) {
        return webClient.get()
            .uri("/users/{id}", userId)
            .retrieve()
            .bodyToMono(User.class);
    }
    
    // ✅ Good: Hot for shared live data
    public Flux<StockPrice> stockPriceStream() {
        return webSocketClient
            .execute(URI.create("wss://api.stocks.com/prices"))
            .flatMapMany(session -> session.receive()
                .map(this::parseStockPrice))
            .share(); // Share the WebSocket connection
    }
    
    // ✅ Good: Cache expensive operations
    public Mono<String> expensiveComputation(String input) {
        return Mono.fromCallable(() -> performExpensiveWork(input))
            .cache(Duration.ofMinutes(5)); // Cache for 5 minutes
    }
    
    // ✅ Good: Replay for event sourcing
    public Flux<DomainEvent> eventStream() {
        return eventSink.asFlux()
            .replay(1000) // Keep last 1000 events for new subscribers
            .autoConnect();
    }
    
    private String performExpensiveWork(String input) {
        // Simulate expensive work
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        return "processed: " + input;
    }
    
    private StockPrice parseStockPrice(WebSocketMessage message) {
        // Parse logic here
        return new StockPrice("AAPL", 150.0);
    }
    
    static class StockPrice {
        final String symbol;
        final double price;
        StockPrice(String symbol, double price) {
            this.symbol = symbol;
            this.price = price;
        }
    }
    
    static class User {
        // User properties
    }
    
    static class DomainEvent {
        // Event properties
    }
}
```

## 7.7 Common Pitfalls and Solutions

### Pitfall 1: Accidental Cold-to-Hot Conversion

```java
public class CommonPitfalls {
    
    // ❌ Wrong: Accidentally making database calls hot
    public Flux<User> getAllUsersWrong() {
        return userRepository.findAll()
            .share(); // BAD: All subscribers share same stale data!
    }
    
    // ✅ Right: Keep database calls cold
    public Flux<User> getAllUsersRight() {
        return userRepository.findAll(); // Each subscriber gets fresh data
    }
    
    // ❌ Wrong: Creating multiple WebSocket connections
    public Flux<String> getNotificationsWrong() {
        return webSocketClient.connect() // Each subscriber creates new connection!
            .flatMapMany(session -> session.receive());
    }
    
    // ✅ Right: Share single WebSocket connection
    public Flux<String> getNotificationsRight() {
        return webSocketClient.connect()
            .flatMapMany(session -> session.receive())
            .share(); // All subscribers share one connection
    }
    
    // ❌ Wrong: Memory leak with unbounded replay
    public Flux<Event> getEventStreamWrong() {
        return eventSource.getEvents()
            .replay() // Unbounded replay - memory leak!
            .autoConnect();
    }
    
    // ✅ Right: Bounded replay
    public Flux<Event> getEventStreamRight() {
        return eventSource.getEvents()
            .replay(Duration.ofMinutes(5)) // Only replay last 5 minutes
            .autoConnect();
    }
}
```

### Pitfall 2: Subscription Timing Issues

```java
public class SubscriptionTimingPitfalls {
    
    @Test
    public void demonstrateTimingIssue() {
        // ❌ Problem: Hot publisher starts immediately, subscribers miss data
        Flux<Long> immediateHot = Flux.interval(Duration.ofMillis(100))
            .take(5)
            .share();
        
        // This subscriber will miss some values because interval started immediately
        try { Thread.sleep(250); } catch (InterruptedException e) {}
        
        immediateHot.subscribe(value -> System.out.println("Late subscriber: " + value));
        
        try { Thread.sleep(500); } catch (InterruptedException e) {}
    }
    
    @Test
    public void solutionWithConnectableFlux() {
        // ✅ Solution: Use ConnectableFlux to control when emission starts
        ConnectableFlux<Long> controllableHot = Flux.interval(Duration.ofMillis(100))
            .take(5)
            .publish();
        
        // Add all subscribers first
        controllableHot.subscribe(value -> System.out.println("Subscriber 1: " + value));
        
        try { Thread.sleep(250); } catch (InterruptedException e) {}
        
        controllableHot.subscribe(value -> System.out.println("Subscriber 2: " + value));
        
        // Now start emission - both subscribers get all values
        controllableHot.connect();
        
        try { Thread.sleep(600); } catch (InterruptedException e) {}
    }
    
    @Test
    public void solutionWithDefer() {
        // ✅ Solution: Use defer to delay hot publisher creation
        Flux<Long> deferredHot = Flux.defer(() -> 
            Flux.interval(Duration.ofMillis(100))
                .take(5)
                .share()
        );
        
        // Each subscription creates its own shared interval
        deferredHot.subscribe(value -> System.out.println("Subscriber 1: " + value));
        
        try { Thread.sleep(250); } catch (InterruptedException e) {}
        
        deferredHot.subscribe(value -> System.out.println("Subscriber 2: " + value));
        
        try { Thread.sleep(600); } catch (InterruptedException e) {}
    }
}
```

### Pitfall 3: Resource Management

```java
public class ResourceManagementPitfalls {
    
    @Test
    public void connectionLeakProblem() {
        // ❌ Problem: Each subscriber creates new expensive resource
        Flux<String> expensiveStream = Flux.fromCallable(() -> {
            System.out.println("Creating expensive database connection...");
            return "expensive-resource-" + System.nanoTime();
        })
        .flatMapMany(resource -> 
            Flux.interval(Duration.ofSeconds(1))
                .map(i -> resource + "-" + i)
                .take(5)
        );
        
        // This creates multiple expensive resources!
        expensiveStream.subscribe(data -> System.out.println("Sub1: " + data));
        expensiveStream.subscribe(data -> System.out.println("Sub2: " + data));
        
        try { Thread.sleep(6000); } catch (InterruptedException e) {}
    }
    
    @Test
    public void resourceSharingSolution() {
        // ✅ Solution: Share the expensive resource
        Flux<String> sharedExpensiveStream = Flux.fromCallable(() -> {
            System.out.println("Creating expensive database connection...");
            return "shared-resource-" + System.nanoTime();
        })
        .flatMapMany(resource -> 
            Flux.interval(Duration.ofSeconds(1))
                .map(i -> resource + "-" + i)
                .take(5)
        )
        .share(); // Share the resource among subscribers
        
        // Only one expensive resource is created
        sharedExpensiveStream.subscribe(data -> System.out.println("Sub1: " + data));
        sharedExpensiveStream.subscribe(data -> System.out.println("Sub2: " + data));
        
        try { Thread.sleep(6000); } catch (InterruptedException e) {}
    }
    
    @Test
    public void properResourceCleanup() {
        // ✅ Proper resource cleanup with refCount
        Flux<String> managedStream = Flux.using(
            () -> {
                System.out.println("Acquiring expensive resource");
                return "expensive-resource";
            },
            resource -> Flux.interval(Duration.ofSeconds(1))
                .map(i -> resource + "-" + i),
            resource -> System.out.println("Cleaning up: " + resource)
        )
        .publish()
        .refCount(); // Auto-connect/disconnect based on subscribers
        
        System.out.println("Adding first subscriber...");
        Disposable sub1 = managedStream.subscribe(data -> System.out.println("Sub1: " + data));
        
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        System.out.println("Adding second subscriber...");
        Disposable sub2 = managedStream.subscribe(data -> System.out.println("Sub2: " + data));
        
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        System.out.println("Disposing first subscriber...");
        sub1.dispose();
        
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        System.out.println("Disposing second subscriber...");
        sub2.dispose(); // Resource should be cleaned up here
        
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
    }
}
```

## 7.8 Advanced Patterns and Real-World Examples

### Pattern 1: Event Aggregator with Replay

```java
public class EventAggregatorPattern {
    
    static class EventAggregator {
        private final Sinks.Many<DomainEvent> eventSink;
        private final Flux<DomainEvent> eventStream;
        private final Map<Class<?>, Flux<?>> typedStreams = new ConcurrentHashMap<>();
        
        public EventAggregator() {
            this.eventSink = Sinks.many().replay().limit(Duration.ofHours(1));
            this.eventStream = eventSink.asFlux()
                .doOnNext(event -> System.out.println("Event: " + event))
                .share();
        }
        
        public void publish(DomainEvent event) {
            Sinks.EmitResult result = eventSink.tryEmitNext(event);
            if (result != Sinks.EmitResult.OK) {
                throw new RuntimeException("Failed to emit event: " + result);
            }
        }
        
        @SuppressWarnings("unchecked")
        public <T extends DomainEvent> Flux<T> subscribe(Class<T> eventType) {
            return (Flux<T>) typedStreams.computeIfAbsent(eventType, type -> 
                eventStream
                    .filter(type::isInstance)
                    .cast(type)
                    .share()
            );
        }
        
        public Flux<DomainEvent> allEvents() {
            return eventStream;
        }
        
        public void shutdown() {
            eventSink.tryEmitComplete();
        }
    }
    
    static class DomainEvent {
        final String id = UUID.randomUUID().toString();
        final Instant timestamp = Instant.now();
        
        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + id + ")";
        }
    }
    
    static class UserCreated extends DomainEvent {
        final String userId;
        UserCreated(String userId) { this.userId = userId; }
    }
    
    static class OrderPlaced extends DomainEvent {
        final String orderId;
        OrderPlaced(String orderId) { this.orderId = orderId; }
    }
    
    @Test
    public void eventAggregatorExample() {
        EventAggregator aggregator = new EventAggregator();
        
        // Subscribe to specific events
        aggregator.subscribe(UserCreated.class)
            .subscribe(event -> System.out.println("User handler: " + event));
        
        aggregator.subscribe(OrderPlaced.class)
            .subscribe(event -> System.out.println("Order handler: " + event));
        
        // Publish events
        aggregator.publish(new UserCreated("user-123"));
        aggregator.publish(new OrderPlaced("order-456"));
        
        // Late subscriber gets replayed events
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        
        aggregator.subscribe(UserCreated.class)
            .subscribe(event -> System.out.println("Late user handler: " + event));
        
        aggregator.publish(new UserCreated("user-789"));
        
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        aggregator.shutdown();
    }
}
```

### Pattern 2: Real-time Dashboard with Multiple Data Sources

```java
public class RealTimeDashboardPattern {
    
    static class MetricData {
        final String name;
        final double value;
        final Instant timestamp;
        
        MetricData(String name, double value) {
            this.name = name;
            this.value = value;
            this.timestamp = Instant.now();
        }
        
        @Override
        public String toString() {
            return String.format("%s: %.2f at %s", name, value, timestamp);
        }
    }
    
    static class DashboardService {
        private final Sinks.Many<MetricData> metricSink;
        private final Flux<MetricData> metricStream;
        
        public DashboardService() {
            this.metricSink = Sinks.many().multicast().onBackpressureBuffer(1000);
            this.metricStream = metricSink.asFlux()
                .share();
        }
        
        public void publishMetric(String name, double value) {
            metricSink.tryEmitNext(new MetricData(name, value));
        }
        
        public Flux<MetricData> getMetricStream(String metricName) {
            return metricStream
                .filter(metric -> metric.name.equals(metricName));
        }
        
        public Flux<List<MetricData>> getAggregatedMetrics(Duration window) {
            return metricStream
                .buffer(window)
                .filter(list -> !list.isEmpty());
        }
        
        public Flux<MetricData> getAllMetrics() {
            return metricStream;
        }
        
        public void shutdown() {
            metricSink.tryEmitComplete();
        }
    }
    
    @Test
    public void realTimeDashboardExample() {
        DashboardService dashboard = new DashboardService();
        
        // Start metric collectors (simulated)
        startCPUMetricCollector(dashboard);
        startMemoryMetricCollector(dashboard);
        startNetworkMetricCollector(dashboard);
        
        // Dashboard subscribers
        dashboard.getMetricStream("CPU")
            .subscribe(metric -> System.out.println("CPU Monitor: " + metric));
        
        dashboard.getMetricStream("Memory")
            .subscribe(metric -> System.out.println("Memory Monitor: " + metric));
        
        // Aggregated view
        dashboard.getAggregatedMetrics(Duration.ofSeconds(2))
            .subscribe(metrics -> 
                System.out.println("Dashboard update: " + metrics.size() + " metrics"));
        
        // Let it run for a while
        try { Thread.sleep(5000); } catch (InterruptedException e) {}
        dashboard.shutdown();
    }
    
    private void startCPUMetricCollector(DashboardService dashboard) {
        Flux.interval(Duration.ofMillis(500))
            .take(20)
            .subscribe(i -> dashboard.publishMetric("CPU", Math.random() * 100));
    }
    
    private void startMemoryMetricCollector(DashboardService dashboard) {
        Flux.interval(Duration.ofSeconds(1))
            .take(10)
            .subscribe(i -> dashboard.publishMetric("Memory", Math.random() * 16384));
    }
    
    private void startNetworkMetricCollector(DashboardService dashboard) {
        Flux.interval(Duration.ofMillis(200))
            .take(50)
            .subscribe(i -> dashboard.publishMetric("Network", Math.random() * 1000));
    }
}
```

### Pattern 3: Cache-Aside Pattern with Hot Publishers

```java
public class CacheAsidePattern {
    
    static class CacheService<K, V> {
        private final Map<K, Sinks.One<V>> cache = new ConcurrentHashMap<>();
        private final Function<K, Mono<V>> loader;
        
        public CacheService(Function<K, Mono<V>> loader) {
            this.loader = loader;
        }
        
        public Mono<V> get(K key) {
            return Mono.defer(() -> {
                Sinks.One<V> sink = cache.computeIfAbsent(key, k -> {
                    Sinks.One<V> newSink = Sinks.one();
                    
                    // Load asynchronously and emit to sink
                    loader.apply(k)
                        .subscribe(
                            value -> newSink.tryEmitValue(value),
                            error -> newSink.tryEmitError(error)
                        );
                    
                    return newSink;
                });
                
                return sink.asMono();
            });
        }
        
        public void invalidate(K key) {
            cache.remove(key);
        }
        
        public void clear() {
            cache.clear();
        }
    }
    
    @Test
    public void cacheAsideExample() {
        AtomicInteger loadCount = new AtomicInteger(0);
        
        // Expensive loader function
        Function<String, Mono<String>> expensiveLoader = key -> {
            int count = loadCount.incrementAndGet();
            System.out.println("Loading " + key + " (call #" + count + ")");
            
            return Mono.delay(Duration.ofSeconds(1))
                .map(i -> "loaded-" + key + "-" + count);
        };
        
        CacheService<String, String> cache = new CacheService<>(expensiveLoader);
        
        // Multiple concurrent requests for same key
        Flux.range(1, 5)
            .flatMap(i -> 
                cache.get("user-123")
                    .doOnNext(value -> System.out.println("Request " + i + ": " + value))
            )
            .blockLast();
        
        System.out.println("Total loads: " + loadCount.get()); // Should be 1
        
        // Request different key
        cache.get("user-456")
            .doOnNext(value -> System.out.println("Different key: " + value))
            .block();
        
        System.out.println("Total loads: " + loadCount.get()); // Should be 2
    }
}
```

## Key Takeaways

1. **Cold Publishers (Default)**:
   - Each subscriber gets independent execution
   - Perfect for database calls, file operations, REST APIs
   - Reproducible and predictable behavior

2. **Hot Publishers (Shared)**:
   - All subscribers share the same stream
   - Ideal for live data, events, WebSocket connections
   - Late subscribers may miss early values

3. **ConnectableFlux**:
   - Fine-grained control over when emission starts
   - Perfect when you need to coordinate multiple subscribers

4. **Sinks (Modern Approach)**:
   - Thread-safe programmatic emission
   - Replaces deprecated Processors
   - Better error handling and backpressure support

5. **Testing Strategies**:
   - Test cold publishers for independent behavior
   - Test hot publishers for shared behavior and timing
   - Use `TestPublisher` for controlled emission scenarios

6. **Common Pitfalls**:
   - Accidentally making expensive operations hot
   - Resource leaks with unbounded replay
   - Subscription timing issues

Key Concepts Mastered in Phase 7:

Cold Publishers (Default Behavior):

Lazy execution - nothing happens until subscription
Each subscriber gets independent stream execution
Perfect for database calls, API requests, file operations


Hot Publishers (Shared Streams):

Eager execution - starts emitting regardless of subscribers
All subscribers share the same stream
Ideal for live events, WebSocket connections, sensor data


ConnectableFlux for Control:

publish() - manual control over when emission starts
autoConnect() - starts when N subscribers join
refCount() - starts/stops based on subscriber count


Modern Sinks API:

Thread-safe programmatic emission
Sinks.Many for multiple values with various strategies
Sinks.One for single value emission
Proper error handling and retry mechanisms


Advanced Patterns:

Event aggregator with replay capabilities
Real-time dashboard with multiple data sources
Cache-aside pattern using hot publishers


Testing Hot vs Cold:

Verifying independent execution for cold publishers
Testing shared behavior and timing for hot publishers
Using StepVerifier with ConnectableFlux and Sinks



Critical Decision Points:

Use Cold when you need independent, reproducible operations
Use Hot when you need to share live data or expensive resources
Use Replay when late subscribers need historical data
Use ConnectableFlux when you need precise control over emission timing

## Next Steps

In **Phase 8**, we'll explore Context and Reactive Streams specification, learning how to propagate contextual information through reactive chains and understand the underlying contracts that make reactive streams work.