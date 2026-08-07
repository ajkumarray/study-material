package dev.ajay.expenseapi.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ajay.expenseapi.user.AppUser;
import dev.ajay.expenseapi.user.Role;
import dev.ajay.expenseapi.user.UserRepository;

/**
 * Registration + login logic (Phase 7). Register hashes the password with
 * BCrypt and stores the user; login delegates to the {@link AuthenticationManager}
 * (which uses our UserDetailsService + PasswordEncoder to verify credentials),
 * then issues a JWT. Both paths return a signed token.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder,
                       AuthenticationManager authManager, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
    }

    @Transactional
    public String register(String username, String rawPassword) {
        if (users.existsByUsername(username)) {
            throw new UsernameTakenException(username);
        }
        // Store the HASH, never the raw password.
        users.save(new AppUser(username, encoder.encode(rawPassword), Role.USER));
        return jwt.generateToken(username);
    }

    public String login(String username, String rawPassword) {
        // Throws (BadCredentialsException) if the password doesn't match the hash.
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, rawPassword));
        return jwt.generateToken(username);
    }
}
