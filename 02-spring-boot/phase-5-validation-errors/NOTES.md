<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · data jpa](../phase-4-data-jpa/NOTES.md) | [Phase 6 · testing ➡](../phase-6-testing/NOTES.md)
<!-- /nav -->

# Phase 5 — Validation & Error Handling: Notes

## 1. Bean Validation

**Definition:** Bean Validation (the Jakarta Validation spec, implemented by Hibernate Validator, pulled in via `spring-boot-starter-validation`) is a declarative way to express constraints on data by annotating fields — instead of writing manual `if` checks scattered through service code.

```java
public record CreateExpenseRequest(
        @NotBlank(message = "description must not be blank")
        String description,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount,

        @NotNull(message = "category is required")
        Category category,

        @NotNull(message = "spentOn is required")
        @PastOrPresent(message = "spentOn must not be in the future")
        LocalDate spentOn
) { }
```

**Key concepts — common constraint annotations:**
- **`@NotNull`** — the value must not be `null`. Works on any type.
- **`@NotBlank`** — the value must not be `null`, and (for `String`s) must contain at least one non-whitespace character after trimming. Used for `description` above: an empty string `""` or `"   "` both fail, not just `null`.
- **`@NotEmpty`** — must not be `null`, and (for strings/collections) must have a size/length greater than zero — whitespace-only strings *pass* `@NotEmpty` but fail `@NotBlank`.
- **`@Positive`/`@PositiveOrZero`**, **`@Min`/`@Max`** — numeric range constraints. `amount` must be `> 0` — a zero or negative expense amount is rejected before it ever reaches `ExpenseService`.
- **`@Size(min=, max=)`** — length/size bounds for strings and collections (used on `RegisterRequest`'s `username`/`password` fields: `@Size(min = 3, max = 50)`, `@Size(min = 6, max = 100)`).
- **`@Email`**, **`@Pattern(regexp = ...)`** — format constraints (email shape, arbitrary regex).
- **`@Past`/`@PastOrPresent`/`@Future`** — date/time constraints. `spentOn` must be today or earlier — you can't log an expense for a date in the future, enforced with `@PastOrPresent`.

**Strictness comparison — `@NotNull` vs `@NotEmpty` vs `@NotBlank`:**

| Annotation | Rejects `null`? | Rejects `""`? | Rejects `"   "` (whitespace only)? |
|---|---|---|---|
| `@NotNull` | Yes | No | No |
| `@NotEmpty` | Yes | Yes | No |
| `@NotBlank` | Yes | Yes | Yes |

Each is strictly stronger than the last — `@NotBlank` implies `@NotEmpty` implies `@NotNull` in terms of what they reject, but only `@NotBlank`/`@NotEmpty` apply meaningfully to strings/collections; `@NotNull` is the only one of the three that makes sense on, say, a `BigDecimal` or `LocalDate` field.

**`@Valid` triggers validation at the boundary:**

```java
@PostMapping
public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody CreateExpenseRequest req) {
    Expense saved = service.add(req.description(), req.amount(), req.category(), req.spentOn());
    // ...
}
```

Placing **`@Valid`** on the `@RequestBody` parameter tells Spring MVC to run Bean Validation against the deserialized `CreateExpenseRequest` *before* the `create` method body executes at all. If any constraint fails, Spring throws `MethodArgumentNotValidException` immediately — the handler body, and by extension `ExpenseService`, never runs. This is exactly the principle of pushing input validation to the API boundary, keeping the service layer free to assume its inputs are already well-formed.

**Custom validation rules**: for constraints that don't fit a stock annotation — cross-field rules (e.g., "endDate must be after startDate") or business rules that need a database lookup — you write a custom `ConstraintValidator<MyAnnotation, TargetType>` implementation plus the annotation itself, or (more commonly for cross-field/business rules that need other injected beans) validate explicitly inside the service layer instead of trying to force everything into a declarative annotation.

**Why it's useful:** validation annotations turn "does this request look right" from imperative, easy-to-forget `if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw ...` checks scattered across every handler into a single declarative, self-documenting contract read directly off the DTO's field declarations.

## 2. Centralized exception handling

**Definition:** `@RestControllerAdvice` designates a class whose `@ExceptionHandler` methods apply *globally*, across every `@RestController` in the application — a single place mapping exception types to HTTP responses, instead of duplicating try/catch logic in every controller.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 — the resource doesn't exist.
    @ExceptionHandler(ExpenseNotFoundException.class)
    public ProblemDetail handleNotFound(ExpenseNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 400 — Bean Validation failed on a @Valid body. Collect field errors.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "validation failed");
        pd.setProperty("errors", fieldErrors);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // 409 — registering a username that already exists.
    @ExceptionHandler(UsernameTakenException.class)
    public ProblemDetail handleUsernameTaken(UsernameTakenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 401 — wrong username/password at login.
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "invalid credentials");
    }

    // 500 — anything unexpected. Don't leak internals to the client.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "unexpected error");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
```

**Key concepts:**
- **`@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`** — same relationship as `@RestController`/`@Controller` (Phase 2.2): return values from these handler methods are serialized straight to the response body (JSON), never resolved as a view name.
- **`@ExceptionHandler(SomeException.class)`** matches a specific exception type (including its subclasses) thrown anywhere inside a controller method — or, transitively, anywhere in the call chain the controller invokes (the service, the repository) — and maps it to a chosen HTTP response. Each exception in `expense-api`'s domain gets its own precise mapping: `ExpenseNotFoundException` → 404, `UsernameTakenException` → 409, `MethodArgumentNotValidException` (thrown automatically by `@Valid` failures) → 400, `BadCredentialsException` (thrown by Spring Security's `AuthenticationManager` on a wrong password) → 401.
- **Controllers stay on the happy path**: `ExpenseController.get(id)` simply calls `service.byId(id)`, which throws `ExpenseNotFoundException` when the id doesn't exist — the controller has no `try/catch`, no `if (found == null) return 404` branching. The advice class is the *only* place that knows how to translate that exception into an HTTP response. This is the same exception-translation principle used at the persistence layer (`@Repository`, Phase 3.1), now applied at the web layer.
- **Catch-all `Exception` handler**: maps anything not explicitly handled to a generic `500 Internal Server Error` with a safe, non-specific message — critically, it never echoes `ex.getMessage()` or a stack trace back to the client (unlike the specific handlers above, which safely echo their own exception's message because those messages are deliberately written to be user-facing). The real exception detail should be logged server-side (not shown in this handler, but the expected pairing in a production system) for debugging, while the client only ever sees "unexpected error."
- **One place owns the mapping**: every controller in the application benefits from the same consistent error shape without duplicating a single line of handling logic — add a new domain exception anywhere in the codebase, add one `@ExceptionHandler` method here, and every controller that might throw it is covered.

**Why it's useful:** centralizing exception handling is what keeps a growing API's error responses consistent — without it, each controller ends up inventing its own slightly different error JSON shape over time, which is exactly the kind of inconsistency that makes an API frustrating to integrate against.

## 3. Problem Details (RFC 7807)

**Definition:** `ProblemDetail` is Spring's built-in implementation of RFC 7807 ("Problem Details for HTTP APIs") — a standardized JSON shape for representing an HTTP error, so clients can parse failures uniformly instead of guessing at each API's bespoke error format.

```java
return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "expense not found: 99");
```

produces:
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "expense not found: 99"
}
```

**Key concepts:**
- **Standard fields**: `type` (a URI identifying the error category — `about:blank` when there's no more specific documentation URI), `title` (a short, human-readable summary derived from the status code), `status` (the numeric HTTP status), `detail` (a specific, human-readable explanation of *this* occurrence — `ex.getMessage()` in the not-found handler), and optionally `instance` (a URI identifying the specific request/resource that failed).
- **Extending with custom properties**: `pd.setProperty("errors", fieldErrors)` attaches an arbitrary extension member beyond the RFC's base fields — `GlobalExceptionHandler`'s validation handler uses this to attach a `errors` map of `field → message` (so a client can highlight exactly which form fields failed) and a `timestamp`. `ExpenseControllerTest`'s `createInvalidReturns400WithFieldErrors` test asserts exactly this shape: `jsonPath("$.errors.amount").exists()` and `jsonPath("$.errors.description").exists()` after submitting a blank description and a negative amount together.
- **Why standardize at all**: a client library integrating against this API (or any RFC 7807-compliant API) can write one generic error-parsing function — read `status`, `title`, `detail`, and any known extension properties — rather than special-casing each API's own ad-hoc `{"error": "..."}` or `{"message": "...", "code": "..."}` shape.

**Why it's useful:** RFC 7807 is a real, referenceable standard (not a Spring-specific convention), so adopting `ProblemDetail` signals to any API consumer — human or automated — exactly what shape to expect for every error, with room to extend it (as the `errors` map does here) without breaking that base contract.

## 4. Where should validation live?

**Definition:** Different kinds of correctness checks belong at different layers of the application — input/format validation at the web boundary, business-rule validation in the service layer, and structural constraints as a last line of defense at the database.

- **Input/format validation → controller boundary**: "is this a well-formed request" (required fields present, numeric ranges sane, dates not in the future) is exactly what Bean Validation + `@Valid` on the DTO handles, and it should fail fast with `400 Bad Request` before touching any business logic. `CreateExpenseRequest`'s constraints are entirely this category.
- **Business-rule/cross-field validation → service layer**: rules that require domain knowledge or a database lookup — "this username is already taken" (`AuthService.register` explicitly checks `users.existsByUsername(username)` and throws `UsernameTakenException` before proceeding) — don't fit cleanly as a Bean Validation annotation on a single field, because they depend on state beyond the request payload itself.
- **Database constraints → last line of defense**: `@Column(nullable = false, unique = true)` on `AppUser.username` guarantees, at the database level, that two users can never share a username even under a race condition the service-layer check alone can't fully prevent (two concurrent registration requests both passing the `existsByUsername` check before either has committed). This is defense in depth: the earlier layers exist for good user experience (a fast, specific 400/409 response) and the database constraint exists as the unconditional guarantee.

**Why not return stack traces or exception messages directly to clients?** Two separate reasons. Security: a raw stack trace or exception message can leak internal implementation details — class names, package structure, SQL fragments, file paths — information that helps an attacker map the system's internals. User experience: `java.lang.NullPointerException at dev.ajay.expenseapi.service.ExpenseService.add(ExpenseService.java:36)` is meaningless to an API client trying to figure out what to fix in their request. `GlobalExceptionHandler`'s catch-all `Exception` handler deliberately returns a generic `"unexpected error"` detail with no exception-specific information, while the specific, expected exceptions (`ExpenseNotFoundException`, `UsernameTakenException`) safely surface `ex.getMessage()` because those messages are written *as* client-facing text, not as raw internal diagnostics.

## 5. Default rollback behavior interacting with exception handling

**Definition:** Because `@RestControllerAdvice` catches exceptions *after* they've propagated out of a `@Transactional` service method, the transaction's rollback decision has already been made by the time the advice class ever sees the exception — understanding the order of these two mechanisms matters for reasoning about data consistency.

- `@Transactional` (Phase 4.3) rolls back automatically on unchecked exceptions (`RuntimeException`/`Error`), not on checked exceptions, by default.
- `ExpenseNotFoundException` and `UsernameTakenException` are both unchecked (`extends RuntimeException`), so if either is thrown partway through a transactional service method that had already made other changes earlier in the same method, those earlier changes would also roll back — the whole method's transaction is atomic, not just the specific line that threw.
- `GlobalExceptionHandler` only runs *after* the exception has already unwound out of the transactional proxy (and the rollback has already happened) — the advice class's job is purely to translate the (already-thrown, already-rolled-back-if-applicable) exception into an HTTP response; it plays no role in the transaction decision itself.

**Why it's useful:** this ordering — transaction rollback happens at the proxy boundary around the service method, HTTP response mapping happens later at the web layer — is exactly the kind of subtlety interviewers use to check whether you understand these as two independent, composable mechanisms rather than one blurred concept.

**Summary:**
- Bean Validation (`@NotBlank`, `@Positive`, `@PastOrPresent`, etc.) on DTOs + `@Valid` on the controller parameter validates input *before* the handler runs — `MethodArgumentNotValidException` on failure.
- `@RestControllerAdvice` + `@ExceptionHandler` centralizes exception → HTTP mapping in one place, keeping controllers on the happy path.
- `ProblemDetail` (RFC 7807) is the standard, extensible error JSON shape — `type`/`title`/`status`/`detail` plus custom properties like `errors`.
- Never leak stack traces/internal messages to clients — the catch-all handler returns a safe generic message; specific handlers only surface messages written to be client-facing.
- Validate input at the boundary, business rules in the service, and let database constraints be the final unconditional guarantee.
- Rollback (unchecked exceptions only, by default) happens at the `@Transactional` proxy boundary, independently of and before the web-layer exception mapping.
