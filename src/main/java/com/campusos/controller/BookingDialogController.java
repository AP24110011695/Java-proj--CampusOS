package com.campusos.controller;

import com.campusos.model.Booking;
import com.campusos.model.Resource;
import com.campusos.service.BookingService;
import com.campusos.service.ResourceService;
import com.campusos.session.Session;
import com.campusos.ui.Navigator;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;
import javafx.stage.Modality;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * "Request a booking" dialog (Module 2). Students and faculty pick a
 * resource, date and half-hour time window. Submit runs in the background;
 * the dialog stays open with the error banner if the service rejects the
 * request (validation or time conflict), and closes on success.
 */
public class BookingDialogController {

    @FXML private Label formError;
    @FXML private Label resourceSummary;
    @FXML private ComboBox<Resource> resourceCombo;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<LocalTime> startCombo;
    @FXML private ComboBox<LocalTime> endCombo;
    @FXML private TextField purposeField;

    private Session session;
    private Navigator navigator;
    private final BookingService bookingService = new BookingService();
    private final ResourceService resourceService = new ResourceService();

    /** Opens the modal dialog; on success refreshes the calling view. */
    public static void showDialog(Session session, Navigator navigator, Long preselectedResourceId) {
        Dialog<ButtonType> dialog;
        BookingDialogController controller;
        try {
            FXMLLoader loader = new FXMLLoader(
                    BookingDialogController.class.getResource("/fxml/dialog-booking.fxml"));
            dialog = new Dialog<>();
            dialog.setDialogPane(loader.load());
            controller = loader.getController();
        } catch (Exception e) {
            Alerts.error("Could not open the booking form", e);
            return;
        }
        dialog.setTitle("Request a booking");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
        Button submitButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.APPLY);
        submitButton.setText("Submit request");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancel");

        controller.init(session, navigator, preselectedResourceId);

        // The APPLY button must not close the dialog: we validate first and
        // close manually only after the service confirms the request.
        submitButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            String validationError = controller.validate();
            if (validationError != null) {
                controller.formError.setText(validationError);
                return;
            }
            controller.formError.setText("");
            submitButton.setDisable(true);
            submitButton.setText("Submitting...");
            controller.submitAsync(booking -> {
                        dialog.close();
                        Alerts.info("Booking requested",
                                "Your request for " + booking.getResourceName() + " on "
                                        + booking.getBookingDate() + " ("
                                        + booking.getStartTime() + "–" + booking.getEndTime()
                                        + ") was submitted and is now PENDING approval.");
                        navigator.refreshCurrentView();
                    },
                    error -> {
                        submitButton.setDisable(false);
                        submitButton.setText("Submit request");
                        controller.formError.setText(error.getMessage());
                    });
        });

        dialog.showAndWait();
    }

    public void init(Session session, Navigator navigator, Long preselectedResourceId) {
        this.session = session;
        this.navigator = navigator;

        formError.visibleProperty().bind(formError.textProperty().isNotEmpty());
        formError.managedProperty().bind(formError.textProperty().isNotEmpty());
        datePicker.setValue(LocalDate.now().plusDays(1));

        // Half-hour slots between 07:00 and 21:30.
        List<LocalTime> slots = new java.util.ArrayList<>();
        for (int hour = 7; hour <= 21; hour++) {
            slots.add(LocalTime.of(hour, 0));
            slots.add(LocalTime.of(hour, 30));
        }
        startCombo.setItems(FXCollections.observableList(slots));
        endCombo.setItems(FXCollections.observableList(slots));
        startCombo.setValue(LocalTime.of(9, 0));
        endCombo.setValue(LocalTime.of(10, 0));

        resourceCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Resource r) {
                return r == null ? "" : r.getName() + " (" + r.getType().getLabel() + ")";
            }
            @Override public Resource fromString(String s) { return null; }
        });
        resourceCombo.valueProperty().addListener((obs, old, r) -> updateSummary(r));

        Bg.run(resourceService::listBookable, resources -> {
            resourceCombo.setItems(FXCollections.observableList(resources));
            if (preselectedResourceId != null) {
                resources.stream()
                        .filter(r -> r.getId().equals(preselectedResourceId))
                        .findFirst()
                        .ifPresent(resourceCombo::setValue);
            }
        });
    }

    private void updateSummary(Resource resource) {
        if (resource == null) {
            resourceSummary.setText("");
            return;
        }
        StringBuilder text = new StringBuilder(resource.getName());
        if (resource.getLocation() != null && !resource.getLocation().isBlank()) {
            text.append("  •  ").append(resource.getLocation());
        }
        if (resource.getCapacity() != null) {
            text.append("  •  capacity: ").append(resource.getCapacity());
        }
        resourceSummary.setText(text.toString());
    }

    /** Returns an error message, or null when the form is complete. */
    private String validate() {
        if (resourceCombo.getValue() == null) {
            return "Please choose a resource.";
        }
        if (datePicker.getValue() == null) {
            return "Please choose a booking date.";
        }
        LocalTime start = startCombo.getValue();
        LocalTime end = endCombo.getValue();
        if (start == null || end == null) {
            return "Please choose start and end times.";
        }
        if (!end.isAfter(start)) {
            return "End time must be after start time.";
        }
        if (purposeField.getText() == null || purposeField.getText().isBlank()) {
            return "Please describe the purpose of your booking.";
        }
        return null;
    }

    private void submitAsync(java.util.function.Consumer<Booking> onSuccess,
                             java.util.function.Consumer<Throwable> onError) {
        Resource resource = resourceCombo.getValue();
        Bg.run(() -> bookingService.requestBooking(session, resource.getId(),
                        datePicker.getValue(), startCombo.getValue(), endCombo.getValue(),
                        purposeField.getText()),
                onSuccess, onError);
    }
}
