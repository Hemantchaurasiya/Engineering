# 06. Building a Resilient System — Circuit Breakers, Retry, Time Limiter

[← 05. Interservice Communication](05-interservice-communication.md) | [README](README.md) | Next → [07. Spring Cloud Gateway](07-spring-cloud-gateway.md)

---

## 1. Your Original Notes (cleaned up)

**Problem Statement:** If a remote service doesn't provide the expected
behaviour (slow, erroring, down), calling it anyway impacts *your* resource
utilization too.

**Principle:** When some requests fail, instead of continuing to call the remote
service repeatedly, give a default/fallback response directly to the consumer.

### What is a Circuit Breaker?
1. A circuit breaker is an **integration-tier design pattern** used to protect
   client applications while accessing remote services.
2. It is **not** a Spring-specific feature — it's a general design pattern that
   can be applied anywhere a client accesses a remote service.
3. Remote services can become unresponsive due to:
   a. Heavy load
   b. Limitations at their end
4. If a client keeps hitting an unresponsive remote service anyway, it creates
   real problems:
   a. Longer waiting time for the caller.
   b. Caller becomes unresponsive itself due to blocked threads.
   c. Because threads are stuck blocked, the *client* application can crash too.

Instead of implementing the circuit breaker pattern manually, Spring Cloud
provides these third-party library integrations:
1. Netflix Hystrix *(legacy)*
2. Resilience4j *(modern, recommended)*
3. Spring Retry
4. Sentinel

**From the TOC (headings only in your notes, fleshed out below):**
- Implementing Resilience4j
- Time Limiter
- Retry Mechanism

---

## 2. Deep Dive — What Your Notes Didn't Cover

### 2.1 The Circuit Breaker state machine (the core mechanic your notes named
but never explained)

```
        failure rate > threshold
   CLOSED ─────────────────────────→ OPEN
     ▲                                 │
     │                                 │ wait-duration-in-open-state elapses
     │  success rate ≥ threshold       ▼
     └──────────────────────────  HALF_OPEN
                                  (allows a small number of
                                   trial calls through)
```

- **CLOSED** — normal operation, calls pass through to the real remote service.
  The breaker tracks a rolling window of success/failure.
- **OPEN** — failure rate crossed the configured threshold. Calls **fail
  immediately** (short-circuited) without even attempting the remote call — this
  is what actually protects the caller's threads from blocking on a struggling
  downstream.
- **HALF_OPEN** — after a wait duration, the breaker lets a limited number of
  **trial calls** through to check if the downstream has recovered. If they
  succeed at a high enough rate → back to CLOSED. If they still fail → back to
  OPEN.

**Interview trap:** many candidates think "circuit breaker = retry with
backoff." It is not — a circuit breaker's entire point is to **stop calling**
the downstream for a while (fail fast), which is the *opposite* of retrying
more. Retry and circuit breaker are complementary, different patterns (see 2.4).

### 2.2 Resilience4j — full production setup

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

```yaml
resilience4j:
  circuitbreaker:
    instances:
      productService:
        sliding-window-type: COUNT_BASED     # or TIME_BASED
        sliding-window-size: 10              # last 10 calls
        minimum-number-of-calls: 5           # don't evaluate until 5 calls happened
        failure-rate-threshold: 50           # OPEN if >=50% of the window failed
        slow-call-rate-threshold: 80
        slow-call-duration-threshold: 2s     # a call slower than this counts as "slow"
        wait-duration-in-open-state: 10s     # how long OPEN before trying HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
  retry:
    instances:
      productService:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - java.io.IOException
          - feign.RetryableException
        ignore-exceptions:
          - com.example.order.exception.ProductNotFoundException   # never retry a real 404!
  timelimiter:
    instances:
      productService:
        timeout-duration: 3s
  bulkhead:
    instances:
      productService:
        max-concurrent-calls: 20
  ratelimiter:
    instances:
      productService:
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 0s
```

**Programmatic wiring with Feign** (matches file 05's `ProductClient`):

```java
@FeignClient(
    name = "product-service",
    fallback = ProductClientFallback.class
)
public interface ProductClient {
    @GetMapping("/api/products/{id}")
    ProductDto getProduct(@PathVariable("id") Long id);
}

@Component
public class ProductClientFallback implements ProductClient {
    @Override
    public ProductDto getProduct(Long id) {
        // fallback response — degrade gracefully instead of surfacing an error
        return ProductDto.unavailable(id);
    }
}
```

```yaml
# enable circuit breaker on Feign globally
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
```

**Programmatic wiring without Feign** (e.g. around a WebClient/RestTemplate
call, using `Resilience4jCircuitBreakerFactory` or the annotation style):

```java
@Service
@RequiredArgsConstructor
public class ProductGateway {

    private final RestTemplate restTemplate;
    private final CircuitBreakerFactory circuitBreakerFactory;

    public ProductDto getProduct(Long id) {
        CircuitBreaker cb = circuitBreakerFactory.create("productService");
        return cb.run(
            () -> restTemplate.getForObject(
                "http://product-service/api/products/{id}", ProductDto.class, id),
            throwable -> ProductDto.unavailable(id)   // fallback
        );
    }
}
```

**Annotation style (Resilience4j-native, stacking multiple resilience
patterns):**

```java
@Service
public class ProductGateway {

    @CircuitBreaker(name = "productService", fallbackMethod = "productFallback")
    @Retry(name = "productService")
    @TimeLimiter(name = "productService")
    @Bulkhead(name = "productService")
    public CompletableFuture<ProductDto> getProduct(Long id) {
        return CompletableFuture.supplyAsync(() -> productClient.getProduct(id));
    }

    private CompletableFuture<ProductDto> productFallback(Long id, Throwable t) {
        return CompletableFuture.completedFuture(ProductDto.unavailable(id));
    }
}
```

**Order matters** when stacking annotations: Resilience4j applies them
outside-in as declared, roughly: `CircuitBreaker(Retry(TimeLimiter(Bulkhead(actual call))))`
— meaning the circuit breaker sees the *final outcome after* retries have been
exhausted, not each individual attempt. This is a genuinely tricky, frequently
misunderstood detail (see Q3 below).

### 2.3 Time Limiter — why it exists separately from `read-timeout`
Your notes name "Time Limiter" in the TOC without explaining it. It matters
specifically for **asynchronous/`CompletableFuture`-based** calls — a plain HTTP
client read-timeout doesn't apply once you're wrapping the call in your own
async logic; `TimeLimiter` enforces a wall-clock timeout on the `Future` itself,
independent of the underlying HTTP client's own timeout, and integrates with the
circuit breaker's failure counting (a timeout counts as a failure toward the
circuit breaker's failure rate).

### 2.4 Retry — the trap your notes' TOC also left unexplained
Retry means: if a call fails, **try again** (usually with backoff) before giving
up. Sounds simple, but two big traps:

1. **Never retry non-idempotent operations blindly.** Retrying a `POST
   /orders` (create order) after a timeout — where the first request may have
   actually *succeeded* server-side but the response was lost — can create a
   **duplicate order**. Only safely retry idempotent operations (GET, or
   PUT/DELETE designed idempotently), or use an **idempotency key** so the
   server can dedupe retried requests.
2. **Never retry business errors.** A `404 Product Not Found` or `400 Invalid
   Request` will fail identically on retry — retrying wastes time/resources and
   delays surfacing a real error to the user. Only retry **transient/infra**
   failures (timeouts, `503`, connection reset) — this is exactly why
   `retry-exceptions`/`ignore-exceptions` exist in the config above.

### 2.5 Bulkhead — the pattern your notes didn't mention at all
Named after ship bulkheads (compartments that stop one flooded section from
sinking the whole ship). Limits how many **concurrent calls** can be in-flight
to a given downstream, so a slow/stuck downstream can only tie up a *bounded*
number of caller threads/resources — protecting unrelated calls (to *other*
downstreams) sharing the same thread pool from being starved out. Two flavors in
Resilience4j:
- **Semaphore-based bulkhead** — limits concurrent calls via a semaphore
  (lightweight, works with any thread model).
- **ThreadPool-based bulkhead** — routes calls to a dedicated, isolated thread
  pool, so even if that pool saturates and queues, it can't consume the
  caller's *main* request-handling threads.

### 2.6 Hystrix legacy — what it looked like (for maintenance/interview context)

```java
@HystrixCommand(fallbackMethod = "getProductFallback",
    commandProperties = {
        @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "3000"),
        @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold", value = "10"),
        @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "50")
    })
public ProductDto getProduct(Long id) {
    return productClient.getProduct(id);
}

public ProductDto getProductFallback(Long id) {
    return ProductDto.unavailable(id);
}
```
Conceptually near-identical to Resilience4j's model (CLOSED/OPEN/HALF_OPEN,
fallback methods, thresholds) — Hystrix additionally offered thread-pool
isolation by default (heavier) and a real-time dashboard (**Hystrix Dashboard +
Turbine**), both now superseded by Resilience4j + Micrometer metrics +
Grafana/Prometheus.

---

## 3. "Behind the Scenes"

**Why does a circuit breaker prevent thread exhaustion, concretely?** Without
one, every incoming request that needs `product-service` blocks its own handler
thread for up to the full read-timeout while waiting on a struggling downstream.
If `product-service` is degraded and every caller request needs it, and
requests arrive faster than the timeout duration, the caller's finite thread
pool (e.g. Tomcat's default ~200 threads) fills up with threads all blocked
waiting — **new, unrelated** requests (even ones that don't need
`product-service` at all!) can't get a thread to be handled, and the *entire*
caller service appears down. This is the textbook definition of **cascading
failure**. An **OPEN** circuit breaker short-circuits calls to `product-service`
*before* a thread ever blocks on the network call — the fallback returns near-
instantly, freeing that thread immediately, so unrelated request-handling
capacity is preserved.

---

## 4. Interview Q&A

**Q1: What's the difference between a circuit breaker and a simple try/catch
with a fallback?**
A: A try/catch fallback still makes the network call **every single time**, even
while the downstream is completely dead — so you still pay the full
connect/read timeout cost on every request, still risk thread exhaustion under
load, and can't distinguish "occasionally failing" from "currently down." A
circuit breaker tracks failure *rate over a rolling window* and, once OPEN,
skips the network call entirely (fail fast) until a trial period says the
downstream has likely recovered.

**Q2 (tricky): Should a `404 Not Found` from `product-service` count as a
circuit-breaker "failure"?**
A: Generally **no** — a 404 is often a legitimate business outcome (product
genuinely doesn't exist), not evidence the *service* itself is unhealthy.
Counting business-error responses toward the circuit breaker's failure rate can
trip the breaker OPEN due to a burst of legitimate 404s (e.g. a client bug
querying invalid IDs), needlessly blocking *all* calls including valid ones.
Configure `ignore-exceptions`/a custom `Predicate<Throwable>`
(`recordExceptions`/`ignoreExceptions` in Resilience4j) so only true
infra/timeout/5xx failures count.

**Q3 (very tricky, frequently missed): You stack `@CircuitBreaker` +
`@Retry` on the same method with `@Retry` configured for 3 attempts. Does the
circuit breaker see 3 separate failure events per call, or 1?**
A: With the default Resilience4j aspect ordering, `@CircuitBreaker` wraps
*outside* `@Retry` — meaning Retry runs its full 3 attempts internally first,
and the circuit breaker only observes the **final outcome** (success, or
failure after all retries exhausted) as a single event. This matters a lot: if
you assumed the circuit breaker sees every individual retry attempt as its own
failure, you'd wildly overestimate how fast it opens. Always verify/explicitly
configure aspect order in production (`resilience4j.circuitbreaker.instances.
...` ordering config, or via `@Retry` + `@CircuitBreaker` ordering annotations)
rather than assume.

**Q4: Why combine a Time Limiter with a Circuit Breaker instead of relying only
on the HTTP client's own read-timeout?**
A: They serve different layers — the HTTP client's read-timeout bounds a single
low-level network call; `TimeLimiter` bounds the **overall async operation**
(which might involve more than just the HTTP call, e.g. serialization,
additional async composition) and, critically, its timeout events are what feed
the circuit breaker's failure tracking in an async pipeline — without it, a
hung `CompletableFuture` might never resolve at all, so the circuit breaker
would never even see a failure to count.

**Q5: What's the actual difference between a Bulkhead and a Circuit Breaker —
aren't they both about protecting a caller from a struggling downstream?**
A: Related but distinct axes of protection. **Circuit breaker** = protects
against a downstream that's failing **over time** (failure rate), by stopping
new calls once a threshold is crossed. **Bulkhead** = protects against a
downstream that's simply **slow right now**, by capping *concurrency* — even if
every call would eventually succeed, too many concurrent slow calls can still
exhaust caller resources; a bulkhead caps that blast radius regardless of
success/failure rate. In production you typically want both, plus a Time
Limiter and sane Retry — Resilience4j is explicitly designed to compose all
four.

---

## 5. Scenario Questions

**Scenario 1:** *"`payment-service` starts timing out intermittently — about 30%
of calls. Your circuit breaker is configured with `failure-rate-threshold: 50`.
Requests keep flowing through at full volume and your team notices `order-
service` latency creeping up, but the breaker never opens. What's wrong, and how
do you fix it?"*

Model answer: A 30% failure rate is genuinely below your configured 50%
threshold, so the breaker is behaving correctly per its config — the real
problem is the threshold/window tuning doesn't reflect the actual cost of
*slow* (not just failed) calls. Fix: add/lower `slow-call-rate-threshold` and
`slow-call-duration-threshold` so **slow** calls (even ones that eventually
"succeed") also count toward tripping the breaker — a call that takes 8 seconds
to succeed is still tying up caller threads just as badly as an outright
failure, and Resilience4j explicitly supports counting slow calls separately
from failed calls for exactly this reason.

**Scenario 2:** *"A teammate adds `@Retry(maxAttempts=5)` to a Feign client
method that calls `POST /orders` (create order) with no idempotency key, to
'make it more resilient to network blips.' Three weeks later, customers report
occasional duplicate orders after checkout, especially on flaky mobile
connections. Explain the root cause and the correct fix."*

Model answer: This is exactly the non-idempotent-retry trap (2.4) — on a flaky
connection, the original request may have reached the server and created the
order successfully, but the *response* was lost before the client saw it
(timeout from the client's perspective, success from the server's). The retry
then sends a *second* `POST /orders`, creating a duplicate, because the server
has no way to know "this is the same logical request as before." Correct fix:
generate a client-side **idempotency key** (e.g. a UUID) per logical order
attempt, send it as a header, and have `order-service`'s create-order endpoint
deduplicate — if it sees the same idempotency key again, return the
*original* result instead of creating a second order. Only then is it safe to
retry `POST /orders`.

---

[← 05. Interservice Communication](05-interservice-communication.md) | [README](README.md) | Next → [07. Spring Cloud Gateway](07-spring-cloud-gateway.md)
