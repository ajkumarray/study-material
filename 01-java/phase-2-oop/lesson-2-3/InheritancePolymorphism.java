/*
 * Lesson 2.3 — Inheritance & polymorphism
 *
 * INHERITANCE: a class `extends` another, receiving its fields and
 * methods. Models an IS-A relationship (a Circle IS-A Shape).
 *
 * POLYMORPHISM: code written against the parent type works with ANY
 * subtype — and calls the SUBTYPE's version of overridden methods.
 * The mechanism is DYNAMIC DISPATCH: the method that runs is chosen
 * at RUNTIME from the object's actual class, not the variable's type.
 *
 * This pair is the single most important idea in OOP interviews.
 */
public class InheritancePolymorphism {

    public static void main(String[] args) {

        // ============================================================
        // 1. Polymorphism in action
        // ============================================================
        System.out.println("=== dynamic dispatch ===");

        // Variable type: Shape (parent). Object type: Circle/Rectangle (child).
        // This "parent variable, child object" shape is UPCASTING — always safe,
        // no cast syntax needed.
        Shape s1 = new Circle(5);
        Shape s2 = new Rectangle(4, 6);

        // Which describe() runs? The OBJECT's version — decided at runtime:
        System.out.println(s1.describe());     // Circle's describe
        System.out.println(s2.describe());     // Rectangle's describe

        // The payoff: one method handles every current AND FUTURE shape.
        Shape[] drawing = {new Circle(1), new Rectangle(2, 3), new Circle(10)};
        System.out.println("total area = " + totalArea(drawing));
        // Add a Triangle class tomorrow -> totalArea works, UNCHANGED.
        // That's the Open/Closed Principle: open to extension,
        // closed to modification.

        // ============================================================
        // 2. What the child inherits & super
        // ============================================================
        System.out.println("\n=== super ===");

        Circle c = new Circle(5);
        // name field and describe() came from Shape; area() is Circle's own.
        // Circle's constructor called super(...) — see the class below.
        // Circle's describe() calls super.describe() to REUSE the parent's
        // version and add to it, instead of copy-pasting.

        // ============================================================
        // 3. The variable's type decides what you CAN CALL;
        //    the object's type decides WHAT RUNS
        // ============================================================
        System.out.println("\n=== compile-time type vs runtime type ===");

        Shape shape = new Circle(2);
        // shape.getRadius();          // <- does NOT compile: Shape has no getRadius.
        // The COMPILER only knows the variable is a Shape.

        // DOWNCASTING recovers the specific type — but it's checked at runtime:
        if (shape instanceof Circle circle) {   // pattern matching (Java 16+):
            System.out.println("radius = " + circle.getRadius());  // test + cast + bind in one
        }

        // A WRONG downcast compiles but explodes at runtime:
        Shape rect = new Rectangle(1, 1);
        try {
            Circle boom = (Circle) rect;        // a Rectangle is NOT a Circle
        } catch (ClassCastException e) {
            System.out.println("bad cast -> ClassCastException");
        }
        // Moral: `instanceof` before casting — or better, design so you
        // rarely need to downcast at all (polymorphism usually replaces it).

        // ============================================================
        // 4. Overriding vs overloading side by side
        // ============================================================
        System.out.println("\n=== overriding is NOT overloading ===");
        // OVERLOADING: same name, different parameters, SAME class,
        //              resolved at COMPILE time (lesson 1.4).
        // OVERRIDING:  same signature, SUBCLASS replaces parent's version,
        //              resolved at RUNTIME (dynamic dispatch).

        // Fields do NOT dispatch dynamically — only methods do:
        Shape f = new Circle(1);
        System.out.println("field via Shape var: " + f.kind);   // "generic shape" — field = variable's type!
        System.out.println("method via Shape var: " + f.name()); // "circle"       — method = object's type
    }

    // Written once, against the PARENT type. Works for every subtype, forever.
    static double totalArea(Shape[] shapes) {
        double total = 0;
        for (Shape s : shapes) {
            total += s.area();          // dynamic dispatch on every element
        }
        return total;
    }
}

class Shape {
    protected String name;      // protected: subclasses may touch it (2.2's table)
    String kind = "generic shape";      // to demo: fields don't dispatch

    Shape(String name) {
        this.name = name;
    }

    double area() {
        return 0;               // parent gives a default; children override
    }

    String describe() {
        return "I am a " + name + " with area " + area();
        //                                        ^ even HERE dispatch is dynamic:
        // called on a Circle, this runs Circle's area().
    }

    String name() {
        return name;
    }
}

class Circle extends Shape {
    private final double radius;
    String kind = "circle";     // SHADOWS parent field (don't do this in real code)

    Circle(double radius) {
        super("circle");        // MUST be first statement: parent initializes first.
        // If you omit super(...), the compiler inserts super() — which
        // FAILS to compile here because Shape has no no-arg constructor.
        this.radius = radius;
    }

    // @Override asks the compiler to VERIFY this really overrides something.
    // Typo the name or signature and you'd silently create a new method
    // instead — @Override turns that bug into a compile error. Always use it.
    @Override
    double area() {
        return Math.PI * radius * radius;
    }

    @Override
    String describe() {
        return super.describe() + " (radius " + radius + ")";  // reuse + extend
    }

    double getRadius() {        // Circle-specific: not visible through a Shape variable
        return radius;
    }
}

class Rectangle extends Shape {
    private final double width, height;

    Rectangle(double width, double height) {
        super("rectangle");
        this.width = width;
        this.height = height;
    }

    @Override
    double area() {
        return width * height;
    }
    // No describe() override -> inherits Shape's version as-is.
}

/*
 * Notes not shown in output:
 *
 * - Java is SINGLE inheritance: one parent per class (multiple
 *   inheritance of BEHAVIOR comes via interfaces — lesson 2.4).
 * - `final` on a class -> nobody can extend it (String is final).
 *   `final` on a method -> subclasses can't override it.
 * - Overriding rules: same signature; return type may be a SUBTYPE
 *   (covariant return); access may WIDEN but never narrow
 *   (public parent method can't become private in child);
 *   can't throw broader checked exceptions (Phase 3).
 * - Every class with no `extends` implicitly extends Object —
 *   the root of the entire class tree (lesson 2.5).
 */
