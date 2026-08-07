<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · oop ➡](../phase-2-oop/NOTES.md)
<!-- /nav -->

# Phase 1 — Language Basics: Notes

## 1.1 — Hello World & how Java runs

**The pipeline:**

```
HelloWorld.java --javac--> HelloWorld.class --java--> JVM: load -> verify -> interpret/JIT -> run
   (source)                 (bytecode)
```

- **Why bytecode?** Java compiles to a portable intermediate format, not machine code. Any machine with a JVM runs the same `.class`/`.jar` unchanged — "write once, run anywhere." The trade: an extra runtime layer, which JIT compilation largely erases.
- **JDK ⊃ JRE ⊃ JVM.** JVM executes bytecode; JRE = JVM + standard library; JDK = JRE + tools (`javac`, `jar`, `javadoc`, `jshell`, debugger). Since Java 11 there's no standalone JRE download — apps ship a trimmed runtime built with `jlink`.
- **What happens at `java HelloWorld`:**
  1. **Class loading** — the class loader finds `HelloWorld.class` on the classpath and loads it (lazily: classes load on first use, not all up front).
  2. **Bytecode verification** — the JVM proves the bytecode doesn't do anything illegal (stack underflow, type confusion, jumping into the middle of instructions). This is why bytecode from an untrusted source can't corrupt the JVM.
  3. **Interpretation + JIT** — code starts interpreted; the JVM profiles it, and *hot* methods get compiled to native machine code by the JIT (HotSpot's C1 "client" compiler quickly, then C2 "server" compiler with aggressive optimizations — tiered compilation). Long-running Java is close to C speed *after warm-up*.
- **Peek at bytecode yourself:** `javap -c HelloWorld` disassembles a class — worth doing once to demystify the whole thing.
- **One public class per file, named after the file.** Other package-private classes may share the file.
- **`public static void main(String[] args)`** — fixed entry signature. `static` because no object exists yet. Since Java 11, `java HelloWorld.java` compiles+runs in one step; Java 21 previews even terser forms.

## 1.2 — Variables, primitive types, operators

**The 8 primitives — sizes and ranges:**

| type | bits | range / values | literal notes |
|---|---|---|---|
| `byte` | 8 | −128 … 127 | |
| `short` | 16 | −32,768 … 32,767 | |
| `int` | 32 | ±~2.1 × 10⁹ | **default** for whole numbers |
| `long` | 64 | ±~9.2 × 10¹⁸ | needs `L` suffix |
| `float` | 32 | ~7 decimal digits | needs `f` suffix |
| `double` | 64 | ~15 decimal digits | **default** for decimals |
| `char` | 16 | 0 … 65,535 (Unicode code unit) | single quotes `'A'` |
| `boolean` | JVM-dependent | `true` / `false` | not 0/1 — no numeric conversion |

- **Why static typing:** the compiler knows every variable's type and rejects illegal mixes *before* the program runs — an entire bug class eliminated at compile time.
- **Two's complement** is how negative integers are stored: the top bit carries negative weight. This is *why* overflow wraps: `Integer.MAX_VALUE + 1` rolls over to `Integer.MIN_VALUE` with no error. Guard with `Math.addExact()` when overflow must fail loudly.
- **Casting:** widening (`int` → `long` → `double`) is implicit — no data loss possible. Narrowing needs an explicit cast and **truncates toward zero**: `(int) 3.99 == 3`, `(int) -3.7 == -3`. Note the subtle case: `long` → `double` is "widening" but can lose *precision* (doubles have only 53 bits of mantissa).
- **IEEE 754 floating point:** doubles store binary fractions, so most decimal fractions are approximations — `0.1 + 0.2 == 0.30000000000000004`. Errors compound. Money → `BigDecimal` (constructed from a String!) or integer cents in a `long`. Also meet `NaN` (`0.0/0.0`, never equal to anything including itself) and `Infinity` (`1.0/0.0` — floating-point division by zero doesn't throw; integer division by zero does).
- **`char` is a number** — `'A' + 1` is 66; cast back to get `'B'`. One `char` is a UTF-16 *code unit*: emoji and many scripts need two (a "surrogate pair"), which is why `String.length()` can differ from the human-perceived character count.
- **Integer division:** `17 / 5 == 3`; `%` gives the remainder (sign follows the *dividend*: `-17 % 5 == -2`). Promote one side to `double` for real division.
- **Short-circuit `&&`/`||`:** the right side isn't evaluated when the left decides — enables the null-guard idiom `s != null && s.length() > 0`. `&`/`|` always evaluate both (and double as bitwise ops on ints).
- **`var` (Java 10+):** local-variable type inference. Still statically typed — the type is locked at the declaration. Only for locals with initializers; not fields, not parameters, not return types.

## 1.3 — Control flow

**Branching:**
- `if`/`else if` chains: first true branch wins. Always brace, even one-liners — Apple's "goto fail" TLS bug came from an unbraced `if` swallowing a second indented line.
- **Classic `switch` statement falls through** unless each case ends with `break` — the #1 switch bug; deliberate fall-through (grouping cases) should carry a comment.
- **Switch expressions (Java 14+):** arrow `->` cases don't fall through, need no `break`, allow multiple labels (`case 6, 7 ->`), and *return a value*; multi-statement branches use `{ ... yield x; }`. Prefer them in new code.
- Switchable types: `int`-family + `char`, their wrappers, `String` (Java 7 — compiles to hashCode + equals checks), enums, and type patterns (Java 21). Not `long`, `float`, `double`, `boolean`.
- Under the hood the compiler emits `tableswitch` (dense cases → O(1) jump table) or `lookupswitch` (sparse cases → binary search). Choose switch for readability, not speed.

**Loops — decision table:**

| loop | use when | runs |
|---|---|---|
| `for` | count/range known up front | 0+ |
| `for-each` | walking a whole array/collection | 0+ |
| `while` | repeat while a condition holds | 0+ |
| `do-while` | body must run before you can decide (prompt/retry) | **1+** |

**Jumps:** `continue` skips to the next iteration; `break` exits the innermost loop; **labeled break** (`outer: ... break outer;`) exits nested loops — the cleaner alternative is extracting the nest into a method and using `return`.

**FizzBuzz moral:** branch order is logic — test the most specific condition (`% 15`) first or a broader one swallows it.

## 1.4 — Methods, parameters, overloading

**Why methods:** reuse (DRY), self-documenting names, testable units. *Parameters* in the definition; *arguments* at the call site.

**The call stack:** every call pushes a *stack frame* holding parameters, locals, and the return address. Deep/unbounded recursion overflows it → `StackOverflowError`. Each thread gets its own stack (~512KB–1MB by default); the heap is shared.

**Pass-by-value — the big one.** Java *always* copies the argument into the parameter. What's copied:
- primitive → the value. Callee can't touch the caller's variable.
- object → the **reference**. Both references point at one object, so the callee *can mutate the object* (caller sees it) but *reassigning* the parameter only repoints the callee's copy.

Mental model: a photocopy of your house key — they can rearrange your furniture, they can't move you to a new house. Corollary: there is no way to write a `swap(a, b)` for two ints in Java.

**Signature & overloading:** signature = name + parameter types (order matters). Not part of it: return type, parameter names, modifiers, `throws`. Overloads are resolved at **compile time**; preference order: exact match → widening (`int`→`long`) → autoboxing (`int`→`Integer`) → varargs. `f(null)` picks the *most specific* applicable reference type — ambiguity between unrelated types (e.g., `String` vs `Integer`) is a compile error.

**Varargs:** `int... values` arrives as an `int[]`; max one per method, last position. A non-varargs overload that matches wins — varargs is the resolution of last resort.

**Style:** guard clauses (early returns for edge cases) keep the happy path flat and readable.

**Recursion:** base case + smaller-problem recursive case. Java has **no tail-call optimization** — a tail-recursive loop still grows the stack; convert to iteration when depth is unbounded. (Recursion returns in force in the DSA track: trees, backtracking, divide & conquer.)

## 1.5 — Arrays and Strings

**Arrays:**
- Fixed-size, homogeneous, 0-indexed, and real heap **objects** (assignable to `Object`, have a runtime class like `[I`).
- `arr.length` is a final **field**; contrast `String.length()` (method) and `List.size()` (method) — the classic trivia trio.
- Fresh arrays are zero-filled (`0`, `false`, `null`) — same defaults as object fields.
- Out-of-bounds access compiles fine, throws `ArrayIndexOutOfBoundsException` at runtime (the index check is a JVM safety guarantee — C would happily read garbage).
- "Resizing" = `Arrays.copyOf` into a new array — precisely what `ArrayList` automates with amortized growth (Phase 3).
- 2D arrays are arrays *of arrays* — rows are independent objects and may be jagged (`new int[3][]` with rows of different lengths).
- Toolbox: `Arrays.toString` / `deepToString`, `sort`, `equals`, `fill`, `copyOf`, `copyOfRange`, `binarySearch` (sorted input only).

**Strings:**
- **Immutable.** Every "modifying" method returns a new String; `s.toUpperCase();` alone is a no-op (and a real-bug shape). Why immutable: safe pooling, thread safety, security (validated paths/URLs can't change after the check), and a **cached hashCode** (computed once — Strings make fast, reliable map keys).
- **String pool:** literals are interned — one shared object per distinct literal, stored in the heap (moved out of PermGen back in Java 7). `"hello" == "hello"` is true *only because of pooling*; `new String("hello")` forces a fresh object. `intern()` returns the pooled instance. Rule regardless: **compare content with `.equals()`**.
- Since Java 9, **compact strings**: Latin-1-only strings store 1 byte/char internally instead of 2 — memory win, invisible semantically.
- `substring(begin, end)` is half-open `[begin, end)` — length is `end - begin`. Half-open ranges are the Java-wide convention.
- Toolbox: `length` `charAt` `indexOf` `contains` `startsWith`/`endsWith` `replace` `strip` (Unicode-aware trim) `split` `String.join` `formatted` `repeat` `isBlank`/`isEmpty` `chars()`.
- **Text blocks** (`"""`, Java 15+): multi-line literals; the closing delimiter's position controls stripped indentation.

**StringBuilder:**
- Loop concatenation with `+=` is O(n²): each pass allocates a new String and copies everything so far. Measured in the lesson: 100k appends → **581 ms (`+=`) vs 3 ms (StringBuilder)**.
- StringBuilder wraps a growable `char` buffer (doubles capacity when full); `append` is amortized O(1), one `toString()` at the end.
- Single-expression concat (`a + b + c`) is fine — the compiler emits efficient code (`invokedynamic`-based since Java 9).
- `StringBuffer` = synchronized legacy twin; effectively never the right choice in new code.
