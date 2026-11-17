package com.campusos.service;

import com.campusos.dao.Db;
import com.campusos.dao.UserDAO;
import com.campusos.exception.AuthorizationException;
import com.campusos.exception.CampusOSException;
import com.campusos.exception.NotFoundException;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.security.PasswordHasher;
import com.campusos.session.Session;
import com.campusos.validation.Validator;

import java.util.List;

/**
 * User management (Module 1). Administrator-only operations for creating,
 * editing, deactivating and searching students and faculty, plus
 * self-service profile updates for every logged-in user.
 */
public class UserService {

    private final UserDAO userDAO = new UserDAO();
    private final AuditService auditService = new AuditService();

    /** Administrator creates any account (students, faculty, other admins). */
    public User createUser(Session session, User user, String plainPassword) {
        session.requireAdmin();
        validate(user, plainPassword, true);

        Db.withTransactionVoid(conn -> {
            ensureUnique(conn, user, null);
            user.setEmail(user.getEmail().trim().toLowerCase());
            user.setPasswordHash(PasswordHasher.hash(plainPassword));
            long id = userDAO.insert(conn, user);
            user.setId(id);
        });
        auditService.record(session, "USER_CREATED", "USER", user.getId(),
                "Administrator created " + user.getRole().getLabel() + " account: " + user.getEmail());
        return user;
    }

    /** Administrator edits an existing account. Null password keeps the old one. */
    public void updateUser(Session session, User changes, String newPlainPassword) {
        session.requireAdmin();
        validate(changes, newPlainPassword, false);

        Db.withTransactionVoid(conn -> {
            User existing = userDAO.findById(conn, changes.getId());
            if (existing == null) {
                throw new NotFoundException("User not found.");
            }
            ensureUnique(conn, changes, changes.getId());
            changes.setEmail(changes.getEmail().trim().toLowerCase());
            userDAO.update(conn, changes);
            if (newPlainPassword != null && !newPlainPassword.isBlank()) {
                userDAO.updatePasswordHash(conn, changes.getId(), PasswordHasher.hash(newPlainPassword));
            }
        });
        auditService.record(session, "USER_UPDATED", "USER", changes.getId(),
                "Account updated: " + changes.getEmail()
                        + (newPlainPassword != null && !newPlainPassword.isBlank() ? " (password reset)" : ""));
    }

    /** Soft delete: deactivate instead of deleting referenced rows. */
    public void setActive(Session session, long userId, boolean active) {
        session.requireAdmin();
        if (userId == session.currentUserId() && !active) {
            throw new AuthorizationException("You cannot deactivate your own account while logged in.");
        }
        int rows = Db.withConnection(conn -> {
            User user = userDAO.findById(conn, userId);
            if (user == null) {
                throw new NotFoundException("User not found.");
            }
            return userDAO.setActive(conn, userId, active) ? 1 : 0;
        });
        if (rows == 0) {
            throw new CampusOSException("Deactivation failed.");
        }
        auditService.record(session, active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                "USER", userId, "Account " + userId + (active ? " activated." : " deactivated (soft delete)."));
    }

    /** Every logged-in user may edit their own basic profile fields. */
    public void updateOwnProfile(Session session, String fullName, String email, String phone) {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, fullName, "Full name");
        Validator.email(errors, email);
        Validator.phone(errors, phone, true);
        Validator.check(errors);

        long userId = session.currentUserId();
        Db.withTransactionVoid(conn -> {
            User changes = new User();
            changes.setId(userId);
            changes.setFullName(fullName.trim());
            changes.setEmail(email.trim().toLowerCase());
            changes.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
            if (userDAO.existsByEmail(conn, changes.getEmail(), userId)) {
                throw new CampusOSException("Another account already uses this email.");
            }
            userDAO.updateProfile(conn, changes);
        });
        auditService.record(session, "PROFILE_UPDATED", "USER", userId,
                "User updated their own profile.");
    }

    /** Read-only searches used by the management screens. */
    public List<User> search(Session session, Role role, Long departmentId, String searchText) {
        session.requireAdmin();
        return Db.withConnection(conn ->
                userDAO.findWithFilters(conn, role, departmentId, searchText, null));
    }

    public List<User> listByRole(Session session, Role role, boolean activeOnly) {
        session.requireAny(Role.ADMIN, Role.FACULTY);
        return Db.withConnection(conn -> userDAO.findByRole(conn, role, activeOnly));
    }

    public User getById(Session session, long userId) {
        return Db.withConnection(conn -> {
            User user = userDAO.findById(conn, userId);
            if (user == null) {
                throw new NotFoundException("User not found.");
            }
            // Privacy: users may read only themselves unless they are admins.
            if (session.currentUser().getRole() != Role.ADMIN
                    && !session.currentUser().getId().equals(userId)) {
                throw new AuthorizationException("You may only view your own profile.");
            }
            return user;
        });
    }

    // ------------------------------------------------------------------ helpers

    private void validate(User user, String plainPassword, boolean requirePassword) {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, user.getFullName(), "Full name");
        Validator.maxLen(errors, user.getFullName(), 100, "Full name");
        Validator.email(errors, user.getEmail());
        Validator.phone(errors, user.getPhone(), true);
        Validator.identifier(errors, user.getIdentifier(),
                user.getRole() == Role.STUDENT ? "Student ID" : "Employee ID");
        if (user.getRole() == null) {
            errors.add("Role is required.");
        }
        if (requirePassword) {
            Validator.password(errors, plainPassword);
        }
        Validator.check(errors);
    }

    private void ensureUnique(java.sql.Connection conn, User user, Long excludeId) {
        if (userDAO.existsByEmail(conn, user.getEmail(), excludeId)) {
            throw new CampusOSException("An account with this email already exists.");
        }
        if (userDAO.existsByIdentifier(conn, user.getIdentifier(), excludeId)) {
            throw new CampusOSException("This student/employee ID is already in use.");
        }
    }
}
