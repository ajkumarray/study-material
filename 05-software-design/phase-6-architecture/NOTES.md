<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · behavioral](../phase-5-behavioral/NOTES.md) | [Phase 7 · refactoring ➡](../phase-7-refactoring/NOTES.md)
<!-- /nav -->

# Phase 6 — Architectural Patterns & Principles: Notes

Where patterns operate on classes, **architecture** organizes the whole system — how major parts are split, how dependencies flow, and where the business logic lives. The goal is the same as clean code at scale: **manageable change, testability, and separation of concerns.**

## 6.1 — Layered architecture & separation of concerns
Split the app into horizontal **layers**, each with one responsibility, depending only on the layer below:
- **Presentation/Web** (controllers) → **Application/Service** (use cases, orchestration) → **Domain** (business rules) → **Persistence** (data access).
This is exactly your Java capstone and Spring app: `@RestController → @Service → @Repository`. Benefits: each layer is testable and swappable in isolation; concerns don't bleed together. Risk: an "anemic" domain (logic leaks into services) and rigid top-to-bottom coupling.

## 6.2 — MVC / MVP / MVVM (UI architectures)
Separate the **UI** from the data and logic:
- **MVC** (Model-View-Controller): Model = data/logic, View = display, Controller = handles input and updates the model. Spring MVC on the server; the original web pattern.
- **MVP** (Model-View-Presenter): the Presenter holds view logic and updates a passive View via an interface — testable without a UI.
- **MVVM** (Model-View-ViewModel): the ViewModel exposes bindable state; the View data-binds to it (Angular, WPF, Vue). React is component-based but shares the "UI = f(state)" spirit (track 08).
All three share one idea: **keep rendering separate from business logic** so each evolves independently and the logic is testable.

## 6.3 — Hexagonal (Ports & Adapters) and DI/IoC
- **Hexagonal architecture** (a.k.a. Ports & Adapters / Clean/Onion architecture): put the **domain at the center** with *no dependencies on frameworks, DB, or UI*. The domain defines **ports** (interfaces it owns); **adapters** implement them for specific technologies (a JDBC repo adapter, an SMTP notifier, a REST controller). **Dependencies point inward** — the outside depends on the domain, never the reverse. This is **Dependency Inversion (Phase 2) at the architecture level**. Payoff (demonstrated in `HexagonalDemo.java`): the domain is tested with fake adapters — no DB, no web — and infrastructure swaps without touching business logic. "Driving" adapters (web/CLI) call in through input ports; "driven" adapters (DB/email) are called out through output ports.
- **Dependency Injection & IoC** (the bridge to Spring — Spring Boot Phase 3): **Inversion of Control** means the framework, not your code, wires objects together; **DI** is the technique (constructor injection). This is what makes layered/hexagonal architectures practical — the container injects the right adapter into the domain, so nothing hardcodes `new ConcreteRepo()`. DI is the runtime mechanism that realizes DIP.

## 6.4 — Core architectural principles
- **Composition over inheritance** (Java Phase 2): assemble behavior from parts rather than deep class hierarchies — more flexible, less fragile.
- **Program to an interface, not an implementation** — depend on abstractions so implementations swap freely (every port above).
- **Separation of concerns** — each module owns one axis of change (SRP at module scale).
- **Loose coupling / high cohesion** — the recurring north star.

## Broader landscape (know the names)
- **Monolith vs microservices:** one deployable vs many small services (System Design). Start monolith (a *modular* monolith), split to microservices only when scaling/team boundaries demand it — microservices trade simplicity for independent scaling/deployment and add distributed-systems complexity (System Design Phases 4–9).
- **Event-driven architecture** (System Design 9): services communicate via events — loose coupling, easy extension (an Observer/Chain flavor at system scale).
- **CQRS / Event Sourcing** — separate read/write models; store state as an event log (advanced; System Design 9).

## Perspective
Architecture is about **where dependencies point** and **where logic lives**. The recurring answer across layered, hexagonal, and DI: keep the **domain independent** of frameworks and infrastructure, depend on **abstractions**, and let a **container wire** the concrete pieces. Choose the simplest architecture that fits — a well-layered monolith beats premature microservices; hexagonal shines when you need to isolate a rich domain from volatile infrastructure. Don't cargo-cult architecture; match it to the problem's real complexity.
