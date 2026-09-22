<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · creational](../phase-3-creational/NOTES.md) | [Phase 5 · behavioral ➡](../phase-5-behavioral/NOTES.md)
<!-- /nav -->

# Phase 4 — Structural Design Patterns: Notes

Structural patterns are about **composing** classes and objects into larger structures — adapting mismatched interfaces, adding behavior without touching existing code, simplifying access to something complex, and controlling access to an object — almost entirely through **composition** rather than inheritance (Phase 1's "favor composition over inheritance," applied concretely). Several of these patterns are woven through the JDK and Spring so thoroughly that recognizing them turns a lot of framework "magic" into ordinary, predictable structure. All examples below are runnable in `StructuralPatterns.java`.

---

## Adapter — make incompatible interfaces work together

Wrap a class so its interface matches the one your code actually expects, letting two otherwise-incompatible components collaborate.

### Key Concepts
- **The problem**: a third-party or legacy API exposes a method shape that doesn't match what your code needs to call (`StripeApi.makeCharge(int cents)` vs. your own `PaymentProcessor.pay(int amount)`), and you can't (or shouldn't) modify the third-party class.
- **The Adapter wraps the incompatible class** and translates calls: it implements *your* interface, and internally delegates to the wrapped object, converting arguments/return values as needed.
- **Like a physical plug adapter** — it doesn't change what's on either side, it just sits between them and translates.
- **JDK examples**: `Arrays.asList(array)` adapts an array to the `List` interface; `InputStreamReader` adapts a byte-oriented `InputStream` to a character-oriented `Reader`.

### Worked Example

```java
// From StructuralPatterns.java
interface PaymentProcessor { String pay(int amount); }   // what OUR code wants
static class StripeApi { String makeCharge(int cents) { return "charged " + cents + " cents"; } }  // their API
static class StripeAdapter implements PaymentProcessor {
    private final StripeApi stripe;
    StripeAdapter(StripeApi stripe) { this.stripe = stripe; }
    public String pay(int amount) { return "paid " + amount + " via [" + stripe.makeCharge(amount * 100) + "]"; }
}

// usage:
PaymentProcessor pay = new StripeAdapter(new StripeApi());
assert pay.pay(100).contains("100");
```

In this example, calling code only ever sees `PaymentProcessor.pay(int amount)` — it has no idea `StripeApi` or `makeCharge(int cents)` exist. `StripeAdapter` is the only class that knows both interfaces; it converts dollars to cents (`amount * 100`) and delegates to `stripe.makeCharge`. If the payment provider were swapped for a different SDK entirely, only a new adapter class would need to be written — `PaymentProcessor`, and every caller of it, would be untouched.

### Why It's Useful
Adapter lets you integrate a third-party library, a legacy module, or any API you don't control without either modifying it (often impossible) or littering your codebase with its specific method shapes. It's also what keeps your own interfaces stable and clean even when the vendor behind them changes.

---

## Decorator — add behavior by wrapping

Attach additional responsibilities to an object **dynamically**, at runtime, by wrapping it in one or more objects that share the same interface — a flexible alternative to creating a new subclass for every combination of behavior.

### Key Concepts
- **Same interface, layered behavior** — a decorator implements the same interface as the object it wraps, holds a reference to the wrapped ("inner") instance, and adds its own behavior before or after delegating to the inner object.
- **Stacks freely** — because each decorator both *implements* and *wraps* the same interface, decorators compose: `new MilkDecorator(new SugarDecorator(new SimpleCoffee()))` layers milk on top of sugar on top of plain coffee, and each layer only needs to know about the one directly inside it.
- **Avoids subclass explosion** — without Decorator, supporting "coffee," "coffee+milk," "coffee+sugar," "coffee+milk+sugar" as separate combinations would require a new subclass for every combination; Decorator needs only one decorator class per *behavior*, freely combinable.
- **The JDK's `java.io` package IS this pattern**: `new BufferedReader(new FileReader(file))` — `BufferedReader` wraps and adds buffering on top of whatever `Reader` it's given, without needing to know it's specifically a `FileReader`.
- **Follows Open/Closed (Phase 2)**: adding a new flavor of behavior means adding a new decorator class, not modifying `SimpleCoffee` or any existing decorator.

### Worked Example

```java
// From StructuralPatterns.java
interface Coffee { double cost(); String description(); }
static class SimpleCoffee implements Coffee {
    public double cost() { return 2; }
    public String description() { return "coffee"; }
}
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

// usage:
Coffee c = new MilkDecorator(new SugarDecorator(new SimpleCoffee()));
assert c.cost() == 2 + 0.5 + 0.7;
assert c.description().contains("milk") && c.description().contains("sugar");
```

In this example, `c.cost()` resolves as a chain: `MilkDecorator.cost()` calls `inner.cost()` (which is the `SugarDecorator`), which calls *its* `inner.cost()` (the `SimpleCoffee`), giving `2`; `SugarDecorator` adds `0.5` to get `2.5`; `MilkDecorator` adds `0.7` to get `3.2`, matching `2 + 0.5 + 0.7`. `description()` builds up the same way, appending " + sugar" then " + milk" as the calls unwind back outward. Neither `MilkDecorator` nor `SugarDecorator` needed to know about `SimpleCoffee` specifically, or about each other — each only knows the shared `Coffee` interface.

### Why It's Useful
Behavior is composed at runtime rather than fixed at compile time in a class hierarchy — you can build exactly the combination a given request needs (`milk` only, `sugar` only, both, neither) with the same small set of decorator classes, instead of a combinatorial explosion of subclasses. It's the pattern behind `java.io` stream wrapping, and it's why chaining `new BufferedReader(new FileReader(f))` works the way it does.

---

## Facade — a simple front over a complex subsystem

Provide **one simplified interface** that hides the coordination of several subsystem parts, reducing how much a client needs to know to get something done.

### Key Concepts
- **Hides coordination, not capability** — the subsystem parts (`Inventory`, `Payment`, `Shipping`) still exist and still do real work; the Facade's job is to sequence calls to them correctly so the client doesn't have to know the right order or how the pieces relate.
- **Reduces coupling** — a client that only talks to `OrderFacade` depends on one class instead of three, and doesn't break if the internal coordination logic between `Inventory`/`Payment`/`Shipping` changes, as long as `OrderFacade`'s own interface stays stable.
- **Doesn't prevent direct access** — a Facade is a convenience layer, not a security boundary; code that genuinely needs finer control can still reach the subsystem classes directly if they're accessible.
- **Real-world examples**: Spring's `JdbcTemplate` is a facade over raw JDBC (connection handling, statement preparation, resource cleanup, exception translation all hidden behind simple `query`/`update` calls); most SDKs are facades over a raw HTTP API.

### Worked Example

```java
// From StructuralPatterns.java
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

// usage:
assert new OrderFacade().placeOrder("book").contains("confirmed");
```

In this example, the caller of `placeOrder("book")` never touches `Inventory`, `Payment`, or `Shipping` directly, and doesn't need to know that reserving inventory must happen before charging payment, or that shipping is scheduled last. `OrderFacade` owns that sequencing internally — if the correct order of operations ever changed (say, charging payment before reserving inventory), only `OrderFacade.placeOrder`'s body would need to change; the caller's code (`new OrderFacade().placeOrder("book")`) stays exactly the same.

### Why It's Useful
A Facade turns "here are five classes and the specific order you must call them in" into "here is one method." It's the difference between a client needing deep knowledge of a subsystem's internals and a client needing to know one clear entry point — exactly the role `JdbcTemplate` plays for raw JDBC, or the role a well-designed `*Service` class plays over several `*Repository` collaborators.

---

## Proxy — a stand-in that controls access

Provide a placeholder object with the **same interface** as a real object, which controls access to that real object — adding lazy loading, caching, logging, access control, or remote-call handling.

### Key Concepts
- **Same interface as the real subject** — a caller holding a `Proxy` can't tell it apart from the real object by type, since both implement the same interface; the difference is entirely in what the proxy does *around* delegating to the real object.
- **Common uses**: **lazy initialization** (defer creating an expensive real object until it's actually needed — the demo below), caching (return a cached result instead of recomputing), logging/auditing (record every call), access control (check permissions before delegating), and remote proxies (represent an object that actually lives on another machine, hiding the network call).
- **Hugely important in Spring**: `@Transactional` and `@Cacheable` work by wrapping your bean in a **dynamic proxy** — Spring generates a proxy class implementing your bean's interface (or subclassing it), and calls to your annotated methods actually go through the proxy first, which starts a transaction or checks the cache before delegating to your real method. JPA's lazy-loaded entity associations are also backed by proxies (a placeholder entity that fetches its data from the database only on first real access).
- **The Spring "self-invocation" gotcha follows directly from this**: calling a `@Transactional` method from *within the same class* doesn't go through the proxy — it's a direct Java method call on `this` — so the transactional behavior silently doesn't apply. Understanding proxies is what makes that surprising behavior make sense instead of feeling like a framework bug.

### Worked Example

```java
// From StructuralPatterns.java
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

// usage:
Image img = new LazyImageProxy("photo.jpg");
assert !LazyImageProxy.loaded;               // not loaded yet
img.display();
assert LazyImageProxy.loaded;                // loaded on first use
```

In this example, constructing `new LazyImageProxy("photo.jpg")` does **not** trigger `RealImage`'s expensive construction — `loaded` is still `false` right after. Only when `img.display()` is actually called does `LazyImageProxy` construct the real `RealImage` for the first time and delegate to it; every subsequent `display()` call reuses the already-constructed `real` instead of loading again. The caller holding `Image img` never had to know or care whether it was working with a real, eagerly-loaded image or a lazily-loaded proxy — the interface is identical either way.

### Why It's Useful
Proxy lets you insert cross-cutting behavior (lazy loading, caching, security checks, logging) *around* an object's real logic without modifying that logic itself, and without the caller needing to know it's happening. This is precisely the mechanism Spring AOP, `@Transactional`, `@Cacheable`, and JPA lazy loading all rely on — once you recognize "a proxy sits between the caller and the real object," a large amount of Spring's apparent magic becomes ordinary, explainable structure.

---

## Composite — treat individuals and groups uniformly

Compose objects into **tree structures** and let client code treat a single object (a leaf) and a composition of objects (a branch) through the **same interface**, without needing to know which one it's dealing with.

### Key Concepts
- **A common interface for both leaves and branches** — `FsNode.size()` is implemented by both `File` (a leaf, just returns its own byte count) and `Directory` (a branch, sums its children's `size()` recursively).
- **Recursion is what makes "uniform treatment" work** — a `Directory`'s `size()` doesn't need to know whether each child is a `File` or another `Directory`; it just calls `size()` on each child and lets that call resolve polymorphically, at any depth.
- **The client never special-cases "is this a leaf or a branch?"** — code that wants a total size just calls `.size()` on whatever `FsNode` it has, whether that's a single file or an entire deeply-nested directory tree.
- **Real-world examples**: a file system (the demo below), the Swing/AWT GUI component tree (a `Panel` contains `Component`s, some of which are themselves `Panel`s), the HTML DOM (an element can contain text nodes or further elements), nested menus.

### Worked Example

```java
// From StructuralPatterns.java
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

// usage:
Directory root = new Directory("root");
root.add(new File("a.txt", 10));
Directory sub = new Directory("sub");
sub.add(new File("b.txt", 20));
sub.add(new File("c.txt", 5));
root.add(sub);
assert root.size() == 35;                    // recurses into the tree
```

In this example, `root.size()` sums its direct children's sizes: `File("a.txt", 10).size()` returns `10` directly, and `sub.size()` (itself a `Directory`) recurses into *its* children, summing `20 + 5 = 25`. `root.size()` therefore evaluates to `10 + 25 = 35` — an arbitrarily deep tree collapses to a single recursive call from the caller's point of view, and `root.add(sub)` works because `Directory` is itself an `FsNode`, so a directory can be added as a child of another directory exactly the same way a file can.

### Why It's Useful
Composite eliminates a whole class of "is this a single item or a collection of items?" branching from client code — whether you have one file or a thousand nested directories, the calling code is `node.size()`, unchanged either way. This is exactly what makes recursive structures like file systems, UI component trees, and the DOM manageable: every operation is defined once, at the leaf and the composite level, and composition handles arbitrary depth for free.

---

## Bridge & Flyweight (know the names)

Two structural patterns that are asked about by name in interviews far more often than they're hand-implemented day to day — but the ideas behind them come up constantly.

### Bridge
- **Definition**: separate an **abstraction** from its **implementation** so the two can vary **independently**, connected via composition instead of inheritance.
- **The problem it solves**: if you have two dimensions of variation — say, several `Shape` types and several `Renderer` types (vector vs. raster) — modeling every combination as a subclass (`VectorCircle`, `RasterCircle`, `VectorSquare`, `RasterSquare`, ...) explodes combinatorially as either dimension grows.
- **The fix**: `Shape` holds a reference to a `Renderer` (composition) instead of each shape/renderer combination being a separate subclass. You can add a new `Shape` or a new `Renderer` independently, and every existing combination on the other axis keeps working with zero new classes.
- **Composition over multiplicative inheritance** — the same underlying discipline as Decorator, applied to two independently-varying dimensions instead of one wrapped chain.

### Flyweight
- **Definition**: share fine-grained objects to reduce memory usage when a program needs to represent a very large number of similar objects.
- **Intrinsic vs. extrinsic state**: split an object's data into **intrinsic** state (shared, identical across every use — e.g., a glyph's shape data for the character `'a'`) and **extrinsic** state (varies per use, passed in at the point of use — e.g., that glyph's on-screen position). Only intrinsic state is actually shared/cached; extrinsic state is supplied by the caller each time.
- **Real examples**: Java's `Integer` caching for values in the range −128..127 (`Integer.valueOf(100) == Integer.valueOf(100)` is `true` because both come from the same cached instance; this doesn't hold outside that range) is a Flyweight in the JDK itself. Glyph/character rendering in a text editor, where millions of character instances on screen share a small set of underlying glyph objects, is the classic textbook example.

---

## Comparison: Adapter vs. Decorator vs. Proxy vs. Facade

All four "wrap" another object, which makes them easy to confuse — the distinguishing factor is **intent**, not structure.

| Pattern | Changes the interface? | Adds behavior? | Typical intent |
|---|---|---|---|
| **Adapter** | Yes — converts one interface into a different one | No (just translates) | Make an incompatible class usable through the interface you need |
| **Decorator** | No — same interface as the wrapped object | Yes — adds responsibilities | Attach extra behavior dynamically, stackable |
| **Proxy** | No — same interface as the real object | Adds control (not new "business" behavior) | Control access: lazy-load, cache, secure, log, remote-call |
| **Facade** | Yes — a new, smaller interface over many classes | No (just coordinates existing behavior) | Simplify a whole complex subsystem behind one entry point |

## Perspective
Structural patterns are how you **integrate and extend without rewriting**: adapt what doesn't fit your interface (Adapter), decorate to layer on behavior (Decorator), simplify a tangle of collaborators behind one entry point (Facade), intercept access transparently (Proxy), and let a tree of parts be treated uniformly (Composite). Recognizing them in frameworks you didn't write — `java.io` stream wrapping is Decorator, `JdbcTemplate` is Facade, `@Transactional` is Proxy, the Swing component tree is Composite — turns a lot of "how does this even work" into "oh, this is just a pattern I already know."
