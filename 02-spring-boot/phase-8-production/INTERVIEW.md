<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · security](../phase-7-security/NOTES.md)
<!-- /nav -->

# Phase 8 — Production: Interview Q&A

⭐ = asked constantly.

**Q: What is Spring Boot Actuator?** ⭐
A module exposing production-ready operational endpoints — `/health`, `/info`, `/metrics`, `/loggers`, `/env`, etc. — for monitoring and managing a running app. Health checks feed load balancers and Kubernetes probes; metrics feed Prometheus/Grafana. Expose and secure deliberately.

**Q: How do you configure an app differently per environment?** ⭐
Profiles: `application-{profile}.yml` activated via `spring.profiles.active`, plus environment variables and command-line args (which override files). One built artifact behaves correctly in dev/test/prod; secrets injected from the environment, never committed.

**Q: What does `mvn package` produce for a Spring Boot app?**
An executable "fat JAR" (uber-JAR) containing your code, all dependencies, and the embedded server, runnable with `java -jar`. Self-contained and identical across environments — the foundation for containerization.

**Q: How do virtual threads help a Spring Boot app?** ⭐
On Boot 3.2+/Java 21, `spring.threads.virtual.enabled=true` runs each request on a virtual thread. Blocking I/O (DB, downstream calls) becomes cheap, so the simple thread-per-request model scales to huge concurrency without reactive complexity — one config line, no code change.

**Q: What are the three pillars of observability?**
Logs (discrete events), metrics (aggregated numeric measurements over time), and traces (a request's path across services). Spring integrates via SLF4J/Logback, Micrometer (→ Prometheus), and Micrometer Tracing/OpenTelemetry.

**Q: How do you add caching?**
`@EnableCaching` + `@Cacheable`/`@CacheEvict` on methods, backed by a cache provider (in-memory, or Redis for a distributed cache — track 15). The abstraction keeps caching declarative and provider-agnostic.

**Q: How would you containerize this app?**
Because it's a self-contained JAR, a minimal Dockerfile (`FROM eclipse-temurin:21-jre`, copy the JAR, `ENTRYPOINT java -jar`) suffices; or use Buildpacks (`mvn spring-boot:build-image`) for an optimized layered image. Covered in the Docker track.
