# Tech Stack — SDE Learning Journey

A hands-on repo for learning software engineering, built one topic at a time on a
single running example (an expense tracker) that grows from a Java domain into a
full-stack, containerized, orchestrated, event-driven system.

> **Status: all 17 tracks complete.** ✅ See **[ARCHITECTURE.md](./ARCHITECTURE.md)**
> for how every track composes into one end-to-end system, and how to follow a single
> request through all of them.

## Roadmap

### Backend track
- [x] **01 — Java** — basics to advanced: OOP, collections, generics, streams, concurrency, JDBC (7 phases + capstone, all complete)
- [x] **02 — Spring Boot** — REST APIs, DI, Spring Data JPA, validation, error handling, testing (8 phases; `expense-api` = the Java capstone reborn as a REST API, 9 tests passing). Security/production as theory + wired actuator.

### Fundamentals
- [x] **03 — DSA deep dive** — complexity, arrays, linked lists, stacks/queues, hashing, recursion/backtracking, trees/BST, heaps, graphs, sorting/searching, DP, greedy (12 phases + capstone, all self-verifying in Java)
- [x] **04 — Databases** — full multi-model course COMPLETE (12 phases + capstone): SQL mastery, schema design/normalization, indexing/EXPLAIN, transactions/MVCC, PostgreSQL power features (JSONB/PLpgSQL/RLS), production/scaling, then NoSQL — MongoDB, Redis, Neo4j — and polyglot persistence. Taught on real PostgreSQL 16.
- [x] **05 — Software Design & Best Practices** — clean code, the SOLID principles, the Gang-of-Four design patterns (creational/structural/behavioral), architectural patterns (layered, MVC, hexagonal, DI), code smells & refactoring, testing discipline. Taught with Java examples; cross-references patterns already used in the Java capstone.

### Frontend track (running in parallel with backend)
- [x] **06 — JavaScript** — language fundamentals, closures, prototypes, async/await, the event loop (7 phases complete; capstone deferred to React)
- [x] **07 — TypeScript** — types, generics, narrowing, utility types, strict mode
- [x] **08 — React** — components, hooks, state management, rendering model (7 phases; `expense-web` = Vite+React 18 frontend for the Spring Boot API, 6 Vitest tests passing). Completes the full-stack loop: React → REST → JPA → Postgres.
- [x] **09 — Next.js** — routing, server components, SSR/SSG, API routes
- [x] **10 — Angular** — components, DI, RxJS, signals, modules vs standalone
- [x] **11 — CSS frameworks** — Tailwind CSS (utility-first) and Bootstrap (component-based)

### Infrastructure & DevOps track
- [x] **12 — Docker** — images, containers, Dockerfiles, docker-compose (containerize the Java capstone)
- [x] **13 — CI/CD** — concepts, then GitHub Actions and Jenkins pipelines (build/test/deploy the capstone)
- [x] **14 — Kubernetes** — pods, deployments, services, config, scaling (deploy the containerized capstone)

### Middleware & data systems track
- [x] **15 — Redis** — caching patterns, data structures, TTLs, session storage (wired into the Spring Boot app; data-store depth lives in the Databases track)
- [x] **16 — Kafka** — topics, producers/consumers, partitions, event-driven architecture (with Spring Boot)

### System design & backend engineering (capstone track)
- [x] **17 — System Design** — COMPLETE (11 phases): scalability & CAP, caching, DB replication/sharding, **concurrency & idempotency**, load balancing & rate limiting, reliability (retries/circuit breakers), **resource management & leaks**, **performance optimization**, messaging, observability, and classic design-interview problems — with runnable demos (idempotency, consistent hashing, rate limiter, circuit breaker, cache stampede).

The capstone project is the thread tying it together: build it in Java/Spring Boot, give it a frontend, then containerize it (Docker), automate it (CI/CD), orchestrate it (Kubernetes), scale it (Redis, Kafka), and reason about it holistically (System Design) — learning each tool on a real app instead of toy examples.

## Structure

Each topic lives in its own directory with a `README.md` curriculum, `NOTES.md` for concepts learned, and runnable code — exercises first, then a small capstone project per topic.

## How this works

- One topic at a time, each checked off above — now all complete.
- Learn by writing code, not just reading — every concept gets a runnable example
  (or, where no daemon is available, a real correct-by-construction artifact).
- Notes capture the *why*, not just the *what*; every phase pairs `NOTES.md` with an
  `INTERVIEW.md` of graded Q&A.
- See **[ARCHITECTURE.md](./ARCHITECTURE.md)** for the end-to-end picture.

## Environment

- Java: OpenJDK 21 (LTS)
- Build: Maven 3.8

<!-- tracks-nav -->
## 📂 Tracks — quick navigation

- [01 — Java Deep Dive](01-java/README.md)
- [02 — Spring Boot](02-spring-boot/README.md)
- [03 — Data Structures & Algorithms](03-dsa/README.md)
- [04 — Databases — Full Deep Dive](04-databases/README.md)
- [05 — Software Design & Best Practices](05-software-design/README.md)
- [06 — JavaScript Deep Dive](06-javascript/README.md)
- [07 — TypeScript Deep Dive](07-typescript/README.md)
- [08 — React](08-react/README.md)
- [09 — Next.js (App Router)](09-nextjs/README.md)
- [10 — Angular](10-angular/README.md)
- [11 — CSS Frameworks: Tailwind & Bootstrap](11-css-frameworks/README.md)
- [12 — Docker](12-docker/README.md)
- [13 — CI/CD](13-cicd/README.md)
- [14 — Kubernetes](14-kubernetes/README.md)
- [15 — Redis (as Middleware)](15-redis/README.md)
- [16 — Kafka (Event-Driven Architecture)](16-kafka/README.md)
- [17 — System Design & Backend Engineering](17-system-design/README.md)
<!-- /tracks-nav -->
