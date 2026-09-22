<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · state events ➡](../phase-2-state-events/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals: Interview Q&A

⭐ = asked constantly.

**Q: What is React and what problem does it solve?** ⭐⭐
React is a JavaScript library for building user interfaces from composable components. Its model is UI = f(state): you write a function that, given the current state, declares what the UI should look like, and React handles turning that declaration into the correct DOM updates. It solves the problem of manually keeping a UI in sync with changing data — before component libraries like React, developers wrote imperative code that tracked exactly which DOM nodes needed to be created, updated, or removed for every possible state change, which is tedious and extremely bug-prone at scale (the raw-DOM todo app from JS Phase 7.2 is a small-scale example of exactly that pain). React lets you instead write "render the whole UI from this data" and re-run that on every change; it figures out the minimal real DOM operations via reconciliation.

It's worth being precise that React is a *library*, not a framework: it only owns rendering and component state. Routing, global state management, and data fetching are separate libraries you choose (React Router, Redux/Zustand, React Query) — unlike Angular, which bundles all of that.

**Q: What is JSX, and why doesn't the browser run it directly?** ⭐
JSX is a syntax extension that lets you write HTML-like markup inside JavaScript. It is *not* HTML and is not natively understood by browsers — a build step (Babel, or here `@vitejs/plugin-react`) compiles it into plain JavaScript function calls that build element-describing objects (`React.createElement(...)`, or `jsx(...)` under the newer automatic JSX runtime). Because it compiles to normal function calls, anything valid inside a JS expression is valid inside `{ }` in JSX — this is why `.map()`, ternaries, and `&&` all work naturally inside markup, unlike a restricted templating language.

Syntactic differences from HTML that come up constantly: `className` instead of `class` (`class` is a reserved JS keyword), `htmlFor` instead of `for`, camelCase event handlers (`onClick`, `onChange`) that take function references (not strings), and the requirement that a component return exactly one root node (or a `<>...</>` Fragment).

```jsx
const el = <h1 className="title">Hi</h1>;
// compiles (automatic runtime) roughly to:
const el = _jsx('h1', { className: 'title', children: 'Hi' });
```

*Follow-up: what happens if you forget the key when rendering a list in JSX?* React renders it anyway but logs a console warning ("Each child in a list should have a unique key prop"), and falls back to matching items by position during reconciliation — which can cause visual/state bugs on reorder, insert, or delete (detailed in Phase 2).

**Q: What are props, and why are they described as "read-only"?** ⭐⭐
Props are a component's inputs, passed from a parent like HTML attributes and always received by the component as a single object argument (commonly destructured, e.g. `function ExpenseItem({ expense, onDelete })`). They're read-only in the sense that a component must never reassign or mutate the props object it receives — `expense.amount = 0` inside `ExpenseItem` would be a bug, because `expense` belongs to the parent's state. Data only flows one direction, parent to child.

To send information back *up* to a parent, a child doesn't mutate anything — it calls a **callback prop** the parent handed it. In `expense-web`, `App` passes `onAdd={add}` to `ExpenseForm`; when the user submits the form, `ExpenseForm` calls `onAdd(newExpense)`, which is really `App`'s `add` function from `useExpenses`. `ExpenseForm` never touches `App`'s state directly — it just invokes a function it was given.

*Follow-up: can you pass a function or an object as a prop, or only strings?* Any JavaScript value — props are just a plain object, so functions, arrays, objects, booleans, numbers all work. `expense={e}` passes a whole object; `onDelete={remove}` passes a function reference.

**Q: What is the virtual DOM and what is reconciliation?** ⭐⭐
The virtual DOM is an in-memory tree of plain JavaScript objects that describes what the UI should look like — it is not the real browser DOM. When state changes, React re-runs the affected component functions to build a *new* virtual tree, then diffs it against the *previous* virtual tree (this diffing step is called reconciliation), and computes the minimal set of real-DOM mutations needed to bring the browser in line — then applies only those. So "re-rendering" a component (calling its function again) is cheap and does not by itself mean any real DOM node changes; the diff decides that.

The practical payoff is that you can always write "just re-describe the whole UI from the current state" without thinking about which specific nodes to touch — React's diffing makes that model performant, instead of forcing you to hand-optimize DOM writes for every change like in JS Phase 7.2's raw-DOM approach.

*Follow-up: is the virtual DOM always faster than direct DOM manipulation?* No — this is a common misconception. Hand-optimized, surgical direct-DOM code can beat React's general-purpose diffing for a specific known update. The virtual DOM's real value isn't raw speed; it's giving you a simple, safe programming model (declare UI from state, always) that has *good enough* performance in the vast majority of cases, trading a little peak speed for a lot of maintainability and fewer bugs.

**Q: Why does React need a `key` prop when rendering a list?** ⭐
Keys give each item in a list a stable identity across renders so the reconciler can match a specific virtual-DOM node to the *same* item the next time the list renders — enabling it to reuse (and preserve the state/DOM of) that item, or correctly detect that it moved, instead of naively diffing by position. Without a good key, or when using the array index as a key on a list that can reorder/insert/delete, React can associate the wrong DOM node (and any local state inside it, like an open input) with the wrong data item after the list changes — a subtle, hard-to-spot bug class. The fix is always to use a stable, unique identifier from the data itself (`key={e.id}`), never the array index, whenever the list can be reordered or filtered. `ExpenseList` uses `key={e.id}` for exactly this reason.

**Q: Function components vs. class components — what's the difference, and which should you write today?** ⭐
Function components are plain functions that return JSX and use Hooks (`useState`, `useEffect`, etc.) for state and side effects. Class components extend `React.Component`, keep state on `this.state`, update it via `this.setState()`, and use lifecycle methods (`componentDidMount`, `componentDidUpdate`, `componentWillUnmount`) instead of `useEffect`. Function components + Hooks are the standard for all new code — they're more concise, avoid `this`-binding footguns, and, critically, let you extract and reuse stateful logic via **custom hooks** (Phase 4), which class components could only approximate with more verbose patterns like Higher-Order Components or render props. You'll still encounter class components in legacy codebases, but you wouldn't start a new component that way today.

**Q: What is composition in React, and how does it relate to "composition over inheritance"?**
Composition means building complex UI by nesting and combining small, focused components — `App` composes `ExpenseForm`, `Summary`, and `ExpenseList`; `ExpenseList` composes many `ExpenseItem`s. Content is passed into wrapper components via the `children` prop (whatever JSX you nest inside a component's tags), letting a component like a `Card` or `Modal` render arbitrary content without knowing what it is. This is React's primary mechanism for reuse — the direct UI-level analogue of the "prefer composition over inheritance" principle from object-oriented design (Software Design Phase 2): React doesn't even offer component inheritance as an option, and composition alone has proven sufficient.

**Q: What's the difference between a presentational and a container component?**
Presentational components render what they're given and report events upward via callback props — no state, no side effects, just a pure function of props (`ExpenseItem`, `ExpenseList`). Container components own state and/or data fetching and coordinate the presentational pieces beneath them (`App`, via the `useExpenses` hook). The split is useful because presentational components are trivial to test (render with props, assert output) and trivial to reuse, while containers concentrate the "moving parts." In modern React this line has blurred somewhat because custom hooks can hold most of the "container logic" independently of any specific component, but the underlying instinct — keep most components dumb/pure, push state to a few coordinating spots — still holds.

*Follow-up: where does state actually "live" in `expense-web`?* In the `useExpenses` custom hook, which `App` calls. `App` is still the container in the sense that it's the component that calls the hook and passes the results down, but the stateful logic itself is factored out of `App`'s body and into the hook, making it independently testable and reusable.

**Q: What must a component's render function avoid doing?**
It must be pure with respect to rendering — for the same props/state it should always return equivalent JSX, and it must not perform side effects (network requests, timers, logging with side effects, mutating variables outside its own scope) directly in the function body during the call. Side effects belong in `useEffect` (Phase 3), which runs *after* the render commits, not during it. This purity requirement is also what makes it safe for React to call your component function more than once for a single logical render — which is exactly what happens under `<StrictMode>` in development (Phase 3) to help surface violations of this rule.
