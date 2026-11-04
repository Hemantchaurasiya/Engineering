# SPRING CORE — Interview Mastery Notes (5-10 yrs level)

Base source = **Hemant's personal Spring Core notes** (kept as-is, nothing skipped) → corrected where wrong → expanded with missing concepts, real production code, and tricky interview questions.

## How this is organized

| File | Covers |
|---|---|
| `01-intro-ioc-di-fundamentals.md` | Why Spring came, J2EE drawbacks, SRP, dependent vs dependency, IOC principle, what happens internally when Container is created, BeanFactory vs ApplicationContext |
| `02-configuration-styles.md` | XML config, Java config, @Component + @ComponentScan + @Autowired, drawbacks of XML |
| `03-dependency-injection-types.md` | Constructor / Setter / Field injection, @Primary, @Qualifier, @Required, cyclic dependency |
| `04-bean-scopes.md` | Singleton, Prototype, Request/Session/Application, scope combination cases, @Lazy |
| `05-properties-profiles-externalized-config.md` | @PropertySource, Environment, @Value, @Profile, real CI/CD environment flow |
| `06-annotations-reference-cheatsheet.md` | Every Spring Core annotation, one-line meaning + gotcha |
| `07-tricky-interview-qa-master-bank.md` | 60+ tricky Q&A, the kind asked at 5-10 yr level |
| `08-real-world-production-example.md` | One realistic e-commerce **Payment module** wiring together every concept above (not toy Dog/Pedigree examples) |

## Original notes correction log (things fixed from your raw notes)

- **Rod Johnson** (not "Rod Johnsen") wrote the book *"Expert One-on-One J2EE Design and Development"* in 2002 — that's where Spring's ideas came from (project was first called **interface21**, later renamed Spring). He was **not from Sun Microsystems** — he was an independent consultant/developer frustrated with the complexity of EJB-based J2EE development.
- "Spring core Scope" for Singleton/Prototype is right, but **Request, Session, Application, WebSocket** scopes are **web-aware scopes** — they only work inside a `WebApplicationContext` (i.e., a web app), not in a plain Container.
- IOC Container is **not literally a Java `HashMap`** — that's a simplification to understand it. Internally, Spring uses a `BeanFactory` implementation (like `DefaultListableBeanFactory`) which holds `BeanDefinition` objects in a registry (a map-like structure of bean name → `BeanDefinition` metadata), and actual **singleton bean instances** in a separate cache (`singletonObjects` map). So "Container = HashMap-like registry" is a fair mental model, just not literally `java.util.HashMap`.
- `new XMLBeanFactory(...)` is a **legacy/deprecated** class (Spring 1.x style). In real projects (and interviews) you talk about `ApplicationContext` implementations like `ClassPathXmlApplicationContext`, `AnnotationConfigApplicationContext`.

## Goal
Crack any Spring Core interview at 5–10 yrs experience level, including trick questions on IOC internals, bean lifecycle, scope misuse, circular dependency, and real-world design decisions — not just definitions.
