<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · arrays](../phase-2-arrays/NOTES.md) | [Phase 4 · stacks queues ➡](../phase-4-stacks-queues/NOTES.md)
<!-- /nav -->

# Phase 3 — Linked Lists: Interview Q&A + Problems

⭐ = asked constantly.

**Q: Reverse a linked list.** ⭐⭐
Iterative: three pointers (`prev`, `cur`, `next`); flip each `cur.next` to `prev`, advance. O(n) time, O(1) space. Be ready to also write it recursively (O(n) stack). The single most common linked-list question.

**Q: Detect a cycle in a linked list.** ⭐⭐
Floyd's tortoise-and-hare: slow +1, fast +2; if they meet, there's a cycle; if fast hits null, none. O(n) time, O(1) space. Follow-ups: find the cycle's start node (reset one pointer to head, advance both by 1, they meet at the entry) and the cycle length.

**Q: Why fast/slow instead of a hash set for cycle detection?**
Both are O(n) time, but fast/slow is **O(1) space** vs the hash set's O(n). Interviewers ask this to push you from the obvious solution to the optimal one.

**Q: Find the middle of a linked list.** ⭐
Fast/slow: when fast reaches the end, slow is the middle — one pass, no length precount. For even length, decide whether you want the first or second middle (loop condition controls it).

**Q: Merge two sorted linked lists.** ⭐
Dummy head; repeatedly attach the smaller of the two heads; splice the remaining list. O(n+m). Extends to "merge k sorted lists" via a min-heap or pairwise merging (Phase 8).

**Q: Remove the Nth node from the end (one pass).** ⭐
Dummy head + two pointers with a gap of n; advance both until the lead reaches the end; the trailer is just before the target — skip it. One pass, handles removing the head via the dummy.

**Q: Is a linked list a palindrome? (O(1) space)** ⭐
Find the middle (fast/slow), reverse the second half, compare the halves node by node. O(n) time, O(1) space. (Restore the list afterward if the interviewer requires it unchanged.)

**Q: Why does the dummy-head trick help?**
It gives a stable node *before* the head so operations that might change the head (insert-front, delete-head, merge) need no special case — you always attach to `dummy.next` and return it. Cleaner code, fewer null bugs.

**Q: When would you actually choose a linked list over an array/ArrayList?** ⭐
When you frequently insert/delete at positions you already reference (e.g., an LRU cache pairs a doubly-linked list with a hash map for O(1) move-to-front and eviction), or need a stable pointer/iterator across mutations. For index access or iteration-heavy work, arrays win on cache locality.

**Q: Reverse nodes in k-group / swap pairs / rotate.**
All are reversal variants with careful boundary pointers — practice reverse-in-place first, then apply it to sublists. Draw the pointers for a small example before coding.

**Q: Find where two linked lists intersect.**
Align by length difference (advance the longer list's pointer by the difference, then move both together until they're equal), or the two-pointer "switch heads at the end" trick — both O(n) time, O(1) space.
