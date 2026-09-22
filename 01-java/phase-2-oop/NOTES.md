<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · basics](../phase-1-basics/NOTES.md) | [Phase 3 · core apis ➡](../phase-3-core-apis/NOTES.md)
<!-- /nav -->

# Phase 2 — Object-Oriented Programming: Notes

This phase is where Java stops being "a scripting language with types" and becomes
Java. Everything from here on — Spring, JPA, the collections framework, the JDK
itself — is built on the ideas below: classes and objects, encapsulation, inheritance,
polymorphism, the `Object` contracts, and the modern type kinds (records, sealed
types) that make illegal states unrepresentable. Every example below is runnable Java,
taken from (or extending) the lesson source files in `phase-2-oop/lesson-2-*/`.

## 2.1 — Classes, objects, constructors, `this`

A **class** is a compile-time blueprint: it declares what state (fields) and behavior
(methods) something has, but it does not itself hold any values. An **object** is a
runtime instance built from that blueprint, living on the heap, with its own
independent copy of every instance field. One class, many objects — exactly the way one
architectural blueprint produces many houses, each with its own furniture.

### Key Concepts

- **`new` does three things, in order**: (1) allocates memory on the heap for the
  object's fields, zero-defaulting them first; (2) runs the matching constructor to
  initialize those fields; (3) returns a *reference* to the finished object, which is
  what actually gets stored in your variable.
- **Objects live on the heap; references live on the stack.** A variable never holds an
  object — it holds a reference (effectively a pointer) to one. This is the direct
  continuation of Phase 1's "pass-by-value" lesson: copying a reference copies the
  *pointer*, not the object it points at.
- **Fields get default values; locals do not.** An uninitialized `int` field is `0`, a
  `boolean` field is `false`, an object-reference field is `null` — but an uninitialized
  local variable is a compile error the moment you try to read it (definite assignment).
- **Constructors initialize new objects.** Same name as the class, **no return type at
  all** — not even `void`. Adding `void` silently turns it into an ordinary method that
  happens to share the class's name; this is a classic trick question.
- **Constructor chaining with `this(...)`.** One constructor can delegate to another via
  `this(args)`, which must be the *first statement* in the constructor body. This lets
  you funnel every initialization path through one canonical constructor instead of
  duplicating logic.
- **The default constructor rule.** If a class declares *no* constructors, the compiler
  supplies a public no-argument one for free. The moment you declare *any* constructor —
  even a one-argument one — that free gift is withdrawn, and `new Thing()` stops
  compiling unless you add a no-arg constructor back yourself.
- **`this` has four uses**: disambiguating a field from a same-named parameter
  (`this.owner = owner`), chaining constructors (`this(...)`), passing the current
  object to other code (`registry.add(this)`), and returning the current object for a
  fluent/builder API (`return this;`).

### Worked example

```java
class BankAccount {
    private final String owner;      // final: assigned once, at construction
    private double balance;
    private static int accountCount = 0;   // one copy, shared by the whole class

    BankAccount(String owner, double openingBalance) {
        this.owner = owner;          // `this.owner` (the field) vs `owner` (the parameter)
        this.balance = openingBalance;
        accountCount++;
    }

    // Constructor chaining: delegates to the constructor above.
    BankAccount(String owner) {
        this(owner, 0.0);            // must be the first statement
    }

    void deposit(double amount) {
        if (amount <= 0) return;     // guard clause
        balance += amount;
    }

    boolean withdraw(double amount) {
        if (amount <= 0 || amount > balance) return false;  // refuse to go negative
        balance -= amount;
        return true;
    }

    String describe() { return owner + "'s balance: " + balance; }

    static int getAccountCount() { return accountCount; }
}

public class Demo {
    public static void main(String[] args) {
        BankAccount ajay = new BankAccount("Ajay", 1000.0);
        BankAccount ravi = new BankAccount("Ravi");           // uses the chained constructor

        ajay.deposit(500);
        ravi.deposit(50);
        System.out.println(ajay.describe());   // Ajay's balance: 1500.0
        System.out.println(ravi.describe());   // Ravi's balance: 50.0   <- untouched by Ajay's deposit

        BankAccount alias = ajay;               // copies the REFERENCE, not the object
        alias.deposit(1);
        System.out.println(ajay.describe());   // Ajay's balance: 1501.0  <- same object!

        System.out.println(BankAccount.getAccountCount());  // 2
    }
}
```

In this example: `ajay` and `ravi` are two independent objects with independent
`balance` fields — depositing into one never touches the other. `alias` is a *second
reference to the same object* as `ajay`, so mutating through `alias` is visible through
`ajay` too — this is the "photocopied house key" model from Phase 1's pass-by-value
lesson, now applied to a variable-to-variable assignment instead of a method call.
`accountCount` is `static`, so both constructors increment the *same* shared counter —
the final count (2) belongs to the class, not to either object.

### Why it's useful

Constructors are where you guarantee an object is *born valid* — `BankAccount` can
never exist with a `null` owner or nonsensical state, because every path to creating one
runs through a constructor. Constructor chaining keeps that guarantee in exactly one
place instead of duplicating validation across overloads. Understanding reference
semantics (as opposed to value semantics) is the single most important mental model for
debugging "I changed X and Y changed too" bugs, and for reasoning about aliasing before
concurrency (Phase 5) makes it dangerous.

### Summary / Key Takeaways

- `new` = allocate + construct + return a reference; the object lives on the heap, the
  reference lives wherever you store it.
- A constructor has no return type, is not inherited, and can be overloaded and chained
  with `this(...)` (first statement only).
- Declaring any constructor removes the free no-arg default constructor.
- Copying a reference (`BankAccount alias = ajay;`) does **not** copy the object —
  mutations through either variable are visible through both.
- `static` members belong to the class (one shared copy); instance members belong to
  each object (one copy per object).

## 2.2 — Encapsulation

**Encapsulation** means hiding an object's internal state and forcing all access to go
through methods that can enforce rules. It exists so that invariants ("salary is never
negative") live in exactly one unbypassable place, so internal representations can
change without breaking callers, and so every state change can be observed, validated,
or logged in one spot.

### Key Concepts

- **Access modifiers, widest to narrowest:**

  | modifier | same class | same package | subclass (other package) | everywhere |
  |---|---|---|---|---|
  | `public` | ✓ | ✓ | ✓ | ✓ |
  | `protected` | ✓ | ✓ | ✓ | ✗ |
  | *(default / package-private)* | ✓ | ✓ | ✗ | ✗ |
  | `private` | ✓ | ✗ | ✗ | ✗ |

  `protected` is easy to misjudge: it's package access **plus** any subclass, anywhere —
  *wider* than the no-modifier default, which is a common interview trap. Top-level
  classes may only be `public` or default (package-private) — never `protected` or
  `private`.
- **Rule of thumb: start `private`, widen only when a real caller needs it.** Fields are
  private by default in idiomatic Java; methods start private unless they're part of the
  intended public API.
- **Getters/setters are not a reflexive pair.** Each accessor is its own API decision. A
  `final` field with a getter and no setter is read-only from outside the class. A
  "getter" with no backing field at all — a *computed property* — is indistinguishable
  from a stored one to callers, which is exactly the point: you can switch the
  implementation later without breaking anyone.
- **Setters validate; constructors should reuse them.** Throwing `IllegalArgumentException`
  from a setter, then calling that same setter from the constructor, means validation
  logic exists exactly once and can never be bypassed by either path.
- **JavaBeans naming (`getX`/`setX`/`isX` for booleans) matters beyond style** — Spring,
  JPA, and Jackson discover properties reflectively by this convention, so deviating from
  it silently breaks framework integration later in the repo.
- **The leak: `private` protects the *variable*, not the object it references.** If a
  getter returns a mutable field directly (an array, a `List`, a mutable `Date`), the
  caller now holds a live reference into your object's internals and can mutate your
  state without ever calling a setter.
- **Fix: defensive copies, both directions.** Copy mutable input in the constructor/setter
  (`Arrays.copyOf(ratings, ratings.length)`) and copy mutable output in the getter — or,
  better, use immutable types (`List.copyOf(...)`, `LocalDate`) so there's nothing to
  defend in the first place.
- **The immutable-class recipe:** `private final` fields, no setters, defensive copies
  of any mutable inputs/outputs, and mark the class `final` so a subclass can't reopen
  mutability. "Mutating" methods return a *new* instance instead (`String`, `BigDecimal`,
  and the `Point.translate` example below all follow this pattern).

### Worked example — the leak and the fix

```java
class Employee {
    private final String name;      // final + no setter = read-only after construction
    private double salary;
    private int[] ratings = {};

    Employee(String name, double salary) {
        this.name = name;
        setSalary(salary);          // reuse the setter -> constructor gets validation free
    }

    public String getName() { return name; }
    public double getSalary() { return salary; }

    public void setSalary(double salary) {
        if (salary < 0) throw new IllegalArgumentException("salary cannot be negative: " + salary);
        this.salary = salary;
    }

    public double yearlyBonus() { return salary * 0.10; }   // computed — no field behind it

    public void setRatings(int[] ratings) {
        this.ratings = Arrays.copyOf(ratings, ratings.length);   // defensive copy IN
    }

    public int[] getRatingsLeaky() { return ratings; }                          // BAD
    public int[] getRatings()      { return Arrays.copyOf(ratings, ratings.length); }  // GOOD
}

Employee dev = new Employee("Ravi", 40_000);
dev.setRatings(new int[]{4, 5, 3});

int[] stolen = dev.getRatingsLeaky();
stolen[0] = -100;
System.out.println(Arrays.toString(dev.getRatingsLeaky()));  // [-100, 5, 3] <- internal state corrupted!

dev.setRatings(new int[]{4, 5, 3});         // reset
int[] safe = dev.getRatings();
safe[0] = -100;
System.out.println(Arrays.toString(dev.getRatings()));       // [4, 5, 3]    <- untouched
```

In this example: `getRatingsLeaky()` hands out the actual internal array reference, so
mutating the returned array *is* mutating the object's private state — no setter was
ever called, and no `IllegalArgumentException` guard was ever consulted, because the
caller bypassed the front door entirely. `getRatings()` returns a *copy*, so external
mutation only affects the copy — the object's real state is untouched. `salary` never
leaks this way because `double` is a primitive: `getSalary()` returns a copy of the
*value* automatically, which is why the leak only exists for object/array-typed fields.

### Worked example — full immutability

```java
final class Point {                 // final: can't be subclassed to add mutable state
    private final int x;
    private final int y;

    Point(int x, int y) { this.x = x; this.y = y; }

    public int getX() { return x; }
    public int getY() { return y; }

    public Point translate(int dx, int dy) {   // "modification" returns a NEW object
        return new Point(x + dx, y + dy);
    }

    public String describe() { return "(" + x + ", " + y + ")"; }
}

Point p1 = new Point(3, 4);
Point moved = p1.translate(2, 0);
System.out.println(p1.describe());     // (3, 4)   <- unchanged, exactly like String
System.out.println(moved.describe());  // (5, 4)
```

`p1` is never modified — `translate` builds and returns a brand-new `Point`. This is the
same pattern `String` uses (`s.toUpperCase()` returns a new `String`; it never rewrites
`s`), and it's exactly what `record` (2.6) generates automatically.

### Why it's useful

Encapsulation is what makes a codebase safe to change. If every field were public, any
caller anywhere could set a bank balance negative or corrupt a shared array, and finding
where that happened would mean searching the entire codebase. With encapsulation, a
single breakpoint in one setter catches every write. Immutable objects — the end state
of encapsulation — are free to share across threads with zero synchronization (Phase 5),
make reliable hash-map keys (2.5), and eliminate an entire category of "who changed my
object" bugs.

### Summary / Key Takeaways

- Access modifiers widen in this order: `private` < default < `protected` < `public`;
  `protected` includes subclasses in other packages, which default does not.
- Getters/setters are individual API decisions, not a mechanical pair — read-only fields
  and computed properties are both normal and desirable.
- `private` protects the field/variable, not a mutable object it points to — always
  defensive-copy mutable state in and out, or use immutable types.
- The immutable-class recipe (`private final` fields, no setters, defensive copies,
  `final` class) gives you thread safety, safe sharing, and stable hash keys for free.
- Constructors should call setters (or equivalent validation) rather than duplicate
  their rules.

## 2.3 — Inheritance & polymorphism

**Inheritance** (`extends`) lets one class acquire the fields and methods of another,
modeling an IS-A relationship — a `Circle` IS-A `Shape`. **Polymorphism** means code
written against the parent type automatically works with any subtype, and calling an
overridden method runs the *subtype's* version. The mechanism behind that is **dynamic
dispatch**: the JVM chooses which method body runs at runtime, based on the object's
actual class, not the compile-time type of the variable holding it.

### Key Concepts

- **Single inheritance of class, unlimited inheritance of interface type.** Every class
  has exactly one direct superclass (`Object` if none is named); a class may implement
  any number of interfaces.
- **Construction order runs parent-first.** A subclass constructor's first statement is
  always (explicitly or implicitly) a call to `super(...)`. If you don't write one, the
  compiler inserts a no-arg `super()` — which is a compile error if the parent has no
  no-arg constructor. Parents are always fully constructed before the child's own field
  initializers and constructor body run.
- **Never call an overridable method from a constructor.** Because construction runs
  parent-first, calling an overridable method from the *parent's* constructor invokes
  the *child's* override before the child's own fields have been initialized — the
  override observes half-built state. This is a real, subtle bug source; call only
  `private`, `static`, or `final` methods from constructors.
- **Compile-time type vs. runtime type.** The variable's declared type governs what you
  are *allowed to call* (checked by the compiler); the object's actual type governs
  *what code runs* (resolved by the JVM). `Shape s = new Circle(5);` — you can only call
  methods declared on `Shape` through `s`, but any overridden method runs `Circle`'s
  version.
- **Dynamic dispatch is `invokevirtual`.** Conceptually, every class has a per-class
  method table (a "vtable"); overriding a method replaces the parent's slot in the
  child's table. Every non-`private`, non-`static`, non-`final` instance method in Java
  is virtual by default — the opposite default from C++, where you must opt in with
  `virtual`.
- **Fields never dispatch dynamically — only methods do.** A field access is resolved at
  compile time using the *variable's* declared type, even if the object shadows that
  field with a same-named one in a subclass. This is a genuinely surprising trap and a
  reason to never shadow fields in real code.
- **`@Override` is not optional in practice.** It asks the compiler to verify the method
  really does override a parent method. Without it, a typo'd signature (wrong parameter
  type, wrong name) silently compiles as a brand-new, unrelated method — a bug that is
  very hard to spot by eye. With it, that same typo becomes a compile error.
- **Rules for a valid override:** identical method name and parameter types; the return
  type may be a *subtype* of the parent's return type (**covariant return**); access may
  only *widen*, never narrow (a `public` parent method can't become `private` in the
  child); no new or broader checked exceptions may be declared (Phase 3). `private`,
  `static`, and `final` methods cannot be overridden — attempting to redeclare a
  `static` method with the same signature in a subclass **hides** it instead (resolved
  by the variable's compile-time type, not dispatched).
- **Upcasting vs. downcasting.** Upcasting (child reference stored in a parent-typed
  variable) is implicit and always safe. Downcasting (parent-typed variable cast back to
  a specific subtype) requires an explicit cast and is checked at runtime — a wrong guess
  throws `ClassCastException`. Prefer the `instanceof` pattern-matching form
  (`if (shape instanceof Circle c) { ... }`, Java 16+), which tests, casts, and binds a
  variable in one expression. Needing to downcast routinely is usually a design smell
  that polymorphism (or sealed types + exhaustive `switch`, 2.6) removes.
- **`final` on a class or method.** `final` on a class forbids subclassing entirely
  (`String` is `final` specifically to protect its immutability guarantees). `final` on
  a method forbids overriding it — used to lock an invariant or to make a step of a
  template method (2.4) un-skippable.
- **Composition over inheritance.** Inheritance is the *strongest* form of coupling: a
  subclass depends on its parent's internal implementation details, not just its public
  contract (the "fragile base class" problem — a parent's internal change can silently
  break every subclass). Prefer composition (a field holding a collaborator object, with
  calls delegated to it) unless there's a genuine IS-A relationship with full
  substitutability — the **Liskov Substitution Principle**: anywhere a `Shape` is
  expected, every subtype must behave acceptably. The textbook LSP violation is
  `Square extends Rectangle` with independent `setWidth`/`setHeight` — a `Square` can't
  honor both setters independently without breaking the "it's still a rectangle"
  contract callers rely on.

### Worked example

```java
class Shape {
    protected String name;
    String kind = "generic shape";     // demonstrates fields don't dispatch

    Shape(String name) { this.name = name; }

    double area() { return 0; }        // parent default; children override

    String describe() {
        return "I am a " + name + " with area " + area();
        //  even here, area() dispatches dynamically to the ACTUAL object's version
    }
}

class Circle extends Shape {
    private final double radius;
    String kind = "circle";            // shadows the parent field — don't do this in real code

    Circle(double radius) {
        super("circle");               // must be the first statement
        this.radius = radius;
    }

    @Override double area() { return Math.PI * radius * radius; }

    @Override String describe() { return super.describe() + " (radius " + radius + ")"; }

    double getRadius() { return radius; }   // Circle-only; not visible through a Shape variable
}

class Rectangle extends Shape {
    private final double width, height;
    Rectangle(double width, double height) { super("rectangle"); this.width = width; this.height = height; }
    @Override double area() { return width * height; }
}

static double totalArea(Shape[] shapes) {
    double total = 0;
    for (Shape s : shapes) total += s.area();   // dynamic dispatch, once per element
    return total;
}

Shape s1 = new Circle(5);
Shape s2 = new Rectangle(4, 6);
System.out.println(s1.describe());   // I am a circle with area 78.53981633974483 (radius 5.0)
System.out.println(s2.describe());   // I am a rectangle with area 24.0

Shape[] drawing = {new Circle(1), new Rectangle(2, 3), new Circle(10)};
System.out.println(totalArea(drawing));  // 325.1327412287183

Shape f = new Circle(1);
System.out.println(f.kind);    // generic shape  <- field, resolved by the VARIABLE's type (Shape)
System.out.println(f.name());  // (if Shape had a name() method returning `name`) resolved by the OBJECT's type

Shape rect = new Rectangle(1, 1);
try {
    Circle boom = (Circle) rect;      // a Rectangle is not a Circle
} catch (ClassCastException e) {
    System.out.println("bad cast");   // bad cast
}
```

In this example: `s1.describe()` and `s2.describe()` are called through the exact same
`Shape`-typed reference type, but each runs its own subtype's `area()` — that's dynamic
dispatch. `totalArea` is written once, against `Shape`, and correctly sums the area of
every current and future subtype without modification (the Open/Closed Principle: open
to extension, closed to modification). `f.kind` prints `"generic shape"`, not `"circle"`,
because field access is resolved by the variable's declared type (`Shape`) at compile
time — this is the one place polymorphism does *not* apply. The bad downcast compiles
fine (the compiler only checks that `Circle` and `Rectangle` are related through `Shape`)
but fails at runtime, because the object's actual class doesn't match.

### Comparison: overriding vs. overloading

| | Overloading | Overriding |
|---|---|---|
| Same method name? | Yes | Yes |
| Parameter list | Different | Identical |
| Where declared | Usually the same class (or an unrelated one) | Subclass replaces a parent's method |
| Resolved when | Compile time (static/early binding) | Runtime (dynamic dispatch/late binding) |
| Purpose | Multiple ways to call related behavior | Specialize inherited behavior |
| One-liner | "the compiler picks overloads" | "the JVM picks overrides" |

### Why it's useful

Polymorphism is what lets frameworks call *your* code without knowing your class exists
at compile time — Spring invokes your `@Controller` methods, the collections framework
calls your `compareTo`/`equals`, JUnit calls your `@Test` methods, all through interfaces
or base classes written long before your class existed. It's also what keeps
`totalArea`-style code stable as a codebase grows: new shapes, new payment types, new
handlers all plug in without touching the code that consumes them.

### Summary / Key Takeaways

- Construction always runs parent-first; never call an overridable method from a
  constructor.
- Compile-time (variable) type gates what you can call; runtime (object) type decides
  what actually executes — for methods only, never for fields.
- `@Override` turns silent "new method by typo" bugs into compile errors — use it
  always.
- Upcasting is implicit and safe; downcasting is explicit, runtime-checked, and can
  throw `ClassCastException` — prefer `instanceof` pattern matching.
- Prefer composition over inheritance unless there's a true IS-A relationship that
  satisfies the Liskov Substitution Principle.

## 2.4 — Abstract classes & interfaces

Both **abstract classes** and **interfaces** define contracts — "you must provide this
behavior" — but for different relationships. An **abstract class** models IS-A plus
shared machinery: it can hold state, constructors, and fully implemented methods,
alongside `abstract` methods that subclasses are forced to fill in, and it cannot be
instantiated directly. An **interface** models a pure CAN-DO capability: historically no
instance state at all, implementable by any number of unrelated classes — Java's answer
to multiple inheritance of type (and, since defaults, partially of behavior too).

### Key Concepts

- **Abstract classes can have constructors**, run implicitly via a subclass's
  `super(...)` call, even though the abstract class itself can never be instantiated with
  `new`.
- **An abstract class can have zero abstract methods** — `abstract` on the class alone
  is enough to block direct instantiation, useful when you want to force subclassing
  without demanding any specific override.
- **The Template Method pattern** puts the algorithm's fixed skeleton in a `final`
  method on the abstract parent, with the variable steps expressed as `abstract` methods
  that subclasses supply. `final` prevents subclasses from rearranging the skeleton —
  they can only fill in the blanks. This is the backbone of many frameworks (JUnit's
  test lifecycle, Spring's `JdbcTemplate`).
- **Interface members are implicitly `public`.** Abstract methods declared in an
  interface are `public abstract` whether you write those keywords or not; fields are
  implicitly `public static final` (constants) — interfaces cannot hold per-instance
  state. Implementing classes must declare interface methods `public` explicitly (access
  can't be narrowed — 2.3's overriding rule applies here too).
- **`default` methods (Java 8+)** let an interface ship a method *with* a body,
  inherited for free by every implementor, and overridable if a specific implementation
  wants something different. This is literally how the JDK added `forEach`, `sort`, and
  `stream()`-adjacent methods to interfaces like `List` and `Collection` that had existed
  for over a decade, without breaking every class that already implemented them.
- **`static` methods on an interface** provide factory/helper methods that belong to the
  interface itself, not to any instance (e.g., `Notifier.none()` returning a no-op
  implementation).
- **`private` interface methods (Java 9+)** let multiple `default` methods on the same
  interface share code without exposing that shared code as part of the public contract.
- **The diamond problem, Java's way.** If a class implements two interfaces that both
  provide a `default` method with the identical signature, the compiler refuses to guess
  — it's a compile error until the class overrides the method itself. Inside that
  override, `InterfaceName.super.method()` reaches a *specific* parent interface's
  default implementation, letting you combine or choose between them explicitly.
- **Choosing between them:** need a capability that unrelated classes can share →
  interface. Need to share actual state or machinery among a tight family of related
  types → abstract class. Unsure → default to interface (it's less coupling). Modern JDK
  style often combines both: `List` is the interface (the contract) and `AbstractList`
  is an abstract skeleton class (shared machinery for implementors who want it).
- **Marker interfaces** — interfaces with no members at all (`Serializable`,
  `Cloneable`) — exist purely to tag a type for runtime `instanceof` checks. Annotations
  have mostly superseded this pattern for new code, but marker interfaces are still
  common in the JDK, and unlike annotations they participate in the type system (you can
  write `<T extends Serializable>`, which an annotation-based marker cannot express).

### Worked example — template method + interfaces as capabilities

```java
abstract class Payment {
    protected final double amount;
    Payment(double amount) { this.amount = amount; }

    final void process() {                 // template method: fixed skeleton, `final`
        if (!validate()) { System.out.println("validation failed"); return; }
        execute();                          // dynamic dispatch fills in the blank
        System.out.println("processed " + amount + " via " + methodName());
    }

    abstract boolean validate();
    abstract void execute();
    abstract String methodName();
}

interface Refundable {
    void refund();                          // implicitly public abstract
    int MAX_REFUND_DAYS = 30;               // implicitly public static final
}

class CardPayment extends Payment implements Refundable {
    private final String maskedCard;
    CardPayment(double amount, String maskedCard) { super(amount); this.maskedCard = maskedCard; }

    @Override boolean validate() { return maskedCard.length() >= 9; }
    @Override void execute()     { System.out.println("charging card " + maskedCard); }
    @Override String methodName(){ return "card"; }

    @Override
    public void refund() {   // must be `public` — interface methods can't be narrowed
        System.out.println("refunded " + amount + " to " + maskedCard);
    }
}

class UpiPayment extends Payment {          // no Refundable — that's fine, capability is optional
    private final String vpa;
    UpiPayment(double amount, String vpa) { super(amount); this.vpa = vpa; }
    @Override boolean validate() { return vpa.contains("@"); }
    @Override void execute()     { System.out.println("UPI request to " + vpa); }
    @Override String methodName(){ return "UPI"; }
}

static void refundIfPossible(Payment p) {
    if (p instanceof Refundable r) r.refund();
    else System.out.println("(no refund support for this payment type)");
}

Payment card = new CardPayment(2500, "4212-****");
Payment upi  = new UpiPayment(499, "ajay@upi");
card.process();   // charging card 4212-****   \n  processed 2500.0 via card
upi.process();     // UPI request to ajay@upi    \n  processed 499.0 via UPI
refundIfPossible(card);   // refunded 2500.0 to 4212-****
refundIfPossible(upi);    // (no refund support for this payment type)
```

In this example: `process()` is written exactly once, on the abstract parent, and is
`final` so subclasses can never reorder validate → execute → log. `CardPayment` and
`UpiPayment` supply the three abstract steps independently; `process()` calls each
through dynamic dispatch without knowing which subclass it's running on. `Refundable` is
implemented only by `CardPayment` — `refundIfPossible` uses `instanceof` to ask "can you
do this?" rather than checking a class hierarchy position, which is exactly the CAN-DO
relationship interfaces model.

### Worked example — default methods and the diamond

```java
interface Notifier {
    void send(String message);                          // required
    default void sendUrgent(String message) {            // shipped with a body, free to inherit
        send("URGENT: " + message.toUpperCase());
    }
    static Notifier none() { return msg -> { }; }         // interface static factory
}

class EmailNotifier implements Notifier {
    @Override public void send(String message) { System.out.println("email: " + message); }
}

Notifier basic = new EmailNotifier();
basic.send("payment received");        // email: payment received
basic.sendUrgent("card declined!");    // email: URGENT: CARD DECLINED!   <- default method, inherited free

interface English { default String greet() { return "Hello"; } }
interface Hindi   { default String greet() { return "Namaste"; } }

class SmartNotifier implements English, Hindi {
    @Override
    public String greet() {                              // MUST override — compiler won't guess
        return English.super.greet() + " / " + Hindi.super.greet();
    }
}

System.out.println(new SmartNotifier().greet());   // Hello / Namaste
```

`SmartNotifier` implements two interfaces that both provide a conflicting `default
greet()`; the class fails to compile until it overrides `greet()` itself, and inside
that override, `English.super.greet()` and `Hindi.super.greet()` explicitly reach each
parent's own default — that qualified-super syntax exists specifically for this
situation.

### Comparison: abstract class vs. interface

| | Abstract class | Interface |
|---|---|---|
| Instance fields | Yes | No (only `public static final` constants) |
| Constructors | Yes (run via subclass `super(...)`) | No |
| Method bodies | Any (normal methods) | `default`, `static`, `private` only |
| How many can a class have? | One (single inheritance) | Many |
| Access modifiers on members | All four | Members implicitly `public` |
| Relationship modeled | IS-A + shared code | CAN-DO capability |
| Can be instantiated? | No | No |

### Why it's useful

Interfaces are what let unrelated classes cooperate through a shared contract without a
common ancestor — `Comparable`, `Runnable`, `AutoCloseable`, and JDBC's `Connection` all
work this way. Abstract classes with template methods are how frameworks let you
customize a fixed algorithm (test setup/teardown, request handling) without letting you
break its overall shape. Together they're how Java achieves the "program to an
interface, not an implementation" principle that underlies almost all of Spring.

### Summary / Key Takeaways

- Abstract class = partial implementation with state + constructors, single inheritance,
  can't be instantiated.
- Interface = capability contract, implicitly `public` members, multiple implementation
  allowed, no instance state.
- `default`/`static`/`private` interface methods (Java 8/8/9) let interfaces evolve and
  share code without breaking existing implementors.
- Diamond conflicts between two inherited defaults are compile errors until the class
  overrides and disambiguates with `InterfaceName.super.method()`.
- Choose interface for a capability, abstract class for shared state/machinery among a
  tight family; when unsure, interface.

## 2.5 — The `Object` contracts: `equals`, `hashCode`, `toString`

Every Java class implicitly extends `Object`, which means every class inherits three
methods whose default behavior is about **identity**, not **value**: `toString()`
returns `ClassName@hexHashcode`, `equals(Object)` behaves like `==` (same object?), and
`hashCode()` is derived from that same identity. For value-like classes — two instances
with equal data should count as "the same thing" — you must override all three, or hash
collections silently misbehave.

### Key Concepts

- **Why the contract exists.** `HashMap`/`HashSet` are backed by an array of buckets.
  `hashCode()` picks which bucket an object belongs in (roughly, `hash & (capacity - 1)`);
  `equals()` disambiguates between objects that landed in the same bucket. If two objects
  are `equals()`-equal but report different hash codes, they'll be placed in *different*
  buckets and the collection will never recognize them as duplicates — this is the
  "contract" every value class must honor: **`a.equals(b)` implies `a.hashCode() ==
  b.hashCode()`** (the converse is not required — unequal objects sharing a hash code is
  a normal, harmless collision that gets resolved by chaining/probing within the
  bucket).
- **`equals()`'s own laws:** reflexive (`x.equals(x)` is always true), symmetric
  (`x.equals(y)` iff `y.equals(x)`), transitive (`x.equals(y)` and `y.equals(z)` implies
  `x.equals(z)`), consistent (repeated calls with unchanged state give the same answer),
  and `x.equals(null)` must be `false`.
- **The canonical `equals` recipe:** `==` fast path (identical reference short-circuits
  to `true`) → `instanceof` pattern check (which is also null-safe, since
  `null instanceof AnyType` is always `false`) → field-by-field comparison of the fields
  that define the value, using `Objects.equals(a, b)` for nullable fields.
- **The canonical `hashCode` recipe:** `Objects.hash(sameFieldsAsEquals)` — always the
  *same set* of fields used in `equals`, never more, never fewer. A hashCode that
  includes a field `equals` ignores (or vice versa) breaks the contract.
- **Never mutate a field that feeds `hashCode()` while the object sits inside a hash
  collection.** The object was filed under the bucket computed from its *old* hash;
  after mutation, `contains`/`get`/`remove` compute the *new* hash and probe the wrong
  bucket — the object becomes silently unfindable (not an exception, just wrong
  behavior). This is why hash-collection keys should be immutable, or at least never
  mutated while stored.
- **`instanceof` vs. `getClass()` inside `equals`.** Using `instanceof` allows a
  subclass to compare equal to its parent, but this can break *symmetry* if the subclass
  adds its own fields and its own `equals`: `parent.equals(child)` might be `true` while
  `child.equals(parent)` is `false`. Using `getClass()` restores strict symmetry (two
  objects are equal only if they're the *exact* same runtime class) at the cost of never
  allowing any cross-class equality at all. The cleanest escape from the dilemma: make
  value classes `final` (or use records, which are implicitly final) so there's no
  subclass to create the ambiguity.
- **`toString()`** exists for humans debugging or logging — make it readable, never rely
  on parsing it programmatically, and never put secrets (passwords, tokens) into it,
  since logs routinely end up somewhere less secure than the application itself.

### Worked example — broken vs. fixed

```java
class BrokenBook {                  // no overrides -> identity semantics inherited from Object
    final String title;
    final String author;
    BrokenBook(String title, String author) { this.title = title; this.author = author; }
}

BrokenBook b1 = new BrokenBook("Effective Java", "Bloch");
BrokenBook b2 = new BrokenBook("Effective Java", "Bloch");
System.out.println(b1);                 // BrokenBook@1b6d3586  <- unreadable
System.out.println(b1.equals(b2));      // false                <- same data, different objects!

Set<BrokenBook> library = new HashSet<>();
library.add(b1);
library.add(b2);                        // "duplicate" — but the set has no way to know
System.out.println(library.size());     // 2   (should be 1!)
System.out.println(library.contains(new BrokenBook("Effective Java", "Bloch")));  // false

class Book {
    private final String title;
    private final String author;
    Book(String title, String author) { this.title = title; this.author = author; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                        // 1. fast path
        if (!(o instanceof Book other)) return false;      // 2. type check + safe cast
        return title.equals(other.title) && author.equals(other.author);  // 3. same fields as hashCode
    }

    @Override
    public int hashCode() { return Objects.hash(title, author); }  // SAME fields as equals

    @Override
    public String toString() { return "Book[title=" + title + ", author=" + author + "]"; }
}

Book g1 = new Book("Effective Java", "Bloch");
Book g2 = new Book("Effective Java", "Bloch");
System.out.println(g1);                 // Book[title=Effective Java, author=Bloch]
System.out.println(g1.equals(g2));      // true

Set<Book> shelf = new HashSet<>();
shelf.add(g1);
shelf.add(g2);                          // recognized as a duplicate
System.out.println(shelf.size());       // 1
System.out.println(shelf.contains(new Book("Effective Java", "Bloch")));  // true
```

In this example: `BrokenBook` inherits `Object`'s default identity-based `equals`, so
two objects built from identical data still compare unequal — the `HashSet` therefore
stores both as if they were different books. `Book` overrides `equals`/`hashCode` on the
same two fields, so equal data produces equal hash codes and the `HashSet` correctly
collapses the duplicate. This is precisely why value classes must override all three
methods together: overriding only `equals` (and leaving the inherited identity
`hashCode`) would still break `HashSet`/`HashMap`, just in a harder-to-spot way — equal
objects with different hash codes landing in different buckets.

### Why it's useful

Almost every data class you write that isn't a pure service/utility — a `Money` amount,
a coordinate, an ID wrapper, a DTO — needs value equality to behave correctly inside
`HashSet`, as a `HashMap` key, or when compared with `assertEquals` in a test. Getting
`equals`/`hashCode` wrong is a genuinely common production bug (silent duplicate
records, phantom "not found" lookups) precisely because it fails without throwing an
exception — nothing crashes, data is just quietly wrong.

### Summary / Key Takeaways

- `Object`'s defaults are identity-based: `equals` ≡ `==`, and `toString` is unreadable
  boilerplate — override all three for value classes.
- The contract: equal objects **must** report equal hash codes; unequal hash codes for
  equal objects breaks `HashSet`/`HashMap` silently (no exception, just wrong results).
- Recipe: `equals` — `==` fast path, `instanceof` check, compare the value-defining
  fields; `hashCode` — `Objects.hash` over the *identical* set of fields.
- Never mutate a field used in `hashCode` while the object is stored in a hash
  collection — it becomes unfindable.
- Prefer `final` classes or records for value types to sidestep the `instanceof` vs.
  `getClass()` symmetry dilemma entirely.

## 2.6 — Enums, records, sealed classes

Three modern type kinds, each answering a different design question. An **enum**
answers "this type has a fixed, known-at-compile-time set of *instances*" (days of the
week, order statuses, planets). A **record** answers "this type *is* its data, nothing
more" (DTOs, coordinates, API responses). A **sealed** type answers "this type has a
fixed, known-at-compile-time set of *subtypes*" (a closed hierarchy of outcomes or
states).

### Key Concepts — enums

- **Enums are real classes.** Each declared constant (`NEW`, `PAID`, `SHIPPED`, ...) is
  a `public static final` instance of the enum type, all created once during class
  initialization. Because there's exactly one object per constant, forever, comparing
  enum values with `==` is always correct and is the idiomatic way to compare them —
  this makes enums the strongest, simplest singleton pattern available in Java (each
  constant is a thread-safe singleton, guaranteed by the JLS's class-initialization
  rules, no extra effort required).
- **Enums can have fields, constructors, and methods**, just like ordinary classes. The
  constructor is implicitly `private` — the fixed list of constants at the top of the
  declaration is the *only* place instances are ever created; you cannot `new` an enum
  constant from outside.
- **Enums can even give individual constants their own method bodies** (constant-specific
  class bodies), effectively implementing the Strategy pattern per constant — not shown
  in this repo's lesson code, but common in production Java for operations like a
  calculator's `PLUS`/`MINUS` enum where each constant implements its own `apply(a, b)`.
- **The built-in API:** `values()` returns every constant in declaration order,
  `valueOf(String)` parses a constant by exact name (throwing
  `IllegalArgumentException` if no match), `.name()` returns the declared identifier,
  and `.ordinal()` returns the zero-based declaration position.
- **Never persist `ordinal()`.** It's nothing more than declaration position — inserting
  or reordering constants silently repoints every previously stored ordinal at the wrong
  constant. Persist `.name()` (or an explicit code field) instead. (This is exactly why
  JPA's `@Enumerated` should always be `EnumType.STRING`, never the default `ORDINAL`.)
- **Switching over an enum that covers every constant needs no `default` clause** — and
  because the compiler knows the full set of constants, adding a new one later breaks
  compilation at every switch that doesn't yet handle it. The compiler becomes an
  automatic checklist for "did you update every place that reacts to this enum?"
- **`EnumSet`/`EnumMap`** are specialized, bitset/array-backed collections for enum keys
  — far faster and more memory-efficient than the general-purpose `HashSet`/`HashMap`
  equivalents, because the full key space is known and small.

### Key Concepts — records

- **A record header declares its components; the compiler generates the rest.** From
  `record Money(long amount, String currency)`, the compiler synthesizes: `private
  final` fields for each component, a canonical constructor, accessor methods named
  after the components (`amount()`, `currency()` — no `get` prefix, unlike JavaBeans),
  value-based `equals`/`hashCode` (comparing every component), and a readable `toString`.
- **The compact constructor** (`Money { ... }`, no parameter list repeated) runs
  *before* the fields are assigned — it's the place to validate or normalize incoming
  values. Reassigning a parameter inside a compact constructor changes what gets
  assigned to the field.
- **Restrictions**: a record cannot declare additional *instance* fields beyond its
  components (static fields are fine); it is implicitly `final` (can't be subclassed);
  it implicitly extends `Record` (so it can't extend anything else) but *can* implement
  interfaces.
- **Records give only shallow immutability.** `record Holder(List<String> xs)` still
  exposes a mutable `List` if the caller passes one in and the compact constructor
  doesn't defensively copy it — you still need `xs = List.copyOf(xs);` in the compact
  constructor for true immutability, exactly as with hand-written immutable classes
  (2.2).
- **"Mutation" idiom**: since records are immutable, a method that appears to modify one
  returns a brand-new record instead — often nicknamed a "wither" (`withAmount(long)`
  returning `new Money(newAmount, currency)`).
- **Best used for**: DTOs, API request/response payloads, map keys, and multi-value
  method returns — anywhere the type's entire purpose is to carry a fixed bundle of
  data.

### Key Concepts — sealed types

- **`sealed ... permits A, B, C`** restricts which types may extend/implement a class or
  interface to an explicit, closed list, all of which must be known and compiled
  alongside the sealed type itself.
- **Every permitted subtype must declare one of three modifiers**: `final` (closes that
  branch entirely — no further subclassing), `sealed` (continues restricting who can
  extend it further), or `non-sealed` (reopens that one branch to unrestricted
  subclassing).
- **The payoff is exhaustive pattern matching.** A `switch` expression over a sealed
  type that covers every permitted subtype is exhaustive *without needing a `default`
  clause* — and because the compiler knows the complete set of subtypes, adding a new
  one later makes every such `switch` a compile error until it's updated to handle the
  new case. This eliminates an entire class of "forgot to handle the new case" runtime
  bugs that an unsealed hierarchy (which would need `default`) cannot catch.
- **Sealed interface + records = algebraic data types.** Modeling "exactly these
  outcomes, nothing else" — a `Result`, a `State`, an `Event` — as a sealed interface
  with record implementations is Java's version of what functional languages call sum
  types, and it is now idiomatic modern Java for exactly this shape of problem.

### Worked example

```java
enum OrderStatus {
    NEW("order placed"), PAID("payment received"), SHIPPED("on the way"),
    DELIVERED("with customer"), CANCELLED("order cancelled");

    private final String label;
    OrderStatus(String label) { this.label = label; }
    String getLabel() { return label; }
    boolean isTerminal() { return this == DELIVERED || this == CANCELLED; }
}

OrderStatus status = OrderStatus.SHIPPED;
System.out.println(status);              // SHIPPED
System.out.println(status.ordinal());    // 2
System.out.println(status.getLabel());   // on the way

String action = switch (status) {        // exhaustive: every constant covered, no default
    case NEW        -> "await payment";
    case PAID       -> "pack the box";
    case SHIPPED    -> "track the courier";
    case DELIVERED  -> "ask for a review";
    case CANCELLED  -> "restock items";
};
System.out.println(action);              // track the courier

record Money(long amount, String currency) {
    Money {                                          // compact constructor
        if (amount < 0) throw new IllegalArgumentException("amount cannot be negative: " + amount);
        currency = currency.toUpperCase();            // normalize (reassigns the parameter)
    }
    Money withAmount(long newAmount) { return new Money(newAmount, currency); }
}

Money price = new Money(2499, "inr");
System.out.println(price);                // Money[amount=2499, currency=INR]
System.out.println(price.amount());        // 2499   <- accessor, not getAmount()
Money discounted = price.withAmount(1999);
System.out.println(price);                 // Money[amount=2499, currency=INR]   <- unchanged
System.out.println(discounted);            // Money[amount=1999, currency=INR]

sealed interface PaymentResult permits Success, Declined, NetworkError { }
record Success(String txnId)      implements PaymentResult { }
record Declined(String reason)    implements PaymentResult { }
record NetworkError(int attempts) implements PaymentResult { }

PaymentResult r = new Declined("insufficient funds");
String msg = switch (r) {                  // exhaustive over the sealed hierarchy, no default
    case Success s      -> "OK, ref=" + s.txnId();
    case Declined d     -> "declined: " + d.reason();
    case NetworkError n -> "retry (attempt " + n.attempts() + ")";
};
System.out.println(msg);                   // declined: insufficient funds
```

In this example: the `switch` over `OrderStatus` needs no `default` because the compiler
knows there are exactly five constants; deleting the `CANCELLED` case would fail to
compile. `Money`'s compact constructor both validates (rejects negative amounts) and
normalizes (`toUpperCase()`s the currency) before the fields are ever set, and
`withAmount` returns a new `Money` rather than mutating the existing one. The sealed
`PaymentResult` switch is exhaustive the same way the enum switch is — but over *types*
instead of *constants* — and adding a fourth `permits` type would break this switch at
compile time until a fourth case is added.

### Comparison: three ways to fix a set of possibilities

| | enum | record | sealed interface |
|---|---|---|---|
| Fixes a set of... | instances (values) | fields (a data shape) | subtypes (variants, possibly with different shapes) |
| Each case has different data? | No — same fields for all constants | N/A — it's one shape | Yes — each `permits` type can carry its own data |
| Compiler-checked exhaustive switch? | Yes | N/A | Yes |
| Typical use | status codes, fixed categories | DTOs, value objects | result/outcome/event modeling |

### Why it's useful

These three types together let you make illegal states genuinely unrepresentable at
compile time rather than merely discouraged at runtime. A `PaymentResult` modeled as
`sealed` with three outcome records cannot silently gain an unhandled fourth outcome
without every consuming `switch` failing to compile — compare that to a base
`PaymentResult` class with `instanceof` checks scattered around the codebase, where a
new subclass can slip through any check that wasn't updated.

### Summary / Key Takeaways

- Enums are real classes with one singleton instance per constant — safe to compare
  with `==`, can carry fields/methods, never persist `.ordinal()`.
- Records generate fields, constructor, accessors, `equals`/`hashCode`/`toString` from a
  one-line component header; validate/normalize in the compact constructor; only
  shallowly immutable — defensively copy mutable components yourself.
- Sealed types restrict a hierarchy to an explicit `permits` list, enabling exhaustive,
  `default`-free `switch` pattern matching that breaks at compile time when a new
  variant is added and not yet handled.
- Sealed interface + records = Java's algebraic data types, ideal for modeling a fixed
  set of outcomes/states/events.

## 2.7 — Nested classes & the `static` deep-dive

Java has four kinds of nested classes, each solving a different scoping/coupling
problem, plus a complete set of rules for `static` members and initializer blocks that
this lesson proves with output ordering.

### Key Concepts

- **Static nested class** — declared with `static` inside another class, carrying *no*
  reference to any outer instance. This is the default choice whenever a nested type is
  purely a namespacing/cohesion device (`Map.Entry`, or `ShoppingCart.Item` below) —
  it's just a normal class that happens to live inside another class's namespace.
- **Inner class** (non-static) — every instance is permanently tied to one *outer*
  instance, constructed with the unusual syntax `outerInstance.new Inner()`. An inner
  class can read the outer object's `private` members directly (and vice versa — nested
  classes and their enclosing class see each other's privates, implemented via
  compiler-generated synthetic accessor methods under the hood).
- **The inner-class memory trap.** Each inner instance holds a hidden reference back to
  its outer instance, which *pins that outer object in memory* for as long as the inner
  instance is reachable — a classic memory-leak shape (the canonical example is a
  non-static `Handler` inner class in Android holding an `Activity` alive after it
  should have been garbage collected). If a nested class doesn't actually need access to
  the outer instance, make it `static`.
- **Local class** — declared inside a method body, scoped to that method, and able to
  capture local variables from the enclosing method — but only if those locals are
  *effectively final* (never reassigned after their first value). The capture is a
  *copy*, taken when the local class instance is created; allowing the original local to
  be reassigned afterward would desynchronize the copy from the original, hence the
  restriction.
- **Anonymous class** — declares and instantiates a class in a single expression
  (`new Greeter() { ... }`), with no name of its own, inheriting the enclosing method's
  capture rules just like a local class. For interfaces with a single abstract method,
  lambdas (Phase 4) now replace this pattern almost entirely — but anonymous classes are
  still necessary when you need to extend a concrete class, or override more than one
  method inline.
- **Compiled representation**: nested classes compile to their own `.class` files named
  `Outer$Inner.class` — visible if you inspect a `target/classes` directory.
- **Static initializer blocks** (`static { ... }`) run exactly once, the first time the
  class is loaded (touched via instantiation, static member access, or
  `Class.forName`), before any instance can be created — the place to set up a complex
  static field a single expression can't build.
- **Instance initializer blocks** (`{ ... }`, no keyword) run before *every*
  constructor's body, in textual order relative to field initializers. They are rare in
  practice because constructors normally do this job, but they exist and matter for
  reasoning about complex multi-constructor initialization order.
- **The proven initialization order**: class load (once) → static field initializers and
  static blocks, in textual order → for each `new`: instance field initializers and
  instance blocks, in textual order → the constructor body. Under inheritance, it's
  parent statics, then child statics (once, at first load of either), then for each
  instance: parent's instance init + constructor, then child's instance init +
  constructor.
- **The complete `static` rules**: static members belong to the class — exactly one
  shared copy exists no matter how many instances (or zero instances) there are. Static
  methods have no `this` and so cannot touch instance state directly; instance methods
  *can* freely touch static state (there's only ever one copy to touch). Calling a
  static member through an instance reference (`cart.staticMethod()`) compiles but is
  misleading style — prefer `ClassName.staticMethod()`. Top-level classes cannot be
  declared `static` — there is no enclosing class for "static" to mean anything relative
  to.

### Worked example

```java
interface Greeter { String greet(String name); }

class ShoppingCart {
    private Item[] items = new Item[10];
    private int count = 0;

    void add(Item item) { items[count++] = item; }
    int total() { int sum = 0; for (int i = 0; i < count; i++) sum += items[i].price; return sum; }

    static class Item {                 // static nested: no tie to any particular cart
        final String name;
        final int price;
        Item(String name, int price) { this.name = name; this.price = price; }
    }

    class Auditor {                     // inner: bound to one cart, reads its privates
        void report() {
            System.out.println("audit: " + count + " items, total " + total());
            // Explicit form if names clashed: ShoppingCart.this.count
        }
    }
}

ShoppingCart.Item item = new ShoppingCart.Item("keyboard", 2999);
ShoppingCart cart = new ShoppingCart();
cart.add(item);
cart.add(new ShoppingCart.Item("mouse", 999));
System.out.println(cart.total());              // 3998

ShoppingCart.Auditor auditor = cart.new Auditor();   // note: outerInstance.new Inner()
auditor.report();                               // audit: 2 items, total 3998

Greeter formal = new Greeter() {                // anonymous class
    @Override public String greet(String name) { return "Good day, " + name + "."; }
};
System.out.println(formal.greet("Ajay"));       // Good day, Ajay.

Greeter casual = name -> "yo " + name;          // lambda — same job, far less ceremony
System.out.println(casual.greet("Ajay"));       // yo Ajay

class Config {
    static final String APP;
    static {
        System.out.println("[static block] runs ONCE");
        APP = "tech-stack";
    }
    { System.out.println("[instance block] runs before each constructor"); }
    Config() { System.out.println("[constructor] runs last"); }
}

System.out.println(Config.APP);   // [static block] runs ONCE   \n   tech-stack
new Config();                      // [instance block] runs before each constructor
                                    // [constructor] runs last
new Config();                      // [instance block] ...  (static block does NOT run again)
                                    // [constructor] ...
```

In this example: `ShoppingCart.Item` is constructed without any `ShoppingCart` instance
at all (`new ShoppingCart.Item(...)`), proving it carries no outer reference, while
`Auditor` requires the unusual `cart.new Auditor()` syntax precisely because each
`Auditor` is permanently bound to the `cart` it was created from and can read `count`
and `items` directly as if they were its own fields. `Config.APP` triggers class loading
on first touch, which runs the static block exactly once — a second `new Config()` does
*not* re-run it, but *does* re-run the instance block before its own constructor body,
proving the "static once, instance every time" rule from the source output.

### Comparison: the four kinds of nested classes

| kind | needs outer instance? | typical use |
|---|---|---|
| static nested | No | Namespacing/cohesion (`Map.Entry`, `ShoppingCart.Item`) — default choice |
| inner (non-static) | Yes (hidden reference) | A helper intrinsically tied to one specific outer object |
| local | No (but can capture effectively-final locals) | A scoped one-off type used only inside a single method |
| anonymous | Inherits enclosing context | An inline one-off implementation — mostly superseded by lambdas |

### Why it's useful

Static nested classes keep tightly related helper types (`Map.Entry`, builder classes,
small value objects only meaningful inside one class) namespaced without polluting the
top-level package, and without accidentally leaking a reference to some outer object
that has nothing to do with them. Inner classes are the right tool exactly when a helper
genuinely needs live access to a specific enclosing object's state — iterators are a
classic real-world example, where `Iterator` implementations are frequently non-static
inner classes of the collection they iterate, needing direct access to that collection's
internals. Anonymous classes (and the lambdas that mostly replaced them) are what make
callback-style APIs (event listeners, `Runnable`, comparator arguments) concise.

### Summary / Key Takeaways

- Default to `static` nested classes; use non-static inner classes only when you
  genuinely need the hidden link back to a specific outer instance — that link can leak
  memory if held onto too long.
- Local and anonymous classes capture *effectively final* locals only, because the
  capture is a copy taken at creation time.
- Static blocks run once, at class load; instance blocks run before every constructor,
  every time an object is created.
- Static members belong to the class (one shared copy); static methods can't use `this`
  or touch instance state directly.
- Top-level classes can never be `static` — the modifier only makes sense relative to an
  enclosing class.
