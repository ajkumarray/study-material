<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · stacks queues](../phase-4-stacks-queues/NOTES.md) | [Phase 6 · recursion backtracking ➡](../phase-6-recursion-backtracking/NOTES.md)
<!-- /nav -->

# Phase 5 — Hashing: Notes

## The core power
A hash map/set gives **O(1) average** insert, lookup, delete (Java Phase 3.2 — buckets indexed by `hashCode`, resolved by `equals`, treeified past a threshold). Worst case is O(n) if hashes collide badly, but with good hashing it's O(1) amortized. That single power converts many O(n²) scans into O(n): the recurring instinct is **"spend O(n) space to buy O(n) time."**

## The patterns
- **"Have I seen X?" / complement lookup** — two-sum unsorted: store each value→index; for `x`, look up `target - x` in one pass. O(n) vs the O(n²) double loop (and no sort needed, unlike the two-pointer version).
- **Frequency counting** — tally counts in a map (`merge(c, 1, Integer::sum)`), then answer questions: first unique char, majority element, top-k frequent, valid anagram. For fixed alphabets an `int[26]`/`int[128]` array is a faster hash map.
- **Grouping by a canonical key** — map a *derived key* to a list of members: anagrams keyed by sorted letters (or a 26-count signature), points keyed by slope, etc. `computeIfAbsent(k, x -> new ArrayList<>()).add(v)` is the lazy-multimap idiom (Java Phase 3.2).
- **Set for O(1) membership** — longest consecutive sequence: put all in a set, and only start counting a run from its smallest element (`x-1` absent). Each number is touched O(1) times → **O(n)** overall (beating the O(n log n) sort-then-scan).
- **Prefix sum + map** — count/find subarrays with a target sum in O(n) (Phase 2.3): the key trick that shows up again and again (subarray sum = k, equal 0s/1s, divisible sums).
- **Index/last-seen maps** — "duplicate within distance k," sliding-window bookkeeping (Phase 2.2's variable window uses exactly this).

## When NOT to reach for a hash map
- **Ordered** queries (range, min/max, floor/ceiling, sorted iteration) → use a `TreeMap`/`TreeSet` (balanced BST, O(log n)) instead — a hash map has no order.
- **Small fixed key domains** (lowercase letters, bytes) → a plain array is faster and simpler than a `HashMap`.
- **Custom object keys** → they *must* implement `equals`/`hashCode` correctly (Java Phase 2.5), and be effectively immutable while stored, or lookups silently fail (the mutated-key bug).

## Complexity summary
Most hashing solutions here are **O(n) time, O(n) space**. The trade is memory for speed; when space is constrained, the fallback is often sort-first (O(n log n) time, O(1)/O(log n) space) or a bit/math trick (XOR for the single-number problem, cyclic sort for 1..n ranges).
