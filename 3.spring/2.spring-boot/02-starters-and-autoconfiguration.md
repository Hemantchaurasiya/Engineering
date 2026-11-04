# 02 - Starter Dependencies & Auto-Configuration (deep dive)

---

## Your notes (corrected) — Feature 1: Dependency management is easier

Spring Boot introduced the concept of **starter dependencies**:
1. `spring-boot-starter-parent` POM
2. `spring-boot-starter-<feature>` dependencies

### 1) `spring-boot-starter-parent`

- Spring integrates with a huge number of third-party technologies — MongoDB, Redis, Kafka, etc.
- The parent POM centrally pins **version numbers** for Spring modules and common third-party libraries, so you never specify a version yourself for anything it manages (`dependencyManagement` in Maven terms).
- Spring Boot's own version is tied to a specific compatible Spring Framework version — the parent POM takes care of that mapping so you don't have to figure it out.

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
</parent>
```

If your company can't inherit the parent POM directly (e.g. you already have a corporate parent), you import `spring-boot-dependencies` as a BOM instead:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.3.4</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2) `spring-boot-starter-<feature>`

- `spring-boot-starter-web`
- `spring-boot-starter-jdbc`
- `spring-boot-starter-data-redis`

```
web application → spring-boot-starter-web → spring-core
                                            → spring-web
                                            → spring-webmvc
                                            → jackson-databind
                                            → tomcat-embed-core
                                            → hibernate-validator
```

**Note (your original):** a starter dependency pulls in every jar required for that feature. Add `spring-boot-starter-web` and you get everything needed to build a web app — no need to hand-pick individual jars.

- **starter-parent POM** → supplies version information for everything
- **starter-feature dependency** → supplies the actual required libraries for that feature, in one line

---

## Feature 2 — Auto Configuration (the real mechanics)

Your notes correctly identify the core tension:

- Beans can be created via XML, Java `@Bean` config, or `@Component` + component scan
- `@Component` only works on classes **you wrote** (you have the source)
- It cannot be applied to framework classes you don't own: `DataSource`, `JdbcTemplate`, `RestTemplate`, `MongoTemplate` — those still need manual `@Bean` declarations
- So even after adopting annotations, some manual configuration always remained

**Auto-configuration removes that remaining manual configuration too**, using `@SpringBootApplication` (specifically `@EnableAutoConfiguration` inside it).

> **Auto Configuration =** instead of the developer manually declaring beans for undefined (your own) or predefined (framework) types, Spring Boot inspects the classpath and configuration properties and creates the right beans for you automatically.

### `@SpringBootApplication` unpacked

```java
@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

is exactly equivalent to stacking these three:

```java
@SpringBootConfiguration
@ComponentScan
@EnableAutoConfiguration
public class OrderServiceApplication { }
```

| Annotation | What it does |
|---|---|
| `@SpringBootConfiguration` | A specialization of `@Configuration` — just Boot's own naming convention, marks this class as a source of bean definitions |
| `@ComponentScan` | Scans **your own** source-code classes (`@Component`, `@Service`, `@Repository`, `@Controller`) starting from the package of the annotated class downward — default base package = the package of the main class |
| `@EnableAutoConfiguration` | Handles **predefined/framework** classes — turns on the auto-configuration machinery |

---

## How `@ConditionalOnXxx` actually drives auto-configuration

This is the part your notes had a placeholder for — here's the real mechanism, and it's a favorite "explain internals" interview topic.

Auto-configuration classes are just regular `@Configuration` classes, but every `@Bean` method (or the whole class) is guarded by a `@ConditionalOnXxx` annotation, so the bean is only created **if the condition holds true**.

```java
@Configuration
@ConditionalOnClass(DataSource.class)          // only if DataSource.class is on the classpath
public class DataSourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean                  // only if the developer hasn't already declared their own DataSource bean
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
```

### Categories of `@ConditionalOnXxx` (your list, corrected & completed)

**1. Class conditions**
- `@ConditionalOnClass` — bean is created only if the given class **is** present on the classpath
- `@ConditionalOnMissingClass` — bean is created only if the given class is **absent**

**2. Bean conditions**
- `@ConditionalOnBean` — only if a bean of that type already exists in the container
- `@ConditionalOnMissingBean` — only if a bean of that type does **not** already exist (this is the #1 mechanism that lets *you* override any auto-configured bean — just declare your own `@Bean` of the same type and Boot's auto-config backs off)

**3. Property conditions**
- `@ConditionalOnProperty(name = "feature.enabled", havingValue = "true")` — only if that property is set to that value in `application.properties`/`.yml`

**4. Resource conditions**
- `@ConditionalOnResource(resources = "classpath:some-file.xml")` — only if the given resource exists on the classpath

**5. Web application conditions**
- `@ConditionalOnWebApplication` — only inside a web app context
- `@ConditionalOnNotWebApplication` — only in a non-web context

Real example — this is essentially how `DispatcherServletAutoConfiguration` is gated:

```java
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
public class DispatcherServletAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DispatcherServlet.class)
    public DispatcherServlet dispatcherServlet() {
        return new DispatcherServlet();
    }
}
```

### Where do these classes live? (your note, corrected for modern Boot)

- **Boot 1.x/2.x:** listed in `spring-boot-autoconfigure.jar` → `META-INF/spring.factories`
- **Boot 2.7+ / 3.x (current):** moved to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — a plain newline-separated file of fully qualified class names, replacing `spring.factories` for this purpose (this is a common "gotcha" question — see file 08)

### Why doesn't loading 150+ auto-config classes make every app slow/bloated?

Your note nailed the real question: *"There are 150+ autoconfigure classes but in my project I use only 4 or 5 — why load all of them?"*

Answer: Boot doesn't actually **activate** all 150+. It evaluates each one's `@ConditionalOnXxx` guards at startup — most fail their condition immediately (e.g. `@ConditionalOnClass(RedisConnectionFactory.class)` fails instantly if Redis isn't on the classpath) and are skipped cheaply without instantiating any beans. Only the handful whose conditions actually pass go on to register beans. `@ConditionalOnClass` checks are also ordered to fail fast, and Boot 2.x+ additionally supports auto-configuration report caching via `-Dspring.autoconfigure` diagnostics if you need to inspect exactly what got applied/skipped (`--debug` flag or the `/actuator/conditions` endpoint prints this).

**`DispatcherServletAutoConfiguration`** (your note) — this specific auto-config class is responsible for creating the `DispatcherServlet` object, guarded by `@ConditionalOnWebApplication(type = SERVLET)` + `@ConditionalOnClass(DispatcherServlet.class)`.

---

## Real-world example: excluding an auto-configuration class

Very common interview + real-job scenario: you don't want Boot to auto-configure a `DataSource` (e.g. your service doesn't use a DB, or you configure it manually):

```java
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class OrderServiceApplication { }
```

or via properties (useful when you can't touch the annotated class):

```properties
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

---

## Tricky interview questions — Section 2

**Q1. How does `@ConditionalOnMissingBean` let developers override Boot's defaults?**
Auto-config bean methods are annotated `@ConditionalOnMissingBean`. If you declare your own `@Bean` of that same type in your own `@Configuration` class, Boot's auto-configured bean condition fails (a bean already exists) and backs off — your bean wins. Order matters: your own `@Configuration` classes are processed before auto-configuration classes (auto-config classes are always registered last), which is exactly why this override pattern works reliably.

**Q2. What's the difference between `@Component`-based scanning and auto-configuration?**
`@ComponentScan` discovers **your own** classes annotated `@Component`/`@Service`/`@Repository`/`@Controller` under a base package. Auto-configuration is a separate mechanism (`@EnableAutoConfiguration`) that registers **framework-level** beans (DataSource, ObjectMapper, DispatcherServlet, etc.) conditionally, based on classpath + properties — it doesn't scan your packages at all, it reads a fixed list of candidate auto-config classes shipped inside `spring-boot-autoconfigure.jar`.

**Q3. Can two starters bring in conflicting versions of the same transitive dependency? How does Boot prevent it?**
The starter-parent's `dependencyManagement` block centrally pins one version for every managed dependency. As long as you don't explicitly override a version yourself, Maven/Gradle will resolve to the parent-pinned version regardless of which starter pulled it in transitively — this is exactly what avoids the "dependency hell" from file 01.

**Q4. If you add `spring-boot-starter-web` and `spring-boot-starter-webflux` to the same project, what happens?**
Both `spring-webmvc` and `spring-webflux` end up on the classpath, which confuses the `WebApplicationType` deduction logic (see file 03) — Boot's official guidance is: don't do this. If you truly need both (e.g. reactive gateway + a couple of blocking endpoints), you must explicitly set `spring.main.web-application-type=servlet` (or `reactive`) to force the choice.

**Q5. How would you debug "why did my custom `DataSource` bean never get picked up, Boot's default one is still active"?**
Check: (a) is your `@Configuration` class actually being scanned (right package, or explicitly imported)? (b) does your bean's return type exactly match what auto-config's `@ConditionalOnMissingBean` checks against (sometimes it's keyed by a more specific type)? (c) run with `--debug` or hit `/actuator/conditions` to print the auto-configuration report, which lists every conditional evaluation and why it matched/didn't match.

**Q6. Why does Boot 3.x replace `spring.factories` with the `.imports` file for auto-configuration specifically (while `spring.factories` is still used for some other things)?**
Performance and clarity — `spring.factories` was a single shared file used for many unrelated extension points (auto-config, `ApplicationListener`s, `ApplicationContextInitializer`s, etc.), so Boot had to parse the whole file and filter by key even when it only cared about one concern. The dedicated `.imports` file makes auto-configuration class loading a simple line-by-line read, which is measurably faster at large scale and easier to reason about/tooling-friendly.
