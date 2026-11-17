package com.campusos;

import com.campusos.model.Booking;
import com.campusos.model.BookingStatus;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.service.BookingRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** Booking overlap detection, status transitions and authorization rules. */
class BookingRulesTest {

    private static final LocalTime T0900 = LocalTime.of(9, 0);
    private static final LocalTime T1000 = LocalTime.of(10, 0);
    private static final LocalTime T1030 = LocalTime.of(10, 30);
    private static final LocalTime T1100 = LocalTime.of(11, 0);
    private static final LocalTime T1130 = LocalTime.of(11, 30);
    private static final LocalTime T1200 = LocalTime.of(12, 0);

    private User user(Role role, long id) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    @Test
    @DisplayName("overlap condition: 10:30-11:30 overlaps existing 10:00-11:00")
    void overlappingSlotsDetected() {
        assertTrue(BookingRules.overlaps(T1030, T1130, T1000, T1100));
    }

    @Test
    @DisplayName("contained ranges overlap")
    void containedRangeOverlaps() {
        assertTrue(BookingRules.overlaps(T1000, T1200, T1030, T1100));
        assertTrue(BookingRules.overlaps(T1030, T1100, T1000, T1200));
    }

    @Test
    @DisplayName("adjacent slots (end == start) do NOT overlap")
    void adjacentSlotsDoNotOverlap() {
        assertFalse(BookingRules.overlaps(T1000, T1100, T1100, T1200));
        assertFalse(BookingRules.overlaps(T1100, T1200, T1000, T1100));
    }

    @Test
    @DisplayName("separate ranges do not overlap")
    void separateRangesDoNotOverlap() {
        assertFalse(BookingRules.overlaps(T0900, T1000, T1100, T1200));
        assertFalse(BookingRules.overlaps(T1100, T1200, T0900, T1000));
    }

    @Test
    @DisplayName("allowed transitions: PENDING → APPROVED/REJECTED/CANCELLED")
    void pendingTransitions() {
        assertTrue(BookingRules.canTransition(BookingStatus.PENDING, BookingStatus.APPROVED));
        assertTrue(BookingRules.canTransition(BookingStatus.PENDING, BookingStatus.REJECTED));
        assertTrue(BookingRules.canTransition(BookingStatus.PENDING, BookingStatus.CANCELLED));
    }

    @Test
    @DisplayName("allowed transitions: APPROVED → CANCELLED/COMPLETED")
    void approvedTransitions() {
        assertTrue(BookingRules.canTransition(BookingStatus.APPROVED, BookingStatus.CANCELLED));
        assertTrue(BookingRules.canTransition(BookingStatus.APPROVED, BookingStatus.COMPLETED));
    }

    @Test
    @DisplayName("negative: terminal statuses cannot change")
    void terminalStatusesAreFinal() {
        for (BookingStatus terminal : List.of(BookingStatus.REJECTED, BookingStatus.CANCELLED,
                BookingStatus.COMPLETED)) {
            for (BookingStatus target : BookingStatus.values()) {
                assertFalse(BookingRules.canTransition(terminal, target),
                        terminal + " must be terminal (target " + target + ")");
            }
        }
        // and PENDING cannot jump straight to COMPLETED
        assertFalse(BookingRules.canTransition(BookingStatus.PENDING, BookingStatus.COMPLETED));
    }

    @Test
    @DisplayName("authorization: only students/faculty request, only admins decide")
    void authorizationRoles() {
        assertTrue(BookingRules.canRequest(user(Role.STUDENT, 1)));
        assertTrue(BookingRules.canRequest(user(Role.FACULTY, 2)));
        assertFalse(BookingRules.canRequest(user(Role.ADMIN, 3)));
        assertFalse(BookingRules.canRequest(null));

        assertTrue(BookingRules.canDecide(user(Role.ADMIN, 3)));
        assertFalse(BookingRules.canDecide(user(Role.STUDENT, 1)));
        assertFalse(BookingRules.canDecide(user(Role.FACULTY, 2)));
    }

    @Test
    @DisplayName("owner can cancel own booking; another student cannot")
    void cancelAuthorization() {
        User owner = user(Role.STUDENT, 42);
        User stranger = user(Role.STUDENT, 43);
        Booking booking = new Booking();
        booking.setUserId(42L);
        assertTrue(BookingRules.canCancel(owner, booking));
        assertFalse(BookingRules.canCancel(stranger, booking));
        User admin = user(Role.ADMIN, 99);
        assertTrue(BookingRules.canCancel(admin, booking), "admins may cancel any booking");
    }

    @Test
    @DisplayName("cancellable: pending always, approved only before slot starts")
    void cancelEligibility() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Booking pending = new Booking();
        pending.setStatus(BookingStatus.PENDING);
        pending.setBookingDate(tomorrow);
        pending.setStartTime(T1000);
        assertTrue(BookingRules.isCancellable(pending, LocalDateTime.now()));

        Booking approvedFuture = new Booking();
        approvedFuture.setStatus(BookingStatus.APPROVED);
        approvedFuture.setBookingDate(tomorrow);
        approvedFuture.setStartTime(T1000);
        assertTrue(BookingRules.isCancellable(approvedFuture, LocalDateTime.now()));

        Booking approvedPast = new Booking();
        approvedPast.setStatus(BookingStatus.APPROVED);
        approvedPast.setBookingDate(LocalDate.now().minusDays(1));
        approvedPast.setStartTime(T1000);
        assertFalse(BookingRules.isCancellable(approvedPast, LocalDateTime.now()),
                "past approved bookings are history and cannot be cancelled");

        Booking rejected = new Booking();
        rejected.setStatus(BookingStatus.REJECTED);
        rejected.setBookingDate(tomorrow);
        rejected.setStartTime(T1000);
        assertFalse(BookingRules.isCancellable(rejected, LocalDateTime.now()));
    }

    @Test
    @DisplayName("window validation rejects past dates and inverted times")
    void windowValidation() {
        LocalDate today = LocalDate.now();
        assertNotNull(BookingRules.validateWindow(today.minusDays(1), T1000, T1100, today));
        assertNotNull(BookingRules.validateWindow(today.plusDays(1), T1100, T1000, today),
                "end before start must fail");
        assertNotNull(BookingRules.validateWindow(null, T1000, T1100, today));
        assertNull(BookingRules.validateWindow(today.plusDays(1), T1000, T1100, today));
    }
}
