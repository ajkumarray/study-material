<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · rest ➡](../phase-2-rest/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals: Interview Q&A

⭐ = asked constantly.

**Q: What is Inversion of Control and Dependency Injection?** ⭐⭐
IoC: the framework, not your code, controls object creation and lifecycle. DI is the mechanism — dependencies are *injected* (via constructor/setter/field) rather than created with `new`. Result: loose coupling, swappable implementations, easy testing (inject mocks). Spring's container is an IoC container.

**Q: Spring vs Spring Boot?** ⭐
Spring is the core framework (IoC, MVC, Data, etc.) but historically needed heavy configuration. Spring Boot layers on **auto-configuration**, **starters**, an **embedded server**, and sensible defaults so you build production apps with almost no boilerplate. Boot is Spring made turnkey.

**Q: What does `@SpringBootApplication` do?** ⭐
It combines `@SpringBootConfiguration` (config class), `@EnableAutoConfiguration` (configure beans based on the classpath), and `@ComponentScan` (find your beans in this package tree). One annotation bootstraps the whole app.

**Q: What is auto-configuration and how does it work?** ⭐⭐
Boot ships `@Conditional` configuration classes that activate based on what's on the classpath and what beans already exist (`@ConditionalOnClass`, `@ConditionalOnMissingBean`). Add the JPA starter → a `DataSource`, `EntityManagerFactory`, and transaction manager get configured automatically — unless you define your own, which wins. Debug with `--debug` to see the "conditions evaluation report."

**Q: What is a Spring starter?**
A curated dependency descriptor that pulls a coherent set of libraries for a capability (`starter-web`, `starter-data-jpa`, `starter-security`). It removes version-juggling — the parent BOM pins compatible versions.

**Q: Where does the embedded server come from, and why is it good?**
The web starter bundles embedded Tomcat (swappable for Jetty/Undertow). The app becomes a self-contained executable JAR you run with `java -jar` — no external server install, identical artifact in every environment, trivial to containerize (Docker track).

**Q: How does Spring Boot handle configuration?**
Externalized config from `application.yml/properties`, environment variables, command-line args, and profiles, merged by a defined precedence order. Inject values with `@Value` or bind groups with `@ConfigurationProperties` (Phase 3.3).
