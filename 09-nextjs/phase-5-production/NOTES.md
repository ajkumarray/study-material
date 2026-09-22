<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · data mutations](../phase-4-data-mutations/NOTES.md)
<!-- /nav -->

# Phase 5 — Production Concerns: Notes

Shipping a Next app: SEO/metadata, the (famously subtle) layers of caching, the
middleware/runtime split, environment variables, and the actual deployment model. This
phase closes the loop from Phases 1-4 (routing, rendering, data) to what happens when
the app is actually built and served.

## 1. Metadata & SEO

**Definition.** The App Router replaces hand-written `<head>` tags with a typed
**metadata API**: export a `metadata` object (static) or a `generateMetadata` async
function (dynamic, can depend on `params`/data) from a `layout.tsx` or `page.tsx`, and
Next renders the corresponding `<title>`, `<meta>`, Open Graph, and Twitter tags into
the server-rendered HTML.

```tsx
// app-demo/app/layout.tsx
export const metadata = {
  title: "Next.js Expense Demo",
  description: "App Router: server components, route handlers, server actions",
};
```

In this example: because this is a plain exported object (not a function), it's
static metadata — evaluated once, applied to every route under this layout unless a
nested `page.tsx` overrides specific fields. A route whose title needs to depend on
fetched data (e.g. an expense detail page titled after that expense's description)
would instead export an async `generateMetadata({ params })` function and `await` the
same kind of data call the page itself uses.

- **Why this matters for SEO specifically**: because App Router pages are
  server-rendered (Phase 2), crawlers receive real HTML with the actual title/meta
  tags already present — no client-side JS execution required to see them, unlike a
  pure client-rendered SPA where `<head>` tags injected by JS may not be reliably
  picked up by every crawler.
- **File-convention metadata**: `favicon.ico`, `apple-icon.png`, an `opengraph-image`
  file/route, `robots.ts`, and `sitemap.ts` generate the corresponding static assets
  or dynamic routes (`/robots.txt`, `/sitemap.xml`) automatically from file presence
  alone — no manual route needed.

## 2. The caching layers

Next's caching is powerful and is the single most common source of "why is my data
stale" confusion in production Next apps. There are conceptually four layers, and — as
established in Phase 4 — **the defaults for several of these changed in Next 15 and
carry forward into this repo's Next 16.2.12**, so stating a specific default without
checking the version is a common source of wrong answers.

| Layer | What it caches | Scope | Invalidate with |
|---|---|---|---|
| **Request memoization** | Duplicate `fetch` calls (same URL/options) | One single render pass | Automatic — resets per request, nothing to configure |
| **Data Cache** | `fetch`/data results, persisted across requests and deployments | Server-wide, persistent | `revalidate` option, `revalidateTag`, `revalidatePath` |
| **Full Route Cache** | The rendered HTML/RSC payload of statically-rendered routes | Server-wide, persistent | Rebuild, or revalidation of the underlying data that route depends on |
| **Router Cache** (client-side) | Visited routes' RSC payload, held in the browser | Per browser session | `router.refresh()`, `revalidatePath`, time-based expiry, hard navigation |

- **Mental model**: static output and cacheable fetches are cached aggressively by
  design (that's the whole performance win of SSG/ISR); the burden is on **you** to
  invalidate after a mutation. This repo's `addExpenseAction` demonstrates the
  pattern end-to-end: it writes the new expense, then explicitly calls
  `revalidatePath("/expenses")` — without that call, the Full Route Cache entry for
  the (statically-rendered) `/expenses` page would keep serving the old list
  indefinitely.
- **Opting out per-fetch**: `{ cache: "no-store" }` on a `fetch` call skips the Data
  Cache entirely for that call.
- **Opting out per-route**: `export const dynamic = "force-dynamic"` forces a route to
  render on every request, bypassing the Full Route Cache for it; `export const
  revalidate = N` sets a time-based ISR window for the route.
- **Caution on defaults across versions**: this repo confirmed via a real `next
  build` that a plain `await` of a non-`fetch` async call does *not*, by itself, force
  a route dynamic (`/expenses` still came out static, Phase 2), and that `fetch`
  itself is uncached by default on this version (Phase 4) — both are the kind of
  behavior that has changed across major Next releases, so state the *concepts* in an
  interview and verify exact defaults against the version actually in use.

## 3. Middleware

**Definition.** A `middleware.ts` file at the project root (this demo doesn't have
one, but the mechanism is a standard App Router feature) runs **before** a matched
request is handed to routing/rendering — for cross-cutting request-level logic that
needs to happen for many/all routes uniformly.

- **Common uses**: auth gating/redirects (redirect an unauthenticated user to
  `/login` before any page code runs), URL rewrites, setting response headers or
  cookies, i18n locale detection/redirects, bot detection, A/B test bucket assignment.
- **Runs on the Edge runtime** (below) — a lightweight, globally-distributed V8
  environment, not the full Node.js runtime — so middleware code must avoid Node-only
  APIs (`fs`, most of `crypto`'s Node-specific surface, etc.) and should stay fast,
  since it's on the critical path of every matched request.
- **`matcher` config** scopes which paths the middleware actually runs on/against
  (e.g. exclude static assets), so you're not paying its cost on every single request
  in the app if it's only relevant to a subset of routes.
- Middleware **decides/adjusts the request**, it does not render UI — it's not a
  place to fetch page data or produce HTML.

## 4. Environment variables

- **Server-only by default.** Any variable in `.env*` is available on the server
  (Server Components, Server Actions, Route Handlers, middleware) but is **not**
  bundled into client JavaScript, so it never reaches the browser — this is the
  correct default place to keep API keys, database URLs, and other secrets.
- **`NEXT_PUBLIC_`-prefixed variables** are the deliberate exception: Next inlines
  them into the client bundle at build time, making them available in Client
  Components too. Only prefix a variable this way if its value is genuinely safe to
  ship to every visitor's browser (a public API base URL, a public analytics id) —
  never a secret.
- **Practical rule**: if you're not sure whether something needs `NEXT_PUBLIC_`,
  default to *not* prefixing it and only read it from server-side code; add the
  prefix only when a Client Component genuinely needs the value directly.

## 5. Runtimes: Node.js vs Edge

| | Node.js runtime | Edge runtime |
|---|---|---|
| API surface | Full Node.js APIs | A restricted, Web-standard-focused subset |
| Cold start | Slower | Faster |
| Deployment | Typically a single region/server or serverless function | Globally distributed by the hosting platform |
| Good for | Heavy computation, full Node library compatibility, long-running work | Lightweight, latency-sensitive logic (redirects, header rewriting, simple auth checks) |
| Default for | Server Components, Route Handlers, Server Actions (unless overridden) | `middleware.ts` (always) |
| Opt-in via | `export const runtime = "nodejs"` | `export const runtime = "edge"` |

You choose the runtime **per route or per middleware** (route/handler code defaults
to Node; middleware always runs on Edge) based on whether the code needs full Node
API compatibility (Node) or needs to be as fast and globally close to the user as
possible with a constrained API surface (Edge).

## 6. Deployment model

- **Build output is hybrid**: `next build` produces static assets (suitable for a
  CDN) for statically-rendered routes, alongside server code (for dynamic routes,
  Route Handlers, Server Actions) that needs an actual server or serverless function
  to execute. This repo's real build output makes the split concrete:

```
Route (app)
┌ ○ /               ← static asset, servable from a CDN with no per-request server work
├ ○ /_not-found
├ ƒ /api/expenses    ← needs a running server/function per request
├ ○ /expenses
└ ƒ /expenses/[id]
```

- **Vercel** is the framework's first-party hosting platform (built by the same
  company as Next.js) and requires zero extra configuration for this hybrid output.
- **It also runs anywhere via `next start`** on any machine/container with Node.js
  installed — Next is not locked to one host.
- **`output: "standalone"`** (a `next.config` option) produces a minimal,
  self-contained server bundle with only the dependencies actually needed at
  runtime traced in — this is the standard way to build a small, production-ready
  Docker image (track 12) instead of shipping the entire `node_modules` tree, and is
  what you'd put behind a Kubernetes Deployment (track 14) for a self-hosted setup.

## 7. Performance features (mostly automatic)

Most of this is automatic once you're on the App Router; the meaningful manual lever
remains where you draw the `"use client"` boundary (Phase 2).

- **Automatic code-splitting per route** — a visitor only downloads the JS for the
  route they're on, not the whole app.
- **`next/image`** — lazy loading by default, responsive `srcset` generation, modern
  format negotiation (e.g. WebP/AVIF where supported), and explicit width/height to
  prevent layout shift.
- **`next/font`** — self-hosts font files (no third-party request to a font CDN at
  runtime) and avoids the layout shift caused by a late-loading web font swapping in.
- **`<Link>` prefetching** — destination route code/data is fetched ahead of the
  click (Phase 1), so the actual navigation feels instant.
- **RSCs shipping less JS** — the direct performance consequence of Phase 2's
  server/client component split: a page with mostly-static content and one small
  interactive island ships JS for only that island.

## Why this all matters

Production Next.js is mostly about **caching correctness and the server/edge
boundary**. The caching layers are where most real-world Next bugs live — almost
always "I mutated data but the UI still shows the old value," traceable to a missing
`revalidatePath`/`revalidateTag` or an accidentally-static route. Keeping secrets
server-side (`NEXT_PUBLIC_` as the one deliberate escape hatch) and picking Node vs
Edge per workload are the other two recurring production decisions. The payoff for
getting this right: a hybrid build (CDN-served statics + server functions only where
truly needed) that's both fast for visitors and cheap to run, which is exactly the
artifact the Docker (track 12), CI/CD (track 13), and Kubernetes (track 14) tracks
take the rest of the way to a running production system.

## Summary — key takeaways

- **Metadata API** (`metadata` object / `generateMetadata`) renders real `<title>`/
  `<meta>` tags into server HTML — crawlers see them without executing client JS.
- **Four caching layers**: request memoization (per-render dedupe), Data Cache
  (`fetch` results across requests), Full Route Cache (rendered static HTML/RSC),
  Router Cache (client-side, per browser session) — know these by name and how to
  invalidate each.
- **Reads are cached aggressively by default for static content; mutations must
  explicitly invalidate** via `revalidatePath`/`revalidateTag` — this repo's demo
  shows the exact pattern.
- **`middleware.ts`** runs before routing, on the **Edge runtime** — fast, globally
  distributed, but a restricted API surface (no Node-only APIs); scope it with
  `matcher` so it doesn't run on every request unnecessarily.
- **`NEXT_PUBLIC_`-prefixed env vars** are the only ones exposed to the browser;
  everything else stays server-only by default — keep secrets unprefixed.
- **Node runtime** (default, full Node API) vs **Edge runtime** (faster cold start,
  restricted API, what middleware always uses) — pick per route/handler with `export
  const runtime`.
- Deployment produces a **hybrid build** (CDN statics + server functions/handlers);
  `output: "standalone"` is the standard way to get a minimal server bundle for a
  Docker image; Vercel is first-party but not required — `next start` runs anywhere
  Node does.
