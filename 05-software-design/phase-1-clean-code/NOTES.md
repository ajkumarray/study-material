<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · solid ➡](../phase-2-solid/NOTES.md)
<!-- /nav -->

# Phase 1 — Clean Code Fundamentals: Notes

**Clean code is code optimized for reading.** You read code far more than you write it, and code is read by others (and future-you). Clean code produces the *same behavior* but is named well, small, and obvious — cheaper to change, safer to modify, easier to test. It's the substrate the SOLID principles (Phase 2) and patterns (Phases 3–5) build on.

## 1.1 — Names, functions, comments
- **Meaningful names** reveal intent: `totalShippableValue` not `calc`, `orders` not `l`. No cryptic abbreviations, no encodings, no mental mapping. A good name removes the need for a comment. Rename freely — the IDE makes it safe.
- **Small, single-purpose functions.** A function should do *one thing* at one level of abstraction. Long functions hide bugs and mix concerns; extract until each function reads like a sentence. Few parameters (0–3); many parameters signal a missing object.
- **Comments that earn their place.** Prefer self-documenting code over comments that restate it (`i++; // increment i` is noise). Comment the **why** — non-obvious rationale, trade-offs, warnings, links to a spec/ticket — not the *what*. A comment explaining bad code is a request to clean the code instead.

## 1.2 — DRY, KISS, YAGNI, magic numbers
- **DRY** (Don't Repeat Yourself) — every piece of knowledge has one authoritative representation. Duplicated logic (the free-shipping rule) drifts out of sync; extract it to one place. (But avoid *false* DRY — coincidentally-similar code isn't duplication.)
- **KISS** (Keep It Simple) — the simplest thing that works; complexity must earn its place.
- **YAGNI** (You Aren't Gonna Need It) — don't build for imagined future requirements; add flexibility when a real need appears. The counterweight to over-applying patterns.
- **No magic numbers/strings** — name constants (`FREE_SHIPPING_THRESHOLD = 500`): the name states intent and there's one place to change it.
- **Guard clauses over deep nesting** — handle edge cases first with early returns; keep the happy path flat and unindented (Java Phase 1.4). Avoids the "arrow" anti-pattern of nested ifs.
- **The Boy Scout Rule** — leave the code cleaner than you found it; small continuous improvements beat a big rewrite.

## 1.3 — Cohesion, coupling, Law of Demeter
- **High cohesion** — a module's parts belong together (do one job); **low coupling** — modules depend on each other minimally, through stable interfaces. The goal of good design; SOLID is largely in service of it.
- **Law of Demeter** ("don't talk to strangers") — a method should only call methods on: itself, its parameters, objects it creates, and its direct fields — not reach through chains (`order.getCustomer().getAddress().getCity()`). Deep chains couple you to the whole object graph; add a method that answers the question (`order.customerCity()`).
- **Command-Query Separation** — a method should either *do* something (command, returns void, has side effects) or *answer* something (query, returns a value, no side effects) — not both. Makes code predictable (a query is safe to call anywhere).

## 1.4 — Error handling as design
- Use exceptions, not error codes; fail **fast** and close to the cause (validate early — Java Phase 3.1). Don't swallow exceptions (empty catch = hidden bug). Throw meaningful, typed exceptions with context; translate at layer boundaries.
- Prefer returning **`Optional`** or throwing over returning `null` for "absent" (Java Phase 4.3) — null is the "billion-dollar mistake." Never pass or return null casually.
- Keep error handling separate from the happy path (try/catch wraps a focused block; guard clauses reject bad input up front).

## Perspective
Clean code is a discipline, not a rulebook — the aim is *readability and changeability*, and rules serve that. Signs you're succeeding: small functions with clear names, shallow nesting, no duplication, obvious flow, and tests that are easy to write (untestable code is usually badly-designed code — Java Phase 6.2). Signs of trouble (Phase 7's smells): long methods, god classes, deep nesting, duplicated logic, cryptic names. Code review and refactoring (Phase 7) keep it clean over time.
