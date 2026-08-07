import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/*
 * Lesson 4.2 — The Streams API
 *
 * A STREAM is a pipeline over data:
 *
 *   source  ->  intermediate ops (0+)  ->  terminal op (exactly 1)
 *   .stream()   filter, map, sorted...    collect, count, forEach...
 *
 * Three facts that explain everything:
 *   1. LAZY      — intermediate ops do NOTHING until the terminal op runs
 *   2. ONE-SHOT  — a consumed stream is dead; make a new one
 *   3. NON-MUTATING — the source collection is never changed
 *
 * Streams say WHAT you want; loops say HOW to get it.
 */
public class StreamsTour {

    record Employee(String name, String team, int salary) { }

    static final List<Employee> STAFF = List.of(
            new Employee("ajay",  "backend",  95_000),
            new Employee("meera", "backend", 130_000),
            new Employee("ravi",  "frontend", 88_000),
            new Employee("zoya",  "frontend", 92_000),
            new Employee("dev",   "data",    140_000),
            new Employee("nina",  "data",    115_000));

    public static void main(String[] args) {

        // ============================================================
        // 1. The shape of a pipeline
        // ============================================================
        System.out.println("=== filter -> map -> collect ===");

        List<String> wellPaidBackend = STAFF.stream()
                .filter(e -> e.team().equals("backend"))     // keep matching
                .filter(e -> e.salary() > 90_000)
                .map(Employee::name)                         // transform Employee -> String
                .sorted()
                .toList();                                   // terminal (Java 16 shorthand)
        System.out.println("backend > 90k: " + wellPaidBackend);

        // The same as a loop = 8 lines of mutable accumulator noise.
        // The stream reads like the REQUIREMENT.

        // ============================================================
        // 2. Laziness, seen with your own eyes
        // ============================================================
        System.out.println("\n=== laziness ===");

        Stream<Employee> pipeline = STAFF.stream()
                .filter(e -> {
                    System.out.println("  filtering " + e.name());
                    return e.salary() > 100_000;
                });
        System.out.println("pipeline built — notice NOTHING printed yet");
        System.out.println("now the terminal op: count = " + pipeline.count());

        // And short-circuiting: findFirst stops at the FIRST match —
        // watch how few elements get examined:
        Optional<Employee> firstData = STAFF.stream()
                .peek(e -> System.out.println("  examining " + e.name()))
                .filter(e -> e.team().equals("data"))
                .findFirst();
        System.out.println("first data hire: " + firstData.map(Employee::name).orElse("none"));

        // ============================================================
        // 3. The core intermediate toolbox
        // ============================================================
        System.out.println("\n=== toolbox ===");

        System.out.println("distinct : " + Stream.of(1, 2, 2, 3, 3, 3).distinct().toList());
        System.out.println("limit    : " + IntStream.rangeClosed(1, 100).limit(5).boxed().toList());
        System.out.println("skip     : " + IntStream.rangeClosed(1, 10).skip(7).boxed().toList());
        System.out.println("sorted   : " + Stream.of("c", "a", "b").sorted().toList());

        // flatMap: one element -> many; flattens nested structure.
        List<List<String>> nested = List.of(List.of("a", "b"), List.of("c"), List.of("d", "e"));
        System.out.println("flatMap  : " + nested.stream().flatMap(List::stream).toList());

        // mapToInt -> primitive stream: no boxing + numeric ops for free
        var stats = STAFF.stream().mapToInt(Employee::salary).summaryStatistics();
        System.out.println("stats    : avg=" + (int) stats.getAverage()
                + " min=" + stats.getMin() + " max=" + stats.getMax());

        // ============================================================
        // 4. COLLECTORS — the powerful endings
        // ============================================================
        System.out.println("\n=== collectors ===");

        // groupingBy: List -> Map<key, group> — the #1 interview collector
        Map<String, List<String>> byTeam = STAFF.stream()
                .collect(Collectors.groupingBy(
                        Employee::team,
                        Collectors.mapping(Employee::name, Collectors.toList())));
        System.out.println("groupingBy : " + byTeam);

        // ...with a downstream aggregation:
        Map<String, Double> avgSalary = STAFF.stream()
                .collect(Collectors.groupingBy(
                        Employee::team,
                        Collectors.averagingInt(Employee::salary)));
        System.out.println("avg/team   : " + avgSalary);

        // partitioningBy: the two-bucket special case
        Map<Boolean, Long> sixFigure = STAFF.stream()
                .collect(Collectors.partitioningBy(
                        e -> e.salary() >= 100_000, Collectors.counting()));
        System.out.println("100k split : " + sixFigure);

        // joining: strings with separator/prefix/suffix
        String roster = STAFF.stream()
                .map(Employee::name)
                .collect(Collectors.joining(", ", "[", "]"));
        System.out.println("joining    : " + roster);

        // toMap: watch for duplicate keys (throws without a merge fn!)
        Map<String, Integer> topPerTeam = STAFF.stream()
                .collect(Collectors.toMap(
                        Employee::team,
                        Employee::salary,
                        Integer::max));            // merge function resolves collisions
        System.out.println("toMap      : " + topPerTeam);

        // ============================================================
        // 5. reduce — folding to a single value
        // ============================================================
        System.out.println("\n=== reduce ===");

        int totalPayroll = STAFF.stream()
                .mapToInt(Employee::salary)
                .sum();                                       // sum/min/max = packaged reduces
        System.out.println("sum      : " + totalPayroll);

        Optional<Employee> topEarner = STAFF.stream()
                .reduce((a, b) -> a.salary() >= b.salary() ? a : b);
        System.out.println("reduce   : top earner = " + topEarner.map(Employee::name).orElse("-"));
        // (max(Comparator.comparingInt(Employee::salary)) says it clearer.)

        // ============================================================
        // 6. Lesson 3's word count, revisited — the promised collapse
        // ============================================================
        System.out.println("\n=== word count, stream edition ===");

        String sentence = "to be or not to be";
        Map<String, Long> counts = Stream.of(sentence.split(" "))
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
        System.out.println(counts);

        // ============================================================
        // 7. Rules of the road
        // ============================================================
        // - A stream can be consumed ONCE:
        Stream<String> once = Stream.of("a", "b");
        once.count();
        try {
            once.count();
        } catch (IllegalStateException e) {
            System.out.println("\nreuse -> IllegalStateException (streams are one-shot)");
        }
        // - Don't mutate shared state in forEach (defeats the model; breaks
        //   parallel streams). Collect instead.
        // - .parallelStream() exists but measure first: for small data the
        //   fork/join overhead usually LOSES. Default to sequential.
        // - Streams complement loops, not replace them: 2-line loops with
        //   early exits or index math often stay clearer as loops.
    }
}
