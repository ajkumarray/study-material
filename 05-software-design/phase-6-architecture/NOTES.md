<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · behavioral](../phase-5-behavioral/NOTES.md) | [Phase 7 · refactoring ➡](../phase-7-refactoring/NOTES.md)
<!-- /nav -->

# Phase 6 — Architectural Patterns & Principles: Notes

Where design patterns (Phases 3–5) operate on individual classes, **architecture** organizes the whole system — how major parts are split, which direction dependencies flow, and where the actual business logic lives. The goal is the same as clean code (Phase 1) applied at a larger scale: **manageable change, testability, and separation of concerns.** This phase's runnable example, `HexagonalDemo.java`, demonstrates the most important architectural idea in the phase — dependency direction — concretely, in code you can execute.

---

## 1. Layered Architecture & Separation of Concerns

Split an application into **horizontal layers**, each with one clear responsibility, where each layer depends only on the layer directly below it.

### Key Concepts
- **The standard stack**: Presentation/Web (controllers — handle HTTP, parse requests) → Application/Service (use cases, orchestration) → Domain (core business rules) → Persistence (data access). This is exactly your Java capstone and Spring app's shape: `@RestController → @Service → @Repository`.
- **Each layer is independently testable and swappable** — you can unit-test the service layer with a fake repository, or swap the persistence layer from an in-memory store to JDBC, without the controller layer noticing.
- **Dependencies flow one direction only** — the presentation layer depends on the service layer, which depends on the domain, which (ideally) doesn't depend on persistence at all; persistence depends *down* on nothing (it's the bottom).
- **Two real risks**: an **anemic domain** (business logic leaks upward into services instead of living in the domain model itself, leaving domain objects as pure data with no behavior — a system-wide version of the Phase 7 "data class" smell); and **rigid top-to-bottom coupling** (every layer strictly depending on the concrete layer below rather than an abstraction, so a persistence-layer change ripples upward).

### Worked Example
Conceptually, in your Java capstone: `ExpenseController` (presentation) calls `ExpenseService.addExpense(...)` (application), which applies business rules and calls `ExpenseRepository.save(...)` (persistence, accessed through an interface — this is where Dependency Inversion, Phase 2, enters layered architecture: the service depends on the `ExpenseRepository` *interface*, not a concrete JDBC class). Each layer can be tested in isolation: the service layer's business rules are testable with a fake repository and no real database; the controller layer's request/response handling is testable with a fake service and no real business logic running.

### Why It's Useful
A clean layer boundary means a change confined to one layer (a new validation rule in the service layer, a new database engine in the persistence layer, a new response format in the controller layer) doesn't ripple into the others — exactly the "localized change" payoff from Phase 1's cohesion/coupling discussion, applied at the scale of a whole application.

### Summary / Key Takeaways
- Presentation → Application/Service → Domain → Persistence, each depending only downward.
- Matches `@RestController → @Service → @Repository` in a real Spring app.
- Watch for an anemic domain (logic that should live in the domain leaking into services) and rigid concrete-to-concrete coupling between layers.

---

## 2. UI Architectures: MVC, MVP, MVVM

Three closely related patterns, all built around the same core idea — **keep rendering separate from business logic** — that differ in exactly *where* view-related logic lives and how testable it is.

### Key Concepts
- **MVC (Model-View-Controller)**: Model holds data and logic; View displays it; Controller handles input and updates the Model (and typically selects which View to render). The original web pattern — Spring MVC on the server follows this shape directly (`@Controller` methods handle a request, update/query the model, and return a view name).
- **MVP (Model-View-Presenter)**: the Presenter holds view *logic* (what should be displayed, how to react to input) and updates a deliberately **passive** View through a narrow interface — the View itself contains almost no logic, which makes the Presenter fully testable without ever needing a real UI to run.
- **MVVM (Model-View-ViewModel)**: the ViewModel exposes **bindable state**; the View **data-binds** to that state directly, so UI updates happen automatically as the ViewModel's state changes, without the ViewModel needing to know about the View at all (used by Angular, WPF, Vue — track 10). React (track 08) is component-based rather than a strict MVVM implementation, but shares the same underlying spirit: "UI is a pure function of state."

### Comparison Table

| | Who holds view logic | View's role | Testability of logic |
|---|---|---|---|
| **MVC** | Controller (partly) + Model | Displays what Controller/Model give it | Moderate — Controller often still touches the View API |
| **MVP** | Presenter | Fully passive, implements a narrow interface | High — Presenter is tested with no real UI |
| **MVVM** | ViewModel | Binds directly to ViewModel's observable state | High — ViewModel tested independently; View is declarative binding |

### Why It's Useful
All three exist to answer the same question: how do you keep "what should happen" (business/view logic) separate from "how it's actually drawn on screen" (rendering), so each can change independently and the logic half can be unit-tested without spinning up a real UI. The differences are about exactly how strictly the View is kept passive/dumb, and whether updates are pushed explicitly (MVC/MVP) or happen automatically via binding (MVVM).

### Summary / Key Takeaways
- All three separate rendering from logic — the shared goal, not the distinguishing detail.
- MVC: Controller updates Model, selects View. MVP: Presenter drives a passive View via an interface. MVVM: ViewModel exposes bindable state; View binds to it.
- Spring MVC is server-side MVC; Angular/Vue lean MVVM; React shares MVVM's "UI = f(state)" spirit without being a strict implementation of it.

---

## 3. Hexagonal Architecture (Ports & Adapters)

Put the **domain (business logic) at the center**, with **no dependencies on frameworks, databases, or UI whatsoever**. The domain talks to the outside world exclusively through **ports** — interfaces the domain itself owns — and **adapters** implement those ports for specific technologies. Also known as Clean Architecture or Onion Architecture, with minor variations in terminology.

### Key Concepts
- **Ports are interfaces the domain defines and depends on** — `UserRepositoryPort` and `NotifierPort` in the demo are declared *inside* the domain code, expressing exactly what the domain needs from the outside world, in the domain's own vocabulary — nothing about JDBC, SMTP, or HTTP appears anywhere in a port's signature.
- **Adapters implement ports for specific technology** — `InMemoryUserRepo` is one adapter implementing `UserRepositoryPort`; a real application would also have a JDBC-backed adapter implementing the exact same port, swappable with zero changes to the domain.
- **Driving vs. driven ports/adapters**: a **driving** adapter calls *into* the domain (a REST controller invoking a use case — the "primary" direction, initiating action); a **driven** adapter is called *out to, by* the domain (a repository or notifier the domain calls to persist data or send a message — the "secondary" direction, the domain reaching out for something it needs).
- **Dependencies point inward, always** — the domain never imports a framework, a JDBC driver, or an HTTP library; the outside (adapters) depends on the domain's ports, never the reverse. This is **Dependency Inversion (Phase 2) applied at the scale of an entire architecture**, not just a single class.
- **Payoff**: the domain can be fully unit-tested using fake/in-memory adapters, with no database and no web server running at all — exactly what `HexagonalDemo.java`'s `main` method does.

### Worked Example

```java
// From HexagonalDemo.java — the domain core (no framework/DB/web imports)
record User(String email) { }

// PORTS — interfaces the DOMAIN owns and depends on (abstractions, DIP)
interface UserRepositoryPort {                 // "driven" port (domain -> infra)
    boolean existsByEmail(String email);
    void save(User user);
}
interface NotifierPort {                        // "driven" port
    void notify(String message);
}

// The domain service — pure business logic, depends only on PORTS
static class RegistrationService {
    private final UserRepositoryPort users;     // injected abstraction
    private final NotifierPort notifier;
    RegistrationService(UserRepositoryPort users, NotifierPort notifier) {
        this.users = users; this.notifier = notifier;
    }
    void register(String email) {               // the use case
        if (users.existsByEmail(email)) throw new IllegalStateException("user exists: " + email);
        users.save(new User(email));
        notifier.notify("welcome " + email);
    }
}

// ADAPTERS — implement the ports for specific tech (the OUTSIDE)
static class InMemoryUserRepo implements UserRepositoryPort {
    private final List<User> store = new ArrayList<>();
    public boolean existsByEmail(String email) { return store.stream().anyMatch(u -> u.email().equals(email)); }
    public void save(User user) { store.add(user); }
}
static class ConsoleNotifier implements NotifierPort {
    public void notify(String message) { System.out.println("[notify] " + message); }
}

// usage — wire adapters into the domain, exactly like Spring's DI container does:
var service = new RegistrationService(new InMemoryUserRepo(), msg -> log.add(msg));
service.register("ajay@dev.io");
service.register("meera@dev.io");
// assert log.equals(List.of("welcome ajay@dev.io", "welcome meera@dev.io"))

// the domain rejects a duplicate — a BUSINESS rule, entirely tech-independent:
try { service.register("ajay@dev.io"); assert false; }
catch (IllegalStateException e) { assert e.getMessage().contains("exists"); }
```

In this example, `RegistrationService` — the domain — never imports `java.sql`, never imports an HTTP framework, and has no idea whether `UserRepositoryPort` is ultimately backed by an in-memory list, a real Postgres table, or anything else. `main` wires `InMemoryUserRepo` and a lambda-based `NotifierPort` into `RegistrationService`'s constructor, exactly the way a Spring `@Configuration` class (or Spring's component scanning) would wire a real `JdbcUserRepo` in production. The duplicate-email rejection (`"user exists: " + email`) is pure business logic living entirely inside `register`, verifiable with zero database or network involved — the domain was tested using fake adapters, and swapping `InMemoryUserRepo` for a real JDBC adapter later requires touching *only* the adapter, never `RegistrationService`.

### Why It's Useful
Hexagonal architecture is what makes a rich, complex domain testable in isolation and infrastructure genuinely swappable — the database, the email provider, even the web framework can all change without a single line of business logic being touched, because none of them were ever depended on directly in the first place.

### Summary / Key Takeaways
- Domain at the center, zero dependencies on frameworks/DB/UI.
- Ports = interfaces the domain owns; adapters = technology-specific implementations of those ports.
- Driving adapters call *into* the domain; driven adapters are called *out to* by the domain.
- Dependencies always point inward — DIP applied at the architecture level.
- Payoff, demonstrated directly in `HexagonalDemo.java`: test the domain with fake adapters, no DB/web involved at all.

---

## 4. Dependency Injection & Inversion of Control (the bridge to Spring)

The runtime mechanism that makes layered and hexagonal architectures *practical* rather than merely theoretical.

### Key Concepts
- **Inversion of Control (IoC)**: the framework — not your own code — controls object creation and the overall flow; the framework calls *you* (via annotations, lifecycle hooks, callbacks) rather than you explicitly calling the framework to get things done.
- **Dependency Injection (DI)**: the specific *technique* that implements IoC for wiring collaborators — a class's dependencies are supplied from outside (most commonly via constructor injection) instead of being constructed internally.
- **A DI container (Spring) automates this wiring** — it scans for `@Component`/`@Service`/`@Repository` classes, constructs them, and injects the right concrete implementation wherever an interface is requested, entirely replacing the hand-wiring `HexagonalDemo.java`'s `main` method does manually (`new RegistrationService(new InMemoryUserRepo(), ...)`).
- **This is what makes hexagonal architecture "just work" in a real Spring app**: you write ports as interfaces and adapters as `@Component` classes; Spring's container injects the right adapter into the domain service automatically, with nothing in the domain ever hardcoding `new ConcreteRepo()`.

### Why It's Useful
Without DI, someone still has to write the "glue" code that constructs every object and threads the right concrete dependency into every constructor — and doing that by hand everywhere reintroduces exactly the coupling DIP is trying to remove. A DI container centralizes that wiring in one place (configuration, annotations, and component scanning) so application code itself never contains a `new ConcreteImplementation()` call for anything it depends on abstractly.

### Summary / Key Takeaways
- IoC: the framework controls flow and wiring, not your code.
- DI: the technique — dependencies supplied from outside, typically via constructor injection.
- A DI container (Spring) automates DI at scale — this is the runtime mechanism that realizes DIP (Phase 2) and hexagonal architecture's inward-pointing dependencies in a real application.

---

## 5. Core Architectural Principles (recap, at system scale)

- **Composition over inheritance** (Phase 1/4) — assemble systems from focused, composable parts rather than deep, rigid hierarchies; more flexible, less fragile to a single change rippling outward.
- **Program to an interface, not an implementation** — depend on abstractions (every port in hexagonal architecture, every `EmployeeRepository`-style interface from Phase 2) so concrete implementations swap freely underneath.
- **Separation of concerns** — each module owns one axis of change; this is SRP (Phase 2) restated at the scale of an entire module or layer instead of a single class.
- **Loose coupling / high cohesion** — the recurring north star from Phase 1, now applied to how whole subsystems relate to each other rather than how classes relate to each other.

---

## 6. Broader Landscape (know the names)

- **Monolith vs. microservices**: a monolith is one deployable unit; microservices are many small, independently deployable services (System Design track). Start with a monolith — ideally a **modular** monolith, internally organized with clean boundaries even though it deploys as one unit — and split into microservices only when independent scaling or independent team ownership at a real boundary actually demands it. Microservices trade simplicity for independent deployability/scalability, at the cost of real distributed-systems complexity: network failures, eventual consistency, and harder observability (System Design Phases 4–9 cover this trade-off in depth).
- **Event-driven architecture** (System Design 9): services communicate by emitting and reacting to events rather than direct synchronous calls, giving loose coupling and easy extensibility — a new consumer can subscribe to an existing event stream with zero changes to the producer. It's Observer (Phase 5) and Chain of Responsibility ideas at system scale, at the cost of eventual consistency and harder end-to-end tracing.
- **CQRS / Event Sourcing** — advanced patterns (System Design 9) that separate the read model from the write model (CQRS), and/or store state as an append-only log of events rather than as current-state rows (Event Sourcing) — powerful for high-scale or audit-heavy systems, but real added complexity that should be reserved for problems that actually need it.

---

## Perspective

Architecture is fundamentally about two questions: **where do dependencies point**, and **where does logic live**. Across layered architecture, hexagonal architecture, and dependency injection, the recurring answer is the same: keep the **domain independent** of frameworks and infrastructure, **depend on abstractions** rather than concrete details, and let a **container wire** the concrete pieces together at the edges. Choose the simplest architecture that actually fits the problem's real complexity — a well-organized, modular monolith beats a premature microservices split almost every time; hexagonal architecture earns its overhead specifically when you have a genuinely rich domain that needs to be isolated from volatile, fast-changing infrastructure. Don't cargo-cult architecture patterns onto a problem that doesn't need them — that's Phase 1's YAGNI, one more time, applied at the largest possible scale.
