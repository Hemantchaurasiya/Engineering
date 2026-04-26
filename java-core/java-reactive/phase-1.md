# Phase 1: Reactive Programming Foundations - Deep Dive

## 1.1 Understanding Reactive Programming Concepts

### What is Reactive Programming?

Reactive programming is a **declarative programming paradigm** concerned with **data streams** and the **propagation of change**. Think of it as programming with **asynchronous data streams**.

#### Traditional vs Reactive Programming

**Traditional (Imperative) Approach:**
```java
// Traditional synchronous approach
List<String> names = Arrays.asList("Alice", "Bob", "Charlie");
List<String> result = new ArrayList<>();

for (String name : names) {
    if (name.length() > 3) {
        result.add(name.toUpperCase());
    }
}
System.out.println(result); // [ALICE, CHARLIE]
```

**Reactive Approach (conceptual):**
```java
// Reactive approach - we'll implement this properly later
Flux.fromIterable(Arrays.asList("Alice", "Bob", "Charlie"))
    .filter(name -> name.length() > 3)
    .map(String::toUpperCase)
    .subscribe(System.out::println);
```

### Core Concepts Deep Dive

#### 1. Asynchronous Data Streams

**What are streams?**
- A **sequence of ongoing events** ordered in time
- Can emit three types of events: **values**, **errors**, or **completion signal**
- Everything is a stream: variables, user inputs, properties, caches, data structures, etc.

**Visual representation:**
```
Stream of clicks:
--c----c--c----c------c-->
  |    |  |    |      |
  |    |  |    |      +-- Click events
  |    |  |    +--------- Time
  |    |  +-------------- Each 'c' is a click
  |    +----------------- Streams flow over time
  +---------------------- Stream starts here
                     --> Stream continues (potentially infinite)
```

**Key characteristics:**
- **Temporal**: Events happen over time
- **Async**: Events can occur at any time
- **Observable**: You can listen/subscribe to streams
- **Composable**: Streams can be combined and transformed

#### 2. Event-Driven Programming

Traditional programming is often **request-response** based:
```java
// Traditional: I ask, then wait for answer
String result = service.fetchData(); // Blocks until response
processResult(result);
```

Reactive programming is **event-driven**:
```java
// Reactive: I register interest, then react when events arrive
service.fetchDataStream()
    .subscribe(result -> processResult(result)); // Non-blocking
```

#### 3. The Observer Pattern at Scale

Reactive programming is essentially the **Observer pattern** applied to asynchronous data streams.

**Classic Observer Pattern:**
```java
// Traditional Observer - synchronous, single values
interface Observer {
    void update(String data);
}

class Subject {
    private List<Observer> observers = new ArrayList<>();
    
    public void addObserver(Observer observer) {
        observers.add(observer);
    }
    
    public void notifyObservers(String data) {
        for (Observer observer : observers) {
            observer.update(data); // Synchronous call
        }
    }
}
```

**Reactive Observer Pattern:**
- **Asynchronous**: Notifications happen asynchronously
- **Multiple values**: Can emit multiple values over time
- **Error handling**: Built-in error propagation
- **Completion**: Signals when stream is complete
- **Backpressure**: Handles fast producers, slow consumers

#### 4. Push vs Pull-Based Systems

**Pull-based (Traditional):**
```java
// Consumer controls when to get data
Iterator<String> iterator = list.iterator();
while (iterator.hasNext()) {
    String item = iterator.next(); // Consumer PULLS data
    process(item);
}
```

**Push-based (Reactive):**
```java
// Producer controls when to send data
publisher.subscribe(new Subscriber<String>() {
    @Override
    public void onNext(String item) {
        process(item); // Producer PUSHES data to consumer
    }
    // ... other methods
});
```

## 1.2 The Reactive Manifesto

The Reactive Manifesto defines four key principles for reactive systems:

### 1. Responsive
**Definition**: The system responds in a timely manner if possible.

**What it means:**
- Quick and consistent response times
- Problems are detected quickly and dealt with effectively
- User experience is not degraded under load

**Example scenario:**
```java
// Non-responsive approach
public String fetchUserData(String userId) {
    // This might take 5 seconds if database is slow
    return database.blockingQuery("SELECT * FROM users WHERE id = " + userId);
}

// Responsive approach
public Mono<String> fetchUserData(String userId) {
    return database.query("SELECT * FROM users WHERE id = " + userId)
        .timeout(Duration.ofSeconds(2)) // Respond with timeout if too slow
        .onErrorReturn("Default user data"); // Graceful degradation
}
```

### 2. Resilient
**Definition**: The system stays responsive in the face of failure.

**Key strategies:**
- **Isolation**: Failure in one component doesn't cascade
- **Replication**: Multiple instances of critical components
- **Supervision**: Failed components are restored by supervisors
- **Containment**: Failures are contained within components

**Example:**
```java
// Resilient service call
public Flux<String> fetchDataWithResilience() {
    return webClient.get()
        .uri("/api/data")
        .retrieve()
        .bodyToFlux(String.class)
        .retry(3) // Automatic retry on failure
        .onErrorResume(throwable -> {
            // Fallback to cached data or alternative service
            return Flux.just("Fallback data");
        });
}
```

### 3. Elastic
**Definition**: The system stays responsive under varying workload.

**Key characteristics:**
- Scale up/down based on demand
- No contention points or central bottlenecks
- Ability to shard or replicate components
- Predictable scaling algorithms

**Example:**
```java
// Elastic processing with backpressure
public Flux<ProcessedData> processLargeDataset(Flux<RawData> input) {
    return input
        .publishOn(Schedulers.parallel(), 256) // Bounded buffer for elasticity
        .flatMap(this::processData, 8) // Limit concurrent processing
        .onBackpressureBuffer(1000, // Handle bursts
            data -> log.warn("Dropping data due to backpressure"));
}
```

### 4. Message Driven
**Definition**: Reactive systems rely on asynchronous message-passing.

**Benefits:**
- **Loose coupling**: Components interact via messages
- **Location transparency**: Components can be local or remote
- **Non-blocking**: Message passing is asynchronous
- **Flow control**: Built-in backpressure management

**Example:**
```java
// Message-driven communication
public class OrderProcessor {
    
    // Receives order messages asynchronously
    public Flux<OrderResult> processOrders(Flux<Order> orderStream) {
        return orderStream
            .flatMap(this::validateOrder)
            .flatMap(this::processPayment)
            .flatMap(this::updateInventory)
            .doOnNext(result -> notificationService.send(result));
    }
}
```

## 1.3 Key Principles Deep Dive

### 1. Non-blocking I/O

**Blocking I/O (Traditional):**
```java
// Thread is blocked waiting for I/O
String response = httpClient.get("http://api.example.com/data");
// Thread can't do anything else until response arrives
processResponse(response);
```

**Problems with blocking:**
- **Thread waste**: Threads sit idle waiting
- **Poor scalability**: Need many threads for many concurrent operations
- **Resource consumption**: Each thread consumes memory (~1MB stack)

**Non-blocking I/O (Reactive):**
```java
// Thread is NOT blocked
httpClient.get("http://api.example.com/data")
    .subscribe(response -> processResponse(response));
// Thread is free to handle other requests immediately
```

**Benefits:**
- **Better resource utilization**: Fewer threads needed
- **Higher scalability**: Handle thousands of concurrent operations
- **Lower latency**: No waiting for thread scheduling

### 2. Backpressure Handling

**The Problem:**
- Producer creates data faster than consumer can process
- Without backpressure, system can run out of memory
- Traditional solution: blocking (defeats non-blocking benefits)

**Visual representation:**
```
Fast Producer:    [1][2][3][4][5][6][7][8][9][10]...
                    \  \  \  \  \  \  \  \  \  \
Slow Consumer:      \  \  \  \  \  \  \  \  \  \___Processing [1]
                     \  \  \  \  \  \  \  \  \
Queue fills up:      [2][3][4][5][6][7][8][9][10]... OVERFLOW!
```

**Reactive Solutions:**
1. **Buffer**: Store excess items (with limits)
2. **Drop**: Discard items when overwhelmed
3. **Sample**: Take only latest items
4. **Request**: Consumer controls rate

```java
// Example backpressure strategies (we'll implement these later)
fastProducer
    .onBackpressureBuffer(1000)     // Buffer up to 1000 items
    .onBackpressureDrop()           // Drop items if overwhelmed
    .onBackpressureLatest()         // Keep only latest item
```

### 3. Declarative Programming Style

**Imperative (how to do it):**
```java
List<String> result = new ArrayList<>();
for (String name : names) {
    if (name.startsWith("A")) {
        String upper = name.toUpperCase();
        result.add(upper);
    }
}
```

**Declarative (what to do):**
```java
names.stream()
    .filter(name -> name.startsWith("A"))  // What: filter names starting with A
    .map(String::toUpperCase)              // What: convert to uppercase
    .collect(toList());                    // What: collect results
```

**Benefits:**
- **More readable**: Express intent clearly
- **Less error-prone**: Less manual state management
- **Composable**: Easy to combine operations
- **Testable**: Each operation can be tested independently

### 4. Functional Composition

**Building complex operations from simple ones:**

```java
// Individual functions
Function<String, String> toUpperCase = String::toUpperCase;
Function<String, Boolean> isLongName = name -> name.length() > 5;
Function<String, String> addPrefix = name -> "Mr. " + name;

// Compose them together
names.stream()
    .filter(isLongName::apply)
    .map(toUpperCase)
    .map(addPrefix)
    .forEach(System.out::println);
```

**In reactive programming:**
```java
// Each operator is a function that transforms the stream
dataStream
    .filter(this::isValid)           // Function: Stream<T> -> Stream<T>
    .map(this::transform)            // Function: Stream<T> -> Stream<U>
    .flatMap(this::expandData)       // Function: Stream<U> -> Stream<V>
    .subscribe(this::processResult); // Function: Stream<V> -> void
```

## 1.4 Java Reactive Landscape

### Java 9 Flow API

Java 9 introduced the **Flow API** (`java.util.concurrent.Flow`) - Java's standard reactive streams specification.

#### Core Interfaces:

**1. Publisher<T>**
```java
@FunctionalInterface
public interface Publisher<T> {
    // Subscribe a subscriber to this publisher
    void subscribe(Subscriber<? super T> subscriber);
}
```

**2. Subscriber<T>**
```java
public interface Subscriber<T> {
    // Called when subscription is established
    void onSubscribe(Subscription subscription);
    
    // Called for each item
    void onNext(T item);
    
    // Called on error (terminal)
    void onError(Throwable throwable);
    
    // Called when complete (terminal)
    void onComplete();
}
```

**3. Subscription**
```java
public interface Subscription {
    // Request n items from upstream
    void request(long n);
    
    // Cancel the subscription
    void cancel();
}
```

**4. Processor<T,R>**
```java
// Acts as both Publisher and Subscriber
public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
}
```

#### Flow API Example:
```java
// Simple Flow API example
public class SimplePublisher implements Flow.Publisher<String> {
    private final List<String> items;
    
    public SimplePublisher(List<String> items) {
        this.items = items;
    }
    
    @Override
    public void subscribe(Flow.Subscriber<? super String> subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {
            private int index = 0;
            private boolean cancelled = false;
            
            @Override
            public void request(long n) {
                if (cancelled) return;
                
                for (int i = 0; i < n && index < items.size(); i++) {
                    subscriber.onNext(items.get(index++));
                }
                
                if (index >= items.size()) {
                    subscriber.onComplete();
                }
            }
            
            @Override
            public void cancel() {
                cancelled = true;
            }
        });
    }
}

// Usage
List<String> data = Arrays.asList("A", "B", "C");
SimplePublisher publisher = new SimplePublisher(data);

publisher.subscribe(new Flow.Subscriber<String>() {
    private Flow.Subscription subscription;
    
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1); // Request first item
    }
    
    @Override
    public void onNext(String item) {
        System.out.println("Received: " + item);
        subscription.request(1); // Request next item
    }
    
    @Override
    public void onError(Throwable throwable) {
        System.err.println("Error: " + throwable);
    }
    
    @Override
    public void onComplete() {
        System.out.println("Completed!");
    }
});
```

### Popular Reactive Libraries

#### 1. Project Reactor (Spring's choice)
- **Mono<T>**: 0 or 1 element
- **Flux<T>**: 0 to N elements
- Excellent Spring integration
- Great performance and features

#### 2. RxJava (Netflix's creation)
- **Observable**: 0 to N elements (no backpressure)
- **Flowable**: 0 to N elements (with backpressure)
- **Single**: Exactly 1 element
- **Maybe**: 0 or 1 element
- **Completable**: No elements, just completion/error

#### 3. Akka Streams (Lightbend)
- Actor-based reactive streams
- Excellent for complex stream processing
- Built-in graph DSL for complex topologies

#### 4. Vert.x (Eclipse Foundation)
- Event-driven application framework
- Built-in reactive streams support
- Great for microservices

## Phase 1 Practice Exercises

### Exercise 1: Understanding Concepts
**Task**: Write a simple explanation in your own words for each concept:
- Reactive programming
- Event-driven vs request-response
- Push vs pull systems
- Backpressure

### Exercise 2: Observer Pattern Implementation
**Task**: Implement a simple reactive-like observer system:
```java
// Create a simple event emitter that can:
// 1. Accept multiple observers
// 2. Emit events asynchronously
// 3. Handle errors gracefully
// 4. Notify observers of completion
```

### Exercise 3: Flow API Practice
**Task**: Implement a publisher that emits numbers 1-10 with a delay, and a subscriber that processes them.

### Exercise 4: Blocking vs Non-blocking
**Task**: Write two versions of a data processing pipeline:
1. Blocking version using traditional Java
2. Non-blocking version using CompletableFuture

Compare performance with multiple concurrent operations.

## Assessment Questions

1. **What is the key difference between reactive and imperative programming?**

2. **Explain the four principles of the Reactive Manifesto with examples.**

3. **What is backpressure and why is it important?**

4. **How does the Flow API support the reactive streams specification?**

5. **When would you choose reactive programming over traditional approaches?**

## Next Steps

Once you've mastered these foundations:
- Practice implementing simple Flow API publishers/subscribers
- Experiment with different backpressure scenarios
- Try converting imperative code to reactive thinking
- Set up your development environment for Project Reactor

**Ready for Phase 2?** We'll dive into Project Reactor's Mono and Flux, where you'll start building real reactive applications!

## Key Takeaways

✅ **Reactive programming** = Programming with asynchronous data streams  
✅ **Event-driven** approach provides better scalability than request-response  
✅ **Non-blocking I/O** enables better resource utilization  
✅ **Backpressure** prevents system overload  
✅ **Reactive Manifesto** provides design principles for reactive systems  
✅ **Flow API** is Java's standard for reactive streams  
✅ **Multiple libraries** provide different approaches to reactive programming