<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · components](../phase-1-components/NOTES.md) | [Phase 3 · dependency injection ➡](../phase-3-dependency-injection/NOTES.md)
<!-- /nav -->

# Phase 2 — Templates & Data Binding: Notes

Angular templates are HTML enriched with binding syntax. Data flows **class → template**
(rendering) and **template → class** (events); the framework keeps them in sync via
change detection.

## 2.1 — The four bindings

| Syntax | Direction | Meaning |
|---|---|---|
| `{{ expr }}` | class → view | **Interpolation** — render a value as text |
| `[prop]="expr"` | class → view | **Property binding** — set a DOM/component property (`[disabled]`, `[routerLink]`) |
| `(event)="handler()"` | view → class | **Event binding** — run a method on a DOM/output event (`(click)`, `(ngSubmit)`) |
| `[(ngModel)]="field"` | both | **Two-way binding** — the "banana in a box"; sugar for `[value]` + `(input)` |

- Property binding `[x]` sets a *property* (not an attribute) — use it for dynamic
  values; string attributes can stay plain HTML.
- Two-way binding works on any component exposing a matched `@Input`/`@Output` pair
  (`[(x)]` = `[x]` + `(xChange)`); `ngModel` provides it for form inputs.
- **Signals in templates:** you call a signal to read it — `{{ total() }}`,
  `@if (expenses().length)`. Reading a signal in a template subscribes that view to it.

## 2.2 — Built-in control flow (`@if` / `@for` / `@switch`)

Modern Angular (v17+) replaced the `*ngIf`/`*ngFor` structural directives with **block
control flow** built into the template compiler:

```html
@if (expense(); as e) { <h2>{{ e.description }}</h2> } @else { <p>Not found</p> }

@for (e of expenses(); track e.id) {
  <li>{{ e.description }}</li>
} @empty { <li>No expenses</li> }

@switch (status()) {
  @case ('loading') { <spinner/> }
  @default { <content/> }
}
```

- **`track` is required in `@for`** — it's the identity key (like React's `key`) that
  lets Angular reuse DOM nodes instead of re-creating them; use a stable id, not the
  index. This is the single biggest `@for` performance lever.
- `@if (x; as y)` binds the truthy value to a local `y` — handy for
  "fetch-then-render" without repeating the expression.
- Block control flow is faster (compiler-optimized), needs no imports (unlike `NgIf`/
  `NgFor`), and reads more like ordinary code.

## 2.3 — Pipes

**Pipes** transform a value for display in the template with `|`:

- Built-in: `currency`, `date`, `number`, `percent`, `json`, `uppercase`, `slice`, and
  the crucial **`async`** pipe (subscribes to an Observable/Promise and renders its
  latest value, auto-unsubscribing — Phase 4).
- Standalone components must **import** the pipe they use (`CurrencyPipe`, `DatePipe`) —
  the demo imports `CurrencyPipe`.
- **Custom pipe:** a class with `@Pipe({ name: 'myPipe' })` implementing
  `transform(value, ...args)`. Prefer **pure** pipes (recompute only when inputs
  change) for performance.
- Pipes are for *presentation* transforms only — keep business logic in the class/
  service, not the template.

## Change detection (the engine underneath)

Angular re-renders when data changes. Classic change detection re-checks the component
tree on events/async (via Zone.js). **Signals** (Phase 4) enable *fine-grained*
reactivity — only the views that read a changed signal update — and are moving Angular
toward **zoneless** change detection. For interviews: know that bindings stay in sync
because of change detection, and that signals make it targeted rather than tree-wide.

## Perspective

Templates are declarative: describe what the UI *is* for the current state, and let
bindings + change detection keep the DOM matching. Learn the four bindings, the new
`@`-control-flow (with `track`), and pipes for display, and keep logic in the class —
the template stays a thin, readable projection of state.
