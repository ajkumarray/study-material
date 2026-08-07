package dev.ajay.expenseapi.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Creates and verifies JWTs (Phase 7.3), using the JJWT library.
 *
 * <p>A JWT is {@code header.payload.signature} (base64url). We sign with an
 * HMAC-SHA key derived from a secret (config: {@code app.jwt.secret}); the
 * signature is what makes the token tamper-proof — change any claim and
 * verification fails. Nothing sensitive goes in the payload (it's only encoded,
 * not encrypted).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // Secret must be >= 256 bits for HS256.
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    /** Issue a signed token whose subject is the username. */
    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Extract the username (subject) if the token is valid; else throw. */
    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    /** True if the signature verifies and the token hasn't expired. */
    public boolean isValid(String token) {
        try {
            return parse(token).getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;                 // bad signature, malformed, expired, etc.
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)          // verify signature with our key
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
