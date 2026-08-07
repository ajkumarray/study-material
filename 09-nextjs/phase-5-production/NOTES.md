<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · data mutations](../phase-4-data-mutations/NOTES.md)
<!-- /nav -->

# Phase 5 — Production Concerns: Notes

Shipping a Next app: SEO/metadata, the (famously subtle) caching layers, middleware,
and the deployment model.

## 5.1 — Metadata & SEO

- **The `metadata` API.** Export a `metadata` object (static) or `generateMetadata`
  (dynamic, async, per-params) from a `layout`/`page` — Next renders the `<title>`,
  `<meta>`, Open Graph, and Twitter tags into the server HTML. Because pages are
  server-rendered, crawlers see real content and tags (a core SSR win over a client SPA).
- File conventions handle the rest: `favicon.ico`, `opengraph-image`, `robots.ts`,
  `sitemap.ts` generate the corresponding assets/routes.

## 5.2 — The caching layers

Next's caching is powerful and the most common source of confusion. Four layers:

| Layer | What it caches | Invalidate with |
|---|---|---|
| **Request memoization** | duplicate `fetch`es within a single render | (automatic, per-request) |
| **Data Cache** | `fetch`/data results across requests & deployments | `revalidate` time, `revalidateTag`, `revalidatePath` |
| **Full Route Cache** | rendered HTML/RSC payload of static routes | rebuild / revalidation of underlying data |
| **Router Cache** | visited routes' RSC payload in the browser (client nav) | `revalidatePath`, `router.refresh()`, time |

Mental model: **reads are cached aggressively; mutations must invalidate.** After any
write, `revalidatePath`/`revalidateTag` (Phase 4) is what makes the change visible.
Opt out per fetch with `no-store`, or per route with `export const dynamic =
"force-dynamic"` / `revalidate`. (Caching defaults shift between Next versions — state
the *concepts*; verify the current defaults for the version in use.)

## 5.3 — Middleware, env, deployment

- **`middleware.ts`** runs before matched requests complete — auth redirects, rewrites,
  header/cookie manipulation, i18n, bot/A-B logic. It runs on the **Edge runtime** (a
  lightweight, globally-distributed V8 environment) so it must avoid Node-only APIs.
- **Environment variables:** server-only by default; only `NEXT_PUBLIC_`-prefixed vars
  are exposed to the browser (bundled into client JS). Keep secrets unprefixed so they
  never leave the server.
- **Runtimes:** **Node.js runtime** (default, full Node APIs) vs **Edge runtime**
  (faster cold starts, globally deployed, limited API surface) — choose per route/
  middleware with `export const runtime`.
- **Deployment:** Next builds a hybrid output — static assets to a CDN, server
  components/handlers to a Node/Edge server (or serverless functions). Vercel is the
  first-party host; it also runs anywhere via `next start` in a container (Docker,
  track 12) or a Node server. The **standalone** output (`output: "standalone"`)
  produces a minimal self-contained server ideal for Docker images.

## 5.4 — Performance features (mostly automatic)

Automatic code-splitting per route, `next/image` (lazy loading, responsive sizes,
modern formats), `next/font` (self-hosted fonts, no layout shift), prefetching on
`<Link>`, and RSC shipping less JS. The main *manual* lever is keeping the `"use
client"` boundary low (Phase 2).

## Perspective

Production Next is mostly about **caching and the server/edge boundary**. Know the four
cache layers and how to invalidate them (this is where real bugs live), keep secrets
server-side (`NEXT_PUBLIC_` is the only escape), and pick Node vs Edge per need. The
build output is a hybrid (CDN statics + server functions) — which is exactly what the
Docker (12) and CI/CD (13) tracks take to production.
