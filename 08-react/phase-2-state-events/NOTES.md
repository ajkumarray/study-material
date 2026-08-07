<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · effects ➡](../phase-3-effects/NOTES.md)
<!-- /nav -->

# Phase 2 — State & Events: Notes

## useState & the render loop
**State** is data a component remembers across renders. `const [value, setValue] = useState(initial)` returns the current value and a setter. The fundamental React loop: **state → UI**; a user **event → setState → re-render** with the new state. Calling the setter is what tells React to re-render — mutating a variable directly does nothing.

**Rules:**
- **Never mutate state directly** — always call the setter with a *new* value (`setExpenses([created, ...prev])`, not `expenses.push(...)`). React detects changes by reference (immutability, JS Phase 1); mutating won't trigger a re-render and corrupts the model.
- **State updates are asynchronous & batched** — after `setX(...)`, `x` in the same function still holds the old value; the new value appears on the next render.
- **Use the functional updater when deriving from previous state** — `setCount(c => c + 1)` / `setExpenses(prev => prev.filter(...))` — safe across batching and closures.
- **Don't store derived data in state** — compute it during render (the total/breakdown in `Summary`, Phase 4). Redundant state drifts out of sync.

## Events
React wraps DOM events in a cross-browser **SyntheticEvent**. Handlers are camelCase and take a *function*: `onClick={() => onDelete(id)}` (the arrow defers the call). `event.preventDefault()` stops default browser behavior (form submit reloading the page). Pass data to a handler via a closure/arrow.

## Controlled inputs & forms
A **controlled input** has its `value` driven by state and updates state on `onChange` — React is the single source of truth for the field (`ExpenseForm`). This makes validation, formatting, and reset trivial (just set state). The pattern:
```
value={description} onChange={e => setDescription(e.target.value)}
```
On submit: `preventDefault()`, validate, call the parent's `onAdd` callback, then reset the fields. (Uncontrolled inputs using a `ref` exist for simple/perf cases — Phase 4.)

## Lists & keys
Render a collection by mapping data → JSX (`expenses.map(e => <ExpenseItem key={e.id} .../>)`). Every sibling in a list needs a **stable, unique `key`** so the reconciler matches items across renders. Use a real id; **never the array index** if the list can reorder/insert/delete (causes wrong-item bugs and lost input state).

## Conditional rendering
Return different JSX based on state: `if (loading) return <Spinner/>`, the `&&` idiom (`{error && <p>{error}</p>}` — render only if truthy), and the ternary (`{isOpen ? <A/> : <B/>}`). `ExpenseList` shows an empty-state message when there are no items.
