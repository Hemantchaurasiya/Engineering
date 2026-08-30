# 03 - `SpringApplication.run()` Internals

Interviewers at 5-10 YOE love this question because it separates people who used Boot from people who understand Boot. Your notes had the right skeleton — filled in below with the accurate, current sequence.

---

## Q: What happens when `SpringApplication.run()` executes?

**Your notes (corrected & completed):**

1. The `SpringApplication` constructor runs first (before `.run()` is even called) — it inspects the classpath via `deduceFromClasspath()` to figure out which `ApplicationContext` implementation will be needed later (see the WebApplicationType table below). It also detects and registers `ApplicationContextInitializer`s and `ApplicationListener`s found via `spring.factories` at this point.
2. Creates an empty `ConfigurableEnvironment` object.
3. Loads external configuration into that environment — `application.properties` / `application.yml`, OS environment variables, command-line args, in a defined precedence order (command-line args generally win over file-based properties — see file 05 for the full precedence chain).
4. Prints the Spring Boot banner (the ASCII art `:: Spring Boot ::` — can be disabled with `spring.main.banner-mode=off`).
5. Determines `WebApplicationType` and creates the matching `ApplicationContext`:

| Classpath contains | `WebApplicationType` | `ApplicationContext` created |
|---|---|---|
| `spring-webmvc` (Spring MVC / servlet API) | `SERVLET` | `AnnotationConfigServletWebServerApplicationContext` |
| `spring-webflux` and **not** `spring-webmvc` | `REACTIVE` | `AnnotationConfigReactiveWebServerApplicationContext` |
| Neither | `NONE` | `AnnotationConfigApplicationContext` |

6. Instantiates the discovered `ApplicationContextInitializer`s and applies them to the freshly created context.
7. Executes each registered `ApplicationContextInitializer.initialize(context)` — these can programmatically add property sources, register additional beans, etc., *before* the context is refreshed.
8. **`prepareContext()`** — attaches the environment to the context, applies initializers, and registers the primary `@SpringBootApplication` source class as a bean definition, and publishes the `ApplicationPreparedEvent`.
9. **`refreshContext()`** — this is the actual Spring IoC bootstrap: `@ComponentScan` runs, all bean definitions (yours + auto-configured) are resolved, singleton beans are instantiated and dependency-injected, `BeanPostProcessor`s run, and the embedded server (Tomcat etc.) is actually started here as a side-effect of the `ServletWebServerApplicationContext.onRefresh()` hook.
10. Throughout all these stages, Boot publishes a sequence of `SpringApplicationEvent`s and invokes any registered `SpringApplicationRunListener`s / `ApplicationListener`s so external code can hook into the lifecycle.

### The event sequence, in order (worth memorizing)

1. `ApplicationStartingEvent` — fired right at the start, before environment/context exist
2. `ApplicationEnvironmentPreparedEvent` — environment is ready, context isn't yet
3. `ApplicationContextInitializedEvent` — context created + initializers applied, beans not loaded yet
4. `ApplicationPreparedEvent` — bean definitions loaded, context not refreshed yet
5. `ApplicationStartedEvent` — context refreshed (all beans created), but before runners execute
6. `AvailabilityChangeEvent` (LivenessState.CORRECT) — Boot 2.3+, tells actuator liveness probes the app is up
7. `ApplicationReadyEvent` — `CommandLineRunner`/`ApplicationRunner` beans have executed, app is fully ready to serve traffic
8. `AvailabilityChangeEvent` (ReadinessState.ACCEPTING_TRAFFIC) — tells actuator readiness probes to start routing traffic
9. `ApplicationFailedEvent` — fired instead of the above if startup throws

```java
@Component
public class StartupLogger {

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("Order service is fully up and accepting traffic");
    }
}
```

---

## How auto-configuration actually gets triggered (tying back to file 02)

**Your Q:** How does auto-configuration work?

`@SpringBootApplication` → `@EnableAutoConfiguration` is what enables the machinery. During `refreshContext()`, `AutoConfigurationImportSelector` reads the candidate auto-configuration class list from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, and imports every one of them as a `@Configuration` class — each then gets evaluated against its own `@ConditionalOnXxx` guards as described in file 02.

---

## Real-world example — a `CommandLineRunner` that reacts to startup

```java
@Component
@RequiredArgsConstructor
public class CacheWarmupRunner implements CommandLineRunner {

    private final ProductCacheService cacheService;

    @Override
    public void run(String... args) {
        cacheService.warmUpTopSellingProducts();
    }
}
```

Runs automatically, once, right after the `ApplicationContext` is fully refreshed and before `ApplicationReadyEvent` fires — perfect place for cache warmup, one-time DB seed checks, or printing "loaded X countries/states" style startup diagnostics, matching your original note about display of country/state lists at startup.

---

## Tricky interview questions — Section 3

**Q1. At what exact point does the embedded Tomcat server actually start listening on the port?**
Inside `refreshContext()` — specifically `ServletWebServerApplicationContext.onRefresh()`, which is invoked as part of the standard `AbstractApplicationContext.refresh()` lifecycle (the same `refresh()` every Spring context goes through). This is *after* all singleton beans are created, which is why a `@PostConstruct` method can safely call another fully-wired bean, but the server isn't accepting connections yet at that exact moment either — it opens the socket right after refresh completes.

**Q2. Difference between `ApplicationStartedEvent` and `ApplicationReadyEvent` — why does it matter which one you listen to?**
`ApplicationStartedEvent` fires once the context is refreshed but *before* `CommandLineRunner`/`ApplicationRunner` beans run. `ApplicationReadyEvent` fires *after* those runners complete. If your runner does critical setup (e.g. loading a cache) and another component needs that cache to be ready, that component should listen for `ApplicationReadyEvent`, not `ApplicationStartedEvent`, or it may run before the cache is warm.

**Q3. How would you find out which `WebApplicationType` your app resolved to, and can you force it?**
It's logged in the startup banner/logs. You can force it explicitly (bypassing classpath deduction) with `spring.main.web-application-type=servlet|reactive|none`, or programmatically via `new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE).run(args)`.

**Q4. `CommandLineRunner` vs `ApplicationRunner` — what's the actual difference?**
Both run once after context refresh, in `Ordered`/`@Order` sequence if multiple exist. `CommandLineRunner.run(String... args)` gets the raw, unparsed command-line args. `ApplicationRunner.run(ApplicationArguments args)` gets a parsed wrapper that distinguishes option args (`--server.port=8081`) from plain non-option args and lets you query them by name — prefer `ApplicationRunner` when you actually need to interpret CLI flags.

**Q5. Your app hangs at startup with no error — how do you know if it's stuck *before* or *during* `refreshContext()`?**
Enable `--debug` (prints the auto-configuration condition evaluation report) and check whether any `ApplicationEnvironmentPreparedEvent`/`ApplicationContextInitializedEvent` logs appeared. If those show but nothing after, the hang is inside bean creation during refresh — usually a `@PostConstruct` or constructor doing a blocking network call (e.g. a DB connection pool trying to reach an unreachable DB with no timeout), which is one of the most common real production "Boot app won't start" incidents.
