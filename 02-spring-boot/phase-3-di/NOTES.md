<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · rest](../phase-2-rest/NOTES.md) | [Phase 4 · data jpa ➡](../phase-4-data-jpa/NOTES.md)
<!-- /nav -->

# Phase 3 — Dependency Injection Deep Dive: Notes

## 1. Beans and stereotype annotations

**Definition:** A **bean** is any object whose lifecycle (creation, wiring, and destruction) is managed by the Spring IoC container, instead of by your own code calling `new`. **Stereotype annotations** are class-level annotations that mark a class as a bean and communicate its architectural role.

**Key concepts:**
- **`@Component`** — the generic, base stereotype. Any class annotated `@Component` (found by component scanning) becomes a bean.
- **`@Service`** — a specialization of `@Component` for the business-logic layer. Semantically identical to `@Component` at runtime (no extra behavior), but it documents intent and lets tools/humans identify the service layer at a glance. `expense-api`'s `ExpenseService` and `AuthService` are both `@Service`.
- **`@Repository`** — a specialization of `@Component` for the persistence layer. Unlike `@Service`, this one adds real behavior: Spring wraps repository beans with exception translation, converting low-level, technology-specific persistence exceptions (e.g., a raw JDBC `SQLException`) into Spring's unchecked `DataAccessException` hierarchy — a consistent, technology-agnostic set of exceptions regardless of whether you're using JDBC, JPA, or something else. Spring Data repository interfaces (`ExpenseRepository extends JpaRepository<...>`) get this automatically without you writing `@Repository` yourself.
- **`@Configuration`** — marks a class as a source of bean definitions via `@Bean` methods (see Section 4). `DataSeeder` in `expense-api` is a plain `@Configuration` class.
- **`@RestController` / `@Controller`** — the web layer stereotype (Phase 2.2).

```java
@Service
@Transactional
public class ExpenseService {
    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }
    // ...
}
```

`@ComponentScan` (enabled transitively by `@SpringBootApplication`, Phase 1.3) walks the package tree starting at `ExpenseApiApplication`'s package and finds every class carrying one of these annotations, registering each as a bean definition. **By default, every bean is a singleton** — exactly one shared instance exists per container, handed out to every class that depends on it.

**Why it's useful:** stereotypes are effectively free documentation. A new team member scanning package names and class annotations (`@Repository` in `repository/`, `@Service` in `service/`, `@RestController` in `web/`) can reconstruct the whole layered architecture without reading a single method body.

## 2. Injection styles: constructor, setter, field

**Definition:** Injection style is *how* the container delivers a bean's dependencies to it. Spring supports three mechanisms; constructor injection is the modern standard and the only style used throughout `expense-api`.

```java
// Constructor injection — used everywhere in this project
public class ExpenseController {
    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {   // Spring calls this
        this.service = service;
    }
}

// Field injection — AVOID (shown for contrast, not used in this project)
public class BadExample {
    @Autowired
    private ExpenseService service;   // hidden dependency, needs reflection to set in a test
}

// Setter injection — rarely preferred, but allows optional/late-bound dependencies
public class AnotherExample {
    private ExpenseService service;

    @Autowired
    public void setService(ExpenseService service) { this.service = service; }
}
```

**Key concepts / comparison:**

| Style | Dependencies `final`? | Testable without Spring? | Fails fast on missing dep? | Recommended? |
|---|---|---|---|---|
| Constructor | Yes | Yes — just `new X(mock)` | Yes — startup fails immediately | **Yes, always default to this** |
| Field (`@Autowired` on a field) | No | No — needs reflection or a Spring context | No — `NullPointerException` at first use | Avoid |
| Setter | No (unless defensively guarded) | Partially — can construct then call setter | No — object can exist half-wired | Rare, edge cases only |

- **Why constructor injection wins**: dependencies can be declared `final`, so the compiler guarantees they're set exactly once and never reassigned — the object is *always* in a fully valid state after construction, there's no window where it's "half-wired." It's trivially testable without any Spring machinery at all: `new ExpenseService(mockRepository)` works in a plain JUnit test (this is exactly what Phase 6's unit tests do). Circular dependencies (A needs B, B needs A) surface immediately at application startup as a clear error, rather than manifesting later as a subtle runtime bug. And since Spring 4.3, if a class has exactly **one** constructor, `@Autowired` is implicit — `ExpenseService`'s constructor above has no `@Autowired` annotation on it at all, and Spring still uses it.
- **Why field injection is discouraged**: the dependency is invisible from the outside — nothing in the class's public API (constructors, method signatures) reveals what it needs, so you can't `new` the class in a plain unit test; you'd need Spring's test support or reflection tricks (`ReflectionTestUtils`) to set the private field. It also permits an object to be constructed successfully and then blow up with a `NullPointerException` the first time the un-set field is used, if it's ever created outside a Spring context.
- **Setter injection** is occasionally justified for genuinely optional dependencies that may be reconfigured after construction, but it allows an object to exist in a partially-initialized state between construction and the setter call — a narrow niche compared to constructor injection's guarantees.

**Why it's useful:** "why is constructor injection preferred" is one of the single most common Spring interview questions — it directly tests whether you understand immutability, testability, and fail-fast design, not just Spring trivia.

## 3. `@Bean` methods vs. component scanning

**Definition:** `@Bean` is a method-level annotation, used inside a `@Configuration` class, that explicitly constructs and registers an object as a bean — an alternative to stereotype annotations for cases where you don't own the class being registered, or where construction needs custom logic.

```java
@Configuration
@Profile("!test")
public class DataSeeder {

    @Bean
    CommandLineRunner seed(ExpenseRepository repo, UserRepository users, PasswordEncoder encoder) {
        return args -> {
            if (!users.existsByUsername("demo")) {
                users.save(new AppUser("demo", encoder.encode("password123"), Role.USER));
            }
            if (repo.count() > 0) return;
            repo.save(new Expense("groceries", new BigDecimal("2400.50"), Category.FOOD, LocalDate.now()));
        };
    }
}
```

**Key concepts:**
- **When to use `@Bean` instead of a stereotype**: you can't annotate a class you don't own (a third-party library class has no `@Component` you can add), or the object needs construction logic beyond a bare constructor call (here, a `CommandLineRunner` lambda that seeds demo data, parameterized by three other injected beans). `SecurityConfig` (Phase 7) does the same thing for `PasswordEncoder` and `AuthenticationManager` — types Spring Security provides that `expense-api` configures rather than owns.
- **Method parameters are auto-wired**: `seed(ExpenseRepository repo, UserRepository users, PasswordEncoder encoder)` — Spring resolves each parameter from the container exactly as it would constructor parameters, and passes the results in when it calls this method to build the bean.
- **`CommandLineRunner`**: a functional interface Boot invokes with the app's command-line arguments once the application context is fully initialized and *right before* the app starts serving requests — the standard "run this once at startup" hook. `DataSeeder` uses it to populate a demo user and a few sample expenses so the API is immediately usable after `mvn spring-boot:run`.
- **`@Profile("!test")`**: this whole configuration class — and therefore the `CommandLineRunner` bean it defines — is excluded when the active Spring profile is `test`. That's why the test suite (which activates the `test` profile, see `SecurityJwtIntegrationTest`'s `@ActiveProfiles("test")`) starts from a clean, unseeded database.

**Why it's useful:** knowing when to reach for `@Bean` versus a stereotype annotation shows you understand the container isn't just "put an annotation on every class" — it's a general registry that can hold objects you construct with arbitrary logic, including ones from libraries you don't control.

## 4. Bean scopes

**Definition:** A bean's scope determines how many instances of it exist and how long each instance lives — whether the container hands out the same shared object every time, or a fresh one per injection/request.

| Scope | Instances | Typical use |
|---|---|---|
| `singleton` (default) | One per container | Stateless services, repositories, controllers — everything in `expense-api` |
| `prototype` | New instance every time it's injected/requested | Stateful, non-thread-safe helper objects |
| `request` (web) | One per HTTP request | Request-scoped data holders in web apps |
| `session` (web) | One per HTTP session | Session-scoped data in traditional session-based web apps |

- **Singleton is the default, and everything in `expense-api` uses it implicitly** — `ExpenseService`, `ExpenseController`, `JwtService`, and every repository interface are all singleton beans; the container creates exactly one instance of each and shares it across every concurrent request.
- **The direct implication**: because the same `ExpenseService` instance handles every request concurrently, it (and every other singleton bean) must be **stateless or thread-safe**. `ExpenseService` has exactly one field, `private final ExpenseRepository repository`, set once at construction and never mutated — it holds no per-request state, so sharing one instance across concurrent threads is entirely safe.
- **Prototype and web scopes exist for the opposite case** — a bean that genuinely needs to carry mutable, request-specific or use-specific state — but they're the exception, not the rule, in a typical layered REST application.

**Why it's useful:** understanding that "singleton by default" forces a statelessness discipline is what connects Spring's DI model to real production correctness — mutable instance fields on a `@Service` are a data-race bug waiting to happen precisely *because* only one instance exists for the whole application.

## 5. Resolving multiple candidates: `@Qualifier` and `@Primary`

**Definition:** When more than one bean of the same type exists, Spring cannot pick automatically by type alone and needs an explicit hint — `@Qualifier` names the specific bean to use, `@Primary` marks a default winner among candidates.

```java
public interface Notifier { void send(String msg); }

@Component @Primary
public class EmailNotifier implements Notifier { ... }

@Component
public class SmsNotifier implements Notifier { ... }

// Injects EmailNotifier — it's marked @Primary, so it wins ties by default.
public class AlertService {
    public AlertService(Notifier notifier) { ... }
}

// Injects the SMS one specifically, overriding the @Primary default.
public class UrgentAlertService {
    public UrgentAlertService(@Qualifier("smsNotifier") Notifier notifier) { ... }
}
```

- **Without disambiguation**: if two beans implement `Notifier` and a class asks for a plain `Notifier` with no qualifier and no `@Primary` winner, Spring throws `NoUniqueBeanDefinitionException` at startup — a fail-fast error, not a silent pick.
- **`@Primary`** designates one bean as the default choice whenever multiple candidates exist, without requiring every injection point to specify which one.
- **`@Qualifier("beanName")`** lets a specific injection point pick a *non-default* bean by name, overriding what `@Primary` would otherwise select.
- **Handling optional dependencies**: `@Autowired(required = false)`, wrapping the type in `Optional<T>`, or using Spring's `ObjectProvider<T>` all express "this dependency may legitimately be absent" without failing startup.

**Why it's useful:** this scenario comes up the moment a codebase has two implementations of the same interface (a real vs. a stub payment gateway, two notification channels) — knowing `@Qualifier`/`@Primary` exist (and the difference between them) is the difference between a clean fix and reaching for messy workarounds.

## 6. Externalized configuration: `@Value` and `@ConfigurationProperties`

**Definition:** Both annotations bind values from `application.yml`/environment/command-line into Java fields — `@Value` for a single property, `@ConfigurationProperties` for a structured group bound to a typed object.

```java
// @Value — one property, injected via constructor (expense-api's JwtService)
@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }
}
```

```yaml
# application.yml
app:
  jwt:
    secret: ${JWT_SECRET:change-me-to-a-long-random-secret-at-least-32-bytes!!}
    expiration-ms: 3600000
```

`JwtService`'s constructor is injected with two individual properties via `@Value`, read straight from the `app.jwt.*` keys in `application.yml` — this is the exact mechanism that gets the signing secret and token lifetime out of Java code and into externalized config (Phase 1.6).

**Key concepts:**
- **`@Value("${key}")`** injects exactly one property, and supports Spring Expression Language (SpEL) for more advanced cases (defaults, arithmetic). Best for one or two standalone values, as in `JwtService`.
- **`@ConfigurationProperties(prefix = "app")`** is the preferred approach once you have a *group* of related settings — it binds an entire nested prefix onto a typed class (record or POJO) in one shot, supports relaxed binding (`expiration-ms` in YAML maps to `expirationMs` in Java), and can be validated as a unit with `@Validated`. `expense-api` currently reads its two JWT settings with plain `@Value`, since there are only two — but as soon as a config group grows past a couple of properties, `@ConfigurationProperties` scales better and keeps related settings colocated in one class instead of scattered `@Value` fields across the codebase.
- **Comparison:**

| | `@Value` | `@ConfigurationProperties` |
|---|---|---|
| Scope | One property at a time | A whole group/prefix bound to a class |
| Type safety | Per-field, manual | Structural — one class models the whole group |
| SpEL support | Yes | No |
| Best for | One-off values | Cohesive config blocks (datasource, JWT, feature flags) |

## 7. Spring profiles and configuration precedence

**Definition:** A **profile** is a named label used to conditionally activate beans or configuration values for a particular environment (dev, test, prod), letting a single deployable artifact behave correctly everywhere without a rebuild.

```java
@Configuration
@Profile("!test")   // active in every profile EXCEPT "test"
public class DataSeeder {
    // ...seeds demo data...
}
```

```java
@SpringBootTest
@ActiveProfiles("test")   // activates the "test" profile for this test class
class SecurityJwtIntegrationTest { ... }
```

**Key concepts:**
- **`@Profile("expr")`** on a class or `@Bean` method controls whether that bean is registered at all, based on the currently active profile(s). `!test` means "active whenever `test` is *not* the active profile" — exactly the mechanism keeping the demo-data seeder out of the test run, so tests always start from an empty, predictable database.
- **Activating a profile**: `spring.profiles.active=test` (as a JVM arg, environment variable, or in `application.yml`), or `@ActiveProfiles("test")` directly on a test class (Spring Test's way of setting it for that test run only).
- **Profile-specific config files**: `application-dev.yml`, `application-prod.yml` layer on top of (and override) the base `application.yml` when that profile is active — the common pattern for per-environment datasource URLs, logging levels, or feature flags (expanded on in Phase 8.2).
- **Configuration precedence** (highest wins, roughly, from Spring Boot's documented order): command-line arguments > environment variables > profile-specific files (`application-{profile}.yml`) > the base `application.yml` > defaults hardcoded in `@Value`/`@ConfigurationProperties`. This layered override system is what lets the identical built JAR run correctly against an in-memory H2 database locally and a real Postgres instance in production — only the configuration surrounding it changes, never the code.

**Why it's useful:** profiles are the standard way real Spring Boot applications avoid `if (env.equals("prod"))` branches scattered through business logic — the *branching* happens declaratively, at the bean-definition level, driven entirely by configuration.

**Summary:**
- A bean is anything the container creates/wires; stereotypes (`@Component`, `@Service`, `@Repository`, `@Configuration`, `@RestController`) mark *what kind* of bean a class is.
- Constructor injection is the default choice: immutable dependencies, no Spring needed to unit test, fails fast on missing/circular dependencies.
- `@Bean` methods handle beans you don't own or that need custom construction logic; component scanning handles everything else.
- Beans are singletons by default — design them stateless, because one instance serves every concurrent request.
- `@Qualifier`/`@Primary` resolve ambiguity when multiple beans implement the same type.
- `@Value` for one property, `@ConfigurationProperties` for a structured group; profiles + config precedence let one artifact behave correctly across every environment.
