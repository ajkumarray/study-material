/*
 * Lesson 1.4 — Methods, parameters, overloading
 *
 * A method = a named, reusable block of code. Why they matter:
 *   1. Reuse       — write once, call everywhere (DRY: Don't Repeat Yourself)
 *   2. Naming      — `calculateTax(income)` documents itself
 *   3. Testability — small units you can verify in isolation
 *
 * (All methods here are static so we can call them from main without
 *  objects. Instance methods arrive in Phase 2 — OOP.)
 */
public class Methods {

    public static void main(String[] args) {

        // ============================================================
        // 1. Calling methods
        // ============================================================
        System.out.println("=== basics ===");

        greet("Ajay");                          // void method: does something, returns nothing

        int sum = add(17, 25);                  // returning method: produces a value
        System.out.println("add(17, 25) = " + sum);

        // Terminology: the DEFINITION has PARAMETERS (a, b);
        //              the CALL passes ARGUMENTS (17, 25).

        // ============================================================
        // 2. Early return — guard clauses beat deep nesting
        // ============================================================
        System.out.println("\n=== guard clauses ===");
        System.out.println("describe(42)  -> " + describe(42));
        System.out.println("describe(-7)  -> " + describe(-7));

        // ============================================================
        // 3. PASS-BY-VALUE — the big one
        // ============================================================
        System.out.println("\n=== pass-by-value ===");

        // Java is ALWAYS pass-by-value: the method gets a COPY of what
        // you pass. What's copied differs:
        //   primitive -> the value itself is copied
        //   object    -> the REFERENCE (pointer) is copied; both copies
        //                point at the SAME object

        int x = 10;
        tryToChange(x);
        System.out.println("after tryToChange(x): x = " + x + "   (unchanged — copy of the value)");

        int[] numbers = {10, 20, 30};
        modifyContents(numbers);
        System.out.println("after modifyContents: numbers[0] = " + numbers[0]
                + "   (CHANGED — both references point at the same array)");

        reassignParameter(numbers);
        System.out.println("after reassignParameter: numbers[0] = " + numbers[0]
                + "  (unchanged — reassigning the copied reference does nothing outside)");

        // Mental model: you hand someone a PHOTOCOPY of your house key.
        //  - They CAN rearrange your furniture (mutate the object).
        //  - They CANNOT make you live in a different house
        //    (reassigning their copy of the key changes nothing for you).

        // ============================================================
        // 4. OVERLOADING — same name, different parameter lists
        // ============================================================
        System.out.println("\n=== overloading ===");

        // The compiler picks the version whose parameters match the arguments:
        System.out.println(area(5.0));          // circle:    radius
        System.out.println(area(4.0, 6.0));     // rectangle: width, height
        System.out.println(area(3));            // square:    int side

        // A method's SIGNATURE = name + parameter types (order matters).
        // The return type is NOT part of it — two methods differing only
        // in return type won't compile.

        // ============================================================
        // 5. VARARGS — accept any number of arguments
        // ============================================================
        System.out.println("\n=== varargs ===");

        System.out.println("max()          = " + max());
        System.out.println("max(3)         = " + max(3));
        System.out.println("max(3, 9, 4, 7) = " + max(3, 9, 4, 7));
        // Inside the method, `values` is just an int[].
        // Rules: at most one varargs parameter, and it must come LAST.

        // ============================================================
        // 6. RECURSION — a method calling itself
        // ============================================================
        System.out.println("\n=== recursion ===");

        System.out.println("factorial(5) = " + factorial(5));
        // Every recursion needs:
        //   BASE CASE      — when to stop (n <= 1)
        //   RECURSIVE CASE — a smaller version of the same problem (n - 1)
        // Miss the base case -> StackOverflowError.
        // (This is a warm-up for the DSA track, where recursion is everywhere.)
    }

    // ---- definitions -------------------------------------------------

    static void greet(String name) {
        System.out.println("Hello, " + name + "!");
    }

    static int add(int a, int b) {
        return a + b;
    }

    // Guard clause style: handle the edge case and bail out early.
    // The "happy path" stays unindented and readable.
    static String describe(int n) {
        if (n < 0) {
            return "negative — can't describe";
        }
        return "a fine number: " + n;
    }

    static void tryToChange(int value) {
        value = 999;                     // changes only the local copy
    }

    static void modifyContents(int[] arr) {
        arr[0] = 999;                    // mutates the SHARED array — visible to caller
    }

    static void reassignParameter(int[] arr) {
        arr = new int[]{7, 7, 7};        // repoints the local copy — invisible to caller
    }

    // Three overloads of `area` — same name, different parameter lists:
    static double area(double radius) {
        return Math.PI * radius * radius;
    }

    static double area(double width, double height) {
        return width * height;
    }

    static int area(int side) {
        return side * side;
    }

    // Varargs: callable with 0, 1, or many ints.
    static int max(int... values) {
        if (values.length == 0) {
            return Integer.MIN_VALUE;    // "no arguments" sentinel
        }
        int best = values[0];
        for (int v : values) {
            if (v > best) best = v;
        }
        return best;
    }

    static long factorial(int n) {
        if (n <= 1) {                    // base case
            return 1;
        }
        return n * factorial(n - 1);     // recursive case: smaller problem
    }
}
