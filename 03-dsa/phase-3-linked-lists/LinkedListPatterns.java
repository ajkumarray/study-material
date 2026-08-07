/*
 * Phase 3 — Linked Lists
 * Run:  java -ea LinkedListPatterns.java
 *
 * A linked list is a chain of nodes, each holding a value + a reference to the
 * next node. Unlike an array, there's NO index math and NO contiguous memory —
 * you can only reach a node by walking from the head. That single fact explains
 * every strength (O(1) insert/delete at a known node) and weakness (O(n) access,
 * poor cache locality — Java Phase 3.2, ArrayList vs LinkedList).
 *
 * The interview toolkit is all POINTER MANIPULATION: dummy heads, reversal, and
 * fast/slow (two-speed) pointers.
 */
public class LinkedListPatterns {

    // The node. In real code this is often a private static nested class.
    static class Node {
        int val;
        Node next;
        Node(int val) { this.val = val; }
    }

    public static void main(String[] args) {
        // reversal
        assert listEquals(reverse(build(1, 2, 3, 4, 5)), build(5, 4, 3, 2, 1));
        assert listEquals(reverse(build(1, 2, 3)), build(3, 2, 1));
        assert listEquals(reverseRecursive(build(1, 2, 3, 4)), build(4, 3, 2, 1));
        assert reverse(null) == null;                       // edge: empty list

        // middle (fast/slow)
        assert middle(build(1, 2, 3, 4, 5)).val == 3;       // odd -> exact middle
        assert middle(build(1, 2, 3, 4)).val == 3;          // even -> second of the two middles

        // cycle detection (Floyd)
        assert !hasCycle(build(1, 2, 3));
        assert hasCycle(withCycle());                       // a list whose tail loops back

        // merge two sorted lists
        assert listEquals(mergeSorted(build(1, 3, 5), build(2, 4, 6)), build(1, 2, 3, 4, 5, 6));
        assert listEquals(mergeSorted(build(1, 2), null), build(1, 2));

        // remove Nth from end
        assert listEquals(removeNthFromEnd(build(1, 2, 3, 4, 5), 2), build(1, 2, 3, 5));
        assert listEquals(removeNthFromEnd(build(1), 1), null);   // remove the only node

        // palindrome
        assert isPalindrome(build(1, 2, 3, 2, 1));
        assert !isPalindrome(build(1, 2, 3));

        System.out.println("All linked-list tests passed.");
        demo();
    }

    // ============================================================
    // REVERSAL — the fundamental pointer exercise. Re-point each node's
    // `next` to the previous node as you walk. O(n) time, O(1) space.
    // ============================================================
    static Node reverse(Node head) {
        Node prev = null, cur = head;
        while (cur != null) {
            Node nextTmp = cur.next;   // 1. remember the rest
            cur.next = prev;           // 2. flip the pointer backward
            prev = cur;                // 3. advance prev
            cur = nextTmp;             // 4. advance cur
        }
        return prev;                   // prev is the new head
    }

    // Recursive reversal — same idea, O(n) stack space. Good to be able to write both.
    static Node reverseRecursive(Node head) {
        if (head == null || head.next == null) return head;   // base case
        Node newHead = reverseRecursive(head.next);
        head.next.next = head;         // the node after me should point back at me
        head.next = null;              // and I become the tail
        return newHead;
    }

    // ============================================================
    // FAST / SLOW POINTERS — one moves 2 steps, one moves 1. When fast
    // reaches the end, slow is at the MIDDLE. The trick behind many problems.
    // ============================================================
    static Node middle(Node head) {
        Node slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        return slow;
    }

    // FLOYD'S CYCLE DETECTION ("tortoise and hare"): if there's a loop, the
    // fast pointer laps the slow one and they MEET. If fast hits null, no cycle.
    // O(n) time, O(1) space (vs a hash set of visited nodes, O(n) space).
    static boolean hasCycle(Node head) {
        Node slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) return true;   // same NODE (reference), not same value
        }
        return false;
    }

    // ============================================================
    // MERGE TWO SORTED LISTS — a DUMMY HEAD avoids special-casing the first
    // node. Weave the two lists by always attaching the smaller head.
    // ============================================================
    static Node mergeSorted(Node a, Node b) {
        Node dummy = new Node(0), tail = dummy;   // dummy: a stand-in "node before the head"
        while (a != null && b != null) {
            if (a.val <= b.val) { tail.next = a; a = a.next; }
            else                { tail.next = b; b = b.next; }
            tail = tail.next;
        }
        tail.next = (a != null) ? a : b;          // attach whatever remains
        return dummy.next;                        // the real head
    }

    // ============================================================
    // REMOVE Nth FROM END — one pass with a GAP of n between two pointers.
    // When the lead hits the end, the trailer is just before the target.
    // ============================================================
    static Node removeNthFromEnd(Node head, int n) {
        Node dummy = new Node(0);
        dummy.next = head;
        Node lead = dummy, trail = dummy;
        for (int i = 0; i < n; i++) lead = lead.next;   // open a gap of n
        while (lead.next != null) { lead = lead.next; trail = trail.next; }
        trail.next = trail.next.next;                    // skip the target node
        return dummy.next;
    }

    // Palindrome check: find middle, reverse the second half, compare. O(n)/O(1).
    static boolean isPalindrome(Node head) {
        Node mid = middle(head);
        Node second = reverse(mid);
        Node p1 = head, p2 = second;
        while (p2 != null) {
            if (p1.val != p2.val) return false;
            p1 = p1.next; p2 = p2.next;
        }
        return true;
    }

    // ---- helpers ----
    static Node build(int... vals) {
        Node dummy = new Node(0), tail = dummy;
        for (int v : vals) { tail.next = new Node(v); tail = tail.next; }
        return dummy.next;
    }

    static Node withCycle() {          // 1->2->3->4->2 (loops back to node 2)
        Node head = build(1, 2, 3, 4);
        Node second = head.next;
        Node tail = head;
        while (tail.next != null) tail = tail.next;
        tail.next = second;            // create the loop
        return head;
    }

    static boolean listEquals(Node a, Node b) {
        while (a != null && b != null) {
            if (a.val != b.val) return false;
            a = a.next; b = b.next;
        }
        return a == null && b == null;
    }

    static String toString(Node head) {
        StringBuilder sb = new StringBuilder();
        for (Node n = head; n != null; n = n.next) sb.append(n.val).append(n.next != null ? "," : "");
        return sb.toString();
    }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("reverse([1,2,3,4,5])      -> " + toString(reverse(build(1, 2, 3, 4, 5))));
        System.out.println("middle([1,2,3,4,5])       -> " + middle(build(1, 2, 3, 4, 5)).val);
        System.out.println("merge([1,3,5],[2,4,6])    -> " + toString(mergeSorted(build(1, 3, 5), build(2, 4, 6))));
        System.out.println("removeNthFromEnd([1..5],2)-> " + toString(removeNthFromEnd(build(1, 2, 3, 4, 5), 2)));
        System.out.println("hasCycle(1->2->3->4->2)   -> " + hasCycle(withCycle()));
    }
}
