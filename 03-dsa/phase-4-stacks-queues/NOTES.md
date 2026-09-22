<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · linked lists](../phase-3-linked-lists/NOTES.md) | [Phase 5 · hashing ➡](../phase-5-hashing/NOTES.md)
<!-- /nav -->

# Phase 4 — Stacks & Queues: Notes

Source demo for this phase: `StackQueuePatterns.java` (run with `java -ea StackQueuePatterns.java` —
`-ea` enables the `assert` statements that self-verify every example below).

## 1. The Stack — LIFO

A **stack** is a linear structure where every insertion and removal happens at the *same* end,
called the **top**. The last element pushed is always the first one popped — **L**ast **I**n,
**F**irst **O**ut. It exists because a huge class of problems are naturally "undo the most
recent thing first": matching nested symbols, reversing order, backtracking, and the call
stack itself.

### Key Concepts
- **push(x)**: add `x` to the top. O(1).
- **pop()**: remove and return the top element. O(1). Throws/underflows if empty — always
  check `isEmpty()` first in real code.
- **peek()**: look at the top element without removing it. O(1).
- **LIFO order**: the *most recently added* item is the *first* one you see again. Contrast
  with a queue, where it's the *oldest* item.
- **No random access**: you cannot ask "what's the 3rd item from the bottom?" in O(1) — that's
  the price for O(1) push/pop. If you need that, you don't want a stack.
- **Mental models**: a stack of plates (you only ever take from the top); the JVM call stack,
  where each method invocation pushes a frame and returns pop it (Java Phase 1.4 — this is
  exactly why deep recursion without tail-call optimization overflows the stack).

### Worked Example — push/pop/peek with `ArrayDeque`
```java
Deque<Integer> stack = new ArrayDeque<>();
stack.push(3);
stack.push(5);
stack.push(2);
System.out.println(stack.peek());   // 2  (the last thing pushed)
System.out.println(stack.pop());    // 2  (removed and returned)
System.out.println(stack.pop());    // 5
System.out.println(stack.peek());   // 3  (only one item left)
```
In this example: `push` adds to the front of the deque, and `pop`/`peek` both look at that
same front slot, so every operation is O(1) — no shifting of other elements is ever needed,
unlike inserting at the front of an `ArrayList`.

### Why It's Useful
- **Function call stacks / recursion**: every recursive call is implicitly a stack push; you
  can always convert recursion to an explicit iterative loop with your own `Deque` when
  recursion would blow the JVM's call stack (deep trees/graphs — Phases 7 and 9).
- **Undo/redo**: each action pushes onto an "undo" stack; popping and re-applying it in
  reverse implements undo.
- **DFS (depth-first search)**: an explicit stack (or recursion, which *is* a stack) visits
  the most recently discovered node first — Phase 9.
- **Parsing and matching**: compilers use stacks to match brackets/tags and to evaluate
  expressions (see §5, §9 below).

### Summary
- Stack = LIFO, O(1) push/pop/peek, all at one end.
- No random access — that's the tradeoff for constant-time ends.
- Think "most recent first": undo, DFS, call stack, nested matching.

## 2. The Queue — FIFO

A **queue** is a linear structure where insertion happens at one end (the **back**/**tail**)
and removal happens at the other end (the **front**/**head**). The first element added is the
first one removed — **F**irst **I**n, **F**irst **O**ut. It models anything processed in
arrival order: a line of people, a print queue, a task scheduler, breadth-first search.

### Key Concepts
- **offer(x)** (or `add`/`enqueue`): insert `x` at the back. O(1).
- **poll()** (or `remove`/`dequeue`): remove and return the item at the front. O(1).
- **peek()**: look at the front item without removing it. O(1).
- **FIFO order**: the *oldest* surviving item is always served next — fairness by arrival
  time, unlike a stack's "most recent first."
- **Two active ends**: unlike a stack, a queue actively uses *both* ends (insert at one,
  remove at the other), which is why a naive array-backed queue (shifting everyone left on
  every removal) is O(n) unless you use a circular buffer (see §10).

### Worked Example — `ArrayDeque` as a FIFO queue
```java
Deque<Integer> queue = new ArrayDeque<>();
queue.offer(1);
queue.offer(2);
queue.offer(3);
System.out.println(queue.peek());   // 1  (the first thing offered)
System.out.println(queue.poll());   // 1  (removed from the front)
System.out.println(queue.poll());   // 2
queue.offer(4);
System.out.println(queue);          // [3, 4]  (3 was never removed, 4 just arrived)
```
In this example: `offer` always appends to the back and `poll` always removes from the front,
so items come out in exactly the order they went in — item `3` survives two `poll()` calls
because two *older* items (`1`, `2`) were served ahead of it.

### Why It's Useful
- **BFS (breadth-first search)**: process nodes level by level — Phase 9 relies entirely on
  a queue to guarantee the "closest first" visiting order.
- **Task/job scheduling**: work arrives and is processed in submission order (or with
  priority — see Phase 8's heap-backed priority queue for that variant).
- **Buffering / streaming**: a producer enqueues, a consumer dequeues, decoupling their
  speeds (the conceptual root of message queues like Kafka).
- **Level-order traversal**: printing a tree row by row is a queue-driven BFS.

### Summary
- Queue = FIFO, O(1) at both ends *only if backed by the right structure* (circular buffer
  or linked list — never a plain array with front-shifting).
- Think "arrival order fairness": BFS, scheduling, buffering.

## 3. The Deque — Double-Ended Queue

A **deque** ("deck," short for double-ended queue) allows O(1) insertion and removal at
*both* ends. It is a strict superset of both stack and queue: you can use it as either just
by picking which methods you call.

### Key Concepts
- **addFirst/offerFirst** and **addLast/offerLast**: insert at either end.
- **removeFirst/pollFirst** and **removeLast/pollLast**: remove from either end.
- **peekFirst/peekLast**: inspect either end without removing.
- **As a stack**: use `push` (= `addFirst`) and `pop` (= `removeFirst`) — both act on the
  front, giving LIFO.
- **As a queue**: use `offer` (= `addLast`) and `poll` (= `pollFirst`) — insert at the back,
  remove from the front, giving FIFO.
- **Sliding-window problems**: a deque holding *indices* with a monotonic invariant is the
  standard tool for window-max/min in O(n) (see §11).

### Worked Example — one `Deque`, both disciplines
```java
Deque<Integer> d = new ArrayDeque<>();

// used as a stack (LIFO): push/pop both act on the SAME end
d.push(1); d.push(2); d.push(3);
System.out.println(d.pop());   // 3

// used as a queue (FIFO): offer adds at the back, poll removes from the front
d.clear();
d.offer(1); d.offer(2); d.offer(3);
System.out.println(d.poll());  // 1
```
In this example the *same* `ArrayDeque` instance backs both disciplines — the difference is
purely which method names you call, because `push`/`pop` operate on the front while
`offer`/`poll` insert at the back and remove from the front.

## 4. Choosing a Java Implementation

### Key Concepts
- **`ArrayDeque` — the default choice for stack, queue, and deque.** Internally a resizable
  **circular array** (a ring buffer), so both ends are O(1) with excellent cache locality
  (contiguous memory, unlike a linked structure). This is what `StackQueuePatterns.java` uses
  for every pattern in this phase.
- **`java.util.Stack` — legacy, avoid.** It extends `Vector`, which means every method is
  `synchronized` (unnecessary locking overhead in single-threaded code) and, worse, it's a
  `List` under the hood — nothing stops you from calling `get(i)` and reaching into the
  "middle" of what's supposed to be a stack. It breaks the LIFO abstraction it's meant to
  enforce.
- **`LinkedList` also implements `Deque`.** It works, but every node is a separate heap
  allocation with pointer-chasing overhead and worse cache locality than `ArrayDeque`'s
  contiguous backing array. Prefer `ArrayDeque` unless you specifically need `LinkedList`'s
  other `List` behaviors.
- **`PriorityQueue`** is a different beast entirely — not FIFO, but "smallest/largest first"
  via a heap. Covered in Phase 8; don't reach for it when you just need ordinary FIFO.

### Comparison Table — Stack/Queue/Deque implementation choices in Java

| Implementation | Push/Pop (front) | Enqueue/Dequeue (back) | Random access | Thread-safe | Notes |
|---|---|---|---|---|---|
| `ArrayDeque` | O(1) amortized | O(1) amortized | No | No | **Preferred** — circular array, best cache locality |
| `LinkedList` | O(1) | O(1) | O(n) | No | Extra per-node allocation & pointer overhead |
| `Stack` (legacy) | O(1) | — | O(1) via `get` | Yes (synchronized) | Avoid — broken abstraction, locking cost |
| `Vector` (legacy) | O(1) amortized (as stack) | O(n) at front | O(1) | Yes (synchronized) | Avoid for stacks/queues |
| Plain array, front-shifting queue | — | O(n) per dequeue | O(1) | No | Naive/wrong — always shifts remaining elements |
| Circular array (ring buffer) | O(1) | O(1) | O(1) index math | No | What `ArrayDeque` implements internally |

### Why It's Useful
Knowing *which* Java class backs a stack/queue matters in interviews: naming `ArrayDeque`
and explaining why not `Stack` is a fast, cheap signal of Java fluency, and understanding the
ring-buffer internals lets you reason about amortized costs when resizing occurs (same
doubling-and-copy amortized analysis as `ArrayList` — Phase 2).

### Summary
- Use `ArrayDeque` for stack, queue, *and* deque needs — it's strictly better than the
  alternatives for these roles.
- Never use the legacy `Stack` class; it's synchronized and abstraction-broken.
- `LinkedList` implements `Deque` too, but loses to `ArrayDeque` on cache locality.

## 5. Stack Pattern — Matching / Nesting (Valid Parentheses)

**Definition**: given a string of bracket characters, determine whether every opening
bracket has a matching, correctly-nested closing bracket. This is the canonical
introductory stack problem and the template for any "does this nest correctly" question
(brackets, XML/HTML tags, matching delimiters in a parser).

### Key Concepts
- **Push openers, pop on closers**: when you see an opening bracket, push it. When you see a
  closing bracket, pop the stack and check it's the *matching* opener — if the stack is empty
  or the popped item doesn't match, the string is invalid immediately.
- **End-of-string check**: after processing every character, the stack must be *empty* — a
  leftover opener (e.g. `"("`) means something was never closed.
- **Why a stack and not a counter**: a simple counter works for a *single* bracket type
  (`(` / `)`), but as soon as you mix bracket types (`([{}])` vs `([)]`) you need to know
  *which* opener is waiting to be closed next — that's exactly what the stack's LIFO order
  tracks (the innermost, most-recently-opened bracket must close first).

### Worked Example
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
    return stack.isEmpty();     // leftover openers => unbalanced
}

validParens("()[]{}");   // true  — each pair closes immediately
validParens("([{}])");   // true  — properly nested
validParens("(]");       // false — '(' popped but expected ')' for ']'
validParens("([)]");     // false — '[' is on top when ')' arrives; mismatch
validParens("(");        // false — stack not empty at the end
```
In this example: `"([)]"` fails specifically because brackets must close in LIFO order — when
`)` appears, the *top* of the stack is `[` (pushed after `(`), not `(`, so the mismatch is
caught immediately. This is the same reason a stack (not a queue) is the right structure: the
*most recently opened* bracket is the one that must close *next*.

### Why It's Useful
- Direct real-world analogue: validating balanced syntax in a compiler/parser, matching HTML
  tags, checking JSON/XML nesting.
- The exact same push/pop-on-mismatch shape reappears in tag-matching, path simplification
  (`"a/b/../c"`), and calculator/expression parsing.

### Summary
- Push openers, pop-and-check on closers, verify empty at the end — O(n) time, O(n) space.
- A stack (not a counter) is required as soon as more than one bracket type can nest.

## 6. Stack Pattern — Min-Stack (O(1) `getMin`)

**Definition**: design a stack that supports the usual `push`/`pop`/`top` *and* a `getMin()`
that returns the current minimum element, all in O(1). The interesting constraint is that the
minimum can change as you pop, so you can't just track a single "min so far" variable — it
needs to be able to "roll back" to a previous minimum on `pop()`.

### Key Concepts
- **The trick — a second, parallel stack**: alongside the main data stack, maintain a `mins`
  stack. Every time you push `x` onto the main stack, push `min(x, current min)` onto `mins`.
  Every `pop()` pops *both* stacks in lockstep. `mins.peek()` is then always the minimum of
  everything currently on the main stack.
- **Why it works**: because both stacks grow and shrink together, `mins` at any depth `i`
  always holds "the minimum of the bottom `i+1` elements of the main stack" — exactly the
  value that should reappear as the minimum once you've popped back down to that depth.
- **Space cost**: O(n) extra — one min-stack entry per element. (A space-optimized variant
  only pushes to `mins` when a *new* minimum is set, storing a "how many pushes since this
  min" count instead — trades a little time complexity nuance for less space.)
- **Generalizes**: the same "second stack tracking a running aggregate" idea gives you a
  max-stack, or in principle any O(1)-mergeable aggregate (e.g., running GCD) over "whatever
  is currently on the stack."

### Worked Example
```java
static class MinStack {
    private final Deque<Integer> stack = new ArrayDeque<>();
    private final Deque<Integer> mins = new ArrayDeque<>();

    void push(int x) {
        stack.push(x);
        mins.push(mins.isEmpty() ? x : Math.min(x, mins.peek()));
    }
    void pop()   { stack.pop(); mins.pop(); }
    int top()    { return stack.peek(); }
    int getMin() { return mins.peek(); }   // O(1)
}

MinStack ms = new MinStack();
ms.push(3); ms.push(5); ms.push(2); ms.push(2);
ms.getMin();   // 2   (mins stack: [3, 3, 2, 2] bottom -> top)
ms.pop();      // removes the second 2; mins is now [3, 3, 2]
ms.getMin();   // 2   (the other 2 is still on the stack)
ms.pop();      // removes that 2; mins is now [3, 3]
ms.getMin();   // 3   (min correctly "rolled back" to 3)
```
In this example, after both pops the minimum correctly reverts to `3` — even though we never
explicitly recomputed it, the parallel `mins` stack had already recorded what the minimum
*was* at that depth when it was pushed.

### Why It's Useful
- A frequently-asked "design" question because it forces you to think about maintaining an
  aggregate incrementally rather than by rescanning — a pattern that generalizes to streaming
  statistics broadly.

### Summary
- O(1) all operations, O(n) extra space via a parallel stack tracking running minimum.
- The general lesson: pair a data stack with an aggregate stack that moves in lockstep.

## 7. The Monotonic Stack (the star pattern of this phase)

**Definition**: a **monotonic stack** is a stack whose elements are kept in sorted order
(strictly increasing or strictly decreasing from bottom to top) by popping any elements that
would violate that order *before* pushing the new one. It looks like it should be O(n²)
because of the nested `while` loop inside the `for` loop, but it is actually **O(n)**: across
the *entire* run of the algorithm, every element is pushed at most once and popped at most
once, so the total number of push/pop operations is bounded by `2n`, not `n²`.

### Key Concepts
- **Amortized O(n), not O(n²)**: this is the single most important thing to say out loud in
  an interview — the `while` loop looks expensive per-iteration, but summed across all `n`
  outer-loop iterations, the *total* work done by all inner `while` loops combined is O(n),
  because each element enters and leaves the stack exactly once.
- **Store indices, not values**: keeping indices on the stack (rather than the raw values)
  lets you both look up the value (`a[stack.peek()]`) *and* compute distances/widths from
  index arithmetic — needed for span and width calculations.
- **Decreasing stack -> "next greater" questions**: pop elements smaller than the incoming
  value; whatever just got popped just found its answer.
- **Increasing stack -> width/span questions** (like histogram): pop elements taller than
  the incoming (shorter) value; each pop lets you finalize a rectangle/span that element could
  have anchored.
- **Recognize it when**: the problem asks, for *each* element, "what is the nearest
  greater/smaller element to the left/right" — or anything reducible to that shape (spans,
  histogram rectangles, trapping rain water).

### Worked Example — Next Greater Element
```java
static int[] nextGreater(int[] a) {
    int[] res = new int[a.length];
    Arrays.fill(res, -1);
    Deque<Integer> stack = new ArrayDeque<>();   // holds INDICES, values decreasing
    for (int i = 0; i < a.length; i++) {
        while (!stack.isEmpty() && a[i] > a[stack.peek()]) {
            res[stack.pop()] = a[i];             // current index just resolved the top
        }
        stack.push(i);
    }
    return res;
}

nextGreater(new int[]{2, 1, 2, 4, 3});  // [4, 2, 4, -1, -1]
nextGreater(new int[]{5, 4, 3, 2});     // [-1, -1, -1, -1]  (strictly decreasing: nothing is ever greater)
```
In this example, trace index 1 (`value 1`): it's pushed, and stays on the stack until `i=2`
(`value 2`), which is greater, so `res[1] = 2`. Index 0 (`value 2`) is never popped by index 2
(`2 > 2` is false — strict inequality), but *is* popped by index 3 (`value 4`), giving
`res[0] = 4`. Indices 3 and 4 (`4` and `3`) never find anything greater to their right, so
they keep the default `-1`. The fully decreasing second test case shows the degenerate case
where the stack only ever grows — confirming the `while` body can run zero times per
iteration, which is exactly why the amortized bound holds.

### Worked Example — Largest Rectangle in a Histogram
```java
static int largestRectangle(int[] heights) {
    Deque<Integer> stack = new ArrayDeque<>();
    int best = 0;
    for (int i = 0; i <= heights.length; i++) {
        int h = (i == heights.length) ? 0 : heights[i];   // sentinel 0 flushes the stack
        while (!stack.isEmpty() && h < heights[stack.peek()]) {
            int height = heights[stack.pop()];
            int width = stack.isEmpty() ? i : i - stack.peek() - 1;
            best = Math.max(best, height * width);
        }
        stack.push(i);
    }
    return best;
}

largestRectangle(new int[]{2, 1, 5, 6, 2, 3});  // 10   (bars 5,6 -> height 5, width 2)
largestRectangle(new int[]{2, 4});              // 4
```
In this example the stack stays *increasing*: bar `6` (index 3) sits above bar `5` (index 2)
happily. When index 4 (`height 2`) arrives, it's shorter than both `6` and `5`, so both get
popped and "closed off": bar `6` alone forms a `6 × 1` rectangle, then bar `5` — now that `6`
is gone — extends from index 2 to (not including) index 4, a width of 2, giving `5 × 2 = 10`,
the answer. The trailing sentinel height `0` at `i == heights.length` guarantees every
remaining bar on the stack gets flushed and considered, even if the histogram never shrinks
back down within its actual bounds.

### Comparison Table — Monotonic Stack Problem Family

| Problem | Stack order | What a pop means | Complexity |
|---|---|---|---|
| Next greater element | Decreasing (values) | Popped index's "next greater" is the current value | O(n) |
| Next smaller element | Increasing (values) | Popped index's "next smaller" is the current value | O(n) |
| Daily temperatures (days until warmer) | Decreasing | Distance = current index − popped index | O(n) |
| Stock span (consecutive days ≤ today's price) | Decreasing | Span = current index − new top index | O(n) |
| Largest rectangle in histogram | Increasing | Rectangle anchored at popped bar is finalized | O(n) |
| Trapping rain water (stack variant) | Non-increasing | Popped bar traps water between new top and current | O(n) |
| Sum of subarray minimums | Increasing (with care for ties) | Popped element is the min for a range of subarrays | O(n) |
| Naive "for each element, scan for next greater" | — | — | O(n²) — what the monotonic stack replaces |

### Why It's Useful
This is one of the highest-leverage patterns in interview prep: a family of problems that
*look* like they need nested loops (O(n²)) but collapse to O(n) once you recognize the "next
greater/smaller" shape. Interviewers specifically probe for this recognition because it
demonstrates you're not just brute-forcing.

### Summary
- Monotonic stack: pop violators before pushing, giving each element one push and one pop →
  amortized O(n), even with a nested `while`.
- Decreasing stack -> "next greater" family; increasing stack -> width/span/histogram family.
- Store indices on the stack when you need to compute distances or widths.
- Recognize the pattern: "for each element, the nearest greater/smaller element."

## 8. Queue Pattern — Queue via Two Stacks

**Definition**: implement a FIFO queue using only two LIFO stacks. This tests whether you
understand that reversing an order *twice* restores the original order — the core insight
behind translating between the two disciplines.

### Key Concepts
- **Two stacks, `in` and `out`.** `enqueue` always pushes onto `in`. `dequeue` always pops
  from `out`; if `out` is empty, first drain *all* of `in` into `out` (popping from `in` and
  pushing onto `out`), which reverses the order once — turning `in`'s LIFO order back into
  FIFO order relative to arrival.
- **Only refill `out` when it's empty** — this is the detail that makes the amortized
  analysis work. If you refilled on *every* dequeue, you'd get O(n) per call; refilling only
  when `out` is empty means each element is moved from `in` to `out` **at most once** over
  its lifetime.
- **Amortized O(1)**: any individual `dequeue` *can* cost O(n) (when it triggers a full
  refill), but summed over `n` operations the total work is O(n), so the *average* cost per
  operation is O(1) — the same amortized-analysis idea as dynamic array resizing (Phase 2)
  and the monotonic stack (§7).
- **The mirror problem — stack via two queues** — is also a classic: it's *harder* to make
  efficient, because a queue can't "peek the last-inserted item" the way a stack peeks the
  top; typically one operation (push or pop) ends up O(n) instead of amortized O(1).

### Worked Example
```java
static class QueueViaStacks {
    private final Deque<Integer> in = new ArrayDeque<>();    // for enqueue
    private final Deque<Integer> out = new ArrayDeque<>();   // for dequeue

    void enqueue(int x) { in.push(x); }

    int dequeue() {
        if (out.isEmpty()) {                          // only refill when empty
            while (!in.isEmpty()) out.push(in.pop());  // reverse in -> out
        }
        return out.pop();
    }
}

QueueViaStacks q = new QueueViaStacks();
q.enqueue(1); q.enqueue(2); q.enqueue(3);   // in = [3,2,1] (top->bottom)
q.dequeue();                                 // out refilled to [1,2,3] (top->bottom); returns 1
q.enqueue(4);                                // in = [4]; out still [2,3]
q.dequeue();                                 // out not empty, no refill; returns 2
q.dequeue();                                 // returns 3; out now empty
q.dequeue();                                 // out empty -> refill from in([4]) -> out=[4]; returns 4
```
In this example, notice the first `dequeue()` triggers a refill because `out` starts empty —
that single call does O(n) work reversing all of `in`. But the *next two* `dequeue()` calls
are pure O(1) pops from the already-reversed `out`. The 4th `dequeue()` triggers a second
(cheap, single-element) refill. Across all four dequeues, each of the four enqueued elements
was moved from `in` to `out` exactly once — that's the amortized O(1) guarantee.

### Why It's Useful
- A classic "translate between two disciplines" design question that tests amortized-analysis
  reasoning, not just data structure trivia.
- The "only refill when empty" trick generalizes: lazy/deferred work triggered only when
  needed, amortized across many calls, shows up again in caching and buffering designs.

### Summary
- `in` absorbs enqueues; `out` serves dequeues; refill `out` from `in` only when `out` is
  empty — this makes each element move at most once, giving amortized O(1) dequeue.
- The reverse direction (stack via two queues) is harder — one operation typically stays O(n).

## 9. Expression Evaluation — Reverse Polish Notation and Infix Parsing

**Definition**: **Reverse Polish Notation (RPN)**, also called postfix notation, writes
operators *after* their operands (`2 1 + 3 *` instead of `(2 + 1) * 3`). It requires no
parentheses and no operator-precedence rules to evaluate, which is exactly why calculators
and compilers like it internally — a stack evaluates it in a single linear pass.

### Key Concepts
- **Evaluating RPN**: scan tokens left to right. A number pushes onto the stack. An operator
  pops its operands (**note the order** — for non-commutative ops like `-` and `/`, the
  *first* popped value is the right-hand operand, since it was pushed last), computes the
  result, and pushes it back. The final stack has exactly one value: the answer.
- **Why order matters for `-`/`/`**: if you pop `b` then `a`, the operation is `a - b` (or
  `a / b`), *not* `b - a` — a very common off-by-reversal bug.
- **Infix -> postfix (shunting-yard algorithm)**: converting normal "infix" expressions
  (`2 + 1 * 3`) to postfix uses an *operator* stack that holds operators until a
  lower-or-equal-precedence operator (or a closing paren) forces higher-precedence operators
  to be popped into the output — this is how real parsers and calculators respect
  `*`/`/` binding tighter than `+`/`-`.
- **Basic calculator (infix, direct evaluation)**: a common variant combines a *value* stack
  and an *operator* stack, applying the operator stack's top whenever precedence rules say
  the pending operator should fire before pushing the new one — same shape as shunting-yard,
  fused with evaluation.

### Worked Example
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

evalRPN(new String[]{"2", "1", "+", "3", "*"});   // 9   -- (2+1)*3
evalRPN(new String[]{"4", "13", "5", "/", "+"});  // 6   -- 4 + 13/5 (integer division: 13/5 = 2)
```
In this example, `"2 1 + 3 *"` pushes `2`, then `1`, then `+` pops `1` and `2` (order doesn't
matter for addition) and pushes `3`; then the literal `3` is pushed; then `*` pops both `3`s
and pushes `9`. For `"4 13 5 / +"`, `/` pops `5` then `13` — note `b=5, a=13` — computing
`13 / 5 = 2` (integer division truncates), pushed as `2`; then `+` pops `2` and `4`, pushing
`6`.

### Why It's Useful
- Calculator/expression-evaluator questions are common "design a mini-interpreter" interview
  problems, and RPN evaluation is the simplest fully-worked example of "a stack turns a
  linear token stream into a computed value with no backtracking."
- Directly mirrors how real compilers lower expressions to bytecode (JVM bytecode is itself
  stack-based).

### Summary
- RPN: operands push, operators pop-compute-push; O(n) time, O(n) space.
- Watch operand order for non-commutative operators: pop `b` before `a`, compute `a op b`.
- Infix parsing (shunting-yard / dual-stack calculators) is the same idea plus a
  precedence-driven rule for when to fire pending operators.

## 10. Queue Pattern — Circular Queue / Ring Buffer

**Definition**: a **circular queue** (ring buffer) is a fixed-capacity FIFO that reuses array
slots by wrapping indices around with modulo arithmetic, instead of shifting elements or
growing unbounded. This is what `ArrayDeque` implements internally, and it's the standard
technique whenever you need a bounded, high-throughput queue (streaming buffers, producer/
consumer pipelines).

### Key Concepts
- **Fixed-size backing array** with two index pointers, `head` and `tail` (plus a size
  counter, or a "one slot always empty" convention, to distinguish full from empty).
- **Wrap-around via modulo**: `tail = (tail + 1) % capacity` — when `tail` reaches the end of
  the array it wraps back to index 0, reusing slots that were freed by earlier dequeues,
  instead of shifting all remaining elements left (which would be O(n) per dequeue on a naive
  array-backed queue).
- **O(1) enqueue/dequeue**, bounded, fixed memory — no resizing needed if you accept a fixed
  capacity (or amortized O(1) if you allow doubling like `ArrayDeque` does).
- **`ArrayDeque` already gives you this** — you rarely hand-roll a ring buffer in application
  Java code; you implement one when an interviewer specifically asks "design a circular
  buffer" or "design a bounded queue," to test your understanding of the index arithmetic.

### Why It's Useful
- Bounded producer/consumer buffers (classic concurrency building block, e.g. behind
  `BlockingQueue` implementations).
- Streaming windows: keeping the "last N events" in fixed memory.
- It's the mental model for *why* `ArrayDeque` achieves O(1) at both ends without shifting —
  useful to be able to explain when asked "how does ArrayDeque work internally?"

### Summary
- Circular queue = fixed array + modulo-wrapped head/tail pointers -> O(1) enqueue/dequeue
  without shifting.
- `ArrayDeque` is a circular array under the hood; that's *why* it's O(1) at both ends.

## 11. Deque Pattern — Monotonic Deque (Sliding Window Maximum)

**Definition**: given an array and a window size `k`, find the maximum (or minimum) of every
contiguous window of size `k` as it slides across the array, in O(n) total instead of the
naive O(n·k) (rescanning each window) or O(n log k) (a heap-based approach). This is the
window-max companion to Phase 2's sliding-window techniques, and it uses a **deque of
indices kept monotonic**, generalizing the monotonic-stack idea (§7) to a structure that can
evict from *both* ends.

### Key Concepts
- **Deque of indices, values kept decreasing front-to-back** (for a max query): the front of
  the deque always holds the index of the current window's maximum.
- **Evict from the back before pushing**: when a new value arrives, pop from the *back* any
  indices whose values are ≤ the new value — they can never be the max again as long as the
  new (larger, later) value is in the window, so they're permanently useless.
- **Evict from the front when out of window**: before reading the max, pop from the *front*
  any index that has fallen out of the current window (`index <= currentIndex - k`).
- **Why O(n)**: exactly like the monotonic stack, every index is pushed once and popped at
  most once (from either end), so total deque operations are bounded by O(n) despite the
  per-step "while" evictions.
- **Two active ends is why this needs a deque, not a stack**: eviction happens from the back
  (dominance) *and* the front (expiry) — a plain stack can only ever operate on one end.

### Worked Example (conceptual walk-through using `ArrayDeque<Integer>` of indices)
```java
static int[] maxSlidingWindow(int[] a, int k) {
    Deque<Integer> dq = new ArrayDeque<>();   // indices, values decreasing front->back
    int[] res = new int[a.length - k + 1];
    for (int i = 0; i < a.length; i++) {
        while (!dq.isEmpty() && a[dq.peekLast()] <= a[i]) dq.pollLast();  // evict dominated
        dq.addLast(i);
        if (dq.peekFirst() <= i - k) dq.pollFirst();                     // evict expired
        if (i >= k - 1) res[i - k + 1] = a[dq.peekFirst()];               // front = window max
    }
    return res;
}

maxSlidingWindow(new int[]{1, 3, -1, -3, 5, 3, 6, 7}, 3);
// [3, 3, 5, 5, 6, 7]
```
In this example, when `i=4` (`value 5`) arrives, it's greater than everything currently in
the deque, so the back-eviction loop clears the deque entirely before pushing index 4 — `5`
dominates every earlier candidate in its window. When `i=6` (`value 6`) arrives, index 4
(`value 5`) is evicted from the back since `5 <= 6`; but note index 4 wasn't evicted from the
*front* for being out-of-window until later — the two evictions (back for dominance, front for
expiry) are independent checks.

### Comparison Table — Sliding Window Maximum Approaches

| Approach | Time | Space | Notes |
|---|---|---|---|
| Naive: scan each window for max | O(n·k) | O(1) | Simple but slow for large k |
| Max-heap of (value, index), lazy deletion | O(n log k) | O(n) | Works, but log factor and heap overhead |
| Monotonic deque of indices | **O(n)** | O(k) | Optimal — each index pushed/popped at most once |

### Why It's Useful
- A staple "harder" array/deque interview question precisely because the O(n) solution is
  non-obvious until you've seen the monotonic-deque trick.
- Same pattern underlies streaming analytics: "max value in the last N events" with O(1)
  amortized update.

### Summary
- Monotonic deque: evict smaller/equal values from the back before pushing (dominance);
  evict out-of-window indices from the front before reading (expiry).
- O(n) total — beats O(n·k) brute force and O(n log k) heap approaches.
- Requires a deque (not a stack) because eviction happens at both ends independently.

## 12. Recursion and the Call Stack

**Definition**: every recursive function call is implicitly pushed onto the JVM's **call
stack** — a stack frame holding local variables, parameters, and the return address. This is
not just an analogy: it's *literally* a stack, and it's why every recursive algorithm can be
mechanically rewritten as an iterative loop with an explicit `Deque` standing in for the call
stack.

### Key Concepts
- **Each call = one stack frame push; each return = one pop.** The deepest call is always
  "on top," and returns happen in the reverse order of calls — exactly LIFO.
- **Stack overflow**: recursion that goes too deep (e.g., no base case, or an inherently
  O(n)-deep recursion on large `n`, such as naive recursive tree/graph traversal without
  bounding depth) exhausts the JVM's call stack and throws `StackOverflowError`. This is a
  concrete, practical reason to know how to convert recursion to iteration.
- **Converting recursion to iteration**: replace the implicit call stack with an explicit
  `Deque` holding "work still to do" (e.g., nodes still to visit). This is exactly how
  iterative DFS is implemented in Phase 9, and it's a direct, practical extension of
  everything in this phase.

### Why It's Useful
- Explains *why* deep un-optimized recursion (e.g., processing a very unbalanced/skewed
  binary tree recursively, Phase 7) can crash with `StackOverflowError` while the same logic
  written iteratively with a `Deque` handles the same input fine.
- A frequent interview follow-up after any recursive solution: "can you do this iteratively?"
  — the answer is almost always "yes, with an explicit stack."

### Summary
- Recursion *is* a stack (the call stack) — every call pushes a frame, every return pops one.
- Any recursive algorithm has an iterative equivalent using an explicit `Deque` in place of
  the call stack — useful both as a party trick and as a real fix for stack-overflow risk.

## Overall Complexity Reference — Phase 4

| Structure / Operation | Time | Space | Backing |
|---|---|---|---|
| `ArrayDeque` push/pop (stack use) | O(1) amortized | O(n) | Circular array |
| `ArrayDeque` offer/poll (queue use) | O(1) amortized | O(n) | Circular array |
| Valid parentheses | O(n) | O(n) | Stack |
| Min-stack push/pop/getMin | O(1) each | O(n) | Two parallel stacks |
| Monotonic stack (next greater, histogram, etc.) | O(n) total (amortized) | O(n) | Stack of indices |
| Naive "next greater" (nested loop) | O(n²) | O(1) | — for comparison |
| Queue via two stacks — enqueue | O(1) | O(n) | Two stacks |
| Queue via two stacks — dequeue | O(1) amortized | — | Two stacks |
| RPN evaluation | O(n) | O(n) | Stack |
| Circular queue enqueue/dequeue | O(1) | O(capacity) | Fixed array + modulo |
| Sliding window max (monotonic deque) | O(n) total (amortized) | O(k) | Deque of indices |
| Sliding window max (naive rescan) | O(n·k) | O(1) | — for comparison |
</content>
