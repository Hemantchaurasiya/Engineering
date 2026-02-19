# Complete Java Concurrency Guide: Phase 5 - Advanced Synchronizers

> **A comprehensive guide with real-world examples and production-ready code**

---

## Table of Contents

1. [Introduction](#introduction)
2. [5.1 CountDownLatch - Service Startup Coordinator](#51-countdownlatch)
3. [5.2 CyclicBarrier - Parallel Data Processing](#52-cyclicbarrier)
4. [5.3 Semaphore - Database Connection Pool](#53-semaphore)
5. [5.4 Exchanger - Trading System Buffer Swap](#54-exchanger)
6. [5.5 Phaser - Dynamic Batch Processing](#55-phaser)
7. [Synchronizers Comparison](#synchronizers-comparison)
8. [Practice Exercises](#practice-exercises)
9. [Common Pitfalls & Solutions](#common-pitfalls)
10. [Best Practices Checklist](#best-practices-checklist)

---

## Introduction

Phase 5 focuses on Java's **Advanced Synchronizers** - powerful concurrency utilities that solve complex coordination problems. These tools go beyond basic locks and synchronized blocks to handle sophisticated multi-threaded scenarios.

### Why Advanced Synchronizers Matter

- **CountDownLatch**: Wait for N events to complete before proceeding
- **CyclicBarrier**: Synchronize N threads at multiple points
- **Semaphore**: Limit concurrent access to resources
- **Exchanger**: Bidirectional data exchange between two threads
- **Phaser**: Dynamic multi-phase coordination with variable participants

These are essential for building production-grade concurrent systems like microservices, data pipelines, and high-performance applications.

---

## 5.1 CountDownLatch

### Real-World Scenario: Microservice Startup Coordination

A microservice needs to initialize multiple components (database, cache, message queue) before accepting requests. All must be ready before the service starts.

### Key Concepts

- **One-time use**: Cannot be reset after reaching zero
- **Countdown from N to 0**: Each task decrements the count
- **Await until zero**: Main thread waits for all tasks to complete
- **Thread-safe**: Multiple threads can countdown and await simultaneously

### Complete Implementation

```java
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Real-world example: Microservice startup coordinator
 * Use case: Ensure all critical services initialize before accepting traffic
 */
public class ServiceStartupCoordinator {
    private static final Logger logger = Logger.getLogger(ServiceStartupCoordinator.class.getName());
    
    // Latch to wait for all services to initialize
    private final CountDownLatch startupLatch;
    private final ExecutorService executor;
    
    public ServiceStartupCoordinator(int serviceCount) {
        this.startupLatch = new CountDownLatch(serviceCount);
        this.executor = Executors.newFixedThreadPool(serviceCount);
    }
    
    /**
     * Base class for services that need initialization
     */
    abstract static class Service {
        private final String name;
        
        public Service(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        abstract void initialize() throws Exception;
    }
    
    /**
     * Simulates database connection initialization
     */
    static class DatabaseService extends Service {
        public DatabaseService() {
            super("Database");
        }
        
        @Override
        void initialize() throws Exception {
            logger.info(getName() + " - Connecting to database...");
            Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 3000));
            logger.info(getName() + " - Connected successfully!");
        }
    }
    
    /**
     * Simulates cache initialization
     */
    static class CacheService extends Service {
        public CacheService() {
            super("Cache");
        }
        
        @Override
        void initialize() throws Exception {
            logger.info(getName() + " - Warming up cache...");
            Thread.sleep(ThreadLocalRandom.current().nextInt(500, 2000));
            logger.info(getName() + " - Cache ready!");
        }
    }
    
    /**
     * Simulates message queue connection
     */
    static class MessageQueueService extends Service {
        public MessageQueueService() {
            super("MessageQueue");
        }
        
        @Override
        void initialize() throws Exception {
            logger.info(getName() + " - Connecting to message broker...");
            Thread.sleep(ThreadLocalRandom.current().nextInt(800, 2500));
            logger.info(getName() + " - Message queue connected!");
        }
    }
    
    /**
     * Simulates external API client initialization
     */
    static class ExternalApiService extends Service {
        public ExternalApiService() {
            super("ExternalAPI");
        }
        
        @Override
        void initialize() throws Exception {
            logger.info(getName() + " - Initializing API client...");
            Thread.sleep(ThreadLocalRandom.current().nextInt(600, 1800));
            logger.info(getName() + " - API client ready!");
        }
    }
    
    /**
     * Starts a service initialization task
     */
    public void startService(Service service) {
        executor.submit(() -> {
            try {
                service.initialize();
                logger.info(service.getName() + " - Initialization complete");
            } catch (Exception e) {
                logger.severe(service.getName() + " - Initialization failed: " + e.getMessage());
            } finally {
                // CRITICAL: Always countdown, even on failure
                startupLatch.countDown();
                logger.info("Remaining services to initialize: " + startupLatch.getCount());
            }
        });
    }
    
    /**
     * Waits for all services to complete initialization
     */
    public boolean awaitStartup(long timeout, TimeUnit unit) throws InterruptedException {
        logger.info("Waiting for all services to initialize...");
        boolean success = startupLatch.await(timeout, unit);
        
        if (success) {
            logger.info("✓ All services initialized successfully!");
        } else {
            logger.warning("✗ Timeout: " + startupLatch.getCount() + " services failed");
        }
        
        return success;
    }
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public static void main(String[] args) {
        ServiceStartupCoordinator coordinator = new ServiceStartupCoordinator(4);
        
        try {
            logger.info("=== Starting Microservice Initialization ===\n");
            
            coordinator.startService(new DatabaseService());
            coordinator.startService(new CacheService());
            coordinator.startService(new MessageQueueService());
            coordinator.startService(new ExternalApiService());
            
            boolean ready = coordinator.awaitStartup(10, TimeUnit.SECONDS);
            
            if (ready) {
                logger.info("\n=== Microservice is READY to accept traffic ===");
            } else {
                logger.severe("\n=== Microservice startup FAILED ===");
                System.exit(1);
            }
            
        } catch (InterruptedException e) {
            logger.severe("Startup interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            coordinator.shutdown();
        }
    }
}
```

### Key Learnings

1. **One-time use**: Cannot be reset after countdown reaches zero
2. **Always countdown in finally**: Prevents deadlock if exceptions occur
3. **Use await(timeout)**: Never use `await()` without timeout
4. **Count represents remaining operations**: Not completed ones
5. **Multiple threads can await**: All will be released when count reaches zero

### When to Use CountDownLatch

✅ **Good for:**
- Waiting for N parallel initialization tasks
- Starting N threads simultaneously (start gun pattern)
- Waiting for N computations before proceeding
- One-time event coordination

❌ **Not suitable for:**
- Repeated synchronization (use CyclicBarrier)
- Dynamic participant count (use Phaser)
- Resource limiting (use Semaphore)

### Alternative

For more flexibility, use `CompletableFuture.allOf()` which allows composition and error handling.

---

## 5.2 CyclicBarrier

### Real-World Scenario: Parallel CSV Processing Pipeline

Processing large CSV files in parallel chunks, where each processing phase must complete before the next phase begins.

### Key Concepts

- **Reusable**: Same barrier for multiple synchronization points
- **Mutual waiting**: Threads wait for each other to reach the barrier
- **Barrier action**: Runs once when all threads arrive
- **Broken barrier**: If one thread fails, all threads get BrokenBarrierException

### Complete Implementation

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * Real-world example: Multi-phase parallel CSV processing
 */
public class ParallelCsvProcessor {
    private static final Logger logger = Logger.getLogger(ParallelCsvProcessor.class.getName());
    
    private final int workerCount;
    private final CyclicBarrier barrier;
    private final ExecutorService executor;
    
    private final ConcurrentHashMap<String, Integer> wordCounts = new ConcurrentHashMap<>();
    private final AtomicInteger totalLinesProcessed = new AtomicInteger(0);
    private final AtomicInteger currentPhase = new AtomicInteger(1);
    
    public ParallelCsvProcessor(int workerCount) {
        this.workerCount = workerCount;
        this.executor = Executors.newFixedThreadPool(workerCount);
        
        // Barrier action runs when all workers reach the barrier
        Runnable barrierAction = () -> {
            int phase = currentPhase.get();
            logger.info(String.format(
                "\n>>> PHASE %d COMPLETE - All workers synchronized! <<<", phase
            ));
            logger.info("Total lines processed: " + totalLinesProcessed.get());
            logger.info("Unique words found: " + wordCounts.size());
            currentPhase.incrementAndGet();
        };
        
        this.barrier = new CyclicBarrier(workerCount, barrierAction);
    }
    
    class DataWorker implements Callable<ProcessingResult> {
        private final int workerId;
        private final List<String> dataChunk;
        
        public DataWorker(int workerId, List<String> dataChunk) {
            this.workerId = workerId;
            this.dataChunk = dataChunk;
        }
        
        @Override
        public ProcessingResult call() throws Exception {
            ProcessingResult result = new ProcessingResult(workerId);
            
            try {
                // PHASE 1: Parse and validate
                logger.info("Worker-" + workerId + " starting PHASE 1: Parsing...");
                List<String[]> parsedData = parsePhase(dataChunk);
                result.parsedRecords = parsedData.size();
                barrier.await();
                
                // PHASE 2: Transform and enrich
                logger.info("Worker-" + workerId + " starting PHASE 2: Transforming...");
                List<EnrichedRecord> enrichedData = transformPhase(parsedData);
                result.transformedRecords = enrichedData.size();
                barrier.await();
                
                // PHASE 3: Aggregate and count
                logger.info("Worker-" + workerId + " starting PHASE 3: Aggregating...");
                aggregatePhase(enrichedData);
                result.aggregatedRecords = enrichedData.size();
                barrier.await();
                
                logger.info("Worker-" + workerId + " completed all phases!");
                
            } catch (BrokenBarrierException e) {
                logger.severe("Worker-" + workerId + " - Barrier broken: " + e.getMessage());
                throw new RuntimeException("Processing pipeline failed", e);
            }
            
            return result;
        }
        
        private List<String[]> parsePhase(List<String> lines) throws InterruptedException {
            List<String[]> parsed = new ArrayList<>();
            for (String line : lines) {
                Thread.sleep(10);
                parsed.add(line.split(","));
                totalLinesProcessed.incrementAndGet();
            }
            return parsed;
        }
        
        private List<EnrichedRecord> transformPhase(List<String[]> records) 
                throws InterruptedException {
            List<EnrichedRecord> enriched = new ArrayList<>();
            for (String[] record : records) {
                Thread.sleep(5);
                enriched.add(new EnrichedRecord(record));
            }
            return enriched;
        }
        
        private void aggregatePhase(List<EnrichedRecord> records) 
                throws InterruptedException {
            for (EnrichedRecord record : records) {
                Thread.sleep(3);
                for (String word : record.words) {
                    wordCounts.merge(word.toLowerCase(), 1, Integer::sum);
                }
            }
        }
    }
    
    static class EnrichedRecord {
        final String[] rawData;
        final List<String> words;
        final long timestamp;
        
        EnrichedRecord(String[] rawData) {
            this.rawData = rawData;
            this.timestamp = System.currentTimeMillis();
            this.words = new ArrayList<>();
            for (String field : rawData) {
                words.addAll(Arrays.asList(field.split("\\s+")));
            }
        }
    }
    
    static class ProcessingResult {
        final int workerId;
        int parsedRecords;
        int transformedRecords;
        int aggregatedRecords;
        
        ProcessingResult(int workerId) {
            this.workerId = workerId;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Worker-%d: Parsed=%d, Transformed=%d, Aggregated=%d",
                workerId, parsedRecords, transformedRecords, aggregatedRecords
            );
        }
    }
    
    public void processDataset(List<String> dataset) 
            throws InterruptedException, ExecutionException {
        logger.info("=== Starting Parallel Processing ===\n");
        
        int chunkSize = (dataset.size() + workerCount - 1) / workerCount;
        List<Future<ProcessingResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < workerCount; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, dataset.size());
            
            if (start < dataset.size()) {
                List<String> chunk = dataset.subList(start, end);
                futures.add(executor.submit(new DataWorker(i, chunk)));
            }
        }
        
        for (Future<ProcessingResult> future : futures) {
            logger.info("Result: " + future.get());
        }
        
        logger.info("\n=== Processing Complete ===");
        logger.info("Total lines: " + totalLinesProcessed.get());
        logger.info("Unique words: " + wordCounts.size());
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
```

### Key Learnings

1. **Reusable**: Same barrier works for multiple phases
2. **Barrier action**: Runs once when all threads arrive
3. **BrokenBarrierException**: If any thread fails, barrier breaks
4. **barrier.reset()**: Can reset a broken barrier
5. **Perfect for iterative algorithms**: MapReduce, parallel sorting

### CyclicBarrier vs CountDownLatch

| Feature | CyclicBarrier | CountDownLatch |
|---------|--------------|----------------|
| Reusable | ✅ Yes | ❌ No |
| Pattern | Threads wait for each other | Thread waits for events |
| Action | Barrier action available | No action |
| Use case | Multi-phase algorithms | One-time initialization |

### When to Use CyclicBarrier

✅ **Good for:**
- Multi-phase parallel algorithms (MapReduce)
- Simulations with synchronized time steps
- Parallel matrix computations
- Workers that need to synchronize repeatedly

❌ **Not suitable for:**
- One-time coordination (use CountDownLatch)
- Dynamic participants (use Phaser)

---

## 5.3 Semaphore

### Real-World Scenario: Database Connection Pool

Limiting concurrent access to a finite resource (database connections) to prevent overwhelming the database.

### Key Concepts

- **Permits**: Controls access to N resources
- **acquire/release**: Get and return permits
- **Fair vs non-fair**: FIFO ordering vs better performance
- **tryAcquire**: Non-blocking attempt with timeout

### Complete Implementation

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * Real-world example: Database connection pool with resource limiting
 */
public class DatabaseConnectionPool {
    private static final Logger logger = Logger.getLogger(DatabaseConnectionPool.class.getName());
    
    private final Semaphore semaphore;
    private final Queue<Connection> availableConnections;
    private final Set<Connection> allConnections;
    private final int maxConnections;
    private final AtomicInteger connectionIdGenerator = new AtomicInteger(0);
    
    // Statistics
    private final AtomicInteger totalAcquisitions = new AtomicInteger(0);
    private final AtomicInteger totalReleases = new AtomicInteger(0);
    private final AtomicInteger timeoutCount = new AtomicInteger(0);
    
    public DatabaseConnectionPool(int maxConnections) {
        this.maxConnections = maxConnections;
        this.semaphore = new Semaphore(maxConnections, true); // Fair semaphore
        this.availableConnections = new ConcurrentLinkedQueue<>();
        this.allConnections = ConcurrentHashMap.newKeySet();
        
        initializeConnections();
    }
    
    static class Connection {
        private final int id;
        private final AtomicBoolean inUse = new AtomicBoolean(false);
        private final long createdAt;
        private volatile long lastUsedAt;
        private final AtomicInteger queryCount = new AtomicInteger(0);
        
        Connection(int id) {
            this.id = id;
            this.createdAt = System.currentTimeMillis();
            this.lastUsedAt = createdAt;
        }
        
        public void executeQuery(String sql) throws InterruptedException {
            if (!inUse.get()) {
                throw new IllegalStateException("Connection not acquired!");
            }
            
            logger.info(String.format("Connection-%d executing: %s", id, sql));
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
            queryCount.incrementAndGet();
            lastUsedAt = System.currentTimeMillis();
            logger.info(String.format("Connection-%d completed query", id));
        }
        
        void markInUse() { inUse.set(true); }
        void markAvailable() { inUse.set(false); }
        public int getId() { return id; }
        public int getQueryCount() { return queryCount.get(); }
    }
    
    private void initializeConnections() {
        logger.info("Initializing pool with " + maxConnections + " connections");
        for (int i = 0; i < maxConnections; i++) {
            Connection conn = new Connection(connectionIdGenerator.incrementAndGet());
            availableConnections.offer(conn);
            allConnections.add(conn);
        }
        logger.info("Connection pool initialized");
    }
    
    /**
     * Acquire a connection with timeout
     */
    public Connection acquireConnection(long timeout, TimeUnit unit) 
            throws InterruptedException {
        logger.fine("Thread-" + Thread.currentThread().getId() + " requesting connection");
        
        // Try to acquire permit
        boolean acquired = semaphore.tryAcquire(timeout, unit);
        
        if (!acquired) {
            timeoutCount.incrementAndGet();
            logger.warning("Thread-" + Thread.currentThread().getId() + 
                          " - Connection acquisition TIMEOUT");
            return null;
        }
        
        Connection conn = availableConnections.poll();
        
        if (conn == null) {
            conn = new Connection(connectionIdGenerator.incrementAndGet());
            allConnections.add(conn);
            logger.warning("Created additional connection: " + conn.getId());
        }
        
        conn.markInUse();
        totalAcquisitions.incrementAndGet();
        
        logger.info(String.format(
            "Thread-%d acquired Connection-%d (Available: %d)",
            Thread.currentThread().getId(),
            conn.getId(),
            semaphore.availablePermits()
        ));
        
        return conn;
    }
    
    /**
     * Release connection back to pool
     */
    public void releaseConnection(Connection conn) {
        if (conn == null) return;
        
        if (!conn.inUse.get()) {
            logger.warning("Releasing connection that's not in use!");
            return;
        }
        
        conn.markAvailable();
        availableConnections.offer(conn);
        semaphore.release();
        totalReleases.incrementAndGet();
        
        logger.info(String.format(
            "Thread-%d released Connection-%d (Available: %d)",
            Thread.currentThread().getId(),
            conn.getId(),
            semaphore.availablePermits()
        ));
    }
    
    /**
     * Execute query with automatic connection management
     */
    public void executeWithConnection(String sql, long timeout, TimeUnit unit) 
            throws InterruptedException {
        Connection conn = null;
        try {
            conn = acquireConnection(timeout, unit);
            if (conn == null) {
                logger.severe("Failed to acquire connection for: " + sql);
                return;
            }
            conn.executeQuery(sql);
        } finally {
            // CRITICAL: Always release in finally
            if (conn != null) {
                releaseConnection(conn);
            }
        }
    }
    
    public PoolStats getStats() {
        return new PoolStats(
            allConnections.size(),
            semaphore.availablePermits(),
            totalAcquisitions.get(),
            totalReleases.get(),
            timeoutCount.get()
        );
    }
    
    static class PoolStats {
        final int totalConnections;
        final int availableConnections;
        final int totalAcquisitions;
        final int totalReleases;
        final int timeoutCount;
        
        PoolStats(int total, int available, int acquisitions, int releases, int timeouts) {
            this.totalConnections = total;
            this.availableConnections = available;
            this.totalAcquisitions = acquisitions;
            this.totalReleases = releases;
            this.timeoutCount = timeouts;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Total=%d, Available=%d, Acquisitions=%d, Releases=%d, Timeouts=%d",
                totalConnections, availableConnections, totalAcquisitions, 
                totalReleases, timeoutCount
            );
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        DatabaseConnectionPool pool = new DatabaseConnectionPool(3);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        logger.info("=== Simulating 10 concurrent clients with 3-connection pool ===\n");
        
        CountDownLatch latch = new CountDownLatch(10);
        
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    String sql = "SELECT * FROM users WHERE id = " + taskId;
                    pool.executeWithConnection(sql, 2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        
        logger.info("\n=== Final Statistics ===");
        logger.info(pool.getStats().toString());
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
```

### Key Learnings

1. **Controls N permits**: Limits concurrent resource access
2. **Always release in finally**: Prevents permit leaks
3. **Fair semaphore**: FIFO ordering but slower
4. **tryAcquire with timeout**: Prevents indefinite blocking
5. **Any thread can release**: Doesn't track ownership

### Semaphore vs Lock

| Feature | Semaphore | Lock |
|---------|-----------|------|
| Permits | Multiple (N) | Single (1) |
| Ownership | No tracking | Owner thread only |
| Release | Any thread | Only owner |
| Use case | Resource pools | Mutual exclusion |

### When to Use Semaphore

✅ **Good for:**
- Connection pools
- Rate limiting
- Bounded buffers
- Resource throttling

❌ **Not suitable for:**
- Mutual exclusion (use Lock)
- Two-thread exchange (use Exchanger)

---

## 5.4 Exchanger

### Real-World Scenario: High-Frequency Trading Pipeline

Producer fills buffers while consumer processes previous buffers using the double-buffering pattern.

### Key Concepts

- **Exactly 2 threads**: Bidirectional data exchange
- **Blocking exchange**: Both must call exchange()
- **Zero-copy**: Swap references, no data copying
- **Perfect for double buffering**: Producer/consumer pattern

### Complete Implementation

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Real-world example: High-frequency trading data pipeline
 * Double buffering pattern for zero-copy exchange
 */
public class TradingDataPipeline {
    private static final Logger logger = Logger.getLogger(TradingDataPipeline.class.getName());
    
    static class MarketTick {
        final String symbol;
        final double price;
        final long volume;
        final long timestamp;
        
        MarketTick(String symbol, double price, long volume) {
            this.symbol = symbol;
            this.price = price;
            this.volume = volume;
            this.timestamp = System.nanoTime();
        }
        
        @Override
        public String toString() {
            return String.format("%s: $%.2f (vol: %d)", symbol, price, volume);
        }
    }
    
    static class DataBuffer {
        private final int capacity;
        private final List<MarketTick> ticks;
        
        DataBuffer(int capacity) {
            this.capacity = capacity;
            this.ticks = new ArrayList<>(capacity);
        }
        
        void add(MarketTick tick) {
            if (ticks.size() >= capacity) {
                throw new IllegalStateException("Buffer full!");
            }
            ticks.add(tick);
        }
        
        boolean isFull() { return ticks.size() >= capacity; }
        int size() { return ticks.size(); }
        List<MarketTick> getTicks() { return ticks; }
        void clear() { ticks.clear(); }
    }
    
    static class MarketDataProducer implements Callable<Integer> {
        private final Exchanger<DataBuffer> exchanger;
        private final int totalTicks;
        private final int bufferSize;
        private int ticksProduced = 0;
        
        MarketDataProducer(Exchanger<DataBuffer> exchanger, int totalTicks, int bufferSize) {
            this.exchanger = exchanger;
            this.totalTicks = totalTicks;
            this.bufferSize = bufferSize;
        }
        
        @Override
        public Integer call() throws Exception {
            DataBuffer currentBuffer = new DataBuffer(bufferSize);
            String[] symbols = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"};
            Random random = new Random();
            
            logger.info("[PRODUCER] Started - will produce " + totalTicks + " ticks");
            
            while (ticksProduced < totalTicks) {
                MarketTick tick = new MarketTick(
                    symbols[random.nextInt(symbols.length)],
                    100 + random.nextDouble() * 100,
                    random.nextInt(10000)
                );
                
                currentBuffer.add(tick);
                ticksProduced++;
                Thread.sleep(5);
                
                if (currentBuffer.isFull()) {
                    logger.info(String.format(
                        "[PRODUCER] Buffer full (%d ticks) - exchanging...",
                        currentBuffer.size()
                    ));
                    
                    long startTime = System.nanoTime();
                    currentBuffer = exchanger.exchange(currentBuffer);
                    long exchangeTime = (System.nanoTime() - startTime) / 1_000_000;
                    
                    logger.info(String.format(
                        "[PRODUCER] Exchange complete in %dms - received empty buffer",
                        exchangeTime
                    ));
                    
                    currentBuffer.clear();
                }
            }
            
            if (currentBuffer.size() > 0) {
                logger.info(String.format(
                    "[PRODUCER] Exchanging final partial buffer (%d ticks)",
                    currentBuffer.size()
                ));
                exchanger.exchange(currentBuffer);
            }
            
            logger.info("[PRODUCER] Finished - produced " + ticksProduced + " ticks");
            return ticksProduced;
        }
    }
    
    static class MarketDataConsumer implements Callable<Integer> {
        private final Exchanger<DataBuffer> exchanger;
        private final int bufferSize;
        private int ticksProcessed = 0;
        private volatile boolean running = true;
        
        private final Map<String, Double> latestPrices = new ConcurrentHashMap<>();
        private final Map<String, Long> totalVolume = new ConcurrentHashMap<>();
        
        MarketDataConsumer(Exchanger<DataBuffer> exchanger, int bufferSize) {
            this.exchanger = exchanger;
            this.bufferSize = bufferSize;
        }
        
        @Override
        public Integer call() throws Exception {
            DataBuffer emptyBuffer = new DataBuffer(bufferSize);
            
            logger.info("[CONSUMER] Started - ready to process");
            
            try {
                while (running) {
                    logger.info("[CONSUMER] Waiting for full buffer...");
                    
                    DataBuffer fullBuffer = exchanger.exchange(
                        emptyBuffer, 
                        5, 
                        TimeUnit.SECONDS
                    );
                    
                    logger.info(String.format(
                        "[CONSUMER] Received %d ticks - processing...",
                        fullBuffer.size()
                    ));
                    
                    long startTime = System.nanoTime();
                    processBuffer(fullBuffer);
                    long processingTime = (System.nanoTime() - startTime) / 1_000_000;
                    
                    logger.info(String.format(
                        "[CONSUMER] Processed %d ticks in %dms",
                        fullBuffer.size(),
                        processingTime
                    ));
                    
                    fullBuffer.clear();
                    emptyBuffer = fullBuffer;
                }
            } catch (TimeoutException e) {
                logger.info("[CONSUMER] Timeout - producer finished");
            }
            
            logger.info("[CONSUMER] Finished - processed " + ticksProcessed + " ticks");
            printAnalytics();
            
            return ticksProcessed;
        }
        
        private void processBuffer(DataBuffer buffer) throws InterruptedException {
            for (MarketTick tick : buffer.getTicks()) {
                Thread.sleep(10);
                latestPrices.put(tick.symbol, tick.price);
                totalVolume.merge(tick.symbol, tick.volume, Long::sum);
                ticksProcessed++;
            }
        }
        
        private void printAnalytics() {
            logger.info("\n=== Market Analytics ===");
            logger.info("Latest Prices:");
            latestPrices.forEach((symbol, price) -> 
                logger.info(String.format("  %s: $%.2f", symbol, price))
            );
            
            logger.info("\nTotal Volume:");
            totalVolume.forEach((symbol, volume) -> 
                logger.info(String.format("  %s: %,d", symbol, volume))
            );
        }
        
        void stop() { running = false; }
    }
    
    public static void main(String[] args) throws Exception {
        logger.info("=== Starting Trading Data Pipeline ===\n");
        
        Exchanger<DataBuffer> exchanger = new Exchanger<>();
        
        int bufferSize = 20;
        int totalTicks = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        MarketDataConsumer consumer = new MarketDataConsumer(exchanger, bufferSize);
        MarketDataProducer producer = new MarketDataProducer(exchanger, totalTicks, bufferSize);
        
        Future<Integer> consumerFuture = executor.submit(consumer);
        Future<Integer> producerFuture = executor.submit(producer);
        
        int produced = producerFuture.get();
        logger.info("\nProducer completed: " + produced + " ticks");
        
        Thread.sleep(1000);
        consumer.stop();
        
        try {
            int processed = consumerFuture.get(2, TimeUnit.SECONDS);
            logger.info("Consumer completed: " + processed + " ticks");
        } catch (TimeoutException e) {
            logger.info("Consumer timed out (expected)");
        }
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        logger.info("\n=== Pipeline Complete ===");
    }
}
```

### Key Learnings

1. **Exactly 2 threads**: Bidirectional data swap
2. **exchange() blocks**: Until both threads call it
3. **Double buffering**: One fills, one processes
4. **Use timeout**: Prevents indefinite blocking
5. **Zero-copy**: Swap references, not data

### Double Buffering Pattern

```
Time 1: Producer fills Buffer A | Consumer processes Buffer B
Time 2: [Exchange buffers]
Time 3: Producer fills Buffer B | Consumer processes Buffer A
Time 4: [Exchange buffers]
...
```

### When to Use Exchanger

✅ **Good for:**
- High-frequency data pipelines
- Audio/video frame processing
- Network packet buffers
- Producer ≈ Consumer speed

❌ **Not suitable for:**
- More than 2 threads (use BlockingQueue)
- Very different speeds
- Multiple producers/consumers

---

## 5.5 Phaser

### Real-World Scenario: Distributed Batch Processing

Dynamic worker pool processing images through multiple phases, with workers joining and leaving dynamically.

### Key Concepts

- **Dynamic registration**: Workers can join/leave
- **Multi-phase**: Like CyclicBarrier but unlimited phases
- **Termination detection**: Knows when all parties deregister
- **onAdvance callback**: Runs at phase completion

### Complete Implementation

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * Real-world example: Distributed image processing pipeline
 * Dynamic worker pool with multi-phase coordination
 */
public class DistributedImageProcessor {
    private static final Logger logger = Logger.getLogger(DistributedImageProcessor.class.getName());
    
    static class MonitoredPhaser extends Phaser {
        private final AtomicInteger phasesCompleted = new AtomicInteger(0);
        
        MonitoredPhaser(int parties) {
            super(parties);
        }
        
        @Override
        protected boolean onAdvance(int phase, int registeredParties) {
            phasesCompleted.incrementAndGet();
            
            logger.info(String.format(
                "\n>>> PHASE %d COMPLETED - %d workers for next phase <<<\n",
                phase,
                registeredParties
            ));
            
            return registeredParties == 0; // Terminate when no parties
        }
        
        int getPhasesCompleted() {
            return phasesCompleted.get();
        }
    }
    
    static class Image {
        final String filename;
        final int sizeKB;
        byte[] data;
        
        boolean downloaded = false;
        boolean resized = false;
        boolean filtered = false;
        boolean compressed = false;
        
        Image(String filename, int sizeKB) {
            this.filename = filename;
            this.sizeKB = sizeKB;
            this.data = new byte[sizeKB * 1024];
        }
        
        String getStatus() {
            return String.format(
                "%s: [D:%s R:%s F:%s C:%s]",
                filename,
                downloaded ? "✓" : "✗",
                resized ? "✓" : "✗",
                filtered ? "✓" : "✗",
                compressed ? "✓" : "✗"
            );
        }
    }
    
    static class ImageWorker implements Runnable {
        private final int workerId;
        private final MonitoredPhaser phaser;
        private final BlockingQueue<Image> workQueue;
        private final int maxImages;
        private final boolean canLeaveEarly;
        
        private int imagesProcessed = 0;
        private volatile boolean shouldStop = false;
        
        ImageWorker(int workerId, MonitoredPhaser phaser, 
                   BlockingQueue<Image> workQueue, int maxImages, boolean canLeaveEarly) {
            this.workerId = workerId;
            this.phaser = phaser;
            this.workQueue = workQueue;
            this.maxImages = maxImages;
            this.canLeaveEarly = canLeaveEarly;
            
            phaser.register();
            logger.info(String.format(
                "Worker-%d registered (Total: %d)",
                workerId,
                phaser.getRegisteredParties()
            ));
        }
        
        @Override
        public void run() {
            try {
                while (!shouldStop && imagesProcessed < maxImages) {
                    // PHASE 1: Download
                    Image image = downloadPhase();
                    if (image == null) break;
                    
                    int phase = phaser.arriveAndAwaitAdvance();
                    
                    // PHASE 2: Resize
                    resizePhase(image);
                    phaser.arriveAndAwaitAdvance();
                    
                    // PHASE 3: Filter
                    filterPhase(image);
                    phaser.arriveAndAwaitAdvance();
                    
                    // PHASE 4: Compress
                    compressPhase(image);
                    phaser.arriveAndAwaitAdvance();
                    
                    imagesProcessed++;
                    logger.info(String.format(
                        "Worker-%d completed %d/%d: %s",
                        workerId, imagesProcessed, maxImages, image.getStatus()
                    ));
                    
                    if (canLeaveEarly && imagesProcessed >= maxImages / 2) {
                        logger.warning(String.format(
                            "Worker-%d leaving early after %d images",
                            workerId, imagesProcessed
                        ));
                        break;
                    }
                }
            } catch (InterruptedException e) {
                logger.warning("Worker-" + workerId + " interrupted");
                Thread.currentThread().interrupt();
            } finally {
                phaser.arriveAndDeregister();
                logger.info(String.format(
                    "Worker-%d deregistered (Remaining: %d)",
                    workerId,
                    phaser.getRegisteredParties()
                ));
            }
        }
        
        private Image downloadPhase() throws InterruptedException {
            Image image = workQueue.poll(2, TimeUnit.SECONDS);
            if (image == null) {
                shouldStop = true;
                return null;
            }
            logger.fine("Worker-" + workerId + " downloading: " + image.filename);
            Thread.sleep(100 + ThreadLocalRandom.current().nextInt(100));
            image.downloaded = true;
            return image;
        }
        
        private void resizePhase(Image image) throws InterruptedException {
            Thread.sleep(80 + ThreadLocalRandom.current().nextInt(80));
            image.resized = true;
        }
        
        private void filterPhase(Image image) throws InterruptedException {
            Thread.sleep(120 + ThreadLocalRandom.current().nextInt(100));
            image.filtered = true;
        }
        
        private void compressPhase(Image image) throws InterruptedException {
            Thread.sleep(90 + ThreadLocalRandom.current().nextInt(90));
            image.compressed = true;
        }
        
        int getImagesProcessed() {
            return imagesProcessed;
        }
    }
    
    static class WorkerCoordinator {
        private final MonitoredPhaser phaser;
        private final BlockingQueue<Image> workQueue;
        private final ExecutorService executor;
        private final List<ImageWorker> workers = new ArrayList<>();
        
        WorkerCoordinator(int initialWorkers, BlockingQueue<Image> workQueue) {
            this.phaser = new MonitoredPhaser(0);
            this.workQueue = workQueue;
            this.executor = Executors.newCachedThreadPool();
            
            for (int i = 0; i < initialWorkers; i++) {
                addWorker(i, 10, false);
            }
        }
        
        void addWorker(int id, int maxImages, boolean canLeaveEarly) {
            ImageWorker worker = new ImageWorker(
                id, phaser, workQueue, maxImages, canLeaveEarly
            );
            workers.add(worker);
            executor.submit(worker);
            logger.info("Added Worker-" + id);
        }
        
        void waitForCompletion() {
            while (!phaser.isTerminated()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            logger.info("\n=== All workers completed ===");
            printStatistics();
        }
        
        void shutdown() {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        private void printStatistics() {
            logger.info("\nWorker Statistics:");
            int total = 0;
            for (ImageWorker worker : workers) {
                int processed = worker.getImagesProcessed();
                total += processed;
                logger.info(String.format(
                    "Worker-%d: %d images",
                    worker.workerId,
                    processed
                ));
            }
            logger.info("Total: " + total);
            logger.info("Phases: " + phaser.getPhasesCompleted());
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        logger.info("=== Distributed Image Processing ===\n");
        
        BlockingQueue<Image> workQueue = new LinkedBlockingQueue<>();
        for (int i = 1; i <= 20; i++) {
            workQueue.offer(new Image(
                "image_" + i + ".jpg",
                500 + ThreadLocalRandom.current().nextInt(500)
            ));
        }
        
        logger.info("Created " + workQueue.size() + " images\n");
        
        WorkerCoordinator coordinator = new WorkerCoordinator(3, workQueue);
        
        // Dynamic worker addition
        Thread.sleep(2000);
        logger.info("\n>>> Adding worker dynamically <<<");
        coordinator.addWorker(100, 5, false);
        
        Thread.sleep(1000);
        logger.info("\n>>> Adding worker that leaves early <<<");
        coordinator.addWorker(200, 10, true);
        
        coordinator.waitForCompletion();
        coordinator.shutdown();
        
        logger.info("\n=== Complete ===");
    }
}
```

### Key Learnings

1. **Dynamic parties**: register()/deregister() anytime
2. **arriveAndAwaitAdvance()**: Sync point like barrier
3. **arriveAndDeregister()**: Leave permanently
4. **onAdvance()**: Callback when phase completes
5. **isTerminated()**: True when no parties remain

### Phaser Methods

| Method | Description |
|--------|-------------|
| register() | Add new party |
| arriveAndAwaitAdvance() | Arrive and wait (like barrier.await()) |
| arriveAndDeregister() | Arrive and leave permanently |
| arrive() | Arrive without waiting |
| bulkRegister(n) | Register N parties at once |

### Phaser vs CyclicBarrier

| Feature | Phaser | CyclicBarrier |
|---------|--------|--------------|
| Parties | Dynamic | Fixed |
| Complexity | High | Low |
| Phases | Unlimited | Unlimited |
| Best for | Complex workflows | Simple sync points |

### When to Use Phaser

✅ **Good for:**
- Variable participants
- Multi-phase algorithms
- Dynamic worker pools
- Complex coordination

❌ **Not suitable for:**
- Simple scenarios (use CyclicBarrier)
- Two threads only (use Exchanger)
- One-time events (use CountDownLatch)

---

## Synchronizers Comparison

### Quick Reference Table

| Synchronizer | Parties | Reusable | Dynamic | Best For |
|--------------|---------|----------|---------|----------|
| CountDownLatch | N → 0 | ❌ | ❌ | One-time events |
| CyclicBarrier | N ↔ N | ✅ | ❌ | Repeated sync |
| Semaphore | Any | ✅ | ✅ | Resource limits |
| Exchanger | 2 only | ✅ | ❌ | Buffer swap |
| Phaser | Any | ✅ | ✅ | Complex workflows |

### Decision Tree

```
┌─ Need to limit N concurrent accesses?
│  └─> SEMAPHORE
│
├─ Need exactly 2 threads to exchange?
│  └─> EXCHANGER
│
├─ Need dynamic join/leave?
│  └─> PHASER
│
├─ One-time wait for N events?
│  └─> COUNTDOWNLATCH
│
└─ Repeated N-thread synchronization?
   └─> CYCLICBARRIER
```

---

## Practice Exercises

### Exercise 1: CountDownLatch ⭐
**Build a Distributed Test Suite**

```java
// TODO: Implement
class TestSuiteRunner {
    // Run 10 test classes in parallel
    // Each has 5 test methods
    // Wait for all to complete
    // Print pass/fail summary
}
```

### Exercise 2: CyclicBarrier ⭐⭐
**Multi-Player Game Turns**

```java
// TODO: Implement
class GameEngine {
    // 4 players, 10 rounds
    // All wait after each round
    // Calculate scores in barrier action
}
```

### Exercise 3: Semaphore ⭐⭐
**Rate-Limited API Client**

```java
// TODO: Implement
class RateLimitedClient {
    // Max 5 concurrent requests
    // 100 requests to send
    // Retry with backoff
    // Track success/failure
}
```

### Exercise 4: Exchanger ⭐⭐⭐
**Log Processing Pipeline**

```java
// TODO: Implement
class LogProcessor {
    // Producer reads file
    // Consumer parses logs
    // Double buffering (1000 lines/buffer)
    // Handle EOF gracefully
}
```

### Exercise 5: Phaser ⭐⭐⭐
**MapReduce Framework**

```java
// TODO: Implement
class MapReduceEngine {
    // Dynamic 3-7 mappers
    // Phases: Map → Shuffle → Reduce
    // Workers join during Map
    // Track phase progress
}
```

---

## Common Pitfalls

### Pitfall 1: Forgetting finally block

```java
// ❌ WRONG
latch.countDown();
doWork();

// ✅ CORRECT
try {
    doWork();
} finally {
    latch.countDown();
}
```

### Pitfall 2: No timeout

```java
// ❌ WRONG - blocks forever
semaphore.acquire();

// ✅ CORRECT
if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
    throw new TimeoutException();
}
```

### Pitfall 3: Broken barrier stays broken

```java
// ❌ WRONG
try {
    barrier.await();
} catch (BrokenBarrierException e) {
    // Barrier broken for ALL threads!
}

// ✅ CORRECT
try {
    barrier.await();
} catch (BrokenBarrierException e) {
    barrier.reset();
    throw e;
}
```

### Pitfall 4: Exchanger without timeout

```java
// ❌ WRONG
buffer = exchanger.exchange(buffer);

// ✅ CORRECT
try {
    buffer = exchanger.exchange(buffer, 5, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    // Handle timeout
}
```

### Pitfall 5: Phaser memory leak

```java
// ❌ WRONG
phaser.register();
doWork();

// ✅ CORRECT
phaser.register();
try {
    doWork();
} finally {
    phaser.arriveAndDeregister();
}
```

---

## Best Practices Checklist

### CountDownLatch ✅
- Always countDown() in finally block
- Use await(timeout), never await()
- One latch per operation
- Check return value of await(timeout)

### CyclicBarrier ✅
- Create once, reuse many times
- Handle BrokenBarrierException
- Use barrier action for cleanup
- All parties must await()

### Semaphore ✅
- Always release() in finally
- Match permits to resources
- Use tryAcquire() with timeout
- Consider fairness tradeoffs

### Exchanger ✅
- Use exchange(data, timeout)
- Both threads same type
- Handle TimeoutException
- Perfect for double-buffering

### Phaser ✅
- Always arriveAndDeregister() in finally
- Override onAdvance() for monitoring
- Check isTerminated() for completion
- Use bulkRegister() for efficiency

---

## Summary

You've mastered **Phase 5: Advanced Synchronizers**!

### Key Takeaways

1. **CountDownLatch**: One-time coordination, wait for N events
2. **CyclicBarrier**: Repeated synchronization, phased algorithms
3. **Semaphore**: Resource limiting, connection pools
4. **Exchanger**: Double buffering, two-thread exchange
5. **Phaser**: Dynamic coordination, complex workflows

### Production Patterns Learned

- Microservice initialization
- Parallel data processing
- Connection pool management
- High-frequency trading pipelines
- Distributed batch processing

### Next Steps

Ready for **Phase 6: Locks and Conditions**:
- ReentrantLock
- ReadWriteLock
- StampedLock
- Condition objects

---

## Additional Resources

- **Java Concurrency in Practice** - Brian Goetz
- **Java Language Specification** - Chapter 17
- **Doug Lea's Papers** - Concurrency fundamentals
- **OpenJDK Source** - Study actual implementations

---

*Happy Concurrent Programming! 🚀*