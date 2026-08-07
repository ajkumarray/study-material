<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · routing](../phase-3-routing/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Data Fetching & Mutations: Notes

How data flows in and out. The App Router's answer: **fetch in Server Components**,
**mutate with Server Actions**, and build APIs with **Route Handlers** — all on the
server, all typed.

## 4.1 — Fetching in Server Components

- **Just `await` it.** An async Server Component fetches data directly in its body
  (`const expenses = await getExpenses()` in the demo). No `useEffect`, no client
  loading state, no exposed API keys. Data arrives in the initial HTML.
- **The extended `fetch` cache.** Next augments the global `fetch` with caching:
  - `fetch(url)` — cached by default in many cases; dedupe within a render.
  - `fetch(url, { cache: "no-store" })` — always fresh (dynamic).
  - `fetch(url, { next: { revalidate: 60 } })` — ISR: cache for 60s, then revalidate.
  - `fetch(url, { next: { tags: ["expenses"] } })` — tag it for on-demand invalidation.
- **No waterfalls:** fetch in parallel with `Promise.all`, or let independent segments
  stream via `<Suspense>` so a slow query doesn't block the fast ones.
- Fetching lives **next to the component that needs it** (colocation), not in a
  separate data layer that must be threaded through props.

## 4.2 — Route Handlers (building an API)

- A **`route.ts`** exports functions named for HTTP methods (`GET`, `POST`, `PUT`,
  `DELETE`, …) using the Web **`Request`/`Response`** APIs (the demo's
  `app/api/expenses/route.ts`). This is how you expose a JSON/REST API from the same
  project.
- Use them for: webhooks, third-party callbacks, public APIs, or endpoints consumed by
  external clients / non-React code. `NextResponse.json(data, { status })` is the
  common return.
- **Route Handlers vs Server Actions:** handlers are general HTTP endpoints (any
  client, any method, REST semantics); Server Actions are for **mutations invoked from
  your own React UI**. For in-app form submits, prefer Server Actions; for an API
  surface, use handlers.

## 4.3 — Server Actions (mutations)

- **`"use server"`** marks an async function as a **Server Action**: it runs on the
  server but can be **called directly from client code** (Next generates the secure
  RPC). No manual endpoint, no `fetch` boilerplate for mutations.
- Wire to a form: `<form action={addExpenseAction}>` — the action receives `FormData`
  and runs on submit (works even before JS loads — **progressive enhancement**). Or
  call from a client handler.
- **Revalidation after a mutation:** call **`revalidatePath("/expenses")`** or
  **`revalidateTag("expenses")`** to invalidate the cached render so the UI reflects
  the change (the demo does this). `redirect()` can navigate afterward.
- **Security:** a Server Action is a public endpoint — **validate and authorize inside
  it** (never trust the client). Treat args as untrusted input (parse with a schema —
  TS Phase 7.1).

## Where Next meets the Spring Boot backend (02)

Two valid architectures: (a) Next **is** the backend — Server Components/Actions/Route
Handlers talk straight to the DB; (b) Next is a **BFF/frontend** that calls the Spring
`expense-api` over HTTP (Server Components `fetch` it server-side, keeping tokens off
the client). Choose (b) when the Java service owns the domain/business logic and you
want Next only for the UI + server rendering.

## Perspective

The unifying idea: **do data work on the server, close to the component**. Reads are
`await`ed in Server Components with a caching `fetch`; writes are Server Actions that
then revalidate. Route Handlers remain for true API surfaces. You rarely write a
client-side `useEffect(fetch)` anymore — and secrets, tokens, and heavy queries never
reach the browser.
