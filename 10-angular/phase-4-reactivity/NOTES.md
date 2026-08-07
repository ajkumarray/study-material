<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · dependency injection](../phase-3-dependency-injection/NOTES.md) | [Phase 5 · routing forms ➡](../phase-5-routing-forms/NOTES.md)
<!-- /nav -->

# Phase 4 — Reactivity: Signals & RxJS: Notes

Angular has **two** reactivity systems. **Signals** (newer) for synchronous state and
derived values; **RxJS Observables** (long-standing) for asynchronous streams. Modern
Angular uses signals for state and RxJS for events/async I/O, bridging them where needed.

## 4.1 — Signals

A **signal** is a reactive container for a value that notifies readers when it changes —
Angular's answer to fine-grained reactivity (and conceptually like a React
`useState` + automatic dependency tracking).

```ts
const count = signal(0);        // create
count();                        // read (tracks the reader as a dependency)
count.set(5);                   // replace
count.update(n => n + 1);       // derive from current

const doubled = computed(() => count() * 2);   // DERIVED signal, memoized
effect(() => console.log(count()));            // SIDE EFFECT, re-runs on change
```

- **`computed`** — a read-only derived signal; recomputes lazily only when a dependency
  changes and is cached (the demo's `totalCents`). No manual subscription.
- **`effect`** — runs a side effect whenever its tracked signals change (logging, sync to
  storage). Not for deriving values — use `computed` for that.
- **Fine-grained:** reading a signal in a template subscribes *only that view* to it, so
  a change updates precisely what depends on it — enabling **zoneless** change detection.

## 4.2 — RxJS

**RxJS** models **asynchronous streams** of values over time as **Observables** — the
foundation of Angular's async APIs (HTTP, router events, forms valueChanges).

- An **Observable** is a lazy stream you `subscribe` to; **operators** (`map`, `filter`,
  `switchMap`, `debounceTime`, `delay`, `catchError`) transform it via `.pipe(...)`.
- **`HttpClient`** returns Observables: `http.get<Expense[]>('/api/expenses')`. You
  transform with operators and render with the **`async` pipe** (Phase 2.3), which
  subscribes and unsubscribes for you.
- **Key operators to know:** `map` (transform), `switchMap` (flatten + cancel previous —
  the go-to for typeahead/dependent requests), `debounceTime` (rate-limit input),
  `combineLatest`/`forkJoin` (combine streams), `catchError` (recover).
- **Subscription hygiene:** a manual `.subscribe()` must be cleaned up (leak — System
  Design Phase 7); prefer the `async` pipe or `takeUntilDestroyed()` so you never leak.
- **Worked example:** see [`rxjs-pipe-example.ts`](./rxjs-pipe-example.ts) — a type-checked
  service showing `.pipe()` transforms (`map`/`filter`/`scan`), a `switchMap` search box
  (debounce + distinct + cancel-previous), `catchError` recovery, and consuming the result
  via the `async` pipe / `toSignal()`.

## 4.3 — Signals vs RxJS — when each

| Use **Signals** for… | Use **RxJS** for… |
|---|---|
| synchronous UI state (`count`, `isOpen`, form-derived values) | asynchronous streams (HTTP, WebSocket, DOM event streams) |
| derived values (`computed`) | time-based logic (debounce, throttle, intervals) |
| simple, readable state without subscriptions | complex event orchestration (cancellation, retry, combine) |

- **Bridges:** `toSignal(observable$)` turns a stream into a signal (great for consuming
  an HTTP result as state); `toObservable(signal)` goes the other way. This lets you keep
  async in RxJS and state in signals.
- The direction of travel: **signals for state, RxJS for events/async**. Signals reduce
  the RxJS you need for plain state; RxJS remains essential for real streams.

## Perspective

Two tools, two jobs. Reach for a **signal** when you have a value that changes and views
that depend on it — it's simpler, subscription-free, and fine-grained. Reach for **RxJS**
when you have a *stream* over time — HTTP, events, anything needing cancellation/
debounce/retry — and render it with the `async` pipe. Convert between them with
`toSignal`/`toObservable`. Getting this split right is the core of modern Angular
reactivity.
