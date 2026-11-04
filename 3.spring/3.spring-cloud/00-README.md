# Spring Cloud with Microservices — Complete Interview Master Notes

Goal: Crack any Spring Boot / Spring Cloud microservices interview — 5 to 10 YOE level and above.

This is your original notes, kept fully intact, **expanded** with the missing concepts,
real production-style code (Spring Boot 3.3.x + Spring Cloud 2023.0.x "Leyton" release train,
Java 21), internals ("behind the scenes"), and tricky interview questions with answers.

> Note on versions: Netflix Ribbon, Zuul and Hystrix are **in maintenance mode / EOL**.
> Every file below covers the classic Netflix stack (for interview history questions —
> interviewers with 8-10 YOE still ask about it) **and** the modern Spring-native
> replacement that you should actually use in new projects:
>
> | Old (Netflix, maintenance mode) | New (Spring-native, use this) |
> |---|---|
> | Eureka | Eureka (still actively used) / Consul / Kubernetes native discovery |
> | Ribbon | Spring Cloud LoadBalancer |
> | Zuul | Spring Cloud Gateway |
> | Hystrix | Resilience4j |
> | Sleuth | Micrometer Tracing (Sleuth is EOL since Boot 3) |

## Index

| # | File | Topic |
|---|---|---|
| 01 | [01-introduction-monolith-vs-microservices.md](01-introduction-monolith-vs-microservices.md) | Cloud characteristics, vertical vs horizontal scaling, virtualization, monolith vs microservices, splitting strategy |
| 02 | [02-spring-cloud-evolution-netflix-stack.md](02-spring-cloud-evolution-netflix-stack.md) | Spring Cloud BOM, release trains, Netflix OSS stack evolution, what replaced what and why |
| 03 | [03-config-server-and-config-client.md](03-config-server-and-config-client.md) | Spring Cloud Config Server/Client, externalized config, encryption, @RefreshScope, Spring Cloud Bus |
| 04 | [04-service-discovery-eureka.md](04-service-discovery-eureka.md) | Eureka Server/Client, self-preservation, heartbeats, DNS vs Eureka, AP vs CP discovery |
| 05 | [05-interservice-communication.md](05-interservice-communication.md) | RestTemplate vs WebClient, Ribbon vs Spring Cloud LoadBalancer, OpenFeign, client-side vs server-side LB |
| 06 | [06-resilience-circuit-breakers.md](06-resilience-circuit-breakers.md) | Circuit breaker pattern, Resilience4j (CircuitBreaker, Retry, TimeLimiter, RateLimiter, Bulkhead), Hystrix legacy |
| 07 | [07-spring-cloud-gateway.md](07-spring-cloud-gateway.md) | Spring Cloud Gateway routes/predicates/filters, Zuul legacy, aggregation, security at the edge |
| 08 | [08-distributed-tracing-logging.md](08-distributed-tracing-logging.md) | Sleuth → Micrometer Tracing, Zipkin, correlation IDs, centralized logging with the ELK stack |
| 09 | [09-security-oauth2-microservices.md](09-security-oauth2-microservices.md) | OAuth2/OIDC in microservices, Resource Server, token relay, API Gateway security |

## How to use this

Each file follows the same structure:
1. **Your Original Notes** — cleaned up, nothing removed
2. **Deep Dive / What Was Missing** — concepts your notes didn't cover
3. **Production Code Example** — real, runnable-shape code
4. **Behind the Scenes** — internals
5. **Interview Q&A** — standard + tricky
6. **Scenario Questions** — the kind asked at 5-10 YOE level

Work through 01 → 09 in order — each builds on the previous one (Config Server needs
Eureka, Gateway needs LoadBalancer/Feign, Resilience wraps Feign calls, etc.)
