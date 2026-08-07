<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · tooling](../phase-6-tooling/NOTES.md)
<!-- /nav -->

# Phase 7 — Data Access: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 7.1 — SQL & JDBC

**Q: What is SQL injection and how do you prevent it?** ⭐⭐ *guaranteed*
Injecting SQL through unsanitized input concatenated into a query — `Ajay'; DROP TABLE account; --` turns a lookup into a destructive command. Prevention: **PreparedStatement with `?` parameters** — SQL and data travel on separate channels, so bound values are never parsed as SQL. Never build queries by string concatenation of user input. (ORMs/JPQL parameterize by default, but native queries can reintroduce the hole.)

**Q: PreparedStatement vs Statement?** ⭐
PreparedStatement is precompiled and parameterized: injection-safe, and the DB caches the execution plan across calls (faster for repeated queries). Statement takes a raw SQL string — fine for fixed DDL, dangerous with any dynamic input. Default to PreparedStatement.

**Q: What are the JOIN types?** ⭐
INNER (only matching rows), LEFT (all left rows + matched right, NULLs where unmatched), RIGHT (mirror), FULL OUTER (all rows both sides), CROSS (cartesian product). LEFT JOIN + `COALESCE(SUM(...), 0)` is the standard "counts per parent including zeros" pattern.

**Q: WHERE vs HAVING?**
WHERE filters *rows before* grouping; HAVING filters *groups after* aggregation (`HAVING COUNT(*) > 5`). You can't use an aggregate in WHERE. Rooted in SQL's logical evaluation order (FROM→WHERE→GROUP BY→HAVING→SELECT→ORDER BY).

**Q: What is an index? Trade-offs?** ⭐
A B-tree (usually) on column(s) turning O(n) scans into O(log n) lookups for WHERE/JOIN/ORDER BY. Costs: slower INSERT/UPDATE/DELETE (index maintenance) and disk space. Index the columns you filter/join on; don't over-index write-heavy tables. The PK is auto-indexed.
*Follow-up: "How would you find a slow query's problem?" — `EXPLAIN`/`EXPLAIN ANALYZE` shows the plan; a "seq scan" on a big filtered table means a missing index.*

**Q: Why store money as DECIMAL, not DOUBLE?** ⭐
DECIMAL(p,s) is exact base-10; DOUBLE is binary floating point and can't represent 0.10 exactly (lesson 1.2) — errors compound across financial math. Same reason `BigDecimal` in Java.

**Q: Why must JDBC resources be closed, and how?**
Connections, Statements, ResultSets hold DB/socket resources and (pooled) a scarce connection; leaks starve the pool and exhaust the DB's connection limit. Use try-with-resources — they're all AutoCloseable — so they close even on exception.

**Q: What's the N+1 problem at the JDBC level?** *bridge*
Querying a list then issuing one query per element in a loop — N+1 round trips. Fix with a JOIN or an `IN (...)` batch. It reappears (and bites harder) in ORMs (7.3).

## 7.2 — Pooling & transactions

**Q: Why use a connection pool?** ⭐
Establishing a DB connection is expensive (handshake, auth, session) — tens of milliseconds. A pool keeps connections open and reuses them, so `close()` returns to the pool instead of disconnecting. Without one, a busy web app spends most of its time connecting and can exhaust the DB's connection limit. HikariCP is the de-facto standard (Spring Boot default).

**Q: How do you size a connection pool?** *senior probe*
Counterintuitively small — more connections than the DB can concurrently service just adds contention. A common starting formula: `((cores × 2) + effective_spindles)`, typically a small double-digit number; then measure. The bottleneck is usually the database, not the pool.

**Q: What does ACID stand for?** ⭐⭐
Atomicity (all-or-nothing), Consistency (invariants/constraints hold before and after), Isolation (concurrent transactions don't interfere), Durability (committed data survives crashes). The transfer example: debit + credit must be atomic or money vanishes/appears.

**Q: Walk through the JDBC transaction pattern.** ⭐
`setAutoCommit(false)` to begin; do the work; `commit()` on success; `rollback()` in a catch on any failure; restore `setAutoCommit(true)` and close in finally. Autocommit-on (the default) commits every statement individually — no atomic multi-step operations.

**Q: Explain isolation levels and the anomalies they prevent.** ⭐⭐
READ_UNCOMMITTED → allows dirty reads (seeing uncommitted data). READ_COMMITTED → blocks dirty reads (Postgres default). REPEATABLE_READ → also blocks non-repeatable reads, a re-read row is stable (MySQL default). SERIALIZABLE → also blocks phantom reads, as if transactions ran one at a time. Ladder of anomalies: dirty → non-repeatable → phantom. Stronger = more correct, less concurrent.

**Q: Optimistic vs pessimistic locking?** ⭐
Pessimistic: lock the row up front (`SELECT ... FOR UPDATE`) — others wait; safe under high contention, throughput cost. Optimistic: no lock; keep a `version` column, and on update check the version hasn't changed, retry if it did — scales well when conflicts are rare. JPA implements optimistic locking with `@Version`.

**Q: What is a deadlock at the database level?**
Two transactions each hold a lock the other needs — the DB detects the cycle and kills one (victim gets a rollback + error to retry). Prevention mirrors thread deadlocks (5.2): consistent ordering of row/table access.

## 7.3 — JPA / Hibernate

**Q: JPA vs Hibernate — what's the difference?** ⭐
JPA (Jakarta Persistence API) is the *specification* (interfaces/annotations — `EntityManager`, `@Entity`); Hibernate is the most common *implementation*. Coding to JPA keeps you provider-portable; Hibernate adds extras beyond the spec. "JPA is the interface, Hibernate is the class."

**Q: What is an ORM and why/when use one?** ⭐
Object-Relational Mapper — maps objects↔rows, generating SQL from object operations. Wins: less boilerplate (no manual ResultSet mapping), DB portability, caching, dirty checking, relationship navigation. Costs: a learning curve, hidden queries, and performance traps (N+1). Use for standard CRUD-heavy apps; drop to SQL/JDBC (or jOOQ) for complex reporting queries.

**Q: What is dirty checking?** ⭐⭐
Within a transaction, the persistence context holds a snapshot of each managed entity; at flush/commit Hibernate compares current state to the snapshot and auto-generates UPDATEs for changed fields — you never call `update()`. Demonstrated: a `setBalance` produced an UPDATE with no explicit save. (Corollary: mutating a managed entity outside your intent silently writes to the DB — a real gotcha.)

**Q: Entity lifecycle states?** ⭐
**Transient** (new object, not in a context, no DB row) → **Managed/Persistent** (attached to an EntityManager, dirty-checked) → **Detached** (context closed; changes no longer tracked) → **Removed** (marked for DELETE at flush). `persist` transient→managed, `find` loads managed, `remove` managed→removed, `merge` detached→managed.

**Q: What is the N+1 SELECT problem and how do you fix it?** ⭐⭐ *the ORM interview question*
Load N parents in 1 query, then a lazy relation triggers 1 query per parent → N+1 round trips, often invisibly. Fixes: `JOIN FETCH` in JPQL, `@EntityGraph`, batch fetching (`@BatchSize`), or a projection/DTO query. Detect it by watching the SQL log explode. Interviewers love this because it separates people who've run ORMs in production from those who've only read about them.

**Q: What is LazyInitializationException?**
Accessing a lazily-fetched association after its EntityManager/session has closed — there's no open context to load it. Fixes: access it inside the transaction, use JOIN FETCH/entity graph, or (carefully) `open-session-in-view`. It's the flip side of lazy loading's benefit.

**Q: FetchType LAZY vs EAGER?**
LAZY (default for collections/`@OneToMany`): the relation loads on first access — avoids over-fetching but risks LazyInitializationException/N+1. EAGER (default for `@ManyToOne`): loaded with the parent — convenient but can pull huge graphs. Best practice: default everything LAZY and fetch explicitly where needed.

**Q: Should a JPA entity be a record?**
No — JPA requires a no-arg constructor and mutable, proxy-able fields (it instantiates then populates via reflection, and creates lazy proxies by subclassing). Records are final and immutable. Records shine as DTOs/query projections *around* the entity layer, not as entities.

**Q: Why still learn raw SQL/JDBC if ORMs exist?** ⭐
Because ORMs are a leaky abstraction: diagnosing N+1, slow queries, and lock contention requires reading the generated SQL and understanding indexes, joins, and execution plans. The ORM writes SQL for you; it doesn't free you from knowing it. (And some queries are cleaner as native SQL.)
