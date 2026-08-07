import { useExpenses } from './hooks/useExpenses.js';
import { ExpenseForm } from './components/ExpenseForm.jsx';
import { ExpenseList } from './components/ExpenseList.jsx';
import { Summary } from './components/Summary.jsx';

/*
 * Phase 5 — COMPOSITION & LIFTING STATE UP.
 *
 * App is the container: it owns the shared state (via the useExpenses hook) and
 * passes data DOWN as props and callbacks. The form and list are siblings that
 * need to share the expense list, so the state lives in their common parent
 * (App) — "lifting state up". Data flows down; events flow up. This one-way
 * data flow is what makes React apps predictable.
 *
 * `loader` is injected so tests can render <App loader={fakeApi} /> without a
 * real backend.
 */
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
