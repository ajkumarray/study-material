import java.util.Arrays;

/*
 * Phase 12 — Greedy Algorithms & patterns wrap-up
 * Run:  java -ea GreedyPatterns.java
 *
 * A GREEDY algorithm makes the choice that looks best RIGHT NOW and never
 * reconsiders. It's simple and fast (often O(n log n) with a sort), but it's
 * only CORRECT when a local optimum leads to a global optimum ("greedy-choice
 * property" + optimal substructure). When it doesn't, you need DP (Phase 11).
 * Proving greedy is the hard part — usually an exchange argument.
 */
public class GreedyPatterns {

    public static void main(String[] args) {
        // interval scheduling: max non-overlapping meetings
        assert maxMeetings(new int[][]{{1, 3}, {2, 4}, {3, 5}, {0, 6}}) == 2;   // [1,3],[3,5]

        // merge overlapping intervals
        assert Arrays.deepToString(mergeIntervals(new int[][]{{1, 3}, {2, 6}, {8, 10}, {15, 18}}))
                .equals("[[1, 6], [8, 10], [15, 18]]");

        // jump game: can you reach the end?
        assert canJump(new int[]{2, 3, 1, 1, 4});
        assert !canJump(new int[]{3, 2, 1, 0, 4});

        // min jumps to reach the end
        assert minJumps(new int[]{2, 3, 1, 1, 4}) == 2;

        // max subarray (Kadane, revisited as a greedy/DP hybrid)
        assert maxProfit(new int[]{7, 1, 5, 3, 6, 4}) == 5;   // buy at 1, sell at 6

        // gas station circuit
        assert gasStation(new int[]{1, 2, 3, 4, 5}, new int[]{3, 4, 5, 1, 2}) == 3;

        System.out.println("All greedy tests passed.");
        demo();
    }

    // ============================================================
    // INTERVAL SCHEDULING — sort by END time, greedily take each meeting that
    // starts after the last one taken. Earliest-finish-first is provably optimal.
    // ============================================================
    static int maxMeetings(int[][] intervals) {
        Arrays.sort(intervals, (a, b) -> a[1] - b[1]);   // by end time
        int count = 0, lastEnd = Integer.MIN_VALUE;
        for (int[] iv : intervals) {
            if (iv[0] >= lastEnd) { count++; lastEnd = iv[1]; }   // take it
        }
        return count;
    }

    // MERGE INTERVALS — sort by START, then extend or emit.
    static int[][] mergeIntervals(int[][] intervals) {
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        java.util.List<int[]> merged = new java.util.ArrayList<>();
        for (int[] iv : intervals) {
            if (merged.isEmpty() || merged.get(merged.size() - 1)[1] < iv[0]) {
                merged.add(iv);                                    // no overlap -> new interval
            } else {
                merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], iv[1]);
            }
        }
        return merged.toArray(new int[0][]);
    }

    // ============================================================
    // JUMP GAME — track the FARTHEST reachable index as you scan. Greedy.
    // ============================================================
    static boolean canJump(int[] nums) {
        int farthest = 0;
        for (int i = 0; i < nums.length; i++) {
            if (i > farthest) return false;                        // stuck: can't reach i
            farthest = Math.max(farthest, i + nums[i]);
        }
        return true;
    }

    // Min jumps: greedily extend the current "jump window"; when you exhaust it,
    // you must have taken another jump. O(n).
    static int minJumps(int[] nums) {
        int jumps = 0, curEnd = 0, farthest = 0;
        for (int i = 0; i < nums.length - 1; i++) {
            farthest = Math.max(farthest, i + nums[i]);
            if (i == curEnd) { jumps++; curEnd = farthest; }       // window exhausted -> jump
        }
        return jumps;
    }

    // Best time to buy/sell stock — track the min price so far, greedily update
    // the best profit. (A one-pass greedy; Kadane's cousin.)
    static int maxProfit(int[] prices) {
        int minPrice = Integer.MAX_VALUE, best = 0;
        for (int p : prices) {
            minPrice = Math.min(minPrice, p);
            best = Math.max(best, p - minPrice);
        }
        return best;
    }

    // Gas station: if total gas >= total cost a solution exists; the start is
    // the index right after the point where the running tank dips lowest. O(n).
    static int gasStation(int[] gas, int[] cost) {
        int total = 0, tank = 0, start = 0;
        for (int i = 0; i < gas.length; i++) {
            int diff = gas[i] - cost[i];
            total += diff;
            tank += diff;
            if (tank < 0) { start = i + 1; tank = 0; }             // can't reach i+1 from `start`
        }
        return total >= 0 ? start : -1;
    }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("maxMeetings([[1,3],[2,4],[3,5],[0,6]]) -> " + maxMeetings(new int[][]{{1,3},{2,4},{3,5},{0,6}}));
        System.out.println("mergeIntervals(...)                   -> " + Arrays.deepToString(mergeIntervals(new int[][]{{1,3},{2,6},{8,10},{15,18}})));
        System.out.println("canJump([2,3,1,1,4]) / minJumps       -> " + canJump(new int[]{2,3,1,1,4}) + " / " + minJumps(new int[]{2,3,1,1,4}));
        System.out.println("maxProfit([7,1,5,3,6,4])              -> " + maxProfit(new int[]{7,1,5,3,6,4}));
    }
}
