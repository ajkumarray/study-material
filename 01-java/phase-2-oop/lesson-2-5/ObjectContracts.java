import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/*
 * Lesson 2.5 — The Object contracts: equals, hashCode, toString
 *
 * Every class implicitly extends Object, inheriting:
 *   toString()  -> "ClassName@hexHash"        (useless for humans)
 *   equals(o)   -> this == o                  (IDENTITY: same object?)
 *   hashCode()  -> derived from identity
 *
 * For VALUE classes (two instances with the same data should count as
 * "the same thing" — money, points, IDs), you must override all three.
 * Getting this wrong silently breaks HashSet/HashMap — proven below.
 */
public class ObjectContracts {

    public static void main(String[] args) {

        // ============================================================
        // 1. The defaults are about IDENTITY, not value
        // ============================================================
        System.out.println("=== the defaults ===");

        BrokenBook b1 = new BrokenBook("Effective Java", "Bloch");
        BrokenBook b2 = new BrokenBook("Effective Java", "Bloch");

        System.out.println("toString: " + b1);                      // ClassName@hash gibberish
        System.out.println("b1.equals(b2): " + b1.equals(b2));      // false! same data, different objects

        // ============================================================
        // 2. Why it MATTERS: hash collections break silently
        // ============================================================
        System.out.println("\n=== broken in a HashSet ===");

        Set<BrokenBook> library = new HashSet<>();
        library.add(b1);
        library.add(b2);                       // "duplicate"... but the set can't tell
        System.out.println("library size = " + library.size() + "  (should be 1!)");
        System.out.println("contains copy? " + library.contains(new BrokenBook("Effective Java", "Bloch")));

        // ============================================================
        // 3. Done right
        // ============================================================
        System.out.println("\n=== fixed ===");

        Book g1 = new Book("Effective Java", "Bloch");
        Book g2 = new Book("Effective Java", "Bloch");

        System.out.println("toString: " + g1);                      // readable!
        System.out.println("g1.equals(g2): " + g1.equals(g2));      // true — value equality

        Set<Book> shelf = new HashSet<>();
        shelf.add(g1);
        shelf.add(g2);
        System.out.println("shelf size = " + shelf.size() + "  (duplicate detected)");
        System.out.println("contains copy? " + shelf.contains(new Book("Effective Java", "Bloch")));

        // ============================================================
        // 4. THE CONTRACT — why hashCode must follow equals
        // ============================================================
        System.out.println("\n=== the contract ===");
        //  RULE: a.equals(b)  =>  a.hashCode() == b.hashCode()
        //  (Unequal objects MAY share a hash — that's just a collision.)
        //
        //  Why: HashSet/HashMap first jump to the BUCKET chosen by
        //  hashCode, then use equals within it. Equal objects with
        //  different hashes land in different buckets and NEVER MEET —
        //  exactly the BrokenBook failure above if you override equals only.
        System.out.println("g1.hashCode() == g2.hashCode() -> "
                + (g1.hashCode() == g2.hashCode()) + "  (required, since they're equal)");

        // equals' own contract: reflexive (a=a), symmetric (a=b <-> b=a),
        // transitive (a=b, b=c -> a=c), consistent, and a.equals(null) == false.
    }
}

// The default inheritance from Object — broken as a value class:
class BrokenBook {
    final String title;
    final String author;

    BrokenBook(String title, String author) {
        this.title = title;
        this.author = author;
    }
    // no overrides -> identity semantics
}

// The canonical recipe (records generate ALL of this — lesson 2.6):
class Book {
    private final String title;
    private final String author;

    Book(String title, String author) {
        this.title = title;
        this.author = author;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                   // 1. fast path: same object
        if (!(o instanceof Book other)) return false; // 2. type check + cast (handles null too:
                                                      //    null instanceof X is always false)
        return title.equals(other.title)              // 3. compare the fields that define
                && author.equals(other.author);       //    "the same value"
        // For nullable fields use Objects.equals(a, b) — null-safe.
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, author);   // combine the SAME fields as equals. Always.
    }

    @Override
    public String toString() {
        return "Book[title=" + title + ", author=" + author + "]";
    }
}

/*
 * Notes:
 * - Override equals -> ALWAYS override hashCode. IDEs and records do
 *   this for you; hand-rolled forgetting is a classic production bug.
 * - `instanceof` vs `getClass()` in equals: instanceof allows subclass
 *   equality (and is what most code wants); strict getClass() equality
 *   is the conservative choice when subclasses add state. Know the
 *   trade-off exists; pick one consistently.
 * - toString: for debugging/logging. Never parse it; never put secrets in it.
 */
