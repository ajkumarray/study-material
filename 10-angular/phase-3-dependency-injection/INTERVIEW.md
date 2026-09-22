<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · templates binding](../phase-2-templates-binding/NOTES.md) | [Phase 4 · reactivity ➡](../phase-4-reactivity/NOTES.md)
<!-- /nav -->

# Phase 3 — Dependency Injection & Services: Interview Q&A

⭐ = asked constantly.

**Q: What is a service and why use one?** ⭐⭐
A class — typically decorated `@Injectable` — that holds shared state, business logic,
or I/O, deliberately kept out of components so components stay thin, presentation-
focused, and easy to test. In this repo, `ExpenseService` holds the expense list as a
signal, exposes it read-only, and is the single shared source of truth for the list,
detail, and add-expense components — none of them duplicate or own their own copy of
the data. It's the same separation-of-concerns argument Spring makes for pulling logic
out of controllers and into `@Service` beans.

**Q: What does `providedIn: 'root'` do, and why not just register the service in
`app.config.ts`'s `providers` array?** ⭐⭐
`providedIn: 'root'` registers the service with the root injector as an app-wide
singleton — one shared instance for everything that injects it — and, crucially, makes
it **tree-shakable**: if nothing in the compiled app ever injects the service, the
bundler drops it entirely. A manually-listed `providers: [ExpenseService]` entry in
`app.config.ts` achieves the same singleton behavior but loses tree-shaking, because the
bundler can't prove the entry is unused just by looking at the array. `providedIn:
'root'` is the standard choice for any ordinary shared service.

**Q: `inject()` vs constructor injection — what's actually different?** ⭐
Both ask the injector for a dependency by type and get the same result; the difference
is where each can run. Constructor injection (`constructor(private s: Svc) {}`) only
works as a constructor parameter. `inject(Svc)` works anywhere in an "injection
context" — field initializers, constructors, and plain functions Angular explicitly
runs with an active injector, like a functional route guard. That last point is the
real reason `inject()` displaced constructor injection as the idiomatic style: it lets
DI-dependent code exist *outside* a class, which is what makes Angular's functional
guards/resolvers/interceptors possible — a class-based guard would need a constructor
just to hold one dependency.

```ts
private readonly service = inject(ExpenseService);   // field initializer, no constructor needed
```

**Q: How does Angular's DI resolve a dependency?** ⭐
Injectors form a tree mirroring the component tree — the root injector at the top,
optionally a route injector, optionally a component injector. When something asks for
a dependency, Angular starts at the requesting component's own injector and walks
**up** the tree until it finds a matching provider; the first (nearest) match wins.
This lets you register a provider globally at the root (the common case) or override it
locally by re-declaring the same token in a component's or route's own `providers`
array — components deeper in the tree see the closer provider.

**Q: How do you inject something that isn't a class — say, a config object?**
*nuance*
Use an `InjectionToken<T>` as the lookup key and register a value for it. TypeScript
interfaces are erased at compile time — there's no runtime representation left for the
injector to key on — so `inject(SomeInterface)` isn't possible. A token is a real
object that exists at runtime, so it can serve as the DI key, while the interface only
describes the *shape* of what comes back.

```ts
export const APP_CONFIG = new InjectionToken<AppConfig>('app.config');
providers: [{ provide: APP_CONFIG, useValue: { apiBaseUrl: '/api' } }],
// later: private readonly config = inject(APP_CONFIG);
```

**Q: What are the different provider forms (`useClass`, `useValue`, `useExisting`,
`useFactory`) and when would you use each?** ⭐
`useClass` constructs an instance of a given class for a token — the mechanism for
swapping a real implementation for a mock (`{ provide: ExpenseService, useClass:
MockExpenseService }`). `useValue` hands back a pre-built value with no construction —
good for constants/config objects or a hand-built test double. `useExisting` aliases
one token to an already-registered provider's *same instance* — two lookup keys, one
object, useful when renaming a token while keeping old references working. `useFactory`
computes the value with a function, optionally injecting its own dependencies via
`deps: [...]` — for values needing real setup logic. In practice `useClass`/`useValue`
swaps are what you reach for constantly in tests; `useExisting`/`useFactory` show up in
more advanced library/config code.

**Q: How do you get a new instance per component instead of a singleton?**
Provide the service in the component's own `providers` array (or a route's providers)
instead of relying on `providedIn: 'root'`. Angular then creates a fresh instance for
that specific component instance (and shares it with that component's descendants),
rather than resolving up to the root singleton — useful for feature- or dialog-local
state that should reset every time the component is created rather than persist
app-wide.

```ts
@Component({
  selector: 'app-expense-wizard',
  providers: [WizardStateService],   // fresh instance per <app-expense-wizard>
})
export class ExpenseWizard {}
```

**Q: How does DI help testing, concretely?** ⭐
You configure the test's injector (`TestBed.configureTestingModule({ providers: [...] })`)
to provide a fake in place of the real service — `{ provide: ExpenseService, useValue:
fakeExpenseService }`. Any component resolved from that test module which injects
`ExpenseService` receives the fake, so the component's behavior can be verified against
controlled, deterministic data with no network calls or real shared state. This works
*only* because the component depends on the abstraction (the type `ExpenseService`),
not a concrete `new ExpenseService()` call baked into its code — the Dependency
Inversion payoff.

**Q: What do `@Optional()`, `@Self()`, and `@SkipSelf()` do?** *nuance*
These are parameter decorators that change *how* the injector searches the tree for a
dependency, mostly relevant when building composable directive/component APIs meant to
be nested. `@Optional()` returns `null` instead of throwing if no provider is found —
for genuinely optional dependencies. `@Self()` restricts the lookup to the requesting
component/directive's own injector only (no walking up) — throws if not found there.
`@SkipSelf()` does the opposite: skip the requesting component's own injector and start
the search at its parent, useful when a nested directive wants its *ancestor's*
instance of a service rather than a coincidentally re-provided local one (e.g. a
form-control directive wanting the enclosing form's shared validation context). These
show up in library code (Angular Material internals) far more than everyday app code,
but knowing them signals real DI depth.

**Q: How does Angular DI compare to Spring's?**
Conceptually the same Inversion-of-Control container: declare injectables
(`@Injectable`/`@Component`/`@Service`), register them (`providedIn`/`providers` vs
component scanning or `@Bean`), and let the framework construct and supply instances by
type rather than the class calling `new` itself. The concrete differences are mostly
surface-level: Angular's injectors are explicitly hierarchical and mirror the UI
component tree (so provider *scope* — root, route, component — maps directly onto
*where in the tree* something is visible), where Spring's application context is
conceptually flatter and scope is controlled via bean scope annotations and profiles
instead of a tree position.

*Follow-up: "If both default to singleton, when would you actually want a
non-singleton service in Angular, and how would that look in Spring terms?"* — Angular:
component-level `providers` for state that should reset per feature instance (a wizard,
a dialog). Spring's rough equivalent is a bean with `@Scope("prototype")` or a scope
tied to a web request/session rather than the singleton default — different mechanism,
same underlying idea of intentionally *not* sharing one instance app-wide.
