package dev.ajay.data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/*
 * Lesson 7.2 — Connection pooling & transactions
 *
 * Two production essentials JDBC gives you but doesn't do for you.
 */
public class PoolingAndTransactions {

    public static void main(String[] args) throws Exception {

        // ============================================================
        // 1. CONNECTION POOLING with HikariCP
        // ============================================================
        System.out.println("=== connection pool ===");

        // WHY pool: opening a DB connection is EXPENSIVE (TCP handshake,
        // auth, session setup — often 50-100ms). A web app doing that per
        // request would collapse. A pool keeps N connections OPEN and
        // hands them out; close() RETURNS to the pool, doesn't disconnect.
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:txlab;DB_CLOSE_DELAY=-1");
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(5);          // THE key tuning knob
        cfg.setPoolName("demo-pool");

        try (HikariDataSource pool = new HikariDataSource(cfg)) {
            setup(pool);

            // A DataSource is the modern replacement for DriverManager —
            // frameworks inject it; you just ask for connections.
            try (Connection c = pool.getConnection()) {
                System.out.println("got connection from pool: " + c.getClass().getSimpleName());
            }   // <- returns to pool, NOT closed
            System.out.println("pool size=" + pool.getMaximumPoolSize()
                    + ", idle now that we returned it");

            // ========================================================
            // 2. TRANSACTIONS — all-or-nothing (the ACID demo)
            // ========================================================
            System.out.println("\n=== transaction: commit ===");
            transfer(pool, "Ajay", "Ravi", new BigDecimal("300.00"));
            printBalances(pool);

            System.out.println("\n=== transaction: ROLLBACK on failure ===");
            try {
                // This transfer will fail HALFWAY (insufficient funds check
                // after the debit). Without a transaction, Ajay would lose
                // money that never reached Ravi — the canonical bug.
                transfer(pool, "Ravi", "Ajay", new BigDecimal("999999.00"));
            } catch (IllegalStateException e) {
                System.out.println("transfer failed: " + e.getMessage());
            }
            printBalances(pool);
            System.out.println("(balances UNCHANGED — the partial debit was rolled back)");
        }

        // ============================================================
        // 3. Isolation levels — the concurrency dial (reference)
        // ============================================================
        System.out.println("\n=== isolation levels ===");
        System.out.println("""
                Weaker -> stronger (more consistent, less concurrent):
                  READ_UNCOMMITTED : sees others' uncommitted writes (dirty reads)
                  READ_COMMITTED   : only committed data (Postgres default)
                  REPEATABLE_READ  : same row reads identically in a txn (MySQL default)
                  SERIALIZABLE     : as if transactions ran one-by-one (safest, slowest)
                The anomalies each prevents: dirty read -> non-repeatable read ->
                phantom read. Default READ_COMMITTED is right ~95% of the time.""");
    }

    /*
     * THE TRANSACTION PATTERN — memorize this shape:
     *   1. setAutoCommit(false)   turn OFF per-statement commits
     *   2. ... do the work ...
     *   3. commit()               make it all permanent, atomically
     *   4. catch -> rollback()    undo EVERYTHING on any failure
     *   5. finally -> restore autoCommit before returning to the pool
     */
    static void transfer(DataSource ds, String from, String to, BigDecimal amount) throws SQLException {
        Connection c = ds.getConnection();
        try {
            c.setAutoCommit(false);                       // BEGIN

            BigDecimal fromBalance = lockedBalance(c, from);
            debit(c, from, amount);                       // step 1 of 2

            if (fromBalance.compareTo(amount) < 0) {
                // Fail AFTER the debit on purpose, to prove rollback works:
                throw new IllegalStateException(from + " has insufficient funds");
            }

            credit(c, to, amount);                        // step 2 of 2
            c.commit();                                   // COMMIT: both, or neither
            System.out.println("  transferred " + amount + " from " + from + " to " + to);
        } catch (SQLException | IllegalStateException e) {
            c.rollback();                                 // ROLLBACK: undo the debit
            throw e;
        } finally {
            c.setAutoCommit(true);                        // reset before returning to pool
            c.close();                                    // -> back to pool
        }
    }

    static BigDecimal lockedBalance(Connection c, String owner) throws SQLException {
        // SELECT ... FOR UPDATE: lock the row so a concurrent transfer
        // can't read a stale balance and double-spend (pessimistic lock).
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT balance FROM account WHERE owner = ? FOR UPDATE")) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal("balance");
            }
        }
    }

    static void debit(Connection c, String owner, BigDecimal amount) throws SQLException {
        adjust(c, owner, amount.negate());
    }

    static void credit(Connection c, String owner, BigDecimal amount) throws SQLException {
        adjust(c, owner, amount);
    }

    static void adjust(Connection c, String owner, BigDecimal delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE account SET balance = balance + ? WHERE owner = ?")) {
            ps.setBigDecimal(1, delta);
            ps.setString(2, owner);
            ps.executeUpdate();
        }
    }

    static void setup(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(new String(PoolingAndTransactions.class
                    .getResourceAsStream("/schema.sql").readAllBytes()));
            st.execute("INSERT INTO account (owner, balance) VALUES ('Ajay', 1000.00), ('Ravi', 500.00)");
        }
    }

    static void printBalances(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT owner, balance FROM account ORDER BY owner")) {
            while (rs.next()) {
                System.out.println("  " + rs.getString("owner") + ": " + rs.getBigDecimal("balance"));
            }
        }
    }
}
