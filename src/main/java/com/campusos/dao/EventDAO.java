package com.campusos.dao;

import com.campusos.model.Event;
import com.campusos.model.EventStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Event persistence. Listing queries aggregate the number of seat-occupying
 * registrations (REGISTERED + ATTENDED) with LEFT JOIN + GROUP BY so the UI
 * can show live capacity usage.
 */
public class EventDAO {

    private static final String BASE_SELECT = """
            SELECT e.*, u.full_name AS organizer_name, d.department_name,
                   COALESCE(reg.active_count, 0) AS registered_count,
                   COALESCE(reg.attended_count, 0) AS attended_count
            FROM events e
            JOIN users u ON u.user_id = e.created_by
            LEFT JOIN departments d ON d.department_id = e.department_id
            LEFT JOIN (
                SELECT event_id,
                       SUM(status IN ('REGISTERED','ATTENDED')) AS active_count,
                       SUM(status = 'ATTENDED') AS attended_count
                FROM event_registrations
                GROUP BY event_id
            ) reg ON reg.event_id = e.event_id""";

    public Event findById(Connection conn, long id) {
        return Sql.queryOne(conn, BASE_SELECT + " WHERE e.event_id = ?", EventDAO::map, id);
    }

    /**
     * @param upcomingOnly when true, only UPCOMING events ordered by start time
     * @param organizerId  when non-null, restricts to events created by that user
     */
    public List<Event> findWithFilters(Connection conn, EventStatus status, Long organizerId,
                                       String searchText, boolean upcomingOnly) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (upcomingOnly && status == null) {
            sql.append(" AND e.status = 'UPCOMING'");
        } else if (status != null) {
            sql.append(" AND e.status = ?");
            params.add(status.name());
        }
        if (organizerId != null) {
            sql.append(" AND e.created_by = ?");
            params.add(organizerId);
        }
        if (searchText != null && !searchText.isBlank()) {
            sql.append(" AND (e.title LIKE ? OR e.venue LIKE ? OR e.description LIKE ?)");
            String like = "%" + searchText.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(upcomingOnly
                ? " ORDER BY e.start_time ASC"
                : " ORDER BY e.start_time DESC");
        return Sql.query(conn, sql.toString(), EventDAO::map, params.toArray());
    }

    public long countAll(Connection conn) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM events");
    }

    public long countByStatus(Connection conn, EventStatus status) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM events WHERE status = ?", status.name());
    }

    /** Locks the event row - the caller's transaction serializes registrations on it. */
    public Event findByIdForUpdate(Connection conn, long id) {
        String sql = """
                SELECT e.*, u.full_name AS organizer_name, d.department_name,
                       COALESCE(reg.active_count, 0) AS registered_count,
                       COALESCE(reg.attended_count, 0) AS attended_count
                FROM events e
                JOIN users u ON u.user_id = e.created_by
                LEFT JOIN departments d ON d.department_id = e.department_id
                LEFT JOIN (
                    SELECT event_id,
                           SUM(status IN ('REGISTERED','ATTENDED')) AS active_count,
                           SUM(status = 'ATTENDED') AS attended_count
                    FROM event_registrations
                    GROUP BY event_id
                ) reg ON reg.event_id = e.event_id
                WHERE e.event_id = ?
                FOR UPDATE""";
        return Sql.queryOne(conn, sql, EventDAO::map, id);
    }

    public long insert(Connection conn, Event e) {
        return Sql.insert(conn, """
                INSERT INTO events (title, description, venue, start_time, end_time,
                                    registration_deadline, max_participants, created_by,
                                    department_id, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'UPCOMING')""",
                e.getTitle(), e.getDescription(), e.getVenue(),
                Timestamp.valueOf(e.getStartTime()), Timestamp.valueOf(e.getEndTime()),
                Timestamp.valueOf(e.getRegistrationDeadline()), e.getMaxParticipants(),
                e.getCreatedById(), e.getDepartmentId());
    }

    public boolean update(Connection conn, Event e) {
        return Sql.update(conn, """
                UPDATE events
                   SET title = ?, description = ?, venue = ?, start_time = ?, end_time = ?,
                       registration_deadline = ?, max_participants = ?, department_id = ?
                 WHERE event_id = ?""",
                e.getTitle(), e.getDescription(), e.getVenue(),
                Timestamp.valueOf(e.getStartTime()), Timestamp.valueOf(e.getEndTime()),
                Timestamp.valueOf(e.getRegistrationDeadline()), e.getMaxParticipants(),
                e.getDepartmentId(), e.getId()) > 0;
    }

    public boolean setStatus(Connection conn, long eventId, EventStatus status) {
        return Sql.update(conn, "UPDATE events SET status = ? WHERE event_id = ?",
                status.name(), eventId) > 0;
    }

    /** GROUP BY status for event analytics. */
    public List<com.campusos.model.CountStat> countGroupedByStatus(Connection conn) {
        return Sql.query(conn,
                "SELECT status, COUNT(*) AS total FROM events GROUP BY status ORDER BY total DESC",
                rs -> new com.campusos.model.CountStat(rs.getString("status"), rs.getLong("total")));
    }

    static Event map(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setId(rs.getLong("event_id"));
        e.setTitle(rs.getString("title"));
        e.setDescription(rs.getString("description"));
        e.setVenue(rs.getString("venue"));
        Timestamp start = rs.getTimestamp("start_time");
        if (start != null) e.setStartTime(start.toLocalDateTime());
        Timestamp end = rs.getTimestamp("end_time");
        if (end != null) e.setEndTime(end.toLocalDateTime());
        Timestamp deadline = rs.getTimestamp("registration_deadline");
        if (deadline != null) e.setRegistrationDeadline(deadline.toLocalDateTime());
        e.setMaxParticipants(rs.getInt("max_participants"));
        e.setCreatedById(rs.getLong("created_by"));
        e.setCreatedByName(rs.getString("organizer_name"));
        long dept = rs.getLong("department_id");
        e.setDepartmentId(rs.wasNull() ? null : dept);
        e.setDepartmentName(rs.getString("department_name"));
        e.setStatus(EventStatus.valueOf(rs.getString("status")));
        e.setRegisteredCount(rs.getInt("registered_count"));
        e.setAttendedCount(rs.getInt("attended_count"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) e.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) e.setUpdatedAt(updated.toLocalDateTime());
        return e;
    }
}
