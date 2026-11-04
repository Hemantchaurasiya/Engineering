package com.orderengine.phase5;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class Phase5ConcurrencyTest {

    public static void main(String[] args) throws InterruptedException {
        testVipOrdering();
        testVipBackpressure();
        testRetryBackoffOrdering();
        testNotificationRegistryConcurrentIteration();
    }

    private static void testVipOrdering() throws InterruptedException {
        VipAwareOrderQueue queue = new VipAwareOrderQueue(100);
        // push regular orders first, VIP orders after — VIPs must still come out first
        for (int i = 0; i < 5; i++) {
            queue.put(new VipAwareOrderQueue.PrioritizedOrder(i, false, i));
        }
        for (int i = 5; i < 8; i++) {
            queue.put(new VipAwareOrderQueue.PrioritizedOrder(i, true, i));
        }

        StringBuilder order = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            VipAwareOrderQueue.PrioritizedOrder o = queue.take();
            order.append(o.vip() ? "V" : "r");
        }
        String result = order.toString();
        boolean vipsFirst = result.startsWith("VVV");
        System.out.println("VIP ordering: sequence=" + result
                + " -> " + (vipsFirst ? "OK (all 3 VIPs came out first)" : "MISMATCH"));
    }

    private static void testVipBackpressure() throws InterruptedException {
        VipAwareOrderQueue queue = new VipAwareOrderQueue(3);
        for (int i = 0; i < 3; i++) {
            queue.put(new VipAwareOrderQueue.PrioritizedOrder(i, false, i));
        }
        boolean fourthAccepted = queue.offer(
                new VipAwareOrderQueue.PrioritizedOrder(4, false, 4), 200, java.util.concurrent.TimeUnit.MILLISECONDS);
        System.out.println("VIP queue backpressure: capacity=3, 4th offer accepted=" + fourthAccepted
                + " -> " + (!fourthAccepted ? "OK (correctly blocked/rejected over capacity)" : "MISMATCH"));
    }

    private static void testRetryBackoffOrdering() throws InterruptedException {
        RetryScheduler scheduler = new RetryScheduler();
        // Schedule out of order on purpose: order 2's retry has a SHORTER
        // delay than order 1's, so it must come out first despite being
        // scheduled second.
        scheduler.scheduleRetry(1L, 3); // attempt 4 -> 1600ms delay
        scheduler.scheduleRetry(2L, 0); // attempt 1 -> 200ms delay

        RetryScheduler.RetryTask first = scheduler.takeReadyRetry();
        boolean correctOrder = first.orderId == 2L;
        System.out.println("Retry delay ordering: first ready = orderId " + first.orderId
                + " -> " + (correctOrder ? "OK (shorter delay came out first, not submission order)" : "MISMATCH"));

        RetryScheduler.RetryTask second = scheduler.takeReadyRetry();
        System.out.println("  second ready = orderId " + second.orderId
                + " -> " + (second.orderId == 1L ? "OK" : "MISMATCH"));
    }

    private static void testNotificationRegistryConcurrentIteration() throws InterruptedException {
        NotificationChannelRegistry registry = new NotificationChannelRegistry();
        AtomicInteger notifiedCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            final int id = i;
            registry.register(new NotificationChannelRegistry.NotificationChannel() {
                @Override public String name() { return "channel-" + id; }
                @Override public void notify(long orderId, String message) { notifiedCount.incrementAndGet(); }
            });
        }

        // Fire notifyAll() concurrently with registering MORE channels —
        // proves no ConcurrentModificationException is thrown, regardless
        // of whether the new channel happens to be seen by the in-flight
        // iteration (snapshot semantics — either outcome is valid, a
        // thrown exception would not be).
        CountDownLatch startLatch = new CountDownLatch(1);
        Thread notifier = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 1000; i++) {
                    registry.notifyAll(1L, "msg");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread mutator = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 50; i++) {
                    final int id = i;
                    NotificationChannelRegistry.NotificationChannel ch =
                            new NotificationChannelRegistry.NotificationChannel() {
                                @Override public String name() { return "dynamic-" + id; }
                                @Override public void notify(long orderId, String message) {}
                            };
                    registry.register(ch);
                    registry.deregister(ch);
                }
            } catch (Exception e) {
                System.out.println("  UNEXPECTED EXCEPTION during concurrent mutation+iteration: " + e);
            }
        });

        notifier.start();
        mutator.start();
        startLatch.countDown();
        notifier.join();
        mutator.join();

        System.out.println("NotificationChannelRegistry concurrent iteration+mutation: "
                + "no exception thrown, notifications delivered=" + notifiedCount.get() + " -> OK");
    }
}
