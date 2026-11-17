package com.campusos.dao;

import com.campusos.model.ComplaintStatus;
import com.campusos.model.ComplaintUpdate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/** Append-only history of complaint status changes (never updated or deleted). */
public class ComplaintUpdateDAO {

    /** Newest first, joined with the updater's name for the timeline view. */
    public List<ComplaintUpdate> findByComplaint(Connection conn, long complaintId) {
        String sql = """
                SELECT cu.*, u.full_name AS updated_by_name
                FROM complaint_updates cu
                JOIN users u ON u.user_id = cu.updated_by
                WHERE cu.complaint_id = ?
                ORDER BY cu.created_at DESC, cu.update_id DESC""";
        return Sql.query(conn, sql, ComplaintUpdateDAO::map, complaintId);
    }

    public long insert(Connection conn, ComplaintUpdate update) {
        return Sql.insert(conn, """
                INSERT INTO complaint_updates
                       (complaint_id, updated_by, previous_status, new_status, remarks)
                VALUES (?, ?, ?, ?, ?)""",
                update.getComplaintId(), update.getUpdatedById(),
                update.getPreviousStatus() == null ? null : update.getPreviousStatus().name(),
                update.getNewStatus().name(), update.getRemarks());
    }

    static ComplaintUpdate map(ResultSet rs) throws SQLException {
        ComplaintUpdate u = new ComplaintUpdate();
        u.setId(rs.getLong("update_id"));
        u.setComplaintId(rs.getLong("complaint_id"));
        u.setUpdatedById(rs.getLong("updated_by"));
        u.setUpdatedByName(rs.getString("updated_by_name"));
        String prev = rs.getString("previous_status");
        u.setPreviousStatus(prev == null ? null : ComplaintStatus.valueOf(prev));
        u.setNewStatus(ComplaintStatus.valueOf(rs.getString("new_status")));
        u.setRemarks(rs.getString("remarks"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) u.setCreatedAt(created.toLocalDateTime());
        return u;
    }
}
