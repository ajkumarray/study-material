import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/*
 * Phase 5 — BEHAVIORAL design patterns
 * Run:  java -ea BehavioralPatterns.java
 *
 * Behavioral patterns are about how objects COMMUNICATE and DISTRIBUTE
 * RESPONSIBILITY — algorithms, message passing, and control flow between objects.
 * You've already USED several of these (Strategy via lambdas, Template Method in
 * the Java capstone) — here they're named and formalized.
 */
public class BehavioralPatterns {

    public static void main(String[] args) {
        // Strategy — swap an algorithm at runtime
        assert new Cart(new PercentDiscount(10)).total(200) == 180;
        assert new Cart(new FlatDiscount(50)).total(200) == 150;
        assert new Cart(amount -> amount).total(200) == 200;   // a lambda IS a strategy

        // Observer — notify many dependents of a change
        var channel = new Channel();
        List<String> log = new ArrayList<>();
        channel.subscribe(msg -> log.add("A:" + msg));
        channel.subscribe(msg -> log.add("B:" + msg));
        channel.publish("hi");
        assert log.equals(List.of("A:hi", "B:hi"));            // both notified

        // Command — encapsulate a request as an object (enables undo/queue)
        var editor = new TextEditor();
        var history = new CommandHistory();
        history.execute(new TypeCommand(editor, "hello "));
        history.execute(new TypeCommand(editor, "world"));
        assert editor.text.toString().equals("hello world");
        history.undo();                                        // undo last command
        assert editor.text.toString().equals("hello ");

        // Template Method — fixed algorithm skeleton, customizable steps
        assert new CsvReport().generate().equals("HEADER\ndata,as,csv\nFOOTER");

        // State — behavior changes with internal state
        var doc = new Doc();
        assert doc.publishAction().equals("moved to review");   // Draft -> Review
        assert doc.publishAction().equals("published");         // Review -> Published

        // Chain of Responsibility — pass a request along handlers until one acts
        var chain = Handler.chain(new AuthHandler(), new ValidationHandler(), new BusinessHandler());
        assert chain.handle("valid-authed-request").equals("processed");
        assert chain.handle("bad").equals("rejected at validation");

        System.out.println("All behavioral-pattern tests passed.");
        demo();
    }

    // ============================================================
    // STRATEGY — define a family of interchangeable algorithms and select one at
    // runtime. In Java, a strategy is often just a lambda/functional interface
    // (you did this all through the Streams/functional phases). Open/Closed: add
    // a new strategy without touching the client.
    // ============================================================
    interface DiscountStrategy { double apply(double amount); }
    record PercentDiscount(double pct) implements DiscountStrategy {
        public double apply(double a) { return a * (1 - pct / 100); }
    }
    record FlatDiscount(double off) implements DiscountStrategy {
        public double apply(double a) { return Math.max(0, a - off); }
    }
    static class Cart {
        private final DiscountStrategy discount;      // the injected strategy
        Cart(DiscountStrategy discount) { this.discount = discount; }
        double total(double amount) { return discount.apply(amount); }
    }

    // ============================================================
    // OBSERVER — a subject maintains a list of observers and notifies them of
    // events (publish/subscribe). Decouples the source from the reactors.
    // (JDK: listeners; Java Phase / reactive streams; the DOM's addEventListener.)
    // ============================================================
    interface Subscriber { void onMessage(String msg); }
    static class Channel {
        private final List<Subscriber> subs = new ArrayList<>();
        void subscribe(Subscriber s) { subs.add(s); }
        void publish(String msg) { subs.forEach(s -> s.onMessage(msg)); }   // notify all
    }

    // ============================================================
    // COMMAND — encapsulate a request (action + data) as an object, so you can
    // parameterize, queue, log, and UNDO operations. (GUI actions, task queues,
    // transaction logs.)
    // ============================================================
    static class TextEditor { StringBuilder text = new StringBuilder(); }
    interface Command { void execute(); void undo(); }
    static class TypeCommand implements Command {
        private final TextEditor editor; private final String s;
        TypeCommand(TextEditor e, String s) { this.editor = e; this.s = s; }
        public void execute() { editor.text.append(s); }
        public void undo() { editor.text.setLength(editor.text.length() - s.length()); }
    }
    static class CommandHistory {
        private final java.util.Deque<Command> done = new java.util.ArrayDeque<>();
        void execute(Command c) { c.execute(); done.push(c); }
        void undo() { if (!done.isEmpty()) done.pop().undo(); }   // reverse the last command
    }

    // ============================================================
    // TEMPLATE METHOD — a base class defines the SKELETON of an algorithm in a
    // final method, deferring specific steps to subclasses. (You used this in the
    // Java capstone's payment flow; JUnit/Spring lifecycles are full of it.)
    // ============================================================
    static abstract class Report {
        final String generate() {                     // the FIXED skeleton
            return header() + "\n" + body() + "\n" + footer();
        }
        String header() { return "HEADER"; }          // default steps
        String footer() { return "FOOTER"; }
        abstract String body();                        // the subclass fills this in
    }
    static class CsvReport extends Report {
        String body() { return "data,as,csv"; }
    }

    // ============================================================
    // STATE — an object alters its behavior when its internal state changes; it
    // appears to change class. Replaces sprawling if/switch on a status field.
    // ============================================================
    static class Doc {
        private DocState state = new Draft();
        String publishAction() { String r = state.publish(this); return r; }
        void setState(DocState s) { this.state = s; }
    }
    interface DocState { String publish(Doc d); }
    static class Draft implements DocState {
        public String publish(Doc d) { d.setState(new Review()); return "moved to review"; }
    }
    static class Review implements DocState {
        public String publish(Doc d) { d.setState(new Published()); return "published"; }
    }
    static class Published implements DocState {
        public String publish(Doc d) { return "already published"; }
    }

    // ============================================================
    // CHAIN OF RESPONSIBILITY — pass a request along a chain of handlers; each
    // either handles it or forwards to the next. (Servlet filters, Spring Security
    // filter chain, middleware pipelines — System Design.)
    // ============================================================
    static abstract class Handler {
        protected Handler next;
        abstract String handle(String request);
        static Handler chain(Handler... handlers) {   // link them
            for (int i = 0; i < handlers.length - 1; i++) handlers[i].next = handlers[i + 1];
            return handlers[0];
        }
        String forward(String r) { return next != null ? next.handle(r) : "processed"; }
    }
    static class AuthHandler extends Handler {
        String handle(String r) { return r.contains("unauthed") ? "rejected at auth" : forward(r); }
    }
    static class ValidationHandler extends Handler {
        String handle(String r) { return r.contains("valid") ? forward(r) : "rejected at validation"; }
    }
    static class BusinessHandler extends Handler {
        String handle(String r) { return forward(r); }   // last -> "processed"
    }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("strategy: 10% off 200 = " + new Cart(new PercentDiscount(10)).total(200));
        System.out.println("template method:\n" + new CsvReport().generate());
        Function<Double, Double> tax = a -> a * 1.18;         // strategy as a plain function
        System.out.println("strategy-as-lambda: 100 + tax = " + tax.apply(100.0));
    }
}
