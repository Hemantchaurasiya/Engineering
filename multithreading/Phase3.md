# Phase 3: Memory Model & Visibility - Complete Guide

## Table of Contents
1. [Introduction](#introduction)
2. [3.1 Java Memory Model (JMM)](#31-java-memory-model-jmm)
3. [3.2 Volatile Keyword Deep Dive](#32-volatile-keyword-deep-dive)
4. [3.3 Safe Publication & Final Fields](#33-safe-publication--final-fields)
5. [3.4 Common Memory Visibility Problems](#34-common-memory-visibility-problems)
6. [Comprehensive Practice Exercise](#comprehensive-practice-exercise)
7. [Summary & Key Takeaways](#summary--key-takeaways)

---

## Introduction

Phase 3 focuses on one of the most critical and challenging aspects of Java concurrency: **memory visibility and the Java Memory Model (JMM)**. Understanding these concepts is essential because:

- **Hidden Bugs**: Memory visibility issues create subtle, non-deterministic bugs that are extremely hard to reproduce and debug
- **Performance**: Proper use of memory visibility guarantees can help you write lock-free, high-performance code
- **Correctness**: Without understanding the JMM, your concurrent code may appear to work but fail randomly under load

This guide uses **real-world examples** and **production-ready code** to demonstrate these concepts.

---

## 3.1 Java Memory Model (JMM)

### Understanding the Problem

Modern CPUs have multiple levels of cache (L1, L2, L3) for performance. Each thread may cache variables locally, and without proper synchronization, these caches aren't automatically synchronized. This leads to **visibility problems** where changes made by one thread are never seen by other threads.

### Real-World Scenario: Configuration Hot-Reload System

Imagine you're building a web application where configuration can be updated without restart. Multiple request-handling threads need to see the latest configuration.

#### ❌ Broken Implementation

```java
/**
 * BROKEN CODE - Demonstrates Java Memory Model visibility issues
 * 
 * Problem: Changes made by ConfigLoader thread may NEVER be visible
 * to RequestHandler threads due to CPU cache coherence issues.
 */
public class BrokenConfigurationSystem {
    
    static class AppConfig {
        private int maxConnections = 100;  // NOT volatile - PROBLEM!
        private String apiEndpoint = "http://api.example.com";
        
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { 
            this.maxConnections = maxConnections; 
        }
        
        public String getApiEndpoint() { return apiEndpoint; }
        public void setApiEndpoint(String apiEndpoint) { 
            this.apiEndpoint = apiEndpoint; 
        }
    }
    
    private static AppConfig config = new AppConfig();
    
    static class ConfigLoader extends Thread {
        @Override
        public void run() {
            try {
                Thread.sleep(2000);
                System.out.println("[ConfigLoader] Loading new configuration...");
                
                // Update configuration - but other threads may NEVER see this!
                config.setMaxConnections(500);
                config.setApiEndpoint("http://api-v2.example.com");
                
                System.out.println("[ConfigLoader] Configuration updated!");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    static class RequestHandler extends Thread {
        private final String name;
        
        public RequestHandler(String name) {
            this.name = name;
        }
        
        @Override
        public void run() {
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(1000);
                    
                    // These threads might NEVER see the updated values!
                    int connections = config.getMaxConnections();
                    String endpoint = config.getApiEndpoint();
                    
                    System.out.println("[" + name + "] Using config - " +
                                     "maxConnections: " + connections + 
                                     ", endpoint: " + endpoint);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
```

**Why This Fails:**
- ConfigLoader writes to 'config' fields in its CPU cache
- RequestHandler threads read from their own CPU caches
- There's no synchronization forcing cache coherence
- Result: Handlers may forever read stale cached values

#### ✅ Correct Implementation

```java
import java.util.concurrent.atomic.AtomicReference;

/**
 * PRODUCTION-READY Configuration System
 * Demonstrates proper memory visibility using AtomicReference
 */
public class ThreadSafeConfigurationSystem {
    
    /**
     * Immutable configuration object
     * Thread-safe by design
     */
    static class ImmutableConfig {
        private final int maxConnections;
        private final String apiEndpoint;
        private final int timeout;
        private final boolean sslEnabled;
        
        public ImmutableConfig(int maxConnections, String apiEndpoint, 
                              int timeout, boolean sslEnabled) {
            this.maxConnections = maxConnections;
            this.apiEndpoint = apiEndpoint;
            this.timeout = timeout;
            this.sslEnabled = sslEnabled;
        }
        
        public int getMaxConnections() { return maxConnections; }
        public String getApiEndpoint() { return apiEndpoint; }
        public int getTimeout() { return timeout; }
        public boolean isSslEnabled() { return sslEnabled; }
        
        @Override
        public String toString() {
            return String.format("Config[connections=%d, endpoint=%s, timeout=%d, ssl=%s]",
                               maxConnections, apiEndpoint, timeout, sslEnabled);
        }
    }
    
    /**
     * Production-ready configuration holder
     * Uses AtomicReference for thread-safe config replacement
     */
    static class ConfigurationManager {
        // AtomicReference provides lock-free atomic updates
        private final AtomicReference<ImmutableConfig> currentConfig;
        
        public ConfigurationManager(ImmutableConfig initialConfig) {
            this.currentConfig = new AtomicReference<>(initialConfig);
        }
        
        /**
         * Atomically update entire configuration
         * All readers will see either old or new config, never partial updates
         */
        public void updateConfig(ImmutableConfig newConfig) {
            ImmutableConfig oldConfig = currentConfig.getAndSet(newConfig);
            System.out.println("[ConfigManager] Updated from: " + oldConfig);
            System.out.println("[ConfigManager] Updated to: " + newConfig);
        }
        
        /**
         * Get current configuration
         * Guaranteed to be consistent (no partial reads)
         */
        public ImmutableConfig getConfig() {
            return currentConfig.get();
        }
    }
    
    static class ConfigLoader extends Thread {
        private final ConfigurationManager configManager;
        
        public ConfigLoader(ConfigurationManager configManager) {
            this.configManager = configManager;
        }
        
        @Override
        public void run() {
            try {
                Thread.sleep(2000);
                
                ImmutableConfig newConfig = new ImmutableConfig(
                    500, "http://api-v2.example.com", 5000, true
                );
                
                configManager.updateConfig(newConfig);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    static class RequestHandler extends Thread {
        private final String name;
        private final ConfigurationManager configManager;
        
        public RequestHandler(String name, ConfigurationManager configManager) {
            super(name);
            this.name = name;
            this.configManager = configManager;
        }
        
        @Override
        public void run() {
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(1000);
                    
                    ImmutableConfig config = configManager.getConfig();
                    
                    System.out.printf("[%s] Request #%d using: connections=%d, " +
                                    "endpoint=%s%n",
                                    name, i + 1, 
                                    config.getMaxConnections(),
                                    config.getApiEndpoint());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
```

### Key Concepts: Java Memory Model

#### 1. Happens-Before Relationship

The JMM defines when actions in one thread are guaranteed to be visible to another thread through **happens-before** rules:

- **Program Order Rule**: Each action in a thread happens-before every subsequent action in that thread
- **Volatile Variable Rule**: A write to a volatile field happens-before every subsequent read of that field
- **Thread Start Rule**: A call to `Thread.start()` happens-before any action in the started thread
- **Thread Termination Rule**: Any action in a thread happens-before any other thread detects that thread has terminated
- **Synchronization Rule**: An unlock on a monitor happens-before every subsequent lock on that monitor

#### 2. Memory Visibility Problems

Without proper synchronization:
- Threads may cache variables in CPU registers or cache
- Changes made by one thread may never be visible to others
- Compilers and CPUs may reorder instructions for optimization
- No guarantee that writes propagate from CPU caches to main memory

#### 3. Solution Patterns

**Best Practices:**
1. **Immutability**: Make configuration objects immutable with final fields
2. **Atomic Reference**: Use `AtomicReference` for atomic config swaps
3. **Lock-free**: Achieve thread safety without locks for better performance

**When to Use:**
- Configuration management
- Cache updates
- Publishing complex objects
- Any scenario needing atomic state replacement

---

## 3.2 Volatile Keyword Deep Dive

### What Volatile Guarantees

The `volatile` keyword provides:
1. **Visibility**: Writes are immediately visible to all threads
2. **Ordering**: Prevents instruction reordering around volatile access
3. **Atomicity**: Individual reads and writes are atomic (but NOT compound operations)

### Real-World Example: Shutdown Flag Pattern

One of the most common uses of `volatile` is for control flags like shutdown signals.

#### ❌ Broken: Without Volatile

```java
static class BrokenBackgroundWorker extends Thread {
    private boolean running = true;  // NOT volatile - DANGEROUS!
    
    public void shutdown() {
        System.out.println("[Main] Sending shutdown signal...");
        running = false;  // May never be visible to worker thread!
    }
    
    @Override
    public void run() {
        System.out.println("[Worker] Started");
        int workCount = 0;
        
        // This loop might run FOREVER because 'running' is cached
        while (running) {
            workCount++;
            
            if (workCount % 1000000 == 0) {
                System.out.println("[Worker] Processed " + workCount + " items");
            }
        }
        
        System.out.println("[Worker] Stopped after " + workCount + " items");
    }
}
```

#### ✅ Correct: With Volatile

```java
static class CorrectBackgroundWorker extends Thread {
    private volatile boolean running = true;  // volatile ensures visibility
    
    public void shutdown() {
        System.out.println("[Main] Sending shutdown signal...");
        running = false;  // Immediately visible to worker!
    }
    
    @Override
    public void run() {
        System.out.println("[Worker] Started");
        int workCount = 0;
        
        while (running) {  // Always reads fresh value
            workCount++;
            
            if (workCount % 1000000 == 0) {
                System.out.println("[Worker] Processed " + workCount + " items");
            }
        }
        
        System.out.println("[Worker] Stopped gracefully after " + workCount + " items");
    }
}
```

### Production Pattern: Multiple Control Flags

```java
static class ProductionBackgroundService extends Thread {
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final String serviceName;
    
    public ProductionBackgroundService(String serviceName) {
        super(serviceName);
        this.serviceName = serviceName;
    }
    
    public void shutdown() {
        System.out.println("[" + serviceName + "] Shutdown requested");
        running = false;
    }
    
    public void pause() {
        System.out.println("[" + serviceName + "] Pause requested");
        paused = true;
    }
    
    public void resume() {
        System.out.println("[" + serviceName + "] Resume requested");
        paused = false;
    }
    
    @Override
    public void run() {
        System.out.println("[" + serviceName + "] Service started");
        int tasksProcessed = 0;
        
        while (running) {
            // Check pause flag
            while (paused && running) {
                try {
                    System.out.println("[" + serviceName + "] Paused...");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                    break;
                }
            }
            
            if (!running) break;
            
            try {
                processTask(tasksProcessed++);
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        cleanup(tasksProcessed);
    }
    
    private void processTask(int taskId) {
        System.out.println("[" + serviceName + "] Processing task #" + taskId);
    }
    
    private void cleanup(int tasksProcessed) {
        System.out.println("[" + serviceName + "] Shutting down...");
        System.out.println("[" + serviceName + "] Total tasks processed: " + tasksProcessed);
    }
}
```

### Volatile Limitations

```java
static class VolatileLimitations {
    private volatile int readCount = 0;
    private volatile int writeCount = 0;
    
    // ❌ DANGER: Not atomic - race condition!
    public void incrementRead() {
        readCount++;  // Read-modify-write is NOT atomic!
    }
    
    // ❌ DANGER: Not atomic - inconsistent reads possible
    public int getTotal() {
        int reads = readCount;
        int writes = writeCount;
        return reads + writes;  // Another thread might update between reads
    }
    
    // ✅ FIX: Use synchronization for compound operations
    public synchronized void incrementReadSafely() {
        readCount++;
    }
    
    public synchronized int getTotalSafely() {
        return readCount + writeCount;
    }
}
```

### Volatile Decision Matrix

| Use Case | Volatile? | Alternative |
|----------|-----------|-------------|
| Status flags (shutdown, running) | ✅ Yes | - |
| One writer, multiple readers | ✅ Yes | - |
| Publishing immutable objects | ✅ Yes | AtomicReference |
| Double-checked locking | ✅ Yes | - |
| Counters with increment | ❌ No | AtomicInteger |
| Complex state consistency | ❌ No | synchronized blocks |
| Check-then-act patterns | ❌ No | synchronized blocks |
| Multiple related variables | ❌ No | synchronized blocks |

### Key Rules for Volatile

**✅ When to Use Volatile:**
- Status flags (running, shutdown, initialized)
- One writer, multiple readers scenarios
- Publishing immutable objects
- Double-checked locking (with proper pattern)

**❌ When NOT to Use Volatile:**
- Counters that need increment operations (use `AtomicInteger`)
- Complex state that needs consistency (use locks)
- Check-then-act patterns (use synchronization)
- Multiple related variables that must be updated together

---

## 3.3 Safe Publication & Final Fields

### The Problem: Unsafe Publication

Objects can become visible to other threads before they're fully constructed. This is extremely dangerous and leads to subtle bugs.

#### ❌ Unsafe Publication Example

```java
static class UnsafeUserSession {
    private String userId;
    private String sessionToken;
    private long loginTime;
    private Map<String, String> attributes;
    
    // DANGER: 'this' reference escapes during construction!
    public UnsafeUserSession(String userId, String sessionToken, 
                            SessionRegistry registry) {
        this.userId = userId;
        
        // PROBLEM: Publishing 'this' before construction completes!
        registry.register(this);  // ← UNSAFE PUBLICATION!
        
        // These might not be visible to threads that got 'this' from registry
        this.sessionToken = sessionToken;
        this.loginTime = System.currentTimeMillis();
        this.attributes = new HashMap<>();
    }
}
```

**Why This Fails:**
- `this` reference escapes before constructor completes
- Other threads might see partially constructed object
- Non-final fields may not be visible to other threads
- Object invariants may be violated

### Safe Publication with Final Fields

#### ✅ Safe Implementation

```java
/**
 * SAFE: Properly constructed immutable session
 * Uses final fields for safe publication guarantees
 */
static class SafeUserSession {
    // final fields are guaranteed to be visible after construction
    private final String userId;
    private final String sessionToken;
    private final long loginTime;
    private final Map<String, String> attributes;
    
    private SafeUserSession(String userId, String sessionToken, 
                           long loginTime, Map<String, String> attributes) {
        this.userId = userId;
        this.sessionToken = sessionToken;
        this.loginTime = loginTime;
        // Defensive copy + immutable wrapper
        this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
    }
    
    /**
     * Factory method ensures safe publication
     * Object is fully constructed before being returned
     */
    public static SafeUserSession create(String userId, String sessionToken,
                                        Map<String, String> attributes) {
        long loginTime = System.currentTimeMillis();
        
        // Object constructed completely before returning
        SafeUserSession session = new SafeUserSession(
            userId, sessionToken, loginTime, attributes
        );
        
        return session;  // Now safe to publish
    }
    
    public String getUserId() { return userId; }
    public String getSessionToken() { return sessionToken; }
    public long getLoginTime() { return loginTime; }
    public Map<String, String> getAttributes() { return attributes; }
}
```

### Safe Publication Techniques

#### 1. Using ConcurrentHashMap

```java
static class SessionRegistry {
    // ConcurrentHashMap provides safe publication
    private final ConcurrentHashMap<String, SafeUserSession> sessions = 
        new ConcurrentHashMap<>();
    
    public void register(SafeUserSession session) {
        sessions.put(session.getUserId(), session);
    }
    
    public SafeUserSession getSession(String userId) {
        return sessions.get(userId);
    }
}
```

#### 2. Using Volatile Reference

```java
static class VolatilePublication {
    private volatile SafeUserSession currentSession;
    
    public void publishViaVolatile(SafeUserSession session) {
        // volatile write establishes happens-before
        this.currentSession = session;
    }
    
    public SafeUserSession getViaVolatile() {
        return currentSession;
    }
}
```

#### 3. Using Synchronization

```java
static class SynchronizedPublication {
    private SafeUserSession syncedSession;
    
    public synchronized void publishViaSynchronized(SafeUserSession session) {
        this.syncedSession = session;
    }
    
    public synchronized SafeUserSession getViaSynchronized() {
        return syncedSession;
    }
}
```

#### 4. Static Initializer

```java
static class StaticPublication {
    // Static initialization is thread-safe by JVM guarantee
    private static final SafeUserSession DEFAULT_SESSION;
    
    static {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("type", "default");
        DEFAULT_SESSION = SafeUserSession.create("system", "default-token", attrs);
    }
    
    public static SafeUserSession getDefaultSession() {
        return DEFAULT_SESSION;
    }
}
```

### Safe Publication Rules

**Safe Publication Idioms:**
1. ✅ Store in volatile field
2. ✅ Store in AtomicReference
3. ✅ Store in final field
4. ✅ Store in properly locked field
5. ✅ Store in concurrent collection (ConcurrentHashMap, etc.)
6. ✅ Initialize in static initializer

**Construction Safety Rules:**
1. ❌ Don't let 'this' escape during construction
2. ❌ Don't start threads in constructors
3. ❌ Don't register callbacks in constructors
4. ✅ Use factory methods for complex initialization
5. ✅ Make fields final when possible

---

## 3.4 Common Memory Visibility Problems

### Bug #1: Broken Double-Checked Locking

#### ❌ The Bug

```java
static class BrokenLazyInitialization {
    private static ExpensiveResource instance;  // NOT volatile - BROKEN!
    
    public static ExpensiveResource getInstance() {
        if (instance == null) {  // First check (no locking)
            synchronized (BrokenLazyInitialization.class) {
                if (instance == null) {
                    instance = new ExpensiveResource();
                    // PROBLEM: Other threads might see partially constructed object!
                }
            }
        }
        return instance;
    }
}
```

#### ✅ The Fix

```java
static class CorrectLazyInitialization {
    private static volatile ExpensiveResource instance;  // volatile!
    
    public static ExpensiveResource getInstance() {
        if (instance == null) {
            synchronized (CorrectLazyInitialization.class) {
                if (instance == null) {
                    instance = new ExpensiveResource();
                }
            }
        }
        return instance;
    }
}
```

#### ✅ Best Practice: Initialization-on-Demand Holder

```java
static class BestLazyInitialization {
    private BestLazyInitialization() {}
    
    // Static inner class - loaded only when accessed
    private static class Holder {
        static final ExpensiveResource INSTANCE = new ExpensiveResource();
    }
    
    public static ExpensiveResource getInstance() {
        return Holder.INSTANCE;  // Thread-safe, lazy, no locks!
    }
}
```

### Bug #2: Volatile Counter Race

#### ❌ The Bug

```java
static class BrokenCounter {
    private volatile int count = 0;  // volatile alone isn't enough!
    
    public void increment() {
        count++;  // NOT atomic! Race condition!
    }
    
    public int getCount() {
        return count;
    }
}
```

**Why It Fails:**
- `count++` is three operations: read, add, write
- Even though `count` is volatile, compound operation is NOT atomic
- Multiple threads can read same value, increment, write back

#### ✅ The Fix

```java
static class CorrectCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet();  // Atomic operation
    }
    
    public int getCount() {
        return count.get();
    }
}
```

### Bug #3: Check-Then-Act Race

#### ❌ The Bug

```java
static class BrokenCache {
    private volatile String cachedValue = null;
    private volatile long lastUpdateTime = 0;
    
    public String getValue() {
        // PROBLEM: Check and act are not atomic
        if (cachedValue == null || isCacheExpired()) {
            // Multiple threads can pass this check!
            cachedValue = expensiveOperation();
            lastUpdateTime = System.currentTimeMillis();
        }
        return cachedValue;
    }
    
    private boolean isCacheExpired() {
        return System.currentTimeMillis() - lastUpdateTime > 5000;
    }
}
```

#### ✅ The Fix

```java
static class CorrectCache {
    private String cachedValue = null;
    private long lastUpdateTime = 0;
    private final Lock lock = new ReentrantLock();
    
    public String getValue() {
        // First check without lock (optimization)
        if (cachedValue != null && !isCacheExpired()) {
            return cachedValue;
        }
        
        lock.lock();
        try {
            // Double-check after acquiring lock
            if (cachedValue == null || isCacheExpired()) {
                cachedValue = expensiveOperation();
                lastUpdateTime = System.currentTimeMillis();
            }
            return cachedValue;
        } finally {
            lock.unlock();
        }
    }
}
```

### Bug #4: Constructor Publication

#### ❌ The Bug

```java
static class BrokenEventListener {
    private final EventSource source;
    
    // DANGER: 'this' escapes before construction completes
    public BrokenEventListener(EventSource source) {
        this.source = source;
        source.registerListener(this);  // UNSAFE!
    }
}
```

#### ✅ The Fix

```java
static class CorrectEventListener {
    private final EventSource source;
    
    private CorrectEventListener(EventSource source) {
        this.source = source;
    }
    
    // Factory method ensures safe construction
    public static CorrectEventListener create(EventSource source) {
        CorrectEventListener listener = new CorrectEventListener(source);
        source.registerListener(listener);  // Safe now
        return listener;
    }
}
```

### Common Bug Patterns Summary

| Bug Pattern | Problem | Solution |
|-------------|---------|----------|
| Double-checked locking | Partially constructed objects | Add volatile |
| Volatile counter | Non-atomic increment | Use AtomicInteger |
| Check-then-act | Race between check and action | Synchronize both |
| Constructor publication | 'this' escapes early | Use factory method |
| Stale data reads | Missing happens-before | Add synchronization |

---

## Comprehensive Practice Exercise

### Real-Time Metrics Collection System

This exercise combines all Phase 3 concepts into a production-ready system.

```java
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Build a Real-Time Metrics Collection System
 * 
 * Requirements:
 * 1. Multiple threads report metrics (requests, errors, latency)
 * 2. A monitoring thread reads metrics every second
 * 3. Configuration can be updated hot (without restart)
 * 4. All operations must be thread-safe
 * 5. No data races or visibility issues
 */
public class RealTimeMetricsSystem {
    
    /**
     * Immutable configuration - uses final fields
     */
    static class MetricsConfig {
        private final int samplingIntervalMs;
        private final int maxLatencyMs;
        private final boolean detailedLogging;
        private final String applicationName;
        
        public MetricsConfig(int samplingIntervalMs, int maxLatencyMs, 
                           boolean detailedLogging, String applicationName) {
            this.samplingIntervalMs = samplingIntervalMs;
            this.maxLatencyMs = maxLatencyMs;
            this.detailedLogging = detailedLogging;
            this.applicationName = applicationName;
        }
        
        public int getSamplingIntervalMs() { return samplingIntervalMs; }
        public int getMaxLatencyMs() { return maxLatencyMs; }
        public boolean isDetailedLogging() { return detailedLogging; }
        public String getApplicationName() { return applicationName; }
    }
    
    /**
     * Thread-safe metrics collector
     */
    static class MetricsCollector {
        // AtomicLong for thread-safe counters
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successfulRequests = new AtomicLong(0);
        private final AtomicLong failedRequests = new AtomicLong(0);
        private final AtomicLong totalLatencyMs = new AtomicLong(0);
        
        // AtomicReference for safe configuration updates
        private final AtomicReference<MetricsConfig> config;
        
        // Volatile for shutdown flag
        private volatile boolean active = true;
        
        public MetricsCollector(MetricsConfig initialConfig) {
            this.config = new AtomicReference<>(initialConfig);
        }
        
        public void recordSuccess(long latencyMs) {
            totalRequests.incrementAndGet();
            successfulRequests.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
            
            MetricsConfig currentConfig = config.get();
            if (currentConfig.isDetailedLogging() && 
                latencyMs > currentConfig.getMaxLatencyMs()) {
                System.out.println("[WARN] High latency: " + latencyMs + "ms");
            }
        }
        
        public void recordFailure(long latencyMs) {
            totalRequests.incrementAndGet();
            failedRequests.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
        }
        
        public MetricsSnapshot getSnapshot() {
            return new MetricsSnapshot(
                totalRequests.get(),
                successfulRequests.get(),
                failedRequests.get(),
                totalLatencyMs.get()
            );
        }
        
        public void updateConfig(MetricsConfig newConfig) {
            MetricsConfig oldConfig = config.getAndSet(newConfig);
            System.out.println("[Config] Updated from: " + oldConfig);
            System.out.println("[Config] Updated to: " + newConfig);
        }
        
        public MetricsConfig getConfig() {
            return config.get();
        }
        
        public void shutdown() {
            active = false;
        }
        
        public boolean isActive() {
            return active;
        }
    }
    
    /**
     * Immutable metrics snapshot
     */
    static class MetricsSnapshot {
        private final long totalRequests;
        private final long successfulRequests;
        private final long failedRequests;
        private final long totalLatencyMs;
        
        public MetricsSnapshot(long totalRequests, long successfulRequests,
                             long failedRequests, long totalLatencyMs) {
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.failedRequests = failedRequests;
            this.totalLatencyMs = totalLatencyMs;
        }
        
        public double getSuccessRate() {
            return totalRequests == 0 ? 0.0 : 
                   (double) successfulRequests / totalRequests * 100;
        }
        
        public double getAverageLatencyMs() {
            return totalRequests == 0 ? 0.0 : 
                   (double) totalLatencyMs / totalRequests;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Metrics[total=%d, success=%d, failed=%d, rate=%.2f%%, avgLatency=%.2fms]",
                totalRequests, successfulRequests, failedRequests, 
                getSuccessRate(), getAverageLatencyMs()
            );
        }
    }
}
```

### Exercise Tasks

1. **Add histogram for latency distribution**
2. **Implement percentile calculations (p50, p95, p99)**
3. **Add metric alerting when thresholds exceeded**
4. **Implement metric persistence to disk**
5. **Add support for custom metrics with tags**
6. **Implement sliding window statistics**
7. **Add metric aggregation across collectors**

### Discussion Questions

1. Why use `AtomicReference` instead of volatile for config?
2. Could we use synchronized instead of `AtomicLong`? Trade-offs?
3. What would break if we removed 'volatile' from shutdown flags?
4. How does immutability help with thread safety here?
5. What happens if multiple threads update config simultaneously?

---

## Summary & Key Takeaways

### 🎯 Core Concepts Mastered

#### 1. Java Memory Model (JMM)
- ✅ Thread-local caches vs main memory
- ✅ Happens-before relationships
- ✅ Why changes might not be visible across threads
- ✅ Memory barriers and fences

#### 2. Volatile Keyword
- ✅ Ensures visibility across threads
- ✅ Prevents instruction reordering
- ✅ Limitations (not atomic for compound operations)
- ✅ When to use vs when to avoid

#### 3. Safe Publication
- ✅ Final fields guarantee safe publication
- ✅ Multiple safe publication idioms
- ✅ Avoiding premature object escape
- ✅ Factory method pattern

#### 4. Common Visibility Bugs
- ✅ Double-checked locking pitfalls
- ✅ Counter races with volatile
- ✅ Check-then-act patterns
- ✅ Constructor publication issues

### 📚 Real-World Examples Built

1. **Configuration Hot-Reload System**
   - AtomicReference + Immutable pattern
   - Lock-free thread-safe updates
   - Production-ready implementation

2. **Background Service with Shutdown**
   - Volatile flags for control
   - Pause/resume functionality
   - Graceful shutdown pattern

3. **Session Registry**
   - Safe publication with concurrent collections
   - Final fields for immutability
   - Factory methods for safe construction

4. **Metrics Collection System**
   - Complete production-ready example
   - AtomicLong for counters
   - Snapshot pattern for consistency

### ✅ Best Practices Learned

1. **Memory Visibility**
   - Use `volatile` for simple flags and status
   - Use `AtomicXxx` for counters and references
   - Use synchronization for compound operations

2. **Safe Publication**
   - Prefer immutability + safe publication
   - Never let `this` escape during construction
   - Use final fields extensively

3. **Code Quality**
   - Document thread-safety guarantees
   - Use factory methods for complex objects
   - Leverage concurrent collections

4. **Performance**
   - Lock-free algorithms where appropriate
   - Minimize synchronization scope
   - Understand cache effects

### 🔍 Common Pitfalls to Avoid

| Pitfall | Why It's Wrong | Correct Approach |
|---------|----------------|------------------|
| `volatile` for `count++` | Not atomic | Use `AtomicInteger` |
| Double-checked locking without volatile | Partial construction visible | Add volatile |
| Publishing `this` in constructor | Object not fully constructed | Use factory method |
| Check-then-act without sync | Race condition | Synchronize both |
| Assuming visibility | No happens-before guarantee | Add synchronization |

### 🛠️ Tools & Techniques

**Detection:**
- Code review for missing synchronization
- Static analysis (FindBugs, SpotBugs)
- Stress testing with high thread counts
- Thread sanitizers and race detectors

**Debugging:**
- Thread dumps analysis
- Using `Thread.yield()` to expose races
- Logging carefully (can mask bugs)
- Reproduce under different loads

### 📖 Further Reading

1. **"Java Concurrency in Practice"** by Brian Goetz
   - Chapter 3: Sharing Objects
   - Chapter 16: The Java Memory Model

2. **Java Language Specification**
   - Chapter 17: Threads and Locks

3. **Doug Lea's Papers**
   - "The Java Memory Model"
   - "Using JDK 5.0 JSR-166 Concurrency"

4. **OpenJDK Source Code**
   - `java.util.concurrent.atomic` package
   - `java.util.concurrent.locks` package

### 🎓 Next Steps

You're now ready for **Phase 4: Java Concurrency Utilities**!

Phase 4 will cover:
- **Executor Framework** - Thread pools and task execution
- **Callable and Future** - Asynchronous results
- **ThreadPoolExecutor** - Advanced configuration and tuning
- **ScheduledExecutorService** - Delayed and periodic tasks

### 💡 Quick Reference Card

```
MEMORY VISIBILITY QUICK GUIDE

When to use what:

volatile:
  ✅ Status flags (running, shutdown)
  ✅ One writer, many readers
  ✅ Publishing immutable objects
  ❌ Counters (use AtomicInteger)
  ❌ Check-then-act (use synchronized)

AtomicXxx:
  ✅ Counters and numeric operations
  ✅ Compare-and-swap operations
  ✅ Lock-free algorithms
  ✅ Reference updates

synchronized:
  ✅ Multiple related variables
  ✅ Compound operations
  ✅ Check-then-act patterns
  ✅ When you need mutual exclusion

final fields:
  ✅ Immutable objects
  ✅ Safe publication guarantees
  ✅ Thread-safe by design
  ✅ Always prefer when possible
```

---

## Appendix: Complete Code Examples

All code examples from this guide are production-ready and follow best practices. They demonstrate:

1. **Proper error handling**
2. **Resource cleanup**
3. **Defensive copying**
4. **Documentation**
5. **Thread-safety guarantees**

### Practice Recommendations

1. **Type out all examples** - Don't copy-paste
2. **Modify and experiment** - Break things intentionally
3. **Add logging** - Understand execution flow
4. **Test with multiple threads** - Verify thread-safety
5. **Use profiling tools** - Measure performance
6. **Read JDK source** - Learn from experts

### Success Metrics

You've mastered Phase 3 when you can:
- ✅ Explain the Java Memory Model confidently
- ✅ Choose the right synchronization mechanism
- ✅ Identify memory visibility bugs in code reviews
- ✅ Design thread-safe classes from scratch
- ✅ Explain happens-before relationships
- ✅ Use volatile correctly and know its limitations

---

**Remember**: Memory visibility is one of the most subtle aspects of concurrency. Take your time with these concepts - they're the foundation for everything that follows!

---

*This guide is part of the Complete Java Multithreading & Concurrency Mastery Roadmap.*
*Progress: Phase 3 of 16 complete