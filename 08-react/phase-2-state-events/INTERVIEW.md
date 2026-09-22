<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · effects ➡](../phase-3-effects/NOTES.md)
<!-- /nav -->

# Phase 2 — State & Events: Interview Q&A

⭐ = asked constantly.

**Q: What is `useState` and how does the render cycle work?** ⭐⭐
`useState(initialValue)` gives a function component memory that survives across re-renders, returning a pair: the current value and a setter function. `initialValue` is only used on the component's very first render — after that, React tracks the state internally per-hook-call and `useState` just returns whatever the current value is, ignoring the literal argument in the source. Calling the setter is the *only* thing that schedules a re-render; the render cycle is: render from current state → user event fires → the handler calls the setter → React re-renders the component (re-running its function body) → the new JSX is diffed against the previous virtual tree and the minimal DOM changes are applied.

**Q: Why can't you mutate state directly — walk through what actually goes wrong.** ⭐⭐
```jsx
// WRONG:
expenses.push(newExpense);
// state variable `expenses` still refers to the SAME array object; nothing re-renders

// RIGHT:
setExpenses(prev => [newExpense, ...prev]);
```
React (in the default case, without a custom `Object.is` override) determines whether a re-render is needed by comparing the *reference* of the new state value to the old one, not by deep-comparing contents. `array.push(x)` mutates the array in place and returns the new length — the variable still points at the exact same array object it did before, so React's reference check sees "the same value" and skips re-rendering entirely, even though the underlying data changed. Worse, because the mutation happened outside of `setState`, React's internal bookkeeping for that state slot is now silently out of sync with the array's actual contents in some cases. The fix is always to build a *new* array or object (spread, `.map()`, `.filter()`, `.concat()`) and pass that new reference to the setter — this is the same "treat data as immutable" discipline as JS Phase 1, now load-bearing for correctness, not just style.

**Q: Is `setState` synchronous? What would this log, and why?** ⭐
```jsx
function handleClick() {
  console.log(count);        // 0
  setCount(count + 1);
  console.log(count);        // 0 -- NOT 1
}
```
No — state updates are asynchronous and (in React 18+) automatically batched. Calling the setter *schedules* an update; it does not immediately mutate the `count` variable in the current function's scope. The second `console.log(count)` still reads `count` from the same closure captured when `handleClick` started running, which is still `0` — the new value (`1`) will only be visible the *next* time the component function itself runs (the next render). This trips people up because it looks like a plain variable assignment but behaves more like "I've told React what I want next, and it'll get there." The practical consequence: if you need the *next* value to compute a further update in the same handler, use the functional updater form (`setCount(c => c + 1)`), which reads React's actual latest queued value instead of the stale closure variable.

*Follow-up: what if you call `setCount(count + 1)` three times in a row in the same handler — does count go up by 3?* No, it goes up by 1. All three calls read the same stale `count` from the closure and compute the same "old value + 1," so they collapse to one net update. `setCount(c => c + 1)` called three times *would* correctly increment by 3, because each functional update receives the result of the previous one.

**Q: What is a controlled component, and what happens if you set `value` but forget `onChange`?** ⭐⭐
A controlled component is a form input whose `value` is driven by React state and whose changes are pushed back into that state via `onChange` — React is the single source of truth for the field's contents (`ExpenseForm`'s inputs: `value={description} onChange={e => setDescription(e.target.value)}`). If you set `value` from state without an `onChange` handler, the input becomes effectively read-only: the user can type, the DOM briefly shows the keystroke, but on the very next render React resets the `value` attribute back to the (unchanged) state — so the field appears frozen. This is one of the most common controlled-component bugs and usually shows up as "my input won't let me type."

*Follow-up: what's the alternative, and when would you use it?* An uncontrolled input, which manages its own value in the DOM and is read only when needed via a `ref` (Phase 4) — e.g., `inputRef.current.value` read once on submit. It avoids a re-render on every keystroke and is simpler for "just grab it on submit" cases or things that must be uncontrolled (like `<input type="file">`), at the cost of React no longer being the authoritative source of the value, which makes live validation/formatting harder.

**Q: Why does each list item need a `key`, and specifically why not the array index?** ⭐⭐
Keys give React a stable identity to match list items across re-renders during reconciliation, so it can correctly determine which items were added, removed, reordered, or unchanged — and reuse the right DOM node (and any state attached to it) for the right logical item. The array index is a *position*, not an identity: if you delete the first of three items, the remaining two shift from indices `1, 2` down to `0, 1`. If the key was the index, React thinks "index 0 still exists" and reuses that DOM node/state for what is now a *different* underlying item — this can manifest as a deleted-looking row actually staying on screen with stale data, an input keeping focus/text that belongs to the wrong item, or a checkbox's checked state jumping to the wrong row. Using a stable id from the data (`key={e.id}`, as `ExpenseList` does) avoids all of this because the key always refers to the same logical item no matter where it sits in the array.

**Q: How do you handle events in React, and what's the difference between `onClick={fn}` and `onClick={() => fn(x)}`?**
Event props are camelCase and take a function reference (`onClick`, `onChange`, `onSubmit`); React wraps the native event in a cross-browser `SyntheticEvent`. `onClick={fn}` registers `fn` to be called directly by React when the click fires, with the event object as its argument. `onClick={() => fn(x)}` instead registers a brand-new arrow function that, *when later invoked by the click*, calls `fn(x)` — this is the pattern you need whenever the handler needs an argument beyond the event itself, like `ExpenseItem`'s `onClick={() => onDelete(id)}`, which passes the specific item's `id` closed over from that render, not the raw click event. A very common bug is writing `onClick={fn(x)}` (no arrow) — that *calls `fn(x)` immediately during render* and passes its return value as the handler, which is almost never what you want.

**Q: Should you store computed/derived values in state?** ⭐
No — compute them during render from existing state/props instead, optionally wrapped in `useMemo` if the computation is expensive (Phase 4). `Summary`'s `total` and `byCategory` are derived entirely from the `expenses` array; storing them as their *own* `useState` would create a second source of truth that has to be manually kept in sync on every add/remove/edit, and any place that update is forgotten becomes a silent bug where the displayed total drifts from reality. Computing during render guarantees the derived value can never be stale, because it's recalculated fresh from the current state every time the component renders.

**Q: What are the ways to conditionally render in JSX, and what's a common pitfall with `&&`?**
Early `return` for "render an entirely different tree" (the loading/error/data pattern in `App`), the `&&` short-circuit for "render this or nothing" (`{error && <p>{error}</p>}`), and the ternary for "exactly one of two things" (`{isOpen ? <A/> : <B/>}`). For many branches, compute the JSX into a variable above the `return` rather than nesting ternaries, which get unreadable fast.

The `&&` pitfall: `{cond && <X/>}` renders whatever `cond` evaluates to if it's falsy — and while `false`, `null`, and `undefined` all render as nothing, `0` does *not* (it's a valid, renderable child, so React prints the literal `0`). `{count && <Badge/>}` will show a stray `0` on screen when `count` is `0`. The fix is an explicit boolean: `{count > 0 && <Badge/>}`.

**Q: How do you share state between two sibling components?**
Lift the state up to their closest common parent and pass it down as props, with callback props for the children to request changes — siblings never talk to each other directly. In `expense-web`, `ExpenseForm` and `ExpenseList` are siblings that both need the expense list; neither holds it — it lives in `App` (via `useExpenses`), which passes the data and callbacks down to both. This is examined in depth as "lifting state up" in Phase 5.

**Q: In React 18, does automatic batching change anything about the "stale closure" behavior of `setState`?**
Not the closure behavior itself (state is still only updated on the next render either way), but it does change *when* multiple updates in the same tick get coalesced into one re-render. Before React 18, updates inside plain promises/`setTimeout`/native event listeners were *not* batched — each `setState` call there triggered its own separate re-render. React 18's automatic batching extends batching to those cases too, so multiple `setState` calls anywhere in the same synchronous block of work typically produce a single re-render, which is a performance improvement but doesn't change the "the variable in this closure is still old" behavior developers need to reason about.
