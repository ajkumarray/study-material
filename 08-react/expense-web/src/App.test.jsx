import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from './App.jsx';

/*
 * Phase 7 — TESTING React with Vitest + React Testing Library.
 *
 * RTL's philosophy: test what the USER sees and does (find elements by their
 * text/role/label, click, type), NOT implementation details. Tests run in jsdom
 * (a fake DOM in Node) — no browser needed.
 *
 * We inject a FAKE api `loader` so there's no real network — a hand-rolled test
 * double (the same idea as the Java capstone's in-memory repository).
 */
function makeFakeApi(initial = []) {
  let store = [...initial];
  let nextId = 100;
  return {
    list: async () => [...store],
    create: async (e) => {
      const created = { id: nextId++, ...e };
      store = [created, ...store];
      return created;
    },
    remove: async (id) => {
      store = store.filter((e) => e.id !== id);
      return null;
    },
  };
}

const seed = [
  { id: 1, description: 'groceries', amount: 2400, category: 'FOOD', spentOn: '2026-07-20' },
  { id: 2, description: 'metro', amount: 500, category: 'TRANSPORT', spentOn: '2026-07-19' },
];

describe('Expense Tracker app', () => {
  it('loads and displays expenses from the API', async () => {
    render(<App loader={makeFakeApi(seed)} />);
    // The list loads asynchronously (useEffect) -> wait for it.
    expect(await screen.findByText('groceries')).toBeInTheDocument();
    expect(screen.getByText('metro')).toBeInTheDocument();
  });

  it('shows the computed total (derived state via useMemo)', async () => {
    render(<App loader={makeFakeApi(seed)} />);
    await screen.findByText('groceries');
    expect(screen.getByText(/Total: ₹2900\.00/)).toBeInTheDocument();   // 2400 + 500
  });

  it('adds an expense through the form and updates the total', async () => {
    const user = userEvent.setup();
    render(<App loader={makeFakeApi(seed)} />);
    await screen.findByText('groceries');

    await user.type(screen.getByLabelText('description'), 'coffee');
    await user.type(screen.getByLabelText('amount'), '150');
    await user.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByText('coffee')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByText(/Total: ₹3050\.00/)).toBeInTheDocument()   // 2900 + 150
    );
  });

  it('validates the form (positive amount, non-blank description)', async () => {
    const user = userEvent.setup();
    render(<App loader={makeFakeApi(seed)} />);
    await screen.findByText('groceries');

    await user.type(screen.getByLabelText('description'), 'bad');
    await user.type(screen.getByLabelText('amount'), '-5');
    await user.click(screen.getByRole('button', { name: 'Add' }));

    expect(screen.getByRole('alert')).toHaveTextContent('amount must be positive');
    expect(screen.queryByText('bad')).not.toBeInTheDocument();   // not added
  });

  it('deletes an expense', async () => {
    const user = userEvent.setup();
    render(<App loader={makeFakeApi(seed)} />);
    await screen.findByText('metro');

    await user.click(screen.getByRole('button', { name: 'delete metro' }));
    await waitFor(() => expect(screen.queryByText('metro')).not.toBeInTheDocument());
    expect(screen.getByText('groceries')).toBeInTheDocument();   // the other survives
  });

  it('shows the empty state when there are no expenses', async () => {
    render(<App loader={makeFakeApi([])} />);
    expect(await screen.findByText(/No expenses yet/)).toBeInTheDocument();
  });
});
