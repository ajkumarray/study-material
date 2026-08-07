<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · linked lists ➡](../phase-3-linked-lists/NOTES.md)
<!-- /nav -->

# Phase 2 — Arrays & Strings: Notes

Arrays/strings are the most common interview category. Four patterns cover most of them — recognizing *which* pattern applies is the whole skill.

## 2.1 — Two pointers

Two indices moving through the array, usually **O(n) time, O(1) space**. Variants:
- **Converging** (`lo`/`hi` from both ends) — works on a **sorted** array or when comparing ends: pair-sum, palindrome check, reverse-in-place, container-with-most-water.
- **Same-direction** (slow/fast) — dedupe a sorted array in place, move zeros, partition (and linked-list cycle detection, Phase 3).

Key insight for sorted two-sum: if the sum is too small, only moving `lo` up can help; too big, only `hi` down. Each step eliminates a whole row/column of possibilities → O(n) instead of O(n²).

## 2.2 — Sliding window

A contiguous sub-range `[start, end]` that expands/contracts to maintain a property — turning many O(n²) "check every subarray" problems into **O(n)** (each element enters and leaves the window at most once).
- **Fixed size k:** compute the first window, then slide — add the entering element, subtract the leaving one (`sum += a[i] - a[i-k]`) in O(1). Max/min/avg of every k-window.
- **Variable size:** grow `end`; when the constraint breaks, advance `start` until valid again. "Longest substring without repeating characters," "smallest subarray with sum ≥ target," "longest substring with at most k distinct." Often paired with a hash map / frequency count of the window's contents.

Recognize it when the problem says *contiguous subarray/substring* + *longest/shortest/max/min* under a constraint.

## 2.3 — Prefix sums

Precompute cumulative totals (`pre[i] = sum of first i elements`) in O(n); then **any range sum is O(1)**: `sum(a[l..r]) = pre[r+1] - pre[l]`. Trades O(n) space for O(1) queries — ideal when there are many range queries.

**The powerful combo — prefix sum + hash map:** count/find subarrays with a target sum. Since `sum(a[i..j]) = pre[j+1] - pre[i]`, a subarray sums to `k` whenever two prefix sums differ by `k`. Walk once, and for each running prefix ask "how many earlier prefixes equal `running - k`?" via a hash map → **O(n)** for "count subarrays summing to k" (would be O(n²) by brute force). Same trick: subarray with equal 0s/1s, divisible-sum subarrays.

## 2.4 — Kadane's algorithm (max subarray)

Maximum-sum contiguous subarray in **O(n)** — a one-line DP: `bestEndingHere = max(x, bestEndingHere + x)` ("start fresh at x, or extend the previous run"), tracking the global best. The template for many "best contiguous run" problems (max product needs tracking min too, since negatives flip). This is your first taste of dynamic programming (Phase 11).

## General array notes
- **In-place** algorithms use O(1) extra space by overwriting the input (reverse, dedupe, Dutch-flag partition) — often what "optimize the space" is asking for.
- **Strings are immutable in Java** (Phase 1.5) — build results with `StringBuilder` or a `char[]`, not `+=` in a loop.
- **Sorting first** (O(n log n)) often unlocks two-pointer/greedy O(n) follow-ups — worth it when the input isn't required to stay ordered.
- Watch the boundaries: off-by-one on window edges and inclusive/exclusive range ends are the most common bugs — the half-open convention (Java 1.5) helps.
