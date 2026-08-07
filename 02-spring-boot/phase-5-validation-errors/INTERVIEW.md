<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · data jpa](../phase-4-data-jpa/NOTES.md) | [Phase 6 · testing ➡](../phase-6-testing/NOTES.md)
<!-- /nav -->

# Phase 5 — Validation & Error Handling: Interview Q&A

⭐ = asked constantly.

**Q: How does validation work in Spring Boot?** ⭐
Bean Validation (Jakarta Validation / Hibernate Validator). Annotate DTO fields (`@NotBlank`, `@Positive`, `@PastOrPresent`), put `@Valid` on the controller parameter, and Spring validates before the handler runs. A failure throws `MethodArgumentNotValidException`, mapped to a 400 by your handler.

**Q: `@NotNull` vs `@NotEmpty` vs `@NotBlank`?** ⭐
`@NotNull` — not null (any type). `@NotEmpty` — not null and size/length > 0 (strings, collections). `@NotBlank` — not null and contains non-whitespace (strings only). Blank ⊂ empty ⊂ null in strictness.

**Q: How do you handle exceptions globally?** ⭐⭐
`@RestControllerAdvice` class with `@ExceptionHandler` methods mapping exception types to responses. Centralizes error handling, keeps controllers clean, and produces consistent error payloads. Return `ResponseEntity` or `ProblemDetail` with the right status.

**Q: `@ControllerAdvice` vs `@RestControllerAdvice`?**
`@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`, so handler return values are serialized to the response body (JSON) — the right choice for REST APIs.

**Q: What is RFC 7807 / Problem Details?** ⭐
A standard JSON format for HTTP error responses (`type`, `title`, `status`, `detail`, `instance`, plus extensions). Spring's `ProblemDetail` implements it. Standardized errors let clients handle failures uniformly instead of parsing bespoke shapes.

**Q: Where should validation live — controller, service, or entity?**
Input/format validation at the boundary (DTO + `@Valid` in the controller) so bad requests fail fast with 400. Business-rule/cross-field validation in the service. Entity constraints (`@Column(nullable=false)`) are the last line of defense at the DB. Defense in depth, but push user-input errors to the edge.

**Q: Why not return stack traces or exception messages directly to clients?**
Security (leaks internal structure, class names, SQL) and UX (unhelpful). The catch-all handler returns a generic 500 with a safe message and logs the real error server-side. Map known exceptions to specific, safe 4xx responses.

**Q: Default rollback behavior of `@Transactional` with exceptions?**
Rolls back on unchecked exceptions (`RuntimeException`/`Error`) by default, not on checked exceptions — override with `rollbackFor`/`noRollbackFor`. Relevant when an exception handler sits above a transactional service.
