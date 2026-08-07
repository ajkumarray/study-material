<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · linked lists](../phase-3-linked-lists/NOTES.md) | [Phase 5 · hashing ➡](../phase-5-hashing/NOTES.md)
<!-- /nav -->

# Phase 4 — Stacks & Queues: Notes

## 4.1 — The two structures

**Stack — LIFO** (last in, first out). Operations at *one* end: `push`, `pop`, `peek`, all O(1). Mental models: a stack of plates; the call stack (Java Phase 1.4 — recursion *is* a stack). Used for: matching/nesting (parentheses), undo, DFS, expression evaluation, backtracking.

**Queue — FIFO** (first in, first out). Add at the back, remove from the front, O(1). Mental models: a line of people; a task/print queue. Used for: BFS (Phase 9), scheduling, buffering, level-order traversal.

**Deque** ("deck") — double-ended queue: add/remove at *both* ends. A superset of both.

**In Java, use `ArrayDeque` for all three** (Java Phase 3.2): `push`/`pop`/`peek` for a stack, `offer`/`poll` for a queue. Never the legacy `Stack` class (it extends `Vector`, is synchronized, and leaks a broken List abstraction). `ArrayDeque` is a circular array — O(1) both ends, cache-friendly.

## 4.2 — Stack patterns

**Matching/nesting** (valid parentheses): push openers, and on each closer pop and check it matches — leftover items or an early mismatch means unbalanced. The template for balanced-brackets, tag matching, and expression validation.

**Min-stack** (O(1) `getMin`): keep a *second* stack storing the minimum-so-far at each level, pushed/popped in lockstep with the main stack. Generalizes to max-stack, and to any "track an aggregate over the current stack cheaply."

**Expression evaluation:** Reverse Polish Notation — operands push, an operator pops its two operands and pushes the result. (Infix → postfix conversion uses the shunting-yard algorithm, also stack-based.)

## 4.3 — The monotonic stack (the star pattern)

A **monotonic stack** keeps its elements sorted (strictly increasing or decreasing) by popping violators as you push. The magic: **each element is pushed and popped at most once → O(n) total**, even though there's a nested `while`.

It's the go-to for **"next/previous greater/smaller element"** and **span** problems:
- **Next greater element:** keep a *decreasing* stack of indices; when the current value exceeds the top's value, you've found *that* index's next-greater — resolve and pop. (Store indices, not values, so you can also compute distances.)
- **Largest rectangle in a histogram:** an *increasing* stack of bar indices; when a shorter bar arrives, pop taller bars and compute the rectangle each anchors (`height × width`), where width spans back to the previous shorter bar. A sentinel height of 0 at the end flushes the stack. O(n) — the elegant solution to a problem that looks O(n²).
- Same shape solves: daily temperatures (days until warmer), stock span, trapping rain water (a monotonic variant), and "sum of subarray minimums."

Recognize it when the problem asks, for each element, about the *nearest* larger/smaller element in some direction.

## 4.4 — Queue patterns

**Queue via two stacks:** an `in` stack for enqueue and an `out` stack for dequeue. When `out` is empty, pour `in` into `out` (reversing order twice restores FIFO). **Amortized O(1)** — each element transfers at most once. (The mirror, "stack via two queues," is a classic too.)

**Monotonic deque** — a sliding-window maximum/minimum in O(n): keep a deque of indices whose values are monotonic, evicting from the front when they leave the window and from the back when a new value dominates. The window-max companion to Phase 2's sliding window.

**Circular queue / ring buffer** — fixed-size FIFO reusing array slots (`ArrayDeque` under the hood), the basis of bounded buffers and streaming.
