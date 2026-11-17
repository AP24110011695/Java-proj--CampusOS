package com.campusos.exception;

/** Thrown when a booking time range overlaps an existing pending/approved booking. */
public class BookingConflictException extends CampusOSException {
    public BookingConflictException(String message) {
        super(message);
    }
}
