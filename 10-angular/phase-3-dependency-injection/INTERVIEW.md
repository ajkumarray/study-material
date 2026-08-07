<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · templates binding](../phase-2-templates-binding/NOTES.md) | [Phase 4 · reactivity ➡](../phase-4-reactivity/NOTES.md)
<!-- /nav -->

# Phase 3 — Dependency Injection & Services: Interview Q&A

⭐ = asked constantly.

**Q: What is a service and why use one?** ⭐⭐
A class (usually `@Injectable`) that holds shared state, business logic, or I/O, kept
separate from components. Components stay thin and focused on the view; services are
reusable, testable, and shared via DI. It's separation of concerns — the same reason
Spring has `@Service` beans.

**Q: What does `providedIn: 'root'` do?** ⭐⭐
Registers the service with the root injector as an app-wide singleton — one shared
instance for everything that injects it — and makes it tree-shakable (dropped if
unused). It's the standard way to declare a shared service.

**Q: `inject()` vs constructor injection?** ⭐
Both request a dependency by type from the injector. `constructor(private s: Svc)` is
the classic form; `inject(Svc)` is the modern function form that works in field
initializers and helper functions and pairs well with signals. They're equivalent;
`inject()` is now preferred.

**Q: How does Angular's DI resolve a dependency?** ⭐
Injectors form a tree mirroring the component tree. When a component asks for a
dependency, Angular walks up from that component's injector to the root and uses the
first matching provider. This lets you scope providers globally (root) or locally
(component/route).

**Q: How do you inject something that isn't a class?** *nuance*
Use an `InjectionToken<T>` as the key and register a value for it in `providers`.
Interfaces and other types are erased at runtime (they have no value to inject by), so a
token provides a stable runtime identity for config objects, primitives, or abstractions.

**Q: How do you get a new instance per component instead of a singleton?**
Provide the service in the component's own `providers` array (or a route's providers)
rather than `providedIn: 'root'`. Each component instance (and its subtree) then gets its
own instance — useful for feature/dialog-local state.

**Q: How does DI help testing?** ⭐
You swap a provider for a fake/mock in the test's injector, so a component is tested
against a controlled dependency instead of the real service (no network/DB). It's the
Dependency Inversion payoff — depend on an abstraction, inject the implementation.

**Q: How does Angular DI compare to Spring's?**
Conceptually the same IoC container: declare injectables, register them, and the
framework constructs and supplies them by type. Differences are surface — Angular's
injectors are hierarchical (mirroring the UI tree) and configured via
`providedIn`/`providers`, where Spring uses component scanning and bean scopes.
