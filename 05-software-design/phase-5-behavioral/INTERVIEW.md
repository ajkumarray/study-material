<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · structural](../phase-4-structural/NOTES.md) | [Phase 6 · architecture ➡](../phase-6-architecture/NOTES.md)
<!-- /nav -->

# Phase 5 — Behavioral Patterns: Interview Q&A

⭐ = asked constantly.

**Q: What is the Strategy pattern?** ⭐⭐
Defining a family of interchangeable algorithms behind a common interface and choosing (or injecting) one at runtime, so the client is fully decoupled from the specific algorithm and new ones can be added without ever modifying the client — a direct instance of Open/Closed. `Cart` holds a `DiscountStrategy` and calls `discount.apply(amount)` without knowing or caring whether that's a `PercentDiscount`, a `FlatDiscount`, or a no-op lambda. In Java, a strategy is frequently *just* a lambda or method reference rather than a named class — passing a `Comparator`, a `Function`, or a bare `amount -> amount` to a constructor already *is* the Strategy pattern, even with zero boilerplate.

```java
interface DiscountStrategy { double apply(double amount); }
class Cart {
    private final DiscountStrategy discount;
    Cart(DiscountStrategy discount) { this.discount = discount; }   // injected
    double total(double amount) { return discount.apply(amount); }
}
```

**Q: Strategy vs. Template Method?** ⭐⭐
Both let an algorithm vary, but through opposite mechanisms. Strategy uses composition — a strategy object or lambda is injected from outside, so it's swappable per-instance and at runtime; it shines when the algorithm genuinely varies freely (pricing rules, comparators). Template Method uses inheritance — a base class fixes the overall *sequence* of an algorithm in a (usually `final`) method, and subclasses override only specific steps within that fixed sequence; it shines when the overall flow is genuinely constant and shouldn't be reorderable, but one or two steps legitimately differ (`Report.generate()` always does header → body → footer; `CsvReport` only supplies `body()`). Modern Java code leans toward Strategy because functional interfaces make it essentially free — no subclass, no abstract method, just a lambda.

*Follow-up: "Could `Report` have been written with Strategy instead of Template Method?"* Yes — you could inject a `BodyProducer` strategy into a single `Report` class instead of subclassing. The trade-off: Template Method guarantees, at the type-system level, that `header()` and `footer()` are always called in the right order around whatever `body()` does, since `generate()` itself is `final` and not overridable; a Strategy-based rewrite would need to trust that whatever assembles the final report string calls the pieces correctly, since there's no single fixed method enforcing the sequence.

**Q: What is the Observer pattern and where do you see it?** ⭐⭐
A subject maintains a list of observers and notifies all of them when relevant state changes, without knowing their concrete types — a one-to-many, publish/subscribe relationship that decouples the event source from whatever reacts to it. `Channel.publish(msg)` calls `onMessage` on every registered `Subscriber`, and `Channel` has no idea what any given subscriber actually does with the message. Real examples: JDK/GUI event listeners, the DOM's `addEventListener`, reactive streams, and message/event buses at the distributed-systems scale (System Design Phase 9) — Observer at increasing granularity, from a single button click up to services publishing events across a network.

**Q: What is the Command pattern good for?** ⭐
Encapsulating a request — an action plus its data — as an object with an `execute()` method (and typically `undo()`), which makes it possible to queue, log, and reverse operations that a plain, direct method call has no way to represent afterward. `CommandHistory.execute(command)` both runs the command and pushes it onto a stack; `undo()` pops the most recently executed command and calls its own `undo()`, reversing exactly that one action and leaving earlier ones untouched. Used for GUI actions (every button/menu click is often a Command object), task queues and schedulers, transaction logs, and macro recording (record a sequence of Commands, replay them later).

```java
history.execute(new TypeCommand(editor, "hello "));
history.execute(new TypeCommand(editor, "world"));   // editor.text == "hello world"
history.undo();                                       // reverses ONLY the last command -> "hello "
```

**Q: What is the State pattern, and what does it replace?** ⭐
An object delegates its behavior to a state object and transitions between different state objects as its lifecycle progresses, so its observable behavior changes with its current state — without a giant conditional. It replaces a sprawling `if (status == DRAFT) ... else if (status == REVIEW) ...` scattered across every method that cares about status with one small class per state, each responsible only for its own behavior and its own outgoing transitions. `Doc.publishAction()` delegates to whichever `DocState` object is currently set; `Draft.publish` transitions `Doc` to a `Review` state object, and the *same* `doc.publishAction()` call now behaves differently the second time it's invoked, purely because the underlying state object changed. Adding a new state (`Archived`) means adding a new class, not editing the existing ones (Open/Closed) — it implements a state machine.

**Q: Where is Chain of Responsibility used in real frameworks?** ⭐⭐
Passing a request through a sequence of handlers until one of them handles it (or rejects it) — servlet filters, the Spring Security filter chain (authentication, then authorization, then CSRF checks, each a link), generic middleware pipelines, and layered exception handlers are all Chain of Responsibility in production frameworks. It decouples the sender of a request from whichever handler eventually deals with it, and it makes the pipeline itself configurable — reordering or inserting a handler doesn't require touching any existing handler's logic. In the demo, `AuthHandler → ValidationHandler → BusinessHandler`: a request failing validation (`"bad"`) is rejected directly by `ValidationHandler` and never reaches `BusinessHandler` at all, while a fully valid, authenticated request passes through every handler and reaches `"processed"`.

*Follow-up: "How is this different from Decorator, since both link/wrap objects?"* Structurally similar, but the intent differs: Decorator's every layer unconditionally adds behavior and always delegates onward; Chain of Responsibility's handlers can *stop the chain early* by rejecting or fully handling the request themselves, and typically only one handler in the chain actually "does the real work" for any given request rather than every link contributing.

**Q: What is the Iterator pattern?**
Providing sequential access to a collection's elements without exposing how that collection is actually structured internally. Java bakes this in directly through the `Iterator`/`Iterable` interfaces and the enhanced `for` loop, so you almost never hand-implement it — but every `for (var x : collection)` you write is using Iterator underneath.

**Q: What is the Visitor pattern and its trade-off?**
It lets you add new operations over a fixed family of element types without modifying those element classes, using double dispatch: each element has an `accept(visitor)` method that calls back a specific method on the visitor corresponding to that element's concrete type, so which code actually runs depends on both the element's type *and* the visitor's type. The trade-off is a hard asymmetry: adding a new *operation* is easy (write a new Visitor implementation, touching nothing existing), but adding a new *element type* is hard, since every existing Visitor implementation needs a new method to handle it. It pairs well with Java's sealed types and pattern-matching `switch`, which give you a compiler-enforced exhaustiveness check with far less scaffolding than a classic Visitor hierarchy.

**Q: How do you decide which behavioral pattern fits a given problem?**
Identify what actually needs to vary or be decoupled, and match it: an interchangeable algorithm → Strategy; one-to-many event notification → Observer; a deferred or undoable action → Command; behavior tied to a lifecycle phase → State; a request that one of several handlers might process, possibly rejecting early → Chain of Responsibility; a fixed overall flow with a small number of variable steps → Template Method. Name the specific axis of change first, then pick the pattern built to isolate exactly that axis — trying to force-fit a pattern before identifying what actually varies is how you end up with an over-engineered solution to a problem that didn't need one.

**Q: Aren't design patterns less relevant with lambdas and modern languages?** *nuance*
Several genuinely collapse into language features: Strategy and Command are often just a lambda passed around; Iterator is built directly into the language. What doesn't disappear is the *design vocabulary and intent* — recognizing "this is Strategy" or "this needs Chain of Responsibility because a request might be rejected partway through" remains valuable for design conversations, for reading unfamiliar frameworks quickly (filters are Chain of Responsibility, `*Template` classes are Template Method), and for choosing *which* lambda-shaped solution actually fits the problem. Patterns are a means to decoupled, maintainable code — treating the catalogue as a checklist to force onto every problem, rather than a vocabulary to reach for when the underlying problem actually calls for it, is the real anti-pattern.

**Q: If a `switch` statement on a type/status field keeps growing every time a new case is added, which pattern(s) fix it, and how do you choose between them?**
Both State and Strategy (and, for pure creation logic, Factory Method from Phase 3) replace a growing type-switch with polymorphism, but they answer different questions. If the switch selects *which algorithm to run* for a given operation and the choice doesn't depend on any ongoing lifecycle — Strategy: inject the right implementation once. If the switch is dispatching on an object's *current lifecycle phase*, and that phase itself changes over time in response to actions (draft → review → published) — State: each phase becomes a class that also knows its own valid transitions. The practical tell: does the "type" ever change for a given instance over its lifetime? If yes, it's State; if it's fixed for the object's whole life, it's Strategy (or just polymorphism via Factory Method at construction time).
