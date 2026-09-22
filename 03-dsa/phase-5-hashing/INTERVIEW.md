<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · stacks queues](../phase-4-stacks-queues/NOTES.md) | [Phase 6 · recursion backtracking ➡](../phase-6-recursion-backtracking/NOTES.md)
<!-- /nav -->

# Phase 5 — Hashing: Interview Q&A + Problems

⭐ = asked constantly.

**Q: Two Sum (unsorted array). Walk through your approach and its complexity.** ⭐⭐

Use a single pass with a `value → index` map. For each element `x`, before
inserting it, check whether `target - x` is already a key in the map — if it
is, its stored index plus the current index is the answer. If not, insert `x`
with its own index and keep going. This is O(n) time and O(n) space, versus
the O(n²) brute-force double loop.

```java
static int[] twoSum(int[] a, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < a.length; i++) {
        Integer j = seen.get(target - a[i]);
        if (j != null) return new int[]{j, i};
        seen.put(a[i], i);
    }
    return new int[]{-1, -1};
}
```

The key detail interviewers probe: you must check for the complement *before*
inserting the current element, otherwise an element could "pair with itself"
(e.g. target 4, current element 2, but there's only one 2 in the array).

*Follow-up: how would you solve this if the array were sorted and you needed
O(1) extra space?* — Two pointers from both ends, moving the low pointer up if
the sum is too small and the high pointer down if too large; O(n) time, O(1)
space, but it requires the array sorted (and loses original indices unless
you tracked them before sorting).

*Follow-up: what if you need all unique pairs, not just one, with no
duplicate pairs in the output?* — Sort first, then two-pointer, skipping over
duplicate values at both pointers after each match — this is the "3Sum"-style
generalization and needs sorting (dedup by value is much easier sorted than
via a hash-based approach).

---

**Q: Group anagrams. What's your key and what's the complexity?** ⭐

Map every word to a canonical key that's identical for all anagrams of each
other, then bucket words by that key using `computeIfAbsent`. Two common
choices of key:

```java
// Option A: sort the characters — "eat" and "tea" both become "aet"
char[] chars = w.toCharArray();
Arrays.sort(chars);
String key = new String(chars);

// Option B: a 26-length frequency signature, e.g. "1010...2" or a tuple —
// avoids sorting each word
```

Sorting each word costs O(k log k) for a word of length k, so grouping n words
of average length k is O(n·k log k). Using a fixed-length count signature
instead (Option B) drops that to O(n·k), at the cost of slightly fussier key
construction (usually joining the 26 counts into a string or using them as a
composite object key with a proper `hashCode`/`equals`).

*Follow-up: why does `computeIfAbsent` matter here vs. manual
get-check-put?* — It avoids a double map lookup (`containsKey` then
`get`/`put`) and reads as a single intention: "get this list, creating it
lazily if it's the first time." It's the standard idiom for building a
multimap out of a `Map<K, List<V>>`.

---

**Q: First unique / first non-repeating character.**

Count character frequencies in one O(n) pass (`map.merge(c, 1,
Integer::sum)`), then re-scan the string in its original order and return the
index of the first character whose count is exactly 1. Two O(n) passes, O(k)
space for k distinct characters (O(1) if you know the alphabet is bounded,
e.g. lowercase-only, and use an `int[26]` instead of a map).

*Follow-up: how would you adapt this for a live stream of characters, where
you need "first unique so far" queried repeatedly?* — Keep the frequency map
as before, plus a queue of candidates in arrival order. On each new
character, increment its count and push it onto the queue if it's new; on
each query, pop candidates off the front of the queue while their count is >1
(they're no longer unique), and the new front (if any) is the answer. This
keeps each character enqueued/dequeued at most once — amortized O(1) per
character.

---

**Q: Longest consecutive sequence, in O(n).** ⭐

Put every number in a `HashSet`. For each number `x`, only attempt to count a
run **if `x - 1` is not in the set** — that means `x` is the smallest element
of its run. From there, count upward (`x+1`, `x+2`, ...) while each successor
is present. Because every number is only ever the *start* of a counted run
once (numbers in the middle of a run are skipped by the `x-1` check), and
every number is only ever visited by the counting `while` loop once total
across the whole algorithm, total work is O(n) — despite the nested-looking
loop structure.

```java
static int longestConsecutive(int[] a) {
    Set<Integer> set = new HashSet<>();
    for (int x : a) set.add(x);
    int best = 0;
    for (int x : set) {
        if (!set.contains(x - 1)) {
            int len = 1;
            while (set.contains(x + len)) len++;
            best = Math.max(best, len);
        }
    }
    return best;
}
```

This beats the naive "sort, then scan for consecutive runs" approach, which
is O(n log n) because of the sort.

*Follow-up: why is this still O(n) even though there's a loop inside a loop?*
— Because the inner `while` only ever runs starting from a run's smallest
element, and each number belongs to exactly one run. Summed across every
outer-loop iteration, the inner loop's total iterations across the *whole*
execution equal the total count of numbers that are part of *some* run — at
most n. This is an amortized/aggregate analysis argument, the same style used
to justify that dynamic array `push` is amortized O(1) despite occasional
O(n) resizes.

---

**Q: Count duplicate within distance k (`containsNearbyDuplicate`).**

Keep a `value → last-seen-index` map. For each element, if it was seen before
and the gap between the current index and that last index is `≤ k`, return
true. Otherwise, update the map with the current index and continue. O(n)
time, O(min(n, distinct values)) space — each value only ever needs its
*most recent* occurrence tracked, not all of them.

*Follow-up: how would you extend this to "duplicate within index distance k
**and** value distance t" (contains-nearby-almost-duplicate)?* — Bucket each
value by `value / (t + 1)` in a `TreeMap` (or a `HashMap` plus checking
adjacent buckets), maintaining only a sliding window of the last `k` indices
(remove the value that falls outside the window each step). This combines the
last-seen-map idea with a bucketing trick to check "close enough" instead of
exact equality.

---

**Q: Count subarrays with sum = k, in O(n).** ⭐

Track a running prefix sum and a map of `prefix-sum value → how many times
it's occurred so far`, seeded with `{0: 1}` to account for subarrays starting
at index 0. For each new running total, the number of valid subarrays
*ending here* equals `count.getOrDefault(running - k, 0)`, because
`sum(i..j) = prefix[j] - prefix[i-1]`, so any earlier prefix equal to
`running - k` marks a valid starting point.

```java
static int subarraySum(int[] a, int k) {
    Map<Integer, Integer> count = new HashMap<>();
    count.put(0, 1);
    int running = 0, res = 0;
    for (int x : a) {
        running += x;
        res += count.getOrDefault(running - k, 0);
        count.merge(running, 1, Integer::sum);
    }
    return res;
}
```

*Follow-up: what would break if you seeded the map with `{}` (empty) instead
of `{0: 1}`?* — Any subarray starting at index 0 whose sum happens to exactly
equal `k` would be missed, because there would be no entry for prefix `0` to
match against `running - k` when `running == k`.

*Follow-up: how would you adapt this to "subarray sum divisible by k"
instead?* — Key the map by `running % k` instead of `running` itself (careful
to normalize negative remainders in Java, since `%` can return negative for
negative operands); two prefixes with the same remainder mean the subarray
between them is divisible by k.

---

**Q: When would you use a `TreeMap` instead of a `HashMap`?** ⭐

When you need ordering: sorted iteration, range queries, or
floor/ceiling/first/last lookups. `TreeMap` is backed by a red-black tree —
O(log n) for every operation, but keys always come back in sorted order (by
natural ordering or a supplied `Comparator`), and it exposes navigation
methods (`firstKey`, `lastKey`, `floorKey`, `ceilingKey`, `headMap`,
`tailMap`) that a `HashMap` simply cannot offer, since a `HashMap`'s iteration
order is unspecified and unrelated to key order.

*Follow-up: what about `LinkedHashMap` — when is that the right call?* —
When you need **insertion order** preserved during iteration (a `HashMap`
gives no order guarantee at all), or when you want an access-ordered map that
auto-evicts its least-recently-used entry — the standard building block for
an LRU cache via `removeEldestEntry`. It keeps `HashMap`'s O(1) average
performance while adding a doubly linked list to track order.

---

**Q: What's the worst-case complexity of a hash map operation?**

O(n) if many keys collide into the same bucket (e.g., a malicious or
pathological set of keys all hashing to the same value), since in the
non-treeified case that bucket degrades to a linked list that must be walked.
Mitigations: a good, well-distributed hash function; load-factor-triggered
resizing (default load factor 0.75, capacity doubles, keeping average chain
length short); and, in Java 8+, automatically converting a long bucket chain
(8+ entries, table capacity ≥ 64) into a red-black tree, bounding that
bucket's worst case to O(log n) instead of O(n). Average case across
well-distributed keys stays O(1).

*Follow-up: does resizing itself cost anything?* — Yes — when a resize
triggers, every existing entry must be rehashed into the new, larger table,
which is an O(n) operation. But because resizes happen exponentially less
often as the map grows (capacity doubles each time), the *amortized* cost per
insert averaged over many inserts remains O(1) — the same amortized argument
used for dynamic array growth.

---

**Q: What must a key type guarantee to work correctly in a hash map?** ⭐

Correct, mutually-consistent `hashCode()` and `equals()`: if
`a.equals(b) == true`, then `a.hashCode() == b.hashCode()` must also be true
(the reverse isn't required — unequal objects sharing a hash is just a normal
collision, handled by chaining). And the key must be **effectively immutable**
while stored — if fields that feed into `hashCode()` change after insertion,
the entry stays physically in its old bucket, but future lookups recompute
the hash from the object's new state and search the *wrong* bucket, making
the entry silently unreachable (`get`/`containsKey` fail even though
`values()`/iteration would still show it).

```java
Map<Point, String> labels = new HashMap<>();
Point p = new Point(1, 2);
labels.put(p, "origin-ish");
p.x = 99;                      // mutate a hash-relevant field after insertion
labels.get(p);                 // -> null! bucket for the entry didn't move with the mutation
```

*Follow-up: is this only a theoretical concern, or have you seen it in real
code?* — It's a real bug class — mutable domain objects used as cache keys or
in a "visited" `Set` during graph traversal are classic places this bites.
The fix is either making the key type immutable, or keying by a stable
derived value (an ID string/long) instead of the mutable object itself.

---

**Q: Find the single number where every other element appears exactly
twice.**

XOR all elements together. XOR is commutative, associative, and self-inverse
(`a ^ a == 0`, `a ^ 0 == a`), so every pair of duplicate values cancels to 0
regardless of order, leaving only the unpaired value.

```java
static int singleNumber(int[] a) {
    int result = 0;
    for (int x : a) result ^= x;
    return result;
}
// singleNumber({4,1,2,1,2}) -> 4
```

O(n) time, **O(1) space** — this is the go-to answer when an interviewer
follows a hash-set-based duplicate-finding solution with "can you do it
without extra space?"

*Follow-up: what if every element appears three times except one?* — XOR
alone doesn't work (it only cancels pairs). The general technique is bit
counting: for each of the 32 bit positions, sum how many numbers have that
bit set; if the count isn't a multiple of 3, the unique number has that bit
set. This is O(32n) = O(n) time, O(1) space.

---

**Q: Top K frequent elements.**

First count frequencies with a map (O(n)). Then select the K highest:

- A **min-heap of size k** (`PriorityQueue`): push every `(count, element)`
  pair, popping the smallest whenever size exceeds k. O(n log k) time, O(n)
  space (Phase 8's heap material) — good when k is small relative to n.
- **Bucket sort by frequency**: since frequency can never exceed n, create an
  array of n+1 buckets indexed by frequency, drop each element into
  `bucket[freq]`, then scan buckets from n down to 0 collecting elements
  until k are gathered. O(n) time, O(n) space — strictly better
  asymptotically, and the answer to give if the interviewer asks "can you do
  better than O(n log k)?"

*Follow-up: why not just sort all (element, count) pairs by count and take
the top k?* — Correct but suboptimal: that's O(n log n), strictly worse than
either the heap (O(n log k)) or bucket-sort (O(n)) approaches once k is
meaningfully smaller than n.

---

**Q: LRU cache design.** ⭐

Combine a hash map (`key → node`) with a doubly linked list that tracks usage
order, so that both "find by key" and "move to most-recently-used" /
"evict least-recently-used" are O(1). On `get`, look the node up in the map
(O(1)) and move it to the front of the list (O(1) — splicing a doubly linked
list node needs no traversal since you already hold a reference to it). On
`put`, if at capacity, evict the tail node (O(1)) and remove it from the map
too.

In Java, this is exactly what `LinkedHashMap` already implements internally
when constructed with `accessOrder=true` — overriding `removeEldestEntry` to
return `size() > capacity` gives you a working LRU cache in a handful of
lines instead of hand-rolling the doubly linked list.

```java
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    LRUCache(int capacity) {
        super(16, 0.75f, true);   // accessOrder = true
        this.capacity = capacity;
    }
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
```

This is the classic hashing + linked-list mashup (a direct tie-in to Phase
3's linked list material): a map alone can't maintain recency order, and a
linked list alone can't do O(1) lookup by key — you need both together to hit
O(1) on every operation.

*Follow-up: why does a doubly (not singly) linked list matter here?* —
Eviction and move-to-front both need to unlink a node from the *middle* of
the list in O(1), which requires knowing both its previous and next
neighbors. A singly linked list would need O(n) traversal to find a node's
predecessor before it could be unlinked.

*Follow-up: what would you use instead if you needed an LFU (least
frequently used, not least recently used) cache?* — A frequency-bucketed
structure: a map from key to (value, frequency), plus a map from frequency to
an ordered set/list of keys at that frequency (a doubly linked list per
frequency bucket), plus tracking the current minimum frequency — evicting
from the min-frequency bucket's least-recently-used slot in O(1). Meaningfully
more complex than LRU because there are now two orderings to maintain
(frequency, and recency within a frequency).
