package com.orderengine.phase6;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.orderengine.phase6.Domain.Order;

/**
 * Wires validation -> reservation -> sink into one running pipeline and
 * proves the whole chain end-to-end: MULTIPLE producer threads submit
 * orders concurrently into validation's shared input queue; validation's
 * MULTIPLE workers consume concurrently and forward directly into
 * reservation's submit(); reservation's MULTIPLE workers consume
 * concurrently (all sharing one Ledger — the multi-consumer-on-shared-
 * state case) and forward into the sink; the sink collects everything.
 *
 * Then shuts the whole pipeline down cleanly, stage by stage, in
 * pipeline order — mirroring Phase 4's StageExecutors.shutdownGracefully()
 * front-to-back rationale, and made SAFE here structurally (not just by
 * convention) by PipelineStage's direct-downstream-call forwarding — see
 * that class's javadoc for exactly why calling validationStage.shutdown()
 * fully drains it before reservationStage.shutdown() is ever called.
 */
public class PipelineDemo {

    private static final int TOTAL_ORDERS = 2000;
    private static final int PRODUCER_THREADS = 6;

    public static void main(String[] args) throws InterruptedException {
        SinkStage sinkStage = new SinkStage(4, 100);

        InventoryReservationStage reservationStage = new InventoryReservationStage(
                4, 100, sinkStage::submit,
                Map.of("widget", 5000, "gadget", 300)); // gadget deliberately under-stocked -> real rejections

        OrderValidationStage validationStage = new OrderValidationStage(
                4, 100, reservationStage::submit);

        reservationStage.start();
        validationStage.start();
        sinkStage.start();

        System.out.println("Pipeline started. Submitting " + TOTAL_ORDERS + " orders via "
                + PRODUCER_THREADS + " concurrent producer threads...\n");

        AtomicLong nextOrderId = new AtomicLong(1);
        Thread[] producers = new Thread[PRODUCER_THREADS];
        for (int p = 0; p < PRODUCER_THREADS; p++) {
            producers[p] = new Thread(() -> {
                while (true) {
                    long id = nextOrderId.getAndIncrement();
                    if (id > TOTAL_ORDERS) return;
                    String product = (id % 5 == 0) ? "gadget" : "widget"; // 20% gadget traffic -> exhausts stock
                    Order order = new Order(id, product, 1 + (int) (id % 3), 19.99);
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
        for (Thread p : producers) p.start();
        for (Thread p : producers) p.join();
        System.out.println("All " + TOTAL_ORDERS + " orders submitted by producers in "
                + (System.currentTimeMillis() - start) + "ms.\n");

        // Shut down front-to-back: validation first (no more input arrives
        // once producers are done), which fully drains into reservation
        // BEFORE validationStage.shutdown() returns (see PipelineStage
        // javadoc) — so it's safe to shut reservation down next, and then
        // the sink last, with a hard structural guarantee nothing is lost
        // mid-flight between stages.
        System.out.println("Shutting down stage by stage, pipeline order...");
        validationStage.shutdown(15);
        reservationStage.shutdown(15);
        sinkStage.shutdown(15);

        System.out.println("\nFinal results:");
        System.out.println("  validation   processed=" + validationStage.processedCount()
                + " rejected=" + validationStage.rejectedCount());
        System.out.println("  reservation  processed=" + reservationStage.processedCount()
                + " rejected=" + reservationStage.rejectedCount());
        System.out.println("  sink         collected=" + sinkStage.collected().size());
        System.out.println("  (rejected orders at reservation = orders for the deliberately "
                + "under-stocked 'gadget' product once its 300 units run out — a REAL business "
                + "rejection, not a bug; expect processed+rejected to equal validation's processed "
                + "count exactly, and sink.collected() to equal reservation's processed count exactly)");
    }
}
