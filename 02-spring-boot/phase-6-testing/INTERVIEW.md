<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · validation errors](../phase-5-validation-errors/NOTES.md) | [Phase 7 · security ➡](../phase-7-security/NOTES.md)
<!-- /nav -->

# Phase 6 — Testing: Interview Q&A

⭐ = asked constantly.

**Q: `@SpringBootTest` vs test slices?** ⭐⭐
`@SpringBootTest` loads the full application context — high fidelity, slow — for integration tests. Slices load only one layer: `@WebMvcTest` (controllers + MVC infra, collaborators mocked), `@DataJpaTest` (JPA + in-memory DB, rolled back). Prefer slices for fast, focused tests; reserve full-context tests for real end-to-end wiring.

**Q: What is MockMvc?** ⭐
A test utility that sends simulated HTTP requests through the Spring MVC stack without starting a server. You assert on status, headers, and response body (`jsonPath`). Ideal with `@WebMvcTest` for controller behavior.

**Q: `@Mock` vs `@MockBean`/`@MockitoBean`?** ⭐
`@Mock` (plain Mockito) creates a mock object with no Spring involvement. `@MockBean` (Boot ≤3.3) / `@MockitoBean` (3.4+) creates a Mockito mock **and** places it in the Spring context, replacing the real bean — so injected collaborators use the mock. Use the Spring variant in slice/context tests.

**Q: How do you test the persistence layer?**
`@DataJpaTest` — loads JPA config against an in-memory DB, auto-rolls back each test for isolation. It verifies entity mappings and that derived/`@Query` methods generate correct, working SQL. For real-DB fidelity, use Testcontainers.

**Q: What is Testcontainers and why use it?** ⭐
A library that starts real dependencies (Postgres, Kafka, Redis) in Docker containers for tests, ensuring integration tests run against the *actual* technology, not a stand-in like H2 — catching dialect/behavior differences. Torn down automatically after the test.

**Q: Describe the testing pyramid for a Spring app.**
Many fast unit/slice tests (services with mocked repos, `@WebMvcTest`, `@DataJpaTest`), fewer integration tests (`@SpringBootTest`, Testcontainers), and a small number of end-to-end tests. Optimizes for speed and feedback while still covering real wiring.

**Q: How do you unit-test a service without Spring?**
Because we use constructor injection, just `new ExpenseService(mockRepo)` with a Mockito mock — no Spring context needed, milliseconds to run. This testability is a direct benefit of constructor injection (Phase 3).
