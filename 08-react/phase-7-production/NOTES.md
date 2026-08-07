<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · routing](../phase-6-routing/NOTES.md)
<!-- /nav -->

# Phase 7 — Ecosystem & Production: Notes

## 7.1 — Testing (implemented: `App.test.jsx`, 6 passing)
Stack: **Vitest** (fast Vite-native test runner, Jest-compatible API) + **React Testing Library (RTL)** + **jsdom** (a fake DOM so tests run headless in Node — no browser).

**RTL philosophy:** test what the **user** sees and does, not implementation details. Query elements by accessible role/text/label (`getByRole('button', { name: 'Add' })`, `findByText`, `getByLabelText`), interact via `userEvent` (type, click), and assert on the resulting DOM. This makes tests resilient to refactors — they break only when *behavior* changes.

- **`findBy*`** (async, waits) for content that appears after an effect/fetch; **`getBy*`** (sync, throws if missing); **`queryBy*`** (returns null — for asserting *absence*).
- **Mock the boundary, not the internals** — inject a fake API `loader` (like the Java capstone's in-memory repository) so there's no network; test the real components and hooks. This is the testing pyramid (Java Phase 6.2): mostly component/unit tests, fewer end-to-end (Playwright/Cypress) tests.
- Our suite verifies load-from-API, derived total (`useMemo`), add-via-form, validation, delete, and the empty state — the whole app, headless.

## 7.2 — State management landscape
Local state (`useState`/`useReducer`) + lifting + Context handles most apps. When shared state grows:
- **Context** — built-in; good for low-frequency global data (theme, current user, auth). Downside: every consumer re-renders on any change, so avoid for high-frequency state.
- **Redux (Toolkit)** — a single predictable store with reducers/actions (the `useReducer` pattern at app scale), great devtools/time-travel; more boilerplate (Redux Toolkit cuts most of it). For large apps with complex shared state.
- **Zustand / Jotai** — lightweight modern stores, minimal boilerplate, hooks-first — popular defaults now.
- **React Query / SWR** — for **server state** (data from APIs). Crucially, most "global state" is really *server cache* — React Query manages fetching/caching/refetching so you need far less client state. Separate **server state** (React Query) from **UI state** (useState/Zustand).

## 7.3 — Performance
- **It re-renders more than you think** — a component re-renders when its state/props change or its parent re-renders. Usually fine (the virtual DOM diff is cheap). Optimize only measured problems (React DevTools **Profiler**).
- **Tools:** `React.memo` (skip re-render if props are unchanged), `useMemo`/`useCallback` (stable values/identities for memoized children), correct **keys** (Phase 2). **Don't premature-optimize** — these add complexity.
- **Bundle:** route-based **code splitting** (`React.lazy` + `Suspense`), tree-shaking (JS Phase 6), and lazy-loading images/components — the front-end perf levers from System Design Phase 8.

## 7.4 — Build & deploy
**Vite** bundles to static HTML/CSS/JS (`npm run build` → `dist/`, verified). Being static files, a React SPA deploys to any static host / CDN (Netlify, Vercel, S3+CloudFront) — no server needed (the app calls the API separately). Set the API base URL via env (`VITE_API_URL`) per environment. The full-stack picture: this SPA (served from a CDN) → the Spring Boot REST API (track 02) → Postgres (track 04) — with the JWT from Spring Security (Spring Boot Phase 7) authenticating requests. Next.js (track 09) adds server rendering on top.
