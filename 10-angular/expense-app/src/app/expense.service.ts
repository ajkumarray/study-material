import { Injectable, computed, signal } from '@angular/core';
import { Observable, delay, of } from 'rxjs';
import { Expense } from './expense.model';

// `providedIn: 'root'` registers a single app-wide instance with Angular's DI
// container (like a Spring @Service singleton). Components inject it via `inject()`.
@Injectable({ providedIn: 'root' })
export class ExpenseService {
  // STATE as a signal: a reactive value. Reading it in a template subscribes the
  // view; setting it re-renders exactly what depends on it (fine-grained reactivity).
  private readonly _expenses = signal<Expense[]>([
    { id: 1, description: 'Lunch', amountCents: 1250, category: 'food' },
    { id: 2, description: 'Bus pass', amountCents: 3000, category: 'transport' },
    { id: 3, description: 'Rent', amountCents: 90000, category: 'housing' },
  ]);

  // Expose read-only + DERIVED state via computed signals (recompute only when deps change).
  readonly expenses = this._expenses.asReadonly();
  readonly totalCents = computed(() => this._expenses().reduce((s, e) => s + e.amountCents, 0));

  // RxJS side: model async I/O as an Observable (what an HttpClient call returns).
  // `of(...).pipe(delay())` simulates a network fetch stream.
  loadFromApi(): Observable<Expense[]> {
    return of(this._expenses()).pipe(delay(10));
  }

  getById(id: number): Expense | undefined {
    return this._expenses().find((e) => e.id === id);
  }

  add(input: Omit<Expense, 'id'>): void {
    // `update` mutates the signal immutably; every dependent view/computed refreshes.
    this._expenses.update((list) => [...list, { id: list.length + 1, ...input }]);
  }
}
