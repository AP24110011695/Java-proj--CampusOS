package com.campusos;

import com.campusos.security.PasswordHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Password hashing: BCrypt must never store plain text and must verify correctly. */
class PasswordHasherTest {

    @Test
    @DisplayName("hash produces a BCrypt-formatted hash that differs from the plain password")
    void hashIsBcryptFormatted() {
        String hash = PasswordHasher.hash("Student@123");
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$"), "BCrypt hashes start with $2a$");
        assertNotEquals("Student@123", hash);
        assertEquals(60, hash.length(), "BCrypt hash length is 60 characters");
    }

    @Test
    @DisplayName("verify accepts the correct password")
    void verifyCorrectPassword() {
        String hash = PasswordHasher.hash("Faculty@123");
        assertTrue(PasswordHasher.verify("Faculty@123", hash));
    }

    @Test
    @DisplayName("verify rejects a wrong password (negative scenario)")
    void verifyRejectsWrongPassword() {
        String hash = PasswordHasher.hash("Faculty@123");
        assertFalse(PasswordHasher.verify("Faculty@124", hash));
        assertFalse(PasswordHasher.verify("faculty@123", hash));
    }

    @Test
    @DisplayName("each hash uses a unique random salt")
    void saltsAreUnique() {
        String h1 = PasswordHasher.hash("same-password-1");
        String h2 = PasswordHasher.hash("same-password-1");
        assertNotEquals(h1, h2, "identical passwords must produce different hashes");
        assertTrue(PasswordHasher.verify("same-password-1", h1));
        assertTrue(PasswordHasher.verify("same-password-1", h2));
    }

    @Test
    @DisplayName("empty or null passwords are rejected before hashing")
    void rejectsEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(""));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
    }

    @Test
    @DisplayName("verification against a malformed/empty hash fails safely (no exception)")
    void malformedHashFailsSafely() {
        assertFalse(PasswordHasher.verify("whatever", ""));
        assertFalse(PasswordHasher.verify("whatever", "not-a-bcrypt-hash"));
        assertFalse(PasswordHasher.verify("whatever", null));
        assertFalse(PasswordHasher.verify(null, "$2a$12$validformathashvaluehere"));
    }
}
