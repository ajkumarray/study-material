package dev.ajay.expenseapi.security;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The heart of stateless JWT auth (Phase 7.3). Runs once per request
 * (extends {@link OncePerRequestFilter}), placed early in the filter chain.
 *
 * <p>Flow: read the {@code Authorization: Bearer <token>} header → validate the
 * token → load the user → build an {@code Authentication} and put it in the
 * {@link SecurityContextHolder}. From that point the request is "logged in" for
 * this thread only — no server-side session is created (that's what stateless
 * means). If there's no/invalid token, we simply don't authenticate and let the
 * chain continue (authorization rules will then reject protected endpoints).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwt, UserDetailsService userDetailsService) {
        this.jwt = jwt;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);        // no token -> continue unauthenticated
            return;
        }

        String token = header.substring(7);           // strip "Bearer "
        if (jwt.isValid(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String username = jwt.extractUsername(token);
            UserDetails user = userDetailsService.loadUserByUsername(username);

            var auth = new UsernamePasswordAuthenticationToken(
                    user, null, user.getAuthorities());   // credentials null: already verified
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
