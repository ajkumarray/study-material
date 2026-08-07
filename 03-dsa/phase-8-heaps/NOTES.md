<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · trees](../phase-7-trees/NOTES.md) | [Phase 9 · graphs ➡](../phase-9-graphs/NOTES.md)
<!-- /nav -->

# Phase 8 — Heaps / Priority Queues: Notes

## 8.1 — The binary heap
A **binary heap** is a *complete* binary tree stored in an **array** (no node objects/pointers). The **heap property**: in a min-heap every parent ≤ its children, so the minimum is always at index 0 (max-heap: parent ≥ children). Array index math:
```
parent(i) = (i-1)/2     left(i) = 2i+1     right(i) = 2i+2
```
Operations:
- **peek** (min/max) → **O(1)** (it's the root).
- **push**: append at the end, **sift up** (swap with parent while it violates) → **O(log n)**.
- **pop**: take the root, move the last element to the root, **sift down** (swap with the smaller child while it violates) → **O(log n)**.
- **build-heap** from n elements → **O(n)** (bottom-up heapify — better than n inserts' O(n log n)).

Not sorted internally — only the root is guaranteed extreme. Popping repeatedly yields sorted order (that's **heapsort**, Phase 10).

## 8.2 — In Java
`java.util.PriorityQueue` **is** a binary min-heap. `offer`/`poll`/`peek`. For a **max-heap**, pass `Collections.reverseOrder()` or a custom `Comparator` (e.g., by frequency, by distance). Comparators are how you heap-order objects (Phase 3.2's Comparator, reused).

## 8.3 — The patterns
- **Top-K / Kth largest** — keep a **size-K heap**: a *min*-heap for the K *largest* (evict the smallest when it overflows; the root is the Kth largest). **O(n log k)** and streaming — better than sorting (O(n log n)) when k ≪ n. (Quickselect does Kth-largest in O(n) average, Phase 10.)
- **Top-K frequent** — count with a hash map, then a size-K heap ordered by frequency. (Bucket sort by frequency does it in O(n).)
- **Merge K sorted lists/arrays** — a heap of the K current heads; poll the smallest, push its successor. O(N log k) for N total elements.
- **Two-heap running median** — a **max-heap for the lower half** + a **min-heap for the upper half**, kept balanced in size; the median is a top (odd count) or the average of both tops (even). `add` is O(log n), `median` is O(1). The elegant answer to a streaming problem.
- **Scheduling / greedy-by-priority** — task scheduler, meeting rooms (min-heap of end times), K closest points, Dijkstra's shortest path (Phase 9). Any "repeatedly take the current best" is a heap.

## When a heap vs alternatives
- Need *only* the extreme repeatedly → heap (don't fully sort).
- Need everything sorted → just sort (O(n log n)).
- Need ordered *and* searchable/range queries → `TreeMap`/`TreeSet` (balanced BST).
- Need the Kth element once, offline → quickselect O(n) average.
