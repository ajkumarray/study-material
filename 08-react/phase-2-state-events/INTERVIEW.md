<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · effects ➡](../phase-3-effects/NOTES.md)
<!-- /nav -->

# Phase 2 — State & Events: Interview Q&A

⭐ = asked constantly.

**Q: What is `useState` and how does the render cycle work?** ⭐⭐
`useState` gives a function component state that persists across renders, returning `[value, setter]`. Calling the setter schedules a re-render with the new value. The cycle: render from state → user event → setState → re-render. The setter is the only way to trigger an update.

**Q: Why can't you mutate state directly?** ⭐⭐
React decides what to re-render by comparing references. Mutating (`arr.push`, `obj.x = 1`) keeps the same reference, so React doesn't detect a change and won't re-render — and it corrupts the source of truth. Always create a new value (spread, map, filter) and pass it to the setter.

**Q: Is `setState` synchronous?** ⭐
No — updates are asynchronous and batched. After calling the setter, the state variable still holds the old value in the same function scope; the new value is available on the next render. Use the functional updater (`setX(prev => ...)`) when the next value depends on the previous.

**Q: What is a controlled component?** ⭐⭐
A form input whose value is driven by React state (`value={x}` + `onChange` updating `x`), so React is the single source of truth. Enables easy validation, transformation, and reset. Uncontrolled components read values from the DOM via a ref instead.

**Q: Why does each list item need a key, and why not the index?** ⭐⭐
Keys let React match items across re-renders to reuse DOM and preserve state. The array index changes when items are inserted/removed/reordered, causing React to associate the wrong DOM/state with an item (visual bugs, wrong input values). Use a stable unique id.

**Q: How do you handle events in React?**
With camelCase props taking functions (`onClick={handler}`); React normalizes them as SyntheticEvents for cross-browser consistency. Use `e.preventDefault()`/`e.stopPropagation()` as needed; pass arguments via an arrow/closure.

**Q: How do you share state between two sibling components?**
Lift the state up to their common parent and pass it down as props (and callbacks to update it) — Phase 5. Siblings don't talk directly; the parent coordinates.

**Q: Should you store computed/derived values in state?** ⭐
No — compute them during render from existing state/props (optionally memoized with `useMemo`). Storing derived data creates a second source of truth that can drift out of sync and requires manual updating.

**Q: What are the ways to conditionally render?**
Early `return`, the `&&` short-circuit (`{cond && <X/>}`), and the ternary (`{cond ? <A/> : <B/>}`). For many branches, compute the element in a variable or use a lookup map.
