import { Component, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ExpenseService } from '../expense.service';
import { AddExpense } from '../add-expense/add-expense';

// A standalone component: it declares its own template dependencies in `imports`
// (no NgModule). `inject()` pulls the service from DI.
@Component({
  selector: 'app-expense-list',
  imports: [RouterLink, AddExpense, CurrencyPipe],
  templateUrl: './expense-list.html',
})
export class ExpenseList {
  private readonly service = inject(ExpenseService);
  // Bind the service's signals straight into the template.
  readonly expenses = this.service.expenses;
  readonly totalCents = this.service.totalCents;
}
