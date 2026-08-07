<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · solid](../phase-2-solid/NOTES.md) | [Phase 4 · structural ➡](../phase-4-structural/NOTES.md)
<!-- /nav -->

# Phase 3 — Creational Patterns: Interview Q&A

⭐ = asked constantly.

**Q: What are design patterns and why use them?** ⭐
Named, reusable solutions to recurring design problems (Gang of Four). They provide a shared vocabulary, proven structures, and embody principles like SOLID — making designs easier to communicate and extend. They're guidance, not mandates; misapplied, they add needless complexity.

**Q: What are the three pattern categories?**
Creational (object creation — Factory, Builder, Singleton, Prototype, Abstract Factory), Structural (composition — Adapter, Decorator, Facade, Proxy, Composite, Bridge, Flyweight), Behavioral (communication/responsibility — Strategy, Observer, Command, Template Method, State, Chain of Responsibility, Iterator, Visitor, Mediator, Memento).

**Q: How do you implement a singleton in Java, and what's the catch?** ⭐⭐
Best: an enum with a single `INSTANCE` — thread-safe, serialization-safe, lazy. (Alternatives: eager static final, or lazy with double-checked locking + volatile.) The catch: singletons are often anti-patterns — global state that hides dependencies and hurts testability. Prefer dependency injection (Spring manages singleton beans for you), so the instance is injected rather than globally fetched.

**Q: Factory Method vs Abstract Factory?** ⭐
Factory Method: one method creates one product, returning an abstraction — decouples the client from concrete classes. Abstract Factory: creates *families* of related products (a matching set) via one factory interface with multiple creation methods — guarantees the products go together. Abstract Factory is Factory Method scaled to product families.

**Q: When do you use the Builder pattern?** ⭐⭐
When an object has many parameters, especially optional ones — the Builder gives a readable, chainable construction (`new X.Builder().a().b().build()`) and avoids telescoping constructors and invalid partial states, often producing an immutable object. Examples: `StringBuilder`, HTTP request builders, Lombok `@Builder`.

**Q: What problem does the Prototype pattern solve?**
Creating new objects by cloning an existing one, when construction is expensive or you want a copy of a preconfigured object. Watch shallow vs deep copy — clone mutable nested state defensively or the copies share references.

**Q: Aren't many patterns just lambdas now in modern Java?** *nuance*
Yes for several — Strategy and Factory Method are often a functional interface/method reference; simple Builders give way to records; DI containers replace hand-rolled Singletons. The underlying ideas remain valuable; the boilerplate largely disappears. Recognizing the pattern still matters for design conversations.

**Q: How do creational patterns relate to Dependency Inversion?**
They centralize/abstract object creation so high-level code depends on interfaces, not concretions — a factory returns an abstraction, and the concrete choice is made in one place (or injected). This is DIP in action, keeping the client open to new implementations without modification (Open/Closed).

**Q: Can you give a real-world example where you'd use a factory?**
Creating a payment gateway client based on config (`StripeClient` vs `RazorpayClient`) behind a `PaymentGateway` interface; parsing different file formats into a common model; instantiating the right `Notification` channel (email/SMS/push). Anywhere the concrete type is chosen at runtime but callers should stay decoupled.
