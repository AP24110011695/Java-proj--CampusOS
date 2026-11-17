package com.campusos.ui;

import com.campusos.controller.AnalyticsController;
import com.campusos.controller.AuditLogController;
import com.campusos.controller.BookingsAdminController;
import com.campusos.controller.ComplaintsAdminController;
import com.campusos.controller.DashboardAdminController;
import com.campusos.controller.DashboardFacultyController;
import com.campusos.controller.DashboardStudentController;
import com.campusos.controller.DepartmentsController;
import com.campusos.controller.EventsBrowseController;
import com.campusos.controller.EventsManageController;
import com.campusos.controller.MyBookingsController;
import com.campusos.controller.MyComplaintsController;
import com.campusos.controller.ProfileController;
import com.campusos.controller.ResourcesController;
import com.campusos.controller.UserManagementController;
import com.campusos.model.Role;

/**
 * Every navigable screen with the roles allowed to see it, the FXML file
 * and the controller class. The shell injects the session into the
 * controller BEFORE the FXML loads, so initialize() always has context.
 */
public enum View {

    DASHBOARD("Dashboard", Role.ADMIN, Role.FACULTY, Role.STUDENT),
    USERS("User Management", "users.fxml", UserManagementController.class,
            Role.ADMIN),
    DEPARTMENTS("Departments", "departments.fxml", DepartmentsController.class,
            Role.ADMIN),
    RESOURCES("Campus Resources", "resources.fxml", ResourcesController.class,
            Role.ADMIN, Role.FACULTY, Role.STUDENT),
    BOOKINGS("Booking Approvals", "bookings-admin.fxml", BookingsAdminController.class,
            Role.ADMIN),
    MY_BOOKINGS("My Bookings", "my-bookings.fxml", MyBookingsController.class,
            Role.FACULTY, Role.STUDENT),
    COMPLAINTS("Complaint Management", "complaints-admin.fxml", ComplaintsAdminController.class,
            Role.ADMIN),
    MY_COMPLAINTS("My Complaints", "my-complaints.fxml", MyComplaintsController.class,
            Role.FACULTY, Role.STUDENT),
    EVENTS_MANAGE("Event Management", "events-manage.fxml", EventsManageController.class,
            Role.ADMIN, Role.FACULTY),
    EVENTS_BROWSE("Campus Events", "events-browse.fxml", EventsBrowseController.class,
            Role.ADMIN, Role.FACULTY, Role.STUDENT),
    ANALYTICS("Analytics & Reports", "analytics.fxml", AnalyticsController.class,
            Role.ADMIN),
    AUDIT_LOGS("Audit Logs", "audit-logs.fxml", AuditLogController.class,
            Role.ADMIN),
    PROFILE("My Profile", "profile.fxml", ProfileController.class,
            Role.ADMIN, Role.FACULTY, Role.STUDENT);

    private final String title;
    private final String fxmlBase;   // null for DASHBOARD (role decides the file)
    private final Class<? extends ViewController> controllerAdmin;
    private final Class<? extends ViewController> controllerFaculty;
    private final Class<? extends ViewController> controllerStudent;
    private final Role[] roles;

    /** Single controller for every role. */
    View(String title, String fxmlBase, Class<? extends ViewController> controller,
         Role... roles) {
        this(title, fxmlBase, controller, controller, controller, roles);
    }

    /** DASHBOARD: role-specific fxml + controller resolved per role. */
    View(String title, Role... roles) {
        this(title, null, DashboardAdminController.class,
                DashboardFacultyController.class, DashboardStudentController.class, roles);
    }

    View(String title, String fxmlBase,
         Class<? extends ViewController> admin, Class<? extends ViewController> faculty,
         Class<? extends ViewController> student, Role... roles) {
        this.title = title;
        this.fxmlBase = fxmlBase;
        this.controllerAdmin = admin;
        this.controllerFaculty = faculty;
        this.controllerStudent = student;
        this.roles = roles;
    }

    public String getTitle() {
        return title;
    }

    public boolean allows(Role role) {
        for (Role r : roles) {
            if (r == role) {
                return true;
            }
        }
        return false;
    }

    /** FXML file (inside /fxml) for this view given the viewer's role. */
    public String fxmlFor(Role role) {
        if (fxmlBase != null) {
            return fxmlBase;
        }
        return switch (role) {
            case ADMIN -> "dashboard-admin.fxml";
            case FACULTY -> "dashboard-faculty.fxml";
            case STUDENT -> "dashboard-student.fxml";
        };
    }

    /** Controller class for this view given the viewer's role. */
    public Class<? extends ViewController> controllerFor(Role role) {
        if (fxmlBase != null) {
            return controllerAdmin;
        }
        return switch (role) {
            case ADMIN -> controllerAdmin;
            case FACULTY -> controllerFaculty;
            case STUDENT -> controllerStudent;
        };
    }
}
