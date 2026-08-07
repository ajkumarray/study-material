<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · reactivity](../phase-4-reactivity/NOTES.md) | [Phase 6 · version migration ➡](../phase-6-version-migration/NOTES.md)
<!-- /nav -->

# Phase 5 — Routing & Forms: Notes

Two first-party subsystems every Angular app uses: the **Router** (URL ↔ components) and
**Forms** (typed, validated user input). Both are provided by the framework, not chosen
from an ecosystem.

## 5.1 — The Router

- **Route config** is an array of `{ path, component }` (the demo's `app.routes.ts`),
  wired with **`provideRouter(routes)`** in `app.config`. The active route's component
  renders in **`<router-outlet>`**.
- **Navigation:** `routerLink="/expenses"` (or `[routerLink]="['/expenses', id]"`) for
  declarative links; `Router.navigate([...])` for programmatic. `routerLinkActive` marks
  the active link.
- **Route params:** `:id` in the path. Read them via the `ActivatedRoute` (its
  `paramMap`/`snapshot`) **or**, with **`withComponentInputBinding()`**, bind them
  straight to a component `input()` — the demo does the latter (`id = input.required()`),
  which is the cleanest modern approach.
- **Lazy loading:** `loadComponent: () => import('./x').then(m => m.X)` code-splits a
  route so it's fetched only when visited (the demo lazy-loads the detail route — you saw
  the separate `expense-detail` chunk in the build). Cuts initial bundle size.

## 5.2 — Guards & resolvers

- **Guards** are functions that allow/deny navigation:
  - `canActivate` — can the user enter this route? (auth checks → redirect to login).
  - `canDeactivate` — can the user leave? (warn about unsaved form changes).
  - `canMatch` — should this route even be considered (feature flags, role-based route
    swaps).
- **Resolvers** (`resolve`) pre-fetch data **before** the route activates, so the
  component renders with data ready (an alternative to fetching inside the component).
- Modern guards/resolvers are plain functions using `inject()` (no class boilerplate).

## 5.3 — Forms: Reactive vs Template-driven

Angular has two form systems:

- **Reactive Forms** (the demo, recommended for non-trivial forms): the form model is
  defined in **TypeScript** (`FormBuilder`/`FormGroup`/`FormControl`), the template binds
  to it via `[formGroup]`/`formControlName`. Typed (`nonNullable.group`), explicit,
  testable without the DOM, and validation is declared in code.
- **Template-driven Forms**: the form is built in the **template** with `ngModel` +
  directives; Angular infers the model. Simpler for small forms, but less scalable and
  harder to test.
- **Validation:** built-in validators (`Validators.required`, `min`, `email`, …) and
  custom validator functions. `form.invalid`, control `.errors`, and states like
  `touched`/`dirty` drive error UI and disable submit (the demo disables Save on invalid).

## Perspective

The Router and Forms are where Angular's "batteries included" pays off: URL routing with
lazy loading and guards, and a typed, validated forms model — all first-party and
consistent. For routing, prefer lazy `loadComponent` + component input binding; for
anything beyond a trivial form, prefer **Reactive Forms** for the typed, testable,
code-first model. Together with DI (Phase 3) and reactivity (Phase 4), these complete the
picture of a full Angular app — exactly what the `expense-app` capstone assembles.
