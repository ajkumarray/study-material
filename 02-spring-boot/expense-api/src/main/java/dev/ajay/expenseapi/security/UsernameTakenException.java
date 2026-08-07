package dev.ajay.expenseapi.security;

/** Thrown when registering a username that already exists; mapped to 409. */
public class UsernameTakenException extends RuntimeException {
    public UsernameTakenException(String username) {
        super("username already taken: " + username);
    }
}
