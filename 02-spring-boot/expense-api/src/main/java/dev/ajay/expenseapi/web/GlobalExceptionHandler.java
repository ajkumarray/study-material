package dev.ajay.expenseapi.web;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.security.authentication.BadCredentialsException;

import dev.ajay.expenseapi.security.UsernameTakenException;
import dev.ajay.expenseapi.service.ExpenseNotFoundException;

/**
 * Centralized error handling (Phase 5.2) — one place that turns exceptions
 * into clean HTTP responses, so controllers stay focused on the happy path
 * (the Java Phase 3.1 "exception translation" idea, at the web layer).
 *
 * <p>{@code @RestControllerAdvice} applies these handlers across ALL
 * controllers. We return {@link ProblemDetail} (RFC 7807, Phase 5.3) — the
 * standard machine-readable error format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 — the resource doesn't exist.
    @ExceptionHandler(ExpenseNotFoundException.class)
    public ProblemDetail handleNotFound(ExpenseNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 400 — Bean Validation failed on a @Valid body. Collect field errors.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "validation failed");
        pd.setProperty("errors", fieldErrors);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // 409 — registering a username that already exists.
    @ExceptionHandler(UsernameTakenException.class)
    public ProblemDetail handleUsernameTaken(UsernameTakenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 401 — wrong username/password at login.
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "invalid credentials");
    }

    // 500 — anything unexpected. Don't leak internals to the client.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "unexpected error");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
