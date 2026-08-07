import java.util.List;

/*
 * S — SINGLE RESPONSIBILITY PRINCIPLE (SRP)
 * "A class should have ONE reason to change."
 * Run:  java S_SingleResponsibility.java
 *
 * A class that does several unrelated jobs must change whenever ANY of those
 * jobs changes — and those changes risk breaking each other. Split by
 * responsibility so each class has a single axis of change.
 */
public class S_SingleResponsibility {

    public static void main(String[] args) {
        // AFTER: each collaborator has one job; the service orchestrates.
        var repo = new InMemoryEmployeeRepo();
        var payroll = new PayrollCalculator();
        var reporter = new PayslipFormatter();

        Employee e = new Employee("Ajay", 100_000, 0.10);
        repo.save(e);
        double net = payroll.netPay(e);
        System.out.println(reporter.format(e, net));
        System.out.println("stored employees: " + repo.count());
    }
}

/* ---- BEFORE (the anti-pattern, shown for contrast) --------------------------
   class Employee {
       String name; double salary;
       double calculatePay() { ... tax rules ... }      // reason to change #1: pay policy
       void save() { ... JDBC/SQL ... }                 // reason to change #2: persistence
       String toPayslip() { ... formatting ... }        // reason to change #3: report format
   }
   Three unrelated reasons to change live in one class. A tax-law tweak, a DB
   migration, and a report redesign all edit Employee — stepping on each other.
----------------------------------------------------------------------------- */

// AFTER: a plain data holder — its only reason to change is the employee model.
record Employee(String name, double grossSalary, double taxRate) { }

// Responsibility: pay calculation (changes when pay/tax POLICY changes).
class PayrollCalculator {
    double netPay(Employee e) {
        return e.grossSalary() * (1 - e.taxRate());
    }
}

// Responsibility: persistence (changes when STORAGE changes).
interface EmployeeRepository {
    void save(Employee e);
    int count();
}
class InMemoryEmployeeRepo implements EmployeeRepository {
    private final List<Employee> store = new java.util.ArrayList<>();
    public void save(Employee e) { store.add(e); }
    public int count() { return store.size(); }
}

// Responsibility: presentation (changes when the REPORT FORMAT changes).
class PayslipFormatter {
    String format(Employee e, double net) {
        return "Payslip[%s: gross=%.0f, net=%.0f]".formatted(e.name(), e.grossSalary(), net);
    }
}

/*
 * WHY IT'S BETTER:
 *  - Each class has ONE reason to change; edits are localized and safe.
 *  - Each is independently testable (test payroll math without a database).
 *  - Reusable and swappable (a new formatter or repo doesn't touch the others).
 * SRP is the foundation for the other four principles.
 */
