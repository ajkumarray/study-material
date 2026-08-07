import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/*
 * Phase 4 — Stacks & Queues
 * Run:  java -ea StackQueuePatterns.java
 *
 * STACK = LIFO (last in, first out) — push/pop/peek at one end. Think a stack
 *         of plates, or the call stack (Java Phase 1.4).
 * QUEUE = FIFO (first in, first out) — add at the back, remove from the front.
 *         Think a line of people, or a task queue.
 *
 * In Java use ArrayDeque for BOTH (Java Phase 3.2 — never the legacy Stack).
 * The interview star of this phase is the MONOTONIC STACK.
 */
public class StackQueuePatterns {

    public static void main(String[] args) {
        // ---- stack basics: balanced parentheses ----
        assert validParens("()[]{}");
        assert validParens("([{}])");
        assert !validParens("(]");
        assert !validParens("([)]");
        assert !validParens("(");

        // ---- min-stack: O(1) getMin ----
        MinStack ms = new MinStack();
        ms.push(3); ms.push(5); ms.push(2); ms.push(2);
        assert ms.getMin() == 2;
        ms.pop(); assert ms.getMin() == 2;
        ms.pop(); assert ms.getMin() == 3;

        // ---- monotonic stack: next greater element ----
        assert Arrays.equals(nextGreater(new int[]{2, 1, 2, 4, 3}), new int[]{4, 2, 4, -1, -1});
        assert Arrays.equals(nextGreater(new int[]{5, 4, 3, 2}), new int[]{-1, -1, -1, -1});

        // ---- monotonic stack: largest rectangle in histogram ----
        assert largestRectangle(new int[]{2, 1, 5, 6, 2, 3}) == 10;   // 5,6 -> 5*2
        assert largestRectangle(new int[]{2, 4}) == 4;

        // ---- queue via two stacks ----
        QueueViaStacks q = new QueueViaStacks();
        q.enqueue(1); q.enqueue(2); q.enqueue(3);
        assert q.dequeue() == 1;
        q.enqueue(4);
        assert q.dequeue() == 2;
        assert q.dequeue() == 3;
        assert q.dequeue() == 4;

        // ---- evaluate Reverse Polish Notation ----
        assert evalRPN(new String[]{"2", "1", "+", "3", "*"}) == 9;   // (2+1)*3
        assert evalRPN(new String[]{"4", "13", "5", "/", "+"}) == 6;  // 4 + 13/5

        System.out.println("All stack/queue tests passed.");
        demo();
    }

    // ============================================================
    // STACK — matching/nesting problems. Push openers, pop on closers.
    // ============================================================
    static boolean validParens(String s) {
        Deque<Character> stack = new ArrayDeque<>();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '(' , '[' , '{' -> stack.push(c);
                case ')' -> { if (stack.isEmpty() || stack.pop() != '(') return false; }
                case ']' -> { if (stack.isEmpty() || stack.pop() != '[') return false; }
                case '}' -> { if (stack.isEmpty() || stack.pop() != '{') return false; }
            }
        }
        return stack.isEmpty();     // leftover openers => unbalanced
    }

    // ============================================================
    // MIN-STACK — support getMin() in O(1). Trick: a second stack that
    // remembers the minimum-so-far at each level.
    // ============================================================
    static class MinStack {
        private final Deque<Integer> stack = new ArrayDeque<>();
        private final Deque<Integer> mins = new ArrayDeque<>();

        void push(int x) {
            stack.push(x);
            mins.push(mins.isEmpty() ? x : Math.min(x, mins.peek()));
        }
        void pop()      { stack.pop(); mins.pop(); }
        int top()       { return stack.peek(); }
        int getMin()    { return mins.peek(); }   // O(1)
    }

    // ============================================================
    // MONOTONIC STACK — a stack kept sorted (here: decreasing). Each element
    // is pushed and popped at most once => O(n) total. The go-to for
    // "next greater/smaller element" and span/histogram problems.
    // ============================================================
    static int[] nextGreater(int[] a) {
        int[] res = new int[a.length];
        Arrays.fill(res, -1);
        Deque<Integer> stack = new ArrayDeque<>();   // holds INDICES, values decreasing
        for (int i = 0; i < a.length; i++) {
            // while current beats the value at the stack's top index, we've
            // found ITS next-greater = a[i]. Resolve and pop.
            while (!stack.isEmpty() && a[i] > a[stack.peek()]) {
                res[stack.pop()] = a[i];
            }
            stack.push(i);
        }
        return res;
    }

    // Largest rectangle in a histogram — monotonic (increasing) stack of
    // indices. When a shorter bar arrives, pop taller bars and compute the
    // rectangle each could form. O(n).
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

    // ============================================================
    // QUEUE VIA TWO STACKS — reversing twice restores FIFO order.
    // Amortized O(1) per operation (each element moves at most once).
    // ============================================================
    static class QueueViaStacks {
        private final Deque<Integer> in = new ArrayDeque<>();    // for enqueue
        private final Deque<Integer> out = new ArrayDeque<>();   // for dequeue

        void enqueue(int x) { in.push(x); }

        int dequeue() {
            if (out.isEmpty()) {                 // only refill when empty
                while (!in.isEmpty()) out.push(in.pop());   // reverse in -> out
            }
            return out.pop();
        }
    }

    // Evaluate Reverse Polish Notation — operands push, operators pop two.
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

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("validParens(\"([{}])\")               -> " + validParens("([{}])"));
        System.out.println("nextGreater([2,1,2,4,3])            -> " + Arrays.toString(nextGreater(new int[]{2,1,2,4,3})));
        System.out.println("largestRectangle([2,1,5,6,2,3])     -> " + largestRectangle(new int[]{2,1,5,6,2,3}));
        System.out.println("evalRPN([\"2\",\"1\",\"+\",\"3\",\"*\"])       -> " + evalRPN(new String[]{"2","1","+","3","*"}));
    }
}
