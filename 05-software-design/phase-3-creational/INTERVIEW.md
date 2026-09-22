<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · solid](../phase-2-solid/NOTES.md) | [Phase 4 · structural ➡](../phase-4-structural/NOTES.md)
<!-- /nav -->

# Phase 3 — Creational Patterns: Interview Q&A

⭐ = asked constantly.

**Q: What are design patterns, and why use them?** ⭐⭐
Named, reusable solutions to recurring object-oriented design problems, catalogued by the "Gang of Four." Their value is threefold: they give engineers a shared vocabulary ("just use a Strategy here" communicates a whole design in three words instead of a paragraph), they're proven structures that have already worked for the problem you're facing, and most of them directly embody the SOLID principles — so applying a pattern is often a fast, well-tested route to satisfying Open/Closed or Dependency Inversion rather than deriving a solution from first principles each time. They're guidance, not mandates — misapplied to a problem that doesn't need them, they add indirection without payoff (see the "aren't patterns just lambdas now" question below).

**Q: What are the three pattern categories, and what does each address?**
Creational (Factory Method, Abstract Factory, Builder, Singleton, Prototype) address *how objects are created*, decoupling client code from the concrete classes it instantiates. Structural (Adapter, Decorator, Facade, Proxy, Composite, Bridge, Flyweight — Phase 4) address *how objects and classes are composed* into larger structures. Behavioral (Strategy, Observer, Command, Template Method, State, Chain of Responsibility, Iterator, Visitor, Mediator, Memento — Phase 5) address *how objects communicate and distribute responsibility*.

**Q: How do you implement a singleton in Java, and what's the catch?** ⭐⭐
The best implementation is an `enum` with a single constant, `enum Config { INSTANCE; ... }` — the JVM's class-initialization guarantees make it thread-safe and lazy (created on first reference) with no manual synchronization, and it's immune to the reflection and serialization attacks that can break a naively hand-rolled Singleton (a private constructor plus a static field can still be instantiated a second time via reflection, or produce a second instance on deserialization, unless you write extra defensive code — the enum form sidesteps both issues entirely). The alternatives are an eager `static final` field (simplest, but pays the construction cost even if the instance is never used) or lazy initialization with double-checked locking and a `volatile` field (correct, but easy to get subtly wrong on the memory-visibility details).

The catch: Singleton is frequently an anti-pattern in practice — it's a global variable wearing a design-pattern name. It hides a class's real dependencies (nothing in a method's signature reveals that it silently reaches into `Config.INSTANCE`), and it makes unit testing painful, since you can't easily substitute a test double for something reached through a global static reference. The modern fix is dependency injection: a Spring `@Service` bean is *effectively* a managed singleton (one instance per application context, by default), but it's explicitly *injected* into whatever needs it — visible in the constructor signature, and trivially replaceable with a mock in tests.

*Follow-up: "Why is the enum singleton serialization-safe when a normal class isn't?"* Java's enum deserialization is handled specially by the JVM — it resolves to the existing enum constant by name rather than invoking a constructor, so you can never end up with two `INSTANCE`s from deserializing the same enum twice. A hand-written Singleton class needs to explicitly implement `readResolve()` to get the same guarantee.

**Q: Factory Method vs. Abstract Factory?** ⭐⭐
Factory Method centers on one creation method producing one product, returned through an abstraction — it decouples the client from the concrete class being instantiated (`ShapeFactory.create("circle")` returns a `Shape`, never exposing `Circle` to the caller). Abstract Factory scales this up to *families* of related products: one factory interface with several creation methods (`GuiFactory.button()`, `GuiFactory.checkbox()`), where a single concrete factory implementation (`DarkFactory`) guarantees every product it returns belongs to the same consistent family — you can't accidentally get a dark button paired with a light checkbox as long as you consistently use one factory instance. Abstract Factory is Factory Method generalized to a *set* of products that must stay consistent with each other.

```java
interface GuiFactory { Button button(); Checkbox checkbox(); }
class DarkFactory implements GuiFactory { ... }   // button() and checkbox() always match
```

**Q: When do you use the Builder pattern?** ⭐⭐
When an object has many parameters — especially several *optional* ones — where a positional constructor would either need many overloads (telescoping constructors) or force callers to pass `null`/default placeholders for parts they don't want, with no clarity about which positional argument means what. Builder replaces that with a chainable, named API: `new Pizza.Builder("medium").cheese().topping("olive").build()`. Required arguments go on the builder's own constructor; optional ones become chainable methods with sensible defaults. The result is typically immutable and only obtainable through `.build()`, so there's no way to end up holding a half-configured object. Real examples: `StringBuilder`, `HttpRequest.newBuilder()`, Lombok's `@Builder`.

*Follow-up: "Why not just use a record with all fields?"* A record works well when most fields are required and there are few of them — it's simpler and needs no extra Builder class. Reach for Builder specifically when several fields are optional (so a record constructor would force you to pass placeholder values for the ones you don't want) or when construction genuinely happens incrementally across several steps.

**Q: What problem does the Prototype pattern solve?**
Creating new objects by cloning an existing "prototype" instead of building one from scratch — worthwhile when construction is expensive (parsing, heavy computation) or when you want several variants of an already-configured baseline object. The critical implementation detail is shallow vs. deep copy: naively copying an object's fields also copies *references* to any mutable fields (like a `List`), so the "copy" ends up silently sharing that mutable state with the original — mutating one corrupts the other. A correct Prototype implementation defensively copies mutable fields, as `Document`'s constructor does by wrapping `sections` in `new ArrayList<>(sections)`.

```java
Document copy() { return new Document(title, sections); }  // constructor defensively copies sections
```

**Q: Aren't many creational patterns just lambdas or records now in modern Java?** *nuance*
Yes, for several of them the ceremony has largely dissolved into language features while the underlying idea survives: a simple Factory Method is often just a static method or a method reference; a Strategy (Phase 5) — arguably a creational/behavioral crossover — is frequently a plain lambda; simple Builders give way to records once there's no telescoping-constructor problem to solve; DI containers replace hand-rolled Singletons entirely. What doesn't disappear is the *design vocabulary* — recognizing "this is a Factory Method" or "this needs an Abstract Factory because these two things must stay consistent" still matters for design conversations and for reading unfamiliar codebases and frameworks, even when the concrete syntax is three lines instead of thirty.

**Q: How do creational patterns relate to Dependency Inversion?**
They centralize and abstract object creation so high-level code depends on interfaces rather than concrete classes — a factory returns an abstraction (`Shape`, `MessageSender`), and the decision of *which* concrete implementation to instantiate lives in exactly one place (the factory), or is delegated to a DI container entirely. This is Dependency Inversion applied specifically to the *creation* step: the client is open to new implementations (Open/Closed) without ever needing to be modified, because it never named a concrete class to begin with.

**Q: Can you give a real-world example where you'd reach for a factory?**
Selecting a payment gateway client at runtime based on configuration — `PaymentGatewayFactory.create(config.getProvider())` returning a `PaymentGateway` interface, backed by `StripeClient` or `RazorpayClient` depending on environment — so the rest of the application only ever depends on `PaymentGateway`. Other common cases: parsing different file formats (CSV, JSON, XML) into a common internal model behind one factory; instantiating the right notification channel (email/SMS/push) based on a user's preference. The common thread is always: the concrete type is chosen dynamically (config, user input, environment), but every caller downstream should stay decoupled from which concrete type was actually picked.

**Q: What's the difference between Singleton and a Spring-managed `@Service` bean, given both are "one instance"?**
Both typically result in exactly one instance existing at runtime, but the *access model* differs completely. A classic Singleton is reached globally (`Config.INSTANCE`) — any code anywhere can grab it directly, which hides the dependency and makes it hard to substitute in a test. A Spring bean's "singleton scope" instance is *injected* into whatever constructor declares it as a parameter — the dependency is visible in the class's own signature, and Spring's test support makes it trivial to inject a mock instead for a unit test. Same cardinality (one instance), very different coupling and testability characteristics.
