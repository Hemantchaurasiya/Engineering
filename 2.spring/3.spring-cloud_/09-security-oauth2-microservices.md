# 09. Security — OAuth2/OIDC in a Microservices Architecture

[← 08. Distributed Tracing & Logging](08-distributed-tracing-logging.md) | [README](README.md) | Back to [README](README.md)

---

## 1. Your Original Notes (cleaned up)

- **OAUTH** — that's the entirety of the original TOC entry for this section.
  Everything below fills in what "apply security at the gateway level" (file
  07, point 1) actually requires, end-to-end.

---

## 2. Deep Dive — Building This Out From Scratch

### 2.1 Why security is *harder*, not easier, in microservices
In a monolith, authentication happens once (e.g. a session cookie), and every
feature (Catalog, Cart, Orders) trusts the same in-process session — no network
hop, no re-verification needed. In microservices, a single user action can
trigger calls across many services (Gateway → order-service → product-service →
payment-service), each potentially on a different host — **every hop is a
network call that could be spoofed if you don't verify identity at each
boundary.** This is exactly why file 07 flagged the "Gateway does auth, so
downstream services don't need to" assumption as a trap.

### 2.2 OAuth2 vocabulary (assumed knowledge in every interview on this topic)
- **Resource Owner** — the end user.
- **Client** — the application requesting access on the user's behalf (e.g. your
  frontend/mobile app).
- **Authorization Server** — issues tokens after authenticating the user (e.g.
  Keycloak, Okta, Auth0, AWS Cognito, or a self-hosted Spring Authorization
  Server).
- **Resource Server** — the API that holds protected data and validates the
  token before serving a request (in our world: `order-service`,
  `product-service`, etc.).
- **Access Token** — a short-lived credential (usually a JWT) proving the
  client is authorized to call resource servers on the user's behalf.
- **OIDC (OpenID Connect)** — a thin identity layer *on top of* OAuth2 that adds
  an **ID Token** (proves *who the user is*, not just *what they're allowed to
  do*) — OAuth2 alone is about authorization, not authentication; OIDC adds
  authentication.

### 2.3 Where tokens get validated — the two-tier pattern
```
Client → [ Gateway: validates JWT signature + expiry, coarse-grained routing ]
              → order-service: [ validates JWT AGAIN + fine-grained authorization ]
                  → product-service: [ validates JWT AGAIN + fine-grained authorization ]
```

**Gateway-level validation (coarse):** reject obviously invalid/expired/
unsigned tokens before they even reach internal services — cheap, fast,
protects internal services from unnecessary load from garbage requests.

**Service-level validation (fine-grained) — this is the part your notes'
single-word "OAUTH" entry skipped entirely, and it's the actual interview
focus:** each Resource Server independently validates the token *again*
(defense-in-depth — never trust "the Gateway already checked it" as your only
line of defense) **and** applies its own authorization logic — e.g. does *this*
token's subject actually own *this specific* order? That's business-level
authorization no generic gateway rule can express.

### 2.4 Resource Server setup (each microservice validates JWTs independently)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```yaml
# order-service application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com/realms/ecommerce
          # Spring auto-discovers the JWK Set URI from the issuer's
          # /.well-known/openid-configuration endpoint — no need to hardcode
          # the public key; keys can even rotate without a redeploy.
```

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(CsrfConfigurer::disable)   // stateless APIs, not browser form-based — CSRF not applicable
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/orders/**").hasAuthority("SCOPE_orders.read")
                .requestMatchers(HttpMethod.POST, "/api/orders/**").hasAuthority("SCOPE_orders.write")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

```java
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/api/orders/{id}")
    @PreAuthorize("hasAuthority('SCOPE_orders.read')")
    public OrderDto getOrder(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();   // "sub" claim — who is calling
        OrderDto order = orderService.getOrder(id);

        // fine-grained, business-level authorization — the part the
        // Gateway/generic JWT validation CANNOT do for you:
        if (!order.customerId().equals(userId)) {
            throw new AccessDeniedException("Not your order");
        }
        return order;
    }
}
```

### 2.5 Token Relay — propagating the user's identity across service calls
When `order-service` calls `product-service` on behalf of the original user, it
needs to **forward** (relay) the same access token — otherwise `product-
service` has no idea who the original caller was and can't apply its own
authorization checks.

```java
@Bean
@LoadBalanced
public RestTemplate restTemplate(OAuth2AuthorizedClientManager manager) {
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.getInterceptors().add((request, body, execution) -> {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        request.getHeaders().setBearerAuth(jwt.getTokenValue());  // relay the SAME token
        return execution.execute(request, body);
    });
    return restTemplate;
}
```

With **Feign**, this is even simpler via a `RequestInterceptor`:

```java
@Component
public class TokenRelayFeignInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof Jwt jwt) {
            template.header("Authorization", "Bearer " + jwt.getTokenValue());
        }
    }
}
```

At the **Gateway**, Spring Cloud Gateway has a built-in `TokenRelay=` filter
that does exactly this automatically for OAuth2-login flows:

```yaml
- id: order-service-route
  uri: lb://order-service
  predicates:
    - Path=/api/orders/**
  filters:
    - TokenRelay=
```

### 2.6 Service-to-service auth WITHOUT a user in the loop (Client Credentials
grant) — a scenario your single-word "OAUTH" note gives zero hint about
Not every inter-service call happens *on behalf of* a user — e.g. a scheduled
batch job in `order-service` that periodically syncs data from `product-
service` has no logged-in user/token to relay. For this, OAuth2's
**Client Credentials grant** issues a token representing the **service itself**
(not a user):

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          order-service-client:
            provider: keycloak
            client-id: order-service
            client-secret: ${ORDER_SERVICE_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: internal.sync
        provider:
          keycloak:
            token-uri: https://auth.example.com/realms/ecommerce/protocol/openid-connect/token
```

```java
@FeignClient(name = "product-service")
public interface ProductSyncClient {
    @GetMapping("/internal/api/products/updated-since")
    List<ProductDto> getUpdatedProducts(@RequestParam Instant since);
}

// registered as an OAuth2-authorized Feign client that automatically
// fetches/caches/refreshes a client-credentials token before each call
```

### 2.7 Symmetric vs Asymmetric JWT signing — a subtle but real interview trap
- **Symmetric (HMAC, e.g. HS256)** — the *same secret* signs and verifies the
  token. Every Resource Server that needs to validate tokens must hold that
  same shared secret — a real problem at scale: leaking the secret to *one*
  service (or one compromised service) compromises the ability to forge tokens
  for **every** service.
- **Asymmetric (RSA/EC, e.g. RS256)** — the Authorization Server signs with a
  **private** key it alone holds; every Resource Server only needs the
  corresponding **public** key to verify (which can be freely distributed, even
  published at a public JWK endpoint, as in 2.4's `issuer-uri` auto-discovery).
  This is why production OAuth2 deployments almost universally use RS256 (or
  similar) — no shared secret to leak, and key rotation is transparent to
  Resource Servers via the JWK Set endpoint.

---

## 3. "Behind the Scenes"

**How `issuer-uri` auto-configuration actually validates a token, step by
step:** On startup, Spring Security's Resource Server auto-configuration fetches
`{issuer-uri}/.well-known/openid-configuration` — a well-known OIDC discovery
document — which tells it, among other things, the `jwks_uri` (where public
signing keys live). It then fetches and caches that JWK Set. On each incoming
request with a `Bearer` token, Spring: (1) parses the JWT, (2) looks up the
matching public key by the token's `kid` (key ID) header, (3) cryptographically
verifies the signature against that public key, (4) checks standard claims
(`exp` not expired, `iss` matches the configured issuer, `aud` if configured).
Only after all of that passes does Spring populate the `SecurityContext` with an
authenticated `Jwt` principal for your `@PreAuthorize`/controller code to use.
None of this requires a network call to the Authorization Server *per request*
— only the initial JWK fetch (and periodic refresh on key rotation) hits the
network; token validation itself is entirely local/cryptographic, which is
exactly why JWT-based auth scales far better across many microservices than a
scheme requiring a live call-back to the Authorization Server on every request
(e.g. classic opaque-token introspection, which *does* require that per-request
network round trip — a real trade-off worth knowing: JWT trades "instant
revocation" for "no per-request network hop").

---

## 4. Interview Q&A

**Q1: Why shouldn't downstream microservices simply trust that the Gateway
already authenticated the request?**
A: Defense-in-depth — if the Gateway is ever bypassed (misconfigured network
policy, an internal service reachable directly, a compromised component inside
the perimeter), a service with no independent validation would accept
**anything**. Also, the Gateway typically can't express fine-grained,
business-level authorization ("does this user own this specific order?") — only
the owning service has the data to make that call. Both correctness and
security require each service to independently validate.

**Q2 (tricky): What's the real trade-off between JWT (self-contained token) and
opaque tokens with introspection, specifically for a high-throughput
microservices platform?**
A: JWTs are validated **locally/cryptographically** by each Resource Server (no
network call per request — see "Behind the Scenes" above), which scales far
better under high request volume across many services. The cost: a JWT, once
issued, **can't be instantly revoked** — it remains valid until it naturally
expires, even if e.g. the user's account is disabled 2 seconds after issuance.
Opaque tokens require the Resource Server to call the Authorization Server's
introspection endpoint on every request (extra network hop + latency + load on
the Auth Server, but **instant revocation** is possible since the Auth Server
checks live state every time). Common real-world compromise: JWTs with **short
expiry** (minutes) + a refresh-token flow, so the "stale JWT" window is bounded
tightly rather than eliminated.

**Q3: In the Client Credentials grant example (2.6), is there a "user" involved
at all? What does `sub` (subject) mean for such a token?**
A: No end user — the token represents the **service itself** as the identity.
The `sub` claim (and other identity claims) typically identifies the *client*
(e.g. `order-service`), not a person. This is exactly why per-service
`@PreAuthorize` logic that assumes "there's always a real user to check
ownership against" needs a separate code path (or explicit scope-based checks
only) for machine-to-machine calls — trying to look up "which user does
`sub=order-service` belong to" would be a bug.

**Q4 (tricky): You rotate your Authorization Server's signing key (RS256).
Do you need to redeploy every microservice for token validation to keep
working?**
A: No — that's precisely the benefit of `issuer-uri`-based auto-configuration
(2.3/"Behind the Scenes"): Resource Servers fetch signing keys from the JWK Set
endpoint (not a hardcoded key baked into config/code), and Spring Security
periodically refreshes/re-fetches that JWK Set, so a key rotation on the
Authorization Server side is picked up transparently. This *would* be a real
problem (requiring coordinated redeploys) if you'd hardcoded a symmetric secret
or a static public key instead of using discovery-based JWK resolution — another
reason RS256 + `issuer-uri` auto-config is the production-recommended setup.

**Q5: Why disable CSRF protection (`.csrf(CsrfConfigurer::disable)`) in a
Resource Server security config — isn't disabling security protections
dangerous?**
A: CSRF protection defends against a browser being tricked into making an
**authenticated, cookie-based, state-changing request** to your app without the
user's intent — it's a browser-session/cookie-auth-specific attack. A stateless
REST API authenticated via a `Bearer` token in an `Authorization` header (never
stored in a cookie, never automatically attached by the browser to
cross-origin requests) isn't vulnerable to CSRF in the first place — the attack
vector simply doesn't apply. Leaving CSRF protection *enabled* on a
token-based, stateless API would actually break legitimate API clients for no
security benefit.

---

## 5. Scenario Questions

**Scenario 1:** *"Security review flags that `product-service`'s internal
`/internal/api/products/updated-since` endpoint (called only by
`order-service`'s batch sync job) is reachable directly from the public
internet with no authentication at all, because it was assumed 'the Gateway
handles that.' How did this happen, and how do you fix it correctly?"*

Model answer: This is the exact defense-in-depth failure from Q1 — an internal,
service-to-service-only endpoint was left unauthenticated because of an
implicit assumption that all traffic flows through the Gateway, which turned
out to be false (the endpoint was directly network-reachable). Fix: (1)
immediately require the Client Credentials-issued token (2.6) on that endpoint,
validated independently by `product-service` itself — never rely solely on
network topology/perimeter assumptions for security; (2) additionally, as
defense-in-depth at the infrastructure layer, restrict network policy so
`/internal/**` paths genuinely can't be reached from outside the cluster/VPC at
all — belt and suspenders, not either/or.

**Scenario 2:** *"A user reports that after their account was suspended for
fraud, they were still able to place orders for another 40 minutes. Explain why,
using what you know about JWT vs opaque tokens, and describe two different fixes
with different trade-offs."*

Model answer: Classic JWT non-revocability (Q2) — their previously-issued JWT
remained cryptographically valid (unexpired) even after the account state
changed server-side, because Resource Servers validate JWTs locally without
re-checking live account status against the Authorization Server on every
request. Fix option A (lower blast radius change): shorten access-token expiry
significantly (e.g. from 60 minutes to 5 minutes) so the "stale-but-valid"
window shrinks a lot, combined with a refresh-token flow so legitimate users
aren't forced to re-login constantly — but a determined bad actor still has up
to 5 minutes. Fix option B (bigger architectural change, needed for truly
instant revocation): switch to opaque tokens + introspection (or add a
short-TTL denylist/"suspended account" cache check at the Resource Server or
Gateway layer, checked on every request) — trading the "no per-request network
hop" scalability benefit of JWTs for real-time revocation capability; a common
compromise is a lightweight, fast (e.g. Redis-backed) "is this user currently
suspended" check layered on top of otherwise-JWT-based auth, giving near-instant
revocation without full introspection overhead.

---

## Full Interview Master List — Quick Reference (all 9 files)

Use this as a rapid-fire self-test before an interview. If you can explain
*why*, not just *what*, for each, you're ready:

1. Why microservices over a monolith, and when is a monolith still the right
   call? (File 01)
2. Vertical vs horizontal scaling — and why databases often still scale
   vertically. (File 01)
3. Why is Eureka AP, not CP — and what does that trade-off actually protect
   against? (Files 01, 04)
4. What does the Spring Cloud release train/BOM actually solve? (File 02)
5. Why is Ribbon/Hystrix/Zuul in maintenance mode, and what replaced each?
   (File 02)
6. `bootstrap.yml` vs `spring.config.import: configserver:` — which is current?
   (File 03)
7. What happens to a running instance (not a new one) if Config Server goes
   down? (File 03)
8. Eureka self-preservation mode — what triggers it and why is it usually left
   on in prod? (File 04)
9. Why does an Eureka client survive a brief Eureka Server outage? (File 04)
10. RestTemplate vs WebClient vs RestClient vs Feign — current recommendation?
    (File 05)
11. How does `@LoadBalanced`/`lb://` actually resolve a "service name" URL?
    (Files 05, 07)
12. Circuit breaker state machine — CLOSED/OPEN/HALF_OPEN, and why it's not
    "just a retry." (File 06)
13. Retry pitfalls — non-idempotent operations, business errors vs infra
    errors. (File 06)
14. Bulkhead vs Circuit Breaker — what different failure mode does each guard
    against? (File 06)
15. Why is Spring Cloud Gateway non-blocking, and why can't you mix it with
    `spring-boot-starter-web`? (File 07)
16. Trace vs Span, and how does trace context actually propagate across an
    HTTP call? (File 08)
17. Sleuth vs Micrometer Tracing — which is current for Spring Boot 3? (File 08)
18. The three pillars of observability, and which tool answers which question?
    (File 08)
19. Why must every microservice validate a JWT independently, even behind a
    Gateway? (File 09)
20. JWT vs opaque token — the scalability vs instant-revocation trade-off.
    (File 09)

---

[← 08. Distributed Tracing & Logging](08-distributed-tracing-logging.md) | [README](README.md)
