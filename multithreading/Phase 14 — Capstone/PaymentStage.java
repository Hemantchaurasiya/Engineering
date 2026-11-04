package com.orderengine.capstone;

import java.util.concurrent.ThreadLocalRandom;

import com.orderengine.capstone.Domain.Order;
import com.orderengine.capstone.Domain.OrderResult;

/**
 * I/O-bound: uses AsyncIoStage's virtual-thread dispatch (Phase 9)
 * instead of a sized platform pool, guarded by a CircuitBreaker (Phase
 * 12) around the simulated gateway call — combining two phases'
 * techniques the way a real production stage would.
 */
public class PaymentStage extends AsyncIoStage<Order, OrderResult> {

    private final CircuitBreaker circuitBreaker = new CircuitBreaker(20, 0.5, 1000);

    public PaymentStage(int queueCapacity, int maxConcurrentCalls,
                         PipelineStage.Downstream<OrderResult> downstream, Metrics metrics) {
        super("payment", queueCapacity, maxConcurrentCalls, downstream, metrics);
    }

    @Override
    protected OrderResult process(Order order) throws Exception {
        String txnId = circuitBreaker.call(() -> simulateGatewayCall(order));
        return new OrderResult(order.orderId(), "CHARGED", txnId);
    }

    private String simulateGatewayCall(Order order) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(20, 80)); // simulated network wait
        if (ThreadLocalRandom.current().nextDouble() < 0.03) {
            throw new RuntimeException("simulated gateway failure for order " + order.orderId());
        }
        return "txn-" + order.orderId();
    }
}
