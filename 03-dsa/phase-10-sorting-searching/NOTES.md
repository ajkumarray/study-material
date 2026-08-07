<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · graphs](../phase-9-graphs/NOTES.md) | [Phase 11 · dynamic programming ➡](../phase-11-dynamic-programming/NOTES.md)
<!-- /nav -->

# Phase 10 — Sorting & Searching: Notes

## 10.1 — The sorts
In production you call `Arrays.sort`/`Collections.sort` (a tuned dual-pivot quicksort for primitives, **Timsort** — a merge/insertion hybrid — for objects). Interviews want you to *know* the classics:

| Sort | Time (avg / worst) | Space | Stable? | Notes |
|---|---|---|---|---|
| Bubble/Selection | O(n²) / O(n²) | O(1) | bubble yes, selection no | teaching only |
| **Insertion** | O(n²) / O(n²), **O(n)** nearly-sorted | O(1) | yes | great on small/nearly-sorted; used inside Timsort/quicksort for tiny subarrays |
| **Merge sort** | O(n log n) / **O(n log n)** | O(n) | **yes** | guaranteed n log n; the stable choice; external sort |
| **Quick sort** | O(n log n) / **O(n²)** | O(log n) | no | fastest in practice; worst case on bad pivots (mitigate with random/median pivot) |
| **Heap sort** | O(n log n) / O(n log n) | O(1) | no | in-place n log n; poor cache locality (Phase 8) |
| Counting/Radix/Bucket | O(n) / O(n) | O(n+k) | yes | **not** comparison sorts; need bounded integer keys |

**Stability** = equal elements keep their relative order — matters when sorting by a secondary key. **O(n log n) is the proven lower bound for comparison sorts**; counting/radix beat it only by exploiting key structure. Know the recurrence `T(n)=2T(n/2)+O(n)=O(n log n)` (merge sort, Master Theorem).

## 10.2 — Binary search (the real prize)
Halve the search space each step → **O(log n)**. Memorize the template: `lo <= hi`, `mid = lo + (hi-lo)/2` (overflow-safe), move `lo`/`hi` past `mid`.

**Beyond "find in a sorted array":**
- **Lower/upper bound** (first/last occurrence with duplicates): on a match, don't stop — keep searching left (first) or right (last). The basis of `Arrays.binarySearch`-style range queries, insertion points, count-of-value.
- **Rotated sorted array:** one half is always sorted; determine which, test if the target lies within it, recurse into the right half. Still O(log n).
- **Binary search on the ANSWER** — the powerful generalization. When the answer is a value in a range and feasibility is **monotonic** ("if speed s works, so does any s' > s"), binary-search the *answer space* with a feasibility check: minimum eating speed, ship-capacity-in-D-days, split-array-largest-sum, "smallest divisor." Recognize it by "minimize/maximize X such that a condition holds." Complexity: O(log(range) × cost-of-check).
- **Search space on a matrix**, **peak element**, **median of two sorted arrays** (O(log(min(m,n)))) — all binary-search variants.

## 10.3 — Quickselect
Kth smallest/largest **without fully sorting**: partition like quicksort, but recurse into only the side containing the target. **O(n) average** (O(n²) worst), O(1) space — beats the heap approach (O(n log k)) when you can mutate the array and want a single Kth query. It's how you'd answer "median without sorting."

## Practical notes
- Sorting first (O(n log n)) frequently unlocks a two-pointer or greedy O(n) follow-up (Phases 2, 12) — often the intended first step.
- Custom order → a `Comparator` (Phase 3.2); careful with `a - b` overflow (use `Integer.compare`).
- The off-by-one and infinite-loop bugs in binary search come from the boundary update and `mid` rounding — test on size 0, 1, 2 and both target-present/absent.
