# Phase 2: Project Reactor Fundamentals - Deep Dive

## Setup First

Add Project Reactor to your project:

**Maven:**
```xml
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
    <version>3.5.11</version>
</dependency>

<!-- For testing -->
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <version>3.5.11</version>
    <scope>test</scope>
</dependency>
```

**Gradle:**
```gradle
implementation 'io.projectreactor:reactor-core:3.5.11'
testImplementation 'io.projectreactor:reactor-test:3.5.11'
```

## 2.1 Core Building Blocks

### Understanding Publisher Hierarchy

```
Publisher<T> (Flow API Interface)
    │
    ├── Mono<T>     (0 or 1 element)
    └── Flux<T>     (0 to N elements)
```

### Mono<T> - Deep Dive

**Mono** represents a stream that emits **0 or 1** element, then completes.

#### Visual Representation:
```
Mono with value:     --[value]-->|
Mono empty:          ----------->|
Mono with error:     ----X
                          |
                          └── Error
```

#### Core Creation Methods:

**1. Mono.just() - Immediate Value**
```java
// Create Mono with immediate value
Mono<String> mono = Mono.just("Hello World");

// Subscribe to see the value
mono.subscribe(
    value -> System.out.println("Received: " + value),
    error -> System.err.println("Error: " + error),
    () -> System.out.println("Completed!")
);
// Output: 
// Received: Hello World
// Completed!
```

**2. Mono.empty() - No Value**
```java
Mono<String> empty = Mono.empty();

empty.subscribe(
    value -> System.out.println("Received: " + value),    // Won't be called
    error -> System.err.println("Error: " + error),       // Won't be called
    () -> System.out.println("Completed!")                // Will be called
);
// Output: Completed!
```

**3. Mono.error() - Error Signal**
```java
Mono<String> error = Mono.error(new RuntimeException("Something went wrong"));

error.subscribe(
    value -> System.out.println("Received: " + value),    // Won't be called
    err -> System.err.println("Error: " + err.getMessage()), // Will be called
    () -> System.out.println("Completed!")                // Won't be called
);
// Output: Error: Something went wrong
```

**4. Mono.fromCallable() - Lazy Evaluation**
```java
// This is LAZY - computation happens only when subscribed
Mono<String> lazy = Mono.fromCallable(() -> {
    System.out.println("Computing value...");
    // Simulate expensive computation
    Thread.sleep(1000);
    return "Computed result";
});

System.out.println("Mono created, but computation hasn't started yet");

// Computation starts here
lazy.subscribe(value -> System.out.println("Result: " + value));

// Output:
// Mono created, but computation hasn't started yet
// Computing value...
// Result: Computed result
```

**5. Mono.fromSupplier() - Lazy with Supplier**
```java
Mono<LocalDateTime> timestamp = Mono.fromSupplier(LocalDateTime::now);

// Each subscription gets a fresh timestamp
timestamp.subscribe(time -> System.out.println("Time 1: " + time));
Thread.sleep(1000);
timestamp.subscribe(time -> System.out.println("Time 2: " + time));
```

**6. Mono.defer() - Deferred Creation**
```java
// Defer creation until subscription
Mono<String> deferred = Mono.defer(() -> {
    System.out.println("Creating Mono now...");
    return Mono.just("Deferred value");
});

System.out.println("Deferred Mono created");
deferred.subscribe(System.out::println);

// Output:
// Deferred Mono created
// Creating Mono now...
// Deferred value
```

#### Practical Mono Examples:

**Database Lookup (Single Result)**
```java
public Mono<User> findUserById(String id) {
    return Mono.fromCallable(() -> {
        // Simulate database call
        if ("123".equals(id)) {
            return new User(id, "John Doe");
        }
        return null;
    })
    .cast(User.class)
    .switchIfEmpty(Mono.error(new UserNotFoundException("User not found: " + id)));
}

// Usage
findUserById("123")
    .subscribe(
        user -> System.out.println("Found user: " + user.getName()),
        error -> System.err.println("Error: " + error.getMessage())
    );
```

**HTTP Request (Single Response)**
```java
public Mono<String> fetchUserProfile(String userId) {
    return webClient
        .get()
        .uri("/users/{id}", userId)
        .retrieve()
        .bodyToMono(String.class)
        .doOnNext(response -> System.out.println("Received response"))
        .doOnError(error -> System.err.println("Request failed"));
}
```

### Flux<T> - Deep Dive

**Flux** represents a stream that emits **0 to N** elements, then completes.

#### Visual Representation:
```
Flux with values:    --[1]--[2]--[3]--|
Flux empty:          ------------------|
Flux infinite:       --[1]--[2]--[3]-->
Flux with error:     --[1]--[2]--X
```

#### Core Creation Methods:

**1. Flux.just() - Multiple Values**
```java
Flux<String> names = Flux.just("Alice", "Bob", "Charlie");

names.subscribe(
    name -> System.out.println("Name: " + name),
    error -> System.err.println("Error: " + error),
    () -> System.out.println("All names processed")
);
// Output:
// Name: Alice
// Name: Bob
// Name: Charlie
// All names processed
```

**2. Flux.fromIterable() - From Collections**
```java
List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
Flux<Integer> numberFlux = Flux.fromIterable(numbers);

numberFlux
    .doOnNext(num -> System.out.println("Processing: " + num))
    .subscribe();
```

**3. Flux.range() - Number Sequences**
```java
// Generate numbers from 1 to 10
Flux<Integer> range = Flux.range(1, 10);

range.subscribe(num -> System.out.println("Number: " + num));
```

**4. Flux.interval() - Time-Based Sequences**
```java
// Emit every 1 second (infinite stream)
Flux<Long> interval = Flux.interval(Duration.ofSeconds(1));

// Take only first 5 elements to avoid infinite output
interval
    .take(5)
    .subscribe(tick -> System.out.println("Tick: " + tick));

// Keep main thread alive to see output
Thread.sleep(6000);
```

**5. Flux.generate() - Programmatic Generation**
```java
// Generate Fibonacci sequence
Flux<Integer> fibonacci = Flux.generate(
    () -> new int[]{0, 1},                    // Initial state
    (state, sink) -> {                        // Generator function
        sink.next(state[0]);                  // Emit current value
        int next = state[0] + state[1];       // Calculate next
        state[0] = state[1];                  // Update state
        state[1] = next;
        return state;                         // Return new state
    }
);

fibonacci
    .take(10)
    .subscribe(num -> System.out.println("Fibonacci: " + num));
// Output: 0, 1, 1, 2, 3, 5, 8, 13, 21, 34
```

**6. Flux.create() - Asynchronous Bridge**
```java
// Bridge callback-based APIs to reactive
Flux<String> asyncFlux = Flux.create(emitter -> {
    // Simulate async callbacks
    Timer timer = new Timer();
    AtomicInteger counter = new AtomicInteger(0);
    
    TimerTask task = new TimerTask() {
        @Override
        public void run() {
            int value = counter.incrementAndGet();
            emitter.next("Event " + value);
            
            if (value >= 5) {
                emitter.complete();
                timer.cancel();
            }
        }
    };
    
    timer.schedule(task, 0, 1000); // Every second
    
    // Handle cancellation
    emitter.onDispose(timer::cancel);
});

asyncFlux.subscribe(
    event -> System.out.println("Received: " + event),
    error -> System.err.println("Error: " + error),
    () -> System.out.println("Stream completed")
);

Thread.sleep(6000); // Wait for completion
```

#### Practical Flux Examples:

**Stream Processing**
```java
public Flux<ProcessedData> processDataStream(Flux<RawData> inputStream) {
    return inputStream
        .filter(this::isValid)
        .map(this::transform)
        .buffer(100)  // Process in batches
        .flatMap(this::processBatch)
        .doOnNext(result -> logResult(result));
}
```

**Real-time Events**
```java
public Flux<StockPrice> stockPriceStream(String symbol) {
    return Flux.interval(Duration.ofSeconds(1))
        .map(tick -> fetchCurrentPrice(symbol))
        .distinctUntilChanged()  // Only emit when price changes
        .doOnNext(price -> System.out.println(symbol + ": $" + price));
}
```

## 2.2 Subscription and Lifecycle

### The Subscription Contract

When you call `subscribe()`, this happens:
1. **onSubscribe()**: Subscriber receives a Subscription
2. **request(n)**: Subscriber requests n elements
3. **onNext()**: Publisher sends elements (up to n)
4. **onComplete()** or **onError()**: Stream terminates

#### Basic Subscription Patterns

**1. Simple Subscribe (Fire and Forget)**
```java
Flux.just(1, 2, 3)
    .subscribe(); // Just start the stream, ignore results
```

**2. Subscribe with Consumer**
```java
Flux.just(1, 2, 3)
    .subscribe(value -> System.out.println("Got: " + value));
```

**3. Subscribe with Error Handler**
```java
Flux.just(1, 2, 0, 4)
    .map(i -> 10 / i)  // Will cause division by zero
    .subscribe(
        value -> System.out.println("Result: " + value),
        error -> System.err.println("Error: " + error.getMessage())
    );
```

**4. Subscribe with Completion Handler**
```java
Flux.range(1, 3)
    .subscribe(
        value -> System.out.println("Value: " + value),
        error -> System.err.println("Error: " + error),
        () -> System.out.println("Stream completed!")
    );
```

**5. Full Subscription Control**
```java
Flux.range(1, 100)
    .subscribe(new BaseSubscriber<Integer>() {
        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            System.out.println("Subscribed!");
            request(5); // Request first 5 elements
        }
        
        @Override
        protected void hookOnNext(Integer value) {
            System.out.println("Received: " + value);
            if (value % 5 == 0) {
                request(5); // Request next 5 elements
            }
        }
        
        @Override
        protected void hookOnComplete() {
            System.out.println("Completed!");
        }
        
        @Override
        protected void hookOnError(Throwable throwable) {
            System.err.println("Error: " + throwable);
        }
    });
```

### Disposable Interface

**Managing Subscriptions:**
```java
// Keep reference to subscription
Disposable subscription = Flux.interval(Duration.ofSeconds(1))
    .subscribe(tick -> System.out.println("Tick: " + tick));

// Cancel after 5 seconds
Thread.sleep(5000);
subscription.dispose();

System.out.println("Is disposed: " + subscription.isDisposed());
```

**Composite Disposable (Managing Multiple Subscriptions):**
```java
import reactor.core.Disposable;
import reactor.core.Disposables;

Disposable.Composite disposables = Disposables.composite();

// Add multiple subscriptions
disposables.add(
    Flux.interval(Duration.ofSeconds(1))
        .subscribe(tick -> System.out.println("Timer 1: " + tick))
);

disposables.add(
    Flux.interval(Duration.ofMillis(500))
        .subscribe(tick -> System.out.println("Timer 2: " + tick))
);

// Dispose all at once
Thread.sleep(3000);
disposables.dispose();
```

### Resource Management

**Using try-with-resources pattern:**
```java
public void processWithAutoCleanup() {
    try (DisposableResource resource = new DisposableResource()) {
        Flux.range(1, 10)
            .doOnNext(resource::process)
            .subscribe();
    } // Resource automatically disposed
}

class DisposableResource implements AutoCloseable {
    public void process(Integer value) {
        System.out.println("Processing: " + value);
    }
    
    @Override
    public void close() {
        System.out.println("Resource cleaned up");
    }
}
```

## 2.3 Terminal vs Non-Terminal Operations

### Non-Terminal Operations (Return Publisher)
These transform the stream but don't trigger subscription:

```java
Flux<String> pipeline = Flux.just("hello", "world")
    .map(String::toUpperCase)        // Non-terminal: returns Flux<String>
    .filter(s -> s.length() > 4)     // Non-terminal: returns Flux<String>
    .take(1);                        // Non-terminal: returns Flux<String>

// Nothing happens yet - no subscription!

pipeline.subscribe(System.out::println); // Terminal: triggers execution
```

### Terminal Operations (Trigger Subscription)

**1. subscribe() variants**
```java
// Various subscribe options
flux.subscribe();                                    // Fire and forget
flux.subscribe(System.out::println);                // With consumer
flux.subscribe(System.out::println, System.err::println); // With error handler
```

**2. block() - Blocking Operations**
```java
// Block until first element (Mono)
String result = Mono.just("Hello").block();
System.out.println(result); // "Hello"

// Block until completion (Flux) - returns last element
Integer last = Flux.range(1, 5).blockLast();
System.out.println(last); // 5

// Block until completion with timeout
String timedResult = Mono.delay(Duration.ofSeconds(2))
    .map(tick -> "Done")
    .block(Duration.ofSeconds(3)); // Will succeed

// This will throw TimeoutException
try {
    Mono.delay(Duration.ofSeconds(5))
        .map(tick -> "Done")
        .block(Duration.ofSeconds(1)); // Timeout!
} catch (Exception e) {
    System.err.println("Timeout: " + e.getMessage());
}
```

**⚠️ Important: Avoid block() in reactive applications!**

## 2.4 Cold vs Hot Publishers

### Cold Publishers (Default Behavior)

**Characteristics:**
- Start emitting only when subscribed
- Each subscriber gets its own independent stream
- Data is generated per subscription

```java
// Cold publisher - each subscription is independent
Mono<String> cold = Mono.fromCallable(() -> {
    System.out.println("Generating data...");
    return "Data at " + LocalTime.now();
});

System.out.println("=== First Subscription ===");
cold.subscribe(data -> System.out.println("Subscriber 1: " + data));

Thread.sleep(2000);

System.out.println("=== Second Subscription ===");
cold.subscribe(data -> System.out.println("Subscriber 2: " + data));

// Output shows two different timestamps:
// === First Subscription ===
// Generating data...
// Subscriber 1: Data at 10:30:15.123
// === Second Subscription ===
// Generating data...
// Subscriber 2: Data at 10:30:17.456
```

**Cold Flux Example:**
```java
Flux<Integer> coldFlux = Flux.range(1, 3)
    .doOnSubscribe(sub -> System.out.println("Subscribed!"))
    .doOnNext(item -> System.out.println("Emitting: " + item));

System.out.println("=== Subscriber 1 ===");
coldFlux.subscribe(item -> System.out.println("Sub1 received: " + item));

System.out.println("=== Subscriber 2 ===");
coldFlux.subscribe(item -> System.out.println("Sub2 received: " + item));

// Each subscriber gets the full sequence independently
```

### Hot Publishers (Shared Streams)

We'll cover hot publishers in detail in Phase 7, but here's a preview:

```java
// Convert cold to hot with share()
Flux<String> hot = Flux.interval(Duration.ofSeconds(1))
    .map(tick -> "Tick " + tick)
    .share(); // Makes it hot

// Start the stream
hot.subscribe(data -> System.out.println("Early subscriber: " + data));

Thread.sleep(3000); // Let some events pass

// Late subscriber only gets future events
hot.subscribe(data -> System.out.println("Late subscriber: " + data));

Thread.sleep(3000);
```

## Practical Exercises - Week 3

### Exercise 1: Basic Mono Operations
```java
public class MonoExercises {
    
    // Exercise 1a: Create a method that returns user data
    public Mono<User> getUserById(String id) {
        // TODO: Return Mono with user if id exists, empty if not
        // Use a Map to simulate database
    }
    
    // Exercise 1b: Create a method that validates user
    public Mono<User> validateUser(User user) {
        // TODO: Return user if valid, error if invalid
        // Validation: name not null and email contains @
    }
    
    // Exercise 1c: Chain the operations
    public Mono<String> getUserEmail(String id) {
        // TODO: Get user by id, validate, then return email
        // Handle all error cases
    }
}
```

### Exercise 2: Basic Flux Operations
```java
public class FluxExercises {
    
    // Exercise 2a: Process a list of numbers
    public Flux<Integer> processNumbers(List<Integer> numbers) {
        // TODO: Filter even numbers, multiply by 2, take first 5
    }
    
    // Exercise 2b: Generate custom sequence
    public Flux<String> generateMessages() {
        // TODO: Generate "Message 1", "Message 2", etc. every 500ms
        // Stop after 10 messages
    }
    
    // Exercise 2c: Handle errors gracefully
    public Flux<Integer> divideNumbers(List<Integer> numbers) {
        // TODO: Divide each number by 2
        // Handle any division errors by skipping the problematic number
    }
}
```

### Exercise 3: Subscription Management
```java
public class SubscriptionExercises {
    
    public void demonstrateSubscriptionControl() {
        // TODO: Create a Flux that emits 100 numbers
        // Subscribe with custom BaseSubscriber that:
        // 1. Requests 10 items at a time
        // 2. Prints progress every 10 items
        // 3. Cancels subscription after 50 items
    }
}
```

## Practical Exercises - Week 4

### Exercise 4: Real-world Scenarios

**4a: User Service**
```java
public class UserService {
    private Map<String, User> users = new HashMap<>();
    
    public Mono<User> createUser(User user) {
        // TODO: Validate user, save, return created user or error
    }
    
    public Flux<User> getAllUsers() {
        // TODO: Return all users as Flux
    }
    
    public Mono<User> updateUser(String id, User updates) {
        // TODO: Find user, apply updates, save, return updated user
    }
}
```

**4b: Data Processing Pipeline**
```java
public class DataProcessor {
    
    public Flux<ProcessedData> processDataFile(String filename) {
        // TODO: 
        // 1. Read file line by line (simulate with Flux.fromIterable)
        // 2. Parse each line to RawData
        // 3. Validate data
        // 4. Transform to ProcessedData
        // 5. Handle errors by logging and continuing
    }
}
```

### Exercise 5: Performance Comparison

Create two versions of a data processing system:
1. **Blocking version**: Traditional Java with streams
2. **Reactive version**: Using Mono/Flux

Compare performance with:
- 1,000 items
- 10,000 items  
- Simulated network delays

## Key Concepts Summary

### Mono<T>
✅ **0 or 1 element** - perfect for single values  
✅ **Lazy evaluation** - computation starts only on subscription  
✅ **Composable** - chain operations declaratively  
✅ **Error handling** - built-in error propagation  

### Flux<T>
✅ **0 to N elements** - perfect for streams of data  
✅ **Time-based operations** - intervals, delays, timeouts  
✅ **Backpressure ready** - handles fast producers automatically  
✅ **Infinite streams** - can represent endless data sources  

### Subscriptions
✅ **Lazy by nature** - nothing happens until subscribe()  
✅ **Disposable** - can be cancelled to free resources  
✅ **Flexible** - from simple fire-and-forget to full control  
✅ **Resource safe** - proper cleanup mechanisms  

### Cold vs Hot
✅ **Cold** - independent stream per subscriber (default)  
✅ **Hot** - shared stream across subscribers  
✅ **Conversion** - can transform cold to hot with operators  

## Next Phase Preview

In **Phase 3**, we'll explore:
- Essential operators (map, filter, flatMap, etc.)
- Error handling strategies
- Combining multiple publishers
- Debugging and logging techniques

## Key Learning Path for Phase 2:
Week 3 Focus:

- Start with Mono - understand the "0 or 1" concept thoroughly
- Practice all the creation methods (just, fromCallable, defer, etc.)
- Master the subscription patterns - this is crucial for everything that follows
- Work through Exercise 1 and 2

Week 4 Focus:

- Deep dive into Flux - understand streams of data
- Practice time-based operations with interval
- Master the cold publisher concept (hot publishers come later)
- Build the real-world exercises (User Service, Data Processor)

## Critical Understanding Points:

- Lazy Nature: Nothing happens until you subscribe - this is fundamental
- Cold Publishers: Each subscription is independent by default
- Resource Management: Always consider how to clean up subscriptions
- Terminal vs Non-Terminal: Know which operations trigger execution