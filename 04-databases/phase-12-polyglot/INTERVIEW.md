<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 11 · neo4j](../phase-11-neo4j/NOTES.md)
<!-- /nav -->

# Phase 12 — Polyglot Persistence: Interview Q&A

⭐ = asked constantly.

**Q: What is polyglot persistence?** ⭐⭐
Polyglot persistence is the deliberate use of multiple database technologies
within a single system, each one chosen specifically for the data shape and
access pattern it handles best — PostgreSQL for transactional, relationally
integral data; Redis for caching, sessions, and counters; Elasticsearch for
ranked full-text search; Neo4j for relationship-traversal questions like
recommendations; Kafka for a durable, replayable event log between services. The
underlying principle is that no single database technology is simultaneously the
best choice for every kind of data and every access pattern a real system
accumulates, so rather than forcing all of it through one store, each store holds
the slice of data it's genuinely best suited to.

**Q: How do you actually decide which database to use for a given piece of
data?** ⭐⭐
Work through the same ordered decision framework from Phase 8: first, the
**access pattern** — a simple key lookup points to key-value, a whole-nested-
entity read points to document, deep relationship traversal points to graph,
ad-hoc joins and unanticipated queries point to relational, and aggregate-heavy
scans over huge event volumes point to a columnar warehouse. Second,
**consistency requirements** — correctness-critical data wants strong consistency
(relational, or a CP-configured NoSQL system); tolerant-of-staleness data can
trade consistency for availability. Third, **scale** — does it fit comfortably on
one node? Fourth, **schema stability** — fixed and genuinely relational, or highly
variable? Fifth, **operational cost** — is the specific benefit worth one more
technology for the team to run and know well? In practice, default to PostgreSQL
(it already covers relational data, `JSONB` documents, full-text search, and
array/range types, all under one ACID system) and add a specialized store only
once a concrete, measured requirement genuinely outgrows what Postgres alone can
provide.

**Q: What's the downside of polyglot persistence — why not just adopt every
specialized store that could theoretically help?** ⭐
Every additional datastore is real, ongoing operational burden: another system to
deploy, monitor, back up, secure, upgrade, and have the team genuinely know how to
operate well under incident pressure at 3am — and it's one more place the same
underlying fact can exist, meaning one more opportunity for those copies to drift
out of sync with each other. The guiding principle is "as few databases as
possible, as many as necessary" — every additional store needs to earn its
operational cost with a demonstrated, specific requirement it solves better than
extending the existing default (Postgres) would. Complexity that isn't justified
by a real, current need is a cost paid for no corresponding benefit.

**Q: How do you keep data consistent across multiple different data stores, given
you can't wrap one transaction across them?** ⭐⭐
You genuinely cannot use a single ACID transaction spanning two different database
technologies the way you can across tables within one relational database — this
has to be engineered deliberately instead. For a cache specifically, straightforward
invalidation (delete or update the cached key on write to the source of truth,
with TTL as a backstop) is usually sufficient. For propagating a change to other,
independent stores reliably, the **outbox pattern** is the standard solution: write
the actual change and an "outbox" record of that change to the primary database in
one ordinary, fully atomic transaction, then have a separate relay process publish
an event (typically via Kafka) from that outbox for other stores to consume and
update themselves from — this avoids ever having to write to two different systems
as a single non-atomic operation. **CQRS** takes this further by maintaining a
distinct, specialized read model (built from the same event stream) alongside the
authoritative write model. In every case, you accept **eventual consistency**
between the stores, and you design the propagated updates to be **idempotent**, so
that retries after a failure never produce a different, wrong result.

**Q: What is the dual-write problem, specifically, and how does the outbox pattern
solve it?** ⭐
The dual-write problem is what happens when an application tries to write the same
logical change directly to two different stores as two separate operations — for
example, updating an order in Postgres and then separately updating its document
in Elasticsearch. These two writes aren't atomic as a pair: the first can succeed
while the second fails (a crash between the two calls, a network error, a bug), and
now the two stores permanently disagree with no built-in mechanism to detect or
repair the divergence. The outbox pattern solves this by making the *only* thing
that needs to be atomic a write to a single database — the actual data change plus
an outbox row describing it, both written in one ordinary transaction against
Postgres alone. The risky "write to two different systems" step is eliminated
entirely; instead, a separate relay reliably reads the outbox and publishes events
that other stores consume at their own pace, tolerating and safely retrying any
failures on that side without ever risking the two stores diverging from an
unatomic dual write.

**Q: If you had to pick exactly one database to start a new project, which would
you choose, and why?**
PostgreSQL. It provides full relational modeling with ACID transactions for
correctness-critical data, and — via `JSONB`, full-text search, arrays, and range
types (Phase 6) — already covers a meaningful share of what teams historically
needed a separate document database or search engine for, all inside one
well-understood, extensively battle-tested, single system to operate. This covers
the large majority of real application needs on its own. The standard, lowest-risk
first addition once a concrete need appears is Redis, for caching and session
storage — introduced only once profiling or a specific requirement actually
justifies it, not preemptively.

**Q: Walk through how you'd model the same domain — users, purchases, friendships
— across the different database paradigms covered in this track.**
Relational (Postgres): normalized tables — `user`, `book`, `purchase` as a
junction table linking user and book, `friendship` as a junction table linking two
users — reassembled at query time with joins; best for transactional integrity and
ad-hoc queries you didn't fully anticipate up front. Document (MongoDB): a `user`
document that embeds that user's recent purchases directly, referencing books by
ID; fast, single-read access to one user's full recent activity, at the cost of
that data being denormalized. Key-value (Redis): cached user sessions, cached
individual book documents with a TTL, and a sorted set for a bestseller
leaderboard — explicitly a speed-focused complement to the system of record, never
the source of truth itself. Graph (Neo4j): users and books as nodes, with
`PURCHASED`, `FRIEND`, and `SIMILAR_TO` relationships as edges — the natural model
for "which of my friends bought this book" or degrees-of-separation style
recommendation questions. The takeaway to state explicitly: none of these four
models is objectively "correct" in isolation — each is the best fit for a
different dominant question you might ask of the same underlying facts.

**Q: Isn't adopting more database technologies sometimes just "resume-driven
development"?** *judgment question*
Often, genuinely, yes — and acknowledging that risk directly is a stronger answer
than pretending it doesn't exist. Every new datastore should be justified by a
demonstrated, specific problem the current stack can't solve well, not by a
technology being currently interesting to learn or impressive to list on a resume.
The disciplined approach: measure the actual need first (a genuinely slow query, a
concrete scale ceiling, a specific access pattern the current store handles
poorly), prefer extending what's already in place (PostgreSQL, typically) before
reaching for something new, and adopt a specialized store only when its specific
strength — horizontal write scale, deep relationship traversal, sub-millisecond
cache reads — is actually required for the problem in front of you. Fewer moving
parts, all else equal, is itself a feature of a system, not a limitation to be
engineered around.

**Q: How would you design the data layer for a ride-sharing app, and justify each
database choice?**
Walking through the framework: driver/rider accounts, trip records, and payments
are correctness-critical, relational data with real ad-hoc reporting needs
("revenue by city this month") — PostgreSQL. A driver's live location needs
extremely fast writes/reads and only the *current* value matters, not history —
Redis (a simple key per driver, or a geospatial-capable store), with TTL acting as
a natural "driver went offline" signal. Matching a rider to nearby available
drivers is fundamentally a geospatial/proximity query — either Postgres's
geospatial (PostGIS) extension or a specialized geospatial index, depending on
query volume. "Riders who take similar routes" or fraud-pattern detection across
accounts, devices, and payment methods is a relationship-traversal question — a
good candidate for Neo4j, if that analysis becomes a frequent, deep query rather
than an occasional offline report. Trip event history for analytics (completion
rates, surge pricing analysis) fits a columnar warehouse better than the
transactional database it originated in. The key move in answering a question like
this is naming the *specific* access pattern behind each piece of data before
naming a database for it — the justification is the substance of the answer, not
just the final list of technologies.
