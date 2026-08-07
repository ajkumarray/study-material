import java.util.List;

/*
 * Lesson 4.4 — Pattern matching (+ var & text blocks recap)
 *
 * Pattern matching = TEST a value's shape and DECONSTRUCT it in one
 * step. Rolled out across Java 16-21:
 *   16: instanceof patterns          if (o instanceof String s)
 *   21: switch patterns              case String s ->
 *   21: record patterns              case Point(int x, int y) ->
 *
 * Combined with sealed types (2.6) it gives compiler-checked handling
 * of every possible shape — the functional style, natively in Java.
 */
public class PatternMatching {

    // A tiny expression language — THE classic showcase:
    sealed interface Expr permits Num, Add, Mul, Neg { }
    record Num(double value)          implements Expr { }
    record Add(Expr left, Expr right) implements Expr { }
    record Mul(Expr left, Expr right) implements Expr { }
    record Neg(Expr inner)            implements Expr { }

    // Events for the guards demo:
    sealed interface Event permits Click, KeyPress, Scroll { }
    record Click(int x, int y, int button) implements Event { }
    record KeyPress(char key)              implements Event { }
    record Scroll(int amount)              implements Event { }

    public static void main(String[] args) {

        // ============================================================
        // 1. instanceof patterns (the 2.3 upgrade, now official)
        // ============================================================
        System.out.println("=== instanceof pattern ===");

        Object mystery = List.of("a", "b", "c");

        // Old: test, THEN cast, THEN use — three chances to typo.
        // New: test-and-bind in one, scoped to where the test is true:
        if (mystery instanceof List<?> list && !list.isEmpty()) {
            System.out.println("a list of " + list.size() + "  (bound AND usable in the same condition)");
        }

        // Flow scoping even works inverted — early-return style:
        System.out.println(describeLength("kubernetes"));

        // ============================================================
        // 2. switch with TYPE patterns
        // ============================================================
        System.out.println("\n=== switch type patterns ===");

        for (Object o : new Object[]{42, "hello", 3.14, List.of(1, 2)}) {
            String desc = switch (o) {
                case Integer i when i > 40 -> "big int: " + i;     // `when` = GUARD
                case Integer i             -> "int: " + i;
                case String s              -> "string of length " + s.length();
                case List<?> l             -> "list sized " + l.size();
                default                    -> "something else: " + o;
            };  // `default` needed — Object's subtypes are open-ended
            System.out.println("  " + desc);
        }
        // Order matters: guarded case before unguarded, narrower before
        // wider — an unreachable case is a compile error (like catch blocks).
        // And: `case null` is now expressible; without it, switch on null
        // still throws NPE (matching the old behavior).

        // ============================================================
        // 3. RECORD patterns — deconstruction
        // ============================================================
        System.out.println("\n=== record patterns ===");

        Object point = new Point(3, -7);

        // Test the type AND pull out components in one pattern (nestable!):
        if (point instanceof Point(int x, int y)) {
            System.out.println("deconstructed: x=" + x + ", y=" + y);
        }

        Object line = new Line(new Point(0, 0), new Point(3, 4));
        if (line instanceof Line(Point(var x1, var y1), Point(var x2, var y2))) {
            double len = Math.hypot(x2 - x1, y2 - y1);
            System.out.println("nested deconstruction: length=" + len);
        }

        // ============================================================
        // 4. THE PAYOFF: sealed + records + switch = an interpreter
        // ============================================================
        System.out.println("\n=== expression evaluator ===");

        // (3 + 4) * -(2)  as a tree:
        Expr expr = new Mul(
                new Add(new Num(3), new Num(4)),
                new Neg(new Num(2)));

        System.out.println("eval((3+4) * -2) = " + eval(expr));
        System.out.println("show: " + show(expr));
        // Note eval(): EXHAUSTIVE without default (sealed!), recursive,
        // deconstructing — a complete tree interpreter in 6 lines.
        // Pre-Java-21 this needed the Visitor pattern: ~50 lines of
        // accept/visit ceremony. THIS is why pattern matching matters.

        // ============================================================
        // 5. Guards in real event handling
        // ============================================================
        System.out.println("\n=== guards ===");

        Event[] events = {new Click(10, 20, 1), new Click(99, 5, 2),
                          new KeyPress('q'), new Scroll(-3)};
        for (Event e : events) {
            System.out.println("  " + handle(e));
        }

        // ============================================================
        // 6. var & text blocks — the recap corner
        // ============================================================
        System.out.println("\n=== var + text block recap ===");

        // var (1.2): compile-time inference, locals only. Style guide:
        // use when the type is OBVIOUS from the right side, avoid when
        // it would hide it (var x = service.process() — process what?)
        var report = """
                {
                  "phase": 4,
                  "status": "complete"
                }""";
        System.out.println(report);
    }

    record Point(int x, int y) { }
    record Line(Point from, Point to) { }

    static String describeLength(String s) {
        // The negated form: if NOT a match, we returned — after this line
        // the binding is in scope. Flow analysis is smart.
        if (s.length() < 5) return "short";
        return "long: " + s.length() + " chars";
    }

    // The interpreter. Exhaustive: all four Expr shapes handled, no default —
    // add a fifth record to Expr and this METHOD stops compiling. (2.6's
    // promise, delivered.)
    static double eval(Expr e) {
        return switch (e) {
            case Num(double v)          -> v;
            case Add(Expr l, Expr r)    -> eval(l) + eval(r);
            case Mul(Expr l, Expr r)    -> eval(l) * eval(r);
            case Neg(Expr inner)        -> -eval(inner);
        };
    }

    static String show(Expr e) {
        return switch (e) {
            case Num n                  -> String.valueOf(n.value());
            case Add(Expr l, Expr r)    -> "(" + show(l) + " + " + show(r) + ")";
            case Mul(Expr l, Expr r)    -> "(" + show(l) + " * " + show(r) + ")";
            case Neg(Expr inner)        -> "-" + show(inner);
        };
    }

    static String handle(Event e) {
        return switch (e) {
            case Click(int x, int y, int b) when b == 2 -> "right-click at " + x + "," + y;
            case Click(int x, int y, var b)             -> "click at " + x + "," + y;
            case KeyPress(char k) when k == 'q'         -> "quit requested";
            case KeyPress(char k)                       -> "key: " + k;
            case Scroll(int amt)                        -> "scroll by " + amt;
        };
    }
}
