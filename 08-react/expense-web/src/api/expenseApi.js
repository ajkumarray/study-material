// The API layer — talks to the Spring Boot expense-api (track 02). Isolated
// here so components never call fetch directly (separation of concerns, and
// easy to mock in tests). Mirrors the backend's /api/expenses endpoints.
const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

async function handle(res) {
  if (!res.ok) throw new Error(`HTTP ${res.status}`);   // fetch doesn't reject on 4xx/5xx (JS 7.3)
  return res.status === 204 ? null : res.json();
}

export const expenseApi = {
  list: () => fetch(`${BASE}/api/expenses`).then(handle),

  create: (expense) =>
    fetch(`${BASE}/api/expenses`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(expense),
    }).then(handle),

  remove: (id) =>
    fetch(`${BASE}/api/expenses/${id}`, { method: 'DELETE' }).then(handle),
};
