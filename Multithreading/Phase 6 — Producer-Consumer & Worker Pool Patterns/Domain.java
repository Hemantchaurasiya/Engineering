package com.orderengine.phase6;

/**
 * Self-contained domain types for this phase (this codebase compiles
 * each phase's source directory independently — see the run instructions
 * in the phase notes — so phase6 doesn't import Phase 1/2's Order or
 * InventoryLedger classes; it defines its own minimal equivalents here,
 * with comments pointing back to where the fuller version lives).
 */
public final class Domain {

    private Domain() {}

    /** Mirrors Phase 1's Order — an immutable unit of work. */
    public record Order(long orderId, String productId, int quantity, double amount) {}

    /** Produced by OrderValidationStage once an Order passes validation. */
    public record ValidatedOrder(long orderId, String productId, int quantity, double amount) {}

    /** Produced by InventoryReservationStage once stock is reserved. */
    public record ReservedOrder(long orderId, String productId, int quantity, double amount) {}

    public static final class ValidationException extends Exception {
        public ValidationException(String message) { super(message); }
    }

    public static final class InsufficientStockException extends Exception {
        public InsufficientStockException(String message) { super(message); }
    }
}
