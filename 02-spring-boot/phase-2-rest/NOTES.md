<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · di ➡](../phase-3-di/NOTES.md)
<!-- /nav -->

# Phase 2 — REST APIs: Notes

## 1. REST principles

**Definition:** REST (REpresentational State Transfer) is an architectural style for web APIs built around resources (nouns, not actions), manipulated through a small, uniform set of standard HTTP methods (verbs), where each request is self-contained.

**Key concepts:**
- **Resources are nouns, not verbs**: `/api/expenses` and `/api/expenses/{id}` name *things* (the expense collection, one expense). The action is expressed by the HTTP method, not the URL — `GET /api/expenses/deleteExpense?id=5` is not RESTful; `DELETE /api/expenses/5` is.
- **Standard HTTP verbs express intent**: GET reads, POST creates, PUT replaces, PATCH partially updates, DELETE removes.
- **Statelessness**: every request carries everything the server needs to process it (auth token, parameters, body) — the server holds no per-client session between requests. This is exactly why `expense-api`'s security model is a stateless JWT bearer token (Phase 7.3) rather than a server-side session.
- **Uniform interface**: the same conventions (status codes, verbs, representation format) apply consistently across every resource in the API.
- **Representations decoupled from storage**: the client interacts with a JSON *representation* of a resource (an `ExpenseResponse`), not the database row directly — see DTOs below.
- **HATEOAS** (Hypermedia As The Engine Of Application State) is the strictest, fullest form of REST — responses include links to related actions/resources so a client can navigate the API without hardcoding URLs. Most real-world "RESTful" APIs (including this one) skip HATEOAS and are more accurately "HTTP+JSON APIs following REST conventions" — worth knowing the distinction for an interview, even though the pragmatic industry usage of "REST API" almost always means the lighter-weight version.

**Why it's useful:** REST conventions are a shared vocabulary. A client developer who has never seen this codebase can predict that `POST /api/expenses` creates and `DELETE /api/expenses/{id}` removes, without reading documentation — the URL and verb *are* the contract.

## 2. `@RestController` and request mapping

**Definition:** `@RestController` marks a class whose methods handle HTTP requests and whose return values are written directly to the response body (serialized to JSON), rather than resolved to a server-rendered view.

```java
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {   // constructor injection, Phase 3
        this.service = service;
    }

    @GetMapping
    public List<ExpenseResponse> list() {
        return service.all().stream().map(ExpenseResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable long id) {
        return ExpenseResponse.from(service.byId(id));
    }
}
```

**Key concepts:**
- **`@RestController` = `@Controller` + `@ResponseBody`**: `@Controller` alone is Spring MVC's general-purpose handler annotation, whose return values are traditionally resolved as *view names* (e.g., a Thymeleaf template to render as HTML). Adding `@ResponseBody` tells Spring to skip view resolution and instead serialize the return value straight into the HTTP response body via an `HttpMessageConverter` (Jackson, for JSON, auto-configured by the web starter). `@RestController` bundles both so every method in the class behaves this way by default.
- **`@RequestMapping("/api/expenses")`** at the class level sets a base path every handler method's mapping is relative to. `ExpenseController`'s `@GetMapping("/{id}")` therefore actually matches `GET /api/expenses/{id}`.
- **Verb-specific mapping shortcuts** — each is really `@RequestMapping(method = ...)` under the hood, given a friendlier name:

| Annotation | HTTP verb | Typical use | Idempotent? |
|---|---|---|---|
| `@GetMapping` | GET | Read a resource or collection | Yes |
| `@PostMapping` | POST | Create a new resource | No |
| `@PutMapping` | PUT | Fully replace a resource | Yes |
| `@PatchMapping` | PATCH | Partially update a resource | Not guaranteed |
| `@DeleteMapping` | DELETE | Remove a resource | Yes |

*Idempotent* means: making the same request N times has the same effect as making it once. Two identical `POST /api/expenses` calls create two different expense rows (not idempotent); two identical `DELETE /api/expenses/5` calls leave the system in the same end state (idempotent — the second delete is a no-op, typically returning 404 instead of 204 the second time, but the *resource state* — "expense 5 doesn't exist" — is unchanged).

**Why it's useful:** choosing the right verb and being able to explain idempotency correctly is a very common interview checkpoint, because it signals whether you actually understand HTTP semantics or are just pattern-matching CRUD to four annotations.

## 3. Binding request data

**Definition:** Spring MVC extracts values from different parts of an incoming HTTP request (URL path segments, query string, request body) and converts them into typed Java method parameters automatically.

```java
// GET /api/expenses/42                → id = 42
@GetMapping("/{id}")
public ExpenseResponse get(@PathVariable long id) { ... }

// GET /api/expenses/recent?days=14    → days = 14 (or 7 if the param is absent)
@GetMapping("/recent")
public List<ExpenseResponse> recent(@RequestParam(defaultValue = "7") int days) { ... }

// POST /api/expenses  { "description": "book", "amount": 599, ... }
@PostMapping
public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody CreateExpenseRequest req) { ... }
```

**Key concepts:**
- **`@PathVariable`** binds a segment embedded in the URL path — used when the value identifies *which* resource (`/api/expenses/{id}`). It's a required part of the URL structure, not optional.
- **`@RequestParam`** binds a query-string parameter — used for filters, options, and pagination that modify *how* a resource is fetched (`?days=7`). Supports `defaultValue` (as in `recent`, defaulting to the last 7 days if `days` is omitted) and can be marked `required = false`.
- **`@RequestBody`** deserializes the raw JSON request body into a Java object via Jackson. Paired with `@Valid` (Phase 5), it means: parse the JSON into a `CreateExpenseRequest`, then run Bean Validation on it, *before* the handler body executes at all.
- **Type conversion is automatic**: `@PathVariable long id` converts the string path segment to a primitive `long`; if the segment isn't numeric, Spring rejects the request with a 400 before your code runs.

**Why it's useful:** knowing exactly which annotation to reach for (path variable to *identify*, query param to *filter*, body to *submit data*) is what keeps an API's URLs clean and predictable — mixing them up (e.g., putting a resource id in the query string) is a common design smell interviewers listen for.

## 4. `ResponseEntity` and HTTP status codes

**Definition:** `ResponseEntity<T>` is a wrapper giving full control over the HTTP response — status code, headers, and body — as opposed to returning a plain object, which always yields `200 OK` with the object serialized as the body.

```java
// POST /api/expenses  ->  201 Created + Location header + the created resource
@PostMapping
public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody CreateExpenseRequest req) {
    Expense saved = service.add(req.description(), req.amount(), req.category(), req.spentOn());
    ExpenseResponse body = ExpenseResponse.from(saved);
    return ResponseEntity
            .created(URI.create("/api/expenses/" + saved.getId()))
            .body(body);
}

// DELETE /api/expenses/{id}  ->  204 No Content (nothing to return)
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
}
```

In the `create` example, `ResponseEntity.created(uri)` sets the status to `201 Created` *and* populates the `Location` response header with the URI of the newly created resource — the standard REST convention for "here's where to find what you just made." `.body(body)` attaches the JSON-serialized created expense. In `delete`, `ResponseEntity.noContent().build()` returns `204 No Content` with an empty body — there's nothing meaningful to send back after a successful delete.

**Status code fluency (a staple interview topic):**

| Range | Meaning | Common codes used in `expense-api` |
|---|---|---|
| 2xx | Success | `200 OK` (default success), `201 Created` (POST success), `204 No Content` (DELETE success) |
| 3xx | Redirection | (not used here) |
| 4xx | Client error | `400 Bad Request` (validation failure), `401 Unauthorized` (missing/invalid auth), `404 Not Found` (missing resource), `409 Conflict` (duplicate username) |
| 5xx | Server error | `500 Internal Server Error` (unexpected/unhandled exception) |

Returning the *correct* status code is not a cosmetic detail — it **is** part of the API contract. A client library can programmatically branch on status ("if 404, show 'not found'; if 401, redirect to login") without parsing the body at all, as long as the server is disciplined about codes.

**Why it's useful:** getting status codes right is what separates a professional API from one that returns `200 OK` for every outcome, including errors, and forces every client to parse the body just to know whether the call actually succeeded.

## 5. DTOs vs. entities

**Definition:** A Data Transfer Object (DTO) is a plain object whose sole purpose is to define the shape of data crossing a system boundary (here, the HTTP/JSON boundary) — distinct from the JPA entity that represents a database row. `expense-api` never returns or accepts a `dev.ajay.expenseapi.domain.Expense` entity directly at the web layer; it always converts to/from a DTO.

```java
// Inbound: validated at the boundary, immutable, no id field (server-assigned)
public record CreateExpenseRequest(
        @NotBlank String description,
        @NotNull @Positive BigDecimal amount,
        @NotNull Category category,
        @NotNull @PastOrPresent LocalDate spentOn
) { }

// Outbound: the shape the API exposes, built from the entity
public record ExpenseResponse(
        Long id, String description, BigDecimal amount, Category category, LocalDate spentOn
) {
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(e.getId(), e.getDescription(), e.getAmount(),
                e.getCategory(), e.getSpentOn());
    }
}
```

**Key concepts:**
- **Decoupling**: the wire format (JSON shape clients depend on) and the persistence model (database columns) can evolve independently. Renaming a database column doesn't have to break every client overnight — you adjust the mapping inside `ExpenseResponse.from`.
- **Security / least exposure**: `CreateExpenseRequest` has no `id` field, so a client cannot set or overwrite the server-assigned primary key. If the entity had internal-only fields (an audit column, an owner's internal user id), a DTO simply omits them from the response — the entity's full shape is never exposed over the wire.
- **Validation at the boundary**: Bean Validation annotations (`@NotBlank`, `@Positive`, `@PastOrPresent`) live on the *request* DTO, so bad input is rejected before it ever reaches the service or touches the database (Phase 5.1).
- **Records are ideal DTOs**: both `CreateExpenseRequest` and `ExpenseResponse` are Java `record`s — immutable, with auto-generated constructors/accessors/`equals`/`hashCode`, and Jackson can bind JSON straight to (and from) them with zero extra configuration. Contrast with the JPA entity `Expense`, which *cannot* be a record (Phase 4.1 explains why — Hibernate needs a no-arg constructor and mutable fields).
- **The mapping pattern used throughout this project**: `CreateExpenseRequest` (validated input) → service builds/mutates an `Expense` entity → `ExpenseResponse.from(entity)` (validated, curated output). Three distinct types for three distinct concerns, at the cost of a small amount of mapping boilerplate.

**Why it's useful:** exposing JPA entities directly at the web layer is one of the most common "smells" flagged in Spring code review — it silently couples your public API contract to your database schema, can leak lazy-loading proxy artifacts through Jackson, and gives clients a way to set fields (like `id`) they should never control. Interviewers specifically probe this because it separates people who've shipped a real API from people who've only followed a tutorial.

**Summary:**
- REST = resources as nouns + HTTP verbs as actions + statelessness + a uniform, predictable interface.
- `@RestController` skips view resolution and serializes return values to JSON; `@RequestMapping`/verb shortcuts map URLs and methods to handlers.
- `@PathVariable` identifies a resource, `@RequestParam` filters/options it, `@RequestBody` submits it.
- `ResponseEntity<T>` gives explicit control over status code, headers (`Location`), and body — use the *correct* status code, it's part of the contract.
- Never expose entities directly: DTOs (ideally records) decouple the API contract from the database schema and control exactly what's exposed and accepted.
