package com.campusos.service;

import com.campusos.model.Booking;
import com.campusos.model.BookingStatus;
import com.campusos.model.Role;
import com.campusos.model.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Pure booking business rules - no database, no UI. Keeping them in static
 * methods makes the critical logic directly unit-testable (JUnit).
 */
public final class BookingRules {

    private BookingRules() { }

    /**
     * The overlap condition required by the specification:
     * newStart &lt; existingEnd AND newEnd &gt; existingStart.
     * Two touching ranges (end == start) do NOT overlap.
     */
    public static boolean overlaps(LocalTime newStart, LocalTime newEnd,
                                   LocalTime existingStart, LocalTime existingEnd) {
        return newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart);
    }

    /** Who may create a booking request: students and faculty only. */
    public static boolean canRequest(User actor) {
        return actor != null
                && (actor.getRole() == Role.STUDENT || actor.getRole() == Role.FACULTY);
    }

    /** Only administrators approve or reject requests. */
    public static boolean canDecide(User actor) {
        return actor != null && actor.getRole() == Role.ADMIN;
    }

    /** Only the booking owner or an administrator may cancel. */
    public static boolean canCancel(User actor, Booking booking) {
        if (actor == null || booking == null) {
            return false;
        }
        return actor.getRole() == Role.ADMIN || actor.getId().equals(booking.getUserId());
    }

    /**
     * Allowed status transitions:
     * PENDING → APPROVED / REJECTED / CANCELLED
     * APPROVED → CANCELLED (before start) / COMPLETED
     * REJECTED, CANCELLED, COMPLETED are terminal.
     */
    public static boolean canTransition(BookingStatus from, BookingStatus to) {
        return switch (from) {
            case PENDING -> to == BookingStatus.APPROVED
                    || to == BookingStatus.REJECTED
                    || to == BookingStatus.CANCELLED;
            case APPROVED -> to == BookingStatus.CANCELLED
                    || to == BookingStatus.COMPLETED;
            default -> false;
        };
    }

    /**
     * Cancel eligibility: PENDING can always be cancelled; APPROVED only
     * while the slot has not started yet. Past bookings are history.
     */
    public static boolean isCancellable(Booking booking, LocalDateTime now) {
        if (booking.getBookingDate() == null || booking.getStartTime() == null) {
            return false;
        }
        LocalDateTime slotStart = LocalDateTime.of(booking.getBookingDate(), booking.getStartTime());
        if (booking.getStatus() == BookingStatus.PENDING) {
            return true;
        }
        return booking.getStatus() == BookingStatus.APPROVED && slotStart.isAfter(now);
    }

    /** Human-readable validation of a new booking request (pure checks only). */
    public static String validateWindow(LocalDate date, LocalTime start, LocalTime end, LocalDate today) {
        if (date == null) {
            return "Booking date is required.";
        }
        if (start == null || end == null) {
            return "Start and end time are required.";
        }
        if (!end.isAfter(start)) {
            return "End time must be after start time.";
        }
        if (date.isBefore(today)) {
            return "Booking date cannot be in the past.";
        }
        return null;
    }
}
