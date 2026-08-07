package dev.ajay.expenseapi.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Login payload (Phase 7.3). */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) { }
