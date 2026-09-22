<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · routing](../phase-6-routing/NOTES.md)
<!-- /nav -->

# Phase 7 — Ecosystem & Production: Notes

## 7.1 — Testing (implemented: `App.test.jsx`, 6 passing)

The stack: **Vitest** (a fast, Vite-native test runner with a Jest-compatible API — `describe`/`it`/`expect`), **React Testing Library (RTL)** (renders components and queries the result), and **jsdom** (a JavaScript implementation of the DOM/browser APIs that runs inside Node, so `render()` produces a fake-but-realistic DOM tree without ever opening an actual browser).

```js
// From vite.config.js
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,              // describe/it/expect available without imports
    environment: 'jsdom',       // a fake DOM so React can render in Node
    setupFiles: './src/test/setup.js',
  },
});
```

**RTL's philosophy: test what the *user* sees and does, not implementation details.** You query elements the way a real user (or an accessibility tool like a screen reader) would identify them — by their visible text, accessible role, or label — and you interact with them by simulating real user actions (typing, clicking), then assert on what's now visible in the DOM. You do **not** reach into component internals — no testing "did `setState` get called," no querying by CSS class names or component instance methods.

```jsx
// From App.test.jsx
it('adds an expense through the form and updates the total', async () => {
  const user = userEvent.setup();
  render(<App loader={makeFakeApi(seed)} />);
  await screen.findByText('groceries');                       // wait for the initial async load

  await user.type(screen.getByLabelText('description'), 'coffee');
  await user.type(screen.getByLabelText('amount'), '150');
  await user.click(screen.getByRole('button', { name: 'Add' }));

  expect(await screen.findByText('coffee')).toBeInTheDocument();
  await waitFor(() =>
    expect(screen.getByText(/Total: ₹3050\.00/)).toBeInTheDocument()   // 2900 + 150
  );
});
```
In this example, nothing in the test knows or cares that `ExpenseForm` uses `useState` internally, or that submitting calls `onAdd`, or that `Summary` uses `useMemo` — it only interacts the way a real user would (find the description field *by its label*, type into it, find the Add *button by its accessible name*, click it) and asserts on what actually appears in the rendered output (the new expense's text, the updated total). If the component were refactored internally — say, `ExpenseForm`'s four `useState` calls became one `useReducer` — this test would keep passing unchanged, because it never touched those internals in the first place. That's the core payoff of RTL's approach: tests stay resilient to refactors and only break when actual user-visible *behavior* changes.

- **Query priority (roughly, most to least preferred)**: `getByRole` (matches how assistive technology identifies elements — `getByRole('button', { name: 'Add' })` finds the submit button by its accessible role and visible text), `getByLabelText` (form fields, matching `ExpenseForm`'s `aria-label="description"`), `getByText` (visible text content), and only as a last resort `getByTestId` (an explicit `data-testid`, for elements with no meaningful accessible role/text). Preferring role/label queries has a nice side effect: it nudges you toward writing more accessible markup, since a component that's hard to query by role/label is usually also hard for a screen reader user to use.
- **`getBy*` vs. `queryBy*` vs. `findBy*`**:
  - **`getBy*`** — synchronous; throws immediately if no match is found. Use for elements that should already be present.
  - **`queryBy*`** — synchronous; returns `null` instead of throwing if no match is found. Use specifically to assert **absence** (`expect(screen.queryByText('bad')).not.toBeInTheDocument()`), since `getBy*` would throw before you ever got to the assertion.
  - **`findBy*`** — asynchronous (returns a Promise, resolves once found or times out); use for content that appears *after* an effect/fetch resolves, like `await screen.findByText('groceries')` waiting for `useExpenses`'s mount-time fetch to complete.
- **Mock the boundary, not the internals.** `App.test.jsx` injects a hand-rolled fake `loader` object (`makeFakeApi`) implementing the same `list`/`create`/`remove` shape as the real `expenseApi`, entirely in memory — no real network call ever happens, but every real component (`App`, `ExpenseForm`, `ExpenseList`, `ExpenseItem`, `Summary`) and the real `useExpenses` hook run exactly as they would in production. This is only possible because `useExpenses(loader = expenseApi)` was written to *accept* its dependency rather than import and call `expenseApi` directly — the same Dependency Inversion idea as the Java capstone's in-memory repository (Software Design Phase 2), applied at the hook boundary. Mocking closer to the network edge, rather than mocking individual components/hooks, means the test suite exercises the real integration between all the pieces, catching bugs a more granular mock would hide.
- **The testing pyramid, applied here (Java Phase 6.2's framing, same idea)**: mostly fast component/integration tests like this suite (render real components together, fake only the network), fewer slow, full end-to-end tests (Playwright/Cypress, driving a real browser against a real running app+API) reserved for critical user flows across the whole real stack.
- **What the suite actually verifies** (6 tests, all in `App.test.jsx`): loading the list from the API, the computed total via `useMemo` (`Summary`), adding an expense through the form and seeing the total update, form validation (rejecting a non-positive amount, with the invalid entry never appearing in the list), deleting an expense, and the empty-state message when there are no expenses — the whole app's primary user flows, verified headless with zero real network dependency.

**Why it's useful:** Testing through the user-facing API of a component (what's rendered, what you can click/type) rather than its internal implementation means your tests document (and verify) actual product behavior, and they keep working across internal refactors — which is exactly the property you want from a test suite you intend to keep for the life of the codebase, not one you'll be rewriting every time you change how a component is implemented internally.

**Summary / key takeaways:**
- Vitest + RTL + jsdom runs real component tests headless, with no browser needed.
- Query by role/label/text (the way a user or screen reader identifies elements), not by CSS class or internal state — this is RTL's core philosophy.
- `getBy*` (sync, throws if missing), `queryBy*` (sync, returns null — for asserting absence), `findBy*` (async, waits — for post-effect content).
- Mock at the network boundary (a fake `loader`/API object), not by mocking individual components or hooks — this exercises real integration between real pieces.

## 7.2 — State management landscape

For most apps, **local state (`useState`/`useReducer`) + lifting state up + occasional Context** (Phases 2, 4, 5) is entirely sufficient — this is exactly the level `expense-web` operates at, and it doesn't need anything more. As an app's shared state grows in size and update frequency, a few further options exist, each suited to a different kind of state:

- **Context** (built-in, Phase 4) — good for low-frequency, broadly-needed data: current user, theme, locale, feature flags. Its key limitation: every component consuming a given context re-renders whenever that context's value changes, regardless of which specific piece of the value that component actually reads — so it's a poor fit for state that changes often and is read widely.
- **Redux (Redux Toolkit)** — a single, app-wide store updated through reducers and dispatched actions — essentially the `useReducer` pattern (Phase 4) scaled up to the whole application instead of one component. Strengths: a single predictable source of truth, excellent devtools (including time-travel debugging — stepping backward/forward through every state change that ever happened), and a mature ecosystem. Historically criticized for boilerplate; Redux Toolkit (the now-standard way to use Redux) substantially cuts that down with utilities like `createSlice`. Best fit: large apps with complex, deeply-shared client state and a need for strong debugging/traceability guarantees.
- **Zustand / Jotai** — newer, lighter-weight state libraries with much less boilerplate than classic Redux and a hooks-first API (a Zustand store is just a hook you call: `const count = useStore(s => s.count)`), with more granular subscriptions than Context (a component only re-renders when the specific slice it reads changes, not on every store update). Popular as a default choice for apps that need more than Context but don't want Redux's ceremony.
- **React Query (TanStack Query) / SWR** — specifically for **server state**: data that actually lives on a server and is merely cached on the client (lists, records, anything from an API). This is the crucial distinction this section exists to make: a large fraction of what people reach for a "global state library" to manage is actually server data being manually cached and kept in sync by hand — which is exactly the problem `useExpenses`'s hand-rolled `useState`+`useEffect` pattern (Phase 3) solves in a minimal way. A library like React Query replaces that pattern with built-in caching, deduplication, background refetching, and retry, which means an app adopting it typically needs *far less* client-side "global state" than it would otherwise, because a good chunk of what looked like state is really just a cache the library now manages.

| | Best for | Re-render granularity |
|---|---|---|
| Context | Low-frequency, broadly-needed data (theme, user) | Coarse — every consumer re-renders on any change |
| Redux Toolkit | Large apps, complex shared client state, strong devtools needs | Fine, with selectors |
| Zustand / Jotai | Lightweight shared client state, less boilerplate | Fine — subscribe to just the slice you read |
| React Query / SWR | Server state — anything cached from an API | Per-query, with built-in cache invalidation |

**The practical rule: separate server state from UI state, and reach for the lightest tool that actually solves your problem.** Server state (React Query/SWR) and UI state (`useState`/`useReducer`/a small store for genuinely client-only state like "is this modal open") are different problems with different lifecycles — conflating them (e.g., manually caching API responses in a Redux store instead of using a purpose-built data-fetching library) is where a lot of unnecessary state-management complexity in real apps actually comes from.

**Why it's useful:** Picking a heavier tool than the app currently needs (Redux for an app that's really just `expense-web`-sized) adds real cost — more boilerplate, more indirection, more onboarding overhead — for no corresponding benefit; picking a lighter tool than the app needs (trying to coordinate genuinely complex, widely-shared, frequently-changing state with prop drilling alone) creates its own mess of tangled prop chains. Recognizing which category (local, shared-but-simple, shared-and-complex, or server-cached) a given piece of state actually falls into is most of the decision.

**Summary / key takeaways:**
- Local state + lifting + occasional Context handles most apps — `expense-web` is a working example of exactly that being sufficient.
- Context suits low-frequency global data; it re-renders every consumer on any change, so it's a poor fit for high-frequency widely-shared state.
- Redux Toolkit (large, complex shared state, strong devtools) and Zustand/Jotai (lightweight, less boilerplate) are the two common upgrade paths for *client* UI state once Context isn't enough.
- Most "global state" is really server data — React Query/SWR manage that as a cache, which is usually the actual fix rather than a heavier client-state library.

## 7.3 — Performance

**A component re-renders more than beginners expect, and that's usually fine.** A component re-renders whenever its own state changes, its props change, or — importantly — **whenever its parent re-renders, even if this component's own props didn't change** (React does not skip re-rendering a child just because its props look the same, unless you explicitly opt in with `React.memo`). This is by design: the virtual-DOM diff (Phase 1) that follows a re-render is cheap, so "re-render more than strictly necessary, then diff efficiently" is a reasonable default trade-off, not a bug to chase down reflexively.

- **`React.memo`** — a higher-order component that wraps a component and skips re-rendering it if its props are shallowly equal to the previous render's props (comparing each prop with `===`, one level deep — not a deep comparison of nested object contents):
```jsx
// Wrapping a presentational component so it skips re-rendering when its
// props haven't actually changed, even if its parent re-renders:
export const ExpenseItem = React.memo(function ExpenseItem({ expense, onDelete }) {
  const { id, description, amount, category, spentOn } = expense;
  return ( /* ... */ );
});
```
  `React.memo` only helps if the props it's comparing are actually stable across renders when nothing "real" changed — an inline arrow function or object literal passed as a prop (`onDelete={() => remove(id)}`, or `style={{ color: 'red' }}`) is a *new reference* every render, which defeats the shallow comparison every time regardless of memoization. This is exactly why `useCallback`/`useMemo` (Phase 4) matter specifically *at this boundary*: `useExpenses` memoizing `add`/`remove` with `useCallback` is what would let a `React.memo`-wrapped `ExpenseForm`/`ExpenseItem` actually benefit from the memoization — without it, `onDelete`/`onAdd` would be a fresh function every render, and `React.memo` would never see "unchanged props."
- **`useMemo`/`useCallback`** (Phase 4) — stabilize computed values and function identities so children wrapped in `React.memo`, or effects/memos that depend on them, don't see spurious "changes."
- **Correct `key`s** (Phase 2) — beyond correctness, good keys also help performance: a stable key lets the reconciler reuse an unchanged list item's DOM subtree entirely rather than tearing it down and recreating it.
- **Don't premature-optimize.** `React.memo`/`useMemo`/`useCallback` all have their own cost (extra comparisons, extra memory to hold cached values/deps) and add real code complexity — apply them to a *measured* hotspot, found with the **React DevTools Profiler** (records which components rendered, how long each took, and why — including a "why did this render" breakdown), not defensively across the whole tree.
- **Bundle-size performance** (distinct from render performance): **route-based code splitting** (`React.lazy` + `Suspense`, Phase 6) so users only download the JS for routes they actually visit; **tree-shaking** (the bundler — Vite/Rollup here — eliminates unused exports from the final bundle, JS Phase 6's ES-module-based dead-code elimination); and lazy-loading below-the-fold images/components. These are the same front-end performance levers covered generally in System Design Phase 8, applied specifically to a React/Vite build.

**Why it's useful:** Most apps never need to think hard about render performance at all — the virtual DOM diff genuinely is cheap for typical UI sizes. The tools in this section exist for the minority of cases (large lists, expensive per-item rendering, deeply nested trees with frequent updates high up) where a *measured* problem actually shows up — knowing they exist, and specifically knowing that `React.memo` is defeated by unstable prop references, is what separates "used correctly to fix a real slowdown" from "added everywhere and mostly wasted."

**Summary / key takeaways:**
- A component re-renders whenever its state/props change *or its parent re-renders* — that's normal, not automatically a problem.
- `React.memo` skips a re-render only if props are shallowly equal — it's defeated by inline functions/objects as props, which is exactly why `useCallback`/`useMemo` matter at that specific boundary.
- Use the React DevTools Profiler to find real hotspots before reaching for `React.memo`/`useMemo`/`useCallback` — these tools have their own cost and aren't free wins applied blindly.
- Bundle-size optimization (code splitting, tree-shaking) is a separate lever from render optimization — both matter, for different symptoms (slow interactions vs. slow initial load).

## 7.4 — Build & deploy

**Vite** is both the dev server (fast startup, instant Hot Module Replacement during development) and the production bundler for `expense-web`. `npm run build` compiles and bundles the app (JSX → JS, module bundling, minification, tree-shaking) into static `index.html` + hashed `.js`/`.css` files in `dist/` — verified to actually produce a working build in this repo.

```bash
npm run build     # -> dist/ (static HTML/CSS/JS, verified to build)
npm run preview   # serve the dist/ build locally, to sanity-check the production bundle
```

- **Because the output is just static files, a React SPA deploys to any static host or CDN** — Netlify, Vercel, AWS S3 + CloudFront, GitHub Pages, or literally any web server capable of serving files — with **no application server required for the frontend itself**. This is a direct consequence of the CSR (client-side rendering) model: all the rendering logic ships as JavaScript that runs entirely in the user's browser; the "server" only ever needs to hand out the same static files to everyone.
- **The API base URL is configured per environment via env variables**, not hardcoded:
```js
// From expenseApi.js
const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
```
  Vite exposes environment variables prefixed `VITE_` to client code via `import.meta.env` (this prefix requirement is a deliberate Vite security measure — it prevents accidentally bundling *unprefixed* server-side secrets into a client-visible JS file). Setting `VITE_API_URL` differently in a `.env.production` (or via the hosting platform's environment variable settings) versus local development lets the exact same built code point at a different backend per environment, without a code change or separate build per environment.
- **The full-stack picture, tying every track together**: this SPA (static files, served from a CDN) → calls the **Spring Boot expense-api** REST API (track 02) over HTTP → which talks to **Postgres** (track 04) — with requests authenticated via the **JWT issued by Spring Security** (Spring Boot Phase 7), stored client-side (commonly in memory or `localStorage`/a cookie) and attached to each API call. Nothing about the React app needs to run on a server beyond serving its own static files; all actual business logic, data storage, and authorization live in the Spring Boot API and Postgres.
- **Next.js (track 09)** adds server-side rendering on top of this same component model — a deliberate next step once CSR's trade-offs (weaker SEO for public content, a blank-then-populate first paint) actually matter for a given app, not a strict "upgrade path" every app needs.

**Why it's useful:** Understanding that a built React SPA is *just static files* demystifies deployment entirely — there's no special "React server" to run or configure; the same nginx/S3/CDN that could serve a plain HTML page serves the whole app, and the app's only runtime dependency is the separate backend API it calls over the network.

**Summary / key takeaways:**
- `npm run build` produces static HTML/CSS/JS in `dist/` — verified in this repo — deployable to any static host/CDN, with no server needed for the frontend itself.
- Configure environment-specific values (like the API URL) via `VITE_`-prefixed env vars read through `import.meta.env`, not hardcoded.
- Full picture: CDN-served SPA → Spring Boot REST API → Postgres, authenticated via a JWT the React app stores and attaches to requests but never itself validates.
- Next.js layers server rendering on top of the same component model when CSR's trade-offs (SEO, first-paint) start to matter for a given app.
