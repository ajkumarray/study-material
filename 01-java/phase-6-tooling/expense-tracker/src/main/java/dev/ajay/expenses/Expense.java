package dev.ajay.expenses;

import java.time.LocalDate;

/**
 * A single expense entry.
 *
 * <p>Lesson 6.3 sidebar — this comment is <b>Javadoc</b>: structured API
 * documentation the {@code javadoc} tool turns into HTML. Write it on every
 * public type/method of a library; document the <i>contract</i> (what, not how).
 *
 * @param description what the money went to; never blank
 * @param amount      amount in rupees; must be positive
 * @param category    spending bucket, used for report grouping
 * @param date        when it happened
 */
public record Expense(String description, double amount, Category category, LocalDate date) {

    /**
     * Compact constructor enforcing the invariants (lesson 2.6).
     *
     * @throws IllegalArgumentException if description is blank or amount is not positive
     */
    public Expense {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got: " + amount);
        }
        description = description.strip();
    }
}
