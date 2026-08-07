// A plain typed model (TypeScript track 07 — interfaces).
export interface Expense {
  id: number;
  description: string;
  amountCents: number;
  category: 'food' | 'transport' | 'housing' | 'leisure' | 'other';
}
