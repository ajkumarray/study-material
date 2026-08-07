# Capstone — The Angular Expense App (`expense-app/`)

The Angular track's synthesis: a **real Angular app that builds** (`ng build` verified),
scaffolded with the CLI and written in the modern **standalone + signals** style, on the
repo's running expense theme.

```bash
cd 10-angular/expense-app
npm install
npm run build     # ng build — verified (incl. a lazy-loaded chunk)
npm start         # ng serve → http://localhost:4200
```

## Verified build output

```
Initial chunk files | Names          | Raw size
chunk-....js        | -              | 240 kB     (framework)
main-....js         | main           |  48 kB
Lazy chunk files    | Names          |
chunk-....js        | expense-detail | 928 bytes  ← lazy-loaded route
Application bundle generation complete.
```

The `expense-detail` route is a **separate lazy chunk** — proof the `loadComponent`
code-splitting works.

## Every phase, applied

| Phase | Concept | Where in the app |
|---|---|---|
| 1 | Standalone components + bootstrap | every component; `main.ts` + `app.config.ts` |
| 2 | New control flow + pipes | `@for (…; track e.id)` / `@if` and the `currency` pipe in the list/detail templates |
| 3 | DI + `@Injectable` singleton service | `ExpenseService` (`providedIn: 'root'`) injected via `inject()` into every component |
| 4 | Signals + `computed` (+ RxJS) | service state is a `signal`, `totalCents` is `computed`; `loadFromApi()` returns an RxJS `Observable` |
| 5 | Router + lazy load + Reactive Forms | `app.routes.ts` (lazy detail, `withComponentInputBinding`), `AddExpense` reactive form with validators |

## The architecture in one sentence

A single **injected signal service** owns the expense state; **standalone components**
inject it and render its signals with the new `@for`/`@if` control flow; the **router**
maps `/expenses/:id` (lazy-loaded) to a detail component whose route param binds straight
to an `input()`; and a **Reactive Form** adds expenses through the service, whose
`computed` total updates every dependent view automatically.

## React ↔ Angular, side by side (both apps exist in this repo)

| Concern | React (`08-react/expense-web`) | Angular (`10-angular/expense-app`) |
|---|---|---|
| Component | function + JSX | class + `@Component` + HTML template |
| State | `useState` hook | `signal()` |
| Derived state | `useMemo` | `computed()` |
| Shared state/logic | custom hook / context | injected `@Injectable` service |
| Async | `useEffect` + fetch | RxJS `Observable` + `async` pipe |
| Routing | react-router (library) | first-party Router |
| Forms | controlled inputs | Reactive Forms |

Same app, two philosophies: React = library + hooks + you-assemble; Angular = framework
+ DI + classes + batteries-included. Having built both, you can speak to the trade-off
directly — the most valuable thing an interview wants here.

## Connections

- Contrasts the **React (08)** app feature-for-feature; both consume typed models from
  **TypeScript (07)**.
- The DI/service model is the **Spring Boot (02)** IoC pattern on the frontend (Software
  Design Phase 6).
- The production `dist/` bundle is what **Docker (12)**/**CI-CD (13)** would ship (served
  as static files behind Nginx or a CDN).
