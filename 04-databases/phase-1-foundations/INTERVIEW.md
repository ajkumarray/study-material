<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · multi table ➡](../phase-2-multi-table/NOTES.md)
<!-- /nav -->

# Phase 1 — Relational Foundations & SQL Basics: Interview Q&A

⭐ = asked constantly. All examples reference the `author`/`book` bookstore schema from
`00-setup.sql` (see NOTES.md for the exact DDL and seed data).

**Q: What is a relational database, and what does "relational" actually refer to?** ⭐
A relational database stores data as tables (formally *relations*): a fixed set of
typed columns (attributes) and any number of rows (tuples). "Relational" comes from
relational algebra — Codd's mathematical model where a table is a *relation* and
queries are compositions of well-defined operators (selection, projection, join,
union, etc.). That's why SQL feels composable: a `JOIN` or a `WHERE` is just another
algebraic operator you can chain. Interviewers sometimes probe whether you think
"relational" means "tables have relationships to each other" — that's a common but
imprecise folk etymology; the formal meaning is about the table itself being a
mathematical relation, and foreign keys are simply one way to model relationships
between relations.

**Q: What is a primary key vs a foreign key?** ⭐
A primary key (PK) is the column (or set of columns) that uniquely identifies every row
in a table; it's implicitly `NOT NULL` and `UNIQUE`, and a table has at most one. A
foreign key (FK) is a column in one table that references another table's primary key,
and the database enforces **referential integrity** — you cannot insert a row whose FK
value doesn't exist as a PK in the parent table, and by default you cannot delete a
parent row that a child still references.
```sql
CREATE TABLE book (
    id         SERIAL PRIMARY KEY,
    author_id  INTEGER REFERENCES author(id),
    ...
);
INSERT INTO book (author_id, ...) VALUES (999, ...);
-- ERROR: insert or update on table "book" violates foreign key constraint
-- DETAIL: Key (author_id)=(999) is not present in table "author".
```
Follow-up: *surrogate vs natural key?* A surrogate key is a synthetic, meaningless
identifier (`SERIAL`/`IDENTITY`/UUID) added purely to be the PK; a natural key is a
real-world attribute (email, SSN, ISBN) that happens to be unique. Surrogate keys are
preferred in practice because natural keys can change (a person's email) or turn out
not to be as unique as assumed, which would force cascading updates through every FK
that references them.

**Q: DDL vs DML vs DQL vs DCL vs TCL — what's the difference?**
These are the five sub-languages of SQL, grouped by what they act on:
- **DDL** (Data Definition Language) — defines/changes structure: `CREATE`, `ALTER`,
  `DROP`, `TRUNCATE`. Runs implicitly outside normal row-level transactions in some
  databases (Postgres actually allows DDL inside transactions, which is a notable
  feature vs MySQL).
- **DML** (Data Manipulation Language) — changes data: `INSERT`, `UPDATE`, `DELETE`.
- **DQL** (Data Query Language) — reads data: `SELECT`. Sometimes folded into DML.
- **DCL** (Data Control Language) — controls access: `GRANT`, `REVOKE` (Phase 6).
- **TCL** (Transaction Control Language) — manages transaction boundaries: `BEGIN`,
  `COMMIT`, `ROLLBACK`, `SAVEPOINT` (Phase 5).
Interviewers use this taxonomy as a warm-up question, but a strong answer connects it
to something concrete — e.g. "TRUNCATE is technically DDL, which is why it can't be
filtered with WHERE and resets identity sequences, unlike DELETE which is DML."

**Q: What is the logical order of execution of a SELECT statement?** ⭐⭐
```
FROM → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT
```
This is one of the highest-value things to memorize because it explains several
"gotchas" people hit constantly:
- A column **alias** defined in `SELECT` cannot be used in `WHERE` (WHERE runs before
  SELECT) but **can** be used in `ORDER BY` (ORDER BY runs after SELECT).
- Aggregate functions (`COUNT`, `SUM`, ...) can't appear in `WHERE` — by the time
  `WHERE` runs, groups don't exist yet — you need `HAVING`, which runs after
  `GROUP BY`.
- `LIMIT` is dead last, which is why `LIMIT 10` after `ORDER BY` gives you the top 10
  by that order, not 10 arbitrary rows then sorted.
```sql
SELECT price, price * 1.1 AS with_tax
FROM book
WHERE price > 20         -- OK: price is a real column
ORDER BY with_tax DESC;  -- OK: with_tax is a SELECT alias, ORDER BY runs after SELECT
```
Follow-up: *would `WHERE with_tax > 20` work here?* No — `with_tax` doesn't exist yet
when `WHERE` is evaluated; you'd get `column "with_tax" does not exist`.

**Q: `WHERE` vs `HAVING`?** ⭐
`WHERE` filters individual rows before any grouping happens and cannot reference
aggregate functions. `HAVING` filters groups after `GROUP BY` has collapsed rows, and
is specifically for conditions on aggregates.
```sql
SELECT genre, COUNT(*) AS n
FROM book
WHERE price > 10          -- row filter, applied first
GROUP BY genre
HAVING COUNT(*) > 1;      -- group filter, applied after grouping
```
A quick way to remember it: if the condition needs an aggregate (`COUNT`, `AVG`,
`SUM`...), it belongs in `HAVING`; if it's about a raw column value, it belongs in
`WHERE` (and should be, since filtering early means fewer rows to group — better for
performance).

**Q: What is NULL, and how do you test for it?** ⭐⭐
`NULL` represents "unknown" or "missing," not zero, empty string, or false. This gives
SQL **three-valued logic**: any comparison involving `NULL` evaluates to `UNKNOWN`
(not `TRUE` or `FALSE`), and `WHERE`/`HAVING`/`JOIN ON` only keep rows where the
condition is `TRUE` — `UNKNOWN` rows are silently dropped, same as `FALSE` rows.
```sql
SELECT * FROM book WHERE genre = NULL;      -- 0 rows, always — this is a bug, not a filter
SELECT * FROM book WHERE genre IS NULL;     -- correct: returns "An Untitled Draft"
```
You must use `IS NULL` / `IS NOT NULL`, never `= NULL` / `<> NULL`. Beyond that,
`COALESCE(a, b, ...)` returns the first non-null argument (great for defaults),
`NULLIF(a, b)` returns NULL if `a = b` (handy for avoiding divide-by-zero:
`NULLIF(denominator, 0)`), and `a IS DISTINCT FROM b` is a null-safe `<>` that treats
two NULLs as equal to each other instead of UNKNOWN.

**Q: What's the gotcha with `NOT IN` and NULL?** ⭐ *senior-level probe*
If the list or subquery on the right side of `NOT IN` contains even one `NULL`, the
entire expression evaluates to `UNKNOWN` for every row, so the query returns **zero
rows** — silently, with no error.
```sql
SELECT * FROM book WHERE genre NOT IN ('tech', NULL);   -- always 0 rows, for ANY genre
SELECT * FROM book WHERE genre NOT IN
  (SELECT genre FROM book WHERE price < 15);             -- dangerous if that subquery can return NULL
```
`NOT IN (x, y, z)` is really `col <> x AND col <> y AND col <> z`; once one of those
comparisons is `UNKNOWN` (because it's being compared to NULL), the whole `AND` chain
can never be `TRUE`. The safe alternative is `NOT EXISTS`, which uses row existence
rather than value comparison and isn't tripped up by NULLs:
```sql
SELECT * FROM book b
WHERE NOT EXISTS (
  SELECT 1 FROM book b2 WHERE b2.genre = b.genre AND b2.price < 15
);
```
This is a favorite "gotcha" question precisely because it produces no error — just
silently wrong (empty) results, which is worse than a crash in production.

**Q: `count(*)` vs `count(column)` vs `count(DISTINCT column)`?** ⭐
`count(*)` counts every row regardless of NULLs. `count(column)` counts only rows
where that specific column is non-NULL (all aggregate functions skip NULLs).
`count(DISTINCT column)` counts distinct non-NULL values of that column.
```sql
SELECT count(*)               AS all_rows,      -- 8
       count(genre)           AS non_null_genre, -- 7 (one book has NULL genre)
       count(DISTINCT genre)  AS distinct_genres -- e.g. 4
FROM book;
```
This is a classic trick question because people assume `count(column)` behaves like
`count(*)` — it doesn't the moment that column has NULLs.

**Q: Why use `NUMERIC`/`DECIMAL` instead of `FLOAT`/`REAL` for money?** ⭐
`FLOAT`/`REAL`/`DOUBLE PRECISION` are binary floating point — they can't represent most
base-10 fractions exactly, so small rounding errors creep in and compound across
arithmetic (`0.1 + 0.2` isn't exactly `0.3` in binary floating point). `NUMERIC(p, s)`
(alias `DECIMAL(p, s)`) stores an exact base-10 value with `p` total digits and `s`
digits after the decimal point — no rounding error, at some cost in storage/CPU vs
native floats. `price NUMERIC(6,2)` in the `book` table means up to 6 total digits,
2 after the decimal (max `9999.99`). This is the same reasoning behind Java's
`BigDecimal` for currency — never use a binary float type for money, quantities that
must reconcile exactly, or anything audited.

**Q: `CHAR` vs `VARCHAR` vs `TEXT`?**
`CHAR(n)` is fixed-length and space-pads shorter values to length `n`. `VARCHAR(n)` is
variable-length with a maximum of `n` characters. `TEXT` is variable-length with no
length limit. In PostgreSQL specifically, all three have essentially identical
performance internally (unlike some other databases where `CHAR` can be faster) — the
Postgres-idiomatic advice is to use `TEXT` and only add a `CHECK (length(col) <= n)` or
`VARCHAR(n)` if the length cap is a genuine business rule you want the database to
enforce, not for a performance reason.

**Q: `DELETE` vs `TRUNCATE` vs `DROP`?** ⭐
- `DELETE FROM t WHERE ...` — removes matching rows (or all rows, without `WHERE`).
  It's DML, fully transactional (can be rolled back), fires triggers, and logs each
  row deletion — slow for large tables.
- `TRUNCATE t` — removes **all** rows instantly with minimal logging (it deallocates
  the table's data pages rather than deleting row by row), resets any `SERIAL`/
  identity sequence back to its start, but **cannot** be filtered with `WHERE`. It's
  DDL-like (acquires an `ACCESS EXCLUSIVE` lock) though Postgres does allow it inside a
  transaction and it can be rolled back there too.
- `DROP TABLE t` — removes the table's structure and data entirely; the table no
  longer exists.
```sql
DELETE FROM book WHERE price < 10;   -- removes some rows, id sequence untouched
TRUNCATE book;                       -- removes all rows, next id starts back at 1
DROP TABLE book;                     -- table gone
```

**Q: What does `RETURNING` do, and why is it useful?**
`RETURNING` attaches to `INSERT`/`UPDATE`/`DELETE` and hands back column values from
the affected rows in the same round trip, avoiding a second `SELECT`.
```sql
INSERT INTO author (name, country) VALUES ('New Author', 'IN')
RETURNING id, name;
--  id |    name
-- ----+------------
--  6  | New Author
```
This is exactly the SQL-native equivalent of JDBC's `getGeneratedKeys()` — instead of
inserting and then querying `currval()`/`lastInsertId()` separately, you get the
generated `SERIAL` id atomically with the insert.

**Q: What's an upsert, and how do you write one in Postgres?**
"Insert, or update if it already exists." Postgres does this with
`INSERT ... ON CONFLICT (key) DO UPDATE SET ...`, where `EXCLUDED` refers to the row
values that were attempted in the `INSERT`.
```sql
INSERT INTO author (id, name, country) VALUES (1, 'Robert C. Martin', 'US')
ON CONFLICT (id) DO UPDATE
  SET name = EXCLUDED.name, country = EXCLUDED.country;
```
If `id = 1` already exists, this updates its `name`/`country` to the new values
instead of raising a unique-violation error. `ON CONFLICT (key) DO NOTHING` is the
"insert if absent, otherwise silently skip" variant.

**Q: `LIKE` vs `ILIKE`, and what do the wildcards mean?**
`LIKE` does pattern matching: `%` matches any sequence of zero or more characters, `_`
matches exactly one character. `ILIKE` is PostgreSQL's case-insensitive version of
`LIKE`.
```sql
SELECT title FROM book WHERE title LIKE 'Clean%';   -- starts with "Clean"
SELECT title FROM book WHERE title ILIKE '%code%';  -- contains "code", any case
```
Follow-up: *why can a leading-`%` pattern (`LIKE '%code'`) hurt performance?* A normal
B-tree index is built on a left-to-right prefix, so it can efficiently jump to rows
starting with a known prefix (`'Clean%'`) but can't do the same for a pattern that
starts with a wildcard — that forces a full scan unless you add a trigram (`pg_trgm`)
index (covered in Phase 4).

**Q: What does `psql`'s `\d tablename` show you, and why would you use it over
`SELECT * FROM information_schema.columns`?**
`\d book` prints the table's columns, types, defaults, indexes, and foreign-key
constraints in one compact, human-readable view — it's a `psql` client meta-command,
not SQL, so it only works in the interactive `psql` terminal (or `psql -c`), not from
application code. `information_schema.columns` is the SQL-queryable, portable
(ANSI-standard) way to get the same information programmatically from any client or
another database engine, at the cost of being far more verbose to read by eye.

**Q: What's the difference between a `SERIAL` column and a manually managed integer
"counter" column, and why would you use one?**
`SERIAL` is Postgres sugar that creates a backing sequence and sets the column's
default to `nextval('sequence_name')`, so every `INSERT` that omits the column gets the
next value atomically — safe under concurrency because sequence increments don't
participate in transaction rollback contention the way a manually maintained "max + 1"
counter would (two concurrent transactions computing `MAX(id) + 1` can race and
collide). Modern Postgres (10+) prefers `GENERATED ALWAYS AS IDENTITY`, which is the
SQL-standard equivalent with slightly stricter semantics (you can't just override the
value by inserting it, by default) — both solve the same "give me a safe auto-
incrementing key" problem `SERIAL` was invented for.

**Q: Are `INSERT`/`UPDATE`/`DELETE` automatically committed, or can they be rolled
back?**
By default in `psql`, each statement runs in its own implicit transaction and commits
immediately (autocommit mode). But you can wrap several statements in an explicit
`BEGIN ... COMMIT`/`ROLLBACK` block (Phase 5 covers this fully) to make them atomic
together, or to rehearse a risky `UPDATE`/`DELETE` safely:
```sql
BEGIN;
DELETE FROM book WHERE genre = 'tech';
-- inspect the result, decide it looks wrong
ROLLBACK;   -- undone, as if it never happened
```
This "wrap it in BEGIN, check it, ROLLBACK if wrong" habit is one of the most useful
safety nets for ad-hoc production data fixes.
