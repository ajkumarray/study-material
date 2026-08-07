<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · patterns](../phase-5-patterns/NOTES.md) | [Phase 7 · production ➡](../phase-7-production/NOTES.md)
<!-- /nav -->

# Phase 6 — Routing & Data: Notes

*(Concepts + code sketches — react-router isn't installed in `expense-web` to
keep the dependency set minimal; add it with `npm i react-router-dom`.)*

## Client-side routing
A React app is a **Single-Page Application (SPA)**: one HTML page, and JavaScript swaps the UI as you navigate — no full-page reloads. **Client-side routing** maps the URL to which components render, updating the address bar via the History API so back/forward and bookmarks work. **React Router** is the standard library.

```jsx
import { BrowserRouter, Routes, Route, Link, useParams, useNavigate } from 'react-router-dom';

<BrowserRouter>
  <nav><Link to="/">Home</Link> <Link to="/expenses">Expenses</Link></nav>
  <Routes>
    <Route path="/" element={<Home />} />
    <Route path="/expenses" element={<ExpenseList />} />
    <Route path="/expenses/:id" element={<ExpenseDetail />} />   {/* URL param */}
    <Route path="*" element={<NotFound />} />                     {/* 404 catch-all */}
  </Routes>
</BrowserRouter>
```

- **`<Link>` / `<NavLink>`** — navigate without a page reload (don't use `<a href>` for internal links — that reloads).
- **`useParams()`** — read URL params (`/expenses/:id` → `{ id }`).
- **`useNavigate()`** — navigate programmatically (after a form submit).
- **`useSearchParams()`** — read/write query string (`?category=food`).
- **Nested routes & layouts** — an `<Outlet/>` renders child routes inside a shared layout (nav/sidebar rendered once).

## Data loading
- **In-component** (what we do now): fetch in `useEffect`/a hook per route component (Phase 3).
- **Router loaders** (React Router 6.4+ data APIs): a route declares a `loader` that fetches *before* the component renders, so you never render a loading spinner for the initial data — the router waits and provides it via `useLoaderData()`. Actions handle form submissions similarly.
- **Route-based code splitting** — `React.lazy(() => import('./Page'))` + `<Suspense fallback={...}>` loads a route's code only when visited, shrinking the initial bundle (performance, Phase 7).

## Protected routes & guards
Wrap routes that require auth: check the user/token (e.g., the JWT from Spring Boot Phase 7), and `<Navigate to="/login">` if absent. The auth token typically lives in Context or a store and is attached to API requests.

## Where this is heading
Client-side routing is what **Next.js** (track 09) formalizes and extends — file-based routing, server-side rendering, and server components move routing and data loading to the framework/server. React Router is the library-level version of the same ideas.
