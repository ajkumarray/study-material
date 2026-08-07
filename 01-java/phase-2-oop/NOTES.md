<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · basics](../phase-1-basics/NOTES.md) | [Phase 3 · core apis ➡](../phase-3-core-apis/NOTES.md)
<!-- /nav -->

# Phase 2 — Object-Oriented Programming: Notes

## 2.1 — Classes, objects, constructors, `this`

**Class = compile-time blueprint; object = runtime instance** with its own copy of the fields. The point of OOP in one sentence: *data and the rules that keep it valid live in one place* (a `BankAccount` that cannot go negative because `withdraw` checks).

**`new` does three things:** heap allocation (fields start at zero-defaults) → constructor runs → reference returned. Objects live on the **heap** (shared, GC-managed); references and primitive locals live in the current thread's **stack frame**. Copying a variable copies the reference — two variables, one object (1.4's photocopied key). Dropping the last reference makes the object *unreachable* — eligible for GC; there's no `delete` in Java. *(How that reclamation actually works — generational heap, minor/major GC, the collectors, tuning — is Phase 6.4.)*

**Constructors:**
- Same name as class, **no return type at all** (adding `void` silently makes it an ordinary method — trick-question classic).
- Overloadable; **chaining** with `this(...)` — must be the first statement — funnels all initialization through one canonical constructor.
- **Default constructor rule:** no constructors declared → compiler supplies a public no-arg one; declare *any* → the freebie vanishes.
- Not inherited; each constructor implicitly starts with `super()` unless you write `this(...)` or `super(...)` yourself.

**`this`:** (1) disambiguate shadowed names (`this.owner = owner`); (2) chain constructors; (3) hand the current object to other code; (4) `return this` for fluent APIs/builders.

**Fields vs locals:** fields get defaults; locals demand definite assignment. `final` field = assigned exactly once (declaration or every constructor path).

## 2.2 — Encapsulation

**Encapsulation = hide state; force all access through methods that enforce the rules.** Payoffs: invariants in one unbypassable place; internals swappable without breaking callers (computed `yearlyBonus()` looks identical to a stored field); every write funnels through a setter → one breakpoint sees all changes.

**Access modifiers (widest → narrowest):**

| modifier | same class | same package | subclass (other pkg) | world |
|---|---|---|---|---|
| `public` | ✓ | ✓ | ✓ | ✓ |
| `protected` | ✓ | ✓ | ✓ | ✗ |
| *(default)* | ✓ | ✓ | ✗ | ✗ |
| `private` | ✓ | ✗ | ✗ | ✗ |

Note `protected` = package access **plus** subclasses — wider than default, a common misconception. Top-level classes: only `public` or default. Rule: **start private, widen reluctantly.** Packages are the unit the default modifier protects — design packages as modules with small public surfaces.

**Getters/setters done right:** not a reflexive pair per field — each accessor is an API decision. `final` + no setter = read-only. Setters validate (`IllegalArgumentException`); constructors *reuse* setters to inherit the validation. JavaBeans naming (`getX`/`setX`/`isX`) matters because Spring/JPA/Jackson discover properties by it reflectively.

**The leak:** `private` guards the *variable*, not the *object it references*. A getter returning a mutable field (array/`List`/`Date`) hands out live internals — callers mutate your state with no setter involved. Fixes: **defensive copies in and out**, or store immutable types (`List.copyOf`, `LocalDate`) so there's nothing to defend.

**Immutable class recipe:** `private final` fields; no setters; defensive-copy mutable inputs/outputs; `final` class. "Mutators" return new instances (`String`, `BigDecimal`, `Point.translate`). Free thread safety, safe sharing/caching, reliable map keys. `record` automates it.

## 2.3 — Inheritance & polymorphism

**Inheritance** (`extends`) = IS-A + implementation reuse. Single class inheritance; every class ultimately extends `Object`.

**Construction order:** `super(...)` first statement (or compiler-inserted `super()` — compile error if the parent lacks a no-arg constructor). Parents fully initialize before children. Pitfall worth knowing: calling an overridable method from a constructor runs the *child's* override before the child's fields are initialized — never call overridables in constructors.

**Dynamic dispatch — the core mechanism.** Compile-time (variable) type: what you *may call*. Runtime (object) type: what *actually runs*. The JVM's `invokevirtual` looks the method up in the actual object's class — conceptually a per-class **vtable** (method table); overrides replace the parent's slot. This lookup is what makes `totalArea(Shape[])` work for subtypes written years later — the **Open/Closed Principle**.

- **Fields never dispatch** — a shadowed field reads by the *variable's* type. Only instance methods are virtual. (In Java, all non-`private`/`static`/`final` methods are virtual by default — opposite of C++.)
- **`@Override` always:** a typo'd signature silently creates a *new* method; the annotation turns that into a compile error.
- **Overriding rules:** same signature; **covariant returns** (subtype) allowed; access widen-only; no new/broader checked exceptions; `private`/`static`/`final` methods aren't overridable (`static` gets *hidden* instead — resolved by reference type, a trap).
- **Casting:** upcast implicit/safe; downcast explicit, runtime-checked → `ClassCastException`. Prefer `instanceof` pattern (`if (s instanceof Circle c)`). Needing frequent downcasts = design smell; polymorphism or sealed+switch usually removes it.
- **`final`:** class → can't extend (`String` — protects immutability); method → can't override (locks invariants, enables template method).

**Composition over inheritance:** inheritance is the *strongest* coupling — children depend on parent internals (the "fragile base class" problem). Prefer HAS-A (a field delegating to a helper) unless there's a true IS-A with substitutability (**Liskov Substitution Principle**: anywhere a `Shape` is expected, any subtype must behave acceptably — the classic violation is `Square extends Rectangle` with independent width/height setters).

## 2.4 — Abstract classes & interfaces

**Abstract class** = partial implementation + contract: state, constructors (run via `super()`), concrete methods, and `abstract` holes subclasses must fill. Can't instantiate. May have zero abstract methods (blocks instantiation only).

**Template method pattern:** parent's `final process()` fixes the algorithm skeleton (validate → execute → log); children fill the steps via abstract methods; dispatch inserts their code into the fixed flow. Framework backbone (Spring's `JdbcTemplate`, JUnit lifecycles).

**Interface** = capability contract (CAN-DO): no instance state; methods implicitly `public abstract`; fields implicitly `public static final`; implementations must declare methods `public` (can't narrow). A class implements many — this is Java's multiple inheritance *of type*.

**Interface evolution (Java 8+):** `default` methods carry bodies — how `List.sort` landed on a 20-year-old interface without breaking the ecosystem. Also `static` (factories/helpers) and `private` (Java 9 — share code among defaults). **Diamond rule:** two inherited defaults with the same signature → compile error until the class overrides; `InterfaceName.super.method()` reaches a chosen parent.

**Choosing:** capability → interface. Shared state/machinery among tight family → abstract class. Modern pattern: interface for the contract + abstract skeleton class for convenience (`List` + `AbstractList`). Marker interfaces (empty — `Serializable`, `Cloneable`) tag types for runtime checks; largely superseded by annotations but alive in the JDK.

## 2.5 — The Object contracts: equals, hashCode, toString

Defaults are **identity**: `equals` ≡ `==`, `hashCode` from identity, `toString` = `Class@hexHash`. Value classes must override the trio, or hash collections fail *silently* (lesson demo: HashSet holds two "equal" books; `contains` lies).

**Why the contract exists:** `HashMap`/`HashSet` are bucket arrays — `hashCode` picks the bucket (index ≈ `hash & (capacity-1)`), `equals` disambiguates within it. Equal objects with unequal hashes land in different buckets and never meet. Hence: **equal ⇒ same hashCode** (unequal may collide — collisions are normal, they just chain in the bucket). And: never mutate a field that feeds hashCode while the object sits in a hash collection — it becomes unfindable (lookup probes the *old* bucket).

**equals recipe:** `==` fast path → `instanceof` pattern (null-safe: `null instanceof X` is false) → `Objects.equals` on defining fields. **hashCode:** `Objects.hash(sameFields)` — same fields as equals, always. **equals laws:** reflexive, symmetric, transitive, consistent, `x.equals(null) == false`.

**`instanceof` vs `getClass()` in equals:** `instanceof` allows subclass equality but breaks *symmetry* once a subclass adds fields and its own equals (`base.equals(sub)` true, `sub.equals(base)` false). `getClass()` restores symmetry at the cost of any subclass ever comparing equal. Practical answer: make value classes `final` (or records) and the dilemma evaporates.

**toString:** for logs/debugging — make it readable, never parse it, never leak secrets into it.

## 2.6 — Enums, records, sealed classes

Each answers a design question: **enum** — fixed set of *instances*; **record** — the type *is* its data; **sealed** — fixed set of *subtypes*.

**Enums:** real classes extending `java.lang.Enum`; constants are `public static final` singletons created at class load → safe to compare with `==`, intrinsically thread-safe (the strongest singleton idiom in Java). Private constructors; fields + methods fine; even per-constant bodies (`PLUS { apply(){...} }`) for strategy-per-constant. API: `values()`, `valueOf`, `name()`, `ordinal()` — **never persist `ordinal()`** (insert/reorder corrupts stored data; persist `name()`). Switch over an enum covering all constants needs no `default`, so adding a constant breaks every switch at compile time — the compiler as a checklist. Related toolbox: `EnumSet`/`EnumMap` — bitset/array-backed, far faster than hash versions.

**Records (Java 16+):** header components generate `private final` fields, canonical constructor, accessors (`amount()`, no `get`), value `equals`/`hashCode`, `toString`. **Compact constructor** `Money { ... }` runs *before* field assignment — the validation/normalization hook (reassign parameters there). Constraints: no extra instance fields (static ok), implicitly `final`, extends `Record` so can't extend anything else — but can implement interfaces. Shallow immutability only: a `record Holder(List<String> xs)` still needs a defensive `List.copyOf` in the compact constructor. Use for DTOs, API payloads, map keys, multi-returns.

**Sealed (Java 17):** `sealed interface PaymentResult permits Success, Declined, NetworkError` closes the hierarchy; each permitted type must declare `final`, `sealed` (continue restricting), or `non-sealed` (reopen). Payoff: pattern-matching switch over a sealed type is **exhaustive with no default** — new subtype ⇒ compile error at every switch ⇒ impossible to forget a handler. Sealed interface + records = **algebraic data types**; models "exactly these outcomes" (results, states, events) with compiler-enforced completeness.

## 2.7 — Nested classes & static deep-dive

**Four kinds:**

| kind | outer instance? | typical use |
|---|---|---|
| static nested | no | namespacing/cohesion (`Map.Entry`, `Cart.Item`) — **default choice** |
| inner | yes (hidden ref) | helper intrinsically tied to one outer object |
| local | captures effectively-final locals | scoped one-off inside a method |
| anonymous | inherits enclosing context | inline one-off implementation — now mostly lambdas |

- Nested classes see the outer class's `private` members (and vice versa) — compiled as synthetic accessors; files come out as `Outer$Inner.class`.
- Inner classes: constructed via `outer.new Inner()`; explicit outer access via `Outer.this.field`. The hidden reference **pins the outer object** — the classic memory leak (Android's non-static Handler). If the nested class doesn't need outer state, make it static.
- Anonymous classes: `new Greeter() { ... }` — define + instantiate in one expression. For single-abstract-method interfaces, lambdas replace them (Phase 4); still needed to extend a class or override several methods inline.

**Initialization order (proven by lesson output):** static field initializers + static blocks — once, in textual order, at class load (first touch triggers it). Per instance: field initializers + instance blocks in textual order → constructor body. With inheritance: parent statics → child statics → parent (fields+blocks, constructor) → child (fields+blocks, constructor).

**static — complete rules:** class-level, one copy, no `this`; static methods can't touch instance state directly (instance methods can touch static freely). Call as `ClassName.member` — `instance.staticMethod()` compiles but misleads. Static nested classes = same concept applied to types. `static final` constants: `UPPER_SNAKE_CASE`.
