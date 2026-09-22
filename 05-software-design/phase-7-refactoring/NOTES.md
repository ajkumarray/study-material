<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · architecture](../phase-6-architecture/NOTES.md)
<!-- /nav -->

# Phase 7 — Refactoring, Code Smells & Testing Discipline: Notes

**Refactoring** means changing code's internal *structure* without changing its observable *behavior*, in order to make it cleaner, more understandable, and easier to change going forward. It's the discipline that keeps code clean over time (Phase 1) and lets you safely evolve a design toward SOLID (Phase 2) and the GoF patterns (Phases 3–5) *starting from* imperfect, real-world code rather than a blank page. `Refactoring.java` demonstrates two classic refactorings with assertions that hold for **both** the "before" and "after" versions — direct, runnable proof that the structural change preserved behavior.

---

## 1. Code Smells (symptoms that suggest refactoring)

A **code smell** is a surface indication of a deeper design problem — it isn't a bug (the code still works correctly), but it's a strong signal that the design underneath is fragile, hard to change safely, or hard to understand.

### Key Concepts / Catalogue
- **Long method** — a method that does too much, mixing several concerns at different levels of abstraction, making it hard to read and hard to test in isolation. → **Extract Method** (Section 2).
- **God class / Large class** — one class accumulating many unrelated responsibilities and fields (the Phase 2 SRP violation example: pay calculation + persistence + formatting all in `Employee`). → **Extract Class**, splitting by responsibility.
- **Long parameter list** — many parameters, often several that always travel together (they're really one concept). → introduce a parameter object bundling them.
- **Duplicated code** — arguably the single worst smell, because duplicated *logic* silently drifts out of sync when one copy is updated and another is forgotten (Phase 1's DRY violation, concretely). → Extract and reuse.
- **Feature envy** — a method that's more interested in another class's data than its own (it calls several getters on some other object far more than it touches its own fields). → Move Method to the class that actually owns the data.
- **Switch/if on type** — a conditional that must grow every time a new type/case is added (the `shippingCost_before` example below). → **Replace Conditional with Polymorphism** (Section 2), tying directly back to Open/Closed (Phase 2) and Strategy/State (Phase 5).
- **Primitive obsession** — using bare primitives (a `String` for an email address, a `double` for money) instead of small, purpose-built types that could enforce their own invariants. → introduce a value object.
- **Shotgun surgery** — a single conceptual change forces edits across many unrelated places in the codebase (the opposite problem from a god class: responsibility that *should* be cohesive is instead scattered). → gather the responsibility into one place.
- **Data class / anemic domain** — a class that's pure data with no behavior, while the logic that operates on that data lives elsewhere (the system-wide version of this smell is discussed in Phase 6 under layered architecture). → move behavior onto the data where it naturally belongs.
- **Other classics** (covered in depth in Phase 1): deep nesting, magic numbers, cryptic names, dead code, and comments that explain away bad code rather than the code being fixed.

Smells are **heuristics that tell you *where* to look**, not hard rules that always demand action — a short, three-branch `switch` that will realistically never gain a fourth case may not be worth the ceremony of Replace Conditional with Polymorphism. Judgment matters more than mechanically eliminating every smell on sight.

### Why It's Useful
Smells give you a vocabulary for spotting design trouble quickly, in code review or while reading unfamiliar code, well before it becomes an active bug — "this is feature envy" or "this is a god class" communicates a specific, well-understood problem (and usually a specific, well-understood fix) in a couple of words.

### Summary / Key Takeaways
- A smell is a symptom, not a bug — code still works, but the design is signaling trouble.
- Long method, god class, duplicated code, switch-on-type, and primitive obsession are the ones you'll spot constantly in real codebases.
- Smells are heuristics for *where* to refactor, not a checklist that must always be zeroed out.

---

## 2. Refactoring Techniques (safe structural changes)

Martin Fowler's *Refactoring* book catalogues dozens of named techniques; two are demonstrated, runnable and behavior-verified, in `Refactoring.java`.

### Extract Method

**Definition**: pull a chunk of a long, multi-purpose method out into its own small, named helper method.

```java
// BEFORE — smell: long, does 3 things (line total, discount, all inline), magic number 500
static double receiptTotal_before(List<Item> items) {
    double total = 0;
    for (Item i : items) total += i.qty() * i.price();   // calc line total inline
    if (total > 500) total = total * 0.9;                // magic discount inline
    return total;
}

// AFTER — from Refactoring.java: extracted, named, single-purpose helpers
static final double BULK_THRESHOLD = 500;
static final double BULK_DISCOUNT = 0.10;

static double receiptTotal_after(List<Item> items) {
    double subtotal = subtotal(items);
    return applyBulkDiscount(subtotal);
}
static double subtotal(List<Item> items) {
    return items.stream().mapToDouble(i -> i.qty() * i.price()).sum();
}
static double applyBulkDiscount(double amount) {
    return amount > BULK_THRESHOLD ? amount * (1 - BULK_DISCOUNT) : amount;
}
```

In this example, `receiptTotal_before` mixes computing each line's total, summing them, and applying a magic-number discount all in one loop-plus-conditional block. `receiptTotal_after` splits that into `subtotal(items)` (just the sum) and `applyBulkDiscount(amount)` (just the discount rule, now using named constants `BULK_THRESHOLD`/`BULK_DISCOUNT` instead of a bare `500`/`0.9`). `Refactoring.java`'s `main` asserts `receiptTotal_before(items) == receiptTotal_after(items)` for the same input — the structural change didn't alter the computed result at all, only how the logic is organized and named. `subtotal` and `applyBulkDiscount` are now independently testable and independently reusable (another method that only needs a bulk discount applied to an arbitrary amount can call `applyBulkDiscount` directly, without re-deriving a subtotal first).

### Replace Conditional with Polymorphism

**Definition**: turn a conditional (`if`/`switch`) that dispatches on a type or category tag into a set of strategies (or subclasses), one per case, selected once and then dispatched polymorphically.

```java
// BEFORE — smell: switch-on-type; adding a shipping type means editing this method
static double shippingCost_before(String type, double weight) {
    switch (type) {
        case "standard":  return weight * 0.5;
        case "express":   return weight * 1.0 + 10;
        case "overnight": return weight * 2.0 + 25;
        default: throw new IllegalArgumentException("unknown: " + type);
    }
}

// AFTER — from Refactoring.java: each type is a Shipping strategy
interface Shipping { double cost(double weight); }
static Shipping shippingFor(String type) {
    return switch (type) {                       // one factory point (Phase 3)
        case "standard"  -> weight -> weight * 0.5;
        case "express"   -> weight -> weight * 1.0 + 10;
        case "overnight" -> weight -> weight * 2.0 + 25;
        default -> throw new IllegalArgumentException("unknown: " + type);
    };
}
static double shippingCost_after(String type, double weight) {
    return shippingFor(type).cost(weight);        // polymorphic dispatch, no conditional in the logic
}
```

In this example, `main` asserts `shippingCost_before(type, 100) == shippingCost_after(type, 100)` for every type in `{"standard", "express", "overnight"}` — behavior is provably identical before and after. The type-dispatch decision still exists somewhere (`shippingFor`'s `switch`), but it happens exactly *once*, at lookup time, centralizing the "which case applies" decision in a single factory method (Phase 3's Factory Method pattern) instead of embedding it directly in the cost-calculation logic. `shippingCost_after` itself contains no conditional at all — it just asks for the right `Shipping` strategy and calls `.cost(weight)` on it. Adding a fourth shipping type means adding one more `case` inside `shippingFor` — the calculation logic in `shippingCost_after` (and any other method that already uses `Shipping`) never changes.

### Other Refactorings Worth Knowing (names + one-liners)
- **Extract Class** — split a god class into two or more classes, each with one responsibility (the mechanical move behind fixing an SRP violation, Phase 2).
- **Move Method / Move Field** — relocate a method or field to the class it's actually most concerned with (the standard fix for feature envy).
- **Rename** — the highest-value, lowest-risk refactoring there is; a modern IDE makes renaming a symbol project-wide a single, safe, mechanical operation — there's rarely a good excuse to keep an unclear name once you've noticed it.
- **Introduce Parameter Object** — bundle a long, repeated group of parameters into one named type.
- **Inline** — the reverse of Extract Method/Class: fold an unnecessary indirection back into its caller when the extraction no longer earns its keep.
- **Replace Magic Number with Constant** — name a bare literal (Phase 1).
- **Encapsulate Field** — make a public field private and expose it through accessor methods, so future logic (validation, computed values) can be added later without breaking callers.

### The Refactoring Workflow — Safe, Small Steps

1. **Ensure the code is covered by tests** — write them first if they don't already exist. Without tests, "refactoring" is really just "changing code and hoping."
2. Make **one small** structural change — a single Extract Method, a single Rename, one step at a time.
3. **Run the tests** — they must stay green. If they don't, you know *immediately* which tiny change broke something, because you only changed one small thing.
4. **Commit**, then repeat from step 2.

**Never refactor and add a feature in the same step.** If you change structure *and* behavior at once and a test breaks, you have no way to know which of the two changes caused it — separating the two kinds of change is what keeps each one individually safe and easy to reason about. `Refactoring.java`'s design makes this concrete: every assertion is literally `before(...) == after(...)`, which is the workflow's core guarantee — refactor, then prove nothing observable changed — written directly as executable code instead of left as a claim you have to trust.

### Summary / Key Takeaways
- Extract Method: pull a named, single-purpose helper out of a long method.
- Replace Conditional with Polymorphism: turn a type-switch into strategies, dispatched once, so adding a case means adding code, not editing existing logic (Open/Closed).
- Tests first, one small change at a time, green after every step, never mix refactoring with new features.
- Rename is the cheapest, safest, most underused refactoring — do it liberally.

---

## 3. TDD & the Testing Pyramid

### Key Concepts
- **Tests are the safety net that makes refactoring possible at all** — without them, changing a working system's internal structure is a gamble on whether you preserved its behavior; with them, it's a checked, provable claim.
- **Untestable code is usually a symptom of bad design**, not an unrelated inconvenience — code that's hard to unit-test is very often tightly coupled, has hidden dependencies (reaching into a global Singleton, Phase 3), or is doing too many things at once (an SRP violation, Phase 2). Improving testability (extracting a class, injecting a dependency instead of constructing it internally) very often *is* the design improvement, not a separate task from it.
- **TDD (Test-Driven Development)**: **Red** — write a failing test for behavior that doesn't exist yet; **Green** — write the simplest code that makes it pass; **Refactor** — clean up the implementation while the tests stay green, confident nothing broke because you're re-running the same tests after every change. TDD drives small, focused, well-factored units almost as a side effect of the process, and gives you the refactoring safety net continuously, from the very first line of a feature.
- **The testing pyramid** (Java Phase 6.2 / Spring Boot Phase 6): many fast, cheap **unit** tests at the base; fewer, slower **integration** tests in the middle (verifying components work together, e.g., a real database); a handful of slow, expensive **end-to-end** tests at the top (verifying the whole system through its real interface). Test *behavior* through public APIs rather than internal implementation details, so a refactor — by definition, a change that preserves behavior — doesn't also break the tests that are supposed to be verifying that behavior was preserved.

### Why It's Useful
A test suite that verifies public, observable behavior (not internal structure) lets you refactor aggressively and confidently: if the tests still pass after you've restructured a class's internals, you have concrete, automated evidence that nothing observable changed — which is the entire refactoring workflow's foundation from Section 2.

### Summary / Key Takeaways
- Tests turn refactoring from "hope nothing broke" into "prove nothing broke."
- Hard-to-test code is a design smell in its own right, not just an inconvenience to work around.
- TDD: Red → Green → Refactor, repeated continuously.
- Test the pyramid's shape: many unit tests, fewer integration tests, a few end-to-end tests — and test through public APIs so refactors don't spuriously break tests.

---

## 4. Pragmatic Principles

### Technical Debt
The implied future cost of a shortcut — deliberate (a conscious trade-off to ship faster now) or accidental (cruft that accumulated without anyone deciding to accept it). Like financial debt, some strategic debt can genuinely be worth taking on deliberately, but unpaid "interest" — slower future changes, more bugs surfacing near the affected code, more onboarding friction for new team members — compounds over time if it's never addressed. Manage it by making it visible (track it explicitly, don't let it become invisible/forgotten), and pay it down continuously via the Boy Scout Rule (Phase 1) and regular, test-guarded refactoring rather than waiting for a dedicated (and often perpetually-deprioritized) "cleanup sprint."

### Code Review
A second pair of eyes catches smells the author is too close to the code to notice, spreads design knowledge and conventions across the team, and enforces a consistent standard without needing a rulebook nobody reads. Review for readability and design (SRP, coupling, naming, smells) in addition to raw correctness — and give feedback that's specific, kind, and actionable, focused on the code rather than the person who wrote it.

### When to Break the Rules
SOLID, the GoF patterns, and DRY are guidelines that serve readability and changeability — they are not ends in themselves. Over-applying any of them (needless abstraction, a factory that builds a single factory, forcibly DRY-ing two blocks of code that are only coincidentally similar) creates its own form of technical debt: complexity that exists for the pattern's own sake rather than to relieve any real, present pain. Apply a principle specifically when it relieves pain you can actually point to; otherwise keep it simple (KISS/YAGNI, Phase 1) until real complexity shows up and justifies the investment. Judgment over dogma, every time.

---

## The Through-Line of the Whole Track

Clean code (Phase 1) → SOLID (Phase 2) → design patterns (Phases 3–5) → architecture (Phase 6) → refactoring (Phase 7) all serve exactly one goal: **code that's easy to understand and easy to change.** Refactoring is *how you get there* starting from real, imperfect code — nobody writes a perfectly-designed system on the first pass — and tests are what make that journey safe rather than a gamble. The capstone applies all of it end-to-end: take a genuine god class, and refactor it step by step, each individual move removing one named smell and applying one specific principle or pattern from earlier phases, with every step guarded by tests proving behavior was preserved along the way.
