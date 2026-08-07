<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 03 — Data Structures & Algorithms

The fundamentals that underpin every other track and dominate technical
interviews. Practiced in **Java** (track 01) — you already met many pieces
(ArrayList growth, HashMap buckets, recursion, `PriorityQueue`, tree/heap
ideas); here we build them from scratch and learn the patterns that solve
whole families of problems.

Format: runnable, **self-verifying** `.java` per topic (assertions in `main`,
run with `java File.java`), plus phase-wise `NOTES.md` (complexity + the "why")
and `INTERVIEW.md` (the classic problems + patterns). ⭐ = asked constantly.

## Curriculum

### Phase 1 — Complexity & foundations ✅
- [x] 1.1 Big-O / Θ / Ω; time vs space; how to analyze
- [x] 1.2 Common complexity classes; amortized analysis; recursion cost

### Phase 2 — Arrays & Strings ✅
- [x] 2.1 Two pointers (pair sum, dedupe, reverse)
- [x] 2.2 Sliding window (fixed & variable)
- [x] 2.3 Prefix sums; Kadane's; interval basics

### Phase 3 — Linked Lists ✅
- [x] 3.1 Singly/doubly; build, traverse, insert, delete
- [x] 3.2 Reversal; fast/slow pointers; cycle detection (Floyd)
- [x] 3.3 Merge, middle, remove-Nth, reorder

### Phase 4 — Stacks & Queues ✅
- [x] 4.1 Stack/queue/deque; array vs linked implementations
- [x] 4.2 Monotonic stack (next greater element, histogram)
- [x] 4.3 Queue via stacks; min-stack

### Phase 5 — Hashing ✅
- [x] 5.1 Hash maps/sets; frequency counting; grouping
- [x] 5.2 Two-sum family; subarray-sum patterns

### Phase 6 — Recursion & Backtracking ✅
- [x] 6.1 Recursion mechanics; subproblems; the call stack
- [x] 6.2 Backtracking: subsets, permutations, combinations
- [x] 6.3 Constraint problems: N-Queens, generate parentheses

### Phase 7 — Trees & BSTs ✅
- [x] 7.1 Binary trees; DFS traversals (pre/in/post), BFS level order
- [x] 7.2 BST: insert/search, validation, kth smallest, LCA
- [x] 7.3 Common problems: depth, diameter, balanced, symmetric

### Phase 8 — Heaps / Priority Queues ✅
- [x] 8.1 Binary heap from scratch; heapify; the JDK `PriorityQueue`
- [x] 8.2 Top-K, kth largest, top-K frequent, running median

### Phase 9 — Graphs ✅
- [x] 9.1 Representations; BFS & DFS; islands/flood fill
- [x] 9.2 Topological sort; cycle detection
- [x] 9.3 Shortest paths (Dijkstra); Union-Find (DSU)

### Phase 10 — Sorting & Searching ✅
- [x] 10.1 The sorts: insertion, merge, quick; stability; quickselect
- [x] 10.2 Binary search & the "search space" pattern (on answer, rotated array)

### Phase 11 — Dynamic Programming ✅
- [x] 11.1 Memoization vs tabulation; 1D DP (stairs, robber, coin change, LIS)
- [x] 11.2 2D DP (grid paths, LCS, edit distance)
- [x] 11.3 Knapsack family (0/1, partition)

### Phase 12 — Greedy & patterns wrap-up ✅
- [x] 12.1 Greedy (intervals, jump game, gas station); when greedy vs DP
- [x] 12.2 Pattern cheatsheet; how to pick a technique in an interview

### Capstone ✅
- [x] `capstone/MUST-KNOW.md` — 17 curated must-know problems mapped to their
      patterns/phases, plus the trigger→technique cheatsheet.

## Structure

```
03-dsa/
├── README.md
├── phase-1-foundations/
│   ├── NOTES.md
│   ├── INTERVIEW.md
│   └── *.java          <- runnable, self-verifying
└── ...
```

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · foundations** — [Notes](phase-1-foundations/NOTES.md) · [Interview](phase-1-foundations/INTERVIEW.md)
- **Phase 2 · arrays** — [Notes](phase-2-arrays/NOTES.md) · [Interview](phase-2-arrays/INTERVIEW.md)
- **Phase 3 · linked lists** — [Notes](phase-3-linked-lists/NOTES.md) · [Interview](phase-3-linked-lists/INTERVIEW.md)
- **Phase 4 · stacks queues** — [Notes](phase-4-stacks-queues/NOTES.md) · [Interview](phase-4-stacks-queues/INTERVIEW.md)
- **Phase 5 · hashing** — [Notes](phase-5-hashing/NOTES.md) · [Interview](phase-5-hashing/INTERVIEW.md)
- **Phase 6 · recursion backtracking** — [Notes](phase-6-recursion-backtracking/NOTES.md) · [Interview](phase-6-recursion-backtracking/INTERVIEW.md)
- **Phase 7 · trees** — [Notes](phase-7-trees/NOTES.md) · [Interview](phase-7-trees/INTERVIEW.md)
- **Phase 8 · heaps** — [Notes](phase-8-heaps/NOTES.md) · [Interview](phase-8-heaps/INTERVIEW.md)
- **Phase 9 · graphs** — [Notes](phase-9-graphs/NOTES.md) · [Interview](phase-9-graphs/INTERVIEW.md)
- **Phase 10 · sorting searching** — [Notes](phase-10-sorting-searching/NOTES.md) · [Interview](phase-10-sorting-searching/INTERVIEW.md)
- **Phase 11 · dynamic programming** — [Notes](phase-11-dynamic-programming/NOTES.md) · [Interview](phase-11-dynamic-programming/INTERVIEW.md)
- **Phase 12 · greedy** — [Notes](phase-12-greedy/NOTES.md) · [Interview](phase-12-greedy/INTERVIEW.md)
<!-- /phases-nav -->
