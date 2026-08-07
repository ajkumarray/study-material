package dev.ajay.expenseapi.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import dev.ajay.expenseapi.domain.Category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO (Phase 2.3 + validation Phase 5.1). A record is perfect here —
 * immutable, and Jackson binds JSON to it. Bean Validation annotations declare
 * the rules; {@code @Valid} in the controller triggers them BEFORE our code
 * runs, so invalid input never reaches the service.
 *
 * <p>Why a DTO instead of exposing the entity: decouples the API contract from
 * the DB schema, hides the id (server-assigned), and lets validation live at
 * the edge. This is the same "don't leak internals" lesson as encapsulation.
 */
public record CreateExpenseRequest(
        @NotBlank(message = "description must not be blank")
        String description,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount,

        @NotNull(message = "category is required")
        Category category,

        @NotNull(message = "spentOn is required")
        @PastOrPresent(message = "spentOn must not be in the future")
        LocalDate spentOn
) { }
