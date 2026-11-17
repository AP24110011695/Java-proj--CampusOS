package com.campusos.service;

import com.campusos.dao.Db;
import com.campusos.dao.UserDAO;
import com.campusos.exception.AuthenticationException;
import com.campusos.exception.CampusOSException;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.security.PasswordHasher;
import com.campusos.session.Session;
import com.campusos.validation.Validator;

import java.util.List;

/**
 * Login, student self-registration and password changes.
 * Passwords are verified against BCrypt hashes; plain-text is never stored.
 */
public class AuthService {

    private final UserDAO userDAO = new UserDAO();
    private final AuditService auditService = new AuditService();

    /** Verifies credentials and returns an authenticated session. */
    public Session login(String email, String password) {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, email, "Email");
        Validator.nonBlank(errors, password, "Password");
        Validator.check(errors);

        String normalizedEmail = email.trim().toLowerCase();
        User user = Db.withConnection(conn -> userDAO.findByEmail(conn, normalizedEmail));

        if (user == null || !PasswordHasher.verify(password, user.getPasswordHash())) {
            // Audit without user id - the account may not even exist.
            auditService.recordWithUserId(null, "LOGIN_FAILED", "USER", null,
                    "Failed login attempt for email: " + normalizedEmail);
            throw new AuthenticationException("Invalid email or password.");
        }
        if (!user.isActive()) {
            throw new AuthenticationException(
                    "This account has been deactivated. Please contact the administrator.");
        }

        Session session = new Session();
        session.login(user);
        auditService.record(session, "USER_LOGIN", "USER", user.getId(),
                user.getFullName() + " (" + user.getRole().getLabel() + ") logged in.");
        return session;
    }

    /**
     * Public student self-registration. Faculty and admin accounts can
     * never be created through this path (role is forced to STUDENT).
     */
    public User registerStudent(String fullName, String email, String phone,
                                String identifier, Long departmentId, String password,
                                String confirmPassword) {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, fullName, "Full name");
        Validator.maxLen(errors, fullName, 100, "Full name");
        Validator.email(errors, email);
        Validator.phone(errors, phone, true);
        Validator.identifier(errors, identifier, "Student ID");
        Validator.password(errors, password);
        if (departmentId == null) {
            errors.add("Please select your department.");
        }
        if (password != null && !password.equals(confirmPassword)) {
            errors.add("Password and confirmation do not match.");
        }
        Validator.check(errors);

        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setRole(Role.STUDENT);   // self-registration can only ever create students
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        user.setIdentifier(identifier == null || identifier.isBlank() ? null : identifier.trim());
        user.setDepartmentId(departmentId);

        return Db.withTransaction(conn -> {
            if (userDAO.existsByEmail(conn, user.getEmail(), null)) {
                throw new CampusOSException("An account with this email already exists.");
            }
            if (userDAO.existsByIdentifier(conn, user.getIdentifier(), null)) {
                throw new CampusOSException("This student ID is already registered.");
            }
            user.setPasswordHash(PasswordHasher.hash(password));
            long id = userDAO.insert(conn, user);
            user.setId(id);
            auditService.recordWithUserId(null, "USER_REGISTERED", "USER", id,
                    "New student self-registered: " + user.getEmail());
            return user;
        });
    }

    /** Self-service password change: old password must verify first. */
    public void changePassword(Session session, String oldPassword, String newPassword,
                               String confirmPassword) {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, oldPassword, "Current password");
        Validator.password(errors, newPassword);
        if (newPassword != null && !newPassword.equals(confirmPassword)) {
            errors.add("New password and confirmation do not match.");
        }
        Validator.check(errors);

        long userId = session.currentUserId();
        User user = Db.withConnection(conn -> userDAO.findById(conn, userId));
        if (user == null || !PasswordHasher.verify(oldPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Your current password is incorrect.");
        }
        String newHash = PasswordHasher.hash(newPassword);
        Db.withConnectionVoid(conn -> userDAO.updatePasswordHash(conn, userId, newHash));
        auditService.record(session, "PASSWORD_CHANGED", "USER", userId,
                "User changed their own password.");
    }
}
