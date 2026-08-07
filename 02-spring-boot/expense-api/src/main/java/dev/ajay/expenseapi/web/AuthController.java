package dev.ajay.expenseapi.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.ajay.expenseapi.security.AuthService;
import dev.ajay.expenseapi.web.dto.AuthResponse;
import dev.ajay.expenseapi.web.dto.LoginRequest;
import dev.ajay.expenseapi.web.dto.RegisterRequest;

import jakarta.validation.Valid;

/**
 * Public auth endpoints (Phase 7.3). {@code /api/auth/**} is permitAll in
 * SecurityConfig — you must be able to reach these WITHOUT a token to obtain one.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    // POST /api/auth/register -> 201 + token
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        String token = auth.register(req.username(), req.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.bearer(token));
    }

    // POST /api/auth/login -> 200 + token
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return AuthResponse.bearer(auth.login(req.username(), req.password()));
    }
}
