package com.campusos.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when user input fails validation. Collects every problem so the
 * UI can display all messages at once instead of one error per attempt.
 */
public class ValidationException extends CampusOSException {

    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super("Validation failed: " + String.join("; ", errors));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public ValidationException(String singleError) {
        this(List.of(singleError));
    }

    public List<String> getErrors() {
        return errors;
    }
}
