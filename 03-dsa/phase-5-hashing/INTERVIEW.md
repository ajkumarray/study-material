<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · stacks queues](../phase-4-stacks-queues/NOTES.md) | [Phase 6 · recursion backtracking ➡](../phase-6-recursion-backtracking/NOTES.md)
<!-- /nav -->

# Phase 5 — Hashing: Interview Q&A + Problems

⭐ = asked constantly.

**Q: Two Sum (unsorted).** ⭐⭐
One pass with a `value → index` map; for each `x`, check if `target - x` was seen. O(n) time & space. The archetypal "hash map removes the inner loop" problem.

**Q: Group anagrams.** ⭐
Map a canonical key (sorted letters, or a 26-length count signature) to the list of words sharing it. O(n·k log k) with sorting, O(n·k) with the count key.

**Q: First unique / first non-repeating character.**
Count frequencies in one pass, then scan for the first char with count 1. O(n). For a stream, pair a count map with a queue.

**Q: Longest consecutive sequence (O(n)).** ⭐
Put all numbers in a set; start a run only from a number whose predecessor is absent, then count upward. Each number is visited O(1) times → O(n), beating sort's O(n log n).

**Q: Count subarrays with sum = k.** ⭐
Prefix sum + a map of prefix-sum frequencies (Phase 2). For each running sum, add the count of `running - k` seen so far. O(n).

**Q: When would you use a `TreeMap` instead of a `HashMap`?** ⭐
When you need ordering: sorted iteration, range queries, or floor/ceiling/first/last. `TreeMap` is a balanced BST — O(log n) ops but ordered; `HashMap` is O(1) but unordered.

**Q: What's the worst-case complexity of a hash map operation?**
O(n) if many keys collide into one bucket (all same hash). Mitigations: a good hash function, load-factor-triggered resizing, and Java 8+ treeifying long buckets to O(log n). Average case stays O(1).

**Q: What must a key type guarantee to work in a hash map?** ⭐
Correct, consistent `hashCode` and `equals` (equal objects → equal hashes; Java Phase 2.5), and effective immutability of the hash-relevant fields while stored — mutating a key after insertion makes it unfindable.

**Q: Find the single number (every other appears twice).**
XOR all elements — pairs cancel to 0, leaving the unique value. O(n) time, **O(1) space** — the bit trick that beats the obvious hash-set answer.

**Q: Top K frequent elements.**
Count with a map, then either a min-heap of size k (O(n log k), Phase 8) or bucket sort by frequency (O(n)). A common "combine two data structures" question.

**Q: LRU cache design.**
Hash map (key → node) + doubly linked list (usage order): O(1) get and put with move-to-front and evict-from-back. The classic hashing + linked-list mashup (Phase 3 tie-in).
