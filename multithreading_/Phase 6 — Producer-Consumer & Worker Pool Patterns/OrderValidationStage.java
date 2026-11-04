package com.orderengine.phase6;

import com.orderengine.phase6.Domain.Order;
import com.orderengine.phase6.Domain.ValidatedOrder;
import com.orderengine.phase6.Domain.ValidationException;

/**
 * First real stage of the pipeline. Multiple ingestion "producer" threads
 * (simulated in PipelineDemo as several concurrent client threads) submit
 * Orders here; multiple VALIDATION WORKER threads consume them
 * concurrently from the same shared input queue — this is the
 * "multiple producers, multiple consumers sharing one bounded buffer"
 * shape this whole phase is about, not just a single producer/single
 * consumer toy example.
 *
 * Validation itself is intentionally simple (a few field checks) — the
 * point of this class is the concurrency wiring around it (inherited
 * from PipelineStage), not the business rules.
 */
public class OrderValidationStage extends PipelineStage<Order, ValidatedOrder> {

    public OrderValidationStage(int workerCount, int queueCapacity,
                                 Downstream<ValidatedOrder> downstream) {
        super("validation", workerCount, queueCapacity, downstream);
    }

    @Override
    protected ValidatedOrder process(Order order) throws ValidationException {
        if (order.quantity() <= 0) {
            throw new ValidationException("order " + order.orderId() + " has non-positive quantity");
        }
        if (order.amount() <= 0) {
            throw new ValidationException("order " + order.orderId() + " has non-positive amount");
        }
        if (order.productId() == null || order.productId().isBlank()) {
            throw new ValidationException("order " + order.orderId() + " missing productId");
        }
        // Simulate real validation work (business-rule checks, parsing) so
        // the demo's queue depth / backpressure behavior is observable
        // instead of instantaneous.
        busyWork();
        return new ValidatedOrder(order.orderId(), order.productId(), order.quantity(), order.amount());
    }

    private void busyWork() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
