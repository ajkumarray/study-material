<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · multi table ➡](../phase-2-multi-table/NOTES.md)
<!-- /nav -->

# Phase 1 — Foundations & SQL Basics: Interview Q&A

⭐ = asked constantly.

**Q: What is a primary key vs a foreign key?** ⭐
A primary key uniquely identifies each row in a table (unique + not null, one per table). A foreign key is a column that references another table's primary key, enforcing referential integrity — the database rejects rows that point to a non-existent parent. PKs are usually surrogate (auto-increment/UUID) rather than natural.

**Q: DDL vs DML vs DQL vs DCL vs TCL?**
DDL defines structure (`CREATE`/`ALTER`/`DROP`); DML changes data (`INSERT`/`UPDATE`/`DELETE`); DQL queries (`SELECT`); DCL controls access (`GRANT`/`REVOKE`); TCL manages transactions (`COMMIT`/`ROLLBACK`).

**Q: What is the logical order of execution of a SELECT?** ⭐⭐
`FROM → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT`. It explains why a `SELECT` alias works in `ORDER BY` but not in `WHERE`, and why aggregates go in `HAVING`, not `WHERE`.

**Q: `WHERE` vs `HAVING`?** ⭐
`WHERE` filters individual rows *before* grouping and can't use aggregates. `HAVING` filters *groups* after `GROUP BY` and is where aggregate conditions go (`HAVING count(*) > 5`). (Full aggregation in Phase 2.)

**Q: What is NULL, and how do you test for it?** ⭐⭐
NULL means unknown/missing — not 0, '', or false. Because comparisons with NULL yield UNKNOWN (three-valued logic), `= NULL` never matches; you must use `IS NULL` / `IS NOT NULL`. Handle it with `COALESCE`, `NULLIF`, or `IS DISTINCT FROM`.

**Q: What's the gotcha with `NOT IN` and NULL?** ⭐ *senior probe*
If the subquery/list contains a NULL, `NOT IN` returns no rows at all (every comparison becomes UNKNOWN). Prefer `NOT EXISTS`, which handles NULLs correctly.

**Q: `count(*)` vs `count(column)` vs `count(DISTINCT column)`?** ⭐
`count(*)` counts all rows. `count(column)` counts rows where that column is non-NULL (aggregates skip NULLs). `count(DISTINCT column)` counts distinct non-NULL values. A frequent trick question.

**Q: Why use `NUMERIC`/`DECIMAL` instead of `FLOAT` for money?** ⭐
Floating point is binary and can't represent most decimal fractions exactly, so errors accumulate (`0.1 + 0.2 ≠ 0.3`). `NUMERIC(p,s)` is exact base-10. Same principle as Java's `BigDecimal`.

**Q: `CHAR` vs `VARCHAR` vs `TEXT`?**
`CHAR(n)` is fixed-length (space-padded); `VARCHAR(n)` is variable with a max; `TEXT` is unlimited. In PostgreSQL all three have the same performance — prefer `TEXT` (or `VARCHAR` when a length limit is a real business rule).

**Q: `DELETE` vs `TRUNCATE` vs `DROP`?** ⭐
`DELETE` removes rows (optionally filtered by `WHERE`), is transactional and row-by-row. `TRUNCATE` removes *all* rows quickly with minimal logging, resets identity sequences, but can't be filtered. `DROP` removes the table (structure and all).

**Q: What does `RETURNING` do?**
Returns column values from rows affected by an `INSERT`/`UPDATE`/`DELETE` in the same statement — commonly to get an auto-generated id without a second query.

**Q: What's an upsert?**
"Insert or update." In Postgres: `INSERT ... ON CONFLICT (key) DO UPDATE SET ...` — inserts a new row, or updates the existing one if a unique/primary key conflicts. `EXCLUDED` refers to the values you tried to insert.

**Q: `LIKE` vs `ILIKE`, and the wildcards?**
`LIKE` does pattern matching where `%` matches any sequence and `_` matches a single character. `ILIKE` (Postgres) is the case-insensitive version. Leading-`%` patterns can't use a normal B-tree index (Phase 4).
