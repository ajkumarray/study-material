<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · arrays](../phase-2-arrays/NOTES.md) | [Phase 4 · stacks queues ➡](../phase-4-stacks-queues/NOTES.md)
<!-- /nav -->

# Phase 3 — Linked Lists: Interview Q&A + Problems

⭐ = asked constantly.

**Q: Reverse a linked list.** ⭐⭐

The iterative solution uses three pointers: `prev` (starts `null`), `cur` (starts at `head`), and a temporary that holds `cur.next` before it gets overwritten. At each step you save the rest of the list, flip `cur.next` to point backward at `prev`, then advance both `prev` and `cur` forward. When `cur` becomes `null`, `prev` is the new head. This is O(n) time and O(1) space.

```java
static Node reverse(Node head) {
    Node prev = null, cur = head;
    while (cur != null) {
        Node nextTmp = cur.next;
        cur.next = prev;
        prev = cur;
        cur = nextTmp;
    }
    return prev;
}
```

You should also be ready to write it recursively: the base case is a null or single-node list; otherwise you recurse to the end, then on the way back up, tell the next node to point back at the current one and null out the current node's forward pointer. That's O(n) time but O(n) stack space, since there's one frame per node. This is the single most commonly asked linked-list question, and it's also the foundation for palindrome checks, "reverse in groups of k," and "reorder list" — so it needs to be fast and automatic, not derived from scratch each time.

*Follow-up: What would happen if you forgot to save `cur.next` before overwriting it?* You'd permanently lose the reference to the rest of the list the moment you flip the first pointer — `cur.next = prev` overwrites the only path forward, so `cur = cur.next` afterward would just move you to `prev` again (or worse), never progressing through the original list. This is the #1 bug in this problem.

*Follow-up: Reverse a sublist between positions m and n, in one pass.* Walk to position `m-1` with a dummy-head-protected pointer, then apply the same 3-pointer reversal loop but only for `n - m + 1` nodes, and finally re-attach the reversed segment's head and tail to the surrounding list. It's the same reversal loop, just with careful boundary bookkeeping — sketch the pointers on paper for a small `m`/`n` before coding.

---

**Q: Detect a cycle in a linked list.** ⭐⭐

Floyd's tortoise-and-hare: two pointers start at `head`; `slow` advances one node per step, `fast` advances two. If the list has a cycle, `fast` — moving twice as fast — eventually laps `slow` from behind and they land on the exact same node, which the code detects with `slow == fast` (reference equality, not `.val` equality). If there's no cycle, `fast` (or `fast.next`) hits `null` and the loop exits normally. This is O(n) time and O(1) space.

```java
static boolean hasCycle(Node head) {
    Node slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) return true;
    }
    return false;
}
```

*Follow-up: Find the cycle's start node.* Once `slow` and `fast` meet somewhere inside the loop, reset one pointer to `head` and then advance *both* pointers one step at a time (both now at speed 1). The node where they next meet is the cycle's entry point. This works because of a fixed relationship between the distance from `head` to the cycle start, and the distance from the meeting point back around to the cycle start — a classic derivation worth having memorized as the recipe, even if you don't re-derive the proof live.

*Follow-up: Find the length of the cycle.* Once you've detected a meeting point, keep one pointer fixed there and advance the other pointer one step at a time, counting steps, until it returns to the fixed pointer. The step count is the cycle's length.

---

**Q: Why fast/slow instead of a hash set for cycle detection?**

Both approaches are O(n) time, but they differ in space: fast/slow uses **O(1)** extra space (just two pointers), while the hash-set approach — walk the list, add each node reference to a `HashSet`, and declare a cycle the moment you're about to add a node that's already in the set — uses **O(n)** extra space in the worst case (a list with no cycle puts every node in the set before returning false).

```java
// Hash-set approach: correct, but O(n) space
static boolean hasCycleHashSet(Node head) {
    Set<Node> seen = new HashSet<>();
    for (Node n = head; n != null; n = n.next) {
        if (!seen.add(n)) return true;   // add() returns false if already present
    }
    return false;
}
```

Interviewers ask this question specifically to see whether you'll stop at the first correct answer or push toward the optimal one. The hash-set version is a perfectly valid starting point — mention it, then pivot to "but we can do this in O(1) space with fast/slow pointers," which shows you understand the space/time tradeoff rather than just having memorized Floyd's algorithm as a fact.

*Follow-up: Is there ever a reason to prefer the hash-set approach in production code?* If the list nodes don't support cheap identity comparison in the way you'd expect (rare in practice), or if you need to also track *which* nodes were visited for some other purpose beyond just cycle detection, the hash set gives you that data structure for free as a side effect. Otherwise, fast/slow is strictly better for the pure cycle-detection question.

---

**Q: Find the middle of a linked list.** ⭐

Fast/slow pointers again: `fast` moves two steps for every one step of `slow`. When `fast` reaches the end (or falls off it), `slow` is sitting at the middle — found in a single pass, with no need to first count the list's length. For an odd-length list this lands exactly on the center node; for an even-length list, the loop condition `fast != null && fast.next != null` makes it land on the *second* of the two middle candidates.

```java
static Node middle(Node head) {
    Node slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    return slow;
}
```

*Follow-up: How would you get the FIRST of the two middles for an even-length list instead?* Change the loop condition to `fast.next != null && fast.next.next != null`, which makes the loop run one fewer iteration for even-length inputs, landing `slow` one node earlier. Always clarify with the interviewer which middle they want before coding, since both are valid interpretations of "the middle" for an even-length list.

*Follow-up: How is this used in the palindrome check?* Finding the middle is step one of a three-step composition: find the middle, reverse the second half starting there, then compare the reversed second half against the first half node by node. It's a clean example of chaining two known techniques instead of inventing something new.

---

**Q: Merge two sorted linked lists.** ⭐

Use a dummy head to avoid special-casing which list's node becomes the very first node of the result. Then repeatedly compare the current heads of both lists, attach the smaller one to the tail of the result, and advance that list's pointer. Once one list is exhausted, splice the entire remainder of the other list onto the tail directly (no need to keep comparing one at a time). O(n + m) time, O(1) extra space, since existing nodes are re-linked rather than copied.

```java
static Node mergeSorted(Node a, Node b) {
    Node dummy = new Node(0), tail = dummy;
    while (a != null && b != null) {
        if (a.val <= b.val) { tail.next = a; a = a.next; }
        else                { tail.next = b; b = b.next; }
        tail = tail.next;
    }
    tail.next = (a != null) ? a : b;
    return dummy.next;
}
```

*Follow-up: Extend this to merge k sorted lists.* Two viable strategies: pairwise merge (merge list 1 with list 2, merge that result with list 3, and so on), which costs O(k·n) total where n is average list length; or maintain a min-heap (Phase 8) containing the current head of each of the k lists, repeatedly pop the minimum, advance that list, and push its new head — this costs O(n log k) where n is the *total* number of nodes across all lists, which is the answer interviewers expect once "k lists" is on the table.

*Follow-up: What if the two input lists use `<=` vs `<` for the tie-breaking comparison — does it matter?* It affects **stability** (which list's node comes first when values are equal) but not correctness of the sort order. If the problem cares about preserving relative order of equal elements from a particular source list, the comparison operator choice matters; otherwise it doesn't change the final sorted sequence.

---

**Q: Remove the Nth node from the end (one pass).** ⭐

Use a dummy head (in case the node to remove is the original head itself) plus two pointers with a gap of `n` nodes between them. Advance the lead pointer `n` steps first to open the gap, then move both pointers forward together until the lead reaches the end of the list. At that point the trailing pointer sits exactly one node before the target, so `trail.next = trail.next.next` removes it. One pass, and the dummy head means removing the true head requires no special case.

```java
static Node removeNthFromEnd(Node head, int n) {
    Node dummy = new Node(0);
    dummy.next = head;
    Node lead = dummy, trail = dummy;
    for (int i = 0; i < n; i++) lead = lead.next;
    while (lead.next != null) { lead = lead.next; trail = trail.next; }
    trail.next = trail.next.next;
    return dummy.next;
}
```

*Follow-up: Why start both `lead` and `trail` at `dummy` instead of `head`?* If `n` equals the length of the list, the node to remove is the original head, and `trail` needs to be sitting on some node *before* the head to perform the removal via `trail.next = trail.next.next`. Starting at `dummy` guarantees that "node before the head" always exists, so the edge case of removing the head requires no separate branch.

*Follow-up: Could you solve this in two passes instead, and why would one pass be preferred?* Yes — compute the list length in one pass, then walk `length - n` steps from the head in a second pass to find the node before the target. One pass is preferred purely for elegance and slightly better constant factors (you touch each node roughly once instead of up to twice); both are O(n) time, so this is more a style/efficiency-signal question than a correctness one.

---

**Q: Is a linked list a palindrome? (O(1) space)** ⭐

Find the middle with fast/slow pointers, reverse the second half in place starting from there, then walk the original first half and the reversed second half together, comparing values node by node. If every pair matches, it's a palindrome. O(n) time, O(1) extra space — this is the composition example: no new algorithm, just reversal plus fast/slow chained together.

```java
static boolean isPalindrome(Node head) {
    Node mid = middle(head);
    Node second = reverse(mid);
    Node p1 = head, p2 = second;
    while (p2 != null) {
        if (p1.val != p2.val) return false;
        p1 = p1.next; p2 = p2.next;
    }
    return true;
}
```

*Follow-up: This mutates the input list — how would you avoid that?* Either restore the list by reversing the second half back before returning (adds an extra O(n) pass but keeps the same O(1) space), or abandon the O(1)-space approach entirely and instead copy all values into an array/`ArrayList` and use two-pointer comparison from both ends — that avoids mutation but costs O(n) extra space. State this tradeoff explicitly if the interviewer says "don't modify the input."

*Follow-up: Why does comparing `p1` against `p2` correctly check the palindrome property, rather than comparing the wrong halves?* Reversing the second half and walking it from its new head effectively walks the original list from its *tail* inward, while `p1` walks from the *head* inward — so each comparison pairs up symmetric positions (first vs. last, second vs. second-to-last, and so on), which is exactly what a palindrome check needs.

---

**Q: Why does the dummy-head trick help?**

It gives you a stable node that exists *before* the real head, so operations that might change which node is the head — inserting at the front, deleting the head, or building a new list from scratch (as in merging) — need no special-case branch. Every operation can be written uniformly as "do pointer work relative to `dummy`," and you return `dummy.next` at the end, whatever that ends up being. Without it, code tends to grow an `if (index == 0) { ... } else { ... }` branch for every operation that could touch the head, which is both more code and a more common source of null-pointer bugs.

*Follow-up: Is there ever a downside to always using a dummy head?* It adds one extra node's worth of allocation and one extra `.next` hop when reading the true result out, both negligible in practice. There's essentially no real downside — some engineers default to always using a dummy head for any list-building code specifically because it eliminates a whole category of head-related edge-case bugs.

---

**Q: When would you actually choose a linked list over an array/ArrayList?** ⭐

When you frequently insert or delete at positions you already hold a reference to, and rarely need random access by index. The canonical real-world example is an **LRU cache**: pairing a doubly linked list with a hash map gives O(1) "move this entry to the front" (unlink and re-link three pointers, no shifting) and O(1) eviction of the least-recently-used entry (drop the tail), which a plain array/`ArrayList` cannot match — moving an element to the front of an array-backed list requires shifting every element in between, O(n). Linked lists are also preferred when you need a stable pointer or iterator that survives concurrent mutation of the rest of the structure, since array-backed structures can invalidate indices/iterators on resize or shift.

For anything index-access-heavy or iteration-heavy without mid-sequence mutation, arrays and `ArrayList` win, largely due to cache locality — contiguous memory means the CPU can prefetch effectively, while a linked list's scattered nodes cause pointer-chasing cache misses on every hop, even though the asymptotic complexity of a full traversal is "O(n)" for both.

*Follow-up: Given that, why does Java's `LinkedList` implement `Deque`, and when would you actually reach for it over `ArrayDeque`?* `LinkedList`'s doubly-linked structure gives true O(1) insertion/removal at both ends without any resizing/shifting concerns, and O(1) removal of an arbitrary node if you're holding an `Iterator` positioned there via `ListIterator.remove()`. In practice, `ArrayDeque` is almost always preferred for pure stack/queue use because it avoids per-node allocation overhead and has better cache behavior — `LinkedList` mainly earns its keep when you specifically need that arbitrary-position O(1) removal via a held iterator, which `ArrayDeque` cannot offer.

---

**Q: Reverse nodes in k-group / swap pairs / rotate.**

These are all reversal variants that add careful boundary-pointer management on top of the basic 3-pointer reversal loop. "Swap pairs" is k-group reversal with `k=2`. "Reverse in k-groups" repeats the basic reversal loop for exactly `k` nodes at a time, tracking the tail of the previous group so it can be re-attached to the head of the newly reversed group — and requires checking upfront whether at least `k` nodes remain before reversing a group (the last partial group, if any, is typically left unreversed per the classic problem statement). "Rotate the list right/left by k" is often easier to think of as: find the new tail (length - k steps from head, or use fast/slow style counting), break the list there, and re-attach the old head to the old tail to close it into a temporary circle before breaking it at the new point.

*Follow-up: What's the general strategy for tackling an unfamiliar "harder" linked-list problem in an interview?* Practice writing plain reversal cold first — it's the muscle memory everything else builds on — then, for the harder variant, explicitly draw out a small example (5-6 nodes) on paper, label every pointer you'll need (`prevGroupTail`, `groupHead`, `groupTail`, `nextGroupHead`), and only then translate the picture into code. Trying to hold all the pointer relationships in your head without drawing them is the most common way these problems go wrong under interview pressure.

---

**Q: Find where two linked lists intersect.**

Two singly linked lists "intersect" if they share a common tail (from the intersection node onward, both lists are identical by reference, not just by value). Two O(n) time, O(1) space approaches: (1) compute both lengths, advance the pointer on the longer list by the length difference so both pointers now have the same remaining distance to the end, then advance both together — they'll arrive at the intersection node (or both become `null` if there's no intersection) at the same time; (2) the "switch heads" trick — walk pointer A to the end of list A and then continue into list B from its head, and walk pointer B to the end of list B and then continue into list A from its head. Because both pointers traverse exactly `lenA + lenB` total nodes before potentially meeting, they arrive at the intersection point (or both `null`) simultaneously, with no length precomputation needed.

```java
static Node getIntersection(Node headA, Node headB) {
    Node a = headA, b = headB;
    while (a != b) {
        a = (a == null) ? headB : a.next;
        b = (b == null) ? headA : b.next;
    }
    return a;   // intersection node, or null if none
}
```

*Follow-up: Why does the "switch heads" trick work even when the two lists have different lengths?* Say list A has length `p` before the intersection and list B has length `q` before the intersection, with a shared tail of length `c` after. Pointer A travels `p + c + q` total nodes before reaching the intersection on its second pass; pointer B travels `q + c + p` — the same total distance, just partitioned differently between the two lists. Since both totals are equal, both pointers arrive at the intersection node on the same step, or both become `null` at the same step if there's no shared tail at all.

*Follow-up: How would you solve this with extra space instead, and when might that be preferable?* Walk list A into a `HashSet<Node>`, then walk list B checking each node against the set — the first hit is the intersection. O(n) time, O(n) space. This is simpler to write correctly under pressure and is a reasonable first answer, but the two-pointer approaches above are what an interviewer is angling for once they ask "can you do this in O(1) space?"
