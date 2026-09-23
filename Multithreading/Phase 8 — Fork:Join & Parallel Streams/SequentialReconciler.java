package com.orderengine.phase8;

import java.util.ArrayList;
import java.util.List;

import com.orderengine.phase8.Domain.Mismatch;
import com.orderengine.phase8.Domain.ReconciliationRecord;

/** Plain, single-threaded baseline — the correctness reference every other
 *  implementation in this phase is checked against, and the performance
 *  reference every "parallel" implementation must actually beat to justify
 *  its added complexity. */
public class SequentialReconciler {

    public List<Mismatch> reconcile(List<ReconciliationRecord> records) {
        List<Mismatch> mismatches = new ArrayList<>();
        for (ReconciliationRecord record : records) {
            if (Domain.isMismatch(record)) {
                mismatches.add(new Mismatch(record.orderId(), record.orderAmount(),
                        record.ledgerAmount(), record.orderAmount() - record.ledgerAmount()));
            }
        }
        return mismatches;
    }
}
