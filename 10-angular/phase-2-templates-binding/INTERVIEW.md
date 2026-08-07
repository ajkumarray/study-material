<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · components](../phase-1-components/NOTES.md) | [Phase 3 · dependency injection ➡](../phase-3-dependency-injection/NOTES.md)
<!-- /nav -->

# Phase 2 — Templates & Data Binding: Interview Q&A

⭐ = asked constantly.

**Q: What are the types of data binding in Angular?** ⭐⭐
Interpolation `{{ }}` (value → text), property binding `[prop]` (value → DOM/component
property), event binding `(event)` (DOM/output event → method), and two-way binding
`[(ngModel)]` (both directions). Two-way is sugar for a property binding plus an event
binding.

**Q: What is two-way binding and how does it work?** ⭐
`[(x)]="field"` keeps a component field and a view value in sync. It desugars to
`[x]="field"` + `(xChange)="field = $event"` — a property binding plus an event binding.
`ngModel` provides it for form inputs.

**Q: How does the new control flow differ from `*ngIf`/`*ngFor`?** ⭐⭐
`@if`/`@for`/`@switch` are built into the template compiler (v17+), so they need no
imports, are faster, and read like normal code with `@else`/`@empty` blocks. They
replace the `*ngIf`/`*ngFor` structural directives (which required importing
`CommonModule`).

**Q: Why is `track` required in `@for`?** ⭐⭐
It's the identity key Angular uses to match items across renders (like React's `key`),
so it can move/reuse DOM nodes instead of destroying and recreating them. Use a stable
unique id; using the index defeats the optimization and can cause state/DOM bugs.

**Q: What is a pipe?** ⭐
A template transform applied with `|` for presentation — `currency`, `date`, `async`,
etc. It formats a value without changing the underlying data. Standalone components must
import the pipes they use; custom pipes implement `transform()` with `@Pipe`.

**Q: What does the `async` pipe do and why use it?** ⭐⭐
It subscribes to an Observable (or Promise) in the template, renders the latest emitted
value, and **automatically unsubscribes** when the component is destroyed — preventing
memory leaks and removing manual subscription management. It's the idiomatic way to
consume Observables in templates.

**Q: Pure vs impure pipes?**
A pure pipe (default) recomputes only when its inputs change by reference — cheap and
preferred. An impure pipe runs on every change-detection cycle (needed for
mutable/stateful inputs like `async`), which is more expensive. Prefer pure pipes and
immutable data.

**Q: How does Angular know when to update the DOM?** *nuance*
Change detection. Classically Zone.js patches async APIs and triggers a tree check after
events/timers/HTTP. Signals enable fine-grained, targeted updates (only views reading a
changed signal), which is moving Angular toward zoneless change detection. Bindings stay
in sync because change detection re-evaluates them.
