/*
 * Lesson 2.1 — Classes, objects, constructors, `this`
 *
 * A CLASS is a blueprint: it defines what state (fields) and behavior
 * (methods) something has. An OBJECT is one concrete instance built
 * from that blueprint, with its own copy of the state.
 *
 *   class  BankAccount        -> the blueprint (written once)
 *   ajay's account, ravi's    -> objects (created with `new`, each independent)
 *
 * Until now everything was `static` — code belonging to the class itself.
 * Instance members belong to each OBJECT. This lesson is the switch-over.
 */
public class ClassesAndObjects {

    public static void main(String[] args) {

        // ============================================================
        // 1. Creating objects with `new`
        // ============================================================
        System.out.println("=== creating objects ===");

        // `new` does three things:
        //   1. allocates memory on the HEAP for the object's fields
        //   2. runs the CONSTRUCTOR to initialize it
        //   3. returns a REFERENCE to it (which lives in the variable)
        BankAccount ajay = new BankAccount("Ajay", 1000.0);
        BankAccount ravi = new BankAccount("Ravi");        // different constructor!

        // Each object has ITS OWN state:
        ajay.deposit(500);
        ravi.deposit(50);
        System.out.println(ajay.describe());
        System.out.println(ravi.describe());   // Ravi's balance untouched by Ajay's deposit

        // ============================================================
        // 2. Methods can protect state (a taste of encapsulation)
        // ============================================================
        System.out.println("\n=== guarded behavior ===");

        boolean ok = ajay.withdraw(200);
        System.out.println("withdraw 200 ok? " + ok + " -> " + ajay.describe());

        ok = ajay.withdraw(99_999);            // more than the balance
        System.out.println("withdraw 99999 ok? " + ok + " -> " + ajay.describe());
        // The object REFUSED to enter an invalid state. That's the whole
        // point of OOP: data + the rules that keep it valid, in one place.

        // ============================================================
        // 3. References vs objects (lesson 1.4 pays off)
        // ============================================================
        System.out.println("\n=== references ===");

        BankAccount alias = ajay;              // copies the REFERENCE, not the object
        alias.deposit(1);
        System.out.println("deposit via alias -> " + ajay.describe() + "  (same object!)");

        // A reference can point at NOTHING:
        BankAccount nobody = null;
        // nobody.deposit(10);  // <- would throw NullPointerException at runtime
        System.out.println("nobody == null -> " + (nobody == null));

        // ============================================================
        // 4. The class counts its instances (static preview)
        // ============================================================
        System.out.println("\n=== static counter ===");
        System.out.println("accounts created: " + BankAccount.getAccountCount());
        // static belongs to the CLASS — one copy shared by all objects.
        // Full static deep-dive in lesson 2.7.
    }
}

/*
 * Only ONE public class per file — but other (package-private) classes
 * may share it. Real projects put each class in its own file; here we
 * keep them together so the lesson is one runnable unit.
 */
class BankAccount {

    // ---- FIELDS: per-object state -----------------------------------
    // Fields get default values (0, false, null) if not initialized —
    // unlike locals. We initialize via constructors anyway.
    private final String owner;      // final: set once, never changes
    private double balance;          // private: only THIS class can touch it (2.2 explains)

    // ---- STATIC field: ONE copy, shared by the whole class ----------
    private static int accountCount = 0;

    // ---- CONSTRUCTORS -----------------------------------------------
    // A constructor initializes a new object. Rules:
    //   - same name as the class, NO return type (not even void)
    //   - runs exactly once per object, when `new` executes
    //   - overloadable, like methods

    BankAccount(String owner, double openingBalance) {
        // `this` = "the object currently being built/used".
        // Needed here because the parameter `owner` SHADOWS the field:
        this.owner = owner;
        this.balance = openingBalance;
        accountCount++;
    }

    // Constructor CHAINING: this(...) calls a sibling constructor —
    // must be the FIRST statement. Keeps initialization logic in ONE place.
    BankAccount(String owner) {
        this(owner, 0.0);            // delegate to the main constructor
    }

    // If you write NO constructor at all, the compiler gifts you a no-arg
    // "default constructor". The moment you write ANY constructor, that
    // gift is withdrawn — `new BankAccount()` would not compile here.

    // ---- INSTANCE METHODS: behavior that uses the object's state ----

    void deposit(double amount) {
        if (amount <= 0) {
            return;                  // guard clause: reject nonsense silently for now
        }
        balance += amount;           // shorthand for this.balance — no shadowing here
    }

    boolean withdraw(double amount) {
        if (amount <= 0 || amount > balance) {
            return false;            // refuse to go negative
        }
        balance -= amount;
        return true;
    }

    String describe() {
        return owner + "'s balance: " + balance;
    }

    // static method: called on the CLASS (BankAccount.getAccountCount()),
    // no `this` available — it belongs to no particular object.
    static int getAccountCount() {
        return accountCount;
    }
}
