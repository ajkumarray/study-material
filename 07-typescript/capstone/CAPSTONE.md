# Capstone — A Fully-Typed Expense-Tracker Domain

The TypeScript track's synthesis: one small domain (`expenseDomain.ts`) that puts
**every phase to work at once**, on the repo's running expense-tracker theme (the
Spring `expense-api` and React `expense-web`). It is verified **two ways** — runtime
`console.assert`s via `tsx`, and `tsc --noEmit`, where the `@ts-expect-error` lines
prove the compiler *rejects* the illegal cases.

```bash
cd 07-typescript
npx tsx capstone/expenseDomain.ts   # run the assertions
npm run typecheck                   # type-check the whole track (incl. the capstone)
```

## What it builds

A minimal but realistic expense domain:

- **`Expense`** entity — branded `id`, integer `amountCents` (never float money),
  `category` from a fixed set, `createdAt`.
- **`ExpenseService`** — the only mutator; `create` / `update` / `remove` /
  `totalByCategory`, each returning a **`Result`** instead of throwing.
- **`InMemoryStore<T extends HasId>`** — a reusable generic store (swap for a real
  repo without touching the service — Dependency Inversion, Software Design Phase 6).
- **`ExpenseEvent`** analytics log + an exhaustive **reducer** folding events to a
  running total.

## Each phase, applied

| Track phase | Concept | Where it shows up in the capstone |
|---|---|---|
| 2.2 | `as const` → derived union | `CATEGORIES` array is the single source; `Category` is derived from it |
| 4 | Generics + constraints | `InMemoryStore<T extends HasId>`, generic `ok`/`fail` |
| 4.4 / 7.3 | `Result<T,E>` + typed errors | every service method; `DomainError` discriminated union |
| 5.1 | Discriminated union + exhaustive `never` | `ExpenseEvent` reducer & `formatError` (`never` catches unhandled cases) |
| 5.3 | Utility types derive DTOs | `NewExpense = Omit<…>`, `ExpensePatch = Partial<Pick<…>>`, `Record<Category, number>` |
| 7.2 | Make illegal states unrepresentable | can't `create` with a system-owned `id`; can't read `.value` before checking `.ok` |
| 7.4 | Branded identity | `ExpenseId`; a raw `string` is rejected where an `ExpenseId` is required |

## The guarantees the compiler enforces (the `@ts-expect-error` lines)

These are the payoff — bugs that **cannot compile**:

```ts
svc.remove("exp_1");                       // ✗ a raw string is not an ExpenseId
svc.create({ id, description, amount… });   // ✗ NewExpense forbids a system-owned id
created.value;                              // ✗ can't read .value before narrowing .ok
```

Plus, invisibly: `totalByCategory` returns `Record<Category, number>`, so **every**
category key is guaranteed present (no `undefined` totals); and adding a new
`DomainError` code or `ExpenseEvent` type makes the `never` branch fail to compile
until you handle it — the compiler becomes your TODO list.

## The through-line

The whole track builds to one idea: **push correctness into the type system so whole
classes of bug are caught before the code runs.** Validate at the boundary (types are
erased — Phase 7.1), then model the domain so illegal states, wrong IDs, unhandled
cases, and unchecked failures are *compile errors*, not runtime surprises. Same goal
as the Software Design track — "make the wrong thing impossible" — now enforced
mechanically by `tsc`.

## Where it goes next

This typed domain is exactly the shape a **React (08)** app or **Next.js (09)** route
would consume: the DTOs become component props, `Result` drives the request-state
discriminated union (Phase 7.2), and the branded IDs flow through the UI. TypeScript
is the connective tissue for the rest of the frontend tracks.
