<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · solid ➡](../phase-2-solid/NOTES.md)
<!-- /nav -->

# Phase 1 — Clean Code: Interview Q&A

⭐ = asked constantly.

**Q: What does "clean code" mean to you?** ⭐⭐
Code optimized for reading and changing, not just for working. It produces the exact same behavior as messy code, but with meaningful names, small single-purpose functions, no duplicated logic, shallow nesting, and an obvious flow. The underlying justification is economic: code is read far more often than it's written — during review, during debugging, months later by someone (often you) who has forgotten the context — so the cost of a bad name or a tangled function is paid over and over, while the cost of a good one is paid once. Clean code is what keeps that ongoing cost low.

*Follow-up: "Is clean code the same as 'well-documented' code?"* No — heavy commenting is often a symptom of *unclear* code compensating with prose. Clean code aims to need fewer comments because the names and structure already communicate intent; comments are reserved for information the code genuinely can't express (the "why," not the "what").

**Q: What makes a good function?** ⭐⭐
It does one thing, at one consistent level of abstraction, has a name that fully describes that one thing, takes few parameters (0–3 as a rule of thumb), has no hidden side effects a caller wouldn't expect from its name, and reads almost like a sentence when you read its body top to bottom. A practical test: if you need the word "and" to describe what it does ("validates and saves the order"), it's doing more than one job and should be split. Another test: if extracting a chunk of the body into its own well-named function would make the remaining code *more* readable, that chunk should be extracted.

```java
// smells like two responsibilities — split it
void validateAndSave(Order order) { ... }

// two functions, each testable and reusable independently
void validate(Order order) { ... }
void save(Order order) { ... }
```

*Follow-up: "Why is a long parameter list a problem?"* Beyond raw readability, a long parameter list is a design smell (Phase 7) — it usually signals that several of those parameters are really one concept traveling together (e.g., `street, city, zip` are really an `Address`). Bundling them into a value object shortens the signature and gives the concept a name.

**Q: DRY, KISS, YAGNI — explain each and how they interact.** ⭐⭐
DRY (Don't Repeat Yourself): every piece of *knowledge* should have one authoritative representation, so a rule change happens in exactly one place instead of needing to be hunted down across several duplicated copies that can silently drift apart. KISS (Keep It Simple): choose the simplest solution that correctly solves the problem in front of you — complexity has to earn its place. YAGNI (You Aren't Gonna Need It): don't build flexibility or abstraction for a requirement that doesn't exist yet; add it when a real second use case actually appears.

They interact as a set of counterweights: DRY pushes toward extracting shared logic, but taken too far it produces "false DRY" — merging code that's only *coincidentally* similar, which couples two things that should evolve independently. YAGNI is the brake on both DRY and pattern-application — it stops you from building a generic, reusable abstraction for a single current use case "just in case." KISS is the general-purpose brake on over-engineering across the board. Together they fight the two failure modes of a codebase: duplication drifting out of sync, and speculative complexity nobody needed.

**Q: When are comments good vs. bad?** ⭐
Bad comments restate what the code already says (`i++; // increment i`) — pure noise that adds reading time for zero information, and that will silently go stale the moment the code around it changes since nothing forces a comment to stay accurate. Good comments explain the *why*: a non-obvious rationale, a trade-off, a warning about a subtlety, a link to the spec or ticket that justifies an odd-looking value. The best comment is the one you don't need, because a clear name or a well-extracted function already conveys the same information. A comment that apologizes for or explains away bad code ("hacky, don't touch") is a request to refactor the code, not a reason to leave it as-is.

*Follow-up: "Would you ever comment obvious code?"* No — if the code is genuinely obvious, a comment adds nothing but upkeep burden. The bar for "worth commenting" is: does this convey information the code structurally cannot?

**Q: What is the Law of Demeter?** ⭐⭐
Informally, "only talk to your immediate friends": a method should call methods on itself, its parameters, objects it creates, and its own direct fields — not reach through a chain of getters into an object it doesn't directly own, like `order.getCustomer().getAddress().getCity()`. That chain couples the caller to `Order`, `Customer`, *and* `Address` simultaneously, even though the caller only actually cares about a city string. The fix is to add a method at the level that already owns the relationship — `order.customerCity()` — so every other caller depends only on `Order`'s stable public method instead of the internal shape of three separate classes.

```java
// violates LoD — depends on Order, Customer, AND Address
String city = order.getCustomer().getAddress().getCity();

// respects LoD — Order does the traversal internally, caller depends only on Order
String city = order.customerCity();
```

*Follow-up: "Is method chaining always a Law of Demeter violation?"* No — a fluent builder like `new Pizza.Builder("medium").cheese().topping("olive")` chains calls on the *same object being built* (each call returns `this` or the builder), which is fine. The Law of Demeter is about reaching through a chain of *different, unrelated* objects, not about chaining calls in general.

**Q: What is Command-Query Separation?**
A method should either perform an action with side effects and return nothing meaningful (a command), or answer a question with no side effects (a query) — never both in the same method. The payoff is predictability: if you know a method is a query, you can call it anywhere — in a log line, an assertion, a loop condition — with total confidence it won't silently mutate program state. The classic violation is a "getter" that also advances internal state as a side effect (e.g., a `getNext()` with no separate way to advance a cursor) — calling it "just to look" is impossible without also changing something.

*Follow-up: "Is a `boolean save(Employee e)` method that persists and returns success/failure a CQS violation?"* It's a commonly tolerated pragmatic exception (a command that also reports outcome), rather than the strict violation CQS warns about — the real danger case is a *query-looking* method with a hidden mutating side effect, since callers have no reason to expect that from the name.

**Q: How should you handle nulls and errors?** ⭐⭐
Prefer exceptions or `Optional` over returning `null` to signal "this failed" or "this is absent" — a `null` is invisible in a method signature and easy to forget to check, deferring a `NullPointerException` to some unrelated line far from the actual cause. Fail fast: validate input at the boundary with guard clauses so bad data doesn't travel deep into the call stack before something finally chokes on it. Throw typed, meaningful exceptions with context (`new IllegalArgumentException("unknown shape: " + type)`, as `ShapeFactory` in Phase 3 does) rather than a generic exception with no message. Never swallow an exception silently — an empty or log-and-continue `catch` block turns a loud, diagnosable failure into a silent, mysterious one that resurfaces somewhere unrelated later. Translate exceptions at layer boundaries (a low-level `SQLException` shouldn't leak into an HTTP response).

```java
static Shape create(String type) {
    return switch (type) {
        case "circle" -> new Circle(1);
        case "square" -> new Square(0);
        default -> throw new IllegalArgumentException("unknown shape: " + type);
    };
}
```

*Follow-up: "What's actually wrong with returning null for 'not found'?"* Nothing in the type system forces a caller to check for it, so a missed check becomes a `NullPointerException` at some later, unrelated line with no indication of *why* the value was null. `Optional<T>` (or a thrown, typed exception) makes the absence explicit in the signature and nudges — or in the exception case, forces — the caller to handle it.

**Q: What is cohesion vs. coupling?** ⭐⭐
Cohesion measures how focused a single module is — high cohesion means every part of a class serves one clear job. Coupling measures how entangled modules are with each other — low coupling means a change inside one module is unlikely to force a change in another. The two reinforce each other: a highly cohesive class (doing one job) naturally needs less from the outside world and exposes a narrower, more stable surface, which produces lower coupling. Nearly every principle in this track — SOLID, the GoF patterns, layered/hexagonal architecture — is, underneath, a mechanism for raising cohesion and lowering coupling.

*Follow-up: "Give a concrete example of low cohesion."* An `Employee` class that does pay calculation, database persistence, *and* payslip formatting (the Phase 2 SRP example) — three unrelated jobs bundled into one class, each pulling in different dependencies and each a different reason to change.

**Q: How do you keep a codebase clean over time?**
The Boy Scout Rule — leave the code a little cleaner than you found it every time you touch it, rather than waiting for a dedicated cleanup effort that competing priorities usually prevent from ever happening. Backed by continuous, test-guarded refactoring (Phase 7), regular code review (a second pair of eyes catching smells and spreading conventions), and consistent, automated formatting/linting so style isn't a matter of individual taste or manual nitpicking in review. Cleanliness is a habit exercised on every change, not a one-time achievement.

**Q: Isn't clean code just subjective/style?** *nuance*
Formatting choices (brace placement, line length) are largely convention — pick one, automate it with a formatter, and stop debating it in review. But the substantive core — clear names, small single-purpose functions, no duplicated logic, low coupling, an obvious flow — is not a matter of taste: it measurably reduces defect rates and the cost of making a change, and it correlates strongly with how easy the code is to test (tight coupling and mixed responsibilities are exactly what makes code hard to unit-test). The specific choices vary by team; the underlying goal — code that's cheap to read, change, and verify — is concrete and largely non-negotiable.

**Q: What would happen if you skipped guard clauses and nested every validation instead?** ⭐
The logic still works — behavior is unchanged — but every added validation increases indentation ("the arrow anti-pattern"), and the actual point of the method ends up buried several `if`/`else` levels deep. A reader has to mentally track which branch they're currently inside at each brace level just to find the real logic. Guard clauses flatten this: each precondition is checked and rejected independently at the top level, and the happy path sits unindented at the end, so adding a fourth validation only means adding one more early `if` — it never requires re-indenting existing code.

**Q: What's a code smell that tells you a function needs to be split, versus one that's fine as-is?**
A long function mixing several concerns (validation, calculation, formatting) at different levels of abstraction in the same body is the clearest signal — especially if you can point at a chunk and give it a name that's different from the function's own name (that chunk is a hidden sub-responsibility). A function that's long only because it enumerates many sequential, single-level steps of one coherent operation (a template-method-style linear script) is a closer call — the question is whether extracting sub-steps would *actually* make it easier to test or reuse, not just shorter for its own sake.
