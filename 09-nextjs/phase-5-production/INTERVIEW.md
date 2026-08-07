<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · data mutations](../phase-4-data-mutations/NOTES.md)
<!-- /nav -->

# Phase 5 — Production Concerns: Interview Q&A

⭐ = asked constantly.

**Q: How does Next handle SEO/metadata?** ⭐
Export a `metadata` object or async `generateMetadata` from a layout/page; Next renders
title/meta/OG tags into server HTML. Because content is server-rendered, crawlers see
real markup and tags — the main SEO advantage over a client-only SPA. File conventions
(`sitemap.ts`, `robots.ts`, `opengraph-image`) generate the rest.

**Q: Explain Next's caching layers.** ⭐⭐
Four: request memoization (dedupes fetches within one render), the Data Cache
(fetch/data results across requests, revalidated by time/tag/path), the Full Route
Cache (rendered HTML/RSC for static routes), and the Router Cache (client-side RSC
payload for visited routes). Reads are cached; mutations invalidate via
`revalidatePath`/`revalidateTag`.

**Q: A user reports stale data after an update — what's wrong?** ⭐
Likely a cache wasn't invalidated after the mutation. The Server Action/handler should
call `revalidatePath`/`revalidateTag` (Data + Router caches), or the fetch should use
`no-store`/appropriate `revalidate`. Caching-without-invalidation is the classic Next
bug.

**Q: How do you keep secrets out of the browser?** ⭐⭐
Environment variables are server-only unless prefixed `NEXT_PUBLIC_`, which bundles them
into client JS. Keep API keys/secrets unprefixed and only read them in Server
Components/Actions/Route Handlers/middleware — never in client components.

**Q: Node runtime vs Edge runtime?** ⭐
Node runtime (default) has full Node APIs, good for heavy work and Node libraries. Edge
runtime is a lightweight V8 environment deployed globally with fast cold starts but a
limited API surface (no most Node built-ins). Choose per route/middleware via
`export const runtime`; middleware runs on Edge.

**Q: What does `middleware.ts` do?**
Runs before matched requests complete (on the Edge): auth gating/redirects, rewrites,
setting headers/cookies, i18n, A/B. It shapes the request/response but doesn't render;
keep it fast and free of Node-only APIs.

**Q: How do you deploy a Next app (besides Vercel)?** ⭐
It builds a hybrid: static assets to a CDN + a Node/Edge server for dynamic routes and
handlers. Run `next start` on any Node host, or use `output: "standalone"` to produce a
minimal self-contained server that goes into a Docker image (track 12) and onto
Kubernetes (14). Vercel is first-party but not required.

**Q: What performance features are automatic vs manual?** *nuance*
Automatic: per-route code-splitting, `next/image`, `next/font`, `<Link>` prefetching,
and RSCs shipping less JS. Manual: keeping the `"use client"` boundary low, parallelizing
fetches, choosing static/ISR where possible, and cache configuration. The framework does
a lot, but bundle size still depends on where you draw the client boundary.
