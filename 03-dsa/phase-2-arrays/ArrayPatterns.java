import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/*
 * Phase 2 — Arrays & Strings: the core patterns
 * Run:  java -ea ArrayPatterns.java     (-ea enables the assert checks)
 *
 * These four patterns — two pointers, sliding window, prefix sums, and the
 * hash-map trick — solve a huge fraction of array/string interview problems.
 * Each method below is a named, tested technique.
 */
public class ArrayPatterns {

    public static void main(String[] args) {
        // ---- TWO POINTERS ----
        assert Arrays.equals(twoSumSorted(new int[]{2, 7, 11, 15}, 9), new int[]{0, 1});
        assert Arrays.equals(twoSumSorted(new int[]{1, 3, 4, 5, 7}, 12), new int[]{3, 4});
        assert isPalindrome("racecar");
        assert !isPalindrome("hello");
        assert reverseInPlace(new int[]{1, 2, 3, 4})[0] == 4;

        // ---- SLIDING WINDOW ----
        assert maxSumWindow(new int[]{2, 1, 5, 1, 3, 2}, 3) == 9;      // [5,1,3]
        assert longestUniqueSubstring("abcabcbb") == 3;                 // "abc"
        assert longestUniqueSubstring("bbbbb") == 1;                    // "b"
        assert longestUniqueSubstring("pwwkew") == 3;                   // "wke"

        // ---- PREFIX SUM ----
        int[] pre = prefixSums(new int[]{3, 1, 4, 1, 5});
        assert rangeSum(pre, 1, 3) == 6;                                // 1+4+1
        assert subarraysSummingTo(new int[]{1, 1, 1}, 2) == 2;
        assert subarraysSummingTo(new int[]{1, 2, 3}, 3) == 2;          // [1,2] and [3]

        // ---- KADANE (max subarray) ----
        assert maxSubarray(new int[]{-2, 1, -3, 4, -1, 2, 1, -5, 4}) == 6;  // [4,-1,2,1]
        assert maxSubarray(new int[]{-1, -2, -3}) == -1;               // best single

        System.out.println("All array-pattern tests passed.");
        demo();
    }

    // ============================================================
    // TWO POINTERS — start/end converge; O(n) time, O(1) space.
    // Works when the array is SORTED (or you want pairs from both ends).
    // ============================================================
    static int[] twoSumSorted(int[] a, int target) {
        int lo = 0, hi = a.length - 1;
        while (lo < hi) {
            int sum = a[lo] + a[hi];
            if (sum == target) return new int[]{lo, hi};
            else if (sum < target) lo++;        // need bigger -> move left pointer up
            else hi--;                          // need smaller -> move right pointer down
        }
        return new int[]{-1, -1};
    }

    static boolean isPalindrome(String s) {
        int lo = 0, hi = s.length() - 1;
        while (lo < hi) if (s.charAt(lo++) != s.charAt(hi--)) return false;
        return true;
    }

    static int[] reverseInPlace(int[] a) {
        int lo = 0, hi = a.length - 1;
        while (lo < hi) { int t = a[lo]; a[lo++] = a[hi]; a[hi--] = t; }
        return a;
    }

    // ============================================================
    // SLIDING WINDOW — a moving sub-range; expand/contract to maintain a
    // property. Turns many O(n^2) scans into O(n).
    // ============================================================

    // FIXED window of size k: slide, adding the new element and dropping the old.
    static int maxSumWindow(int[] a, int k) {
        int sum = 0;
        for (int i = 0; i < k; i++) sum += a[i];      // first window
        int best = sum;
        for (int i = k; i < a.length; i++) {
            sum += a[i] - a[i - k];                    // O(1) slide, not re-sum
            best = Math.max(best, sum);
        }
        return best;
    }

    // VARIABLE window: grow the right edge; when the constraint breaks,
    // shrink from the left. Classic "longest substring without repeats".
    static int longestUniqueSubstring(String s) {
        Map<Character, Integer> lastSeen = new HashMap<>();
        int start = 0, best = 0;
        for (int end = 0; end < s.length(); end++) {
            char c = s.charAt(end);
            if (lastSeen.containsKey(c) && lastSeen.get(c) >= start) {
                start = lastSeen.get(c) + 1;           // jump past the duplicate
            }
            lastSeen.put(c, end);
            best = Math.max(best, end - start + 1);
        }
        return best;
    }

    // ============================================================
    // PREFIX SUMS — precompute cumulative totals so any range sum is O(1).
    // ============================================================
    static int[] prefixSums(int[] a) {
        int[] pre = new int[a.length + 1];             // pre[i] = sum of first i elements
        for (int i = 0; i < a.length; i++) pre[i + 1] = pre[i] + a[i];
        return pre;
    }

    // sum of a[l..r] inclusive, in O(1) after O(n) precompute
    static int rangeSum(int[] prefix, int l, int r) {
        return prefix[r + 1] - prefix[l];
    }

    // Count subarrays summing to k — prefix sum + hash map (the powerful combo):
    // if prefix[j] - prefix[i] == k, then a[i..j-1] sums to k. Count seen prefixes.
    static int subarraysSummingTo(int[] a, int k) {
        Map<Integer, Integer> count = new HashMap<>();
        count.put(0, 1);                               // empty prefix
        int running = 0, result = 0;
        for (int x : a) {
            running += x;
            result += count.getOrDefault(running - k, 0);   // how many earlier prefixes enable a k-sum ending here
            count.merge(running, 1, Integer::sum);
        }
        return result;
    }

    // ============================================================
    // KADANE — max-sum contiguous subarray in O(n). A tiny DP:
    // "best ending here" = max(this element, this element + best ending at previous).
    // ============================================================
    static int maxSubarray(int[] a) {
        int bestEndingHere = a[0], best = a[0];
        for (int i = 1; i < a.length; i++) {
            bestEndingHere = Math.max(a[i], bestEndingHere + a[i]);
            best = Math.max(best, bestEndingHere);
        }
        return best;
    }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("twoSumSorted([2,7,11,15], 9)         -> " + Arrays.toString(twoSumSorted(new int[]{2,7,11,15}, 9)));
        System.out.println("longestUniqueSubstring(\"pwwkew\")     -> " + longestUniqueSubstring("pwwkew"));
        System.out.println("subarraysSummingTo([1,1,1], 2)       -> " + subarraysSummingTo(new int[]{1,1,1}, 2));
        System.out.println("maxSubarray([-2,1,-3,4,-1,2,1,-5,4]) -> " + maxSubarray(new int[]{-2,1,-3,4,-1,2,1,-5,4}));
    }
}
