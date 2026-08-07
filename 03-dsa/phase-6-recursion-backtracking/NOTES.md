<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · hashing](../phase-5-hashing/NOTES.md) | [Phase 7 · trees ➡](../phase-7-trees/NOTES.md)
<!-- /nav -->

# Phase 6 — Recursion & Backtracking: Notes

## 6.1 — Recursion mechanics
A recursive function solves a problem via smaller instances of itself: a **base case** (when to stop) + a **recursive case** (reduce toward the base). Each call pushes a stack frame (Java Phase 1.4); depth too great → `StackOverflowError`. Time = calls × work-per-call; space = max depth. Naive `fib` is O(2ⁿ) because it recomputes overlapping subproblems — the motivation for memoization (Phase 11). Recursion and stacks are interchangeable (Phase 4): any recursion can be rewritten with an explicit stack.

## 6.2 — Backtracking: the template
Backtracking = recursion that builds a candidate incrementally and **abandons** it the instant it can't succeed. The universal shape:
```
backtrack(state):
    if isSolution(state): record(state); return
    for choice in choices(state):
        apply(choice)        // choose
        backtrack(state)     // explore
        undo(choice)         // un-choose  ← the "back"
```
The `undo` step (`cur.remove(cur.size()-1)`, `used[i]=false`) restores state so siblings explore cleanly. **Record a *copy*** of the current state (`new ArrayList<>(cur)`) — the working list keeps mutating.

## 6.3 — The canonical problems (all in the code)
- **Subsets** (2ⁿ): for each element, branch include / exclude.
- **Permutations** (n!): for each position, try each *unused* element (a `used[]` array prunes).
- **Combinations** (C(n,k)) and **combination sum**: a `start` index enforces increasing order so `{1,2}` and `{2,1}` aren't both generated; reuse-allowed variants pass `i` instead of `i+1`.
- **N-Queens** (constraint satisfaction): place one queen per row, prune columns/diagonals with `isSafe`; count/collect complete placements. `nQueens(8) = 92` is the classic check.
- **Generate parentheses / word search / Sudoku / subsets-with-dups**: same template with problem-specific *pruning*.

## Pruning — what makes backtracking fast
The exponential search space is only tractable because we **cut branches early**: stop when the partial sum overshoots (`remain < 0`), skip used elements, reject illegal queen placements before recursing, bound with open/close counts for parentheses. Good pruning is the difference between "works" and "times out." Still, worst-case complexity is exponential (subsets O(2ⁿ), permutations O(n!·n)) — backtracking is for problems where n is small or pruning is aggressive.

## Choosing/de-duplicating
- Order matters (arrangements) → permutations. Order doesn't (selections) → combinations/subsets with a `start` index.
- Input has duplicates and you must avoid duplicate outputs → sort first, then skip `i > start && a[i] == a[i-1]`.
- "Count solutions" vs "list solutions" → same recursion; return an int vs collect copies.
