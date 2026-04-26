# Java Multithreading & Concurrency — Phase 11: Thread Safety Patterns

> **From the Complete Java Multithreading & Concurrency Mastery Roadmap**  
> Real-world examples with production-quality code

---

## Table of Contents

1. [11.1 Immutability](#111-immutability)
2. [11.2 Thread Confinement](#112-thread-confinement)
3. [11.3 Safe Publication](#113-safe-publication)
4. [11.4 Double-Checked Locking](#114-double-checked-locking)
5. [11.5 Instance Confinement](#115-instance-confinement)
6. [Summary: When to Use Which Pattern](#summary-when-to-use-which-pattern)

---

## 11.1 Immutability

**Real-world example: A Money/Currency transfer record**

A financial transaction record should never change after creation. If it could be mutated, you'd have race conditions where one thread reads a half-updated transaction.

### Rules for Immutability

- Class is `final` — prevents subclasses from adding mutable state
- All fields are `private final`
- No setters
- Defensive copy of mutable inputs (lists, arrays, dates)
- Return defensive copies or unmodifiable views of mutable fields

```java
// BAD - Mutable, NOT thread-safe
public class Transaction {
    private String id;
    private double amount;
    private String fromAccount;
    
    public void setAmount(double amount) { this.amount = amount; } // Dangerous!
}

// GOOD - Immutable, inherently thread-safe
public final class Transaction {                    // final: can't be subclassed
    private final String id;
    private final BigDecimal amount;                // BigDecimal instead of double for precision
    private final String fromAccount;
    private final String toAccount;
    private final Instant timestamp;
    private final List<String> tags;               // mutable type - needs defensive copy

    public Transaction(String id, BigDecimal amount, 
                       String fromAccount, String toAccount,
                       List<String> tags) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.amount = Objects.requireNonNull(amount, "amount cannot be null");
        this.fromAccount = Objects.requireNonNull(fromAccount);
        this.toAccount = Objects.requireNonNull(toAccount);
        this.timestamp = Instant.now();
        // Defensive copy: caller's list changes won't affect our state
        this.tags = List.copyOf(tags);            // Java 10+: unmodifiable + copied
    }

    // Only getters, no setters
    public String getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public Instant getTimestamp() { return timestamp; }
    
    public List<String> getTags() { 
        return tags; // already unmodifiable, safe to return directly
    }

    // Need a "modified" version? Return a new object
    public Transaction withTags(List<String> newTags) {
        return new Transaction(this.id, this.amount, 
                               this.fromAccount, this.toAccount, newTags);
    }

    @Override
    public String toString() {
        return String.format("Transaction[id=%s, amount=%s, from=%s, to=%s]",
                id, amount, fromAccount, toAccount);
    }
}

// Usage: multiple threads can safely share the same Transaction object
public class ImmutabilityDemo {
    public static void main(String[] args) throws InterruptedException {
        Transaction tx = new Transaction(
            "TXN-001",
            new BigDecimal("1500.00"),
            "ACC-123", "ACC-456",
            List.of("urgent", "international")
        );

        // Safe to share across threads - no synchronization needed
        Runnable reader = () -> {
            for (int i = 0; i < 1000; i++) {
                // No race condition possible - object can't change
                System.out.println(Thread.currentThread().getName() 
                    + " reads: " + tx.getAmount());
            }
        };

        Thread t1 = new Thread(reader, "AuditThread");
        Thread t2 = new Thread(reader, "ReportingThread");
        Thread t3 = new Thread(reader, "NotificationThread");

        t1.start(); t2.start(); t3.start();
        t1.join(); t2.join(); t3.join();
    }
}
```

---

## 11.2 Thread Confinement

**Real-world example: Database connections and user session data**

A database connection is NOT thread-safe. You must ensure each thread uses its own connection. This is exactly what connection pools do internally.

### Stack Confinement

Object is confined to the stack — only one thread can ever touch it.

```java
public class ReportGenerator {
    
    public String generateMonthlyReport(List<Transaction> transactions) {
        // StringBuilder is confined to this method's stack frame
        // No other thread can access it - inherently safe
        StringBuilder report = new StringBuilder();
        
        report.append("=== Monthly Report ===\n");
        
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction tx : transactions) {
            report.append(tx.getId()).append(": ").append(tx.getAmount()).append("\n");
            total = total.add(tx.getAmount());
        }
        
        report.append("Total: ").append(total);
        
        // Once we return the String, it's safe (String is immutable)
        return report.toString();
        // report (StringBuilder) is garbage collected - was never shared
    }
}
```

### ThreadLocal — the most important confinement tool

> ⚠️ **The #1 ThreadLocal mistake**: Forgetting to call `remove()` in thread pools. The thread gets reused for the next task and picks up the previous request's data — a serious security bug in web applications.

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Real-world: each thread gets its own DB connection
public class DatabaseConnectionManager {

    // Each thread gets its own Connection instance
    private static final ThreadLocal<Connection> connectionHolder = 
        ThreadLocal.withInitial(() -> {
            try {
                System.out.println(Thread.currentThread().getName() 
                    + " creating new DB connection");
                // In real code, this would come from a connection pool
                return DriverManager.getConnection(
                    "jdbc:h2:mem:testdb", "sa", ""
                );
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create connection", e);
            }
        });

    public static Connection getConnection() {
        return connectionHolder.get();  // Returns THIS thread's connection
    }

    // CRITICAL: must clean up to prevent memory leaks in thread pools
    // Thread pool threads are reused - ThreadLocal persists between tasks!
    public static void closeConnection() {
        Connection conn = connectionHolder.get();
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                // log error
            } finally {
                connectionHolder.remove();  // Remove from this thread's map
            }
        }
    }
}

// Real-world: Request context in a web application
// Each HTTP request is handled by one thread - store request data in ThreadLocal
public class RequestContext {

    private static final ThreadLocal<RequestContext> current = new ThreadLocal<>();

    private final String userId;
    private final String requestId;
    private final String tenantId;
    private final Instant startTime;

    private RequestContext(String userId, String requestId, String tenantId) {
        this.userId = userId;
        this.requestId = requestId;
        this.tenantId = tenantId;
        this.startTime = Instant.now();
    }

    // Called at the start of each HTTP request (e.g., in a Filter)
    public static void initialize(String userId, String requestId, String tenantId) {
        current.set(new RequestContext(userId, requestId, tenantId));
    }

    // Called anywhere in the call stack - no need to pass context around
    public static RequestContext get() {
        RequestContext ctx = current.get();
        if (ctx == null) {
            throw new IllegalStateException("No request context - called outside request?");
        }
        return ctx;
    }

    // Called at the end of each request (in a finally block in the Filter)
    public static void clear() {
        current.remove();  // Prevent memory leaks in thread pools
    }

    public String getUserId() { return userId; }
    public String getRequestId() { return requestId; }
    public String getTenantId() { return tenantId; }
    
    public long getElapsedMs() {
        return Instant.now().toEpochMilli() - startTime.toEpochMilli();
    }
}

// Service class - notice no need to pass userId/requestId in every method!
public class PaymentService {
    
    public void processPayment(BigDecimal amount) {
        // Get user from thread-local context - no parameter passing needed
        String userId = RequestContext.get().getUserId();
        String requestId = RequestContext.get().getRequestId();
        
        System.out.printf("[%s] Processing payment of %s for user %s%n",
            requestId, amount, userId);
        
        // DB connection also comes from ThreadLocal - no need to pass it around
        Connection conn = DatabaseConnectionManager.getConnection();
        // ... use connection
    }
}

// Simulating a web server handling multiple requests
public class ThreadLocalDemo {
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(3);
    
    public static void simulateRequest(String userId, String requestId) {
        threadPool.submit(() -> {
            try {
                // Simulates what a web framework Filter does
                RequestContext.initialize(userId, requestId, "tenant-A");
                
                // Deep in the call stack - no parameters being passed!
                PaymentService service = new PaymentService();
                service.processPayment(new BigDecimal("100.00"));
                
                System.out.printf("[%s] Request took %dms%n",
                    RequestContext.get().getRequestId(),
                    RequestContext.get().getElapsedMs());
                    
            } finally {
                // ALWAYS clean up in finally - thread goes back to pool
                RequestContext.clear();
                DatabaseConnectionManager.closeConnection();
            }
        });
    }
    
    public static void main(String[] args) throws InterruptedException {
        simulateRequest("user-1", "req-abc");
        simulateRequest("user-2", "req-def");
        simulateRequest("user-3", "req-ghi");
        
        threadPool.shutdown();
        threadPool.awaitTermination(5, TimeUnit.SECONDS);
    }
}
```

---

## 11.3 Safe Publication

**Real-world example: Publishing a configuration object loaded at startup**

"Publication" means making an object reference available to other threads. Even if the object itself is thread-safe, if you publish it *unsafely*, other threads may see a **partially constructed object**.

### Bad Publication Patterns

```java
class UnsafePublicationExamples {

    // BAD 1: Public field, no synchronization
    // Another thread might see holder.config as non-null 
    // but with uninitialized fields inside!
    public static AppConfig config;  // NOT safe!

    // BAD 2: Lazy init without synchronization
    private AppConfig lazyConfig;
    
    public AppConfig getConfig() {
        if (lazyConfig == null) {               // Thread A checks: null
            lazyConfig = new AppConfig(...);    // Thread A constructs
        }
        return lazyConfig;                      // Thread B might see partial object!
    }
}
```

### Good Publication Patterns

```java
// Pattern 1: Volatile field
// volatile guarantees: writes are visible to all threads, prevents reordering
class VolatilePublication {
    private volatile AppConfig config;  // volatile ensures visibility
    
    public void publishConfig(AppConfig cfg) {
        this.config = cfg;  // Write happens-before any subsequent read
    }
    
    public AppConfig getConfig() {
        return config;  // Always sees the most recent write
    }
}

// Pattern 2: static final - the simplest and safest
// JVM guarantees static initializers complete before any thread accesses the class
class StaticFinalPublication {
    // Initialized once when class loads, visible to all threads
    public static final AppConfig CONFIG = new AppConfig(
        "jdbc:postgresql://prod-db:5432/payments",
        20,
        Map.of("dark-mode", "true", "new-checkout", "false")
    );
}

// Pattern 3: Synchronized publication
class SynchronizedPublication {
    private AppConfig config;
    
    public synchronized void setConfig(AppConfig cfg) {
        this.config = cfg;
    }
    
    public synchronized AppConfig getConfig() {
        return config;
    }
}

// Pattern 4: AtomicReference - lock-free + safe
class AtomicPublication {
    private final AtomicReference<AppConfig> configRef = new AtomicReference<>();
    
    public void publishConfig(AppConfig cfg) {
        configRef.set(cfg);
    }
    
    public AppConfig getConfig() {
        return configRef.get();
    }
    
    // Bonus: atomic compare-and-swap for config hot reload
    public boolean reloadConfig(AppConfig expected, AppConfig newConfig) {
        return configRef.compareAndSet(expected, newConfig);
    }
}

// Pattern 5: Concurrent collection for publishing multiple objects
class ConcurrentCollectionPublication {
    // ConcurrentHashMap's puts are safely visible to subsequent gets
    private final ConcurrentHashMap<String, AppConfig> tenantConfigs 
        = new ConcurrentHashMap<>();
    
    public void publishTenantConfig(String tenantId, AppConfig config) {
        tenantConfigs.put(tenantId, config);  // Safe publication
    }
    
    public AppConfig getTenantConfig(String tenantId) {
        return tenantConfigs.get(tenantId);  // Safely sees published config
    }
}
```

### Safe Publication — Quick Reference

| Method | Thread-Safe? | Notes |
|--------|-------------|-------|
| `public static field` | ❌ No | Other threads may see partial object |
| `volatile field` | ✅ Yes | Prevents reordering, ensures visibility |
| `static final field` | ✅ Yes | Safest option, set once at class load |
| `synchronized` getter/setter | ✅ Yes | Works, but adds lock overhead |
| `AtomicReference` | ✅ Yes | Lock-free, supports CAS operations |
| `ConcurrentHashMap.put` | ✅ Yes | Safe publication of values |

---

## 11.4 Double-Checked Locking

**Real-world example: Lazy-loading an expensive service (e.g., ML model, connection pool)**

### Why This Pattern Exists

- We don't want to pay initialization cost until needed (lazy)
- We don't want to synchronize every call (performance)
- But we need thread safety

### The Broken Version (pre-Java 5)

```java
// BAD: Broken without volatile
class BrokenDoubleCheckedLocking {
    private static ExpensiveService instance;  // NOT volatile - broken!
    
    public static ExpensiveService getInstance() {
        if (instance == null) {                     // Check 1 (no lock)
            synchronized (BrokenDoubleCheckedLocking.class) {
                if (instance == null) {             // Check 2 (with lock)
                    instance = new ExpensiveService();  // PROBLEM!
                    // CPU can reorder: 
                    // 1. Allocate memory
                    // 2. Assign reference to 'instance'  <- another thread sees non-null!
                    // 3. Call constructor               <- but object not initialized yet!
                }
            }
        }
        return instance;  // Might return partially constructed object!
    }
}
```

### Fixed with Volatile (Java 5+)

`volatile` prevents the reordering of the memory allocation and constructor call steps.

```java
class DoubleCheckedLocking {
    private static volatile ExpensiveService instance;  // volatile is the key!
    
    private DoubleCheckedLocking() {}
    
    public static ExpensiveService getInstance() {
        if (instance == null) {                     // Check 1: fast path, no lock
            synchronized (DoubleCheckedLocking.class) {
                if (instance == null) {             // Check 2: inside lock
                    instance = new ExpensiveService();  // Safe with volatile
                }
            }
        }
        return instance;
    }
}
```

### Better: Initialization-on-Demand Holder (preferred in modern Java)

No `volatile`, no synchronization on reads — relies on JVM class loading guarantees.

```java
class ServiceHolder {
    
    private ServiceHolder() {}
    
    // Inner class is NOT loaded until first reference to INSTANCE
    private static class Holder {
        // Class loading is inherently thread-safe in the JVM
        static final ExpensiveService INSTANCE = new ExpensiveService();
    }
    
    public static ExpensiveService getInstance() {
        return Holder.INSTANCE;  // No synchronization needed - class loading does it
    }
}
```

### Real-World: Lazy-Loading an ML Model

```java
public class FraudDetectionModel {
    
    private volatile static FraudDetectionModel instance;
    
    private final Object model;  // Imagine this is a TensorFlow model
    
    private FraudDetectionModel() {
        System.out.println("Loading fraud detection model from disk... (expensive!)");
        // Simulate loading a large model - takes 5 seconds
        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        this.model = new Object(); // Placeholder for actual model
        System.out.println("Model loaded successfully");
    }
    
    public static FraudDetectionModel getInstance() {
        if (instance == null) {                         // Fast path - no locking
            synchronized (FraudDetectionModel.class) {
                if (instance == null) {                 // Slow path - with locking
                    instance = new FraudDetectionModel();
                }
            }
        }
        return instance;
    }
    
    public boolean isFraudulent(Transaction transaction) {
        return transaction.getAmount().compareTo(new BigDecimal("10000")) > 0;
    }
    
    // Demo: 10 threads all try to get the instance simultaneously
    // Model should only be loaded ONCE
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        
        for (int i = 0; i < 10; i++) {
            pool.submit(() -> {
                FraudDetectionModel model = FraudDetectionModel.getInstance();
                System.out.println(Thread.currentThread().getName() 
                    + " got model: " + model);
            });
        }
        
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
    }
}
```

### DCL Approaches — Comparison

| Approach | Reads | Writes | Complexity |
|----------|-------|--------|------------|
| No sync (broken) | Fast | Fast | Low — but broken! |
| Full sync every call | Slow (lock always) | Safe | Medium |
| DCL + volatile | Fast (no lock) | Safe | Medium |
| Holder class | Fastest (no sync at all) | Safe at class load | Low — **preferred** |

---

## 11.5 Instance Confinement

**Real-world example: A bank account with monitored, thread-safe access**

The idea: even if the internal state is mutable, you **encapsulate it entirely** and control all access through synchronized methods. The mutable state never "escapes" the object.

```java
public class BankAccount {

    private final String accountId;
    private BigDecimal balance;
    private final List<String> transactionLog; // mutable - must be confined
    
    public BankAccount(String accountId, BigDecimal initialBalance) {
        this.accountId = accountId;
        this.balance = initialBalance;
        this.transactionLog = new ArrayList<>();  // ArrayList: NOT thread-safe on its own!
    }

    // Synchronized: only one thread can execute this at a time
    public synchronized void deposit(BigDecimal amount) {
        validatePositive(amount);
        balance = balance.add(amount);
        transactionLog.add(String.format("DEPOSIT: +%s | Balance: %s | Time: %s",
            amount, balance, Instant.now()));
        notifyAll();  // wake up threads waiting for funds
    }

    public synchronized void withdraw(BigDecimal amount) throws InsufficientFundsException {
        validatePositive(amount);
        
        while (balance.compareTo(amount) < 0) {
            try {
                System.out.printf("[%s] Insufficient funds. Waiting for deposit...%n",
                    Thread.currentThread().getName());
                wait(5000);  // Wait up to 5 seconds for a deposit
                // Check again after waking up (spurious wakeup protection)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for funds", e);
            }
        }
        
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                "Required: " + amount + ", Available: " + balance);
        }
        
        balance = balance.subtract(amount);
        transactionLog.add(String.format("WITHDRAWAL: -%s | Balance: %s | Time: %s",
            amount, balance, Instant.now()));
    }

    public synchronized BigDecimal getBalance() {
        return balance;  // Simple read still needs sync for visibility
    }

    // Returns a COPY - never expose the internal list!
    public synchronized List<String> getTransactionHistory() {
        return new ArrayList<>(transactionLog);  // Defensive copy
    }

    // Transfer between accounts: classic two-lock problem
    // WRONG: synchronized(this) then synchronized(other) => potential deadlock!
    // CORRECT: Consistent lock ordering by accountId prevents deadlock
    public static void transfer(BankAccount from, BankAccount to, BigDecimal amount) 
            throws InsufficientFundsException {
        
        BankAccount first = from.accountId.compareTo(to.accountId) < 0 ? from : to;
        BankAccount second = first == from ? to : from;
        
        synchronized (first) {
            synchronized (second) {
                from.withdraw(amount);
                to.deposit(amount);
            }
        }
    }

    private void validatePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
    }

    @Override
    public String toString() {
        return String.format("BankAccount[id=%s, balance=%s]", accountId, balance);
    }
}

class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String msg) { super(msg); }
}

// Demo: concurrent operations on a bank account
public class InstanceConfinementDemo {
    
    public static void main(String[] args) throws InterruptedException {
        BankAccount account = new BankAccount("ACC-001", new BigDecimal("1000.00"));

        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(1);

        // 3 threads try to withdraw simultaneously
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    latch.await();  // All start at same time
                    System.out.printf("[Thread-%d] Withdrawing 400...%n", idx);
                    account.withdraw(new BigDecimal("400"));
                    System.out.printf("[Thread-%d] Withdrawal success! Balance: %s%n",
                        idx, account.getBalance());
                } catch (InsufficientFundsException e) {
                    System.out.printf("[Thread-%d] Failed: %s%n", idx, e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // 1 thread deposits after a delay to unblock waiting threads
        pool.submit(() -> {
            try {
                latch.await();
                Thread.sleep(1000);  // Let withdrawals start waiting
                System.out.println("Depositing 500 to help waiting threads...");
                account.deposit(new BigDecimal("500"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        latch.countDown();  // Release all threads simultaneously
        
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);
        
        System.out.println("\nFinal balance: " + account.getBalance());
        System.out.println("\nTransaction History:");
        account.getTransactionHistory().forEach(System.out::println);
    }
}
```

### Key Rules for Instance Confinement

- Always `synchronized` on every method that accesses shared mutable state — including simple reads
- **Never let internal state escape**: return defensive copies of collections, not the real ones
- Use `wait()` inside a `while` loop, never `if` — protects against spurious wakeups
- When locking multiple objects, always acquire locks in a **consistent order** to prevent deadlock

---

## Summary: When to Use Which Pattern

| Pattern | Use When | Real-World Example |
|---------|----------|--------------------|
| **Immutability** | Object shouldn't change after creation | Transaction records, config snapshots, Money values |
| **Stack Confinement** | Temporary objects used in one method only | `StringBuilder` for building responses |
| **ThreadLocal** | Per-thread state needed across a deep call stack | DB connections, HTTP request context, user sessions |
| **Safe Publication** | Sharing a newly created object with other threads | Publishing a loaded config, sharing a service reference |
| **Double-Checked Locking** | Expensive lazy singleton initialization | ML model, connection pool, expensive resource |
| **Instance Confinement** | Mutable state that needs tightly controlled access | Bank accounts, shopping carts, game state |

### The Golden Rule

> **The less shared mutable state, the fewer bugs.**

Prefer in this order:
1. **Immutability** — no sharing problem if nothing can change
2. **Confinement** — only one thread can ever reach the object
3. **Controlled sharing** — synchronize carefully when you must share mutable state

---

*Part of the Complete Java Multithreading & Concurrency Mastery Roadmap — Phase 11 of 16*