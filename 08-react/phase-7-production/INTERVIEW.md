<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · routing](../phase-6-routing/NOTES.md)
<!-- /nav -->

# Phase 7 — Ecosystem & Production: Interview Q&A

⭐ = asked constantly.

**Q: How do you test React components?** ⭐⭐
With a test runner (Vitest/Jest) + React Testing Library in jsdom (headless). Query by accessible role/text/label, simulate user interactions with `userEvent`, and assert on the rendered DOM — testing behavior, not implementation. Mock the API boundary (a fake) so tests don't hit the network.

**Q: What's React Testing Library's philosophy?** ⭐
"Test your components the way users use them." Query by what's visible/accessible (role, text, label), not by internal structure (CSS classes, state, instance methods). This makes tests resilient to refactors and encourages accessible markup.

**Q: When do you need a state-management library?** ⭐⭐
When state is shared widely and changes often, and lifting + Context become unwieldy or cause excessive re-renders. Options: Redux Toolkit (large, complex shared state), Zustand/Jotai (lightweight), and React Query/SWR for server state. Much "global state" is actually server cache — handle that with React Query first.

**Q: Server state vs UI state?** ⭐
Server state is data owned by the backend and cached on the client (lists, profiles) — best managed by React Query/SWR (fetch, cache, refetch, dedupe). UI state is local interface state (modals open, form input, selected tab) — `useState`/`useReducer`/a small store. Conflating them causes most state-management pain.

**Q: When does a component re-render, and how do you optimize?** ⭐
When its state/props change or its parent re-renders. Optimize measured hotspots with `React.memo` (skip if props unchanged), `useMemo`/`useCallback` (stable references for memoized children), and correct keys. Use the Profiler to find real problems; don't optimize preemptively.

**Q: What is `React.memo`?**
A higher-order component that memoizes a component, skipping re-render when its props are shallowly equal to the previous render. Pair it with `useCallback`/`useMemo` on the parent so passed functions/objects keep stable identities — otherwise memo is defeated.

**Q: How is a React app built and deployed?** ⭐
A bundler (Vite) compiles it to static HTML/CSS/JS. Since it's static files, an SPA deploys to any static host or CDN (Netlify, Vercel, S3+CloudFront) and calls the API separately. Configure the API URL via environment variables per environment.

**Q: How does a React SPA fit into a full-stack system?**
The SPA is served from a CDN, runs in the browser, and calls a backend REST API (e.g., Spring Boot) over HTTP with a JWT for auth; the API talks to the database. CDN → SPA → API → DB. Next.js adds server-side rendering/server components for SEO and faster first paint.

**Q: What's the difference between CSR, SSR, and SSG?**
CSR (client-side rendering, plain React/Vite): the browser downloads JS and renders — fast navigation, slower first paint, weaker SEO. SSR (server renders HTML per request) and SSG (HTML built at build time) improve first paint and SEO — provided by Next.js (track 09). Trade-offs in initial load, server cost, and freshness.
