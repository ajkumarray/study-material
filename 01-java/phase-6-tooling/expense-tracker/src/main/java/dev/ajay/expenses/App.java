package dev.ajay.expenses;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tiny demo entry point — run with {@code mvn compile exec:java} or your IDE. */
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        log.info("expense tracker starting");

        ExpenseTracker tracker = new ExpenseTracker();
        tracker.add(new Expense("groceries", 2_400, Category.FOOD, LocalDate.now()));
        tracker.add(new Expense("metro card", 500, Category.TRANSPORT, LocalDate.now().minusDays(1)));
        tracker.add(new Expense("rent", 22_000, Category.RENT, LocalDate.now().minusDays(3)));

        log.info("total spent: {}", tracker.total());
        log.info("by category: {}", tracker.byCategory());
        tracker.largest().ifPresent(e -> log.info("largest: {} ({})", e.description(), e.amount()));
    }
}
