<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · rest ➡](../phase-2-rest/NOTES.md)
<!-- /nav -->

# Phase 1 — Spring Boot Fundamentals: Notes

## 1.1 — Spring & the IoC container

**Spring** is a framework built around **Inversion of Control (IoC)**: instead of your code creating and wiring its dependencies (`new JdbcExpenseRepository(ds)` as the Java capstone's `App` did), a **container** creates objects (**beans**), wires them together, and hands them to you. **Dependency Injection (DI)** is how that wiring is delivered.

Why it matters: your classes declare *what* they need (a constructor parameter) and the container supplies it — so components are decoupled, swappable, and testable (pass a mock in tests). The capstone already did this by hand via the repository *interface*; Spring industrializes it.

**Spring Boot** = Spring + opinionated defaults + embedded server + no XML. It removes the historical Spring ceremony (mountains of config) so you start productive in minutes.

## 1.2 — @SpringBootApplication, starters, auto-configuration

**`@SpringBootApplication`** bundles three annotations:
- `@SpringBootConfiguration` — this class contributes configuration/beans.
- `@EnableAutoConfiguration` — **the "Boot" magic**: Spring inspects the classpath and configures sensible defaults. `spring-boot-starter-web` present → configure Spring MVC + an embedded Tomcat + Jackson. `data-jpa` present → configure Hibernate + a `DataSource`. You get a working stack with zero manual setup.
- `@ComponentScan` — discover beans (`@Component`, `@Service`, `@RestController`, ...) in this package and below.

**Starters** are curated dependency bundles. One line (`spring-boot-starter-web`) pulls a coherent, version-aligned set transitively. The **parent POM** (`spring-boot-starter-parent`) is a BOM pinning hundreds of compatible versions, so your `<dependency>` entries need no `<version>` — Maven's `dependencyManagement` (Java Phase 6.1) at scale.

## 1.3 — The embedded server & config

Spring Boot embeds the web server *inside* the app (no external Tomcat to install/deploy a WAR to). `SpringApplication.run(...)` boots the container, runs auto-configuration, and starts the server — the app is a self-contained executable JAR (Phase 8).

**Externalized config** lives in `application.yml`/`.properties`: datasource URL, port, JPA settings, logging — never hardcoded in Java, overridable per environment (Phase 3.3 profiles). Our config points at in-memory H2; swapping to Postgres is four lines, no Java change (the driver abstraction from Java Phase 7).
