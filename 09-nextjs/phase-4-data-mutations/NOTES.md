<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · routing](../phase-3-routing/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Data Fetching & Mutations: Notes

How data flows in and out of the App Router. The pattern: **fetch in Server
Components**, **mutate with Server Actions**, and build general-purpose APIs with
**Route Handlers** — all server-side, all typed, with no client-side data-fetching
library required for the basics.

## 1. Fetching in Server Components

**Definition.** An async Server Component fetches data by simply `await`ing it
directly in the function body — the data source can be a database call, an internal
service call, or `fetch` to an external API.

- **No `useEffect`, no loading state plumbing**: by the time the component's output
  reaches the client, the data is already resolved and baked into the render. There's
  no "mount, then fetch, then re-render" cycle to manage.
- **No exposed secrets**: since the component and its data-fetching code never leave
  the server, a database connection string or an internal API's auth token used
  inside it never reaches the browser.
- **Colocation**: the fetch lives directly next to the component that needs the data,
  not routed through a separate global data layer that has to be threaded down
  through props from a page-level component.

```tsx
// app-demo/app/expenses/page.tsx
import { getExpenses, totalCents } from "@/lib/data";

export default async function ExpensesPage() {
  const expenses = await getExpenses(); // "fetch" — here, an async in-memory lookup
  const total = totalCents(expenses);
  return (/* render expenses + total */);
}
```

In this example: `getExpenses()` in `app-demo/lib/data.ts` simulates async I/O
(`await new Promise(r => setTimeout(r, 10))`) the way a real database driver call
would behave. The component doesn't know or care whether the underlying call is a
DB query, an internal service call, or a `fetch` — it just `await`s it. This is the
same shape you'd use against a real Postgres/Prisma/HTTP call.

## 2. The extended `fetch` cache

**Definition.** Next augments the global `fetch` API with its own caching layer, on
top of what the browser's normal `fetch` does — controlled through options on the
`fetch` call itself.

- **`fetch(url)`** — as of Next.js 15+ (this repo runs 16.2.12), a plain `fetch` call
  is **not cached by default** — this is a real breaking-default change from Next 14
  and earlier, where `fetch` was cached (`force-cache`) unless told otherwise.
  Requests are still **deduplicated** within a single render pass (calling the same
  `fetch` twice while rendering one request only issues one network call), but that's
  request-scoped deduping, not cross-request caching.
- **`fetch(url, { cache: "force-cache" })`** — explicitly opt back into caching the
  result indefinitely (until a deploy or a manual revalidation).
- **`fetch(url, { cache: "no-store" })`** — explicitly always fresh, forces the route
  dynamic if used in a Server Component render path.
- **`fetch(url, { next: { revalidate: 60 } })`** — ISR-style: cache the result, but
  treat it as stale after 60 seconds and refetch on the next request past that window.
- **`fetch(url, { next: { tags: ["expenses"] } })`** — tag the cached result so it can
  be invalidated on demand later with `revalidateTag("expenses")`, independent of any
  time-based expiry.

> **Version note**: this repo's `app-demo/lib/data.ts` doesn't call `fetch` at all —
> it's an in-memory array with a simulated delay, standing in for a real data source —
> so none of the `fetch`-cache options above are directly exercised by the demo code.
> The concepts still apply identically to a real `fetch` call to an external API or to
> the Spring Boot `expense-api` (track 02) if this app were wired to it as a BFF.

| `fetch` option | Behavior | Rendering effect |
|---|---|---|
| *(no options)* — Next 15+/16 default | Not cached; a fresh request each time | Contributes to marking the route dynamic if it depends on request data |
| `{ cache: "force-cache" }` | Cached, effectively indefinitely | Supports static rendering |
| `{ cache: "no-store" }` | Never cached | Forces the route dynamic |
| `{ next: { revalidate: N } }` | Cached for `N` seconds, then refreshed | ISR |
| `{ next: { tags: [...] } }` | Cached, invalidated on demand via `revalidateTag` | Static/ISR + event-driven invalidation |

## 3. Avoiding data-fetching waterfalls

- **Parallel fetches**: if a component needs two independent pieces of data, `await`
  a `Promise.all([fetchA(), fetchB()])` rather than `await fetchA(); await fetchB();`
  sequentially — the second form makes the second fetch wait for the first to finish
  even though they don't depend on each other.
- **Streaming independent segments**: wrap slow, independent parts of a page in their
  own `<Suspense>` boundary (`loading.tsx`, Phase 2/3) so a slow data source doesn't
  block the fast ones from appearing — each segment streams in as its own data
  resolves, instead of the whole page waiting for the slowest piece.
- Colocating fetches in Server Components already removes the classic SPA client-side
  waterfall (mount → effect → fetch → re-render) — these two additional techniques
  address the remaining *server-side* waterfall risk (sequential awaits, one slow
  segment blocking the rest of the page).

## 4. Route Handlers (building an API)

**Definition.** A `route.ts` file exports functions named for HTTP methods (`GET`,
`POST`, `PUT`, `DELETE`, `PATCH`, …) using the standard Web `Request`/`Response` APIs
— this is how the App Router exposes a JSON/REST endpoint from inside a Next project.

```ts
// app-demo/app/api/expenses/route.ts
import { NextResponse } from "next/server";
import { getExpenses, addExpense, type Expense } from "@/lib/data";

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
```

In this example: `GET` returns the full expense list as JSON; `POST` reads and
validates a JSON body, rejects an invalid payload with a `400` and an error message,
and returns the created resource with a `201`. `NextResponse.json(data, { status })`
is the standard way to send a JSON response with a specific status code. Confirmed
against a real `next build` of this repo, `/api/expenses` is classified **dynamic
(`ƒ`)** — as of Next 15+, `GET` Route Handlers are **no longer cached/prerendered by
default** the way they were in earlier Next versions, which matches what you'd want
here since this endpoint reads live (mutable) data.

- **Use Route Handlers for**: webhooks, third-party callbacks, a public API consumed
  by non-React clients (a mobile app, another service), or any endpoint that needs
  general HTTP semantics (specific verbs, custom headers, streaming responses).
- **Route Handlers vs Server Actions**: a handler is a general-purpose HTTP endpoint —
  any client, any method, standard REST semantics, reachable by URL. A Server Action
  is specifically for mutations invoked **from your own React UI** — less boilerplate,
  built-in progressive enhancement, integrated cache revalidation. For an in-app form
  submit, prefer a Server Action; for a real API surface, use a Route Handler.
- A folder **cannot** contain both a `route.ts` and a `page.tsx` (Phase 1) — a segment
  is either an API endpoint or a page, never both.

## 5. Server Actions (mutations)

**Definition.** The `"use server"` directive marks an async function as a **Server
Action**: it always executes on the server, but — unlike a normal server-only
function — it can be invoked **directly from client code** (a form, a button's
`onClick`), with Next generating the secure request/response plumbing behind the
scenes. No hand-written API route and no manual client-side `fetch` call are needed
for the mutation itself.

```ts
// app-demo/app/expenses/actions.ts
"use server";

import { revalidatePath } from "next/cache";
import { addExpense, type Expense } from "@/lib/data";

export async function addExpenseAction(formData: FormData): Promise<void> {
  const description = String(formData.get("description") ?? "").trim();
  const amount = Number(formData.get("amount"));
  const category = String(formData.get("category") ?? "other") as Expense["category"];

  if (!description || !Number.isFinite(amount) || amount <= 0) return;

  await addExpense({ description, amountCents: Math.round(amount * 100), category });

  revalidatePath("/expenses"); // invalidate the cached render so the new row appears
}
```

```tsx
// app-demo/components/AddExpenseForm.tsx (Client Component)
"use client";
export function AddExpenseForm({ action }: { action: (fd: FormData) => Promise<void> }) {
  return (
    <form action={action}>
      <input name="description" required />
      <input name="amount" type="number" step="0.01" required />
      <button type="submit">Save</button>
    </form>
  );
}
```

In this example: `addExpenseAction` reads fields directly off the submitted
`FormData`, validates them (rejecting silently on invalid input — a real app would
surface an error), writes through `addExpense`, then calls `revalidatePath("/expenses")`
so the (statically-rendered — Phase 2) `/expenses` page picks up the new row on the
next render. `<form action={addExpenseAction}>` wires the action to the form directly
— on submit, the browser (or Next's client runtime, once hydrated) invokes the action
with the form's data as a `FormData` object. Because the action is wired through the
native `action` attribute, this **works even before client JS has finished loading** —
**progressive enhancement**: a plain HTML form submit still reaches the server action.

- **Revalidation after a mutation**: `revalidatePath("/expenses")` invalidates the
  cached render for that specific path so subsequent requests regenerate it;
  `revalidateTag("expenses")` invalidates every cached `fetch` tagged with
  `"expenses"`, wherever it's used. `redirect()` (Phase 1) can navigate the user
  after a successful write, e.g. to a new resource's detail page.
- **Security — a Server Action is a public endpoint.** Even though it's called like a
  local function from your React code, Next exposes it as a callable network endpoint
  under the hood — anyone who can reach your app can, in principle, invoke it
  directly (not just through your form). **Always validate and authorize inside the
  action itself**; never assume the caller went through your UI or that `formData`
  contains what your form would have sent. Treat every field the same way you'd treat
  untrusted input to any other server endpoint (ideally parsed with a schema, as
  covered in the TypeScript track).
- React 19 (installed here: `react@19.2.8`) also provides `useActionState` and
  `useFormStatus` (from `react-dom`) as client-side hooks for wiring pending/error UI
  state around a form action — this demo keeps things minimal and doesn't use them,
  but they're the standard tool for showing a "Saving…" state or a validation error
  returned by the action.

| | Server Action | Route Handler |
|---|---|---|
| Invoked from | Your own React UI (`<form action>`, or called directly from a Client Component) | Any HTTP client (browser, curl, mobile app, another service) |
| Boilerplate | Minimal — pass the function as a prop/action | You write the endpoint's request parsing/response shape |
| Progressive enhancement | Yes, via `<form action={...}>` | N/A — it's just an HTTP endpoint |
| Typical use | In-app mutations (add/edit/delete via a form or button) | Public/external API, webhooks, non-React consumers |
| Cache integration | `revalidatePath`/`revalidateTag` called directly inside it | You decide what, if anything, to invalidate |

## 6. Where Next meets a separate backend (e.g. Spring Boot)

Two valid architectures for a real product:

- **(a) Next *is* the backend** — Server Components, Server Actions, and Route
  Handlers talk directly to the database. This is what `app-demo` does (via the
  in-memory `lib/data.ts` standing in for a DB).
- **(b) Next is a BFF (backend-for-frontend)** — it calls an existing service (e.g.
  the Spring Boot `expense-api` from track 02) over HTTP, but that call happens
  **inside a Server Component/Action/Handler**, server-to-server — so auth tokens and
  internal URLs never reach the browser. The browser only ever talks to Next; Next
  talks to the real backend.

Choose (b) when another service already owns the domain/business logic (validation
rules, persistence, authorization) and you want Next purely for the UI layer plus
server rendering — you're not duplicating business logic into two places.

## Why this model is useful

The unifying idea is: **do data work on the server, as close to the component that
needs it as possible.** Reads are `await`ed directly in Server Components with a
caching-aware `fetch` (or an equivalent async data call); writes are Server Actions
that mutate, then explicitly revalidate whatever cached output depended on that data.
Route Handlers remain available for genuine API surfaces. The practical payoff: you
rarely write a client-side `useEffect(() => fetch(...), [])` anymore for initial
data, secrets and heavy queries never reach the browser bundle, and the two most
common bug classes in older architectures — "forgot to show a loading state" and
"the UI is stale after a write" — are addressed by the framework's own primitives
(`loading.tsx` for the first, `revalidatePath`/`revalidateTag` for the second) instead
of hand-rolled state management.

## Summary — key takeaways

- Fetch by **`await`ing directly inside an async Server Component** — no
  `useEffect`, no client loading state, no exposed secrets.
- Next's extended `fetch` is **not cached by default as of Next 15+/16** (a real
  breaking-default change from earlier versions) — opt in with `cache: "force-cache"`,
  go fully fresh with `"no-store"`, or use `next: { revalidate }` / `next: { tags }`
  for ISR-style or tag-based invalidation.
- Avoid waterfalls with **`Promise.all`** for independent parallel fetches and
  **`<Suspense>`/`loading.tsx`** so one slow segment doesn't block the rest of the page.
- **Route Handlers** (`route.ts`) build a general HTTP API; **Server Actions**
  (`"use server"`) are for mutations triggered from your own React UI and get
  progressive enhancement via `<form action={...}>` for free.
- After any mutation, call **`revalidatePath`/`revalidateTag`** to invalidate the
  relevant cached render — this repo's demo does exactly this after adding an expense,
  and it matters because `/expenses` itself renders statically (Phase 2).
- **A Server Action is a public endpoint** — validate and authorize inside it; never
  trust that a request came from your own form.
