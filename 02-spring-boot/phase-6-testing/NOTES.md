<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · validation errors](../phase-5-validation-errors/NOTES.md) | [Phase 7 · security ➡](../phase-7-security/NOTES.md)
<!-- /nav -->

# Phase 6 — Testing Spring Apps: Notes

Spring Boot's test support builds directly on JUnit 5 + AssertJ + Mockito (the same stack covered in the Java track's Phase 6.2) and adds **test slices**: annotations that load only the part of the application context relevant to what's being tested, instead of the whole app, for speed and isolation. `expense-api`'s test suite — `ExpenseRepositoryTest` (`@DataJpaTest`), `ExpenseControllerTest` (`@WebMvcTest`), and `SecurityJwtIntegrationTest` (`@SpringBootTest`) — deliberately exercises all three levels.

## 1. Test slices vs. full context

**Definition:** A "slice" test loads a deliberately narrow subset of the Spring context — just the beans relevant to one architectural layer — rather than every bean in the application, making the test faster to start and more tightly focused on exactly what it's meant to verify.

**Key concepts / comparison:**

| Annotation | What's loaded | Speed | Used for |
|---|---|---|---|
| `@SpringBootTest` | The entire application context — every bean, real wiring end to end | Slowest | Full integration tests, verifying the whole stack actually works together |
| `@WebMvcTest(Controller.class)` | Only the web layer: the named controller(s), Spring MVC infra, JSON conversion, validation, `@RestControllerAdvice` | Fast | Controller behavior in isolation — status codes, JSON shape, validation wiring |
| `@DataJpaTest` | Only the JPA layer, against an in-memory database, each test auto-rolled back | Fast | Proving entity mappings and derived/`@Query` methods generate correct SQL |

- **`@SpringBootTest`** boots the real `ApplicationContext`, exactly as `SpringApplication.run` would in production — every `@Service`, `@Repository`, `@Configuration`, and security filter is real, wired together. `SecurityJwtIntegrationTest` uses this because its whole point is verifying the *actual* filter chain (real `JwtAuthFilter`, real `SecurityConfig`, real `JwtService`) end to end, not a stand-in.
- **`@WebMvcTest(controllers = ExpenseController.class)`** loads *only* what's needed to exercise `ExpenseController`: MVC infrastructure (request mapping, JSON serialization, Bean Validation, `GlobalExceptionHandler`), but not `@Service`/`@Repository` beans — those must be supplied as mocks (see Section 2). This is dramatically faster than booting the whole app because there's no real database, no real Hibernate, no real security filter chain to initialize.
- **`@DataJpaTest`** configures just enough of the JPA stack — an embedded/in-memory database (H2, in this project), Hibernate, and repository beans — to test persistence in isolation, and wraps *each test method* in a transaction that's rolled back at the end, so tests never leak data into one another regardless of execution order.

**Why it's useful:** slices are the practical mechanism behind the testing pyramid — you want many fast, narrowly-scoped tests and only a few slow, full-context ones. `expense-api`'s suite uses `@WebMvcTest` for controller behavior (6 tests) and `@DataJpaTest` for repository behavior (3 tests) — both fast — reserving the slower `@SpringBootTest` for the one thing that genuinely needs the whole real stack: proving the JWT security flow works end to end.

## 2. `MockMvc` and mocking collaborators

**Definition:** `MockMvc` is a test utility that drives simulated HTTP requests through the Spring MVC dispatch mechanism *without* starting a real network server or listening on a real port — request handling, controller invocation, JSON conversion, and exception handling all run for real, just without an actual socket in front of them.

```java
@WebMvcTest(controllers = ExpenseController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class ExpenseControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ExpenseService service;

    @Test
    void getMissingReturns404() throws Exception {
        given(service.byId(anyLong())).willThrow(new ExpenseNotFoundException(99));

        mvc.perform(get("/api/expenses/99"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.detail").value("expense not found: 99"));
    }

    @Test
    void createInvalidReturns400WithFieldErrors() throws Exception {
        String json = """
                {"description":"","amount":-5,"category":"OTHER","spentOn":"2026-07-15"}""";

        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.detail").value("validation failed"))
           .andExpect(jsonPath("$.errors.amount").exists())
           .andExpect(jsonPath("$.errors.description").exists());
    }
}
```

**Key concepts:**
- **`mvc.perform(get(...))` / `post(...)` / `delete(...)`** build and dispatch a fake HTTP request through the real Spring MVC stack — the real `ExpenseController`, the real `GlobalExceptionHandler`, real Jackson serialization all execute, exactly as they would for a genuine network request.
- **`.andExpect(status().isNotFound())` / `.isBadRequest()` / `.isCreated()` / `.isNoContent()`** assert on the resulting HTTP status.
- **`jsonPath("$.field").value(...)`** asserts on values inside the JSON response body using a JSONPath-like expression — `jsonPath("$.detail").value("expense not found: 99")` reaches into the `ProblemDetail` JSON and checks its `detail` field precisely; `jsonPath("$.errors.amount").exists()` checks that the validation error map contains an entry for the `amount` field, without asserting its exact wording.
- **`@MockBean` (Boot ≤ 3.3) / `@MockitoBean` (Boot 3.4+)** replaces a real bean in the test's Spring context with a Mockito mock — `ExpenseService service` here is a mock, not the real service, so this test verifies *only* the controller's HTTP-layer behavior (status codes, JSON shape, how it reacts to what the service returns/throws) completely independent of whatever the real service or database would actually do. `given(service.byId(anyLong())).willThrow(new ExpenseNotFoundException(99))` (Mockito's BDD-style syntax) programs the mock to throw exactly as if a real lookup had failed, letting the test assert that `GlobalExceptionHandler` correctly converts that into a 404 — without a real database anywhere in the picture.
- **Excluding security from a web-slice test**: `ExpenseControllerTest` explicitly excludes `SecurityConfig`/`JwtAuthFilter` from the slice's component scan and sets `@AutoConfigureMockMvc(addFilters = false)`, because this slice is testing the *controller's* behavior, not authentication — security's own correctness is verified separately (and more appropriately) by the full-context `SecurityJwtIntegrationTest`. Mixing concerns (asserting both "does this endpoint require a token" and "does this endpoint return the right JSON shape" in the same test class) would make failures harder to diagnose; splitting them by test type keeps each test's failure message pointing at exactly one layer.

**Why it's useful:** `MockMvc` gives you real HTTP-semantics assertions (status codes, headers, JSON body) at unit-test speed — no server startup, no real network I/O — while `@MockBean`/`@MockitoBean` lets you isolate exactly the layer under test by replacing its collaborators with fully controllable stand-ins.

## 3. Testing the persistence layer with `@DataJpaTest`

**Definition:** `@DataJpaTest` configures just the JPA/Hibernate infrastructure against an in-memory database and auto-rolls back each test, letting you verify that repository methods — especially derived queries, whose implementation Spring Data generates for you — actually produce correct results.

```java
@DataJpaTest
class ExpenseRepositoryTest {

    @Autowired
    ExpenseRepository repo;

    @Test
    void savesAndGeneratesId() {
        Expense saved = repo.save(new Expense("coffee", new BigDecimal("150"), Category.FOOD, LocalDate.now()));
        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(repo.findById(saved.getId())).isPresent();
    }

    @Test
    void derivedQueryFindByCategory() {
        repo.save(new Expense("a", new BigDecimal("10"), Category.FOOD, LocalDate.now()));
        repo.save(new Expense("b", new BigDecimal("20"), Category.FOOD, LocalDate.now()));
        repo.save(new Expense("c", new BigDecimal("30"), Category.RENT, LocalDate.now()));

        assertThat(repo.findByCategory(Category.FOOD)).hasSize(2);
        assertThat(repo.findByCategory(Category.RENT)).hasSize(1);
    }
}
```

**Key concepts:**
- **Why this matters specifically for Spring Data**: derived query methods (Phase 4.2) have no implementation written by hand — Spring Data generates the query from the method name. `@DataJpaTest` is the mechanism that actually proves the generated SQL is correct, rather than merely trusting that it compiles. `derivedQueryFindByCategory` saves two `FOOD` rows and one `RENT` row, then asserts `findByCategory(FOOD)` returns exactly 2 and `findByCategory(RENT)` returns exactly 1 — verifying the generated `WHERE category = ?` filter is genuinely correct, not just plausible.
- **Auto-rollback isolation**: each `@Test` method runs inside its own transaction that Spring rolls back at the end, so data saved in one test (`savesAndGeneratesId`) never leaks into another test (`derivedQueryFindByCategory`) — tests remain independent regardless of execution order, without needing manual cleanup code.
- **Real fidelity vs. H2's limits**: `@DataJpaTest` uses an in-memory database (H2 here) by default, which is fast and convenient but not byte-for-byte identical to the production database engine (Postgres, in a typical real deployment) — H2 can silently accept SQL that a stricter production database would reject, or behave subtly differently for certain functions/types. For genuine production-fidelity integration testing, see Testcontainers below.

## 4. Integration testing across the whole stack

**Definition:** `@SpringBootTest` boots the complete, real `ApplicationContext` — every layer wired together exactly as in production — for tests that specifically need to verify cross-layer behavior that a narrow slice test can't see.

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityJwtIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mvc.perform(get("/api/expenses"))
           .andExpect(status().isUnauthorized());          // 401 — no token
    }

    @Test
    void registerThenAccessWithToken() throws Exception {
        String body = """
                {"username":"ajay_test","password":"secret123"}""";
        String response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(response).get("token").asText();

        mvc.perform(get("/api/expenses").header("Authorization", "Bearer " + token))
           .andExpect(status().isOk());

        mvc.perform(get("/api/expenses").header("Authorization", "Bearer not.a.jwt"))
           .andExpect(status().isUnauthorized());
    }
}
```

**Key concepts:**
- **Why this specific test needs the full context**: proving "a request without a token gets 401, registering returns a usable token, that token then authenticates a subsequent request, and a garbage token is rejected" requires the *real* `JwtAuthFilter`, the *real* `SecurityConfig` filter chain, the *real* `JwtService` (actually signing and verifying tokens), and the *real* `AuthService`/`UserRepository` persisting an actual user — none of which a `@WebMvcTest` slice would wire up. This is genuinely a cross-layer, end-to-end behavior, so it correctly uses the heaviest, slowest test type.
- **`@ActiveProfiles("test")`** activates the `test` Spring profile for this test class, which (via `DataSeeder`'s `@Profile("!test")`, Phase 3.3) excludes the demo-data seeder — the test starts from a genuinely empty database rather than one pre-populated with a `demo` user and sample expenses, avoiding any risk of the seeded data interfering with what the test itself registers/asserts.
- **`@AutoConfigureMockMvc`** (note: no `addFilters = false` here, unlike the web-slice test) keeps the real security filter chain active in `MockMvc`'s simulated requests — that's the entire point of this test class.

**Testcontainers**: for even higher-fidelity integration testing than an in-memory H2 database offers, **Testcontainers** is a library that spins up a real instance of your actual production dependency — Postgres, Kafka, Redis — inside a Docker container, just for the duration of a test run, then tears it down automatically afterward. Combined with `@SpringBootTest` and Spring's `@DynamicPropertySource` (which lets a test inject the container's actual runtime connection URL/port into the Spring context's configuration before it starts), this lets a test run against the real database engine's actual behavior — catching dialect-specific SQL differences, real constraint enforcement, or real connection-pool behavior that H2 might not surface. This project doesn't currently include Testcontainers (it would require Docker in the execution environment), but it's the professional standard for genuinely trustworthy integration tests in real Spring Boot codebases, and worth knowing by name and purpose even where it isn't wired up yet.

## 5. Unit-testing a service without Spring at all

**Definition:** Because `ExpenseService` (and every other service in this project) uses constructor injection exclusively, it can be instantiated directly in a plain JUnit test with `new`, passing a Mockito mock for its dependency — no Spring context, no `@SpringBootTest`, no `@WebMvcTest`, nothing Spring-specific involved at all.

```java
// Illustrative — the direct payoff of constructor injection (Phase 3.2), not
// currently a file in this project, but exactly how ExpenseService could be tested:
@Test
void addSavesAndReturnsTheExpense() {
    ExpenseRepository mockRepo = mock(ExpenseRepository.class);
    ExpenseService service = new ExpenseService(mockRepo);       // no Spring context at all

    given(mockRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

    Expense result = service.add("coffee", new BigDecimal("150"), Category.FOOD, LocalDate.now());

    assertThat(result.getDescription()).isEqualTo("coffee");
    verify(mockRepo).save(any(Expense.class));
}
```

This test never touches Spring Boot at all — `ExpenseService` is instantiated with a bare `new`, and `ExpenseRepository` is a Mockito mock, not a Spring Data proxy. It runs in milliseconds, with no context startup cost whatsoever — the fastest possible tier of the testing pyramid.

**Why it's useful:** this is the concrete, measurable payoff of preferring constructor injection (Phase 3.2) over field injection — a class built around field injection *cannot* be constructed this way at all (there's no constructor parameter to pass a mock into; you'd need reflection or a real Spring context just to populate the field), making this fastest and simplest style of test unavailable to it.

**The testing pyramid, restated for a Spring app:**
- **Base (many, fastest)**: plain unit tests of services/logic with `new` + mocked dependencies (no Spring at all), plus fast slice tests (`@WebMvcTest`, `@DataJpaTest`) that load only one layer.
- **Middle (fewer, slower)**: `@SpringBootTest` integration tests verifying real cross-layer wiring, optionally against Testcontainers-backed real infrastructure for full fidelity.
- **Top (fewest, slowest)**: full end-to-end tests against a fully deployed system (not exercised inside this project, but the logical top layer in a real deployment pipeline).

**Summary:**
- `@SpringBootTest` = full real context, slow, for genuine cross-layer/end-to-end verification.
- `@WebMvcTest` = web layer only, collaborators mocked with `@MockBean`/`@MockitoBean`, fast controller tests via `MockMvc`.
- `@DataJpaTest` = JPA layer only, in-memory DB, auto-rolled-back per test — the tool that actually proves derived queries generate correct SQL.
- `MockMvc` drives real Spring MVC dispatch without a real server; `jsonPath` asserts on the JSON response body precisely.
- Testcontainers runs tests against the real production infrastructure (via Docker) for higher fidelity than an in-memory stand-in like H2.
- Constructor injection is what makes a service unit-testable with zero Spring involvement at all — the fastest tier of the pyramid.
