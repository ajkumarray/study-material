<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · state events](../phase-2-state-events/NOTES.md) | [Phase 4 · hooks ➡](../phase-4-hooks/NOTES.md)
<!-- /nav -->

# Phase 3 — Effects & Data Fetching: Notes

## useEffect

Rendering a component must be **pure** — no network calls, timers, subscriptions, or manual DOM mutation directly in the render body (Phase 1). But real apps need exactly those things: fetching data on mount, subscribing to an external event source, setting up a timer. **`useEffect`** is the Hook for these **side effects**: the function you pass to it runs *after* React has committed the render to the DOM, not during rendering itself.

```jsx
useEffect(() => {
  // the effect: runs after the DOM is updated
  return () => {
    // the cleanup: runs before the effect re-runs, and on unmount
  };
}, [deps]);
```

- **The dependency array controls when the effect re-runs:**
  - `[]` (empty array) — runs exactly **once**, after the first render (mount). Nothing in the effect depends on props/state that change, so it never needs to re-run. Analogous to a constructor in class-based languages, but running after the initial paint rather than before.
  - `[a, b]` — runs after the first render, and again after any render where `a` or `b` is different from its previous value (compared with `Object.is`, essentially `===`).
  - **omitted entirely (no array)** — runs after *every single render*, with no filtering. This is rarely what you want and is easy to do by accident (just forgetting the array), often causing infinite loops if the effect itself calls a setter.
- **List every reactive value the effect's function body reads** — every prop, state value, or function from component scope it uses. Leaving one out is a **stale closure** bug: the effect keeps using the value from whichever render it was *created* in, not the latest one, because the function closes over the variables from that render. The ESLint rule `react-hooks/exhaustive-deps` catches this automatically and is the reason you should almost never manually shrink a dependency array to "make a warning go away" — the warning is telling you about a real bug.
- **The cleanup function** (whatever the effect callback `return`s) runs in two situations: right *before* the effect re-runs (to undo whatever the previous run set up, before setting the new one up), and once more on **unmount**. This is where you unsubscribe from an event source, clear a timer (`clearInterval`), or, as shown below, guard against setting state after the component is gone.

```jsx
// From useExpenses.js — mount-only fetch with a cleanup guard
useEffect(() => {
  let active = true;                       // guard flag, local to THIS effect run
  loader
    .list()
    .then((data) => { if (active) { setExpenses(data); setLoading(false); } })
    .catch((e) => { if (active) { setError(e.message); setLoading(false); } });
  return () => { active = false; };        // cleanup: runs on unmount
}, [loader]);
```
In this example, the effect fires once after `useExpenses` (and whatever component calls it) mounts, kicking off `loader.list()`. If the component unmounts *before* the request resolves (e.g., the user navigates away quickly), the cleanup function has already flipped `active` to `false` by the time `.then`/`.catch` runs, so the `setExpenses`/`setError`/`setLoading` calls are skipped — calling a setter on an unmounted component would otherwise produce a React warning and is a real (if usually harmless-looking) memory-leak pattern, because the closure holding the setter keeps the old component instance's update machinery alive longer than it should be. The dependency array is `[loader]`, not `[]`, because the effect's body *reads* `loader` — even though in practice `loader` rarely changes (it defaults to `expenseApi` and is passed the same reference on every render unless the caller passes something new), the linter (and correctness in general) requires it to be listed since the effect closes over it.

```jsx
// A stale-closure bug, illustrated
function Counter({ step }) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    const id = setInterval(() => {
      setCount(c => c + step);   // reads `step` from the render this effect was created in
    }, 1000);
    return () => clearInterval(id);
  }, []);   // BUG: missing `step` in deps

  // If `step` prop changes from 1 to 5 after mount, the interval keeps adding 1,
  // not 5 -- because the effect never re-ran, so its closure still has the OLD
  // `step` value from the very first render. Fixing it means adding `step` to
  // the dependency array, which correctly tears down the old interval and
  // starts a new one that closes over the current `step`.
}
```
This shows the mechanism, not just the symptom: an effect's callback is an ordinary JS closure, captured at the moment the effect runs. If a value the closure depends on changes but isn't in the dependency array, the effect never gets a chance to re-create that closure with the new value — it just keeps using the old one until (if ever) the component unmounts.

**Why it's useful:** `useEffect` is the single place side effects are allowed to happen, keeping render logic pure and predictable while still giving components a way to synchronize with anything outside React's own state — a REST API, `localStorage`, `window` event listeners, timers, WebSocket connections, or a third-party non-React library.

**Summary / key takeaways:**
- `useEffect` runs after render commits; use it for anything that isn't pure UI computation.
- `[]` = once on mount; `[deps]` = on mount and whenever a listed dependency changes; omitted = every render (usually a mistake).
- List every value the effect reads, or risk a stale-closure bug; let `react-hooks/exhaustive-deps` catch it for you.
- The returned cleanup function runs before the next effect run and on unmount — use it to cancel/unsubscribe/guard against post-unmount state updates.

## Data fetching pattern

The standard shape for fetching data in a component (or a custom hook wrapping one, Phase 4) is to explicitly track and render three states: **loading**, **error**, and **data** — because a network request genuinely has three outcomes, and the UI should reflect whichever one is currently true rather than assuming the happy path.

```jsx
// From useExpenses.js
const [expenses, setExpenses] = useState([]);
const [loading, setLoading] = useState(true);
const [error, setError] = useState(null);

useEffect(() => {
  let active = true;
  loader.list()
    .then((data) => { if (active) { setExpenses(data); setLoading(false); } })
    .catch((e) => { if (active) { setError(e.message); setLoading(false); } });
  return () => { active = false; };
}, [loader]);
```

```jsx
// From App.jsx — rendering each of the three states explicitly
export function App({ loader }) {
  const { expenses, loading, error, add, remove } = useExpenses(loader);

  if (loading) return <p className="loading">Loading…</p>;
  if (error) return <p className="error" role="alert">Failed to load: {error}</p>;

  return ( /* the real UI, now that data is here and there's no error */ );
}
```
In this example, `App` never tries to render the expense list while `loading` is `true` or `error` is set — the early returns (Phase 2's conditional-rendering pattern) guarantee exactly one of the three states is shown at a time. `loading` starts `true` (there's always a request in flight on mount) and only flips to `false` inside the `.then`/`.catch`, so the spinner is visible for exactly as long as the request is actually pending.

- **The cleanup guard (`active`) prevents the classic "setState on unmounted component" leak.** This is the client-side version of the resource-leak concern from System Design Phase 7 — a component that's gone shouldn't still be doing work (or, worse, silently succeed at mutating state nobody will ever read, wasting the update cycle and, in tests, causing "an update was not wrapped in act(...)" warnings after a test finishes but a fetch is still in flight).
- **`fetch` does not reject on HTTP error statuses (4xx/5xx)** — a `404` or `500` response resolves successfully as far as `fetch`'s own promise is concerned; you must check `res.ok` yourself and throw to convert it into a proper rejected promise (JS Phase 7.3 covers this at the `fetch` level):
```js
// From expenseApi.js
async function handle(res) {
  if (!res.ok) throw new Error(`HTTP ${res.status}`);   // fetch doesn't reject on 4xx/5xx
  return res.status === 204 ? null : res.json();
}
```
- **Keep `fetch` out of components.** `expenseApi.js` is a dedicated module that owns every network call (`list`, `create`, `remove`); components and hooks call these functions and never touch `fetch` directly. This mirrors backend layering (a service layer that doesn't know about HTTP directly) and, critically, makes the network boundary trivial to fake in tests — `useExpenses(loader)` accepts *any* object with `list`/`create`/`remove` methods, so `App.test.jsx` passes a fully in-memory fake instead of hitting a real server.

**Why it's useful:** Explicitly modeling loading/error/data avoids the two most common data-fetching bugs: showing stale/empty data while a request is still pending (because you forgot a loading flag), and silently swallowing failures so the UI just looks broken with no explanation (because you forgot to handle rejection). Isolating the actual `fetch` calls behind a small API module keeps components framework-agnostic about *how* data arrives and makes both unit testing and swapping the transport (e.g., to GraphQL, or adding auth headers) a one-file change.

**Summary / key takeaways:**
- Model loading, error, and data as three explicit states and render each with an early return — never assume the happy path.
- Use a cleanup guard (or `AbortController`, see below) to avoid setting state after unmount.
- `fetch` resolves even on HTTP error responses — always check `res.ok` and throw.
- Isolate network calls in a dedicated API module/object so components stay pure renderers and the network boundary is easy to mock in tests.

## StrictMode

`<StrictMode>` (wrapping `<App />` in `main.jsx`) is a development-only, no-UI wrapper component that opts a subtree into extra checks intended to surface bugs early. One of its most visible effects: in development, React intentionally **renders components twice** and, for components with effects, runs the mount → cleanup → mount sequence twice (`useEffect`'s setup, then its cleanup, then setup again) on the very first mount.

```jsx
// From main.jsx
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>
);
```

- **It has zero effect in production builds** — `StrictMode`'s double-invocation behavior is stripped out of `npm run build` output entirely; it exists purely to catch bugs *during development* that would otherwise only surface intermittently or in production under specific timing.
- **What it's protecting you from**: effects that aren't properly idempotent/cleaned-up. If your effect sets up a subscription/timer/fetch and its cleanup function doesn't correctly undo that setup, running mount→cleanup→mount exposes the bug immediately (e.g., a duplicated subscription, a request fired twice with no cancellation) instead of it lurking until a specific production scenario (like fast route navigation) triggers it.
- **`useExpenses`'s effect is written to survive double-invocation correctly**: the first mount sets `active = true` and starts the fetch; the (StrictMode-only) cleanup immediately sets `active = false`; the second mount starts a *new* effect run with its own fresh `active = true` and a new fetch. The first fetch's `.then`/`.catch` callbacks, if they resolve later, see `active === false` (from the first run's own closure) and skip updating state — so even though `loader.list()` technically gets called twice in development, only the second run's result is ever applied, and no error or leak occurs. That's precisely the property StrictMode is checking for.
- **If double-invocation breaks something, the effect had a real bug** — not a StrictMode quirk to "work around" by removing `<StrictMode>`. Common breakage: an effect without a cleanup function that subscribes to something (now subscribed twice), or a timer never cleared (now two timers running).

**Why it's useful:** Bugs from missing/incorrect cleanup are notoriously intermittent in production — they depend on exact timing of unmounts, fast navigation, or race conditions that might not show up in casual manual testing. StrictMode forces the "does this survive teardown-and-setup happening back to back" scenario to happen on literally every development render, turning an intermittent bug into a reliably reproducible one you'll notice immediately.

**Summary / key takeaways:**
- StrictMode double-invokes render and effect setup/cleanup in development only — it's stripped from production builds entirely.
- Its purpose is to surface missing/incorrect effect cleanup and impure renders while you're still developing, not to change runtime behavior.
- A correctly-written effect (proper cleanup, idempotent setup) is unaffected in observable behavior by double-invocation; if something breaks under it, fix the effect, don't remove StrictMode.

## Beyond hand-rolled fetching

Manually managing loading/error/data state and writing the fetch inside `useEffect` (as `useExpenses` does) is worth understanding deeply because it's the *primitive* everything else builds on — but production codebases very rarely hand-roll this for every piece of server data, because the same handful of concerns (caching, avoiding duplicate in-flight requests for the same data, refetching on reconnect/focus, retries, pagination) show up in every app and are easy to get subtly wrong by hand.

- **React Query (TanStack Query) / SWR** — dedicated data-fetching libraries that replace the `useState`×3 + `useEffect` pattern with a single hook call (e.g., `useQuery(['expenses'], () => api.list())`), and additionally provide: caching by key (so navigating away and back doesn't necessarily refetch), request deduplication (two components asking for the same data in the same tick share one network call), automatic background refetching (on window refocus, network reconnect, or a polling interval), retry-with-backoff on failure, and built-in loading/error/data states equivalent to what `useExpenses` tracks manually.
- **What you get for free that `useExpenses` doesn't have**: no caching (every mount refetches from scratch), no deduplication, no automatic retry, and the "avoid setState after unmount" guard has to be hand-written (as it is here) instead of built in.
- **`useEffect` fetching is still the right thing to *understand*** even when you'll use a library day-to-day — the library is doing exactly this pattern internally, just generalized and hardened; understanding the manual version is what lets you reason about what the library is actually doing for you (and debug it when something looks wrong).
- **Frameworks push further still.** Next.js (track 09) with Server Components can fetch data *on the server*, during rendering, before any HTML reaches the browser at all — eliminating the client-side loading state for that data entirely for the first paint. React Router's data APIs (Phase 6) sit in between: still client-side, but fetching is driven by the router before the component renders, rather than by the component itself after it renders.

**Why it's useful:** Recognizing when hand-rolled `useEffect` fetching is "good enough" (small apps, one-off fetches, learning) versus when the missing caching/dedup/retry behavior will actually bite you (any app with more than a couple of data-dependent views, or where the same data is needed in multiple places) is a real architectural decision, not just a style preference.

**Summary / key takeaways:**
- `useEffect`-based fetching (as in `useExpenses`) is the foundational pattern; libraries like React Query/SWR build on exactly this idea with caching, dedup, and retry added.
- Prefer a data-fetching library for production apps with more than trivial data needs — reserve hand-rolled `useEffect` fetching for simple cases or for understanding the mechanism.
- Frameworks (Next.js) can move fetching to the server entirely, removing the client-side loading state for initial data.
