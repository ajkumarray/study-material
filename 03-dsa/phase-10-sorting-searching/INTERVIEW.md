<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · graphs](../phase-9-graphs/NOTES.md) | [Phase 11 · dynamic programming ➡](../phase-11-dynamic-programming/NOTES.md)
<!-- /nav -->

# Phase 10 — Sorting & Searching: Interview Q&A + Problems

⭐ = asked constantly.

**Q: Compare merge sort and quick sort.** ⭐⭐
Merge: always O(n log n), stable, but O(n) extra space; great when stability or guaranteed performance matters (and for external/linked-list sorting). Quick: O(n log n) average but O(n²) worst (bad pivots), in-place O(log n) stack, not stable; fastest in practice with good pivots (random/median-of-three).

**Q: What is a stable sort and when does it matter?** ⭐
Stable = equal keys retain their input order. Matters for multi-key sorting (sort by name, then stably by age keeps names ordered within each age). Merge sort and insertion sort are stable; quick sort and heap sort are not.

**Q: What's the lower bound for comparison sorting? How do counting/radix beat it?** ⭐
Ω(n log n) for comparison-based sorts (decision-tree argument). Counting/radix/bucket sort achieve O(n) by not comparing — they exploit bounded integer keys (index by value/digit). Only applicable when keys fit that structure.

**Q: Write binary search. What are the classic bugs?** ⭐⭐
`lo <= hi`, `mid = lo + (hi-lo)/2` (avoid `(lo+hi)` overflow), move `lo`/`hi` strictly past `mid` (avoid infinite loops). Test size 0/1/2 and present/absent targets.

**Q: Find the first/last occurrence of a value (with duplicates).** ⭐
Binary search that, on a match, continues left (first) or right (last) instead of returning — lower/upper bound. Enables counting occurrences in O(log n).

**Q: Search in a rotated sorted array.** ⭐⭐
At each step one half is sorted; check which, test whether the target lies in that half, and recurse accordingly. O(log n). A very common variant.

**Q: What is "binary search on the answer"?** ⭐⭐
When the answer is a number in a range and a feasibility predicate is monotonic, binary-search the answer space, checking feasibility each step. Problems: minimum eating speed (Koko), ship capacity in D days, split array largest sum, smallest divisor. Recognize "minimize/maximize X subject to a condition."

**Q: Find the Kth largest/smallest element.** ⭐
Quickselect — partition and recurse into one side — O(n) average, O(1) space; or a size-K heap (Phase 8) O(n log k) that also streams. State both and their trade-offs.

**Q: Median of two sorted arrays.**
Binary search on the smaller array's partition point to split both into equal halves with left ≤ right — O(log(min(m,n))). A hard but classic binary-search problem.

**Q: How would you sort a nearly-sorted array, or a huge file that doesn't fit in memory?**
Nearly-sorted: insertion sort (O(n·k)) or Timsort exploit existing runs. Huge file: external merge sort — sort chunks that fit in RAM, then k-way merge with a heap.

**Q: Sort colors / Dutch national flag.**
Three-way partition with three pointers (low, mid, high) in one pass, O(n) time, O(1) space — the partitioning idea behind quicksort's 3-way variant.
