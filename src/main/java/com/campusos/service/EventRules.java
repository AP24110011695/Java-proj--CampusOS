package com.campusos.service;

import com.campusos.model.Event;
import com.campusos.model.EventRegistration;
import com.campusos.model.EventStatus;
import com.campusos.model.RegistrationStatus;

import java.time.LocalDateTime;

/**
 * Pure event registration rules - capacity, deadline, duplicate and
 * cancellation logic, kept static for direct JUnit testing.
 */
public final class EventRules {

    private EventRules() { }

    /**
     * @param existing    the caller's current registration row, or null
     * @return null when registration is allowed, otherwise the reason it is not
     */
    public static String checkRegistration(Event event, EventRegistration existing, LocalDateTime now) {
        if (event == null) {
            return "Event not found.";
        }
        if (event.getStatus() != EventStatus.UPCOMING) {
            return "Registration is closed - the event is " + event.getStatus().getLabel() + ".";
        }
        if (now.isAfter(event.getRegistrationDeadline())) {
            return "Registration deadline (" + event.getRegistrationDeadline() + ") has passed.";
        }
        if (existing != null && existing.occupiesSeat()) {
            return "You are already registered for this event (duplicate registration).";
        }
        if (existing != null && existing.getStatus() == RegistrationStatus.CANCELLED) {
            // Unique key (event_id, user_id): a cancelled row keeps the seat record,
            // so re-registration is intentionally not possible.
            return "Your previous registration for this event was cancelled and cannot be renewed.";
        }
        if (event.getRegisteredCount() >= event.getMaxParticipants()) {
            return "The event is full (" + event.getMaxParticipants() + " participants). Registration closed automatically.";
        }
        return null;
    }

    /** Cancel eligibility: still holding a seat and the event has not started. */
    public static String checkCancellation(EventRegistration registration, LocalDateTime now) {
        if (registration == null) {
            return "Registration not found.";
        }
        if (registration.getStatus() != RegistrationStatus.REGISTERED) {
            return "Only active registrations can be cancelled (this one is "
                    + registration.getStatus().getLabel() + ").";
        }
        if (registration.getEventStartTime() != null && !now.isBefore(registration.getEventStartTime())) {
            return "The event has already started - the registration can no longer be cancelled.";
        }
        return null;
    }

    /** Attendance may be marked by the organizer/admin while the event is not cancelled. */
    public static boolean canMarkAttendance(Event event) {
        return event != null && event.getStatus() != EventStatus.CANCELLED;
    }

    /** Validation shared by event create/edit (returns first error or null). */
    public static String validateWindow(LocalDateTime start, LocalDateTime end,
                                        LocalDateTime deadline, LocalDateTime now) {
        if (start == null || end == null || deadline == null) {
            return "Start, end and registration deadline are required.";
        }
        if (!end.isAfter(start)) {
            return "Event end must be after its start.";
        }
        if (!start.isAfter(now)) {
            return "Event start must be in the future.";
        }
        if (deadline.isAfter(start)) {
            return "Registration deadline must be on or before the event start.";
        }
        return null;
    }
}
