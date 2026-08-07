// ============================================================================
// Lesson 11.3/11.4 — Neo4j: traversals, variable-length paths, when graphs win
// Run after 01-cypher-basics.cypher.
// ============================================================================

// The whole point of a graph DB: MULTI-HOP TRAVERSALS are fast and natural,
// because relationships are stored (each node knows its neighbors) — you don't
// pay the exponential JOIN cost that relational databases do for deep links.

// ---- Variable-length paths — *N traverses N hops ----
// Friends-of-friends (exactly 2 hops), excluding direct friends and self:
MATCH (me:Person {name: 'Ajay'})-[:FRIEND]->(:Person)-[:FRIEND]->(fof)
WHERE fof <> me AND NOT (me)-[:FRIEND]->(fof)
RETURN DISTINCT fof.name AS suggestion;                 // friend recommendation!

// *1..3 = between 1 and 3 hops (any depth in a range):
MATCH path = (me:Person {name: 'Ajay'})-[:FRIEND*1..3]->(reachable)
RETURN DISTINCT reachable.name, length(path) AS hops
ORDER BY hops;

// ---- Shortest path between two nodes (built-in) ----
MATCH (a:Person {name: 'Ajay'}), (b:Person {name: 'Ravi'}),
      p = shortestPath((a)-[:FRIEND*]-(b))               // undirected traversal
RETURN [n IN nodes(p) | n.name] AS chain, length(p) AS degrees;   // "degrees of separation"

// ---- Recommendation pattern — "people who like what you like, also like…" ----
MATCH (me:Person {name: 'Ajay'})-[:INTERESTED_IN]->(t:Topic)<-[:INTERESTED_IN]-(other:Person)
      -[:INTERESTED_IN]->(rec:Topic)
WHERE NOT (me)-[:INTERESTED_IN]->(rec) AND other <> me
RETURN rec.name AS recommended, count(*) AS strength
ORDER BY strength DESC;
// This is a 3-hop query. In SQL it's 3 self-joins on a junction table — slow and
// unreadable as depth grows. In Cypher it's a single readable pattern.

// ---- Indexing & constraints (Phase 4 ideas, graph edition) ----
CREATE INDEX person_name IF NOT EXISTS FOR (p:Person) ON (p.name);   // speed lookups
CREATE CONSTRAINT unique_person IF NOT EXISTS FOR (p:Person) REQUIRE p.name IS UNIQUE;
// Indexes make finding the STARTING node(s) fast; traversal from there is
// index-free (each node points to its relationships directly).

// ----------------------------------------------------------------------------
// WHEN A GRAPH DB WINS: the value is in the RELATIONSHIPS and you query them
// deeply — social networks (friends-of-friends), recommendations, fraud rings,
// knowledge graphs, network/dependency topology, access-control hierarchies,
// routing. Rule of thumb: if your SQL needs 3+ self-joins on junction tables, or
// "find all X connected to Y within N steps", a graph DB is likely a big win.
//
// WHEN NOT: simple tabular data, aggregate-heavy analytics, or when relationships
// are shallow (1-2 hops) — relational handles those better. Neo4j is ACID and
// can be a primary store for graph-shaped domains, but most apps use it ALONGSIDE
// a relational DB (polyglot, Phase 12), for the specifically graphy part.
// ----------------------------------------------------------------------------
