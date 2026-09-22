<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · graphs](../phase-9-graphs/NOTES.md) | [Phase 11 · dynamic programming ➡](../phase-11-dynamic-programming/NOTES.md)
<!-- /nav -->

# Phase 10 — Sorting & Searching: Notes

All code below is from `SortingSearching.java` in this directory (run with `java -ea SortingSearching.java`) unless a section is marked "not implemented in this file" — those round out the picture with classic follow-up problems.

## 1. Why Learn Sorting Algorithms When `Arrays.sort` Exists?

In production Java code, you call `Arrays.sort()` / `Collections.sort()` — never hand-roll a sort. Under the hood, `Arrays.sort` uses a tuned **dual-pivot quicksort** for primitive arrays (no stability needed, since primitives have no identity beyond their value) and **Timsort** — a hybrid of merge sort and insertion sort — for object arrays and `Collections.sort` (stability matters here, since two distinct objects can compare equal by a key while still being different objects worth keeping in order).

Interviews ask you to know the classic algorithms anyway, for three reasons: (1) their time/space/stability trade-offs come up constantly as comparison questions, (2) several of them (partitioning, merging, heapify) are *reused* as building blocks inside other algorithms (quickselect, heapsort's use in top-K, merge's role in merge-k-sorted-lists), and (3) understanding *why* `Arrays.sort` picks quicksort-for-primitives vs. Timsort-for-objects requires understanding the stability and worst-case trade-offs of each.

**Why it's useful**: knowing this distinction (primitives → dual-pivot quicksort, objects → Timsort) is itself a common interview question, and it reinforces *why* stability matters — objects often get compared by only one field while carrying others that should preserve their relative order.

**Summary — Key Takeaways:**
- Never hand-roll sorting in real code — `Arrays.sort`/`Collections.sort` are already tuned, correct, and fast.
- `Arrays.sort` on primitives = dual-pivot quicksort (no stability needed). On objects / `Collections.sort` = Timsort (stable).
- The classic algorithms below matter because their *techniques* (partition, merge, heapify) are reused elsewhere, not because you'll write them at work.

---

## 2. The Classic Sorting Algorithms

### Insertion sort

- **Idea**: build up a sorted prefix one element at a time — take the next unsorted element (the "key"), and shift every larger element in the sorted prefix one position to the right until the key's correct spot is found, then drop it in.
- **Time Complexity**: O(n²) average/worst case, but **O(n)** on nearly-sorted input — each element only needs to shift past a few out-of-place neighbors, not the whole prefix.
- **Space Complexity**: O(1) — sorts in place.
- **Stable**: yes — equal elements are never swapped past each other (the `a[j] > key` comparison, using strict `>`, only shifts elements strictly greater than the key, leaving equal elements in their original relative order).

```java
static int[] insertionSort(int[] a) {
    for (int i = 1; i < a.length; i++) {
        int key = a[i], j = i - 1;
        while (j >= 0 && a[j] > key) a[j + 1] = a[j--];
        a[j + 1] = key;
    }
    return a;
}
// insertionSort({5,2,9,1,5,6}) -> {1,2,5,5,6,9}
```

In this example: at `i=1` (`key=2`), the loop shifts `5` (at index 0) one spot right since `5 > 2`, then places `2` at index 0 — the sorted prefix is now `[2,5]`. This continues, with each new element sliding leftward only as far as it needs to.

**Why it's useful**: insertion sort is exactly what Timsort and quicksort switch to for tiny subarrays (typically under ~16-47 elements, tuned per implementation) — its O(n²) worst case is irrelevant at small sizes, and its low constant-factor overhead and nearly-sorted-input speed make it genuinely faster than merge sort or quicksort's recursive overhead in that size range.

### Merge sort

- **Idea**: divide the array in half recursively until each piece has 0 or 1 elements (trivially sorted), then **merge** sorted pairs back together, always taking the smaller of the two current fronts.
- **Time Complexity**: **O(n log n)**, guaranteed — average *and* worst case, since the divide step always splits evenly regardless of input order.
- **Space Complexity**: O(n) — the merge step needs auxiliary arrays to hold the two halves being combined.
- **Stable**: yes — the merge step uses `<=` (not `<`) when comparing the two fronts, which means an element from the left half is taken before an equal element from the right half, preserving original relative order.

```java
static int[] mergeSort(int[] a) {
    if (a.length <= 1) return a;
    int mid = a.length / 2;
    int[] left = mergeSort(Arrays.copyOfRange(a, 0, mid));
    int[] right = mergeSort(Arrays.copyOfRange(a, mid, a.length));
    return merge(left, right);
}
static int[] merge(int[] l, int[] r) {
    int[] out = new int[l.length + r.length];
    int i = 0, j = 0, k = 0;
    while (i < l.length && j < r.length) out[k++] = (l[i] <= r[j]) ? l[i++] : r[j++];  // <= keeps stability
    while (i < l.length) out[k++] = l[i++];
    while (j < r.length) out[k++] = r[j++];
    return out;
}
// mergeSort({5,2,9,1,5,6}) -> {1,2,5,5,6,9}
```

In this example: the array splits into `[5,2,9]` and `[1,5,6]`, each recursively sorted to `[2,5,9]` and `[1,5,6]`, then merged: compare `2` vs `1` (take `1`), `2` vs `5` (take `2`), `5` vs `5` — **equal**, and because the comparison is `<=`, the *left* half's `5` is taken first, preserving the original left-before-right order of the two equal `5`s from the input array.

- **Recurrence**: `T(n) = 2T(n/2) + O(n)` — two half-sized recursive calls plus O(n) merge work — which the Master Theorem resolves to `O(n log n)`. This is worth being able to state explicitly if asked to justify the complexity.
- **Real-world use**: merge sort's guaranteed-`O(n log n)`-regardless-of-input property, plus its natural fit for external memory (merging pre-sorted chunks doesn't require random access), makes it the standard choice for **external sorting** (sorting data too large to fit in RAM) and for sorting linked lists (no random access needed, unlike quicksort's partitioning).

### Quick sort

- **Idea**: pick a **pivot**, **partition** the array so everything smaller than the pivot ends up to its left and everything larger to its right (this places the pivot at its final sorted position in one pass), then recursively sort the two sides.
- **Time Complexity**: O(n log n) average, but **O(n²) worst case** — happens when the pivot choice consistently produces a badly unbalanced split (e.g., always picking the last element as pivot on an already-sorted or reverse-sorted array, as this implementation does).
- **Space Complexity**: O(log n) average for the recursion stack (O(n) worst case on the same bad-pivot inputs that trigger O(n²) time).
- **Stable**: no — partitioning swaps elements based on position relative to the pivot, with no guarantee about preserving relative order of equal elements.
- **Mitigating the worst case**: choosing a **random** pivot, or the **median-of-three** (median of the first, middle, and last elements), makes the adversarial O(n²) input pattern astronomically unlikely rather than guaranteed by a fixed, predictable pivot choice.

```java
static void quick(int[] a, int lo, int hi) {
    if (lo >= hi) return;
    int p = partition(a, lo, hi);
    quick(a, lo, p - 1);
    quick(a, p + 1, hi);
}
static int partition(int[] a, int lo, int hi) {
    int pivot = a[hi], i = lo;              // Lomuto partition; pivot = last element
    for (int j = lo; j < hi; j++) if (a[j] < pivot) swap(a, i++, j);
    swap(a, i, hi);
    return i;
}
// quickSort({5,2,9,1,5,6}) -> {1,2,5,5,6,9}
```

In this example (Lomuto partition scheme): the pivot is the last element (`6`). The loop scans left to right, and every time it finds an element smaller than the pivot, it swaps that element into the next "smaller than pivot" slot (tracked by `i`), effectively compacting all smaller elements to the front as it goes. After the scan, swapping `a[i]` with the pivot places the pivot at its correct final index `i`, with everything smaller to its left and everything larger to its right.

**Why it's useful**: quicksort's in-place partitioning (O(log n) space vs. merge sort's O(n)) and excellent real-world constant factors are why it (in a dual-pivot form) is what `Arrays.sort` actually uses for primitive arrays — the O(n²) worst case is a real risk only for adversarially-constructed or naively-pivoted inputs, which randomized/tuned pivot selection defends against.

### Heap sort

- **Idea**: build a max-heap (Phase 8) from all n elements in O(n) (bottom-up heapify), then repeatedly swap the root (the current maximum) with the last unsorted element and sift down to restore the heap property over the remaining unsorted prefix — each extraction places one more element in its final sorted position at the end of the array.
- **Time Complexity**: **O(n log n)**, guaranteed — build-heap is O(n), and each of the n extractions costs O(log n).
- **Space Complexity**: **O(1)** — the heap is built in place within the same array, no auxiliary array needed (unlike merge sort).
- **Stable**: no — swapping the root with the last element can easily reorder equal elements relative to each other.
- **Trade-off**: despite matching merge sort's O(n log n) worst case *and* beating it on space (O(1) vs O(n)), heap sort tends to be slower in practice than a well-tuned quicksort due to **poor cache locality** — heap operations jump around the array via `2i+1`/`2i+2` index arithmetic rather than accessing memory sequentially, causing more cache misses than quicksort's more sequential partitioning passes.

### Non-comparison sorts: counting / radix / bucket

- **Counting sort**: count occurrences of each distinct key value in an auxiliary array indexed by value, then reconstruct the sorted output from those counts. O(n + k) time and space, where k is the range of possible key values — only practical when k isn't much larger than n (e.g., sorting exam scores 0-100, or single-digit values).
- **Radix sort**: apply counting sort repeatedly, once per digit (or byte), from least-significant to most-significant, using counting sort's stability to ensure each pass doesn't undo the ordering established by previous passes. O(d · (n + k)) where d is the number of digits and k is the base (e.g., 10 for decimal, 256 for byte-wise).
- **Bucket sort**: distribute elements into a fixed number of buckets based on value ranges, sort each bucket individually (often with insertion sort, since buckets are small), then concatenate. O(n + k) average when input is roughly uniformly distributed across the range; degrades toward O(n²) if all elements land in one bucket.
- **Key limitation, common to all three**: these are **not comparison sorts** — they don't compare elements pairwise at all, which is exactly how they beat the O(n log n) comparison lower bound (see section 3). This only works when keys have exploitable structure: a bounded, known range of integer values (counting/bucket) or a fixed number of digits/bytes (radix). They don't generalize to arbitrary comparable objects the way merge/quick/heap sort do.

| Sort | Time (avg / worst) | Space | Stable? | Notes |
|---|---|---|---|---|
| Bubble / Selection | O(n²) / O(n²) | O(1) | bubble: yes, selection: no | Teaching only — rarely used in practice |
| **Insertion** | O(n²) / O(n²), **O(n)** nearly-sorted | O(1) | yes | Great on small/nearly-sorted input; used inside Timsort/quicksort for tiny subarrays |
| **Merge sort** | O(n log n) / **O(n log n)** | O(n) | **yes** | Guaranteed n log n; the stable choice; standard for external sort and linked lists |
| **Quick sort** | O(n log n) / **O(n²)** | O(log n) avg | no | Fastest in practice with good pivots; `Arrays.sort` uses a dual-pivot variant for primitives |
| **Heap sort** | O(n log n) / O(n log n) | **O(1)** | no | In-place guaranteed n log n; poor cache locality vs. quicksort |
| Counting / Radix / Bucket | O(n) / O(n) (bounded keys) | O(n+k) | yes | **Not** comparison sorts — need bounded/structured integer keys |

**Why it's useful**: this table is the single most reusable artifact from this phase — nearly every "which sort would you use for X" interview question maps directly onto a row (stability requirement → merge/insertion/counting; guaranteed worst case → merge/heap; in-place + fastest average → quicksort; bounded integer keys → counting/radix).

**Summary — Key Takeaways:**
- Merge sort: stable, guaranteed O(n log n), O(n) space — the safe default when stability or worst-case guarantees matter.
- Quick sort: unstable, O(n log n) average but O(n²) worst (mitigate with randomized/median-of-three pivots), O(log n) space, fastest in practice — what `Arrays.sort` uses for primitives.
- Heap sort: unstable, guaranteed O(n log n), O(1) space, but worse cache locality than quicksort in practice.
- Counting/radix/bucket sort achieve O(n) by NOT comparing — only when keys are bounded integers or fixed-digit values.
- Insertion sort's O(n) nearly-sorted case and low overhead are why it's used as the base case inside hybrid sorts (Timsort, tuned quicksort) for small subarrays.

---

## 3. Stability and the Comparison-Sort Lower Bound

- **Stability**: a sort is **stable** if two elements that compare equal retain their original relative order in the output. This matters whenever you're sorting by one key but there's meaningful information in another field — e.g., sort a list of people by last name, then (stably) by first name, and within each identical-first-name group the last-name order from the previous sort is preserved, giving a correctly multi-key-sorted result without a single comparator that checks both fields at once.
- **The Ω(n log n) comparison-sort lower bound**: any sorting algorithm that only learns information about the input via pairwise comparisons (`<`, `>`, `==`) cannot do better than O(n log n) in the worst case. The argument is information-theoretic: there are n! possible orderings of n elements, and each comparison yields at most 1 bit of information (branches one of two ways), so distinguishing between n! possible outcomes requires at least `log2(n!) = O(n log n)` comparisons in the worst case — this is a **decision-tree argument**, not tied to any specific algorithm.
- **How counting/radix/bucket sort "beat" O(n log n)**: they don't actually violate the lower bound — they simply aren't comparison sorts at all. By indexing directly into buckets by value (or digit), they extract more than 1 bit of information per "step," which the comparison-based lower bound's assumptions don't cover. This only works because they exploit *structural* knowledge about the keys (a bounded range, a fixed number of digits) that a generic comparison sort doesn't get to assume.

```java
// Sort by (age, then stably by original insertion order) using a stable sort:
people.sort(Comparator.comparingInt(Person::getAge));
// Since Collections.sort/List.sort use Timsort (stable), people with equal age
// keep whatever relative order they had BEFORE this sort call.
```

**Why it's useful**: knowing exactly *why* radix sort isn't a counterexample to the O(n log n) lower bound (rather than just knowing "radix sort is O(n)") signals a deeper understanding that interviewers specifically probe for with "but doesn't that contradict what you just said?" follow-ups.

**Summary — Key Takeaways:**
- Stable sort = equal elements keep their relative order — essential for correct multi-key sorting via successive stable sorts.
- Ω(n log n) is a proven lower bound for *comparison-based* sorting — a decision-tree, information-theoretic argument, not specific to any one algorithm.
- Counting/radix/bucket sort achieve O(n) by not comparing at all — they exploit bounded/structured keys, which sidesteps (not contradicts) the comparison lower bound.

---

## 4. Binary Search — the Core Template

**Binary search** repeatedly halves the search space, discarding the half that provably cannot contain the answer, giving **O(log n)** time on sorted (or, more generally, monotonically-partitionable) data.

- **The template** (worth memorizing exactly, since subtle variations cause most bugs): `lo = 0, hi = a.length - 1`; loop `while (lo <= hi)`; compute `mid = lo + (hi - lo) / 2` (not `(lo + hi) / 2` — see below); compare `a[mid]` to the target and move `lo` or `hi` strictly past `mid` on each non-match.
- **Overflow safety**: `mid = lo + (hi - lo) / 2` avoids the classic bug where `lo + hi` overflows a 32-bit `int` when both are large (this famously shipped as a real bug in several early binary search implementations, including a JDK version) — `(hi - lo)` is always small and non-negative, so adding it to `lo` never overflows in the same way.
- **Moving past `mid`, not onto it**: `lo = mid + 1` / `hi = mid - 1` (not `lo = mid` / `hi = mid`) is what guarantees termination — using `mid` itself as a new boundary risks the search space never shrinking, causing an infinite loop.

```java
static int binarySearch(int[] a, int target) {
    int lo = 0, hi = a.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (a[mid] == target) return mid;
        else if (a[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}
// binarySearch({1,3,5,7,9,11}, 7) -> 3   (index of 7)
// binarySearch({1,3,5,7,9,11}, 8) -> -1  (not present)
```

In this example: for target `7`, `lo=0, hi=5`, first `mid=2` (`a[2]=5 < 7`, so `lo=3`); next `mid=4` (`a[4]=9 > 7`, so `hi=3`); now `lo=hi=3`, `mid=3` (`a[3]=7`, match) — found at index 3 in two comparisons past the initial one. For target `8` (which doesn't exist), the search space shrinks to empty (`lo > hi`) and the loop exits, correctly returning `-1`.

**Why it's useful**: binary search's real power in interviews is far beyond "find X in a sorted array" — sections 5 and 6 below cover the generalizations (bounds with duplicates, rotated arrays, and "binary search on the answer") that make up the majority of actual binary-search interview questions.

**Summary — Key Takeaways:**
- Memorize the exact template: `lo <= hi`, `mid = lo + (hi-lo)/2`, move `lo`/`hi` strictly past `mid`.
- `mid = lo + (hi-lo)/2` avoids integer overflow that `(lo+hi)/2` risks on large arrays.
- Always test size-0, size-1, size-2 inputs and both target-present and target-absent cases — the classic sources of off-by-one and infinite-loop bugs.

---

## 5. Binary Search Variants: Bounds and Rotated Arrays

### First / last occurrence (lower / upper bound)

- **The problem**: given a sorted array with duplicate values, find the **first** (or **last**) index where a target value occurs, rather than just *any* matching index.
- **The technique**: same binary search skeleton, but **on a match, don't stop** — record the current index as a candidate answer, then keep narrowing in the direction that might find an even earlier (or later) occurrence: `hi = mid - 1` for first occurrence (keep searching left), `lo = mid + 1` for last occurrence (keep searching right).

```java
static int firstOccurrence(int[] a, int target) {
    int lo = 0, hi = a.length - 1, res = -1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (a[mid] == target) { res = mid; hi = mid - 1; }   // keep searching LEFT
        else if (a[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return res;
}
static int lastOccurrence(int[] a, int target) {
    int lo = 0, hi = a.length - 1, res = -1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (a[mid] == target) { res = mid; lo = mid + 1; }   // keep searching RIGHT
        else if (a[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return res;
}
// dups = {1, 2, 2, 2, 3, 4}
// firstOccurrence(dups, 2) -> 1
// lastOccurrence(dups, 2)  -> 3
```

In this example: for `firstOccurrence`, once a `2` is found at some index, the search keeps narrowing leftward (`hi = mid - 1`) to check if an even earlier `2` exists, recording each match found along the way as the new best candidate — by the time the loop ends, `res` holds the leftmost occurrence.

- **Why it's useful**: `firstOccurrence` and `lastOccurrence` together give you `lastOccurrence - firstOccurrence + 1` — the **count** of a value in a sorted array, in O(log n), instead of an O(n) linear scan. This is also the mechanism behind `Arrays.binarySearch`-style insertion-point queries.

### Rotated sorted array

- **The problem**: an array that was sorted, then rotated at some unknown pivot (e.g., `[4,5,6,7,0,1,2]` — sorted `[0,1,2,4,5,6,7]` rotated left by 4). Find a target's index in O(log n), despite the array not being globally sorted.
- **The key insight**: at every step, **at least one half** of the current search range (`[lo, mid]` or `[mid, hi]`) is guaranteed to be internally sorted, even though the whole array isn't. Determine which half is sorted by comparing `a[lo]` to `a[mid]`; then check whether the target falls within that sorted half's value range — if so, recurse there; if not, the target (if present at all) must be in the other half.

```java
static int searchRotated(int[] a, int target) {
    int lo = 0, hi = a.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (a[mid] == target) return mid;
        if (a[lo] <= a[mid]) {              // left half sorted
            if (a[lo] <= target && target < a[mid]) hi = mid - 1;
            else lo = mid + 1;
        } else {                            // right half sorted
            if (a[mid] < target && target <= a[hi]) lo = mid + 1;
            else hi = mid - 1;
        }
    }
    return -1;
}
// rot = {4, 5, 6, 7, 0, 1, 2}
// searchRotated(rot, 0) -> 4   (0 is at index 4)
// searchRotated(rot, 3) -> -1  (3 doesn't exist in this array)
```

In this example: `lo=0, hi=6, mid=3` (`a[3]=7`). Since `a[lo]=4 <= a[mid]=7`, the **left** half `[4,5,6,7]` is sorted. Target `0` is not within `[4, 7)`, so the target must be in the other half: `lo = mid + 1 = 4`. Next iteration: `lo=4, hi=6, mid=5` (`a[5]=1`). Now `a[lo]=0 <= a[mid]=1`, so the left half `[0,1]` (indices 4-5) is sorted, and `0` *is* within `[0, 1)` — wait, `target < a[mid]` means `0 < 1`, true, and `a[lo] <= target` means `0 <= 0`, true — so narrow to that half: `hi = mid - 1 = 4`. Now `lo=hi=4`, `mid=4`, `a[4]=0` — match, return `4`.

**Why it's useful**: "search in a rotated sorted array" is one of the most frequently asked binary search variants precisely because it forces you to reason about *which half is sorted* at each step rather than mechanically applying the plain template — this "identify the invariant that still holds, even though the obvious one (global sortedness) doesn't" skill generalizes to many other binary-search-on-a-modified-structure problems.

**Summary — Key Takeaways:**
- First/last occurrence: don't stop on a match — keep narrowing in the direction that might reveal an earlier/later match, recording each candidate found.
- `lastOccurrence - firstOccurrence + 1` gives the count of a value in O(log n), beating an O(n) scan.
- Rotated array search: at least one half is always internally sorted — identify which, check if the target's value range falls within it, and recurse accordingly. Still O(log n).

---

## 6. Binary Search on the Answer

This is the single most powerful generalization of binary search, and often the hardest one to *recognize* as a binary search problem, since the input often isn't sorted at all — instead, **the answer itself** is a value in some range, and a feasibility check on candidate answers is **monotonic**.

- **The pattern**: if you can write a predicate `feasible(x)` — "is x a good-enough answer?" — such that once `feasible(x)` is true, it stays true for all larger (or smaller) `x` too (monotonicity), you can binary search directly over the space of possible *answers*, not over the input array, converging on the smallest (or largest) feasible value.
- **Recognizing it**: phrases like "minimize the maximum X such that Y" or "find the minimum/maximum value such that condition holds" or "what's the smallest speed/capacity/divisor that still finishes in time" are the signal.
- **Complexity**: O(log(range) × cost-of-feasibility-check) — often much better than trying every candidate value linearly.

**Worked example — minimum eating speed (Koko eating bananas)**: given piles of bananas and h hours, find the minimum constant eating speed (bananas per hour) such that all piles can be finished within h hours.

- **Why the feasibility check is monotonic**: if speed `s` finishes in time, any speed `s' > s` also finishes in time (eating faster never takes longer) — this monotonicity is exactly what licenses binary search over the speed value itself.

```java
static int minEatingSpeed(int[] piles, int hours) {
    int lo = 1, hi = Arrays.stream(piles).max().getAsInt();
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (hoursNeeded(piles, mid) <= hours) hi = mid;   // feasible -> try slower
        else lo = mid + 1;                                // too slow -> speed up
    }
    return lo;
}
static int hoursNeeded(int[] piles, int speed) {
    int h = 0;
    for (int p : piles) h += (p + speed - 1) / speed;     // ceil division
    return h;
}
// minEatingSpeed({3,6,7,11}, 8) -> 4
//   at speed 4: hours = ceil(3/4)+ceil(6/4)+ceil(7/4)+ceil(11/4) = 1+2+2+3 = 8 <= 8 -- feasible
//   at speed 3: hours = 1+2+3+4 = 10 > 8 -- infeasible
//   so 4 is the minimum feasible speed
```

In this example: `lo` starts at `1` (slowest possible speed) and `hi` starts at the largest single pile (eating an entire pile in one hour is always enough, so no speed beyond the max pile size is ever necessary). The loop narrows toward the smallest speed for which `hoursNeeded(piles, speed) <= hours` holds, exploiting that "feasible" speeds form a contiguous range `[answer, infinity)` — once you find *any* feasible speed, every larger speed is also feasible, so binary search correctly converges on the boundary.

Other classic "binary search on the answer" problems, all sharing this exact shape: **ship capacity within D days** (minimize the ship's max daily-load capacity such that all packages ship within D days — monotonic in capacity), **split array largest sum** (minimize the largest subarray sum when splitting into k parts — monotonic in the max-sum bound), **smallest divisor given a threshold** (minimize a divisor such that the sum of ceil-divided values stays under a threshold — same shape as Koko).

**Why it's useful**: this pattern converts problems that look like they need dynamic programming or exhaustive search into a clean O(log(range) × O(check)) solution — recognizing "minimize/maximize X such that a monotonic condition holds" as this pattern, rather than reaching for brute force, is a significant interview differentiator.

**Summary — Key Takeaways:**
- Binary search on the answer applies when a feasibility predicate over a *range of possible answers* is monotonic — not when the input array itself is sorted.
- Recognize it from "minimize/maximize X such that Y holds" phrasing.
- Complexity: O(log(range) × cost of the feasibility check per candidate) — often far better than brute-forcing every candidate.
- Koko/ship-capacity/split-array-largest-sum/smallest-divisor are all the same underlying pattern with a different feasibility check.

---

## 7. Quickselect — Kth Smallest/Largest Without Full Sorting

**Quickselect** finds the Kth smallest (or largest) element **without fully sorting** the array, using the same partitioning idea as quicksort but recursing into only the *one* side that's guaranteed to contain the target rank.

- **The idea**: partition the array around a pivot exactly as in quicksort (section 2). After partitioning, the pivot sits at its final sorted index `p`. If `p` equals the target index, you're done — that's the answer. If `p` is less than the target index, the answer must be in the right partition, so recurse (or loop) there; if greater, recurse left. Unlike full quicksort, only **one** side is ever explored, never both.
- **Time Complexity**: **O(n) average** — each partitioning pass costs O(current range size), and because only one side is recursed into, the total work across all passes forms a geometric series that sums to O(n), not O(n log n). **O(n²) worst case** on adversarial pivot choices, same risk as quicksort.
- **Space Complexity**: **O(1)** — this iterative version partitions in place with no extra arrays.

```java
static int quickSelect(int[] a, int k) {
    int lo = 0, hi = a.length - 1, target = k - 1;         // kth smallest = index k-1
    while (lo <= hi) {
        int p = partition(a, lo, hi);
        if (p == target) return a[p];
        else if (p < target) lo = p + 1;
        else hi = p - 1;
    }
    return -1;
}
// quickSelect({7,10,4,3,20,15}, 3) -> 7   (the 3rd smallest; sorted the array would be [3,4,7,10,15,20])
```

In this example: the target index for "3rd smallest" is `k - 1 = 2` (0-indexed). Each partition pass places its pivot at its final sorted position and reports that index — the loop keeps discarding the half of the array that *can't* contain index 2 until the pivot itself lands exactly on index 2, at which point `a[p]` (the value `7`) is returned directly, without ever fully sorting the rest of the array.

| Approach | Time | Space | Streams? |
|---|---|---|---|
| Quickselect | O(n) average, O(n²) worst | **O(1)** | No — needs the full array up front, mutates it |
| Size-K heap (Phase 8) | O(n log k) | O(k) | **Yes** — works incrementally on a stream |
| Full sort then index | O(n log n) | O(n) or O(1) in-place | No |

**Why it's useful**: quickselect beats the heap approach whenever you can freely mutate the input array and only need a single Kth-element answer (not a running/streaming one) — it's also exactly how you'd find a **median without fully sorting**: the median is just the Kth-smallest element with `k = n/2` (or the average of two such queries for even-length arrays).

**Summary — Key Takeaways:**
- Quickselect = quicksort's partition step, but recurse into only the side containing the target rank — O(n) average, O(1) space.
- O(n²) worst case, same adversarial-pivot risk as quicksort — mitigate the same way (random/median-of-three pivot).
- Prefer quickselect over a size-K heap when you can mutate the array and only need one offline query; prefer the heap when data streams in incrementally.

---

## 8. Practical Notes

- **Sort-then-optimize is a common two-step pattern**: sorting first (O(n log n)) frequently unlocks a subsequent two-pointer or greedy O(n) pass (Phases 2 and 12) that would be impossible or much harder on unsorted data — this "sort, then linear scan" combo is often the *intended* overall approach even when the problem doesn't explicitly mention sorting.
- **Custom ordering**: use a `Comparator` (see the Java track's Phase 3.2) rather than hand-rolling comparison logic inline. Be careful with the common `(a, b) -> a - b` idiom for integer comparators — it silently breaks on overflow for large or negative values close to `Integer.MIN_VALUE`/`MAX_VALUE`; prefer `Integer.compare(a, b)`, which handles this correctly.
- **Binary search bugs are almost always boundary bugs**: off-by-one errors and infinite loops trace back to exactly two places — how `lo`/`hi` are updated (must move strictly past `mid`, not onto it) and how `mid` itself is computed (overflow-safe form). Always dry-run a fix against size-0, size-1, and size-2 inputs, and both a target-present and target-absent case, before trusting a binary search variant.

**Why it's useful**: these three notes collectively explain why so many interview solutions "sort first" even when sorting isn't the final answer, why `Comparator`-based custom ordering is preferred over inline comparison logic, and why binary search — despite being conceptually simple — is disproportionately bug-prone in practice.

**Summary — Key Takeaways:**
- Sorting is very often step one of a larger O(n log n) solution, not the whole solution — watch for an O(n) two-pointer/greedy pass hiding after the sort.
- Use `Comparator` + `Integer.compare` for custom ordering, not raw subtraction (`a - b` overflows).
- Binary search bugs come from exactly two places: boundary updates and `mid` computation — always test size 0/1/2 and both present/absent targets.
