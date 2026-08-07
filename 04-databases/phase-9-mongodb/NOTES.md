<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · nosql foundations](../phase-8-nosql-foundations/NOTES.md) | [Phase 10 · redis ➡](../phase-10-redis/NOTES.md)
<!-- /nav -->

# Phase 9 — MongoDB (Document Database): Notes (Theory)

The most popular document database. Data is stored as flexible, nested **documents** — the model maps naturally to objects/JSON, which is why it's loved for app development.

## 9.1 — Documents, collections, BSON
- **Document** — a JSON-like record (stored as **BSON**, binary JSON with more types: ObjectId, Date, etc.), up to **16MB**. Nested objects and arrays are first-class. No fixed schema — documents in a collection can differ.
- **Collection** — a group of documents (≈ a table, but schemaless).
- **`_id`** — the primary key, auto-generated as a globally-unique **ObjectId** if you don't supply one.
- **Mapping from relational:** collection ≈ table, document ≈ row (but nested + schemaless), field ≈ column. The big difference: you **embed** related data instead of joining.

## 9.2 — CRUD & query operators
- **Create:** `insertOne` / `insertMany`.
- **Read:** `find(filter, projection)` / `findOne`. Query **operators** start with `$`: comparison (`$gt`,`$lt`,`$in`,`$ne`), logical (`$or`,`$and`,`$not`), array (`$all`,`$elemMatch`,`$size`), existence (`$exists`). **Dot notation** queries nested fields (`'author.country'`); querying an array field matches if it *contains* the value. Chain `.sort().limit().skip()` for ordering/pagination.
- **Update:** `updateOne`/`updateMany` with **update operators** that modify in place — `$set`, `$inc`, `$push`/`$pull` (arrays), `$unset` — you don't rewrite the whole document. **`upsert: true`** = insert if absent (the idempotent "save," System Design 4).
- **Delete:** `deleteOne`/`deleteMany`.

## 9.3 — The aggregation pipeline
Mongo's analytics engine: documents flow through **stages** (`$`-prefixed), each transforming the stream — like SQL GROUP BY + a Unix pipe. Common stages:
- **`$match`** (WHERE — put first so it can use indexes), **`$group`** (GROUP BY, with `$sum`/`$avg`/`$max`/`$push`), **`$sort`** (ORDER BY), **`$project`** (SELECT/compute fields), **`$limit`/`$skip`** (paginate), **`$unwind`** (explode an array to one doc per element), **`$lookup`** (a left join to another collection), **`$addFields`/`$set`** (add computed fields). It's how you do reporting/analytics in Mongo.

## 9.4 — Indexing & schema design
- **Indexing** works like SQL (Phase 4): B-trees, single/compound (leftmost-prefix rule), unique, text, geospatial. `createIndex`, `explain('executionStats')` shows `COLLSCAN` vs `IXSCAN`. Index the fields you filter/sort on; indexes cost writes and RAM.
- **The core design decision — embed vs reference:**
  - **Embed** (nest data in one document) when the data is read together, is a one-to-few "contains" relationship, and you want a **single fast read with no join** — Mongo's main win. E.g., a book with its reviews inline.
  - **Reference** (store an id, join with `$lookup`) when the data is large/shared/unbounded, changes independently, or is many-to-many. E.g., a book's thousands of sales in a separate collection.
- **Denormalization is expected** (the opposite of relational 3NF): model around your **access patterns**, duplicating data to make common reads fast, accepting that updates to duplicated data touch multiple documents. The 16MB limit forces referencing for unbounded growth.

**The mantra:** *"data that's accessed together is stored together."* Design the documents to match how the app reads, not to eliminate redundancy.

## 9.5 — Transactions, replica sets, sharding
- **Transactions:** single-document writes are atomic; **multi-document transactions** exist (since 4.0) for the rarer cases you need them — but the model encourages designing so one document = one atomic unit.
- **Replica sets:** a primary + secondaries (leader–follower) for high availability and automatic failover; reads can go to secondaries (eventual consistency).
- **Sharding:** horizontal scale by a **shard key** across many nodes (consistent hashing / ranges) — the same trade-offs as System Design 3 (choose the shard key well; cross-shard queries are costly).

**When to choose Mongo:** flexible/evolving schemas, document-shaped data accessed as whole entities, rapid development, and horizontal scale. **When not:** heavily relational data with many-to-many joins and strong multi-entity transactional needs (use SQL) — or when Postgres JSONB (Phase 6) already suffices.
