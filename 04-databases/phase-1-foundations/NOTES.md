<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · multi table ➡](../phase-2-multi-table/NOTES.md)
<!-- /nav -->

# Phase 1 — Relational Foundations & SQL Basics: Notes (Theory)

## 1.1 — What an RDBMS is

A **relational database** stores data as **tables** (relations): rows (records/tuples) and columns (fields/attributes), each column having a fixed **type**. A **RDBMS** (PostgreSQL, MySQL, Oracle, SQL Server) is the software managing them — enforcing types and constraints, running queries, handling concurrency and durability.

**Why relational dominates:** a clean mathematical model (relational algebra), a declarative query language (**SQL** — you say *what* you want, the engine decides *how*), strong consistency (ACID, Phase 5), and flexible ad-hoc querying (join anything to anything). The trade-off vs NoSQL is rigidity of schema and harder horizontal scaling (Phase 8).

**Keys:**
- **Primary key (PK)** — uniquely identifies a row; not null, unique. Prefer a **surrogate key** (`SERIAL`/`IDENTITY` integer, or UUID) over a natural key.
- **Foreign key (FK)** — a column referencing another table's PK; the DB enforces **referential integrity** (no orphan rows).

**SQL sub-languages:** **DDL** (define structure: `CREATE`/`ALTER`/`DROP`), **DML** (change data: `INSERT`/`UPDATE`/`DELETE`), **DQL** (`SELECT`), **DCL** (`GRANT`/`REVOKE`), **TCL** (`BEGIN`/`COMMIT`/`ROLLBACK`).

`psql` is Postgres's terminal client. Useful meta-commands: `\dt` (list tables), `\d book` (describe a table), `\l` (list databases), `\?` (help), `\q` (quit).

## 1.2 — SELECT / WHERE / ORDER BY / LIMIT / DISTINCT

- **`SELECT`** = *projection* (which columns). **`WHERE`** = *selection* (which rows). These are the two fundamental operations of relational algebra.
- **Operators:** `=`, `<>`/`!=`, `<`, `>`, `BETWEEN a AND b` (inclusive), `IN (...)`, `LIKE`/`ILIKE` (`%` = any run of chars, `_` = one char; ILIKE = case-insensitive), `IS NULL`.
- **`ORDER BY col [ASC|DESC] [NULLS FIRST|LAST]`**, multi-key (`ORDER BY genre, price DESC`).
- **`LIMIT n OFFSET m`** — top-N and pagination (though keyset/cursor pagination scales better than large OFFSETs).
- **`DISTINCT`** removes duplicate result rows; `DISTINCT a, b` dedupes on the *combination*.

**The one rule that explains many errors — logical evaluation order:**
```
FROM → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT
```
You write `SELECT ... FROM ... WHERE ...`, but the engine evaluates `FROM`/`WHERE` first and `SELECT` late. Consequences: a `SELECT` alias is usable in `ORDER BY` (runs after) but **not** in `WHERE` (runs before); aggregates can't appear in `WHERE` (use `HAVING`, Phase 2).

## 1.3 — Data types & NULL (three-valued logic)

**Types you'll use constantly:** `INTEGER`/`BIGINT`, **`NUMERIC(p,s)` for money** (exact — never `REAL`/`DOUBLE` for currency, same reason as Java `BigDecimal`), `TEXT` (prefer over `VARCHAR(n)` in Postgres unless you need the length cap), `BOOLEAN`, `DATE`/`TIMESTAMPTZ` (prefer timezone-aware), `SERIAL` (auto-increment shortcut), plus Postgres specialties `UUID`/`JSONB`/arrays (Phase 6). Cast with `value::type` or `CAST(value AS type)`.

**NULL = "unknown / missing," not zero/empty/false.** This creates **three-valued logic**: comparisons yield TRUE, FALSE, or **UNKNOWN**, and `WHERE` keeps only TRUE.
- `x = NULL` and `x <> NULL` are **always UNKNOWN → 0 rows**. Use **`IS NULL` / `IS NOT NULL`**.
- Any arithmetic with NULL → NULL (contagious).
- `NOT IN (subquery with a NULL)` silently returns nothing — a classic bug; prefer `NOT EXISTS`.
- Aggregates **skip NULLs**: `count(*)` counts rows, `count(col)` counts non-null values.
- Tools: **`COALESCE(a, b, ...)`** (first non-null — defaults/display), **`NULLIF(a, b)`** (null if equal), **`a IS DISTINCT FROM b`** (null-safe `<>`).

Mental model: when rows vanish unexpectedly, suspect a NULL meeting `=`, `<>`, `NOT IN`, or a join condition.

## 1.4 — DML: INSERT / UPDATE / DELETE / RETURNING

- **`INSERT INTO t (cols) VALUES (...), (...)`** — multi-row in one statement is far faster than N statements.
- **`UPDATE t SET c = ... WHERE ...`** and **`DELETE FROM t WHERE ...`** — **always include `WHERE`** or you hit every row. Safety habit: write it as a `SELECT` first to preview the matched rows, then convert.
- **`RETURNING`** hands back values the DB generated (the `SERIAL` id, computed columns) without a second round-trip — this is exactly what JDBC's `getGeneratedKeys()` used.
- **`INSERT ... ON CONFLICT (key) DO UPDATE`** = **upsert** ("save": insert or update if it exists); `EXCLUDED` is the would-be-inserted row.
- **`TRUNCATE`** empties a table fast (no per-row overhead) but is DDL-like and can't be filtered.
- All DML runs inside a **transaction** (Phase 5): until `COMMIT` you can `ROLLBACK`. In `psql`, wrap risky changes in `BEGIN; ... ROLLBACK;` to rehearse safely.
