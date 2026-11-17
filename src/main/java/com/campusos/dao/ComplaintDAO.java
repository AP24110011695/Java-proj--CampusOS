package com.campusos.dao;

import com.campusos.model.Complaint;
import com.campusos.model.ComplaintCategory;
import com.campusos.model.ComplaintPriority;
import com.campusos.model.ComplaintStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Complaint persistence. Joins three references (submitter, assigned
 * faculty, department) so every screen gets ready-to-display names.
 */
public class ComplaintDAO {

    private static final String BASE_SELECT = """
            SELECT c.*, s.full_name AS submitter_name, s.email AS submitter_email,
                   d.department_name, a.full_name AS assigned_to_name
            FROM complaints c
            JOIN users s ON s.user_id = c.user_id
            LEFT JOIN departments d ON d.department_id = c.department_id
            LEFT JOIN users a ON a.user_id = c.assigned_to""";

    public Complaint findById(Connection conn, long id) {
        return Sql.queryOne(conn, BASE_SELECT + " WHERE c.complaint_id = ?", ComplaintDAO::map, id);
    }

    /** Admin complaint browser with every filter from the requirements. */
    public List<Complaint> findWithFilters(Connection conn, ComplaintStatus status,
                                           ComplaintCategory category, ComplaintPriority priority,
                                           Long departmentId, Long assignedToId,
                                           Long submitterId, String searchText) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (status != null) {
            sql.append(" AND c.status = ?");
            params.add(status.name());
        }
        if (category != null) {
            sql.append(" AND c.category = ?");
            params.add(category.name());
        }
        if (priority != null) {
            sql.append(" AND c.priority = ?");
            params.add(priority.name());
        }
        if (departmentId != null) {
            sql.append(" AND c.department_id = ?");
            params.add(departmentId);
        }
        if (assignedToId != null) {
            sql.append(" AND c.assigned_to = ?");
            params.add(assignedToId);
        }
        if (submitterId != null) {
            sql.append(" AND c.user_id = ?");
            params.add(submitterId);
        }
        if (searchText != null && !searchText.isBlank()) {
            sql.append(" AND (c.title LIKE ? OR c.description LIKE ?)");
            String like = "%" + searchText.trim() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY FIELD(c.priority, 'CRITICAL','HIGH','MEDIUM','LOW'), c.created_at DESC");
        return Sql.query(conn, sql.toString(), ComplaintDAO::map, params.toArray());
    }

    /** Complaints currently assigned to a faculty member. */
    public List<Complaint> findByAssignee(Connection conn, long facultyId) {
        return Sql.query(conn, BASE_SELECT
                        + " WHERE c.assigned_to = ? AND c.status IN ('ASSIGNED','IN_PROGRESS')"
                        + " ORDER BY FIELD(c.priority, 'CRITICAL','HIGH','MEDIUM','LOW'), c.created_at DESC",
                ComplaintDAO::map, facultyId);
    }

    /** Count of a faculty member's complaints in any set of statuses. */
    public long countByAssigneeAndStatuses(Connection conn, long facultyId,
                                           ComplaintStatus... statuses) {
        StringBuilder in = new StringBuilder();
        List<Object> params = new ArrayList<>(List.of(facultyId));
        for (ComplaintStatus status : statuses) {
            in.append("?,");
            params.add(status.name());
        }
        in.setLength(in.length() - 1);
        return Sql.queryLong(conn,
                "SELECT COUNT(*) FROM complaints WHERE assigned_to = ? AND status IN (" + in + ")",
                params.toArray());
    }

    public long countAll(Connection conn) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM complaints");
    }

    public long countByStatuses(Connection conn, ComplaintStatus... statuses) {
        StringBuilder in = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            if (i > 0) in.append(",");
            in.append("?");
            params.add(statuses[i].name());
        }
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM complaints WHERE status IN (" + in + ")",
                params.toArray());
    }

    public long insert(Connection conn, Complaint c) {
        return Sql.insert(conn, """
                INSERT INTO complaints (user_id, department_id, category, title, description,
                                        priority, status)
                VALUES (?, ?, ?, ?, ?, ?, 'SUBMITTED')""",
                c.getUserId(), c.getDepartmentId(), c.getCategory().name(), c.getTitle(),
                c.getDescription(), c.getPriority().name());
    }

    /** Applies a status transition (caller is responsible for validation + history row). */
    public boolean updateStatus(Connection conn, long complaintId, ComplaintStatus newStatus) {
        boolean resolved = newStatus == ComplaintStatus.RESOLVED;
        String sql = resolved
                ? "UPDATE complaints SET status = ?, resolved_at = NOW() WHERE complaint_id = ?"
                : "UPDATE complaints SET status = ? WHERE complaint_id = ?";
        return Sql.update(conn, sql, newStatus.name(), complaintId) > 0;
    }

    public boolean assign(Connection conn, long complaintId, Long assignedToId, ComplaintStatus newStatus) {
        return Sql.update(conn, "UPDATE complaints SET assigned_to = ?, status = ? WHERE complaint_id = ?",
                assignedToId, newStatus.name(), complaintId) > 0;
    }

    /** GROUP BY status for the complaints pie chart. */
    public List<com.campusos.model.CountStat> countGroupedByStatus(Connection conn) {
        return Sql.query(conn,
                "SELECT status, COUNT(*) AS total FROM complaints GROUP BY status ORDER BY total DESC",
                rs -> new com.campusos.model.CountStat(rs.getString("status"), rs.getLong("total")));
    }

    /** GROUP BY category for analytics. */
    public List<com.campusos.model.CountStat> countGroupedByCategory(Connection conn) {
        return Sql.query(conn,
                "SELECT category, COUNT(*) AS total FROM complaints GROUP BY category ORDER BY total DESC",
                rs -> new com.campusos.model.CountStat(rs.getString("category"), rs.getLong("total")));
    }

    static Complaint map(ResultSet rs) throws SQLException {
        Complaint c = new Complaint();
        c.setId(rs.getLong("complaint_id"));
        c.setUserId(rs.getLong("user_id"));
        c.setUserName(rs.getString("submitter_name"));
        c.setUserEmail(rs.getString("submitter_email"));
        long dept = rs.getLong("department_id");
        c.setDepartmentId(rs.wasNull() ? null : dept);
        c.setDepartmentName(rs.getString("department_name"));
        long assigned = rs.getLong("assigned_to");
        c.setAssignedToId(rs.wasNull() ? null : assigned);
        c.setAssignedToName(rs.getString("assigned_to_name"));
        c.setCategory(ComplaintCategory.valueOf(rs.getString("category")));
        c.setTitle(rs.getString("title"));
        c.setDescription(rs.getString("description"));
        c.setPriority(ComplaintPriority.valueOf(rs.getString("priority")));
        c.setStatus(ComplaintStatus.valueOf(rs.getString("status")));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) c.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) c.setUpdatedAt(updated.toLocalDateTime());
        Timestamp resolved = rs.getTimestamp("resolved_at");
        if (resolved != null) c.setResolvedAt(resolved.toLocalDateTime());
        return c;
    }
}
