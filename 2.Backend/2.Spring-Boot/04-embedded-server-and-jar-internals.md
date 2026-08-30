# 04 - Embedded Server & Jar Internals

---

## Your notes (corrected) — what is an embedded server

- A server bundled **inside** the application itself is called an embedded server.
- Conceptually: `Source Code + Tomcat (bundled as a dependency, pre-configured) = Spring Boot Application (runnable as a single jar)`
- Very useful for cloud deployments — you ship one artifact, no separate server install/config step on the target machine.

### The 4 embedded servers Boot supports

1. **Tomcat** — default, servlet-based
2. **Jetty** — servlet-based, lighter weight, alternative to Tomcat
3. **Undertow** — servlet-based, from the WildFly/JBoss ecosystem, known for low memory footprint
4. **Netty** — reactive only, used with `spring-boot-starter-webflux` (not servlet-based, event-loop model)

- Default embedded server = **Tomcat**, and you can override this default.
- You don't add Tomcat as a separate dependency — it rides in automatically with `spring-boot-starter-web`.
- `EmbeddedWebServerFactoryCustomizerAutoConfiguration` (your note, name corrected) is the auto-config responsible for creating the embedded server instance based on which server implementation is actually present on the classpath.

### Swapping the default embedded server

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-undertow</artifactId>
</dependency>
```

### Tomcat configuration lives in `application.properties`

```properties
server.port=8081
server.tomcat.threads.max=200
server.tomcat.connection-timeout=5s
server.servlet.context-path=/orders
```

---

## Jar vs War packaging

- Boot's default packaging is a self-contained, executable **jar** — no separate app server needed, no WAR deployment step.
- Because the server is embedded, the main class doesn't get packaged as a WAR by default; it needs no external servlet container, unlike classic Spring MVC.
- You *can* still produce a deployable WAR (for legacy environments that mandate an external Tomcat/WebLogic), by:
  1. Making your main class extend `SpringBootServletInitializer`
  2. Overriding `configure(SpringApplicationBuilder builder)`

```java
@SpringBootApplication
public class OrderServiceApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(OrderServiceApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

`SpringApplicationBuilder` (your note) figures out the required `ApplicationContext` type and starts instantiating all required beans, exactly the same way `SpringApplication.run()` does — it's the same machinery, just entered via the servlet container's `onStartup()` hook instead of a `main()` method.

---

## Normal jar vs Spring Boot "fat/uber" jar — the actual difference

**Your Q:** what's the difference between a normal jar and a Spring Boot jar?

- A **normal jar** built by plain `mvn package` contains only *your* compiled `.class` files. It has no `Main-Class` manifest entry set up to run standalone with all dependencies resolved — you'd need `-cp` pointing at every dependency jar separately to run it.
- A **Spring Boot jar** (e.g. `order-service-1.0.0-SNAPSHOT.jar`) is a **fat/uber jar** — it contains your classes **plus every dependency jar, nested inside it**, plus a custom class-loading mechanism, so `java -jar app.jar` just works with zero external classpath setup.

### What's inside a Spring Boot jar (your diagram, corrected structure)

```
order-service-1.0.0.jar
├── META-INF/
│   └── MANIFEST.MF
│         Main-Class: org.springframework.boot.loader.launch.JarLauncher
│         Start-Class: com.hemant.orderservice.OrderServiceApplication
├── BOOT-INF/
│   ├── classes/         ← your compiled application .class files + application.properties
│   └── lib/             ← every dependency jar, unmodified, nested as-is
└── org/springframework/boot/loader/launch/
    ├── Launcher.class          (abstract base)
    ├── JarLauncher.class
    └── WarLauncher.class
```

> Note: in Boot 3.2+, the loader package moved from `org.springframework.boot.loader` to `org.springframework.boot.loader.launch` — worth mentioning if asked about version differences.

### Step by step: what happens on `java -jar app.jar`

1. `java -jar app.jar` is the input to the JVM.
2. The JVM's default classloader loads only what the manifest's `Main-Class` needs — it does **not** understand nested jars natively (this is the whole reason Boot needs a custom launcher).
3. The JVM reads `META-INF/MANIFEST.MF`, finds `Main-Class: org.springframework.boot.loader.launch.JarLauncher`, and invokes `JarLauncher.main()`.
4. `JarLauncher` sets up a custom `LaunchedURLClassLoader` that knows how to read classes directly out of the **nested** `BOOT-INF/lib/*.jar` files and `BOOT-INF/classes/` — without needing to explode/unzip anything to disk first.
5. It then reads the `Start-Class` attribute from the manifest — this is *your* actual `@SpringBootApplication` class (`OrderServiceApplication` in the example) — and invokes its `main()` method using the custom classloader.
6. From there, normal `SpringApplication.run()` execution begins (see file 03).

`WarLauncher` follows the identical pattern but reads from `WEB-INF/classes` and `WEB-INF/lib` (the standard WAR layout) instead of `BOOT-INF`.

---

## Embedded vs external server — when to use which (your notes, kept)

**Embedded server preferred when:**
- App expects variable/high traffic and needs to scale instances up/down at runtime (auto-scaling)
- Deploying to cloud/container platforms (Kubernetes, ECS) — very natural fit for microservice architecture
- Benefits: autoscale, availability, cost efficiency (no per-instance manual server setup)

**External server preferred when:**
- Architecture is monolithic
- On-prem servers where traffic is known/bounded and IT already manages a shared app server fleet (common in legacy enterprise shops still running WebLogic/WebSphere)

---

## Tricky interview questions — Section 4

**Q1. Why can't the JVM directly run classes from a jar nested inside another jar (why does Boot need a custom launcher at all)?**
The JVM's built-in classloading (`URLClassLoader`) understands entries inside *one* jar/zip, but has no native concept of a jar-within-a-jar — it can't reach into `BOOT-INF/lib/spring-web-6.1.jar` sitting as a binary blob inside the outer jar. Boot's `JarLauncher` + `LaunchedURLClassLoader` implement custom logic to read those nested jar entries directly from the outer jar's bytes without extracting anything to disk, which is what makes the single self-contained executable jar possible.

**Q2. Can you unpack a Boot fat jar and run it as if it were exploded on disk? Why would you want to?**
Yes — `java -cp "BOOT-INF/classes:BOOT-INF/lib/*" com.hemant.orderservice.OrderServiceApplication` after extracting, or use `spring-boot:build-image` / Boot's built-in layered jar support to split into layers for Docker. Companies do this in Docker images to get better layer caching: dependencies (which change rarely) go in one Docker layer, your application classes (which change every build) go in a separate thin layer on top, so `docker build` doesn't re-push the entire dependency set on every code change.

**Q3. Your Boot app runs fine with `mvn spring-boot:run` in the IDE but throws `NoSuchMethodError` only when run as the packaged jar — what's a likely cause?**
Classic shaded/fat-jar dependency conflict: two different dependencies (possibly pulled transitively) bundle different versions of the same class, and the packaging step picked the wrong one, or a `provided`-scope dependency needed at runtime got excluded from the fat jar. `mvn dependency:tree` to spot duplicate/conflicting versions is the standard first move.

**Q4. Why does Spring Boot default to Tomcat over Jetty/Undertow?**
Historical/ecosystem reasons — Tomcat is the most widely used and battle-tested servlet container in the Java ecosystem, and Boot wanted the path of least surprise for the majority of users coming from traditional Spring MVC + Tomcat deployments. Undertow is often chosen instead in memory-constrained environments (smaller footprint), Jetty for specific embedded/OSGi use cases.

**Q5. If you exclude Tomcat and forget to add a replacement embedded server dependency, what happens at startup?**
`WebApplicationType` still resolves to `SERVLET` if `spring-webmvc` is on the classpath, but there is no `ServletWebServerFactory` bean available to create an actual server instance — the app fails to start with a clear `ApplicationContextException` telling you no embedded servlet container factory bean was found.
