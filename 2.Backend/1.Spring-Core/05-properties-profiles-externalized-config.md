# 05 — Properties, Environment, @Value, @Profile (Externalized Configuration)

## Loading & reading values from a properties file

`sample.properties`
```
username=sreenu
password=welcome123
```

**Without Spring (plain Java, for context):**
```java
Properties p = new Properties();
p.load(new FileInputStream("sample.properties"));
String uname = p.getProperty("username");
String pwd   = p.getProperty("password");
```

**With Spring — step 1: load the file into the Container**
```java
@Configuration
@PropertySource("classpath:sample.properties")
public class AppConfig { }
```

**Step 2: read values — two ways**

### (1) `Environment` object

```java
@Component
public class SomeBean {
    @Autowired
    private Environment environment;

    public void printCreds() {
        System.out.println(environment.getProperty("blackink.brand"));
        System.out.println(environment.getProperty("blackink.color"));
    }
}
```
`Environment` is auto-registered as a bean by Spring at Container startup — you just `@Autowired` it.

### (2) `@Value` annotation

```java
@Component
public class SomeBean {
    @Value("${blackink.brand}")
    private String brand;

    @Value("${blackink.color}")
    private String color;
}
```

`PropertySourcesPlaceholderConfigurer` is the internal bean that recognizes and resolves `${...}` placeholders in `@Value`.

### Default values with `@Value`

```java
@Value("${blackink.brand:Parker}")   // "Parker" is default if key is missing
private String brand;
```

- If key **missing** in properties file → uses default value.
- If key **present** → property file value always wins over the default.
- If key **missing AND no default given** → app fails to start with:
  ```
  java.lang.IllegalArgumentException: Could not resolve placeholder 'blackink.brand' in value "${blackink.brand}"
  ```
  (this is a **fail-fast** behavior — good, catches missing config immediately)

---

## Environment-based properties (dev / test / uat / prod)

Real CI/CD flow:
```
Developer commits code -> Git -> Jenkins
  Jenkins/Maven pipeline (CI):
    1. Compile code
    2. Run JUnit tests
    3. Code quality checks
    4. SonarQube analysis
    5. Build JAR
    6. Build Docker image
    7. Push image to Docker Hub / ECR
  Deployment (CD):
    8. Deploy to dev -> test -> uat
    9. Deploy to production
```

Each environment has its own DB/config:
```
sample.properties          (common/shared defaults)
sample-dev.properties       (dev backend config)
sample-test.properties      (test backend config)
sample-prod.properties      (production backend config)
```

### Without Spring — manual approach

```java
String env = System.getProperty("environment");     // set at server/JVM startup
String filename = "sample-" + env + ".properties";
Properties p = new Properties();
p.load(new FileInputStream(filename));
```

### With Spring — `@Profile`

`@Profile` reads the **active profile** value that was set on the JVM at startup and picks the matching configuration.

**How to set the active profile:**

| Context | How |
|---|---|
| Standalone app | `System.setProperty("spring.profiles.active", "dev")` or JVM arg `-Dspring.profiles.active=dev` |
| application.properties | `spring.profiles.active=dev` |
| Tomcat | `Catalina.properties -> env=dev` (or via `-D` JVM opt on startup script) |
| JBoss | `server.xml -> env=dev` |
| Docker | `ENV SPRING_PROFILES_ACTIVE=dev` in Dockerfile, or `-e SPRING_PROFILES_ACTIVE=dev` at `docker run` |
| Kubernetes | set as a container env var in the Deployment YAML |

**Reading the active profile value programmatically:**
```java
String env = System.getProperty("spring.profiles.active");
// OR — the Spring-native way:
@Autowired Environment environment;
String[] activeProfiles = environment.getActiveProfiles();
```

### `@Profile` at class level

```java
@Configuration
@Profile("dev")
@PropertySource("classpath:sample-dev.properties")
public class DevConfig { }

@Configuration
@Profile("prod")
@PropertySource("classpath:sample-prod.properties")
public class ProdConfig { }
```
→ number of profiles = number of `@Configuration` classes.

### `@Profile` at method level

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Profile("dev")
    public DataSource devDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:h2:mem:devdb")
                .build();
    }

    @Bean
    @Profile("prod")
    public DataSource prodDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:mysql://prod-db:3306/orders")
                .build();
    }
}
```
→ one `@Configuration` class, multiple `@Bean` methods, each handles a different profile's config.

**Real Spring Boot convention (extends your notes — very commonly asked):**

Spring Boot auto-loads `application-{profile}.properties` (or `.yml`) automatically — no `@PropertySource` needed:
```
application.properties          <- common/default config, always loaded
application-dev.properties       <- loaded only when profile "dev" active
application-prod.properties      <- loaded only when profile "prod" active
```
```properties
# application.properties
spring.profiles.active=dev
```
Boot **merges** `application.properties` + `application-{active-profile}.properties`, with the profile-specific file's values **overriding** the common ones for matching keys.

**Interview-favorite follow-up:** *"What if you activate two profiles at once (`dev,cache`)? "* → Spring Boot supports **multiple comma-separated active profiles** — beans/config for all of them get merged/activated; if two profile-specific property files define the **same** key differently, the **later one in the list wins**.
