<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · rest ➡](../phase-2-rest/NOTES.md)
<!-- /nav -->

# Phase 1 — Spring Boot Fundamentals: Notes

## 1. Inversion of Control (IoC) and the Spring container

**Definition:** Inversion of Control is a design principle where the *framework*, not your own code, is responsible for creating objects and wiring them together. Instead of a class reaching out and constructing what it needs (`new SomeDependency()`), it declares what it needs and something external — the **IoC container** — supplies it. That supplying mechanism is called **Dependency Injection (DI)**.

- **Control is inverted**: in plain Java, `App.main` decides *when* and *how* every object gets built (this is exactly what the Java capstone's `App` class did — it manually built a `JdbcExpenseRepository`, passed it into a service, and so on). With Spring, the container owns that responsibility; your classes only declare dependencies.
- **The container** is the runtime component that reads configuration (annotations in modern Spring), creates objects (called **beans**), resolves their dependencies, and manages their lifecycle from creation to destruction.
- **Dependency Injection** is the *pattern* that implements IoC. There are three common injection styles — constructor, setter, field — covered in depth in Phase 3.
- **Loose coupling** is the point of all this: a class depends on an abstraction (often an interface) and never instantiates its collaborators directly, so implementations can be swapped (a real repository in production, a mock in a test) without touching the class itself.

```java
// Without a container — manual wiring (what the Java capstone did)
ExpenseRepository repo = new JdbcExpenseRepository(dataSource);
ExpenseService service = new ExpenseService(repo);

// With Spring — declare the need, the container supplies it
@Service
public class ExpenseService {
    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {  // Spring calls this constructor
        this.repository = repository;                       // and passes in a managed bean
    }
}
```

In this example, the manual version explicitly constructs `JdbcExpenseRepository` and threads it through by hand — the caller controls creation. In the Spring version, `ExpenseService` only *declares* that it needs an `ExpenseRepository`; at startup the container finds (or creates) a bean that satisfies that type and passes it into the constructor. The class itself never calls `new` on its dependency. This is the same interface-based decoupling the Java capstone already used — Spring simply automates the wiring step.

**Why it's useful:** loose coupling makes components independently testable (inject a Mockito mock instead of a real database-backed implementation — see Phase 6), swappable (change a JDBC repository for a JPA one without touching the service), and easier to reason about (a class's constructor signature *is* its list of dependencies — no hidden `new` calls buried in method bodies).

**Summary:**
- IoC = the framework controls object creation/wiring, not your code.
- DI = the mechanism (constructor/setter/field) by which the container satisfies a class's declared dependencies.
- The payoff is loose coupling: swappable implementations and trivial unit testing.
- Spring's IoC container is the thing that creates, wires, and manages **beans** — the term for any object it manages.

## 2. Spring vs. Spring Boot

**Definition:** Spring (Spring Framework) is the core platform providing IoC/DI, Spring MVC, Spring Data, and dozens of other modules. Spring Boot is a layer *on top of* Spring that removes almost all manual configuration by providing sensible defaults, auto-configuration, and an embedded server, so you can go from zero to a running application in minutes.

- **Historical pain Boot solves**: pre-Boot Spring applications required hand-written XML or Java `@Configuration` classes wiring every `DataSource`, `EntityManagerFactory`, `DispatcherServlet`, view resolver, etc. Getting a simple REST endpoint running could take dozens of lines of setup.
- **Opinionated defaults**: Boot ships with pre-built configuration classes that activate automatically based on what's on the classpath, and can always be overridden if you define your own bean of the same type.
- **Embedded server**: Boot bundles Tomcat (or Jetty/Undertow) *inside* the application artifact — no separate application server to install or deploy a WAR to (Phase 1.3 below).
- **Starters**: curated, version-aligned dependency bundles (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`) that pull in everything a capability needs with one line in `pom.xml`.
- **Not a replacement**: Spring Boot doesn't replace Spring — it *is* Spring, configured for you. Every annotation you use (`@Service`, `@Autowired`, `@Transactional`) is still core Spring; Boot just removes the ceremony of wiring it all together.

**Why it's useful:** in an interview, the crisp way to say this is: *"Spring is the framework; Spring Boot is Spring plus auto-configuration, starters, and an embedded server, aimed at getting a production-ready app running with minimal manual setup."*

**Summary:**
- Spring = the core framework (IoC, MVC, Data, Security, ...).
- Spring Boot = Spring + auto-configuration + starters + embedded server + opinionated defaults.
- Boot doesn't hide Spring — it configures it for you and lets you override anything.

## 3. `@SpringBootApplication` and its three annotations

**Definition:** `@SpringBootApplication` is a single meta-annotation placed on the application's main class that turns on component scanning, auto-configuration, and marks the class itself as a configuration source. It is the entry point that makes an ordinary Java class into a bootable Spring Boot application.

```java
@SpringBootApplication
public class ExpenseApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseApiApplication.class, args);
    }
}
```

**Key concepts — what `@SpringBootApplication` bundles:**
- **`@SpringBootConfiguration`**: a specialization of `@Configuration`, meaning this class itself is allowed to declare `@Bean` methods and is a source of bean definitions (just like `DataSeeder` in this project, which is a plain `@Configuration` class with a `@Bean` method).
- **`@EnableAutoConfiguration`**: the actual "Boot magic." At startup, Spring scans a list of auto-configuration classes (each guarded with conditions like `@ConditionalOnClass` and `@ConditionalOnMissingBean`) and activates the ones whose conditions are satisfied by what's on the classpath. Because `expense-api`'s `pom.xml` includes `spring-boot-starter-web`, auto-configuration detects Spring MVC on the classpath and configures a `DispatcherServlet`, an embedded Tomcat, and Jackson for JSON. Because it includes `spring-boot-starter-data-jpa`, it configures a `DataSource`, an `EntityManagerFactory`, and a `PlatformTransactionManager` — all without a line of manual `@Bean` code.
- **`@ComponentScan`**: tells Spring to scan the package containing the annotated class (and all sub-packages) for stereotype-annotated classes (`@Component`, `@Service`, `@Repository`, `@RestController`, ...) and register them as beans. This is why `ExpenseApiApplication` sits in `dev.ajay.expenseapi` — every class in `dev.ajay.expenseapi.*` (controllers, services, repositories, security beans) gets picked up automatically.

**`SpringApplication.run(...)`** does the actual bootstrapping at runtime: it creates the `ApplicationContext` (the concrete IoC container), triggers component scanning and auto-configuration, refreshes the context (instantiating and wiring every bean), and — because the web starter is present — starts the embedded server, all before `main` returns.

**Why it's useful:** understanding what `@SpringBootApplication` unpacks to is a very common interview question because it demonstrates you know Boot isn't magic — it's three well-understood Spring mechanisms (config class, conditional auto-configuration, component scanning) combined behind one convenient annotation.

**Summary:**
- `@SpringBootApplication` = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`.
- Auto-configuration reacts to the classpath (and can always be overridden by defining your own bean).
- Component scanning only looks at the annotated class's package and below — package placement matters.
- `SpringApplication.run` is what actually creates the container and starts everything.

## 4. Starters and the parent POM (dependency management)

**Definition:** A **starter** is a single Maven/Gradle dependency that transitively pulls in a coherent, version-compatible set of libraries for one capability. The **parent POM** (`spring-boot-starter-parent`) is a Bill of Materials (BOM) that centrally pins the exact, tested version of every library Boot supports, so individual `<dependency>` declarations don't need `<version>` tags.

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <!-- no <version> needed — the parent BOM supplies one -->
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
</dependencies>
```

- **`spring-boot-starter-web`** — Spring MVC, embedded Tomcat, Jackson (JSON binding). Everything needed to write a REST controller.
- **`spring-boot-starter-data-jpa`** — Spring Data JPA, Hibernate, a connection-pool integration (HikariCP), transaction management.
- **`spring-boot-starter-security`** — the Spring Security filter chain, authentication/authorization infrastructure, `PasswordEncoder` implementations.
- **`spring-boot-starter-validation`** — Bean Validation (Jakarta Validation) + Hibernate Validator, the engine behind `@NotBlank`, `@Positive`, etc. (Phase 5).
- **`spring-boot-starter-actuator`** — production-ready operational endpoints (`/health`, `/metrics`) (Phase 8).
- **`spring-boot-starter-test`** — JUnit 5, AssertJ, Mockito, Spring Test/MockMvc, bundled as one test-scoped dependency (Phase 6).

**Why it's useful:** without a BOM, adding Spring MVC + Hibernate + Jackson + a JSON library + a logging framework each with independently chosen versions is a well-known source of runtime `NoSuchMethodError`s from version drift. The parent POM guarantees every dependency it manages is a version known to work together — you upgrade the whole stack by bumping one `<version>` on the parent.

**Summary:**
- A starter = one line in the POM, many aligned libraries pulled in transitively.
- The parent POM is a BOM: it pins compatible versions so you don't specify them yourself.
- Starters exist per capability (web, data-jpa, security, validation, actuator, test) and compose — add only what you need.

## 5. The embedded server

**Definition:** Spring Boot bundles a servlet container (Tomcat by default, swappable for Jetty or Undertow) directly inside the application's executable artifact, rather than requiring you to deploy a WAR file to an externally installed application server.

- **No external server install**: `spring-boot-starter-web` transitively includes `spring-boot-starter-tomcat`. There is nothing to install on the host beyond a JVM.
- **Self-contained executable JAR**: `mvn package` (via the `spring-boot-maven-plugin`, Phase 8.2) produces a single "fat JAR" containing your compiled classes, all dependencies, *and* the embedded Tomcat. Run it anywhere with `java -jar expense-api.jar`.
- **Identical artifact across environments**: the exact same JAR runs on a developer laptop, in CI, and in a container — there's no "the server config on prod is slightly different" class of bug, because the server ships with the app instead of being a separate piece of environment-specific infrastructure.
- **Configurable via `application.yml`**: `server.port: 8080` sets the listening port; there is no `server.xml` to edit.

```yaml
server:
  port: 8080
```

This single property (from `expense-api`'s `application.yml`) is all it takes to change the port the embedded Tomcat listens on — compare that to configuring a port in a standalone Tomcat's `server.xml`.

**Why it's useful:** this is exactly what makes Spring Boot apps trivial to containerize (the Docker track): `FROM eclipse-temurin:21-jre`, `COPY target/*.jar app.jar`, `ENTRYPOINT ["java","-jar","/app.jar"]` — three lines, because the JAR already *is* the whole running application, server included.

**Summary:**
- Tomcat (or Jetty/Undertow) is embedded in the JAR, not installed separately.
- `mvn package` → one executable fat JAR containing code + dependencies + server.
- Same artifact runs identically everywhere — the foundation of easy containerization.
- Port and server behavior are configured through `application.yml`, not server-specific XML.

## 6. Externalized configuration (`application.yml`)

**Definition:** Externalized configuration means environment-specific values (database URLs, credentials, ports, feature flags) live *outside* compiled Java code, in a config file (or environment variables, or command-line args), so the same built artifact can behave differently per environment without a rebuild.

```yaml
# application.yml (expense-api)
spring:
  application:
    name: expense-api
  datasource:
    url: jdbc:h2:mem:expenses;DB_CLOSE_DELAY=-1
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

server:
  port: 8080

app:
  jwt:
    secret: ${JWT_SECRET:change-me-to-a-long-random-secret-at-least-32-bytes!!}
    expiration-ms: 3600000
```

**Key concepts:**
- **YAML vs. `.properties`**: Boot supports both `application.yml` and `application.properties`; YAML is the idiomatic modern choice because nested keys (`spring.datasource.url`) read as an actual hierarchy instead of a flat dotted string.
- **Auto-configuration reads this file directly**: `spring.datasource.url` is picked up by the auto-configured `DataSource` bean; `spring.jpa.hibernate.ddl-auto` configures Hibernate's schema-generation behavior — no Java code touches these values.
- **Placeholder syntax with defaults**: `${JWT_SECRET:change-me-to-a-long-random-secret-at-least-32-bytes!!}` reads the `JWT_SECRET` environment variable if set, otherwise falls back to the literal default after the colon. This is how a secret is injected in production (via an env var) while local development still works out of the box.
- **Never hardcode environment values in Java**: swapping the H2 in-memory database for Postgres in production is a change to four YAML lines (`url`, `username`, `password`, driver), not a Java recompile — the same abstraction benefit as JDBC's driver-agnostic `Connection` interface.
- **Profiles** (`application-dev.yml`, `application-prod.yml`) extend this further, covered in Phase 3.3 and Phase 8.2.

**Why it's useful:** externalized config is what lets one built JAR be promoted through dev → staging → prod unchanged — only the configuration around it changes. It also keeps secrets out of source control (the JWT secret's default is an obviously-fake placeholder; the real value only ever exists as an environment variable injected at deploy time).

**Summary:**
- Config lives in `application.yml`, never hardcoded in Java classes.
- `${VAR:default}` pulls from environment variables with a fallback.
- Swapping infrastructure (DB engine, port, exposed actuator endpoints) is a config change, not a code change.
- This is the foundation profiles (Phase 3.3) build on for per-environment behavior.
