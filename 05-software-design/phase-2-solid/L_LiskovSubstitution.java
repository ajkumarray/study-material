/*
 * L — LISKOV SUBSTITUTION PRINCIPLE (LSP)
 * "Subtypes must be substitutable for their base type without breaking
 *  correctness." If code works with a base type, it must work with ANY subtype.
 * Run:  java L_LiskovSubstitution.java
 *
 * Inheritance implies an IS-A + behaves-as promise. A subtype that weakens the
 * base's contract (throws where the base didn't, strengthens preconditions,
 * violates invariants) breaks callers that trusted the base. This is the
 * Square-extends-Rectangle trap you met in Java Phase 2.3.
 */
public class L_LiskovSubstitution {

    public static void main(String[] args) {
        // A caller written against Rectangle: "set width 5, height 4, expect area 20."
        System.out.println("BROKEN inheritance model:");
        demonstrateBreakage();

        System.out.println("\nFIXED with a shared abstraction (no bad inheritance):");
        for (Shape4 s : new Shape4[]{new Rect(5, 4), new Sq(5)}) {
            System.out.printf("  %-10s area = %.0f%n", s.getClass().getSimpleName(), s.area());
        }
    }

    // ---- BEFORE: Square extends Rectangle. Looks fine ("a square IS a
    // rectangle"), but overriding the setters to keep sides equal BREAKS the
    // Rectangle contract that width and height vary independently. ----
    static class Rectangle {
        protected int width, height;
        void setWidth(int w)  { this.width = w; }
        void setHeight(int h) { this.height = h; }
        int area() { return width * height; }
    }
    static class Square extends Rectangle {
        @Override void setWidth(int w)  { this.width = w; this.height = w; }   // side effect!
        @Override void setHeight(int h) { this.width = h; this.height = h; }   // side effect!
    }

    static void demonstrateBreakage() {
        Rectangle r = new Square();          // substitute a Square for a Rectangle...
        r.setWidth(5);
        r.setHeight(4);                      // caller expects width=5, height=4 -> area 20
        System.out.printf("  expected area 20, got %d  <-- LSP VIOLATED%n", r.area());  // 16!
        // The caller is CORRECT for Rectangle; Square silently breaks it.
    }

    // ---- AFTER: don't force a false IS-A. Model both as Shapes with their own
    // rules; a Square isn't a settable Rectangle, so don't inherit one. ----
    interface Shape4 { double area(); }
    record Rect(int w, int h) implements Shape4 { public double area() { return w * h; } }
    record Sq(int side)       implements Shape4 { public double area() { return side * side; } }
}

/*
 * THE LSP CONTRACT (what a well-behaved subtype must honor):
 *  - preconditions no STRONGER than the base (don't demand more from callers)
 *  - postconditions no WEAKER (deliver at least what the base promised)
 *  - invariants preserved; don't throw new exceptions the base didn't
 *  - the classic smells: overriding a method to do nothing / throw
 *    UnsupportedOperationException, or `if (x instanceof Subtype)` special-casing.
 * Fix by favoring COMPOSITION over inheritance and modeling true abstractions.
 */
