package com.orderengine.phase8;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * End-of-day reconciliation: compare each order's recorded amount
 * against the ledger's recorded amount for that same order, flagging
 * mismatches (a real operational need — catching double-charges, missed
 * refunds, rounding drift, or data-sync bugs between order-service and
 * the ledger). This is classic CPU-bound, embarrassingly-parallel,
 * divide-and-conquer work: each record's comparison is fully independent
 * of every other record's, with no shared mutable state to coordinate —
 * exactly the shape fork/join and parallel streams are built for, unlike
 * Phase 7's I/O-bound async work.
 */
public final class Domain {
    private Domain() {}

    public record ReconciliationRecord(long orderId, double orderAmount, double ledgerAmount) {}

    public record Mismatch(long orderId, double orderAmount, double ledgerAmount, double difference) {}

    public static List<ReconciliationRecord> generateDataset(int size, double mismatchRate) {
        List<ReconciliationRecord> records = new ArrayList<>(size);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (long i = 0; i < size; i++) {
            double orderAmount = 10 + rnd.nextDouble() * 490;
            double ledgerAmount = orderAmount;
            if (rnd.nextDouble() < mismatchRate) {
                ledgerAmount += rnd.nextDouble() * 20 - 10; // inject a real mismatch
            }
            records.add(new ReconciliationRecord(i, round2(orderAmount), round2(ledgerAmount)));
        }
        return records;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Simulates the actual per-record comparison cost — deliberately non-trivial so
     *  splitting overhead has real work to be worth amortizing against. */
    public static boolean isMismatch(ReconciliationRecord record) {
        // A bit of artificial CPU work standing in for a real comparison
        // that might involve currency rounding rules, tolerance bands,
        // etc. — kept deterministic and side-effect-free.
        double diff = Math.abs(record.orderAmount() - record.ledgerAmount());
        for (int i = 0; i < 50; i++) {
            diff = Math.sqrt(diff * diff + 0.0001) - Math.sqrt(0.0001); // busywork, converges back near diff
        }
        return diff > 0.005;
    }
}
