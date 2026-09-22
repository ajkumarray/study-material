<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · routing](../phase-3-routing/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Data Fetching & Mutations: Interview Q&A

⭐ = asked constantly.

**Q: How do you fetch data in the App Router?** ⭐⭐
In an async Server Component, `await` the data source directly in the component body
— a database call, an internal service call, or `fetch` to an external API. It runs
entirely on the server, so there's no client-side loading state to manage and no risk
of exposing secrets (a DB connection string, an API key) to the browser, since that
code never ships there. The data is already resolved by the time the component's
output reaches the client — no `useEffect` is needed for initial data at all.

```tsx
export default async function ExpensesPage() {
  const expenses = await getExpenses(); // runs server-side, resolved before render
  return <ul>{expenses.map(e => <li key={e.id}>{e.description}</li>)}</ul>;
}
```

*Follow-up: "Would you ever still need `useEffect` for data in a Next App Router
app?"* Yes — for data that depends on client-side state/interaction after the
initial load (e.g. refetching on a client-side filter change in a Client Component),
or for genuinely client-only data (something read from `localStorage`). Initial
page data doesn't need it; interaction-driven refetches in a Client Component still
do, or you'd combine a Server Action / Route Handler call with client state.

**Q: How does Next's extended `fetch` caching work, and what's the default on the
version this repo uses?** ⭐⭐ *version-specific*
Next augments the global `fetch` with its own cache layer. Critically, **as of
Next.js 15 (unchanged in this repo's 16.2.12), a plain `fetch` call is not cached by
default** — this reversed the pre-15 default, where `fetch` was cached unless you
opted out. You still get request-scoped deduping (the same `fetch` called twice while
rendering one request only goes over the network once), but that's not the same as
caching across requests. To opt into caching you pass `{ cache: "force-cache" }`
explicitly; `{ cache: "no-store" }` is explicitly always-fresh; `{ next: { revalidate:
N } }` caches for `N` seconds then refreshes (ISR); `{ next: { tags: [...] } }` tags
the cached entry for later on-demand invalidation with `revalidateTag`.

*Follow-up: "Why would Next reverse a default like that?"* Cached-by-default `fetch`
was a frequent source of "why is my data stale" bug reports — developers expected
`fetch` to behave like the browser's normal `fetch` (always fresh) and were surprised
it was silently cached. Making caching opt-in matches the more intuitive mental model
and pushes teams to be deliberate about performance/freshness trade-offs instead of
hitting them by accident.

**Q: What is a Route Handler?** ⭐
A `route.ts` file exporting functions named for HTTP methods (`GET`, `POST`, `PUT`,
`DELETE`, …), built on the standard Web `Request`/`Response` APIs — this is how you
expose a JSON/REST API from inside a Next app. It's used for webhooks, public APIs
consumed by non-React clients, or anywhere you need general HTTP semantics rather
than a React-UI-triggered mutation. A folder can't have both a `route.ts` and a
`page.tsx` — it's one or the other.

```ts
export async function GET() {
  const expenses = await getExpenses();
  return NextResponse.json(expenses);
}
export async function POST(request: Request) {
  const body = await request.json();
  if (!body.description) return NextResponse.json({ error: "invalid" }, { status: 400 });
  const created = await addExpense(body);
  return NextResponse.json(created, { status: 201 });
}
```

*Follow-up: "Is a `GET` Route Handler cached by default?"* Not on this version — as
of Next 15+, `GET` Route Handlers are no longer prerendered/cached by default the way
they were in older Next releases; this repo's `/api/expenses` confirms it, coming out
marked dynamic (`ƒ`) in a real `next build`. You can still opt a specific handler into
static behavior if its response is safe to cache.

**Q: What is a Server Action?** ⭐⭐
An async function marked with the `"use server"` directive that always runs on the
server but can be **called directly from client code** — a form's `action` prop, or a
direct call from a Client Component's event handler — with Next generating the secure
request/response plumbing so you don't hand-write an API route or a client-side
`fetch` call for the mutation. Wired to `<form action={myAction}>`, it receives the
submitted `FormData` and, because it's invoked through the form's native `action`
attribute, it works even before client JS has finished loading — **progressive
enhancement**.

```ts
"use server";
export async function addExpenseAction(formData: FormData): Promise<void> {
  const description = String(formData.get("description") ?? "").trim();
  if (!description) return;
  await addExpense({ description, /* ... */ });
  revalidatePath("/expenses");
}
```

**Q: Server Actions vs Route Handlers — when do you reach for each?** ⭐
Server Actions for mutations triggered by your own React UI: forms and buttons in the
app you're building, where you want minimal boilerplate, progressive enhancement, and
tight integration with cache revalidation (`revalidatePath`/`revalidateTag` called
right inside the action). Route Handlers for a general HTTP API surface: anything
consumed by an external client, another service, a webhook sender, or code that isn't
your React UI and needs to talk over standard REST semantics/verbs/status codes. In
this repo, `AddExpenseForm` uses a Server Action for its own form, while
`app/api/expenses/route.ts` exists as a separate, independently-callable REST
endpoint doing the same underlying work — both are valid; they serve different
callers.

**Q: After a mutation, how does the UI end up showing the new data?** ⭐⭐
The Server Action (or handler) calls `revalidatePath(path)` or `revalidateTag(tag)`
to invalidate whatever cached render depended on that data, so the next request for
that path regenerates it with fresh data. `redirect()` can additionally navigate the
user somewhere after a successful write (e.g. to a newly-created resource's page).
This repo's `addExpenseAction` calls `revalidatePath("/expenses")` after adding an
expense — and that call isn't optional decoration: `/expenses` itself renders
*statically* (confirmed by real `next build` output), so without the explicit
revalidation, a newly added row would never appear on that page without a full
rebuild.

*Follow-up: "What's the difference between `revalidatePath` and `revalidateTag`?"*
`revalidatePath` invalidates the cached render for one specific route path.
`revalidateTag` invalidates every cached `fetch` result anywhere in the app that was
tagged with that tag (via `next: { tags: [...] }`) — useful when several different
routes/components all depend on the same underlying data and you want to invalidate
all of them in one call rather than enumerating every path.

**Q: Are Server Actions secure by default?** ⭐⭐
No — a Server Action is, under the hood, a callable network endpoint, even though
you invoke it like a local function from your React code. Anyone who can reach your
app can, in principle, invoke the action directly, bypassing your form/UI entirely.
You must **validate and authorize inside the action itself** — never assume `formData`
contains exactly what your form would have sent, and never assume the caller is
authenticated/authorized just because the call "looks like" it came from your app.
Treat every argument as untrusted input, the same discipline you'd apply to any other
server endpoint — ideally parsed against a schema rather than trusted ad hoc.

**Q: How does Next relate to a separate backend like Spring Boot?** *nuance*
Two valid setups. Either Next **is** the backend — Server Components/Actions/Route
Handlers talk directly to the database, which is what this repo's demo does (with an
in-memory store standing in for a real DB). Or Next is a **BFF** (backend-for-frontend)
that calls an existing service, like the Spring Boot `expense-api` from track 02,
over HTTP — but that call happens server-to-server, inside a Server
Component/Action/Handler, so auth tokens and internal service URLs never reach the
browser; the browser only ever talks to Next. You'd choose the BFF approach when
another service already owns the domain/business logic and you want Next purely for
UI plus server rendering, rather than duplicating business rules in two places.

**Q: How do you avoid data-fetching waterfalls in the App Router?**
Use `Promise.all` to fetch independent data sources concurrently rather than awaiting
them one after another — sequential `await`s make the second fetch wait for the
first even when they don't depend on each other. Separately, wrap independent slow
segments of a page in their own `<Suspense>` boundary (`loading.tsx`) so one slow
data source streams in on its own timeline instead of blocking the rest of the page
from appearing. Colocating fetches in Server Components in the first place already
removes the classic *client-side* SPA waterfall (mount → `useEffect` → fetch →
re-render); these two techniques address the remaining *server-side* waterfall risk.
