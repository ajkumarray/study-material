<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · dependency injection](../phase-3-dependency-injection/NOTES.md) | [Phase 5 · routing forms ➡](../phase-5-routing-forms/NOTES.md)
<!-- /nav -->

# Phase 4 — Reactivity: Signals & RxJS: Notes

Angular has **two** reactivity systems that solve different problems. **Signals** are
for synchronous, in-memory state and values derived from it. **RxJS Observables** are
for asynchronous *streams* of values over time (HTTP responses, DOM event streams,
router events). Modern Angular's guidance is: reach for signals for state, RxJS for
async/events, and bridge between the two where a feature genuinely needs both.

## Signals

A **signal** is a reactive container for a value that notifies its readers when the
value changes — Angular's mechanism for fine-grained reactivity, conceptually similar
to a React `useState` combined with automatic dependency tracking (you never write a
dependency array).

```ts
import { signal, computed, effect } from '@angular/core';

const count = signal(0);          // create a signal holding 0
count();                          // READ by calling it -> 0 (and registers the reader as a dependent)
count.set(5);                     // REPLACE the value outright -> now 5
count.update(n => n + 1);         // DERIVE the new value from the current one -> now 6

const doubled = computed(() => count() * 2);     // a DERIVED, memoized, read-only signal
console.log(doubled());           // 12

effect(() => console.log('count is now', count()));  // SIDE EFFECT, re-runs whenever count() changes
count.set(10);                    // logs: "count is now 10"
```

This repo's `ExpenseService` is signal-based end to end:

```ts
// expense-app/src/app/expense.service.ts
private readonly _expenses = signal<Expense[]>([...]);
readonly expenses = this._expenses.asReadonly();
readonly totalCents = computed(() => this._expenses().reduce((s, e) => s + e.amountCents, 0));

add(input: Omit<Expense, 'id'>): void {
  // update() replaces the array immutably — every reader (template, computed) refreshes.
  this._expenses.update((list) => [...list, { id: list.length + 1, ...input }]);
}
```

In `expense-list.html`, `{{ totalCents() / 100 | currency }}` reads `totalCents()`
directly in the template — that read registers the *template binding itself* as a
dependent, so adding an expense re-renders exactly that line, not the whole component
tree.

- **Key Concepts**
  - **`signal(initialValue)`**: creates a writable signal. Read it by *calling* it as a
    function — `count()`, not `count`. This is different from React state, where you
    read the variable directly; forgetting the parentheses in Angular is a very common
    beginner mistake (it references the signal function itself, not its value).
  - **`.set(value)`**: replace the value outright.
  - **`.update(fn)`**: derive the new value from the current one — the idiomatic way to
    do immutable updates on objects/arrays held in a signal (`list => [...list, x]`
    rather than mutating in place).
  - **`computed(fn)`**: a read-only, **derived** signal. It recomputes lazily — only
    when read again *after* one of its dependencies changed — and the result is cached
    between changes, so reading it repeatedly without an intervening dependency change
    is free. Use it for anything calculated from other signals (`totalCents` from
    `_expenses`), never for something with side effects.
  - **`effect(fn)`**: runs a side effect (logging, syncing to `localStorage`, calling a
    non-reactive API) automatically whenever any signal it reads changes. It has no
    return value used by the reactive graph — if you're computing a value other code
    will read, that's what `computed` is for, not `effect`.
  - **`.asReadonly()`**: returns a read-only view of a writable signal — same current
    value, but no `.set()`/`.update()` on the returned reference. `ExpenseService`
    exposes `expenses` this way so only the service itself (via `add()`) can mutate the
    underlying list — encapsulation, same idea as a private field with a getter.
  - **Fine-grained reactivity**: reading a signal inside a template binds *only that
    binding* to it. A signal change updates precisely the parts of the DOM that depend
    on it, without Angular needing to re-check the whole component tree — this is the
    mechanism that makes zoneless change detection (Phase 2) possible.

**Why it's useful**: signals remove the ceremony RxJS used to require for plain
synchronous state (a `BehaviorSubject` + manual subscription just to hold "is this
panel open") — a `signal(false)` plus `.set()`/`.update()` does the same job with no
subscription management, no unsubscribe-on-destroy concern, and less code.

## Signal-based component inputs (`input()`, `model()`)

Beyond plain `signal()`, Angular exposes signal-shaped helpers specifically for
component communication (introduced in Phase 1, detailed here for their reactive
behavior):

```ts
// expense-app/src/app/expense-detail/expense-detail.ts
readonly id = input.required({ transform: (v: string) => Number(v) });
readonly expense = computed(() => this.service.getById(this.id()));   // reactive: recomputes whenever id() changes
```

- **Key Concepts**
  - **`input()`/`input.required()`**: the incoming value is a real signal — reading it
    inside a `computed()` automatically re-derives that computed whenever a new value
    arrives, with no `ngOnChanges` boilerplate.
  - **`model()`**: a signal that is both readable *and* writable from outside the
    component, generating the paired input + `xChange` output automatically for
    `[(x)]` two-way binding (Phase 2) — a signal-based alternative to hand-writing an
    `@Input()` + `@Output() EventEmitter` pair for the same job.
  - **`linkedSignal()`**: a writable signal whose default value is *derived* from
    another signal, but which the user can then locally override — e.g. a
    "selectedCategory" signal that resets to the first available category whenever the
    category list changes, but that the user can still manually reassign in between.
    It fills the gap between a plain `computed()` (never independently writable) and a
    plain `signal()` (no automatic reset-on-dependency-change).

**Why it's useful**: these make component inputs first-class participants in the
signal graph — an input flows straight into a `computed()`/`effect()` the same way any
other signal would, instead of needing a lifecycle hook to notice it changed.

## RxJS Observables

**RxJS** models asynchronous sequences of values over time as **Observables** — the
foundation under Angular's async-facing APIs: `HttpClient`, router events, and reactive
forms' `valueChanges`.

```ts
import { Observable, of, delay } from 'rxjs';

// A stand-in for an HttpClient call — in a real app this is http.get<Expense[]>(url).
function loadFromApi(): Observable<Expense[]> {
  return of(sampleExpenses).pipe(delay(10));   // emits the array once, after a fake delay
}

loadFromApi().subscribe((expenses) => {
  console.log('loaded', expenses.length, 'expenses');   // "loaded 3 expenses" (after ~10ms)
});
```

- **Key Concepts**
  - **Lazy**: an Observable does nothing until something calls `.subscribe()` on it —
    creating `loadFromApi()`'s returned Observable doesn't fire the (simulated)
    request; subscribing does. Compare to a Promise, which starts running the moment
    it's created.
  - **Can emit multiple values over time**: unlike a Promise (resolves once), an
    Observable can emit zero, one, or many values, and can error or complete — the
    right shape for things like WebSocket messages, router navigation events, or a
    debounced search-input stream.
  - **`.pipe(...operators)`**: transforms the stream by chaining operators; each
    operator takes a stream in and returns a *new* stream out — nothing is mutated.
  - **`HttpClient`**: Angular's HTTP client returns Observables —
    `http.get<Expense[]>('/api/expenses')` — which you transform with operators and
    render via the `async` pipe rather than a manual `.subscribe()`.

**Why it's useful**: Observables are the vocabulary for *anything happening over
time* — a single HTTP response, a stream of keystrokes, a sequence of router
navigations — with a rich, composable operator library for exactly the kind of logic
(debounce, retry, cancel-and-restart, combine multiple sources) that's painful to
hand-roll with plain Promises/callbacks.

## RxJS operators (`.pipe()`)

See the worked, type-checked companion file
[`rxjs-pipe-example.ts`](./rxjs-pipe-example.ts) for a complete service exercising
every operator below against the same `Expense` shape used elsewhere in this repo.

```ts
// map + filter — reshape and narrow each emitted value.
readonly foodExpenses$: Observable<ExpenseView[]> = this.loadFromApi().pipe(
  map((expenses) => expenses.filter((e) => e.category === 'food')),
  map((foods) => foods.map((e) => ({ label: e.description, dollars: e.amountCents / 100 }))),
  tap((views) => console.log('[pipe] food views:', views)),   // side-effect, stream unchanged
);
```

```ts
// switchMap — the classic search-box pattern: debounce input, cancel the previous
// in-flight request when a new term arrives, and recover from errors.
readonly searchResults$: Observable<ExpenseView[]> = this.searchTerm$.pipe(
  startWith(''),                 // seed an initial value so the stream isn't empty at first
  debounceTime(300),             // wait for typing to pause 300ms
  distinctUntilChanged(),        // ignore repeated identical terms
  switchMap((term) => this.searchApi(term)),   // flatten + cancel the previous inner request
  catchError(() => of([] as ExpenseView[])),   // recover: emit an empty list instead of erroring
);
```

- **Key Concepts / operator cheat-sheet**
  - **`map`**: transform each emitted value — the everyday workhorse.
  - **`filter`**: drop values that don't match a predicate.
  - **`tap`**: run a side effect (logging/debugging) without altering the stream.
  - **`scan`**: a running accumulation over the stream, emitting the accumulated value
    at each step — like `reduce`, but it emits progressively instead of only at the
    end. Good for running totals or building up state from a sequence of events.
  - **`debounceTime(ms)`**: wait for a pause of `ms` with no new emission before
    forwarding the latest one — rate-limits bursty input like keystrokes.
  - **`distinctUntilChanged()`**: skip consecutive duplicate emissions.
  - **`switchMap`**: flattens a stream-of-streams (an Observable whose values are
    *themselves* Observables) into a single stream, and **cancels the previous inner
    subscription** whenever a new outer value arrives. The go-to choice for typeahead
    search and any "only the latest matters" dependent request.
  - **`mergeMap`**: also flattens a stream-of-streams, but keeps **all** inner
    subscriptions running concurrently instead of cancelling — use when every emission
    must run to completion (e.g. firing off several independent writes in parallel).
  - **`concatMap`**: flattens, running inner Observables **one at a time in order** —
    use when order matters and requests must not overlap.
  - **`catchError`**: recover from an error by switching to a fallback stream (e.g.
    `of([])`) instead of letting the error propagate and kill the subscription.
  - **`startWith(value)`**: seed the stream with an initial value before the first real
    emission arrives.

| Operator | Concurrency behavior | Typical use |
|---|---|---|
| `switchMap` | Cancels the previous inner Observable | Typeahead search, "latest wins" |
| `mergeMap` | Runs all inner Observables concurrently | Independent parallel requests |
| `concatMap` | Runs inner Observables one at a time, in order | Order-sensitive sequential requests |

**Why it's useful**: this operator vocabulary is what lets you express "debounce
typing, cancel the previous search, recover from failure" in about five lines instead
of hand-rolled timers and cancellation flags — and it's exactly the kind of async
orchestration signals are *not* designed for (signals are synchronous by design).

## The `async` pipe

The **`async` pipe** subscribes to an Observable (or resolves a Promise) directly
inside a template, renders its latest value, and automatically unsubscribes when the
component is destroyed.

```html
@if (foodExpenses$ | async; as foods) {
  @for (f of foods; track f.label) {
    <li>{{ f.label }} — {{ f.dollars | currency }}</li>
  }
}
```

- **Key Concepts**
  - **No manual subscription management.** Compare to `ngOnInit() { this.sub =
    this.foodExpenses$.subscribe(...) }` plus `ngOnDestroy() { this.sub.unsubscribe()
    }` — the `async` pipe does both automatically, tied to the template's lifetime.
  - **Re-subscribes on a new Observable reference**, unsubscribing from the old one
    first — so binding `| async` to an Observable that's recreated on every
    change-detection cycle causes repeated subscribe/unsubscribe churn; keep the
    Observable reference stable (a class field, not a method call in the template).
  - Combine with `@if (... ; as name)` to read the resolved value once and reuse it
    across multiple lines in the block, rather than piping `| async` repeatedly.

**Why it's useful**: it's the idiomatic, leak-free way to render Observable data in a
template with zero boilerplate — the reason "never leave a bare `.subscribe()` in a
component" is such firm Angular guidance.

## Subscription hygiene and `takeUntilDestroyed`

A manual `.subscribe()` call creates a subscription that lives until something
explicitly tears it down — if that never happens, it's a memory/resource leak (see
System Design Phase 7).

```ts
import { DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

export class SomeComponent {
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    someStream$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((value) => {
      // automatically unsubscribed when this component is destroyed
    });
  }
}
```

- **Key Concepts**
  - **Prefer the `async` pipe first.** Only reach for a manual `.subscribe()` when you
    need to run imperative logic (not just render a value) in response to an emission.
  - **`takeUntilDestroyed(destroyRef)`**: an operator that automatically completes the
    stream (ending the subscription) when the given `DestroyRef`'s component/directive
    is destroyed — ties a manual subscription's lifetime to the component's, the same
    safety the `async` pipe gives you, for the cases where you genuinely need
    `.subscribe()`.
  - **`DestroyRef`**: an injectable handle representing "this component/directive
    instance's destruction," usable as `takeUntilDestroyed`'s input or with
    `destroyRef.onDestroy(callback)` directly.

**Why it's useful**: leaked subscriptions are one of the most common real-world Angular
bugs — a component keeps polling, logging, or holding references long after it's been
navigated away from. Knowing both the "avoid the problem" pattern (`async` pipe) and
the "fix it when you can't avoid `.subscribe()`" pattern (`takeUntilDestroyed`) covers
the whole topic.

## Signals vs RxJS — when to use each

| Use **Signals** for… | Use **RxJS** for… |
|---|---|
| Synchronous UI state (`isOpen`, form-derived values, a running total) | Asynchronous streams (HTTP responses, WebSocket messages, DOM event streams) |
| Derived values (`computed`) | Time-based logic (`debounceTime`, `throttleTime`, intervals) |
| Simple, subscription-free reads in templates | Complex event orchestration (cancellation via `switchMap`, retry, combining streams) |
| State a component owns and reacts to locally | Data that genuinely arrives *over time*, possibly more than once |

- **Bridging the two**: `toSignal(observable$)` converts a stream into a signal — the
  idiomatic way to consume an HTTP call's result as plain state (`toSignal(this.http.get(...),
  { initialValue: [] })`). `toObservable(signal)` goes the other direction, useful when
  a signal-based value needs to feed into an RxJS operator pipeline (e.g. debounce a
  signal's changes).
  ```ts
  readonly foods = toSignal(this.foodExpenses$, { initialValue: [] as ExpenseView[] });
  readonly foodCount = computed(() => this.foods().length);   // a computed built on a bridged stream
  ```

**Why it's useful**: the direction of travel in modern Angular is *signals for state,
RxJS for events/async* — signals absorbed most of the plain synchronous-state use cases
RxJS used to be reached for (a `BehaviorSubject` just to hold a boolean), while RxJS
remains the right tool for genuine async streams and time-based orchestration. Knowing
when to reach for which — and how to bridge them with `toSignal`/`toObservable` when a
feature needs both — is the core skill this phase is testing.

## Summary — Key takeaways

- **Signals** (`signal`, `computed`, `effect`) are for synchronous state: read by
  calling (`count()`), write with `.set()`/`.update()`, derive with `computed`, react
  with `effect` (never use `effect` to *compute* a value other code depends on).
- Signal-based inputs (`input()`, `model()`, `linkedSignal()`) make component inputs
  first-class participants in the signal graph — no `ngOnChanges` needed to react to
  them.
- **RxJS Observables** model async streams over time; `.pipe(...operators)` transforms
  them without mutation, and nothing runs until `.subscribe()`.
- Know the operator cheat-sheet cold: `map`/`filter`/`tap` for everyday transforms,
  `debounceTime`/`distinctUntilChanged` for input, `switchMap` (cancel-previous) vs
  `mergeMap` (concurrent) vs `concatMap` (sequential) for flattening, `catchError` for
  recovery.
- The `async` pipe is the default way to consume an Observable in a template — auto
  subscribe/unsubscribe, no leaks; `takeUntilDestroyed(DestroyRef)` covers the cases
  where you must `.subscribe()` manually.
- Modern guidance: **signals for state, RxJS for async/events**; bridge with
  `toSignal`/`toObservable` when one feature genuinely needs both.
