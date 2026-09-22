<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · graphs](../phase-9-graphs/NOTES.md) | [Phase 11 · dynamic programming ➡](../phase-11-dynamic-programming/NOTES.md)
<!-- /nav -->

# Phase 10 — Sorting & Searching: Interview Q&A + Problems

⭐ = asked constantly.

**Q: Compare merge sort and quick sort.** ⭐⭐

Merge sort divides the array in half recursively, then merges sorted halves back together, always taking the smaller of the two current fronts. It's **stable** (the merge step uses `<=`, so an equal element from the left half is taken before the right half's), guarantees **O(n log n)** in the average *and* worst case regardless of input order, but needs O(n) auxiliary space for the merge buffers. Quick sort picks a pivot and partitions the array so everything smaller ends up left of the pivot and everything larger ends up right, then recurses on both sides. It's **not stable** (partitioning swaps elements by position, not preserving relative order of equals), runs O(n log n) on average but degrades to **O(n²)** on adversarial pivot choices (e.g., an already-sorted array with a fixed last-element pivot), and sorts in place with only O(log n) recursion-stack space.

```java
// Merge's stability-preserving comparison:
while (i < l.length && j < r.length) out[k++] = (l[i] <= r[j]) ? l[i++] : r[j++];
```

In practice, merge sort is preferred when stability matters (multi-key sorts), when a guaranteed worst case matters (real-time systems, adversarial-input concerns), or for external sorting / linked lists (no random access needed). Quick sort is preferred when average-case speed and low memory overhead matter more than worst-case guarantees or stability — with randomized or median-of-three pivot selection to make the O(n²) worst case practically unreachable.

*Follow-up: Why does `Arrays.sort` use a dual-pivot quicksort variant for primitive arrays but Timsort for object arrays?* Primitives have no identity beyond their value, so stability is meaningless for them — quicksort's speed and low memory overhead win. Objects can carry additional fields beyond the sort key, so equal-by-comparator objects can still be meaningfully distinct — Timsort's stability (it's a merge/insertion hybrid) preserves that distinction, which quicksort cannot guarantee.

---

**Q: What is a stable sort and when does it matter?** ⭐

A stable sort preserves the original relative order of elements that compare equal under the sort key. It matters whenever you sort by one key but want to preserve meaningful order from a *previous* sort or the original input on a secondary dimension — e.g., sort a list of people by age with a stable sort, and among people of the same age, they retain whatever order they had before (which might itself be alphabetical by name, if you sorted by name first). This lets you build a correct multi-key sort by chaining single-key stable sorts, from least significant key to most significant, without writing a composite comparator.

```java
// Two successive stable sorts, least-significant key first:
people.sort(Comparator.comparing(Person::getFirstName));
people.sort(Comparator.comparing(Person::getLastName));   // stable -- ties keep first-name order
```

Merge sort and insertion sort are stable; quick sort and heap sort are not (both rearrange elements via swaps that don't track or preserve original relative position among equals).

*Follow-up: How would radix sort break if the underlying digit-bucket pass weren't stable?* Radix sort relies on processing digits from least-significant to most-significant, using a stable pass at each digit so that the ordering established by previous (less significant) digit passes is preserved when the current (more significant) digit ties. An unstable per-digit sort would scramble that previously-established order on every tie, producing an incorrect final result.

---

**Q: What's the lower bound for comparison sorting? How do counting/radix beat it?** ⭐

Ω(n log n) is a proven lower bound for any algorithm that sorts purely by pairwise comparisons. The argument is information-theoretic: there are n! possible orderings of n distinct elements, and each comparison yields at most one bit of information (it branches one of two ways), so distinguishing among n! possibilities requires at least `log2(n!) = Θ(n log n)` comparisons in the worst case — a decision-tree argument, independent of any specific algorithm's implementation.

Counting sort, radix sort, and bucket sort achieve O(n) by simply not being comparison sorts at all — they index directly into buckets by value (or digit), extracting more information per step than a single comparison could. This only works when the keys have exploitable structure: a bounded range of integer values (counting/bucket sort) or a fixed number of digits/bytes (radix sort). They don't contradict the Ω(n log n) bound; they sidestep its assumptions entirely by not comparing.

*Follow-up: Could you use counting sort to sort an array of arbitrary strings?* Not directly — counting sort needs a bounded, small range of possible key values to index buckets by. Strings don't have that property in general (unbounded length, unbounded distinct values), though radix sort *can* be adapted to sort strings character-by-character (treating each character position as a "digit"), which is how string radix sort / MSD radix sort works.

---

**Q: Write binary search. What are the classic bugs?** ⭐⭐

```java
static int binarySearch(int[] a, int target) {
    int lo = 0, hi = a.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;       // avoids (lo+hi) overflow
        if (a[mid] == target) return mid;
        else if (a[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}
```

The loop condition is `lo <= hi` (not `<`), since a single-element remaining range (`lo == hi`) still needs to be checked. `mid` is computed as `lo + (hi - lo) / 2` rather than `(lo + hi) / 2` — the latter can overflow a 32-bit `int` when both `lo` and `hi` are large, a bug that famously shipped in real production binary-search implementations (including an early JDK version) before being widely publicized. On each non-match, `lo`/`hi` must move **strictly past** `mid` (`mid + 1` / `mid - 1`), never landing back on `mid` itself — using `lo = mid` or `hi = mid` risks the search range never shrinking, causing an infinite loop when the range narrows to two elements.

*Follow-up: Trace binary search on `{1,3,5,7,9,11}` searching for `8`.* `lo=0,hi=5,mid=2` (`a[2]=5<8`, so `lo=3`); `lo=3,hi=5,mid=4` (`a[4]=9>8`, so `hi=3`); now `lo=3,hi=3,mid=3` (`a[3]=7<8`, so `lo=4`); now `lo=4>hi=3`, loop exits, return `-1` — correctly reporting `8` isn't present.

*Follow-up: How would you adapt this to search a `List<Integer>` (no direct array indexing) or a data source too large to hold in memory?* For a `List`, the same index-based logic works as long as `get(index)` is O(1) (true for `ArrayList`, false for `LinkedList` — binary search on a `LinkedList` degrades badly since each `get` is itself O(n)). For data too large for memory, you'd need the data source to support random access by some form of offset/index (e.g., a sorted file with fixed-width records, or an external index) rather than plain in-memory array access.

---

**Q: Find the first/last occurrence of a value (with duplicates).** ⭐

Run the same binary search skeleton, but on a match, don't return immediately — record the index as a candidate and keep narrowing in the direction that might reveal an even better match: continue searching left (`hi = mid - 1`) for the first occurrence, or right (`lo = mid + 1`) for the last occurrence.

```java
static int firstOccurrence(int[] a, int target) {
    int lo = 0, hi = a.length - 1, res = -1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (a[mid] == target) { res = mid; hi = mid - 1; }
        else if (a[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return res;
}
```

This enables counting occurrences of a value in a sorted array in O(log n) — `lastOccurrence - firstOccurrence + 1` — instead of an O(n) linear scan, and it's the core mechanism behind `Arrays.binarySearch`-style insertion-point and range queries.

*Follow-up: How would you find the insertion point for a value not present in the array (i.e., where it *would* go to keep the array sorted)?* A minor variant: track the smallest index where `a[mid] >= target` becomes true (never breaking early on a match, always continuing to narrow `hi = mid - 1` whenever `a[mid] >= target`), converging on the leftmost position where `target` could be inserted without violating sort order — this is essentially "lower bound" in the C++ `std::lower_bound` sense.

---

**Q: Search in a rotated sorted array.** ⭐⭐

At every step of the search, **at least one half** of the current range is guaranteed to be internally sorted, even though the array as a whole isn't (since it's a sorted array rotated at some unknown pivot). Determine which half is sorted by comparing `a[lo]` to `a[mid]`; then check whether the target's value falls within that sorted half's range — if it does, recurse (or narrow) into that half; if it doesn't, the target (if present) must be in the other half.

```java
static int searchRotated(int[] a, int target) {
    int lo = 0, hi = a.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (a[mid] == target) return mid;
        if (a[lo] <= a[mid]) {
            if (a[lo] <= target && target < a[mid]) hi = mid - 1;
            else lo = mid + 1;
        } else {
            if (a[mid] < target && target <= a[hi]) lo = mid + 1;
            else hi = mid - 1;
        }
    }
    return -1;
}
```

This remains O(log n) — the same halving-the-search-space logic as plain binary search, just with an extra decision step to identify which half is trustworthy before applying the usual "is the target in this range?" check.

*Follow-up: How would this change if the array could contain duplicate values (e.g., `{1,1,1,0,1}`)?* Duplicates can defeat the "which half is sorted" check — if `a[lo] == a[mid]`, you can't tell which half is sorted from that comparison alone. The fix is to handle that ambiguous case by shrinking the range conservatively (`lo++`, or equivalently `hi--`), which degrades the worst-case complexity to O(n) (imagine an array of all-identical values with one different value hidden somewhere — you're forced toward a near-linear scan in that adversarial case).

---

**Q: What is "binary search on the answer"?** ⭐⭐

When you're asked to minimize or maximize some value X subject to a condition, and a feasibility predicate "is X (or better) achievable?" is **monotonic** — once true for some value, it stays true for every value beyond it in the same direction — you can binary search directly over the space of possible *answers*, not over the input array. This solves "minimum eating speed" (Koko), "ship capacity in D days," "split array largest sum," and "smallest divisor given a threshold" — all share the shape "minimize/maximize X such that a monotonic condition holds."

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
```

Complexity is O(log(range) × cost-of-feasibility-check) — for Koko, that's O(log(maxPile) × n) since each feasibility check scans all piles. This is usually dramatically better than brute-forcing every candidate value in the range linearly.

*Follow-up: Why is `hoursNeeded` monotonically decreasing in speed, and why does that matter for correctness?* Faster eating speed can only reduce (or keep equal) the hours needed for any given pile — it never increases them. That monotonicity is exactly what guarantees the set of "feasible" speeds forms a single contiguous range `[answer, ∞)` with no gaps, which is the precondition binary search requires to correctly converge on the boundary rather than potentially skipping over the true answer.

*Follow-up: How would "split array largest sum" (split an array into k contiguous subarrays minimizing the largest subarray sum) use this same pattern?* Binary search over the candidate "largest allowed subarray sum" value, from `max(single element)` to `sum(all elements)`. The feasibility check greedily accumulates a running subarray sum, starting a new subarray whenever adding the next element would exceed the candidate maximum, and counts how many subarrays that requires — feasible if that count is `<= k`. Smaller candidate maximums are harder to satisfy (monotonic), so binary search converges on the smallest feasible "largest sum."

---

**Q: Find the Kth largest/smallest element.** ⭐

Two standard approaches, both worth stating with their trade-offs. **Quickselect**: partition like quicksort, but recurse into only the side containing the target rank — O(n) average, O(1) space, but requires mutating the array and doesn't stream. **Size-K heap** (Phase 8): maintain a min-heap capped at size K (for "Kth largest") — O(n log k), doesn't mutate the input, and naturally handles a streaming input where elements arrive over time.

```java
static int quickSelect(int[] a, int k) {
    int lo = 0, hi = a.length - 1, target = k - 1;
    while (lo <= hi) {
        int p = partition(a, lo, hi);
        if (p == target) return a[p];
        else if (p < target) lo = p + 1;
        else hi = p - 1;
    }
    return -1;
}
```

*Follow-up: In what scenario would the heap approach actually be preferable to quickselect, despite quickselect's better average-case complexity?* Whenever the data arrives incrementally (a stream) rather than being fully available up front, or when the input array must not be mutated (quickselect rearranges the array in place during partitioning), or when you need to answer the query repeatedly as new data arrives — a heap handles all of these naturally, while quickselect is fundamentally a one-shot, offline, mutating algorithm.

---

**Q: Median of two sorted arrays.**

Binary search on the smaller of the two arrays' partition point, seeking a split of both arrays into left/right halves such that every element in the combined "left" portion is `<=` every element in the combined "right" portion, and the two portions are equal in size (or differ by one for odd total length). Once the correct partition is found, the median is derivable directly from the four boundary elements around the partition (the max of the two "left" portions, and/or the min of the two "right" portions, depending on odd/even total length). Binary searching over the smaller array's partition point (rather than iterating all possible partitions) achieves **O(log(min(m, n)))** — a notably tighter bound than a naive O(m+n) merge-based median.

*Follow-up: Why binary search specifically on the smaller array?* Once you fix a partition point in the smaller array, the corresponding partition point in the larger array is fully determined (it must make the two "left" portions sum to exactly half the combined length) — so you only need to search one array's partition space, and choosing the smaller array minimizes both the search space size and the risk of the derived partition point in the other array going out of bounds.

*Follow-up: Why is this considered a "hard" binary search problem specifically?* Unlike standard binary search where "found" vs "not found" is a simple comparison, correctness here requires simultaneously satisfying two cross-array ordering conditions at the partition boundary, and the update rule (which direction to move the search) depends on both conditions together — it's a genuine binary search, but over a much subtler feasibility condition than "is `a[mid]` less than the target."

---

**Q: How would you sort a nearly-sorted array, or a huge file that doesn't fit in memory?**

For a **nearly-sorted array**: insertion sort runs in O(n·k) where k is roughly "how far out of place" elements are, which approaches O(n) when the array is only slightly disturbed from sorted order — far better than a generic O(n log n) sort's constant factors for this specific case. In practice, Timsort (used by `Collections.sort`/`List.sort`) already detects and exploits existing sorted "runs" in the input automatically, so calling the standard library sort on nearly-sorted data is often close to optimal without any special-casing.

For a **file too large to fit in RAM**: **external merge sort** — split the file into chunks small enough to fit in memory, sort each chunk in place (using any in-memory sort), write each sorted chunk back to disk, then perform a k-way merge of all the sorted chunks using a min-heap (Phase 8) of the current front element from each chunk, writing the merged result out sequentially. This is exactly the "merge K sorted lists" pattern from Phase 8, applied at the scale of disk-resident data instead of in-memory lists.

*Follow-up: Why merge sort specifically for external sorting, rather than quicksort?* Merge sort's merge step only needs sequential access to its inputs (read the next element from each sorted chunk in order) — it never needs random access, which is exactly what makes disk-based chunk merging efficient. Quicksort's partitioning step requires random access and in-place swapping, which doesn't translate well to data spread across disk-resident chunks.

---

**Q: Sort colors / Dutch national flag.**

Given an array containing only three distinct values (e.g., 0, 1, 2 representing red/white/blue), sort it in a single O(n) pass using O(1) extra space via **three-way partitioning** — three pointers: `low` (boundary of the "0" region, everything before it is confirmed 0), `mid` (current element under examination), and `high` (boundary of the "2" region, everything after it is confirmed 2). Walk `mid` through the array: if `a[mid] == 0`, swap it with `a[low]` and advance both `low` and `mid`; if `a[mid] == 2`, swap it with `a[high]` and decrement `high` only (don't advance `mid` — the newly-swapped-in element at `mid` hasn't been examined yet); if `a[mid] == 1`, just advance `mid`.

```java
static void sortColors(int[] a) {
    int low = 0, mid = 0, high = a.length - 1;
    while (mid <= high) {
        if (a[mid] == 0) { swap(a, low++, mid++); }
        else if (a[mid] == 2) { swap(a, mid, high--); }
        else { mid++; }
    }
}
```

This is the same three-way partitioning idea behind quicksort's "3-way" (Dutch-national-flag) variant, which groups elements into "less than pivot," "equal to pivot," and "greater than pivot" in one pass — a significant optimization for arrays with many duplicate keys, since standard 2-way partitioning wastes time repeatedly re-comparing and re-swapping equal elements.

*Follow-up: Why must the `high` swap case NOT advance `mid`?* The element swapped into position `mid` from `high` hasn't been examined yet — it could be a 0, 1, or 2 — so `mid` must stay put and re-examine that new value on the next loop iteration. In contrast, the `low` swap case is safe to advance `mid`, because the element swapped in from `low` is known to have already been confirmed as either equal to the current position's prior known state (a 1, from the invariant that everything between `low` and `mid` is 1) — it's already been "seen" and classified correctly.
