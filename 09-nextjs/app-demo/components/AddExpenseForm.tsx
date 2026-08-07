"use client";

import { useState } from "react";

// A Client Component: the "use client" directive opts this subtree into the browser
// runtime so it can use state/effects/handlers. It's an "island" in a server-rendered
// page. It receives a Server Action as a prop and passes it to the form's `action`.
export function AddExpenseForm({ action }: { action: (fd: FormData) => Promise<void> }) {
  const [open, setOpen] = useState(false);

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} type="button">
        + Add expense
      </button>
    );
  }

  return (
    <form action={action} style={{ display: "grid", gap: "0.5rem", maxWidth: 320 }}>
      <input name="description" placeholder="Description" required />
      <input name="amount" type="number" step="0.01" placeholder="Amount" required />
      <select name="category" defaultValue="food">
        <option value="food">food</option>
        <option value="transport">transport</option>
        <option value="housing">housing</option>
        <option value="leisure">leisure</option>
        <option value="other">other</option>
      </select>
      <button type="submit">Save</button>
    </form>
  );
}
