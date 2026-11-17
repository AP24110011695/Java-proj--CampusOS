package com.campusos.validation;

import com.campusos.exception.ValidationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Central input validation. Every service call validates its inputs here
 * before touching the database, so invalid data can never reach SQL.
 */
public final class Validator {

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{10,13}$");
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9./-]{3,20}$");

    private Validator() { }

    /** Runs every check; throws ValidationException listing ALL problems. */
    public static void check(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    public static List<String> newErrorList() {
        return new ArrayList<>();
    }

    public static void nonBlank(List<String> errors, String value, String field) {
        if (value == null || value.isBlank()) {
            errors.add(field + " is required.");
        }
    }

    public static void maxLen(List<String> errors, String value, int max, String field) {
        if (value != null && value.length() > max) {
            errors.add(field + " must be at most " + max + " characters.");
        }
    }

    public static void email(List<String> errors, String email) {
        if (email == null || email.isBlank() || !EMAIL.matcher(email.trim()).matches()) {
            errors.add("A valid email address is required (example: name@college.edu).");
        }
    }

    public static void phone(List<String> errors, String phone, boolean optional) {
        if (phone == null || phone.isBlank()) {
            if (!optional) {
                errors.add("Phone number is required.");
            }
            return;
        }
        String digits = phone.replaceAll("[\\s-]", "");
        if (!PHONE.matcher(digits).matches()) {
            errors.add("Phone number must contain 10-13 digits.");
        }
    }

    public static void identifier(List<String> errors, String identifier, String field) {
        if (identifier == null || identifier.isBlank()) {
            return; // null-identifier is allowed for admins
        }
        if (!IDENTIFIER.matcher(identifier.trim()).matches()) {
            errors.add(field + " may contain 3-20 letters, digits, dots, dashes or slashes.");
        }
    }

    /**
     * Password policy: minimum 8 characters with at least one letter and one digit.
     * Rejects the usual weak choices students would otherwise use.
     */
    public static void password(List<String> errors, String password) {
        if (password == null || password.length() < 8) {
            errors.add("Password must be at least 8 characters long.");
            return;
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
            errors.add("Password must contain at least one letter and one digit.");
        }
    }

    public static void positiveInt(List<String> errors, Integer value, String field) {
        if (value == null || value <= 0) {
            errors.add(field + " must be a positive number.");
        }
    }

    public static void futureDateTime(List<String> errors, LocalDateTime value, String field) {
        if (value == null || !value.isAfter(LocalDateTime.now())) {
            errors.add(field + " must be in the future.");
        }
    }
}
