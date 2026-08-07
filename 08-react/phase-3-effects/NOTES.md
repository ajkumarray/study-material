<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · state events](../phase-2-state-events/NOTES.md) | [Phase 4 · hooks ➡](../phase-4-hooks/NOTES.md)
<!-- /nav -->

# Phase 3 — Effects & Data Fetching: Notes

## useEffect
Rendering must be **pure** (no side effects during render). **`useEffect`** is where side effects go — data fetching, subscriptions, timers, manual DOM work — running *after* the render commits.

```
useEffect(() => { /* effect */ return () => { /* cleanup */ }; }, [deps]);
```

- **Dependency array** controls when it re-runs: `[]` = once on mount (like a constructor); `[x]` = on mount and whenever `x` changes; omitted = after *every* render (rarely what you want). List every value from the component scope the effect uses, or you get stale-closure bugs (the ESLint `react-hooks/exhaustive-deps` rule enforces this).
- **Cleanup function** (the returned function) runs before the effect re-runs and on unmount — unsubscribe, clear timers, cancel/ignore in-flight requests. Skipping cleanup causes leaks and "setState on unmounted component" warnings.

## Data fetching pattern
`useExpenses` fetches on mount: call the API, then set data/loading/error state. Handle the three states explicitly (**loading → error → data**) so the UI reflects reality (`App` renders a spinner, an error, or the list). The **cleanup guard** (`let active = true; ... return () => active = false`) prevents setting state after the component unmounts mid-request (the React equivalent of the leak from System Design Phase 7).

**Fetch reminders (JS Phase 7.3):** `fetch` doesn't reject on 4xx/5xx — check `res.ok` and throw (the API layer does this). Keep `fetch` out of components — a dedicated `api` module (or a hook) isolates it, mirroring the backend's layering.

## StrictMode
In development, `<StrictMode>` intentionally **double-invokes** effects (mount → unmount → mount) to surface missing cleanup and impure effects. It's dev-only (no effect in production) — if your effect breaks under double-invocation, it has a cleanup bug worth fixing.

## Beyond hand-rolled fetching
Manual `useEffect` fetching is fine to *understand*, but production apps use a **data-fetching library** — **React Query (TanStack Query)** or **SWR** — which handle caching, deduplication, refetching, loading/error states, and stale-while-revalidate for you (the caching ideas from System Design Phase 2, on the client). Know that `useEffect` fetching is the primitive these build on. Server Components / frameworks (Next.js, track 09) move much fetching to the server entirely.
