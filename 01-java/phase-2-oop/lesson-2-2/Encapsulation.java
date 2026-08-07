import java.util.Arrays;

/*
 * Lesson 2.2 — Encapsulation: access modifiers, getters/setters
 *
 * ENCAPSULATION = hide the internal state of an object and force all
 * access through methods that enforce the rules.
 *
 * Why it matters:
 *   1. INVARIANTS  — rules like "salary is never negative" are enforced
 *                    in ONE place, and cannot be bypassed
 *   2. FREEDOM     — you can change the internal representation later
 *                    without breaking any caller (they only see methods)
 *   3. DEBUGGING   — state changes happen only through methods, so a
 *                    breakpoint in the setter catches EVERY change
 *
 * THE FOUR ACCESS MODIFIERS (widest to narrowest):
 *
 *   modifier    | same class | same package | subclass | everywhere
 *   ------------+------------+--------------+----------+-----------
 *   public      |    yes     |     yes      |   yes    |   yes
 *   protected   |    yes     |     yes      |   yes    |   no
 *   (default)   |    yes     |     yes      |   no     |   no
 *   private     |    yes     |     no       |   no     |   no
 *
 *   (default) = no keyword at all — called "package-private".
 *   Rule of thumb: start private, widen only when needed.
 */
public class Encapsulation {

    public static void main(String[] args) {

        // ============================================================
        // 1. State goes through the front door
        // ============================================================
        System.out.println("=== controlled access ===");

        Employee emp = new Employee("Ajay", 50_000);

        // emp.salary = -999;        // <- does not compile: salary is private.
        // The ONLY way in is the setter, and the setter has rules:

        emp.setSalary(60_000);
        System.out.println(emp.getName() + " now earns " + emp.getSalary());

        try {
            emp.setSalary(-500);     // the setter REFUSES bad data
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }
        System.out.println("salary still: " + emp.getSalary());

        // ============================================================
        // 2. Not every field needs a setter (or even a getter)
        // ============================================================
        System.out.println("\n=== read-only + computed ===");

        // name has a getter but NO setter -> read-only after construction.
        // yearlyBonus() is COMPUTED on demand — no field behind it at all.
        // Callers can't tell the difference. That's the freedom: today
        // it's computed, tomorrow it could be a cached field. No caller changes.
        System.out.println("bonus: " + emp.yearlyBonus());

        // ============================================================
        // 3. THE LEAK: returning a mutable field breaks encapsulation
        // ============================================================
        System.out.println("\n=== defensive copies ===");

        Employee dev = new Employee("Ravi", 40_000);
        dev.setRatings(new int[]{4, 5, 3});

        // getRatingsLeaky() returns the INTERNAL array reference.
        int[] stolen = dev.getRatingsLeaky();
        stolen[0] = -100;                      // mutate it from OUTSIDE...
        System.out.println("after external mutation (leaky): "
                + Arrays.toString(dev.getRatingsLeaky()));
        // ...and the object's private state changed WITHOUT any setter.
        // `private` protected the VARIABLE, not the OBJECT it points to.

        // The fix — return a COPY:
        dev.setRatings(new int[]{4, 5, 3});    // reset
        int[] safe = dev.getRatings();         // defensive copy
        safe[0] = -100;                        // mutate the copy all you want...
        System.out.println("after external mutation (safe):  "
                + Arrays.toString(dev.getRatings()));   // internal state intact

        // ============================================================
        // 4. Fully immutable objects — encapsulation's end game
        // ============================================================
        System.out.println("\n=== immutable class ===");

        // No setters at all. State fixed at construction. Nothing to
        // defend, nothing to synchronize, safe to share anywhere.
        Point p1 = new Point(3, 4);
        Point moved = p1.translate(2, 0);      // "modification" returns a NEW object
        System.out.println("p1    = " + p1.describe() + "   (unchanged — like String!)");
        System.out.println("moved = " + moved.describe());
    }
}

class Employee {

    // Fields: private. Always. Widen access via methods, never the field.
    private final String name;         // final + no setter = read-only
    private double salary;
    private int[] ratings = {};        // mutable object — needs defensive care

    Employee(String name, double salary) {
        this.name = name;
        setSalary(salary);             // reuse the setter -> constructor gets validation FREE
    }

    // ---- getters (JavaBeans naming: getX, isX for booleans) ---------

    public String getName() {
        return name;
    }

    public double getSalary() {
        return salary;
    }

    // ---- setter WITH RULES — this is the point ----------------------

    public void setSalary(double salary) {
        if (salary < 0) {
            throw new IllegalArgumentException("salary cannot be negative: " + salary);
        }
        this.salary = salary;
    }

    // Computed "property" — no field exists; callers can't tell.
    public double yearlyBonus() {
        return salary * 0.10;
    }

    // ---- the mutable-field problem ----------------------------------

    public void setRatings(int[] ratings) {
        // Defensive copy IN: otherwise the caller keeps a live reference
        // to our internals via the array they passed.
        this.ratings = Arrays.copyOf(ratings, ratings.length);
    }

    // BAD: hands out the internal array — private field, public state!
    public int[] getRatingsLeaky() {
        return ratings;
    }

    // GOOD: defensive copy OUT.
    public int[] getRatings() {
        return Arrays.copyOf(ratings, ratings.length);
    }
}

/*
 * An IMMUTABLE class: all fields final, no setters, no leaks.
 * Recipe: final fields + no setters + defensive-copy any mutable inputs
 * (+ mark the class final so subclasses can't add mutability — 2.3).
 * Java 16+ `record` generates exactly this pattern — lesson 2.6.
 */
final class Point {
    private final int x;
    private final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }

    // "Modifying" an immutable object = returning a new one (String taught us this)
    public Point translate(int dx, int dy) {
        return new Point(x + dx, y + dy);
    }

    public String describe() {
        return "(" + x + ", " + y + ")";
    }
}
