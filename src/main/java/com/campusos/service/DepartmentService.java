package com.campusos.service;

import com.campusos.dao.Db;
import com.campusos.dao.DepartmentDAO;
import com.campusos.exception.CampusOSException;
import com.campusos.exception.NotFoundException;
import com.campusos.model.Department;
import com.campusos.session.Session;
import com.campusos.validation.Validator;

import java.util.List;

/** Department management (Module 1) - administrator only. */
public class DepartmentService {

    private final DepartmentDAO departmentDAO = new DepartmentDAO();
    private final AuditService auditService = new AuditService();

    public List<Department> listAll(Session session) {
        session.requireAdmin();
        return Db.withConnection(departmentDAO::findAll);
    }

    /** Active departments for registration and user forms - no auth needed (read-only). */
    public List<Department> listActive() {
        return Db.withConnection(departmentDAO::findActive);
    }

    public Department create(Session session, Department department) {
        session.requireAdmin();
        validate(department);
        long id = Db.withTransaction(conn -> {
            if (departmentDAO.existsByName(conn, department.getName(), null)) {
                throw new CampusOSException("A department with this name already exists.");
            }
            if (departmentDAO.existsByCode(conn, department.getCode(), null)) {
                throw new CampusOSException("A department with this code already exists.");
            }
            return departmentDAO.insert(conn, department);
        });
        department.setId(id);
        auditService.record(session, "DEPARTMENT_CREATED", "DEPARTMENT", id,
                "Department created: " + department.getName());
        return department;
    }

    public void update(Session session, Department department) {
        session.requireAdmin();
        validate(department);
        Db.withTransactionVoid(conn -> {
            if (departmentDAO.findById(conn, department.getId()) == null) {
                throw new NotFoundException("Department not found.");
            }
            if (departmentDAO.existsByName(conn, department.getName(), department.getId())) {
                throw new CampusOSException("Another department already uses this name.");
            }
            if (departmentDAO.existsByCode(conn, department.getCode(), department.getId())) {
                throw new CampusOSException("Another department already uses this code.");
            }
            departmentDAO.update(conn, department);
        });
        auditService.record(session, "DEPARTMENT_UPDATED", "DEPARTMENT", department.getId(),
                "Department updated: " + department.getName());
    }

    /** Deactivate when users are attached; hard-delete only when empty. */
    public void remove(Session session, long departmentId) {
        session.requireAdmin();
        Db.withTransactionVoid(conn -> {
            Department department = departmentDAO.findById(conn, departmentId);
            if (department == null) {
                throw new NotFoundException("Department not found.");
            }
            long users = departmentDAO.countUsers(conn, departmentId);
            if (users > 0) {
                throw new CampusOSException("This department has " + users
                        + " user(s) attached. Deactivate it instead of deleting.");
            }
            departmentDAO.delete(conn, departmentId);
            auditService.record(session, "DEPARTMENT_DELETED", "DEPARTMENT", departmentId,
                    "Department deleted: " + department.getName());
        });
    }

    private void validate(Department department) {
        List<String> errors = Validator.newErrorList();
        Validator.nonBlank(errors, department.getName(), "Department name");
        Validator.maxLen(errors, department.getName(), 100, "Department name");
        Validator.nonBlank(errors, department.getCode(), "Department code");
        Validator.maxLen(errors, department.getCode(), 10, "Department code");
        Validator.maxLen(errors, department.getDescription(), 255, "Description");
        Validator.check(errors);
    }
}
