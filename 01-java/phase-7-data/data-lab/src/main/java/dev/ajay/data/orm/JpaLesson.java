package dev.ajay.data.orm;

import java.math.BigDecimal;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/*
 * Lesson 7.3 — JPA / Hibernate in action
 *
 * Compare EVERY operation here with the JDBC version (7.1): no SQL
 * strings, no ResultSet loops, no manual row->object mapping. You
 * manipulate OBJECTS; Hibernate emits the SQL (watch the Hibernate:
 * lines in the output — the generated SQL is right there).
 *
 * Core types:
 *   EntityManagerFactory — heavy, create ONCE per app (like a pool)
 *   EntityManager        — a persistence context / unit of work, short-lived
 *   EntityTransaction    — begin/commit/rollback (7.2's pattern, wrapped)
 */
public class JpaLesson {

    public static void main(String[] args) {

        // Reads persistence.xml, builds the schema (create-drop). Expensive —
        // done once. (Spring Boot creates this for you and injects the EM.)
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("datalab-pu");

        try {
            // ========================================================
            // 1. PERSIST — INSERT without writing INSERT
            // ========================================================
            System.out.println("\n=== persist ===");
            inTransaction(emf, em -> {
                em.persist(new Account("Ajay", new BigDecimal("1000.00")));
                em.persist(new Account("Ravi", new BigDecimal("500.00")));
                em.persist(new Account("Meera", new BigDecimal("2500.00")));
                // No id set — Hibernate fills it from the DB after insert.
            });

            // ========================================================
            // 2. FIND — SELECT by primary key
            // ========================================================
            System.out.println("\n=== find by id ===");
            inTransaction(emf, em -> {
                Account a = em.find(Account.class, 1L);   // SELECT ... WHERE id = 1
                System.out.println("  found: " + a);
            });

            // ========================================================
            // 3. DIRTY CHECKING — the ORM's party trick
            // ========================================================
            System.out.println("\n=== dirty checking (auto-UPDATE) ===");
            inTransaction(emf, em -> {
                Account a = em.find(Account.class, 1L);
                a.setBalance(a.getBalance().add(new BigDecimal("250.00")));
                // NO em.update() call exists or is needed! A MANAGED entity
                // is watched; at commit Hibernate DIFFS it against its
                // loaded snapshot and auto-issues UPDATE for what changed.
                System.out.println("  changed in memory; Hibernate will UPDATE at commit");
            });
            inTransaction(emf, em ->
                    System.out.println("  reloaded: " + em.find(Account.class, 1L)));

            // ========================================================
            // 4. JPQL — queries over ENTITIES, not tables
            // ========================================================
            System.out.println("\n=== JPQL ===");
            inTransaction(emf, em -> {
                // Note: 'Account' is the CLASS, 'balance' the FIELD — JPQL is
                // object-oriented SQL. Still parameterized (injection-safe).
                List<Account> rich = em.createQuery(
                        "SELECT a FROM Account a WHERE a.balance > :min ORDER BY a.balance DESC",
                        Account.class)
                        .setParameter("min", new BigDecimal("600.00"))
                        .getResultList();
                rich.forEach(a -> System.out.println("  rich: " + a));

                BigDecimal total = em.createQuery(
                        "SELECT SUM(a.balance) FROM Account a", BigDecimal.class)
                        .getSingleResult();
                System.out.println("  total across all accounts: " + total);
            });

            // ========================================================
            // 5. REMOVE — DELETE
            // ========================================================
            System.out.println("\n=== remove ===");
            inTransaction(emf, em -> {
                Account meera = em.find(Account.class, 3L);
                em.remove(meera);          // managed entity -> DELETE at commit
            });
            inTransaction(emf, em -> {
                Long count = em.createQuery("SELECT COUNT(a) FROM Account a", Long.class)
                        .getSingleResult();
                System.out.println("  accounts remaining: " + count);
            });

            System.out.println("""

                    === the JPA payoff, and its traps ===
                    WINS  : object-oriented, DB-portable (H2<->Postgres), dirty
                            checking, caching, lazy loading, relationship mapping.
                    TRAPS : - N+1 SELECT: loading a list then a query per element;
                              fix with JOIN FETCH / entity graphs.
                            - LazyInitializationException: touching a lazy relation
                              after the EntityManager closed.
                            - leaky abstraction: you STILL must know SQL to diagnose
                              the generated queries. ORM hides SQL; it doesn't excuse
                              not knowing it (which is why 7.1 came first).
                    Spring Data JPA (track 02) removes even this boilerplate:
                    declare 'interface AccountRepository extends JpaRepository<Account, Long>'
                    and findByBalanceGreaterThan(...) is IMPLEMENTED FOR YOU.""");
        } finally {
            emf.close();
        }
    }

    // Wraps 7.2's begin/commit/rollback pattern so the lessons stay focused.
    interface Work { void run(EntityManager em); }

    static void inTransaction(EntityManagerFactory emf, Work work) {
        EntityManager em = emf.createEntityManager();
        var tx = em.getTransaction();
        try {
            tx.begin();
            work.run(em);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }
}
