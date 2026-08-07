<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · clean code](../phase-1-clean-code/NOTES.md) | [Phase 3 · creational ➡](../phase-3-creational/NOTES.md)
<!-- /nav -->

# Phase 2 — The SOLID Principles: Notes

Five principles (Robert C. Martin) for object-oriented design that produce code which is **easy to change, test, and extend**. They're not rules to apply mechanically — they're pressures that push toward loose coupling and high cohesion. Each has a runnable before→after in this folder.

## S — Single Responsibility Principle
**"A class should have one reason to change."** Group by *axis of change*: pay-policy logic, persistence, and formatting each change for different reasons and belong in different classes. A "god class" doing all three must be edited for any of them, and those edits risk breaking the others. SRP payoff: localized changes, independent testability, reuse. It's the foundation the other four build on. (Applies to methods and modules too — one job each.)

## O — Open/Closed Principle
**"Open for extension, closed for modification."** Add new behavior by adding code, not editing tested code. The enabler is **polymorphism**: depend on an abstraction (`Shape`), and new subtypes extend it without touching the consumer (`AreaCalculator`). The smell it removes: a growing `if/else`/`switch` on a type tag that you edit for every new case. This is your Java Phase 2.3 `Shape.area()` lesson stated as a principle; realized via Strategy pattern, plugins, Spring `@Component` discovery. Balance with YAGNI — don't abstract for variation that never arrives.

## L — Liskov Substitution Principle
**"Subtypes must be substitutable for their base type without breaking correctness."** Inheritance is a *behavioral* promise, not just structural. A subtype must: not strengthen preconditions, not weaken postconditions, preserve invariants, and not throw exceptions the base didn't. The canonical violation (demonstrated live — area 16 where 20 was expected): `Square extends Rectangle` overriding setters to keep sides equal breaks callers that rely on width/height varying independently (Java Phase 2.3 flagged this exact trap). Smells: overriding a method to throw `UnsupportedOperationException` or do nothing, and `if (x instanceof Subtype)` special-casing. Fix: model true abstractions and prefer **composition over inheritance**.

## I — Interface Segregation Principle
**"No client should be forced to depend on methods it doesn't use."** Prefer many small **role interfaces** over one fat interface. A fat `Worker { work(); eat(); }` forces `Robot` to implement `eat()` (usually by throwing — which also breaks LSP). Split into `Workable` and `Eatable`; each implementer takes only the roles that apply, and each client depends only on the narrow role it needs. ISP is SRP applied to interfaces. Real-world: `Runnable` vs `Callable`; Spring's focused `*Repository`/`*Aware` interfaces.

## D — Dependency Inversion Principle
**"High-level modules and low-level modules should both depend on abstractions, not on each other."** A business policy that `new`s a concrete `EmailSender` is welded to it — unswappable and untestable. Invert it: define an interface (`MessageSender`) that the *high-level* module owns, have details implement it, and **inject** the concrete choice (constructor injection). Now you swap email→Slack→SMS and test with a fake, all without touching the policy.

**DIP vs DI vs IoC** (a frequent point of confusion): **DIP** is the *principle* (depend on abstractions); **Dependency Injection** is a *technique* for supplying dependencies from outside; an **IoC container** (Spring) *automates* that wiring. Your Java capstone's `ExpenseService → ExpenseRepository (interface)` did DIP by hand; Spring's `@Service` + constructor injection (Spring Boot Phase 3) is the same principle, framework-wired.

## Perspective
SOLID isn't dogma — over-applying it creates needless indirection (a factory for a factory). The goal is **manageable change**: apply a principle when you feel the pain it prevents (rigid code that resists a new requirement, fragile code that breaks elsewhere when touched, code you can't unit-test). SOLID sits alongside the broader toolkit: DRY, KISS, YAGNI, high cohesion / low coupling, composition over inheritance, and the design patterns (Phases 3–5) that are concrete embodiments of these principles.
