package com.campusos.dao;

import com.campusos.model.AuditLog;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Audit trail persistence. Rows are only ever INSERTed here - there is no
 * update or delete method by design, keeping the log tamper-resistant.
 */
public class AuditLogDAO {

    private static final String BASE_SELECT = """
            SELECT a.*, u.full_name AS user_name
            FROM audit_logs a
            LEFT JOIN users u ON u.user_id = a.user_id""";

    public List<AuditLog> findWithFilters(Connection conn, String action, Long userId,
                                          String entityType, int limit) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            sql.append(" AND a.action LIKE ?");
            params.add("%" + action.trim().toUpperCase() + "%");
        }
        if (userId != null) {
            sql.append(" AND a.user_id = ?");
            params.add(userId);
        }
        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND a.entity_type = ?");
            params.add(entityType.trim().toUpperCase());
        }
        sql.append(" ORDER BY a.created_at DESC, a.audit_id DESC LIMIT ?");
        params.add(limit);
        return Sql.query(conn, sql.toString(), AuditLogDAO::map, params.toArray());
    }

    /** Personal activity feed for the current user's dashboard. */
    public List<AuditLog> findRecentByUser(Connection conn, long userId, int limit) {
        return Sql.query(conn, BASE_SELECT + " WHERE a.user_id = ? ORDER BY a.created_at DESC LIMIT ?",
                AuditLogDAO::map, userId, limit);
    }

    public long countAll(Connection conn) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM audit_logs");
    }

    public void insert(Connection conn, Long userId, String action, String entityType,
                       Long entityId, String description) {
        Sql.insert(conn, """
                INSERT INTO audit_logs (user_id, action, entity_type, entity_id, description)
                VALUES (?, ?, ?, ?, ?)""",
                userId, action, entityType, entityId, description);
    }

    static AuditLog map(ResultSet rs) throws SQLException {
        AuditLog a = new AuditLog();
        a.setId(rs.getLong("audit_id"));
        long uid = rs.getLong("user_id");
        a.setUserId(rs.wasNull() ? null : uid);
        a.setUserName(rs.getString("user_name"));
        a.setAction(rs.getString("action"));
        a.setEntityType(rs.getString("entity_type"));
        long eid = rs.getLong("entity_id");
        a.setEntityId(rs.wasNull() ? null : eid);
        a.setDescription(rs.getString("description"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) a.setCreatedAt(created.toLocalDateTime());
        return a;
    }
}
