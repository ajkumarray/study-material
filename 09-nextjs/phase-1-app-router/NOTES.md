<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · rendering ➡](../phase-2-rendering/NOTES.md)
<!-- /nav -->

# Phase 1 — Next.js & the App Router Model: Notes

**Next.js is a React framework.** React itself is a *library* for building UIs; it
leaves routing, data fetching, bundling, and server rendering to you. Next provides
all of that plus a **server runtime**, so a single project is both the frontend and a
backend.

## 1.1 — Library vs framework; App Router vs Pages Router

- **What Next adds over React:** file-based **routing**, **server rendering** (SSR/
  SSG/streaming), **data fetching** on the server, **route handlers** (API endpoints),
  **image/font/script optimization**, code-splitting, and a production build. You stop
  wiring `react-router` + a bundler + an Express server together.
- **Two routers exist.** The older **Pages Router** (`pages/`, `getServerSideProps`)
  and the current **App Router** (`app/`, React Server Components). New apps use the
  **App Router** — that's this track. Know the Pages Router exists (lots of legacy code)
  but learn App Router.
- The App Router is built on **React Server Components (RSC)** — Phase 2 — which is the
  headline architectural shift.

## 1.2 — File conventions (routing by folders)

Routes are **folders** under `app/`; special filenames define behavior:

| File | Role |
|---|---|
| `layout.tsx` | Shared UI wrapping a segment and its children; **preserves state** across navigation; nests. The root `app/layout.tsx` is **required** and must render `<html>`/`<body>`. |
| `page.tsx` | The unique UI for a route (makes the segment publicly routable). |
| `loading.tsx` | Instant loading UI (wraps the segment in a `<Suspense>` boundary). |
| `error.tsx` | Error boundary for the segment (must be a Client Component). |
| `not-found.tsx` | UI for `notFound()` / unmatched routes. |
| `route.ts` | A **Route Handler** (API endpoint) — *cannot* coexist with `page.tsx` in the same folder. |
| `template.tsx` | Like layout but **re-mounts** on navigation (fresh state). |

A folder with a `page.tsx` becomes a URL; a folder without one is just organization.

## 1.3 — Nested layouts & navigation

- **Layouts nest.** `app/layout` wraps `app/expenses/layout` wraps
  `app/expenses/page`. On navigation between sibling routes, shared parent layouts
  **do not re-render** — only the changed segment does (partial rendering), which
  keeps scroll/state and is fast.
- **`<Link>`** (from `next/link`) does **client-side navigation**: it prefetches the
  target in the background and swaps only the changed segments — no full page reload,
  unlike a raw `<a>`. (The demo uses `<a>` for brevity; real apps use `<Link>`.)
- **`useRouter`**/`redirect()`/`usePathname` handle programmatic navigation
  (client and server variants respectively).

## Mental model

Think of `app/` as a tree: **layouts** are the stable frame, **pages** are the
swappable content, and special files (`loading`, `error`, `not-found`) are boundaries
Next wires up for you. The folder structure *is* the route table and the component
tree at once — no central router config. The single biggest new idea (Phase 2) is that
these components run on the **server by default**.
