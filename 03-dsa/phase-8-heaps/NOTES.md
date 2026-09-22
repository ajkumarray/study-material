<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · trees](../phase-7-trees/NOTES.md) | [Phase 9 · graphs ➡](../phase-9-graphs/NOTES.md)
<!-- /nav -->

# Phase 8 — Heaps / Priority Queues: Notes

All code below is from `HeapPatterns.java` in this directory (run with `java -ea HeapPatterns.java`) unless a section is marked "not implemented in this file" — those round out the picture with classic follow-up problems.

## 1. The Binary Heap — Structure and Invariant

A **binary heap** is a *complete* binary tree (every level fully filled except possibly the last, which fills left to right with no gaps) stored **implicitly in an array** — there are no node objects and no pointer fields at all. The **completeness** property is exactly what makes array storage work: you can compute any node's parent and children from its index alone, with no pointers needed.

- **Heap property**: in a **min-heap**, every parent is `<=` both of its children, which guarantees the overall minimum is always sitting at index 0 (the root). A **max-heap** flips the inequality: every parent `>=` its children, so the maximum sits at the root.
- **Not fully sorted**: a heap only guarantees the *root* is the extreme value — siblings and deeper nodes have no defined order relative to each other beyond satisfying the parent/child invariant. This is what makes heap operations cheaper than fully sorting (you're maintaining a much weaker invariant).
- **Index math** (0-indexed array):
  ```
  parent(i) = (i - 1) / 2     (integer division)
  left(i)   = 2i + 1
  right(i)  = 2i + 2
  ```

```java
static class MinHeap {
    private int[] a = new int[16];
    private int n = 0;

    int size() { return n; }
    int peek() { return a[0]; }              // root = minimum, O(1)

    void push(int x) {
        if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2);
        a[n] = x;
        siftUp(n++);
    }

    int pop() {
        int min = a[0];
        a[0] = a[--n];
        siftDown(0);
        return min;
    }
}
```

**Why it's useful**: this array-backed, pointer-free representation is exactly why heaps are cache-friendly and space-efficient compared to a pointer-based tree — it's also why `java.util.PriorityQueue` (section 3) can be implemented entirely on top of a resizable array under the hood.

**Summary — Key Takeaways:**
- A heap is a complete binary tree stored as a flat array; index math (`(i-1)/2`, `2i+1`, `2i+2`) replaces pointers entirely.
- Min-heap: parent `<=` children, min at the root. Max-heap: parent `>=` children, max at the root.
- Only the root is guaranteed extreme — a heap is a much weaker (and cheaper to maintain) invariant than a full sort.

---

## 2. Heap Operations: Sift Up, Sift Down, Build-Heap

The three operations that maintain the heap property after a mutation:

### Push (insert) — sift up

- **Steps**: append the new element at the end of the array (the next available leaf position — this preserves completeness), then repeatedly compare it against its parent, swapping upward while it violates the heap property (`a[i] < a[parent]` for a min-heap), stopping the moment the parent is already smaller or the element reaches the root.
- **Time Complexity**: O(log n) — the element can travel at most the height of the tree, which is O(log n) for a complete tree of n nodes.

```java
private void siftUp(int i) {
    while (i > 0) {
        int parent = (i - 1) / 2;
        if (a[i] >= a[parent]) break;     // parent already smaller -> done
        swap(i, parent);
        i = parent;
    }
}
// push(5), push(3), push(8), push(1), push(9), push(2)
// after all six pushes, peek() == 1  (the smallest value bubbled all the way to the root)
```

### Pop (extract min/max) — sift down

- **Steps**: save the root's value (the answer to return), move the *last* element in the array into the root position (this keeps the array/tree complete — removing the last leaf and placing it at the root is much cheaper than trying to remove the root directly and re-link everything), then repeatedly compare that element against its two children, swapping with whichever child is smaller (for a min-heap) while the heap property is violated, stopping when both children are `>=` the current element or there are no children left.
- **Time Complexity**: O(log n) — same reasoning as sift up, the element travels at most the tree's height.

```java
private void siftDown(int i) {
    while (true) {
        int l = 2 * i + 1, r = 2 * i + 2, smallest = i;
        if (l < n && a[l] < a[smallest]) smallest = l;
        if (r < n && a[r] < a[smallest]) smallest = r;
        if (smallest == i) break;         // heap property holds -> done
        swap(i, smallest);
        i = smallest;
    }
}
// pop() -> 1, pop() -> 2, pop() -> 3   (repeated pops come out in SORTED order)
```

In this example: after pushing `5, 3, 8, 1, 9, 2`, three consecutive `pop()` calls return `1`, `2`, `3` — the three smallest values, in sorted order. This is not a coincidence: repeatedly popping a min-heap always yields values in ascending order, since each pop is guaranteed to return the current minimum. This exact process, applied to build-then-pop-everything, **is** heapsort (Phase 10).

### Build-heap from n elements — O(n), not O(n log n)

- **The naive way**: insert n elements one at a time into an empty heap, each push costing O(log n) — total O(n log n).
- **The better way — bottom-up heapify**: place all n elements into the array in arbitrary order, then call `siftDown` on every **non-leaf** node, working from the *last* non-leaf up to the root (leaves need no work — a single node trivially satisfies the heap property with itself).
- **Why this is O(n), not O(n log n)**: the cost telescopes. Roughly half the nodes are leaves (0 work), a quarter are one level up (sift-down distance at most 1), an eighth are two levels up (distance at most 2), and so on. Summing `n/2 * 0 + n/4 * 1 + n/8 * 2 + ...` converges to O(n) rather than O(n log n) — the key insight is that *most* nodes are near the bottom, where sift-down has very little distance to travel, so the "log n" cost only applies to the few nodes near the root.

| Build method | Time Complexity | Why |
|---|---|---|
| n individual pushes | O(n log n) | Every push costs O(log n), and there are n of them |
| Bottom-up heapify | **O(n)** | Cost telescopes — most nodes are near the bottom with small sift distances |

**Why it's useful**: knowing build-heap is O(n) — not O(n log n) — is a common interview trap question, and it's also what makes heapsort's overall O(n log n) come from the n subsequent pops, not the initial build.

**Summary — Key Takeaways:**
- Push = append + sift up, O(log n). Pop = swap-in-last-element + sift down, O(log n).
- Build-heap from n elements is O(n) via bottom-up heapify — NOT O(n log n) like n individual pushes.
- Repeated pops from a min-heap yield ascending sorted order — this is exactly heapsort's mechanism.

---

## 3. Heaps in Java: `java.util.PriorityQueue`

`java.util.PriorityQueue` **is** a binary min-heap — you never need to hand-roll one (the `MinHeap` class above exists purely to teach the mechanics). Its core operations map directly onto the concepts above:

- **`offer(x)`** (or `add(x)`): insert — internally a sift-up. O(log n).
- **`poll()`**: remove and return the smallest element — internally a sift-down. O(log n). Returns `null` on an empty queue (as opposed to `remove()`, which throws).
- **`peek()`**: look at the smallest element without removing it. O(1).
- **Default ordering**: natural ordering (smallest first) for `Comparable` types — a **min-heap** by default.
- **Max-heap**: pass `Collections.reverseOrder()`, or any custom `Comparator`, to the constructor.

```java
PriorityQueue<Integer> max = new PriorityQueue<>(Collections.reverseOrder());
max.addAll(List.of(4, 1, 7, 3));
System.out.println(max.poll());   // 7  -- reverseOrder() flips natural ordering, so "smallest" becomes "largest"
```

In this example: `PriorityQueue`'s min-heap machinery is unchanged — what changes is the *comparator* it uses to decide "smaller." `Collections.reverseOrder()` inverts natural integer ordering, so from the heap's perspective `7` now compares as "smallest" (because it would normally be the largest), and it ends up at the root, which is exactly what a max-heap needs.

Custom comparators are how you heap-order arbitrary objects — order tasks by priority field, points by distance from origin, or (as in `topKFrequent` below) integers by an externally-computed frequency rather than their own natural value:

```java
PriorityQueue<Integer> heap = new PriorityQueue<>((x, y) -> freq.get(x) - freq.get(y));
```

**Why it's useful**: nearly every "repeatedly take the current best" interview problem reduces to "put things in a `PriorityQueue` with the right comparator" — the hard part is almost always identifying the comparator and the heap size cap, not writing heap mechanics from scratch (which the JDK already provides).

**Summary — Key Takeaways:**
- `PriorityQueue` is a min-heap by default; `offer`/`poll`/`peek` are O(log n)/O(log n)/O(1).
- Max-heap: pass `Collections.reverseOrder()` or a custom `Comparator`.
- Custom comparators let you heap-order by any derived property (frequency, distance, deadline) — this is the main design decision in most heap problems.

---

## 4. Top-K / Kth Largest — the Size-K Heap Pattern

**The pattern**: to find the K largest elements (or the Kth largest specifically) while scanning a stream or array, maintain a **min-heap capped at size K**. Every new element is offered to the heap; if that pushes the size over K, immediately poll (evict) the *smallest* element in the heap. After scanning everything, the heap contains exactly the K largest elements seen, with the smallest of them sitting at the root.

- **Why a min-heap for the K *largest*** (the common trap): it seems backward at first, but the min-heap's root is always the *weakest* member of your current top-K — exactly the element you want cheap access to for eviction. A max-heap of all n elements also technically works (poll K times) but costs O(n log n) to build and doesn't stream (you'd need to hold all n elements before you could start).
- **Time Complexity**: O(n log k) — n offers/evictions, each O(log k) since the heap never exceeds size k. This beats fully sorting (O(n log n)) whenever `k` is much smaller than `n`.
- **Space Complexity**: O(k).

```java
static List<Integer> kLargest(int[] a, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();   // keep the k LARGEST
    for (int x : a) {
        minHeap.offer(x);
        if (minHeap.size() > k) minHeap.poll();   // evict the smallest -> top k remain
    }
    List<Integer> res = new java.util.ArrayList<>(minHeap);
    Collections.sort(res);
    return res;
}
// kLargest({3,1,5,12,2,11}, 3) -> [5, 11, 12]   (the 3 largest values, returned sorted)

static int kthLargest(int[] a, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();
    for (int x : a) {
        minHeap.offer(x);
        if (minHeap.size() > k) minHeap.poll();
    }
    return minHeap.peek();                        // the kth largest sits at the root
}
// kthLargest({3,2,1,5,6,4}, 2) -> 5   (the 2nd largest value in the array)
```

In this example: for `kthLargest`, once the heap has processed all 6 elements while never growing past size 2, it's left holding only the two largest values (`5` and `6`); the *smaller* of those two, sitting at the min-heap's root, is exactly the 2nd-largest overall — which is `5`.

**Alternative — Quickselect** (Phase 10): finds the Kth largest in O(n) *average* time and O(1) extra space by partitioning around a pivot (like quicksort, but only recursing into the side that contains the target rank) — asymptotically better than the heap's O(n log k) for a one-shot query, but doesn't stream and has O(n²) worst case without randomization safeguards.

| Approach | Time | Space | Streams? |
|---|---|---|---|
| Size-K min-heap | O(n log k) | O(k) | Yes |
| Full sort | O(n log n) | O(n) (or O(1) in-place) | No |
| Quickselect | O(n) average, O(n²) worst | O(1) | No |

**Why it's useful**: "top K" and "Kth largest" questions are some of the most frequently asked heap problems precisely because the min-heap-for-largest trick is non-obvious the first time you see it — practicing it until it's automatic pays off across a huge family of variants (K closest points, K most frequent, Kth largest in a stream).

**Summary — Key Takeaways:**
- Top-K largest: min-heap capped at size K, evict the smallest on overflow. O(n log k), O(k) space, and it streams.
- The min-heap-for-largest choice is the classic trap — the root holds your current *weakest* top-K member, which is exactly what you want to evict cheaply.
- Quickselect beats the heap asymptotically (O(n) average) for a one-shot, offline Kth-largest query, at the cost of not streaming.

---

## 5. Top-K Frequent Elements

A two-step pattern: first count occurrences with a hash map (Phase 5), then use a size-K heap — but ordered by the *frequency* (a derived value from the map), not by the element's own natural value.

- **Time Complexity**: O(n) to build the frequency map, plus O(m log k) for the heap step, where `m` is the number of distinct elements — O(n log k) overall in the typical case.
- **Alternative — bucket sort by frequency**: since frequency can range only from `1` to `n`, you can create `n+1` buckets (an array of lists indexed by frequency), place each distinct element into the bucket matching its count, then read off the top K by scanning buckets from highest frequency down. This achieves O(n) — strictly better than the heap's O(n log k) — because it avoids comparison-based ordering entirely, exploiting the bounded range of possible frequencies.

```java
static List<Integer> topKFrequent(int[] a, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    for (int x : a) freq.merge(x, 1, Integer::sum);
    PriorityQueue<Integer> heap = new PriorityQueue<>((x, y) -> freq.get(x) - freq.get(y));
    for (int key : freq.keySet()) {
        heap.offer(key);
        if (heap.size() > k) heap.poll();
    }
    return new java.util.ArrayList<>(heap);
}
// topKFrequent({1,1,1,2,2,3}, 2) -> contains 1 (freq 3) and 2 (freq 2); 3 (freq 1) is excluded
```

In this example: `freq` ends up as `{1: 3, 2: 2, 3: 1}`. The heap's comparator orders by `freq.get(x)`, so `3` (frequency 1) is the first to be evicted once the heap exceeds size 2, leaving `1` and `2` — the two most frequent values.

| Approach | Time | When to use |
|---|---|---|
| Size-K heap ordered by frequency | O(n log k) | General-purpose, simple to write |
| Bucket sort by frequency | **O(n)** | Frequency range is bounded (always true here: 1..n) — strictly faster |

**Why it's useful**: this pattern — "derive a secondary key via a hash map, then heap-order by that derived key" — generalizes to any "top K by some computed property" problem (top K by distance, by score, by recency), not just raw frequency.

**Summary — Key Takeaways:**
- Count with a hash map, then heap-order by the counted value (not the element itself) — O(n log k).
- Bucket sort by frequency achieves O(n), beating the heap, by exploiting the bounded range of possible frequencies (1..n).
- The "hash map for a derived key, then heap on that key" pattern generalizes far beyond just frequency.

---

## 6. Merge K Sorted Lists / Arrays

**The pattern**: maintain a min-heap containing the *current head* of each of the K lists (or arrays). Repeatedly poll the overall smallest, append it to the result, and push that same list's *next* element (if any) back onto the heap. Repeat until the heap is empty.

- **Time Complexity**: O(N log k), where N is the *total* number of elements across all k lists — each of the N elements is pushed and popped exactly once, each operation costing O(log k) since the heap never holds more than k elements at once.
- **Why this beats naive pairwise merging**: merging lists two at a time sequentially (merge list 1 with list 2, that result with list 3, and so on) costs O(N·k) in the worst case, since each of the k-1 merge steps can touch up to N elements. The heap approach amortizes the "find the current minimum among k candidates" cost to O(log k) per element instead of O(k) per element.

```java
// Sketch (using linked-list Nodes from Phase 3, or List<Integer>/index pairs for arrays):
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

| Approach | Time | Why |
|---|---|---|
| Naive pairwise merge | O(N·k) | Each of k-1 merge steps can touch up to N elements |
| Min-heap of k current heads | **O(N log k)** | "Find current min among k" costs O(log k), not O(k), per element |

**Why it's useful**: this is the direct generalization of the two-list merge from Phase 3 to k lists, and it's also structurally identical to Dijkstra's shortest-path algorithm (Phase 9), which is itself "repeatedly extract the current best candidate from a heap, then push its newly-discovered neighbors" — recognizing this shared shape across phases is a strong interview signal.

**Summary — Key Takeaways:**
- Min-heap of the k current "frontier" elements (one per list); poll smallest, push its successor. O(N log k) total.
- Beats naive pairwise merging's O(N·k), because the heap turns "find the min among k candidates" into O(log k) instead of O(k).
- Same shape as Dijkstra's algorithm (Phase 9) — "repeatedly take the best candidate, then expand its neighbors."

---

## 7. Two-Heap Running Median

**The problem**: given a stream of numbers arriving one at a time, report the median after each insertion, without re-sorting from scratch every time.

- **The trick**: maintain two heaps that together always hold every number seen so far, split into a "lower half" and an "upper half." The **lower half** is stored in a **max-heap** (so its largest element — the boundary closest to the median — is instantly accessible at the root). The **upper half** is stored in a **min-heap** (so its smallest element — the other boundary — is instantly accessible). The two heaps are kept balanced in size (differing by at most 1 element).
- **Median**: if the heaps are unequal in size, the median is simply the top of whichever heap has one more element. If they're equal in size, the median is the average of both tops.
- **Insertion (`add`)**: always offer the new value to `low` (the max-heap) first, then immediately move `low`'s current maximum over to `high` (this guarantees every value in `low` is `<=` every value in `high`, maintaining the split invariant even though the new value might actually belong in the upper half); finally, if this left `high` larger than `low`, move `high`'s minimum back to `low` to restore the size balance.
- **Time Complexity**: `add` is O(log n) (heap push/pop); `median` is **O(1)** (just peeking two roots).

```java
static class MedianFinder {
    private final PriorityQueue<Integer> low = new PriorityQueue<>(Collections.reverseOrder()); // max-heap
    private final PriorityQueue<Integer> high = new PriorityQueue<>();                            // min-heap

    void add(int x) {
        low.offer(x);
        high.offer(low.poll());                   // balance: move low's max to high
        if (high.size() > low.size()) low.offer(high.poll());   // keep low >= high in size
    }

    double median() {
        if (low.size() > high.size()) return low.peek();
        return (low.peek() + high.peek()) / 2.0;
    }
}
// add(1), add(2) -> median() == 1.5   (low={1}, high={2}, equal size -> average of tops)
// add(3)         -> median() == 2.0   (low={2,1}, high={3}, low bigger -> low's top)
```

In this example: after `add(1)` then `add(2)`, `low` holds `{1}` and `high` holds `{2}` (equal sizes), so the median is the average of their tops: `(1 + 2) / 2.0 = 1.5`. After `add(3)`: `3` is offered to `low` first, `low`'s max (which is now `3`) moves to `high`, then since `high` (now `{2, 3}`, size 2) is larger than `low` (now `{1}`, size 1), `high`'s min (`2`) moves back to `low` — leaving `low = {2, 1}` (size 2) and `high = {3}` (size 1). Since `low` is now larger, the median is simply `low.peek() == 2`, matching the expected median of `1, 2, 3`.

**Why it's useful**: this is the textbook example of "two heaps, split at the median" for any streaming statistics problem — it's a clean, elegant O(log n)-per-insertion answer to a problem that would otherwise require an O(n) re-sort (or an O(n) insertion into a sorted structure) on every new value.

**Summary — Key Takeaways:**
- Max-heap for the lower half (top = largest of the low values), min-heap for the upper half (top = smallest of the high values), kept balanced in size.
- `add` is O(log n); `median` is O(1) — a massive improvement over re-sorting on every insertion.
- The `add` sequence (offer to low, move low's max to high, rebalance if needed) is worth memorizing as a fixed recipe — it's easy to get the direction backward under pressure.

---

## 8. Scheduling / Greedy-by-Priority — Not Fully Implemented in This File

Any problem shaped "repeatedly take the current best/earliest/nearest candidate" is a heap problem, even when it doesn't look like a classic "top-K" question on the surface:

- **Task scheduler with cooldown**: repeatedly pick the most-frequent remaining task type to run next (a max-heap ordered by remaining count), respecting a cooldown before the same task type can run again.
- **Meeting rooms II** (minimum rooms needed): sort meetings by start time; maintain a min-heap of the *end times* of currently-occupied rooms; for each new meeting, if the earliest-ending room (the heap's root) ends at or before the new meeting's start, reuse that room (pop and push the new end time); otherwise a new room is needed (just push). The heap's size at the end (or its peak size) is the answer.
- **K closest points to the origin**: identical shape to `kLargest`, but ordered by (squared) Euclidean distance instead of raw value — maintain a max-heap of size K ordered by distance, evicting the farthest point whenever the heap exceeds size K.
- **Dijkstra's shortest path** (Phase 9): a min-heap of `(distance, node)` pairs — repeatedly pop the currently-closest unvisited node, and push its neighbors with updated tentative distances. Structurally identical to the merge-K-lists pattern from section 6.

```java
// Meeting rooms II sketch
static int minMeetingRooms(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);              // sort by start time
    PriorityQueue<Integer> endTimes = new PriorityQueue<>();     // min-heap of active room end times
    for (int[] iv : intervals) {
        if (!endTimes.isEmpty() && endTimes.peek() <= iv[0]) endTimes.poll();  // reuse a freed room
        endTimes.offer(iv[1]);
    }
    return endTimes.size();
}
```

**Why it's useful**: recognizing the "repeatedly take the current best" signal is often more valuable than knowing any specific problem's solution — it immediately tells you to reach for a `PriorityQueue` and then just figure out the right comparator, rather than trying to invent a bespoke algorithm.

**Summary — Key Takeaways:**
- "Repeatedly take the best/earliest/nearest, then possibly push a new candidate" is the heap signal — recognize it independent of the problem's surface phrasing.
- Meeting rooms II: min-heap of active end times; reuse a room when the earliest-ending one frees up before the next meeting starts.
- Dijkstra (Phase 9) is structurally the same pattern as merge-K-lists: pop the current best, push its expansions.

---

## 9. When to Use a Heap vs. Alternatives

| Need | Best structure | Why |
|---|---|---|
| Only the extreme value, repeatedly, with insertions mixed in | **Heap** | O(log n) push/pop, O(1) peek — don't pay for a full sort you don't need |
| Everything fully sorted | Just sort | O(n log n) once; a heap doesn't help if you need the *whole* order anyway |
| Ordered data **and** search/range queries (floor, ceiling, range iteration) | `TreeMap` / `TreeSet` (balanced BST, Phase 7) | A heap has no efficient search or range operations — only the root is accessible in O(1) |
| The Kth element once, offline (no further insertions) | Quickselect | O(n) average, better than a heap's O(n log k) for a single one-shot query |
| Streaming Kth-largest / top-K / running median | **Heap** | Handles ongoing insertions in O(log n) each; quickselect and full sorts don't stream |

**Why it's useful**: this decision table is the actual interview skill — implementing heap mechanics correctly matters less than recognizing *when* a heap (vs. a full sort, vs. a `TreeMap`, vs. quickselect) is the right tool for the specific access pattern the problem describes.

**Summary — Key Takeaways:**
- Heap: best for "repeatedly need the extreme, with insertions still happening."
- Full sort: best when you need everything ordered and there's no streaming requirement.
- `TreeMap`/`TreeSet`: best when you need ordering *and* search/range queries — a heap can't do range queries at all.
- Quickselect: best for a single, offline "find the Kth element" query — O(n) average, but doesn't stream.
