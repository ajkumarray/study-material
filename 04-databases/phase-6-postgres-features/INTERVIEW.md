<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · transactions](../phase-5-transactions/NOTES.md) | [Phase 7 · production ➡](../phase-7-production/NOTES.md)
<!-- /nav -->

# Phase 6 — PostgreSQL Power Features: Interview Q&A

⭐ = asked constantly.

**Q: View vs materialized view — what's the difference, and when would you use
each?** ⭐⭐
A view is a saved query with no storage of its own — every `SELECT` against it
re-runs the underlying query, so it's always perfectly current, at the cost of
paying that query's full cost on every single read. A materialized view stores the
query's *result* physically on disk — reads are fast because there's nothing left
to compute, but the data is frozen as of the last `REFRESH MATERIALIZED VIEW` and
goes stale as soon as the underlying tables change. Use a plain view to encapsulate
a complex join/aggregation behind a simple, stable name, or to expose a restricted
subset of a table for security. Use a materialized view for an expensive
aggregate/report that's read often, written rarely, and can tolerate being a few
minutes (or hours) out of date — a dashboard summary, for instance, rather than a
live account balance.

**Q: Why use a view at all, if it doesn't actually improve performance over the raw
query?**
Two reasons that have nothing to do with performance. First, encapsulation: a view
hides a complex multi-table join/aggregate behind a simple name, so callers write
`SELECT * FROM author_revenue` instead of repeating (and risking subtly diverging
copies of) a five-line join everywhere it's needed — and if the underlying schema
changes, you can often update just the view's definition rather than every caller.
Second, security: you can `GRANT` a role access to a view that exposes only certain
columns or rows of a sensitive base table, without ever granting direct access to
the base table itself.

**Q: How do you refresh a materialized view without blocking readers?**
Plain `REFRESH MATERIALIZED VIEW name` takes an exclusive lock for the duration of
the recompute, so any query trying to read the view blocks until the refresh
finishes. `REFRESH MATERIALIZED VIEW CONCURRENTLY name` avoids that — it computes
the new result set in the background and atomically swaps it in, so concurrent
readers keep seeing the old (slightly stale) snapshot right up until the swap.
`CONCURRENTLY` has one prerequisite: the materialized view needs a `UNIQUE` index
(`CREATE UNIQUE INDEX ON name (id)`), which Postgres uses to match up old and new
rows during the swap.

**Q: Function vs stored procedure in Postgres — what's the actual difference?**
A **function** returns a value (a scalar, or a whole result set via `RETURNS
TABLE`) and always executes inside the *caller's* transaction — it cannot issue its
own `COMMIT` or `ROLLBACK`. A **procedure** is invoked with `CALL` rather than
`SELECT`, returns nothing, and — unlike a function — **can** manage its own
transaction boundaries internally. Functions fit reusable calculations and
parameterized queries; procedures fit multi-step operational tasks that genuinely
need transaction control of their own, like a batch job that commits progress after
each chunk rather than holding one giant transaction open for the whole run.

**Q: What is a trigger, and when would you reach for one?** ⭐
A trigger is a function that fires automatically whenever a specified data-changing
event (`INSERT`/`UPDATE`/`DELETE`) happens on a table — declared to run `BEFORE` or
`AFTER` that event, and either `FOR EACH ROW` or `FOR EACH STATEMENT`. Inside a
row-level trigger function, `NEW` refers to the row's state after the change and
`OLD` to its state before (an `INSERT` trigger only has `NEW`, a `DELETE` trigger
only has `OLD`). Typical uses: audit logging (write an old/new value pair to a
history table whenever a sensitive column changes), keeping a derived or
denormalized column in sync automatically, and enforcing integrity rules too
dynamic to express as a static `CHECK` constraint.
```sql
CREATE TRIGGER trg_price_audit
    BEFORE UPDATE ON book
    FOR EACH ROW
    EXECUTE FUNCTION log_price_change();
```
For a `BEFORE` trigger, the row the trigger function `RETURN`s is what actually
gets written — this is also how a `BEFORE` trigger can veto or modify a write
before it happens, not just react to it afterward.

**Q: Should business logic live in the database (functions/triggers) or in the
application layer?** *judgment question*
Data-integrity rules, audit trails, and invariants that must hold no matter which
of possibly many applications or scripts touches the table belong in the database —
that's exactly the same reasoning behind putting constraints (Phase 3) in the
database rather than only in application code. Core, evolving *business* logic
belongs in the application layer, where it's unit-testable, versionable alongside
the rest of the codebase, and far easier to step through in a debugger than
PL/pgSQL running inside the database. Overusing triggers/stored procedures for
business logic creates hidden, hard-to-trace behavior (an engineer reading the
application code has no way to know an `UPDATE` also silently cascades into three
other tables via triggers); underusing database-level integrity enforcement leaves
bad data reachable by any writer that skips the application's validation path. The
practical line: database code for integrity/audit concerns that must hold
regardless of the writer; application code for everything that's actually business
logic.

**Q: What is JSONB, and when would you use it?** ⭐⭐
`JSONB` is Postgres's binary-encoded JSON column type — schemaless, arbitrarily
nested, and unlike plain `JSON`, both queryable (`->`, `->>`, `@>`, path operators)
and indexable (a GIN index accelerates `@>` containment lookups). Use it for
attributes that are genuinely semi-structured or vary per row — product
specifications that differ wildly by product category, a flexible "settings"
column, third-party webhook payloads you need to store as-received. The trade-off
is real: `JSONB` fields carry none of the type-checking, `NOT NULL`, or foreign-key
integrity that real columns get, so it should be a deliberate choice for genuinely
variable data, not a way to avoid modeling a schema you actually understand.
```sql
SELECT name FROM product WHERE attrs @> '{"wireless": true}';   -- containment, GIN-indexable
UPDATE product SET attrs = jsonb_set(attrs, '{ram_gb}', '32') WHERE name = 'Laptop';
```

**Q: `JSONB` vs a separate document database like MongoDB — when would you pick
one over the other?**
`JSONB` gives you document-style flexibility for the specific columns that need it,
while keeping everything else about a relational database: full ACID transactions,
real joins to your other properly-typed tables, and a single system to operate,
back up, and monitor. A dedicated document database wins once the *entire* data
model is naturally document-shaped, or once you need horizontal sharding/scale
characteristics a single Postgres instance genuinely can't provide, or specialized
document-store query patterns that `JSONB` doesn't support as richly (Phase 9 goes
into MongoDB's aggregation pipeline and indexing model in depth). In practice, many
teams start with Postgres and `JSONB` for the flexible parts of their schema and
only introduce a second, separate document database once they've hit a concrete
limitation — not preemptively.

**Q: How does full-text search work in Postgres, and why is it better than `LIKE
'%word%'`?**
`to_tsvector('english', text)` normalizes and stems a body of text into a
searchable "document" representation (so "running," "runs," and "ran" all reduce to
a common stem). `to_tsquery('english', terms)` does the same normalization to the
search terms. The `@@` operator matches the two. Indexed with GIN on the
`to_tsvector(...)` expression, this supports real search features — stemming,
relevance ranking, phrase search — that a `LIKE '%word%'` predicate has none of;
`LIKE` with a leading wildcard also can't use a normal B-tree index at all (Phase 4)
and forces a full scan, whereas GIN-indexed full-text search stays fast at scale.
For many applications this built-in capability is enough to avoid standing up a
separate search engine like Elasticsearch entirely.

**Q: What is Row-Level Security, and why does it matter for a multi-tenant
application?** ⭐
Row-Level Security (RLS) lets you attach policies to a table that restrict *which
rows* a given role can see or modify — filtering below the level of individual
tables, which is all `GRANT`/`REVOKE` alone can control. Once enabled with `ALTER
TABLE ... ENABLE ROW LEVEL SECURITY` and a policy like `USING (owner =
current_setting('app.current_user', true))`, an ordinary `SELECT * FROM document`
with no `WHERE` clause at all still only returns the current tenant's rows — the
database itself applies the filter transparently. This matters enormously for
multi-tenant systems because the alternative — every single query in the
application remembering to add `WHERE tenant_id = ?` correctly, forever, across
every endpoint anyone ever writes — is exactly the kind of thing that eventually
gets missed once, and when it does, it's a cross-tenant data leak. RLS moves that
guarantee from "every engineer must remember" to "the database structurally
enforces it," which is a fundamentally stronger security posture.

**Q: How do you secure a Postgres database in production, end to end?**
Several layers, applied together (defense in depth): connect applications through a
least-privilege role, never as a superuser, and `GRANT` only the specific
privileges that role genuinely needs (`ALTER DEFAULT PRIVILEGES` so new tables
inherit the right grants automatically); use Row-Level Security for per-tenant row
isolation where multi-tenancy is in play; keep credentials in environment
variables or a secrets manager, never committed in source code; use parameterized
queries (prepared statements) in application code to prevent SQL injection (Phase
1's DML material is the query-construction half of this defense); and restrict
network-level access (`pg_hba.conf` rules, TLS for connections, firewalling the
database port from the public internet). No single layer is sufficient on its own —
a least-privilege role limits blast radius even if RLS or application-level
filtering is somehow bypassed, and RLS protects data even if a query built with
string concatenation somehow slips through.
