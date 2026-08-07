package dev.ajay.expenseapi.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import dev.ajay.expenseapi.user.UserRepository;

/**
 * Bridges our {@code AppUser} to Spring Security (Phase 7.2). Security calls
 * {@code loadUserByUsername} during authentication; we look the user up and
 * hand back a Spring {@link UserDetails} (username + hashed password +
 * authorities). Security then compares the submitted password against the
 * stored hash using the {@code PasswordEncoder}.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPassword())            // the BCrypt hash
                        .roles(u.getRole().name())            // ROLE_USER / ROLE_ADMIN
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("user not found: " + username));
    }
}
