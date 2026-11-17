package com.campusos;

import com.campusos.model.Event;
import com.campusos.model.EventRegistration;
import com.campusos.model.EventStatus;
import com.campusos.model.RegistrationStatus;
import com.campusos.service.EventRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Event capacity, deadline, duplicate and cancellation rules. */
class EventRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0);

    private Event openEvent(int capacity, int registered) {
        Event e = new Event();
        e.setStatus(EventStatus.UPCOMING);
        e.setRegistrationDeadline(NOW.plusDays(3));
        e.setMaxParticipants(capacity);
        e.setRegisteredCount(registered);
        e.setStartTime(NOW.plusDays(7));
        return e;
    }

    private EventRegistration registration(RegistrationStatus status) {
        EventRegistration r = new EventRegistration();
        r.setStatus(status);
        r.setEventStartTime(NOW.plusDays(7));
        return r;
    }

    @Test
    @DisplayName("registration allowed while open, before deadline, seats remain")
    void registrationAllowed() {
        assertNull(EventRules.checkRegistration(openEvent(50, 10), null, NOW));
    }

    @Test
    @DisplayName("negative: event full - automatic registration closure")
    void registrationBlockedWhenFull() {
        String reason = EventRules.checkRegistration(openEvent(2, 2), null, NOW);
        assertNotNull(reason);
        assertTrue(reason.toLowerCase().contains("full"), () -> reason);

        // the edge case: last seat taken
        assertNull(EventRules.checkRegistration(openEvent(2, 1), null, NOW));
    }

    @Test
    @DisplayName("negative: registration after deadline is rejected")
    void registrationBlockedAfterDeadline() {
        Event event = openEvent(50, 0);
        LocalDateTime afterDeadline = NOW.plusDays(4);
        String reason = EventRules.checkRegistration(event, null, afterDeadline);
        assertNotNull(reason);
        assertTrue(reason.toLowerCase().contains("deadline"));
    }

    @Test
    @DisplayName("negative: duplicate registration blocked; cancelled registration cannot renew")
    void duplicateAndCancelledRegistrations() {
        Event event = openEvent(50, 5);

        String duplicate = EventRules.checkRegistration(event,
                registration(RegistrationStatus.REGISTERED), NOW);
        assertNotNull(duplicate);
        assertTrue(duplicate.toLowerCase().contains("already registered"));

        String cancelled = EventRules.checkRegistration(event,
                registration(RegistrationStatus.CANCELLED), NOW);
        assertNotNull(cancelled);
        assertTrue(cancelled.toLowerCase().contains("cancel"));

        // attended occupies a seat too
        assertNotNull(EventRules.checkRegistration(event,
                registration(RegistrationStatus.ATTENDED), NOW));
    }

    @Test
    @DisplayName("negative: registration closed for non-upcoming events")
    void registrationBlockedForNonUpcoming() {
        for (EventStatus status : List.of(EventStatus.ONGOING, EventStatus.COMPLETED,
                EventStatus.CANCELLED)) {
            Event e = openEvent(50, 0);
            e.setStatus(status);
            assertNotNull(EventRules.checkRegistration(e, null, NOW), status.name());
        }
    }

    @Test
    @DisplayName("cancellation allowed while REGISTERED and event not started")
    void cancellationRules() {
        assertNull(EventRules.checkCancellation(registration(RegistrationStatus.REGISTERED), NOW));

        String attended = EventRules.checkCancellation(registration(RegistrationStatus.ATTENDED), NOW);
        assertNotNull(attended, "attended registrations cannot be cancelled");

        String cancelled = EventRules.checkCancellation(registration(RegistrationStatus.CANCELLED), NOW);
        assertNotNull(cancelled);

        EventRegistration started = registration(RegistrationStatus.REGISTERED);
        started.setEventStartTime(NOW.minusHours(1));
        String late = EventRules.checkCancellation(started, NOW);
        assertNotNull(late, "cannot cancel after the event started");
        assertTrue(late.toLowerCase().contains("already started") || late.toLowerCase().contains("event"));
    }

    @Test
    @DisplayName("attendance can be marked for any non-cancelled event")
    void attendanceRules() {
        Event upcoming = openEvent(10, 0);
        assertTrue(EventRules.canMarkAttendance(upcoming));
        Event cancelled = openEvent(10, 0);
        cancelled.setStatus(EventStatus.CANCELLED);
        assertFalse(EventRules.canMarkAttendance(cancelled));
    }

    @Test
    @DisplayName("window validation: end after start, future start, deadline ≤ start")
    void windowValidation() {
        assertNull(EventRules.validateWindow(NOW.plusDays(7), NOW.plusDays(7).plusHours(3),
                NOW.plusDays(5), NOW));
        assertNotNull(EventRules.validateWindow(NOW.plusDays(7), NOW.plusDays(7), NOW.plusDays(5), NOW),
                "end == start invalid");
        assertNotNull(EventRules.validateWindow(NOW, NOW.plusHours(2), NOW, NOW),
                "past start invalid");
        assertNotNull(EventRules.validateWindow(NOW.plusDays(7), NOW.plusDays(8),
                NOW.plusDays(8), NOW), "deadline after start invalid");
    }
}
