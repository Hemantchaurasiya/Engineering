package com.orderengine.phase6;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The classic multi-producer/multi-consumer pattern, generalized into a
 * reusable base every pipeline stage in this engine extends: a bounded
 * BlockingQueue as the shared buffer between producers and this stage's
 * own worker pool (which acts as CONSUMER of its input and, via the
 * downstream forwarder, PRODUCER into the next stage — this dual role
 * is the whole point of a multi-stage pipeline).
 *
 * Forwarding to the next stage is done by directly calling the next
 * stage's submit(result) — synchronously, from inside this stage's own
 * worker thread — rather than through a second intermediate queue this
 * class would own. This keeps the design simple and gives a crucial
 * correctness property for free: because forward() is a direct,
 * synchronous call, by the time a worker thread has finished processing
 * (and this stage's shutdown() has observed all workers exit via the
 * CountDownLatch), every item that worker ever produced has ALREADY been
 * fully submitted into the downstream stage's own queue. There is no
 * separate relay/bridge thread whose own drain state would need
 * independent tracking — the orchestrator can safely call
 * upstream.shutdown() and then, only after that returns, downstream.shutdown(),
 * with a hard guarantee that nothing upstream produced is still "in
 * flight" and unaccounted for. This is precisely the ordering
 * StageExecutors.shutdownGracefully() (Phase 4) relies on informally;
 * here the guarantee is made structural rather than just "usually true".
 *
 * BOUNDED BUFFER / BACKPRESSURE: the input queue has a fixed capacity.
 * If this stage's workers fall behind, producers calling put() on a full
 * queue BLOCK — deliberate, real backpressure: it naturally slows an
 * upstream stage (whose own worker thread is blocked inside forward())
 * down to match this stage's actual processing rate, instead of an
 * unbounded buildup (Phase 5's PriorityBlockingQueue danger). Because
 * forward() blocks the UPSTREAM worker thread when the downstream queue
 * is full, backpressure naturally propagates all the way back up the
 * pipeline to the original producers — see BoundedBufferBackpressureDemo.
 *
 * POISON-PILL SHUTDOWN: shutdown() enqueues exactly `workerCount` poison
 * pills — one per worker — onto the input queue, then blocks until every
 * worker has actually exited. Because the queue is FIFO, every item
 * enqueued before shutdown() was called is guaranteed to be drained and
 * processed first; workers only ever see a poison pill once all
 * legitimate work ahead of it in the queue is gone.
 */
public abstract class PipelineStage<IN, OUT> {

    /** A downstream stage's submit(), or null for a terminal (sink) stage. */
    @FunctionalInterface
    public interface Downstream<OUT> {
        void submit(OUT value) throws InterruptedException;
    }

    private final String stageName;
    private final int workerCount;
    private final BlockingQueue<WorkItem<IN>> inputQueue;
    private final Downstream<OUT> downstream; // null for a terminal (sink) stage
    private final ExecutorService workerPool;
    private final CountDownLatch workersFinished;

    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    protected PipelineStage(String stageName,
                             int workerCount,
                             int inputQueueCapacity,
                             Downstream<OUT> downstream) {
        this.stageName = stageName;
        this.workerCount = workerCount;
        this.inputQueue = new ArrayBlockingQueue<>(inputQueueCapacity);
        this.downstream = downstream;
        this.workersFinished = new CountDownLatch(workerCount);
        this.workerPool = Executors.newFixedThreadPool(workerCount, r -> {
            Thread t = new Thread(r, stageName + "-worker-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            return t;
        });
    }

    /**
     * The actual per-item work this stage performs. A terminal (sink)
     * stage's OUT type is typically Void (always null) — that's fine:
     * "processed successfully" is defined as "returned without throwing",
     * not by the returned value's null-ness (see workerLoop — the
     * downstream-null check only gates FORWARDING, not counting). Throw
     * for real rejections, counted via rejectedCount() instead.
     */
    protected abstract OUT process(IN input) throws Exception;

    public void start() {
        for (int i = 0; i < workerCount; i++) {
            workerPool.execute(this::workerLoop);
        }
    }

    private void workerLoop() {
        try {
            while (true) {
                WorkItem<IN> item;
                try {
                    item = inputQueue.take(); // blocks until work OR a poison pill arrives
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (item instanceof WorkItem.PoisonPill) {
                    return; // this worker is done — exactly one pill consumed per worker
                }

                IN value = ((WorkItem.Data<IN>) item).value();
                try {
                    OUT result = process(value);
                    processedCount.incrementAndGet(); // no exception thrown = processed
                    if (downstream != null) {
                        downstream.submit(result); // may BLOCK here — real backpressure, see class javadoc
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                    System.out.println("  [" + stageName + "] REJECTED item: " + e.getMessage());
                }
            }
        } finally {
            workersFinished.countDown();
        }
    }

    /** Producers (or an upstream stage) call this — blocks (real backpressure) if the input queue is full. */
    public void submit(IN item) throws InterruptedException {
        inputQueue.put(WorkItem.of(item));
    }

    /**
     * Enqueues one poison pill per worker, then blocks until every
     * worker has drained everything ahead of its pill and exited. Only
     * call this once producers/upstream for this stage are done
     * submitting. See class javadoc for why it's then safe to
     * immediately shut the NEXT stage down too.
     */
    public void shutdown(long timeoutSeconds) throws InterruptedException {
        for (int i = 0; i < workerCount; i++) {
            inputQueue.put(WorkItem.PoisonPill.instance());
        }
        boolean finished = workersFinished.await(timeoutSeconds, TimeUnit.SECONDS);
        workerPool.shutdown();
        if (!finished) {
            System.out.println("  [" + stageName + "] WARNING: workers did not all finish within timeout");
        }
        System.out.println("  [" + stageName + "] shut down. processed=" + processedCount.get()
                + " rejected=" + rejectedCount.get());
    }

    public int queueDepth() {
        return inputQueue.size();
    }

    public long processedCount() {
        return processedCount.get();
    }

    public long rejectedCount() {
        return rejectedCount.get();
    }

    public String name() {
        return stageName;
    }
}
