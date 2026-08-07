<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · app router](../phase-1-app-router/NOTES.md) | [Phase 3 · routing ➡](../phase-3-routing/NOTES.md)
<!-- /nav -->

# Phase 2 — Server vs Client Components: Interview Q&A

⭐ = asked constantly.

**Q: What is a React Server Component?** ⭐⭐
A component that runs only on the server (at request/build time) and whose code is
never sent to the browser. It renders to a serialized stream the client reconstructs.
It can be `async` and access server resources directly, but can't use state, effects,
event handlers, or browser APIs.

**Q: Server Components vs Client Components — when do you use each?** ⭐⭐
Default to Server Components for data fetching and non-interactive UI (no client JS,
secure, SEO-friendly). Use Client Components (`"use client"`) only where you need
interactivity — state, effects, event handlers, browser APIs. Keep client components
small and at the leaves.

**Q: What does `"use client"` actually do?** ⭐
It marks a module (and its import subtree) as client code: server-rendered for initial
HTML, then hydrated and run in the browser. It's the boundary between the server tree
and an interactivity island — everything imported below it becomes client code too.

**Q: Can a Client Component import a Server Component?** ⭐
Not directly (it would pull server-only code into the bundle). Instead, pass the Server
Component as `children`/props from a parent Server Component (the slot pattern). A
Server Component *can* render and pass serializable props to a Client Component.

**Q: What props can cross the server→client boundary?**
Only serializable values — primitives, plain objects/arrays, etc. Functions (except
Server Actions), class instances, Dates-with-methods, etc. don't serialize. This is why
you pass data down and actions/handlers are either client-defined or Server Actions.

**Q: SSG vs SSR vs ISR?** ⭐⭐
SSG renders to HTML at build time (served from CDN, same for everyone). SSR renders per
request (per-user/dynamic data). ISR is static + periodic/on-demand revalidation —
CDN speed with freshness. Next picks per route based on what it uses; you can override
with segment config.

**Q: What is streaming / how does `loading.tsx` work?** ⭐
Streaming sends the page shell immediately and streams slower parts as they resolve.
`loading.tsx` wraps a segment in `<Suspense>`, so its fallback shows instantly while
the async segment renders on the server and streams in — improving perceived and real
load time.

**Q: What makes a route dynamic instead of static?** *nuance*
Using request-time data: `cookies()`, `headers()`, `searchParams`, or uncached
`fetch`/`no-store`. Any of these opts the route into per-request rendering. Without
them, Next statically generates the route by default. You can force either with
`export const dynamic`.

**Q: How do RSCs fix SPA weaknesses?**
They move data fetching and rendering to the server, so the browser gets HTML with data
already in it (fast first paint, SEO), ships less JS (only interactive islands hydrate),
and avoids client-side data waterfalls. The trade-off is a server is now required in the
request path (vs a pure static SPA).
