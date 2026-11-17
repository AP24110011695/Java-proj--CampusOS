package com.campusos.dao;

import com.campusos.exception.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal JDBC toolkit shared by every DAO.
 *
 * Every method uses PreparedStatement with parameter binding, so all SQL is
 * immune to SQL injection. {@link #insert} demonstrates Statement.RETURN_GENERATED_KEYS
 * retrieval, the standard way to obtain MySQL AUTO_INCREMENT ids.
 */
public final class Sql {

    private Sql() { }

    /** Maps one ResultSet row to a model object. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public static <T> List<T> query(Connection conn, String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Query failed: " + e.getMessage(), e);
        }
    }

    /** Returns the first matching row or null. */
    public static <T> T queryOne(Connection conn, String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = query(conn, sql, mapper, params);
        return results.isEmpty() ? null : results.get(0);
    }

    /** Scalar aggregate query (COUNT/SUM/MAX). */
    public static long queryLong(Connection conn, String sql, Object... params) {
        Long value = queryOne(conn, sql, rs -> rs.getLong(1), params);
        return value == null ? 0L : value;
    }

    /** UPDATE / DELETE. Returns affected row count. */
    public static int update(Connection conn, String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Update failed: " + e.getMessage(), e);
        }
    }

    /** INSERT that returns the generated AUTO_INCREMENT primary key. */
    public static long insert(Connection conn, String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new DataAccessException("Insert succeeded but MySQL returned no generated key.");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Insert failed: " + e.getMessage(), e);
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
