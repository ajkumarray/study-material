import java.util.ArrayList;
import java.util.List;

/*
 * Software Design CAPSTONE — refactoring a GOD CLASS, guarded by tests.
 * Run:  java -ea OrderRefactor.java
 *
 * The "before" (GodOrderProcessor) does EVERYTHING: pricing, discount rules,
 * tax, persistence, notification, and formatting — a textbook god class
 * violating SRP, OCP, DIP, and riddled with smells (long method, switch-on-type,
 * magic numbers, mixed concerns). The "after" applies the whole track: SOLID +
 * Strategy + DIP/ports + clean code. The SAME tests pass against BOTH, proving
 * the refactor preserved behavior — the discipline of Phase 7.
 */
public class OrderRefactor {

    record Line(String sku, int qty, double price) { }

    public static void main(String[] args) {
        var order = List.of(new Line("book", 2, 30), new Line("pen", 5, 2));  // subtotal 70

        // ---- The SAME assertions hold for the god class AND the refactored design ----
        // 'gold' (VIP) customer: 10% discount, 18% tax.
        double before = new GodOrderProcessor().process(order, "gold");
        double after  = refactoredProcess(order, "gold");
        assert before == after : "refactor changed behavior! " + before + " vs " + after;
        assert after == round((70 * 0.90) * 1.18);        // discount then tax

        // 'regular' customer: no discount.
        assert new GodOrderProcessor().process(order, "regular") == refactoredProcess(order, "regular");

        System.out.println("Capstone: god class refactored, behavior preserved.");
        System.out.printf("  gold total = %.2f, regular total = %.2f%n",
                after, refactoredProcess(order, "regular"));
    }

    static double round(double v) { return Math.round(v * 100) / 100.0; }

    // ============================================================================
    // BEFORE — the GOD CLASS. One method does pricing + discounts + tax +
    // persistence + notification + formatting. Adding a customer tier or a tax
    // rule means editing this method (OCP violation); you can't test pricing
    // without triggering "persistence" and "email" (no DIP); magic numbers
    // everywhere; it has many reasons to change (SRP violation).
    // ============================================================================
    static class GodOrderProcessor {
        double process(List<Line> lines, String customerType) {
            // 1. subtotal (pricing concern)
            double subtotal = 0;
            for (Line l : lines) subtotal += l.qty() * l.price();
            // 2. discount (business-rule concern, switch-on-type smell + magic numbers)
            double discounted = subtotal;
            switch (customerType) {
                case "gold":   discounted = subtotal * 0.90; break;
                case "silver": discounted = subtotal * 0.95; break;
                case "regular": break;
                default: throw new IllegalArgumentException("unknown type");
            }
            // 3. tax (another concern, magic 0.18)
            double total = discounted * 1.18;
            // 4. persistence + 5. notification (side effects tangled into the calc)
            //    saveToDatabase(order); sendEmail(...);   <-- can't unit-test around these
            // 6. formatting (yet another concern)
            return Math.round(total * 100) / 100.0;
        }
    }

    // ============================================================================
    // AFTER — decomposed by responsibility (SRP), extensible (OCP/Strategy),
    // depending on abstractions (DIP/ports), with named constants (clean code).
    // Each piece is independently testable. Adding a tier/tax = a new small class,
    // not an edit to a mega-method.
    // ============================================================================
    static final double TAX_RATE = 0.18;

    // SRP: pricing is one job.
    static class PricingService {
        double subtotal(List<Line> lines) {
            return lines.stream().mapToDouble(l -> l.qty() * l.price()).sum();
        }
    }
    // STRATEGY + OCP: each discount rule is a class; add a tier without editing others.
    interface DiscountPolicy { double apply(double subtotal); }
    static DiscountPolicy discountFor(String type) {
        return switch (type) {                              // one small factory point (Phase 3)
            case "gold"    -> s -> s * 0.90;
            case "silver"  -> s -> s * 0.95;
            case "regular" -> s -> s;
            default -> throw new IllegalArgumentException("unknown type: " + type);
        };
    }
    // SRP: tax is its own concern.
    static class TaxCalculator {
        double withTax(double amount) { return amount * (1 + TAX_RATE); }
    }
    // DIP: side effects behind PORTS (interfaces), injected — so the domain is
    // testable with fakes and infra swaps freely (hexagonal, Phase 6).
    interface OrderRepository { void save(double total); }
    interface Notifier { void notify(String msg); }

    // The orchestrator composes the focused collaborators (small, readable).
    static class OrderService {
        private final PricingService pricing = new PricingService();
        private final TaxCalculator tax = new TaxCalculator();
        private final OrderRepository repo;
        private final Notifier notifier;
        OrderService(OrderRepository repo, Notifier notifier) { this.repo = repo; this.notifier = notifier; }

        double checkout(List<Line> lines, String customerType) {
            double subtotal = pricing.subtotal(lines);
            double discounted = discountFor(customerType).apply(subtotal);
            double total = round(tax.withTax(discounted));
            repo.save(total);                               // side effects, isolated
            notifier.notify("order total " + total);
            return total;
        }
    }

    // helper used by the tests — wires fakes into the refactored service.
    static double refactoredProcess(List<Line> lines, String type) {
        var saved = new ArrayList<Double>();
        var service = new OrderService(saved::add, msg -> { });   // fake repo + notifier
        double total = service.checkout(lines, type);
        assert saved.size() == 1 && saved.get(0) == total;        // side effect happened once
        return total;
    }

    // ----------------------------------------------------------------------------
    // WHAT EACH STEP FIXED (see CAPSTONE writeup below in this file's dir):
    //   god class          -> split into Pricing/Discount/Tax/Repo/Notifier   (SRP)
    //   switch-on-type      -> DiscountPolicy strategy per tier                (OCP + Strategy)
    //   tangled side effects-> repository + notifier PORTS, injected           (DIP + hexagonal)
    //   magic numbers       -> named TAX_RATE constant                         (clean code)
    //   untestable          -> each piece tested in isolation w/ fakes         (testability)
    // The tests (before == after) prove every change preserved behavior (Phase 7).
    // ----------------------------------------------------------------------------
}
