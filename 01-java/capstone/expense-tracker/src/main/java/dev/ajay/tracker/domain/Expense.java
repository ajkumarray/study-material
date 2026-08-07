package dev.ajay.tracker.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single expense. Immutable value object (Phase 2.6 record) with its
 * invariants enforced in the compact constructor.
 *
 * <p>{@code id} is {@code null} for a not-yet-persisted expense and set by
 * the repository after insert — the "transient vs managed" idea from 7.3,
 * done by hand.
 *
 * @param id          database id, or null before persistence
 * @param description what the money went to; never blank
 * @param amount      positive money value (DECIMAL in the DB, never double)
 * @param category    spending bucket
 * @param spentOn     the date it happened; not in the future
 */
public record Expense(Long id, String description, BigDecimal amount,
                      Category category, LocalDate spentOn) {

    public Expense {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive, got: " + amount);
        }
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (spentOn == null || spentOn.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("spentOn must be a past or present date");
        }
        description = description.strip();
    }

    /** Factory for a new (unsaved) expense — id is assigned on persist. */
    public static Expense of(String description, BigDecimal amount,
                             Category category, LocalDate spentOn) {
        return new Expense(null, description, amount, category, spentOn);
    }

    /** Returns a copy carrying the database-assigned id (records are immutable). */
    public Expense withId(long newId) {
        return new Expense(newId, description, amount, category, spentOn);
    }
}
