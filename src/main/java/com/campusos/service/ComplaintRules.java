package com.campusos.service;

import com.campusos.model.Complaint;
import com.campusos.model.ComplaintStatus;
import com.campusos.model.Role;
import com.campusos.model.User;

/**
 * Pure complaint lifecycle rules:
 * SUBMITTED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED
 * with role-based authorization for every transition.
 */
public final class ComplaintRules {

    private ComplaintRules() { }

    /** Structural transition map (who may not matter here). */
    public static boolean isValidTransition(ComplaintStatus from, ComplaintStatus to) {
        return switch (from) {
            case SUBMITTED -> to == ComplaintStatus.ASSIGNED;
            case ASSIGNED -> to == ComplaintStatus.IN_PROGRESS;
            case IN_PROGRESS -> to == ComplaintStatus.RESOLVED;
            case RESOLVED -> to == ComplaintStatus.CLOSED;
            case CLOSED -> false;
        };
    }

    /**
     * Full authorization check. Returns null when permitted, otherwise a
     * human-readable reason (services throw this message to the UI).
     *
     * - SUBMITTED → ASSIGNED        : administrator only
     * - ASSIGNED → IN_PROGRESS      : assigned faculty or administrator
     * - IN_PROGRESS → RESOLVED      : assigned faculty or administrator
     * - RESOLVED → CLOSED           : submitter (acknowledging) or administrator
     */
    public static String check(User actor, Complaint complaint, ComplaintStatus to) {
        if (actor == null || complaint == null) {
            return "Not authorized.";
        }
        ComplaintStatus from = complaint.getStatus();
        if (!isValidTransition(from, to)) {
            return "A complaint cannot move from " + from.getLabel() + " to " + to.getLabel()
                    + ". The lifecycle is SUBMITTED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED.";
        }
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isAssignee = actor.getId().equals(complaint.getAssignedToId());
        boolean isSubmitter = actor.getId().equals(complaint.getUserId());

        return switch (to) {
            case ASSIGNED -> isAdmin ? null
                    : "Only administrators can assign complaints.";
            case IN_PROGRESS, RESOLVED -> (isAdmin || isAssignee) ? null
                    : "Only the assigned faculty member or an administrator can update this complaint.";
            case CLOSED -> (isAdmin || isSubmitter) ? null
                    : "Only the submitter or an administrator can close this complaint.";
            case SUBMITTED -> "A complaint cannot go back to Submitted.";
        };
    }

    /** Admins may reassign a complaint that is already ASSIGNED. */
    public static boolean canReassign(User actor, Complaint complaint) {
        return actor != null
                && actor.getRole() == Role.ADMIN
                && complaint.getStatus() == ComplaintStatus.ASSIGNED;
    }

    /** Who may view the full complaint detail and history. */
    public static boolean canView(User actor, Complaint complaint) {
        if (actor == null || complaint == null) {
            return false;
        }
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isSubmitter = actor.getId().equals(complaint.getUserId());
        boolean isAssignee = actor.getId().equals(complaint.getAssignedToId());
        return isAdmin || isSubmitter || isAssignee;
    }
}
