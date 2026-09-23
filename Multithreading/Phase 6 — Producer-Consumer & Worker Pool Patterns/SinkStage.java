package com.orderengine.phase6;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.orderengine.phase6.Domain.ReservedOrder;

/**
 * Terminal stage — no output queue (passes null to the PipelineStage
 * constructor), just records what came through. In the real engine this
 * would be where payment/notification stages (Phases 7+) pick up instead;
 * for this phase, a simple sink is enough to prove end-to-end flow
 * through two real concurrent stages plus itself.
 */
public class SinkStage extends PipelineStage<ReservedOrder, Void> {

    private final List<ReservedOrder> collected = new CopyOnWriteArrayList<>(); // Phase 5's read-light/write-light-but-concurrent list

    public SinkStage(int workerCount, int queueCapacity) {
        super("sink", workerCount, queueCapacity, null);
    }

    @Override
    protected Void process(ReservedOrder order) {
        collected.add(order);
        return null; // nothing to forward — this is the end of the line
    }

    public List<ReservedOrder> collected() {
        return List.copyOf(collected);
    }
}
