import java.util.Arrays;

/*
 * Phase 11 — Dynamic Programming
 * Run:  java -ea DynamicProgramming.java
 *
 * DP applies when a problem has:
 *   1. OVERLAPPING SUBPROBLEMS — the same smaller problems recur (naive fib
 *      recomputes fib(3) many times), AND
 *   2. OPTIMAL SUBSTRUCTURE — the optimal answer is built from optimal answers
 *      to subproblems.
 * The fix: solve each subproblem ONCE and store it.
 *   MEMOIZATION (top-down): recursion + a cache.
 *   TABULATION (bottom-up): fill a table from base cases up.
 * DP turns exponential brute force into polynomial. The hard part is finding
 * the STATE and the RECURRENCE ("what does dp[i] mean, and how from smaller?").
 */
public class DynamicProgramming {

    public static void main(String[] args) {
        // 1D
        assert climbStairs(5) == 8;               // Fibonacci-shaped
        assert houseRobber(new int[]{2, 7, 9, 3, 1}) == 12;   // 2+9+1
        assert coinChange(new int[]{1, 2, 5}, 11) == 3;       // 5+5+1
        assert longestIncreasingSubsequence(new int[]{10, 9, 2, 5, 3, 7, 101, 18}) == 4;  // 2,3,7,101

        // 2D
        assert uniquePaths(3, 3) == 6;
        assert longestCommonSubsequence("abcde", "ace") == 3;  // "ace"
        assert editDistance("horse", "ros") == 3;

        // knapsack
        assert knapsack01(new int[]{1, 3, 4, 5}, new int[]{1, 4, 5, 7}, 7) == 9;  // items 3,4 -> w4+w3=7,v5+4=9
        assert canPartition(new int[]{1, 5, 11, 5});           // {1,5,5} vs {11}

        // memoization vs the naive exponential
        assert fibMemo(40) == 102334155L;

        System.out.println("All DP tests passed.");
        demo();
    }

    // ============================================================
    // 1D DP — dp[i] depends on a few earlier entries.
    // ============================================================

    // Climbing stairs: ways(i) = ways(i-1) + ways(i-2). Fibonacci in disguise.
    static int climbStairs(int n) {
        int prev2 = 1, prev1 = 1;                 // rolling variables -> O(1) space
        for (int i = 2; i <= n; i++) { int cur = prev1 + prev2; prev2 = prev1; prev1 = cur; }
        return prev1;
    }

    // House robber: dp[i] = max(skip i -> dp[i-1], rob i -> dp[i-2] + nums[i]).
    static int houseRobber(int[] nums) {
        int prev2 = 0, prev1 = 0;
        for (int x : nums) { int cur = Math.max(prev1, prev2 + x); prev2 = prev1; prev1 = cur; }
        return prev1;
    }

    // Coin change (fewest coins): dp[amt] = 1 + min over coins of dp[amt-coin].
    static int coinChange(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        Arrays.fill(dp, amount + 1);              // "infinity" sentinel
        dp[0] = 0;
        for (int a = 1; a <= amount; a++)
            for (int c : coins)
                if (c <= a) dp[a] = Math.min(dp[a], 1 + dp[a - c]);
        return dp[amount] > amount ? -1 : dp[amount];
    }

    // Longest increasing subsequence: dp[i] = longest ending at i. O(n^2)
    // (an O(n log n) patience-sorting version exists).
    static int longestIncreasingSubsequence(int[] a) {
        int[] dp = new int[a.length];
        Arrays.fill(dp, 1);
        int best = 1;
        for (int i = 1; i < a.length; i++) {
            for (int j = 0; j < i; j++)
                if (a[j] < a[i]) dp[i] = Math.max(dp[i], dp[j] + 1);
            best = Math.max(best, dp[i]);
        }
        return best;
    }

    // ============================================================
    // 2D DP — dp[i][j] over two dimensions (two strings, a grid).
    // ============================================================

    // Unique grid paths (only right/down): dp[i][j] = dp[i-1][j] + dp[i][j-1].
    static int uniquePaths(int m, int n) {
        int[][] dp = new int[m][n];
        for (int[] row : dp) row[0] = 1;
        for (int j = 0; j < n; j++) dp[0][j] = 1;
        for (int i = 1; i < m; i++)
            for (int j = 1; j < n; j++)
                dp[i][j] = dp[i - 1][j] + dp[i][j - 1];
        return dp[m - 1][n - 1];
    }

    // Longest common subsequence: if chars match, 1 + diagonal; else max(up, left).
    static int longestCommonSubsequence(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++)
                dp[i][j] = (a.charAt(i - 1) == b.charAt(j - 1))
                        ? dp[i - 1][j - 1] + 1
                        : Math.max(dp[i - 1][j], dp[i][j - 1]);
        return dp[a.length()][b.length()];
    }

    // Edit distance (Levenshtein): min insert/delete/replace to turn a into b.
    static int editDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;   // delete all
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;   // insert all
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++)
                dp[i][j] = (a.charAt(i - 1) == b.charAt(j - 1))
                        ? dp[i - 1][j - 1]
                        : 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
        return dp[a.length()][b.length()];
    }

    // ============================================================
    // KNAPSACK family — choose items under a capacity constraint.
    // ============================================================

    // 0/1 knapsack: each item taken or not. dp[w] = best value at capacity w.
    static int knapsack01(int[] weights, int[] values, int capacity) {
        int[] dp = new int[capacity + 1];
        for (int i = 0; i < weights.length; i++)
            for (int w = capacity; w >= weights[i]; w--)   // iterate DOWN so each item used once
                dp[w] = Math.max(dp[w], dp[w - weights[i]] + values[i]);
        return dp[capacity];
    }

    // Partition equal subset sum = 0/1 knapsack for target = totalSum/2.
    static boolean canPartition(int[] nums) {
        int sum = Arrays.stream(nums).sum();
        if (sum % 2 != 0) return false;
        int target = sum / 2;
        boolean[] dp = new boolean[target + 1];
        dp[0] = true;
        for (int x : nums)
            for (int t = target; t >= x; t--)
                dp[t] = dp[t] || dp[t - x];
        return dp[target];
    }

    // Memoized Fibonacci — top-down DP; contrast the O(2^n) naive version (Phase 6).
    static long fibMemo(int n) {
        long[] memo = new long[n + 1];
        Arrays.fill(memo, -1);
        return fibH(n, memo);
    }
    static long fibH(int n, long[] memo) {
        if (n < 2) return n;
        if (memo[n] != -1) return memo[n];        // cache hit -> no recomputation
        return memo[n] = fibH(n - 1, memo) + fibH(n - 2, memo);
    }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("climbStairs(5)                    -> " + climbStairs(5));
        System.out.println("coinChange([1,2,5], 11)           -> " + coinChange(new int[]{1,2,5}, 11));
        System.out.println("LCS(\"abcde\",\"ace\")               -> " + longestCommonSubsequence("abcde", "ace"));
        System.out.println("editDistance(\"horse\",\"ros\")       -> " + editDistance("horse", "ros"));
        System.out.println("knapsack01(w[1,3,4,5],v[1,4,5,7],7)-> " + knapsack01(new int[]{1,3,4,5}, new int[]{1,4,5,7}, 7));
    }
}
