<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · idempotency](../phase-4-idempotency/NOTES.md) | [Phase 6 · reliability ➡](../phase-6-reliability/NOTES.md)
<!-- /nav -->

# Phase 5 — Scaling the Web Tier: Notes

Once you've committed to horizontal scaling (Phase 1.1), you need three things: a way to
spread incoming traffic across many servers, a guarantee those servers are freely
interchangeable, and a way to protect them from being overwhelmed. This phase covers
load balancing, statelessness, autoscaling, rate limiting, and the API gateway — the
machinery that sits between the internet and your fleet of app servers.
`RateLimiterDemo.java` in this directory implements both a couple of load-balancing
algorithms and a real, runnable token-bucket rate limiter.

## 5.1 — Load balancing: algorithms

A load balancer (LB) sits in front of a pool of servers and decides, per request, which
server actually handles it.

### Key Concepts

- **Round-robin** — rotate through the server list in fixed order (server 1, 2, 3, 1, 2,
  3, ...). Simple and gives even distribution when every request costs roughly the same
  amount of work.
- **Least-connections** — route to whichever server currently has the fewest in-flight
  (unfinished) requests. Better than round-robin when request durations vary a lot,
  since it avoids piling new requests onto a server that's still busy with a slow one.
- **Weighted (round-robin or least-connections)** — assign each server a capacity weight
  (a bigger instance gets more traffic) so a heterogeneous fleet is still balanced
  proportionally to actual capacity, not just by request count.
- **IP-hash / consistent-hash** — hash the client's IP (or another key) to consistently
  route the same client to the same server. Provides session stickiness (5.2) without an
  external session store, at the cost of uneven load if client IPs aren't evenly
  distributed, and at the cost of losing that client's "session" if the server it was
  pinned to goes down.
- **Least-response-time** — route based on a combination of active connections and
  recently observed response time, adapting to servers that are slow for reasons beyond
  just connection count (e.g., a noisy-neighbor CPU spike).

### Worked example — `RateLimiterDemo.loadBalancers()`

```java
// Round-robin: rotate through servers in order.
String[] servers = {"s1", "s2", "s3"};
for (int i = 0; i < 6; i++) System.out.print(servers[i % servers.length] + " ");
// round-robin  : s1 s2 s3 s1 s2 s3

// Least-connections: send to the server with the fewest in-flight requests.
int[] conns = {5, 2, 8};
int min = 0;
for (int i = 1; i < conns.length; i++) if (conns[i] < conns[min]) min = i;
// least-conns  : conns[5, 2, 8] -> pick s2
```

Round-robin's `i % servers.length` guarantees a perfectly even rotation with no memory
of server state — it doesn't know or care that `s2` might currently be handling a
30-second report generation while `s1` and `s3` sit idle. Least-connections fixes
exactly that: with `conns = [5, 2, 8]` (5 active requests on `s1`, 2 on `s2`, 8 on `s3`),
it picks `s2` — the server with the smallest current load — rather than blindly
continuing a rotation that might hand the next request to the already-overloaded `s3`.

### Why it's useful

The algorithm choice is a direct trade between simplicity and adaptiveness: round-robin
needs zero state and works fine when requests are uniform; least-connections needs the
LB to track per-server in-flight counts but handles the far more realistic case of
variable request cost (some endpoints are a fast cache read, others a slow report
generation). Most production load balancers (nginx, Envoy, cloud ALBs) default to
something closer to least-connections or least-response-time precisely because uniform
request cost is the exception, not the rule.

## 5.2 — L4 vs. L7 load balancing, and the reverse proxy

### Key Concepts

- **L4 (transport layer)** — balances based on IP address and port, without looking at
  the actual HTTP content. Very fast (no need to parse the request), protocol-agnostic
  (works for any TCP/UDP traffic, not just HTTP), but can't make routing decisions based
  on the request's path, headers, or cookies.
- **L7 (application layer)** — HTTP-aware; can route based on URL path (`/api/*` to one
  pool, `/static/*` to another), headers, or cookies, and can terminate TLS at the LB so
  backend servers don't each need to manage certificates. More capable, with a small
  amount of added processing overhead per request.
- **Reverse proxy** — a server (nginx, Envoy, HAProxy) that sits in front of your app
  servers and forwards requests to them, often bundling load balancing with TLS
  termination, response compression, static-asset caching, and request routing in one
  component. Cloud-managed load balancers (AWS ALB/NLB, GCP's equivalents) are managed
  versions of the same idea.
- **The LB itself must not become a single point of failure** — a naive design puts one
  load balancer in front of many app servers and just relocates the SPOF instead of
  eliminating it. Real deployments run a redundant pair (or more) of load balancers,
  often fronted by DNS round-robin or anycast IP routing, with health checks between
  them so a failed LB is routed around too.

### Why it's useful

L4 vs. L7 is a genuine trade-off, not "L7 is strictly better": L4 is the right choice
when you need maximum throughput and minimum latency and don't need content-aware
routing (a raw TCP proxy in front of a database cluster, for instance); L7 is the right
choice for HTTP APIs where path-based routing, TLS termination, and cookie-based
stickiness are actually useful — which is most public-facing web traffic, hence why
nginx/Envoy/ALB (all L7-capable) dominate that space.

## 5.3 — Statelessness and sessions

Horizontal scaling (Phase 1.1) only works if any server can handle any request. That
single requirement drives everything in this section.

### Key Concepts

- **Stateless app server** — holds no server-local state tied to a particular client
  between requests. Any instance can serve any request; instances can be added, removed,
  or restarted freely with zero impact on in-progress user sessions (because there
  aren't any sessions "on" a given server to lose).
- **Why in-memory sessions break this** — if user data (login state, shopping cart) is
  stored in one app server's memory, only *that* server can correctly serve that user's
  subsequent requests. A load balancer routing to a different instance sees a "logged
  out" user; that server crashing loses the session outright.
- **Externalized sessions** — store session state in a shared store (Redis is the
  standard choice) or the database, keyed by a session id the client holds (usually in a
  cookie). Every app server reads from the same shared store, so any server can serve
  any user correctly. This is the standard, scalable approach.
- **Sticky sessions (session affinity)** — the load balancer routes a given client to
  the same server every time (via IP-hash or a routing cookie), so that server's local,
  in-memory session data stays valid for that client. Simpler to implement than
  externalizing state, but it genuinely breaks statelessness: that server dying loses
  those users' sessions, load can skew if some sessions are much heavier than others,
  and deploying a new version requires carefully draining sticky connections rather than
  just cycling instances freely.
- **Client-side state via JWT** — a signed JSON Web Token carries the user's identity
  and claims directly in the token itself (Spring Boot Phase 7); the server verifies the
  signature and reads the claims from the token on every request, holding no session
  state anywhere server-side. Excellent for stateless APIs, at the cost that revoking a
  single token before its expiry requires an extra mechanism (a blocklist), since the
  token is self-contained and normally trusted until it expires.

### Comparison table — session strategies

| | In-memory (local) | Sticky sessions | Externalized (Redis/DB) | JWT (client-side) |
|---|---|---|---|---|
| Stateless app tier? | No | Effectively no | Yes | Yes |
| Server crash impact | Session lost | Session lost for that server's users | None — any server reads it | None — token is self-contained |
| Extra infrastructure | None | LB affinity config | A shared store (Redis) | None (verification only) |
| Revocation | N/A (dies with session) | N/A | Delete the session record | Needs a blocklist mechanism |
| Scales freely? | No | Partially (skew risk) | Yes | Yes |

### Why it's useful

"Why must app servers be stateless to scale horizontally?" is one of the single most
commonly asked system-design questions precisely because it tests whether you understand
*why* horizontal scaling works, not just that it does. The answer chain — statelessness
requires externalizing anything that would otherwise live in server memory, and
externalizing it means either a shared store or pushing it entirely to the client — is
the direct, practical consequence of Phase 1.1's claim that "any server can handle any
request."

## 5.4 — Autoscaling

### Key Concepts

- **Horizontal autoscaling** — automatically add or remove server instances based on
  observed load metrics (CPU utilization, request rate/QPS, queue depth), scaling out
  under a traffic spike and scaling in when load drops to control cost. Requires the
  statelessness from 5.3 (a newly added instance must be immediately able to serve
  traffic correctly with zero warm-up state) and fast startup (containers/Kubernetes,
  DevOps track, are what makes "spin up a new instance in seconds" practical).
- **Scale-up lag** — there's a real delay between load increasing and a new instance
  actually being ready to serve traffic (container pull, app startup, health check
  passing) — during that window, existing instances absorb the extra load, which is why
  autoscaling thresholds are typically set to trigger *before* the fleet is fully
  saturated, not at the saturation point itself.
- **Thundering herd on cold caches** — when a burst of new instances comes online at
  once (a scale-out event, or a deploy replacing the whole fleet), every new instance
  starts with a cold local cache (Phase 2.2) and may simultaneously hit the database or
  a shared cache to warm up, which can itself cause a load spike right when the system
  was already under pressure. Mitigate with gradual rollout and by warming caches ahead
  of a known traffic event.

### Why it's useful

Autoscaling is what turns "provision for peak load, all the time" (expensive, and still
inadequate for an unanticipated spike) into "provision for typical load, and grow on
demand" — but it's only safe because of statelessness; autoscaling a stateful fleet
would mean new instances coming up with no user data and old instances being killed
mid-session, losing state either way.

## 5.5 — Rate limiting and throttling

Rate limiting protects a system from overload (whether from a traffic spike, a buggy
client retry loop, or deliberate abuse) and enforces usage quotas/tiers between clients.

### Key Concepts

- **Token bucket** — a bucket holds up to `capacity` tokens, refilled continuously at a
  steady `refillPerSec` rate; each request consumes one token, and an empty bucket
  rejects the request (HTTP **429 Too Many Requests**). This allows short bursts up to
  the bucket's capacity, followed by a steady sustained rate once the burst is consumed
  — the most widely used algorithm (Stripe, AWS API Gateway, nginx's `limit_req` all use
  variants of it).
- **Leaky bucket** — requests are queued and drained (processed) at a fixed rate,
  regardless of how bursty their arrival was — smooths *output* rather than just
  capping burst size, at the cost of added latency for queued requests.
- **Fixed-window counter** — count requests in discrete time windows (e.g., "max 100
  requests per minute, reset every minute on the clock"). Simple to implement, but
  allows up to double the intended rate right at a window boundary (100 requests in the
  last second of one window, then another 100 in the first second of the next — 200
  requests in ~2 seconds against a "100/minute" limit).
- **Sliding-window counter/log** — fixes the boundary problem by considering a moving
  window (either an exact log of recent request timestamps, or a weighted blend of the
  current and previous fixed windows) instead of a hard-reset clock boundary.
- **Distributed rate limiting** — a rate limit must be enforced *across the whole
  fleet*, not per server (otherwise a limit of "100/min" becomes "100/min × N servers"
  in practice). Implemented with a shared store (Redis) holding the counter/bucket state,
  keyed by whatever the limit applies to (user id, IP, API key), with atomic
  increment-and-check operations (or a Lua script for multi-step atomicity) so
  concurrent requests across servers don't race each other.

### Worked example — `RateLimiterDemo.TokenBucket` and `tokenBucket()`

```java
static class TokenBucket {
    private final double capacity, refillPerSec;
    private double tokens;
    private long lastNanos;

    synchronized boolean allow() {
        refill();
        if (tokens >= 1) { tokens -= 1; return true; }
        return false;                                   // -> 429 Too Many Requests
    }
    private void refill() {
        long now = System.nanoTime();
        double elapsed = (now - lastNanos) / 1e9;
        tokens = Math.min(capacity, tokens + elapsed * refillPerSec);
        lastNanos = now;
    }
}

// capacity 5, refill 10/sec. Fire 8 requests instantly:
TokenBucket bucket = new TokenBucket(5, 10);
// burst of 8 (capacity 5): OK OK OK OK OK 429 429 429
// -> 5 allowed, 3 rejected  (bursts capped at capacity)

sleep(500);   // 0.5s * 10/sec = 5 tokens refilled
// after 0.5s refill, 6 more : 5 allowed  (steady rate resumes)
```

The bucket starts full (`tokens = capacity = 5`). The first 5 of the 8 immediate
requests succeed, each consuming one token; by the 6th request, `tokens` has dropped
below 1 and `allow()` returns `false` — exactly capturing "bursts are fine up to
capacity, then you're throttled." `refill()` is called lazily on every `allow()` check
rather than on a background timer — it computes elapsed real time since the last check
and adds back `elapsed * refillPerSec` tokens (capped at `capacity`), which is why
waiting 500ms at a 10/sec refill rate restores 5 tokens exactly, letting the next batch
of requests through. This lazy-refill technique avoids needing a background thread per
bucket, which matters a lot when you have millions of buckets (one per user) in a real
system.

### Why it's useful

Rate limiting is what stands between a single misbehaving client (a retry loop with no
backoff, Phase 6.1) or a deliberate abuse attempt and your entire system's availability
for every other client. Token bucket specifically is favored because "allow reasonable
bursts, then settle to a sustained rate" matches how real client traffic actually
behaves — a user loading a page that fires 10 API calls at once is a legitimate burst,
not abuse, and a naive fixed-rate limiter would incorrectly throttle it.

## 5.6 — API gateway and request-edge design

### Key Concepts

- **API gateway** — the single entry point for external clients in a system with
  multiple backend services (especially microservices). It centralizes cross-cutting
  concerns so individual services don't each have to reimplement them: authentication/
  authorization, rate limiting, request routing and response aggregation, TLS
  termination, request/response transformation, and observability (logging, metrics,
  tracing — Phase 10) at one chokepoint.
- **API versioning** — version via the URL path (`/v1/orders`) or a header
  (`Accept-Version: 2`). The governing rule is **backward compatibility**: add new
  fields and endpoints freely, but never remove or repurpose an existing field/endpoint
  that a client might depend on — deprecate with advance notice and support the old
  version for a defined window before retiring it.
- **The full web-tier picture**: client → DNS → CDN (serves static/cacheable content
  directly, Phase 2.4) → load balancer / API gateway (TLS termination, authentication,
  rate limiting) → a pool of stateless, autoscaled app servers → cache → database
  (replicas/shards, Phase 3). Each layer is independently scalable and independently
  replaceable — a failure or a capacity limit at one layer doesn't require redesigning
  the layers around it.

### Why it's useful

Without a gateway, every backend service in a microservices architecture would need to
independently implement authentication, rate limiting, and TLS — both a maintenance
burden and a security risk (one service forgetting auth is a breach). Centralizing these
concerns at the edge means a single, carefully audited implementation protects every
service behind it, and lets backend teams focus purely on their service's business
logic.

## Summary / Key Takeaways

- **Load balancers** distribute traffic (round-robin, least-connections, weighted,
  IP-hash) and operate at **L4** (fast, protocol-agnostic) or **L7** (HTTP-aware:
  path/header routing, TLS termination) — the LB itself needs redundancy so it isn't a
  new single point of failure.
- **Statelessness is the prerequisite for horizontal scaling**: any server must be able
  to handle any request, which means externalizing session state (Redis/DB) or pushing
  it to the client (JWT) rather than keeping it in server memory; **sticky sessions**
  are a partial, imperfect workaround that reintroduces server affinity.
- **Autoscaling** adds/removes instances by load metrics, but only works safely on top
  of statelessness, and must account for scale-up lag and cold-cache thundering herds on
  newly added instances.
- **Rate limiting** (token bucket — demo: burst of 8 against capacity 5 → 5 allowed, 3
  rejected, then steady-rate refill) protects against overload/abuse; must be
  **distributed** (a shared store like Redis) to enforce a limit across the whole fleet
  rather than per server.
- An **API gateway** centralizes auth, rate limiting, routing, and TLS termination at
  the edge so backend services don't each reimplement them — pair with careful,
  backward-compatible **API versioning**.
