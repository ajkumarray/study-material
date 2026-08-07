<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · trees](../phase-7-trees/NOTES.md) | [Phase 9 · graphs ➡](../phase-9-graphs/NOTES.md)
<!-- /nav -->

# Phase 8 — Heaps / Priority Queues: Interview Q&A + Problems

⭐ = asked constantly.

**Q: What is a heap and what are its operation costs?** ⭐⭐
A complete binary tree in an array with the heap property (parent ≤ children for a min-heap). peek O(1), push/pop O(log n) via sift-up/sift-down, build-heap O(n). The root is the min (or max); it's not fully sorted.

**Q: How is a heap stored, and what's the index math?**
As an array: for index `i`, parent = `(i-1)/2`, children = `2i+1`, `2i+2`. No pointers — contiguous and cache-friendly.

**Q: Kth largest element.** ⭐⭐
Maintain a min-heap of size k while scanning; the root is the kth largest. O(n log k). Alternative: quickselect, O(n) average, O(1) space (Phase 10). Know both and their trade-offs.

**Q: Top K frequent elements.** ⭐
Count frequencies (hash map), then a size-K heap ordered by frequency → O(n log k); or bucket sort by frequency → O(n).

**Q: For "top K largest," do you use a min-heap or a max-heap?** ⭐ *trap*
A **min-heap of size K** — so the smallest of your current top-K sits at the root and is cheap to evict when a bigger element arrives. (A max-heap of all n also works but is O(n log n) and non-streaming.)

**Q: Find the median of a data stream.** ⭐⭐
Two heaps: a max-heap for the lower half, a min-heap for the upper half, balanced so their sizes differ by ≤1. Median = the larger heap's top (odd) or the average of both tops (even). add O(log n), median O(1).

**Q: Merge K sorted lists.** ⭐
Min-heap of the K current heads; repeatedly poll the smallest and push its list's next element. O(N log K) for N total elements — beats merging pairwise naively.

**Q: How do you make a max-heap in Java?**
`new PriorityQueue<>(Collections.reverseOrder())` or a custom comparator. Java's `PriorityQueue` is a min-heap by default.

**Q: Build-heap in O(n) — how, when n inserts is O(n log n)?**
Bottom-up heapify: sift-down every non-leaf from the last parent up. The cost telescopes because most nodes are near the bottom with small sift distances, summing to O(n).

**Q: Heap vs balanced BST (TreeMap)?**
Heap: O(1) peek-extreme, O(log n) push/pop, but no ordered search/range/iteration. BST: O(log n) search/insert/delete AND ordered operations (floor/ceiling/range/sorted iteration). Use a heap for "repeatedly take the extreme," a BST when you also need order/search.

**Q: K closest points to origin / task scheduler / meeting rooms II.**
All are heaps: size-K heap by distance; a heap of task cooldowns; a min-heap of meeting end times to count overlapping rooms. Recognize "repeatedly need the current best/earliest."
