<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · multi table](../phase-2-multi-table/NOTES.md) | [Phase 4 · indexing ➡](../phase-4-indexing/NOTES.md)
<!-- /nav -->

# Phase 3 — Schema Design & Data Modeling: Notes (Theory)

Good schema design makes correct data easy and bad data impossible. It's where the "relational" in RDBMS earns its keep — splitting data into related tables so every fact lives in exactly one place.

## 3.1 — Constraints
Constraints push integrity rules *into* the database, so no app, script, or migration can write bad data — the DB is the last line of defense (better than trusting every caller).
- **PRIMARY KEY** — unique + NOT NULL; identifies each row. Prefer a **surrogate** key (`GENERATED ALWAYS AS IDENTITY` / `SERIAL` / UUID) over a natural key (natural keys change; surrogates don't).
- **NOT NULL** — required value.
- **UNIQUE** — no duplicate values (NULLs allowed and treated as distinct). Can span multiple columns (composite unique). NOT NULL + UNIQUE = a required business key.
- **FOREIGN KEY** — must reference an existing parent row (referential integrity — no orphans). The **referential action** sets what happens to children when the parent goes: `ON DELETE CASCADE` (delete them), `RESTRICT`/`NO ACTION` (block, the default), `SET NULL`, `SET DEFAULT`.
- **CHECK** — an arbitrary per-row boolean (`CHECK (age >= 18)`, `CHECK (status IN (...))` as a poor-man's enum).
- **DEFAULT** — value used when none is supplied.

Constraints are DDL — add/drop later with `ALTER TABLE`. Enforcing in the DB (not just app code) guarantees the invariant across every writer, forever.

## 3.2 — Relationships (cardinality)
Modeled with foreign keys; *where* the FK lives (and whether it's UNIQUE) sets the cardinality:
- **One-to-one (1:1)** — FK on either side + **UNIQUE** on that FK (the UNIQUE stops it from becoming 1:many). Used to split off rarely-used or large columns.
- **One-to-many (1:many)** — FK on the **many** side, not unique (an author has many books; the FK is on `book`). The most common relationship.
- **Many-to-many (m:n)** — you *can't* put the FK on either side; use a **junction / bridge / join table** holding a FK to each side, with a **composite primary key** to prevent duplicate pairs. It turns one m:n into two 1:many relationships, and is the natural home for **relationship attributes** (grade, joined date, role). Resolve an m:n query with two joins through the junction.

## 3.3 — Normalization
Organizing tables to **reduce redundancy** and eliminate **update/insert/delete anomalies**. Built on **functional dependencies** — `X → Y` ("X determines Y"). Each normal form assumes the previous holds:
- **1NF** — atomic values: no multi-valued cells or repeating groups; each row unique. (Split a "Clean Code, Refactoring" cell into one row per product.)
- **2NF** — 1NF + no **partial dependency**: no non-key column depends on only *part* of a composite key. (With PK `(order_id, line)`, `customer_name` depends on `order_id` alone → split customers off.)
- **3NF** — 2NF + no **transitive dependency**: no non-key column depends on another non-key column. (`order → customer → city`: extract customer into its own table so a city change is one row, not many.)
- **BCNF** — a stricter 3NF (every *determinant* must be a candidate key); rarely differs from 3NF in practice.

**The mnemonic:** every non-key column depends on *"the key, the whole key, and nothing but the key"* (1NF/2NF/3NF).

**The anomalies it prevents** (the *why*):
- **Update anomaly** — a fact stored in many rows (Ajay's city on every order line) must be changed everywhere; miss one and the data contradicts itself. Normalized → one row to change.
- **Insert anomaly** — can't record a customer who has no order yet (if customer data only lives in the orders table). Normalized → a customer table stands alone.
- **Delete anomaly** — deleting a customer's only order erases the customer's existence. Normalized → the customer row survives.

Aim for **3NF as the default** for transactional (OLTP) systems.

## 3.4 — Denormalization (breaking the rules on purpose)
Normalization optimizes for *writes and integrity*; heavy *read* workloads sometimes pay too much in join cost. **Denormalization** deliberately duplicates data (or precomputes aggregates) to speed reads:
- Store a derived/redundant column (an order's `total`, a cached `author_name` on `book`), a **materialized view** (Phase 6), or duplicated data in a read model (System Design CQRS).
- **The trade-off:** faster reads, fewer joins — at the cost of write complexity and the risk of the copies drifting out of sync (you must keep them consistent, e.g., via triggers or app logic).
- **Rule of thumb:** normalize first (3NF), then denormalize *selectively* where profiling proves a real read bottleneck — never preemptively. NoSQL document stores (Mongo, Phase 9) embrace denormalization/embedding by design, trading join-free reads for update complexity.

Modeling method: identify **entities** (nouns → tables), their **attributes** (columns), and **relationships** (FKs/junctions); pick keys; apply constraints; normalize to 3NF; then denormalize only where measured reads demand it. An ER diagram is the usual sketch.
