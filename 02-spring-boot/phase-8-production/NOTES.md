<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · security](../phase-7-security/NOTES.md)
<!-- /nav -->

# Phase 8 — Production Concerns: Notes

## 1. Spring Boot Actuator

**Definition:** `spring-boot-starter-actuator` is a starter that exposes a set of production-ready HTTP endpoints for monitoring and managing a running application — health status, build metadata, runtime metrics, live configuration — without writing any of that infrastructure by hand.

```yaml
# expense-api's application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Key concepts — the endpoints:**
- **`/actuator/health`** — a liveness/readiness signal: is the app (and its critical dependencies, like the database connection) actually working? This is the exact endpoint a load balancer or a Kubernetes readiness/liveness probe polls to decide whether to route traffic to an instance, or restart it if it's stuck. `expense-api`'s own `SecurityConfig` explicitly `permitAll()`s `/actuator/health`, because a health-check probe has no way to present a JWT — it needs to be reachable with no authentication at all.
- **`/actuator/info`** — arbitrary build/app metadata you choose to expose (version, git commit, build time) — useful for confirming exactly what's deployed without SSHing into a box.
- **`/actuator/metrics`** — a namespace of numeric measurements: JVM memory/GC stats, HTTP request counts and latencies per endpoint, datasource connection-pool stats, and more, all collected via **Micrometer** (Spring Boot's metrics facade) and exportable to systems like Prometheus for dashboards/alerting (Grafana).
- **Other endpoints** exist but aren't enabled by `expense-api`'s current config: `/env` (resolved configuration properties — useful for debugging, but sensitive, since it can reveal secrets), `/loggers` (view and *change* log levels at runtime without a restart — genuinely useful for live-debugging a production incident), `/mappings` (every registered `@RequestMapping`), `/beans` (every bean in the context).
- **`management.endpoints.web.exposure.include`** is the explicit allowlist controlling which actuator endpoints are reachable over HTTP at all — by default, Boot only exposes `health` unless you opt more in, precisely because several actuator endpoints (`/env`, `/beans`, `/mappings`) can leak sensitive internal details and should never be blindly exposed to the public internet. `expense-api` opts into exactly three: `health`, `info`, `metrics`.

**Why it's useful:** Actuator is effectively the "ops interface" of the application — instead of every team inventing its own bespoke health-check endpoint and metrics format, Actuator gives a consistent, well-known surface that monitoring tooling (Kubernetes, Prometheus, load balancers) already knows how to consume out of the box.

## 2. Profiles and per-environment configuration (recap and extension)

**Definition:** The same mechanism introduced in Phase 3.3 — Spring profiles — is what makes one built artifact behave correctly across dev, test, and production without a rebuild; Phase 8 is where that mechanism gets applied specifically to production-readiness concerns like secrets and packaging.

- **`application-{profile}.yml`** files (e.g., `application-dev.yml`, `application-prod.yml`) layer on top of the base `application.yml` when that profile is active via `spring.profiles.active` — typically varying datasource URLs, logging verbosity, or actuator exposure per environment.
- **Secrets never live in a config file committed to source control.** `expense-api`'s `app.jwt.secret: ${JWT_SECRET:change-me-to-a-long-random-secret-at-least-32-bytes!!}` is the concrete pattern: the real value is injected as an environment variable (or pulled from a secrets manager that ultimately sets that same environment variable) at deploy time, while the fallback after the colon is an obviously-fake local-development-only default.
- **Configuration precedence** (Phase 3.3) — command-line args > environment variables > profile-specific files > the base `application.yml` — is exactly what allows an environment variable set by a deployment platform to override whatever's baked into the packaged JAR's config files, without touching the artifact itself.

## 3. Packaging: the executable fat JAR

**Definition:** `mvn package`, powered by the `spring-boot-maven-plugin` declared in `pom.xml`, produces a single, self-contained, executable "fat JAR" — application code, every dependency, and the embedded server, all bundled into one file runnable with `java -jar`.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

**Key concepts:**
- **Fat/uber JAR**: an ordinary `mvn package` without the Boot plugin would produce a "thin" JAR containing only your own compiled classes, relying on a separately assembled classpath to run — not directly executable on its own. The Boot plugin repackages that thin JAR into a self-contained one that nests all of its dependency JARs internally and knows how to load them via a custom class loader, so `java -jar expense-api.jar` runs the application with nothing else needed on the target machine except a JVM.
- **Same artifact everywhere**: this identical file runs locally, in a CI pipeline, and in production — there's no separate "build for prod" step producing a differently-assembled artifact, eliminating an entire class of environment-drift bugs.
- **The foundation for containerization**: because the built JAR already *is* the complete runnable application (code + dependencies + embedded server — Phase 1.5), a Dockerfile for it is close to the simplest possible shape: `FROM eclipse-temurin:21-jre`, `COPY target/expense-api-*.jar app.jar`, `ENTRYPOINT ["java","-jar","/app.jar"]`. Boot also supports `mvn spring-boot:build-image` (Cloud Native Buildpacks), which produces an optimized, layered container image directly from the Maven build without a hand-written Dockerfile at all — covered in depth in the Docker track.
- **`mvn spring-boot:run`** (also enabled by the same plugin) runs the app directly from source during development, without needing to package a JAR first — the everyday inner-loop command, distinct from the `package` goal that produces the deployable artifact.

**Why it's useful:** "how would you containerize this" is a natural follow-up to any Spring Boot discussion, and the strong answer is specifically that the *hard part is already done* by the fat-JAR packaging — Docker's job is just to provide a JVM and copy one file in.

## 4. Observability: logs, metrics, traces

**Definition:** Observability is the practice of being able to understand a running system's internal state from its external outputs — conventionally broken into three complementary "pillars": logs, metrics, and traces, each answering a different kind of question.

**Key concepts / comparison:**

| Pillar | What it captures | Spring/Java tooling | Answers |
|---|---|---|---|
| Logs | Discrete, timestamped events, often with free-text detail | SLF4J + Logback (the same stack from the Java track's Phase 6.3) | "What exactly happened, and what did this specific request/error look like?" |
| Metrics | Aggregated numeric measurements over time (counters, gauges, histograms) | Micrometer → exported to Prometheus, visualized in Grafana | "How is the system behaving in aggregate — request rate, error rate, latency percentiles, memory usage?" |
| Traces | The path a single request takes across services/components, with timing at each hop | Micrometer Tracing / OpenTelemetry | "Where exactly did time go for *this specific* slow request, across every service it touched?" |

- **Logging**: `expense-api` uses the standard SLF4J-over-Logback stack Boot configures automatically; `spring.jpa.show-sql: true` in its `application.yml` is itself a logging-adjacent setting, causing Hibernate to log every SQL statement it generates — invaluable during development for seeing exactly what query a derived method (Phase 4.2) produced, but generally turned off in production due to volume and potential sensitive-data exposure. Production logging is typically configured as structured/JSON output rather than plain text, so log-aggregation systems (the ELK stack, Datadog, etc.) can parse and index fields reliably instead of pattern-matching free text.
- **Metrics**: Micrometer is Spring Boot's vendor-neutral metrics *facade* — your code (or auto-configuration) records metrics through Micrometer's API, and a registry implementation (a Prometheus registry, in the common case) handles actually exporting them in whatever format the chosen monitoring backend expects. Actuator's `/actuator/metrics` endpoint (Section 1) is backed directly by Micrometer.
- **Traces**: in a single-service application like `expense-api` today, tracing matters less; it becomes essential the moment a request fans out across multiple services (e.g., once this API calls another downstream service), where you need to see the *complete* cross-service timeline for one request to diagnose where latency is actually coming from — Micrometer Tracing (Spring's tracing facade) paired with OpenTelemetry is the modern standard mechanism.

**Why it's useful:** being able to name and distinguish all three pillars precisely — and give an example of a question only each one can answer — is a strong signal in a "how would you debug a production issue" interview conversation, versus vaguely saying "we'd check the logs" for every scenario.

## 5. Virtual threads

**Definition:** Virtual threads (introduced in Java 21, the Java version this project targets) are lightweight threads managed by the JVM rather than the OS, making the traditional "one thread per request" web server model viable at far higher concurrency than platform (OS) threads ever allowed — with a single Spring Boot configuration line and no code changes.

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Key concepts:**
- **The problem virtual threads solve**: a traditional Spring MVC app handles each incoming request on one OS ("platform") thread for the request's entire duration, including while that thread is blocked waiting on I/O (a database query, a downstream HTTP call). OS threads are expensive — each one consumes a meaningful chunk of memory for its stack, and an OS can only reasonably schedule a few thousand of them — so under high concurrent load with slow I/O, the app can run out of threads long before it runs out of CPU or memory otherwise, a classic thread-pool-exhaustion bottleneck.
- **How virtual threads fix it**: a virtual thread is a JVM-managed abstraction that's cheap to create (you can have millions) and, crucially, when a virtual thread blocks on I/O, the JVM automatically *unmounts* it from its underlying OS ("carrier") thread, freeing that OS thread to run other virtual threads in the meantime — then remounts the virtual thread onto some OS thread once the I/O completes. The blocking code you already wrote (a synchronous `repository.findById(id)` call, exactly as `ExpenseService` does today) doesn't change at all; the JVM handles the efficient scheduling underneath it transparently.
- **`spring.threads.virtual.enabled=true`** (Boot 3.2+, on Java 21+) switches the embedded Tomcat's request-handling thread pool to hand out virtual threads instead of platform threads — one configuration line, zero code changes, and every existing blocking call in the codebase (JDBC queries, `RestTemplate` calls, anything synchronous) automatically benefits from this cheap-to-block model.
- **Why this matters relative to the reactive alternative**: before virtual threads, the industry answer to "blocking I/O doesn't scale" was largely reactive programming (WebFlux, `Mono`/`Flux`) — a fundamentally different, non-blocking programming model with real learning-curve and debugging-complexity costs. Virtual threads let a codebase keep its simple, synchronous, blocking style (the style `expense-api` is written in throughout) while still scaling to a large number of concurrent blocked requests — the payoff without the paradigm shift.

**Why it's useful:** this is one of the most significant recent changes in the Java/Spring ecosystem, and being able to explain *why* it matters (thread cost, blocking I/O, the reactive alternative it largely obviates for many use cases) rather than just "it's a config flag that makes things faster" is what separates a surface-level answer from a genuinely informed one.

## 6. Graceful shutdown, connection pooling, and caching

**Definition:** Rounding out production readiness — behavior at shutdown, database connection management, and response caching — each addresses a different class of real-world operational failure mode.

**Key concepts:**
- **Graceful shutdown** (`server.shutdown: graceful`, with a configurable grace period): when an instance is being taken down (a rolling deployment, a scale-down event), the server stops accepting *new* requests immediately but allows in-flight requests a bounded window to finish, rather than abruptly severing them mid-response — avoiding a burst of client-visible errors during every routine deployment.
- **Connection pool tuning (HikariCP)**: Boot's auto-configured JPA/JDBC `DataSource` uses HikariCP (the connection-pool library the Java track's Phase 7.2 covered directly) by default. A connection pool reuses a small, bounded set of already-open database connections rather than opening a new physical connection per request (expensive — TCP handshake, authentication, session setup, every time). Pool size needs deliberate tuning in production: too small and requests queue waiting for a free connection under load; too large and you risk overwhelming the database server's own connection limits, or wasting memory on idle connections. `expense-api`'s current config relies on HikariCP's sensible defaults, appropriate for its small in-memory H2 setup; a production deployment against a real database would tune `spring.datasource.hikari.*` properties (max pool size, connection timeout) against the actual database's capacity and the app's real concurrency needs.
- **Caching (`@Cacheable`, `@CacheEvict`)**: Spring's caching abstraction lets you declaratively cache the result of an expensive method call — `@Cacheable("expenseReports")` on a method annotates that its return value should be cached, keyed by its arguments, so repeated calls with the same arguments skip re-execution and return the cached value instead. `@EnableCaching` turns the abstraction on; the actual storage is pluggable — a simple in-memory `ConcurrentHashMap`-backed cache for a single instance, or a distributed cache like Redis (covered in track 15) once multiple instances need to share a consistent cache. `expense-api` doesn't currently use caching (its dataset and query patterns are simple enough not to need it yet), but the abstraction is exactly where you'd reach for it if, say, `totalsByCategory()` became an expensive aggregation queried far more often than the underlying data changes.

**Why it's useful:** these three concerns are frequently what actually breaks a Spring Boot app in production, even when its business logic and tests are entirely correct — abrupt shutdowns causing deployment-time errors, a misconfigured connection pool causing intermittent timeouts under load, or a hot, uncached endpoint causing unnecessary database load, are all "worked in dev, broke in prod" categories of bugs distinct from logic bugs.

## The finished capstone

`expense-api` is the Java capstone reborn as a real, tested, production-shaped REST API: layered architecture (web/service/repository), dependency injection throughout, Spring Data JPA persistence, declarative validation and centralized error handling, DB-backed JWT security, a genuine test suite spanning unit/slice/full-context tests, and Actuator-backed observability — all runnable as a single executable JAR. The wiring this project wrote by hand in the Java capstone (`JdbcExpenseRepository`, manual transaction management, manual object construction) is now the framework's job — and because it was built by hand first, none of Spring's annotations are unexplained magic; each one maps to a concrete mechanism covered somewhere in Phases 1–8. From here: the **React** frontend (track 08) consumes this API over its JWT-secured REST contract; **Docker/Kubernetes** (tracks 12–14) containerize and orchestrate it using exactly the fat-JAR packaging described in Section 3; **Redis** (track 15) is the natural backing store the moment `@Cacheable` gets introduced for real.
