<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · components](../phase-1-components/NOTES.md) | [Phase 3 · dependency injection ➡](../phase-3-dependency-injection/NOTES.md)
<!-- /nav -->

# Phase 2 — Templates & Data Binding: Notes

An Angular **template** is HTML enriched with a small, purpose-built binding syntax.
Data flows in two directions: **class → template** (rendering current state) and
**template → class** (forwarding user events). Angular's change-detection engine keeps
the two in sync so the DOM always reflects the latest state.

## Interpolation

**Interpolation** (`{{ expression }}`) renders the result of a TypeScript expression as
text inside the template.

```html
<h1>Expenses (Angular)</h1>
<!-- from expense-detail.html -->
<h2>{{ e.description }}</h2>
<p>Amount: {{ e.amountCents / 100 | currency }}</p>
```

- **Key Concepts**
  - **One-way, class → view**: the expression is re-evaluated whenever change
    detection runs; there's no way to write *into* the class from `{{ }}`.
  - **Expression, not a full statement**: interpolation accepts property
    access/method calls/arithmetic (`{{ e.amountCents / 100 }}`), but not assignments
    or multi-statement code — keep the logic in the class and expose a simple value or
    method.
  - **Signals**: because a signal is read by *calling* it, interpolation of a signal
    looks like `{{ total() }}`, not `{{ total }}` — forgetting the parentheses
    interpolates the signal function itself (which stringifies uselessly), not its
    value.
  - **Auto-escaped**: interpolated values are inserted as text, not parsed as HTML, so
    interpolation is XSS-safe by default (Angular escapes `<`, `>`, `&`, etc.).

**Why it's useful**: interpolation is the simplest, most common binding — anywhere you
need to show a value as text, it's the tool. It's read-only by design, which keeps the
data flow predictable (you always know where a value *came from*: the class).

## Property binding

**Property binding** (`[property]="expression"`) sets a DOM element property or a child
component's `@Input()`/`input()` to the result of an expression.

```html
<button type="submit" [disabled]="form.invalid">Save</button>
<a [routerLink]="['/expenses', e.id]">{{ e.description }}</a>
```

- **Key Concepts**
  - **Sets a *property*, not an attribute.** `[disabled]="form.invalid"` sets the DOM
    `disabled` *property* directly, which is what actually controls whether the button
    is clickable — a plain HTML attribute (`disabled="false"`) would be truthy just by
    being present, regardless of its string value. This distinction (property vs
    attribute) trips up almost everyone coming from plain HTML.
  - **Any DOM property is bindable**: `[value]`, `[src]`, `[hidden]`, `[className]`, or
    a component's own `@Input()`/`input()` (`[id]="expenseId"`).
  - **Static vs dynamic**: if a value never changes, skip the brackets and write a
    plain HTML attribute (`type="submit"`); use `[prop]` only when the value comes from
    the class/state.
  - **Attribute binding** (`[attr.x]="expr"`) is a related but distinct syntax for
    setting an actual HTML *attribute* rather than a DOM property — needed for
    attributes with no corresponding DOM property, like `[attr.aria-label]="label"` or
    `[attr.colspan]="n"`.
  - **Class and style bindings**: `[class.active]="isActive"` toggles a single CSS
    class based on a boolean; `[style.color]="color"` sets a single inline style.
    `[ngClass]`/`[ngStyle]` (from `CommonModule`) handle multiple classes/styles from
    an object at once, e.g. `[ngClass]="{ active: isActive, disabled: isDisabled }"`.

**Why it's useful**: property binding is how *every* dynamic DOM value gets set —
disabling a button while a form is invalid, toggling a CSS class, passing data into a
child component. It's the "class → view" half of every interactive UI.

## Event binding

**Event binding** (`(event)="handler($event)"`) runs a class method in response to a
DOM event or a child component's `@Output()`/`output()` emission.

```html
<button type="button" (click)="open.set(true)">+ Add expense</button>
<form [formGroup]="form" (ngSubmit)="submit()">
```

- **Key Concepts**
  - **`$event`**: the built-in template variable holding the event payload — the
    native DOM `Event` for a DOM event (`(input)="onInput($event)"` →
    `$event.target.value`), or whatever value an `@Output()`/`output()` emitted for a
    component event.
  - **Direction: view → class.** This is the counterpart to property binding; between
    them they cover the two halves of one-way data flow.
  - **Custom component events**: `(changed)="onChanged($event)"` listens for a parent
    component's own `output()`/`@Output()` emission exactly like a native DOM event —
    Angular doesn't distinguish syntactically between the two.
  - **Angular-specific events**: some "events" are Angular's own, not native DOM events
    — e.g. `(ngSubmit)` on a form (fires on submit *and* calls `preventDefault()` for
    you, unlike the native `submit` event).

**Why it's useful**: event binding is how the UI reacts to the user — clicks, input,
form submission, custom child-component events. Combined with property binding, it's
the whole "keep view and state in sync" story before you even bring in forms or
routing.

## Two-way binding

**Two-way binding** (`[(x)]="field"` — the "banana in a box", `[()]` looking like a
banana in a box) keeps a class field and a view value synchronized in both directions
in a single piece of syntax.

```html
<input [(ngModel)]="searchTerm" placeholder="Search expenses..." />
```

```ts
// [(ngModel)]="searchTerm" desugars to:
[ngModel]="searchTerm"                          // property binding: class -> view
(ngModelChange)="searchTerm = $event"           // event binding: view -> class
```

- **Key Concepts**
  - **It's sugar, not new syntax.** `[(x)]="field"` is exactly
    `[x]="field" (xChange)="field = $event"` — a property binding paired with an
    event binding whose name is the property name plus `Change`. This pattern works
    for *any* component that exposes a matching `x` input and `xChange` output, not
    just `ngModel`.
  - **`ngModel`** (from `FormsModule`) provides two-way binding for native form
    controls — the basis of template-driven forms (Phase 5). It must be imported into
    a standalone component's `imports` array to be used.
  - **`model()`** (Angular 17.2+) is a signal-based way to *define* a two-way-bindable
    input on your own component — declaring `readonly value = model(0);` automatically
    creates both the `value` input and the `valueChange` output the `[(value)]` syntax
    needs, without hand-writing an `EventEmitter`.
  - **Custom two-way binding example**:
    ```ts
    @Component({ selector: 'app-counter', template: `...` })
    export class Counter {
      readonly count = model(0);           // creates `count` input + `countChange` output
      increment() { this.count.update(n => n + 1); }
    }
    ```
    ```html
    <app-counter [(count)]="total" />   <!-- keeps `total` in the parent synced -->
    ```

**Why it's useful**: two-way binding removes the boilerplate of manually wiring a
property binding plus its matching event handler for the extremely common "this input
mirrors this piece of state" case — most visibly in forms, but useful for any
component exposing a value the parent should be able to both read and set.

## Built-in control flow: `@if` / `@for` / `@switch`

Modern Angular (stable since v17) replaced the `*ngIf`/`*ngFor`/`*ngSwitch` structural
directives with **block control flow** built directly into the template compiler.

```html
<!-- expense-list.html — real code from this repo -->
@if (expenses().length > 0) {
  <ul>
    @for (e of expenses(); track e.id) {
      <li>
        <a [routerLink]="['/expenses', e.id]">{{ e.description }}</a>
        — {{ e.amountCents / 100 | currency }} <em>({{ e.category }})</em>
      </li>
    }
  </ul>
  <p><strong>Total: {{ totalCents() / 100 | currency }}</strong></p>
} @else {
  <p>No expenses yet.</p>
}
```

```html
<!-- expense-detail.html — @if ... as binds the truthy value to a local variable -->
@if (expense(); as e) {
  <h2>{{ e.description }}</h2>
  <p>Amount: {{ e.amountCents / 100 | currency }}</p>
} @else {
  <p>Expense not found.</p>
}
```

```html
@switch (status()) {
  @case ('loading') { <p>Loading…</p> }
  @case ('error')   { <p>Something went wrong.</p> }
  @default          { <p>Ready.</p> }
}
```

- **Key Concepts**
  - **`track` is required in `@for`.** It's the identity key Angular uses to match
    items across re-renders — conceptually identical to React's `key` prop. Giving
    Angular a stable unique id (`track e.id`) lets it reuse existing DOM nodes when the
    list changes instead of destroying and recreating everything; `track $index` (or
    forgetting `track` — which the compiler won't even let you do) defeats this
    optimization and can cause form-state or animation bugs when items are
    inserted/removed/reordered.
  - **`@if (expr; as name)`**: binds the (truthy) result of `expr` to a local template
    variable `name`, avoiding re-evaluating (and, for a signal or an Observable via
    `async`, re-subscribing to) the same expression multiple times inside the block.
  - **`@empty`**: an optional block on `@for` that renders when the iterable is empty —
    `@for (e of expenses(); track e.id) { ... } @empty { <li>No expenses</li> }`.
  - **No imports needed**: unlike the legacy `*ngIf`/`*ngFor` structural directives
    (which require importing `CommonModule` or the individual directives), `@if`/`@for`/
    `@switch` are part of the template syntax itself — nothing to add to `imports`.
  - **Legacy equivalents** (`*ngIf`, `*ngFor`, `*ngSwitch`) still appear in
    pre-v17 code and any un-migrated template:
    ```html
    <!-- Legacy structural directives -->
    <li *ngFor="let e of expenses; trackBy: trackById">{{ e.description }}</li>
    <p *ngIf="expenses.length === 0">No expenses yet.</p>
    ```
    `*ngFor` uses a `trackBy` *function* reference instead of an inline `track`
    expression; `*ngIf`/`*ngFor` are directives (prefixed with `*`, Angular's
    "structural directive" shorthand) rather than compiler-level syntax, which is part
    of why the new block syntax compiles to more efficient instructions.

| | `*ngIf`/`*ngFor` (legacy) | `@if`/`@for` (v17+) |
|---|---|---|
| Import needed | `CommonModule` (or the individual directive) | None — built into the template compiler |
| Performance | Directive-based, more runtime overhead | Compiler-optimized, generally faster |
| Identity key | `trackBy: fn` (a function reference) | `track expr` (an inline expression) |
| Empty-list case | Separate `*ngIf="list.length === 0"` block | Built-in `@empty` block |
| Readability | HTML attribute syntax, easy to miss on a wrapper `<ng-container>` | Reads like ordinary block-structured code |

**Why it's useful**: nearly every template needs conditional rendering and list
rendering — this is the single most-used piece of Angular template syntax after
interpolation. `track` in particular is a very common interview question because
getting it wrong (or omitting it) is a real, easy-to-make performance/correctness bug.

## Pipes

A **pipe** transforms a value for display directly inside a template, using the `|`
syntax — display-layer formatting, not a place for business logic.

```html
{{ e.amountCents / 100 | currency }}          <!-- e.g. 1250 -> "$12.50" -->
{{ today | date:'mediumDate' }}                <!-- e.g. "Sep 22, 2026" -->
{{ description | uppercase | slice:0:10 }}     <!-- pipes chain left-to-right -->
```

- **Key Concepts**
  - **Built-in pipes**: `currency`, `date`, `number`, `percent`, `json` (handy for
    debugging — dumps any value as formatted JSON), `uppercase`/`lowercase`, `slice`,
    and the crucial **`async`** pipe, which subscribes to an Observable or resolves a
    Promise and renders its latest value, automatically unsubscribing when the
    component is destroyed (Phase 4 covers this in depth).
  - **Must be imported.** Because standalone components declare their own template
    dependencies, using `| currency` requires importing `CurrencyPipe` from
    `@angular/common` into the component's `imports` array — the `expense-list`
    component does exactly this.
  - **Chaining and parameters**: pipes chain left to right (`value | pipeA | pipeB`),
    and take parameters with a colon (`date:'shortDate'`, `slice:0:10`).
  - **Custom pipes**: a class decorated `@Pipe({ name: 'myPipe' })` implementing a
    `transform(value, ...args)` method:
    ```ts
    @Pipe({ name: 'centsToDollars' })
    export class CentsToDollarsPipe implements PipeTransform {
      transform(cents: number): string {
        return `$${(cents / 100).toFixed(2)}`;
      }
    }
    // usage: {{ e.amountCents | centsToDollars }}
    ```
  - **Pure vs impure pipes**: a **pure** pipe (the default) only recomputes when its
    input changes *by reference* — cheap, since Angular can skip re-running it most
    change-detection cycles. An **impure** pipe (`@Pipe({ name: '...', pure: false })`)
    re-runs on every change-detection cycle regardless of whether its input changed by
    reference — needed for pipes over mutable data (e.g. filtering an array that's
    mutated in place), but expensive. Prefer pure pipes and immutable data; reach for
    `pure: false` only when you can't avoid mutation.
  - **Pipes are presentation-only.** Business logic (validation, calculations that
    matter beyond display) belongs in the class or an injected service — a pipe should
    be a pure, side-effect-free formatting function.

**Why it's useful**: pipes keep templates declarative and readable — `{{ amount |
currency }}` says exactly what's rendered without cluttering the class with formatting
methods, and the built-in ones (especially `async`) remove a large amount of manual
subscription/formatting boilerplate you'd otherwise write by hand.

## Template reference variables

A **template reference variable** (`#name`) gives a template-local name to a DOM
element or directive instance, usable elsewhere in the same template.

```html
<input #searchBox (keyup.enter)="search(searchBox.value)" placeholder="Search..." />
<button (click)="searchBox.focus()">Focus search</button>
```

- **Key Concepts**
  - `#searchBox` refers to the native `<input>` DOM element — `searchBox.value`,
    `searchBox.focus()` are plain DOM API, not Angular-specific.
  - On a component or directive, `#name` refers to the *component/directive instance*
    instead of the raw DOM element, letting you call its public methods/read its
    public fields from the template (e.g. `#form="ngForm"` gives you the `NgForm`
    directive instance for a template-driven form).
  - Scope: a template reference variable is visible anywhere in the same template
    (including *before* its declaration in document order), but not from the component
    class — it's a template-only concept.

**Why it's useful**: it's the lightweight, template-local way to reach a DOM
element/directive without wiring up a `@ViewChild()` in the class — handy for quick
things like focusing an input or reading its value on an event, without adding a class
field for something purely presentational.

## Change detection (the engine underneath)

**Change detection** is the process by which Angular notices that data changed and
updates the DOM to match — every binding described above depends on it running.

- **Key Concepts**
  - **Classic (Zone.js-based)**: Angular patches async browser APIs
    (`setTimeout`, `addEventListener`, Promise resolution, XHR, etc.) via **Zone.js**,
    so it knows when *something* async happened and can trigger a change-detection pass
    over the component tree afterward. This is "tree-wide": Angular re-checks bindings
    in every component (except ones explicitly opted out) after each such event.
  - **`ChangeDetectionStrategy.OnPush`**: an opt-in per-component strategy that skips
    re-checking a component unless one of its `@Input()`s changed *by reference*, a
    DOM event originated from within it, or an Observable it's subscribed to (via
    `async`) emitted. A major performance lever in large trees with immutable data.
  - **Signals — fine-grained reactivity**: reading a signal inside a template subscribes
    *just that binding* to it. When the signal changes, Angular updates precisely the
    views that depend on it, without walking the whole tree — a more targeted mechanism
    than Zone.js's tree-wide checks.
  - **Zoneless Angular**: because signals already know exactly what changed and where,
    Angular can (as of recent versions, opt-in) run without Zone.js entirely, relying on
    signals (and explicit APIs like `ChangeDetectorRef.markForCheck()` for non-signal
    cases) to know when to update — smaller bundle (no Zone.js), more predictable
    timing, but requires code to not rely on Zone's implicit "anything async triggers a
    check" behavior.

**Why it's useful**: understanding change detection explains *why* bindings update —
it's not magic, it's Angular re-evaluating expressions after it's notified (by Zone.js,
or by a signal) that something might have changed. This becomes directly relevant when
diagnosing "why isn't my view updating" bugs (usually: mutated an object in place
instead of replacing it, under `OnPush`) and when discussing performance in a large
app.

## Summary — Key takeaways

- Four core bindings: `{{ }}` interpolation (read), `[prop]` property binding (write a
  DOM/component property), `(event)` event binding (react to an event), `[(x)]`
  two-way binding (sugar for `[x]` + `(xChange)`).
- `[prop]` sets a DOM *property*; `[attr.x]` sets an HTML *attribute* — know the
  difference (`disabled` is the classic example).
- `@if`/`@for`/`@switch` (v17+) replace `*ngIf`/`*ngFor`/`*ngSwitch`: no imports needed,
  compiler-optimized, and `@for` *requires* a `track` expression — always use a stable
  id, never the index.
- Pipes (`| currency`, `| date`, `| async`, custom) are for display-only
  transformation; prefer pure pipes; standalone components must import the pipes they
  use.
- The `async` pipe is the idiomatic way to render an Observable/Promise in a template —
  it subscribes and unsubscribes for you.
- Change detection is what actually keeps bindings in sync — classically Zone.js
  triggers tree-wide checks; signals enable fine-grained, targeted updates and are the
  path toward zoneless Angular.
