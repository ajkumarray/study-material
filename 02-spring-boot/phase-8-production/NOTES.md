<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · security](../phase-7-security/NOTES.md)
<!-- /nav -->

# Phase 8 — Production Concerns: Notes

## 8.1 — Actuator

`spring-boot-starter-actuator` (in our POM) exposes operational endpoints for monitoring and management:
- `/actuator/health` — liveness/readiness (used by Kubernetes probes — DevOps track).
- `/actuator/info` — build/app metadata.
- `/actuator/metrics` — JVM, HTTP, datasource metrics (feed Prometheus/Grafana).
- others: `/env`, `/loggers` (change log levels at runtime), `/mappings`, `/beans`.

Exposure is controlled in config (`management.endpoints.web.exposure.include`) — expose only what's needed, and secure them. This is the "ops interface" of the app.

## 8.2 — Config & packaging

- **Profiles** (Phase 3.3): `application-dev.yml` / `application-prod.yml` selected by `spring.profiles.active`, so one artifact runs correctly per environment. Secrets come from environment variables / a secrets manager, never committed.
- **Packaging**: `mvn package` produces an **executable fat JAR** (the Boot plugin bundles all dependencies + the embedded server). Run with `java -jar expense-api.jar` — the same artifact locally, in CI, and in a container. This self-containment is what makes the Docker track trivial (`FROM eclipse-temurin` + copy the JAR).

## 8.3 — Observability & performance

- **Logging**: SLF4J + Logback (Java Phase 6.3), configurable per environment; structured/JSON logging for aggregation.
- **Observability**: metrics (Micrometer → Prometheus), health checks, distributed tracing (Micrometer Tracing / OpenTelemetry) — the three pillars: logs, metrics, traces.
- **Virtual threads** (Java Phase 5.5): `spring.threads.virtual.enabled=true` on Boot 3.2+/Java 21 runs each request on a virtual thread — the thread-per-request model at massive scale, the payoff we flagged in the Java concurrency phase. One line; no code change.
- **Graceful shutdown**, connection pool tuning (HikariCP — Java Phase 7.2), and caching (`@Cacheable`, backed by Redis — track 15) round out production readiness.

## The finished capstone

`expense-api` is the Java capstone reborn: a tested REST API with layered architecture, DI, Spring Data JPA persistence, validation, centralized error handling, actuator, and a clear path to security and containerization. The wiring you wrote by hand in `01-java/capstone` is now the framework's job — and because you built it manually first, none of the annotations are magic. Next: the **React** frontend (track 08) consumes this API; **Docker/K8s** (tracks 12–14) ship it; **Redis** (15) caches it.
