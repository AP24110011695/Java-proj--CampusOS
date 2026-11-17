package com.campusos.exception;

/** Thrown when a referenced record (user, resource, booking...) does not exist. */
public class NotFoundException extends CampusOSException {
    public NotFoundException(String message) {
        super(message);
    }
}
