<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · arrays ➡](../phase-2-arrays/NOTES.md)
<!-- /nav -->

# Phase 1 — Complexity & Foundations: Notes

## 1.1 — Big-O and how to analyze

**Big-O** describes how an algorithm's cost **grows** as input size `n` grows, ignoring constants and lower-order terms. It answers the only question that matters at scale: *"does this still work when n is a million?"*

- **O(1)** constant — independent of n (array index, hash lookup).
- **O(log n)** logarithmic — halve the problem each step (binary search, balanced-tree ops).
- **O(n)** linear — one pass (sum, scan, single loop).
- **O(n log n)** — the good sorts (merge/quick/heap); the practical floor for comparison sorting.
- **O(n²)** quadratic — nested loops over the same data (naive pair checks). Doubling n **quadruples** the work (measured in the demo: 8k→16k roughly 4×).
- **O(2ⁿ)**, **O(n!)** — exponential/factorial; only viable for tiny n (naive subsets/permutations — Phase 6).

**The three notations:** Big-**O** = upper bound (worst case, the common one); **Ω** = lower bound (best case); **Θ** = tight bound (same upper and lower). Interviewers usually mean worst-case Big-O; say so.

**How to analyze code:**
- Sequential statements add → keep the dominant term (`O(n) + O(n²) = O(n²)`).
- Nested loops multiply (`for×for` over n → `O(n²)`); loops over *different* inputs → `O(n·m)`.
- Halving each iteration → `O(log n)`; a loop that does O(n) work log n times → `O(n log n)`.
- **Drop constants and lower terms:** `O(2n + 5)` is `O(n)`, `O(n² + n)` is `O(n²)`.

## 1.2 — Space, amortized, recursion cost

**Space complexity** = extra memory beyond the input. `sum` uses O(1); the hash-set dedupe uses O(n). The **time–space tradeoff** is a recurring theme: the demo's duplicate check is O(n²) time / O(1) space with nested loops, or O(n) time / O(n) space with a set — *choosing the data structure changes the complexity class.* This is the single most useful interview instinct: "can a hash map turn this O(n²) into O(n)?"

**Amortized analysis** — average cost per operation over a sequence. `ArrayList.add` is *amortized O(1)*: it doubles capacity when full, so most adds are O(1) and the occasional O(n) resize averages out (1,000,000 adds → only ~20 resizes in the demo). Same idea behind HashMap resizing (Java Phase 3.2).

**Recursion cost:** time = (number of calls) × (work per call); space = **max recursion depth** × (frame size), because each pending call holds a stack frame (Java Phase 1.4 — `StackOverflowError` when too deep). A recursion tree that branches into 2 with depth n is O(2ⁿ) calls but only O(n) stack space. Analyzing recursive time often uses recurrences (e.g., `T(n) = 2T(n/2) + O(n)` → O(n log n) by the Master Theorem — merge sort).

**Practical caveats:** Big-O hides constants that matter in reality — an O(n) with huge constants can lose to an O(n log n) for realistic n; cache locality makes contiguous arrays beat "theoretically equal" linked structures (Java Phase 3.2, ArrayList vs LinkedList). Big-O is the first filter, not the whole story.
