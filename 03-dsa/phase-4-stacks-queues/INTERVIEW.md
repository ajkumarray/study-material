<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · linked lists](../phase-3-linked-lists/NOTES.md) | [Phase 5 · hashing ➡](../phase-5-hashing/NOTES.md)
<!-- /nav -->

# Phase 4 — Stacks & Queues: Interview Q&A + Problems

⭐ = asked constantly.

**Q: Stack vs queue — when do you use each?** ⭐
Stack (LIFO): reverse order, nesting/matching, DFS, backtracking, undo, call-stack simulation. Queue (FIFO): process in arrival order, BFS, scheduling, buffering. If you need both ends, a deque.

**Q: Which Java class for a stack or queue, and why not `Stack`?** ⭐
`ArrayDeque` for both. The legacy `Stack` extends `Vector` — synchronized (slow) and a broken abstraction (you can index into the "middle" of a stack). `ArrayDeque` is a fast circular array, O(1) at both ends. (`LinkedList` also implements `Deque` but has worse locality.)

**Q: Valid parentheses.** ⭐⭐
Push openers; on a closer, pop and verify it matches the expected opener; reject on mismatch or empty stack; at the end the stack must be empty. O(n). The canonical stack question.

**Q: Design a min-stack (O(1) getMin).** ⭐⭐
Keep an auxiliary stack of the running minimum, pushed/popped alongside the main stack so the top is always the current min. O(1) for all ops, O(n) extra space. (Space-optimization follow-up: store deltas to the min.)

**Q: What is a monotonic stack? What problems does it solve?** ⭐⭐
A stack kept strictly increasing or decreasing by popping violators on push — so each element is pushed/popped once, giving O(n). Solves next/previous greater/smaller element, daily temperatures, stock span, largest rectangle in histogram, trapping rain water, sum of subarray minimums.

**Q: Next greater element for each item.** ⭐
Decreasing monotonic stack of indices: while the current value exceeds the top's value, pop and record current as its next-greater; push current. O(n). Store indices so you can also report distances.

**Q: Largest rectangle in a histogram.** ⭐
Increasing monotonic stack of indices; when a shorter bar appears, pop taller bars and compute `height × width` for each (width extends back to the previous shorter bar); a trailing sentinel 0 flushes the stack. O(n). A hard-looking problem with an elegant linear solution.

**Q: Implement a queue using two stacks (and vice versa).** ⭐
Two stacks `in`/`out`: enqueue pushes to `in`; dequeue pops from `out`, refilling it from `in` (reversed) only when empty — amortized O(1). Stack-from-two-queues is the mirror (one op becomes O(n)).

**Q: Sliding window maximum.** ⭐
Monotonic deque of indices with decreasing values: evict front indices that fall out of the window, evict back indices whose values are ≤ the incoming one; the front is the window max. O(n) — beats the O(n·k) or heap O(n log k) approaches.

**Q: Evaluate Reverse Polish Notation / basic calculator.**
RPN: operands push, operators pop two and push the result. Infix calculators combine a value stack and an operator stack (or shunting-yard) respecting precedence.

**Q: How does recursion relate to a stack?**
Every recursive call pushes a frame onto the call stack (Java Phase 1.4); you can always convert recursion to iteration with an explicit stack (used to avoid stack overflow on deep trees/graphs — Phases 7, 9).
