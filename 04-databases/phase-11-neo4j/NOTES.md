<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · redis](../phase-10-redis/NOTES.md) | [Phase 12 · polyglot ➡](../phase-12-polyglot/NOTES.md)
<!-- /nav -->

# Phase 11 — Neo4j (Graph Database): Notes (Theory)

The leading graph database. Where relational databases make **relationships** expensive (join tables, multi-way joins), graph databases make them **first-class and fast** — the right tool when the connections *are* the value.

## 11.1 — The property-graph model
- **Node** — an entity (a Person, Topic, Product), with a **label** (its type, like a table) and **properties** (key-value data).
- **Relationship (edge)** — a *directed, typed* connection between two nodes (`:FRIEND`, `:INTERESTED_IN`), which **also has properties** (`since: 2020`). Relationships are **stored directly** — each node physically points to its relationships.
- **Why this matters:** in a relational DB, a many-to-many link needs a junction table and a JOIN, and a *deep* traversal (friends-of-friends-of-friends) needs repeated self-joins whose cost explodes with depth. In a graph DB, traversal is **"index-free adjacency"** — following a relationship is a pointer hop, O(1) per step, independent of total data size. This is the core performance advantage.

Mapping from relational: node ≈ row, label ≈ table type, relationship ≈ a foreign key *but stored and traversable directly*, properties live on both nodes and edges.

## 11.2 — Cypher basics
Cypher is Neo4j's declarative query language; its ASCII-art syntax *draws* the graph: `(node)-[:REL]->(node)`.
- **`CREATE`** — make nodes `(v:Label {props})` and relationships `(a)-[:TYPE {props}]->(b)`.
- **`MATCH`** — the `SELECT`: describe a **pattern** and return matches. `MATCH (p:Person)-[:FRIEND]->(f) RETURN f.name`.
- **`WHERE`** — filter (like SQL). **`RETURN`** — project results. **`ORDER BY`/`LIMIT`/`count()`** — as in SQL.
- **`MERGE`** — "get or create" (upsert) — matches an existing node/edge or creates it; avoids duplicates.
- **`SET`/`REMOVE`** — update/remove properties; **`DETACH DELETE`** — delete a node and its relationships.

## 11.3 — Traversals (the superpower)
- **Variable-length paths:** `-[:FRIEND*2]->` (exactly 2 hops), `-[:FRIEND*1..3]->` (1 to 3 hops) traverse arbitrary depth in one pattern — friends-of-friends, reachability.
- **`shortestPath((a)-[:FRIEND*]-(b))`** — degrees of separation, routing.
- **Recommendation pattern:** "people who like what I like also like X" is a readable 3-hop pattern — in SQL it's three self-joins on a junction table, slow and unreadable as depth grows.
- **Rule of thumb:** if your SQL needs **3+ self-joins on junction tables**, or you ask "find everything connected to X within N steps," a graph database is likely a large win.

## 11.4 — Indexing, constraints, modeling
- **Indexes** (`CREATE INDEX ... FOR (p:Person) ON (p.name)`) speed finding the **starting** node(s); the traversal from there is index-free (adjacency). **Constraints** (`REQUIRE p.name IS UNIQUE`) enforce uniqueness/existence.
- **Modeling:** nouns → nodes, verbs → relationships, adjectives → properties. Model relationships you'll traverse as edges (not properties). Neo4j is **ACID** and can be a primary store for graph-shaped domains.

## When to choose a graph database
**Wins:** social networks (friends-of-friends), recommendation engines, fraud detection (finding rings/patterns of connections), knowledge graphs, network/dependency/topology data, access-control hierarchies, and routing — anywhere the **relationships and their traversal** are central.
**Not ideal:** simple tabular data, aggregate-heavy analytics, or shallow (1–2 hop) relationships — relational handles those better. Most systems use Neo4j **alongside** a relational DB (polyglot persistence, Phase 12), applying it to the specifically connected part of the domain. Query languages are converging on **GQL** (an ISO standard graph query language) with Cypher as the basis.
