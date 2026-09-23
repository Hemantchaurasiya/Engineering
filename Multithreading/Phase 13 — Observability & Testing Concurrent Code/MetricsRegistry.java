package com.orderengine.phase13;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * A minimal, hand-built metrics registry — standing in for a real
 * library like Micrometer, which every production service would
 * actually use instead of this. Built by hand here specifically so the
 * underlying concurrency primitive choices are visible rather than
 * hidden behind a dependency: every counter is Phase 3's LongAdder,
 * chosen for exactly the reason Phase 3 §5 laid out — these are hot,
 * high-write, approximate-read counters (incremented on every single
 * order across every pipeline stage), which is precisely LongAdder's
 * sweet spot, and precisely the wrong place to reach for AtomicLong's
 * stronger-but-slower guarantees or (far worse) a lock.
 *
 * A real metrics library adds a great deal on top of this: tagging/
 * dimensional metrics, export formats (Prometheus, StatsD, etc.),
 * proper histogram bucketing with configurable precision, and much
 * more — this class exists to make the underlying primitive-selection
 * reasoning explicit, not to be a metrics library replacement.
 */
public class MetricsRegistry {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LatencyHistogram> histograms = new ConcurrentHashMap<>();

    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }

    public long counterValue(String name) {
        LongAdder adder = counters.get(name);
        return adder == null ? 0 : adder.sum();
    }

    public void recordLatency(String name, long millis) {
        histograms.computeIfAbsent(name, k -> new LatencyHistogram()).record(millis);
    }

    public LatencyHistogram.Snapshot latencySnapshot(String name) {
        LatencyHistogram histogram = histograms.get(name);
        return histogram == null ? LatencyHistogram.Snapshot.EMPTY : histogram.snapshot();
    }

    public Map<String, Long> allCounters() {
        Map<String, Long> result = new java.util.TreeMap<>();
        counters.forEach((name, adder) -> result.put(name, adder.sum()));
        return result;
    }

    public Map<String, LatencyHistogram.Snapshot> allLatencies() {
        Map<String, LatencyHistogram.Snapshot> result = new java.util.TreeMap<>();
        histograms.forEach((name, histogram) -> result.put(name, histogram.snapshot()));
        return result;
    }
}
