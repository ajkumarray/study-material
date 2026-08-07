# Capstone — Expense Tracker

The Java track's finale: one real, layered, tested, persistent console
application that exercises every phase. This is also the app we **rebuild as
a Spring Boot REST API** at the start of track 02 — watch how much of the
hand-written plumbing here collapses into annotations there.

## Run it

```bash
cd expense-tracker

mvn test                                                   # 18 tests (unit + integration)

mvn -q compile exec:java -Dexec.mainClass=dev.ajay.tracker.App -Dexec.args=demo   # scripted demo
mvn -q compile exec:java -Dexec.mainClass=dev.ajay.tracker.App                    # interactive REPL
```

REPL commands: `add <amount> <category> <description>`, `list`, `report`,
`recent [days]`, `delete <id>`, `help`, `quit`.

## Architecture — layered, dependency-inverted

```
      App (console I/O)                 <- knows the user, not the DB
        |  depends on
      ExpenseService (business logic)   <- streams/Optional; no SQL, no I/O
        |  depends on
      ExpenseRepository (interface)     <- the seam
        |  implemented by
   JdbcExpenseRepository  |  InMemoryExpenseRepository (tests)
        |
   HikariCP pool -> H2
```

Each layer depends only on the layer below, and the service depends on the
repository **interface** — so the JDBC store swaps for an in-memory fake in
tests with zero changes to the code under test. That's the Dependency
Inversion Principle, and it's the exact structure Spring will wire for us
with `@Service` / `@Repository` / constructor injection.

## Where each phase shows up

| Phase | In the capstone |
|-------|-----------------|
| 1 — basics | control flow in the REPL dispatch; `BigDecimal` money (never `double`) |
| 2 — OOP | `Expense`/`Report` records, `Category` enum, layered classes, encapsulation |
| 3 — core APIs | `List`/`Map` everywhere, generics, custom `DataAccessException`, try-with-resources |
| 4 — functional | `report()` built with streams `groupingBy`/`reducing`/`sorted`; `Optional` returns; `ParamBinder` functional seam |
| 5 — concurrency | `InMemoryExpenseRepository` uses `ConcurrentHashMap` + `AtomicLong` (thread-safe fake) |
| 6 — tooling | Maven project, JUnit 5 + AssertJ (18 tests), SLF4J logging, Javadoc |
| 7 — data | JDBC repository, PreparedStatements, HikariCP pool, generated keys, indexed schema |

## Testing strategy (Phase 6.2 in practice)

- **Unit tests** (`ExpenseServiceTest`) run the logic against the in-memory
  fake — fast, no database, test the *behavior* (report math, validation,
  date filtering, ordering).
- **Integration tests** (`JdbcExpenseRepositoryTest`) run the real JDBC code
  against a throwaway H2 database (unique name per test = full isolation) —
  proving the SQL, the row mapping, and money precision actually work.

This unit-vs-integration split is the testing pyramid: many fast unit tests,
fewer slower integration tests.

## Deliberate extension exercises

1. **Budgets:** add a `Budget` per category; `report()` flags categories over
   budget (log a `warn`). Test the exactly-at-budget boundary.
2. **Monthly report:** `reportForMonth(YearMonth)` using a new repository
   query — decide whether to filter in SQL (better) or in Java, and justify it.
3. **CSV export/import:** `export(Path)` and `importFrom(Path)` using NIO.2
   (Phase 3.4) inside a single transaction (all rows or none).
4. **Update:** add `update(id, ...)` to the repository + service + REPL.
5. **Persistence swap:** point the JDBC URL at a file (`jdbc:h2:./data/tracker`)
   and confirm data survives restarts; then try a Postgres URL + driver and
   change nothing else — proving the driver abstraction.
```
