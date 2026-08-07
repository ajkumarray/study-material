import java.util.List;

/*
 * Phase 1 — CLEAN CODE fundamentals
 * Run:  java -ea CleanCode.java
 *
 * "Clean code" = code optimized for READING (you read code far more than you
 * write it). Same behavior, but named well, small, and obvious. This file shows
 * clean versions; the messy "before" is in comments so you can compare.
 */
public class CleanCode {

    public static void main(String[] args) {
        var orders = List.of(
            new Order("food", 120, true),
            new Order("electronics", 2000, false),
            new Order("food", 80, true));

        assert totalShippableValue(orders) == 200;      // only shippable: 120 + 80
        assert describeOrder(orders.get(1)).contains("premium");
        assert !isEligibleForFreeShipping(new Order("food", 100, true));   // < threshold
        assert isEligibleForFreeShipping(new Order("food", 600, true));

        System.out.println("All clean-code tests passed.");
        System.out.println(describeOrder(orders.get(0)));
    }

    record Order(String category, double amount, boolean shippable) { }

    // ============================================================
    // 1. MEANINGFUL NAMES — reveal intent; no cryptic abbreviations.
    //
    // BEFORE:  double calc(List<Order> l) {
    //            double x = 0;
    //            for (Order o : l) if (o.shippable()) x += o.amount();
    //            return x;
    //          }
    // "calc" of what? "l"? "x"? The reader must decode it. AFTER: the names say it.
    // ============================================================
    static double totalShippableValue(List<Order> orders) {
        double total = 0;
        for (Order order : orders) {
            if (order.shippable()) total += order.amount();
        }
        return total;
    }

    // ============================================================
    // 2. SMALL, SINGLE-PURPOSE FUNCTIONS + NO MAGIC NUMBERS.
    //
    // BEFORE (does 3 things, magic 500):
    //   String describe(Order o) {
    //     String s = o.category();
    //     if (o.amount() > 500) s += " (premium)";        // what's 500?
    //     if (o.amount() > 500 && o.shippable()) s += " - free shipping";
    //     return s;
    //   }
    // AFTER: the constant is named, and the eligibility rule is its own function
    // (reused, testable, and reads like a sentence).
    // ============================================================
    static final double FREE_SHIPPING_THRESHOLD = 500;   // named -> intent + one place to change

    static boolean isEligibleForFreeShipping(Order order) {
        return order.shippable() && order.amount() > FREE_SHIPPING_THRESHOLD;
    }

    static String describeOrder(Order order) {
        String description = order.category();
        if (order.amount() > FREE_SHIPPING_THRESHOLD) description += " (premium)";
        if (isEligibleForFreeShipping(order)) description += " - free shipping";
        return description;
    }

    // ============================================================
    // 3. GUARD CLAUSES over deep nesting (fail fast; keep the happy path flat).
    //
    // BEFORE (arrow anti-pattern):
    //   String process(Order o) {
    //     if (o != null) {
    //       if (o.amount() > 0) {
    //         if (o.category() != null) {
    //           return "ok";
    //         } else return "no category";
    //       } else return "bad amount";
    //     } else return "null";
    //   }
    // AFTER: handle the edge cases first and return; the main logic isn't buried.
    // ============================================================
    static String process(Order order) {
        if (order == null) return "null order";
        if (order.amount() <= 0) return "invalid amount";
        if (order.category() == null) return "missing category";
        return "ok: " + order.category();               // the happy path, unindented
    }

    // ----------------------------------------------------------------------------
    // The rest of clean code (see NOTES): DRY (don't repeat yourself — the
    // eligibility rule lives in ONE place above), KISS, YAGNI, comments that
    // explain WHY (not what the code already says), consistent formatting, and
    // command-query separation. The theme: write for the next human to read it.
    // ----------------------------------------------------------------------------
}
