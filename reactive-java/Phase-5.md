# Phase 5: Backpressure Management - Complete Implementation Guide

## 5.1 Understanding Backpressure

### What is Backpressure?

Backpressure occurs when a **producer** (Publisher) emits data faster than a **consumer** (Subscriber) can process it. This creates a bottleneck that can lead to:

- Memory exhaustion
- System crashes
- Degraded performance
- Dropped data

### Key Scenarios Where Backpressure Occurs

1. **Fast Producer, Slow Consumer**: Database queries producing results faster than UI can render
2. **Network I/O Mismatches**: High-throughput data streams over slower network connections
3. **CPU Intensive Processing**: Simple data generation with complex processing logic
4. **Resource Constraints**: Limited memory or thread pools

### Demonstration: Creating Backpressure

```java
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;

public class BackpressureDemo {
    public static void demonstrateBackpressure() {
        Flux.interval(Duration.ofMillis(1))  // Fast producer: 1000 items/second
            .onBackpressureError()           // Will throw exception on overflow
            .publishOn(Schedulers.single())  // Switch to single thread
            .map(i -> {
                // Slow consumer: artificial delay
                try {
                    Thread.sleep(10);        // Only 100 items/second processing
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Processed: " + i;
            })
            .subscribe(
                System.out::println,
                error -> System.err.println("Backpressure error: " + error)
            );
    }
}
```

## 5.2 Backpressure Strategies

### Strategy 1: onBackpressureBuffer()

Buffers overflow items until they can be processed. **Use with caution** - can lead to OutOfMemoryError.

```java
public class BufferStrategy {
    
    // Basic buffering (unbounded - dangerous!)
    public static Flux<String> basicBuffer() {
        return Flux.interval(Duration.ofMillis(1))
            .onBackpressureBuffer()  // Unbounded buffer
            .map(i -> "Item: " + i);
    }
    
    // Bounded buffer with overflow strategy
    public static Flux<String> boundedBuffer() {
        return Flux.interval(Duration.ofMillis(1))
            .onBackpressureBuffer(1000,              // Max buffer size
                dropped -> System.out.println("Dropped: " + dropped),  // On overflow
                reactor.util.concurrent.Queues.SMALL_BUFFER_SIZE)       // Buffer type
            .map(i -> "Item: " + i);
    }
    
    // Time-based buffer cleanup
    public static Flux<String> timeBasedBuffer() {
        return Flux.interval(Duration.ofMillis(1))
            .onBackpressureBuffer(Duration.ofSeconds(1),  // Buffer for max 1 second
                1000,                                      // Max size
                dropped -> System.out.println("Time-based drop: " + dropped))
            .map(i -> "Item: " + i);
    }
}
```

### Strategy 2: onBackpressureDrop()

Drops newest items when buffer is full. Good for **real-time scenarios** where only latest data matters.

```java
public class DropStrategy {
    
    // Drop newest items
    public static Flux<String> dropNewest() {
        return Flux.interval(Duration.ofMillis(1))
            .onBackpressureDrop(dropped -> 
                System.out.println("Dropped item: " + dropped))
            .map(i -> "Processing: " + i);
    }
    
    // Real-world example: Stock price updates
    public static Flux<StockPrice> stockPriceStream() {
        return Flux.interval(Duration.ofMillis(10))  // High-frequency updates
            .map(i -> new StockPrice("AAPL", 150.0 + Math.random() * 10))
            .onBackpressureDrop(price -> 
                System.out.println("Dropped stale price: " + price))
            .publishOn(Schedulers.boundedElastic());
    }
    
    static class StockPrice {
        final String symbol;
        final double price;
        final long timestamp;
        
        StockPrice(String symbol, double price) {
            this.symbol = symbol;
            this.price = price;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("%s: $%.2f at %d", symbol, price, timestamp);
        }
    }
}
```

### Strategy 3: onBackpressureLatest()

Keeps only the **latest** item, dropping intermediate values. Ideal for **live data scenarios**.

```java
public class LatestStrategy {
    
    // Keep only latest sensor reading
    public static Flux<SensorReading> sensorStream() {
        return Flux.interval(Duration.ofMillis(5))  // High-frequency sensor
            .map(i -> new SensorReading("TEMP", 20.0 + Math.random() * 10))
            .onBackpressureLatest()  // Only keep latest reading
            .publishOn(Schedulers.single());
    }
    
    // UI updates - only latest matters
    public static Flux<UIUpdate> uiUpdateStream() {
        return Flux.interval(Duration.ofMillis(1))
            .map(i -> new UIUpdate("Progress: " + i + "%"))
            .onBackpressureLatest()
            .delayElements(Duration.ofMillis(100));  // Slow UI updates
    }
    
    static class SensorReading {
        final String type;
        final double value;
        final long timestamp;
        
        SensorReading(String type, double value) {
            this.type = type;
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("%s: %.2f at %d", type, value, timestamp);
        }
    }
    
    static class UIUpdate {
        final String message;
        final long timestamp;
        
        UIUpdate(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return message + " (" + timestamp + ")";
        }
    }
}
```

### Strategy 4: onBackpressureError()

Immediately fails with an exception when backpressure occurs. Use for **critical systems** where data loss is unacceptable.

```java
public class ErrorStrategy {
    
    // Fail fast on backpressure
    public static Flux<String> criticalDataStream() {
        return Flux.interval(Duration.ofMillis(1))
            .onBackpressureError()  // Throw exception immediately
            .map(i -> "Critical data: " + i)
            .publishOn(Schedulers.single())
            .delayElements(Duration.ofMillis(10));  // Simulate slow processing
    }
    
    // With custom error message
    public static Flux<String> customErrorMessage() {
        return Flux.interval(Duration.ofMillis(1))
            .onBackpressureError(() -> 
                new IllegalStateException("System cannot keep up with data rate!"))
            .map(i -> "Data: " + i);
    }
}
```

## 5.3 Flow Control and Advanced Patterns

### Custom Backpressure Handling

```java
public class CustomBackpressure {
    
    // Adaptive processing speed based on backpressure
    public static Flux<String> adaptiveProcessing() {
        return Flux.create(sink -> {
            AtomicLong requestCount = new AtomicLong(0);
            
            sink.onRequest(n -> {
                requestCount.addAndGet(n);
                // Emit items based on demand
                for (int i = 0; i < n && !sink.isCancelled(); i++) {
                    sink.next("Item " + i);
                }
            });
            
            sink.onCancel(() -> System.out.println("Cancelled"));
        });
    }
    
    // Sampling strategy - emit every Nth item under backpressure
    public static <T> Flux<T> sampling(Flux<T> source, int sampleRate) {
        return source.buffer(sampleRate)
                    .map(list -> list.get(list.size() - 1));  // Take last item
    }
}
```

### Request Patterns and Demand Signaling

```java
public class DemandControl {
    
    // Manual demand control
    public static void manualDemandExample() {
        Flux<Integer> source = Flux.range(1, 1000);
        
        source.subscribe(new BaseSubscriber<Integer>() {
            private int processedCount = 0;
            
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                request(10);  // Request first 10 items
            }
            
            @Override
            protected void hookOnNext(Integer value) {
                System.out.println("Processing: " + value);
                processedCount++;
                
                if (processedCount % 10 == 0) {
                    // Request next batch after processing 10 items
                    request(10);
                }
            }
            
            @Override
            protected void hookOnComplete() {
                System.out.println("Completed processing " + processedCount + " items");
            }
        });
    }
    
    // Conditional requesting based on system load
    public static Flux<String> loadBasedProcessing(Flux<String> source) {
        return source.handle((item, sink) -> {
            double systemLoad = ManagementFactory.getOperatingSystemMXBean()
                                               .getProcessCpuLoad();
            
            if (systemLoad < 0.8) {  // Process only if CPU usage < 80%
                sink.next("Processed: " + item);
            } else {
                System.out.println("Dropping item due to high system load: " + item);
            }
        });
    }
}
```

### Bounded Queues and Memory Management

```java
public class BoundedQueueExample {
    
    // Using different queue types for different scenarios
    public static Flux<String> differentQueueStrategies() {
        
        // Small buffer for low-latency scenarios
        Flux<String> lowLatency = Flux.interval(Duration.ofMillis(1))
            .onBackpressureBuffer(32, // Small buffer
                dropped -> System.out.println("Low-latency drop: " + dropped),
                reactor.util.concurrent.Queues.XS_BUFFER_SIZE)
            .map(i -> "Low-latency: " + i);
        
        // Large buffer for batch processing
        Flux<String> batchProcessing = Flux.interval(Duration.ofMillis(1))
            .onBackpressureBuffer(10000, // Large buffer
                dropped -> System.out.println("Batch drop: " + dropped),
                reactor.util.concurrent.Queues.CAPACITY_UNSURE)
            .buffer(100)  // Process in batches
            .map(batch -> "Batch of " + batch.size() + " items");
        
        return Flux.merge(lowLatency.take(100), batchProcessing.take(10));
    }
}
```

## 5.4 Practical Examples and Best Practices

### Example 1: File Processing Pipeline

```java
public class FileProcessingPipeline {
    
    public static Flux<String> processLargeFile(String fileName) {
        return Flux.using(
            // Resource supplier
            () -> Files.lines(Paths.get(fileName)),
            
            // Stream processor
            lines -> Flux.fromStream(lines)
                .onBackpressureBuffer(1000)  // Buffer up to 1000 lines
                .map(this::processLine)
                .filter(Objects::nonNull)
                .onErrorContinue((error, item) -> 
                    System.err.println("Error processing line: " + item + ", " + error)),
            
            // Resource cleanup
            Stream::close
        );
    }
    
    private String processLine(String line) {
        // Simulate CPU-intensive processing
        return line.toUpperCase().trim();
    }
}
```

### Example 2: Real-time Data Dashboard

```java
public class RealTimeDashboard {
    
    public static Flux<DashboardUpdate> createDashboard() {
        // Multiple data sources with different rates
        Flux<MetricData> cpuMetrics = Flux.interval(Duration.ofMillis(100))
            .map(i -> new MetricData("CPU", Math.random() * 100))
            .onBackpressureLatest();  // Only latest CPU reading matters
        
        Flux<MetricData> memoryMetrics = Flux.interval(Duration.ofSeconds(1))
            .map(i -> new MetricData("Memory", Math.random() * 16384))
            .onBackpressureBuffer(60);  // Buffer 1 minute of memory readings
        
        Flux<MetricData> networkMetrics = Flux.interval(Duration.ofMillis(50))
            .map(i -> new MetricData("Network", Math.random() * 1000))
            .onBackpressureDrop(dropped -> 
                System.out.println("Dropped network metric: " + dropped));
        
        return Flux.merge(cpuMetrics, memoryMetrics, networkMetrics)
                   .buffer(Duration.ofSeconds(1))  // Aggregate per second
                   .map(DashboardUpdate::new);
    }
    
    static class MetricData {
        final String type;
        final double value;
        final long timestamp;
        
        MetricData(String type, double value) {
            this.type = type;
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("%s: %.2f", type, value);
        }
    }
    
    static class DashboardUpdate {
        final List<MetricData> metrics;
        final long timestamp;
        
        DashboardUpdate(List<MetricData> metrics) {
            this.metrics = metrics;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("Dashboard update with %d metrics at %d", 
                               metrics.size(), timestamp);
        }
    }
}
```

## 5.5 Testing Backpressure Scenarios

```java
public class BackpressureTests {
    
    @Test
    public void testBufferStrategy() {
        StepVerifier.create(
            Flux.interval(Duration.ofMillis(10))
                .onBackpressureBuffer(5)
                .take(10)
        )
        .expectNextCount(10)
        .verifyComplete();
    }
    
    @Test
    public void testDropStrategy() {
        AtomicInteger droppedCount = new AtomicInteger(0);
        
        StepVerifier.create(
            Flux.interval(Duration.ofMillis(1))
                .onBackpressureDrop(item -> droppedCount.incrementAndGet())
                .delayElements(Duration.ofMillis(10))
                .take(5)
        )
        .expectNextCount(5)
        .verifyComplete()
        .verify();
        
        // Assert that some items were dropped
        assertTrue(droppedCount.get() > 0);
    }
    
    @Test
    public void testErrorStrategy() {
        StepVerifier.create(
            Flux.interval(Duration.ofMillis(1))
                .onBackpressureError()
                .delayElements(Duration.ofMillis(100))
        )
        .expectError(IllegalStateException.class)
        .verify();
    }
}
```

## Key Takeaways

1. **Choose the Right Strategy**: 
   - Buffer for temporary spikes
   - Drop for real-time data where latest matters
   - Latest for UI updates and sensor data
   - Error for critical systems

2. **Monitor Memory Usage**: Always set bounded buffers in production

3. **Test Under Load**: Use tools to simulate backpressure scenarios

4. **Consider System Architecture**: Sometimes the solution is upstream (rate limiting) or downstream (horizontal scaling)

5. **Combine Strategies**: Use different strategies for different parts of your pipeline

Key highlights of Phase 5:

Practical Understanding: Real-world examples showing when and why backpressure occurs
Four Core Strategies: Complete implementations of buffer, drop, latest, and error strategies
Advanced Patterns: Custom backpressure handling, demand signaling, and adaptive processing
Production Examples: File processing pipelines and real-time dashboards
Testing Approaches: How to test backpressure scenarios effectively

The examples are designed to be runnable and demonstrate realistic scenarios you might encounter in production systems. Each strategy includes both basic usage and advanced patterns.
Would you like me to:

Create specific code examples for any particular use case?
Dive deeper into any of the backpressure strategies?
Show integration examples with your Phase 4 scheduler work?
Start preparing materials for Phase 6 (Testing Reactive Code)?

## Next Steps

In **Phase 6**, we'll explore testing these reactive streams with StepVerifier, including time-based testing and backpressure scenario validation.