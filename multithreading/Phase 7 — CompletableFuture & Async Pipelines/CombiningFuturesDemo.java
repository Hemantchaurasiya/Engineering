package com.orderengine.phase7;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CombiningFuturesDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== thenCompose: dependent chain (flattens nested futures) ===");
        demoThenCompose();

        System.out.println("\n=== thenCombine: two INDEPENDENT futures run in parallel, merged ===");
        demoThenCombine();

        System.out.println("\n=== allOf: wait for a whole batch of independent futures ===");
        demoAllOf();

        System.out.println("\n=== anyOf: react to whichever of several futures finishes FIRST ===");
        demoAnyOf();
    }

    private static void demoThenCompose() throws Exception {
        // fetchUserId() -> fetchUserProfile(id) : the SECOND call needs
        // the FIRST call's result as input. thenCompose ("flatMap" for
        // futures) keeps this a single flat CompletableFuture<Profile>
        // instead of a CompletableFuture<CompletableFuture<Profile>> —
        // using thenApply here BY MISTAKE (a very common error) would
        // produce exactly that doubly-nested, awkward-to-use type,
        // because thenApply doesn't know the function it's given returns
        // a future itself; it would just wrap it as the outer future's
        // result type.
        CompletableFuture<String> result = fetchUserId()
                .thenCompose(CombiningFuturesDemo::fetchUserProfile);
        System.out.println("  thenCompose result: " + result.get());
    }

    private static CompletableFuture<Integer> fetchUserId() {
        return CompletableFuture.supplyAsync(() -> {
            sleep(50);
            return 42;
        });
    }

    private static CompletableFuture<String> fetchUserProfile(int userId) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(50);
            return "profile-for-user-" + userId;
        });
    }

    private static void demoThenCombine() throws Exception {
        // Two calls with NO dependency on each other — start both
        // immediately, let them run genuinely concurrently, combine only
        // once BOTH are done. Total time should be close to
        // max(callA, callB), not callA + callB — proving they actually
        // ran in parallel, not sequentially.
        long start = System.currentTimeMillis();
        CompletableFuture<Integer> inventoryCheck = CompletableFuture.supplyAsync(() -> {
            sleep(150);
            return 42; // units in stock
        });
        CompletableFuture<Double> priceLookup = CompletableFuture.supplyAsync(() -> {
            sleep(150);
            return 19.99;
        });

        CompletableFuture<String> combined = inventoryCheck.thenCombine(priceLookup,
                (stock, price) -> stock + " units available at $" + price);
        System.out.println("  thenCombine result: " + combined.get());
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  elapsed=" + elapsed + "ms (each call alone takes ~150ms — if this were "
                + "much closer to 300ms than 150ms, the calls were NOT actually running concurrently)");
    }

    private static void demoAllOf() {
        List<CompletableFuture<Integer>> checks = List.of(
                delayedValue(1, 80), delayedValue(2, 120), delayedValue(3, 60), delayedValue(4, 100));

        CompletableFuture<Void> allDone = CompletableFuture.allOf(checks.toArray(new CompletableFuture[0]));
        allDone.join(); // blocks until every one of the 4 is complete

        // allOf itself discards individual results (it's CompletableFuture<Void>)
        // — you still collect them yourself once you know all are done.
        List<Integer> results = checks.stream().map(CompletableFuture::join).toList();
        System.out.println("  allOf: all 4 completed, results=" + results);
    }

    private static void demoAnyOf() throws Exception {
        // Three redundant calls to (simulated) mirrored services — take
        // whichever answers first, ignore the rest. A real use: querying
        // multiple read replicas and using the fastest response.
        CompletableFuture<Object> fastest = CompletableFuture.anyOf(
                delayedValue("replica-A", 200),
                delayedValue("replica-B", 50),
                delayedValue("replica-C", 300));
        System.out.println("  anyOf: fastest response was from " + fastest.get()
                + " (expect replica-B, the shortest configured delay)");
    }

    private static <T> CompletableFuture<T> delayedValue(T value, long delayMillis) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.schedule(() -> {
            future.complete(value);
            scheduler.shutdown();
        }, delayMillis, TimeUnit.MILLISECONDS);
        return future;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
