import java.util.ArrayList;
import java.util.List;

/*
 * Phase 6 — ARCHITECTURAL patterns: Hexagonal (Ports & Adapters), runnable.
 * Run:  java -ea HexagonalDemo.java
 *
 * Hexagonal architecture puts the DOMAIN (business logic) at the center, with
 * no dependencies on frameworks/DB/UI. It talks to the outside world ONLY
 * through PORTS (interfaces the domain owns). ADAPTERS implement those ports for
 * specific tech (a DB adapter, a web adapter). The dependency arrows point
 * INWARD — this is Dependency Inversion (Phase 2) at the architecture level, and
 * exactly the layering your Java capstone / Spring app already used.
 */
public class HexagonalDemo {

    public static void main(String[] args) {
        // Wire adapters into the domain (like Spring's DI container does).
        // Swap InMemory for a JDBC adapter and the domain doesn't change.
        NotifierPort notifier = new ConsoleNotifier();
        var log = new ArrayList<String>();
        var service = new RegistrationService(new InMemoryUserRepo(), msg -> log.add(msg));

        service.register("ajay@dev.io");
        service.register("meera@dev.io");
        assert log.equals(List.of("welcome ajay@dev.io", "welcome meera@dev.io"));

        // The domain rejects a duplicate — business rule, tech-independent.
        try { service.register("ajay@dev.io"); assert false; }
        catch (IllegalStateException e) { assert e.getMessage().contains("exists"); }

        System.out.println("All hexagonal tests passed (domain tested with fake adapters).");
    }

    // ---- THE DOMAIN CORE (no framework/DB/web imports) ----
    record User(String email) { }

    // PORTS — interfaces the DOMAIN owns and depends on (abstractions, DIP).
    interface UserRepositoryPort {                 // "driven" port (domain -> infra)
        boolean existsByEmail(String email);
        void save(User user);
    }
    interface NotifierPort {                        // "driven" port
        void notify(String message);
    }

    // The domain service — pure business logic, depends only on PORTS.
    static class RegistrationService {
        private final UserRepositoryPort users;     // injected abstraction
        private final NotifierPort notifier;
        RegistrationService(UserRepositoryPort users, NotifierPort notifier) {
            this.users = users; this.notifier = notifier;
        }
        void register(String email) {               // the use case (a "driving" port in full form)
            if (users.existsByEmail(email)) throw new IllegalStateException("user exists: " + email);
            users.save(new User(email));
            notifier.notify("welcome " + email);
        }
    }

    // ---- ADAPTERS — implement the ports for specific tech (the OUTSIDE) ----
    // Swap these without touching the domain (a real app would have a JDBC repo
    // adapter, an SMTP notifier adapter, a REST controller as the driving adapter).
    static class InMemoryUserRepo implements UserRepositoryPort {
        private final List<User> store = new ArrayList<>();
        public boolean existsByEmail(String email) { return store.stream().anyMatch(u -> u.email().equals(email)); }
        public void save(User user) { store.add(user); }
    }
    static class ConsoleNotifier implements NotifierPort {
        public void notify(String message) { System.out.println("[notify] " + message); }
    }
}
