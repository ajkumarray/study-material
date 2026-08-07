<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · linked lists ➡](../phase-3-linked-lists/NOTES.md)
<!-- /nav -->

# Phase 2 — Arrays & Strings: Interview Q&A + Problems

⭐ = asked constantly. These are *problems*, not trivia — practice narrating the pattern you'd pick.

**Q: Two Sum — return indices of two numbers adding to target.** ⭐⭐
Unsorted: one pass with a hash map (`value → index`); for each `x` check if `target - x` was seen — O(n) time/space. Sorted: two converging pointers — O(n) time, O(1) space. Knowing both (and when each applies) is the point.

**Q: Longest substring without repeating characters.** ⭐⭐
Variable sliding window + a map of last-seen index. Expand `end`; when you hit a char already inside the window, jump `start` past its previous position. O(n).

**Q: Maximum subarray sum (contiguous).** ⭐⭐
Kadane's: `bestEndingHere = max(x, bestEndingHere + x)`, track global max. O(n). Follow-up: return the indices, or handle max *product* (track both max and min because a negative flips them).

**Q: When do you use two pointers vs a sliding window vs a hash map?** ⭐
Two pointers: sorted array or comparing/among ends. Sliding window: contiguous subarray/substring with a longest/shortest/max constraint. Hash map: fast lookups/counts, "have I seen X," grouping — and prefix-sum+map for subarray-sum problems. Recognizing the trigger words is the skill.

**Q: Count subarrays with sum = k.** ⭐
Prefix sum + hash map of prefix-sum frequencies: for each running sum, add the count of `running - k` seen so far. O(n) vs the O(n²) brute force.

**Q: Move all zeros to the end, keeping order, in place.**
Same-direction two pointers: a `write` index; scan, copy non-zeros to `write++`, then fill the rest with zeros. O(n) time, O(1) space.

**Q: Is a string a palindrome? (ignoring case/non-alphanumerics)**
Converging two pointers skipping non-alphanumeric chars, comparing lowercased. O(n).

**Q: Merge two sorted arrays / merge overlapping intervals.**
Merge: two pointers walking both arrays. Intervals: sort by start, then sweep merging when `current.start <= prev.end`. O(n log n) from the sort.

**Q: Group anagrams.**
Hash map keyed by the sorted characters (or a 26-count signature) of each word → list of members. O(n·k log k) or O(n·k).

**Q: Container with most water / trapping rain water.** ⭐
Both are converging two pointers: move the shorter wall inward (it's the limiting factor). Trapping water also has a prefix-max/suffix-max variant. Classic "why move the smaller side?" reasoning question.

**Q: Find the duplicate / the missing number.**
Hash set (O(n) space), or math (sum formula) / XOR trick (O(1) space), or cyclic-sort/Floyd for the "1..n" constrained versions. Interviewers push you from the obvious O(n)-space answer toward O(1) space.
