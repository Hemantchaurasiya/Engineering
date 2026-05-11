# Phase 10: Performance and Optimization (Week 19-20)

## Learning Objectives
By the end of Phase 10, you will:
- Master performance optimization techniques for reactive streams
- Implement proper monitoring and metrics collection
- Avoid common performance anti-patterns
- Optimize memory usage and prevent leaks
- Profile and debug reactive applications effectively

---

## 10.1 Performance Considerations

### Understanding Operator Fusion

Reactor automatically optimizes operator chains through **fusion** - combining multiple operators into a single, more efficient operation.

```java
public class OperatorFusion {
    
    public static void demonstrateFusion() {
        // These operations will be fused into a single optimized operation
        Flux.range(1, 1000000)
            .filter(i -> i % 2 == 0)  // Fused
            .map(i -> i * 2)          // Fused  
            .map(i -> "Item: " + i)   // Fused
            .subscribe(System.out::println);
        
        // Fusion is broken by operators that need to see the entire stream
        Flux.range(1, 1000000)
            .filter(i -> i % 2 == 0)  // Fused
            .map(i -> i * 2)          // Fused
            .collectList()            // Fusion breaker - needs entire stream
            .map(list -> list.size()) // New fusion segment starts
            .subscribe(System.out::println);
    }
    
    public static void fusionBreakers() {
        // Operations that break fusion:
        Flux.range(1, 100)
            .map(i -> i * 2)
            // These break fusion:
            .publishOn(Schedulers.parallel()) // Thread boundary
            .buffer(10)                       // Buffering
            .flatMap(Flux::fromIterable)      // Async operations
            .collectList()                    // Terminal collection
            .subscribe(System.out::println);
    }
}
```

### Memory Management and Backpressure

#### 1. Efficient Memory Usage
```java
public class MemoryOptimization {
    
    // ❌ Memory inefficient - buffers everything in memory
    public static Mono<List<String>> inefficientProcessing() {
        return Flux.range(1, 1000000)
            .map(i -> "Large data item " + i + " with lots of content...")
            .collectList(); // Keeps everything in memory!
    }
    
    // ✅ Memory efficient - processes in chunks
    public static Flux<ProcessedChunk> efficientProcessing() {
        return Flux.range(1, 1000000)
            .map(i -> "Large data item " + i)
            .buffer(1000) // Process in chunks
            .map(chunk -> processChunk(chunk))
            .doOnNext(chunk -> {
                // Allow GC to reclaim memory
                System.gc(); // Only for demonstration - don't do this in production
            });
    }
    
    // ✅ Streaming approach - constant memory usage
    public static Flux<String> streamingProcessing() {
        return Flux.range(1, 1000000)
            .map(i -> processItem(i))
            .onBackpressureBuffer(1000) // Limit buffer size
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    private static ProcessedChunk processChunk(List<String> chunk) {
        return new ProcessedChunk(chunk.size());
    }
    
    private static String processItem(int i) {
        return "Processed: " + i;
    }
    
    record ProcessedChunk(int size) {}
}
```

#### 2. Backpressure Strategies
```java
public class BackpressureOptimization {
    
    // Fast producer, slow consumer scenario
    public static void backpressureStrategies() {
        Flux<Integer> fastProducer = Flux.range(1, 1000000)
            .delayElements(Duration.ofNanos(1)); // Very fast
        
        // Strategy 1: Buffer with size limit
        fastProducer
            .onBackpressureBuffer(1000, 
                dropped -> System.out.println("Dropped: " + dropped),
                BufferOverflowStrategy.DROP_OLDEST)
            .delayElements(Duration.ofMillis(10)) // Slow consumer
            .subscribe(System.out::println);
        
        // Strategy 2: Drop excess items
        fastProducer
            .onBackpressureDrop(dropped -> 
                System.out.println("Dropped due to backpressure: " + dropped))
            .delayElements(Duration.ofMillis(10))
            .subscribe(System.out::println);
        
        // Strategy 3: Keep only latest
        fastProducer
            .onBackpressureLatest()
            .delayElements(Duration.ofMillis(10))
            .subscribe(System.out::println);
        
        // Strategy 4: Error on overflow
        fastProducer
            .onBackpressureError()
            .delayElements(Duration.ofMillis(10))
            .subscribe(
                System.out::println,
                error -> System.err.println("Backpressure error: " + error.getMessage())
            );
    }
    
    // Custom backpressure handling
    public static <T> Function<Flux<T>, Flux<T>> smartBackpressure(
            int bufferSize, Duration samplingInterval) {
        return flux -> flux
            .onBackpressureBuffer(bufferSize)
            .sample(samplingInterval) // Sample at regular intervals when overwhelmed
            .doOnNext(item -> System.out.println("Sampled: " + item));
    }
}
```

### CPU vs I/O Bound Operations

```java
public class WorkloadOptimization {
    
    // CPU-bound operations
    public static Flux<Integer> cpuIntensiveWork() {
        return Flux.range(1, 1000)
            .parallel(Runtime.getRuntime().availableProcessors()) // Use all cores
            .runOn(Schedulers.parallel()) // CPU-optimized scheduler
            .map(WorkloadOptimization::heavyCpuTask)
            .sequential();
    }
    
    // I/O-bound operations  
    public static Flux<String> ioIntensiveWork() {
        return Flux.range(1, 1000)
            .parallel(100) // More parallelism for I/O
            .runOn(Schedulers.boundedElastic()) // I/O-optimized scheduler
            .flatMap(i -> performIOOperation(i))
            .sequential();
    }
    
    // Mixed workload optimization
    public static Flux<ProcessedData> mixedWorkload() {
        return Flux.range(1, 1000)
            // I/O phase
            .flatMap(i -> performIOOperation(i), 50) // High concurrency for I/O
            .subscribeOn(Schedulers.boundedElastic())
            // CPU phase
            .parallel()
            .runOn(Schedulers.parallel())
            .map(data -> heavyCpuProcessing(data))
            .sequential();
    }
    
    private static Integer heavyCpuTask(Integer input) {
        // Simulate CPU-intensive work
        int result = input;
        for (int i = 0; i < 10000; i++) {
            result = result * 2 + 1;
        }
        return result;
    }
    
    private static Mono<String> performIOOperation(Integer input) {
        return Mono.fromCallable(() -> {
            // Simulate I/O operation
            Thread.sleep(10);
            return "IO Result: " + input;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    private static ProcessedData heavyCpuProcessing(String input) {
        // Simulate CPU processing
        return new ProcessedData(input.hashCode());
    }
    
    record ProcessedData(int hash) {}
}
```

---

## 10.2 Monitoring and Metrics

### Micrometer Integration

```java
@Configuration
@EnableAutoConfiguration
public class MetricsConfiguration {
    
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
    
    @Bean
    public TimedAspect timedAspect(MeterRegistry meterRegistry) {
        return new TimedAspect(meterRegistry);
    }
}

@Service
public class ReactiveMetricsService {
    
    private final MeterRegistry meterRegistry;
    private final Timer processTimer;
    private final Counter successCounter;
    private final Counter errorCounter;
    private final Gauge activeProcesses;
    
    public ReactiveMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.processTimer = Timer.builder("reactive.process.duration")
            .description("Time taken to process items")
            .register(meterRegistry);
        
        this.successCounter = Counter.builder("reactive.process.success")
            .description("Number of successful processes")
            .register(meterRegistry);
        
        this.errorCounter = Counter.builder("reactive.process.error")
            .description("Number of failed processes")
            .register(meterRegistry);
        
        AtomicInteger activeCount = new AtomicInteger(0);
        this.activeProcesses = Gauge.builder("reactive.process.active")
            .description("Number of active processes")
            .register(meterRegistry, activeCount, AtomicInteger::get);
    }
    
    public Flux<ProcessResult> processWithMetrics(Flux<String> input) {
        return input
            .doOnSubscribe(s -> System.out.println("Processing started"))
            .name("reactive.processing") // Name the sequence for metrics
            .metrics() // Enable built-in metrics
            .transform(this::addCustomMetrics)
            .doFinally(signal -> System.out.println("Processing finished: " + signal));
    }
    
    private Flux<ProcessResult> addCustomMetrics(Flux<String> flux) {
        return flux
            .doOnNext(item -> ((AtomicInteger) activeProcesses.gauge()).incrementAndGet())
            .flatMap(this::processWithTiming)
            .doOnNext(result -> {
                ((AtomicInteger) activeProcesses.gauge()).decrementAndGet();
                if (result.success()) {
                    successCounter.increment();
                } else {
                    errorCounter.increment();
                }
            })
            .doOnError(error -> {
                ((AtomicInteger) activeProcesses.gauge()).decrementAndGet();
                errorCounter.increment();
            });
    }
    
    private Mono<ProcessResult> processWithTiming(String input) {
        return Mono.fromCallable(() -> processItem(input))
            .subscribeOn(Schedulers.boundedElastic())
            .transform(mono -> Timer.Sample.start(meterRegistry)
                .stop(processTimer, mono));
    }
    
    private ProcessResult processItem(String input) {
        // Simulate processing
        try {
            Thread.sleep(Random.nextInt(100));
            if (Math.random() < 0.1) { // 10% failure rate
                throw new ProcessingException("Random failure");
            }
            return new ProcessResult(true, "Processed: " + input);
        } catch (Exception e) {
            return new ProcessResult(false, "Error: " + e.getMessage());
        }
    }
    
    record ProcessResult(boolean success, String result) {}
    
    static class ProcessingException extends RuntimeException {
        public ProcessingException(String message) {
            super(message);
        }
    }
}
```

### Custom Metrics and Monitoring

```java
@Component
public class ReactiveMonitor {
    
    private final MeterRegistry meterRegistry;
    private final AtomicLong processedItems = new AtomicLong(0);
    private final Map<String, Timer.Sample> activeTasks = new ConcurrentHashMap<>();
    
    public ReactiveMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        setupGauges();
        scheduleMetricsReporting();
    }
    
    private void setupGauges() {
        Gauge.builder("reactive.items.processed.total")
            .register(meterRegistry, processedItems, AtomicLong::get);
        
        Gauge.builder("reactive.tasks.active")
            .register(meterRegistry, activeTasks, map -> (double) map.size());
    }
    
    private void scheduleMetricsReporting() {
        Flux.interval(Duration.ofSeconds(30))
            .doOnNext(tick -> reportMetrics())
            .subscribe();
    }
    
    public <T> Function<Flux<T>, Flux<T>> monitor(String operationName) {
        return flux -> flux
            .doOnSubscribe(subscription -> {
                Timer.Sample sample = Timer.Sample.start(meterRegistry);
                activeTasks.put(operationName + "-" + subscription.hashCode(), sample);
            })
            .doOnNext(item -> {
                processedItems.incrementAndGet();
                recordThroughput(operationName);
            })
            .doOnError(error -> recordError(operationName, error))
            .doFinally(signal -> {
                String taskKey = operationName + "-" + signal.hashCode();
                Timer.Sample sample = activeTasks.remove(taskKey);
                if (sample != null) {
                    sample.stop(Timer.builder("reactive.operation.duration")
                        .tag("operation", operationName)
                        .tag("signal", signal.toString())
                        .register(meterRegistry));
                }
            });
    }
    
    private void recordThroughput(String operationName) {
        Counter.builder("reactive.throughput")
            .tag("operation", operationName)
            .register(meterRegistry)
            .increment();
    }
    
    private void recordError(String operationName, Throwable error) {
        Counter.builder("reactive.errors")
            .tag("operation", operationName)
            .tag("error.type", error.getClass().getSimpleName())
            .register(meterRegistry)
            .increment();
    }
    
    private void reportMetrics() {
        System.out.println("=== Reactive Metrics Report ===");
        System.out.println("Processed items: " + processedItems.get());
        System.out.println("Active tasks: " + activeTasks.size());
        
        // Report top errors
        meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().equals("reactive.errors"))
            .forEach(meter -> {
                String operation = meter.getId().getTag("operation");
                String errorType = meter.getId().getTag("error.type");
                double count = meter.measure().iterator().next().getValue();
                System.out.println("Errors in " + operation + " [" + errorType + "]: " + count);
            });
    }
}

// Usage example
@Service
public class MonitoredService {
    
    private final ReactiveMonitor monitor;
    
    public MonitoredService(ReactiveMonitor monitor) {
        this.monitor = monitor;
    }
    
    public Flux<String> processData(Flux<String> input) {
        return input
            .transform(monitor.monitor("data-processing"))
            .map(this::processItem)
            .transform(monitor.monitor("post-processing"));
    }
    
    private String processItem(String item) {
        // Simulate processing
        return "Processed: " + item;
    }
}
```

### Health Checks and Observability

```java
@Component
public class ReactiveHealthIndicator implements HealthIndicator {
    
    private final ReactiveHealthMetrics healthMetrics;
    
    public ReactiveHealthIndicator(ReactiveHealthMetrics healthMetrics) {
        this.healthMetrics = healthMetrics;
    }
    
    @Override
    public Health health() {
        Health.Builder status = new Health.Builder();
        
        // Check error rates
        double errorRate = healthMetrics.getErrorRate();
        if (errorRate > 0.1) { // 10% error rate threshold
            status.down()
                .withDetail("errorRate", errorRate)
                .withDetail("reason", "High error rate detected");
        }
        
        // Check throughput
        double throughput = healthMetrics.getThroughput();
        if (throughput < 10) { // Minimum throughput threshold
            status.down()
                .withDetail("throughput", throughput)
                .withDetail("reason", "Low throughput detected");
        }
        
        // Check memory usage
        long memoryUsage = healthMetrics.getMemoryUsage();
        if (memoryUsage > 0.9) { // 90% memory threshold
            status.down()
                .withDetail("memoryUsage", memoryUsage)
                .withDetail("reason", "High memory usage");
        }
        
        return status.up()
            .withDetail("errorRate", errorRate)
            .withDetail("throughput", throughput)
            .withDetail("memoryUsage", memoryUsage)
            .build();
    }
}

@Service
public class ReactiveHealthMetrics {
    
    private final MeterRegistry meterRegistry;
    private final MemoryMXBean memoryBean;
    
    public ReactiveHealthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    public double getErrorRate() {
        Counter errors = meterRegistry.find("reactive.errors").counter();
        Counter total = meterRegistry.find("reactive.throughput").counter();
        
        if (errors != null && total != null && total.count() > 0) {
            return errors.count() / total.count();
        }
        return 0.0;
    }
    
    public double getThroughput() {
        Counter throughput = meterRegistry.find("reactive.throughput").counter();
        return throughput != null ? throughput.count() : 0.0;
    }
    
    public long getMemoryUsage() {
        MemoryUsage heapMemory = memoryBean.getHeapMemoryUsage();
        return heapMemory.getUsed() * 100 / heapMemory.getMax();
    }
}
```

---

## 10.3 Common Anti-patterns

### Performance Anti-patterns

```java
public class PerformanceAntiPatterns {
    
    // ❌ Anti-pattern 1: Blocking in reactive chains
    public static Flux<String> blockingAntiPattern() {
        return Flux.range(1, 100)
            .map(i -> {
                // NEVER do this - blocks the reactive thread
                try {
                    Thread.sleep(100);
                    return httpClient.get("/api/data/" + i).block(); // Blocking!
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "Error";
                }
            });
    }
    
    // ✅ Correct approach
    public static Flux<String> nonBlockingPattern() {
        return Flux.range(1, 100)
            .flatMap(i -> httpClient.get("/api/data/" + i), 10) // Non-blocking with concurrency
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    // ❌ Anti-pattern 2: Excessive thread switching
    public static Flux<String> excessiveThreadSwitching() {
        return Flux.range(1, 100)
            .publishOn(Schedulers.parallel())    // Switch 1
            .map(String::valueOf)
            .publishOn(Schedulers.boundedElastic()) // Switch 2
            .map(s -> s.toUpperCase())
            .publishOn(Schedulers.single())      // Switch 3
            .map(s -> s + "!");
    }
    
    // ✅ Minimize thread switching
    public static Flux<String> minimizedThreadSwitching() {
        return Flux.range(1, 100)
            .map(String::valueOf)
            .map(s -> s.toUpperCase())
            .map(s -> s + "!")
            .publishOn(Schedulers.boundedElastic()); // Single switch at the end
    }
    
    // ❌ Anti-pattern 3: Memory leaks in long-running streams
    public static Flux<String> memoryLeakPattern() {
        Map<String, String> cache = new ConcurrentHashMap<>(); // Never cleared!
        
        return Flux.interval(Duration.ofSeconds(1))
            .map(i -> "key-" + i)
            .doOnNext(key -> {
                cache.put(key, "value-" + System.currentTimeMillis());
                // Cache grows indefinitely!
            })
            .map(cache::get);
    }
    
    // ✅ Proper cache management
    public static Flux<String> properCacheManagement() {
        Cache<String, String> cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();
        
        return Flux.interval(Duration.ofSeconds(1))
            .map(i -> "key-" + i)
            .map(key -> cache.get(key, k -> "value-" + System.currentTimeMillis()));
    }
    
    // Mock HTTP client for examples
    private static WebClient httpClient = WebClient.create();
}
```

### Error Handling Anti-patterns

```java
public class ErrorHandlingAntiPatterns {
    
    // ❌ Anti-pattern: Swallowing errors
    public static Flux<String> swallowingErrors() {
        return Flux.range(1, 10)
            .map(i -> {
                if (i == 5) throw new RuntimeException("Error at " + i);
                return "Item " + i;
            })
            .onErrorReturn(""); // Swallows the error without logging!
    }
    
    // ✅ Proper error handling
    public static Flux<String> properErrorHandling() {
        return Flux.range(1, 10)
            .map(i -> {
                if (i == 5) throw new RuntimeException("Error at " + i);
                return "Item " + i;
            })
            .doOnError(error -> log.error("Processing failed", error))
            .onErrorResume(error -> {
                // Log and provide meaningful fallback
                return Flux.just("Fallback for error: " + error.getMessage());
            });
    }
    
    // ❌ Anti-pattern: Generic error handling
    public static Flux<String> genericErrorHandling() {
        return riskyOperation()
            .onErrorReturn("Generic error occurred"); // Too generic!
    }
    
    // ✅ Specific error handling
    public static Flux<String> specificErrorHandling() {
        return riskyOperation()
            .onErrorResume(TimeoutException.class, ex -> 
                Flux.just("Operation timed out, using cached result"))
            .onErrorResume(ServiceException.class, ex -> 
                Flux.just("Service unavailable, trying alternative"))
            .onErrorResume(ValidationException.class, ex -> 
                Flux.error(new IllegalArgumentException("Invalid input: " + ex.getMessage())))
            .onErrorResume(ex -> 
                Flux.just("Unexpected error: " + ex.getClass().getSimpleName()));
    }
    
    private static Flux<String> riskyOperation() {
        return Flux.just("data");
    }
    
    private static final Logger log = LoggerFactory.getLogger(ErrorHandlingAntiPatterns.class);
    
    // Custom exceptions
    static class ServiceException extends Exception {}
    static class ValidationException extends Exception {}
}
```

---

## Performance Profiling and Debugging

### Debugging Tools

```java
public class ReactiveDebugging {
    
    public static void enableGlobalDebugging() {
        // Enable global debugging (development only)
        Hooks.onOperatorDebug();
        
        // Custom debug configuration
        Hooks.onNextDropped(item -> 
            System.out.println("Item dropped: " + item));
        
        Hooks.onErrorDropped(error -> 
            System.err.println("Error dropped: " + error.getMessage()));
    }
    
    public static Flux<String> debuggingPipeline() {
        return Flux.range(1, 10)
            .checkpoint("After range creation") // Checkpoint for easier debugging
            .filter(i -> i % 2 == 0)
            .checkpoint("After filtering")
            .map(i -> {
                if (i == 8) throw new RuntimeException("Intentional error");
                return "Item: " + i;
            })
            .checkpoint("After mapping")
            .doOnNext(item -> System.out.println("Processing: " + item))
            .log("MAIN") // Built-in logging
            .hide(); // Hide from fusion for debugging
    }
    
    public static void customLogging() {
        Flux.range(1, 5)
            .transform(logOperator("CUSTOM"))
            .subscribe();
    }
    
    private static <T> Function<Flux<T>, Flux<T>> logOperator(String name) {
        return flux -> flux
            .doOnSubscribe(s -> System.out.println(name + ": Subscribed"))
            .doOnRequest(n -> System.out.println(name + ": Requested " + n))
            .doOnNext(item -> System.out.println(name + ": Next " + item))
            .doOnError(error -> System.err.println(name + ": Error " + error))
            .doOnComplete(() -> System.out.println(name + ": Complete"))
            .doOnCancel(() -> System.out.println(name + ": Cancelled"));
    }
}
```

### Performance Benchmarking

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class ReactivePerformanceBenchmark {
    
    private static final int ITEMS = 100_000;
    
    @Benchmark
    public void sequentialProcessing(Blackhole blackhole) {
        Flux.range(1, ITEMS)
            .map(i -> i * 2)
            .map(i -> i + 1)
            .subscribe(blackhole::consume);
    }
    
    @Benchmark
    public void parallelProcessing(Blackhole blackhole) {
        Flux.range(1, ITEMS)
            .parallel()
            .runOn(Schedulers.parallel())
            .map(i -> i * 2)
            .map(i -> i + 1)
            .sequential()
            .subscribe(blackhole::consume);
    }
    
    @Benchmark
    public void bufferProcessing(Blackhole blackhole) {
        Flux.range(1, ITEMS)
            .buffer(1000)
            .flatMap(Flux::fromIterable)
            .map(i -> i * 2)
            .map(i -> i + 1)
            .subscribe(blackhole::consume);
    }
    
    @Benchmark
    public void windowProcessing(Blackhole blackhole) {
        Flux.range(1, ITEMS)
            .window(1000)
            .flatMap(window -> window
                .map(i -> i * 2)
                .map(i -> i + 1)
            )
            .subscribe(blackhole::consume);
    }
}
```

---

## Production Readiness Checklist

### ✅ Performance Checklist

1. **Memory Management**
   - [ ] Use appropriate buffer sizes
   - [ ] Implement proper backpressure handling
   - [ ] Avoid memory leaks in long-running streams
   - [ ] Monitor heap usage and GC pressure

2. **Threading**
   - [ ] Use appropriate schedulers for workload type
   - [ ] Minimize thread switching
   - [ ] Avoid blocking operations in reactive chains
   - [ ] Configure thread pool sizes appropriately

3. **Error Handling**
   - [ ] Implement specific error handling strategies
   - [ ] Log errors appropriately
   - [ ] Provide meaningful fallbacks
   - [ ] Use circuit breakers for external dependencies

4. **Monitoring**
   - [ ] Implement comprehensive metrics
   - [ ] Set up health checks
   - [ ] Monitor error rates and latency
   - [ ] Track resource utilization

5. **Testing**
   - [ ] Performance benchmarks
   - [ ] Load testing
   - [ ] Memory leak detection
   - [ ] Error scenario testing

---

## Key Takeaways

1. **Operator Fusion**: Understanding how Reactor optimizes operator chains automatically
2. **Memory Management**: Proper buffering, backpressure, and avoiding memory leaks
3. **Scheduler Selection**: Choose the right scheduler for CPU vs I/O bound work
4. **Monitoring**: Comprehensive metrics and health checks for production systems
5. **Anti-patterns**: Avoiding common mistakes that hurt performance
6. **Debugging**: Tools and techniques for troubleshooting reactive applications

This completes Phase 10! You now have the knowledge to build high-performance, production-ready reactive applications. Ready for Phase 11: Real-world Applications?