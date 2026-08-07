# Capstone — The Next.js Expense App (`app-demo/`)

The Next.js track's synthesis: a **real App-Router app that builds** (`next build`
verified), implementing the repo's running expense theme with every core primitive
from Phases 1–4 working together.

```bash
cd 09-nextjs/app-demo
npm install
npm run build     # ✓ verified: 5 routes compiled
npm run dev       # http://localhost:3000
```

## Verified build output

```
Route (app)
┌ ○ /                    (Static)   — home, Server Component
├ ○ /_not-found          (Static)
├ ƒ /api/expenses        (Dynamic)  — Route Handler (GET/POST)
├ ○ /expenses            (Static)   — async Server Component list + client island
└ ƒ /expenses/[id]       (Dynamic)  — dynamic route, async params, notFound()
```

`○ Static` = prerendered HTML; `ƒ Dynamic` = server-rendered on demand.

## Every phase, applied

| Phase | Concept | Where in the app |
|---|---|---|
| 1 | Nested layout + file routing | `app/layout.tsx` wraps all routes; folders = URLs |
| 2 | Server Component (default) | `app/page.tsx`, `app/expenses/page.tsx` — fetch on server, zero client JS |
| 2 | Client Component island | `components/AddExpenseForm.tsx` (`"use client"`) — the only hydrated piece |
| 2 | Server→client boundary | Server page passes a Server Action as a prop to the client form |
| 3 | Dynamic route + async params + `notFound()` | `app/expenses/[id]/page.tsx` |
| 4 | Async data fetching in a Server Component | `await getExpenses()` — no `useEffect` |
| 4 | Route Handler (REST) | `app/api/expenses/route.ts` — `GET`/`POST` JSON |
| 4 | Server Action + `revalidatePath` | `app/expenses/actions.ts` — mutate, then refresh the list |

## The architecture in one sentence

The list page **renders on the server** (fast first paint, SEO, no exposed data
source), embeds a **small client island** for the add-form, whose submit calls a
**Server Action** that mutates and **revalidates** the cached render — while a
**Route Handler** exposes the same data as a REST API for external clients. That's the
full App-Router loop: server render → interactive island → server mutation →
revalidate.

## Connections & what's next

- It reuses the **React (08)** component model and **TypeScript (07)** types end-to-end.
- `lib/data.ts` stands in for a database or the **Spring `expense-api` (02)** — swap it
  for a real `fetch` to the Java service (the BFF pattern, Phase 4) or a direct DB call.
- The production build (`output: "standalone"`) is what the **Docker (12)** and
  **CI/CD (13)** tracks containerize and deploy, and **Kubernetes (14)** runs.
