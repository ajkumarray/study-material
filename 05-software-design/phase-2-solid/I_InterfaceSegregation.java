/*
 * I — INTERFACE SEGREGATION PRINCIPLE (ISP)
 * "No client should be forced to depend on methods it does not use."
 * Prefer many small, focused (role) interfaces over one fat one.
 * Run:  java I_InterfaceSegregation.java
 *
 * A fat interface forces implementers to provide methods that don't apply to
 * them — usually by throwing UnsupportedOperationException (which also breaks
 * LSP). Split the interface along the roles clients actually need.
 */
public class I_InterfaceSegregation {

    public static void main(String[] args) {
        Workable[] workers = { new Human("Ajay"), new Robot("R2") };
        for (Workable w : workers) System.out.println(w.work());

        // Only humans eat; code that needs an eater depends ONLY on Eatable,
        // and a Robot simply isn't one — no dummy method, no exception.
        Eatable human = new Human("Ajay");
        System.out.println(human.eat());
    }
}

/* ---- BEFORE (violates ISP) --------------------------------------------------
   interface Worker { String work(); String eat(); }
   class Robot implements Worker {
       public String work() { return "building"; }
       public String eat()  { throw new UnsupportedOperationException(); }  // robots don't eat!
   }
   The fat Worker interface forces Robot to implement eat(). Callers can't trust
   eat() on a Worker (it might throw) — and that throw also violates LSP.
----------------------------------------------------------------------------- */

// AFTER: small role interfaces. Implement only what applies.
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

/*
 * WHY IT'S BETTER:
 *  - Implementers provide only relevant behavior (no throwing stubs).
 *  - Clients depend only on the narrow role they use (looser coupling).
 *  - Changes to one role's interface don't ripple to unrelated implementers.
 * Real-world: Java split big interfaces into roles (Runnable vs Callable);
 * Spring's many focused *Aware / *Repository interfaces. ISP is SRP applied to
 * interfaces.
 */
