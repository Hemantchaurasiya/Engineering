# 07 — Tricky Interview Q&A Master Bank (5-10 yrs level)

### Q1. What's the actual difference between IOC and DI?
IOC is the **principle**: "don't create your own dependencies, let something else control creation and wiring." DI is **one implementation technique** of that principle used by Spring. (Dependency Lookup is another, less-preferred, technique of IOC.)

### Q2. BeanFactory vs ApplicationContext — which gets used in real projects and why?
`ApplicationContext` — because it eagerly instantiates singleton beans (fail-fast at startup instead of failing mysteriously in production later), and adds support for AOP, event publishing, i18n, and web contexts. `BeanFactory` is rarely used directly today.

### Q3. Are Singleton beans thread-safe?
**No, not automatically.** Singleton scope just means *one instance is shared*; it says nothing about thread safety of that instance's **mutable state**. If a Singleton bean has mutable instance fields that multiple threads write to, you get race conditions — you must handle synchronization yourself (or better: keep Singleton beans **stateless**, and store any per-request/per-thread data locally in method parameters/local variables, or use `ThreadLocal` if truly needed).

### Q4. Why are Spring beans generally stateless?
Because the default scope is Singleton, and a Singleton is shared across the whole app / all threads. If it held mutable state, concurrent requests would corrupt each other's data. Real services (`@Service`, `@Repository`) should only hold **immutable, injected dependencies** as fields — never per-request mutable state.

### Q5. What happens if you inject a Prototype bean into a Singleton bean via plain `@Autowired`?
The Singleton is created once at startup; at that moment Spring injects **one** instance of the Prototype bean into it. That same Prototype instance then gets reused forever inside the Singleton — defeating the purpose of "prototype." Fix: `ObjectFactory<T>`, `@Lookup` method injection, or manual `ApplicationContext.getBean()` call. (See file 04.)

### Q6. Does Spring call `@PreDestroy` on Prototype beans?
**No.** Spring hands off a Prototype bean and does not track it afterward — it's the caller's responsibility to clean it up if needed. `@PreDestroy` only fires for Singleton beans when the Container itself shuts down.

### Q7. Constructor injection fails on circular dependency, but field/setter injection "works" — is that actually a good thing?
No — it's a design smell that field/setter injection just happens to paper over. Two classes needing each other directly usually means a missing abstraction. Since Spring Boot 2.6, circular references are **disabled by default** even for field/setter injection — you'd have to explicitly set `spring.main.allow-circular-references=true` to allow it, which is a strong hint you should instead refactor.

### Q8. What's the actual mechanism Spring uses to resolve setter/field-based circular dependencies (the "3-level cache")?
Spring maintains 3 caches during singleton creation:
1. `singletonObjects` — fully initialized beans
2. `earlySingletonObjects` — raw, not-fully-initialized bean references (created but dependencies not yet injected)
3. `singletonFactories` — factories that can produce an early reference (used for potential AOP-proxying)

When A needs B and B needs A: A's raw instance is put into the early cache **before** its dependencies are injected, so when B needs A, it gets that raw early reference instead of triggering "A" creation again — breaking the infinite loop. This mechanism only works because field/setter injection allows the bean to exist (empty) before its dependencies are set; **constructor injection can't do this**, because the object literally can't exist until the constructor call completes with all args resolved — hence circular constructor injection always fails.

### Q9. Why can constructor injection never resolve circular dependencies, but setter/field injection sometimes can?
Constructor injection requires **both** objects to be fully constructed (with resolved args) before either exists — a chicken-and-egg deadlock. Setter/field injection lets Spring create a **bare, uninitialized object first**, register that early reference, and inject the fields **afterward** — so the "half-built" object can be handed to the other bean while it's still being wired.

### Q10. `@Component` vs `@Bean` — when would you be FORCED to use `@Bean` instead of `@Component`?
When you don't own the class's source code (e.g., `RestTemplate`, `ObjectMapper`, `DataSource`, any 3rd-party library class) — you can't add `@Component` to a class you can't edit. Also when a bean's construction needs conditional logic / parameters computed at config time.

### Q11. What does `@Configuration` actually do differently from a plain class with `@Bean` methods scattered elsewhere?
`@Configuration` classes are **CGLIB-proxied** by Spring. If method `beanA()` internally calls `beanB()` (another `@Bean` method) directly in Java, the proxy intercepts that call and returns the **same cached Singleton instance** of B instead of running `beanB()` again — this is how "bean methods calling each other" still respects Singleton scope. Without `@Configuration` (e.g., using `@Component` with `@Bean` methods, which Spring also allows in "lite mode"), that call-interception doesn't happen — you'd get a brand-new object each time, silently breaking Singleton semantics.

### Q12. If two beans of the same type exist and neither has `@Primary` or `@Qualifier`, what happens?
`NoUniqueBeanDefinitionException` at startup — Spring refuses to guess.

### Q13. Can you have `@Qualifier` on a `@Bean` method as well as `@Component`?
Yes — `@Qualifier` can annotate a `@Bean` method (to name that bean) exactly like it names a `@Component` class, or annotate the injection point to request a specific name.

### Q14. What's the real difference between `@Autowired(required=false)` and using `Optional<T>`?
`@Autowired(required = false)` — if no matching bean exists, the field stays `null` (NPE risk later). `Optional<T>` as the injection type — Spring wraps the result in `Optional.empty()` if not found, forcing the consumer to explicitly handle absence — safer, more explicit code.

### Q15. Difference between `@Resource` and `@Autowired`?
`@Resource` (JSR-250, `javax`/`jakarta.annotation`) resolves **by name first, then by type**. `@Autowired` (Spring-specific) resolves **by type first**, and only uses name/`@Qualifier` to break ties among multiple type matches.

### Q16. What does "ApplicationContext is eager for singletons" actually mean in practice for debugging?
It means **all** config/wiring errors across the **entire app** surface immediately at startup (`Caused by: ... NoSuchBeanDefinitionException` etc.) rather than at random times in production when a rarely-used code path finally calls `getBean()`. This is the "fail-fast" principle — one of the biggest practical reasons `ApplicationContext` is preferred over lazy `BeanFactory`.

### Q17. What is a `BeanDefinition`, concretely?
It's Spring's internal metadata object describing a bean — its class, scope, constructor args, property values, lazy-init flag, init/destroy method names, etc. It is **not** the actual object instance — it's the "recipe." The Container reads `BeanDefinition`s to know **how** to create actual instances.

### Q18. Explain the actual bean creation lifecycle, in strict order.
1. Instantiate raw object (constructor call, dependencies resolved recursively first if constructor injection)
2. Populate properties (setter/field injection, if used)
3. `Aware` interface callbacks if implemented (`BeanNameAware`, `ApplicationContextAware`, etc.)
4. `BeanPostProcessor.postProcessBeforeInitialization()`
5. `@PostConstruct` / `InitializingBean.afterPropertiesSet()` / custom `init-method`
6. `BeanPostProcessor.postProcessAfterInitialization()` — **this is where AOP proxies get created!**
7. Bean ready for use
8. ... (app runs) ...
9. `@PreDestroy` / `DisposableBean.destroy()` / custom `destroy-method` on Container shutdown

### Q19. Why is `@PostConstruct` needed if the constructor already runs after DI in constructor-injection style?
With **constructor** injection, yes, all deps are set by the time the constructor body runs. But with **field/setter** injection, the constructor runs **before** those fields get populated — so any init logic that depends on injected fields would NPE if placed in the constructor. `@PostConstruct` is guaranteed to run **after all injection styles** are complete, making it the universally-safe place for init logic regardless of DI style used.

### Q20. Real production scenario: `@Profile("prod")` bean and `@Profile("dev")` bean both provide `DataSource`. What happens if NO profile is active at all?
Neither bean gets created — if some other bean has a hard `@Autowired DataSource` dependency, the app fails to start (`NoSuchBeanDefinitionException`) because there's no `DataSource` bean matching (both are profile-gated off). Best practice: always define a default profile or make critical beans available without profile gating, or set `spring.profiles.active` with a sane default in `application.properties`.

### Q21. What's `spring.profiles.active` vs `spring.profiles.include`?
`spring.profiles.active` **sets** (replaces) the active profile list. `spring.profiles.include` **adds** extra profiles unconditionally on top of whatever active profile is chosen — commonly used to always pull in a shared/common profile (e.g., `logging`, `swagger`) regardless of environment.

### Q22. Can `@Value` inject a List/Map, not just a String?
Yes — with comma-separated properties (`@Value("${my.list}")` where property is `a,b,c`, injected into `List<String>`), or via SpEL: `@Value("#{'${my.list}'.split(',')}")`. For complex structured config (nested objects, lists of objects), the recommended real-world approach is `@ConfigurationProperties` instead of many scattered `@Value`s.

### Q23. `@Value` vs `@ConfigurationProperties` — when would you pick one over the other in a real project?
`@Value` — good for one-off, simple scalar values. `@ConfigurationProperties` — good for **grouped, structured** config (a whole `app.mail.*` block mapped to a POJO) — type-safe, supports validation (`@Validated`), supports relaxed binding (`kebab-case` in YAML → camelCase in Java), and is far easier to unit test/maintain when there are more than a handful of properties.

### Q24. Why does Spring disable `@ComponentScan` by default rather than always scanning everything?
Performance — with hundreds of JARs on the classpath in a real enterprise app, scanning the **entire** classpath for `@Component` on every startup would be extremely slow. Explicit base packages keep scanning fast and predictable.

### Q25. If a class has both `@Component` and is also declared via a `@Bean` method in a `@Configuration` class, what happens?
You'd end up with **two separate bean definitions** for the same class (different bean names, e.g. auto-generated `myClass` from `@Component` and the `@Bean` method's name) — likely causing ambiguity errors (`NoUniqueBeanDefinitionException`) wherever that type is injected without a qualifier. In practice: **never do both** — pick one declaration mechanism per class.

### Q26. What's the difference between `@Primary` and marking a bean `@Qualifier` with a default-sounding name like `"default"`?
`@Primary` is a Spring-recognized mechanism that auto-resolves ambiguity — no `@Qualifier` needed at injection points. A `@Qualifier("default")` bean is **not automatically preferred**; every injection point still must explicitly say `@Qualifier("default")`, or it still fails with ambiguity — naming something "default" doesn't make Spring treat it specially.

### Q27. Real scenario: You have a Singleton `@Service` with a `@Autowired` `HttpServletRequest`. Does this break "Singleton has no state" rule?
No, surprisingly — Spring injects a special **scoped proxy** for `HttpServletRequest` (it's registered with `request` scope internally by Spring MVC's web context), so the Singleton doesn't actually hold real per-request state directly; the proxy transparently delegates to the *real* current-thread's request object at call time. This is a well-known exception pattern: injecting a narrower-scoped bean into a wider-scoped one **works** specifically because Spring provides an auto-generated scoped proxy for these particular web-scoped beans.

### Q28. How would you manually achieve the same "scoped proxy into singleton" trick for your own custom Prototype/Request bean?
```java
@Bean
@Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)
public MyBean myBean() { return new MyBean(); }
```
`proxyMode = TARGET_CLASS` (or `INTERFACES`) tells Spring to inject a **proxy** instead of a direct reference — the proxy resolves the real, fresh instance from the Container on **every method call**, solving the Case-4 scope-mismatch trap from file 04 without manually writing `ObjectFactory`/`@Lookup` code.

### Q29. Difference between `@Import` and `@ComponentScan`?
`@Import` explicitly registers **specific** `@Configuration` classes (or regular classes/`ImportSelector`s) — precise and predictable. `@ComponentScan` scans an entire **package** and picks up **anything** annotated `@Component` (and its specializations) — broader, less explicit, relies on package structure discipline.

### Q30. What is `@ImportResource` used for and when would you actually need it in 2024+ code?
Loads a legacy XML bean config file into an otherwise Java-config-based app — mainly used during **migration** of old XML-heavy Spring apps to Java/annotation config, without a big-bang rewrite.

### Q31. Trick question: does marking a class `final` break Spring's ability to create a bean from it?
It depends. Plain bean creation (constructor injection, no AOP) works fine on `final` classes. But if that bean needs to be **proxied** (e.g., `@Transactional`, `@Async`, `@Cacheable`, or any AOP advice, or a scoped proxy per Q28) and Spring falls back to **CGLIB subclass-based proxying**, CGLIB **cannot subclass a `final` class** → `BeanCreationException` at startup. (JDK dynamic proxies avoid this but only work for interface-based beans.) In real code: avoid `final` on classes that will be `@Transactional`/`@Async`/AOP-advised, unless you're certain they're interface-proxied.

### Q32. What's the practical difference between constructor injection with a `final` field vs setter injection, in terms of testability?
Constructor + `final` field → you can construct the object directly with `new MyService(mockDep)` in a plain JUnit test, zero Spring context needed, and the compiler guarantees the dependency is always set. Setter injection → you *can* forget to call the setter in a test and get a silent `NullPointerException` deep inside a method later, much harder to trace.

### Q33. Real-world: why might a team explicitly avoid `@Primary` across a large codebase?
Because `@Primary` is an *implicit* default — new developers reading an injection point with plain `@Autowired MyInterface dep` can't tell **which** implementation is actually wired without hunting for the `@Primary` annotation elsewhere in the codebase. `@Qualifier` at every injection point is more verbose but far more explicit/traceable — many teams enforce "no `@Primary`, always `@Qualifier`" as a code-review rule for exactly this reason.

### Q34. If `@Autowired` field injection is discouraged, why does Spring still support it, and where is it actually still acceptable?
Mainly for brevity in **test classes** (`@MockBean`/`@Autowired` fields in `@SpringBootTest` classes are conventionally field-injected since test classes aren't meant to be instantiated manually with `new`) and quick prototyping. In production business logic, constructor injection remains the standard.

### Q35. What does "Spring Container is just an in-memory registry, not a HashMap literally" actually mean at the code level?
Concretely, `DefaultListableBeanFactory` (the real class behind `ApplicationContext`) holds bean definitions in something like `Map<String, BeanDefinition> beanDefinitionMap` for metadata, and singleton instances in a separate `Map<String, Object> singletonObjects` cache (part of `DefaultSingletonBeanRegistry`). So conceptually it *is* map-backed, but it's a purpose-built, thread-safe registry with lifecycle logic layered on top — not something you'd casually treat as a plain `HashMap` in your own code.
