<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · structural](../phase-4-structural/NOTES.md) | [Phase 6 · architecture ➡](../phase-6-architecture/NOTES.md)
<!-- /nav -->

# Phase 5 — Behavioral Patterns: Interview Q&A

⭐ = asked constantly.

**Q: What is the Strategy pattern?** ⭐⭐
Defining a family of interchangeable algorithms behind a common interface and choosing one at runtime, so the client is decoupled from the specific algorithm and new ones can be added without modifying it (Open/Closed). In Java, a strategy is frequently just a lambda/functional interface — a `Comparator`, a `Function`, a discount rule.

**Q: Strategy vs Template Method?** ⭐⭐
Both parameterize an algorithm. Strategy uses composition — inject a strategy object/lambda; more flexible, swappable at runtime. Template Method uses inheritance — a base class fixes the skeleton and subclasses override steps; simpler when the overall flow is fixed. Modern code favors Strategy with lambdas.

**Q: What is the Observer pattern and where do you see it?** ⭐⭐
A subject notifies a list of observers of state changes without knowing their concrete types (publish/subscribe), decoupling the source from reactors. Examples: event listeners, JS `addEventListener`, reactive streams, message/event buses. Great for one-to-many event propagation.

**Q: What is the Command pattern good for?** ⭐
Encapsulating a request as an object (action + data) with `execute()` — enabling queuing, logging, and undo/redo (store executed commands, call `undo()` to reverse). Used in GUI actions, task schedulers, transactional systems, and macro recording.

**Q: What is the State pattern, and what does it replace?** ⭐
An object delegates behavior to a state object and transitions between states, so its behavior changes with state without giant conditionals. It replaces sprawling `if/switch` on a status field with polymorphism — each state is a class, and adding a state doesn't touch the others (Open/Closed). It implements a state machine.

**Q: Where is Chain of Responsibility used in real frameworks?** ⭐⭐
Passing a request through a chain of handlers until one handles it: servlet filters, the Spring Security filter chain, Express/middleware pipelines, logging handlers, and event-handling hierarchies. It decouples sender from receiver and makes the pipeline configurable.

**Q: What is the Iterator pattern?**
Providing sequential access to a collection's elements without exposing its internal structure. Java bakes it in via `Iterator`/`Iterable` and the enhanced for loop; you rarely implement it by hand but use it constantly.

**Q: What is the Visitor pattern and its trade-off?**
It lets you add new operations to an object structure without changing the element classes, using double dispatch (elements accept a visitor that has a method per element type). Trade-off: easy to add operations, hard to add new element types (every visitor must change). Pairs with sealed types + pattern-matching switches in modern Java.

**Q: How do you decide which behavioral pattern fits a problem?**
Ask what varies/needs decoupling: interchangeable algorithm → Strategy; one-to-many event notification → Observer; deferred/undoable actions → Command; behavior tied to lifecycle state → State; a request that several handlers might process → Chain of Responsibility; a fixed flow with variable steps → Template Method. Name the axis of change, pick the pattern that isolates it.

**Q: Aren't design patterns less relevant with lambdas and modern languages?** *nuance*
Some collapse into language features (Strategy/Command → lambdas, Iterator → built-in), but the *design vocabulary* and intent remain valuable for communication and for recognizing structure in frameworks. Patterns are a means to maintainable, decoupled code — not a checklist to force onto every problem.
