# 07 - Actuator

---

## Your notes (corrected) — why Actuator exists

After development is "complete," you still can't ship straight to production, because clients/ops teams need to **monitor** the app in production:
1. Health check
2. Metrics
3. Info
4. Thread dumps
5. Logs

Without a dedicated tool, the developer has to spend significant time building these utilities themselves, which means:
1. Time cost
2. Money/effort cost
3. Delayed production delivery

**To solve this, Spring Boot introduced Actuator.**

> Spring Boot Actuator = a set of prepackaged, production-ready endpoints commonly needed for monitoring and managing an application in production, built by the Boot team, that integrate with a Boot app by simply adding one dependency.

Using Actuator, a "dev-complete but not production-ready" application becomes production-ready with almost no extra code.

**To enable Actuator:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

---

## Actuator endpoints (your list, corrected & expanded)

| Endpoint | Purpose |
|---|---|
| `/actuator/info` | Application info (name, description, custom build/git info you configure) |
| `/actuator/health` | Readiness/liveness of the application (your note said `/healthcheck` — the actual path is `/health`) |
| `/actuator/env` | Shows all resolved environment properties (and where each came from) |
| `/actuator/beans` | Every bean definition currently in the IoC container |
| `/actuator/metrics` | Memory, CPU, HTTP request counts/latencies, custom metrics, etc. |
| `/actuator/loggers` | View and even **change** logger levels at runtime, without redeploying |
| `/actuator/mappings` | Every `@RequestMapping` registered — great for debugging "why is my endpoint 404ing" |
| `/actuator/threaddump` | JVM thread dump — for diagnosing deadlocks/high CPU |
| `/actuator/heapdump` | Downloads a heap dump — for diagnosing memory leaks |
| `/actuator/conditions` | The auto-configuration condition evaluation report (ties back to file 02) |
| `/actuator/shutdown` | Gracefully shuts the app down — **disabled by default** for security reasons |

By default, all endpoints are under the `/actuator/` prefix:
```
http://<host>/actuator/info
http://<host>/actuator/metrics
```

### Two exposure channels (your note, kept)

1. **JMX** (Java Management Extensions) — a protocol for managing/monitoring Java applications, typically accessed via JMX clients/tools (e.g. JConsole, VisualVM) rather than HTTP
2. **HTTP endpoints** — plain REST endpoints, called like any other API

---

## "Enabled" vs "Exposed" — the exact distinction that trips people up

Your note captured the key definitions correctly — worth stating precisely, because interviewers deliberately test this pair:

- **Enabled** = the endpoint is included as part of the application at all (its bean/logic exists)
- **Exposed** = the endpoint is actually reachable from the outside (via HTTP or JMX)

An endpoint being enabled does **not** automatically make it exposed. Both conditions must be true for you to actually be able to call it.

```properties
management.endpoint.shutdown.enabled=true
management.endpoint.info.enabled=true
management.endpoints.enabled-by-default=false   # disables ALL endpoints by default, opt in individually
```

- By default, **all** endpoints are **enabled** *except* `shutdown` (disabled for safety — you don't want a random unauthenticated caller able to kill your prod service).
- By default over **JMX**, all endpoints are exposed.
- By default over **HTTP/web**, only **2** endpoints are exposed out of the box: `health` and `info`. Everything else must be explicitly opted in.

```properties
# JMX exposure
management.endpoints.jmx.exposure.include=info,metrics
management.endpoints.jmx.exposure.exclude=env

# Web/HTTP exposure
management.endpoints.web.exposure.include=info,metrics,health,beans,loggers
management.endpoints.web.exposure.exclude=env

# Expose everything over HTTP (use with real caution in prod - see security note below)
management.endpoints.web.exposure.include=*
```

---

## Real-world example — production-safe Actuator config

In practice, teams never blindly do `include=*` on a public-facing service. A realistic production config:

```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=when-authorized
management.endpoint.shutdown.enabled=false
server.port=8080
management.server.port=8081   # actuator served on a SEPARATE internal port
```

Serving actuator on a **separate port** (`management.server.port`) is a very common real-world pattern — it lets you expose business APIs on 8080 to the public/load balancer while keeping actuator's sensitive diagnostic endpoints (`/env`, `/beans`, `/heapdump`) reachable only on an internal port that's firewalled off from the public internet, typically only scraped by an internal Prometheus/monitoring system.

### Custom health indicator (very common real interview + real job task)

```java
@Component
public class DownstreamPaymentServiceHealthIndicator implements HealthIndicator {

    private final PaymentServiceClient client;

    public DownstreamPaymentServiceHealthIndicator(PaymentServiceClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            client.ping();
            return Health.up().withDetail("payment-service", "reachable").build();
        } catch (Exception ex) {
            return Health.down(ex).withDetail("payment-service", "unreachable").build();
        }
    }
}
```

This automatically becomes part of the aggregate `/actuator/health` response, and Kubernetes/load balancers using that endpoint as a liveness/readiness probe will correctly mark the pod unhealthy if a critical downstream dependency is down.

---

## Tricky interview questions — Section 7

**Q1. `/actuator/health` returns `UP` but a critical downstream dependency (DB) is actually down — why, and how do you fix it?**
The default health indicator only checks what it's configured/auto-detected to check. If you haven't wired up a `DataSourceHealthIndicator` (usually auto-configured automatically when `spring-boot-starter-jdbc`/JPA is present) or a custom `HealthIndicator` for that dependency, Actuator has no way to know it's unhealthy. Fix: ensure the relevant starter is present (auto-configures the matching health indicator) or write a custom `HealthIndicator` like the example above.

**Q2. Why is exposing all Actuator endpoints (`include=*`) over the public internet a real security risk?**
Endpoints like `/env` can leak configuration values (including secrets if not masked), `/heapdump` can leak sensitive data held in memory (tokens, PII), `/shutdown` (if accidentally enabled) can let anyone kill the service, and `/beans`/`/mappings` leak internal architecture details useful for an attacker doing reconnaissance. Standard mitigation: expose only `health` and `info` publicly, put everything else behind a separate internal-only management port and/or Spring Security authentication, and mask sensitive property values (Boot auto-masks common secret-looking keys in `/env` by default, but don't rely on that alone).

**Q3. How would you secure Actuator endpoints with Spring Security so only an ops role can access `/actuator/**`?**
```java
@Bean
public SecurityFilterChain actuatorSecurity(HttpSecurity http) throws Exception {
    http.securityMatcher("/actuator/**")
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .anyRequest().hasRole("OPS"))
        .httpBasic(Customizer.withDefaults());
    return http.build();
}
```

**Q4. What's the difference between `/actuator/metrics` and integrating Prometheus/Grafana — don't they do the same thing?**
`/actuator/metrics` exposes Micrometer-collected metrics in Boot's own JSON format, browsable one metric at a time — fine for ad hoc debugging, not built for long-term storage/dashboards/alerting. Adding `micrometer-registry-prometheus` exposes the same underlying Micrometer metrics in Prometheus's scrape format at `/actuator/prometheus`, which a Prometheus server polls on an interval and stores as a time series — that's what actually powers dashboards/alerting in Grafana. In real production setups you use both starters together: Actuator/Micrometer is the *instrumentation* layer, Prometheus+Grafana is the *storage and visualization* layer.

**Q5. `/actuator/loggers` — what real production problem does this solve that redeploying doesn't?**
It lets you flip a specific logger's level (e.g. turn `com.hemant.orderservice.payment` from `INFO` to `DEBUG`) **at runtime**, on a live instance, via a POST request — no redeploy, no restart, no lost in-memory state — to investigate an active production issue, then flip it back down once you're done. This is one of the most operationally valuable and most under-appreciated Actuator endpoints in real incident response.
