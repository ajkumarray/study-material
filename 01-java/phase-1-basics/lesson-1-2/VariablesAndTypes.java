/*
 * Lesson 1.2 — Variables, primitive types, operators
 *
 * Java is STATICALLY TYPED: every variable has a type known at compile time,
 * and the compiler rejects code that mixes types illegally. This catches a
 * whole class of bugs before the program ever runs.
 */
public class VariablesAndTypes {

    public static void main(String[] args) {

        // ============================================================
        // 1. THE 8 PRIMITIVE TYPES
        // "Primitive" = a raw value in memory, not an object.
        // ============================================================

        // --- Integer types (whole numbers) ---
        byte  aByte  = 127;                  // 8 bits:  -128 to 127
        short aShort = 32_000;               // 16 bits: ~ +/-32k
        int   anInt  = 2_147_483_647;        // 32 bits: ~ +/-2.1 billion  <- DEFAULT choice
        long  aLong  = 9_000_000_000L;       // 64 bits: huge. Note the L suffix —
                                             // without it, 9000000000 is an int literal and too big!

        // Underscores in literals (like 2_147_483_647) are just for readability.

        // --- Floating-point types (decimals) ---
        float  aFloat  = 3.14f;              // 32 bits. Needs the f suffix.
        double aDouble = 3.141592653589793;  // 64 bits. <- DEFAULT choice for decimals

        // --- Single character ---
        char aChar = 'A';                    // 16 bits, single quotes. Actually a number (Unicode)!

        // --- True/false ---
        boolean isLearning = true;

        System.out.println("=== Primitives ===");
        System.out.println("byte:    " + aByte);
        System.out.println("short:   " + aShort);
        System.out.println("int:     " + anInt);
        System.out.println("long:    " + aLong);
        System.out.println("float:   " + aFloat);
        System.out.println("double:  " + aDouble);
        System.out.println("char:    " + aChar);
        System.out.println("boolean: " + isLearning);

        // String is NOT a primitive — it's an object (a reference type).
        // Notice the capital S: class names are capitalized, primitives aren't.
        String name = "Java";

        // ============================================================
        // 2. CASTING — converting between types
        // ============================================================
        System.out.println("\n=== Casting ===");

        // Widening (small -> big) is automatic and safe: nothing can be lost.
        int small = 42;
        long big = small;          // int -> long, implicit
        double bigger = small;     // int -> double, implicit
        System.out.println("widened int 42 to long " + big + " and double " + bigger);

        // Narrowing (big -> small) needs an explicit cast — you're telling the
        // compiler "I accept the risk of losing data."
        double pi = 3.99;
        int truncated = (int) pi;  // 3, NOT 4 — casting TRUNCATES, it doesn't round
        System.out.println("(int) 3.99 = " + truncated + "  (truncated, not rounded!)");

        // Overflow: values wrap around silently. A classic bug source.
        int maxInt = Integer.MAX_VALUE;
        System.out.println("MAX_VALUE + 1 = " + (maxInt + 1) + "  (wrapped to negative!)");

        // char is secretly a number — you can do arithmetic on it:
        char letter = 'A';
        char next = (char) (letter + 1);
        System.out.println("'A' + 1 = " + next + "  ('A' is Unicode " + (int) letter + ")");

        // ============================================================
        // 3. OPERATORS
        // ============================================================
        System.out.println("\n=== Operators ===");

        int a = 17, b = 5;
        System.out.println("17 + 5  = " + (a + b));
        System.out.println("17 - 5  = " + (a - b));
        System.out.println("17 * 5  = " + (a * b));

        // INTEGER DIVISION: int / int drops the remainder. Huge gotcha.
        System.out.println("17 / 5  = " + (a / b) + "   (int division drops the remainder)");
        System.out.println("17 % 5  = " + (a % b) + "   (% gives the remainder — 'modulo')");
        System.out.println("17 / 5.0 = " + (a / 5.0) + " (make one side a double to get decimals)");

        // Compound assignment and increment/decrement
        int counter = 10;
        counter += 5;   // counter = counter + 5
        counter++;      // counter = counter + 1
        System.out.println("10 += 5 then ++ -> " + counter);

        // Comparison operators produce booleans
        System.out.println("a > b: " + (a > b) + ", a == b: " + (a == b) + ", a != b: " + (a != b));

        // Logical operators: && (and), || (or), ! (not)
        // They SHORT-CIRCUIT: if the left side decides the answer,
        // the right side is never evaluated.
        int score = 85;
        boolean passed = score >= 40 && score <= 100;
        System.out.println("score 85 is a pass: " + passed);

        // Ternary operator: a one-line if/else that produces a value
        String grade = score >= 80 ? "distinction" : "pass";
        System.out.println("grade: " + grade);

        // ============================================================
        // 4. FLOATING-POINT SURPRISE
        // ============================================================
        System.out.println("\n=== Floating-point gotcha ===");
        // Doubles are binary fractions — they can't represent 0.1 exactly,
        // the same way decimal can't represent 1/3 exactly.
        System.out.println("0.1 + 0.2 = " + (0.1 + 0.2) + "  (not 0.3!)");
        // Lesson: NEVER use double for money. (BigDecimal exists for that — later.)

        // ============================================================
        // 5. var — local type inference (Java 10+)
        // ============================================================
        // The compiler figures out the type from the right-hand side.
        // Still statically typed! 'message' is a String forever.
        var message = "var infers the type at COMPILE time";
        var count = 42;           // count is an int
        System.out.println("\n" + message + " -> count is int: " + count);
    }
}
