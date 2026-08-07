package dev.ajay.expenseapi.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for users (Phase 4 pattern, reused). */
public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);   // derived query

    boolean existsByUsername(String username);
}
