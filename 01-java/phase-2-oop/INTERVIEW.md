<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · basics](../phase-1-basics/NOTES.md) | [Phase 3 · core apis ➡](../phase-3-core-apis/NOTES.md)
<!-- /nav -->

# Phase 2 — OOP: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 2.1 — Classes, objects, constructors

**Q: Class vs object?**
Class: compile-time blueprint — field/method definitions. Object: runtime instance on the heap with its own field values. One class, many independent objects.

**Q: What exactly happens at `new BankAccount("Ajay")`?**
Heap memory for the fields is allocated and zero-defaulted → the matching constructor runs (after implicitly running the parent chain first) → a reference to the finished object is returned into your variable. The object never lives "in" the variable — only the reference does.

**Q: Constructor vs method?** ⭐
Constructor: class's exact name, **no return type — not even void** (add `void` and it silently becomes a normal method: beloved trick question), invoked only via `new`, runs once per object, not inherited, can't be `abstract`/`final`/`static`. Both can be overloaded.

**Q: When do you NOT get a default constructor?** ⭐
Declare zero constructors → compiler gifts a public no-arg one. Declare *any* → gift revoked; `new Thing()` breaks unless you add the no-arg form back. Root cause of many "worked yesterday" compile errors.

**Q: Uses of `this`?**
Disambiguating shadowed fields (`this.x = x`), constructor chaining (`this(...)` — first statement only), passing the current object out (`register(this)`), fluent returns (`return this`).

**Q: Why would a constructor be `private`?**
To control instantiation: singletons, static factory methods (`List.of`, `LocalDate.of` — expressive names, caching, may return subtypes), non-instantiable utility classes.
*Follow-up: static factories vs constructors is Effective Java Item 1 — worth reading.*

**Q: Stack vs heap?** ⭐
Stack: per-thread; frames with locals/params/return addresses; freed automatically on return; overflow → `StackOverflowError`. Heap: shared; all objects and arrays; reclaimed by GC; exhaustion → `OutOfMemoryError`. `BankAccount a = new BankAccount()` → object on heap, reference `a` in the frame.

## 2.2 — Encapsulation & access control

**Q: What is encapsulation, really?** ⭐
Private state + public behavior + rules enforced at the boundary. Wins: invariants can't be bypassed; internals can change without breaking callers; every state change flows through methods you can log/validate/breakpoint.

**Q: Encapsulation vs abstraction?** ⭐
Abstraction hides *how it works* behind *what it does* (interface/contract level — `List` regardless of array or linked backing). Encapsulation hides *the data itself* (access-control level — private fields). Abstraction designs the contract; encapsulation defends it.

**Q: The four access modifiers?** ⭐
`public` → everywhere; `protected` → package **+ subclasses anywhere**; default → package only; `private` → class only. Trap: protected is *wider* than default. Top-level classes: public or default only.

**Q: Why getters/setters over public fields?**
A public field is an eternal, uncontrolled promise. Accessors allow validation, computed values, logging, representation changes, and asymmetry (getter without setter = read-only). Frameworks (Spring/JPA/Jackson) bind via the JavaBeans `getX`/`setX`/`isX` convention.

**Q: Can `private` state leak?** ⭐ *junior/senior separator*
Yes: return a mutable field (array, `List`, `Date`) and callers hold live internals — mutation bypasses every setter. `private` protects the variable, not the referenced object. Fix: defensive copies both directions, or immutable field types (`List.copyOf`, `LocalDate`).

**Q: Design an immutable class.** ⭐
`final` class; `private final` fields; no setters; defensive copies of mutable inputs (constructor) and outputs (getters); "mutators" return new instances. Gains: thread safety with zero locks, safe sharing/caching, stable hash keys. Records generate the pattern.
*Follow-up: "Is a record deeply immutable?" — no, shallow: a `List` component is still mutable unless you copy it in the compact constructor.*

## 2.3 — Inheritance & polymorphism

**Q: The four pillars of OOP?** ⭐ *the opener*
Encapsulation (hide state behind guarded methods), Inheritance (IS-A reuse), Polymorphism (parent-typed code runs subtype behavior), Abstraction (expose what, hide how). Prepare a one-liner + tiny example each.

**Q: Overriding vs overloading?** ⭐⭐
Overloading: same name, different parameter lists, resolved at **compile time** — static polymorphism. Overriding: same signature, subclass replaces implementation, resolved at **runtime** via dynamic dispatch. One-liner: *the compiler picks overloads; the JVM picks overrides.*

**Q: Explain dynamic dispatch.** ⭐
`Shape s = new Circle(5)`: compile-time type `Shape` gates what's callable; runtime type `Circle` chooses what runs. The JVM's `invokevirtual` resolves the method in the actual object's class — conceptually a vtable lookup where overrides replace parent slots. In Java every non-private, non-static, non-final instance method is virtual by default (unlike C++).
*Follow-up: "Do fields dispatch?" — No: shadowed fields resolve by the variable's type. Methods only.*

**Q: Rules for a valid override?**
Same name/params; covariant (subtype) return allowed; access may widen, never narrow; no new/broader checked exceptions; `private`/`static`/`final` not overridable. Always `@Override` — makes signature typos compile errors.
*Follow-up: "Overriding a static method?" — that's* hiding: *resolved by reference type at compile time. `Parent.staticM()` vs `Child.staticM()` — the object doesn't matter.*

**Q: Why no multiple class inheritance in Java?** ⭐
Diamond problem: two parents with state and the same method — which wins? Java: one class parent (single inheritance of state), unlimited interfaces (multiple inheritance of type; of *behavior* too since default methods — with mandatory explicit resolution of conflicts).

**Q: Composition vs inheritance — when each?** ⭐ *design maturity probe*
Inheritance couples you to parent internals (fragile base class); it's justified only for true IS-A with substitutability (Liskov: any subtype must behave acceptably wherever the parent is expected — `Square extends Rectangle` with setters famously fails). Default to composition: HAS-A a collaborator and delegate. Slogan: *inherit to be reused, compose to reuse.*

**Q: Why not call overridable methods from a constructor?** *senior probe*
The child's override runs *before* the child's fields initialize (parent constructor executes first) — it observes half-built state. Constructors should call only `private`/`final`/`static` methods.

**Q: Upcasting vs downcasting?**
Up (child→parent): implicit, always safe. Down: explicit, runtime-checked, `ClassCastException` on a wrong guess — guard with `instanceof` pattern matching (`if (s instanceof Circle c)`). Routine downcasting signals a design problem; polymorphism or sealed-switch usually removes it.

## 2.4 — Abstract classes & interfaces

**Q: Abstract class vs interface?** ⭐⭐ *guaranteed*
Abstract class: instance state, constructors, any modifiers, shared machinery — but single inheritance. Interface: stateless contract, implicitly public members, implement many. Choose: capability → interface; shared state/skeleton among close family → abstract class; unsure → interface. Modern JDK combines both: `List` (contract) + `AbstractList` (skeleton).

**Q: Can an abstract class have constructors? Zero abstract methods? Be final?**
Constructors yes — run via subclass `super(...)`. Zero abstract methods yes — blocks instantiation only. `abstract final` — contradiction, won't compile (one demands subclassing, the other forbids it).

**Q: Why were default methods added?** ⭐
Interface evolution: Java 8 needed `sort`, `forEach`, `stream` on decades-old interfaces without breaking every implementor on earth. Default methods = body in the interface, inherited free, overridable.
*Follow-up: "Same default from two interfaces?" — compile error until the class overrides; disambiguate inside via `InterfaceName.super.method()`.*

**Q: What can an interface contain in modern Java?**
Abstract methods; `default` methods; `static` methods; `private` methods (9+, shared code for defaults); constants (implicitly `public static final`). Never instance fields — state stays with classes.

**Q: Template method pattern?**
Abstract parent fixes the algorithm in a `final` method calling abstract "hole" methods children implement. Guarantees flow, customizes steps. Everywhere in frameworks (JUnit lifecycle, Spring templates).

**Q: What is a marker interface?**
An empty interface tagging a type (`Serializable`, `Cloneable`) for runtime `instanceof` checks. Modern alternative: annotations — but markers participate in the type system (`<T extends Serializable>` works; an annotation can't do that).

## 2.5 — equals, hashCode, toString

**Q: The equals/hashCode contract?** ⭐⭐ *guaranteed*
`a.equals(b)` ⇒ identical hashCodes (converse not required; collisions legal). equals: reflexive, symmetric, transitive, consistent, false vs null.
*Follow-up: "Break it — what happens in a HashMap?" — hashCode picks the bucket, equals resolves within it; equal-but-different-hash objects occupy different buckets → phantom duplicates, failed lookups. Silent wrongness, no exception.*

**Q: Walk me through a correct equals.**
`==` fast path → `instanceof` pattern (null-safe) → `Objects.equals` per defining field. hashCode: `Objects.hash` over the *same* fields. In practice: record, `final` class + IDE generation. Hand-rolled drift between equals and hashCode is a real production-bug genre.

**Q: What if you mutate a field used in hashCode while the object is in a HashSet?** *senior probe*
The object was filed under its old hash bucket; lookups now compute the new hash and probe elsewhere → `contains` false, can't remove, effectively lost until rehash. Moral: hash-collection keys must be immutable (or at least never mutated while stored).

**Q: `instanceof` vs `getClass()` in equals?** *depth probe*
`instanceof` permits subclass equality but breaks symmetry when a subclass adds fields + its own equals (`base.equals(sub)` ≠ `sub.equals(base)`). `getClass()` is strictly symmetric but forbids any cross-class equality. Escape the dilemma: `final` value classes / records.

**Q: Integer `==` trap?** ⭐
`Integer a = 127, b = 127; a == b` → true (JVM caches −128…127); `Integer a = 128, b = 128; a == b` → **false** (distinct boxes). Autoboxing hides object creation. Always `.equals()` (or unbox) for wrapper comparison. Full story with collections in Phase 3.

## 2.6 — Enums, records, sealed

**Q: What is an enum under the hood?** ⭐
A final class extending `java.lang.Enum`; each constant a `public static final` singleton created during class initialization (thread-safe by the JLS class-init guarantee) — hence safe `==` comparison and "enum is the best singleton implementation" (Effective Java).
*Follow-ups: fields/constructors (implicitly private)/methods — yes; per-constant class bodies — yes (strategy per constant); extend a class — no (already extends Enum); implement interfaces — yes.*

**Q: Why never persist `ordinal()`?**
It's just declaration position. Insert/reorder constants and every stored ordinal silently points at the wrong constant. Persist `name()` or an explicit code field. (JPA: `@Enumerated(STRING)`, never `ORDINAL`.)

**Q: What do records generate — and their restrictions?** ⭐
From `record Money(long amount, String currency)`: private final fields, canonical constructor, `amount()`/`currency()` accessors, value equals/hashCode, toString. Restrictions: no extra instance fields; implicitly final; extends `Record` (no other superclass); interfaces ok. Validation in the **compact constructor** (`Money { if (amount < 0) throw ...; }` — runs before assignment, may normalize parameters).
*Follow-up: "Record vs Lombok?" — records are language-level, semantic (value class), with pattern-matching support; Lombok is codegen convenience on a mutable-class model.*

**Q: What problem do sealed types solve?** ⭐ *modern-Java signal*
Closed hierarchies: `sealed ... permits A, B, C` tells the compiler *all* subtypes. Pattern-matching switch becomes exhaustive **without default** — adding a subtype breaks every switch at compile time instead of slipping through a default at runtime. Permitted types: `final` | `sealed` | `non-sealed`. Sealed interface + records ≈ algebraic data types (Result/State/Event modeling).

## 2.7 — Nested classes & static

**Q: Static nested vs inner class?** ⭐
Static nested: namespaced class, no outer instance (`new Outer.Nested()`) — the default. Inner: each instance bound to an outer instance (`outer.new Inner()`), holds a hidden reference, reads outer privates. That hidden reference **pins the outer object** — leak classic (Android non-static Handler). Rule: static unless you need the link.

**Q: What is an anonymous class, and what replaced it?**
Unnamed class defined + instantiated in one expression (`new Greeter() { ... }`). Lambdas replaced it for single-abstract-method interfaces; still needed to extend a class or override multiple methods inline.
*Follow-up: locals captured by anonymous/local classes (and lambdas) must be* effectively final *— the capture is a copy; allowing reassignment would desynchronize the copies.*

**Q: Initialization order — static blocks, instance blocks, constructors?**
Class load (first touch, once): static field inits + static blocks, textual order. Each `new`: instance field inits + instance blocks, textual order → constructor body. With inheritance: parent statics → child statics → parent instance+ctor → child instance+ctor.
*Follow-up: "When does a class load?" — lazily: first instantiation, static access, or `Class.forName` — not at JVM startup.*

**Q: Why can't static methods use `this`?**
They belong to the class and run with no instance in existence — there's nothing for `this` to denote. Static → instance requires being handed/creating an object; instance → static is always fine (exactly one shared copy exists).

**Q: Is Java's `main` in a class — so how does OOP square with a static entry point?** *conceptual*
`static main` bootstraps before any object exists; from there you construct the object graph and hand off (`new App().run()`). Frameworks (Spring Boot) do exactly this behind `SpringApplication.run(App.class)`.
