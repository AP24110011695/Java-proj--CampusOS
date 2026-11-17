package com.campusos;

import com.campusos.exception.ValidationException;
import com.campusos.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Input validation rules, including negative scenarios. */
class ValidationTest {

    @Test
    @DisplayName("valid inputs produce no errors")
    void validInputs() {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, "Aarav Mehta", "Full name");
        Validator.email(errors, "aarav.mehta@student.edu");
        Validator.phone(errors, "9876511001", true);
        Validator.password(errors, "Student@123");
        Validator.identifier(errors, "STU2023CSE01", "Student ID");
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
    }

    @Test
    @DisplayName("blank required fields are reported")
    void blankRequiredFields() {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, "", "Full name");
        Validator.nonBlank(errors, null, "Title");
        assertFalse(errors.isEmpty());
        assertEquals(2, errors.size());
    }

    @Test
    @DisplayName("invalid email formats are rejected")
    void invalidEmails() {
        for (String bad : List.of("plain", "no-domain@", "@no-local.com", "spaces in@mail.com")) {
            List<String> errors = Validator.newErrorList();
            Validator.email(errors, bad);
            assertEquals(1, errors.size(), bad + " should be invalid");
        }
    }

    @Test
    @DisplayName("password policy: length, letters and digits (negative scenarios)")
    void passwordPolicy() {
        List<String> weak = List.of("short1", "onlyletters", "12345678", "", "no-digits-here!");
        for (String password : weak) {
            List<String> errors = Validator.newErrorList();
            Validator.password(errors, password);
            assertFalse(errors.isEmpty(), "password '" + password + "' must be rejected");
        }
        List<String> errors = Validator.newErrorList();
        Validator.password(errors, "GoodPass123");
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("phone must contain 10-13 digits")
    void phoneRules() {
        List<String> errors = Validator.newErrorList();
        Validator.phone(errors, "12345", false);
        assertFalse(errors.isEmpty());
        errors = Validator.newErrorList();
        Validator.phone(errors, "98765-11001", false); // separators stripped
        assertTrue(errors.isEmpty());
        errors = Validator.newErrorList();
        Validator.phone(errors, null, true); // optional
        assertTrue(errors.isEmpty());
        errors = Validator.newErrorList();
        Validator.phone(errors, null, false); // required
        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("ValidationException aggregates ALL problems at once")
    void exceptionAggregatesErrors() {
        List<String> errors = List.of("Title is required.", "Email is invalid.");
        ValidationException exception = new ValidationException(errors);
        assertEquals(2, exception.getErrors().size());
        assertTrue(exception.getMessage().contains("Title is required."));
        assertTrue(exception.getMessage().contains("Email is invalid."));
    }
}
