<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · solid](../phase-2-solid/NOTES.md) | [Phase 4 · structural ➡](../phase-4-structural/NOTES.md)
<!-- /nav -->

# Phase 3 — Creational Design Patterns: Notes

**Design patterns** are named, reusable solutions to recurring design problems (from the "Gang of Four" book). They're a shared vocabulary and proven structures — concrete embodiments of the SOLID principles (Phase 2). Creational patterns handle **object creation**, decoupling client code from the concrete classes it instantiates (so `new` isn't scattered everywhere and implementations swap easily) — embodying Open/Closed and Dependency Inversion.

## The patterns (all runnable in `CreationalPatterns.java`)

### Singleton — one instance, globally accessible
Ensure a class has exactly one instance with a global access point. Use for genuinely-single resources (a config registry, a connection pool). In Java the **enum singleton** is best — thread-safe, serialization-safe, lazy by the class-init guarantee (Java Phase 2.6). **Caution:** it's often an anti-pattern — a global variable in disguise that hides dependencies and hurts testability. Prefer **dependency injection** (Spring beans are singletons managed *for* you) so the single instance is injected, not reached globally.

### Factory Method — create by type behind an abstraction
A method decides which concrete class to instantiate and returns the abstraction (`Shape`), so callers depend on the interface, not the concretions — and adding a new type doesn't change caller code (Open/Closed). The one place that knows the concrete classes is the factory. (JDK: `Integer.valueOf`, `List.of`, `Calendar.getInstance`.)

### Builder — construct complex objects step by step
A fluent, chainable API to assemble an object with many optional parts, avoiding **telescoping constructors** (`Pizza(size, cheese, t1, t2, ...)`) and keeping the result immutable. `new Pizza.Builder("medium").cheese().topping("olive").build()`. (JDK: `StringBuilder`, `Stream.Builder`; Lombok's `@Builder`; records reduce the need for simple cases.)

### Abstract Factory — families of related objects
Create whole **families** of related objects (a themed UI kit: matching dark button + dark checkbox) without naming concrete classes, guaranteeing consistency (you never mix a dark button with a light checkbox). One concrete factory per family. Heavier than Factory Method — use when you must produce *sets* of things that go together.

### Prototype — clone instead of build
Create new objects by copying an existing prototype (a copy constructor / `clone`), useful when construction is expensive or you want a variant of a configured object. Mind **shallow vs deep copy** (defensive-copy mutable fields — Java Phase 2.2).

## Perspective
- **Patterns are tools, not goals.** Applying a pattern where a plain constructor or a lambda suffices is over-engineering (the YAGNI counterweight). Reach for one when you feel the problem it solves.
- **Modern Java softens some:** factories/strategies are often just method references/lambdas; records replace simple builders; DI containers replace hand-rolled singletons. The *ideas* endure; the boilerplate shrinks.
- Creational patterns pair with DIP (Phase 2): the factory returns an abstraction the client depends on, and the concrete choice is centralized/injected.
