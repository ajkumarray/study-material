<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 09 — Next.js (App Router)

**React, but a full framework**: file-based routing, server-side rendering, data
fetching, and a backend (route handlers + server actions) in one project. Next.js
is the default way to ship a production React app.

Builds directly on **React (08)** and **TypeScript (07)**. Where React is a *library*
(you assemble routing, data fetching, and a build yourself), Next is a *framework*
that provides all of it and adds a **server**: components can run on the server, fetch
data directly, and stream HTML — solving the SEO, first-paint, and data-waterfall
problems of a pure client SPA.

Taught for someone who knows React + TS + a Spring Boot backend (02): Next's server
model is contrasted with both the React client model and a classic
controller/service backend.

## The runnable app: `app-demo/`

A **real Next.js 16 App-Router app that builds** (`next build` verified). It
implements the repo's expense theme with every core primitive:

| File | Primitive it teaches |
|---|---|
| `app/layout.tsx` | root layout (wraps all routes, Server Component) |
| `app/page.tsx` | a Server Component page |
| `app/expenses/page.tsx` | **async Server Component** fetching data directly |
| `app/expenses/[id]/page.tsx` | **dynamic route** with async `params` + `notFound()` |
| `app/expenses/actions.ts` | **Server Action** (server mutation callable from client) |
| `components/AddExpenseForm.tsx` | **Client Component** island (`"use client"`) |
| `app/api/expenses/route.ts` | **Route Handler** (REST `GET`/`POST`) |
| `lib/data.ts` | in-memory data source (stands in for a DB/API) |

```bash
cd 09-nextjs/app-demo
npm install
npm run build      # production build (what we verified)
npm run dev        # http://localhost:3000
```

## Curriculum

### Phase 1 — Next.js & the App Router model ✅
- [x] 1.1 Library vs framework; what Next adds; App Router vs Pages Router
- [x] 1.2 File conventions: `layout`, `page`, `loading`, `error`, `not-found`, `route`
- [x] 1.3 Nested layouts & the component tree; `Link` and client navigation
- NOTES · INTERVIEW

### Phase 2 — Rendering: Server vs Client Components ✅
- [x] 2.1 React Server Components (RSC): default, server-only, zero client JS
- [x] 2.2 Client Components (`"use client"`): the interactivity island; the boundary
- [x] 2.3 SSR vs SSG vs ISR vs streaming; when each applies
- NOTES · INTERVIEW

### Phase 3 — Routing ✅
- [x] 3.1 Dynamic segments `[id]`, catch-all `[...slug]`, async `params`/`searchParams`
- [x] 3.2 `loading.tsx` (Suspense) & `error.tsx` (error boundary) & `not-found`
- [x] 3.3 Route groups `(group)`, parallel `@slot` & intercepting routes (theory)
- NOTES · INTERVIEW

### Phase 4 — Data fetching & mutations ✅
- [x] 4.1 Fetching in Server Components; the `fetch` cache & `revalidate`
- [x] 4.2 Route Handlers: building a JSON/REST API in-app
- [x] 4.3 Server Actions: mutations without an API route; `revalidatePath`/`Tag`
- NOTES · INTERVIEW

### Phase 5 — Production concerns ✅
- [x] 5.1 Metadata & SEO; the `metadata` API
- [x] 5.2 The caching layers (request/data/full-route/router) & their invalidation
- [x] 5.3 Middleware, env vars, deployment model (edge vs node), performance
- NOTES · INTERVIEW

### Capstone ✅
- [x] The `app-demo` expense app — server-rendered list, dynamic detail, client
  form wired to a Server Action, and a REST route handler. `next build` passes.
- See `CAPSTONE.md`.

## How this connects

- **← React (08):** Next *is* React; components, hooks, props are unchanged. The new
  ideas are the **server** boundary and file-based routing.
- **← TypeScript (07):** the app is TS end-to-end; props, params, and API payloads
  are typed.
- **↔ Spring Boot (02):** Route Handlers/Server Actions overlap with a REST backend.
  Next can *be* the backend, or a BFF in front of the Spring API — discussed in Phase 4.
- **→ Docker (12) / CI-CD (13):** the production build is what gets containerized and
  deployed.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · app router** — [Notes](phase-1-app-router/NOTES.md) · [Interview](phase-1-app-router/INTERVIEW.md)
- **Phase 2 · rendering** — [Notes](phase-2-rendering/NOTES.md) · [Interview](phase-2-rendering/INTERVIEW.md)
- **Phase 3 · routing** — [Notes](phase-3-routing/NOTES.md) · [Interview](phase-3-routing/INTERVIEW.md)
- **Phase 4 · data mutations** — [Notes](phase-4-data-mutations/NOTES.md) · [Interview](phase-4-data-mutations/INTERVIEW.md)
- **Phase 5 · production** — [Notes](phase-5-production/NOTES.md) · [Interview](phase-5-production/INTERVIEW.md)
<!-- /phases-nav -->
