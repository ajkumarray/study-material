<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · structural](../phase-4-structural/NOTES.md) | [Phase 6 · architecture ➡](../phase-6-architecture/NOTES.md)
<!-- /nav -->

# Phase 5 — Behavioral Design Patterns: Notes

Behavioral patterns govern **how objects communicate and distribute responsibility** — algorithms, message passing, and control flow between collaborators. You've already *used* several of these without necessarily naming them: Strategy shows up every time you pass a lambda (a `Comparator`, a `Function`); Template Method is exactly the shape of the Java capstone's payment flow. This phase formalizes and names them so you can recognize and discuss them deliberately. All examples below are runnable in `BehavioralPatterns.java`.

---

## Strategy — swap an algorithm at runtime

Define a family of interchangeable algorithms behind a common interface, and let the client select — or be handed — one at runtime.

### Key Concepts
- **The client is decoupled from the specific algorithm** — `Cart` only knows it holds *some* `DiscountStrategy`; it has no idea whether that's a percentage discount, a flat discount, or no discount at all.
- **Adding a new algorithm means adding a new class, never touching the client** — pure Open/Closed (Phase 2) in action.
- **In modern Java, a strategy is very often just a lambda or method reference.** `DiscountStrategy` is a single-abstract-method interface, so `amount -> amount` (no discount) is a perfectly valid strategy with no named class at all — you've been using Strategy throughout every functional-programming exercise involving `Comparator`, `Function`, `Predicate`, etc.
- **Injected via the constructor** — the strategy is typically supplied to the class that uses it (constructor injection), tying Strategy directly to Dependency Inversion (Phase 2) as well.

### Worked Example

```java
// From BehavioralPatterns.java
interface DiscountStrategy { double apply(double amount); }
record PercentDiscount(double pct) implements DiscountStrategy {
    public double apply(double a) { return a * (1 - pct / 100); }
}
record FlatDiscount(double off) implements DiscountStrategy {
    public double apply(double a) { return Math.max(0, a - off); }
}
static class Cart {
    private final DiscountStrategy discount;      // the injected strategy
    Cart(DiscountStrategy discount) { this.discount = discount; }
    double total(double amount) { return discount.apply(amount); }
}

// usage:
assert new Cart(new PercentDiscount(10)).total(200) == 180;
assert new Cart(new FlatDiscount(50)).total(200) == 150;
assert new Cart(amount -> amount).total(200) == 200;   // a lambda IS a strategy
```

In this example, `Cart` never checks "is this a percent discount or a flat discount?" — it just calls `discount.apply(amount)`, and whichever concrete `DiscountStrategy` was injected determines the result. `new Cart(new PercentDiscount(10)).total(200)` returns `180` (10% off 200); `new Cart(new FlatDiscount(50)).total(200)` returns `150` (200 minus a flat 50); and `new Cart(amount -> amount).total(200)` returns `200` unchanged, using a bare lambda as the strategy with no named class implementing `DiscountStrategy` anywhere in sight.

### Why It's Useful
New pricing rules — a loyalty discount, a bulk discount, a holiday promotion — become new `DiscountStrategy` implementations (or new lambdas) with zero changes to `Cart`. This is the everyday workhorse of behavioral patterns precisely because Java's functional interfaces make it nearly free to apply: any time you pass behavior as a parameter, you're doing Strategy.

---

## Observer — publish/subscribe notifications

A subject maintains a list of interested observers and notifies all of them whenever a relevant event occurs, without the subject knowing anything about the observers' concrete types.

### Key Concepts
- **Decouples the event source from the reactors** — `Channel` (the subject) has no idea what `A` or `B` (the observers) actually do with a published message; it just calls `onMessage` on everyone subscribed.
- **One-to-many notification** — any number of observers can subscribe, and a single `publish` call reaches every one of them.
- **Subscription is dynamic** — observers can be added (and, in a fuller implementation, removed) at runtime; the subject's behavior toward existing observers doesn't need to change as the observer list grows or shrinks.
- **Everywhere in practice**: JDK event listeners, the DOM's `addEventListener` (JS track), reactive streams, and message/event buses at the system-design scale (System Design Phase 9) are all Observer at different granularities.

### Worked Example

```java
// From BehavioralPatterns.java
interface Subscriber { void onMessage(String msg); }
static class Channel {
    private final List<Subscriber> subs = new ArrayList<>();
    void subscribe(Subscriber s) { subs.add(s); }
    void publish(String msg) { subs.forEach(s -> s.onMessage(msg)); }   // notify all
}

// usage:
var channel = new Channel();
List<String> log = new ArrayList<>();
channel.subscribe(msg -> log.add("A:" + msg));
channel.subscribe(msg -> log.add("B:" + msg));
channel.publish("hi");
assert log.equals(List.of("A:hi", "B:hi"));            // both notified
```

In this example, two subscribers — both plain lambdas implementing `Subscriber` — register with `channel.subscribe`. A single `channel.publish("hi")` call reaches both, in subscription order, each appending its own tagged entry to the shared `log`. `Channel` never referenced `A` or `B` by name or type; it only ever called the `Subscriber` interface's one method, `onMessage`, on whatever was registered at publish time.

### Why It's Useful
Observer lets you add new reactions to an event without touching the code that raises the event — a new subscriber is just another `subscribe` call, with zero changes to `Channel.publish`. This is Open/Closed applied to notifications, and it's the foundation of most event-driven designs, from a UI's click handlers up to a distributed system's event bus.

---

## Command — a request as an object

Encapsulate a request — an action plus the data it needs — as an object with an `execute()` method (and often `undo()`), so requests can be parameterized, queued, logged, and reversed.

### Key Concepts
- **The action becomes a first-class object**, not just a direct method call — this is what makes it possible to store a request, pass it around, queue it for later, or keep a history of executed ones.
- **Undo/redo falls out naturally** — if every `Command` knows how to reverse itself (`undo()`), a history stack of executed commands gives you undo for free: pop the most recent command and call its `undo()`.
- **Decouples the invoker from the receiver** — `CommandHistory` knows how to `execute()` and `undo()` any `Command`, but has no idea what a `TypeCommand` actually does to a `TextEditor`; that knowledge lives entirely inside the command itself.
- **Real-world uses**: GUI actions (every menu item/button click is often a Command object), task queues and job schedulers, transaction logs, macro recording (record a sequence of Commands, replay them later).

### Worked Example

```java
// From BehavioralPatterns.java
static class TextEditor { StringBuilder text = new StringBuilder(); }
interface Command { void execute(); void undo(); }
static class TypeCommand implements Command {
    private final TextEditor editor; private final String s;
    TypeCommand(TextEditor e, String s) { this.editor = e; this.s = s; }
    public void execute() { editor.text.append(s); }
    public void undo() { editor.text.setLength(editor.text.length() - s.length()); }
}
static class CommandHistory {
    private final Deque<Command> done = new ArrayDeque<>();
    void execute(Command c) { c.execute(); done.push(c); }
    void undo() { if (!done.isEmpty()) done.pop().undo(); }   // reverse the last command
}

// usage:
var editor = new TextEditor();
var history = new CommandHistory();
history.execute(new TypeCommand(editor, "hello "));
history.execute(new TypeCommand(editor, "world"));
assert editor.text.toString().equals("hello world");
history.undo();                                        // undo last command
assert editor.text.toString().equals("hello ");
```

In this example, `history.execute(new TypeCommand(editor, "hello "))` both runs the command immediately (appending `"hello "` to `editor.text`) and pushes it onto `done`, a stack of executed commands. After a second `execute` appends `"world"`, `editor.text` reads `"hello world"`. Calling `history.undo()` pops the *most recently executed* command — the `"world"` one — and calls its `undo()`, which trims exactly `"world".length()` characters off the end of `editor.text`, leaving `"hello "`. The `"hello "` command is untouched; only the last executed command was reversed, which is exactly what a proper undo stack should do.

### Why It's Useful
Anywhere "do this later," "log what was done," or "let the user undo this" is a real requirement, Command is the structural answer — the alternative (directly calling methods with no record of what happened) simply has nowhere to store the information needed to reverse or replay an action.

---

## Template Method — fixed skeleton, customizable steps

A base class defines the **fixed skeleton** of an algorithm in one method (often `final`, so it can't be overridden), while deferring specific, varying steps to subclasses.

### Key Concepts
- **The overall flow never changes** — every `Report` follows header → body → footer, in that order, no matter which subclass is used; only the *content* of each step varies.
- **Subclasses fill in the blanks, not the sequence** — `CsvReport` overrides `body()` alone; it has no ability to reorder the steps or skip one, since `generate()` itself is fixed.
- **Uses inheritance, not composition** — this is the key structural difference from Strategy (below): Template Method's variation point is an *overridden method* in a subclass, while Strategy's variation point is an *injected object*.
- **Everywhere in frameworks**: JUnit's test lifecycle (`@BeforeEach` → test method → `@AfterEach` is a fixed skeleton with pluggable steps), and Spring's `*Template` classes (`JdbcTemplate`, `RestTemplate`) fix the skeleton of "acquire resource → do the variable part → clean up resource, handle exceptions" while you supply the variable part via a callback.

### Worked Example

```java
// From BehavioralPatterns.java
static abstract class Report {
    final String generate() {                     // the FIXED skeleton
        return header() + "\n" + body() + "\n" + footer();
    }
    String header() { return "HEADER"; }          // default steps
    String footer() { return "FOOTER"; }
    abstract String body();                        // the subclass fills this in
}
static class CsvReport extends Report {
    String body() { return "data,as,csv"; }
}

// usage:
assert new CsvReport().generate().equals("HEADER\ndata,as,csv\nFOOTER");
```

In this example, `Report.generate()` is `final` — no subclass can change the *order* header → body → footer, or skip a step. `CsvReport` only supplies `body()`; it inherits `header()` and `footer()`'s default implementations unchanged. A second report type (say, `JsonReport`) would only need to override `body()` as well — the skeleton in `generate()` stays exactly as written, shared by every report subclass that ever exists.

### Why It's Useful
Template Method is the right tool specifically when the overall *sequence* of an algorithm is genuinely fixed and shouldn't vary, but one or two steps within that sequence legitimately differ by case — it guarantees every subclass follows the same required order (you can't accidentally forget to call `footer()`, because `generate()` calls it for you).

---

## State — behavior changes with internal state

An object delegates its behavior to a **state object**, and transitions between different state objects over time — so the object appears to change its class as its internal state changes.

### Key Concepts
- **Replaces sprawling conditionals on a status field** — instead of `if (status == DRAFT) ... else if (status == REVIEW) ... else if (status == PUBLISHED) ...` scattered through every method that cares about status, each state becomes its own class implementing the shared behavior for that state only.
- **A state object decides the next state** — in the example below, `Draft.publish` is what actually assigns `Review` as the document's next state; the `Doc` object itself just delegates and holds whichever state object is "current."
- **Open/Closed for new states** — adding a new state (say, `Archived`) means adding a new class implementing `DocState`, without touching the existing state classes' logic.
- **Implements a state machine** — the state graph (which states can transition to which) is expressed as which state classes know how to construct which other state classes.

### Worked Example

```java
// From BehavioralPatterns.java
static class Doc {
    private DocState state = new Draft();
    String publishAction() { return state.publish(this); }
    void setState(DocState s) { this.state = s; }
}
interface DocState { String publish(Doc d); }
static class Draft implements DocState {
    public String publish(Doc d) { d.setState(new Review()); return "moved to review"; }
}
static class Review implements DocState {
    public String publish(Doc d) { d.setState(new Published()); return "published"; }
}
static class Published implements DocState {
    public String publish(Doc d) { return "already published"; }
}

// usage:
var doc = new Doc();
assert doc.publishAction().equals("moved to review");   // Draft -> Review
assert doc.publishAction().equals("published");         // Review -> Published
```

In this example, `doc` starts in the `Draft` state. The first `publishAction()` call delegates to `Draft.publish(doc)`, which transitions `doc` to a new `Review` state object and returns `"moved to review"`. The *second* call to `publishAction()` on the *same* `doc` object now delegates to `Review.publish(doc)` instead — `doc.publishAction()`'s behavior genuinely changed without any conditional logic inside `Doc` itself, purely because its internal `state` field now points at a different object. A third call would delegate to `Published.publish`, which simply returns `"already published"` with no further transition — a terminal state.

### Why It's Useful
State turns "the object's behavior depends on which phase of its lifecycle it's in" from an ever-growing conditional (fragile, easy to forget a case, hard to extend) into a set of small, independently-testable classes, each responsible for exactly one state's behavior and its outgoing transitions.

---

## Chain of Responsibility — pass a request along handlers

Give multiple candidate handlers a chance to process a request, by passing it along a **chain** until one of them handles it (or the chain is exhausted).

### Key Concepts
- **Each handler either handles the request or forwards it** — a handler decides independently whether it's the right one to act, and if not, passes the request to the next handler in the chain.
- **The sender doesn't know (or need to know) which handler will actually process the request** — it only knows the entry point of the chain, decoupling sender from eventual receiver.
- **The pipeline is configurable** — the chain itself (which handlers, in what order) is assembled separately from the handlers' individual logic, so reordering or adding a handler doesn't require changing any existing handler's code.
- **Ubiquitous in real frameworks**: servlet filters, the **Spring Security filter chain** (authentication, authorization, CSRF checks, etc., each a link in a chain — Spring Boot Phase 7), generic middleware pipelines, and layered exception handlers are all Chain of Responsibility.

### Worked Example

```java
// From BehavioralPatterns.java
static abstract class Handler {
    protected Handler next;
    abstract String handle(String request);
    static Handler chain(Handler... handlers) {   // link them
        for (int i = 0; i < handlers.length - 1; i++) handlers[i].next = handlers[i + 1];
        return handlers[0];
    }
    String forward(String r) { return next != null ? next.handle(r) : "processed"; }
}
static class AuthHandler extends Handler {
    String handle(String r) { return r.contains("unauthed") ? "rejected at auth" : forward(r); }
}
static class ValidationHandler extends Handler {
    String handle(String r) { return r.contains("valid") ? forward(r) : "rejected at validation"; }
}
static class BusinessHandler extends Handler {
    String handle(String r) { return forward(r); }   // last -> "processed"
}

// usage:
var chain = Handler.chain(new AuthHandler(), new ValidationHandler(), new BusinessHandler());
assert chain.handle("valid-authed-request").equals("processed");
assert chain.handle("bad").equals("rejected at validation");
```

In this example, `Handler.chain(...)` links three handlers in order: `AuthHandler → ValidationHandler → BusinessHandler`. Calling `chain.handle("valid-authed-request")` starts at `AuthHandler`: since the string doesn't contain `"unauthed"`, it forwards to `ValidationHandler`; since the string *does* contain `"valid"`, that forwards again to `BusinessHandler`, which has no further rejection logic and simply forwards to `"processed"` (there is no `next` after it). Calling `chain.handle("bad")` also passes `AuthHandler` (no `"unauthed"` substring), but `"bad"` doesn't contain `"valid"`, so `ValidationHandler` rejects it directly with `"rejected at validation"` — the request never reaches `BusinessHandler` at all.

### Why It's Useful
Chain of Responsibility is the pattern behind nearly every real request-processing pipeline you'll touch professionally — authentication, then authorization, then validation, then business logic, each concern isolated in its own handler and freely reorderable/extensible without touching the others' code. It's a close cousin of Decorator structurally (both wrap/link), but its *intent* is "one of these should handle it, and possibly reject early," not "every layer adds behavior unconditionally."

---

## Other Behavioral Patterns Worth Knowing

- **Iterator** — provides sequential access to a collection's elements without exposing its internal structure. Java bakes this in directly via the `Iterator`/`Iterable` interfaces and the enhanced `for` loop — you rarely hand-implement it, but you use it in essentially every loop over a collection.
- **Mediator** — centralizes complex communication between a set of objects in one mediator object, so those objects don't need direct references to each other. Reduces a potential many-to-many web of dependencies down to a many-to-one relationship with the mediator.
- **Memento** — captures and externally stores an object's internal state so it can be restored later, without violating the object's encapsulation (the memento itself is opaque to everyone except the object that created it). The classic mechanism behind undo/redo snapshots and "save game" functionality.
- **Visitor** — lets you add new operations over a fixed set of classes without modifying those classes, by having each element `accept` a visitor object that has one method per concrete element type (a form of double dispatch: which visitor method runs depends on *both* the element's runtime type and the visitor's runtime type). Trade-off: adding a new *operation* is easy (just write a new Visitor); adding a new *element type* is hard (every existing Visitor implementation must be updated with a new method). Pairs naturally with Java's sealed types and pattern-matching `switch` (Java Phase 2.6), which give you a compiler-checked, Visitor-like exhaustiveness guarantee with far less boilerplate.

---

## Comparison: Strategy vs. Template Method

Both patterns let an algorithm vary, but through opposite mechanisms — a frequent, precise interview distinction.

| | Mechanism | Flexibility | When it shines |
|---|---|---|---|
| **Strategy** | Composition — inject a strategy object/lambda | Swappable at runtime, per-instance | The algorithm can vary freely and often does (discount rules, comparators) |
| **Template Method** | Inheritance — override specific steps | Fixed at compile time, per-subclass | The overall sequence is genuinely fixed; only specific steps vary |

Modern Java code leans heavily toward Strategy precisely because functional interfaces make it nearly free — no subclass, no `abstract` method, just a lambda passed to a constructor or method.

## Perspective
Behavioral patterns are mostly about **deciding who is decoupled from what**: Strategy decouples the client from *which algorithm* runs; Observer decouples the event source from *who reacts*; Command decouples the invoker from *what action* actually happens (and defers it); Chain of Responsibility decouples the sender from *which handler* eventually processes the request; State decouples "what behavior runs now" from a sprawling conditional, tying it instead to an object's current phase. Recognizing these in frameworks you use daily — filters are Chain of Responsibility, listeners are Observer, `*Template` classes are Template Method, comparators are Strategy — turns library "magic" into ordinary, nameable structure, and naming the pattern in a design discussion communicates your intent instantly to anyone else who knows it.
