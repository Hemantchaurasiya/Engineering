package com.orderengine.capstone;

public class EngineDemo {
    public static void main(String[] args) throws Exception {
        EngineConfig config = EngineConfig.defaultConfig();
        System.out.println("Config: validationWorkers=" + config.validationWorkers
                + " reservationWorkers=" + config.reservationWorkers
                + " paymentBulkheadSize=" + config.paymentBulkheadSize
                + " (derived from Phase 4's CPU-bound/I-O-bound formulas, not hardcoded)\n");
        System.out.println("While this runs, try: curl http://localhost:" + config.dashboardPort + "/metrics\n");

        OrderProcessingEngine engine = new OrderProcessingEngine(config);
        engine.run(5000, 8);
    }
}
