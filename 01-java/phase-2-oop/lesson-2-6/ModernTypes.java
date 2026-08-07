/*
 * Lesson 2.6 — Enums, records, sealed classes
 *
 * Three modern type kinds, each ANSWERING a design question:
 *   enum   -> "this type has a FIXED SET of instances"  (days, states, planets)
 *   record -> "this type IS its data"                   (DTOs, coordinates, API responses)
 *   sealed -> "this type has a FIXED SET of SUBTYPES"   (closed hierarchies)
 */
public class ModernTypes {

    public static void main(String[] args) {

        // ============================================================
        // 1. ENUMS — type-safe constants that are real objects
        // ============================================================
        System.out.println("=== enums ===");

        OrderStatus status = OrderStatus.SHIPPED;

        System.out.println("status: " + status);              // toString -> "SHIPPED"
        System.out.println("name(): " + status.name() + ", ordinal(): " + status.ordinal());
        // (Don't persist ordinal() — reordering the enum corrupts your data. Store name().)

        // Enums can carry FIELDS and METHODS — they're full classes:
        System.out.println("label: " + status.getLabel() + ", terminal? " + status.isTerminal());

        // values() iterates all instances, valueOf parses a name:
        for (OrderStatus s : OrderStatus.values()) {
            System.out.println("  " + s + " -> " + s.getLabel());
        }
        System.out.println("parsed: " + OrderStatus.valueOf("DELIVERED"));

        // switch on enums — with EXHAUSTIVENESS: cover all constants and
        // no default is needed; add a constant later and this stops compiling
        // until you handle it. The compiler becomes your checklist.
        String action = switch (status) {
            case NEW        -> "await payment";
            case PAID       -> "pack the box";
            case SHIPPED    -> "track the courier";
            case DELIVERED  -> "ask for a review";
            case CANCELLED  -> "restock items";
        };
        System.out.println("next action: " + action);

        // Enum comparison uses == (they're singletons — one instance per constant):
        System.out.println("status == SHIPPED: " + (status == OrderStatus.SHIPPED));

        // ============================================================
        // 2. RECORDS — immutable data carriers, zero boilerplate
        // ============================================================
        System.out.println("\n=== records ===");

        // One line declares: final fields, constructor, accessors,
        // equals, hashCode, toString. (Compare: lesson 2.5's Book was ~30 lines.)
        Money price = new Money(2499, "INR");
        Money same  = new Money(2499, "INR");

        System.out.println("toString : " + price);            // Money[amount=2499, currency=INR]
        System.out.println("accessor : " + price.amount());   // note: amount(), NOT getAmount()
        System.out.println("equals   : " + price.equals(same));   // value equality, generated

        // Records are IMMUTABLE — "modification" returns a new one (a "wither"):
        Money discounted = price.withAmount(1999);
        System.out.println("original : " + price + ", discounted: " + discounted);

        // Compact constructor VALIDATES (see the record below):
        try {
            new Money(-5, "INR");
        } catch (IllegalArgumentException e) {
            System.out.println("rejected : " + e.getMessage());
        }

        // ============================================================
        // 3. SEALED — the compiler knows ALL subtypes
        // ============================================================
        System.out.println("\n=== sealed hierarchy ===");

        PaymentResult[] results = {
                new Success("TXN-42"),
                new Declined("insufficient funds"),
                new NetworkError(3),
        };

        for (PaymentResult r : results) {
            // Pattern-matching switch over a sealed type: EXHAUSTIVE, no default.
            // A new subtype tomorrow = compile error here = you can't forget to handle it.
            String msg = switch (r) {
                case Success s      -> "OK, ref=" + s.txnId();
                case Declined d     -> "declined: " + d.reason();
                case NetworkError n -> "retry (attempt " + n.attempts() + ")";
            };
            System.out.println("  " + msg);
        }
    }
}

/*
 * ENUM with state & behavior. Constructor is implicitly private —
 * the constant list at the top is the ONLY place instances are made.
 */
enum OrderStatus {
    NEW("order placed"),
    PAID("payment received"),
    SHIPPED("on the way"),
    DELIVERED("with customer"),
    CANCELLED("order cancelled");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    String getLabel() {
        return label;
    }

    boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}

/*
 * RECORD: the header declares COMPONENTS; everything else is generated.
 * Perfect for DTOs, API payloads, map keys, multi-value returns.
 */
record Money(long amount, String currency) {

    // COMPACT constructor: no parameter list, runs before fields are set.
    // The place for validation/normalization.
    Money {
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative: " + amount);
        }
        currency = currency.toUpperCase();     // normalize (reassigns the parameter)
    }

    // Records can have extra methods (and static members). Not extra
    // instance fields — components in the header are the ONLY state.
    Money withAmount(long newAmount) {
        return new Money(newAmount, currency);
    }
}

/*
 * SEALED interface: `permits` lists the ONLY allowed implementations.
 * Each permitted subtype must be final, sealed, or non-sealed.
 * Records happen to be great sealed-subtypes (final by nature).
 */
sealed interface PaymentResult permits Success, Declined, NetworkError { }

record Success(String txnId)      implements PaymentResult { }
record Declined(String reason)    implements PaymentResult { }
record NetworkError(int attempts) implements PaymentResult { }

/*
 * Why sealed matters: PaymentResult models "exactly these outcomes".
 * Unsealed, the switch in main would need a `default` — and adding a
 * new outcome would be SILENTLY unhandled. Sealed + exhaustive switch
 * = the compiler FORCES every handler to catch up. This is Java
 * borrowing algebraic data types from functional languages.
 */
