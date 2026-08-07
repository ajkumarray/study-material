<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 11 · dynamic programming](../phase-11-dynamic-programming/NOTES.md)
<!-- /nav -->

# Phase 12 — Greedy & Patterns Wrap-up: Notes

## 12.1 — Greedy algorithms
A **greedy** algorithm builds a solution by making the choice that looks best *right now*, never reconsidering. Simple and fast — usually a sort (O(n log n)) plus a linear scan. But it's **only correct when a local optimum yields a global optimum** (the *greedy-choice property* + optimal substructure).

**The catch:** proving greedy works is the hard part — typically an **exchange argument** ("any optimal solution can be transformed into the greedy one without getting worse"). When the greedy choice *isn't* safe, greedy gives a wrong answer and you need DP (Phase 11). Example: coin change is greedy for standard coin systems but **not** for arbitrary denominations (`coins=[1,3,4]`, `amount=6`: greedy 4+1+1=3 coins, optimal 3+3=2) — which is exactly why Phase 11 solved it with DP.

**Classic greedy problems (in the code):**
- **Interval scheduling** (max non-overlapping): sort by **end time**, take each meeting starting after the last taken. Earliest-finish-first is provably optimal.
- **Merge intervals:** sort by **start**, extend or emit.
- **Jump game / min jumps:** track the farthest reachable index; extend a jump window.
- **Best time to buy/sell stock:** track the min price so far, update best profit (one-pass greedy).
- **Gas station:** total feasibility + reset the start when the running tank goes negative.
- Others: activity selection, Huffman coding, fractional knapsack (greedy!, unlike 0/1 which needs DP), assign cookies, task scheduling, minimum platforms.

**Greedy vs DP — the decision:** if a locally optimal choice is provably safe → greedy (faster, simpler). If choices interact and you must consider combinations / reuse subproblem results → DP. When unsure, try to find a counterexample to greedy; if you can't and can argue the exchange, greedy wins.

## 12.2 — How to pick a technique (the interview cheatsheet)

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

**General interview flow:** clarify constraints → brute force + its complexity → identify the pattern from the triggers above → optimize (often "can a hash map / sort / two pointers cut this to O(n)/O(n log n)?") → code carefully (edge cases: empty, single, duplicates, overflow) → test on a small example → state final time/space. Narrating this is as important as the answer.
