<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · multi table](../phase-2-multi-table/NOTES.md) | [Phase 4 · indexing ➡](../phase-4-indexing/NOTES.md)
<!-- /nav -->

# Phase 3 — Schema Design & Normalization: Interview Q&A

⭐ = asked constantly.

**Q: What is normalization and why do it?** ⭐⭐
Organizing tables to reduce redundancy and eliminate update/insert/delete anomalies, so each fact is stored in exactly one place. Benefits: consistency (change data once), less storage, and integrity. Cost: more tables and joins to reassemble data.

**Q: Explain 1NF, 2NF, 3NF.** ⭐⭐
1NF: atomic values — no multi-valued cells or repeating groups. 2NF: 1NF + no partial dependency (no non-key column depends on only part of a composite key). 3NF: 2NF + no transitive dependency (no non-key column depends on another non-key column). Mnemonic: every non-key column depends on "the key, the whole key, and nothing but the key."

**Q: What is a functional dependency?**
`X → Y` means X determines Y — given X, Y is fixed (e.g., `order_id → customer_name`). Normalization is defined in terms of which dependencies are allowed; anomalies come from non-key columns depending on non-key columns or partial keys.

**Q: What anomalies does normalization prevent? Give examples.** ⭐
Update anomaly (a duplicated fact must be changed in many rows; miss one → inconsistency), insert anomaly (can't add an entity without an unrelated row existing), delete anomaly (deleting one row erases an unrelated fact). Example: storing a customer's city on every order line — moving cities means updating every line, and deleting their last order deletes the customer.

**Q: 3NF vs BCNF?**
BCNF is a stricter form of 3NF: every determinant (left side of a functional dependency) must be a candidate key. They differ only in rare cases with overlapping candidate keys; 3NF is the practical target.

**Q: How do you model a many-to-many relationship?** ⭐⭐
With a junction (bridge) table containing a foreign key to each side and a composite primary key on the pair (to prevent duplicates). It decomposes the m:n into two 1:many relationships and holds any relationship attributes (e.g., enrollment grade/date). You can't put the FK on either entity table directly.

**Q: Where does the foreign key go for 1:1 vs 1:many?** ⭐
1:many → FK on the "many" side (not unique). 1:1 → FK on either side plus a UNIQUE constraint on it (the UNIQUE prevents it from becoming 1:many).

**Q: Surrogate key vs natural key?** ⭐
Surrogate: a system-generated id (auto-increment/UUID) with no business meaning — stable, simple, never changes. Natural: a real-world attribute (email, SSN) — meaningful but can change and may not be truly unique/stable. Prefer surrogates as PKs; enforce natural keys with UNIQUE constraints.

**Q: Why enforce constraints in the database instead of application code?**
Multiple apps, scripts, and migrations touch the data; the database guarantees the invariant for all of them, forever, even under bugs or direct writes. App validation is a convenience/UX layer, not a guarantee.

**Q: What is denormalization and when is it justified?** ⭐⭐
Deliberately introducing redundancy (duplicated columns, precomputed aggregates, materialized views) to speed up reads by avoiding joins. Justified for read-heavy workloads where profiling shows join cost is a real bottleneck — accepting extra write complexity and the risk of copies drifting. Normalize first (3NF), denormalize selectively, never preemptively.

**Q: `ON DELETE CASCADE` vs `RESTRICT` vs `SET NULL`?**
Referential actions on a foreign key when the parent is deleted: CASCADE deletes the children too; RESTRICT/NO ACTION blocks the delete if children exist (the default); SET NULL nulls the FK. Choose by whether children are meaningful without the parent.

**Q: How would you design a schema for [orders / library / social app]?**
Identify entities (nouns → tables), attributes (columns), and relationships (1:1/1:many/m:n → FKs/junctions); pick surrogate keys; add constraints (NOT NULL, UNIQUE, FK, CHECK); normalize to 3NF; then denormalize only where measured reads demand it. Sketch it as an ER diagram and state assumptions.
