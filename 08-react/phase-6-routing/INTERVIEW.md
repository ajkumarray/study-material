<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · patterns](../phase-5-patterns/NOTES.md) | [Phase 7 · production ➡](../phase-7-production/NOTES.md)
<!-- /nav -->

# Phase 6 — Routing & Data: Interview Q&A

⭐ = asked constantly.

**Q: What is client-side routing / an SPA?** ⭐⭐
A Single-Page Application loads one HTML page and uses JavaScript to swap views as the user navigates, updating the URL via the History API — no full-page reloads. Client-side routing maps URLs to components. React Router is the standard implementation.

**Q: Why use `<Link>` instead of `<a href>` for internal navigation?** ⭐
`<a href>` triggers a full page reload (re-downloading and re-initializing the app). `<Link>` intercepts the click, updates the URL via the History API, and re-renders only the changed components — instant, state-preserving navigation.

**Q: How do you read a URL parameter or query string?**
`useParams()` for path params (`/users/:id` → `{ id }`); `useSearchParams()` for the query string (`?tab=x`). `useNavigate()` navigates programmatically.

**Q: What are route loaders (React Router data APIs)?**
A route can declare a `loader` that fetches data before rendering the component, so the initial view has its data ready (no in-component loading flash). Retrieved with `useLoaderData()`; `action`s handle form submissions. It moves data fetching into the routing layer.

**Q: What is route-based code splitting?** ⭐
Loading a route's JavaScript only when the user visits it, via `React.lazy(() => import(...))` + `<Suspense>`. It shrinks the initial bundle and speeds first load — the client-side counterpart of lazy loading (System Design Phase 8).

**Q: How do you protect routes that require authentication?**
Wrap them in a guard that checks the auth state (user/JWT, often in Context or a store) and redirects to login with `<Navigate>` if absent. Attach the token to API requests. The JWT here is the one issued by the Spring Boot backend (Spring Boot Phase 7).

**Q: How does React Router relate to Next.js?**
React Router is a client-side routing library. Next.js (track 09) provides file-based routing plus server-side rendering and server components, moving routing and data fetching to the framework/server. Same routing concepts, more built-in and server-capable.

**Q: How do nested routes and layouts work?**
Parent routes render a shared layout containing an `<Outlet/>` where matched child routes render. This lets you render persistent chrome (nav, sidebar) once while swapping the inner content per route.
