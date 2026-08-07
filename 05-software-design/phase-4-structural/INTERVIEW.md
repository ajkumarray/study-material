<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · creational](../phase-3-creational/NOTES.md) | [Phase 5 · behavioral ➡](../phase-5-behavioral/NOTES.md)
<!-- /nav -->

# Phase 4 — Structural Patterns: Interview Q&A

⭐ = asked constantly.

**Q: What is the Adapter pattern?** ⭐
It converts one interface into another that the client expects, letting incompatible classes work together. You wrap a third-party/legacy class so it conforms to your interface (e.g., adapt a payment SDK to your `PaymentProcessor`). Like a physical plug adapter.

**Q: What is the Decorator pattern, and where's it in the JDK?** ⭐⭐
It adds responsibilities to an object dynamically by wrapping it in objects sharing the same interface — a flexible alternative to subclassing. Java I/O is the canonical example: `new BufferedReader(new FileReader(f))` stacks buffering onto file reading. Each decorator adds behavior and delegates inward. Follows Open/Closed.

**Q: Adapter vs Decorator vs Proxy vs Facade — what's the difference?** ⭐⭐
They all wrap, but intent differs: Adapter *changes* an interface to a different one; Decorator *adds behavior* while keeping the same interface; Proxy *controls access* (lazy/cache/security) with the same interface; Facade *simplifies* a whole subsystem behind a new, smaller interface. Intent, not structure, distinguishes them.

**Q: What is a Proxy, and how does Spring use it?** ⭐⭐
A stand-in with the same interface as a real object that controls access — lazy loading, caching, logging, security, or remoting. Spring uses dynamic proxies for `@Transactional`, `@Cacheable`, and AOP: it wraps your bean so cross-cutting behavior runs around your methods. JPA lazy-loading also uses proxies. (This is why calling a `@Transactional` method from within the same class bypasses it — the call doesn't go through the proxy.)

**Q: What is the Facade pattern?**
A single simplified interface over a complex subsystem, hiding the coordination of its parts and reducing client coupling. Examples: `JdbcTemplate` over raw JDBC, a service class over several repositories, most SDKs over an HTTP API.

**Q: What is the Composite pattern?** ⭐
It composes objects into tree structures and lets clients treat individual objects (leaves) and compositions (branches) uniformly through a common interface — e.g., files and directories both answering `size()`, with directories recursing. Used for the DOM, GUI component trees, and menus.

**Q: When would you use Bridge?**
When two dimensions vary independently and inheritance would explode combinatorially — e.g., shapes × renderers. Bridge separates the abstraction (Shape) from the implementation (Renderer) via composition, so you add shapes and renderers independently rather than creating a subclass per pairing.

**Q: What problem does Flyweight solve?**
Memory: when you have huge numbers of similar objects, share the common (intrinsic) state and pass the varying (extrinsic) state per operation. Java's `Integer` cache (−128..127) and glyph objects in text rendering are examples. It trades some complexity for large memory savings.

**Q: Why prefer composition (these patterns) over inheritance?**
Composition is more flexible and less coupled — you assemble behavior at runtime (decorate, adapt, bridge) instead of locking it into a class hierarchy, avoiding fragile base classes and combinatorial subclass explosion. Most structural patterns are composition-based, reflecting "favor composition over inheritance" (Java Phase 2 / SOLID).
