package dev.ajay.tracker.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import dev.ajay.tracker.domain.Expense;

/**
 * Persistence contract for {@link Expense}. Programming to this interface
 * (not the JDBC class) is the Dependency Inversion Principle: the service
 * layer depends on this abstraction, so the storage tech is swappable and
 * the service is testable against a fake. This is the shape Spring Data
 * JPA generates for you from a one-line interface (7.3).
 */
public interface ExpenseRepository {

    /** Inserts a new expense, returning a copy with its assigned id. */
    Expense save(Expense expense);

    /** @return the expense with this id, if it exists (4.3 Optional). */
    Optional<Expense> findById(long id);

    /** @return all expenses, newest first. */
    List<Expense> findAll();

    /** @return expenses on or after {@code from}, newest first. */
    List<Expense> findSince(LocalDate from);

    /** Deletes by id. @return true if a row was removed. */
    boolean deleteById(long id);

    /** @return number of stored expenses. */
    long count();
}
