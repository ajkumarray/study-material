<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 10 — Angular

A **batteries-included, opinionated framework** for building SPAs — the enterprise
counterpoint to React. Where React is a library you assemble, Angular ships routing,
forms, HTTP, DI, and a compiler in one CLI-driven package, all in TypeScript.

Taught for someone who knows **TypeScript (07)**, **React (08)**, and a **Spring Boot
backend (02)** — because Angular will feel *more* like Spring than like React:
class-based components, decorators (`@Component`, `@Injectable`), constructor/`inject`
dependency injection, and a strong opinion about structure. The contrasts to React
(and the parallels to Spring DI) are the teaching hook throughout.

## The runnable app: `expense-app/`

A **real Angular app that builds** (`ng build` verified), scaffolded with the Angular
CLI and written in the **modern standalone + signals** style. Same expense theme as the
Next.js and React apps.

| File | Concept |
|---|---|
| `src/app/expense.model.ts` | typed model |
| `src/app/expense.service.ts` | **`@Injectable` service**: signals for state, `computed` derived state, RxJS `Observable` for async |
| `src/app/expense-list/` | **standalone component**, `inject()`, new `@for`/`@if` control flow, `currency` pipe |
| `src/app/expense-detail/` | **lazy-loaded** route, route param → `input()` binding |
| `src/app/add-expense/` | **Reactive Forms** with validators |
| `src/app/app.routes.ts` | router config with a lazy `loadComponent` |
| `src/app/app.config.ts` | app providers (`provideRouter`, `withComponentInputBinding`) |

```bash
cd 10-angular/expense-app
npm install
npm run build     # ng build — what we verified
npm start         # ng serve → http://localhost:4200
```

## Curriculum

### Phase 1 — Angular & standalone components ✅
- [x] 1.1 Framework vs library; the CLI; TypeScript-first; how it compares to React/Spring
- [x] 1.2 Component anatomy: `@Component`, selector, template, styles
- [x] 1.3 Standalone components & `imports` (vs the legacy `NgModule` world)
- [x] 1.4 Bootstrapping: `bootstrapApplication`, `app.config` providers
- NOTES · INTERVIEW

### Phase 2 — Templates & data binding ✅
- [x] 2.1 Interpolation `{{ }}`, property `[x]`, event `(x)`, two-way `[(ngModel)]`
- [x] 2.2 New control flow: `@if`, `@for` (with `track`), `@switch`
- [x] 2.3 Pipes (`currency`, `date`, `async`) and writing a custom pipe
- NOTES · INTERVIEW

### Phase 3 — Dependency Injection & services ✅
- [x] 3.1 `@Injectable({ providedIn: 'root' })`; the DI container (vs Spring beans)
- [x] 3.2 `inject()` vs constructor injection; injection tokens
- [x] 3.3 Hierarchical injectors & provider scopes; singletons
- NOTES · INTERVIEW

### Phase 4 — Reactivity: Signals & RxJS ✅
- [x] 4.1 Signals: `signal`, `computed`, `effect`; fine-grained reactivity
- [x] 4.2 RxJS: Observables, operators, the `async` pipe; `HttpClient`
- [x] 4.3 Signals vs RxJS — when to use which; `toSignal`/`toObservable`
- NOTES · INTERVIEW

### Phase 5 — Routing & forms ✅
- [x] 5.1 Router: routes, `routerLink`, `<router-outlet>`, params, lazy `loadComponent`
- [x] 5.2 Route guards & resolvers; component input binding
- [x] 5.3 Reactive Forms vs Template-driven; `FormBuilder`, validators
- NOTES · INTERVIEW

### Phase 6 — Version migration (Angular 10 → latest LTS) ✅
- [x] 6.1 The release model: SemVer, ~2 majors/year, 18-month support, coupled peers
- [x] 6.2 `ng update` + migration schematics; `angular.dev/update-guide`; one-major-at-a-time
- [x] 6.3 What changed v10→v20 (Ivy, standalone, typed forms, signals, `@if`, esbuild, zoneless)
- [x] 6.4 The multi-major upgrade playbook; modernization schematics (`ng generate`)
- [x] 6.5 The breakages that bite: Ivy/ngcc, Material MDC, typed forms, RxJS 6→7, builder flip
- NOTES · INTERVIEW

### Capstone ✅
- [x] The `expense-app` — signal service, standalone list/detail/form components,
  reactive form, lazy-loaded detail route. `ng build` passes.
- See `CAPSTONE.md`.

## How this connects

- **↔ React (08):** the constant contrast — components/props/state exist in both, but
  Angular is class + decorator + DI + framework-provided everything; React is
  function + hooks + library + you-choose-the-rest.
- **↔ Spring Boot (02):** decorators ≈ annotations, `@Injectable` ≈ `@Service`, the DI
  container is the same idea (Software Design Phase 6, IoC/DI). Angular is "Spring for
  the frontend" in spirit.
- **← TypeScript (07):** Angular is TS-first — decorators, generics, and typed forms
  are everywhere; the type system is not optional.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · components** — [Notes](phase-1-components/NOTES.md) · [Interview](phase-1-components/INTERVIEW.md)
- **Phase 2 · templates binding** — [Notes](phase-2-templates-binding/NOTES.md) · [Interview](phase-2-templates-binding/INTERVIEW.md)
- **Phase 3 · dependency injection** — [Notes](phase-3-dependency-injection/NOTES.md) · [Interview](phase-3-dependency-injection/INTERVIEW.md)
- **Phase 4 · reactivity** — [Notes](phase-4-reactivity/NOTES.md) · [Interview](phase-4-reactivity/INTERVIEW.md)
- **Phase 5 · routing forms** — [Notes](phase-5-routing-forms/NOTES.md) · [Interview](phase-5-routing-forms/INTERVIEW.md)
- **Phase 6 · version migration** — [Notes](phase-6-version-migration/NOTES.md) · [Interview](phase-6-version-migration/INTERVIEW.md)
<!-- /phases-nav -->
