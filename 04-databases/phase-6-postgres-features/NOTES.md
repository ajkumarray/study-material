<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · transactions](../phase-5-transactions/NOTES.md) | [Phase 7 · production ➡](../phase-7-production/NOTES.md)
<!-- /nav -->

# Phase 6 — PostgreSQL Power Features: Notes (Theory)

This phase is where Postgres goes beyond plain relational SQL — the features that
let one database cover use cases (document storage, full-text search, scheduling,
row-level multi-tenancy) that would otherwise mean reaching for a second, separate
tool. Sections 6.1–6.2 reuse the Phase 2 `author`/`book`/`customer`/`sale`
bookstore schema.

---

## 1. Views and Materialized Views

A **view** is a named, saved query that behaves like a virtual table. It stores no
data of its own — every time you `SELECT` from it, Postgres re-runs the underlying
query. A **materialized view** is a stored *snapshot* of a query's result, written
to disk, that you refresh explicitly.

### Key Concepts

- **View**: encapsulates complex joins/aggregation logic behind a simple, stable
  name; always reflects live data because it recomputes on every read. Simple views
  (over one table, no aggregation) are **updatable** (`INSERT`/`UPDATE` through
  them); views with joins or aggregates are **read-only** unless you add an
  `INSTEAD OF` trigger.
- **Security use**: a view can expose only certain columns/rows of a sensitive base
  table — grant access to the view instead of the raw table.
- **Materialized view**: stores the query's result physically, so reads are fast
  (no recompute) — but the data is **stale** until you explicitly `REFRESH` it.
- **`REFRESH MATERIALIZED VIEW ... CONCURRENTLY`**: refreshes without blocking
  concurrent readers of the old snapshot; requires a unique index on the
  materialized view first.

### Worked Examples

```sql
-- A view hiding a multi-table join behind a simple name
CREATE OR REPLACE VIEW author_revenue AS
SELECT a.id, a.name,
       COALESCE(SUM(s.quantity * b.price), 0) AS revenue,
       COUNT(s.id)                            AS num_sales
FROM author a
LEFT JOIN book b ON b.author_id = a.id
LEFT JOIN sale s ON s.book_id = b.id
GROUP BY a.id, a.name;

SELECT name, revenue FROM author_revenue WHERE revenue > 0 ORDER BY revenue DESC;
-- reads exactly like querying a normal table — the 3-table join is invisible to the caller

SELECT AVG(revenue) FROM author_revenue;   -- views compose: query a view like any table
```
In this example: `author_revenue` never actually stores a `revenue` column
anywhere — every `SELECT` against it re-executes the full `LEFT JOIN`/`GROUP BY`
underneath. That means it's always perfectly fresh, at the cost of paying the join's
full computation cost on every single read.

```sql
-- A materialized view — a stored snapshot, fast to read, stale until refreshed
CREATE MATERIALIZED VIEW author_revenue_cached AS
SELECT a.id, a.name, COALESCE(SUM(s.quantity * b.price), 0) AS revenue
FROM author a
LEFT JOIN book b ON b.author_id = a.id
LEFT JOIN sale s ON s.book_id = b.id
GROUP BY a.id, a.name;

SELECT * FROM author_revenue_cached ORDER BY revenue DESC;   -- instant — no recompute

-- New sales happen... but author_revenue_cached does NOT reflect them yet.
REFRESH MATERIALIZED VIEW author_revenue_cached;              -- recomputes and re-stores the snapshot

-- To refresh without locking out concurrent readers of the old data:
CREATE UNIQUE INDEX ON author_revenue_cached (id);
REFRESH MATERIALIZED VIEW CONCURRENTLY author_revenue_cached;
```
Plain `REFRESH MATERIALIZED VIEW` takes an exclusive lock for the duration of the
recompute, blocking reads of the view in the meantime; `CONCURRENTLY` avoids that by
computing the new result alongside the old one and swapping it in, but requires a
unique index to be able to identify matching rows between the old and new versions.

### Comparison Table

| | View | Materialized view |
|---|---|---|
| Storage | none — just a saved query | stores the result on disk |
| Freshness | always current (recomputed each read) | stale until `REFRESH` |
| Read cost | pays the full query cost every time | reads are fast (precomputed) |
| Best for | encapsulation, security, always-fresh logic | expensive aggregates/reports that tolerate some staleness |

### Why It's Useful

A view is a pure readability/security convenience with no performance trade-off of
its own (it's exactly as expensive as running the underlying query yourself). A
materialized view is the built-in version of a caching/denormalization strategy
(Phase 3.4's denormalization idea, and the caching layer covered conceptually in
System Design) — instead of an external cache, Postgres stores and manages the
snapshot for you, including safely refreshing it without downtime via
`CONCURRENTLY`.

### Summary / Key Takeaways

- A view is a saved query — no storage, always fresh, recomputed on every read.
- A materialized view stores the result — fast reads, but stale until `REFRESH`.
- Use a view to simplify or secure access to complex underlying tables; use a
  materialized view to cache an expensive aggregate/report that can tolerate some
  staleness.
- `REFRESH MATERIALIZED VIEW CONCURRENTLY` avoids blocking readers, but needs a
  unique index on the materialized view first.

---

## 2. Functions, Procedures, and Triggers (PL/pgSQL)

**PL/pgSQL** is Postgres's procedural language — it lets you run real logic (loops,
conditionals, variables) *inside* the database, rather than only declarative SQL.

### Key Concepts

- **Function**: returns a value — either a scalar or, via `RETURNS TABLE`, a whole
  result set — and executes inside the *caller's* transaction (it cannot
  independently `COMMIT`/`ROLLBACK`). Good for reusable calculations and
  parameterized queries.
- **Procedure**: invoked with `CALL` (not `SELECT`), returns nothing, and — unlike
  a function — **can manage its own transaction** (`COMMIT`/`ROLLBACK` inside it).
  Good for multi-step operations that need that transaction control.
- **Trigger**: a function that fires automatically in response to
  `INSERT`/`UPDATE`/`DELETE` on a table — declared `BEFORE` or `AFTER` the event,
  and `FOR EACH ROW` or `FOR EACH STATEMENT`. Inside a row-level trigger function,
  `NEW` is the row after the change, `OLD` is the row before it (an `INSERT`
  trigger only has `NEW`; a `DELETE` trigger only has `OLD`).
- **Trade-off**: logic living in the database is fast (no network round trip),
  central (enforced no matter which application writes), and can't be bypassed —
  but it's harder to version, unit-test, and debug than ordinary application code.

### Worked Examples

```sql
-- A scalar FUNCTION
CREATE OR REPLACE FUNCTION book_revenue(p_book_id INT)
RETURNS NUMERIC AS $$
DECLARE
    total NUMERIC;
BEGIN
    SELECT COALESCE(SUM(s.quantity * b.price), 0)
    INTO total
    FROM sale s JOIN book b ON b.id = s.book_id
    WHERE b.id = p_book_id;
    RETURN total;
END;
$$ LANGUAGE plpgsql;

SELECT title, book_revenue(id) AS revenue FROM book ORDER BY revenue DESC;
```
`book_revenue` runs as an ordinary part of the calling query's own transaction — if
the outer statement rolls back, whatever the function did (had it written data)
would roll back with it.

```sql
-- A table-returning FUNCTION — usable like a parameterized view
CREATE OR REPLACE FUNCTION books_over_price(p_min NUMERIC)
RETURNS TABLE(title TEXT, price NUMERIC) AS $$
BEGIN
    RETURN QUERY SELECT b.title, b.price FROM book b WHERE b.price > p_min ORDER BY b.price DESC;
END;
$$ LANGUAGE plpgsql;

SELECT * FROM books_over_price(40);
--        title        | price
-- ----------------------+-------
--  Refactoring          | 47.99
--  Effective Java       | 45.00
```

```sql
-- A PROCEDURE — CALLed, no return value, CAN control its own transaction
CREATE OR REPLACE PROCEDURE give_raise_to_prices(p_pct NUMERIC)
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE book SET price = price * (1 + p_pct / 100.0);
    -- a procedure could COMMIT here and keep working — a function cannot
END;
$$;

CALL give_raise_to_prices(10);   -- +10% on every book's price
```

```sql
-- A TRIGGER — auto-log every price change to an audit table
CREATE TABLE IF NOT EXISTS price_audit (
    id         SERIAL PRIMARY KEY,
    book_id    INT,
    old_price  NUMERIC,
    new_price  NUMERIC,
    changed_at TIMESTAMPTZ DEFAULT now()
);

CREATE OR REPLACE FUNCTION log_price_change()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.price <> OLD.price THEN
        INSERT INTO price_audit (book_id, old_price, new_price)
        VALUES (OLD.id, OLD.price, NEW.price);
    END IF;
    RETURN NEW;    -- for a BEFORE trigger, the row this returns is what actually gets written
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_price_audit
    BEFORE UPDATE ON book
    FOR EACH ROW
    EXECUTE FUNCTION log_price_change();

UPDATE book SET price = price + 1 WHERE title = 'Clean Code';
SELECT * FROM price_audit;
--  id | book_id | old_price | new_price |          changed_at
-- ----+---------+-----------+-----------+-------------------------------
--   1 |    1     |  38.50    |  39.50    | 2026-09-22 10:14:02.331+00
```
In this example: nothing in the `UPDATE book SET price = price + 1 ...` statement
mentions `price_audit` at all — the trigger fires automatically and transparently
underneath it, guaranteeing every price change gets logged regardless of which
application, migration, or ad-hoc script performed the update.

### Comparison Table

| | Function | Procedure | Trigger |
|---|---|---|---|
| Invoked with | `SELECT`/inline in a query | `CALL` | automatically, by an INSERT/UPDATE/DELETE |
| Returns | a value (scalar or table) | nothing | nothing (modifies `NEW`/blocks the operation) |
| Transaction control | none — runs in caller's transaction | can `COMMIT`/`ROLLBACK` internally | runs in the triggering statement's transaction |
| Typical use | reusable calculations, parameterized queries | multi-step operations, batch jobs | audit logs, derived columns, complex integrity rules |

### Why It's Useful

Triggers and functions guarantee a rule holds *at the data layer*, regardless of
which of possibly many applications, scripts, or engineers touches the table —
exactly the same motivation as constraints (Phase 3), but for logic too dynamic to
express as a static `CHECK`. The judgment call: keep core, evolving *business*
logic in the application layer (testable, versioned, easy to trace in a debugger);
reserve database functions/triggers for data-integrity, auditing, and derived-data
concerns that genuinely must hold no matter what wrote the data.

### Summary / Key Takeaways

- Functions return a value and run inside the caller's transaction; procedures are
  `CALL`ed, return nothing, and can manage their own transaction.
- Triggers fire automatically on data changes; `NEW`/`OLD` give you the row
  after/before the change inside a row-level trigger.
- Database logic is fast and universally enforced, but harder to test/version than
  application code — use it deliberately, for integrity/audit concerns, not as a
  substitute for the application's business logic layer.

---

## 3. Advanced Types — the "Multi-Model" Part of Postgres

Postgres supports several column types beyond the basics from Phase 1 that let it
cover document storage, list-valued attributes, fixed-vocabulary enums, ranges, and
search — all inside one relational engine.

### Key Concepts

- **`JSONB`**: binary-encoded JSON — schemaless, nested, and (unlike plain `JSON`)
  both **queryable** and **indexable**. `->` extracts a JSON value, `->>` extracts
  it as text, `@>` tests containment, `jsonb_set` updates a nested field in place.
  Indexable via a **GIN** index for fast `@>` containment queries.
- **Arrays**: a column can hold multiple values of the same type (`text[]`).
  `ANY(array)` tests membership, `unnest(array)` expands an array into rows,
  `array_length` gets its size.
- **Enum types**: `CREATE TYPE ... AS ENUM (...)` creates a genuine type restricted
  to a fixed set of values — stricter and more self-documenting than a `CHECK (col
  IN (...))`, since the type itself enforces it everywhere it's used.
- **Range types**: a single value representing a *span* (`daterange`, `int4range`,
  ...), with containment (`@>`) and overlap (`&&`) operators — pairs naturally with
  an `EXCLUDE` constraint to enforce "no two rows may have overlapping ranges" (a
  booking/scheduling system's core invariant).
- **Full-text search**: `to_tsvector` turns text into a normalized, stemmed
  searchable document; `to_tsquery` parses search terms the same way; `@@` matches
  the two. GIN-indexable for real search performance — genuine word-based search
  with stemming and ranking, far more capable than `LIKE '%word%'`.

### Worked Examples

```sql
CREATE TABLE product (
    id    SERIAL PRIMARY KEY,
    name  TEXT NOT NULL,
    tags  TEXT[],       -- array column
    attrs JSONB,        -- schemaless document-style column
    price NUMERIC(10,2)
);

INSERT INTO product (name, tags, attrs, price) VALUES
    ('Laptop', ARRAY['electronics','computers'],
     '{"brand":"Dell","ram_gb":16,"ports":["usb-c","hdmi"]}', 85000),
    ('Keyboard', ARRAY['electronics','accessories'],
     '{"brand":"Keychron","switches":"brown","wireless":true}', 7999);
```

```sql
-- Arrays
SELECT name, tags FROM product WHERE 'electronics' = ANY(tags);   -- membership test
SELECT name, array_length(tags, 1) AS num_tags FROM product;
SELECT name, unnest(tags) AS tag FROM product;                    -- expand array to rows
--   name    |     tag
-- -----------+---------------
--  Laptop    | electronics
--  Laptop    | computers
--  Keyboard  | electronics
--  Keyboard  | accessories
```

```sql
-- JSONB — extraction, filtering, containment, nested access, in-place update
SELECT name, attrs->>'brand' AS brand, (attrs->>'ram_gb')::int AS ram FROM product;
--   name   | brand |  ram
-- ----------+-------+------
--  Laptop   | Dell  |  16
--  Keyboard | NULL  |  NULL   (Keyboard's attrs has no ram_gb key -> NULL, not an error)

SELECT name FROM product WHERE attrs->>'brand' = 'Dell';         -- Laptop

SELECT name FROM product WHERE attrs @> '{"wireless": true}';    -- containment (Keyboard)

SELECT name FROM product WHERE attrs->'ports' ? 'hdmi';          -- key/array-element exists (Laptop)

UPDATE product SET attrs = jsonb_set(attrs, '{ram_gb}', '32') WHERE name = 'Laptop';
-- Laptop's attrs.ram_gb is now 32, everything else in the JSON untouched

CREATE INDEX idx_product_attrs ON product USING GIN (attrs);     -- makes @> containment fast
```
In this example: `attrs->>'brand'` returns `NULL` for Keyboard, not an error, since
JSONB path extraction on a missing key simply yields SQL NULL — the same "missing
means NULL" behavior as an absent column value elsewhere in Postgres. `@>`
containment (`attrs @> '{"wireless": true}'`) is what a GIN index on the column is
specifically built to accelerate.

```sql
-- Enum type — a real, restricted value set
CREATE TYPE order_status AS ENUM ('pending','paid','shipped','delivered');
SELECT 'shipped'::order_status;    -- valid
-- SELECT 'banned'::order_status;  -- ERROR: invalid input value for enum order_status
```

```sql
-- Range type — containment test
SELECT '[2026-01-01,2026-12-31]'::daterange @> '2026-06-15'::date AS in_range;
--  in_range
-- ----------
--    t
```
A booking system pairs a range column with an `EXCLUDE USING gist (room_id WITH =,
during WITH &&)` constraint to make the database itself reject any attempt to
double-book a room — no application-level "check for overlap first" race condition
possible, the same "push the invariant into the database" idea as `CHECK`/`UNIQUE`
constraints from Phase 3.

```sql
-- Full-text search
SELECT name FROM product
WHERE to_tsvector('english', name || ' ' || coalesce(attrs->>'brand',''))
      @@ to_tsquery('english', 'dell');
-- Laptop  (matches on its brand, "Dell", after stemming/normalization)
```
`to_tsvector` normalizes and stems the searchable text (so "running" and "run" would
both match a search for "run"); `to_tsquery` does the same normalization to the
search terms; `@@` is the match operator. Indexing `to_tsvector(...)` with GIN turns
this into a fast, real search feature rather than a linear `LIKE` scan.

### Comparison Table

| Type | Solves | Indexed with |
|---|---|---|
| `JSONB` | semi-structured/variable attributes | GIN (`@>` containment) |
| Array (`text[]`) | a small, fixed-shape list per row | GIN, or none for small arrays |
| Enum | a fixed, self-documenting value set | (regular B-tree, like any column) |
| Range (`daterange`, ...) | a value spanning an interval | GiST, often with `EXCLUDE` |
| Full-text (`tsvector`/`tsquery`) | real word search, stemming, ranking | GIN |

### Why It's Useful

These features cover use cases — a document store, list-valued attributes, a basic
search engine, an overlap-free scheduling system — that would otherwise mean
standing up and operating an entirely separate specialized database, purely to add
one feature. `JSONB` in particular is a direct alternative to MongoDB's whole data
model (Phase 9) for teams that want document flexibility *without* giving up ACID
transactions, joins, and a single system to operate. The practical rule of thumb:
reach for a genuinely separate specialized database only once Postgres's version of
the feature demonstrably can't keep up (extreme scale, or a truly specialized access
pattern).

### Summary / Key Takeaways

- `JSONB` gives document-database flexibility inside a relational database —
  queryable and GIN-indexable, at the cost of weaker integrity/typing than real
  columns.
- Arrays hold small, fixed-shape multi-value columns; `unnest` expands them to rows
  for further querying.
- Enum types are a stricter, self-documenting alternative to a `CHECK (col IN
  (...))` list.
- Range types plus an `EXCLUDE` constraint enforce "no overlaps" at the database
  level — ideal for booking/scheduling.
- Full-text search (`to_tsvector`/`to_tsquery`/`@@`, GIN-indexed) is a real search
  engine built into Postgres, far more capable than `LIKE '%word%'`.

---

## 4. Security — Roles, Privileges, and Row-Level Security

Postgres access control is built on **roles** (a role can represent an individual
user, a group, or both). The guiding principle throughout is **least privilege**:
an application should connect as a narrowly-scoped role, never as a superuser.

### Key Concepts

- **`GRANT`/`REVOKE`**: give or take away specific privileges (`SELECT`,
  `INSERT`, `UPDATE`, `DELETE`, ...) on specific objects, to/from specific roles.
- **`ALTER DEFAULT PRIVILEGES`**: sets privileges that will automatically apply to
  objects (e.g. tables) created *in the future*, so you don't have to re-`GRANT`
  after every new table.
- **Role membership**: `GRANT group_role TO user_role` makes a user inherit every
  privilege the group role has — a way to manage permissions in bulk rather than
  per-user.
- **Row-Level Security (RLS)**: policies that restrict *which rows* a role can
  see/modify, on top of which *tables* it can access. This is the mechanism for
  genuine multi-tenant data isolation enforced by the database itself, rather than
  trusted to every query the application happens to write.

### Worked Examples

```sql
-- Roles and privilege grants
CREATE ROLE app_readonly;                              -- a group role, no login
CREATE ROLE app_service LOGIN PASSWORD 'secret';        -- a login role for an application

GRANT CONNECT ON DATABASE current_database() TO app_service;
GRANT USAGE ON SCHEMA public TO app_readonly, app_service;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_readonly;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_service;

-- Make the SELECT grant apply to tables created LATER too, not just existing ones
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO app_readonly;

-- Role membership: app_service also gets everything app_readonly can do
GRANT app_readonly TO app_service;

REVOKE DELETE ON book FROM app_service;   -- take a specific privilege back
```
In this example: `app_service` is the role the application actually connects as —
it can read, write, and update, but was never granted superuser or DDL rights, so
even a SQL-injection vulnerability in the application couldn't be used to drop a
table or read another database on the same cluster.

```sql
-- ROW-LEVEL SECURITY — restrict which ROWS a role can see/modify
CREATE TABLE document (
    id      SERIAL PRIMARY KEY,
    owner   TEXT NOT NULL,
    content TEXT
);
INSERT INTO document (owner, content) VALUES ('ajay','A'), ('meera','B'), ('ajay','C');

ALTER TABLE document ENABLE ROW LEVEL SECURITY;

CREATE POLICY owner_can_see ON document
    USING (owner = current_setting('app.current_user', true));

CREATE POLICY owner_can_write ON document
    FOR ALL
    USING (owner = current_setting('app.current_user', true))
    WITH CHECK (owner = current_setting('app.current_user', true));

-- The application sets who the "current user" is once per session/transaction:
SET app.current_user = 'ajay';
SELECT * FROM document;
--  id | owner | content
-- ----+-------+---------
--   1 | ajay  |    A
--   3 | ajay  |    C
--  (meera's row is simply invisible — not filtered by the app, filtered by the DB)
```
In this example: `SELECT * FROM document` contains no `WHERE owner = ...` clause at
all — the row filtering is applied transparently by Postgres itself based on the
active RLS policy and the session variable `app.current_user`. Even a buggy or
malicious query issued through the `app_service` connection cannot see or modify
Meera's rows, because the restriction is enforced below the SQL layer, not by
application code remembering to add a `WHERE` clause correctly on every single
query.

### Why It's Useful

Least-privilege roles limit the blast radius of any single compromised credential
or vulnerable code path. Row-Level Security is the strongest practical guarantee
against the single most common and costly kind of multi-tenant bug — a query that
forgets its `WHERE tenant_id = ?` filter and leaks one customer's data into
another's response — because the enforcement point moves from "every engineer
remembers to add the filter, every time, forever" to "the database enforces it,
structurally, even when they forget."

### Summary / Key Takeaways

- Connect applications as least-privilege roles — never as a superuser — and
  `GRANT` only the specific privileges each role genuinely needs.
- `ALTER DEFAULT PRIVILEGES` keeps grants applying automatically to future tables.
- Row-Level Security enforces per-row (typically per-tenant) access control inside
  the database itself, so even a buggy application query can't leak another
  tenant's rows.
- Security here is one layer of a broader defense-in-depth strategy that also
  includes parameterized queries against SQL injection (Phase 1), secrets kept out
  of source code, and restricted network access to the database.
