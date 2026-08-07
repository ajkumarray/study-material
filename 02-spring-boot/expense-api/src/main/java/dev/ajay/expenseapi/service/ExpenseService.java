package dev.ajay.expenseapi.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ajay.expenseapi.domain.Category;
import dev.ajay.expenseapi.domain.Expense;
import dev.ajay.expenseapi.repository.ExpenseRepository;

/**
 * Business-logic layer (Phase 3 DI + Phase 4 transactions).
 *
 * <p>{@code @Service} marks it a Spring bean; the container creates it once and
 * injects it where needed. {@code ExpenseRepository} arrives via CONSTRUCTOR
 * INJECTION — no {@code new}, no wiring code (the Java capstone's {@code App}
 * did that by hand). Constructor injection is preferred: dependencies are
 * final, the object is always valid, and it's trivially testable (pass a mock).
 */
@Service
@Transactional                       // methods run in a transaction; rolls back on unchecked ex
public class ExpenseService {

    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {   // injected by Spring
        this.repository = repository;
    }

    public Expense add(String description, BigDecimal amount, Category category, LocalDate spentOn) {
        return repository.save(new Expense(description, amount, category, spentOn));
    }

    @Transactional(readOnly = true)   // read-only txns are an optimization hint
    public List<Expense> all() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Expense byId(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ExpenseNotFoundException(id));   // -> 404
    }

    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new ExpenseNotFoundException(id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Expense> recent(int days) {
        return repository.findBySpentOnGreaterThanEqualOrderBySpentOnDesc(
                LocalDate.now().minusDays(days));
    }

    /** Per-category totals — streams over the entities (Java Phase 4 reused). */
    @Transactional(readOnly = true)
    public Map<Category, BigDecimal> totalsByCategory() {
        return repository.findAll().stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));
    }
}
