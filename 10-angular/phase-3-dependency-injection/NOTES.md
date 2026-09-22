<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · templates binding](../phase-2-templates-binding/NOTES.md) | [Phase 4 · reactivity ➡](../phase-4-reactivity/NOTES.md)
<!-- /nav -->

# Phase 3 — Dependency Injection & Services: Notes

**Dependency Injection (DI)** is Angular's backbone, and the clearest bridge from a
Spring background: a class declares *what* it needs, and a framework-managed
**injector** supplies (and owns the lifetime of) an instance — the class never calls
`new` on its own dependencies. This is Inversion of Control (see Software Design Phase
6 — Dependency Inversion) built into the framework rather than left to convention.

## Services

A **service** is a plain TypeScript class — usually decorated with `@Injectable` —
that holds shared state, business logic, or I/O, kept separate from components so
components stay thin and focused on presentation.

```ts
// expense-app/src/app/expense.service.ts — the real service this app is built around.
@Injectable({ providedIn: 'root' })
export class ExpenseService {
  private readonly _expenses = signal<Expense[]>([
    { id: 1, description: 'Lunch', amountCents: 1250, category: 'food' },
    { id: 2, description: 'Bus pass', amountCents: 3000, category: 'transport' },
    { id: 3, description: 'Rent', amountCents: 90000, category: 'housing' },
  ]);

  readonly expenses = this._expenses.asReadonly();
  readonly totalCents = computed(() => this._expenses().reduce((s, e) => s + e.amountCents, 0));

  getById(id: number): Expense | undefined {
    return this._expenses().find((e) => e.id === id);
  }

  add(input: Omit<Expense, 'id'>): void {
    this._expenses.update((list) => [...list, { id: list.length + 1, ...input }]);
  }
}
```

- **Key Concepts**
  - **Separation of concerns**: `ExpenseList`, `ExpenseDetail`, and `AddExpense` all
    inject this *same* service instead of each holding its own copy of the expense
    list — the service is the single source of truth, and every component that injects
    it sees the same signal-backed state update reactively.
  - **`@Injectable`**: marks a class as available for DI. It's needed on any class that
    itself has injected dependencies (so Angular knows how to construct it), and by
    convention on every service even with no dependencies, for consistency and future-
    proofing.
  - **Encapsulated mutation**: `_expenses` is private; `expenses` exposes it
    **read-only** via `.asReadonly()`. Components read `service.expenses()` but can
    only *change* the list by calling `service.add(...)` — the service controls how its
    own state can be mutated, the same encapsulation discipline as a well-designed
    Java class (Software Design Phase 1).

**Why it's useful**: without a shared service, "how many expenses do I have" would need
to be lifted to a common ancestor and passed down through inputs/outputs at every
level (prop-drilling) — exactly the pain DI-injected shared state avoids. It's also
what makes components independently testable: a component under test can be given a
fake `ExpenseService` instead of exercising the real one.

## `providedIn: 'root'`

`@Injectable({ providedIn: 'root' })` registers a service with the **root injector**,
making it an app-wide **singleton** and marking it **tree-shakable**.

- **Key Concepts**
  - **Singleton**: exactly one instance is created, lazily, the first time anything
    injects it, and that same instance is shared by every subsequent injector — just
    like a Spring `@Service` bean with the default singleton scope. The `expense-app`'s
    `ExpenseService` is one instance shared by the list, detail, and add-expense
    components.
  - **Tree-shakable**: because the *provider* is declared on the service itself
    (`providedIn: 'root'`) rather than in a separate `providers` array, if nothing in
    the compiled app ever injects the service, the bundler can strip it out entirely —
    unused services cost nothing in production bundle size.
  - **Alternative scopes** (`providedIn: 'platform'`, `providedIn: 'any'`) exist for
    rarer cases — a service shared across multiple Angular apps on the same page, or
    one instance per lazily-loaded module — but `'root'` is the default choice for
    ordinary app-wide services.

**Why it's useful**: `providedIn: 'root'` is the single line that turns a plain class
into "the one shared place expense state lives," with zero manual wiring (no need to
list it in `app.config.ts`'s `providers` array) and no bundle-size cost if unused.

## `inject()` vs constructor injection

Two equivalent ways to ask the injector for a dependency by type.

```ts
// Modern: the inject() function — works in field initializers, outside constructors,
// and composes into plain helper functions. Used throughout expense-app.
export class ExpenseList {
  private readonly service = inject(ExpenseService);
  readonly expenses = this.service.expenses;
}
```

```ts
// Classic: constructor-parameter injection — Angular reads the parameter's type
// via TypeScript's emitted metadata and supplies a matching instance.
export class ExpenseList {
  constructor(private readonly service: ExpenseService) {}
}
```

| | Constructor injection | `inject()` |
|---|---|---|
| Where it can run | Only in a constructor parameter | Field initializers, constructors, and any function called during an injection context (e.g. a functional route guard) |
| Boilerplate | A `constructor(...)` block per dependency | One line per dependency, no constructor needed |
| Composability | Tied to a specific class | Usable inside standalone helper functions that need DI (e.g. a functional guard, a factory) |
| Works with signals | Fine | Slightly more idiomatic — declare `private x = inject(Y)` right next to the signal fields it initializes |

- **Key Concepts**
  - Both resolve the dependency **by type** — Angular looks up a provider registered
    for that class (or `InjectionToken`, below) in the injector tree.
  - `inject()` must be called during an **injection context** — inside a constructor,
    a field initializer, or a function Angular explicitly runs one (like a functional
    route guard or `provideAppInitializer` factory). Calling it later (e.g. inside a
    `setTimeout` callback) throws, because there's no current injector to ask.
  - `inject()` is now the preferred style in idiomatic modern Angular code — it's what
    every component in `expense-app` uses.

**Why it's useful**: `inject()` removes constructor boilerplate and, critically, lets
DI-dependent code live outside a class entirely — this is what makes Angular's modern
**functional guards/resolvers/interceptors** (Phase 5) possible, since a plain function
can call `inject()` without needing to be a class with a constructor.

## Injection tokens

An **`InjectionToken<T>`** is a DI key for a dependency that isn't a class — a
configuration object, a primitive value, or an abstraction with no runtime
representation.

```ts
// Interfaces are erased at compile time (TypeScript track — no runtime value to key
// on), so you can't `inject(SomeInterface)`. A token stands in as a real runtime key.
export interface AppConfig {
  apiBaseUrl: string;
  pageSize: number;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('app.config');

// Registered once, usually in app.config.ts:
providers: [
  { provide: APP_CONFIG, useValue: { apiBaseUrl: '/api', pageSize: 20 } },
],

// Injected anywhere by the token, typed as AppConfig:
private readonly config = inject(APP_CONFIG);
```

- **Key Concepts**
  - **Why not just inject the interface?** TypeScript interfaces exist only at compile
    time and are erased when the code is compiled to JavaScript — there's nothing left
    at runtime for the injector to look up. A token is a real object that exists at
    runtime and can serve as the lookup key, with the interface only used for typing
    what comes back.
  - **`useValue`** provides a plain value for the token (shown above); `useFactory`
    (below) can compute the value, e.g. reading it from `environment.ts`.
  - Tokens are also used for **multi-providers** — several values registered under the
    same token, retrieved together as an array (`{ provide: TOKEN, useValue: x,
    multi: true }`), the mechanism `HTTP_INTERCEPTORS`-style APIs are built on.

**Why it's useful**: tokens are how you bring non-class configuration (base URLs, flags,
feature toggles) into the same DI system as your services, so a component doesn't need
to import a config module directly — it just injects the token, and tests can swap in
a different config value for that token.

## Provider types (`useClass`, `useValue`, `useExisting`, `useFactory`)

`providedIn: 'root'` is shorthand for the common case. The full **provider** syntax
lets you control exactly what an injector hands back for a given token/class.

```ts
providers: [
  ExpenseService,                                         // shorthand for { provide: ExpenseService, useClass: ExpenseService }
  { provide: ExpenseService, useClass: MockExpenseService },   // swap implementation — e.g. in tests
  { provide: APP_CONFIG, useValue: { apiBaseUrl: '/api' } },   // provide a fixed value
  { provide: LegacyLogger, useExisting: Logger },              // alias: same instance, different lookup key
  {
    provide: APP_CONFIG,
    useFactory: (http: HttpClient) => computeConfig(http),      // compute the value, with its own deps
    deps: [HttpClient],
  },
],
```

- **Key Concepts**
  - **`useClass`**: construct an instance of the given class when this token is
    requested — the mechanism behind swapping a real service for a mock/fake in tests
    without touching the component under test.
  - **`useValue`**: hand back a pre-built value as-is (no construction) — for constants,
    config objects, or a hand-built fake in a test.
  - **`useExisting`**: alias one token to an *already-registered* provider's instance —
    two different injection keys resolve to the exact same singleton instance, useful
    when renaming/deprecating a token while keeping backward compatibility.
  - **`useFactory`**: compute the provided value with a function, optionally injecting
    its own dependencies via `deps: [...]` — for values that need setup logic beyond a
    simple `new`.

**Why it's useful**: this is precisely the lever that makes Angular DI valuable for
testing and environment-specific configuration — swap `useClass: RealApiService` for
`useClass: FakeApiService` in a test module's providers, and every component under test
that injects `ApiService` gets the fake, with zero changes to the component itself.

## Hierarchical injectors & provider scope

Angular's injectors form a **tree** that mirrors the component tree. Resolving a
dependency means walking **up** from the requesting component to the root, using the
first matching provider found.

- **Key Concepts**
  - **`providedIn: 'root'`** → registered on the root injector → one instance shared
    app-wide (the default, and what `ExpenseService` uses).
  - **Component-level `providers`** → registered on that specific component's own
    injector → a **new instance per component instance**, shared only by that component
    and its descendants. Useful for state that should be isolated per feature instance
    (e.g. a wizard/dialog that should reset each time it's opened, rather than reusing
    app-wide state).
    ```ts
    @Component({
      selector: 'app-expense-wizard',
      providers: [WizardStateService],   // a fresh WizardStateService per <app-expense-wizard>
      // ...
    })
    export class ExpenseWizard {}
    ```
  - **Route-level `providers`**: services scoped to a lazily-loaded route and its
    children — shared across that route's component tree, destroyed when you navigate
    away, without polluting the root injector.
  - **Resolution order**: when a component asks for a dependency, Angular checks that
    component's own injector first, then walks up through ancestor injectors, and
    finally the root injector — the *nearest* provider wins, which is how
    component-level and route-level providers can locally "override" a root-level one.

**Why it's useful**: this hierarchy lets you share state globally (`providedIn:
'root'`) *or* scope it tightly (one instance per dialog, per route) using the exact
same `@Injectable`/`inject()` mechanism — no separate API to learn, unlike ad-hoc
prop-drilling or manually-scoped React Context providers.

## Optional, self, and skipSelf dependency modifiers

A small set of parameter decorators fine-tune *how* Angular looks up a dependency in
the injector tree — mostly relevant when building reusable directives/components meant
to be nested.

- **Key Concepts**
  - **`@Optional()`**: don't throw if the dependency isn't found — inject `null`
    instead. Use when a dependency is genuinely optional (e.g. an analytics service
    that may not be configured in every environment).
  - **`@Self()`**: only look at the requesting component/directive's *own* injector —
    don't walk up the tree. Throws if not found there.
  - **`@SkipSelf()`**: skip the requesting component's own injector and start the
    search at its parent — used when a component wants "the *ancestor's* instance of
    this service," not its own local one (e.g. a form-control directive that wants the
    parent form's validation context, not a coincidentally-local re-provision of it).
  - **`@Host()`**: stop the search at the current component's host element — don't walk
    past a defined host boundary.

**Why it's useful**: these are the tools behind building composable, nestable
component/directive APIs (the kind of thing Angular Material's form-field and menu
components use internally) — less common in everyday app code, but a strong signal of
DI depth in an interview.

## DI and testing

**Testability** is the practical payoff of DI: swap a real dependency for a
test double without changing the class under test.

```ts
TestBed.configureTestingModule({
  providers: [
    { provide: ExpenseService, useValue: fakeExpenseService },
  ],
});
// Any component/service resolved from this TestBed that injects ExpenseService
// gets fakeExpenseService instead of the real one — no network, no real state.
```

- **Key Concepts**
  - **Dependency Inversion in practice**: `ExpenseList` depends on the *type*
    `ExpenseService`, not a concrete construction (`new ExpenseService()`) — so at test
    time, the injector can hand it any object matching that type, real or fake.
  - **Isolation**: because DI removes hard-coded construction from component code,
    each component/service can be unit-tested in isolation from the rest of the app.

**Why it's useful**: this is the same benefit Dependency Inversion gives in Spring
(Software Design Phase 2/6) — code depends on abstractions, concrete implementations
are supplied externally, and tests supply a different (controlled, fast, deterministic)
implementation than production does.

## Comparing Angular DI to Spring

| | Angular | Spring |
|---|---|---|
| Marking a class injectable | `@Injectable()` | `@Component`/`@Service`/`@Repository` |
| Default scope | Singleton (`providedIn: 'root'`) | Singleton (default bean scope) |
| Registration | `providedIn` on the class, or a `providers` array | Component scanning, or explicit `@Bean` definitions |
| Injector structure | Hierarchical tree mirroring the component tree | Flat application context (profiles/scopes for variation) |
| Requesting a dependency | `inject(Type)` or constructor param | Constructor injection (recommended) or field `@Autowired` |
| Non-class dependency | `InjectionToken<T>` | Qualifier + bean name/type, or a `@Configuration` bean |

**Why it's useful**: if you already understand Spring's IoC container, you already
understand the *shape* of Angular DI — the differences are largely surface syntax, not
conceptual. This mapping is worth saying explicitly in an interview; it signals
transferable understanding rather than memorized syntax.

## Summary — Key takeaways

- A **service** (`@Injectable`) holds shared state/logic; components stay thin and
  inject what they need instead of constructing it.
- `providedIn: 'root'` = app-wide singleton + tree-shakable — the default choice for
  shared services (`ExpenseService` in this repo).
- `inject()` is the modern, preferred way to get a dependency — works in field
  initializers and plain functions (functional guards), not just constructors.
- `InjectionToken<T>` is how you inject non-class values (config, primitives) since
  interfaces are erased at runtime.
- Provider forms (`useClass`, `useValue`, `useExisting`, `useFactory`) control exactly
  what an injector hands back — `useClass`/`useValue` swaps are the core testing lever.
- Injectors are hierarchical (root → route → component); the nearest provider wins,
  letting you share globally or scope locally without a separate API.
- DI's real payoff: **testability** (swap real for fake) and **decoupling** (depend on
  a type, not a `new`) — the same Dependency Inversion principle Spring is built on.
