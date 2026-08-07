<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · arrays](../phase-2-arrays/NOTES.md) | [Phase 4 · stacks queues ➡](../phase-4-stacks-queues/NOTES.md)
<!-- /nav -->

# Phase 3 — Linked Lists: Notes

## 3.1 — The structure

A **linked list** is a chain of nodes, each holding a value + a reference (`next`) to the next node. No contiguous memory, no index math — you reach a node only by walking from the `head`. That one fact explains everything:

| | Array / ArrayList | Linked List |
|---|---|---|
| access by index | **O(1)** | O(n) (walk) |
| insert/delete at a known node | O(n) (shift) | **O(1)** (re-point) |
| memory | contiguous, cache-friendly | scattered, pointer-chasing |

So linked lists win only when you do many inserts/deletes at positions you already hold a reference to, and rarely need random access. In practice `ArrayList` beats `LinkedList` for most work (Java Phase 3.2) due to cache locality — but linked lists are *interview gold* because they're pure pointer manipulation.

**Singly** linked: `next` only. **Doubly** linked: `next` + `prev` (enables O(1) delete of a given node and backward traversal; Java's `LinkedList`/`Deque`). **Circular**: the tail points back to the head.

## 3.2 — The three core techniques

**Dummy head** — a throwaway node placed *before* the real head. It removes special-casing for "operations that might change the head" (merge, remove-Nth, insert-at-front): you always have a node to attach to, and return `dummy.next` at the end. Reach for it whenever the head might move.

**Reversal** — walk the list re-pointing each `next` to the *previous* node: remember `next`, flip the pointer, advance both. O(n) time, **O(1) space**. Memorize the 4-line iterative form; also know the recursive version (O(n) stack). Reversal (whole or partial) is a building block for palindrome checks, reorder, and k-group reversal.

**Fast/slow (two-speed) pointers** — advance one pointer 2 steps for every 1 of the other:
- **Middle:** when fast reaches the end, slow is at the midpoint (one pass, no length count).
- **Floyd's cycle detection** ("tortoise & hare"): if a loop exists, fast laps slow and they **meet at the same node** (compare by *reference*, not value); if fast hits `null`, no cycle. O(n) time, **O(1) space** — vs the hash-set-of-visited-nodes approach at O(n) space. A follow-up finds the cycle's *start* (reset one pointer to head, advance both at speed 1; they meet at the entry).
- **Nth from end:** open a gap of n between two pointers, then move both until the lead hits the end — the trailer is at the target. One pass.

## 3.3 — Common problems & pitfalls

- **Merge two sorted lists:** dummy head + attach the smaller head each step; splice the remainder. O(n+m).
- **Remove Nth from end / reorder / palindrome:** compositions of the above (gap pointers, find-middle + reverse-second-half + compare).
- **Detect & remove cycle, find intersection of two lists:** fast/slow or length-difference alignment.

**Pitfalls that cause most bugs:**
- **Losing the rest of the list** — always save `next` *before* you overwrite a pointer.
- **Null checks** — `fast != null && fast.next != null` order matters (short-circuit, Java 1.3); an empty or single-node list is the usual edge case.
- **Reference vs value equality** — cycle detection compares node *identity* (`==`), not `.val`.
- **Off-by-one on the dummy/gap** — draw the pointers on paper for n=1 and n=length.
