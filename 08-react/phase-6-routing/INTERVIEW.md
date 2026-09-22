<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · patterns](../phase-5-patterns/NOTES.md) | [Phase 7 · production ➡](../phase-7-production/NOTES.md)
<!-- /nav -->

# Phase 6 — Routing & Data: Interview Q&A

⭐ = asked constantly.

**Q: What is client-side routing, and what does "SPA" mean in this context?** ⭐⭐
A Single-Page Application loads exactly one HTML document from the server, and from then on JavaScript handles all navigation by swapping which components render, updating the URL via the browser's History API (`pushState`/`popState`) rather than requesting a new HTML page. Client-side routing is the layer that maps the current URL to which component tree should render, and keeps back/forward navigation, bookmarking, and direct links working correctly even though no real page reload happens for internal navigation. React Router is the standard library implementing this for React apps — it isn't part of React itself, which is deliberately routing-agnostic.

**Q: Why use `<Link>` instead of a plain `<a href>` for internal navigation?** ⭐
A plain `<a href="/expenses">` is a real browser link — clicking it triggers a full page navigation: the browser discards the current page, requests a new HTML document, and re-downloads and re-initializes the entire JS bundle from scratch, defeating the whole point of an SPA (and losing any in-memory state). `<Link to="/expenses">` intercepts the click, prevents the default browser navigation, and instead updates the URL via the History API while React swaps only the components that need to change — instant, with existing state elsewhere in the app preserved. The rule of thumb: `<Link>`/`<NavLink>` for anything staying inside the app, plain `<a href>` only for genuinely external URLs (or a full-page reload you actually want).

**Q: How do you read a URL parameter or query string in a routed component?**
`useParams()` reads path segments declared with a `:name` pattern in the route (`path="/expenses/:id"` → `useParams()` returns `{ id: '42' }` when visiting `/expenses/42`) — note it's always a string parsed out of the URL, so convert with `Number(id)` if a numeric type is needed, the same caveat as any other string-sourced form/URL value. `useSearchParams()` reads and updates the query string (`?category=food`) — useful for filter/sort/pagination state you want to be shareable and bookmarkable via the URL itself, rather than kept in local component state that resets on refresh. `useNavigate()` returns a function for *programmatic* navigation, for cases that aren't a direct link click — most commonly redirecting after a successful form submission or action.

**Q: What are React Router's "data APIs" / route loaders, and what problem do they solve versus in-component fetching?**
A route declared via `createBrowserRouter` can specify a `loader` function that the router calls *before* rendering that route's component, retrieving the data via `useLoaderData()` inside the component once it does render:
```jsx
const router = createBrowserRouter([
  { path: '/expenses/:id', element: <ExpenseDetail />, loader: ({ params }) => expenseApi.get(params.id) },
]);
function ExpenseDetail() {
  const expense = useLoaderData();   // already resolved by the time this renders
  return <p>{expense.description}</p>;
}
```
This solves the "flash of loading state on every navigation" problem inherent to in-component fetching (what `useExpenses` does today, in `useEffect` after the component has already mounted) — because the loader runs *before* the component is rendered for that navigation, the initial render already has its data, with no separate loading UI needed inside the component itself for that first paint. `action`s are the write-side counterpart, handling `<Form method="post">` submissions similarly, without the manual `onSubmit`/`fetch`/`useState` wiring `ExpenseForm` currently does by hand.

*Follow-up: does this mean you should always prefer loaders over `useEffect` fetching?* For a route's primary data, yes if you're using React Router's data-router APIs at all — loaders are strictly better for the initial-render-loading-flash problem. But `useEffect`-based fetching (or a data-fetching library) is still appropriate for data that isn't tied to route entry — data fetched in response to user interaction after the page is already showing, polling, or data needed by a component that isn't itself a routed page.

**Q: What is route-based code splitting, and how do you implement it?** ⭐
Loading a route's JavaScript bundle only when the user actually navigates to it, instead of including every route's code in the app's initial download — implemented with `React.lazy(() => import('./ExpenseDetail.jsx'))` wrapped in a `<Suspense fallback={...}>` boundary that renders while the chunk downloads:
```jsx
const ExpenseDetail = lazy(() => import('./ExpenseDetail.jsx'));
<Route path="/expenses/:id" element={<Suspense fallback={<p>Loading…</p>}><ExpenseDetail /></Suspense>} />
```
This is a build-tool-level optimization (the bundler splits `ExpenseDetail`'s code into its own chunk) rather than a data-fetching one — it's completely independent of, and complementary to, router loaders: loaders solve "when does *data* arrive," code splitting solves "when does *code* arrive." A user who never visits `/expenses/:id` never downloads its JS at all, which matters increasingly as an app accumulates more routes/pages.

**Q: How do you protect a route that requires authentication?**
Wrap the route's element in a guard component that checks the current auth state (commonly read from Context or a small state store, populated from a token like a JWT) and renders `<Navigate to="/login" replace />` instead of the real content when unauthenticated:
```jsx
function RequireAuth({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return children;
}
```
The `replace` flag avoids leaving the protected URL in browser history, so pressing back after logging in doesn't bounce through the URL that triggered the redirect. The auth token itself is typically attached to every outgoing API request's `Authorization` header — in a stack like this repo's (React frontend, Spring Boot backend), that's the JWT issued by Spring Security (Spring Boot Phase 7).

*Follow-up: is a client-side route guard actually a security measure?* No, and this is a common interview trap — a client-side guard only improves UX by redirecting a legitimate but unauthenticated user cleanly to login instead of showing a broken page; it does nothing to stop someone from bypassing the React app entirely and calling the API directly. Real authorization must be enforced server-side, on every request, by validating the JWT there — the client-side guard's absence or presence has zero effect on whether the API actually protects its data.

**Q: How does nested routing with an `<Outlet/>` work, and why use it?**
A parent `<Route>` can wrap child `<Route>`s and render a layout component containing an `<Outlet/>` — the child route matching the current URL renders wherever `<Outlet/>` appears in that layout, while everything else in the layout (nav bar, sidebar, footer) renders exactly once and stays mounted across navigations between its children, instead of being torn down and rebuilt every time the user moves between child routes. This both avoids unnecessary re-mounting of persistent chrome and lets you express "these routes share this layout" declaratively in the route tree, instead of manually including the nav/sidebar JSX inside every individual page component.

**Q: How does React Router relate to Next.js, and when would you choose one over the other?**
React Router is a client-side routing *library* you add to a plain React SPA (like `expense-web`) — it handles URL-to-component mapping, nested layouts, and (with the 6.4+ data APIs) loaders/actions, entirely running in the browser. Next.js (track 09) is a *framework* built around the same core routing concepts but implemented as file-based routing (a file's location in the `app/`/`pages/` directory defines its route) plus server-side rendering and Server Components, which can fetch and even render on the server before any JavaScript reaches the browser at all — moving more of the "avoid a loading flash" problem to the server than router loaders alone can, and improving SEO/first-paint for content that doesn't need to be interactive immediately. Choose React Router when you're building (or already have) a client-rendered SPA and just need routing; choose Next.js when you're starting fresh and want server rendering, file-based routing, and tighter framework integration from the start.

**Q: What's the difference between in-component fetching, router loaders, and server rendering, in terms of when data arrives relative to the first paint?**
In-component fetching (`useEffect`, what `useExpenses` does): the component mounts and paints first (often showing a loading state), *then* the fetch resolves and a second render shows the data — there's always a visible gap. Router loaders: the router fetches data *before* rendering the route's component for that navigation, so the component's first render already has the data — no loading gap for that route's own data, though the very first app load still needs the initial JS bundle downloaded and parsed before any of this can run. Server rendering (Next.js): the server fetches data and renders HTML *before ever sending a response to the browser* — the user's very first paint already contains real content, with no client-side loading state needed at all for that data, at the cost of a server round-trip on every server-rendered request instead of an instant client-side transition.
