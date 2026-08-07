package dev.ajay.expenseapi.web.dto;

/** The token handed back after register/login (Phase 7.3). */
public record AuthResponse(String token, String tokenType) {
    public static AuthResponse bearer(String token) {
        return new AuthResponse(token, "Bearer");
    }
}
