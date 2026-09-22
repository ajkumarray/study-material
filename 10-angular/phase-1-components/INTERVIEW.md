<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · templates binding ➡](../phase-2-templates-binding/NOTES.md)
<!-- /nav -->

# Phase 1 — Angular & Standalone Components: Interview Q&A

⭐ = asked constantly.

**Q: How is Angular different from React?** ⭐⭐
Angular is a complete, opinionated framework: routing, forms, an HTTP client,
dependency injection, testing utilities, and a compiler all ship first-party and are
versioned together. React is a UI-rendering library; everything else (routing, state
management, build tooling) is a separate ecosystem choice you assemble yourself.

Structurally, Angular components are TypeScript classes decorated with `@Component`,
using dependency injection for services and either RxJS Observables or Signals for
reactive state. React components are functions that use hooks (`useState`,
`useEffect`, `useContext`) for the same jobs. Neither is "better" in the abstract —
Angular trades flexibility for consistency (useful across large teams where you want
one canonical way to do routing or forms), React trades a smaller core for freedom to
pick your own stack.

*Follow-up: "Which would you choose for a small team building an MVP?"* — React's
smaller learning curve and flexible ecosystem often wins for fast iteration; Angular's
enterprise-oriented structure pays off more as team size and app longevity grow.

**Q: What is a component in Angular?** ⭐
A TypeScript class decorated with `@Component`, whose metadata specifies a `selector`
(the custom HTML tag it registers, e.g. `app-expense-list`), a `template`/`templateUrl`
(HTML plus Angular's binding syntax), and `styleUrl`/`styleUrls` (CSS scoped to that
component). Class fields hold the state the template reads; class methods are the event
handlers the template calls. Example from this repo's `expense-app`:

```ts
@Component({
  selector: 'app-expense-list',
  imports: [RouterLink, AddExpense, CurrencyPipe],
  templateUrl: './expense-list.html',
})
export class ExpenseList {
  private readonly service = inject(ExpenseService);
  readonly expenses = this.service.expenses;   // template state
}
```

**Q: What are standalone components, and why did Angular move to them?** ⭐⭐
Components that declare their own template dependencies in an `imports` array instead
of being registered inside an `NgModule`. They became stable at v15, preview at v14,
and are the CLI's *default* since v19 (`standalone: true` is implicit — you don't even
write it). They removed a whole layer of indirection: previously, to know what a
template could use, you had to trace which `NgModule` declared the component and what
that module imported. With standalone, the answer is right there in the component's own
decorator. It also improves tree-shaking and enables finer-grained lazy loading — a
route can lazy-load a single component instead of pulling in an entire feature module.

**Q: What's the difference between a standalone app and an NgModule-based app?**
NgModule apps group `declarations` (components/directives/pipes owned by that module),
`imports` (other modules whose exports it needs), `exports` (what it makes available to
importers), and `providers` (DI registrations) inside `@NgModule` classes, and bootstrap
via `platformBrowserDynamic().bootstrapModule(AppModule)`. Standalone apps skip that
layer entirely: each component lists what it needs directly, app-wide providers live in
`app.config.ts`, and the app bootstraps with `bootstrapApplication(RootComponent,
appConfig)`. Standalone is simpler and is what every current CLI project (and this
repo's `expense-app`) uses; NgModules remain common in codebases that haven't migrated
(see Phase 6).

**Q: How does an Angular app start up?** ⭐
`main.ts` calls `bootstrapApplication(App, appConfig)`. This mounts the root standalone
component (`App`) into the `<app-root>` element in `index.html`, using `appConfig`
(from `app.config.ts`) to configure the root injector — `provideRouter(routes)`,
`provideHttpClient()`, global error listeners, etc. Bootstrapping is async and returns a
Promise, so production `main.ts` files always chain `.catch(err => console.error(err))`
to surface a startup failure instead of silently swallowing it. A legacy (NgModule)
app instead bootstraps a root `AppModule` via `platformBrowserDynamic().bootstrapModule(AppModule)`.

**Q: What is view encapsulation, and what are the three modes?**
Angular's mechanism for scoping a component's CSS so it can't leak globally and global
CSS can't leak in. `Emulated` (default) rewrites selectors with generated attributes to
fake Shadow-DOM-style scoping using regular CSS — the most compatible option.
`ShadowDom` uses the browser's real Shadow DOM for true encapsulation but opts out of
some global styling patterns. `None` disables scoping — the component's styles become
global CSS, which is rarely what you want. You set it per component via `encapsulation:
ViewEncapsulation.X` in the decorator.

*Follow-up: "Why would `::ng-deep` not work with `ShadowDom` encapsulation?"* —
`::ng-deep` is an Angular-specific escape hatch that works by defeating the *emulated*
attribute-based scoping; it has no effect against a real Shadow DOM boundary, which the
browser enforces natively.

**Q: What is content projection, and when do you use `<ng-content>`?**
A mechanism for a parent to pass markup — not just data — into a child component's
template, similar to React's `children` prop. A bare `<ng-content />` projects any
unmatched content into that spot; `<ng-content select="[title]" />` (or a class/tag
selector) projects only matching elements, letting a component define multiple named
insertion points (e.g. a card component with a title slot and a body slot). It's the
right tool for generic wrapper components — cards, modals, layout shells — whose
*content* varies per use but whose structure/styling should stay consistent.

**Q: Walk through the Angular component lifecycle hooks and when each fires.** ⭐⭐
In creation order: the **constructor** runs first (plain class construction — inputs
aren't guaranteed set yet, so avoid input-dependent logic here). **`ngOnChanges(changes)`**
fires next, and again on every subsequent change to a bound input — `changes` is a map
of input name to `{previousValue, currentValue, firstChange}`. **`ngOnInit()`** fires
once, right after the first `ngOnChanges`, and is the idiomatic place to fetch initial
data because inputs are guaranteed populated by then. **`ngAfterContentInit()`** and
**`ngAfterViewInit()`** fire after projected content and the component's own view are
fully initialized respectively — the right place to read a `@ViewChild`/`@ContentChild`.
Finally **`ngOnDestroy()`** fires once, right before Angular removes the component —
the place to unsubscribe from manual RxJS subscriptions, clear timers, and detach
observers to avoid leaks.

**Q: Why put data-fetching in `ngOnInit` instead of the constructor?** ⭐
The constructor runs during object construction, before Angular has necessarily bound
`@Input()`/`input()` values or set up the component's context — code that depends on an
input value can run against `undefined`. `ngOnInit` runs after the first
`ngOnChanges`, guaranteeing inputs are set, and semantically separates "construct the
object" (constructor, DI) from "the component's inputs are ready, do your setup work"
(`ngOnInit`).

**Q: What would happen if you forgot to implement `ngOnDestroy` and unsubscribe from a
manual RxJS subscription?** ⭐
The subscription (and anything it closes over — the component instance, injected
services, DOM references) stays alive even after Angular removes the component from the
view, because the Observable still holds a live reference to the subscriber callback.
Over time, repeated navigation to and from that component leaks memory and can keep
doing unwanted work (network polling, logging) in the background. This is exactly why
the idiomatic pattern is to avoid manual subscriptions where possible — use the `async`
pipe or `takeUntilDestroyed()` (Phase 4), which tie the subscription to the component's
lifetime automatically.

**Q: What are inputs and outputs, and what's the difference between the decorator API
and the newer signal-based API?** ⭐⭐
Both are how a parent and child component communicate: an **input** flows data down
(`@Input()` or `input()`); an **output** emits events up (`@Output() + EventEmitter` or
`output()`). The decorator API stores the value as a plain class field — reading it
isn't itself reactive, so reacting to a change needs `ngOnChanges`. The signal-based
API (`input()`/`input.required()`, stable since v17.1) makes the input a real signal:
you read it by calling it (`this.id()`), and it composes directly into a `computed()`
or an `effect()` without any lifecycle-hook boilerplate. `input.required<T>()` also
gets compile-time enforcement that the input was bound, versus a runtime-only
`@Input({ required: true })` check.

```ts
readonly id = input.required({ transform: (v: string) => Number(v) });
readonly expense = computed(() => this.service.getById(this.id()));
```

**Q: How does your Spring/Java background map onto Angular?** *nuance*
Directly, and it's a useful thing to say out loud in an interview: `@Injectable`
services registered `providedIn: 'root'` behave like singleton Spring `@Service` beans;
the Angular injector is the same Inversion-of-Control container idea Spring popularized
(see Software Design Phase 6 — Dependency Inversion); decorators (`@Component`,
`@Injectable`, `@Input`) play the same metadata-annotation role as Spring's
`@Component`/`@Autowired`. Angular is sometimes informally described as "Spring for the
frontend" for exactly this reason, which is why it tends to appeal to backend-heavy
teams moving into full-stack work.

**Q: What does the Angular CLI actually do for you?**
It scaffolds new projects and generated code following Angular's file/naming
conventions (`ng new`, `ng generate component/service/...`), runs a dev server with
live reload (`ng serve`), produces an optimized production build (`ng build`), and runs
the test suite (`ng test`). Because every Angular project uses the same CLI and the
same generated file layout, moving between Angular codebases at different companies
involves far less ramp-up than moving between differently-assembled React projects.
