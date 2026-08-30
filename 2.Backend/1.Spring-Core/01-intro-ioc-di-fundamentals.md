# 01 — Introduction, IOC & DI Fundamentals

## Introduction (from your notes)

(i) Main goal of Spring framework → make **J2EE application development easier**.

**J2EE :-** Servlet, JSP, RMI, EJB, etc.

### Drawbacks of J2EE without Spring

1. **Tightly coupled** — Application classes had to extend Servlet, EJB base classes, etc.
2. **Heavy weight** — Application startup takes more processing (EJB containers were heavy).
3. **Boilerplate code** — Same code repeated in multiple places to do some activity (e.g. JDBC connection open/close, transaction handling).
4. **Cross cutting concerns** — J2EE has no inbuilt support (logging, security, transactions) — you had to implement manually everywhere.

> **Corrected note:** Rod Johnson (an independent developer/consultant, **not from Sun Microsystems**) was disappointed with the complexity of EJB-heavy J2EE. In 2002 he wrote the book *"Expert One-on-One J2EE Design and Development"* along with ~30,000 lines of code that later became the seed of the framework. That codebase was named **interface21**, later renamed **Spring** (2003/2004 first release) — meaning "a fresh start after the J2EE winter".

## Spring Advantages

(i) Makes application development easier
(ii) Light weight
(iii) Modularity — pick only what you need:
- Spring Core
- Spring MVC
- Spring Batch
- Spring WebMVC
- Spring DAO
- Spring AOP
- Spring Boot
- Spring Transaction
- Spring Security

(iv) **POJO development** — Spring doesn't force your classes to extend/implement any Spring framework class.

(v) **Popular because** — loosely coupled + unit-testable code.

### Why does Spring stay relevant in market?

Because Spring gives a **consistent programming/abstraction API to talk to different technologies** — you write similar-style code regardless of backend.

- `RedisTemplate` → Redis Cache
- `MongoTemplate` → MongoDB
- `KafkaTemplate` → Kafka
- `JdbcTemplate` → any RDBMS

This is the real reason it survived 20+ years — new tech (Kafka, Reactive, Cloud) keeps getting a Spring-style wrapper, so developers don't need to relearn a new mental model each time.

---

## Spring Core — the base module

Spring Core is the **base module** all other modules (MVC, AOP, Boot, Security...) sit on top of. Its main job: **manage object creation and object dependencies.**

```java
public class Account {
    // state      -- instance variables
    // behaviour  -- methods
}
```

## SOLID — starting point: SRP

**SRP — Single Responsibility Principle**: every class should have **one reason to change** (one responsibility).

- ❌ class `User` → holds both user details + account details
- ✅ class `User` → only user details; separate class `Account` → only account details

**Why this matters for DI:** once you split responsibilities across many small classes, those classes need to *collaborate* — object A needs object B to do its job. That collaboration is exactly the problem IOC/DI solves.

```java
class A {
    B b;              // A depends on B
    void m() {
        b.amount();
    }
}
class B {
    void amount() { }
}
```

So:
- **Class A** → dependent / source class (the one that needs help)
- **Class B** → dependency / target class (the one that provides help)

## Dependent vs Dependency

- **dependent** → an object that is *depending on* another object to get some info/work done.
- **dependency** → the object *required by* another object to carry out its functionality.

### Example — the tightly-coupled version (why plain Java hurts)

```java
public class Pedigree {
    public void eat() { System.out.println("eating pedigree"); }
}

public class Dog {
    private Pedigree p;
    public Dog() {
        this.p = new Pedigree();   // <-- Dog is HARD-WIRED to Pedigree
    }
    public void eat() { this.p.eat(); }
}

public class DogOwner {
    Dog d = new Dog();
}
```

`DogOwner --dependent-->  Dog --dependent-->  Pedigree (dependency)`

**Problems:**
1. **Tightly coupled** — Dog can only ever eat Pedigree; want it to eat Bread? Change the `Dog` class.
2. **Not unit-testable** — you can't substitute a fake/mock `Pedigree` while testing `Dog`, because `new Pedigree()` is baked inside the constructor.

### Fix attempt 1 — program to an interface

```java
public interface Food {
    void eat();
}
public class Pedigree implements Food { public void eat() { } }
public class Bread    implements Food { public void eat() { } }
public class Meat     implements Food { public void eat() { } }

public class Dog {
    private Food f;
    public Dog(Food f) {          // still creating it wrong below, see DogOwner
        this.f = f;
    }
    public void eat() { this.f.eat(); }
}

public class DogOwner {
    Food f = new Bread();
    Dog dog = new Dog(f);          // DogOwner is now RESPONSIBLE for creating Food
}
```

**Advantage:** loosely coupled to the concrete Food type, unit-testable (`Dog` doesn't care what `Food` is).
**Disadvantage:** `DogOwner` now knows internal wiring detail — which `Food` to `new` up and pass — that's **not `DogOwner`'s job**. Every class ends up doing manual object creation & wiring, which doesn't scale once you have 100s of classes.

### Fix — Dependency Injection

```java
public class DogOwner {
    private Dog dog;
    public DogOwner(Dog dog) {   // dog is INJECTED from outside, DogOwner never calls `new Dog()`
        this.dog = dog;
    }
}
```

Now **nobody manually calls `new`** — the **Spring IOC Container** creates `Pedigree`, `Dog`, `DogOwner` and wires them together, based on configuration.

**Advantages of DI:**
1. Loosely coupled
2. Unit-testable (inject a mock in tests)
3. No broken encapsulation — dependent doesn't need to know internal creation logic of dependency

---

## IOC — Inversion of Control

**Definition:** *Collaboration of objects and managing the lifecycle of objects* is called IOC.

- **Collaboration** → managing dependencies (who needs whom)
- **Lifecycle** → object instantiation and destruction

Traditionally, **your code** controls object creation (`new SomeClass()`). With Spring, **control is inverted** — the **Container** creates objects and hands them to you. That inversion (from "you control creation" to "framework controls creation") is why it's called *Inversion* of Control.

### Two ways to get dependencies

1. **Dependency Lookup** — developer manually writes code to go to the Container and fetch (`getBean(...)`) the object. You are still asking for it explicitly.
2. **Dependency Injection** — the Container automatically supplies (pushes) the dependency into your object — you never call `getBean()` yourself for it.

> **DI is one specific technique/implementation of the broader IOC principle.** IOC is the concept ("someone else controls object creation & wiring"); DI is *how* Spring achieves it.

---

## To implement Spring Core — 3 steps

1. **Configuration** — tell Spring: what objects to create, how to create them, what to inject.
2. **Dependency Injector** — reads/parses that configuration.
3. **Use the beans** — fetch beans from Container and use them wherever required.

### What happens when we create the Container? (internal flow)

```java
ApplicationContext context =
    new ClassPathXmlApplicationContext("beans.xml");
```

1. Spring reads the XML/Java config file and **validates** it. Invalid config → exception at startup (fail fast).
2. Spring creates an **in-memory logical space inside the JVM** — this space is the **IOC Container**.
3. It loads the bean **configuration metadata** (not the actual objects yet!) into that space — i.e. it registers `BeanDefinition` objects (id, class name, scope, dependencies, init/destroy methods, etc.) keyed by bean name.
4. Returns a reference to this Container as an `ApplicationContext` (or `BeanFactory`).
5. When you call:
   ```java
   DogOwner dogowner = (DogOwner) context.getBean("dogowner");
   ```
   - Container looks up the `BeanDefinition` for id `"dogowner"`.
   - If **not found** → throws `NoSuchBeanDefinitionException` and the lookup fails.
   - If found → Spring uses **Java Reflection** to load the class into the JVM and instantiate the object (and resolve/inject its dependencies first, recursively, before handing it back).

> **Corrected note:** for **Singleton**-scoped beans, `ApplicationContext` (unlike plain `BeanFactory`) **eagerly** creates all singleton beans **at Container startup**, not lazily on first `getBean()` call — this is a very common interview trip-up (see tricky Q&A file).

### Is the Container software or hardware?

Neither — it's **just an in-memory data structure/registry living inside your JVM's memory** (conceptually like a `Map<beanName, BeanDefinition>` for metadata + a separate cache of already-created singleton instances). Spring uses **Java Reflection** internally to instantiate classes and inject dependencies.

```
Objects live in:            JVM heap
Container holds:            references + metadata (bean id -> class, scope, dependencies)
```

---

## Types of Spring Container

| | BeanFactory | ApplicationContext |
|---|---|---|
| What | Root/base interface of Spring IOC container | Sub-interface of `BeanFactory` — richer, "enterprise" container |
| Bean creation | **Lazy** — singleton created only on first `getBean()` call | **Eager** — all singletons pre-created at startup (fail-fast on config errors) |
| Use case | Lightweight / resource constrained / standalone apps only | Standalone apps, **web apps**, **AOP**, **event publishing** (`ApplicationEvent`), internationalization (i18n), annotation config support |
| Real usage | Rarely used directly in real projects today | **This is what you use in real Spring/Spring Boot apps** |

Common `ApplicationContext` implementations:
- `ClassPathXmlApplicationContext` — loads XML config from classpath
- `FileSystemXmlApplicationContext` — loads XML config from file system path
- `AnnotationConfigApplicationContext` — loads Java `@Configuration` classes
- `AnnotationConfigWebApplicationContext` / Spring Boot's embedded context — for web apps

**Interview one-liner:** *"BeanFactory is lazy, ApplicationContext is eager (for singletons) and adds web/AOP/eventing support — in real projects we always use ApplicationContext (Spring Boot wires one for you automatically)."*
