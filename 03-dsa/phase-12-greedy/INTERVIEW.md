<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 11 · dynamic programming](../phase-11-dynamic-programming/NOTES.md)
<!-- /nav -->

# Phase 12 — Greedy & Wrap-up: Interview Q&A + Problems

⭐ = asked constantly.

**Q: What is a greedy algorithm, and when is it correct?** ⭐⭐

A greedy algorithm builds a solution incrementally, at each step making whatever choice looks best *right now*, and never revisiting or undoing that choice. It has no backtracking, no "what if I'd chosen differently" table — it commits and moves on. That's exactly why it's fast (typically O(n log n) for a sort plus a linear scan) and exactly why it's dangerous to apply blindly.

Greedy is only *correct* — not just fast — when the problem has two structural properties: the **greedy-choice property** (a globally optimal solution can be reached by making the locally optimal choice first, then solving the remainder without ever reconsidering that first choice) and **optimal substructure** (an optimal solution to the whole problem is built from optimal solutions to its subproblems — the same requirement DP has). If you can't demonstrate the greedy-choice property — typically via an exchange argument — you should assume greedy is unsafe and reach for DP instead.

```java
// Correct: interval scheduling — sort by END time, take non-overlapping greedily.
// Incorrect for the same problem: sort by duration or start time (counterexamples exist).
```

*Follow-up: How would you convince an interviewer your greedy solution is correct, without a full formal proof?* State the exchange argument out loud: "Assume an optimal solution doesn't make the greedy choice first; show that swapping the greedy choice in for whatever the optimal solution did first can only help or stay neutral, never hurt. By induction, this reshapes any optimal solution into the greedy one." That's the standard template interviewers expect for interval scheduling, Huffman coding, and fractional knapsack.

---

**Q: Greedy vs dynamic programming — how do you decide?** ⭐⭐

Both require optimal substructure — an optimal answer must be built from optimal answers to subproblems. The difference is what happens with the choices at each step. Greedy commits to a single, locally-best choice and never revisits it; it does **not** need overlapping subproblems, because there's no repeated re-solving happening. DP instead considers all viable choices at each state and combines/compares the results of subproblems, which is precisely why it needs (and benefits from) **overlapping subproblems** — memoization/tabulation only pay off when the same subproblem recurs.

The classic contrast is coin change: with canonical denominations like US coins (`{1, 5, 10, 25}`), greedily taking the largest coin ≤ the remaining amount happens to always produce the minimum count. But with arbitrary denominations like `{1, 3, 4}` targeting `6`, greedy picks `4 + 1 + 1` (3 coins) while the true optimum is `3 + 3` (2 coins) — greedy strands itself with a bad remainder. That's exactly the failure mode DP is built to avoid: DP (Phase 11) considers *every* coin at *every* amount, so it never commits to a choice it can't walk back from.

| Dimension | Greedy | DP |
|---|---|---|
| Choice per step | One, locally optimal, never revisited | All viable choices, compared/combined |
| Needs optimal substructure | Yes | Yes |
| Needs overlapping subproblems | No | Yes |
| Typical complexity | O(n log n) sort + O(n) scan | O(n·k), O(n²), O(n·W) — proportional to state space |
| Correctness proof | Exchange argument | Correct recurrence (more mechanical) |

*Follow-up: Give a problem where greedy is correct for one variant but requires DP for a very similar-looking variant.* Knapsack: fractional knapsack (you may take any fraction of an item) is greedy-optimal by sorting on value/weight ratio; 0/1 knapsack (whole item or nothing) requires DP, because being forced to take a whole item can leave leftover capacity that no remaining item fits well — the all-or-nothing constraint breaks the exchange argument.

---

**Q: Interval scheduling — maximum non-overlapping intervals.** ⭐

Sort intervals by **end time**, then scan left to right, greedily taking any interval whose start is `>=` the end of the last interval taken. This maximizes the *count* of non-overlapping intervals — it's the classic "activity selection" problem, modeling one resource (a meeting room, a single CPU, an exam slot) and many requests.

```java
static int maxMeetings(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[1] - b[1]);   // by end time
    int count = 0, lastEnd = Integer.MIN_VALUE;
    for (int[] iv : intervals) {
        if (iv[0] >= lastEnd) { count++; lastEnd = iv[1]; }
    }
    return count;
}
// maxMeetings({{1,3},{2,4},{3,5},{0,6}}) -> 2  (picks [1,3] then [3,5])
```

The subtlety interviewers probe is *why end time and not start time or duration*: taking the interval that finishes earliest always leaves the most room on the timeline for everything after it. Sorting by start time lets a long early meeting block out two short ones that would otherwise fit; sorting by duration has a similar counterexample. Total cost is O(n log n), dominated by the sort.

*Follow-up: What if you wanted the maximum-weight set of non-overlapping intervals (each interval has a value, not just a slot)?* That's no longer safely greedy — weighted interval scheduling requires DP: sort by end time, then for each interval either skip it or take it plus the best solution among intervals that end before its start (found via binary search), taking the max.

---

**Q: Merge overlapping intervals / insert interval / meeting rooms.** ⭐⭐

Merging intervals is a different problem from scheduling: instead of picking a *subset*, you combine everything into the minimal set of disjoint ranges covering the same points. Sort by **start** time; scan through, and if the next interval's start is `<=` the current merged interval's end, extend the end to the max of the two; otherwise close out the current merged interval and start a new one. O(n log n) for the sort, O(n) for the merge, O(n) output space.

```java
static int[][] mergeIntervals(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    List<int[]> merged = new ArrayList<>();
    for (int[] iv : intervals) {
        if (merged.isEmpty() || merged.get(merged.size() - 1)[1] < iv[0]) {
            merged.add(iv);
        } else {
            merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], iv[1]);
        }
    }
    return merged.toArray(new int[0][]);
}
// mergeIntervals({{1,3},{2,6},{8,10},{15,18}}) -> [[1,6],[8,10],[15,18]]
```

"Meeting rooms II" (minimum rooms needed to host all meetings, i.e. maximum simultaneous overlap) is a *third*, different problem — it's not solved by either the scheduling loop or the merge loop. It needs a min-heap of active end times (push a meeting's end when it starts, pop/compare when a new meeting starts before the heap's minimum end) or a sweep line over separated `+1`/`-1` start/end events, tracking the running maximum.

*Follow-up: Given a new interval to insert into an already-sorted, already-disjoint list, how do you do it in one pass without re-sorting?* Walk the list once: copy over every interval that ends strictly before the new interval starts, then merge every interval that overlaps the new interval into it (expanding its bounds), then copy over the rest. O(n), no sort needed since the input is already sorted.

---

**Q: Jump game (reachable? / min jumps).** ⭐

**Reachability (Jump Game I)**: track `farthest`, the farthest index reachable so far. Scan left to right; the moment the scan index `i` exceeds `farthest`, you're stuck — return false. Otherwise `farthest = max(farthest, i + nums[i])`. O(n) time, O(1) space — you never need to know *which* jump path gets you there, only the single best reach-so-far.

**Minimum jumps (Jump Game II)**, assuming reachability is guaranteed: maintain a "current jump window" `[?, curEnd]` and a `farthest` reachable with one more jump. When the scan index `i` reaches `curEnd` — the current jump budget is exhausted — increment `jumps` and advance `curEnd = farthest`. This mirrors BFS level-by-level expansion, but done with two integers instead of an explicit queue.

```java
static boolean canJump(int[] nums) {
    int farthest = 0;
    for (int i = 0; i < nums.length; i++) {
        if (i > farthest) return false;
        farthest = Math.max(farthest, i + nums[i]);
    }
    return true;
}

static int minJumps(int[] nums) {
    int jumps = 0, curEnd = 0, farthest = 0;
    for (int i = 0; i < nums.length - 1; i++) {
        farthest = Math.max(farthest, i + nums[i]);
        if (i == curEnd) { jumps++; curEnd = farthest; }
    }
    return jumps;
}
```

Both are O(n)/O(1). A common trap: a `0` in the array is not automatically fatal for `canJump` — it only matters if it's the *only* way to extend `farthest` at that particular scan position; an earlier, longer jump may have already pushed `farthest` past it.

*Follow-up: Why does `minJumps` loop stop at `nums.length - 1` instead of `nums.length`?* Because you never need to "jump" once you're already standing on the last index — including the last index in the scan would risk incrementing `jumps` one time too many if `curEnd` happens to land exactly on it.

---

**Q: Best time to buy and sell stock.** ⭐

Single transaction: scan once, tracking the minimum price seen so far and the best profit achievable by selling today against that minimum (`price - minPrice`). This is safe because for any fixed sell day, the best possible buy day is always the cheapest price seen *before* it — there's no exchange argument scenario where a pricier earlier buy beats the running minimum. O(n) time, O(1) space.

```java
static int maxProfit(int[] prices) {
    int minPrice = Integer.MAX_VALUE, best = 0;
    for (int p : prices) {
        minPrice = Math.min(minPrice, p);
        best = Math.max(best, p - minPrice);
    }
    return best;
}
// maxProfit({7,1,5,3,6,4}) -> 5 (buy at 1, sell at 6)
```

Unlimited transactions: greedily sum every positive day-to-day delta (`prices[i] - prices[i-1]` whenever positive) — mathematically equivalent to buying at the start and selling at the end of every uninterrupted up-run. Still greedy because the decisions don't interact. The moment a constraint couples consecutive decisions — a cooldown day after selling, a transaction fee, or a cap of `k` total transactions — the problem stops being greedy-separable and needs a DP state machine (`dp[i][holding]` or `dp[i][k][holding]`).

*Follow-up: Why does the multi-transaction greedy break once you add a cooldown?* Because summing every positive delta assumes you can buy back in immediately after selling — a cooldown forbids that, which couples "did I sell yesterday" into today's decision, exactly the kind of interaction DP is needed for.

---

**Q: Gas station circuit.**

Given circular arrays `gas[i]` and `cost[i]`, find a station to start from that lets you complete the full circuit (or -1 if impossible). A valid start exists **iff** `sum(gas) >= sum(cost)` — a global necessary-and-sufficient condition. To find it: walk once accumulating a running `tank` from a candidate `start`; whenever `tank` goes negative at station `i`, no station between `start` and `i` inclusive can be valid either (each would hit the same deficit), so jump the candidate to `i + 1` and reset `tank` to 0. O(n), a single pass — no need to re-simulate from every candidate start, which would be the O(n²) brute force.

```java
static int gasStation(int[] gas, int[] cost) {
    int total = 0, tank = 0, start = 0;
    for (int i = 0; i < gas.length; i++) {
        int diff = gas[i] - cost[i];
        total += diff;
        tank += diff;
        if (tank < 0) { start = i + 1; tank = 0; }
    }
    return total >= 0 ? start : -1;
}
```

*Follow-up: Prove why skipping past every station up to `i` is safe when the tank goes negative at `i`.* If the tank first goes negative arriving at `i`, then for any candidate `s` with `start <= s <= i`, the prefix sum from `s` to `i` is a sub-run of an already-negative run from `start` to `i`. Since the run from `start` up to `s` was non-negative (that's why `start` survived that far), removing that non-negative prefix can't turn a negative total positive — so restarting at `s` also goes negative. Hence it's safe to skip straight to `i + 1`.

---

**Q: Give an example where greedy fails.** ⭐

Coin change with denominations `{1, 3, 4}` and target `6`: greedy (always take the largest coin `<=` the remainder) picks `4, 1, 1` — 3 coins — but the optimum is `3, 3` — 2 coins. Greedy fails because taking the biggest coin now can strand you with a remainder that needs many small coins, and there's no exchange argument that rescues it; Phase 11 solves this correctly with DP by considering every coin at every amount.

0/1 knapsack is another canonical failure: sorting items by value/weight ratio and greedily taking the best-ratio items (the strategy that *does* work for fractional knapsack) fails once items can't be split — a high-ratio item might not "fit cleanly," blocking a better combination that DP would find. Longest path in a general graph is a third example — greedily extending the current longest edge doesn't guarantee a globally longest path, and in fact the general longest-path problem is NP-hard, unlike shortest path.

*Follow-up: How do you go about proving a proposed greedy rule is wrong, given the time constraints of an interview?* Try to construct a small counterexample by hand — 3 or 4 elements is usually enough — rather than trying to disprove the exchange argument abstractly. If you can't find one after a genuine attempt and can sketch why swapping in the greedy choice never hurts, that's reasonable evidence (though not proof) that greedy is safe.

---

**Q: Fractional knapsack vs 0/1 knapsack — why does greedy work for one and not the other?**

Fractional knapsack lets you take *any fraction* of an item, so greedy is provably optimal: sort by value/weight ratio descending, take as much of the best-ratio item as fits, then move to the next-best ratio, repeating until capacity runs out (the last item taken may be a fraction). Because you can always exactly fill remaining capacity with a partial item, there's never any "wasted" capacity that a better ratio ordering could have used — the exchange argument goes through cleanly.

```java
static double fractionalKnapsack(int[] values, int[] weights, int capacity) {
    int n = values.length;
    Integer[] idx = new Integer[n];
    for (int i = 0; i < n; i++) idx[i] = i;
    Arrays.sort(idx, (a, b) -> Double.compare(
        (double) values[b] / weights[b], (double) values[a] / weights[a]));
    double totalValue = 0;
    int remaining = capacity;
    for (int i : idx) {
        if (remaining <= 0) break;
        if (weights[i] <= remaining) { totalValue += values[i]; remaining -= weights[i]; }
        else { totalValue += values[i] * ((double) remaining / weights[i]); remaining = 0; }
    }
    return totalValue;
}
```

0/1 knapsack forbids splitting, so the same ratio-sort greedy can leave capacity that no remaining whole item fits well, blocking a combination that would have been strictly better. That "all-or-nothing" constraint is exactly what breaks the exchange argument, and it's why 0/1 knapsack needs the O(n·W) DP table from Phase 11 instead.

*Follow-up: What's the time complexity difference, and why does it matter in practice?* Fractional knapsack is O(n log n) (just a sort); 0/1 knapsack DP is O(n·W), pseudo-polynomial in the capacity `W`. For large `W` (say, capacity in the millions), the DP table becomes impractically large — this is worth mentioning if an interviewer asks about scaling.

---

**Q: How does Huffman coding use greedy, and where does a heap come in?**

Huffman coding builds an optimal prefix-free binary code minimizing total encoded length, given symbol frequencies. The greedy rule: repeatedly take the two currently-smallest-frequency nodes, merge them into a new node whose frequency is their sum, and push the merged node back into the pool — repeat until one tree remains. A min-heap (Phase 8) is exactly the right structure to efficiently retrieve "the two smallest" at every step in O(log n) each, instead of a linear scan, giving O(n log n) total for n-1 merges.

```java
static String huffmanSketch(int[] freqs) {
    PriorityQueue<Integer> pq = new PriorityQueue<>();
    for (int f : freqs) pq.offer(f);
    int totalBits = 0;
    while (pq.size() > 1) {
        int a = pq.poll(), b = pq.poll();
        totalBits += a + b;
        pq.offer(a + b);
    }
    return "weighted path length = " + totalBits;
}
```

The exchange argument here: the two globally least-frequent symbols can always be placed as siblings at the deepest level of *some* optimal tree, so merging them first never costs anything — the problem then recurses on a smaller frequency set with the merged node standing in for both. Huffman coding underlies real compression: DEFLATE/zip, JPEG, and MP3 all use Huffman coding or a close variant as one stage.

*Follow-up: Why min-heap specifically, and not a sorted array re-sorted each iteration?* Re-sorting an array after every merge would cost O(n log n) *per merge*, O(n² log n) total. A min-heap supports poll and insert in O(log n) each, so n-1 merges cost O(n log n) total — the heap is what makes the greedy loop efficient, not just correct.

---

**Q: How do you decide which technique to use on an unseen problem?** ⭐⭐

Map the problem's phrasing to a pattern signal: sorted array or pair-from-both-ends → two pointers; contiguous subarray/substring under a constraint → sliding window; range sums or "subarray sums to k" → prefix sum (+ hash map); "have I seen this / counts / grouping" → hash map/set; nearest-greater/smaller or span problems → monotonic stack; top/kth/most-extreme repeatedly or merge-k → heap; all combinations/permutations/subsets → backtracking; tree/hierarchy aggregation → DFS/BFS recursion; shortest path unweighted → BFS; weighted shortest path (non-negative) → Dijkstra; dependency ordering / cycle detection in a DAG → topological sort; dynamic connectivity/components/MST → Union-Find; "find in sorted" or "minimize X subject to feasible(X)" with monotonic feasibility → binary search (on the array or on the answer); count ways / min-max cost / reachability where choices interact → DP; a locally-optimal choice that's provably safe (often intervals, ratios) → greedy.

| Signal in the problem | Pattern | Phase |
|---|---|---|
| Sorted array, pair from both ends | Two pointers | 2 |
| Contiguous window, longest/shortest under constraint | Sliding window | 2 |
| Subarray sum = k | Prefix sum + hash map | 2 / 5 |
| Nearest greater/smaller, histogram | Monotonic stack | 4 |
| top/kth, merge-k, median | Heap | 8 |
| All combinations/permutations/subsets | Backtracking | 6 |
| Shortest path, unweighted | BFS | 9 |
| Dependencies / cycle in DAG | Topological sort | 9 |
| "Minimize X s.t. feasible(X)", monotonic | Binary search on answer | 10 |
| Choices interact, count ways / min-max | DP | 11 |
| Provably-safe local choice, intervals/ratios | Greedy | 12 |

*Follow-up: What do you do when two patterns both seem to fit?* State the brute force first, then prefer whichever pattern gives the lower complexity while still being provably correct — greedy over DP whenever you can complete the exchange argument, since it's simpler to code and faster to run.

---

**Q: Walk me through your general problem-solving process.**

Clarify inputs, constraints, and edge cases first (empty input, single element, duplicates, negative numbers, overflow) — this alone often reveals which pattern applies. State the brute-force approach and its complexity, even if you won't code it, because it anchors the conversation and gives you something to optimize from. Recognize the pattern from the signals above, then optimize — often by trading space for time (a hash map, a prefix array, a heap) or by proving a greedy rule is safe instead of paying for a full DP table. Code carefully with edge cases in mind, dry-run on a small example by hand to catch off-by-one and boundary bugs, then state the final time and space complexity explicitly. Communicating this process out loud is as important as arriving at working code — interviewers are evaluating how you think, not just whether the final answer compiles.

*Follow-up: An interviewer says your first solution is correct but not optimal — how do you respond?* Acknowledge the complexity of the current solution, identify specifically what's being repeated or wasted (recomputation, a nested scan that could be a hash lookup, an unnecessary sort), and propose the concrete data structure or technique that removes it — don't just say "we could optimize this," name the mechanism.
