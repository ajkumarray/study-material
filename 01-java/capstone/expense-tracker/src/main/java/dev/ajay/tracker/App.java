package dev.ajay.tracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ajay.tracker.domain.Category;
import dev.ajay.tracker.domain.Expense;
import dev.ajay.tracker.repository.Database;
import dev.ajay.tracker.repository.ExpenseRepository;
import dev.ajay.tracker.repository.JdbcExpenseRepository;
import dev.ajay.tracker.service.ExpenseService;

/**
 * Console entry point — wires the layers together (poor man's dependency
 * injection; Spring Boot will do this for us in track 02) and runs either
 * a scripted demo or an interactive loop.
 *
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=dev.ajay.tracker.App -Dexec.args=demo
 *   mvn -q exec:java -Dexec.mainClass=dev.ajay.tracker.App            (interactive)
 * </pre>
 */
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private final ExpenseService service;

    public App(ExpenseService service) {
        this.service = service;
    }

    public static void main(String[] args) {
        // File-backed H2 so data survives restarts (a real persistent store).
        DataSource ds = Database.pooled("jdbc:h2:mem:tracker;DB_CLOSE_DELAY=-1");
        ExpenseRepository repo = new JdbcExpenseRepository(ds);
        App app = new App(new ExpenseService(repo));

        if (args.length > 0 && args[0].equals("demo")) {
            app.runDemo();
        } else {
            app.runInteractive();
        }
    }

    /** Scripted end-to-end walkthrough — also what the "smoke test" exercises. */
    void runDemo() {
        log.info("=== expense tracker demo ===");
        service.add("groceries", new BigDecimal("2400.50"), Category.FOOD, LocalDate.now());
        service.add("metro card", new BigDecimal("500"), Category.TRANSPORT, LocalDate.now().minusDays(1));
        service.add("movie", new BigDecimal("350"), Category.ENTERTAINMENT, LocalDate.now().minusDays(2));
        service.add("lunch", new BigDecimal("480"), Category.FOOD, LocalDate.now().minusDays(2));
        service.add("rent", new BigDecimal("22000"), Category.RENT, LocalDate.now().minusDays(5));

        System.out.println("\n--- all expenses ---");
        service.all().forEach(App::printExpense);

        System.out.println("\n--- report ---");
        System.out.println(service.report().render());

        System.out.println("\n--- recent (last 3 days) ---");
        service.recent(3).forEach(App::printExpense);

        System.out.println("\n--- validation demo ---");
        try {
            service.add("bad", new BigDecimal("-5"), Category.OTHER, LocalDate.now());
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }
        log.info("demo complete; {} expenses stored", service.all().size());
    }

    /** Simple REPL. Commands: add, list, report, recent, delete, help, quit. */
    void runInteractive() {
        System.out.println("Expense Tracker — type 'help' for commands.");
        try (Scanner in = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!in.hasNextLine()) break;
                String line = in.nextLine().strip();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+", 2);
                String cmd = parts[0].toLowerCase();
                String rest = parts.length > 1 ? parts[1] : "";

                try {
                    // Pattern-matching-friendly command dispatch (Phase 1.3/4.4).
                    switch (cmd) {
                        case "add" -> handleAdd(rest);
                        case "list" -> service.all().forEach(App::printExpense);
                        case "report" -> System.out.println(service.report().render());
                        case "recent" -> service.recent(rest.isBlank() ? 7 : Integer.parseInt(rest))
                                .forEach(App::printExpense);
                        case "delete" -> System.out.println(
                                service.delete(Long.parseLong(rest)) ? "deleted" : "not found");
                        case "help" -> printHelp();
                        case "quit", "exit" -> { System.out.println("bye"); return; }
                        default -> System.out.println("unknown command: " + cmd + " (try 'help')");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("expected a number: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.out.println("invalid: " + e.getMessage());
                }
            }
        }
    }

    // Parses:  add <amount> <category> <description...>
    private void handleAdd(String rest) {
        String[] f = rest.split("\\s+", 3);
        if (f.length < 3) {
            System.out.println("usage: add <amount> <category> <description>");
            return;
        }
        Expense saved = service.add(f[2], new BigDecimal(f[0]),
                Category.from(f[1]), LocalDate.now());
        System.out.println("added #" + saved.id());
    }

    private static void printExpense(Expense e) {
        System.out.printf("  #%-3d %-12s %10s  %-13s %s%n",
                e.id(), e.spentOn(), e.amount(), e.category(), e.description());
    }

    private void printHelp() {
        System.out.println("""
                commands:
                  add <amount> <category> <description>   record an expense (dated today)
                  list                                    show all expenses
                  report                                  spending analytics
                  recent [days]                           expenses in last N days (default 7)
                  delete <id>                             remove an expense
                  help / quit""");
    }
}
