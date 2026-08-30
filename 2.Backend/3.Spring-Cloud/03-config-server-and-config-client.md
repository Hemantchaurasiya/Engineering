# 03. Spring Cloud Config Server & Config Client

[← 02. Evolution](02-spring-cloud-evolution-netflix-stack.md) | [README](README.md) | Next → [04. Eureka](04-service-discovery-eureka.md)

---

## 1. Your Original Notes (cleaned up)

**Problem Statement:**
1. In microservices, we typically package configuration inside the application
   itself (`application.properties`/`.yml`), then deploy.
2. If configuration needs to change, you must modify it, **rebuild/repackage**,
   and **redeploy** the whole application.
3. In a containerized environment, this means rebuilding the image and pushing it
   to an artifact registry (Docker Hub, etc.) — just for a config change.

**Q: Can we inject updated configuration into a running application without
rebuilding/restarting?**
**A: Yes — externalize the configuration** (move it out of the deployable
artifact entirely).

```
Externalize the Configuration
↓
Service1.properties    → Combine all three
Service2.properties      server configurations
Service3.properties      in one separate server,
                         called the Config Server
```

Don't keep application config *inside* the project — move it to an external
location, typically a **Git repository**.

```
service-one ──→ git repo details      → <application-name>.properties
                  → url                   service-one.properties
                  → username              service-one.dev.properties
                  → password              service-one.prod.properties
                ↕
                input: application          service-two.properties
                name = service-one          service-two.dev.properties
                ↓
            Config Server               Git Repository
```

- **input:** application-name = `service-one`
- **output:** `service-one.properties`, `application.properties`

Config Server exposes a REST API that microservices call to retrieve their
configuration: `Microservice → Config Server → external configuration (git)`.

**@RefreshScope** — beans annotated with this are re-initialized when configuration
changes, without a full app restart.

`http://localhost:9090/actuator/refresh` — POST to this endpoint on the
**consumer** (service-one) to make it pull and apply the latest config from
Config Server.

### Encrypted values flow

```
microservice    Spring Cloud Config Server    git repo
    ──────────────→────────────────────────→  password = 123
    ←──────────────────────────────────────
```
- If Config Server reads plain text from git → it can encrypt-at-rest concerns
  don't apply, sends plain text to the microservice.
- If Config Server reads an **encrypted** value from git → it decrypts it (by
  default) before sending plain text to the microservice, **or** can be configured
  to pass the encrypted value through as-is and let the client decrypt it.

**Steps to use encryption:**
1. Configure an `encrypt.key=<value>` in Config Server's own
   `application.properties`/`bootstrap.properties`.
2. `POST http://localhost:9091/encrypt` with the plain text in the request body →
   returns the encrypted text.
3. Store the encrypted text in the git repo (never plain text secrets in git).
4. By default `spring.cloud.config.server.enabled=true` + the presence of a key
   means Config Server auto-decrypts on the way out to clients.

### Refresh without restart — full steps
1. Update the config value in the git repo.
2. Call `/actuator/refresh` on the microservice to pull and re-apply the new
   config.
3. `@RefreshScope` is what actually re-injects the new value into any bean using
   `@Value` for that property.

### Bootstrap vs Application config
Every microservice, on startup, has to talk to Config Server and load its config
into a **parent** IoC container before the main application context even starts —
this is boilerplate Spring Cloud automates via:
1. **spring-cloud-bootstrap** — loads `bootstrap.properties`/`.yml` and injects
   values into the `Environment` *before* the rest of the app starts.
2. **Spring Cloud Config Client** — actually talks to Config Server.

Rule of thumb for where a property belongs:
1. Needed **during bootstrap** (e.g. Config Server URL itself, app name) →
   `bootstrap.properties`.
2. **Application-specific and never changes at runtime** → `application.properties`.
3. **Application-specific but changes over time** → external Config Server.

---

## 2. Deep Dive — What Your Notes Didn't Cover

### 2.1 `bootstrap.properties` is deprecated in modern Spring Cloud!
This is the single biggest gap and a very likely interview trap. As of Spring
Cloud 2020.0 ("Ilford"), the bootstrap context is **disabled by default**. You
must either:
- Add `spring-cloud-starter-bootstrap` explicitly to re-enable the old
  `bootstrap.yml` mechanism, **or** (recommended, modern approach)
- Use `spring.config.import=configserver:http://localhost:8888` directly inside
  `application.yml` — no separate bootstrap phase needed at all.

```yaml
# application.yml (modern approach, Spring Boot 2.4+/Spring Cloud 2020.0+)
spring:
  application:
    name: order-service
  config:
    import: "configserver:http://localhost:8888"
  cloud:
    config:
      fail-fast: true        # crash on boot if Config Server is unreachable
      retry:
        max-attempts: 6
        initial-interval: 1000
```

**Interview trap:** "Why is my `bootstrap.yml` being ignored in Spring Boot 3?" —
because bootstrap context needs the explicit starter now; the recommended fix is
to stop using bootstrap.yml altogether and switch to `spring.config.import`.

### 2.2 Full working Config Server

```java
// ConfigServerApplication.java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

```yaml
# config-server application.yml
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/config-repo
          default-label: main
          clone-on-start: true
          search-paths: '{application}'
          # for private repos:
          username: ${GIT_USERNAME}
          password: ${GIT_TOKEN}
      # encryption key — in production, use an env var or better, a vault/KMS
  encrypt:
    key: ${CONFIG_ENCRYPT_KEY}

management:
  endpoints:
    web:
      exposure:
        include: refresh, health, info, encrypt, decrypt
```

Config repo layout (`config-repo` git repository):
```
config-repo/
├── application.yml               # shared by ALL services
├── order-service.yml             # order-service, all profiles
├── order-service-dev.yml         # order-service, dev profile only
├── order-service-prod.yml        # order-service, prod profile only
└── product-service.yml
```

Resolution URL pattern exposed by Config Server:
```
GET /{application}/{profile}[/{label}]
GET /order-service/dev            → merges application.yml + order-service.yml + order-service-dev.yml
GET /order-service/prod/main      → git branch "main"
```

### 2.3 Config Client (consumer) — modern setup

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

```yaml
# order-service application.yml
spring:
  application:
    name: order-service
  profiles:
    active: dev
  config:
    import: "optional:configserver:http://localhost:8888"
    # "optional:" prefix → don't fail startup if Config Server is down
  cloud:
    config:
      fail-fast: true
```

```java
@RestController
@RefreshScope   // <-- re-created when /actuator/refresh is called
public class FeatureFlagController {

    @Value("${feature.new-checkout-enabled:false}")
    private boolean newCheckoutEnabled;

    @GetMapping("/feature-flags")
    public Map<String, Boolean> flags() {
        return Map.of("newCheckoutEnabled", newCheckoutEnabled);
    }
}
```

```bash
# trigger refresh on this one instance
curl -X POST http://localhost:8081/actuator/refresh
```

### 2.4 Encryption — symmetric vs asymmetric
Your notes cover symmetric encryption (`encrypt.key`). In production, asymmetric
(RSA keypair) is preferred — the public key can encrypt, only Config Server (with
the private key, often in a keystore/HSM/Vault) can decrypt:

```yaml
encrypt:
  key-store:
    location: classpath:/config-server.jks
    password: ${KEYSTORE_PASSWORD}
    alias: config-server-key
    secret: ${KEY_PASSWORD}
```

Values in git are prefixed `{cipher}` so Config Server knows to decrypt them:
```yaml
datasource:
  password: '{cipher}AQBx7z...base64EncryptedBlob...'
```

### 2.5 Spring Cloud Bus — refreshing ALL instances, not just one
Your notes only cover hitting `/actuator/refresh` on a **single** instance. In
production with N instances of `order-service` behind a load balancer, you don't
want to curl each one manually. **Spring Cloud Bus** (backed by RabbitMQ or Kafka)
broadcasts a refresh event to every instance at once:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bus-amqp</artifactId>
</dependency>
```

```bash
# single call refreshes every instance of every service connected to the bus
curl -X POST http://localhost:8081/actuator/busrefresh
```

Even better: wire a Git webhook → Config Server's `/monitor` endpoint
(`spring-cloud-config-monitor`) so a `git push` automatically triggers
`busrefresh` across the fleet — **zero manual steps** for a config change to
propagate.

### 2.6 What `@RefreshScope` can't fix
`@RefreshScope` only re-injects `@Value`/`@ConfigurationProperties` on **beans**.
It cannot change things baked in at framework-bootstrap time — e.g. the server
port, DataSource connection pool settings picked at construction (depending on
pool implementation), logging levels configured outside Spring's logging
abstraction, or anything read in a `static` initializer. Also, `@RefreshScope`
beans are **proxied** and lazily re-created on next access — so there's a subtle
gotcha: the *first* call after a refresh pays the cost of re-instantiating the
bean.

---

## 3. "Behind the Scenes"

`@RefreshScope` works by wrapping the target bean in a **scoped proxy** (same
proxy machinery used for `request`/`session` scope — see your Spring Boot
mastery notes file 03). On `/actuator/refresh`, Spring Cloud Context calls
`RefreshScope.refreshAll()`, which evicts every `@RefreshScope` bean from its
internal cache. The **next** method call on the proxy detects the cache miss,
re-runs the bean's `@Bean`/`@Component` creation logic (re-reading the now-updated
`Environment`), and caches the new instance. This is why `@RefreshScope` beans
must be lightweight to re-create — anything with expensive initialization (e.g.
opening a DB connection pool in the constructor) is a poor fit for
`@RefreshScope`.

---

## 4. Interview Q&A

**Q1: Why must every microservice own its own database, and how does that
interact with Config Server?**
A: Not directly related to ownership of the DB itself, but Config Server is
exactly how each service gets *its own* DB connection details externally (and
securely, via encryption) without hardcoding credentials into the JAR — keeping
service autonomy (file 01) intact even for infrastructure config.

**Q2 (tricky): If Config Server is down, does your entire microservices platform
go down?**
A: It depends on configuration. With `spring.config.import: "configserver:..."`
(no `optional:` prefix) and `fail-fast: true`, **yes** — a service that can't
reach Config Server on startup will refuse to start. This is a legitimate design
choice for services that truly need fresh config to run correctly, but for
**already-running** instances, Config Server being briefly unavailable is *not*
fatal — they keep running with the config they already loaded; it just blocks
new deployments/refreshes/scale-out (new instances can't boot) until Config
Server recovers. Mitigations: `optional:` prefix, `retry` config, and running
Config Server itself highly-available (multiple instances behind a load
balancer, or backed by a highly available git host).

**Q3: What's the difference between `application.properties` (local) and
`application.yml` on Config Server named `application.yml` (shared)?**
A: `application.yml` in the **config repo** (served by Config Server) is special —
it is the **default/shared configuration applied to every service** regardless of
`spring.application.name`, analogous to a "global" properties file. Per-service
files (`order-service.yml`) override it for that specific service.

**Q4 (tricky): Can two different microservices read the same property key from
Config Server and get different resolved values?**
A: Yes — that's the entire point of profile + application-name scoping. E.g.
`server.port` in the shared `application.yml` might be `8080`, but
`order-service-dev.yml` overrides it to `8081` — `order-service` running with
`spring.profiles.active=dev` gets `8081`, while `product-service` (no override)
gets `8080`.

**Q5: What happens to in-flight requests when `/actuator/refresh` re-creates a
`@RefreshScope` bean?**
A: In-flight requests already holding a reference to the **old** bean instance
(via the proxy, before eviction) complete against the old value; only *new*
method invocations on the proxy after the refresh see the new value. There's no
hard cutover moment — it's eventually consistent per-request, not per-instant.

---

## 5. Scenario Questions

**Scenario 1:** *"A junior engineer stores a plaintext DB password in
`order-service.yml` in the config-repo, which is a private GitHub repo. Is this
acceptable? What would you change?"*

Model answer: No — "private repo" is not sufficient protection; anyone with repo
access (including CI/CD systems, contractors, or a future leaked credential) gets
the plaintext DB password, and git history retains it forever even if later
removed. Use Config Server's `{cipher}` encryption (symmetric via `encrypt.key`,
or better, asymmetric with a keystore) so only Config Server, not the git repo
itself, can produce the plaintext — or migrate secrets specifically to a
dedicated secrets manager (Vault, AWS Secrets Manager) and keep only non-secret
config in Config Server.

**Scenario 2:** *"You deploy a config change (a feature flag flip) to
`order-service`, which runs 20 instances behind a load balancer. Five minutes
later only 3 instances show the new behavior. Diagnose it."*

Model answer: Someone likely called `/actuator/refresh` on individual instances
(or only on the instance the load balancer happened to route their curl to)
instead of using **Spring Cloud Bus** `/actuator/busrefresh`, which broadcasts to
every instance connected to the message bus (RabbitMQ/Kafka) at once. Fix: wire
up Spring Cloud Bus, and ideally automate refresh entirely via a Config Server
git webhook → `/monitor` endpoint, removing the manual step altogether.

---

[← 02. Evolution](02-spring-cloud-evolution-netflix-stack.md) | [README](README.md) | Next → [04. Eureka](04-service-discovery-eureka.md)
