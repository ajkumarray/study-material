<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · oop ➡](../phase-2-oop/NOTES.md)
<!-- /nav -->

# Phase 1 — Language Basics: Notes

*Source: `lesson-1-1` through `lesson-1-5`.*

## 1.1 — How Java runs: JDK, JRE, JVM, bytecode, JIT

Java code doesn't run directly on your CPU. It's compiled to an intermediate, CPU-neutral instruction set called **bytecode**, which a **Java Virtual Machine (JVM)** interprets and progressively compiles to native machine code as the program runs. This two-stage pipeline (compile once, run anywhere a JVM exists) is the mechanism behind Java's "write once, run anywhere" promise.

### Key Concepts

- **JVM (Java Virtual Machine)**: the runtime engine that loads, verifies, and executes `.class` bytecode. It is platform-*specific* (a Windows JVM binary differs from a Linux one) — and that's precisely what lets *your compiled code* be platform-*independent*: any machine with a matching JVM runs the same `.class`/`.jar` file unchanged.
- **JRE (Java Runtime Environment)**: JVM + the standard class library (`java.lang`, `java.util`, …) — everything needed to *run* a Java program, but no compiler.
- **JDK (Java Development Kit)**: JRE + development tools — `javac` (compiler), `jar` (packager), `javadoc`, `jshell` (REPL), a debugger. What you install to *write* Java. The containment is `JDK ⊃ JRE ⊃ JVM`.
- **Since Java 11**, there's no separate JRE download — the JDK ships one runtime, and apps can produce a trimmed custom runtime with `jlink` (only the modules they actually use).
- **Bytecode**: the compiled `.class` format — a stack-based instruction set, not machine code. Portable and verifiable.
- **Class loading is lazy**: a class loads on first use, not all at program start, via a hierarchy of class loaders (bootstrap → platform → application).
- **Bytecode verification**: before running any class, the JVM proves the bytecode is well-formed and type-safe (no stack underflow, no jumping into the middle of an instruction, no forging a reference into an int). This is a security boundary — it's why bytecode from an untrusted source (e.g. downloaded over a network, historically applets) can't corrupt the JVM's internal state.
- **Interpretation + JIT (Just-In-Time compilation)**: execution starts *interpreted* (bytecode read and executed instruction-by-instruction — slow but instant-start). The JVM profiles which methods run often ("hot" methods) and compiles those to native machine code on the fly. HotSpot (Oracle/OpenJDK's JVM) uses **tiered compilation**: the C1 "client" compiler kicks in fast with light optimization, then the C2 "server" compiler recompiles the hottest code paths with aggressive optimizations (method inlining, escape analysis, loop unrolling). This is why long-running Java processes approach native (C-like) speed *after a warm-up period* — and why the first request to a freshly started Java server is often slow.

### Worked Example

```java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
        if (args.length > 0) {
            System.out.println("Hello, " + args[0] + "!");
        } else {
            System.out.println("(Pass your name as an argument to get a personal greeting.)");
        }
    }
}
```

```
$ javac HelloWorld.java      # compiles to HelloWorld.class (bytecode)
$ java HelloWorld Ajay
Hello, World!
Hello, Ajay!
```

Running `javac HelloWorld.java` produces `HelloWorld.class`. Running `java HelloWorld Ajay` starts a JVM, which class-loads `HelloWorld`, verifies its bytecode, then calls `main`, passing `["Ajay"]` as `args`. `args.length > 0` is `true`, so the second branch runs. Since Java 11, `java HelloWorld.java` (no explicit `javac` step) compiles and runs in one command for quick scripts — the class file is generated in memory and discarded.

You can inspect the compiled bytecode yourself with `javap -c HelloWorld` — worth doing once to see that `System.out.println(...)` really does compile to `getstatic`/`invokevirtual` instructions operating on a stack, demystifying "bytecode" as a concept.

### `public static void main(String[] args)`

- **`public`**: the JVM launcher is "outside" the class, so it needs `main` to be visible.
- **`static`**: the JVM calls `main` before any object of the class exists — there's no instance to call an instance method on, and no reasonable way for the JVM to guess which constructor to use.
- **`void`**: `main` returns nothing to the caller; the process's exit status is set explicitly via `System.exit(code)` (0 = success by convention), not via a return value.
- **`String[] args`**: command-line arguments after the class name, e.g. `java HelloWorld Ajay 30` gives `args = {"Ajay", "30"}` (both are Strings — parse them yourself with `Integer.parseInt` etc.).
- **One public class per `.java` file**, and it must be named exactly like the file (`HelloWorld` → `HelloWorld.java`). A file may contain other, package-private top-level classes.
- `main` can be **overloaded** (e.g. `main(String[] args, int x)`) — those overloads compile fine but the JVM launcher only ever calls the exact `public static void main(String[])` signature.
- A class **without `main`** compiles but can't be launched directly with `java ClassName` (it can still be used as a library class, or run via JUnit, etc.).

### Why It's Useful

Understanding the compile → bytecode → JIT pipeline explains real-world Java behavior you'll be asked about in interviews and hit in production: why Java server startup/first-request latency is worse than a native binary's, why long-running services (the common case for Java) get *faster* over their lifetime as JIT kicks in, why a corrupted or hand-crafted `.class` file is safely rejected at class-load time instead of crashing the JVM, and why the exact same `.jar` runs unmodified on a developer's laptop and in a Linux container in production.

### Summary / Key Takeaways

- `JDK ⊃ JRE ⊃ JVM`: JVM runs bytecode, JRE adds the standard library, JDK adds dev tools.
- Java is compiled (to bytecode) *and* interpreted/JIT-compiled (at runtime) — not purely one or the other.
- Class loading is lazy; bytecode is verified before it runs, which is a security guarantee.
- `main`'s signature (`public static void main(String[] args)`) is fixed because the JVM calls it with no object and no return value expected.

## 1.2 — Variables, primitive types, and casting

A **variable** is a named storage location with a declared type. Java is **statically typed**: every variable's type is known and checked at compile time, which rejects an entire category of bugs (assigning a `String` where an `int` is expected, calling a method that doesn't exist on a type) before the program ever runs.

### The 8 Primitive Types

A **primitive** is a raw value stored directly in memory — not an object, no header, no heap allocation, no method table. Java has exactly eight:

| type | bits | range / notes | literal suffix |
|---|---|---|---|
| `byte` | 8 | −128 … 127 | |
| `short` | 16 | −32,768 … 32,767 | |
| `int` | 32 | ≈ ±2.1 × 10⁹ | **default** for whole-number literals |
| `long` | 64 | ≈ ±9.2 × 10¹⁸ | needs `L` suffix (`9_000_000_000L`) |
| `float` | 32 | IEEE 754, ~7 significant decimal digits | needs `f` suffix (`3.14f`) |
| `double` | 64 | IEEE 754, ~15 significant decimal digits | **default** for decimal literals |
| `char` | 16 | 0 … 65,535 (a UTF-16 code *unit*, unsigned) | single quotes: `'A'` |
| `boolean` | JVM-dependent size | `true` / `false` only — no numeric conversion | |

### Key Concepts

- **Why primitives exist at all in an OO language**: performance. An `int` is 4 raw bytes; an `Integer` object adds an object header (~12–16 bytes), lives on the heap, needs garbage collection, and defeats CPU cache locality when you have millions of them (e.g. in an array). Java trades OO purity for speed here — primitives are the deliberate escape hatch. Wrapper classes (`Integer`, `Double`, `Character`, `Boolean`, …) exist to let primitives participate where only objects are allowed (generics, collections) via **autoboxing/unboxing**.
- **`long` needs an `L` suffix** for literals that overflow `int`: `9_000_000_000L` — without the suffix, `9000000000` is parsed as an `int` literal first and is a compile error (too big for `int`) even though it's being assigned to a `long`.
- **Underscores in numeric literals** (`2_147_483_647`) are purely for human readability — the compiler strips them.
- **`String` is not a primitive** — it's a reference type (a class). This is why it's capitalized like other classes, while all eight primitive type names are lowercase.
- **Default values**: instance and static fields you don't initialize get a zero-ish default (`0`, `0.0`, `false`, `'\\u0000'`, `null` for references). **Local variables get no default** — the compiler enforces "definite assignment": reading a local before assigning it is a *compile error*, not a runtime surprise.

### Worked Example: declaring and printing primitives

```java
byte  aByte  = 127;
short aShort = 32_000;
int   anInt  = 2_147_483_647;
long  aLong  = 9_000_000_000L;      // L suffix required — too big for int
float  aFloat  = 3.14f;             // f suffix required — double is the default
double aDouble = 3.141592653589793;
char aChar = 'A';                   // secretly a number (Unicode code point)
boolean isLearning = true;

System.out.println(aByte);    // 127
System.out.println(aLong);    // 9000000000
System.out.println(aChar);    // A
```

Each variable's type is fixed forever at declaration. `aLong` prints as a plain number, not scientific notation — `long` is an exact integer type. Removing the `L` suffix from `9_000_000_000L` would fail to compile, because Java parses that literal *as an `int`* before considering the assignment target, and `9_000_000_000` overflows 32 bits.

### Casting: widening vs narrowing

```java
int small = 42;
long big = small;          // widening: implicit, always safe
double bigger = small;     // widening: implicit, always safe

double pi = 3.99;
int truncated = (int) pi;  // narrowing: explicit cast REQUIRED
System.out.println(truncated);        // 3  — truncated toward zero, NOT rounded
System.out.println((int) -3.7);       // -3 — also truncates toward zero
```

- **Widening** (`byte→short→int→long→float→double`) happens implicitly — no magnitude can be lost, so the compiler allows it silently.
- **Narrowing** (the reverse direction) requires an explicit cast — you're telling the compiler "I accept the risk of losing data" — and it **truncates toward zero**, it does not round: `(int) 3.99` is `3`, `(int) -3.7` is `-3` (not `-4`).
- **Subtlety**: `long → double` is classified as "widening" (compiles without a cast) yet can lose *precision*, because a `double`'s mantissa is only 53 bits — a `long` can represent integers a `double` cannot represent exactly. Widening guarantees no *magnitude* loss, not no *precision* loss.

### Integer Overflow

```java
int maxInt = Integer.MAX_VALUE;   // 2147483647
System.out.println(maxInt + 1);   // -2147483648 — wraps around silently, no exception
```

Java integers use **two's complement** representation, where the highest bit carries negative weight. Overflow wraps around silently rather than throwing — `Integer.MAX_VALUE + 1` becomes `Integer.MIN_VALUE`. This is a classic bug source in loop counters and running sums. To fail loudly instead, use `Math.addExact`, `Math.multiplyExact`, etc. (they throw `ArithmeticException` on overflow), or switch to `long`/`java.math.BigInteger` for a bigger range. A famous related trap: `Math.abs(Integer.MIN_VALUE)` returns `Integer.MIN_VALUE` itself (still negative!) — `+2147483648` has no representation in a 32-bit `int`.

### Floating-Point Gotchas

```java
System.out.println(0.1 + 0.2);   // 0.30000000000000004 — NOT 0.3
```

`double` and `float` store values in IEEE 754 binary floating-point. Just as decimal cannot represent `1/3` exactly, binary cannot represent most decimal fractions (like `0.1`) exactly — the stored value is the closest binary approximation, and errors compound across arithmetic. **Never use `double`/`float` for money.** Use `java.math.BigDecimal` (constructed **from a `String`**, e.g. `new BigDecimal("0.1")` — constructing it `new BigDecimal(0.1)` from the primitive double already carries the binary imprecision into the `BigDecimal`), or store integer minor units (cents) in a `long`.

Related special values: `1.0 / 0.0` is `Infinity` (floating-point division by zero does **not** throw); `0.0 / 0.0` is `NaN` ("Not a Number") — and `NaN` is the one value that is never equal to itself (`Double.NaN == Double.NaN` is `false`); test with `Double.isNaN(x)`. Contrast with **integer** division by zero, which *does* throw `ArithmeticException`.

### `char` Is a Number

```java
char letter = 'A';
char next = (char) (letter + 1);
System.out.println(next);               // B
System.out.println((int) letter);       // 65 — 'A' is Unicode code point 65
```

`char` holds an unsigned 16-bit UTF-16 code *unit*. Arithmetic works directly on it (`'A' + 1` is `66`, an `int`), so getting back a `char` needs an explicit cast. Because a `char` is only 16 bits, characters outside the Basic Multilingual Plane (many emoji, some scripts) need **two** `char`s — a "surrogate pair" — which is why `String.length()` can differ from the human-perceived character count for such text.

### Operators

```java
int a = 17, b = 5;
System.out.println(a + b);     // 22
System.out.println(a / b);     // 3   — int / int drops the remainder!
System.out.println(a % b);     // 2   — remainder ("modulo")
System.out.println(a / 5.0);   // 3.4 — promote one side to double for real division
```

- **Integer division truncates**: `17 / 5` is `3`, not `3.4` — the fractional part is simply dropped. To get a real quotient, make at least one operand a `double` (`a / 5.0`).
- **`%` (modulo)** gives the remainder; its sign follows the *dividend* — `-17 % 5` is `-2`, not `3`.
- **Compound assignment / increment**: `counter += 5` means `counter = counter + 5`; `counter++` (post-increment) evaluates to the *old* value then increments; `++counter` (pre-increment) increments first then evaluates to the *new* value. As standalone statements they behave identically; the difference only matters when the expression's *value* is used (`arr[i++]` vs `arr[++i]`).
- **Comparison operators** (`>`, `<`, `>=`, `<=`, `==`, `!=`) produce `boolean`.
- **Short-circuit logical operators `&&` / `||`**: the right operand is *not evaluated* if the left already determines the result. This enables the extremely common null-guard idiom:

```java
String s = null;
if (s != null && s.length() > 0) { ... }   // safe: length() never called when s is null
```

Using `&` or `|` instead always evaluates both sides — `s != null & s.length() > 0` would throw `NullPointerException` when `s` is null, because `s.length()` still runs. (`&`/`|` also double as bitwise AND/OR on integer types.)

- **Ternary operator** `condition ? ifTrue : ifFalse` is a one-line if/else that *produces a value*: `String grade = score >= 80 ? "distinction" : "pass";`.

### `var` — Local Type Inference (Java 10+)

```java
var message = "var infers the type at COMPILE time";   // inferred: String
var count = 42;                                          // inferred: int
```

`var` does **not** make Java dynamically typed. The compiler infers the concrete type from the initializer expression at compile time, and that type is then **locked forever** for that variable — `var x = "hi"; x = 5;` fails to compile (can't assign an `int` to a `String`-typed variable). `var` is legal only for **local variables with an initializer** — never for fields, method parameters, or return types. It exists purely to reduce redundant boilerplate (`Map<String, List<Order>> ordersByCustomer = new HashMap<String, List<Order>>()` → `var ordersByCustomer = new HashMap<String, List<Order>>();`), not to change type semantics.

### Why It's Useful

Type sizes, overflow behavior, and floating-point precision are not academic — they're the root cause of real production bugs: silent numeric overflow in counters or IDs, financial calculations gone wrong from using `double` instead of `BigDecimal`, and off-by-tiny-amount bugs in comparisons (`if (x == 0.3)` almost never matches after floating-point arithmetic). Short-circuit evaluation is the standard defensive-null-check idiom used throughout production Java. `var` is a routine modern-Java readability tool for verbose generic types.

### Summary / Key Takeaways

- Java has exactly 8 primitives; know their sizes and which two (`int`, `double`) are the defaults for numeric literals.
- Widening casts are implicit and safe; narrowing casts are explicit and **truncate**, not round.
- Integer overflow wraps silently (two's complement) — it does not throw.
- `0.1 + 0.2 != 0.3` because binary floating-point can't represent most decimal fractions exactly — never use `double`/`float` for money; use `BigDecimal` or integer minor units.
- `&&`/`||` short-circuit (enables safe null-guards); `&`/`|` always evaluate both sides.
- `var` is compile-time type inference for initialized locals only — Java stays statically typed.

## 1.3 — Control flow

Control flow determines **which** code runs and **how many times**.

### `if` / `else if` / `else`

```java
int score = 85;
if (score >= 90) {
    System.out.println("Grade A");
} else if (score >= 75) {
    System.out.println("Grade B");     // first true branch wins — this one
} else if (score >= 60) {
    System.out.println("Grade C");
} else {
    System.out.println("Fail");
}
```

The first branch whose condition is `true` runs; every later branch, even if also true, is skipped. **Always brace every branch, even one-liners.** Apple's infamous "goto fail" TLS bug came from an unbraced `if` that let a second, visually-indented-but-not-actually-nested line always execute — a bug class braces eliminate entirely.

### Classic `switch` Statement (fall-through)

```java
int day = 6; // 1 = Monday ... 7 = Sunday
switch (day) {
    case 6:                     // no break here...
    case 7:                     // ...so case 6 FALLS THROUGH into case 7's code
        System.out.println("Weekend!");
        break;                  // break exits the switch entirely
    default:
        System.out.println("Weekday");
        break;
}
```

- **Fall-through** is the classic `switch` statement's defining (and most bug-prone) behavior: once a case matches, execution continues into the *next* case's code regardless of whether its label matches, until a `break` (or the switch ends). Forgetting `break` is the #1 switch bug. Deliberate fall-through — like grouping case `6` and `7` above to share one block — is legitimate but should carry a comment, since it looks identical to a mistake.
- **Switchable types**: `byte`/`short`/`char`/`int` and their wrapper classes, `String` (since Java 7 — compiles to a hashCode-then-equals check chain), enums, and (Java 21+) type patterns. **Not** `long`, `float`, `double`, or `boolean`.

### `switch` Expressions (Java 14+)

```java
String dayType = switch (day) {
    case 1, 2, 3, 4, 5 -> "Weekday";     // multiple labels on one arrow case
    case 6, 7          -> "Weekend";
    default            -> "Invalid day";
};
System.out.println(dayType);              // Weekend

String feeling = switch (day) {
    case 1 -> "Ugh, Monday";
    case 5 -> {
        String base = "Friday";
        yield base + "!!!";               // yield = "this block's value"
    }
    default -> "just a day";
};
```

Arrow (`->`) cases in a switch *expression* **do not fall through** and need no `break`; a case can list several labels separated by commas; and the whole construct **evaluates to a value** you can assign or return. A multi-statement branch uses a `{ ... yield value; }` block, where `yield` is the expression-switch equivalent of `return`. Prefer switch expressions over the classic statement form in new code — fall-through bugs become structurally impossible.

### Loops

| loop | use when | guaranteed runs |
|---|---|---|
| `for` | the count/range is known up front | 0+ |
| `for-each` (enhanced for) | walking every element of an array/collection | 0+ |
| `while` | repeat while some condition holds, unknown count | 0+ |
| `do-while` | the body must run at least once before you can decide to repeat (prompt/retry) | **1+** |

```java
int countdown = 3;
while (countdown > 0) {           // condition checked BEFORE each iteration
    System.out.println(countdown);
    countdown--;                  // omit this and the loop never terminates
}

int attempts = 0;
do {
    attempts++;
    System.out.println("attempt #" + attempts);
} while (attempts < 1);           // condition checked AFTER — body always runs once

for (int i = 1; i <= 5; i++) {    // init; condition; update — condition checked before each pass
    System.out.print(i + " ");    // 1 2 3 4 5
}

String[] stack = {"Java", "Spring Boot", "Docker", "Kubernetes"};
for (String tech : stack) {       // "for each tech in stack"
    System.out.println("learning: " + tech);
}
```

`while` checks its condition *before* the body, so it can run zero times. `do-while` checks *after*, so the body always runs at least once — the natural shape for "do the thing, then decide whether to retry" (password prompts, connection retries). `for-each` is the cleanest way to visit every element of an array or collection, but you lose the index and cannot assign into the array slot through the loop variable, and you cannot iterate backwards or skip — reach for an indexed `for` loop in those cases.

### `break`, `continue`, and Labeled `break`

```java
for (int i = 1; i <= 10; i++) {
    if (i % 2 == 0) continue;    // skip the rest of THIS iteration only
    if (i > 7) break;            // exit the WHOLE loop immediately
    System.out.print(i + " ");   // 1 3 5 7
}

search:
for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
        if (row == 1 && col == 2) {
            System.out.println("found it at (" + row + "," + col + ")");
            break search;         // exits BOTH loops at once
        }
    }
}
```

`continue` skips to the next iteration of the *innermost enclosing* loop; plain `break` exits only the *innermost* loop. To exit an outer loop from inside a nested one, label the outer loop (`search:`) and use `break search;` (or `continue search;`). A cleaner alternative that most style guides prefer is extracting the nested loops into their own method and using `return` for early exit.

### `switch` Performance Note

Under the hood, the compiler emits a `tableswitch` bytecode instruction for dense, contiguous `int` cases (an O(1) jump table lookup) or a `lookupswitch` for sparse cases (effectively a binary search). This is a genuine performance characteristic, but for a handful of branches it's noise — choose `switch` vs `if/else` for readability, and mention the bytecode fact only if asked to go deeper.

### FizzBuzz — Everything Combined

```java
for (int i = 1; i <= 15; i++) {
    if (i % 15 == 0)      System.out.println("FizzBuzz");
    else if (i % 3 == 0)  System.out.println("Fizz");
    else if (i % 5 == 0)  System.out.println("Buzz");
    else                  System.out.println(i);
}
```

Branch order is part of the logic here: checking `% 3` or `% 5` *before* `% 15` would mean multiples of 15 get swallowed by the first matching (but less specific) branch and never reach the `FizzBuzz` case. Test the most specific condition first.

### Why It's Useful

Control flow bugs (unbraced `if`, fall-through `switch`, off-by-one loop bounds, wrong branch order) are some of the most common real-world defects — they compile fine and only misbehave on specific inputs. Switch expressions were added specifically because fall-through caused enough production bugs that the language added a fall-through-proof alternative. Loop choice communicates intent to readers: seeing `do-while` tells a reviewer "this always runs once" without reading the body.

### Summary / Key Takeaways

- `if/else if` runs the first true branch only; always brace every branch.
- Classic `switch` falls through without `break`; switch *expressions* (`->`, Java 14+) don't fall through and return a value via `yield`.
- `while` may run zero times; `do-while` always runs at least once.
- `continue` skips an iteration; `break` exits the innermost loop; labeled `break`/`continue` reach outer loops.
- In cascading conditions, order matters — check the most specific case first.

## 1.4 — Methods, pass-by-value, overloading, recursion

A **method** is a named, reusable block of code. Methods give you reuse (DRY — Don't Repeat Yourself), self-documenting names (`calculateTax(income)` explains itself at the call site), and testable units (small pieces you can verify in isolation). Terminology: the *definition* declares **parameters**; the *call site* supplies **arguments**.

```java
static void greet(String name) {                 // parameter: name
    System.out.println("Hello, " + name + "!");
}
static int add(int a, int b) { return a + b; }

greet("Ajay");            // argument: "Ajay"     -> void: does something, returns nothing
int sum = add(17, 25);    // arguments: 17, 25    -> returns a value: 42
```

### Guard Clauses

```java
static String describe(int n) {
    if (n < 0) {
        return "negative — can't describe";   // handle the edge case, bail out early
    }
    return "a fine number: " + n;              // happy path stays flat, unindented
}
```

**Guard clauses** — early returns for edge cases at the top of a method — keep the "happy path" readable at the lowest indentation level, instead of nesting it inside an `if (valid) { ... }` block.

### The Call Stack

Every method call pushes a **stack frame** onto the calling thread's call stack, holding that call's parameters, local variables, and the return address to jump back to when the method finishes. Each thread has its own private call stack (~512 KB–1 MB by default, JVM-dependent); the **heap** (where objects live) is shared across all threads. Deep or unbounded recursion exhausts the stack and throws `StackOverflowError` — note this is an `Error`, not an `Exception` (it signals a serious, generally unrecoverable condition).

### Pass-by-Value (the most-asked Java fundamentals question)

Java is **always pass-by-value** — a method always receives a *copy* of what you pass. What differs is *what* gets copied:

- For a **primitive** argument, the value itself is copied. The callee can't affect the caller's variable.
- For an **object** argument, the **reference** (essentially a pointer) is copied. Both the caller's variable and the callee's parameter now point at the *same* object in the heap — so the callee *can* mutate that object's contents (visible to the caller), but *reassigning* the parameter to point somewhere else only repoints the callee's local copy of the reference; the caller's variable is untouched.

```java
static void tryToChange(int value) {
    value = 999;                     // changes only this method's local copy
}
static void modifyContents(int[] arr) {
    arr[0] = 999;                    // mutates the SHARED array object — caller sees it
}
static void reassignParameter(int[] arr) {
    arr = new int[]{7, 7, 7};        // repoints the LOCAL copy of the reference — invisible outside
}

int x = 10;
tryToChange(x);
System.out.println(x);                      // 10 — unchanged

int[] numbers = {10, 20, 30};
modifyContents(numbers);
System.out.println(numbers[0]);             // 999 — changed! same array object, mutated in place

reassignParameter(numbers);
System.out.println(numbers[0]);             // still 999 — reassigning inside the method did nothing outside
```

**Mental model**: you hand someone a *photocopy* of your house key. They can rearrange your furniture (mutate the object through the shared reference). They cannot make you move to a different house (reassigning their copy of the key changes nothing about which house *you* point to). One direct corollary: **there is no way to write a working `swap(int a, int b)` in Java** — both `a` and `b` are local copies, and no reassignment inside the method escapes it. (You'd need to swap elements of an array, or return a pair.)

### Method Signature and Overloading

A method's **signature** is its **name + parameter types** (in order). The **return type is not part of the signature** — two methods differing only in return type do not compile as overloads. Parameter names, modifiers (`public`, `static`, …), and `throws` clauses are likewise not part of the signature.

```java
static double area(double radius) { return Math.PI * radius * radius; }   // circle
static double area(double width, double height) { return width * height; } // rectangle
static int    area(int side) { return side * side; }                      // square

area(5.0);        // -> circle overload (double)
area(4.0, 6.0);    // -> rectangle overload (two doubles)
area(3);           // -> square overload (int)
```

**Overload resolution happens at compile time**, based on the static (declared) types of the arguments, with this preference order: **exact type match → widening conversion (e.g. `int`→`long`) → autoboxing (`int`→`Integer`) → varargs** (varargs is the resolution method of last resort). Passing `null` picks the *most specific* applicable reference-type overload; if two unrelated overloads (say `String` and `Integer`) are both applicable to `null`, the call is ambiguous and fails to compile.

### Varargs

```java
static int max(int... values) {          // arrives inside the method as an int[]
    if (values.length == 0) return Integer.MIN_VALUE;
    int best = values[0];
    for (int v : values) if (v > best) best = v;
    return best;
}

max();               // -> Integer.MIN_VALUE (zero arguments — values.length == 0)
max(3);               // -> 3
max(3, 9, 4, 7);       // -> 9
```

`type... name` lets a method accept zero or more arguments, which arrive as an array of that type inside the method body. Rules: **at most one varargs parameter per method**, and it **must be the last parameter**. If an exact or non-varargs overload also matches a call, that overload wins — varargs is only used when nothing more specific applies.

### Recursion

```java
static long factorial(int n) {
    if (n <= 1) {                // BASE CASE — stops the recursion
        return 1;
    }
    return n * factorial(n - 1); // RECURSIVE CASE — a smaller version of the same problem
}
factorial(5);   // 5 * factorial(4) * ... -> 120
```

Every recursive method needs a **base case** (when to stop) and a **recursive case** that makes real progress toward that base case (a strictly smaller sub-problem). Missing or unreachable base cases cause unbounded recursion and `StackOverflowError`. **Java has no tail-call optimization** — even a "tail-recursive" method (where the recursive call is the very last operation) still grows the call stack one frame per call, unlike some other languages/compilers. Convert unbounded-depth recursion to an explicit loop when the input size isn't safely bounded. (Recursion becomes central in the DSA track — trees, backtracking, divide-and-conquer algorithms all lean on it.)

### Why It's Useful

Pass-by-value semantics explain a huge share of "why did my object change but my `int` didn't" bugs, and are one of the single most commonly asked Java interview questions — interviewers use it to check whether a candidate actually understands references vs. values, not just memorized vocabulary. Overload resolution rules explain confusing compiler errors ("ambiguous method call") and why `null` sometimes needs an explicit cast (`(String) null`) to disambiguate. Guard clauses and the call-stack model are foundational to writing (and debugging) any non-trivial method, recursive or not.

### Summary / Key Takeaways

- Java is always pass-by-value; for objects, the *reference* is copied, which is why mutation is visible to the caller but reassignment isn't.
- A method signature = name + parameter types; return type doesn't count, so overloads must differ in parameters.
- Overload resolution is a compile-time decision: exact match beats widening beats autoboxing beats varargs.
- Varargs (`type...`) is sugar for an array parameter; at most one, and it must be last.
- Every recursive method needs a base case and a shrinking recursive case; Java has no tail-call optimization, so unbounded recursion overflows the stack.

## 1.5 — Arrays and Strings

### Arrays

An **array** is a fixed-size, zero-indexed, homogeneous (single-type) container. Despite holding primitives or objects, an array is itself a real heap-allocated **object** in Java — it has a runtime class, can be assigned to an `Object` reference, and is returned by `getClass()` with a name like `[I` for `int[]`.

### Key Concepts

- **Creation**: `new int[5]` allocates a sized array filled with the type's default values (`0` for numeric types); `{2, 3, 5, 7, 11}` is an array literal, sized from the initializer.
- **`array.length` is a final field, not a method** — no parentheses. Contrast `String.length()` (a *method*) and `List.size()` (also a *method*) — a classic trivia trio that tests attention to detail.
- **Fixed size forever**: an array cannot grow or shrink after creation. "Resizing" means allocating a new, bigger array and copying — exactly what `Arrays.copyOf(original, newLength)` does, and precisely the mechanism `ArrayList` automates internally (Phase 3).
- **Out-of-bounds access compiles fine, throws at runtime**: `ArrayIndexOutOfBoundsException` — the JVM enforces bounds checking as a memory-safety guarantee (unlike, say, raw C, which would silently read adjacent memory).
- **`==` on arrays compares references, not contents** — two arrays with identical elements are still different objects unless they're literally the same reference. Use `Arrays.equals(a, b)` for content comparison (and `Arrays.deepEquals` for nested/2D arrays).
- **2D arrays are arrays of arrays**: each row is its own independently-sized array object, so rows can be "jagged" (different lengths) — `new int[3][]` then filling each row separately.
- **Toolbox** (`java.util.Arrays`): `toString`/`deepToString` (printing), `sort`, `equals`/`deepEquals`, `fill`, `copyOf`/`copyOfRange`, `binarySearch` (requires sorted input).

### Worked Example

```java
int[] scores = new int[5];                 // {0, 0, 0, 0, 0} — zero-filled default
int[] primes = {2, 3, 5, 7, 11};

scores[0] = 95;
System.out.println(scores.length);          // 5 — field, not a method call
System.out.println(Arrays.toString(scores)); // [95, 0, 0, 0, 0]

try {
    int boom = primes[5];                    // valid indices are 0..4
} catch (ArrayIndexOutOfBoundsException e) {
    System.out.println(e.getMessage());      // "Index 5 out of bounds for length 5"
}

int[] grown = Arrays.copyOf(primes, 8);      // new array, copies + pads with 0s
System.out.println(Arrays.toString(grown));  // [2, 3, 5, 7, 11, 0, 0, 0]

int[] a1 = {1, 2, 3};
int[] a2 = {1, 2, 3};
System.out.println(a1 == a2);                // false — different objects
System.out.println(Arrays.equals(a1, a2));   // true — same contents
```

`scores[5]` doesn't exist yet — `primes[5]` is out of bounds because a 5-element array has valid indices `0..4`; the exception message even reports the actual out-of-bounds index and array length. `a1 == a2` is `false` despite identical contents because array literals always allocate a fresh object; `Arrays.equals` is the content-aware comparison you almost always actually want.

### 2D Arrays

```java
int[][] grid = { {1, 2, 3}, {4, 5, 6} };
System.out.println(grid[1][2]);   // 6 — row 1, column 2

for (int[] row : grid) {          // each element of `grid` is itself an int[]
    System.out.println(Arrays.toString(row));
}
// [1, 2, 3]
// [4, 5, 6]
```

### Strings

A `String` is an **immutable** sequence of characters. No method ever changes an existing `String` object — every "modifying" method (`toUpperCase`, `replace`, `substring`, …) returns a **new** `String`, leaving the original untouched.

### Key Concepts

- **Immutability**: `tech.toUpperCase();` on its own line is a silent no-op — the returned uppercase String is discarded; you must capture it (`tech = tech.toUpperCase();`). This is a real, common bug shape, not just trivia.
- **Why immutable**: (1) **string pooling** is only safe if nobody can mutate a shared literal out from under other code; (2) **security** — once a path, URL, or class name string has been validated, nobody downstream can mutate it before use; (3) **thread safety** — an immutable object can be freely shared across threads with no locking; (4) **cached hash code** — a `String`'s `hashCode()` is computed once and cached, making Strings fast, stable `HashMap`/`HashSet` keys.
- **String pool / interning**: string literals are *interned* — the JVM keeps one shared object per distinct literal value (stored on the heap since Java 7, previously in PermGen). `new String("hello")` bypasses the pool and forces a distinct heap object. `.intern()` on any String returns the pooled instance for its content.
- **Compact Strings (Java 9+)**: a `String` containing only Latin-1 characters is internally stored using 1 byte per character instead of 2 — a memory optimization that's completely invisible at the API/semantic level.
- **`substring(begin, end)` is half-open**: `[begin, end)` — `end` is exclusive, so the result length is `end - begin`. This half-open convention (inclusive start, exclusive end) is used consistently across the Java standard library (`Arrays.copyOfRange`, stream ranges, etc.).
- **Toolbox**: `length()`, `charAt(i)`, `indexOf`, `contains`, `startsWith`/`endsWith`, `replace`, `strip()` (Unicode-aware trim — prefer over the older `trim()`), `split`, `String.join`, `formatted`, `repeat`, `isBlank`/`isEmpty`, `chars()` (an `IntStream` of code points).

### Worked Example: immutability, `==` vs `.equals()`

```java
String tech = "Java";
tech.toUpperCase();                       // return value discarded — a no-op bug!
System.out.println(tech);                 // Java — unchanged
String loud = tech.toUpperCase();         // capture it this time
System.out.println(loud);                 // JAVA

String lit1 = "hello";                    // pooled literal
String lit2 = "hello";                    // same pooled object as lit1
String made = new String("hello");        // forces a NEW heap object

System.out.println(lit1 == lit2);         // true  — same pooled object
System.out.println(lit1 == made);         // false — different objects
System.out.println(lit1.equals(made));    // true  — same CONTENT
```

`lit1 == lit2` is `true` **only because of string pooling** — both literals resolve to the exact same interned object. `new String("hello")` deliberately opts out of pooling, producing a distinct object with identical content, so `==` (reference equality) sees them as different while `.equals()` (content equality) correctly reports them equal. **Rule of thumb: always use `.equals()` to compare String content; reserve `==` for "is this literally the same object" checks.**

### `substring` and Common Operations

```java
String s = "  Learning Java 21  ";
System.out.println(s.strip());                      // "Learning Java 21"
System.out.println(s.indexOf("Java"));               // 12 (counting the leading spaces)
System.out.println(s.strip().substring(9, 13));      // "Java" — [9,13) is half-open

String csv = "java,spring,docker,kafka";
String[] parts = csv.split(",");
System.out.println(Arrays.toString(parts));           // [java, spring, docker, kafka]
System.out.println(String.join(" -> ", parts));        // java -> spring -> docker -> kafka
```

### StringBuilder and StringBuffer

Because `String` is immutable, concatenating in a loop with `+=` allocates a *brand-new* String and copies everything accumulated so far on every single pass — an O(n²) total cost for n appends. `StringBuilder` wraps a mutable, growable `char` buffer: `append` is amortized O(1), and you call `toString()` once at the end to get the final immutable `String`.

```java
StringBuilder sb = new StringBuilder();
for (String part : parts) {
    sb.append(part).append(" | ");
}
System.out.println(sb.toString());   // java | spring | docker | kafka |
```

Measured in the lesson (100,000 appends each way): `String +=` took **581 ms**; `StringBuilder.append` took **3 ms** — nearly two orders of magnitude faster, and the gap only widens as the loop count grows, since `+=` is quadratic and `StringBuilder` is linear.

| approach | mutability | thread safety | typical use |
|---|---|---|---|
| `String` | immutable | inherently safe (shared freely) | fixed text, keys, single-expression concatenation |
| `StringBuilder` | mutable | **not** synchronized | building strings in loops — the default choice |
| `StringBuffer` | mutable | synchronized (legacy) | essentially never the right choice in new code — the sync overhead buys nothing in typical single-threaded string building |

A single-expression concatenation like `a + b + c` is *not* a performance problem — since Java 9 the compiler emits efficient `invokedynamic`-based string concatenation code for it automatically; the O(n²) trap only shows up when concatenation happens **inside a loop**, because each iteration is a separate statement the compiler can't fuse together.

### Text Blocks (Java 15+)

```java
String json = """
        {
          "track": "java",
          "lesson": "1.5"
        }""";
```

Triple-quoted **text blocks** let you write multi-line string literals without `\n` and escaped quotes everywhere. The position of the *closing* `"""` delimiter controls how much common leading whitespace is stripped from every line (incidental indentation for the source code doesn't leak into the string's content).

### Why It's Useful

`==` vs `.equals()` confusion is one of the most common real Java bugs for developers coming from languages where `==` compares content — getting this wrong means a `Map` lookup or a conditional silently fails for logically-equal-but-different-object Strings. StringBuilder vs `+=` is a routine, measurable performance decision that shows up in any code building output in a loop (report generation, JSON building, log formatting). Array bounds checking and the fixed-size nature of arrays directly motivate why `ArrayList` exists and how it works internally (covered in Phase 3).

### Summary / Key Takeaways

- Arrays are fixed-size heap objects; `.length` is a field; out-of-bounds access throws `ArrayIndexOutOfBoundsException` at runtime, not compile time.
- `==` on arrays and Strings compares references; use `Arrays.equals`/`.equals()` for content comparison.
- Strings are immutable — every "modifying" method returns a new String; the string pool means literal `==` can be `true`, but `new String(...)` always forces a fresh, `==`-different object.
- Use `StringBuilder` for string building in loops (amortized O(1) append) — `+=` in a loop is O(n²).
- `substring` and most Java ranges are half-open: `[begin, end)`.

## Additional Follow-Up Points

- **Printing an array directly** (`System.out.println(scores)`) shows something like `[I@1b6d3586` — arrays don't override `toString()`, so you get the internal type descriptor (`[I` = `int[]`) plus the object's hash code in hex. Always use `Arrays.toString`/`Arrays.deepToString` to print contents.
- **`x++` vs `++x`** as *expressions*: `x++` (post-increment) yields the value *before* incrementing; `++x` (pre-increment) yields the value *after*. As standalone statements the net effect on `x` is identical either way; the distinction only matters when the expression's result is itself used (e.g. as an array index or in another expression).
