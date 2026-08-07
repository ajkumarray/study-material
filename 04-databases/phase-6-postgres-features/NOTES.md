<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · transactions](../phase-5-transactions/NOTES.md) | [Phase 7 · production ➡](../phase-7-production/NOTES.md)
<!-- /nav -->

# Phase 6 — PostgreSQL Power Features: Notes (Theory)

Where Postgres goes beyond plain SQL — the features that let one database cover use cases you'd otherwise reach for other tools.

## 6.1 — Views & materialized views
- **View** = a named, saved query that acts like a virtual table; stores no data, re-runs on each select. Encapsulates complex joins/logic behind a simple name and supports security (expose a view, hide the raw tables). Simple views are updatable; join/aggregate views are read-only (unless you add `INSTEAD OF` triggers).
- **Materialized view** = a **stored snapshot** of a query's result on disk. Reads are fast (no recompute) but **stale until `REFRESH`** (use `CONCURRENTLY` + a unique index to avoid locking readers). Use for expensive aggregates/reports that tolerate some staleness — the denormalization (3.4) / caching (System Design 2) idea, built in.

## 6.2 — Functions, procedures, triggers (PL/pgSQL)
**PL/pgSQL** runs procedural logic inside the DB.
- **Function** — returns a value (scalar or a table via `RETURNS TABLE`), runs in the caller's transaction. Good for reusable calculations and query building.
- **Procedure** — `CALL`ed, returns nothing, can manage its own transactions (COMMIT/ROLLBACK) — functions can't.
- **Trigger** — a function fired automatically on INSERT/UPDATE/DELETE (`BEFORE`/`AFTER`, `FOR EACH ROW`/`STATEMENT`), with `NEW`/`OLD` row references. Uses: audit logs, maintaining derived/denormalized columns, complex integrity rules.

**Judgment:** DB code is fast and central and enforces rules regardless of which app writes — but it's harder to version, test, and debug than application code. Keep heavy *business* logic in the app (Spring service layer); use DB functions/triggers for data-integrity, auditing, and things that must hold at the data layer.

## 6.3 — Advanced types (the "multi-model" part)
- **`JSONB`** — binary JSON: schemaless, nested, **queryable and indexable** (GIN index for `@>` containment). `->`/`->>` extract JSON/text; `@>` tests containment; `jsonb_set` updates in place. Gives **document-database flexibility inside a relational DB** (what MongoDB, Phase 9, does as its whole model) — great for variable/semi-structured attributes; trade-off is weaker integrity than real columns.
- **Arrays** — multiple values per column (`text[]`); `ANY`, `unnest`, `array_length`.
- **Enums** — a real type restricting a column to a fixed value set (stricter than a CHECK).
- **Range types** — a value spanning a range (`daterange`, `int4range`) with containment (`@>`) and overlap (`&&`); pair with an exclusion constraint for no-overlap booking systems.
- **Full-text search** — `to_tsvector` (normalized, stemmed document) `@@` `to_tsquery` (search terms), GIN-indexed — real word search with ranking, far better than `LIKE '%…%'`. A basic search engine built in.

**Takeaway:** these cover many needs (document store, lists, search, scheduling) without a second database. Reach for a specialized DB only when Postgres genuinely can't keep up (scale, specialized access patterns).

## 6.4 — Security: roles, privileges, RLS
Access control is built on **roles** (a role is a user and/or a group). Principle: **least privilege** — apps connect as a limited role, never superuser.
- **`GRANT`/`REVOKE`** privileges (`SELECT`/`INSERT`/…) on objects to roles; `ALTER DEFAULT PRIVILEGES` covers future tables; role membership (`GRANT group TO user`) shares grants.
- **Row-Level Security (RLS)** — policies restrict *which rows* a role can see/modify, not just which tables. Essential for **multi-tenant** apps: a policy like `USING (owner = current_setting('app.current_user'))` makes the DB itself filter each query to the current tenant's rows — even a buggy query can't leak another tenant's data. The app sets the current user per session (`SET app.current_user = ...`).

**Defense in depth:** limited connection role + least privilege + RLS for tenant isolation + secrets in env vars (not code) + parameterized queries against injection (Phase 1.4). Security is layered, and the database is the last line.
