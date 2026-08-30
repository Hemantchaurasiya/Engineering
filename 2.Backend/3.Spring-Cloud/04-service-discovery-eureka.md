# 04. Service Discovery Using Netflix Eureka

[← 03. Config Server](03-config-server-and-config-client.md) | [README](README.md) | Next → [05. Interservice Communication](05-interservice-communication.md)

---

## 1. Your Original Notes (cleaned up)

**Problem: IP addresses are not stable in the cloud.**
1. During autoscaling, service instances restart, get replaced, or scale
   in/out — their IP address changes (or a same IP can get reused differently).
2. If Order Service wants to call Product Service, it needs to know Product
   Service's *current* location — but that location keeps changing.
3. **Rule: never hardcode IP address/hostname/port of another microservice.**

**Q: If we can't hardcode the IP, how do we find another microservice?**
**A: Eureka Server** maintains the up-to-date list of IP addresses/instances for
every registered microservice.

```
(Table of microservice instances)
App name        | instance details
Config Server    9.00.8888
                  9.00.9050
Product Service   6.0.0.8800
                   6.00.9911
Order Service     7.0.0.8002
                   7.0.0.9022
```

- During startup, each microservice **registers** itself with Eureka Server
  (stores its own instance details there).
- **Eureka Server = Service Discovery** — stores the instance/IP list of every
  registered microservice.

### The problem with DNS-based discovery (mentioned in TOC, expand below in
Deep Dive since original notes didn't detail it)

### How to call another service — two approaches
**Use Case 1 — RestTemplate with a hardcoded URL** (monolith-style): works fine
only when the target's hostname/IP never changes (not true in microservices).

**Use Case 2 — Discovery Client / Ribbon / Feign Client:** never hardcode the
host — ask Eureka for the current instance list first, then call.

**Implementation Steps:**
1. Set up Eureka Server.
2. Develop `product-service`, `order-service`.
3. Register both with Eureka Server.
4. `order-service` calls `product-service`:
   a. `order-service` asks Eureka Server for the instance/IP list of
      `product-service`.
   b. Write the REST client code in `order-service`.
   c. Invoke `product-service`.

**Note (from original notes):** Eureka Server internally uses a server-side load
balancer, but this load balancer can't hold request state — so traffic can end up
routed to only one instance repeatedly. This is what motivates **client-side**
load balancing (Ribbon/Spring Cloud LoadBalancer) — Eureka's `DiscoveryClient`
fetches the instance list from Eureka and hands it to the client-side balancer,
which then invokes the other microservice directly. (Full detail in file 05.)

---

## 2. Deep Dive — What Your Notes Didn't Cover

### 2.1 The problem with DNS-based service discovery (this was a TOC heading
in your notes with no content — filling it in)
Traditional DNS-based discovery (round-robin DNS, or a single load-balanced
hostname) has real limitations for microservices:
- **DNS caching/TTL** — clients (and OS-level resolvers) cache DNS answers for a
  TTL; when an instance dies, clients keep sending traffic to it until the cache
  expires — could be minutes.
- **No health awareness** — DNS doesn't know if an instance is *actually* healthy,
  only whether an A-record exists; a hung-but-not-crashed instance keeps
  receiving traffic.
- **No rich metadata** — DNS can't easily expose metadata like instance status
  (STARTING, UP, OUT_OF_SERVICE), zone/region, or version, which client-side load
  balancers need for intelligent routing.
- **Registration lag** — updating DNS records for a newly started instance is
  typically slower than an application-level heartbeat-based registry like
  Eureka, where an instance can be discoverable within seconds.

Eureka solves all four: application-level heartbeats (30s default), health-aware
status, rich instance metadata, and near-real-time registry updates via client
caching + delta fetches (see 2.3).

### 2.2 Full working Eureka Server

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

```yaml
# eureka-server application.yml
server:
  port: 8761

eureka:
  client:
    # a Eureka server, by default, is ALSO a client of itself (registers with
    # peers) — disable this for a single-node, standalone dev setup
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://localhost:8761/eureka/
  server:
    enable-self-preservation: true   # keep true in prod! (see 2.4)
    eviction-interval-timer-in-ms: 60000
```

For a **highly-available Eureka cluster** (production), each node registers
*with* the others (peer awareness), so `register-with-eureka`/`fetch-registry`
would be `true` and `defaultZone` would point at the *peer* nodes, not itself.

### 2.3 Full working Eureka Client (registering a service)

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

```java
@SpringBootApplication
// @EnableDiscoveryClient is optional as of Spring Cloud 2020.0+ if the
// eureka-client starter is on the classpath — auto-configured automatically.
// Explicit annotation is still fine and often kept for clarity.
@EnableDiscoveryClient
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
```

```yaml
# product-service application.yml
spring:
  application:
    name: product-service     # <-- this is the App Name Eureka registers under

server:
  port: 0    # 0 = random port — great for running multiple local instances

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    registry-fetch-interval-seconds: 5     # how often client refreshes local registry cache
  instance:
    prefer-ip-address: true                # register IP, not hostname (important in containers)
    lease-renewal-interval-in-seconds: 10   # heartbeat frequency (default 30 — lowered here for faster failure detection)
    lease-expiration-duration-in-seconds: 30
```

### 2.4 Self-Preservation Mode — the classic Eureka interview trap
If Eureka Server stops receiving heartbeats from **>15% of instances within a 15
minute window**, it assumes this is a **network partition**, not that all those
instances actually died — and **stops evicting instances**, even ones that
haven't sent a heartbeat in a while. This is Eureka's **AP** (Availability over
Consistency) behavior in action (see file 01, section 2.1): it would rather serve
a slightly stale registry (including some dead instances) than aggressively
evict everything and leave clients with an empty/near-empty registry during what
might just be a transient network blip.

```
WARN: EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP WHEN THEY'RE NOT.
RENEWALS ARE LESSER THAN THRESHOLD AND HENCE THE INSTANCES ARE NOT BEING EXPIRED JUST TO BE SAFE.
```

**Trade-off to know:**
- `enable-self-preservation: true` (**default, recommended in production**) —
  safer against false-positive mass-eviction during network issues, at the cost
  of possibly routing some traffic to genuinely-dead instances until they're
  eventually cleaned up.
- `enable-self-preservation: false` — commonly disabled **only in local dev/single
  Eureka instance setups** so dead instances are evicted promptly during testing
  — **never disable this in a real multi-instance production cluster.**

### 2.5 Client-side registry caching — why Eureka clients survive a Eureka
Server outage
The Eureka *client* doesn't call Eureka Server on every single service
invocation — it fetches the full registry once, then periodically fetches only
the **delta** (`registry-fetch-interval-seconds`, default 30s) and keeps a local
in-memory copy. This means:
- If Eureka Server goes down, already-running clients **keep working** off their
  last-known-good local registry cache (this is a deliberate resilience
  feature — Eureka Server being a temporary SPOF for discovery *lookups* doesn't
  take down inter-service calls that are already happening).
- Only **newly starting** instances that haven't fetched a registry yet, or
  instances needing to discover a *brand-new* service they've never cached, are
  actually blocked by a Eureka Server outage.

### 2.6 Eureka instance states
`STARTING → UP → DOWN / OUT_OF_SERVICE`. Spring Boot Actuator's health indicator
can automatically flip an instance's Eureka status to `OUT_OF_SERVICE` if a
health check fails, without deregistering it — useful for graceful maintenance
mode (`eureka.client.healthcheck.enabled: true` to wire Actuator health into
Eureka status).

### 2.7 Reading the registry programmatically

```java
@RestController
@RequiredArgsConstructor
public class DiscoveryDemoController {

    private final DiscoveryClient discoveryClient;

    @GetMapping("/instances/{serviceName}")
    public List<ServiceInstance> getInstances(@PathVariable String serviceName) {
        return discoveryClient.getInstances(serviceName);
        // returns URI, host, port, metadata for every UP instance of that service
    }
}
```

---

## 3. "Behind the Scenes"

**Registration flow, step by step:**
1. On startup, the Eureka client sends a `POST /eureka/apps/{APP-NAME}` with its
   `InstanceInfo` (host, port, health check URL, metadata, status=STARTING).
2. Every 30s (configurable) it sends a **heartbeat**: `PUT
   /eureka/apps/{APP-NAME}/{instance-id}` — this *renews the lease*.
3. If a lease isn't renewed within `lease-expiration-duration-in-seconds`
   (default 90s), Eureka Server marks it eligible for **eviction** (unless
   self-preservation has kicked in — see 2.4). An eviction sweep runs every
   `eviction-interval-timer-in-ms` (default 60s).
4. On graceful shutdown, the client sends `DELETE
   /eureka/apps/{APP-NAME}/{instance-id}` — proactive de-registration, faster
   than waiting for lease expiry.
5. Other clients don't hit Eureka Server per-call — they pull the registry (full,
   then incremental deltas) into a local cache on their own schedule (2.5).

This is exactly why the numbers in your notes' "instance table" example are
**eventually consistent**, not real-time — there's a registration delay, a
heartbeat interval, a client fetch interval, and an eviction sweep interval, each
adding latency to how fast the *actual* topology (a new instance is up, or a dead
instance is gone) propagates to every client.

---

## 4. Interview Q&A

**Q1: Why is Eureka considered an AP system, not a CP system?**
A: During a network partition, Eureka Server prioritizes remaining **available**
and continuing to answer registry queries (even with possibly stale/incorrect
data — see self-preservation), rather than refusing to serve or aggressively
evicting instances it can't confirm are alive. A CP system would instead refuse
to serve queries it can't guarantee are consistent.

**Q2 (tricky): If Eureka Server goes completely down for 10 minutes, does
`order-service` immediately fail to call `product-service`?**
A: No, not immediately — because of client-side registry caching (2.5),
`order-service` keeps using its last cached registry of `product-service`
instances for inter-service calls. It only becomes a real problem for: (a)
**newly starting** instances that need a fresh registry fetch, and (b) if the
outage is long enough that real topology changes (scale-in/scale-out, actual
crashes) diverge significantly from the stale cache, causing calls to land on
now-dead instances (mitigated by client-side retries/circuit breakers, file 06).

**Q3: What does `prefer-ip-address: true` do and why does it matter in
containers?**
A: By default, Eureka registers an instance's **hostname**. In Docker/Kubernetes,
container hostnames are often internal/ephemeral and not resolvable by other
services outside that container's network namespace, whereas the container's IP
usually is directly routable within the cluster network. Setting
`prefer-ip-address: true` registers the IP instead, avoiding DNS resolution
failures when another service tries to call it.

**Q4 (tricky): Two instances of `order-service` register with different lease
renewal intervals — one at the default (30s), one misconfigured to 5 minutes. Is
that even allowed, and what happens?**
A: Yes, it's allowed (lease timing is a per-instance client configuration, not
enforced centrally by Eureka Server). The instance heartbeating every 5 minutes
risks being evicted if `lease-expiration-duration-in-seconds` (default 90s) is
shorter than its heartbeat interval — it will *never* renew in time and will be
evicted repeatedly, causing that instance to flap in and out of the registry.
Lesson: renewal interval must always be meaningfully shorter than the expiration
duration.

**Q5: Eureka vs Kubernetes-native service discovery — when would you still
choose Eureka in 2026?**
A: If you're **not** deploying on Kubernetes (VMs, on-prem, Cloud Foundry, mixed
environments), or you need Eureka's rich client-side metadata/zone-awareness
features that K8s' basic Service/DNS discovery doesn't give you out of the box.
If you're fully on Kubernetes, K8s Services + kube-dns (or a service mesh like
Istio) typically replace Eureka entirely, removing an extra moving part.

---

## 5. Scenario Questions

**Scenario 1:** *"During a routine network maintenance window, 40% of your
`order-service` instances briefly stop sending heartbeats to Eureka for 3
minutes, then resume normally. What does Eureka Server do, and why is that
correct?"*

Model answer: Since more than 15% of renewals dropped, Eureka Server's
self-preservation mode kicks in and it **stops evicting** any instance,
including the ones that missed heartbeats — because losing >15% of renewals in a
short window looks like a network partition, not mass instance death. This is
the correct/safe behavior: evicting those instances would have removed
perfectly healthy instances from the registry just because of a transient
network blip, causing unnecessary call failures on the *remaining* healthy
instances. Once heartbeats resume, the affected instances simply renew their
lease again and nothing needed fixing.

**Scenario 2:** *"You deploy a new version of `product-service`. Old instances
are being terminated as part of a rolling deploy, but for about 20 seconds,
`order-service` calls still occasionally fail with connection refused against
the old, now-dead instance IPs. Why, and how do you reduce this window?"*

Model answer: This is exactly the client-side caching effect (2.5) plus
heartbeat/eviction timing — `order-service`'s local registry cache doesn't
instantly reflect that the old instance is gone; it only refreshes every
`registry-fetch-interval-seconds` (default 30s), and Eureka Server itself might
not have even evicted the dead instance yet if it exited abnormally rather than
sending the `DELETE` de-registration call on shutdown. Reduce the window by: (1)
ensuring the old instances are given a graceful shutdown that explicitly
de-registers (Spring Boot handles this automatically on `SIGTERM` if using the
Eureka client's lifecycle hooks — just don't `kill -9`), (2) lowering
`registry-fetch-interval-seconds` on clients, and (3) layering a **circuit
breaker + retry** (file 06) on the calling side so a handful of failed calls to a
just-terminated instance automatically retry against a different, live instance
instead of surfacing as a user-facing error.

---

[← 03. Config Server](03-config-server-and-config-client.md) | [README](README.md) | Next → [05. Interservice Communication](05-interservice-communication.md)
