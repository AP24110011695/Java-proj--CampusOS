package com.campusos.exception;

/** Wraps low-level java.sql.SQLException so the rest of the app stays SQLException-free. */
public class DataAccessException extends CampusOSException {
    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
