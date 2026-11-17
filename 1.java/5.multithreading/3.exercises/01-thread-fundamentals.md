# Group 1 — Thread Fundamentals

**Level:** Beginner | **Exercises:** 6 | **Prerequisites:** core Java, lambdas

## What you will master
- Creating and starting threads (`Thread`, `Runnable`), `start()` vs `run()`
- Thread lifecycle and states, `join()`
- Daemon vs user threads and JVM shutdown
- Cooperative cancellation with interruption
- Uncaught exception handling in threads
- `ThreadLocal` for request-scoped context, and its leak risk
- Why thread-per-request does not scale (the motivation for pools)

---

## Exercise 1.1 — Welcome-Email Dispatcher (lifecycle & join) ★

**Scenario:** After 5 users sign up, your service must send a welcome email to each one without blocking the signup API thread.

**Task**
1. Create one named thread per email (`email-sender-1` … `email-sender-5`). Each simulates 500–1500 ms of SMTP latency.
2. Print each thread's `getState()` at: before `start()`, right after `start()`, while sleeping, while waiting on a lock, and after it finishes.
3. The main thread must `join()` all senders and then print "All welcome emails sent in X ms".
4. Replace `start()` with `run()` once and observe what changes (thread name, total time).

**Starter**
```java
Thread t = new Thread(() -> sendEmail(user), "email-sender-" + i);
System.out.println(t.getState()); // NEW
t.start();
```

**Prove it:** You can show all six states `NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED` in a single run (hint: you need a lock held by another thread for `BLOCKED`, and `Object.wait()` for `WAITING`).

**Stretch:** Add a "main thread must not wait more than 2 s" requirement using `join(long)` and report which emails were still pending.

---

## Exercise 1.2 — Heartbeat & Metrics Flusher (daemon threads) ★

**Scenario:** A background thread flushes in-memory metrics to a log every second. When the application's real work is done, the JVM should exit without you manually stopping the flusher.

**Task**
1. Implement `MetricsFlusher` as a thread that prints counters every second.
2. Run once as a user thread and observe that the JVM never exits.
3. Run again with `setDaemon(true)` and observe the JVM exit.
4. Make the flusher lose its final batch on exit; then fix that by adding a shutdown hook (`Runtime.addShutdownHook`) that performs one last flush.

**Prove it:** Final flush appears in the output even when the JVM exits normally.

**Questions to answer in `NOTES.md`:** Why is a daemon thread a bad choice for a thread that writes to a database or file you care about?

---

## Exercise 1.3 — Cancellable Report Export (interruption) ★★

**Scenario:** A user requests a 10-million-row CSV export, then closes the browser tab. The export must stop quickly and release resources.

**Task**
1. Write `ReportExporter implements Runnable` that processes rows in a loop and simulates DB paging with `Thread.sleep(50)` per page.
2. Cancel it from another thread with `interrupt()` after 1 second.
3. Handle both cases correctly:
   - Interrupt arrives while sleeping (`InterruptedException`).
   - Interrupt arrives while doing CPU work (no exception — you must poll `Thread.currentThread().isInterrupted()`).
4. On cancellation, delete the partial file and log "cancelled after N rows".

**Anti-pattern to avoid (write it, then fix it):**
```java
try { Thread.sleep(50); } catch (InterruptedException e) { /* swallowed */ }
```

**Prove it:** Cancellation latency is under 100 ms in both scenarios, and the interrupt flag is restored (`Thread.currentThread().interrupt()`) whenever you catch `InterruptedException` and do not rethrow.

**Stretch:** Build a `CancellableTask` wrapper that supports a cancellation reason and a callback.

---

## Exercise 1.4 — The Silent Worker Death (UncaughtExceptionHandler) ★★

**Scenario:** A worker thread processing payment webhooks throws a `NullPointerException` on a malformed payload. Ops sees nothing in the logs and webhooks silently stop being processed.

**Task**
1. Reproduce: a worker loop that dies on a bad message. Observe the default stderr output and that no other thread notices.
2. Set a per-thread `UncaughtExceptionHandler` that logs thread name, payload id, and exception, and increments a `worker_deaths` counter.
3. Set a JVM-wide default with `Thread.setDefaultUncaughtExceptionHandler`.
4. Better design: catch exceptions per message inside the loop so one poison message does not kill the worker. Compare both approaches.

**Prove it:** A poison message is logged, counted, and the worker keeps processing subsequent messages.

**Observe:** Exceptions thrown inside `ExecutorService.submit()` tasks do *not* reach the handler. Verify this (you will revisit it in Group 5).

---

## Exercise 1.5 — Correlation ID Propagation (ThreadLocal) ★★

**Scenario:** Every incoming request gets a correlation id used in all logs. Business code deep in the call stack must read it without passing it as a parameter.

**Starter**
```java
public final class RequestContext {
    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();
    public static void set(String id) { CORRELATION_ID.set(id); }
    public static String get()        { return CORRELATION_ID.get(); }
    public static void clear()        { CORRELATION_ID.remove(); }
}
```

**Task**
1. Simulate 3 request threads, each with a different correlation id, calling a deep service chain that logs the id.
2. Run the same logic on a `FixedThreadPool(2)` with 6 requests **without** calling `clear()`. Demonstrate a request logging a *previous* request's id (context leak).
3. Fix with `try/finally { clear(); }`.
4. Show that a child thread started inside a request does **not** see the parent's value, then use `InheritableThreadLocal` and explain why it is still unsafe with pools.
5. Write a `ContextAwareExecutor` wrapper that captures the submitter's context and restores it in the worker.

**Prove it:** Zero cross-request leakage over 10,000 tasks on a 4-thread pool.

---

## Exercise 1.6 — Thread-per-Request Meltdown (why pools exist) ★★

**Scenario:** A naive server creates a new platform thread for each of N incoming "requests" that each hold for 2 seconds.

**Task**
1. Simulate N = 1,000 → 10,000 → 50,000 requests with `new Thread(...)`.
2. Measure: time to create/start, peak thread count (`ThreadMXBean`), heap/RSS, and whether you hit `OutOfMemoryError: unable to create native thread`.
3. Reduce `-Xss` and compare.
4. Write down the maximum your machine handles and what limits it.

**Prove it:** You have a table of N vs. time vs. memory. Keep it; you will compare it against pools (Group 5) and virtual threads (Group 10).

---

## Group 1 Completion Checklist
- [ ] I can explain `start()` vs `run()` and show the difference
- [ ] I can enumerate every thread state and trigger each one
- [ ] I always restore the interrupt flag or rethrow
- [ ] I know why exceptions in worker threads can vanish
- [ ] I always clear `ThreadLocal` values in pooled threads
