<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · linked lists ➡](../phase-3-linked-lists/NOTES.md)
<!-- /nav -->

# Phase 2 — Arrays & Strings: Notes

Arrays and strings are the single most common interview category. Almost every
"array problem" is really one of a handful of *patterns* wearing a different
costume — two pointers, sliding window, prefix sums, and the hash-map trick.
Learning to recognize *which* pattern a problem wants, from its phrasing, is
the actual skill being tested; the code itself is usually short once you know
the pattern. This phase's source file, `ArrayPatterns.java`, implements and
tests all four patterns below with `assert` statements (run with `java -ea`).

## 2.1 — Two Pointers

Two indices that walk through the array (or string) toward or alongside each
other, replacing what would otherwise be a nested loop (O(n²)) with a single
O(n) pass.

### Key Concepts

- **Converging pointers**: one pointer starts at index `0` (`lo`), the other
  at the last index (`hi`); they move toward each other and stop when
  `lo >= hi`. Requires the array to be **sorted**, or the problem to naturally
  compare the two ends (palindrome check, reversing, container-with-water).
- **Same-direction pointers (slow/fast)**: both start at `0`; one (`write`)
  only advances when it needs to record a result, the other (`read` or the
  loop variable) always advances. Used for in-place partitioning: dedupe a
  sorted array, move zeros to the end, Dutch-flag partitioning. This is also
  the basis of Floyd's cycle detection on linked lists (Phase 3).
- **Why sorted matters for converging pairs**: on a sorted array, if
  `a[lo] + a[hi]` is too small, the *only* way to increase the sum is to move
  `lo` up (moving `hi` down can only shrink it further, since the array is
  sorted). Symmetric logic applies if the sum is too big. Each comparison
  eliminates an entire row or column of the O(n²) pair-search space, which is
  exactly why the technique collapses an O(n²) brute force into O(n).
- **Space**: two-pointer techniques are almost always **O(1) extra space**
  because they operate on the original array in place — this is frequently
  what "can you do it without extra space?" is asking for.

### Worked Example — Two Sum on a Sorted Array

```java
static int[] twoSumSorted(int[] a, int target) {
    int lo = 0, hi = a.length - 1;
    while (lo < hi) {
        int sum = a[lo] + a[hi];
        if (sum == target) return new int[]{lo, hi};
        else if (sum < target) lo++;   // need bigger -> move left pointer up
        else hi--;                     // need smaller -> move right pointer down
    }
    return new int[]{-1, -1};
}

twoSumSorted(new int[]{2, 7, 11, 15}, 9);   // -> [0, 1]  (2 + 7 == 9)
twoSumSorted(new int[]{1, 3, 4, 5, 7}, 12); // -> [3, 4]  (5 + 7 == 12)
```

In this example: `lo` starts at `2` and `hi` at `15`. Their sum (`17`) is too
big, so `hi` moves down to `11`. `2 + 11 = 13`, still too big, `hi` moves to
`7`. Now `2 + 7 = 9` matches, and we return immediately. Total work is O(n)
because `lo` and `hi` together traverse the array once, never revisiting an
index — contrast with the brute-force nested loop, which is O(n²).

### Worked Example — Palindrome Check and In-Place Reverse

```java
static boolean isPalindrome(String s) {
    int lo = 0, hi = s.length() - 1;
    while (lo < hi) if (s.charAt(lo++) != s.charAt(hi--)) return false;
    return true;
}

static int[] reverseInPlace(int[] a) {
    int lo = 0, hi = a.length - 1;
    while (lo < hi) { int t = a[lo]; a[lo++] = a[hi]; a[hi--] = t; }
    return a;
}

isPalindrome("racecar");            // -> true
isPalindrome("hello");              // -> false
reverseInPlace(new int[]{1,2,3,4}); // -> [4, 3, 2, 1]
```

Both are the converging-pointer shape without a sortedness requirement — they
compare/swap symmetric positions from the outside in. `reverseInPlace` swaps
`a[lo]` and `a[hi]` and advances both pointers inward each iteration, so it
finishes in `n/2` swaps — O(n) time, O(1) space, and it mutates the input
array directly rather than allocating a new one.

### Worked Example — Same-Direction Pointers: Move Zeros

```java
static void moveZeroesToEnd(int[] a) {
    int write = 0;
    for (int read = 0; read < a.length; read++) {
        if (a[read] != 0) {
            int t = a[write]; a[write] = a[read]; a[read] = t;
            write++;
        }
    }
}

int[] a = {0, 1, 0, 3, 12};
moveZeroesToEnd(a);
// a -> [1, 3, 12, 0, 0]
```

`write` only advances when a non-zero is found, so it always points at the
next slot that should hold a non-zero value. `read` scans every element once.
Because every swap either places a non-zero in its correct compacted position
or swaps two zeros (a no-op in effect), relative order of the non-zero
elements is preserved — O(n) time, O(1) space, in place.

## 2.2 — Sliding Window

A contiguous sub-range `[start, end]` of the array or string that expands and
contracts while scanning, maintaining some property (a sum, a count, a set of
distinct characters). It converts "check every possible contiguous
subarray/substring" problems — naively O(n²) or worse — into **O(n)**, because
each element enters the window exactly once and leaves it at most once.

### Key Concepts

- **Fixed-size window (size `k`)**: compute the sum/state of the first window
  once, then slide one step at a time — add the element entering on the
  right, subtract the element leaving on the left. Each slide is O(1) instead
  of re-scanning the whole window, so the total is O(n) instead of O(n·k).
- **Variable-size window**: grow the right edge (`end`) to bring in more
  elements; when the window violates a constraint, shrink from the left
  (`start`) until it's valid again. The window's size at each valid state is
  a candidate answer (for "longest") or you record the first valid state (for
  "shortest").
- **Recognizing the pattern**: look for the phrase *contiguous subarray* or
  *substring* combined with *longest/shortest/max/min* under some constraint
  ("without repeating characters", "with sum ≥ target", "with at most k
  distinct characters"). That combination is the trigger.
- **Companion data structure**: variable windows are frequently paired with a
  `HashMap`/`HashSet` that tracks the frequency or last-seen position of each
  element currently inside the window, so membership/count checks stay O(1).

### Worked Example — Fixed Window: Max Sum of Any k Consecutive Elements

```java
static int maxSumWindow(int[] a, int k) {
    int sum = 0;
    for (int i = 0; i < k; i++) sum += a[i];        // first window
    int best = sum;
    for (int i = k; i < a.length; i++) {
        sum += a[i] - a[i - k];                      // O(1) slide, not re-sum
        best = Math.max(best, sum);
    }
    return best;
}

maxSumWindow(new int[]{2, 1, 5, 1, 3, 2}, 3);  // -> 9   (window [5,1,3])
```

In this example: the first window `[2,1,5]` sums to `8`. Sliding to
`[1,5,1]` adds `a[3]=1` and subtracts `a[0]=2` — no need to re-add the middle
elements. The best window found along the way is `[5,1,3]` summing to `9`.
This O(1)-per-slide trick is what keeps the whole scan at O(n) instead of the
naive O(n·k) of re-summing every window from scratch.

### Worked Example — Variable Window: Longest Substring Without Repeating Characters

```java
static int longestUniqueSubstring(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();
    int start = 0, best = 0;
    for (int end = 0; end < s.length(); end++) {
        char c = s.charAt(end);
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= start) {
            start = lastSeen.get(c) + 1;   // jump past the duplicate
        }
        lastSeen.put(c, end);
        best = Math.max(best, end - start + 1);
    }
    return best;
}

longestUniqueSubstring("abcabcbb"); // -> 3   ("abc")
longestUniqueSubstring("bbbbb");    // -> 1   ("b")
longestUniqueSubstring("pwwkew");   // -> 3   ("wke")
```

In this example: `end` scans forward. When it reaches the second `'b'` in
`"abcabcbb"` (index 4), `lastSeen` says `'b'` was already seen at index 1,
which is `>= start` (0), so `start` jumps to `2`. The window is never
re-scanned character by character to check for duplicates — the `HashMap`
answers "is this char already inside the window?" in O(1), so the whole
algorithm is O(n) with O(min(n, alphabet size)) extra space.

## 2.3 — Prefix Sums

Precompute a running-total array so that **any range sum becomes an O(1)
lookup** instead of an O(n) re-scan. This trades O(n) preprocessing time and
O(n) extra space for O(1) queries — worth it whenever there will be many
range-sum queries against the same array.

### Key Concepts

- **Definition**: `pre[i]` = sum of the first `i` elements of `a` (so
  `pre[0] = 0`, an empty prefix). Built with one O(n) pass:
  `pre[i+1] = pre[i] + a[i]`.
- **Range sum formula**: `sum(a[l..r] inclusive) = pre[r+1] - pre[l]`. The
  `+1` offset exists because `pre` is one element longer than `a`, which
  cleanly handles ranges starting at index `0` without a special case.
- **The powerful combo — prefix sum + hash map**: to count/find subarrays
  whose sum equals a target `k`, note that `sum(a[i..j-1]) = pre[j] - pre[i]`.
  So a subarray ending at `j` sums to `k` exactly when some earlier prefix
  `pre[i]` equals `pre[j] - k`. Walking once and asking a hash map "how many
  times have I seen the prefix value `running - k` so far?" turns an O(n²)
  brute force (checking every `(i, j)` pair) into a single O(n) pass.
- **Seeding the map with `{0: 1}`**: this represents the "empty prefix" and is
  what correctly counts subarrays that start at index `0` (there's no prefix
  *before* the array to subtract from).

### Worked Example — Precompute and Query Range Sums

```java
static int[] prefixSums(int[] a) {
    int[] pre = new int[a.length + 1];       // pre[i] = sum of first i elements
    for (int i = 0; i < a.length; i++) pre[i + 1] = pre[i] + a[i];
    return pre;
}

static int rangeSum(int[] prefix, int l, int r) {
    return prefix[r + 1] - prefix[l];
}

int[] pre = prefixSums(new int[]{3, 1, 4, 1, 5});
// pre -> [0, 3, 4, 8, 9, 14]
rangeSum(pre, 1, 3);   // -> 6   (a[1]+a[2]+a[3] = 1+4+1)
```

In this example: after O(n) preprocessing, `rangeSum(pre, 1, 3)` computes
`pre[4] - pre[1] = 9 - 3 = 6` in O(1) — no re-summing the middle elements.
If you had `q` range-sum queries against the same array, brute force costs
O(n·q); prefix sums cost O(n + q).

### Worked Example — Count Subarrays Summing to k (Prefix Sum + Hash Map)

```java
static int subarraysSummingTo(int[] a, int k) {
    Map<Integer, Integer> count = new HashMap<>();
    count.put(0, 1);                        // empty prefix
    int running = 0, result = 0;
    for (int x : a) {
        running += x;
        result += count.getOrDefault(running - k, 0);
        count.merge(running, 1, Integer::sum);
    }
    return result;
}

subarraysSummingTo(new int[]{1, 1, 1}, 2);  // -> 2   ([1,1] at [0,1] and [1,2])
subarraysSummingTo(new int[]{1, 2, 3}, 3);  // -> 2   ([1,2] and [3])
```

In this example, walking `{1,2,3}` with `k=3`: after `x=1`, `running=1`,
looks for `1-3=-2` (not seen), records `running=1`. After `x=2`, `running=3`,
looks for `3-3=0` (seen once, the seeded empty prefix) — that's the subarray
`[1,2]`, so `result=1`. After `x=3`, `running=6`, looks for `6-3=3` (seen once
from the previous step) — that's the subarray `[3]` alone, so `result=2`.
Each lookup and update is O(1) amortized, giving O(n) overall versus O(n²) for
checking every `(i, j)` pair directly.

## 2.4 — Kadane's Algorithm (Maximum Subarray Sum)

Finds the maximum-sum **contiguous** subarray in O(n) — a tiny, one-line
dynamic-programming recurrence, and most people's first real taste of DP
(Phase 11) even before they've heard the word.

### Key Concepts

- **Recurrence**: `bestEndingHere = max(x, bestEndingHere + x)` for each
  element `x` — "either start a fresh subarray at `x`, or extend the run that
  ended at the previous element." Track a separate running `best` (the
  overall maximum seen so far), since the best subarray doesn't have to end
  at the last element.
- **Why "start fresh or extend" is correct**: if `bestEndingHere` (the best
  sum of a subarray *ending exactly at the previous index*) is negative,
  carrying it forward can only hurt the next element — better to discard it
  and start over at `x`. This greedy local choice is what makes the O(n)
  single pass correct; it's provably optimal because a negative prefix never
  helps any future sum.
- **All-negative arrays**: Kadane's as written above still works — it returns
  the least-negative single element, because `bestEndingHere` always considers
  "just `x` alone" as an option via `max(x, ...)`.
- **Variant — maximum product subarray**: because multiplying by a negative
  number flips max and min, you must track *both* the running max **and**
  running min ending at each position, since a very negative running min
  times a new negative number can become the new max.

### Worked Example

```java
static int maxSubarray(int[] a) {
    int bestEndingHere = a[0], best = a[0];
    for (int i = 1; i < a.length; i++) {
        bestEndingHere = Math.max(a[i], bestEndingHere + a[i]);
        best = Math.max(best, bestEndingHere);
    }
    return best;
}

maxSubarray(new int[]{-2, 1, -3, 4, -1, 2, 1, -5, 4}); // -> 6   ([4,-1,2,1])
maxSubarray(new int[]{-1, -2, -3});                     // -> -1  (best single element)
```

In this example: at index 0, both trackers start at `-2`. At index 1
(`x=1`), `bestEndingHere = max(1, -2+1) = max(1,-1) = 1` — the algorithm
"restarts" here because carrying the `-2` would only shrink the sum. By index
3 (`x=4`), `bestEndingHere` has reset and grown to `4`; continuing through
`-1, 2, 1` accumulates to `6`, which becomes the final answer. The whole scan
is a single O(n) pass with O(1) extra space — no subarrays are materialized.

## 2.5 — Related Patterns Interviewers Expect

These aren't in `ArrayPatterns.java` but build directly on the four patterns
above and come up constantly in interviews.

### Merging Sorted Arrays / Merging Intervals

```java
// Merge two sorted arrays into one sorted array — two pointers walking both.
static int[] mergeSorted(int[] a, int[] b) {
    int[] out = new int[a.length + b.length];
    int i = 0, j = 0, k = 0;
    while (i < a.length && j < b.length) out[k++] = (a[i] <= b[j]) ? a[i++] : b[j++];
    while (i < a.length) out[k++] = a[i++];
    while (j < b.length) out[k++] = b[j++];
    return out;
}
```

Merging **intervals** is a different pattern: sort the intervals by start
time (O(n log n)), then sweep once, merging the current interval into the
previous one whenever `current.start <= previous.end`. The sort is what makes
a single linear sweep sufficient afterward — without sorting you'd need to
compare every pair.

### Group Anagrams

```java
static Map<String, List<String>> groupAnagrams(String[] words) {
    Map<String, List<String>> groups = new HashMap<>();
    for (String w : words) {
        char[] chars = w.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);              // canonical signature
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(w);
    }
    return groups;
}
```

Every anagram of a word shares the same sorted-character signature, so a
`HashMap` keyed by that signature groups them in O(n·k log k) time (`n` words,
average length `k`) — or O(n·k) if you use a fixed 26-count array as the key
instead of sorting, since lowercase-English counting sort is O(k).

### Container With Most Water / Trapping Rain Water

Both are converging two-pointer problems built on the same insight: the water
level (or container capacity) at any point is bounded by the **shorter** of
the two walls, so the shorter wall is always the one worth moving.

```java
static int maxWaterContainer(int[] height) {
    int lo = 0, hi = height.length - 1, best = 0;
    while (lo < hi) {
        int area = Math.min(height[lo], height[hi]) * (hi - lo);
        best = Math.max(best, area);
        if (height[lo] < height[hi]) lo++;   // shorter wall is the limiting factor
        else hi--;
    }
    return best;
}
```

Moving the *taller* wall can never increase the area (the shorter wall still
caps it, and the width only shrinks), so it's provably safe to always move
the shorter one — this is the classic "why move the smaller side?" reasoning
question. Trapping rain water is the same idea generalized to every bar
trapping water above it, usually solved with the same two-pointer sweep or a
prefix-max / suffix-max array (the amount of water above index `i` is
`min(maxLeft[i], maxRight[i]) - height[i]`).

### Find the Duplicate / Find the Missing Number

| Approach | Time | Space | Notes |
|---|---|---|---|
| Hash set (seen-before check) | O(n) | O(n) | Simplest; works for any input, not just `1..n` |
| Sum formula (`n(n+1)/2 - actualSum`) | O(n) | O(1) | Only for "missing number in `1..n`"; can overflow for large `n` |
| XOR trick | O(n) | O(1) | XOR all values 1..n with all array values; duplicates/pairs cancel |
| Cyclic sort / Floyd's cycle detection | O(n) | O(1) | Works when values are constrained to `1..n`; treats the array as a linked list via `a[i] -> a[a[i]]` |

Interviewers typically accept the hash-set answer first, then push toward
O(1) space — that's when the sum-formula, XOR, or cyclic-sort tricks become
the expected follow-up.

## General Array & String Notes

- **In-place algorithms** use O(1) extra space by overwriting the input array
  directly (reverse, dedupe, move-zeros, Dutch-flag partition) — this is
  usually what "can you optimize the space?" is asking for.
- **Strings are immutable in Java** (see Phase 1): every `+=` on a `String`
  inside a loop allocates a brand-new `String` object, making naive string
  building O(n²) in the total characters copied. Build results with a
  `StringBuilder` (amortized O(1) per append) or a `char[]` instead.
- **Sorting first** costs O(n log n) but often unlocks an O(n) two-pointer or
  greedy follow-up — worth it whenever the problem doesn't require the
  original order to be preserved (interval merging, three-sum, closest-pair
  problems).
- **Off-by-one errors** at window/range boundaries are the single most common
  bug source in this category — decide up front whether a range is inclusive
  or half-open (`[start, end)`) and apply it consistently; the half-open
  convention (see Phase 1) tends to avoid the most `+1`/`-1` mistakes.

## Time/Space Complexity Summary

| Pattern | Typical Time | Typical Space | Recognize by |
|---|---|---|---|
| Two pointers (converging) | O(n) | O(1) | Sorted array, or comparing both ends |
| Two pointers (same direction) | O(n) | O(1) | In-place partition/compaction |
| Sliding window (fixed k) | O(n) | O(1) (or O(k) for a window map) | "every window of size k" |
| Sliding window (variable) | O(n) | O(min(n, alphabet)) | "longest/shortest contiguous ... under a constraint" |
| Prefix sums | O(n) preprocess, O(1) per query | O(n) | Many range-sum queries |
| Prefix sum + hash map | O(n) | O(n) | "count/find subarrays summing to k" |
| Kadane's algorithm | O(n) | O(1) | "maximum sum contiguous subarray" |
| Brute force (naive nested loop) | O(n²) or worse | O(1) | The fallback if no pattern is spotted |

## Why It's Useful

These four patterns aren't academic — they're the actual tools used to keep
real systems fast: sliding windows power rate limiters and network
congestion-control buffers; prefix sums back range-query features in
analytics dashboards and spreadsheet formulas; two-pointer partitioning is
literally how `Arrays.sort`-adjacent quicksort partitioning and the Dutch
national flag problem (three-way partitioning, used in `Collections.sort`'s
dual-pivot quicksort for primitives) work under the hood. Recognizing "this is
a sliding-window problem" instantly, from the phrasing alone, is one of the
highest-leverage pattern-recognition skills for both interviews and
day-to-day array/string manipulation code.

## Key Takeaways

- **Two pointers** turn O(n²) pair/partition problems into O(n) — converging
  on sorted data, same-direction for in-place compaction.
- **Sliding window** turns O(n²) "every contiguous subarray/substring" scans
  into O(n) — fixed-size windows slide in O(1); variable windows grow/shrink
  with a companion `HashMap`/`HashSet`.
- **Prefix sums** turn repeated O(n) range-sum queries into O(1) each after
  one O(n) precompute; combined with a hash map, they turn "count subarrays
  summing to k" from O(n²) into O(n).
- **Kadane's algorithm** is a one-line DP (`max(x, running + x)`) that solves
  maximum-subarray-sum in O(n) and generalizes to many "best contiguous run"
  problems.
- Always ask: is the array sorted (or can I sort it)? Is this asking about a
  *contiguous* range? Do I need O(1) space? The answers point straight at
  which pattern to reach for.
