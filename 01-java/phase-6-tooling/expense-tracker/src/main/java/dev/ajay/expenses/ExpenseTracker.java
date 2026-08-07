package dev.ajay.expenses;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks expenses and answers questions about them.
 *
 * <p>Deliberately exercises the whole curriculum so far: collections (3.2),
 * streams (4.2), Optional (4.3) — now with logging (6.3) and under test (6.2).
 */
public class ExpenseTracker {

    /*
     * Lesson 6.3 — LOGGING with SLF4J.
     *
     * Why not System.out.println?
     *   - LEVELS  : debug/info/warn/error — filter noise per environment
     *               (debug in dev, info in prod) WITHOUT code changes
     *   - CONTEXT : timestamps, thread, logger name added automatically
     *   - ROUTING : console, files, aggregators — config, not code
     *   - FACADE  : code depends on slf4j-api only; the backend
     *               (logback, log4j2, java.util.logging) is swappable
     *
     * Convention: one private static final logger per class, named by class.
     */
    private static final Logger log = LoggerFactory.getLogger(ExpenseTracker.class);

    private final List<Expense> expenses = new ArrayList<>();

    /**
     * Records a new expense.
     *
     * @param expense the entry to add; validation lives in {@link Expense}
     */
    public void add(Expense expense) {
        // {} placeholders — NOT string concat. The message is only built
        // if the level is enabled: free when debug is off.
        log.debug("adding expense: {}", expense);
        expenses.add(expense);
        if (expense.amount() > 10_000) {
            log.warn("large expense recorded: {} ({})", expense.description(), expense.amount());
        }
    }

    /** @return total spent across all recorded expenses */
    public double total() {
        return expenses.stream().mapToDouble(Expense::amount).sum();
    }

    /** @return total spent in the given category */
    public double totalFor(Category category) {
        return expenses.stream()
                .filter(e -> e.category() == category)
                .mapToDouble(Expense::amount)
                .sum();
    }

    /** @return per-category totals, ready for a report */
    public Map<Category, Double> byCategory() {
        return expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::category,
                        Collectors.summingDouble(Expense::amount)));
    }

    /** @return the single largest expense, if any exist */
    public Optional<Expense> largest() {
        return expenses.stream()
                .max(java.util.Comparator.comparingDouble(Expense::amount));
    }

    /** @return expenses recorded on or after {@code from}, newest first */
    public List<Expense> since(LocalDate from) {
        return expenses.stream()
                .filter(e -> !e.date().isBefore(from))
                .sorted(java.util.Comparator.comparing(Expense::date).reversed())
                .toList();
    }

    /** @return number of recorded expenses */
    public int count() {
        return expenses.size();
    }
}
