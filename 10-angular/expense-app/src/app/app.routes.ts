import { Routes } from '@angular/router';
import { ExpenseList } from './expense-list/expense-list';

export const routes: Routes = [
  { path: '', redirectTo: 'expenses', pathMatch: 'full' },
  { path: 'expenses', component: ExpenseList, title: 'All expenses' },
  {
    // Lazy loading: the detail component is code-split and loaded on demand.
    path: 'expenses/:id',
    loadComponent: () =>
      import('./expense-detail/expense-detail').then((m) => m.ExpenseDetail),
    title: 'Expense detail',
  },
];
