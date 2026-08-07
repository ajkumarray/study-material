import { useMemo } from 'react';

/*
 * Phase 4 — useMemo for DERIVED STATE.
 *
 * The total and per-category breakdown are DERIVED from expenses — don't store
 * them in separate state (that risks going out of sync). Compute them during
 * render. useMemo caches the result so the (potentially expensive) reduce only
 * re-runs when `expenses` actually changes, not on every unrelated re-render.
 * This is the same streams/reduce from JS Phase 4, memoized.
 */
export function Summary({ expenses }) {
  const { total, byCategory } = useMemo(() => {
    const total = expenses.reduce((sum, e) => sum + Number(e.amount), 0);
    const byCategory = expenses.reduce((acc, e) => {
      acc[e.category] = (acc[e.category] ?? 0) + Number(e.amount);
      return acc;
    }, {});
    return { total, byCategory };
  }, [expenses]);   // recompute only when expenses change

  return (
    <div className="summary">
      <strong>Total: ₹{total.toFixed(2)}</strong>
      <ul>
        {Object.entries(byCategory)
          .sort((a, b) => b[1] - a[1])
          .map(([cat, amt]) => (
            <li key={cat}>{cat}: ₹{amt.toFixed(2)}</li>
          ))}
      </ul>
    </div>
  );
}
