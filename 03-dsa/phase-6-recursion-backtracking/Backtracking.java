import java.util.ArrayList;
import java.util.List;

/*
 * Phase 6 — Recursion & Backtracking
 * Run:  java -ea Backtracking.java
 *
 * RECURSION: a function that solves a problem by solving smaller instances of
 * itself (base case + recursive case; Java Phase 1.4). BACKTRACKING is
 * recursion that BUILDS candidates incrementally and ABANDONS a partial
 * candidate ("backtracks") the moment it can't lead to a solution.
 *
 * The template:
 *     void backtrack(state, choices):
 *         if solution(state): record it; return
 *         for choice in choices:
 *             apply(choice)          // choose
 *             backtrack(...)         // explore
 *             undo(choice)           // un-choose  <-- the "back" in backtracking
 */
public class Backtracking {

    public static void main(String[] args) {
        // recursion warmups
        assert factorial(5) == 120;
        assert fib(10) == 55;

        // subsets: 2^n of them
        assert subsets(new int[]{1, 2, 3}).size() == 8;

        // permutations: n! of them
        assert permutations(new int[]{1, 2, 3}).size() == 6;

        // combinations: choose k from n
        assert combinations(4, 2).size() == 6;      // C(4,2) = 6

        // combination sum (reuse allowed)
        List<List<Integer>> cs = combinationSum(new int[]{2, 3, 6, 7}, 7);
        assert cs.size() == 2;                       // [2,2,3] and [7]

        // N-Queens: number of distinct solutions
        assert nQueens(4) == 2;
        assert nQueens(8) == 92;                      // the classic answer

        // generate valid parentheses
        assert generateParens(3).size() == 5;         // ((())) (()()) (())() ()(()) ()()()

        System.out.println("All backtracking tests passed.");
        demo();
    }

    // ---- pure recursion ----
    static long factorial(int n) { return n <= 1 ? 1 : n * factorial(n - 1); }
    static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }   // O(2^n) - memoize in Phase 11

    // ============================================================
    // SUBSETS — at each element, CHOOSE to include it or not. 2^n leaves.
    // ============================================================
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

    // ============================================================
    // PERMUTATIONS — pick an unused element for each position. n! leaves.
    // ============================================================
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

    // COMBINATIONS — choose k of n; the `start` index avoids duplicates.
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

    // COMBINATION SUM — reuse allowed; prune when the remaining target goes negative.
    static List<List<Integer>> combinationSum(int[] cand, int target) {
        List<List<Integer>> res = new ArrayList<>();
        comboSumHelper(cand, 0, target, new ArrayList<>(), res);
        return res;
    }
    static void comboSumHelper(int[] cand, int start, int remain, List<Integer> cur, List<List<Integer>> res) {
        if (remain == 0) { res.add(new ArrayList<>(cur)); return; }
        if (remain < 0) return;               // prune: overshot
        for (int i = start; i < cand.length; i++) {
            cur.add(cand[i]);
            comboSumHelper(cand, i, remain - cand[i], cur, res);   // i (not i+1): reuse allowed
            cur.remove(cur.size() - 1);
        }
    }

    // ============================================================
    // N-QUEENS — place n queens on n×n so none attack. The canonical
    // constraint-satisfaction backtracking problem. Prune illegal placements.
    // ============================================================
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

    // Generate all valid parenthesis strings of n pairs — prune with open/close counts.
    static List<String> generateParens(int n) {
        List<String> res = new ArrayList<>();
        parenHelper(new StringBuilder(), 0, 0, n, res);
        return res;
    }
    static void parenHelper(StringBuilder sb, int open, int close, int n, List<String> res) {
        if (sb.length() == 2 * n) { res.add(sb.toString()); return; }
        if (open < n) { sb.append('('); parenHelper(sb, open + 1, close, n, res); sb.deleteCharAt(sb.length() - 1); }
        if (close < open) { sb.append(')'); parenHelper(sb, open, close + 1, n, res); sb.deleteCharAt(sb.length() - 1); }
    }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("subsets([1,2,3]) count      -> " + subsets(new int[]{1,2,3}).size());
        System.out.println("permutations([1,2,3])       -> " + permutations(new int[]{1,2,3}));
        System.out.println("combinationSum([2,3,6,7],7) -> " + combinationSum(new int[]{2,3,6,7}, 7));
        System.out.println("nQueens(8) solutions        -> " + nQueens(8));
    }
}
