<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · linked lists ➡](../phase-3-linked-lists/NOTES.md)
<!-- /nav -->

# Phase 2 — Arrays & Strings: Interview Q&A + Problems

⭐ = asked constantly. These are *problems*, not trivia — practice narrating
the pattern you'd pick and *why*, out loud, before writing code. Interviewers
weight the reasoning at least as heavily as the final syntax.

**Q: Two Sum — return indices of two numbers adding to a target.** ⭐⭐

There are two standard solutions, and knowing both — plus when each applies —
is the actual point of the question. If the array is **unsorted**, use a
single pass with a `HashMap<Integer, Integer>` mapping `value -> index`: for
each `x`, check whether `target - x` has already been seen. If it has, return
the stored index and the current index. This is O(n) time and O(n) space,
and it only needs one pass because you check for the complement *before*
inserting the current value (so you never match an element with itself).

```java
static int[] twoSumUnsorted(int[] a, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < a.length; i++) {
        int need = target - a[i];
        if (seen.containsKey(need)) return new int[]{seen.get(need), i};
        seen.put(a[i], i);
    }
    return new int[]{-1, -1};
}
```

If the array is **sorted** (or you're allowed to sort it and only need the
*values*, not original indices), converging two pointers do it in O(n) time
and O(1) extra space, as shown in `twoSumSorted` in `ArrayPatterns.java`.
The follow-up interviewers usually ask: "what if you need indices into the
*original* unsorted array but want O(1) space?" — the honest answer is you
generally can't have both; sorting destroys the original index mapping unless
you carry `(value, originalIndex)` pairs through the sort, which then costs
O(n) space for the pairs anyway.

*Follow-up: what if there could be multiple valid pairs, or you need all of
them?* Keep going after the first match instead of returning immediately;
with the hash-map approach you'd need to store a list of indices per value to
handle duplicate values correctly.

**Q: Longest substring without repeating characters.** ⭐⭐

Variable-size sliding window with a `HashMap<Character, Integer>` recording
the last-seen index of each character. Expand `end` one character at a time;
if the character at `end` was already seen *inside the current window* (its
last-seen index is `>= start`), jump `start` to just past that previous
occurrence rather than incrementing it one step at a time — this is what
keeps the algorithm O(n) instead of O(n²), since `start` never revisits
positions it has already passed.

```java
static int longestUniqueSubstring(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();
    int start = 0, best = 0;
    for (int end = 0; end < s.length(); end++) {
        char c = s.charAt(end);
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= start) {
            start = lastSeen.get(c) + 1;
        }
        lastSeen.put(c, end);
        best = Math.max(best, end - start + 1);
    }
    return best;
}
```

For `"pwwkew"`, the answer is `3` (`"wke"`), not `"pwke"`, because once the
second `'w'` is hit at index 2, `start` jumps to index 2 as well — the window
can never re-include the first `'w'`.

*Follow-up: what would this return for `"abba"`, and why?* Walk it: at
`end=2` (`'b'`), `lastSeen.get('b')=1 >= start(0)`, so `start` jumps to `2`.
At `end=3` (`'a'`), `lastSeen.get('a')=0`, but `0 < start(2)` — that `'a'` is
*outside* the current window, so it must **not** trigger a jump; this is
exactly why the code checks `lastSeen.get(c) >= start` and not just
`lastSeen.containsKey(c)`. Getting that guard wrong is the most common bug in
this problem.

**Q: Maximum subarray sum (contiguous).** ⭐⭐

Kadane's algorithm: maintain `bestEndingHere = max(x, bestEndingHere + x)` as
you scan, and track the global `best` separately since the optimal subarray
might not end at the last element. It's O(n) time, O(1) space, and correct
because a negative `bestEndingHere` can never help extend a future sum — so
discarding it and restarting at the current element is always at least as
good.

*Follow-up: return the actual subarray, not just the sum.* Track the start
index of the current run; whenever you "restart" (`x > bestEndingHere + x`),
update a `tempStart = i`; whenever `best` updates, snapshot
`(resultStart, resultEnd) = (tempStart, i)`.

*Follow-up: maximum-**product** subarray instead of sum — what changes?*
Multiplying by a negative number flips the ordering, so a very negative
running product can become the maximum with one more negative multiplier.
You must track **both** the running max *and* running min ending at each
position (`newMax = max(x, x*oldMax, x*oldMin)`, and symmetrically for min),
otherwise a case like `[-2, 3, -4]` (max product `24`, from all three
elements) will be missed if you only ever track the max.

**Q: When do you reach for two pointers vs. a sliding window vs. a hash
map?** ⭐

**Two pointers**: the input is sorted (or you're comparing/combining values
from both ends), and you want O(1) space — pair-sum, palindrome checks,
in-place partitioning. **Sliding window**: the problem asks about a
*contiguous* subarray or substring and a longest/shortest/max/min value under
some constraint — the window's size or contents change as you scan.
**Hash map**: you need O(1) lookups, counts, or "have I seen this before"
membership checks that don't depend on order or contiguity — grouping,
deduplication, or (combined with prefix sums) counting subarrays with a
target sum. In practice the trigger words in the problem statement
("sorted", "contiguous", "count pairs/subarrays") are usually enough to pick
the right tool before you've even finished reading the prompt.

**Q: Count subarrays with sum = k.** ⭐

Prefix sum + hash map: keep a running sum as you scan, and a
`HashMap<Integer, Integer>` of how many times each prefix-sum value has been
seen so far (seeded with `{0: 1}` for the "empty prefix" before the array
starts). At each step, `running - k` tells you the prefix value that, if it
existed earlier, would mean the subarray between that point and now sums to
exactly `k` — so add `count.getOrDefault(running - k, 0)` to the result, then
record the current `running` sum. This is O(n) time and O(n) space, versus
O(n²) for checking every `(i, j)` pair directly, or O(n²) even with prefix
sums precomputed but no hash map (since you'd still scan all pairs).

*Follow-up: does this work if the array has negative numbers?* Yes — unlike a
sliding-window approach (which relies on the window sum only growing as you
expand, true for non-negative values), the prefix-sum + hash-map approach
makes no assumption about sign, since it's just tracking exact totals, not
monotonic growth. This is the key reason interviewers prefer this problem
over sliding window once negatives are allowed.

**Q: Move all zeros to the end, keeping the relative order of non-zero
elements, in place.**

Same-direction two pointers: maintain a `write` index. Scan with a `read`
index across the whole array; whenever `a[read] != 0`, swap it into
`a[write]` and increment `write`. By the end, all non-zero elements have been
compacted to the front in their original relative order, and everything from
`write` onward is zero. O(n) time, O(1) space, single pass.

*Follow-up: what if you can't use swaps — you can only overwrite?* Do it in
two passes: first pass copies every non-zero element forward into
`a[write++]`; second pass fills the remaining tail (`write` to the end) with
zeros. Still O(n) time, O(1) space, but avoids the (harmless here, but
sometimes unwanted) extra writes that a swap-based single pass performs on
zero-to-zero swaps.

**Q: Is a string a palindrome? (ignoring case and non-alphanumeric
characters)**

Converging two pointers from both ends, skipping over non-alphanumeric
characters as you go, and comparing lowercased characters at each valid pair
of positions. O(n) time, O(1) extra space (aside from any case-folding, which
can be done character-by-character without allocating a new string).

*Follow-up: how would you check if a string can be **rearranged** into a
palindrome, rather than already being one?* Different pattern entirely — use
a frequency count (array or `HashMap`) of each character; a rearrangement is
possible if at most one character has an odd count (the potential middle
character of an odd-length palindrome). That's a hashing/counting problem
(Phase 5), not a two-pointer one.

**Q: Merge two sorted arrays / merge overlapping intervals.**

These sound similar but use different patterns. **Merging two sorted
arrays** is straightforward two pointers, one per array, always advancing
whichever pointer currently points at the smaller head element — O(n+m) time
in the combined length. **Merging overlapping intervals** requires sorting
the intervals by start time first (O(n log n), since intervals arrive in
arbitrary order), then a single linear sweep: keep a "current merged
interval" and extend its end whenever the next interval's start is
`<= currentEnd`; otherwise close out the current interval and start a new
one. The sort is what makes the one-pass sweep sufficient — without it you'd
have to compare every pair.

**Q: Group anagrams.**

Hash map keyed by a canonical signature of each word — either its sorted
characters (`Arrays.sort` the `char[]`, then rebuild a `String` as the key)
or, for lowercase-English-only input, a fixed 26-length character-count array
converted to a key (e.g. a comma-joined string or a packed encoding). Every
member of an anagram group produces the same key, so a single pass groups
them into `Map<String, List<String>>`. Time is O(n·k log k) with the sort-key
approach (`n` words, average length `k`), or O(n·k) with the count-array
approach, since counting is linear in the word length while sorting is
`k log k`.

*Follow-up: which approach is better for very long words?* The count-array
key, since it avoids the `log k` sorting factor — but it only works cleanly
for a small, known alphabet (e.g., lowercase English letters); for arbitrary
Unicode you'd fall back to sorting or a more general frequency map.

**Q: Container with most water / trapping rain water.** ⭐

Both are converging two-pointer problems. For "container with most water,"
the area between two walls is `min(height[lo], height[hi]) * (hi - lo)` — it
is always capped by the **shorter** wall. So at each step, move the pointer
at the shorter wall inward: moving the taller wall can only shrink the width
without ever raising the cap (still bounded by the same short wall or an even
shorter one), so it can never improve the area — meaning it's always safe,
and never wrong, to move the shorter side. O(n) time, O(1) space.

"Trapping rain water" extends this to every bar: the water trapped above
index `i` is `min(maxHeightToTheLeft, maxHeightToTheRight) - height[i]`
(floored at 0). This can be solved with two pointers tracking running
left-max/right-max as you converge, or more simply with two precomputed
prefix-max / suffix-max arrays and a single O(n) pass to sum the trapped
water — the array version is O(n) time and O(n) space; the two-pointer
version is O(n) time and O(1) space.

*Follow-up: prove the "move the shorter wall" rule is safe.* If you move the
taller wall instead, the width strictly decreases, and the area is still
bounded by `min(oldShorter, newWall)` — which is at most the old shorter
height. So the new area can be no larger than what moving the shorter wall
could have found; you're only foreclosing better options, never gaining any.

**Q: Find the duplicate number / find the missing number (values constrained
to `1..n`).**

Interviewers usually want you to walk through a spectrum of solutions from
most-obvious to most-optimal:

| Approach | Time | Space |
|---|---|---|
| Hash set — track seen values, flag the repeat/gap | O(n) | O(n) |
| Sum formula — `expectedSum - actualSum` (missing) or `actualSum - expectedSum` (duplicate, if exactly one extra) | O(n) | O(1) |
| XOR trick — XOR all values `1..n` with all array values; unmatched pairs cancel to reveal the answer | O(n) | O(1) |
| Cyclic sort / Floyd's cycle detection — treat the array as a function `i -> a[i]` and detect the cycle | O(n) | O(1) |

The hash-set answer is the safe first answer; the expected follow-up is
"can you do it in O(1) space?" — which is where the sum/XOR/cyclic-sort
tricks come in. The sum-formula trick has a real caveat worth mentioning
unprompted: for large `n`, `n*(n+1)/2` can overflow a 32-bit `int` in Java,
so production code should accumulate in a `long`.

*Follow-up: what if there could be multiple duplicates, or values aren't
constrained to `1..n`?* The sum/XOR/cyclic-sort tricks all rely on the
`1..n` constraint and break down with arbitrary values or multiple
duplicates — fall back to the hash-set approach (or sort the array first and
scan for adjacent equal values, O(n log n) time but O(1) extra space beyond
the sort).

**Q: Why is naive string concatenation in a loop a performance problem in
Java, and how do you fix it?**

`String` is immutable in Java, so every `s += c` inside a loop allocates a
brand-new `String` object and copies all previous characters into it. Over
`n` iterations, that's `1 + 2 + ... + n` characters copied, which is O(n²)
total work even though it looks like a simple O(n) loop. The fix is
`StringBuilder`, whose `append` is backed by a mutable, amortized-O(1)-growth
character buffer — building an n-character string with `StringBuilder` is
O(n) total.

```java
// O(n^2) — reallocates and copies on every iteration
String bad = "";
for (char c : chars) bad += c;

// O(n) — mutable buffer, amortized O(1) appends
StringBuilder sb = new StringBuilder();
for (char c : chars) sb.append(c);
String good = sb.toString();
```

*Follow-up: when would `+=` on strings actually be fine?* For a small, fixed
number of concatenations (not inside a loop over the input size), the
compiler often optimizes consecutive `+` operations into a single
`StringBuilder` chain anyway, so the concern is specifically about
concatenation *inside a loop whose iteration count scales with input size*.
