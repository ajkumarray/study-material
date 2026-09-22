<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · multi table ➡](../phase-2-multi-table/NOTES.md)
<!-- /nav -->

# Phase 1 — Relational Foundations & SQL Basics: Notes (Theory)

All examples in this file run against the tiny bookstore schema created by `00-setup.sql`
and used again in `01-select-where-order.sql`, `02-types-and-null.sql`, and
`03-dml-insert-update-delete.sql`. It's worth keeping the shape of that schema in your
head as you read:

```sql
-- author: one row per writer
CREATE TABLE author (
    id       SERIAL PRIMARY KEY,
    name     TEXT NOT NULL,
    country  TEXT
);

-- book: one row per title, linked to an author
CREATE TABLE book (
    id         SERIAL PRIMARY KEY,
    title      TEXT NOT NULL,
    author_id  INTEGER REFERENCES author(id),
    price      NUMERIC(6,2) NOT NULL,
    genre      TEXT,
    published  DATE,
    in_stock   INTEGER NOT NULL DEFAULT 0
);
```

Right after `00-setup.sql` runs, `author` has 5 rows and `book` has 8 rows, including
one author with a `NULL` country (`Unknown Writer`) and one book with `NULL`
`author_id`, `genre`, and `published` (`An Untitled Draft`) — planted on purpose so the
NULL lessons have real data to bite on. Every worked example below quotes the exact
rows you'd see if you ran the query yourself at that point in the lesson sequence.

---

## 1. What Is an RDBMS

A **relational database** stores data as **tables** (mathematically, *relations*):
each table is a grid of **rows** (records/tuples) and **columns** (fields/attributes),
and every column has a fixed **data type**. An **RDBMS** (Relational Database
Management System — PostgreSQL, MySQL, Oracle, SQL Server) is the software that stores
those tables, enforces their rules, and answers queries against them.

### Key Concepts

- **Table (relation)**: A named collection of rows that all share the same columns
  and types — like a typed, named 2D array that lives on disk.
- **Row (record/tuple)**: One entity instance — one book, one author.
- **Column (field/attribute)**: One named, typed property shared by every row —
  `price`, `genre`, `published`.
- **Schema**: The structure itself — table names, column names/types, constraints,
  relationships — as opposed to the data inside it.
- **SQL (Structured Query Language)**: A *declarative* language — you state *what*
  result you want, not *how* to compute it. The query planner decides the "how"
  (Phase 4 covers `EXPLAIN` and how it makes that decision).
- **ACID**: The RDBMS's durability/consistency contract for transactions
  (Atomicity, Consistency, Isolation, Durability) — introduced properly in Phase 5,
  but it's the reason relational databases are trusted for money, inventory, and
  anything where "half-applied" is unacceptable.

### Worked Example

```sql
SELECT * FROM book WHERE id = 1;
--  id |   title    | author_id | price | genre |  published | in_stock
-- ----+------------+-----------+-------+-------+------------+---------
--   1 | Clean Code |         1 | 38.50 | tech  | 2008-08-01 |       12
```

This one row is a tuple: six typed attributes (`id` is `INTEGER`, `title` is `TEXT`,
`price` is `NUMERIC(6,2)`, etc.). The RDBMS guarantees every other row in `book` has
the exact same six columns with the exact same types — you can never insert a row with
a seventh column or a text value where `price` expects a number.

### Why Relational Dominates

- A clean mathematical foundation (**relational algebra** — selection, projection,
  join, union) means the query language has well-defined, provably-correct
  transformations, which is what lets query planners rewrite/optimize queries safely.
- **Declarative SQL** — you don't write a loop to find expensive books; you write
  `WHERE price > 40` and the engine picks the fastest access path (sequential scan,
  index scan, etc. — Phase 4).
- **Strong consistency** via ACID transactions — critical for anything involving
  money, inventory counts, or multi-step business operations that must all succeed or
  all fail together.
- **Flexible ad-hoc querying** — because data is normalized into small tables linked
  by keys, you can `JOIN` any two related tables together at query time in ways the
  original schema designer never had to anticipate.

The trade-off (covered more in Phase 8, scalability): a rigid, up-front schema, and
`JOIN`-heavy queries that get harder to scale horizontally across machines than
schema-less/document stores.

### Summary / Key Takeaways

- An RDBMS stores typed, structured tables and enforces rules on them; SQL is the
  declarative language for reading and changing that data.
- Tables = relations, rows = tuples, columns = attributes — know the vocabulary,
  interviewers use "relation" and "tuple" interchangeably with "table" and "row."
- Relational databases win on strong consistency (ACID) and flexible querying
  (joins); they trade off some schema flexibility and horizontal scaling ease.

---

## 2. Keys: Primary Keys and Foreign Keys

A **key** is a column (or set of columns) used to uniquely identify rows or to link
rows in one table to rows in another. Keys are how a relational database expresses
*relationships* without duplicating data.

### Key Concepts

- **Primary key (PK)**: The column (or columns) that uniquely identifies every row in
  a table. A PK is implicitly `NOT NULL` and `UNIQUE`, and a table has at most one.
  `book.id` and `author.id` are the primary keys here.
- **Surrogate key**: A PK with no business meaning — just an arbitrary auto-generated
  identifier (`SERIAL`, `BIGSERIAL`, `GENERATED ... AS IDENTITY`, or a `UUID`). Both
  `author.id` and `book.id` are surrogate keys.
- **Natural key**: A PK built from real-world data that's already unique (e.g. an
  ISBN, a national ID number, an email address). Generally **avoided** as a PK because
  "unique forever" is a hard promise for real-world data to keep (ISBNs get
  reassigned, emails change) and because natural keys are often wide/composite,
  making every foreign key referencing them wider too.
- **Foreign key (FK)**: A column in one table that stores the primary-key value of a
  row in another table, creating a link between them. `book.author_id` is a foreign
  key referencing `author.id`.
- **Referential integrity**: The guarantee the RDBMS enforces automatically once a FK
  is declared — you cannot insert a `book` row whose `author_id` doesn't exist in
  `author` (no "orphan" rows pointing nowhere), and by default you can't delete an
  `author` row that books still reference.
- **Composite key**: A key made of more than one column together (common on
  many-to-many join tables — covered in Phase 2).

### Worked Example

```sql
-- author_id is declared as a foreign key back to author(id):
CREATE TABLE book (
    id         SERIAL PRIMARY KEY,
    title      TEXT NOT NULL,
    author_id  INTEGER REFERENCES author(id),   -- FK -> author.id
    ...
);
```

```sql
-- This fails: author 999 does not exist, so referential integrity blocks it.
INSERT INTO book (title, author_id, price) VALUES ('Ghost Book', 999, 9.99);
-- ERROR:  insert or update on table "book" violates foreign key constraint
-- DETAIL: Key (author_id)=(999) is not present in table "author".
```

Because `author_id INTEGER REFERENCES author(id)` was declared when `book` was
created, PostgreSQL checks every `INSERT`/`UPDATE` on `book.author_id` against the
`author` table's primary key before allowing it. This is enforcement the application
code never has to write by hand — no "check author exists" query needed first.

Note that `book.author_id` is **nullable** — one seed row, `An Untitled Draft`, has
`author_id = NULL`. A `NULL` foreign key simply means "not yet linked to any parent
row"; it does not violate referential integrity (`NULL` never equals anything,
including a PK value — more on this in the NULL section below).

### Why It's Useful

Keys are what let you normalize data instead of repeating it: an author's name and
country live in exactly one row in `author`, and every book by that author just
stores a small integer (`author_id`) pointing at it. Update `Martin Fowler`'s country
once, and every book "by" him is automatically up to date — there's no risk of ten
different `book` rows disagreeing about their author's country because there is only
ever one copy of that fact.

### Summary / Key Takeaways

- PK = uniquely identifies a row (not null, unique, one per table); FK = a column
  that points at another table's PK and is enforced by the RDBMS (referential
  integrity).
- Prefer surrogate keys (`SERIAL`/`IDENTITY`/`UUID`) over natural keys for PKs.
- A FK column can still be `NULL` (meaning "no parent yet") unless you also add
  `NOT NULL`.

---

## 3. The SQL Sub-Languages: DDL, DML, DQL, DCL, TCL

SQL statements are conventionally grouped into five sub-languages by what kind of
thing they operate on: structure, data, queries, permissions, or transactions.

### Key Concepts

- **DDL (Data Definition Language)**: Defines/changes *structure* —
  `CREATE`, `ALTER`, `DROP`, `TRUNCATE`. Changes are typically auto-committed and hard
  to roll back casually (in PostgreSQL specifically, DDL *can* run inside a
  transaction and be rolled back — unusual among RDBMSs — but treat it as structural,
  not row-level, work).
- **DML (Data Manipulation Language)**: Changes *data* (rows) —
  `INSERT`, `UPDATE`, `DELETE`.
- **DQL (Data Query Language)**: Reads data — `SELECT`. Sometimes folded into DML in
  looser usage, but worth knowing as its own bucket since it's the one you'll use
  most.
- **DCL (Data Control Language)**: Controls *permissions* — `GRANT`, `REVOKE`.
- **TCL (Transaction Control Language)**: Controls *transaction boundaries* —
  `BEGIN`/`START TRANSACTION`, `COMMIT`, `ROLLBACK`, `SAVEPOINT`.

### Worked Example

```sql
-- DDL: define structure
CREATE TABLE author (id SERIAL PRIMARY KEY, name TEXT NOT NULL, country TEXT);

-- DML: change data
INSERT INTO author (name, country) VALUES ('Erich Gamma', 'Switzerland');
UPDATE author SET country = 'CH' WHERE name = 'Erich Gamma';
DELETE FROM author WHERE name = 'Erich Gamma';

-- DQL: read data
SELECT name, country FROM author;

-- DCL: control access (illustrative — no roles are set up in this lesson's schema)
-- GRANT SELECT ON author TO reporting_role;

-- TCL: control transaction boundaries
BEGIN;
UPDATE author SET country = 'UK' WHERE name = 'Martin Fowler';
COMMIT;
```

Each statement above belongs to exactly one bucket by what it touches: `CREATE TABLE`
touches structure (DDL), `INSERT`/`UPDATE`/`DELETE` touch rows (DML), `SELECT` reads
rows (DQL), `GRANT` touches who-can-do-what (DCL), and `BEGIN`/`COMMIT` touch when
changes become permanent (TCL).

### Why It's Useful

This taxonomy is mostly interview vocabulary — in practice you just write SQL — but
it matters for one practical reason: **DDL and DML have very different blast radii and
rollback stories**. Dropping a column (DDL) is a structural change every future query
must live with; deleting rows without a `WHERE` (DML) is data loss you can often still
`ROLLBACK` if you're inside an open transaction and haven't committed yet. Knowing
which bucket a statement is in tells you how dangerous it is to run casually.

### Summary / Key Takeaways

- DDL = structure (`CREATE`/`ALTER`/`DROP`), DML = data (`INSERT`/`UPDATE`/`DELETE`),
  DQL = reads (`SELECT`), DCL = permissions (`GRANT`/`REVOKE`), TCL = transaction
  boundaries (`COMMIT`/`ROLLBACK`).
- This is standard interview vocabulary — expect "which of these is DDL vs DML"
  as a quick warm-up question.

---

## 4. psql Meta-Commands

`psql` is PostgreSQL's official interactive terminal client. Alongside ordinary SQL,
it understands **meta-commands** — backslash-prefixed shortcuts for common
introspection tasks that aren't SQL themselves (they're client-side commands psql
translates into queries against Postgres's system catalogs).

### Key Concepts

- **`\dt`**: List tables in the current schema (here: `author`, `book`).
- **`\d book`**: Describe a table — columns, types, defaults, indexes, constraints,
  and any foreign keys pointing in or out.
- **`\d+ book`**: Like `\d` but with extra detail (storage size, description).
- **`\l`**: List all databases on the server.
- **`\c dbname`**: Connect to a different database.
- **`\du`**: List roles/users.
- **`\x`**: Toggle "expanded display" — one column per line instead of a wide table;
  invaluable for wide rows or `SELECT *` on tables with many columns.
- **`\i file.sql`**: Execute a `.sql` file (how you'd run `00-setup.sql`:
  `psql -U <user> -d <db> -f 00-setup.sql` from the shell, or `\i 00-setup.sql` from
  inside an open `psql` session).
- **`\?`**: Help — list all meta-commands.
- **`\q`**: Quit.

### Worked Example

```
$ psql -U postgres -d bookstore
bookstore=# \dt
          List of relations
 Schema |  Name  | Type  |  Owner
--------+--------+-------+----------
 public | author | table | postgres
 public | book   | table | postgres

bookstore=# \d book
                                     Table "public.book"
   Column   |     Type      | Collation | Nullable |              Default
------------+---------------+-----------+----------+------------------------------------
 id         | integer       |           | not null | nextval('book_id_seq'::regclass)
 title      | text          |           | not null |
 author_id  | integer       |           |          |
 price      | numeric(6,2)  |           | not null |
 genre      | text          |           |          |
 published  | date          |           |          |
 in_stock   | integer       |           | not null | 0
Foreign-key constraints:
    "book_author_id_fkey" FOREIGN KEY (author_id) REFERENCES author(id)
```

`\dt` confirms the two tables exist; `\d book` is the fastest way to answer "what
columns/types/constraints does this table actually have" without writing a query
against `information_schema` by hand — it shows the `NOT NULL` on `title`/`price`,
the default `0` on `in_stock`, the auto-increment default on `id`, and the foreign key
back to `author` all in one shot.

### Why It's Useful

In real work you spend a huge amount of time *exploring* an unfamiliar database before
you ever write a query against it — psql's meta-commands are how you do that
exploration fast, from the terminal, without switching to a GUI tool.

### Summary / Key Takeaways

- `\dt` = list tables, `\d table` = describe a table (columns/types/constraints/FKs),
  `\l` = list databases, `\q` = quit.
- These are client-side shortcuts, not SQL — they don't work inside application code
  or ORMs, only in the interactive `psql` shell (or things that emulate it).

---

## 5. SELECT and WHERE — Projection and Selection

`SELECT` and `WHERE` are the two most fundamental operations in SQL, and they map
directly onto the two most fundamental operations in relational algebra.

### Key Concepts

- **`SELECT`** = **projection** — choosing *which columns* appear in the result.
- **`WHERE`** = **selection** — choosing *which rows* appear in the result.
- **`SELECT *`**: All columns. Convenient at the terminal, discouraged in real code —
  it breaks if columns are added/reordered/removed and it fetches data the caller may
  not need.
- **`FROM`**: Names the table (or tables, or subquery) the query reads from.
- Combine filters with **`AND`**, **`OR`**, **`NOT`** — parenthesize explicitly when
  mixing `AND`/`OR` in one condition, since operator precedence (`AND` binds tighter
  than `OR`) is a common source of subtle bugs.

### Worked Example

```sql
-- Projection: which COLUMNS.
SELECT title, price FROM book;
--            title            | price
-- ------------------------------------
--  Clean Code                 | 38.50
--  Clean Architecture         | 32.00
--  Effective Java             | 45.00
--  Refactoring                | 47.99
--  Head First Java            | 29.99
--  Patterns of Enterprise...  | 54.00
--  A Mystery Novel            | 12.99
--  An Untitled Draft          |  9.99
-- (8 rows)

-- Selection: which ROWS.
SELECT title, price FROM book WHERE price > 40;
--            title           | price
-- -----------------------------------
--  Effective Java            | 45.00
--  Refactoring                | 47.99
--  Patterns of Enterprise...  | 54.00
-- (3 rows)
```

The first query keeps all 8 rows but drops every column except `title` and `price`
(projection). The second query keeps all columns you asked for but drops every row
where `price` isn't greater than 40 — only 3 of the 8 books qualify (selection). Note
`=` is equality in SQL, not `==` — `WHERE genre = 'tech'` reads naturally once you
remember that.

```sql
-- Combining conditions — parenthesize AND/OR mixes to be explicit:
SELECT title, price, in_stock
FROM book
WHERE genre = 'tech' AND price < 40 AND in_stock > 0;
--       title      | price | in_stock
-- --------------------------------------
--  Clean Code       | 38.50 |       12
--  Head First Java  | 29.99 |       20
-- (2 rows)
```

Of the 6 tech books, only `Clean Code` and `Head First Java` satisfy all three
conditions simultaneously (`price < 40` rules out `Effective Java`, `Refactoring`, and
`Patterns of Enterprise...`; `Clean Architecture` fails `in_stock > 0` since it's out
of stock).

### Why It's Useful

Every non-trivial query starts as "which columns do I need" plus "which rows match my
business rule" — projection and selection are the two knobs you turn constantly, and
every other SQL clause (joins, grouping, ordering) builds on top of them.

### Summary / Key Takeaways

- `SELECT` = projection (columns), `WHERE` = selection (rows) — relational algebra's
  two most basic operators, expressed directly in SQL syntax.
- Avoid `SELECT *` outside of ad-hoc exploration; name your columns in real code.
- Parenthesize mixed `AND`/`OR` conditions — don't rely on remembering precedence.

---

## 6. WHERE Operators

Beyond plain `=`/`<`/`>`, SQL gives you several purpose-built operators for common
filtering patterns: ranges, set membership, and pattern matching.

### Key Concepts

- **`BETWEEN a AND b`**: Inclusive range check — equivalent to
  `col >= a AND col <= b`.
- **`IN (v1, v2, ...)`**: Set membership — equivalent to a chain of `OR col = vN`, but
  clearer and usually faster to plan.
- **`LIKE` / `ILIKE`**: Pattern matching. `%` matches any run of characters
  (including zero), `_` matches exactly one character. `LIKE` is case-sensitive;
  **`ILIKE`** (PostgreSQL-specific) is case-insensitive.
- **`IS NULL` / `IS NOT NULL`**: The only correct way to test for `NULL` (see the
  NULL section below — `= NULL` never works).
- **Date comparison**: Dates compare with ordinary `<`/`>`/`>=`/`<=` against ISO-format
  string literals (`'2015-01-01'`), which PostgreSQL implicitly casts to `DATE`.

### Worked Example

```sql
SELECT title, price FROM book WHERE price BETWEEN 30 AND 48;
--            title           | price
-- -----------------------------------
--  Clean Code                | 38.50
--  Clean Architecture        | 32.00
--  Effective Java            | 45.00
--  Refactoring                | 47.99
-- (4 rows)
```

`BETWEEN 30 AND 48` is inclusive on both ends, so `Refactoring` at exactly `47.99`
qualifies, but `Patterns of Enterprise...` at `54.00` and `Head First Java` at
`29.99` (just under 30) do not.

```sql
SELECT title FROM book WHERE genre IN ('tech', 'fiction');
-- 7 rows: every book except "An Untitled Draft" (genre IS NULL)

SELECT title FROM book WHERE title LIKE 'Clean%';
--        title
-- --------------------
--  Clean Code
--  Clean Architecture
-- (2 rows)

SELECT title FROM book WHERE title ILIKE '%java%';
--       title
-- ------------------
--  Effective Java
--  Head First Java
-- (2 rows)
```

`IN ('tech', 'fiction')` matches every book whose genre is one of the two listed
values — note it does **not** match the `NULL`-genre row, because `NULL IN (...)`
is itself `UNKNOWN`, not `TRUE` (a NULL gotcha covered in depth below).
`LIKE 'Clean%'` anchors at the start of the string (no wildcard before `Clean`), so it
finds exactly the two titles beginning with "Clean". `ILIKE '%java%'` wraps the
pattern in wildcards on both sides (a "contains" search) and ignores case, matching
both books with "Java" in the title regardless of capitalization.

```sql
SELECT title FROM book WHERE published >= '2015-01-01';
--        title
-- --------------------
--  Clean Architecture
--  Effective Java
--  Refactoring
-- (3 rows)
```

Three books were published on or after 2015; note `An Untitled Draft` (published
`NULL`) is silently excluded — `NULL >= '2015-01-01'` is `UNKNOWN`, and `WHERE` only
keeps rows where the condition is `TRUE`.

### Why It's Useful

`BETWEEN`/`IN`/`LIKE` cover the overwhelming majority of real filtering needs
(price ranges, category lists, search-by-prefix/substring) more readably than manually
chained `OR`s — and the query planner recognizes these forms specifically, so they can
often use indexes more effectively than an equivalent hand-rolled `OR` chain
(Phase 4 covers indexing `LIKE` patterns).

### Summary / Key Takeaways

- `BETWEEN` is inclusive on both ends; `IN` is set membership; `LIKE`/`ILIKE` do
  pattern matching with `%` (any run) and `_` (one char).
- `ILIKE` is PostgreSQL-specific case-insensitive `LIKE` — most other RDBMSs need
  `LOWER(col) LIKE LOWER(pattern)` instead.
- A leading `%` (`LIKE '%code'`) can't use a plain B-tree index efficiently — it has
  to scan every value (Phase 4 covers trigram/full-text indexes as the fix).

---

## 7. Logical Query Processing Order

SQL is written in one order but **evaluated in a different order**. Understanding this
evaluation order explains several "why doesn't this work" moments that otherwise seem
arbitrary.

### Key Concepts

- **Written order**: `SELECT ... FROM ... WHERE ... GROUP BY ... HAVING ... ORDER BY
  ... LIMIT`.
- **Logical evaluation order**:
  ```
  FROM  →  WHERE  →  GROUP BY  →  HAVING  →  SELECT  →  DISTINCT  →  ORDER BY  →  LIMIT
  ```
- `FROM` (and any `JOIN`s) run first — the engine figures out the full set of rows it
  could possibly return before anything else happens.
- `WHERE` filters *individual rows*, and runs **before** `SELECT` — so aliases defined
  in `SELECT` don't exist yet when `WHERE` evaluates, and aggregates can't be used in
  `WHERE` (there are no groups yet).
- `GROUP BY`/`HAVING` (Phase 2) build and filter *groups* — `HAVING` runs after
  grouping and is where aggregate conditions belong (`HAVING count(*) > 5`).
- `SELECT` builds the final column list (projection) — this is where aliases are
  *defined*.
- `DISTINCT` dedupes the *projected* rows — after `SELECT`, so it sees only the
  columns you kept.
- `ORDER BY` runs **after** `SELECT`, so it *can* reference a `SELECT` alias.
- `LIMIT`/`OFFSET` run dead last, trimming the final sorted result.

### Worked Example

```sql
-- This FAILS: WHERE runs before SELECT, so alias "v" doesn't exist yet.
SELECT price * in_stock AS v FROM book WHERE v > 100;
-- ERROR:  column "v" does not exist

-- This WORKS: ORDER BY runs after SELECT, so alias "v" is already defined.
SELECT price * in_stock AS v FROM book ORDER BY v DESC;
--    v
-- --------
--  599.80   -- Head First Java: 29.99 * 20
--  462.00   -- Clean Code: 38.50 * 12
--  315.00   -- Effective Java: 45.00 * 7
--  143.97   -- Refactoring: 47.99 * 3
--   54.00   -- Patterns of Enterprise...: 54.00 * 1
--   49.95   -- An Untitled Draft: 9.99 * 5
--    0.00   -- Clean Architecture: 32.00 * 0
--    0.00   -- A Mystery Novel: 12.99 * 0
-- (8 rows)
```

Same expression, two different clauses, two different outcomes — purely because of
evaluation order. `WHERE v > 100` fails because at the moment `WHERE` runs, the engine
hasn't computed `SELECT`'s output list yet, so there's no column named `v` to compare
against (you'd have to repeat the full expression:
`WHERE price * in_stock > 100`). `ORDER BY v` succeeds because sorting is one of the
very last steps — by the time it runs, `v` has already been computed and named by
`SELECT`.

### WHERE vs HAVING

| | `WHERE` | `HAVING` |
|---|---|---|
| Filters | Individual rows | Groups (after `GROUP BY`) |
| Runs | Before grouping | After grouping |
| Can use aggregates? | No (`count()`, `sum()` not yet computed) | Yes — that's its purpose |
| Typical use | `WHERE price > 40` | `HAVING count(*) > 5` (Phase 2) |

### Why It's Useful

This is the single mental model that resolves the most confusing SQL error messages:
"column does not exist" in a `WHERE` that clearly has that column in `SELECT`, or
"aggregate functions are not allowed in WHERE." Once you know `WHERE` runs before
`SELECT` (and before grouping), both errors become obvious instead of mysterious.

### Summary / Key Takeaways

- Logical order: `FROM → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT`.
- A `SELECT` alias is usable in `ORDER BY` (runs after) but not in `WHERE` (runs
  before) — repeat the expression in `WHERE` instead.
- Aggregates belong in `HAVING`, not `WHERE`, because `WHERE` runs before groups even
  exist.

---

## 8. ORDER BY

`ORDER BY` sorts the final result set. Sorting is not guaranteed by default — without
an explicit `ORDER BY`, PostgreSQL may return rows in any order it finds convenient
(often insertion/storage order in simple cases, but this is **not** a guarantee you
should rely on).

### Key Concepts

- **`ASC`** (default) / **`DESC`**: Ascending or descending sort direction.
- **Multi-key sort**: `ORDER BY col1, col2 DESC` sorts by `col1` first, breaking ties
  with `col2` (descending) — each column can have its own direction.
- **`NULLS FIRST` / `NULLS LAST`**: Explicitly control where `NULL`s land. PostgreSQL's
  default (when not stated): `NULLS LAST` for `ASC`, `NULLS FIRST` for `DESC` — i.e.
  by default `NULL` sorts as if it were "larger" than every real value.
- Can sort by a `SELECT`-defined alias or computed expression (see Section 7).

### Worked Example

```sql
SELECT title, price FROM book ORDER BY price DESC;
--            title            | price
-- ------------------------------------
--  Patterns of Enterprise...  | 54.00
--  Refactoring                | 47.99
--  Effective Java             | 45.00
--  Clean Code                 | 38.50
--  Clean Architecture         | 32.00
--  Head First Java            | 29.99
--  A Mystery Novel            | 12.99
--  An Untitled Draft          |  9.99
-- (8 rows, most expensive first)

SELECT title, published FROM book ORDER BY published DESC NULLS LAST;
--            title            | published
-- -------------------------------------------
--  Refactoring                | 2018-11-30
--  Effective Java             | 2018-01-06
--  Clean Architecture         | 2017-09-20
--  A Mystery Novel            | 2010-05-05
--  Clean Code                 | 2008-08-01
--  Head First Java            | 2005-02-09
--  Patterns of Enterprise...  | 2002-11-15
--  An Untitled Draft          |            -- NULL, pushed to the end
-- (8 rows)
```

Without `NULLS LAST`, `ORDER BY published DESC` would put `An Untitled Draft` (`NULL`
published date) **first**, since PostgreSQL's default for `DESC` is `NULLS FIRST` —
that's almost never what you want for a "newest first" listing, so explicitly stating
`NULLS LAST` is a habit worth having whenever you sort a nullable column.

```sql
-- Multi-key: sort by genre first, then by price (descending) within each genre.
SELECT title, genre, price FROM book ORDER BY genre, price DESC;
--            title            |  genre  | price
-- -----------------------------------------------
--  A Mystery Novel            | fiction | 12.99
--  Patterns of Enterprise...  | tech    | 54.00
--  Refactoring                | tech    | 47.99
--  Effective Java             | tech    | 45.00
--  Clean Code                 | tech    | 38.50
--  Clean Architecture         | tech    | 32.00
--  Head First Java            | tech    | 29.99
--  An Untitled Draft          |         |  9.99   -- NULL genre sorts last (ASC default)
-- (8 rows)
```

`genre` has no explicit direction, so it defaults to `ASC` — alphabetically,
`'fiction'` sorts before `'tech'`, and `NULL` sorts after both (default `NULLS LAST`
for `ASC`). Within the `tech` group, `price DESC` breaks ties from most to least
expensive.

### Why It's Useful

Almost every user-facing list — search results, "top sellers," "newest first,"
leaderboards — is `ORDER BY` doing the real work. Getting `NULL` placement right
(`NULLS LAST` on a "most recent" sort) is the kind of detail that separates a listing
that *looks* right in a demo from one that's actually correct once real (nullable)
data shows up.

### Summary / Key Takeaways

- `ORDER BY col DESC` for descending; multiple columns sort by priority, left to
  right, each with its own direction.
- Postgres's default `NULL` placement is easy to get backwards — be explicit with
  `NULLS FIRST`/`NULLS LAST` whenever the sorted column is nullable.
- `ORDER BY` can reference `SELECT` aliases (it runs after `SELECT` — Section 7).

---

## 9. LIMIT / OFFSET — Pagination

`LIMIT` caps the number of rows returned; `OFFSET` skips a number of rows before
starting to return them. Together they implement "top N" and page-by-page pagination.

### Key Concepts

- **`LIMIT n`**: Return at most `n` rows.
- **`OFFSET m`**: Skip the first `m` rows of the (already-sorted) result before
  applying `LIMIT`.
- Always pair `LIMIT`/`OFFSET` with `ORDER BY` — without a defined sort order,
  "the first 3 rows" is not a stable, repeatable concept.
- **Offset pagination doesn't scale**: `OFFSET 100000` still has to compute and
  discard 100,000 rows before returning the 100,001st — cost grows with the page
  number. **Keyset (cursor) pagination** — `WHERE id > :last_seen_id ORDER BY id
  LIMIT n` — avoids this because it seeks directly via an index instead of skipping
  rows one by one (Phase 4 territory, but worth knowing exists even here).

### Worked Example

```sql
SELECT title, price FROM book ORDER BY price DESC LIMIT 3;
--            title            | price
-- ------------------------------------
--  Patterns of Enterprise...  | 54.00
--  Refactoring                | 47.99
--  Effective Java             | 45.00
-- (3 rows — the 3 most expensive books)

SELECT title, price FROM book ORDER BY price DESC LIMIT 3 OFFSET 3;
--       title       | price
-- ---------------------------
--  Clean Code        | 38.50
--  Clean Architecture| 32.00
--  Head First Java   | 29.99
-- (3 rows — "page 2": ranks 4-6 by price)
```

The first query is a classic "top-N" report. The second demonstrates pagination:
sorting is identical, but `OFFSET 3` skips the first three (highest-priced) rows
before `LIMIT 3` takes the next three — the definition of "page 2" when page size is
3.

### LIMIT/OFFSET vs Keyset Pagination

| | `LIMIT`/`OFFSET` | Keyset (cursor) |
|---|---|---|
| Syntax | `ORDER BY id LIMIT 20 OFFSET 40` | `WHERE id > :last_id ORDER BY id LIMIT 20` |
| Cost for late pages | Grows with offset (must skip N rows) | Constant — index seek |
| "Jump to page N" | Trivial | Hard (no direct page-number concept) |
| Stable under inserts/deletes | No — a row inserted mid-list shifts every later offset | Yes — cursor is a value, not a position |
| Typical use | Small admin UIs, "page 2 of 5" | Infinite scroll, APIs, large tables |

### Why It's Useful

Every "top 10," "show more," and paginated API response is `LIMIT`/`OFFSET` (or its
keyset cousin) under the hood. Knowing when `OFFSET` becomes a performance problem —
and that keyset pagination is the standard production fix — is a common senior-level
follow-up question.

### Summary / Key Takeaways

- `LIMIT n OFFSET m` = take `n` rows after skipping `m`, always alongside `ORDER BY`.
- Large `OFFSET`s are slow at scale — the DB still computes and discards every skipped
  row.
- Keyset/cursor pagination (`WHERE id > last_id LIMIT n`) is the production-scale
  alternative for deep pagination.

---

## 10. DISTINCT

`DISTINCT` removes duplicate rows from a result set, comparing the *entire projected
row* for equality.

### Key Concepts

- **`SELECT DISTINCT col`**: Unique values of a single column (including `NULL`,
  counted once if present).
- **`SELECT DISTINCT col1, col2`**: Unique *combinations* of both columns together —
  not independently distinct per column.
- `DISTINCT` runs after `SELECT` in the logical order (Section 7) — it dedupes on
  whatever columns you actually projected.
- `DISTINCT` requires the engine to compare/sort all rows to find duplicates — it's
  not "free," and on large tables can be a meaningful cost (Phase 4 covers execution
  plans for this).

### Worked Example

```sql
SELECT DISTINCT genre FROM book;
--  genre
-- ---------
--  tech
--  fiction
--
-- (3 rows — NULL genre counted once)

SELECT DISTINCT genre, author_id FROM book;
--  genre   | author_id
-- ---------------------
--  tech    |         1
--  tech    |         2
--  tech    |         3
--  tech    |         4
--  fiction |         5
--          |
-- (6 rows — unique (genre, author_id) PAIRS)
```

The first query has 8 books but only 3 distinct `genre` values (`'tech'`, `'fiction'`,
and `NULL` — treated as one distinct group by `DISTINCT`, unlike ordinary `=`
comparison where `NULL` never equals `NULL`). The second query shows the "combination"
behavior: both `Clean Code` and `Clean Architecture` are `('tech', 1)`, and both
`Refactoring` and `Patterns of Enterprise...` are `('tech', 3)` — those pairs collapse
to one row each, leaving 6 distinct pairs from 8 source rows.

### Why It's Useful

`DISTINCT` is the quick way to answer "what are all the unique X's in this table" —
listing all genres, all countries authors are from, all distinct customer IDs that
placed an order — without writing a `GROUP BY` (which Phase 2 shows is the more
powerful, aggregate-capable alternative for the same underlying question).

### Summary / Key Takeaways

- `DISTINCT` dedupes on the full set of projected columns, not each column
  independently.
- `NULL` is treated as one distinct value for `DISTINCT` purposes (unlike `=`, where
  `NULL = NULL` is `UNKNOWN`, not `TRUE`).
- For anything beyond "list the unique values," `GROUP BY` (Phase 2) is the more
  general tool — it also lets you aggregate (`count`, `sum`) per group.

---

## 11. Column Aliases and Computed Columns

`SELECT` isn't limited to plain column names — you can project *expressions*
(arithmetic, string concatenation, casts) and name the result with `AS`.

### Key Concepts

- **`AS`**: Names a projected column or expression. Optional syntactically
  (`price p` works too) but recommended for readability.
- **Computed column**: Any expression in the `SELECT` list — `price * in_stock`,
  `price::TEXT || ' USD'` — evaluated per row, not stored anywhere.
- Aliases are usable in `ORDER BY` (and, in PostgreSQL, `GROUP BY`/`HAVING`) because
  those clauses run after `SELECT` — but **not** in `WHERE` (Section 7).

### Worked Example

```sql
SELECT title,
       price,
       price * in_stock AS inventory_value
FROM book
WHERE in_stock > 0
ORDER BY inventory_value DESC;
--            title            | price | inventory_value
-- -------------------------------------------------------
--  Head First Java            | 29.99 |          599.80
--  Clean Code                 | 38.50 |          462.00
--  Effective Java             | 45.00 |          315.00
--  Refactoring                | 47.99 |          143.97
--  Patterns of Enterprise...  | 54.00 |           54.00
--  An Untitled Draft          |  9.99 |           49.95
-- (6 rows — Clean Architecture and A Mystery Novel excluded, in_stock = 0)
```

`price * in_stock` is computed fresh for every row and labeled `inventory_value`; it
isn't a real column in `book` — it exists only in this query's result. Because
`ORDER BY` runs after `SELECT` in the logical evaluation order, it can refer to the
alias directly instead of repeating the whole expression.

### Why It's Useful

Computed columns let you push business math (totals, discounts, formatted labels)
into the query itself instead of pulling raw numbers back and computing in
application code — useful for reports, and it means the DB (not the app) is the
single source of truth for "how do we calculate inventory value."

### Summary / Key Takeaways

- `AS` names a projected expression; the expression can be arithmetic, string
  concatenation, a cast, or a function call.
- Computed columns are per-row, per-query — not stored — and can be referenced by
  alias in `ORDER BY` but not `WHERE`.

---

## 12. PostgreSQL Data Types

Every column has a declared type, and PostgreSQL enforces it on every write. Picking
the right type up front avoids entire categories of bugs (money stored as float,
dates stored as unparsed text, etc.).

### Key Concepts

- **`INTEGER` / `BIGINT` / `SMALLINT`**: Whole numbers of increasing range.
- **`NUMERIC(p, s)` / `DECIMAL(p, s)`**: Exact fixed-point decimal — `p` = total
  significant digits, `s` = digits after the decimal point. `book.price` is
  `NUMERIC(6,2)`: up to 4 integer digits + 2 decimal digits (max `9999.99`).
- **`REAL` / `DOUBLE PRECISION`**: Approximate binary floating point — fast, but
  **not exact** for base-10 fractions (see Section 13).
- **`TEXT`**: Unlimited-length string. PostgreSQL-idiomatic default for text.
- **`VARCHAR(n)`**: Variable-length string with a hard cap of `n` characters.
- **`CHAR(n)`**: Fixed-length, space-padded to exactly `n` characters.
- **`BOOLEAN`**: `TRUE` / `FALSE` / `NULL` (a boolean column can itself be unknown).
- **`DATE`**: Calendar date, no time-of-day.
- **`TIMESTAMP`**: Date + time, no timezone.
- **`TIMESTAMPTZ`**: Date + time, timezone-aware (stored internally as UTC,
  converted on display to the session's timezone) — generally the right default for
  "when did this happen" columns.
- **`SERIAL` / `BIGSERIAL`**: Not a true type — shorthand that creates an `INTEGER`/
  `BIGINT` column with a backing sequence and a `nextval()` default, giving
  auto-increment behavior (`book.id`, `author.id`). The modern SQL-standard
  alternative is `GENERATED ALWAYS AS IDENTITY`, which behaves similarly but is
  harder to accidentally insert an explicit value into.
- **Postgres specialties** (deferred to a later phase): `UUID`, `JSONB` (indexed
  JSON), arrays (`INTEGER[]`), ranges (`INT4RANGE`), and more.
- **Casting**: `value::type` (PostgreSQL shorthand) or `CAST(value AS type)`
  (standard SQL) — both do the same thing.

### Worked Example

```sql
-- The book table's declared types, from 00-setup.sql:
--   id         SERIAL          -- INTEGER + auto-increment sequence
--   title      TEXT            -- unlimited string
--   author_id  INTEGER         -- FK to author.id
--   price      NUMERIC(6,2)    -- exact decimal, money
--   genre      TEXT
--   published  DATE
--   in_stock   INTEGER

SELECT '42'::INTEGER + 8 AS cast_shorthand;
--  cast_shorthand
-- -----------------
--               50

SELECT CAST('2020-01-01' AS DATE) AS cast_standard;
--  cast_standard
-- ---------------
--   2020-01-01

SELECT price::TEXT || ' USD' AS price_label FROM book WHERE title = 'Clean Code';
--  price_label
-- --------------
--   38.50 USD
```

`'42'::INTEGER` casts the text literal `'42'` to a real integer before adding `8`,
producing `50`. `CAST('2020-01-01' AS DATE)` is the standard-SQL spelling of the same
idea. The last example casts `price` (a `NUMERIC`) to `TEXT` so it can be concatenated
(`||`) with a literal string — you can't `||` a number directly with text in
PostgreSQL, it must be cast first.

### CHAR vs VARCHAR vs TEXT

| | `CHAR(n)` | `VARCHAR(n)` | `TEXT` |
|---|---|---|---|
| Length | Fixed — padded with spaces to exactly `n` | Variable, capped at `n` | Variable, unlimited |
| Storage in Postgres | Same underlying mechanism as the others | Same | Same |
| Performance in Postgres | Identical (no special optimization for fixed length) | Identical | Identical |
| When to use | Rarely — legacy fixed-format codes only | When the length cap is a real business rule | Default choice in PostgreSQL |

### NUMERIC / DECIMAL vs REAL / DOUBLE PRECISION

| | `NUMERIC(p,s)` | `REAL` / `DOUBLE PRECISION` |
|---|---|---|
| Representation | Exact base-10 | Approximate base-2 (IEEE 754) |
| `0.1 + 0.2` | Exactly `0.30` | Not guaranteed to be exactly `0.3` |
| Speed | Slower (software arithmetic) | Faster (hardware float arithmetic) |
| Use for money? | **Yes — always** | **Never** |
| Use for scientific/sensor data? | Overkill | Yes — appropriate |

### Why It's Useful

Choosing `NUMERIC` for `book.price` instead of `REAL` is not a style preference — it's
the difference between a query that reliably totals an invoice to the exact cent and
one that silently drifts by fractions of a cent after enough additions
(Section 13 goes deeper on why). Choosing `TIMESTAMPTZ` over `TIMESTAMP` avoids an
entire class of "meeting time is off by N hours" bugs once users span timezones.

### Summary / Key Takeaways

- `NUMERIC(p,s)` for money — exact decimal, never `REAL`/`DOUBLE PRECISION`.
- `TEXT` is the PostgreSQL-idiomatic string type; `VARCHAR(n)` only when a length cap
  is a genuine business rule; `CHAR(n)` almost never.
- Prefer `TIMESTAMPTZ` over `TIMESTAMP` for "when did this happen" columns.
- `SERIAL` is sugar for an `INTEGER` + sequence + default; `GENERATED ... AS IDENTITY`
  is the modern standard-SQL equivalent.

---

## 13. NUMERIC vs FLOAT — Why Money Needs Exact Decimals

This deserves its own callout because it's one of the most commonly asked "gotcha"
questions in SQL interviews, and it's a direct SQL analogue of the same problem in
every programming language (Java's `BigDecimal` exists for exactly this reason).

### Key Concepts

- **Binary floating point** (`REAL`/`DOUBLE PRECISION`) stores numbers as a sum of
  powers of 2. Most base-10 decimal fractions (`0.1`, `0.2`, `0.3`...) have **no exact
  binary representation**, the same way `1/3` has no exact finite decimal
  representation.
- **`NUMERIC(p,s)`** stores digits directly in base-10, so any value that fits within
  its precision/scale is represented *exactly* — no rounding error from the storage
  format itself.
- Errors from float imprecision are small per operation but **accumulate** across many
  additions/multiplications — exactly the pattern of a running total in an invoice or
  ledger.

### Worked Example

```sql
-- book.price is NUMERIC(6,2) — every value is exact:
SELECT price, price * 3 AS times_three FROM book WHERE title = 'Clean Code';
--  price | times_three
-- -------------------
--  38.50 |      115.50   -- exact, always
```

If `price` had instead been declared `REAL`, the same kind of repeated arithmetic
(summing many line items, applying a percentage discount across thousands of rows)
could accumulate visible rounding error — the classic illustration outside SQL is
that `0.1 + 0.2` in IEEE-754 double precision is `0.30000000000000004`, not exactly
`0.3`. `NUMERIC` doesn't have this problem because it never converts the decimal value
into binary fractions in the first place.

### Why It's Useful

Any column that represents currency, a count of physical units that must reconcile
exactly, or anything audited/regulated should be `NUMERIC`, never `REAL`/`DOUBLE
PRECISION`. This is precisely why `book.price` in this schema is `NUMERIC(6,2)`, not
`REAL` — an inventory system's prices need to add up to the exact cent every time.

### Summary / Key Takeaways

- Binary floats can't exactly represent most base-10 fractions; errors compound
  across repeated arithmetic.
- `NUMERIC(p,s)` is exact base-10 — always use it for money, and generally for
  anything that must reconcile to an exact value.
- This is the SQL version of "why does Java have `BigDecimal`" — same underlying
  cause (binary vs decimal representation), same fix (exact decimal type).

---

## 14. NULL and Three-Valued Logic

`NULL` represents **"unknown" or "missing"** — it is not zero, not an empty string,
and not `false`. Because of this, SQL logic isn't simple `TRUE`/`FALSE` — it's
**three-valued**: every comparison evaluates to `TRUE`, `FALSE`, or `UNKNOWN`, and
`WHERE`/`HAVING`/`JOIN ... ON` only keep rows where the condition is `TRUE`
(`UNKNOWN` rows are silently dropped, just like `FALSE` rows).

### Key Concepts

- **`NULL` = unknown/missing**, semantically distinct from `0`, `''`, or `FALSE`.
- **Any comparison involving `NULL` (`=`, `<>`, `<`, `>`, etc.) evaluates to
  `UNKNOWN`**, never `TRUE` or `FALSE` — including `NULL = NULL`.
- **`WHERE` keeps only `TRUE`** — `UNKNOWN` rows are excluded exactly like `FALSE`
  rows, which is why `x = NULL` always returns zero rows, no matter what `x` is.
- **Arithmetic with `NULL` is contagious**: `anything + NULL` is `NULL`.
- **`NOT IN` with a `NULL` in the list/subquery** silently returns **zero rows total**
  — a classic, easy-to-miss bug (explained below).

### Worked Example

```sql
-- WRONG — these both return ZERO rows, always, no matter the data:
SELECT title FROM book WHERE genre = NULL;
SELECT title FROM book WHERE genre <> NULL;
-- (0 rows in both cases — "= NULL" and "<> NULL" are UNKNOWN, never TRUE)

-- CORRECT — use IS NULL / IS NOT NULL:
SELECT title FROM book WHERE genre IS NULL;
--        title
-- --------------------
--  An Untitled Draft
-- (1 row)

SELECT name FROM author WHERE country IS NULL;
--       name
-- ----------------
--  Unknown Writer
-- (1 row)
```

`genre = NULL` is not "false," it's *unknown* — the database has no idea whether an
unknown genre equals `NULL` or not, so it can't say `TRUE`, and `WHERE` drops it.
This is true for **every** row, including rows where `genre` actually is `NULL` — so
the query returns nothing, which looks like "no NULL genres exist" even when one
clearly does. `IS NULL` is a special predicate (not a comparison operator) built
specifically to test for this case correctly.

```sql
-- Arithmetic contagion — NULL "infects" any expression it touches:
SELECT title, price + NULL AS oops FROM book;
--            title             | oops
-- -------------------------------------
--  Clean Code                  |
--  Clean Architecture          |
--  Effective Java              |
--  ...                          -- every row: oops is NULL, regardless of price
-- (8 rows, all NULL in "oops")
```

Every row's `oops` column is `NULL` — it doesn't matter that `price` has a real value
on every row, because `NULL` poisons any arithmetic expression it appears in.

### `= NULL` / `<> NULL` vs `IS NULL` / `IS NOT NULL`

| | `= NULL` / `<> NULL` | `IS NULL` / `IS NOT NULL` |
|---|---|---|
| What it is | An ordinary comparison operator | A dedicated predicate for NULL-ness |
| Result when compared value is NULL | `UNKNOWN` (dropped by `WHERE`) | `TRUE` (correctly matches) |
| Result when compared value is non-NULL | `UNKNOWN` (dropped by `WHERE`) | `FALSE` (correctly excludes) |
| Correct way to test for NULL? | **No — never matches anything** | **Yes — always use this** |

### The `NOT IN` + `NULL` Trap

```sql
-- Illustrative (not literally in this lesson's data): if a subquery/list used by
-- NOT IN contains even one NULL, the whole NOT IN returns ZERO rows.
-- SELECT title FROM book WHERE author_id NOT IN (SELECT id FROM author WHERE ...);
-- If that subquery could ever produce a NULL id, EVERY row's NOT IN becomes UNKNOWN.
```

`x NOT IN (a, b, NULL)` expands logically to `x <> a AND x <> b AND x <> NULL`. The
last term is always `UNKNOWN`, and `TRUE AND TRUE AND UNKNOWN` is `UNKNOWN` — so even
rows that clearly aren't equal to `a` or `b` still get dropped, because the `NULL`
comparison poisons the whole `AND` chain. The standard fix is **`NOT EXISTS`**, which
tests row existence rather than chaining equality comparisons and isn't vulnerable to
this.

### Why It's Useful

This is the single most common source of "my query is missing rows and I don't know
why" bugs in real SQL codebases — especially `NOT IN` against a subquery that can
return `NULL`. Recognizing the pattern (`=`/`<>`/`NOT IN` meeting a nullable column or
subquery) turns a confusing debugging session into an instant diagnosis.

### Summary / Key Takeaways

- `NULL` means unknown, not zero/empty/false — comparisons with `NULL` are
  `UNKNOWN`, and `WHERE` treats `UNKNOWN` like `FALSE` (drops the row).
- Always use `IS NULL`/`IS NOT NULL`, never `= NULL`/`<> NULL`.
- `NOT IN` with any `NULL` in its list/subquery silently returns zero rows — prefer
  `NOT EXISTS`.
- Arithmetic with `NULL` always produces `NULL`.

---

## 15. Handling NULL: COALESCE, NULLIF, IS DISTINCT FROM

SQL gives you three purpose-built tools to work *with* `NULL` instead of being
surprised by it.

### Key Concepts

- **`COALESCE(a, b, c, ...)`**: Returns the **first non-NULL** argument in the list —
  the standard way to supply a default/fallback display value.
- **`NULLIF(a, b)`**: Returns `NULL` if `a = b`, otherwise returns `a` — useful for
  turning a "sentinel" value (like `0` meaning "not set") into a real `NULL`.
- **`a IS DISTINCT FROM b`**: A **NULL-safe** version of `<>` — treats `NULL` as a
  comparable value rather than an unknown, so `NULL IS DISTINCT FROM 'tech'` is
  `TRUE` (they genuinely are different), unlike ordinary `<>` which would be
  `UNKNOWN`. The inverse is **`IS NOT DISTINCT FROM`**, a NULL-safe `=`.

### Worked Example

```sql
SELECT title, COALESCE(genre, '(unclassified)') AS genre FROM book;
--            title             |      genre
-- ------------------------------------------------
--  Clean Code                  | tech
--  Clean Architecture          | tech
--  Effective Java              | tech
--  Refactoring                  | tech
--  Head First Java             | tech
--  Patterns of Enterprise...   | tech
--  A Mystery Novel             | fiction
--  An Untitled Draft           | (unclassified)
-- (8 rows)
```

Every row where `genre` is non-`NULL` passes through unchanged; the one row where
`genre IS NULL` (`An Untitled Draft`) gets the fallback string instead. This is the
standard pattern for "display a friendly default instead of a blank."

```sql
SELECT NULLIF(in_stock, 0) AS stock_or_null FROM book;
--  stock_or_null
-- ---------------
--             12
--                -- Clean Architecture: in_stock was 0, NULLIF turns it into NULL
--              7
--              3
--             20
--              1
--                -- A Mystery Novel: in_stock was 0 -> NULL
--              5
-- (8 rows)
```

`NULLIF(in_stock, 0)` compares each `in_stock` value to `0`; where they're equal, it
returns `NULL` instead of `0` — handy when "0" and "not applicable" need to be told
apart downstream (e.g. to avoid a `0` skewing an `AVG()`, since `AVG` ignores `NULL`s
but would include a real `0`).

```sql
SELECT title FROM book WHERE genre IS DISTINCT FROM 'tech';
--        title
-- --------------------
--  A Mystery Novel
--  An Untitled Draft
-- (2 rows — includes the NULL-genre row)
```

Compare this to `WHERE genre <> 'tech'`, which would return only `A Mystery Novel` —
the `NULL`-genre row would be silently dropped because `NULL <> 'tech'` is `UNKNOWN`.
`IS DISTINCT FROM` treats `NULL` as a real, comparable value ("genre is not set,
which is indeed different from `'tech'`"), so it correctly includes that row too.

### Why It's Useful

`COALESCE` is everywhere real applications display data ("show 'N/A' instead of a
blank"). `NULLIF` is the standard trick to avoid divide-by-zero errors:
`NULLIF(divisor, 0)` turns a `0` divisor into `NULL`, and dividing by `NULL` yields
`NULL` (no data) instead of raising a runtime "division by zero" error. `IS DISTINCT
FROM` matters anywhere you need "genuinely different, including when one side is
NULL" — a common need in deduplication logic and change-detection queries ("did any
column actually change, treating NULL-to-NULL as no change").

### Summary / Key Takeaways

- `COALESCE(a, b, ...)` = first non-NULL value — the default/fallback tool.
- `NULLIF(a, b)` = `NULL` if equal, else `a` — commonly paired with division to avoid
  divide-by-zero.
- `IS DISTINCT FROM` / `IS NOT DISTINCT FROM` = NULL-safe `<>`/`=` — use when `NULL`
  should be treated as a comparable value, not an automatic `UNKNOWN`.

---

## 16. Aggregates and NULL

Aggregate functions (`count`, `sum`, `avg`, `min`, `max` — covered fully with
`GROUP BY` in Phase 2) have a specific, consistent rule for how they treat `NULL`
that's worth learning now, since it trips people up constantly.

### Key Concepts

- **Aggregate functions skip `NULL` values** in the column they're aggregating —
  with one specific exception: **`count(*)`** counts *rows*, not a column's values,
  so it's unaffected by `NULL`s in any column.
- **`count(column)`** counts only rows where that specific column is non-`NULL`.
- **`count(DISTINCT column)`** counts distinct non-`NULL` values of that column.
- This "skip NULLs" rule also applies to `sum`, `avg`, `min`, `max` — e.g. `avg(col)`
  divides by the number of *non-NULL* rows, not the total row count.

### Worked Example

```sql
SELECT count(*)             AS all_rows,
       count(genre)         AS rows_with_genre,
       count(DISTINCT genre) AS distinct_genres
FROM book;
--  all_rows | rows_with_genre | distinct_genres
-- --------------------------------------------
--         8 |               7 |                2
```

`count(*)` = 8 — every row in `book`, `NULL`s or not. `count(genre)` = 7 — one book
(`An Untitled Draft`) has `genre IS NULL`, and that row is skipped, not counted.
`count(DISTINCT genre)` = 2 — only `'tech'` and `'fiction'` are distinct non-`NULL`
values; the `NULL` itself is never counted as a "distinct value" by `count`.

### Why It's Useful

This is a near-guaranteed interview question because the three forms look almost
identical but answer three different real questions: "how many rows total," "how many
rows actually have this field filled in," and "how many unique values does this field
take." Mixing them up produces subtly wrong dashboards (e.g. reporting "7 genres" when
you meant "7 books have *a* genre").

### Summary / Key Takeaways

- `count(*)` = row count, immune to `NULL`s. `count(col)` = non-`NULL` values in
  `col`. `count(DISTINCT col)` = distinct non-`NULL` values.
- All aggregates (`sum`/`avg`/`min`/`max`) skip `NULL`s in the aggregated column —
  they never treat `NULL` as `0`.

---

## 17. Type Casting

PostgreSQL supports two equivalent syntaxes for converting a value from one type to
another: the `::` shorthand and the standard-SQL `CAST(... AS ...)`.

### Key Concepts

- **`value::type`**: PostgreSQL-specific shorthand.
- **`CAST(value AS type)`**: Standard SQL, portable to other RDBMSs.
- Casts can fail at runtime if the value genuinely can't convert (e.g.
  `'abc'::INTEGER` raises an error) — casting is not the same as silent
  reinterpretation.
- String concatenation (`||`) requires both sides to be strings — numeric/date values
  must be cast to `TEXT` first.

### Worked Example

```sql
SELECT '42'::INTEGER + 8          AS cast_shorthand;   -- 50
SELECT CAST('2020-01-01' AS DATE) AS cast_standard;     -- 2020-01-01
SELECT price::TEXT || ' USD'      AS price_label FROM book;
--  price_label
-- --------------
--   38.50 USD
--   32.00 USD
--   45.00 USD
--   ...
-- (8 rows)
```

Both cast syntaxes produce identical results — `::` is just terser and Postgres-only.
The third example is a common real pattern: `price` is `NUMERIC`, and `||` (string
concatenation) requires `TEXT` on both sides, so `price::TEXT` converts it before
concatenating with the literal `' USD'`.

### Why It's Useful

Casting shows up constantly: converting form input (always strings) into the right
column type before comparison, building formatted display strings from numeric/date
columns, and normalizing types across a `UNION` of differently-typed columns.

### Summary / Key Takeaways

- `value::type` and `CAST(value AS type)` are equivalent; `::` is Postgres-only
  shorthand.
- Casts can fail at runtime on genuinely invalid conversions — they're not silent.
- `||` requires both operands to already be text — cast non-text values first.

---

## 18. INSERT

`INSERT` adds new rows to a table.

### Key Concepts

- **`INSERT INTO table (col1, col2, ...) VALUES (v1, v2, ...)`**: Single-row insert.
  Always name the columns explicitly (don't rely on positional order matching the
  table's physical column order — that's fragile if the table is ever altered).
- **Multi-row insert**: `VALUES (...), (...), (...)` in one statement — a single
  round trip to the database, far faster than N separate `INSERT` statements when
  loading multiple rows.
- **`RETURNING col, ...`**: Hands back values from the just-inserted row(s) — most
  commonly the auto-generated `SERIAL`/`IDENTITY` id — without a second query. This is
  the SQL-level equivalent of what JDBC's `getGeneratedKeys()` does under the hood.
- Columns not listed in an `INSERT` get their declared `DEFAULT` (or `NULL` if no
  default and the column is nullable) — e.g. omitting `in_stock` would give `0`
  (its declared default in this schema).

### Worked Example

```sql
INSERT INTO author (name, country) VALUES ('Erich Gamma', 'Switzerland');
-- INSERT 0 1   -- one row inserted; id auto-assigned by the SERIAL sequence

INSERT INTO book (title, author_id, price, genre, published, in_stock) VALUES
    ('Design Patterns', 6, 49.99, 'tech', '1994-10-31', 4),
    ('Test Driven Dev', 3, 39.99, 'tech', '2002-11-08', 6);
-- INSERT 0 2   -- both rows inserted in one statement/round trip

INSERT INTO author (name, country)
VALUES ('New Author', 'India')
RETURNING id, name;
--  id |    name
-- --------------------
--   7 | New Author
-- (1 row)
```

The first statement adds one author (`Erich Gamma`, id auto-assigned as `6` — the
next value from `author`'s backing sequence, since 5 authors already existed). The
second adds two books in a single statement — `Design Patterns` references
`author_id = 6` (the row just created), demonstrating that FK references can point at
rows inserted earlier in the same session. The third demonstrates `RETURNING`: instead
of inserting and then running a separate `SELECT ... WHERE name = 'New Author'` to
find the generated `id`, the `INSERT` statement itself hands back `id = 7` and
`name` directly.

### Why It's Useful

`RETURNING` eliminates an entire round trip (and a possible race condition — what if
two authors both named the same thing get inserted concurrently, and your follow-up
`SELECT` picks up the wrong one?) that would otherwise be needed just to learn what
primary key the database assigned. Multi-row `INSERT` is the standard way to bulk-load
seed/import data efficiently instead of one statement per row.

### Summary / Key Takeaways

- Always name columns explicitly in `INSERT` — never rely on positional/table order.
- Multi-row `VALUES (...), (...)` is one round trip — much faster than N single-row
  inserts for bulk loads.
- `RETURNING` gets generated values (like a `SERIAL` id) back in the same statement,
  no follow-up `SELECT` needed.

---

## 19. UPDATE

`UPDATE` modifies existing rows in place.

### Key Concepts

- **`UPDATE table SET col = expr, ... WHERE condition`**: Changes matching rows.
  `SET` can reference the row's own current values (`price = price * 1.10`).
- **`WHERE` is not optional in practice** — omit it and *every* row in the table gets
  updated. There is no implicit "did you mean just this row?" safety net.
- **Safety habit**: Write the `WHERE` clause as a `SELECT` first to preview exactly
  which rows match, then change `SELECT ... FROM` to `UPDATE ... SET`.
- `UPDATE` can set multiple columns in one statement, comma-separated in `SET`.
- `RETURNING` works on `UPDATE` too — confirms exactly which rows changed and to what
  values, in the same statement.

### Worked Example

Continuing from the state after Section 18's inserts (8 original books + `Design
Patterns` + `Test Driven Dev` = 10 books, all 8 non-fiction/non-NULL-genre books are
`'tech'`):

```sql
UPDATE book SET price = price * 1.10 WHERE genre = 'tech';
-- UPDATE 8   -- 8 tech books affected: the original 6 tech titles plus the 2 just inserted
```

```sql
UPDATE book SET in_stock = in_stock + 5 WHERE title = 'Clean Code';
-- UPDATE 1
SELECT title, in_stock FROM book WHERE title = 'Clean Code';
--    title    | in_stock
-- -----------------------
--  Clean Code |       17   -- was 12, +5
```

```sql
UPDATE book
SET price = 19.99, genre = 'fiction'
WHERE title = 'An Untitled Draft'
RETURNING id, title, price, genre;
--  id |       title        | price | genre
-- ----------------------------------------
--   8 | An Untitled Draft   | 19.99 | fiction
-- (1 row)
```

The first statement gives every `'tech'`-genre book a 10% price increase in one pass —
`price = price * 1.10` reads the row's *current* price on the right-hand side before
writing the new value, so this is safe to run even though `price` appears on both
sides. The second updates a single row identified by `title`, growing `in_stock` from
`12` to `17`. The third sets two columns at once and uses `RETURNING` to confirm, in
the same statement, exactly what the row looks like after the change — no separate
`SELECT` needed to verify the update worked.

### Why It's Useful

`UPDATE ... WHERE` is how virtually all state changes happen in a running application
— adjusting inventory, changing a price, flipping a status flag. The
`SELECT`-first-then-convert-to-`UPDATE` habit, plus `RETURNING` to confirm the result,
is the practical discipline that prevents "I meant to update one row and updated the
whole table" incidents — one of the most common real-world production accidents.

### Summary / Key Takeaways

- `UPDATE` without `WHERE` changes every row — always write and verify the `WHERE`
  first, ideally by running it as a `SELECT` first.
- `SET col = col + n` safely reads the current value before writing the new one.
- `RETURNING` confirms exactly what changed, in the same round trip.

---

## 20. DELETE

`DELETE` removes rows from a table.

### Key Concepts

- **`DELETE FROM table WHERE condition`**: Removes matching rows. Just like
  `UPDATE`, omitting `WHERE` empties the *entire* table.
- `DELETE` is row-by-row and fully transactional/logged — it fires triggers, updates
  indexes incrementally, and can be `ROLLBACK`'d if still inside an open transaction.
- `RETURNING` works on `DELETE` too — shows exactly what was removed, useful for
  confirming/auditing a destructive operation.

### Worked Example

Continuing the running example — after Section 19's updates, `An Untitled Draft` now
has `genre = 'fiction'` but `in_stock = 5` (unchanged), while the original
`A Mystery Novel` is still `genre = 'fiction'` with `in_stock = 0`:

```sql
DELETE FROM book WHERE in_stock = 0 AND genre = 'fiction';
-- DELETE 1
-- Only "A Mystery Novel" matches both conditions (in_stock = 0 AND genre = 'fiction').
-- "An Untitled Draft" is also genre = 'fiction' now, but in_stock = 5, so it survives.
```

```sql
DELETE FROM book WHERE published < '2006-01-01' RETURNING title;
--          title
-- --------------------------
--  Head First Java
--  Patterns of Enterprise...
--  Design Patterns
--  Test Driven Dev
-- (4 rows deleted — "An Untitled Draft", published = NULL, is NOT matched:
--  NULL < '2006-01-01' is UNKNOWN, so WHERE excludes it, same rule as Section 14)
```

The first `DELETE` demonstrates that a compound `WHERE` only removes rows matching
*all* conditions — `An Untitled Draft` shares the `genre = 'fiction'` condition but
survives because its `in_stock` isn't `0`. The second shows `RETURNING` on a `DELETE`:
it lists exactly which titles were removed in the same statement, and it also
reinforces the NULL rule from Section 14 — `An Untitled Draft`'s `NULL` `published`
date means the `<` comparison is `UNKNOWN`, so that row is left alone even though it's
clearly "old" in the sense of "we don't know when it was published."

### DELETE vs TRUNCATE vs DROP

| | `DELETE` | `TRUNCATE` | `DROP` |
|---|---|---|---|
| Removes | Matching rows (or all, if no `WHERE`) | All rows | The table itself (structure + data) |
| Can filter with `WHERE`? | Yes | No | N/A |
| Logging | Row-by-row (slower on huge deletes) | Minimal — deallocates pages directly | N/A |
| Resets `SERIAL`/identity sequence? | No | Yes (by default) | N/A (table is gone) |
| Fires row-level triggers? | Yes | No (fires statement-level triggers only) | N/A |
| Transactional/rollback-able? | Yes | Yes (in PostgreSQL) | Yes (in PostgreSQL) |
| Use when | Removing specific rows | Emptying a table completely, fast | Removing the table entirely |

### Why It's Useful

`DELETE ... WHERE` combined with `RETURNING` is the safe, auditable way to remove data
in production — you see exactly what left. Knowing `TRUNCATE` exists (and that it
resets identity sequences and skips row-level triggers) matters for "reset a table
between test runs" scenarios, where `DELETE FROM table` (no `WHERE`) would work but be
needlessly slower on a large table.

### Summary / Key Takeaways

- `DELETE FROM table WHERE ...` removes matching rows; no `WHERE` empties the table.
- `RETURNING` on `DELETE` shows exactly what was removed — good practice for anything
  destructive.
- `TRUNCATE` empties a table fast with minimal logging and resets the identity
  sequence, but can't be filtered with `WHERE`; `DROP` removes the table's structure
  entirely.

---

## 21. TRUNCATE

`TRUNCATE` is a fast, unfiltered way to empty an entire table.

### Key Concepts

- **`TRUNCATE table`**: Removes every row immediately, without scanning/deleting
  row-by-row — it deallocates the table's data pages directly, which is why it's much
  faster than `DELETE` with no `WHERE` on a large table.
- Cannot take a `WHERE` clause — it's all-or-nothing by design.
- Resets any `SERIAL`/identity sequence back to its starting value by default (so the
  next inserted row gets id `1` again), unless `TRUNCATE ... CONTINUE IDENTITY` is
  used to keep the sequence where it was.
- Behaves more like DDL than DML in terms of cost profile, even though it's
  data-focused — in PostgreSQL specifically it's still transactional and can be
  rolled back if issued inside an open transaction that hasn't committed.

### Worked Example

```sql
-- Illustrative — not actually run against this lesson's live data, since it would
-- empty the whole table. Shown here exactly as it appears (commented out) in
-- 03-dml-insert-update-delete.sql:
-- TRUNCATE book;
```

`TRUNCATE book` would remove all remaining rows from `book` in one fast operation and
reset `book_id_seq` (the backing sequence for `book.id`) back to `1` — so the very
next `INSERT` would get `id = 1` again, even though rows with earlier ids previously
existed. This is exactly why it's left commented out in the lesson file: running it
would wipe the working example data.

### Why It's Useful

`TRUNCATE` is the standard tool for resetting a table between test runs, clearing a
staging/temp table before a bulk reload, or wiping a table in a data pipeline —
anywhere "empty this whole table, fast, and I don't need row-level filtering or
triggers" is the actual requirement.

### Summary / Key Takeaways

- `TRUNCATE table` empties a whole table fast, with minimal logging — no `WHERE`
  clause possible.
- It resets the identity/`SERIAL` sequence by default (`RESTART IDENTITY` is actually
  the default behavior for `TRUNCATE` in most setups — check `CONTINUE IDENTITY` if
  you want to keep the sequence's current position).
- It's transactional in PostgreSQL (rollback-able before `COMMIT`), unlike in some
  other RDBMSs where it isn't.

---

## 22. Upsert: INSERT ... ON CONFLICT

An **upsert** ("update or insert") is a single statement that inserts a new row, or —
if a conflicting unique/primary key already exists — updates the existing row instead.
PostgreSQL implements this with `INSERT ... ON CONFLICT`.

### Key Concepts

- **Requires a `UNIQUE` or `PRIMARY KEY` constraint** on the conflict target column(s)
  — that's what the database uses to detect "this row already exists."
- **`ON CONFLICT (col) DO UPDATE SET ...`**: On conflict, update the existing row
  instead of failing.
- **`ON CONFLICT (col) DO NOTHING`**: On conflict, silently skip the insert (leave the
  existing row untouched).
- **`EXCLUDED`**: A special pseudo-table name inside the `DO UPDATE SET` clause,
  referring to the row values that *would have been* inserted — lets you write
  `SET price = EXCLUDED.price` to mean "use the new value I tried to insert."

### Worked Example

```sql
-- Illustrative — this schema does NOT currently have a UNIQUE constraint on
-- book.title, so this exact statement is not runnable as-is against 00-setup.sql.
-- It shows the pattern assuming such a constraint existed:
--
-- INSERT INTO book (title, author_id, price)
-- VALUES ('Clean Code', 1, 41.00)
-- ON CONFLICT (title) DO UPDATE SET price = EXCLUDED.price;
--
-- If book.title were UNIQUE and a "Clean Code" row already existed, this would
-- update that existing row's price to 41.00 instead of inserting a duplicate row
-- and erroring out on the uniqueness violation.
```

Without a unique constraint to detect the conflict, `ON CONFLICT (title)` has nothing
to key off — PostgreSQL needs a real constraint (or unique index) on exactly the
column(s) named in `ON CONFLICT` to know what counts as "the same row." That's why
this pattern is shown as illustrative rather than run directly against the lesson's
schema: `book.title` is not declared `UNIQUE` here (two books could legitimately share
a title in this schema as written).

### Why It's Useful

Upsert is the standard pattern for "save" operations where the caller doesn't know
(or doesn't care) whether the row already exists — importing/syncing external data,
caching computed values keyed by some unique identifier, or idempotent retries of a
write (if the client retries an insert after a timeout, an upsert means the retry is
safe instead of erroring on a duplicate key or silently double-inserting).

### Summary / Key Takeaways

- Upsert = insert-or-update in one statement: `INSERT ... ON CONFLICT (key) DO
  UPDATE SET ... `.
- It requires an actual `UNIQUE`/`PRIMARY KEY` constraint on the conflict-target
  column(s) — there's no upsert without something to detect the conflict against.
- `EXCLUDED.col` refers to the value that was about to be inserted, for use inside
  `DO UPDATE SET`.
- `ON CONFLICT (key) DO NOTHING` is the "insert if new, otherwise ignore" variant.

---

## 23. Transactions Basics

A **transaction** groups one or more statements into a single all-or-nothing unit —
either every statement in it takes effect, or none do. Full ACID coverage (isolation
levels, concurrency anomalies, locking) is Phase 5 territory, but the basic
`BEGIN`/`COMMIT`/`ROLLBACK` mechanics matter now because every DML statement you run
is implicitly wrapped in one.

### Key Concepts

- **Autocommit (default)**: Outside an explicit `BEGIN`, every individual statement
  in `psql` is its own transaction — it commits immediately when it succeeds.
- **`BEGIN` (or `START TRANSACTION`)**: Opens an explicit transaction — subsequent
  statements are grouped together and nothing is permanent yet.
- **`COMMIT`**: Makes every change since `BEGIN` permanent and visible to other
  connections.
- **`ROLLBACK`**: Discards every change since `BEGIN` as if it never happened.
- In PostgreSQL specifically, **DDL is transactional too** (unlike many other
  RDBMSs) — you can `BEGIN`, `CREATE TABLE`, and `ROLLBACK` to undo it, which is
  unusually powerful for safely rehearsing schema changes.

### Worked Example

```sql
BEGIN;

DELETE FROM book WHERE genre = 'fiction';
-- DELETE 1   -- looks right so far...

SELECT count(*) FROM book WHERE genre = 'fiction';
--  count
-- -------
--      0

ROLLBACK;
-- ROLLBACK   -- undo everything since BEGIN

SELECT count(*) FROM book WHERE genre = 'fiction';
--  count
-- -------
--      1    -- back to how it was before BEGIN
```

Wrapping the risky `DELETE` in `BEGIN ... ROLLBACK` lets you inspect the result
(`count(*)` showing `0` fiction books) before deciding whether to keep it. Since
`ROLLBACK` was issued instead of `COMMIT`, the delete is undone entirely — the second
`count(*)` proves the fiction row is back, as if the `DELETE` never ran. Had `COMMIT`
been issued instead of `ROLLBACK`, the change would be permanent.

### Why It's Useful

This is the safety net behind the "rehearse risky changes" habit mentioned throughout
the DML sections above: wrap an `UPDATE`/`DELETE` you're not 100% sure about in
`BEGIN; ...; `, inspect the result with a `SELECT`, and only `COMMIT` once you're
confident — `ROLLBACK` if anything looks wrong. It's also the foundation every later,
more advanced transaction topic (isolation levels, deadlocks, optimistic locking —
Phase 5) builds on.

### Summary / Key Takeaways

- Every statement outside an explicit `BEGIN` autocommits immediately in `psql`.
- `BEGIN ... COMMIT` groups statements into one all-or-nothing unit; `ROLLBACK`
  discards everything since `BEGIN`.
- PostgreSQL allows rolling back DDL too, not just DML — useful for safely
  rehearsing schema changes.
- Practical habit: wrap an uncertain `UPDATE`/`DELETE` in `BEGIN; ...; ROLLBACK;`
  first to preview its effect risk-free, then re-run with `COMMIT` once verified.
