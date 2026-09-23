package com.orderengine.phase10;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real startup requirement: every pipeline stage's worker pool must be
 * fully initialized — threads created, any warm-up work done — BEFORE
 * order ingestion begins, and every stage should ideally start accepting
 * work at the same moment rather than in a staggered, arbitrary order
 * (which could let early orders queue up disproportionately against a
 * stage that just happens to initialize slower than its neighbors).
 *
 * This is the classic DUAL CountDownLatch pattern, worth knowing by name
 * because it comes up constantly in real startup/test-harness code:
 *
 *   readySignal  (count = number of stages): each stage counts this DOWN
 *                once IT is ready. The coordinator awaits this reaching
 *                zero — i.e., waits for ALL stages to report ready.
 *
 *   startSignal  (count = 1): the coordinator counts this DOWN exactly
 *                once, after readySignal has reached zero. Every stage
 *                is meanwhile blocked awaiting THIS latch — so they all
 *                unblock at effectively the same instant, once the
 *                single countDown() fires.
 *
 * A CountDownLatch is intentionally ONE-SHOT: once its count reaches
 * zero, it stays open forever — await() returns immediately for any
 * future caller, and countDown() past zero is a harmless no-op. This
 * makes it exactly right for "wait for N one-time events" (startup) but
 * WRONG for anything that needs to reset and repeat across multiple
 * rounds — that's what CyclicBarrier exists for instead (see
 * BatchPhaseCyclicBarrierDemo in this same phase).
 */
public class StartupCoordinator {

    private final CountDownLatch readySignal;
    private final CountDownLatch startSignal = new CountDownLatch(1);
    private final AtomicInteger readyCount = new AtomicInteger(0);

    public StartupCoordinator(int stageCount) {
        this.readySignal = new CountDownLatch(stageCount);
    }

    /** Called once by each stage's initialization logic when it's fully ready. */
    public void signalStageReady(String stageName) {
        int count = readyCount.incrementAndGet();
        readySignal.countDown();
        System.out.println("  [" + stageName + "] ready (" + count + " stages ready so far)");
    }

    /** Called by each stage's worker threads — blocks until ALL stages are ready AND start() has been called. */
    public void awaitStart(String stageName) throws InterruptedException {
        startSignal.await();
        System.out.println("  [" + stageName + "] released — beginning work");
    }

    /** Called once by the coordinator/orchestrator: waits for every stage to report ready, then releases everyone at once. */
    public boolean startWhenAllReady(long timeoutSeconds) throws InterruptedException {
        boolean allReady = readySignal.await(timeoutSeconds, TimeUnit.SECONDS);
        if (!allReady) {
            System.out.println("  TIMEOUT waiting for all stages to become ready — not starting");
            return false;
        }
        System.out.println("  all stages ready — releasing startSignal for everyone simultaneously");
        startSignal.countDown(); // fires once; every waiting stage unblocks together
        return true;
    }
}
