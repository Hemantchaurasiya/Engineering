package com.orderengine.phase13;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A real, live HTTP endpoint exposing MetricsRegistry's counters and
 * latency percentiles — what Phase 12's ChaosTestHarness only ever
 * printed once at the END of a run, now servable continuously WHILE the
 * engine is running, the way an actual production dashboard/scraper
 * (Prometheus, a Grafana panel, an internal ops page) would consume it.
 *
 * Uses com.sun.net.httpserver.HttpServer — a minimal HTTP server built
 * into the JDK itself, no external dependency needed for a demo like
 * this (a real production service would more likely use a proper
 * framework's metrics endpoint, e.g. Spring Boot Actuator or a
 * Micrometer registry's built-in exporter, but the JDK's own HttpServer
 * is enough to prove the concept end-to-end here without adding a
 * dependency this project doesn't otherwise need).
 *
 * Each incoming HTTP request runs on the server's own internal executor
 * — by default a small thread-per-request-ish pool; production code
 * would typically call httpServer.setExecutor(...) with a properly
 * sized/dedicated executor (exactly Phase 4/9's sizing discipline)
 * rather than relying on the JDK default, which this class does for
 * simplicity but calls out explicitly rather than silently.
 */
public class HealthDashboardServer {

    private final HttpServer server;
    private final MetricsRegistry metrics;

    public HealthDashboardServer(MetricsRegistry metrics, int port) throws IOException {
        this.metrics = metrics;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/metrics", this::handleMetrics);
    }

    public void start() {
        server.start();
        System.out.println("Health dashboard listening on http://localhost:"
                + server.getAddress().getPort() + "/metrics");
    }

    public void stop() {
        server.stop(0);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "OK\n");
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        StringBuilder body = new StringBuilder();
        body.append("# Order Processing Engine — live metrics\n\n");

        body.append("## Counters\n");
        Map<String, Long> counters = metrics.allCounters();
        if (counters.isEmpty()) {
            body.append("(none recorded yet)\n");
        } else {
            counters.forEach((name, value) -> body.append(String.format("%-32s %d%n", name, value)));
        }

        body.append("\n## Latencies (ms)\n");
        Map<String, LatencyHistogram.Snapshot> latencies = metrics.allLatencies();
        if (latencies.isEmpty()) {
            body.append("(none recorded yet)\n");
        } else {
            body.append(String.format("%-32s %8s %8s %8s %8s %10s%n", "name", "p50", "p95", "p99", "max", "samples"));
            latencies.forEach((name, s) -> body.append(String.format("%-32s %8d %8d %8d %8d %10d%n",
                    name, s.p50(), s.p95(), s.p99(), s.max(), s.sampleCount())));
        }

        respond(exchange, 200, body.toString());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
