import { ExpenseItem } from './ExpenseItem.jsx';

/*
 * Phase 2 — rendering a LIST, and COMPOSITION.
 *
 * Render a collection by mapping data -> components (JS Phase 4 map, now
 * producing JSX). Each sibling in a list needs a stable, unique `key` so React
 * can efficiently match items across re-renders (never use the array index if
 * the list reorders). This component COMPOSES ExpenseItem — small components
 * assembled into bigger ones, the core React idea.
 */
export function ExpenseList({ expenses, onDelete }) {
  if (expenses.length === 0) {
    // Conditional rendering: return different JSX based on state.
    return <p className="empty">No expenses yet — add one above.</p>;
  }
  return (
    <ul className="expense-list">
      {expenses.map((e) => (
        <ExpenseItem key={e.id} expense={e} onDelete={onDelete} />   // key = e.id (stable)
      ))}
    </ul>
  );
}
