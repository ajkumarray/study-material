package dev.ajay.expenses;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Lesson 6.2 — Unit testing with JUnit 5 + AssertJ
 *
 * Naming: test class = ClassUnderTest + "Test", same package as the
 * code (so it could touch package-private members), under src/test.
 *
 * Every test follows ARRANGE — ACT — ASSERT:
 *   arrange: set up the world     act: poke it     assert: check the result
 *
 * A GOOD unit test is FIRST: Fast, Independent (any order, no shared
 * state), Repeatable (same result everywhere), Self-validating
 * (passes/fails without a human reading output), Timely.
 */
@DisplayName("ExpenseTracker")
class ExpenseTrackerTest {

    // Fresh fixture per test: JUnit creates a NEW test-class instance
    // for each @Test, and @BeforeEach reruns — tests can't contaminate
    // each other. (@AfterEach / @BeforeAll / @AfterAll complete the set.)
    private ExpenseTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ExpenseTracker();
    }

    // Helper keeps tests readable — noise factored out, intent left in.
    private static Expense expense(String desc, double amount, Category cat) {
        return new Expense(desc, amount, cat, LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("starts empty")
    void startsEmpty() {
        assertThat(tracker.count()).isZero();
        assertThat(tracker.total()).isEqualTo(0.0);
        assertThat(tracker.largest()).isEmpty();          // AssertJ knows Optional!
    }

    @Test
    @DisplayName("total sums all expenses")
    void totalSumsAllExpenses() {
        // arrange
        tracker.add(expense("chai", 20, Category.FOOD));
        tracker.add(expense("bus", 45, Category.TRANSPORT));

        // act + assert — AssertJ reads like English, and its failure
        // messages show expected vs actual in full:
        assertThat(tracker.total()).isEqualTo(65.0);
        assertThat(tracker.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("groups totals by category")
    void groupsByCategory() {
        tracker.add(expense("chai", 20, Category.FOOD));
        tracker.add(expense("lunch", 180, Category.FOOD));
        tracker.add(expense("bus", 45, Category.TRANSPORT));

        Map<Category, Double> report = tracker.byCategory();

        assertThat(report)
                .hasSize(2)
                .containsEntry(Category.FOOD, 200.0)
                .containsEntry(Category.TRANSPORT, 45.0)
                .doesNotContainKey(Category.RENT);        // rich Map assertions
    }

    @Test
    @DisplayName("finds the largest expense")
    void findsLargest() {
        tracker.add(expense("chai", 20, Category.FOOD));
        tracker.add(expense("rent", 22_000, Category.RENT));

        assertThat(tracker.largest())
                .isPresent()
                .hasValueSatisfying(e ->
                        assertThat(e.description()).isEqualTo("rent"));
    }

    @Test
    @DisplayName("since() filters and sorts newest first")
    void sinceFiltersAndSorts() {
        var today = LocalDate.of(2026, 7, 15);
        tracker.add(new Expense("old", 10, Category.OTHER, today.minusDays(30)));
        tracker.add(new Expense("mid", 20, Category.OTHER, today.minusDays(5)));
        tracker.add(new Expense("new", 30, Category.OTHER, today));

        var recent = tracker.since(today.minusDays(7));

        assertThat(recent)
                .hasSize(2)
                .extracting(Expense::description)          // project, then assert
                .containsExactly("new", "mid");            // exact ORDER checked
    }

    // ============================================================
    // Testing the UNHAPPY path — as important as the happy one
    // ============================================================
    @Nested                                        // @Nested groups related
    @DisplayName("validation")                     // tests into inner classes —
    class Validation {                             // shows up as a tree in reports

        @Test
        @DisplayName("rejects a blank description")
        void rejectsBlankDescription() {
            assertThatThrownBy(() -> expense("   ", 10, Category.FOOD))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        // ONE test, FIVE cases — parameterized tests kill copy-paste:
        @ParameterizedTest(name = "rejects non-positive amount: {0}")
        @ValueSource(doubles = {0, -1, -0.01, -99_999, -0.0})
        void rejectsNonPositiveAmounts(double bad) {
            assertThatThrownBy(() -> expense("thing", bad, Category.OTHER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @ParameterizedTest(name = "{0} + {1} -> total {2}")
        @CsvSource({
                "100, 250, 350",
                "0.5, 0.25, 0.75",
                "9999, 1, 10000",
        })
        void totalsAddUp(double first, double second, double expected) {
            tracker.add(expense("a", first, Category.OTHER));
            tracker.add(expense("b", second, Category.OTHER));

            // Floating point (lesson 1.2!): compare with a tolerance.
            assertThat(tracker.total()).isCloseTo(expected,
                    org.assertj.core.data.Offset.offset(1e-9));
        }
    }
}
