package dev.ajay.data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
 * Lesson 7.1 — SQL essentials + JDBC
 *
 * JDBC (Java Database Connectivity) = the standard Java API for talking
 * to ANY relational database. You code against java.sql interfaces; the
 * DRIVER (H2 here, Postgres in prod) implements them. Swap the driver +
 * URL, keep the code.
 *
 * The four core types:
 *   Connection        — a session with the database
 *   PreparedStatement — a precompiled, parameterized SQL statement
 *   ResultSet         — a cursor over query rows
 *   (DriverManager / DataSource — hands out Connections)
 *
 * Everything here is AutoCloseable -> try-with-resources (3.1) everywhere,
 * because a leaked Connection permanently starves the pool.
 */
public class JdbcLesson {

    // In-memory H2. "DB_CLOSE_DELAY=-1" keeps the DB alive while the JVM
    // runs; a file/Postgres URL would be the only change for persistence.
    static final String URL = "jdbc:h2:mem:datalab;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {

            createSchema(conn);

            // ========================================================
            // 1. INSERT — with generated keys
            // ========================================================
            System.out.println("=== INSERT ===");
            long ajayId = insertAccount(conn, "Ajay", new BigDecimal("1000.00"));
            long raviId = insertAccount(conn, "Ravi", new BigDecimal("50.00"));
            System.out.println("inserted accounts, ids " + ajayId + " and " + raviId);

            // ========================================================
            // 2. THE #1 SECURITY LESSON: PreparedStatement vs injection
            // ========================================================
            System.out.println("\n=== SQL injection ===");

            // A classic attack string. With string-concatenated SQL:
            //   "... WHERE owner = '" + evil + "'"
            // this would comment out the rest and dump/destroy data.
            String evil = "Ajay'; DROP TABLE account; --";
            Optional<BigDecimal> found = balanceOf(conn, evil);   // uses ? binding
            System.out.println("lookup of injection string -> " + found
                    + "  (harmless: ? treats it as DATA, not SQL)");
            // Proof the table still exists:
            System.out.println("account table intact: " + accountExists(conn));

            // ========================================================
            // 3. SELECT — mapping rows to objects
            // ========================================================
            System.out.println("\n=== SELECT ===");
            insertEntry(conn, ajayId, new BigDecimal("-200.00"), "groceries");
            insertEntry(conn, ajayId, new BigDecimal("5000.00"), "salary");
            insertEntry(conn, raviId, new BigDecimal("-10.00"), "chai");

            List<Entry> ajayEntries = entriesFor(conn, ajayId);
            ajayEntries.forEach(e -> System.out.println("  " + e));

            // ========================================================
            // 4. Aggregates + JOIN — let the DB do the work
            // ========================================================
            System.out.println("\n=== JOIN + GROUP BY ===");
            // Pushing computation to the DB beats pulling rows into Java
            // and summing them (less data over the wire, indexed engine).
            String sql = """
                    SELECT a.owner, COUNT(e.id) AS entries, COALESCE(SUM(e.amount), 0) AS net
                    FROM account a
                    LEFT JOIN ledger_entry e ON e.account_id = a.id
                    GROUP BY a.owner
                    ORDER BY a.owner
                    """;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    System.out.printf("  %-6s entries=%d net=%s%n",
                            rs.getString("owner"), rs.getInt("entries"), rs.getBigDecimal("net"));
                }
            }

            // ========================================================
            // 5. UPDATE / DELETE — executeUpdate returns rows affected
            // ========================================================
            System.out.println("\n=== UPDATE ===");
            int changed = adjustBalance(conn, ajayId, new BigDecimal("4800.00"));
            System.out.println("rows updated: " + changed
                    + ", new balance: " + balanceOf(conn, "Ajay").orElseThrow());
        }
    }

    static void createSchema(Connection conn) throws SQLException, java.io.IOException {
        String ddl = new String(JdbcLesson.class.getResourceAsStream("/schema.sql").readAllBytes());
        try (Statement st = conn.createStatement()) {
            st.execute(ddl);
        }
    }

    // RETURN_GENERATED_KEYS: get the auto-increment id the DB assigned.
    static long insertAccount(Connection conn, String owner, BigDecimal balance) throws SQLException {
        String sql = "INSERT INTO account (owner, balance) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, owner);        // parameters are 1-INDEXED (JDBC quirk)
            ps.setBigDecimal(2, balance);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    static void insertEntry(Connection conn, long accountId, BigDecimal amount, String note)
            throws SQLException {
        String sql = "INSERT INTO ledger_entry (account_id, amount, note) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setBigDecimal(2, amount);
            ps.setString(3, note);
            ps.executeUpdate();
        }
    }

    // ? placeholders: the driver sends SQL and data SEPARATELY, so data
    // can never be parsed as SQL. Injection-proof AND faster (the DB
    // caches the compiled plan across calls).
    static Optional<BigDecimal> balanceOf(Connection conn, String owner) throws SQLException {
        String sql = "SELECT balance FROM account WHERE owner = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getBigDecimal("balance")) : Optional.empty();
            }
        }
    }

    static List<Entry> entriesFor(Connection conn, long accountId) throws SQLException {
        String sql = "SELECT id, account_id, amount, note FROM ledger_entry WHERE account_id = ? ORDER BY id";
        List<Entry> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {          // the row-to-object mapping ORMs automate (7.3)
                    out.add(new Entry(
                            rs.getLong("id"),
                            rs.getLong("account_id"),
                            rs.getBigDecimal("amount"),
                            rs.getString("note")));
                }
            }
        }
        return out;
    }

    static int adjustBalance(Connection conn, long accountId, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE account SET balance = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newBalance);
            ps.setLong(2, accountId);
            return ps.executeUpdate();       // number of rows changed
        }
    }

    static boolean accountExists(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ACCOUNT'")) {
            rs.next();
            return rs.getInt(1) == 1;
        }
    }

    record Entry(long id, long accountId, BigDecimal amount, String note) { }
}
