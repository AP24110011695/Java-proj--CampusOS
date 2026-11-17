package com.campusos.session;

import com.campusos.exception.AuthenticationException;
import com.campusos.exception.AuthorizationException;
import com.campusos.model.Role;
import com.campusos.model.User;

/**
 * Holds the currently logged-in user for this application session and
 * performs role checks used by every service method (authorization).
 */
public final class Session {

    private User currentUser;

    public void login(User user) {
        this.currentUser = user;
    }

    public void logout() {
        this.currentUser = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public User currentUser() {
        if (currentUser == null) {
            throw new AuthenticationException("You are not logged in.");
        }
        return currentUser;
    }

    public Long currentUserId() {
        return currentUser().getId();
    }

    public boolean hasRole(Role role) {
        return currentUser != null && currentUser.getRole() == role;
    }

    /** Passes only if the current user has one of the given roles. */
    public void requireAny(Role... roles) {
        User user = currentUser();
        for (Role r : roles) {
            if (user.getRole() == r) {
                return;
            }
        }
        throw new AuthorizationException(
                "Your role (" + user.getRole().getLabel() + ") is not permitted to perform this action.");
    }

    public void requireAdmin() {
        requireAny(Role.ADMIN);
    }

    public void requireFacultyOrAdmin() {
        requireAny(Role.FACULTY, Role.ADMIN);
    }

    public void requireStudent() {
        requireAny(Role.STUDENT);
    }
}
