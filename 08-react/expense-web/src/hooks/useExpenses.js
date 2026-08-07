import { useState, useEffect, useCallback } from 'react';
import { expenseApi } from '../api/expenseApi.js';

/*
 * Phase 3 (effects/data-fetching) + Phase 4 (CUSTOM HOOK).
 *
 * A custom hook is a function starting with "use" that bundles reusable stateful
 * logic. This one owns everything about the expense list — loading, errors, and
 * the add/remove operations — so components stay focused on rendering. Extracting
 * logic into hooks is React's answer to code reuse (composition over inheritance).
 *
 * useEffect runs side effects (data fetching, subscriptions) AFTER render. The
 * dependency array [] means "run once on mount" (like a constructor). Effects
 * can return a cleanup function (for subscriptions/timers) — shown conceptually.
 *
 * `loader` is injected (defaults to the real API) so tests can pass a fake — the
 * Dependency Inversion idea from Software Design, in a hook.
 */
export function useExpenses(loader = expenseApi) {
  const [expenses, setExpenses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // useEffect: fetch the list once when the component mounts.
  useEffect(() => {
    let active = true;                       // guard against setting state after unmount
    loader
      .list()
      .then((data) => { if (active) { setExpenses(data); setLoading(false); } })
      .catch((e) => { if (active) { setError(e.message); setLoading(false); } });
    return () => { active = false; };        // cleanup: runs on unmount (avoids the leak)
  }, [loader]);

  // useCallback memoizes these functions so their identity is stable across
  // renders (avoids needless re-renders of children that receive them as props).
  const add = useCallback(async (expense) => {
    const created = await loader.create(expense);
    setExpenses((prev) => [created, ...prev]);   // functional update: derive from previous state
  }, [loader]);

  const remove = useCallback(async (id) => {
    await loader.remove(id);
    setExpenses((prev) => prev.filter((e) => e.id !== id));   // immutable update (no mutation!)
  }, [loader]);

  return { expenses, loading, error, add, remove };
}
