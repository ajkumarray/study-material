<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · app router](../phase-1-app-router/NOTES.md) | [Phase 3 · routing ➡](../phase-3-routing/NOTES.md)
<!-- /nav -->

# Phase 2 — Rendering: Server vs Client Components: Notes

This is the defining feature of the App Router. Components are **Server Components by
default**; you opt specific subtrees into the client with `"use client"`. Getting this
boundary right is the whole game — most Next.js performance and architecture questions
come back to "where did you draw the client boundary, and why."

## 1. React Server Components (RSC)

**Definition.** A Server Component is a component that runs **only on the server**
(at request time or build time), renders to a serialized description of the UI, and
streams that description to the client — its component code is **never sent to the
browser**. In the App Router, every component is a Server Component unless you
explicitly opt it out with `"use client"`.

- **Zero client JS cost**: the component's own code (and its server-only
  dependencies) never end up in the JavaScript bundle shipped to the browser. For
  static or display-only UI, this means literally 0 bytes of JS for that component.
- **Direct server access**: a Server Component can read a database, the filesystem,
  or environment secrets directly in its body — no API layer needed in between, and
  those secrets never reach client code because the component itself never reaches
  the client.
- **Can be `async`**: a Server Component function can be `async function`, and you
  `await` data directly inside it (Phase 4) — no `useEffect`, no `useState` for a
  loading flag, no client-side data-fetching library needed for initial data.
- **No interactivity**: Server Components **cannot** use `useState`, `useEffect`,
  event handlers (`onClick`, etc.), or any browser-only API, because none of that
  code ever runs in a place with a `window`, a DOM event loop, or a React
  reconciliation client-side.

```tsx
// app-demo/app/expenses/page.tsx — an ASYNC Server Component
export default async function ExpensesPage() {
  const expenses = await getExpenses(); // runs on the server; no useEffect needed
  const total = totalCents(expenses);
  return (
    <section>
      <h2>All expenses</h2>
      <ul>
        {expenses.map((e) => (
          <li key={e.id}>{e.description} — ${(e.amountCents / 100).toFixed(2)}</li>
        ))}
      </ul>
      <p>Total: ${(total / 100).toFixed(2)}</p>
    </section>
  );
}
```

In this example: `ExpensesPage` fetches its own data by simply `await`ing
`getExpenses()` in the function body. There is no loading spinner state, no
`useEffect(() => { fetch(...) }, [])`, and no risk of the client briefly rendering an
empty list before data arrives — by the time this component's output reaches the
browser, the data is already baked into it. None of `getExpenses`'s code (which in a
real app might contain a DB connection string) is ever sent to the browser either.

## 2. Client Components (`"use client"`)

**Definition.** Adding the `"use client"` directive as the very first line of a file
marks that module — and everything it imports — as Client Component code: it's
still rendered once on the server to produce the initial HTML, but it's *also*
shipped as JavaScript, hydrated in the browser, and re-rendered there for any
subsequent interaction.

- **Interactivity requires it**: `useState`, `useEffect`, `useContext` (for
  client-only providers), event handlers, and any browser API (`localStorage`,
  `window`, etc.) only work in a Client Component.
- **The boundary is contagious downward**: everything a Client Component *imports*
  becomes part of the client bundle too, even if that imported thing doesn't itself
  use any client-only feature. Putting `"use client"` too high in the tree drags a
  large subtree of otherwise-static UI into the JS bundle.
- **Think "islands"**: in an App Router page, Client Components are small
  interactive islands inside an otherwise server-rendered page — not the whole page,
  the way a traditional SPA would be.

```tsx
// app-demo/components/AddExpenseForm.tsx — a Client Component "island"
"use client";
import { useState } from "react";

export function AddExpenseForm({ action }: { action: (fd: FormData) => Promise<void> }) {
  const [open, setOpen] = useState(false); // useState requires "use client"

  if (!open) {
    return <button onClick={() => setOpen(true)}>+ Add expense</button>;
  }
  return (
    <form action={action}>
      <input name="description" required />
      {/* ...other fields... */}
      <button type="submit">Save</button>
    </form>
  );
}
```

In this example: `useState` and the `onClick` handler are exactly why this file needs
`"use client"` — neither works in a Server Component. Everything else on the
`/expenses` page (the list, the total, the page shell) stays a Server Component; only
this small form island ships JS and hydrates.

## 3. Crossing the server/client boundary

- A Server Component **can** render a Client Component and pass it props — but only
  **serializable** props: primitives, plain objects/arrays, and (specially) Server
  Actions. Functions in general do **not** serialize across the boundary — you can't
  pass an arbitrary client-side callback down from a Server Component, because there's
  no way to send a function reference over the wire. Server Actions are the one
  exception: Next generates a secure reference Next can invoke back on the server.
- A Client Component **cannot** `import` a Server Component directly — doing so would
  try to pull server-only code into the client bundle, which Next disallows. Instead,
  a Server Component higher in the tree renders the Server Component and passes it
  down to the Client Component as `children` (or any prop) — this is the **"slot"
  pattern**, and it keeps server-only code out of the bundle while still letting a
  Client Component visually "wrap" server-rendered content.

```tsx
// app-demo/app/expenses/page.tsx wires this up exactly:
export default async function ExpensesPage() {
  const expenses = await getExpenses();       // Server Component: fetches data
  return (
    <section>
      {/* ...server-rendered list... */}
      <AddExpenseForm action={addExpenseAction} /> {/* Client island, gets a Server Action prop */}
    </section>
  );
}
```

In this example: `addExpenseAction` (a `"use server"` function, Phase 4) is passed as
a prop into a Client Component. This is legal specifically because Server Actions are
serializable as a special reference — the Client Component can call `action(formData)`
and Next routes that call back to the server, without the action's actual code ever
being shipped to the browser.

| | Server Component | Client Component |
|---|---|---|
| Runs on server | Yes, always | Yes, once (for initial HTML) |
| Runs in browser | Never | Yes, after hydration |
| Can be `async`/`await` data directly | Yes | No (use hooks/effects instead) |
| `useState`/`useEffect`/event handlers | No | Yes |
| Direct DB/filesystem/secret access | Yes | No |
| Ships JS to the browser | No | Yes |
| Can import a Server Component | Yes (it is one) | No — receive it as a prop/children instead |
| Can be imported by a Client Component | No | Yes |

## 4. Rendering strategies

Next chooses — per route, automatically, unless you override it — how each route's
HTML gets produced:

- **Static (SSG)** — rendered **once, at build time**, to plain HTML that can be
  served from a CDN with no server work per request. Fastest possible response; used
  when the content is the same for every visitor. This is Next's default when a route
  reads no request-time data.
- **Dynamic (SSR)** — rendered **on every request**, on the server. Used for
  per-request or per-user data, or whenever the route reads something request-time
  (cookies, headers, `searchParams`, an uncached `fetch`).
- **ISR (Incremental Static Regeneration)** — static, but **revalidated** on a timer
  (`revalidate: N` seconds) or on demand (`revalidatePath`/`revalidateTag`, Phase 4).
  You get CDN-level speed with periodic or event-driven freshness instead of
  rebuilding the whole app.
- **Streaming** — the server sends the HTML **shell** immediately and streams in the
  slower parts of the page as they finish rendering, each wrapped in a `<Suspense>`
  boundary (`loading.tsx` from Phase 1 is exactly this). This shortens time-to-first-byte
  and stops one slow data source from blocking an entire page.

**How Next actually decides, and how to prove it**: Next statically analyzes a route.
If nothing in it uses a "dynamic API" (`cookies()`, `headers()`, `searchParams`, an
uncached `fetch`), Next renders it once at build time and marks it static. Running
`next build` in `app-demo/` shows this decision explicitly:

```
Route (app)
┌ ○ /
├ ○ /_not-found
├ ƒ /api/expenses
├ ○ /expenses
└ ƒ /expenses/[id]

○  (Static)   prerendered as static content
ƒ  (Dynamic)  server-rendered on demand
```

In this example: `/` and `/expenses` are both marked **static (○)** — even though
`ExpensesPage` is an `async` component that `await`s `getExpenses()`. That's not a
contradiction: `getExpenses()` is a plain async function doing a simulated delay, not
a call to Next's instrumented `fetch`, and the component reads no dynamic API — so
there's nothing to tell Next this route needs to be re-rendered per request, and it
gets prerendered once at build time. `/expenses/[id]` is marked **dynamic (ƒ)**
because it's a dynamic segment with no `generateStaticParams` (Phase 3) supplying a
known set of ids ahead of time, so Next has to render it on demand per request.
`/api/expenses` is marked **dynamic (ƒ)** too — as of Next 15+, Route Handlers'
`GET` functions are no longer cached/prerendered by default the way they were in
earlier Next versions (this is exactly the kind of default that changes between major
versions and is worth re-checking against the installed `next` version rather than
assuming).

**Consequence worth internalizing**: because `/expenses` is static, the list of
expenses baked into its HTML is frozen at build time — the demo's `addExpenseAction`
(Phase 4) calling `revalidatePath("/expenses")` after a write is not a nice-to-have,
it is the *only* way the statically-rendered `/expenses` page ever reflects a new
row without a full rebuild. This is ISR-by-mutation in practice, not just theory.

| Strategy | When rendered | Speed | Freshness | Next 16 trigger |
|---|---|---|---|---|
| Static (SSG) | Once, at build time | Fastest (CDN) | Frozen until rebuild/revalidate | Default when no dynamic API is used |
| Dynamic (SSR) | Every request | Slower (server round trip) | Always fresh | `cookies()`, `headers()`, `searchParams`, uncached `fetch`, dynamic segment w/o `generateStaticParams` |
| ISR | Build time + periodic/on-demand refresh | CDN speed, mostly | Fresh within `revalidate` window or on trigger | `revalidate` option, `revalidatePath`/`revalidateTag` |
| Streaming | Progressive, per request | Fast time-to-first-byte | N/A (orthogonal to above) | A `<Suspense>` boundary / `loading.tsx` around a slow subtree |

You can force a strategy explicitly with **route segment config**, exported from a
`page.tsx`/`layout.tsx`:

```ts
export const dynamic = "force-static";   // always static, even if it reads dynamic data
export const dynamic = "force-dynamic";  // always render per request
export const revalidate = 60;            // ISR: revalidate at most every 60s
```

## 5. Hydration

**Definition.** Hydration is the process where React, in the browser, takes the
server-rendered HTML already on the page and attaches event listeners and internal
state to it, turning static markup into an interactive React tree — without
re-creating the DOM from scratch.

- Only Client Components hydrate. Server Components have no client-side counterpart
  to hydrate — their output is just HTML (well, technically an RSC payload the client
  reconstructs into that HTML), with no matching client JS waiting to attach to it.
- This is why keeping the `"use client"` boundary small directly reduces hydration
  cost: less JS shipped means less work the browser does turning markup into a live
  app, which matters most on slow devices/networks.

## Why this model is useful

The model inverts the default SPA architecture. A traditional SPA ships all component
code to the browser and renders everything there, meaning: a big JS bundle before
anything is interactive, a blank/loading first paint while JS boots, weak SEO unless
you bolt on separate SSR, and a client-side data-fetching waterfall (component mounts
→ `useEffect` fires → fetch starts → data arrives → re-render). RSC flips this: render
as much as possible on the server (fast, secure — secrets and heavy dependencies never
leave the server — and SEO-friendly, since crawlers get real content), and ship only
the interactive leaves as client JS. The `/expenses` page in this demo is a working
example: the list and total are zero-JS server output; only the "Add expense" form is
a hydrated island.

## Summary — key takeaways

- **Server Components are the default**; they run only on the server, can be `async`,
  and ship zero JS — but cannot use state, effects, or event handlers.
- **`"use client"`** opts a file (and its import subtree) into being hydrated,
  interactive client code — reach for it only at the leaves that actually need
  interactivity.
- Crossing the boundary: only **serializable props** (plus Server Actions) pass from
  Server → Client; a Client Component can't `import` a Server Component, but can
  receive one as `children`/props (the slot pattern).
- Rendering strategy (**static / dynamic / ISR**) is decided **per route**, inferred
  from whether the route uses a dynamic API — verify this against real `next build`
  output rather than assuming, since a component that merely `await`s data (without
  touching `cookies()`/`headers()`/`searchParams`/uncached `fetch`) can still come out
  **static**.
- **Streaming** via `<Suspense>`/`loading.tsx` lets a fast shell render immediately
  while slow parts stream in, instead of one slow query blocking the whole page.
- The overall goal: push rendering and data work to the server by default, and place
  the `"use client"` boundary as **low** in the tree as possible.
