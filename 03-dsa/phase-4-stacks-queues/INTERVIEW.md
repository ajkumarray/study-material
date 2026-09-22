<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · linked lists](../phase-3-linked-lists/NOTES.md) | [Phase 5 · hashing ➡](../phase-5-hashing/NOTES.md)
<!-- /nav -->

# Phase 4 — Stacks & Queues: Interview Q&A + Problems

⭐ = asked constantly.

**Q: Stack vs queue — when do you use each?** ⭐

A stack is LIFO (last in, first out): the element most recently pushed is the first one
popped. Reach for it whenever a problem is about "undo the most recent thing," nesting/
matching (brackets, tags), depth-first traversal, or backtracking, and whenever recursion is
involved at all — the call stack *is* a stack. A queue is FIFO (first in, first out):
elements are served in the order they arrived. Reach for it for breadth-first traversal,
task/job scheduling, buffering between a producer and a consumer, and any "process in arrival
order" requirement. If you need to insert or remove efficiently at *both* ends — for example
a sliding-window maximum, or implementing a stack and queue from the same object — use a
deque, which is a strict superset of both.

*Follow-up: "Give me a real scenario where using the wrong one silently produces wrong
output, not just slower output."* — BFS with a stack instead of a queue: you'd still visit
every node, but not in "closest first" order, so shortest-path-by-edge-count guarantees
(Phase 9) break; the algorithm terminates and returns *an* answer, just not the correct one.
That's more dangerous than a performance regression because it can pass on some test cases
and silently fail on others.

**Q: Which Java class should you use for a stack or queue, and why not `java.util.Stack`?** ⭐

Use `ArrayDeque` for all three roles — stack, queue, and deque. It's backed by a resizable
circular array (a ring buffer), giving O(1) amortized operations at both ends with excellent
cache locality since the backing storage is contiguous memory. The legacy `Stack` class
should be avoided for two independent reasons: it extends `Vector`, so every method is
`synchronized`, adding locking overhead you almost never need in single-threaded code; and
because it's fundamentally a `List`, nothing stops calling code from doing `stack.get(0)` and
reaching into the "middle" of what's supposed to be a LIFO-only structure — it doesn't
actually enforce the abstraction it's named for. `LinkedList` also implements the `Deque`
interface and works correctly, but each element is a separate heap-allocated node with
pointer-chasing overhead, so it loses to `ArrayDeque` on cache locality and per-operation
constant factors.

```java
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1); stack.push(2);
System.out.println(stack.pop());   // 2 — push/pop act on the front

Deque<Integer> queue = new ArrayDeque<>();
queue.offer(1); queue.offer(2);
System.out.println(queue.poll());  // 1 — offer appends to back, poll removes from front
```

*Follow-up: "How does `ArrayDeque` achieve O(1) at both ends internally?"* — It's a circular
array: a `head` and `tail` index that wrap around via modulo arithmetic when they reach the
end of the backing array, so elements never need to be shifted. When the array fills up it
resizes (typically doubling) and copies elements — the same amortized-O(1) analysis as
`ArrayList` growth (Phase 2).

**Q: Valid parentheses — walk me through your approach.** ⭐⭐

Scan the string once. Every opening bracket (`(`, `[`, `{`) gets pushed onto a stack. Every
closing bracket must match the *top* of the stack: pop it and check it's the corresponding
opener; if the stack is empty at that point, or the popped opener doesn't match, the string
is invalid and you can return `false` immediately. After the full scan, the stack must be
empty — any leftover opener means something was never closed. It's O(n) time and O(n) worst-case
space (a string of all openers).

```java
static boolean validParens(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        switch (c) {
            case '(', '[', '{' -> stack.push(c);
            case ')' -> { if (stack.isEmpty() || stack.pop() != '(') return false; }
            case ']' -> { if (stack.isEmpty() || stack.pop() != '[') return false; }
            case '}' -> { if (stack.isEmpty() || stack.pop() != '{') return false; }
        }
    }
    return stack.isEmpty();
}
```

*Follow-up: "Why does `([)]` fail but `([{}])` pass — walk through the stack state for both"*
— For `([{}])`: push `(`, push `[`, push `{`; then `}` pops `{` (match), `]` pops `[`
(match), `)` pops `(` (match); stack ends empty → valid. For `([)]`: push `(`, push `[`; then
`)` arrives and pops the *top*, which is `[`, not `(` — immediate mismatch, returns `false`.
The key insight is that a stack enforces LIFO closing order: whichever bracket was opened
*most recently* must be the one that closes *next*, which is exactly what "properly nested"
means.

*Follow-up: "Could you solve this with just a counter instead of a stack?"* — Only for a
single bracket type. A counter tracks "how many are currently open" but throws away *which*
type is open, so it can't detect `([)]`-style type mismatches once more than one bracket kind
is in play.

**Q: Design a min-stack that supports O(1) `getMin()`.** ⭐⭐

The naive approach — scan the whole stack for the minimum on every `getMin()` call — is O(n)
per query. To get O(1), maintain a *second* stack, `mins`, in lockstep with the main data
stack: on every `push(x)`, also push `Math.min(x, mins.peek())` (or just `x` if `mins` is
empty) onto `mins`. On every `pop()`, pop both stacks together. `mins.peek()` is then always
the minimum of everything currently on the main stack, in O(1), at the cost of O(n) extra
space.

```java
static class MinStack {
    private final Deque<Integer> stack = new ArrayDeque<>();
    private final Deque<Integer> mins = new ArrayDeque<>();
    void push(int x) {
        stack.push(x);
        mins.push(mins.isEmpty() ? x : Math.min(x, mins.peek()));
    }
    void pop()   { stack.pop(); mins.pop(); }
    int getMin() { return mins.peek(); }
}
```

*Follow-up: "How would you optimize the space if pushes are frequent but the minimum rarely
changes?"* — Instead of pushing a `mins` entry on *every* push, only push when a new minimum
is actually set, and store how many consecutive elements share that minimum (or push the
*delta* from the previous minimum instead of the raw value). This trades a bit of extra logic
on `pop()` for meaningfully less space when the minimum is stable across many pushes.

*Follow-up: "How would you adapt this to a max-stack, or to `getMin` over just the last k
elements?"* — Max-stack is the mirror: track `Math.max` instead of `Math.min`. "Min over the
last k" is a materially different, harder problem — that's exactly the sliding-window-minimum
shape solved with a monotonic deque (see below), not a plain min-stack, because old elements
need to expire.

**Q: What is a monotonic stack, and what family of problems does it solve?** ⭐⭐

A monotonic stack keeps its elements in sorted order — strictly increasing or strictly
decreasing from bottom to top — by popping any elements that would violate that order *before*
pushing a new one. The reason it's fast despite the nested `while` loop inside the outer `for`
loop is that every element is pushed exactly once and popped at most once over the entire
run, so the total number of stack operations across the whole algorithm is bounded by `2n`,
giving amortized **O(n)**, not O(n²). It's the standard tool for any "for each element, find
the nearest greater/smaller element" question: next/previous greater or smaller element,
daily temperatures, stock span, largest rectangle in histogram, trapping rain water (stack
variant), and sum of subarray minimums.

```java
static int[] nextGreater(int[] a) {
    int[] res = new int[a.length];
    Arrays.fill(res, -1);
    Deque<Integer> stack = new ArrayDeque<>();   // indices, values decreasing
    for (int i = 0; i < a.length; i++) {
        while (!stack.isEmpty() && a[i] > a[stack.peek()]) {
            res[stack.pop()] = a[i];
        }
        stack.push(i);
    }
    return res;
}
```

*Follow-up: "Prove the O(n) bound to me — I don't believe the nested while is linear."* — Look
at total work, not per-iteration work. Each index is pushed onto the stack exactly once (once
per outer-loop iteration). Each index can be popped at most once ever, because once it's
popped it's gone for good. So across the *entire* algorithm, the number of pushes is exactly
`n` and the number of pops is at most `n` — total operations `≤ 2n`, which is O(n) regardless
of how the pops are distributed across outer iterations (some iterations do zero pops, others
do many, but the sum is capped).

*Follow-up: "Why store indices instead of values?"* — Values alone tell you *what* is next
greater, but many variants (daily temperatures, stock span) ask *how far away* it is — that
requires knowing positions, which only indices give you; you can always recover the value via
`a[index]`.

**Q: Largest rectangle in a histogram.** ⭐

Keep an *increasing* monotonic stack of bar indices. Walk through the bars (plus one extra
"sentinel" iteration with height 0 at the end, to force-flush anything still on the stack).
When the current bar is *shorter* than the bar at the top of the stack, that taller bar can't
extend any further right, so pop it and finalize the largest rectangle it could anchor: its
height is `heights[popped]`, and its width extends from just after the *new* stack top (the
nearest bar shorter than it on the left) up to (but not including) the current index. Track
the running maximum of `height × width` across all such finalized rectangles. This runs in
O(n) — each bar is pushed once and popped once.

```java
static int largestRectangle(int[] heights) {
    Deque<Integer> stack = new ArrayDeque<>();
    int best = 0;
    for (int i = 0; i <= heights.length; i++) {
        int h = (i == heights.length) ? 0 : heights[i];
        while (!stack.isEmpty() && h < heights[stack.peek()]) {
            int height = heights[stack.pop()];
            int width = stack.isEmpty() ? i : i - stack.peek() - 1;
            best = Math.max(best, height * width);
        }
        stack.push(i);
    }
    return best;
}
```

For `[2, 1, 5, 6, 2, 3]` the answer is 10, from bars of height 5 and 6 together spanning
width 2 (`5 × 2`).

*Follow-up: "Why is the width `i - stack.peek() - 1` and not just `i - stack.peek()`?"* — The
new stack top (after popping) is the index of the nearest bar to the left that's *shorter*
than the one just popped — that bar is a boundary the rectangle cannot include, so the
rectangle spans strictly between it and `i` (also excluded, since `heights[i] < height`).
That's `i - stack.peek() - 1` positions, not `i - stack.peek()`.

*Follow-up: "This looks like it should be O(n²) — one rectangle check per pair of bars. Why
isn't it?"* — Same amortized argument as next-greater-element: each bar index is pushed once
and popped once across the whole run, so the total work is O(n) even though computing "how
far does this bar extend" for a naive approach would otherwise require rescanning
neighbors — which is exactly what the O(n²) brute force does.

**Q: Implement a queue using two stacks (and briefly, the reverse).** ⭐

Use two stacks, `in` and `out`. `enqueue(x)` always pushes onto `in` — O(1), no exceptions.
`dequeue()` pops from `out`; if `out` is empty, first drain all of `in` into `out` (popping
each element off `in` and pushing it onto `out`), which reverses the order exactly once,
turning `in`'s LIFO order back into the correct FIFO order relative to arrival, then pop from
`out`. The key efficiency detail is refilling `out` **only when it's empty** — that guarantees
each element is moved from `in` to `out` at most once over its entire lifetime, so while any
single `dequeue()` call *can* cost O(n) (when it triggers a refill), the amortized cost per
operation across a sequence of operations is O(1).

```java
static class QueueViaStacks {
    private final Deque<Integer> in = new ArrayDeque<>();
    private final Deque<Integer> out = new ArrayDeque<>();
    void enqueue(int x) { in.push(x); }
    int dequeue() {
        if (out.isEmpty()) { while (!in.isEmpty()) out.push(in.pop()); }
        return out.pop();
    }
}
```

The mirror problem, stack via two queues, is the harder direction: a queue can't cheaply
"peek the most recently added item" the way a stack peeks its top, so a typical
implementation ends up making one of push/pop cost O(n) (e.g., by rotating the queue after
every push so the newest element sits at the front) instead of achieving the same clean
amortized O(1) both ways.

*Follow-up: "Is dequeue's O(1) claim true in the worst case, or only amortized? Explain the
difference in this specific example."* — Only amortized. A single `dequeue()` immediately
after a burst of `n` enqueues costs O(n) because `out` is empty and must be fully refilled.
But once refilled, the next `n-1` dequeues are O(1) pure pops, because nothing has to move
again until `out` empties out completely. Summing the O(n) refill cost across all `n`
elements and dividing by `n` operations gives O(1) *average* cost — that's what "amortized"
means, as opposed to a guarantee that hold for every single call individually.

**Q: Sliding window maximum — find the max of every window of size k as it slides.** ⭐

Use a deque holding *indices*, kept monotonically decreasing by value from front to back, so
the front index always holds the current window's maximum. For each new element: first, evict
from the *back* any indices whose value is ≤ the incoming value — they're permanently
dominated and can never be the max again while the new, later, larger value remains in the
window. Push the new index onto the back. Then evict from the *front* any index that has
fallen outside the window (`index <= currentIndex - k`). The front of the deque is then the
answer for the current window. This is O(n) total, since — exactly like the monotonic stack —
each index is pushed once and popped at most once (from either end) over the whole run. It
beats the naive O(n·k) rescan-every-window approach and the O(n log k) max-heap-with-lazy-
deletion approach.

```java
static int[] maxSlidingWindow(int[] a, int k) {
    Deque<Integer> dq = new ArrayDeque<>();
    int[] res = new int[a.length - k + 1];
    for (int i = 0; i < a.length; i++) {
        while (!dq.isEmpty() && a[dq.peekLast()] <= a[i]) dq.pollLast();
        dq.addLast(i);
        if (dq.peekFirst() <= i - k) dq.pollFirst();
        if (i >= k - 1) res[i - k + 1] = a[dq.peekFirst()];
    }
    return res;
}
```

*Follow-up: "Why a deque and not a stack — what does the stack version of this problem lack?"*
— The monotonic stack pattern only ever evicts and reads from one end. Here you need to evict
from the *back* for dominance (a newer, bigger value invalidates older smaller candidates) and
*independently* evict from the *front* for expiry (an old index leaving the window, regardless
of its value) — two unrelated eviction rules operating on opposite ends simultaneously, which
only a deque supports.

*Follow-up: "Could you use a max-heap instead?"* — Yes, storing `(value, index)` pairs, but
you can't efficiently remove an arbitrary expired element from a heap, so you do "lazy
deletion" — pop from the top and discard entries whose index has expired before trusting the
next top as the true max. That works but costs O(log k) per operation instead of the deque's
O(1) amortized, and needs the extra expired-check logic on every read.

**Q: Evaluate Reverse Polish Notation (postfix). What about a general calculator with
parentheses and precedence?**

RPN evaluation is a single left-to-right scan with one stack: a number token pushes its
value; an operator token pops its two operands and pushes the computed result. At the end
exactly one value remains on the stack — the answer. The one common bug is operand order for
non-commutative operators (`-`, `/`): since the *second* operand was pushed most recently, it
must be popped *first* — pop `b` then `a`, and compute `a op b`, not `b op a`.

```java
static int evalRPN(String[] tokens) {
    Deque<Integer> stack = new ArrayDeque<>();
    for (String t : tokens) {
        switch (t) {
            case "+" -> stack.push(stack.pop() + stack.pop());
            case "*" -> stack.push(stack.pop() * stack.pop());
            case "-" -> { int b = stack.pop(), a = stack.pop(); stack.push(a - b); }
            case "/" -> { int b = stack.pop(), a = stack.pop(); stack.push(a / b); }
            default  -> stack.push(Integer.parseInt(t));
        }
    }
    return stack.pop();
}
```
`evalRPN(["4","13","5","/","+"])` computes `13 / 5 = 2` (integer division truncates) then
`4 + 2 = 6`.

For general infix expressions with parentheses and precedence (`2 + 3 * (4 - 1)`), the
standard technique is the **shunting-yard algorithm** or, equivalently, a dual-stack
calculator: one stack of pending values, one stack of pending operators. When a new operator
arrives with precedence ≤ the operator on top of the operator stack, you first "fire" the
pending operator (pop two values, apply it, push the result) before pushing the new one — this
is exactly how a compiler respects `*`/`/` binding tighter than `+`/`-` without needing to
build a full parse tree.

*Follow-up: "What would `evalRPN` do if given malformed input, like an operator with only one
operand on the stack?"* — `stack.pop()` on an empty `ArrayDeque` throws
`NoSuchElementException` — the method as written assumes well-formed RPN and doesn't validate
input; a production implementation would check `stack.size() >= 2` before applying an
operator and throw a clearer error.

**Q: How does recursion relate to a stack, and when would you convert a recursive solution to
an iterative one?**

Every recursive function call pushes a frame — local variables, parameters, and a return
address — onto the JVM's call stack; every return pops one. This is not just an analogy: it's
a literal stack, LIFO, and it's exactly why the deepest, most-recent call always returns
first. It also means any recursive algorithm can be mechanically rewritten as an iterative
loop over an explicit `Deque` that stands in for the call stack, pushing "work to do" and
popping it in the same order recursion would have visited it. You'd actually do this
conversion when recursion depth could realistically exceed the JVM's default stack size — a
skewed, unbalanced binary tree (Phase 7) or a long path in a graph DFS (Phase 9) processed
recursively can throw `StackOverflowError`, while the same traversal written iteratively with
an explicit `Deque` handles arbitrarily deep input, bounded only by heap memory rather than
the call stack.

*Follow-up: "Give a concrete example of iterative DFS using an explicit stack, and explain why
it doesn't overflow where the recursive version might."* — Push the root node; loop while the
stack isn't empty, popping a node, processing it, and pushing its unvisited neighbors/children.
It doesn't overflow because the "stack" here is a `Deque` backed by heap-allocated array
storage (which can grow to the size of available heap memory), not the JVM's fixed-size call
stack, which is typically a few MB and has a fixed per-frame overhead regardless of how simple
the recursive function's logic is.

*Follow-up: "Is converting recursion to iteration always a straightforward mechanical
transform?"* — For simple linear/tail-recursive patterns, yes — a single explicit stack
suffices. For recursion with multiple recursive calls per frame combined *after* both return
(like merge sort's combine step, or a recursive tree problem needing post-order results from
both children), you often need to push extra state (which children have been visited, partial
results) onto your explicit stack to reconstruct what would have been "return value used after
the recursive call" — it's still mechanical, but noticeably more bookkeeping than pure DFS
traversal.
</content>
