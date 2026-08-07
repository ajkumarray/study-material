import java.util.concurrent.atomic.AtomicInteger;

/*
 * System Design Phase 5 — Scaling the web tier: LOAD BALANCING + RATE LIMITING
 * Run:  java RateLimiterDemo.java
 *
 * Two web-tier essentials, made concrete:
 *   - Load-balancing ALGORITHMS decide which server gets each request.
 *   - RATE LIMITING protects the system from overload/abuse (the token-bucket
 *     algorithm — the most common one — implemented here).
 */
public class RateLimiterDemo {

    public static void main(String[] args) {
        System.out.println("=== load-balancing algorithms (which server gets the request?) ===");
        loadBalancers();

        System.out.println("\n=== token-bucket rate limiter ===");
        tokenBucket();
    }

    // ------------------------------------------------------------
    // LOAD BALANCERS distribute requests across servers. Common strategies:
    // ------------------------------------------------------------
    static void loadBalancers() {
        String[] servers = {"s1", "s2", "s3"};

        // Round-robin: rotate through servers in order. Simple, even for uniform work.
        System.out.print("  round-robin  : ");
        for (int i = 0; i < 6; i++) System.out.print(servers[i % servers.length] + " ");
        System.out.println();

        // Least-connections: send to the server with the fewest in-flight requests.
        // Better when request durations vary (avoids piling onto a busy server).
        int[] conns = {5, 2, 8};
        int min = 0;
        for (int i = 1; i < conns.length; i++) if (conns[i] < conns[min]) min = i;
        System.out.println("  least-conns  : conns" + java.util.Arrays.toString(conns)
                + " -> pick " + servers[min]);

        System.out.println("  others       : weighted (by capacity), IP-hash (sticky sessions),");
        System.out.println("                 least-response-time. L4 (transport) vs L7 (HTTP-aware).");
    }

    // ------------------------------------------------------------
    // TOKEN BUCKET: a bucket holds up to `capacity` tokens, refilled at a steady
    // `refillPerSec`. Each request takes one token; if the bucket is empty, the
    // request is REJECTED (HTTP 429). Allows bursts up to capacity, then steady
    // rate. Used by API gateways, Stripe, AWS, nginx.
    // ------------------------------------------------------------
    static class TokenBucket {
        private final double capacity, refillPerSec;
        private double tokens;
        private long lastNanos;

        TokenBucket(double capacity, double refillPerSec) {
            this.capacity = capacity; this.refillPerSec = refillPerSec;
            this.tokens = capacity; this.lastNanos = System.nanoTime();
        }
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

    static void tokenBucket() {
        // capacity 5, refill 10/sec. Fire 8 requests instantly, then more after a pause.
        TokenBucket bucket = new TokenBucket(5, 10);
        AtomicInteger allowed = new AtomicInteger(), rejected = new AtomicInteger();

        System.out.print("  burst of 8 (capacity 5): ");
        for (int i = 0; i < 8; i++) {
            boolean ok = bucket.allow();
            System.out.print(ok ? "OK " : "429 ");
            (ok ? allowed : rejected).incrementAndGet();
        }
        System.out.println("\n  -> " + allowed.get() + " allowed, " + rejected.get()
                + " rejected  (bursts capped at capacity)");

        sleep(500);   // 0.5s * 10/sec = 5 tokens refilled
        System.out.print("  after 0.5s refill, 6 more : ");
        int ok2 = 0;
        for (int i = 0; i < 6; i++) if (bucket.allow()) ok2++;
        System.out.println(ok2 + " allowed (steady rate resumes)");

        System.out.println("  variants: leaky bucket (smooth output), fixed/sliding window counters.");
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }
}
