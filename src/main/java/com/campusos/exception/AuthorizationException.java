package com.campusos.exception;

/**
 * Thrown when the current user tries to perform an operation their role
 * does not permit. Raised at the service layer so the rule cannot be
 * bypassed even if the UI layer is wrong or modified.
 */
public class AuthorizationException extends CampusOSException {
    public AuthorizationException(String message) {
        super(message);
    }
}
