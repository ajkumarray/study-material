<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · architecture](../phase-6-architecture/NOTES.md)
<!-- /nav -->

# Phase 7 — Refactoring, Code Smells & Testing Discipline: Notes

**Refactoring** = changing code's *structure* without changing its *behavior*, to make it cleaner and easier to change. The discipline that keeps code clean over time (Phase 1) and lets you safely evolve a design toward SOLID/patterns (Phases 2–6).

## 7.1 — Code smells (symptoms that suggest refactoring)
A **smell** is a surface indication of a deeper design problem — not a bug, but a signal. The catalogue:
- **Long method** — does too much; hard to read/test. → Extract Method.
- **God class / Large class** — one class with many responsibilities and fields. → Extract Class, split by responsibility (SRP).
- **Long parameter list** — → introduce a parameter object.
- **Duplicated code** — the worst smell; logic drifts out of sync. → Extract and reuse (DRY).
- **Feature envy** — a method more interested in another class's data than its own. → Move Method to where the data lives.
- **Switch/if on type** — a conditional that grows with every new type. → Replace Conditional with Polymorphism (Strategy/State — Phases 2, 5).
- **Primitive obsession** — using primitives instead of small types (a `String` for money/email). → introduce a value object.
- **Shotgun surgery** — one change forces edits in many places. → gather the responsibility into one place (cohesion).
- **Data class / anemic domain** — data with no behavior; logic lives elsewhere. → move behavior onto the data.
- **Deep nesting, magic numbers, cryptic names, dead code, comments explaining bad code** (Phase 1).

Smells guide *where* to refactor; they're heuristics, not rules — use judgment.

## 7.2 — Refactoring techniques (safe structural changes)
The catalogue (Fowler). Common ones — the two runnable in `Refactoring.java`:
- **Extract Method** — pull a chunk of a long method into a named helper (the receipt example: split calc + discount + naming the magic number). Smaller, testable, DRY.
- **Replace Conditional with Polymorphism** — turn a type-switch into a strategy per type (the shipping example: `switch(type)` → a `Shipping` strategy). Adding a type becomes adding a class (Open/Closed) instead of editing the switch.
- Others: **Extract Class** (split a god class), **Move Method/Field**, **Rename** (the highest-value, safest refactor — IDEs make it mechanical), **Introduce Parameter Object**, **Inline** (the reverse of extract), **Replace Magic Number with Constant**, **Encapsulate Field**.

**The workflow — refactor in tiny, safe steps:**
1. Ensure the code is covered by **tests** (write them first if not).
2. Make **one small** structural change.
3. Run the tests → **green**.
4. Commit. Repeat.
**Never refactor and add features in the same step** — you won't know which broke a test. The `Refactoring.java` demo makes this concrete: the tests assert `before == after`, proving each structural change preserved behavior.

## 7.3 — TDD & the testing pyramid
- **Tests are the safety net that makes refactoring possible** — without them, changing structure is gambling. Untestable code is usually badly-designed code, so writing tests *improves* the design (tight coupling and hidden dependencies make tests painful — a design smell).
- **TDD (Test-Driven Development):** Red (write a failing test) → Green (make it pass simply) → Refactor (clean up under green). It drives small, testable units and gives you the refactoring safety net for free.
- **The testing pyramid** (Java Phase 6.2 / Spring Boot 6): many fast **unit** tests, fewer **integration** tests, a few slow **end-to-end** tests. Test behavior through public APIs, not implementation, so refactoring doesn't break tests.

## 7.4 — Pragmatic principles
- **Technical debt** — the cost of shortcuts (deliberate or accidental). Like financial debt, a little strategic debt can be worth it, but unpaid interest (harder changes, more bugs) compounds. Track it, and pay it down via continuous refactoring (the Boy Scout Rule).
- **Code review** — a second pair of eyes catches smells, spreads knowledge, and enforces standards. Review for readability and design, not just correctness; be kind and specific.
- **When to break the rules** — SOLID/patterns/DRY are guidelines serving readability and changeability, not ends in themselves. Over-applying them (needless abstraction, a factory for a factory) creates its own debt. Apply a principle to relieve real pain; keep it simple (KISS/YAGNI) until complexity is justified. Judgment over dogma.

## The through-line of the whole track
Clean code (1) → SOLID (2) → patterns (3–5) → architecture (6) → refactoring (7) all serve one goal: **code that's easy to understand and change.** Refactoring is how you *get* there from imperfect code, and tests are what make it safe. The capstone applies all of it: take a god class, and refactor it step by step — each move removing a smell and applying a principle/pattern, guarded by tests.
