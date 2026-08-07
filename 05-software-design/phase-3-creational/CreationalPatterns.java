import java.util.HashMap;
import java.util.Map;

/*
 * Phase 3 — CREATIONAL design patterns
 * Run:  java -ea CreationalPatterns.java
 *
 * Creational patterns are about HOW OBJECTS ARE CREATED — decoupling client
 * code from the concrete classes it instantiates (so `new` isn't scattered
 * everywhere, and swapping implementations is easy). They embody the Open/Closed
 * and Dependency Inversion principles (Phase 2).
 */
public class CreationalPatterns {

    public static void main(String[] args) {
        // Singleton — one shared instance
        assert Config.INSTANCE == Config.INSTANCE;
        Config.INSTANCE.set("env", "prod");
        assert Config.INSTANCE.get("env").equals("prod");

        // Factory Method — create by type without exposing constructors
        assert ShapeFactory.create("circle") instanceof Circle;
        assert ShapeFactory.create("square").area() == 0;   // default size 0

        // Builder — construct a complex object step by step, readably
        Pizza p = new Pizza.Builder("medium").cheese().topping("mushroom").topping("olive").build();
        assert p.toppings.size() == 2 && p.cheese;

        // Abstract Factory — families of related objects
        GuiFactory dark = new DarkFactory();
        assert dark.button().render().contains("dark");
        assert dark.checkbox().render().contains("dark");

        // Prototype — clone an existing object instead of building anew
        Document original = new Document("template", java.util.List.of("intro", "body"));
        Document copy = original.copy();
        copy.title = "my-doc";
        assert original.title.equals("template") && copy.sections.equals(original.sections);

        System.out.println("All creational-pattern tests passed.");
        demo();
    }

    // ============================================================
    // SINGLETON — exactly ONE instance, globally accessible. The ENUM form is
    // the best in Java (thread-safe, serialization-safe, lazy — Java Phase 2.6).
    // Use for truly-single resources (config, a registry). Overuse = a global
    // variable in disguise; prefer dependency injection (Spring) where possible.
    // ============================================================
    enum Config {
        INSTANCE;                                   // the single instance
        private final Map<String, String> values = new HashMap<>();
        void set(String k, String v) { values.put(k, v); }
        String get(String k) { return values.get(k); }
    }

    // ============================================================
    // FACTORY METHOD — a method decides which concrete class to instantiate, so
    // callers depend on the ABSTRACTION (Shape), not the concretions. Adding a
    // new shape doesn't change caller code (Open/Closed).
    // ============================================================
    interface Shape { double area(); }
    record Circle(double r) implements Shape { public double area() { return Math.PI * r * r; } }
    record Square(double s) implements Shape { public double area() { return s * s; } }

    static class ShapeFactory {
        static Shape create(String type) {
            return switch (type) {                  // the one place that knows the concretions
                case "circle" -> new Circle(1);
                case "square" -> new Square(0);
                default -> throw new IllegalArgumentException("unknown shape: " + type);
            };
        }
    }

    // ============================================================
    // BUILDER — construct a complex object step by step with a fluent API,
    // avoiding telescoping constructors (Pizza(size, cheese, t1, t2, t3...)).
    // Great for objects with many optional parts. (Java records/@Builder/Lombok
    // relate; the JDK's StringBuilder is a builder.)
    // ============================================================
    static class Pizza {
        final String size;
        final boolean cheese;
        final java.util.List<String> toppings;
        private Pizza(Builder b) { size = b.size; cheese = b.cheese; toppings = b.toppings; }

        static class Builder {
            private final String size;
            private boolean cheese = false;
            private final java.util.List<String> toppings = new java.util.ArrayList<>();
            Builder(String size) { this.size = size; }          // required arg
            Builder cheese() { this.cheese = true; return this; } // optional, chainable
            Builder topping(String t) { toppings.add(t); return this; }
            Pizza build() { return new Pizza(this); }             // produce the immutable object
        }
    }

    // ============================================================
    // ABSTRACT FACTORY — create FAMILIES of related objects (a whole themed UI
    // kit) without specifying concrete classes, guaranteeing they match (all
    // dark, or all light). One factory per family.
    // ============================================================
    interface Button { String render(); }
    interface Checkbox { String render(); }
    interface GuiFactory { Button button(); Checkbox checkbox(); }

    static class DarkFactory implements GuiFactory {
        public Button button() { return () -> "[dark button]"; }
        public Checkbox checkbox() { return () -> "[dark checkbox]"; }
    }
    static class LightFactory implements GuiFactory {
        public Button button() { return () -> "[light button]"; }
        public Checkbox checkbox() { return () -> "[light checkbox]"; }
    }

    // ============================================================
    // PROTOTYPE — create new objects by CLONING an existing one (a prototype),
    // useful when construction is expensive or you want a copy with tweaks.
    // ============================================================
    static class Document {
        String title;
        java.util.List<String> sections;
        Document(String title, java.util.List<String> sections) {
            this.title = title;
            this.sections = new java.util.ArrayList<>(sections);   // defensive copy
        }
        Document copy() { return new Document(title, sections); }  // copy constructor / clone
    }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("factory: " + ShapeFactory.create("circle").getClass().getSimpleName());
        System.out.println("builder: " + new Pizza.Builder("large").cheese().topping("basil").toppings);
        System.out.println("abstract factory (light): " + new LightFactory().button().render());
    }
}
