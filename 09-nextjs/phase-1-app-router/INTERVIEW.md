<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · rendering ➡](../phase-2-rendering/NOTES.md)
<!-- /nav -->

# Phase 1 — App Router Model: Interview Q&A

⭐ = asked constantly.

**Q: How is Next.js different from React?** ⭐⭐
React is a UI *library* — it gives you components, JSX, and a reconciler, and
deliberately leaves routing, data fetching, bundling, and server rendering to you or a
third-party library (`react-router`, `webpack`, an Express server, etc.). Next.js is a
*framework* built on React: it makes those architectural decisions, bundles them into
one opinionated toolchain, and adds a server runtime, so a single Next project can be
both the frontend and a backend. Concretely, Next gives you file-based routing, a
choice of rendering strategy per route (static, server-rendered, incrementally
regenerated, streamed), server-side data fetching with no client waterfall, Route
Handlers for building a REST/JSON API, and automatic image/font/script optimization
and code-splitting — all things you'd otherwise assemble yourself around React.

*Follow-up: "If Next does so much for you, why would a team ever choose plain React
instead?"* When you don't want a server in the request path at all (a pure static
SPA deployed as flat files), when you're embedding React into an existing non-Next
backend, or when the extra abstraction (rendering strategy per route, RSC boundary)
is overhead for a small, fully-client app like an internal tool or a widget.

**Q: App Router vs Pages Router — what's the actual difference?** ⭐⭐
The Pages Router (`pages/`, `getServerSideProps`/`getStaticProps`) is the original
model: every component is a Client Component by default, data fetching happens through
special exported functions per page, and there's one global layout (`_app.tsx`). The
App Router (`app/`) is the current model, built on React Server Components: components
are **Server Components by default**, data fetching is just `await`ing inside an async
component, layouts nest arbitrarily deep and persist across navigation, and streaming
via `<Suspense>` is built in. New Next projects use the App Router; the Pages Router
remains supported for legacy codebases, and you should still recognize its vocabulary
(`getServerSideProps`, `getStaticPaths`, `_document.tsx`) when you encounter it.

*Follow-up: "Can you mix both routers in one project?"* Yes — Next allows `app/` and
`pages/` to coexist during a migration; `app/` takes precedence when a route exists in
both.

**Q: What files are special inside the `app/` directory, and what does each do?** ⭐⭐
`page.tsx` is the UI for a route and is what makes a folder actually routable.
`layout.tsx` is shared UI that wraps a segment and persists across navigation within
it (state isn't lost when you navigate to a sibling route). `loading.tsx` becomes the
fallback of an automatic `<Suspense>` boundary around the segment. `error.tsx` is a
client-side error boundary for the segment (must have `"use client"`). `not-found.tsx`
renders on `notFound()` or an unmatched route. `route.ts` turns the folder into an API
endpoint (Web `Request`/`Response`) instead of a page — it can't coexist with
`page.tsx` in the same folder. `template.tsx` is like `layout.tsx` but remounts (fresh
state) on every navigation instead of persisting.

```ts
// app-demo/app/api/expenses/route.ts and app-demo/app/expenses/page.tsx
// live in DIFFERENT folders precisely because a folder can't be both
// a page and a route handler.
```

*Follow-up: "Why must `error.tsx` be a Client Component?"* Because React error
boundaries are implemented with a class component lifecycle method
(`getDerivedStateFromError`/`componentDidCatch`), which only exists in the client
React runtime — Server Components have no client-side lifecycle to catch a render
error with, so the boundary has to be hydrated, client-rendered code.

**Q: Why is the root layout required, and what must it contain?** ⭐
There's exactly one root layout (`app/layout.tsx`), and it's the outermost shell for
every route in the app, so it's the one place responsible for rendering `<html>` and
`<body>` — no page ever renders those tags itself. Next renders it once and keeps it
mounted across every navigation in the app, which is also where you'd put app-wide
providers, global CSS imports, and persistent chrome like a nav bar.

**Q: What's the difference between `layout` and `template`?**
Both wrap the segment's children, but a `layout` **persists** across navigation within
its segment — it doesn't unmount or re-render when you navigate between sibling
routes, so its component state, scroll position, and any open connections survive.
A `template` creates a **brand-new instance** on every navigation — state resets,
`useEffect`s re-run, as if the component remounted from scratch. You reach for
`template` specifically when you want that reset behavior, e.g. an enter animation
that should replay on every visit, or a feature toggle that should re-run its setup
effect per navigation. In practice `layout` is what you want almost all the time;
`template` is the exception.

**Q: How does `<Link>` differ from a plain `<a>` tag?** ⭐⭐
`<Link>` (from `next/link`) performs client-side navigation: Next prefetches the
destination route's code and data ahead of the click (by default, when the link
scrolls into the viewport), and on click it swaps only the route segments that
actually changed, reusing the Router Cache — no full-document reload, and any state
held in shared layouts is preserved (partial rendering). A plain `<a>` triggers a
standard browser navigation: the whole document unloads, every layout remounts from
scratch, and all client-side state is lost, even for layouts that didn't logically
change. The visible difference is a flash/reload with `<a>` versus an instant,
app-like transition with `<Link>`.

*Follow-up: "The demo app uses raw `<a>` tags — does that break routing?"* No, it
still routes to the right page since it's a standard browser link to a same-origin
path — it just loses prefetching and partial rendering, so every navigation is a full
reload. It's fine for a small teaching demo; a production app would use `<Link>`
everywhere for in-app navigation.

**Q: What is partial rendering?** ⭐
On navigation between two routes, Next re-renders only the segments of the route tree
that actually changed — any shared parent layout stays mounted and isn't re-rendered
or re-fetched. This is what preserves layout-level state (an open modal, scroll
position, a WebSocket connection) across navigations and is a large part of why
App Router navigation feels fast: you're not re-doing work for UI that didn't change.

**Q: `useRouter()` vs `redirect()` — when do you use each?**
`useRouter()` is a Client Component hook (`next/navigation`) for *programmatic*
navigation triggered by a client-side event — e.g. `router.push('/thanks')` after a
client-validated form submits, or `router.refresh()` to re-fetch Server Component
data without a full navigation. `redirect()` is the server-side equivalent, callable
from Server Components, Server Actions, and Route Handlers — there's no `useRouter`
instance on the server, so `redirect()` throws a special signal Next intercepts to
issue the redirect response directly. You'd use `redirect()` inside a Server Action
after a successful mutation, or in a Server Component to gate a page behind auth.

**Q: What is colocation, and how do private folders (`_folder`) fit in?**
Colocation means you can put a route's supporting files — components, helpers, Server
Actions — inside the same `app/` folder as the route that uses them, since only
specifically-named files (`page.tsx`, `route.ts`, etc.) are treated as routes; anything
else is invisible to the router. `app-demo/app/expenses/actions.ts` sits right next to
`app/expenses/page.tsx` for exactly this reason. A private folder (`_components/`)
takes this further: prefixing a folder name with an underscore excludes it and
everything inside from routing entirely, even if it accidentally contains a
`page.tsx` — useful when you want to be explicit that a folder is implementation
detail, not a route, regardless of what ends up inside it.

**Q: Is Next.js only for server-side rendering?** *nuance*
No. Rendering strategy is a **per-route** decision, not an app-wide one: a route can
be statically generated at build time (SSG), server-rendered per request (SSR),
incrementally regenerated on a timer or on demand (ISR), or contain fully
client-rendered interactive islands via `"use client"` — all in the same app,
side by side. Phase 2 covers exactly how Next decides which strategy applies to a
given route by default.

**Q: What bundler does Next.js 16 use, and how do you know?** *version-specific*
Next.js 16 defaults to **Turbopack** (Next's Rust-based bundler) for both `next dev`
and `next build` — running `next build` in this repo's `app-demo/` prints `▲ Next.js
16.2.12 (Turbopack)` and leaves a `.next/turbopack` cache directory. Both `next dev`
and `next build` still accept an explicit `--webpack` flag to opt back into the
legacy bundler if something isn't yet Turbopack-compatible. This is the kind of fact
worth double-checking against the installed `next` version rather than assuming,
since bundler defaults are exactly what changes between major versions.
