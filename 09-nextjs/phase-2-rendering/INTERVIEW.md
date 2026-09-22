<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · app router](../phase-1-app-router/NOTES.md) | [Phase 3 · routing ➡](../phase-3-routing/NOTES.md)
<!-- /nav -->

# Phase 2 — Server vs Client Components: Interview Q&A

⭐ = asked constantly.

**Q: What is a React Server Component?** ⭐⭐
A component that runs only on the server — at build time or request time — and whose
code is never sent to the browser; it renders to a serialized payload that the client
reconstructs into HTML/DOM. Because its code never leaves the server, it can be
`async` and directly `await` a database call, read the filesystem, or use environment
secrets, with none of that logic or those credentials ending up in the client bundle.
What it *can't* do is anything that requires a live browser runtime: `useState`,
`useEffect`, event handlers, or `window`/`localStorage` — there's no client instance of
the component for that state or those handlers to attach to.

```tsx
export default async function ExpensesPage() {
  const expenses = await getExpenses(); // fine — no useEffect required
  return <ul>{expenses.map(e => <li key={e.id}>{e.description}</li>)}</ul>;
}
```

*Follow-up: "What would happen if you put `useState` in this component?"* A build/
type error in practice (or a runtime error) — Server Components are not allowed to
use client-only hooks. You'd have to extract the interactive part into a separate
`"use client"` component and render *that* from here.

**Q: Server Components vs Client Components — when do you use each?** ⭐⭐
Default to Server Components for anything that's fetching data or rendering
non-interactive UI: no client JS shipped, direct server-side access, better for SEO
and first paint. Reach for a Client Component (`"use client"`) only for the specific
subtree that needs interactivity — local state, effects, browser APIs, event
handlers. The practical discipline is keeping Client Components small and pushed to
the leaves of the tree, since everything a Client Component imports becomes client
bundle too — a `"use client"` placed high up drags a lot of otherwise-static UI into
the JS payload with it.

**Q: What does `"use client"` actually do, mechanically?** ⭐
It's a directive at the top of a file that marks that module, and everything it
imports, as client code. The component is still rendered once on the server (so the
initial HTML has real content, not a blank div), but it's *also* compiled into the
client JS bundle, sent to the browser, and hydrated there so it can respond to
interaction going forward. It's the literal boundary marker between the server-only
tree and a hydrated island.

*Follow-up: "If `"use client"` still renders on the server once, what's actually
different from a Server Component?"* A Server Component's code and dependencies never
leave the server at all — there's no client-side copy of it to hydrate. A Client
Component's code is duplicated into the client bundle specifically so it *can*
hydrate and re-render in the browser after the initial paint.

**Q: Can a Client Component import a Server Component?** ⭐
No — importing would try to pull server-only code (and its dependencies, possibly
including secrets or heavy server libraries) into the client bundle, which Next
disallows. The workaround is the "slot" pattern: a Server Component higher in the
tree renders the Server Component you want, and passes it down as `children` (or any
prop) to the Client Component. The Client Component never imports it; it just renders
whatever was handed to it, which lets it visually wrap server-rendered content
without ever bundling that content's source code.

```tsx
// Server Component (parent)
<ClientWrapper>
  <ServerRenderedChild />  {/* passed as children, not imported by ClientWrapper */}
</ClientWrapper>
```

**Q: What props can actually cross the server → client boundary?**
Only serializable values: primitives, plain objects and arrays, and — as a special
case — Server Actions. Ordinary functions, class instances, and anything with
non-plain internal state don't survive serialization, because there's no way to send
"a live function reference" over the wire from server to browser. This is precisely
why the demo's `AddExpenseForm` (a Client Component) receives `addExpenseAction` as a
prop: it's a `"use server"` function, which Next represents as a special reference the
client can invoke, rather than an arbitrary closure.

**Q: SSG vs SSR vs ISR — explain the differences.** ⭐⭐
SSG (static) renders once at build time to plain HTML served from a CDN — fastest, and
identical for every visitor, so it's the right fit for content with no request-time
dependency. SSR (dynamic) renders on every single request on the server — needed
whenever the output depends on something request-specific: cookies, headers,
`searchParams`, or a deliberately uncached fetch. ISR is static *plus* revalidation:
the page is served from the static cache like SSG, but gets regenerated either on a
timer (`revalidate: N` seconds) or on demand (`revalidatePath`/`revalidateTag`) — so
you keep CDN-level speed for most requests while still being able to reflect
underlying data changes without a full rebuild. Next picks per route automatically
based on what the route touches; you can force a choice with `export const dynamic`.

**Q: A page that `await`s a data call came out marked static (`○`) in the build
output — is that a bug?** ⭐ *version-specific*
No — this is exactly what happens in this repo's demo. `await`ing a plain async
function (even one simulating I/O with a delay) doesn't, by itself, make a route
dynamic. What forces dynamic rendering is specifically the use of a *dynamic API* —
`cookies()`, `headers()`, `searchParams`, or an uncached `fetch()` — none of which
`getExpenses()` touches. So Next statically analyzes the route, finds nothing
request-dependent, and prerenders it once at build time. The practical consequence:
that page's content is frozen until something explicitly revalidates it — which is
why the demo's Server Action calls `revalidatePath("/expenses")` after a write; without
that call, a newly added expense would never show up on the statically-cached page.

**Q: What is streaming, and how does `loading.tsx` fit in?** ⭐
Streaming means the server sends the page shell as soon as it's ready and streams in
slower parts of the page as they finish, instead of waiting for the entire page —
including its slowest data source — before sending anything. `loading.tsx` is the
mechanism: Next automatically wraps the segment in a `<Suspense>` boundary and uses
that file's export as the fallback shown while the segment's async work is still in
flight. This both improves perceived performance (something useful shows up
immediately) and real performance (a slow query in one part of the page no longer
blocks the fast parts from being visible).

**Q: What makes a route dynamic instead of static, precisely?** *nuance*
Any of: calling `cookies()` or `headers()` (both read request-specific data), reading
`searchParams` in a Server Component, using `fetch` with `cache: "no-store"` (or a
`revalidate: 0`), or a dynamic route segment with no `generateStaticParams` supplying
known values ahead of time. Any of these forces per-request rendering for that route.
Absent all of them, Next defaults to static generation. You can override either
direction explicitly with `export const dynamic = "force-static" | "force-dynamic"`.

**Q: How do RSCs address the weaknesses of a traditional client-rendered SPA?** ⭐
A traditional SPA ships all component code up front, so there's a JS bundle to
download and execute before anything useful appears, plus a client-side data
waterfall — component mounts, `useEffect` fires, a fetch goes out, data arrives, then
a re-render finally shows real content — and weaker SEO unless you add separate SSR
tooling. RSCs move rendering and data fetching to the server by default: the HTML the
browser first receives already has real data in it (fast first paint, real content
for crawlers), and only the interactive leaves ship as client JS, so the bundle is
smaller. The trade-off is that a server (or serverless function) is now required in
the request path — you give up "purely static files on a CDN with zero server," which
is what you'd have with an SPA (or Next in fully-static export mode).

**Q: What is hydration, and why does minimizing the `"use client"` boundary reduce
its cost?**
Hydration is React, in the browser, attaching event listeners and internal state to
the server-rendered HTML already on the page, turning static markup into an
interactive tree — without throwing away and re-creating the DOM. Only Client
Components hydrate; Server Components have no client-side counterpart to attach
anything to. So the amount of JS the browser has to parse, execute, and hydrate is
directly proportional to how much of the tree is marked `"use client"` — which is the
concrete, measurable reason "keep the client boundary small and at the leaves" isn't
just style advice, it's a performance lever.
