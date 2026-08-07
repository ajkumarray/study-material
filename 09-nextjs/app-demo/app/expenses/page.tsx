import { getExpenses, totalCents } from "@/lib/data";
import { AddExpenseForm } from "@/components/AddExpenseForm";
import { addExpenseAction } from "./actions";

// An ASYNC Server Component: you can `await` data directly in the component body.
// This runs on the server on every request (dynamic) unless cached/static.
export default async function ExpensesPage() {
  const expenses = await getExpenses(); // no useEffect, no loading state needed
  const total = totalCents(expenses);

  return (
    <section>
      <h2>All expenses</h2>
      <ul>
        {expenses.map((e) => (
          <li key={e.id}>
            {/* Link to a dynamic route /expenses/[id] */}
            <a href={`/expenses/${e.id}`}>{e.description}</a> — ${(e.amountCents / 100).toFixed(2)}{" "}
            <em>({e.category})</em>
          </li>
        ))}
      </ul>
      <p>
        <strong>Total: ${(total / 100).toFixed(2)}</strong>
      </p>

      {/* A Client Component island for interactivity, wired to a Server Action. */}
      <AddExpenseForm action={addExpenseAction} />
    </section>
  );
}
