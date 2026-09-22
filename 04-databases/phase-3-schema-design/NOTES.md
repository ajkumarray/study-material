<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · multi table](../phase-2-multi-table/NOTES.md) | [Phase 4 · indexing ➡](../phase-4-indexing/NOTES.md)
<!-- /nav -->

# Phase 3 — Schema Design & Data Modeling: Notes (Theory)

Good schema design makes correct data easy and bad data impossible. It's where the
"relational" in RDBMS earns its keep: splitting data into related tables, held
together by keys and constraints, so every fact lives in exactly one place.

---

## 1. Constraints — Rules the Database Enforces For You

A **constraint** is a rule attached to a table or column that the database itself
refuses to let any write violate — regardless of which application, script, ORM, or
human wrote the offending `INSERT`/`UPDATE`. This makes the database the *last line
of defense* for data integrity, rather than trusting every caller to validate
correctly, forever, in every code path.

### Key Concepts

- **PRIMARY KEY**: implicitly `NOT NULL` + `UNIQUE`; identifies each row. Prefer a
  **surrogate key** (`GENERATED ALWAYS AS IDENTITY`, the modern replacement for
  `SERIAL`, or a UUID) over a **natural key** (an existing real-world attribute) —
  natural keys can change or turn out not to be as unique as assumed.
- **NOT NULL**: the column must always have a value.
- **UNIQUE**: no two rows may share the same value in this column (or column
  combination, for a *composite* unique constraint). NULLs are allowed and are **not**
  considered equal to each other under `UNIQUE`, so multiple NULLs can coexist.
  `NOT NULL` + `UNIQUE` together express "a required business key."
- **FOREIGN KEY**: this column's value must match an existing value in the
  referenced (parent) table's key — referential integrity, no orphan rows.
- **Referential action** (on the FK, controls what happens when the parent row is
  deleted/updated): `ON DELETE CASCADE` (delete the children too), `RESTRICT`/
  `NO ACTION` (block the parent delete — the default), `SET NULL` (null out the FK),
  `SET DEFAULT` (reset the FK to its default value).
- **CHECK**: an arbitrary per-row boolean expression (`CHECK (age >= 18)`,
  `CHECK (status IN ('active','suspended','closed'))` as a poor-man's enum).
- **DEFAULT**: the value used when a column is omitted from an `INSERT`.

### Worked Example

```sql
CREATE TABLE customer_account (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,   -- surrogate PK
    email    TEXT NOT NULL,
    CONSTRAINT uq_email UNIQUE (email),
    age      INTEGER CHECK (age >= 18),
    balance  NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status   TEXT NOT NULL DEFAULT 'active'
             CHECK (status IN ('active', 'suspended', 'closed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_item (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES customer_account(id) ON DELETE CASCADE,
    product    TEXT NOT NULL,
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT uq_account_product UNIQUE (account_id, product)  -- composite UNIQUE
);
```

```sql
-- Each of these fails a specific constraint:
INSERT INTO customer_account (email, age) VALUES ('ajay@dev.io', 40);
-- ERROR: duplicate key value violates unique constraint "uq_email"

INSERT INTO customer_account (email, age) VALUES (NULL, 40);
-- ERROR: null value in column "email" violates not-null constraint

INSERT INTO customer_account (email, age) VALUES ('kid@dev.io', 15);
-- ERROR: new row for relation "customer_account" violates check constraint (age >= 18)

INSERT INTO order_item (account_id, product, quantity, unit_price)
VALUES (999, 'Ghost', 1, 10);
-- ERROR: insert or update on table "order_item" violates foreign key constraint
-- DETAIL: Key (account_id)=(999) is not present in table "customer_account".

DELETE FROM customer_account WHERE id = 1;
-- succeeds, and CASCADEs: also deletes every order_item row where account_id = 1
```
In this example: a duplicate email fails `UNIQUE`, a missing email fails
`NOT NULL`, an underage customer fails `CHECK`, an order pointing at a nonexistent
account fails the `FOREIGN KEY`, and deleting a customer whose FK was declared
`ON DELETE CASCADE` automatically removes their dependent order rows instead of
being blocked. Every one of these is enforced no matter which application wrote the
statement — that's the entire point.

Constraints are part of the schema (DDL) and can be added or dropped later:
```sql
ALTER TABLE order_item ADD CONSTRAINT chk_qty CHECK (quantity <= 1000);
ALTER TABLE order_item DROP CONSTRAINT chk_qty;
```

### Comparison Table

| Constraint | Enforces | NULLs allowed? |
|---|---|---|
| `PRIMARY KEY` | uniqueness + existence, one per table | no (implicitly NOT NULL) |
| `UNIQUE` | no duplicate values, any number per table | yes, and multiple NULLs don't conflict |
| `NOT NULL` | value must be present | n/a — this is the rule itself |
| `FOREIGN KEY` | value exists in the referenced table | yes, unless also NOT NULL (a NULL FK just means "no parent") |
| `CHECK` | an arbitrary boolean expression per row | passes automatically if any operand is NULL (UNKNOWN isn't FALSE) |

### Why It's Useful

Application-level validation is a UX convenience — it can be skipped by a bug, a
different microservice, a bulk-load script, an analyst running ad-hoc SQL, or a
future engineer who doesn't know the rule exists. Database constraints are the one
place a rule is enforced for *every* writer, permanently, which is exactly why
production schemas lean on them heavily for anything that would be a genuine data
corruption (negative balances, orphaned foreign keys, duplicate emails) rather than
relying on application code alone.

### Summary / Key Takeaways

- Constraints push integrity rules into the database itself — the one place every
  writer, forever, is guaranteed to obey them.
- `PRIMARY KEY` = unique + not null identity; `UNIQUE` allows NULLs; `CHECK` is an
  arbitrary boolean; `FOREIGN KEY` enforces "no orphans."
- The referential action (`CASCADE`/`RESTRICT`/`SET NULL`) decides what happens to
  children when a parent row is deleted — choose it deliberately per relationship.
- Constraints are DDL and can be added/dropped with `ALTER TABLE` as requirements
  evolve.

---

## 2. Relationships and Cardinality

Relationships between tables are modeled with **foreign keys**. *Where* the FK is
placed, and whether it carries a `UNIQUE` constraint, is what determines the
relationship's cardinality (one-to-one, one-to-many, or many-to-many).

### Key Concepts

- **One-to-one (1:1)**: an FK on either table, with a `UNIQUE` constraint on that
  FK column — the `UNIQUE` is exactly what prevents it from silently becoming
  one-to-many. Typically used to split off rarely-accessed or very large columns
  from a "hot" main table (e.g. a `user_profile` with a big `bio`/`avatar` kept
  separate from `app_user`'s frequently-queried login columns).
- **One-to-many (1:many)**: the FK lives on the **"many"** side and is **not**
  unique — e.g. one `author` has many `book`s, so `book.author_id` references
  `author.id`. This is by far the most common relationship in any schema.
- **Many-to-many (m:n)**: cannot be modeled by putting an FK directly on either
  table — you need a **junction table** (also called a bridge or join table) with a
  foreign key to *each* side and a **composite primary key** across both FKs (to
  prevent the same pair being recorded twice). A junction table is also the natural
  home for **relationship attributes** — data that belongs to the pairing itself,
  not to either entity alone (a grade, an enrollment date, a role).

### Worked Examples

```sql
-- ONE-TO-ONE: app_user <-> user_profile
CREATE TABLE app_user (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email TEXT NOT NULL UNIQUE
);
CREATE TABLE user_profile (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,  -- UNIQUE => 1:1
    bio     TEXT,
    avatar  TEXT
);
```
`user_id` being `UNIQUE` is the whole mechanism — without it, nothing would stop a
second `user_profile` row from also referencing `user_id = 1`, which would silently
turn this into a one-to-many relationship instead.

```sql
-- MANY-TO-MANY: student <-> course, through the junction table `enrollment`
CREATE TABLE student (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE course  (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, title TEXT NOT NULL);

CREATE TABLE enrollment (
    student_id  BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    course_id   BIGINT NOT NULL REFERENCES course(id)  ON DELETE CASCADE,
    grade       TEXT,
    enrolled_on DATE NOT NULL DEFAULT current_date,
    PRIMARY KEY (student_id, course_id)     -- composite PK: this pair appears once
);

INSERT INTO student (name) VALUES ('Ajay'), ('Meera'), ('Ravi');
INSERT INTO course (title) VALUES ('Databases'), ('Algorithms'), ('Networks');
INSERT INTO enrollment (student_id, course_id, grade) VALUES
    (1, 1, 'A'), (1, 2, 'B'),   -- Ajay: Databases, Algorithms
    (2, 1, 'A'), (2, 3, 'A'),   -- Meera: Databases, Networks
    (3, 2, 'C');                -- Ravi: Algorithms
```

```sql
-- Resolving a many-to-many query needs TWO joins through the junction table
SELECT s.name AS student, c.title AS course, e.grade
FROM enrollment e
JOIN student s ON s.id = e.student_id
JOIN course  c ON c.id = e.course_id
ORDER BY s.name, c.title;
--  student |    course    | grade
-- ---------+---------------+-------
--  Ajay    | Algorithms    |   B
--  Ajay    | Databases     |   A
--  Meera   | Databases     |   A
--  Meera   | Networks      |   A
--  Ravi    | Algorithms    |   C
```
In this example: `enrollment.(student_id, course_id)` as a composite primary key
means the pair `(Ajay, Databases)` can only be recorded once — a second attempt to
insert the same pair fails a `PRIMARY KEY` violation, which is exactly the guard
against duplicate enrollment rows. Reading the relationship back out always needs
one join per side of the junction.

```sql
-- Aggregating "through" a junction table
SELECT c.title, count(*) AS students
FROM enrollment e JOIN course c ON c.id = e.course_id
GROUP BY c.title ORDER BY students DESC;
--     title      | students
-- -----------------+----------
--  Databases       |    2
--  Algorithms      |    2
--  Networks        |    1
```

### Comparison Table

| Cardinality | Where the FK lives | Extra constraint needed |
|---|---|---|
| One-to-one | either table | `UNIQUE` on the FK column |
| One-to-many | the "many" side | none (a plain FK) |
| Many-to-many | neither — needs a junction table | composite PK on `(fk_a, fk_b)` in the junction |

### Why It's Useful

Getting cardinality right up front avoids two very different classes of bugs later:
modeling a genuinely many-to-many relationship as one-to-many silently loses data (a
student can only take one course), while forgetting the `UNIQUE` on a 1:1 FK lets a
"should be exactly one" relationship quietly become one-to-many without any error.
Recognizing which of the three shapes a real-world relationship is (and reaching for
a junction table without hesitation for m:n) is one of the most practical schema-
design skills there is.

### Summary / Key Takeaways

- 1:1 → FK + `UNIQUE`; 1:many → FK on the "many" side; m:n → junction table with a
  composite PK — memorize this cheatsheet.
- A junction table decomposes one many-to-many relationship into two one-to-many
  relationships, and is where relationship-specific attributes belong.
- Querying through a junction table always requires joining both sides back to
  their entity tables.
- The presence (or absence) of a `UNIQUE` constraint on an FK is the single detail
  that distinguishes a 1:1 relationship from a 1:many one.

---

## 3. Normalization — 1NF, 2NF, 3NF, BCNF

**Normalization** is the process of organizing tables to **reduce redundancy** and
eliminate **update/insert/delete anomalies**, by ensuring each fact is stored in
exactly one place. It's formally defined in terms of **functional dependencies**:
`X → Y` means "X determines Y" — given a value of X, Y's value is fixed.

### Key Concepts

- **1NF (First Normal Form)**: every cell holds a single, atomic value — no
  multi-valued cells or repeating groups, and every row is uniquely identifiable.
- **2NF**: 1NF, plus no **partial dependency** — no non-key column may depend on
  only *part* of a composite primary key.
- **3NF**: 2NF, plus no **transitive dependency** — no non-key column may depend on
  *another non-key column* (only on the key).
- **BCNF (Boyce-Codd Normal Form)**: a stricter version of 3NF — every
  *determinant* (the left side of any functional dependency) must be a candidate
  key. In practice it rarely differs from 3NF except with overlapping candidate keys.
- **The mnemonic**: every non-key column should depend on *"the key, the whole key,
  and nothing but the key."*
- **Anomalies** normalization exists to prevent:
  - **Update anomaly** — a duplicated fact (a customer's city, repeated on every
    order line) must be updated in every row that holds it; miss one and the data
    now contradicts itself.
  - **Insert anomaly** — you can't record a fact about an entity that has no
    related row yet (e.g. can't add a customer with no order, if customer data only
    lives embedded in the orders table).
  - **Delete anomaly** — deleting the only row that happens to carry a fact erases
    that fact too (deleting a customer's only order erases all record of the
    customer's name and city).

### Worked Example — Normalizing Step by Step

Starting point: one denormalized "spreadsheet" table.
```
order_id | customer_name | customer_city | products                    | prices
---------+----------------+----------------+-----------------------------+----------------
1        | Ajay           | Pune           | "Clean Code, Refactoring"  | "38.50, 47.99"
2        | Meera          | Mumbai         | "Effective Java"            | "45.00"
```
Problems: multiple values crammed into one cell (`products`, `prices`), customer
info duplicated per order, and no clean way to query "which orders include Clean
Code."

```sql
-- 1NF — atomic values, one row per (order, product) line
CREATE TABLE orders_1nf (
    order_id      INT,
    line          INT,
    customer_name TEXT,
    customer_city TEXT,
    product       TEXT,
    price         NUMERIC(8,2),
    PRIMARY KEY (order_id, line)
);
INSERT INTO orders_1nf VALUES
    (1,1,'Ajay','Pune','Clean Code',38.50),
    (1,2,'Ajay','Pune','Refactoring',47.99),
    (2,1,'Meera','Mumbai','Effective Java',45.00);
```
Now every cell holds one value and the table is queryable — but `customer_name`/
`customer_city` are duplicated across `(1,1)` and `(1,2)`, since both lines belong
to Ajay's same order.

```sql
-- 2NF — remove the PARTIAL dependency: customer_name/customer_city depend on
-- order_id ALONE (not on the full composite key (order_id, line)), so split them
-- into their own table, keyed by order_id.
CREATE TABLE orders_2nf (
    order_id      INT PRIMARY KEY,
    customer_name TEXT,
    customer_city TEXT
);
CREATE TABLE order_lines_2nf (
    order_id INT REFERENCES orders_2nf(order_id),
    line     INT,
    product  TEXT,
    price    NUMERIC(8,2),
    PRIMARY KEY (order_id, line)
);
```
Customer data is now stored once per order (not once per line) — the functional
dependency was `order_id → customer_name, customer_city`, and since `order_id` is
only *part* of the composite key `(order_id, line)`, that dependency was partial,
violating 2NF.

```sql
-- 3NF — remove the TRANSITIVE dependency: customer_name/city ultimately depend
-- on customer_id, not directly on order_id (order_id -> customer_id -> name/city).
-- Extract customer into its own table so a city change is one row, not many.
CREATE TABLE customer_3nf (
    customer_id   INT PRIMARY KEY,
    customer_name TEXT NOT NULL,
    city          TEXT
);
CREATE TABLE orders_3nf (
    order_id    INT PRIMARY KEY,
    customer_id INT REFERENCES customer_3nf(customer_id),   -- FK, not repeated data
    ordered_on  DATE DEFAULT current_date
);
CREATE TABLE order_lines_3nf (
    order_id INT REFERENCES orders_3nf(order_id),
    line     INT,
    product  TEXT,
    price    NUMERIC(8,2),
    PRIMARY KEY (order_id, line)
);
```
Now every fact lives in exactly one place: a customer's name/city in
`customer_3nf`, an order's customer link in `orders_3nf` (just an FK, no repeated
data), and a line's product/price in `order_lines_3nf`. Reassembling the full,
human-readable view is a join:
```sql
SELECT o.order_id, c.customer_name, c.city, l.product, l.price
FROM orders_3nf o
JOIN customer_3nf c ON c.customer_id = o.customer_id
JOIN order_lines_3nf l ON l.order_id = o.order_id
ORDER BY o.order_id, l.line;
--  order_id | customer_name | city   |    product     | price
-- ----------+----------------+--------+-----------------+--------
--     1     |     Ajay       | Pune   | Clean Code      | 38.50
--     1     |     Ajay       | Pune   | Refactoring     | 47.99
--     2     |     Meera      | Mumbai | Effective Java  | 45.00
```

### Comparison Table

| Normal form | Requires | Fixes |
|---|---|---|
| 1NF | atomic values, unique rows | multi-valued cells / repeating groups |
| 2NF | 1NF + no partial dependency | non-key columns depending on part of a composite key |
| 3NF | 2NF + no transitive dependency | non-key columns depending on another non-key column |
| BCNF | 3NF + every determinant is a candidate key | rare edge cases with overlapping candidate keys |

### Denormalization — Breaking the Rules on Purpose

Normalization optimizes for **write correctness and storage efficiency**; it does
so at the cost of more `JOIN`s to reassemble data for reads. Heavy read workloads
sometimes can't afford that join cost, so **denormalization** deliberately
reintroduces redundancy to speed reads up:
- Storing a derived/redundant column directly (an order's precomputed `total`, a
  cached `author_name` copied onto `book` to avoid a join on every read).
- A **materialized view** (Phase 6) — a precomputed, storable query result.
- Duplicated data in a separate read-optimized model (the CQRS pattern, covered in
  System Design).

**The trade-off**: faster reads and fewer joins, at the cost of write complexity and
the risk that the duplicated copies drift out of sync unless something (a trigger,
application logic, an event pipeline) keeps them consistent.

**Rule of thumb**: normalize to 3NF by default; denormalize *selectively*, only
where profiling has proven a genuine read bottleneck — never preemptively. NoSQL
document stores like MongoDB (Phase 9) embrace denormalization/embedding by design,
deliberately trading join-free reads for more complex, multi-document updates.

### A Practical Modeling Method

1. Identify **entities** (the nouns in the domain → tables).
2. Identify each entity's **attributes** (→ columns) and pick a **primary key**
   (prefer a surrogate).
3. Identify **relationships** between entities (1:1 / 1:many / m:n) → foreign keys
   or junction tables.
4. Add **constraints** (`NOT NULL`, `UNIQUE`, `FOREIGN KEY`, `CHECK`) to encode every
   business rule you can express declaratively.
5. Normalize to 3NF by default.
6. Denormalize only where a *measured* read bottleneck justifies it.
An entity-relationship (ER) diagram is the usual sketch for step 1–3 before writing
any DDL.

### Why It's Useful

Normalization is what keeps a growing schema from silently accumulating
contradictory data as it's edited over years by many engineers — every "why does
this customer show two different cities in two different tables" production bug
traces back to a fact that wasn't normalized into one authoritative place.
Interviewers ask about it because schema design mistakes are expensive to fix later
(they require data migrations), unlike most other kinds of bugs.

### Summary / Key Takeaways

- 1NF = atomic values. 2NF = 1NF + no partial dependency. 3NF = 2NF + no transitive
  dependency. Mnemonic: "the key, the whole key, and nothing but the key."
- Normalization prevents update, insert, and delete anomalies by ensuring every
  fact lives in exactly one row.
- 3NF is the practical default target for OLTP (transactional) schemas; BCNF rarely
  matters beyond it in practice.
- Denormalize deliberately and selectively for proven read bottlenecks — never as
  the starting design.
