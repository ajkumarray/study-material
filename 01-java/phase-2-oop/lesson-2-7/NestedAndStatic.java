/*
 * Lesson 2.7 — Nested classes & the `static` deep-dive
 *
 * Java has FOUR kinds of nested classes:
 *   1. static nested  — a class scoped inside another; NO outer instance
 *   2. inner          — non-static; every instance is TIED to an outer instance
 *   3. local          — declared inside a method body
 *   4. anonymous      — declared and instantiated in one expression
 *
 * Plus the full story on static: fields, methods, and initializer blocks.
 */
public class NestedAndStatic {

    public static void main(String[] args) {

        // ============================================================
        // 1. STATIC NESTED — the workhorse (default choice)
        // ============================================================
        System.out.println("=== static nested ===");

        // Lives inside ShoppingCart purely for NAMESPACING/cohesion —
        // an Item belongs conceptually to a cart. No outer instance needed:
        ShoppingCart.Item item = new ShoppingCart.Item("keyboard", 2999);

        ShoppingCart cart = new ShoppingCart();
        cart.add(item);
        cart.add(new ShoppingCart.Item("mouse", 999));
        System.out.println("total = " + cart.total());

        // ============================================================
        // 2. INNER (non-static) — carries a hidden reference to outer
        // ============================================================
        System.out.println("\n=== inner class ===");

        // An Auditor can read the cart's PRIVATE state — nested classes
        // see the outer class's privates (and vice versa).
        // Note the weird construction syntax: outerInstance.new Inner()
        ShoppingCart.Auditor auditor = cart.new Auditor();
        auditor.report();

        // Because each inner instance PINS its outer instance in memory,
        // prefer STATIC nested unless you genuinely need that link.
        // (Non-static inner classes inside long-lived objects are a
        //  classic memory-leak source.)

        // ============================================================
        // 3. ANONYMOUS — one-off implementation, inline
        // ============================================================
        System.out.println("\n=== anonymous class ===");

        // "new Interface() { body }" defines a class with NO NAME and
        // instantiates it, all in one expression:
        Greeter formal = new Greeter() {
            @Override
            public String greet(String name) {
                return "Good day, " + name + ".";
            }
        };
        System.out.println(formal.greet("Ajay"));

        // For ONE-method interfaces, a lambda says the same thing shorter —
        // this is exactly what Phase 4 is about:
        Greeter casual = name -> "yo " + name;
        System.out.println(casual.greet("Ajay"));

        // ============================================================
        // 4. static INITIALIZER blocks & class-loading order
        // ============================================================
        System.out.println("\n=== initialization order ===");

        // Watch the print order when we touch Config for the first time:
        System.out.println("about to touch Config...");
        System.out.println("Config.APP = " + Config.APP);
        new Config();
        new Config();
        // Order proven by output:
        //   static block  -> ONCE, when the class is first loaded
        //   instance block-> before EVERY constructor run
        //   constructor   -> after the instance block
    }
}

interface Greeter {
    String greet(String name);
}

class ShoppingCart {
    private ShoppingCart.Item[] items = new Item[10];
    private int count = 0;

    void add(Item item) {
        items[count++] = item;
    }

    int total() {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += items[i].price;
        return sum;
    }

    // STATIC NESTED: no tie to any particular cart. Just namespaced.
    static class Item {
        final String name;
        final int price;

        Item(String name, int price) {
            this.name = name;
            this.price = price;
        }
    }

    // INNER (non-static): each Auditor belongs to ONE cart and can
    // reach its private fields directly.
    class Auditor {
        void report() {
            // 'count' and 'items' here are the OUTER object's privates:
            System.out.println("audit: " + count + " items, total " + total());
            // Explicit form, if names clash: ShoppingCart.this.count
        }
    }
}

class Config {
    static final String APP;

    // STATIC initializer: runs ONCE, at class load. For static setup
    // too complex for a one-liner (load a file, build a lookup table).
    static {
        System.out.println("  [static block] class loaded, runs ONCE");
        APP = "tech-stack";
    }

    // INSTANCE initializer: runs before EVERY constructor body.
    // (Rare in practice; constructors usually do this job.)
    {
        System.out.println("  [instance block] runs before each constructor");
    }

    Config() {
        System.out.println("  [constructor] runs last");
    }
}

/*
 * static — the complete rules:
 * - static members belong to the CLASS: one copy total, shared by all
 *   instances, accessible without any instance (Math.max, Integer.MAX_VALUE).
 * - static methods have NO `this` -> can touch only static state directly.
 *   Instance methods CAN touch static state (shared counter in 2.1).
 * - Don't call static via an instance (`cart.staticMethod()` compiles
 *   but misleads readers — say ShoppingCart.staticMethod()).
 * - static nested classes: the same idea applied to classes — no outer
 *   instance required. Top-level classes can't be `static` (there's
 *   nothing to be static IN).
 */
