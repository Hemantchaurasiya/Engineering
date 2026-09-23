package com.orderengine.phase11;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A minimal account with its own lock, standing in for something like
 * two InventoryLedger entries (Phase 2) that need a transfer operation
 * moving stock/balance from one to the other — a genuinely common real
 * operation (rebalancing stock between warehouses, refund reversals
 * between two ledger entries) that inherently needs TWO locks held at
 * once, which is exactly the shape that creates deadlock risk.
 */
public class Account {
    final int id;
    private final ReentrantLock lock = new ReentrantLock();
    private double balance;

    public Account(int id, double initialBalance) {
        this.id = id;
        this.balance = initialBalance;
    }

    public ReentrantLock lock() {
        return lock;
    }

    public double balance() {
        return balance;
    }

    public void adjust(double delta) {
        balance += delta;
    }

    public int id() {
        return id;
    }
}
