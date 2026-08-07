import java.util.Arrays;

/*
 * Lesson 1.5 — Arrays and Strings
 *
 * The two data containers you'll touch every single day.
 * Both are OBJECTS (reference types), but each has famous quirks.
 */
public class ArraysAndStrings {

    public static void main(String[] args) {

        // ============================================================
        // 1. ARRAYS — fixed-size, same-type, indexed from 0
        // ============================================================
        System.out.println("=== arrays ===");

        // Two ways to create:
        int[] scores = new int[5];               // sized: filled with defaults (0)
        int[] primes = {2, 3, 5, 7, 11};         // literal: sized from the values

        scores[0] = 95;                          // index starts at 0
        scores[4] = 42;                          // last valid index = length - 1

        // length is a FIELD on arrays (no parentheses!)
        System.out.println("scores.length = " + scores.length);

        // Printing an array directly gives garbage ([I@hash) — use Arrays.toString:
        System.out.println("scores  = " + Arrays.toString(scores));
        System.out.println("primes  = " + Arrays.toString(primes));

        // Default values by type: numbers 0, boolean false, objects null —
        // same zero-ish defaults as object fields (lesson 1.2).

        // Walking past the end throws at RUNTIME (compiles fine!):
        try {
            int boom = primes[5];                // valid indexes: 0..4
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("primes[5] -> " + e.getMessage()
                    + " (ArrayIndexOutOfBoundsException)");
        }

        // Arrays are FIXED SIZE forever. "Growing" = allocate bigger + copy:
        int[] grown = Arrays.copyOf(primes, 8);  // copies, pads with 0s
        System.out.println("grown   = " + Arrays.toString(grown));
        // (ArrayList automates exactly this — Phase 3.)

        // Handy utilities:
        int[] shuffled = {40, 10, 30, 20};
        Arrays.sort(shuffled);
        System.out.println("sorted  = " + Arrays.toString(shuffled));

        // Comparing: == on arrays compares REFERENCES, not contents!
        int[] a1 = {1, 2, 3};
        int[] a2 = {1, 2, 3};
        System.out.println("a1 == a2          -> " + (a1 == a2) + "   (different objects)");
        System.out.println("Arrays.equals     -> " + Arrays.equals(a1, a2) + "    (same contents)");

        // ============================================================
        // 2. 2D ARRAYS — an array of arrays
        // ============================================================
        System.out.println("\n=== 2D arrays ===");

        int[][] grid = {
                {1, 2, 3},
                {4, 5, 6},
        };
        System.out.println("grid[1][2] = " + grid[1][2] + "  (row 1, col 2)");

        for (int[] row : grid) {                  // each element is itself an int[]
            System.out.println(Arrays.toString(row));
        }

        // ============================================================
        // 3. STRINGS — immutable character sequences
        // ============================================================
        System.out.println("\n=== strings ===");

        String tech = "Java";

        // Strings are IMMUTABLE: no method changes the string —
        // every "modifying" method RETURNS A NEW STRING.
        tech.toUpperCase();                        // result thrown away!
        System.out.println("after tech.toUpperCase(): " + tech + "   (unchanged!)");
        String loud = tech.toUpperCase();          // capture the returned string
        System.out.println("captured result:          " + loud);

        // The daily-driver methods:
        String s = "  Learning Java 21  ";
        System.out.println("length    : " + s.length());            // METHOD on String (vs array's field)
        System.out.println("strip     : '" + s.strip() + "'");      // trims whitespace (modern trim)
        System.out.println("charAt(2) : " + s.charAt(2));
        System.out.println("indexOf   : " + s.indexOf("Java"));      // first position, -1 if absent
        System.out.println("contains  : " + s.contains("Java"));
        System.out.println("replace   : " + s.strip().replace("21", "twenty-one"));
        System.out.println("substring : " + s.strip().substring(9, 13)); // [start, end) — end EXCLUSIVE

        // split & join — string <-> parts
        String csv = "java,spring,docker,kafka";
        String[] parts = csv.split(",");
        System.out.println("split     : " + Arrays.toString(parts));
        System.out.println("join      : " + String.join(" -> ", parts));

        // ============================================================
        // 4. == vs .equals() — THE classic
        // ============================================================
        System.out.println("\n=== == vs equals ===");

        String lit1 = "hello";                    // literals go in the STRING POOL...
        String lit2 = "hello";                    // ...so identical literals share ONE object
        String made = new String("hello");        // `new` FORCES a separate heap object

        System.out.println("lit1 == lit2      -> " + (lit1 == lit2) + "   (same pooled object)");
        System.out.println("lit1 == made      -> " + (lit1 == made) + "  (different objects!)");
        System.out.println("lit1.equals(made) -> " + lit1.equals(made) + "   (same CONTENTS)");

        // RULE: == asks "same object?"  .equals() asks "same value?"
        // For strings (and objects generally) you almost always want .equals().

        // ============================================================
        // 5. StringBuilder — for building strings in loops
        // ============================================================
        System.out.println("\n=== StringBuilder ===");

        // Because String is immutable, s += "x" in a loop creates a NEW
        // string every pass — copying everything so far, O(n^2) overall.
        // StringBuilder is a MUTABLE buffer: append is cheap, one
        // toString() at the end.
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(part).append(" | ");
        }
        System.out.println(sb.toString());

        // Quick proof it matters (100k appends each way):
        long t0 = System.nanoTime();
        String slow = "";
        for (int i = 0; i < 100_000; i++) slow += "x";
        long concatMs = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        StringBuilder fast = new StringBuilder();
        for (int i = 0; i < 100_000; i++) fast.append("x");
        fast.toString();
        long builderMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("100k appends — String +=: " + concatMs + " ms, StringBuilder: "
                + builderMs + " ms");

        // ============================================================
        // 6. Text blocks (Java 15+) — multi-line strings
        // ============================================================
        System.out.println("\n=== text block ===");

        String json = """
                {
                  "track": "java",
                  "lesson": "1.5"
                }""";
        System.out.println(json);
    }
}
