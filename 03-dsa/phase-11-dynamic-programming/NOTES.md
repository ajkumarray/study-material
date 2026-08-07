<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · sorting searching](../phase-10-sorting-searching/NOTES.md) | [Phase 12 · greedy ➡](../phase-12-greedy/NOTES.md)
<!-- /nav -->

# Phase 11 — Dynamic Programming: Notes

## When DP applies
Two conditions:
1. **Overlapping subproblems** — the same smaller problems recur (naive `fib` recomputes `fib(3)` exponentially many times).
2. **Optimal substructure** — the optimal answer is composed of optimal answers to subproblems.

When both hold, solve each subproblem **once** and reuse it → exponential brute force becomes polynomial. (Contrast greedy, Phase 12, which also needs optimal substructure but makes a locally-optimal choice *without* revisiting subproblems; and divide-and-conquer, whose subproblems *don't* overlap.)

## The two styles
- **Memoization (top-down):** ordinary recursion + a cache; compute lazily, only the states you reach. Closest to the brute-force recursion — write that first, then add a cache. `fibMemo` turns O(2ⁿ) into O(n).
- **Tabulation (bottom-up):** fill a table from base cases upward in dependency order; no recursion, often allows **space optimization** (keep only the last row/few variables — `climbStairs`/`houseRobber` use O(1) space via rolling variables).

Both are O(states × transition cost). Same complexity; pick by taste and whether you need all states.

## The method (how to actually solve a DP)
1. **Define the state** — what does `dp[i]` / `dp[i][j]` *mean*? (e.g., "fewest coins to make amount i", "LCS of prefixes a[0..i), b[0..j)".) This is 80% of the difficulty.
2. **Write the recurrence** — express `dp[state]` from smaller states (the transition).
3. **Base cases** — the smallest states' values.
4. **Order** — iterate so dependencies are computed first (or memoize to sidestep ordering).
5. **Answer** — which state holds it.

## The pattern families (all in the code)
- **1D DP:** climbing stairs (Fibonacci shape), house robber (take/skip), coin change (min/count ways), longest increasing subsequence. `dp[i]` from a few earlier entries.
- **2D DP:** grid unique paths, longest common subsequence, edit distance. `dp[i][j]` over two indices/strings; the "match → diagonal, else → max/min of neighbors" template covers a whole family (LCS, edit distance, string interleaving, wildcard matching).
- **Knapsack:** 0/1 (each item once — iterate capacity *downward*), unbounded (reuse — iterate upward), subset-sum/partition (0/1 with boolean target). The choose-under-a-constraint template.
- **Interval / tree DP:** matrix-chain, burst balloons, palindrome partitioning (state = a range `[i..j]`); DP on trees (state = subtree rooted at a node) — the advanced tier.

## Interview strategy
Start by writing the **brute-force recursion** (define the choice at each step); identify the repeated state → add memoization; optionally convert to tabulation and optimize space. Narrating this progression (exponential → memoized → tabulated → space-optimized) is exactly what interviewers want to see. Recognize DP triggers: "count the number of ways", "min/max cost/length", "is it possible to reach", especially with a "can't use a simple greedy" flavor.
