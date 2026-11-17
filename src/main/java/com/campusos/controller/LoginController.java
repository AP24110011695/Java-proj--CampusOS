package com.campusos.controller;

import com.campusos.Main;
import com.campusos.service.AuthService;
import com.campusos.session.Session;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Login screen controller. Authenticates via AuthService (BCrypt verify)
 * in the background and opens the role-specific main shell on success.
 */
public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private javafx.scene.control.Button loginButton;
    @FXML private Label errorBanner;
    @FXML private Label statusLabel;

    private final AuthService authService = new AuthService();
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void initialize() {
        errorBanner.visibleProperty().bind(errorBanner.textProperty().isNotEmpty());
        errorBanner.managedProperty().bind(errorBanner.textProperty().isNotEmpty());
        emailField.requestFocus();
    }

    @FXML
    private void handleLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();
        errorBanner.setText("");
        statusLabel.setText("Signing in...");
        loginButton.setDisable(true);

        Bg.run(() -> authService.login(email, password), session -> {
            statusLabel.setText("");
            loginButton.setDisable(false);
            Main.openShell(session, stage);
        }, this::showLoginError);
    }

    private void showLoginError(Throwable error) {
        loginButton.setDisable(false);
        statusLabel.setText("");
        if (error instanceof com.campusos.exception.AuthenticationException
                || error instanceof com.campusos.exception.ValidationException
                || error instanceof com.campusos.exception.CampusOSException) {
            errorBanner.setText(error.getMessage());
        } else {
            Alerts.error("Sign-in failed", error);
        }
    }

    @FXML
    private void handleOpenRegister() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/fxml/register.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 560, 640);
            scene.getStylesheets().add(Main.CSS);
            javafx.stage.Stage registerStage = new javafx.stage.Stage();
            registerStage.setTitle("CampusOS - Student registration");
            registerStage.setScene(scene);
            registerStage.setResizable(false);
            registerStage.initOwner(stage);
            registerStage.showAndWait();
        } catch (Exception e) {
            Alerts.error("Could not open registration", e);
        }
    }
}
