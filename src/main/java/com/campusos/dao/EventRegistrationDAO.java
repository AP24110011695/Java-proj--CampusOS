package com.campusos.dao;

import com.campusos.model.EventRegistration;
import com.campusos.model.RegistrationStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Event registration persistence.
 * The UNIQUE KEY (event_id, user_id) in the schema is the database-level
 * guarantee against duplicate registrations; insert() surfaces a friendly
 * error when that constraint fires.
 */
public class EventRegistrationDAO {

    private static final String BASE_SELECT = """
            SELECT er.*, e.title AS event_title, e.start_time AS event_start_time, e.venue,
                   u.full_name AS user_name, u.email AS user_email
            FROM event_registrations er
            JOIN events e ON e.event_id = er.event_id
            JOIN users u ON u.user_id = er.user_id""";

    public EventRegistration findById(Connection conn, long registrationId) {
        return Sql.queryOne(conn, BASE_SELECT + " WHERE er.registration_id = ?",
                EventRegistrationDAO::map, registrationId);
    }

    /** Registration of one specific user for one event (may be CANCELLED). */
    public EventRegistration findSpecific(Connection conn, long eventId, long userId) {
        return Sql.queryOne(conn, BASE_SELECT + " WHERE er.event_id = ? AND er.user_id = ?",
                EventRegistrationDAO::map, eventId, userId);
    }

    /** Participant list for organizers (name, email, status, timestamps). */
    public List<EventRegistration> findByEvent(Connection conn, long eventId) {
        return Sql.query(conn, BASE_SELECT + " WHERE er.event_id = ? ORDER BY er.registered_at DESC",
                EventRegistrationDAO::map, eventId);
    }

    /** Every registration in the system - used by the CSV export. */
    public List<EventRegistration> findByEventAll(Connection conn) {
        return Sql.query(conn, BASE_SELECT + " ORDER BY e.start_time DESC, er.registered_at DESC",
                EventRegistrationDAO::map);
    }

    /** "My registrations" view with upcoming events first. */
    public List<EventRegistration> findByUser(Connection conn, long userId) {
        return Sql.query(conn, BASE_SELECT + " WHERE er.user_id = ? ORDER BY e.start_time DESC",
                EventRegistrationDAO::map, userId);
    }

    /** Number of registrations currently occupying seats. */
    public long countActive(Connection conn, long eventId) {
        return Sql.queryLong(conn, """
                SELECT COUNT(*) FROM event_registrations
                WHERE event_id = ? AND status IN ('REGISTERED','ATTENDED')""", eventId);
    }

    public long countAll(Connection conn) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM event_registrations");
    }

    public long insert(Connection conn, EventRegistration reg) {
        try {
            return Sql.insert(conn, """
                    INSERT INTO event_registrations (event_id, user_id, status)
                    VALUES (?, ?, 'REGISTERED')""",
                    reg.getEventId(), reg.getUserId());
        } catch (com.campusos.exception.DataAccessException e) {
            // MySQL error 1062 = duplicate key: the unique (event_id, user_id) constraint fired.
            if (e.getCause() instanceof java.sql.SQLException sqlEx
                    && sqlEx.getErrorCode() == 1062) {
                throw new com.campusos.exception.DuplicateRegistrationException(
                        "You are already registered for this event (duplicate registration blocked by the database).");
            }
            throw e;
        }
    }

    public boolean setStatus(Connection conn, long registrationId, RegistrationStatus status) {
        String sql = switch (status) {
            case CANCELLED -> "UPDATE event_registrations SET status = ?, cancelled_at = NOW() WHERE registration_id = ?";
            case ATTENDED -> "UPDATE event_registrations SET status = ?, attended_at = NOW() WHERE registration_id = ?";
            case REGISTERED -> "UPDATE event_registrations SET status = ?, cancelled_at = NULL, attended_at = NULL WHERE registration_id = ?";
        };
        return Sql.update(conn, sql, status.name(), registrationId) > 0;
    }

    /** Top events by active registrations for analytics charts. */
    public List<com.campusos.model.CountStat> topEventsByRegistrations(Connection conn, int limit) {
        String sql = """
                SELECT e.title, COUNT(er.registration_id) AS total
                FROM events e
                JOIN event_registrations er ON er.event_id = e.event_id
                     AND er.status IN ('REGISTERED','ATTENDED')
                GROUP BY e.event_id, e.title
                ORDER BY total DESC
                LIMIT ?""";
        return Sql.query(conn, sql,
                rs -> new com.campusos.model.CountStat(rs.getString("title"), rs.getLong("total")),
                limit);
    }

    static EventRegistration map(ResultSet rs) throws SQLException {
        EventRegistration r = new EventRegistration();
        r.setId(rs.getLong("registration_id"));
        r.setEventId(rs.getLong("event_id"));
        r.setEventTitle(rs.getString("event_title"));
        Timestamp eventStart = rs.getTimestamp("event_start_time");
        if (eventStart != null) r.setEventStartTime(eventStart.toLocalDateTime());
        r.setVenue(rs.getString("venue"));
        r.setUserId(rs.getLong("user_id"));
        r.setUserName(rs.getString("user_name"));
        r.setUserEmail(rs.getString("user_email"));
        r.setStatus(RegistrationStatus.valueOf(rs.getString("status")));
        Timestamp registered = rs.getTimestamp("registered_at");
        if (registered != null) r.setRegisteredAt(registered.toLocalDateTime());
        Timestamp cancelled = rs.getTimestamp("cancelled_at");
        if (cancelled != null) r.setCancelledAt(cancelled.toLocalDateTime());
        Timestamp attended = rs.getTimestamp("attended_at");
        if (attended != null) r.setAttendedAt(attended.toLocalDateTime());
        return r;
    }
}
