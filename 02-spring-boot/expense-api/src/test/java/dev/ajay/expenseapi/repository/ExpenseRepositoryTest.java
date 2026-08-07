package dev.ajay.expenseapi.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import dev.ajay.expenseapi.domain.Category;
import dev.ajay.expenseapi.domain.Expense;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence-slice test (Phase 6.1). {@code @DataJpaTest} loads only the JPA
 * layer against an in-memory DB and rolls back after each test. It proves the
 * DERIVED QUERIES actually generate correct SQL — the part Spring wrote for us.
 */
@DataJpaTest
class ExpenseRepositoryTest {

    @Autowired
    ExpenseRepository repo;

    @Test
    void savesAndGeneratesId() {
        Expense saved = repo.save(new Expense("coffee", new BigDecimal("150"), Category.FOOD, LocalDate.now()));
        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(repo.findById(saved.getId())).isPresent();
    }

    @Test
    void derivedQueryFindByCategory() {
        repo.save(new Expense("a", new BigDecimal("10"), Category.FOOD, LocalDate.now()));
        repo.save(new Expense("b", new BigDecimal("20"), Category.FOOD, LocalDate.now()));
        repo.save(new Expense("c", new BigDecimal("30"), Category.RENT, LocalDate.now()));

        assertThat(repo.findByCategory(Category.FOOD)).hasSize(2);
        assertThat(repo.findByCategory(Category.RENT)).hasSize(1);
    }

    @Test
    void derivedQueryByAmountAndDate() {
        repo.save(new Expense("cheap", new BigDecimal("10"), Category.OTHER, LocalDate.now().minusDays(10)));
        repo.save(new Expense("pricey", new BigDecimal("5000"), Category.OTHER, LocalDate.now()));

        assertThat(repo.findByAmountGreaterThan(new BigDecimal("100")))
                .extracting(Expense::getDescription).containsExactly("pricey");
        assertThat(repo.findBySpentOnGreaterThanEqualOrderBySpentOnDesc(LocalDate.now().minusDays(3)))
                .extracting(Expense::getDescription).containsExactly("pricey");
    }
}
