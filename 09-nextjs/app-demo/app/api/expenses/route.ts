import { NextResponse } from "next/server";
import { getExpenses, addExpense, type Expense } from "@/lib/data";

// A Route Handler defines HTTP methods for /api/expenses using the Web Request/
// Response APIs. This is how you build a REST/JSON API in the App Router.
export async function GET() {
  const expenses = await getExpenses();
  return NextResponse.json(expenses);
}

export async function POST(request: Request) {
  const body = (await request.json()) as Partial<Omit<Expense, "id">>;
  if (!body.description || typeof body.amountCents !== "number") {
    return NextResponse.json({ error: "invalid payload" }, { status: 400 });
  }
  const created = await addExpense({
    description: body.description,
    amountCents: body.amountCents,
    category: body.category ?? "other",
  });
  return NextResponse.json(created, { status: 201 });
}
