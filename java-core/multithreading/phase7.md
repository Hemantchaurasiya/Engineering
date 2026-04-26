# Phase 7: Concurrent Collections - Complete Mastery Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Thread-Safe Collection Basics](#71-thread-safe-collection-basics)
3. [ConcurrentHashMap](#72-concurrenthashmap---the-production-solution)
4. [CopyOnWriteArrayList](#73-copyonwritearraylist---event-listeners-system)
5. [BlockingQueue Implementations](#74-blockingqueue---producer-consumer-pattern)
6. [Lock-Free Collections](#75--76-lock-free-collections)
7. [Complete Integration Example](#complete-e-commerce-system)
8. [Summary & Best Practices](#summary--best-practices)
9. [Practice Exercises](#practice-exercises)

---

## Introduction

Concurrent collections are the foundation of thread-safe programming in Java. This guide provides real-world examples demonstrating each collection type through production-grade scenarios.

**Why Concurrent Collections Matter:**
- Regular collections (HashMap, ArrayList) are NOT thread-safe
- `Collections.synchronizedXxx()` wrappers have poor performance
- Concurrent collections provide both safety AND performance
- They use sophisticated algorithms (lock-free, lock-striping, CAS operations)

---

## 7.1 Thread-Safe Collection Basics

### The Problem: Why Regular Collections Fail

Regular collections like `HashMap` and `ArrayList` have zero thread-safety guarantees. When multiple threads access them concurrently, you get:
- **Race conditions** - Lost updates
- **ConcurrentModificationException** - During iteration
- **Corrupted internal state** - Infinite loops, NPEs
- **Data inconsistency** - Stale reads

### Example: Broken Shopping Cart

```java
import java.util.*;
import java.util.concurrent.*;

/**
 * BROKEN IMPLEMENTATION - Demonstrates why regular HashMap fails
 */
public class BrokenShoppingCart {
    
    // PROBLEM: Regular HashMap is NOT thread-safe
    private final Map<String, Integer> cart = new HashMap<>();
    
    public void addItem(String productId, int quantity) {
        // Race condition here! Multiple threads can read the same old value
        Integer currentQty = cart.get(productId);
        int newQty = (currentQty == null) ? quantity : currentQty + quantity;
        
        // Context switch happens here - another thread overwrites our update!
        cart.put(productId, newQty);
    }
    
    public int getQuantity(String productId) {
        return cart.getOrDefault(productId, 0);
    }
    
    public static void main(String[] args) throws InterruptedException {
        BrokenShoppingCart cart = new BrokenShoppingCart();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1000);
        
        // 1000 threads each adding 1 item
        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                try {
                    cart.addItem("PRODUCT-123", 1);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        int finalQty = cart.getQuantity("PRODUCT-123");
        System.out.println("Expected: 1000, Actual: " + finalQty);
        // Output: Expected: 1000, Actual: 743 (LOST UPDATES!)
    }
}
```

### Naive Fix: Collections.synchronizedMap()

```java
class SynchronizedMapCart {
    private final Map<String, Integer> cart = 
        Collections.synchronizedMap(new HashMap<>());
    
    public void addItem(String productId, int quantity) {
        // STILL BROKEN! This needs external synchronization
        synchronized (cart) {
            Integer currentQty = cart.get(productId);
            int newQty = (currentQty == null) ? quantity : currentQty + quantity;
            cart.put(productId, newQty);
        }
    }
}
```

**Problems with synchronizedMap:**
1. Only makes individual operations atomic (get, put separately)
2. Compound operations need additional synchronization
3. All threads compete for ONE lock (poor concurrency)
4. Iterator needs manual synchronization
5. No atomic compound operations

**Solution:** Use `ConcurrentHashMap`!

---

## 7.2 ConcurrentHashMap - The Production Solution

### Key Features

- **Lock-free reads** - Multiple threads read simultaneously
- **Fine-grained locking** - Lock only specific buckets during writes
- **Atomic compound operations** - compute(), merge(), putIfAbsent()
- **No ConcurrentModificationException** - Safe iteration
- **Weakly consistent iterators** - Reflect state at some point

### Architecture Evolution

**Java 7 and Earlier:**
- Segment-based locking (16 segments by default)
- Each segment has its own lock
- 16x better concurrency than synchronized map

**Java 8+:**
- CAS-based updates (Compare-And-Swap)
- No segments - fine-grained per-bucket locking
- TreeBins for hash collision handling
- Even better performance

### Real-World Example: Production Shopping Cart

```java
import java.util.concurrent.*;
import java.time.LocalDateTime;

public class ShoppingCart {
    
    private static class CartItem {
        private final String productName;
        private final double price;
        private int quantity;
        private final LocalDateTime addedAt;
        
        public CartItem(String productName, double price, int quantity) {
            this.productName = productName;
            this.price = price;
            this.quantity = quantity;
            this.addedAt = LocalDateTime.now();
        }
        
        public double getSubtotal() {
            return price * quantity;
        }
    }
    
    private final ConcurrentHashMap<String, CartItem> items = 
        new ConcurrentHashMap<>();
    private final String userId;
    
    public ShoppingCart(String userId) {
        this.userId = userId;
    }
    
    /**
     * Add item using atomic compute() operation
     * Thread-safe without explicit synchronization!
     */
    public void addItem(String productId, String productName, 
                       double price, int quantity) {
        
        // compute() is ATOMIC - remapping function performed atomically
        items.compute(productId, (key, existingItem) -> {
            if (existingItem == null) {
                return new CartItem(productName, price, quantity);
            } else {
                existingItem.quantity += quantity;
                return existingItem;
            }
        });
        
        System.out.printf("[%s] Added %d x %s%n", userId, quantity, productName);
    }
    
    /**
     * Update quantity using computeIfPresent()
     */
    public boolean updateQuantity(String productId, int newQuantity) {
        if (newQuantity <= 0) {
            return removeItem(productId);
        }
        
        CartItem updated = items.computeIfPresent(productId, (key, item) -> {
            item.quantity = newQuantity;
            return item;
        });
        
        return updated != null;
    }
    
    /**
     * Remove item - atomic operation
     */
    public boolean removeItem(String productId) {
        return items.remove(productId) != null;
    }
    
    /**
     * Apply discount using computeIfPresent()
     */
    public void applyDiscount(String productId, double discountPercent) {
        items.computeIfPresent(productId, (key, item) -> {
            double discountedPrice = item.price * (1 - discountPercent / 100);
            return new CartItem(item.productName, discountedPrice, item.quantity);
        });
    }
    
    /**
     * Calculate total - safe iteration
     * Weakly consistent: reflects state at some point
     */
    public double getTotal() {
        return items.values().stream()
                    .mapToDouble(CartItem::getSubtotal)
                    .sum();
    }
    
    /**
     * Get item count using reduceValues() - parallel-friendly
     */
    public int getTotalItems() {
        return items.reduceValues(1, 
            item -> item.quantity, 
            Integer::sum);
    }
    
    /**
     * Atomic check-and-add using putIfAbsent()
     */
    public boolean addIfNotExists(String productId, String productName, double price) {
        CartItem previous = items.putIfAbsent(productId, 
            new CartItem(productName, price, 1));
        return previous == null; // Returns true if item was added
    }
    
    /**
     * Clear cart atomically
     */
    public void clear() {
        items.clear();
    }
}
```

### Atomic Operations Deep Dive

#### 1. compute(key, remappingFunction)
```java
// Atomically compute new value based on old value
items.compute("P001", (key, oldItem) -> {
    if (oldItem == null) {
        return new CartItem("Laptop", 999.99, 1);
    }
    oldItem.quantity += 1;
    return oldItem;
});
```

#### 2. computeIfPresent(key, remappingFunction)
```java
// Only compute if key exists
items.computeIfPresent("P001", (key, item) -> {
    item.quantity *= 2; // Double the quantity
    return item;
});
```

#### 3. computeIfAbsent(key, mappingFunction)
```java
// Compute only if key is absent
items.computeIfAbsent("P001", key -> 
    new CartItem("Laptop", 999.99, 1)
);
```

#### 4. merge(key, value, remappingFunction)
```java
// Merge value with existing entry
items.merge("P001", 
    new CartItem("Laptop", 999.99, 1),
    (existing, newItem) -> {
        existing.quantity += newItem.quantity;
        return existing;
    }
);
```

#### 5. putIfAbsent(key, value)
```java
// Add only if key doesn't exist
CartItem previous = items.putIfAbsent("P001", 
    new CartItem("Laptop", 999.99, 1)
);
// Returns null if added, existing value otherwise
```

### Performance Characteristics

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| get() | O(1) | Lock-free |
| put() | O(1) | Fine-grained locking |
| compute() | O(1) | Atomic compound operation |
| size() | O(n) | Avoid in hot paths (Java 8+: better) |
| Iteration | O(n) | Weakly consistent |

### When to Use ConcurrentHashMap

✅ **Perfect for:**
- High-concurrency scenarios
- More reads than writes (but handles writes well too)
- When you need atomic compound operations
- Caching layers
- Session management
- Configuration registries

❌ **Avoid when:**
- Single-threaded application
- Need strong consistency during iteration
- Memory is extremely constrained
- All operations are reads (use immutable map)

---

## 7.3 CopyOnWriteArrayList - Event Listeners System

### Key Characteristics

- **All writes copy the entire array** - Expensive!
- **Reads are lock-free** - No synchronization needed
- **Iterators never fail** - Snapshot-based
- **Perfect for read-heavy workloads** - 99% reads, 1% writes
- **Memory overhead** - Multiple array copies

### Real-World Example: Order Event Notification System

```java
import java.util.*;
import java.util.concurrent.*;

@FunctionalInterface
interface OrderEventListener {
    void onOrderEvent(OrderEvent event);
}

class OrderEvent {
    enum Type { CREATED, PAID, SHIPPED, DELIVERED, CANCELLED }
    
    private final String orderId;
    private final Type type;
    private final Map<String, String> details;
    
    public OrderEvent(String orderId, Type type, Map<String, String> details) {
        this.orderId = orderId;
        this.type = type;
        this.details = new HashMap<>(details);
    }
    
    public String getOrderId() { return orderId; }
    public Type getType() { return type; }
}

public class OrderEventNotifier {
    
    // CopyOnWriteArrayList: Perfect for listener lists!
    private final CopyOnWriteArrayList<OrderEventListener> listeners = 
        new CopyOnWriteArrayList<>();
    
    /**
     * Register listener - WRITE operation (expensive)
     * Creates new copy of internal array
     */
    public void addListener(String listenerId, OrderEventListener listener) {
        listeners.add(listener);
        System.out.printf("✓ Registered: %s (total: %d)%n", 
            listenerId, listeners.size());
    }
    
    /**
     * Remove listener - also WRITE operation
     */
    public boolean removeListener(OrderEventListener listener) {
        return listeners.remove(listener);
    }
    
    /**
     * Fire event - READ operation (very efficient)
     * Iterates over snapshot - safe even if listeners added/removed!
     */
    public void fireEvent(OrderEvent event) {
        // Lock-free iteration, never throws ConcurrentModificationException
        for (OrderEventListener listener : listeners) {
            try {
                listener.onOrderEvent(event);
            } catch (Exception e) {
                // Don't let one bad listener break others
                System.err.println("Listener error: " + e.getMessage());
            }
        }
    }
    
    public static void main(String[] args) {
        OrderEventNotifier notifier = new OrderEventNotifier();
        
        // Register listeners
        notifier.addListener("email-service", event -> {
            if (event.getType() == OrderEvent.Type.CREATED) {
                System.out.println("📧 Sending confirmation email");
            }
        });
        
        notifier.addListener("inventory-service", event -> {
            if (event.getType() == OrderEvent.Type.PAID) {
                System.out.println("📦 Reserving inventory");
            }
        });
        
        notifier.addListener("analytics", event -> 
            System.out.println("📊 Recording: " + event.getType())
        );
        
        // Fire events
        notifier.fireEvent(new OrderEvent("ORD-001", 
            OrderEvent.Type.CREATED, Map.of()));
        notifier.fireEvent(new OrderEvent("ORD-001", 
            OrderEvent.Type.PAID, Map.of()));
    }
}
```

### Safe Concurrent Modification Example

```java
public static void demonstrateSafeIteration() throws InterruptedException {
    OrderEventNotifier notifier = new OrderEventNotifier();
    
    // Add initial listeners
    notifier.addListener("L1", e -> System.out.println("L1: " + e.getType()));
    notifier.addListener("L2", e -> System.out.println("L2: " + e.getType()));
    
    ExecutorService executor = Executors.newFixedThreadPool(2);
    
    // Thread 1: Fire events continuously
    executor.submit(() -> {
        for (int i = 0; i < 5; i++) {
            notifier.fireEvent(new OrderEvent("ORD-" + i, 
                OrderEvent.Type.CREATED, Map.of()));
            Thread.sleep(100);
        }
    });
    
    // Thread 2: Add listener DURING iteration
    executor.submit(() -> {
        Thread.sleep(150);
        System.out.println("\n➕ Adding listener during iteration");
        notifier.addListener("dynamic", e -> 
            System.out.println("DYNAMIC: I was added while iterating!"));
    });
    
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
    
    // ✅ No ConcurrentModificationException!
}
```

### When to Use CopyOnWriteArrayList

✅ **Perfect for:**
- Event listener lists (like above)
- Observer patterns
- Configuration that rarely changes
- Small collections (< 100 elements typically)
- Read-heavy workloads (99% reads, 1% writes)
- When you need fail-safe iteration

❌ **Avoid for:**
- Large collections (memory overhead)
- Frequent modifications (copies entire array!)
- Write-heavy workloads
- Fast indexOf/contains needed (always O(n))
- Memory-constrained environments

### Performance Characteristics

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| get(index) | O(1) | Lock-free |
| add(element) | O(n) | Copies entire array |
| remove(index) | O(n) | Copies entire array |
| iterator() | O(1) | Returns snapshot |
| Iteration | O(n) | Never fails |

---

## 7.4 BlockingQueue - Producer-Consumer Pattern

### BlockingQueue Family Overview

| Implementation | Capacity | Ordering | Use Case |
|----------------|----------|----------|----------|
| ArrayBlockingQueue | Bounded (fixed) | FIFO | Backpressure, bounded resources |
| LinkedBlockingQueue | Optionally bounded | FIFO | Better throughput |
| PriorityBlockingQueue | Unbounded | Priority | Task scheduling |
| SynchronousQueue | 0 (direct handoff) | N/A | Thread coordination |
| DelayQueue | Unbounded | Delay | Scheduled tasks |

### Key Methods

**Blocking Operations:**
```java
queue.put(element);     // Blocks if queue is full
element = queue.take(); // Blocks if queue is empty
```

**Timeout Operations:**
```java
boolean added = queue.offer(element, 1, TimeUnit.SECONDS);
Element e = queue.poll(1, TimeUnit.SECONDS);
```

**Non-blocking Operations:**
```java
boolean added = queue.offer(element); // Returns false if full
Element e = queue.poll();             // Returns null if empty
```

### Real-World Example: Order Processing Pipeline

```java
import java.util.concurrent.*;
import java.time.LocalDateTime;

class Order implements Comparable<Order> {
    private final String orderId;
    private final String customerId;
    private final double amount;
    private final boolean isPriority; // VIP customers
    private volatile OrderStatus status;
    
    enum OrderStatus {
        SUBMITTED, VALIDATED, PAYMENT_PROCESSING, PAID, FULFILLED
    }
    
    public Order(String customerId, double amount, boolean isPriority) {
        this.orderId = "ORD-" + System.nanoTime();
        this.customerId = customerId;
        this.amount = amount;
        this.isPriority = isPriority;
        this.status = OrderStatus.SUBMITTED;
    }
    
    // For PriorityQueue: VIP orders processed first
    @Override
    public int compareTo(Order other) {
        if (this.isPriority != other.isPriority) {
            return this.isPriority ? -1 : 1;
        }
        return Double.compare(other.amount, this.amount);
    }
}

public class OrderProcessingPipeline {
    
    // Stage 1: Bounded queue prevents overload
    private final BlockingQueue<Order> validationQueue;
    
    // Stage 2: Priority queue for VIP customers
    private final PriorityBlockingQueue<Order> paymentQueue;
    
    // Stage 3: Unbounded queue for fulfillment
    private final LinkedBlockingQueue<Order> fulfillmentQueue;
    
    private static final Order POISON_PILL = 
        new Order("SHUTDOWN", 0, false);
    
    public OrderProcessingPipeline(int validationQueueSize) {
        // ArrayBlockingQueue: Fixed size, blocks when full
        this.validationQueue = new ArrayBlockingQueue<>(validationQueueSize);
        
        // PriorityBlockingQueue: Unbounded, orders by priority
        this.paymentQueue = new PriorityBlockingQueue<>();
        
        // LinkedBlockingQueue: Unbounded, FIFO
        this.fulfillmentQueue = new LinkedBlockingQueue<>();
        
        startWorkers();
    }
    
    /**
     * Submit order - BLOCKS if queue is full (natural backpressure!)
     */
    public boolean submitOrder(Order order) throws InterruptedException {
        validationQueue.put(order); // Blocks until space available
        return true;
    }
    
    /**
     * Try submit with timeout - returns false if queue full
     */
    public boolean trySubmitOrder(Order order, long timeout, TimeUnit unit) 
            throws InterruptedException {
        return validationQueue.offer(order, timeout, unit);
    }
    
    private void startWorkers() {
        // Validation workers
        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                while (true) {
                    try {
                        Order order = validationQueue.take(); // Blocks
                        
                        if (order == POISON_PILL) {
                            validationQueue.put(POISON_PILL);
                            break;
                        }
                        
                        validateOrder(order);
                        paymentQueue.put(order);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }).start();
        }
        
        // Payment processors
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                while (true) {
                    try {
                        Order order = paymentQueue.take();
                        
                        if (order == POISON_PILL) {
                            paymentQueue.put(POISON_PILL);
                            break;
                        }
                        
                        processPayment(order);
                        fulfillmentQueue.put(order);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }).start();
        }
        
        // Fulfillment workers
        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                while (true) {
                    try {
                        // poll with timeout to check shutdown
                        Order order = fulfillmentQueue.poll(1, TimeUnit.SECONDS);
                        
                        if (order == POISON_PILL) {
                            fulfillmentQueue.put(POISON_PILL);
                            break;
                        }
                        
                        if (order != null) {
                            fulfillOrder(order);
                        }
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }).start();
        }
    }
    
    private void validateOrder(Order order) throws InterruptedException {
        System.out.println("✓ Validating: " + order.orderId);
        Thread.sleep(100); // Simulate validation
        order.status = Order.OrderStatus.VALIDATED;
    }
    
    private void processPayment(Order order) throws InterruptedException {
        System.out.printf("💳 Processing payment: %s%s%n", 
            order.orderId, order.isPriority ? " [VIP]" : "");
        Thread.sleep(200);
        order.status = Order.OrderStatus.PAID;
    }
    
    private void fulfillOrder(Order order) throws InterruptedException {
        System.out.println("📦 Shipping: " + order.orderId);
        Thread.sleep(150);
        order.status = Order.OrderStatus.FULFILLED;
    }
    
    public void shutdown() throws InterruptedException {
        validationQueue.put(POISON_PILL);
        paymentQueue.put(POISON_PILL);
        fulfillmentQueue.put(POISON_PILL);
    }
}
```

### BlockingQueue Comparison

#### 1. ArrayBlockingQueue
```java
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(100);

// Blocks if full - provides natural backpressure
queue.put(task);

// Non-blocking alternative
if (!queue.offer(task)) {
    System.out.println("Queue full - rejecting task");
}
```

**Characteristics:**
- Fixed capacity (bounded)
- Backed by array
- FIFO ordering
- Optionally fair (FIFO for waiting threads)
- Best for preventing system overload

#### 2. LinkedBlockingQueue
```java
// Optionally bounded
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(1000);

// Or unbounded (dangerous!)
BlockingQueue<Task> unbounded = new LinkedBlockingQueue<>();
```

**Characteristics:**
- Optionally bounded (default unbounded)
- Backed by linked nodes
- FIFO ordering
- Better throughput than ArrayBlockingQueue
- Uses two locks (head and tail separate)

#### 3. PriorityBlockingQueue
```java
// Orders by natural ordering or comparator
BlockingQueue<Task> queue = new PriorityBlockingQueue<>();

class Task implements Comparable<Task> {
    int priority;
    
    @Override
    public int compareTo(Task other) {
        return Integer.compare(other.priority, this.priority); // High first
    }
}
```

**Characteristics:**
- Unbounded
- Heap-based (priority order)
- Does NOT guarantee FIFO for equal priorities
- Iterator NOT in priority order (!)
- Best for task scheduling

#### 4. SynchronousQueue
```java
// Zero capacity - direct handoff
BlockingQueue<Task> queue = new SynchronousQueue<>();

// Producer blocks until consumer ready
queue.put(task); // Waits for take()

// Consumer blocks until producer ready
Task task = queue.take(); // Waits for put()
```

**Characteristics:**
- No capacity (0 elements)
- Each put() must wait for take()
- Used by `Executors.newCachedThreadPool()`
- Best for thread coordination

#### 5. DelayQueue
```java
class DelayedTask implements Delayed {
    private final long executeAt;
    
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(executeAt - System.currentTimeMillis(), 
                           TimeUnit.MILLISECONDS);
    }
    
    @Override
    public int compareTo(Delayed other) {
        return Long.compare(this.getDelay(TimeUnit.MILLISECONDS),
                           other.getDelay(TimeUnit.MILLISECONDS));
    }
}

DelayQueue<DelayedTask> queue = new DelayQueue<>();
queue.put(new DelayedTask(System.currentTimeMillis() + 5000));

// take() blocks until delay expires
DelayedTask task = queue.take();
```

**Characteristics:**
- Unbounded
- Elements available only after delay expires
- Best for scheduled tasks, caching with TTL

---

## 7.5 & 7.6 Lock-Free Collections

### ConcurrentLinkedQueue

**Key Features:**
- **Completely lock-free** using CAS operations
- **Unbounded** capacity
- **FIFO** ordering
- **Excellent throughput** - no lock contention
- **Weakly consistent** iterators
- **size() is O(n)** - avoid in hot paths!

### Real-World Example: High-Frequency Trading Order Book

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class Trade {
    private static final AtomicLong tradeIdGenerator = new AtomicLong(0);
    
    enum Side { BUY, SELL }
    enum Status { PENDING, FILLED, CANCELLED }
    
    private final long tradeId;
    private final String symbol;
    private final Side side;
    private final double price;
    private final int quantity;
    private volatile Status status;
    
    public Trade(String symbol, Side side, double price, int quantity) {
        this.tradeId = tradeIdGenerator.incrementAndGet();
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.status = Status.PENDING;
    }
    
    // Getters and setters...
}

/**
 * Lock-free order book - perfect for HFT
 */
class OrderBook {
    
    // Lock-free queue using CAS internally
    private final ConcurrentLinkedQueue<Trade> pendingOrders = 
        new ConcurrentLinkedQueue<>();
    
    private final ConcurrentLinkedQueue<Trade> filledOrders = 
        new ConcurrentLinkedQueue<>();
    
    // Atomic counters
    private final AtomicLong totalOrdersSubmitted = new AtomicLong(0);
    private final AtomicLong totalOrdersFilled = new AtomicLong(0);
    
    /**
     * Submit order - completely lock-free!
     */
    public boolean submitOrder(Trade trade) {
        boolean added = pendingOrders.offer(trade); // Non-blocking
        if (added) {
            totalOrdersSubmitted.incrementAndGet();
        }
        return added;
    }
    
    /**
     * Match order - lock-free removal
     */
    public Trade matchOrder() {
        Trade order = pendingOrders.poll(); // Non-blocking
        
        if (order != null) {
            order.setStatus(Trade.Status.FILLED);
            filledOrders.offer(order);
            totalOrdersFilled.incrementAndGet();
            return order;
        }
        
        return null;
    }
    
    /**
     * Get pending count - O(n) operation!
     * Avoid in hot paths
     */
    public int getPendingCount() {
        return pendingOrders.size(); // Weakly consistent
    }
}
```

### Performance Demonstration

```java
public static void demonstrateHighFrequency() throws InterruptedException {
    OrderBook orderBook = new OrderBook();
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(1000);
    
    long startTime = System.nanoTime();
    
    // 1000 concurrent order submissions
    for (int i = 0; i < 1000; i++) {
        executor.submit(() -> {
            try {
                Trade trade = new Trade("AAPL", Trade.Side.BUY, 
                    150.0, 100);
                orderBook.submitOrder(trade);
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await();
    long duration = System.nanoTime() - startTime;
    
    executor.shutdown();
    
    System.out.printf("Processed 1000 orders in %.2f ms%n", 
        duration / 1_000_000.0);
    System.out.printf("Throughput: %.0f orders/second%n", 
        1_000_000_000.0 / duration * 1000);
    
    // Typical output: 10-20ms, 50,000-100,000 orders/second
}
```

### ConcurrentSkipListMap

**Key Features:**
- **Lock-free reads** (concurrent with writes)
- **Sorted** by key (NavigableMap interface)
- **O(log n)** operations on average
- **Better concurrency** than TreeMap
- **Perfect for** price-time priority, leaderboards

### Real-World Example: Price Level Order Book

```java
import java.util.*;
import java.util.concurrent.*;

class PriceLevelOrderBook {
    
    // Skip list maintains sorted price levels
    // Highest buy price first
    private final ConcurrentSkipListMap<Double, ConcurrentLinkedQueue<Trade>> 
        buyOrders = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    
    // Lowest sell price first
    private final ConcurrentSkipListMap<Double, ConcurrentLinkedQueue<Trade>> 
        sellOrders = new ConcurrentSkipListMap<>();
    
    /**
     * Add order to price level
     */
    public void addOrder(Trade trade) {
        ConcurrentSkipListMap<Double, ConcurrentLinkedQueue<Trade>> book = 
            (trade.getSide() == Trade.Side.BUY) ? buyOrders : sellOrders;
        
        // computeIfAbsent is atomic
        book.computeIfAbsent(trade.getPrice(), 
            k -> new ConcurrentLinkedQueue<>())
            .offer(trade);
    }
    
    /**
     * Get best bid (highest buy price)
     */
    public Double getBestBid() {
        return buyOrders.isEmpty() ? null : buyOrders.firstKey();
    }
    
    /**
     * Get best ask (lowest sell price)
     */
    public Double getBestAsk() {
        return sellOrders.isEmpty() ? null : sellOrders.firstKey();
    }
    
    /**
     * Get spread
     */
    public Double getSpread() {
        Double bid = getBestBid();
        Double ask = getBestAsk();
        return (bid != null && ask != null) ? ask - bid : null;
    }
    
    /**
     * Print market depth
     */
    public void printTopLevels(int n) {
        System.out.println("\n=== Market Depth ===");
        
        System.out.println("ASKS:");
        sellOrders.entrySet().stream()
            .limit(n)
            .forEach(entry -> {
                System.out.printf("  $%.2f: %d orders%n", 
                    entry.getKey(), entry.getValue().size());
            });
        
        Double spread = getSpread();
        if (spread != null) {
            System.out.printf("\n  --- SPREAD: $%.2f ---\n", spread);
        }
        
        System.out.println("BIDS:");
        buyOrders.entrySet().stream()
            .limit(n)
            .forEach(entry -> {
                System.out.printf("  $%.2f: %d orders%n", 
                    entry.getKey(), entry.getValue().size());
            });
    }
    
    /**
     * Match orders at best prices
     */
    public List<Trade> matchOrders() {
        List<Trade> matches = new ArrayList<>();
        
        while (true) {
            Double bestBid = getBestBid();
            Double bestAsk = getBestAsk();
            
            if (bestBid == null || bestAsk == null || bestBid < bestAsk) {
                break; // No match possible
            }
            
            ConcurrentLinkedQueue<Trade> buyQueue = buyOrders.get(bestBid);
            ConcurrentLinkedQueue<Trade> sellQueue = sellOrders.get(bestAsk);
            
            Trade buyOrder = buyQueue.poll();
            Trade sellOrder = sellQueue.poll();
            
            if (buyOrder != null && sellOrder != null) {
                buyOrder.setStatus(Trade.Status.FILLED);
                sellOrder.setStatus(Trade.Status.FILLED);
                matches.add(buyOrder);
                matches.add(sellOrder);
            }
            
            // Clean up empty levels
            if (buyQueue.isEmpty()) buyOrders.remove(bestBid);
            if (sellQueue.isEmpty()) sellOrders.remove(bestAsk);
        }
        
        return matches;
    }
}
```

### Usage Example

```java
public static void main(String[] args) {
    PriceLevelOrderBook book = new PriceLevelOrderBook();
    
    // Add buy orders
    book.addOrder(new Trade("TSLA", Trade.Side.BUY, 250.00, 100));
    book.addOrder(new Trade("TSLA", Trade.Side.BUY, 250.00, 50));
    book.addOrder(new Trade("TSLA", Trade.Side.BUY, 249.50, 200));
    
    // Add sell orders
    book.addOrder(new Trade("TSLA", Trade.Side.SELL, 251.00, 75));
    book.addOrder(new Trade("TSLA", Trade.Side.SELL, 251.50, 100));
    
    book.printTopLevels(3);
    
    // Execute matching
    List<Trade> matches = book.matchOrders();
    System.out.println("Matched " + matches.size() + " orders");
    
    book.printTopLevels(3);
}
```

### Lock-Free Collection Benefits

**Why Lock-Free Matters:**
1. **No thread blocking** - better latency
2. **No deadlock possibility** - progress guaranteed
3. **Better CPU cache** utilization
4. **Scales better** on multi-core systems
5. **Lower tail latencies** - no lock waiting

**Trade-offs:**
- More complex implementation
- size() operations may be expensive
- Weakly consistent iteration
- Some operations are best-effort

**Real-World Usage:**
- High-frequency trading systems
- Message queues (Kafka, RabbitMQ)
- Event processing pipelines
- Task schedulers
- Logging systems

---

## Complete E-Commerce System

Here's a comprehensive example integrating ALL concurrent collections in a production-grade e-commerce platform:

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ECommercePlatform {
    
    // 1. Product catalog - ConcurrentHashMap
    private final ConcurrentHashMap<String, Product> catalog = 
        new ConcurrentHashMap<>();
    
    // 2. Price index - ConcurrentSkipListMap (sorted)
    private final ConcurrentSkipListMap<Double, Set<Product>> priceIndex = 
        new ConcurrentSkipListMap<>();
    
    // 3. User sessions - ConcurrentHashMap
    private final ConcurrentHashMap<String, Set<String>> userCarts = 
        new ConcurrentHashMap<>();
    
    // 4. Order queue - PriorityBlockingQueue (VIP first)
    private final PriorityBlockingQueue<Order> orderQueue = 
        new PriorityBlockingQueue<>();
    
    // 5. Fulfillment - LinkedBlockingQueue
    private final LinkedBlockingQueue<Order> fulfillmentQueue = 
        new LinkedBlockingQueue<>();
    
    // 6. Notifications - ConcurrentLinkedQueue (lock-free)
    private final ConcurrentLinkedQueue<Notification> notificationQueue = 
        new ConcurrentLinkedQueue<>();
    
    // 7. Event listeners - CopyOnWriteArrayList (read-heavy)
    private final CopyOnWriteArrayList<OrderEventListener> orderListeners = 
        new CopyOnWriteArrayList<>();
    
    // Statistics
    private final AtomicLong totalOrders = new AtomicLong(0);
    private final AtomicLong totalRevenue = new AtomicLong(0);
    
    // Catalog Management
    public void addProduct(String id, String name, double price, int stock) {
        Product product = new Product(id, name, price, stock);
        catalog.put(id, product);
        priceIndex.computeIfAbsent(price, k -> ConcurrentHashMap.newKeySet())
                  .add(product);
    }
    
    // Cart Management
    public void addToCart(String userId, String productId) {
        userCarts.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                 .add(productId);
        notify(userId, "Added to cart");
    }
    
    // Order Processing
    public boolean checkout(String userId, boolean isVip) {
        Set<String> cartItems = userCarts.get(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            return false;
        }
        
        // Reserve stock and calculate total
        double total = 0;
        for (String productId : cartItems) {
            Product product = catalog.get(productId);
            if (product != null && product.reserveStock(1)) {
                total += product.getPrice();
            } else {
                return false; // Out of stock
            }
        }
        
        // Create order
        Order order = new Order(userId, new ArrayList<>(cartItems), 
            total, isVip);
        
        // Add to priority queue
        orderQueue.put(order);
        
        // Clear cart
        userCarts.remove(userId);
        
        // Update statistics
        totalOrders.incrementAndGet();
        totalRevenue.addAndGet((long)(total * 100));
        
        // Notify listeners (safe iteration with CopyOnWriteArrayList)
        for (OrderEventListener listener : orderListeners) {
            listener.onOrderCreated(order);
        }
        
        notify(userId, "Order placed: " + order);
        return true;
    }
    
    // Background Workers
    private void processOrders() {
        while (running) {
            Order order = orderQueue.poll(1, TimeUnit.SECONDS);
            if (order != null) {
                // VIP orders come first due to PriorityBlockingQueue
                System.out.println("Processing: " + order);
                fulfillmentQueue.put(order);
            }
        }
    }
    
    private void fulfillOrders() {
        while (running) {
            Order order = fulfillmentQueue.poll(1, TimeUnit.SECONDS);
            if (order != null) {
                System.out.println("Shipping: " + order);
                notify(order.getUserId(), "Order shipped!");
            }
        }
    }
    
    private void sendNotifications() {
        while (running) {
            Notification notif = notificationQueue.poll();
            if (notif != null) {
                System.out.println("Notification: " + notif);
            }
            Thread.sleep(100);
        }
    }
    
    private void notify(String userId, String message) {
        notificationQueue.offer(new Notification(userId, message));
    }
}
```

This complete system demonstrates:
- **ConcurrentHashMap** for catalog and sessions
- **ConcurrentSkipListMap** for price-sorted search
- **PriorityBlockingQueue** for VIP order priority
- **LinkedBlockingQueue** for fulfillment pipeline
- **ConcurrentLinkedQueue** for notifications
- **CopyOnWriteArrayList** for event listeners

---

## Summary & Best Practices

### Collection Selection Guide

| Use Case | Collection | Why |
|----------|-----------|-----|
| High-concurrency cache | ConcurrentHashMap | Lock-free reads, atomic operations |
| Event listeners | CopyOnWriteArrayList | Read-heavy, safe iteration |
| Producer-consumer | BlockingQueue | Built-in blocking, backpressure |
| Task queue with priority | PriorityBlockingQueue | Priority ordering |
| Lock-free messaging | ConcurrentLinkedQueue | No locks, high throughput |
| Sorted concurrent map | ConcurrentSkipListMap | Sorted + concurrent |

### Design Principles

1. **Choose based on access pattern**
   - Read-heavy → CopyOnWriteArrayList
   - Write-heavy → ConcurrentHashMap
   - Mixed → BlockingQueue variants

2. **Consider read/write ratio**
   - 90% reads, 10% writes → CopyOnWrite*
   - 50/50 → ConcurrentHashMap
   - Unpredictable → BlockingQueue

3. **Bounded vs Unbounded**
   - Bounded prevents OOM
   - Unbounded offers flexibility
   - Always monitor queue sizes

4. **Blocking vs Non-blocking**
   - Blocking for backpressure
   - Non-blocking for throughput
   - Consider your requirements

5. **Ordering requirements**
   - FIFO → LinkedBlockingQueue
   - Priority → PriorityBlockingQueue
   - Sorted → ConcurrentSkipListMap

6. **Performance under contention**
   - Lock-free best for high contention
   - Lock-based fine for low contention
   - Profile your specific workload

### Common Mistakes to Avoid

❌ **DON'T:**
1. Use `Collections.synchronizedXxx()` for high concurrency
2. Use `CopyOnWriteArrayList` for large or frequently modified lists
3. Create unbounded queues without monitoring
4. Forget about weakly consistent iteration semantics
5. Ignore memory overhead of concurrent collections
6. Call `size()` on `ConcurrentLinkedQueue` in hot paths
7. Assume strong consistency during iteration

✅ **DO:**
1. Use atomic operations (compute, merge) instead of get-modify-put
2. Monitor queue sizes and set reasonable bounds
3. Choose collection based on profiling, not assumptions
4. Use appropriate timeout values with blocking operations
5. Implement proper shutdown mechanisms (poison pills)
6. Consider using bulk operations when possible
7. Test under realistic concurrent load

### Performance Tips

1. **ConcurrentHashMap**
   - Use `compute*()` methods for atomic updates
   - Avoid unnecessary synchronization
   - Size properly (0.75 load factor)

2. **CopyOnWriteArrayList**
   - Keep lists small (< 100 elements)
   - Batch writes when possible
   - Use for truly read-heavy scenarios

3. **BlockingQueue**
   - Use bounded queues with monitoring
   - Choose right variant for your pattern
   - Consider batch operations

4. **Lock-free collections**
   - Avoid `size()` in hot paths
   - Accept weakly consistent iteration
   - Profile before assuming faster

---

## Practice Exercises

### Exercise 1: Real-Time Chat System

Build a chat application using concurrent collections:

**Requirements:**
- Support multiple chat rooms
- Users can join/leave rooms
- Messages broadcast to all room members
- Online user tracking
- Message history per room

**Collections to use:**
- `ConcurrentHashMap<String, ChatRoom>` - room registry
- `CopyOnWriteArrayList<User>` - room subscribers
- `ConcurrentLinkedQueue<Message>` - message delivery
- `LinkedBlockingQueue<Message>` - persistence queue

**Key features:**
```java
public class ChatSystem {
    private final ConcurrentHashMap<String, ChatRoom> rooms;
    private final ConcurrentHashMap<String, User> onlineUsers;
    
    public void sendMessage(String roomId, Message message) {
        // Broadcast to all subscribers
    }
    
    public void joinRoom(String userId, String roomId) {
        // Add to subscriber list
    }
    
    public void leaveRoom(String userId, String roomId) {
        // Remove from subscriber list
    }
}
```

### Exercise 2: URL Rate Limiter

Implement a distributed rate limiter:

**Requirements:**
- Limit requests per IP address
- Sliding window algorithm
- Configurable limits (100 req/min)
- Automatic cleanup of expired entries

**Collections to use:**
- `ConcurrentHashMap<String, RateLimitBucket>` - per-IP buckets
- `DelayQueue<ExpiredEntry>` - automatic cleanup
- `AtomicInteger` for counters

**Key features:**
```java
public class RateLimiter {
    private final ConcurrentHashMap<String, TokenBucket> buckets;
    private final int maxRequests;
    private final Duration window;
    
    public boolean allowRequest(String ipAddress) {
        // Check and update rate limit
    }
    
    public void cleanup() {
        // Remove expired entries
    }
}
```

### Exercise 3: Task Scheduler

Build a priority-based task scheduler:

**Requirements:**
- Schedule tasks with delays
- Support priority levels (LOW, NORMAL, HIGH, CRITICAL)
- Recurring tasks
- Task dependencies
- Graceful shutdown

**Collections to use:**
- `PriorityBlockingQueue<ScheduledTask>` - task queue
- `ConcurrentSkipListMap<Long, Set<Task>>` - time-based index
- `ConcurrentHashMap<String, Task>` - task registry

**Key features:**
```java
public class TaskScheduler {
    private final PriorityBlockingQueue<ScheduledTask> taskQueue;
    private final ExecutorService workers;
    
    public void schedule(Task task, Duration delay, Priority priority) {
        // Add to queue with priority
    }
    
    public void scheduleRecurring(Task task, Duration interval) {
        // Reschedule after execution
    }
    
    public void cancel(String taskId) {
        // Remove from queue
    }
}
```

### Exercise 4: Concurrent Cache with TTL

Implement a thread-safe cache with time-to-live:

**Requirements:**
- Get/put operations
- TTL per entry
- LRU eviction policy
- Maximum size limit
- Statistics (hit rate, size)

**Collections to use:**
- `ConcurrentHashMap<K, CacheEntry<V>>` - main storage
- `ConcurrentSkipListMap<Long, Set<K>>` - expiration index
- `ConcurrentLinkedDeque<K>` - LRU tracking

**Key features:**
```java
public class ConcurrentCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> cache;
    private final int maxSize;
    private final Duration defaultTtl;
    
    public V get(K key) {
        // Return value if not expired
    }
    
    public void put(K key, V value, Duration ttl) {
        // Add with TTL, evict if necessary
    }
    
    public CacheStats getStats() {
        // Return hit rate, size, etc.
    }
}
```

### Exercise 5: Log Aggregator

Build a high-performance log aggregation system:

**Requirements:**
- Collect logs from multiple sources
- Batch processing
- Priority levels (ERROR > WARN > INFO)
- Persistent storage
- Real-time search

**Collections to use:**
- `ConcurrentLinkedQueue<LogEntry>` - incoming logs
- `PriorityBlockingQueue<LogEntry>` - processing queue
- `ConcurrentHashMap<String, List<LogEntry>>` - search index
- `LinkedBlockingQueue<Batch>` - persistence queue

**Key features:**
```java
public class LogAggregator {
    private final ConcurrentLinkedQueue<LogEntry> incomingLogs;
    private final PriorityBlockingQueue<LogEntry> processingQueue;
    
    public void log(LogEntry entry) {
        // Add to incoming queue
    }
    
    public List<LogEntry> search(String query, LogLevel minLevel) {
        // Search indexed logs
    }
    
    private void batchProcessor() {
        // Batch and persist logs
    }
}
```

---

## Next Steps

You've completed Phase 7! Here's what to do next:

### Immediate Actions:
1. **Implement all 5 practice exercises**
2. **Read JDK source code** for these collections
3. **Profile your code** to see performance characteristics
4. **Write comprehensive unit tests** including race condition tests

### Continue Learning:
- **Phase 8**: Atomic Variables and CAS Operations
- **Phase 9**: Fork/Join Framework
- **Phase 10**: CompletableFuture and Async Programming

### Recommended Reading:
- "Java Concurrency in Practice" by Brian Goetz (Chapters 5-6)
- "Concurrent Programming in Java" by Doug Lea
- JDK source code: `java.util.concurrent` package
- Java Memory Model (JLS Chapter 17)

### Key Takeaways:

1. **Regular collections are NOT thread-safe**
2. **Choose collections based on access patterns**
3. **ConcurrentHashMap for general-purpose concurrency**
4. **CopyOnWriteArrayList for read-heavy scenarios**
5. **BlockingQueue for producer-consumer patterns**
6. **Lock-free collections for high throughput**
7. **Always profile before optimizing**

### Resources:
- [OpenJDK Source Code](https://github.com/openjdk/jdk)
- [Java Concurrency Tutorial](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [Doug Lea's Concurrent Programming](http://gee.cs.oswego.edu/dl/cpj/)
- [JCStress for concurrency testing](https://github.com/openjdk/jcstress)

---

**Ready to continue? Let me know if you want to:**
1. Move to Phase 8 (Atomic Variables)
2. Deep dive into specific collection internals
3. Build more complex projects
4. Review and practice more exercises

Good luck on your concurrency mastery journey! 🚀