<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · rest ➡](../phase-2-rest/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals: Interview Q&A

⭐ = asked constantly.

**Q: What is Inversion of Control and Dependency Injection?** ⭐⭐
IoC is a design principle: object creation and wiring are controlled by an external container, not by the code that uses the objects. Traditionally, a class that needs a collaborator calls `new` on it directly — that class controls both *what* implementation it gets and *when* it's created. IoC inverts that: the class only declares what it needs (typically via a constructor parameter), and a container decides what concrete instance to hand it and when. Dependency Injection is the specific mechanism that implements IoC — the container "injects" the dependency into the class, most commonly through the constructor.

The benefit is loose coupling. A class that depends on an interface (`ExpenseRepository`) rather than constructing a concrete class itself can be tested with a mock implementation, or have its real implementation swapped (JDBC today, JPA tomorrow) without any change to the dependent class's own code. Spring's `ApplicationContext` is the concrete IoC container: it scans for beans, resolves each bean's dependencies, and wires the object graph together at startup.

*Follow-up: What's the difference between IoC and DI specifically, if they're so related?* IoC is the broader principle (control of flow/object creation is inverted). DI is one specific technique that achieves it. You could technically achieve IoC through other patterns (e.g., the Service Locator pattern, where a class actively asks a registry for a dependency) — DI is considered the cleaner implementation because dependencies are explicit in the constructor signature rather than hidden behind a lookup call.

**Q: Spring vs Spring Boot?** ⭐
Spring (the Spring Framework) is the core platform — IoC container, Spring MVC, Spring Data, Spring Security, transaction management, and so on. Historically, using it required substantial manual configuration: XML bean definitions or verbose `@Configuration` classes wiring a `DataSource`, an `EntityManagerFactory`, a `DispatcherServlet`, view resolvers, etc., by hand.

Spring Boot sits on top of Spring and removes that ceremony. It adds **auto-configuration** (sensible beans configured automatically based on what's on the classpath), **starters** (curated, version-aligned dependency bundles), an **embedded server** (no separate Tomcat install/WAR deploy), and opinionated defaults for nearly everything. Boot doesn't replace any Spring concept — every annotation you write (`@Service`, `@Transactional`, `@Autowired`) is still core Spring. Boot is "Spring, pre-configured for the common case, with escape hatches to override anything."

*Follow-up: If Boot auto-configures a bean for you, how do you override it?* Define your own bean of that type in a `@Configuration` class (or via a stereotype annotation). Boot's auto-configuration classes are guarded with `@ConditionalOnMissingBean`, so your explicit bean wins and the auto-configured default backs off.

**Q: What does `@SpringBootApplication` do?** ⭐
It's a meta-annotation combining three things. `@SpringBootConfiguration` marks the class as a source of bean definitions (a specialization of `@Configuration` — it can itself hold `@Bean` methods). `@EnableAutoConfiguration` is the actual "Boot magic": it activates a large set of conditional configuration classes based on what's present on the classpath — add `spring-boot-starter-web` and you get an auto-configured `DispatcherServlet` plus embedded Tomcat plus Jackson; add `spring-boot-starter-data-jpa` and you get an auto-configured `DataSource`, `EntityManagerFactory`, and transaction manager. `@ComponentScan` tells Spring to scan the annotated class's package (and everything below it) for `@Component`/`@Service`/`@Repository`/`@RestController` classes and register them as beans.

`SpringApplication.run(ExpenseApiApplication.class, args)` is the call that actually does the work at runtime: it builds the `ApplicationContext`, triggers scanning and auto-configuration, instantiates and wires every bean, and (because the web starter is on the classpath) starts the embedded server — all before `main` returns.

*Follow-up: Why does package placement of the main class matter?* Because `@ComponentScan` defaults to scanning the main class's package and sub-packages only. If a `@Service` class lives in a sibling package outside that tree, it silently won't be picked up, and you'd get a `NoSuchBeanDefinitionException` at injection time. Convention is to put the `@SpringBootApplication` class at the root package (e.g. `dev.ajay.expenseapi`) so everything underneath is covered.

**Q: What is auto-configuration and how does it actually decide what to configure?** ⭐⭐
Boot ships hundreds of `@Configuration` classes, each guarded by conditional annotations: `@ConditionalOnClass` (activate only if a given class is present on the classpath — proof of a dependency being included), `@ConditionalOnMissingBean` (activate only if the user hasn't already defined their own bean of that type — so user config always wins), `@ConditionalOnProperty` (activate based on a config value), and others. At startup, Spring evaluates every candidate auto-configuration class against these conditions and only activates the ones that match.

Concretely: `expense-api` includes `spring-boot-starter-data-jpa`, so Hibernate and a JPA `EntityManagerFactory` classes are on the classpath; the JPA auto-configuration's `@ConditionalOnClass` conditions pass, so it configures a `DataSource` (reading `spring.datasource.*` from `application.yml`), an `EntityManagerFactory`, and a `PlatformTransactionManager` — all without a line of manual `@Bean` code in the project.

*Follow-up: How do you debug what auto-configuration actually activated?* Run with `--debug` (or set `debug: true` in config) to print the "auto-configuration report" at startup, which lists every candidate class and whether it was applied (with the reason) or excluded (with the failing condition). This is the practical way to answer "why isn't my bean configured the way I expect."

**Q: What is a Spring starter, and what does the parent POM do?**
A starter is a single dependency that transitively pulls in a coherent, tested set of libraries for a capability — `spring-boot-starter-web` brings Spring MVC, embedded Tomcat, and Jackson together; `spring-boot-starter-data-jpa` brings Spring Data JPA, Hibernate, and a connection pool. It removes the need to hand-pick individually compatible versions of a dozen libraries.

The **parent POM** (`spring-boot-starter-parent`) is a Bill of Materials that centrally pins the exact tested version of every library Boot supports via `dependencyManagement`. Because of that, individual `<dependency>` entries in a Boot project's `pom.xml` don't need a `<version>` — the parent supplies one, and upgrading the whole stack is a single version bump on the parent rather than editing dozens of dependency versions individually.

**Q: Where does the embedded server come from, and why is that a good design?**
`spring-boot-starter-web` transitively includes `spring-boot-starter-tomcat`, which embeds Tomcat *inside* the built artifact rather than requiring a separately installed application server that you deploy a WAR to. `mvn package` (via the `spring-boot-maven-plugin`) produces a self-contained executable "fat JAR" holding your compiled code, all dependencies, and the embedded server. You run it with `java -jar app.jar` on any machine with a JVM — no Tomcat installation step, and (crucially) the *exact same artifact* runs in dev, CI, and production, eliminating an entire class of "works on my machine, server config differs in prod" bugs. It's also what makes containerizing a Boot app trivial: the Dockerfile just copies the JAR and runs it.

*Follow-up: Can you swap Tomcat for another server?* Yes — exclude `spring-boot-starter-tomcat` from the web starter and add `spring-boot-starter-jetty` or `spring-boot-starter-undertow` instead; auto-configuration adapts automatically since it only activates the embedded-server config matching what's on the classpath.

**Q: How does Spring Boot handle configuration, and how would you inject a secret like a JWT signing key?**
Configuration is externalized into `application.yml`/`.properties`, environment variables, command-line arguments, and per-environment profile files, merged by a defined precedence order (command-line args and env vars override file-based config). This is what lets one built artifact behave correctly across dev/staging/prod without recompiling.

For a secret, the idiomatic pattern (used in `expense-api`'s config for the JWT signing key) is a placeholder with a fallback: `app.jwt.secret: ${JWT_SECRET:change-me-to-a-long-random-secret-at-least-32-bytes!!}`. At runtime, Spring resolves `${JWT_SECRET}` from the environment if it's set; otherwise it uses the literal default after the colon (obviously unsafe for real use, but convenient for local dev). In production you'd set the `JWT_SECRET` environment variable (or pull it from a secrets manager injected as an env var) and never commit the real value to source control. Values get bound into Java either with `@Value("${app.jwt.secret}")` on a single field/constructor parameter, or with `@ConfigurationProperties` for a whole group (Phase 3.3).

*Follow-up: What would happen if `JWT_SECRET` were shorter than 32 bytes in production, given HS256 signing?* `Keys.hmacShaKeyFor(secret.getBytes())` (used in `JwtService`) requires a key of at least 256 bits for HS256; passing a shorter secret throws a `WeakKeyException` at startup — a fail-fast safety net against an accidentally weak signing key.
