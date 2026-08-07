<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · clean code](../phase-1-clean-code/NOTES.md) | [Phase 3 · creational ➡](../phase-3-creational/NOTES.md)
<!-- /nav -->

# Phase 2 — SOLID: Interview Q&A

⭐ = asked constantly. SOLID is near-guaranteed for mid/senior interviews.

**Q: What does SOLID stand for?** ⭐⭐
Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion — five OO design principles for maintainable, extensible, testable code. Be ready with a one-line definition AND a code example for each.

**Q: Single Responsibility Principle — with an example.** ⭐⭐
A class should have one reason to change. Example: don't put pay calculation, database persistence, and payslip formatting in one `Employee` class — a tax change, a DB migration, and a report redesign are three different reasons to change and belong in three classes. Benefit: localized, safe changes and independent testing.

**Q: Open/Closed Principle — how do you achieve it?** ⭐⭐
Open for extension, closed for modification: add features by adding code, not editing tested code. Achieved with polymorphism/abstraction — e.g., a `Shape` interface so adding `Triangle` needs no change to `AreaCalculator`. Replaces growing if/else type-switches. Realized via Strategy pattern and plugin designs.

**Q: Liskov Substitution Principle — give the classic violation.** ⭐⭐
Subtypes must be usable anywhere the base type is, without breaking behavior. Classic violation: `Square extends Rectangle` — overriding setters to keep sides equal breaks code that sets width and height independently (expects area 20, gets 16). Fix: don't force a false IS-A; use a shared abstraction and composition.

**Q: What must a subtype preserve for LSP?**
Preconditions no stronger, postconditions no weaker, invariants maintained, no new unexpected exceptions. Smell test: if you override a method to throw `UnsupportedOperationException` or need `instanceof` checks on subtypes, you're likely violating LSP.

**Q: Interface Segregation Principle — example.** ⭐
Don't force clients to depend on methods they don't use; prefer small role interfaces. A fat `Worker { work(); eat(); }` forces a `Robot` to implement `eat()` (throwing). Split into `Workable` and `Eatable`. It's SRP for interfaces, and it prevents the throwing-stub that also breaks LSP.

**Q: Dependency Inversion Principle — what does it invert?** ⭐⭐
High-level modules shouldn't depend on low-level modules; both depend on abstractions. It inverts the *direction of the source-code dependency*: instead of a service depending on a concrete `EmailSender`, both depend on a `MessageSender` interface (owned by the high-level side), and the concrete class is injected. Enables swapping and testing.

**Q: DIP vs Dependency Injection vs IoC?** ⭐ *frequently confused*
DIP is the design *principle* (depend on abstractions). Dependency Injection is a *technique* for providing dependencies from outside (constructor/setter/field). Inversion of Control is the broader idea of the framework controlling flow/wiring; an IoC container (Spring) automates DI. DIP is the "why," DI is the "how," IoC container is the "who does it for you."

**Q: How does SOLID relate to design patterns?**
Patterns are concrete implementations of these principles: Strategy/Template Method → OCP; Adapter/Facade → decoupling; Factory/Builder → SRP + DIP; Observer → OCP. SOLID is the "why," patterns are proven "how"s (Phases 3–5).

**Q: Can you over-apply SOLID?** *senior nuance*
Yes — excessive abstraction adds indirection and cognitive load (a factory for a factory, an interface with one implementation forever). Apply a principle to relieve real pain (rigidity, fragility, untestability), guided by YAGNI. Judgment, not dogma.

**Q: How does SOLID make code more testable?**
SRP yields small units to test in isolation; DIP lets you inject mocks/fakes instead of real collaborators (no DB/network in unit tests); OCP/LSP keep behavior predictable under substitution. Testability is often the fastest signal that a design follows SOLID.
