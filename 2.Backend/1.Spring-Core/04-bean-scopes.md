# 04 — Bean Scopes & Lifecycle, @Lazy

## Bean Scope = lifetime of a bean in the Container

(when Container creates it, when it gets destroyed)

### Types of scope

1. **Singleton** — Spring core scope, default
2. **Prototype** — Spring core scope
3. **Request** — web-aware scope
4. **Session** — web-aware scope
5. **Application** — web-aware scope
6. **WebSocket** — web-aware scope

> **Corrected note:** Request/Session/Application/WebSocket scopes only work inside a **web application context** (`DispatcherServlet`-managed context) — they are **not** available in a plain standalone Spring Container. Trying to use them outside a web app throws `BeanCreationException: Scope 'request' is not active`.

### 1) Singleton

> "**One object per Container per bean definition.**"

- Created **during Container startup** (eagerly, for `ApplicationContext`) and lives until the Container itself is closed/destroyed.
- Singleton scope = **Container-scoped lifetime**, not JVM-scoped.
- **Not the same as GoF Java Singleton pattern** — GoF Singleton = one instance for the **entire JVM/classloader** (usually via private constructor + static instance). Spring Singleton = one instance **per bean definition per Container** — if you spin up two `ApplicationContext`s (rare, but happens in tests), you get two separate "singleton" instances.

```xml
<bean id="abc" class="com.pkg.ABC" />     <!-- one object created for THIS bean definition -->
<bean id="abc2" class="com.pkg.ABC" />    <!-- a DIFFERENT object, new bean definition, same class -->
```

### 2) Prototype

> "**A new object every time the bean is requested.**"

- Every `getBean()` call, or every injection point, gets a **brand-new object**.
- Spring Container creates it and **hands it off** — it does **not** keep a reference afterward, so:
  - Spring does **not** call the destroy lifecycle callback (`@PreDestroy`, `DisposableBean`) for prototype beans — that's the caller's responsibility.
  - The object is eventually garbage collected normally by the JVM once nothing references it.

```xml
<bean id="blackInk" class="com.st.BlackInk" scope="prototype" />
```
```java
@Component
@Scope("prototype")            // default scope (if omitted) is "singleton"
public class BlackInk { }
```

**Default scope = Singleton** if nothing specified.

**When to use which (interview-favorite):**
- Class has **no instance/mutable state** → **Singleton** (safe to share, more memory-efficient, thread-safe by default *if truly stateless*).
- Class **holds state that varies per use** (e.g. a `Cart`, a `ReportBuilder` accumulating data) → **Prototype**.

---

## Scope Combination Cases (the classic interview trap)

```
class A { B b; }   // A depends on B
```

**Case 1 — A Singleton, B Singleton:** one instance of each, shared across all requests. Simple, no surprises.

**Case 2 — A Prototype, B Prototype:** every time A is requested, a fresh A **and** a fresh B are created together.
```
req1 -> (a1, b1)
req2 -> (a2, b2)
req3 -> (a3, b3)
```

**Case 3 — A Prototype, B Singleton:** every request gets a new `A`, but the **same** `B` instance is reused/injected into every new `A` (because B, being Singleton, was created once at startup).
```
req1 -> a1 (injected with the one shared b)
req2 -> a2 (same shared b)
req3 -> a3 (same shared b)
```

**Case 4 — A Singleton, B Prototype — THE TRAP:** ⚠️
```
req1 -> a1 (injected with b1, created once at startup)
req2 -> a1 (SAME a1, and STILL b1 — no new B is ever created!)
req3 -> a1 (SAME a1, SAME b1)
```

**Why:** `A` (Singleton) is created **once**, at Container startup. At that single moment, Spring injects **one** instance of `B` into it. After that, `A` is never re-created, so `B` is never re-injected — you're stuck with the **first** `B` instance forever, even though `B` is declared `prototype`. Spring will **not** silently give you a new `B` each time you call a method on `A` — plain field injection of a prototype into a singleton effectively behaves like a singleton.

> **Golden rule:** *"Never inject a shorter-lived bean into a longer-lived bean naively."*

### The real fix for Case 4 (this is asked constantly in interviews)

You need the Singleton to **fetch a fresh prototype bean on every method call**, not just once at wiring time. Options:

**Option A — `ObjectFactory` / `Provider`:**
```java
@Component
public class A {
    private final ObjectFactory<B> bFactory;   // Spring auto-implements this

    public A(ObjectFactory<B> bFactory) { this.bFactory = bFactory; }

    public void doWork() {
        B freshB = bFactory.getObject();       // NEW prototype instance every call
        freshB.process();
    }
}
```

**Option B — `@Lookup` method injection:**
```java
@Component
public abstract class A {
    @Lookup
    protected abstract B getB();     // Spring overrides this at runtime (via CGLIB) to return a fresh prototype B each call

    public void doWork() {
        B freshB = getB();
        freshB.process();
    }
}
```

**Option C — inject `ApplicationContext` and call `getBean(B.class)` manually** (works, but couples your class to the Spring API directly — least preferred).

---

## `@Lazy`

1. Marks a bean to be **lazily initialized** — created only when first actually referenced/requested, not at Container startup.
2. Can annotate: class level (`@Component`/`@Configuration`), or method level (on a `@Bean` method).
3. **Why singletons are eagerly created by default:** so that any missing-config or wiring errors are discovered **immediately at startup** ("fail fast") rather than randomly at runtime in production.
4. To force eager init even when `@Lazy` might apply elsewhere: `@Lazy(value = false)`.
5. Equivalent in XML: `lazy-init="true"` on `<bean>`.
6. `@Lazy` behavior:
   - Bean is **not created** until something else references/injects it, or it's explicitly requested via `getBean()`.
7. `@Lazy` can also be placed at an **injection point** (on a field/parameter with `@Autowired`) — this delays creation of *that particular dependency* even if the dependency bean itself isn't globally marked `@Lazy`. This is exactly the mechanism used to **break circular dependency deadlocks** (see file 03).
8. Introduced in **Spring 3.0**.

```java
@Component
@Lazy
public class ExpensiveReportGenerator {
    public ExpensiveReportGenerator() {
        System.out.println("Expensive setup running...");
    }
}
```
This constructor only runs the **first time** `ExpensiveReportGenerator` is actually needed — not at app startup.

**Real-world use case:** a bean that loads a large ML model, or opens an expensive network connection, that's only used in one rarely-hit code path — no point paying that startup cost for every app boot.
