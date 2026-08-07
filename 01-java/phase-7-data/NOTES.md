<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · tooling](../phase-6-tooling/NOTES.md)
<!-- /nav -->

# Phase 7 — Data Access: Notes

*All three lessons live in `data-lab/`, a Maven project using in-memory H2 (a real SQL DB in a jar). Swapping to PostgreSQL is a URL + driver change — nothing else.*

## 7.1 — SQL essentials + JDBC

**SQL in one breath:**
- **DDL** (structure): `CREATE/ALTER/DROP TABLE`. **DML** (data): `INSERT/UPDATE/DELETE`. **Query:** `SELECT`. **DCL:** `GRANT/REVOKE`.
- `SELECT cols FROM t WHERE pred GROUP BY g HAVING agg-pred ORDER BY o LIMIT n` — logical evaluation order is FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT (why you can't use a SELECT alias in WHERE).
- **JOINs:** INNER (matching rows only), LEFT (all left + matched right, NULLs otherwise — our account→entries), RIGHT, FULL. `COALESCE(x, 0)` defaults NULLs.
- **Keys:** PRIMARY KEY (unique + not null, one per table — prefer a surrogate `AUTO_INCREMENT`/IDENTITY id), FOREIGN KEY (referential integrity — the DB refuses orphan rows; `ON DELETE CASCADE` propagates), UNIQUE, NOT NULL, CHECK.
- **Money = `DECIMAL(p,s)`, never `DOUBLE`** — exact decimal (lesson 1.2's floating-point lesson, enforced at the schema).
- **Indexes** make WHERE/JOIN/ORDER fast (B-tree, O(log n) lookups) at the cost of slower writes + space; the PK is auto-indexed. Add them for columns you filter/join on.

**JDBC — the standard DB API:** you code against `java.sql` interfaces; a **driver** implements them. Four core types: `Connection` (a session), `PreparedStatement` (precompiled parameterized SQL), `ResultSet` (row cursor), `DriverManager`/`DataSource` (hand out connections). All `AutoCloseable` → try-with-resources everywhere; a leaked Connection permanently starves the pool.

**PreparedStatement vs SQL injection — the #1 security lesson (demonstrated live):** string-concatenated SQL lets input like `Ajay'; DROP TABLE account; --` execute as code. `?` placeholders send SQL and data on **separate channels** — the driver never parses bound values as SQL, so injection is structurally impossible. Bonus: the DB caches the compiled plan across calls (faster), and parameters are 1-indexed (JDBC's quirk). **Always parameterize; never concatenate user input into SQL.**

**Patterns:** `executeQuery` → ResultSet (SELECT); `executeUpdate` → int rows-affected (INSERT/UPDATE/DELETE); `Statement.RETURN_GENERATED_KEYS` + `getGeneratedKeys()` to retrieve DB-assigned ids. Mapping `ResultSet` rows to objects by hand (the `while (rs.next())` loop building records) is exactly the tedium ORMs automate (7.3). Push aggregation into the DB (`JOIN`/`GROUP BY`/`SUM`) rather than pulling rows into Java — less data over the wire, indexed engine does the work.

## 7.2 — Connection pooling & transactions

**Pooling — why:** opening a DB connection is expensive (TCP handshake, auth, session setup — tens of ms). Per-request connect/disconnect crushes a web app. A **pool** (HikariCP — the standard, Spring Boot's default) keeps N connections open and lends them; `connection.close()` **returns it to the pool** rather than disconnecting. `DataSource` is the modern connection source that replaces `DriverManager` — frameworks inject it. Key knob: `maximumPoolSize` (too small → threads wait; too big → the DB is overwhelmed; a common formula is `connections ≈ ((core_count × 2) + effective_spindle_count)` — usually a small double-digit number, not hundreds).

**Transactions — ACID:** **A**tomic (all-or-nothing), **C**onsistent (invariants preserved), **I**solated (concurrent txns don't corrupt each other), **D**urable (committed = survives crash). The classic case: a transfer = debit + credit; a crash between them without a transaction loses money that never arrived. Demonstrated live: a transfer that fails after the debit **rolled back completely** — balances unchanged.

**The transaction pattern (memorize the shape):**
```
setAutoCommit(false);          // BEGIN — stop per-statement commits
try { ...work...; commit(); }  // all permanent, atomically
catch { rollback(); throw; }   // undo everything on any failure
finally { setAutoCommit(true); close(); }  // reset, return to pool
```
`SELECT ... FOR UPDATE` locks a row (pessimistic locking) so a concurrent transfer can't read a stale balance and double-spend.

**Isolation levels** (weaker→stronger, more concurrent→more consistent): READ_UNCOMMITTED (dirty reads), READ_COMMITTED (Postgres default — sees only committed data), REPEATABLE_READ (MySQL default — a row reads identically all txn), SERIALIZABLE (as if serial). The anomalies form a ladder: **dirty read → non-repeatable read → phantom read**, each level blocking one more. READ_COMMITTED is right ~95% of the time. **Optimistic locking** (a `@Version` column, retry on conflict) is the scalable alternative to `FOR UPDATE` for low-contention workloads — JPA supports it natively.

## 7.3 — JPA / Hibernate

**What & why:** JPA (Jakarta Persistence API) is the *spec*; **Hibernate** is the dominant *implementation*. An **ORM** (Object-Relational Mapper) maps objects ↔ table rows so you manipulate Java objects and it generates the SQL — eliminating the hand-written `ResultSet`→object mapping of 7.1. The output shows Hibernate's `Hibernate: insert/select/update/delete` SQL for every object operation.

**Mapping via annotations:** `@Entity` (class↔table), `@Table`, `@Id` + `@GeneratedValue(strategy = IDENTITY)` (PK, DB-assigned), `@Column(nullable, precision, scale)`. Entities need a **no-arg constructor and mutable fields** (the framework instantiates then populates via reflection) — one of the rare places `record` doesn't fit.

**Core runtime types:** `EntityManagerFactory` (heavy — one per app, like a pool), `EntityManager` (a short-lived *persistence context* / unit of work), `EntityTransaction` (7.2's begin/commit/rollback, wrapped). `persist` (INSERT), `find` (SELECT by PK), `remove` (DELETE).

**Dirty checking — the ORM's party trick (demonstrated):** a *managed* entity loaded in a transaction is watched; mutate a field and at commit Hibernate **diffs it against its loaded snapshot and auto-issues UPDATE** — no `update()` call exists. The output proves it: `setBalance(...)` produced a `Hibernate: update account set balance=?` with nothing else asked. Entity lifecycle states: **transient** (new, unmanaged) → **managed** (in the persistence context, dirty-checked) → **detached** (context closed) → **removed**.

**JPQL:** object-oriented query language over *entities and fields* (`SELECT a FROM Account a WHERE a.balance > :min`) — Hibernate translates to SQL; still parameterized (`:min`), still injection-safe. Also: Criteria API (type-safe programmatic queries), native SQL when needed.

**The wins:** object-oriented code, DB portability (H2↔Postgres, no code change), dirty checking, caching (first/second level), lazy loading, relationship mapping (`@OneToMany`/`@ManyToOne`).

**The traps (know these — they're interview and production gold):**
- **N+1 SELECT:** load a list (1 query), then a relation per element (N queries). Fix: `JOIN FETCH`, entity graphs, batch size.
- **LazyInitializationException:** touching a lazily-loaded relation after the EntityManager closed. Fix: fetch within the transaction, or fetch eagerly where needed.
- **Leaky abstraction:** you *still* must know SQL to read the generated queries and diagnose performance — the ORM hides SQL, it doesn't excuse ignorance of it. (Why 7.1 came before 7.3.)

**`hibernate.hbm2ddl.auto`:** `create-drop` (build schema from entities at startup, drop at shutdown — demos/tests) vs `validate`/`none` (production, with **Flyway/Liquibase** migrations owning the schema). Never `create-drop`/`update` against a real database.

**The bridge to Spring Boot (track 02):** `persistence.xml`, the EntityManagerFactory, the boilerplate `inTransaction` wrapper — Spring Boot generates all of it from `application.properties` and injects a ready EntityManager. **Spring Data JPA** goes further: declare `interface AccountRepository extends JpaRepository<Account, Long>` and methods like `findByBalanceGreaterThan(BigDecimal)` are *implemented for you* from the method name. This phase is the "under the hood" that makes that magic legible instead of mysterious.
