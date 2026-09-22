<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · dependency injection](../phase-3-dependency-injection/NOTES.md) | [Phase 5 · routing forms ➡](../phase-5-routing-forms/NOTES.md)
<!-- /nav -->

# Phase 4 — Reactivity: Signals & RxJS: Interview Q&A

⭐ = asked constantly.

**Q: What is a signal, and how do you read one versus write to one?** ⭐⭐
A signal is a reactive container for a value: you read it by *calling* it as a
function (`count()`), which registers whoever is reading it — a template binding, a
`computed()`, an `effect()` — as a dependent that will be notified on change. You write
to it with `.set(newValue)` (replace outright) or `.update(fn)` (derive the new value
from the current one, the idiomatic way to do immutable updates on an array/object,
e.g. `this._expenses.update(list => [...list, newItem])`). Anything that read the
signal automatically re-evaluates when it changes — no manual subscription, no
dependency array to maintain by hand.

**Q: `computed` vs `effect` — what's the difference and when do you use each?** ⭐
`computed(fn)` produces a derived, **read-only**, memoized signal — use it any time you
need a value calculated from other signals (`totalCents` derived from the expense
list). It recomputes lazily, only when read again after a dependency actually changed,
and the result is cached in between. `effect(fn)` runs a **side effect** — logging,
persisting to `localStorage`, syncing a non-reactive API — whenever a signal it reads
changes; it produces no value other code consumes. The rule of thumb: if you're
computing something other code will read, use `computed`; if you're doing something
that happens *because* a value changed (with no return value that matters), use
`effect`. Using `effect` to compute derived state (calling `.set()` on another signal
from inside it) is an anti-pattern — it works but reimplements what `computed` already
does, less efficiently and with a harder-to-follow dependency graph.

**Q: What is an Observable, and how is it different from a Promise?** ⭐⭐
An Observable is a lazy, potentially-multi-valued stream: nothing happens until
`.subscribe()` is called, and it can emit zero, one, or many values over time (plus
optionally error or complete), with `.pipe(...operators)` to transform the stream. A
Promise starts executing the moment it's created (eager), and resolves exactly once. In
Angular, Observables are the shape of `HttpClient` responses, router navigation events,
and reactive forms' `valueChanges` — anything modeling something that happens
(possibly repeatedly) over time.

**Q: What does the `async` pipe do, and why is it preferred over a manual
`.subscribe()` in a component?** ⭐⭐
`| async` subscribes to an Observable (or resolves a Promise) directly in the template,
renders the latest emitted value, and automatically unsubscribes when the component is
destroyed. A manual `.subscribe()` call in a component class creates a subscription
that must be explicitly torn down (typically in `ngOnDestroy`) or it leaks — the
subscription keeps the component instance and anything it closes over alive even after
Angular has removed it from the view. The `async` pipe removes that entire failure
mode by tying the subscription's lifetime declaratively to the template.

```html
@if (foodExpenses$ | async; as foods) {
  @for (f of foods; track f.label) { <li>{{ f.label }}</li> }
}
```

**Q: `switchMap` vs `mergeMap` vs `concatMap` — what's the difference?** ⭐⭐
All three flatten a stream-of-streams (an outer Observable whose emitted values are
*themselves* Observables) into one stream — a plain `map` there would produce
`Observable<Observable<T>>`, which isn't usually useful. They differ in concurrency
behavior: `switchMap` cancels the previous inner subscription the moment a new outer
value arrives — the standard choice for typeahead search, where only the latest
request's result matters and in-flight stale requests should be discarded. `mergeMap`
runs every inner Observable concurrently to completion — use it when each emission
must independently complete regardless of what happens after (e.g. firing several
parallel writes). `concatMap` runs inner Observables one at a time, strictly in order,
starting the next only after the previous completes — use it when both order and
non-overlap matter (e.g. sequential dependent writes).

*Follow-up: "You use `mergeMap` for a search box by mistake — what breaks?"* — Every
keystroke's request keeps running even after a newer one is fired, so responses can
arrive out of order; a slow response to an earlier, now-stale search term can overwrite
the UI with results for a term the user already changed. `switchMap` avoids this by
cancelling the stale request outright.

**Q: How do you avoid memory leaks with RxJS?** ⭐
Prefer never leaving a bare `.subscribe()` uncleaned up. The `async` pipe handles the
common "render this stream in the template" case automatically. When you genuinely need
imperative logic on each emission (not just rendering), use `takeUntilDestroyed(destroyRef)`
— an operator that completes the stream (ending the subscription) when the current
component/directive is destroyed, via an injected `DestroyRef`. Both approaches tie the
subscription's lifetime to the component's, so navigating away or destroying the
component reliably cleans it up instead of leaving it running in the background
(System Design Phase 7 — resource leaks).

```ts
private readonly destroyRef = inject(DestroyRef);
someStream$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(v => { /* ... */ });
```

**Q: When do you use signals vs RxJS?** ⭐⭐
Signals for synchronous, in-memory state and values derived from it — simpler,
subscription-free, and update only the specific views/computeds that depend on them.
RxJS for genuinely asynchronous streams and anything time-based — HTTP calls, WebSocket
messages, debounced input, cancellation, combining multiple sources. The current
Angular guidance is "signals for state, RxJS for events/async": signals have absorbed
most of the plain-state use cases RxJS used to be reached for out of habit (e.g. a
`BehaviorSubject` just to hold whether a panel is open), while RxJS remains the right
tool wherever there's a real stream of values arriving over time.

**Q: How do you bridge signals and Observables?**
`toSignal(obs$, { initialValue })` converts an Observable into a signal — the idiomatic
way to consume, say, an HTTP call's result as plain reactive state without a manual
subscription. `toObservable(sig)` goes the other way, turning a signal's changes into
an Observable stream — useful when a signal-derived value needs to feed into RxJS
operators (e.g. debouncing changes to a search-term signal). This lets a service fetch
data with RxJS/`HttpClient` but expose the result to the rest of the app as a plain
signal, using each tool where it's strongest.

```ts
readonly foods = toSignal(this.foodExpenses$, { initialValue: [] as ExpenseView[] });
readonly foodCount = computed(() => this.foods().length);   // computed built on the bridged signal
```

**Q: How do signals affect Angular's change detection?** ⭐
They enable fine-grained, targeted updates: reading a signal in a template subscribes
only that specific binding to it, so a change to the signal updates exactly the DOM
that depends on it, without Angular walking and re-checking the whole component tree —
unlike the classic Zone.js model, where an async event (any `setTimeout`, any HTTP
response) triggers a tree-wide check. This precision is what makes Angular's **zoneless**
mode viable: without signals telling Angular exactly what changed, Zone.js's blunt
"something async happened, re-check everything" strategy would be the only option.

**Q: What is `linkedSignal`, and how is it different from a plain `computed`?**
*nuance* A `computed()` signal is always derived and can never be independently
written to — call `.set()` on it and you get a compile error. `linkedSignal()`
produces a signal whose *default* value is derived from another signal (like
`computed`), but which can also be locally overridden with `.set()`/`.update()` after
the fact — and it automatically resets to the freshly-derived default whenever its
source signal changes again. A concrete example: a "selected category" signal that
defaults to the first category in a filtered list, but which the user can manually pick
a different one — and which should snap back to a sensible default if the underlying
category list itself changes. Neither a plain `signal()` (no automatic reset) nor a
plain `computed()` (never independently writable) covers that case alone.

**Q: Why does `ExpenseService.add()` use `this._expenses.update(list => [...list,
newItem])` instead of `this._expenses().push(newItem)`?** ⭐
Because `.push()` mutates the existing array *in place* — the signal's underlying
reference never changes, so nothing observing it is notified, and any `OnPush`
component depending on the array as an input wouldn't re-render either (Phase 2).
`.update()` with a spread creates a **new** array reference and hands it to the signal,
which is what actually triggers change notification to every dependent — the template,
any `computed()` built on top of it, and any `effect()` watching it. This immutable-
update discipline is the same reason `OnPush` change detection cares about reference
equality, and it's a very common place for beginners to introduce a silent "my UI
doesn't update" bug.
