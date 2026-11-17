package com.campusos.dao;

import com.campusos.model.Department;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** CRUD + aggregate queries for the departments table. */
public class DepartmentDAO {

    /** JOIN with users to show how many people belong to each department. */
    public List<Department> findAll(Connection conn) {
        String sql = """
                SELECT d.*, COUNT(u.user_id) AS member_count
                FROM departments d
                LEFT JOIN users u ON u.department_id = d.department_id
                GROUP BY d.department_id
                ORDER BY d.department_name""";
        return Sql.query(conn, sql, rs -> {
            Department d = map(rs);
            d.setMemberCount(rs.getLong("member_count"));
            return d;
        });
    }

    public List<Department> findActive(Connection conn) {
        return Sql.query(conn,
                "SELECT * FROM departments WHERE is_active = 1 ORDER BY department_name",
                DepartmentDAO::map);
    }

    public Department findById(Connection conn, long id) {
        return Sql.queryOne(conn, "SELECT * FROM departments WHERE department_id = ?",
                DepartmentDAO::map, id);
    }

    public boolean existsByName(Connection conn, String name, Long excludeId) {
        if (excludeId == null) {
            return Sql.queryLong(conn,
                    "SELECT COUNT(*) FROM departments WHERE department_name = ?", name) > 0;
        }
        return Sql.queryLong(conn,
                "SELECT COUNT(*) FROM departments WHERE department_name = ? AND department_id <> ?",
                name, excludeId) > 0;
    }

    public boolean existsByCode(Connection conn, String code, Long excludeId) {
        if (excludeId == null) {
            return Sql.queryLong(conn,
                    "SELECT COUNT(*) FROM departments WHERE department_code = ?", code) > 0;
        }
        return Sql.queryLong(conn,
                "SELECT COUNT(*) FROM departments WHERE department_code = ? AND department_id <> ?",
                code, excludeId) > 0;
    }

    /** How many users reference this department (used before delete). */
    public long countUsers(Connection conn, long departmentId) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM users WHERE department_id = ?", departmentId);
    }

    public long insert(Connection conn, Department d) {
        return Sql.insert(conn, """
                INSERT INTO departments (department_name, department_code, description, is_active)
                VALUES (?, ?, ?, ?)""",
                d.getName(), d.getCode(), d.getDescription(), d.isActive() ? 1 : 0);
    }

    public boolean update(Connection conn, Department d) {
        return Sql.update(conn, """
                UPDATE departments
                   SET department_name = ?, department_code = ?, description = ?, is_active = ?
                 WHERE department_id = ?""",
                d.getName(), d.getCode(), d.getDescription(), d.isActive() ? 1 : 0, d.getId()) > 0;
    }

    public boolean setActive(Connection conn, long id, boolean active) {
        return Sql.update(conn, "UPDATE departments SET is_active = ? WHERE department_id = ?",
                active ? 1 : 0, id) > 0;
    }

    public boolean delete(Connection conn, long id) {
        return Sql.update(conn, "DELETE FROM departments WHERE department_id = ?", id) > 0;
    }

    static Department map(ResultSet rs) throws SQLException {
        Department d = new Department();
        d.setId(rs.getLong("department_id"));
        d.setName(rs.getString("department_name"));
        d.setCode(rs.getString("department_code"));
        d.setDescription(rs.getString("description"));
        d.setActive(rs.getBoolean("is_active"));
        java.sql.Timestamp created = rs.getTimestamp("created_at");
        java.sql.Timestamp updated = rs.getTimestamp("updated_at");
        if (created != null) d.setCreatedAt(created.toLocalDateTime());
        if (updated != null) d.setUpdatedAt(updated.toLocalDateTime());
        return d;
    }
}
