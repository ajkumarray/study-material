<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · trees](../phase-7-trees/NOTES.md) | [Phase 9 · graphs ➡](../phase-9-graphs/NOTES.md)
<!-- /nav -->

# Phase 8 — Heaps / Priority Queues: Interview Q&A + Problems

⭐ = asked constantly.

**Q: What is a heap and what are its operation costs?** ⭐⭐

A binary heap is a *complete* binary tree stored implicitly in an array — no node objects, no pointers, just index arithmetic (`parent(i) = (i-1)/2`, `left(i) = 2i+1`, `right(i) = 2i+2`). It maintains the heap property: in a min-heap, every parent is `<=` both of its children, which guarantees the minimum is always at the root (index 0); a max-heap flips the inequality. It is **not** fully sorted — only the root is guaranteed to be extreme; nothing is guaranteed about the relative order of siblings or deeper nodes.

Operation costs: `peek` (look at the root) is O(1). `push` appends at the end of the array, then sifts the new element up toward the root while it violates the heap property — O(log n), since the element travels at most the height of the tree. `pop` takes the root value, moves the *last* array element into the root position, then sifts it down toward whichever child is smaller until the property holds — also O(log n). Building a heap from n elements all at once is O(n) via bottom-up heapify, notably *better* than n individual pushes, which would cost O(n log n).

```java
private void siftDown(int i) {
    while (true) {
        int l = 2 * i + 1, r = 2 * i + 2, smallest = i;
        if (l < n && a[l] < a[smallest]) smallest = l;
        if (r < n && a[r] < a[smallest]) smallest = r;
        if (smallest == i) break;
        swap(i, smallest);
        i = smallest;
    }
}
```

*Follow-up: What does repeatedly popping a min-heap give you?* Values come out in ascending sorted order — every pop returns the current minimum. Build a heap from n elements (O(n)) and pop all n of them (O(n log n) total) and you've just performed heapsort (Phase 10) — the heap operations you already know are the entire mechanism.

---

**Q: How is a heap stored, and what's the index math?**

As a plain array — no pointers, no node objects. For a 0-indexed array, node at index `i` has parent at `(i-1)/2` (integer division), left child at `2i+1`, right child at `2i+2`. This works precisely because the heap is a *complete* binary tree — every level is fully packed except possibly the last, which fills strictly left-to-right with no gaps — so there's a deterministic 1-to-1 mapping between tree position and array index, with no need to store explicit child/parent references.

*Follow-up: Why is contiguous array storage advantageous over a pointer-based tree here?* Cache locality — an array is one contiguous block of memory, so sequential or nearby accesses (which sift-up/sift-down naturally produce, since parent/child indices are close together) benefit from CPU cache prefetching. A pointer-based tree scatters nodes across the heap, causing cache misses on every hop, exactly the same tradeoff discussed for linked lists vs. arrays in Phase 3.

---

**Q: Kth largest element.** ⭐⭐

Maintain a min-heap capped at size k while scanning the array: offer every element, and the moment the heap's size exceeds k, poll (evict) the smallest element. After the full scan, the heap holds exactly the k largest elements seen, and the root — the smallest of that group — is the kth largest overall.

```java
static int kthLargest(int[] a, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();
    for (int x : a) {
        minHeap.offer(x);
        if (minHeap.size() > k) minHeap.poll();
    }
    return minHeap.peek();
}
```

This is O(n log k), which beats a full sort's O(n log n) whenever k is much smaller than n, and it streams (works incrementally without ever holding all n elements sorted). The alternative is **quickselect** (Phase 10) — partition-based selection that finds the kth largest in O(n) *average* time and O(1) extra space, but it's offline (needs the full array up front, no streaming) and has O(n²) worst case without randomized pivot safeguards.

*Follow-up: When would you prefer quickselect over the heap approach, and vice versa?* Quickselect wins for a single, one-shot, offline query on a fixed array — it's asymptotically faster on average. The heap wins whenever the data arrives as a stream (you don't have it all up front), or you need to repeat the query as more data arrives without redoing all the work from scratch.

---

**Q: Top K frequent elements.** ⭐

Two steps: count occurrences with a hash map (`HashMap<value, count>`), then maintain a size-K heap ordered by the *frequency* (not the element's own value) — offer every distinct key, evicting the lowest-frequency element whenever the heap exceeds size K.

```java
PriorityQueue<Integer> heap = new PriorityQueue<>((x, y) -> freq.get(x) - freq.get(y));
for (int key : freq.keySet()) {
    heap.offer(key);
    if (heap.size() > k) heap.poll();
}
```

This is O(n log k) overall (O(n) to build the frequency map, O(m log k) for the heap step over m distinct elements). A strictly faster O(n) alternative exists: **bucket sort by frequency** — since frequency is bounded between 1 and n, create n+1 buckets indexed by frequency, drop each distinct element into its bucket, then read off the top K by scanning from the highest-frequency bucket downward. This avoids comparison-based heap operations entirely by exploiting the bounded range of possible frequencies.

*Follow-up: Why does bucket sort work here but wouldn't generally work for "top K largest by raw value"?* Bucket sort needs a bounded, discrete range to index buckets by. Frequency is naturally bounded (`1` to `n`, the array's length), but raw element *values* could be arbitrarily large or even non-integer, so there's no guaranteed small bucket count to exploit in the general "top K largest value" problem — that's why `kLargest` uses a heap while `topKFrequent` has a genuine O(n) bucket-sort alternative.

---

**Q: For "top K largest," do you use a min-heap or a max-heap?** ⭐ *trap*

A **min-heap of size K** — this is the classic trap, since intuition might suggest a max-heap for "largest" elements. The min-heap's root holds the *smallest* of your current top-K candidates, which is exactly the element you want fast, cheap access to for eviction the moment a bigger element shows up. A max-heap holding *all n* elements also technically produces the correct answer (poll K times), but that costs O(n log n) to build and doesn't stream — you'd need to see all n elements before you could even start popping.

*Follow-up: What if the question were "top K smallest" instead?* Flip it — use a **max-heap of size K**, evicting the largest candidate whenever the heap overflows, so the root always holds the weakest (largest) member of your current top-K-smallest set.

---

**Q: Find the median of a data stream.** ⭐⭐

Maintain two heaps: a **max-heap for the lower half** of values seen so far, and a **min-heap for the upper half**, kept balanced so their sizes differ by at most 1. On every insertion, offer the new value to the low (max) heap first, then move `low`'s current maximum over to `high` (this keeps every value in `low` `<=` every value in `high`, regardless of where the new value actually belonged), and finally rebalance by moving `high`'s minimum back to `low` if `high` became larger. The median is then either the top of whichever heap has one more element (odd total count), or the average of both tops (even total count).

```java
void add(int x) {
    low.offer(x);
    high.offer(low.poll());
    if (high.size() > low.size()) low.offer(high.poll());
}
double median() {
    if (low.size() > high.size()) return low.peek();
    return (low.peek() + high.peek()) / 2.0;
}
```

`add` is O(log n) (a couple of heap push/pops); `median` is O(1) (just peeking two roots) — a dramatic improvement over re-sorting the entire stream on every new value, which would cost O(n log n) per insertion.

*Follow-up: Why does the insertion always go to `low` first, rather than deciding up front which half the new value belongs to?* It's a deliberate simplification: by always routing through `low` and then immediately shuffling the max of `low` into `high`, the code guarantees the split invariant (`every value in low <= every value in high`) holds after every insertion, without needing an explicit comparison against the current median to decide the target heap — the rebalancing step does that work implicitly and uniformly.

*Follow-up: How would you extend this to support removing a value from the stream (not just adding)?* You'd need to locate which heap the value lives in and remove it from the middle of that heap — Java's `PriorityQueue.remove(Object)` supports this but is O(n) (a linear scan to find the element, since a heap array isn't searchable in less than O(n)), so removal breaks the clean O(log n) guarantee this structure otherwise provides.

---

**Q: Merge K sorted lists.** ⭐

Maintain a min-heap containing the current head of each of the K lists. Repeatedly poll the overall smallest element, append it to the result, and push that same list's next element back onto the heap (if it has one). Repeat until the heap empties.

```java
static Node mergeKLists(Node[] lists) {
    PriorityQueue<Node> heap = new PriorityQueue<>((a, b) -> a.val - b.val);
    for (Node head : lists) if (head != null) heap.offer(head);
    Node dummy = new Node(0), tail = dummy;
    while (!heap.isEmpty()) {
        Node smallest = heap.poll();
        tail.next = smallest;
        tail = tail.next;
        if (smallest.next != null) heap.offer(smallest.next);
    }
    return dummy.next;
}
```

This is O(N log K) for N total elements across all K lists — each element is pushed and popped exactly once, at O(log K) per operation since the heap never holds more than K elements simultaneously. This beats naive pairwise merging (merge list 1 into 2, that result into 3, and so on), which costs O(N·K) in the worst case, since finding the minimum among K candidates the naive way costs O(K) instead of the heap's O(log K).

*Follow-up: How does this generalize beyond linked lists — say, merging K sorted arrays or K sorted streams from external sources?* The exact same shape applies: keep a heap of `(currentValue, sourceIndex, positionWithinSource)` tuples, one per source, poll the minimum, advance that source's position, and push its next value if one exists. This is the standard technique behind external merge sort (merging sorted chunks too large to fit in memory) and merging sorted results from multiple database shards.

---

**Q: How do you make a max-heap in Java?**

`new PriorityQueue<>(Collections.reverseOrder())`, or supply any custom `Comparator` that inverts the natural ordering. `PriorityQueue` is a min-heap by default (uses natural `Comparable` ordering), so max-heap behavior always comes from flipping the comparator — the underlying sift-up/sift-down mechanics never change, only what "smaller" means to the heap.

```java
PriorityQueue<Integer> max = new PriorityQueue<>(Collections.reverseOrder());
max.addAll(List.of(4, 1, 7, 3));
max.poll();   // 7
```

*Follow-up: How would you build a max-heap of custom objects, say `Task` objects ordered by priority field descending?* `new PriorityQueue<>((a, b) -> b.priority - a.priority)` — a comparator that returns positive when `a` should be considered "greater" (i.e., come out first), which for a task-priority max-heap means comparing `b.priority - a.priority` so higher-priority tasks sort as "smaller" internally and surface at the root.

---

**Q: Build-heap in O(n) — how, when n inserts is O(n log n)?**

Bottom-up heapify: place all n elements into the array in arbitrary order, then call sift-down on every non-leaf node, starting from the *last* non-leaf and working up to the root (leaves are already trivially valid heaps of size 1, so they're skipped entirely). The total cost telescopes rather than multiplying out to O(n log n): roughly half the nodes are leaves (0 work), a quarter are one level above the leaves (sift-down distance at most 1), an eighth are two levels up (distance at most 2), and so on — summing this geometric-ish series converges to O(n) total, because the vast majority of nodes are near the bottom where sift-down barely has to move anything, and only a handful of nodes near the root pay the full O(log n) cost.

*Follow-up: Why does inserting n elements one at a time cost O(n log n) but bottom-up heapify costs O(n) for the exact same final heap?* Because with one-at-a-time insertion, *every* element potentially has to travel all the way from a leaf position up to the root (O(log n) each, n times). With bottom-up heapify, an element only sifts down as far as its *own* subtree's height — and the overwhelming majority of nodes are near the bottom of the tree, where that height is small, so the average work per node is O(1), not O(log n).

---

**Q: Heap vs balanced BST (TreeMap)?**

A heap gives O(1) peek-extreme and O(log n) push/pop, but it offers **no efficient search, no range queries, and no ordered iteration** — you can only cheaply access the root; finding an arbitrary element or asking "give me everything between X and Y" both require an O(n) scan. A balanced BST (Java's `TreeMap`/`TreeSet`, Phase 7) gives O(log n) search, insert, and delete, **and** ordered operations like `floorKey`/`ceilingKey`/`headMap`/`tailMap`/sorted iteration — strictly more capability, at the cost of a slightly higher constant factor than a heap for the pure "give me the extreme" use case.

| | Heap | TreeMap / TreeSet (balanced BST) |
|---|---|---|
| Peek extreme | O(1) | O(log n) |
| Push / pop extreme | O(log n) | O(log n) |
| Search arbitrary element | O(n) | **O(log n)** |
| Range query / floor / ceiling | Not supported | **O(log n)** |
| Sorted iteration | Not supported (only root is ordered) | **O(n) in sorted order** |

*Follow-up: If a heap can't do range queries, why not just always use a TreeMap instead?* A heap's O(1) peek and slightly cheaper constant factors matter when the *only* thing you ever need is "the current best," repeatedly, with no searching or range logic — e.g., a priority-based task queue or Dijkstra's algorithm. Reaching for a `TreeMap` when you only need "extract-min repeatedly" is over-engineering; reaching for a heap when you actually need "find everything between X and Y" simply doesn't work at all.

---

**Q: K closest points to origin / task scheduler / meeting rooms II.**

All three are variations on "repeatedly need the current best/earliest, possibly with new candidates arriving." **K closest points**: identical shape to top-K largest, but heap-ordered by (squared) Euclidean distance instead of raw value — maintain a max-heap of size K by distance, evicting the farthest point on overflow. **Task scheduler with cooldown**: a max-heap of remaining task counts — repeatedly run the most-frequent remaining task type, respecting a per-type cooldown before it can run again. **Meeting rooms II** (minimum rooms needed to host all meetings): sort meetings by start time, then maintain a min-heap of the end times of currently-occupied rooms — for each new meeting, if the earliest-ending room (the heap's root) has already finished by the new meeting's start, reuse that room (pop the old end time, push the new one); otherwise you need an additional room (just push). The heap's final (or peak) size answers the question.

```java
static int minMeetingRooms(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    PriorityQueue<Integer> endTimes = new PriorityQueue<>();
    for (int[] iv : intervals) {
        if (!endTimes.isEmpty() && endTimes.peek() <= iv[0]) endTimes.poll();
        endTimes.offer(iv[1]);
    }
    return endTimes.size();
}
```

*Follow-up: How is "meeting rooms II" different from the Phase 12 interval-merging or interval-scheduling problems?* Interval scheduling (Phase 12) picks a *subset* of non-overlapping intervals to maximize count; interval merging collapses overlaps into the minimum number of *disjoint ranges*. Meeting rooms II answers a third, different question — the maximum number of intervals *simultaneously* overlapping at any point in time — which needs the heap (or an equivalent sweep-line with separated start/end events), not the sort-by-end-time-and-scan loop that solves plain interval scheduling.

*Follow-up: What's the general signal that tells you "this needs a heap," across all of these variants?* The phrase "repeatedly need the current best/earliest/nearest, and new candidates may still be arriving" — whenever taking the extreme and then continuing to process more data (rather than a one-shot, static, offline computation) is the shape of the problem, a heap is almost always the right structure.
