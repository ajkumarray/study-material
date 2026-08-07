import java.util.List;

/*
 * Phase 7 — REFACTORING & code smells
 * Run:  java -ea Refactoring.java
 *
 * Refactoring = changing code's STRUCTURE without changing its BEHAVIOR, to make
 * it cleaner. The safety net is TESTS: they prove behavior is preserved after
 * each small step. This file shows two classic refactorings, with assertions
 * that hold for BOTH the "before" and "after" — proving the refactor is safe.
 */
public class Refactoring {

    public static void main(String[] args) {
        var items = List.of(
            new Item("book", 2, 30),
            new Item("pen", 5, 2),
            new Item("laptop", 1, 800));

        // Same result before & after refactoring #1 (extract method):
        assert receiptTotal_before(items) == receiptTotal_after(items);
        assert receiptTotal_after(items) == (2*30 + 5*2 + 1*800) * 0.9;   // subtotal 870 > 500 -> 10% off = 783

        // Same result before & after refactoring #2 (replace conditional w/ polymorphism):
        for (String type : List.of("standard", "express", "overnight")) {
            assert shippingCost_before(type, 100) == shippingCost_after(type, 100);
        }

        System.out.println("All refactoring tests passed (behavior preserved).");
    }

    record Item(String name, int qty, double price) { }

    // ============================================================
    // REFACTORING 1 — EXTRACT METHOD. The "before" is a long method mixing
    // calculation, a magic discount, and formatting (a SMELL: long method + does
    // several things). "After" extracts named steps — smaller, testable, DRY.
    // ============================================================

    // BEFORE (smell: long, multi-purpose, magic number 500):
    static double receiptTotal_before(List<Item> items) {
        double total = 0;
        for (Item i : items) total += i.qty() * i.price();   // calc line total inline
        if (total > 500) total = total * 0.9;                // magic discount inline
        return total;
    }

    // AFTER (extracted, named, single-purpose helpers):
    static final double BULK_THRESHOLD = 500;
    static final double BULK_DISCOUNT = 0.10;

    static double receiptTotal_after(List<Item> items) {
        double subtotal = subtotal(items);
        return applyBulkDiscount(subtotal);
    }
    static double subtotal(List<Item> items) {
        return items.stream().mapToDouble(i -> i.qty() * i.price()).sum();
    }
    static double applyBulkDiscount(double amount) {
        return amount > BULK_THRESHOLD ? amount * (1 - BULK_DISCOUNT) : amount;
    }

    // ============================================================
    // REFACTORING 2 — REPLACE CONDITIONAL WITH POLYMORPHISM. The "before" is a
    // switch on a type string (a SMELL: switch/if on type — every new type edits
    // this method, violating Open/Closed). "After" makes each type a strategy, so
    // adding a type = adding a class, not editing the switch (Phases 2 & 5).
    // ============================================================

    // BEFORE (smell: type-switch; adding a type modifies this method):
    static double shippingCost_before(String type, double weight) {
        switch (type) {
            case "standard":  return weight * 0.5;
            case "express":   return weight * 1.0 + 10;
            case "overnight": return weight * 2.0 + 25;
            default: throw new IllegalArgumentException("unknown: " + type);
        }
    }

    // AFTER (each type is a Shipping strategy; open for extension):
    interface Shipping { double cost(double weight); }
    static Shipping shippingFor(String type) {
        return switch (type) {                       // one factory point (Phase 3)
            case "standard"  -> weight -> weight * 0.5;
            case "express"   -> weight -> weight * 1.0 + 10;
            case "overnight" -> weight -> weight * 2.0 + 25;
            default -> throw new IllegalArgumentException("unknown: " + type);
        };
    }
    static double shippingCost_after(String type, double weight) {
        return shippingFor(type).cost(weight);       // polymorphic dispatch, no conditional in the logic
    }

    // ----------------------------------------------------------------------------
    // The workflow (see NOTES): 1) have tests, 2) make a small structural change,
    // 3) re-run tests (green), 4) repeat. Never refactor and add features at once.
    // The tests here (assert before == after) are the safety net that makes the
    // structural changes provably behavior-preserving.
    // ----------------------------------------------------------------------------
}
