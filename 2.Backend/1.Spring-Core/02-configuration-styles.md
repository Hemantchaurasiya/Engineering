# 02 — Configuration Styles: XML → Java Config → Annotation

## What is "Configuration"?

Configuration tells Spring three things:
1. **What** objects to create
2. **How** to create them (constructor args, dependencies)
3. **What** dependencies to inject where

**Types:**
1. XML Configuration
2. Java Configuration (`@Configuration` + `@Bean`)
3. Annotation Configuration (`@Component` + `@Autowired`)

---

## 1) XML Configuration

```xml
<beans>
  <bean id="pedigree" class="com.pkg.Pedigree" />

  <bean id="dog" class="com.pkg.Dog">
      <constructor-arg ref="pedigree" />   <!-- Constructor injection -->
  </bean>

  <bean id="dogOwner" class="com.pkg.DogOwner">
      <constructor-arg ref="dog" />
  </bean>
</beans>
```

```java
ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");
Writer writer = (Writer) context.getBean("writer");
writer.write();
```

### Full working example (Pen/Ink, corrected from your notes)

```java
public interface Pen {
    void write();
}
public interface Ink {
    String getBrandName();
    String getColor();
}

public class BlackInk implements Ink {
    public String getBrandName() { return "Parker"; }
    public String getColor()     { return "Black"; }
}

public class FountainPen implements Pen {
    private final Ink ink;
    public FountainPen(final Ink ink) { this.ink = ink; }
    public void write() {
        System.out.println("Writing with " + ink.getColor() + " ink of " + ink.getBrandName() + " brand");
    }
}

public class Writer {
    private final Pen pen;
    public Writer(final Pen pen) { this.pen = pen; }
    public void write() { pen.write(); }
}
```

```xml
<beans>
  <bean id="blackInk" class="com.st.BlackInk" />

  <bean id="fountainPen" class="com.st.FountainPen">
      <constructor-arg ref="blackInk" />
  </bean>

  <bean id="writer" class="com.st.Writer">
      <constructor-arg ref="fountainPen" />
  </bean>
</beans>
```

```java
public static void main(String[] args) {
    ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");
    Writer writer = (Writer) context.getBean("writer");
    writer.write();   // Output: Writing with Black ink of Parker brand
}
```

### pom.xml — minimum dependencies for plain Spring project

```xml
<dependencies>
  <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-core</artifactId>
      <version>5.3.30</version>   <!-- 4.2.5 is very old / EOL; use a current 5.x/6.x in real projects -->
  </dependency>
  <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>5.3.30</version>
  </dependency>
</dependencies>
```

`spring-context` internally pulls `spring-core`, `spring-beans`, `spring-expression` — these together give you the full IOC container. (In a real Spring Boot project you never manage these manually — `spring-boot-starter` brings them transitively.)

### Drawbacks of XML Configuration

1. **Learning curve** — need to learn XML syntax/schema to work with it.
2. **No type safety** — if you pass a wrong `ref`, XML has no compile-time check; error only shows up at **runtime** when Container tries to wire it.
3. **Readability** — logic is in Java, wiring is in XML → constant context-switching between files.
4. **Maintenance** — with hundreds of beans, config becomes huge; risk of duplicate bean ids.
5. **No conditional bean creation** — can't easily say "create this bean only if X" in plain XML (Java Config solves this with `@Conditional`, `@Profile`).

---

## 2) Java Configuration — `@Configuration` + `@Bean`

```java
@Configuration                 // equivalent of <beans> root tag
public class JavaConfig {

    @Bean                       // equivalent of <bean id="blackInk" .../>
    public BlackInk blackInk() {
        return new BlackInk();
    }

    @Bean                       // Spring detects the parameter and auto-wires
    public FountainPen fountainPen(BlackInk blackInk) {   // constructor injection via method param
        return new FountainPen(blackInk);
    }

    @Bean
    public Writer writer(FountainPen fountainPen) {
        return new Writer(fountainPen);     // corrected: your notes had `new FountainPen(fountainPen)` by mistake
    }
}
```

```java
ApplicationContext context = new AnnotationConfigApplicationContext(JavaConfig.class);
Writer writer = (Writer) context.getBean("writer");
```

**Key rule:** the **method name becomes the bean id** by default (`blackInk()` → bean id `"blackInk"`). You can override with `@Bean("customName")`.

---

## 3) Annotation Configuration — `@Component` + `@ComponentScan` + `@Autowired`

Problem: if a project has 100+ beans, writing a `@Bean` method for each in `JavaConfig` is still boilerplate. Solution: **let Spring discover beans automatically.**

```java
@Component
public class BlackInk implements Ink { ... }

@Component
public class FountainPen implements Pen {
    private final Ink ink;
    @Autowired                              // constructor injection
    public FountainPen(Ink ink) { this.ink = ink; }
    ...
}

@Component
public class Writer {
    private final Pen pen;
    @Autowired
    public Writer(Pen pen) { this.pen = pen; }
    ...
}
```

**XML way to enable scanning:**
```xml
<context:component-scan base-package="com.st" />
```

**Java way to enable scanning:**
```java
@Configuration
@ComponentScan(basePackages = "com.st")
public class JavaConfig { }
```

### Why is Component Scan disabled by default?

Because a real project may have **hundreds of JARs** on the classpath. `@Component` could theoretically be anywhere. If Spring scanned the **entire classpath** by default, startup would be extremely slow. So you must **explicitly** tell it which package(s) to scan — Spring only scans the current package + sub-packages by default when you do specify a base package.

```java
@ComponentScan(basePackages = "com.st")
@ComponentScan(basePackages = {"com.st", "com.abc"})
@ComponentScan(basePackageClasses = SomeMarkerClass.class)   // type-safe, refactor-safe alternative to string package names
```

> Spring Boot's `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` (scans the package of the main class and everything under it) — that's why in Boot apps you almost never write `@ComponentScan` explicitly, as long as your classes live under (or below) the main application class's package.

### 3 ways to declare a bean — summary

| Style | Annotation/tag | When used in real projects |
|---|---|---|
| XML | `<bean>` | Legacy projects; almost never for new code |
| Java Config | `@Bean` (inside `@Configuration`) | For beans you **don't own** the source of — 3rd-party classes like `DataSource`, `RestTemplate`, `ObjectMapper`, `KafkaTemplate` — you can't put `@Component` on their class |
| Annotation | `@Component` (and specializations `@Service`, `@Repository`, `@Controller`) | For **your own** classes — this is the default in Spring Boot apps |

### Types of dependency injection with `@Autowired`

```java
@Component
public class A {
    private final B b;

    @Autowired                       // (1) Constructor injection — RECOMMENDED
    public A(B b) { this.b = b; }

    @Autowired                       // (2) Setter injection
    public void setB(B b) { this.b = b; }

    @Autowired                       // (3) Field injection — NOT recommended
    private B b;
}
```

**Why field injection is discouraged (interview favorite):**
1. Can't write proper unit tests without reflection or a Spring test context (no constructor to pass a mock into).
2. Hides the class's dependencies — can't tell from the constructor how "big" the class is (bad code smell → too many `@Autowired` fields silently signals SRP violation).
3. Field can't be made `final` → object can theoretically be left in a partially-constructed state.
4. Circular dependencies get silently allowed via field/setter injection (see file 03) — constructor injection makes them fail fast, which is actually a good thing.

**Combination note (from your original notes, still valid in real projects):**
`@ComponentScan` also works *with* `@Bean` methods and XML beans in the *same* application — this is common in real projects, e.g.:
```java
a) pure XML config
b) XML + Java Config mixed (@ImportResource in a @Configuration class)
c) Java Config + @Component/@Autowired mixed  <-- most common in real Spring Boot apps
```
