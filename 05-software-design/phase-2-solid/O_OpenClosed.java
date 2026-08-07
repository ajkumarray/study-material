import java.util.List;

/*
 * O — OPEN/CLOSED PRINCIPLE (OCP)
 * "Software entities should be OPEN for extension, CLOSED for modification."
 * Run:  java O_OpenClosed.java
 *
 * You should be able to add new behavior by ADDING code, not EDITING existing,
 * tested code. The enabler is polymorphism: depend on an abstraction, and let
 * new subtypes extend it. (This is exactly your Java Phase 2.3 Shape.area()
 * lesson — the Open/Closed Principle is what dynamic dispatch buys you.)
 */
public class O_OpenClosed {

    public static void main(String[] args) {
        List<Shape> shapes = List.of(new Circle(2), new Rectangle(3, 4), new Triangle(6, 2));
        // Adding Triangle required ZERO changes to AreaCalculator or the others.
        System.out.printf("total area = %.2f%n", new AreaCalculator().totalArea(shapes));
    }
}

/* ---- BEFORE (violates OCP) --------------------------------------------------
   double area(Object shape) {
       if (shape instanceof Circle c)       return Math.PI * c.r * c.r;
       else if (shape instanceof Rectangle r) return r.w * r.h;
       // every NEW shape forces you to EDIT this method (and re-test it),
       // risking the cases that already worked.
   }
----------------------------------------------------------------------------- */

// AFTER: the abstraction. New shapes implement it; existing code never changes.
interface Shape {
    double area();
}

record Circle(double r) implements Shape {
    public double area() { return Math.PI * r * r; }
}
record Rectangle(double w, double h) implements Shape {
    public double area() { return w * h; }
}
// Adding Triangle = ADDING this class. AreaCalculator stays closed (untouched).
record Triangle(double base, double height) implements Shape {
    public double area() { return 0.5 * base * height; }
}

// Closed for modification: this works for every current AND future Shape.
class AreaCalculator {
    double totalArea(List<Shape> shapes) {
        return shapes.stream().mapToDouble(Shape::area).sum();
    }
}

/*
 * WHY IT'S BETTER:
 *  - New requirements = new classes, not edits to tested code (fewer regressions).
 *  - The calculator has no growing if/else chain to maintain.
 * Real-world OCP: the Strategy pattern (inject behavior), plugin architectures,
 * Spring's ability to add a @Component without editing a registry.
 * Note the balance: don't over-abstract for variation that never comes (YAGNI).
 */
