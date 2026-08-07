package dev.ajay.expenseapi.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import dev.ajay.expenseapi.domain.Category;
import dev.ajay.expenseapi.domain.Expense;

/**
 * Response DTO (Phase 2.3). The shape the API returns — separate from the
 * entity so the persistence model can change without breaking clients.
 */
public record ExpenseResponse(
        Long id,
        String description,
        BigDecimal amount,
        Category category,
        LocalDate spentOn
) {
    /** Map an entity to its API representation. */
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(e.getId(), e.getDescription(), e.getAmount(),
                e.getCategory(), e.getSpentOn());
    }
}
