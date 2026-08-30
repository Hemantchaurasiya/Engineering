# 08. Distributed Tracing & Centralized Logging

[← 07. Spring Cloud Gateway](07-spring-cloud-gateway.md) | [README](README.md) | Next → [09. Security (OAuth2)](09-security-oauth2-microservices.md)

---

## 1. Your Original Notes (cleaned up)

- **Sleuth Logging** — adds tracing IDs to logs.
- **Zipkin Server** — collects and visualizes distributed traces.
- **ELK** — centralized logging (Elasticsearch, Logstash, Kibana).

(Your notes had just these three TOC headings with no further detail — this
file fills in the "why" and the "how", plus the fact that Sleuth itself is now
retired.)

---

## 2. Deep Dive — What Your Notes Didn't Cover

### 2.1 The core problem this whole area solves
In a monolith, one request = one process = one log file → tracing a request's
path through the code is trivial (just read top to bottom). In microservices,
**one user request might fan out across 5-10 services**, each with its own log
file, on its own instance, possibly on a different host. Without a shared
**correlation/trace ID** threaded through every hop, you cannot answer "why was
*this specific* user's checkout request slow?" — you'd have to manually
correlate timestamps across a dozen unrelated log streams. This is the single
biggest operational pain point microservices introduce versus a monolith, and
it's why distributed tracing is not optional tooling — it's a hard prerequisite
for operating microservices in production at all.

### 2.2 Spring Cloud Sleuth is retired — Micrometer Tracing is the successor
This is a critical, frequently-tested update your notes don't reflect: **Spring
Cloud Sleuth reached end-of-life with Spring Boot 3** and was replaced by
**Micrometer Tracing** (a core part of Micrometer, the same library behind
Spring Boot Actuator's metrics). Conceptually identical role (auto-inject
trace/span IDs into every request, propagate them across service calls, export
to Zipkin/other backends), different dependency and slightly different
config keys.

```xml
<!-- modern (Spring Boot 3.x) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
<!-- legacy, Spring Boot 2.x only:
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
-->
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # trace 100% of requests in dev; typically 0.1 (10%) in prod for overhead
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
    # produces log lines like:
    # INFO [order-service,4bf92f3577b34da6a3ce929d0e0e4736,00f067aa0ba902b7] Order placed...
```

### 2.3 Trace vs Span — vocabulary that's assumed knowledge in interviews
- **Trace** — the entire end-to-end journey of one logical request across every
  service it touches (e.g. `Client → Gateway → order-service → product-service →
  payment-service`). Identified by one **trace ID**, shared by every hop.
- **Span** — one unit of work *within* that trace — e.g. the time
  `order-service` spent calling `product-service` is one span. Each span has its
  own **span ID** and references its **parent span ID**, forming a tree.
- A trace is therefore a **tree of spans**; Zipkin's UI visualizes exactly this
  tree, showing you which span (which specific hop) consumed the most time.

### 2.4 How trace context actually propagates across an HTTP call (this is the
mechanism your notes never explained)
1. `order-service` starts handling an incoming request. Micrometer Tracing
   (via an instrumented filter) either reads an existing trace context from
   incoming headers (if this request came from something upstream, like the
   Gateway) or **starts a new trace** if none exists.
2. When `order-service` calls `product-service` (via Feign/RestTemplate/
   WebClient), Micrometer Tracing's instrumentation automatically injects
   **B3 propagation headers** into the outgoing request:
   `X-B3-TraceId`, `X-B3-SpanId`, `X-B3-ParentSpanId`, `X-B3-Sampled`.
3. `product-service` reads those headers, recognizes it's part of an existing
   trace (same trace ID), creates a **new span** as a child of the incoming
   span ID, and continues the chain if it calls anything further.
4. Each service asynchronously reports its own completed spans to Zipkin, which
   stitches them back together into the full trace using the shared trace ID.

This is exactly why **every** HTTP client in the chain (RestTemplate, Feign,
WebClient, and the Gateway itself) needs the tracing dependency on its
classpath — a single un-instrumented hop breaks the chain, and everything
downstream of that hop shows up in Zipkin as a **separate, disconnected** trace.

### 2.5 Zipkin Server setup

```bash
# Zipkin ships as a self-contained jar/docker image — not something you write
docker run -d -p 9411:9411 openzipkin/zipkin
```
Then browse to `http://localhost:9411` — search by service name, trace ID, or
tags/duration to find slow/failed traces and see the full span waterfall.

**Alternatives to know:** Jaeger (CNCF, OpenTelemetry-native), and
**OpenTelemetry (OTel)** itself, which is increasingly replacing both Zipkin's
and Brave's own instrumentation as the vendor-neutral standard — Micrometer
Tracing can be bridged to either Brave/Zipkin *or* OpenTelemetry depending on
`micrometer-tracing-bridge-otel` vs `-brave`.

### 2.6 Centralized Logging with the ELK Stack — filling in the "how"
Your notes name ELK but don't explain the pipeline. In a microservices
deployment, you never SSH into individual instances to `tail -f` a log file —
instances are ephemeral (autoscaled, replaced). The standard pattern:

```
[Service 1 logs] ─┐
[Service 2 logs] ─┼─→ Filebeat/Logstash (ships + parses logs) → Elasticsearch (stores + indexes) → Kibana (search/visualize/dashboards)
[Service N logs] ─┘
```

- **Elasticsearch** — a distributed search/analytics engine; stores and indexes
  every log line so it's fast to full-text search across millions of entries.
- **Logstash** (or the lighter-weight **Filebeat**) — collects logs (from files,
  stdout in containers, etc.), parses/enriches them (e.g. extract `traceId` as
  its own searchable field), and ships them to Elasticsearch.
- **Kibana** — the UI on top of Elasticsearch: search by service name, trace ID,
  log level, time range; build dashboards/alerts.

**Structured (JSON) logging is a prerequisite**, not optional, for this to work
well — plain-text log lines force Logstash to parse with fragile regex; JSON
logs give clean, queryable fields out of the box:

```xml
<!-- logback-spring.xml -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>
```
```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
    </encoder>
</appender>
```
This is exactly what makes it possible to search Kibana for a single `traceId`
and instantly see every log line, from every service, that request touched —
tying distributed tracing and centralized logging into one coherent debugging
workflow.

### 2.7 The full observability picture (what your 3 TOC bullets are really
part of)
Interviewers at senior level expect you to know the **three pillars of
observability**, and where each Spring/Netflix tool fits:

| Pillar | Answers | Tooling |
|---|---|---|
| **Metrics** | "How much/how often/how fast, in aggregate?" | Micrometer + Prometheus + Grafana |
| **Logging** | "What exactly happened, in detail, for this one event?" | Structured logs + ELK/EFK |
| **Tracing** | "Which services did this one request touch, and where did time go?" | Micrometer Tracing + Zipkin/Jaeger |

None of the three alone is sufficient — metrics tell you *something* is wrong
(latency spike), tracing tells you *where* (which service/span), logs tell you
*why* (the actual error/exception detail) — you use `traceId` as the thread
connecting all three when investigating an incident.

---

## 3. "Behind the Scenes"

**Sampling — why `probability: 1.0` in dev but ~`0.1` in production:** Every
traced request incurs overhead (creating spans, propagating headers,
serializing/exporting span data to Zipkin) and, at real production volume,
tracing *100%* of requests can itself become a meaningful load/cost/storage
burden on Zipkin/Elasticsearch. Sampling means only a configured fraction of
traces are fully recorded — but critically, the **sampling decision is made
once, at the start of the trace** (typically at the Gateway, the first hop),
and that decision (sampled or not) is propagated via the `X-B3-Sampled` header
to every downstream hop, so a trace is either **fully captured end-to-end** or
**not captured at all** — you never get a half-sampled, broken trace with gaps.

---

## 4. Interview Q&A

**Q1: What's the difference between a trace ID and a span ID, and why do both
exist?**
A: Trace ID identifies the *entire* end-to-end request journey across all
services (constant across every hop). Span ID identifies *one specific unit of
work* within that journey (a new one is generated per hop/operation). Both
matter because you often need to zoom in ("how long did just the
`product-service` call take?" → span-level) as well as zoom out ("show me the
whole checkout flow" → trace-level).

**Q2 (tricky): If `order-service` calls `product-service` via a raw
`java.net.http.HttpClient` you wired up manually, bypassing RestTemplate/Feign/
WebClient entirely, does trace context propagate automatically?**
A: No — automatic header injection is provided by Micrometer Tracing's
**instrumentation** of specific HTTP client libraries (RestTemplate, WebClient,
Feign, RestClient). A raw, unwrapped `HttpClient` call has no automatic
instrumentation hook, so the B3 headers won't be injected unless you manually
propagate them yourself (reading the current `TraceContext` from Micrometer's
`Tracer` API and setting the headers by hand). This is a common real-world gap
— any "exotic" HTTP client not covered by auto-instrumentation silently breaks
the trace chain at that hop.

**Q3: Why is 100% trace sampling generally a bad idea in production, and is it
ever justified?**
A: Overhead (CPU for span creation/propagation, network/storage cost exporting
every span) scales with traffic — at high QPS, 100% sampling can materially
impact both the traced services' performance and the tracing backend's storage
costs. It *is* sometimes justified temporarily/selectively — e.g. tracing 100%
of a specific low-volume, business-critical flow (like payments) while sampling
everything else at 10%, using **per-endpoint** sampling rules rather than a
single global rate.

**Q4: A trace in Zipkin shows a 4-second gap between the `order-service` span
ending and the `payment-service` span beginning, even though `order-service`
calls `payment-service` directly (no queue in between). What are the likely
causes?**
A: Since it's a direct synchronous call with no messaging layer, a visible gap
between spans most likely points to something **outside the traced HTTP call
itself** eating time before the request is actually sent — e.g. connection pool
exhaustion (waiting to acquire an available connection from the pool before the
call can even start), DNS resolution delay, or client-side load balancer
resolution/retry logic (file 05) running before the actual instrumented HTTP
call begins. It's a good prompt to also check connection pool metrics
(Micrometer) for that specific downstream around the same time window.

---

## 5. Scenario Questions

**Scenario 1:** *"Customers report that 'sometimes checkout is slow', but your
team can't reproduce it and application-level metrics (average latency) look
fine. How do you actually find the root cause using the tools in this file?"*

Model answer: Average latency hides tail-latency problems (P99/P95) affecting a
minority of requests — exactly the "sometimes slow" pattern. Use Zipkin/tracing
filtered by **duration** (find the slowest traces for the checkout flow over
the relevant time window) rather than relying on aggregate metrics alone;
inspect the span waterfall for those specific slow traces to identify which
*specific* downstream hop (e.g. `payment-service`, or a specific DB query span
if DB calls are instrumented too) is responsible in the slow cases but not the
fast ones. Then pull the `traceId` of one of those slow traces and search it in
Kibana to see the actual application logs/exceptions from every service in that
one request's path, which usually reveals the "why" (e.g. lock contention,
GC pause, a retry that fired).

**Scenario 2:** *"Two services, `order-service` and `product-service`, are
fully instrumented with Micrometer Tracing and reporting to Zipkin. But traces
that should span both services always show up in Zipkin as two completely
separate, disconnected traces instead of one. What's the most likely
misconfiguration?"*

Model answer: This is the classic broken-propagation symptom — most likely
`order-service` calls `product-service` through an HTTP client that isn't
instrumented/auto-configured for tracing (e.g. a manually constructed
`RestTemplate` bean created *without* going through Spring's
auto-configured, tracing-aware `RestTemplateBuilder`, or a raw HTTP client as in
Q2 above), so the B3 propagation headers are never attached to the outgoing
call — `product-service` therefore sees no incoming trace context and starts a
brand-new trace instead of continuing the existing one. Fix: ensure every
outbound HTTP client used for inter-service calls is built via
Spring's auto-configured, tracing-instrumented builders/beans (or explicitly
verify the tracing bridge dependency is on the classpath and default
auto-configuration for that specific client type hasn't been bypassed).

---

[← 07. Spring Cloud Gateway](07-spring-cloud-gateway.md) | [README](README.md) | Next → [09. Security (OAuth2)](09-security-oauth2-microservices.md)
