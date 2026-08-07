<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 08 — React

The most popular UI library. We build the **frontend for the Spring Boot
expense-api** (track 02) — the JavaScript track's raw-DOM todo (JS Phase 7.2)
reborn as a real component app, closing the full-stack loop: React UI → REST API
→ database.

One running project — **`expense-web/`** (Vite + React 18 + Vitest) — evolves
across the phases. It's **fully tested** (`npm test` → 6 passing tests running
headless in jsdom), so the whole app is verified without a browser. Each phase
has `NOTES.md` (the "why") and `INTERVIEW.md`. ⭐ = commonly asked.

Taught in **JSX/JavaScript** (you finished the JS track); TypeScript + React is
track 07's follow-on. Contrasts with the raw DOM (JS 7.2) show what React buys you.

## Run it

```bash
cd expense-web
npm install
npm test          # 6 tests, headless (jsdom)
npm run dev       # dev server at http://localhost:5173 (needs the Spring API on :8080)
npm run build     # production bundle in dist/
```

## Curriculum

### Phase 1 — Fundamentals: components, JSX, props
- [x] 1.1 What React is; the component model; the virtual DOM & reconciliation
- [x] 1.2 JSX; embedding expressions; `ExpenseItem` (a props-driven component)
- [x] 1.3 Composition — small components assembled into bigger ones

### Phase 2 — State & events
- [x] 2.1 `useState`; the render loop (state → UI, event → setState → re-render)
- [x] 2.2 Event handling; controlled inputs & forms (`ExpenseForm`)
- [x] 2.3 Rendering lists with `key`; conditional rendering (`ExpenseList`)

### Phase 3 — Effects & data fetching
- [x] 3.1 `useEffect`; the dependency array; running on mount
- [x] 3.2 Fetching from the API; loading/error states; cleanup (avoid leaks)

### Phase 4 — Hooks deep dive
- [x] 4.1 Custom hooks (`useExpenses`) — reusable stateful logic
- [x] 4.2 `useMemo` for derived state (`Summary`); `useCallback`; `useRef`/`useReducer`/`useContext` (NOTES)

### Phase 5 — Component patterns
- [x] 5.1 Lifting state up; one-way data flow (`App` as container)
- [x] 5.2 Container vs presentational; composition over inheritance

### Phase 6 — Routing & data (NOTES — router not installed)
- [x] 6.1 React Router concepts; client-side routing; data loading patterns

### Phase 7 — Ecosystem & production
- [x] 7.1 Testing with Vitest + React Testing Library (`App.test.jsx`)
- [x] 7.2 State management landscape (Context / Redux / Zustand / React Query) — NOTES
- [x] 7.3 Performance (memo, keys, code-splitting); build & deploy — NOTES

### Capstone
- [x] `expense-web` = the tested expense-tracker frontend consuming the Spring
      Boot API. Full-stack loop complete: React → REST → JPA → Postgres.

## Structure

```
08-react/
├── README.md
├── expense-web/            <- the Vite + React + Vitest project
│   ├── src/components/     <- ExpenseItem, ExpenseList, ExpenseForm, Summary
│   ├── src/hooks/          <- useExpenses (custom hook)
│   ├── src/api/            <- expenseApi (talks to Spring Boot)
│   ├── src/App.jsx         <- container (lifts state)
│   └── src/App.test.jsx    <- 6 passing tests
├── phase-1-fundamentals/   <- NOTES.md + INTERVIEW.md
└── ...
```

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · fundamentals** — [Notes](phase-1-fundamentals/NOTES.md) · [Interview](phase-1-fundamentals/INTERVIEW.md)
- **Phase 2 · state events** — [Notes](phase-2-state-events/NOTES.md) · [Interview](phase-2-state-events/INTERVIEW.md)
- **Phase 3 · effects** — [Notes](phase-3-effects/NOTES.md) · [Interview](phase-3-effects/INTERVIEW.md)
- **Phase 4 · hooks** — [Notes](phase-4-hooks/NOTES.md) · [Interview](phase-4-hooks/INTERVIEW.md)
- **Phase 5 · patterns** — [Notes](phase-5-patterns/NOTES.md) · [Interview](phase-5-patterns/INTERVIEW.md)
- **Phase 6 · routing** — [Notes](phase-6-routing/NOTES.md) · [Interview](phase-6-routing/INTERVIEW.md)
- **Phase 7 · production** — [Notes](phase-7-production/NOTES.md) · [Interview](phase-7-production/INTERVIEW.md)
<!-- /phases-nav -->
