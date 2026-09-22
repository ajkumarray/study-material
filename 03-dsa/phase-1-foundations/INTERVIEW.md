<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · arrays ➡](../phase-2-arrays/NOTES.md)
<!-- /nav -->

# Phase 1 — Complexity: Interview Q&A

⭐ = asked constantly.

**Q: What is Big-O notation?** ⭐⭐

Big-O describes how the running time or memory usage of an algorithm grows as the
size of its input, `n`, grows — expressed as an upper bound and stripped of constant
factors and lower-order terms. Formally, `f(n) = O(g(n))` means there exist constants
`c` and `n₀` such that `f(n) ≤ c·g(n)` for all `n ≥ n₀`. Practically, it lets you
compare two algorithms' scalability independent of the hardware they run on, and it
lets you predict whether a solution will still work when the input is 10x or 1000x
larger than what you tested.

The reason it drops constants is that they depend on implementation details (language,
compiler, machine) that don't change the fundamental growth *shape* of the algorithm.
`3n` and `n` are both O(n) because, as n grows without bound, the difference between
them is dwarfed by the difference between `n` and, say, `n²`.

**Follow-up: Is Big-O always the worst case?**
By convention, yes, unless stated otherwise — "the Big-O of this algorithm" defaults
to worst-case input. Best case uses Ω (Big-Omega), and when best and worst case match,
you'd technically say Θ (Big-Theta). A linear search is O(n) worst case (target is
last or absent) but Ω(1) best case (target is first).

---

**Q: What's the difference between Big-O, Big-Omega, and Big-Theta?**

- **Big-O (O)** — upper bound: the algorithm grows *no faster than* this.
- **Big-Omega (Ω)** — lower bound: the algorithm grows *at least as fast as* this.
- **Big-Theta (Θ)** — tight bound: upper and lower bound coincide; the algorithm's
  growth is *exactly* this rate (up to constants).

An algorithm can have different upper and lower bounds across different inputs —
quicksort is O(n²) (upper bound, worst case: bad pivot choices on already-sorted
data) but Θ(n log n) on average. In interviews, when someone asks "what's the
complexity," they almost always want the tight worst-case bound, so answering with
Big-O in the worst-case sense is the safe default — but being able to name the best
case too (and explain *why* it differs) signals a deeper understanding.

---

**Q: Order these from fastest- to slowest-growing: O(n²), O(1), O(n log n), O(log n),
O(n), O(2ⁿ), O(n!).** ⭐

```
O(1) < O(log n) < O(n) < O(n log n) < O(n²) < O(2ⁿ) < O(n!)
```

Being able to recite this instantly matters less than being able to attach a concrete
example to each one on demand: O(1) is a hash lookup or array index; O(log n) is
binary search; O(n) is a single scan; O(n log n) is merge/heap/quick sort; O(n²) is
nested loops over the same collection; O(2ⁿ) is unmemoized recursive Fibonacci or
generating all subsets; O(n!) is brute-force permutations. Interviewers frequently
probe "give me an example of an O(n log n) algorithm" as a sanity check that you're
not just reciting the ordering.

---

**Q: Why do we drop constants and lower-order terms in Big-O?**

Big-O describes growth *rate* as `n → ∞`. As n gets arbitrarily large, the
dominant (highest-order) term overwhelms everything else — `3n² + 100n + 5` behaves
just like `n²` for large enough n, because the `100n` and `5` terms become negligible
by comparison. Constants like the `3` depend on implementation specifics (a tight
loop in one language vs. another, cache behavior, etc.) that Big-O deliberately
abstracts away so it can compare algorithms, not implementations.

**Follow-up: does this mean constants never matter?**
No — see the "is a lower Big-O always faster in practice" question below. Big-O
ignoring constants is exactly why an O(n) algorithm with a huge constant factor can
lose to an O(n log n) algorithm with a small one, for any n you'll realistically hit.

---

**Q: What's the time complexity of this nested loop? Of a loop that halves its range
each iteration?** ⭐

A nested loop where both loops run over the same input of size n is `O(n) * O(n) =
O(n²)` — for every one of the n outer iterations, the inner loop does another n units
of work. If the inner loop runs over a *different*-sized collection of size m, it's
`O(n·m)`, not `O(n²)` — don't default to squaring just because loops are nested.

A loop that halves the remaining range each iteration (binary search) is `O(log n)`:
starting from n, you can only halve it about `log₂(n)` times before reaching 1. And a
loop that does `O(n)` work and repeats that `log n` times — the shape of merge sort —
is `O(n log n)`.

Practice narrating this out loud: "the outer loop runs n times, the inner loop runs n
times for each outer iteration, so total work is n × n = O(n²)." Interviewers are
listening for that narration, not just the final answer.

---

**Q: What is amortized complexity? Give an example.** ⭐

Amortized complexity is the average cost per operation across a *sequence* of
operations, used when most operations are cheap and only occasional ones are
expensive, in a pattern that guarantees the expensive ones stay rare relative to the
sequence length. It's a worst-case guarantee over time, not a probabilistic
average-case claim about random input.

The canonical example is `ArrayList.add` (a dynamic array's append). Most appends are
O(1) — there's spare capacity, so the element just gets written. When capacity runs
out, the array allocates a new backing array (typically double the size) and copies
every existing element — an O(n) operation. But because capacity doubles rather than
growing by a fixed amount, that expensive copy only happens `O(log n)` times over n
total appends. Summed and divided across all n appends, the average cost per append
is still O(1) — "amortized O(1)."

```java
int resizes = 0, capacity = 1;
for (int i = 0; i < 1_000_000; i++) {
    if (i == capacity) { capacity *= 2; resizes++; }
}
// resizes ends up around 20 — log2(1,000,000) ≈ 20
```

**Follow-up: what would happen to the amortized complexity if the array grew by a
fixed increment (e.g. +10 capacity) instead of doubling?**
It would become O(n) amortized, not O(1). Growing by a constant amount means you'd
need roughly `n/10` resizes over n appends, and each resize copies up to n elements —
that's O(n²/10) total work over n appends, or O(n) amortized per append. Doubling
(geometric growth) is what makes the resize count logarithmic; without it, the
"amortized O(1)" guarantee collapses. `HashMap.put` relies on the exact same
geometric-growth trick when it rehashes past the load factor.

---

**Q: Give an example of a time–space tradeoff.** ⭐⭐

Duplicate detection is the cleanest example. Comparing every pair with nested loops
is `O(n²)` time but `O(1)` extra space — no new data structure, just loop counters.
Using a `HashSet` to remember what's been seen is `O(n)` time but `O(n)` extra space
— you're storing up to every element. Same problem, same correctness, opposite ends
of the time/space spectrum.

```java
static boolean hasDuplicateQuadratic(int[] a) {   // O(n^2) time, O(1) space
    for (int i = 0; i < a.length; i++)
        for (int j = i + 1; j < a.length; j++)
            if (a[i] == a[j]) return true;
    return false;
}

static boolean hasDuplicateLinear(int[] a) {       // O(n) time, O(n) space
    Set<Integer> seen = new HashSet<>();
    for (int x : a) if (!seen.add(x)) return true;
    return false;
}
```

This same instinct — "can a hash map absorb an inner loop's linear scan and turn it
into a constant-time lookup, at the cost of O(n) memory?" — solves two-sum, "first
non-repeating character," and is the entire idea behind memoization in dynamic
programming (Phase 11): spend memory on a cache to avoid recomputing the same
subproblem, turning exponential or polynomial time into something much smaller.

**Follow-up: when would you actually prefer the O(n²)/O(1) version in production?**
When n is guaranteed small and bounded (say, under a few hundred elements) and memory
really is constrained — an embedded system, or a hot path called millions of times
per second where allocating a HashSet per call creates GC pressure that outweighs the
algorithmic win. This is judgment interviewers want you to volunteer, not just the
tradeoff itself.

---

**Q: What's the space complexity of a recursive function?** ⭐

O(maximum recursion depth) for the call stack, since each pending (not-yet-returned)
recursive call occupies its own stack frame, and those frames are only released as
calls return. This is distinct from the *time* complexity, which counts the total
number of calls made across the whole execution, not just how many are pending
simultaneously.

For linear recursion (a function that calls itself once per invocation, like summing
1..n recursively), the number of calls and the maximum depth are the same, so both
time and space are O(n). For branching recursion — a function that calls itself
*twice* per invocation, like naive unmemoized Fibonacci — the total call count is
exponential, `O(2ⁿ)`, but the maximum depth at any single moment (the longest path
from the root call down to a base case) is only `O(n)`. So naive Fibonacci is `O(2ⁿ)`
time but only `O(n)` space — a distinction that trips people up if they assume time
and space always match for recursive code.

**Follow-up: what happens if recursion goes too deep in Java?**
`StackOverflowError` — the JVM's call stack has a fixed (configurable but bounded)
size, and Java performs no automatic tail-call optimization, so even a "simple" tail
recursion that goes millions of calls deep will crash rather than run in constant
stack space, unlike in languages that do guarantee TCO. This is a real reason to
prefer an iterative version or explicit stack-based simulation for very deep
recursion in Java.

---

**Q: Is a lower Big-O always faster in practice?** *senior-level nuance*

No, and being able to explain why is a stronger signal than reciting the complexity
ordering. Big-O deliberately ignores constant factors and memory-access patterns, both
of which matter enormously for real wall-clock performance:

- An `O(n)` algorithm with a large constant factor (heavy per-element work) can be
  slower than an `O(n log n)` algorithm with a small constant, for any n that fits in
  realistic workloads — Big-O only guarantees which one wins *eventually*, as n keeps
  growing without bound.
- Data structures with equal Big-O can differ hugely due to **cache locality**: a
  contiguous array is typically faster to iterate than a linked list of the same
  theoretical complexity, because the CPU can prefetch adjacent array elements into
  cache lines, while a linked list's nodes are scattered across memory and each
  traversal step can be a cache miss (this is why `ArrayList` frequently outperforms
  `LinkedList` in practice even for operations where their Big-O is nominally similar
  or LinkedList is "supposed" to win — see Phase 3).

Big-O is the first filter for eliminating obviously bad approaches (an O(n²)
algorithm on a million-row dataset is dead on arrival), not the final word on which
implementation to ship — that requires actually profiling for the real data size and
hardware.

---

**Q: What's the best possible time complexity for a comparison-based sorting
algorithm? Can you beat it?**

`O(n log n)` is the proven lower bound for any comparison-based sort — this is a
mathematical result (via decision-tree argument: with n elements there are n!
possible orderings, and each comparison can only halve the remaining possibilities,
so you need at least `log₂(n!) = O(n log n)` comparisons in the worst case). Merge
sort, heap sort, and quicksort's average case all hit this bound; you cannot do
better using only pairwise comparisons.

You *can* beat `O(n log n)` — getting to `O(n)` — with non-comparison sorts like
counting sort, radix sort, or bucket sort, but only under extra constraints on the
keys being sorted (e.g., bounded-range integers for counting sort, fixed-digit-count
integers for radix sort). These trade generality for speed: they work by exploiting
structure in the key values themselves rather than by comparing elements pairwise, so
the `O(n log n)` lower bound — which is specifically a bound on comparison sorts —
doesn't apply to them. (Covered in depth in Phase 10.)
