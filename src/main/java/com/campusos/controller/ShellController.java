package com.campusos.controller;

import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.session.Session;
import com.campusos.ui.Navigator;
import com.campusos.ui.ViewController;
import com.campusos.ui.View;
import com.campusos.util.Alerts;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.EnumSet;

/**
 * Main application shell: sidebar navigation, top bar and the content
 * host that swaps in each module view. The sidebar is built dynamically
 * from the View enum filtered by the logged-in user's role.
 */
public class ShellController implements Navigator {

    @FXML private VBox sidebar;
    @FXML private VBox navButtons;
    @FXML private Button logoutButton;
    @FXML private Label sidebarUserName;
    @FXML private Label sidebarUserRole;
    @FXML private Label viewTitle;
    @FXML private Label roleChip;
    @FXML private Label topbarUser;
    @FXML private BorderPane contentHost;

    private Session session;
    private Stage stage;
    private View currentView;

    /** Called by Main after loading shell.fxml, before the stage is shown. */
    public void start(Session session, Stage stage) {
        this.session = session;
        this.stage = stage;
        User user = session.currentUser();
        Role role = user.getRole();

        sidebarUserName.setText(user.getFullName());
        sidebarUserRole.setText(user.getDepartmentName() == null
                ? role.getLabel() : role.getLabel() + " • " + user.getDepartmentName());
        roleChip.setText(role.getLabel().toUpperCase());
        topbarUser.setText(user.getFullName() + "  ·  " + user.getEmail());

        navButtons.getChildren().clear();
        for (View view : View.values()) {
            if (!view.allows(role)) {
                continue;
            }
            Button navButton = new Button(navLabel(view));
            navButton.getStyleClass().add("nav-item");
            navButton.setMaxWidth(Double.MAX_VALUE);
            navButton.setOnAction(event -> navigate(view));
            navButton.setUserData(view);
            navButtons.getChildren().add(navButton);
        }

        navigate(View.DASHBOARD);
    }

    private String navLabel(View view) {
        return switch (view) {
            case DASHBOARD -> "▦  Dashboard";
            case USERS -> "👥  User Management";
            case DEPARTMENTS -> "🏛  Departments";
            case RESOURCES -> "🏫  Campus Resources";
            case BOOKINGS -> "🗓  Booking Approvals";
            case MY_BOOKINGS -> "🗓  My Bookings";
            case COMPLAINTS -> "🎧  Complaint Management";
            case MY_COMPLAINTS -> "🎧  My Complaints";
            case EVENTS_MANAGE -> "🎪  Event Management";
            case EVENTS_BROWSE -> "🎪  Campus Events";
            case ANALYTICS -> "📊  Analytics & Reports";
            case AUDIT_LOGS -> "🛡  Audit Logs";
            case PROFILE -> "👤  My Profile";
        };
    }

    /**
     * Loads a view into the content host and refreshes it.
     *
     * Order matters: the controller is created FIRST, receives the session
     * via initContext, and only then is the FXML loaded - so initialize()
     * can safely use the session. The View enum's controller registry must
     * agree with the fx:controller attribute; the factory enforces that.
     */
    @Override
    public void navigate(View view) {
        Role role = session.currentUser().getRole();
        if (!view.allows(role)) {
            Alerts.error("Access denied", "Your role is not permitted to open " + view.getTitle() + ".");
            return;
        }
        currentView = view;

        String fxmlName = view.fxmlFor(role);
        URL fxmlUrl = getClass().getResource("/fxml/" + fxmlName);
        if (fxmlUrl == null) {
            Alerts.error("Navigation error", "View resource not found: /fxml/" + fxmlName);
            return;
        }

        try {
            Class<? extends ViewController> controllerClass = view.controllerFor(role);
            ViewController controller = controllerClass.getDeclaredConstructor().newInstance();

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setControllerFactory(requested -> {
                if (!requested.equals(controllerClass)) {
                    throw new IllegalStateException("FXML declares " + requested.getName()
                            + " but the View registry expects " + controllerClass.getName()
                            + " - keep fx:controller and View in sync.");
                }
                return controller;
            });

            controller.initContext(session, this);   // BEFORE load()
            Parent root = loader.load();             // initialize() runs here
            controller.onShow();                     // fresh data on every visit

            viewTitle.setText(view.getTitle());
            contentHost.setCenter(root);

            // Highlight the active sidebar item
            for (Node node : navButtons.getChildren()) {
                boolean active = ((Button) node).getUserData() == view;
                if (active) {
                    ((Button) node).getStyleClass().add("nav-active");
                } else {
                    ((Button) node).getStyleClass().remove("nav-active");
                }
            }
        } catch (ReflectiveOperationException | IOException e) {
            Alerts.error("Could not open " + view.getTitle(), e);
        }
    }

    @Override
    public void refreshCurrentView() {
        if (currentView != null) {
            navigate(currentView);
        }
    }

    @FXML
    private void handleLogout() {
        boolean confirmed = Alerts.confirm("Sign out",
                "Do you want to sign out of CampusOS?");
        if (!confirmed) {
            return;
        }
        new com.campusos.service.AuditService().record(session, "USER_LOGOUT", "USER",
                session.currentUserId(), session.currentUser().getFullName() + " logged out.");
        session.logout();
        stage.close();
        // Reopen the login window.
        Platform.runLater(() -> {
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                        getClass().getResource("/fxml/login.fxml"));
                Parent root = loader.load();
                javafx.scene.Scene scene = new javafx.scene.Scene(root, 440, 560);
                scene.getStylesheets().add(com.campusos.Main.CSS);
                javafx.stage.Stage loginStage = new javafx.stage.Stage();
                loginStage.setTitle("CampusOS - Sign in");
                loginStage.setScene(scene);
                loginStage.setResizable(false);
                loginStage.centerOnScreen();
                loginStage.show();
                com.campusos.controller.LoginController controller = loader.getController();
                controller.setStage(loginStage);
            } catch (IOException e) {
                Alerts.error("Could not reopen the login window", e);
            }
        });
    }
}
