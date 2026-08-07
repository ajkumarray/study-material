# Software Design Capstone — Refactoring a God Class

The finale that ties the whole track together: take a deliberately awful **god
class** and refactor it into a clean design applying **clean code + SOLID +
patterns + architecture**, with **tests guarding every step** so behavior is
provably preserved. Runnable: `java -ea OrderRefactor.java` (green).

## The "before" — `GodOrderProcessor`
One method (`process`) does *everything*: compute the subtotal, apply
tier-based discounts via a `switch`, add tax, "persist," "notify," and format —
with magic numbers throughout. It's a textbook accumulation of smells and
violations.

## The refactor — step by step, each removing a smell / applying a principle

| # | Smell (before) | Refactoring | Principle / pattern applied |
|---|----------------|-------------|-----------------------------|
| 1 | **God class** — many responsibilities, many reasons to change | **Extract Class** into `PricingService`, `DiscountPolicy`, `TaxCalculator`, `OrderRepository`, `Notifier` | **SRP** (each class, one job) |
| 2 | **Switch-on-type** — adding a tier edits the method | **Replace Conditional with Polymorphism** — a `DiscountPolicy` (strategy/lambda) per tier, one factory point | **OCP** + **Strategy** (Phase 5) |
| 3 | **Tangled side effects** — pricing can't be tested without DB/email | Persistence & notification behind **ports** (interfaces), **injected** | **DIP** + **hexagonal** (Phase 6) |
| 4 | **Magic numbers** (`0.18`, `0.90`) | Named constant `TAX_RATE`; discount rates in their strategies | **Clean code** (Phase 1) |
| 5 | **Untestable** — one big method, hidden side effects | Each collaborator tested in isolation with **fakes**; the orchestrator composed | **Testability** / testing pyramid (Phase 7) |

## The safety net (Phase 7)
The tests assert **`godClass.process(...) == refactored(...)`** for multiple
customer tiers. Because they pass for *both* the before and after, every
structural change is proven to preserve behavior — the essence of refactoring.
(The refactored test also asserts the side effect — a save — happened exactly
once, via an injected fake repository.)

## The "after" — the clean design
```
OrderService (orchestrator, small & readable)
  ├── PricingService          (SRP: subtotal)
  ├── DiscountPolicy          (Strategy/OCP: one per tier, add without editing)
  ├── TaxCalculator           (SRP: tax)
  ├── OrderRepository  (port) (DIP: injected; fake in tests, JDBC in prod)
  └── Notifier         (port) (DIP: injected)
```
Adding a `platinum` tier or a new tax rule is now a **new small class**, not an
edit to a mega-method. Each piece is independently testable. The domain logic
doesn't know about databases or email.

## The through-line of the whole track
**Clean code (P1) → SOLID (P2) → patterns (P3–5) → architecture (P6) →
refactoring (P7)** all serve one goal: *code that's easy to understand and
change.* This capstone is the proof — starting from imperfect code, small
test-guarded refactorings move it there, each step applying a principle you
learned. And it echoes what you built earlier: the layered structure is your
Spring app, the injected ports are the Java capstone's repository interface, the
strategy-per-tier is the lambdas-as-behavior you used all through the functional
phases. You were already doing this — now it's named, systematic, and provable.
