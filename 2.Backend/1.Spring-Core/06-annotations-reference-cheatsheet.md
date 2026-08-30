# 06 — Spring Core Annotations — Quick Reference Cheatsheet

| Annotation | What it does | Gotcha / interview point |
|---|---|---|
| `@Configuration` | Marks a class as a source of bean definitions (replaces `<beans>` XML root) | Internally CGLIB-proxies the class so calling one `@Bean` method from another still returns the **same Singleton instance** instead of a new object |
| `@Bean` | Declares a bean, placed on a method inside `@Configuration` | Use for beans you don't own the source of (3rd-party classes) |
| `@Component` | Generic stereotype — makes a POJO a Spring-managed bean | Base annotation; `@Service`, `@Repository`, `@Controller` are specializations of it |
| `@Service` | Specialization of `@Component` for service/business-logic layer | Purely semantic marker — behaves identically to `@Component` at the container level |
| `@Repository` | Specialization of `@Component` for DAO/persistence layer | Also enables **automatic exception translation** — converts JDBC/JPA-specific exceptions into Spring's unified `DataAccessException` hierarchy |
| `@Controller` / `@RestController` | Specialization of `@Component` for web layer | `@RestController` = `@Controller` + `@ResponseBody` on every method |
| `@ComponentScan` | Tells Spring which package(s) to scan for `@Component` classes | Disabled by default — must be explicitly enabled; scans current package + sub-packages if base package given |
| `@Autowired` | Injects a bean by type (falls back to name if multiple candidates) | Can go on constructor, setter, or field; constructor preferred |
| `@ImportResource` | Imports an XML config file into a Java `@Configuration` class | Used for hybrid XML + Java config migrations |
| `@Import` | Imports another `@Configuration` class into a Java config | Used to split large config into modules |
| `@Primary` | Marks default bean among multiple candidates of same type | Only one `@Primary` per type allowed |
| `@Qualifier` | Injects by explicit bean name — resolves ambiguity | Wins over `@Primary` when both are present |
| `@Scope` | Sets bean scope (`singleton`, `prototype`, `request`, `session`, etc.) | Default is `singleton` if omitted |
| `@Lazy` | Delays bean creation until first actual use | Can be on the bean itself, or on an injection point |
| `@PropertySource` | Loads a `.properties` file into the Spring `Environment` | Needs `classpath:` or `file:` prefix |
| `@Value` | Injects a single property value (with optional default `${key:default}`) | Throws `IllegalArgumentException` if key missing and no default |
| `@Profile` | Activates a bean/config only for a given environment | Class-level or method-level; multiple profiles can be active at once |
| `@Required` | Forces a setter-injected dependency to be mandatory | **Deprecated since Spring 5.1** — prefer constructor injection instead |
| `@PostConstruct` | Runs a method right after dependency injection completes (lifecycle callback) | JSR-250 standard annotation, not Spring-specific; commonly used for init logic that needs injected dependencies already set |
| `@PreDestroy` | Runs a method just before bean is destroyed | Only fires for **Singleton**-scoped beans on Container shutdown — never fires for Prototype beans |
| `@Conditional` / `@ConditionalOnProperty` etc. | Creates a bean only if a condition is true | Heavily used by Spring Boot's auto-configuration internally |
| `@DependsOn` | Forces one bean to be created **after** another, regardless of injection | Used when there's a dependency Spring can't detect automatically (e.g., static initialization order) |
| `@Lookup` | Method-injection technique to fetch a fresh Prototype bean inside a Singleton | Solves the "Prototype injected into Singleton" trap (see file 04) |

## Bean lifecycle callback options (interview loves this)

Three ways to hook into a bean's init/destroy lifecycle, in the order Spring checks them:

1. **`@PostConstruct` / `@PreDestroy`** (JSR-250, annotation-based) — **preferred modern way**
2. **`InitializingBean` / `DisposableBean`** interfaces (`afterPropertiesSet()` / `destroy()`) — old Spring-specific way, couples your class to Spring API
3. **`init-method` / `destroy-method`** attributes on `<bean>` (XML) or `@Bean(initMethod=..., destroyMethod=...)` (Java config) — useful when you can't modify the class itself (e.g., 3rd-party class)

```java
@Component
public class DbConnectionPool {

    @PostConstruct
    public void init() {
        System.out.println("Opening connection pool...");
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("Closing connection pool...");
    }
}
```

**Order guarantee:** constructor → dependency injection (constructor/setter/field) → `@PostConstruct` → bean ready for use → ... → `@PreDestroy` → bean destroyed.
This ordering is exactly *why* `@PostConstruct` exists — at constructor time, `@Autowired` fields/setters haven't run yet, so you can't safely use injected dependencies inside the constructor body itself; `@PostConstruct` guarantees they're already set.
