<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · hashing](../phase-5-hashing/NOTES.md) | [Phase 7 · trees ➡](../phase-7-trees/NOTES.md)
<!-- /nav -->

# Phase 6 — Recursion & Backtracking: Notes

All code examples in this phase are grounded in `Backtracking.java` in this directory,
which is a self-verifying `java -ea` demo (every claim below was actually run — see the
`demo()` output quoted in each section).

## 1. Recursion mechanics

A **recursive function** solves a problem by calling itself on a smaller version of the
same problem, until it reaches a case simple enough to answer directly. Every correct
recursive function needs exactly two things: a **base case** (the stopping condition,
answered without further recursion) and a **recursive case** (work done, plus a call
on a *smaller* input that moves toward the base case). If either is missing or the
recursive case doesn't actually shrink the problem, the function recurses forever (in
practice, until Java's call stack is exhausted).

### Key Concepts

- **Base case**: The condition under which the function returns a value directly, with
  no further recursive calls. Every recursive path must eventually reach one.
- **Recursive case**: The branch where the function does some work and calls itself on
  a reduced version of the input (e.g., `n - 1`, a shorter array, a smaller subtree).
- **Call stack**: Each recursive call pushes a new **stack frame** onto the JVM call
  stack, holding that call's local variables and return address (see Java Phase 1.4).
  The frame is popped when the call returns. This is the *same* call stack used for
  ordinary method calls — recursion is not special machinery, just repeated calls.
- **StackOverflowError**: If recursion depth grows too large (too many nested frames
  before hitting a base case, or a missing/broken base case), the JVM throws
  `StackOverflowError`. This is a real, observable failure mode for deep or infinite
  recursion — not a compiler warning.
- **No tail-call optimization (TCO)**: Some languages collapse a "tail call" (a
  recursive call that is the very last operation, with nothing left to do after it
  returns) into a loop, using constant stack space. **Java does not do this.** Every
  Java recursive call — even a tail call — consumes a new stack frame. This matters
  for interviews: "can't I just make it tail-recursive to avoid overflow?" — not in
  Java, without rewriting it as an explicit loop.
- **Recursion tree**: A way to visualize recursive calls as a tree, where each node is
  one call and its children are the calls it makes. The tree's **shape** (branching
  factor and depth) determines time complexity (roughly, total nodes) and space
  complexity (the tree's height, since only one root-to-leaf path is on the stack at
  a time).

### Worked example: factorial (linear recursion)

```java
static long factorial(int n) { return n <= 1 ? 1 : n * factorial(n - 1); }

factorial(5);
// Call stack grows: factorial(5) -> factorial(4) -> factorial(3) -> factorial(2) -> factorial(1)
// factorial(1) hits the base case (n <= 1), returns 1
// Unwinding: 1*1=1 -> 2*1=2 -> 3*2=6 -> 4*6=24 -> 5*24=120
assert factorial(5) == 120;   // verified in Backtracking.java
```

Here, `n <= 1` is the base case and `n * factorial(n - 1)` is the recursive case. The
recursion depth is exactly `n`, so this uses O(n) stack frames and does O(n) total work
— one multiplication per call. Time = O(n), space = O(n) (the call stack).

### Worked example: naive Fibonacci (exponential recursion)

```java
static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }   // O(2^n)

assert fib(10) == 55;   // verified
```

Unlike `factorial`, `fib` branches into **two** recursive calls per call. Its recursion
tree looks like:

```
                fib(4)
             /          \
        fib(3)          fib(2)
        /    \           /    \
   fib(2)  fib(1)   fib(1)  fib(0)
   /    \
fib(1) fib(0)
```

Notice `fib(2)` is computed **twice** here (once under `fib(3)`, once directly), and
this duplication compounds — `fib(n-3)` gets recomputed even more times, and so on.
Because the tree roughly doubles in size per level of depth, naive `fib(n)` does O(2ⁿ)
total calls even though there are only `n` *distinct* subproblems. This wasted,
repeated work — recomputing the same subproblem many times — is exactly the motivation
for **memoization** (caching each subproblem's answer the first time it's computed),
which turns this into O(n) time. Memoization and its bottom-up cousin (tabulation) are
covered fully in Phase 11 — Dynamic Programming; this is the bridge between recursion
and DP.

### Time and space complexity of recursion — the general rule

| Recursion shape | Example | Calls (time) | Stack depth (space) |
|---|---|---|---|
| Linear (1 recursive call per call) | `factorial(n)` | O(n) | O(n) |
| Binary, no memo (2 calls per call, no sharing) | naive `fib(n)` | O(2ⁿ) | O(n) — deepest path only |
| Divide-and-conquer (2 calls, input halved) | merge sort | O(n log n) | O(log n) |
| Backtracking (branching factor b, depth d) | permutations, N-Queens | O(bᵈ) or worse | O(d) |

The key space insight: **only one root-to-leaf path is on the call stack at any
instant** — siblings are explored one at a time, and a frame is popped before the next
sibling's frame is pushed. So stack space is proportional to the recursion tree's
*height*, not its total number of nodes, even when the total number of calls (time) is
exponential.

### Why it's useful

Recursion is the natural way to express problems that are themselves defined in terms
of smaller versions of themselves: tree/graph traversal (Phase 7, 9), divide-and-conquer
sorts (Phase 10), and — the subject of the rest of this phase — exhaustively searching a
space of candidate solutions (backtracking). Understanding stack depth as the source of
space cost, and the recursion tree as the source of time cost, is what lets you predict
whether a recursive solution will actually run in time, or blow the stack, before you
even run it.

### Summary — Key Takeaways

- Every recursive function needs a base case (stops) and a recursive case (shrinks
  toward the base case); skip either and you get infinite recursion / stack overflow.
- Java has no tail-call optimization — deep linear recursion still costs O(depth) stack
  frames, so very deep recursion (tens of thousands of frames) risks
  `StackOverflowError` and should be rewritten iteratively.
- A recursion tree's total node count drives time complexity; its height drives space
  complexity (stack depth) — these can differ wildly (e.g., O(2ⁿ) time, O(n) space for
  naive Fibonacci).
- Naive recursive Fibonacci is O(2ⁿ) because it recomputes overlapping subproblems —
  memoization/tabulation (Phase 11) fixes this to O(n).
- Recursion and an explicit stack are interchangeable (Phase 4): anything written
  recursively can be rewritten iteratively with your own `Deque`-based stack.

---

## 2. Backtracking: the universal template

**Backtracking** is recursion with a specific discipline: it builds a candidate
solution incrementally, one choice at a time, and the instant a partial candidate
cannot possibly lead to a valid solution, it **abandons** that branch and "backs up" to
try the next choice. It is exhaustive search with early pruning, structured as
recursion.

### Key Concepts

- **State**: The partial candidate being built (e.g., the current list of chosen
  elements, the queen placements so far, the string built so far).
- **Choose**: Apply one candidate choice to the state (add an element, place a queen,
  append a character).
- **Explore**: Recurse with the updated state, trying to extend it further.
- **Un-choose (the "back" in backtracking)**: After the recursive call returns, undo
  the choice — restore the state to what it was before this choice — so the *next*
  sibling choice starts from a clean slate. This is the step that is easy to forget and
  is the source of most backtracking bugs.
- **Record a copy, not a reference**: When a valid full candidate is found, you must add
  a **copy** of the current working state (`new ArrayList<>(cur)`) to the results list —
  not `cur` itself. `cur` keeps being mutated by later choose/un-choose steps, so storing
  a live reference to it would leave every "recorded" result pointing at the same
  (eventually empty) list.

### The template

```java
void backtrack(State state) {
    if (isSolution(state)) {
        record(new State(state));   // record a COPY
        return;
    }
    for (Choice choice : choicesFrom(state)) {
        if (!isValid(choice, state)) continue;   // prune illegal choices early
        apply(choice, state);        // choose
        backtrack(state);            // explore
        undo(choice, state);         // un-choose
    }
}
```

Every problem in this phase — subsets, permutations, combinations, combination sum,
N-Queens, generate-parentheses — is this exact same shape with a different
`choicesFrom`, `isValid`, and stopping condition.

### Why it's useful

Many real problems ("find all ways to...", "is there a way to...", "count the ways
to...") amount to searching a combinatorially large space of candidates where most
branches are quickly disqualified. Backtracking gives a single, reusable mental
template for all of them, so once you recognize the shape you can adapt it rather than
re-deriving a solution from scratch. It's the standard approach for constraint
satisfaction (N-Queens, Sudoku), combinatorial generation (subsets, permutations,
combinations), and constrained search (word search in a grid, path-finding with
constraints).

### Summary — Key Takeaways

- Backtracking = recursion + choose/explore/undo, with pruning of invalid branches.
- Always record a **copy** of the working state at a solution, never the live
  reference.
- The "undo" step is what makes backtracking correct across sibling branches — without
  it, state from one branch leaks into the next.
- Recognize the template: if a problem says "generate/find/count all ways to...", it is
  very likely backtracking.

---

## 3. Subsets — include/exclude at every element

**Subsets** (the power set) means generating every possible subset of a set of `n`
distinct elements, including the empty set and the full set. There are always exactly
`2ⁿ` subsets, because each of the `n` elements independently is either included or
excluded.

### Key Concepts

- **Branching factor 2**: At each element (index `i`), the recursion branches into
  exactly two calls — one where `a[i]` is included, one where it is not.
- **Leaf = full decision made**: A leaf of the recursion tree is reached once every
  element has had an include/exclude decision made (`i == a.length`), at which point
  the current partial list *is* a complete subset and gets recorded.
- **Alternative: bitmask enumeration**: Since each subset corresponds to one of `2ⁿ`
  binary choices, you can also enumerate subsets iteratively by counting `mask` from
  `0` to `2ⁿ - 1` and, for each mask, including `a[j]` when bit `j` of `mask` is set.
  This avoids recursion/backtracking machinery entirely but is equivalent in complexity.

### Worked example

```java
static List<List<Integer>> subsets(int[] a) {
    List<List<Integer>> res = new ArrayList<>();
    subsetsHelper(a, 0, new ArrayList<>(), res);
    return res;
}
static void subsetsHelper(int[] a, int i, List<Integer> cur, List<List<Integer>> res) {
    if (i == a.length) { res.add(new ArrayList<>(cur)); return; }   // record a copy
    cur.add(a[i]);                        // choose: include a[i]
    subsetsHelper(a, i + 1, cur, res);
    cur.remove(cur.size() - 1);           // un-choose: exclude a[i]
    subsetsHelper(a, i + 1, cur, res);
}

subsets(new int[]{1, 2, 3}).size();
// -> 8   (verified: assert subsets(new int[]{1,2,3}).size() == 8)
// The 8 subsets are: [] [3] [2] [2,3] [1] [1,3] [1,2] [1,2,3]
```

In this example: at index 0 the code first recurses having *included* `1`, exploring
that whole "include 1" half of the tree, then — after that call returns and
`cur.remove(...)` undoes the include — recurses again having *excluded* `1`. Every leaf
of this binary recursion tree is one subset, and there are `2³ = 8` leaves for 3
elements. Depth (and thus stack space) is O(n); total nodes (and thus time, ignoring
the O(n) cost of copying each subset) is O(2ⁿ).

### Why it's useful

Subset generation appears directly (power-set problems) and as a building block inside
other backtracking problems: combination sum is essentially "which subset of the
candidates, with repetition, sums to the target"; many "partition into groups" or
"can you split this array to satisfy X" problems reduce to trying subsets.

### Summary — Key Takeaways

- `n` elements → `2ⁿ` subsets; the recursion branches include/exclude at each element.
- Recording happens at `i == a.length` (full decision made for every element), and must
  copy `cur`.
- Equivalent iterative approach: bitmask from `0` to `2ⁿ - 1`.
- Time is O(2ⁿ · n) if you count the cost of copying each subset (there are `2ⁿ`
  subsets, each up to length `n`); space (excluding output) is O(n) for the recursion
  stack.

---

## 4. Permutations — order matters, every element used once

A **permutation** is an arrangement of *all* `n` elements where order matters. There
are `n!` permutations of `n` distinct elements. Unlike subsets, every permutation uses
every element exactly once — the choice at each position is *which unused element goes
here*, not *include or exclude*.

### Key Concepts

- **`used[]` tracks which elements are already placed**: Since every element must
  appear exactly once, a boolean array (or a `Set`) marks which indices have already
  been chosen for the current path, so they're skipped (pruned) when choosing the next
  position.
- **Branching factor shrinks as the path grows**: At the first position there are `n`
  choices, at the second there are `n - 1` (one is used), and so on — this is exactly
  why the total is `n!` rather than `nⁿ`.
- **Leaf = current list is full length**: `cur.size() == a.length` marks a complete
  permutation.
- **Duplicate elements need extra care**: If the input array has repeated values,
  naively using `used[]` alone produces duplicate permutations (e.g., `[1,1,2]` treated
  positionally still yields the same visible sequence twice). Fix: sort the array
  first, then within one recursion level skip a candidate `a[i]` if it equals `a[i-1]`
  **and** `a[i-1]` is not currently used (i.e., don't start a second branch on the same
  value at the same level).

### Worked example

```java
static List<List<Integer>> permutations(int[] a) {
    List<List<Integer>> res = new ArrayList<>();
    permuteHelper(a, new boolean[a.length], new ArrayList<>(), res);
    return res;
}
static void permuteHelper(int[] a, boolean[] used, List<Integer> cur, List<List<Integer>> res) {
    if (cur.size() == a.length) { res.add(new ArrayList<>(cur)); return; }
    for (int i = 0; i < a.length; i++) {
        if (used[i]) continue;            // prune: skip already-placed elements
        used[i] = true; cur.add(a[i]);    // choose
        permuteHelper(a, used, cur, res); // explore
        used[i] = false; cur.remove(cur.size() - 1);  // un-choose
    }
}

permutations(new int[]{1, 2, 3});
// -> [[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]   (verified output, 6 = 3! permutations)
```

In this example: at the top level the loop tries `i=0` (value `1`) first, marks it
used, and fully explores every permutation *starting* with `1` — that's the
`[1,2,3]` and `[1,3,2]` pair — before undoing (`used[0]=false`, remove `1`) and moving
to `i=1` (value `2`) at the top level. This is why the output is grouped in blocks
starting with each element in turn.

### Comparison: Subsets vs. Permutations vs. Combinations

| | Subsets | Permutations | Combinations (choose k) |
|---|---|---|---|
| Order matters? | No (each element in/out) | Yes | No |
| Every element used? | No (varies per subset) | Yes, all n | No (only k of n) |
| Count | 2ⁿ | n! | C(n,k) = n! / (k!(n−k)!) |
| Recursion state | index `i` (include/exclude) | `used[]` boolean array | `start` index |
| Pruning trick | none needed (all branches valid) | skip `used[i]` | `start` forces increasing indices |

### Why it's useful

Permutations show up whenever "order" is part of the answer: scheduling problems
("in what order should these jobs run"), password/string rearrangement problems,
traveling-salesman-style brute force for small n, and as a building block for problems
like "next permutation" or anagram generation.

### Summary — Key Takeaways

- `n` distinct elements → `n!` permutations; time is O(n! · n) (n! results, each O(n)
  to copy/build).
- A `used[]` array is the standard way to enforce "each element exactly once."
- With duplicate input values, sort first and skip equal siblings at the same
  recursion level to avoid duplicate output permutations.
- Space (excluding output) is O(n): recursion depth plus the `used[]` array.

---

## 5. Combinations and Combination Sum — the `start` index

**Combinations** means choosing `k` elements out of `n` where order does *not* matter
(`{1,2}` and `{2,1}` count as the same combination). **Combination Sum** is a variant:
choose a subset of candidate numbers (optionally with repetition) that sums exactly to
a target.

### Key Concepts

- **`start` index enforces increasing order**: Rather than tracking a `used[]` array,
  combinations pass a `start` parameter and only try indices `>= start` at each level.
  This guarantees each combination is generated with its elements in one canonical
  (increasing-index) order, so `{1,2}` is generated once, never also as `{2,1}`.
- **`i + 1` vs `i` controls reuse**: When recursing after choosing index `i`, passing
  `i + 1` as the next `start` means element `i` cannot be reused (each element chosen at
  most once — plain combinations). Passing `i` instead allows the *same* element to be
  chosen again (combination sum with unlimited reuse of each candidate).
  variant.
- **Pruning on the target**: In combination sum, once the running `remain` (target
  minus sum-so-far) goes negative, that branch can never reach exactly 0 — return
  immediately rather than exploring further. Sorting the candidates first lets you
  prune even earlier (once `cand[i] > remain`, every later, larger candidate would also
  overshoot).

### Worked example — combinations C(n, k)

```java
static List<List<Integer>> combinations(int n, int k) {
    List<List<Integer>> res = new ArrayList<>();
    combineHelper(1, n, k, new ArrayList<>(), res);
    return res;
}
static void combineHelper(int start, int n, int k, List<Integer> cur, List<List<Integer>> res) {
    if (cur.size() == k) { res.add(new ArrayList<>(cur)); return; }
    for (int i = start; i <= n; i++) {
        cur.add(i);
        combineHelper(i + 1, n, k, cur, res);   // i+1: each element once, increasing
        cur.remove(cur.size() - 1);
    }
}

combinations(4, 2).size();
// -> 6   (verified: assert combinations(4, 2).size() == 6)
// C(4,2) = 4!/(2!2!) = 6 -> {1,2} {1,3} {1,4} {2,3} {2,4} {3,4}
```

### Worked example — combination sum (reuse allowed)

```java
static void comboSumHelper(int[] cand, int start, int remain, List<Integer> cur, List<List<Integer>> res) {
    if (remain == 0) { res.add(new ArrayList<>(cur)); return; }
    if (remain < 0) return;               // prune: overshot
    for (int i = start; i < cand.length; i++) {
        cur.add(cand[i]);
        comboSumHelper(cand, i, remain - cand[i], cur, res);   // i (not i+1): reuse allowed
        cur.remove(cur.size() - 1);
    }
}

combinationSum(new int[]{2, 3, 6, 7}, 7);
// -> [[2, 2, 3], [7]]   (verified: cs.size() == 2)
```

In this example: `2+2+3 = 7` is reachable because passing `i` (not `i+1`) lets index 0
(`2`) be picked again immediately after picking it; `7` alone is also a valid
combination. The `remain < 0` prune stops the search the instant a partial sum can no
longer reach exactly `target`, which is what keeps this fast despite the exponential
search space.

### Why it's useful

The `start`-index technique for "no duplicates, no reordering" and the `i` vs `i + 1`
switch for "reuse allowed vs not" are the standard toolkit for any "choose a group /
choose a multiset that satisfies X" interview problem: coin-change-style enumeration,
subset-sum variants, and picking non-overlapping intervals.

### Summary — Key Takeaways

- `start` index (instead of `used[]`) is how you generate combinations without
  duplicate reorderings.
- `i + 1` in the recursive call = "use each element at most once"; `i` = "reuse
  allowed."
- Prune combination-sum branches as soon as `remain < 0` — don't wait to reach a leaf.
- C(n, k) = n! / (k!(n−k)!); the recursion generates exactly that many leaves.

---

## 6. N-Queens — constraint satisfaction with row-by-row placement

**N-Queens** asks: place `n` chess queens on an `n×n` board so that no two attack each
other (no shared row, column, or diagonal). It is the canonical **constraint
satisfaction** backtracking problem — at every step you both make a choice *and* check
it against constraints before committing.

### Key Concepts

- **One queen per row, by construction**: Rather than choosing "row and column" freely,
  the recursion processes rows `0..n-1` in order and picks exactly one column per row.
  This means row conflicts are impossible by construction — you only need to check
  column and diagonal conflicts against already-placed queens.
- **`cols[row]` encodes the whole board compactly**: `cols[row] = column of the queen
  placed in that row` is enough state to represent a partial or full placement — no
  need for a 2D boolean board.
- **`isSafe` checks column and both diagonals**: For a candidate `(row, col)` against
  each previously placed queen `(r, cols[r])`: same column (`cols[r] == col`) or same
  diagonal (`Math.abs(cols[r] - col) == Math.abs(r - row)`, since on a diagonal the row
  and column deltas are equal in magnitude) both disqualify the placement.
- **Pruned before recursing, not after**: `isSafe` is checked *before* the recursive
  call, so illegal placements never even enter the next level of recursion — this is
  what keeps N-Queens tractable despite an `nⁿ`-ish naive search space.
- **Counting vs. collecting**: The demo code counts solutions (`return 1` at a complete
  placement, summed up the call stack) rather than storing full boards; the same
  recursion could instead append a copy of `cols` to a results list if you need the
  actual placements, not just the count.

### Worked example

```java
static int nQueens(int n) {
    return placeQueen(0, n, new int[n]);   // cols[row] = column of the queen in that row
}
static int placeQueen(int row, int n, int[] cols) {
    if (row == n) return 1;                // all rows filled -> one solution
    int count = 0;
    for (int col = 0; col < n; col++) {
        if (isSafe(row, col, cols)) {
            cols[row] = col;               // choose
            count += placeQueen(row + 1, n, cols);   // explore
            // un-choose is implicit: next loop iteration overwrites cols[row]
        }
    }
    return count;
}
static boolean isSafe(int row, int col, int[] cols) {
    for (int r = 0; r < row; r++) {
        int c = cols[r];
        if (c == col || Math.abs(c - col) == Math.abs(r - row)) return false;  // same col or diagonal
    }
    return true;
}

nQueens(4);   // -> 2    (verified)
nQueens(8);   // -> 92   (verified — the classic textbook answer)
```

In this example: for `n = 4`, `placeQueen` tries column 0 for row 0, then searches for a
safe column in row 1 — columns 0 and 1 are unsafe (same column / diagonal), so column 2
or 3 must be tried, and so on down the rows; whenever no column in a row is safe, that
branch returns 0 (no completions) without recursing further, and the caller's loop
moves to its next candidate column. Note there's no explicit `undo` call here: because
`cols[row]` is a fixed-size array indexed by `row`, the *next* iteration of the `for
(col...)` loop simply overwrites `cols[row]` with a new value, which is equivalent to
undoing the previous choice — a subtle but common backtracking optimization when state
is stored positionally rather than appended/removed from a list.

### Why it's useful

N-Queens is the interview shorthand for "can you do constraint-satisfaction
backtracking with early pruning" — the same shape (place a unit while checking growing
constraints against everything placed so far) underlies Sudoku solvers, graph coloring,
and general CSP problems.

### Summary — Key Takeaways

- Process rows in order, one queen per row — eliminates row conflicts by construction.
- `cols[row]` is a compact encoding of the board; `isSafe` checks column + both
  diagonals against every earlier row.
- Pruning happens *before* recursing (`isSafe` gate on the loop), not after — this is
  what makes N-Queens fast in practice despite exponential worst case.
- `nQueens(8) == 92` is the standard sanity check for a correct implementation.
- Worst-case time is still exponential (loosely O(n!) with pruning, O(nⁿ) without);
  N-Queens is tractable for interview-sized `n` because of how aggressively the
  column/diagonal checks prune.

---

## 7. Generate Parentheses — bounding with running counters

**Generate Parentheses** asks for every string of `n` pairs of balanced parentheses.
It's a good example of pruning with *running counters* rather than a `used[]` array or
`start` index.

### Key Concepts

- **Two counters, two guarded branches**: `open` (parens opened so far) and `close`
  (parens closed so far) drive two conditional branches instead of a fixed loop: append
  `'('` only if `open < n` (haven't used all opens yet), append `')'` only if `close <
  open` (never close more than has been opened — this is exactly the "balanced" rule).
- **Leaf condition**: the string is complete (and automatically valid) once its length
  reaches `2 * n`.
- **`StringBuilder` + `deleteCharAt` as choose/un-choose**: Instead of a `List`, the
  state here is a mutable `StringBuilder`; "choose" is `sb.append(...)`, "un-choose" is
  `sb.deleteCharAt(sb.length() - 1)` — same pattern, different container.

### Worked example

```java
static void parenHelper(StringBuilder sb, int open, int close, int n, List<String> res) {
    if (sb.length() == 2 * n) { res.add(sb.toString()); return; }
    if (open < n) { sb.append('('); parenHelper(sb, open + 1, close, n, res); sb.deleteCharAt(sb.length() - 1); }
    if (close < open) { sb.append(')'); parenHelper(sb, open, close + 1, n, res); sb.deleteCharAt(sb.length() - 1); }
}

generateParens(3).size();
// -> 5   (verified: assert generateParens(3).size() == 5)
// The 5 strings: ((())) (()()) (())() ()(()) ()()()
```

In this example: the `close < open` guard is the entire correctness mechanism — it's
what prevents ever generating an invalid prefix like `")"` or `"())"`. Because both
branches are pruned by counters rather than exhaustively tried and checked afterward,
every leaf reached is automatically a valid string; there's no "is this valid?" filter
step needed at the end.

### Why it's useful

The counter-pruning technique (bound choices with running tallies instead of checking
validity after the fact) generalizes to any problem with a "never let X exceed Y"
constraint — bracket matching, budget-constrained selection, and problems where
validity can be checked incrementally rather than only at the end.

### Summary — Key Takeaways

- `open < n` and `close < open` are exactly the balanced-parentheses invariant, checked
  incrementally.
- Because both branches are pre-validated, every leaf is automatically a correct
  answer — no post-hoc filtering needed.
- Same choose/explore/undo template, just with a `StringBuilder` instead of a `List`.

---

## 8. Pruning — what actually makes backtracking fast

**Pruning** is cutting off a branch of the search as soon as you can prove it cannot
lead to a valid solution, instead of exploring it fully and discarding the result at
the end. It is the difference between a backtracking solution that finishes instantly
and one that times out, even though the *asymptotic worst case* is unchanged.

### Key Concepts

- **Prune on infeasibility, not just invalidity**: `remain < 0` in combination sum,
  `!isSafe(...)` in N-Queens, and `close < open` / `open < n` in generate-parentheses
  are all checks that stop a branch *before* it wastes further recursive calls on a
  doomed path.
- **Sort first to prune earlier**: When candidates are sorted (ascending), you can stop
  a loop the moment a candidate alone would overshoot a remaining budget, since every
  later candidate is even larger — this is a common combination-sum/subset-sum
  optimization not shown in the base demo but a natural extension of it.
- **Pruning doesn't change worst-case complexity**: Subsets are still O(2ⁿ) worst case
  and permutations still O(n!·n) worst case — pruning reduces the *actual* branches
  explored for a *given input*, not the theoretical upper bound. Backtracking remains
  appropriate only when `n` is small or the constraints are tight enough that pruning
  eliminates most of the search space in practice.

### Why it's useful

Recognizing where to prune (and proving a prune is correct — i.e., that the pruned
branch truly cannot contain a solution) is the actual skill being tested in backtracking
interview questions; the choose/explore/undo skeleton is nearly identical across
problems, so interviewers focus on whether you can find and justify the pruning
condition for a *new* problem.

### Summary — Key Takeaways

- Pruning cuts branches early based on a provable "this can't work" condition specific
  to the problem (overshoot, conflict, imbalance).
- It changes real-world runtime dramatically without changing worst-case Big-O.
- Sorting inputs is a common enabler of stronger, earlier pruning.
- Backtracking is a tool for exponential search spaces made tractable by aggressive,
  correct pruning — not a way to avoid exponential complexity altogether.

---

## 9. Choosing the right technique — a decision guide

| Question | Answer |
|---|---|
| Does order matter in the output? | Yes → permutations. No → combinations/subsets. |
| Are all elements used in every output? | Yes → permutations. No → subsets/combinations (size varies or is fixed at k). |
| Can an element be reused/repeated? | Yes → recurse with `i` as next start. No → recurse with `i + 1`. |
| Does the input contain duplicate values that must not produce duplicate outputs? | Sort first; skip `i > start && a[i] == a[i-1]` at each recursion level. |
| Do you need to count solutions or list them? | Same recursion either way — return an `int` accumulator (N-Queens style) to count, or collect a copy of state into a results list to list. |
| Is there a hard constraint that can be checked incrementally (not just at the end)? | Prune on it as early as possible (N-Queens `isSafe`, combination-sum `remain < 0`, parens counters). |

### Summary — Key Takeaways

- Subsets/permutations/combinations/combination-sum/N-Queens/generate-parentheses are
  all the *same* backtracking template with a different `choicesFrom`, validity check,
  and leaf condition — recognizing which variant you need is mostly answering "does
  order matter, are all elements used, can elements repeat."
- Recursion and iteration (with an explicit stack, Phase 4) are interchangeable; Java's
  lack of tail-call optimization means very deep linear recursion should prefer
  iteration.
- The bridge from this phase to Phase 11 (Dynamic Programming) is naive recursive
  Fibonacci: once you see repeated subproblems in a recursion tree, memoization turns
  exponential time into polynomial time.
