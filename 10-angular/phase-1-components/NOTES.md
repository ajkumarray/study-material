<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · templates binding ➡](../phase-2-templates-binding/NOTES.md)
<!-- /nav -->

# Phase 1 — Angular & Standalone Components: Notes

Angular is a **complete, opinionated framework** (not a library): it provides routing,
forms, HTTP, DI, testing, and a build/compiler out of the box, all TypeScript-first and
CLI-driven. Coming from React you'll notice it decides a lot *for* you; coming from
Spring, the DI + decorator model will feel familiar.

## 1.1 — Framework vs library; the mental map

- **Framework, not library.** React gives you components and leaves routing/data/build
  to the ecosystem; Angular ships them all as first-party, versioned-together packages.
  Fewer choices, more consistency across teams — the enterprise trade-off.
- **The CLI (`ng`)** scaffolds, serves, builds, tests, and generates code
  (`ng generate component`, `ng build`, `ng serve`). It's central to the workflow.
- **Two mental bridges:**
  - *vs React:* class components with **decorators**, framework-managed change
    detection, DI instead of prop-drilling/context, RxJS + Signals instead of hooks.
  - *vs Spring:* `@Component`/`@Injectable` ≈ Spring annotations, the **DI container**
    is the same IoC idea (Software Design Phase 6), services are singletons like
    `@Service` beans.

## 1.2 — Component anatomy

A component is a class decorated with **`@Component`**:

```ts
@Component({
  selector: 'app-expense-list',   // the custom element tag: <app-expense-list>
  imports: [RouterLink, CurrencyPipe],  // template dependencies (standalone)
  templateUrl: './expense-list.html',   // template (or inline `template:`)
  styleUrl: './expense-list.css',       // scoped styles (encapsulated by default)
})
export class ExpenseList { /* fields = template state, methods = handlers */ }
```

- The **template** is HTML + Angular syntax (Phase 2). Class **fields** are the state
  the template binds to; **methods** are event handlers.
- **View encapsulation:** component styles are scoped to that component by default
  (emulated shadow DOM) — no global CSS bleed.

## 1.3 — Standalone components (the modern default)

- Historically Angular grouped components/pipes/directives into **`NgModule`s**
  (`@NgModule` with `declarations`/`imports`). Modern Angular (v17+) makes components
  **standalone**: each component declares its own template dependencies in its own
  **`imports`** array — no NgModule needed.
- This is simpler (less boilerplate, clearer dependencies, better tree-shaking/lazy
  loading) and is now the default the CLI generates. Know NgModules exist (legacy
  codebases) but write standalone.

## 1.4 — Bootstrapping

- An app starts with **`bootstrapApplication(App, appConfig)`** in `main.ts` — it
  mounts the root standalone component.
- **`app.config.ts`** holds **application-wide providers** via the `providers` array
  (`provideRouter(routes)`, `provideHttpClient()`, etc.) — the standalone replacement
  for a root NgModule's `providers`. This is where you configure DI, routing, HTTP,
  and more for the whole app.

## Perspective

Angular's identity: **structure and completeness**. The unit is a decorated class
(component or service), assembled by a DI container, compiled ahead-of-time. If React
optimizes for flexibility and a small core, Angular optimizes for a consistent,
full-featured platform — which is why large teams and enterprises favor it, and why
your Spring background transfers so directly (decorators, DI, opinionated layering).
