package com.campusos.exception;

/** Thrown when a user tries to register for an event they are already registered for. */
public class DuplicateRegistrationException extends CampusOSException {
    public DuplicateRegistrationException(String message) {
        super(message);
    }
}
