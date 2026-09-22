<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · behavioral](../phase-5-behavioral/NOTES.md) | [Phase 7 · refactoring ➡](../phase-7-refactoring/NOTES.md)
<!-- /nav -->

# Phase 6 — Architectural Patterns: Interview Q&A

⭐ = asked constantly.

**Q: What is layered architecture?** ⭐⭐
Splitting an application into horizontal layers — typically presentation, application/service, domain, and persistence — each with one clear responsibility, and each depending only on the layer directly below it. This separates concerns, makes each layer independently testable and swappable (you can unit-test the service layer against a fake repository, or swap the persistence layer entirely without the controller layer noticing), and it's the standard structure of a real Spring application: `@RestController → @Service → @Repository`. The main failure modes to watch for are an anemic domain (business logic leaking upward into the service layer instead of living in the domain model) and rigid concrete-to-concrete coupling between layers instead of coupling through abstractions.

**Q: Explain MVC.**
Model-View-Controller separates data and business logic (Model), display (View), and input handling (Controller). The Controller receives input, updates the Model, and selects which View renders the result; the View renders whatever the Model currently holds. It keeps UI-rendering concerns out of business logic. MVP and MVVM are variants that shift *where* view-specific logic lives and how strictly the View is kept passive: MVP puts it in a Presenter that drives a deliberately passive View through a narrow interface (highly testable, since the Presenter needs no real UI to test); MVVM puts it in a ViewModel that exposes bindable state, which the View binds to directly so updates happen automatically without the ViewModel ever referencing the View.

*Follow-up: "Why does MVP's 'passive View' matter for testability?"* Because if the View contains no logic of its own — it just displays whatever the Presenter tells it to — then the Presenter (which holds all the actual decision-making) can be fully unit-tested by asserting what it *would* have told a real View to display, using a fake/mock View implementation, with no UI framework running at all.

**Q: What is hexagonal (ports and adapters) architecture?** ⭐⭐
The domain — the core business logic — sits at the center with zero dependencies on frameworks, databases, or UI. It defines ports: interfaces it owns, expressed entirely in its own vocabulary, describing exactly what it needs from the outside world (`UserRepositoryPort.existsByEmail`, `NotifierPort.notify`). Adapters implement those ports for specific technology — an in-memory repo for tests, a JDBC-backed repo for production, an SMTP notifier, a REST controller as the entry point. Dependencies point inward, always: adapters depend on the domain's ports, never the reverse, so the domain never imports a framework or a driver. The direct payoff, demonstrated in `HexagonalDemo.java`: `RegistrationService` is fully unit-tested — including its business rule that rejects a duplicate email — using `InMemoryUserRepo` and a lambda-based notifier, with no real database and no web server involved anywhere.

```java
interface UserRepositoryPort { boolean existsByEmail(String email); void save(User user); }
class RegistrationService {
    private final UserRepositoryPort users;   // domain depends on the PORT, not a concrete DB class
    void register(String email) {
        if (users.existsByEmail(email)) throw new IllegalStateException("user exists: " + email);
        ...
    }
}
```

**Q: How does hexagonal architecture relate to Dependency Inversion?** ⭐
Directly and precisely — DIP says high-level modules shouldn't depend on low-level modules, and both should depend on abstractions. Hexagonal architecture applies exactly this at the scale of a whole application: the domain (the high-level module) owns the port interfaces, and adapters (the low-level modules — JDBC, SMTP, HTTP) implement those ports, so the dependency arrow always points from infrastructure *toward* the domain, never the other way. The domain never imports `java.sql` or an HTTP client library — it only ever references its own port interfaces.

*Follow-up: "What's the difference between a driving and a driven port/adapter?"* A driving adapter calls *into* the domain to trigger a use case — a REST controller invoking `RegistrationService.register(...)` is the primary, initiating direction. A driven adapter is called *out to, by* the domain when it needs something — `UserRepositoryPort`/`NotifierPort` and their concrete adapters are driven, secondary: the domain reaches out to them, not the other way around.

**Q: What is Inversion of Control and how does DI relate?** ⭐⭐
IoC means the framework controls object creation and program flow rather than your own code explicitly driving it — the framework calls you (through annotations, lifecycle hooks, callbacks) instead of you calling the framework to make things happen. Dependency Injection is the specific technique that implements IoC for wiring collaborators together: a class's dependencies are supplied from the outside (constructor injection being the most common and generally preferred form) instead of the class constructing them internally. A DI container like Spring automates this at scale — it scans for annotated components, constructs them, and wires the correct concrete implementation into every constructor that asks for an interface, which is exactly what makes hexagonal architecture's inward-pointing dependencies practical in a real application rather than something you have to hand-wire in every `main` method the way `HexagonalDemo.java` does for demonstration purposes.

**Q: Monolith vs. microservices — how do you choose?** ⭐⭐
Start with a (modular) monolith — simpler to build, deploy, test, debug, and reason about as a single unit, while still internally organized with clean module boundaries so a future split, if it's ever needed, isn't starting from a tangled mess. Move toward microservices specifically when you need independent scaling of different parts of the system, independent deployability, or genuine team-autonomy boundaries — and go in accepting the real costs that come with it: network failures where there used to be a plain method call, eventual consistency where there used to be one transaction, and materially harder observability and debugging across service boundaries. Adopting microservices prematurely, before any of those specific needs are real, is one of the most common and expensive architecture mistakes teams make — a well-modularized monolith is usually the correct first architecture, and often the correct architecture for a very long time.

**Q: What is separation of concerns?**
Organizing a system so each part addresses one distinct concern with minimal overlap with any other part — UI rendering vs. business logic vs. data access at the architecture scale, or one responsibility per class at the SOLID scale. It reduces coupling and localizes the blast radius of any given change, and it's the underlying idea behind layered architecture, MVC/MVP/MVVM, and hexagonal architecture alike — each is, in a sense, a specific, named strategy for achieving separation of concerns at a particular granularity.

**Q: Why "program to an interface, not an implementation"?**
Depending on an abstraction rather than a concrete class lets you swap the concrete implementation freely — a test double in unit tests, an alternative backend in production — without ever touching the code that depends on it, and it's what makes dependency injection possible in the first place (you can only inject *something* in place of a dependency if the depending code asked for an interface, not a specific concrete class). It's the practical, everyday rule behind DIP (Phase 2) and behind every port in a hexagonal architecture — `RegistrationService` asking for a `UserRepositoryPort` rather than a `JdbcUserRepo` directly is this rule applied concretely.

**Q: What is event-driven architecture, at a high level?**
Components communicate by emitting and reacting to events instead of calling each other directly and synchronously, which gives loose coupling (a producer doesn't need to know who's listening) and easy extensibility (a new consumer subscribes to an existing event stream with zero changes required on the producer's side). It's Observer (Phase 5) and Chain-of-Responsibility-style ideas scaled up to whole services communicating across a network. The trade-offs are real: eventual consistency (a consumer might process an event slightly after it happened, so the system isn't instantaneously consistent everywhere) and materially harder end-to-end tracing/debugging, since a single logical operation might now span several asynchronously-triggered handlers instead of one synchronous call stack (System Design Phases 9–10 dig into this trade-off in depth).

**Q: How do you decide on an architecture for a new system?**
Match the architecture's complexity to the problem's actual, current complexity — start simple with a layered (and ideally modular) monolith, reach for hexagonal architecture's port/adapter isolation specifically when you have a rich domain that genuinely needs to be protected from volatile, fast-changing infrastructure decisions, and only add events or microservices when scale or team-boundary realities actually demand the distributed-systems complexity that comes with them. Across all of it, prioritize the same few things: keep the domain independent of frameworks, depend on abstractions rather than concrete details, and keep the design testable — and consciously avoid cargo-culting a pattern you don't have a concrete, present need for (the architecture-scale version of YAGNI from Phase 1).

**Q: In `HexagonalDemo.java`, what would change — and what wouldn't — if you swapped `InMemoryUserRepo` for a real JDBC-backed repository?**
`RegistrationService`, `UserRepositoryPort`, `NotifierPort`, and the `register(...)` business logic (including the duplicate-email rejection) would not change at all — none of them know or care what's actually implementing `UserRepositoryPort`. Only a new class, say `JdbcUserRepo implements UserRepositoryPort`, would need to be written, using a real JDBC connection or a Spring `JdbcTemplate`/JPA repository internally, and the wiring in `main` (or, in a real app, Spring's component scanning) would construct `RegistrationService` with the new adapter instead of `InMemoryUserRepo`. That's the concrete, demonstrable payoff of dependencies pointing inward: swapping an entire technology (in-memory list → real database) is a change confined entirely to one new adapter class.
