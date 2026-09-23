package com.orderengine.capstone;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.orderengine.capstone.Domain.Order;

/**
 * The full engine: every phase's component, wired together.
 *
 *   startup   -> Phase 10's dual-latch StartupCoordinator: every stage
 *                signals ready, then every producer is released at once.
 *   pipeline  -> validation (Phase 4 CPU-bound sizing, Phase 6 poison
 *                pill) -> reservation (Phase 2/5 atomic ledger) ->
 *                payment (Phase 9 virtual threads, Phase 12 circuit
 *                breaker) -> notification (Phase 9 virtual threads,
 *                Phase 5 CopyOnWriteArrayList collection).
 *   metrics   -> Phase 3 LongAdder counters, Phase 13 live HTTP dashboard,
 *                reachable the ENTIRE time the engine runs below.
 *   shutdown  -> Phase 6's front-to-back ordering: validation first,
 *                notification last, each stage's shutdown() blocking
 *                until it has genuinely finished (Phase 6 §4's
 *                structural guarantee for the synchronous stages, this
 *                capstone's AsyncIoStage in-flight drain for the
 *                virtual-thread stages).
 */
public class OrderProcessingEngine {

    private final EngineConfig config;
    private final Metrics metrics = new Metrics();

    private ValidationStage validationStage;
    private ReservationStage reservationStage;
    private PaymentStage paymentStage;
    private NotificationStage notificationStage;

    public OrderProcessingEngine(EngineConfig config) {
        this.config = config;
    }

    public void run(int totalOrders, int producerThreads) throws Exception {
        metrics.startDashboard(config.dashboardPort);

        InventoryLedger ledger = new InventoryLedger(Map.of("widget", 1_000_000, "gadget", 1_000_000));

        notificationStage = new NotificationStage(config.inputQueueCapacity, config.paymentBulkheadSize, metrics);
        paymentStage = new PaymentStage(config.inputQueueCapacity, config.paymentBulkheadSize,
                notificationStage::submit, metrics);
        reservationStage = new ReservationStage(config.reservationWorkers, config.inputQueueCapacity,
                paymentStage::submit, metrics, ledger);
        validationStage = new ValidationStage(config.validationWorkers, config.inputQueueCapacity,
                reservationStage::submit, metrics);

        StartupCoordinator coordinator = new StartupCoordinator(4);
        validationStage.start();
        coordinator.signalStageReady("validation");
        reservationStage.start();
        coordinator.signalStageReady("reservation");
        paymentStage.start();
        coordinator.signalStageReady("payment");
        notificationStage.start();
        coordinator.signalStageReady("notification");

        System.out.println("\nAll stages ready — releasing producers...\n");
        boolean started = coordinator.startWhenAllReady(5);
        if (!started) throw new IllegalStateException("stages failed to become ready in time");

        AtomicLong nextOrderId = new AtomicLong(1);
        Thread[] producers = new Thread[producerThreads];
        for (int p = 0; p < producerThreads; p++) {
            producers[p] = new Thread(() -> {
                try {
                    coordinator.awaitStart();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (true) {
                    long id = nextOrderId.getAndIncrement();
                    if (id > totalOrders) return;
                    Order order = new Order(id, (id % 4 == 0) ? "gadget" : "widget", 1, 29.99);
                    try {
                        validationStage.submit(order);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "producer-" + p);
        }

        long start = System.currentTimeMillis();
        for (Thread t : producers) t.start();
        for (Thread t : producers) t.join();
        System.out.println(totalOrders + " orders submitted by " + producerThreads
                + " producers in " + (System.currentTimeMillis() - start) + "ms\n");

        System.out.println("Shutting down front-to-back...");
        validationStage.shutdown(15);
        reservationStage.shutdown(15);
        paymentStage.shutdown(15);
        notificationStage.shutdown(15);

        System.out.println("\nFinal metrics snapshot:");
        metrics.snapshot().forEach((name, value) -> System.out.println("  " + name + " = " + value));
        System.out.println("\nnotification collected count: " + notificationStage.collectedCount());

        Thread.sleep(1000); // leave the dashboard reachable briefly for a final manual check
        metrics.stopDashboard();
        System.out.println("Engine stopped.");
    }
}
