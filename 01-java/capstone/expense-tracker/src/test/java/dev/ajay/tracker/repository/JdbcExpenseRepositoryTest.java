package dev.ajay.tracker.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.ajay.tracker.domain.Category;
import dev.ajay.tracker.domain.Expense;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the JDBC repository against a REAL (throwaway,
 * in-memory) H2 database — each test class gets a fresh unique DB via a
 * random name, so tests stay isolated. (Phase 6.2 + 7.1)
 */
@DisplayName("JdbcExpenseRepository (integration)")
class JdbcExpenseRepositoryTest {

    private ExpenseRepository repo;

    @BeforeEach
    void setUp() {
        // Unique in-memory DB per test -> no shared state (FIRST: Independent).
        DataSource ds = Database.pooled("jdbc:h2:mem:test_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        repo = new JdbcExpenseRepository(ds);
    }

    private Expense sample(String desc, String amount) {
        return Expense.of(desc, new BigDecimal(amount), Category.FOOD, LocalDate.now());
    }

    @Test
    @DisplayName("save assigns a generated id and round-trips")
    void saveAndFind() {
        Expense saved = repo.save(sample("coffee", "150"));

        assertThat(saved.id()).isNotNull().isPositive();
        assertThat(repo.findById(saved.id()))
                .isPresent()
                .get()
                .satisfies(e -> {
                    assertThat(e.description()).isEqualTo("coffee");
                    assertThat(e.amount()).isEqualByComparingTo("150");
                    assertThat(e.category()).isEqualTo(Category.FOOD);
                });
    }

    @Test
    @DisplayName("findById returns empty for a missing id")
    void findMissing() {
        assertThat(repo.findById(999)).isEmpty();
    }

    @Test
    @DisplayName("findAll returns newest first")
    void findAllOrdered() {
        repo.save(Expense.of("old", new BigDecimal("10"), Category.OTHER, LocalDate.now().minusDays(5)));
        repo.save(Expense.of("new", new BigDecimal("20"), Category.OTHER, LocalDate.now()));

        assertThat(repo.findAll())
                .extracting(Expense::description)
                .containsExactly("new", "old");
    }

    @Test
    @DisplayName("findSince filters by date")
    void findSinceFilters() {
        repo.save(Expense.of("old", new BigDecimal("10"), Category.OTHER, LocalDate.now().minusDays(10)));
        repo.save(Expense.of("recent", new BigDecimal("20"), Category.OTHER, LocalDate.now()));

        assertThat(repo.findSince(LocalDate.now().minusDays(3)))
                .extracting(Expense::description)
                .containsExactly("recent");
    }

    @Test
    @DisplayName("delete removes the row and reports outcome")
    void deleteWorks() {
        Expense saved = repo.save(sample("temp", "99"));

        assertThat(repo.deleteById(saved.id())).isTrue();
        assertThat(repo.deleteById(saved.id())).isFalse();
        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("count reflects saves")
    void countReflectsSaves() {
        assertThat(repo.count()).isZero();
        repo.save(sample("a", "1"));
        repo.save(sample("b", "2"));
        assertThat(repo.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("BigDecimal precision survives the round trip (money safety)")
    void moneyPrecision() {
        Expense saved = repo.save(sample("precise", "1234.56"));
        assertThat(repo.findById(saved.id()).orElseThrow().amount())
                .isEqualByComparingTo("1234.56");
    }
}
