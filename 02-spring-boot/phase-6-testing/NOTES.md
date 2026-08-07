<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · validation errors](../phase-5-validation-errors/NOTES.md) | [Phase 7 · security ➡](../phase-7-security/NOTES.md)
<!-- /nav -->

# Phase 6 — Testing Spring Apps: Notes

Spring Boot's test support builds on JUnit 5 + AssertJ + Mockito (Java Phase 6.2) and adds **test slices** — load only the layer under test for speed.

## 6.1 — Test slices vs full context

- **`@SpringBootTest`** — loads the *entire* application context. Use for full integration tests (real wiring end to end). Slowest; use sparingly.
- **`@WebMvcTest(XController.class)`** — loads *only* the web layer (controllers, JSON, validation, exception advice), not services/repositories. Collaborators are mocked. Fast controller tests.
- **`@DataJpaTest`** — loads *only* the JPA layer against an in-memory DB, rolls back each test. Proves entities/repositories/derived queries generate correct SQL.

Slices keep tests fast and focused — you test one layer's behavior without booting the world. Our suite uses `@WebMvcTest` for the controller (6 tests) and `@DataJpaTest` for the repository (3 tests), 9 passing.

## 6.2 — MockMvc & Mockito

- **`MockMvc`** drives fake HTTP requests through the Spring MVC stack without a running server: `mvc.perform(get("/api/expenses")).andExpect(status().isOk()).andExpect(jsonPath("$[0].description").value("coffee"))`. It verifies status, headers, and JSON body (via `jsonPath`).
- **`@MockBean`** (Boot 3.3; `@MockitoBean` in 3.4+) replaces a bean in the context with a Mockito mock. In the web-slice test, `ExpenseService` is mocked so we test *only* the controller's HTTP behavior — `given(service.byId(99)).willThrow(new ExpenseNotFoundException(99))` then assert a 404.

This is the testing pyramid (Java Phase 6.2): many fast slice/unit tests, fewer full `@SpringBootTest` integration tests, few end-to-end.

## 6.3 — Integration testing & Testcontainers

For true integration against real infrastructure, **Testcontainers** spins up a real database (or Kafka/Redis) in a Docker container for the test, then tears it down — higher fidelity than H2 (catches DB-specific behavior). Combined with `@SpringBootTest` and `@DynamicPropertySource` to point the app at the container. (Requires Docker — covered in the DevOps track; noted here as the professional standard for integration tests.)
