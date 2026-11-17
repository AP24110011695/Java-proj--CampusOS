package com.campusos.controller;

import com.campusos.model.Department;
import com.campusos.model.User;
import com.campusos.service.AuthService;
import com.campusos.service.DepartmentService;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Student self-registration screen. Role is always STUDENT here - faculty
 * and admin accounts can only be created by an administrator.
 */
public class RegisterController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private TextField identifierField;
    @FXML private TextField phoneField;
    @FXML private ComboBox<Department> departmentCombo;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmField;
    @FXML private Button registerButton;
    @FXML private Label errorBanner;
    @FXML private Label successLabel;

    private final AuthService authService = new AuthService();
    private final DepartmentService departmentService = new DepartmentService();

    @FXML
    private void initialize() {
        errorBanner.visibleProperty().bind(errorBanner.textProperty().isNotEmpty());
        errorBanner.managedProperty().bind(errorBanner.textProperty().isNotEmpty());
        successLabel.visibleProperty().bind(successLabel.textProperty().isNotEmpty());
        successLabel.managedProperty().bind(successLabel.textProperty().isNotEmpty());

        departmentCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Department d) { return d == null ? null : d.getName(); }
            @Override public Department fromString(String s) { return null; }
        });

        Bg.run(() -> departmentService.listActive(),
                departments -> departmentCombo.setItems(FXCollections.observableList(departments)));
    }

    @FXML
    private void handleRegister() {
        errorBanner.setText("");
        successLabel.setText("");
        registerButton.setDisable(true);

        Department department = departmentCombo.getValue();
        Bg.run(() -> authService.registerStudent(
                        nameField.getText(), emailField.getText(), phoneField.getText(),
                        identifierField.getText(),
                        department == null ? null : department.getId(),
                        passwordField.getText(), confirmField.getText()),
                user -> {
                    registerButton.setDisable(false);
                    successLabel.setText("Account created successfully! You can now sign in as "
                            + user.getEmail() + ".");
                    clearForm();
                },
                error -> {
                    registerButton.setDisable(false);
                    errorBanner.setText(error.getMessage());
                });
    }

    private void clearForm() {
        nameField.clear();
        emailField.clear();
        identifierField.clear();
        phoneField.clear();
        passwordField.clear();
        confirmField.clear();
        departmentCombo.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) registerButton.getScene().getWindow();
        stage.close();
    }
}
