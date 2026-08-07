/*
 * Lesson 1.3 — Control flow: if/else, switch, loops
 *
 * Control flow = deciding WHICH code runs and HOW MANY times.
 */
public class ControlFlow {

    public static void main(String[] args) {

        // ============================================================
        // 1. if / else if / else
        // ============================================================
        System.out.println("=== if / else ===");

        int score = 85;
        if (score >= 90) {
            System.out.println("Grade A");
        } else if (score >= 75) {
            System.out.println("Grade B");   // <- first true branch wins, rest are skipped
        } else if (score >= 60) {
            System.out.println("Grade C");
        } else {
            System.out.println("Fail");
        }

        // Style rule: ALWAYS use braces, even for one-liners.
        // Famous bug (Apple's "goto fail"): an unbraced if picked up a
        // second line that looked indented into it but wasn't.

        // ============================================================
        // 2. switch — the OLD statement form (know it, it's everywhere)
        // ============================================================
        System.out.println("\n=== classic switch (fall-through!) ===");

        int day = 6; // 1 = Monday ... 7 = Sunday
        switch (day) {
            case 6:                                   // no break here...
            case 7:                                   // ...so 6 FALLS THROUGH to 7
                System.out.println("Weekend!");
                break;                                // break exits the switch
            default:
                System.out.println("Weekday");
                break;
        }
        // Fall-through is the #1 switch bug: forget a break and execution
        // keeps going into the next case. Sometimes used deliberately
        // (like grouping 6 and 7 above), usually an accident.

        // ============================================================
        // 3. switch EXPRESSION — the modern form (Java 14+)
        // ============================================================
        System.out.println("\n=== switch expression (modern) ===");

        // Arrow form: no fall-through, no break needed, and it RETURNS a value.
        String dayType = switch (day) {
            case 1, 2, 3, 4, 5 -> "Weekday";          // multiple labels per case
            case 6, 7          -> "Weekend";
            default            -> "Invalid day";
        };
        System.out.println("day " + day + " is a " + dayType);

        // If a branch needs multiple statements, use a block + yield:
        String feeling = switch (day) {
            case 1 -> "Ugh, Monday";
            case 5 -> {
                String base = "Friday";
                yield base + "!!!";                    // yield = "this is the value"
            }
            default -> "just a day";
        };
        System.out.println("feeling: " + feeling);

        // Switch works on: int/byte/short/char, String, enums (later),
        // and patterns (later). NOT on long, double, or boolean.

        // ============================================================
        // 4. while — check condition BEFORE each iteration (0+ runs)
        // ============================================================
        System.out.println("\n=== while ===");

        int countdown = 3;
        while (countdown > 0) {
            System.out.println("countdown: " + countdown);
            countdown--;                               // forget this -> infinite loop
        }

        // ============================================================
        // 5. do-while — check condition AFTER each iteration (1+ runs)
        // ============================================================
        System.out.println("\n=== do-while ===");

        int attempts = 0;
        do {
            attempts++;
            System.out.println("attempt #" + attempts);
        } while (attempts < 1);
        // Body ran once even though the condition was immediately false.
        // Use case: "do the thing, THEN decide whether to repeat" —
        // e.g., prompt for a password, retry until valid.

        // ============================================================
        // 6. for — when you know the iteration count/range
        // ============================================================
        System.out.println("\n=== for ===");

        //   init       condition   update  (in that order; condition checked before each pass)
        for (int i = 1; i <= 5; i++) {
            System.out.print(i + " ");
        }
        System.out.println();

        // ============================================================
        // 7. enhanced for (for-each) — iterate a collection/array
        // ============================================================
        System.out.println("\n=== for-each ===");

        String[] stack = {"Java", "Spring Boot", "Docker", "Kubernetes"};
        for (String tech : stack) {                    // "for each tech in stack"
            System.out.println("learning: " + tech);
        }
        // Cleaner than an index loop — but you don't get the index,
        // and you can't modify the array slot through the loop variable.

        // ============================================================
        // 8. break and continue
        // ============================================================
        System.out.println("\n=== break / continue ===");

        for (int i = 1; i <= 10; i++) {
            if (i % 2 == 0) continue;                  // skip THIS iteration, keep looping
            if (i > 7) break;                          // exit the WHOLE loop
            System.out.print(i + " ");                 // prints odd numbers up to 7
        }
        System.out.println();

        // Labeled break: escape NESTED loops in one jump.
        // (Without the label, break only exits the innermost loop.)
        search:
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (row == 1 && col == 2) {
                    System.out.println("found it at (" + row + "," + col + ")");
                    break search;                      // exits BOTH loops
                }
            }
        }

        // ============================================================
        // 9. The classic: FizzBuzz — everything combined
        // ============================================================
        System.out.println("\n=== FizzBuzz 1..15 ===");

        for (int i = 1; i <= 15; i++) {
            // Order matters: check the combined case FIRST,
            // or "divisible by 3" would swallow the 15s.
            if (i % 15 == 0)      System.out.println("FizzBuzz");
            else if (i % 3 == 0)  System.out.println("Fizz");
            else if (i % 5 == 0)  System.out.println("Buzz");
            else                  System.out.println(i);
        }
    }
}
