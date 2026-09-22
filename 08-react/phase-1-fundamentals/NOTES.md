<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · state events ➡](../phase-2-state-events/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals: Notes

## What React is

React is a JavaScript **library** (not a full framework) for building user interfaces out of **components** — small, reusable, self-contained pieces of UI that each know how to render themselves from their inputs. Its central idea is often written as **UI = f(state)**: you write a function that, given the current state, describes what the UI *should* look like, and React is responsible for figuring out how to make the real DOM match that description. You never manually create, update, or remove DOM nodes yourself.

- **Declarative, not imperative**: You describe the *end result* ("show this list of expenses") rather than the *steps to get there* ("create a `<li>`, set its text, append it, remove the old one..."). Contrast this with the raw-DOM todo app (JS Phase 7.2), where every state change required hand-written `createElement`/`appendChild`/`removeChild` calls to keep the DOM in sync with your data — miss one, and the UI drifts from the data.
- **Component-based**: The UI is broken into independent, composable pieces (`ExpenseForm`, `ExpenseList`, `ExpenseItem`, `Summary`). Each is testable and reasoned about in isolation.
- **A library, not a framework**: React only handles rendering/UI. Routing (React Router, Phase 6), server communication, and state-management-at-scale (Phase 7) are separate libraries you choose and wire in yourself — unlike a batteries-included framework like Angular.
- **Runs in the browser (or server, with SSR)**: In this track we use **client-side rendering** via Vite — the browser downloads a JS bundle that renders the whole UI. Next.js (track 09) adds server rendering on top of the same component model.

```jsx
// The mental model in one line: given this data...
const expenses = [{ id: 1, description: 'groceries', amount: 2400 }];

// ...this function describes the UI for it. Call it again with new data,
// and it describes the new UI. React reconciles the difference for you.
function ExpenseSummary({ expenses }) {
  return <p>Total items: {expenses.length}</p>;
}
```
In this example, `ExpenseSummary` is a pure description of "what the UI looks like for this `expenses` array." If `expenses` changes from one item to three, you don't write code that adds two `<li>`s — you just call the same function again with the new array, and React updates the DOM to match. That's the "f(state)" model: the same function, called with different state, produces different UI, and React does the diffing.

**Why it's useful:** Manually keeping a UI in sync with changing data is one of the most error-prone parts of front-end programming (stale DOM, forgotten updates, double-rendered elements). React's declarative model eliminates an entire class of bugs by making "re-describe the whole UI from data" the normal, cheap thing to do on every change, instead of a last resort.

**Summary / key takeaways:**
- React's core idea is UI = f(state): describe the UI for the current state; don't manually mutate the DOM.
- It's a UI library, not a full framework — routing/state-at-scale are separate choices.
- Declarative code says *what* the UI should be; imperative code (raw DOM) says *how* to get there step by step.

## JSX

**JSX** is a syntax extension to JavaScript that looks like HTML but is actually **sugar for function calls**. A build tool (here, `@vitejs/plugin-react`, which wraps Babel) compiles JSX into calls that create React elements — plain JavaScript objects describing what should appear on screen. Under the hood, with the modern ("automatic") JSX runtime that Vite's React plugin uses by default, `<ExpenseItem expense={e} />` compiles roughly to a call like `jsx(ExpenseItem, { expense: e })`; with the classic runtime, it would be `React.createElement(ExpenseItem, { expense: e })`. Either way, JSX is never a special templating language parsed at runtime — it's compiled away before the browser ever sees it.

- **It's not HTML.** JSX looks similar but has JS semantics: attributes are `camelCase` (`onClick`, `tabIndex`), the `class` attribute is `className` (since `class` is a reserved word in JS), and `for` on a `<label>` is `htmlFor`.
- **One root element.** A component must return a single element. If you have multiple siblings to return, wrap them in a real DOM element (`<div>`) or a **Fragment** (`<>...</>`), which renders nothing extra to the DOM.
- **Embed JS with `{ }`.** Anything inside curly braces is a plain JavaScript *expression* (not a statement) — variables, function calls, ternaries, `.map()`, arithmetic. You cannot put `if`/`for` statements directly inside `{ }`, but you can put expressions that produce the same effect (ternaries, `&&`, or a helper variable computed above the `return`).
- **Self-closing tags.** Void elements and components with no children must self-close: `<input />`, `<ExpenseForm />` — leaving off the `/` when there are no children is a syntax error in JSX (unlike HTML, which tolerates it for void tags).
- **Comments** inside JSX use `{/* ... */}` (a JS expression containing a comment), not `<!-- -->`.

```jsx
// From ExpenseItem.jsx — embedding expressions with { }
export function ExpenseItem({ expense, onDelete }) {
  const { id, description, amount, category, spentOn } = expense;
  return (
    <li className="expense-item">          {/* className, not class */}
      <span className="date">{spentOn}</span>
      <span className="desc">{description}</span>
      <span className="category">{category}</span>
      {/* {expression}: a function call (toFixed) embedded directly in markup */}
      <span className="amount">₹{Number(amount).toFixed(2)}</span>
      {/* onClick takes a FUNCTION reference, not a string; camelCase event name */}
      <button onClick={() => onDelete(id)} aria-label={`delete ${description}`}>
        ✕
      </button>
    </li>
  );
}
```
In this example, `{spentOn}`, `{description}`, and `{category}` interpolate plain string values from props. `{Number(amount).toFixed(2)}` shows that anything valid as a JS expression works — here a method call chain. `onClick={() => onDelete(id)}` is important: passing `onDelete(id)` directly (without the arrow) would *call it immediately during render* instead of registering it as a click handler — a very common beginner bug (see the Interview Q&A for the "what does this log" version of this).

```jsx
// JSX compiles to element-creation calls — this is what the browser actually runs
// (conceptually; exact call shape depends on the JSX runtime/transform):
const element = <h1 className="title">Hello</h1>;

// compiles to (automatic runtime, React 17+):
import { jsx as _jsx } from 'react/jsx-runtime';
const element = _jsx('h1', { className: 'title', children: 'Hello' });
```
This is why JSX requires a build step (Vite/Babel/TypeScript) — browsers don't understand JSX natively; they only ever see the compiled `jsx(...)`/`React.createElement(...)` calls.

**Why it's useful:** Writing `jsx('li', { className: 'expense-item', children: [...] })` by hand for a whole app would be unreadable. JSX lets you write UI structure that looks like the HTML it produces while still being full JavaScript underneath — you get template-like readability with the full power of the language (loops via `.map`, conditionals via ternaries/`&&`, arbitrary expressions) instead of a limited templating DSL.

**Summary / key takeaways:**
- JSX compiles to function calls that build element-describing objects — it's not parsed by the browser directly.
- Attributes are camelCase; `class` → `className`, `for` → `htmlFor`.
- `{ }` embeds any JS *expression* (not statements) — this is how loops/conditionals get into JSX.
- A component returns exactly one root node — use a Fragment (`<>...</>`) to avoid an unnecessary wrapper `<div>`.

## Components

A **component** is a JavaScript function that accepts an input object (**props**) and returns JSX describing what should render. Modern React (this whole track) uses **function components**; older code sometimes uses **class components** (see comparison below), but function components + Hooks are the standard for all new code.

- **Naming**: Components must be `PascalCase` (`ExpenseItem`, not `expenseItem`). This isn't just style — JSX uses the casing to decide whether a tag is a built-in DOM element (`<div>`, lowercase → rendered as an HTML tag) or a component reference (`<ExpenseItem>`, capitalized → looked up as a variable/function in scope).
- **A component is just a function.** It can be declared with `function` (as every component in `expense-web` is: `export function ExpenseItem({ expense, onDelete }) { ... }`) or as an arrow function assigned to a `const`. Both work identically; this repo consistently uses named `function` exports for clarity in stack traces and dev tools.
- **Render purity.** A component function must be **pure** with respect to rendering: given the same props/state, it should return the same JSX, and it must not cause side effects (network calls, timers, mutating variables outside itself) *during* the call. Side effects belong in `useEffect` (Phase 3). This purity is what allows React to call your component function as many times as it wants (including twice in development under StrictMode) without changing behavior.
- **Return value**: JSX, `null` (render nothing), an array of elements (each needing a `key`), or a string/number. `ExpenseList` returns different JSX depending on state — either the empty-state message or the `<ul>` of items — but always exactly one of them, never both.

```jsx
// From ExpenseList.jsx — a function component with conditional structure
export function ExpenseList({ expenses, onDelete }) {
  if (expenses.length === 0) {
    return <p className="empty">No expenses yet — add one above.</p>;
  }
  return (
    <ul className="expense-list">
      {expenses.map((e) => (
        <ExpenseItem key={e.id} expense={e} onDelete={onDelete} />
      ))}
    </ul>
  );
}
```
Calling `ExpenseList({ expenses: [], onDelete })` (React calls it internally, you never call it directly like a normal function) returns the "empty" paragraph; calling it with a populated array returns the `<ul>`. Same function, different output for different input — the definition of `UI = f(state)` at the component level.

**Function vs. class components:**

| | Function components | Class components |
|---|---|---|
| Syntax | Plain function returning JSX | `class X extends React.Component` with a `render()` method |
| State | `useState`/`useReducer` hooks | `this.state` + `this.setState()` |
| Side effects | `useEffect` hook | Lifecycle methods (`componentDidMount`, `componentDidUpdate`, `componentWillUnmount`) |
| Logic reuse | Custom hooks (Phase 4) | Higher-Order Components / render props (verbose, "wrapper hell") |
| `this` | Not used | Required (and a common source of bugs — binding handlers) |
| Status | The standard; all new code | Legacy; still found in older codebases, but hooks fully replaced the need for them |

**Why it's useful:** Splitting UI into small functions each responsible for one piece (`ExpenseItem` renders one row, `ExpenseList` renders the collection, `Summary` renders the derived totals) mirrors how you'd naturally decompose a problem into functions in any language — the same discipline you'd apply breaking a Java method into smaller ones, applied to UI.

**Summary / key takeaways:**
- A component is a function from props to JSX; must be `PascalCase` so JSX can distinguish it from a DOM tag.
- Rendering must be pure — no side effects during the render call itself.
- Function components + Hooks are what you write today; class components are legacy knowledge, not a starting point.

## Props

**Props** ("properties") are the read-only inputs passed into a component from its parent, exactly like function arguments — in fact, they *are* the function's argument, always received as a single object. JSX attribute syntax (`<ExpenseItem expense={e} onDelete={fn} />`) is how you pass them; inside the component you typically destructure the object (`{ expense, onDelete }`) rather than writing `props.expense` everywhere.

- **Read-only / immutable.** A component must never modify its own props (`expense.amount = 0` inside `ExpenseItem` would be a bug). Props flow one direction: parent → child. If a child needs to affect data owned by a parent, it does so indirectly, by calling a function the parent passed it (a **callback prop**).
- **Any JS value.** Props aren't limited to strings — you can pass numbers, booleans, arrays, objects, and functions. `expense={e}` passes a whole object; `onDelete={remove}` passes a function reference.
- **`children` is a special prop.** Whatever you nest between a component's opening/closing tags (`<Card>content</Card>`) is automatically available as `props.children` — this is the mechanism behind composition (see below, and Phase 5).
- **Default values** can be given via default parameters in the destructuring (`function Foo({ size = 'md' } = {})`), which is plain JS default-parameter syntax, nothing React-specific.

```jsx
// From App.jsx — passing props (data down) and a callback prop (events up)
export function App({ loader }) {
  const { expenses, loading, error, add, remove } = useExpenses(loader);
  // ...
  return (
    <main className="app">
      <h1>Expense Tracker</h1>
      <ExpenseForm onAdd={add} />                          {/* callback prop */}
      <Summary expenses={expenses} />                       {/* data prop */}
      <ExpenseList expenses={expenses} onDelete={remove} /> {/* data + callback */}
    </main>
  );
}
```
In this example, `App` never lets `ExpenseForm` or `ExpenseList` touch its own `expenses` state directly. Instead, it passes `add`/`remove` functions as props; when `ExpenseForm` calls `onAdd(newExpense)` on submit, it's really invoking `App`'s `add` function — the child triggers behavior in the parent without ever holding a reference to the parent's state. This is the callback-prop pattern that makes "events flow up" concrete (formalized as *lifting state up* in Phase 5).

```jsx
// Callback prop in the child: ExpenseItem doesn't delete anything itself —
// it just reports "this id was clicked" upward.
<button onClick={() => onDelete(id)} aria-label={`delete ${description}`}>✕</button>
```
`ExpenseItem` has no state and no delete logic of its own. It receives `onDelete` as a prop and, on click, calls it with the relevant `id`. The actual removal (`setExpenses(prev => prev.filter(...))`) happens up in `useExpenses`/`App`. This is what makes `ExpenseItem` **presentational**: it's a pure function of its props with no side effects or state, so it's trivial to reuse and test.

**Why it's useful:** Props are what make components composable and predictable. Because a child can never reach into and mutate a parent's data, you can always answer "where did this value come from?" by tracing props upward — a huge win for debugging compared to code where any part of the UI can mutate shared global state from anywhere.

**Summary / key takeaways:**
- Props are read-only inputs, always received as one object; never mutate them in the child.
- Pass a function as a prop (a callback) to let a child report events upward without owning state itself.
- `props.children` carries nested JSX — the foundation of composition.

## Composition

**Composition** is building complex UI by nesting and combining small, focused components rather than through class inheritance. It's React's primary reuse mechanism — mirroring the "composition over inheritance" principle from object-oriented design (Software Design Phase 2), but at the level of UI building blocks instead of classes.

- **Nesting components**: `App` composes `ExpenseForm`, `Summary`, and `ExpenseList` into a page. `ExpenseList` in turn composes many `ExpenseItem`s. Each level only needs to know about the level directly below it.
- **Passing content via `children`**: A wrapper component (a `Card`, `Modal`, or `Layout`) can accept arbitrary JSX as `children` and decide where/how to render it, without knowing or caring what that content actually is.
- **Small, focused components compose better.** `ExpenseItem` does exactly one thing (render one row); that's precisely what makes it safe to reuse inside `ExpenseList`'s `.map()` without any coupling between the two beyond the `expense`/`onDelete` props contract.

```jsx
// expense-web's whole component tree is composition:
//
//   App
//   ├── ExpenseForm          (local field state; calls onAdd)
//   ├── Summary               (derives total/byCategory via useMemo)
//   └── ExpenseList
//       └── ExpenseItem × N   (composed once per array element)

// A generic composition pattern using children (not currently in expense-web,
// but the idiomatic next step for e.g. a reusable Card wrapper):
function Card({ title, children }) {
  return (
    <div className="card">
      <h2>{title}</h2>
      {children}
    </div>
  );
}

// Usage: Card doesn't know or care what's inside it.
<Card title="Recent Expenses">
  <ExpenseList expenses={expenses} onDelete={remove} />
</Card>
```
In this example, `Card` is reusable for *any* content — a list, a form, plain text — because it only depends on `children` being renderable JSX, not on what that JSX actually is. This is the composition equivalent of a generic container class in a typed language: it doesn't need to know the concrete type of what it holds.

**Why it's useful:** Composition keeps components decoupled and independently testable. `ExpenseItem` has no idea it's rendered inside a `<ul>` by `ExpenseList`, which has no idea it's rendered inside `<main>` by `App` — each component's contract is just "give me these props, I'll render this JSX." You can swap, reorder, or reuse any piece without touching the others, which doesn't hold nearly as cleanly with inheritance-based UI toolkits.

**Summary / key takeaways:**
- Composition = building UIs by nesting small components, not by subclassing a base component.
- `children` is the prop that carries nested JSX into a wrapper component.
- Small, single-purpose components (like `ExpenseItem`) compose more easily and are easier to test than large, multi-responsibility ones.

## The virtual DOM & reconciliation

The **virtual DOM** is a lightweight, in-memory JavaScript representation of the UI — a tree of plain objects describing elements, not real browser DOM nodes. On every state change, React builds a *new* virtual tree by re-running the relevant component functions, compares it against the *previous* virtual tree (a process called **reconciliation**, or "diffing"), and computes the minimal set of real-DOM operations needed to make the browser DOM match — then applies just those.

- **Re-render ≠ re-paint everything.** Calling a component function again ("re-rendering") just produces a new virtual-DOM description; it does *not* automatically mean the real DOM is rebuilt. The diff step decides what actually changes on screen.
- **Diffing is heuristic, not exhaustive.** React's algorithm (Fiber, in modern React) uses assumptions — e.g., elements of a different type at the same position are assumed unrelated and get replaced wholesale, not patched — to keep diffing itself cheap (avoiding the general O(n³) tree-diff problem).
- **Keys make list diffing correct.** Without a stable `key`, React falls back to comparing list items by position; **with** a `key`, it can match a specific item across renders even if the list was reordered, and reuse that item's DOM node/state instead of discarding and recreating it. (Covered in depth in Phase 2, since it's really a list-rendering concern.)
- **Direct DOM manipulation is what this replaces.** In the raw-DOM todo app (JS Phase 7.2), you manually tracked which `<li>` corresponded to which todo and wrote imperative create/update/remove code for every change. The virtual DOM's job is to make "just re-describe the whole UI from scratch on every change" fast enough to be the *only* model you ever need to think in.

```jsx
// Conceptually, a state change triggers this cycle:
//
// 1. setExpenses(newList)                 -- state changes
// 2. React calls App(), ExpenseList(), ExpenseItem() again -- new virtual tree built
// 3. React diffs new tree vs. previous tree                -- reconciliation
// 4. React applies only the changed real-DOM nodes          -- e.g., one <li> removed
//
// If you added one expense to a 50-item list, React does NOT touch the other
// 50 <li> elements' DOM nodes — the diff finds they're unchanged (same type,
// same key, same props) and leaves them alone.
```

**Why it's useful:** This is what lets you write components as simple, synchronous "data → JSX" functions without ever thinking about *which specific DOM nodes* need updating — React's diffing handles that performance concern for you, so your code stays declarative and simple even as the app grows.

**Summary / key takeaways:**
- Virtual DOM = an in-memory tree describing the UI; reconciliation = diffing old vs. new tree to compute minimal real-DOM changes.
- Re-rendering a component (calling its function again) is cheap and does not necessarily touch the real DOM at all.
- Keys are how the diff algorithm matches list items across renders (details in Phase 2).
- The virtual DOM isn't "always faster" than hand-optimized direct DOM code — its value is a simple, safe programming model with *good enough* performance by default.

## Presentational vs. container components (preview)

Even in Phase 1's simplest components, a useful split is already visible and is worth naming early (it's formalized in Phase 5):

- **Presentational components** render what they're given and report events upward — no state, no data fetching, no side effects. `ExpenseItem` and `ExpenseList` are presentational: pure functions of their props.
- **Container components** own state and/or data and coordinate the presentational pieces beneath them. `App` (via the `useExpenses` hook, Phase 3/4) is the container in `expense-web`.

**Why it's useful:** Presentational components are trivial to test (render with props, assert on output — no mocking needed) and trivial to reuse (they have no hidden dependencies on where their data came from). Keeping most of your component tree presentational, with a thin layer of containers at the top, is a durable rule of thumb even as state-management approaches change.
