package dev.ajay.expenseapi.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.ajay.expenseapi.domain.Category;
import dev.ajay.expenseapi.domain.Expense;

/**
 * Spring Data JPA repository (Phase 4.1) — THE payoff moment.
 *
 * <p>In the Java capstone, {@code JdbcExpenseRepository} was ~120 lines of
 * PreparedStatements, ResultSet mapping, and connection handling. Here we
 * declare an INTERFACE and Spring Data generates the implementation at
 * startup: {@code save}, {@code findById}, {@code findAll}, {@code deleteById},
 * {@code count}, and more come from {@link JpaRepository}<Entity, IdType>.
 *
 * <p>The methods below are DERIVED QUERIES (Phase 4.2): Spring parses the
 * METHOD NAME into a query. {@code findByCategory} → {@code WHERE category = ?}.
 * No SQL written, no implementation written — the name IS the query.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // WHERE category = ?
    List<Expense> findByCategory(Category category);

    // WHERE spent_on >= ? ORDER BY spent_on DESC
    List<Expense> findBySpentOnGreaterThanEqualOrderBySpentOnDesc(LocalDate from);

    // WHERE amount > ?  — derived from the name; returns matching expenses
    List<Expense> findByAmountGreaterThan(java.math.BigDecimal threshold);
}
