package dev.ajay.tracker;

/**
 * Unchecked wrapper for persistence failures (Phase 3.1 exception
 * translation): the repository catches low-level {@link java.sql.SQLException}
 * and rethrows this, so upper layers never import {@code java.sql}.
 * This is exactly what Spring's {@code DataAccessException} does.
 */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
