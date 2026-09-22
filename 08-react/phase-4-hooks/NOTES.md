<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · effects](../phase-3-effects/NOTES.md) | [Phase 5 · patterns ➡](../phase-5-patterns/NOTES.md)
<!-- /nav -->

# Phase 4 — Hooks Deep Dive: Notes

## What Hooks are, and the Rules of Hooks

**Hooks** are functions — always prefixed `use` by convention — that let a function component "hook into" React features (state, effects, context, refs, and more) that used to require a class component. `useState` and `useEffect` are the two you already know (Phases 2–3); this phase covers the rest, plus how to build your own.

Hooks only work correctly under two rules, enforced by the `eslint-plugin-react-hooks` linter:

- **Only call Hooks at the top level.** Never inside `if`/`for`/`while`/nested functions/`try`/`catch`. Every render must call the exact same Hooks, in the exact same order, every time.
- **Only call Hooks from React function components or from other custom Hooks.** Not from plain utility functions, not from event handlers, not from class components.

```jsx
// WRONG — conditional Hook call
function ExpenseForm({ showCategory }) {
  const [description, setDescription] = useState('');
  if (showCategory) {
    const [category, setCategory] = useState('FOOD');   // BUG: conditional useState
  }
  // ...
}
```
**Why this breaks things**: React does not identify each `useState`/`useEffect` call by name — it identifies them by **the order they're called in**, per component instance, using an internal linked list (or array) of "hook slots" tied to that instance. On the first render, if `showCategory` is `true`, React records: slot 0 = `description`'s state, slot 1 = `category`'s state. If `showCategory` becomes `false` on a later render, the second `useState` call is skipped entirely — but React still tries to match subsequent Hook calls (if any existed after this one) to slot 1, now getting completely the wrong state back, or throwing an error entirely if the hook counts don't reconcile. This is exactly why the rule exists: **calling order is the identity mechanism**, so it must never change between renders of the same component instance.

**Why it's useful:** This constraint is what allows Hooks to be so lightweight syntactically — no explicit names, no manual keys to associate state with a slot — you just call `useState(...)` again and React knows which state you mean, purely from position. The cost of that convenience is the two rules above, which the linter enforces so you don't have to remember them by hand.

**Summary / key takeaways:**
- Hooks are `use`-prefixed functions giving function components state/effects/context/etc.
- React associates each Hook call with its state by *call order*, per component instance — never call Hooks conditionally, in loops, or in nested functions.
- Only call Hooks from component functions or other custom Hooks; the ESLint plugin enforces both rules automatically.

## useMemo

**`useMemo(computeFn, deps)`** caches the *result* of an expensive computation, recomputing it only when one of the values in `deps` changes between renders — on every other render, it returns the previously cached value without re-running `computeFn` at all.

```jsx
// From Summary.jsx
export function Summary({ expenses }) {
  const { total, byCategory } = useMemo(() => {
    const total = expenses.reduce((sum, e) => sum + Number(e.amount), 0);
    const byCategory = expenses.reduce((acc, e) => {
      acc[e.category] = (acc[e.category] ?? 0) + Number(e.amount);
      return acc;
    }, {});
    return { total, byCategory };
  }, [expenses]);   // recompute only when expenses changes

  return (
    <div className="summary">
      <strong>Total: ₹{total.toFixed(2)}</strong>
      {/* ... */}
    </div>
  );
}
```
In this example, `total` and `byCategory` are **derived state** (Phase 2's rule: don't store what you can compute) — they're recalculated from `expenses` rather than kept in their own `useState`. Wrapping the two `.reduce()` calls in `useMemo` means that if `Summary` re-renders for a reason unrelated to `expenses` (say, a parent re-render caused by something else entirely, with the same `expenses` array reference passed down again), React skips re-running both reduces and just returns the cached `{ total, byCategory }` object from last time — because `expenses` (the only dependency) is reference-equal to what it was last render.

- **`useMemo` is a performance hint, not a correctness guarantee.** React is explicitly allowed to discard a memoized value and recompute it even if the dependencies haven't changed (for internal memory-management reasons). Never rely on `useMemo` for something that must only run once, or for a required side effect — only use it to skip *redundant* work that would produce the same result anyway.
- **The dependency comparison is reference equality (`Object.is`), same as `useEffect`'s.** If `expenses` were a *new* array reference every render (even with identical contents), the memo would recompute every time — `useMemo` only helps when the same logical data is actually the same reference across renders.
- **Don't reach for `useMemo` by default.** For a computation as cheap as summing a handful of numbers, the memoization bookkeeping itself (storing the deps, comparing them, storing the cached result) can cost more than just recomputing — `useMemo` earns its keep on genuinely expensive computations (large data transforms, complex derived structures) or when the *identity* of the result matters for downstream memoization (see `useCallback` below).

**Why it's useful:** It lets you keep the "always compute derived data fresh during render" discipline from Phase 2 without paying an unnecessary performance cost when the underlying data hasn't actually changed — you get correctness (never stale) and performance (never redundant) at the same time.

**Summary / key takeaways:**
- `useMemo(fn, deps)` caches a computed *value*; recomputes only when a dependency changes (by reference).
- It's a performance optimization, not a correctness mechanism — React may discard the cache; never rely on it for required side effects.
- Use it for genuinely expensive derived computations, not reflexively on every calculation.

## useCallback

**`useCallback(fn, deps)`** is `useMemo` specialized for caching a **function's identity** across renders — `useCallback(fn, deps)` is exactly equivalent to `useMemo(() => fn, deps)`. Without it, a function defined inside a component body is a brand-new function object on every render (even if its logic is identical), which matters whenever that function's *reference* is compared elsewhere — passed to a memoized child (`React.memo`, Phase 7) or listed in another Hook's dependency array.

```jsx
// From useExpenses.js
const add = useCallback(async (expense) => {
  const created = await loader.create(expense);
  setExpenses((prev) => [created, ...prev]);
}, [loader]);

const remove = useCallback(async (id) => {
  await loader.remove(id);
  setExpenses((prev) => prev.filter((e) => e.id !== id));
}, [loader]);

return { expenses, loading, error, add, remove };
```
In this example, `add` and `remove` keep the *same function reference* across re-renders of whatever component calls `useExpenses`, as long as `loader` hasn't changed. Without `useCallback`, `useExpenses` would return a brand-new `add` function every time it re-ran — meaning every consumer (`App`, and anything `App` passes `add` to, like `ExpenseForm`'s `onAdd` prop) would see `onAdd` change identity on every render, which would defeat any `React.memo` wrapping `ExpenseForm` (it would always think its props changed) and would force any `useEffect` listing `add` as a dependency to re-run every single render.

| | `useMemo` | `useCallback` |
|---|---|---|
| Caches | A computed **value** | A **function's identity** |
| Equivalent to | itself | `useMemo(() => fn, deps)` |
| Typical use | Expensive derived data (`Summary`'s total) | Stable callback identity for memoized children or effect deps (`useExpenses`'s `add`/`remove`) |
| Recomputes when | A dependency changes (reference) | A dependency changes (reference) |

**Why it's useful:** `useCallback` matters specifically at the *boundary* where a function's reference is compared — passing callbacks into `React.memo`-wrapped children, or using a callback as a `useEffect`/`useMemo` dependency. Without it, those optimizations (or correctness guards) silently stop working because "new function every render" looks like "the callback changed" even when its behavior didn't.

**Summary / key takeaways:**
- `useCallback(fn, deps)` caches a function's *reference*, not just its behavior — it's `useMemo` for functions.
- Matters when the function is passed to a memoized child or used as another Hook's dependency; otherwise a fresh function every render is usually harmless.
- `useExpenses` memoizes `add`/`remove` so consumers can treat their identity as stable.

## useRef

**`useRef(initialValue)`** returns a plain mutable object with a single property, `.current`, initialized to `initialValue`. Unlike `useState`, **updating `ref.current` does not cause a re-render**, and the same ref object (same identity) persists across every render of the component. It has two common uses:

1. **Referencing a real DOM node** — attach `ref={someRef}` to a JSX element, and after the DOM is committed, `someRef.current` holds the actual DOM node, so you can imperatively call browser APIs React doesn't wrap (`.focus()`, `.scrollIntoView()`, measuring `.getBoundingClientRect()`, or integrating a non-React library that needs a real node).
```jsx
function SearchBox() {
  const inputRef = useRef(null);
  useEffect(() => { inputRef.current.focus(); }, []);   // focus on mount
  return <input ref={inputRef} placeholder="Search…" />;
}
```
2. **Holding a mutable value that must survive across renders but should never trigger a re-render when it changes** — a timer id (to pass to `clearInterval` later), the previous value of a prop/state (for comparison), an in-flight-request flag, or an `AbortController` instance.
```jsx
function useDebouncedValue(value, delayMs) {
  const timerRef = useRef(null);
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timerRef.current);
  }, [value, delayMs]);
  return debounced;
}
```
In this example, `timerRef.current` holds the current `setTimeout` id purely so a *later* call (the cleanup, or the next effect run) can clear it — the timer id itself is never rendered and shouldn't cause a re-render when it changes, which is exactly the case `useRef` (rather than `useState`) is for.

| | `useState` | `useRef` |
|---|---|---|
| Triggers re-render on change? | Yes | No |
| Persists across renders? | Yes | Yes |
| Typical content | Anything that affects what's rendered | DOM nodes, timer ids, previous values, mutable flags |
| Read/write | `[value, setValue]` — setter is the only way to change it | `ref.current = x` — direct mutation is normal and expected |

**Why it's useful:** Some values genuinely shouldn't drive rendering — a timer id is bookkeeping, not UI data — and using `useState` for them would cause pointless extra re-renders every time you just wanted to remember a value. `useRef` gives you React-managed persistence (the object survives across renders, unlike a plain local variable which is recreated every render) without the re-render cost, plus the escape hatch to real DOM nodes that JSX's declarative model otherwise hides from you.

**Summary / key takeaways:**
- `useRef` returns a stable `{ current }` object; mutating `.current` never triggers a re-render.
- Use it for DOM node access (`ref={...}` on JSX) and for mutable values that shouldn't affect rendering (timer ids, previous values, flags).
- Unlike a plain local variable in the component body, a ref's value survives across re-renders — but unlike state, changing it never schedules a new render.

## useReducer

**`useReducer(reducer, initialState)`** is an alternative to `useState` for managing state via a **reducer function** — `(state, action) => newState` — the same pattern Redux popularized, now built into React itself. Instead of calling several setters, you `dispatch` an **action** object describing *what happened*, and the reducer centrally decides *how* state should change in response.

```jsx
// A reducer-based rewrite of ExpenseForm's four separate useState calls,
// illustrating the pattern (expense-web itself uses plain useState here,
// since the form's state transitions are simple and independent):
function formReducer(state, action) {
  switch (action.type) {
    case 'field':
      return { ...state, [action.field]: action.value, error: '' };
    case 'error':
      return { ...state, error: action.message };
    case 'reset':
      return { description: '', amount: '', category: 'FOOD', error: '' };
    default:
      return state;
  }
}

function ExpenseFormReducerVersion({ onAdd }) {
  const [state, dispatch] = useReducer(formReducer, {
    description: '', amount: '', category: 'FOOD', error: '',
  });

  function handleChange(field) {
    return (e) => dispatch({ type: 'field', field, value: e.target.value });
  }
  // ...
}
```
In this example, every field update funnels through the *same* `dispatch` call with a different `action` payload, and `formReducer` is the single place that knows how each action transforms state — instead of four independent `setX` calls scattered through the component, each of which has to remember to also clear `error`. This centralization is the main value: as the number of related state transitions grows, a reducer keeps the "how state changes" logic in one reviewable, unit-testable function, separate from the component's rendering and event-wiring code.

- **When `useReducer` beats `useState`**: state is complex (many related fields that change together), or the next state depends on the current state in ways that are easy to get wrong with several independent setters (as above — four `setX` calls means four places that could forget to reset `error`), or you want the update logic to be testable in isolation (`formReducer(state, action)` is a plain function — no rendering, no hooks, trivial to unit test with plain assertions).
- **When `useState` is simpler and preferable**: independent pieces of state that don't need coordinated updates — which is exactly `ExpenseForm`'s actual situation (its four fields are largely independent), which is why the real code uses plain `useState` rather than a reducer.

**Why it's useful:** As state logic grows in complexity, spreading "what changes when" across many scattered `setX` calls becomes hard to follow and easy to get subtly wrong (forgetting to reset a related field, applying updates in an inconsistent order). A reducer makes every possible transition explicit and centralizes it, at the cost of more upfront structure than a handful of `useState` calls needs.

**Summary / key takeaways:**
- `useReducer(reducer, initialState)` returns `[state, dispatch]`; you dispatch actions describing intent, and a pure reducer function computes the new state.
- Prefer it over multiple `useState` calls when state transitions are complex, coupled, or need to be independently testable.
- For simple, independent pieces of state (like `ExpenseForm`'s separate fields), plain `useState` calls are simpler and are what this codebase actually uses.

## useContext

**`useContext(SomeContext)`** reads the current value provided by the nearest matching `<SomeContext.Provider value={...}>` above it in the tree, without that value needing to be threaded down as a prop through every intermediate component ("**prop drilling**").

```jsx
// Not currently used in expense-web (it's small enough that props suffice),
// but the idiomatic shape for something like the current authenticated user
// or theme, needed by components at many different depths:
const AuthContext = createContext(null);

function App() {
  const [user, setUser] = useState(null);
  return (
    <AuthContext.Provider value={user}>
      <Header />        {/* doesn't need `user` itself, but its children might */}
      <ExpensePage />
    </AuthContext.Provider>
  );
}

function UserBadge() {
  const user = useContext(AuthContext);   // reads it directly, however deep it is
  return <span>{user?.name ?? 'Guest'}</span>;
}
```
In this example, `UserBadge` can be nested arbitrarily deep inside `ExpensePage` (through any number of intermediate components that don't care about `user` at all) and still read it directly via `useContext(AuthContext)` — none of those intermediate components need a `user` prop just to forward it further down, which is the prop-drilling problem Context solves.

- **Every consumer re-renders whenever the Provider's `value` changes** — this is Context's main limitation. Good for **low-frequency, broadly-needed data** (current user, theme, locale, feature flags). Poor for **high-frequency state** (something changing on every keystroke or every second) shared widely, because every consuming component re-renders on every change regardless of whether it uses the part that changed.
- Context is not a general state-management replacement — it's specifically a way to avoid prop drilling for data many distant components need to read. For complex, frequently-changing shared state, a dedicated state library (Redux Toolkit, Zustand, Jotai — Phase 7) typically scales better, since most of them offer more granular subscription (a component only re-renders when the *specific slice* it reads changes).

**Why it's useful:** Passing a value like the current user or theme through eight layers of components that don't use it themselves, just so the ninth layer down can read it, is both tedious and fragile (renaming a prop means touching every intermediate component). Context lets the data-owning component "broadcast" the value and any interested descendant "tune in" directly.

**Summary / key takeaways:**
- `useContext(Ctx)` reads the nearest `<Ctx.Provider value={...}>`'s value, skipping the need to pass it through every intermediate component as a prop.
- Every consumer re-renders on any Provider value change — good for low-frequency/global data, not for high-frequency state shared widely.
- For complex/frequently-changing shared state, a dedicated state library often scales better than Context (Phase 7).

## Custom hooks — the big idea

A **custom hook** is simply a function whose name starts with `use` and that calls one or more built-in (or other custom) Hooks internally — packaging reusable **stateful logic** into something you can call from any component, exactly the way you'd extract a reusable non-stateful function. Custom hooks are React's primary mechanism for sharing logic between components, and they fully replaced the more awkward class-era patterns (mixins, Higher-Order Components, render props) that existed specifically to work around function components not having a way to share stateful behavior before Hooks existed.

```js
// useExpenses.js — the whole point of a custom hook, in one file
export function useExpenses(loader = expenseApi) {
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

  const add = useCallback(async (expense) => {
    const created = await loader.create(expense);
    setExpenses((prev) => [created, ...prev]);
  }, [loader]);

  const remove = useCallback(async (id) => {
    await loader.remove(id);
    setExpenses((prev) => prev.filter((e) => e.id !== id));
  }, [loader]);

  return { expenses, loading, error, add, remove };
}
```
```jsx
// App.jsx — the consumer side: one line replaces three useState calls,
// a useEffect, and two useCallback-wrapped functions
const { expenses, loading, error, add, remove } = useExpenses(loader);
```
In this example, all the machinery for "own an expense list, load it, track loading/error, and expose stable add/remove functions" lives in `useExpenses` — `App` itself stays a thin renderer that just calls the hook and passes the results down as props. If a second component elsewhere in the app also needed the exact same expense-list behavior, it would call `useExpenses()` too and get an entirely independent instance of that state and logic — no copy-pasting `useState`/`useEffect` blocks required, and no class-based mixin gymnastics.

- **Each call to a custom hook creates its own independent state.** Two components both calling `useExpenses()` do *not* share the same `expenses` array — each gets its own `useState`/`useEffect` instances, exactly as if each had inlined the same code separately. If you want *shared* state across components, that's what lifting state up (Phase 5) or Context is for — a custom hook shares *logic*, not automatically *the state itself*.
- **Custom hooks compose.** `useExpenses` itself uses `useState`, `useEffect`, and `useCallback` internally — you can build higher-level hooks out of lower-level ones, the same layering discipline as regular function composition.
- **Testability**: because `useExpenses` accepts a `loader` parameter (defaulting to the real `expenseApi`) instead of importing and calling `expenseApi` directly inside itself, tests (and any component) can inject a fake in-memory implementation — this is the Dependency Inversion idea from Software Design Phase 2, applied to a hook instead of a class/service.
- **The `use` prefix isn't just convention for readers — the linter relies on it.** `eslint-plugin-react-hooks` uses the `use` prefix to recognize which functions it should apply the Rules of Hooks to. A function that calls `useState` internally but isn't named `useSomething` won't be checked by the linter (and, more importantly, other developers reading the code won't know at a glance that calling it has Hook-like constraints).

**Why it's useful:** Custom hooks let you extract genuinely reusable *stateful* behavior — not just pure logic (plain functions already handled that) — into a unit that's independently testable, independently readable, and composable with other hooks, keeping components focused on the one thing only a component can do: rendering.

**Summary / key takeaways:**
- A custom hook is a `use`-prefixed function that composes other Hooks to package reusable stateful logic.
- `useExpenses` bundles list state, loading/error tracking, and add/remove into one reusable unit, keeping `App` a thin renderer.
- Each call site gets independent state — a custom hook shares logic, not the state instance itself.
- Accepting dependencies as parameters (like `loader`) rather than importing them directly makes a hook both reusable and easy to test with fakes.

## When to reach for what

A quick decision guide, connecting each Hook in this phase back to the situation it solves:

| Situation | Reach for |
|---|---|
| A value that affects what's rendered | `useState` |
| A side effect (fetch, subscription, timer, DOM sync) | `useEffect` |
| An expensive derived value, recomputed only when its inputs change | `useMemo` |
| A function passed to a memoized child, or used as another Hook's dependency, that needs a stable identity | `useCallback` |
| A DOM node reference, or a mutable value that shouldn't cause re-renders | `useRef` |
| Complex or tightly-coupled state transitions | `useReducer` |
| Data many distant components need, without prop drilling | Context (`useContext`) |
| Reusable stateful logic shared across components | A custom hook |

**Don't prematurely memoize.** `useMemo` and `useCallback` are not free — they add code, and the memoization bookkeeping itself (storing dependencies, comparing them each render) has its own small cost. Add them to fix a *measured* performance problem (via the React DevTools Profiler, Phase 7), not reflexively on every value and function "just in case." This is the React-specific instance of the general YAGNI principle: optimize the bottleneck you've actually found, not every place that theoretically could be one.

**Summary / key takeaways:**
- Match the Hook to the actual problem — state that renders vs. side effects vs. expensive computation vs. stable identity vs. non-rendering mutable storage vs. complex transitions vs. shared logic.
- Memoization Hooks are opt-in performance tools, not defaults — apply them where profiling shows they matter.
