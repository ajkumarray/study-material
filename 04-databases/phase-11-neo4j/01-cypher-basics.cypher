// ============================================================================
// Lesson 11.1/11.2 — Neo4j: the property graph & Cypher basics
// Run in the Neo4j Browser or cypher-shell.
// ============================================================================

// A GRAPH database stores data as NODES (entities) and RELATIONSHIPS (edges)
// between them, each with PROPERTIES. Relationships are FIRST-CLASS — stored
// directly, not computed via join tables. This makes "who is connected to whom,
// how" queries fast and natural. CYPHER is the query language; its syntax draws
// the graph:  (node)-[:RELATIONSHIP]->(node)

// ---- CREATE nodes and relationships ----
// (variable:Label { properties }) is a node;  -[:TYPE]->  is a directed relationship.
CREATE (ajay:Person {name: 'Ajay', city: 'Pune'})
CREATE (meera:Person {name: 'Meera', city: 'Mumbai'})
CREATE (ravi:Person {name: 'Ravi', city: 'Pune'})
CREATE (db:Topic {name: 'Databases'})
CREATE (algo:Topic {name: 'Algorithms'})
// relationships between them (with their own properties):
CREATE (ajay)-[:FRIEND {since: 2020}]->(meera)
CREATE (meera)-[:FRIEND {since: 2021}]->(ravi)
CREATE (ajay)-[:INTERESTED_IN]->(db)
CREATE (meera)-[:INTERESTED_IN]->(db)
CREATE (ravi)-[:INTERESTED_IN]->(algo);

// ---- MATCH — the SELECT of Cypher: describe a PATTERN, get matching data ----
// Find all people:
MATCH (p:Person) RETURN p.name, p.city;

// Find Ajay's friends (follow the FRIEND relationship one hop):
MATCH (ajay:Person {name: 'Ajay'})-[:FRIEND]->(friend)
RETURN friend.name;

// WHERE filters (like SQL); relationships can be traversed in any direction:
MATCH (p:Person)-[:INTERESTED_IN]->(t:Topic {name: 'Databases'})
WHERE p.city = 'Pune'
RETURN p.name;

// ---- MERGE — "get or create" (upsert): avoids duplicate nodes/edges ----
MERGE (ajay:Person {name: 'Ajay'})               // matches the existing Ajay, doesn't duplicate
MERGE (spring:Topic {name: 'Spring'})            // creates Spring (new)
MERGE (ajay)-[:INTERESTED_IN]->(spring);         // create the edge if absent

// ---- Aggregation & ordering ----
MATCH (p:Person)-[:INTERESTED_IN]->(t:Topic)
RETURN t.name AS topic, count(p) AS fans
ORDER BY fans DESC;

// ---- UPDATE / DELETE ----
MATCH (p:Person {name: 'Ravi'}) SET p.city = 'Delhi';            // update a property
MATCH (p:Person {name: 'Ravi'}) REMOVE p.city;                  // remove a property
// DETACH DELETE removes a node AND its relationships (can't delete a connected node otherwise):
// MATCH (p:Person {name: 'Ravi'}) DETACH DELETE p;

// ----------------------------------------------------------------------------
// vs relational: nodes~rows, labels~table types, relationships~foreign keys but
// STORED and TRAVERSABLE directly (no join table, no JOIN). The pattern syntax
// (a)-[:REL]->(b) is the query. Properties live on both nodes and relationships.
// ----------------------------------------------------------------------------
