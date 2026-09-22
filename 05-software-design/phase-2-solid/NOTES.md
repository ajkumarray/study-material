<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · clean code](../phase-1-clean-code/NOTES.md) | [Phase 3 · creational ➡](../phase-3-creational/NOTES.md)
<!-- /nav -->

# Phase 2 — The SOLID Principles: Notes

**SOLID** is an acronym for five object-oriented design principles, popularized by Robert C. Martin ("Uncle Bob"), that push a codebase toward being **easy to change, easy to test, and easy to extend**. They are not rules to apply mechanically to every class — they are *pressures* that, applied where a design is actually hurting, produce loose coupling and high cohesion (Phase 1). Each principle below has a runnable before→after example in this folder (`S_SingleResponsibility.java`, `O_OpenClosed.java`, `L_LiskovSubstitution.java`, `I_InterfaceSegregation.java`, `D_DependencyInversion.java`).

---

## S — Single Responsibility Principle (SRP)

**A class should have one, and only one, reason to change.**

### Key Concepts
- **"Reason to change" = axis of change, not "number of methods."** A class can have several methods and still have one responsibility, as long as all those methods change for the same underlying reason.
- **A "god class"** bundles multiple unrelated responsibilities — each one a different reason the class might need to be edited — so a change to *any* of them touches the same file and risks breaking the others.
- **Applies at every scale**: not just classes, but methods (Phase 1's "do one thing") and modules/packages (Phase 6's layered architecture: one layer, one concern).
- **SRP is the foundation the other four SOLID principles build on** — you can't cleanly apply Open/Closed, Liskov Substitution, or Dependency Inversion to a class that's already doing three unrelated jobs.

### Worked Example

```java
/* BEFORE (the anti-pattern) —
   class Employee {
       String name; double salary;
       double calculatePay() { ... tax rules ... }      // reason to change #1: pay policy
       void save() { ... JDBC/SQL ... }                 // reason to change #2: persistence
       String toPayslip() { ... formatting ... }        // reason to change #3: report format
   }
   Three unrelated reasons to change live in one class. A tax-law tweak, a DB
   migration, and a report redesign all edit Employee — stepping on each other. */

// AFTER — from S_SingleResponsibility.java: a plain data holder
record Employee(String name, double grossSalary, double taxRate) { }

// Responsibility: pay calculation (changes when pay/tax POLICY changes)
class PayrollCalculator {
    double netPay(Employee e) {
        return e.grossSalary() * (1 - e.taxRate());
    }
}

// Responsibility: persistence (changes when STORAGE changes)
interface EmployeeRepository {
    void save(Employee e);
    int count();
}
class InMemoryEmployeeRepo implements EmployeeRepository {
    private final List<Employee> store = new java.util.ArrayList<>();
    public void save(Employee e) { store.add(e); }
    public int count() { return store.size(); }
}

// Responsibility: presentation (changes when the REPORT FORMAT changes)
class PayslipFormatter {
    String format(Employee e, double net) {
        return "Payslip[%s: gross=%.0f, net=%.0f]".formatted(e.name(), e.grossSalary(), net);
    }
}
```

In this example, `Employee` becomes a pure data holder with no behavior at all — its only reason to change is the employee *model* itself gaining or losing a field. `PayrollCalculator` changes only if the pay/tax policy changes. `InMemoryEmployeeRepo` changes only if the storage mechanism changes (swap it for a JDBC-backed implementation without touching payroll math or formatting). `PayslipFormatter` changes only if the report layout changes. `main` wires the three collaborators together and orchestrates: `repo.save(e)`, `payroll.netPay(e)`, `reporter.format(e, net)` — none of the three collaborators knows about the others.

### Why It's Useful
Each class is independently testable — you can unit-test `PayrollCalculator.netPay` with plain `Employee` values and no database in sight, something impossible when pay math and JDBC calls live in the same class. Each class is independently reusable and swappable: `PayslipFormatter` can be replaced with a PDF formatter, or `InMemoryEmployeeRepo` with a JDBC-backed one, without touching the other two. And edits are localized — a tax-law change only ever touches `PayrollCalculator`, so there's no risk of a payroll fix accidentally breaking persistence or formatting.

### Summary / Key Takeaways
- One class, one axis of change — not one method, one job.
- A god class bundling unrelated responsibilities is the classic violation.
- SRP payoff: localized changes, independent testability, independent reuse.
- Every other SOLID principle assumes SRP is already roughly in place.

---

## O — Open/Closed Principle (OCP)

**Software entities (classes, modules, functions) should be open for extension, but closed for modification.**

### Key Concepts
- **"Open for extension"**: you should be able to add new behavior.
- **"Closed for modification"**: you should be able to add that behavior *without editing existing, already-tested code.*
- **The enabler is polymorphism**: depend on an abstraction, and let new implementations extend it — this is exactly the "program to an interface, not an implementation" idea, and it's the same mechanism behind dynamic dispatch (`shape.area()` calling the *actual* runtime type's implementation).
- **The smell it removes**: a growing `if (x instanceof A) ... else if (x instanceof B) ...` (or a `switch` on a type tag) that must be edited — and re-tested — every time a new case is added.
- **Balance with YAGNI (Phase 1)**: don't build the abstraction until you actually have (or clearly anticipate) more than one implementation; abstracting prematurely is its own form of over-engineering.

### Worked Example

```java
/* BEFORE (violates OCP) —
   double area(Object shape) {
       if (shape instanceof Circle c)       return Math.PI * c.r * c.r;
       else if (shape instanceof Rectangle r) return r.w * r.h;
       // every NEW shape forces you to EDIT this method (and re-test it),
       // risking the cases that already worked.
   } */

// AFTER — from O_OpenClosed.java: the abstraction
interface Shape {
    double area();
}
record Circle(double r) implements Shape {
    public double area() { return Math.PI * r * r; }
}
record Rectangle(double w, double h) implements Shape {
    public double area() { return w * h; }
}
// Adding Triangle = ADDING this class. AreaCalculator stays closed (untouched).
record Triangle(double base, double height) implements Shape {
    public double area() { return 0.5 * base * height; }
}

// Closed for modification: this works for every current AND future Shape.
class AreaCalculator {
    double totalArea(List<Shape> shapes) {
        return shapes.stream().mapToDouble(Shape::area).sum();
    }
}
```

In this example, adding `Triangle` required creating one new record — `AreaCalculator.totalArea` was never touched, never re-compiled with new logic, never re-tested for regressions in the `Circle`/`Rectangle` cases. `List<Shape> shapes = List.of(new Circle(2), new Rectangle(3, 4), new Triangle(6, 2))` mixes all three types, and `totalArea` sums their areas via `Shape::area` with no knowledge of which concrete types exist — Java's dynamic dispatch resolves `area()` to the correct implementation at runtime for each element.

### Why It's Useful
New requirements become new classes instead of edits to tested code — which means far fewer regressions, since the code paths for `Circle` and `Rectangle` are never re-touched when `Triangle` is added. `AreaCalculator` also never accumulates a growing `if/else` chain to maintain. Real-world realizations of OCP: the **Strategy pattern** (Phase 5) — inject a new algorithm without touching the class that uses it; **plugin architectures**; Spring's ability to add a new `@Component` implementing an existing interface without editing any registry.

### Summary / Key Takeaways
- Add behavior by adding code (new classes), not by editing tested code.
- Polymorphism (interfaces + dynamic dispatch) is the mechanism.
- The smell it fixes: a type-switch that grows with every new case.
- Don't abstract prematurely — apply OCP when a second variant is real, not hypothetical (YAGNI).

---

## L — Liskov Substitution Principle (LSP)

**Subtypes must be substitutable for their base type without breaking the correctness of code written against the base type.** If code works correctly with a base type, it must keep working correctly with *any* subtype, with no special-casing.

### Key Concepts
- **Inheritance is a behavioral promise, not just a structural one.** `class Square extends Rectangle` compiles and type-checks fine — but LSP asks whether `Square` actually *behaves* the way any code written against `Rectangle` expects it to.
- **A well-behaved subtype must preserve**:
  - **Preconditions no stronger than the base** — don't demand more from callers than the base type did.
  - **Postconditions no weaker than the base** — deliver at least what the base type promised.
  - **Invariants preserved** — don't break rules the base type guaranteed.
  - **No new, unexpected exceptions** the base type didn't already throw.
- **Classic smells**: overriding a method to throw `UnsupportedOperationException` or to silently do nothing; `if (x instanceof SpecificSubtype)` special-casing scattered through client code (a sign the client no longer trusts the substitutability promise).
- **The fix is usually composition over inheritance**, or modeling the two things as siblings under a shared abstraction rather than forcing one to inherit from the other.

### Worked Example

```java
// BEFORE: Square extends Rectangle. Looks fine ("a square IS a rectangle"),
// but overriding the setters to keep sides equal BREAKS the Rectangle
// contract that width and height vary independently.
static class Rectangle {
    protected int width, height;
    void setWidth(int w)  { this.width = w; }
    void setHeight(int h) { this.height = h; }
    int area() { return width * height; }
}
static class Square extends Rectangle {
    @Override void setWidth(int w)  { this.width = w; this.height = w; }   // side effect!
    @Override void setHeight(int h) { this.width = h; this.height = h; }   // side effect!
}

static void demonstrateBreakage() {
    Rectangle r = new Square();          // substitute a Square for a Rectangle...
    r.setWidth(5);
    r.setHeight(4);                      // caller expects width=5, height=4 -> area 20
    System.out.printf("expected area 20, got %d  <-- LSP VIOLATED%n", r.area());  // 16!
}

// AFTER — from L_LiskovSubstitution.java: don't force a false IS-A
interface Shape4 { double area(); }
record Rect(int w, int h) implements Shape4 { public double area() { return w * h; } }
record Sq(int side)       implements Shape4 { public double area() { return side * side; } }
```

In this example, `demonstrateBreakage()` writes code that is *completely correct* for a real `Rectangle`: set width to 5, set height to 4, expect area 20. Substituting a `Square` — which LSP says must be safe — silently produces `16` instead, because `Square`'s overridden `setWidth` also mutates `height` as a side effect the caller never asked for and has no way to know about from `Rectangle`'s contract. The caller isn't wrong; `Square` broke the promise `Rectangle` made. The fix models `Rect` and `Sq` as independent implementations of a shared `Shape4` interface, each with only the behavior appropriate to it — neither one inherits setters it can't honor.

### Why It's Useful
LSP violations are especially dangerous because they compile cleanly and pass a casual glance — the bug only shows up at runtime, and often only for specific call sequences, making it one of the harder classes of bug to catch without either a specific regression test or a design review that asks "does this override actually preserve the base type's contract?"

### Summary / Key Takeaways
- Substitutability means "no special-casing needed anywhere the base type is used."
- Overriding a method to throw/no-op, or `instanceof`-checking a subtype in client code, are the classic tells.
- The Square/Rectangle trap is the canonical example: `Square extends Rectangle` looks like a valid IS-A but breaks the base's behavioral contract.
- Fix with composition over inheritance, or a shared abstraction both types implement independently.

---

## I — Interface Segregation Principle (ISP)

**No client should be forced to depend on methods it does not use.** Prefer many small, focused ("role") interfaces over one large, general-purpose one.

### Key Concepts
- **A fat interface forces irrelevant implementations.** If `Worker` declares both `work()` and `eat()`, then every implementer — including one that has no business eating — must provide *some* implementation of `eat()`, usually by throwing `UnsupportedOperationException`.
- **That throwing stub is a double violation** — it violates ISP (the class was forced to depend on/implement a method it doesn't need) *and* LSP (Phase 2's L: a caller holding a `Worker` reference can no longer trust that calling `eat()` won't blow up).
- **The fix**: split the fat interface along the roles clients actually use, and let each implementer pick up only the roles that genuinely apply to it.
- **ISP is SRP applied to interfaces** — just as a class should have one reason to change, an interface should represent one cohesive role, not a bundle of unrelated capabilities.

### Worked Example

```java
/* BEFORE (violates ISP) —
   interface Worker { String work(); String eat(); }
   class Robot implements Worker {
       public String work() { return "building"; }
       public String eat()  { throw new UnsupportedOperationException(); }  // robots don't eat!
   }
   The fat Worker interface forces Robot to implement eat(). Callers can't trust
   eat() on a Worker (it might throw) — and that throw also violates LSP. */

// AFTER — from I_InterfaceSegregation.java: small role interfaces
interface Workable { String work(); }
interface Eatable  { String eat(); }

class Human implements Workable, Eatable {   // a human does both roles
    private final String name;
    Human(String name) { this.name = name; }
    public String work() { return name + " is working"; }
    public String eat()  { return name + " is eating"; }
}

class Robot implements Workable {            // a robot only works — no eat() forced
    private final String id;
    Robot(String id) { this.id = id; }
    public String work() { return "Robot " + id + " is working"; }
}
```

In this example, `Robot` implements only `Workable` — there is no `eat()` method to fake, throw from, or leave broken. Code that only needs "things that can eat" declares a dependency on `Eatable`, not on the wider `Workable`/`Worker` surface, so a `Robot` simply isn't a valid argument for that code — a compile-time guarantee instead of a runtime surprise. `main` demonstrates this: `Eatable human = new Human("Ajay")` compiles because `Human` implements both roles, while there is no way to assign a `Robot` to an `Eatable` reference at all.

### Why It's Useful
Implementers only ever provide behavior that genuinely applies to them — no throwing stubs, no silent no-ops. Clients depend on the narrowest interface that satisfies what they actually need, which loosens coupling (a change to `Eatable`'s contract can't possibly affect `Robot`, since `Robot` never implemented it). Real-world examples: Java splitting `Runnable` (no return value) from `Callable<V>` (returns a value, can throw) instead of one fat task interface; Spring's many small, focused `*Aware` interfaces (`ApplicationContextAware`, `BeanNameAware`) and repository interfaces instead of one giant "do everything" interface.

### Summary / Key Takeaways
- Split fat interfaces into small, role-specific ones.
- A throwing/no-op stub implementation is the classic tell that ISP (and often LSP) is being violated.
- Clients should depend only on the methods they actually use.
- ISP is SRP's idea applied one level up, to interfaces instead of classes.

---

## D — Dependency Inversion Principle (DIP)

**High-level modules should not depend on low-level modules — both should depend on abstractions. Abstractions should not depend on details; details should depend on abstractions.**

### Key Concepts
- **A high-level policy that `new`s a concrete low-level detail is welded to it.** `NotificationService` constructing `new EmailSender()` internally can never be swapped to SMS/Slack without editing `NotificationService`'s source, and can never be unit-tested without actually sending an email.
- **"Inversion" refers to the direction of the source-code dependency.** Normally you'd expect the high-level policy to depend on (import, reference) the low-level detail. DIP inverts that: both the high-level module *and* the low-level detail depend on an abstraction (an interface) that the high-level module effectively *owns* — the dependency arrow now points from the detail *toward* the abstraction, not from the policy toward the detail.
- **The concrete implementation is injected**, typically via the constructor, rather than constructed internally — this is the technique of **Dependency Injection**.
- **DIP vs. DI vs. IoC** (a very common point of confusion — see the comparison table below).

### Worked Example

```java
/* BEFORE (violates DIP) —
   class NotificationService {
       private final EmailSender sender = new EmailSender();   // welded to a concrete class
       String notify(String msg) { return sender.send(msg); }
   }
   NotificationService (high-level) depends directly on EmailSender (low-level).
   Can't switch to SMS/Slack without editing it; can't unit-test without really
   sending email. The dependency arrow points the wrong way. */

// AFTER — from D_DependencyInversion.java: the abstraction both sides depend on
interface MessageSender {
    String send(String message);
}

// High-level policy depends on the ABSTRACTION, received via the constructor.
class NotificationService {
    private final MessageSender sender;                 // an interface, not a concrete type
    NotificationService(MessageSender sender) {         // dependency INJECTED
        this.sender = sender;
    }
    String notify(String message) {
        return "notified: " + sender.send(message);
    }
}

// Low-level details implement the abstraction. Add SlackSender/SmsSender freely.
class EmailSender implements MessageSender {
    public String send(String m) { return "[email] " + m; }
}
class FakeSender implements MessageSender {             // a test double
    private String last;
    public String send(String m) { this.last = m; return "[fake] " + m; }
    String last() { return last; }
}
```

In this example, `main` constructs `new NotificationService(new EmailSender())` for production and `new NotificationService(new FakeSender())` for a test — `NotificationService` itself never changes. `FakeSender` captures the last message sent (`test.sender()`, cast to `FakeSender`, exposes `.last()`) without ever performing real I/O, which is exactly what makes the policy unit-testable without a network connection. This is the same design your Java capstone's `ExpenseService → ExpenseRepository` (an interface) used by hand, and it's precisely what Spring's `@Service` constructor injection automates.

### Comparison: DIP vs. Dependency Injection vs. Inversion of Control

| | What it is | Analogy |
|---|---|---|
| **DIP** | The design **principle**: depend on abstractions, not concrete details. | The rule "don't weld your policy to one vendor." |
| **Dependency Injection (DI)** | A **technique** for supplying a dependency from *outside* a class (constructor, setter, or field injection) instead of the class constructing it internally. | Handing a class its collaborator instead of it going and building one. |
| **Inversion of Control (IoC)** | The **broader idea** that a framework, not your own code, controls object creation and wiring/flow. | The framework calls you, instead of you calling the framework. |
| **IoC container (e.g., Spring)** | A concrete tool that **automates** DI by scanning, constructing, and wiring beans for you. | The mechanic that hands you the assembled car. |

### Why It's Useful
You can swap `EmailSender` for `SlackSender` or `SmsSender` with zero changes to `NotificationService`. You can unit-test the policy in complete isolation from real I/O, using a fake or mock. And the two modules — the policy and any given sender — can be developed and changed independently by different people without stepping on each other, since they only share a small, stable interface.

### Summary / Key Takeaways
- High-level and low-level modules both depend on an abstraction; neither depends on the other directly.
- The concrete implementation is *injected* (constructor injection is the most common form), not constructed internally.
- DIP is the *why* (depend on abstractions); DI is the *how* (inject them); an IoC container is *who does the wiring for you*.
- Your Java capstone's repository interface and Spring's `@Service` + constructor injection are the same principle — one wired by hand, one wired by a framework.

---

## Perspective

SOLID isn't dogma, and over-applying it creates its own cost — a factory that builds a factory, an interface with exactly one implementation that will never have a second, indirection that exists for its own sake rather than to relieve a real pain. The goal is **manageable change**: reach for a specific principle when you actually feel the pain it's designed to prevent — rigid code that resists an obviously-coming new requirement (Open/Closed), fragile code that breaks somewhere unrelated when you touch it (Liskov Substitution, Interface Segregation), or code you simply can't unit-test in isolation (Dependency Inversion, Single Responsibility). SOLID sits alongside the broader Phase 1 toolkit — DRY, KISS, YAGNI, high cohesion / low coupling, composition over inheritance — and directly motivates the concrete, named solutions that follow in Phases 3–5: the GoF design patterns are, in large part, proven embodiments of these five principles.
