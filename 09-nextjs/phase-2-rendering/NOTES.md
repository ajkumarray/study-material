<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · app router](../phase-1-app-router/NOTES.md) | [Phase 3 · routing ➡](../phase-3-routing/NOTES.md)
<!-- /nav -->

# Phase 2 — Rendering: Server vs Client Components: Notes

The defining feature of the App Router. Components are **Server Components by
default**; you opt specific subtrees into the client with `"use client"`. Getting this
boundary right is the whole game.

## 2.1 — React Server Components (RSC)

- A **Server Component** runs **only on the server**, during the request/build. It
  renders to a serialized description that's streamed to the browser — its **code is
  never shipped** to the client. Benefits: zero JS cost for static/display UI, direct
  access to server resources (DB, filesystem, secrets), and no data-fetching waterfall
  to the client.
- Server Components **can be `async`** and `await` data directly in the body (Phase 4)
  — no `useEffect`, no loading spinner plumbing.
- **They cannot** use state/effects (`useState`, `useEffect`), browser APIs, event
  handlers, or React context that depends on those. They have no interactivity.
- Default to Server Components; reach for client only where you need interactivity.

## 2.2 — Client Components (`"use client"`)

- Adding **`"use client"`** at the top of a file marks it (and everything it imports)
  as a Client Component: it's server-rendered for the initial HTML **and** hydrated +
  runs in the browser, so it can use state, effects, event handlers, and browser APIs.
- These are **interactivity islands** in an otherwise server-rendered page (the demo's
  `AddExpenseForm`). Keep them **small and at the leaves** — the boundary is
  contagious downward (imports of a client component are client too), so a high
  `"use client"` drags a big subtree into the bundle.
- **Crossing the boundary:** a Server Component may render a Client Component and pass
  **serializable props** (no functions except Server Actions, no class instances). A
  Client Component **cannot import** a Server Component, but can receive one as
  `children`/props (the "slot" pattern) — this keeps server-only code out of the bundle.

## 2.3 — Rendering strategies

Per route, Next chooses (or you configure) how the HTML is produced:

- **Static (SSG)** — rendered at **build time** to HTML, served from CDN. Fastest,
  cacheable; for content that's the same for all users. Default when a route has no
  dynamic data. (In the demo, `/` is static.)
- **Dynamic (SSR)** — rendered **on each request** on the server. For per-request/
  per-user data or when you read cookies/headers/`searchParams`. (Demo: `/expenses/[id]`,
  `/api/expenses`.)
- **ISR (Incremental Static Regeneration)** — static, but **revalidated** on a timer
  or on-demand (`revalidate`), so you get CDN speed with periodic freshness.
- **Streaming** — the server sends HTML in chunks: the shell renders immediately and
  slow parts stream in as they resolve, wrapped in `<Suspense>` (`loading.tsx`). Cuts
  time-to-first-byte and lets a slow query not block the whole page.

Next infers static vs dynamic from what a route uses (dynamic functions like
`cookies()`, `headers()`, uncached `fetch`, `searchParams` force dynamic). You can pin
it with route segment config (`export const dynamic = "force-static" | "force-dynamic"`).

## Perspective

The model: **render as much as possible on the server** (fast, secure, SEO-friendly,
no JS shipped) and push only the interactive leaves to the client. This inverts the
SPA default (everything client) and directly fixes its weaknesses — big bundles, blank
first paint, SEO gaps, and client-side data waterfalls. The skill is placing the
`"use client"` boundary as **low** as possible.
