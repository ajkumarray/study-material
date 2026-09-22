<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · redis](../phase-10-redis/NOTES.md) | [Phase 12 · polyglot ➡](../phase-12-polyglot/NOTES.md)
<!-- /nav -->

# Phase 11 — Neo4j (Graph Database): Notes (Theory)

Neo4j is the leading graph database. Where a relational database makes
*relationships* relatively expensive to query deeply (junction tables, multi-way
joins that get slower as traversal depth grows), a graph database makes
relationships **first-class and fast** — the right tool specifically when the
connections between records *are* the value, not just metadata about the records.
All examples run in the Neo4j Browser or `cypher-shell`, building a small
Person/Topic social graph.

---

## 1. The Property-Graph Model

### Key Concepts

- **Node**: an entity — a Person, a Topic, a Product — carrying a **label** (its
  type, roughly analogous to a table name) and **properties** (key-value data,
  like a row's columns).
- **Relationship (edge)**: a **directed, typed** connection between exactly two
  nodes (`:FRIEND`, `:INTERESTED_IN`), which — critically — **also carries its own
  properties** (e.g. `since: 2020` on a `:FRIEND` relationship). A relationship
  isn't just a link; it's its own first-class entity with data of its own.
- **Relationships are stored directly**: each node physically holds pointers to
  its own relationships, rather than relationships being *computed* at query time
  by matching foreign key values across tables (the way a SQL `JOIN` works).
- **Index-free adjacency**: because relationships are stored as direct pointers
  on each node, traversing one hop — "follow this relationship to the connected
  node" — is a pointer-follow operation, `O(1)` per hop, **independent of the total
  size of the dataset**. This is the core performance advantage a graph database
  has over a relational database for deep, multi-hop traversal.

### Worked Example

```cypher
CREATE (ajay:Person {name: 'Ajay', city: 'Pune'})
CREATE (meera:Person {name: 'Meera', city: 'Mumbai'})
CREATE (ravi:Person {name: 'Ravi', city: 'Pune'})
CREATE (db:Topic {name: 'Databases'})
CREATE (algo:Topic {name: 'Algorithms'})

CREATE (ajay)-[:FRIEND {since: 2020}]->(meera)
CREATE (meera)-[:FRIEND {since: 2021}]->(ravi)
CREATE (ajay)-[:INTERESTED_IN]->(db)
CREATE (meera)-[:INTERESTED_IN]->(db)
CREATE (ravi)-[:INTERESTED_IN]->(algo);
```
In this example: `ajay`, `meera`, and `ravi` are nodes labeled `Person`; `db` and
`algo` are nodes labeled `Topic`; `FRIEND` and `INTERESTED_IN` are directed
relationship types connecting them. The `:FRIEND` relationship from `ajay` to
`meera` carries its own property (`since: 2020`) — data that describes the
*friendship itself*, not either person. There's no separate "friendship table" here
at all; the relationship *is* the data structure.

### Comparison Table — Mapping from the Relational World

| Relational concept | Graph equivalent | Key difference |
|---|---|---|
| Row | Node | carries a label (type) + properties |
| Table (type grouping) | Label | a node can carry multiple labels |
| Foreign key + JOIN | Relationship (edge) | stored and traversable directly — no join computed at query time |
| Junction table (m:n) | a direct relationship between the two nodes | no separate table needed at all |
| Column on a junction table | a property on the relationship itself | relationships carry their own data |

### Why It's Useful

In a relational schema, a many-to-many relationship needs a junction table (Phase
3), and querying a *deep* connection — friends of friends of friends — needs
repeated self-joins whose cost grows sharply with depth, because each additional
hop is another full join operation across potentially large tables. In a graph
database, following that same chain is just following stored pointers one hop at
a time — the cost per hop stays constant regardless of how much total data is in
the database, which is exactly why deep traversal queries that would be
prohibitively slow (or simply impossible to write cleanly) in SQL are both fast and
natural to express in Cypher.

### Summary / Key Takeaways

- A node is an entity with a label and properties; a relationship is a directed,
  typed, and itself-property-bearing connection between two nodes.
- Relationships are stored directly (index-free adjacency) — traversing one hop is
  a pointer-follow, `O(1)`, regardless of total dataset size.
- This is the structural reason deep, multi-hop queries stay fast in a graph
  database while the equivalent repeated self-joins get progressively more
  expensive in a relational one.

---

## 2. Cypher Basics

**Cypher** is Neo4j's declarative query language. Its most distinctive feature is
that its syntax visually *draws* the graph pattern you're describing:
`(node)-[:RELATIONSHIP]->(node)` reads left to right exactly like the shape of the
data you're looking for.

### Key Concepts

- **`CREATE`**: creates nodes (`(v:Label {props})`) and relationships
  (`(a)-[:TYPE {props}]->(b)`).
- **`MATCH`**: the Cypher equivalent of `SELECT` — describe a **pattern**, and
  Cypher returns every match found in the graph.
- **`WHERE`**: filters matches, just like SQL's `WHERE`.
- **`RETURN`**: projects the result — the equivalent of SQL's `SELECT` column
  list.
- **`ORDER BY`/`LIMIT`/`count()`**: work the same conceptual way they do in SQL.
- **`MERGE`**: "get or create" — matches an existing node/relationship if one
  already exists matching the pattern, or creates it if not. This is Cypher's
  upsert, and it's the standard way to avoid accidentally creating duplicate
  nodes.
- **`SET`/`REMOVE`**: update or remove a property on a node/relationship.
- **`DETACH DELETE`**: deletes a node **and** all of its relationships in one
  operation — a plain `DELETE` on a node that still has relationships attached
  would fail, since Neo4j won't leave a "dangling" relationship pointing at a
  deleted node.

### Worked Examples

```cypher
-- MATCH is the SELECT of Cypher: describe a pattern, get matches
MATCH (p:Person) RETURN p.name, p.city;
--  p.name |  p.city
-- --------+----------
--  Ajay   |  Pune
--  Meera  |  Mumbai
--  Ravi   |  Pune
```

```cypher
-- Follow a relationship one hop: Ajay's friends
MATCH (ajay:Person {name: 'Ajay'})-[:FRIEND]->(friend)
RETURN friend.name;
--  friend.name
-- --------------
--  Meera
```

```cypher
-- WHERE filters, exactly like SQL
MATCH (p:Person)-[:INTERESTED_IN]->(t:Topic {name: 'Databases'})
WHERE p.city = 'Pune'
RETURN p.name;
--  p.name
-- ---------
--  Ajay
```
Ajay is the only `Person` node connected by an `:INTERESTED_IN` relationship to the
`Databases` topic *and* living in `Pune` (Meera is also interested in Databases,
but lives in Mumbai, so she's filtered out by `WHERE`). Note there's no `JOIN`
anywhere in the query — `(p:Person)-[:INTERESTED_IN]->(t:Topic {name:
'Databases'})` *is* the join, expressed as the shape of the pattern itself,
resolved via stored relationship pointers rather than computed by matching
foreign-key values at query time.

```cypher
-- MERGE — upsert: match if it exists, create if it doesn't
MERGE (ajay:Person {name: 'Ajay'})           -- matches the EXISTING Ajay node, no duplicate created
MERGE (spring:Topic {name: 'Spring'})        -- Spring doesn't exist yet -> CREATED
MERGE (ajay)-[:INTERESTED_IN]->(spring);     -- creates the edge only if it's not already there
```

```cypher
-- Aggregation and ordering work like SQL
MATCH (p:Person)-[:INTERESTED_IN]->(t:Topic)
RETURN t.name AS topic, count(p) AS fans
ORDER BY fans DESC;
--    topic    | fans
-- -------------+------
--  Databases   |  2     (Ajay and Meera)
--  Algorithms  |  1     (Ravi)
```

```cypher
-- UPDATE / DELETE
MATCH (p:Person {name: 'Ravi'}) SET p.city = 'Delhi';     -- update a property
MATCH (p:Person {name: 'Ravi'}) REMOVE p.city;            -- remove a property entirely

-- DETACH DELETE removes a node AND every relationship attached to it in one step
-- MATCH (p:Person {name: 'Ravi'}) DETACH DELETE p;
```

### Comparison Table

| SQL | Cypher |
|---|---|
| `SELECT` | `MATCH ... RETURN` |
| `WHERE` | `WHERE` |
| `INSERT` | `CREATE` |
| `INSERT ... ON CONFLICT DO UPDATE` (upsert) | `MERGE` |
| `UPDATE ... SET` | `SET` |
| `DELETE` (with FK cleanup) | `DETACH DELETE` |
| `JOIN ... ON fk = pk` | a relationship pattern in `MATCH`, e.g. `-[:REL]->` |

### Why It's Useful

Cypher's pattern-matching syntax lets you write a query that visually resembles
the data shape you're looking for, which makes graph-shaped queries dramatically
more readable than their relational equivalent — a query like "find people
interested in the same topic as me" reads almost like a sentence in Cypher, versus
a multi-table join with aliases in SQL.

### Summary / Key Takeaways

- Cypher's `(a)-[:REL]->(b)` pattern syntax *is* the query — no separate join
  clause needed, because the pattern shape encodes the traversal.
- `MATCH`/`WHERE`/`RETURN` map directly onto SQL's `SELECT`/`WHERE`/column-list
  concepts; `MERGE` is Cypher's upsert.
- `DETACH DELETE` is required to delete a node that still has relationships —
  Neo4j won't leave a dangling relationship pointing at a deleted node.

---

## 3. Traversals — the Graph Database's Superpower

The entire value proposition of a graph database centers on this: multi-hop
traversals are fast and natural, because each node already knows its neighbors
(index-free adjacency, section 1) — you never pay the exponentially growing join
cost a relational database incurs for deep, repeated self-joins.

### Key Concepts

- **Variable-length paths**: `-[:FRIEND*2]->` matches a path of exactly 2 hops;
  `-[:FRIEND*1..3]->` matches any path between 1 and 3 hops. This lets one Cypher
  pattern express "arbitrary depth traversal" directly, something that requires a
  recursive CTE (Phase 2) — or several manually unrolled self-joins — in SQL.
- **`shortestPath(...)`**: a built-in function that finds the shortest path
  between two nodes along a given relationship pattern — directly answers
  "degrees of separation" and routing-style questions.
- **The recommendation pattern**: "people who like what I like also like X" is
  naturally a 3-hop pattern (me → shared interest → other people → their other
  interests) — a single readable `MATCH` clause in Cypher, versus three self-joins
  on a junction table in SQL, which becomes both slower and progressively harder
  to read as the hop count grows.
- **Rule of thumb**: if a SQL query needs **3 or more self-joins on junction
  tables**, or the underlying question is "find everything connected to X within N
  steps," a graph database is very likely a significant win — both in raw
  performance and in query readability/maintainability.

### Worked Examples

```cypher
-- Friends-of-friends (exactly 2 hops), excluding direct friends and yourself —
-- the canonical "people you may know" recommendation query
MATCH (me:Person {name: 'Ajay'})-[:FRIEND]->(:Person)-[:FRIEND]->(fof)
WHERE fof <> me AND NOT (me)-[:FRIEND]->(fof)
RETURN DISTINCT fof.name AS suggestion;
--  suggestion
-- ------------
--  Ravi           (Ajay -> Meera -> Ravi: a friend-of-a-friend, not a direct friend)
```

```cypher
-- Variable-length path: everyone reachable within 1 to 3 hops
MATCH path = (me:Person {name: 'Ajay'})-[:FRIEND*1..3]->(reachable)
RETURN DISTINCT reachable.name, length(path) AS hops
ORDER BY hops;
--  reachable.name | hops
-- -----------------+------
--  Meera           |  1
--  Ravi            |  2
```
In this example: `*1..3` in one pattern replaces what would otherwise require
either a recursive CTE (Phase 2) with an explicit depth-tracking column, or three
manually written, progressively nested self-joins in SQL — Cypher expresses
"any depth from 1 to 3" as a single, direct syntactic feature of the pattern
itself.

```cypher
-- Shortest path — "degrees of separation" between two people
MATCH (a:Person {name: 'Ajay'}), (b:Person {name: 'Ravi'}),
      p = shortestPath((a)-[:FRIEND*]-(b))
RETURN [n IN nodes(p) | n.name] AS chain, length(p) AS degrees;
--         chain          | degrees
-- -------------------------+---------
--  [Ajay, Meera, Ravi]     |    2
```
`shortestPath` traverses the relationship undirected here (`-[:FRIEND*]-` with no
arrowhead), meaning it follows `:FRIEND` relationships in either direction —
appropriate since friendship in this graph is conceptually mutual even though each
`:FRIEND` edge was created in one direction.

```cypher
-- The recommendation pattern — "people who like what you like also like..."
MATCH (me:Person {name: 'Ajay'})-[:INTERESTED_IN]->(t:Topic)<-[:INTERESTED_IN]-(other:Person)
      -[:INTERESTED_IN]->(rec:Topic)
WHERE NOT (me)-[:INTERESTED_IN]->(rec) AND other <> me
RETURN rec.name AS recommended, count(*) AS strength
ORDER BY strength DESC;
-- (0 rows against this small seed graph — Meera, the only other person who
--  shares a topic with Ajay (Databases), isn't interested in any OTHER topic
--  besides the one she already shares with him, so there's nothing new to
--  recommend. Add a few more INTERESTED_IN edges and this starts returning rows.)
```
This is a genuine 3-hop query: Ajay → a shared topic → another person interested
in that same topic → that person's *other* topics. In SQL, this exact query would
require three self-joins against a junction table connecting people and topics —
slower to execute at scale, and considerably harder to read correctly at a glance
than this single Cypher `MATCH` pattern. The zero-row result here is itself a
useful check: it confirms `WHERE NOT (me)-[:INTERESTED_IN]->(rec)` is correctly
filtering out topics Ajay is already connected to, rather than the query being
broken.

### Comparison Table

| Traversal need | SQL approach | Cypher approach |
|---|---|---|
| 1 hop | one `JOIN` | one relationship arrow `-[:REL]->` |
| Fixed N hops | N-1 self-joins | `-[:REL*N]->` |
| Variable depth range | a recursive CTE | `-[:REL*min..max]->` |
| Shortest path | complex recursive logic, often impractical in plain SQL | `shortestPath(...)`, built in |

### Why It's Useful

The exact same underlying question — "how are these two records connected, and how
closely?" — is a natural, single-pattern query in Cypher and a rapidly-degrading
performance and readability problem in SQL as the traversal depth grows. This is
precisely why systems with a genuinely connections-centric domain (social graphs,
fraud rings, recommendation engines) reach for a graph database specifically for
that part of their data.

### Summary / Key Takeaways

- Variable-length path syntax (`*N`, `*min..max`) lets one Cypher pattern express
  arbitrary-depth traversal directly.
- `shortestPath(...)` is a built-in primitive for degrees-of-separation and
  routing-style questions.
- The friend-of-friend / recommendation pattern is the canonical illustration of
  why graph databases win: a readable 2-3 hop Cypher pattern versus multiple
  increasingly expensive SQL self-joins.
- Rule of thumb: 3+ self-joins on junction tables, or "connected within N steps"
  questions, are strong signals a graph database would be a significant win.

---

## 4. Indexing, Constraints, and Modeling

### Key Concepts

- **Indexes**: `CREATE INDEX index_name IF NOT EXISTS FOR (p:Person) ON
  (p.name)` speeds up finding the **starting node(s)** of a traversal (e.g. "find
  the Person named 'Ajay'" to begin a pattern from). Once that starting node is
  found, the traversal itself is index-free — following relationships from there
  is pure pointer-adjacency, not another indexed lookup.
- **Constraints**: `CREATE CONSTRAINT constraint_name IF NOT EXISTS FOR (p:Person)
  REQUIRE p.name IS UNIQUE` enforces uniqueness (or existence) rules on node
  properties, analogous to a relational `UNIQUE` constraint (Phase 3).
- **Modeling heuristic**: nouns become nodes, verbs become relationships,
  adjectives/descriptive attributes become properties. Critically: model anything
  you'll need to *traverse* as a relationship (an edge), not as a property — a
  property can't be followed with a graph pattern, but a relationship can.
- **Neo4j is ACID**: it supports full transactional guarantees and can serve as a
  primary system-of-record store for a genuinely graph-shaped domain, not merely
  as a secondary/derived index alongside another database.

### Worked Examples

```cypher
CREATE INDEX person_name IF NOT EXISTS FOR (p:Person) ON (p.name);
CREATE CONSTRAINT unique_person IF NOT EXISTS FOR (p:Person) REQUIRE p.name IS UNIQUE;
```
The index makes `MATCH (p:Person {name: 'Ajay'})` fast even in a graph with
millions of `Person` nodes, by avoiding a full scan to find the starting node; the
constraint additionally guarantees no two `Person` nodes can ever share the same
`name`, enforced by the database itself — precisely the same motivation as a
relational `UNIQUE` constraint (Phase 3).

### Why It's Useful

The index-for-entry-point, pointer-adjacency-for-traversal split is the whole
reason graph databases can stay fast on deep queries at scale: you pay a
logarithmic-ish cost once, to locate where a traversal begins, and then a constant
cost per subsequent hop — regardless of how large the rest of the graph is. This is
structurally different from relational indexing, where *every* join in a multi-hop
query needs its own index lookup to stay fast.

### When to Choose a Graph Database

**Strong fits**: social networks (friends-of-friends), recommendation engines,
fraud detection (finding rings or unusual patterns of connections), knowledge
graphs, network/dependency/infrastructure topology, access-control hierarchies,
and routing — anywhere the relationships between records, and traversing them
deeply, are central to the actual questions being asked.

**Not a good fit**: simple tabular data with no meaningful relationship structure,
aggregate-heavy analytics over flat data (a relational or columnar store handles
`GROUP BY`-style reporting far better), or genuinely shallow relationships (1-2
hops) that a plain `JOIN` already handles perfectly well.

Most real systems use Neo4j **alongside** a relational database (the polyglot
persistence pattern, Phase 12), applying it specifically to the connections-centric
slice of the domain rather than as a wholesale relational-database replacement.
Query languages across the graph-database ecosystem are also converging toward
**GQL**, an ISO-standardized graph query language, with Cypher serving as its
primary basis.

### Summary / Key Takeaways

- Indexes speed up finding a traversal's *starting* node(s); the traversal itself
  from there is index-free, pointer-based adjacency.
- Constraints enforce uniqueness/existence on node properties, the same motivation
  as relational `UNIQUE` constraints.
- Model anything you need to traverse as a relationship, not a property.
- Neo4j is fully ACID and can be a primary store for a genuinely graph-shaped
  domain — the choice to use it is about data-model fit, not about giving up
  transactional guarantees.
- Most systems use a graph database alongside a relational one, applying it
  specifically to the connections-centric part of the domain (Phase 12).
