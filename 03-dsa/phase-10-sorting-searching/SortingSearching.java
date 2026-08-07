import java.util.Arrays;

/*
 * Phase 10 — Sorting & Searching
 * Run:  java -ea SortingSearching.java
 *
 * You'll call Arrays.sort / Collections.sort in real code (a tuned dual-pivot
 * quicksort / Timsort). But interviews ask you to KNOW the classic algorithms,
 * their complexity, and stability — and, more usefully, the BINARY SEARCH
 * pattern, which appears far beyond "find in a sorted array."
 */
public class SortingSearching {

    public static void main(String[] args) {
        int[] base = {5, 2, 9, 1, 5, 6};

        assert Arrays.equals(mergeSort(base.clone()), new int[]{1, 2, 5, 5, 6, 9});
        assert Arrays.equals(quickSort(base.clone()), new int[]{1, 2, 5, 5, 6, 9});
        assert Arrays.equals(insertionSort(base.clone()), new int[]{1, 2, 5, 5, 6, 9});

        // binary search — classic
        int[] sorted = {1, 3, 5, 7, 9, 11};
        assert binarySearch(sorted, 7) == 3;
        assert binarySearch(sorted, 8) == -1;

        // bounds (first/last position) — the useful generalization
        int[] dups = {1, 2, 2, 2, 3, 4};
        assert firstOccurrence(dups, 2) == 1;
        assert lastOccurrence(dups, 2) == 3;

        // search in a ROTATED sorted array
        int[] rot = {4, 5, 6, 7, 0, 1, 2};
        assert searchRotated(rot, 0) == 4;
        assert searchRotated(rot, 3) == -1;

        // binary search ON THE ANSWER — min eating speed / capacity problems
        assert minEatingSpeed(new int[]{3, 6, 7, 11}, 8) == 4;

        // quickselect: kth smallest in O(n) average
        assert quickSelect(new int[]{7, 10, 4, 3, 20, 15}, 3) == 7;   // 3rd smallest

        System.out.println("All sorting/searching tests passed.");
        demo();
    }

    // ============================================================
    // MERGE SORT — divide in half, sort each, MERGE. Stable, always O(n log n),
    // but O(n) extra space. The reliable "guaranteed n log n" sort.
    // ============================================================
    static int[] mergeSort(int[] a) {
        if (a.length <= 1) return a;
        int mid = a.length / 2;
        int[] left = mergeSort(Arrays.copyOfRange(a, 0, mid));
        int[] right = mergeSort(Arrays.copyOfRange(a, mid, a.length));
        return merge(left, right);
    }
    static int[] merge(int[] l, int[] r) {
        int[] out = new int[l.length + r.length];
        int i = 0, j = 0, k = 0;
        while (i < l.length && j < r.length) out[k++] = (l[i] <= r[j]) ? l[i++] : r[j++];  // <= keeps stability
        while (i < l.length) out[k++] = l[i++];
        while (j < r.length) out[k++] = r[j++];
        return out;
    }

    // ============================================================
    // QUICK SORT — pick a pivot, PARTITION (smaller left, larger right),
    // recurse. Average O(n log n), worst O(n^2) (bad pivots), in-place.
    // Not stable. What Arrays.sort uses for primitives (dual-pivot variant).
    // ============================================================
    static int[] quickSort(int[] a) { quick(a, 0, a.length - 1); return a; }
    static void quick(int[] a, int lo, int hi) {
        if (lo >= hi) return;
        int p = partition(a, lo, hi);
        quick(a, lo, p - 1);
        quick(a, p + 1, hi);
    }
    static int partition(int[] a, int lo, int hi) {
        int pivot = a[hi], i = lo;              // Lomuto partition; pivot = last element
        for (int j = lo; j < hi; j++) if (a[j] < pivot) swap(a, i++, j);
        swap(a, i, hi);
        return i;
    }

    // INSERTION SORT — O(n^2) but simple, stable, and FAST on small/nearly-sorted
    // input (why Timsort/quicksort switch to it for tiny subarrays).
    static int[] insertionSort(int[] a) {
        for (int i = 1; i < a.length; i++) {
            int key = a[i], j = i - 1;
            while (j >= 0 && a[j] > key) a[j + 1] = a[j--];
            a[j + 1] = key;
        }
        return a;
    }

    // ============================================================
    // BINARY SEARCH — halve the search space each step. O(log n). The
    // template (note lo <= hi and mid to avoid overflow) is worth memorizing.
    // ============================================================
    static int binarySearch(int[] a, int target) {
        int lo = 0, hi = a.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;       // avoids (lo+hi) overflow
            if (a[mid] == target) return mid;
            else if (a[mid] < target) lo = mid + 1;
            else hi = mid - 1;
        }
        return -1;
    }

    // Lower/upper bound — find the FIRST/LAST index of a value (handles dups).
    static int firstOccurrence(int[] a, int target) {
        int lo = 0, hi = a.length - 1, res = -1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] == target) { res = mid; hi = mid - 1; }   // keep searching LEFT
            else if (a[mid] < target) lo = mid + 1;
            else hi = mid - 1;
        }
        return res;
    }
    static int lastOccurrence(int[] a, int target) {
        int lo = 0, hi = a.length - 1, res = -1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] == target) { res = mid; lo = mid + 1; }   // keep searching RIGHT
            else if (a[mid] < target) lo = mid + 1;
            else hi = mid - 1;
        }
        return res;
    }

    // Search in a rotated sorted array — one half is always sorted; decide
    // which, and whether the target is inside it. Still O(log n).
    static int searchRotated(int[] a, int target) {
        int lo = 0, hi = a.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] == target) return mid;
            if (a[lo] <= a[mid]) {              // left half sorted
                if (a[lo] <= target && target < a[mid]) hi = mid - 1;
                else lo = mid + 1;
            } else {                            // right half sorted
                if (a[mid] < target && target <= a[hi]) lo = mid + 1;
                else hi = mid - 1;
            }
        }
        return -1;
    }

    // ============================================================
    // BINARY SEARCH ON THE ANSWER — the powerful generalization. When the
    // answer is a number in a range and "is X feasible?" is monotonic, binary
    // search the ANSWER SPACE. Here: minimum speed to finish in h hours.
    // ============================================================
    static int minEatingSpeed(int[] piles, int hours) {
        int lo = 1, hi = Arrays.stream(piles).max().getAsInt();
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (hoursNeeded(piles, mid) <= hours) hi = mid;   // feasible -> try slower
            else lo = mid + 1;                                // too slow -> speed up
        }
        return lo;
    }
    static int hoursNeeded(int[] piles, int speed) {
        int h = 0;
        for (int p : piles) h += (p + speed - 1) / speed;     // ceil division
        return h;
    }

    // QUICKSELECT — kth smallest without full sort. Partition like quicksort but
    // recurse into only ONE side. O(n) average, O(1) space.
    static int quickSelect(int[] a, int k) {
        int lo = 0, hi = a.length - 1, target = k - 1;         // kth smallest = index k-1
        while (lo <= hi) {
            int p = partition(a, lo, hi);
            if (p == target) return a[p];
            else if (p < target) lo = p + 1;
            else hi = p - 1;
        }
        return -1;
    }

    static void swap(int[] a, int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("mergeSort([5,2,9,1,5,6]) -> " + Arrays.toString(mergeSort(new int[]{5,2,9,1,5,6})));
        System.out.println("binarySearch([1,3,5,7,9,11], 7) -> " + binarySearch(new int[]{1,3,5,7,9,11}, 7));
        System.out.println("searchRotated([4,5,6,7,0,1,2], 0) -> " + searchRotated(new int[]{4,5,6,7,0,1,2}, 0));
        System.out.println("minEatingSpeed([3,6,7,11], 8) -> " + minEatingSpeed(new int[]{3,6,7,11}, 8));
    }
}
