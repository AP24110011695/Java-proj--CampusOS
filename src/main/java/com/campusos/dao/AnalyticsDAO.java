package com.campusos.dao;

import com.campusos.model.CountStat;
import com.campusos.model.DashboardStats;

import java.sql.Connection;
import java.util.List;

/**
 * Aggregate analytics queries for dashboards and charts.
 * Demonstrates COUNT, SUM, GROUP BY, ORDER BY, subqueries and JOINs -
 * every number shown in the UI comes from these live MySQL queries.
 */
public class AnalyticsDAO {

    private final DepartmentDAO departmentDAO = new DepartmentDAO();

    /** All admin dashboard counters in one pass over one connection. */
    public DashboardStats loadDashboardStats(Connection conn) {
        DashboardStats s = new DashboardStats();

        // Scalar subqueries: one round-trip computing every total.
        String totalsSql = """
                SELECT
                  (SELECT COUNT(*) FROM users WHERE role = 'STUDENT')                                   AS students,
                  (SELECT COUNT(*) FROM users WHERE role = 'FACULTY')                                   AS faculty,
                  (SELECT COUNT(*) FROM users WHERE role = 'ADMIN')                                     AS admins,
                  (SELECT COUNT(*) FROM users)                                                          AS users,
                  (SELECT COUNT(*) FROM departments)                                                    AS departments,
                  (SELECT COUNT(*) FROM resources)                                                      AS resources,
                  (SELECT COUNT(*) FROM bookings)                                                       AS bookings,
                  (SELECT COUNT(*) FROM bookings WHERE status = 'PENDING')                              AS pending_bookings,
                  (SELECT COUNT(*) FROM bookings WHERE status = 'APPROVED')                             AS approved_bookings,
                  (SELECT COUNT(*) FROM complaints)                                                     AS complaints,
                  (SELECT COUNT(*) FROM complaints
                     WHERE status IN ('SUBMITTED','ASSIGNED','IN_PROGRESS'))                            AS pending_complaints,
                  (SELECT COUNT(*) FROM complaints
                     WHERE status IN ('RESOLVED','CLOSED'))                                             AS resolved_complaints,
                  (SELECT COUNT(*) FROM events)                                                         AS events,
                  (SELECT COUNT(*) FROM events WHERE status = 'UPCOMING')                               AS upcoming_events,
                  (SELECT COUNT(*) FROM event_registrations
                     WHERE status IN ('REGISTERED','ATTENDED'))                                         AS registrations,
                  (SELECT COUNT(*) FROM audit_logs)                                                     AS audit_entries""";

        return Sql.queryOne(conn, totalsSql, rs -> {
            s.setTotalStudents(rs.getLong("students"));
            s.setTotalFaculty(rs.getLong("faculty"));
            s.setTotalAdmins(rs.getLong("admins"));
            s.setTotalUsers(rs.getLong("users"));
            s.setTotalDepartments(rs.getLong("departments"));
            s.setTotalResources(rs.getLong("resources"));
            s.setTotalBookings(rs.getLong("bookings"));
            s.setPendingBookings(rs.getLong("pending_bookings"));
            s.setApprovedBookings(rs.getLong("approved_bookings"));
            s.setTotalComplaints(rs.getLong("complaints"));
            s.setPendingComplaints(rs.getLong("pending_complaints"));
            s.setResolvedComplaints(rs.getLong("resolved_complaints"));
            s.setTotalEvents(rs.getLong("events"));
            s.setUpcomingEvents(rs.getLong("upcoming_events"));
            s.setTotalRegistrations(rs.getLong("registrations"));
            s.setAuditEntries(rs.getLong("audit_entries"));
            return s;
        });
    }

    /** Department-wise user distribution (LEFT JOIN keeps empty departments). */
    public List<CountStat> usersByDepartment(Connection conn) {
        return Sql.query(conn, """
                SELECT d.department_name AS label, COUNT(u.user_id) AS total
                FROM departments d
                LEFT JOIN users u ON u.department_id = d.department_id
                GROUP BY d.department_id, d.department_name
                ORDER BY total DESC, d.department_name""",
                rs -> new CountStat(rs.getString("label"), rs.getLong("total")));
    }

    /** Top events by seat-occupying registrations. */
    public List<CountStat> registrationsByEvent(Connection conn, int limit) {
        return new EventRegistrationDAO().topEventsByRegistrations(conn, limit);
    }

    /** Resources of each type. */
    public List<CountStat> resourcesByType(Connection conn) {
        return Sql.query(conn, """
                SELECT resource_type AS label, COUNT(*) AS total
                FROM resources GROUP BY resource_type ORDER BY total DESC""",
                rs -> new CountStat(rs.getString("label"), rs.getLong("total")));
    }

    /** Registration popularity per event with attendance (SUM aggregate). */
    public List<CountStat> eventAttendanceOverview(Connection conn) {
        return Sql.query(conn, """
                SELECT e.title AS label,
                       SUM(er.status = 'ATTENDED') AS total
                FROM events e
                JOIN event_registrations er ON er.event_id = e.event_id
                GROUP BY e.event_id, e.title
                ORDER BY total DESC, e.title
                LIMIT 8""",
                rs -> new CountStat(rs.getString("label"), rs.getLong("total")));
    }
}
