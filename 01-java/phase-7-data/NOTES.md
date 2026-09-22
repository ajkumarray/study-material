<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · tooling](../phase-6-tooling/NOTES.md)
<!-- /nav -->

# Phase 7 — Data Access: Notes

*All three lessons live in `data-lab/`, a Maven project using in-memory H2 (a real SQL database packaged as a jar). Swapping to PostgreSQL in production is a connection-URL-and-driver change — the JDBC/JPA code itself doesn't change.*

## 7.1 — SQL Essentials and JDBC

**SQL** (Structured Query Language) is the standard language for defining and manipulating relational data. **JDBC** (Java Database Connectivity) is the standard Java API for talking to *any* relational database from Java code, independent of which specific database is on the other end.

### SQL in One Breath

- **DDL** (Data Definition Language — structure): `CREATE TABLE`, `ALTER TABLE`, `DROP TABLE`.
- **DML** (Data Manipulation Language — data): `INSERT`, `UPDATE`, `DELETE`.
- **Query**: `SELECT`.
- **DCL** (Data Control Language): `GRANT`, `REVOKE` (permissions).

### Key Concepts

- **Logical query evaluation order**: `SELECT cols FROM t WHERE pred GROUP BY g HAVING agg-pred ORDER BY o LIMIT n` is written in that order, but **evaluated** in the order `FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT`. This is precisely why a column alias defined in `SELECT` can't be referenced in that same query's `WHERE` clause — `WHERE` is logically evaluated before `SELECT` even runs, so the alias doesn't exist yet at that point.
- **JOIN types**: `INNER JOIN` returns only rows with a match on both sides; `LEFT JOIN` returns every row from the left table plus matched rows from the right (with `NULL` in right-side columns where there's no match — this project's account-to-ledger-entries query is exactly this shape); `RIGHT JOIN` mirrors that from the right side; `FULL OUTER JOIN` returns all rows from both sides. `COALESCE(x, 0)` is the standard way to turn a `NULL` (from an unmatched `LEFT JOIN` row) into a sensible default like zero.
- **Keys**: `PRIMARY KEY` enforces uniqueness and non-null on a column (or columns), and every table should have exactly one — prefer a surrogate, database-generated id (`AUTO_INCREMENT`/`IDENTITY`) over a "natural" business key, which can turn out to change or not be as unique as assumed. `FOREIGN KEY` enforces referential integrity — the database refuses to insert a row that would reference a non-existent parent row, and `ON DELETE CASCADE` propagates a parent deletion down to its dependent child rows automatically. `UNIQUE` and `NOT NULL` are additional column-level constraints; `CHECK` enforces an arbitrary boolean condition on a column's value.
- **Money is `DECIMAL(p, s)`, never `DOUBLE`.** This is the schema-level enforcement of Phase 1's floating-point lesson: `DOUBLE` is binary floating point and cannot represent most decimal fractions (like `0.10`) exactly, so financial arithmetic on `DOUBLE` values accumulates small but real errors. `DECIMAL(precision, scale)` is exact base-10 arithmetic — exactly what `java.math.BigDecimal` maps onto on the Java side.
- **Indexes** turn a full O(n) table scan into an O(log n) lookup for `WHERE`/`JOIN`/`ORDER BY` operations on the indexed column(s), typically implemented as a B-tree internally — at the cost of slightly slower writes (the index itself must be maintained on every `INSERT`/`UPDATE`/`DELETE`) and additional disk space. The primary key is automatically indexed; add explicit indexes to columns you frequently filter or join on.

### JDBC — the Standard Database API

You write code against `java.sql` **interfaces**; a concrete **driver** (H2's driver here, PostgreSQL's driver in production) implements them underneath. Swapping databases is, ideally, purely a matter of swapping the driver dependency and connection URL — the actual JDBC code calling `Connection`/`PreparedStatement`/`ResultSet` doesn't change.

```java
static final String URL = "jdbc:h2:mem:datalab;DB_CLOSE_DELAY=-1";

try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
    // ... work with the connection ...
}
```

The four core JDBC types: **`Connection`** (a session with the database), **`PreparedStatement`** (a precompiled, parameterized SQL statement), **`ResultSet`** (a cursor over the rows a query returned), and **`DriverManager`**/**`DataSource`** (the source that hands out `Connection`s — `DriverManager` for simple standalone code, `DataSource` for pooled/managed connections, 7.2). **Every one of these is `AutoCloseable`**, so try-with-resources (Phase 3) is used everywhere — a leaked, never-closed `Connection` doesn't just consume memory, it permanently occupies a connection slot the pool (and the database itself) needed to hand out to other work.

### `PreparedStatement` vs. SQL Injection — the #1 Security Lesson

```java
String evil = "Ajay'; DROP TABLE account; --";
Optional<BigDecimal> found = balanceOf(conn, evil);   // uses ? binding
System.out.println(found);   // Optional.empty  (harmless — ? treats it as DATA, not SQL)
System.out.println(accountExists(conn));   // true — the table is still intact
```

```java
static Optional<BigDecimal> balanceOf(Connection conn, String owner) throws SQLException {
    String sql = "SELECT balance FROM account WHERE owner = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, owner);           // parameters are 1-INDEXED — a JDBC quirk
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(rs.getBigDecimal("balance")) : Optional.empty();
        }
    }
}
```

If SQL were built by **string concatenation** — `"... WHERE owner = '" + evil + "'"` — the malicious input above would terminate the intended string literal early and inject an entirely separate, destructive `DROP TABLE` statement, which the database would then genuinely execute. `PreparedStatement`'s `?` placeholders eliminate this entire class of attack structurally: the SQL text and the bound parameter values are sent to the database on **separate channels**, so a bound value is *never* parsed as SQL syntax, no matter what characters it contains. The example above proves this directly — the injection string is looked up harmlessly as ordinary data (finding nothing, since no account owner matches that literal string), and the `account` table remains fully intact afterward. As a bonus, the database also caches the compiled execution plan for a prepared statement's SQL text across repeated calls, making it faster for repeated queries, not just safer. **Rule, no exceptions: always parameterize; never concatenate untrusted input directly into SQL text.**

### Insert, Select, and Retrieving Generated Keys

```java
static long insertAccount(Connection conn, String owner, BigDecimal balance) throws SQLException {
    String sql = "INSERT INTO account (owner, balance) VALUES (?, ?)";
    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        ps.setString(1, owner);
        ps.setBigDecimal(2, balance);
        ps.executeUpdate();
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }
}

long ajayId = insertAccount(conn, "Ajay", new BigDecimal("1000.00"));
long raviId = insertAccount(conn, "Ravi", new BigDecimal("50.00"));
System.out.println("inserted accounts, ids " + ajayId + " and " + raviId);
// inserted accounts, ids 1 and 2
```

`executeQuery()` runs a `SELECT` and returns a `ResultSet`. `executeUpdate()` runs an `INSERT`/`UPDATE`/`DELETE` and returns the **number of rows affected** as an `int` — not the data itself. Passing `Statement.RETURN_GENERATED_KEYS` to `prepareStatement`, followed by `getGeneratedKeys()` after executing, is how you retrieve a database-assigned auto-increment id right after inserting the row that got it.

```java
static List<Entry> entriesFor(Connection conn, long accountId) throws SQLException {
    String sql = "SELECT id, account_id, amount, note FROM ledger_entry WHERE account_id = ? ORDER BY id";
    List<Entry> out = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, accountId);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {          // the row-to-object mapping ORMs automate (7.3)
                out.add(new Entry(rs.getLong("id"), rs.getLong("account_id"),
                        rs.getBigDecimal("amount"), rs.getString("note")));
            }
        }
    }
    return out;
}
```

Mapping `ResultSet` rows into Java objects by hand — the `while (rs.next()) { ... }` loop constructing one record per row — is exactly the repetitive, error-prone tedium that Object-Relational Mapping frameworks (7.3) exist to automate away.

### Aggregates and JOINs — Let the Database Do the Work

```sql
SELECT a.owner, COUNT(e.id) AS entries, COALESCE(SUM(e.amount), 0) AS net
FROM account a
LEFT JOIN ledger_entry e ON e.account_id = a.id
GROUP BY a.owner
ORDER BY a.owner
```
```
  Ajay   entries=2 net=4800.00
  Ravi   entries=1 net=-10.00
```

Pushing aggregation (`SUM`, `COUNT`, `GROUP BY`) and filtering down into the database, rather than pulling every raw row across the network into Java and summing them there, is a real, significant performance principle — it moves far less data over the wire, and lets the database's own indexed query engine do the heavy lifting instead of the application. The `LEFT JOIN` here guarantees every account appears in the result even if it has zero ledger entries (an `INNER JOIN` would silently drop such accounts), and `COALESCE(SUM(e.amount), 0)` turns the `NULL` that `SUM` produces over zero matched rows into a sensible `0`.

### Update, and Why This Tour of JDBC Matters

```java
int changed = adjustBalance(conn, ajayId, new BigDecimal("4800.00"));
System.out.println("rows updated: " + changed);   // rows updated: 1
```

### Why It's Useful

Every ORM in existence (7.3) is, underneath, generating and executing exactly this kind of JDBC code — understanding it directly is what lets you actually read and diagnose the SQL an ORM generates when something is slow or wrong. SQL injection via string-concatenated queries remains one of the most common, most damaging real-world web application vulnerabilities, and understanding *why* `PreparedStatement` structurally prevents it (not just "it's the recommended API") is a baseline expectation in any backend interview.

### Summary / Key Takeaways

- SQL's logical evaluation order is `FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT` — this is why a `SELECT` alias can't be used in that query's own `WHERE`.
- Always store money as `DECIMAL`/`BigDecimal`, never `DOUBLE` — the same floating-point precision problem from Phase 1, enforced at the schema level.
- `PreparedStatement`'s `?` placeholders send SQL and data on separate channels, making SQL injection structurally impossible — never build SQL by string-concatenating user input.
- `executeQuery` returns a `ResultSet` (for `SELECT`); `executeUpdate` returns an `int` row count (for `INSERT`/`UPDATE`/`DELETE`); `RETURN_GENERATED_KEYS` retrieves database-assigned ids.
- Push aggregation and filtering into the database via SQL rather than pulling raw rows into Java to compute the same thing.

## 7.2 — Connection Pooling and Transactions

Two production essentials that raw JDBC gives you the building blocks for, but doesn't provide automatically: efficiently reusing expensive connections, and safely grouping multiple statements into one all-or-nothing unit of work.

### Connection Pooling

```java
HikariConfig cfg = new HikariConfig();
cfg.setJdbcUrl("jdbc:h2:mem:txlab;DB_CLOSE_DELAY=-1");
cfg.setUsername("sa");
cfg.setPassword("");
cfg.setMaximumPoolSize(5);          // THE key tuning knob
cfg.setPoolName("demo-pool");

try (HikariDataSource pool = new HikariDataSource(cfg)) {
    try (Connection c = pool.getConnection()) {
        System.out.println("got connection from pool: " + c.getClass().getSimpleName());
    }   // <- returns to the pool, does NOT actually disconnect
}
```

**Why pool at all**: opening a raw database connection is genuinely expensive — a TCP handshake, authentication, and session setup, often tens of milliseconds all told. A web application opening and closing a fresh connection on every single request would spend a large fraction of its time just connecting, and could easily overwhelm the database's own connection limit under real load. A **connection pool** (HikariCP is the de-facto standard, and Spring Boot's default) keeps a fixed number of connections genuinely open in the background and lends them out on demand; calling `connection.close()` on a pooled connection **returns it to the pool** rather than actually tearing down the underlying database session. `DataSource` is the modern, standard interface applications and frameworks use to obtain connections — it's the direct replacement for manually calling `DriverManager.getConnection(...)` everywhere, and it's what frameworks like Spring inject into your code.

**Sizing the pool** is the key tuning knob, and it's genuinely counterintuitive: bigger is not simply better. Too small, and request threads queue up waiting for a free connection under load; too large, and you can overwhelm the database server itself with more concurrent work than it can efficiently handle, actually *reducing* throughput. A commonly cited starting formula is roughly `connections ≈ (core_count × 2) + effective_spindle_count` — typically landing on a small double-digit number, not hundreds, even for a fairly busy service; the honest answer is to start from a reasonable estimate and then measure under realistic load.

### Transactions and ACID

**ACID** describes the guarantees a database transaction makes:

- **Atomic** — a transaction's statements happen entirely, or not at all; there's no partially-applied state visible to anyone else.
- **Consistent** — a transaction moves the database from one valid state to another, never violating its declared constraints.
- **Isolated** — concurrent transactions don't see each other's uncommitted, in-progress changes (with different **isolation levels** controlling exactly how strictly this is enforced — see below).
- **Durable** — once a transaction commits, its changes survive even a subsequent crash.

The classic motivating example: a money transfer is a **debit plus a credit**. Without wrapping both in one transaction, a crash between the two operations could debit one account and never credit the other — money that simply vanishes. The lesson demonstrates this directly with a transfer designed to fail partway through, *after* the debit has already happened:

```java
try {
    // fails on an insufficient-funds check performed AFTER the debit has already run
    transfer(pool, "Ravi", "Ajay", new BigDecimal("999999.00"));
} catch (IllegalStateException e) {
    System.out.println("transfer failed: " + e.getMessage());
}
printBalances(pool);
// balances UNCHANGED — the partial debit was rolled back
```

### The Transaction Pattern — Memorize This Shape

```java
static void transfer(DataSource ds, String from, String to, BigDecimal amount) throws SQLException {
    Connection c = ds.getConnection();
    try {
        c.setAutoCommit(false);                       // BEGIN — stop per-statement commits

        BigDecimal fromBalance = lockedBalance(c, from);
        debit(c, from, amount);                        // step 1 of 2

        if (fromBalance.compareTo(amount) < 0) {
            throw new IllegalStateException(from + " has insufficient funds");
        }

        credit(c, to, amount);                          // step 2 of 2
        c.commit();                                     // COMMIT: both, or neither
    } catch (SQLException | IllegalStateException e) {
        c.rollback();                                    // ROLLBACK: undo the debit too
        throw e;
    } finally {
        c.setAutoCommit(true);                            // reset before returning to the pool
        c.close();                                         // -> back to pool
    }
}
```

By default, JDBC connections run in **auto-commit mode** — every individual statement is its own implicit transaction, committed immediately. `setAutoCommit(false)` turns that off, letting multiple statements be grouped into one real transaction: do the work, `commit()` on success to make every change permanent atomically, or `rollback()` in a `catch` block on any failure to undo **everything** done since the transaction began — including the already-executed debit in the example above. `finally` restores auto-commit mode and closes the connection (returning it to the pool) regardless of the outcome.

```java
static BigDecimal lockedBalance(Connection c, String owner) throws SQLException {
    // SELECT ... FOR UPDATE: locks the row so a concurrent transfer
    // can't read a stale balance and double-spend (pessimistic locking).
    try (PreparedStatement ps = c.prepareStatement(
            "SELECT balance FROM account WHERE owner = ? FOR UPDATE")) {
        ps.setString(1, owner);
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal("balance");
        }
    }
}
```

`SELECT ... FOR UPDATE` locks the selected row for the duration of the current transaction — this is **pessimistic locking**: it prevents a second, concurrent transaction from reading the same row's stale balance and making a conflicting decision based on it (the classic "double-spend" scenario) by simply making that second transaction wait until the first one commits or rolls back.

### Isolation Levels

| level | allows | notes |
|---|---|---|
| `READ_UNCOMMITTED` | dirty reads | sees other transactions' *uncommitted* writes — rarely used in practice |
| `READ_COMMITTED` | non-repeatable reads, phantom reads | only ever sees committed data — PostgreSQL's default |
| `REPEATABLE_READ` | phantom reads | a row read twice in the same transaction reads identically both times — MySQL's default |
| `SERIALIZABLE` | (none of the above) | behaves as if every transaction ran one at a time, in some serial order — strongest, slowest |

Isolation levels form a ladder from weaker/more-concurrent to stronger/more-consistent, and the anomalies they progressively rule out also form a ladder: **dirty read → non-repeatable read → phantom read**. A **dirty read** sees another transaction's not-yet-committed change (which might later be rolled back). A **non-repeatable read** sees a row change value between two reads of it within the same transaction, because another transaction committed a change to it in between. A **phantom read** sees a *set* of rows matching some condition change between two identical queries within the same transaction, because another transaction inserted/deleted matching rows in between. Each stronger isolation level blocks one more of these. `READ_COMMITTED` is the right choice roughly 95% of the time in practice — it's PostgreSQL's default for exactly that reason.

### Optimistic Locking

**Optimistic locking** is the scalable alternative to `SELECT ... FOR UPDATE` for workloads where conflicts are actually rare: instead of locking a row up front, each row carries a `version` column; an update checks that the version hasn't changed since it was read, and fails (to be retried) if it has, rather than blocking other transactions from even reading the row in the meantime. JPA supports this natively via a `@Version` field on an entity (7.3).

### Why It's Useful

Connection pooling and transaction correctness are both directly production-critical: a service without pooling collapses under real load from connection-setup overhead alone, and a multi-step operation without a transaction wrapping it is a real financial/data-integrity bug waiting to happen under any partial failure — exactly the scenario the transfer example demonstrates concretely rather than abstractly.

### Summary / Key Takeaways

- Connection pooling reuses expensive-to-establish connections; `close()` on a pooled connection returns it to the pool rather than disconnecting; size the pool conservatively and measure.
- ACID (Atomic, Consistent, Isolated, Durable) describes what a transaction guarantees; the transfer example shows a debit-then-credit failing safely via rollback, leaving balances untouched.
- Memorize the transaction pattern: `setAutoCommit(false)` → work → `commit()` on success / `rollback()` on failure in `catch` → restore auto-commit and close in `finally`.
- Isolation levels trade concurrency for consistency along a ladder of anomalies (dirty read → non-repeatable read → phantom read); `READ_COMMITTED` is right most of the time.
- `SELECT ... FOR UPDATE` is pessimistic locking (block others up front); a `@Version` column is optimistic locking (detect conflicts at write time) — prefer optimistic under low contention.

## 7.3 — JPA / Hibernate

**JPA** (Jakarta Persistence API) is a *specification* — a set of interfaces and annotations for mapping Java objects to relational database rows. **Hibernate** is the dominant *implementation* of that specification. An **ORM** (Object-Relational Mapper) is the general category of tool this is: it maps objects to table rows so application code manipulates ordinary Java objects, and the framework generates the actual SQL underneath — eliminating the hand-written `ResultSet`-to-object mapping loop from 7.1 entirely.

### Mapping via Annotations

```java
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // maps to AUTO_INCREMENT
    private Long id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    protected Account() { }        // JPA requires this; protected discourages misuse

    public Account(String owner, BigDecimal balance) {
        this.owner = owner;
        this.balance = balance;
    }

    public Long getId()            { return id; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
```

- **`@Entity`** marks a class as mapped to a database table; **`@Table(name = ...)`** optionally overrides the default table name.
- **`@Id`** marks the primary key field; **`@GeneratedValue(strategy = GenerationType.IDENTITY)`** delegates id generation to the database's own auto-increment mechanism.
- **`@Column(nullable = ..., precision = ..., scale = ...)`** controls column-level generation details — here mirroring 7.1's "money is `DECIMAL(12,2)`, never a binary float" rule directly in the mapping.
- **Entities need a no-arg constructor and mutable fields**, since the JPA provider instantiates an entity and then populates its fields via reflection (and, for lazy-loaded relationships, dynamically creates proxy subclasses). This is one of the rare places in modern Java code where a `record` (immutable, no no-arg constructor) genuinely doesn't fit — records work well as DTOs and query projections *around* the entity layer, but not as entities themselves.

### Core Runtime Types

```java
EntityManagerFactory emf = Persistence.createEntityManagerFactory("datalab-pu");
```

**`EntityManagerFactory`** is heavyweight — expensive to create, so it's created exactly **once per application** (conceptually similar to a connection pool). **`EntityManager`** represents a **persistence context** — a short-lived unit of work, roughly one per transaction or request, that tracks every entity loaded or created through it. **`EntityTransaction`** wraps 7.2's begin/commit/rollback pattern specifically for JPA.

```java
interface Work { void run(EntityManager em); }

static void inTransaction(EntityManagerFactory emf, Work work) {
    EntityManager em = emf.createEntityManager();
    var tx = em.getTransaction();
    try {
        tx.begin();
        work.run(em);
        tx.commit();
    } catch (RuntimeException e) {
        if (tx.isActive()) tx.rollback();
        throw e;
    } finally {
        em.close();
    }
}
```

`persist(entity)` issues an `INSERT`; `find(EntityClass, id)` issues a `SELECT` by primary key; `remove(entity)` issues a `DELETE`. This project's helper wraps 7.2's begin/commit/rollback shape once so every example lesson doesn't repeat it.

### `persist` — Insert Without Writing INSERT

```java
inTransaction(emf, em -> {
    em.persist(new Account("Ajay", new BigDecimal("1000.00")));
    em.persist(new Account("Ravi", new BigDecimal("500.00")));
    em.persist(new Account("Meera", new BigDecimal("2500.00")));
});
```
```
Hibernate: insert into account (balance,owner) values (?,?)
Hibernate: insert into account (balance,owner) values (?,?)
Hibernate: insert into account (balance,owner) values (?,?)
```

No `id` was set on any of these `Account` objects — Hibernate generates the actual `INSERT` SQL, executes it, and fills each entity's `id` field with the database-assigned value afterward.

### Dirty Checking — the ORM's Party Trick

```java
inTransaction(emf, em -> {
    Account a = em.find(Account.class, 1L);
    a.setBalance(a.getBalance().add(new BigDecimal("250.00")));
    // NO em.update() call exists or is needed!
});
```
```
Hibernate: select ... from account a1_0 where a1_0.id=?
Hibernate: update account set balance=? where id=?
```

A **managed** entity — one currently loaded through an active `EntityManager`, inside an active transaction — is actively watched by the persistence context. Mutating one of its fields with a plain setter, as shown, does nothing that talks to the database directly — but at commit time, Hibernate **diffs the entity's current state against the snapshot it took when the entity was loaded**, and automatically generates an `UPDATE` statement for whatever actually changed. There is no `update()` or `save()` method call anywhere in the code above; the `UPDATE` shown in the SQL log is entirely automatic. (The flip side worth knowing: mutating a managed entity's field, even accidentally, silently produces a real database write at commit — a genuine gotcha if you don't realize an object you're holding is currently "managed.")

### Entity Lifecycle States

```
transient (new, not tracked) --persist()--> managed (tracked, dirty-checked)
managed --remove()--> removed (DELETE issued at flush/commit)
managed --context closes--> detached (no longer tracked)
detached --merge()--> managed again
```

**Transient** — a plain `new`-ed object with no connection to any persistence context or database row. **Managed** (or "persistent") — attached to an active `EntityManager`, actively dirty-checked, and any change is automatically written at commit. **Detached** — was managed, but its `EntityManager` has since closed; the object still holds its data, but changes to it are no longer tracked or persisted. **Removed** — marked for deletion; the actual `DELETE` fires at flush/commit time.

### JPQL — Queries Over Entities, Not Tables

```java
List<Account> rich = em.createQuery(
        "SELECT a FROM Account a WHERE a.balance > :min ORDER BY a.balance DESC",
        Account.class)
        .setParameter("min", new BigDecimal("600.00"))
        .getResultList();

BigDecimal total = em.createQuery("SELECT SUM(a.balance) FROM Account a", BigDecimal.class)
        .getSingleResult();
```

**JPQL** (Jakarta Persistence Query Language) is an **object-oriented** query language: `Account` above refers to the entity **class**, and `a.balance` refers to a **field**, not a table/column name directly — Hibernate translates JPQL into the actual SQL the underlying database understands. It's still fully **parameterized** (`:min`, bound via `setParameter`), so it's just as injection-safe as a JDBC `PreparedStatement`. Beyond JPQL, JPA also offers the type-safe, programmatically-built Criteria API, and an escape hatch to fully native SQL when a query genuinely doesn't fit the object-oriented model well.

### The Wins and the Traps

**Wins**: fully object-oriented application code (no `ResultSet` loops), database portability (this same code runs unmodified against H2 in tests and PostgreSQL in production), automatic dirty checking, first/second-level caching, lazy loading of relationships, and declarative relationship mapping (`@OneToMany`/`@ManyToOne`).

**Traps** — genuinely important, and genuine interview/production gold:

- **N+1 SELECT**: loading a list of N parent entities triggers 1 query, then lazily loading a related collection/entity *per element* in a loop triggers N additional queries — N+1 round trips total, often completely invisible until you actually look at the SQL log and see it explode. Fix with `JOIN FETCH` in JPQL, `@EntityGraph`, or explicit batch-fetch sizing.
- **`LazyInitializationException`**: attempting to access a lazily-loaded relationship on an entity *after* its owning `EntityManager` has already closed — there's no active persistence context left to actually go fetch it. Fix by accessing the relationship while still inside the transaction, or by fetching it eagerly (`JOIN FETCH`/an entity graph) up front where it's actually needed.
- **Leaky abstraction**: an ORM hides SQL from your day-to-day code, but it does **not** excuse not understanding SQL — diagnosing an N+1 problem, a slow query, or lock contention still requires reading the generated SQL and understanding indexes, joins, and execution plans directly. This is exactly why 7.1's raw SQL/JDBC foundation comes before 7.3's ORM layer in this curriculum.

### Schema Management: `hibernate.hbm2ddl.auto`

`create-drop` builds the database schema automatically from your entity classes at application startup and drops it again at shutdown — convenient for demos and tests (used throughout this lesson), but genuinely dangerous against a real database, since it will happily drop and recreate tables containing real data. Production configurations use `validate` (fail startup if the entities don't match the existing schema) or `none`, with the actual schema owned and evolved by a dedicated migration tool like **Flyway** or **Liquibase**, applying versioned, reviewable SQL migration scripts instead of letting the ORM auto-generate schema changes. **Never use `create-drop` or `update` against a real production database.**

### The Bridge to Spring Boot

In raw JPA, you write `persistence.xml`, manage the `EntityManagerFactory` lifecycle yourself, and hand-write a transaction-wrapping helper like `inTransaction` above. Spring Boot generates all of that configuration from `application.properties` and injects a ready-to-use `EntityManager` directly into your code. **Spring Data JPA** goes further still: declaring `interface AccountRepository extends JpaRepository<Account, Long>` gets you a full set of CRUD methods for free, and a method like `findByBalanceGreaterThan(BigDecimal min)` is **implemented automatically from its name alone**, with no method body at all — Spring Data parses the method name and generates the equivalent JPQL query. This phase's hand-written JDBC and raw-JPA code is precisely the "under the hood" mechanism that makes that later magic legible rather than mysterious.

### Why It's Useful

The N+1 problem and `LazyInitializationException` are two of the most common real production performance and correctness bugs in any ORM-based Java backend — recognizing them, and knowing the standard fixes (`JOIN FETCH`, entity graphs, fetching within the transaction boundary), is a direct, practical, everyday skill in any Spring Boot/Hibernate codebase, not just interview trivia.

### Summary / Key Takeaways

- JPA is the specification (interfaces/annotations); Hibernate is the dominant implementation — "JPA is the interface, Hibernate is the class."
- Entities need a no-arg constructor and mutable fields (populated via reflection) — records don't fit as entities, though they work well as DTOs/projections around them.
- Dirty checking auto-generates `UPDATE`s for changed fields on a managed entity at commit — there's no explicit `update()` call to make.
- Entity lifecycle: transient → managed (`persist`) → detached (context closes) / removed (`remove`); `merge` brings a detached entity back to managed.
- Know the two classic ORM traps cold: N+1 SELECT (fix with `JOIN FETCH`/entity graphs/batching) and `LazyInitializationException` (fix by fetching within the transaction) — and never use `create-drop`/`update` schema generation against real data.
