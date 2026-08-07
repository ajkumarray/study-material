<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · arrays ➡](../phase-2-arrays/NOTES.md)
<!-- /nav -->

# Phase 1 — Complexity: Interview Q&A

⭐ = asked constantly.

**Q: What is Big-O notation?** ⭐⭐
A description of how an algorithm's running time (or space) grows as input size grows, ignoring constants and lower-order terms — an upper bound on worst-case cost. It lets you compare algorithms independently of hardware and predict behavior at scale.

**Q: Big-O vs Θ vs Ω?**
Big-O is an upper bound (worst case), Ω a lower bound (best case), Θ a tight bound (both match). In interviews "complexity" usually means worst-case Big-O.

**Q: Order these from fastest- to slowest-growing.** ⭐
O(1) < O(log n) < O(n) < O(n log n) < O(n²) < O(2ⁿ) < O(n!). Know a concrete example of each (hash lookup, binary search, scan, merge sort, nested loops, subsets, permutations).

**Q: Why do we drop constants and lower-order terms?**
Big-O is about growth *rate* as n→∞, where the dominant term and growth curve determine scalability; constants depend on hardware/implementation and don't change the class. `O(3n² + 100n + 5) = O(n²)`.

**Q: What's the time/space complexity of this nested loop / this halving loop?** ⭐
Nested loops over n → O(n²). A loop dividing the range by 2 each step → O(log n). A loop of O(n) work run log n times → O(n log n). Practice narrating: "outer runs n times, inner runs n times, so n×n = O(n²)."

**Q: What is amortized complexity? Example?** ⭐
Average cost per operation across a sequence, even if individual ops vary. `ArrayList.add`/dynamic-array append is amortized O(1): doubling on resize makes rare O(n) copies average to O(1) per add. HashMap put is similar.

**Q: Give a time–space tradeoff example.** ⭐⭐
"Has duplicate?" — nested-loop compare is O(n²) time, O(1) space; a hash set is O(n) time, O(n) space. Two-sum, caching/memoization (DP), and prefix sums are all the same instinct: spend memory to cut time.

**Q: What's the space complexity of a recursive function?**
O(max recursion depth) for the call stack (plus any allocated data). A linear recursion is O(n) stack; balanced divide-and-conquer is O(log n) depth. Deep recursion risks stack overflow.

**Q: Is a lower Big-O always faster in practice?** *senior nuance*
No. Big-O ignores constants and memory effects. For small/realistic n, an O(n²) with tiny constants can beat an O(n log n); contiguous arrays beat linked structures of equal Big-O due to cache locality. Big-O is the first filter; measure for the real workload.

**Q: What's the best possible complexity for comparison-based sorting?**
O(n log n) — provably the lower bound for comparison sorts. Non-comparison sorts (counting/radix/bucket) can do O(n) under constraints on the input (bounded integer keys). (Phase 10.)
