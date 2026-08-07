package dev.ajay.tracker.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ajay.tracker.domain.Category;
import dev.ajay.tracker.domain.Expense;
import dev.ajay.tracker.repository.ExpenseRepository;

/**
 * Business logic layer. Owns validation-beyond-the-record, orchestrates the
 * repository, and builds analytics with streams (Phase 4.2). Knows nothing
 * about SQL or the console — pure domain logic, which is why it's the easiest
 * layer to unit-test (see ExpenseServiceTest).
 */
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    /** Adds an expense (constructed here so invalid input fails before the DB). */
    public Expense add(String description, BigDecimal amount, Category category, LocalDate spentOn) {
        Expense saved = repository.save(Expense.of(description, amount, category, spentOn));
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            log.warn("large expense recorded: {} ({})", saved.description(), saved.amount());
        }
        log.info("added expense id={} amount={}", saved.id(), saved.amount());
        return saved;
    }

    public List<Expense> all() {
        return repository.findAll();
    }

    public Optional<Expense> find(long id) {
        return repository.findById(id);
    }

    public boolean delete(long id) {
        boolean removed = repository.deleteById(id);
        log.info("delete id={} -> {}", id, removed);
        return removed;
    }

    /** Expenses in the last {@code days} days, newest first. */
    public List<Expense> recent(int days) {
        return repository.findSince(LocalDate.now().minusDays(days));
    }

    /**
     * Builds a full spending {@link Report} from all expenses using streams.
     * A single pass' worth of declarative aggregation replaces a page of loops.
     */
    public Report report() {
        List<Expense> expenses = repository.findAll();

        BigDecimal total = expenses.stream()
                .map(Expense::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);   // exact money math

        // groupingBy + reducing -> per-category totals, then sorted desc into
        // an insertion-ordered LinkedHashMap so render() shows biggest first.
        Map<Category, BigDecimal> byCategory = expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::category,
                        Collectors.reducing(BigDecimal.ZERO, Expense::amount, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        Expense largest = expenses.stream()
                .max(java.util.Comparator.comparing(Expense::amount))
                .orElse(null);

        BigDecimal average = expenses.isEmpty()
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP);

        return new Report(total, expenses.size(), byCategory, largest, average);
    }
}
