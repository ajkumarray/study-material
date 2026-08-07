<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · routing](../phase-3-routing/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Data Fetching & Mutations: Interview Q&A

⭐ = asked constantly.

**Q: How do you fetch data in the App Router?** ⭐⭐
In an async Server Component, `await` the data source directly in the body (DB call or
`fetch`). It runs on the server, so no client loading state or exposed secrets, and the
data is in the initial HTML. No `useEffect` needed for initial data.

**Q: How does Next's `fetch` caching work?** ⭐⭐
Next extends `fetch`: results can be cached and deduped per render. `cache: "no-store"`
forces fresh (dynamic); `next: { revalidate: N }` caches for N seconds then revalidates
(ISR); `next: { tags: [...] }` lets you invalidate on demand with `revalidateTag`.

**Q: What is a Route Handler?** ⭐
A `route.ts` file exporting HTTP-method functions (`GET`, `POST`, …) using the Web
Request/Response APIs — how you build a JSON/REST API inside a Next app. Used for
webhooks, public APIs, and non-React consumers. Can't share a folder with `page.tsx`.

**Q: What is a Server Action?** ⭐⭐
An async function marked `"use server"` that runs on the server but is callable directly
from client components/forms; Next generates the secure RPC. It's the App Router way to
do mutations without hand-writing an API route or client `fetch` — and it works with
progressive enhancement via `<form action={...}>`.

**Q: Server Actions vs Route Handlers — when each?** ⭐
Server Actions for mutations triggered by your own React UI (forms, buttons) — less
boilerplate, progressive enhancement, integrated revalidation. Route Handlers for a
general HTTP API surface consumed by external clients or needing REST semantics/verbs.

**Q: After a mutation, how does the UI update?** ⭐
Call `revalidatePath(path)` or `revalidateTag(tag)` inside the Server Action to
invalidate the cached render, so the next render (or the automatic one after the action)
shows fresh data. `redirect()` can navigate after a successful write.

**Q: Are Server Actions secure by default?** ⭐
No — a Server Action is effectively a public endpoint. You must **validate inputs and
check authorization inside it**; never assume the client sent valid or permitted data.
Treat `FormData`/args as untrusted (parse with a schema).

**Q: How does Next relate to a separate backend like Spring Boot?** *nuance*
Either Next is the backend (Server Components/Actions hit the DB directly), or it's a
BFF that calls the existing service over HTTP from the server (keeping tokens off the
client). Use the BFF approach when another service owns the domain logic and you want
Next just for UI + SSR.

**Q: How do you avoid data-fetching waterfalls?**
Fetch independent data in parallel (`Promise.all`) rather than sequential awaits, and
use `<Suspense>`/`loading.tsx` so independent slow segments stream in without blocking
the rest. Colocating fetches in Server Components also removes the client round-trip
waterfall of the SPA model.
