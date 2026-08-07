package dev.ajay.tracker.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ajay.tracker.DataAccessException;
import dev.ajay.tracker.domain.Category;
import dev.ajay.tracker.domain.Expense;

/**
 * JDBC implementation of {@link ExpenseRepository} (Phase 7.1). Every method
 * uses PreparedStatement (injection-safe), try-with-resources (3.1, no leaks),
 * and translates {@link SQLException} into {@link DataAccessException} (3.1).
 */
public class JdbcExpenseRepository implements ExpenseRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcExpenseRepository.class);

    private final DataSource dataSource;

    public JdbcExpenseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Expense save(Expense expense) {
        String sql = "INSERT INTO expense (description, amount, category, spent_on) VALUES (?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, expense.description());
            ps.setBigDecimal(2, expense.amount());
            ps.setString(3, expense.category().name());   // store enum name, not ordinal (2.6)
            ps.setObject(4, expense.spentOn());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                log.debug("saved expense id={} desc='{}'", id, expense.description());
                return expense.withId(id);
            }
        } catch (SQLException e) {
            throw new DataAccessException("failed to save expense: " + expense.description(), e);
        }
    }

    @Override
    public Optional<Expense> findById(long id) {
        String sql = "SELECT * FROM expense WHERE id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("failed to find expense id=" + id, e);
        }
    }

    @Override
    public List<Expense> findAll() {
        return query("SELECT * FROM expense ORDER BY spent_on DESC, id DESC", ps -> { });
    }

    @Override
    public List<Expense> findSince(LocalDate from) {
        return query("SELECT * FROM expense WHERE spent_on >= ? ORDER BY spent_on DESC, id DESC",
                ps -> ps.setObject(1, from));
    }

    @Override
    public boolean deleteById(long id) {
        String sql = "DELETE FROM expense WHERE id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("failed to delete expense id=" + id, e);
        }
    }

    @Override
    public long count() {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM expense")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new DataAccessException("failed to count expenses", e);
        }
    }

    // A tiny functional seam (Phase 4): callers supply how to bind params,
    // this method owns the connection/close/map/translate boilerplate once.
    private List<Expense> query(String sql, ParamBinder binder) {
        List<Expense> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("query failed: " + sql, e);
        }
        return out;
    }

    @FunctionalInterface
    private interface ParamBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    // The row->object mapping an ORM would automate (7.3).
    private Expense mapRow(ResultSet rs) throws SQLException {
        return new Expense(
                rs.getLong("id"),
                rs.getString("description"),
                rs.getBigDecimal("amount"),
                Category.valueOf(rs.getString("category")),
                rs.getObject("spent_on", LocalDate.class));
    }
}
