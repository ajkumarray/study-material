<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · tooling](../phase-6-tooling/NOTES.md)
<!-- /nav -->

# Phase 7 — Data Access: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 7.1 — SQL & JDBC

**Q: What is SQL injection, and how do you prevent it?** ⭐⭐ *guaranteed*

SQL injection is executing attacker-controlled SQL by concatenating unsanitized input directly into a query string — a value like `Ajay'; DROP TABLE account; --` closes the intended string literal early and injects an entirely separate, destructive statement that the database then genuinely runs as SQL. Prevention is structural, not just "be careful": use **`PreparedStatement` with `?` parameters**. SQL text and bound data are sent to the database on **separate channels**, so a bound value is never parsed as SQL syntax no matter what characters it contains — injection becomes impossible by construction, not just unlikely. Never build a query by string-concatenating user input, ever. (Worth noting: ORMs and JPQL parameterize by default too, but dropping down to a native SQL query inside an ORM can reintroduce exactly this same hole if you concatenate there instead of parameterizing.)

**Q: `PreparedStatement` vs. plain `Statement` — what's the difference?** ⭐

`PreparedStatement` is precompiled and parameterized: it's injection-safe by construction, and the database typically caches the compiled execution plan across repeated calls with different parameter values, making it faster for queries executed repeatedly. `Statement` takes a raw, complete SQL string with no parameter binding — acceptable for fixed DDL with no dynamic input, but genuinely dangerous the moment any part of the query text comes from outside the application. Default to `PreparedStatement` for anything involving external input.

**Q: What are the SQL JOIN types?** ⭐

`INNER JOIN` returns only rows with a match on both sides. `LEFT JOIN` returns every row from the left table plus matched rows from the right, with `NULL` filled in for right-side columns where there's no match. `RIGHT JOIN` is the mirror image from the right side. `FULL OUTER JOIN` returns every row from both sides regardless of match. `CROSS JOIN` produces the full cartesian product of both tables. The standard pattern for "counts per parent including parents with zero children" is a `LEFT JOIN` combined with `COALESCE(SUM(...), 0)` or `COALESCE(COUNT(...), 0)` — an `INNER JOIN` there would silently drop parents that have no matching children at all.

**Q: `WHERE` vs. `HAVING` — what's the difference?**

`WHERE` filters individual **rows**, evaluated **before** any grouping happens. `HAVING` filters **groups**, evaluated **after** aggregation (`HAVING COUNT(*) > 5`) — and you cannot reference an aggregate function in `WHERE`, because `WHERE` is logically evaluated before `GROUP BY` even runs. This is rooted directly in SQL's logical evaluation order: `FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY`.

**Q: What is a database index, and what are its trade-offs?** ⭐

An index (typically a B-tree) built on one or more columns turns what would otherwise be an O(n) full table scan into an O(log n) lookup for `WHERE`/`JOIN`/`ORDER BY` operations that reference those columns. The cost: every `INSERT`/`UPDATE`/`DELETE` must also maintain the index, making writes somewhat slower, plus the index itself consumes disk space. The primary key is automatically indexed by default. Practical guidance: index the columns you actually filter and join on regularly; avoid indexing every column on a write-heavy table indiscriminately, since each additional index adds write overhead for a benefit that may never actually be used.

*Follow-up: "How would you actually find out why a specific query is slow?"* `EXPLAIN` (or `EXPLAIN ANALYZE` for real execution statistics, not just the planned strategy) shows the database's query execution plan. A "sequential scan" appearing on a large table that's being filtered by a specific column is the classic sign of a missing index on that column.

**Q: Why store money as `DECIMAL`, never `DOUBLE`?** ⭐

`DECIMAL(precision, scale)` is exact base-10 arithmetic. `DOUBLE` is binary floating point, and — exactly as covered in Phase 1 — it cannot represent most decimal fractions like `0.10` exactly, so repeated financial arithmetic on `DOUBLE` values accumulates small but real, eventually visible errors. This is precisely the same reason Java code uses `BigDecimal` (constructed from a `String`, not a `double` literal) for money rather than `double` — the database-level and language-level lessons are the same underlying issue.

**Q: Why must JDBC resources be closed, and what's the idiomatic way to do it?**

`Connection`, `Statement`, and `ResultSet` objects all hold real underlying resources — database-side session state, and for a pooled `Connection` specifically, one of a strictly limited number of pool slots. A leaked, never-closed connection permanently occupies a pool slot it will never return, which under sustained load eventually starves the pool entirely and can exhaust the database's own connection limit. The idiomatic fix is try-with-resources (Phase 3) — every one of these JDBC types implements `AutoCloseable`, so nesting them in try-with-resources guarantees they close even when an exception is thrown partway through.

**Q: What's the N+1 problem, described at the raw JDBC level?** *bridge*

Querying a list of parent rows with one query, then issuing a separate query per element inside a loop to fetch related data for each one — N+1 total round trips to the database instead of one or two. The standard fix is a single `JOIN` that pulls everything needed in one query, or batching the related lookups into one `IN (...)` query instead of N separate ones. This exact problem reappears — and typically bites much harder, since it's often invisible in ORM-generated code — in the ORM layer (7.3).

## 7.2 — Pooling & Transactions

**Q: Why use a connection pool instead of opening a fresh connection per request?** ⭐

Because establishing a raw database connection is genuinely expensive — a TCP handshake, authentication, and session setup, commonly tens of milliseconds in total. A busy web application opening and closing a connection for every single request would spend a large share of its total time just connecting, and could exhaust the database's own maximum connection count under real load. A pool keeps a fixed set of connections genuinely open in the background and hands them out on demand; calling `close()` on a pooled connection **returns it to the pool** instead of actually disconnecting. HikariCP is the de-facto standard implementation, and it's Spring Boot's default connection pool.

**Q: How do you actually size a connection pool?** *senior probe*

Counterintuitively small — configuring more connections than the database can efficiently service concurrently just adds contention on the database side and can actually *reduce* overall throughput, not increase it. A commonly cited starting formula is roughly `(core_count × 2) + effective_spindle_count`, which typically lands on a small double-digit number even for a fairly busy service — then you measure under realistic load and adjust. The bottleneck in practice is usually the database server itself, not the pool's configured size.

**Q: What does ACID stand for?** ⭐⭐

**A**tomicity — a transaction's operations happen entirely or not at all, with no partial state ever visible to anyone else. **C**onsistency — a transaction moves the database from one state satisfying all its constraints to another such state. **I**solation — concurrent transactions don't see each other's uncommitted, in-flight changes (with the *degree* of isolation controlled by the isolation level). **D**urability — once a transaction commits, its effects survive even a subsequent crash. The canonical motivating example is a money transfer: a debit and a credit that must succeed or fail together, or money can vanish (debited but never credited) or appear from nowhere (credited without ever being debited) if a failure happens in between the two operations without a transaction wrapping them.

**Q: Walk through the standard JDBC transaction pattern.** ⭐

`connection.setAutoCommit(false)` to begin an explicit transaction (JDBC connections default to auto-commit, where every individual statement is its own implicit transaction, committed immediately). Then perform the actual work. On success, call `commit()`, making every change since the transaction began permanent atomically. On any failure, call `rollback()` in a `catch` block, undoing **everything** done since the transaction began — including any statements that already executed successfully before the failure occurred. Finally, restore `setAutoCommit(true)` and close the connection (returning it to the pool) in a `finally` block, regardless of outcome.

**Q: Explain isolation levels and the specific anomalies each one prevents.** ⭐⭐

`READ_UNCOMMITTED` allows **dirty reads** — seeing another transaction's uncommitted, possibly-about-to-be-rolled-back data. `READ_COMMITTED` blocks dirty reads, only ever seeing committed data (PostgreSQL's default). `REPEATABLE_READ` additionally blocks **non-repeatable reads** — a row read twice within the same transaction is guaranteed to return the same value both times, even if another transaction commits a change to it in between (MySQL's default). `SERIALIZABLE` additionally blocks **phantom reads** — a repeated query matching a *set* of rows returns the same set both times, as if every transaction genuinely ran one at a time in some serial order. The anomalies form a strict ladder — dirty read → non-repeatable read → phantom read — and each stronger isolation level closes off one more of them, generally at some cost to concurrency. `READ_COMMITTED` is the right choice roughly 95% of the time in practice.

**Q: Optimistic vs. pessimistic locking — what's the difference, and when do you pick each?** ⭐

Pessimistic locking (`SELECT ... FOR UPDATE`) locks the relevant row immediately upon reading it, forcing any other transaction that wants to touch that same row to wait until the first one finishes. It's safe under high contention, but at a real throughput cost, since it actively blocks concurrent access. Optimistic locking takes no lock at all — instead, each row carries a `version` column, and an update checks that the version hasn't changed since it was originally read, failing (to be retried) if it has changed in the meantime. It scales well precisely when actual conflicts are rare, since the common case pays no locking overhead at all. JPA implements optimistic locking natively via a `@Version` field on an entity.

**Q: What is a deadlock at the database level, and how does it differ from the JVM thread deadlock in Phase 5?**

Two (or more) transactions each hold a lock the other one needs next — exactly the same structural cycle as a thread deadlock (Phase 5), just at the database's row/table-lock level instead of the JVM's monitor level. Unlike a JVM thread deadlock, though, the database itself actively **detects** this cycle and automatically kills one of the two transactions (the "victim"), rolling it back and returning an error the application is expected to catch and retry. Prevention mirrors the thread-deadlock prevention strategy directly: acquire locks on rows/tables in a **consistent order** across every transaction that touches more than one of them, making a circular wait structurally impossible.

## 7.3 — JPA / Hibernate

**Q: JPA vs. Hibernate — what's the actual relationship between them?** ⭐

JPA (Jakarta Persistence API) is the **specification** — a set of interfaces and annotations (`EntityManager`, `@Entity`, and so on) with no behavior of its own. Hibernate is the most widely used concrete **implementation** of that specification. Coding directly against the JPA interfaces (rather than Hibernate-specific extensions) keeps your code portable across different JPA providers; Hibernate does also offer some extras beyond the spec itself. Short version, good for an interview: "JPA is the interface, Hibernate is the class."

**Q: What is an ORM, and when should you actually reach for one?** ⭐

An Object-Relational Mapper maps Java objects to database table rows, generating the necessary SQL automatically from object-level operations (`persist`, `find`, `remove`) instead of requiring hand-written SQL and manual `ResultSet`-to-object mapping. The wins: significantly less boilerplate, database portability (the same code can run against H2 in tests and PostgreSQL in production with zero changes), built-in caching, automatic dirty checking, and convenient relationship navigation. The costs: a genuine learning curve, SQL generation that's somewhat hidden from casual view, and real performance traps like N+1 queries. In practice, ORMs are a strong default for standard CRUD-heavy application code; for complex reporting-style queries with heavy aggregation, dropping down to raw SQL, JDBC, or a SQL-centric library like jOOQ is often cleaner.

**Q: What is dirty checking, and how does it actually work?** ⭐⭐

Within an active transaction, the persistence context (the `EntityManager`) keeps a snapshot of every entity it has loaded. At flush/commit time, Hibernate compares each managed entity's **current** in-memory state against that original snapshot, and automatically generates and issues `UPDATE` statements for whatever fields actually differ — no explicit `update()` or `save()` method call exists or is ever needed. This is directly demonstrable: calling a plain setter like `account.setBalance(newBalance)` on a managed entity, with no other code, produces a real `UPDATE` statement in the SQL log at commit time. The corollary worth flagging as a real gotcha: mutating a managed entity's field, even by accident, silently produces a real database write when the surrounding transaction commits.

**Q: Describe the entity lifecycle states.** ⭐

**Transient** — a newly constructed object with no association to any persistence context, no corresponding database row. **Managed** (or "persistent") — attached to an active `EntityManager`, actively dirty-checked, any field change automatically written at commit. **Detached** — was managed, but its owning `EntityManager` has since closed; the object still holds whatever data it had, but changes to it are no longer tracked or persisted at all. **Removed** — marked for deletion; the actual `DELETE` fires at flush/commit time. `persist()` moves an entity from transient to managed; `find()` loads an entity directly into the managed state; `remove()` moves a managed entity to removed; `merge()` brings a detached entity's current state back into a managed entity within a new persistence context.

**Q: What is the N+1 SELECT problem, and how do you fix it?** ⭐⭐ *the ORM interview question*

Loading a list of N parent entities issues exactly 1 query. Then, if code subsequently accesses a lazily-loaded relationship on each of those N parents (inside a loop, say), each access triggers its own separate query — N additional queries, for N+1 total round trips to the database, often completely invisible in the application code itself since each individual access looks like an innocuous field read. Standard fixes: `JOIN FETCH` in a JPQL query to eagerly pull the related data in the *same* query as the parents; `@EntityGraph` to declare which relationships to fetch eagerly for a specific query without changing the entity's default fetch type globally; batch fetching (`@BatchSize`) to at least group the N follow-up queries into a smaller number of `IN (...)`-style batches; or a dedicated projection/DTO query that only selects exactly the fields actually needed. This question specifically separates candidates who have actually run an ORM against a real dataset in production (where N+1 shows up as mysteriously slow list endpoints) from those who have only read about ORMs in the abstract.

**Q: What is `LazyInitializationException`?**

It's thrown when code attempts to access a lazily-fetched relationship on an entity **after** the `EntityManager` (persistence context) that loaded it has already closed — there's no active session left through which to actually go fetch the lazy data on demand. Fixes: access the lazy relationship while still inside the original transaction/session boundary; fetch it eagerly up front with `JOIN FETCH` or an entity graph if you know in advance it will be needed; or, more carefully and situationally, an "open session in view" pattern that keeps the session open somewhat longer (with its own trade-offs). It's essentially the direct downside risk that comes paired with lazy loading's performance benefit.

**Q: `FetchType.LAZY` vs. `FetchType.EAGER` — what's the difference, and which is the default for what?**

`LAZY` (the default for collection-valued relationships like `@OneToMany`) loads the related data only on first actual access — this avoids over-fetching data that might never be needed, but risks both `LazyInitializationException` (accessing it too late) and N+1 queries (accessing it inside a loop). `EAGER` (the default for `@ManyToOne`) loads the related data immediately alongside the owning entity — more convenient, but can silently pull in much larger object graphs than actually needed for a given operation. Common best practice: default essentially everything to `LAZY`, and fetch eagerly only explicitly, per-query, exactly where it's actually needed (via `JOIN FETCH`/entity graphs) — rather than relying on a blanket `EAGER` default that fetches too much, too often.

**Q: Should a JPA entity be written as a `record`?**

No. JPA requires a no-arg constructor and mutable, proxy-able fields — the provider instantiates an entity and populates it via reflection, and creates dynamic lazy-loading proxy subclasses for relationships by extending the entity class. Records are `final` (can't be subclassed for proxying) and immutable (no field can be populated after construction by reflection in the way JPA needs). Records are, however, an excellent fit for DTOs and query result projections layered *around* the entity model, just not as the entities themselves.

**Q: Why still learn raw SQL and JDBC if you're going to use an ORM anyway?** ⭐

Because an ORM is a **leaky abstraction** — it generates SQL for you, but it doesn't excuse not understanding SQL. Diagnosing an N+1 problem, a genuinely slow query, or database-level lock contention all require actually reading the SQL an ORM generated and understanding indexes, joins, and execution plans directly — none of which the ORM layer itself will explain to you when something goes wrong. And some queries, particularly complex reporting/aggregation queries, are genuinely cleaner and more efficient written as native SQL than forced through an object-oriented query language. This is also precisely why this curriculum covers raw SQL/JDBC (7.1) before JPA/Hibernate (7.3) — understanding what the ORM is actually doing underneath makes it a legible tool rather than a mysterious black box.
