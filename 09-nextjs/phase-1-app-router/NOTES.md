<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · rendering ➡](../phase-2-rendering/NOTES.md)
<!-- /nav -->

# Phase 1 — Next.js & the App Router Model: Notes

This track uses the `app-demo/` project (Next.js **16.2.12**, React **19.2.8**, App
Router, TypeScript). Every claim in these notes is checked against that project's real
source files and against a real `next build` run — where a fact depends on the exact
version, the notes say so explicitly rather than generalizing across Next versions.

## 1. Next.js is a framework, React is a library

**Definition.** React is a UI *library*: it gives you components, JSX, hooks, and a
reconciler, but leaves routing, data fetching, bundling, and server rendering to you.
Next.js is a *framework* built on top of React: it makes those architectural decisions
for you and adds a server runtime, so a single Next project is both the frontend and a
backend.

- **Routing**: file-system based, no separate router library or config to wire up.
- **Rendering**: server rendering (SSR), static generation (SSG), incremental
  regeneration (ISR), and streaming are all built in and chosen per route.
- **Data fetching**: happens directly on the server, inside components (Phase 4) —
  no client-side `useEffect` + `fetch` dance for initial data.
- **Route Handlers**: a `route.ts` file is a full HTTP endpoint (Web `Request`/
  `Response`) — you get a backend API without standing up Express/Spring separately.
- **Build tooling**: bundling, code-splitting per route, minification, and image/
  font optimization are automatic. Since Next 15/16 the default bundler for both `next
  dev` and `next build` is **Turbopack** (Next's Rust-based bundler) — running `next
  build` in `app-demo/` prints `▲ Next.js 16.2.12 (Turbopack)` and produces a
  `.next/turbopack` cache directory. `--webpack` opts back into the legacy bundler if
  you need it (e.g. an incompatible plugin).

```
// Real output of `npx next build` in app-demo/ (Next 16.2.12, Turbopack):
▲ Next.js 16.2.12 (Turbopack)
  Creating an optimized production build ...
✓ Compiled successfully in 1171ms
  Running TypeScript ...
✓ Generating static pages using 7 workers (5/5) in 227ms

Route (app)
┌ ○ /
├ ○ /_not-found
├ ƒ /api/expenses
├ ○ /expenses
└ ƒ /expenses/[id]

○  (Static)   prerendered as static content
ƒ  (Dynamic)  server-rendered on demand
```

In this example: the build compiles with Turbopack, type-checks with TypeScript, then
prints a **route table** classifying every route as static (`○`, prerendered once at
build time) or dynamic (`ƒ`, rendered per request). This table is Next telling you,
route by route, which rendering strategy it inferred — Phase 2 explains exactly why
`/expenses` came out static and `/expenses/[id]` came out dynamic even though both
`await` data.

## 2. App Router vs Pages Router

- **Two routers coexist in Next.js.** The original **Pages Router** (`pages/`
  directory, `getServerSideProps`/`getStaticProps`, all components are Client
  Components by default) is still supported for legacy codebases. The **App Router**
  (`app/` directory) is the current model, built on **React Server Components**, and is
  what every new Next project — including `app-demo/` — uses.
- The App Router's headline architectural shift is that **components are Server
  Components by default** (Phase 2). The Pages Router has no equivalent: everything
  there runs in the browser after hydration.
- You will see Pages Router code in older tutorials, Stack Overflow answers, and
  production codebases that haven't migrated. Recognize `getServerSideProps`,
  `getStaticProps`, `getStaticPaths`, and `_app.tsx`/`_document.tsx` as Pages Router
  vocabulary — none of it exists in `app/`.

| Aspect | Pages Router (`pages/`) | App Router (`app/`) |
|---|---|---|
| Default component type | Client Component (hydrated) | Server Component |
| Data fetching | `getServerSideProps`/`getStaticProps` (exported functions) | `await` directly in an async Server Component |
| Layouts | `_app.tsx` (one global layout) | Nested `layout.tsx` per segment, arbitrarily deep |
| Loading UI | Hand-rolled | `loading.tsx` (automatic `<Suspense>`) |
| Error handling | Hand-rolled / `_error.tsx` | `error.tsx` per segment (automatic error boundary) |
| API routes | `pages/api/*.ts` | `app/**/route.ts` (Route Handlers) |
| Mutations | Manual `fetch` to an API route | Server Actions (`"use server"`) alongside Route Handlers |
| Streaming | Not supported | Built in via Suspense boundaries |

## 3. File conventions — routing by folder structure

**Definition.** In the App Router, a **route is a folder** under `app/`, and specific
filenames inside that folder give the segment behavior. There is no central router
config file — the folder tree *is* the route table.

- **`page.tsx`**: the unique UI for a route. A folder only becomes a publicly
  reachable URL if it (or a dynamic descendant) contains a `page.tsx`. A folder
  without one is pure organization (e.g. holding shared components) and adds no route.
- **`layout.tsx`**: shared UI that wraps a segment and everything nested under it.
  Layouts **preserve state across navigation** — they don't re-render when a sibling
  route changes (see partial rendering, below). The root `app/layout.tsx` is
  **required** and must render `<html>` and `<body>`, because it's the outermost shell
  for every single route in the app.
- **`loading.tsx`**: an instant loading UI. Its export becomes the fallback of a
  `<Suspense>` boundary that Next automatically wraps around the segment — this is
  what makes streaming (Phase 2) work without you writing `<Suspense>` by hand.
- **`error.tsx`**: an error boundary for the segment. It **must** be a Client
  Component (`"use client"`) because React error boundaries are a class-component /
  client-runtime feature. It receives `error` and a `reset()` function to retry.
- **`not-found.tsx`**: the UI rendered when `notFound()` is called from anywhere in
  the segment, or when a route doesn't match anything. Returns an HTTP 404.
- **`route.ts`**: a **Route Handler** — exports functions named for HTTP methods
  (`GET`, `POST`, …). A folder **cannot** have both `route.ts` and `page.tsx` — a
  segment is either a page or an API endpoint, not both (Phase 4 covers this in depth).
- **`template.tsx`**: like `layout.tsx`, but creates a **new instance** on every
  navigation instead of persisting — state resets, effects re-run. Rare; used for
  per-navigation animations or when you explicitly want a fresh mount.
- **`default.tsx`**: a fallback UI for a parallel route slot (`@slot`) when Next can't
  recover which page to render on a full-page load (Phase 3).

```
app-demo/app/
├── layout.tsx              → root layout, required, renders <html>/<body>
├── page.tsx                → "/"
├── api/
│   └── expenses/
│       └── route.ts        → "/api/expenses" (Route Handler, no page.tsx here)
└── expenses/
    ├── page.tsx             → "/expenses"
    ├── actions.ts            → Server Actions used by the page (no route by itself)
    └── [id]/
        └── page.tsx         → "/expenses/:id" (dynamic segment)
```

In this example: `app-demo/app/layout.tsx` renders the `<html>`/`<body>`/nav shared by
every page. `app/page.tsx` makes `/` routable. `app/expenses/page.tsx` makes
`/expenses` routable and sits alongside `actions.ts`, which exports a Server Action but
— because it isn't named `page.tsx` or `route.ts` — adds no route of its own; it's just
a colocated module. `app/api/expenses/route.ts` is a Route Handler, so `/api/expenses`
is an HTTP endpoint, not a page.

## 4. Nested layouts and partial rendering

**Definition.** Layouts **nest**: the root layout wraps every route; a layout deeper
in the tree wraps only its own segment and descendants. When you navigate between two
routes that share a layout, Next re-renders only the part of the tree that actually
changed — the shared layout stays mounted. This is called **partial rendering**.

- **Why it matters**: a layout that renders a sidebar, a nav bar, or holds open a
  WebSocket connection doesn't get torn down and rebuilt every time you click a link
  to a sibling page. Scroll position, open `<details>` elements, form input state in
  the layout, and any client-side state in the layout survive navigation.
- In `app-demo`, `app/layout.tsx` renders the `<header>` with the nav links once; it is
  not part of what changes when you go from `/` to `/expenses` — only `<main>{children}</main>`'s
  contents swap.
- Layouts **cannot** access the `params` of the segment below them the same way pages
  can via props in all cases, and importantly a layout **cannot** pass data down to
  its `children` the way a normal parent component passes props — `children` here is
  literally "whatever page/layout is nested here," managed by the router, not by you.

```tsx
// app-demo/app/layout.tsx (root layout — required, wraps everything)
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <header>
          <h1>Expenses</h1>
          <nav>
            <a href="/">Home</a>
            <a href="/expenses">All expenses</a>
          </nav>
        </header>
        <main>{children}</main> {/* swapped per-route; header/nav persist */}
      </body>
    </html>
  );
}
```

In this example: navigating from `/` to `/expenses` only replaces what's inside
`<main>`. The `<header>` and `<nav>` are not re-rendered or re-fetched — that's partial
rendering in action, and it's why App Router navigations feel instant compared to a
full page reload.

## 5. Client-side navigation: `<Link>` vs `<a>`

- **`<Link>`** (from `next/link`) performs **client-side navigation**: it prefetches
  the destination route in the background (when it scrolls into view, by default) and,
  on click, swaps only the changed route segments via the Router Cache (Phase 5) — no
  full document reload, no flash of blank page, and the shared layout state is kept.
- **A plain `<a>`** triggers a full browser navigation: the whole document unloads and
  reloads, every layout remounts, and all client state is lost. `app-demo`'s
  `layout.tsx` and pages currently use raw `<a>` tags for brevity in the demo code —
  functionally this still routes correctly (Next intercepts same-origin `<a>` clicks
  are *not* automatically intercepted the way `<Link>` clicks are), but in real apps
  you use `<Link>` for any in-app navigation to get prefetching and partial rendering.
- **`useRouter()`** (Client Components, from `next/navigation`) gives you
  `router.push()`, `router.replace()`, `router.refresh()`, `router.back()` for
  programmatic navigation — e.g. redirecting after a client-side check.
- **`redirect()`** (Server Components, Server Actions, Route Handlers, from
  `next/navigation`) throws a special signal Next uses to issue a server-side
  redirect — you can't use `useRouter` on the server because there's no router
  instance; `redirect()` is the server-side equivalent.
- **`usePathname()`** / **`useSearchParams()`** (Client Components) read the current
  URL reactively — e.g. to highlight the active nav link.

| API | Where it runs | Use case |
|---|---|---|
| `<Link href>` | Anywhere (renders on server, hydrates on client) | Standard in-app navigation with prefetch |
| `useRouter()` | Client Components only | Programmatic navigation after a client event (form validated, modal closed) |
| `redirect()` | Server Components / Server Actions / Route Handlers | Server-side redirect (e.g. auth gate, post-mutation redirect) |
| `usePathname()` | Client Components only | Read current path reactively (active-link styling) |
| `useSearchParams()` | Client Components only | Read `?query=params` reactively |

## 6. Colocation and private folders

- You can put non-route files (components, helpers, actions, types) **inside** `app/`
  next to the routes that use them — `app-demo/app/expenses/actions.ts` lives right
  next to `app/expenses/page.tsx` that imports it. Only `page.tsx`/`route.ts` (and the
  other special filenames) are treated as routable; everything else is invisible to
  the router.
- **Private folders**: prefixing a folder with an underscore (`_components/`) opts it
  and everything below it out of routing entirely, even if it happens to contain a
  `page.tsx` by accident — a stronger, explicit version of "just don't name it
  `page.tsx`."
- **Route groups** `(name)` organize routes without adding a URL segment — covered in
  depth in Phase 3 since they interact with layouts and routing structure.

## Why this model is useful

Colocating routes, layouts, and their supporting code by folder — instead of a
separate `routes.ts` config plus a scattered `components/` tree — means the file tree
tells you the URL structure at a glance, and files that only make sense together
(a page and the Server Action its form calls) live next to each other. It also means
adding a route is "add a folder," not "edit a config file and hope you didn't
introduce a route ordering bug."

## Summary — key takeaways

- Next.js is a **framework** on top of the React **library**: it adds routing, server
  rendering, data fetching, API endpoints, and build tooling.
- The **App Router** (`app/`, React Server Components) is the current model; the
  **Pages Router** (`pages/`, `getServerSideProps`) is legacy but still supported.
- Routes are **folders**; special filenames (`page`, `layout`, `loading`, `error`,
  `not-found`, `route`, `template`) give a folder behavior. A folder needs `page.tsx`
  (or `route.ts`) to be reachable — otherwise it's just organization.
- **Layouts nest and persist across navigation** (partial rendering) — only the
  changed segment re-renders, which is why App Router navigation feels instant.
- Use **`<Link>`** for in-app navigation (prefetch + partial rendering); a raw `<a>`
  forces a full reload and loses all client state.
- Next.js 16 uses **Turbopack** as the default bundler for `dev` and `build` — verify
  this against the installed version, since bundler defaults are the kind of thing
  that changes between major versions.
