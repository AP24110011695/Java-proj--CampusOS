package com.campusos;

import com.campusos.model.Complaint;
import com.campusos.model.ComplaintStatus;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.service.ComplaintRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Complaint lifecycle transitions and role-based permissions. */
class ComplaintRulesTest {

    private User user(Role role, long id) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private Complaint complaint(ComplaintStatus status, long submitterId, Long assignedToId) {
        Complaint c = new Complaint();
        c.setStatus(status);
        c.setUserId(submitterId);
        c.setAssignedToId(assignedToId);
        return c;
    }

    private static final long ADMIN_ID = 1;
    private static final long FACULTY_ID = 2;
    private static final long STUDENT_ID = 3;

    @Test
    @DisplayName("structural lifecycle SUBMITTED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED")
    void lifecycleIsValid() {
        assertTrue(ComplaintRules.isValidTransition(ComplaintStatus.SUBMITTED, ComplaintStatus.ASSIGNED));
        assertTrue(ComplaintRules.isValidTransition(ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS));
        assertTrue(ComplaintRules.isValidTransition(ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED));
        assertTrue(ComplaintRules.isValidTransition(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED));
    }

    @Test
    @DisplayName("negative: skipping steps or going backwards is invalid")
    void illegalTransitions() {
        assertFalse(ComplaintRules.isValidTransition(ComplaintStatus.SUBMITTED, ComplaintStatus.IN_PROGRESS));
        assertFalse(ComplaintRules.isValidTransition(ComplaintStatus.SUBMITTED, ComplaintStatus.RESOLVED));
        assertFalse(ComplaintRules.isValidTransition(ComplaintStatus.ASSIGNED, ComplaintStatus.CLOSED));
        assertFalse(ComplaintRules.isValidTransition(ComplaintStatus.RESOLVED, ComplaintStatus.IN_PROGRESS));
        assertFalse(ComplaintRules.isValidTransition(ComplaintStatus.CLOSED, ComplaintStatus.ASSIGNED));
    }

    @Test
    @DisplayName("only admins assign; only the assignee or admin may progress")
    void assignmentPermissions() {
        Complaint submitted = complaint(ComplaintStatus.SUBMITTED, STUDENT_ID, null);
        assertNull(ComplaintRules.check(user(Role.ADMIN, ADMIN_ID), submitted, ComplaintStatus.ASSIGNED));
        assertNotNull(ComplaintRules.check(user(Role.FACULTY, FACULTY_ID), submitted, ComplaintStatus.ASSIGNED),
                "faculty must not self-assign");
        assertNotNull(ComplaintRules.check(user(Role.STUDENT, STUDENT_ID), submitted, ComplaintStatus.ASSIGNED));
    }

    @Test
    @DisplayName("assigned faculty (or admin) can move ASSIGNED → IN_PROGRESS")
    void assignedFacultyCanProgress() {
        Complaint assigned = complaint(ComplaintStatus.ASSIGNED, STUDENT_ID, FACULTY_ID);
        assertNull(ComplaintRules.check(user(Role.FACULTY, FACULTY_ID), assigned, ComplaintStatus.IN_PROGRESS));
        assertNull(ComplaintRules.check(user(Role.ADMIN, ADMIN_ID), assigned, ComplaintStatus.IN_PROGRESS));
        assertNotNull(ComplaintRules.check(user(Role.STUDENT, STUDENT_ID), assigned, ComplaintStatus.IN_PROGRESS),
                "the submitter cannot progress the complaint themselves");
        // a different faculty member cannot take over
        assertNotNull(ComplaintRules.check(user(Role.FACULTY, 99), assigned, ComplaintStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("RESOLVED → CLOSED: submitter acknowledges, admin can also close")
    void closingPermissions() {
        Complaint resolved = complaint(ComplaintStatus.RESOLVED, STUDENT_ID, FACULTY_ID);
        assertNull(ComplaintRules.check(user(Role.STUDENT, STUDENT_ID), resolved, ComplaintStatus.CLOSED));
        assertNull(ComplaintRules.check(user(Role.ADMIN, ADMIN_ID), resolved, ComplaintStatus.CLOSED));
        assertNotNull(ComplaintRules.check(user(Role.FACULTY, FACULTY_ID), resolved, ComplaintStatus.CLOSED),
                "the resolver cannot close on behalf of the submitter");
    }

    @Test
    @DisplayName("admins may reassign only while ASSIGNED")
    void reassignment() {
        Complaint assigned = complaint(ComplaintStatus.ASSIGNED, STUDENT_ID, FACULTY_ID);
        Complaint inProgress = complaint(ComplaintStatus.IN_PROGRESS, STUDENT_ID, FACULTY_ID);
        assertTrue(ComplaintRules.canReassign(user(Role.ADMIN, ADMIN_ID), assigned));
        assertFalse(ComplaintRules.canReassign(user(Role.FACULTY, FACULTY_ID), assigned),
                "faculty cannot reassign");
        assertFalse(ComplaintRules.canReassign(user(Role.ADMIN, ADMIN_ID), inProgress),
                "reassignment is only allowed before work starts");
    }

    @Test
    @DisplayName("visibility: submitter, assignee and admins only")
    void visibility() {
        Complaint c = complaint(ComplaintStatus.IN_PROGRESS, STUDENT_ID, FACULTY_ID);
        assertTrue(ComplaintRules.canView(user(Role.STUDENT, STUDENT_ID), c));
        assertTrue(ComplaintRules.canView(user(Role.FACULTY, FACULTY_ID), c));
        assertTrue(ComplaintRules.canView(user(Role.ADMIN, ADMIN_ID), c));
        assertFalse(ComplaintRules.canView(user(Role.STUDENT, 99), c),
                "another student must not see this complaint");
    }
}
