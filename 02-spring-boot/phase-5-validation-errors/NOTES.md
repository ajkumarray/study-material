<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · data jpa](../phase-4-data-jpa/NOTES.md) | [Phase 6 · testing ➡](../phase-6-testing/NOTES.md)
<!-- /nav -->

# Phase 5 — Validation & Error Handling: Notes

## 5.1 — Bean Validation

Declarative input validation via annotations on the request DTO (from the validation starter / Hibernate Validator):
- `@NotNull` (present), `@NotBlank` (non-empty string, trimmed), `@NotEmpty` (non-empty collection/string)
- `@Positive`/`@PositiveOrZero`/`@Min`/`@Max`, `@Size(min,max)`
- `@Email`, `@Pattern(regexp=...)`, `@Past`/`@PastOrPresent`/`@Future`

**`@Valid` on the controller parameter triggers validation** before the handler body runs, so invalid input never reaches the service — validation lives at the boundary (our `CreateExpenseRequest`). On failure Spring throws `MethodArgumentNotValidException`. Custom rules: write a `ConstraintValidator` + annotation, or validate in the service for cross-field/business rules.

## 5.2 — Centralized exception handling

**`@RestControllerAdvice`** is a global handler applied across all controllers; **`@ExceptionHandler(SomeException.class)`** methods map exception types to HTTP responses. This keeps controllers on the happy path — throw a meaningful exception, let the advice translate it (the Java Phase 3.1 exception-translation idea, at the web layer):
- `ExpenseNotFoundException` → **404**
- `MethodArgumentNotValidException` → **400** with per-field error details
- catch-all `Exception` → **500** *without leaking internals* (never send stack traces to clients)

One place owns the exception→HTTP mapping, so error responses are consistent across the whole API.

## 5.3 — Problem Details (RFC 7807)

**`ProblemDetail`** is Spring's built-in RFC 7807 type — the standard machine-readable error format:
```json
{ "type": "about:blank", "title": "Not Found", "status": 404,
  "detail": "expense not found: 99" }
```
You can attach custom properties (`setProperty("errors", fieldErrors)`). Standardizing errors means clients can parse failures uniformly instead of guessing at ad-hoc shapes. Our validation handler returns a ProblemDetail with an `errors` map of field → message.
