# 03 — Dependency Injection Types, @Primary, @Qualifier, @Required, Circular Dependency

## Two ways to apply Dependency Injection

1. **Setter Injection** — dependency injected into target object via a setter method.
2. **Constructor Injection** — dependency injected into target object via the constructor.

```java
class B { }

class A_ConstructorInjection {
    B b;
    A_ConstructorInjection(B b) { this.b = b; }
}

class A_SetterInjection {
    B b;
    public void setB(B b) { this.b = b; }
}
```

## Constructor vs Setter Injection — when to use which

| Constructor Injection | Setter Injection |
|---|---|
| Dependency is **mandatory** | Dependency is **optional** |
| Dependency can be made **`final`** (immutable) | Dependency is **non-final** (can be changed later) |
| **Circular dependency NOT supported** — Spring throws `BeanCurrentlyInCreationException` | Circular dependency **is possible** (Spring resolves it, though it's still a design smell) |

> You *can* force a setter-injected dependency to also be treated as mandatory using **`@Required`** on the setter — Spring will throw an exception at startup if that property isn't set. (Note: `@Required` is **deprecated** since Spring 5.1 — in modern code, prefer constructor injection for anything mandatory instead.)

**Real-world guidance (used in almost every serious codebase today):** **Always prefer constructor injection.** It gives you:
- Immutability (`final` fields)
- Fail-fast — missing dependency = won't even compile / won't start
- Easy unit testing (`new MyService(mockDependency)`, no Spring needed)
- Works great with Lombok's `@RequiredArgsConstructor`

```java
@Service
@RequiredArgsConstructor              // Lombok generates constructor for all `final` fields
public class OrderService {
    private final PaymentGateway paymentGateway;
    private final InventoryClient inventoryClient;
    // No @Autowired needed above Spring 4.3+ if there's exactly ONE constructor —
    // Spring auto-detects and injects into it.
}
```

---

## Resolving ambiguity — multiple implementations of one interface

Say `Food` has 3 implementations: `Pedigree`, `Bread`, `Meat`. If `Dog` needs a `Food` via `@Autowired`, Spring doesn't know **which one** — this throws `NoUniqueBeanDefinitionException` at startup.

### `@Primary`

```java
@Component
@Primary                       // this bean wins by default when there's ambiguity
public class Pedigree implements Food { }

@Component
public class Bread implements Food { }
```

- Gives one bean **priority/default** among many candidates — like a `default:` case in a `switch`.
- Can only mark **one** bean `@Primary` per type — marking two is a config error.
- **Rule of thumb:** if you're not sure which implementation should be the sensible default, **don't** use `@Primary` — use `@Qualifier` explicitly everywhere instead, so it's unambiguous.

### `@Qualifier` — inject "by name"

Real-world scenario — a card payment system with 3 backends:

```
CardInfoController -> CardService -> CreditCardServiceImpl -> CreditCardDao   (qualifier: "cc")
                                   -> DebitCardServiceImpl  -> DebitCardDao    (qualifier: "dc")
                                   -> GreenCardServiceImpl  -> GreenCardDao    (qualifier: "gc")
```

```java
public interface CardService {
    void process(BigDecimal amount);
}

@Service("cc")                       // bean name = "cc"
public class CreditCardServiceImpl implements CardService { ... }

@Service("dc")
public class DebitCardServiceImpl implements CardService { ... }

@Service("gc")
public class GreenCardServiceImpl implements CardService { ... }

@RestController
public class CardInfoController {

    private final CardService creditCardService;
    private final CardService debitCardService;
    private final CardService greenCardService;

    public CardInfoController(
            @Qualifier("cc") CardService creditCardService,
            @Qualifier("dc") CardService debitCardService,
            @Qualifier("gc") CardService greenCardService) {
        this.creditCardService = creditCardService;
        this.debitCardService = debitCardService;
        this.greenCardService = greenCardService;
    }
}
```

**`@Primary` vs `@Qualifier` — interview one-liner:** *"`@Primary` sets a default winner when nothing else is specified; `@Qualifier` is explicit, wins over `@Primary` when both exist, and is the safer choice when there's genuinely no natural default."*

---

## Circular Dependency

```java
@Component
class A {
    private final B b;
    A(B b) { this.b = b; }
}

@Component
class B {
    private final A a;
    B(A a) { this.a = a; }
}
```

- With **constructor injection** → app **fails to start**:
  `BeanCurrentlyInCreationException: Error creating bean with name 'a': Requested bean is currently in creation: Is there an unresolvable circular reference?`
- With **field/setter injection** → Spring **can** resolve it, because it creates the raw object first (via no-arg constructor essentially), registers it as an "early reference" in a cache, then injects fields/setters afterward. This is technically legal but is treated as a **design smell** in real projects — it usually means two classes are too tightly coupled and should be refactored (e.g., extract a third service, or use `@Lazy` on one side, or use events).
- Spring Boot 2.6+ **disallows circular references by default** even for field/setter injection (`spring.main.allow-circular-references=false` is the new default) — you must explicitly opt back in, which is a strong signal that circular deps should be fixed, not worked around.

**Real fix patterns in production:**
1. Refactor — extract shared logic into a third class both depend on.
2. Use `@Lazy` on one of the constructor params — delays creation of that bean until first actual use, breaking the startup-time cycle.
3. Prefer setter injection *only* as a last resort for genuinely optional back-references.

---

## Field-level injection example (from your notes, kept + corrected)

```java
package com.st;

@Component
public class A {
    private B b;

    @Autowired
    public A(B b) { this.b = b; }     // constructor injection

    @Autowired
    public void setB(B b) { this.b = b; }   // setter injection (redundant here, shown for illustration)

    @Autowired
    private B b;                       // field injection (don't combine all 3 in real code — pick ONE style)
}

@Component
class B { }
```

> Note: this example combines all 3 injection styles just to show the syntax — **never mix multiple injection styles for the same field in real code.** Pick constructor injection and stay consistent.
