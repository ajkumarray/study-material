<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · basics](../phase-1-basics/NOTES.md) | [Phase 3 · core apis ➡](../phase-3-core-apis/NOTES.md)
<!-- /nav -->

# Phase 2 — OOP: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 2.1 — Classes, objects, constructors

**Q: Class vs object?**
A class is a compile-time blueprint — it declares field and method definitions but
holds no state of its own. An object is a runtime instance built from that blueprint,
living on the heap, with its own independent copy of every instance field. One class can
produce arbitrarily many objects, each independent of the others: `new BankAccount("Ajay",
1000)` and `new BankAccount("Ravi", 0)` are two separate objects sharing one class
definition, and mutating one never affects the other.

**Q: What exactly happens at `new BankAccount("Ajay")`?**
Three things happen, in order. First, the JVM allocates heap memory for the object's
fields and zero-defaults them (numeric fields to `0`, `boolean` to `false`, references
to `null`). Second, the matching constructor runs to actually initialize those fields —
if the class has a parent, the parent's constructor runs first via an implicit or
explicit `super(...)` call. Third, a reference to the now-fully-built object is returned
and stored into whatever variable receives it. The critical point: the object itself
never "lives in" the variable — only the reference to it does, which is why assigning
one variable to another copies the reference, not the object.

*Follow-up: "Where does the object live, and where does the reference live?"* — The
object lives on the shared heap, managed by the garbage collector. The reference lives
wherever it's declared: a local variable's reference lives in the current thread's stack
frame, while a reference stored in a field lives inside whatever object owns that field
(itself ultimately on the heap).

**Q: Constructor vs method?** ⭐
A constructor shares the class's exact name and has **no return type at all — not even
`void`**. Adding `void` doesn't make it "a constructor with no return value"; it silently
turns it into an ordinary method that happens to be named after the class, which is a
classic trick question because the code still compiles. A constructor is invoked only
via `new` (or implicitly, by a subclass's `super()`), runs exactly once per object at
creation time, is not inherited by subclasses, and cannot be `abstract`, `final`, or
`static`. Both constructors and methods can be overloaded — multiple constructors with
different parameter lists are completely normal.

```java
class Thing {
    Thing() { }        // constructor — no return type
    void Thing() { }   // a METHOD named "Thing" — legal, but almost certainly a bug
}
```

**Q: When do you NOT get a default constructor?** ⭐
If a class declares zero constructors, the compiler gifts it a public no-argument
constructor for free. The moment the class declares *any* constructor — even a
one-argument one — that free gift is withdrawn entirely. `new Thing()` then fails to
compile unless the class explicitly declares its own no-arg constructor. This is a real
source of "it compiled yesterday" errors when someone adds a parameterized constructor
to a class that other code was relying on the implicit default of.

**Q: Uses of `this`?**
Four distinct uses: disambiguating a field from a same-named parameter that shadows it
(`this.owner = owner`); chaining to a sibling constructor (`this(...)`, which must be
the first statement in the constructor body); passing the current object as an argument
to other code (`registry.register(this)`); and returning the current object to support a
fluent/builder-style API (`return this;` at the end of a setter-like method).

**Q: Why would a constructor be `private`?**
To control how — or whether — instances get created from outside the class: singletons
(exactly one instance, ever), static factory methods that give the construction step an
expressive name and the freedom to cache or return a subtype (`List.of(...)`,
`LocalDate.of(...)`), and non-instantiable utility classes (a class of only static
members, whose private no-arg constructor exists purely to prevent `new Utility()`).

*Follow-up: "Static factories vs. public constructors — why prefer one?"* — Static
factories can have descriptive names where constructors are all stuck with the class
name (`BigInteger.valueOf(long)` reads better than an overload set of constructors),
they aren't required to create a new object every call (enabling caching/pooling — this
is how `Integer.valueOf` reuses cached instances for small values), and they can return
any subtype of the declared return type, none of which an ordinary constructor can do.
This is Effective Java's Item 1, and it's worth internalizing.

**Q: Stack vs heap?** ⭐
The stack is per-thread: each method call pushes a new stack frame holding that
method's local variables, parameters, and its return address; the frame is
automatically popped when the method returns, and unbounded/too-deep recursion
overflows the stack, throwing `StackOverflowError`. The heap is shared across the
entire JVM process: every object and array is allocated there, and reclaimed only by
the garbage collector once nothing references it anymore; running out of heap space
throws `OutOfMemoryError`. In `BankAccount a = new BankAccount();`, the `BankAccount`
object itself is allocated on the heap, and the reference `a` pointing at it lives in
the current frame on the stack.

## 2.2 — Encapsulation & access control

**Q: What is encapsulation, really?** ⭐
Encapsulation means keeping an object's state private and exposing only the behaviors
(methods) that are allowed to touch it, with those methods enforcing whatever rules the
object needs to remain valid. Concretely, it wins you three things: invariants like
"salary can never be negative" live in one unbypassable place instead of being hoped-for
conventions scattered across every caller; internal representation can be swapped later
(a stored field becomes a computed value, say) without any caller noticing, because they
only ever saw a method, never a field; and every state change flows through code you can
log, validate, or set a breakpoint in.

**Q: Encapsulation vs abstraction?** ⭐
Abstraction hides *how something works* behind *what it does* — a `List` interface
doesn't tell callers whether it's array-backed or linked-list-backed, only that it
supports `add`/`get`/`remove`. Encapsulation hides *the data itself* behind
access-control boundaries — private fields that can't be touched except through methods.
Abstraction is a design-level concept about contracts; encapsulation is an
implementation-level concept about access control that *defends* those contracts from
being bypassed. In practice they work together: an interface provides the abstraction,
and the implementing class's private fields provide the encapsulation.

**Q: The four access modifiers?** ⭐
`public` is visible everywhere. `protected` is visible within the same package **and**
to subclasses anywhere, even in a different package. Default (no modifier written,
"package-private") is visible only within the same package. `private` is visible only
within the declaring class itself. The trap interviewers like to probe: `protected` is
*wider* than default, not narrower as its name might suggest — it's default access plus
an extra grant to subclasses. Top-level classes may only be declared `public` or
default — never `protected` or `private`.

**Q: Why getters/setters over public fields?**
A public field is an unconditional, permanent promise with zero room to add rules
later — anyone can set it to anything, and if you ever need to validate, log, or
recompute, you have to change every call site in the codebase. Accessor methods let you
validate on write, compute on read, log every change, change the internal
representation freely, and even create deliberate asymmetry — a getter with no setter
gives read-only access after construction. Frameworks (Spring, JPA, Jackson) also
specifically rely on the JavaBeans `getX`/`setX`/`isX` naming convention to discover
properties via reflection, so following it isn't just style — it's required for those
tools to work at all later in the repo.

**Q: Can `private` state leak?** ⭐ *junior/senior separator*
Yes — `private` only protects the *variable* (the reference), not the object that
reference points to. If a getter returns a mutable field directly — an array, a `List`,
a mutable `Date` — the caller receives a live reference into the object's actual
internal state and can mutate it freely, with no setter ever invoked and no validation
ever consulted:

```java
public int[] getRatingsLeaky() { return ratings; }   // hands out the real array

int[] stolen = employee.getRatingsLeaky();
stolen[0] = -100;   // employee's internal state is now corrupted, no setter called
```

The fix is defensive copying in both directions — copy mutable input on the way in
(`Arrays.copyOf(ratings, ratings.length)` inside the setter/constructor) and copy
mutable output on the way out (return `Arrays.copyOf(...)` from the getter instead of
the live array) — or, more robustly, prefer immutable types (`List.copyOf(...)`,
`LocalDate`) where there's simply nothing mutable to leak.

**Q: Design an immutable class.** ⭐
Declare the class `final` (so a subclass can't reopen mutability), give every field
`private final`, provide no setters, defensively copy any mutable constructor input, and
defensively copy any mutable value returned from a getter. "Mutating" operations return
a brand-new instance instead of changing the existing one — exactly how `String`,
`BigDecimal`, and a hand-rolled `Point.translate(dx, dy)` all behave. The payoff:
zero-effort thread safety (there's no mutable state to race on), safety to cache or
share the object freely, and stability as a hash-map key (its hash code can never
change out from under the map).

*Follow-up: "Is a record deeply immutable?"* — No, only shallowly. `record
Holder(List<String> xs)` still exposes a genuinely mutable `List` if the caller passed
one in and the compact constructor didn't defensively copy it (`xs = List.copyOf(xs);`).
Records automate the *shape* of immutability (final fields, no setters, generated
equals/hashCode) but not automatic deep copying of mutable components — that's still the
class author's job.

## 2.3 — Inheritance & polymorphism

**Q: The four pillars of OOP?** ⭐ *the opener*
Encapsulation — hide state behind methods that enforce rules. Inheritance — a subclass
acquires a parent's fields and methods, modeling an IS-A relationship and enabling
implementation reuse. Polymorphism — code written against a parent type transparently
works with any subtype, running that subtype's overridden behavior. Abstraction — expose
*what* something does while hiding *how* it does it. Have a one-liner and a tiny concrete
example ready for each — interviewers routinely follow up "give me an example" on
whichever pillar you name first.

**Q: Overriding vs overloading?** ⭐⭐
Overloading is multiple methods sharing a name but differing in parameter list, all
resolved at **compile time** based on the declared (static) types of the arguments —
it's a form of static/early-bound polymorphism, and it doesn't require inheritance at
all. Overriding is a subclass providing its own body for a method with the *identical*
signature inherited from a parent, resolved at **runtime** via dynamic dispatch based on
the object's actual class. The compact way to say it: the compiler picks which overload
runs; the JVM picks which override runs.

**Q: Explain dynamic dispatch.** ⭐
Given `Shape s = new Circle(5);`, the *compile-time type* of `s` is `Shape` — that's
what gates which methods you're even allowed to call through `s`. The *runtime type* of
the object `s` refers to is `Circle` — that's what decides which method body actually
executes when you call `s.area()`. Mechanically, the JVM's `invokevirtual` instruction
looks the method up in the actual object's class at the moment of the call —
conceptually, each class has a per-class method table (a "vtable"), and overriding a
method simply replaces the parent's slot with the child's implementation. In Java, every
instance method that isn't `private`, `static`, or `final` is virtual by default — this
is the opposite default from C++, where you must opt into virtual dispatch explicitly.

*Follow-up: "Do fields dispatch dynamically too?"* — No. Field access is resolved
entirely at compile time by the *variable's declared type*, even if the runtime object
shadows that field with a same-named one. `Shape f = new Circle(1);` reading `f.kind`
returns `Shape`'s `kind`, not `Circle`'s — only methods participate in dynamic dispatch,
which is one reason shadowing fields is considered bad practice: it silently
contradicts the polymorphism everyone expects.

**Q: Rules for a valid override?**
The method name and parameter types must match exactly. The return type may be the same
type or a *subtype* of the parent's declared return type (covariant returns). Access may
only widen, never narrow — a `public` parent method cannot become `protected` or
`private` in the override. The override may not declare new or broader checked
exceptions than the method it overrides (Phase 3 covers checked exceptions in depth).
Methods that are `private`, `static`, or `final` cannot be overridden at all. Always
annotate overrides with `@Override` — it makes the compiler verify the signature really
does match a parent method, turning a silent typo (which would otherwise just create an
unrelated new method) into a compile error.

*Follow-up: "Overriding a static method?"* — That's not overriding, it's **hiding**.
Redeclaring a `static` method with the same signature in a subclass creates an
independent method resolved by the *reference type* at compile time, not the object's
runtime type — `Parent.staticM()` and `Child.staticM()` are two entirely separate
methods, and calling the "hidden" one through a `Parent`-typed variable holding a
`Child` object always runs `Parent`'s version, unlike an actual override.

**Q: Why no multiple class inheritance in Java?**
Multiple *class* inheritance runs into the diamond problem: if two parent classes both
define state and a method with the same signature, there's no principled way to decide
which parent's version wins without introducing complex, error-prone resolution rules
(C++ has these rules, and they're widely considered a wart). Java sidesteps the problem
by allowing only single inheritance of class (and therefore of state), while permitting
unlimited inheritance of *interface type* — and since Java 8, partial inheritance of
*behavior* too via `default` methods, but with mandatory, explicit conflict resolution
when two interfaces disagree (2.4's diamond example).

**Q: Composition vs inheritance — when each?** ⭐ *design maturity probe*
Inheritance is the tightest possible coupling: a subclass depends not just on its
parent's public contract but on its internal implementation details, so a change to the
parent's internals can silently break every subclass (the "fragile base class"
problem). It's only well-justified when there's a genuine IS-A relationship that
satisfies the Liskov Substitution Principle — any subtype must behave acceptably
anywhere the parent type is expected. The textbook violation is `Square extends
Rectangle`: if `Rectangle` exposes independent `setWidth`/`setHeight`, a `Square` cannot
honor both without breaking the invariant callers assume ("changing width doesn't
change height"). Default instead to composition — hold a collaborator object as a field
and delegate calls to it (HAS-A, not IS-A). The one-line summary interviewers want to
hear: *inherit to be reused (as a base class other code builds on), compose to reuse (a
helper you call)*.

**Q: Why not call overridable methods from a constructor?** *senior probe*
Because construction always runs parent-first: the parent's constructor body finishes
executing before the child's own field initializers or constructor body run. If the
parent constructor calls an overridable method, dynamic dispatch means the *child's*
override runs — but it runs before any of the child's own fields have been initialized,
so that override observes fields still at their default zero/null values, not whatever
the child constructor was about to set them to. The safe rule: constructors should only
call methods that are `private`, `static`, or `final` — anything that's guaranteed not
to be replaced by a not-yet-initialized subclass.

**Q: Upcasting vs downcasting?**
Upcasting — storing a child object in a parent-typed variable — is implicit and always
safe; the compiler allows it freely because a `Circle` genuinely can do everything a
`Shape` can. Downcasting — recovering a more specific type from a parent-typed
variable — requires an explicit cast and is checked at runtime; if the object's actual
class doesn't match, it throws `ClassCastException`. Guard downcasts with `instanceof`
pattern matching, which tests, casts, and binds a new variable in a single expression:

```java
if (shape instanceof Circle c) {
    System.out.println(c.getRadius());
}
```

Needing to downcast routinely throughout a codebase is usually a design smell —
polymorphism (pushing the behavior into an overridden method) or, for closed
hierarchies, sealed types with an exhaustive `switch` (2.6), typically eliminate the
need entirely.

## 2.4 — Abstract classes & interfaces

**Q: Abstract class vs interface?** ⭐⭐ *guaranteed*
An abstract class can hold instance state, constructors (run via a subclass's
`super(...)`), methods with any access modifier, and fully implemented (non-abstract)
methods alongside `abstract` ones — but a class can extend only one, because it's still
subject to single inheritance. An interface is a stateless contract whose members are
implicitly `public`; a class can implement any number of interfaces, which is Java's
mechanism for multiple inheritance of type (and, via `default` methods, partial
inheritance of behavior). The decision rule: reach for an interface when you're
describing a capability unrelated classes might share; reach for an abstract class only
when implementations genuinely need to share state or non-trivial machinery among a
tightly related family. When genuinely unsure, default to an interface — it's the looser
form of coupling. The modern JDK style frequently uses both together: `List` is the
interface (the contract) and `AbstractList` is an abstract skeleton class offering
shared machinery to implementors who want it.

**Q: Can an abstract class have constructors? Zero abstract methods? Be `final`?**
Yes to constructors — they can't be invoked via `new` directly on the abstract class,
but they run implicitly whenever a subclass constructor calls (or the compiler inserts)
`super(...)`. Yes to zero abstract methods — the `abstract` keyword on the class alone
is sufficient to block direct instantiation, even if every method already has a body;
this is useful when you want to force subclassing without mandating any specific
override. No to `final` — `abstract` says "you must be subclassed to be useful" while
`final` says "you cannot be subclassed" — the two are a direct contradiction and
`abstract final class` fails to compile.

**Q: Why were default methods added?** ⭐
Interface evolution. Java 8 needed to add methods like `forEach`, `sort`(-adjacent
default helpers), and stream-producing methods to interfaces such as `List` and
`Collection` that had existed, unchanged, for well over a decade, without breaking every
class in the world that already implemented them. A `default` method carries an actual
method body inside the interface itself; every implementing class inherits that body for
free and may override it if it needs different behavior, but is not *required* to
implement it — which is precisely what let those decades-old interfaces gain new
methods without a compile-breaking flag day for the entire ecosystem.

*Follow-up: "What happens if a class implements two interfaces that provide the same
default method?"* — It's a compile error until the class overrides that method itself.
Inside the override, `InterfaceName.super.method()` lets the class reach a *specific*
parent interface's default implementation explicitly, so it can choose one, the other,
or combine both:

```java
interface English { default String greet() { return "Hello"; } }
interface Hindi   { default String greet() { return "Namaste"; } }

class SmartNotifier implements English, Hindi {
    @Override
    public String greet() {
        return English.super.greet() + " / " + Hindi.super.greet();  // "Hello / Namaste"
    }
}
```

**Q: What can an interface contain in modern Java?**
Abstract methods (implicitly `public abstract`); `default` methods with a body,
inherited free and overridable; `static` methods, which belong to the interface itself
rather than any instance (`Notifier.none()`); `private` methods (Java 9+), used purely
to share code between multiple `default` methods on the same interface without exposing
that shared code as part of the public contract; and constants, which are implicitly
`public static final`. An interface can never hold per-instance state — there's no place
for a plain instance field to live.

**Q: Template method pattern?**
The abstract parent fixes the algorithm's overall skeleton in a single, usually `final`,
method — `final` so subclasses cannot rearrange the steps — and expresses each variable
step as an `abstract` method that subclasses are forced to supply. Calling the fixed
method runs the parent's flow, but dynamic dispatch fills each blank with the specific
subclass's implementation:

```java
abstract class Payment {
    final void process() {                       // fixed skeleton, can't be reordered
        if (!validate()) { return; }
        execute();                                 // dynamic dispatch fills this in
        System.out.println("processed via " + methodName());
    }
    abstract boolean validate();
    abstract void execute();
    abstract String methodName();
}
```

This pattern underlies a huge amount of real framework code — JUnit's fixed
setUp/test/tearDown lifecycle and Spring's `JdbcTemplate` (which fixes "open connection,
run statement, handle exceptions, close connection" while letting you supply just the
SQL and row-mapping logic) are both template methods.

**Q: What is a marker interface?**
An interface with no members at all — `Serializable` and `Cloneable` are the JDK's
canonical examples — used purely to tag a type so runtime code can `instanceof`-check
for it. The modern alternative for new code is annotations, but marker interfaces
remain common throughout the JDK for historical reasons, and they have one genuine
advantage annotations lack: they participate in the type system, so you can write
`<T extends Serializable>` as a generic bound, which an annotation-based marker simply
cannot express.

## 2.5 — equals, hashCode, toString

**Q: The equals/hashCode contract?** ⭐⭐ *guaranteed*
If `a.equals(b)` is `true`, then `a.hashCode()` must equal `b.hashCode()` — that
direction is mandatory. The converse is not required: two unequal objects are allowed to
share a hash code (that's just a normal, harmless collision, resolved by chaining within
the bucket). Separately, `equals()` itself must satisfy reflexivity (`x.equals(x)` is
always `true`), symmetry (`x.equals(y)` iff `y.equals(x)`), transitivity (`x.equals(y)`
and `y.equals(z)` implies `x.equals(z)`), consistency (unchanged objects keep returning
the same answer on repeated calls), and `x.equals(null)` must always return `false`.

*Follow-up: "Break it — what happens in a HashMap?"* — `HashMap`/`HashSet` are backed by
an array of buckets; `hashCode()` picks which bucket an object belongs to, and `equals()`
disambiguates between objects sharing a bucket. If two `equals()`-equal objects report
*different* hash codes, they land in different buckets and the collection can never
recognize them as duplicates: you get two "equal" entries coexisting in a `HashSet`, or a
`get()` that can't find a key that's logically present but hashed into the wrong bucket.
Crucially, none of this throws an exception — it's silently, quietly wrong data, which is
exactly what makes this contract violation a genuine, hard-to-diagnose production bug
category rather than a compile-time or even test-time failure most of the time.

**Q: Walk me through a correct equals.**
Start with an identity fast path (`if (this == o) return true;`), then a type check that
doubles as a null check via `instanceof` pattern matching (`if (!(o instanceof Book
other)) return false;` — `null instanceof AnyType` is always `false`, so this line
handles nulls automatically), then compare each field that defines the object's value,
using `Objects.equals(a, b)` for any field that might itself be `null`. `hashCode()`
should run `Objects.hash(...)` over *exactly the same set of fields* used in `equals`,
never more and never fewer — a mismatch between the two field sets is precisely what
breaks the contract. In practice: use a record where the shape fits, or let the IDE
generate both together, because hand-rolled `equals`/`hashCode` that drift apart over
time (someone adds a field to one but forgets the other) is a real, recurring genre of
production bug.

**Q: What if you mutate a field used in hashCode while the object is in a HashSet?**
*senior probe*
The object was originally filed under the bucket computed from its *original* hash
code. After the mutation, any subsequent lookup (`contains`, `get`, `remove`) computes
the object's *current* hash code and probes a different bucket — so the object is now
effectively lost: `contains` returns `false`, `remove` fails silently, and the object
sits unreachable in the set until (if ever) a full rehash happens to relocate it. The
takeaway: objects used as hash-collection keys should be immutable, or at minimum must
never be mutated while they're stored inside such a collection.

**Q: `instanceof` vs `getClass()` in equals?** *depth probe*
`instanceof` permits a subclass instance to compare equal to a parent instance, which is
what most code intuitively wants — but it can silently break *symmetry* the moment a
subclass adds its own fields and its own `equals` override: `parent.equals(child)` might
evaluate `true` (comparing only the parent's fields) while `child.equals(parent)`
evaluates `false` (the child's equals also checks its extra fields, which the parent
doesn't have). `getClass()` comparison is strictly symmetric — two objects are equal
only if they share the *exact* runtime class — but it forbids any cross-class equality
at all, even when a subclass adds nothing. The cleanest way to escape the dilemma
entirely: make value classes `final` (or use records, which are implicitly final), so
there's no subclass to create the ambiguity in the first place.

**Q: Integer `==` trap?** ⭐
```java
Integer a = 127, b = 127;
System.out.println(a == b);   // true  — JVM caches boxed Integers in [-128, 127]
Integer a2 = 128, b2 = 128;
System.out.println(a2 == b2); // false — outside the cache range, two distinct boxes
```
Autoboxing hides the fact that an object is being created, so `==` on boxed types looks
like it should compare values the way primitive `==` does — but it's still reference
comparison, and it happens to return `true` in the cached range purely as an
implementation detail (`Integer.valueOf` reuses cached instances for `-128..127`), never
because of value equality. The rule is unconditional regardless of the range: always
compare boxed wrapper types with `.equals()` (or unbox to the primitive first). Phase 3
revisits this trap in the context of collections, where autoboxing happens constantly
and silently.

## 2.6 — Enums, records, sealed

**Q: What is an enum under the hood?** ⭐
An enum is a real, final class that implicitly extends `java.lang.Enum`. Each declared
constant is a `public static final` instance of that class, all created once during
class initialization — and the JLS guarantees class initialization is thread-safe, so
every enum constant is effectively a safe singleton with zero extra effort, which is why
Effective Java calls the enum the best way to implement a singleton in Java. Because
there's exactly one object per constant for the life of the program, comparing enum
values with `==` is always both safe and idiomatic.

*Follow-up: "Can enums have fields/constructors/methods? Per-constant bodies? Extend a
class? Implement interfaces?"* — Fields, constructors (implicitly `private` — the fixed
constant list is the only place instances are ever created), and methods: yes, enums are
full classes. Per-constant class bodies (each constant supplying its own override of a
method, effectively Strategy-per-constant): yes, though not shown in this repo's lesson
code. Extending another class: no — an enum already implicitly extends `Enum`, and Java
allows only single class inheritance. Implementing interfaces: yes, freely.

**Q: Why never persist `ordinal()`?**
`ordinal()` is nothing more than the constant's position in the declaration list —
purely incidental, not semantic. Inserting a new constant in the middle of the list, or
reordering existing constants, silently shifts every ordinal after that point, so any
previously stored ordinal value now points at a different, wrong constant. Persist
`.name()` instead (or an explicit, deliberately chosen code field) — it's stable across
any amount of reordering. This is exactly why JPA's `@Enumerated` annotation should
always specify `EnumType.STRING`, never rely on the default `EnumType.ORDINAL`.

**Q: What do records generate — and their restrictions?** ⭐
From `record Money(long amount, String currency)`, the compiler generates: `private
final` fields for `amount` and `currency`, a canonical constructor, accessor methods
named exactly after the components (`amount()`, `currency()` — no `get` prefix), a
value-based `equals`/`hashCode` comparing every component, and a readable `toString`.
Restrictions: no additional *instance* fields beyond the declared components (static
fields are allowed); the record is implicitly `final`, so it can't be subclassed; it
implicitly extends `java.lang.Record`, so it can't extend anything else — but it can
freely implement interfaces. Validation and normalization belong in the **compact
constructor** — `Money { if (amount < 0) throw new IllegalArgumentException(...); }` —
which runs *before* the fields are assigned, so reassigning a parameter inside it
changes what actually gets stored.

*Follow-up: "Record vs Lombok?"* — Records are a language-level feature carrying real
semantic meaning (the compiler and pattern matching both understand "this is a value
type made of these components"), while Lombok is compile-time code generation layered on
top of an otherwise ordinary, still-mutable class — it saves typing but doesn't change
what the language itself understands about the type. Records also integrate directly
with pattern matching (`case Success s -> ...`), which generated Lombok classes do not.

**Q: What problem do sealed types solve?** ⭐ *modern-Java signal*
They close a hierarchy: `sealed interface PaymentResult permits Success, Declined,
NetworkError` tells the compiler the *complete* set of types that may ever implement
`PaymentResult` — no other class, anywhere, in any file, can add a fourth
implementation. That closure is what makes pattern-matching `switch` over the type
*exhaustive without a `default` clause*: the compiler can prove every case is handled
because it knows there are no other cases. Add a fourth `permits` type later and every
existing `switch` over `PaymentResult` that doesn't yet handle it fails to compile —
turning "a new case slipped through unhandled at runtime" into "the build won't pass
until every switch is updated." Each permitted subtype must declare `final` (closes that
branch for good), `sealed` (keeps restricting who can extend it further), or
`non-sealed` (reopens just that one branch). Sealed interface plus record
implementations together give Java something close to algebraic data types from
functional languages — ideal for modeling a fixed, closed set of results, states, or
events.

## 2.7 — Nested classes & static

**Q: Static nested vs inner class?** ⭐
A static nested class carries no reference to any outer instance at all — it's
constructed with ordinary syntax (`new Outer.Nested(...)`) and exists purely as a
namespaced, cohesion-grouped type inside another class's scope. This is the default
choice. An inner (non-static) class is bound to one specific outer instance for its
entire lifetime, constructed with the unusual `outerInstance.new Inner()` syntax, and
can read the outer object's `private` members directly. That binding is implemented as a
hidden reference from every inner instance back to its outer instance — which means the
inner instance *pins the outer object in memory* for as long as the inner instance
itself is reachable, a classic memory-leak shape (the textbook example is a non-static
`Handler` inner class on Android keeping an `Activity` alive well past when it should
have been garbage collected). The rule of thumb: default to `static` unless the nested
class genuinely needs that live link back to a specific outer instance.

**Q: What is an anonymous class, and what replaced it?**
An anonymous class declares and instantiates a class in a single expression with no name
of its own — `new Greeter() { @Override public String greet(String name) { ... } }`.
For interfaces with exactly one abstract method (functional interfaces), lambdas (Phase
4) now express the same thing far more concisely and have largely replaced this
pattern. Anonymous classes remain necessary when you need to extend a concrete class
(lambdas can only implement functional *interfaces*) or override more than one method in
a single inline definition.

*Follow-up: "Why must locals captured by an anonymous/local class (or a lambda) be
effectively final?"* — The capture is a *copy* of the local's value, taken at the moment
the anonymous/local class instance is created. If the original local variable could be
reassigned afterward, the captured copy and the "live" original would drift apart with
no way to keep them synchronized — the compiler forbids that ambiguity by requiring the
captured local never be reassigned after its initial value (effectively final), even if
it isn't explicitly declared `final`.

**Q: Initialization order — static blocks, instance blocks, constructors?**
At class load — which happens once, lazily, on first real use — every static field
initializer and every `static { ... }` block runs, in the textual order they appear in
the source. Then, for every single `new` afterward, every instance field initializer and
every instance `{ ... }` block runs, again in textual order, immediately followed by the
constructor's own body. Under inheritance the order interleaves by generation: parent
statics run first (once), then child statics (once), then for each `new`: the parent's
instance initializers and constructor, then the child's instance initializers and
constructor.

*Follow-up: "When does a class actually load?"* — Lazily: on first instantiation, first
access to a static member, or an explicit `Class.forName(...)` call — never automatically
at JVM startup just because the class exists on the classpath. This is why a `static`
block's side effects (like the `[static block] runs ONCE` print in this phase's lesson
code) only appear the first time something actually touches that class.

**Q: Why can't static methods use `this`?**
A static method belongs to the class, not to any particular object, and can be called
without any instance ever having been created — so at the point it runs, there may be no
object for `this` to refer to at all. The relationship is asymmetric: going from static
context to instance context requires an actual object to be handed in or created first,
but going from instance context to static context is always fine, because exactly one
shared copy of any static member exists regardless of how many instances there are.

**Q: Is Java's `main` in a class — so how does OOP square with a static entry point?**
*conceptual*
`public static void main` has to be `static` because the JVM calls it before any object
in the program exists — there's nothing to construct it as an instance method of yet.
From that static bootstrap point, ordinary code takes over: you construct your first
real objects and hand control off into normal object-oriented territory (`new
App().run();` is the idiomatic shape). This is exactly what Spring Boot does behind the
scenes: `SpringApplication.run(App.class, args)` is itself called from a static `main`,
and everything after that point — the entire dependency-injected object graph — is
ordinary OOP.
