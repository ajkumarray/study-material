import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/*
 * Phase 8 — Heaps / Priority Queues
 * Run:  java -ea HeapPatterns.java
 *
 * A BINARY HEAP is a complete binary tree stored in an ARRAY, maintaining the
 * HEAP PROPERTY: in a MIN-heap every parent <= its children (so the smallest
 * is always at the root). It gives:
 *     peek min/max  -> O(1)
 *     insert / poll -> O(log n)   (bubble up / sift down)
 *     build from n  -> O(n)       (heapify)
 * It's the perfect structure for "the top/most-extreme element, repeatedly":
 * top-K, merge-K, scheduling, Dijkstra (Phase 9), running median.
 *
 * Java's PriorityQueue IS a min-heap. We also build one from scratch to show
 * the mechanics.
 */
public class HeapPatterns {

    public static void main(String[] args) {
        // our hand-built min-heap
        MinHeap h = new MinHeap();
        for (int x : new int[]{5, 3, 8, 1, 9, 2}) h.push(x);
        assert h.peek() == 1;
        assert h.pop() == 1 && h.pop() == 2 && h.pop() == 3;   // comes out sorted
        assert h.size() == 3;

        // top-K largest (min-heap of size k)
        assert kLargest(new int[]{3, 1, 5, 12, 2, 11}, 3).equals(List.of(5, 11, 12));

        // kth largest element
        assert kthLargest(new int[]{3, 2, 1, 5, 6, 4}, 2) == 5;

        // top-K frequent
        assert topKFrequent(new int[]{1, 1, 1, 2, 2, 3}, 2).contains(1);

        // running median
        MedianFinder mf = new MedianFinder();
        mf.add(1); mf.add(2); assert mf.median() == 1.5;
        mf.add(3); assert mf.median() == 2.0;

        // JDK PriorityQueue: min-heap by default, max-heap via comparator
        PriorityQueue<Integer> max = new PriorityQueue<>(Collections.reverseOrder());
        max.addAll(List.of(4, 1, 7, 3));
        assert max.poll() == 7;

        System.out.println("All heap tests passed.");
        demo();
    }

    // ============================================================
    // A MIN-HEAP FROM SCRATCH — array-backed complete tree.
    //   parent(i) = (i-1)/2      left(i) = 2i+1      right(i) = 2i+2
    // ============================================================
    static class MinHeap {
        private int[] a = new int[16];
        private int n = 0;

        int size() { return n; }
        int peek() { return a[0]; }              // root = minimum, O(1)

        void push(int x) {
            if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2);
            a[n] = x;
            siftUp(n++);                          // bubble the newcomer up to its spot
        }

        int pop() {
            int min = a[0];
            a[0] = a[--n];                        // move last element to root...
            siftDown(0);                          // ...then sink it to restore the heap
            return min;
        }

        private void siftUp(int i) {
            while (i > 0) {
                int parent = (i - 1) / 2;
                if (a[i] >= a[parent]) break;     // parent already smaller -> done
                swap(i, parent);
                i = parent;
            }
        }

        private void siftDown(int i) {
            while (true) {
                int l = 2 * i + 1, r = 2 * i + 2, smallest = i;
                if (l < n && a[l] < a[smallest]) smallest = l;
                if (r < n && a[r] < a[smallest]) smallest = r;
                if (smallest == i) break;         // heap property holds -> done
                swap(i, smallest);
                i = smallest;
            }
        }

        private void swap(int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }
    }

    // ============================================================
    // TOP-K with a size-K heap — O(n log k), better than sorting's O(n log n)
    // when k << n, and it streams (never holds all n sorted).
    // ============================================================
    static List<Integer> kLargest(int[] a, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();   // keep the k LARGEST
        for (int x : a) {
            minHeap.offer(x);
            if (minHeap.size() > k) minHeap.poll();   // evict the smallest -> top k remain
        }
        List<Integer> res = new java.util.ArrayList<>(minHeap);
        Collections.sort(res);
        return res;
    }

    static int kthLargest(int[] a, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        for (int x : a) {
            minHeap.offer(x);
            if (minHeap.size() > k) minHeap.poll();
        }
        return minHeap.peek();                        // the kth largest sits at the root
    }

    static List<Integer> topKFrequent(int[] a, int k) {
        Map<Integer, Integer> freq = new HashMap<>();
        for (int x : a) freq.merge(x, 1, Integer::sum);
        // min-heap ordered by frequency, size capped at k
        PriorityQueue<Integer> heap = new PriorityQueue<>((x, y) -> freq.get(x) - freq.get(y));
        for (int key : freq.keySet()) {
            heap.offer(key);
            if (heap.size() > k) heap.poll();
        }
        return new java.util.ArrayList<>(heap);
    }

    // ============================================================
    // RUNNING MEDIAN — two heaps: a MAX-heap for the lower half, a MIN-heap for
    // the upper half, kept balanced. Median = a top (odd) or average of tops (even).
    // ============================================================
    static class MedianFinder {
        private final PriorityQueue<Integer> low = new PriorityQueue<>(Collections.reverseOrder()); // max-heap
        private final PriorityQueue<Integer> high = new PriorityQueue<>();                            // min-heap

        void add(int x) {
            low.offer(x);
            high.offer(low.poll());                   // balance: move low's max to high
            if (high.size() > low.size()) low.offer(high.poll());   // keep low >= high in size
        }

        double median() {
            if (low.size() > high.size()) return low.peek();
            return (low.peek() + high.peek()) / 2.0;
        }
    }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("kLargest([3,1,5,12,2,11], 3) -> " + kLargest(new int[]{3,1,5,12,2,11}, 3));
        System.out.println("kthLargest([3,2,1,5,6,4], 2) -> " + kthLargest(new int[]{3,2,1,5,6,4}, 2));
        System.out.println("topKFrequent([1,1,1,2,2,3],2)-> " + topKFrequent(new int[]{1,1,1,2,2,3}, 2));
    }
}
