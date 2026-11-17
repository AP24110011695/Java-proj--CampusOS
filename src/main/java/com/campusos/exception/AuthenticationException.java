package com.campusos.exception;

/** Thrown when login fails or an operation requires a logged-in user. */
public class AuthenticationException extends CampusOSException {
    public AuthenticationException(String message) {
        super(message);
    }
}
