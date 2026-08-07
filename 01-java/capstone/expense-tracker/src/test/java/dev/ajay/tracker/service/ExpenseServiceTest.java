package dev.ajay.tracker.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.ajay.tracker.InMemoryExpenseRepository;
import dev.ajay.tracker.domain.Category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the logic layer, using the in-memory fake repository —
 * fast, no database. (Phase 6.2)
 */
@DisplayName("ExpenseService")
class ExpenseServiceTest {

    private ExpenseService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(new InMemoryExpenseRepository());
    }

    private void seed() {
        service.add("chai", new BigDecimal("20"), Category.FOOD, LocalDate.now().minusDays(1));
        service.add("lunch", new BigDecimal("180"), Category.FOOD, LocalDate.now().minusDays(1));
        service.add("bus", new BigDecimal("45"), Category.TRANSPORT, LocalDate.now());
        service.add("rent", new BigDecimal("22000"), Category.RENT, LocalDate.now().minusDays(3));
    }

    @Test
    @DisplayName("add assigns an id and persists")
    void addAssignsId() {
        var saved = service.add("book", new BigDecimal("599"), Category.OTHER, LocalDate.now());
        assertThat(saved.id()).isNotNull().isPositive();
        assertThat(service.all()).hasSize(1);
    }

    @Test
    @DisplayName("report aggregates totals, categories, largest, average")
    void reportAggregates() {
        seed();
        Report r = service.report();

        assertThat(r.total()).isEqualByComparingTo("22245");
        assertThat(r.count()).isEqualTo(4);
        assertThat(r.largest().description()).isEqualTo("rent");
        assertThat(r.averageAmount()).isEqualByComparingTo("5561.25");
        assertThat(r.byCategory())
                .containsEntry(Category.FOOD, new BigDecimal("200"))
                .containsEntry(Category.TRANSPORT, new BigDecimal("45"));
    }

    @Test
    @DisplayName("byCategory is ordered biggest-spend first")
    void categoriesOrderedDescending() {
        seed();
        assertThat(service.report().byCategory().keySet())
                .containsExactly(Category.RENT, Category.FOOD, Category.TRANSPORT);
    }

    @Test
    @DisplayName("report on an empty tracker is all-zero, not a crash")
    void emptyReport() {
        Report r = service.report();
        assertThat(r.count()).isZero();
        assertThat(r.total()).isEqualByComparingTo("0");
        assertThat(r.largest()).isNull();
        assertThat(r.byCategory()).isEmpty();
    }

    @Test
    @DisplayName("recent() filters by date window")
    void recentFilters() {
        seed();
        assertThat(service.recent(0)).extracting(e -> e.description()).containsExactly("bus");
        assertThat(service.recent(2)).hasSize(3);          // excludes the 3-day-old rent
    }

    @Test
    @DisplayName("delete removes and reports success")
    void deleteWorks() {
        var saved = service.add("temp", new BigDecimal("10"), Category.OTHER, LocalDate.now());
        assertThat(service.delete(saved.id())).isTrue();
        assertThat(service.delete(saved.id())).isFalse();   // already gone
        assertThat(service.all()).isEmpty();
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "-9999.99"})
        void rejectsNonPositiveAmount(String bad) {
            assertThatThrownBy(() ->
                    service.add("x", new BigDecimal(bad), Category.OTHER, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        void rejectsBlankDescription() {
            assertThatThrownBy(() ->
                    service.add("   ", new BigDecimal("10"), Category.OTHER, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void rejectsFutureDate() {
            assertThatThrownBy(() ->
                    service.add("x", new BigDecimal("10"), Category.OTHER, LocalDate.now().plusDays(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("past or present");
        }
    }
}
