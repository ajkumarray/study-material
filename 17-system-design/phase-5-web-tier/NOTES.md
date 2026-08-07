<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · idempotency](../phase-4-idempotency/NOTES.md) | [Phase 6 · reliability ➡](../phase-6-reliability/NOTES.md)
<!-- /nav -->

# Phase 5 — Scaling the Web Tier: Notes

Once you scale horizontally (Phase 1), you need something to spread traffic across servers, keep those servers replaceable, and protect them from overload.

## 5.1 — Load balancing
A **load balancer (LB)** sits in front of your servers and distributes requests — enabling horizontal scaling, high availability (route around dead servers via health checks), and zero-downtime deploys (drain then update).
- **Algorithms:** *round-robin* (rotate — simple, even for uniform work); *least-connections* (fewest in-flight — better when request durations vary); *weighted* (by server capacity); *IP-hash / consistent-hash* (same client → same server, for stickiness); *least-response-time*.
- **L4 vs L7:** L4 (transport) balances by IP/port — fast, protocol-agnostic. L7 (application, HTTP-aware) can route by path/header/cookie, do TLS termination, and content-based routing — more features, slightly more overhead.
- **Reverse proxy** (nginx, Envoy, HAProxy) — often the LB, also doing TLS termination, compression, caching, and routing. Cloud LBs (ALB/NLB) are managed versions. The LB itself must not be a single point of failure (redundant pair + health checks + DNS/anycast).

## 5.2 — Statelessness & sessions
Horizontal scaling requires **stateless** app servers: any server can handle any request, so you can add/remove servers freely and a failure loses nothing. That means **no in-memory session state** tied to one server. Options for session/user state:
- **Externalize** it — store sessions in a shared store (Redis) or the DB; every server reads it. The standard approach.
- **Sticky sessions** (session affinity via IP-hash/cookie) — route a user to the same server. Simpler but breaks statelessness: a server death loses those sessions, and load can skew. Avoid when possible.
- **Client-side state** — a signed **JWT** (Spring Boot Phase 7) carries the identity/claims, so the server holds nothing. Great for stateless APIs.

## 5.3 — Autoscaling & rate limiting
- **Horizontal autoscaling:** add/remove server instances based on load metrics (CPU, QPS, queue depth) — scale out under spikes, in when quiet (cost). Requires statelessness + fast startup (containers/K8s — DevOps track). Watch for scale-up lag and thundering-herd on cold caches.
- **Rate limiting / throttling** protects against overload and abuse (and enforces quotas/tiers). Algorithms:
  - **Token bucket** (demo) — a bucket of `capacity` tokens refilled at a steady rate; each request spends one, empty → reject (HTTP **429**). Allows bursts up to capacity, then a steady rate. The most common (Stripe, AWS, nginx).
  - **Leaky bucket** — requests queue and drain at a fixed rate → smooths bursty output.
  - **Fixed-window counter** — count per time window; simple but allows 2× bursts at window edges. **Sliding-window** log/counter fixes the edge problem.
  - Distributed rate limiting uses a shared store (Redis) so limits hold across all servers, keyed by user/IP/API-key.

## 5.4 — API gateway & the request edge
An **API gateway** is the single entry point for clients, centralizing cross-cutting concerns so services don't each reimplement them: **authentication**, **rate limiting**, routing/aggregation, TLS, request/response transformation, and observability. Common in microservices (a client calls the gateway, which fans out to services). **API design/versioning** lives here too: version via the URL (`/v1/`) or headers; never break existing clients — add fields, don't remove; deprecate with notice. Pair with the REST principles from Spring Boot Phase 2 (resources, verbs, status codes, DTOs).

**The web-tier picture:** client → DNS → CDN (static) → load balancer / API gateway (TLS, auth, rate limit) → a pool of stateless app servers (autoscaled) → cache + database. Each layer is independently scalable and replaceable.
