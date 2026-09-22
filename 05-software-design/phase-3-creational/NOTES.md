<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · solid](../phase-2-solid/NOTES.md) | [Phase 4 · structural ➡](../phase-4-structural/NOTES.md)
<!-- /nav -->

# Phase 3 — Creational Design Patterns: Notes

**Design patterns** are named, reusable solutions to recurring design problems, catalogued by the "Gang of Four" (Gamma, Helm, Johnson, Vlissides) in their 1994 book. They aren't magic — they're a shared vocabulary for structures that experienced engineers kept reinventing, and most of them are concrete embodiments of the SOLID principles from Phase 2. **Creational patterns** specifically address **how objects get created** — decoupling the code that *uses* an object from the code that *constructs* it, so `new SomeConcreteClass()` isn't scattered across the codebase and swapping one implementation for another doesn't require hunting down every call site. All five patterns below are runnable in `CreationalPatterns.java`.

---

## Singleton — one instance, globally accessible

Ensure a class has **exactly one instance** and provide a single, global point of access to it.

### Key Concepts
- **Use it for genuinely single resources** — something where having two instances would be a bug, not just inconvenient: an application-wide configuration registry, a connection pool, a hardware interface.
- **The enum form is the best Singleton implementation in Java** — `enum Config { INSTANCE; ... }` — because the JVM guarantees: the instance is created lazily (only on first reference to the enum), thread-safe (class initialization is synchronized by the JVM itself, no manual locking needed), and serialization-safe (a naive Singleton written with a private constructor and a static field can be broken by deserialization or reflection; an enum cannot).
- **Alternatives you should recognize**: an eager `static final` field (simple, but the instance is created even if never used); lazy initialization with double-checked locking and a `volatile` field (more code, error-prone if you get the memory-visibility details wrong).
- **Caution — Singleton is often an anti-pattern.** It's frequently just a global variable in disguise: it hides a class's real dependencies (nothing in a method signature tells you it silently reaches into `Config.INSTANCE`), and it makes unit testing harder (you can't easily substitute a test double for a globally-reached instance). Prefer **dependency injection** wherever practical — a Spring `@Component`/`@Service` bean *is* effectively a managed singleton, but it's *injected* into whatever needs it rather than reached via a global static reference, which keeps dependencies visible and swappable for tests.

### Worked Example

```java
// From CreationalPatterns.java
enum Config {
    INSTANCE;                                   // the single instance
    private final Map<String, String> values = new HashMap<>();
    void set(String k, String v) { values.put(k, v); }
    String get(String k) { return values.get(k); }
}

// usage:
Config.INSTANCE.set("env", "prod");
assert Config.INSTANCE.get("env").equals("prod");
assert Config.INSTANCE == Config.INSTANCE;       // always the same reference
```

In this example, `Config.INSTANCE` refers to the exact same object everywhere it's used in the program — `set("env", "prod")` from anywhere is visible to `get("env")` called from anywhere else, because there is only ever one underlying `HashMap`. The JVM guarantees this instance is created exactly once, the first time `Config` (the enum class) is touched, with no explicit synchronization code required.

### Why It's Useful
A single, shared instance is exactly right for things that must be unique by nature — you don't want two independent connection pools racing to manage the same database connections, or two independent copies of application configuration silently disagreeing with each other.

### Summary / Key Takeaways
- Singleton guarantees exactly one instance with a global access point.
- In Java, prefer the enum form — thread-safe, lazy, and serialization-safe for free.
- It's a double-edged pattern: genuinely useful for truly-single resources, but easy to misuse as a global variable that hides dependencies.
- Prefer DI (a framework-managed singleton bean, injected explicitly) over a hand-rolled global Singleton wherever a framework is available.

---

## Factory Method — create by type behind an abstraction

Define a method whose job is to decide **which concrete class to instantiate**, returning the result through a shared abstraction — so callers depend on the interface, not on the concrete classes.

### Key Concepts
- **Centralizes the "which concrete class" decision** in one place — the factory — rather than scattering `new Circle(...)` / `new Square(...)` calls (and the `if`/`switch` logic to choose between them) throughout client code.
- **Callers depend only on the abstraction** (`Shape`), never on `Circle` or `Square` directly — this is Dependency Inversion (Phase 2) applied to object creation.
- **Adding a new concrete type doesn't touch caller code** — only the factory's internal `switch`/`if` needs a new branch, and that's the *one* place in the whole codebase allowed to know about concrete shape classes. This is Open/Closed (Phase 2) in action: the factory itself must still be edited to add a case, but every *caller* of the factory is untouched.
- **JDK examples**: `Integer.valueOf(int)` (may return a cached instance instead of always `new Integer(...)`), `List.of(...)`, `Calendar.getInstance()` — all decide which concrete implementation to hand back, hidden behind a static factory method.

### Worked Example

```java
// From CreationalPatterns.java
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

// usage:
assert ShapeFactory.create("circle") instanceof Circle;
assert ShapeFactory.create("square").area() == 0;   // default size 0
```

In this example, the caller never writes `new Circle(1)` directly — it asks `ShapeFactory.create("circle")` and receives back a `Shape`. If a third shape type is added later, only `ShapeFactory.create`'s `switch` needs a new case; every existing caller that already works with `Shape` keeps working unmodified, because it never depended on `Circle` or `Square` by name in the first place.

### Why It's Useful
Object-creation logic (which concrete class, with what default arguments) is concentrated in one auditable place instead of duplicated wherever an object is needed. This also makes the concrete type swappable behind the scenes — for testing, for configuration-driven behavior (`create(config.getShapeType())`), or for a future refactor — without touching any calling code.

### Summary / Key Takeaways
- A factory method returns an abstraction; only the factory itself knows the concrete classes.
- Callers depend on the interface, never on `new SomeConcreteType()` directly.
- Adding a type means editing the factory (one place), not every call site.
- JDK static factories (`List.of`, `Integer.valueOf`) are real, everyday Factory Method usage.

---

## Builder — construct complex objects step by step

Provide a **fluent, chainable API** to assemble an object piece by piece, especially one with many optional parts, avoiding **telescoping constructors** and typically producing an **immutable** result.

### Key Concepts
- **Telescoping constructors are the problem Builder solves**: `new Pizza("medium", true, "mushroom", "olive", null, false, ...)` — a long, positional parameter list where it's easy to mix up argument order or forget which overload applies. Builder replaces this with named, chainable calls.
- **Required vs. optional parameters**: the required arguments go into the builder's constructor (`Builder("medium")`); optional ones become chainable methods with sensible defaults (`.cheese()`, `.topping(...)`), each returning `this` (or the builder) so calls can be chained.
- **The built object is typically immutable** — the `Pizza` constructor is private and only the `Builder` can call it, and once built, `Pizza`'s fields don't change; every field is set exactly once, from the builder's already-validated state.
- **JDK/ecosystem examples**: `StringBuilder` (append step by step, then `.toString()`), `Stream.Builder`, Lombok's `@Builder` annotation (generates this boilerplate for you), most HTTP client request builders (`HttpRequest.newBuilder()...build()`).
- **Records reduce the need for Builder in simple cases** — a record with a handful of required fields and no optional ones is often clearer as a plain record constructor; reach for Builder specifically when there are several *optional* parts or the construction needs to happen incrementally.

### Worked Example

```java
// From CreationalPatterns.java
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

// usage:
Pizza p = new Pizza.Builder("medium").cheese().topping("mushroom").topping("olive").build();
assert p.toppings.size() == 2 && p.cheese;
```

In this example, `size` is required and supplied to `Builder`'s constructor up front; `cheese` and `topping` are optional and only invoked when needed, each returning the builder itself so calls chain fluently. `build()` is the final step, and it's the *only* way to obtain a `Pizza` — the `Pizza` constructor is private, so there's no way to end up with a half-configured `Pizza` floating around; you either have a fully-built one or you're still mid-chain on the builder.

### Why It's Useful
Builder makes complex construction readable — `new Pizza.Builder("medium").cheese().topping("mushroom").topping("olive").build()` reads almost like a sentence describing the pizza, versus a positional constructor call where you'd have to check the parameter list to know what `true` or `null` in the fourth slot means. It also naturally prevents an invalid partial state, since the object only comes into existence, fully formed, at `.build()`.

### Summary / Key Takeaways
- Builder replaces telescoping/positional constructors with a fluent, named, chainable API.
- Required parameters go on the builder's constructor; optional ones become chainable methods with defaults.
- The result is typically immutable and only obtainable via `.build()`, preventing invalid partial states.
- `StringBuilder`, `Stream.Builder`, and Lombok's `@Builder` are Builder in everyday use.

---

## Abstract Factory — families of related objects

Provide an interface for creating **families of related objects** without specifying their concrete classes, **guaranteeing the members of a family are always used together consistently.**

### Key Concepts
- **The problem it solves**: if you have several related object types that must always be used as a matching set (a themed UI kit: a dark button must always pair with a dark checkbox, never a light one), Factory Method alone (one product at a time) doesn't guarantee consistency across the whole set — you could accidentally call the wrong factory for one of the pieces.
- **One concrete factory per family** — `DarkFactory` produces a matching `Button` *and* `Checkbox`, both dark; `LightFactory` produces both light. The client asks one factory object for everything in the family, so mixing is structurally impossible as long as it sticks to one factory instance.
- **Abstract Factory is Factory Method scaled up** — instead of one creation method, the factory interface declares one creation method *per product* in the family.
- **Heavier than a plain Factory Method** — reach for it specifically when you must produce *sets* of related things that need to stay consistent with each other, not for a single product type (which a Factory Method alone already covers).

### Worked Example

```java
// From CreationalPatterns.java
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

// usage:
GuiFactory dark = new DarkFactory();
assert dark.button().render().contains("dark");
assert dark.checkbox().render().contains("dark");
```

In this example, the client holds a single `GuiFactory` reference (`dark`) and asks it for both a `button()` and a `checkbox()` — both guaranteed to come from the same, consistent theme, because they're produced by the same concrete factory instance. There is no code path where a caller can accidentally combine `DarkFactory`'s button with `LightFactory`'s checkbox unless it deliberately holds two different factory references and mixes calls between them — the pattern's whole point is to make that mistake structurally awkward to make by accident.

### Why It's Useful
Whenever "these objects must always travel together, consistently" is a real requirement, Abstract Factory enforces it in the type system rather than relying on programmer discipline — switching an entire application's theme, target platform, or environment (dev/staging/prod client set) becomes swapping which single factory instance is used, everywhere downstream automatically staying consistent.

### Summary / Key Takeaways
- Abstract Factory produces whole families of related objects that must stay consistent with each other.
- One concrete factory implementation per family; the factory interface has one creation method per product.
- It's Factory Method generalized to multiple related products instead of one.
- Use it specifically when mismatched combinations (a dark button + a light checkbox) would be a real bug.

---

## Prototype — clone instead of build

Create new objects by **copying an existing instance** (a "prototype") rather than constructing one from scratch — useful when construction is expensive, or when you want a variant of an already-configured object.

### Key Concepts
- **Copy constructor or `clone()`** — the mechanism is usually a dedicated copy method (`Document.copy()`) or Java's `Cloneable`/`clone()` (less commonly used directly in modern Java due to its well-known design quirks; a copy constructor or a static factory is usually clearer).
- **Shallow vs. deep copy is the critical detail.** A shallow copy copies field *references* — for a mutable field (like a `List`), both the original and the copy would share the exact same underlying list, and mutating one would silently affect the other. A deep copy (or at least a defensive copy of mutable fields) creates independent copies of that mutable state.
- **When it's worth it**: construction that's genuinely expensive (parsing a large template, computing derived state) is a good candidate — clone the expensive pre-built object and tweak the copy, rather than paying the construction cost again from scratch.

### Worked Example

```java
// From CreationalPatterns.java
static class Document {
    String title;
    java.util.List<String> sections;
    Document(String title, java.util.List<String> sections) {
        this.title = title;
        this.sections = new java.util.ArrayList<>(sections);   // defensive copy
    }
    Document copy() { return new Document(title, sections); }  // copy constructor / clone
}

// usage:
Document original = new Document("template", java.util.List.of("intro", "body"));
Document copy = original.copy();
copy.title = "my-doc";
assert original.title.equals("template") && copy.sections.equals(original.sections);
```

In this example, `copy()` constructs a *new* `Document`, reusing `original`'s `title` and `sections` as the arguments to the regular constructor — and because that constructor itself defensively copies `sections` into a fresh `ArrayList` (`new java.util.ArrayList<>(sections)`), `copy.sections` is a genuinely independent list, not a shared reference to `original.sections`. Changing `copy.title` afterward has no effect on `original.title`, confirming the two documents are fully independent once cloned. Had `Document`'s constructor stored the `sections` reference directly instead of defensively copying it, `original` and `copy` would share the same underlying list, and mutating one's sections (`copy.sections.add(...)`) would silently corrupt the other — exactly the shallow-copy trap Prototype implementations must guard against.

### Why It's Useful
Prototype is handy for producing variants of a pre-configured "template" object — a default document template, a pre-parsed configuration, a pre-built object graph — without repeating the (possibly expensive or error-prone) construction logic for every variant.

### Summary / Key Takeaways
- Prototype creates new instances by copying an existing one instead of building from scratch.
- Watch shallow vs. deep copy carefully — a naive copy can leave the "copy" silently sharing mutable state with the original.
- Best used when construction is expensive or you need several variants of one configured baseline.
- Defensive copying (Java Phase 2.2) is the same discipline that makes Prototype implementations safe.

---

## Perspective

- **Patterns are tools, not goals.** Reaching for Abstract Factory or Builder where a plain constructor, a record, or a lambda would do is over-engineering — the YAGNI counterweight (Phase 1) applies to patterns just as much as to any other abstraction.
- **Modern Java softens the boilerplate of several of these**: a Strategy (Phase 5) or Factory Method is frequently just a lambda or method reference; simple Builders give way to records with a handful of required fields; DI containers (Spring) replace hand-rolled Singletons with managed, injected beans. The underlying *ideas* — decouple creation from use, guarantee family consistency, avoid telescoping constructors — remain valuable even as the code that expresses them gets shorter.
- **Creational patterns pair directly with Dependency Inversion (Phase 2)**: a factory returns an abstraction the client depends on, and the decision of *which* concrete class to instantiate is centralized in one place (or injected via a DI container) rather than scattered as `new` calls throughout the codebase.
