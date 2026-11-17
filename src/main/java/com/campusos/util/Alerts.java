package com.campusos.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Consistent, styled dialogs for information, errors and confirmations.
 */
public final class Alerts {

    private Alerts() { }

    public static void info(String title, String message) {
        Alert alert = base(Alert.AlertType.INFORMATION, title, message);
        alert.showAndWait();
    }

    public static void error(String title, String message) {
        Alert alert = base(Alert.AlertType.ERROR, title, message);
        alert.showAndWait();
    }

    /** Builds (but does not show) an error dialog - used by validation forms. */
    public static Alert errorAlert(String title, String message) {
        return base(Alert.AlertType.ERROR, title, message);
    }

    /** Error dialog with an expandable stack trace (for unexpected failures). */
    public static void error(String title, Throwable throwable) {
        String message = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
        Alert alert = base(Alert.AlertType.ERROR, title, message);

        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        TextArea details = new TextArea(sw.toString());
        details.setEditable(false);
        details.getStyleClass().add("stack-trace");
        details.setPrefSize(560, 180);
        VBox box = new VBox(details);
        alert.getDialogPane().setContent(box);
        alert.showAndWait();
    }

    public static boolean confirm(String title, String message) {
        Alert alert = base(Alert.AlertType.CONFIRMATION, title, message);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    private static Alert base(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Label label = new Label(message);
        label.setWrapText(true);
        label.getStyleClass().add("alert-message");
        alert.getDialogPane().setContent(label);
        Stylesheet.applyTo(alert.getDialogPane());
        return alert;
    }
}
