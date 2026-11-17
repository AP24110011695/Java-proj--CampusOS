package com.campusos.dao;

import com.campusos.config.DatabaseConfig;
import com.campusos.exception.DataAccessException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Central JDBC helper.
 *
 * - {@link #withConnection} runs a unit of work on a freshly opened connection
 *   and closes it afterwards (never leak connections).
 * - {@link #withTransaction} wraps the work in a MySQL transaction:
 *   autoCommit is disabled, the work runs, then COMMIT - or ROLLBACK if any
 *   exception escapes. This is how booking conflicts and event capacity
 *   checks stay race-free between concurrent users.
 */
public final class Db {

    private Db() { }

    /** Functional interface whose single method may throw SQLException. */
    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    public static <T> T withConnection(SqlFunction<T> work) {
        try (Connection conn = DatabaseConfig.get().newConnection()) {
            return work.apply(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    public static void withConnectionVoid(SqlConsumer work) {
        withConnection(conn -> {
            work.accept(conn);
            return null;
        });
    }

    public static <T> T withTransaction(SqlFunction<T> work) {
        try (Connection conn = DatabaseConfig.get().newConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = work.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new DataAccessException("Transaction rolled back: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    public static void withTransactionVoid(SqlConsumer work) {
        withTransaction(conn -> {
            work.accept(conn);
            return null;
        });
    }
}
