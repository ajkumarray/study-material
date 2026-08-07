<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · idempotency](../phase-4-idempotency/NOTES.md) | [Phase 6 · reliability ➡](../phase-6-reliability/NOTES.md)
<!-- /nav -->

# Phase 5 — Scaling the Web Tier: Interview Q&A

⭐ = asked constantly.

**Q: What does a load balancer do, and name some algorithms.** ⭐⭐
Distributes incoming requests across a pool of servers — enabling horizontal scaling, high availability (health-check routing around dead nodes), and zero-downtime deploys. Algorithms: round-robin, least-connections, weighted, IP/consistent-hash (stickiness), least-response-time.

**Q: L4 vs L7 load balancing?**
L4 balances at the transport layer (IP/port) — fast, protocol-agnostic. L7 is HTTP-aware — routes by path/header/cookie, terminates TLS, does content-based routing — more capable, slightly more overhead.

**Q: Why must app servers be stateless to scale horizontally?** ⭐⭐
So any server can handle any request: you can add/remove servers freely and a server failure loses nothing in flight. In-memory session state breaks this. Externalize state (Redis/DB), use a stateless JWT, or (least ideal) sticky sessions.

**Q: Sticky sessions vs externalized sessions?**
Sticky (affinity): route a user to the same server — simple, but a server death loses those sessions and load can skew. Externalized: sessions in a shared store (Redis) so any server serves any user — the scalable choice. JWTs push state to the client entirely.

**Q: How does a token-bucket rate limiter work?** ⭐⭐
A bucket holds up to `capacity` tokens, refilled at a steady rate; each request consumes one; empty bucket → reject (HTTP 429). Allows bursts up to capacity, then a steady rate (demo: burst of 8 with capacity 5 → 5 allowed, 3 rejected). Leaky bucket smooths output; fixed/sliding windows are counter-based alternatives.

**Q: How do you rate-limit across many servers?**
Centralize the counter in a shared store (Redis) keyed by user/IP/API-key, so the limit is global rather than per-server. Atomic increments (or a Lua script) keep it correct under concurrency; local caching reduces the shared-store load.

**Q: What status code for a rate-limited request?**
429 Too Many Requests, ideally with a `Retry-After` header. (503 for overload/unavailable.)

**Q: What is an API gateway and why use one?** ⭐
A single entry point that centralizes cross-cutting concerns — authentication, rate limiting, routing/aggregation, TLS termination, transformation, observability — so backend services don't each reimplement them. Common in microservices.

**Q: How do you version an API without breaking clients?**
Version in the URL (`/v1/`) or headers; make additive changes (add fields/endpoints), never remove or repurpose existing ones; deprecate with notice and support old versions for a window. Backward compatibility is the contract.

**Q: Sketch the path of a request through a scaled web tier.**
Client → DNS → CDN (static/cacheable) → load balancer / API gateway (TLS, auth, rate limit) → a pool of stateless, autoscaled app servers → cache → database (replicas/shards). Each layer scales and fails independently.
