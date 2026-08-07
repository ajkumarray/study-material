/*
 * D — DEPENDENCY INVERSION PRINCIPLE (DIP)
 * "High-level modules should not depend on low-level modules. Both should
 *  depend on ABSTRACTIONS. Abstractions should not depend on details."
 * Run:  java D_DependencyInversion.java
 *
 * A high-level policy (business logic) that `new`s a concrete low-level detail
 * (a specific DB, mailer, API) is welded to it — can't swap, can't test in
 * isolation. Invert the dependency: both sides depend on an interface, and the
 * concrete implementation is INJECTED. This is exactly your Java capstone's
 * ExpenseService → ExpenseRepository (interface) design, and what Spring's DI
 * container automates.
 */
public class D_DependencyInversion {

    public static void main(String[] args) {
        // Inject whichever implementation we want — production or a test fake —
        // WITHOUT changing NotificationService (the high-level policy).
        var real = new NotificationService(new EmailSender());
        System.out.println(real.notify("deploy succeeded"));

        var test = new NotificationService(new FakeSender());   // swap for tests
        System.out.println(test.notify("hello"));
        System.out.println("fake captured: " + ((FakeSender) test.sender()).last());
    }
}

/* ---- BEFORE (violates DIP) --------------------------------------------------
   class NotificationService {
       private final EmailSender sender = new EmailSender();   // welded to a concrete class
       String notify(String msg) { return sender.send(msg); }
   }
   NotificationService (high-level) depends directly on EmailSender (low-level).
   Can't switch to SMS/Slack without editing it; can't unit-test without really
   sending email. The dependency arrow points the wrong way.
----------------------------------------------------------------------------- */

// AFTER: the abstraction both sides depend on. Owned by the high-level module.
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
    MessageSender sender() { return sender; }           // (exposed for the demo only)
}

// Low-level details implement the abstraction. Add SlackSender/SmsSender freely.
class EmailSender implements MessageSender {
    public String send(String m) { return "[email] " + m; }
}
class FakeSender implements MessageSender {             // a test double (Java Phase 6.2)
    private String last;
    public String send(String m) { this.last = m; return "[fake] " + m; }
    String last() { return last; }
}

/*
 * WHY IT'S BETTER:
 *  - Swap implementations (email -> Slack -> SMS) with no change to the policy.
 *  - Unit-test the policy with a fake — no real I/O (constructor injection,
 *    Spring Boot Phase 3).
 *  - Decoupled, parallel-developable modules.
 * DIP vs DI vs IoC: DIP is the PRINCIPLE (depend on abstractions); Dependency
 * Injection is a TECHNIQUE to supply them; an IoC container (Spring) automates
 * the wiring. Your capstone did DIP by hand; @Service + constructor injection
 * is the same idea, wired by the framework.
 */
