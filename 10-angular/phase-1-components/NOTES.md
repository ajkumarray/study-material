<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · templates binding ➡](../phase-2-templates-binding/NOTES.md)
<!-- /nav -->

# Phase 1 — Angular & Standalone Components: Notes

## What Angular is

Angular is a **complete, opinionated front-end framework** — not a UI library you glue
things onto. Out of the box it ships routing, forms, an HTTP client, dependency
injection, a testing setup, and a build/compiler pipeline, all first-party, versioned
together, and TypeScript-first. The CLI (`ng`) is the tool you use for all of it:
scaffolding, serving, building, testing, and code generation.

- **Key Concepts**
  - **Framework vs library**: React gives you components and rendering; you assemble
    routing, state management, and build tooling yourself from the ecosystem (React
    Router, Redux/Zustand, Vite, etc.). Angular decides those for you — one router, one
    forms module, one HTTP client, one CLI — trading flexibility for consistency across
    teams and projects.
  - **TypeScript-first**: Angular is written in and designed around TypeScript. Types
    flow through components, services, forms, and the router (e.g. typed `Routes`,
    typed `FormGroup`s in modern Angular).
  - **The CLI (`ng`)**: `ng new` scaffolds a project, `ng generate component/service`
    creates boilerplate following Angular's file/naming conventions, `ng serve` runs a
    dev server with hot reload, `ng build` produces an optimized production bundle,
    `ng test` runs unit tests. Because the CLI encodes Angular's conventions, project
    structure is consistent across companies and teams — a big part of Angular's
    "enterprise" reputation.
  - **Mental bridge from React**: class-based components decorated with `@Component`
    instead of function components; dependency injection instead of prop-drilling or
    Context; RxJS Observables and Signals instead of hooks (`useState`/`useEffect`);
    framework-managed change detection instead of a virtual DOM diff you never see.
  - **Mental bridge from Spring**: `@Component`/`@Injectable` are directly analogous to
    Spring's `@Component`/`@Service` annotations. The **injector** is the same
    Inversion-of-Control container idea (see Software Design Phase 6 — Dependency
    Inversion). A service registered with `providedIn: 'root'` behaves like a
    singleton Spring bean. If you already understand Spring's IoC container, DI in
    Angular (Phase 3) will feel almost identical.

**Why it's useful**: knowing Angular is a *framework* explains almost every design
decision you'll meet later — why there's one canonical way to route, one canonical way
to build forms, why DI is baked in rather than bolted on. It also explains why Angular
apps scale well across large teams: fewer architectural decisions are up for debate.

**Summary**
- Angular = framework (routing + forms + HTTP + DI + testing + compiler, first-party).
- The CLI (`ng`) is the standard way to scaffold, run, build, and test an app.
- Come from React: decorators/DI/RxJS-Signals replace hooks/Context/prop-drilling.
- Come from Spring: decorators ≈ annotations, injector ≈ IoC container.

## Component anatomy

A **component** is the smallest building block of an Angular UI: a TypeScript class
decorated with `@Component` that pairs a piece of the DOM (a template) with the state
and behavior (class fields and methods) that drive it.

```ts
import { Component, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',            // the custom element tag: <app-root></app-root>
  imports: [RouterOutlet, RouterLink],  // template dependencies (standalone component)
  templateUrl: './app.html',       // external template file (or inline `template: '...'`)
  styleUrl: './app.css',           // external stylesheet, scoped to this component
})
export class App {
  protected readonly title = signal('expense-app');  // state the template can read
}
```

This is the real root component of the `expense-app` project
(`expense-app/src/app/app.ts`).

- **Key Concepts**
  - **`selector`**: the custom HTML tag Angular registers for this component
    (`<app-root>`, `<app-expense-list>`). By convention, feature selectors are prefixed
    (`app-`) to avoid colliding with native or third-party elements.
  - **`template`/`templateUrl`**: the view. Inline `template: '<h1>{{ title() }}</h1>'`
    works for tiny components; `templateUrl` points to a separate `.html` file, which is
    the convention for anything non-trivial (see `expense-list.html`,
    `expense-detail.html`).
  - **`styleUrl`/`styleUrls`**: CSS scoped to this component only (see *View
    encapsulation* below). `styleUrl` (singular) takes one file; `styleUrls` (plural,
    older API) takes an array.
  - **Class fields = template state**: any field on the class (plain value, or —
    idiomatically — a `signal()`) is available for the template to read via
    interpolation or binding (Phase 2).
  - **Class methods = event handlers**: methods invoked from the template in response
    to user interaction, e.g. `(click)="open.set(true)"` in `add-expense.html`.
  - **One class, one responsibility**: a component's job is to present a slice of state
    and react to input; business logic and shared state belong in an injected
    **service** (Phase 3), not the component class — this keeps components thin and
    testable.

**Why it's useful**: this is the shape of *every* Angular UI unit — a list item, a
whole page, a button. Understanding the decorator's metadata (`selector`, `template`,
`styleUrl`, `imports`) is a prerequisite for everything else in the track: binding
(Phase 2) happens inside the template this decorator wires up, DI (Phase 3) supplies
the class's dependencies, and routing (Phase 5) decides which component's template is
currently on screen.

## View encapsulation

**View encapsulation** is Angular's mechanism for scoping a component's CSS so it
can't leak out and affect the rest of the page, and so the rest of the page's CSS
can't leak in.

- **Key Concepts**
  - **`ViewEncapsulation.Emulated`** (the default): Angular rewrites your selectors and
    adds unique attributes (e.g. `_ngcontent-xyz`) to the component's host element and
    every element in its template, then scopes the compiled CSS to those attributes.
    This *emulates* Shadow DOM scoping using regular CSS, without needing real Shadow
    DOM — better browser/tooling compatibility.
  - **`ViewEncapsulation.ShadowDom`**: uses the browser's real Shadow DOM. True
    encapsulation (not even `::ng-deep` can pierce it from outside), but opts you out
    of some global styling conventions (e.g. CSS custom properties still cross the
    boundary; regular selectors don't).
  - **`ViewEncapsulation.None`**: no scoping at all — the component's styles become
    global. Rarely appropriate; occasionally used for a small set of intentionally
    global base styles.
  - You set it per component: `@Component({ encapsulation: ViewEncapsulation.ShadowDom, ... })`.
  - **`:host`** selects the component's own host element from inside its stylesheet
    (e.g. `:host { display: block; }`) — you can't target it with a plain element
    selector because the host element lives in the *parent's* template, not this
    component's own template.

**Why it's useful**: without encapsulation, a rule like `h2 { color: red; }` written
for one component's detail page would silently restyle every `<h2>` in the app. Scoped
styles let teams write plain, unprefixed CSS class names per component without a
BEM-style naming convention or CSS Modules — Angular does the scoping for you at
compile time.

## Standalone components (the modern default)

**Standalone components** declare their own template dependencies directly, instead of
being registered inside an `NgModule`. As of Angular v19, standalone is the **default**
for anything the CLI generates (`standalone: true` is implicit — you don't even write
it); Angular 17–18 had it as an explicit opt-in, and pre-17 Angular required NgModules
for everything.

```ts
// Standalone: the component lists exactly what its template needs, right here.
@Component({
  selector: 'app-expense-list',
  imports: [RouterLink, AddExpense, CurrencyPipe],   // directives/components/pipes used in the template
  templateUrl: './expense-list.html',
})
export class ExpenseList { /* ... */ }
```

```ts
// Legacy (pre-standalone): components were declared inside an NgModule, and the
// module's `imports` array supplied dependencies to every declared component.
@NgModule({
  declarations: [ExpenseListComponent],
  imports: [CommonModule, RouterModule, AddExpenseComponent],
})
export class ExpenseModule {}
```

- **Key Concepts**
  - **`imports` array**: lists every component, directive, and pipe the *template*
    references — `RouterLink` for `routerLink`, `CurrencyPipe` for the `| currency`
    pipe, `AddExpense` for the `<app-add-expense />` child component. This is explicit
    and local: reading the decorator tells you every template dependency, no need to
    trace through a module tree.
  - **No `NgModule` needed**: standalone components, directives, and pipes can be used
    directly by any other standalone component's `imports` array, or bootstrapped
    directly (`bootstrapApplication`).
  - **Why it replaced NgModules**: NgModules added an indirection layer (which module
    declares this component? which module do I import to use that pipe?) that mostly
    existed to group `declarations`/`imports`/`exports`/`providers`. Standalone removes
    that layer — less boilerplate, dependencies are visible at the point of use, and it
    enables finer-grained lazy loading (a route can lazy-load a single standalone
    component instead of an entire feature module).
  - **Legacy knowledge still matters**: production codebases from before v14–15 (or not
    yet migrated) still use NgModules. Know the shape (`@NgModule({ declarations,
    imports, exports, providers, bootstrap })`) so you can read and maintain one — see
    Phase 6 for the migration path from NgModules to standalone.

**Why it's useful**: standalone is what you'll write in any current Angular codebase,
and it's what every example in this repo uses (`expense-app` has zero NgModules). But
interviewers — especially at companies with an older codebase — will ask you to
recognize and reason about NgModule-based code too, so know both shapes.

## Bootstrapping an application

**Bootstrapping** is the process of mounting the root component into the real DOM and
wiring up application-wide configuration (DI providers, router config, HTTP client,
etc.) before anything renders.

```ts
// src/main.ts — the actual entry point of expense-app
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
```

```ts
// src/app/app.config.ts — app-wide providers, the standalone replacement for a
// root NgModule's `providers` array.
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding() binds route params/data to component inputs.
    provideRouter(routes, withComponentInputBinding()),
  ],
};
```

- **Key Concepts**
  - **`bootstrapApplication(RootComponent, appConfig)`**: mounts `RootComponent`
    (here, `App`) onto the element matching its selector found in `index.html`
    (`<app-root>`), using `appConfig` to configure the application-wide injector.
  - **`ApplicationConfig.providers`**: the standalone equivalent of a root NgModule's
    `providers` — this is where you register everything the whole app needs: router
    (`provideRouter`), HTTP client (`provideHttpClient()`), animations, global error
    listeners, etc. Each `provideX()` function is a small builder that returns the
    provider(s) that feature needs.
  - **Legacy bootstrapping**: `platformBrowserDynamic().bootstrapModule(AppModule)`
    bootstraps a root `NgModule` instead of a standalone component — the pattern you'll
    see in a pre-standalone app.
  - **`.catch(err => ...)`**: bootstrapping is asynchronous (Angular compiles/loads
    lazily-referenced pieces), so `bootstrapApplication` returns a Promise; always
    handle rejection so a startup failure isn't silently swallowed.

**Why it's useful**: this is the one piece of "magic" every Angular app needs once —
after `main.ts` runs, everything else (routing, DI, rendering) is driven by ordinary
component/service code. Understanding `app.config.ts` also tells you exactly where to
look when something app-wide (routing config, HTTP interceptors, global providers)
needs to change.

## Component lifecycle hooks

Angular calls specific methods on a component class at defined points in its life —
creation, change-detection passes, and destruction — if the class implements the
matching interface.

```ts
import { Component, OnInit, OnChanges, OnDestroy, SimpleChanges, input } from '@angular/core';

@Component({ selector: 'app-widget', template: `...` })
export class Widget implements OnInit, OnChanges, OnDestroy {
  readonly userId = input.required<number>();

  ngOnChanges(changes: SimpleChanges): void {
    // Runs before ngOnInit, and again every time an @Input()/input() changes.
    console.log('userId changed to', changes['userId']?.currentValue);
  }

  ngOnInit(): void {
    // Runs once, after the first ngOnChanges — the idiomatic place to fetch initial data.
    console.log('Widget initialized');
  }

  ngOnDestroy(): void {
    // Runs once, right before Angular removes the component — clean up subscriptions,
    // timers, and observers here to avoid leaks.
    console.log('Widget destroyed');
  }
}
```

- **Key Concepts**
  - **`ngOnChanges(changes)`**: fires before `ngOnInit` and on every subsequent change
    to a bound input. `changes` is a map of input name → `{ previousValue,
    currentValue, firstChange }`. Only implement this if you need to react to specific
    *which-input-changed* logic; for simple derived state prefer a `computed()` signal
    (Phase 4) that recalculates automatically.
  - **`ngOnInit()`**: runs once, after the first `ngOnChanges`. The conventional place
    for initial setup (fetching data, subscribing) rather than the constructor, because
    inputs are guaranteed to be set by the time `ngOnInit` runs (they are not
    guaranteed to be set yet inside the constructor).
  - **`ngOnDestroy()`**: runs once, just before Angular destroys the component (route
    navigated away, `*ngIf`/`@if` toggled off, parent removed). Use it to unsubscribe
    from manually-managed RxJS subscriptions, clear `setInterval`/`setTimeout`, and
    detach observers — otherwise you leak memory and keep dead work running (see System
    Design Phase 7 — resource leaks, and Phase 4 of this track for `takeUntilDestroyed`).
  - **`ngAfterViewInit()` / `ngAfterContentInit()`**: fire after the component's own
    view (and any projected content, respectively) has been fully initialized — the
    right place to read a `@ViewChild`/`@ContentChild` reference, since it isn't
    populated yet in `ngOnInit`.
  - **Constructor vs `ngOnInit`**: the constructor is plain TypeScript class
    construction — useful for DI (`inject()` or constructor params) and default field
    values, but inputs aren't guaranteed to be bound yet. Do data-dependent work in
    `ngOnInit`, not the constructor.
  - **Order on creation**: constructor → `ngOnChanges` → `ngOnInit` →
    `ngAfterContentInit` → `ngAfterViewInit`. Angular calls each subsequent
    `ngOnChanges` before every change-detection pass that changed an input.

**Why it's useful**: lifecycle hooks are where you hook into a component's real-world
timeline — load data once (`ngOnInit`), react to prop changes (`ngOnChanges`), and
prevent leaks (`ngOnDestroy`). They're a near-guaranteed interview topic because
they're the most common source of subtle bugs (fetching before an input is set,
forgetting to unsubscribe).

## Inputs and outputs (component communication)

Components communicate down the tree via **inputs** (parent → child, like function
parameters) and up the tree via **outputs** (child → parent, like a callback).

```ts
// Modern (Angular 17.1+): signal-based input()/output() functions.
import { Component, input, output, computed, inject } from '@angular/core';
import { ExpenseService } from '../expense.service';

@Component({ selector: 'app-expense-detail', /* ... */ })
export class ExpenseDetail {
  private readonly service = inject(ExpenseService);

  // input.required(): this component cannot be used without providing `id`.
  // `transform` converts the incoming value (route params arrive as strings).
  readonly id = input.required({ transform: (v: string) => Number(v) });

  // A derived signal built from the input — recomputes only when `id()` changes.
  readonly expense = computed(() => this.service.getById(this.id()));
}
```

This is the actual `ExpenseDetail` component (`expense-app/src/app/expense-detail/expense-detail.ts`):
its `id` input is populated automatically from the `:id` route parameter because
`app.config.ts` enables `withComponentInputBinding()` (Phase 5 covers this wiring).

```ts
// Older, still-common decorator-based API — equivalent behavior, class-field style.
import { Component, Input, Output, EventEmitter } from '@angular/core';

@Component({ selector: 'app-counter', template: `...` })
export class Counter {
  @Input({ required: true }) startAt!: number;   // parent -> child
  @Output() changed = new EventEmitter<number>(); // child -> parent

  increment(): void {
    this.changed.emit(this.startAt + 1);          // emit()s an event the parent can bind to
  }
}
```

| | `@Input()`/`@Output()` (decorator) | `input()`/`output()` (signal-based, v17.1+) |
|---|---|---|
| Read pattern | Plain class field (`this.startAt`) | Function call (`this.startAt()`) — a real signal |
| Reactivity | Not itself reactive; needs `ngOnChanges` to react to changes | Automatically reactive — read it inside a `computed()`/`effect()`/template and it tracks |
| Required inputs | `@Input({ required: true })` (v16+) | `input.required<T>()` — enforced at compile time |
| Transform incoming value | Manual, inside a setter | Built-in `transform` option |
| Emitting | `EventEmitter` + `.emit()` | `output<T>()` + `.emit()` (same call shape, simpler type) |

- **Key Concepts**
  - **Parent binds an input** with property binding: `<app-expense-detail [id]="5" />`
    (Phase 2 covers `[prop]` binding syntax in depth).
  - **Two-way**: pairing an input named `x` with an output named `xChange` lets the
    parent use the banana-in-a-box shorthand `[(x)]="field"` (Phase 2). Angular also
    has `model()` — a signal that's both readable and writable and generates the
    matching input/output pair automatically, avoiding the need to hand-write the
    `xChange` output.
  - **`input.required()` vs `input()`**: `input()` optionally takes a default value
    (`input(0)`); `input.required()` has no default and Angular's compiler flags any
    usage that forgets to bind it.

**Why it's useful**: inputs/outputs are how the component tree stays composable — a
parent doesn't reach into a child's internals, it just passes data down and listens for
events up, the same discipline as passing props/callbacks in React. Signal-based
`input()` additionally makes the value first-class reactive state you can feed straight
into a `computed()`, removing the need for `ngOnChanges` boilerplate in most cases.

## Content projection

**Content projection** (`<ng-content>`) lets a parent pass *markup*, not just data,
into a child component's template — analogous to React's `children` prop.

```ts
// card.ts
@Component({
  selector: 'app-card',
  template: `
    <div class="card">
      <h3><ng-content select="[title]" /></h3>   <!-- named slot -->
      <div class="body"><ng-content /></div>       <!-- default slot -->
    </div>
  `,
})
export class Card {}
```

```html
<!-- usage -->
<app-card>
  <span title>Monthly Summary</span>
  <p>You spent $1,245 this month.</p>
</app-card>
<!--
Rendered:
<div class="card">
  <h3><span>Monthly Summary</span></h3>
  <div class="body"><p>You spent $1,245 this month.</p></div>
</div>
-->
```

- **Key Concepts**
  - **Default slot**: a bare `<ng-content />` projects any content that didn't match a
    more specific `select`.
  - **Named/selected slots**: `<ng-content select="[title]" />` (or `select="footer"`,
    `select=".class"`) projects only the matching elements into that spot — lets a
    component define multiple insertion points.
  - **One-way**: projected content is compiled in the *parent's* context (it can bind
    to the parent's own fields), not the child's — a common gotcha for newcomers.

**Why it's useful**: content projection is how you build generic, reusable wrapper
components (cards, modals, layout shells, form field wrappers) whose *content* varies
per usage but whose *chrome* (styling, structure) stays consistent — without the child
component needing to know what's inside it.

## Summary — Key takeaways

- Angular is a full framework: routing, forms, HTTP, DI, and a compiler ship together
  and move together; the CLI (`ng`) is the standard tool for all of it.
- A component = `@Component` decorator (selector, template, styles) + a class whose
  fields are template state and whose methods are event handlers.
- View encapsulation scopes component CSS by default (`Emulated`); know `ShadowDom` and
  `None` exist and when you'd pick them.
- Standalone components (default since v19) declare their own `imports`; NgModules are
  the legacy grouping mechanism you'll still meet in older codebases.
- `main.ts` calls `bootstrapApplication(RootComponent, appConfig)`;
  `app.config.ts` → `providers` is where app-wide DI/router/HTTP config lives.
- Lifecycle hooks (`ngOnChanges` → `ngOnInit` → … → `ngOnDestroy`) are where you hook
  into a component's timeline — fetch in `ngOnInit`, clean up in `ngOnDestroy`.
- Inputs/outputs (signal-based `input()`/`output()`, or classic `@Input()`/`@Output()`)
  are how parent and child components talk; content projection (`<ng-content>`) is how
  a parent injects markup into a child.
