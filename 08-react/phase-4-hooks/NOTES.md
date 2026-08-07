<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · effects](../phase-3-effects/NOTES.md) | [Phase 5 · patterns ➡](../phase-5-patterns/NOTES.md)
<!-- /nav -->

# Phase 4 — Hooks Deep Dive: Notes

**Hooks** are functions (prefixed `use`) that let function components use React features. **Rules of Hooks:** call them only at the **top level** (never in loops/conditions/nested functions) and only from React functions — so React can associate each hook call with the right state by call order.

## The hooks
- **`useState`** — local state (Phase 2).
- **`useEffect`** — side effects (Phase 3).
- **`useMemo(fn, deps)`** — cache an expensive *computed value*; recompute only when `deps` change. `Summary` uses it for the total/breakdown so the reduce doesn't re-run on every unrelated render. Don't overuse — for cheap computations the memo overhead isn't worth it.
- **`useCallback(fn, deps)`** — cache a *function's identity* across renders (it's `useMemo` for functions). Matters when passing callbacks to memoized children (`React.memo`) or as effect deps, so their identity doesn't change every render (`useExpenses` memoizes `add`/`remove`).
- **`useRef(initial)`** — a mutable box (`ref.current`) that **persists across renders without causing a re-render**. Two uses: (1) access a DOM node (`<input ref={inputRef}>` then `inputRef.current.focus()`); (2) hold a mutable value (timer id, previous value) that shouldn't trigger renders.
- **`useReducer(reducer, initial)`** — state via a reducer `(state, action) => newState` (the Redux pattern). Better than `useState` when state is complex or the next state depends on the current one in many ways; centralizes update logic and makes transitions testable.
- **`useContext(Context)`** — read a value from the nearest Context provider without prop-drilling (Phase 5/7).

## Custom hooks — the big idea
A **custom hook** is a function starting with `use` that composes other hooks to package **reusable stateful logic**. `useExpenses` bundles the list state, loading/error, and add/remove — so `App` stays a thin renderer. Custom hooks are React's primary code-reuse mechanism (they replaced class-era mixins/HOCs/render-props for logic sharing). They can be tested in isolation and shared across components. Injecting the API `loader` into `useExpenses` makes it testable with a fake (Dependency Inversion, Software Design Phase 2).

## When to reach for what
- Derived value that's expensive → `useMemo`. Callback passed to a memoized child → `useCallback`. DOM access or non-render mutable value → `useRef`. Complex/multi-action state → `useReducer`. Cross-cutting shared data → Context. Reusable logic across components → a custom hook.
- **Don't prematurely memoize.** `useMemo`/`useCallback` add complexity and their own cost; add them to fix a measured performance problem, not by default (the YAGNI of React).
