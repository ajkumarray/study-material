<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 11 · dynamic programming](../phase-11-dynamic-programming/NOTES.md)
<!-- /nav -->

# Phase 12 — Greedy & Patterns Wrap-up: Notes

All code below is from `GreedyPatterns.java` in this directory (run with `java -ea GreedyPatterns.java`) unless a section is explicitly marked as "not implemented in this file" — those are classic interview problems that round out the greedy picture and are included here as extra study material.

## 1. What Is a Greedy Algorithm

A **greedy algorithm** builds a solution step by step, at each step making the choice that looks best *right now*, and never revisits or undoes that choice. It has no backtracking and (usually) no memory of alternatives it passed over. It exists because many optimization problems have a special structure where committing to the locally-best option at every step happens to produce a globally-best result — and when that's true, greedy gets you the DP-quality answer for a fraction of the time/space cost.

- **Two required structural properties**:
  - **Greedy-choice property**: a globally optimal solution can be reached by making a locally optimal (greedy) choice first, then solving the remaining subproblem — you never need to reconsider that first choice later.
  - **Optimal substructure**: an optimal solution to the whole problem contains optimal solutions to its subproblems (this is the *same* requirement DP has — see the comparison table below).
- **No "undo" step**: unlike backtracking (Phase 6) or DP (Phase 11), a greedy algorithm commits. There's no `dp` table of "what if I'd chosen differently" and no recursive exploration of alternatives — that's exactly why it's fast, and exactly why it's only safe when the greedy-choice property provably holds.
- **Typical shape**: sort the input by some key (O(n log n)), then a single linear scan applying a simple local rule (O(n)). Total: **O(n log n)**, dominated by the sort. A handful of greedy problems (gas station, jump game) don't even need a sort and run in O(n) flat.
- **Proving correctness is the hard part**: writing the greedy loop is usually five lines. Proving *why* it's correct is the actual interview-level skill, and it's almost always done with an **exchange argument** (below).

### The exchange argument (proof technique)

An exchange argument proves a greedy choice is safe by contradiction: assume some optimal solution `OPT` does *not* make the greedy choice, then show you can transform `OPT` into a solution that *does* make the greedy choice, without making it any worse. If that transformation always exists, the greedy choice is at least as good as whatever `OPT` did, so greedy is safe to use.

```java
// Interval scheduling exchange argument, informally:
// Claim: picking the interval with the EARLIEST end time first is always safe.
//
// Suppose OPT does NOT pick the earliest-ending interval e first; it picks
// some other interval x first instead (end[x] > end[e]).
// Since end[e] <= end[x], swapping e in for x in OPT's schedule:
//   - never overlaps whatever came after x in OPT (e ends no later than x did)
//   - frees up strictly more room for later picks
// So "OPT with x replaced by e" is a solution at least as good as OPT.
// By induction this swap can be repeated until the schedule matches greedy's.
// => greedy (earliest end time first) is optimal.
```

In this example: the argument never computes an actual better solution — it just shows that *any* optimal solution can be reshaped, interval by interval, into the one greedy would have produced, without losing anything. That "greedy is never worse" argument is the standard template for proving interval scheduling, Huffman coding, and fractional knapsack are all correct.

- **Why it's useful**: interviewers rarely require a full formal proof, but saying *"I'll use an exchange argument: assume an optimal solution differs from greedy at the first choice, show swapping in the greedy choice doesn't hurt"* signals you understand *why* the algorithm works, not just that it happens to pass the test cases.
- **When greedy fails**: if you cannot construct that exchange argument — or worse, you can find a counterexample — greedy is unsafe and you need DP instead. The canonical counterexample: **coin change** with denominations `{1, 3, 4}` and target `6`. Greedy (always take the largest coin ≤ remaining) picks `4 + 1 + 1` = 3 coins. The optimal answer is `3 + 3` = 2 coins. Greedy fails here because taking the biggest coin now can strand you with a bad remainder — there's no exchange argument that saves it, and Phase 11 solves this correctly with DP (`dp[amount]` = min coins, considering *every* coin at *every* amount).

**Summary — Key Takeaways:**
- Greedy commits to one choice per step and never reconsiders; DP/backtracking explore alternatives.
- Needs greedy-choice property + optimal substructure to be *correct*, not just fast.
- Typical cost: O(n log n) for a sort + O(n) scan.
- Prove correctness with an exchange argument: "swapping the optimal solution's first choice for greedy's doesn't make it worse."
- If you can find a counterexample (like coin change on `{1,3,4}`), greedy is wrong for that problem — use DP.

---

## 2. Interval Scheduling / Activity Selection (`maxMeetings`)

**Interval scheduling** (a.k.a. **activity selection**) asks: given a set of intervals (start, end) — think meetings on a shared calendar, or jobs on a single machine — what's the *maximum number* of non-overlapping intervals you can pick? This models any "one resource, many requests, maximize how many you can serve" scenario.

- **Greedy rule**: sort by **end time**, then walk through and take every interval whose start is `>=` the end of the last interval you took.
- **Why end time, not start time**: taking the interval that finishes *earliest* leaves the most room on the timeline for everything after it — that's exactly the exchange argument from section 1. Sorting by start time or by duration does **not** work in general (a long meeting starting early can block out two short ones that would've fit).
- **Time Complexity**: O(n log n) — dominated by the sort; the scan afterward is O(n).
- **Space Complexity**: O(1) extra (O(n) or O(log n) for the sort itself, depending on the sort algorithm), ignoring the input array.

```java
static int maxMeetings(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[1] - b[1]);   // by end time
    int count = 0, lastEnd = Integer.MIN_VALUE;
    for (int[] iv : intervals) {
        if (iv[0] >= lastEnd) { count++; lastEnd = iv[1]; }   // take it
    }
    return count;
}

// maxMeetings({{1,3},{2,4},{3,5},{0,6}})
// sorted by end time: [1,3] [2,4] [3,5] [0,6]   (ends: 3, 4, 5, 6)
// [1,3]: 1 >= -inf  -> take. count=1, lastEnd=3
// [2,4]: 2 >= 3?  no -> skip (overlaps [1,3])
// [3,5]: 3 >= 3   yes -> take. count=2, lastEnd=5
// [0,6]: 0 >= 5?  no -> skip
// returns 2   (the schedule is [1,3] then [3,5])
```

In this example: sorting by end time puts `[1,3]` first even though `[0,6]` starts earlier and `[2,4]` ends only one tick later — end time is what matters. After taking `[1,3]`, `[2,4]` is rejected because `2 < 3` (it overlaps), but `[3,5]` is accepted because `3 >= 3` (back-to-back is allowed, not overlapping). The wide `[0,6]` is rejected last because by the time it's considered, `lastEnd` is already `5`.

| Sort key tried | Works? | Counterexample |
|---|---|---|
| Earliest **end** time | Yes (provably optimal) | — |
| Earliest **start** time | No | `[1,10]` then `[2,3]`,`[4,5]` blocked by the long one |
| **Shortest duration** first | No | Two short intervals that overlap can block a schedule that would fit 3 non-overlapping ones elsewhere |

**Why it's useful**: this is the textbook model for meeting-room booking, single-CPU job scheduling (maximize jobs completed), and exam/interview slot assignment — anywhere you have one resource and want to serve the most non-conflicting requests.

**Summary — Key Takeaways:**
- Sort by **end time**; greedily take an interval if its start is `>=` the last taken end.
- O(n log n) total; correctness proven by exchange argument (earliest finish frees the most future room).
- Sorting by start time or duration is a common wrong-answer trap — know why end time is special.

---

## 3. Merge Overlapping Intervals (`mergeIntervals`)

**Merging intervals** collapses a set of possibly-overlapping ranges into the minimal set of disjoint ranges that cover exactly the same points. Where activity selection picks a *subset*, merging *combines* everything into a clean, non-overlapping timeline — useful for calendars, memory-block coalescing, and range-based data cleanup.

- **Greedy rule**: sort by **start time**. Walk through; if the next interval's start is `<=` the current merged interval's end, extend the end (`max` of the two ends); otherwise the current merged interval is finished — emit it and start a new one.
- **Time Complexity**: O(n log n) for the sort, O(n) for the merge pass.
- **Space Complexity**: O(n) for the output list (plus sort space).
- **Contrast with `maxMeetings`**: same family of problem (intervals), opposite sort key intuition purpose — here we sort by **start** because we're asking "what ranges touch?", not "what's the earliest thing I can free up?".

```java
static int[][] mergeIntervals(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    List<int[]> merged = new ArrayList<>();
    for (int[] iv : intervals) {
        if (merged.isEmpty() || merged.get(merged.size() - 1)[1] < iv[0]) {
            merged.add(iv);                                    // no overlap -> new interval
        } else {
            merged.get(merged.size() - 1)[1] =
                Math.max(merged.get(merged.size() - 1)[1], iv[1]);
        }
    }
    return merged.toArray(new int[0][]);
}

// mergeIntervals({{1,3},{2,6},{8,10},{15,18}})
// sorted by start: [1,3] [2,6] [8,10] [15,18]
// [1,3]: merged empty -> add. merged = [[1,3]]
// [2,6]: last end=3, 3 < 2? no (3 is NOT < 2) -> overlap -> extend: merged=[[1,6]]
// [8,10]: last end=6, 6 < 8? yes -> no overlap -> add. merged=[[1,6],[8,10]]
// [15,18]: last end=10, 10 < 15? yes -> add. merged=[[1,6],[8,10],[15,18]]
// returns [[1,6],[8,10],[15,18]]
```

In this example: `[2,6]` overlaps `[1,3]` because `2 <= 3` (their ranges touch at `2` and `3`), so instead of emitting a separate interval we stretch the last one's end to `6`. `[8,10]` starts after `6` so it's a genuinely new, disjoint block. The final result covers the same points as the input but with the minimum number of ranges.

### Related: Minimum Platforms / Meeting Rooms II (not implemented in this file)

A close cousin: given intervals, find the **maximum number simultaneously overlapping** (e.g., "how many meeting rooms do I need?" or "how many train platforms?"). This is *not* solved by the merge-intervals loop above — it needs either a min-heap of active end times, or a **sweep line** over separated start/end events:

```java
// Sweep-line sketch: +1 at every start, -1 at every end, sort events by time
// (ties: process ends before starts if touching intervals should NOT count as
// overlapping), track a running count, answer = running max.
static int minPlatforms(int[][] intervals) {
    int n = intervals.length;
    int[] starts = new int[n], ends = new int[n];
    for (int i = 0; i < n; i++) { starts[i] = intervals[i][0]; ends[i] = intervals[i][1]; }
    Arrays.sort(starts);
    Arrays.sort(ends);
    int platforms = 0, maxPlatforms = 0, s = 0, e = 0;
    while (s < n) {
        if (starts[s] <= ends[e]) { platforms++; s++; maxPlatforms = Math.max(maxPlatforms, platforms); }
        else { platforms--; e++; }
    }
    return maxPlatforms;
}
```

**Why it's useful**: interval merging is the standard technique for compacting calendar free/busy blocks, coalescing adjacent free memory blocks in an allocator, and simplifying overlapping numeric ranges before further processing (e.g., before running `maxMeetings`-style scheduling on the result).

**Summary — Key Takeaways:**
- Sort by **start**; merge when `next.start <= currentMerged.end`, else emit and start fresh.
- O(n log n) total, O(n) output space.
- "Max simultaneous overlap" (meeting rooms II / min platforms) is a different problem from merging — needs a heap or sweep line, not the merge loop.

---

## 4. Jump Game — Reachability (`canJump`)

Given an array where `nums[i]` is the max jump length from index `i`, **can you reach the last index** starting from index 0? This models reachability under a locally-known "how far can I go from here" budget — think network hop limits or fuel-range planning.

- **Greedy rule**: track the single number `farthest` — the farthest index reachable using any starting jump so far. Scan left to right; if you ever reach an index `i` that is *beyond* `farthest`, you were never able to get there — fail immediately. Otherwise keep extending `farthest = max(farthest, i + nums[i])`.
- **Key Insight**: you don't need to know *which* jump gets you the farthest, or track any path — only the single best reach-so-far. That's what makes it greedy instead of a full BFS/DP over paths.
- **Time Complexity**: O(n), one pass.
- **Space Complexity**: O(1).

```java
static boolean canJump(int[] nums) {
    int farthest = 0;
    for (int i = 0; i < nums.length; i++) {
        if (i > farthest) return false;                        // stuck: can't reach i
        farthest = Math.max(farthest, i + nums[i]);
    }
    return true;
}

// canJump({2,3,1,1,4})
// i=0: 0 > 0? no.  farthest = max(0, 0+2) = 2
// i=1: 1 > 2? no.  farthest = max(2, 1+3) = 4
// i=2: 2 > 4? no.  farthest = max(4, 2+1) = 4
// i=3: 3 > 4? no.  farthest = max(4, 3+1) = 4
// i=4: 4 > 4? no.  farthest = max(4, 4+4) = 8
// loop finishes -> true

// canJump({3,2,1,0,4})
// i=0: farthest = max(0, 0+3) = 3
// i=1: 1 > 3? no.  farthest = max(3, 1+2) = 3
// i=2: 2 > 3? no.  farthest = max(3, 2+1) = 3
// i=3: 3 > 3? no.  farthest = max(3, 3+0) = 3   <- the 0 here traps you
// i=4: 4 > 3? YES  -> return false
```

In this example: in the second trace, index `3` holds a `0`, so once you land exactly on index 3 you can't move further from *that* index — but greedy doesn't fail there, because `farthest` was already pushed to `3` by an earlier, longer jump. The failure is only detected at `i=4`, when the scan pointer finally outruns `farthest`. This is the subtlety that trips people up: greedy fails at the *first unreachable index*, not at the "bad" index that caused it.

**Why it's useful**: reachability-under-per-step-budget shows up in routing/hop-count problems, game level-completion checks, and as a warm-up before the harder "minimum jumps" variant below.

**Summary — Key Takeaways:**
- Track only `farthest` reachable index; fail the instant the scan pointer `i` exceeds it.
- O(n) time, O(1) space — no need to enumerate actual jump choices.
- A `0` in the array isn't automatically fatal; it only matters if it's the *only* way to extend `farthest` at that point.

---

## 5. Jump Game II — Minimum Jumps (`minJumps`)

A harder variant: **given** that the end is reachable, what is the **minimum number of jumps** needed to get there? This is greedy's version of a BFS level-order traversal, done in O(n) instead of O(n) with an explicit queue.

- **Greedy rule**: maintain a "current jump's window" `[?, curEnd]` — the farthest you can already reach using the jumps counted so far. While scanning, keep extending `farthest` (the farthest reachable with *one more* jump). The instant the scan index `i` reaches `curEnd` (you've exhausted everything the current jump count can reach), you *must* take another jump: `jumps++`, and the window becomes `curEnd = farthest`.
- **Why this is like BFS**: `curEnd` is exactly "the last index reachable at BFS depth `jumps`"; `farthest` is "the best reach achievable at depth `jumps + 1`". Advancing the window when `i == curEnd` is the same move as finishing one BFS level and starting the next — except here it's done with two integers instead of a queue.
- **Time Complexity**: O(n), one pass.
- **Space Complexity**: O(1).

```java
static int minJumps(int[] nums) {
    int jumps = 0, curEnd = 0, farthest = 0;
    for (int i = 0; i < nums.length - 1; i++) {   // stop BEFORE the last index
        farthest = Math.max(farthest, i + nums[i]);
        if (i == curEnd) { jumps++; curEnd = farthest; }       // window exhausted -> jump
    }
    return jumps;
}

// minJumps({2,3,1,1,4})     nums.length - 1 = 4, so i runs 0..3
// i=0: farthest = max(0, 0+2) = 2.   i==curEnd(0)? yes -> jumps=1, curEnd=2
// i=1: farthest = max(2, 1+3) = 4.   i==curEnd(2)? no (1 != 2)
// i=2: farthest = max(4, 2+1) = 4.   i==curEnd(2)? yes -> jumps=2, curEnd=4
// i=3: farthest = max(4, 3+1) = 4.   i==curEnd(4)? no (3 != 4)
// loop ends -> jumps = 2   (e.g. index 0 -> index 1 -> index 4)
```

In this example: the first jump's window is `[0,2]` (reachable using 1 jump from index 0). At `i=2` the scan hits the edge of that window, forcing a second jump, whose window becomes `[?,4]` — which already covers the last index (index 4), so 2 jumps suffice. Note the loop bound is `nums.length - 1`, not `nums.length` — you never need to "jump" once you're already standing on the last index.

| | `canJump` (Jump Game I) | `minJumps` (Jump Game II) |
|---|---|---|
| Question | Is the end reachable at all? | Minimum jumps to reach the end (assumes reachable) |
| State tracked | `farthest` only | `farthest` **and** `curEnd` (current window) |
| Failure/answer condition | `i > farthest` -> false | window exhausted (`i == curEnd`) -> count a jump |
| Analogy | Single greedy reach check | BFS level-by-level, done with 2 integers |
| Complexity | O(n) / O(1) | O(n) / O(1) |

**Why it's useful**: minimum-hop problems (fewest flights, fewest network hops, fewest "moves" in a grid with per-cell jump ranges) reduce to this exact window-expansion pattern whenever the "reachability budget" is known upfront per position, avoiding a full BFS's queue overhead.

**Summary — Key Takeaways:**
- Two integers, `curEnd` (current jump's reach) and `farthest` (next jump's best reach), replace an entire BFS queue.
- Increment `jumps` exactly when the scan index catches up to `curEnd`.
- Loop stops at `nums.length - 1` — reaching the last index needs no further jump.
- Same O(n)/O(1) cost as `canJump`, but answers a harder question.

---

## 6. Best Time to Buy and Sell Stock (`maxProfit`)

Given daily prices, find the **maximum profit from one buy and one sell** (buy before you sell). This is a one-pass greedy that also reads as a specialization of Kadane's maximum-subarray algorithm (Phase 2/11) applied to day-to-day price deltas.

- **Greedy rule**: scan once, tracking the lowest price seen *so far* (`minPrice`) and the best profit achievable by selling *today* against that minimum (`price - minPrice`). Keep the running best.
- **Why it's safe**: for a fixed sell day, the best possible buy day is always the cheapest price *before* it — there's no scenario where a more expensive earlier buy beats the running minimum. That's the entire exchange argument: buying at anything other than the running minimum is never better.
- **Time Complexity**: O(n), one pass.
- **Space Complexity**: O(1).

```java
static int maxProfit(int[] prices) {
    int minPrice = Integer.MAX_VALUE, best = 0;
    for (int p : prices) {
        minPrice = Math.min(minPrice, p);
        best = Math.max(best, p - minPrice);
    }
    return best;
}

// maxProfit({7,1,5,3,6,4})
// p=7: minPrice=7   best=max(0, 7-7=0)=0
// p=1: minPrice=1   best=max(0, 1-1=0)=0
// p=5: minPrice=1   best=max(0, 5-1=4)=4
// p=3: minPrice=1   best=max(4, 3-1=2)=4
// p=6: minPrice=1   best=max(4, 6-1=5)=5
// p=4: minPrice=1   best=max(5, 4-1=3)=5
// returns 5   (buy at 1, sell at 6)
```

In this example: profit never comes from buying at `7` (the first price) because a cheaper price (`1`) shows up right after. Once `minPrice` locks to `1`, every later day is just checked against it — the algorithm never needs to "remember" that `7` existed once a smaller price replaces it.

### Extensions (interview follow-ups, not implemented in this file)

| Variant | Approach | Why |
|---|---|---|
| One transaction (this file) | Greedy: track min price so far | Only one buy/sell pair, no interaction between decisions |
| **Unlimited transactions** | Greedy: sum every positive `prices[i] - prices[i-1]` | Buying/selling every up-tick is equivalent to holding through the whole up-run — still no interaction between decisions |
| With **cooldown** (must wait a day after selling) or **transaction fee** | DP: `dp[i][holding]` state machine | A constraint now couples consecutive decisions — no longer greedily separable |
| At most **k** transactions | DP: `dp[i][k][holding]` | Choices interact through the shared budget `k` — classic DP, no valid greedy |

**Why it's useful**: this exact "track the best-so-far reference point, compare every new element against it" shape reappears constantly — max subarray (Kadane), max-difference problems, and any "best pair where one side must come before the other" question.

**Summary — Key Takeaways:**
- Track `minPrice` seen so far; compare today's price against it for the running best profit.
- O(n)/O(1) — no DP table needed for the single-transaction case.
- The moment a constraint links one decision to the next (cooldown, fee, k-limit), the problem stops being greedy and becomes DP.

---

## 7. Gas Station Circuit (`gasStation`)

Given circular arrays `gas[i]` (fuel available at station `i`) and `cost[i]` (fuel needed to drive from station `i` to `i+1`), find a starting station from which you can complete the full circuit, or `-1` if impossible.

- **Feasibility check**: a valid start exists **iff** `sum(gas) >= sum(cost)` — total fuel must at least cover total cost (this is a global necessary-and-sufficient condition; no starting point can rescue a circuit whose totals don't add up).
- **Greedy rule**: walk once, accumulating a running `tank`. Whenever `tank` goes negative at station `i`, **no station between the current `start` and `i` (inclusive) can be a valid start either** — each of them would run the tank down through the same deficit — so jump the candidate start to `i + 1` and reset `tank` to 0.
- **Correctness intuition**: this is another exchange-argument shape. If the tank first goes negative arriving at `i`, then for any candidate start `s` with `start <= s <= i`, the partial sum from `s` to `i` is a *sub-run* of an already-negative total run from `start` to `i` — since prefix sums of the `diff` array from `start` are all `>= 0` up to `s` (by definition of how far `start` survived) and the whole thing still goes negative, restarting at `s` only removes a non-negative prefix, so it goes negative too. So skip straight past `i`.
- **Time Complexity**: O(n), a single pass — no need to re-simulate from every candidate start.
- **Space Complexity**: O(1).

```java
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

// gasStation(gas={1,2,3,4,5}, cost={3,4,5,1,2})
// i=0: diff=1-3=-2  total=-2  tank=-2  tank<0 -> start=1, tank=0
// i=1: diff=2-4=-2  total=-4  tank=-2  tank<0 -> start=2, tank=0
// i=2: diff=3-5=-2  total=-6  tank=-2  tank<0 -> start=3, tank=0
// i=3: diff=4-1=3   total=-3  tank=3   (no reset)
// i=4: diff=5-2=3   total=0   tank=6
// total = 0 >= 0 -> return start = 3
```

In this example: stations `0`, `1`, and `2` each individually run the tank negative, so the candidate start is bumped past each of them in turn. By the time station `3` is reached, `tank` resets to `0` and stays non-negative for the rest of the loop (`+3` then `+3`), and the grand `total` comes out to exactly `0`, which is `>= 0`, so station `3` is confirmed as a valid start.

**Why it's useful**: this "running total dips negative -> reset candidate start" pattern is the general technique for circular feasibility problems — it also appears in problems like "minimum rotations to make an array's prefix sums non-negative" and certain scheduling-with-deadlines problems.

**Summary — Key Takeaways:**
- Global feasibility: `sum(gas) >= sum(cost)`.
- Whenever the running tank dips negative, no station up to and including the current one can be a valid start — jump `start` to `i + 1`.
- Single O(n) pass finds the answer; no need to brute-force every starting station (which would be O(n^2)).

---

## 8. Fractional Knapsack (greedy) vs 0/1 Knapsack (DP) — not implemented in this file

Given items with `value` and `weight`, and a knapsack capacity `W`, maximize total value.

- **Fractional knapsack** (you may take *any fraction* of an item): **greedy is optimal**. Sort items by `value/weight` ratio descending; take as much as you can of the best-ratio item, then the next, until capacity runs out (the last item taken may be a fraction).
- **0/1 knapsack** (each item is all-or-nothing): **greedy fails**; you need DP (Phase 11's knapsack family). Being forced to take a whole item or none means a high-ratio item might not "fit cleanly," so the locally best choice can block a better combination.

```java
// Fractional knapsack — greedy is provably optimal here.
static double fractionalKnapsack(int[] values, int[] weights, int capacity) {
    int n = values.length;
    Integer[] idx = new Integer[n];
    for (int i = 0; i < n; i++) idx[i] = i;
    // sort items by value/weight ratio, descending
    Arrays.sort(idx, (a, b) -> Double.compare(
        (double) values[b] / weights[b], (double) values[a] / weights[a]));

    double totalValue = 0;
    int remaining = capacity;
    for (int i : idx) {
        if (remaining <= 0) break;
        if (weights[i] <= remaining) {
            totalValue += values[i];              // take it whole
            remaining -= weights[i];
        } else {
            totalValue += values[i] * ((double) remaining / weights[i]);  // take a fraction
            remaining = 0;
        }
    }
    return totalValue;
}
```

| | Fractional knapsack | 0/1 knapsack |
|---|---|---|
| Can split an item? | Yes | No |
| Correct approach | Greedy (sort by value/weight ratio) | DP (Phase 11) |
| Why greedy works/fails | Always safe to top up with the next-best ratio; no "wasted" partial capacity | Being forced to take a whole item can leave capacity that no remaining item fits well, so the greedy pick can block a better combination |
| Time complexity | O(n log n) (sort) | O(n * W) (DP table) |

**Why it's useful**: this pair is *the* standard interview example for teaching "greedy works when you can always fully exploit each step's choice; DP is needed the moment an all-or-nothing constraint creates interaction between choices."

**Summary — Key Takeaways:**
- Fractional knapsack: sort by value/weight ratio, greedily fill — O(n log n), always optimal.
- 0/1 knapsack: same setup, but all-or-nothing breaks greedy — needs DP, O(n·W).
- This is the single cleanest "greedy vs DP" teaching example to cite in an interview.

---

## 9. Huffman Coding — not implemented in this file

**Huffman coding** builds an optimal prefix-free binary code for a set of symbols given their frequencies, minimizing total encoded length. It's a greedy algorithm built on a **min-heap** (Phase 8): repeatedly pull the two least-frequent nodes, merge them into a new node (frequency = sum), and push the merged node back, until one tree remains.

- **Greedy rule**: always merge the two *currently* smallest-frequency nodes. Never reconsider a merge once made.
- **Why it's safe**: an exchange argument shows the two globally least-frequent symbols can always be placed as siblings at the deepest level of *some* optimal tree — so merging them first never costs you anything, and the problem then recurses on the smaller frequency-set with the merged node standing in for both.

```java
static String huffmanSketch(int[] freqs) {
    PriorityQueue<Integer> pq = new PriorityQueue<>();
    for (int f : freqs) pq.offer(f);
    int totalBits = 0;
    while (pq.size() > 1) {
        int a = pq.poll(), b = pq.poll();          // two smallest
        totalBits += a + b;                        // cost of this merge
        pq.offer(a + b);                            // merged node re-enters the pool
    }
    return "weighted path length (total encoded bits) = " + totalBits;
}
```

**Why it's useful**: Huffman coding underlies real compression formats (DEFLATE/zip, JPEG, MP3 use Huffman coding or a close variant as one stage), and it's the classic example of "greedy + heap" working together — the heap efficiently gives you "the two smallest" at every step in O(log n) instead of a linear scan.

**Summary — Key Takeaways:**
- Greedy + min-heap: always merge the two smallest-frequency nodes.
- O(n log n) total (n-1 heap merges, each O(log n)).
- Real-world payoff: the compression stage behind zip/JPEG/MP3.

---

## 10. Other Classic Greedy Problems (brief, not implemented in this file)

- **Assign Cookies** (LeetCode 455): sort both children's greed factors and cookie sizes; two-pointer greedily match the smallest cookie that satisfies the smallest unsatisfied child. O(n log n).
  ```java
  static int assignCookies(int[] greed, int[] cookies) {
      Arrays.sort(greed);
      Arrays.sort(cookies);
      int child = 0, cookie = 0;
      while (child < greed.length && cookie < cookies.length) {
          if (cookies[cookie] >= greed[child]) child++;   // this cookie satisfies this child
          cookie++;
      }
      return child;
  }
  ```
- **Task Scheduler with cooldown** (LeetCode 621): given tasks and a cooldown `n` between identical tasks, greedily schedule the most-frequent remaining task type each slot (or, equivalently, compute the answer directly from `(maxFreq - 1) * (n + 1) + numTasksWithMaxFreq`). Uses a max-heap in the simulation form.
- **Minimum number of arrows to burst balloons** (LeetCode 452): same shape as `maxMeetings` — sort by end coordinate, greedily place an arrow at the end of the first unburst balloon, burst everything it overlaps.
- **Non-overlapping intervals — minimum removals** (LeetCode 435): the complement of `maxMeetings` — `total intervals - maxMeetings(...)` gives the minimum number to remove.

**Summary — Key Takeaways:**
- Many "assign smallest that satisfies smallest requirement" problems reduce to sorting both sides and a two-pointer sweep.
- Several interval problems (balloons-to-burst, min-removals) are the *same* end-time-sort greedy as `maxMeetings`, just phrased differently — recognize the shape.

---

## 11. Greedy vs Dynamic Programming — The Decision

Both greedy and DP require **optimal substructure**. The difference is what happens with the choices:

| Dimension | Greedy | Dynamic Programming |
|---|---|---|
| Choice per step | Commit to one, locally-best choice, never revisit | Consider all viable choices, combine/compare subproblem results |
| Needs optimal substructure? | Yes | Yes |
| Needs overlapping subproblems? | No | Yes (that's *why* memoization/tabulation pays off) |
| Typical complexity | O(n log n) (sort) or O(n) | O(n·k) / O(n^2) / O(n·W) — proportional to state space × transitions |
| Space | O(1)–O(n) | O(states), sometimes reducible with rolling arrays |
| Correctness proof | Exchange argument (case-by-case, problem-specific) | Recurrence correctness (usually more mechanical once the state is defined) |
| Fails when… | The locally-best choice can strand you with a worse remainder (coin change on `{1,3,4}`, 0/1 knapsack) | N/A — DP is the fallback that's always correct if brute force would be, just faster |
| Canonical example pair | Fractional knapsack, interval scheduling, Huffman coding | 0/1 knapsack, coin change (arbitrary denominations), LCS/edit distance |

**Decision process**: try to state the greedy rule and its exchange argument. If you can complete the argument (or recognize the problem as a known-safe greedy shape like "sort by end time" or "ratio-based fill"), use greedy — it will be faster and simpler to code. If you instead find a counterexample, or the choices clearly interact (a choice now changes what's optimal later in a way that isn't just "less room remains"), fall back to DP.

**Summary — Key Takeaways:**
- Both need optimal substructure; only DP needs (and exploits) overlapping subproblems.
- Greedy: commit and never look back, O(n log n) typical, proven via exchange argument.
- DP: explore and combine, proven via a correct recurrence, needed whenever choices interact.
- When stuck deciding, hunt for a counterexample to the greedy rule — finding one means "use DP"; failing to find one (and being able to sketch the exchange argument) means "greedy is safe."

---

## 12. How to Pick a Technique — The Interview Cheatsheet

A quick lookup from problem phrasing to pattern, spanning everything covered across all 12 phases of this track:

| The problem says… | Reach for |
|---|---|
| sorted array / pair from both ends | **two pointers** (Phase 2) |
| contiguous subarray/substring, longest/shortest under a constraint | **sliding window** (Phase 2) |
| range sums / subarray sum = k | **prefix sum (+ hash map)** (Phase 2/5) |
| "have I seen", counts, grouping, O(1) lookup | **hash map/set** (Phase 5) |
| nearest greater/smaller element, spans, histogram | **monotonic stack** (Phase 4) |
| top/kth/most-extreme repeatedly, merge-k, median | **heap** (Phase 8) |
| all combinations/permutations/subsets, constraint search | **backtracking** (Phase 6) |
| tree/hierarchy, "for each node combine children" | **DFS/BFS recursion** (Phase 7) |
| shortest path / fewest steps (unweighted) | **BFS** (Phase 9) |
| weighted shortest path (non-negative) | **Dijkstra** (Phase 9) |
| dependencies / ordering / cycle in a DAG | **topological sort** (Phase 9) |
| dynamic connectivity / components / MST | **Union-Find** (Phase 9) |
| find in sorted, or "minimize X s.t. feasible(X)" monotonic | **binary search (on array / on answer)** (Phase 10) |
| count ways / min-max cost / reachability, choices interact | **DP** (Phase 11) |
| locally-optimal choice is provably safe; intervals | **greedy** (Phase 12) |

**General interview flow**: clarify constraints → brute force + its complexity → identify the pattern from the triggers above → optimize (often "can a hash map / sort / two pointers cut this to O(n)/O(n log n)?") → code carefully (edge cases: empty, single, duplicates, overflow) → test on a small example → state final time/space. Narrating this out loud is as important as the answer itself.

**Summary — Key Takeaways:**
- This table is a lookup, not a decision procedure — always sanity-check with a small trace before committing to a pattern.
- "Sort by X, then greedy scan" is the recurring greedy shape; recognize it fast (intervals -> sort by end; ratio-fill -> sort by ratio).
- When two patterns both seem to fit, prefer the one with the lower complexity that still passes correctness (greedy over DP when provably safe).
