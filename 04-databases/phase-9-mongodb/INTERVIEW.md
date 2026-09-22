<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · nosql foundations](../phase-8-nosql-foundations/NOTES.md) | [Phase 10 · redis ➡](../phase-10-redis/NOTES.md)
<!-- /nav -->

# Phase 9 — MongoDB: Interview Q&A

⭐ = asked constantly.

**Q: How does MongoDB's data model differ from a relational database's?** ⭐⭐
MongoDB stores flexible, nested, JSON-like **documents** (physically as BSON) in
schemaless **collections**, rather than rows conforming to a fixed table schema.
The most important structural difference: related data is typically **embedded**
directly inside a document instead of being normalized into separate tables and
recombined at query time with a `JOIN`. Collection is roughly analogous to a table,
document to a row — but a document can be arbitrarily nested, and unlike a
relational table, the database itself doesn't enforce that every document in a
collection shares the same fields or types. `_id` plays the role of the primary
key, auto-generated as a globally unique `ObjectId` if you don't supply one
yourself.

**Q: Embedding vs. referencing — how do you actually decide which to use?** ⭐⭐
Embed related data directly inside a parent document when that data is read
together with the parent (a book and its handful of reviews), the relationship is
"one-to-few" (bounded, small), and you specifically want a single fast document
read with no join at all — this is the central MongoDB performance win. Reference
instead (store just an ID on one or both sides, resolve with `$lookup` when
needed) when the related data is large, shared across many parents, genuinely
unbounded in size (a book's sales over its entire lifetime could be millions of
rows), changes independently of the parent, or the relationship is many-to-many.
The 16MB document size cap is a hard forcing function here too: you literally
cannot embed an unbounded relationship past a certain size, so it has to be
referenced regardless of preference. The underlying design principle is: model the
document shape around your application's actual **access patterns** — what gets
read together, together — rather than around eliminating data duplication the way
relational normalization (Phase 3) does.

**Q: What is the aggregation pipeline, and how does it map to SQL concepts?** ⭐
It's a sequence of `$`-prefixed **stages** that documents flow through, each stage
transforming the stream — MongoDB's equivalent of SQL analytics/reporting.
`$match` filters (like `WHERE`), `$group` groups and aggregates (like `GROUP BY`
plus `SUM`/`AVG`/etc.), `$sort` orders (like `ORDER BY`), `$project` reshapes
documents including computed fields (like `SELECT`), `$limit`/`$skip` paginate
(like `LIMIT`/`OFFSET`), and `$lookup` performs a left join to another collection.
`$unwind` has no direct SQL equivalent — it explodes an array field into one
separate document per array element, which is what makes it possible to
`$group` by individual elements of an array rather than by the array as a whole.
Put `$match` as early as possible in the pipeline, exactly for the same reason
you'd put a selective `WHERE` early conceptually in SQL: it lets the stage use an
index and shrinks the document stream before more expensive stages have to process
it.
```js
db.book.aggregate([
  { $match: { genre: 'tech' } },
  { $group: { _id: '$author.country', avgPrice: { $avg: '$price' }, count: { $sum: 1 } } },
  { $sort: { avgPrice: -1 } },
]);
```

**Q: Does MongoDB support joins and transactions?** ⭐
`$lookup` in the aggregation pipeline performs a genuine left join against another
collection, though relying on it heavily as your primary access pattern usually
signals the data should have been embedded instead. On transactions: a write to a
**single document** is always atomic, including nested arrays/sub-documents
modified together in one operation — this is the foundational guarantee the whole
embed-heavy design philosophy leans on. **Multi-document** ACID transactions have
existed since MongoDB 4.0 for the rarer cases that genuinely need to span multiple
documents or collections atomically, but the idiomatic MongoDB design approach is
to structure documents so that one document *is* the natural atomic unit for a
given operation, specifically to avoid needing multi-document transactions for
routine writes. Don't assume SQL-level, ubiquitous multi-entity transactional
guarantees by default — confirm what a specific operation actually needs and design
for it explicitly.

**Q: What is `_id` in MongoDB, and what is an ObjectId made of?**
`_id` is the primary key of every document, unique within its collection. If the
application doesn't supply one on insert, MongoDB generates a 12-byte `ObjectId`
automatically — composed of a 4-byte timestamp, a 5-byte random/machine-specific
value, and a 3-byte incrementing counter. This structure means ObjectIds are
roughly time-ordered (you can extract an approximate creation timestamp from one)
and can be generated entirely client-side, by any node, without any coordination
with the server or risk of collision — a notably different approach from a
relational database's `SERIAL`/`IDENTITY` column, which requires the database
itself to hand out the next value.

**Q: How do you model a many-to-many relationship in MongoDB, given there's no
junction-table concept the way there is in SQL?**
Typically via referencing: store an array of related IDs on one or both sides of
the relationship, or maintain a separate collection of linking documents (a
document-database analogue of a SQL junction table, Phase 3), and resolve the
relationship with `$lookup` when you need to query across it. Unlike the
relational approach, though, it's common and expected in MongoDB to also
denormalize — duplicating a handful of frequently-needed fields from the "other
side" directly onto each document (e.g. storing a book's title directly on a
review document, not just its ID) — specifically to avoid needing a `$lookup` for
the most common, hot-path reads, accepting the added complexity of keeping that
duplicated data updated when the source changes.

**Q: Is "schemaless" a genuine advantage, or does it come with a real risk?**
*nuance question*
Both, and a strong answer acknowledges the trade-off rather than picking one side.
Schema flexibility speeds up early development and handles data whose shape
genuinely varies or evolves quickly, without the migration overhead a relational
schema change would require. But the schema doesn't actually disappear — it moves
out of the database and into the application code that reads and writes those
documents, enforced far less rigorously (and far less visibly to other engineers)
than a `CREATE TABLE` statement enforces it. Without discipline, this produces
collections full of subtly inconsistent documents that different parts of the
application implicitly assume different shapes for. MongoDB does offer schema
validation rules you can opt into per collection, letting you add schema-on-write
guardrails selectively for the parts of your data model that have stabilized,
while keeping genuinely variable parts flexible.

**Q: How does MongoDB scale and stay highly available?**
**Replica sets** — a primary node plus secondary nodes in a leader-follower
topology — provide high availability and automatic failover if the primary
becomes unreachable; reads can optionally be directed to secondaries to scale read
throughput, at the cost of potentially reading slightly stale data if a secondary
hasn't yet caught up to the primary's latest writes (eventual consistency, Phase
8). **Sharding** distributes data horizontally across many nodes based on a
**shard key**, scaling both write throughput and total storage capacity beyond
what one node could hold. Choosing a good shard key is genuinely critical: an
evenly-distributing key spreads load well, while a poorly chosen one (e.g. a
monotonically increasing timestamp as the sole key) can concentrate writes onto a
single "hot" shard, defeating the purpose of sharding in the first place —
cross-shard queries are also inherently more expensive than single-shard ones, so
the shard key should ideally align with the application's actual query patterns.

**Q: When would you choose MongoDB over PostgreSQL, and when would you choose
PostgreSQL instead?** ⭐
Choose MongoDB when the data's schema is genuinely flexible or rapidly evolving,
the natural access pattern is reading/writing whole document-shaped entities, the
team values fast iteration without relational migration overhead, and the workload
needs horizontal scale beyond a single node. Choose PostgreSQL instead when the
data is heavily relational with real many-to-many relationships and complex ad-hoc
queries across many entities, when strong multi-entity transactional guarantees
are a core requirement, or — worth stating explicitly in an interview — when
PostgreSQL's `JSONB` column type (Phase 6) already covers the specific document-
flexibility need on its own, in which case adopting an entirely separate database
technology may not be justified at all. A thoughtful answer notes that this isn't
a purely technical decision either — operational cost (a second database
technology to run, monitor, and have the team learn) is a real factor (Phase 8).

**Q: What's the practical implication of the 16MB document size limit?**
It's a hard ceiling that forces referencing, rather than embedding, for any
relationship that can grow unbounded over time. A blog post with a handful of
comments can safely embed them; a blog post that could accumulate millions of
comments over its lifetime cannot — that relationship has to live in a separate
`comments` collection referencing the post's ID, resolved via `$lookup` or a
separate query when needed, rather than growing the post document indefinitely.
In effect, the 16MB limit is what forces the embed-vs-reference decision to be made
deliberately for any relationship whose growth isn't obviously bounded, rather than
letting you embed everything by default.
