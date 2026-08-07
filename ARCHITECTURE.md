<!-- nav -->
**[🏠 Repo Home](./README.md)**
<!-- /nav -->

# ARCHITECTURE — The One System That Ties All 17 Tracks Together

Every track in this repo teaches its topic on the **same running example: an
expense tracker.** This document is the thread promised in the root README —
it shows how the pieces built across 17 tracks compose into **one full-stack,
containerized, orchestrated, event-driven system**, and points at the real code
in each track.

The point: you didn't learn 17 topics in isolation — you built (or specified,
where no daemon was available) **one production-shaped system**, one layer at a time.

## The whole system at a glance

```
                                   ┌───────────────────────── USERS ─────────────────────────┐
                                   │                                                          │
                          ┌────────▼─────────┐                                       ┌────────▼────────┐
                          │  Next.js (09)    │   or   React SPA (08)   or   Angular  │  (10)           │
                          │  App Router      │        expense-web           expense-app                │
                          │  SSR + islands   │        (Vite+Vitest)         (standalone+signals)       │
                          └────────┬─────────┘                                                         │
                                   │  HTTPS (styled with Tailwind / Bootstrap — 11)                    │
                                   │                                                                   │
             ┌─────────────────────▼─────────────────────┐        events        ┌────────────────────▼───────┐
             │        Spring Boot API  (02)               │ ───────────────────▶ │        Kafka (16)          │
             │  Controller → Service → Repository         │  expense-events      │  topic, partitioned by     │
             │  (built on Java 01; SOLID/patterns 05)     │  (keyed by userId)   │  userId → consumers:       │
             │  DTO validation, JWT security, error model │                      │  analytics / notifications │
             └───────┬───────────────────────┬───────────┘                      └────────────────────────────┘
                     │ JPA (Spring Data)      │ cache-aside / sessions
              ┌──────▼───────┐         ┌──────▼───────┐
              │ PostgreSQL   │         │  Redis (15)  │
              │ (04)         │         │  cache +     │
              │ source of    │         │  sessions +  │
              │ truth        │         │  rate limit  │
              └──────────────┘         └──────────────┘

   ── all of the above runs as containers (12) ── built/tested/shipped by CI/CD (13) ──
   ── orchestrated, scaled & self-healed on Kubernetes (14) ──
   ── and reasoned about holistically by System Design (17): scaling, CAP, idempotency,
      reliability, rate limiting, observability, leaks, performance.

   Fundamentals under everything: DSA (03) for the algorithms/complexity;
   JavaScript (06) + TypeScript (07) as the language layer of the whole frontend.
```

## Follow one request through every track

A user adds an expense. Here's the path, and which track owns each step:

1. **UI** — the user clicks "Add" in the **Next.js (09)** / **React (08)** / **Angular
   (10)** frontend, styled with **Tailwind/Bootstrap (11)**, written in **TypeScript
   (07)** on top of **JavaScript (06)** fundamentals. A Server Action / controlled form
   / Reactive Form submits the data.
2. **Transport** — an HTTP POST hits the **Spring Boot API (02)**'s controller
   (`expense-api`), the Java capstone (**01**) reborn as REST.
3. **Validation & design** — DTO validation and a clean layered design
   (**Software Design 05**: SOLID, layered/hexagonal, patterns) keep the controller thin.
4. **Business logic** — the service applies rules (pricing/discount logic mirrors the
   Software Design capstone), using efficient data handling (**DSA 03** for any
   in-memory algorithms).
5. **Persistence** — Spring Data JPA writes to **PostgreSQL (04)** — the durable source
   of truth (schema, indexing, transactions all from track 04).
6. **Cache** — the read path uses **Redis (15)** cache-aside (`@Cacheable`), and the
   write evicts/refreshes it; sessions live in Redis so the app tier is stateless.
7. **Events** — the write publishes an `ExpenseEvent` to **Kafka (16)**, keyed by
   `userId` (per-user ordering); an analytics consumer group builds a read model
   idempotently.
8. **Runtime** — every component is a **Docker (12)** image; the whole stack boots with
   `docker compose up`.
9. **Delivery** — **CI/CD (13)** builds, tests (the API's tests), and ships those images
   on every commit/tag.
10. **Scale & resilience** — **Kubernetes (14)** runs the images as Deployments behind
    Services/Ingress, autoscaled by an HPA, with Postgres as a StatefulSet.
11. **Holistic reasoning** — **System Design (17)** is the theory over all of it:
    idempotency (step 7), rate limiting & caching (step 6), replication/sharding (step 5),
    reliability/observability, resource leaks, and performance.

## Track → where its capstone lives

| Track | Capstone artifact | Verified |
|---|---|---|
| 01 Java | `01-java/capstone/` (assertion-tested) | `java -ea` |
| 02 Spring Boot | `02-spring-boot/expense-api/` (REST API) | tests passing |
| 03 DSA | `03-dsa/capstone/` | `java -ea` |
| 04 Databases | `04-databases/capstone/` (bookstore, 4 paradigms) | correct-by-construction SQL |
| 05 Software Design | `05-software-design/capstone/OrderRefactor.java` | `java -ea` |
| 06 JavaScript | *(deferred to React — the JS capstone is the React app)* | — |
| 07 TypeScript | `07-typescript/capstone/expenseDomain.ts` | `tsx` + `tsc --noEmit` |
| 08 React | `08-react/expense-web/` | 6 Vitest tests |
| 09 Next.js | `09-nextjs/app-demo/` | `next build` |
| 10 Angular | `10-angular/expense-app/` | `ng build` |
| 11 CSS | `11-css-frameworks/{tailwind,bootstrap}-demo/` | Tailwind build |
| 12 Docker | `12-docker/examples/` (Dockerfiles + compose) | correct-by-construction |
| 13 CI/CD | `13-cicd/examples/` (Actions + Jenkinsfile) | correct-by-construction |
| 14 Kubernetes | `14-kubernetes/manifests/` | correct-by-construction |
| 15 Redis | `15-redis/examples/` (Spring cache/session) | correct-by-construction |
| 16 Kafka | `16-kafka/examples/` (producer/consumer) | correct-by-construction |
| 17 System Design | `17-system-design/phase-11-design-problems/` + runnable demos | `java -ea` |

## Verification philosophy (why some tracks run and some don't)

- **Runnable & verified live** where the toolchain exists: Java/`java -ea` (01, 03, 05,
  07-via-tsx, 17), Maven tests (02), Node builds/tests (07, 08, 09, 10, 11).
- **Theory + correct-by-construction artifacts** where no daemon is available in the
  environment (04 Postgres was live; 12 Docker, 13 CI runners, 14 K8s, 15 Redis, 16 Kafka
  have no daemon here) — real Dockerfiles / manifests / pipelines / Spring config written
  to production standards, with the commands to run them. This is the honest, explicit
  approach used consistently and noted in each track's README.

## Every lesson's format

Each phase carries a `NOTES.md` (the *why*, at interview depth) and an `INTERVIEW.md`
(⭐-graded Q&A), plus runnable code or real config. Navigation links between them are
generated by `scripts/add_nav.py`. See the [root README](./README.md) for the full
track index.

## The one-sentence summary

**One expense-tracker, built and reasoned about from the language up to the cluster:**
Java → Spring → a database → a typed frontend in three frameworks → containerized →
pipelined → orchestrated → cached → event-driven → and analyzed as a distributed system.
That end-to-end thread *is* the capstone of this repo.
