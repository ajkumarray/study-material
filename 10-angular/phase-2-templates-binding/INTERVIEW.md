<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · components](../phase-1-components/NOTES.md) | [Phase 3 · dependency injection ➡](../phase-3-dependency-injection/NOTES.md)
<!-- /nav -->

# Phase 2 — Templates & Data Binding: Interview Q&A

⭐ = asked constantly.

**Q: What are the types of data binding in Angular?** ⭐⭐
Four: **interpolation** `{{ expr }}` renders a value as text (class → view, read-only).
**Property binding** `[prop]="expr"` sets a DOM property or a component `@Input()`
(class → view). **Event binding** `(event)="handler($event)"` runs a method in
response to a DOM event or component `@Output()` (view → class). **Two-way binding**
`[(x)]="field"` keeps a value synced in both directions — it's sugar for
`[x]="field" (xChange)="field = $event"`, not a distinct mechanism. There's also
**attribute binding** `[attr.x]="expr"` for setting an HTML attribute that has no
corresponding DOM property (e.g. `aria-*`, `colspan`).

**Q: What's the difference between property binding and attribute binding?** ⭐
Property binding (`[disabled]="isDisabled"`) sets the live DOM *property*, which is
what actually drives element behavior — a `false` value truly disables nothing. HTML
*attributes* (plain `disabled="false"`) are just initial values parsed from markup;
their presence alone can make some attributes truthy regardless of the string content.
Attribute binding (`[attr.colspan]="n"`) exists for the attributes that have no
matching DOM property at all, so there's nothing to property-bind to. In practice: use
`[prop]` for anything with a DOM property (almost everything), and `[attr.x]` only when
you specifically need to set a raw attribute like `aria-label` or a custom `data-*`
attribute.

**Q: How does two-way binding actually work under the hood?** ⭐
`[(x)]="field"` desugars to a property binding plus an event binding:
`[x]="field" (xChange)="field = $event"`. `ngModel` (from `FormsModule`) provides this
pair for native form controls. You can create the same pattern on your own component
with a signal-based `model()` — `readonly value = model(0)` automatically generates
both the `value` input and `valueChange` output that `[(value)]` needs, so
`<app-counter [(count)]="total" />` works without hand-writing an `EventEmitter`.

**Q: How does the new control flow (`@if`/`@for`/`@switch`) differ from
`*ngIf`/`*ngFor`/`*ngSwitch`?** ⭐⭐
The legacy forms are *structural directives* (note the `*` prefix) that require
importing `CommonModule` or the specific directive, and are implemented as runtime
directives with more overhead. `@if`/`@for`/`@switch` (stable since v17) are built
directly into the template compiler: no imports needed, compiled to more efficient
instructions, and they read like ordinary block-structured code with `@else`/`@empty`
built in rather than a second `*ngIf` for the empty case. `@if (expr; as name)` also
lets you bind the truthy result to a local variable, avoiding re-evaluating the same
expression (or re-subscribing an `async`-piped Observable) multiple times in one block.

**Q: Why is `track` required in `@for`, and what happens if you use the index?** ⭐⭐
`track` is the identity key Angular uses to match array items across re-renders —
conceptually the same job as React's `key` prop. With a stable value (`track e.id`),
Angular can diff the old and new list, reuse existing DOM nodes for items that didn't
change, and only create/destroy nodes for items that were actually added/removed. If
you `track $index` instead, Angular ties DOM node identity to *position*, not to the
underlying item — so if an item is deleted from the middle of the list, every node
after it appears to have "changed" (because the item at that index changed), and
Angular reuses/mutates the wrong DOM node for each position. This can produce visible
bugs like a text input retaining the wrong value, incorrect animations, or lost
component-local state. `@for` doesn't even compile without a `track` expression — the
Angular compiler forces the decision explicitly, unlike legacy `*ngFor`, where omitting
`trackBy` silently falls back to identity-by-reference (destroy/recreate everything on
any change).

```html
@for (e of expenses(); track e.id) {
  <li>{{ e.description }}</li>
} @empty {
  <li>No expenses</li>
}
```

**Q: What is a pipe, and when would you write a custom one?** ⭐
A template-only transform applied with `|` — `currency`, `date`, `uppercase`, `slice`,
`json`, and the crucial `async` pipe are built in. A custom pipe is a class decorated
`@Pipe({ name: '...' })` implementing `transform(value, ...args)`; you'd write one for
a display transform you use in multiple templates (e.g. `{{ cents | centsToDollars }}`)
to avoid repeating formatting logic. Pipes should stay presentation-only — pure
formatting, no business logic or side effects (beyond the intentional exception of the
`async` pipe, whose entire job is a side effect: subscribing).

**Q: What does the `async` pipe do and why prefer it over manual `.subscribe()`?** ⭐⭐
It subscribes to an Observable (or resolves a Promise) directly in the template,
renders the latest emitted value, and automatically unsubscribes when the component is
destroyed. Manual `.subscribe()` calls in a component class must be manually torn down
in `ngOnDestroy` or they leak (the subscription keeps the component instance and
anything it closes over alive). The `async` pipe removes that entire class of bug and
keeps the subscription's lifetime declaratively tied to the template that uses it.

```html
@if (expenses$ | async; as expenses) {
  @for (e of expenses; track e.id) { <li>{{ e.description }}</li> }
}
```

**Q: Pure vs impure pipes — what's the difference and why does it matter?**
A pure pipe (the default) only recomputes when its input changes *by reference* —
Angular can safely skip re-running it on change-detection cycles where the reference
is unchanged, which is cheap and is why pure is the default. An impure pipe
(`pure: false`) reruns on *every* change-detection cycle regardless of reference
equality — necessary for pipes that need to see mutations to the same object/array
(mutable data), but potentially expensive if the pipe does real work, since it now runs
far more often than the data actually changes. The practical guidance: prefer
immutable data + pure pipes; reach for an impure pipe only when you genuinely can't
avoid mutation.

**Q: What is a template reference variable and how is it different from a component
class field?**
`#name` on an element (`<input #box>`) gives that DOM element (or, on a
component/directive, that instance) a name usable elsewhere in the *same template* —
`box.value`, `box.focus()`. It's template-local: you can't read it from the component
class. It's the lightweight alternative to a `@ViewChild()` for quick, purely
presentational needs (focusing an input, reading a value on a sibling event) without
adding a class field.

**Q: How does Angular know when to re-check bindings and update the DOM?** *nuance*
Change detection. Classically, Zone.js monkey-patches async browser APIs
(`setTimeout`, event listeners, Promise resolution, XHR) so Angular is notified
whenever something async happens, and it then re-checks bindings across the component
tree (or a subtree, under `OnPush`). Signals change this: reading a signal in a
template subscribes only that specific binding to it, so a signal change triggers a
targeted update of exactly the views that depend on it, without a tree-wide check. This
fine-grained model is what makes Angular's zoneless mode possible — Zone.js becomes
unnecessary once signals (plus explicit APIs for the remaining non-signal cases) can
tell Angular precisely what changed.

*Follow-up: "You set `changeDetection: OnPush` on a component and a child stops
updating when you mutate an array in place. Why?"* — `OnPush` only re-checks a
component when an `@Input()` changes *by reference* (among a couple of other triggers).
Mutating an array in place (`arr.push(x)`) keeps the same reference, so Angular sees no
change and skips re-checking that component. The fix is to replace the reference
(`this.items = [...this.items, x]`) or, in modern Angular, to hold the array as a
signal and use `.update()`, which is exactly this immutable-replace pattern built in.

**Q: A component with `OnPush` still updates correctly when a signal inside it
changes — why doesn't it need a new `@Input()` reference?** *nuance*
Because signal reads register their own fine-grained dependency independent of
`OnPush`'s input-reference check — a signal that changed is one of `OnPush`'s
recognized triggers for re-checking the component, alongside a changed `@Input()`
reference, a DOM event originating inside it, and an `async`-piped emission. This is
part of why the ecosystem is moving toward signals as the default state model: they
compose cleanly with `OnPush` (and eventually zoneless) without the "did I remember to
replace the reference" footgun that plain mutable class fields have.
