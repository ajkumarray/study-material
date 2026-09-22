<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · reactivity](../phase-4-reactivity/NOTES.md) | [Phase 6 · version migration ➡](../phase-6-version-migration/NOTES.md)
<!-- /nav -->

# Phase 5 — Routing & Forms: Notes

Two first-party subsystems every non-trivial Angular app uses: the **Router** (mapping
a URL to the component that should render) and **Forms** (typed, validated user
input). Both are shipped and maintained by the Angular team itself, not chosen from a
third-party ecosystem — the same "batteries included" philosophy from Phase 1.

## Route configuration

A **route config** is an array of route definitions — path patterns paired with the
component to render — registered app-wide via `provideRouter`.

```ts
// expense-app/src/app/app.routes.ts — the real routing config for this app.
export const routes: Routes = [
  { path: '', redirectTo: 'expenses', pathMatch: 'full' },
  { path: 'expenses', component: ExpenseList, title: 'All expenses' },
  {
    path: 'expenses/:id',
    loadComponent: () => import('./expense-detail/expense-detail').then((m) => m.ExpenseDetail),
    title: 'Expense detail',
  },
];
```

```ts
// expense-app/src/app/app.config.ts
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
  ],
};
```

```html
<!-- expense-app/src/app/app.html -->
<nav><a routerLink="/expenses">All expenses</a></nav>
<router-outlet />
```

- **Key Concepts**
  - **`path`**: the URL segment to match, relative to its parent route. `:id` marks a
    dynamic **route parameter** — `expenses/:id` matches `expenses/1`, `expenses/42`,
    etc.
  - **`component`**: the standalone component rendered when this route is active
    (`ExpenseList` for `/expenses`).
  - **`redirectTo` + `pathMatch: 'full'`**: redirects the empty path (`''`) to
    `expenses`. `pathMatch: 'full'` means the *entire* remaining URL must be empty for
    the redirect to fire — without it, `'' `would match as a *prefix* of every URL and
    redirect everything.
  - **`title`**: sets `document.title` when the route activates — no manual
    `Title` service call needed for the simple case.
  - **`provideRouter(routes, ...features)`**: registers the route config with the
    application-wide injector in `app.config.ts`'s `providers` array — the standalone
    replacement for `RouterModule.forRoot(routes)` in a legacy NgModule app.
  - **`<router-outlet />`**: the placeholder in a template where the currently-active
    route's component renders. Everything outside it (the header/nav in `app.html`) is
    the persistent shell around every route.

**Why it's useful**: this is the whole shape of client-side routing in Angular — a
declarative table of URL patterns to components, with the outlet as the single
insertion point. Once you can read a `Routes` array, you can navigate any Angular
app's structure.

## Navigation: declarative and programmatic

```html
<!-- Declarative: routerLink -->
<a routerLink="/expenses">All expenses</a>
<a [routerLink]="['/expenses', e.id]">{{ e.description }}</a>
<a routerLink="/expenses" routerLinkActive="active-link">Expenses</a>
```

```ts
// Programmatic: Router.navigate() — e.g. after a form submits successfully.
import { Router } from '@angular/router';

export class AddExpense {
  private readonly router = inject(Router);

  submit(): void {
    // ... save the expense ...
    this.router.navigate(['/expenses']);
  }
}
```

- **Key Concepts**
  - **`routerLink`**: a directive that turns an `<a>` (or any element) into a
    navigation trigger without a full page reload — a string (`routerLink="/expenses"`)
    or an array for path segments plus dynamic parameters
    (`[routerLink]="['/expenses', e.id]"`).
  - **`routerLinkActive`**: adds the given CSS class to the link when its route is the
    currently active one — the standard way to highlight the current nav item.
  - **`Router.navigate(commands)`**: imperative navigation from code — used after an
    action completes (form submit, logout) rather than a direct user click on a link.
    `Router.navigateByUrl('/expenses/5')` is the string-URL equivalent.
  - Both approaches update the browser's URL and history (so back/forward work) without
    a full page reload — the router intercepts navigation and swaps what's inside
    `<router-outlet>`.

**Why it's useful**: `routerLink` covers the vast majority of navigation (anything the
user clicks); `Router.navigate()` covers navigation triggered by application logic
(redirect after save, redirect on auth failure) where there's no literal link element to
attach `routerLink` to.

## Route parameters: `ActivatedRoute` vs component input binding

```ts
// Classic: read the parameter via ActivatedRoute.
export class ExpenseDetail {
  private readonly route = inject(ActivatedRoute);
  id = 0;

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.id = Number(params.get('id'));
    });
  }
}
```

```ts
// Modern: withComponentInputBinding() (enabled in app.config.ts) binds the route
// param straight to a component input — the actual code in this repo.
export class ExpenseDetail {
  private readonly service = inject(ExpenseService);
  readonly id = input.required({ transform: (v: string) => Number(v) });
  readonly expense = computed(() => this.service.getById(this.id()));
}
```

- **Key Concepts**
  - **`ActivatedRoute`**: an injectable representing the currently-activated route.
    `.paramMap` is an Observable of the route's parameters (use it when the parameter
    can change *without* the component being recreated, e.g. navigating from
    `/expenses/1` to `/expenses/2` while staying on the same route); `.snapshot.paramMap`
    is a one-time read (fine when the component is always freshly created per
    navigation).
  - **`withComponentInputBinding()`**: a `provideRouter` feature that automatically
    binds a route's parameters (and route `data`/`resolve` results) to a component
    `input()`/`@Input()` of the matching name — no `ActivatedRoute` boilerplate at all.
    This is the cleanest modern approach and what `expense-app` uses.
  - **Type coercion**: route parameters always arrive as strings (URLs are text) —
    `ExpenseDetail`'s `id` input uses `transform: (v: string) => Number(v)` to convert
    it, since `Expense.id` is a `number`.

**Why it's useful**: `withComponentInputBinding()` removes an entire category of
boilerplate (inject `ActivatedRoute`, subscribe, unwrap, convert type, remember to
unsubscribe) by turning route data into an ordinary, type-safe component input — one
less place manual subscription management could leak.

## Lazy loading

**Lazy loading** defers fetching a route's component (and its dependencies) until the
user actually navigates to it, splitting the app into smaller downloadable chunks.

```ts
{
  // Lazy loading: the detail component (and everything it alone depends on) is
  // code-split into its own chunk and fetched only when a user visits /expenses/:id.
  path: 'expenses/:id',
  loadComponent: () => import('./expense-detail/expense-detail').then((m) => m.ExpenseDetail),
  title: 'Expense detail',
},
```

- **Key Concepts**
  - **`loadComponent`**: dynamically `import()`s a single standalone component for a
    route — the fine-grained, component-level lazy loading standalone components made
    possible (legacy NgModule apps could only lazy-load at the *module* level via
    `loadChildren`).
  - **`loadChildren`**: the older/broader form — lazy-loads a whole set of child
    routes (typically an entire feature area) as one chunk, still commonly used for
    grouping a large feature rather than a single component.
  - **Effect on the build**: the CLI's bundler emits the lazily-loaded component as a
    separate JS chunk, fetched over the network only on first navigation to that route
    — smaller initial bundle, faster first paint, at the cost of a small delay the
    first time that route is visited.

**Why it's useful**: lazy loading is the standard lever for keeping an Angular app's
initial load fast as it grows — most of an app's code (admin panels, rarely-visited
settings pages, this expense-detail view) doesn't need to be downloaded until it's
actually needed.

## Guards

**Guards** are functions that run before a navigation completes and decide whether it's
allowed to proceed.

```ts
// Modern: a plain function using inject() — no class boilerplate.
import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? true : router.createUrlTree(['/login']);
};

// Wired into the route config:
{ path: 'admin', component: AdminPanel, canActivate: [authGuard] },
```

- **Key Concepts**
  - **`canActivate`**: can the user *enter* this route? The classic use is an auth
    check — return `true` to allow, `false` to block, or a `UrlTree` (via
    `router.createUrlTree([...])`) to redirect elsewhere instead of just blocking.
  - **`canDeactivate`**: can the user *leave* the current route? Used to warn about
    unsaved changes in a form before navigating away (return `false`, or prompt the user
    and return their answer).
  - **`canMatch`**: should this route even be *considered* a match at all — evaluated
    before `canActivate`, useful for swapping which route/component handles a given
    path based on a feature flag or role, rather than matching then blocking.
  - **Functional guards**: modern guards are plain functions (`CanActivateFn`, etc.)
    that call `inject()` for dependencies — no class, no `@Injectable`, no constructor.
    This mirrors the general shift toward `inject()`-based functions across the router
    APIs (guards, resolvers, and interceptors).
  - **Return types**: `boolean`, `UrlTree` (redirect), or an `Observable`/`Promise` of
    either — for guards that need to check something async (e.g. verify a token with
    the server) before deciding.

**Why it's useful**: guards centralize navigation-control logic (auth, unsaved-changes
warnings, feature flags) in one composable, reusable place instead of scattering
`if (!loggedIn) return` checks across every component that needs protecting.

## Resolvers

A **resolver** pre-fetches data *before* a route activates, so the destination
component renders with the data already available instead of rendering first and
fetching after.

```ts
import { ResolveFn } from '@angular/router';
import { inject } from '@angular/core';
import { ExpenseService } from './expense.service';
import { Expense } from './expense.model';

export const expenseResolver: ResolveFn<Expense | undefined> = (route) => {
  const service = inject(ExpenseService);
  return service.getById(Number(route.paramMap.get('id')));
};

// Wired into the route config, and read via ActivatedRoute.data or an input()
// (with withComponentInputBinding, a `resolve` key becomes an input the same way a
// route param does):
{ path: 'expenses/:id', component: ExpenseDetail, resolve: { expense: expenseResolver } },
```

- **Key Concepts**
  - **Trade-off vs fetching in the component**: a resolver delays the navigation itself
    until the data is ready (no flash of a loading/empty state inside the component),
    at the cost of a slightly delayed page transition; fetching inside the component
    (e.g. in `ngOnInit`, or via a signal-backed service as `expense-app` does) navigates
    immediately and shows a loading state while data arrives.
  - Modern resolvers, like guards, are plain functions using `inject()` — no class
    boilerplate.

**Why it's useful**: resolvers are the right call when a component genuinely can't
render anything meaningful without its data (vs. a component that can show a sensible
loading skeleton) — know the trade-off, and be ready to argue for the alternative
(fetch-in-component) which `expense-app`'s synchronous signal-backed service sidesteps
entirely by not needing async fetching in this demo.

## Reactive Forms

**Reactive Forms** define the form's data model in TypeScript — `FormGroup`/
`FormControl`, built via `FormBuilder` — and bind the template to that model, rather
than letting the template define the model implicitly.

```ts
// expense-app/src/app/add-expense/add-expense.ts — the real form in this repo.
export class AddExpense {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ExpenseService);

  readonly form = this.fb.nonNullable.group({
    description: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    category: ['food' as Expense['category'], Validators.required],
  });

  submit(): void {
    if (this.form.invalid) return;
    const { description, amount, category } = this.form.getRawValue();
    this.service.add({ description, amountCents: Math.round(amount * 100), category });
    this.form.reset({ description: '', amount: 0, category: 'food' });
  }
}
```

```html
<!-- add-expense.html -->
<form [formGroup]="form" (ngSubmit)="submit()">
  <input formControlName="description" placeholder="Description" />
  <input formControlName="amount" type="number" step="0.01" placeholder="Amount" />
  <select formControlName="category"> <!-- ...options... --> </select>
  <button type="submit" [disabled]="form.invalid">Save</button>
</form>
```

- **Key Concepts**
  - **`FormBuilder.group({...})`**: builds a `FormGroup` — each key becomes a
    `FormControl` with `[initialValue, validators]`. The template binds the group with
    `[formGroup]="form"` and each control with `formControlName="x"` — the module-level
    directive `ReactiveFormsModule` must be imported for these directives to be
    available.
  - **`.nonNullable.group(...)`**: (typed Reactive Forms, standard since v14) makes
    every control's type exclude `null` — resetting a control returns to its *initial*
    value rather than `null`, and `.value`/`.getRawValue()` are properly typed instead
    of `any`. This is what makes `const { description, amount, category } =
    this.form.getRawValue()` type-safe.
  - **`form.invalid`/`form.valid`**: reactive validity state on the whole group (true
    if *any* control is invalid) — bound directly to `[disabled]="form.invalid"` to
    prevent submitting an invalid form.
  - **`form.reset({...})`**: resets every control back to a given set of values (and
    clears `touched`/`dirty` state) — used here after a successful save.
  - **Why "reactive"**: the form model is a plain object graph of `FormControl`s you
    can read, derive from, and subscribe to (`form.valueChanges` is an Observable) —
    fully testable in isolation, without ever rendering a template.

**Why it's useful**: Reactive Forms are the recommended choice for anything beyond a
trivial one-field form because the entire model — values, validity, dirty/touched
state — is explicit TypeScript you can unit test, derive computed values from, and
compose (dynamic controls, cross-field validation) without depending on the DOM being
rendered.

## Template-driven Forms

**Template-driven Forms** build the form model implicitly, driven by `ngModel` and
directives declared directly in the template.

```html
<form #f="ngForm" (ngSubmit)="submit(f.value)">
  <input name="description" [(ngModel)]="model.description" required />
  <button type="submit" [disabled]="f.invalid">Save</button>
</form>
```

- **Key Concepts**
  - **`ngModel` + `name`**: each `[(ngModel)]`-bound input, given a `name`, becomes a
    control inside an automatically-created `FormGroup` that Angular assembles behind
    the scenes from the template structure.
  - **`FormsModule`** (not `ReactiveFormsModule`) must be imported for `ngModel` to be
    available.
  - **`#f="ngForm"`**: a template reference variable (Phase 2) bound to the
    `NgForm` directive instance, giving template access to `f.value`, `f.invalid`, etc.

| | Reactive Forms | Template-driven Forms |
|---|---|---|
| Where the model lives | TypeScript (`FormGroup`/`FormControl`) | Implicit, assembled from the template |
| Type safety | Strong (typed, `.nonNullable`) | Weaker — the model is inferred from markup |
| Testability | Fully unit-testable without rendering | Needs the DOM/template rendered to exercise |
| Validation | Declared in code (`Validators.x`, custom functions) | Declared as template attributes (`required`, custom directives) |
| Dynamic/complex forms | Straightforward (build/modify controls in code) | Awkward past simple cases |
| Best for | Anything non-trivial — the general recommendation | Very small, simple forms |

**Why it's useful**: template-driven forms are faster to write for a two-field form and
familiar if you've used `ngModel` elsewhere, but they don't scale — dynamic fields,
cross-field validation, and unit testing all get harder without a TypeScript-side
model. Know both; default to Reactive Forms for anything beyond the trivial case (the
general industry and Angular-team recommendation).

## Validation

Both form systems support **validators** — functions that inspect a control's value and
return an error object (or `null` if valid).

```ts
// Built-in validators, combined:
amount: [0, [Validators.required, Validators.min(0.01)]],

// A custom validator function:
function noPastDate(control: AbstractControl): ValidationErrors | null {
  return control.value && new Date(control.value) < new Date()
    ? { pastDate: true }
    : null;
}
```

- **Key Concepts**
  - **Built-in validators**: `Validators.required`, `Validators.min`/`max`,
    `Validators.email`, `Validators.pattern`, `Validators.minLength`/`maxLength`, and
    more — attach one or an array to a control.
  - **Custom validators**: a plain function `(control) => ValidationErrors | null` —
    returning an object (e.g. `{ pastDate: true }`) marks the control invalid under
    that key; returning `null` means valid.
  - **Control state, beyond validity**: `touched` (control has been blurred at least
    once), `dirty` (value has been changed from its initial value), `pristine`
    (opposite of dirty), `pending` (an async validator is still running) — used
    together to decide *when* to show an error (e.g. only show "required" after the
    user has touched the field and left it empty, not immediately on page load).
  - **`FormArray`**: a third control type (alongside `FormGroup` and `FormControl`) for
    a *dynamic list* of controls — e.g. an arbitrary number of line items in an
    invoice, added/removed at runtime with `.push()`/`.removeAt()`.

**Why it's useful**: validators are what turn a form from "collects whatever the user
types" into "collects only valid data, with clear feedback" — `expense-app`'s save
button being disabled via `[disabled]="form.invalid"` is the simplest possible
validation-driven UX, and the same `Validators`/custom-validator vocabulary scales up
to much more complex forms.

## Summary — Key takeaways

- A `Routes` array maps URL paths to components; `provideRouter(routes, ...features)`
  registers it; `<router-outlet>` is where the active route renders.
- `routerLink`/`routerLinkActive` for declarative navigation, `Router.navigate()` for
  programmatic navigation after application logic runs.
- `withComponentInputBinding()` binds route params straight to a component's
  `input()`, removing `ActivatedRoute` boilerplate — the modern default; know
  `ActivatedRoute.paramMap`/`.snapshot` as the classic alternative.
- `loadComponent` (single component) / `loadChildren` (feature area) lazy-load routes,
  shrinking the initial bundle.
- Guards (`canActivate`/`canDeactivate`/`canMatch`) control whether navigation is
  allowed; resolvers pre-fetch data before a route activates; both are now typically
  plain `inject()`-based functions, not classes.
- **Reactive Forms** (TypeScript-defined, typed with `.nonNullable`, unit-testable) are
  the default recommendation for anything beyond a trivial form; **template-driven
  forms** (`ngModel`-based) are simpler for small forms but don't scale.
- Validators (`Validators.x`, custom functions) drive `form.valid`/`invalid`; control
  state (`touched`/`dirty`/`pristine`/`pending`) drives *when* to show validation
  feedback; `FormArray` handles dynamic lists of controls.
