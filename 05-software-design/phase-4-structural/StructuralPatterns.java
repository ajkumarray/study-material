import java.util.ArrayList;
import java.util.List;

/*
 * Phase 4 — STRUCTURAL design patterns
 * Run:  java -ea StructuralPatterns.java
 *
 * Structural patterns are about how objects and classes are COMPOSED into larger
 * structures — adapting interfaces, adding behavior, simplifying, and controlling
 * access, mostly through COMPOSITION (Software Design's "composition over
 * inheritance"). Several appear throughout the JDK and Spring.
 */
public class StructuralPatterns {

    public static void main(String[] args) {
        // Adapter — make an incompatible interface usable
        PaymentProcessor pay = new StripeAdapter(new StripeApi());
        assert pay.pay(100).contains("100");

        // Decorator — wrap to add behavior at runtime
        Coffee c = new MilkDecorator(new SugarDecorator(new SimpleCoffee()));
        assert c.cost() == 2 + 0.5 + 0.7;            // base + sugar + milk
        assert c.description().contains("milk") && c.description().contains("sugar");

        // Facade — one simple entry point over a complex subsystem
        assert new OrderFacade().placeOrder("book").contains("confirmed");

        // Proxy — control access / add cross-cutting behavior around a real object
        Image img = new LazyImageProxy("photo.jpg");
        assert !LazyImageProxy.loaded;               // not loaded yet (lazy)
        img.display();
        assert LazyImageProxy.loaded;                // loaded on first use

        // Composite — treat individual objects and groups uniformly
        Directory root = new Directory("root");
        root.add(new File("a.txt", 10));
        Directory sub = new Directory("sub");
        sub.add(new File("b.txt", 20));
        sub.add(new File("c.txt", 5));
        root.add(sub);
        assert root.size() == 35;                    // recurses into the tree

        System.out.println("All structural-pattern tests passed.");
        demo();
    }

    // ============================================================
    // ADAPTER — convert one interface into another the client expects. Wrap a
    // third-party/legacy class so it fits YOUR interface. (JDK: Arrays.asList,
    // InputStreamReader adapts bytes->chars.)
    // ============================================================
    interface PaymentProcessor { String pay(int amount); }   // what OUR code wants
    static class StripeApi { String makeCharge(int cents) { return "charged " + cents + " cents"; } }  // their API
    static class StripeAdapter implements PaymentProcessor {
        private final StripeApi stripe;
        StripeAdapter(StripeApi stripe) { this.stripe = stripe; }
        public String pay(int amount) { return "paid " + amount + " via [" + stripe.makeCharge(amount * 100) + "]"; }
    }

    // ============================================================
    // DECORATOR — attach responsibilities to an object dynamically by WRAPPING
    // it in objects with the same interface. Flexible alternative to subclass
    // explosion. (JDK: java.io — new BufferedReader(new FileReader(...)) IS this.)
    // ============================================================
    interface Coffee { double cost(); String description(); }
    static class SimpleCoffee implements Coffee {
        public double cost() { return 2; }
        public String description() { return "coffee"; }
    }
    // A decorator implements the interface AND holds a wrapped instance:
    static abstract class CoffeeDecorator implements Coffee {
        protected final Coffee inner;
        CoffeeDecorator(Coffee inner) { this.inner = inner; }
    }
    static class MilkDecorator extends CoffeeDecorator {
        MilkDecorator(Coffee c) { super(c); }
        public double cost() { return inner.cost() + 0.7; }
        public String description() { return inner.description() + " + milk"; }
    }
    static class SugarDecorator extends CoffeeDecorator {
        SugarDecorator(Coffee c) { super(c); }
        public double cost() { return inner.cost() + 0.5; }
        public String description() { return inner.description() + " + sugar"; }
    }

    // ============================================================
    // FACADE — a single simplified interface over a complex subsystem, hiding its
    // parts. (Spring's JdbcTemplate is a facade over raw JDBC.)
    // ============================================================
    static class Inventory { boolean reserve(String item) { return true; } }
    static class Payment { boolean charge() { return true; } }
    static class Shipping { String schedule() { return "ship-42"; } }
    static class OrderFacade {                        // hides the three subsystems
        private final Inventory inv = new Inventory();
        private final Payment pay = new Payment();
        private final Shipping ship = new Shipping();
        String placeOrder(String item) {
            if (inv.reserve(item) && pay.charge()) return "order confirmed, " + ship.schedule();
            return "order failed";
        }
    }

    // ============================================================
    // PROXY — a stand-in that controls access to a real object: lazy-load it,
    // add caching/logging/security, or represent a remote object. (Spring AOP,
    // @Transactional, and JPA lazy loading are all dynamic proxies!)
    // ============================================================
    interface Image { void display(); }
    static class RealImage implements Image {
        RealImage(String file) { /* expensive load */ }
        public void display() { /* draw */ }
    }
    static class LazyImageProxy implements Image {
        static boolean loaded = false;
        private final String file;
        private RealImage real;                       // created only when needed
        LazyImageProxy(String file) { this.file = file; }
        public void display() {
            if (real == null) { real = new RealImage(file); loaded = true; }  // lazy init on first use
            real.display();
        }
    }

    // ============================================================
    // COMPOSITE — compose objects into TREES and treat individual objects and
    // compositions UNIFORMLY (both implement the same interface). (JDK: the Swing
    // component tree; a file system; the HTML DOM.)
    // ============================================================
    interface FsNode { int size(); }                  // the common interface
    static class File implements FsNode {
        final String name; final int bytes;
        File(String name, int bytes) { this.name = name; this.bytes = bytes; }
        public int size() { return bytes; }           // a leaf
    }
    static class Directory implements FsNode {
        final String name; final List<FsNode> children = new ArrayList<>();
        Directory(String name) { this.name = name; }
        void add(FsNode n) { children.add(n); }
        public int size() {                           // recurse over children (files OR dirs)
            return children.stream().mapToInt(FsNode::size).sum();
        }
    }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        Coffee c = new MilkDecorator(new SugarDecorator(new SimpleCoffee()));
        System.out.println("decorator: " + c.description() + " = " + c.cost());
        System.out.println("facade: " + new OrderFacade().placeOrder("book"));
        System.out.println("adapter: " + new StripeAdapter(new StripeApi()).pay(50));
    }
}
