import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ExpenseService } from '../expense.service';
import { Expense } from '../expense.model';

// Reactive Forms: the form model lives in TS (typed, testable), the template binds
// to it. Contrast React's controlled inputs — Angular centralizes form state + validation.
@Component({
  selector: 'app-add-expense',
  imports: [ReactiveFormsModule],
  templateUrl: './add-expense.html',
})
export class AddExpense {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ExpenseService);
  readonly open = signal(false);

  readonly form = this.fb.nonNullable.group({
    description: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    category: ['food' as Expense['category'], Validators.required],
  });

  submit(): void {
    if (this.form.invalid) return;
    const { description, amount, category } = this.form.getRawValue();
    this.service.add({ description, amountCents: Math.round(amount * 100), category });
    this.form.reset({ description: '', amount: 0, category: 'food' });
    this.open.set(false);
  }
}
