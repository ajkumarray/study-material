import { useState } from 'react';

/*
 * Phase 2 — STATE, EVENTS, and CONTROLLED INPUTS.
 *
 * useState gives a component memory that survives re-renders. Each keystroke
 * updates state, which re-renders the component with the new value — the input's
 * value is DRIVEN BY state (a "controlled input"), so React is the single source
 * of truth. This is the fundamental React loop: state -> UI, event -> setState -> re-render.
 */
export function ExpenseForm({ onAdd }) {
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState('FOOD');
  const [error, setError] = useState('');

  function handleSubmit(event) {
    event.preventDefault();                 // stop the browser's full-page form reload
    if (!description.trim()) return setError('description required');
    if (!(Number(amount) > 0)) return setError('amount must be positive');

    onAdd({                                 // hand the new expense up to the parent
      description: description.trim(),
      amount: Number(amount),
      category,
      spentOn: new Date().toISOString().slice(0, 10),   // today, YYYY-MM-DD
    });
    setDescription('');                     // reset the form
    setAmount('');
    setError('');
  }

  return (
    <form onSubmit={handleSubmit} className="expense-form">
      <input
        aria-label="description"
        placeholder="description"
        value={description}                          // value comes FROM state
        onChange={(e) => setDescription(e.target.value)}  // event updates state
      />
      <input
        aria-label="amount"
        type="number"
        placeholder="amount"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
      />
      <select aria-label="category" value={category} onChange={(e) => setCategory(e.target.value)}>
        {['FOOD', 'TRANSPORT', 'RENT', 'ENTERTAINMENT', 'OTHER'].map((c) => (
          <option key={c} value={c}>{c}</option>
        ))}
      </select>
      <button type="submit">Add</button>
      {/* Conditional render: only show the error paragraph when there is one */}
      {error && <p className="error" role="alert">{error}</p>}
    </form>
  );
}
