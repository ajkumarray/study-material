/**
 * Capstone — a fully-typed expense-tracker domain.
 *
 * Run:        npx tsx capstone/expenseDomain.ts
 * Type-check: npm run typecheck
 *
 * Ties together the whole track on the repo's running expense-tracker theme
 * (Spring `expense-api`, React `expense-web`):
 *   • branded IDs            (Phase 7.4)          — identity safety
 *   • as-const derived union (Phase 2.2)          — categories, one source of truth
 *   • discriminated unions   (Phase 5.1, 7.2)     — domain events, illegal states impossible
 *   • utility-type DTOs      (Phase 5.3)          — Omit/Pick/Partial derive the API surface
 *   • generics + constraints (Phase 4)            — a reusable in-memory store
 *   • Result<T,E> + typed err(Phase 4.4, 7.3)     — no exceptions across the boundary
 *   • exhaustive `never`     (Phase 5.1)          — event reducer & error formatter
 *
 * Verified two ways: runtime `console.assert`s (via tsx) AND `tsc --noEmit`, where the
 * `@ts-expect-error` lines prove the compiler rejects the illegal cases.
 */

// ── Identity: branded IDs (Phase 7.4) ──────────────────────────────────────
type Brand<T, B> = T & { readonly __brand: B };
type ExpenseId = Brand<string, "ExpenseId">;
let idSeq = 0;
const newExpenseId = (): ExpenseId => `exp_${++idSeq}` as ExpenseId;

// ── Categories: derive a union from an as-const array (Phase 2.2) ───────────
const CATEGORIES = ["food", "transport", "housing", "leisure", "other"] as const;
type Category = (typeof CATEGORIES)[number]; // "food" | ... | "other"

// ── The domain entity ──────────────────────────────────────────────────────
interface Expense {
  readonly id: ExpenseId; // assign-once identity
  description: string;
  amountCents: number; // integer cents — never floats for money
  category: Category;
  readonly createdAt: number; // epoch ms
}

// ── DTOs derived by utility types (Phase 5.3) — single source of truth ──────
// Creating: caller supplies everything the system doesn't own.
type NewExpense = Omit<Expense, "id" | "createdAt">;
// Patching: any subset of the mutable fields.
type ExpensePatch = Partial<Pick<Expense, "description" | "amountCents" | "category">>;

// ── Typed errors + Result (Phase 4.4, 7.3) ─────────────────────────────────
type DomainError =
  | { code: "NOT_FOUND"; id: ExpenseId }
  | { code: "INVALID_AMOUNT"; amountCents: number }
  | { code: "INVALID_CATEGORY"; value: string };

type Result<T, E = DomainError> = { ok: true; value: T } | { ok: false; error: E };
const ok = <T>(value: T): Result<T, never> => ({ ok: true, value });
const fail = <E>(error: E): Result<never, E> => ({ ok: false, error });

function formatError(e: DomainError): string {
  switch (e.code) {
    case "NOT_FOUND":
      return `expense ${e.id} not found`;
    case "INVALID_AMOUNT":
      return `invalid amount: ${e.amountCents}`;
    case "INVALID_CATEGORY":
      return `invalid category: ${e.value}`;
    default: {
      const _exhaustive: never = e; // compile error if a new error code is unhandled
      return _exhaustive;
    }
  }
}

// ── A reusable generic store (Phase 4) ──────────────────────────────────────
interface HasId {
  readonly id: string;
}
class InMemoryStore<T extends HasId> {
  private readonly rows = new Map<string, T>();
  put(entity: T): void {
    this.rows.set(entity.id, entity);
  }
  get(id: string): T | undefined {
    return this.rows.get(id);
  }
  delete(id: string): boolean {
    return this.rows.delete(id);
  }
  list(): readonly T[] {
    return [...this.rows.values()];
  }
}

// ── The service: only place that mutates, returns Result at the boundary ────
class ExpenseService {
  private readonly store = new InMemoryStore<Expense>();

  private validate(amountCents: number, category: string): DomainError | null {
    if (!Number.isInteger(amountCents) || amountCents <= 0)
      return { code: "INVALID_AMOUNT", amountCents };
    if (!CATEGORIES.includes(category as Category))
      return { code: "INVALID_CATEGORY", value: category };
    return null;
  }

  create(input: NewExpense): Result<Expense> {
    const problem = this.validate(input.amountCents, input.category);
    if (problem) return fail(problem);
    const expense: Expense = {
      id: newExpenseId(),
      createdAt: Date.now(),
      description: input.description,
      amountCents: input.amountCents,
      category: input.category,
    };
    this.store.put(expense);
    return ok(expense);
  }

  update(id: ExpenseId, patch: ExpensePatch): Result<Expense> {
    const existing = this.store.get(id);
    if (!existing) return fail({ code: "NOT_FOUND", id });
    const merged: Expense = { ...existing, ...patch };
    const problem = this.validate(merged.amountCents, merged.category);
    if (problem) return fail(problem);
    this.store.put(merged);
    return ok(merged);
  }

  remove(id: ExpenseId): Result<ExpenseId> {
    return this.store.delete(id) ? ok(id) : fail({ code: "NOT_FOUND", id });
  }

  all(): readonly Expense[] {
    return this.store.list();
  }

  totalByCategory(): Record<Category, number> {
    // Record<Category, number> guarantees every category key is present.
    const totals = Object.fromEntries(CATEGORIES.map((c) => [c, 0])) as Record<Category, number>;
    for (const e of this.store.list()) totals[e.category] += e.amountCents;
    return totals;
  }
}

// ── Analytics event log: a discriminated union (Phase 5.1) ──────────────────
type ExpenseEvent =
  | { type: "created"; id: ExpenseId; amountCents: number }
  | { type: "updated"; id: ExpenseId; delta: number }
  | { type: "removed"; id: ExpenseId };

// A reducer that folds events into a running total — exhaustively handled.
function applyEvent(total: number, ev: ExpenseEvent): number {
  switch (ev.type) {
    case "created":
      return total + ev.amountCents;
    case "updated":
      return total + ev.delta;
    case "removed":
      return total; // removal handled elsewhere; total unaffected here
    default: {
      const _exhaustive: never = ev;
      return _exhaustive;
    }
  }
}

// ═══ Assertions ═════════════════════════════════════════════════════════════
const svc = new ExpenseService();

// create — happy path
const created = svc.create({ description: "Lunch", amountCents: 1250, category: "food" });
console.assert(created.ok, "create succeeds for valid input");
const lunchId = created.ok ? created.value.id : newExpenseId();

// create — validation failures return typed errors (no throw)
const badAmount = svc.create({ description: "x", amountCents: -5, category: "food" });
console.assert(!badAmount.ok && badAmount.error.code === "INVALID_AMOUNT", "negative amount rejected");
const badCat = svc.create({ description: "x", amountCents: 100, category: "crypto" as Category });
console.assert(!badCat.ok && formatError(badCat.error) === "invalid category: crypto", "bad category rejected");

// update — merge + re-validate
svc.create({ description: "Bus", amountCents: 300, category: "transport" });
const updated = svc.update(lunchId, { amountCents: 1500 });
console.assert(updated.ok && updated.value.amountCents === 1500, "update patches and re-validates");

// update — not found
const missing = svc.update(newExpenseId(), { description: "ghost" });
console.assert(!missing.ok && missing.error.code === "NOT_FOUND", "update of missing id -> NOT_FOUND");

// analytics: totalByCategory covers every category key (Record guarantee)
const totals = svc.totalByCategory();
console.assert(totals.food === 1500 && totals.transport === 300 && totals.leisure === 0, "totalByCategory");

// event reducer
const eventLog: ExpenseEvent[] = [
  { type: "created", id: lunchId, amountCents: 1500 },
  { type: "updated", id: lunchId, delta: -200 },
  { type: "removed", id: lunchId },
];
const runningTotal = eventLog.reduce(applyEvent, 0);
console.assert(runningTotal === 1300, "event reducer folds to running total");

// ── Type-level guarantees the compiler enforces (would fail tsc if wrong) ───
// @ts-expect-error — a raw string is not an ExpenseId (branded identity)
svc.remove("exp_1");
// @ts-expect-error — NewExpense must not include a system-owned `id`
svc.create({ id: newExpenseId(), description: "x", amountCents: 1, category: "food" });
// @ts-expect-error — can't read `.value` on a Result without checking `.ok` first
created.value;

console.log("Capstone: fully-typed expense domain — all assertions passed.");
console.log(`  running total from events = ${runningTotal}¢, food total = ${totals.food}¢`);

export {}; // module scope
