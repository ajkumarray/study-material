<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · multi table](../phase-2-multi-table/NOTES.md) | [Phase 4 · indexing ➡](../phase-4-indexing/NOTES.md)
<!-- /nav -->

# Phase 3 — Schema Design & Normalization: Interview Q&A

⭐ = asked constantly.

**Q: What is normalization, and why do it?** ⭐⭐
Normalization is organizing tables so that each fact is stored in exactly one place,
which eliminates update/insert/delete anomalies and reduces redundant storage. The
benefit is consistency — you change a fact once, in one row, instead of hunting down
every duplicated copy — plus stronger integrity guarantees. The cost is more tables
and therefore more `JOIN`s needed to reassemble a human-readable view at query time.
A concrete before/after: an unnormalized orders table that repeats
`customer_name`/`customer_city` on every line item forces you to update dozens of
rows if a customer moves; a normalized schema with a separate `customer` table means
that's one `UPDATE` on one row.

**Q: Explain 1NF, 2NF, and 3NF.** ⭐⭐
- **1NF**: every cell holds a single atomic value — no comma-separated lists or
  repeating groups crammed into one column, and every row is uniquely identifiable.
- **2NF**: 1NF, plus no *partial dependency* — no non-key column may depend on only
  part of a composite primary key. If the PK is `(order_id, line)` but
  `customer_name` really only depends on `order_id`, that's a partial dependency,
  and it violates 2NF.
- **3NF**: 2NF, plus no *transitive dependency* — no non-key column may depend on
  another non-key column rather than directly on the key. If `order_id` determines
  `customer_id`, and `customer_id` determines `customer_city`, then `customer_city`
  transitively depends on `order_id` through `customer_id` — extract `customer` into
  its own table.
The standard mnemonic: every non-key column should depend on *"the key, the whole
key, and nothing but the key."*

**Q: What is a functional dependency?**
`X → Y` means "X determines Y" — for any given value of X, Y's value is fixed. For
example `order_id → customer_name` (in an unnormalized table) says: knowing the
order tells you the customer's name with certainty. Normalization is formally
defined entirely in terms of which functional dependencies are and aren't allowed
relative to the table's key — a *partial* dependency (on part of a composite key)
violates 2NF, and a *transitive* dependency (through another non-key column) violates
3NF.

**Q: What anomalies does normalization prevent? Give concrete examples for each.** ⭐
- **Update anomaly** — a fact duplicated across many rows (a customer's city stored
  on every one of their order lines) has to be updated everywhere it's duplicated;
  miss one row during the update and the data now contradicts itself.
- **Insert anomaly** — you can't record a fact about an entity that has no related
  row yet. If customer info only exists embedded inside the orders table, you can't
  register a new customer until they place their first order.
- **Delete anomaly** — deleting one row erases an unrelated fact along with it. If a
  customer's name/city only lives on their orders, deleting their one and only order
  deletes all record that the customer ever existed.
Normalizing extracts each of these facts (customer identity, order, line item) into
its own table, so none of the three anomalies can happen — each fact has exactly one
home, independent of the others.

**Q: 3NF vs BCNF — what's the difference, and does it matter in practice?**
BCNF (Boyce-Codd Normal Form) is a stricter version of 3NF: every *determinant* (the
left-hand side of any functional dependency in the table) must itself be a candidate
key. 3NF allows a narrow exception BCNF does not, involving overlapping candidate
keys. In practice the two forms coincide for the vast majority of real schemas —
you're unlikely to hit a table that satisfies 3NF but fails BCNF unless it has
multiple overlapping composite candidate keys, so 3NF is the practical target most
teams design to.

**Q: How do you model a many-to-many relationship?** ⭐⭐
With a **junction table** (also called a bridge or join table) that holds a foreign
key to each of the two entity tables, plus a composite primary key across both FKs
so the same pairing can't be recorded twice.
```sql
CREATE TABLE enrollment (
    student_id BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    course_id  BIGINT NOT NULL REFERENCES course(id)  ON DELETE CASCADE,
    grade      TEXT,
    PRIMARY KEY (student_id, course_id)
);
```
You cannot put an FK on `student` or `course` directly for this relationship —
either table would need to store a variable number of references, which a single
column can't do. The junction table decomposes one many-to-many relationship into
two one-to-many relationships (`student → enrollment`, `course → enrollment`), and is
also the natural place to put attributes that belong to the *relationship itself*
rather than to either entity — a `grade`, an `enrolled_on` date, a role.

**Q: Where does the foreign key go for a 1:1 relationship vs a 1:many
relationship?** ⭐
For 1:many, the FK lives on the "many" side and is *not* unique — e.g. `book
.author_id` references `author.id`, since one author has many books. For 1:1, the FK
can live on either table, but it must additionally carry a `UNIQUE` constraint —
`user_profile.user_id UNIQUE REFERENCES app_user(id)`. That `UNIQUE` is the entire
mechanism that distinguishes a 1:1 relationship from a 1:many one at the schema
level: remove it, and nothing stops a second profile row from referencing the same
user, silently turning the relationship into one-to-many.

**Q: Surrogate key vs natural key — which should you use as a primary key, and
why?** ⭐
A **surrogate key** is a system-generated identifier with no business meaning — an
auto-incrementing integer (`GENERATED ALWAYS AS IDENTITY`) or a UUID. A **natural
key** is an existing real-world attribute that happens to be unique, like an email
address or a national ID number. Surrogate keys are generally preferred as primary
keys because natural keys can change over time (a person changes their email) or
turn out to not be as globally unique/stable as assumed at design time (VAT numbers,
phone numbers, and even SSNs have all had real-world exceptions) — and changing a
primary key value cascades painfully through every foreign key that references it.
The practical pattern is: use a surrogate as the PK, and still enforce the natural
key's uniqueness with a separate `UNIQUE` constraint so the business rule is still
protected.

**Q: Why enforce constraints in the database instead of (or in addition to)
application code?**
Because multiple applications, background jobs, migration scripts, and even direct
psql sessions from an engineer debugging an incident all write to the same
database — application-level validation only protects the one code path it's written
into. A constraint declared at the schema level is enforced for every single writer,
forever, including future code nobody has written yet. Application-level validation
still has real value (better error messages, avoiding a round trip for an obviously
invalid request) but it's a UX layer on top of the database's guarantee, not a
substitute for it.

**Q: What is denormalization, and when is it justified?** ⭐⭐
Denormalization is deliberately reintroducing redundancy — duplicated columns,
precomputed aggregates, materialized views, or a separately maintained read model —
specifically to make reads faster by avoiding join cost. It's justified for
read-heavy workloads where profiling has *actually shown* that join cost is a real
bottleneck, not preemptively. The standard advice is: normalize to 3NF first (get
correctness and a clean write model), then denormalize selectively, in the specific
places measurement proves it matters, accepting the added write complexity and the
risk of the duplicated copies drifting out of sync unless something (a trigger, an
application-level update, an event pipeline) keeps them consistent.

**Q: `ON DELETE CASCADE` vs `RESTRICT` vs `SET NULL` — how do you choose?**
These are the referential actions a foreign key can declare for what happens to
child rows when their referenced parent row is deleted. `CASCADE` deletes the
children along with the parent — appropriate when the children are meaningless
without the parent (deleting a `user` should delete their `user_profile`).
`RESTRICT`/`NO ACTION` (the default) blocks the parent delete entirely while
children still reference it — appropriate when losing the children silently would be
dangerous or surprising (you probably don't want deleting an `author` to silently
delete every `book` they wrote). `SET NULL` nulls out the FK on the children instead
of deleting them — appropriate when the child entity is still meaningful on its own
once orphaned (deleting an `assigned_to` employee shouldn't delete their open
tickets, just unassign them). The choice should be made deliberately per
relationship, not left at whatever the default happens to be.

**Q: How would you design a schema for [an orders system / a library / a social
app]?**
Work through it in order: identify the **entities** (the nouns — customer, order,
product), then each entity's **attributes** (columns) and a primary key (prefer a
surrogate), then the **relationships** between entities and their cardinality (1:1,
1:many, or m:n, deciding FK placement or a junction table for each), then add
**constraints** (`NOT NULL`, `UNIQUE`, `FOREIGN KEY`, `CHECK`) to encode every
business rule you can express declaratively, then **normalize to 3NF** as the
default, and finally consider **denormalizing** only where a stated read pattern
demands it. Sketching it as a quick ER diagram while narrating the entities and
relationships out loud is the expected format for this kind of open-ended design
question — and stating your assumptions explicitly (e.g. "can a book have multiple
authors? I'll assume yes, so that's many-to-many") is as important as the final
schema.

**Q: What's the difference between a `CHECK` constraint and enforcing the same rule
in application code — is there ever a reason to do both?**
A `CHECK` constraint is evaluated by the database on every write, from every writer,
with no way to bypass it short of dropping the constraint itself. Application-level
validation runs earlier in the request lifecycle and can produce a friendlier,
field-specific error message before a round trip to the database is even made. In
practice, doing both is normal and not redundant: the application check gives a good
user-facing error quickly; the database `CHECK` is the non-negotiable backstop that
still holds even if that specific validation is ever missed, bypassed, or simply not
reimplemented consistently in a second service that also writes to the same table.
