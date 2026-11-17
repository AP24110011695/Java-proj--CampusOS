package com.campusos.service;

import com.campusos.dao.BookingDAO;
import com.campusos.dao.ComplaintDAO;
import com.campusos.dao.Db;
import com.campusos.dao.EventRegistrationDAO;
import com.campusos.dao.UserDAO;
import com.campusos.model.Booking;
import com.campusos.model.Complaint;
import com.campusos.model.EventRegistration;
import com.campusos.model.User;
import com.campusos.session.Session;
import com.campusos.util.CsvUtil;

import java.util.List;

/**
 * CSV export for administrators (Module 5). Uses CsvUtil for RFC-4180
 * escaping and writes through a JavaFX FileChooser chosen path in the UI.
 */
public class CsvExportService {

    private final UserDAO userDAO = new UserDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final ComplaintDAO complaintDAO = new ComplaintDAO();
    private final EventRegistrationDAO registrationDAO = new EventRegistrationDAO();

    /** Students followed by faculty in one file (admins are excluded). */
    public String exportUsers(Session session) {
        session.requireAdmin();
        List<User> users = Db.withConnection(conn ->
                userDAO.findWithFilters(conn, null, null, null, null));
        StringBuilder csv = new StringBuilder();
        csv.append(CsvUtil.row("User ID", "Role", "Full Name", "Email", "ID Number",
                "Department", "Phone", "Active", "Registered On"));
        for (User u : users) {
            if (u.getRole() == com.campusos.model.Role.ADMIN) {
                continue;
            }
            csv.append(CsvUtil.row(u.getId(), u.getRole().getLabel(), u.getFullName(), u.getEmail(),
                    u.getIdentifier(), u.getDepartmentName(), u.getPhone(),
                    u.isActive() ? "Yes" : "No",
                    u.getCreatedAt() == null ? "" : u.getCreatedAt().toLocalDate()));
        }
        return csv.toString();
    }

    public String exportStudents(Session session) {
        session.requireAdmin();
        List<User> users = Db.withConnection(conn -> userDAO.findByRole(conn, com.campusos.model.Role.STUDENT, false));
        StringBuilder csv = new StringBuilder();
        csv.append(CsvUtil.row("User ID", "Full Name", "Email", "Student ID", "Department",
                "Phone", "Active", "Registered On"));
        for (User u : users) {
            csv.append(CsvUtil.row(u.getId(), u.getFullName(), u.getEmail(), u.getIdentifier(),
                    u.getDepartmentName(), u.getPhone(), u.isActive() ? "Yes" : "No",
                    u.getCreatedAt() == null ? "" : u.getCreatedAt().toLocalDate()));
        }
        return csv.toString();
    }

    public String exportFaculty(Session session) {
        session.requireAdmin();
        List<User> users = Db.withConnection(conn -> userDAO.findByRole(conn, com.campusos.model.Role.FACULTY, false));
        StringBuilder csv = new StringBuilder();
        csv.append(CsvUtil.row("User ID", "Full Name", "Email", "Employee ID", "Department",
                "Phone", "Active", "Registered On"));
        for (User u : users) {
            csv.append(CsvUtil.row(u.getId(), u.getFullName(), u.getEmail(), u.getIdentifier(),
                    u.getDepartmentName(), u.getPhone(), u.isActive() ? "Yes" : "No",
                    u.getCreatedAt() == null ? "" : u.getCreatedAt().toLocalDate()));
        }
        return csv.toString();
    }

    public String exportBookings(Session session) {
        session.requireAdmin();
        List<Booking> bookings = Db.withConnection(conn -> bookingDAO.findWithFilters(
                conn, null, null, null, null, null));
        StringBuilder csv = new StringBuilder();
        csv.append(CsvUtil.row("Booking ID", "Resource", "Resource Type", "Booked By", "Email",
                "Date", "Start", "End", "Purpose", "Status",
                "Requested At", "Decided By", "Decision Remarks"));
        for (Booking b : bookings) {
            csv.append(CsvUtil.row(b.getId(), b.getResourceName(), b.getResourceType(),
                    b.getUserName(), b.getUserEmail(), b.getBookingDate(),
                    b.getStartTime(), b.getEndTime(), b.getPurpose(), b.getStatus(),
                    b.getRequestedAt(), b.getDecidedByName(), b.getDecisionRemarks()));
        }
        return csv.toString();
    }

    public String exportComplaints(Session session) {
        session.requireAdmin();
        List<Complaint> complaints = Db.withConnection(conn -> complaintDAO.findWithFilters(
                conn, null, null, null, null, null, null, null));
        StringBuilder csv = new StringBuilder();
        csv.append(CsvUtil.row("Complaint ID", "Submitted By", "Email", "Department", "Category",
                "Title", "Priority", "Status", "Assigned To", "Created At", "Resolved At"));
        for (Complaint c : complaints) {
            csv.append(CsvUtil.row(c.getId(), c.getUserName(), c.getUserEmail(),
                    c.getDepartmentName(), c.getCategory(), c.getTitle(), c.getPriority(),
                    c.getStatus(), c.getAssignedToName(), c.getCreatedAt(), c.getResolvedAt()));
        }
        return csv.toString();
    }

    public String exportEventRegistrations(Session session) {
        session.requireAdmin();
        List<EventRegistration> registrations = Db.withConnection(conn ->
                registrationDAO.findByEventAll(conn));
        StringBuilder csv = new StringBuilder();
        csv.append(CsvUtil.row("Registration ID", "Event", "Event Start", "Participant",
                "Email", "Status", "Registered At", "Attended At"));
        for (EventRegistration r : registrations) {
            csv.append(CsvUtil.row(r.getId(), r.getEventTitle(), r.getEventStartTime(),
                    r.getUserName(), r.getUserEmail(), r.getStatus(),
                    r.getRegisteredAt(), r.getAttendedAt()));
        }
        return csv.toString();
    }
}
