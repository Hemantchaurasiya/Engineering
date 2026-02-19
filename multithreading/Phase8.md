# Phase 8: Atomic Variables and CAS - Complete Mastery Guide

## Table of Contents
1. [Introduction](#introduction)
2. [8.1 Atomic Classes](#81-atomic-classes)
3. [8.2 Compare-and-Swap (CAS)](#82-compare-and-swap-cas)
4. [8.3 AtomicReference](#83-atomicreference)
5. [8.4 LongAdder](#84-longadder)
6. [8.5 AtomicFieldUpdater](#85-atomicfieldupdater)
7. [Deep Dive: PooledObject Explained](#deep-dive-pooledobject-explained)
8. [Summary and Best Practices](#summary-and-best-practices)
9. [Practice Exercises](#practice-exercises)

---

## Introduction

Phase 8 focuses on Java's atomic variables and Compare-and-Swap operations - the foundation of lock-free concurrent programming. These tools enable high-performance, thread-safe operations without the overhead of traditional locking.

**Why Atomic Variables Matter:**
- 2-10x faster than synchronized blocks under high contention
- No deadlock risk (lock-free)
- Better scalability on multi-core systems
- Foundation for building advanced concurrent data structures

---

## 8.1 Atomic Classes

### Real-World Example: Website Visit Counter

**Problem:** Tracking website visits across multiple threads without data corruption.

### The Wrong Ways

#### 1. Non-Thread-Safe (Broken)
```java
class UnsafeCounter {
    private long visitCount = 0;
    
    public void recordVisit() {
        visitCount++; // NOT ATOMIC! Three operations:
                      // 1. Read visitCount
                      // 2. Add 1
                      // 3. Write back
    }
    
    public long getVisits() {
        return visitCount;
    }
}
```
**Problem:** Lost updates. With 100 threads doing 10,000 increments each, you might get 950,000 instead of 1,000,000.

#### 2. Volatile (Still Broken!)
```java
class VolatileCounter {
    private volatile long visitCount = 0;
    
    public void recordVisit() {
        visitCount++; // Volatile ensures VISIBILITY, NOT ATOMICITY
    }
}
```
**Problem:** Volatile guarantees visibility but `++` is still three operations. Still loses updates.

#### 3. Synchronized (Works but Slow)
```java
class SynchronizedCounter {
    private long visitCount = 0;
    
    public synchronized void recordVisit() {
        visitCount++;
    }
    
    public synchronized long getVisits() {
        return visitCount;
    }
}
```
**Problem:** Works correctly but requires lock acquisition/release on every operation. Slow under contention.

### The Right Way: AtomicLong

```java
class AtomicCounter {
    private final AtomicLong visitCount = new AtomicLong(0);
    
    public void recordVisit() {
        visitCount.incrementAndGet(); // Single atomic operation
    }
    
    public long getVisits() {
        return visitCount.get();
    }
}
```

### Key AtomicLong Methods

```java
AtomicLong counter = new AtomicLong(0);

// Basic operations
counter.get();                    // Read current value
counter.set(10);                  // Set value
counter.incrementAndGet();        // ++counter (returns new value)
counter.getAndIncrement();        // counter++ (returns old value)
counter.addAndGet(5);            // Add and return new value
counter.compareAndSet(10, 20);   // CAS operation (explained next)

// Advanced: Functional updates
counter.updateAndGet(current -> current * 2);  // Double the value
counter.accumulateAndGet(5, (current, x) -> current + x); // Add 5
```

### Performance Comparison

```
Configuration: 100 threads, 10,000 increments per thread

Results:
Unsafe Counter      :     985,342 | ✗ WRONG | Time: 150 ms
Volatile Counter    :     991,876 | ✗ WRONG | Time: 152 ms
Synchronized Counter: 1,000,000   | ✓ CORRECT | Time: 890 ms
Atomic Counter      : 1,000,000   | ✓ CORRECT | Time: 320 ms

AtomicLong is 2.8x faster than synchronized!
```

### When to Use Atomic Classes

✅ **Use AtomicXxx when:**
- Single variable needs atomicity
- High contention scenarios
- Building lock-free algorithms
- Simple counters, flags, references

❌ **Don't use when:**
- Need to update multiple variables atomically (use locks)
- Low contention (overhead not worth it)
- Complex state transitions

---

## 8.2 Compare-and-Swap (CAS)

### What is CAS?

Compare-and-Swap is a CPU-level atomic instruction that enables lock-free programming.

**CAS Operation:**
```
compareAndSet(expectedValue, newValue):
    if (currentValue == expectedValue) {
        currentValue = newValue;
        return true;
    } else {
        return false;
    }
```

**Key:** All three steps (read, compare, write) happen atomically!

### Real-World Example: Lock-Free Stack

Used in high-performance scenarios like:
- Object pooling
- Thread-local caches
- Message queues

```java
public class LockFreeStack<T> {
    private static class Node<T> {
        final T value;
        Node<T> next;
        
        Node(T value, Node<T> next) {
            this.value = value;
            this.next = next;
        }
    }
    
    private final AtomicReference<Node<T>> head = new AtomicReference<>();
    
    public void push(T value) {
        Node<T> newHead = new Node<>(value, null);
        Node<T> oldHead;
        
        // CAS retry loop
        do {
            oldHead = head.get();        // 1. Read current head
            newHead.next = oldHead;      // 2. Link new node
        } while (!head.compareAndSet(oldHead, newHead)); // 3. Atomic update
        
        // If another thread modified head between steps 1 and 3,
        // CAS returns false and we retry with new value
    }
    
    public T pop() {
        Node<T> oldHead;
        Node<T> newHead;
        
        do {
            oldHead = head.get();
            if (oldHead == null) {
                return null; // Stack empty
            }
            newHead = oldHead.next;
        } while (!head.compareAndSet(oldHead, newHead));
        
        return oldHead.value;
    }
}
```

### The CAS Pattern

```java
// Standard CAS loop pattern
do {
    oldValue = atomicVariable.get();           // 1. Read
    newValue = computeNewValue(oldValue);      // 2. Compute
} while (!atomicVariable.compareAndSet(oldValue, newValue)); // 3. Update
```

### The ABA Problem

**Scenario:**
1. Thread 1 reads value A
2. Thread 2 changes A → B → A (back to A)
3. Thread 1's CAS succeeds (thinks nothing changed!)
4. But B happened in between!

**Example:**
```
Stack: [A] → [B] → [C]

Thread 1: Reads head = A, prepares to pop
Thread 2: Pops A, pops B, pushes A back
          Stack is now: [A] → [C]
Thread 1: CAS succeeds (head still A), but B was lost!
```

**Solution: AtomicStampedReference**

```java
class ABAProofStack<T> {
    // Combines reference + version stamp
    private final AtomicStampedReference<Node<T>> head;
    
    public ABAProofStack() {
        this.head = new AtomicStampedReference<>(null, 0);
    }
    
    public void push(T value) {
        Node<T> newHead = new Node<>(value, null);
        int[] stampHolder = new int[1];
        
        while (true) {
            Node<T> oldHead = head.get(stampHolder);
            int oldStamp = stampHolder[0];
            
            newHead.next = oldHead;
            
            // CAS with stamp - fails if value OR stamp changed
            if (head.compareAndSet(oldHead, newHead, oldStamp, oldStamp + 1)) {
                break;
            }
        }
    }
}
```

### Lock-Free vs Wait-Free

- **Lock-Free:** System makes progress (at least one thread succeeds)
- **Wait-Free:** Every thread makes progress (stronger guarantee)
- Most practical algorithms are lock-free, not wait-free

---

## 8.3 AtomicReference

### Real-World Example: Configuration Hot-Reload

Production systems need to update configuration without restart:
- Feature flags
- Rate limits
- Timeouts
- API endpoints

### Immutable Configuration Pattern

```java
public class AppConfig {
    private final int maxConnections;
    private final int requestTimeoutMs;
    private final boolean maintenanceMode;
    private final Map<String, String> featureFlags;
    private final long version;
    
    public AppConfig(int maxConnections, int requestTimeoutMs, 
                     boolean maintenanceMode, Map<String, String> featureFlags,
                     long version) {
        this.maxConnections = maxConnections;
        this.requestTimeoutMs = requestTimeoutMs;
        this.maintenanceMode = maintenanceMode;
        this.featureFlags = Map.copyOf(featureFlags); // Defensive copy
        this.version = version;
    }
    
    // Only getters, no setters (immutable)
    public int getMaxConnections() { return maxConnections; }
    public int getRequestTimeoutMs() { return requestTimeoutMs; }
    // ... more getters
}
```

### Configuration Manager

```java
public class ConfigurationManager {
    private final AtomicReference<AppConfig> currentConfig;
    
    public ConfigurationManager(AppConfig initialConfig) {
        this.currentConfig = new AtomicReference<>(initialConfig);
    }
    
    // Lock-free read (always gets consistent snapshot)
    public AppConfig getConfig() {
        return currentConfig.get();
    }
    
    // Atomic update
    public void updateConfig(AppConfig newConfig) {
        AppConfig oldConfig = currentConfig.getAndSet(newConfig);
        notifyListeners(oldConfig, newConfig);
    }
    
    // Conditional update (optimistic locking)
    public boolean updateConfigIfVersion(long expectedVersion, 
                                         AppConfig newConfig) {
        while (true) {
            AppConfig current = currentConfig.get();
            
            if (current.getVersion() != expectedVersion) {
                return false; // Version mismatch
            }
            
            if (currentConfig.compareAndSet(current, newConfig)) {
                notifyListeners(current, newConfig);
                return true;
            }
            // Retry if concurrent update
        }
    }
    
    // Functional update
    public AppConfig updateAndGet(UnaryOperator<AppConfig> updater) {
        while (true) {
            AppConfig current = currentConfig.get();
            AppConfig updated = updater.apply(current);
            
            if (currentConfig.compareAndSet(current, updated)) {
                return updated;
            }
        }
    }
}
```

### Usage Example

```java
// Application reads config
AppConfig config = configMgr.getConfig();
if (config.isMaintenanceMode()) {
    rejectRequest();
} else {
    processRequest(config.getRequestTimeoutMs());
}

// Admin updates config (no restart needed!)
AppConfig newConfig = new AppConfig.Builder()
    .requestTimeoutMs(10000) // Increased timeout
    .version(config.getVersion() + 1)
    .build();
configMgr.updateConfig(newConfig);

// Functional update
configMgr.updateAndGet(current -> 
    new AppConfig.Builder()
        .maxConnections(current.getMaxConnections() * 2)
        .version(current.getVersion() + 1)
        .build()
);
```

### Key Benefits

1. **Lock-free reads** - No blocking, ever
2. **Atomic updates** - Readers see old OR new, never partial state
3. **Immutability** - Thread-safe by design
4. **Versioning** - Prevents lost updates

---

## 8.4 LongAdder

### The Problem with AtomicLong

Under **high contention**, AtomicLong becomes a bottleneck:
- All threads compete for the same memory location
- CPU cache line bounces between cores
- CAS retry loops increase with contention

### How LongAdder Works

**Cell-Based Architecture:**

```
Traditional AtomicLong:
[Counter] ← All threads update this single location

LongAdder:
Thread 1 → [Cell 0]
Thread 2 → [Cell 1]
Thread 3 → [Cell 2]    sum() = Cell0 + Cell1 + Cell2 + ... + base
Thread 4 → [Cell 3]
...
```

**Key Ideas:**
1. Maintains array of "Cell" objects (striped counter)
2. Each thread updates different cell (reduced contention)
3. `sum()` aggregates all cells for total
4. Trades read performance for write performance

### Real-World Example: High-Performance Metrics

```java
public class MetricsSystem {
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    
    // Increment counter (very fast under contention)
    public void incrementCounter(String metricName) {
        counters.computeIfAbsent(metricName, k -> new LongAdder())
                .increment();
    }
    
    // Get snapshot (slower, aggregates all cells)
    public Map<String, Long> getSnapshot() {
        Map<String, Long> snapshot = new HashMap<>();
        counters.forEach((name, adder) -> 
            snapshot.put(name, adder.sum())
        );
        return snapshot;
    }
}

// Usage in high-traffic API server
metrics.incrementCounter("http.requests.total");
metrics.incrementCounter("http.requests./api/users");
metrics.recordLatency("latency./api/users", 45);
```

### Performance Comparison

```
Configuration: 50 threads, 1,000,000 increments per thread

Results:
AtomicLong: 2,450 ms | 20,408,163 ops/sec
LongAdder:    890 ms | 56,179,775 ops/sec

LongAdder is 2.75x faster under high contention!
```

### When to Use LongAdder

✅ **Use LongAdder for:**
- High-frequency counters (metrics, statistics)
- Many threads updating same counter
- Writes >> Reads (write-heavy workloads)
- Performance-critical counting

❌ **Use AtomicLong for:**
- Frequent reads of exact value
- Low contention scenarios
- When memory is extremely constrained
- Coordinating state (not just counting)

### LongAdder vs LongAccumulator

```java
// LongAdder - optimized for addition
LongAdder sum = new LongAdder();
sum.add(10);
sum.increment();

// LongAccumulator - custom accumulation function
LongAccumulator max = new LongAccumulator(Long::max, Long.MIN_VALUE);
max.accumulate(42);
max.accumulate(100);
max.accumulate(17);
System.out.println(max.get()); // 100

// Use cases for LongAccumulator:
// - Max/min tracking
// - Custom aggregation logic
// - Complex accumulation functions
```

---

## 8.5 AtomicFieldUpdater

### The Memory Problem

Consider an object pool with 10,000 connections:

```java
// Using AtomicBoolean (16 bytes per instance)
class PooledObject {
    T object;
    AtomicBoolean available = new AtomicBoolean(true);
}
// Memory: 10,000 × 16 bytes = 160 KB

// Using volatile + AtomicFieldUpdater (1 byte per instance)
class PooledObject {
    T object;
    volatile boolean available = true; // Just 1 byte
}
// Memory: 10,000 × 1 byte = 10 KB
// Savings: 150 KB per pool!
```

### Real-World Example: Object Pool

```java
public class LockFreeObjectPool<T> {
    private static class PooledObject<T> {
        final T object;
        volatile boolean available; // MUST be volatile
        volatile PooledObject<T> next; // MUST be volatile
        
        PooledObject(T object) {
            this.object = object;
            this.available = true;
        }
    }
    
    // Static field updater (shared across all instances)
    private static final AtomicIntegerFieldUpdater<PooledObject> AVAILABLE_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(
            PooledObject.class,  // Target class
            "available"          // Field name
        );
    
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<PooledObject, PooledObject> NEXT_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(
            PooledObject.class,
            PooledObject.class,
            "next"
        );
    
    private final AtomicReference<PooledObject<T>> freeList;
    private final Supplier<T> factory;
    private final int maxSize;
    
    public T acquire() {
        while (true) {
            PooledObject<T> head = freeList.get();
            if (head == null) {
                return createNew(); // Pool empty
            }
            
            PooledObject<T> next = head.next;
            if (freeList.compareAndSet(head, next)) {
                AVAILABLE_UPDATER.set(head, 0); // Mark unavailable
                return head.object;
            }
        }
    }
    
    public void release(T object) {
        PooledObject<T> pooledObj = new PooledObject<>(object);
        
        while (true) {
            PooledObject<T> head = freeList.get();
            pooledObj.next = head;
            
            if (freeList.compareAndSet(head, pooledObj)) {
                AVAILABLE_UPDATER.set(pooledObj, 1); // Mark available
                break;
            }
        }
    }
}
```

### Types of Field Updaters

```java
// 1. AtomicIntegerFieldUpdater
class Counter {
    volatile int count = 0;
}
AtomicIntegerFieldUpdater<Counter> updater = 
    AtomicIntegerFieldUpdater.newUpdater(Counter.class, "count");

Counter c = new Counter();
updater.incrementAndGet(c);
updater.compareAndSet(c, 0, 10);

// 2. AtomicLongFieldUpdater
class Timer {
    volatile long timestamp = 0;
}
AtomicLongFieldUpdater<Timer> updater = 
    AtomicLongFieldUpdater.newUpdater(Timer.class, "timestamp");

// 3. AtomicReferenceFieldUpdater
class Node {
    volatile Node next = null;
}
AtomicReferenceFieldUpdater<Node, Node> updater = 
    AtomicReferenceFieldUpdater.newUpdater(
        Node.class, Node.class, "next"
    );
```

### Requirements for Field Updaters

1. **Field MUST be volatile**
```java
volatile int count; // ✓ Correct
int count;          // ✗ Won't work
```

2. **Field must be accessible**
```java
public volatile int count;    // ✓ Works
protected volatile int count; // ✓ Works
volatile int count;           // ✓ Works (package-private)
private volatile int count;   // ✗ Won't work if updater in different class
```

3. **Updater is static (class-level)**
```java
// ✓ Correct - one updater for all instances
private static final AtomicIntegerFieldUpdater<Counter> UPDATER = ...;

// ✗ Wrong - creates updater per instance (wasteful)
private final AtomicIntegerFieldUpdater<Counter> updater = ...;
```

### Advanced Example: Circuit Breaker

```java
class CircuitBreaker {
    private static final int CLOSED = 0;
    private static final int OPEN = 1;
    private static final int HALF_OPEN = 2;
    
    private volatile int state = CLOSED;
    private volatile long lastFailureTime = 0;
    private volatile int consecutiveFailures = 0;
    
    private static final AtomicIntegerFieldUpdater<CircuitBreaker> STATE_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(CircuitBreaker.class, "state");
    
    private static final AtomicLongFieldUpdater<CircuitBreaker> FAILURE_TIME_UPDATER =
        AtomicLongFieldUpdater.newUpdater(CircuitBreaker.class, "lastFailureTime");
    
    private static final AtomicIntegerFieldUpdater<CircuitBreaker> FAILURES_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(CircuitBreaker.class, "consecutiveFailures");
    
    public boolean allowRequest() {
        int currentState = STATE_UPDATER.get(this);
        
        if (currentState == CLOSED) {
            return true;
        }
        
        if (currentState == OPEN) {
            long timeSinceFailure = System.currentTimeMillis() - lastFailureTime;
            if (timeSinceFailure > resetTimeoutMs) {
                // Try to transition to HALF_OPEN
                if (STATE_UPDATER.compareAndSet(this, OPEN, HALF_OPEN)) {
                    return true;
                }
            }
            return false;
        }
        
        return true; // HALF_OPEN
    }
    
    public void recordFailure() {
        FAILURE_TIME_UPDATER.set(this, System.currentTimeMillis());
        int failures = FAILURES_UPDATER.incrementAndGet(this);
        
        if (failures >= failureThreshold) {
            STATE_UPDATER.set(this, OPEN);
        }
    }
}
```

---

## Deep Dive: PooledObject Explained

Let me break down the `PooledObject` class in extreme detail.

### The Basic Structure

```java
private static class PooledObject<T> {
    final T object;                    // The actual pooled object
    volatile boolean available;        // Is it available for use?
    volatile PooledObject<T> next;    // Link to next in free list
    
    PooledObject(T object) {
        this.object = object;
        this.available = true;
        this.next = null;
    }
}
```

### Why Each Field Exists

#### 1. `final T object`
```java
final T object;
```

**Purpose:** Holds the actual pooled resource (database connection, byte buffer, etc.)

**Why final?**
- Once created, the wrapper never changes what it wraps
- Immutable reference = thread-safe
- JVM can optimize final field access

**Example:**
```java
PooledObject<DatabaseConnection> wrapper = 
    new PooledObject<>(new DatabaseConnection("conn-1"));

// wrapper.object always points to same connection
// Cannot do: wrapper.object = someOtherConnection; (compilation error)
```

#### 2. `volatile boolean available`
```java
volatile boolean available;
```

**Purpose:** Tracks if object is currently in use

**Why volatile?**
Without volatile:
```
Thread 1: Sets available = false
Thread 2: Might still see available = true (cached value)
Result: Both threads use same object! 💥
```

With volatile:
```
Thread 1: Sets available = false
          ↓
       [Main Memory] ← volatile ensures write
          ↓
Thread 2: Reads available from main memory
          Sees: false (correct!)
```

**Critical:** This is why AtomicFieldUpdater requires volatile!

#### 3. `volatile PooledObject<T> next`
```java
volatile PooledObject<T> next;
```

**Purpose:** Forms a linked list of available objects (free list)

**Why volatile?**
Same visibility guarantees for the linked structure.

**Visual:**
```
freeList → [A] → [B] → [C] → null
           next   next   next

When A is acquired:
freeList → [B] → [C] → null
```

### The Field Updaters

```java
// For updating 'available' field atomically
private static final AtomicIntegerFieldUpdater<PooledObject> AVAILABLE_UPDATER =
    AtomicIntegerFieldUpdater.newUpdater(PooledObject.class, "available");

// For updating 'next' field atomically
private static final AtomicReferenceFieldUpdater<PooledObject, PooledObject> NEXT_UPDATER =
    AtomicReferenceFieldUpdater.newUpdater(
        PooledObject.class,    // Class containing the field
        PooledObject.class,    // Type of the field
        "next"                 // Field name
    );
```

### Why Use Field Updaters Instead of AtomicXxx?

#### Option 1: Using AtomicBoolean (Naive)
```java
class PooledObject<T> {
    final T object;
    final AtomicBoolean available = new AtomicBoolean(true);
    final AtomicReference<PooledObject<T>> next = new AtomicReference<>();
}
```

**Memory per instance:**
- `object` reference: 8 bytes
- `AtomicBoolean` object: 16 bytes (object header + value)
- `AtomicReference` object: 16 bytes
- **Total: 40 bytes per instance**

For 10,000 pooled objects: **400 KB**

#### Option 2: Using volatile + FieldUpdater (Optimized)
```java
class PooledObject<T> {
    final T object;
    volatile boolean available = true;
    volatile PooledObject<T> next;
}
```

**Memory per instance:**
- `object` reference: 8 bytes
- `available` boolean: 1 byte
- `next` reference: 8 bytes
- Padding: ~7 bytes (alignment)
- **Total: 24 bytes per instance**

For 10,000 pooled objects: **240 KB**

**Savings: 160 KB (40% reduction!)**

### How It Works in Practice

#### Acquiring an Object

```java
public T acquire() {
    while (true) {
        // 1. Read current head of free list
        PooledObject<T> head = freeList.get();
        
        if (head == null) {
            return createNew(); // No objects available
        }
        
        // 2. Get next object in list
        PooledObject<T> next = head.next;
        
        // 3. Try to atomically update freeList to point to next
        if (freeList.compareAndSet(head, next)) {
            // Success! We removed head from free list
            
            // 4. Mark object as unavailable using field updater
            AVAILABLE_UPDATER.set(head, 0); // 0 = false
            
            return head.object;
        }
        
        // CAS failed - another thread took this object
        // Loop retries with new head
    }
}
```

**Visual Flow:**
```
Initial state:
freeList → [A:available=true] → [B:available=true] → null

Thread 1 acquires:
1. Reads head = A
2. Reads next = B
3. CAS: freeList from A to B ✓
4. Sets A.available = false

Result:
freeList → [B:available=true] → null
Thread 1 has: [A:available=false]
```

#### Releasing an Object

```java
public void release(T object) {
    PooledObject<T> pooledObj = new PooledObject<>(object);
    
    while (true) {
        // 1. Read current head
        PooledObject<T> head = freeList.get();
        
        // 2. Link new node to current head
        pooledObj.next = head;
        
        // 3. Try to atomically make pooledObj the new head
        if (freeList.compareAndSet(head, pooledObj)) {
            // Success!
            
            // 4. Mark as available
            AVAILABLE_UPDATER.set(pooledObj, 1); // 1 = true
            
            break;
        }
        
        // CAS failed - retry with new head
    }
}
```

**Visual Flow:**
```
Initial state:
freeList → [B:available=true] → null
Thread 1 returns: A

1. Create wrapper for A
2. Read head = B
3. Set A.next = B
4. CAS: freeList from B to A ✓
5. Set A.available = true

Result:
freeList → [A:available=true] → [B:available=true] → null
```

### Concurrent Scenarios

#### Scenario 1: Two Threads Acquiring Simultaneously

```
Initial:
freeList → [A] → [B] → [C] → null

Thread 1:                    Thread 2:
1. Read head = A            1. Read head = A
2. Read next = B            2. Read next = B
3. CAS(A, B) ✓              3. CAS(A, B) ✗ (head already B)
4. Return A                    Loop back to step 1
                            4. Read head = B (new value)
                            5. Read next = C
                            6. CAS(B, C) ✓
                            7. Return B

Result: Thread 1 got A, Thread 2 got B (no collision!)
```

#### Scenario 2: Acquire and Release Simultaneously

```
Initial:
freeList → [B] → null
Thread 1 returning: A

Thread 1 (release):         Thread 2 (acquire):
1. Wrap A                   1. Read head = B
2. Read head = B            2. Read next = null
3. Set A.next = B           3. CAS(B, null) ✓
4. CAS(B, A) ✓              4. Return B
5. Done

Result:
freeList → [A] → [B] → null
Wait, B is in use! Fix: B.next should be null

Actually:
When Thread 2 acquires B, it does:
  freeList.compareAndSet(B, B.next)
This sets freeList = null (not B itself)

Corrected result:
freeList → [A] → null
Thread 2 has: B
```

### Why Static Field Updaters?

```java
// ✓ CORRECT: One updater shared by all instances
private static final AtomicIntegerFieldUpdater<PooledObject> AVAILABLE_UPDATER =
    AtomicIntegerFieldUpdater.newUpdater(PooledObject.class, "available");

// ✗ WRONG: Creates updater for each instance (wastes memory!)
private final AtomicIntegerFieldUpdater<PooledObject> availableUpdater =
    AtomicIntegerFieldUpdater.newUpdater(PooledObject.class, "available");
```

**Why?**
- Field updater contains metadata about the field (offset in memory, type info)
- This metadata is same for all instances
- Static = one updater in memory for the entire class
- Instance-level = one updater per object (defeats the memory savings!)

### Common Mistakes

#### Mistake 1: Forgetting volatile
```java
// ✗ WRONG
class PooledObject<T> {
    boolean available; // NOT volatile
}

// AtomicIntegerFieldUpdater.newUpdater() will throw:
// IllegalArgumentException: Must be volatile type
```

#### Mistake 2: Using private with external updater
```java
// ✗ WRONG
class PooledObject<T> {
    private volatile boolean available;
}

// In different class:
AtomicIntegerFieldUpdater.newUpdater(PooledObject.class, "available");
// Throws: Can't access private field
```

#### Mistake 3: Trying to update final fields
```java
// ✗ WRONG
class PooledObject<T> {
    final volatile boolean available = true; // final and volatile is contradictory
}
```

---

## Summary and Best Practices

### Quick Reference Table

| Class | Use Case | Contention | Memory | Performance |
|-------|----------|------------|--------|-------------|
| **AtomicInteger/Long** | General counters | Low-Med | Low | Fast reads |
| **LongAdder** | High-frequency counters | High | Medium | Very fast writes |
| **AtomicReference** | Object updates | Any | Low | Fast |
| **AtomicFieldUpdater** | Many instances | Any | Minimal | Same as Atomic |
| **Synchronized** | Multiple variables | Any | None | Slowest |

### Decision Tree

```
Need atomicity?
├─ Single variable?
│  ├─ Just counting/adding?
│  │  ├─ High contention? → LongAdder
│  │  └─ Low contention? → AtomicLong
│  ├─ Object reference? → AtomicReference
│  └─ Many instances? → AtomicFieldUpdater
└─ Multiple variables? → synchronized/Lock
```

### Best Practices

#### 1. Prefer Immutability
```java
// ✓ Good: Immutable config
class Config {
    private final AtomicReference<ImmutableConfig> config;
    
    public void update(ImmutableConfig newConfig) {
        config.set(newConfig);
    }
}

// ✗ Bad: Mutable state
class Config {
    private volatile Map<String, String> settings; // Can be modified
}
```

#### 2. Use Correct Atomic Type
```java
// ✓ Good: Right tool for the job
LongAdder requestCounter = new LongAdder();  // High-frequency writes
AtomicLong userId = new AtomicLong();        // Coordinating state

// ✗ Bad: Wrong tool
AtomicLong requestCounter = new AtomicLong(); // Slow under high contention
LongAdder userId = new LongAdder();          // Overkill for state coordination
```

#### 3. Minimize CAS Retry Loops
```java
// ✓ Good: Minimal work in CAS loop
do {
    oldValue = atomic.get();
    newValue = oldValue + 1; // Simple operation
} while (!atomic.compareAndSet(oldValue, newValue));

// ✗ Bad: Heavy computation in loop
do {
    oldValue = atomic.get();
    newValue = expensiveComputation(oldValue); // Computed repeatedly!
} while (!atomic.compareAndSet(oldValue, newValue));

// ✓ Better: Compute outside if possible
computed = expensiveComputation(atomic.get());
atomic.set(computed);
```

#### 4. Don't Mix Atomic with Synchronized
```java
// ✗ Bad: Unnecessary complexity
class Counter {
    private AtomicLong count = new AtomicLong();
    
    public synchronized void increment() { // Unnecessary!
        count.incrementAndGet();
    }
}

// ✓ Good: Use one or the other
class Counter {
    private AtomicLong count = new AtomicLong();
    
    public void increment() {
        count.incrementAndGet(); // Already thread-safe
    }
}
```

#### 5. Understand Memory Visibility
```java
// ✗ Bad: Assuming order
AtomicBoolean ready = new AtomicBoolean(false);
String message;

// Thread 1
message = "Hello";
ready.set(true);

// Thread 2
if (ready.get()) {
    System.out.println(message); // Might be null!
}

// ✓ Good: Use volatile or AtomicReference for both
AtomicReference<String> message = new AtomicReference<>();
AtomicBoolean ready = new AtomicBoolean(false);

// Thread 1
message.set("Hello");
ready.set(true);

// Thread 2
if (ready.get()) {
    System.out.println(message.get()); // Guaranteed visible
}
```

---

## Practice Exercises

### Exercise 1: Rate Limiter
Build a token bucket rate limiter using AtomicLong:
```java
class RateLimiter {
    private final AtomicLong tokens;
    private final long maxTokens;
    private final long refillRate; // tokens per second
    
    // Implement:
    // - tryAcquire(): Attempt to consume token
    // - refill(): Add tokens based on time elapsed
}
```

### Exercise 2: Lock-Free Queue
Implement Michael-Scott queue using AtomicReference:
```java
class LockFreeQueue<T> {
    private static class Node<T> {
        final T value;
        final AtomicReference<Node<T>> next;
    }
    
    private final AtomicReference<Node<T>> head;
    private final AtomicReference<Node<T>> tail;
    
    // Implement:
    // - enqueue(T value): Add to tail
    // - dequeue(): Remove from head
}
```

### Exercise 3: Metrics Aggregator
Build a metrics system using LongAdder:
```java
class MetricsAggregator {
    // Track: requests/sec, errors/sec, latency percentiles
    // Support: increment, record, getSnapshot, reset
}
```

### Exercise 4: Connection Pool
Implement a database connection pool:
```java
class ConnectionPool {
    // Use AtomicFieldUpdater for memory efficiency
    // Support: acquire, release, stats, dynamic sizing
}
```

### Exercise 5: Circuit Breaker
Build a circuit breaker with three states:
```java
class CircuitBreaker {
    // States: CLOSED, OPEN, HALF_OPEN
    // Use AtomicFieldUpdater for state transitions
    // Implement: execute, recordSuccess, recordFailure
}
```

---

## Additional Resources

### Books
- **Java Concurrency in Practice** by Brian Goetz (Chapters 15-16)
- **The Art of Multiprocessor Programming** by Herlihy & Shavit

### Papers
- "Simple, Fast, and Practical Non-Blocking Queues" (Michael & Scott)
- "Lock-Free Data Structures" (Sundell & Tsigas)

### Online Resources
- Doug Lea's JSR-166 documentation
- OpenJDK java.util.concurrent source code
- JCStress (Java Concurrency Stress Tests)

### Next Steps
- **Phase 9: Fork/Join Framework** - Work-stealing for parallel algorithms
- **Phase 10: CompletableFuture** - Asynchronous programming
- **Phase 11: Thread Safety Patterns** - Design patterns for concurrent systems

---

## Key Takeaways

1. **Atomic classes** provide lock-free thread-safety for single variables
2. **CAS** is the foundation - understand the retry loop pattern
3. **LongAdder** dramatically outperforms AtomicLong under high contention
4. **AtomicReference** enables lock-free object updates with immutability
5. **AtomicFieldUpdater** saves memory when you have many instances
6. **Know when to use what** - wrong tool = poor performance
7. **Immutability + Atomics** = powerful combination for thread-safety

**Remember:** Lock-free doesn't mean complexity-free. Use these tools when performance matters, but don't prematurely optimize. Sometimes a simple synchronized block is the right answer!