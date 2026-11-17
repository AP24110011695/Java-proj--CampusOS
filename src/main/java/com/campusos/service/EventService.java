package com.campusos.service;

import com.campusos.dao.Db;
import com.campusos.dao.EventDAO;
import com.campusos.dao.EventRegistrationDAO;
import com.campusos.exception.NotFoundException;
import com.campusos.model.Event;
import com.campusos.model.EventRegistration;
import com.campusos.model.EventStatus;
import com.campusos.model.RegistrationStatus;
import com.campusos.model.Role;
import com.campusos.session.Session;
import com.campusos.validation.Validator;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event workflow (Module 4).
 *
 * register() runs inside one MySQL transaction:
 *   1. SELECT ... FOR UPDATE locks the event row, so two students clicking
 *      "Register" at the same instant are serialized.
 *   2. Duplicate, deadline, status and capacity checks run against the
 *      locked, freshly-counted state.
 *   3. The registration row is inserted and COMMIT releases the lock.
 *   4. The UNIQUE KEY (event_id, user_id) is the database-level backstop.
 */
public class EventService {

    private final EventDAO eventDAO = new EventDAO();
    private final EventRegistrationDAO registrationDAO = new EventRegistrationDAO();
    private final AuditService auditService = new AuditService();

    /** Admin or faculty creates an event. Faculty own the events they create. */
    public Event create(Session session, Event event) {
        session.requireFacultyOrAdmin();
        validate(event);
        event.setCreatedById(session.currentUserId());
        long id = Db.withConnection(conn -> eventDAO.insert(conn, event));
        event.setId(id);
        auditService.record(session, "EVENT_CREATED", "EVENT", id,
                "Event created: " + event.getTitle() + " (capacity " + event.getMaxParticipants() + ")");
        return event;
    }

    /** Admin edits any event; faculty edit only their own. */
    public void update(Session session, Event changes) {
        validate(changes);
        Db.withTransactionVoid(conn -> {
            Event existing = eventDAO.findById(conn, changes.getId());
            if (existing == null) {
                throw new NotFoundException("Event not found.");
            }
            requireOwnership(session, existing);
            if (existing.getStatus() == EventStatus.CANCELLED) {
                throw new IllegalArgumentException("Cancelled events cannot be edited.");
            }
            eventDAO.update(conn, changes);
        });
        auditService.record(session, "EVENT_UPDATED", "EVENT", changes.getId(),
                "Event updated: " + changes.getTitle());
    }

    /** Admin cancels any event; faculty cancel only their own. */
    public void cancel(Session session, long eventId) {
        Db.withTransactionVoid(conn -> {
            Event event = eventDAO.findById(conn, eventId);
            if (event == null) {
                throw new NotFoundException("Event not found.");
            }
            requireOwnership(session, event);
            if (event.getStatus() != EventStatus.UPCOMING) {
                throw new IllegalArgumentException("Only upcoming events can be cancelled.");
            }
            eventDAO.setStatus(conn, eventId, EventStatus.CANCELLED);
        });
        auditService.record(session, "EVENT_CANCELLED", "EVENT", eventId,
                "Event #" + eventId + " cancelled.");
    }

    /** Student registers for an event - capacity-safe, duplicate-safe. */
    public EventRegistration register(Session session, long eventId) {
        session.requireStudent();
        long userId = session.currentUserId();

        EventRegistration created = new EventRegistration();
        created.setEventId(eventId);
        created.setUserId(userId);

        Db.withTransactionVoid(conn -> {
            // Row lock serializes concurrent registrations for this event.
            Event locked = eventDAO.findByIdForUpdate(conn, eventId);
            if (locked == null) {
                throw new NotFoundException("Event not found.");
            }
            EventRegistration existing = registrationDAO.findSpecific(conn, eventId, userId);
            String reason = EventRules.checkRegistration(locked, existing, LocalDateTime.now());
            if (reason != null) {
                throw new com.campusos.exception.CampusOSException(reason);
            }
            long regId = registrationDAO.insert(conn, created); // unique key backstop inside
            created.setId(regId);
        });

        auditService.record(session, "EVENT_REGISTERED", "EVENT", eventId,
                session.currentUser().getFullName() + " registered for event #"
                        + eventId + ".");
        return created;
    }

    /** Student cancels their own eligible registration; the seat is released. */
    public void cancelRegistration(Session session, long registrationId) {
        Db.withTransactionVoid(conn -> {
            EventRegistration reg = registrationDAO.findById(conn, registrationId);
            if (reg == null) {
                throw new NotFoundException("Registration not found.");
            }
            if (!reg.getUserId().equals(session.currentUserId())) {
                throw new com.campusos.exception.AuthorizationException(
                        "You can only cancel your own registrations.");
            }
            String reason = EventRules.checkCancellation(reg, LocalDateTime.now());
            if (reason != null) {
                throw new com.campusos.exception.CampusOSException(reason);
            }
            registrationDAO.setStatus(conn, registrationId, RegistrationStatus.CANCELLED);
        });
        auditService.record(session, "EVENT_REGISTRATION_CANCELLED", "EVENT",
                registrationId, "Registration #" + registrationId + " cancelled.");
    }

    /** Organizer or admin marks attendance for one participant. */
    public void markAttendance(Session session, long registrationId, boolean attended) {
        Db.withTransactionVoid(conn -> {
            EventRegistration reg = registrationDAO.findById(conn, registrationId);
            if (reg == null) {
                throw new NotFoundException("Registration not found.");
            }
            Event event = eventDAO.findById(conn, reg.getEventId());
            requireOwnership(session, event);
            if (!EventRules.canMarkAttendance(event)) {
                throw new IllegalArgumentException("Attendance cannot be marked for a cancelled event.");
            }
            if (reg.getStatus() == RegistrationStatus.CANCELLED) {
                throw new IllegalArgumentException("Cancelled registrations cannot be marked as attended.");
            }
            registrationDAO.setStatus(conn, registrationId,
                    attended ? RegistrationStatus.ATTENDED : RegistrationStatus.REGISTERED);
        });
        auditService.record(session, attended ? "ATTENDANCE_MARKED" : "ATTENDANCE_CLEARED",
                "EVENT", registrationId,
                "Attendance " + (attended ? "marked" : "cleared") + " for registration #"
                        + registrationId + ".");
    }

    // ------------------------------------------------------------------ reads

    public List<Event> listUpcoming() {
        return Db.withConnection(conn -> eventDAO.findWithFilters(conn, null, null, null, true));
    }

    public List<Event> listManaged(Session session, String searchText) {
        // Admin sees everything; faculty sees their own events.
        Long organizerId = session.currentUser().getRole() == Role.FACULTY
                ? session.currentUserId() : null;
        return Db.withConnection(conn ->
                eventDAO.findWithFilters(conn, null, organizerId, searchText, false));
    }

    public List<Event> listCreatedBy(Session session, long userId) {
        return Db.withConnection(conn ->
                eventDAO.findWithFilters(conn, null, userId, null, false));
    }

    public Event getDetail(Session session, long eventId) {
        return Db.withConnection(conn -> {
            Event event = eventDAO.findById(conn, eventId);
            if (event == null) {
                throw new NotFoundException("Event not found.");
            }
            return event;
        });
    }

    public List<EventRegistration> participants(Session session, long eventId) {
        Db.withConnectionVoid(conn -> {
            Event event = eventDAO.findById(conn, eventId);
            if (event == null) {
                throw new NotFoundException("Event not found.");
            }
            requireOwnership(session, event);
        });
        return Db.withConnection(conn -> registrationDAO.findByEvent(conn, eventId));
    }

    public List<EventRegistration> myRegistrations(Session session) {
        return Db.withConnection(conn ->
                registrationDAO.findByUser(conn, session.currentUserId()));
    }

    public EventRegistration myRegistrationFor(Session session, long eventId) {
        return Db.withConnection(conn ->
                registrationDAO.findSpecific(conn, eventId, session.currentUserId()));
    }

    // ------------------------------------------------------------------ helpers

    private void requireOwnership(Session session, Event event) {
        boolean isAdmin = session.currentUser().getRole() == Role.ADMIN;
        boolean isOrganizer = event.getCreatedById().equals(session.currentUserId());
        if (!isAdmin && !isOrganizer) {
            throw new com.campusos.exception.AuthorizationException(
                    "Only the event organizer or an administrator can perform this action.");
        }
    }

    private void validate(Event event) {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, event.getTitle(), "Event title");
        Validator.maxLen(errors, event.getTitle(), 150, "Event title");
        Validator.nonBlank(errors, event.getVenue(), "Venue");
        Validator.maxLen(errors, event.getVenue(), 120, "Venue");
        Validator.maxLen(errors, event.getDescription(), 2000, "Description");
        if (event.getMaxParticipants() <= 0) {
            errors.add("Maximum participants must be a positive number.");
        }
        String windowError = EventRules.validateWindow(event.getStartTime(), event.getEndTime(),
                event.getRegistrationDeadline(), LocalDateTime.now());
        if (windowError != null) {
            errors.add(windowError);
        }
        Validator.check(errors);
    }
}
