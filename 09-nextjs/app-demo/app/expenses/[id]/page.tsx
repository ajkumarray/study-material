import { getExpense } from "@/lib/data";
import { notFound } from "next/navigation";

// A dynamic segment: the folder name [id] captures the URL param. In Next 15+,
// `params` is a Promise (async params), so we await it.
export default async function ExpenseDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const expense = await getExpense(id);

  // notFound() renders the nearest not-found UI (a built-in 404 boundary).
  if (!expense) notFound();

  return (
    <section>
      <h2>{expense.description}</h2>
      <p>Amount: ${(expense.amountCents / 100).toFixed(2)}</p>
      <p>Category: {expense.category}</p>
      <p>
        <a href="/expenses">← Back</a>
      </p>
    </section>
  );
}
