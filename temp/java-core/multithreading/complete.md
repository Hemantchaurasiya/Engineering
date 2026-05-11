## **1. Basics of Concurrency in Java**

Concurrency is the ability to run multiple tasks at the same time to improve performance. Java provides built-in support for concurrent programming using **threads**.

---

## **🔹 Introduction to Concurrency vs. Parallelism**

### **Concurrency**

- It means handling multiple tasks at once but not necessarily at the same instant.
- Tasks may be interleaved, switching execution contexts.
- It improves **responsiveness** in applications.

### **Parallelism**

- It means executing multiple tasks simultaneously.
- Requires multiple cores to achieve true parallel execution.
- Useful for computationally intensive tasks.

### **📌 Use Case Example**

**Concurrency**: Handling multiple client requests in a web server.

**Parallelism**: Performing multiple matrix multiplications at the same time using multiple CPU cores.

---

## **🔹 Processes vs. Threads**

### **Process**

- A process is an independent execution unit with its own memory space.
- It does not share memory with other processes.
- Heavyweight in terms of context switching.

### **Thread**

- A thread is a lightweight execution unit that runs inside a process.
- Threads share the same memory space.
- Faster context switching than processes.

### **📌 Use Case Example**

**Processes**: Running a database server and a web server independently.

**Threads**: Handling multiple user requests in a web server using threads.

---

## **🔹 Java Thread Model**

Java provides a **thread-based concurrency model** where threads are the primary unit of execution. The JVM manages threads using:

1. **User-created threads**: Created using `Thread` or `Runnable`.
2. **Daemon threads**: Background threads that terminate when no user threads are running.

### **📌 Where is it Used?**

- Web servers
- Background tasks (garbage collection, logging)
- Asynchronous event handling (GUI, API calls)

---

## **🔹 Creating Threads in Java**

Java provides two ways to create threads:

### **1️⃣ Extending the `Thread` Class**

```java

class MyThread extends Thread {
    public void run() {
        System.out.println("Thread is running: " + Thread.currentThread().getName());
    }
}

public class ThreadExample {
    public static void main(String[] args) {
        MyThread t1 = new MyThread();
        t1.start(); // Starts the thread execution
    }
}

```

### **📌 When to Use?**

Use when **overriding the `Thread` class** and need more control over thread behavior.

---

### **2️⃣ Implementing the `Runnable` Interface**

```java

class MyRunnable implements Runnable {
    public void run() {
        System.out.println("Runnable thread is running: " + Thread.currentThread().getName());
    }
}

public class RunnableExample {
    public static void main(String[] args) {
        Thread t1 = new Thread(new MyRunnable());
        t1.start();
    }
}

```

### **📌 When to Use?**

Use when you need **better separation of logic** (since Java does not support multiple inheritance).

**✅ Best Practice:** Prefer `Runnable` over `Thread` because it allows the class to extend other classes.

---

## **🔹 Thread Lifecycle and States**

Threads in Java have **five lifecycle states**:

| State | Description |
| --- | --- |
| **NEW** | Thread is created but not started (`new Thread()`). |
| **RUNNABLE** | Thread is ready to run (`start()` is called). |
| **BLOCKED** | Thread is waiting for a resource. |
| **WAITING** | Thread is waiting indefinitely for another thread’s signal. |
| **TIMED_WAITING** | Thread is waiting for a fixed time (`sleep()`, `join()`). |
| **TERMINATED** | Thread has completed execution. |

### **📌 Example of Lifecycle**

```java

class LifecycleExample extends Thread {
    public void run() {
        try {
            System.out.println("Thread is RUNNING...");
            Thread.sleep(2000); // TIMED_WAITING
        } catch (InterruptedException e) {
            System.out.println("Thread interrupted!");
        }
        System.out.println("Thread is TERMINATED.");
    }
}

public class ThreadLifecycleDemo {
    public static void main(String[] args) {
        LifecycleExample thread = new LifecycleExample();
        System.out.println("Thread State: " + thread.getState()); // NEW
        thread.start();
        System.out.println("Thread State after start(): " + thread.getState()); // RUNNABLE
    }
}

```

### **📌 Where is it Used?**

- **Waiting**: When a thread waits for a file to download.
- **Blocked**: When multiple threads try to access a synchronized resource.
- **Terminated**: After processing an API response.

---

## **🔹 Summary**

| Concept | Description | Use Case |
| --- | --- | --- |
| **Concurrency vs. Parallelism** | Concurrency is interleaving tasks; Parallelism is simultaneous execution. | Web server vs. CPU-intensive tasks |
| **Processes vs. Threads** | Process has its own memory; Threads share memory. | Web servers vs. API requests |
| **Java Thread Model** | JVM manages user and daemon threads. | Garbage collection, background tasks |
| **Thread vs. Runnable** | `Thread` extends a class, `Runnable` implements an interface. | Prefer `Runnable` for flexibility |
| **Thread Lifecycle** | States: New, Runnable, Blocked, Waiting, Timed Waiting, Terminated. | API calls, File downloads |

---
# **2. Thread Management in Java**

Thread management is crucial in Java to ensure efficient execution, resource utilization, and avoiding concurrency issues. This section covers **thread creation**, **managing execution**, **daemon vs. user threads**, and **thread priorities** with examples.

---

## **🔹 1. Thread Creation: `Thread` Class and `Runnable` Interface**

Java provides two ways to create threads:

### **✅ Method 1: Extending the `Thread` Class**

- The class extends `Thread` and overrides the `run()` method.
- Call `start()` to begin execution.

```java

class MyThread extends Thread {
    public void run() {
        System.out.println("Thread running: " + Thread.currentThread().getName());
    }
}

public class ThreadExample {
    public static void main(String[] args) {
        MyThread t1 = new MyThread();
        t1.start();
    }
}

```

### **📌 Use Case**

- When we need to create a separate execution path without sharing behavior.
- Example: Background services like **garbage collection**.

---

### **✅ Method 2: Implementing `Runnable` Interface**

- More flexible because it allows extending another class.

```java

class MyRunnable implements Runnable {
    public void run() {
        System.out.println("Runnable thread running: " + Thread.currentThread().getName());
    }
}

public class RunnableExample {
    public static void main(String[] args) {
        Thread t1 = new Thread(new MyRunnable());
        t1.start();
    }
}

```

### **📌 Use Case**

- Recommended for better design since Java doesn’t support multiple inheritance.
- Example: Web server handling **multiple client requests**.

---

## **🔹 2. Managing Thread Execution**

Threads can be managed using **start()**, **sleep()**, **yield()**, and **join()**.

### **✅ `start()`: Start a New Thread**

- Creates a separate thread of execution.

```java

class StartExample extends Thread {
    public void run() {
        System.out.println("Thread started: " + Thread.currentThread().getName());
    }
}

public class StartDemo {
    public static void main(String[] args) {
        StartExample t1 = new StartExample();
        t1.start(); // Calls the run() method asynchronously
    }
}

```

### **📌 Use Case**

- Starting a **background logging task**.

---

### **✅ `sleep(ms)`: Pause Execution**

- Causes the thread to **pause** for a given duration.

```java

class SleepExample extends Thread {
    public void run() {
        try {
            System.out.println("Thread sleeping...");
            Thread.sleep(2000); // Sleeps for 2 seconds
            System.out.println("Thread awake!");
        } catch (InterruptedException e) {
            System.out.println("Interrupted!");
        }
    }
}

public class SleepDemo {
    public static void main(String[] args) {
        SleepExample t1 = new SleepExample();
        t1.start();
    }
}

```

### **📌 Use Case**

- **Rate limiting** (API calls), **simulating delays**.

---

### **✅ `yield()`: Suggest CPU Time Reallocation**

- Suggests the **CPU should execute another thread**.

```java

class YieldExample extends Thread {
    public void run() {
        for (int i = 0; i < 3; i++) {
            System.out.println(Thread.currentThread().getName() + " yielding...");
            Thread.yield();
        }
        System.out.println(Thread.currentThread().getName() + " finished execution.");
    }
}

public class YieldDemo {
    public static void main(String[] args) {
        YieldExample t1 = new YieldExample();
        YieldExample t2 = new YieldExample();

        t1.start();
        t2.start();
    }
}

```

### **📌 Use Case**

- **Multithreading scheduling optimization**.
- Example: Giving priority to **higher-priority threads**.

---

### **✅ `join()`: Wait for Thread Completion**

- Makes the calling thread wait until another thread **finishes execution**.

```java

class JoinExample extends Thread {
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println("Executing: " + i);
        }
    }
}

public class JoinDemo {
    public static void main(String[] args) {
        JoinExample t1 = new JoinExample();
        t1.start();
        try {
            t1.join(); // Main thread waits until t1 completes
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Main thread continues...");
    }
}

```

### **📌 Use Case**

- Ensuring **one task completes before another starts**.
- Example: **Loading a configuration before executing tasks**.

---

## **🔹 3. Daemon vs. User Threads**

### **✅ User Thread**

- A normal thread that keeps running **until it finishes** execution.
- Example: **Main thread** in Java.

### **✅ Daemon Thread**

- A background thread that **automatically terminates** when all user threads finish.

```java

class DaemonExample extends Thread {
    public void run() {
        while (true) {
            System.out.println("Daemon thread running...");
        }
    }
}

public class DaemonDemo {
    public static void main(String[] args) {
        DaemonExample t1 = new DaemonExample();
        t1.setDaemon(true); // Set as daemon thread
        t1.start();
        System.out.println("Main thread finished execution.");
    }
}

```

### **📌 Use Case**

- **Garbage collection**.
- **Background monitoring tasks**.

---

## **🔹 4. Thread Priorities**

Threads have **priorities from 1 (MIN_PRIORITY) to 10 (MAX_PRIORITY)**.

- `Thread.NORM_PRIORITY` = 5 (Default)
- `Thread.MIN_PRIORITY` = 1
- `Thread.MAX_PRIORITY` = 10

```java

class PriorityExample extends Thread {
    public void run() {
        System.out.println(Thread.currentThread().getName() + " with priority " + Thread.currentThread().getPriority());
    }
}

public class PriorityDemo {
    public static void main(String[] args) {
        PriorityExample t1 = new PriorityExample();
        PriorityExample t2 = new PriorityExample();

        t1.setPriority(Thread.MIN_PRIORITY); // Priority 1
        t2.setPriority(Thread.MAX_PRIORITY); // Priority 10

        t1.start();
        t2.start();
    }
}

```

### **📌 Use Case**

- Giving **higher priority** to CPU-intensive threads.
- Example: **Rendering a video** vs. **background file cleanup**.

---

## **🔹 Summary**

| Concept | Description | Use Case |
| --- | --- | --- |
| **Thread Creation** | `Thread` vs. `Runnable` | Web servers, background tasks |
| **start()** | Starts a new thread | Multithreading |
| **sleep(ms)** | Pauses execution | Rate limiting, delays |
| **yield()** | Suggests CPU reallocation | Scheduling optimization |
| **join()** | Waits for thread completion | Config loading before execution |
| **Daemon Threads** | Background threads | Garbage collection, logging |
| **Thread Priority** | Defines execution priority | UI responsiveness, CPU-intensive tasks |

---
# **3. Synchronization and Thread Safety in Java**

In multithreading, **synchronization** ensures that multiple threads do not **interfere** with each other when accessing shared resources. Without proper synchronization, **race conditions** can occur, leading to unpredictable behavior.

This section covers:

1. **Need for Synchronization**
2. **`synchronized` Keyword (Methods & Blocks)**
3. **Intrinsic Locks (Monitor Locks)**
4. **Deadlocks, Livelocks, and Starvation**
5. **Atomicity and Visibility Issues**
6. **`volatile` Keyword and Its Use Cases**

---

## **🔹 1. Need for Synchronization**

### **✅ Problem: Race Condition**

A **race condition** occurs when multiple threads access and modify shared data simultaneously, leading to inconsistent results.

### **Example: Bank Account Withdrawal Without Synchronization**

```java

class BankAccount {
    private int balance = 100;

    public void withdraw(int amount) {
        if (balance >= amount) {
            System.out.println(Thread.currentThread().getName() + " is withdrawing: " + amount);
            balance -= amount;
            System.out.println("Remaining Balance: " + balance);
        } else {
            System.out.println("Insufficient balance for " + Thread.currentThread().getName());
        }
    }
}

public class RaceConditionDemo {
    public static void main(String[] args) {
        BankAccount account = new BankAccount();

        Runnable task = () -> {
            account.withdraw(70);
        };

        Thread t1 = new Thread(task, "Thread-1");
        Thread t2 = new Thread(task, "Thread-2");

        t1.start();
        t2.start();
    }
}

```

### **❌ Issue**

- **Both threads may withdraw before the balance updates**, causing incorrect behavior.

### **✅ Solution: Synchronization**

We use **synchronized** to prevent race conditions.

---

## **🔹 2. `synchronized` Keyword (Methods & Blocks)**

The **`synchronized`** keyword ensures that only **one thread** can execute the method/block at a time.

### **✅ `synchronized` Method**

```java

class BankAccount {
    private int balance = 100;

    public synchronized void withdraw(int amount) {
        if (balance >= amount) {
            System.out.println(Thread.currentThread().getName() + " is withdrawing: " + amount);
            balance -= amount;
            System.out.println("Remaining Balance: " + balance);
        } else {
            System.out.println("Insufficient balance for " + Thread.currentThread().getName());
        }
    }
}

```

### **📌 Use Case**

- **Ensuring thread safety** in banking, e-commerce transactions, etc.

---

### **✅ `synchronized` Block**

If **only a part** of the method requires synchronization, use a **synchronized block**.

```java

class BankAccount {
    private int balance = 100;

    public void withdraw(int amount) {
        synchronized (this) { // Synchronizing only critical section
            if (balance >= amount) {
                System.out.println(Thread.currentThread().getName() + " is withdrawing: " + amount);
                balance -= amount;
                System.out.println("Remaining Balance: " + balance);
            } else {
                System.out.println("Insufficient balance for " + Thread.currentThread().getName());
            }
        }
    }
}

```

### **📌 Use Case**

- Allows **better performance** by synchronizing only necessary parts.

---

## **🔹 3. Intrinsic Locks (Monitor Locks)**

- Every Java object has a **monitor lock**.
- When a thread enters a `synchronized` block, it **acquires the lock**.
- Other threads must **wait** until the lock is released.

### **✅ Example: Multiple Threads on Different Methods**

```java

class SharedResource {
    public synchronized void methodA() {
        System.out.println(Thread.currentThread().getName() + " is executing methodA");
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
    }

    public synchronized void methodB() {
        System.out.println(Thread.currentThread().getName() + " is executing methodB");
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
    }
}

public class LockDemo {
    public static void main(String[] args) {
        SharedResource resource = new SharedResource();

        new Thread(resource::methodA, "Thread-1").start();
        new Thread(resource::methodB, "Thread-2").start();
    }
}

```

### **📌 Use Case**

- Ensures **exclusive access** to shared resources.

---

## **🔹 4. Deadlocks, Livelocks, and Starvation**

### **✅ Deadlock**

Occurs when **two or more threads wait indefinitely** for locks held by each other.

```java

class Deadlock {
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();

    public void method1() {
        synchronized (lock1) {
            System.out.println(Thread.currentThread().getName() + " locked lock1");
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            synchronized (lock2) {
                System.out.println(Thread.currentThread().getName() + " locked lock2");
            }
        }
    }

    public void method2() {
        synchronized (lock2) {
            System.out.println(Thread.currentThread().getName() + " locked lock2");
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            synchronized (lock1) {
                System.out.println(Thread.currentThread().getName() + " locked lock1");
            }
        }
    }
}

public class DeadlockExample {
    public static void main(String[] args) {
        Deadlock d = new Deadlock();

        new Thread(d::method1, "Thread-1").start();
        new Thread(d::method2, "Thread-2").start();
    }
}

```

### **❌ Issue**

- Both threads **wait for each other** forever.

### **✅ Solution**

- Always **acquire locks in a fixed order**.
- Use **timeouts** or `tryLock()` (from `ReentrantLock`).

---

## **🔹 5. Atomicity and Visibility Issues**

### **✅ Problem: Non-Atomic Operations**

```java

class Counter {
    private int count = 0;

    public void increment() {
        count++;  // Not atomic
    }
}

```

- The `count++` operation is **not atomic** because it's **read-modify-write**.

### **✅ Solution: Use `AtomicInteger`**

```java

import java.util.concurrent.atomic.AtomicInteger;

class Counter {
    private AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        count.incrementAndGet();
    }
}

```

### **📌 Use Case**

- Ensures **atomic operations** without locks.

---

## **🔹 6. `volatile` Keyword and Its Use Cases**

- Ensures **visibility** of variable updates across threads.

### **✅ Without `volatile` (Visibility Issue)**

```java

class VolatileExample {
    private boolean running = true;

    public void stop() {
        running = false;  // Not visible immediately to other threads
    }
}

```

### **✅ With `volatile`**

```java

class VolatileExample {
    private volatile boolean running = true;

    public void stop() {
        running = false;  // Immediately visible to other threads
    }
}

```

### **📌 Use Case**

- Ensures **latest value is always visible** in multi-threaded environments.
- Example: **Thread stop flag**.

---

## **🔹 Summary**

| Concept | Description | Use Case |
| --- | --- | --- |
| **synchronized Method** | Locks entire method | Bank transactions, Shared logs |
| **synchronized Block** | Locks only critical section | Performance improvement |
| **Intrinsic Locks** | Object-level locking | Shared resources |
| **Deadlocks** | Threads wait indefinitely | Multi-resource locks |
| **Atomic Variables** | Ensures atomicity | Counter updates |
| **`volatile` Keyword** | Ensures visibility | Stop flags |

---
# **Java Memory Model (JMM)**

The **Java Memory Model (JMM)** defines how threads interact through **memory** and ensures visibility, ordering, and atomicity of shared variables.

---

## **🔹 1. Happens-Before Relationship**

The **Happens-Before** relationship is a fundamental concept in JMM that ensures **proper visibility** and **ordering** of operations.

### **✅ Key Rules**

1. **Program Order Rule** → Statements in a single thread appear to execute **in order**.
2. **Monitor Lock Rule** → An unlock (`synchronized`) on an object happens-before any subsequent lock.
3. **Volatile Rule** → A write to a `volatile` variable happens-before any subsequent read of that variable.
4. **Thread Start Rule** → A `Thread.start()` happens-before any action inside that thread.
5. **Thread Join Rule** → A `Thread.join()` happens-before the joined thread's termination.
6. **Final Field Rule** → Final fields are visible once the object is constructed.

### **✅ Example: Happens-Before Using `synchronized`**

```java

class SharedResource {
    private int count = 0;

    public synchronized void increment() { // Happens-before rule applies
        count++;
    }

    public synchronized int getCount() {
        return count;
    }
}

public class HappensBeforeDemo {
    public static void main(String[] args) {
        SharedResource resource = new SharedResource();

        Thread t1 = new Thread(resource::increment);
        Thread t2 = new Thread(() -> System.out.println("Count: " + resource.getCount()));

        t1.start();
        t2.start();
    }
}

```

### **📌 Use Case**

- Ensures that **writes before `synchronized` unlock are visible** after acquiring the lock.

---

## **🔹 2. Visibility, Ordering, and Atomicity Guarantees**

| Feature | Description | Example |
| --- | --- | --- |
| **Visibility** | Ensures one thread’s updates are visible to others. | `volatile`, `synchronized` |
| **Ordering** | Ensures proper execution sequence across threads. | Happens-Before |
| **Atomicity** | Ensures indivisible operations. | `synchronized`, `AtomicInteger` |

---

## **🔹 3. Effect of `volatile`, `synchronized`, and `final` on Memory Visibility**

### **✅ `volatile` Ensures Visibility (But Not Atomicity)**

```java

class VolatileExample {
    private volatile boolean running = true;

    public void stop() {
        running = false; // Visible immediately to other threads
    }
}

```

### **📌 Use Case**

- Use `volatile` for **flags** and ensuring the latest value is seen across threads.

---

### **✅ `synchronized` Ensures Visibility and Atomicity**

```java

class SynchronizedExample {
    private int count = 0;

    public synchronized void increment() { // Ensures visibility + atomicity
        count++;
    }

    public synchronized int getCount() {
        return count;
    }
}

```

### **📌 Use Case**

- Use `synchronized` for **critical sections** (e.g., banking transactions).

---

### **✅ `final` Guarantees Visibility After Construction**

```java

class FinalExample {
    private final int value;

    public FinalExample(int value) {
        this.value = value; // Final fields are safely published
    }

    public int getValue() {
        return value;
    }
}

```

### **📌 Use Case**

- Use `final` for **immutability** and ensuring thread-safe object initialization.

---

## **🔹 Summary Table**

| Feature | Ensures | Use Case |
| --- | --- | --- |
| `volatile` | **Visibility** | Flags, status variables |
| `synchronized` | **Visibility + Atomicity** | Shared counters, transactions |
| `final` | **Safe initialization** | Immutable objects |

---
# **Inter-Thread Communication in Java**

Inter-thread communication allows multiple threads to coordinate execution by sharing data safely. Java provides built-in mechanisms like `wait()`, `notify()`, and `notifyAll()` to help threads communicate effectively.

---

## **🔹 1. wait(), notify(), and notifyAll()**

### **✅ Explanation**

- `wait()`: Releases the lock and **pauses** execution until another thread **notifies** it.
- `notify()`: Wakes up **one** waiting thread.
- `notifyAll()`: Wakes up **all** waiting threads.

**🔹 Important Rules:**

1. Must be called within a `synchronized` block.
2. The thread calling `wait()` must own the **monitor lock**.
3. `notify()` or `notifyAll()` does not release the lock immediately; it signals a waiting thread, which resumes after the current thread exits `synchronized`.

### **✅ Example: Basic Producer-Consumer Using wait() & notify()**

```java

class SharedResource {
    private int data;
    private boolean available = false;

    public synchronized void produce(int value) throws InterruptedException {
        while (available) {
            wait(); // Wait until the resource is consumed
        }
        data = value;
        available = true;
        System.out.println("Produced: " + value);
        notify(); // Notify the consumer
    }

    public synchronized int consume() throws InterruptedException {
        while (!available) {
            wait(); // Wait until a value is produced
        }
        available = false;
        System.out.println("Consumed: " + data);
        notify(); // Notify the producer
        return data;
    }
}

public class WaitNotifyExample {
    public static void main(String[] args) {
        SharedResource resource = new SharedResource();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    resource.produce(i);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) { e.printStackTrace(); }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    resource.consume();
                    Thread.sleep(1500);
                }
            } catch (InterruptedException e) { e.printStackTrace(); }
        });

        producer.start();
        consumer.start();
    }
}

```

### **📌 Use Case**

- Used when **one thread must wait for another to complete** before proceeding (e.g., **Producer-Consumer pattern**).
- Suitable when **manual signaling between threads** is required.

---

## **🔹 2. Producer-Consumer Problem (Using BlockingQueue)**

The **Producer-Consumer** problem is a classic synchronization challenge where:

- A **producer thread** generates data.
- A **consumer thread** processes data.
- They work at different speeds and require **coordination**.

### **✅ Modern Approach Using `BlockingQueue`**

```java

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class Producer implements Runnable {
    private BlockingQueue<Integer> queue;

    public Producer(BlockingQueue<Integer> queue) {
        this.queue = queue;
    }

    public void run() {
        try {
            for (int i = 1; i <= 5; i++) {
                System.out.println("Produced: " + i);
                queue.put(i); // Blocks if the queue is full
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) { e.printStackTrace(); }
    }
}

class Consumer implements Runnable {
    private BlockingQueue<Integer> queue;

    public Consumer(BlockingQueue<Integer> queue) {
        this.queue = queue;
    }

    public void run() {
        try {
            for (int i = 1; i <= 5; i++) {
                int value = queue.take(); // Blocks if the queue is empty
                System.out.println("Consumed: " + value);
                Thread.sleep(1500);
            }
        } catch (InterruptedException e) { e.printStackTrace(); }
    }
}

public class BlockingQueueExample {
    public static void main(String[] args) {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
        new Thread(new Producer(queue)).start();
        new Thread(new Consumer(queue)).start();
    }
}

```

### **📌 Use Case**

- When **high concurrency** is required.
- **Avoids manual synchronization** by handling wait-notify automatically.

---

## **🔹 3. Thread Signaling (Using Locks & Condition Variables)**

A **more flexible alternative** to `wait()/notify()` is **Lock & Condition variables** from `java.util.concurrent.locks`.

### **✅ Example: Producer-Consumer with Lock & Condition**

```java

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class SharedLockResource {
    private int data;
    private boolean available = false;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public void produce(int value) throws InterruptedException {
        lock.lock();
        try {
            while (available) {
                condition.await(); // Wait for consumer
            }
            data = value;
            available = true;
            System.out.println("Produced: " + value);
            condition.signal(); // Signal consumer
        } finally {
            lock.unlock();
        }
    }

    public int consume() throws InterruptedException {
        lock.lock();
        try {
            while (!available) {
                condition.await(); // Wait for producer
            }
            available = false;
            System.out.println("Consumed: " + data);
            condition.signal(); // Signal producer
            return data;
        } finally {
            lock.unlock();
        }
    }
}

public class LockConditionExample {
    public static void main(String[] args) {
        SharedLockResource resource = new SharedLockResource();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    resource.produce(i);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) { e.printStackTrace(); }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    resource.consume();
                    Thread.sleep(1500);
                }
            } catch (InterruptedException e) { e.printStackTrace(); }
        });

        producer.start();
        consumer.start();
    }
}

```

### **📌 Use Case**

- **Preferred over wait/notify** when working with **multiple conditions**.
- Avoids common pitfalls like **spurious wake-ups**.

---

## **🔹 Summary Table**

| Approach | Features | Best Use Case |
| --- | --- | --- |
| `wait() / notify()` | Basic thread communication, requires `synchronized` | Manual thread coordination |
| `BlockingQueue` | Built-in thread safety, prevents busy waiting | High-performance Producer-Consumer |
| `Lock & Condition` | Fine-grained thread signaling, supports multiple conditions | Complex synchronization needs |

---

## **🚀 Key Takeaways**

1. **Use `wait()` & `notify()`** for **basic thread communication**.
2. **Prefer `BlockingQueue`** for **modern Producer-Consumer** problems.
3. **Use `Lock` & `Condition`** for **fine-grained thread control**.

---
# **Advanced Thread Synchronization in Java**

Java provides advanced synchronization mechanisms beyond `synchronized`, including **Explicit Locks (`ReentrantLock`)**, **Condition Variables**, **Read-Write Locks**, and **Optimistic Locking with `StampedLock`**. These help improve **performance, fine-grained control, and concurrency**.

---

## **🔹 1. Explicit Locks (`ReentrantLock`)**

### **✅ Explanation**

- Unlike `synchronized`, `ReentrantLock` allows **explicit** locking and unlocking.
- Supports:
    - **Fair locking** (prioritizes waiting threads)
    - **Interruptible locking** (a thread can cancel a waiting attempt)
    - **Try-lock with timeout** (avoids deadlocks)

### **✅ Code Example: ReentrantLock with Try-Lock**

```java

import java.util.concurrent.locks.ReentrantLock;

class SharedResource {
    private final ReentrantLock lock = new ReentrantLock();

    public void accessResource(String threadName) {
        if (lock.tryLock()) {  // Try to acquire the lock
            try {
                System.out.println(threadName + " acquired the lock.");
                Thread.sleep(1000); // Simulating work
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock(); // Ensure the lock is released
                System.out.println(threadName + " released the lock.");
            }
        } else {
            System.out.println(threadName + " couldn't acquire the lock.");
        }
    }
}

public class ReentrantLockExample {
    public static void main(String[] args) {
        SharedResource resource = new SharedResource();

        Runnable task = () -> resource.accessResource(Thread.currentThread().getName());

        Thread t1 = new Thread(task, "Thread 1");
        Thread t2 = new Thread(task, "Thread 2");

        t1.start();
        t2.start();
    }
}

```

### **📌 Use Cases**

- **Fine-grained locking** where `synchronized` is too restrictive.
- **Avoids deadlocks** by using `tryLock()` with a timeout.
- **Interruptible locks** where threads need the option to exit while waiting.

---

## **🔹 2. Condition Variables (`Condition` interface)**

### **✅ Explanation**

- `Condition` provides a way to wait for conditions to be met, similar to `wait()/notify()`.
- Used with `ReentrantLock` for **finer control** over thread synchronization.

### **✅ Code Example: Producer-Consumer Using `Condition`**

```java

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class SharedData {
    private int data;
    private boolean available = false;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public void produce(int value) throws InterruptedException {
        lock.lock();
        try {
            while (available) {
                condition.await(); // Wait if data is not consumed
            }
            data = value;
            available = true;
            System.out.println("Produced: " + value);
            condition.signal(); // Notify consumer
        } finally {
            lock.unlock();
        }
    }

    public void consume() throws InterruptedException {
        lock.lock();
        try {
            while (!available) {
                condition.await(); // Wait if no data
            }
            System.out.println("Consumed: " + data);
            available = false;
            condition.signal(); // Notify producer
        } finally {
            lock.unlock();
        }
    }
}

public class ConditionExample {
    public static void main(String[] args) {
        SharedData resource = new SharedData();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    resource.produce(i);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) { e.printStackTrace(); }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    resource.consume();
                    Thread.sleep(1500);
                }
            } catch (InterruptedException e) { e.printStackTrace(); }
        });

        producer.start();
        consumer.start();
    }
}

```

### **📌 Use Cases**

- **More flexibility** than `wait()/notify()` for multi-condition waiting.
- **Multiple conditions** can be managed separately (e.g., **readers & writers**).
- Used in **thread coordination scenarios** like **bounded buffers**.

---

## **🔹 3. Read-Write Locks (`ReentrantReadWriteLock`)**

### **✅ Explanation**

- Allows **multiple readers** but only **one writer** at a time.
- Useful for **frequently read, rarely modified** shared resources.

### **✅ Code Example: Read-Write Lock**

```java

import java.util.concurrent.locks.ReentrantReadWriteLock;

class SharedDataRW {
    private int data = 0;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void readData(String threadName) {
        lock.readLock().lock();
        try {
            System.out.println(threadName + " reading data: " + data);
            Thread.sleep(500);
        } catch (InterruptedException e) { e.printStackTrace(); }
        finally {
            lock.readLock().unlock();
        }
    }

    public void writeData(int value, String threadName) {
        lock.writeLock().lock();
        try {
            data = value;
            System.out.println(threadName + " writing data: " + data);
            Thread.sleep(1000);
        } catch (InterruptedException e) { e.printStackTrace(); }
        finally {
            lock.writeLock().unlock();
        }
    }
}

public class ReadWriteLockExample {
    public static void main(String[] args) {
        SharedDataRW sharedData = new SharedDataRW();

        Runnable readTask = () -> sharedData.readData(Thread.currentThread().getName());
        Runnable writeTask = () -> sharedData.writeData(100, Thread.currentThread().getName());

        Thread t1 = new Thread(readTask, "Reader 1");
        Thread t2 = new Thread(readTask, "Reader 2");
        Thread t3 = new Thread(writeTask, "Writer");

        t1.start();
        t2.start();
        t3.start();
    }
}

```

### **📌 Use Cases**

- **Database-like scenarios** with **more reads than writes**.
- **Caching mechanisms** where multiple threads read frequently.

---

## **🔹 4. StampedLock and Optimistic Locking**

### **✅ Explanation**

- `StampedLock` improves `ReentrantReadWriteLock` by:
    - **Optimistic Reads**: Readers don't block writes **until** a modification happens.
    - **Better Performance** for read-heavy scenarios.

### **✅ Code Example: StampedLock with Optimistic Read**

```java

import java.util.concurrent.locks.StampedLock;

class SharedStampedData {
    private int data = 0;
    private final StampedLock lock = new StampedLock();

    public void readData(String threadName) {
        long stamp = lock.tryOptimisticRead();
        int readValue = data;

        if (!lock.validate(stamp)) { // Check if a write occurred
            stamp = lock.readLock();
            try {
                readValue = data;
            } finally {
                lock.unlockRead(stamp);
            }
        }

        System.out.println(threadName + " read data: " + readValue);
    }

    public void writeData(int value, String threadName) {
        long stamp = lock.writeLock();
        try {
            data = value;
            System.out.println(threadName + " wrote data: " + data);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}

public class StampedLockExample {
    public static void main(String[] args) {
        SharedStampedData resource = new SharedStampedData();

        Runnable readTask = () -> resource.readData(Thread.currentThread().getName());
        Runnable writeTask = () -> resource.writeData(100, Thread.currentThread().getName());

        new Thread(readTask, "Reader 1").start();
        new Thread(readTask, "Reader 2").start();
        new Thread(writeTask, "Writer").start();
    }
}

```

### **📌 Use Cases**

- **Optimistic locking** for **highly concurrent reads**.
- Suitable for **low-write, high-read scenarios** (e.g., **caches**).

---

## **🚀 Summary**

| Mechanism | Best Use Case |
| --- | --- |
| `ReentrantLock` | Fine-grained locking, interruptible locks |
| `Condition` | Multi-condition thread signaling |
| `ReentrantReadWriteLock` | More reads than writes |
| `StampedLock` | Optimistic locking, high-read, low-write |

---
# **Thread Coordination & Executors Framework in Java**

Java provides the **Executors Framework** to efficiently manage and coordinate threads. Instead of manually creating and managing threads, the framework provides **thread pools, scheduling, and asynchronous execution mechanisms**.

---

## **🔹 1. Java Executor Framework (`Executor`, `ExecutorService`, `ScheduledExecutorService`)**

### **✅ Explanation**

- **`Executor`**: A simple interface for executing tasks.
- **`ExecutorService`**: Extends `Executor` and provides methods for task lifecycle management (shutdown, submit, etc.).
- **`ScheduledExecutorService`**: Allows scheduling tasks at **fixed delays or intervals**.

### **✅ Code Example: Using `ExecutorService`**

```java

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorExample {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(3); // Pool of 3 threads

        Runnable task = () -> {
            System.out.println(Thread.currentThread().getName() + " is executing the task.");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) { e.printStackTrace(); }
        };

        for (int i = 0; i < 5; i++) {
            executor.execute(task); // Submit tasks
        }

        executor.shutdown(); // Shutdown the executor after tasks are done
    }
}

```

### **📌 Use Cases**

- Managing **multiple concurrent tasks** efficiently.
- **Thread reuse** to avoid excessive thread creation overhead.
- Suitable for **web servers, batch processing, and parallel computation**.

---

## **🔹 2. Thread Pools (`FixedThreadPool`, `CachedThreadPool`, `SingleThreadExecutor`, `WorkStealingPool`)**

### **✅ Explanation**

Java provides different types of thread pools optimized for different scenarios:

| Thread Pool Type | Description | Use Case |
| --- | --- | --- |
| `FixedThreadPool(n)` | **Fixed number** of worker threads | **CPU-bound tasks** (consistent load) |
| `CachedThreadPool()` | **Dynamic thread creation** (reuses idle threads) | **Short-lived, high-volume tasks** |
| `SingleThreadExecutor()` | **Single worker thread** (queue-based) | **Sequential task execution** |
| `WorkStealingPool()` | **Fork-join pool** for parallelism | **Divide-and-conquer tasks** |

### **✅ Code Example: FixedThreadPool vs CachedThreadPool**

```java

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadPoolExample {
    public static void main(String[] args) {
        ExecutorService fixedPool = Executors.newFixedThreadPool(2);
        ExecutorService cachedPool = Executors.newCachedThreadPool();

        Runnable task = () -> {
            System.out.println(Thread.currentThread().getName() + " is executing a task.");
            try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
        };

        System.out.println("Using FixedThreadPool:");
        for (int i = 0; i < 4; i++) {
            fixedPool.execute(task);
        }
        fixedPool.shutdown();

        System.out.println("\nUsing CachedThreadPool:");
        for (int i = 0; i < 4; i++) {
            cachedPool.execute(task);
        }
        cachedPool.shutdown();
    }
}

```

### **📌 Use Cases**

- **`FixedThreadPool`** → Best for **steady workloads** (e.g., database connections).
- **`CachedThreadPool`** → Best for **short-lived, high-volume tasks** (e.g., network requests).
- **`SingleThreadExecutor`** → Best for **task queuing scenarios** (e.g., logging).
- **`WorkStealingPool`** → Best for **parallel computations** (e.g., image processing).

---

## **🔹 3. Asynchronous Computation with `Callable` & `Future`**

### **✅ Explanation**

- Unlike `Runnable`, `Callable` allows tasks to **return a result** and **throw checked exceptions**.
- `Future` represents the **result of an asynchronous computation**, providing methods to:
    - **Check completion**
    - **Retrieve the result**
    - **Cancel execution**

### **✅ Code Example: Callable & Future**

```java

import java.util.concurrent.*;

public class CallableFutureExample {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Integer> task = () -> {
            Thread.sleep(2000);
            return 10 + 20; // Returning a result
        };

        Future<Integer> future = executor.submit(task); // Submit Callable task

        try {
            System.out.println("Waiting for result...");
            Integer result = future.get(); // Blocks until result is available
            System.out.println("Result: " + result);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }
}

```

### **📌 Use Cases**

- When **tasks need to return results** (e.g., fetching API responses).
- **Long-running tasks** where you don't want to block the main thread.
- Implementing **async computations** in **microservices and batch jobs**.

---

## **🔹 4. `CompletionService` for Managing Multiple Async Tasks**

### **✅ Explanation**

- `CompletionService` simplifies managing multiple async tasks.
- Instead of polling each `Future`, it allows **retrieving results as they complete**.

### **✅ Code Example: Using `CompletionService`**

```java

import java.util.concurrent.*;

public class CompletionServiceExample {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CompletionService<Integer> completionService = new ExecutorCompletionService<>(executor);

        // Submit tasks
        for (int i = 1; i <= 3; i++) {
            final int num = i;
            completionService.submit(() -> {
                Thread.sleep(2000 * num);
                return num * num; // Return square
            });
        }

        // Retrieve results as they complete
        for (int i = 1; i <= 3; i++) {
            try {
                Future<Integer> future = completionService.take(); // Blocks until a task is done
                System.out.println("Result: " + future.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
    }
}

```

### **📌 Use Cases**

- **Multiple independent computations** running in parallel.
- **Prioritizing faster tasks** by retrieving results as soon as they're available.
- Suitable for **data aggregation, batch processing, and parallel algorithms**.

---

## **🚀 Summary Table**

| Feature | Description | Best Use Cases |
| --- | --- | --- |
| `ExecutorService` | Manages task execution and lifecycle | General **task execution** |
| `FixedThreadPool` | Limited, reusable threads | **CPU-intensive tasks** |
| `CachedThreadPool` | Dynamically scales with demand | **Short-lived, burst tasks** |
| `SingleThreadExecutor` | Sequential task execution | **Logging, Queued Tasks** |
| `WorkStealingPool` | Parallelism using multiple queues | **Fork-Join, Parallel Processing** |
| `Callable & Future` | Asynchronous computation with results | **Tasks that return values** |
| `CompletionService` | Retrieve async results as soon as they're ready | **Batch processing, Data aggregation** |

## **🚀 Final Thoughts**

The **Executors Framework** makes it easier to **manage and coordinate** multiple threads efficiently.\

---
# **Parallel Computing in Java** 🚀

Parallel computing allows Java programs to execute multiple tasks **simultaneously**, improving performance for **CPU-intensive** operations like **data processing, matrix computations, and recursive algorithms**.

Java provides **two primary approaches** for parallelism:

1. **Fork/Join Framework** (Optimized for Recursive Task Splitting)
2. **Parallel Streams** (Simplified Parallel Data Processing)

---

## **🔹 1. Fork/Join Framework**

### **✅ Explanation**

- Introduced in **Java 7**, it **divides tasks into smaller subtasks** that run in parallel.
- Uses **work-stealing**: idle threads pick up unfinished tasks from busy threads.
- Based on the `ForkJoinPool` class with `RecursiveTask` (returns result) and `RecursiveAction` (no result).

### **✅ Code Example: Sum of an Array using Fork/Join**

```java

import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinPool;

class SumTask extends RecursiveTask<Integer> {
    private int[] arr;
    private int start, end;
    private static final int THRESHOLD = 5; // Base case size

    SumTask(int[] arr, int start, int end) {
        this.arr = arr;
        this.start = start;
        this.end = end;
    }

    @Override
    protected Integer compute() {
        if ((end - start) <= THRESHOLD) { // Base case: Compute directly
            int sum = 0;
            for (int i = start; i < end; i++) sum += arr[i];
            return sum;
        }

        // Split task
        int mid = (start + end) / 2;
        SumTask leftTask = new SumTask(arr, start, mid);
        SumTask rightTask = new SumTask(arr, mid, end);

        leftTask.fork(); // Run left task in parallel
        int rightResult = rightTask.compute(); // Compute right task in current thread
        int leftResult = leftTask.join(); // Wait for left task

        return leftResult + rightResult;
    }
}

public class ForkJoinExample {
    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool();
        int[] arr = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        SumTask task = new SumTask(arr, 0, arr.length);

        int result = pool.invoke(task);
        System.out.println("Sum: " + result);
    }
}

```

### **📌 Use Cases**

- **Recursive parallel algorithms** (e.g., **Merge Sort, Quick Sort, Fibonacci**).
- **Large array computations** (e.g., **sum, filtering, and transformation**).
- **Image and video processing** (e.g., **pixel transformations**).

---

## **🔹 2. `RecursiveTask` vs `RecursiveAction`**

| Feature | `RecursiveTask<T>` | `RecursiveAction` |
| --- | --- | --- |
| **Returns Value?** | ✅ Yes (Generic Type) | ❌ No |
| **Use Case** | Computation tasks (sum, sorting) | Side-effect tasks (file operations, logging) |

### **✅ Code Example: RecursiveAction (No Return)**

```java

import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ForkJoinPool;

class PrintTask extends RecursiveAction {
    private int start, end;
    private static final int THRESHOLD = 3;

    PrintTask(int start, int end) {
        this.start = start;
        this.end = end;
    }

    @Override
    protected void compute() {
        if ((end - start) <= THRESHOLD) {
            for (int i = start; i < end; i++)
                System.out.println(Thread.currentThread().getName() + " prints: " + i);
            return;
        }

        int mid = (start + end) / 2;
        PrintTask leftTask = new PrintTask(start, mid);
        PrintTask rightTask = new PrintTask(mid, end);

        invokeAll(leftTask, rightTask); // Execute both in parallel
    }
}

public class RecursiveActionExample {
    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(new PrintTask(1, 10));
    }
}

```

### **📌 Use Cases**

- **Parallel processing on large datasets** (e.g., **big data log processing**).
- **Image transformations** (e.g., **applying filters, resizing**).
- **Parallel file operations** (e.g., **searching in files**).

---

## **🔹 3. Parallel Streams (`parallelStream()`)**

### **✅ Explanation**

- Introduced in **Java 8**, it enables **automatic parallelism** for **collections**.
- Uses **Fork/Join Pool internally** but **simplifies syntax**.
- Suitable for **data processing and bulk operations**.

### **✅ Code Example: Parallel Sum**

```java

import java.util.Arrays;
import java.util.List;

public class ParallelStreamExample {
    public static void main(String[] args) {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        int sum = numbers.parallelStream()
                         .mapToInt(Integer::intValue)
                         .sum(); // Summing in parallel

        System.out.println("Sum: " + sum);
    }
}

```

### **📌 Use Cases**

- **Processing large lists efficiently** (e.g., **aggregations, filtering**).
- **Batch computations** (e.g., **financial transactions**).
- **Text processing** (e.g., **counting words in documents**).

---

## **🔹 4. Performance Comparison: `Stream()` vs `parallelStream()`**

| Feature | `stream()` (Sequential) | `parallelStream()` (Parallel) |
| --- | --- | --- |
| **Processing** | One element at a time | Multiple elements at once |
| **Performance** | Fast for small data | Faster for large data |
| **Use Case** | UI tasks, logging | Large-scale computations |

### **✅ Code Example: Comparing Performance**

```java

import java.util.*;
import java.util.stream.IntStream;

public class StreamPerformanceTest {
    public static void main(String[] args) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) numbers.add(i);

        long start1 = System.nanoTime();
        numbers.stream().mapToInt(i -> i * 2).sum();
        long end1 = System.nanoTime();
        System.out.println("Sequential Time: " + (end1 - start1) / 1e6 + " ms");

        long start2 = System.nanoTime();
        numbers.parallelStream().mapToInt(i -> i * 2).sum();
        long end2 = System.nanoTime();
        System.out.println("Parallel Time: " + (end2 - start2) / 1e6 + " ms");
    }
}

```

🔹 **`parallelStream()` is better for large datasets** but may **cause overhead** for small data.

---

## **🚀 Summary Table**

| Feature | Description | Best Use Cases |
| --- | --- | --- |
| **Fork/Join Framework** | Divides tasks for parallel execution | **Recursive parallel computations** |
| **RecursiveTask** | Returns results (e.g., sum, sorting) | **Parallel data aggregation** |
| **RecursiveAction** | No return (e.g., logging, IO) | **Parallel side-effect operations** |
| **Parallel Streams** | Automatic parallelism for collections | **Large data processing (filtering, mapping)** |

---

## **🚀 Final Thoughts**

- **Use `Fork/Join`** for **recursive, CPU-intensive tasks**.
- **Use `parallelStream()`** for **simplified parallel processing of collections**.
- **Avoid parallel execution for small datasets** (overhead may slow down execution).

---
# **Concurrent Data Structures in Java 🚀**

### **Why Use Concurrent Data Structures?**

In a **multi-threaded environment**, using standard data structures (e.g., `HashMap`, `ArrayList`) **leads to race conditions and inconsistencies**. Java provides **thread-safe** alternatives in the `java.util.concurrent` package.

🔹 **Benefits:**

✅ Prevents data corruption in multi-threaded environments.

✅ Optimized for high-concurrency scenarios.

✅ Avoids explicit synchronization (`synchronized`, `Lock`).

---

## **1️⃣ ConcurrentHashMap**

### **✅ Explanation**

- A **thread-safe** alternative to `HashMap`.
- Uses **segment-based locking** (**bucket-level locking** instead of global locking).
- Allows **concurrent reads and updates** with **minimal contention**.

### **✅ Code Example: Using `ConcurrentHashMap` for Shared Counters**

```java

import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMapExample {
    public static void main(String[] args) {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        // Simulating multiple threads updating the map
        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                map.merge("count", 1, Integer::sum); // Thread-safe update
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Final Count: " + map.get("count"));
    }
}

```

### **📌 Use Cases**

✅ **Shared counters** in a multi-threaded system.

✅ **Caching in multi-threaded applications** (e.g., storing user sessions).

✅ **Thread-safe frequency counting** (e.g., **word frequency in large documents**).

---

## **2️⃣ ConcurrentLinkedQueue**

### **✅ Explanation**

- A **non-blocking, thread-safe queue** based on a **linked list**.
- Uses **CAS (Compare-And-Swap)** for lock-free performance.
- Best suited for **multi-threaded producer-consumer scenarios**.

### **✅ Code Example: Producer-Consumer Using `ConcurrentLinkedQueue`**

```java

import java.util.concurrent.ConcurrentLinkedQueue;

public class ConcurrentQueueExample {
    public static void main(String[] args) {
        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();

        // Producer thread
        Runnable producer = () -> {
            for (int i = 1; i <= 5; i++) {
                queue.offer(i);
                System.out.println("Produced: " + i);
            }
        };

        // Consumer thread
        Runnable consumer = () -> {
            while (!queue.isEmpty()) {
                Integer item = queue.poll();
                if (item != null) System.out.println("Consumed: " + item);
            }
        };

        Thread t1 = new Thread(producer);
        Thread t2 = new Thread(consumer);
        t1.start();
        t2.start();
    }
}

```

### **📌 Use Cases**

✅ **Multi-threaded event processing** (e.g., **logging, messaging**).

✅ **Producer-Consumer models** (e.g., **task queues**).

✅ **Thread-safe work queues in concurrent applications**.

---

## **3️⃣ ConcurrentSkipListMap**

### **✅ Explanation**

- A **thread-safe, sorted map** using **Skip List**.
- Supports **logarithmic-time insertions, deletions, and lookups**.
- **Better for concurrent sorted operations than `TreeMap`**.

### **✅ Code Example: Real-time Stock Prices with `ConcurrentSkipListMap`**

```java

import java.util.concurrent.ConcurrentSkipListMap;

public class SkipListMapExample {
    public static void main(String[] args) {
        ConcurrentSkipListMap<Integer, String> stockPrices = new ConcurrentSkipListMap<>();

        // Adding stock prices
        stockPrices.put(100, "Company A");
        stockPrices.put(105, "Company B");
        stockPrices.put(110, "Company C");

        System.out.println("Lowest Price: " + stockPrices.firstEntry());
        System.out.println("Highest Price: " + stockPrices.lastEntry());
    }
}

```

### **📌 Use Cases**

✅ **Sorted real-time data** (e.g., **stock prices, leaderboards**).

✅ **Multi-threaded priority scheduling**.

✅ **Efficient range-based queries in concurrent applications**.

---

## **4️⃣ CopyOnWriteArrayList**

### **✅ Explanation**

- **Thread-safe version of `ArrayList`**, but **not efficient for frequent updates**.
- **Copy-on-Write Mechanism**: On every **modification**, a new copy of the list is created.
- **Best for read-heavy applications** with occasional writes.

### **✅ Code Example: Read-Heavy Logging System**

```java

import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteArrayListExample {
    public static void main(String[] args) {
        CopyOnWriteArrayList<String> logs = new CopyOnWriteArrayList<>();

        // Multiple threads adding logs
        Runnable task = () -> {
            logs.add(Thread.currentThread().getName() + " - Log Entry");
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Logs: " + logs);
    }
}

```

### **📌 Use Cases**

✅ **Read-heavy applications** (e.g., **caching, configurations**).

✅ **Multi-threaded logging frameworks**.

✅ **Snapshot-based operations (e.g., generating reports)**.

---

## **5️⃣ CopyOnWriteArraySet**

### **✅ Explanation**

- A **thread-safe Set** that **prevents duplicate elements**.
- Uses an **internally wrapped `CopyOnWriteArrayList`**.
- Best for **multi-threaded applications requiring read-heavy operations**.

### **✅ Code Example: Unique User Sessions**

```java

import java.util.concurrent.CopyOnWriteArraySet;

public class CopyOnWriteSetExample {
    public static void main(String[] args) {
        CopyOnWriteArraySet<String> userSessions = new CopyOnWriteArraySet<>();

        Runnable task = () -> {
            userSessions.add(Thread.currentThread().getName() + "_Session");
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Active Sessions: " + userSessions);
    }
}

```

### **📌 Use Cases**

✅ **Thread-safe user session management**.

✅ **Event listener registrations**.

✅ **Read-heavy applications with unique elements**.

---

## **🚀 Summary Table**

| **Data Structure** | **Best Use Case** | **Thread-Safety Mechanism** |
| --- | --- | --- |
| **ConcurrentHashMap** | Multi-threaded caching, frequency counting | Bucket-based locking |
| **ConcurrentLinkedQueue** | Producer-consumer, event queues | Lock-free, CAS-based |
| **ConcurrentSkipListMap** | Sorted real-time data (leaderboards, stock prices) | Skip list |
| **CopyOnWriteArrayList** | Read-heavy, logging, configs | Copy-on-Write |
| **CopyOnWriteArraySet** | Unique user sessions, event listeners | Copy-on-Write |

---

## **🚀 Key Takeaways**

✔️ Use **`ConcurrentHashMap`** for **fast concurrent read/write** operations.

✔️ Use **`ConcurrentLinkedQueue`** for **lock-free, thread-safe queues**.

✔️ Use **`ConcurrentSkipListMap`** for **sorted concurrent data access**.

✔️ Use **`CopyOnWriteArrayList/Set`** when **reads are more frequent than writes**.

---
# **Atomic Operations & Locks in Java** 🚀

### **Why Do We Need Atomic Operations?**

In a multi-threaded environment, shared variables create **race conditions** when multiple threads modify them simultaneously. The traditional approach is to use `synchronized`, but this introduces performance overhead. **Atomic operations** provide **lock-free** solutions using **hardware-supported atomic instructions**.

✅ **Key Benefits:**

- No need for explicit locking (`synchronized`, `ReentrantLock`).
- **Faster than locks** in scenarios with low contention.
- **Ensures atomicity** of updates to shared variables.

---

## **1️⃣ `java.util.concurrent.atomic` Package**

Java provides **atomic variable classes** in `java.util.concurrent.atomic` that use **CAS (Compare-And-Swap)** for updates without locking.

| **Atomic Class** | **Replaces** | **Usage** |
| --- | --- | --- |
| `AtomicInteger` | `int` | Thread-safe counter |
| `AtomicLong` | `long` | Thread-safe long counter |
| `AtomicBoolean` | `boolean` | Thread-safe flag |
| `AtomicReference<T>` | `T` (object) | Thread-safe object reference |
| `AtomicStampedReference<T>` | `T` (with versioning) | Solves ABA problem |

---

### **✅ `AtomicInteger` Example: Multi-threaded Counter**

```java

import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterExample {
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                counter.incrementAndGet(); // Atomic operation
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("Final Count: " + counter.get());
    }
}

```

### **📌 Where to Use?**

✅ **Thread-safe counters** (e.g., request counters, analytics).

✅ **Performance over `synchronized` in low-contention cases**.

---

### **✅ `AtomicReference<T>` Example: Updating Shared Objects Safely**

```java

import java.util.concurrent.atomic.AtomicReference;

class User {
    String name;
    User(String name) { this.name = name; }
}

public class AtomicReferenceExample {
    private static final AtomicReference<User> userRef = new AtomicReference<>(new User("Alice"));

    public static void main(String[] args) {
        Runnable task = () -> {
            userRef.set(new User("Bob")); // Atomic update
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Final User: " + userRef.get().name);
    }
}

```

### **📌 Where to Use?**

✅ **Thread-safe updates to shared objects**.

✅ **Reference updates without locks**.

---

## **2️⃣ LongAdder & LongAccumulator**

**Why?** `AtomicLong` suffers from **contention** when many threads update it.

Solution? **LongAdder** & **LongAccumulator** distribute updates across multiple variables, reducing contention.

---

### **✅ `LongAdder` Example: High-Throughput Counter**

```java

import java.util.concurrent.atomic.LongAdder;

public class LongAdderExample {
    private static final LongAdder counter = new LongAdder();

    public static void main(String[] args) throws InterruptedException {
        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                counter.increment(); // Faster than AtomicLong
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("Final Count: " + counter.sum());
    }
}

```

### **📌 Where to Use?**

✅ **High-concurrency counters** (e.g., request counters in web servers).

✅ **Better performance than `AtomicLong` under high contention**.

---

### **✅ `LongAccumulator` Example: Custom Accumulation (Multiplication)**

```java

import java.util.concurrent.atomic.LongAccumulator;

public class LongAccumulatorExample {
    private static final LongAccumulator accumulator = new LongAccumulator((x, y) -> x * y, 1);

    public static void main(String[] args) throws InterruptedException {
        Runnable task = () -> accumulator.accumulate(2); // Multiply by 2

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("Final Value: " + accumulator.get());
    }
}

```

### **📌 Where to Use?**

✅ **Custom aggregations (e.g., running products, min/max values).**

✅ **Better performance than explicit locks for aggregation tasks.**

---

## **3️⃣ Compare-And-Swap (CAS) Mechanism**

### **✅ What is CAS?**

- **Atomic operations rely on CAS** instead of locks.
- CAS checks if a value is still the **expected value** before updating.
- If the value was modified by another thread, the update **fails and retries**.

### **✅ CAS-Based Counter Using `AtomicInteger`**

```java

import java.util.concurrent.atomic.AtomicInteger;

public class CASExample {
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        Runnable task = () -> {
            while (true) {
                int current = counter.get();
                int updated = current + 1;

                // Compare-And-Swap: If current == expected, update
                if (counter.compareAndSet(current, updated)) {
                    break;
                }
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("Final Count: " + counter.get());
    }
}

```

### **📌 Where to Use?**

✅ **When frequent reads and occasional writes are required.**

✅ **Alternative to locks for performance improvement.**

---

## **🚀 Summary Table**

| **Concept** | **Usage** | **Performance** |
| --- | --- | --- |
| `AtomicInteger` / `AtomicLong` | Simple thread-safe counters | ✅ Good for low-contention |
| `AtomicReference<T>` | Thread-safe object updates | ✅ No locking overhead |
| `LongAdder` | High-throughput counters | ✅ Better than `AtomicLong` |
| `LongAccumulator` | Custom aggregation (min/max, product) | ✅ Efficient for accumulations |
| CAS (`compareAndSet()`) | Lock-free updates to variables | ✅ Avoids blocking |

---

## **🚀 Key Takeaways**

✔️ Use **`AtomicInteger` / `AtomicLong`** for **simple counters**.

✔️ Use **`AtomicReference<T>`** for **thread-safe object references**.

✔️ Use **`LongAdder` for high-concurrency counters** (better than `AtomicLong`).

✔️ Use **`LongAccumulator` for complex aggregations**.

✔️ **CAS (Compare-And-Swap) avoids locks**, but works best with **low contention**.

---
# **Thread Local Storage in Java** 🚀

In a **multi-threaded environment**, sharing data across threads can lead to **race conditions**. `ThreadLocal` provides **thread-local storage**, ensuring each thread has **its own isolated copy** of a variable.

---

## **1️⃣ What is `ThreadLocal`?**

🔹 `ThreadLocal<T>` allows **each thread** to have its **own independent copy** of a variable.

🔹 Useful when **each thread needs its own unique state** (e.g., database connections, user sessions).

🔹 **Avoids synchronization** because each thread has its own variable instance.

### **✅ Example: Using `ThreadLocal` for Thread-Specific Data**

```java

class ThreadLocalExample {
    // Create a ThreadLocal variable for each thread
    private static final ThreadLocal<Integer> threadLocalValue = ThreadLocal.withInitial(() -> 0);

    public static void main(String[] args) {
        Runnable task = () -> {
            threadLocalValue.set(threadLocalValue.get() + 1); // Each thread modifies its own value
            System.out.println(Thread.currentThread().getName() + " -> " + threadLocalValue.get());
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
    }
}

```

**🔹 Output (varies per execution):**

```
rust
CopyEdit
Thread-0 -> 1
Thread-1 -> 1

```

Each thread gets its **own separate variable**, avoiding conflicts.

### **📌 Where to Use?**

✅ **User session management** (e.g., storing user data in web applications).

✅ **Database connections** (each thread gets a dedicated connection).

✅ **Logging per thread** (e.g., tracking request logs independently).

---

## **2️⃣ `InheritableThreadLocal`: Passing Values to Child Threads**

🔹 By default, `ThreadLocal` **does not propagate values** to child threads.

🔹 **`InheritableThreadLocal`** allows **child threads** to **inherit values** from the parent thread.

### **✅ Example: Using `InheritableThreadLocal` to Share Data with Child Threads**

```java

class InheritableThreadLocalExample {
    private static final InheritableThreadLocal<String> threadLocalUser = new InheritableThreadLocal<>();

    public static void main(String[] args) {
        threadLocalUser.set("Admin");

        Thread childThread = new Thread(() -> {
            System.out.println(Thread.currentThread().getName() + " inherited: " + threadLocalUser.get());
        });

        childThread.start();
    }
}

```

**🔹 Output:**

```
yaml
CopyEdit
Thread-0 inherited: Admin

```

The child thread **inherits** the parent's value (`"Admin"`).

### **📌 Where to Use?**

✅ **Propagating security context across threads**.

✅ **Passing transaction IDs/logging context to spawned threads**.

---

## **🚀 Key Takeaways**

| Feature | `ThreadLocal` | `InheritableThreadLocal` |
| --- | --- | --- |
| **Scope** | Each thread gets its own copy | Child threads inherit values |
| **Use Case** | Caching, session storage | Security context, transactions |
| **Synchronization Required?** | ❌ No | ❌ No |

---
# **Java Virtual Machine (JVM) & Concurrency** 🚀

Concurrency in Java is closely tied to how the **JVM manages threads, memory, and garbage collection (GC)**. Understanding these aspects helps optimize multi-threaded applications.

---

## **1️⃣ Thread Dump Analysis 🛠️**

🔹 A **thread dump** captures the current state of all threads in the JVM.

🔹 Used to **debug deadlocks, high CPU usage, and blocked threads**.

### **📌 When to Use?**

✅ Analyzing **stuck** or **waiting** threads.

✅ Debugging **deadlocks** and **high CPU utilization**.

✅ Checking **thread pool usage** in production.

### **✅ Example: Generating a Thread Dump**

**🔹 Use these commands to generate a thread dump in production:**

- **Linux/macOS:**
    
    ```
    sh
    CopyEdit
    jstack <PID> > thread_dump.txt
    
    ```
    
- **Windows:**
    
    ```
    sh
    CopyEdit
    jcmd <PID> Thread.print > thread_dump.txt
    
    ```
    
- **JVisualVM Tool:** A GUI tool to inspect live threads.

### **✅ Example: Analyzing a Deadlocked Thread Dump**

If two threads hold locks and wait for each other, `jstack` output might show:

```
csharp
CopyEdit
Found one Java-level deadlock:
Thread-1 is waiting for lock held by Thread-2
Thread-2 is waiting for lock held by Thread-1

```

**🔹 Solution:** Identify synchronized code and refactor to avoid circular dependencies.

---

## **2️⃣ Synchronization Performance Tuning ⚡**

🔹 **Synchronization (`synchronized`, `Lock`) is expensive** due to contention.

🔹 **Overuse** of synchronization can lead to **performance bottlenecks**.

### **📌 When to Use?**

✅ If **too many threads** are waiting for the same lock.

✅ To improve **throughput** in multi-threaded applications.

✅ If a **synchronized block causes delays** in execution.

### **✅ Example: Avoiding Synchronization Overhead**

Instead of synchronizing an entire method:

```java

public synchronized void increment() {
    count++;
}

```

🔹 **Use fine-grained locking:**

```java

public void increment() {
    synchronized (this) {
        count++;
    }
}

```

🔹 **Use `AtomicInteger` to remove locks:**

```java

private AtomicInteger count = new AtomicInteger(0);
public void increment() {
    count.incrementAndGet();
}

```

**🚀 Impact:** **Removes locking overhead**, making the code **more scalable**.

---

## **3️⃣ Java Garbage Collection & Its Impact on Multithreading 🗑️**

🔹 **Garbage Collection (GC) pauses** can **block** threads and cause performance drops.

🔹 **Thread-local objects** can reduce GC impact.

🔹 Use **GC tuning** to optimize memory allocation.

### **📌 When to Use?**

✅ If **GC pauses affect response time**.

✅ If **large object allocations** slow down processing.

✅ If too many **short-lived objects** are created in high-concurrency apps.

### **✅ Example: Using G1 GC for Low Latency Apps**

Use JVM options to enable **G1 Garbage Collector**:

```
sh
CopyEdit
java -XX:+UseG1GC -Xms1g -Xmx4g -XX:MaxGCPauseMillis=100 MyApp

```

**🚀 Impact:** Reduces **pause times** for **real-time applications**.

---

## **🚀 Key Takeaways**

| Topic | Purpose | When to Use? |
| --- | --- | --- |
| **Thread Dump Analysis** | Debugging deadlocks & high CPU usage | Production debugging |
| **Synchronization Performance** | Reducing lock contention & improving scalability | High thread contention |
| **GC Tuning** | Minimizing pauses & improving throughput | Low-latency applications |

---
# **CompletableFuture & Reactive Programming in Java** 🚀

### **1️⃣ CompletableFuture for Asynchronous Programming**

🔹 `CompletableFuture` is part of **java.util.concurrent** and provides a powerful way to handle **asynchronous computations**.

🔹 It allows **non-blocking**, **chaining**, and **parallel execution** of tasks.

---

### **📌 When to Use?**

✅ When performing **long-running computations** (e.g., API calls, DB queries).

✅ When needing **non-blocking** code execution.

✅ When executing **dependent or parallel tasks**.

### **✅ Example: Creating a Simple CompletableFuture**

```java

import java.util.concurrent.CompletableFuture;

public class AsyncExample {
    public static void main(String[] args) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            System.out.println("Task executed in: " + Thread.currentThread().getName());
        });

        future.join(); // Wait for the task to complete
    }
}

```

🔹 **Output:**

```
arduino
CopyEdit
Task executed in: ForkJoinPool.commonPool-worker-1

```

**🚀 Benefit:** Runs asynchronously in a separate thread.

---

### **2️⃣ Chaining & Combining Futures**

🔹 Use **thenApply**, **thenAccept**, and **thenCompose** to chain tasks.

🔹 Combine multiple futures using **allOf()** and **anyOf()**.

### **✅ Example: Chaining Computations**

```java

import java.util.concurrent.CompletableFuture;

public class FutureChaining {
    public static void main(String[] args) {
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> 10)
                .thenApply(n -> n * 2) // Multiply result by 2
                .thenApply(n -> n + 5); // Add 5

        System.out.println("Final Result: " + future.join());
    }
}

```

🔹 **Output:**

```
sql
CopyEdit
Final Result: 25

```

**🚀 Benefit:** **Non-blocking, sequential transformations**.

---

### **3️⃣ Exception Handling in Async Computation**

🔹 Use **exceptionally()** or **handle()** to recover from failures.

### **✅ Example: Handling Exceptions**

```java

import java.util.concurrent.CompletableFuture;

public class ExceptionHandling {
    public static void main(String[] args) {
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
            if (Math.random() > 0.5) throw new RuntimeException("Something went wrong!");
            return 10;
        }).exceptionally(ex -> {
            System.out.println("Error: " + ex.getMessage());
            return 0; // Default value in case of failure
        });

        System.out.println("Result: " + future.join());
    }
}

```

🔹 **Output (may vary based on random execution):**

```
makefile
CopyEdit
Error: java.lang.RuntimeException: Something went wrong!
Result: 0

```

**🚀 Benefit:** **Gracefully handle failures** in asynchronous tasks.

---

### **4️⃣ Introduction to Reactive Streams (Flow API in Java 9)**

🔹 The **Flow API (Java 9)** provides **reactive programming** support in the JDK.

🔹 It consists of **Publisher, Subscriber, Subscription, and Processor** interfaces.

### **✅ Example: Simple Flow API Publisher & Subscriber**

```java

import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

public class FlowExample {
    public static void main(String[] args) {
        SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

        Flow.Subscriber<String> subscriber = new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(1); // Request one item
            }

            public void onNext(String item) {
                System.out.println("Received: " + item);
            }

            public void onError(Throwable throwable) {
                System.err.println("Error: " + throwable.getMessage());
            }

            public void onComplete() {
                System.out.println("Processing Complete");
            }
        };

        publisher.subscribe(subscriber);
        publisher.submit("Hello, Reactive World!");
        publisher.close();
    }
}

```

🔹 **Output:**

```
makefile
CopyEdit
Received: Hello, Reactive World!
Processing Complete

```

**🚀 Benefit:** **Reactive, event-driven programming**.

---

### **5️⃣ Project Reactor & RxJava (Optional for Deep Learning)**

🔹 **Project Reactor** and **RxJava** provide **advanced reactive programming** capabilities.

🔹 These are used for **event-driven architectures**, **real-time streaming**, and **backpressure handling**.

### **✅ Example: Reactive Programming Using Project Reactor**

```java

import reactor.core.publisher.Flux;

public class ReactorExample {
    public static void main(String[] args) {
        Flux.just("A", "B", "C")
            .map(String::toLowerCase)
            .subscribe(System.out::println);
    }
}

```

🔹 **Output:**

```
css
CopyEdit
a
b
c

```

**🚀 Benefit:** **More efficient event processing** with **backpressure support**.

---

## **🚀 Key Takeaways**

| Topic | Purpose | When to Use? |
| --- | --- | --- |
| **CompletableFuture** | Non-blocking async programming | DB calls, API requests |
| **Chaining Futures** | Sequential async execution | Dependent computations |
| **Exception Handling** | Handling failures in async tasks | Resilient async workflows |
| **Flow API (Java 9)** | Reactive streams support | Event-driven architectures |
| **Project Reactor & RxJava** | Advanced reactive programming | Streaming, real-time systems |

---
# Best Practices

# **Best Practices & Performance Considerations in Java Concurrency** 🚀

Concurrency in Java offers great **performance improvements**, but **misuse** can lead to **deadlocks, race conditions, and inefficient resource usage**. Below are the best practices and performance optimizations to use in multithreaded applications.

---

## **1️⃣ Choosing the Right Concurrency Model**

### ✅ **Use Case: Selecting the Best Approach**

Choosing the correct concurrency model depends on the **use case**:

| **Requirement** | **Best Concurrency Model** |
| --- | --- |
| Short-lived, independent tasks | `ExecutorService` (ThreadPool) |
| Computation-intensive tasks | `ForkJoinPool` |
| Task dependencies (Async) | `CompletableFuture` |
| Real-time event-driven processing | **Reactive Streams (Flow API, Project Reactor, RxJava)** |
| Shared data access | **Concurrent Collections (ConcurrentHashMap, CopyOnWriteArrayList)** |
| High-throughput applications | **Non-blocking I/O (NIO)** |

### ✅ **Example: Choosing Executors for Managing Threads**

Using an `ExecutorService` to efficiently manage multiple threads:

```java

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorExample {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(3); // 3 threads
        for (int i = 1; i <= 5; i++) {
            int taskId = i;
            executor.execute(() -> {
                System.out.println("Executing Task " + taskId + " in " + Thread.currentThread().getName());
            });
        }
        executor.shutdown(); // Close the thread pool
    }
}

```

🔹 **Output (May vary):**

```
arduino
CopyEdit
Executing Task 1 in pool-1-thread-1
Executing Task 2 in pool-1-thread-2
Executing Task 3 in pool-1-thread-3
Executing Task 4 in pool-1-thread-1
Executing Task 5 in pool-1-thread-2

```

✅ **Benefit:** **Efficiently reuses threads instead of creating new ones**.

---

## **2️⃣ Avoiding Deadlocks & Race Conditions**

### **❌ Problem: Deadlock**

Deadlocks happen when **two or more threads wait for each other’s lock indefinitely**.

### **✅ Example: Deadlock Scenario**

```java

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DeadlockExample {
    private static final Lock lock1 = new ReentrantLock();
    private static final Lock lock2 = new ReentrantLock();

    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            lock1.lock();
            System.out.println("Thread 1 acquired Lock 1");
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            lock2.lock();
            System.out.println("Thread 1 acquired Lock 2");
            lock2.unlock();
            lock1.unlock();
        });

        Thread t2 = new Thread(() -> {
            lock2.lock();
            System.out.println("Thread 2 acquired Lock 2");
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            lock1.lock();
            System.out.println("Thread 2 acquired Lock 1");
            lock1.unlock();
            lock2.unlock();
        });

        t1.start();
        t2.start();
    }
}

```

### **✅ Fixing Deadlocks**

- **Always acquire locks in the same order.**
- **Use `tryLock()` with timeouts to avoid waiting forever.**

```java

if (lock1.tryLock() && lock2.tryLock()) {
    try {
        // Critical section
    } finally {
        lock1.unlock();
        lock2.unlock();
    }
}

```

---

## **3️⃣ Optimizing Thread Pool Size**

### **📌 Why is Thread Pool Optimization Important?**

- **Too many threads** → High CPU and memory usage.
- **Too few threads** → Wasted CPU cycles due to idle time.

### ✅ **Formula to Calculate Thread Pool Size**

🔹 **For CPU-bound tasks:**

Thread Pool Size=CPU Cores+1\text{Thread Pool Size} = \text{CPU Cores} + 1

Thread Pool Size=CPU Cores+1

🔹 **For I/O-bound tasks:**

Thread Pool Size=CPU Cores×(1+Wait Time/Compute Time)1\text{Thread Pool Size} = \frac{\text{CPU Cores} \times (1 + \text{Wait Time} / \text{Compute Time})}{1}

Thread Pool Size=1CPU Cores×(1+Wait Time/Compute Time)​

🔹 **Example Calculation:**

If a system has **4 cores** and **spends 80% time waiting for I/O**, the optimal pool size is:

4×(1+4)1=20\frac{4 \times (1 + 4)}{1} = 20

14×(1+4)​=20

### ✅ **Code: Choosing Optimal Thread Pool**

```java

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OptimizedThreadPool {
    public static void main(String[] args) {
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores + 1);

        for (int i = 0; i < 10; i++) {
            executor.execute(() -> {
                System.out.println(Thread.currentThread().getName() + " executing task");
            });
        }
        executor.shutdown();
    }
}

```

🔹 **Benefit:** **Dynamically determines optimal thread pool size**.

---

## **4️⃣ Measuring & Profiling Performance**

### **📌 Tools for Performance Monitoring**

| **Tool** | **Purpose** |
| --- | --- |
| **JVisualVM** | Thread dumps, CPU profiling |
| **JConsole** | Monitor threads, CPU, and memory usage |
| **Java Flight Recorder (JFR)** | Advanced performance tuning |
| **Thread Dump Analysis** | Debugging deadlocks & performance issues |

### ✅ **Example: Taking a Thread Dump**

```
sh
CopyEdit
jstack <PID> > threaddump.txt

```

✅ **Use JVisualVM to analyze thread states and lock contention**.

---

## **🚀 Summary of Best Practices**

| **Best Practice** | **What It Solves** | **Code Strategy** |
| --- | --- | --- |
| **Choose the Right Model** | Avoids inefficiencies | `ExecutorService`, `ForkJoinPool`, `CompletableFuture` |
| **Avoid Deadlocks** | Prevents infinite waiting | Acquire locks in the same order, use `tryLock()` |
| **Optimize Thread Pool** | Balances performance | Use `Runtime.getRuntime().availableProcessors()` |
| **Profile Performance** | Detects bottlenecks | Use **JVisualVM, JConsole, JFR** |