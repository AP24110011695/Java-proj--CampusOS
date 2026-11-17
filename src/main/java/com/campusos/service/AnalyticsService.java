package com.campusos.service;

import com.campusos.dao.AnalyticsDAO;
import com.campusos.dao.BookingDAO;
import com.campusos.dao.ComplaintDAO;
import com.campusos.dao.Db;
import com.campusos.dao.EventRegistrationDAO;
import com.campusos.dao.Sql;
import com.campusos.model.CountStat;
import com.campusos.model.DashboardStats;
import com.campusos.model.Role;
import com.campusos.session.Session;

import java.util.List;

/**
 * Analytics (Module 5). All chart and dashboard data comes from live
 * aggregate queries - no hardcoded numbers anywhere.
 */
public class AnalyticsService {

    private final AnalyticsDAO analyticsDAO = new AnalyticsDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final ComplaintDAO complaintDAO = new ComplaintDAO();
    private final EventRegistrationDAO registrationDAO = new EventRegistrationDAO();

    /** Administrator dashboard counters (one aggregate query with subselects). */
    public DashboardStats adminStats(Session session) {
        session.requireAdmin();
        return Db.withConnection(analyticsDAO::loadDashboardStats);
    }

    public List<CountStat> bookingsByStatus(Session session) {
        session.requireAdmin();
        return Db.withConnection(bookingDAO::countGroupedByStatus);
    }

    public List<CountStat> complaintsByStatus(Session session) {
        session.requireAdmin();
        return Db.withConnection(complaintDAO::countGroupedByStatus);
    }

    public List<CountStat> complaintsByCategory(Session session) {
        session.requireAdmin();
        return Db.withConnection(complaintDAO::countGroupedByCategory);
    }

    /** GROUP BY month over the trailing months - monthly booking trend. */
    public List<CountStat> monthlyBookingTrend(Session session, int months) {
        session.requireAdmin();
        return Db.withConnection(conn -> bookingDAO.monthlyTrend(conn, months));
    }

    /** LEFT JOIN + GROUP BY - department-wise user distribution. */
    public List<CountStat> usersByDepartment(Session session) {
        session.requireAdmin();
        return Db.withConnection(analyticsDAO::usersByDepartment);
    }

    /** Top events by registrations for the events chart. */
    public List<CountStat> registrationsByEvent(Session session, int limit) {
        session.requireAdmin();
        return Db.withConnection(conn -> registrationDAO.topEventsByRegistrations(conn, limit));
    }

    /** Attended participants per event (SUM aggregate). */
    public List<CountStat> eventAttendanceOverview(Session session) {
        session.requireAdmin();
        return Db.withConnection(analyticsDAO::eventAttendanceOverview);
    }

    /** Faculty dashboard counters, computed from their own data. */
    public FacultySummary facultySummary(Session session) {
        session.requireFacultyOrAdmin();
        long userId = session.currentUserId();
        return Db.withConnection(conn -> {
            FacultySummary s = new FacultySummary();
            s.assignedComplaints = complaintDAO.countByAssigneeAndStatuses(conn, userId,
                    com.campusos.model.ComplaintStatus.ASSIGNED,
                    com.campusos.model.ComplaintStatus.IN_PROGRESS);
            s.resolvedComplaints = complaintDAO.countByAssigneeAndStatuses(conn, userId,
                    com.campusos.model.ComplaintStatus.RESOLVED,
                    com.campusos.model.ComplaintStatus.CLOSED);
            s.upcomingEvents = Sql.queryLong(conn,
                    "SELECT COUNT(*) FROM events WHERE created_by = ? AND status = 'UPCOMING'", userId);
            s.totalBookings = Sql.queryLong(conn,
                    "SELECT COUNT(*) FROM bookings WHERE user_id = ?", userId);
            return s;
        });
    }

    /** Student dashboard counters, computed from their own data. */
    public StudentSummary studentSummary(Session session) {
        session.requireStudent();
        long userId = session.currentUserId();
        return Db.withConnection(conn -> {
            StudentSummary s = new StudentSummary();
            s.totalBookings = Sql.queryLong(conn,
                    "SELECT COUNT(*) FROM bookings WHERE user_id = ?", userId);
            s.pendingBookings = Sql.queryLong(conn,
                    "SELECT COUNT(*) FROM bookings WHERE user_id = ? AND status = 'PENDING'", userId);
            s.approvedBookings = Sql.queryLong(conn,
                    "SELECT COUNT(*) FROM bookings WHERE user_id = ? AND status = 'APPROVED'", userId);
            s.openComplaints = Sql.queryLong(conn, """
                    SELECT COUNT(*) FROM complaints
                    WHERE user_id = ? AND status IN ('SUBMITTED','ASSIGNED','IN_PROGRESS')""", userId);
            s.resolvedComplaints = Sql.queryLong(conn, """
                    SELECT COUNT(*) FROM complaints
                    WHERE user_id = ? AND status IN ('RESOLVED','CLOSED')""", userId);
            s.upcomingRegistrations = Sql.queryLong(conn, """
                    SELECT COUNT(*) FROM event_registrations er
                    JOIN events e ON e.event_id = er.event_id
                    WHERE er.user_id = ? AND er.status = 'REGISTERED'
                      AND e.status = 'UPCOMING'""", userId);
            return s;
        });
    }

    /** Simple holder for faculty dashboard numbers. */
    public static class FacultySummary {
        public long assignedComplaints;
        public long resolvedComplaints;
        public long upcomingEvents;
        public long totalBookings;
    }

    /** Simple holder for student dashboard numbers. */
    public static class StudentSummary {
        public long totalBookings;
        public long pendingBookings;
        public long approvedBookings;
        public long openComplaints;
        public long resolvedComplaints;
        public long upcomingRegistrations;
    }
}
