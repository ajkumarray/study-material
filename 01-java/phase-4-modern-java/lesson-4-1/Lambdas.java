import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/*
 * Lesson 4.1 — Lambdas & functional interfaces
 *
 * A LAMBDA is an inline implementation of a FUNCTIONAL INTERFACE —
 * an interface with exactly ONE abstract method (a "SAM type").
 *
 *   (parameters) -> expression
 *   (parameters) -> { statements; return x; }
 *
 * Java is still statically typed: a lambda has no type of its own —
 * it takes the type of the functional interface it's assigned to
 * (the "target type"). Same lambda text, different interfaces, fine.
 */
public class Lambdas {

    public static void main(String[] args) {

        // ============================================================
        // 1. From anonymous class to lambda (2.7's cliffhanger resolved)
        // ============================================================
        System.out.println("=== evolution ===");

        // 2004 style:
        Greeter old = new Greeter() {
            @Override
            public String greet(String name) {
                return "Hello, " + name;
            }
        };

        // Modern: same semantics, the boilerplate deleted.
        Greeter lambda = name -> "Hello, " + name;

        System.out.println(old.greet("anonymous class"));
        System.out.println(lambda.greet("lambda"));

        // ============================================================
        // 2. THE BIG FOUR from java.util.function — learn these cold
        // ============================================================
        System.out.println("\n=== the big four ===");

        // Predicate<T>: T -> boolean          "does it pass?"
        Predicate<String> isLong = s -> s.length() > 5;
        System.out.println("Predicate : isLong(\"kafka\") = " + isLong.test("kafka"));

        // Function<T, R>: T -> R              "transform it"
        Function<String, Integer> length = s -> s.length();
        System.out.println("Function  : length(\"docker\") = " + length.apply("docker"));

        // Consumer<T>: T -> void              "use it up"
        Consumer<String> printer = s -> System.out.println("Consumer  : consuming " + s);
        printer.accept("redis");

        // Supplier<T>: () -> T                "make one on demand"
        Supplier<List<String>> freshList = () -> new ArrayList<>();
        System.out.println("Supplier  : fresh = " + freshList.get());

        // Variants worth knowing: BiFunction<T,U,R>, UnaryOperator<T>,
        // BinaryOperator<T>, and primitive versions (IntPredicate,
        // ToIntFunction...) that avoid boxing in hot loops.
        BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
        UnaryOperator<String> shout = s2 -> s2.toUpperCase() + "!";
        System.out.println("BiFunction: " + add.apply(17, 25) + ", UnaryOp: " + shout.apply("go"));

        // ============================================================
        // 3. METHOD REFERENCES — lambdas that just call one method
        // ============================================================
        System.out.println("\n=== method references ===");

        // Four kinds:                              lambda form:
        Function<String, Integer> parse = Integer::parseInt;      // s -> Integer.parseInt(s)   [static]
        Function<String, Integer> len   = String::length;         // s -> s.length()            [instance method, unbound]
        Predicate<String> isJava        = "java"::equals;         // s -> "java".equals(s)      [instance method, bound]
        Supplier<ArrayList<String>> mk  = ArrayList::new;         // () -> new ArrayList<>()    [constructor]

        System.out.println("static ref   : " + parse.apply("42"));
        System.out.println("unbound ref  : " + len.apply("spring"));
        System.out.println("bound ref    : " + isJava.test("java"));
        System.out.println("ctor ref     : " + mk.get());

        // Rule of thumb: if the lambda only calls one existing method,
        // use the reference — it names the intent.

        // ============================================================
        // 4. COMPOSITION — small functions, snapped together
        // ============================================================
        System.out.println("\n=== composition ===");

        Function<String, String> trim = String::strip;
        Function<String, String> upper = String::toUpperCase;
        Function<String, String> cleaned = trim.andThen(upper);       // trim FIRST, then upper
        System.out.println("andThen  : '" + cleaned.apply("  ship it  ") + "'");

        Predicate<String> notBlank = Predicate.not(String::isBlank);
        Predicate<String> shortEnough = s -> s.length() <= 10;
        Predicate<String> valid = notBlank.and(shortEnough);          // predicates: and/or/negate
        System.out.println("and      : valid(\"ok\") = " + valid.test("ok")
                + ", valid(\"\") = " + valid.test(""));

        // ============================================================
        // 5. CAPTURE — lambdas close over their environment
        // ============================================================
        System.out.println("\n=== capture / effectively final ===");

        int taxRate = 18;                             // captured variable
        Function<Integer, Integer> withTax = price -> price + price * taxRate / 100;
        System.out.println("withTax(100) = " + withTax.apply(100));
        // taxRate = 20;   // <- uncomment: BOTH lines error. A captured local
        //                    must be EFFECTIVELY FINAL (never reassigned).
        // Why: the lambda copies the value; allowing reassignment would let
        // the copy and the variable silently disagree (2.7 said the same
        // about anonymous classes).

        // Unlike anonymous classes, lambdas have NO `this` of their own —
        // `this` inside a lambda is the enclosing instance. And they don't
        // shadow: cleaner scoping, one less trap.

        // ============================================================
        // 6. Writing an API that ACCEPTS behavior
        // ============================================================
        System.out.println("\n=== behavior as a parameter ===");

        List<String> services = List.of("auth", "payments", "notifications", "search");

        // One method, infinitely many filters — behavior parameterization,
        // THE idea that unlocks streams (next lesson):
        System.out.println("long names : " + pickWhere(services, s -> s.length() > 6));
        System.out.println("has 'a'    : " + pickWhere(services, s -> s.contains("a")));
    }

    static List<String> pickWhere(List<String> items, Predicate<String> keep) {
        List<String> out = new ArrayList<>();
        for (String item : items) {
            if (keep.test(item)) out.add(item);
        }
        return out;
    }
}

/*
 * @FunctionalInterface asks the compiler to ENFORCE "exactly one
 * abstract method" — adding a second becomes a compile error.
 * (default/static methods don't count against the one.)
 */
@FunctionalInterface
interface Greeter {
    String greet(String name);
}
