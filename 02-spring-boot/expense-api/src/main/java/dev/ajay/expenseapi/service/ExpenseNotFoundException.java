package dev.ajay.expenseapi.service;

/** Thrown when an expense id doesn't exist; mapped to HTTP 404 (Phase 5.2). */
public class ExpenseNotFoundException extends RuntimeException {
    public ExpenseNotFoundException(long id) {
        super("expense not found: " + id);
    }
}
