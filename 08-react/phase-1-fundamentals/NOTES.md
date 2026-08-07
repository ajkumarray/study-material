<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · state events ➡](../phase-2-state-events/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals: Notes

## What React is
A library for building UIs from **components** — reusable, self-contained pieces that return markup. The core idea: **UI = f(state)** — you describe what the UI should look like for a given state, and React figures out the DOM changes. Contrast the raw-DOM todo (JS Phase 7.2), where you manually created/updated/destroyed nodes and kept UI in sync with data by hand: React does that for you.

## Components & JSX
A **component** is a function that returns **JSX** — HTML-like syntax that compiles (via the Vite React plugin / Babel) to `React.createElement(...)` calls. Rules: components are `PascalCase`, return a single root element (or a `<>…</>` Fragment), use `className` (not `class`) and `htmlFor`, and self-close void tags. **Embed JavaScript with `{ }`** — `{expense.amount}`, `{items.map(...)}`, `{cond && <X/>}`. JSX is just expressions, so all your JS (map/filter/ternary) works inside it.

**Props** are a component's read-only inputs, passed like HTML attributes (`<ExpenseItem expense={e} onDelete={fn} />`) and received as a single object (usually destructured). **Props flow down, are immutable** — a child never modifies its props; to communicate up, it calls a **callback prop** (`onDelete`). `ExpenseItem` is *presentational*: it renders what it's given and reports clicks upward, holding no state.

## Composition
Build complex UIs by **composing** small components (`ExpenseList` renders many `ExpenseItem`s; `App` composes form + summary + list). This is React's answer to reuse — **composition over inheritance** (Software Design Phase 2): you nest and combine components, and pass content via props/`children`, rather than extending classes.

## The virtual DOM & reconciliation
React keeps a lightweight in-memory tree (the **virtual DOM**). On a state change it re-renders the component to a new virtual tree, **diffs** it against the previous one (**reconciliation**), and applies only the minimal real-DOM changes. Direct DOM manipulation is slow and error-prone (JS 7.2); the virtual DOM makes "re-render everything on every change" efficient enough to be the model you code against. **Keys** (Phase 2) help the diff match list items across renders.

## Function components & hooks (the modern model)
Modern React uses **function components** + **hooks** (Phase 2+). Class components (with lifecycle methods) still exist in old code, but hooks are the standard — simpler, composable, and the only thing you should write in new code.
