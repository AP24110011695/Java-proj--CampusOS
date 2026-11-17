package com.campusos.service;

import com.campusos.dao.Db;
import com.campusos.dao.UserDAO;
import com.campusos.exception.CampusOSException;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.security.PasswordHasher;
import com.campusos.validation.Validator;

import java.util.List;

/**
 * Secure first-administrator bootstrap.
 *
 * No admin account ships with the schema or sample data. The first
 * administrator is created once by the AdminBootstrap CLI tool through
 * this service, from explicitly provided credentials - never a hardcoded
 * default password.
 */
public class BootstrapService {

    private final UserDAO userDAO = new UserDAO();
    private final AuditService auditService = new AuditService();

    /** Creates the first admin. Refuses if an administrator already exists. */
    public User createFirstAdmin(String fullName, String email, String password) {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, fullName, "Full name");
        Validator.email(errors, email);
        Validator.password(errors, password);
        Validator.check(errors);

        User admin = new User();
        admin.setFullName(fullName.trim());
        admin.setEmail(email.trim().toLowerCase());
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(PasswordHasher.hash(password));

        long id = Db.withTransaction(conn -> {
            long existingAdmins = userDAO.countByRole(conn, Role.ADMIN);
            if (existingAdmins > 0) {
                throw new CampusOSException("An administrator account already exists ("
                        + existingAdmins + " found). Use the application's user management screen instead.");
            }
            if (userDAO.existsByEmail(conn, admin.getEmail(), null)) {
                throw new CampusOSException("An account with this email already exists.");
            }
            return userDAO.insert(conn, admin);
        });
        admin.setId(id);
        auditService.recordWithUserId(null, "ADMIN_BOOTSTRAPPED", "USER", id,
                "First administrator account created via secure bootstrap: " + admin.getEmail());
        return admin;
    }
}
