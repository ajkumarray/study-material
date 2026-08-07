<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · behavioral](../phase-5-behavioral/NOTES.md) | [Phase 7 · refactoring ➡](../phase-7-refactoring/NOTES.md)
<!-- /nav -->

# Phase 6 — Architectural Patterns: Interview Q&A

⭐ = asked constantly.

**Q: What is layered architecture?** ⭐
Splitting an app into horizontal layers — presentation, application/service, domain, persistence — each with a single responsibility, depending only on the layer below. It separates concerns, makes each layer testable/swappable, and is the standard structure of a Spring app (`@Controller → @Service → @Repository`).

**Q: Explain MVC.** ⭐
Model-View-Controller separates data/logic (Model), display (View), and input handling (Controller). The controller updates the model and selects a view; the view renders the model. It keeps UI concerns out of business logic. Variants MVP and MVVM shift where view logic lives (Presenter / bindable ViewModel).

**Q: What is hexagonal (ports and adapters) architecture?** ⭐⭐
The domain sits at the center with no dependencies on frameworks/DB/UI. It defines ports (interfaces it owns); adapters implement them for specific tech (DB, email, web). Dependencies point inward — infrastructure depends on the domain, not vice versa. Result: the domain is testable in isolation and infrastructure swaps freely. It's Dependency Inversion at the architecture level.

**Q: How does hexagonal architecture relate to Dependency Inversion?**
Directly — DIP says high-level modules depend on abstractions, not low-level modules. Hexagonal applies this to the whole app: the domain (high-level) owns the port interfaces, and adapters (low-level) implement them, so the dependency arrow points from infrastructure toward the domain. The domain never imports a framework or driver.

**Q: What is Inversion of Control and how does DI relate?** ⭐⭐
IoC means the framework controls the flow and object wiring rather than your code (the framework calls you). Dependency Injection is a technique implementing IoC — dependencies are supplied from outside (constructor injection) rather than created internally. A DI container (Spring) wires concrete adapters into your domain, realizing DIP at runtime.

**Q: Monolith vs microservices — how do you choose?** ⭐⭐
Start with a (modular) monolith: simpler to build, deploy, test, and reason about. Move to microservices when you need independent scaling, independent deployment, or team autonomy at boundaries — accepting distributed-systems complexity (network failure, data consistency, observability). Don't adopt microservices prematurely; a well-modularized monolith is usually the right first architecture.

**Q: What is separation of concerns?**
Organizing a system so each part addresses a distinct concern with minimal overlap — UI vs business logic vs data access, or one responsibility per module. It reduces coupling and localizes change, and underlies layered/MVC/hexagonal architectures (SRP at the module/system scale).

**Q: Why "program to an interface, not an implementation"?**
Depending on an abstraction lets you swap implementations without changing clients (test doubles, alternative backends), enables DI, and reduces coupling. It's the practical rule behind DIP and every port in hexagonal architecture.

**Q: What is event-driven architecture, at a high level?**
Components communicate by emitting and reacting to events rather than direct calls, giving loose coupling and easy extensibility (add a new consumer without changing producers). Trade-offs: eventual consistency and harder end-to-end tracing (System Design Phases 9–10).

**Q: How do you decide on an architecture for a new system?**
Match complexity to the problem: start simple (layered monolith), isolate a rich/volatile domain with hexagonal ports, add events/microservices only when scale or team structure demands. Prioritize keeping the domain independent of frameworks, depending on abstractions, and testability — and avoid cargo-culting patterns you don't need.
