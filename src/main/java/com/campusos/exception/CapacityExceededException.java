package com.campusos.exception;

/** Thrown when an event has reached its maximum participant capacity. */
public class CapacityExceededException extends CampusOSException {
    public CapacityExceededException(String message) {
        super(message);
    }
}
