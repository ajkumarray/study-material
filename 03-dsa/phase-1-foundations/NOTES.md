<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · arrays ➡](../phase-2-arrays/NOTES.md)
<!-- /nav -->

# Phase 1 — Complexity & Foundations: Notes

Everything in this track (arrays, lists, trees, graphs, DP, greedy...) is ultimately a
conversation about **how cost grows**. Before touching a single data structure, you
need a precise, reusable vocabulary for describing that growth — that's what this
phase builds. All examples below are grounded in `ComplexityDemo.java` in this
directory, which *measures* the growth rates instead of just asserting them.

## 1. Big-O Notation

**Big-O** is a mathematical way of describing how the running time (or memory use) of
an algorithm grows as the size of its input, `n`, grows — while ignoring constant
factors and lower-order terms. It answers the only question that matters once data
gets large: *"if I 10x my input, does this still work?"*

### Key Concepts

- **Upper bound**: Big-O describes a ceiling on cost. `O(n)` means "grows **no faster
  than** linearly" — it's technically an upper bound, though in casual interview usage
  people say "Big-O of the algorithm" to mean its *tight* worst-case bound.
- **Worst case by convention**: unless stated otherwise, "the Big-O of this algorithm"
  means the worst-case input. A linear search is O(n) worst case even though it's O(1)
  if the target happens to be first.
- **Growth rate, not a stopwatch**: Big-O says nothing about actual seconds on actual
  hardware. Two O(n) algorithms can differ by a 100x constant factor and Big-O treats
  them the same — this is a deliberate simplification, not a flaw (see the "practical
  caveats" section below for when this bites you).
- **Asymptotic**: Big-O is a statement about behavior as `n → ∞`. For small n, a
  "worse" complexity class can still run faster in wall-clock time.

### The Three Notations: O, Ω, Θ

| Notation | Name | Meaning | Interview usage |
|---|---|---|---|
| **O(f(n))** | Big-O | Upper bound — cost grows *no faster than* f(n) | "What's the time complexity?" almost always means this, worst case |
| **Ω(f(n))** | Big-Omega | Lower bound — cost grows *at least as fast as* f(n) | Rarely asked directly; used to say "you can't do better than Ω(n log n) for comparison sorts" |
| **Θ(f(n))** | Big-Theta | Tight bound — upper and lower bound match | The mathematically precise term when best/worst case are the same, e.g. summing an array is always Θ(n) |

In practice, interviewers say "Big-O" but mean "the tight worst-case bound" — that's
fine to assume unless the question explicitly asks about best case.

### Common Complexity Classes, Fastest to Slowest

| Complexity | Name | Example | Doubling n does... |
|---|---|---|---|
| O(1) | Constant | Array index `a[i]`, HashMap `get` | Nothing — cost stays flat |
| O(log n) | Logarithmic | Binary search, balanced-tree operations | Adds **one** more step |
| O(n) | Linear | Single pass/scan, sum of an array | Doubles the cost |
| O(n log n) | Linearithmic | Merge sort, heap sort, quicksort (avg) | Slightly more than doubles |
| O(n²) | Quadratic | Nested loops over the same array (naive pair check) | **Quadruples** the cost |
| O(n³) | Cubic | Triple-nested loops (naive matrix multiply) | 8x's the cost |
| O(2ⁿ) | Exponential | Naive recursive subsets, unmemoized Fibonacci | Squares the cost (catastrophic) |
| O(n!) | Factorial | Brute-force permutations, traveling salesman brute force | Explodes — unusable past n≈12 |

### Worked Example: Measuring Growth, Not Guessing It

`ComplexityDemo.java` solves the same problem — "does this array contain a
duplicate?" — three ways and times each one across growing input sizes:

```java
// O(n^2): nested loop compares every pair.
static boolean hasDuplicateQuadratic(int[] a) {
    for (int i = 0; i < a.length; i++)
        for (int j = i + 1; j < a.length; j++)
            if (a[i] == a[j]) return true;
    return false;
}

// O(n) time, O(n) space: a hash set remembers what we've seen.
static boolean hasDuplicateLinear(int[] a) {
    Set<Integer> seen = new HashSet<>();
    for (int x : a) if (!seen.add(x)) return true;   // add returns false if present
    return false;
}
```

Running both across `n = 1_000, 2_000, 4_000, 8_000, 16_000` (all-distinct, shuffled
arrays so there's no lucky early exit) produces output shaped like this:

```
=== growth rates (time in ms) ===
n        O(n)       O(n^2)       O(n) set
1000     ...        ...          ...
2000     ...        ...          ...
4000     ...        ...          ...
8000     ...        ...          ...
16000    ...        ...          ...
```

The exact microsecond numbers depend on the machine, but the **pattern** is
deterministic and is the entire point of the demo: the `O(n^2)` column roughly
**quadruples** every time n doubles, while both `O(n)` columns roughly **double**.
That ratio — 4x vs 2x — *is* what "quadratic" and "linear" mean operationally. In this
example: the quadratic version does `n*(n-1)/2` comparisons (every pair), so doubling
n roughly quadruples the comparison count; the linear version does one hash-set
insert per element, so doubling n roughly doubles the work.

### Analyzing Code Without Running It

You won't always get to measure — most interviews want you to read code and state its
complexity. The rules:

- **Sequential statements add, then you keep the dominant term.** A loop that's O(n)
  followed by a separate loop that's O(n²) is `O(n) + O(n²)`, which simplifies to
  `O(n²)` — the lower-order term is dropped because it becomes irrelevant as n grows.
- **Nested loops multiply.** Two nested loops both running to n is `O(n) * O(n) =
  O(n²)`. If the outer loop runs to n and the inner to a *different* bound m, it's
  `O(n*m)`, not `O(n²)`.
- **Halving each step is O(log n).** A loop that divides the remaining range by 2 each
  iteration (binary search) takes about `log₂(n)` iterations to hit the base case.
- **O(n) work repeated log n times is O(n log n).** This is exactly how merge sort
  works: `log n` levels of merging, each level doing O(n) total work across it.
- **Drop constants and lower-order terms.** `O(2n + 5)` simplifies to `O(n)`; `O(n² +
  n)` simplifies to `O(n²)`. Constants matter for real performance but not for the
  complexity *class*.

```java
static int log2Steps(long n) {
    int steps = 0;
    while (n > 1) { n /= 2; steps++; }   // halves n each iteration -> O(log n)
    return steps + 1;
}
```

Calling `log2Steps` with `n = 16`, `1_000`, `1_000_000`, `1_000_000_000` produces step
counts of roughly 5, 10, 20, 30 — each **1000x** increase in `n` costs only about
**10 more** iterations. This is why binary search and balanced trees stay fast even
at huge scale: the input can grow enormously while the operation count barely moves.

### Why It's Useful

Big-O is the shared language for discussing "will this scale" in a code review, a
design doc, or an interview. It's also the fastest way to spot a bad solution before
writing any code: if someone proposes checking every pair of a million records
(O(n²) = 10¹² comparisons), you know instantly that's infeasible without running it.

### Summary — Key Takeaways

- Big-O = worst-case upper bound on growth, ignoring constants; Ω = lower bound; Θ =
  tight bound (both match). Interviewers say "Big-O" but usually mean the tight,
  worst-case bound.
- Memorize the ordering: `O(1) < O(log n) < O(n) < O(n log n) < O(n²) < O(2ⁿ) < O(n!)`.
- Sequential code **adds** complexities (keep the dominant term); nested loops
  **multiply**; halving each step is `O(log n)`.
- Always be able to name a concrete example for each class (hash lookup, binary
  search, single scan, merge sort, nested loops, naive Fibonacci, permutations).

## 2. Space Complexity

**Space complexity** measures the *extra* memory an algorithm needs beyond its input
— auxiliary variables, data structures you build, and (for recursive code) the call
stack. It's analyzed with the same O/Ω/Θ vocabulary as time.

### Key Concepts

- **Auxiliary space vs total space**: interviewers almost always mean *auxiliary*
  space — memory beyond what was already given to you as input. An in-place array
  reversal is O(1) auxiliary space even though the array itself is O(n).
- **Data-structure cost**: a `HashSet`/`HashMap` you build to remember "have I seen
  this before" costs O(n) extra space in the worst case (every element distinct).
- **Recursive call stack counts as space**: every pending recursive call holds a stack
  frame until it returns; this is covered in depth in the recursion section below.

### Worked Example

```java
static boolean hasDuplicateQuadratic(int[] a) {   // O(1) extra space — no new structure
    for (int i = 0; i < a.length; i++)
        for (int j = i + 1; j < a.length; j++)
            if (a[i] == a[j]) return true;
    return false;
}

static boolean hasDuplicateLinear(int[] a) {       // O(n) extra space — the HashSet
    Set<Integer> seen = new HashSet<>();
    for (int x : a) if (!seen.add(x)) return true;
    return false;
}
```

In this example: `hasDuplicateQuadratic` uses only a couple of loop counters — its
memory footprint doesn't grow with `n`, so it's O(1) space. `hasDuplicateLinear`
builds a `HashSet` that, in the worst case (no duplicates found until the very end),
holds all `n` elements — O(n) space. Both solve the same problem; they sit at
opposite ends of the time-space tradeoff below.

### Why It's Useful

Space complexity matters wherever memory is constrained — embedded systems, very
large datasets that must fit in RAM, or systems with many concurrent requests each
holding their own working set. It's also a common interview follow-up: "you solved it
in O(n) time — what's the space complexity, and can you do better?"

### Summary — Key Takeaways

- Space complexity = extra memory beyond the input, analyzed with the same notation
  as time complexity.
- A new `HashMap`/`HashSet`/array sized proportionally to input is O(n) space; a
  handful of scalar variables is O(1) space regardless of input size.
- Recursive calls consume stack space proportional to recursion depth — covered next.

## 3. The Time–Space Tradeoff

**The time–space tradeoff** is the recurring interview pattern where you can lower
time complexity by spending more memory, or lower memory by spending more time — and
picking the right data structure is usually *how* you make that trade.

### Key Concepts

- **Same problem, different cost profile**: `hasDuplicateQuadratic` (O(n²) time, O(1)
  space) and `hasDuplicateLinear` (O(n) time, O(n) space) solve the identical problem.
  Neither is "more correct" — they trade one resource for the other.
- **The instinct to practice**: "can a hash map turn this O(n²) into O(n)?" is one of
  the single most useful reflexes in interviews. Two-sum, "first non-repeating
  character," and duplicate detection are all the same shape: nested-loop brute force
  vs. a hash structure that remembers what's been seen.
- **It generalizes beyond hashing**: memoization in dynamic programming (Phase 11) is
  the same idea — spend O(n) or O(n²) extra memory on a cache to avoid recomputing
  overlapping subproblems, turning exponential time into polynomial time.

### Comparison Table

| Approach | Time | Space | When to prefer it |
|---|---|---|---|
| Nested-loop pair comparison | O(n²) | O(1) | Memory is extremely constrained and n is small |
| Hash set / hash map | O(n) | O(n) | The default choice — memory is cheap, time usually isn't |
| Sort first, then scan for adjacency | O(n log n) | O(1) extra (in-place sort) or O(n) if a copy is kept | You need O(1) extra space but can tolerate O(n log n) time, or you need the data sorted anyway |

### Why It's Useful

This is the single most common "can you optimize this?" follow-up in coding
interviews. Recognizing "I'm doing repeated linear scans inside a loop — a hash map
turns each lookup from O(n) to O(1)" is what separates a brute-force answer from an
optimal one, and it applies far beyond duplicate detection: caching, memoization, and
database indexing are all instances of the same tradeoff.

### Summary — Key Takeaways

- The same problem almost always has a low-time/high-space solution and a
  low-space/high-time solution; know both and be able to name the tradeoff explicitly.
- Default interview reflex: "nested loop with repeated lookups" → "can a hash
  map/hash set eliminate the inner loop?"
- Sorting first is a common O(1)-extra-space alternative to hashing when O(n log n)
  time is acceptable.

## 4. Amortized Complexity

**Amortized analysis** is the average cost per operation across a *sequence* of
operations, used when most operations are cheap but occasional ones are expensive —
and the expensive ones happen rarely enough that they don't change the per-operation
average.

### Key Concepts

- **Not the same as average case**: amortized analysis is about worst-case behavior
  *summed over a sequence*, not about probabilistic average-case input. It's a
  guarantee, not a hope.
- **Classic example: dynamic array growth.** A resizable array (Java's `ArrayList`)
  starts with some capacity; when it fills up, it allocates a new backing array —
  typically double the size — and copies every element over. That copy is O(n), but
  it only happens `O(log n)` times over n insertions, and the cost of each doubling is
  "paid for" by the many O(1) appends since the last resize.
- **HashMap resizing follows the same pattern**: `HashMap.put` is described as
  "amortized O(1)" for the identical reason — occasional O(n) rehashing when the load
  factor is exceeded, averaged across all puts.

### Worked Example

`ComplexityDemo.java` simulates the doubling strategy directly:

```java
int resizes = 0, capacity = 1;
for (int i = 0; i < 1_000_000; i++) {
    if (i == capacity) { capacity *= 2; resizes++; }   // simulate doubling
}
System.out.printf("  1,000,000 adds triggered only %d resizes -> amortized O(1)%n", resizes);
```

Output:
```
1,000,000 adds triggered only 20 resizes -> amortized O(1)
```

In this example: capacity doubles every time it's exceeded (1, 2, 4, 8, 16, ...), so
after 1,000,000 inserts, capacity has only doubled about `log₂(1,000,000) ≈ 20` times.
Nineteen out of every twenty operations are O(1); the twentieth-ish is an O(n) copy —
but spread across a million operations, the *average* cost per operation is still
O(1). That's the amortized guarantee.

### Why It's Useful

Amortized O(1) is why you're told "just use an `ArrayList`, appends are effectively
free" instead of "appends are sometimes O(n), be careful." It's also why interviewers
accept "amortized O(1)" as the correct answer for `ArrayList.add` and `HashMap.put`
rather than marking you down for the occasional O(n) resize — understanding *why*
that occasional cost doesn't dominate is the actual signal they're checking for.

### Summary — Key Takeaways

- Amortized cost = total cost of a sequence of operations, divided by the number of
  operations — a worst-case guarantee over time, not an average-case hope.
- `ArrayList.add` and `HashMap.put` are both amortized O(1): rare O(n) resizes are
  "paid for" by many surrounding O(1) operations.
- Doubling capacity (rather than growing by a fixed amount) is what makes the resize
  count logarithmic instead of linear — growing by a constant increment instead would
  make append true O(n) amortized, not O(1).

## 5. Recursion Cost: Time and Space

**Recursive functions** have a cost profile that's easy to get wrong if you only
count "how many times does this call itself" — you also have to account for the call
stack that each pending call occupies.

### Key Concepts

- **Time = (number of calls) × (work per call, excluding recursive calls).** Count
  every invocation of the function across the whole call tree, then multiply by the
  non-recursive work each one does.
- **Space = maximum recursion depth × frame size**, *not* the total number of calls.
  Only the calls currently "pending" (waiting on a deeper call to return) occupy stack
  frames simultaneously — once a call returns, its frame is popped.
- **A branching recursion tree can have exponential calls but only linear depth.** A
  function that calls itself twice per invocation (like naive Fibonacci) makes O(2ⁿ)
  total calls, but the deepest single chain of pending calls is only O(n) — so time is
  O(2ⁿ) while space is O(n).
- **Too much depth throws `StackOverflowError`** in Java — each frame consumes real
  memory on a bounded stack, so unbounded or very deep recursion (e.g. recursing
  n = 1,000,000 times with no tail-call optimization, which the JVM doesn't perform)
  can crash rather than just run slowly.
- **Recurrences describe recursive time complexity formally.** A divide-and-conquer
  function that splits work in half and recombines, like merge sort, satisfies
  `T(n) = 2T(n/2) + O(n)` (two half-size recursive calls, plus O(n) work to merge
  results) — which solves to `O(n log n)` by the Master Theorem.

### Worked Example: Depth vs. Call Count

```java
// Linear recursion: n calls, n deep. Time O(n), stack space O(n).
static long sumTo(int n) {
    if (n == 0) return 0;
    return n + sumTo(n - 1);
}

// Branching recursion: ~2^n calls, but only n deep at any moment.
// Time O(2^n), stack space O(n) — NOT O(2^n).
static long fibNaive(int n) {
    if (n <= 1) return n;
    return fibNaive(n - 1) + fibNaive(n - 2);
}
```

In this example: `sumTo(5)` makes exactly 6 calls and the stack is 6 frames deep right
before the base case — calls and depth match because it's linear recursion.
`fibNaive(5)` makes far more calls (the call tree has 2^5-ish nodes), but at any
single moment the stack only holds the current path from root to leaf, which is at
most 5 frames deep — so its call *count* is exponential but its stack *depth*, and
therefore its space complexity, stays linear. This distinction — "how many calls
total" vs. "how many calls pending at once" — is exactly time complexity vs. space
complexity for recursion, and it's precisely what makes memoization (Phase 11) work:
caching by argument value cuts the exponential call count down to linear without
changing the linear stack depth.

### Why It's Useful

Recursion cost analysis is the foundation for reasoning about divide-and-conquer
algorithms (merge sort, quick sort, binary search on recursive form) and for spotting
when naive recursion needs memoization (Fibonacci, many DP problems in Phase 11) —
recognizing "this recurses exponentially because it's recomputing the same
subproblems" is the entire insight behind top-down dynamic programming.

### Summary — Key Takeaways

- Recursive time = number of calls × work per call; recursive space = **maximum
  simultaneous depth**, not total call count.
- Linear recursion (calls itself once) has matching time and depth, both O(n).
  Branching recursion (calls itself twice, like naive Fibonacci) has exponential time
  but only linear depth/space.
- Divide-and-conquer recurrences like `T(n) = 2T(n/2) + O(n)` solve to `O(n log n)` —
  this is the formal justification for merge sort's complexity.
- Excessive recursion depth throws `StackOverflowError` in Java; there's no automatic
  tail-call optimization to rely on.

## 6. Practical Caveats: When Big-O Isn't the Whole Story

Big-O is the *first* filter for comparing algorithms, not the only one. Two
algorithms in the same complexity class — or even where one is "worse" — can have
very different real-world performance.

### Key Concepts

- **Constants matter at realistic scale.** `O(n)` with a huge constant factor (say,
  100 operations per element) can be slower than `O(n log n)` with a tiny constant,
  for all n you'll actually encounter. Big-O only guarantees which one wins
  *eventually*, not at n = 1,000.
- **Cache locality favors contiguous memory.** An array (contiguous in memory) is
  typically faster to iterate than a linked list of equal Big-O complexity, because
  CPU cache lines fetch nearby memory together and array access pattern-matches that;
  a linked list's nodes can be scattered anywhere in memory, causing a cache miss per
  node (see Phase 3, `ArrayList` vs `LinkedList`).
- **Best/worst/average case can differ wildly.** Quicksort is O(n log n) average but
  O(n²) worst case (already-sorted input with a naive pivot choice); interviewers
  expect you to name both, not just the optimistic one.

### Why It's Useful

Big-O tells you which algorithm wins at scale; it doesn't tell you which one to
actually ship for your real data size. Knowing when to reach past Big-O — "this is
technically O(n²) but n never exceeds 50 here, so the simpler code is fine" — is a
mark of engineering judgment interviewers listen for in senior-level answers.

### Summary — Key Takeaways

- A lower Big-O class is **not** automatically faster for realistic, non-asymptotic n
  — constants and memory-access patterns matter in practice.
  Contiguous data (arrays) beats scattered data (linked lists) of equal Big-O due to
  cache locality.
- Always be ready to state best/average/worst case separately when they differ
  (quicksort is the textbook example).
- Comparison-based sorting has a proven lower bound of `Ω(n log n)` — you cannot sort
  by comparisons alone faster than that; non-comparison sorts (counting/radix/bucket,
  Phase 10) can beat it only under constraints on the key values.
