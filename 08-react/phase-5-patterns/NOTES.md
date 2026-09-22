<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · hooks](../phase-4-hooks/NOTES.md) | [Phase 6 · routing ➡](../phase-6-routing/NOTES.md)
<!-- /nav -->

# Phase 5 — Component Patterns: Notes

## Lifting state up & one-way data flow

**Lifting state up** means: when two or more components need to read or change the same data, you don't try to keep separate copies in sync — you move (**lift**) that state to their nearest common ancestor, and pass it down to each of them as props (data) plus callbacks (ways to request a change).

```jsx
// From App.jsx — App is the common ancestor; it owns the state via useExpenses
// and hands pieces of it down to three separate children.
export function App({ loader }) {
  const { expenses, loading, error, add, remove } = useExpenses(loader);

  if (loading) return <p className="loading">Loading…</p>;
  if (error) return <p className="error" role="alert">Failed to load: {error}</p>;

  return (
    <main className="app">
      <h1>Expense Tracker</h1>
      <ExpenseForm onAdd={add} />
      <Summary expenses={expenses} />
      <ExpenseList expenses={expenses} onDelete={remove} />
    </main>
  );
}
```
In this example, `ExpenseForm`, `Summary`, and `ExpenseList` are **siblings** — none of them is an ancestor of another, so none of them can pass props directly to the others. But all three need to interact with the same expense list: `ExpenseForm` adds to it, `Summary` reads it to compute totals, `ExpenseList` reads and removes from it. Since siblings can't talk to each other directly, the state has to live somewhere they *do* share — their common parent, `App` — which passes each sibling exactly the slice of data and the callbacks it needs. `ExpenseForm` never sees the `expenses` array at all (it only needs `onAdd`); `Summary` never sees `remove` (it only needs `expenses`) — each child gets only what it actually uses, even though the state technically lives one level up from all of them.

This produces **one-way (unidirectional) data flow**: data always flows **down** through props, and requests to change that data always flow **up** through callback props (never sideways, never by a child reaching into and mutating a parent's or sibling's state directly).

```
        App  (owns `expenses` via useExpenses)
       / |  \
      /  |   \
ExpenseForm Summary ExpenseList
 (onAdd)  (expenses) (expenses, onDelete)

  data flows DOWN (props: expenses) ↓
  events flow UP (callbacks: onAdd, onDelete) ↑
```

**Why it's useful:** Because data can only ever flow one direction, you can always answer "where does this value actually live, and what could have changed it?" by tracing upward through the props chain to wherever the `useState`/hook call actually is — there's exactly one place per piece of state where it's created, and every other place is just a read-only view of it or a caller of a callback that updates it. Contrast a design where any component could reach out and mutate any other component's data directly: tracing a bug would mean searching the entire app for anywhere that touched that data, instead of one well-known place.

**Summary / key takeaways:**
- When siblings need the same data, lift it to their nearest common parent and pass it down as props + callbacks — siblings never communicate directly.
- One-way data flow: props carry data down, callback props carry events up; this single constraint is what makes state changes in a React app traceable.
- Each child receives only the props it actually needs, even if the state "lives" further up the tree.

## Container vs. presentational components

A useful, if slightly informal, split for organizing a component tree:

- **Container components** own state and/or data-fetching, and coordinate the pieces beneath them. `App` is the container in `expense-web` — it calls `useExpenses` and distributes the results.
- **Presentational components** just render whatever props they're given and report events upward via callbacks — no state of their own (or only trivial, purely-local UI state), no data fetching, no side effects. `ExpenseItem`, `ExpenseList`, and `Summary` are all presentational: each is a pure function of its props.

```jsx
// Presentational: ExpenseItem holds no state, does nothing but render props
// and forward a click as a callback call.
export function ExpenseItem({ expense, onDelete }) {
  const { id, description, amount, category, spentOn } = expense;
  return (
    <li className="expense-item">
      <span className="date">{spentOn}</span>
      <span className="desc">{description}</span>
      <span className="category">{category}</span>
      <span className="amount">₹{Number(amount).toFixed(2)}</span>
      <button onClick={() => onDelete(id)} aria-label={`delete ${description}`}>✕</button>
    </li>
  );
}
```
Given the exact same `expense` and `onDelete` props, `ExpenseItem` always renders the exact same output — there's nothing hidden inside it that could make it behave differently. That makes it trivial to test (render it with a fixed prop object and assert on the output — no mocking of network calls, no dealing with async loading states) and trivial to reuse anywhere the same shape of data needs to be shown.

**Why the split has softened, but the instinct still holds:** In this codebase, most of the actual "container logic" — state, data fetching, loading/error handling — doesn't live directly inside `App`'s JSX-writing body at all; it lives inside the `useExpenses` **custom hook** (Phase 4), which `App` merely calls. So the strict "container component vs. presentational component" boundary is less about which *component* holds state and more about which *hook* does — but the underlying discipline (push stateful/side-effect logic to a small number of well-defined places; keep the rendering-heavy components pure functions of props) is exactly the same idea, just implemented with hooks instead of two different classes of component.

| | Container | Presentational |
|---|---|---|
| Owns state/data fetching | Yes (often via a custom hook) | No |
| Side effects | Yes | No |
| Typical count in an app | Few, near the top of the tree | Many, spread throughout |
| Testability | Needs mocking (fake API/loader) | Trivial (render with fixed props) |
| Reusability | Low (tied to specific data/state) | High (pure function of props) |
| Example here | `App` (+ `useExpenses`) | `ExpenseItem`, `ExpenseList`, `Summary` |

**Why it's useful:** Concentrating state and side effects in a small number of places, and keeping the rest of the tree as pure, prop-driven components, means most of your codebase (the presentational majority) needs no special test setup and can't develop the kind of "who changed this and when" bugs that stateful components are prone to.

**Summary / key takeaways:**
- Container components own state/data and coordinate; presentational components render props and emit events, with no state or side effects of their own.
- Custom hooks now absorb much of what used to be "container component" responsibility — the split is really about *where logic lives*, not strictly which component type.
- Presentational components are cheap to test and reuse precisely because they have no hidden dependencies beyond their props.

## Composition patterns

Beyond simply nesting components (Phase 1), a few named composition patterns come up repeatedly:

- **The `children` prop** — pass arbitrary JSX into a component as content, letting a wrapper control layout/behavior without knowing what it's wrapping:
```jsx
function Card({ title, children }) {
  return (
    <div className="card">
      <h2>{title}</h2>
      {children}
    </div>
  );
}

// Usage — Card doesn't know or care that its content is an ExpenseList:
<Card title="Recent Expenses">
  <ExpenseList expenses={expenses} onDelete={remove} />
</Card>
```
  This is "composition over configuration" — instead of `Card` accepting a dozen props trying to anticipate every possible layout need, it just accepts *content* and handles the chrome (title, border, padding) around it.

- **Render props / slots** — a function passed as a prop that returns JSX, letting the *parent* control exactly how a piece of UI renders while the *child* controls *when*/*with what data* it renders:
```jsx
// A generic pattern for e.g. a list wrapper that handles the empty state
// itself but lets the caller control how each item renders:
function DataList({ items, renderItem, emptyMessage }) {
  if (items.length === 0) return <p className="empty">{emptyMessage}</p>;
  return <ul>{items.map((item) => <li key={item.id}>{renderItem(item)}</li>)}</ul>;
}

<DataList
  items={expenses}
  emptyMessage="No expenses yet — add one above."
  renderItem={(e) => <ExpenseItem expense={e} onDelete={remove} />}
/>
```
  This pattern was much more central before Hooks existed (it, and Higher-Order Components, were the main ways to share *behavior* between components). Hooks replaced most of that use case for sharing *logic* — but render props/slots are still used today specifically for flexible *UI* composition, where the parent needs to control rendering, not just share behavior.

- **Lifting vs. colocating state** — the companion rule to "lift state up" is: **keep state as local as possible, and lift only when it's genuinely shared.** `ExpenseForm`'s field values (`description`, `amount`, `category`, `error`) are a good example of state that stays *local* — no other component needs to know what's currently typed into the form before it's submitted, so lifting them up to `App` would only add unnecessary re-renders of everything else in `App`'s tree on every keystroke, for no benefit. Only the *finished, submitted* expense needs to leave the form (via `onAdd`), so that's the only thing that crosses the component boundary.

**Why it's useful:** `children` and render props both let you separate "what a component structurally does" from "what content/behavior fills that structure," which keeps generic wrapper/layout components reusable across many different specific uses. Colocating state prevents the opposite failure mode from under-sharing state: over-lifting, where a single top-level component ends up owning everything and re-rendering enormous portions of the tree for changes that were never actually shared.

**Summary / key takeaways:**
- `children` lets a wrapper component accept arbitrary nested content without knowing its shape — the standard way to build layout/wrapper components.
- Render props/slots hand rendering control to the parent while the child controls when/with what data — largely superseded by hooks for *logic* sharing, still used for flexible *UI* composition.
- Keep state as local as possible; lift only when a value is genuinely needed by more than one component. `ExpenseForm`'s field state is a textbook example of state that should stay local.

## Forms & controlled flow (recap in the patterns context)

Forms are where local state and lifted state typically meet in the same component tree: the form itself owns *local* state for its own fields (Phase 2's controlled inputs), validates using that local state, and — only on successful submit — calls a *lifted* callback prop to hand the finished result up to whoever owns the shared data.

```jsx
// ExpenseForm: local state (description, amount, category, error) +
// a single point of contact with the outside world (onAdd).
function handleSubmit(event) {
  event.preventDefault();
  if (!description.trim()) return setError('description required');
  if (!(Number(amount) > 0)) return setError('amount must be positive');
  onAdd({ description: description.trim(), amount: Number(amount), category, spentOn: /* ... */ });
  setDescription(''); setAmount(''); setError('');
}
```
This is a clean illustration of the "colocate, lift only what's shared" rule from above: every keystroke updates *local* state (fast, contained, no impact on siblings), and exactly one thing — the finished, validated expense object — ever crosses into the lifted `onAdd` callback, at exactly one moment (submit).

For complex forms (many fields, cross-field validation, async validation, wizard-style multi-step flows), hand-rolling this with individual `useState` calls per field gets unwieldy — libraries like **React Hook Form** or **Formik** handle field registration, validation, and submission state with much less boilerplate, while still fundamentally building on the same controlled-input ideas from Phase 2.

**Why it's useful:** Keeping form field state local and only surfacing the finished result avoids re-rendering the entire app on every keystroke, and keeps validation logic colocated with the fields it validates instead of scattered across the tree.

**Summary / key takeaways:**
- Forms typically combine local state (the in-progress field values) with a single lifted callback (the finished, submitted result) — this is the pattern `ExpenseForm`/`App` use.
- For complex forms, dedicated libraries (React Hook Form, Formik) reduce boilerplate while keeping the same controlled-input foundation.

## Conditional & list rendering recap

The everyday toolkit for building most real UIs is just three ideas working together, all already covered individually: **composition** (small components assembled into bigger ones, Phase 1), **conditional rendering** (choosing which component/branch to show based on state, Phase 2), and **list rendering** (mapping arrays of data to arrays of components with stable keys, Phase 2). Almost every screen in a typical app — including this whole `expense-web` app — is built from nothing more than these three combined: decide *which* components to show, map data *arrays* into component arrays, and *compose* the pieces into a tree.

**Summary / key takeaways:**
- Most real-world UI is composition + conditional rendering + list rendering, combined — no additional "magic" beyond what Phases 1–2 already covered.
- Recognizing which of these three you're doing at any point in a component tree is usually enough to know what pattern applies.
