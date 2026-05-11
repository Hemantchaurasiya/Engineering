# Java Multithreading & Concurrency — Phase 13: Advanced Topics

> Based on the Complete Java Multithreading & Concurrency Mastery Roadmap  
> Phase 13 covers: Parallel Streams · Reactive Programming · Virtual Threads · Structured Concurrency

---

## Table of Contents

1. [13.1 Parallel Streams](#131-parallel-streams)
2. [13.2 Reactive Programming with Project Reactor](#132-reactive-programming-with-project-reactor)
3. [13.3 Virtual Threads (Java 21)](#133-virtual-threads-java-21)
4. [13.4 Structured Concurrency with StructuredTaskScope](#134-structured-concurrency-with-structuredtaskscope)
5. [Summary: When to Use What](#summary-when-to-use-what)

---

## 13.1 Parallel Streams

**Real-world scenario:** You work at an e-commerce company and need to process a catalog of 1 million products — applying discounts, filtering by availability, and computing final prices.

### Domain Model

```java
record Product(
    String id,
    String name,
    double price,
    String category,
    boolean inStock,
    int inventoryCount
) {}

record ProcessedProduct(
    String id,
    String name,
    double originalPrice,
    double discountedPrice,
    String category,
    boolean eligible
) {}
```

### Full Implementation

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

public class EcommerceCatalogProcessor {

    private static final int SEQUENTIAL_THRESHOLD = 10_000;
    private static final ForkJoinPool CUSTOM_POOL =
        new ForkJoinPool(Runtime.getRuntime().availableProcessors());

    // ✅ GOOD: Uses parallel stream with a meaningful threshold check
    public List<ProcessedProduct> processCatalog(List<Product> catalog) {
        Stream<Product> stream = catalog.size() > SEQUENTIAL_THRESHOLD
            ? catalog.parallelStream()
            : catalog.stream();

        return stream
            .filter(Product::inStock)
            .filter(p -> p.inventoryCount() > 0)
            .map(this::applyDiscount)
            .filter(ProcessedProduct::eligible)
            .sorted(Comparator.comparingDouble(ProcessedProduct::discountedPrice))
            .collect(Collectors.toList());
    }

    // ✅ GOOD: Custom ForkJoinPool to avoid blocking the common pool
    // Use this when your tasks do I/O (e.g., enriching with DB calls)
    public List<ProcessedProduct> processCatalogWithCustomPool(List<Product> catalog)
            throws ExecutionException, InterruptedException {
        return CUSTOM_POOL.submit(() ->
            catalog.parallelStream()
                .filter(Product::inStock)
                .map(this::applyDiscountWithEnrichment) // simulates I/O
                .filter(ProcessedProduct::eligible)
                .collect(Collectors.toList())
        ).get();
    }

    // ✅ GOOD: Grouping by category in parallel — thread-safe because
    // groupingByConcurrent uses a thread-safe collector
    public Map<String, DoubleSummaryStatistics> getPricingStatsByCategory(
            List<Product> catalog) {
        return catalog.parallelStream()
            .collect(Collectors.groupingByConcurrent(
                Product::category,
                Collectors.summarizingDouble(Product::price)
            ));
    }

    // ❌ BAD PRACTICE — shown for contrast
    // Shared mutable state with parallel streams causes race conditions
    public List<ProcessedProduct> badExample(List<Product> catalog) {
        List<ProcessedProduct> results = new ArrayList<>(); // NOT thread-safe!
        catalog.parallelStream().forEach(p -> {
            results.add(applyDiscount(p)); // ConcurrentModificationException risk
        });
        return results;
    }

    private ProcessedProduct applyDiscount(Product product) {
        double discount = getDiscountRate(product.category());
        double discountedPrice = product.price() * (1 - discount);
        boolean eligible = discountedPrice < 500.0 && product.inventoryCount() > 5;
        return new ProcessedProduct(
            product.id(), product.name(),
            product.price(), discountedPrice,
            product.category(), eligible
        );
    }

    private ProcessedProduct applyDiscountWithEnrichment(Product product) {
        // Simulate I/O enrichment (e.g., fetching discount from DB)
        try { Thread.sleep(1); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return applyDiscount(product);
    }

    private double getDiscountRate(String category) {
        return switch (category) {
            case "Electronics" -> 0.15;
            case "Clothing"    -> 0.25;
            case "Books"       -> 0.10;
            default            -> 0.05;
        };
    }

    // ✅ Benchmark helper to show when parallel actually helps
    public static void benchmark() {
        var processor = new EcommerceCatalogProcessor();
        var catalog = generateCatalog(1_000_000);

        long start = System.currentTimeMillis();
        processor.processCatalog(catalog);
        System.out.printf("Sequential-auto: %dms%n", System.currentTimeMillis() - start);

        start = System.currentTimeMillis();
        catalog.parallelStream().map(processor::applyDiscount).toList();
        System.out.printf("Parallel:        %dms%n", System.currentTimeMillis() - start);
    }

    private static List<Product> generateCatalog(int size) {
        var categories = List.of("Electronics", "Clothing", "Books", "Furniture");
        var rng = new Random();
        return IntStream.range(0, size).mapToObj(i -> new Product(
            "PROD-" + i,
            "Product " + i,
            50 + rng.nextDouble() * 950,
            categories.get(rng.nextInt(categories.size())),
            rng.nextBoolean(),
            rng.nextInt(100)
        )).toList();
    }
}
```

### Key Rules for Parallel Streams

| Situation | Use Parallel? |
|---|---|
| Large dataset (>10k elements), CPU-bound ops | ✅ Yes |
| Small dataset | ❌ No — overhead outweighs gain |
| I/O-bound operations | ❌ No — blocks common ForkJoinPool; use custom pool |
| Stateful lambdas / shared mutable state | ❌ Never |
| Operations with ordering requirements | ⚠️ Be careful — `forEachOrdered` kills gains |

---

## 13.2 Reactive Programming with Project Reactor

**Real-world scenario:** A financial trading platform streams live stock prices and needs to process them with backpressure — the consumer (risk engine) is slower than the producer (market data feed).

> **Gradle dependency:** `implementation 'io.projectreactor:reactor-core:3.6.0'`

### Full Implementation

```java
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;

public class TradingPlatformReactive {

    // Flux = 0..N items (like a stream)
    // Mono = 0..1 item (like a Future)

    // Simulates a real-time market data feed
    public Flux<StockTick> marketDataFeed() {
        return Flux.interval(Duration.ofMillis(10))   // emits every 10ms
            .map(seq -> new StockTick(
                pickSymbol(seq),
                90 + Math.random() * 20,
                System.currentTimeMillis()
            ))
            .share(); // multicasts to multiple subscribers
    }

    // ✅ Backpressure strategy: DROP — fast producer, slow consumer
    // Real use: logging ticks is optional; missing a few is fine
    public void subscribeWithDropStrategy(Flux<StockTick> feed) {
        feed
            .onBackpressureDrop(dropped ->
                System.out.println("Dropped tick: " + dropped.symbol()))
            .publishOn(Schedulers.boundedElastic()) // offload to non-blocking thread
            .subscribe(tick -> {
                processTickSlowly(tick); // slow consumer
            });
    }

    // ✅ Backpressure strategy: BUFFER with bounded buffer
    // Real use: order execution — can't drop, but buffer with limit
    public void subscribeWithBoundedBuffer(Flux<StockTick> feed) {
        feed
            .onBackpressureBuffer(
                1000,                    // max 1000 buffered
                dropped -> System.err.println("Buffer overflow: " + dropped.symbol()),
                BufferOverflowStrategy.DROP_OLDEST
            )
            .publishOn(Schedulers.boundedElastic())
            .subscribe(this::processTickSlowly);
    }

    // ✅ Pipeline: filter → transform → aggregate → alert
    public Flux<PriceAlert> buildAlertPipeline(Flux<StockTick> feed) {
        return feed
            .filter(tick -> List.of("AAPL", "GOOG", "MSFT").contains(tick.symbol()))
            .bufferTimeout(100, Duration.ofMillis(500))  // batch: 100 ticks OR 500ms
            .flatMap(batch -> Flux.fromIterable(batch)
                .groupBy(StockTick::symbol)
                .flatMap(grouped -> grouped
                    .map(StockTick::price)
                    .reduce(new PriceStats(), PriceStats::accumulate)
                    .map(stats -> new PriceAlert(grouped.key(), stats))
                )
            )
            .filter(PriceAlert::isSignificant)
            .publishOn(Schedulers.single()); // alerts processed on single thread (ordered)
    }

    // ✅ Error handling — retry with exponential backoff
    // Real use: market data feed loses connection
    public Flux<StockTick> resilientFeed(String symbol) {
        return fetchFromExchange(symbol)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(30))
                .jitter(0.5)  // randomness to avoid thundering herd
                .doBeforeRetry(signal -> System.out.println(
                    "Retrying after error: " + signal.failure().getMessage()))
            )
            .onErrorResume(e -> {
                System.err.println("Feed permanently failed, switching to backup");
                return fetchFromBackupExchange(symbol);
            });
    }

    // ✅ Mono for single async results — like CompletableFuture but composable
    public Mono<TradeConfirmation> executeTrade(TradeOrder order) {
        return validateOrder(order)           // Mono<TradeOrder>
            .flatMap(this::checkRiskLimits)   // Mono<TradeOrder>
            .flatMap(this::submitToExchange)  // Mono<TradeConfirmation>
            .timeout(Duration.ofSeconds(5))
            .doOnSuccess(c -> System.out.println("Trade confirmed: " + c.id()))
            .doOnError(e -> System.err.println("Trade failed: " + e.getMessage()));
    }

    // --- Supporting types ---

    record StockTick(String symbol, double price, long timestamp) {}
    record PriceAlert(String symbol, PriceStats stats) {
        boolean isSignificant() { return stats.range() > 2.0; }
    }
    record TradeOrder(String symbol, int quantity, double limitPrice) {}
    record TradeConfirmation(String id, TradeOrder order, double executedPrice) {}

    static class PriceStats {
        double min = Double.MAX_VALUE, max = Double.MIN_VALUE, sum = 0;
        int count = 0;
        PriceStats accumulate(double price) {
            min = Math.min(min, price); max = Math.max(max, price);
            sum += price; count++;
            return this;
        }
        double range() { return max - min; }
        double avg()   { return count == 0 ? 0 : sum / count; }
    }

    // Stubs
    private void processTickSlowly(StockTick t) {
        try { Thread.sleep(50); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    private String pickSymbol(long seq) {
        return List.of("AAPL", "GOOG", "MSFT", "AMZN").get((int)(seq % 4));
    }
    private Flux<StockTick>  fetchFromExchange(String s)       { return Flux.empty(); }
    private Flux<StockTick>  fetchFromBackupExchange(String s) { return Flux.empty(); }
    private Mono<TradeOrder> validateOrder(TradeOrder o)       { return Mono.just(o); }
    private Mono<TradeOrder> checkRiskLimits(TradeOrder o)     { return Mono.just(o); }
    private Mono<TradeConfirmation> submitToExchange(TradeOrder o) {
        return Mono.just(new TradeConfirmation("TXN-001", o, o.limitPrice()));
    }
}
```

### Reactive vs Traditional Threading

| Concern | Thread-per-request | Reactive |
|---|---|---|
| 10k concurrent connections | 10k threads (~80GB RAM) | Handful of threads |
| Slow I/O | Blocks thread | Releases thread, resumes when ready |
| Backpressure | Manual with queues | Built-in |
| Code style | Imperative, easy to read | Functional pipelines, steeper learning curve |

---

## 13.3 Virtual Threads (Java 21)

**Real-world scenario:** A hotel booking service handles thousands of simultaneous reservation requests — each hitting the database, checking availability, sending emails. Classic thread-per-request breaks down; virtual threads solve it elegantly.

### Key Concept

Virtual threads are cheap to create (a few KB vs ~1MB for platform threads). The JVM automatically **suspends** a virtual thread during blocking I/O and **mounts it back** on a carrier thread when the call returns. You write plain blocking code, and the JVM handles the concurrency.

### Full Implementation

```java
import java.util.concurrent.*;

public class HotelBookingService {

    // ✅ Virtual thread executor — the key change from platform threads
    // No pooling needed: virtual threads are cheap to create (few KB vs ~1MB)
    private final ExecutorService virtualExecutor =
        Executors.newVirtualThreadPerTaskExecutor();

    // Before Java 21: needed a fixed thread pool → limits concurrency
    // ExecutorService platformExecutor = Executors.newFixedThreadPool(200);

    // ✅ Each booking request gets its own virtual thread
    public CompletableFuture<BookingConfirmation> bookRoom(BookingRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            // These blocking calls are fine in virtual threads!
            // JVM automatically unmounts during blocking and remounts after
            RoomAvailability availability = checkAvailability(request); // blocks: DB call
            if (!availability.isAvailable()) {
                throw new RoomUnavailableException("Room not available");
            }
            Payment payment = processPayment(request);   // blocks: payment API
            sendConfirmationEmail(request);              // blocks: email API
            return new BookingConfirmation(
                UUID.randomUUID().toString(), request, payment.transactionId()
            );
        }, virtualExecutor);
    }

    // ✅ Handle 10,000 concurrent booking simulations with ease
    public void handleConcurrentBookings(List<BookingRequest> requests)
            throws InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // try-with-resources shuts down executor after all tasks finish
            for (BookingRequest request : requests) {
                executor.submit(() -> {
                    try {
                        bookRoom(request).get();
                    } catch (Exception e) {
                        System.err.println("Booking failed: " + e.getMessage());
                    }
                });
            }
        } // blocks here until all submitted tasks complete
    }

    // ✅ Structured Concurrency (Java 21 Preview)
    // Groups related concurrent tasks — if any fails, others are cancelled
    public BookingBundle bookMultipleRooms(List<BookingRequest> requests)
            throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<BookingConfirmation>> subtasks =
                requests.stream()
                    .map(r -> scope.fork(() -> bookRoomSync(r)))
                    .toList();

            scope.join();           // wait for all
            scope.throwIfFailed();  // propagate first failure, cancels rest

            List<BookingConfirmation> confirmations = subtasks.stream()
                .map(StructuredTaskScope.Subtask::get)
                .toList();

            return new BookingBundle(confirmations);
        }
    }

    // ⚠️ PITFALL: Don't use ThreadLocal with virtual threads carelessly
    // Virtual threads can be millions — ThreadLocal values multiply too
    // Use ScopedValue instead (Java 21 Preview)
    private static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    public void processWithScopedValue(String requestId, BookingRequest request) {
        ScopedValue.where(REQUEST_ID, requestId).run(() -> {
            // REQUEST_ID.get() returns "requestId" within this scope only
            // Automatically cleaned up when scope exits — no leaks!
            checkAvailability(request);
            System.out.println("Processing request: " + REQUEST_ID.get());
        });
    }

    // ⚠️ PITFALL: Don't pin virtual threads with synchronized on long blocking ops
    // This blocks the carrier (platform) thread — defeats the purpose

    // ❌ BAD: pins carrier thread
    private synchronized RoomAvailability badSynchronizedQuery(BookingRequest r) {
        return checkAvailability(r); // blocks carrier thread while holding monitor!
    }

    // ✅ GOOD: Use ReentrantLock — it doesn't pin the carrier thread
    private final ReentrantLock dbLock = new ReentrantLock();
    private RoomAvailability goodLockQuery(BookingRequest r) {
        dbLock.lock();
        try {
            return checkAvailability(r); // virtual thread suspends, not carrier
        } finally {
            dbLock.unlock();
        }
    }

    // --- Supporting types ---

    record BookingRequest(String guestId, String roomType, String dates) {}
    record BookingConfirmation(String id, BookingRequest request, String txnId) {}
    record BookingBundle(List<BookingConfirmation> confirmations) {}
    record RoomAvailability(boolean isAvailable) {}
    record Payment(String transactionId) {}

    class RoomUnavailableException extends RuntimeException {
        RoomUnavailableException(String msg) { super(msg); }
    }

    private RoomAvailability checkAvailability(BookingRequest r) {
        try { Thread.sleep(50); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new RoomAvailability(true);
    }

    private Payment processPayment(BookingRequest r) {
        try { Thread.sleep(100); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new Payment("TXN-" + System.nanoTime());
    }

    private void sendConfirmationEmail(BookingRequest r) {
        try { Thread.sleep(30); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private BookingConfirmation bookRoomSync(BookingRequest r) {
        RoomAvailability a = checkAvailability(r);
        Payment p = processPayment(r);
        return new BookingConfirmation(UUID.randomUUID().toString(), r, p.transactionId());
    }
}
```

### Virtual Threads: Do's and Don'ts

| Practice | Verdict | Reason |
|---|---|---|
| Blocking DB/HTTP calls inside virtual thread | ✅ Do it | JVM unmounts during block |
| `Executors.newVirtualThreadPerTaskExecutor()` | ✅ Use it | No pooling needed |
| `ThreadLocal` for per-request state | ⚠️ Avoid | Memory multiplies with millions of VTs |
| `ScopedValue` for per-request state | ✅ Prefer | Auto-cleanup, no leaks |
| `synchronized` around long blocking ops | ❌ Avoid | Pins carrier thread |
| `ReentrantLock` for mutual exclusion | ✅ Use it | Doesn't pin carrier thread |

---

## 13.4 Structured Concurrency with StructuredTaskScope

**Real-world scenario:** Assembling a product page from multiple microservices. Fail fast if any service fails; timeout the whole operation.

### Full Implementation

```java
import java.util.concurrent.StructuredTaskScope;
import java.time.Instant;

public class ProductPageAssembler {

    // ✅ ShutdownOnFailure: fan-out to all services; cancel all if any fails
    public ProductPage assemblePage(String productId)
            throws InterruptedException, ExecutionException, TimeoutException {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var productTask   = scope.fork(() -> fetchProduct(productId));
            var reviewsTask   = scope.fork(() -> fetchReviews(productId));
            var inventoryTask = scope.fork(() -> fetchInventory(productId));
            var relatedTask   = scope.fork(() -> fetchRelatedProducts(productId));

            // Wait max 2 seconds for all — if any fails, others cancel immediately
            scope.joinUntil(Instant.now().plusSeconds(2));
            scope.throwIfFailed();

            return new ProductPage(
                productTask.get(),
                reviewsTask.get(),
                inventoryTask.get(),
                relatedTask.get()
            );
        }
    }

    // ✅ ShutdownOnSuccess: race between primary and backup DB
    // Returns whichever replies first
    public Product fetchProductFast(String productId) throws InterruptedException {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<Product>()) {
            scope.fork(() -> fetchFromPrimaryDB(productId));
            scope.fork(() -> fetchFromReplicaDB(productId));
            scope.join();
            return scope.result();
        }
    }

    // Supporting types & stubs

    record ProductPage(Object product, Object reviews, Object inventory, Object related) {}
    record Product(String id) {}

    private Object fetchProduct(String id)         { sleep(100); return new Object(); }
    private Object fetchReviews(String id)         { sleep(150); return new Object(); }
    private Object fetchInventory(String id)       { sleep(80);  return new Object(); }
    private Object fetchRelatedProducts(String id) { sleep(200); return new Object(); }
    private Product fetchFromPrimaryDB(String id)  { sleep(100); return new Product(id); }
    private Product fetchFromReplicaDB(String id)  { sleep(60);  return new Product(id); }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### ShutdownOnFailure vs ShutdownOnSuccess

| Scope Type | Behaviour | Best Use Case |
|---|---|---|
| `ShutdownOnFailure` | Cancels all siblings if **any** subtask fails | Assembling a page from required microservices |
| `ShutdownOnSuccess` | Cancels all siblings as soon as **one** succeeds | Racing primary vs backup data source |

---

## Summary: When to Use What

```
CPU-bound work on big data?         → Parallel Streams
Many I/O-bound requests?            → Virtual Threads
Async pipelines + backpressure?     → Reactive (Reactor / RxJava)
Fan-out + collect results?          → StructuredTaskScope (Java 21)
Thread-local state w/ VThreads?     → ScopedValue (not ThreadLocal)
Single async result?                → Mono (Reactor) or CompletableFuture
```

### Overall Comparison

| Technology | Threading Model | Code Style | Best For |
|---|---|---|---|
| Parallel Streams | ForkJoinPool | Declarative/Functional | CPU-bound bulk data processing |
| Reactive (Reactor) | Event loop | Functional pipelines | High-throughput I/O with backpressure |
| Virtual Threads | 1 VT per task | Imperative (familiar) | I/O-bound microservices, REST APIs |
| StructuredTaskScope | Virtual threads | Imperative + scoped | Fan-out/fan-in coordination |

---

### The Key Mental Shift

> Move from **"one task = one blocked thread"** to **"one task = a lightweight fiber that suspends and resumes."**

Virtual threads and reactive programming both solve the same scalability problem:

- **Virtual threads** let you keep imperative, easy-to-debug code while the JVM handles scheduling.
- **Reactive** gives you more control over backpressure and operator composition, at the cost of a steeper learning curve.

---

*Roadmap reference: Phase 13 of the Complete Java Multithreading & Concurrency Mastery Roadmap*  
*Java versions: Java 21 (GA) for Virtual Threads & StructuredTaskScope preview, Java 8+ for Parallel Streams & CompletableFuture*