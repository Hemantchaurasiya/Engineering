# Phase 4: Java Concurrency Utilities - Complete Guide

## Real-World Implementation with E-Commerce Order Processing System

---

## Table of Contents

1. [Introduction](#introduction)
2. [Real-World Scenario](#real-world-scenario)
3. [4.1 Executor Framework](#41-executor-framework)
4. [4.2 Callable and Future](#42-callable-and-future)
5. [4.3 ThreadPoolExecutor Deep Dive](#43-threadpoolexecutor-deep-dive)
6. [4.4 ScheduledExecutorService](#44-scheduledexecutorservice)
7. [Summary & Key Takeaways](#summary--key-takeaways)
8. [Practice Project](#practice-project)
9. [Next Steps](#next-steps)

---

## Introduction

Phase 4 focuses on Java's high-level concurrency utilities that abstract away the complexity of manual thread management. Instead of creating and managing threads directly, you'll learn to use:

- **Executor Framework**: Thread pool management
- **Callable & Future**: Tasks that return values
- **ThreadPoolExecutor**: Fine-tuning thread pools
- **ScheduledExecutorService**: Delayed and periodic tasks

These utilities are the foundation of modern concurrent Java applications.

---

## Real-World Scenario

### E-Commerce Order Processing Platform

Throughout this guide, we'll build a realistic order processing system. Each order requires:

1. **Payment validation** (network I/O, ~2 seconds)
2. **Inventory checking** (database query, ~1 second)
3. **Shipping label generation** (API call, ~1.5 seconds)
4. **Email notification** (SMTP, ~500ms)

**The Problem**: Creating a new thread for each order is inefficient and can crash the system during high traffic (Black Friday, flash sales).

**The Solution**: Use thread pools to manage a fixed number of worker threads that process orders concurrently.

---

## 4.1 Executor Framework

### Why Executors?

**Without Executors** (bad approach):
```java
// DON'T DO THIS - creates unlimited threads!
for (Order order : orders) {
    new Thread(() -> processPayment(order)).start();
}
// Problems: 
// - 10,000 orders = 10,000 threads = system crash
// - No reuse, high overhead
// - No control over execution
```

**With Executors** (correct approach):
```java
// DO THIS - bounded thread pool
ExecutorService executor = Executors.newFixedThreadPool(10);
for (Order order : orders) {
    executor.submit(() -> processPayment(order));
}
// Benefits:
// - Fixed 10 threads reused for all orders
// - Automatic task queuing
// - Graceful shutdown support
```

### Types of Thread Pools

#### 1. FixedThreadPool

**Best for**: Predictable workload, CPU-intensive tasks

```java
ExecutorService executor = Executors.newFixedThreadPool(3);
```

**Characteristics**:
- Fixed number of threads (3 in example)
- Threads are reused for multiple tasks
- Unbounded queue (can grow indefinitely)
- Threads stay alive even when idle

**Use Case**: Processing 100 orders/minute with consistent arrival rate

**Example**:
```java
ExecutorService executor = Executors.newFixedThreadPool(3);

try {
    for (int i = 1; i <= 10; i++) {
        Order order = new Order("ORD-" + i, 99.99 * i);
        executor.execute(new PaymentProcessor(order));
    }
} finally {
    executor.shutdown();
    executor.awaitTermination(60, TimeUnit.SECONDS);
}
```

**Output Pattern**:
```
[pool-1-thread-1] Processing order ORD-1
[pool-1-thread-2] Processing order ORD-2
[pool-1-thread-3] Processing order ORD-3
[pool-1-thread-1] Processing order ORD-4  // Thread reused!
```

---

#### 2. CachedThreadPool

**Best for**: Short-lived async tasks, unpredictable bursts

```java
ExecutorService executor = Executors.newCachedThreadPool();
```

**Characteristics**:
- Creates new threads as needed
- Reuses idle threads (60-second timeout)
- No core threads
- Synchronous handoff (no queuing)

**Use Case**: Sporadic email notifications, webhook callbacks

**Example**:
```java
ExecutorService executor = Executors.newCachedThreadPool();

// Burst of 5 tasks
for (int i = 1; i <= 5; i++) {
    executor.execute(() -> sendEmail());
}

// After 1 second, threads might be reused
Thread.sleep(1000);

for (int i = 6; i <= 8; i++) {
    executor.execute(() -> sendEmail());
}
```

**Warning**: Can create too many threads under heavy load!

---

#### 3. SingleThreadExecutor

**Best for**: Sequential execution, ordering guarantees

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
```

**Characteristics**:
- Exactly one thread
- Tasks execute sequentially
- Unbounded queue
- Ordering guarantee

**Use Case**: Processing refunds (must be sequential for accounting)

**Example**:
```java
ExecutorService executor = Executors.newSingleThreadExecutor();

for (int i = 1; i <= 5; i++) {
    final int refundNum = i;
    executor.execute(() -> {
        System.out.println("Processing refund #" + refundNum);
        // Always executes in submission order
    });
}
```

---

#### 4. ScheduledThreadPool

**Best for**: Periodic tasks, delayed execution

```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
```

**Characteristics**:
- Supports delays and periodic execution
- Fixed thread pool
- Suitable for cron-like jobs

**Use Case**: Abandoned cart reminders, daily reports

**Example**:
```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

// One-time delay
scheduler.schedule(() -> {
    sendReminder();
}, 3, TimeUnit.SECONDS);

// Periodic execution
scheduler.scheduleAtFixedRate(() -> {
    checkAbandonedCarts();
}, 0, 5, TimeUnit.MINUTES);
```

---

### Executor Shutdown: Best Practices

**CRITICAL**: Always shutdown executors to prevent resource leaks!

#### Graceful Shutdown Pattern

```java
private static void shutdownExecutorGracefully(ExecutorService executor) {
    // 1. Initiate shutdown (no new tasks accepted)
    executor.shutdown();
    
    try {
        // 2. Wait for existing tasks (60 seconds)
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            // 3. Force shutdown if timeout
            executor.shutdownNow();
            
            // 4. Wait again for tasks to respond to interruption
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("Executor didn't terminate");
            }
        }
    } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

#### shutdown() vs shutdownNow()

| Method | Behavior | Use Case |
|--------|----------|----------|
| `shutdown()` | Graceful - completes queued tasks | Normal shutdown |
| `shutdownNow()` | Aggressive - attempts immediate stop | Emergency shutdown |

**Always use try-finally**:
```java
ExecutorService executor = Executors.newFixedThreadPool(5);

try {
    // Submit tasks
    executor.execute(task);
} finally {
    shutdownExecutorGracefully(executor);
}
```

---

### RejectedExecutionException

Occurs when:
1. Task submitted after shutdown
2. Thread pool saturated (bounded queue full)

**Example**:
```java
ExecutorService executor = Executors.newSingleThreadExecutor();
executor.shutdown(); // Shutdown immediately

try {
    executor.execute(() -> System.out.println("Won't run"));
} catch (RejectedExecutionException e) {
    System.err.println("Task rejected: " + e.getMessage());
}
```

---

## 4.2 Callable and Future

### The Problem with Runnable

```java
// Runnable limitations:
class PaymentTask implements Runnable {
    @Override
    public void run() {
        boolean success = processPayment();
        // ❌ Can't return success/failure
        // ❌ Can't throw checked exceptions
    }
}
```

### Solution: Callable<T>

```java
class PaymentCallable implements Callable<PaymentResult> {
    @Override
    public PaymentResult call() throws Exception {
        // ✅ Returns a value
        // ✅ Can throw checked exceptions
        
        if (paymentGatewayDown()) {
            throw new PaymentException("Gateway timeout");
        }
        
        return new PaymentResult(true, "TXN-12345");
    }
}
```

### Future<T>: Getting Results

**Future** represents the result of an asynchronous computation.

#### Basic Usage

```java
ExecutorService executor = Executors.newFixedThreadPool(3);

// Submit task
Future<PaymentResult> future = executor.submit(new PaymentCallable());

// Do other work
performOtherOperations();

// Get result (blocking call)
try {
    PaymentResult result = future.get(); // Waits if not ready
    System.out.println("Payment: " + result);
} catch (ExecutionException e) {
    // Task threw an exception
    System.err.println("Payment failed: " + e.getCause());
}
```

---

### Future Methods

#### 1. get() - Blocking Retrieval

```java
// Wait indefinitely
PaymentResult result = future.get();

// Wait with timeout
try {
    PaymentResult result = future.get(5, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    System.err.println("Payment took too long");
    future.cancel(true); // Cancel the task
}
```

#### 2. isDone() - Check Completion

```java
while (!future.isDone()) {
    System.out.println("Still processing...");
    Thread.sleep(500);
}

PaymentResult result = future.get(); // Won't block
```

#### 3. cancel() - Cancel Task

```java
boolean cancelled = future.cancel(true);
// Parameter: mayInterruptIfRunning
// true = interrupt the thread if running
// false = only cancel if not started

if (future.isCancelled()) {
    System.out.println("Task was cancelled");
}
```

---

### Parallel Execution Pattern

**Sequential** (slow):
```java
long start = System.currentTimeMillis();

PaymentResult payment = processPayment();        // 2000ms
InventoryResult inventory = checkInventory();    // 1000ms
ShippingResult shipping = generateLabel();       // 1500ms

// Total: 4500ms
```

**Parallel** (fast):
```java
ExecutorService executor = Executors.newFixedThreadPool(3);

long start = System.currentTimeMillis();

Future<PaymentResult> paymentFuture = 
    executor.submit(() -> processPayment());
    
Future<InventoryResult> inventoryFuture = 
    executor.submit(() -> checkInventory());
    
Future<ShippingResult> shippingFuture = 
    executor.submit(() -> generateLabel());

// All executing concurrently!

PaymentResult payment = paymentFuture.get();
InventoryResult inventory = inventoryFuture.get();
ShippingResult shipping = shippingFuture.get();

// Total: ~2000ms (longest task)
```

**Speedup**: 4500ms → 2000ms (2.25x faster!)

---

### submit() vs execute()

| Feature | execute(Runnable) | submit(Callable) | submit(Runnable) |
|---------|-------------------|------------------|------------------|
| Return value | void | Future<T> | Future<?> |
| Task type | Runnable only | Callable<T> | Runnable |
| Exception handling | Uncaught | Via Future.get() | Via Future.get() |
| Use case | Fire-and-forget | Need result | Need completion notification |

**Examples**:
```java
// execute() - no return value
executor.execute(() -> sendEmail());

// submit(Callable) - returns value
Future<Integer> result = executor.submit(() -> calculateSum());

// submit(Runnable) - completion notification only
Future<?> done = executor.submit(() -> sendEmail());
done.get(); // Blocks until email sent, returns null
```

---

### Batch Operations

#### invokeAll() - Wait for All

```java
List<Callable<PaymentResult>> tasks = Arrays.asList(
    new PaymentCallable(order1),
    new PaymentCallable(order2),
    new PaymentCallable(order3)
);

// Execute ALL and wait for ALL to complete
List<Future<PaymentResult>> futures = executor.invokeAll(tasks);

// All tasks are now complete
for (Future<PaymentResult> future : futures) {
    PaymentResult result = future.get(); // Won't block
}
```

**Use Case**: Batch processing where you need all results

---

#### invokeAny() - First Success Wins

```java
List<Callable<PaymentResult>> gateways = Arrays.asList(
    new StripeGateway(order),
    new PayPalGateway(order),
    new BraintreeGateway(order)
);

// Returns as soon as ONE succeeds
PaymentResult result = executor.invokeAny(gateways);

// Other tasks are cancelled
```

**Use Case**: Redundancy, failover, picking fastest service

---

### Error Handling Pattern

```java
Future<PaymentResult> future = executor.submit(new PaymentCallable());

try {
    PaymentResult result = future.get(5, TimeUnit.SECONDS);
    
    if (result.isSuccess()) {
        processOrder(result);
    } else {
        handleFailure(result.getErrorMessage());
    }
    
} catch (TimeoutException e) {
    future.cancel(true);
    log.error("Payment timeout for order {}", orderId);
    
} catch (ExecutionException e) {
    Throwable cause = e.getCause();
    log.error("Payment error: {}", cause.getMessage());
    
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    log.error("Thread interrupted");
}
```

---

## 4.3 ThreadPoolExecutor Deep Dive

### Understanding the Anatomy

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    2,                              // corePoolSize
    5,                              // maximumPoolSize
    60L,                            // keepAliveTime
    TimeUnit.SECONDS,               // time unit
    new ArrayBlockingQueue<>(100),  // workQueue
    new NamedThreadFactory(),       // threadFactory
    new ThreadPoolExecutor.CallerRunsPolicy()  // rejection handler
);
```

---

### Thread Pool Growth Strategy

**Critical Understanding**:

1. **Tasks ≤ corePoolSize** → Create new thread
2. **Tasks > corePoolSize** → Queue task
3. **Queue full** → Create thread (up to maxPoolSize)
4. **maxPoolSize reached + queue full** → Reject task

**Example Flow**:
```
corePoolSize = 2
maxPoolSize = 5
queueCapacity = 3

Task 1: Create thread 1 (pool size: 1)
Task 2: Create thread 2 (pool size: 2)
Task 3: Queue (queue size: 1)
Task 4: Queue (queue size: 2)
Task 5: Queue (queue size: 3)
Task 6: Create thread 3 (pool size: 3, queue full!)
Task 7: Create thread 4 (pool size: 4)
Task 8: Create thread 5 (pool size: 5, max reached!)
Task 9: REJECTED (pool full, queue full)
```

---

### Work Queue Types

#### 1. ArrayBlockingQueue (Bounded)

```java
new ArrayBlockingQueue<>(1000)
```

**Characteristics**:
- Fixed capacity
- Fairness option available
- Array-backed (contiguous memory)

**Pros**:
- Prevents memory overflow
- Predictable behavior

**Cons**:
- Tasks rejected when full
- Fixed size can be inefficient

**Use Case**: Production systems where predictability matters

---

#### 2. LinkedBlockingQueue (Optionally Bounded)

```java
new LinkedBlockingQueue<>()        // Unbounded
new LinkedBlockingQueue<>(1000)    // Bounded to 1000
```

**Characteristics**:
- Linked list structure
- Can be unbounded or bounded

**Pros**:
- Dynamic sizing (if unbounded)
- Good throughput

**Cons**:
- **Unbounded = OutOfMemoryError risk**
- Pool never grows beyond core size (unbounded queue never fills!)

**Use Case**: Low to moderate load systems

---

#### 3. SynchronousQueue (Zero Capacity)

```java
new SynchronousQueue<>()
```

**Characteristics**:
- No internal capacity
- Direct thread handoff
- Producer blocks until consumer available

**Pros**:
- Pool grows quickly
- Low latency
- Used in CachedThreadPool

**Cons**:
- Can create many threads

**Use Case**: CachedThreadPool pattern, many short tasks

---

#### 4. PriorityBlockingQueue (Priority-Based)

```java
new PriorityBlockingQueue<>()
```

**Characteristics**:
- Tasks processed by priority
- Requires Comparable tasks
- Unbounded

**Use Case**: VIP customer orders processed first

```java
class PriorityOrder implements Runnable, Comparable<PriorityOrder> {
    private int priority;
    
    @Override
    public int compareTo(PriorityOrder other) {
        return Integer.compare(other.priority, this.priority); // High first
    }
}
```

---

### Rejection Policies

When pool + queue are full, what happens?

#### 1. AbortPolicy (Default)

```java
new ThreadPoolExecutor.AbortPolicy()
```

**Behavior**: Throws `RejectedExecutionException`

**Use Case**: Critical tasks that must be processed

```java
try {
    executor.execute(criticalTask);
} catch (RejectedExecutionException e) {
    log.error("System overloaded, task rejected");
    sendAlert("Thread pool saturated");
}
```

---

#### 2. CallerRunsPolicy

```java
new ThreadPoolExecutor.CallerRunsPolicy()
```

**Behavior**: Caller thread executes the task

**Use Case**: Provides natural backpressure

```java
// If pool full, main thread executes the task
// This slows down task submission naturally
executor.execute(task); // Might run in main thread!
```

**Effect**: Slows down producer, prevents overwhelming system

---

#### 3. DiscardPolicy

```java
new ThreadPoolExecutor.DiscardPolicy()
```

**Behavior**: Silently drops the task

**Use Case**: Non-critical tasks (e.g., analytics events)

**Warning**: No exception, no log - task just disappears!

---

#### 4. DiscardOldestPolicy

```java
new ThreadPoolExecutor.DiscardOldestPolicy()
```

**Behavior**: Drops oldest queued task, retries new task

**Use Case**: Time-sensitive data where new is more valuable

---

### Thread Pool Sizing Formulas

#### CPU-Bound Tasks

```
Optimal threads = CPU cores + 1
```

**Reasoning**: One thread per core + 1 for occasional I/O

**Example**:
```java
int cpuCount = Runtime.getRuntime().availableProcessors();
int poolSize = cpuCount + 1; // e.g., 4 cores → 5 threads
```

---

#### I/O-Bound Tasks

```
Optimal threads = CPU cores × (1 + wait_time / compute_time)
```

**Example**: Order processing (wait 3 seconds, compute 1 second)
```java
int cpuCount = Runtime.getRuntime().availableProcessors(); // 4
double waitTime = 3.0;
double computeTime = 1.0;

int poolSize = (int) (cpuCount * (1 + waitTime / computeTime));
// = 4 × (1 + 3/1) = 4 × 4 = 16 threads
```

---

#### Mixed Workload

**Start with**:
```
Threads = 2 × CPU cores
```

**Then tune** based on monitoring:
- High CPU usage → Reduce threads
- Low CPU, high latency → Increase threads
- Monitor: queue size, rejection rate, response time

---

### Production Configuration Example

```java
int cpuCount = Runtime.getRuntime().availableProcessors();
int corePoolSize = cpuCount * 2;        // Start conservatively
int maxPoolSize = cpuCount * 4;         // Allow growth
int queueCapacity = 1000;               // Prevent OOM

ThreadPoolExecutor executor = new ThreadPoolExecutor(
    corePoolSize,
    maxPoolSize,
    120L,                               // 2 minutes keep-alive
    TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(queueCapacity),
    new NamedThreadFactory("OrderProcessor"),
    new ThreadPoolExecutor.CallerRunsPolicy()  // Backpressure
);

// Optional: Let core threads die when idle
executor.allowCoreThreadTimeOut(true);

// Monitoring
ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
monitor.scheduleAtFixedRate(() -> {
    log.info("Pool: size={}, active={}, queue={}, completed={}",
        executor.getPoolSize(),
        executor.getActiveCount(),
        executor.getQueue().size(),
        executor.getCompletedTaskCount());
}, 0, 30, TimeUnit.SECONDS);
```

---

### Custom ThreadFactory

**Why**: Named threads for better debugging

```java
class NamedThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;
    
    public NamedThreadFactory(String namePrefix) {
        this.namePrefix = namePrefix;
    }
    
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, 
            namePrefix + "-" + threadNumber.getAndIncrement());
        
        t.setDaemon(false);              // Non-daemon
        t.setPriority(Thread.NORM_PRIORITY);  // Normal priority
        
        return t;
    }
}
```

**Usage**:
```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5, 10, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(),
    new NamedThreadFactory("PaymentProcessor")
);

// Thread names: PaymentProcessor-1, PaymentProcessor-2, ...
```

**Benefits**:
- Easy identification in thread dumps
- Better logging
- Debugging made easier

---

## 4.4 ScheduledExecutorService

### The Three Scheduling Methods

#### 1. schedule() - One-Time Delay

```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

// Execute once after 3 seconds
scheduler.schedule(() -> {
    sendConfirmationEmail();
}, 3, TimeUnit.SECONDS);
```

**Use Cases**:
- Order confirmation after payment
- Session timeout
- Retry after delay

---

#### 2. scheduleAtFixedRate() - Fixed Start Intervals

```java
scheduler.scheduleAtFixedRate(() -> {
    syncInventory();
}, 0, 5, TimeUnit.SECONDS);
// initialDelay: 0 seconds (start immediately)
// period: 5 seconds
```

**Execution Timeline**:
```
Time:  0s    5s    10s   15s   20s
       |-----|-----|-----|-----|
       Start Start Start Start Start
```

**Behavior**:
- Starts every 5 seconds **regardless of execution time**
- If task takes > 5s, next execution starts immediately after

**Example**:
```
0s:  Task 1 starts
2s:  Task 1 completes
5s:  Task 2 starts (on schedule)
8s:  Task 2 completes
10s: Task 3 starts (on schedule)
```

**If task takes 7 seconds**:
```
0s:  Task 1 starts
7s:  Task 1 completes
7s:  Task 2 starts (immediately, was scheduled at 5s)
14s: Task 2 completes
15s: Task 3 starts (on schedule)
```

**Warning**: Tasks can overlap if pool size > 1!

---

#### 3. scheduleWithFixedDelay() - Fixed Gap Between Executions

```java
scheduler.scheduleWithFixedDelay(() -> {
    processAbandonedCarts();
}, 0, 5, TimeUnit.SECONDS);
// initialDelay: 0 seconds
// delay: 5 seconds AFTER completion
```

**Execution Timeline**:
```
Task 1: 0s - 3s (3s duration)
Delay:  3s - 8s (5s delay)
Task 2: 8s - 11s (3s duration)
Delay:  11s - 16s (5s delay)
Task 3: 16s - 19s (3s duration)
```

**Behavior**:
- Waits for task to complete
- **Then** waits 5 seconds
- **Then** starts next execution

**Benefit**: Prevents overlapping executions

---

### Comparison: Fixed Rate vs Fixed Delay

| Scenario | Fixed Rate | Fixed Delay |
|----------|------------|-------------|
| Task: 1s, Period/Delay: 5s | Every 5s | Every 6s (1s task + 5s delay) |
| Task: 7s, Period/Delay: 5s | Every 7s | Every 12s (7s task + 5s delay) |
| Overlapping | Possible | Never |
| Clock changes | Affected | Not affected |

**When to use**:
- **Fixed Rate**: Precise timing needed (heartbeat, animation)
- **Fixed Delay**: Variable task duration (polling, batch processing)

---

### Exception Handling in Scheduled Tasks

**CRITICAL BUG**:
```java
// ❌ BAD: Exception kills the task permanently!
scheduler.scheduleAtFixedRate(() -> {
    if (new Random().nextBoolean()) {
        throw new RuntimeException("Oops");
    }
    processData();
}, 0, 1, TimeUnit.SECONDS);

// After exception: task stops forever, no more executions!
```

**Correct Pattern**:
```java
// ✅ GOOD: Always wrap in try-catch
scheduler.scheduleAtFixedRate(() -> {
    try {
        if (new Random().nextBoolean()) {
            throw new RuntimeException("Oops");
        }
        processData();
    } catch (Exception e) {
        log.error("Error in scheduled task", e);
        // Task continues executing on next schedule
    }
}, 0, 1, TimeUnit.SECONDS);
```

**Rule**: **ALWAYS** wrap scheduled task logic in try-catch!

---

### Cancelling Scheduled Tasks

```java
ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
    checkAbandonedCarts();
}, 0, 5, TimeUnit.MINUTES);

// Later, cancel the task
task.cancel(false);  // Don't interrupt if running

if (task.isCancelled()) {
    System.out.println("Task successfully cancelled");
}
```

**Parameters**:
- `cancel(true)`: Interrupt if currently running
- `cancel(false)`: Let current execution finish

---

### Production Scheduler Example

```java
public class BackgroundJobScheduler {
    private final ScheduledExecutorService scheduler;
    private final List<ScheduledFuture<?>> scheduledTasks;
    
    public BackgroundJobScheduler() {
        this.scheduler = Executors.newScheduledThreadPool(3,
            new NamedThreadFactory("BackgroundJob"));
        this.scheduledTasks = new ArrayList<>();
    }
    
    public void start() {
        // Job 1: Session cleanup every 5 minutes
        ScheduledFuture<?> sessionCleanup = scheduler.scheduleWithFixedDelay(
            this::cleanupExpiredSessions,
            0, 5, TimeUnit.MINUTES
        );
        scheduledTasks.add(sessionCleanup);
        
        // Job 2: Price sync every 30 seconds
        ScheduledFuture<?> priceSync = scheduler.scheduleAtFixedRate(
            this::syncProductPrices,
            0, 30, TimeUnit.SECONDS
        );
        scheduledTasks.add(priceSync);
        
        // Job 3: Daily sales report at 2 AM
        long delayUntil2AM = calculateDelayUntil2AM();
        ScheduledFuture<?> dailyReport = scheduler.scheduleAtFixedRate(
            this::generateSalesReport,
            delayUntil2AM, 24, TimeUnit.HOURS
        );
        scheduledTasks.add(dailyReport);
    }
    
    private void cleanupExpiredSessions() {
        try {
            log.info("Cleaning up expired sessions...");
            int cleaned = sessionService.removeExpired();
            log.info("Cleaned {} sessions", cleaned);
        } catch (Exception e) {
            log.error("Session cleanup failed", e);
        }
    }
    
    private void syncProductPrices() {
        try {
            log.info("Syncing product prices...");
            int updated = priceService.syncFromWarehouse();
            log.info("Updated {} prices", updated);
        } catch (Exception e) {
            log.error("Price sync failed", e);
        }
    }
    
    private void generateSalesReport() {
        try {
            log.info("Generating daily sales report...");
            Report report = reportService.generateDailyReport();
            emailService.send(report, "management@company.com");
            log.info("Daily report sent");
        } catch (Exception e) {
            log.error("Report generation failed", e);
        }
    }
    
    public void shutdown() {
        log.info("Cancelling {} scheduled tasks", scheduledTasks.size());
        
        for (ScheduledFuture<?> task : scheduledTasks) {
            task.cancel(false);
        }
        
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Background job scheduler shut down");
    }
}
```

---

## Summary & Key Takeaways

### Executor Framework

✅ **Use Cases**:
- **FixedThreadPool**: Predictable load (10 orders/second)
- **CachedThreadPool**: Bursty tasks (webhook callbacks)
- **SingleThreadExecutor**: Sequential processing (accounting)
- **ScheduledThreadPool**: Periodic jobs (cron tasks)

✅ **Best Practices**:
1. Always shutdown executors
2. Use try-finally for cleanup
3. Handle RejectedExecutionException
4. Monitor pool metrics

---

### Callable & Future

✅ **Key Differences**:

| Feature | Runnable | Callable |
|---------|----------|----------|
| Return value | ❌ No | ✅ Yes (T) |
| Checked exceptions | ❌ No | ✅ Yes |
| Method | run() | call() |

✅ **Future Methods**:
- `get()`: Block until result available
- `get(timeout)`: Block with timeout
- `isDone()`: Check completion
- `cancel()`: Cancel task
- `isCancelled()`: Check if cancelled

✅ **Best Practices**:
1. Use timeouts to prevent blocking forever
2. Handle ExecutionException
3. Use invokeAll() for batch processing
4. Use invokeAny() for failover

---

### ThreadPoolExecutor

✅ **Growth Strategy**:
```
Tasks ≤ core → Create thread
Tasks > core → Queue
Queue full → Create thread (up to max)
Max reached → Reject
```

✅ **Sizing Formulas**:
```
CPU-bound:  cores + 1
I/O-bound:  cores × (1 + wait/compute)
Mixed:      Start with cores × 2, tune
```

✅ **Queue Choice**:
- **ArrayBlockingQueue**: Bounded, prevents OOM
- **LinkedBlockingQueue**: Unbounded, risky
- **SynchronousQueue**: Direct handoff, grows fast
- **PriorityBlockingQueue**: Priority-based

✅ **Rejection Policies**:
- **AbortPolicy**: Throw exception (default)
- **CallerRunsPolicy**: Caller executes (backpressure)
- **DiscardPolicy**: Silent drop
- **DiscardOldestPolicy**: Drop oldest

---

### ScheduledExecutorService

✅ **Methods**:
```
schedule()              → One-time delay
scheduleAtFixedRate()   → Fixed start times
scheduleWithFixedDelay() → Fixed gap between runs
```

✅ **Critical Rules**:
1. **ALWAYS** wrap task in try-catch
2. Store ScheduledFuture for cancellation
3. Use fixed delay for variable-duration tasks
4. Cancel tasks before shutdown

✅ **Common Mistake**:
```java
// ❌ Exception kills task forever
scheduler.scheduleAtFixedRate(() -> {
    riskyOperation(); // Throws exception
}, 0, 1, TimeUnit.MINUTES);

// ✅ Correct: Catch exceptions
scheduler.scheduleAtFixedRate(() -> {
    try {
        riskyOperation();
    } catch (Exception e) {
        log.error("Error", e);
    }
}, 0, 1, TimeUnit.MINUTES);
```

---

## Practice Project

### Build: Complete Order Processing System

**Requirements**:

1. **Thread Pool Configuration**
   - FixedThreadPool for order processing (10 threads)
   - Calculate optimal size based on I/O characteristics
   - Custom ThreadFactory for named threads
   - CallerRunsPolicy for backpressure

2. **Order Processing Pipeline**
   - Each order requires 3 steps:
     - Payment validation (Callable, 2 seconds)
     - Inventory check (Callable, 1 second)
     - Shipping label (Callable, 1.5 seconds)
   - Use Future to get results from each step
   - Handle timeouts (5 seconds max per step)
   - Aggregate success/failure statistics

3. **Scheduled Background Jobs**
   - Abandoned cart checker (every 5 minutes)
   - Inventory sync (every 30 seconds)
   - Daily sales report (once per day at 2 AM)
   - All with proper exception handling

4. **Monitoring Dashboard**
   - Track: orders processed, failures, avg processing time
   - Pool metrics: size, active, queue length
   - Scheduled task status
   - Print stats every 10 seconds

5. **Graceful Shutdown**
   - Cancel all scheduled tasks
   - Wait for in-flight orders (60 seconds)
   - Force shutdown if needed
   - Print final statistics

**Starter Template**:

```java
public class OrderProcessingSystem {
    private final ThreadPoolExecutor orderPool;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger ordersProcessed = new AtomicInteger();
    private final AtomicInteger ordersFailed = new AtomicInteger();
    
    public OrderProcessingSystem() {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        
        // TODO: Configure thread pool
        this.orderPool = new ThreadPoolExecutor(
            // Your configuration here
        );
        
        // TODO: Configure scheduler
        this.scheduler = Executors.newScheduledThreadPool(3);
    }
    
    public Future<OrderResult> processOrder(Order order) {
        return orderPool.submit(() -> {
            try {
                // TODO: Implement payment, inventory, shipping
                return new OrderResult(true, order.getId());
            } catch (Exception e) {
                ordersFailed.incrementAndGet();
                throw e;
            }
        });
    }
    
    public void startBackgroundJobs() {
        // TODO: Schedule abandoned cart checker
        // TODO: Schedule inventory sync
        // TODO: Schedule daily report
    }
    
    public void shutdown() {
        // TODO: Implement graceful shutdown
    }
    
    public static void main(String[] args) {
        OrderProcessingSystem system = new OrderProcessingSystem();
        system.startBackgroundJobs();
        
        // Simulate order processing
        for (int i = 0; i < 100; i++) {
            Order order = new Order("ORD-" + i, 99.99);
            system.processOrder(order);
        }
        
        // Keep running for 5 minutes
        Thread.sleep(5 * 60 * 1000);
        
        system.shutdown();
    }
}
```

**Bonus Challenges**:
1. Add circuit breaker for payment gateway failures
2. Implement retry logic with exponential backoff
3. Add priority queue for VIP orders
4. Create metrics dashboard with real-time updates
5. Implement dynamic pool sizing based on load

---

## Next Steps

### Phase 5: Advanced Synchronizers

You're ready to learn:

1. **CountDownLatch**
   - Coordinate multiple threads
   - Wait for initialization to complete
   - One-time synchronization point

2. **CyclicBarrier**
   - Reusable synchronization barrier
   - Parallel algorithm phases
   - Thread rendezvous point

3. **Semaphore**
   - Limit concurrent access
   - Resource pool management
   - Rate limiting

4. **Phaser**
   - Dynamic participant registration
   - Multi-phase coordination
   - Flexible alternative to barriers

5. **Exchanger**
   - Two-thread data exchange
   - Producer-consumer pairs
   - Bidirectional handoff

**What to expect**: Real-world examples using parallel data processing, connection pool management, and multi-stage pipeline processing.

---

## Additional Resources

### Essential Reading
- **Java Concurrency in Practice** by Brian Goetz (Chapters 6-8)
- Java Docs: java.util.concurrent package
- Doug Lea's papers on Fork/Join

### Practice Platforms
- LeetCode: Concurrency problems
- HackerRank: Multithreading challenges
- Project Euler: Parallelizable algorithms

### Tools
- VisualVM: Thread monitoring
- Java Mission Control: Performance profiling
- jcstress: Concurrency stress testing

---

## Quick Reference Card

### Thread Pool Selection

```
Steady workload, CPU-bound → FixedThreadPool
Bursty, short tasks → CachedThreadPool
Sequential guarantee → SingleThreadExecutor
Scheduled/periodic → ScheduledThreadPool
Custom requirements → ThreadPoolExecutor
```

### Sizing Formula

```
Threads = Cores × (1 + WaitTime/ComputeTime)
```

### Rejection Policies

```
Must process → AbortPolicy + catch
Natural backpressure → CallerRunsPolicy
Can drop → DiscardPolicy
Newest matters → DiscardOldestPolicy
```

### Scheduling

```
One-time delay → schedule()
Fixed intervals → scheduleAtFixedRate()
Gap between runs → scheduleWithFixedDelay()
```

### Shutdown Pattern

```java
executor.shutdown();
if (!executor.awaitTermination(60, SECONDS)) {
    executor.shutdownNow();
}
```

---

**Phase 4 Complete! Ready for Phase 5? Let's continue! 🚀**