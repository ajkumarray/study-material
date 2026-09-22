<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · patterns](../phase-5-patterns/NOTES.md) | [Phase 7 · production ➡](../phase-7-production/NOTES.md)
<!-- /nav -->

# Phase 6 — Routing & Data: Notes

*(Concepts + code sketches — `react-router-dom` isn't installed in `expense-web`
to keep the dependency set minimal for a single-page app; add it with
`npm i react-router-dom` if the app grows multiple views. Everything below is
accurate React Router v6 usage, but none of it currently runs in this repo's
tests — treat the code blocks as reference sketches, not verified output.)*

## Client-side routing & the SPA model

A React app built the way `expense-web` is (Vite, `createRoot`, one `index.html`) is a **Single-Page Application (SPA)**: the browser loads exactly one HTML document, and from then on JavaScript swaps what's rendered as the user navigates — there is no full-page reload/re-download for internal navigation. **Client-side routing** is the layer that maps a URL to *which components should currently render*, and keeps the browser's address bar, back/forward buttons, and bookmarking working correctly via the browser's **History API** (`pushState`/`popState`), even though no new HTML page is ever actually fetched from the server for those transitions.

- **Why you need a router library at all, rather than just `if`/`else`-ing on `window.location.pathname` yourself**: a router centralizes URL-to-component mapping, handles the History API correctly (so back/forward work, and a direct link/bookmark to a nested URL renders the right thing on first load), and provides hooks for reading URL parameters, query strings, and navigating programmatically — all of which are easy to get subtly wrong by hand (e.g., forgetting to call `preventDefault()` and intercept clicks, or not handling the browser back button).
- **React Router** is the de facto standard library for this in the React ecosystem (there are alternatives — TanStack Router, Next.js's built-in router for framework apps — but React Router is what "adding routing to a plain React SPA" almost always means).

```jsx
import { BrowserRouter, Routes, Route, Link, useParams, useNavigate } from 'react-router-dom';

function AppRoutes() {
  return (
    <BrowserRouter>
      <nav>
        <Link to="/">Home</Link> <Link to="/expenses">Expenses</Link>
      </nav>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/expenses" element={<ExpenseListPage />} />
        <Route path="/expenses/:id" element={<ExpenseDetail />} />   {/* URL param */}
        <Route path="*" element={<NotFound />} />                     {/* 404 catch-all */}
      </Routes>
    </BrowserRouter>
  );
}
```
In this example, `<BrowserRouter>` provides the routing context (reading/writing the real URL via the History API) to everything nested inside it. `<Routes>` looks at the current URL and renders whichever single `<Route>`'s `element` matches — `/expenses/42` matches the `/expenses/:id` pattern (`:id` is a **path parameter**, capturing `42`), while a URL matching none of the explicit paths falls through to the `path="*"` catch-all, the standard way to render a 404 page.

- **`<Link to="...">` / `<NavLink to="...">`** — the router's replacement for `<a href="...">` for *internal* navigation. `<Link>` intercepts the click, calls `preventDefault()` internally, and updates the URL via the History API instead of letting the browser do a real navigation — this is what keeps the SPA a single-page app; using a plain `<a href>` for an internal route would trigger a full page reload (re-downloading and re-initializing the entire JS bundle), which defeats the point. `<NavLink>` is the same thing with automatic "active" styling support (it can apply a class/style when its `to` matches the current URL) — useful for nav bars.
- **`useParams()`** — reads the dynamic segments of the *current* matched route as an object. For a route defined as `path="/expenses/:id"`, visiting `/expenses/42` makes `useParams()` return `{ id: '42' }` inside `ExpenseDetail` (note: always a string, parsed from the URL — convert with `Number(id)` if you need it numeric, the same "form input values are strings" caveat as `ExpenseForm`'s `amount` field, Phase 2).
- **`useNavigate()`** — returns a function for **programmatic navigation**, for cases that aren't a plain link click — most commonly, redirecting after a form submission or a successful action:
```jsx
function ExpenseDetail() {
  const { id } = useParams();
  const navigate = useNavigate();

  async function handleDelete() {
    await expenseApi.remove(id);
    navigate('/expenses');   // go back to the list after deleting
  }
  // ...
}
```
- **`useSearchParams()`** — reads and updates the query string (`?category=food&sort=amount`) as a `URLSearchParams`-like object with a setter, for filter/sort/pagination state that should be shareable/bookmarkable via the URL itself rather than kept in component state that resets on refresh.
- **Nested routes & layouts** — a parent `<Route>` can wrap child `<Route>`s and render a persistent layout (nav bar, sidebar) once, with an `<Outlet/>` marking where the matched child route's content should appear:
```jsx
<Route path="/" element={<AppLayout />}>       {/* renders nav + <Outlet/> */}
  <Route index element={<Home />} />
  <Route path="expenses" element={<ExpenseListPage />} />
</Route>
```
  This avoids re-rendering (and re-mounting) the shared nav/sidebar chrome every time the user navigates between child routes — only the `<Outlet/>`'s content swaps.

**Why it's useful:** Client-side routing is what lets an SPA feel like a normal multi-page site (distinct, bookmarkable, shareable URLs for different views; working back/forward) while still keeping the actual navigation instant (no full reload, no re-fetching the whole JS bundle) — the best of both models.

**Summary / key takeaways:**
- An SPA loads one HTML page; a client-side router maps the URL to which components render, using the History API so back/forward/bookmarks behave correctly without real page reloads.
- Use `<Link>`/`<NavLink>` for internal navigation, never plain `<a href>` (that forces a full reload); `useNavigate()` for navigation triggered by code (e.g., after a submit).
- `useParams()` reads path segments (`:id`); `useSearchParams()` reads/writes the query string — both always give you strings, parse as needed.
- Nested routes + `<Outlet/>` let a shared layout render once while only its content region swaps per route.

## Data loading strategies

There are three broadly different points at which a routed app can fetch the data a page needs, each with different trade-offs:

1. **In-component fetching (what `expense-web` does today, Phase 3)** — the route's component fetches its own data in a `useEffect`/custom hook *after* it renders. The user sees the component mount (often showing a loading state) and then the data appears once the fetch resolves. Simple, and exactly the pattern `useExpenses` already implements; the trade-off is an unavoidable "flash of loading state" on every navigation to that route, even if the same data was already fetched recently.

2. **Router loaders (React Router 6.4+ "data APIs")** — a route declares a `loader` function that the router calls *before* rendering the route's component, so by the time the component actually renders, its data is already available (no loading spinner needed for the *initial* render of that route) — retrieved inside the component via `useLoaderData()`:
```jsx
// Route config with a loader (React Router 6.4+ createBrowserRouter API)
const router = createBrowserRouter([
  {
    path: '/expenses/:id',
    element: <ExpenseDetail />,
    loader: async ({ params }) => expenseApi.get(params.id),   // runs BEFORE the component renders
  },
]);

function ExpenseDetail() {
  const expense = useLoaderData();   // already resolved -- no loading state needed here
  return <p>{expense.description}</p>;
}
```
   **Actions** are the loader's counterpart for *writes* — a route can declare an `action` that runs in response to a `<Form method="post">` submission, handling the mutation (and typically redirecting or revalidating data afterward) without you wiring up `onSubmit`/`fetch`/`useState` by hand the way `ExpenseForm` currently does.

3. **Route-based code splitting** — independent of *when* data loads, `React.lazy(() => import('./ExpenseDetail.jsx'))` combined with `<Suspense fallback={<Spinner/>}>` loads a route's *JavaScript* only when the user actually navigates to it, instead of bundling every route's code into the initial download. This shrinks the initial bundle size and speeds up first load — the client-side routing counterpart of the general lazy-loading performance idea (System Design Phase 8), applied specifically at route boundaries since routes are a natural "only needed sometimes" unit.

```jsx
import { lazy, Suspense } from 'react';

const ExpenseDetail = lazy(() => import('./ExpenseDetail.jsx'));

<Route
  path="/expenses/:id"
  element={
    <Suspense fallback={<p>Loading…</p>}>
      <ExpenseDetail />
    </Suspense>
  }
/>
```
In this example, the code for `ExpenseDetail` (and anything it exclusively imports) is split into its own JS chunk by the bundler (Vite/Rollup) and only downloaded the first time a user actually navigates to `/expenses/:id` — a user who never visits that route never pays for its code at all, and users on other routes get a smaller initial bundle to download and parse.

**Why it's useful:** As an app grows past a handful of screens, fetching everything up front (or bundling every route's code into one JS file) stops scaling — most users only ever visit a fraction of an app's routes in a session. Loaders push data-fetching earlier (avoiding a loading flash on the common case), and code splitting defers loading code until it's actually needed — both are ways of not paying for what you don't (yet) use.

**Summary / key takeaways:**
- In-component fetching (`useEffect`, what `useExpenses` does) is the simplest but always shows a loading state per visit.
- Router loaders (6.4+ data APIs) fetch before the route component renders, avoiding that initial loading flash; `action`s handle form submissions the same way.
- `React.lazy` + `<Suspense>` code-splits a route's JavaScript so it's only downloaded when visited — a separate, composable optimization from data loading.

## Protected routes & guards

A **protected route** (or "route guard") checks whether the current user is authorized before rendering a route's real content, redirecting to a login page otherwise.

```jsx
function RequireAuth({ children }) {
  const { user } = useAuth();          // e.g., from Context, reading a stored JWT
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

<Route path="/expenses" element={<RequireAuth><ExpenseListPage /></RequireAuth>} />
```
In this example, `RequireAuth` wraps the real page component; if there's no authenticated user, it renders `<Navigate to="/login" />` instead of `children` — `<Navigate>` is a component that, when rendered, performs a redirect (the declarative equivalent of calling `useNavigate()` in an effect). `replace` means the redirect doesn't leave the protected URL in browser history, so the back button after logging in doesn't bounce the user back to a URL they were just redirected away from.

- **Where the auth state typically lives**: Context (Phase 4) or a small state store (Phase 7), populated from a token stored in memory/`localStorage`/an HTTP-only cookie, and typically attached to every API request's `Authorization` header. In the context of this repo's full stack, that token is the JWT issued by the Spring Boot backend's Spring Security setup (Spring Boot Phase 7) — the React app doesn't validate the token itself, it just stores it and attaches it to requests; the backend is the authority on whether it's valid.
- **Client-side guards are a UX feature, not a security boundary.** Hiding a route on the client only prevents a legitimate user from *seeing* a page they shouldn't navigate to by URL — it does nothing to stop a request forged directly against the API. The real authorization check must happen server-side (the Spring Boot API validating the JWT and enforcing permissions on every request); the client-side guard's job is purely to give a good user experience (redirect to login instead of showing broken/empty data or a confusing error).

**Why it's useful:** Without a route guard, an unauthenticated user could navigate directly to a protected URL and see the component mount, attempt to fetch protected data, and likely see confusing error states instead of a clean redirect to log in — the guard turns that into a predictable, deliberate UX flow.

**Summary / key takeaways:**
- A protected route wraps its content in a check (often via Context-stored auth state) and renders `<Navigate to="/login"/>` if unauthenticated.
- The auth token (e.g., a JWT from a Spring Boot backend) typically lives in Context/a store and is attached to outgoing API requests.
- Client-side guards are UX only — real authorization must be enforced server-side; never treat hiding a route client-side as a security control.

## Where this is heading

Everything in this phase — URL-to-component mapping, nested layouts, data loaders, code splitting — is exactly what **Next.js** (track 09) formalizes and builds further into the framework itself: **file-based routing** (a file's path in the `app/`/`pages/` directory *is* its route, no explicit `<Route>` config needed), **server-side rendering and Server Components** (data fetching and even rendering happen on the server before any JS reaches the browser, eliminating client-side loading states for a lot more data than router loaders alone can), and built-in code splitting per route without manual `React.lazy` calls. React Router is the **library-level** version of these same routing ideas, usable in a plain SPA; Next.js is the **framework-level** version, with server rendering as a first-class capability on top.

**Summary / key takeaways:**
- React Router's concepts (route matching, params, nested layouts, loaders, code splitting) are the same ideas Next.js formalizes at the framework level with file-based routing and server rendering.
- Moving from "library adds routing to your app" (React Router) to "framework provides routing and more" (Next.js) is a deliberate architectural choice, not just a syntax difference — covered fully in track 09.
