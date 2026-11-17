package com.campusos.service;

import com.campusos.dao.BookingDAO;
import com.campusos.dao.Db;
import com.campusos.dao.ResourceDAO;
import com.campusos.exception.BookingConflictException;
import com.campusos.exception.NotFoundException;
import com.campusos.model.Booking;
import com.campusos.model.BookingStatus;
import com.campusos.model.Resource;
import com.campusos.session.Session;
import com.campusos.validation.Validator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Booking workflow (Module 2).
 *
 * requestBooking() runs inside a MySQL transaction:
 *   1. SELECT ... FOR UPDATE locks the resource row, serializing every
 *      concurrent booking attempt for the same resource.
 *   2. The overlap query (newStart < existingEnd AND newEnd > existingStart)
 *      checks PENDING + APPROVED bookings on the same date.
 *   3. Only when no conflict exists is the booking inserted, and COMMIT
 *      releases the lock.
 * This makes conflict detection safe under concurrency - the check and the
 * insert are atomic together.
 */
public class BookingService {

    private final BookingDAO bookingDAO = new BookingDAO();
    private final ResourceDAO resourceDAO = new ResourceDAO();
    private final AuditService auditService = new AuditService();

    public Booking requestBooking(Session session, long resourceId, LocalDate date,
                                  LocalTime start, LocalTime end, String purpose) {
        if (!BookingRules.canRequest(session.currentUser())) {
            throw new com.campusos.exception.AuthorizationException(
                    "Only students and faculty can submit booking requests.");
        }
        List<String> errors = Validator.newErrorList();
        String windowError = BookingRules.validateWindow(date, start, end, LocalDate.now());
        if (windowError != null) {
            errors.add(windowError);
        }
        Validator.nonBlank(errors, purpose, "Purpose");
        Validator.maxLen(errors, purpose, 255, "Purpose");
        Validator.check(errors);

        Booking booking = new Booking();
        booking.setResourceId(resourceId);
        booking.setUserId(session.currentUserId());
        booking.setBookingDate(date);
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setPurpose(purpose.trim());

        Db.withTransactionVoid(conn -> {
            // Row lock on the resource: concurrent requests for this resource queue here.
            Resource resource = resourceDAO.findByIdForUpdate(conn, resourceId);
            if (resource == null) {
                throw new NotFoundException("Resource not found.");
            }
            if (resource.getStatus() != com.campusos.model.ResourceStatus.ACTIVE) {
                throw new BookingConflictException(
                        "This resource is currently " + resource.getStatus().getLabel()
                        + " and cannot be booked.");
            }
            booking.setResourceName(resource.getName());
            // Overlap check INSIDE the same transaction as the insert.
            List<Booking> conflicts = bookingDAO.findConflicting(conn, resourceId, date, start, end);
            if (!conflicts.isEmpty()) {
                Booking clash = conflicts.get(0);
                throw new BookingConflictException(String.format(
                        "Time conflict! %s is already booked %s to %s on %s (status: %s, requested by %s). "
                                + "Please choose a different time slot.",
                        resource.getName(), clash.getStartTime(), clash.getEndTime(),
                        clash.getBookingDate(), clash.getStatus().getLabel(),
                        clash.getUserName()));
            }

            long id = bookingDAO.insert(conn, booking);
            booking.setId(id);
        });

        auditService.record(session, "BOOKING_REQUESTED", "BOOKING", booking.getId(),
                "Booking requested for resource #" + resourceId + " on " + date
                        + " " + start + "-" + end + ".");
        return booking;
    }

    /** Administrator approves or rejects a pending request. */
    public void decide(Session session, long bookingId, BookingStatus decision, String remarks) {
        if (!BookingRules.canDecide(session.currentUser())) {
            throw new com.campusos.exception.AuthorizationException(
                    "Only administrators can approve or reject bookings.");
        }
        if (decision != BookingStatus.APPROVED && decision != BookingStatus.REJECTED) {
            throw new IllegalArgumentException("Decision must be APPROVED or REJECTED.");
        }
        Db.withTransactionVoid(conn -> {
            Booking booking = bookingDAO.findById(conn, bookingId);
            if (booking == null) {
                throw new NotFoundException("Booking not found.");
            }
            if (!BookingRules.canTransition(booking.getStatus(), decision)) {
                throw new BookingConflictException("A " + booking.getStatus().getLabel()
                        + " booking cannot be moved to " + decision.getLabel() + ".");
            }
            bookingDAO.markDecision(conn, bookingId, decision, session.currentUserId(), remarks);
        });
        auditService.record(session, decision == BookingStatus.APPROVED
                        ? "BOOKING_APPROVED" : "BOOKING_REJECTED",
                "BOOKING", bookingId,
                "Booking #" + bookingId + " " + decision.getLabel().toLowerCase()
                        + (remarks == null || remarks.isBlank() ? "." : " - " + remarks));
    }

    /** Owner or admin cancels an eligible booking. */
    public void cancel(Session session, long bookingId) {
        Db.withTransactionVoid(conn -> {
            Booking booking = bookingDAO.findById(conn, bookingId);
            if (booking == null) {
                throw new NotFoundException("Booking not found.");
            }
            if (!BookingRules.canCancel(session.currentUser(), booking)) {
                throw new com.campusos.exception.AuthorizationException(
                        "You can only cancel your own bookings.");
            }
            if (!BookingRules.isCancellable(booking, LocalDateTime.now())) {
                throw new BookingConflictException("This booking can no longer be cancelled ("
                        + booking.getStatus().getLabel() + ").");
            }
            bookingDAO.markCancelled(conn, bookingId);
        });
        auditService.record(session, "BOOKING_CANCELLED", "BOOKING", bookingId,
                "Booking #" + bookingId + " cancelled by "
                        + session.currentUser().getFullName() + ".");
    }

    /** Administrator marks an approved past booking as completed. */
    public void markCompleted(Session session, long bookingId) {
        session.requireAdmin();
        Db.withTransactionVoid(conn -> {
            Booking booking = bookingDAO.findById(conn, bookingId);
            if (booking == null) {
                throw new NotFoundException("Booking not found.");
            }
            if (!BookingRules.canTransition(booking.getStatus(), BookingStatus.COMPLETED)) {
                throw new BookingConflictException("Only APPROVED bookings can be marked completed.");
            }
            bookingDAO.markCompleted(conn, bookingId);
        });
        auditService.record(session, "BOOKING_COMPLETED", "BOOKING", bookingId,
                "Booking #" + bookingId + " marked completed.");
    }

    // ------------------------------------------------------------------ reads

    public List<Booking> listAll(Session session, BookingStatus status, Long resourceId,
                                 LocalDate date, String searchText) {
        session.requireAdmin();
        return Db.withConnection(conn ->
                bookingDAO.findWithFilters(conn, status, resourceId, null, date, searchText));
    }

    public List<Booking> listMine(Session session, BookingStatus status) {
        return Db.withConnection(conn ->
                bookingDAO.findByUser(conn, session.currentUserId(), status));
    }

    public Booking getById(Session session, long bookingId) {
        return Db.withConnection(conn -> {
            Booking booking = bookingDAO.findById(conn, bookingId);
            if (booking == null) {
                throw new NotFoundException("Booking not found.");
            }
            return booking;
        });
    }
}
