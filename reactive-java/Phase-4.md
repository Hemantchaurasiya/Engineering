# Phase 4: Schedulers and Threading - Deep Dive

## Overview

Understanding schedulers is crucial for building high-performance reactive applications. This phase covers how to control threading, optimize performance, and avoid common threading pitfalls in reactive streams.

## 4.1 Understanding Schedulers - The Foundation

### What are Schedulers?

**Schedulers** in Project Reactor are abstractions that control:
- **Where** operations execute (which thread/thread pool)
- **When** operations are scheduled
- **How** concurrency is managed

**Key Concept**: By default, reactive streams execute on the **calling thread** (the thread that called `subscribe()`).

```java
// Demonstration of default behavior
public static void demonstrateDefaultThreading() {
    System.out.println("Main thread: " + Thread.currentThread().getName());
    
    Flux.just("A", "B", "C")
        .map(item -> {
            System.out.println("Processing " + item + " on: " + 
                Thread.currentThread().getName());
            return item.toLowerCase();
        })
        .subscribe(result -> {
            System.out.println("Received " + result + " on: " + 
                Thread.currentThread().getName());
        });
    
    // Output shows everything runs on main thread
}
```

## 4.2 Types of Schedulers

### 1. Schedulers.immediate()
**Purpose**: Execute immediately on current thread (no thread switching).

```java
// Immediate scheduler - no thread switching
Flux.just(1, 2, 3)
    .publishOn(Schedulers.immediate())
    .map(n -> {
        System.out.println("Processing " + n + " on: " + 
            Thread.currentThread().getName());
        return n * 2;
    })
    .subscribe();

// Useful for testing or when you want to ensure synchronous execution
```

### 2. Schedulers.single()
**Purpose**: Single dedicated thread for all operations.

```java
// Single scheduler - all operations on one dedicated thread
Scheduler singleScheduler = Schedulers.single();

Flux.range(1, 5)
    .publishOn(singleScheduler)
    .map(n -> {
        System.out.println("Task " + n + " on: " + 
            Thread.currentThread().getName());
        // Simulate work
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        return n * 2;
    })
    .subscribe(System.out::println);

// Don't forget to dispose when done
singleScheduler.dispose();
```

**Use Cases:**
- Sequential processing requirements
- Stateful operations that need thread safety
- I/O operations that don't benefit from parallelism

### 3. Schedulers.boundedElastic()
**Purpose**: Elastic thread pool optimized for blocking I/O operations.

```java
// Bounded elastic - good for blocking I/O
Flux.range(1, 10)
    .publishOn(Schedulers.boundedElastic())
    .map(n -> {
        System.out.println("Processing " + n + " on: " + 
            Thread.currentThread().getName());
        
        // Simulate blocking I/O (database call, file read, etc.)
        try {
            Thread.sleep(1000); // This is OK on boundedElastic
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return "Result: " + n;
    })
    .subscribe(System.out::println);

Thread.sleep(12000); // Wait for completion
```

**Characteristics:**
- **Bounded**: Limited number of threads (default: 10 × CPU cores)
- **Elastic**: Creates threads on demand, removes idle ones
- **Optimized for blocking**: Designed for I/O-bound operations
- **TTL**: Threads removed after 60 seconds of inactivity

**Configuration:**
```java
// Custom bounded elastic scheduler
Scheduler customElastic = Schedulers.newBoundedElastic(
    50,              // Thread cap
    Integer.MAX_VALUE, // Queue size  
    "custom-elastic", // Thread name prefix
    300,             // TTL in seconds
    true             // Daemon threads
);
```

### 4. Schedulers.parallel()
**Purpose**: Fixed parallel thread pool optimized for CPU-intensive operations.

```java
// Parallel scheduler - good for CPU-intensive work
Flux.range(1, 20)
    .publishOn(Schedulers.parallel())
    .map(n -> {
        System.out.println("Processing " + n + " on: " + 
            Thread.currentThread().getName());
        
        // CPU-intensive work
        long result = fibonacci(n + 30);
        return n + " -> " + result;
    })
    .subscribe(System.out::println);

// Fibonacci calculation (CPU intensive)
private static long fibonacci(int n) {
    if (n <= 1) return n;
    return fibonacci(n - 1) + fibonacci(n - 2);
}
```

**Characteristics:**
- **Fixed size**: Default = number of CPU cores
- **Non-blocking optimized**: Best for CPU-bound operations
- **Work-stealing**: Efficient load distribution

### 5. Custom Schedulers

```java
// Custom thread pool scheduler
ExecutorService customExecutor = Executors.newFixedThreadPool(4, r -> {
    Thread t = new Thread(r, "custom-worker");
    t.setDaemon(true);
    return t;
});

Scheduler customScheduler = Schedulers.fromExecutor(customExecutor);

// Use custom scheduler
Flux.range(1, 10)
    .publishOn(customScheduler)
    .map(n -> {
        System.out.println("Custom processing " + n + " on: " + 
            Thread.currentThread().getName());
        return n * 2;
    })
    .subscribe();

// Cleanup
customScheduler.dispose();
customExecutor.shutdown();
```

## 4.3 subscribeOn() vs publishOn()

This is **THE MOST IMPORTANT** concept in reactive threading.

### subscribeOn() - Controls Subscription Thread

**Purpose**: Determines which scheduler the **entire subscription chain** runs on.

```java
// subscribeOn affects the ENTIRE chain
Flux<String> flux = Flux.just("A", "B", "C")
    .doOnSubscribe(s -> System.out.println("doOnSubscribe: " + 
        Thread.currentThread().getName()))
    .map(item -> {
        System.out.println("map: " + item + " on " + 
            Thread.currentThread().getName());
        return item.toLowerCase();
    })
    .subscribeOn(Schedulers.boundedElastic()); // Affects everything above

flux.subscribe(result -> System.out.println("subscribe: " + result + 
    " on " + Thread.currentThread().getName()));

Thread.sleep(1000);
```

**Key Rules for subscribeOn():**
- **Upstream effect**: Affects all operators **above** it in the chain
- **First wins**: Only the **first** `subscribeOn()` in the chain has effect
- **Subscription source**: Controls where the subscription starts

```java
// Demonstrating "first subscribeOn wins"
Flux.just("A", "B", "C")
    .subscribeOn(Schedulers.single())        // This one wins
    .map(String::toLowerCase)
    .subscribeOn(Schedulers.parallel())      // This is ignored
    .subscribe();
```

### publishOn() - Controls Emission Thread

**Purpose**: Changes which scheduler **downstream operators** run on.

```java
// publishOn affects downstream operations
Flux.just("A", "B", "C")
    .map(item -> {
        System.out.println("Before publishOn - map: " + item + " on " + 
            Thread.currentThread().getName());
        return item;
    })
    .publishOn(Schedulers.parallel())  // Switch to parallel scheduler
    .map(item -> {
        System.out.println("After publishOn - map: " + item + " on " + 
            Thread.currentThread().getName());
        return item.toLowerCase();
    })
    .subscribe(result -> System.out.println("subscribe: " + result + 
        " on " + Thread.currentThread().getName()));

Thread.sleep(1000);
```

**Key Rules for publishOn():**
- **Downstream effect**: Affects all operators **below** it
- **Multiple allowed**: Can use multiple `publishOn()` calls
- **Thread handoff**: Creates a queue and hands off elements

### Visual Representation

```
subscribeOn(Scheduler.A):
    Subscription ←─ Scheduler A ─← map() ←─ Scheduler A ─← filter() ←─ Scheduler A

publishOn(Scheduler.B):
    source() ─→ Scheduler A ─→ map() ─→ [Queue] ─→ Scheduler B ─→ filter()
```

## 4.4 Complex Threading Examples

### Example 1: Mixed I/O and CPU Operations

```java
public Flux<ProcessedData> processDataPipeline(Flux<String> input) {
    return input
        // Start on calling thread
        .doOnNext(item -> System.out.println("Input: " + item + 
            " on " + Thread.currentThread().getName()))
        
        // Switch to boundedElastic for I/O operations
        .publishOn(Schedulers.boundedElastic())
        .flatMap(this::fetchDataFromDatabase) // I/O operation
        .doOnNext(data -> System.out.println("After DB: " + data + 
            " on " + Thread.currentThread().getName()))
        
        // Switch to parallel for CPU-intensive work
        .publishOn(Schedulers.parallel())
        .map(this::performHeavyComputation) // CPU operation
        .doOnNext(result -> System.out.println("After CPU: " + result + 
            " on " + Thread.currentThread().getName()))
        
        // Back to boundedElastic for final I/O
        .publishOn(Schedulers.boundedElastic())
        .flatMap(this::saveToCache); // I/O operation
}

private Mono<DatabaseResult> fetchDataFromDatabase(String input) {
    return Mono.fromCallable(() -> {
        // Simulate database call
        Thread.sleep(100);
        return new DatabaseResult(input + "_from_db");
    });
}

private ComputationResult performHeavyComputation(DatabaseResult data) {
    // Simulate CPU-intensive work
    long result = 0;
    for (int i = 0; i < 1000000; i++) {
        result += i;
    }
    return new ComputationResult(data.getValue() + "_computed");
}

private Mono<ProcessedData> saveToCache(ComputationResult result) {
    return Mono.fromCallable(() -> {
        // Simulate cache save
        Thread.sleep(50);
        return new ProcessedData(result.getValue() + "_cached");
    });
}
```

### Example 2: Parallel Processing with Coordination

```java
public Flux<AggregatedResult> parallelProcessWithCoordination(
        Flux<WorkItem> workItems) {
    
    return workItems
        .subscribeOn(Schedulers.boundedElastic()) // Start subscription on I/O thread
        
        // Parallel processing
        .parallel(4) // Split into 4 parallel rails
        .runOn(Schedulers.parallel()) // Each rail runs on parallel scheduler
        .map(item -> {
            System.out.println("Processing " + item.getId() + 
                " on " + Thread.currentThread().getName());
            return processWorkItem(item);
        })
        
        // Coordination point - back to sequential
        .sequential()
        .publishOn(Schedulers.single()) // Single thread for aggregation
        
        // Aggregate results (stateful operation)
        .buffer(10) // Collect 10 items
        .map(this::aggregateResults)
        .doOnNext(result -> System.out.println("Aggregated on: " + 
            Thread.currentThread().getName()));
}

private ProcessedItem processWorkItem(WorkItem item) {
    // Simulate CPU work
    try { Thread.sleep(100); } catch (InterruptedException e) {}
    return new ProcessedItem(item.getId(), item.getData().toUpperCase());
}

private AggregatedResult aggregateResults(List<ProcessedItem> items) {
    // Aggregate logic
    return new AggregatedResult(items.size(), 
        items.stream().map(ProcessedItem::getId).collect(toList()));
}
```

### Example 3: Event-Driven Processing with Threading

```java
public class EventProcessor {
    private final Scheduler ioScheduler = Schedulers.boundedElastic();
    private final Scheduler cpuScheduler = Schedulers.parallel();
    private final Scheduler coordinationScheduler = Schedulers.single();
    
    public Flux<ProcessingResult> processEvents(Flux<Event> events) {
        return events
            // Receive events on any thread
            .doOnNext(event -> log.info("Received event: {} on {}", 
                event.getId(), Thread.currentThread().getName()))
            
            // Group by event type
            .groupBy(Event::getType)
            
            // Process each group independently
            .flatMap(group -> 
                group
                    // I/O operations for enrichment
                    .publishOn(ioScheduler)
                    .flatMap(this::enrichEvent)
                    
                    // CPU-intensive validation
                    .publishOn(cpuScheduler)
                    .filter(this::validateEvent)
                    .map(this::transformEvent)
                    
                    // Coordination for persistence
                    .publishOn(coordinationScheduler)
                    .flatMap(this::persistEvent)
                    
                    // Buffer and batch process
                    .buffer(Duration.ofSeconds(5), 100)
                    .filter(batch -> !batch.isEmpty())
                    .flatMap(this::processBatch)
            );
    }
    
    private Mono<EnrichedEvent> enrichEvent(Event event) {
        return Mono.fromCallable(() -> {
            // Simulate I/O enrichment
            Thread.sleep(50);
            return new EnrichedEvent(event, fetchEnrichmentData(event));
        }).subscribeOn(ioScheduler); // Ensure I/O operations use correct scheduler
    }
    
    // Other methods...
}
```

## 4.5 Performance Optimization Strategies

### Strategy 1: Scheduler Selection Guide

**Decision Tree:**
```java
// Use this decision logic:
if (operation.isBlocking() && operation.isIOBound()) {
    return Schedulers.boundedElastic(); // Database, HTTP, File I/O
} else if (operation.isCPUIntensive()) {
    return Schedulers.parallel(); // Calculations, transformations
} else if (operation.requiresOrdering()) {
    return Schedulers.single(); // Sequential processing
} else {
    return Schedulers.immediate(); // Simple, non-blocking operations
}
```

### Strategy 2: Avoiding Thread Switching Overhead

```java
// BAD - Unnecessary thread switching
Flux.just(1, 2, 3, 4, 5)
    .publishOn(Schedulers.parallel())
    .map(n -> n * 2) // Simple operation
    .publishOn(Schedulers.boundedElastic())
    .map(n -> n + 1) // Another simple operation
    .subscribe();

// GOOD - Minimize thread switching
Flux.just(1, 2, 3, 4, 5)
    .map(n -> n * 2)    // Keep on calling thread
    .map(n -> n + 1)    // Keep on calling thread
    .publishOn(Schedulers.boundedElastic()) // Switch only when needed
    .flatMap(this::performIOOperation)
    .subscribe();
```

### Strategy 3: Resource Pool Management

```java
public class OptimizedProcessingService {
    // Dedicated schedulers for different types of work
    private final Scheduler ioScheduler = Schedulers.newBoundedElastic(
        20, 1000, "io-pool");
    private final Scheduler cpuScheduler = Schedulers.newParallel(
        "cpu-pool", Runtime.getRuntime().availableProcessors());
    
    public Flux<Result> optimizedProcessing(Flux<Input> input) {
        return input
            .publishOn(cpuScheduler)  // Light CPU work
            .map(this::lightTransformation)
            
            .publishOn(ioScheduler)   // I/O work
            .flatMap(this::ioOperation, 10) // Limit concurrency
            
            .publishOn(cpuScheduler)  // Heavy CPU work
            .map(this::heavyComputation);
    }
    
    @PreDestroy
    public void cleanup() {
        ioScheduler.dispose();
        cpuScheduler.dispose();
    }
}
```

## 4.6 Common Threading Pitfalls and Solutions

### Pitfall 1: Blocking in Reactive Chains

```java
// BAD - Blocking call breaks reactive chain
Flux.range(1, 1000)
    .publishOn(Schedulers.parallel())
    .map(n -> {
        // This blocks the parallel scheduler thread!
        String result = blockingHttpClient.get("/api/" + n);
        return result;
    })
    .subscribe();

// GOOD - Use appropriate scheduler for blocking operations
Flux.range(1, 1000)
    .publishOn(Schedulers.boundedElastic()) // Scheduler designed for blocking
    .map(n -> {
        String result = blockingHttpClient.get("/api/" + n);
        return result;
    })
    .subscribe();

// BETTER - Use reactive client
Flux.range(1, 1000)
    .publishOn(Schedulers.parallel()) // Can use parallel for non-blocking
    .flatMap(n -> 
        reactiveWebClient.get()
            .uri("/api/" + n)
            .retrieve()
            .bodyToMono(String.class)
    )
    .subscribe();
```

### Pitfall 2: Misunderstanding subscribeOn Placement

```java
// INCORRECT - subscribeOn doesn't affect this operation
Flux.range(1, 10)
    .map(n -> heavyComputation(n)) // Runs on calling thread
    .subscribeOn(Schedulers.parallel()); // Only affects subscription, not map

// CORRECT - Use publishOn to affect downstream operations  
Flux.range(1, 10)
    .publishOn(Schedulers.parallel()) // Affects map operation
    .map(n -> heavyComputation(n));
```

### Pitfall 3: Thread Pool Exhaustion

```java
// BAD - Can exhaust thread pool with nested blocking
Flux.range(1, 1000)
    .publishOn(Schedulers.boundedElastic())
    .flatMap(n -> 
        Mono.fromCallable(() -> {
            // Nested blocking call on same scheduler
            return anotherBlockingCall(n);
        }).subscribeOn(Schedulers.boundedElastic()) // Same scheduler!
    )
    .subscribe();

// GOOD - Use different schedulers or limit concurrency
Flux.range(1, 1000)
    .publishOn(Schedulers.boundedElastic())
    .flatMap(n -> 
        Mono.fromCallable(() -> anotherBlockingCall(n))
            .subscribeOn(Schedulers.boundedElastic()),
        10 // Limit concurrent operations
    )
    .subscribe();
```

## 4.7 Testing with Schedulers

### Virtual Time Testing

```java
@Test
public void testWithVirtualTime() {
    StepVerifier.withVirtualTime(() -> 
        Flux.interval(Duration.ofSeconds(1))
            .take(3)
            .map(tick -> "Tick " + tick)
    )
    .expectSubscription()
    .expectNoEvent(Duration.ofSeconds(1))
    .expectNext("Tick 0")
    .thenAwait(Duration.ofSeconds(1))
    .expectNext("Tick 1")
    .thenAwait(Duration.ofSeconds(1))
    .expectNext("Tick 2")
    .verifyComplete();
}
```

### Testing with Custom Schedulers

```java
@Test
public void testSchedulerBehavior() {
    // Create test scheduler
    VirtualTimeScheduler testScheduler = VirtualTimeScheduler.getOrSet();
    
    try {
        Flux<String> flux = Flux.just("A", "B", "C")
            .delayElements(Duration.ofSeconds(1), testScheduler);
        
        StepVerifier.create(flux)
            .then(() -> testScheduler.advanceTimeBy(Duration.ofSeconds(1)))
            .expectNext("A")
            .then(() -> testScheduler.advanceTimeBy(Duration.ofSeconds(1)))
            .expectNext("B")
            .then(() -> testScheduler.advanceTimeBy(Duration.ofSeconds(1)))
            .expectNext("C")
            .verifyComplete();
    } finally {
        VirtualTimeScheduler.reset();
    }
}
```

## 4.8 Practical Exercises - Week 7

### Exercise 1: Threading Analysis
```java
public class ThreadingAnalysisExercises {
    
    // Exercise 1a: Analyze and fix threading issues
    public Flux<ProcessedData> analyzeThreading(Flux<RawData> input) {
        // TODO: This pipeline has threading issues. Identify and fix them:
        return input
            .map(data -> {
                // Light CPU work
                return data.transform();
            })
            .publishOn(Schedulers.parallel())
            .map(data -> {
                // This is actually blocking I/O!
                Thread.sleep(100);
                return data.enrichWithExternalCall();
            })
            .subscribeOn(Schedulers.single())
            .flatMap(data -> {
                // More light CPU work
                return Mono.just(data.finalize());
            });
    }
    
    // Exercise 1b: Design optimal threading strategy
    public Flux<Result> designOptimalThreading(Flux<Task> tasks) {
        // TODO: Design threading for this pipeline:
        // 1. Receive tasks (any thread)
        // 2. Validate tasks (CPU-intensive)
        // 3. Fetch dependencies from database (I/O)
        // 4. Process tasks (CPU-intensive)
        // 5. Save results (I/O)
        // 6. Send notifications (I/O)
        return null;
    }
}
```

### Exercise 2: Parallel Processing Optimization
```java
public class ParallelProcessingExercises {
    
    // Exercise 2a: Implement parallel processing with proper coordination
    public Flux<AggregatedResult> parallelProcessing(Flux<WorkItem> items) {
        // TODO: 
        // 1. Process items in parallel (4 threads)
        // 2. Each item requires CPU-intensive computation
        // 3. Results need to be aggregated in order
        // 4. Handle backpressure properly
        return null;
    }
    
    // Exercise 2b: Mixed workload optimization
    public Flux<FinalResult> mixedWorkloadOptimization(Flux<Job> jobs) {
        // TODO: Optimize this mixed workload:
        // 1. 30% of jobs are I/O intensive (database queries)
        // 2. 50% are CPU intensive (calculations)
        // 3. 20% are mixed (I/O then CPU)
        // Design optimal threading strategy
        return null;
    }
}
```

## 4.9 Practical Exercises - Week 8

### Exercise 3: Resource Management
```java
public class ResourceManagementExercises {
    
    // Exercise 3a: Connection pool management
    public Flux<QueryResult> connectionPoolManagement(Flux<Query> queries) {
        // TODO: Manage database connections efficiently:
        // 1. Limited connection pool (10 connections)
        // 2. Each query blocks a connection
        // 3. Implement backpressure when pool is exhausted
        // 4. Ensure connections are properly released
        return null;
    }
    
    // Exercise 3b: Custom scheduler lifecycle
    public class CustomSchedulerService {
        // TODO: Create a service that:
        // 1. Creates custom schedulers for different workloads
        // 2. Monitors scheduler health and utilization
        // 3. Provides graceful shutdown
        // 4. Handles scheduler failures
    }
}
```

### Exercise 4: Production Monitoring
```java
public class ProductionMonitoringExercises {
    
    // Exercise 4a: Thread pool monitoring
    public Flux<ProcessingResult> monitoredProcessing(Flux<Work> work) {
        // TODO: Add monitoring for:
        // 1. Active threads per scheduler
        // 2. Queue sizes
        // 3. Task completion times
        // 4. Thread utilization
        // 5. Scheduler health metrics
        return null;
    }
    
    // Exercise 4b: Dynamic scheduler scaling
    public class DynamicSchedulerManager {
        // TODO: Implement dynamic scaling:
        // 1. Monitor workload
        // 2. Scale thread pools up/down based on demand
        // 3. Maintain performance SLAs
        // 4. Handle scaling failures gracefully
    }
}
```

## 4.10 Performance Best Practices

### 1. Scheduler Selection Matrix

| Operation Type | Scheduler | Reasoning |
|----------------|-----------|-----------|
| Simple CPU transforms | `immediate()` | No threading overhead |
| Heavy CPU computation | `parallel()` | Utilize all CPU cores |
| Blocking I/O | `boundedElastic()` | Designed for blocking operations |
| Sequential stateful ops | `single()` | Thread safety guaranteed |
| Custom requirements | Custom | Full control over resources |

### 2. Thread Switching Optimization

```java
// Minimize context switches
public Flux<Result> optimizedPipeline(Flux<Input> input) {
    return input
        // Group related operations on same scheduler
        .publishOn(Schedulers.parallel())
        .map(this::transform1)     // CPU operation
        .map(this::transform2)     // CPU operation
        .filter(this::validate)    // CPU operation
        
        // Switch only when necessary
        .publishOn(Schedulers.boundedElastic())
        .flatMap(this::ioOperation1) // I/O operation
        .flatMap(this::ioOperation2) // I/O operation
        
        // Final CPU work
        .publishOn(Schedulers.parallel())
        .map(this::finalTransform);
}
```

### 3. Resource Pool Sizing

```java
// Calculate optimal pool sizes
public class SchedulerConfiguration {
    
    // CPU-bound: cores × utilization target
    private final int cpuPoolSize = 
        Runtime.getRuntime().availableProcessors() * 1; // 100% utilization
    
    // I/O-bound: much larger, based on expected blocking time
    private final int ioPoolSize = 
        Runtime.getRuntime().availableProcessors() * 10; // 10:1 ratio
    
    // Custom schedulers with appropriate sizing
    public Scheduler cpuScheduler() {
        return Schedulers.newParallel("cpu", cpuPoolSize);
    }
    
    public Scheduler ioScheduler() {
        return Schedulers.newBoundedElastic(
            ioPoolSize,           // Max threads
            Integer.MAX_VALUE,    // Queue size
            "io",                 // Name prefix
            60,                   // TTL seconds
            true                  // Daemon threads
        );
    }
}
```

## Key Takeaways - Phase 4

### Scheduler Types
✅ **immediate()**: No thread switching, current thread  
✅ **single()**: One dedicated thread, sequential processing  
✅ **boundedElastic()**: Elastic pool, optimized for blocking I/O  
✅ **parallel()**: Fixed pool, optimized for CPU work  
✅ **Custom**: Full control for specific requirements  

### Threading Control
✅ **subscribeOn()**: Controls where subscription starts (entire chain)  
✅ **publishOn()**: Controls where downstream operations run  
✅ **Thread switching**: Minimize unnecessary context switches  
✅ **Resource management**: Proper scheduler lifecycle management  

### Performance Optimization
✅ **Scheduler selection**: Match scheduler to operation type  
✅ **Pool sizing**: Appropriate sizing for workload  
✅ **Monitoring**: Track thread utilization and health  
✅ **Testing**: Use virtual time for reliable tests  

### Common Pitfalls Avoided
✅ **Blocking in wrong scheduler**: Use boundedElastic for blocking  
✅ **Unnecessary thread switching**: Group similar operations  
✅ **Resource leaks**: Always dispose custom schedulers  
✅ **Pool exhaustion**: Monitor and limit concurrency

Key Highlights of Phase 4:
🎯 Critical Concepts Mastered:

subscribeOn() vs publishOn() - This is THE fundamental threading concept
Scheduler selection - Matching the right scheduler to the operation type
Thread switching optimization - Minimizing context switching overhead
Resource management - Proper lifecycle management of custom schedulers

⚡ Performance Insights:

boundedElastic() for blocking I/O operations
parallel() for CPU-intensive computations
Thread switching costs - Why you should minimize them
Pool sizing strategies - How to calculate optimal sizes

🛠 Production-Ready Patterns:

Mixed workload optimization - Different schedulers for different operations
Resource pool management - Connection pools, thread pools
Monitoring and observability - Thread utilization tracking
Testing strategies - Virtual time and scheduler testing

Critical Understanding Check:
Before moving to Phase 5, make sure you can answer these:

When would you use subscribeOn() vs publishOn()?
Which scheduler would you choose for database operations? Why?
How do you prevent thread pool exhaustion?
What happens if you use the wrong scheduler for blocking operations?
How do you test time-dependent reactive streams?

Learning Approach for Phase 4:
Week 7 Focus:

Master the scheduler types and their use cases
Practice subscribeOn() vs publishOn() until it's intuitive
Work through the threading analysis exercises

Week 8 Focus:

Focus on performance optimization patterns
Build the resource management exercises
Practice production monitoring techniques

Phase 4 Success Indicators:
You've mastered Phase 4 when you can:

 Choose the optimal scheduler for any operation type
 Control exactly where operations execute in reactive pipelines
 Optimize threading for mixed workloads
 Avoid common threading pitfalls
 Monitor and manage scheduler health in production

Most Important Takeaway:
Threading is not automatic in reactive programming - you must consciously decide where operations execute. The default (calling thread) is rarely optimal for production applications.

## Next Phase Preview

**Phase 5** covers **Backpressure Management** - where you'll learn:
- Understanding backpressure scenarios
- Backpressure strategies (buffer, drop, latest, error)
- Flow control and demand management
- Building backpressure-aware applications

