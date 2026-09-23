package com.orderengine.phase13;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A general-purpose load generator: ramps concurrency in stages against
 * a target operation, using Phase 9's virtual-thread-per-task model
 * (cheap enough that "launch thousands of concurrent callers" needs no
 * pool-sizing calculation of its own — the load generator's OWN
 * mechanics shouldn't become the bottleneck being measured), recording
 * throughput and latency into a MetricsRegistry the whole time so a live
 * HealthDashboardServer can show real-time load-test progress rather
 * than only a final summary.
 *
 * HONESTY NOTE, in the same spirit as this project's benchmark
 * disclaimers since Phase 2: this is a load-testing TOOL for exploring
 * this project's own components interactively, not a substitute for
 * proper tools. For real production capacity planning or performance
 * regression testing you'd reach for purpose-built tools: JMH for
 * precise microbenchmarks of small units of code (with proper warmup
 * iteration handling, dead-code-elimination protection, and statistical
 * rigor this hand-rolled loop doesn't attempt), or Gatling/k6/Locust for
 * full-scale, realistic load testing against a running service over the
 * network with proper ramp profiles, correlated request chains, and
 * distributed load generation. This class's honest scope: a convenient
 * way to drive concurrent load against in-process Java code and watch
 * this project's own metrics/dashboard machinery respond to it.
 */
public class StressTestHarness {

    private final MetricsRegistry metrics;

    public StressTestHarness(MetricsRegistry metrics) {
        this.metrics = metrics;
    }

    /**
     * Runs {@code totalCalls} invocations of {@code operation} using up
     * to {@code maxConcurrency} concurrent virtual threads, recording a
     * "{metricPrefix}.calls" counter, a "{metricPrefix}.errors" counter,
     * and a "{metricPrefix}.latency" histogram into the shared registry
     * as it goes — visible live on the dashboard while the run is still
     * in progress, not just after it completes.
     */
    public void run(String metricPrefix, int totalCalls, int maxConcurrency,
                     Callable<?> operation) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(totalCalls);
        AtomicLong completed = new AtomicLong(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // A semaphore-free approach to bounding concurrency would
            // just fire all totalCalls at once; capping at maxConcurrency
            // here instead models a more realistic "sustained load at a
            // target concurrency" shape rather than one giant instantaneous
            // burst — using Phase 10's Semaphore for exactly this purpose.
            Semaphore concurrencyLimit = new Semaphore(maxConcurrency);

            for (int i = 0; i < totalCalls; i++) {
                concurrencyLimit.acquire();
                executor.submit(() -> {
                    long start = System.currentTimeMillis();
                    try {
                        operation.call();
                        metrics.incrementCounter(metricPrefix + ".calls");
                    } catch (Exception e) {
                        metrics.incrementCounter(metricPrefix + ".errors");
                    } finally {
                        long elapsed = System.currentTimeMillis() - start;
                        metrics.recordLatency(metricPrefix + ".latency", elapsed);
                        concurrencyLimit.release();
                        completed.incrementAndGet();
                        done.countDown();
                    }
                });
            }
            done.await();
        }

        System.out.println("Stress test '" + metricPrefix + "' complete: " + completed.get()
                + "/" + totalCalls + " calls finished.");
        LatencyHistogram.Snapshot snapshot = metrics.latencySnapshot(metricPrefix + ".latency");
        System.out.printf("  p50=%dms p95=%dms p99=%dms max=%dms (over last %d samples)%n",
                snapshot.p50(), snapshot.p95(), snapshot.p99(), snapshot.max(), snapshot.sampleCount());
    }
}
