<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · state events](../phase-2-state-events/NOTES.md) | [Phase 4 · hooks ➡](../phase-4-hooks/NOTES.md)
<!-- /nav -->

# Phase 3 — Effects & Data Fetching: Interview Q&A

⭐ = asked constantly.

**Q: What is `useEffect` for, and why can't you just fetch data directly in the component body?** ⭐⭐
`useEffect` runs a function *after* React commits a render to the DOM, which is the only place side effects (data fetching, subscriptions, timers, manual DOM/third-party library interaction) are allowed to happen. The component's render function itself must stay pure — called with the same props/state, it must return equivalent JSX with no side effects — because React may call it multiple times per logical render (StrictMode double-invokes it in development) and relies on being able to call it whenever it needs to, without that triggering real-world side effects like duplicate network requests. If you fetched directly in the component body, the fetch would fire on literally every render (including ones caused by unrelated state changes elsewhere in the tree, or by StrictMode's extra invocation), not just when you actually want it to — `useEffect` plus a dependency array is what lets you say "only run this after mount" or "only run this when X changes."

**Q: What does the dependency array do, and what happens with each of `[]`, `[a, b]`, and no array at all?** ⭐⭐
It controls when the effect re-runs, by comparing each listed value against its value from the previous render:
- `[]` — the effect runs once, right after the first render (mount), and never again (barring StrictMode's dev-only double mount, see below). Used when the effect sets something up that never needs to change, like `useExpenses`'s initial fetch.
- `[a, b]` — runs after the first render, and again after any render where `a` or `b` differs from last time.
- **omitted** — runs after *every single render*, with no filtering at all. This is almost never what you want; if the effect itself calls a setter unconditionally, this produces an infinite render loop (render → effect runs → setState → re-render → effect runs again → ...).

You must list every value the effect's callback actually reads from component scope — every prop, state variable, or function — or you get a **stale closure bug**: the effect keeps using whatever value that dependency had *when the effect was last created*, not the current one, because the callback is an ordinary JS closure over the variables in scope at that point. The ESLint plugin `react-hooks/exhaustive-deps` flags missing dependencies automatically; silencing that warning by hand almost always just hides a real bug rather than fixing it.

**Q: What is the cleanup function, and when exactly does it run?** ⭐
It's whatever function the effect callback `return`s. It runs in two situations: immediately *before* the effect re-runs (so the previous setup is torn down before the new one begins), and once more when the component **unmounts**. Typical uses: `clearInterval`/`clearTimeout` for a timer the effect started, unsubscribing from an event emitter or WebSocket, and — the pattern used in `useExpenses` — flipping a local `active` flag to `false` so any still-pending promise from this effect run knows not to call a setter once the component is gone:
```jsx
useEffect(() => {
  let active = true;
  loader.list()
    .then((data) => { if (active) { setExpenses(data); setLoading(false); } })
    .catch((e) => { if (active) { setError(e.message); setLoading(false); } });
  return () => { active = false; };
}, [loader]);
```
Skipping cleanup here would mean that if the component unmounts while `loader.list()` is still in flight, the `.then`/`.catch` callback would eventually fire and call `setExpenses`/`setError` on a component that no longer exists — React logs a warning for this, and more importantly it's wasted work and, in more complex effects (real subscriptions, timers), an actual resource leak.

**Q: How do you fetch data in React, following the pattern in this codebase?** ⭐
Call the API from inside `useEffect` (directly, or — better, as done here — inside a custom hook like `useExpenses` that bundles the fetch with its own `loading`/`error`/`expenses` state), track all three of loading, error, and data as explicit state, and render each state distinctly rather than assuming the happy path:
```jsx
if (loading) return <p className="loading">Loading…</p>;
if (error) return <p className="error" role="alert">Failed to load: {error}</p>;
return ( /* real UI */ );
```
Guard against setting state after unmount with a cleanup flag (shown above) or an `AbortController` that cancels the in-flight `fetch` on cleanup. Keep the actual `fetch` calls out of the component/hook entirely — isolate them in a small API module (`expenseApi.js`) whose functions the hook calls, both for separation of concerns and so the network boundary is trivially fakeable in tests. Also remember that `fetch`'s promise does *not* reject on HTTP error statuses like 404/500 — you must check `res.ok` yourself and throw, or a failed request will be silently treated as "successful" with an unparseable or error-shaped body.

**Q: Why does `useEffect` sometimes appear to run twice right after mount in development?** ⭐
`<StrictMode>` (wrapping `<App/>` in `main.jsx`) intentionally double-invokes the mount → effect-cleanup → mount sequence for components in development only, specifically to surface effects with missing or incorrect cleanup. It has zero effect on production builds — the double-invocation behavior is stripped out entirely. If a component's behavior breaks under this double run (a duplicated subscription, a counter that increments twice, a request fired twice with visible side effects), that's a sign the effect isn't properly idempotent or its cleanup function doesn't fully undo what setup did — not a reason to remove `<StrictMode>`. `useExpenses`'s fetch effect is written so double-invocation is harmless: the first run's cleanup sets `active = false` before the second run starts, so even though `loader.list()` is called twice, only the second run's result is ever applied to state.

**Q: What's a stale closure bug — can you show one?**
It's when an effect (or any callback) keeps using a value captured from an earlier render because that value wasn't included in the dependency array, so the effect never got a chance to re-create its closure with the current value:
```jsx
useEffect(() => {
  const id = setInterval(() => {
    setCount(c => c + step);   // closes over `step` from THIS effect run only
  }, 1000);
  return () => clearInterval(id);
}, []);   // missing `step` -- the interval keeps using the FIRST render's step forever
```
If `step` changes from `1` to `5` after mount, the interval keeps adding `1`, because the effect (and its `setInterval` callback closure) was only ever created once, with the `step` value from that first render. The fix is adding `step` to the dependency array — that makes the effect re-run (tearing down the old interval via cleanup, creating a new one) whenever `step` changes, so the new interval closes over the current value.

**Q: Why not fetch directly inside the component body, outside of any hook?**
The component body runs on every render and is required to be pure — no side effects allowed during that call, both because React may call it more than once per logical render (StrictMode) and because "the render function has an observable side effect" breaks the entire mental model of components as pure functions of state. Fetching there would fire the request on *every* render, including ones caused by completely unrelated state changes, producing far more requests than intended and no reliable way to know when the request should actually happen. `useEffect` exists precisely to move side effects to a well-defined point (after commit) with explicit control (the dependency array) over when they re-run.

**Q: When would you reach for a data-fetching library (React Query/SWR) instead of hand-rolled `useEffect` fetching?** ⭐
Almost always, once an app is beyond trivial — `useExpenses`'s hand-rolled pattern (three `useState` calls plus a `useEffect`) is the right thing to understand deeply, but it lacks caching (every mount refetches from scratch), request deduplication (two components fetching the same data in the same tick both hit the network), automatic background refetching, and retry/backoff — all things React Query/SWR provide out of the box behind a single hook call. Use hand-rolled `useEffect` fetching for small apps, learning, or genuinely one-off fetches; reach for a library as soon as the same server data needs to be shown/refreshed in more than one place, or staleness/caching starts to matter.

*Follow-up: is `useEffect` fetching "wrong," then?* No — it's the low-level primitive these libraries are themselves built on internally. Understanding it is what lets you reason about what a library like React Query is actually doing under the hood (and debug it when its caching behavior seems surprising), even if you rarely hand-write the raw version in a production app.
