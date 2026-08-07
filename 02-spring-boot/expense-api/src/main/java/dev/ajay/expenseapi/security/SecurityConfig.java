package dev.ajay.expenseapi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Central security configuration (Phase 7.1/7.2).
 *
 * <p>Defines the {@link SecurityFilterChain} — which endpoints are public vs
 * protected — plus the {@link PasswordEncoder} (BCrypt) and the
 * {@link AuthenticationManager} (used by the login endpoint to verify
 * credentials). Our {@link JwtAuthFilter} is inserted before the username/
 * password filter so a valid token authenticates the request.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless REST API using bearer tokens (not cookies) -> disable CSRF.
            .csrf(csrf -> csrf.disable())
            // No HTTP session: each request re-authenticates from its token.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()          // register/login open
                    .requestMatchers("/actuator/health").permitAll()      // health public
                    .requestMatchers("/h2-console/**").permitAll()        // dev convenience
                    .anyRequest().authenticated())                        // everything else: token required
            // Unauthenticated request to a protected endpoint -> 401 (not the
            // default 403). 401 = "who are you?" (no/invalid credentials);
            // 403 = "I know you, but you're not allowed" (authenticated, wrong role).
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
            // Run our JWT filter before the standard auth filter.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "authentication required");
    }

    // BCrypt: slow, salted, adaptive hashing. Used to hash on register and
    // verify on login. Never store or compare plaintext passwords.
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Exposes the AuthenticationManager so the login endpoint can authenticate
    // (username + password) using our UserDetailsService + PasswordEncoder.
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
