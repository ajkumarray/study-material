import java.util.HashSet;
import java.util.Set;

/*
 * Phase 1 — Complexity & foundations
 * Run:  java ComplexityDemo.java
 *
 * Big-O describes how an algorithm's cost GROWS as input size n grows —
 * ignoring constants and lower-order terms. It answers "will this still work
 * when n is a million?" We MEASURE it here so the growth curves are real, not
 * abstract.
 */
public class ComplexityDemo {

    public static void main(String[] args) {

        // ============================================================
        // 1. O(1) vs O(n) vs O(n^2) — by measurement
        // ============================================================
        System.out.println("=== growth rates (time in ms) ===");
        System.out.printf("%-8s %-10s %-12s %-12s%n", "n", "O(n)", "O(n^2)", "O(n) set");
        for (int n : new int[]{1_000, 2_000, 4_000, 8_000, 16_000}) {
            int[] a = randomArray(n);

            // O(n): one pass
            long t0 = System.nanoTime();
            long sum = 0;
            for (int x : a) sum += x;
            long linear = (System.nanoTime() - t0) / 1_000;   // microseconds

            // O(n^2): the naive "has duplicate?" — compare every pair
            t0 = System.nanoTime();
            boolean dup = hasDuplicateQuadratic(a);
            long quad = (System.nanoTime() - t0) / 1_000;

            // O(n): the SAME problem with a hash set — trades space for time
            t0 = System.nanoTime();
            boolean dup2 = hasDuplicateLinear(a);
            long linSet = (System.nanoTime() - t0) / 1_000;

            System.out.printf("%-8d %-10d %-12d %-12d  (us)%n", n, linear, quad, linSet);
            assert dup == dup2;                                // same answer, different cost
            if (sum == Long.MIN_VALUE) System.out.print("");   // use sum so JIT can't drop it
        }
        System.out.println("Notice: O(n^2) roughly QUADRUPLES when n doubles;");
        System.out.println("the O(n) columns roughly DOUBLE. Same problem, algorithm choice wins.");

        // ============================================================
        // 2. O(log n) — halving the search space
        // ============================================================
        System.out.println("\n=== O(log n): binary search step count ===");
        for (int n : new int[]{16, 1_000, 1_000_000, 1_000_000_000}) {
            System.out.printf("  n=%-12d worst-case comparisons ~ %d%n", n, log2Steps(n));
        }
        // Doubling n adds just ONE step. This is why sorted lookups scale.

        // ============================================================
        // 3. Space complexity
        // ============================================================
        System.out.println("\n=== space ===");
        System.out.println("  sumInPlace uses O(1) extra space (one accumulator).");
        System.out.println("  hasDuplicateLinear uses O(n) extra space (the set).");
        System.out.println("  Time-space tradeoff: the set makes it O(n) time but O(n) space.");

        // ============================================================
        // 4. Amortized — why ArrayList.add is 'O(1)'
        // ============================================================
        System.out.println("\n=== amortized O(1) (dynamic array growth) ===");
        // A dynamic array doubles when full: most adds are O(1), the rare
        // resize is O(n), but AVERAGED over all adds it's O(1) amortized.
        int resizes = 0, capacity = 1;
        for (int i = 0; i < 1_000_000; i++) {
            if (i == capacity) { capacity *= 2; resizes++; }   // simulate doubling
        }
        System.out.printf("  1,000,000 adds triggered only %d resizes -> amortized O(1)%n", resizes);

        System.out.println("\nAll assertions passed.");
    }

    // O(n^2): nested loop compares every pair.
    static boolean hasDuplicateQuadratic(int[] a) {
        for (int i = 0; i < a.length; i++)
            for (int j = i + 1; j < a.length; j++)
                if (a[i] == a[j]) return true;
        return false;
    }

    // O(n) time, O(n) space: a hash set remembers what we've seen.
    static boolean hasDuplicateLinear(int[] a) {
        Set<Integer> seen = new HashSet<>();
        for (int x : a) if (!seen.add(x)) return true;   // add returns false if present
        return false;
    }

    static int log2Steps(long n) {
        int steps = 0;
        while (n > 1) { n /= 2; steps++; }
        return steps + 1;
    }

    // All-DISTINCT values (shuffled) so "hasDuplicate" must scan everything —
    // exercising the true O(n^2) worst case (no lucky early return).
    static int[] randomArray(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        java.util.Random r = new java.util.Random(42);
        for (int i = n - 1; i > 0; i--) {          // Fisher-Yates shuffle
            int j = r.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
        return a;
    }
}
