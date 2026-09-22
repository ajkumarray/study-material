<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · sorting searching](../phase-10-sorting-searching/NOTES.md) | [Phase 12 · greedy ➡](../phase-12-greedy/NOTES.md)
<!-- /nav -->

# Phase 11 — Dynamic Programming: Interview Q&A + Problems

⭐ = asked constantly. DP is the most feared category; the method below tames it.

**Q: What is dynamic programming? When does it apply?** ⭐⭐

Dynamic programming solves a problem by breaking it into subproblems, solving each **exactly once**, and reusing that stored result every time the same subproblem recurs — converting what would otherwise be exponential brute-force recursion into polynomial time. It applies when a problem has both **overlapping subproblems** (the same smaller subproblem is reached via multiple different call paths — naive Fibonacci recomputes `fib(3)` many times as `n` grows) and **optimal substructure** (the optimal answer to the whole problem is built directly from optimal answers to its subproblems, without needing to consider suboptimal subproblem combinations).

```java
static long fibNaive(int n) {
    if (n < 2) return n;
    return fibNaive(n - 1) + fibNaive(n - 2);   // fib(n-2) recomputed from both branches -> O(2^n)
}
```

*Follow-up: Give a problem that has optimal substructure but NOT overlapping subproblems, and explain why DP wouldn't help there.* Merge sort's divide step: the optimal sorted result is built from the optimal sorted results of the two halves (optimal substructure holds), but the two halves are always disjoint segments of the array — there's no subproblem that recurs across different branches of the recursion, so there's nothing to cache. Adding memoization to merge sort would add overhead for zero benefit.

---

**Q: Memoization vs tabulation?** ⭐⭐

Memoization is top-down: write the natural recursive brute-force solution, add a cache checked at the top of the function and populated before returning — it computes lazily, touching only the states actually reached by the specific input. Tabulation is bottom-up: fill a table iteratively from the base cases upward in dependency order, with no recursion at all — it computes every state in the table (unless extra logic skips some), but avoids recursion/stack overhead entirely and is much easier to space-optimize (collapse to rolling variables once you see a state only depends on a small, fixed window of previous states).

```java
// Memoization
static long fibH(int n, long[] memo) {
    if (n < 2) return n;
    if (memo[n] != -1) return memo[n];
    return memo[n] = fibH(n - 1, memo) + fibH(n - 2, memo);
}
// Tabulation (space-optimized)
static int climbStairs(int n) {
    int prev2 = 1, prev1 = 1;
    for (int i = 2; i <= n; i++) { int cur = prev1 + prev2; prev2 = prev1; prev1 = cur; }
    return prev1;
}
```

Both are the same asymptotic time, O(states × transition cost). Memoization mirrors the brute force most closely (easiest to derive under time pressure); tabulation avoids Java's `StackOverflowError` risk on deep recursion and unlocks space optimization more naturally.

*Follow-up: For very large `n` (say, n = 10⁶), which style would you choose and why?* Tabulation — memoization's recursion depth would risk `StackOverflowError` at that scale (Java's default stack depth is typically a few thousand to tens of thousands of frames, far short of a million), while an iterative tabulated loop has no such ceiling and can also be trivially space-optimized to O(1) for a problem like Fibonacci.

---

**Q: DP vs greedy vs divide-and-conquer?** ⭐

All three techniques exploit some form of problem substructure, but differently. **Divide-and-conquer** splits into subproblems that **don't overlap** (merge sort's two halves are always disjoint) — there's nothing to cache, so no DP-style memoization applies. **Greedy** (Phase 12) requires optimal substructure but makes a single locally-optimal choice at each step and never reconsiders it — correct only when that choice is provably safe (via an exchange argument), and like divide-and-conquer, it doesn't need overlapping subproblems since nothing gets re-solved. **DP** requires both optimal substructure *and* overlapping subproblems — it considers all viable choices at each state (not just one) and reuses previously-computed subproblem results, which is exactly the tool for problems where a greedy choice can be proven *unsafe* (the coin-change counterexample from Phase 12: `{1,3,4}` targeting `6` — greedy picks 3 coins, DP correctly finds 2).

| | Divide-and-conquer | Greedy | DP |
|---|---|---|---|
| Subproblems overlap? | No | N/A (no re-solving) | **Yes** |
| Choices per step | N/A — recursively combines disjoint pieces | One, never revisited | All viable, compared/combined |
| Correctness needs | Just correct combination logic | Provable exchange argument | Correct recurrence |

*Follow-up: How do you decide, when facing a brand-new problem, whether to even attempt a greedy solution before reaching for DP?* Try to state a plausible greedy rule and its exchange argument (Phase 12). If you can complete the argument, greedy will be simpler and faster. If you find a counterexample (even a small hand-constructed one), or the greedy rule clearly requires "looking ahead" or reconsidering past choices based on future information, that's the signal to move to DP instead.

---

**Q: How do you approach a DP problem?** ⭐⭐

Define the state first — precisely state what `dp[i]` (or `dp[i][j]`) *means* in plain English; this is roughly 80% of the actual difficulty, since a vague state definition ("dp[i] = the best so far") produces a recurrence that doesn't actually work. Then write the recurrence, expressing `dp[state]` from smaller states — this is where the problem's actual decision points get encoded. Set the base cases (the smallest states' trivially-known values). Choose an evaluation order so dependencies are computed before they're needed (for tabulation; memoization sidesteps this via recursion). Finally, identify which specific state holds the answer — it's not always the "last" or "largest" state (longest increasing subsequence's answer is `max(dp)`, not `dp[n-1]`).

In an interview specifically: start with the brute-force recursion (it hands you the state and recurrence almost for free), identify the repeated state, add memoization, then optionally convert to tabulation and optimize space — narrating this progression out loud is exactly what's being evaluated.

*Follow-up: You've defined a state and recurrence, but the resulting code gives a wrong answer — what's your debugging process?* Trace the recurrence by hand on the smallest failing example, checking each `dp[]` entry against what it should mean per your state definition. Most bugs trace back to either an incorrect base case, an off-by-one in the state definition (e.g., inclusive vs. exclusive prefix boundary), or an iteration order that reads a "future" state before it's been computed.

---

**Q: Climbing stairs / Fibonacci / house robber.** ⭐

Climbing stairs: `dp[i] = dp[i-1] + dp[i-2]` (ways to reach step `i` via a final 1-step or 2-step) — Fibonacci in disguise, O(n) time, O(1) space with rolling variables. House robber adds a take-or-skip decision at each step: `dp[i] = max(dp[i-1], dp[i-2] + nums[i])` — either skip house `i` (carry forward the best without it) or rob it (which forces skipping house `i-1`, so add `nums[i]` to `dp[i-2]`).

```java
static int houseRobber(int[] nums) {
    int prev2 = 0, prev1 = 0;
    for (int x : nums) { int cur = Math.max(prev1, prev2 + x); prev2 = prev1; prev1 = cur; }
    return prev1;
}
// houseRobber({2,7,9,3,1}) -> 12  (rob indices 0, 2, 4: 2+9+1)
```

*Follow-up: "House Robber II" adds the constraint that the houses are arranged in a circle (first and last are now also adjacent) — how would you adapt the solution?* Run the same linear house-robber DP twice: once excluding the last house, once excluding the first house, and take the max of the two results. This correctly handles the circular adjacency without needing a fundamentally different recurrence, since excluding either endpoint breaks the circular constraint back into a linear one.

---

**Q: Coin change (fewest coins).** ⭐⭐

`dp[a] = 1 + min over each coin c <= a of dp[a - c]`, with base case `dp[0] = 0` and an "infinity" sentinel (here, `amount + 1`) for amounts that turn out unreachable. O(amount × number of coins) time, O(amount) space.

```java
static int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, amount + 1);
    dp[0] = 0;
    for (int a = 1; a <= amount; a++)
        for (int c : coins)
            if (c <= a) dp[a] = Math.min(dp[a], 1 + dp[a - c]);
    return dp[amount] > amount ? -1 : dp[amount];
}
```

The "count the number of ways" variant sums instead of minimizing (`dp[a] += dp[a-c]`), but with a crucial loop-order detail: the **coin loop must be outer**, the amount loop inner — looping amount-outer/coin-inner would count `1+2` and `2+1` as two separate ways (permutations), when the problem usually wants combinations (order doesn't matter, counted once).

*Follow-up: Why does the sentinel value need to be `amount + 1` specifically, rather than, say, `Integer.MAX_VALUE`?* Using `amount + 1` avoids integer overflow risk entirely (adding `1 + dp[a-c]` to a value near `Integer.MAX_VALUE` would overflow), and it's provably large enough to never be mistaken for a real answer, since the true minimum coin count for any amount can never exceed `amount` itself (in the worst case, a coin of value 1 exists and it takes exactly `amount` of them).

---

**Q: Longest common subsequence / edit distance.** ⭐⭐

Both are 2D DP over two strings, sharing a "match → diagonal, mismatch → combine neighbors" template. **LCS**: on a character match, extend the diagonal (`dp[i][j] = dp[i-1][j-1] + 1`); on a mismatch, take the best of dropping a character from either string (`max(dp[i-1][j], dp[i][j-1])`). **Edit distance**: on a match, no edit needed — inherit the diagonal unchanged (`dp[i][j] = dp[i-1][j-1]`); on a mismatch, pay for one edit and take the cheapest of substitute/delete/insert (`1 + min(dp[i-1][j-1], dp[i-1][j], dp[i][j-1])`).

```java
static int longestCommonSubsequence(String a, String b) {
    int[][] dp = new int[a.length() + 1][b.length() + 1];
    for (int i = 1; i <= a.length(); i++)
        for (int j = 1; j <= b.length(); j++)
            dp[i][j] = (a.charAt(i - 1) == b.charAt(j - 1))
                    ? dp[i - 1][j - 1] + 1
                    : Math.max(dp[i - 1][j], dp[i][j - 1]);
    return dp[a.length()][b.length()];
}
```

Both are O(m·n) time and space, though space can be reduced to O(min(m,n)) via a rolling two-row technique, since each row only ever depends on the row directly above it. This "match/mismatch" 2D template extends directly to string interleaving, wildcard/regex matching, and distinct-subsequence-counting problems.

*Follow-up: Why does LCS's mismatch case take a `max` but edit distance's mismatch case takes a `min`?* LCS is maximizing a count (the longest shared subsequence — bigger is better), so when characters don't match, you want the best (largest) result from dropping a character off either string. Edit distance is minimizing a cost (fewest edits — smaller is better), so when characters don't match, you want the cheapest (smallest) of the three possible edit operations.

*Follow-up: How would you reconstruct the actual LCS string, not just its length?* After filling the `dp` table, walk backward from `dp[m][n]`: if `a.charAt(i-1) == b.charAt(j-1)`, that character is part of the LCS — record it and move diagonally to `(i-1, j-1)`; otherwise move toward whichever of `dp[i-1][j]` or `dp[i][j-1]` is larger (the direction that produced the max). Reverse the recorded characters at the end.

---

**Q: 0/1 knapsack.** ⭐

`dp[w]` = the best value achievable with capacity exactly (or at most) `w`. For each item, iterate capacity **downward** (from `capacity` to the item's weight) so that `dp[w - weight[i]]`, read during this item's update, still reflects the state *before* this item was considered — this is what guarantees each item is used at most once. Iterating upward instead would let the same item contribute to `dp[w]` more than once within a single item's pass, incorrectly allowing reuse.

```java
static int knapsack01(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];
    for (int i = 0; i < weights.length; i++)
        for (int w = capacity; w >= weights[i]; w--)   // iterate DOWN so each item used once
            dp[w] = Math.max(dp[w], dp[w - weights[i]] + values[i]);
    return dp[capacity];
}
```

Unbounded knapsack (unlimited reuse per item) is the same code with the capacity loop **reversed to iterate upward** — that's the entire difference. Subset-sum / "partition equal subset sum" is a boolean 0/1-knapsack variant: `dp[t]` tracks whether some subset sums to exactly `t`, with `target = totalSum / 2` for the partition problem (if the found subset sums to half, the remainder automatically sums to the other half).

*Follow-up: How would you recover which specific items were chosen, not just the best value?* Either keep a full 2D `dp[i][w]` table (instead of the space-optimized 1D rolling version) and backtrack through it comparing `dp[i][w]` to `dp[i-1][w]` (if equal, item `i` wasn't used; if different, it was, so subtract its weight and move to row `i-1`), or maintain a parallel "choice" table alongside the 1D version recording which item last updated each `dp[w]` entry.

*Follow-up: What's the actual time complexity of 0/1 knapsack, and why is it sometimes called "pseudo-polynomial"?* O(items × capacity). It's pseudo-polynomial because the complexity depends on the *numeric value* of the capacity, not just the *count* of input items — if capacity is exponentially large relative to the number of items (e.g., capacity = 2^1000 with only 10 items), this "polynomial-looking" complexity becomes impractically slow, unlike a true polynomial algorithm whose cost scales only with input *size* (bit-length), not magnitude.

---

**Q: Longest increasing subsequence.**

`dp[i]` = the length of the longest strictly-increasing subsequence ending exactly at index `i`, computed as `max over all j < i with a[j] < a[i] of (dp[j] + 1)`, defaulting to `1` if no valid `j` exists. The final answer is `max(dp)` over the whole array, **not** `dp[n-1]` — the longest subsequence overall might end at any index. This naive approach is O(n²).

```java
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
```

An O(n log n) approach also exists, via "patience sorting": maintain an auxiliary array of the smallest possible tail value achievable for each subsequence length seen so far, and for each new element, binary search that array for where it belongs — either extending the array (a new longest length found) or replacing an existing tail with a smaller value (keeping future extension possibilities as open as possible). Know that both approaches exist and their complexities, even if you only derive the O(n²) version live.

*Follow-up: Why does replacing an existing tail with a smaller value in the O(n log n) approach not corrupt the answer, even though the auxiliary array doesn't represent an actual valid subsequence?* The auxiliary array only needs to track the *smallest possible tail value* for each achievable length — it doesn't need to be a real subsequence itself. Replacing a tail with a smaller value can only ever help future elements extend a sequence of that length (a smaller tail is easier to build on top of), and it never causes the recorded *length* for that position to decrease, so the final answer (the array's length) remains correct.

---

**Q: How do you optimize DP space?**

Check whether `dp[i]` (or `dp[i][j]`) only ever depends on a small, fixed window of previous states — not the entire table built so far. If so, discard entries outside that window as you go: a 1D table where `dp[i]` depends only on `dp[i-1]` and `dp[i-2]` collapses to two rolling variables (O(1) space, as in `climbStairs`/`houseRobber`); a 2D table where `dp[i][j]` depends only on row `i-1` collapses to two 1D rows (O(n) space instead of O(m·n), as is possible for LCS and edit distance). This reduces space without changing time complexity at all — it's purely a constant-factor / asymptotic-space win, not a time optimization.

*Follow-up: If you need to reconstruct the actual solution path (not just the optimal value), can you still use the space-optimized version?* Generally no — reconstruction typically requires walking backward through the full table to see which choices were made at each state, and a space-optimized rolling version has already discarded the rows/entries needed for that backward walk. If reconstruction is required, you either keep the full table (accepting the higher space cost) or use a more advanced technique (e.g., Hirschberg's algorithm for LCS, which achieves reconstruction in O(min(m,n)) space via a divide-and-conquer trick, at the cost of a higher constant factor).

---

**Q: Which is faster, memoization or tabulation?**

Same asymptotic time complexity for both — O(states × transition cost) either way. In practice, tabulation tends to have a lower constant factor since it avoids function-call/recursion overhead, and it also avoids Java's stack-depth limits entirely, which matters for problems with very deep recursion (large `n`). Memoization's advantage is that it only computes states actually *reached* by the specific input — for problems where many theoretically-possible states are never needed for a given input, this can mean genuinely less work done, not just a smaller constant factor. Memoization is also usually faster to *derive* correctly under interview time pressure, since it's a near-mechanical addition to the brute-force recursion you'd write first anyway.

*Follow-up: Give a concrete example where memoization does asymptotically less work than tabulation for the same problem.* Consider a recursive search over "can I reach the end from position i using jump lengths from a given set," where many positions are simply never reachable from the start for a specific input. Memoization only computes `dp[i]` for positions actually visited during the recursive exploration, while a naive tabulation would compute every `dp[i]` from `0` to `n` regardless of reachability — for an input where only a small fraction of positions end up reachable, memoization does meaningfully less total work.
