package com.campusos.controller;

import com.campusos.service.AuthService;
import com.campusos.service.UserService;
import com.campusos.session.Session;
import com.campusos.ui.Navigator;
import com.campusos.ui.ViewController;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import com.campusos.util.Fmt;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * My Profile (all roles): view account details, update basic contact info,
 * and change password (old password must verify before the new hash is stored).
 */
public class ProfileController implements ViewController {

    @FXML private Label idLabel;
    @FXML private Label roleLabel;
    @FXML private Label emailLabel;
    @FXML private Label departmentLabel;
    @FXML private Label identifierLabel;
    @FXML private Label createdLabel;
    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private Button saveProfileButton;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button changePasswordButton;
    @FXML private Label passwordMessage;

    private Session session;
    private Navigator navigator;
    private final UserService userService = new UserService();
    private final AuthService authService = new AuthService();

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        passwordMessage.visibleProperty().bind(passwordMessage.textProperty().isNotEmpty());
        passwordMessage.managedProperty().bind(passwordMessage.textProperty().isNotEmpty());
    }

    @Override
    public void onShow() {
        render(session.currentUser());
    }

    private void render(com.campusos.model.User user) {
        idLabel.setText("#" + user.getId());
        roleLabel.setText(user.getRole().getLabel());
        emailLabel.setText(user.getEmail());
        departmentLabel.setText(user.getDepartmentName() == null ? "—" : user.getDepartmentName());
        identifierLabel.setText(user.getIdentifier() == null ? "—" : user.getIdentifier());
        createdLabel.setText(Fmt.datetime(user.getCreatedAt()));

        nameField.setText(user.getFullName());
        emailField.setText(user.getEmail());
        phoneField.setText(user.getPhone() == null ? "" : user.getPhone());
    }

    @FXML
    private void handleSaveProfile() {
        saveProfileButton.setDisable(true);
        Bg.run(() -> {
            userService.updateOwnProfile(session, nameField.getText(), emailField.getText(),
                    phoneField.getText());
            // Re-read the user so the UI shows fresh data
            return userService.getById(session, session.currentUserId());
        }, user -> {
            saveProfileButton.setDisable(false);
            session.login(user);
            render(user);
            Alerts.info("Profile saved", "Your profile was updated successfully.");
        }, error -> {
            saveProfileButton.setDisable(false);
            Alerts.error("Could not save profile", error.getMessage());
        });
    }

    @FXML
    private void handleChangePassword() {
        passwordMessage.getStyleClass().removeAll("error-banner", "info-banner");
        changePasswordButton.setDisable(true);
        Bg.run(() -> {
            authService.changePassword(session, currentPasswordField.getText(),
                    newPasswordField.getText(), confirmPasswordField.getText());
            return (Void) null;
        }, result -> {
            changePasswordButton.setDisable(false);
            currentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
            passwordMessage.getStyleClass().add("info-banner");
            passwordMessage.setText("Password changed successfully.");
            Alerts.info("Password changed", "Your password has been updated.");
        }, error -> {
            changePasswordButton.setDisable(false);
            passwordMessage.getStyleClass().add("error-banner");
            passwordMessage.setText(error.getMessage());
        });
    }
}
