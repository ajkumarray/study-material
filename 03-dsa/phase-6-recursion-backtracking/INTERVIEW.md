<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · hashing](../phase-5-hashing/NOTES.md) | [Phase 7 · trees ➡](../phase-7-trees/NOTES.md)
<!-- /nav -->

# Phase 6 — Recursion & Backtracking: Interview Q&A + Problems

⭐ = asked constantly.

**Q: What is backtracking, and what's the template?** ⭐⭐
Recursion that builds candidates incrementally and abandons a partial one as soon as it can't lead to a valid solution. Template: choose → explore (recurse) → un-choose (undo). The undo restores state for sibling branches.

**Q: Generate all subsets of a set.** ⭐
For each element, branch on include/exclude → 2ⁿ subsets. Or iterate bitmasks 0..2ⁿ−1. Record a copy of the running list at each leaf.

**Q: Generate all permutations.** ⭐⭐
For each position, try every unused element (track with a boolean array), recurse, then release it. n! results, O(n!·n) time. With duplicates: sort and skip equal siblings.

**Q: Combination sum (elements reusable).** ⭐
Backtrack with a `start` index; pass `i` (not `i+1`) to allow reuse; prune when the remaining target goes negative. Sorting enables earlier pruning.

**Q: Solve N-Queens.** ⭐⭐
Place one queen per row; for each candidate column check no earlier queen shares the column or a diagonal (`|Δrow| == |Δcol|`); recurse to the next row; count/collect full placements. Classic constraint-satisfaction backtracking.

**Q: Why is naive recursive Fibonacci exponential, and how do you fix it?** ⭐
It recomputes overlapping subproblems (`fib(n-1)` and `fib(n-2)` both recompute `fib(n-3)`…) → O(2ⁿ). Fix with memoization (cache results) or bottom-up tabulation → O(n). This is the bridge to dynamic programming (Phase 11).

**Q: What determines the space complexity of a recursive solution?**
The maximum recursion depth (call-stack frames), plus any data structures carried. Linear recursion → O(n) stack; deep recursion risks overflow — convert to iteration with an explicit stack if needed.

**Q: How does pruning affect backtracking?** ⭐
It cuts branches that can't yield a solution (overshoot, constraint violation, symmetry), often turning an intractable search into a fast one. The asymptotic worst case stays exponential, but pruning decides real-world feasibility.

**Q: Word search in a grid / Sudoku solver / letter combinations of a phone number.**
All are the same template: from the current cell/position, try each valid next choice, mark it, recurse, unmark. Grid problems mark visited cells and restore them on backtrack.

**Q: Subsets/permutations WITH duplicates — how do you avoid duplicate outputs?**
Sort the input, then within a recursion level skip a candidate equal to its predecessor (`i > start && a[i] == a[i-1]`), so each distinct value starts a branch only once.

**Q: Recursion vs iteration — trade-offs?**
Recursion is often clearer for tree/divide-and-conquer/backtracking problems but costs stack space and risks overflow; iteration (with an explicit stack/queue) is more memory-controlled. Java has no tail-call optimization, so deep linear recursion should be iterative.
