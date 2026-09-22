<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · idempotency](../phase-4-idempotency/NOTES.md) | [Phase 6 · reliability ➡](../phase-6-reliability/NOTES.md)
<!-- /nav -->

# Phase 5 — Scaling the Web Tier: Interview Q&A

⭐ = asked constantly.

**Q: What does a load balancer do, and walk through the common algorithms?** ⭐⭐
It sits in front of a pool of servers and decides, per incoming request, which server
handles it — enabling horizontal scaling, high availability (routing around servers that
fail health checks), and zero-downtime deploys (drain traffic from an instance, update
it, add it back). Round-robin rotates through servers in fixed order — simple, and even
when every request costs about the same. Least-connections routes to whichever server
currently has the fewest in-flight requests — better when request durations vary, since
it avoids stacking new requests onto a server still working through a slow one (the
demo picks the server with `conns = [5, 2, 8]` → index 1, the one with only 2 active
connections). Weighted variants of either account for a heterogeneous fleet where some
servers have more capacity. IP-hash/consistent-hash routes the same client to the same
server consistently, providing session stickiness at the cost of potential load skew.

**Q: L4 vs. L7 load balancing — what's the actual difference and when would you pick
each?**
L4 (transport layer) balances purely on IP and port, without parsing the HTTP content —
fast and protocol-agnostic, works for any TCP/UDP traffic. L7 (application layer) is
HTTP-aware and can route by path, header, or cookie, and can terminate TLS at the load
balancer so individual backend servers don't each manage certificates — more capable, at
a small added-processing cost per request. Pick L4 when you need maximum raw throughput
and don't need content-aware routing (fronting a database cluster, for instance); pick
L7 — which is what most public HTTP APIs actually use — when you need path-based
routing, centralized TLS termination, or cookie-based stickiness.

**Q: Why must app servers be stateless to scale horizontally?** ⭐⭐
Horizontal scaling assumes any server can handle any request — that's what lets you add
or remove instances freely and lets a load balancer route without caring which specific
server it picks. If a server holds session data (login state, a shopping cart) in its
own memory, only that one server can correctly serve that user again; a request routed
to a different instance sees a "logged out" state, and that server crashing loses the
session entirely. So statelessness isn't a style preference — it's a direct requirement
of the scaling model, and it forces you to externalize anything that would otherwise
live in server memory: either a shared store like Redis that every instance reads, or a
self-contained token (JWT) that pushes the state to the client entirely.

*Follow-up: sticky sessions vs. externalized sessions — why isn't sticky the default
answer?* Sticky sessions (routing a client to the same server via IP-hash or a routing
cookie) let you keep the simplicity of in-memory session storage while still technically
running multiple servers, but they don't actually deliver the benefits of statelessness:
that server dying still loses those users' sessions, load can skew if some sessions are
heavier than others, and rolling deploys require carefully draining sticky connections
instead of just cycling instances freely. Externalized sessions in Redis (or a JWT) give
you true statelessness — any server, any request, no affinity required — which is why
it's the standard, scalable answer despite needing an extra piece of infrastructure.

**Q: How does a token-bucket rate limiter work, and why is it preferred over a simple
fixed-window counter?** ⭐⭐
A bucket holds up to `capacity` tokens, continuously refilled at a steady rate; each
request consumes one token, and an empty bucket rejects the request with HTTP 429. This
allows legitimate short bursts up to the bucket's capacity (a page load firing several
API calls at once, for example) followed by a steady sustained rate once the burst is
used up — in the demo, a bucket with capacity 5 and refill rate 10/sec allows the first 5
of 8 immediate requests and rejects 3, then after a 500ms wait (5 tokens refilled at
10/sec) allows another batch through. A fixed-window counter (e.g., "max 100 requests
per minute, reset on the clock") is simpler but has a boundary flaw: a client can send
100 requests in the last second of one window and another 100 in the first second of the
next, getting 200 requests through in about 2 seconds against a nominal "100/minute"
limit. Leaky bucket and sliding-window counters are the other standard variants — leaky
bucket smooths the *output* rate by queuing and draining at a fixed rate (adds latency
for queued requests); sliding-window fixes the fixed-window boundary problem by
considering a moving window instead of a hard clock reset.

**Q: How do you rate-limit correctly across many application servers?** ⭐
You can't keep the counter/bucket state in each server's local memory — a "100/min"
limit enforced per-server independently becomes "100/min × N servers" in practice for
any client hitting the fleet through a load balancer. The fix is centralizing the state
in a shared store, typically Redis, keyed by whatever dimension the limit applies to
(user id, IP, API key), with atomic increment-and-check operations — or a Lua script
when the check-and-update needs to be multi-step-atomic — so concurrent requests landing
on different app servers don't race each other and double-allow requests that should
have been rejected. Some systems accept a small amount of local, per-server
approximate counting (synced to the shared store periodically) as a performance
trade-off against hitting Redis on every single request.

**Q: What HTTP status code should a rate-limited request get, and what else should the
response include?**
`429 Too Many Requests`, ideally with a `Retry-After` header telling the client how long
to wait before trying again — this lets well-behaved clients back off automatically
instead of immediately retrying and making the overload worse. (`503 Service
Unavailable` is the related but distinct code for general overload/unavailability, not
specifically a per-client rate limit.)

**Q: What is an API gateway and why introduce one instead of having clients call
services directly?** ⭐
An API gateway is the single entry point for external clients in a system with multiple
backend services. It centralizes cross-cutting concerns — authentication/authorization,
rate limiting, routing and response aggregation, TLS termination, request/response
transformation, and observability — so individual backend services don't each have to
reimplement (and potentially get wrong) the same security and operational logic. Without
a gateway, every service independently handling auth means one service forgetting it is
a security breach; centralizing it at the edge means a single, carefully audited
implementation protects everything behind it.

**Q: How do you version an API without breaking existing clients?**
Version explicitly, either in the URL path (`/v1/orders`) or via a header. The governing
principle is backward compatibility: make additive changes only — new fields, new
optional parameters, new endpoints — and never remove, rename, or change the meaning of
an existing field or endpoint that clients may already depend on. When a breaking change
is genuinely necessary, ship it as a new version, deprecate the old one with clear
advance notice, and support both for a defined transition window before retiring the
old version. The contract with existing clients is the constraint you're designing
around, not a guideline.

**Q: Sketch the path of a request through a fully scaled web tier.** ⭐
Client → DNS resolves to the service → CDN serves anything static or cacheable directly
at the edge, never reaching your infrastructure at all → load balancer / API gateway
handles TLS termination, authentication, and rate limiting → a pool of stateless,
autoscaled app servers, any of which can handle the request → a cache (Redis or
in-process) absorbs read load before it hits → the database, which itself may be
replicated (read scaling) and sharded (write/storage scaling). Every layer here scales
and fails independently of the others — a database replica going down doesn't take out
the app tier, and adding app server capacity doesn't require touching the database
layer at all.

**Q: What would you watch for when autoscaling a fleet under a sudden traffic spike?**
Two specific risks beyond "just add instances": scale-up lag — there's a real delay
between load rising and a new instance actually being ready to serve traffic (image
pull, app startup, passing its health check), so existing instances have to absorb the
extra load during that window, which is why autoscaling thresholds should trigger before
the fleet is fully saturated, not at the saturation point. And a thundering herd on cold
caches — a burst of newly started instances each begin with an empty local cache (Phase
2.2) and may simultaneously hit the database or shared cache to warm up, which can itself
create a secondary load spike right when the system is already under pressure from the
original traffic surge.
