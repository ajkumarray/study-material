<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · structural](../phase-4-structural/NOTES.md) | [Phase 6 · architecture ➡](../phase-6-architecture/NOTES.md)
<!-- /nav -->

# Phase 5 — Behavioral Design Patterns: Notes

Behavioral patterns govern **how objects communicate and distribute responsibility** — algorithms, message passing, and control flow. You've already *used* several (Strategy via lambdas throughout the functional phases; Template Method in the Java capstone's payment flow) — here they're named and formalized.

## The patterns (runnable in `BehavioralPatterns.java`)

### Strategy — swap an algorithm at runtime
Define a family of interchangeable algorithms behind a common interface and select one at runtime (`Cart` takes a `DiscountStrategy`). Adds new behavior without touching the client (Open/Closed). **In Java a strategy is often just a lambda / functional interface** — passing a `Comparator`, a `Function`, or `discount -> ...` *is* the Strategy pattern. The everyday workhorse.

### Observer — publish/subscribe notifications
A subject maintains a list of observers and notifies them of events; observers react without the subject knowing their concrete types (`Channel.publish` calls every `Subscriber`). Decouples the event source from the reactors. (JDK listeners, JS `addEventListener`, reactive streams, event buses — System Design Phase 9.)

### Command — a request as an object
Encapsulate an action + its data as an object with `execute()` (and often `undo()`), so you can parameterize, queue, log, and **undo** operations (`CommandHistory` supports undo by popping the last command). Uses: GUI actions, task queues, transaction logs, redo/undo stacks.

### Template Method — fixed skeleton, customizable steps
A base class defines the **skeleton** of an algorithm in a (usually `final`) method and defers specific steps to subclasses (`Report.generate` fixes header→body→footer; `CsvReport` fills in `body`). You used this in the Java capstone; JUnit lifecycles and Spring's `*Template` classes are built on it. Contrast Strategy (composition) — Template Method uses inheritance.

### State — behavior changes with internal state
An object delegates behavior to a **state object** and transitions between states, so it appears to change class (`Doc`: Draft → Review → Published, each state handling `publish` differently). Replaces sprawling `if/switch` on a status field with polymorphism — cleaner and Open/Closed for new states. (Related to a state machine.)

### Chain of Responsibility — pass a request along handlers
Give multiple objects a chance to handle a request by passing it along a chain until one handles it (`Auth → Validation → Business`). Decouples sender from receiver and makes the pipeline configurable. **Ubiquitous:** servlet filters, the **Spring Security filter chain** (Spring Boot Phase 7), middleware pipelines, exception handlers.

### Others worth knowing (names + one-liners)
- **Iterator** — sequential access to a collection without exposing its internals (Java's `Iterator`/`Iterable`, `for-each` — Java Phase 3).
- **Mediator** — centralize complex communication between objects in a mediator (reduces many-to-many coupling to many-to-one).
- **Memento** — capture and restore an object's state (snapshots for undo).
- **Visitor** — add operations to an object structure without changing its classes (double dispatch — pairs with sealed types, Java Phase 2.6; the interpreter you built there is Visitor-adjacent).

## Perspective
- Behavioral patterns are mostly about **decoupling who calls what:** Strategy (which algorithm), Observer (who reacts), Command (what action, deferred), Chain (which handler), State (which behavior now).
- **Strategy vs Template Method:** both vary an algorithm — Strategy via composition (inject a strategy object/lambda, more flexible), Template Method via inheritance (override steps, simpler when the skeleton is fixed). Modern code leans on Strategy-with-lambdas.
- Recognizing these in frameworks (filters = Chain, listeners = Observer, `*Template` = Template Method, comparators = Strategy) makes libraries far less mysterious — and naming them communicates design intent instantly.
