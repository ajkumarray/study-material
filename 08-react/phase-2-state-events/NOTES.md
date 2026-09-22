<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · effects ➡](../phase-3-effects/NOTES.md)
<!-- /nav -->

# Phase 2 — State & Events: Notes

## useState & the render loop

**State** is data a component "remembers" across renders — unlike a plain local variable inside the component function, which is recreated from scratch every time the function runs. `useState` is the Hook that gives a function component this memory:

```jsx
const [value, setValue] = useState(initialValue);
```

It returns a two-element array: the **current value** for this render, and a **setter function** that schedules an update. Calling the setter is the *only* thing that makes React re-render the component with a new value — nothing else does.

- **The fundamental loop**: render from current state → user interacts (an event) → event handler calls the setter → React schedules a re-render → component function runs again, `useState` returns the *new* value this time → new JSX is diffed and painted. This loop (`state → UI`, `event → setState → re-render`) is the mental model for everything else in this phase.
- **`initialValue` is used only on the very first render.** On every subsequent render, `useState` ignores the argument you pass and returns whatever the current state actually is — React is tracking that value internally, associated with this specific `useState` call in this specific component instance.
- **Each `useState` call is independent.** A component can (and usually does) call `useState` multiple times for unrelated pieces of state (`ExpenseForm` has four: `description`, `amount`, `category`, `error`) — no need to combine everything into one object unless the values are always updated together.

```jsx
// From ExpenseForm.jsx — four independent pieces of state
const [description, setDescription] = useState('');
const [amount, setAmount] = useState('');
const [category, setCategory] = useState('FOOD');
const [error, setError] = useState('');
```
On the component's first render, `description` is `''`. When the user types, `setDescription(e.target.value)` runs, React re-renders `ExpenseForm`, and this time `useState('')`'s call *returns* the new string instead of `''` — even though the literal argument `''` is still written in the source. React remembers state per-hook-call, not by re-evaluating the initializer.

### Rules for using state correctly

- **Never mutate state directly.** Always call the setter with a *new* value; never mutate the existing one in place.
```jsx
// WRONG — mutates the array in place; React's reference check sees no change
expenses.push(newExpense);       // no re-render happens; bug

// RIGHT — build a new array and hand it to the setter
setExpenses((prev) => [newExpense, ...prev]);
```
  React (in the common case, without a custom comparator) decides whether to re-render by comparing the *new* state value's reference to the old one. Mutating an array or object in place keeps the same reference — React sees "no change" and skips the re-render, even though the data underneath changed. This is the same reference-vs-value distinction as JS Phase 1's discussion of mutable vs. immutable data, now with a rendering consequence attached.

- **State updates are asynchronous and batched.** After calling `setX(newValue)`, the variable `x` in the *same* function call still holds the *old* value — the new value only becomes visible on the *next* render.
```jsx
function handleClick() {
  console.log(count);        // e.g., 0
  setCount(count + 1);
  console.log(count);        // STILL 0 — setCount hasn't re-rendered yet;
                              // `count` in this closure is still the old value
}
```
  In React 18+, this batching applies not just inside React event handlers but also inside promises, `setTimeout`, and other async callbacks ("automatic batching") — multiple `setState` calls in the same tick are coalesced into a single re-render rather than one re-render per call.

- **Use the functional updater form when the new value depends on the previous value.**
```jsx
// From useExpenses.js — functional updates are safe under batching/closures
setExpenses((prev) => [created, ...prev]);
setExpenses((prev) => prev.filter((e) => e.id !== id));
```
  `setCount(count + 1)` reads `count` from the closure at the time the handler was created/called, which can be stale if multiple updates are queued before a re-render happens. `setCount(c => c + 1)` instead receives React's *actual latest* pending value as its argument, guaranteeing correctness even if several updates are batched together. Rule of thumb: if the new state is computed from the old state, use the functional form.

- **Don't store derived data in state.** If a value can be *computed* from existing state/props, compute it during render instead of storing (and separately updating) it in its own `useState`. `Summary`'s `total` and `byCategory` are derived from `expenses` — they are computed in the render body (wrapped in `useMemo`, Phase 4) rather than kept as separate state that would need to be manually kept in sync with `expenses` on every add/remove.

**Why it's useful:** `useState` is what turns a stateless rendering function into an interactive component. Every dynamic behavior in a React app — a form field, a toggle, a loaded list, a loading spinner — is state driving what gets rendered, updated only through the setter so React always knows when to re-render.

**Summary / key takeaways:**
- `useState` returns `[value, setter]`; calling the setter is the only way to trigger a re-render.
- Never mutate state — always create and pass a new value (spread, map, filter, `[...]`).
- State updates are async/batched — the variable doesn't update mid-function; use the functional updater (`prev => ...`) when deriving from previous state.
- Don't duplicate data into state that could instead be computed during render.

## Events

React normalizes native DOM events into **SyntheticEvent** objects, giving you a consistent, cross-browser API (`e.target`, `e.preventDefault()`, `e.stopPropagation()`, etc.) regardless of which browser is running the code. Event handler props are camelCase (`onClick`, `onChange`, `onSubmit`) and take a **function reference**, not a string (unlike inline HTML `onclick="..."` attributes).

- **Pass a function, don't call it.** `onClick={handleDelete}` registers `handleDelete` as the handler; `onClick={handleDelete()}` calls `handleDelete` immediately during render (and typically passes its *return value*, often `undefined`, as the handler — usually not what you want, and definitely not "run this on click").
- **Passing arguments to a handler**: wrap it in an arrow function so the call is deferred until the event actually fires.
```jsx
// From ExpenseItem.jsx
<button onClick={() => onDelete(id)} aria-label={`delete ${description}`}>✕</button>
```
  `() => onDelete(id)` is a new function created on every render that, when *later* invoked by the click, calls `onDelete` with `id` closed over from this render. Contrast `onClick={onDelete}` — that would call `onDelete` with the raw DOM `MouseEvent` as its argument, not the expense's `id`, since React always passes the event object as the first argument to a directly-referenced handler.
- **`event.preventDefault()`** stops the browser's default behavior — most commonly, stopping a `<form onSubmit>` from doing a full-page navigation/reload (the browser's native, pre-JS form behavior).
```jsx
// From ExpenseForm.jsx
function handleSubmit(event) {
  event.preventDefault();   // stop the browser's full-page form reload
  if (!description.trim()) return setError('description required');
  // ...
}
```
- **`event.stopPropagation()`** prevents the event from continuing to bubble up to parent handlers — useful when a nested clickable element (e.g., a delete button inside a clickable row) shouldn't also trigger the row's own click handler.

**Why it's useful:** Consistent event handling across browsers, plus first-class function values as handlers, means event logic is just regular JavaScript functions/closures — no separate templating-language event syntax to learn, and passing contextual data (like an item's `id`) to a handler is just a normal closure, not a special framework feature.

**Summary / key takeaways:**
- Handlers are camelCase props taking function references; SyntheticEvent normalizes cross-browser differences.
- Wrap a handler in an arrow function (`() => fn(arg)`) to pass extra arguments — never call it directly in the JSX.
- `preventDefault()` stops default browser behavior (most commonly form submission reload); `stopPropagation()` stops bubbling.

## Controlled inputs & forms

A **controlled input** is a form element (`<input>`, `<textarea>`, `<select>`) whose displayed `value` is driven entirely by React state, and which updates that state on every `onChange` — so React (not the DOM) is the single source of truth for what the field contains.

```jsx
// value={description} onChange={e => setDescription(e.target.value)}
value={description}                                // value comes FROM state
onChange={(e) => setDescription(e.target.value)}    // every keystroke updates state
```

- **The loop for a controlled field**: user types a character → `onChange` fires with the new full string in `e.target.value` → `setDescription` updates state → component re-renders → the `<input>`'s `value` attribute is set (by React) back to the (now-updated) state, which visually looks like nothing happened because the state matches what was just typed. If you set `value` from state but forget the `onChange` handler, the input becomes **read-only** (React keeps resetting it to the unchanging state value) — a very common beginner bug.
- **Why bother controlling it?** Because state is the source of truth, validation, formatting, and resetting are all just state operations. `ExpenseForm` resets the whole form after a successful submit purely by setting state back to empty strings:
```jsx
// From ExpenseForm.jsx — full controlled-form flow
function handleSubmit(event) {
  event.preventDefault();
  if (!description.trim()) return setError('description required');
  if (!(Number(amount) > 0)) return setError('amount must be positive');

  onAdd({
    description: description.trim(),
    amount: Number(amount),
    category,
    spentOn: new Date().toISOString().slice(0, 10),
  });
  setDescription('');    // reset the form — just setting state
  setAmount('');
  setError('');
}
```
In this example, submitting the form validates against the current `description`/`amount` state, calls the parent's `onAdd` callback with the assembled expense object (converting `amount` from a string to a `Number` — note the form always stores it as a string, since that's what an `<input type="number">`'s `e.target.value` gives you), and then resets all three fields to their initial values. Because the inputs are controlled, "reset the form" needs no DOM API calls at all — it's just three `setState` calls.

- **`<select>` is controlled the same way** — `value={category}` on the `<select>` itself (not on the individual `<option>`s), with `onChange` updating it:
```jsx
<select aria-label="category" value={category} onChange={(e) => setCategory(e.target.value)}>
  {['FOOD', 'TRANSPORT', 'RENT', 'ENTERTAINMENT', 'OTHER'].map((c) => (
    <option key={c} value={c}>{c}</option>
  ))}
</select>
```
- **Uncontrolled inputs** exist too — an input that manages its own value in the DOM, read via a `ref` only when needed (e.g., on submit), instead of on every keystroke. They avoid a re-render per keystroke and are simpler for "just grab the value once" cases, at the cost of React no longer being the source of truth. Covered with `useRef` in Phase 4.

| | Controlled | Uncontrolled |
|---|---|---|
| Source of truth | React state | The DOM itself |
| Re-renders | On every keystroke (one `setState` per change) | None from typing; only when you choose to read it |
| Validation/formatting as you type | Trivial (just derive from state) | Needs a manual `onChange` + `ref` read |
| Reset | `setState(initial)` | `ref.current.value = ...` or `form.reset()` |
| Typical use | Forms with validation, dynamic behavior (this repo's `ExpenseForm`) | Simple "read on submit" fields, file inputs (which *must* be uncontrolled), perf-sensitive cases |

**Why it's useful:** Controlled forms make an entire category of form bugs (stale displayed value, validation that doesn't match what's shown, inconsistent reset behavior) structurally impossible, because there's only ever one place the value lives — state — instead of two places (state and the DOM) that could disagree.

**Summary / key takeaways:**
- A controlled input's `value` comes from state and is updated via `onChange` — React owns the value, not the DOM.
- Setting `value` without `onChange` makes an input read-only (a common bug); setting neither makes it fully uncontrolled.
- Validation, formatting, and reset are all just state operations on a controlled input — no manual DOM reads/writes needed.
- Uncontrolled inputs (via `ref`, Phase 4) trade "React as source of truth" for fewer re-renders and less code in simple cases.

## Lists & keys

Rendering a collection means mapping an array of data to an array of JSX elements — the same `.map()` from JS Phase 4, just producing components instead of plain values.

```jsx
// From ExpenseList.jsx
<ul className="expense-list">
  {expenses.map((e) => (
    <ExpenseItem key={e.id} expense={e} onDelete={onDelete} />
  ))}
</ul>
```

- **Every element in an array returned from `.map()` inside JSX needs a `key` prop.** The `key` is not passed to the component as a regular prop (it's not accessible as `props.key` inside `ExpenseItem`) — React reserves it purely for its own bookkeeping, to match elements across renders during reconciliation.
- **Use a stable, unique identifier from the data — never the array index — if the list can reorder, filter, or have items inserted/removed anywhere but the very end.** `e.id` (assigned by the backend/API) is stable no matter how the array is sorted or filtered; the index `0, 1, 2, ...` is not — it shifts whenever an earlier item is removed or the list is reordered.

```jsx
// Why index-as-key breaks: a list of controlled inputs, one per item
// Before delete: [{id:1,key:0}, {id:2,key:1}, {id:3,key:2}]
// Delete item id=1 (index 0):
// After, using INDEX as key: [{id:2,key:0}, {id:3,key:1}]
//   -> React sees key=0 "still exists" and reuses that DOM node/state for what
//      is now a DIFFERENT item (id 2, formerly at key=1). Any local state tied
//      to that DOM node (e.g., an open edit input, scroll position, focus)
//      now belongs to the WRONG expense. Visually items can appear to keep
//      old values or lose input focus incorrectly.
// After, using e.id as key: [{id:2,key:2}, {id:3,key:3}]
//   -> keys 2 and 3 are recognized as the SAME items as before; key 1 is gone
//      -> React correctly removes exactly that one DOM node and leaves the
//      other two untouched, with their state intact.
```
In this example, index-based keys cause React to misattribute per-item state after a deletion in the middle of the list, because the *positions* shift even though the *identities* of the remaining items didn't change. `e.id`-based keys stay attached to the correct logical item regardless of position, so React can correctly determine "this exact one was removed" and "these two are unchanged, just shifted."

- **When is the array index an acceptable key?** Only when the list is static and never reorders/filters/inserts/removes anything except possibly appending at the end, and items have no internal state. In practice, defaulting to a real id is the safer habit, so this repo (and this guide) treats index keys as something to avoid rather than something conditionally fine.

**Why it's useful:** Correct keys make the reconciler's diff both *fast* (matching by identity instead of comparing whole subtrees) and *correct* (preserving the right DOM node/state for the right logical item across reorders) — getting this wrong is one of the most common real-world React bugs, usually surfacing as "the wrong row's input keeps its old value" or "clicking delete removes the wrong-looking row for a frame."

**Summary / key takeaways:**
- Map arrays to JSX with `.map()`; every element needs a stable, unique `key`.
- Never use the array index as a key for a list that can reorder, filter, or have items removed from the middle — it causes wrong-item state bugs.
- `key` is reserved by React for reconciliation — it's not accessible as a regular prop inside the component.

## Conditional rendering

Choosing *which* JSX to render based on state/props — the UI equivalent of an `if` statement, expressed with JS expressions since JSX only allows expressions inside `{ }`.

- **Early return** — return different JSX entirely from different branches of the function, before reaching the "main" return:
```jsx
// From ExpenseList.jsx
if (expenses.length === 0) {
  return <p className="empty">No expenses yet — add one above.</p>;
}
return ( /* the <ul> of items */ );
```
- **The `&&` idiom** — render something only if a condition is truthy; renders nothing (React ignores `false`/`null`/`undefined` as children) if falsy:
```jsx
// From ExpenseForm.jsx
{error && <p className="error" role="alert">{error}</p>}
```
  Careful with falsy-but-not-boolean values here: `{count && <Badge/>}` would render the literal text `0` on screen when `count` is `0`, because `0` is what `&&` evaluates to (and `0` *is* a valid React child — it gets rendered as text, unlike `false`/`null`/`undefined`, which render as nothing). The fix is to force a boolean: `{count > 0 && <Badge/>}` or `{Boolean(count) && <Badge/>}`.
- **The ternary** — pick between exactly two alternatives inline: `{isOpen ? <Panel/> : null}`.
- **A lookup/variable computed above the return** — for many branches, compute the element into a variable (or look it up in an object keyed by state) before the `return`, rather than nesting ternaries, which get unreadable past two branches.

```jsx
// From App.jsx — early returns for the three-state loading pattern (Phase 3)
if (loading) return <p className="loading">Loading…</p>;
if (error) return <p className="error" role="alert">Failed to load: {error}</p>;
return ( /* the loaded UI */ );
```
This shows the common "loading / error / data" pattern: three mutually exclusive branches, each an early return, so only one of the three possible UIs is ever rendered for a given render — there's no chance of accidentally showing the spinner *and* the data at once, because the function simply returns before reaching the later branches.

**Why it's useful:** Because conditional rendering is just JS control flow (or expressions) producing different JSX, there's no separate templating syntax to learn for "if/else in markup," and the same reasoning you'd apply to any function's branches (make sure every path returns something sensible, avoid deeply nested conditionals) applies directly.

**Summary / key takeaways:**
- Use early `return` for "entirely different UI" branches (loading/error/empty/data), `&&` for "show this or nothing," and ternaries for "show one of exactly two things."
- `cond && <X/>` renders the literal value of `cond` if it's falsy-but-not-`false`/`null`/`undefined` (notably `0`) — guard against numeric falsy values with an explicit boolean check.
- For 3+ branches, compute the element in a variable before the `return` rather than chaining ternaries.
