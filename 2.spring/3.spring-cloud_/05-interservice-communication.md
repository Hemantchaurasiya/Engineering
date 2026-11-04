# 05. Microservices Interservice Communication

[← 04. Eureka](04-service-discovery-eureka.md) | [README](README.md) | Next → [06. Resilience & Circuit Breakers](06-resilience-circuit-breakers.md)

---

## 1. Your Original Notes (cleaned up)

### Server-Side Load Balancing — problems
1. Even with multiple instances running, load isn't distributed properly — the
   same instance often keeps getting picked because the balancer can't hold
   per-request state cleanly at scale.
2. If you have 100 microservices, needing 100 separate load balancers is
   expensive.
→ To overcome these, prefer **client-side load balancing**.

### Client-Side Load Balancing
1. The client itself is aware of how many instances are running.
2. It makes sure requests aren't always sent to the same instance.
3. **Ribbon** is the (legacy) tool used to implement client-side load balancing.
4. Use Ribbon if you're running multiple instances **without** autoscaling.
5. Use client-side load balancing generally whenever multiple instances run
   without a dedicated load balancer in front.

**Implementation (Ribbon-era):**
1. Create a `RestTemplate` bean annotated with `@LoadBalanced`.
2. In the URL, use the **application/service name** registered with Eureka
   instead of a raw IP/port.

### Open Feign
1. `spring-cloud-starter-openfeign` is a **declarative** client library to call a
   microservice/REST endpoint without writing boilerplate HTTP client code.
2. Create a declarative client interface using `@FeignClient`.

**Note:** Feign is conceptually similar to using Ribbon/LoadBalancer under the
hood, but removes boilerplate entirely — you just declare an interface annotated
with `@FeignClient`.

Load balancers internally use **Round Robin** and **LRU** (and other) algorithms
to route requests across instances.

**Q: How do you achieve inter-service communication in a microservices
architecture?**
**A:**
1. `DiscoveryClient` (manual, using Eureka's client directly)
2. Ribbon Client (legacy client-side LB)
3. Feign Client (declarative REST client)

---

## 2. Deep Dive — What Your Notes Didn't Cover

### 2.1 RestTemplate is in maintenance mode too!
As of Spring 5, `RestTemplate` itself is in **maintenance mode** — Spring
recommends `WebClient` (reactive, from Spring WebFlux, but usable in a blocking
style too via `.block()`) for new code. `RestTemplate` still works and is still
extremely common in existing codebases/interview questions, but a senior
candidate should know the modern picture:

| Client | Style | Status |
|---|---|---|
| `RestTemplate` | Blocking, synchronous | Maintenance mode — don't use for **new** code |
| `WebClient` | Reactive, non-blocking (also usable blocking) | Recommended |
| `RestClient` | Blocking, synchronous, fluent (Spring 6.1+/Boot 3.2+) | Recommended for new **blocking** code |
| `OpenFeign` | Declarative (wraps an HTTP client underneath) | Recommended for declarative style |

### 2.2 Client-side LB with the modern `spring-cloud-starter-loadbalancer`
Ribbon's `@LoadBalanced RestTemplate` pattern still works, just backed by Spring
Cloud LoadBalancer instead of Ribbon under the hood as of recent Spring Cloud:

```java
@Configuration
public class RestClientsConfig {

    @Bean
    @LoadBalanced   // <-- resolves "http://product-service/..." via Eureka + LB
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RestTemplate restTemplate;

    public ProductDto getProduct(Long productId) {
        // note: "product-service" here is the Eureka application name,
        // NOT a hostname — @LoadBalanced intercepts and resolves it.
        return restTemplate.getForObject(
            "http://product-service/api/products/{id}",
            ProductDto.class,
            productId
        );
    }
}
```

Load-balancer strategy is pluggable — default is a simple **round-robin**
(`RoundRobinLoadBalancer`), but you can swap in a weighted/zone-aware strategy:

```java
@Configuration
@LoadBalancerClient(name = "product-service", configuration = ProductServiceLbConfig.class)
public class ProductServiceLbConfig {

    @Bean
    public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
            Environment environment, LoadBalancerClientFactory factory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new RoundRobinLoadBalancer(
            factory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
    }
}
```

### 2.3 OpenFeign — the production-grade version

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

```java
@SpringBootApplication
@EnableFeignClients   // scans for @FeignClient interfaces
public class OrderServiceApplication { ... }
```

```java
// declarative client — no implementation needed, Feign generates a proxy at runtime
@FeignClient(
    name = "product-service",            // Eureka app name (also used for LB + circuit breaker naming)
    fallback = ProductClientFallback.class,   // see file 06 for resilience wiring
    configuration = ProductClientConfig.class
)
public interface ProductClient {

    @GetMapping("/api/products/{id}")
    ProductDto getProduct(@PathVariable("id") Long id);

    @PostMapping("/api/products/{id}/reserve")
    ReservationResponse reserveStock(@PathVariable("id") Long id,
                                      @RequestBody ReserveStockRequest request);
}
```

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductClient productClient;   // just inject the interface — Feign wires the impl

    public OrderDto placeOrder(PlaceOrderRequest req) {
        ProductDto product = productClient.getProduct(req.productId());
        // ... business logic
        return orderRepository.save(toEntity(req, product));
    }
}
```

Feign-specific config (timeouts, logging, error handling) — important, often
missed:

```java
public class ProductClientConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;   // NONE / BASIC / HEADERS / FULL
    }

    @Bean
    Request.Options requestOptions() {
        return new Request.Options(
            2000, TimeUnit.MILLISECONDS,   // connectTimeout
            5000, TimeUnit.MILLISECONDS,   // readTimeout
            true                            // followRedirects
        );
    }

    @Bean
    ErrorDecoder errorDecoder() {
        return (methodKey, response) -> switch (response.status()) {
            case 404 -> new ProductNotFoundException("Product not found");
            case 409 -> new StockConflictException("Insufficient stock");
            default -> new RetryableException(
                response.status(), "Product service error", null, null, response.request());
        };
    }
}
```

```yaml
# application.yml — connection/read pool tuning for Feign (backed by Apache HttpClient5 or OkHttp)
spring:
  cloud:
    openfeign:
      httpclient:
        hc5:
          enabled: true
      client:
        config:
          product-service:
            connect-timeout: 2000
            read-timeout: 5000
            logger-level: basic
```

### 2.4 Round Robin vs LRU vs Weighted — what your notes glossed over
- **Round Robin** — cycles instances in order 1,2,3,1,2,3... Simple, assumes all
  instances are equally capable and equally loaded — often *not* true right after
  a scale-out event (new instance is still warming up JIT/connection pools).
- **Least Response Time / LRU-based** — routes to the instance that has recently
  responded fastest / been used least — better under uneven load, more complex to
  implement correctly (needs live latency stats).
- **Weighted** — some instances (e.g. bigger VM size) get proportionally more
  traffic.
- **Zone/Region-aware** — prefer instances in the same availability zone to
  reduce cross-zone network cost/latency, only falling back cross-zone if the
  local zone has no healthy instances.

### 2.5 What "declarative" actually buys you with Feign, concretely
Compare the boilerplate:

```java
// RestTemplate — imperative, you manage everything
ResponseEntity<ProductDto> response = restTemplate.exchange(
    "http://product-service/api/products/{id}", HttpMethod.GET,
    null, ProductDto.class, id);
if (response.getStatusCode().is2xxSuccessful()) {
    return response.getBody();
}
// ... manual error handling
```

```java
// Feign — declarative, the interface method signature IS the contract
ProductDto product = productClient.getProduct(id);
// serialization, URL building, LB resolution, error decoding all handled by Feign
```

Feign interfaces can even be extracted into a **shared client library** (a
tiny JAR published by the `product-service` team) so consumers get a
compile-time-checked contract instead of hand-copying URLs/DTOs — a common
real-world pattern, though it does re-introduce a form of coupling (a shared
JAR dependency) that some teams deliberately avoid in favor of independently
copied DTOs per consumer.

---

## 3. "Behind the Scenes"

**How `@LoadBalanced RestTemplate`/Feign actually resolve `http://product-service/...`:**
1. `@LoadBalanced` registers a `ClientHttpRequestInterceptor` (for RestTemplate)
   or, for Feign, a `Client` implementation backed by
   `FeignBlockingLoadBalancerClient`.
2. On each call, this interceptor extracts the **host** part of the URL
   (`product-service`) and treats it not as a DNS hostname but as a **Eureka
   application name**.
3. It asks the `LoadBalancerClient` (Spring Cloud LoadBalancer) to pick one
   concrete `ServiceInstance` from the current registry (fetched via
   `DiscoveryClient`, which itself reads from the Eureka client's locally cached
   registry — see file 04, section 2.5).
4. The load-balancer strategy (Round Robin by default) selects one instance's
   actual host:port.
5. The interceptor **rewrites** the request URI, swapping `product-service` for
   the real `host:port`, and lets the underlying HTTP client (RestTemplate's
   `ClientHttpRequestFactory`, or Feign's configured client) actually execute it.

This is exactly why the service name in the URL is never resolved via real DNS —
it's a **logical name resolved via the load balancer + discovery client**, not a
network hostname lookup at all.

---

## 4. Interview Q&A

**Q1: Why is client-side load balancing generally preferred over a traditional
server-side load balancer (like an ELB/nginx) in microservice-to-microservice
calls?**
A: A dedicated server-side LB per service (as your notes point out) doesn't
scale operationally to hundreds of services — one LB per pair of communicating
services is expensive and adds a network hop + latency for every call. Baking
load-balancing logic into the client instead removes that extra hop and extra
infra, at the cost of the LB logic living in every service (mitigated today by
letting a shared library/starter — Spring Cloud LoadBalancer — handle it
uniformly).

**Q2 (tricky): Your `@FeignClient` method throws `FeignException.NotFound` for
a 404. Is that always the right way to detect "product doesn't exist" versus
"network/config problem"?**
A: Not entirely — a bare `FeignException` subclass check conflates a legitimate
business 404 (product genuinely doesn't exist) with, potentially, a
misconfigured route on the Gateway/Feign client returning 404 for an entirely
different resource. Best practice (shown in 2.3) is a custom `ErrorDecoder` that
maps specific HTTP status codes to specific, meaningful exception types
(`ProductNotFoundException` vs a generic `RetryableException`), so callers can
distinguish "expected business outcome" from "infrastructure/transient failure"
— which also matters for what should and shouldn't trigger a **retry** (file 06).

**Q3: What's the actual difference between Ribbon-era client-side LB and Spring
Cloud LoadBalancer?**
A: Functionally similar role (both pick an instance client-side), but Spring
Cloud LoadBalancer is reactive-first (built to work naturally with WebFlux/
WebClient, not just blocking RestTemplate), has a simpler pluggable SPI
(`ServiceInstanceListSupplier`/`ReactorServiceLoadBalancer`), and doesn't carry
Ribbon's legacy Netflix-specific config properties (`ribbon.*`) — configuration
is unified under `spring.cloud.loadbalancer.*`.

**Q4 (tricky): You configure a 2-second connect timeout and 5-second read
timeout on your Feign client. A downstream call takes 4 seconds due to DB load
on `product-service`. Does the Feign call fail?**
A: No — read timeout (5s) is the ceiling for the total time waiting for a
response *after* the connection is established; a 4-second response completes
successfully within that window. Connect timeout (2s) only bounds how long
establishing the TCP/TLS connection itself may take. Candidates often confuse
these two timeouts — know the distinction cold.

**Q5: Why might a naive Round Robin strategy actually hurt performance right
after a scale-out event?**
A: A freshly-started instance has a cold JVM (JIT not warmed up), cold
connection pools (DB, downstream HTTP clients), and possibly cold caches — Round
Robin sends it the **same share** of traffic as fully-warmed instances
immediately, which can cause elevated latency/errors on that instance right when
it's least ready. Mitigations: slow-start/ramp-up load-balancing strategies,
readiness probes that delay registering the instance as `UP` until it's actually
warmed, or weighted LB that starts new instances at low weight.

---

## 5. Scenario Questions

**Scenario 1:** *"`order-service` calls `product-service` via a `@LoadBalanced
RestTemplate`. During a deploy, `product-service` has 3 old instances and 2 new
instances (rolling deploy) registered simultaneously. Some `order-service`
requests intermittently get a schema/response shape they don't expect. Diagnose
and fix."*

Model answer: This is a classic **rolling deploy contract mismatch** — Round
Robin doesn't distinguish old vs new instance versions, so `order-service` (an
old consumer, or a consumer expecting the new contract) can land on either
version non-deterministically mid-rollout. Fix at the API level, not the LB
level: enforce **backward-compatible** API changes during rollout (additive
fields only, never remove/rename fields consumers depend on until *all*
consumers have migrated), and/or use **API versioning** (e.g. `/api/v2/products`)
so old and new contracts can coexist safely during the transition, exactly as
called out in the "Breaking Change" advantage in file 01's notes.

**Scenario 2:** *"A Feign client to `payment-service` has no explicit timeout
configuration. During a payment-service incident, `order-service` requests start
piling up and `order-service` itself becomes unresponsive under load. Explain
what happened and how you'd prevent it."*

Model answer: With no explicit `connect-timeout`/`read-timeout`, Feign falls
back to defaults (historically very long, e.g. 60s or more depending on the
underlying HTTP client) — so each stuck call to a struggling `payment-service`
ties up an `order-service` request-handling thread for far too long. Under
enough concurrent traffic, `order-service`'s own thread pool exhausts, and it
becomes unresponsive too — a classic **cascading failure**, exactly what file 06
(Circuit Breakers) exists to prevent. Fix: set explicit, tight
connect/read timeouts on every Feign client, and wrap the call in a
**circuit breaker + bulkhead** so a struggling downstream fails fast and in
isolation instead of exhausting the caller's own resources.

---

[← 04. Eureka](04-service-discovery-eureka.md) | [README](README.md) | Next → [06. Resilience & Circuit Breakers](06-resilience-circuit-breakers.md)
