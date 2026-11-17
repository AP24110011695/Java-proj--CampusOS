package com.campusos.controller;

import com.campusos.model.Department;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.service.DepartmentService;
import com.campusos.util.Alerts;
import javafx.scene.control.Alert;
import com.campusos.util.Bg;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.List;

/**
 * Add/Edit user form used inside a modal JavaFX Dialog by the
 * user management screen. Roles: Student, Faculty, Administrator.
 */
public class UserEditDialogController {

    @FXML private Label formError;
    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private ComboBox<Role> roleCombo;
    @FXML private ComboBox<Department> departmentCombo;
    @FXML private TextField identifierField;
    @FXML private TextField phoneField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Label identifierLabel;
    @FXML private Label passwordLabel;

    private final DepartmentService departmentService = new DepartmentService();
    private List<Department> departments = List.of();
    private Long editingId;   // null while creating a new account

    /** Simple result holder returned to the caller. */
    public record FormOutcome(User user, String password, Alert error) { }

    @FXML
    private void initialize() {
        formError.visibleProperty().bind(formError.textProperty().isNotEmpty());
        formError.managedProperty().bind(formError.textProperty().isNotEmpty());

        roleCombo.getItems().setAll(Role.STUDENT, Role.FACULTY, Role.ADMIN);
        roleCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Role role) { return role == null ? "" : role.getLabel(); }
            @Override public Role fromString(String s) { return null; }
        });
        roleCombo.valueProperty().addListener((obs, old, role) ->
                identifierLabel.setText(role == Role.STUDENT ? "Student ID" : "Employee ID"));

        statusCombo.getItems().setAll("Active", "Inactive");
        statusCombo.getSelectionModel().selectFirst();

        passwordLabel.setText("Password (required)");

        departmentCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Department d) { return d == null ? "— none —" : d.getName(); }
            @Override public Department fromString(String s) { return null; }
        });

        Bg.run(() -> departmentService.listActive(), departments -> {
            this.departments = departments;
            List<Department> items = new java.util.ArrayList<>();
            items.add(null);
            items.addAll(departments);
            departmentCombo.getItems().setAll(items);
        });
    }

    /** Prefills the form for editing, or leaves it empty for a new account. */
    public void load(User existing) {
        if (existing == null) {
            editingId = null;
            return;
        }
        editingId = existing.getId();
        nameField.setText(existing.getFullName());
        emailField.setText(existing.getEmail());
        roleCombo.setValue(existing.getRole());
        identifierField.setText(existing.getIdentifier());
        phoneField.setText(existing.getPhone());
        statusCombo.setValue(existing.isActive() ? "Active" : "Inactive");
        passwordLabel.setText("New password (leave empty to keep the current one)");
        for (Department d : departments) {
            if (d != null && existing.getDepartmentId() != null
                    && d.getId().equals(existing.getDepartmentId())) {
                departmentCombo.setValue(d);
            }
        }
    }

    /** Validates the form. On failure returns an error alert to display. */
    public FormOutcome buildOutcome(boolean isCreate) {
        User user = new User();
        user.setFullName(nameField.getText());
        user.setEmail(emailField.getText());
        user.setRole(roleCombo.getValue());
        Department department = departmentCombo.getValue();
        user.setDepartmentId(department == null ? null : department.getId());
        user.setIdentifier(identifierField.getText());
        user.setPhone(phoneField.getText());
        user.setActive("Active".equals(statusCombo.getValue()));

        if (!isCreate) {
            user.setId(editingId);
        }

        String password = passwordField.getText();
        if (!isCreate && password.isBlank()) {
            password = null; // keep existing password
        }

        // Basic client-side sanity checks; the service re-validates everything.
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            problems.add("Full name is required.");
        }
        if (user.getEmail() == null || !user.getEmail().contains("@")) {
            problems.add("A valid email is required.");
        }
        if (user.getRole() == null) {
            problems.add("Role is required.");
        }
        if (isCreate && (password == null || password.isBlank())) {
            problems.add("Password is required for a new account.");
        }
        if (!problems.isEmpty()) {
            return new FormOutcome(null, null,
                    Alerts.errorAlert("Please fix the form", String.join("\n", problems)));
        }
        return new FormOutcome(user, password, null);
    }
}
