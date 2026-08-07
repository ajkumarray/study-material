<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · nosql foundations](../phase-8-nosql-foundations/NOTES.md) | [Phase 10 · redis ➡](../phase-10-redis/NOTES.md)
<!-- /nav -->

# Phase 9 — MongoDB: Interview Q&A

⭐ = asked constantly.

**Q: How does MongoDB's model differ from relational?** ⭐⭐
It stores flexible, nested JSON-like documents (BSON) in schemaless collections, rather than rows in fixed-schema tables. Related data is embedded within a document instead of split across tables joined by keys. Collection ≈ table, document ≈ row, but with nesting and no enforced schema.

**Q: Embedding vs referencing — how do you decide?** ⭐⭐
Embed when data is accessed together, is one-to-few, and you want a single read with no join (a book with its reviews). Reference when data is large/shared/unbounded, changes independently, or is many-to-many (a book's many sales in a separate collection, joined via `$lookup`). Design around access patterns.

**Q: What is the aggregation pipeline?** ⭐
A sequence of stages (`$match`, `$group`, `$sort`, `$project`, `$unwind`, `$lookup`) that documents flow through, each transforming the stream — Mongo's equivalent of SQL GROUP BY/analytics. Put `$match` early to use indexes.

**Q: Does MongoDB support joins and transactions?** ⭐
`$lookup` performs a left join in aggregation, though embedding is usually preferred. Single-document writes are atomic; multi-document ACID transactions exist (since 4.0) but the model encourages designing one document as the atomic unit. Don't assume SQL-level multi-entity transactions by default.

**Q: What is `_id` in MongoDB?**
The primary key, unique per collection. If you don't provide one, Mongo generates an ObjectId — a 12-byte globally-unique, roughly time-ordered id (timestamp + machine + counter), so ids can be created client-side without coordination.

**Q: How do you model a many-to-many relationship in Mongo?**
Reference: store arrays of ids on one/both sides, or a separate "join" collection of pairs, and resolve with `$lookup`. Unlike relational you often denormalize (duplicate some fields) to avoid the join for hot reads, accepting update complexity.

**Q: Is "schemaless" an advantage or a risk?** *nuance*
Both. It speeds early development and handles evolving/variable data, but the schema really lives in your application code — without validation you get inconsistent documents. Mongo offers schema validation rules to add guardrails when you want them (schema-on-write when needed).

**Q: How does MongoDB scale and stay available?**
Replica sets (primary + secondaries) provide HA and failover, with optional secondary reads (eventual consistency). Sharding distributes data across nodes by a shard key for horizontal scale. Choosing a good shard key (even distribution, query locality) is critical.

**Q: When would you choose MongoDB over PostgreSQL?**
Flexible/evolving schemas, document-shaped data read as whole entities, rapid iteration, and easy horizontal scale. Choose Postgres for heavily relational data, complex joins, and strong multi-entity transactions — and note Postgres JSONB already covers many document use cases with ACID, so evaluate whether you need a separate database at all.

**Q: What's the 16MB document limit's implication?**
A single document can't grow unbounded, so unbounded one-to-many relationships (a post's millions of comments) must be referenced in a separate collection rather than embedded. It forces the embed/reference decision for growing data.
