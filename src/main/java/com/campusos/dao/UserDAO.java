package com.campusos.dao;

import com.campusos.model.Role;
import com.campusos.model.User;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD and filtered searches for users.
 * All selects JOIN departments so the UI can show the department name directly.
 */
public class UserDAO {

    private static final String BASE_SELECT = """
            SELECT u.*, d.department_name
            FROM users u
            LEFT JOIN departments d ON d.department_id = u.department_id""";

    public User findById(Connection conn, long id) {
        return Sql.queryOne(conn, BASE_SELECT + " WHERE u.user_id = ?", UserDAO::map, id);
    }

    public User findByEmail(Connection conn, String email) {
        return Sql.queryOne(conn, BASE_SELECT + " WHERE u.email = ?", UserDAO::map, email);
    }

    /**
     * Filtered listing for the user management screen.
     * The WHERE clause is assembled dynamically from the non-null filters.
     */
    public List<User> findWithFilters(Connection conn, Role role, Long departmentId,
                                      String searchText, Boolean activeOnly) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (role != null) {
            sql.append(" AND u.role = ?");
            params.add(role.name());
        }
        if (departmentId != null) {
            sql.append(" AND u.department_id = ?");
            params.add(departmentId);
        }
        if (searchText != null && !searchText.isBlank()) {
            sql.append(" AND (u.full_name LIKE ? OR u.email LIKE ? OR u.identifier LIKE ?)");
            String like = "%" + searchText.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (activeOnly != null && activeOnly) {
            sql.append(" AND u.is_active = 1");
        }
        sql.append(" ORDER BY u.role, u.full_name");
        return Sql.query(conn, sql.toString(), UserDAO::map, params.toArray());
    }

    /** Faculty (optionally restricted to active accounts) used for complaint assignment. */
    public List<User> findByRole(Connection conn, Role role, boolean activeOnly) {
        String sql = BASE_SELECT + " WHERE u.role = ?" + (activeOnly ? " AND u.is_active = 1" : "")
                + " ORDER BY u.full_name";
        return Sql.query(conn, sql, UserDAO::map, role.name());
    }

    public boolean existsByEmail(Connection conn, String email, Long excludeId) {
        if (excludeId == null) {
            return Sql.queryLong(conn, "SELECT COUNT(*) FROM users WHERE email = ?", email) > 0;
        }
        return Sql.queryLong(conn,
                "SELECT COUNT(*) FROM users WHERE email = ? AND user_id <> ?", email, excludeId) > 0;
    }

    public boolean existsByIdentifier(Connection conn, String identifier, Long excludeId) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        if (excludeId == null) {
            return Sql.queryLong(conn, "SELECT COUNT(*) FROM users WHERE identifier = ?", identifier) > 0;
        }
        return Sql.queryLong(conn,
                "SELECT COUNT(*) FROM users WHERE identifier = ? AND user_id <> ?",
                identifier, excludeId) > 0;
    }

    public long countByRole(Connection conn, Role role) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM users WHERE role = ?", role.name());
    }

    public long countAll(Connection conn) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM users");
    }

    public long insert(Connection conn, User user) {
        return Sql.insert(conn, """
                INSERT INTO users (full_name, email, password_hash, role, department_id,
                                   identifier, phone, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                user.getFullName(), user.getEmail(), user.getPasswordHash(),
                user.getRole().name(), user.getDepartmentId(), user.getIdentifier(),
                user.getPhone(), user.isActive() ? 1 : 0);
    }

    public boolean update(Connection conn, User user) {
        return Sql.update(conn, """
                UPDATE users
                   SET full_name = ?, email = ?, role = ?, department_id = ?,
                       identifier = ?, phone = ?, is_active = ?
                 WHERE user_id = ?""",
                user.getFullName(), user.getEmail(), user.getRole().name(),
                user.getDepartmentId(), user.getIdentifier(), user.getPhone(),
                user.isActive() ? 1 : 0, user.getId()) > 0;
    }

    /** Profile self-service updates never change role or identifier. */
    public boolean updateProfile(Connection conn, User user) {
        return Sql.update(conn, """
                UPDATE users SET full_name = ?, email = ?, phone = ? WHERE user_id = ?""",
                user.getFullName(), user.getEmail(), user.getPhone(), user.getId()) > 0;
    }

    public boolean updatePasswordHash(Connection conn, long userId, String newHash) {
        return Sql.update(conn, "UPDATE users SET password_hash = ? WHERE user_id = ?",
                newHash, userId) > 0;
    }

    public boolean setActive(Connection conn, long userId, boolean active) {
        return Sql.update(conn, "UPDATE users SET is_active = ? WHERE user_id = ?",
                active ? 1 : 0, userId) > 0;
    }

    static User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("user_id"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(Role.valueOf(rs.getString("role")));
        long dept = rs.getLong("department_id");
        u.setDepartmentId(rs.wasNull() ? null : dept);
        u.setDepartmentName(rs.getString("department_name"));
        u.setIdentifier(rs.getString("identifier"));
        u.setPhone(rs.getString("phone"));
        u.setActive(rs.getBoolean("is_active"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        if (created != null) u.setCreatedAt(created.toLocalDateTime());
        if (updated != null) u.setUpdatedAt(updated.toLocalDateTime());
        return u;
    }
}
