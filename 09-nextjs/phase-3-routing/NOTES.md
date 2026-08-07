<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · rendering](../phase-2-rendering/NOTES.md) | [Phase 4 · data mutations ➡](../phase-4-data-mutations/NOTES.md)
<!-- /nav -->

# Phase 3 — Routing: Notes

The App Router's routing is entirely folder-driven. Beyond the basics (Phase 1), this
covers dynamic segments, the built-in boundaries, and the advanced organizational
patterns.

## 3.1 — Dynamic segments & route params

- **`[id]`** — a dynamic segment captures one path piece: `app/expenses/[id]/page.tsx`
  matches `/expenses/1`, `/expenses/2`, … The value arrives in `params`.
- **`[...slug]`** — catch-all (one or more segments); **`[[...slug]]`** — optional
  catch-all (also matches the base path).
- **Async `params`/`searchParams`.** In Next 15+, `params` and `searchParams` are
  **Promises** — you `await` them (the demo does `const { id } = await params`). This
  enables streaming and reflects that they're request-time data.
- **`generateStaticParams`** pre-renders dynamic routes at build time (SSG for a known
  set of ids) — the App Router replacement for `getStaticPaths`.

## 3.2 — Boundaries: loading, error, not-found

Special files create React boundaries automatically around a segment:

- **`loading.tsx`** → wraps the segment in `<Suspense>`; its export is the fallback
  shown while the async segment renders (enables streaming — Phase 2.3).
- **`error.tsx`** → an **error boundary** (must be `"use client"`); catches render
  errors in the segment and gets `error` + a `reset()` to retry. A `global-error.tsx`
  catches errors in the root layout.
- **`not-found.tsx`** → rendered when `notFound()` is called (the demo's detail page)
  or a route doesn't match. Returns a 404 status.

These compose with nesting: an error in a child bubbles to the nearest `error.tsx`,
keeping the rest of the app interactive.

## 3.3 — Advanced organization (know the names)

- **Route groups `(marketing)`** — a folder in parentheses organizes routes **without
  adding a URL segment**. Useful to give different sections their own layout (e.g.
  `(shop)/layout` vs `(admin)/layout`) or to group files.
- **Parallel routes `@slot`** — render **multiple pages in the same layout
  simultaneously** (e.g. a dashboard with `@team` and `@analytics` slots), each with
  independent loading/error states. The layout receives them as named props.
- **Intercepting routes `(.)`, `(..)`** — render a route *in the current context*
  instead of navigating away — the classic "open a photo in a modal over the feed, but
  a hard refresh shows the full page" pattern.
- **`middleware.ts`** (Phase 5) runs before a request completes — auth redirects,
  rewrites, headers, A/B — at the edge.

## Perspective

Routing is **the filesystem**: segments are folders, params are `[bracketed]` folders,
and the framework wires Suspense/error/404 boundaries from special files so you don't
hand-roll them. The advanced patterns (groups, parallel, intercepting) exist for
real product needs — multiple layouts, dashboards, and modal routes — but you reach for
them only when a screen actually calls for it (YAGNI, Software Design Phase 1).
