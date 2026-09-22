<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · nosql foundations](../phase-8-nosql-foundations/NOTES.md) | [Phase 10 · redis ➡](../phase-10-redis/NOTES.md)
<!-- /nav -->

# Phase 9 — MongoDB (Document Database): Notes (Theory)

MongoDB is the most widely used document database. Data is stored as flexible,
nested **documents** rather than rows in fixed-schema tables — a model that maps
naturally onto objects/JSON in application code, which is a large part of why it's
popular for application development. All examples run in `mongosh` against a
`bookstore` database, adapting the same author/book/sale data used in the SQL
phases.

---

## 1. Documents, Collections, and BSON

### Key Concepts

- **Document**: a JSON-like record, physically stored as **BSON** (Binary JSON —
  adds types plain JSON lacks, like `ObjectId` and native `Date`). A single
  document can be up to **16MB**. Nested objects and arrays are first-class parts
  of a document, not something you bolt on.
- **Collection**: a named group of documents — roughly analogous to a SQL table,
  except a collection is **schemaless**: two documents in the same collection can
  have completely different fields.
- **`_id`**: every document's primary key, unique within its collection. If you
  don't supply one, MongoDB auto-generates a 12-byte **ObjectId** — globally
  unique and roughly time-ordered (it encodes a timestamp, a machine/process
  identifier, and a counter), which means IDs can be generated client-side without
  any coordination with the server.
- **Mapping from the relational world**: collection ≈ table, document ≈ row (but
  nested and schemaless), field ≈ column. The single biggest conceptual
  difference: instead of normalizing related data into separate tables and
  `JOIN`ing them at query time, MongoDB encourages **embedding** related data
  directly inside one document.

### Worked Example

```js
use('bookstore');

db.book.insertOne({
  title: 'Clean Code',
  author: { name: 'Robert Martin', country: 'USA' },  // an embedded sub-document
  price: 38.5,
  genre: 'tech',
  tags: ['clean-code', 'oop'],                        // an array — first-class
  reviews: [{ user: 'ajay', stars: 5 }],               // an array of sub-documents
});
// { acknowledged: true, insertedId: ObjectId("...") }
```
In this example: `author`, `tags`, and `reviews` are not separate collections
joined by a foreign key the way they'd be in a relational schema (Phase 2/3) —
they're nested directly inside the `book` document itself. Reading this one book,
together with its author info and all its reviews, is a single document fetch with
no join at all.

### Comparison Table

| Relational concept | MongoDB equivalent | Key difference |
|---|---|---|
| Table | Collection | no enforced schema across documents |
| Row | Document | can be deeply nested; arbitrary shape per document |
| Column | Field | not every document needs every field |
| Primary key | `_id` | auto-generated ObjectId if omitted, globally unique |
| Foreign key + JOIN | embedding, or a reference + `$lookup` | embedding avoids the join entirely |

### Why It's Useful

Because a document maps almost directly onto a JSON object (which is itself close
to an in-memory object in most application languages), there's very little
"impedance mismatch" between what the application holds in memory and what's
stored — no ORM translating between rows/columns and objects the way a relational
mapper (like JPA/Hibernate) has to. This is a large part of why document databases
feel fast to build with, especially early in a project when the data's shape is
still evolving.

### Summary / Key Takeaways

- A document is a flexible, nested, schemaless BSON record, capped at 16MB;
  collections group documents with no enforced common shape.
- `_id` is the auto-generated, globally unique primary key unless you supply your
  own.
- The core mental shift from relational modeling: embed related data inside one
  document instead of normalizing it into separate tables joined at query time.

---

## 2. CRUD and Query Operators

### Key Concepts

- **Create**: `insertOne(doc)` for a single document, `insertMany([docs])` for
  bulk insertion.
- **Read**: `find(filter, projection)` returns a cursor over every matching
  document; `findOne(filter)` returns the first match (or `null`). Chain
  `.sort()`, `.limit()`, `.skip()` onto a `find()` cursor for ordering and
  pagination.
- **Query operators** (all prefixed `$`): comparison (`$gt`, `$gte`, `$lt`,
  `$lte`, `$ne`, `$in`, `$nin`), logical (`$or`, `$and`, `$not`, `$nor`), array
  (`$all`, `$elemMatch`, `$size`), and existence (`$exists`).
- **Dot notation**: query into a nested field with `'author.country'` — no join
  needed since the data is embedded.
- **Array field matching**: filtering `{ tags: 'java' }` matches any document
  whose `tags` array *contains* `'java'` — you don't need a special array
  operator for simple "contains this value" checks.
- **Update**: `updateOne(filter, update)` / `updateMany(filter, update)` apply
  **update operators** that modify fields *in place*, rather than requiring you to
  send back the entire rewritten document — `$set` (set a field), `$inc` (add to a
  numeric field), `$push`/`$pull` (add to/remove from an array), `$unset` (remove
  a field).
- **Upsert**: passing `{ upsert: true }` to an update means "insert a new document
  matching the filter if nothing matched" — the idempotent "save" pattern.
- **Delete**: `deleteOne(filter)` / `deleteMany(filter)`.

### Worked Examples

```js
db.book.insertMany([
  { title: 'Refactoring', author: { name: 'Martin Fowler', country: 'UK' }, price: 47.99, genre: 'tech', tags: ['refactoring'] },
  { title: 'Effective Java', author: { name: 'Joshua Bloch', country: 'USA' }, price: 45.0, genre: 'tech', tags: ['java'] },
  { title: 'A Novel', author: { name: 'Someone' }, price: 12.99, genre: 'fiction' },
]);
```

```js
db.book.find({ genre: 'tech' });                     // equivalent to WHERE genre = 'tech'
db.book.findOne({ title: 'Clean Code' });             // first matching document, or null

db.book.find({ price: { $gt: 40 } });                 // price > 40
db.book.find({ genre: { $in: ['tech', 'fiction'] } });

db.book.find({ 'author.country': 'USA' });            // dot notation into a nested field
--                                                       matches Clean Code and Effective Java

db.book.find({ tags: 'java' });                       // array CONTAINS 'java' -> Effective Java
db.book.find({ tags: { $all: ['clean-code', 'oop'] } }); // array contains BOTH values

db.book.find({ $or: [{ price: { $lt: 20 } }, { genre: 'tech' }] });
```
In this example: `find({ tags: 'java' })` doesn't require any special array
syntax — MongoDB automatically interprets an equality filter against an array
field as "does the array contain this value," which is a genuinely different
default from a relational `WHERE` clause, where a column either equals a value or
it doesn't.

```js
-- Projection, sort, limit, skip — chained onto find()
db.book.find({ genre: 'tech' }, { title: 1, price: 1, _id: 0 })
       .sort({ price: -1 })     // -1 = descending, 1 = ascending
       .limit(2)
       .skip(0);
// [
//   { title: 'Refactoring', price: 47.99 },
//   { title: 'Effective Java', price: 45.0 }
// ]
```
The projection `{ title: 1, price: 1, _id: 0 }` is the MongoDB equivalent of
`SELECT title, price` — `1` includes a field, `0` excludes it, and `_id` has to be
explicitly excluded since it's included by default.

```js
-- Update operators modify fields WITHOUT rewriting the whole document
db.book.updateOne(
  { title: 'Clean Code' },
  { $set: { price: 41.0 }, $push: { tags: 'bestseller' } }
);
db.book.updateMany({ genre: 'tech' }, { $inc: { price: 1 } });  // +1 to every tech book's price

db.book.updateOne({ title: 'New Book' }, { $set: { price: 20 } }, { upsert: true });
// if no document matched { title: 'New Book' }, one is INSERTED with price: 20
```

```js
db.book.deleteOne({ title: 'A Novel' });
db.book.deleteMany({ price: { $lt: 5 } });
db.book.countDocuments({ genre: 'tech' });
```

### Comparison Table

| SQL | MongoDB equivalent |
|---|---|
| `SELECT * FROM book WHERE genre='tech'` | `db.book.find({ genre: 'tech' })` |
| `SELECT title, price FROM book` | `db.book.find({}, { title: 1, price: 1, _id: 0 })` |
| `UPDATE book SET price = 41 WHERE title='Clean Code'` | `db.book.updateOne({title:'Clean Code'}, {$set:{price:41}})` |
| `INSERT ... ON CONFLICT DO UPDATE` (upsert) | `updateOne(filter, update, { upsert: true })` |
| `DELETE FROM book WHERE price < 5` | `db.book.deleteMany({ price: { $lt: 5 } })` |

### Why It's Useful

Update operators like `$set`/`$inc`/`$push` are important for more than
convenience — they let MongoDB apply a change atomically to just the affected
fields of one document, without the client having to read the whole document,
modify it in memory, and write the whole thing back (which would risk a lost
update if two clients did this concurrently, the same lost-update problem covered
for SQL in Phase 5).

### Summary / Key Takeaways

- `find`/`findOne` read, `insertOne`/`insertMany` create, `updateOne`/`updateMany`
  modify in place via operators, `deleteOne`/`deleteMany` remove.
- Query operators all start with `$`; dot notation queries into nested fields; an
  equality filter against an array field matches if the array *contains* that
  value.
- Update operators (`$set`, `$inc`, `$push`, `$unset`) change specific fields
  atomically without rewriting the whole document.
- `upsert: true` is MongoDB's version of the SQL "insert or update" pattern.

---

## 3. The Aggregation Pipeline

The **aggregation pipeline** is MongoDB's analytics/reporting engine. Documents
flow through a sequence of **stages** (each `$`-prefixed), and each stage
transforms the stream of documents flowing through it — conceptually like a Unix
pipe, or SQL's `GROUP BY` generalized into a whole composable pipeline of
operations.

### Key Concepts

- **`$match`**: filters documents — the aggregation equivalent of `WHERE`. Put it
  as early as possible in the pipeline so it can use indexes and shrink the
  document stream before more expensive stages run.
- **`$group`**: groups documents by a key and computes aggregate values per
  group — the equivalent of `GROUP BY`, using accumulator operators like `$sum`,
  `$avg`, `$max`, `$push` (collect grouped values into an array).
- **`$sort`**: orders the document stream — the equivalent of `ORDER BY`.
- **`$project`**: reshapes each document — the equivalent of `SELECT`, including
  computed fields.
- **`$limit`/`$skip`**: pagination, same idea as SQL's `LIMIT`/`OFFSET`.
- **`$unwind`**: "explodes" an array field into one separate document per array
  element — turns a one-document-with-an-array shape into a one-row-per-element
  shape, useful right before a `$group` that needs to count/aggregate per array
  element.
- **`$lookup`**: performs a **left join** against another collection — Mongo does
  support joins, though embedding is usually the preferred design where it
  applies.
- **`$addFields`/`$set`**: add or overwrite computed fields on documents flowing
  through the pipeline, without removing the fields already there (unlike
  `$project`, which requires listing everything you want to keep).

### Worked Examples

```js
-- $match -> $group -> $sort, the analytics workhorse combination
db.book.aggregate([
  { $match: { genre: 'tech' } },                 // filter first, so it can use an index
  { $group: {
      _id: '$author.country',                    // group key
      avgPrice: { $avg: '$price' },
      count: { $sum: 1 },
      titles: { $push: '$title' },                // collect titles into an array per group
  }},
  { $sort: { avgPrice: -1 } },
]);
// [
//   { _id: 'UK',  avgPrice: 47.99, count: 1, titles: ['Refactoring'] },
//   { _id: 'USA', avgPrice: 41.75, count: 2, titles: ['Clean Code', 'Effective Java'] }
// ]
```
In this example: `_id` in a `$group` stage isn't the document's primary key — it's
the *group key*, exactly the way `GROUP BY author.country` in SQL would define
what each output row represents. `$push` inside `$group` is how you collect a list
of values per group, something SQL needs `string_agg`/`array_agg` for (Phase 2).

```js
-- $project reshapes documents, including computed fields
db.book.aggregate([
  { $project: { _id: 0, title: 1, author: '$author.name',
                priceWithTax: { $multiply: ['$price', 1.18] } } },
]);
// [ { title: 'Clean Code', author: 'Robert Martin', priceWithTax: 45.43 }, ... ]
```

```js
-- $unwind explodes an array into one document per element, then group by it
db.book.aggregate([
  { $unwind: '$tags' },                                  // one document PER TAG
  { $group: { _id: '$tags', books: { $sum: 1 } } },      // count books per tag
  { $sort: { books: -1 } },
]);
// [ { _id: 'tech' /* if a tag existed */, books: N }, ... one row per distinct tag ]
```
`$unwind` is exactly what makes it possible to aggregate over the *elements* of an
array field, rather than the documents themselves — without it, `$group`ing by
`$tags` directly would group by the entire array as a single value, not by each
tag individually.

```js
-- $lookup performs a LEFT JOIN to another collection
db.book.aggregate([
  { $lookup: { from: 'sale', localField: 'title', foreignField: 'bookTitle', as: 'sales' } },
  { $addFields: { unitsSold: { $sum: '$sales.qty' } } },
]);
```
`$lookup` attaches an *array* of matching documents from the `sale` collection
(named `sales` here, via `as`) onto each `book` document — that's the structural
difference from a SQL join, which produces one flat output row per match rather
than nesting the matches as an array on the parent document.

### Comparison Table

| SQL clause | Aggregation stage |
|---|---|
| `WHERE` | `$match` |
| `GROUP BY` + aggregate functions | `$group` |
| `ORDER BY` | `$sort` |
| `SELECT` (incl. computed columns) | `$project` |
| `LIMIT`/`OFFSET` | `$limit`/`$skip` |
| a join | `$lookup` |
| (no direct equivalent — array explosion) | `$unwind` |

### Why It's Useful

The aggregation pipeline is how you do the same kind of reporting/analytics work
in MongoDB that `GROUP BY` and window functions handle in SQL (Phase 2) — it's
what you reach for the moment a query is more than a simple `find()`, especially
anything involving grouping, computed fields, or joining across collections.

### Summary / Key Takeaways

- The aggregation pipeline is a sequence of `$`-prefixed stages, each transforming
  the document stream flowing through it.
- `$match` early (uses indexes, shrinks the stream), then `$group`/`$sort`/
  `$project` do the equivalent of SQL's `GROUP BY`/`ORDER BY`/`SELECT`.
- `$unwind` explodes an array into one document per element — needed before
  grouping by individual array elements.
- `$lookup` performs a real left join, though embedding is generally preferred
  over relying on it as your primary access pattern.

---

## 4. Indexing and Schema Design

### Key Concepts

- **Indexing** works conceptually the same way it does in SQL (Phase 4): B-trees
  under the hood, single-field or compound indexes (the leftmost-prefix rule
  applies to compound indexes here too), unique indexes, text indexes for search,
  and geospatial indexes.
- **`createIndex`**: creates an index on one or more fields; `1` = ascending, `-1`
  = descending.
- **`explain('executionStats')`**: MongoDB's version of `EXPLAIN` (Phase 4) —
  shows whether a query used `COLLSCAN` (a full collection scan, the equivalent of
  a SQL Seq Scan) or `IXSCAN` (an index scan).
- **The core Mongo-specific design decision — embed vs. reference**:
  - **Embed** related data directly inside a document when it's read together
    with the parent, the relationship is "one-to-few" (a book with a handful of
    reviews, not millions), and the goal is a single fast read with no join —
    this is MongoDB's central performance win.
  - **Reference** (store just an ID, and join with `$lookup` when needed) when the
    related data is large, shared across many parents, unbounded in size, changes
    independently of the parent, or the relationship is many-to-many.
- **Denormalization is the expected default in MongoDB** — the philosophical
  opposite of relational 3NF (Phase 3). You model documents around your
  **access patterns**, deliberately duplicating data to make the common reads
  fast, and accept that an update to duplicated data may need to touch multiple
  documents.
- **The 16MB document size limit** is a hard constraint that forces referencing
  (rather than embedding) for any genuinely unbounded one-to-many relationship —
  you cannot embed a million comments inside one blog post document.

### Worked Examples

```js
db.book.createIndex({ genre: 1 });                          // single-field index
db.book.createIndex({ 'author.country': 1, price: -1 });    // compound (leftmost-prefix applies)
db.book.createIndex({ title: 'text' });                     // text index for search
db.book.createIndex({ title: 1 }, { unique: true });        // unique constraint
db.book.getIndexes();
```

```js
db.book.find({ genre: 'tech' }).explain('executionStats');
// stage summary shows either:
//   "COLLSCAN"  -> scanned every document (no usable index)
//   "IXSCAN"    -> walked an index to find matches (the idx_genre index above)
```
This is directly analogous to reading `EXPLAIN` in Postgres (Phase 4) — `COLLSCAN`
on a large collection with a selective filter is exactly the same red flag a `Seq
Scan` is in SQL.

```js
-- EMBED — data accessed together, "contains" relationship, one-to-few
{
  title: 'Clean Code',
  author: { name: 'Robert Martin', country: 'USA' },   // embedded: read together, always
  reviews: [{ user: 'ajay', stars: 5 }]                // embedded: a few reviews per book
}

-- REFERENCE — large/shared/unbounded/independent/many-to-many
// sale collection, referencing book by id rather than embedding sales in book:
{ bookId: ObjectId("..."), qty: 3, date: ISODate("2026-02-05") }
```
In this example: `author` and a small `reviews` array are embedded directly in
`book`, because they're small, read together with the book, and rarely need to be
queried independently of it. `sale` is a *separate* collection referencing
`bookId`, because a popular book could have thousands or millions of sales over
time — embedding those would eventually blow past the 16MB document limit, and
sales data is naturally queried and aggregated independently of any single book
(e.g. "total revenue this month across all books").

### Comparison Table

| | Embed | Reference |
|---|---|---|
| Read cost | one document fetch, no join | needs `$lookup` or a second query |
| Update cost | update is local to one document | update only touches the referenced document |
| Best for | small, "contains," one-to-few, always-read-together data | large, shared, unbounded, or many-to-many data |
| Example | a book's few reviews | a book's many sales over time |

### The Mantra

*"Data that's accessed together is stored together."* Design MongoDB documents to
match how the application actually reads data, not to eliminate redundancy the way
relational normalization does — this is a genuinely different design instinct from
Phase 3's normalization mindset, and getting comfortable with intentional,
access-pattern-driven duplication is the biggest mental shift when moving from
relational to document modeling.

### Why It's Useful

Choosing embed vs. reference correctly is the single decision that determines
whether a MongoDB schema actually delivers on its main promise (fast, join-free
reads of whole entities) or ends up needing just as many `$lookup`s as a relational
schema needed `JOIN`s, while also having given up the strong integrity guarantees
a relational schema provided.

### Summary / Key Takeaways

- MongoDB indexing works like SQL indexing — B-trees, compound indexes with a
  leftmost-prefix rule, and `explain()` to see `COLLSCAN` vs `IXSCAN`.
- Embed one-to-few, always-read-together, "contains" relationships; reference
  large, shared, unbounded, or many-to-many relationships.
- Denormalization (deliberate duplication, modeled around access patterns) is the
  expected default in MongoDB, not an exception the way it is in a normalized
  relational schema.
- The 16MB document limit is a hard forcing function toward referencing for any
  genuinely unbounded relationship.

---

## 5. Transactions, Replica Sets, and Sharding

### Key Concepts

- **Single-document atomicity**: a write to a single document is always atomic —
  no other reader ever sees a partially-applied update to one document, even
  though the document may contain nested arrays/sub-documents being modified
  together in one operation.
- **Multi-document transactions**: available since MongoDB 4.0, for the rarer
  cases where an operation genuinely needs to atomically span multiple documents
  (or multiple collections). The document model actively encourages designing so
  that one document *is* the natural atomic unit, specifically to avoid needing
  multi-document transactions for routine operations.
- **Replica sets**: a primary node plus secondary nodes (a leader-follower
  topology) providing high availability and automatic failover if the primary goes
  down. Reads can optionally be routed to secondaries for read scaling, at the
  cost of potentially reading slightly stale (eventually consistent) data if a
  secondary hasn't yet applied the primary's latest writes.
- **Sharding**: horizontal scaling by distributing data across many nodes based on
  a **shard key** — the same fundamental trade-offs covered generally in Phase 8
  (choosing a shard key well for even distribution and query locality matters a
  great deal; a poorly chosen shard key can create a "hot shard" that receives
  disproportionate traffic, and cross-shard queries are inherently more expensive
  than single-shard ones).

### Why It's Useful

Understanding that MongoDB's transaction model is built around single-document
atomicity (rather than assuming full multi-entity ACID transactions the way a
relational database does by default) directly explains why the embed-vs-reference
decision matters so much — embedding related data into one document isn't just a
read-performance optimization, it's also how you get atomic, transaction-free
writes for that data by design.

### When to Choose MongoDB (and When Not To)

**Choose MongoDB** when the data genuinely has a flexible or rapidly evolving
schema, is naturally document-shaped and accessed as whole entities, the team wants
to iterate quickly without schema-migration overhead, and the workload needs
horizontal scale that a single-node relational database can't provide.

**Don't reach for MongoDB** (prefer relational/SQL) when the data is heavily
relational with genuine many-to-many relationships and complex ad-hoc queries
across many entities, when strong multi-entity transactional guarantees are a core
requirement, or — increasingly commonly — when PostgreSQL's `JSONB` support (Phase
6) already covers the specific document-flexibility need without requiring a
second database technology to operate at all.

### Summary / Key Takeaways

- Single-document writes are always atomic; multi-document transactions exist but
  are the exception, not the default design assumption.
- Replica sets provide HA/failover; reading from secondaries trades consistency
  for read scaling.
- Sharding scales writes/storage horizontally via a shard key — choosing that key
  well is critical to avoid hot shards and expensive cross-shard queries.
- MongoDB's sweet spot: flexible/evolving, document-shaped data read as whole
  entities, at horizontal scale. Its weak spot: heavily relational data needing
  many-to-many joins and strong multi-entity transactions.
