package com.campusos.dao;

import com.campusos.model.Booking;
import com.campusos.model.BookingStatus;
import com.campusos.model.ResourceType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Booking persistence. The heart of Module 2 is {@link #findConflicting},
 * which implements the overlap condition
 *
 *     newStart &lt; existingEnd AND newEnd &gt; existingStart
 *
 * against PENDING and APPROVED bookings of the same resource on the same
 * date, using SELECT ... FOR UPDATE so concurrent booking attempts are
 * serialized inside the caller's transaction.
 */
public class BookingDAO {

    private static final String BASE_SELECT = """
            SELECT b.*, r.resource_name, r.resource_type,
                   u.full_name AS user_name, u.email AS user_email,
                   d.full_name AS decided_by_name
            FROM bookings b
            JOIN resources r ON r.resource_id = b.resource_id
            JOIN users u ON u.user_id = b.user_id
            LEFT JOIN users d ON d.user_id = b.decided_by""";

    /** Conflicting active bookings for the same resource/date/time window. */
    public List<Booking> findConflicting(Connection conn, long resourceId, LocalDate date,
                                         LocalTime start, LocalTime end) {
        String sql = BASE_SELECT + "\n WHERE b.resource_id = ?"
                + "\n   AND b.booking_date = ?"
                + "\n   AND b.status IN ('PENDING', 'APPROVED')"
                + "\n   AND b.start_time < ?"
                + "\n   AND b.end_time   > ?"
                + "\n FOR UPDATE";
        return Sql.query(conn, sql, BookingDAO::map, resourceId, Date.valueOf(date),
                Time.valueOf(end), Time.valueOf(start));
    }

    public Booking findById(Connection conn, long id) {
        return Sql.queryOne(conn, BASE_SELECT + " WHERE b.booking_id = ?", BookingDAO::map, id);
    }

    /** Filtered listing for the admin booking management screen. */
    public List<Booking> findWithFilters(Connection conn, BookingStatus status, Long resourceId,
                                         Long userId, LocalDate date, String searchText) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (status != null) {
            sql.append(" AND b.status = ?");
            params.add(status.name());
        }
        if (resourceId != null) {
            sql.append(" AND b.resource_id = ?");
            params.add(resourceId);
        }
        if (userId != null) {
            sql.append(" AND b.user_id = ?");
            params.add(userId);
        }
        if (date != null) {
            sql.append(" AND b.booking_date = ?");
            params.add(Date.valueOf(date));
        }
        if (searchText != null && !searchText.isBlank()) {
            sql.append(" AND (u.full_name LIKE ? OR u.email LIKE ? OR b.purpose LIKE ?)");
            String like = "%" + searchText.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY b.booking_date DESC, b.start_time DESC, b.booking_id DESC");
        return Sql.query(conn, sql.toString(), BookingDAO::map, params.toArray());
    }

    /** Personal booking history for students and faculty dashboards. */
    public List<Booking> findByUser(Connection conn, long userId, BookingStatus status) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE b.user_id = ?");
        List<Object> params = new ArrayList<>(List.of(userId));
        if (status != null) {
            sql.append(" AND b.status = ?");
            params.add(status.name());
        }
        sql.append(" ORDER BY b.booking_date DESC, b.start_time DESC");
        return Sql.query(conn, sql.toString(), BookingDAO::map, params.toArray());
    }

    /** Newest requests for dashboard "recent activity" panels. */
    public List<Booking> findRecent(Connection conn, int limit) {
        return Sql.query(conn, BASE_SELECT + " ORDER BY b.requested_at DESC LIMIT ?",
                BookingDAO::map, limit);
    }

    public long countAll(Connection conn) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM bookings");
    }

    public long countByStatus(Connection conn, BookingStatus status) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM bookings WHERE status = ?", status.name());
    }

    public long insert(Connection conn, Booking b) {
        return Sql.insert(conn, """
                INSERT INTO bookings (resource_id, user_id, booking_date, start_time, end_time,
                                      purpose, status, requested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())""",
                b.getResourceId(), b.getUserId(), Date.valueOf(b.getBookingDate()),
                Time.valueOf(b.getStartTime()), Time.valueOf(b.getEndTime()),
                b.getPurpose(), b.getStatus().name());
    }

    /** Sets a decision (approve/reject) with who decided and when. */
    public boolean markDecision(Connection conn, long bookingId, BookingStatus status,
                                long decidedBy, String remarks) {
        return Sql.update(conn, """
                UPDATE bookings
                   SET status = ?, decided_by = ?, decided_at = NOW(), decision_remarks = ?
                 WHERE booking_id = ?""",
                status.name(), decidedBy, remarks, bookingId) > 0;
    }

    public boolean markCancelled(Connection conn, long bookingId) {
        return Sql.update(conn,
                "UPDATE bookings SET status = 'CANCELLED', cancelled_at = NOW() WHERE booking_id = ?",
                bookingId) > 0;
    }

    public boolean markCompleted(Connection conn, long bookingId) {
        return Sql.update(conn,
                "UPDATE bookings SET status = 'COMPLETED' WHERE booking_id = ?", bookingId) > 0;
    }

    /** GROUP BY status for analytics charts. */
    public List<com.campusos.model.CountStat> countGroupedByStatus(Connection conn) {
        return Sql.query(conn,
                "SELECT status, COUNT(*) AS total FROM bookings GROUP BY status ORDER BY total DESC",
                rs -> new com.campusos.model.CountStat(rs.getString("status"), rs.getLong("total")));
    }

    /** Monthly booking trend over the trailing N months (GROUP BY month). */
    public List<com.campusos.model.CountStat> monthlyTrend(Connection conn, int months) {
        String sql = """
                SELECT DATE_FORMAT(booking_date, '%b %Y') AS month_label,
                       DATE_FORMAT(booking_date, '%Y-%m') AS month_key,
                       COUNT(*) AS total
                FROM bookings
                WHERE booking_date >= DATE_SUB(CURDATE(), INTERVAL ? MONTH)
                GROUP BY month_key, month_label
                ORDER BY month_key""";
        return Sql.query(conn, sql,
                rs -> new com.campusos.model.CountStat(rs.getString("month_label"), rs.getLong("total")),
                months);
    }

    static Booking map(ResultSet rs) throws SQLException {
        Booking b = new Booking();
        b.setId(rs.getLong("booking_id"));
        b.setResourceId(rs.getLong("resource_id"));
        b.setResourceName(rs.getString("resource_name"));
        b.setResourceType(ResourceType.valueOf(rs.getString("resource_type")));
        b.setUserId(rs.getLong("user_id"));
        b.setUserName(rs.getString("user_name"));
        b.setUserEmail(rs.getString("user_email"));
        Date date = rs.getDate("booking_date");
        b.setBookingDate(date == null ? null : date.toLocalDate());
        Time start = rs.getTime("start_time");
        b.setStartTime(start == null ? null : start.toLocalTime());
        Time end = rs.getTime("end_time");
        b.setEndTime(end == null ? null : end.toLocalTime());
        b.setPurpose(rs.getString("purpose"));
        b.setStatus(BookingStatus.valueOf(rs.getString("status")));
        Timestamp requested = rs.getTimestamp("requested_at");
        if (requested != null) b.setRequestedAt(requested.toLocalDateTime());
        Timestamp decided = rs.getTimestamp("decided_at");
        if (decided != null) b.setDecidedAt(decided.toLocalDateTime());
        long decider = rs.getLong("decided_by");
        b.setDecidedById(rs.wasNull() ? null : decider);
        b.setDecidedByName(rs.getString("decided_by_name"));
        b.setDecisionRemarks(rs.getString("decision_remarks"));
        Timestamp cancelled = rs.getTimestamp("cancelled_at");
        if (cancelled != null) b.setCancelledAt(cancelled.toLocalDateTime());
        return b;
    }
}
