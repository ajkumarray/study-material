<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · redis](../phase-10-redis/NOTES.md) | [Phase 12 · polyglot ➡](../phase-12-polyglot/NOTES.md)
<!-- /nav -->

# Phase 11 — Neo4j / Graph Databases: Interview Q&A

⭐ = asked constantly.

**Q: What is a graph database, and how does it differ from a relational
database?** ⭐⭐
A graph database stores data as **nodes** (entities, each with a label/type and
properties) and **relationships** (typed, directed edges between two nodes, which
also carry their own properties), and — critically — stores those relationships
*directly*, as pointers each node holds to its neighbors. A relational database
instead models a relationship through foreign keys and, for many-to-many
relationships, a separate junction table, and has to *compute* the relationship at
query time via a `JOIN` by matching key values across tables. For a single hop,
this difference barely matters in practice. For deep, multi-hop traversal
(friends-of-friends-of-friends), it matters enormously: a graph database follows
stored pointers one hop at a time, while a relational database needs another join
per additional hop, with cost that grows sharply as depth increases.

**Q: What is index-free adjacency, and why does it matter?** ⭐⭐
Index-free adjacency means each node physically stores direct references to its
own relationships, so traversing one hop — moving from a node to a connected node
— is a pointer-follow operation, `O(1)` per hop, and that cost is **independent of
the total size of the graph**. This is the structural reason deep traversals stay
fast in a graph database as the dataset grows, while the SQL equivalent (repeated
self-joins on a junction table) gets progressively more expensive, because each
additional join has to search across the full breadth of the relevant tables
rather than simply following a pointer. Indexes in a graph database (section on
indexing) are still used — but only to find the *starting* node(s) of a
traversal; everything after that first lookup is index-free adjacency.

**Q: When would you choose a graph database over a relational one?** ⭐⭐
When relationships between records — and traversing them, potentially several
hops deep — are central to the actual questions the system needs to answer: social
networks (friends-of-friends), recommendation engines ("people who like what you
like also like X"), fraud detection (finding rings or unusual patterns of
connections between accounts/transactions), knowledge graphs, network/dependency/
infrastructure topology, access-control hierarchies, and routing/shortest-path
problems. A useful rule of thumb: if a SQL query needs **three or more self-joins
on junction tables**, or the underlying business question is phrased as "find
everything connected to X within N steps," a graph database is very likely a
significant win, both in raw query performance and in how readable the resulting
query is.

**Q: What is Cypher, and what makes its syntax distinctive?**
Cypher is Neo4j's declarative query language. Its most distinctive feature is that
the query syntax visually *draws* the graph pattern being searched for:
`(a:Person)-[:FRIEND]->(b)` reads, left to right, exactly like the shape of data
you're describing — a `Person` node, connected by a `:FRIEND` relationship, to
another node. `MATCH` plays the role of `SELECT` (describe a pattern, get every
match), `WHERE` filters exactly as it does in SQL, `RETURN` projects results, and
`CREATE` inserts new nodes/relationships. `MERGE` is Cypher's upsert — it matches
an existing node/relationship if the pattern already exists, or creates it if not,
which is the standard way to avoid accidentally creating duplicate nodes when
re-running a script.

**Q: How would you find friends-of-friends, or the shortest path between two
people, in Cypher?** ⭐
Friends-of-friends, excluding direct friends and the person themself:
```cypher
MATCH (me:Person {name: 'Ajay'})-[:FRIEND]->()-[:FRIEND]->(fof)
WHERE fof <> me AND NOT (me)-[:FRIEND]->(fof)
RETURN DISTINCT fof.name;
```
Variable-length traversal to any depth from 1 to 3 hops:
```cypher
MATCH (me:Person {name: 'Ajay'})-[:FRIEND*1..3]->(reachable)
RETURN DISTINCT reachable.name;
```
Shortest path (built in):
```cypher
MATCH p = shortestPath((a:Person {name:'Ajay'})-[:FRIEND*]-(b:Person {name:'Ravi'}))
RETURN length(p) AS degrees;
```
Every one of these is a single, direct pattern in Cypher. The SQL equivalent of
the variable-length version needs a recursive CTE (Phase 2) with explicit depth
tracking to avoid infinite recursion, and the shortest-path version is
impractical to express cleanly in plain SQL at all without significant procedural
logic.

**Q: Do graph databases support ACID transactions?**
Neo4j specifically does — it's fully ACID-compliant and can serve as a primary
system-of-record database for a genuinely graph-shaped domain, not merely as a
secondary, derived index sitting alongside a relational database. (It's worth
noting that some other graph systems, particularly ones built for extreme
distributed scale, do relax some of these guarantees in exchange for that scale —
so the honest general answer is "it depends on the specific system," with Neo4j
itself being a clear ACID example.) The practical takeaway: choosing a graph
database is a decision about which data model fits the problem best, not a
decision to give up transactional correctness.

**Q: How does a graph database model a many-to-many relationship, compared to a
relational database?**
As direct relationships between the two node types — no junction table needed at
all. A relational database has to introduce a separate junction table with a
composite key and two foreign keys (Phase 3) specifically because a plain foreign
key column can only point to one row; a graph database instead just creates one
relationship edge per connection, and any number of such edges can exist between
different node pairs without needing an intermediate structure. Attributes that
would live as columns on that junction table in a relational schema (a `since`
date, a `weight`, a `role`) instead live directly as properties on the
relationship (edge) itself in a graph database.

**Q: When is a graph database the wrong choice?**
For simple tabular data with no meaningful relationship structure to traverse,
for aggregate-heavy analytics over largely flat data (a relational or columnar
store handles `GROUP BY`-style reporting more naturally and typically faster), or
when the relationships in the domain are genuinely shallow — just 1-2 hops — where
a plain `JOIN` already handles the access pattern perfectly well and a graph
database's advantages simply don't come into play. Graph databases earn their
keep specifically on deep, complex connectivity; they don't offer a meaningful
advantage for set-based aggregation over largely unconnected, flat data.

**Q: Can you do graph-style traversal queries without adopting a dedicated graph
database?**
To a limited degree, yes. Recursive CTEs in a relational database (Phase 2) can
walk a hierarchy or a bounded-depth relationship chain, and some relational
databases offer graph-query extensions (Apache AGE adds Cypher-style querying on
top of Postgres, for instance). For occasional, shallow traversal needs within an
otherwise relational system, this is often perfectly sufficient and avoids
introducing a second database technology. But for a domain where relationship-
centric queries are frequent and genuinely deep, a native graph database is
substantially faster (due to index-free adjacency) and produces queries that stay
readable as complexity grows, in a way recursive CTEs increasingly don't once the
traversal logic gets complicated.

**Q: How do indexes and constraints work in Neo4j, given traversal itself is
index-free?**
An index (`CREATE INDEX ... FOR (p:Person) ON (p.name)`) speeds up finding the
**starting node(s)** of a query — e.g. locating the one `Person` node named
`'Ajay'` quickly, even among millions of nodes, rather than scanning every node
with that label. Once that starting node is found, the traversal outward from it
is index-free adjacency — following relationships from there doesn't need any
further index lookups. Constraints (`CREATE CONSTRAINT ... FOR (p:Person) REQUIRE
p.name IS UNIQUE`) enforce uniqueness (or existence) rules on node properties, the
same underlying motivation as a relational `UNIQUE` constraint (Phase 3) —
guaranteeing an invariant holds regardless of which code path writes the data.

**Q: How would you approach modeling a new domain as a property graph?**
A common heuristic: nouns in the domain become nodes, verbs become relationships,
and adjectives/descriptive attributes become properties on whichever node or
relationship they actually describe. The critical modeling decision, and a common
mistake for people new to graph modeling, is deciding what should be a
relationship (edge) versus what should just be a property: anything you'll need
to *traverse* — follow from one node to related nodes — must be modeled as a
relationship, because a plain property can't be followed with a graph pattern the
way an edge can. For example, "interested in" needs to be a relationship (so you
can later ask "who else is interested in this same topic"), even though it might
initially look like it could just be a tag/property on the person.
