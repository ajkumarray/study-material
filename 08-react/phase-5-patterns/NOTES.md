<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · hooks](../phase-4-hooks/NOTES.md) | [Phase 6 · routing ➡](../phase-6-routing/NOTES.md)
<!-- /nav -->

# Phase 5 — Component Patterns: Notes

## Lifting state up & one-way data flow
When multiple components need the same data, move (**lift**) that state to their nearest common parent and pass it down as props. `App` owns the expense state (via `useExpenses`) and passes it to `ExpenseForm` (a callback `onAdd`), `Summary` (the list), and `ExpenseList` (the list + `onDelete`). The form and list are siblings sharing state through their parent.

This creates **one-way (unidirectional) data flow**: **data flows down** (props), **events flow up** (callbacks). No component reaches sideways or up to mutate another's state. This single constraint is why React apps are predictable — you can always trace where state lives and how it changes ("single source of truth").

## Container vs presentational
A useful separation:
- **Container** components own state/data-fetching and coordinate (`App`). Few, near the top.
- **Presentational** components just render props and emit events (`ExpenseItem`, `ExpenseList`, `Summary`). Many, reusable, trivially testable (pure functions of props).

Keeping most components presentational makes them reusable and your tests simple. (Hooks like `useExpenses` now absorb the "container logic," so the distinction is softer than it once was — but the instinct to push state up and keep leaves dumb still holds.)

## Composition patterns
- **`children` prop** — pass JSX into a component (`<Card>{content}</Card>`), the React way to build wrappers/layouts (a `Modal`, `Card`, `Layout`). Composition over configuration.
- **Slots / render props** — pass a function as a prop that returns JSX, letting the parent control rendering of a piece. Largely superseded by hooks for logic sharing, but still used for flexible UI.
- **Lifting vs colocating** — keep state as *local* as possible; lift only when genuinely shared. Over-lifting makes a giant top component that re-renders everything.

## Forms & controlled flow
Forms (Phase 2) are the common place these patterns meet: the form holds its own field state locally, validates, and calls a parent callback with the result — local state for the fields, lifted state for the shared list. For complex forms, libraries (React Hook Form, Formik) reduce boilerplate.

## Conditional & list rendering recap
Composition + conditional rendering + list mapping (Phase 2) are the everyday tools: choose which component to render from state, and map arrays of data to arrays of components. Most UIs are just these three combined.
