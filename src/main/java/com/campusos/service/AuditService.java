package com.campusos.service;

import com.campusos.dao.AuditLogDAO;
import com.campusos.dao.Db;
import com.campusos.model.AuditLog;
import com.campusos.session.Session;

import java.util.List;

/**
 * Records important system activities in the append-only audit_logs table.
 * Called by every other service after successful state-changing operations.
 */
public class AuditService {

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    /** The user id is taken from the session; pass null only for system-level actions. */
    public void recordWithUserId(Long userId, String action, String entityType, Long entityId, String description) {
        Db.withConnectionVoid(conn ->
                auditLogDAO.insert(conn, userId, action, entityType, entityId, description));
    }

    /** Convenience overload using the current session user. */
    public void record(Session session, String action, String entityType, Long entityId, String description) {
        Long userId = session.isLoggedIn() ? session.currentUser().getId() : null;
        recordWithUserId(userId, action, entityType, entityId, description);
    }

    public List<AuditLog> recentByUser(long userId, int limit) {
        return Db.withConnection(conn -> auditLogDAO.findRecentByUser(conn, userId, limit));
    }
}
