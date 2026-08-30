# 07. Spring Cloud Gateway (and Zuul Legacy)

[← 06. Resilience & Circuit Breakers](06-resilience-circuit-breakers.md) | [README](README.md) | Next → [08. Distributed Tracing & Logging](08-distributed-tracing-logging.md)

---

## 1. Your Original Notes (cleaned up)

**Common cross-cutting requirements to enforce at the edge of a microservices
system:**
1. **Security** — apply security (authN/authZ) at the gateway level; it acts as
   a single entry point.
2. **Routing** — route requests to different versions of a microservice based on
   conditions.
3. **Transformation** — modify request and/or response bodies via filters
   attached to the gateway.
4. **Data Aggregation** — call multiple microservices and aggregate their
   responses into a single response back to the client.

**Two approaches to exposing services to clients:**

```
I-way (direct paths, one per service — from notes):
                                → product Service
Client → API Gateway → order Service
                                → payment Service

http://10.0.150/products
http://10.0.150/orders
http://10.0.150/payment
```

```
II-way (via discovery, from notes):
                        4 ────────→ product Service
                                              ↓ Register
                    2          Register →
                         Register → Eureka Server
Client → /product → product service ← 3.b
              1     ← 3.a
                              ↓ Register 2.b
                         order service
```

The Gateway sits between the client and the microservices, using Eureka to
discover the real, current locations of `product-service`/`order-service`
instead of the client needing to know them directly.

**Setup steps:**
- Setup Spring Cloud Gateway
- Configure microservices
- Configure routing rules
- Configure the Gateway to route through Eureka discovery

---

## 2. Deep Dive — What Your Notes Didn't Cover

### 2.1 Why Zuul 1.x was replaced (recap from file 02, applied here concretely)
Zuul 1.x is built on **blocking Servlet I/O** — each in-flight request at the
gateway ties up a thread for its entire duration (including waiting on the
downstream). Spring Cloud Gateway is built on **Project Reactor + Netty**
(non-blocking, event-loop based) — a small, fixed number of event-loop threads
can handle a very large number of concurrent in-flight requests, because threads
aren't blocked waiting on I/O. For a component that, by definition, sits in
front of *every* request to *every* service (i.e. the highest-traffic single
point in the whole system), this throughput difference is why Spring Cloud
Gateway replaced Zuul rather than just patching it.

### 2.2 Core vocabulary — Route, Predicate, Filter
A **Route** is the basic building block: an ID, a destination URI, a collection
of **Predicates** (conditions that must ALL match for the route to apply), and a
collection of **Filters** (transformations applied to the request/response as it
passes through).

```
Client Request
      │
      ▼
[ Predicates match? ] ──no──► try next route
      │ yes
      ▼
[ Pre Filters ]  (modify request: add headers, rewrite path, auth check...)
      │
      ▼
[ Proxy the actual downstream call ]
      │
      ▼
[ Post Filters ] (modify response: add headers, log, transform body...)
      │
      ▼
Client Response
```

### 2.3 Full working Gateway — YAML style (I-way and II-way from your notes,
implemented for real)

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

> **Important:** Spring Cloud Gateway uses **WebFlux (Netty)**, not
> Spring MVC (Tomcat) — do NOT add `spring-boot-starter-web` alongside it; they
> conflict (two different embedded servers/programming models).

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true            # "II-way": auto-create a route per Eureka-registered service
          lower-case-service-id: true
      routes:
        # "I-way": explicit, hand-written route (full control) — resolves via Eureka using lb://
        - id: product-service-route
          uri: lb://product-service     # lb:// = resolve via load balancer + discovery, NOT a literal host
          predicates:
            - Path=/api/products/**
          filters:
            - StripPrefix=1
            - AddRequestHeader=X-Gateway-Source, api-gateway

        - id: order-service-route
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
            - Method=GET,POST
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: orderServiceCB
                fallbackUri: forward:/fallback/orders

        # routing by version header — "different version of microservices" from your notes
        - id: order-service-v2
          uri: lb://order-service-v2
          predicates:
            - Path=/api/orders/**
            - Header=X-API-Version, v2

  config:
    import: "optional:configserver:http://localhost:8888"

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

`discovery.locator.enabled: true` is exactly your notes' "II-way" — Gateway
auto-generates a route `/product-service/**` → `lb://product-service` for
**every** service registered in Eureka, no manual route config needed. The
hand-written `routes:` list above is the "I-way" — explicit control (custom
paths, filters, per-route resilience) at the cost of manual maintenance. Real
production Gateways almost always use the explicit style for anything
client-facing, since auto-generated routes expose Eureka's internal service
names directly, which you usually don't want as public API paths.

### 2.4 Programmatic route configuration (Java DSL — equivalent to the YAML
above, useful when routing logic needs to be dynamic/conditional in code)

```java
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("product-service-route", r -> r
                .path("/api/products/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .addRequestHeader("X-Gateway-Source", "api-gateway"))
                .uri("lb://product-service"))
            .route("order-service-route", r -> r
                .path("/api/orders/**")
                .and().method(HttpMethod.GET, HttpMethod.POST)
                .filters(f -> f
                    .stripPrefix(1)
                    .circuitBreaker(c -> c
                        .setName("orderServiceCB")
                        .setFallbackUri("forward:/fallback/orders")))
                .uri("lb://order-service"))
            .build();
    }
}
```

### 2.5 Data Aggregation — the pattern your notes named but didn't implement
The classic "call multiple services, combine into one response" (item 4 in your
notes) is typically implemented via a dedicated **Backend-for-Frontend (BFF)**
service behind the Gateway, or a lightweight aggregation controller, using
reactive composition:

```java
@RestController
@RequiredArgsConstructor
public class OrderDetailsAggregationController {

    private final WebClient.Builder webClientBuilder;

    @GetMapping("/aggregated/orders/{id}")
    public Mono<OrderDetailsView> getOrderDetails(@PathVariable Long id) {
        WebClient webClient = webClientBuilder.build();

        Mono<OrderDto> orderMono = webClient.get()
            .uri("lb://order-service/api/orders/{id}", id)
            .retrieve().bodyToMono(OrderDto.class);

        Mono<CustomerDto> customerMono = webClient.get()
            .uri("lb://customer-service/api/customers/{id}", id)
            .retrieve().bodyToMono(CustomerDto.class);

        // fire both calls concurrently, combine when both complete
        return Mono.zip(orderMono, customerMono)
            .map(tuple -> new OrderDetailsView(tuple.getT1(), tuple.getT2()));
    }
}
```

Gateway itself typically isn't where you write this aggregation logic (it's a
routing/cross-cutting layer, not a business-logic layer) — aggregation usually
lives in a purpose-built downstream BFF service that the Gateway simply routes
to like any other service.

### 2.6 Global Filters — cross-cutting concerns applied to EVERY route

```java
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders()
            .getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header(CORRELATION_ID_HEADER, correlationId)
            .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;  // run first, before any route-specific filters
    }
}
```
This directly feeds file 08's distributed tracing — a correlation ID injected
once at the Gateway propagates through every downstream call.

### 2.7 Rate limiting at the Gateway (using Redis)

```yaml
- id: product-service-route
  uri: lb://product-service
  predicates:
    - Path=/api/products/**
  filters:
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: 20
        redis-rate-limiter.burstCapacity: 40
        key-resolver: "#{@userKeyResolver}"
```

```java
@Bean
KeyResolver userKeyResolver() {
    return exchange -> Mono.just(
        exchange.getRequest().getHeaders().getFirst("X-User-Id") != null
            ? exchange.getRequest().getHeaders().getFirst("X-User-Id")
            : "anonymous");
}
```

---

## 3. "Behind the Scenes"

**How `lb://product-service` differs from `http://product-service` at the
Gateway:** Spring Cloud Gateway ships a special `LoadBalancerClientFilter` /
`ReactiveLoadBalancerClientFilter` global filter that specifically recognizes
the `lb` URI scheme. When a route's `uri` starts with `lb://`, this filter
intercepts before the request is proxied, resolves the service name via the
reactive `LoadBalancerClient` (same discovery + LB machinery as file 05's
`@LoadBalanced RestTemplate`, just reactive), and rewrites the URI to a concrete
`host:port` before the actual downstream HTTP call is made by Gateway's Netty
HTTP client. Using a plain `http://product-service` (no `lb://`) would instead
attempt a literal DNS lookup for the hostname `product-service`, which will fail
unless you happen to have DNS/hosts-file entries for it — a very common
first-timer mistake.

---

## 4. Interview Q&A

**Q1: Why should you never put `spring-boot-starter-web` and
`spring-cloud-starter-gateway` in the same application?**
A: `spring-boot-starter-web` auto-configures a **Servlet-based** (blocking,
Tomcat by default) web application; Spring Cloud Gateway requires **WebFlux**
(non-blocking, Netty). Having both on the classpath causes Spring Boot's
auto-configuration to get confused about which web application type to
bootstrap, and even if it picks one, you lose Gateway's entire non-blocking
throughput advantage if it ends up running on a Servlet container. Keep the
Gateway module's classpath WebFlux-only.

**Q2 (tricky): What's the difference between `Predicates` and `Filters` in
Spring Cloud Gateway, and can a route have zero of either?**
A: A **Predicate** decides **whether** a route matches a given request (a
boolean condition — path, header, method, time-window, etc.); a **Filter**
**modifies** the request/response for a route that already matched. A route
needs at least one predicate that can match something (otherwise it can never
be selected, though `uri` alone with no predicate defaults to matching
everything, which is rarely intended) — filters are entirely optional; a route
with zero filters is a pure pass-through proxy.

**Q3: Your notes mention applying "Security" at the Gateway. Does that mean
downstream services (order-service, product-service) don't need their own
security checks?**
A: No — this is the classic **defense-in-depth** trap. The Gateway is a great
place for coarse-grained, edge-level security (reject unauthenticated traffic
before it even reaches internal services, rate limiting, WAF-style checks), but
downstream services should still enforce their **own** authorization (e.g. "can
*this* authenticated user access *this specific* order?") — never assume every
request that reaches an internal service was already fully authorized just
because it came through the Gateway, especially in environments where internal
services might also be reachable directly (misconfigured network policy,
service mesh east-west traffic, etc.). Full detail in file 09.

**Q4 (tricky): You add a `CircuitBreaker` filter on the `order-service` route
with a `fallbackUri`. When the circuit opens, does the client get an HTTP error
or a 200 with fallback content?**
A: Depends entirely on what the `fallbackUri` endpoint itself returns — the
circuit breaker filter forwards the request internally to that URI
(`forward:/fallback/orders` in the example) and returns *whatever that endpoint
produces* as the actual response. If that fallback controller returns a 200
with a degraded/cached payload, the client sees 200. If it deliberately returns
a `503` with a "service temporarily degraded" body, the client sees 503. Both
are valid design choices depending on whether the caller (often a frontend) can
usefully handle a degraded 200 payload or needs an explicit error signal to
show a retry/error UI.

---

## 5. Scenario Questions

**Scenario 1:** *"You migrate from `discovery.locator.enabled: true`
(auto-generated routes) to explicit hand-written routes for security reasons.
The day after the migration, mobile app users report `404` on
`/product-service/api/products/5`, a URL that worked yesterday."*

Model answer: `discovery.locator.enabled` auto-generates routes prefixed with
the **Eureka service ID** itself (`/product-service/**` → `lb://product-service`
— literally exposing the internal service name as a public path segment), while
a hand-written explicit route (as in 2.3) typically uses a clean public path
like `/api/products/**` with `StripPrefix` instead. The mobile client was
(incorrectly) built against the auto-generated, internal-service-name-based
path. Fix: don't silently break the contract — either keep a temporary explicit
route for the old `/product-service/**` path as an alias during a deprecation
window, and communicate the new `/api/products/**` path to mobile clients with
a migration deadline, rather than a hard cutover.

**Scenario 2:** *"Your Gateway proxies requests to `payment-service`, which
occasionally takes 15+ seconds under load. Gateway's own thread/connection pool
starts saturating, and even unrelated routes (like `/api/products/**`) start
timing out. What's happening and how do you fix it, tying together concepts
from files 06 and 07?"*

Model answer: Even though Spring Cloud Gateway is non-blocking (doesn't block a
thread per in-flight request the way Zuul 1 would), it still has finite
resources — Netty's event loop and, more relevantly, a bounded **connection
pool** to each downstream. A struggling `payment-service` holding many
connections open for 15+ seconds can exhaust the pool of connections available
to reach it, and depending on configuration, resource contention (memory
buffering slow responses, connection pool limits shared/misconfigured) can spill
over and affect unrelated routes. Fix: attach a **CircuitBreaker filter** (2.3)
scoped specifically to the `payment-service` route so it fails fast once
`payment-service` is clearly struggling, combine with a **per-route timeout**
(`spring.cloud.gateway.httpclient.connect-timeout` /
`response-timeout`, settable per-route too) so slow calls to `payment-service`
don't hold resources indefinitely, and ensure connection pools are configured
**per-route/per-downstream** rather than one shared global pool, so
`payment-service` degrading can't starve `product-service` traffic.

---

[← 06. Resilience & Circuit Breakers](06-resilience-circuit-breakers.md) | [README](README.md) | Next → [08. Distributed Tracing & Logging](08-distributed-tracing-logging.md)
