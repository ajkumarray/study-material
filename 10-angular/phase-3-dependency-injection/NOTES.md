<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · templates binding](../phase-2-templates-binding/NOTES.md) | [Phase 4 · reactivity ➡](../phase-4-reactivity/NOTES.md)
<!-- /nav -->

# Phase 3 — Dependency Injection & Services: Notes

DI is Angular's backbone and the clearest bridge from your Spring background. A
**service** is a class that holds logic/state; the **injector** creates and supplies it
where needed. This is Inversion of Control (Software Design Phase 6) built into the
framework.

## 3.1 — Services & `providedIn: 'root'`

- A **service** is a plain class decorated with **`@Injectable`**, typically holding
  shared state, business logic, or I/O (HTTP). Components stay thin; services do the
  work (separation of concerns).
- **`@Injectable({ providedIn: 'root' })`** registers it with the **root injector** as
  an app-wide **singleton** — exactly like a Spring `@Service` bean. One instance is
  shared by everything that injects it (the demo's `ExpenseService` holds the
  expense signal, shared by list + detail + form). It's also **tree-shakable**: if
  nothing injects it, it's dropped from the bundle.

## 3.2 — `inject()` vs constructor injection

Two equivalent ways to get a dependency:

```ts
// Modern: the inject() function (works in field initializers; less boilerplate).
private readonly service = inject(ExpenseService);

// Classic: constructor parameter (Angular reads the type and supplies it).
constructor(private service: ExpenseService) {}
```

- Both ask the injector for an instance by **type**. `inject()` is now preferred
  (cleaner with signals, usable outside constructors, composes into helper functions).
- **Injection tokens:** when the dependency isn't a class (a config object, a string,
  an interface with no runtime value), use an `InjectionToken<T>` as the key and provide
  a value for it. (Interfaces are erased — TS Phase 1 — so you can't inject by interface;
  a token stands in.)

## 3.3 — Hierarchical injectors & scopes

- Angular's injectors form a **tree** mirroring the component tree. A dependency is
  resolved by walking **up** from the requesting component to the root; the first
  provider found wins.
- **Provider scope:**
  - `providedIn: 'root'` → one app-wide singleton (the usual choice).
  - Provided in a **component's `providers`** → a new instance *per component instance*
    (and its subtree) — use for state that should be isolated to one feature/dialog.
  - Route-level providers → scoped to a lazy-loaded route.
- This hierarchy lets you share globally *or* scope locally without extra machinery —
  a strength over ad-hoc React context/prop-drilling for cross-cutting services.

## Why DI matters (the payoff)

- **Testability:** inject a fake/mock service in tests instead of the real one (swap the
  provider) — the same DIP benefit as Spring (Software Design Phase 2/6). Components are
  tested in isolation.
- **Decoupling:** components depend on a *type/abstraction*, not a concrete construction
  (`new`), so implementations swap freely (real API vs in-memory).
- **Single source of truth:** shared state lives in one injected service, not duplicated
  across components.

## Perspective

If you understood Spring's IoC container, you already understand Angular DI: declare a
service, register it (usually `providedIn: 'root'`), and `inject()` it wherever needed.
The injector owns construction and lifetime; your classes just declare what they need.
This is what keeps components thin and the app testable — and it's the same principle
the Software Design track calls Dependency Inversion.
