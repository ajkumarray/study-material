<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · rendering](../phase-2-rendering/NOTES.md) | [Phase 4 · data mutations ➡](../phase-4-data-mutations/NOTES.md)
<!-- /nav -->

# Phase 3 — Routing: Notes

The App Router's routing is entirely folder-driven (Phase 1). This phase goes deeper:
dynamic segments and their param plumbing, the automatic Suspense/error/404
boundaries, and the advanced organizational patterns (route groups, parallel routes,
intercepting routes) that real product UIs eventually need.

## 1. Dynamic segments and route params

**Definition.** A folder name wrapped in square brackets captures a variable piece of
the URL path as a **param**, instead of matching one fixed segment name.

- **`[id]`** — a single dynamic segment. `app/expenses/[id]/page.tsx` matches
  `/expenses/1`, `/expenses/2`, `/expenses/anything` — the captured value arrives via
  the `params` prop.
- **`[...slug]`** — a **catch-all** segment, matching one *or more* path pieces as an
  array. `app/docs/[...slug]/page.tsx` matches `/docs/a`, `/docs/a/b`, `/docs/a/b/c`,
  giving `slug: ["a", "b", "c"]`.
- **`[[...slug]]`** — an **optional catch-all**: same as above, but also matches the
  base path with zero segments (`/docs` itself), giving `slug: undefined`.
- **`generateStaticParams`** — exported from a dynamic route's `page.tsx`, it returns
  the list of param values to pre-render at build time (SSG for a known, finite set of
  ids) — the App Router's replacement for the Pages Router's `getStaticPaths`.
  Combine it with `revalidate` for ISR on those pre-rendered pages.

```tsx
// app-demo/app/expenses/[id]/page.tsx
import { getExpense } from "@/lib/data";
import { notFound } from "next/navigation";

export default async function ExpenseDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params; // params is a Promise — see next section
  const expense = await getExpense(id);
  if (!expense) notFound();
  return <h2>{expense.description}</h2>;
}
```

In this example: the folder `app/expenses/[id]/` captures whatever comes after
`/expenses/` as `id`. Visiting `/expenses/1` sets `id` to `"1"`; `getExpense("1")`
looks it up. This route has **no** `generateStaticParams`, which is exactly why the
real `next build` output in this repo classifies it as dynamic (`ƒ`, Phase 2) — Next
has no way to know the full set of valid ids ahead of time, so it renders each one
on demand.

## 2. Async `params` and `searchParams` — a version-specific detail

**Definition.** As of Next.js 15+ (this repo runs 16.2.12), the `params` and
`searchParams` props passed to a page/layout/route handler are **Promises**, not plain
objects — you must `await` them to get the actual values.

- **Why**: making them Promises lets Next start rendering and streaming a route
  before that request-time data is fully resolved, and keeps the async data model
  consistent across the whole framework (everything request-dependent is now
  something you explicitly `await`, mirroring how you `await` a `fetch`).
- **What this means in code**: `app-demo/app/expenses/[id]/page.tsx` types `params`
  as `Promise<{ id: string }>` and does `const { id } = await params;` — this is not
  optional boilerplate, it's required by the type and the runtime behavior on this
  Next version. Code written against an older Next.js tutorial that does
  `{ params: { id } }` directly (no `await`) is targeting a pre-15 version and will
  not type-check here.
- Route Handlers (`route.ts`) receive the same `params` shape — also a Promise, also
  requiring `await` on this Next version.

```ts
// Correct on Next 16 (this repo):
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
}

// Would NOT type-check on Next 16 (this is the pre-Next-15 shape):
export default function Page({ params }: { params: { id: string } }) {
  const { id } = params; // no await — wrong Promise type on this version
}
```

In this example: the only difference is `await`, but it's a real breaking change
between major versions — one more reason to always check the installed `next` version
in `package.json` before trusting an example's exact syntax.

## 3. Automatic boundaries: `loading`, `error`, `not-found`

Special files create React boundaries around a segment automatically — you don't
hand-wire `<Suspense>` or an error boundary component yourself.

- **`loading.tsx`** wraps the segment in a `<Suspense>` boundary; its export is the
  fallback UI shown while the segment's async work (an `await` inside an async Server
  Component) is in flight. This is the mechanism behind streaming (Phase 2).
- **`error.tsx`** is an error boundary for the segment. It **must** be a Client
  Component (`"use client"` — error boundaries rely on client-side React lifecycle).
  It receives an `error` object and a `reset()` function that re-attempts rendering
  the segment. A `global-error.tsx` at the app root catches errors thrown by the root
  layout itself (where a normal `error.tsx` can't help, since it lives *inside* the
  layout it would need to replace).
- **`not-found.tsx`** renders when `notFound()` is called from within the segment, or
  when no route matches at all. It returns an HTTP 404 status.

```tsx
// app-demo/app/expenses/[id]/page.tsx uses notFound() directly:
const expense = await getExpense(id);
if (!expense) notFound(); // renders the nearest not-found.tsx, returns 404
```

In this example: if you visit `/expenses/999` (an id that doesn't exist in
`lib/data.ts`'s in-memory list), `getExpense` returns `undefined`, `notFound()` is
called, and Next renders the nearest `not-found.tsx` up the tree with a 404 status —
no manual `if` branch rendering a "not found" JSX block, no manual status-code
plumbing.

These boundaries **compose with nesting**: an error thrown deep in the tree bubbles up
to the *nearest* `error.tsx` above it, not necessarily the root — so an error in one
part of the page can be contained without taking down the whole app, and unaffected
sibling segments stay interactive.

| Special file | Boundary type | Must be Client Component? | Triggered by |
|---|---|---|---|
| `loading.tsx` | `<Suspense>` fallback | No | The segment's async work being in flight |
| `error.tsx` | Error boundary | Yes | A render/runtime error thrown in the segment |
| `global-error.tsx` | Error boundary for the root layout | Yes | An error thrown in the root layout itself |
| `not-found.tsx` | 404 UI | No | `notFound()` call, or no route matching |

## 4. Route groups `(name)`

**Definition.** A folder wrapped in parentheses — `(marketing)` — organizes routes
**without adding a segment to the URL**. `app/(marketing)/about/page.tsx` still serves
`/about`, not `/marketing/about`.

- **Why you'd use one**: to give different sections of the same app their own root
  layout — e.g. a `(marketing)` group with a simple public layout, and an `(app)`
  group with an authenticated dashboard layout, both living under the same `app/`
  tree but each getting a completely different `layout.tsx` — while URLs stay clean
  (no `/marketing` or `/app` prefix leaking into the address bar).
- Also used purely for file organization — grouping related routes in the source tree
  without any layout or URL implication at all.

## 5. Parallel routes `@slot`

**Definition.** A folder prefixed with `@` defines a **named slot**: a layout can
render multiple independent pages simultaneously in the same view, each getting its
own loading/error state, by accepting each slot as a prop.

- Classic use case: a dashboard where `@team` and `@analytics` render side by side in
  one layout, each fetching and streaming independently — a slow analytics query
  doesn't block the team panel from showing up.
- The layout receives each slot as a prop matching its folder name (`@team` →
  a `team` prop) alongside the normal `children` prop.
- **`default.tsx`** inside a slot folder provides a fallback for that slot when Next
  can't determine what to render there on a full page load/refresh (e.g. you navigated
  to a URL that only matches one slot's sub-navigation state).

## 6. Intercepting routes `(.)`, `(..)`, `(...)`

**Definition.** A folder name prefixed with `(.)`-style markers renders a route **in
the current layout context** instead of fully navigating away from it — while a
direct visit or hard refresh to that same URL still renders the full, standalone page.

- The canonical example: a photo grid where clicking a photo opens it in a **modal
  overlaid on the grid** (intercepted, client-side feeling instant) — but pasting that
  photo's URL directly into the address bar, or refreshing, shows the full
  photo page with no grid behind it (the actual, non-intercepted route).
- The dot syntax indicates how many segment levels up to match against: `(.)` matches
  a segment at the *same* level, `(..)` one level up, `(..)(..)` two levels up,
  `(...)` from the root.
- This pairs with parallel routes in practice — the modal is often rendered into a
  parallel slot (e.g. `@modal`) so it can be shown/dismissed independently of the main
  content.

## 7. `middleware.ts` — where it fits (detail in Phase 5)

`middleware.ts` at the project root runs **before** a matched request completes —
before any routing/rendering happens. It's for cross-cutting request-level decisions:
auth redirects, rewrites, setting headers/cookies, A/B assignment. It is *not* part
of the `app/` folder-routing tree itself (it applies to whichever routes its
`matcher` config selects) and it does not render UI — it decides/adjusts the request,
then hands off to the matched route. This demo project has no `middleware.ts`; Phase 5
covers its runtime constraints (it runs on the Edge runtime) in depth.

## Why this model is useful

Route groups let you give different sections of one app genuinely different layouts
(marketing vs authenticated dashboard) without polluting the URL. Parallel routes let
a single view compose multiple independently-loading panels (dashboards) instead of
forcing one monolithic page component to orchestrate everything. Intercepting routes
solve the "modal that's also a real, linkable, refreshable page" problem that used to
require substantial custom routing logic in a Pages Router or a plain SPA. All three
are advanced — reach for them only when a screen actually calls for that behavior
(the same YAGNI instinct as Software Design Phase 1's simplicity principles), not
by default.

## Summary — key takeaways

- Dynamic segments: **`[id]`** (one segment), **`[...slug]`** (catch-all, 1+
  segments), **`[[...slug]]`** (optional catch-all, 0+ segments).
- On Next 15+/16, **`params` and `searchParams` are Promises** — you must `await`
  them; this is a real breaking change from earlier Next versions, not stylistic.
- **`generateStaticParams`** pre-renders a known set of dynamic-route values at build
  time (SSG for dynamic routes); without it, a dynamic segment renders on demand.
- **`loading.tsx`**, **`error.tsx`** (must be a Client Component), and
  **`not-found.tsx`** are automatic, nesting boundaries wired up purely by file
  convention — no manual `<Suspense>`/error-boundary/404 plumbing.
- **Route groups** `(name)` change layout structure without changing the URL;
  **parallel routes** `@slot` render independent panels in one layout; **intercepting
  routes** `(.)/(..)` render a route in the current context (e.g. a modal) while still
  supporting a real, standalone URL on direct visit/refresh.
- **`middleware.ts`** runs before routing, for request-level cross-cutting concerns —
  it decides/adjusts the request, it does not render.
