<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · creational](../phase-3-creational/NOTES.md) | [Phase 5 · behavioral ➡](../phase-5-behavioral/NOTES.md)
<!-- /nav -->

# Phase 4 — Structural Design Patterns: Notes

Structural patterns are about **composing** classes and objects into larger structures — adapting interfaces, adding behavior, simplifying, and controlling access — mostly through **composition** (the "composition over inheritance" principle). Many are woven through the JDK and Spring, so recognizing them helps you read those libraries.

## The patterns (runnable in `StructuralPatterns.java`)

### Adapter — make incompatible interfaces work together
Wrap a class so its interface matches what your code expects. You adapt a third-party/legacy API (`StripeApi.makeCharge`) to your interface (`PaymentProcessor.pay`). It's the "plug adapter" of code — lets otherwise-incompatible components collaborate. (JDK: `Arrays.asList`, `InputStreamReader` adapts a byte stream to a char reader.)

### Decorator — add behavior by wrapping
Attach responsibilities to an object **dynamically** by wrapping it in objects sharing the same interface — a flexible alternative to a subclass explosion. `new MilkDecorator(new SugarDecorator(new SimpleCoffee()))` stacks behavior; each layer adds cost/description and delegates to the inner one. **The JDK's `java.io` IS this:** `new BufferedReader(new FileReader(...))`. Follows Open/Closed (extend by wrapping, not modifying).

### Facade — a simple front over a complex subsystem
Provide one simplified interface that hides the coordination of many subsystem parts (`OrderFacade.placeOrder` hides inventory + payment + shipping). Reduces coupling between clients and the subsystem. (Spring's `JdbcTemplate` is a facade over raw JDBC; most SDKs are facades.)

### Proxy — a stand-in that controls access
A placeholder with the same interface as the real object, adding control: **lazy loading** (create the expensive object on first use — the demo), caching, logging, access control, or representing a remote object. **Hugely important in Spring:** `@Transactional`, `@Cacheable`, and Spring AOP work by wrapping your bean in a dynamic **proxy**; JPA lazy-loading uses proxies too. Understanding proxy explains a lot of "magic."

### Composite — treat individuals and groups uniformly
Compose objects into **trees** and let clients treat a single object and a composition the same way (both implement one interface). The file-system demo: `File` (leaf) and `Directory` (composite) both have `size()`; a directory recurses over its children. (JDK: the Swing/AWT component tree; the HTML DOM; menus.)

### Bridge & Flyweight (know the names)
- **Bridge** — separate an abstraction from its implementation so they vary independently (e.g., a `Shape` hierarchy and a `Renderer` hierarchy connected by composition, not a combinatorial explosion of subclasses). Composition over multiplicative inheritance.
- **Flyweight** — share fine-grained objects to save memory when you have millions of similar ones (e.g., `Integer` caching −128..127, glyphs in a text editor). Split intrinsic (shared) from extrinsic (per-use) state.

## Perspective
- Structural patterns are how you **integrate and extend** without rewriting: adapt what doesn't fit, decorate to add behavior, facade to simplify, proxy to intercept, composite to nest.
- **Adapter vs Decorator vs Proxy vs Facade** all "wrap" but differ in intent: Adapter *changes* the interface; Decorator *adds behavior* (same interface); Proxy *controls access* (same interface); Facade *simplifies* a whole subsystem (new, smaller interface). Knowing the intent distinction is a classic interview point.
