import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { ExpenseService } from '../expense.service';

// With `withComponentInputBinding()` (wired in app.config), route params bind
// straight to component `input()`s — `id` here comes from the /expenses/:id URL.
@Component({
  selector: 'app-expense-detail',
  imports: [RouterLink, CurrencyPipe],
  templateUrl: './expense-detail.html',
})
export class ExpenseDetail {
  private readonly service = inject(ExpenseService);
  // Route param arrives as a string; convert with a transform.
  readonly id = input.required({ transform: (v: string) => Number(v) });
  readonly expense = computed(() => this.service.getById(this.id()));
}
