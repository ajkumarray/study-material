<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · transactions](../phase-5-transactions/NOTES.md) | [Phase 7 · production ➡](../phase-7-production/NOTES.md)
<!-- /nav -->

# Phase 6 — PostgreSQL Power Features: Interview Q&A

⭐ = asked constantly.

**Q: View vs materialized view?** ⭐⭐
A view is a saved query, re-run on each access — always fresh, no storage. A materialized view stores the query result on disk — fast reads but stale until `REFRESH`. Use a view to simplify/secure queries; a materialized view to cache expensive aggregates that tolerate staleness.

**Q: Why use a view?**
Encapsulate complex joins/logic behind a simple name, provide a stable interface as tables change, and enforce security (grant access to a view exposing only some columns/rows instead of the base tables).

**Q: Function vs stored procedure in Postgres?**
A function returns a value and runs within the caller's transaction (can't COMMIT). A procedure is `CALL`ed, returns nothing, and can manage transactions (COMMIT/ROLLBACK). Functions for computed values/queries; procedures for multi-step operations needing transaction control.

**Q: What is a trigger and when would you use one?** ⭐
A function fired automatically on INSERT/UPDATE/DELETE (BEFORE/AFTER, per row/statement), with NEW/OLD references. Uses: audit logging, maintaining derived/denormalized columns, and integrity rules too complex for constraints. Avoid putting core business logic in triggers (hard to test/trace).

**Q: Should business logic live in the database or the application?** *judgment*
Data-integrity, auditing, and rules that must hold regardless of the writer belong in the DB (constraints, triggers). Business logic belongs in the app (testable, versioned, easier to debug). Overusing stored procedures creates hidden, hard-to-maintain logic; underusing constraints lets bad data in. Balance.

**Q: What is JSONB and when do you use it?** ⭐⭐
A binary JSON column type — schemaless, nested, queryable (`->`, `->>`, `@>`) and indexable (GIN). Use for semi-structured or variable attributes without a rigid schema — document-database flexibility inside Postgres. Trade-off: less integrity/typing than real columns, so use it for genuinely variable data, not to avoid modeling.

**Q: JSONB vs a separate document database (MongoDB)?**
JSONB gives document flexibility while keeping ACID transactions, joins, and one system to operate. A dedicated document DB wins for very large scale, sharding, or when the whole model is document-shaped. Many teams start with Postgres JSONB and move only if needed.

**Q: How does Postgres do full-text search?**
`to_tsvector` turns text into a normalized, stemmed document; `to_tsquery` parses search terms; `@@` matches them; a GIN index makes it fast. It supports stemming, ranking, and phrase search — far better than `LIKE '%x%'`, and enough to avoid a separate search engine for many apps.

**Q: What is Row-Level Security and why does it matter?** ⭐
RLS lets you attach policies that restrict which rows each role can see/modify, enforced by the database. It's key for multi-tenant apps — the DB filters every query to the current tenant, so even a buggy query can't leak another tenant's data. Defense in depth beyond app-level checks.

**Q: How do you secure a Postgres database?**
Connect apps as least-privilege roles (never superuser), GRANT only needed privileges, use RLS for row/tenant isolation, keep credentials in environment/secrets managers, use parameterized queries against injection, and restrict network access (pg_hba, TLS). Layered defenses.
