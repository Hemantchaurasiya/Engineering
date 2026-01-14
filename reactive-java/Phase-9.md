# Phase 9: Advanced Patterns and Operators (Week 17-18)

## Learning Objectives
By the end of Phase 9, you will:
- Master window and buffer operations for stream segmentation
- Create reusable operator chains with transform() and compose()
- Build custom operators for complex business logic
- Implement advanced streaming patterns
- Optimize reactive pipelines for performance and readability

---

## 9.1 Window and Buffer Operations

### Understanding Windowing vs Buffering

**Windowing** splits a stream into multiple sub-streams (Flux<Flux<T>>), while **buffering** collects items into collections (Flux<List<T>>).

```java
// Buffer: Collects items into Lists
Flux.range(1, 10)
    .buffer(3)
    .subscribe(list -> System.out.println("Buffer: " + list));
// Output: Buffer: [1, 2, 3], Buffer: [4, 5, 6], Buffer: [7, 8, 9], Buffer: [10]

// Window: Creates sub-streams
Flux.range(1, 10)
    .window(3)
    .flatMap(window -> window.collectList())
    .subscribe(list -> System.out.println("Window: " + list));
// Output: Window: [1, 2, 3], Window: [4, 5, 6], Window: [7, 8, 9], Window: [10]
```

### Basic Buffer Operations

#### 1. Size-based Buffering
```java
public class BufferExamples {
    
    public static void sizeBasedBuffer() {
        Flux.range(1, 15)
            .buffer(4) // Buffer every 4 items
            .subscribe(System.out::println);
        // Output: [1, 2, 3, 4], [5, 6, 7, 8], [9, 10, 11, 12], [13, 14, 15]
    }
    
    public static void overlappingBuffer() {
        Flux.range(1, 10)
            .buffer(3, 2) // Size=3, Skip=2 (overlapping)
            .subscribe(System.out::println);
        // Output: [1, 2, 3], [3, 4, 5], [5, 6, 7], [7, 8, 9], [9, 10]
    }
    
    public static void gappingBuffer() {
        Flux.range(1, 10)
            .buffer(2, 3) // Size=2, Skip=3 (gaps)
            .subscribe(System.out::println);
        // Output: [1, 2], [4, 5], [7, 8], [10]
    }
}
```

#### 2. Time-based Buffering
```java
public class TimeBasedBuffer {
    
    public static void timeBuffer() {
        Flux.interval(Duration.ofMillis(100))
            .take(20)
            .buffer(Duration.ofMillis(500)) // Buffer for 500ms
            .subscribe(list -> System.out.println("Time buffer: " + list.size() + " items"));
    }
    
    public static void timeOrSizeBuffer() {
        Flux.interval(Duration.ofMillis(50))
            .take(100)
            .bufferTimeout(10, Duration.ofMillis(300)) // Max 10 items OR 300ms
            .subscribe(list -> System.out.println("Buffer size: " + list.size()));
    }
}
```

#### 3. Conditional Buffering
```java
public class ConditionalBuffer {
    
    public static void bufferUntil() {
        Flux.just("a", "b", "DELIMITER", "c", "d", "e", "DELIMITER", "f")
            .bufferUntil("DELIMITER"::equals) // Buffer until condition is met
            .subscribe(System.out::println);
        // Output: [a, b, DELIMITER], [c, d, e, DELIMITER], [f]
    }
    
    public static void bufferWhile() {
        Flux.just("apple", "apricot", "banana", "berry", "cherry", "coconut")
            .bufferWhile(s -> s.startsWith("a")) // Buffer while condition is true
            .subscribe(System.out::println);
        // Output: [apple, apricot], [banana], [berry], [cherry], [coconut]
    }
    
    public static void bufferUntilChanged() {
        Flux.just("A", "A", "B", "B", "B", "C", "C", "A")
            .bufferUntilChanged() // Buffer until value changes
            .subscribe(System.out::println);
        // Output: [A, A], [B, B, B], [C, C], [A]
    }
}
```

### Advanced Window Operations

#### 1. Window with Processing
```java
public class WindowProcessing {
    
    public static void processWindows() {
        Flux.range(1, 20)
            .window(5)
            .flatMap(window -> window
                .reduce(0, Integer::sum) // Sum each window
                .map(sum -> "Window sum: " + sum)
            )
            .subscribe(System.out::println);
        // Output: Window sum: 15, Window sum: 40, Window sum: 65, Window sum: 90
    }
    
    public static void parallelWindowProcessing() {
        Flux.range(1, 100)
            .window(10)
            .parallel(4) // Process windows in parallel
            .runOn(Schedulers.parallel())
            .map(window -> window
                .collectList()
                .map(list -> {
                    // Simulate heavy processing
                    String threadName = Thread.currentThread().getName();
                    return "Processed " + list.size() + " items on " + threadName;
                })
            )
            .flatMap(mono -> mono)
            .sequential()
            .subscribe(System.out::println);
    }
}
```

#### 2. Time-based Windows
```java
public class TimeWindows {
    
    public static void slidingTimeWindow() {
        Flux.interval(Duration.ofMillis(100))
            .take(50)
            .window(Duration.ofSeconds(1), Duration.ofMillis(500)) // 1s window, 500ms slide
            .flatMap(window -> window
                .count()
                .map(count -> "Window count: " + count)
            )
            .subscribe(System.out::println);
    }
    
    public static void sessionWindowing() {
        // Simulate user events with gaps
        Flux<String> events = Flux.concat(
            Flux.just("event1", "event2", "event3").delayElements(Duration.ofMillis(100)),
            Flux.just("event4", "event5").delayElements(Duration.ofMillis(100)).delaySubscription(Duration.ofSeconds(2)),
            Flux.just("event6").delaySubscription(Duration.ofSeconds(4))
        );
        
        events
            .windowTimeout(100, Duration.ofSeconds(1)) // Session timeout: 1 second
            .flatMap(window -> window
                .collectList()
                .map(list -> "Session: " + list)
            )
            .subscribe(System.out::println);
    }
}
```

### Real-world Windowing Patterns

#### 1. Batch Processing with Error Handling
```java
@Service
public class BatchProcessor {
    
    public Flux<BatchResult> processBatchData(Flux<DataItem> dataStream) {
        return dataStream
            .buffer(100, Duration.ofSeconds(30)) // Max 100 items or 30 seconds
            .filter(batch -> !batch.isEmpty())
            .flatMap(this::processBatch, 3) // Max 3 concurrent batches
            .onErrorContinue((error, batch) -> {
                log.error("Failed to process batch", error);
                // Could emit to dead letter queue
            });
    }
    
    private Mono<BatchResult> processBatch(List<DataItem> batch) {
        return Mono.fromCallable(() -> {
            // Simulate batch processing
            Thread.sleep(1000);
            return new BatchResult(batch.size(), Instant.now());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(Duration.ofSeconds(10));
    }
}

record DataItem(String id, String data) {}
record BatchResult(int processedCount, Instant timestamp) {}
```

#### 2. Real-time Analytics with Sliding Windows
```java
@Component
public class MetricsProcessor {
    
    public Flux<MetricsSummary> processMetrics(Flux<MetricEvent> events) {
        return events
            .window(Duration.ofMinutes(5), Duration.ofMinutes(1)) // 5min window, 1min slide
            .flatMap(this::calculateMetrics)
            .doOnNext(this::publishMetrics);
    }
    
    private Mono<MetricsSummary> calculateMetrics(Flux<MetricEvent> window) {
        return window
            .collectMultimap(MetricEvent::getType, MetricEvent::getValue)
            .map(this::createSummary);
    }
    
    private MetricsSummary createSummary(Map<String, Collection<Double>> metrics) {
        Map<String, Double> averages = metrics.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0)
            ));
        
        return new MetricsSummary(averages, Instant.now());
    }
    
    private void publishMetrics(MetricsSummary summary) {
        // Publish to monitoring system
        System.out.println("Metrics: " + summary);
    }
}

record MetricEvent(String type, double value, Instant timestamp) {}
record MetricsSummary(Map<String, Double> averages, Instant timestamp) {}
```

---

## 9.2 Transform and Compose Operations

### Understanding Transform vs Compose

- **`transform()`**: Applies a function to the entire Publisher
- **`compose()`**: Applies a function and returns a different Publisher type
- **`as()`**: Terminal transformation, returns any type

```java
public class TransformVsCompose {
    
    public static void demonstrateDifferences() {
        Flux<Integer> source = Flux.range(1, 5);
        
        // transform() - returns same Publisher type
        Flux<String> transformed = source.transform(flux -> 
            flux.map(i -> "Item: " + i)
                .filter(s -> !s.contains("3"))
        );
        
        // compose() - can return different Publisher type  
        Mono<List<Integer>> composed = source.compose(flux ->
            flux.collectList()
        );
        
        // as() - terminal transformation, any return type
        String result = source.as(flux -> 
            flux.map(String::valueOf)
                .collectList()
                .map(list -> String.join(", ", list))
                .block()
        );
    }
}
```

### Reusable Operator Chains

#### 1. Creating Custom Transformers
```java
public class CustomTransformers {
    
    // Logging transformer
    public static <T> Function<Flux<T>, Flux<T>> logWithPrefix(String prefix) {
        return flux -> flux
            .doOnSubscribe(s -> System.out.println(prefix + ": Subscribed"))
            .doOnNext(item -> System.out.println(prefix + ": " + item))
            .doOnError(error -> System.err.println(prefix + ": Error - " + error.getMessage()))
            .doOnComplete(() -> System.out.println(prefix + ": Completed"));
    }
    
    // Retry with exponential backoff
    public static <T> Function<Flux<T>, Flux<T>> retryWithBackoff(
            int maxRetries, Duration initialDelay) {
        return flux -> flux
            .retryWhen(Retry.backoff(maxRetries, initialDelay)
                .doBeforeRetry(retrySignal -> 
                    System.out.println("Retrying... attempt: " + (retrySignal.totalRetries() + 1)))
            );
    }
    
    // Timeout with fallback
    public static <T> Function<Flux<T>, Flux<T>> timeoutWithFallback(
            Duration timeout, Flux<T> fallback) {
        return flux -> flux
            .timeout(timeout)
            .onErrorResume(TimeoutException.class, ex -> {
                System.out.println("Timeout occurred, using fallback");
                return fallback;
            });
    }
    
    // Usage example
    public static void useTransformers() {
        Flux.interval(Duration.ofSeconds(1))
            .take(5)
            .transform(logWithPrefix("MAIN"))
            .transform(retryWithBackoff(3, Duration.ofMillis(100)))
            .transform(timeoutWithFallback(Duration.ofSeconds(10), Flux.just(-1L)))
            .subscribe();
    }
}
```

#### 2. Business Logic Transformers
```java
@Component
public class BusinessTransformers {
    
    // Data validation transformer
    public static <T> Function<Flux<T>, Flux<T>> validateAndFilter(Predicate<T> validator) {
        return flux -> flux
            .filter(item -> {
                boolean isValid = validator.test(item);
                if (!isValid) {
                    System.out.println("Invalid item filtered: " + item);
                }
                return isValid;
            });
    }
    
    // Audit transformer
    public Function<Flux<String>, Flux<String>> auditProcessing(String operation) {
        return flux -> flux
            .doOnNext(item -> auditService.logProcessing(operation, item))
            .doOnError(error -> auditService.logError(operation, error))
            .doOnComplete(() -> auditService.logCompletion(operation));
    }
    
    // Rate limiting transformer
    public static <T> Function<Flux<T>, Flux<T>> rateLimit(Duration period) {
        return flux -> flux
            .delayElements(period)
            .doOnNext(item -> System.out.println("Rate limited: " + item));
    }
    
    // Circuit breaker pattern
    public <T> Function<Flux<T>, Flux<T>> circuitBreaker(
            String name, int failureThreshold, Duration timeout) {
        return flux -> flux
            .transform(CircuitBreakerOperator.of(
                CircuitBreaker.ofDefaults(name)
                    .toBuilder()
                    .failureRateThreshold(failureThreshold)
                    .waitDurationInOpenState(timeout)
                    .build()
            ));
    }
}

@Service
class AuditService {
    public void logProcessing(String operation, Object item) {
        System.out.println("AUDIT: " + operation + " - processing: " + item);
    }
    
    public void logError(String operation, Throwable error) {
        System.err.println("AUDIT: " + operation + " - error: " + error.getMessage());
    }
    
    public void logCompletion(String operation) {
        System.out.println("AUDIT: " + operation + " - completed");
    }
}
```

#### 3. Conditional Transformers
```java
public class ConditionalTransformers {
    
    public static <T> Function<Flux<T>, Flux<T>> conditionalTransform(
            Predicate<T> condition, 
            Function<Flux<T>, Flux<T>> transformer) {
        return flux -> flux
            .groupBy(condition::test)
            .flatMap(group -> group.key() ? 
                group.transform(transformer) : 
                group
            );
    }
    
    public static <T> Function<Flux<T>, Flux<T>> branchingTransform(
            Predicate<T> condition,
            Function<Flux<T>, Flux<T>> trueTransformer,
            Function<Flux<T>, Flux<T>> falseTransformer) {
        return flux -> flux
            .groupBy(condition::test)
            .flatMap(group -> group.key() ? 
                group.transform(trueTransformer) : 
                group.transform(falseTransformer)
            );
    }
    
    // Usage example
    public static void demonstrateConditional() {
        Flux.range(1, 10)
            .transform(conditionalTransform(
                i -> i % 2 == 0,  // Even numbers
                evenFlux -> evenFlux.map(i -> i * 10) // Multiply by 10
            ))
            .transform(branchingTransform(
                i -> i > 50,  // Large numbers
                largeFlux -> largeFlux.map(i -> "LARGE: " + i),
                smallFlux -> smallFlux.map(i -> "small: " + i)
            ))
            .subscribe(System.out::println);
    }
}
```

---

## 9.3 Custom Operators

### Building Stateless Custom Operators

```java
public class StatelessOperators {
    
    // Custom map-like operator
    public static <T, R> Function<Flux<T>, Flux<R>> customMap(Function<T, R> mapper) {
        return flux -> flux.handle((item, sink) -> {
            try {
                R result = mapper.apply(item);
                sink.next(result);
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }
    
    // Custom filter with logging
    public static <T> Function<Flux<T>, Flux<T>> filterWithLogging(
            Predicate<T> predicate, String logPrefix) {
        return flux -> flux.handle((item, sink) -> {
            if (predicate.test(item)) {
                System.out.println(logPrefix + ": PASSED - " + item);
                sink.next(item);
            } else {
                System.out.println(logPrefix + ": FILTERED - " + item);
            }
        });
    }
    
    // Custom take while with count
    public static <T> Function<Flux<T>, Flux<T>> takeWhileWithCount(
            Predicate<T> predicate, int maxCount) {
        return flux -> flux.handle((item, sink) -> {
            if (predicate.test(item) && sink.currentContext().getOrDefault("count", 0) < maxCount) {
                sink.next(item);
                sink.contextWrite(ctx -> ctx.put("count", (Integer) ctx.getOrDefault("count", 0) + 1));
            } else {
                sink.complete();
            }
        });
    }
}
```

### Building Stateful Custom Operators

```java
public class StatefulOperators {
    
    // Running average operator
    public static Function<Flux<Double>, Flux<Double>> runningAverage() {
        return flux -> flux.scan(new RunningAverageState(), (state, value) -> {
            state.count++;
            state.sum += value;
            state.average = state.sum / state.count;
            return state;
        }).map(state -> state.average);
    }
    
    private static class RunningAverageState {
        double sum = 0;
        int count = 0;
        double average = 0;
    }
    
    // Deduplication with time window
    public static <T> Function<Flux<T>, Flux<T>> deduplicateWithin(Duration timeWindow) {
        return flux -> flux.flatMap(item -> 
            Mono.just(item)
                .delayElement(timeWindow)
                .then(Mono.empty())
                .startWith(Mono.just(item))
        ).distinct();
    }
    
    // Rate limiting with burst support
    public static <T> Function<Flux<T>, Flux<T>> rateLimitWithBurst(
            int maxBurst, Duration refillPeriod) {
        return flux -> {
            AtomicInteger tokens = new AtomicInteger(maxBurst);
            
            // Refill tokens periodically
            Flux<Long> refillSchedule = Flux.interval(refillPeriod)
                .doOnNext(tick -> tokens.set(Math.min(maxBurst, tokens.get() + 1)));
            
            return flux.zipWith(Flux.merge(Flux.just(0L), refillSchedule), (item, tick) -> item)
                .filter(item -> {
                    if (tokens.get() > 0) {
                        tokens.decrementAndGet();
                        return true;
                    }
                    return false;
                });
        };
    }
}
```

### Advanced Custom Operators

#### 1. Sliding Window Aggregation
```java
public class SlidingWindowOperator {
    
    public static <T, R> Function<Flux<T>, Flux<R>> slidingWindowAggregate(
            Duration windowSize, 
            Duration slideInterval,
            Function<List<T>, R> aggregator) {
        
        return flux -> {
            // Buffer items with timestamps
            Flux<TimestampedItem<T>> timestamped = flux
                .map(item -> new TimestampedItem<>(item, Instant.now()));
            
            return timestamped
                .window(slideInterval)
                .flatMap(window -> window
                    .buffer(windowSize)
                    .filter(buffer -> !buffer.isEmpty())
                    .map(buffer -> {
                        Instant cutoff = Instant.now().minus(windowSize);
                        List<T> windowItems = buffer.stream()
                            .filter(item -> item.timestamp().isAfter(cutoff))
                            .map(TimestampedItem::value)
                            .collect(Collectors.toList());
                        return aggregator.apply(windowItems);
                    })
                );
        };
    }
    
    record TimestampedItem<T>(T value, Instant timestamp) {}
    
    // Usage example
    public static void demonstrateSlidingWindow() {
        Flux.interval(Duration.ofMillis(100))
            .take(50)
            .transform(slidingWindowAggregate(
                Duration.ofSeconds(1),    // 1-second window
                Duration.ofMillis(200),   // Slide every 200ms
                list -> list.size()       // Count items in window
            ))
            .subscribe(count -> System.out.println("Window count: " + count));
    }
}
```

#### 2. Circuit Breaker Operator
```java
public class CircuitBreakerOperator {
    
    public static <T> Function<Flux<T>, Flux<T>> circuitBreaker(
            String name,
            int failureThreshold,
            Duration timeout,
            Duration resetTimeout) {
        
        return flux -> {
            CircuitBreakerState state = new CircuitBreakerState(
                name, failureThreshold, timeout, resetTimeout);
            
            return flux
                .doOnNext(item -> state.recordSuccess())
                .doOnError(error -> state.recordFailure())
                .handle((item, sink) -> {
                    if (state.allowRequest()) {
                        sink.next(item);
                    } else {
                        sink.error(new CircuitBreakerOpenException(
                            "Circuit breaker " + name + " is OPEN"));
                    }
                });
        };
    }
    
    private static class CircuitBreakerState {
        private final String name;
        private final int failureThreshold;
        private final Duration timeout;
        private final Duration resetTimeout;
        
        private volatile CircuitState state = CircuitState.CLOSED;
        private volatile int failureCount = 0;
        private volatile Instant lastFailureTime;
        private volatile Instant openTime;
        
        public CircuitBreakerState(String name, int failureThreshold, 
                                 Duration timeout, Duration resetTimeout) {
            this.name = name;
            this.failureThreshold = failureThreshold;
            this.timeout = timeout;
            this.resetTimeout = resetTimeout;
        }
        
        public boolean allowRequest() {
            if (state == CircuitState.CLOSED) {
                return true;
            }
            
            if (state == CircuitState.OPEN) {
                if (Instant.now().isAfter(openTime.plus(resetTimeout))) {
                    state = CircuitState.HALF_OPEN;
                    System.out.println("Circuit breaker " + name + " -> HALF_OPEN");
                    return true;
                }
                return false;
            }
            
            // HALF_OPEN state
            return true;
        }
        
        public void recordSuccess() {
            if (state == CircuitState.HALF_OPEN) {
                state = CircuitState.CLOSED;
                failureCount = 0;
                System.out.println("Circuit breaker " + name + " -> CLOSED");
            }
        }
        
        public void recordFailure() {
            failureCount++;
            lastFailureTime = Instant.now();
            
            if (failureCount >= failureThreshold && state == CircuitState.CLOSED) {
                state = CircuitState.OPEN;
                openTime = Instant.now();
                System.out.println("Circuit breaker " + name + " -> OPEN");
            } else if (state == CircuitState.HALF_OPEN) {
                state = CircuitState.OPEN;
                openTime = Instant.now();
                System.out.println("Circuit breaker " + name + " -> OPEN (from HALF_OPEN)");
            }
        }
    }
    
    enum CircuitState { CLOSED, OPEN, HALF_OPEN }
    
    static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
```

---

## Practical Exercises

### Exercise 1: Log Analysis Pipeline
Create a reactive pipeline that processes log entries in real-time:

```java
public class LogAnalysisExercise {
    
    public static void main(String[] args) {
        Flux<String> logStream = generateLogStream();
        
        // TODO: Implement the pipeline
        logStream
            // 1. Parse log entries
            // 2. Buffer logs by 1-minute windows
            // 3. Calculate metrics (error rate, request count)
            // 4. Alert on high error rates
            // 5. Handle parsing errors gracefully
            .subscribe();
    }
    
    private static Flux<String> generateLogStream() {
        return Flux.interval(Duration.ofMillis(10))
            .map(i -> generateRandomLogEntry());
    }
    
    private static String generateRandomLogEntry() {
        // Generate sample log entries
        return "2024-01-15 10:30:00 INFO Request processed successfully";
    }
}

// Create LogEntry, LogMetrics classes and implement the solution
```

### Exercise 2: Custom Caching Operator
Implement a caching operator that stores recent results:

```java
public class CachingOperator {
    
    public static <T> Function<Flux<T>, Flux<T>> cacheRecent(
            Duration cacheTime, 
            int maxSize) {
        // TODO: Implement caching with expiration and size limits
        return flux -> flux; // Replace with implementation
    }
    
    // Test the operator
    public static void testCaching() {
        Flux.interval(Duration.ofSeconds(1))
            .map(i -> "Item " + i)
            .transform(cacheRecent(Duration.ofSeconds(5), 10))
            .take(20)
            .subscribe(System.out::println);
    }
}
```

### Exercise 3: Complex Event Processing
Build a system for complex event pattern detection:

```java
public class EventProcessingExercise {
    
    // TODO: Implement pattern detection
    // - Detect sequences of events (A followed by B within 5 seconds)
    // - Count occurrences in sliding windows
    // - Generate alerts for specific patterns
    
    public static Function<Flux<Event>, Flux<PatternAlert>> detectPatterns() {
        return flux -> flux
            // Your implementation here
            .map(event -> new PatternAlert("pattern", event.timestamp()));
    }
}

record Event(String type, String data, Instant timestamp) {}
record PatternAlert(String pattern, Instant detectedAt) {}
```

---

## Performance Optimization Tips

### ✅ Best Practices

1. **Use appropriate operators for the use case**
   ```java
   // ✅ For large datasets
   flux.window(1000).flatMap(window -> window.collectList().map(this::processBatch))
   
   // ❌ Avoid for large datasets  
   flux.buffer(1000000) // Too much memory
   ```

2. **Minimize operator chains**
   ```java
   // ✅ Combined operations
   flux.filter(predicate).map(mapper)
   
   // ❌ Separate transform calls
   flux.transform(f -> f.filter(predicate)).transform(f -> f.map(mapper))
   ```

3. **Use parallel processing wisely**
   ```java
   // ✅ CPU-bound work
   flux.parallel().runOn(Schedulers.parallel()).map(cpuIntensiveWork)
   
   // ❌ I/O-bound work on parallel scheduler
   flux.parallel().runOn(Schedulers.parallel()).flatMap(this::httpCall)
   ```

### Common Anti-patterns

1. **❌ Creating unnecessary intermediate collections**
2. **❌ Using blocking operations in custom operators**
3. **❌ Not handling backpressure in custom Publishers**
4. **❌ Memory leaks in stateful operators**

---

## Key Takeaways

1. **Window vs Buffer**: Windows create sub-streams, buffers create collections
2. **Transform/Compose**: Create reusable operator chains for complex logic
3. **Custom Operators**: Use `handle()` for simple cases, full Publisher implementation for complex ones
4. **Stateful Operations**: Be careful with memory and thread safety
5. **Performance**: Choose the right operator for your use case and data size

This completes Phase 9! You now have the tools to build sophisticated reactive applications with advanced stream processing patterns. Ready for Phase 10: Performance and Optimization?