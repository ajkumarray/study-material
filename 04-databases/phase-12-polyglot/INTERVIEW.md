<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 11 · neo4j](../phase-11-neo4j/NOTES.md)
<!-- /nav -->

# Phase 12 — Polyglot Persistence: Interview Q&A

⭐ = asked constantly.

**Q: What is polyglot persistence?** ⭐⭐
Using multiple database technologies within one system, each chosen for the data/access pattern it handles best — e.g., PostgreSQL for transactional data, Redis for cache/sessions, Elasticsearch for search, Neo4j for recommendations, Kafka for events. One size doesn't fit all.

**Q: How do you decide which database to use for a given need?** ⭐⭐
By access pattern (key lookup → KV, nested entity → document, traversal → graph, ad-hoc joins/transactions → relational, aggregate scans → columnar), consistency requirements, scale, and schema stability. Default to Postgres (it covers a lot with JSONB/FTS) and add specialists only for real requirements.

**Q: What's the downside of polyglot persistence?** ⭐
Every extra store adds operational burden (deploy, monitor, back up, secure, staff expertise) and consistency risk (the same fact in multiple places drifts). Principle: as few databases as possible, as many as necessary. Complexity must be justified by a real need.

**Q: How do you keep multiple data stores consistent?** ⭐⭐
You can't use one ACID transaction across them. Use cache invalidation for caches; the outbox pattern + events (Kafka) to propagate changes reliably (avoiding dual writes); CQRS to build read models from an event stream; and design updates to be idempotent, accepting eventual consistency between stores.

**Q: If you had to pick one database to start a project, which and why?**
PostgreSQL — it's relational with ACID, plus JSONB (documents), full-text search, arrays, and range types, covering the majority of use cases in a single, well-understood, reliable system. Add specialized stores (Redis first, usually) only when a concrete requirement outgrows it.

**Q: How would you model the same domain (users/purchases/friends) in each paradigm?**
Relational: normalized tables + junction tables, reassembled with joins. Document: user documents embedding purchases, books referenced. Key-value: cached entities, sessions, a sorted-set leaderboard. Graph: users/books as nodes with PURCHASED/FRIEND/SIMILAR edges for recommendations. The best model depends on the dominant query.

**Q: What is the dual-write problem in a polyglot system?**
Writing the same change to two stores (e.g., DB and search index, or DB and cache) isn't atomic — one can succeed while the other fails, leaving them inconsistent. Solve with the outbox pattern (write to the DB + an outbox in one transaction; a relay publishes an event to update the others) rather than writing to both directly.

**Q: Isn't adding databases just "resume-driven development"?** *judgment*
Often, yes — a real risk. Each new datastore should solve a demonstrated problem, not chase novelty. The disciplined approach: measure the need, prefer extending Postgres, and adopt a specialist only when its specific strength (scale, traversal, sub-ms cache) is genuinely required. Fewer moving parts is a feature.
