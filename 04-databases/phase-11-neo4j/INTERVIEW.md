<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · redis](../phase-10-redis/NOTES.md) | [Phase 12 · polyglot ➡](../phase-12-polyglot/NOTES.md)
<!-- /nav -->

# Phase 11 — Neo4j / Graph Databases: Interview Q&A

⭐ = asked constantly.

**Q: What is a graph database and how does it differ from relational?** ⭐⭐
It stores data as nodes (entities) and relationships (typed, directed edges), both with properties, and stores relationships directly. Relational databases model relationships via foreign keys/junction tables and must JOIN to traverse them. Graph DBs make connected-data queries natural and fast.

**Q: What is index-free adjacency and why does it matter?** ⭐⭐
Each node physically references its relationships, so traversing one hop is a pointer follow — O(1) per step, independent of total data size. This is why deep traversals (friends-of-friends-of-friends) stay fast in a graph DB, while the equivalent multi-self-join grows exponentially expensive in SQL.

**Q: When would you choose a graph database?** ⭐⭐
When relationships and their traversal are central: social networks, recommendations, fraud detection (connection patterns/rings), knowledge graphs, network/dependency topology, access hierarchies, routing. Rule of thumb: if your SQL needs 3+ self-joins on junction tables or "connected within N steps" queries, consider a graph DB.

**Q: What is Cypher?**
Neo4j's declarative query language. Its ASCII-art syntax draws the graph — `(a:Person)-[:FRIEND]->(b)` — with `MATCH` (find patterns), `WHERE`, `RETURN`, `CREATE`, `MERGE` (upsert). It reads like the shape of the data you're looking for.

**Q: How do you find friends-of-friends or shortest path in Cypher?**
Friends-of-friends: `MATCH (me)-[:FRIEND]->()-[:FRIEND]->(fof) WHERE fof<>me RETURN DISTINCT fof`. Variable length: `-[:FRIEND*1..3]->`. Shortest path: `shortestPath((a)-[:FRIEND*]-(b))`. These are one-line patterns versus complex recursive SQL.

**Q: Do graph databases support ACID?**
Neo4j does — it's ACID-compliant and can be a primary store for graph-shaped domains. (Some distributed graph systems relax this for scale.) So the choice is about the data model fit, not giving up transactions.

**Q: How does a graph DB model a many-to-many relationship vs relational?**
As direct relationships between nodes — no junction table. Relational needs a junction table and joins; the graph stores each connection as an edge you traverse. Attributes of the relationship live on the edge itself (e.g., `since`, `weight`, `role`).

**Q: When is a graph database the wrong choice?**
For simple tabular data, heavy aggregate analytics, or shallow relationships (1–2 hops) — relational or columnar stores are better. Graph DBs shine on deep, complex connectivity, not on set-based aggregation over flat data.

**Q: Can you get graph queries without a dedicated graph DB?**
To a degree: recursive CTEs (Databases Phase 2) traverse hierarchies in SQL, and Postgres extensions (Apache AGE) add graph queries. For occasional shallow traversal that's fine; for a relationship-centric domain with frequent deep traversals, a native graph DB is far faster and clearer.
