# 09 - Master Tricky Q&A Bank (Cross-Cutting, 5-10 YOE Level)

Every earlier file has its own section-specific tricky questions tied to that topic. This file is the **cross-cutting bank** — questions that combine multiple concepts, or that senior-level interviewers specifically ask to separate "used Boot" from "understands Boot deeply."

---

## Architecture & Design Judgment

**Q1. You're designing a new microservice. Walk through every reason you'd pick Spring Boot over plain Spring for it, and one legitimate reason you might NOT.**
For: fast bootstrap via starters, embedded server for container-friendly single-artifact deployment, Actuator for instant production observability, convention over configuration reduces onboarding time for new team members. Against: Boot's opinionated auto-configuration can be a liability in a highly constrained environment (e.g. a shared app server mandate, or a legacy system requiring extremely specific bean wiring that fights auto-config's defaults) — in those rare cases plain Spring's explicit control can actually be less friction than fighting Boot's magic.

**Q2. What's the actual difference between "convention over configuration" and "auto-configuration," or are they the same thing?**
"Convention over configuration" is the broader design philosophy (e.g. name your file `application.properties` and it's picked up automatically, no need to declare it). "Auto-configuration" is the specific mechanism (`@ConditionalOnXxx`-guarded `@Configuration` classes) Boot uses to implement that philosophy for bean creation. Convention over configuration is the *goal*; auto-configuration is *one tool* Boot uses to achieve it (starter POMs and the properties-file convention are two other tools toward the same goal).

**Q3. Two microservices, both Spring Boot, need to share some common auto-configuration (a custom logging filter, a common exception handler) — how do you avoid copy-pasting it into every service?**
Build a shared internal library module containing your custom `@Configuration` class(es), guarded with your own `@ConditionalOnXxx` as appropriate, and register it the same way Boot's own auto-config classes register themselves: a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file inside that shared jar. Any service that depends on the shared jar picks up the shared behavior automatically, following exactly the same mechanism Boot itself uses internally — this is literally how organizations build internal "platform starters."

---

## Startup, Failure & Debugging

**Q4. A Boot app takes 45 seconds to start locally but 4 minutes in a specific cloud environment — how do you find the bottleneck without guessing?**
Enable `spring.jmx.enabled` diagnostics or, more directly, use Boot's built-in `ApplicationStartup` tracking (`FlightRecorderApplicationStartup` or `BufferingApplicationStartup`) to record step-by-step timing of the bean-creation/refresh lifecycle, then inspect where time is actually spent — commonly it's a bean whose constructor/`@PostConstruct` does a blocking network call (DB, config server, service discovery registration) that's slow specifically in that cloud environment (DNS resolution delay, network latency to a config server, etc.), not the app's own logic.

**Q5. Your app fails to start with `BeanCurrentlyInCreationException` — what causes this and how do you fix it?**
This is Spring detecting a **circular dependency**: Bean A's constructor needs Bean B, and Bean B's constructor needs Bean A, and neither can be fully constructed first. With constructor injection this fails fast and loudly at startup (a good thing — see file 01 Q4). Fixes: redesign to break the cycle (usually one of the two beans shouldn't actually depend on the other — extract shared logic into a third bean), or as a last resort use setter/field injection with `@Lazy` on one side to defer resolution, though that's treating the symptom, not the actual design smell.

**Q6. What's the difference between `@Component` and `@Bean`, and when would using the wrong one bite you?**
`@Component` (class-level, discovered via `@ComponentScan`) is for classes **you own** and can annotate directly. `@Bean` (method-level, inside a `@Configuration` class) is for objects you **don't own the source of** (third-party classes) or that need custom construction logic that annotating the class itself can't express (e.g. picking between multiple implementations based on a condition, or building an object via a builder pattern). Using `@Component` when you actually needed conditional/parameterized construction logic (`@Bean` inside `@Configuration`) leads to inflexible, hard-to-override bean creation.

---

## Concurrency, Scope & Statefulness (common senior-level trap questions)

**Q7. Are Spring `@Service`/`@Component` beans thread-safe by default? What does that actually depend on?**
By default beans are **singleton-scoped** — one instance shared across all requests/threads. Spring does nothing special to make that instance thread-safe; it's entirely up to you. As long as the bean is **stateless** (no mutable instance fields written to at request time — only injected, effectively-final dependencies), it's safe by construction, because there's nothing to race on. The moment you add a mutable instance field that gets written during request handling, you've introduced a shared-mutable-state bug across concurrent requests — a classic real production incident, usually from someone adding a "temporary" instance field for convenience.

**Q8. When would you actually need `@Scope("prototype")` or `@Scope("request")`, and what's the catch with injecting a prototype bean into a singleton?**
`request` scope: a bean that legitimately needs to hold per-HTTP-request mutable state (rare, usually replaced by passing state through method parameters instead). `prototype` scope: you need a fresh instance every time the bean is requested from the container. The catch: if you directly `@Autowired` a prototype bean into a singleton, Spring resolves it **once** at singleton-creation time and you get the same prototype instance forever after — defeating the purpose. Fix: inject an `ObjectFactory<T>`/`ObjectProvider<T>` or ApplicationContext-lookup proxy instead, so a *new* instance is fetched on each actual use.

**Q9. Real production bug: an `@Autowired` `SimpleDateFormat` field (or similar mutable helper) as a singleton field is causing corrupted/wrong dates under load — explain why, and the fix.**
`SimpleDateFormat` is famously not thread-safe internally (it mutates internal `Calendar` state during parse/format calls). As a singleton-scoped bean field, concurrent requests call `format()`/`parse()` on the *same* instance simultaneously, corrupting each other's in-flight state. Fix: use `DateTimeFormatter` (java.time, genuinely immutable and thread-safe) instead, or if you must keep a stateful helper, make it method-local (a new instance per call) rather than a shared field.

---

## Configuration & Environment (deeper than file 05)

**Q10. `@Value("${some.property}")` works fine in a `@Component`, but the exact same annotation on a field in a `@Bean`-method-created object silently does nothing — why?**
`@Value` resolution (like `@Autowired`) is applied by Spring's `AutowiredAnnotationBeanPostProcessor` to beans that Spring itself constructs and manages via its container lifecycle. An object manually `new`'d inside a `@Bean` method body (rather than being itself registered as a bean/return value processed by the container) doesn't go through that post-processing unless it *is* the object returned from the `@Bean` method (which then does get post-processed) — the confusion usually comes from `@Value`-annotating a field on some *nested*, non-bean helper object instantiated inside business logic, which was never a Spring-managed bean at all.

**Q11. How would you validate that all required configuration properties are present and correctly typed *before* the app is allowed to accept traffic, rather than discovering a missing property mid-request in production?**
Bind configuration through `@ConfigurationProperties` classes annotated with `@Validated` and JSR-303 constraints (`@NotBlank`, `@Min`, `@Pattern`, etc.) — binding failures then throw at startup (`ApplicationContextException` wrapping a `BindValidationException`), which fails the deployment/health check immediately in CI/CD rather than surfacing as a runtime NPE hours later in production.

---

## Testing (a very common gap in Boot self-study — often skipped, always asked)

**Q12. `@SpringBootTest` vs `@WebMvcTest` vs `@DataJpaTest` — when do you use which, and why does it matter for CI speed?**
- `@SpringBootTest` — boots the **entire** application context (all beans), closest to a real integration test, slowest.
- `@WebMvcTest(ProductController.class)` — boots only the web layer (that controller + MVC infrastructure), mocks out service-layer beans via `@MockBean` — fast, focused on controller/request-mapping/validation behavior.
- `@DataJpaTest` — boots only JPA-related beans (repositories, an embedded/test DB), for testing repository query logic in isolation.
Using slice tests (`@WebMvcTest`/`@DataJpaTest`) instead of `@SpringBootTest` everywhere dramatically speeds up a CI pipeline as the codebase grows — a `@SpringBootTest`-only test suite with hundreds of tests becomes a real, felt pain point in larger real-world codebases.

**Q13. How do you test a `@RestControllerAdvice` global exception handler without booting the whole application?**
`@WebMvcTest` for the controller under test automatically picks up `@RestControllerAdvice` classes as part of the web layer slice (they're MVC infrastructure, not business-layer beans), so you can assert the mapped HTTP status/body directly via `MockMvc`, without needing a full `@SpringBootTest`.

---

## Version & Migration Awareness (senior-level "have you kept up" questions)

**Q14. What changed about Spring Boot's minimum Java version requirement across major versions, and why does it matter for a hiring decision?**
Boot 2.x supports Java 8+; Boot 3.x requires a minimum of **Java 17** and moved the whole ecosystem from `javax.*` to `jakarta.*` namespaces (Jakarta EE, not Java EE, following the Eclipse Foundation transfer). This is a real, often painful migration point in industry — a straight major-version bump of Boot 2→3 in an existing codebase is never "just a version number change," because every `javax.persistence.*`/`javax.validation.*` import has to become `jakarta.persistence.*`/`jakarta.validation.*`, and every third-party library dependency has to also support the Jakarta namespace.

**Q15. What's Spring's "GraalVM native image" support in Boot 3.x, and what real trade-off does it involve?**
Boot 3.x has first-class support for compiling to a GraalVM native image — an ahead-of-time compiled native executable with dramatically faster startup (milliseconds instead of seconds) and lower memory footprint, valuable for serverless/scale-to-zero workloads. Trade-off: reflection-heavy code (common in Spring itself, and in libraries relying on runtime proxies/dynamic class generation) needs explicit reachability metadata hints, build times are significantly longer, and not every library in the ecosystem is fully native-image-compatible yet — so it's not a drop-in free upgrade for an arbitrary existing app.
