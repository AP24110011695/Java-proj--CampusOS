package com.campusos.service;

import com.campusos.dao.ComplaintDAO;
import com.campusos.dao.ComplaintUpdateDAO;
import com.campusos.dao.Db;
import com.campusos.dao.UserDAO;
import com.campusos.exception.NotFoundException;
import com.campusos.model.Complaint;
import com.campusos.model.ComplaintCategory;
import com.campusos.model.ComplaintPriority;
import com.campusos.model.ComplaintStatus;
import com.campusos.model.ComplaintUpdate;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.session.Session;
import com.campusos.validation.Validator;

import java.util.List;

/**
 * Complaint workflow (Module 3).
 * Every status change writes an append-only row to complaint_updates, so
 * the complete history is preserved (never overwritten).
 */
public class ComplaintService {

    private final ComplaintDAO complaintDAO = new ComplaintDAO();
    private final ComplaintUpdateDAO updateDAO = new ComplaintUpdateDAO();
    private final UserDAO userDAO = new UserDAO();
    private final AuditService auditService = new AuditService();

    /** Student or faculty submits a complaint (starts in SUBMITTED). */
    public Complaint submit(Session session, ComplaintCategory category, String title,
                            String description, ComplaintPriority priority, Long departmentId) {
        session.requireAny(Role.STUDENT, Role.FACULTY);
        List<String> errors = Validator.newErrorList();
        if (category == null) {
            errors.add("Complaint category is required.");
        }
        if (priority == null) {
            errors.add("Priority is required.");
        }
        Validator.nonBlank(errors, title, "Title");
        Validator.maxLen(errors, title, 150, "Title");
        Validator.nonBlank(errors, description, "Description");
        Validator.maxLen(errors, description, 2000, "Description");
        Validator.check(errors);

        Complaint complaint = new Complaint();
        complaint.setUserId(session.currentUserId());
        complaint.setDepartmentId(departmentId);
        complaint.setCategory(category);
        complaint.setTitle(title.trim());
        complaint.setDescription(description.trim());
        complaint.setPriority(priority);

        long id = Db.withConnection(conn -> complaintDAO.insert(conn, complaint));
        complaint.setId(id);

        // The very first history row records the SUBMITTED state.
        ComplaintUpdate first = new ComplaintUpdate();
        first.setComplaintId(id);
        first.setUpdatedById(session.currentUserId());
        first.setPreviousStatus(null);
        first.setNewStatus(ComplaintStatus.SUBMITTED);
        first.setRemarks("Complaint submitted.");
        Db.withConnectionVoid(conn -> updateDAO.insert(conn, first));

        auditService.record(session, "COMPLAINT_SUBMITTED", "COMPLAINT", id,
                "Complaint submitted: " + complaint.getTitle());
        return complaint;
    }

    /** Administrator assigns a complaint to a faculty member (SUBMITTED → ASSIGNED). */
    public void assign(Session session, long complaintId, long facultyId, String remarks) {
        session.requireAdmin();
        Db.withTransactionVoid(conn -> {
            Complaint complaint = complaintDAO.findById(conn, complaintId);
            if (complaint == null) {
                throw new NotFoundException("Complaint not found.");
            }
            User faculty = userDAO.findById(conn, facultyId);
            if (faculty == null || faculty.getRole() != Role.FACULTY || !faculty.isActive()) {
                throw new IllegalArgumentException("Please select an active faculty member.");
            }
            String reason = ComplaintRules.check(session.currentUser(), complaint,
                    complaint.getStatus() == ComplaintStatus.SUBMITTED
                            ? ComplaintStatus.ASSIGNED : complaint.getStatus());
            if (reason != null) {
                throw new com.campusos.exception.AuthorizationException(reason);
            }

            boolean wasSubmitted = complaint.getStatus() == ComplaintStatus.SUBMITTED;
            if (wasSubmitted) {
                complaintDAO.assign(conn, complaintId, facultyId, ComplaintStatus.ASSIGNED);
            } else {
                // Reassignment inside ASSIGNED: only the assignee changes.
                complaintDAO.assign(conn, complaintId, facultyId, complaint.getStatus());
            }

            ComplaintUpdate history = new ComplaintUpdate();
            history.setComplaintId(complaintId);
            history.setUpdatedById(session.currentUserId());
            history.setPreviousStatus(complaint.getStatus());
            history.setNewStatus(wasSubmitted ? ComplaintStatus.ASSIGNED : complaint.getStatus());
            history.setRemarks((remarks == null || remarks.isBlank()
                    ? "" : remarks.trim() + " ") + "Assigned to " + faculty.getFullName() + ".");
            updateDAO.insert(conn, history);
        });
        auditService.record(session, "COMPLAINT_ASSIGNED", "COMPLAINT", complaintId,
                "Complaint #" + complaintId + " assigned to user #" + facultyId + ".");
    }

    /** Advance the complaint one lifecycle step with a mandatory remark. */
    public void updateStatus(Session session, long complaintId, ComplaintStatus to, String remarks) {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, remarks, "Remarks");
        Validator.check(errors);

        Db.withTransactionVoid(conn -> {
            Complaint complaint = complaintDAO.findById(conn, complaintId);
            if (complaint == null) {
                throw new NotFoundException("Complaint not found.");
            }
            String reason = ComplaintRules.check(session.currentUser(), complaint, to);
            if (reason != null) {
                throw new com.campusos.exception.AuthorizationException(reason);
            }
            complaintDAO.updateStatus(conn, complaintId, to);

            ComplaintUpdate history = new ComplaintUpdate();
            history.setComplaintId(complaintId);
            history.setUpdatedById(session.currentUserId());
            history.setPreviousStatus(complaint.getStatus());
            history.setNewStatus(to);
            history.setRemarks(remarks.trim());
            updateDAO.insert(conn, history);
        });
        auditService.record(session, "COMPLAINT_STATUS_CHANGED", "COMPLAINT", complaintId,
                "Complaint #" + complaintId + " moved to " + to.getLabel()
                        + " by " + session.currentUser().getFullName() + ".");
    }

    /** Full detail + timeline; visible to submitter, assignee and admins. */
    public Complaint getDetail(Session session, long complaintId) {
        Complaint complaint = Db.withConnection(conn -> complaintDAO.findById(conn, complaintId));
        if (complaint == null) {
            throw new NotFoundException("Complaint not found.");
        }
        if (!ComplaintRules.canView(session.currentUser(), complaint)) {
            throw new com.campusos.exception.AuthorizationException(
                    "You are not allowed to view this complaint.");
        }
        return complaint;
    }

    public List<ComplaintUpdate> getHistory(Session session, long complaintId) {
        getDetail(session, complaintId); // permission check happens here
        return Db.withConnection(conn -> updateDAO.findByComplaint(conn, complaintId));
    }

    // ------------------------------------------------------------------ reads

    public List<Complaint> listForAdmin(Session session, ComplaintStatus status,
                                        ComplaintCategory category, ComplaintPriority priority,
                                        Long departmentId, String searchText) {
        session.requireAdmin();
        return Db.withConnection(conn -> complaintDAO.findWithFilters(conn, status, category,
                priority, departmentId, null, null, searchText));
    }

    public List<Complaint> listMine(Session session) {
        return Db.withConnection(conn ->
                complaintDAO.findWithFilters(conn, null, null, null, null, null,
                        session.currentUserId(), null));
    }

    public List<Complaint> listAssignedToMe(Session session) {
        session.requireFacultyOrAdmin();
        return Db.withConnection(conn -> complaintDAO.findByAssignee(conn, session.currentUserId()));
    }
}
