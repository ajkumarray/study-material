"use server";

import { revalidatePath } from "next/cache";
import { addExpense, type Expense } from "@/lib/data";

// A Server Action: a function that runs on the SERVER but can be invoked directly
// from a client component (Next generates the RPC plumbing). No manual API route
// needed for mutations. It receives the submitted FormData.
export async function addExpenseAction(formData: FormData): Promise<void> {
  const description = String(formData.get("description") ?? "").trim();
  const amount = Number(formData.get("amount"));
  const category = String(formData.get("category") ?? "other") as Expense["category"];

  if (!description || !Number.isFinite(amount) || amount <= 0) return;

  await addExpense({ description, amountCents: Math.round(amount * 100), category });

  // Invalidate the cached render of /expenses so the new row appears.
  revalidatePath("/expenses");
}
