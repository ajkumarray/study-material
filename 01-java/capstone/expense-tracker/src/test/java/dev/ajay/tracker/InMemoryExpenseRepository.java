package dev.ajay.tracker;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import dev.ajay.tracker.domain.Expense;
import dev.ajay.tracker.repository.ExpenseRepository;

/**
 * A test double (fake) — a real, working repository backed by a map instead
 * of a database. Lets ExpenseService be tested without JDBC: fast, isolated,
 * no schema. This is why the service depends on the INTERFACE (DIP, 7.x).
 */
public class InMemoryExpenseRepository implements ExpenseRepository {

    private final ConcurrentHashMap<Long, Expense> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public Expense save(Expense expense) {
        long id = ids.incrementAndGet();
        Expense withId = expense.withId(id);
        store.put(id, withId);
        return withId;
    }

    @Override
    public Optional<Expense> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Expense> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(Expense::spentOn).reversed()
                        .thenComparing(Expense::id, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public List<Expense> findSince(LocalDate from) {
        return store.values().stream()
                .filter(e -> !e.spentOn().isBefore(from))
                .sorted(Comparator.comparing(Expense::spentOn).reversed())
                .toList();
    }

    @Override
    public boolean deleteById(long id) {
        return store.remove(id) != null;
    }

    @Override
    public long count() {
        return store.size();
    }
}
