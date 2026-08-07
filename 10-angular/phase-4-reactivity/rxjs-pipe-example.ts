/**
 * Phase 4 — RxJS `.pipe()` data-transformation examples.
 *
 * A companion to NOTES.md §4.2/§4.3. Shows how to take an Observable *stream* and
 * transform the data flowing through it with `.pipe(...operators)`, then consume the
 * result the Angular way (async pipe / toSignal) — never a bare `.subscribe()`.
 *
 * This mirrors the real `expense.service.ts` in ../expense-app so it's type-correct.
 * It's a service (no template), so it type-checks with plain `tsc` — verified against
 * the expense-app's installed @angular/core + rxjs types.
 *
 * The golden rule of `.pipe()`: each operator takes a stream IN and returns a NEW
 * stream OUT. Nothing is mutated; nothing runs until something subscribes (lazy).
 *
 *     source$.pipe(opA(), opB(), opC())   // → a brand-new Observable
 */

import { Injectable, computed, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  Observable,
  Subject,
  of,
  delay,
  map,
  filter,
  scan,
  tap,
  catchError,
  debounceTime,
  distinctUntilChanged,
  switchMap,
  startWith,
} from 'rxjs';

// The same model the expense-app uses.
export interface Expense {
  id: number;
  description: string;
  amountCents: number;
  category: 'food' | 'transport' | 'housing' | 'leisure' | 'other';
}

// A display-shaped view produced by transforming the raw data.
export interface ExpenseView {
  label: string;
  dollars: number;
}

@Injectable({ providedIn: 'root' })
export class RxjsPipeExampleService {
  // A stand-in for an HttpClient call: emits Expense[] once, after a fake delay.
  // In a real app this is `this.http.get<Expense[]>('/api/expenses')`.
  private loadFromApi(): Observable<Expense[]> {
    const data: Expense[] = [
      { id: 1, description: 'Lunch', amountCents: 1250, category: 'food' },
      { id: 2, description: 'Bus pass', amountCents: 3000, category: 'transport' },
      { id: 3, description: 'Coffee', amountCents: 450, category: 'food' },
    ];
    return of(data).pipe(delay(10)); // `of(...).pipe(delay())` simulates async I/O
  }

  // ───────────────────────────────────────────────────────────────────────────
  // 1) map + filter — the everyday transform: reshape each emitted value.
  //    Here: Expense[] → only "food" → a lighter display shape (ExpenseView[]).
  // ───────────────────────────────────────────────────────────────────────────
  readonly foodExpenses$: Observable<ExpenseView[]> = this.loadFromApi().pipe(
    map((expenses) => expenses.filter((e) => e.category === 'food')), // keep food only
    map((foods) =>
      foods.map((e) => ({ label: e.description, dollars: e.amountCents / 100 })),
    ), // reshape each item
    tap((views) => console.log('[pipe] food views:', views)), // side-effect, stream unchanged
  );

  // ───────────────────────────────────────────────────────────────────────────
  // 2) scan — a running accumulation over the stream (like reduce, but emits each
  //    step). Great for running totals / building state from a sequence of events.
  // ───────────────────────────────────────────────────────────────────────────
  readonly runningTotalDollars$: Observable<number> = this.loadFromApi().pipe(
    // flatten Expense[] into a stream of amounts, then accumulate:
    map((expenses) => expenses.reduce((sum, e) => sum + e.amountCents, 0)),
    scan((total, batch) => total + batch, 0),
    map((cents) => cents / 100),
  );

  // ───────────────────────────────────────────────────────────────────────────
  // 3) switchMap — transforming a stream *of* streams (the classic search box).
  //    A plain `map` here would give Observable<Observable<...>>; switchMap flattens
  //    it AND cancels the previous inner request when a new term arrives.
  // ───────────────────────────────────────────────────────────────────────────
  private readonly searchTerm$ = new Subject<string>(); // push terms via search(term)

  readonly searchResults$: Observable<ExpenseView[]> = this.searchTerm$.pipe(
    startWith(''), // emit an initial value so the stream is populated immediately
    debounceTime(300), // wait for typing to pause (rate-limit)
    distinctUntilChanged(), // ignore repeats of the same term
    switchMap((term) => this.searchApi(term)), // cancel-previous + flatten
    map((expenses) =>
      expenses.map((e) => ({ label: e.description, dollars: e.amountCents / 100 })),
    ),
    catchError(() => of([] as ExpenseView[])), // recover: emit an empty list on error
  );

  /** Call from a component (e.g. on input) to feed the search stream. */
  search(term: string): void {
    this.searchTerm$.next(term);
  }

  // A stand-in search endpoint that filters by description.
  private searchApi(term: string): Observable<Expense[]> {
    const t = term.trim().toLowerCase();
    return this.loadFromApi().pipe(
      map((all) => (t ? all.filter((e) => e.description.toLowerCase().includes(t)) : all)),
      delay(10),
    );
  }

  // ───────────────────────────────────────────────────────────────────────────
  // 4) Consuming the transformed stream — the Angular way (no manual subscribe).
  //
  //   (a) In a TEMPLATE, use the async pipe (auto subscribe + unsubscribe):
  //         @if (foodExpenses$ | async; as foods) {
  //           @for (f of foods; track f.label) { <li>{{ f.label }} — {{ f.dollars | currency }}</li> }
  //         }
  //
  //   (b) Or bridge the stream into a SIGNAL with toSignal (fits a signal-based service).
  //       toSignal subscribes for you and cleans up on destroy — leak-free.
  // ───────────────────────────────────────────────────────────────────────────
  readonly foods = toSignal(this.foodExpenses$, { initialValue: [] as ExpenseView[] });
  readonly results = toSignal(this.searchResults$, { initialValue: [] as ExpenseView[] });

  // Derived signal on top of the bridged stream — signals + RxJS working together.
  readonly foodCount = computed(() => this.foods().length);

  // Local signal state, unrelated to the streams, to show the two coexisting.
  readonly lastSearchedAt = signal<number | null>(null);
}

/*
 * Why no `.subscribe()` here?
 * A manual subscription must be manually torn down or it leaks (the component/handler
 * stays alive — see System Design Phase 7). The `async` pipe and `toSignal()` both
 * subscribe AND unsubscribe automatically, so they're the idiomatic consumers.
 *
 * Operator cheat-sheet used above:
 *   map                – transform each value (the workhorse)
 *   filter             – drop values that don't match
 *   tap                – side-effect without changing the stream (logging/debug)
 *   scan               – running accumulation, emits each step
 *   debounceTime       – wait for a pause (rate-limit bursty input)
 *   distinctUntilChanged – skip consecutive duplicates
 *   switchMap          – flatten a stream-of-streams, cancelling the previous inner one
 *                        (mergeMap = keep all concurrent; concatMap = keep order)
 *   catchError         – recover by switching to a fallback stream
 *   startWith          – seed an initial value
 */
