<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · sorting searching](../phase-10-sorting-searching/NOTES.md) | [Phase 12 · greedy ➡](../phase-12-greedy/NOTES.md)
<!-- /nav -->

# Phase 11 — Dynamic Programming: Interview Q&A + Problems

⭐ = asked constantly. DP is the most feared category; the method below tames it.

**Q: What is dynamic programming? When does it apply?** ⭐⭐
Solving a problem by combining solutions to overlapping subproblems, each computed once and stored. Applies when the problem has **overlapping subproblems** and **optimal substructure**. It converts exponential recursion into polynomial time.

**Q: Memoization vs tabulation?** ⭐⭐
Memoization is top-down: recursion + a cache, computing only reached states lazily. Tabulation is bottom-up: fill a table in dependency order, no recursion, often enabling space optimization. Same time complexity; memoization mirrors the brute force, tabulation is iterative.

**Q: DP vs greedy vs divide-and-conquer?** ⭐
All exploit substructure. Divide-and-conquer: subproblems *don't* overlap (merge sort). Greedy: make a locally optimal choice and never reconsider — works only when the greedy choice is provably optimal (Phase 12). DP: overlapping subproblems, considers all choices and reuses results — used when greedy fails.

**Q: How do you approach a DP problem?** ⭐⭐
Define the state (what `dp[...]` means), write the recurrence from smaller states, set base cases, choose an evaluation order, and identify the answer state. In interviews: start with brute-force recursion, spot the repeated state, add memoization, then optionally tabulate and optimize space.

**Q: Climbing stairs / Fibonacci / house robber.** ⭐
`dp[i]` from `dp[i-1]`/`dp[i-2]`; O(n) time, O(1) space with rolling variables. House robber adds the take-vs-skip choice: `dp[i] = max(dp[i-1], dp[i-2] + nums[i])`.

**Q: Coin change (fewest coins).** ⭐⭐
`dp[amount] = 1 + min over coins of dp[amount - coin]`, base `dp[0]=0`, infinity sentinel for unreachable. O(amount × coins). "Count ways" is a variant that sums instead of min-ing (mind the loop order to avoid double counting).

**Q: Longest common subsequence / edit distance.** ⭐⭐
2D DP over the two strings: on a character match take the diagonal (+1 for LCS, +0 for edit distance); otherwise combine neighbors (max for LCS, 1 + min of the three edits for edit distance). O(m·n). The template for many string-DP problems.

**Q: 0/1 knapsack.** ⭐
`dp[w]` = best value at capacity w; for each item iterate capacity **downward** so each item is used at most once. Unbounded knapsack iterates upward (reuse). Subset-sum/partition is the boolean version.

**Q: Longest increasing subsequence.**
O(n²) DP (`dp[i]` = longest ending at i) or O(n log n) with patience sorting / binary search on the tails array. Know both exist.

**Q: How do you optimize DP space?**
When `dp[i]` depends only on the previous row/few entries, keep just those (2 rows, or O(1) rolling variables). Reduces O(n²)/O(n) space to O(n)/O(1) without changing time.

**Q: Which is faster, memoization or tabulation?**
Same asymptotic time. Tabulation avoids recursion overhead and stack limits and enables space optimization; memoization skips unreachable states and is easier to derive from brute force. Choose per problem.
