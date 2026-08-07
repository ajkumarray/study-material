<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · rest](../phase-2-rest/NOTES.md) | [Phase 4 · data jpa ➡](../phase-4-data-jpa/NOTES.md)
<!-- /nav -->

# Phase 3 — Dependency Injection Deep Dive: Notes

## 3.1 — Beans & stereotypes

A **bean** is an object the Spring container manages. Mark a class as a bean with a **stereotype annotation** (all are `@Component` specializations, differing by intent/semantics):
- **`@Component`** — generic managed component.
- **`@Service`** — business-logic layer (our `ExpenseService`).
- **`@Repository`** — persistence layer; also translates persistence exceptions into Spring's `DataAccessException` (the Java Phase 3.1 exception-translation idea, done for you). Spring Data repositories get this automatically.
- **`@Configuration`** — a class defining `@Bean` methods.
- **`@RestController`/`@Controller`** — web layer.

`@ComponentScan` (via `@SpringBootApplication`) discovers these in the package tree and registers them. Beans are **singletons by default** — one shared instance per container.

## 3.2 — Injection styles & scopes

**Constructor injection is the standard** (and what we use everywhere):
```java
private final ExpenseRepository repo;
public ExpenseService(ExpenseRepository repo) { this.repo = repo; }
```
Why over field/setter injection: dependencies are `final` (immutable, guaranteed set), the object is always fully valid, no reflection needed, circular dependencies surface at startup, and it's trivially unit-testable (just `new ExpenseService(mock)`). Since Spring 4.3, a single constructor needs no `@Autowired`. **Avoid field injection** (`@Autowired` on a field) — it hides dependencies and can't be tested without reflection.

**`@Bean` methods** in a `@Configuration` class create beans manually — for types you don't own (can't annotate) or that need construction logic (our `CommandLineRunner` seeder).

**Scopes:** `singleton` (default — one instance), `prototype` (new each injection), plus web scopes `request`/`session`. Default singleton means beans must be stateless/thread-safe (they're shared across all requests).

## 3.3 — Externalized configuration & profiles

- **`@Value("${server.port}")`** injects a single property.
- **`@ConfigurationProperties(prefix="app")`** binds a whole group of properties to a typed object — the preferred approach for structured config.
- **Profiles** (`@Profile("!test")`, `spring.profiles.active`) activate beans/config per environment (dev/test/prod). Our `DataSeeder` is `@Profile("!test")` so it doesn't pollute tests.
- **Config precedence:** command-line args > env vars > `application-{profile}.yml` > `application.yml` — later sources override earlier. This is how the same JAR runs differently in each environment without a rebuild.
