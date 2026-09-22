<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · arrays](../phase-2-arrays/NOTES.md) | [Phase 4 · stacks queues ➡](../phase-4-stacks-queues/NOTES.md)
<!-- /nav -->

# Phase 3 — Linked Lists: Notes

All code below is from `LinkedListPatterns.java` in this directory (run with `java -ea LinkedListPatterns.java`) unless a section is marked "not implemented in this file" — those round out the picture with classic follow-up problems.

## 1. The Linked List Structure

A **linked list** is a chain of nodes, each holding a value plus a reference (`next`) to the next node. There is no contiguous memory and no index math — the only way to reach a node is to walk from the `head`, one `next` at a time. That single fact explains every strength and weakness a linked list has.

- **Node**: the basic unit — a value field plus one or more reference fields. In this file it's a package-visible static nested class; in production code it's often `private static` so callers never touch the internal pointer.
  ```java
  static class Node {
      int val;
      Node next;
      Node(int val) { this.val = val; }
  }
  ```
- **No random access**: getting to element `i` costs O(n) — you must walk from `head`. There is no equivalent of `array[i]`.
- **O(1) insert/delete at a known node**: if you already hold a reference to the node before the insertion/deletion point, splicing in or removing a node is just re-pointing a couple of references — no shifting, unlike an array.
- **Scattered memory, poor cache locality**: each node can live anywhere on the heap. Walking a linked list means chasing pointers across memory, which is much slower in practice than scanning a contiguous array, even though both are "O(n) to visit everything." This is *the* reason `ArrayList` usually outperforms `LinkedList` in real Java code (see the Java track) despite the linked list's better asymptotic insert/delete complexity.

| Operation | Array / ArrayList | Linked List |
|---|---|---|
| Access by index | **O(1)** | O(n) (must walk) |
| Search by value | O(n) | O(n) |
| Insert/delete at a **known** node | O(n) (shift elements) | **O(1)** (re-point references) |
| Insert/delete at head | O(n) (shift everything) | **O(1)** |
| Memory layout | Contiguous, cache-friendly | Scattered, pointer-chasing |
| Extra memory per element | None | One (or two) reference fields |

- **Singly linked**: each node has `next` only — you can walk forward but not backward.
- **Doubly linked**: each node has both `next` and `prev`, enabling O(1) deletion of a node you're holding (no need to find its predecessor) and backward traversal. Java's `java.util.LinkedList` and `Deque` interface are backed by a doubly linked list internally.
- **Circular**: the tail's `next` points back to the head instead of `null` — used for round-robin scheduling, circular buffers, and is exactly the shape you build to test cycle detection (see below).

```java
static Node build(int... vals) {
    Node dummy = new Node(0), tail = dummy;
    for (int v : vals) { tail.next = new Node(v); tail = tail.next; }
    return dummy.next;
}
// build(1, 2, 3) -> a 3-node list: 1 -> 2 -> 3 -> null
```

In this example: `build` itself already uses the dummy-head trick (below) — `dummy` is a throwaway node so the very first real node doesn't need special-case handling, and the function returns `dummy.next`, which is the true head.

**Why it's useful**: linked lists are the foundation for stacks, queues (Phase 4), hash-map buckets under separate chaining (Phase 5), adjacency lists in graphs (Phase 9), and the doubly-linked-list-plus-hash-map combo that powers an O(1) LRU cache. Understanding pointer manipulation here pays off in nearly every later phase.

**Summary — Key Takeaways:**
- No index math, no contiguous memory — you can only reach a node by walking from `head`.
- Array wins on access (O(1) vs O(n)); linked list wins on insert/delete *at a known node* (O(1) vs O(n) shift).
- Despite the Big-O advantage, `ArrayList` usually beats `LinkedList` in real Java workloads due to cache locality — asymptotic complexity isn't the whole story.
- Singly (`next` only) vs doubly (`next` + `prev`, Java's `LinkedList`) vs circular (tail points back to head).

---

## 2. The Dummy Head Technique

A **dummy head** (also called a sentinel node) is a throwaway node placed immediately *before* the real head of the list. It exists purely to remove special-case handling for operations that might change which node is the head.

- **The problem it solves**: without a dummy, code that might delete or replace the first node needs an `if (index == 0) { head = ...; } else { ... }` branch, because "the node before the head" doesn't exist to re-point. With a dummy, that node *always* exists (`dummy`), so every operation — including ones that affect the real head — can be written uniformly.
- **The pattern**: create `Node dummy = new Node(0)`, point `dummy.next` at (or build onto) the real list, do all your pointer work relative to `dummy`, and return `dummy.next` at the end (the true head, whatever it ended up being).
- **When to reach for it**: any time the head of the result might differ from the head of the input — merging two lists, removing a node (possibly the first one), inserting at the front, or building a new list node-by-node.

```java
static Node mergeSorted(Node a, Node b) {
    Node dummy = new Node(0), tail = dummy;   // dummy: a stand-in "node before the head"
    while (a != null && b != null) {
        if (a.val <= b.val) { tail.next = a; a = a.next; }
        else                { tail.next = b; b = b.next; }
        tail = tail.next;
    }
    tail.next = (a != null) ? a : b;          // attach whatever remains
    return dummy.next;                        // the real head
}
// mergeSorted(build(1,3,5), build(2,4,6)) -> 1,2,3,4,5,6
```

In this example: `dummy` is never part of the final answer — it's discarded the instant `dummy.next` is returned. Every step attaches the smaller of the two current heads to `tail`, so the merged list is built in one linear pass with no need to ask "is this the very first node I'm attaching?" — the dummy already answers that.

**Why it's useful**: the dummy head is one of the highest-leverage tricks in linked-list interviews — it turns "3 special cases plus the general case" into "1 general case," which means fewer null-pointer bugs and shorter code. It shows up in `removeNthFromEnd`, `mergeSorted`, "remove duplicates," "partition list," and any "build a new list as you go" problem.

**Summary — Key Takeaways:**
- A dummy node stands in for "the node before the head" so it always exists, even when the real head might change.
- Do all pointer manipulation relative to `dummy`; return `dummy.next` at the end.
- Reach for it whenever the operation could change, delete, or insert before the current head.

---

## 3. Reversal

**Reversal** walks the list once, re-pointing each node's `next` reference to the *previous* node instead of the next one, turning the whole chain around. It's the single most common linked-list interview question and the foundational building block for palindrome checks, "reorder list," and k-group reversal.

- **Iterative form**: three pointers — `prev` (starts `null`), `cur` (starts at `head`), and a temporary `next`. At each step: save `cur.next` before it's overwritten (or you lose the rest of the list), flip `cur.next` to point at `prev`, then advance both `prev` and `cur`.
- **Time Complexity**: O(n) — one pass.
- **Space Complexity**: O(1) — only three pointers, regardless of list length.
- **Recursive form**: also O(n) time, but O(n) **stack** space (one frame per node) — worth knowing both, since interviewers sometimes ask for the recursive version specifically to test recursion comfort.

```java
static Node reverse(Node head) {
    Node prev = null, cur = head;
    while (cur != null) {
        Node nextTmp = cur.next;   // 1. remember the rest
        cur.next = prev;           // 2. flip the pointer backward
        prev = cur;                // 3. advance prev
        cur = nextTmp;             // 4. advance cur
    }
    return prev;                   // prev is the new head
}
// reverse(build(1,2,3,4,5)) -> 5,4,3,2,1
// reverse(null) -> null   (empty-list edge case, no loop iterations run)
```

In this example: at the start of each iteration `nextTmp` is saved *before* `cur.next` is overwritten — skip that step and the rest of the original list becomes unreachable the moment you flip the first pointer. After the loop, `cur` is `null` (it walked off the end) and `prev` holds the last node visited, which is now the new head.

```java
static Node reverseRecursive(Node head) {
    if (head == null || head.next == null) return head;   // base case
    Node newHead = reverseRecursive(head.next);
    head.next.next = head;         // the node after me should point back at me
    head.next = null;              // and I become the tail
    return newHead;
}
// reverseRecursive(build(1,2,3,4)) -> 4,3,2,1
```

In this example: the recursion drills all the way down to the last node first (base case: `head.next == null`), which becomes `newHead` and is passed back up unchanged through every frame. On the way back up, each frame does exactly two pointer fixes: tell the node ahead of it (`head.next`) to point backward at it, then null out its own forward pointer so it doesn't accidentally keep the old forward chain alive.

| | Iterative reversal | Recursive reversal |
|---|---|---|
| Time | O(n) | O(n) |
| Space | **O(1)** | O(n) (call stack) |
| Readability | Three pointers to track | Easier to state, harder to trace by hand |
| When asked for | Default / "optimize space" follow-up | "Can you write it recursively?" follow-up |

**Why it's useful**: reversal underlies "reverse in groups of k," "reverse a sublist between positions m and n," palindrome checking (reverse the second half and compare), and "reorder list" (find the middle, reverse the second half, weave the two halves together).

**Summary — Key Takeaways:**
- Iterative: `prev`/`cur`/`next` triple, O(n) time, O(1) space — memorize the 4-line loop body.
- Always save `cur.next` before overwriting `cur.next` — this is the #1 source of "lost the rest of the list" bugs.
- Recursive version exists too (O(n) stack) — know it as a follow-up, not just the iterative form.
- Reversal is a *building block*, not just a standalone problem — palindrome, reorder, and k-group reversal all compose it.

---

## 4. Fast / Slow (Two-Speed) Pointers

**Fast/slow pointers** (also called the "tortoise and hare" technique) advance two pointers through the list at different speeds — typically the fast pointer moves 2 steps for every 1 step of the slow pointer. The gap this creates between them, tracked over a single pass, solves several problems that would otherwise need two passes or extra memory.

### Finding the middle

- **Key Concept**: when `fast` reaches the end of the list, `slow` has covered exactly half the distance — it's at the middle. This finds the middle in **one pass**, without first counting the list's length.
- **Even-length lists** have two middles; the loop condition `fast != null && fast.next != null` makes `slow` land on the **second** of the two middles. (Changing the condition to `fast.next != null && fast.next.next != null` would land on the first middle instead — know which one your problem wants.)

```java
static Node middle(Node head) {
    Node slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    return slow;
}
// middle(build(1,2,3,4,5)).val -> 3   (odd length: the exact middle)
// middle(build(1,2,3,4)).val   -> 3   (even length: the SECOND of the two middles)
```

In this example: for the 5-node list `1,2,3,4,5`, iteration 1 moves `slow` to `2` and `fast` to `3`; the loop condition still holds (`fast.next` is `5`, non-null), so iteration 2 moves `slow` to `3` and `fast` to `5`; now `fast.next` is `null`, the loop exits, and `slow` (node `3`) is returned — the exact middle. For the 4-node list `1,2,3,4`, iteration 1 moves `slow` to `2` and `fast` to `3`; the loop condition still holds (`fast.next` is `4`, non-null), so iteration 2 moves `slow` to `3` and `fast` to `4.next = null`; the loop now exits (`fast` is `null`), and `slow` (node `3`) is returned — the *second* of the two middle candidates (`2` and `3`). This confirms the loop condition `fast != null && fast.next != null` lands on the second middle for even-length lists, which is worth memorizing precisely rather than reconstructing under pressure.

### Floyd's cycle detection ("tortoise and hare")

- **Key Concept**: if the list has a cycle, the fast pointer — moving twice as fast — will eventually **lap** the slow pointer inside the loop and they will land on the exact same node. If there's no cycle, `fast` (or `fast.next`) hits `null` first and the loop simply exits.
- **Reference equality, not value equality**: the check is `slow == fast` — comparing that they are literally the same node object, not that their `.val` fields happen to match. Two different nodes could coincidentally hold equal values without there being a cycle.
- **Time Complexity**: O(n). **Space Complexity**: O(1) — this is the entire reason this technique exists; the naive alternative (store every visited node in a `HashSet` and check membership) also works but costs O(n) *extra space*.

```java
static boolean hasCycle(Node head) {
    Node slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) return true;   // same NODE (reference), not same value
    }
    return false;
}
// hasCycle(build(1,2,3))      -> false  (fast reaches null, no cycle)
// hasCycle(withCycle())       -> true   (1->2->3->4->2, fast laps slow inside the loop)
```

In this example: `withCycle()` builds `1->2->3->4` and then re-points the tail's `next` back to node `2`, creating a loop. `fast` (moving 2 at a time) eventually re-enters the loop portion of the list while `slow` (moving 1 at a time) is still working its way there, and because `fast` gains exactly one extra step relative to `slow` on every iteration, it is guaranteed to eventually be on the same node as `slow` rather than skipping past it.

| Approach | Time | Space | Notes |
|---|---|---|---|
| Fast/slow pointers | O(n) | **O(1)** | The interview-expected answer |
| Hash set of visited nodes | O(n) | O(n) | Correct but suboptimal — interviewers ask "can you do it in O(1) space?" to push toward fast/slow |

**Finding the cycle's start** (a standard follow-up): once a meeting point is found, reset one pointer to `head` and advance *both* pointers one step at a time; the node where they next meet is the start of the cycle. This works because of the specific distance relationship between the meeting point, the cycle start, and the head — a fact worth memorizing as "reset one to head, then move both at speed 1."

### Nth node from the end

- **Key Concept**: open a **gap of n** between two pointers (advance the lead pointer n steps first), then move both pointers forward together until the lead reaches the end of the list. The trailing pointer is now exactly n nodes from the end. One pass, no length precount needed.

```java
static Node removeNthFromEnd(Node head, int n) {
    Node dummy = new Node(0);
    dummy.next = head;
    Node lead = dummy, trail = dummy;
    for (int i = 0; i < n; i++) lead = lead.next;   // open a gap of n
    while (lead.next != null) { lead = lead.next; trail = trail.next; }
    trail.next = trail.next.next;                    // skip the target node
    return dummy.next;
}
// removeNthFromEnd(build(1,2,3,4,5), 2) -> 1,2,3,5   (the "4" — 2nd from the end — is removed)
// removeNthFromEnd(build(1), 1)         -> null       (removing the only node)
```

In this example: the dummy head matters here because `n` could equal the list's length, meaning the node to remove is the original head — without the dummy, `trail` would have nowhere to sit "before" the head to do the removal. After opening the gap of 2, `lead` and `trail` move together; by the time `lead.next` is `null`, `trail` sits right before the target node, and `trail.next = trail.next.next` splices it out.

**Why it's useful**: fast/slow pointers are the single most reused trick in this phase — middle-finding underlies palindrome-check and merge-sort-on-a-list; cycle detection is asked constantly as a standalone question and as a building block for "find duplicate number" (treating an array as an implicit linked list via indices); the gap-pointer technique generalizes to any "find something relative to the end, in one pass" problem.

**Summary — Key Takeaways:**
- Fast/slow (2-step vs 1-step) finds the middle in one pass — no length precount needed.
- Floyd's cycle detection is O(n) time, **O(1) space** — strictly better than the O(n)-space hash-set approach; always mention this space tradeoff.
- Cycle detection compares node *identity* (`==`), not `.val` — a common interview trap.
- The gap-pointer variant (open a gap of `n`, then move both) finds "Nth from the end" in one pass.

---

## 5. Merging and Combining Lists

Beyond the core techniques above, a family of problems combines multiple lists or list segments — these all lean on the dummy-head pattern from section 2.

- **Merge two sorted lists**: dummy head + repeatedly attach whichever current head (`a` or `b`) has the smaller value, then splice on whatever remains once one list runs out. O(n + m) time, O(1) extra space (the existing nodes are re-linked, not copied).
- **Merge k sorted lists** (not implemented in this file, classic follow-up): extend the two-list merge either by pairwise merging (merge list 1 with 2, that result with 3, and so on — O(k·n) total) or, better, by keeping the current head of all k lists in a min-heap (Phase 8) and repeatedly popping the smallest — O(n log k) where n is the total number of nodes across all lists. The heap approach is what interviewers expect once they say "generalize to k lists."
- **Find the intersection of two lists** (not implemented in this file): if two singly linked lists intersect (share a tail), the classic O(n) time / O(1) space trick is either (a) compute both lengths, advance the longer list's pointer by the length difference, then walk both pointers together until they're equal (that's the intersection node, or both become `null` if there's no intersection), or (b) the "switch heads" two-pointer trick — walk pointer A to the end of list A then continue into list B, and pointer B to the end of list B then continue into list A; because both pointers traverse `lenA + lenB` total nodes, they arrive at the intersection point (or both `null`) at the same step, with no length precomputation needed.

```java
// Length-difference alignment for intersection (sketch):
static Node getIntersection(Node a, Node b) {
    int lenA = length(a), lenB = length(b);
    while (lenA > lenB) { a = a.next; lenA--; }
    while (lenB > lenA) { b = b.next; lenB--; }
    while (a != b) { a = a.next; b = b.next; }   // both null if no intersection
    return a;
}
```

**Why it's useful**: merging is the linked-list analogue of the merge step in merge sort (Phase 10) — the same "compare two current heads, advance the smaller" idea, just done with pointers instead of array indices. The k-way merge with a heap is a direct preview of Phase 8's "merge k sorted arrays/streams" pattern.

**Summary — Key Takeaways:**
- Merging two sorted lists is dummy-head + "always attach the smaller current head," O(n+m).
- Merging k sorted lists scales best with a min-heap of the k current heads: O(n log k).
- Finding an intersection needs no extra space: align pointers using the length difference, or use the "switch heads" trick so both pointers cover the same total distance.

---

## 6. Palindrome Check (Composing the Techniques)

Checking whether a linked list reads the same forwards and backwards, in O(1) *extra* space, is the clearest example of composing the earlier techniques rather than inventing a new one.

- **The approach**: (1) find the middle with fast/slow pointers, (2) reverse the second half in place, (3) walk both halves (original first half + reversed second half) together comparing values.
- **Time Complexity**: O(n) — each of the three steps is a single O(n/2)-ish pass.
- **Space Complexity**: O(1) — the reversal is done in place on the existing nodes, no auxiliary array or stack.
- **A courtesy detail**: this approach *mutates* the list (the second half stays reversed). If the interviewer needs the original list preserved, reverse it back before returning, or explicitly note the tradeoff (O(n) extra space with a stack/array avoids mutating the input, at the cost of the space savings).

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
// isPalindrome(build(1,2,3,2,1)) -> true
// isPalindrome(build(1,2,3))     -> false
```

In this example: for `1,2,3,2,1`, `middle` returns the node holding `3` (the exact center of an odd-length list, positions 0-indexed as 0,1,2,3,4). `mid` points at the sub-list `3,2,1` (positions 2,3,4); reversing *that* sub-list in place turns it into `1,2,3` (position 4's value first, then 3, then the shared middle node last) and, critically, also cuts `node(3).next` to `null`, which truncates the *original* list so that walking from `head` now only visits `1,2,3` (positions 0,1,2) before hitting `null`. So `p1` walks `1,2,3` (the first half, positions 0-2) while `p2` walks `1,2,3` (the reversed second half, originally positions 4,3,2) — comparing them pairs up position 0 with position 4, position 1 with position 3, and the shared middle with itself, which is exactly the outer-to-inner comparison a palindrome check needs. Both sequences match, so the function returns `true`.

**Why it's useful**: this is the textbook example of the interview skill "recognize that a hard-looking problem is just two or three easy techniques chained together" — nobody expects you to invent a fourth algorithm; they expect you to recognize reversal + fast/slow are exactly what's needed here.

**Summary — Key Takeaways:**
- Palindrome check = find middle (fast/slow) + reverse second half + compare, all O(n) time, O(1) space.
- This composition pattern (chaining 2-3 known techniques) is common — always ask "can I break this into pieces I already know how to solve?" before reaching for something new.
- Mutates the list unless you reverse it back afterward — mention this tradeoff explicitly if it matters for the problem.

---

## 7. Common Pitfalls

These four mistakes account for the large majority of linked-list bugs in interviews:

- **Losing the rest of the list**: always save `cur.next` (or whichever pointer you're about to overwrite) into a temporary variable *before* you overwrite it. Once you flip a pointer without saving what it pointed to, everything downstream of it becomes unreachable garbage.
- **Null-check ordering matters**: `fast != null && fast.next != null` relies on Java's short-circuit `&&` — if `fast` is `null`, the left side fails and `fast.next` is never evaluated (which would throw `NullPointerException`). Writing the checks in the wrong order, or using `&` instead of `&&`, is a real bug, not a style nitpick.
- **Reference vs. value equality**: cycle detection and intersection detection compare node *identity* (`==`), not `.val`. Two distinct nodes can hold equal values without being the same node — using `.equals()` or `==` on `.val` instead of on the node reference silently produces wrong answers.
- **Off-by-one errors on dummy/gap logic**: the gap size in "Nth from end," and whether a loop condition lands on the first or second middle of an even-length list, are exactly the kind of detail that's easy to get backwards under interview pressure. Draw the pointers on paper for a tiny case (`n=1` and a full-length case) before trusting the loop bounds.

**Why it's useful**: naming these four pitfalls out loud during an interview — "let me make sure I save the next pointer before I overwrite it" — signals experience and often prevents you from actually making the mistake, since you're consciously checking for it.

**Summary — Key Takeaways:**
- Save-before-overwrite is the #1 rule; violate it and you silently truncate the list.
- Short-circuit `&&` order in null checks isn't a style choice — it prevents `NullPointerException`.
- Cycle/intersection checks compare node identity, not value.
- Trace gap/dummy logic on paper for `n=1` and full-length inputs before trusting it.
