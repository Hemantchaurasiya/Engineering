package com.orderengine.phase2;

import java.util.HashMap;
import java.util.Map;

/**
 * Sanity check that all three implementations are equally CORRECT — the
 * benchmark only measures speed, and speed is worthless if any version
 * loses updates. Each thread alternates reserve(1) / release(1) on the
 * same product many times; net effect should be zero change, so final
 * stock must exactly equal initial stock. Any implementation that prints
 * MISMATCH has a real bug.
 */
public class LedgerCorrectnessTest {

    private static final String PRODUCT = "product-0";
    private static final int INITIAL_STOCK = 10_000;
    private static final int THREADS = 16;
    private static final int CYCLES_PER_THREAD = 20_000;

    public static void main(String[] args) throws InterruptedException {
        check("synchronized", new InventoryLedgerSynchronized(seed()));
        check("ReadWriteLock", new InventoryLedgerReadWriteLock(seed()));
        check("StampedLock", new InventoryLedgerStamped(seed()));
    }

    private static void check(String label, InventoryLedger ledger) throws InterruptedException {
        Thread[] threads = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < CYCLES_PER_THREAD; i++) {
                    boolean reserved = ledger.reserve(PRODUCT, 1);
                    if (reserved) {
                        ledger.release(PRODUCT, 1);
                    }
                }
            });
        }
        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        int finalStock = ledger.checkAvailability(PRODUCT);
        String verdict = finalStock == INITIAL_STOCK ? "OK" : "MISMATCH";
        System.out.printf("%-16s final=%d expected=%d -> %s%n",
                label, finalStock, INITIAL_STOCK, verdict);
    }

    private static Map<String, Integer> seed() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put(PRODUCT, INITIAL_STOCK);
        return stock;
    }
}
