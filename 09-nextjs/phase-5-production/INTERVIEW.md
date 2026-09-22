<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · data mutations](../phase-4-data-mutations/NOTES.md)
<!-- /nav -->

# Phase 5 — Production Concerns: Interview Q&A

⭐ = asked constantly.

**Q: How does Next.js handle SEO and metadata?** ⭐
You export a `metadata` object (static) or an async `generateMetadata` function
(dynamic — can depend on `params` or fetched data) from a `layout.tsx` or `page.tsx`,
and Next renders the corresponding `<title>`, `<meta>`, Open Graph, and Twitter tags
directly into the server-rendered HTML. This matters specifically because App Router
pages are server-rendered by default: a crawler gets real markup with real metadata
already present, with no need to execute client-side JavaScript to discover it — a
meaningful advantage over a purely client-rendered SPA, where meta tags injected by JS
after the initial load are not reliably picked up by every crawler. File conventions
handle the rest: `sitemap.ts` and `robots.ts` generate `/sitemap.xml` and
`/robots.txt` from code, and an `opengraph-image` file/route generates the social
preview image, all without you wiring up a manual route for any of it.

*Follow-up: "When would you use `generateMetadata` instead of a static `metadata`
object?"* Whenever the title/description needs to depend on data you only know at
request or build time for that specific route — e.g. an expense detail page whose
`<title>` should be the expense's own description, which means the function has to
`await` the same data lookup the page itself does.

**Q: Explain Next's caching layers.** ⭐⭐
There are four, conceptually distinct layers. **Request memoization** dedupes
identical `fetch` calls within a single render pass — automatic, resets every
request, nothing to configure. The **Data Cache** persists `fetch`/data results
across requests and even deployments, controlled per-call with `cache`/`next.revalidate`/
`next.tags` on `fetch`. The **Full Route Cache** stores the rendered HTML/RSC payload
for statically-rendered routes, invalidated by a rebuild or by revalidating the
underlying data. The **Router Cache** is client-side — the browser holds onto visited
routes' RSC payloads so revisiting them via `<Link>`/`router.push` doesn't refetch,
cleared by `router.refresh()`, `revalidatePath`, time-based expiry, or a hard
navigation. The overarching model: reads are cached aggressively by default for
anything that can be statically determined; mutations are responsible for explicitly
invalidating whatever cache layer their write affects.

**Q: A user reports stale data after an update — what's likely wrong, and how do you
debug it?** ⭐
The most common cause is a missing invalidation after a write: the Server
Action/Route Handler that performed the mutation didn't call
`revalidatePath`/`revalidateTag` for the affected route, or a `fetch` used to read
that data was cached (`force-cache`, or a stale `next.revalidate` window) without a
matching invalidation path. To debug: identify which cache layer is serving the stale
value — check whether the route is statically rendered at all (`next build`'s route
table marks it `○`), check whether the read path uses `fetch` with caching enabled,
and confirm the write path actually calls `revalidatePath`/`revalidateTag` for that
exact path/tag. This repo's own `addExpenseAction` is the concrete positive example:
`/expenses` renders statically, so the action's `revalidatePath("/expenses")` call is
not optional — remove it and newly added expenses would never appear without a full
rebuild.

**Q: How do you keep secrets out of the browser bundle?** ⭐⭐
Environment variables are server-only by default — available in Server Components,
Server Actions, Route Handlers, and middleware, but never bundled into client
JavaScript. The one deliberate exception is the `NEXT_PUBLIC_` prefix: any variable
named that way is inlined into the client bundle at build time, so it's readable in
Client Components too. The practical rule is to default to *not* prefixing anything,
and only add `NEXT_PUBLIC_` to a variable whose value is genuinely safe for every
visitor to see (a public API base URL, a public analytics id) — never an API key,
database URL, or any other secret.

*Follow-up: "What actually happens if you accidentally read a secret env var inside
a Client Component?"* If it's not `NEXT_PUBLIC_`-prefixed, it resolves to `undefined`
in the browser (Next doesn't inline non-public vars into the client bundle at all) —
so the failure mode is a broken feature, not a leaked secret, which is the safer
default direction for this kind of mistake to fail in.

**Q: Node.js runtime vs Edge runtime — what's the actual difference, and what always
runs on Edge?** ⭐
The Node.js runtime (the default for Server Components, Route Handlers, and Server
Actions unless overridden) gives you the full Node API surface — good for heavy
computation or anything depending on a Node-specific library. The Edge runtime is a
lightweight, Web-standard-focused environment with a restricted API surface (no
`fs`, limited `crypto`, etc.), but with faster cold starts and — on platforms that
support it — global distribution close to the requester. `middleware.ts` **always**
runs on the Edge runtime, which is exactly why middleware code needs to avoid
Node-only APIs. For routes/handlers, you opt into Edge explicitly with `export const
runtime = "edge"` when the workload is lightweight and latency-sensitive (simple auth
checks, header/redirect logic) and stick with Node for anything heavier or dependent
on Node-only packages.

**Q: What does `middleware.ts` do, and where does it sit relative to routing?**
It runs *before* a matched request is handed off to routing/rendering — for
cross-cutting, request-level concerns that should apply uniformly across many routes:
auth gating/redirects, URL rewrites, setting headers or cookies, i18n locale
detection, bot detection, A/B bucket assignment. It doesn't render any UI — it
inspects and can adjust or short-circuit the request (e.g. redirect an
unauthenticated user to `/login` before any page code runs at all), then hands off to
the normal route. A `matcher` config scopes which paths it actually runs against, so
you're not paying its cost — and its Edge-runtime constraints — on every single
request if it's only relevant to a subset of routes. This repo's demo doesn't include
a `middleware.ts`, since none of its cross-cutting concerns (auth, i18n, etc.) apply.

**Q: How do you deploy a Next app if you're not using Vercel?** ⭐
`next build` produces a hybrid output: static assets for statically-rendered routes
(servable straight from a CDN) plus server code for dynamic routes, Route Handlers,
and Server Actions, which needs an actual running server or serverless function.
`next start` runs that hybrid output on any machine with Node.js installed — Vercel
is the framework's first-party host and needs zero extra config for this output, but
it isn't required. For a containerized deployment, `output: "standalone"` in
`next.config` produces a minimal, self-contained server bundle with only the
dependencies actually used traced in — that's what goes into a small production
Docker image (track 12) and behind a Kubernetes Deployment (track 14), rather than
shipping the full `node_modules` tree into the container.

*Follow-up: "How would you confirm which routes in a given app need that server
piece at all, versus being pure static output?"* Run `next build` and read its route
table: a `○` marks a route prerendered as static content (CDN-servable, no
per-request server work), while a `ƒ` marks a route that's server-rendered on demand
and therefore needs the server/function piece of the deployment. This repo's own
build output shows exactly that split — `/` and `/expenses` are static, `/api/expenses`
and `/expenses/[id]` are dynamic.

**Q: What performance features does Next.js give you automatically, and what's still
a manual decision?** *nuance*
Automatic: per-route code-splitting (a visitor only downloads JS for the route
they're on), `next/image` (lazy loading, responsive images, modern format
negotiation, layout-shift prevention), `next/font` (self-hosted fonts, no late
web-font swap causing layout shift), `<Link>` prefetching of destination routes, and
React Server Components shipping less JS overall by keeping non-interactive UI
server-only. Manual: where you draw the `"use client"` boundary (still the single
biggest lever on bundle size, per Phase 2), whether you parallelize independent data
fetches, whether a given route is static/dynamic/ISR, and your caching/revalidation
strategy. The framework automates a lot of the mechanical performance work, but the
architectural decisions — client boundary placement and caching strategy — remain
yours to get right.
