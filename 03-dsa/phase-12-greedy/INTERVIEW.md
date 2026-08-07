<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 11 · dynamic programming](../phase-11-dynamic-programming/NOTES.md)
<!-- /nav -->

# Phase 12 — Greedy & Wrap-up: Interview Q&A + Problems

⭐ = asked constantly.

**Q: What is a greedy algorithm, and when is it correct?** ⭐⭐
It makes the locally optimal choice at each step and never backtracks. Correct only when the problem has the **greedy-choice property** (a local optimum leads to a global optimum) and optimal substructure. Proving it usually needs an exchange argument; otherwise use DP.

**Q: Greedy vs dynamic programming — how do you decide?** ⭐⭐
Both need optimal substructure. Greedy commits to one choice without reconsidering (fast, but only when that choice is provably safe); DP explores choices and reuses overlapping subproblems (needed when choices interact). Classic contrast: coin change is greedy for canonical coins but requires DP for arbitrary denominations.

**Q: Interval scheduling — maximum non-overlapping intervals.** ⭐
Sort by end time; greedily take each interval that starts at/after the last taken one's end. Earliest-finish-first maximizes count. O(n log n).

**Q: Merge overlapping intervals / insert interval / meeting rooms.** ⭐⭐
Sort by start; merge when the next start ≤ current end. Meeting rooms II counts max overlap via a min-heap of end times (Phase 8) or a sweep line. O(n log n).

**Q: Jump game (reachable? / min jumps).** ⭐
Reachable: track the farthest index reachable; if you ever stand beyond it, fail. Min jumps: greedily extend a jump window, incrementing the count when the window is exhausted. Both O(n).

**Q: Best time to buy and sell stock.** ⭐
Single transaction: track the minimum price so far and the best profit in one pass — O(n). Multiple transactions: sum every positive day-to-day delta (greedy). With cooldown/fees or k transactions: DP.

**Q: Gas station circuit.**
If total gas ≥ total cost, a start exists; find it by resetting the candidate start to `i+1` whenever the running tank goes negative. O(n) — a neat greedy correctness argument.

**Q: Give an example where greedy fails.** ⭐
Coin change with `[1,3,4]` for amount 6: greedy picks 4+1+1 (3 coins) but optimal is 3+3 (2). Also 0/1 knapsack (greedy by value/weight ratio fails — that's fractional knapsack); longest path; these need DP.

**Q: How do you decide which technique to use on an unseen problem?** ⭐⭐
Map the problem's signals to a pattern: sorted/pairs → two pointers; contiguous-window constraint → sliding window; counts/lookups → hashing; nearest-greater → monotonic stack; kth/extreme → heap; all-combinations → backtracking; graph/shortest → BFS/Dijkstra; ordering → topological sort; "minimize X s.t. feasible" → binary search on the answer; interacting choices/counting ways → DP; provably-safe local choice → greedy. State brute force first, then optimize.

**Q: Walk me through your general problem-solving process.**
Clarify inputs/constraints/edge cases → brute force with its complexity → recognize the pattern → optimize (often trading space for time) → code with edge cases in mind → dry-run a small example → state final time/space complexity. Communication throughout matters as much as the solution.
