import java.util.ArrayList;
import java.util.List;

/*
 * Lesson 3.3 — Generics
 *
 * Generics = parameterized types: write code once, TYPE-SAFELY, for
 * any element type. Before generics (pre-2004), collections held raw
 * Objects and every read needed a cast that could explode at runtime.
 * Generics move that explosion to COMPILE TIME.
 *
 * Convention: T = type, E = element, K/V = key/value, R = result.
 */
public class GenericsTour {

    public static void main(String[] args) {

        // ============================================================
        // 1. Why generics exist
        // ============================================================
        System.out.println("=== why ===");

        List<String> safe = new ArrayList<>();      // <> = diamond: type inferred
        safe.add("java");
        // safe.add(42);                            // compile error — caught EARLY
        String s = safe.get(0);                     // no cast needed

        @SuppressWarnings({"rawtypes", "unchecked"})
        List raw = new ArrayList();                 // RAW type: pre-generics style
        raw.add("java");
        raw.add(42);                                // compiler shrugs...
        try {
            String boom = (String) raw.get(1);      // ...and runtime pays
        } catch (ClassCastException e) {
            System.out.println("raw types -> ClassCastException at RUNTIME (generics prevent this)");
        }

        // ============================================================
        // 2. Generic classes
        // ============================================================
        System.out.println("\n=== generic class ===");

        Pair<String, Integer> entry = new Pair<>("kafka", 9092);
        System.out.println("pair  : " + entry.first() + " -> " + entry.second());

        Pair<Integer, Integer> point = new Pair<>(3, 4);
        System.out.println("point : " + point.first() + "," + point.second());

        // ============================================================
        // 3. Generic methods
        // ============================================================
        System.out.println("\n=== generic method ===");

        // The <T> before the return type declares the method's own parameter;
        // the compiler INFERS it per call:
        System.out.println("firstOf(strings) : " + firstOf(List.of("a", "b")));
        System.out.println("firstOf(ints)    : " + firstOf(List.of(1, 2, 3)));

        // ============================================================
        // 4. BOUNDS — constraining what T can be
        // ============================================================
        System.out.println("\n=== bounded types ===");

        // <T extends Comparable<T>>: only types that know how to compare
        // themselves. Inside the method we may CALL compareTo — the bound
        // is what GRANTS access to those methods.
        System.out.println("max of [3,41,7]        : " + max(List.of(3, 41, 7)));
        System.out.println("max of [kafka, redis]  : " + max(List.of("kafka", "redis")));
        // max(List.of(new Object()));  // compile error: Object isn't Comparable

        // ============================================================
        // 5. WILDCARDS & PECS — the interview boss level
        // ============================================================
        System.out.println("\n=== wildcards / PECS ===");

        // THE PROBLEM: List<Integer> is NOT a List<Number>, even though
        // Integer IS a Number. Generics are INVARIANT. Why? If it were allowed:
        //     List<Number> nums = intList;   // (imagine it compiled)
        //     nums.add(3.14);                // a Double into a List<Integer>!
        // Wildcards restore flexibility, safely:

        List<Integer> ints = List.of(1, 2, 3);
        List<Double> doubles = List.of(1.5, 2.5);

        // ? extends Number = "some unknown SUBTYPE of Number".
        // You can READ Numbers out; you can't add (unknown exact type) -> PRODUCER.
        System.out.println("sum(ints)    : " + sum(ints));
        System.out.println("sum(doubles) : " + sum(doubles));   // one method serves both!

        // ? super Integer = "some unknown SUPERTYPE of Integer".
        // You can WRITE Integers in; reads come out as Object -> CONSUMER.
        List<Number> sink = new ArrayList<>();
        fillWithSquares(sink, 4);
        System.out.println("filled       : " + sink);

        // PECS: Producer Extends, Consumer Super.
        // The JDK is full of it — Collections.copy(List<? super T> dest,
        //                                          List<? extends T> src)

        // ============================================================
        // 6. TYPE ERASURE — what generics compile down to
        // ============================================================
        System.out.println("\n=== erasure ===");

        // At runtime the type parameter is GONE — erased to Object (or the
        // bound). Both lists share one runtime class:
        List<String> a = new ArrayList<>();
        List<Integer> b = new ArrayList<>();
        System.out.println("same runtime class? " + (a.getClass() == b.getClass()));

        // Consequences of erasure:
        //   - no `new T()`, no `T.class`, no `instanceof List<String>`
        //   - no generic arrays: new T[10] doesn't compile
        //   - overloads f(List<String>) / f(List<Integer>) clash — same erasure
        //   - why: backward compatibility with pre-2004 bytecode
    }

    // A generic METHOD: its own <T>, independent of any class.
    static <T> T firstOf(List<T> list) {
        return list.get(0);
    }

    // BOUNDED: T must be self-comparable. (The real JDK signature is the
    // wilder <T extends Comparable<? super T>> — same idea, PECS-hardened.)
    static <T extends Comparable<T>> T max(List<T> list) {
        T best = list.get(0);
        for (T item : list) {
            if (item.compareTo(best) > 0) best = item;   // compareTo exists BECAUSE of the bound
        }
        return best;
    }

    // PRODUCER (we read from it) -> extends
    static double sum(List<? extends Number> numbers) {
        double total = 0;
        for (Number n : numbers) total += n.doubleValue();
        return total;
    }

    // CONSUMER (we write into it) -> super
    static void fillWithSquares(List<? super Integer> target, int upTo) {
        for (int i = 1; i <= upTo; i++) target.add(i * i);
    }
}

/*
 * A generic class: type parameters declared after the name, usable
 * as field/param/return types throughout. (Records can be generic too:
 * record Pair<A, B>(A first, B second) {} — one line. We spell it out
 * here to show the anatomy.)
 */
class Pair<A, B> {
    private final A first;
    private final B second;

    Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    A first()  { return first; }
    B second() { return second; }
}
