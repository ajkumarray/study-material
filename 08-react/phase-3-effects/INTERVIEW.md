<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · state events](../phase-2-state-events/NOTES.md) | [Phase 4 · hooks ➡](../phase-4-hooks/NOTES.md)
<!-- /nav -->

# Phase 3 — Effects & Data Fetching: Interview Q&A

⭐ = asked constantly.

**Q: What is `useEffect` for?** ⭐⭐
Running side effects after render — data fetching, subscriptions, timers, manual DOM interactions — since rendering itself must be pure. It keeps side effects out of the render path and lets you synchronize a component with external systems.

**Q: What does the dependency array do?** ⭐⭐
Controls when the effect re-runs: `[]` runs once on mount; `[a, b]` runs on mount and whenever `a` or `b` change; no array runs after every render. You must list every reactive value the effect uses, or you get stale closures (bugs from capturing old values).

**Q: What is the cleanup function and when does it run?** ⭐
The function returned from an effect. It runs before the effect re-executes and on unmount — to unsubscribe, clear timers, or cancel/ignore in-flight requests. Missing cleanup causes memory leaks and updates on unmounted components.

**Q: How do you fetch data in React?** ⭐
Call the API in a `useEffect` (or a custom hook), track loading/error/data state, and render each state. Guard against setting state after unmount (a cleanup flag or AbortController). In production, prefer React Query/SWR, which handle caching and refetching. Check `res.ok` since `fetch` doesn't reject on HTTP errors.

**Q: Why does `useEffect` sometimes run twice in development?** ⭐
`<StrictMode>` double-invokes effects (mount, unmount, mount) in dev to expose missing cleanup and impure effects. It's dev-only. If a double run breaks something, the effect lacks proper cleanup/idempotency.

**Q: What's a stale closure bug?**
An effect (or callback) captures a value from an old render because it wasn't in the dependency array, so it uses outdated state/props. Fix by adding the dependency (and using functional updaters or refs where a stable identity is needed).

**Q: Why not fetch directly inside the component body?**
The component body runs on every render and must be pure; fetching there would fire on every render and cause side effects during render. Fetching belongs in `useEffect` (post-render) or a data-fetching library.

**Q: When would you use a data-fetching library over `useEffect`?** 
Almost always in production — React Query/SWR provide caching, request deduplication, background refetching, retries, and loading/error handling out of the box, eliminating boilerplate and subtle bugs. `useEffect` fetching is the low-level primitive to understand, not the thing to hand-roll everywhere.
