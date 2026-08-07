package dev.ajay.tracker.repository;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import dev.ajay.tracker.DataAccessException;

/** Builds a pooled {@link DataSource} and applies the schema (Phase 7.2). */
public final class Database {

    private Database() { }

    /** A HikariCP pool for the given JDBC URL, with the schema created. */
    public static DataSource pooled(String jdbcUrl) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(4);
        cfg.setPoolName("expense-pool");
        DataSource ds = new HikariDataSource(cfg);
        applySchema(ds);
        return ds;
    }

    private static void applySchema(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            String ddl = new String(Database.class.getResourceAsStream("/schema.sql").readAllBytes());
            st.execute(ddl);
        } catch (SQLException | IOException e) {
            throw new DataAccessException("failed to initialize schema", e);
        }
    }
}
