# 01 - Spring Introduction & Why Spring Boot

> This file keeps your original notes as the base (lightly corrected) and adds the missing pieces around them — real examples, deeper "why", and tricky questions at the end.

---

## Your notes (corrected)

**(i)** The main objective of the Spring framework is to make J2EE application development easier.

**(ii)** The main objective of Spring Boot is to make Spring application development easier.

So the chain is: **J2EE was hard → Spring made it easier → Spring itself became hard to configure at scale → Spring Boot made Spring easier.** Same pattern repeating one level up. This is the single most important "why" answer in any Spring Boot interview — interviewers love asking "why boot on top of spring" and this chain is the answer.

---

## (i) Steps to implement a plain Spring MVC application (no Boot)

- **Step 1** – Create a Maven web project (packaging = war)
- **Step 2** – Add required Spring module dependencies in `pom.xml`
  - a) spring-core
  - b) spring-context
  - c) spring-web
  - d) spring-webmvc
  - e) validation (hibernate-validator)
  - f) jackson-databind (for JSON)
- **Step 3** – Configure `web.xml` (or a `WebApplicationInitializer` if you're going Java-config-only)
- **Step 4** – Configure `DispatcherServlet` (the front controller)
- **Step 5** – Configure DispatcherServlet internals
  1. ViewResolver (how logical view names map to actual JSPs/templates)
  2. DefaultServletHandler (so static resources don't get swallowed by the servlet)
- **Step 6** – Configure DataSource (DB connection)
- **Step 7** – Create Controller classes
- **Step 8** – Implement `@RequestMapping` handler methods

> **NOTE (your original point, still true):** Steps 1–6 are almost identical across every single Spring MVC project you will ever build. Only steps 7 and 8 (your actual business Controllers) change per project. This repetition of 1–6 is exactly the boilerplate Spring Boot eliminates via auto-configuration.

---

## Drawbacks of plain Spring application development

Your original notes:

1. **Lot of manual configuration** — XML config, Java config, `@Autowired` + component scan
   - `@Component` + component scan works for classes **you wrote** (you have the source code)
   - It does **not** work for framework-provided classes you didn't write — `DataSource`, `JdbcTemplate`, `RestTemplate` — those must be manually declared as `@Bean`
   - So even with annotations, you're still hand-wiring a chunk of your app → time-consuming, complex, and you end up memorizing tag/annotation combinations instead of writing business logic
2. **Dependency management is too hard** — you manually pick every jar version and hope they're mutually compatible (see "dependency hell" below)
3. **High barrier to entry** for newcomers trying Spring for the first time — too much ceremony before you see a single working endpoint

**Version compatibility issues (your note ii):** you don't know which Spring-core version is compatible with which Spring-MVC / Jackson / Hibernate version. This is usually called **"dependency hell"** — pull in `spring-web:5.3.1` and `jackson-databind:2.9.0` together and you may get a runtime `NoSuchMethodError` that only shows up when a specific code path executes, not at compile time.

**Result:** To solve all of this, Spring introduced a module called **Spring Boot**.

---

## What Spring Boot actually is

**(i)** Spring Boot exists to solve the problems above.

**(ii)** Spring Boot is **not a replacement** for the Spring framework — it's a module on top of it, same category as Spring Core, Spring MVC, Spring Data.

**(iii)** Spring Boot = Spring Core + Spring MVC + Spring Data + Spring Security + ... wired together with sane defaults, minus the manual configuration.

**(iv)** Using plain Spring you *can* build any kind of application (standalone, web, distributed) — it just takes a lot longer to actually ship it.

**End-to-end application types (your note):**
1. Standalone applications
2. Web applications
3. Distributed applications (microservices / REST APIs)

**(v)** Whatever plain Spring can do, Spring Boot can also do — but Boot gets you to production faster.

**(vi)** Spring Boot is a new *way* of building Spring applications, not a new framework.

**(vii)** Spring Boot's stated goal: simplify building **production-ready** Spring applications (not just "any" application — specifically ones ready to run in prod, with monitoring, health checks etc. baked in).

---

## Spring Boot features (your list, kept, each one now explained)

1. **Easier dependency management** — starter POMs (see file 02)
2. **Auto Configuration** — beans get created for you based on what's on the classpath (see file 02)
3. **Embedded server** — Tomcat/Jetty/Undertow/Netty ships *inside* your jar (see file 04)
4. **Actuator** — addresses non-functional requirements: health, metrics, monitoring — "production-ready" features (see file 06)
5. **DevTools** — automatic recompile + restart on save, speeds up the dev loop
6. **No XML configuration required** — annotation + convention driven
7. **Opinionated but highly customizable** — Boot picks sane defaults for you, but every default can be overridden
8. **Spring Boot CLI** — a command-line tool to quickly prototype Spring apps with Groovy scripts (rarely used in real jobs today, but interviewers still ask about it)

---

## Summary of what Boot buys you

A developer should not waste time on:
- a) Manual bean configuration
- b) Manually resolving compatible dependency versions
- c) Setting up monitoring by hand
- d) Deploying to an external server
- e) Redeploying manually after every code change

Spring Boot takes care of all of the above so the developer can focus purely on writing business logic (Controller/Service/Repository code).

---

## How to create a Spring Boot application

1. Using the Eclipse/STS IDE's built-in Spring Boot project wizard
2. Using **https://start.spring.io** (Spring Initializr) — pick your Boot version, Java version, build tool, and starters, download the zip
3. Using STS (Spring Tool Suite) IDE

In real jobs, almost everyone starts from start.spring.io (or an internal company archetype template built on top of it).

---

## Spring Core — the three application context flavors

Your notes captured the three ways to boot the IoC container manually:

```java
// a) Standalone (no web server at all)
ApplicationContext context =
    new AnnotationConfigApplicationContext(JavaConfig.class);

// b) Servlet-based web application
ApplicationContext context =
    new AnnotationConfigServletWebServerApplicationContext(JavaConfig.class);

// c) Reactive web application (WebFlux)
ApplicationContext context =
    new AnnotationConfigReactiveWebServerApplicationContext(JavaConfig.class);
```

This is exactly what `SpringApplication.run()` decides **for you automatically** at startup, based on what's on the classpath (spring-webmvc vs spring-webflux vs neither) — see file 03 for the full decision logic.

---

## Dependency Injection — quick recap (you'll need this vocabulary throughout)

1. **Setter Injection** — Spring calls the setter after construction
2. **Constructor Injection** — dependencies passed via constructor (recommended in modern Spring — makes fields `final`, fails fast if a required bean is missing, and plays well with immutability)
3. **Field-level Injection** (`@Autowired` directly on a field) — convenient but discouraged in production code: can't make the field `final`, hides dependencies from the constructor signature, and makes unit testing without Spring harder

```java
@Component
public class OrderService {

    private final PaymentClient paymentClient; // constructor injection - preferred

    @Autowired // optional on a single constructor since Spring 4.3+
    public OrderService(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }
}
```

**If we use plain Spring Core, the developer is responsible for:**
a) Writing the configuration (XML/Java)
b) Creating/bootstrapping the IoC container

---

## Loading properties files (plain Spring)

```java
@Configuration
@PropertySource("classpath:app.properties")
public class AppConfig { }
```

**Two ways to read properties once loaded:**
1. `Environment` object — `environment.getProperty("book.isbn")`
2. `@Value` annotation — `@Value("${book.isbn}")`

---

## Tricky interview questions — Section 1

**Q1. Spring Boot vs Spring Framework — what's the one-line difference an interviewer actually wants to hear?**
Spring Framework gives you the building blocks (IoC, DI, MVC, Data). Spring Boot is an opinionated, auto-configured way to *assemble* those building blocks quickly, with embedded servers and production-ready defaults, so you write less configuration code.

**Q2. Is Spring Boot a framework?**
No — technically it's a **tool/module built on top of** the Spring Framework. It doesn't introduce new core capabilities; it removes ceremony around using existing Spring capabilities. (Many interviewers accept "it's colloquially called a framework" too — but be ready to explain the nuance.)

**Q3. Can you use Spring Boot without Spring MVC?**
Yes — a Boot app can be a standalone CLI/batch app with `WebApplicationType.NONE`. Auto-configuration only pulls in MVC-specific beans if `spring-webmvc` is on the classpath.

**Q4. Why is constructor injection preferred over field injection in real production code?**
- Makes dependencies explicit and immutable (`final` fields)
- Fails fast at startup if a required bean is missing (vs a NullPointerException later at runtime with field injection)
- Makes unit testing trivial — you can `new OrderService(mockPaymentClient)` without needing Spring at all
- Prevents circular dependency issues from being silently possible (constructor injection surfaces circular deps immediately as a startup failure)

**Q5. Give a one-sentence definition of "opinionated but customizable" and why that phrase matters.**
Boot ships sensible defaults (e.g., Tomcat as embedded server, Jackson for JSON) so you get a working app instantly, but every one of those defaults can be swapped out via configuration or by excluding the auto-config class — it's not a lock-in.
