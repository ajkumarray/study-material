<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · dependency injection](../phase-3-dependency-injection/NOTES.md) | [Phase 5 · routing forms ➡](../phase-5-routing-forms/NOTES.md)
<!-- /nav -->

# Phase 4 — Reactivity: Signals & RxJS: Interview Q&A

⭐ = asked constantly.

**Q: What is a signal?** ⭐⭐
A reactive value container: you read it by calling it (`count()`), which tracks the
reader as a dependency, and write with `set`/`update`, which notifies dependents.
Templates and `computed`/`effect` that read it update automatically. It gives Angular
fine-grained, subscription-free reactivity for synchronous state.

**Q: `computed` vs `effect`?** ⭐
`computed` produces a derived, memoized, read-only signal — use it to calculate a value
from other signals (recomputes lazily on change). `effect` runs a side effect when its
tracked signals change (logging, persistence, DOM sync) and returns nothing. Derive with
`computed`; act with `effect`.

**Q: What is an Observable?** ⭐⭐
A lazy stream of values over time that you subscribe to, with operators (`.pipe(map,
filter, switchMap, …)`) to transform it. It's RxJS's core abstraction and underlies
Angular's async APIs — `HttpClient`, router events, form `valueChanges`.

**Q: What does the `async` pipe do?** ⭐⭐
Subscribes to an Observable/Promise in the template, renders the latest value, and
unsubscribes automatically on destroy — avoiding memory leaks and manual subscription
management. It's the preferred way to render async data.

**Q: `switchMap` vs `mergeMap`?** *nuance*
Both flatten an Observable-of-Observables. `switchMap` cancels the previous inner
subscription when a new value arrives — ideal for typeahead/search and dependent
requests where only the latest matters. `mergeMap` keeps all inner subscriptions
concurrent — use when every emission must complete (e.g. parallel writes).

**Q: When do you use signals vs RxJS?** ⭐⭐
Signals for synchronous state and derived values (simpler, no subscriptions, fine-
grained updates). RxJS for asynchronous streams and time-based/orchestration logic
(HTTP, events, debounce, cancellation, combining). Modern guidance: signals for state,
RxJS for events/async.

**Q: How do you bridge signals and Observables?**
`toSignal(obs$)` converts a stream into a signal (consume an HTTP result as state);
`toObservable(sig)` converts a signal into a stream. This lets you fetch with RxJS and
hold the result as signal state, using each tool for its strength.

**Q: How do you avoid memory leaks with RxJS?** ⭐
Don't leave manual `.subscribe()` calls uncleaned. Prefer the `async` pipe (auto-
unsubscribes) or `takeUntilDestroyed()` to tie a subscription to the component lifecycle.
Leaked subscriptions keep components/handlers alive (System Design Phase 7 — resource
leaks).

**Q: How do signals affect change detection?**
They enable fine-grained updates: only views that read a changed signal re-render, rather
than checking the whole component tree. This is what allows Angular's move to zoneless
change detection (no Zone.js), improving performance and predictability.
