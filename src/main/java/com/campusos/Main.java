package com.campusos;

import com.campusos.config.DatabaseConfig;
import com.campusos.exception.CampusOSException;
import com.campusos.controller.LoginController;
import com.campusos.session.Session;
import javafx.scene.control.Alert;
import com.campusos.util.Alerts;
import com.campusos.util.Stylesheet;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * JavaFX application entry. Shows the login screen; after a successful
 * login the shell window opens (see LoginController → ShellController).
 */
public class Main extends Application {

    /** Stylesheet location inside the built classpath. */
    public static final String CSS = Stylesheet.path();

    @Override
    public void start(Stage stage) {
        // Fail fast with a friendly message when the DB is not configured.
        try {
            DatabaseConfig.get();
        } catch (CampusOSException configError) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("CampusOS - configuration problem");
            alert.setHeaderText("Database configuration");
            alert.setContentText(configError.getMessage());
            alert.showAndWait();
            Platform.exit();
            return;
        }

        URL loginFxml = getClass().getResource("/fxml/login.fxml");
        if (loginFxml == null) {
            Alerts.error("Startup error", "Cannot find /fxml/login.fxml in the application resources.");
            Platform.exit();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(loginFxml);
            Parent root = loader.load();
            Scene scene = new Scene(root, 440, 560);
            scene.getStylesheets().add(CSS);

            stage.setTitle("CampusOS - Sign in");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
            stage.show();

            LoginController controller = loader.getController();
            controller.setStage(stage);
        } catch (Exception e) {
            Alerts.error("Startup error", e);
            Platform.exit();
        }
    }

    /** Opens the main shell window after login and closes the login window. */
    public static void openShell(Session session, Stage loginStage) {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/shell.fxml"));
            Parent root = loader.load();
            Stage shellStage = new Stage();
            Scene scene = new Scene(root, 1280, 800);
            scene.getStylesheets().add(CSS);
            shellStage.setTitle("CampusOS - Smart Campus Management System");
            shellStage.setMinWidth(1080);
            shellStage.setMinHeight(680);
            shellStage.setScene(scene);
            shellStage.setMaximized(true);
            shellStage.centerOnScreen();

            com.campusos.controller.ShellController shellController = loader.getController();
            shellController.start(session, shellStage);

            shellStage.show();
            shellStage.toFront();
            if (loginStage != null) {
                loginStage.close();
            }
        } catch (Exception e) {
            Alerts.error("Could not open the main window", e);
            Platform.exit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
