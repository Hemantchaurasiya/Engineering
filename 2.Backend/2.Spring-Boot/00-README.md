# Spring Boot — Interview Mastery Notes

Built from your personal Spring Boot notes: corrected, expanded with missing concepts and real-world code, and reorganized so it also works as long-term reference documentation. Goal: crack a Spring Boot interview at the 5-10 YOE / hard-round level.

Every file keeps your original notes as the base (lightly corrected where something was mistyped or slightly off), then adds what was missing — deeper "why," real production code, and a tricky Q&A section at the end. Nothing from your original notes was dropped.

## File index

| File | Covers |
|---|---|
| `01-intro-why-springboot.md` | J2EE → Spring → Boot chain, plain Spring MVC setup pain, drawbacks that led to Boot, Boot's features overview, DI recap |
| `02-starters-and-autoconfiguration.md` | Starter POMs, parent POM, `@SpringBootApplication` breakdown, `@ConditionalOnXxx` mechanics, `spring.factories` → `.imports` migration |
| `03-springapplication-run-internals.md` | Exact `SpringApplication.run()` sequence, `WebApplicationType` detection, the full application event lifecycle, runners |
| `04-embedded-server-and-jar-internals.md` | Embedded servers (Tomcat/Jetty/Undertow/Netty), jar vs war, fat-jar internals, `JarLauncher` mechanics |
| `05-properties-profiles-runners.md` | `@Value` vs `@ConfigurationProperties`, profile-based config, property precedence order, runners |
| `06-spring-mvc-in-boot.md` | DispatcherServlet request flow, plain MVC (`web.xml`) vs Boot MVC, `@Controller` vs `@RestController`, global exception handling |
| `07-actuator.md` | Actuator endpoints, enabled vs exposed, JMX vs HTTP exposure, production-safe config, custom health indicators |
| `08-database-integration-and-jpa.md` | `DataSource`/`JdbcTemplate`, multiple datasources, Spring Data JPA repository hierarchy, query derivation, N+1 problem |
| `09-master-tricky-qa-bank.md` | Cross-cutting senior-level questions: architecture judgment, startup debugging, thread-safety traps, testing strategy, Boot 2→3 migration |

## How to use this for interview prep

- Read files 01-08 in order once for the full mental model — each builds on the last (intro → autoconfig mechanics → startup internals → embedded server → config → MVC → actuator → data).
- Every file's own tricky Q&A section tests that file's specific topic.
- File 09 is the final gate — it deliberately mixes topics across files the way a real senior-round interviewer would, and includes the "have you kept up with recent versions" questions (Boot 3.x, Java 17+, native images) that separate strong candidates at the 5-10 YOE level.
- Revisit `02` and `03` last — auto-configuration mechanics and startup internals are the two topics interviewers dig into most when they want to gauge real depth vs surface-level Boot usage.
