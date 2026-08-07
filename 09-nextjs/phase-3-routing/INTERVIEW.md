<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · rendering](../phase-2-rendering/NOTES.md) | [Phase 4 · data mutations ➡](../phase-4-data-mutations/NOTES.md)
<!-- /nav -->

# Phase 3 — Routing: Interview Q&A

⭐ = asked constantly.

**Q: How do you create a dynamic route?** ⭐⭐
A bracketed folder: `app/expenses/[id]/page.tsx` matches `/expenses/:id`. The value
comes in `params` (a Promise in Next 15+, so you `await` it). `[...slug]` is catch-all;
`[[...slug]]` is optional catch-all.

**Q: Why are `params` and `searchParams` async now?** ⭐
They represent request-time data. Making them Promises lets Next start rendering and
streaming before they're resolved and keeps the async model consistent — you `await`
them where you need the values.

**Q: How do you pre-render dynamic routes at build time?**
Export `generateStaticParams` returning the set of param values; Next statically
generates a page per value (SSG). It's the App Router's replacement for
`getStaticPaths`. Combine with `revalidate` for ISR.

**Q: How do `loading.tsx` and `error.tsx` work?** ⭐⭐
`loading.tsx` is a Suspense fallback shown while a segment renders (enables streaming).
`error.tsx` is a Client Component error boundary that catches render errors in its
segment and receives `error` and a `reset()` function. Both are auto-wired by file
convention and compose with nesting.

**Q: How do you return a 404?** ⭐
Call `notFound()` from a Server Component (or route handler) — it renders the nearest
`not-found.tsx` with a 404 status. Unmatched routes render it automatically.

**Q: What are route groups and why use them?**
Folders in parentheses `(name)` that organize routes **without** affecting the URL.
Used to apply different layouts to different sections (e.g. marketing vs app) or to
group files logically while keeping clean URLs.

**Q: What are parallel and intercepting routes?** *nuance*
Parallel routes (`@slot`) render multiple independent pages within one layout
simultaneously (dashboards), each with its own loading/error. Intercepting routes
(`(.)`/`(..)`) render a target route in the current UI context — e.g. show a detail in a
modal over the list, while a direct visit/refresh shows the full page.

**Q: Where does `middleware.ts` fit?**
It runs before the request is handled (typically at the edge) for cross-cutting
concerns: auth gating/redirects, rewrites, setting headers, i18n, A/B. It's not for
rendering — it decides/adjusts the request, then hands off to the route.
