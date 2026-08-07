import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
 * Lesson 4.3 — Optional done right
 *
 * Optional<T> = a container holding EITHER one T or nothing.
 * Its job: make "might be absent" part of the METHOD SIGNATURE,
 * so callers can't forget the empty case (unlike null, which is
 * invisible in the type and explodes later as NullPointerException —
 * its inventor calls null "the billion dollar mistake").
 *
 *   Optional<User> findUser(String id)   // absence is documented + enforced
 *   User findUser(String id)             // might return null... surprise!
 */
public class OptionalTour {

    record User(String name, String email, Optional<String> nickname) { }

    static final Map<String, User> DB = Map.of(
            "u1", new User("Ajay", "ajay@dev.io", Optional.of("aj")),
            "u2", new User("Meera", "meera@dev.io", Optional.empty()));

    public static void main(String[] args) {

        // ============================================================
        // 1. Creating
        // ============================================================
        System.out.println("=== creating ===");

        Optional<String> present = Optional.of("value");          // throws NPE if null!
        Optional<String> empty   = Optional.empty();
        Optional<String> maybe   = Optional.ofNullable(legacyLookup());  // null -> empty
        System.out.println(present + " / " + empty + " / " + maybe);

        // ============================================================
        // 2. Consuming — the SPECTRUM from bad to good
        // ============================================================
        System.out.println("\n=== consuming ===");

        Optional<User> found = findUser("u1");

        // BAD:  isPresent() + get() — null-checking with extra steps.
        //       (get() on empty throws NoSuchElementException; it exists
        //        for the rare case you've ALREADY proven presence.)
        if (found.isPresent()) {
            System.out.println("bad style : " + found.get().name());
        }

        // GOOD: say what happens in both cases, declaratively:
        System.out.println("orElse    : " + findUser("nope").map(User::name).orElse("guest"));

        findUser("u1").ifPresent(u -> System.out.println("ifPresent : " + u.name()));

        findUser("nope").ifPresentOrElse(
                u -> System.out.println("found " + u.name()),
                () -> System.out.println("ifPresentOrElse: not found branch"));

        // orElseGet vs orElse — the performance trap:
        // orElse(expensive()) ALWAYS evaluates the fallback, even when present.
        // orElseGet(() -> expensive()) evaluates LAZILY, only when empty.
        String v1 = present.orElse(buildDefault("orElse"));       // prints — wasteful!
        String v2 = present.orElseGet(() -> buildDefault("orElseGet")); // silent — lazy
        System.out.println("(orElseGet skipped the fallback: value stayed " + v2 + ")");

        // Fail loudly when absence is a bug:
        try {
            findUser("nope").orElseThrow(
                    () -> new IllegalStateException("user must exist here"));
        } catch (IllegalStateException e) {
            System.out.println("orElseThrow: " + e.getMessage());
        }

        // ============================================================
        // 3. TRANSFORMING — map / filter / flatMap chains
        // ============================================================
        System.out.println("\n=== transforming ===");

        // map: transform the value IF present; empties just flow through.
        String domain = findUser("u1")
                .map(User::email)                      // Optional<String>
                .map(e -> e.substring(e.indexOf('@') + 1))
                .orElse("unknown");
        System.out.println("map chain : " + domain);

        // filter: present -> empty if the predicate fails
        System.out.println("filter    : " + findUser("u1")
                .filter(u -> u.email().endsWith(".io"))
                .map(User::name)
                .orElse("no .io user"));

        // flatMap: when the mapper ITSELF returns Optional —
        // map would give Optional<Optional<String>>; flatMap flattens:
        String nick = findUser("u1")
                .flatMap(User::nickname)
                .orElse("(no nickname)");
        String nick2 = findUser("u2")
                .flatMap(User::nickname)
                .orElse("(no nickname)");
        System.out.println("flatMap   : u1=" + nick + ", u2=" + nick2);

        // or(): chain fallback SOURCES (Java 9+):
        User user = findUser("nope")
                .or(() -> findUser("u2"))              // second lookup only if first empty
                .orElseThrow();
        System.out.println("or        : fell back to " + user.name());

        // ============================================================
        // 4. THE RULES — where Optional belongs (and doesn't)
        // ============================================================
        System.out.println("\n=== rules ===");
        System.out.println("""
                DO   : return Optional from "find/lookup" style methods
                DO   : chain map/filter/flatMap, end with orElse*/ifPresent
                DON'T: Optional PARAMETERS (forces callers to wrap; overload instead)
                DON'T: Optional FIELDS as a habit (not Serializable; adds a box per read
                       — plain nullable field + Optional-returning getter is cleaner)
                DON'T: Optional.get() bare; collections of Optional; Optional<Collection>
                       (an empty list already says "nothing" — don't double-wrap)
                NEVER: return null from a method that declares Optional (defeats it all)
                """);
        // (Our User record uses an Optional component deliberately to show
        //  flatMap — in production code a nullable field + accessor is more common.)

        // Primitive flavors avoid boxing: OptionalInt/OptionalLong/OptionalDouble
        // — and that's what IntStream.max() etc. return (lesson 4.2's stats).
    }

    // The canonical shape: repository lookups return Optional.
    static Optional<User> findUser(String id) {
        return Optional.ofNullable(DB.get(id));
    }

    static String legacyLookup() {
        return null;                                   // old APIs return null...
    }

    static String buildDefault(String tag) {
        System.out.println("  (building fallback via " + tag + ")");
        return "default";
    }
}
