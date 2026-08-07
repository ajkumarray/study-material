import java.util.concurrent.ThreadLocalRandom;

/*
 * System Design Phase 6 — Reliability & resilience
 * Run:  java ResilienceDemo.java
 *
 * At scale, dependencies WILL fail — networks blip, services get slow, nodes
 * die. Resilient systems expect failure and contain it. Two core patterns:
 *   RETRY WITH BACKOFF + JITTER — recover from transient failures without
 *     hammering a struggling service.
 *   CIRCUIT BREAKER — stop calling a failing dependency entirely for a while,
 *     so failures fail FAST instead of piling up (cascading failure).
 */
public class ResilienceDemo {

    public static void main(String[] args) {
        System.out.println("=== 1. RETRY with exponential backoff + jitter ===");
        retryWithBackoff();

        System.out.println("\n=== 2. CIRCUIT BREAKER ===");
        circuitBreaker();
    }

    // ============================================================
    // RETRY: a transient failure (timeout, blip) often succeeds on a retry. But
    // retry immediately and you add load to a struggling service. So wait, and
    // DOUBLE the wait each attempt (exponential backoff), plus random JITTER so
    // many clients don't retry in lockstep (a synchronized retry storm).
    // ONLY retry idempotent operations (Phase 4)!
    // ============================================================
    static void retryWithBackoff() {
        int maxAttempts = 5;
        long baseMs = 100;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (flakyCall(0.6)) {                       // 60% failure rate
                System.out.println("  attempt " + attempt + ": SUCCESS");
                return;
            }
            if (attempt == maxAttempts) { System.out.println("  attempt " + attempt + ": failed - giving up"); break; }
            long backoff = baseMs * (1L << (attempt - 1));          // 100, 200, 400, 800...
            long jitter = ThreadLocalRandom.current().nextLong(backoff / 2 + 1);  // +/- randomness
            long wait = backoff / 2 + jitter;
            System.out.printf("  attempt %d: failed - retry in ~%dms (backoff+jitter)%n", attempt, wait);
        }
    }

    // ============================================================
    // CIRCUIT BREAKER: wraps calls to a dependency. Three states:
    //   CLOSED    - calls pass through; count failures.
    //   OPEN      - too many failures -> REJECT immediately (fail fast) for a
    //               cooldown, giving the dependency time to recover.
    //   HALF_OPEN - after cooldown, allow ONE trial call; success -> CLOSED,
    //               failure -> OPEN again.
    // This prevents a slow/dead dependency from exhausting your threads/pools
    // and cascading the failure upstream.
    // ============================================================
    enum State { CLOSED, OPEN, HALF_OPEN }
    static class CircuitBreaker {
        State state = State.CLOSED;
        int failures = 0;
        final int threshold = 3;
        long openedAt = 0;
        final long cooldownMs = 300;

        boolean allowRequest() {
            if (state == State.OPEN) {
                if (System.currentTimeMillis() - openedAt >= cooldownMs) {
                    state = State.HALF_OPEN;             // time to test recovery
                    return true;
                }
                return false;                            // still open -> fail fast
            }
            return true;                                 // CLOSED or HALF_OPEN
        }
        void onSuccess() { failures = 0; state = State.CLOSED; }
        void onFailure() {
            failures++;
            if (state == State.HALF_OPEN || failures >= threshold) {
                state = State.OPEN; openedAt = System.currentTimeMillis();
            }
        }
    }

    static void circuitBreaker() {
        CircuitBreaker cb = new CircuitBreaker();
        int rejected = 0;
        // Phase A: dependency is DOWN (always fails) -> breaker trips OPEN.
        for (int i = 1; i <= 6; i++) {
            if (!cb.allowRequest()) { System.out.println("  call " + i + ": REJECTED (circuit OPEN - fail fast)"); rejected++; continue; }
            boolean ok = false;                          // simulate a dead dependency
            System.out.println("  call " + i + ": tried dependency -> FAIL (" + cb.state + ")");
            cb.onFailure();
        }
        System.out.println("  breaker state: " + cb.state + ", " + rejected + " calls failed fast (no thread wasted)");

        // Phase B: wait out the cooldown; dependency recovered -> half-open trial -> closed.
        sleep(350);
        if (cb.allowRequest()) {                         // HALF_OPEN trial
            System.out.println("  after cooldown: HALF_OPEN trial -> dependency healthy -> SUCCESS");
            cb.onSuccess();
        }
        System.out.println("  breaker state: " + cb.state + " (recovered)");
        System.out.println("  real libs: Resilience4j / Spring Cloud Circuit Breaker.");
    }

    static boolean flakyCall(double failRate) { return ThreadLocalRandom.current().nextDouble() >= failRate; }
    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }
}
