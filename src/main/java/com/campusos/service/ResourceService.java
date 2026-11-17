package com.campusos.service;

import com.campusos.dao.Db;
import com.campusos.dao.ResourceDAO;
import com.campusos.exception.CampusOSException;
import com.campusos.exception.NotFoundException;
import com.campusos.model.Resource;
import com.campusos.model.ResourceStatus;
import com.campusos.model.ResourceType;
import com.campusos.session.Session;
import com.campusos.validation.Validator;

import java.util.List;

/** Resource management (Module 2) - administrator only for changes. */
public class ResourceService {

    private final ResourceDAO resourceDAO = new ResourceDAO();
    private final AuditService auditService = new AuditService();

    /** Admin management view. */
    public List<Resource> listAll(Session session) {
        session.requireAdmin();
        return Db.withConnection(conn ->
                resourceDAO.findWithFilters(conn, null, null, null));
    }

    /** Bookable (ACTIVE) resources for the browsing screen - any logged-in user. */
    public List<Resource> listBookable() {
        return Db.withConnection(resourceDAO::findBookable);
    }

    public List<Resource> search(Session session, ResourceType type, String searchText) {
        session.requireAdmin();
        return Db.withConnection(conn ->
                resourceDAO.findWithFilters(conn, type, null, searchText));
    }

    public Resource create(Session session, Resource resource) {
        session.requireAdmin();
        validate(resource);
        resource.setCreatedById(session.currentUserId());
        long id = Db.withConnection(conn -> resourceDAO.insert(conn, resource));
        resource.setId(id);
        auditService.record(session, "RESOURCE_CREATED", "RESOURCE", id,
                "Resource created: " + resource.getName());
        return resource;
    }

    public void update(Session session, Resource resource) {
        session.requireAdmin();
        validate(resource);
        Db.withTransactionVoid(conn -> {
            if (resourceDAO.findById(conn, resource.getId()) == null) {
                throw new NotFoundException("Resource not found.");
            }
            resourceDAO.update(conn, resource);
        });
        auditService.record(session, "RESOURCE_UPDATED", "RESOURCE", resource.getId(),
                "Resource updated: " + resource.getName() + " (status " + resource.getStatus() + ")");
    }

    public void setStatus(Session session, long resourceId, ResourceStatus status) {
        session.requireAdmin();
        int rows = Db.withConnection(conn ->
                resourceDAO.setStatus(conn, resourceId, status) ? 1 : 0);
        if (rows == 0) {
            throw new NotFoundException("Resource not found.");
        }
        auditService.record(session, "RESOURCE_STATUS_CHANGED", "RESOURCE", resourceId,
                "Resource status set to " + status.getLabel() + ".");
    }

    /** Hard delete only when no bookings exist; otherwise recommend INACTIVE. */
    public void delete(Session session, long resourceId) {
        session.requireAdmin();
        Db.withTransactionVoid(conn -> {
            Resource resource = resourceDAO.findById(conn, resourceId);
            if (resource == null) {
                throw new NotFoundException("Resource not found.");
            }
            long bookings = resourceDAO.countBookings(conn, resourceId);
            if (bookings > 0) {
                throw new CampusOSException("This resource has " + bookings
                        + " booking record(s). Set it INACTIVE instead of deleting.");
            }
            resourceDAO.delete(conn, resourceId);
            auditService.record(session, "RESOURCE_DELETED", "RESOURCE", resourceId,
                    "Resource deleted: " + resource.getName());
        });
    }

    private void validate(Resource resource) {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, resource.getName(), "Resource name");
        Validator.maxLen(errors, resource.getName(), 100, "Resource name");
        if (resource.getType() == null) {
            errors.add("Resource type is required.");
        }
        if (resource.getCapacity() != null) {
            Validator.positiveInt(errors, resource.getCapacity(), "Capacity");
        }
        Validator.maxLen(errors, resource.getLocation(), 100, "Location");
        Validator.maxLen(errors, resource.getDescription(), 255, "Description");
        Validator.check(errors);
    }
}
