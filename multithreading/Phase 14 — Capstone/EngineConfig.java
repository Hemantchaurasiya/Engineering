package com.orderengine.capstone;

/**
 * Config-driven tuning — every pool/limit size below is DERIVED, not a
 * hardcoded "looks about right" number, exactly Phase 4's discipline:
 *
 *   validationWorkers / reservationWorkers : CPU-bound -> Ncpu + 1
 *   paymentBulkheadSize                     : I/O-bound -> Phase 4's
 *                                              wait/compute formula,
 *                                              assuming ~4ms compute vs
 *                                              ~80ms average gateway wait
 *   notification stage                      : uses Phase 9 virtual
 *                                              threads instead — no pool
 *                                              size needed at all, the
 *                                              single biggest simplification
 *                                              this capstone demonstrates
 *                                              versus an all-platform-thread
 *                                              design
 */
public final class EngineConfig {

    public final int validationWorkers;
    public final int reservationWorkers;
    public final int paymentBulkheadSize;
    public final int inputQueueCapacity;
    public final double paymentRateLimitPerSecond;
    public final int dashboardPort;

    public static EngineConfig defaultConfig() {
        int ncpu = Runtime.getRuntime().availableProcessors();
        int cpuBoundWorkers = ncpu + 1;
        int paymentBulkhead = Math.max(4, (int) Math.ceil(ncpu * 0.9 * (1 + 80.0 / 4.0)));
        return new EngineConfig(cpuBoundWorkers, cpuBoundWorkers, paymentBulkhead, 200, 40.0, 8090);
    }

    public EngineConfig(int validationWorkers, int reservationWorkers, int paymentBulkheadSize,
                         int inputQueueCapacity, double paymentRateLimitPerSecond, int dashboardPort) {
        this.validationWorkers = validationWorkers;
        this.reservationWorkers = reservationWorkers;
        this.paymentBulkheadSize = paymentBulkheadSize;
        this.inputQueueCapacity = inputQueueCapacity;
        this.paymentRateLimitPerSecond = paymentRateLimitPerSecond;
        this.dashboardPort = dashboardPort;
    }
}
