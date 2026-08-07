// A tiny in-memory data source so the demo builds with no external backend.
// In a real app this would be a DB call (Postgres via the Spring API, Prisma, etc.).
export interface Expense {
  id: string;
  description: string;
  amountCents: number;
  category: "food" | "transport" | "housing" | "leisure" | "other";
}

// Module-level state persists per server instance (fine for a demo).
const expenses: Expense[] = [
  { id: "1", description: "Lunch", amountCents: 1250, category: "food" },
  { id: "2", description: "Bus pass", amountCents: 3000, category: "transport" },
  { id: "3", description: "Rent", amountCents: 90000, category: "housing" },
];

// Simulate async I/O (what a real DB/fetch would be).
export async function getExpenses(): Promise<Expense[]> {
  await new Promise((r) => setTimeout(r, 10));
  return expenses;
}

export async function getExpense(id: string): Promise<Expense | undefined> {
  await new Promise((r) => setTimeout(r, 10));
  return expenses.find((e) => e.id === id);
}

export async function addExpense(input: Omit<Expense, "id">): Promise<Expense> {
  const created: Expense = { id: String(expenses.length + 1), ...input };
  expenses.push(created);
  return created;
}

export function totalCents(list: Expense[]): number {
  return list.reduce((sum, e) => sum + e.amountCents, 0);
}
