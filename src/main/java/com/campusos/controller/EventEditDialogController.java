package com.campusos.controller;

import com.campusos.model.Department;
import com.campusos.model.Event;
import com.campusos.service.DepartmentService;
import com.campusos.service.EventService;
import com.campusos.session.Session;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Modality;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Create/Edit event form inside a modal dialog (Module 4).
 */
public class EventEditDialogController {

    @FXML private Label formError;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private TextField venueField;
    @FXML private Spinner<Integer> capacitySpinner;
    @FXML private DatePicker startDatePicker;
    @FXML private ComboBox<LocalTime> startTimeCombo;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<LocalTime> endTimeCombo;
    @FXML private DatePicker deadlineDatePicker;
    @FXML private ComboBox<LocalTime> deadlineTimeCombo;
    @FXML private ComboBox<Department> departmentCombo;

    private final EventService eventService = new EventService();
    private final DepartmentService departmentService = new DepartmentService();
    private List<Department> departments = List.of();

    /** Result holder for the caller. */
    public record FormOutcome(Event event, Alert error) { }

    public static void showCreate(Session session, Runnable onChanged) {
        show(session, null, onChanged);
    }

    public static void showEdit(Session session, Event existing, Runnable onChanged) {
        show(session, existing, onChanged);
    }

    private static void show(Session session, Event existing, Runnable onChanged) {
        Dialog<ButtonType> dialog;
        EventEditDialogController controller;
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    EventEditDialogController.class.getResource("/fxml/dialog-event-edit.fxml"));
            dialog = new Dialog<>();
            dialog.setDialogPane(loader.load());
            controller = loader.getController();
        } catch (Exception e) {
            Alerts.error("Could not open the event form", e);
            return;
        }
        dialog.setTitle(existing == null ? "Create event" : "Edit event");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.APPLY);
        saveButton.setText("Save event");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancel");

        controller.init(session, existing);

        ButtonType answer = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (answer == ButtonType.APPLY) {
            EventEditDialogController.FormOutcome outcome =
                    controller.build(existing == null ? null : existing.getId());
            if (outcome.error != null) {
                outcome.error.showAndWait();
                show(session, existing, onChanged); // reopen for correction
                return;
            }
            Bg.run(() -> {
                if (existing == null) {
                    eventServiceCreate(session, outcome.event());
                } else {
                    outcome.event().setId(existing.getId());
                    eventServiceUpdate(session, outcome.event());
                }
                return (Void) null;
            }, r -> onChanged.run());
        }
    }

    private static void eventServiceCreate(Session session, Event event) {
        new EventService().create(session, event);
    }

    private static void eventServiceUpdate(Session session, Event event) {
        new EventService().update(session, event);
    }

    public void init(Session session, Event existing) {
        formError.visibleProperty().bind(formError.textProperty().isNotEmpty());
        formError.managedProperty().bind(formError.textProperty().isNotEmpty());

        List<LocalTime> slots = new java.util.ArrayList<>();
        for (int hour = 6; hour <= 22; hour++) {
            slots.add(LocalTime.of(hour, 0));
            slots.add(LocalTime.of(hour, 30));
        }
        startTimeCombo.setItems(FXCollections.observableList(slots));
        endTimeCombo.setItems(FXCollections.observableList(slots));
        deadlineTimeCombo.setItems(FXCollections.observableList(slots));

        departmentCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Department d) { return d == null ? "— none —" : d.getName(); }
            @Override public Department fromString(String s) { return null; }
        });

        Bg.run(() -> departmentService.listActive(), found -> {
            departments = found;
            List<Department> items = new java.util.ArrayList<>();
            items.add(null);
            items.addAll(found);
            departmentCombo.getItems().setAll(items);
        });

        if (existing == null) {
            startDatePicker.setValue(LocalDate.now().plusDays(7));
            startTimeCombo.setValue(LocalTime.of(10, 0));
            endDatePicker.setValue(LocalDate.now().plusDays(7));
            endTimeCombo.setValue(LocalTime.of(13, 0));
            deadlineDatePicker.setValue(LocalDate.now().plusDays(5));
            deadlineTimeCombo.setValue(LocalTime.of(23, 59));
        } else {
            titleField.setText(existing.getTitle());
            descriptionField.setText(existing.getDescription());
            venueField.setText(existing.getVenue());
            capacitySpinner.getValueFactory().setValue(existing.getMaxParticipants());
            startDatePicker.setValue(existing.getStartTime().toLocalDate());
            startTimeCombo.setValue(existing.getStartTime().toLocalTime());
            endDatePicker.setValue(existing.getEndTime().toLocalDate());
            endTimeCombo.setValue(existing.getEndTime().toLocalTime());
            deadlineDatePicker.setValue(existing.getRegistrationDeadline().toLocalDate());
            deadlineTimeCombo.setValue(existing.getRegistrationDeadline().toLocalTime());
            for (Department d : departments) {
                if (d != null && existing.getDepartmentId() != null
                        && d.getId().equals(existing.getDepartmentId())) {
                    departmentCombo.setValue(d);
                }
            }
        }
    }

    public FormOutcome build(Long editingId) {
        Event event = new Event();
        event.setId(editingId);
        event.setTitle(titleField.getText());
        event.setDescription(descriptionField.getText());
        event.setVenue(venueField.getText());
        event.setMaxParticipants(capacitySpinner.getValue());
        Department department = departmentCombo.getValue();
        event.setDepartmentId(department == null ? null : department.getId());

        if (startDatePicker.getValue() == null || endDatePicker.getValue() == null
                || deadlineDatePicker.getValue() == null
                || startTimeCombo.getValue() == null || endTimeCombo.getValue() == null
                || deadlineTimeCombo.getValue() == null) {
            return new FormOutcome(null, Alerts.errorAlert("Missing dates",
                    "Start, end and registration deadline (date + time) are all required."));
        }
        LocalDateTime start = LocalDateTime.of(startDatePicker.getValue(), startTimeCombo.getValue());
        LocalDateTime end = LocalDateTime.of(endDatePicker.getValue(), endTimeCombo.getValue());
        LocalDateTime deadline = LocalDateTime.of(deadlineDatePicker.getValue(), deadlineTimeCombo.getValue());
        event.setStartTime(start);
        event.setEndTime(end);
        event.setRegistrationDeadline(deadline);

        List<String> problems = new java.util.ArrayList<>();
        if (event.getTitle() == null || event.getTitle().isBlank()) {
            problems.add("Event title is required.");
        }
        if (event.getVenue() == null || event.getVenue().isBlank()) {
            problems.add("Venue is required.");
        }
        if (!problems.isEmpty()) {
            return new FormOutcome(null, Alerts.errorAlert("Please fix the form",
                    String.join("\n", problems)));
        }
        return new FormOutcome(event, null);
    }
}
