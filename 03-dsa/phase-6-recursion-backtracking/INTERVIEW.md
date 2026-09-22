<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · hashing](../phase-5-hashing/NOTES.md) | [Phase 7 · trees ➡](../phase-7-trees/NOTES.md)
<!-- /nav -->

# Phase 6 — Recursion & Backtracking: Interview Q&A + Problems

⭐ = asked constantly.

**Q: What is recursion, and what two things does every correct recursive function need?**

Recursion is a function solving a problem by calling itself on a smaller instance of
the same problem. Every correct recursive function needs a **base case** — a condition
answered directly with no further recursive calls — and a **recursive case** — work
done plus a call on strictly smaller input that moves toward the base case. If the base
case is missing, or the recursive case doesn't actually shrink toward it, the function
recurses indefinitely, which in Java manifests as a `StackOverflowError` once the call
stack is exhausted (it's not a compile-time error; the code compiles fine and fails at
runtime).

```java
static long factorial(int n) { return n <= 1 ? 1 : n * factorial(n - 1); }
```

Here `n <= 1` is the base case and `n * factorial(n - 1)` is the recursive case; each
call reduces `n` by 1, guaranteeing termination for `n >= 0`.

*Follow-up: what would happen if you called `factorial(-1)`?* It would never hit `n <=
1` in a way that stops it (since `-1 <= 1` is actually true immediately here, so this
particular base case happens to also catch negatives — but a base case like `n == 0`
without the `<=` would recurse forever on negative input and eventually overflow the
stack). This is a good example of why base cases should be written defensively (`<=`
rather than `==`) when the input domain isn't already validated.

---

**Q: Why is each recursive call not "free" — what does it actually cost?**

Each recursive call pushes a new **stack frame** onto the JVM's call stack, holding
that call's local variables, parameters, and return address. This is the same
mechanism used for ordinary (non-recursive) method calls; recursion doesn't get special
treatment. The frame is popped only when that call returns. So recursion depth directly
determines stack space used, and Java provides **no tail-call optimization** — even a
recursive call that is the very last operation in a function (a "tail call") still
allocates a full new frame. Languages like Scheme or Scala can rewrite such calls into
a loop using constant stack space; Java cannot. This means very deep linear recursion
(say, over an input of size 100,000) is a real risk of `StackOverflowError` in Java and
should be rewritten as an explicit loop or an explicit-stack iterative version instead.

*Follow-up: how would you convert a recursive function to an iterative one that avoids
this risk?* Use an explicit `Deque` as your own stack (Phase 4) mirroring what the call
stack would have held, processed with a `while` loop instead of function calls — this
trades JVM-managed stack frames for heap-allocated objects you control, avoiding the
fixed stack-size limit.

---

**Q: Why is naive recursive Fibonacci exponential, and how do you fix it?** ⭐

```java
static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
```

This recurses into two calls per call, and critically, those calls **overlap**:
`fib(n-1)` internally computes `fib(n-2)` again, which the caller is *also* about to
compute directly. Drawing the recursion tree shows `fib(n-2)` computed twice, `fib(n-3)`
computed three times, and so on — the tree roughly doubles in size per level of depth,
giving O(2ⁿ) total calls despite there being only `n` *distinct* subproblems
(`fib(0)`..`fib(n)`). The fix is **memoization**: cache each subproblem's result
(typically in an array or `HashMap` keyed by `n`) the first time it's computed, and
return the cached value on any later request for the same `n`. That collapses the work
to O(n) time (each of the n distinct subproblems computed once) at the cost of O(n)
extra space for the cache. The non-recursive alternative, **tabulation** (build up
`fib[0], fib[1], ..., fib[n]` iteratively bottom-up), achieves the same O(n) time and
can even reduce space to O(1) by keeping only the last two values. This exact
progression — naive recursion → memoized recursion → bottom-up tabulation — is the
subject of Phase 11 (Dynamic Programming).

*Follow-up: what's the difference between memoization and tabulation?* Memoization is
top-down: you keep the natural recursive structure and add a cache check at the top of
the function. Tabulation is bottom-up: you build an explicit table (array) from the
smallest subproblems upward, in a loop, with no recursion at all. Tabulation avoids
recursion's call-stack overhead entirely and often allows further space optimization
(rolling variables), but memoization is usually easier to derive directly from a
recursive brute-force solution.

---

**Q: What determines the space complexity of a recursive solution?**

The maximum recursion **depth** — the number of stack frames alive at once — plus any
additional data structures the recursion carries or builds (like an accumulator list).
Crucially, this is the recursion tree's *height*, not its total node count: even when a
recursion makes exponentially many total calls (like naive Fibonacci's O(2ⁿ)), only one
root-to-leaf path is on the stack at any instant, because sibling branches are explored
one at a time and each frame is popped before the next sibling's frame is pushed. So
naive `fib(n)` is O(2ⁿ) time but only O(n) space. Linear recursion (one recursive call
per call, like `factorial`) is O(n) depth and O(n) space. Divide-and-conquer recursion
that halves its input each call (like merge sort) is only O(log n) depth. Deep or
unbounded-depth recursion risks `StackOverflowError` and is the signal to convert to an
explicit-stack iterative version.

*Follow-up: for a backtracking solution with branching factor b and depth d, what's the
time and space complexity?* Time is roughly O(bᵈ) (total nodes in the recursion tree,
modulo pruning that cuts this down in practice); space is O(d) — the depth of the
current path on the stack, plus whatever the state representation costs to carry
(e.g., a `used[]` array or a partial list of length up to `d`).

---

**Q: What is backtracking, and what's the template?** ⭐⭐

Backtracking is recursion that builds a candidate solution incrementally, one choice at
a time, and abandons ("backtracks" on) a partial candidate the moment it can no longer
lead to a valid solution. The universal template:

```java
void backtrack(State state) {
    if (isSolution(state)) { record(new State(state)); return; }
    for (Choice choice : choicesFrom(state)) {
        if (!isValid(choice, state)) continue;   // prune
        apply(choice, state);     // choose
        backtrack(state);         // explore
        undo(choice, state);      // un-choose — restores state for the next sibling
    }
}
```

The `undo` step is what makes backtracking correct: without it, mutations from one
branch would leak into the next sibling's exploration. A subtlety worth calling out:
when a solution is found, you must **record a copy** of the current state
(`new ArrayList<>(cur)`), not the live reference — the working list/array keeps being
mutated by subsequent choose/un-choose steps, so a stored live reference would end up
pointing at a mutated (often empty) collection by the time you inspect the results.

*Follow-up: give an example of a bug caused by forgetting the copy step.* If
`res.add(cur)` is used instead of `res.add(new ArrayList<>(cur))` inside a subsets or
permutations helper, every element of the final `res` list will actually be the *same*
list object; by the time the recursion finishes, `cur` has been backtracked all the way
to empty, so every "result" in `res` appears empty too.

---

**Q: Generate all subsets of a set.** ⭐

For each element (processed by index), branch into two recursive calls: one where the
element is included in the running list, one where it's excluded. A leaf is reached
once every index has had a decision made, at which point the running list is a complete
subset — record a copy of it. This produces `2ⁿ` subsets for `n` elements.

```java
static void subsetsHelper(int[] a, int i, List<Integer> cur, List<List<Integer>> res) {
    if (i == a.length) { res.add(new ArrayList<>(cur)); return; }
    cur.add(a[i]);
    subsetsHelper(a, i + 1, cur, res);
    cur.remove(cur.size() - 1);
    subsetsHelper(a, i + 1, cur, res);
}
// subsets([1,2,3]).size() == 8
```

An equivalent, non-recursive approach: iterate a bitmask `mask` from `0` to `2ⁿ − 1`,
and for each mask include `a[j]` whenever bit `j` is set. Same O(2ⁿ) complexity, no
recursion needed.

*Follow-up: how would you generate subsets of a specific size k only?* Add a check to
only recurse into the "include" branch if `cur.size() < k`, and only record at a leaf
when `cur.size() == k` — or equivalently, reuse the combinations template from the next
question, since "subsets of size k" and "combinations choose k" are the same thing.

---

**Q: Generate all permutations.** ⭐⭐

For each position in the output, try every element that hasn't been used yet in the
current path (tracked via a `boolean[] used` array), mark it used, recurse to fill the
next position, then unmark it before trying the next candidate at this position. This
produces `n!` results in O(n! · n) time (n! permutations, each O(n) to build/copy).

```java
static void permuteHelper(int[] a, boolean[] used, List<Integer> cur, List<List<Integer>> res) {
    if (cur.size() == a.length) { res.add(new ArrayList<>(cur)); return; }
    for (int i = 0; i < a.length; i++) {
        if (used[i]) continue;
        used[i] = true; cur.add(a[i]);
        permuteHelper(a, used, cur, res);
        used[i] = false; cur.remove(cur.size() - 1);
    }
}
// permutations([1,2,3]) -> [[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]
```

With duplicate input values (e.g., `[1,1,2]`), this naive version produces duplicate
output permutations. Fix: sort the input first, then within a single recursion level
skip a candidate `a[i]` when `a[i] == a[i-1]` and `a[i-1]` is not currently used — this
ensures each distinct *value* only starts a new branch once per level, rather than each
duplicate index independently starting an (identical) branch.

*Follow-up: why does sorting matter for the duplicate-skipping check?* The skip
condition (`a[i] == a[i-1]` where `a[i-1]` is unused) only correctly identifies
"siblings with the same value" when equal values are adjacent — which sorting
guarantees. Without sorting, equal values could be scattered and the adjacency check
would miss most duplicates.

---

**Q: What's the difference between subsets, permutations, and combinations, both
conceptually and in how the recursion is written?**

Subsets: order doesn't matter, and each output can be any size (every element
independently in or out) — recursion branches include/exclude per index, `2ⁿ` results.
Permutations: order matters, and every output uses *all* `n` elements — recursion picks
an unused element per position via a `used[]` array, `n!` results. Combinations: order
doesn't matter, but output size is fixed at `k` — recursion uses a `start` index (not a
`used[]` array) so that elements are only ever chosen in increasing index order,
guaranteeing each combination is generated exactly once; `C(n,k) = n!/(k!(n-k)!)`
results. The `start`-index technique for combinations is strictly cheaper to reason
about than a `used[]` + post-hoc dedup, because it prevents duplicate orderings from
ever being generated in the first place, rather than generating and filtering them.

---

**Q: Combination sum (elements reusable).** ⭐

Backtrack with a `start` index (to avoid generating `{2,3}` and `{3,2}` as separate
results) but pass `i` — not `i + 1` — as the next `start`, which allows the same
element to be chosen again in the next recursive step. Prune the instant the running
remaining target goes negative, since it can never come back to exactly zero.

```java
static void comboSumHelper(int[] cand, int start, int remain, List<Integer> cur, List<List<Integer>> res) {
    if (remain == 0) { res.add(new ArrayList<>(cur)); return; }
    if (remain < 0) return;   // prune: overshot
    for (int i = start; i < cand.length; i++) {
        cur.add(cand[i]);
        comboSumHelper(cand, i, remain - cand[i], cur, res);   // i, not i+1: reuse allowed
        cur.remove(cur.size() - 1);
    }
}
// combinationSum([2,3,6,7], 7) -> [[2,2,3],[7]]
```

Sorting the candidates ascending first (not shown in the base demo, but a standard
extension) allows an even stronger prune: once `cand[i] > remain`, break the loop
entirely, since every later candidate is at least as large.

*Follow-up: how would you modify this if each candidate could be used at most once
(instead of unlimited reuse)?* Pass `i + 1` instead of `i` as the next start — that's
the entire change; it's the same distinction as combinations-without-reuse vs.
combination-sum-with-reuse.

---

**Q: Solve N-Queens.** ⭐⭐

Process the board row by row, placing exactly one queen per row — this eliminates row
conflicts by construction, so only column and diagonal conflicts need checking. Encode
the board compactly as `cols[row] = column of that row's queen`. For each candidate
column in the current row, check it against every previously placed queen: same column
(`cols[r] == col`), or same diagonal (`Math.abs(cols[r] - col) == Math.abs(r - row)`,
since row and column deltas are equal in magnitude along a diagonal). Only recurse into
a column that passes this `isSafe` check — this is pruning *before* recursing, which is
what keeps the search tractable.

```java
static int placeQueen(int row, int n, int[] cols) {
    if (row == n) return 1;
    int count = 0;
    for (int col = 0; col < n; col++) {
        if (isSafe(row, col, cols)) {
            cols[row] = col;
            count += placeQueen(row + 1, n, cols);
        }
    }
    return count;
}
// nQueens(4) == 2, nQueens(8) == 92 (the classic textbook check)
```

Note there's no explicit "undo" call: since `cols[row]` is overwritten by the next loop
iteration's assignment, storing a new value in the same slot implicitly discards the
previous one — equivalent to backtracking, expressed differently because state is
stored positionally in a fixed-size array rather than appended/removed from a growing
list.

*Follow-up: how would you modify this to return the actual board placements, not just a
count?* Change the return type to `List<int[]>`, and at `row == n`, add
`res.add(cols.clone())` instead of returning `1` — same recursion, count-accumulator
swapped for a copy-and-collect.

*Follow-up: what's the time complexity?* Worst case is bounded by roughly O(n!) due to
the column-uniqueness constraint alone (first queen has n choices, next has at most
n−1 remaining columns, etc.), with the diagonal check pruning it further in practice;
it's exponential regardless, which is why N-Queens is solved for boards up to roughly
n≈20-something in practice, not arbitrary n.

---

**Q: Generate all valid combinations of balanced parentheses for n pairs.**

Track two counters: `open` (opened so far) and `close` (closed so far). At each step,
you may append `'('` only if `open < n` (haven't used all opens yet), and you may
append `')'` only if `close < open` (never close more than has been opened so far —
that's the balance invariant). A leaf is reached once the string length hits `2n`. This
prunes at every step rather than generating-then-validating, so every leaf produced is
automatically valid — no filtering pass needed afterward.

```java
static void parenHelper(StringBuilder sb, int open, int close, int n, List<String> res) {
    if (sb.length() == 2 * n) { res.add(sb.toString()); return; }
    if (open < n) { sb.append('('); parenHelper(sb, open + 1, close, n, res); sb.deleteCharAt(sb.length() - 1); }
    if (close < open) { sb.append(')'); parenHelper(sb, open, close + 1, n, res); sb.deleteCharAt(sb.length() - 1); }
}
// generateParens(3).size() == 5 -> ((())) (()()) (())() ()(()) ()()()
```

*Follow-up: why is `close < open` the correct condition, rather than `close < n`?*
`close < n` would allow closing a paren that was never opened (e.g., producing `")"` as
a valid prefix), which isn't balanced. `close < open` specifically enforces that at
every prefix of the string, the number of closes never exceeds the number of opens —
exactly the definition of a valid (never-negative-running-balance) parenthesis prefix.

---

**Q: How does pruning affect backtracking, and does it change the worst-case
complexity?** ⭐

Pruning cuts off a branch the instant you can prove it cannot lead to a valid solution
— `remain < 0` in combination sum, a failed `isSafe` check in N-Queens, the
`open`/`close` bounds in generate-parentheses. It is what turns an exponential search
space from "technically correct but too slow" into "fast enough in practice." However,
pruning does **not** change the theoretical worst-case complexity: subsets remain
O(2ⁿ), permutations remain O(n!·n), and N-Queens remains exponential in the worst case
— pruning reduces the number of branches actually explored for typical/structured
inputs, not the asymptotic upper bound. This is why backtracking is appropriate for
problems where `n` is small (interview-sized inputs, small boards) or where the
constraints are tight enough that pruning eliminates the large majority of the search
space for realistic inputs.

*Follow-up: can you always find a pruning condition, or are some backtracking problems
inherently unprunable?* Some problems have very weak constraints (e.g., generating all
subsets with no further condition) where there's genuinely nothing to prune — every
branch is a valid partial path — and the full `2ⁿ`/`n!` search is unavoidable. Pruning
helps exactly when the problem has constraints that can be checked incrementally
(partial validity), not just at a complete candidate.

---

**Q: Word search in a grid / Sudoku solver / letter combinations of a phone number —
how do these relate to the backtracking template?**

All are the same choose/explore/undo template with problem-specific state and pruning:
word search marks a grid cell visited, recurses into up to 4 neighbors looking for the
next character, and unmarks the cell on the way back out (so it can be reused by a
different path); Sudoku tries each valid digit (1-9) for the next empty cell, recursing
only into digits that don't violate row/column/box constraints, and backtracks (resets
the cell to empty) when no digit leads to a solution; letter combinations of a phone
number picks one letter per digit position and recurses to the next digit. None of
these are fundamentally new algorithms — recognizing "this is backtracking, what's my
state and what's my pruning condition" is the actual skill.

*Follow-up: in word search, why is marking-and-unmarking the visited cell essential,
and what happens if you forget the unmark step?* The mark prevents the current search
path from reusing the same cell twice (a word can't reuse a letter's grid position).
Forgetting to unmark on backtrack means a cell visited by one failed path stays marked
"visited" for *unrelated* later paths that pass through the same cell — causing valid
words to be incorrectly rejected as not found.

---

**Q: Subsets/permutations WITH duplicates — how do you avoid duplicate outputs?**

Sort the input first (so equal values become adjacent), then within a single recursion
level, skip a candidate that equals its immediately preceding sibling candidate at that
same level — for subsets/combinations this is `i > start && a[i] == a[i-1]`; for
permutations it's skipping `a[i] == a[i-1]` when `a[i-1]` is currently unused. The
intuition: among several equal values, only the *first* one at a given decision point
should be allowed to start a new branch; any subsequent equal value would just
regenerate a branch identical to one already explored.

*Follow-up: why check `a[i-1]` is unused, specifically, in the permutations version
rather than just `a[i] == a[i-1]`?* Because in permutations, `a[i-1]` being "used"
means it's already part of the *current path* (a valid state — using two `1`s from
`[1,1,2]` at different positions is fine), whereas `a[i-1]` being unused but equal to
`a[i]` at the *same* decision point means you're about to redundantly start an
identical sibling branch. The unused check distinguishes "legitimately reusing an equal
value later in the path" from "trying the same value twice as alternatives right here."

---

**Q: Recursion vs. iteration — trade-offs, and when would you convert one to the
other?**

Recursion is often clearer for problems with a naturally recursive/tree-like structure
— tree and graph traversal, divide-and-conquer, backtracking — because the code mirrors
the problem's own recursive definition. Its cost is stack space proportional to
recursion depth, and in Java specifically, no tail-call optimization means even
"trivially" tail-recursive functions pay this cost. Iteration, using an explicit
`Deque`-based stack or a loop with manual state tracking, gives you full control over
memory (heap-allocated, not limited by the fixed JVM stack size) at the cost of more
verbose, harder-to-read code that no longer directly mirrors the problem's recursive
structure. In practice: convert to iteration when recursion depth could realistically
approach or exceed tens of thousands of frames (e.g., linear recursion over a large
linked list or array), or when you're in a performance-sensitive path where the
per-call overhead of stack frame setup/teardown matters. For backtracking specifically,
recursion is almost always kept as recursion — because depth is bounded by the problem
size (grid dimensions, string length) which is typically small even when the number of
*branches* explored is large.

*Follow-up: give a concrete Java example where you'd be forced to convert recursion to
iteration.* A naive recursive traversal of a linked list with 1,000,000 nodes
(`void print(Node n) { if (n == null) return; print(n.next); System.out.println(n.val);
}`) would need ~1,000,000 stack frames and throw `StackOverflowError` on a default JVM
stack size — the iterative equivalent (a `while (n != null) { ...; n = n.next; }` loop)
uses O(1) extra space and has no such limit.
