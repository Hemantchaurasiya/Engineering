# 02. The Evolution of Spring Cloud — BOM, Release Trains & the Netflix OSS Stack

[← 01. Introduction](01-introduction-monolith-vs-microservices.md) | [README](README.md) | Next → [03. Config Server](03-config-server-and-config-client.md)

---

## 1. Your Original Notes (cleaned up)

Spring Cloud provides the tooling to discover, integrate, monitor and manage
microservices — originally by wrapping Netflix's open-source tools:

- **Netflix Eureka** — a discovery server
- **Netflix Ribbon** — a client-side load balancer
- **Netflix Zuul** — an edge server (API Gateway)
- **Netflix Hystrix** — a circuit breaker

Netflix OSS tools work, but using them directly requires a lot of boilerplate.
**Spring Cloud = uses Netflix's third-party tools to build Spring-based
microservices** — auto-configured, annotation-driven, far less boilerplate.

Over time, Spring stopped depending on Netflix and built/adopted its own
replacements:

| Old (Netflix) | New (Spring-native) |
|---|---|
| Eureka Server integration | still Eureka, but as a first-class Spring Cloud Netflix module |
| — | Spring Cloud Config Server & Config Client |
| Hystrix | Spring Cloud Circuit Breaker (backed by Resilience4j) |
| Zuul | Spring Cloud Gateway |
| Ribbon | Spring Cloud LoadBalancer |
| — | Feign Client (kept, now integrates with LoadBalancer instead of Ribbon) |

**Spring Boot** = develops the microservice (auto-configuration, embedded server).
**Spring Cloud** = provides the integration tools (Config Server, Eureka, etc.)
around it.

---

## 2. Deep Dive — What Your Notes Didn't Cover

### 2.1 The Spring Cloud BOM & release trains (this is the part your notes skipped
entirely, and it trips people up in real projects)

Spring Cloud doesn't have its own independent version numbers like Spring Boot
does. It ships as a **Bill of Materials (BOM)** using codenamed **release trains**
(originally London Underground station names — Angel, Brixton, Camden... then
switched to a calendar scheme). You must match the release train to your Spring
Boot version, or you'll hit dependency conflicts at startup.

```xml
<!-- pom.xml -->
<properties>
    <java.version>21</java.version>
    <spring-cloud.version>2023.0.3</spring-cloud.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Compatibility matrix (know this cold — asked directly in interviews):

| Spring Boot | Spring Cloud release train |
|---|---|
| 3.3.x | 2023.0.x ("Leyton") |
| 3.2.x | 2023.0.x |
| 3.1.x | 2022.0.x ("Kilburn") |
| 3.0.x | 2022.0.x |
| 2.7.x | 2021.0.x ("Jubilee") — last train supporting Java 8 |
| 2.6.x | 2021.0.x |

**Tricky interview trap:** "Just add `spring-cloud-starter-netflix-eureka-client`
to your Spring Boot 3.3 project" without importing the matching BOM →
`NoSuchMethodError`/`ClassNotFoundException` at boot time from version mismatches.
Always import the BOM.

### 2.2 Why Netflix OSS went into maintenance mode
Netflix themselves stopped actively developing Hystrix, Ribbon, and Zuul around
2018-2019 in favor of adaptive concurrency limiting internally, and the OSS
community/Spring stepped in with modern, actively maintained replacements:
- **Hystrix → Resilience4j**: lighter (no RxJava dependency), functional-style
  decorators, better Java 8+ idioms, pluggable metrics.
- **Zuul (1.x, blocking servlet-based) → Spring Cloud Gateway**: built on Project
  Reactor / Spring WebFlux, non-blocking, much higher throughput under load, and a
  much cleaner predicate/filter DSL. (Zuul 2 was async but Netflix never
  open-sourced it fully / Spring didn't adopt it.)
- **Ribbon → Spring Cloud LoadBalancer**: Ribbon was announced as feature-frozen
  by Netflix in 2016; Spring Cloud LoadBalancer is now the default, reactive,
  simpler API.
- **Eureka**: still maintained and still widely used (no equally simple drop-in
  replacement for non-Kubernetes environments), but many teams on Kubernetes just
  use K8s-native service discovery instead.

### 2.3 Alternatives to the Netflix stack you should know exist
- **Discovery**: Eureka, HashiCorp **Consul**, Apache **Zookeeper**, Kubernetes
  native Service/DNS, Alibaba **Nacos**.
- **Config**: Spring Cloud Config, Consul KV, Kubernetes ConfigMaps/Secrets,
  HashiCorp Vault (for secrets specifically).
- **Gateway**: Spring Cloud Gateway, Kong, NGINX, AWS API Gateway, Istio (service
  mesh ingress).
- **Circuit breaker**: Resilience4j, Istio/Envoy-level circuit breaking (service
  mesh, language-agnostic — moves resilience out of app code entirely).

**Interview angle:** *"Would you build this on Spring Cloud Netflix stack or a
service mesh (Istio/Envoy) today?"* — good senior answer: service meshes move
cross-cutting concerns (discovery, retries, circuit breaking, mTLS, tracing) out
of application code into the infrastructure/sidecar layer, which is
language-agnostic and centrally governable — attractive at large polyglot scale,
but adds operational complexity (sidecars, control plane). Spring Cloud keeps
these concerns in-process, which is simpler to reason about and debug for a
Java-only shop, and gives you compile-time safety (e.g. Feign interfaces).

### 2.4 Starter dependency reference table
| Concern | Dependency |
|---|---|
| Discovery client | `spring-cloud-starter-netflix-eureka-client` |
| Discovery server | `spring-cloud-starter-netflix-eureka-server` |
| Config client | `spring-cloud-starter-config` |
| Config server | `spring-cloud-config-server` |
| Feign | `spring-cloud-starter-openfeign` |
| Load balancer | `spring-cloud-starter-loadbalancer` |
| Circuit breaker | `spring-cloud-starter-circuitbreaker-resilience4j` |
| Gateway | `spring-cloud-starter-gateway` |
| Tracing | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` |
| Bus (config refresh broadcast) | `spring-cloud-starter-bus-amqp` (or `-kafka`) |

---

## 3. "Behind the Scenes"

At startup, `spring-cloud-dependencies` BOM pins the exact compatible versions of
every Spring Cloud module transitively — this is why you only declare
`<spring-cloud.version>` once and every `spring-cloud-starter-*` "just works"
together, the same way `spring-boot-starter-parent` pins Boot's own dependency
versions. If you ever see mismatched Spring Cloud artifact versions on the
classpath (e.g. from a shaded/uber jar or a transitive override), you'll get
cryptic `NoSuchMethodError`s at *runtime*, not compile time — because Maven/Gradle
happily resolve to *a* version, just not necessarily a compatible one.

---

## 4. Interview Q&A

**Q1: Why doesn't Spring Cloud have its own version number like 3.3.0?**
A: Because Spring Cloud is really an umbrella of ~20 independent sub-projects
(Config, Netflix, Gateway, OpenFeign, LoadBalancer, Sleuth/Tracing...), each with
its own release cadence. The **release train** (a BOM) is what guarantees a
mutually compatible set of versions across all of them, tied to a specific Spring
Boot generation.

**Q2 (tricky): Can you use Spring Cloud Gateway and Zuul in the same project?**
A: Technically you *can* add both dependencies, but you shouldn't — they solve the
same problem (edge routing) with incompatible runtime models: Zuul 1.x is
blocking/Servlet-based, Spring Cloud Gateway is non-blocking/WebFlux-based (needs
Netty, not Tomcat, as the embedded server). Mixing them is a red flag in a design
review — pick one edge layer.

**Q3: What does "maintenance mode" mean for Ribbon/Hystrix/Zuul, and is it safe
to use them in a new project in 2026?**
A: Maintenance mode means the projects still receive critical security patches
but no new features. For a **new** project, no — use Spring Cloud LoadBalancer,
Resilience4j, and Spring Cloud Gateway. Ribbon/Hystrix/Zuul knowledge is still
asked in interviews mainly because a huge amount of production code written
2015-2020 still runs on them, and you may be maintaining/migrating such systems.

**Q4: What's the actual difference between Spring Boot and Spring Cloud in one
sentence?**
A: Spring Boot standardizes *how you build a single, standalone, production-ready
application*; Spring Cloud standardizes *how many such applications discover,
configure, and talk to each other* as a distributed system.

---

## 5. Scenario Questions

**Scenario 1:** *"A teammate adds `spring-cloud-starter-netflix-eureka-client` to
a Spring Boot 3.3.2 project without touching the parent POM's dependency
management, and the app fails to start with a cryptic `NoSuchMethodError` deep in
a Spring Cloud class. Diagnose it."*

Model answer: Almost certainly a Spring Cloud BOM mismatch — either the BOM isn't
imported at all (so Maven resolved the Eureka starter's *own* transitive Spring
Cloud Commons version, which may not match what Boot 3.3.2 needs), or an old
`spring-cloud.version` (e.g. 2022.0.x, meant for Boot 3.1) is pinned against Boot
3.3.2. Fix: import `spring-cloud-dependencies` with the release train matching
the Boot version (2023.0.x for Boot 3.3.x) in `<dependencyManagement>`, then run
`mvn dependency:tree` to confirm no stray transitive version is being pulled in.

**Scenario 2:** *"Your company is migrating a 2017-era Spring Cloud Netflix
(Eureka + Ribbon + Zuul + Hystrix) platform to something modern, but can't do a
big-bang rewrite. Propose an incremental migration plan."*

Model answer: Because all four are drop-in-compatible replacements at the
architectural role level, migrate one concern at a time behind the same
interfaces: (1) swap Hystrix → Resilience4j first (lowest blast radius, purely
internal to each service, `spring-cloud-starter-circuitbreaker-resilience4j`);
(2) swap Ribbon → Spring Cloud LoadBalancer next (also mostly internal, Feign/
RestTemplate config changes only); (3) migrate Zuul → Spring Cloud Gateway last,
since it's the shared edge/entry point and touches every consumer — do it behind
a canary/blue-green rollout with the old Zuul instance still live until traffic
is validated on the new gateway; (4) leave Eureka in place unless/until the whole
platform also moves to Kubernetes, at which point discovery can migrate to
K8s-native Services in the same incremental fashion.

---

[← 01. Introduction](01-introduction-monolith-vs-microservices.md) | [README](README.md) | Next → [03. Config Server](03-config-server-and-config-client.md)
