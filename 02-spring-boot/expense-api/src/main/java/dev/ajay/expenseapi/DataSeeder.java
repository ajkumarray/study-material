package dev.ajay.expenseapi;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.ajay.expenseapi.domain.Category;
import dev.ajay.expenseapi.domain.Expense;
import dev.ajay.expenseapi.repository.ExpenseRepository;
import dev.ajay.expenseapi.user.AppUser;
import dev.ajay.expenseapi.user.Role;
import dev.ajay.expenseapi.user.UserRepository;

/**
 * Seeds demo data on startup (Phase 1/3). A {@code CommandLineRunner} bean runs
 * once after the context is ready. {@code @Profile("!test")} keeps it OUT of
 * the test profile so tests start from a clean DB.
 */
@Configuration
@Profile("!test")
public class DataSeeder {

    @Bean
    CommandLineRunner seed(ExpenseRepository repo, UserRepository users, PasswordEncoder encoder) {
        return args -> {
            // A demo login so you can try the API immediately:
            //   POST /api/auth/login {"username":"demo","password":"password123"}
            if (!users.existsByUsername("demo")) {
                users.save(new AppUser("demo", encoder.encode("password123"), Role.USER));
            }
            if (repo.count() > 0) return;
            repo.save(new Expense("groceries", new BigDecimal("2400.50"), Category.FOOD, LocalDate.now()));
            repo.save(new Expense("metro card", new BigDecimal("500"), Category.TRANSPORT, LocalDate.now().minusDays(1)));
            repo.save(new Expense("rent", new BigDecimal("22000"), Category.RENT, LocalDate.now().minusDays(3)));
        };
    }
}
