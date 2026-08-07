<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · di ➡](../phase-3-di/NOTES.md)
<!-- /nav -->

# Phase 2 — REST APIs: Notes

## 2.1 — Controllers & request mapping

**`@RestController`** = `@Controller` + `@ResponseBody`: each handler method's return value is serialized straight to the response body (JSON via Jackson, from the web starter) — no view resolution. **`@RequestMapping("/api/expenses")`** sets a base path; verb-specific shortcuts map HTTP methods:

| Annotation | HTTP | Use |
|---|---|---|
| `@GetMapping` | GET | read (safe, idempotent) |
| `@PostMapping` | POST | create (not idempotent) |
| `@PutMapping` | PUT | full replace (idempotent) |
| `@PatchMapping` | PATCH | partial update |
| `@DeleteMapping` | DELETE | remove (idempotent) |

**Binding request data:**
- `@PathVariable long id` — from the URL path (`/api/expenses/{id}`).
- `@RequestParam(defaultValue="7") int days` — from the query string (`?days=7`).
- `@RequestBody CreateExpenseRequest req` — deserialize the JSON body into an object.

## 2.2 — ResponseEntity & status codes

Returning a plain object → `200 OK` + JSON. For control over status/headers, return **`ResponseEntity<T>`**:
- Create → **`201 Created`** with a **`Location`** header pointing at the new resource (`ResponseEntity.created(uri).body(...)`) — REST convention.
- Delete → **`204 No Content`** (`ResponseEntity.noContent().build()`).
- Not found → **`404`** (via the exception advice, Phase 5).

**Status code fluency (interview staple):** 2xx success (200 OK, 201 Created, 204 No Content); 3xx redirect; 4xx client error (400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 422 Unprocessable); 5xx server error (500, 503). Using the right code IS the API contract.

## 2.3 — DTOs vs entities

**Never expose JPA entities directly at the web edge.** Use **DTOs** (Data Transfer Objects) — here, Java `records`:
- **Decoupling** — the API contract is independent of the DB schema; either can evolve without breaking the other.
- **Security** — hide internal fields; control exactly what's exposed and what's accepted (a client can't set the server-assigned `id`).
- **Validation** — request DTOs carry Bean Validation annotations at the boundary (Phase 5).

Pattern: `CreateExpenseRequest` (inbound, validated) → service works with the entity → `ExpenseResponse.from(entity)` (outbound). Records are ideal DTOs — immutable, and Jackson binds JSON to them. (Entities can't be records — Phase 4.)

**REST principles:** resources as nouns (`/api/expenses`), HTTP verbs for actions, statelessness (each request self-contained), proper status codes, and representations (JSON) decoupled from storage.
