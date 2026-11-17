package com.campusos.exception;

/**
 * Base runtime exception for all CampusOS business and data errors.
 * Extends RuntimeException so DAO/service method signatures stay clean
 * and controllers can render a friendly message in the UI.
 */
public class CampusOSException extends RuntimeException {

    public CampusOSException(String message) {
        super(message);
    }

    public CampusOSException(String message, Throwable cause) {
        super(message, cause);
    }
}
