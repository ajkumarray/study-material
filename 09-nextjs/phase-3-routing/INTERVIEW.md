<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · rendering](../phase-2-rendering/NOTES.md) | [Phase 4 · data mutations ➡](../phase-4-data-mutations/NOTES.md)
<!-- /nav -->

# Phase 3 — Routing: Interview Q&A

⭐ = asked constantly.

**Q: How do you create a dynamic route, and what are the different bracket forms?** ⭐⭐
A bracketed folder name captures a variable path segment. `[id]` — e.g.
`app/expenses/[id]/page.tsx` — matches exactly one segment, like `/expenses/1`, and
the value shows up in `params`. `[...slug]` is a catch-all, matching one *or more*
path pieces into an array — `/docs/a/b/c` gives `slug: ["a","b","c"]`. `[[...slug]]`
is an *optional* catch-all: same as above, but it also matches the bare base path
(`/docs` with zero extra segments), whereas plain `[...slug]` requires at least one.

*Follow-up: "When would you reach for a catch-all instead of several fixed
segments?"* When the number/shape of path segments is genuinely variable and driven
by data, e.g. a docs site's nested category structure, or a CMS-driven URL tree —
anywhere you can't (or don't want to) hand-enumerate every possible folder depth.

**Q: Why are `params` and `searchParams` async (Promises) now, and what breaks if
you forget to `await` them?** ⭐ *version-specific*
Since Next.js 15 (and unchanged in this repo's 16.2.12), `params` and `searchParams`
are typed as Promises rather than plain objects. The reasoning is architectural: they
represent request-time data, and making them explicitly async lets Next start
rendering/streaming a route before that data is fully resolved, keeping the framework's
async data model consistent (everything request-dependent is something you `await`,
the same way you `await` a `fetch`). Forgetting to `await` them is a type error on
this version — code copied from an older Next.js tutorial that destructures `params`
directly (`{ params: { id } }`) won't compile here; it needs `const { id } = await
params;` instead, as the repo's `app/expenses/[id]/page.tsx` does.

```tsx
// This repo (Next 16): params is a Promise
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
}
```

**Q: How do you pre-render dynamic routes at build time?**
Export `generateStaticParams` from the dynamic route's `page.tsx`, returning an array
of the param objects you want pre-rendered — Next then statically generates one page
per returned value (SSG for a dynamic route), the App Router's direct replacement for
the Pages Router's `getStaticPaths`. Combine it with a `revalidate` export for ISR:
pre-rendered at build time, then periodically refreshed. Without
`generateStaticParams`, a dynamic segment renders on demand per request (dynamic/SSR)
— which is exactly the state of this repo's `/expenses/[id]` route, confirmed by its
`ƒ` (dynamic) marker in real `next build` output.

**Q: How do `loading.tsx` and `error.tsx` work, and why must `error.tsx` be a Client
Component?** ⭐⭐
`loading.tsx` becomes the fallback of an automatic `<Suspense>` boundary Next wraps
around the segment, shown while the segment's async rendering is still in flight —
this is the mechanism behind streaming. `error.tsx` is an error boundary for the
segment: it receives the thrown `error` plus a `reset()` function to retry rendering.
It must be a Client Component because React error boundaries are implemented via
client-side lifecycle (`componentDidCatch`/`getDerivedStateFromError`), which has no
equivalent in server-only rendering — there's no client instance on the server for a
lifecycle method to run on. Both are wired up purely by file convention and compose
with nesting: an error bubbles to the *nearest* `error.tsx` above where it was thrown,
so unrelated sibling UI stays interactive.

*Follow-up: "What catches an error thrown by the root layout itself?"* Not a normal
`error.tsx` — since that error boundary lives *inside* the layout it would need to
replace, it can't help if the layout itself is what's broken. `global-error.tsx` at
the app root exists specifically for that case.

**Q: How do you return a 404 from a Server Component?** ⭐
Call `notFound()` (from `next/navigation`). It renders the nearest `not-found.tsx`
boundary and returns an HTTP 404 status — no manual conditional JSX for a "not found"
message and no manual status-code handling. The repo's `app/expenses/[id]/page.tsx`
does exactly this: `if (!expense) notFound();` after a failed lookup. Unmatched
routes (no folder matches the URL at all) render the same boundary automatically,
without you calling anything.

**Q: What are route groups, and why use them?** ⭐
Folders wrapped in parentheses, `(name)`, that organize the `app/` tree **without**
adding a segment to the URL — `app/(marketing)/about/page.tsx` still serves `/about`.
The main real-world use is giving different sections of one project genuinely
different root layouts (e.g. a public marketing layout vs. an authenticated dashboard
layout) while keeping URLs clean, with no `/marketing` or `/dashboard` prefix leaking
into the address bar. They're also used just for organizing files with no layout or
URL implication at all.

**Q: What are parallel routes, and what problem do they solve?** *nuance*
A folder prefixed with `@`, like `@team` or `@analytics`, defines a named "slot" that
a layout can render alongside its normal children — so one layout can show multiple,
independently-loading pages at once (a dashboard with a team panel and an analytics
panel rendering side by side, each with its own `loading`/`error` state). The layout
receives each slot as a prop matching the folder name. Without parallel routes, you'd
have to hand-orchestrate multiple data sources and their independent
loading/error UI inside a single monolithic page component — parallel routes let the
router do that composition instead.

**Q: What are intercepting routes, and how do they differ from a client-side modal
you'd build by hand?** *nuance*
Intercepting routes (`(.)`, `(..)`, `(...)` prefixes indicating how many segment
levels up to match) render a route **in the current layout context** instead of fully
navigating to it — while a direct visit or hard refresh to the same URL still renders
the full, standalone page. The canonical example is a photo grid: clicking a photo
opens it as a modal over the grid (intercepted — fast, no full navigation), but
pasting or refreshing that photo's URL shows the actual full page with no grid behind
it. A hand-rolled client-side modal usually can't reproduce that second behavior (a
real, bookmarkable, refreshable URL for the "opened" state) without significant extra
routing logic — intercepting routes give you both behaviors from the same route
definition, often paired with a parallel route slot to host the modal.

**Q: Where does `middleware.ts` fit relative to routing?**
It runs *before* a matched request is routed to a page/handler at all — for
cross-cutting concerns like auth gating/redirects, rewrites, setting headers or
cookies, and A/B assignment. It's configured at the project root (not inside `app/`),
applies to whichever paths its `matcher` config selects, and does not render any UI —
it inspects/adjusts the request (or short-circuits it with a redirect/rewrite) and
then hands off to the normal route. Phase 5 covers that it specifically runs on the
Edge runtime, which constrains what APIs it can use.
