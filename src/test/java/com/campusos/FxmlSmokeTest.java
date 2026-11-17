package com.campusos;

import com.campusos.controller.LoginController;
import com.campusos.controller.ShellController;
import com.campusos.model.Role;
import com.campusos.service.AuthService;
import com.campusos.session.Session;
import com.campusos.ui.View;
import com.campusos.ui.ViewController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deep UI smoke test.
 *
 * 1. Every View mapping must have an existing FXML resource per allowed role.
 * 2. Standalone FXMLs (login/register/shell + dialogs) must load.
 * 3. Every View x Role combination must load through the SAME sequence the
 *    shell uses: construct controller -> initContext(session) -> load() ->
 *    onShow(). This catches missing FXML imports, wrong fx:controller,
 *    fx:id mismatches and session-dependent initialize() code.
 */
class FxmlSmokeTest {

    private static final Pattern FX_CONTROLLER =
            Pattern.compile("fx:controller=\"([^\"]+)\"");

    private static Session adminSession;
    private static Session facultySession;
    private static Session studentSession;

    @BeforeAll
    static void startJavaFxAndLogin() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // toolkit already started - safe to ignore
        }
        // Real sessions so controllers see exactly what the app gives them.
        AuthService auth = new AuthService();
        adminSession = auth.login("admin@campus.edu", "Admin@123");
        facultySession = auth.login("anita.sharma@campus.edu", "Faculty@123");
        studentSession = auth.login("aarav.mehta@student.edu", "Student@123");
    }

    @AfterAll
    static void shutdownJavaFx() {
        Platform.exit();
    }

    @Test
    @DisplayName("every View mapping has an existing FXML resource for each allowed role")
    void everyViewHasFxmlResource() {
        List<String> missing = new ArrayList<>();
        for (View view : View.values()) {
            for (Role role : Role.values()) {
                if (!view.allows(role)) {
                    continue;
                }
                String fxml = "/fxml/" + view.fxmlFor(role);
                if (getClass().getResource(fxml) == null) {
                    missing.add(view + " as " + role + " -> " + fxml);
                }
            }
        }
        assertTrue(missing.isEmpty(), () -> "Missing FXML resources:\n" + String.join("\n", missing));
    }

    @Test
    @DisplayName("fx:controller in every View FXML matches the View registry")
    void controllerRegistryMatchesFxml() throws Exception {
        List<String> mismatches = new ArrayList<>();
        for (View view : View.values()) {
            for (Role role : Role.values()) {
                if (!view.allows(role)) {
                    continue;
                }
                URL fxml = getClass().getResource("/fxml/" + view.fxmlFor(role));
                String text = Files.readString(Paths.get(fxml.toURI()), StandardCharsets.UTF_8);
                Matcher matcher = FX_CONTROLLER.matcher(text);
                assertTrue(matcher.find(), view + ": FXML has no fx:controller");
                String declared = matcher.group(1);
                String registered = view.controllerFor(role).getName();
                if (!declared.equals(registered)) {
                    mismatches.add(view + " as " + role + ": FXML says " + declared
                            + ", registry says " + registered);
                }
            }
        }
        assertTrue(mismatches.isEmpty(), () ->
                "fx:controller / View registry mismatches:\n" + String.join("\n", mismatches));
    }

    @Test
    @DisplayName("standalone FXMLs (login, register, shell, dialogs) load cleanly")
    void standaloneFxmlsLoad() {
        List<String> fxmlFiles = List.of(
                "login.fxml", "register.fxml", "shell.fxml",
                "dialog-user-edit.fxml", "dialog-resource-edit.fxml", "dialog-booking.fxml",
                "dialog-complaint-detail.fxml", "dialog-event-edit.fxml",
                "dialog-event-participants.fxml");

        List<String> failures = collectLoadFailures(fxmlFiles, null);
        assertTrue(failures.isEmpty(), () ->
                "Broken FXML files (" + failures.size() + "):\n" + String.join("\n", failures));
    }

    @Test
    @DisplayName("every View x Role loads through the shell sequence AND renders with data")
    void everyViewLoadsThroughShellSequence() throws Exception {
        List<String> failures = new ArrayList<>();
        for (View view : View.values()) {
            for (Role role : Role.values()) {
                if (!view.allows(role)) {
                    continue;
                }
                Session session = sessionFor(role);
                try {
                    URL url = getClass().getResource("/fxml/" + view.fxmlFor(role));
                    FXMLLoader loader = new FXMLLoader(url);
                    ViewController controller =
                            view.controllerFor(role).getDeclaredConstructor().newInstance();
                    loader.setControllerFactory(requested -> {
                        if (!requested.equals(view.controllerFor(role))) {
                            throw new IllegalStateException("fx:controller mismatch: " + requested);
                        }
                        return controller;
                    });
                    final Parent[] rootHolder = new Parent[1];
                    runOnFx(() -> { });
                    controller.initContext(session, null);   // BEFORE load, as the shell does
                    rootHolder[0] = loader.load();           // initialize() runs here
                    controller.onShow();                     // background data loads start

                    // Give the background DB tasks time to deliver results.
                    Thread.sleep(1500);
                    runOnFx(() -> { });

                    // Render the view off-screen: full layout + CSS + table cells + charts.
                    verifyRendered(view, role, rootHolder[0]);
                } catch (Throwable t) {
                    failures.add(view + " as " + role + " -> " + deepestCause(t));
                }
            }
        }
        assertTrue(failures.isEmpty(), () ->
                "Broken views (" + failures.size() + "):\n" + String.join("\n", failures));
    }

    /**
     * Attaches the view to an off-screen scene, forces a full layout pass and
     * snapshot (which renders table cells, charts and badges), then asserts
     * the database actually fed at least one TableView with rows.
     */
    private void verifyRendered(View view, Role role, Parent root) throws Exception {
        final AtomicReference<Throwable> renderError = new AtomicReference<>();
        final AtomicBoolean hasPopulatedTable = new AtomicBoolean(false);
        final CountDownLatch rendered = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Scene scene = new Scene(root, 1280, 720);
                root.applyCss();
                root.layout();
                WritableImage image = root.snapshot(new SnapshotParameters(), null);
                if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                    throw new IllegalStateException("rendered snapshot is empty");
                }
                boolean anyTable = false;
                for (javafx.scene.Node node : root.lookupAll(".table-view")) {
                    if (node instanceof TableView<?> table) {
                        anyTable = true;
                        if (!table.getItems().isEmpty()) {
                            hasPopulatedTable.set(true);
                        }
                    }
                }
                // Views with tables must show real database rows; views without
                // tables (profile, dialogs) only need to render. Faculty accounts
                // legitimately have no submitted complaints in the sample data.
                boolean emptyAllowed = (view == View.MY_COMPLAINTS && role == Role.FACULTY)
                        || view == View.PROFILE;
                if (anyTable && !hasPopulatedTable.get() && !emptyAllowed) {
                    throw new IllegalStateException(
                            "no TableView received data from MySQL (all tables empty)");
                }
            } catch (Throwable t) {
                renderError.set(t);
            } finally {
                rendered.countDown();
            }
        });
        if (!rendered.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "render timed out after 20s - the FX thread is blocked (an error dialog?)");
        }
        Throwable error = renderError.get();
        if (error != null) {
            throw new IllegalStateException("render failed", error);
        }
    }

    /** Runs a runnable on the FX thread and waits for it to complete. */
    private static void runOnFx(Runnable runnable) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                runnable.run();
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX thread did not respond within 10s");
        }
    }

    private Session sessionFor(Role role) {
        return switch (role) {
            case ADMIN -> adminSession;
            case FACULTY -> facultySession;
            case STUDENT -> studentSession;
        };
    }

    /** Loads each standalone file directly; returns human-readable failures. */
    private List<String> collectLoadFailures(List<String> fxmlFiles, Session unused) {
        List<String> failures = new ArrayList<>();
        for (String fxml : fxmlFiles) {
            try {
                URL url = getClass().getResource("/fxml/" + fxml);
                assertNotNull(url, fxml + " missing from resources");
                FXMLLoader loader = new FXMLLoader(url);
                Parent root = loader.load();
                assertNotNull(root, fxml + " produced no root node");
                if (fxml.equals("shell.fxml")) {
                    assertInstanceOf(ShellController.class, loader.getController());
                }
                if (fxml.equals("login.fxml")) {
                    assertInstanceOf(LoginController.class, loader.getController());
                }
            } catch (Throwable t) {
                failures.add(fxml + " -> " + deepestCause(t));
            }
        }
        return failures;
    }

    private static String deepestCause(Throwable t) {
        Throwable result = t;
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result.getClass().getSimpleName() + ": " + result.getMessage();
    }
}
