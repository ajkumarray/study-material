# Databases Capstone — One Domain, Four Paradigms

The finale of the Databases track: take **one domain** (an online bookstore —
users buy books; books have authors and genres; users befriend users) and model
it across all four paradigms, then contrast which fits which access pattern. This
synthesizes every phase.

## Part A — Relational (PostgreSQL) — `part-a-postgres.sql` (runnable)
A complete, self-contained script exercising the whole relational half:
- **Schema design (Phase 3):** 3NF tables (`author`, `book`, `app_user`, `purchase` junction) with PK/FK/`CHECK`/`UNIQUE` constraints.
- **Analytical queries (Phase 2):** revenue per genre (GROUP BY), **top-seller per genre** (window `ROW_NUMBER`), per-user running total (window `SUM OVER`).
- **Indexing (Phase 4):** composite + FK indexes, `EXPLAIN ANALYZE` to prove an Index Scan.
- **Transactions (Phase 5):** an atomic "buy a book" — debit balance, decrement stock (a `CHECK` prevents overselling), insert the purchase — with `FOR UPDATE` locks, all-or-nothing.

Run it: `psql -U <user> -d <db> -f part-a-postgres.sql`.

## Part B — The same domain in NoSQL

### Document (MongoDB) — Phase 9
Model around **reads**. A `user` document embeds recent purchases; books are a
separate collection (shared, unbounded → referenced):
```js
// users
{ _id: 42, email: "ajay@dev.io",
  recentPurchases: [ { bookId: 1, title: "Clean Code", qty: 2, at: ISODate() } ] }
// books
{ _id: 1, title: "Clean Code", author: { name: "Robert Martin" }, genre: "tech", price: 38.5 }
```
- **Fast:** "show a user's profile + recent purchases" is a single-document read (no join).
- **Analytics:** revenue per genre via the **aggregation pipeline** (`$lookup` purchases→books, `$group` by genre).
- **Trade-off:** the denormalized title in `recentPurchases` must be updated if a title changes; no cross-document ACID by default.

### Key-value (Redis) — Phase 10
A **complement**, for speed and derived data (not the source of truth):
```
SET  book:1  '{"title":"Clean Code","price":38.5}'  EX 300     # cache-aside (hot book)
HSET session:xyz user 42 expires ...                            # login session (+ TTL)
ZADD bestsellers 3 "Clean Code" 1 "Refactoring"                 # sorted-set leaderboard
INCR views:book:1                                               # atomic counter
```
- **Fast:** cached books (sub-ms), sessions, a live bestsellers leaderboard (`ZREVRANGE`).
- **Role:** sits in front of Postgres; invalidate the cache on writes; never the durable record.

### Graph (Neo4j) — Phase 11
Model the **connections**, for recommendations and social features:
```cypher
(:User {email})-[:PURCHASED {qty}]->(:Book {title, genre})
(:User)-[:FRIEND]->(:User)
(:Book)-[:BY]->(:Author)
```
- **The graphy queries relational struggles with:**
  - "Friends who bought this book" — a 2-hop pattern.
  - "Recommended for you" — users who bought what you bought, also bought X (3 hops).
  - "Degrees of separation" — `shortestPath`.
- **Role:** powers the recommendation/social part; runs alongside Postgres.

## The contrast — which paradigm wins which query

| Query / need | Best fit | Why |
|---|---|---|
| Buy a book atomically (debit + stock + record) | **PostgreSQL** | ACID transaction, `CHECK` constraints, locks |
| Ad-hoc report (revenue per genre, top-N) | **PostgreSQL** | joins + window functions |
| Show a user + their recent purchases (one read) | **MongoDB** | embedded document, no join |
| Serve a hot book / session / view count (sub-ms) | **Redis** | in-memory, TTL, atomic counters |
| Live bestsellers leaderboard | **Redis** | sorted set |
| "Friends who bought this" / recommendations | **Neo4j** | multi-hop traversal |

## The takeaway
The **same domain** has different "best" databases depending on the question you
ask most. **Relational (Postgres) is the correct default** — it handles the
transactional core and ad-hoc analytics, and with JSONB/FTS covers much more.
The NoSQL stores are **specialists** you add deliberately (Redis for speed,
Mongo for document-shaped reads, Neo4j for connections) — the **polyglot
persistence** of Phase 12, kept in sync via events/cache-invalidation and
accepting eventual consistency between stores. Choose for the access pattern,
default to Postgres, and add specialists only for a real, measured need.
