# Complete Java Multithreading & Concurrency Mastery Guide

## Table of Contents

1. [Introduction](#introduction)
2. [Complete Roadmap Overview](#complete-roadmap-overview)
3. [Phase 1: Foundational Concepts](#phase-1-foundational-concepts)
   - [1.1 Understanding Processes vs Threads](#11-understanding-processes-vs-threads)
   - [1.2 Why Concurrency Matters](#12-why-concurrency-matters)
   - [1.3 Thread Basics](#13-thread-basics)
   - [1.4 Daemon vs User Threads](#14-daemon-vs-user-threads)
4. [Practice Exercises with Solutions](#practice-exercises-with-solutions)
   - [Exercise 1: Multi-File Processor](#exercise-1-multi-file-processor)
   - [Exercise 2: Multi-Timer Application](#exercise-2-multi-timer-application)
   - [Exercise 3: Web Scraper with Cancellation](#exercise-3-web-scraper-with-cancellation)
   - [Exercise 4: Background Task Manager](#exercise-4-background-task-manager)
5. [Bonus: PostgreSQL Column Type Changes](#bonus-postgresql-column-type-changes)
6. [Next Steps](#next-steps)

---

## Introduction

This comprehensive guide covers Java multithreading and concurrency from absolute basics to advanced concepts. The roadmap is structured to build knowledge progressively over 6-8 months of dedicated study.

**Learning Approach:**
- Real-world examples for every concept
- Production-quality code with best practices
- Hands-on exercises with complete solutions
- Progressive difficulty building from foundations

---

## Complete Roadmap Overview

### Phase 1: Foundational Concepts (Week 1-2) ✅ COMPLETED
- Processes vs Threads
- Why Concurrency Matters
- Thread Basics (creation, lifecycle, operations)
- Daemon vs User Threads

### Phase 2: Core Threading Mechanisms (Week 3-4)
- Thread Synchronization Fundamentals
- Race Conditions
- synchronized keyword
- wait(), notify(), notifyAll()
- Thread Coordination with join()

### Phase 3: Memory Model & Visibility (Week 5)
- Java Memory Model (JMM)
- Happens-before relationship
- volatile keyword
- Safe Publication patterns

### Phase 4: Java Concurrency Utilities (Week 6-8)
- Executor Framework
- Thread Pools
- Callable and Future
- ScheduledExecutorService

### Phase 5: Advanced Synchronizers (Week 9-10)
- CountDownLatch
- CyclicBarrier
- Semaphore
- Phaser

### Phase 6: Locks and Conditions (Week 11-12)
- ReentrantLock
- ReadWriteLock
- StampedLock
- Condition Objects

### Phase 7: Concurrent Collections (Week 13-14)
- ConcurrentHashMap
- BlockingQueue implementations
- CopyOnWriteArrayList
- Lock-free collections

### Phase 8: Atomic Variables (Week 15)
- AtomicInteger, AtomicLong, AtomicReference
- Compare-and-Swap (CAS)
- LongAdder and LongAccumulator

### Phase 9: Fork/Join Framework (Week 16)
- Work-stealing algorithm
- RecursiveTask and RecursiveAction
- ForkJoinPool

### Phase 10: CompletableFuture (Week 17-18)
- Asynchronous programming
- Chaining and composition
- Error handling
- Combining futures

### Phase 11: Thread Safety Patterns (Week 19-20)
- Immutability
- Thread Confinement
- Safe Publication
- Instance Confinement

### Phase 12: Performance (Week 21-22)
- Amdahl's Law
- Lock contention
- False sharing
- Profiling tools

### Phase 13: Advanced Topics (Week 23-25)
- Parallel Streams
- Virtual Threads (Java 19+)
- Structured Concurrency

### Phase 14: Design Patterns (Week 26-27)
- Producer-Consumer
- Thread Pool Pattern
- Active Object
- Monitor Object

### Phase 15: Real-World Projects (Week 28-30)
- Concurrent Web Crawler
- Thread-Safe Cache
- High-Performance Message Queue

### Phase 16: Testing (Week 31-32)
- Testing concurrent code
- Race condition detection
- Tools and libraries

---

## Phase 1: Foundational Concepts

### 1.1 Understanding Processes vs Threads

#### Real-World Analogy

**Process** = Restaurant Kitchen
- Complete isolation
- Own space, equipment, supplies (memory)
- Heavy resource consumption

**Thread** = Chefs in the same kitchen
- Share space, ingredients, equipment
- Work on different dishes simultaneously
- Lightweight - faster to create

#### Key Differences

| Aspect | Process | Thread |
|--------|---------|--------|
| Memory | Separate memory space | Shared memory within process |
| Resource Cost | Heavy (entire JVM startup) | Light (~1MB stack space) |
| Communication | IPC (Inter-Process Communication) | Direct through shared memory |
| Crash Impact | Isolated - doesn't affect others | Can crash entire process |
| Creation Speed | Slow | Fast |

#### Code Example: Process vs Thread Demo

```java
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * Demonstrates the difference between processes and threads.
 */
public class ProcessThreadDemo {
    
    // Shared memory between threads (NOT shared between processes)
    private static int sharedCounter = 0;
    
    public static void main(String[] args) {
        displayProcessInfo();
        demonstrateThreads();
    }
    
    private static void displayProcessInfo() {
        String processName = ManagementFactory.getRuntimeMXBean().getName();
        long processId = Long.parseLong(processName.split("@")[0]);
        
        System.out.println("Process ID: " + processId);
        System.out.println("Available Processors: " + 
                         Runtime.getRuntime().availableProcessors());
    }
    
    private static void demonstrateThreads() {
        Thread thread1 = new Thread(() -> incrementCounter("Thread-1"), "Worker-1");
        Thread thread2 = new Thread(() -> incrementCounter("Thread-2"), "Worker-2");
        Thread thread3 = new Thread(() -> incrementCounter("Thread-3"), "Worker-3");
        
        thread1.start();
        thread2.start();
        thread3.start();
        
        try {
            thread1.join();
            thread2.join();
            thread3.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Final shared counter: " + sharedCounter);
        System.out.println("All threads modified the SAME variable!");
    }
    
    private static void incrementCounter(String threadName) {
        for (int i = 0; i < 5; i++) {
            sharedCounter++;
            System.out.println(threadName + " incremented to: " + sharedCounter);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
```

**Key Takeaways:**
- Threads share heap memory (objects, static variables)
- Threads have separate stacks (local variables)
- Processes are completely isolated
- Use threads when tasks need to share data
- Use processes for isolation (microservices, sandboxing)

---

### 1.2 Why Concurrency Matters

#### Real-World Example: Restaurant Order Processing

**Scenario:** A restaurant receives 5 orders. Each order takes:
- Validation: 2 seconds
- Cooking: 5 seconds
- Packaging: 1 second
- **Total per order: 8 seconds**

**Sequential Processing (No Concurrency):**
- Process one order at a time
- Total time: 5 orders × 8 seconds = **40 seconds**

**Concurrent Processing (With Threads):**
- Process all orders simultaneously
- Total time: **8 seconds** (all run in parallel)
- **Speedup: 5x faster!**

#### Code Example: Why Concurrency Matters

```java
public class WhyConcurrencyMatters {
    
    private static final int NUMBER_OF_ORDERS = 5;
    
    public static void main(String[] args) {
        // Sequential Processing
        long startTime = System.currentTimeMillis();
        for (int i = 1; i <= NUMBER_OF_ORDERS; i++) {
            processOrder(i);
        }
        long sequentialTime = System.currentTimeMillis() - startTime;
        System.out.println("Sequential: " + sequentialTime + " ms");
        
        // Concurrent Processing
        startTime = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<>();
        
        for (int i = 1; i <= NUMBER_OF_ORDERS; i++) {
            final int orderId = i;
            Thread thread = new Thread(() -> processOrder(orderId));
            threads.add(thread);
            thread.start();
        }
        
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long concurrentTime = System.currentTimeMillis() - startTime;
        System.out.println("Concurrent: " + concurrentTime + " ms");
        System.out.println("Speedup: " + (sequentialTime / (double) concurrentTime) + "x");
    }
    
    private static void processOrder(int orderId) {
        System.out.println("Order #" + orderId + " - Validating...");
        sleep(2000);
        
        System.out.println("Order #" + orderId + " - Cooking...");
        sleep(5000);
        
        System.out.println("Order #" + orderId + " - Packaging...");
        sleep(1000);
        
        System.out.println("Order #" + orderId + " - COMPLETED!");
    }
    
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

#### When Concurrency Helps

✅ **Use Concurrency When:**
- I/O-bound tasks (network, disk, database)
- Independent tasks that can run in parallel
- Multi-core processors available
- Tasks would otherwise block the main thread

❌ **Concurrency Adds Complexity When:**
- Tasks must execute in strict order
- Heavy shared state requiring synchronization
- Tasks are CPU-bound on single core
- Thread overhead exceeds benefits

#### Real-World Applications

1. **Web Servers:** Handle thousands of requests simultaneously
2. **Databases:** Execute multiple queries in parallel
3. **File Processing:** Process multiple files at once
4. **GUI Applications:** Keep UI responsive during long operations
5. **Data Pipelines:** Pipeline stages run concurrently

---

### 1.3 Thread Basics

#### Creating Threads - Two Methods

**Method 1: Extending Thread Class**
```java
class FileDownloaderThread extends Thread {
    private final String fileName;
    
    public FileDownloaderThread(String fileName) {
        super("Downloader-" + fileName);
        this.fileName = fileName;
    }
    
    @Override
    public void run() {
        System.out.println("Downloading " + fileName);
        // Download logic here
    }
}

// Usage
FileDownloaderThread downloader = new FileDownloaderThread("file.pdf");
downloader.start(); // Creates new thread and calls run()
```

**Method 2: Implementing Runnable (PREFERRED)**
```java
class FileDownloaderRunnable implements Runnable {
    private final String fileName;
    
    public FileDownloaderRunnable(String fileName) {
        this.fileName = fileName;
    }
    
    @Override
    public void run() {
        System.out.println("Downloading " + fileName);
        // Download logic here
    }
}

// Usage
Runnable task = new FileDownloaderRunnable("file.pdf");
Thread thread = new Thread(task, "Downloader");
thread.start();
```

**Why Runnable is Preferred:**
1. Can extend other classes (Java single inheritance)
2. Better separation of concerns (task vs thread)
3. Can share same Runnable with multiple threads
4. Works better with thread pools (Phase 4)

#### CRITICAL: start() vs run()

```java
// ❌ WRONG - Calls run() in CURRENT thread (no concurrency!)
Thread thread = new Thread(() -> {
    System.out.println("Running in: " + Thread.currentThread().getName());
});
thread.run(); // Prints: "Running in: main"

// ✓ CORRECT - Creates NEW thread
Thread thread = new Thread(() -> {
    System.out.println("Running in: " + Thread.currentThread().getName());
});
thread.start(); // Prints: "Running in: Thread-0"
```

**Always use start(), NEVER call run() directly!**

#### Thread Lifecycle States

```
NEW → RUNNABLE → RUNNING → TIMED_WAITING/WAITING/BLOCKED → TERMINATED
```

| State | Description |
|-------|-------------|
| **NEW** | Thread created but not started |
| **RUNNABLE** | Thread is executing or ready to execute |
| **RUNNING** | Thread is actively executing |
| **TIMED_WAITING** | Thread sleeping for specific duration |
| **WAITING** | Thread waiting indefinitely |
| **BLOCKED** | Thread waiting for monitor lock |
| **TERMINATED** | Thread has completed execution |

#### Thread Operations

**sleep() - Pause Thread**
```java
Thread.sleep(1000); // Sleep for 1 second
// - Throws InterruptedException
// - Does NOT release locks
// - Can be interrupted
```

**join() - Wait for Thread**
```java
Thread thread = new Thread(() -> {
    // Long running task
});
thread.start();
thread.join(); // Wait for thread to finish
// or
thread.join(5000); // Wait max 5 seconds
```

**interrupt() - Request Cancellation**
```java
Thread thread = new Thread(() -> {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            // Do work
            Thread.sleep(1000);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Restore interrupt status
        System.out.println("Thread interrupted");
    }
});

thread.start();
thread.interrupt(); // Request interruption
```

#### Complete Example: Thread Lifecycle

```java
public class ThreadLifecycleDemo {
    public static void main(String[] args) {
        Thread thread = new Thread(() -> {
            System.out.println("State inside run(): " + 
                             Thread.currentThread().getState());
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Lifecycle-Demo");
        
        // NEW
        System.out.println("1. NEW: " + thread.getState());
        
        thread.start();
        // RUNNABLE
        System.out.println("2. RUNNABLE: " + thread.getState());
        
        try {
            Thread.sleep(500);
            // TIMED_WAITING
            System.out.println("3. TIMED_WAITING: " + thread.getState());
            
            thread.join();
            // TERMINATED
            System.out.println("4. TERMINATED: " + thread.getState());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

#### Best Practices

✅ **DO:**
- Prefer Runnable over extending Thread
- Give threads meaningful names
- Handle InterruptedException properly
- Check interrupted flag in long-running loops
- Use join() for coordination

❌ **DON'T:**
- Call run() directly - use start()
- Ignore InterruptedException
- Catch exceptions without handling
- Create threads without naming them

---

### 1.4 Daemon vs User Threads

#### Understanding the Difference

**User Threads (Default):**
- JVM waits for ALL user threads to complete before exiting
- Handle critical business logic
- Must complete their work

**Daemon Threads:**
- Background/support threads
- Automatically terminated when all user threads finish
- JVM doesn't wait for them
- Provide services to user threads

#### Real-World Analogy

Think of a restaurant:

**User Threads** = Cooks preparing customer orders
- Restaurant stays open until all orders are completed
- Critical to customer satisfaction

**Daemon Threads** = Cleaning staff, maintenance
- Work continuously in background
- Sent home when restaurant closes
- Will resume work next day

#### Code Example: Daemon vs User Threads

```java
public class DaemonUserThreadsDemo {
    
    public static void main(String[] args) {
        // User thread (default)
        Thread userThread = new Thread(() -> {
            for (int i = 1; i <= 5; i++) {
                System.out.println("User thread working: " + i);
                sleep(1000);
            }
            System.out.println("User thread finished");
        }, "User-Worker");
        
        // Daemon thread
        Thread daemonThread = new Thread(() -> {
            try {
                while (true) { // Infinite loop
                    System.out.println("Daemon monitoring...");
                    sleep(800);
                }
            } catch (Exception e) {
                System.out.println("Daemon interrupted");
            }
        }, "Daemon-Monitor");
        
        // CRITICAL: Set daemon BEFORE starting
        daemonThread.setDaemon(true);
        
        System.out.println("User thread is daemon: " + userThread.isDaemon());
        System.out.println("Daemon thread is daemon: " + daemonThread.isDaemon());
        
        userThread.start();
        daemonThread.start();
        
        try {
            userThread.join(); // Wait only for user thread
            // Note: We don't join() daemon thread
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Main ending - daemon will be killed!");
    }
    
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

#### When to Use Each

**Use USER Threads When:**
✅ Work MUST complete
✅ Data consistency is critical
✅ Thread manages resources needing cleanup
✅ Failure would cause data loss

**Examples:**
- Database save operations
- Payment processing
- File uploads
- Email sending
- API calls

**Use DAEMON Threads When:**
✅ Work is non-critical
✅ Thread provides support services
✅ OK to terminate abruptly
✅ Thread can be recreated on next run

**Examples:**
- Garbage collector
- Logging threads
- Monitoring/health checks
- Cache cleanup
- Session timeout checkers
- Connection pool maintenance

#### Critical Rules

1. **Set daemon status BEFORE calling start()**
```java
thread.setDaemon(true);  // Must be before start()
thread.start();
```

2. **Daemon threads inherit status from parent**
- Thread created by daemon = daemon by default
- Thread created by user thread = user thread by default

3. **Cannot change after thread starts**
```java
thread.start();
thread.setDaemon(true);  // IllegalThreadStateException!
```

4. **Main thread is a user thread**
- Even if main() exits, JVM waits for other user threads

#### Common Pitfalls

❌ **WRONG: Making critical threads daemon**
```java
Thread saveToDatabase = new Thread(savingTask);
saveToDatabase.setDaemon(true);  // BAD! Data might not be saved
```

❌ **WRONG: Setting daemon after start**
```java
thread.start();
thread.setDaemon(true);  // IllegalThreadStateException!
```

❌ **WRONG: Expecting cleanup in daemon**
```java
// Daemon might be killed before finally block executes
```

✓ **CORRECT: Use shutdown hooks for cleanup**
```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    // Cleanup code here
}));
```

#### Framework Examples

**Tomcat:**
- Request handlers: USER threads
- Session cleanup: DAEMON threads

**Database Connection Pools:**
- Connection serving: USER threads
- Idle connection cleanup: DAEMON threads

**Schedulers:**
- Task execution: USER threads
- Schedule maintenance: DAEMON threads

---

## Practice Exercises with Solutions

### Exercise 1: Multi-File Processor

**Goal:** Create a program that processes multiple text files concurrently, counting words and reporting statistics.

#### Key Features
- Concurrent file processing (one thread per file)
- Word, line, and character counting
- Processing time tracking
- Thread-safe statistics aggregation
- Proper resource cleanup

#### Solution Architecture

```java
public class MultiFileProcessor {
    // Thread-safe counter
    private static final AtomicInteger processedCount = new AtomicInteger(0);
    
    public static void main(String[] args) {
        // 1. Create sample files
        List<String> filePaths = createSampleFiles();
        
        // 2. Process concurrently
        List<FileStatistics> results = processFilesConcurrently(filePaths);
        
        // 3. Display results
        displayResults(results);
        
        // 4. Cleanup
        cleanupFiles(filePaths);
    }
}
```

#### FileProcessor - Runnable Implementation

```java
class FileProcessor implements Runnable {
    private final String filePath;
    private volatile FileStatistics statistics;
    
    @Override
    public void run() {
        try {
            FileStatistics stats = analyzeFile(filePath);
            this.statistics = stats;
        } catch (IOException e) {
            // Handle error
        }
    }
    
    private FileStatistics analyzeFile(String filePath) throws IOException {
        int lineCount = 0;
        int wordCount = 0;
        int characterCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                characterCount += line.length();
                
                String[] words = line.trim().split("\\s+");
                if (!line.trim().isEmpty()) {
                    wordCount += words.length;
                }
            }
        }
        
        return new FileStatistics(filePath, lineCount, wordCount, characterCount);
    }
}
```

#### Key Concepts Demonstrated

1. **Concurrent File Processing**
   - Each file in separate thread
   - Parallel execution on multi-core systems

2. **Thread Creation Best Practices**
   - Runnable interface (not extending Thread)
   - Meaningful thread names
   - Separation of task and thread

3. **Thread Coordination**
   - join() to wait for all threads
   - Collecting results after completion

4. **Resource Management**
   - try-with-resources for file handling
   - Proper cleanup

5. **Thread Safety**
   - Immutable result objects
   - volatile for visibility
   - AtomicInteger for counters

---

### Exercise 2: Multi-Timer Application

**Goal:** Build a timer app where each timer runs in its own thread, with a daemon thread displaying all active timers.

#### Key Features
- Multiple independent timers
- Start/Stop/Reset individual timers
- Daemon thread for real-time display
- Interactive command-line interface
- Graceful shutdown

#### Solution Architecture

```java
public class MultiTimerApp {
    private static final List<Timer> timers = new CopyOnWriteArrayList<>();
    private static Thread displayDaemon;
    
    public static void main(String[] args) {
        // Start display daemon
        startDisplayDaemon();
        
        // Show instructions
        showInstructions();
        
        // Command loop
        runCommandLoop();
        
        // Cleanup
        shutdown();
    }
}
```

#### Timer Class - USER Thread

```java
class Timer {
    private final int id;
    private final String name;
    private Thread timerThread;
    
    private volatile long elapsedSeconds = 0;
    private volatile boolean running = false;
    private volatile boolean shouldStop = false;
    
    public void start() {
        running = true;
        shouldStop = false;
        
        // USER THREAD - critical counting logic
        timerThread = new Thread(() -> {
            try {
                while (!shouldStop) {
                    Thread.sleep(1000);
                    elapsedSeconds++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running = false;
            }
        }, "Timer-" + id);
        
        timerThread.start(); // User thread (default)
    }
    
    public void stop() {
        shouldStop = true;
        if (timerThread != null) {
            try {
                timerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

#### Display Daemon

```java
private static void startDisplayDaemon() {
    displayDaemon = new Thread(() -> {
        try {
            while (true) {
                displayAllTimers();
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            // Daemon interrupted
        }
    }, "Display-Daemon");
    
    // CRITICAL: Set as daemon before starting
    displayDaemon.setDaemon(true);
    displayDaemon.start();
}
```

#### Why This Design?

**User Threads for Timers:**
- Timing data is critical
- Must complete current second
- No data loss on shutdown

**Daemon Thread for Display:**
- Just shows current state
- Non-critical
- Infinite loop with no natural exit
- Safe to kill on shutdown

#### Key Concepts Demonstrated

1. **Daemon vs User Thread Selection**
   - Critical logic = USER threads
   - Display/monitoring = DAEMON threads

2. **Thread Lifecycle Management**
   - On-demand thread creation
   - Graceful stopping with flags
   - join() for coordination

3. **Thread-Safe Communication**
   - volatile flags for visibility
   - CopyOnWriteArrayList for collection

4. **Independent Thread Control**
   - Each timer operates independently
   - No shared state between timers

---

### Exercise 3: Web Scraper with Cancellation

**Goal:** Build a concurrent web scraper that can download multiple URLs with proper cancellation support.

#### Key Features
- Concurrent URL downloading
- Real-time progress tracking
- Graceful cancellation (press ENTER)
- Timeout handling
- Thread interruption

#### Solution Architecture

```java
public class WebScraperApp {
    private static final AtomicInteger completedCount = new AtomicInteger(0);
    private static final AtomicInteger failedCount = new AtomicInteger(0);
    private static final AtomicInteger cancelledCount = new AtomicInteger(0);
    
    private static volatile boolean cancelled = false;
    
    public static void main(String[] args) {
        List<String> urls = createSampleUrls();
        List<DownloadResult> results = startConcurrentDownloads(urls);
        displayFinalResults(results);
    }
}
```

#### Download Task with Dual Cancellation

```java
class DownloadTask implements Runnable {
    private final String url;
    private volatile DownloadResult result;
    
    @Override
    public void run() {
        try {
            // Check BOTH flag AND interrupt status
            if (WebScraperApp.cancelled || Thread.currentThread().isInterrupted()) {
                result = new DownloadResult(DownloadStatus.CANCELLED);
                return;
            }
            
            // Simulate download with cancellation checks
            long bytes = simulateDownload(url);
            result = new DownloadResult(DownloadStatus.COMPLETED, bytes);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore status
            result = new DownloadResult(DownloadStatus.CANCELLED);
            
        } catch (TimeoutException e) {
            result = new DownloadResult(DownloadStatus.TIMEOUT);
        }
    }
    
    private long simulateDownload(String url) 
            throws InterruptedException, TimeoutException {
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < chunks; i++) {
            // Check for cancellation FREQUENTLY
            if (WebScraperApp.cancelled || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Cancelled");
            }
            
            // Check for timeout
            if (System.currentTimeMillis() - startTime > 5000) {
                throw new TimeoutException("Download timeout");
            }
            
            Thread.sleep(100); // Simulate work
        }
        
        return bytesDownloaded;
    }
}
```

#### Cancellation Listener

```java
private static Thread startCancellationListener(List<Thread> downloadThreads) {
    Thread listener = new Thread(() -> {
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine(); // Wait for ENTER
        
        System.out.println("CANCELLATION REQUESTED");
        cancelled = true;
        
        // Interrupt all download threads
        for (Thread thread : downloadThreads) {
            thread.interrupt();
        }
    }, "Cancellation-Listener");
    
    listener.setDaemon(true);
    listener.start();
    return listener;
}
```

#### Key Concepts Demonstrated

1. **Dual Cancellation Mechanism**
   - volatile flag (cooperative)
   - Thread interruption (forceful)
   - Combined approach for reliability

2. **Proper Interruption Handling**
   - Catching InterruptedException
   - Restoring interrupt status
   - Graceful cleanup

3. **Why Both Mechanisms?**
   - Flag: Works when thread is active
   - Interrupt: Wakes up sleeping threads
   - Together: Covers all scenarios

4. **Thread-Safe Statistics**
   - AtomicInteger for counters
   - volatile flags for visibility

#### The Interruption Pattern

```java
try {
    while (!Thread.currentThread().isInterrupted()) {
        // Check flag too
        if (cancelled) break;
        
        // Do work
        Thread.sleep(100);
    }
} catch (InterruptedException e) {
    // CRITICAL: Restore interrupt status
    Thread.currentThread().interrupt();
    // Cleanup and exit
}
```

**Why restore interrupt status?**
- Code up the call stack might need to know
- Thread pools need to detect interruption
- Best practice for "good citizen" code

---

### Exercise 4: Background Task Manager

**Goal:** Create an application with critical user tasks and daemon monitoring services, demonstrating graceful shutdown.

#### Key Features
- User threads for critical business logic
- Daemon threads for monitoring
- Graceful shutdown on Ctrl+C
- Shutdown hooks
- Final statistics reporting

#### Solution Architecture

```java
public class BackgroundTaskManager {
    private static final List<CriticalTask> criticalTasks = new CopyOnWriteArrayList<>();
    private static final List<MonitoringService> monitoringServices = new CopyOnWriteArrayList<>();
    
    private static volatile boolean shutdownRequested = false;
    
    public static void main(String[] args) {
        // Register shutdown hook
        registerShutdownHook();
        
        // Start monitoring services (DAEMON)
        startMonitoringServices();
        
        // Start critical tasks (USER)
        startCriticalTasks();
        
        // Start dashboard (DAEMON)
        startDashboard();
        
        // Wait and shutdown
        simulateApplicationLifecycle();
    }
}
```

#### Critical Task - USER Thread

```java
class CriticalTask {
    private Thread taskThread;
    private volatile TaskStatus status = TaskStatus.PENDING;
    
    public void start() {
        taskThread = new Thread(() -> {
            try {
                status = TaskStatus.RUNNING;
                
                // Simulate work with progress
                for (int i = 0; i < 10; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        status = TaskStatus.CANCELLED;
                        return;
                    }
                    
                    Thread.sleep(500);
                    updateProgress(i * 10);
                }
                
                status = TaskStatus.COMPLETED;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                status = TaskStatus.CANCELLED;
            }
        }, "CriticalTask-" + id);
        
        // USER THREAD (not daemon) - CRITICAL!
        taskThread.setDaemon(false);
        taskThread.start();
    }
}
```

#### Monitoring Service - DAEMON Thread

```java
class MonitoringService {
    private Thread serviceThread;
    private volatile boolean running = false;
    
    public void start() {
        serviceThread = new Thread(() -> {
            running = true;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    checkLogic.run(); // Perform monitoring
                    Thread.sleep(intervalMs);
                }
            } catch (InterruptedException e) {
                // Service interrupted
            } finally {
                running = false;
            }
        }, name);
        
        // DAEMON THREAD - killed on JVM shutdown
        serviceThread.setDaemon(true);
        serviceThread.start();
    }
}
```

#### Shutdown Hook

```java
private static void registerShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("SHUTDOWN HOOK TRIGGERED");
        performGracefulShutdown();
    }, "Shutdown-Hook"));
}

private static void performGracefulShutdown() {
    shutdownRequested = true;
    
    // Step 1: Stop accepting new tasks
    System.out.println("Stopping new task acceptance...");
    
    // Step 2: Wait for critical tasks
    System.out.println("Waiting for critical tasks...");
    for (CriticalTask task : criticalTasks) {
        if (!task.isCompleted()) {
            try {
                task.join(10000); // Wait max 10 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // Step 3: Stop daemon services (automatic)
    System.out.println("Monitoring services will terminate automatically");
    
    // Step 4: Display final statistics
    displayFinalStatistics();
}
```

#### Why This Design Matters

**Scenario: User presses Ctrl+C**

❌ **BAD (all daemon):**
```
User: Ctrl+C
JVM: Kills everything immediately
Database: "Wait, I'm sav—" [KILLED]
Payment: "Processing tra—" [KILLED]
Result: 💥 CORRUPTED DATA
```

✓ **GOOD (this design):**
```
User: Ctrl+C
Shutdown Hook: "Wait for critical tasks..."
Database: "Saving... done! ✓"
Payment: "Processing... complete! ✓"
Monitors: [killed - no problem]
Result: ✅ ALL DATA SAFE
```

#### Key Concepts Demonstrated

1. **Thread Classification**
   - Critical business logic = USER threads
   - Monitoring/display = DAEMON threads

2. **Shutdown Hooks**
   - Triggered on JVM exit
   - Ctrl+C, System.exit(), natural end
   - Used for cleanup and logging

3. **Graceful Shutdown**
   - Wait for USER threads with join()
   - Daemon threads killed automatically
   - Final statistics reporting

4. **Real-World Pattern**
   - Used in Tomcat, Jetty, batch processors
   - Ensures data integrity
   - Proper audit trails

---

## Bonus: PostgreSQL Column Type Changes

### Using pgAdmin GUI

1. **Connect to database** → Schemas → Tables
2. **Right-click table** → Properties
3. **Columns tab** → Select column → Edit
4. **Change Data type** → Save

### Using SQL (Recommended)

#### Basic Syntax
```sql
ALTER TABLE table_name 
ALTER COLUMN column_name TYPE new_data_type;
```

#### Common Examples

**VARCHAR to TEXT:**
```sql
ALTER TABLE users 
ALTER COLUMN bio TYPE TEXT;
```

**Increase VARCHAR length:**
```sql
ALTER TABLE users 
ALTER COLUMN username TYPE VARCHAR(200);
```

**TEXT to INTEGER (requires USING):**
```sql
ALTER TABLE orders 
ALTER COLUMN quantity TYPE INTEGER 
USING quantity::INTEGER;
```

**TEXT to DATE:**
```sql
ALTER TABLE events 
ALTER COLUMN event_date TYPE DATE 
USING event_date::DATE;
```

**TEXT to BOOLEAN with logic:**
```sql
ALTER TABLE users 
ALTER COLUMN is_active TYPE BOOLEAN 
USING CASE 
    WHEN is_active IN ('true', 't', 'yes', '1') THEN TRUE
    WHEN is_active IN ('false', 'f', 'no', '0') THEN FALSE
    ELSE NULL
END;
```

#### Safe Migration Pattern

```sql
-- Step 1: Add new column
ALTER TABLE users 
ADD COLUMN age_new INTEGER;

-- Step 2: Migrate data
UPDATE users 
SET age_new = age::INTEGER 
WHERE age ~ '^[0-9]+$';

-- Step 3: Check for issues
SELECT id, age, age_new 
FROM users 
WHERE age_new IS NULL AND age IS NOT NULL;

-- Step 4: Drop old column
ALTER TABLE users DROP COLUMN age;

-- Step 5: Rename new column
ALTER TABLE users RENAME COLUMN age_new TO age;
```

#### Using Transactions

```sql
BEGIN;

ALTER TABLE products 
ALTER COLUMN price TYPE NUMERIC(10, 2) 
USING price::NUMERIC;

-- Check results
SELECT * FROM products LIMIT 10;

-- If satisfied:
COMMIT;
-- Otherwise:
-- ROLLBACK;
```

#### Common Gotchas

**Can't decrease VARCHAR length:**
```sql
-- Solution: Truncate during conversion
ALTER TABLE users 
ALTER COLUMN username TYPE VARCHAR(20) 
USING LEFT(username, 20);
```

**NULLs in NOT NULL column:**
```sql
-- Solution: Update NULLs first
UPDATE users SET email = 'unknown@example.com' WHERE email IS NULL;
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(255);
ALTER TABLE users ALTER COLUMN email SET NOT NULL;
```

#### Quick Reference

| From | To | Needs USING? | Example |
|------|----|--------------| --------|
| VARCHAR(N) | VARCHAR(M>N) | No | `ALTER COLUMN col TYPE VARCHAR(200)` |
| VARCHAR | TEXT | No | `ALTER COLUMN col TYPE TEXT` |
| TEXT | INTEGER | Yes | `... USING col::INTEGER` |
| TEXT | DATE | Yes | `... USING col::DATE` |
| INTEGER | BIGINT | No | `ALTER COLUMN col TYPE BIGINT` |

---

## Next Steps

### 🎉 Congratulations on Completing Phase 1!

You've mastered:
- ✅ Processes vs Threads
- ✅ Thread creation and lifecycle
- ✅ Daemon vs User threads
- ✅ Thread operations (sleep, join, interrupt)
- ✅ Basic thread safety (volatile, AtomicInteger)
- ✅ Graceful shutdown patterns

### 🚀 Ready for Phase 2: Thread Synchronization?

**Phase 2 is THE CRITICAL PHASE** where you'll learn:

1. **Race Conditions**
   - See them happen in real code
   - Understand data corruption
   - Learn to detect them

2. **synchronized Keyword**
   - Object-level locking
   - Method vs block synchronization
   - Monitor locks explained

3. **wait() / notify() / notifyAll()**
   - Inter-thread communication
   - Producer-Consumer pattern
   - Avoiding spurious wakeups

4. **Deadlocks**
   - How they occur
   - Detection and prevention
   - Lock ordering

5. **Best Practices**
   - Minimize lock scope
   - Avoid nested locks
   - Use immutability when possible

### Why Phase 2 is Essential

Without synchronization, your concurrent code will have:
- 💥 Race conditions (data corruption)
- 💥 Visibility issues (stale data)
- 💥 Lost updates
- 💥 Hard-to-reproduce bugs

Phase 2 teaches you to write **truly thread-safe code** that works reliably in production.

### Recommended Learning Resources

**Books:**
- "Java Concurrency in Practice" by Brian Goetz (ESSENTIAL)
- "Effective Java" by Joshua Bloch (Concurrency chapters)

**Practice:**
- LeetCode concurrency problems
- Build the suggested enhancement projects
- Read JDK concurrent collections source code

**Next Topics to Master:**
1. Phase 2: Synchronization (race conditions, monitors)
2. Phase 3: Memory Model (happens-before, volatile)
3. Phase 4: Executors (thread pools, futures)
4. Phase 5: Synchronizers (CountDownLatch, Semaphore)

### Keep Practicing!

The exercises in Phase 1 are foundational. Keep the code, experiment with it:
- Add features to the timer app
- Enhance the file processor
- Build a GUI for the download manager
- Create your own concurrent applications

---

## Summary

This guide covered the foundational concepts of Java multithreading with:
- **4 core concepts** explained with real-world analogies
- **4 complete exercises** with production-quality code
- **Best practices** throughout every example
- **Common pitfalls** and how to avoid them

The journey to concurrency mastery is long but rewarding. Phase 1 gives you the foundation - now you're ready to tackle synchronization in Phase 2!

**Happy Concurrent Programming! 🚀**

---

*This document was created as part of a comprehensive Java concurrency learning path. For questions or clarifications on any topic, refer back to the specific sections or practice the exercises again.*