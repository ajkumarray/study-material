<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · effects](../phase-3-effects/NOTES.md) | [Phase 5 · patterns ➡](../phase-5-patterns/NOTES.md)
<!-- /nav -->

# Phase 4 — Hooks: Interview Q&A

⭐ = asked constantly.

**Q: What are the Rules of Hooks and why do they exist?** ⭐⭐
Call hooks only at the top level (not in conditions, loops, or nested functions) and only from React function components/custom hooks. React tracks hook state by call order, so conditional/looped calls would misalign state between renders. An ESLint plugin enforces the rules.

**Q: `useMemo` vs `useCallback`?** ⭐⭐
`useMemo` caches a computed value; `useCallback` caches a function's identity (it's `useMemo(() => fn, deps)`). Use `useMemo` for expensive derived values, `useCallback` for callbacks passed to memoized children or used as effect dependencies so their reference stays stable.

**Q: What is `useRef` for?** ⭐
A mutable container (`ref.current`) that persists across renders without triggering a re-render. Two uses: referencing a DOM node (focus, measure, integrate non-React libs) and storing a mutable value (timer id, previous value) that shouldn't cause renders.

**Q: `useState` vs `useReducer`?** ⭐
`useState` for simple, independent state. `useReducer` for complex state with many related transitions or where the next state derives from the current — a `(state, action) => newState` reducer centralizes the logic, is testable, and scales better. It's the Redux pattern locally.

**Q: What is a custom hook and why use one?** ⭐⭐
A function starting with `use` that composes hooks to package reusable stateful logic (e.g., `useExpenses` for list + loading + CRUD). It's React's main way to share logic between components — cleaner than the old HOC/render-props patterns — and keeps components focused on rendering.

**Q: Why must a custom hook's name start with `use`?**
So React's linter can apply the Rules of Hooks to it (it calls other hooks) and so readers know it's stateful/hook-based. It's a convention the tooling relies on.

**Q: Does `useMemo` guarantee the value won't recompute?**
No — it's a performance hint, not a semantic guarantee. React may discard the cache. Don't rely on `useMemo`/`useCallback` for correctness (e.g., don't put required side effects there); use them only to optimize.

**Q: When should you NOT use `useMemo`/`useCallback`?** *nuance*
When the computation is cheap or the component doesn't re-render often — the memoization overhead and added complexity outweigh the benefit. Optimize based on measured problems (React DevTools Profiler), not reflexively.

**Q: What is `useContext` and what does it solve?**
It reads a value from the nearest matching Context Provider, avoiding "prop drilling" (passing props through many intermediate components). Good for app-wide data like theme, current user, or locale. For frequently-changing state shared widely, a state library may be better (Phase 7).
