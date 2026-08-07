<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 02 — Spring Boot

The backend framework that turns your Java skills into production web services.
We **rebuild the Java capstone** (the expense tracker, `01-java/capstone/`) as a
REST API — so you see exactly what Spring automates: the layered architecture,
the repository, the transactions, the DI you wired by hand all become
annotations you already understand from the inside.

One running project — **`expense-api/`** — evolves across the phases. Each phase
has `NOTES.md` (the "why") and `INTERVIEW.md` (Q&A). ⭐ = commonly asked.

Runtime: **Spring Boot 3.3 on Java 21**, Maven, in-memory H2 (swappable for
Postgres — the Databases track's server).

## Curriculum

### Phase 1 — Spring Boot fundamentals ✅
- [x] 1.1 What Spring is; IoC / the DI container; why Spring Boot exists
- [x] 1.2 `@SpringBootApplication`, starters, auto-configuration
- [x] 1.3 The embedded server; a first running app; `application.yml`

### Phase 2 — REST APIs ✅
- [x] 2.1 `@RestController`, request mappings, path/query/body params
- [x] 2.2 `ResponseEntity`, status codes, content negotiation
- [x] 2.3 DTOs vs entities; request/response records; mapping

### Phase 3 — Dependency injection deep dive ✅
- [x] 3.1 Beans; `@Component`/`@Service`/`@Repository`/`@Configuration`
- [x] 3.2 Constructor injection, bean scopes, `@Bean` methods
- [x] 3.3 Profiles, `@Value`, `@ConfigurationProperties`, externalized config

### Phase 4 — Spring Data JPA ✅
- [x] 4.1 `@Entity`, `JpaRepository` — the one-line repository
- [x] 4.2 Derived query methods; `@Query`; pagination & sorting
- [x] 4.3 Relationships, transactions (`@Transactional`), the persistence layer

### Phase 5 — Validation & error handling ✅
- [x] 5.1 Bean Validation (`@Valid`, `@NotNull`, `@Positive`...)
- [x] 5.2 `@RestControllerAdvice` / `@ExceptionHandler`; consistent error responses
- [x] 5.3 Problem Details (RFC 7807)

### Phase 6 — Testing Spring apps ✅
- [x] 6.1 `@SpringBootTest`, slices (`@WebMvcTest`, `@DataJpaTest`)
- [x] 6.2 `MockMvc` for controllers; Mockito for collaborators
- [x] 6.3 Integration testing; Testcontainers overview

### Phase 7 — Security ✅ *(fully implemented + tested)*
- [x] 7.1 Spring Security model; filter chain; authentication vs authorization
- [x] 7.2 Securing endpoints; DB users, roles, BCrypt password encoding
- [x] 7.3 Stateless JWT auth — `JwtService`, `JwtAuthFilter`, register/login

### Phase 8 — Production concerns ✅
- [x] 8.1 Actuator (health, metrics, info) — wired in the app
- [x] 8.2 Profiles & config for environments; packaging the fat JAR
- [x] 8.3 Observability, virtual threads (`spring.threads.virtual.enabled`)

### Capstone ✅
- [x] `expense-api`: a tested REST API (9 passing tests) with validation, error
      handling, JPA persistence, actuator, and a documented path to security —
      the Java capstone reborn as a service, ready for the React frontend to consume.

## Status

Phases 1–7 are **fully coded and verified** in `expense-api/` (`mvn test` → 13
passing: `@WebMvcTest` controller slice, `@DataJpaTest` repository slice, and a
full-context `@SpringBootTest` JWT security flow). Phase 8 is taught as theory
with actuator wired.

**Try the secured API:**
```bash
mvn spring-boot:run
# 1. get a token (a demo user is seeded)
curl -s -X POST localhost:8080/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"demo","password":"password123"}'
# 2. call a protected endpoint with it
curl localhost:8080/api/expenses -H "Authorization: Bearer <token>"
# without the header -> 401
```

## Structure

```
02-spring-boot/
├── README.md
├── expense-api/            <- the evolving Spring Boot project
│   ├── pom.xml
│   └── src/main/java/... , src/test/java/...
├── phase-1-fundamentals/   <- NOTES.md + INTERVIEW.md
├── phase-2-rest/
└── ...
```

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · fundamentals** — [Notes](phase-1-fundamentals/NOTES.md) · [Interview](phase-1-fundamentals/INTERVIEW.md)
- **Phase 2 · rest** — [Notes](phase-2-rest/NOTES.md) · [Interview](phase-2-rest/INTERVIEW.md)
- **Phase 3 · di** — [Notes](phase-3-di/NOTES.md) · [Interview](phase-3-di/INTERVIEW.md)
- **Phase 4 · data jpa** — [Notes](phase-4-data-jpa/NOTES.md) · [Interview](phase-4-data-jpa/INTERVIEW.md)
- **Phase 5 · validation errors** — [Notes](phase-5-validation-errors/NOTES.md) · [Interview](phase-5-validation-errors/INTERVIEW.md)
- **Phase 6 · testing** — [Notes](phase-6-testing/NOTES.md) · [Interview](phase-6-testing/INTERVIEW.md)
- **Phase 7 · security** — [Notes](phase-7-security/NOTES.md) · [Interview](phase-7-security/INTERVIEW.md)
- **Phase 8 · production** — [Notes](phase-8-production/NOTES.md) · [Interview](phase-8-production/INTERVIEW.md)
<!-- /phases-nav -->
