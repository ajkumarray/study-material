<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · sorting searching](../phase-10-sorting-searching/NOTES.md) | [Phase 12 · greedy ➡](../phase-12-greedy/NOTES.md)
<!-- /nav -->

# Phase 11 — Dynamic Programming: Notes

All code below is from `DynamicProgramming.java` in this directory (run with `java -ea DynamicProgramming.java`) unless a section is marked "not implemented in this file" — those round out the picture with classic follow-up problems.

## 1. When Does DP Apply?

**Dynamic programming** solves a problem by breaking it into subproblems, solving each one **only once**, and reusing that result wherever it's needed again — turning an exponential brute-force recursion into a polynomial-time algorithm. DP applies when a problem has **both** of these properties:

- **Overlapping subproblems**: the same smaller subproblem recurs multiple times during a naive recursive solution. The textbook example is naive Fibonacci: `fib(5)` calls `fib(4)` and `fib(3)`; `fib(4)` calls `fib(3)` and `fib(2)` — `fib(3)` gets computed twice here, and the duplication compounds exponentially as `n` grows, because the recursion tree branches into two calls at every level without ever remembering a previously-computed answer.
- **Optimal substructure**: the optimal (or correct) answer to the whole problem can be built directly from optimal (or correct) answers to its subproblems — you don't need to consider some combination of *sub-optimal* subproblem solutions to get the overall optimal answer.

When **both** hold, the fix is simple in concept: compute each distinct subproblem's answer exactly once, store it, and reuse the stored value every time that same subproblem comes up again — this is the entire mechanism behind DP's speedup.

```java
// Naive recursive Fibonacci -- overlapping subproblems, no memoization: O(2^n)
static long fibNaive(int n) {
    if (n < 2) return n;
    return fibNaive(n - 1) + fibNaive(n - 2);   // fib(n-2) gets recomputed from BOTH branches
}
```

**Contrast with related techniques**: **Greedy** (Phase 12) also requires optimal substructure, but makes a single locally-optimal choice at each step and never revisits it — it works only when that greedy choice is *provably* safe, and it doesn't need (or benefit from) overlapping subproblems since there's no re-solving happening. **Divide-and-conquer** (e.g., merge sort, Phase 10) also breaks a problem into subproblems, but those subproblems **don't overlap** — merge sort's two recursive halves are always over disjoint segments of the array, so there's nothing to cache or reuse.

| | DP | Greedy | Divide-and-conquer |
|---|---|---|---|
| Needs optimal substructure | Yes | Yes | Not strictly required |
| Needs overlapping subproblems | **Yes** (that's why caching pays off) | No | **No** (subproblems are disjoint) |
| Choice per step | Consider all viable choices | Commit to one, never revisit | N/A — recursively combines disjoint pieces |
| Example | Coin change (arbitrary denominations), LCS, edit distance | Interval scheduling, fractional knapsack (Phase 12) | Merge sort, binary search |

**Why it's useful**: correctly identifying "this has overlapping subproblems AND optimal substructure" — versus "this only needs a locally safe greedy choice" or "this cleanly splits into independent pieces" — is the single biggest decision point in choosing the right technique for an unfamiliar problem.

**Summary — Key Takeaways:**
- DP applies when both overlapping subproblems AND optimal substructure hold — solve each distinct subproblem once, reuse the result.
- Naive recursive Fibonacci is the canonical example of overlapping subproblems without caching: O(2ⁿ) time from re-deriving the same values repeatedly.
- Greedy also needs optimal substructure but never revisits a choice (no overlapping subproblems exploited); divide-and-conquer's subproblems don't overlap at all.

---

## 2. The Two Styles: Memoization vs. Tabulation

Both styles solve the same problem in the same asymptotic time — **O(states × transition cost)** — they differ only in *direction* and *mechanism*.

### Memoization (top-down)

- **Idea**: write the natural recursive brute-force solution first, then add a cache (an array, or a `HashMap` for sparse/non-integer states) that's checked at the top of the function and populated just before returning. Subsequent calls with the same arguments hit the cache instead of re-deriving the answer.
- **Key property**: it computes lazily — **only the states actually reached** by the recursion get computed, which can be a real advantage when many theoretically-possible states are never actually needed for the specific input.
- **Closest to brute force**: because it's structurally identical to the naive recursive solution plus a cache check, it's usually the easiest style to *derive* under interview time pressure — write the brute force, verify it's correct (even if slow), then add memoization as a mechanical last step.

```java
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
// fibMemo(40) -> 102334155   (computed in O(n) instead of the naive O(2^n))
```

In this example: `memo` is initialized to `-1` (a sentinel meaning "not yet computed" — safe here since Fibonacci values are never negative). Each call first checks the cache; on a miss, it computes the result recursively and **stores it in the same statement it returns it** (`return memo[n] = ...`), a compact idiom worth recognizing. This turns what would be an O(2ⁿ) call tree into O(n) total work, since each of the n distinct states is computed exactly once.

### Tabulation (bottom-up)

- **Idea**: fill a table (array) iteratively, starting from the base cases and working up through increasing states in dependency order — no recursion at all.
- **Key advantage — space optimization**: when a state only depends on a small, fixed number of previous states (not the entire table), you can often discard old rows/entries and keep only what's still needed, collapsing an O(n) or O(n²) table down to O(1) or O(n) extra space. `climbStairs` and `houseRobber` below both use O(1) space via a couple of "rolling" variables instead of a full array.
- **No recursion overhead or stack depth limits**: for very large `n`, memoization's recursion depth can hit Java's stack limit (`StackOverflowError`); tabulation's iterative loop has no such ceiling.

```java
static int climbStairs(int n) {
    int prev2 = 1, prev1 = 1;                 // rolling variables -> O(1) space
    for (int i = 2; i <= n; i++) { int cur = prev1 + prev2; prev2 = prev1; prev1 = cur; }
    return prev1;
}
// climbStairs(5) -> 8   (ways to climb 5 stairs taking 1 or 2 steps at a time -- Fibonacci in disguise)
```

In this example: rather than a full `dp[]` array of size `n+1`, only the two most recent values (`prev1`, `prev2`) are kept, since `dp[i]` only ever depends on `dp[i-1]` and `dp[i-2]` — this is the O(1)-space rolling-variable optimization applied directly during tabulation.

| | Memoization (top-down) | Tabulation (bottom-up) |
|---|---|---|
| Direction | Recursive, lazy | Iterative, computes every state in order |
| Computes only reachable states? | **Yes** | No — fills the whole table (unless you add extra logic to skip) |
| Recursion/stack risk | Yes — can `StackOverflowError` on deep recursion | No |
| Space optimization | Harder (cache is usually a full structure) | **Easier** — rolling variables, 2-row tables |
| Easiest to derive from brute force | **Yes** — brute force + cache | Requires figuring out iteration order up front |

**Why it's useful**: knowing both styles — and being able to convert between them — is itself a common interview follow-up ("can you now do this bottom-up?" or "can you reduce the space?"). Starting with memoization to prove correctness quickly, then converting to tabulation for space optimization, is a standard, well-received interview progression.

**Summary — Key Takeaways:**
- Same time complexity for both: O(states × transition cost).
- Memoization: recursion + cache, computes lazily, easiest to derive from brute force, but risks stack overflow on deep recursion.
- Tabulation: iterative, fills a table bottom-up, no recursion risk, and much easier to space-optimize via rolling variables.
- A polished interview answer often shows the progression: brute force → memoized → tabulated → space-optimized.

---

## 3. The Method: How to Actually Solve a DP Problem

A repeatable five-step process, worth internalizing as a checklist rather than trying to "see" the answer immediately:

1. **Define the state** — precisely state what `dp[i]` (or `dp[i][j]`, or whatever the state variables are) *means* in plain English. E.g., "`dp[i]` = the fewest coins needed to make amount `i`" or "`dp[i][j]` = the length of the longest common subsequence of `a[0..i)` and `b[0..j)`." **This is roughly 80% of the actual difficulty** — once the state definition is right, the recurrence often falls out almost mechanically.
2. **Write the recurrence (the transition)** — express `dp[state]` in terms of *smaller* states. This is where the problem's actual choices/decisions get encoded (e.g., "take this coin or don't," "these characters match or they don't").
3. **Base cases** — the smallest states' values, usually trivial to state directly (e.g., `dp[0] = 0` coins to make amount 0; an empty string has LCS length 0 with anything).
4. **Evaluation order** — for tabulation, iterate so that every state's dependencies are already computed by the time you need them (usually smaller indices/prefixes first); for memoization, this is sidestepped entirely since recursion resolves the order naturally.
5. **Identify the answer** — which specific state (often, but not always, the "largest" or "full" state) holds the final answer.

**Why it's useful**: this method converts "stare at the problem hoping for insight" into a mechanical process you can narrate out loud step by step — which is exactly what interviewers want to see, since it demonstrates a repeatable approach rather than pattern-memorization of specific problems.

**Summary — Key Takeaways:**
- Define the state precisely first — vague state definitions ("dp[i] = the best so far") lead to recurrences that don't actually work.
- The recurrence encodes the problem's actual decision points; base cases anchor the smallest states.
- For tabulation, get the iteration order right (dependencies computed before they're needed); for memoization, recursion handles this for free.

---

## 4. 1D DP — State Depends on a Few Earlier Entries

### Climbing stairs (Fibonacci in disguise)

- **State**: `dp[i]` = number of distinct ways to reach step `i`, given you can take 1 or 2 steps at a time.
- **Recurrence**: `dp[i] = dp[i-1] + dp[i-2]` — you reach step `i` either from step `i-1` (via a 1-step) or step `i-2` (via a 2-step), and these are the *only* two ways, so the counts simply add.
- **Complexity**: O(n) time, **O(1) space** via rolling variables (shown in section 2).

### House robber (take/skip choice)

- **State**: `dp[i]` = the maximum money obtainable considering houses `0..i`, given adjacent houses can't both be robbed.
- **Recurrence**: `dp[i] = max(dp[i-1], dp[i-2] + nums[i])` — at each house, either **skip** it (carrying forward whatever the best was without it, `dp[i-1]`) or **rob** it (which forces skipping the immediately previous house, so you add `nums[i]` to `dp[i-2]`, not `dp[i-1]`).
- **Complexity**: O(n) time, O(1) space via the same rolling-variable trick.

```java
static int houseRobber(int[] nums) {
    int prev2 = 0, prev1 = 0;
    for (int x : nums) { int cur = Math.max(prev1, prev2 + x); prev2 = prev1; prev1 = cur; }
    return prev1;
}
// houseRobber({2,7,9,3,1}) -> 12   (rob houses at index 0, 2, 4: 2+9+1=12)
```

In this example: processing `x=9` (the third house), `cur = max(prev1=7, prev2=2 + 9=11) = 11` — robbing this house (`11`, built on the best total *excluding* the immediately previous house) beats skipping it (`7`, the best total *including possibly* the previous house). This "take-or-skip with a forced-skip-of-neighbor" shape is the template for an entire family of "no two adjacent" selection problems.

### Coin change — fewest coins

- **State**: `dp[a]` = the minimum number of coins needed to make amount `a` exactly (using unlimited supply of each coin denomination).
- **Recurrence**: `dp[a] = 1 + min over each coin c <= a of dp[a - c]` — try using one of each available coin as the "last" coin added, and take whichever choice leaves the cheapest remainder to solve.
- **Base case**: `dp[0] = 0` (zero coins needed to make amount zero).
- **Unreachable amounts**: initialize the whole table to a sentinel value larger than any possible real answer (here, `amount + 1`, since you can never need more than `amount` coins if a 1-valued coin existed, and if it's still at the sentinel after filling, that amount truly can't be made with the given denominations) — return `-1` in that case.
- **Complexity**: O(amount × number of coins) time, O(amount) space.

```java
static int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, amount + 1);              // "infinity" sentinel
    dp[0] = 0;
    for (int a = 1; a <= amount; a++)
        for (int c : coins)
            if (c <= a) dp[a] = Math.min(dp[a], 1 + dp[a - c]);
    return dp[amount] > amount ? -1 : dp[amount];
}
// coinChange({1,2,5}, 11) -> 3   (5 + 5 + 1)
```

In this example: `dp[11]` considers using coin `1` (leftover `dp[10]`), coin `2` (leftover `dp[9]`), or coin `5` (leftover `dp[6]`), taking `1 + min` of whichever leftover is cheapest to solve — and by the time `a=11` is reached, all smaller `dp[a']` values are already correctly filled in from earlier iterations of the outer loop.

**Variant — "count the number of ways"** (not implemented in this file): instead of `dp[a] = 1 + min(...)`, use `dp[a] += dp[a - c]` (summing instead of minimizing). A crucial detail: to count *combinations* (order doesn't matter, e.g., `1+2` and `2+1` count as one way) rather than *permutations* (order matters, counted separately), the **coin loop must be the outer loop** and the amount loop the inner one — looping amount-outer/coin-inner instead would count `1+2` and `2+1` as two distinct ways, since both orderings would eventually be reached from different directions.

### Longest increasing subsequence (LIS)

- **State**: `dp[i]` = the length of the longest strictly-increasing subsequence that **ends exactly at index `i`**.
- **Recurrence**: `dp[i] = max over all j < i where a[j] < a[i] of (dp[j] + 1)`, or `1` if no such `j` exists (the element alone forms a subsequence of length 1).
- **Complexity**: O(n²) as shown (nested loop over all pairs `i, j`). An **O(n log n)** version exists using "patience sorting" — maintain an auxiliary array of the smallest possible tail value for each achievable subsequence length, and binary search it for where each new element belongs (replacing an existing tail or extending the array) — significantly faster for large inputs, though more intricate to derive and prove correct live.

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
// longestIncreasingSubsequence({10,9,2,5,3,7,101,18}) -> 4   (e.g. 2,5,7,101 or 2,3,7,101 or 2,3,7,18)
```

In this example: `dp[i]` isn't itself the final answer for any specific `i` — the final answer is the **maximum** over all `dp[i]` values, since the longest increasing subsequence overall could end at any index, not necessarily the last one. That's why `best` is tracked separately and updated after each `dp[i]` computation, rather than just returning `dp[a.length - 1]`.

| Pattern | Time | Space | State meaning |
|---|---|---|---|
| Climbing stairs | O(n) | O(1) | Ways to reach step i |
| House robber | O(n) | O(1) | Best total using houses 0..i |
| Coin change (min coins) | O(amount × coins) | O(amount) | Fewest coins to make amount a |
| LIS (DP) | O(n²) | O(n) | Longest increasing run ending AT index i |
| LIS (patience sorting) | O(n log n) | O(n) | Smallest tail value per achievable length |

**Why it's useful**: this family — "state depends on a small, fixed number of earlier 1D entries" — is the most common DP shape in interviews and is almost always solvable with O(1) or O(n) space; recognizing "the answer isn't necessarily `dp[n-1]`, it might be the max/min over the whole table" (as in LIS) is a subtlety worth checking explicitly for every new 1D DP problem.

**Summary — Key Takeaways:**
- Climbing stairs / house robber: `dp[i]` from `dp[i-1]`/`dp[i-2]`, O(n) time, O(1) space via rolling variables.
- Coin change: `dp[a] = 1 + min(dp[a-c])` over coins; "count ways" variant sums instead, and coin-outer/amount-inner loop order avoids double-counting permutations as separate combinations.
- LIS: `dp[i]` = longest run ending AT i, answer is `max(dp)` not `dp[n-1]` — O(n²) naive, O(n log n) via patience sorting.

---

## 5. 2D DP — State Depends on Two Indices

### Unique grid paths

- **State**: `dp[i][j]` = number of distinct paths from the top-left corner to cell `(i, j)`, moving only right or down.
- **Recurrence**: `dp[i][j] = dp[i-1][j] + dp[i][j-1]` — you can only arrive at `(i,j)` from directly above or directly to the left, so the path count is the sum of both.
- **Base cases**: the entire first row and first column are `1` (only one way to reach any cell along the top edge or left edge — keep moving in the single available direction).

```java
static int uniquePaths(int m, int n) {
    int[][] dp = new int[m][n];
    for (int[] row : dp) row[0] = 1;
    for (int j = 0; j < n; j++) dp[0][j] = 1;
    for (int i = 1; i < m; i++)
        for (int j = 1; j < n; j++)
            dp[i][j] = dp[i - 1][j] + dp[i][j - 1];
    return dp[m - 1][n - 1];
}
// uniquePaths(3, 3) -> 6
```

**Complexity**: O(m·n) time and space (though this can be reduced to O(n) space with a single rolling row, since each row only depends on the row directly above it and cells to its own left).

### Longest common subsequence (LCS)

- **State**: `dp[i][j]` = length of the longest common subsequence of the prefixes `a[0..i)` and `b[0..j)`.
- **Recurrence — the "match/mismatch" template**: if `a[i-1] == b[j-1]` (the current characters match), extend the diagonal: `dp[i][j] = dp[i-1][j-1] + 1`. Otherwise, the current characters can't both be part of the LCS together, so take the best of dropping one character from either string: `dp[i][j] = max(dp[i-1][j], dp[i][j-1])`.
- **Base cases**: `dp[i][0] = dp[0][j] = 0` (an empty string has LCS length 0 with anything) — implicit here since Java arrays default-initialize to 0.

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
// longestCommonSubsequence("abcde", "ace") -> 3   ("ace" is the LCS)
```

In this example: indices are offset by 1 (`dp` is sized `[len+1][len+1]`) specifically so that `dp[0][*]` and `dp[*][0]` can cleanly represent "an empty prefix" without needing negative indices — `a.charAt(i-1)` then correctly refers to the `i`-th character of `a` when `dp`'s row index is `i`. Tracing the match at `a`'s `'a'` (index 0) against `b`'s `'a'` (index 0): `dp[1][1] = dp[0][0] + 1 = 1`. The subsequent matches on `'c'` and `'e'` each extend the diagonal similarly, ultimately reaching `dp[5][3] = 3`.

### Edit distance (Levenshtein distance)

- **State**: `dp[i][j]` = the minimum number of insertions, deletions, or substitutions needed to transform `a[0..i)` into `b[0..j)`.
- **Recurrence**: if the current characters match, no edit is needed here — inherit the diagonal unchanged: `dp[i][j] = dp[i-1][j-1]`. Otherwise, one edit is required, and you take the cheapest of the three possible edit types: **substitute** (`dp[i-1][j-1]`, fix both current characters at once), **delete** from `a` (`dp[i-1][j]`), or **insert** into `a` to match `b` (`dp[i][j-1]`) — plus 1 for the edit itself.
- **Base cases**: `dp[i][0] = i` (delete all `i` characters of `a` to match an empty `b`), `dp[0][j] = j` (insert all `j` characters of `b` into an empty `a`).

```java
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
// editDistance("horse", "ros") -> 3   (horse -> rorse -> rose -> ros: substitute h->r, delete r, delete e)
```

**Complexity for both LCS and edit distance**: O(m·n) time and space (both can be reduced to O(min(m,n)) space with a rolling-two-rows technique, since each row only depends on the row directly above it).

| | LCS | Edit distance |
|---|---|---|
| On match | `dp[i-1][j-1] + 1` (extend) | `dp[i-1][j-1]` (no cost, inherit) |
| On mismatch | `max(dp[i-1][j], dp[i][j-1])` | `1 + min(dp[i-1][j-1], dp[i-1][j], dp[i][j-1])` |
| Base case | `dp[i][0] = dp[0][j] = 0` | `dp[i][0] = i`, `dp[0][j] = j` |

**Why it's useful**: this "match → diagonal, else → combine neighbors" 2D template covers a whole family of string-DP problems beyond just these two — string interleaving, wildcard/regex matching, and distinct-subsequence-counting problems all follow close variants of this same shape, differing mainly in exactly how the match and mismatch cases combine neighboring states.

**Summary — Key Takeaways:**
- Unique paths: `dp[i][j] = dp[i-1][j] + dp[i][j-1]` — sum of "arrived from above" and "arrived from the left."
- LCS: match → extend the diagonal (+1); mismatch → best of dropping a character from either string (max of up/left).
- Edit distance: match → inherit the diagonal for free; mismatch → 1 + cheapest of substitute/delete/insert (min of diagonal/up/left).
- The "match→diagonal, mismatch→combine neighbors" 2D template generalizes to a whole family of string-alignment DP problems.

---

## 6. The Knapsack Family

All knapsack variants share the same shape: choose a subset of items under a capacity constraint to maximize value (or achieve an exact target) — they differ in whether items can be reused, and whether the goal is a max value or a yes/no feasibility check.

### 0/1 knapsack (each item used at most once)

- **State**: `dp[w]` = the best total value achievable with capacity exactly (or at most) `w`, considering items processed so far.
- **The critical detail — iterate capacity DOWNWARD**: when updating `dp[w]` for the *current* item, you must iterate `w` from `capacity` down to the item's weight, **not** upward. Iterating downward guarantees that `dp[w - weight[i]]` (read during this item's update) still reflects the state *before* this item was considered — iterating upward would let the same item's value get added into `dp[w]` more than once (via an already-updated smaller `dp[w']` from earlier in the same inner loop pass), incorrectly allowing the item to be "used" multiple times.

```java
static int knapsack01(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];
    for (int i = 0; i < weights.length; i++)
        for (int w = capacity; w >= weights[i]; w--)   // iterate DOWN so each item used once
            dp[w] = Math.max(dp[w], dp[w - weights[i]] + values[i]);
    return dp[capacity];
}
// weights={1,3,4,5}, values={1,4,5,7}, capacity=7 -> 9
//   (take the weight-4/value-5 item and the weight-3/value-4 item: 4+3=7 capacity used, 5+4=9 value)
```

- **Complexity**: O(items × capacity) time, O(capacity) space (this 1D-rolling form is a space-optimized version of a conceptually 2D `dp[item][capacity]` table).

### Unbounded knapsack (unlimited reuse of each item)

- **Difference from 0/1**: iterate capacity **upward** instead of downward — since items can be reused, you *want* `dp[w - weight[i]]` to potentially already reflect having used this same item, allowing it to be picked again.

```java
// Unbounded knapsack sketch — note the UPWARD iteration (contrast with 0/1's downward):
static int unboundedKnapsack(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];
    for (int i = 0; i < weights.length; i++)
        for (int w = weights[i]; w <= capacity; w++)   // iterate UP so an item CAN repeat
            dp[w] = Math.max(dp[w], dp[w - weights[i]] + values[i]);
    return dp[capacity];
}
```

### Subset-sum / partition (boolean 0/1 knapsack)

- **State**: `dp[t]` = whether some subset of the processed items sums to *exactly* `t` (a boolean, not a max-value).
- **"Partition equal subset sum"** is exactly a 0/1-knapsack-shaped subset-sum problem with `target = totalSum / 2` — if such a subset exists, the remaining elements automatically sum to the same target, splitting the array into two equal-sum halves.

```java
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
// canPartition({1,5,11,5}) -> true   ({1,5,5} sums to 11, matching the other half {11})
```

In this example: `sum = 22`, `target = 11`. `dp[0]` starts `true` (the empty subset sums to 0). Processing `x=1`: `dp[1]` becomes `true` (via `dp[0]`). Processing `x=5`: `dp[6]` and `dp[5]` become `true`. Processing `x=11`: `dp[11]` directly becomes true from `dp[0]` — but even without that single item, the earlier `1+5+5=11` combination (found once the second `5` is processed) would also make `dp[11]` `true`, correctly reporting a valid partition exists.

| Knapsack variant | Capacity loop direction | State type |
|---|---|---|
| 0/1 (each item once) | **Downward** | int (max value) or boolean (feasible) |
| Unbounded (reuse allowed) | **Upward** | int (max value) |
| Subset-sum / partition | Downward (it's 0/1-shaped) | boolean (exact-sum feasible) |

**Why it's useful**: the knapsack family is "choose under a constraint" in its purest form, and the downward-vs-upward capacity iteration direction is one of the most common sources of subtle, hard-to-spot bugs in DP interviews — always explicitly justify which direction you're iterating and why, since it's easy to get backward under pressure and produce a solution that silently allows (or disallows) item reuse incorrectly.

**Summary — Key Takeaways:**
- 0/1 knapsack: iterate capacity DOWNWARD so each item is considered at most once per pass.
- Unbounded knapsack: iterate capacity UPWARD so an item can be picked multiple times.
- Subset-sum/partition is a boolean 0/1-knapsack variant — "partition into two equal-sum halves" reduces directly to "does a subset sum to totalSum/2?"
- The capacity-iteration direction is the single most common subtle bug in this family — always justify it explicitly.

---

## 7. Interval / Tree DP — Not Implemented in This File

An advanced tier of DP where the state is a **range** or a **subtree**, rather than a simple prefix index.

- **Interval DP**: state is a range `[i, j]` over the input (e.g., `dp[i][j]` = the best answer considering only the subarray/substring from `i` to `j`). Classic examples: **matrix-chain multiplication** (minimize scalar multiplications when multiplying a chain of matrices — choose where to place parentheses), **burst balloons** (choosing the order to pop balloons to maximize collected coins — the trick is thinking of the *last* balloon popped in a range, not the first), and **palindrome partitioning** (minimum cuts to split a string into palindromic substrings, often paired with a precomputed `isPalindrome[i][j]` table). These typically iterate over increasing range *lengths*, since a range's answer depends on the answers of smaller sub-ranges nested inside it.
- **Tree DP**: state is "the subtree rooted at node X," typically computed via postorder traversal (Phase 7) so each node's answer is derived after both its children's answers are already known. Classic example: "maximum sum of a subset of tree nodes such that no two adjacent (parent-child) nodes are both selected" — a direct tree-shaped generalization of house robber, where each node returns *two* values to its parent (best sum including this node, best sum excluding it), and the parent combines them.

```java
// Tree-DP sketch: "house robber III" -- can't rob a node and its direct parent/child together.
static int[] robTree(TreeNode node) {
    if (node == null) return new int[]{0, 0};             // {rob this node, don't rob this node}
    int[] left = robTree(node.left);
    int[] right = robTree(node.right);
    int robThis = node.val + left[1] + right[1];           // if robbing this node, children must be skipped
    int skipThis = Math.max(left[0], left[1]) + Math.max(right[0], right[1]);
    return new int[]{robThis, skipThis};
}
```

**Why it's useful**: interval and tree DP represent the "next tier" once the 1D/2D/knapsack templates are second nature — recognizing "the state needs to be a *range*, not a single prefix index" (interval DP) or "the state needs to be a *subtree's* aggregated result, computed bottom-up" (tree DP) is what separates these from the more common flat-array DP patterns.

**Summary — Key Takeaways:**
- Interval DP: state is a range `[i, j]`; iterate by increasing range length, since a range's answer depends on smaller nested ranges.
- Tree DP: state is a subtree's aggregated result, computed via postorder (children before parent) — often returning multiple values per node (e.g., "best if included" vs "best if excluded") for the parent to combine.
- These are direct generalizations of the 1D/2D templates — the core "define state, write recurrence, identify base cases" method from section 3 still applies.

---

## 8. Interview Strategy

The winning approach, and the one interviewers are specifically listening for:

1. **Start with the brute-force recursion** — define the choice being made at each step, write it as plain recursion, and verify it's correct (even though it's exponential). This also naturally produces the state definition and recurrence you'll need for DP.
2. **Identify the repeated state** — point out which arguments to the recursive call recur across different call paths; that's your DP state.
3. **Add memoization** — a mechanical addition of a cache to the brute-force recursion, converting exponential time to polynomial.
4. **Optionally convert to tabulation** — restate the same recurrence as a bottom-up loop, removing recursion overhead.
5. **Optionally optimize space** — check whether the recurrence only needs a small, fixed window of previous states, and collapse the table accordingly.

**Recognizing DP triggers in a problem statement**: phrases like "count the number of ways," "minimum/maximum cost/length/value," "is it possible to reach/achieve X," especially when paired with a sense that "a simple greedy choice doesn't obviously work here" (if greedy clearly worked, Phase 12's techniques would apply instead) — these are the standard verbal signals that a problem wants DP.

**Why it's useful**: narrating this exact progression (exponential → memoized → tabulated → space-optimized) out loud, rather than jumping straight to a fully-optimized tabulated solution, demonstrates the *process* interviewers are actually evaluating — DP is widely considered the most feared interview category precisely because candidates try to skip straight to the "clever" final solution instead of building up to it methodically.

**Summary — Key Takeaways:**
- Brute force first (even if exponential) — it hands you the state and recurrence for free.
- Progression: brute force → memoize → (optionally) tabulate → (optionally) optimize space — narrate each step.
- DP triggers: "count the ways," "min/max cost," "is it possible," especially when a simple greedy choice clearly doesn't work.
