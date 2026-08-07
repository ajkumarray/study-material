/*
 * Lesson 2.4 — Abstract classes & interfaces
 *
 * Two tools for defining CONTRACTS — "you must provide this behavior":
 *
 * ABSTRACT CLASS: a partial class. Can hold state, constructors, and
 *   implemented methods, plus `abstract` methods children MUST fill in.
 *   Can't be instantiated. IS-A relationship with shared machinery.
 *
 * INTERFACE: a pure capability. No instance state. A class can
 *   implement MANY of them. CAN-DO relationship (Comparable = "can be
 *   compared", Runnable = "can be run").
 *
 * Rule of thumb: interface for the contract, abstract class only when
 * implementations genuinely share state/code. When in doubt: interface.
 */
public class AbstractAndInterfaces {

    public static void main(String[] args) {

        // ============================================================
        // 1. Abstract class: the TEMPLATE METHOD pattern
        // ============================================================
        System.out.println("=== abstract class / template method ===");

        // Payment p = new Payment(10);   // <- does not compile: abstract class
        Payment card = new CardPayment(2500, "4212-****");
        Payment upi  = new UpiPayment(499, "ajay@upi");

        // process() is written ONCE in the parent; the variable steps
        // (validate/execute) are abstract, filled in by each child.
        card.process();
        upi.process();

        // ============================================================
        // 2. Interfaces: capabilities, mixed in freely
        // ============================================================
        System.out.println("\n=== interfaces as capabilities ===");

        // CardPayment implements Refundable; UpiPayment doesn't.
        // Code can demand JUST the capability it needs:
        refundIfPossible(card);
        refundIfPossible(upi);

        // ============================================================
        // 3. Default methods
        // ============================================================
        System.out.println("\n=== default methods ===");

        // Notifier.notifyAll... has a DEFAULT implementation: implementors
        // get it free, may override. (This is how Java 8 added methods to
        // 20-year-old interfaces like List without breaking the world.)
        Notifier basic = new EmailNotifier();
        basic.send("payment received");
        basic.sendUrgent("card declined!");     // default method, inherited free

        // ============================================================
        // 4. The diamond problem — and Java's answer
        // ============================================================
        System.out.println("\n=== diamond resolution ===");

        // SmartNotifier implements TWO interfaces that BOTH provide a
        // default greet(). Java refuses to guess: the class MUST override
        // and pick (or combine) — see the class below.
        SmartNotifier smart = new SmartNotifier();
        System.out.println(smart.greet());
    }

    // Demands the CAPABILITY, not a class hierarchy position:
    static void refundIfPossible(Payment p) {
        if (p instanceof Refundable r) {
            r.refund();
        } else {
            System.out.println("  (no refund support for this payment type)");
        }
    }
}

// ---------------- abstract class ------------------------------------

abstract class Payment {
    // Abstract classes CAN have state and constructors —
    // interfaces can't. This is their superpower.
    protected final double amount;

    Payment(double amount) {
        this.amount = amount;
    }

    // TEMPLATE METHOD: the algorithm's SKELETON, fixed for everyone.
    // `final` so children can't rearrange the steps.
    final void process() {
        if (!validate()) {
            System.out.println("  validation failed for " + amount);
            return;
        }
        execute();                       // <- dynamic dispatch fills in the blank
        System.out.println("  processed " + amount + " via " + methodName());
    }

    // The blanks children MUST fill in (no body — that's what abstract means):
    abstract boolean validate();
    abstract void execute();
    abstract String methodName();
}

class CardPayment extends Payment implements Refundable {
    private final String maskedCard;

    CardPayment(double amount, String maskedCard) {
        super(amount);
        this.maskedCard = maskedCard;
    }

    @Override boolean validate() { return maskedCard.length() >= 9; }
    @Override void execute()     { System.out.println("  charging card " + maskedCard); }
    @Override String methodName(){ return "card"; }

    @Override
    public void refund() {               // interface methods are implicitly public —
        System.out.println("  refunded " + amount + " to " + maskedCard);
        // so the implementation MUST say `public` (can't narrow access, lesson 2.3)
    }
}

class UpiPayment extends Payment {
    private final String vpa;

    UpiPayment(double amount, String vpa) {
        super(amount);
        this.vpa = vpa;
    }

    @Override boolean validate() { return vpa.contains("@"); }
    @Override void execute()     { System.out.println("  UPI request to " + vpa); }
    @Override String methodName(){ return "UPI"; }
}

// ---------------- interfaces ----------------------------------------

interface Refundable {
    void refund();                       // implicitly public abstract
    // Interface fields are implicitly public static final (constants):
    int MAX_REFUND_DAYS = 30;
}

interface Notifier {
    void send(String message);           // the one REQUIRED method

    // DEFAULT method: shipped WITH the interface, override optional.
    default void sendUrgent(String message) {
        send("URGENT: " + message.toUpperCase());
    }

    // Interfaces can also hold static helpers:
    static Notifier none() {
        return msg -> { };               // (a lambda — Phase 4 preview!)
    }
}

class EmailNotifier implements Notifier {
    @Override
    public void send(String message) {
        System.out.println("  email: " + message);
    }
}

// ---------------- the diamond ---------------------------------------

interface English { default String greet() { return "Hello"; } }
interface Hindi   { default String greet() { return "Namaste"; } }

class SmartNotifier implements English, Hindi {
    // Both parents provide greet() -> compile ERROR unless we override:
    @Override
    public String greet() {
        // InterfaceName.super.method() reaches a specific parent's default:
        return English.super.greet() + " / " + Hindi.super.greet();
    }
}

/*
 * Abstract class vs interface — the interview table:
 *
 *                      | abstract class        | interface
 *  --------------------+-----------------------+----------------------------
 *  instance fields     | yes                   | no (only public static final)
 *  constructors        | yes                   | no
 *  method bodies       | yes                   | default/static/private only
 *  how many can a      | ONE (single           | MANY
 *  class have?         | inheritance)          |
 *  access modifiers    | all four              | members implicitly public
 *  relationship        | IS-A + shared code    | CAN-DO capability
 */
