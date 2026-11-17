package com.campusos.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Thin wrapper around the established jBCrypt library.
 * - Passwords are always stored as a salted BCrypt hash (60 characters).
 * - Plain-text passwords are never stored, logged or persisted anywhere.
 * - Each call to hash() produces a fresh random salt, so identical
 *   passwords yield different hashes in the database.
 */
public final class PasswordHasher {

    /** log_rounds = 12 ≈ 250ms per hash on typical hardware: good security/cost balance. */
    private static final int LOG_ROUNDS = 12;

    private PasswordHasher() { }

    /** Returns the BCrypt hash for the given plain-text password. */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(LOG_ROUNDS));
    }

    /** Constant-time verification of a candidate password against a stored hash. */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
    }
}
