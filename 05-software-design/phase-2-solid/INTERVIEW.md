<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · clean code](../phase-1-clean-code/NOTES.md) | [Phase 3 · creational ➡](../phase-3-creational/NOTES.md)
<!-- /nav -->

# Phase 2 — SOLID: Interview Q&A

⭐ = asked constantly. SOLID is near-guaranteed for mid/senior interviews — expect at least one live "spot the violation" or "design this class" question.

**Q: What does SOLID stand for?** ⭐⭐
Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion — five object-oriented design principles (Robert C. Martin) aimed at producing code that's easy to maintain, extend, and test. Interviewers expect more than the expansion: be ready with a one-line definition *and* a concrete code example for each, since "list the letters" alone signals memorization without understanding.

**Q: Single Responsibility Principle — explain it with an example.** ⭐⭐
A class should have one, and only one, reason to change — where "reason to change" means an axis of change, not a method count. The canonical violation: an `Employee` class that does pay calculation, database persistence, *and* payslip formatting all at once. A tax-law change, a database migration, and a report redesign are three unrelated reasons to change, and all three would touch the same class, risking that a fix to one breaks another. The fix splits it into `PayrollCalculator`, an `EmployeeRepository` implementation, and a `PayslipFormatter`, with `Employee` reduced to a plain data holder.

```java
record Employee(String name, double grossSalary, double taxRate) { }
class PayrollCalculator { double netPay(Employee e) { return e.grossSalary() * (1 - e.taxRate()); } }
interface EmployeeRepository { void save(Employee e); }
class PayslipFormatter { String format(Employee e, double net) { ... } }
```

*Follow-up: "Is SRP just 'one method per class'?"* No — a class can have several methods and still satisfy SRP, as long as every method serves the same single responsibility (e.g., `PayrollCalculator` could gain a `grossToNet` helper alongside `netPay` and still be SRP-compliant, since both serve pay calculation).

**Q: Open/Closed Principle — how do you actually achieve it?** ⭐⭐
Open for extension, closed for modification: you should be able to add new behavior by *adding* code, not by *editing* existing, already-tested code. It's achieved through polymorphism — depend on an abstraction (a `Shape` interface with `area()`), and let new concrete types (`Triangle`) extend it. `AreaCalculator.totalArea` never changes when a new shape is added; it just calls `Shape::area` and lets dynamic dispatch pick the right implementation. This directly replaces a growing `if (shape instanceof Circle) ... else if (shape instanceof Rectangle) ...` chain, which has to be edited — and re-tested for regressions in every existing branch — every time a new type appears.

*Follow-up: "What's a real-world pattern that implements OCP?"* The Strategy pattern (Phase 5) is the cleanest example — injecting a new algorithm implementation requires zero changes to the class that uses it. Plugin architectures and Spring's component-scanning `@Component` discovery are OCP at the framework level.

**Q: Liskov Substitution Principle — give the classic violation.** ⭐⭐
Subtypes must be usable anywhere the base type is expected, without the caller needing to know or care which subtype it actually got. The textbook violation is `Square extends Rectangle`: overriding `setWidth`/`setHeight` to keep both sides equal breaks code written correctly against `Rectangle`, which assumes width and height vary independently. `Rectangle r = new Square(); r.setWidth(5); r.setHeight(4);` — a caller reasonably expects `area() == 20`, but gets `16`, because `Square`'s `setHeight` silently also overwrote `width`. The caller isn't wrong; `Square` broke `Rectangle`'s behavioral contract even though it compiles as a valid subtype. The fix is to not force a false IS-A relationship — model both as independent implementations of a shared `Shape` abstraction instead of one inheriting from the other.

```java
Rectangle r = new Square();
r.setWidth(5);
r.setHeight(4);
// expected area 20, got 16 — LSP violated
```

*Follow-up: "What must a subtype preserve to satisfy LSP?"* Preconditions no stronger than the base (don't demand more of callers), postconditions no weaker (deliver at least what the base promised), invariants preserved, and no new, unexpected exceptions the base type didn't already throw. The practical smell test: if you override a method to throw `UnsupportedOperationException` or silently no-op, or if client code needs `instanceof` checks to special-case a specific subtype, LSP is very likely being violated.

**Q: Interface Segregation Principle — example.** ⭐
No client should be forced to depend on methods it doesn't use — prefer many small, role-specific interfaces over one fat one. A fat `Worker { work(); eat(); }` forces `Robot` to implement `eat()`, and since robots don't eat, that usually means `eat()` throws `UnsupportedOperationException`. Splitting into `Workable { work(); }` and `Eatable { eat(); }` lets `Robot implements Workable` alone — no stub, no throwing method, and code that only needs "things that can eat" depends on the narrow `Eatable` interface, so a `Robot` simply can't be passed where an `Eatable` is expected (a compile-time guarantee instead of a runtime surprise).

*Follow-up: "How does ISP relate to LSP?"* A throwing/no-op stub forced by a fat interface is simultaneously an ISP violation (forced to implement something irrelevant) and an LSP violation (a caller holding the fat interface type can no longer trust that calling any of its methods is safe). Fixing ISP by splitting the interface often fixes the LSP problem as a side effect, since the offending stub method disappears entirely for implementers that don't need it.

**Q: Dependency Inversion Principle — what does it actually invert?** ⭐⭐
High-level modules shouldn't depend on low-level modules; both should depend on abstractions. What's "inverted" is the *direction of the source-code dependency*: normally you'd expect a `NotificationService` to depend on (import, construct) a concrete `EmailSender`. DIP flips that — both `NotificationService` and `EmailSender` depend on a `MessageSender` interface that the high-level module effectively owns, and the concrete `EmailSender` is *injected* into `NotificationService` rather than constructed inside it. This is what makes it possible to swap email for Slack or SMS, and to unit-test the service with a fake sender, without ever touching `NotificationService`'s source.

```java
interface MessageSender { String send(String message); }
class NotificationService {
    private final MessageSender sender;
    NotificationService(MessageSender sender) { this.sender = sender; }  // injected
}
```

**Q: DIP vs. Dependency Injection vs. IoC?** ⭐⭐ *frequently confused — expect this as a direct follow-up*
DIP is the design *principle*: depend on abstractions, not concrete low-level details. Dependency Injection is a *technique* implementing that principle: a dependency is supplied from outside the class (via constructor, setter, or field) instead of the class constructing it internally. Inversion of Control is the *broader idea* that a framework, not your own code, controls object creation and wiring — "the framework calls you" instead of the reverse. An IoC container (Spring) is a concrete tool that *automates* DI: it scans for components, constructs them, and wires their dependencies for you. Short version: DIP is the *why*, DI is the *how*, an IoC container is *who does it for you*.

**Q: How does SOLID relate to the design patterns you'll cover next?**
The GoF patterns are, in large part, concrete, named implementations of these principles. Strategy and Template Method directly realize Open/Closed (swap an algorithm without editing the caller). Adapter and Facade reduce coupling, serving Dependency Inversion's spirit of depending on a stable abstraction rather than a volatile concrete API. Factory Method and Abstract Factory (Phase 3) centralize object creation behind an abstraction, which is SRP (creation is its own responsibility) combined with DIP (the caller depends on the returned interface, not the concrete class). Observer is Open/Closed applied to notifications — new subscribers don't require changing the publisher. SOLID answers "why," the patterns are proven "how"s.

**Q: Can you over-apply SOLID?** *senior nuance*
Yes, and it's a common failure mode for engineers who've just learned the principles. Excessive abstraction adds indirection and cognitive load with no corresponding payoff — a factory that builds a factory, an interface that will only ever have one implementation, dependency injection wired through five layers for a value that never actually varies. Apply a principle when you feel the specific pain it's designed to relieve (rigidity that blocks an actual upcoming requirement, fragility where an unrelated change breaks when you touch one class, or code you genuinely can't unit-test in isolation) — not preemptively, on every class, by default. This is YAGNI (Phase 1) applied to SOLID itself, and it's exactly the kind of judgment senior interviewers are probing for when they ask this question.

**Q: How does SOLID make code more testable?**
Each letter contributes a different piece: SRP yields small, focused units with a single job, which are trivial to test in isolation without dragging in unrelated concerns. DIP lets you inject a fake or mock collaborator (a `FakeSender` instead of a real `EmailSender`) instead of the class under test reaching out to a real database, network, or file system. OCP and LSP keep behavior *predictable* under substitution — a test written against an abstraction stays valid no matter which concrete implementation is plugged in, and a well-behaved subtype won't silently break assumptions the test relies on. In practice, testability is often the fastest, most concrete signal that a design already follows SOLID reasonably well — and conversely, code that's painful to unit-test is usually a symptom of a SOLID violation (frequently SRP or DIP) rather than "the tests are just hard to write."

**Q: If you inherited a class that violates several SOLID principles at once, how would you prioritize fixing it?**
Start with SRP, since it's foundational — splitting a god class by responsibility usually simplifies (or entirely resolves) the other violations riding on top of it, because LSP and ISP problems often stem from a class trying to be too many things at once. From there, DIP is typically the next highest-leverage fix, since injecting abstractions is what unlocks unit testing for the newly-split pieces and lets you verify each subsequent refactor is behavior-preserving as you go (Phase 7's refactoring discipline). OCP and LSP fixes tend to fall out naturally once responsibilities are cleanly separated and dependencies are properly abstracted, rather than needing to be tackled head-on as separate steps.
