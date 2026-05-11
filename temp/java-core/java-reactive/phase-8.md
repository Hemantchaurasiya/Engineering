# Phase 8: Context and Reactive Streams - Complete Implementation Guide

## 8.1 Understanding Reactor Context

### What is Context?

Reactor Context is a **immutable key-value store** that propagates **downstream** through the reactive chain. It's the reactive equivalent of ThreadLocal but designed for asynchronous, non-blocking operations.

#### Key Characteristics:
- **Immutable**: Each modification creates a new Context
- **Downstream Propagation**: Flows from subscriber to publisher
- **Thread-Safe**: Can be safely shared across threads
- **Request-Scoped**: Lives for the duration of a subscription

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

public class BasicContextExamples {
    
    @Test
    public void basicContextUsage() {
        Mono<String> mono = Mono.just("Hello")
            .flatMap(s -> Mono.deferContextual(ctx -> {
                String name = ctx.get("name");
                return Mono.just(s + " " + name);
            }))
            .contextWrite(Context.of("name", "World"));
        
        StepVerifier.create(mono)
            .expectNext("Hello World")
            .verifyComplete();
    }
    
    @Test
    public void contextPropagationDirection() {
        Mono<String> mono = Mono.just("Start")
            .flatMap(s -> {
                System.out.println("Step 1: " + s);
                return Mono.deferContextual(ctx -> {
                    if (ctx.hasKey("user")) {
                        return Mono.just(s + " for " + ctx.get("user"));
                    }
                    return Mono.just(s + " for anonymous");
                });
            })
            .map(s -> {
                System.out.println("Step 2: " + s);
                return s.toUpperCase();
            })
            .contextWrite(Context.of("user", "Alice")); // Context added at the end!
        
        StepVerifier.create(mono)
            .expectNext("START FOR ALICE")
            .verifyComplete();
    }
    
    @Test
    public void multipleContextValues() {
        Mono<String> mono = Mono.deferContextual(ctx -> {
            String user = ctx.get("user");
            String tenant = ctx.get("tenant");
            String correlationId = ctx.get("correlationId");
            
            return Mono.just(String.format("User: %s, Tenant: %s, CorrelationId: %s", 
                                         user, tenant, correlationId));
        })
        .contextWrite(Context.of("user", "bob")
                           .put("tenant", "acme-corp")
                           .put("correlationId", "req-12345"));
        
        StepVerifier.create(mono)
            .expectNext("User: bob, Tenant: acme-corp, CorrelationId: req-12345")
            .verifyComplete();
    }
    
    @Test
    public void contextImmutability() {
        Context originalContext = Context.of("key1", "value1");
        Context newContext = originalContext.put("key2", "value2");
        
        // Original context unchanged
        assertFalse(originalContext.hasKey("key2"));
        
        // New context has both keys
        assertTrue(newContext.hasKey("key1"));
        assertTrue(newContext.hasKey("key2"));
        
        System.out.println("Original: " + originalContext);
        System.out.println("New: " + newContext);
    }
}
```

### Advanced Context Operations

```java
public class AdvancedContextExamples {
    
    @Test
    public void contextWithDefaults() {
        Mono<String> mono = Mono.deferContextual(ctx -> {
            // Get with default value
            String environment = ctx.getOrDefault("environment", "development");
            String region = ctx.getOrDefault("region", "us-east-1");
            
            return Mono.just("Running in " + environment + " environment in " + region);
        })
        .contextWrite(Context.of("environment", "production"));
        // Note: region not provided, will use default
        
        StepVerifier.create(mono)
            .expectNext("Running in production environment in us-east-1")
            .verifyComplete();
    }
    
    @Test
    public void contextModification() {
        Mono<String> mono = Mono.just("initial")
            .contextWrite(Context.of("counter", 1))
            .flatMap(value -> {
                return Mono.deferContextual(ctx -> {
                    int counter = ctx.get("counter");
                    return Mono.just(value + "-" + counter);
                })
                .contextWrite(ctx -> ctx.put("counter", ctx.<Integer>get("counter") + 1));
            })
            .flatMap(value -> {
                return Mono.deferContextual(ctx -> {
                    int counter = ctx.get("counter");
                    return Mono.just(value + "-" + counter);
                });
            });
        
        StepVerifier.create(mono)
            .expectNext("initial-2-2") // Counter was incremented in the middle
            .verifyComplete();
    }
    
    @Test
    public void contextMerging() {
        Context baseContext = Context.of("app", "myapp")
                                   .put("version", "1.0");
        
        Context requestContext = Context.of("user", "alice")
                                      .put("requestId", "req-123");
        
        Mono<String> mono = Mono.deferContextual(ctx -> {
            return Mono.just(String.format("App: %s v%s, User: %s, Request: %s",
                ctx.get("app"), ctx.get("version"), 
                ctx.get("user"), ctx.get("requestId")));
        })
        .contextWrite(baseContext.putAll(requestContext));
        
        StepVerifier.create(mono)
            .expectNext("App: myapp v1.0, User: alice, Request: req-123")
            .verifyComplete();
    }
    
    @Test
    public void contextWithOptionalValues() {
        Mono<String> mono = Mono.deferContextual(ctx -> {
            Optional<String> user = ctx.getOrEmpty("user");
            Optional<String> adminFlag = ctx.getOrEmpty("isAdmin");
            
            if (user.isPresent()) {
                String message = "User: " + user.get();
                if (adminFlag.isPresent() && Boolean.parseBoolean(adminFlag.get())) {
                    message += " (Admin)";
                }
                return Mono.just(message);
            }
            
            return Mono.just("Anonymous user");
        })
        .contextWrite(Context.of("user", "admin-user")
                           .put("isAdmin", "true"));
        
        StepVerifier.create(mono)
            .expectNext("User: admin-user (Admin)")
            .verifyComplete();
    }
}
```

## 8.2 Practical Context Use Cases

### Use Case 1: Request Tracing and Correlation IDs

```java
public class RequestTracingExample {
    
    static class TracingService {
        public static Mono<Void> logRequest(String operation) {
            return Mono.deferContextual(ctx -> {
                String correlationId = ctx.getOrDefault("correlationId", "unknown");
                String userId = ctx.getOrDefault("userId", "anonymous");
                
                System.out.printf("[%s] %s - Operation: %s, User: %s%n", 
                    Instant.now(), correlationId, operation, userId);
                
                return Mono.empty();
            });
        }
        
        public static <T> Mono<T> traced(String operation, Mono<T> operation) {
            return logRequest("Starting " + operation)
                .then(operation)
                .doOnSuccess(result -> 
                    logRequest("Completed " + operation + " with result: " + result).subscribe())
                .doOnError(error -> 
                    logRequest("Failed " + operation + " with error: " + error.getMessage()).subscribe());
        }
    }
    
    static class UserService {
        public Mono<User> findUser(Long userId) {
            return TracingService.traced("findUser", 
                Mono.delay(Duration.ofMillis(100))
                   .map(i -> new User(userId, "User " + userId))
            );
        }
        
        public Mono<User> updateUser(User user) {
            return TracingService.traced("updateUser",
                Mono.delay(Duration.ofMillis(200))
                   .map(i -> {
                       System.out.println("Updating user: " + user.name);
                       return user;
                   })
            );
        }
    }
    
    @Test
    public void requestTracingExample() {
        UserService userService = new UserService();
        
        Mono<User> operation = userService.findUser(123L)
            .flatMap(user -> {
                user.name = "Updated " + user.name;
                return userService.updateUser(user);
            })
            .contextWrite(Context.of("correlationId", "REQ-" + UUID.randomUUID())
                               .put("userId", "alice"));
        
        StepVerifier.create(operation)
            .expectNextMatches(user -> user.name.startsWith("Updated"))
            .verifyComplete();
    }
    
    static class User {
        final Long id;
        String name;
        
        User(Long id, String name) {
            this.id = id;
            this.name = name;
        }
        
        @Override
        public String toString() {
            return "User{id=" + id + ", name='" + name + "'}";
        }
    }
}
```

### Use Case 2: Security Context Propagation

```java
public class SecurityContextExample {
    
    static class SecurityContext {
        final String username;
        final Set<String> roles;
        final String tenantId;
        
        SecurityContext(String username, Set<String> roles, String tenantId) {
            this.username = username;
            this.roles = roles;
            this.tenantId = tenantId;
        }
        
        boolean hasRole(String role) {
            return roles.contains(role);
        }
        
        @Override
        public String toString() {
            return String.format("SecurityContext{user='%s', roles=%s, tenant='%s'}", 
                               username, roles, tenantId);
        }
    }
    
    static class SecurityService {
        public static Mono<SecurityContext> getCurrentSecurity() {
            return Mono.deferContextual(ctx -> {
                if (ctx.hasKey("security")) {
                    return Mono.just(ctx.get("security"));
                }
                return Mono.error(new SecurityException("No security context"));
            });
        }
        
        public static <T> Mono<T> requireRole(String role, Mono<T> operation) {
            return getCurrentSecurity()
                .flatMap(security -> {
                    if (security.hasRole(role)) {
                        return operation;
                    }
                    return Mono.error(new SecurityException(
                        "User " + security.username + " lacks required role: " + role));
                });
        }
        
        public static <T> Mono<T> withTenantFilter(Mono<T> operation) {
            return getCurrentSecurity()
                .flatMap(security -> operation
                    .contextWrite(Context.of("tenantId", security.tenantId)));
        }
    }
    
    static class OrderService {
        public Mono<List<Order>> getAllOrders() {
            return SecurityService.requireRole("READ_ORDERS",
                Mono.deferContextual(ctx -> {
                    String tenantId = ctx.get("tenantId");
                    System.out.println("Fetching orders for tenant: " + tenantId);
                    
                    return Mono.just(Arrays.asList(
                        new Order("ORDER-1", tenantId),
                        new Order("ORDER-2", tenantId)
                    ));
                })
            );
        }
        
        public Mono<Order> createOrder(Order order) {
            return SecurityService.requireRole("CREATE_ORDERS",
                SecurityService.getCurrentSecurity()
                    .flatMap(security -> {
                        order.tenantId = security.tenantId;
                        System.out.println("Creating order for user: " + security.username);
                        return Mono.just(order);
                    })
            );
        }
    }
    
    @Test
    public void securityContextExample() {
        OrderService orderService = new OrderService();
        SecurityContext security = new SecurityContext(
            "alice", 
            Set.of("READ_ORDERS", "CREATE_ORDERS"), 
            "tenant-123"
        );
        
        // Test with proper permissions
        Mono<List<Order>> getOrders = orderService.getAllOrders()
            .contextWrite(Context.of("security", security));
        
        StepVerifier.create(getOrders)
            .expectNextMatches(orders -> 
                orders.size() == 2 && 
                orders.stream().allMatch(o -> o.tenantId.equals("tenant-123")))
            .verifyComplete();
        
        // Test without permissions
        SecurityContext limitedSecurity = new SecurityContext(
            "bob", 
            Set.of("READ_ORDERS"), // Missing CREATE_ORDERS
            "tenant-456"
        );
        
        Mono<Order> createOrder = orderService.createOrder(new Order("NEW-ORDER", null))
            .contextWrite(Context.of("security", limitedSecurity));
        
        StepVerifier.create(createOrder)
            .expectError(SecurityException.class)
            .verify();
    }
    
    static class Order {
        final String id;
        String tenantId;
        
        Order(String id, String tenantId) {
            this.id = id;
            this.tenantId = tenantId;
        }
        
        @Override
        public String toString() {
            return "Order{id='" + id + "', tenantId='" + tenantId + "'}";
        }
    }
}
```

### Use Case 3: Database Transaction Context

```java
public class TransactionContextExample {
    
    static class TransactionContext {
        final String transactionId;
        final boolean readOnly;
        final IsolationLevel isolationLevel;
        
        TransactionContext(String transactionId, boolean readOnly, IsolationLevel isolationLevel) {
            this.transactionId = transactionId;
            this.readOnly = readOnly;
            this.isolationLevel = isolationLevel;
        }
        
        @Override
        public String toString() {
            return String.format("Tx{id='%s', readOnly=%s, isolation=%s}", 
                               transactionId, readOnly, isolationLevel);
        }
    }
    
    enum IsolationLevel {
        READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE
    }
    
    static class TransactionService {
        public static <T> Mono<T> withTransaction(Mono<T> operation) {
            return withTransaction(operation, false, IsolationLevel.READ_COMMITTED);
        }
        
        public static <T> Mono<T> withTransaction(Mono<T> operation, boolean readOnly, IsolationLevel isolation) {
            String txId = "TX-" + UUID.randomUUID().toString().substring(0, 8);
            TransactionContext txContext = new TransactionContext(txId, readOnly, isolation);
            
            return Mono.deferContextual(ctx -> {
                System.out.println("Starting transaction: " + txContext);
                
                return operation
                    .contextWrite(Context.of("transaction", txContext))
                    .doOnSuccess(result -> System.out.println("Committing transaction: " + txId))
                    .doOnError(error -> System.out.println("Rolling back transaction: " + txId + " due to: " + error.getMessage()))
                    .doFinally(signal -> System.out.println("Closed transaction: " + txId));
            });
        }
        
        public static Mono<TransactionContext> getCurrentTransaction() {
            return Mono.deferContextual(ctx -> {
                if (ctx.hasKey("transaction")) {
                    return Mono.just(ctx.get("transaction"));
                }
                return Mono.error(new IllegalStateException("No active transaction"));
            });
        }
    }
    
    static class UserRepository {
        public Mono<User> save(User user) {
            return TransactionService.getCurrentTransaction()
                .flatMap(tx -> {
                    if (tx.readOnly) {
                        return Mono.error(new IllegalStateException("Cannot save in read-only transaction"));
                    }
                    
                    System.out.println("Saving user " + user.name + " in transaction " + tx.transactionId);
                    return Mono.delay(Duration.ofMillis(100))
                        .map(i -> user);
                });
        }
        
        public Mono<User> findById(Long id) {
            return TransactionService.getCurrentTransaction()
                .flatMap(tx -> {
                    System.out.println("Finding user " + id + " in transaction " + tx.transactionId + " (isolation: " + tx.isolationLevel + ")");
                    return Mono.delay(Duration.ofMillis(50))
                        .map(i -> new User(id, "User " + id));
                });
        }
    }
    
    @Test
    public void transactionContextExample() {
        UserRepository userRepo = new UserRepository();
        
        // Test read-write transaction
        Mono<User> saveOperation = TransactionService.withTransaction(
            userRepo.findById(1L)
                .flatMap(user -> {
                    user.name = "Updated " + user.name;
                    return userRepo.save(user);
                })
        );
        
        StepVerifier.create(saveOperation)
            .expectNextMatches(user -> user.name.startsWith("Updated"))
            .verifyComplete();
        
        // Test read-only transaction
        Mono<User> readOnlyOperation = TransactionService.withTransaction(
            userRepo.findById(2L)
                .flatMap(user -> {
                    user.name = "Should fail";
                    return userRepo.save(user); // Should fail in read-only tx
                }),
            true, // read-only
            IsolationLevel.REPEATABLE_READ
        );
        
        StepVerifier.create(readOnlyOperation)
            .expectError(IllegalStateException.class)
            .verify();
    }
    
    static class User {
        final Long id;
        String name;
        
        User(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
```

## 8.3 Testing Context Propagation

```java
public class ContextTestingExamples {
    
    @Test
    public void testContextPropagation() {
        Mono<String> mono = Mono.just("test")
            .flatMap(value -> 
                Mono.deferContextual(ctx -> 
                    Mono.just(value + "-" + ctx.get("suffix"))))
            .contextWrite(Context.of("suffix", "contextual"));
        
        StepVerifier.create(mono)
            .expectNext("test-contextual")
            .verifyComplete();
    }
    
    @Test
    public void testContextWithMultipleChains() {
        Flux<String> flux = Flux.just("a", "b")
            .flatMap(value -> 
                Mono.deferContextual(ctx -> {
                    String prefix = ctx.get("prefix");
                    return Mono.just(prefix + value);
                }))
            .contextWrite(Context.of("prefix", "item-"));
        
        StepVerifier.create(flux)
            .expectNext("item-a", "item-b")
            .verifyComplete();
    }
    
    @Test
    public void testContextVisibility() {
        Mono<String> mono = Mono.just("start")
            .contextWrite(Context.of("early", "visible"))
            .flatMap(value -> 
                Mono.deferContextual(ctx -> {
                    // This should see "early" but not "late"
                    String early = ctx.getOrDefault("early", "missing");
                    String late = ctx.getOrDefault("late", "missing");
                    return Mono.just(value + "-" + early + "-" + late);
                }))
            .contextWrite(Context.of("late", "invisible")); // Added after flatMap
        
        StepVerifier.create(mono)
            .expectNext("start-visible-missing")
            .verifyComplete();
    }
    
    @Test
    public void testContextOverriding() {
        Mono<String> mono = Mono.deferContextual(ctx -> 
                Mono.just("value-" + ctx.get("key")))
            .contextWrite(Context.of("key", "inner"))  // Inner context
            .contextWrite(Context.of("key", "outer")); // Outer context (closer to subscriber)
        
        StepVerifier.create(mono)
            .expectNext("value-inner") // Inner context wins
            .verifyComplete();
    }
    
    @Test
    public void testContextInErrorScenarios() {
        Mono<String> mono = Mono.deferContextual(ctx -> {
                String user = ctx.get("user");
                if ("admin".equals(user)) {
                    return Mono.just("Welcome admin");
                }
                return Mono.error(new SecurityException("Access denied for " + user));
            })
            .contextWrite(Context.of("user", "guest"));
        
        StepVerifier.create(mono)
            .expectErrorMatches(error -> 
                error instanceof SecurityException && 
                error.getMessage().contains("guest"))
            .verify();
    }
}
```

## 8.4 Understanding the Reactive Streams Specification

### The Four Core Interfaces

The Reactive Streams specification defines four interfaces that form the contract for asynchronous stream processing with non-blocking backpressure.

```java
import java.util.concurrent.Flow.*;
import org.reactivestreams.*;

public class ReactiveStreamsSpecification {
    
    /**
     * Publisher<T> - produces a potentially unbounded number of sequenced elements
     */
    interface PublisherExample<T> extends Publisher<T> {
        // Only method: subscribe(Subscriber<? super T> s)
        // Must call onSubscribe exactly once
        // May call onNext 0 to N times
        // Must call onComplete or onError exactly once (terminal event)
        // After terminal event, no more signals
    }
    
    /**
     * Subscriber<T> - receives and processes elements from a Publisher
     */
    static class SubscriberExample<T> implements Subscriber<T> {
        private Subscription subscription;
        private final List<T> received = new ArrayList<>();
        
        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
            System.out.println("Subscribed! Requesting 1 item...");
            s.request(1); // Request first item
        }
        
        @Override
        public void onNext(T item) {
            received.add(item);
            System.out.println("Received: " + item + " (total: " + received.size() + ")");
            
            if (received.size() < 5) { // Request more items
                subscription.request(1);
            } else {
                subscription.cancel(); // Stop receiving
            }
        }
        
        @Override
        public void onError(Throwable t) {
            System.err.println("Error occurred: " + t.getMessage());
        }
        
        @Override
        public void onComplete() {
            System.out.println("Stream completed. Total received: " + received.size());
        }
        
        public List<T> getReceived() {
            return new ArrayList<>(received);
        }
    }
    
    /**
     * Subscription - link between Publisher and Subscriber
     */
    static class SubscriptionExample implements Subscription {
        private final Subscriber<? super String> subscriber;
        private final String[] data;
        private int index = 0;
        private boolean cancelled = false;
        private long pendingRequests = 0;
        
        SubscriptionExample(Subscriber<? super String> subscriber, String[] data) {
            this.subscriber = subscriber;
            this.data = data;
        }
        
        @Override
        public void request(long n) {
            if (n <= 0) {
                subscriber.onError(new IllegalArgumentException("Request must be positive"));
                return;
            }
            
            synchronized (this) {
                if (cancelled) return;
                
                pendingRequests = Long.MAX_VALUE - pendingRequests > n ? 
                    pendingRequests + n : Long.MAX_VALUE;
                
                // Fulfill requests
                while (pendingRequests > 0 && index < data.length && !cancelled) {
                    subscriber.onNext(data[index++]);
                    pendingRequests--;
                }
                
                // Complete if all data sent
                if (index >= data.length && !cancelled) {
                    subscriber.onComplete();
                }
            }
        }
        
        @Override
        public void cancel() {
            synchronized (this) {
                cancelled = true;
                System.out.println("Subscription cancelled at index " + index);
            }
        }
    }
    
    /**
     * Processor<T, R> - both Subscriber and Publisher (transforms elements)
     * Note: Processors are deprecated in Reactor in favor of operators and Sinks
     */
    static class ProcessorExample<T, R> implements Processor<T, R> {
        private final Function<T, R> transformer;
        private Subscriber<? super R> downstream;
        private Subscription upstream;
        private final Queue<R> buffer = new ConcurrentLinkedQueue<>();
        
        ProcessorExample(Function<T, R> transformer) {
            this.transformer = transformer;
        }
        
        // Publisher interface
        @Override
        public void subscribe(Subscriber<? super R> s) {
            this.downstream = s;
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    // Forward requests upstream
                    if (upstream != null) {
                        upstream.request(n);
                    }
                }
                
                @Override
                public void cancel() {
                    if (upstream != null) {
                        upstream.cancel();
                    }
                }
            });
        }
        
        // Subscriber interface
        @Override
        public void onSubscribe(Subscription s) {
            this.upstream = s;
            s.request(1); // Start requesting
        }
        
        @Override
        public void onNext(T item) {
            try {
                R transformed = transformer.apply(item);
                if (downstream != null) {
                    downstream.onNext(transformed);
                }
                
                // Request next item
                if (upstream != null) {
                    upstream.request(1);
                }
            } catch (Exception e) {
                onError(e);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            if (downstream != null) {
                downstream.onError(t);
            }
        }
        
        @Override
        public void onComplete() {
            if (downstream != null) {
                downstream.onComplete();
            }
        }
    }
    
    @Test
    public void reactiveStreamsExample() {
        String[] data = {"A", "B", "C", "D", "E", "F", "G"};
        
        // Create custom publisher
        Publisher<String> publisher = subscriber -> {
            SubscriptionExample subscription = new SubscriptionExample(subscriber, data);
            subscriber.onSubscribe(subscription);
        };
        
        // Create custom subscriber
        SubscriberExample<String> subscriber = new SubscriberExample<>();
        
        // Connect them
        publisher.subscribe(subscriber);
        
        // Wait a bit for async processing
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        
        // Verify received items
        List<String> received = subscriber.getReceived();
        System.out.println("Final received items: " + received);
        assertEquals(5, received.size()); // Should cancel after 5 items
    }
}
```

### Contract Rules and Violations

```java
public class ReactiveStreamsContracts {
    
    @Test
    public void demonstrateContractViolations() {
        // ❌ VIOLATION: Calling onNext after onComplete
        TestPublisher<String> violatingPublisher = TestPublisher.createNoncompliant(
            TestPublisher.Violation.CLEANUP_ON_TERMINATE
        );
        
        StepVerifier.create(violatingPublisher.flux())
            .then(() -> violatingPublisher.emit("first"))
            .expectNext("first")
            .then(() -> violatingPublisher.complete())
            .then(() -> {
                // This violates the spec but TestPublisher allows it
                violatingPublisher.emit("after complete");
            })
            .expectNext("after complete") // Should not happen in real implementation
            .verifyComplete();
    }
    
    @Test
    public void demonstrateBackpressureContract() {
        // Publisher must respect subscriber's demand
        AtomicLong requestedCount = new AtomicLong(0);
        AtomicLong emittedCount = new AtomicLong(0);
        
        Flux<Integer> respectfulPublisher = Flux.create(sink -> {
            sink.onRequest(n -> {
                long currentRequested = requestedCount.addAndGet(n);
                System.out.println("Subscriber requested: " + n + " (total: " + currentRequested + ")");
                
                // Only emit up to requested amount
                while (requestedCount.get() > emittedCount.get() && emittedCount.get() < 10) {
                    int value = (int) emittedCount.incrementAndGet();
                    System.out.println("Emitting: " + value);
                    sink.next(value);
                    requestedCount.decrementAndGet();
                }
                
                if (emittedCount.get() >= 10) {
                    sink.complete();
                }
            });
        });
        
        StepVerifier.create(respectfulPublisher, 3) // Initial demand of 3
            .expectNext(1, 2, 3)
            .thenRequest(2) // Request 2 more
            .expectNext(4, 5)
            .thenRequest(10) // Request remaining
            .expectNext(6, 7, 8, 9, 10)
            .verifyComplete();
    }
    
    @Test
    public void demonstrateSubscriptionContract() {
        // Subscription must handle negative or zero requests properly
        Flux<String> flux = Flux.just("item1", "item2", "item3");
        
        flux.subscribe(new BaseSubscriber<String>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                // ❌ This should cause an error
                try {
                    subscription.request(0); // Invalid: must be > 0
                } catch (Exception e) {
                    System.out.println("Caught expected error: " + e.getMessage());
                }
                
                // ✅ Correct: request positive number
                subscription.request(2);
            }
            
            @Override
            protected void hookOnNext(String value) {
                System.out.println("Received: " + value);
            }
            
            @Override
            protected void hookOnError(Throwable throwable) {
                System.out.println("Error: " + throwable.getMessage());
            }
            
            @Override
            protected void hookOnComplete() {
                System.out.println("Completed");
            }
        });
        
        try { Thread.sleep(100); } catch (InterruptedException e) {}
    }
}
```

## 8.5 Context and Reactive Streams Integration

### Context Propagation Through Publishers

```java
public class ContextStreamIntegration {
    
    @Test
    public void contextWithCustomPublisher() {
        // Custom publisher that reads from context
        Publisher<String> contextAwarePublisher = subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                private boolean cancelled = false;
                
                @Override
                public void request(long n) {
                    if (!cancelled && n > 0) {
                        // In real implementation, you'd need to access context
                        // through the subscriber's context (if it supports it)
                        subscriber.onNext("Custom data");
                        subscriber.onComplete();
                    }
                }
                
                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        };
        
        // Wrap with Reactor to get context support
        Flux<String> contextualFlux = Flux.from(contextAwarePublisher)
            .flatMap(data -> 
                Mono.deferContextual(ctx -> {
                    String prefix = ctx.getOrDefault("prefix", "default");
                    return Mono.just(prefix + ": " + data);
                }))
            .contextWrite(Context.of("prefix", "Enhanced"));
        
        StepVerifier.create(contextualFlux)
            .expectNext("Enhanced: Custom data")
            .verifyComplete();
    }
    
    @Test
    public void contextWithFlowAPI() {
        // Using Java 9+ Flow API with Reactor
        Flow.Publisher<Integer> flowPublisher = subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    for (int i = 1; i <= n && i <= 3; i++) {
                        subscriber.onNext(i);
                    }
                    subscriber.onComplete();
                }
                
                @Override
                public void cancel() {}
            });
        };
        
        // Convert Flow.Publisher to Reactor Flux for context support
        Flux<String> contextualFlux = Flux.from(flowPublisher)
            .map(i -> i * 10)
            .flatMap(value -> 
                Mono.deferContextual(ctx -> {
                    String operation = ctx.get("operation");
                    return Mono.just(operation + " result: " + value);
                }))
            .contextWrite(Context.of("operation", "multiply"));
        
        StepVerifier.create(contextualFlux)
            .expectNext("multiply result: 10", "multiply result: 20", "multiply result: 30")
            .verifyComplete();
    }
}
```

### Advanced Context Patterns

```java
public class AdvancedContextPatterns {
    
    /**
     * Context Scoping - Different contexts for different parts of the stream
     */
    @Test
    public void contextScoping() {
        Flux<String> flux = Flux.just("1", "2", "3")
            .flatMap(item -> {
                // Each item gets processed in its own context scope
                return Mono.just(item)
                    .flatMap(i -> 
                        Mono.deferContextual(ctx -> 
                            Mono.just(ctx.get("prefix") + i)))
                    .contextWrite(Context.of("prefix", "item-" + item + "-"));
            })
            .contextWrite(Context.of("global", "shared"));
        
        StepVerifier.create(flux)
            .expectNext("item-1-1", "item-2-2", "item-3-3")
            .verifyComplete();
    }
    
    /**
     * Context Inheritance and Overriding
     */
    @Test
    public void contextInheritance() {
        Mono<String> mono = Mono.deferContextual(ctx -> {
                String global = ctx.get("global");
                String local = ctx.get("local");
                String overridden = ctx.get("overridden");
                
                return Mono.just(String.format("Global: %s, Local: %s, Overridden: %s", 
                                              global, local, overridden));
            })
            .contextWrite(Context.of("local", "inner-value")
                               .put("overridden", "inner-overridden"))
            .contextWrite(Context.of("global", "outer-value")
                               .put("overridden", "outer-overridden"));
        
        StepVerifier.create(mono)
            .expectNext("Global: outer-value, Local: inner-value, Overridden: inner-overridden")
            .verifyComplete();
    }
    
    /**
     * Context Middleware Pattern
     */
    static class ContextMiddleware {
        public static <T> Function<Mono<T>, Mono<T>> withLogging(String operation) {
            return mono -> mono
                .doOnSubscribe(sub -> 
                    Mono.deferContextual(ctx -> {
                        String correlationId = ctx.getOrDefault("correlationId", "unknown");
                        System.out.println("[" + correlationId + "] Starting: " + operation);
                        return Mono.empty();
                    }).subscribe())
                .doOnSuccess(result -> 
                    Mono.deferContextual(ctx -> {
                        String correlationId = ctx.getOrDefault("correlationId", "unknown");
                        System.out.println("[" + correlationId + "] Completed: " + operation);
                        return Mono.empty();
                    }).subscribe())
                .doOnError(error -> 
                    Mono.deferContextual(ctx -> {
                        String correlationId = ctx.getOrDefault("correlationId", "unknown");
                        System.out.println("[" + correlationId + "] Failed: " + operation + " - " + error.getMessage());
                        return Mono.empty();
                    }).subscribe());
        }
        
        public static <T> Function<Mono<T>, Mono<T>> withTiming(String operation) {
            return mono -> Mono.deferContextual(ctx -> {
                long startTime = System.currentTimeMillis();
                String correlationId = ctx.getOrDefault("correlationId", "unknown");
                
                return mono.doFinally(signal -> {
                    long duration = System.currentTimeMillis() - startTime;
                    System.out.println("[" + correlationId + "] " + operation + " took " + duration + "ms");
                });
            });
        }
        
        public static <T> Function<Mono<T>, Mono<T>> withRetry(String operation, int maxRetries) {
            return mono -> mono
                .retryWhen(Retry.fixedDelay(maxRetries, Duration.ofMillis(100))
                    .doBeforeRetry(retrySignal -> 
                        Mono.deferContextual(ctx -> {
                            String correlationId = ctx.getOrDefault("correlationId", "unknown");
                            System.out.println("[" + correlationId + "] Retrying " + operation + 
                                             " (attempt " + (retrySignal.totalRetries() + 1) + ")");
                            return Mono.empty();
                        }).subscribe()));
        }
    }
    
    @Test
    public void contextMiddlewarePattern() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        Mono<String> operation = Mono.fromCallable(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Attempt " + attempt + " failed");
            }
            return "Success on attempt " + attempt;
        })
        .transform(ContextMiddleware.withLogging("database-query"))
        .transform(ContextMiddleware.withTiming("database-query"))
        .transform(ContextMiddleware.withRetry("database-query", 5))
        .contextWrite(Context.of("correlationId", "REQ-12345"));
        
        StepVerifier.create(operation)
            .expectNext("Success on attempt 3")
            .verifyComplete();
    }
    
    /**
     * Context-Aware Error Handling
     */
    @Test
    public void contextAwareErrorHandling() {
        Mono<String> mono = Mono.error(new RuntimeException("Service unavailable"))
            .onErrorResume(error -> 
                Mono.deferContextual(ctx -> {
                    boolean isDevelopment = ctx.getOrDefault("environment", "production").equals("development");
                    String userId = ctx.getOrDefault("userId", "anonymous");
                    
                    if (isDevelopment) {
                        // In development, return detailed error
                        return Mono.just("DEV ERROR for " + userId + ": " + error.getMessage());
                    } else {
                        // In production, return generic error
                        return Mono.just("Service temporarily unavailable for user " + userId);
                    }
                }))
            .contextWrite(Context.of("environment", "development")
                               .put("userId", "alice"));
        
        StepVerifier.create(mono)
            .expectNext("DEV ERROR for alice: Service unavailable")
            .verifyComplete();
    }
}
```

## 8.6 Performance and Memory Considerations

### Context Performance Best Practices

```java
public class ContextPerformance {
    
    @Test
    public void contextMemoryUsage() {
        // ❌ Bad: Creating large contexts repeatedly
        Flux<String> inefficientFlux = Flux.range(1, 1000)
            .flatMap(i -> 
                Mono.just("item-" + i)
                    .contextWrite(Context.of("item", i)
                                        .put("timestamp", System.currentTimeMillis())
                                        .put("metadata", "large-string-" + i)
                                        .put("config", Map.of("key1", "value1", "key2", "value2")))
            );
        
        // ✅ Better: Reuse context where possible
        Context baseContext = Context.of("config", Map.of("key1", "value1", "key2", "value2"))
                                   .put("metadata", "shared-metadata");
        
        Flux<String> efficientFlux = Flux.range(1, 1000)
            .flatMap(i -> 
                Mono.just("item-" + i)
                    .contextWrite(ctx -> ctx.put("item", i)
                                          .put("timestamp", System.currentTimeMillis())))
            .contextWrite(baseContext);
        
        // Measure memory usage (simplified)
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        StepVerifier.create(efficientFlux.take(10))
            .expectNextCount(10)
            .verifyComplete();
        
        System.gc(); // Suggest garbage collection
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        System.out.println("Memory used: " + (endMemory - startMemory) + " bytes");
    }
    
    @Test
    public void contextAccessOptimization() {
        // ❌ Inefficient: Multiple context accesses
        Mono<String> inefficient = Mono.deferContextual(ctx -> {
            String user = ctx.get("user");
            String tenant = ctx.get("tenant");
            String region = ctx.get("region");
            
            return Mono.just(user + "-" + tenant + "-" + region);
        })
        .flatMap(result -> 
            Mono.deferContextual(ctx -> {
                String user = ctx.get("user"); // Duplicate access
                return Mono.just(result + " processed by " + user);
            }))
        .contextWrite(Context.of("user", "alice")
                           .put("tenant", "acme")
                           .put("region", "us-east"));
        
        // ✅ Efficient: Single context access with data passing
        Mono<String> efficient = Mono.deferContextual(ctx -> {
            String user = ctx.get("user");
            String tenant = ctx.get("tenant");
            String region = ctx.get("region");
            
            String result = user + "-" + tenant + "-" + region;
            
            return Mono.just(result + " processed by " + user);
        })
        .contextWrite(Context.of("user", "alice")
                           .put("tenant", "acme")
                           .put("region", "us-east"));
        
        StepVerifier.create(efficient)
            .expectNext("alice-acme-us-east processed by alice")
            .verifyComplete();
    }
    
    @Test
    public void contextSizeOptimization() {
        // Demonstrate context size impact
        Map<String, Object> largeMap = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            largeMap.put("key" + i, "value" + i);
        }
        
        // ❌ Bad: Storing large objects in context
        Context heavyContext = Context.of("largeData", largeMap)
                                    .put("moreData", Collections.nCopies(100, "repeated-string"));
        
        // ✅ Better: Store only necessary references
        Context lightContext = Context.of("dataRef", "cache-key-123")
                                    .put("size", largeMap.size());
        
        Mono<String> heavyMono = Mono.deferContextual(ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = ctx.get("largeData");
            return Mono.just("Heavy context size: " + data.size());
        }).contextWrite(heavyContext);
        
        Mono<String> lightMono = Mono.deferContextual(ctx -> {
            String dataRef = ctx.get("dataRef");
            Integer size = ctx.get("size");
            // In real scenario, would fetch data using dataRef
            return Mono.just("Light context ref: " + dataRef + ", size: " + size);
        }).contextWrite(lightContext);
        
        StepVerifier.create(lightMono)
            .expectNext("Light context ref: cache-key-123, size: 1000")
            .verifyComplete();
    }
}
```

## Key Takeaways

1. **Reactor Context**:
   - Immutable key-value store that propagates downstream
   - Perfect for request-scoped data (user ID, correlation ID, security context)
   - Added with `contextWrite()` at the bottom of chains
   - Accessed with `deferContextual()` or `Mono.deferContextual()`

2. **Context Best Practices**:
   - Keep contexts small and focused
   - Use meaningful keys and provide defaults
   - Cache expensive context computations
   - Don't store large objects directly

3. **Reactive Streams Specification**:
   - Publisher produces elements following demand signals
   - Subscriber receives elements and controls backpressure
   - Subscription manages the lifecycle and demand
   - Processor transforms elements (deprecated in favor of operators)

4. **Contract Rules**:
   - Publishers must respect subscriber demand
   - Subscribers must request positive numbers
   - Only one terminal event (onComplete or onError)
   - No signals after termination

5. **Integration Patterns**:
   - Context middleware for cross-cutting concerns
   - Security context propagation
   - Transaction context management
   - Request tracing and correlation

6. **Testing Context**:
   - Test context propagation explicitly
   - Verify context visibility rules
   - Test error scenarios with context
   - Use StepVerifier with context assertions

## Next Steps

In **Phase 9**, we'll explore Advanced Patterns and Operators, including custom operators, window/buffer operations, transform/compose patterns, and building reusable reactive components. This builds on the context knowledge for creating sophisticated reactive pipelines.