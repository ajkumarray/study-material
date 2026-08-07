# DSA Capstone — The Must-Know Checklist

The ~15 problems that, between them, exercise every pattern in this track. If
you can solve and *explain the pattern of* each of these, you're interview-ready
across the whole surface. Each maps to a phase where the technique lives (with a
worked, tested implementation).

## The list

| # | Problem | Pattern | Phase | Target complexity |
|---|---------|---------|-------|-------------------|
| 1 | Two Sum | hash map complement | 5 | O(n) |
| 2 | Longest Substring Without Repeating Chars | variable sliding window | 2 | O(n) |
| 3 | Maximum Subarray (Kadane) | 1D DP / running best | 2/11 | O(n) |
| 4 | Merge Intervals | sort + greedy sweep | 12 | O(n log n) |
| 5 | Reverse Linked List | pointer reversal | 3 | O(n)/O(1) |
| 6 | Linked List Cycle (detect + start) | fast/slow (Floyd) | 3 | O(n)/O(1) |
| 7 | Valid Parentheses | stack matching | 4 | O(n) |
| 8 | Daily Temperatures / Next Greater | monotonic stack | 4 | O(n) |
| 9 | Binary Tree Level Order | BFS with a queue | 7 | O(n) |
| 10 | Validate BST / Kth Smallest in BST | inorder / range bounds | 7 | O(n) |
| 11 | Number of Islands | grid DFS/BFS flood fill | 9 | O(rows·cols) |
| 12 | Course Schedule (can finish?) | topological sort / cycle | 9 | O(V+E) |
| 13 | Kth Largest / Top-K Frequent | heap (size K) | 8 | O(n log k) |
| 14 | Subsets / Permutations | backtracking | 6 | O(2ⁿ)/O(n!) |
| 15 | Coin Change / Climbing Stairs | DP (tabulation) | 11 | O(n·m)/O(n) |
| 16 | Binary Search + "on the answer" | binary search | 10 | O(log n) |
| 17 | Search in Rotated Sorted Array | modified binary search | 10 | O(log n) |

*(All 17 have runnable, asserted implementations in their phase folders.)*

## How to use it
- **Cold recall:** for each, name the pattern and sketch the approach *before* looking. That recognition speed is what interviews test.
- **Re-derive from scratch:** implement it in a blank file, run with `java -ea`, compare to the phase version.
- **Explain out loud:** state the brute force, the optimization, and the final time/space — narrating is half the interview score.

## The meta-skill (Phase 12's cheatsheet, condensed)
Recognize the trigger → pick the pattern:
- sorted / pair-from-ends → **two pointers**
- contiguous window + constraint → **sliding window**
- range/subarray sums → **prefix sum (+ map)**
- seen? / counts / groups → **hashing**
- nearest greater/smaller → **monotonic stack**
- kth / extreme repeatedly → **heap**
- all combinations/permutations → **backtracking**
- tree / combine children → **DFS/BFS**
- fewest steps (unweighted) → **BFS**; weighted → **Dijkstra**
- ordering/deps → **topological sort**; connectivity → **Union-Find**
- find in sorted / minimize-X-feasible → **binary search (on array / answer)**
- count ways / min-max, choices interact → **DP**
- provably-safe local choice / intervals → **greedy**

## Beyond this track
Advanced topics to explore later if a role demands them: tries, segment/Fenwick
trees, advanced graph (A*, Bellman-Ford, MST, network flow), string algorithms
(KMP, Rabin-Karp, suffix structures), bit manipulation tricks, and tree/interval
DP. The 12 phases here are the foundation those build on.
