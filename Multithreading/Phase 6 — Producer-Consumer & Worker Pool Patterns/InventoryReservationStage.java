package com.orderengine.phase6;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.orderengine.phase6.Domain.InsufficientStockException;
import com.orderengine.phase6.Domain.ReservedOrder;
import com.orderengine.phase6.Domain.ValidatedOrder;

/**
 * Second real stage of the pipeline. All of this stage's worker threads
 * share ONE InventoryLedger instance — this is the multi-consumer
 * concurrency case in its most direct form: several worker threads
 * calling reserve() concurrently against the SAME shared mutable state,
 * exactly Phase 2's InventoryLedger problem, solved here with
 * ConcurrentHashMap.compute() (Phase 5's atomic-compound-operation
 * pattern) rather than re-deriving a lock-based ledger from scratch —
 * this is deliberate: it shows the two phases' techniques composing,
 * not living in isolation.
 */
public class InventoryReservationStage extends PipelineStage<ValidatedOrder, ReservedOrder> {

    /** A minimal, self-contained stand-in for Phase 2's full InventoryLedger. */
    static final class Ledger {
        private final ConcurrentHashMap<String, Integer> stock;

        Ledger(Map<String, Integer> initialStock) {
            this.stock = new ConcurrentHashMap<>(initialStock);
        }

        /** Atomic check-and-decrement via compute() — see Phase 5 §2 for why this must be one call. */
        boolean reserve(String productId, int quantity) {
            boolean[] success = {false};
            stock.compute(productId, (id, current) -> {
                int available = current == null ? 0 : current;
                if (available >= quantity) {
                    success[0] = true;
                    return available - quantity;
                }
                return current;
            });
            return success[0];
        }
    }

    private final Ledger ledger;

    public InventoryReservationStage(int workerCount, int queueCapacity,
                                      Downstream<ReservedOrder> downstream,
                                      Map<String, Integer> initialStock) {
        super("inventory-reservation", workerCount, queueCapacity, downstream);
        this.ledger = new Ledger(initialStock);
    }

    @Override
    protected ReservedOrder process(ValidatedOrder order) throws InsufficientStockException {
        boolean reserved = ledger.reserve(order.productId(), order.quantity());
        if (!reserved) {
            throw new InsufficientStockException(
                    "order " + order.orderId() + " — insufficient stock for " + order.productId());
        }
        return new ReservedOrder(order.orderId(), order.productId(), order.quantity(), order.amount());
    }
}
