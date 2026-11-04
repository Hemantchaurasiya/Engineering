# 05 - Properties, `@ConfigurationProperties`, Profiles & Runners

---

## Loading the properties file in Spring Boot

**(i)** In Spring Boot, the properties filename must be either `application.properties` or `application.yml` (or the `.yaml` variant) by convention.

**(ii)** Boot loads these automatically at startup — no `@PropertySource` required for the default file.

**(iii)** If you want to load a **custom** properties file (not the default `application.properties`), you still use `@PropertySource`:

```java
@Configuration
@PropertySource("classpath:custom-db.properties")
public class CustomDbConfig { }
```

**(iv)** `@PropertySource` does **not** support YAML files directly. If you need a custom YAML file, either:
1. Convert it to a `.properties` file, or
2. Load it programmatically with `YamlPropertiesFactoryBean` and register it as a `PropertySource` manually

```java
@Bean
public static PropertySourcesPlaceholderConfigurer properties() {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("custom.yml"));
    PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
    configurer.setProperties(yaml.getObject());
    return configurer;
}
```

---

## `@Value` vs `@ConfigurationProperties`

`application.properties`:
```properties
book.isbn=89778
book.author=Sreenu
book.title=Spring Boot in Action
book.price=400
```

**Approach 1 — `@Value` (fine for one or two properties):**
```java
@RestController
public class BookController {

    @Value("${book.isbn}")
    private String isbn;

    @Value("${book.author}")
    private String author;
}
```

Your note: number of properties = number of `@Value` annotations you have to write. As the class grows, this becomes tedious and easy to typo (`${book.isbn}` vs `${book.Isbn}` fails silently at compile time — you only find out at runtime).

**Approach 2 — `@ConfigurationProperties` (correct approach for anything beyond 2-3 properties):**

Spring Boot introduced a `ConfigurationPropertiesBindingPostProcessor` (your note said "ConfigurationPropertiesBeanPostProcessor" — corrected name) that automatically binds a whole prefix of properties onto a POJO's fields by name-matching, and registers that POJO as a bean, invoked once per bean definition, with **no** `@Value` needed per field.

```java
@ConfigurationProperties(prefix = "book")
public class BookProperties {

    private String isbn;
    private String author;
    private String title;
    private int price;

    // getters + setters (or use a Java record in modern Boot, see below)
}
```

**Two annotations needed to wire this up (your note, kept + corrected):**

1. `@EnableConfigurationProperties(BookProperties.class)` on a `@Configuration` class — registers `BookProperties` as a Spring bean and activates the binding post-processor for it
2. `@ConfigurationProperties(prefix = "book")` on the POJO itself, as shown above

```java
@Configuration
@EnableConfigurationProperties(BookProperties.class)
public class BookConfig { }
```

**Modern alternative (Boot 2.2+):** skip step 1 entirely by annotating the POJO with `@Component` alongside `@ConfigurationProperties` — component scan picks it up directly. Even more modern (Boot 2.2+, works great with immutable records in Java 17+):

```java
@ConfigurationProperties(prefix = "book")
public record BookProperties(String isbn, String author, String title, int price) { }
```
...combined with `@ConfigurationPropertiesScan` on your main class instead of per-class `@EnableConfigurationProperties`.

**Why prefer `@ConfigurationProperties` over `@Value` in real production code:**
- Type-safe binding (including nested objects and `List`/`Map` properties, which `@Value` can't do cleanly)
- One typo in the prefix fails validation at startup if you add `@Validated` + JSR-303 annotations, instead of silently returning `null` at runtime like a mistyped `@Value` SpEL expression can
- Groups related config into one cohesive object instead of scattering `@Value` fields across many classes

---

## Profiles — switching between environments (`@Profile`)

**Your Q kept:** what are profiles and what's their purpose?

Profiles let you activate different beans/configuration depending on the environment (dev, test, staging, prod) without changing code.

### Plain Spring Core (manual, your notes kept)

```java
@Configuration
@PropertySource("classpath:db-dev.properties")
@Profile("dev")
public class DevConfig { }

@Configuration
@PropertySource("classpath:db-test.properties")
@Profile("test")
public class TestConfig { }
```

### Spring Boot (no extra config class needed)

Boot supports **profile-specific property files** out of the box by naming convention:

```
application.properties          ← common/shared properties
application-dev.properties      ← dev overrides
application-test.properties     ← test overrides
application-prod.properties     ← prod overrides
```

Activate a profile with:
```properties
spring.profiles.active=dev
```
or as a JVM arg: `-Dspring.profiles.active=prod`, or an env var: `SPRING_PROFILES_ACTIVE=prod` — this last form is exactly how it's typically injected in a Dockerfile/CI-CD pipeline (matches your original note).

You can still use `@Profile("dev")` on individual `@Bean`/`@Component` classes in a Boot app too — the file-naming convention and `@Profile` annotation work together, not instead of each other.

**Real-world example — different `RestTemplate` timeout per environment:**
```java
@Configuration
public class HttpClientConfig {

    @Bean
    @Profile("prod")
    public RestTemplate prodRestTemplate() {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Bean
    @Profile("!prod") // active for every profile except prod
    public RestTemplate devRestTemplate() {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(30)) // generous, for local debugging
                .build();
    }
}
```

---

## Runners — one-time startup activities

**Your notes kept:** used to run code exactly once, right after the IoC container has finished building. Typical uses:
1. Read/prime a value from cache
2. Load and display reference data (country list, state list) at startup

**Two types (your note, kept):**
1. `CommandLineRunner`
2. `ApplicationRunner`

(See file 03 for a worked example and the exact difference between the two.)

**Note (your original, still valid):** using Spring Boot conventions, you typically avoid writing manual `@Bean`, `@PropertySource`, `@Profile`, `@Import` for the common cases — instead you lean on `@Value`, `@Component`, `@Autowired`/constructor injection, and `@Qualifier` where disambiguation is needed, letting auto-configuration + component scanning + the profile-file convention do the rest.

---

## Property precedence — the part your notes didn't cover but interviewers always probe

From highest to lowest priority (higher overrides lower) — abbreviated to the ones that come up in real jobs:

1. Command-line arguments (`--server.port=9090`)
2. `SPRING_APPLICATION_JSON` (inline JSON env var/property)
3. JVM system properties (`-Dserver.port=9090`)
4. OS environment variables (`SERVER_PORT=9090`)
5. Profile-specific properties outside the packaged jar (`application-prod.properties` in the same dir as the jar)
6. Profile-specific properties packaged inside the jar (`application-prod.properties` in `classpath:/`)
7. Application properties outside the packaged jar (`application.properties` next to the jar)
8. Application properties packaged inside the jar (`application.properties` in `classpath:/`)
9. `@PropertySource` annotations
10. Default properties (`SpringApplication.setDefaultProperties`)

Practical takeaway: an env var **always** beats whatever is baked into `application.properties` inside the jar — which is exactly why Kubernetes/Docker deployments inject config via env vars instead of rebuilding the jar per environment.

---

## Tricky interview questions — Section 5

**Q1. `@Value` silently returns `null`/throws at runtime for a mistyped property key — how do you catch this at startup instead?**
Switch to `@ConfigurationProperties` + `@Validated` with JSR-303 (`@NotNull`, `@NotBlank`) on the POJO fields. A binding failure or validation failure then throws `ConfigurationPropertiesBindException` / `BindValidationException` at startup, failing fast instead of surfacing as a mysterious NPE deep in request-handling code later.

**Q2. Two profiles are active at once (`spring.profiles.active=dev,cloud`) and both define the same property key in their `application-<profile>.properties` — which one wins?**
The later profile in the comma-separated list takes precedence for properties (Boot processes them in order and later ones override earlier ones for the same key) — so `application-cloud.properties` would win over `application-dev.properties` in that example. Order in the active list matters.

**Q3. How would you make one property active in every profile except `prod`?**
Use profile expressions on `@Profile`: `@Profile("!prod")`. For file-based defaults, put the shared value in `application.properties` and only override it in `application-prod.properties`.

**Q4. What's the risk of putting a real secret (DB password) directly in `application-prod.properties` packaged inside the jar?**
It ships baked into the artifact — visible to anyone who can unzip the jar, and it ends up version-controlled if the file is checked in. Standard fix: never commit real secrets; inject them at runtime via environment variables or a secrets manager (Vault, AWS Secrets Manager, Kubernetes Secrets mounted as env vars), which — per the precedence table above — automatically override any placeholder value baked into the packaged properties file.

**Q5. Difference between `@ConditionalOnProperty` (file 02) and `@Profile` — when would you pick one over the other?**
`@Profile` is a coarse, environment-level switch (dev/test/prod) — typically several beans move together as a group. `@ConditionalOnProperty` is a fine-grained, single-feature toggle independent of environment (e.g. `feature.new-pricing-engine.enabled=true`), letting you flip one feature on/off in any environment without needing a whole new profile just for that flag — this is the standard mechanism behind feature flags in Spring Boot apps.
