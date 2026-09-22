<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · creational](../phase-3-creational/NOTES.md) | [Phase 5 · behavioral ➡](../phase-5-behavioral/NOTES.md)
<!-- /nav -->

# Phase 4 — Structural Patterns: Interview Q&A

⭐ = asked constantly.

**Q: What is the Adapter pattern?** ⭐
It converts one interface into another that the client expects, letting two otherwise-incompatible classes collaborate. You wrap a third-party or legacy class — one you typically can't or shouldn't modify — behind a class that implements *your* interface and internally translates calls to the wrapped API's shape. The classic example: wrapping a payment SDK's `makeCharge(int cents)` behind your own `PaymentProcessor.pay(int amount)`, converting units and delegating. It's the plug-adapter of code — nothing about either side changes, something just sits between them translating.

```java
interface PaymentProcessor { String pay(int amount); }
class StripeAdapter implements PaymentProcessor {
    private final StripeApi stripe;
    public String pay(int amount) { return stripe.makeCharge(amount * 100); }  // dollars -> cents
}
```

**Q: What is the Decorator pattern, and where's it in the JDK?** ⭐⭐
It adds responsibilities to an object dynamically, at runtime, by wrapping it in one or more objects that share its interface — each decorator delegates to the object it wraps and adds its own behavior before or after that delegation. It's a flexible alternative to subclassing every combination of behavior you might need. Java I/O is the canonical real-world example: `new BufferedReader(new FileReader(f))` wraps buffering around file reading, and neither class needs to know the specific concrete type on the other side — only the shared `Reader`/`Writer` interface. Follows Open/Closed: adding a new flavor of behavior means writing a new decorator class, never modifying `SimpleCoffee` or any existing decorator.

*Follow-up: "Why is Decorator preferable to subclassing here?"* Because behaviors combine. Supporting "coffee," "coffee+milk," "coffee+sugar," and "coffee+milk+sugar" via subclassing needs four (or more, as options grow) separate subclasses; Decorator needs exactly one class per *behavior*, and you compose them at runtime — `new MilkDecorator(new SugarDecorator(new SimpleCoffee()))`.

**Q: Adapter vs. Decorator vs. Proxy vs. Facade — what's the difference?** ⭐⭐
They all wrap another object, but their *intent* differs completely, which is the actual distinguishing factor (not structure — structurally several of them look almost identical). Adapter *changes* an interface into a different one the client needs. Decorator *adds behavior* while deliberately keeping the *same* interface, and is meant to stack. Proxy *controls access* — lazy loading, caching, security, logging, remoting — while also keeping the same interface as the real object, but without adding new "business" behavior of its own. Facade *simplifies* an entire subsystem of several classes behind one new, smaller interface, hiding coordination rather than wrapping a single object.

| Pattern | Interface | Purpose |
|---|---|---|
| Adapter | Different from the wrapped class | Compatibility |
| Decorator | Same as the wrapped object | Add behavior, stackable |
| Proxy | Same as the real object | Control access |
| Facade | New, smaller, over many classes | Simplify a subsystem |

**Q: What is a Proxy, and how does Spring use it?** ⭐⭐
A stand-in object with the same interface as a real object, which controls access to that real object — lazy loading (defer expensive construction until first use), caching, logging, security checks, or standing in for a remote object. Spring relies on dynamic proxies for `@Transactional`, `@Cacheable`, and AOP in general: it generates a proxy that implements (or extends) your bean, and calls to your annotated methods actually hit the proxy first, which starts a transaction or checks a cache before delegating to your real method body. JPA's lazy-loaded entity associations are backed by proxies too — a placeholder entity object that only fetches from the database on first genuine access to a lazy field.

*Follow-up: "Why does calling a `@Transactional` method from within the same class not work?"* Because that's a direct Java method call on `this`, which never passes through the Spring-generated proxy — the proxy only intercepts calls that come in from *outside* the bean, through the proxy reference Spring wired into the application context. Understanding "a proxy sits between the caller and the real object, but only for calls that actually go through the proxy" is what makes this well-known gotcha predictable instead of feeling like a bug.

**Q: What is the Facade pattern?**
A single, simplified interface placed over a complex subsystem, hiding the coordination between several parts so the client only needs to know one entry point. `OrderFacade.placeOrder(item)` internally sequences `Inventory.reserve`, `Payment.charge`, and `Shipping.schedule` in the correct order — the caller never touches those three classes directly and doesn't need to know the required call order. Real examples: `JdbcTemplate` is a facade over raw JDBC (connection handling, statement prep, resource cleanup, exception translation all hidden behind `query`/`update`); most SDKs are facades over a raw HTTP API; a well-designed `*Service` class is often a facade over several `*Repository` collaborators.

*Follow-up: "Does a Facade prevent direct access to the subsystem?"* No — it's a convenience layer, not an access-control boundary. Code that genuinely needs finer-grained control can still reach `Inventory`/`Payment`/`Shipping` directly if they're visible; Facade just means most callers don't have to.

**Q: What is the Composite pattern?** ⭐
It composes objects into tree structures and lets clients treat an individual object (a leaf) and a composition of objects (a branch) uniformly, through one shared interface — neither the client nor, critically, the branch's own implementation needs to special-case "is this child a leaf or another branch?" The file-system demo: `File` and `Directory` both implement `FsNode.size()`; `File.size()` returns its own byte count directly, while `Directory.size()` recurses by summing `size()` over its children, whatever type each child actually is. Used for the DOM, GUI component trees (Swing/AWT), and nested menus — anywhere you have a naturally recursive/hierarchical structure that should be operated on the same way regardless of depth.

```java
interface FsNode { int size(); }
class Directory implements FsNode {
    public int size() { return children.stream().mapToInt(FsNode::size).sum(); }  // recurses either way
}
```

**Q: When would you use Bridge?**
When two dimensions of a design vary independently and modeling every *combination* as a subclass would explode combinatorially — e.g., several shape types crossed with several renderer types. Bridge separates the abstraction (`Shape`) from its implementation (`Renderer`) via composition — `Shape` holds a `Renderer` reference rather than each shape/renderer pairing being its own subclass — so you can add a new shape or a new renderer independently, and every existing combination on the other axis keeps working without any new classes.

**Q: What problem does Flyweight solve?**
Memory, when a program needs an enormous number of similar objects. The fix is splitting an object's state into intrinsic (shared, identical across uses — cached and reused) and extrinsic (varies per use — passed in by the caller at the point of use) parts, so only one shared copy of the intrinsic data exists no matter how many logical "instances" the program appears to have. Java's own `Integer` cache for values −128..127 is a real Flyweight in the JDK — `Integer.valueOf(100) == Integer.valueOf(100)` is `true` because both calls return the same cached object, though this guarantee doesn't extend outside that cached range. Text editors sharing glyph objects across millions of on-screen characters is the textbook example.

**Q: Why prefer composition (these patterns) over inheritance?**
Composition is more flexible and produces looser coupling — behavior is assembled at runtime (decorate, adapt, bridge, wrap in a proxy) rather than locked into a fixed class hierarchy decided at compile time. It avoids the "fragile base class" problem (a change to a shared superclass rippling unpredictably into every subclass) and the combinatorial subclass explosion that shows up whenever two or more dimensions of variation need to be supported together (exactly what Bridge and Decorator solve directly). Nearly every structural pattern in this phase is composition-based, which is why they're often introduced as the concrete, practical face of "favor composition over inheritance" (Phase 1 / SOLID).

**Q: If you saw `new BufferedReader(new InputStreamReader(new FileInputStream(f)))` in the JDK, which patterns are actually at play?**
Two, stacked: `InputStreamReader` is an Adapter — it converts a byte-oriented `InputStream` into a character-oriented `Reader`, a genuine interface change. `BufferedReader` wrapping that `Reader` is a Decorator — it keeps the same `Reader` interface and adds buffering behavior on top, and could just as easily wrap a `FileReader` or any other `Reader` implementation, not specifically an `InputStreamReader`. Recognizing the two distinct roles in one line of "boilerplate" code is a good sign you understand the intent distinction, not just the pattern names.
