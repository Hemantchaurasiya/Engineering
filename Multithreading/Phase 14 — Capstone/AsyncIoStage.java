package com.orderengine.capstone;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The capstone's synthesis point for I/O-bound stages (payment,
 * notification): NO platform-thread pool sizing formula (Phase 4) is
 * needed here at all — a single lightweight dispatcher thread reads the
 * input queue and fires each item as its OWN virtual thread (Phase 9),
 * bounded by a Semaphore permit (Phase 10 §3 / Phase 12 §2's bulkhead-
 * adjacent concurrency cap) rather than a fixed pool size. This is
 * deliberately different from ValidationStage/ReservationStage's
 * platform-thread PipelineStage — those are CPU-bound (Phase 4's
 * Ncpu+1 applies), these are I/O-bound (Phase 9's virtual-thread model
 * applies), and the capstone uses a genuinely different mechanism for
 * each, not one generic "pipeline stage" abstraction stretched to cover
 * both — exactly the judgment Phase 9 argued for.
 *
 * Shutdown drains correctly even though work is fired onto virtual
 * threads asynchronously from the dispatcher's perspective: the poison
 * pill stops the DISPATCHER, but shutdown() then waits for every
 * already-fired virtual-thread task to actually finish (tracked via
 * inFlight + a wait/notify drain), not just for the dispatcher thread
 * to exit — otherwise "shut down" could be declared while work is still
 * genuinely in flight, exactly the correctness bar Phase 6 §4 set for
 * synchronous stages, now met for this asynchronous shape too.
 */
public abstract class AsyncIoStage<IN, OUT> {

    private final String stageName;
    private final LinkedBlockingQueue<WorkItem<IN>> inputQueue;
    private final PipelineStage.Downstream<OUT> downstream;
    private final Metrics metrics;
    private final Semaphore concurrencyLimit;
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final Object drainLock = new Object();
    private volatile boolean draining = false;
    private Thread dispatcherThread;

    protected AsyncIoStage(String stageName, int queueCapacity, int maxConcurrent,
                            PipelineStage.Downstream<OUT> downstream, Metrics metrics) {
        this.stageName = stageName;
        this.inputQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.downstream = downstream;
        this.metrics = metrics;
        this.concurrencyLimit = new Semaphore(maxConcurrent);
    }

    protected abstract OUT process(IN input) throws Exception;

    public void start() {
        dispatcherThread = new Thread(this::dispatchLoop, stageName + "-dispatcher");
        dispatcherThread.start();
    }

    private void dispatchLoop() {
        try {
            while (true) {
                WorkItem<IN> item = inputQueue.take();
                if (item instanceof WorkItem.PoisonPill) return;

                IN value = ((WorkItem.Data<IN>) item).value();
                concurrencyLimit.acquire(); // blocks the dispatcher if the bulkhead is full — real backpressure
                inFlight.incrementAndGet();

                virtualExecutor.submit(() -> {
                    try {
                        OUT result = process(value);
                        metrics.increment(stageName + ".processed");
                        if (downstream != null) downstream.submit(result);
                    } catch (Exception e) {
                        metrics.increment(stageName + ".rejected");
                    } finally {
                        concurrencyLimit.release();
                        synchronized (drainLock) {
                            if (inFlight.decrementAndGet() == 0 && draining) {
                                drainLock.notifyAll();
                            }
                        }
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void submit(IN item) throws InterruptedException {
        inputQueue.put(WorkItem.of(item));
    }

    public void shutdown(long timeoutSeconds) throws InterruptedException {
        inputQueue.put(WorkItem.PoisonPill.instance());
        dispatcherThread.join(timeoutSeconds * 1000);

        synchronized (drainLock) {
            draining = true;
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
            while (inFlight.get() > 0 && System.currentTimeMillis() < deadline) {
                drainLock.wait(Math.max(1, deadline - System.currentTimeMillis()));
            }
        }
        virtualExecutor.shutdown();
        System.out.println("  [" + stageName + "] shut down (in-flight remaining: " + inFlight.get()
                + ") processed=" + metrics.value(stageName + ".processed")
                + " rejected=" + metrics.value(stageName + ".rejected"));
    }
}
