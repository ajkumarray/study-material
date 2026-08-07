/*
 * Phase 1 — a COMPONENT that takes PROPS.
 *
 * A component is a function that returns JSX (HTML-like syntax that compiles to
 * React.createElement calls). It receives read-only inputs called PROPS. This
 * one is "presentational": it just renders what it's given and calls a callback
 * on delete — it holds no state of its own.
 */
export function ExpenseItem({ expense, onDelete }) {
  const { id, description, amount, category, spentOn } = expense;   // destructure props
  return (
    <li className="expense-item">
      <span className="date">{spentOn}</span>
      <span className="desc">{description}</span>
      <span className="category">{category}</span>
      {/* {expression} embeds JS in JSX; toFixed formats the number */}
      <span className="amount">₹{Number(amount).toFixed(2)}</span>
      {/* onClick takes a FUNCTION; the arrow defers the call until clicked */}
      <button onClick={() => onDelete(id)} aria-label={`delete ${description}`}>
        ✕
      </button>
    </li>
  );
}
