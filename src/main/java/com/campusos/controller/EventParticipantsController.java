package com.campusos.controller;

import com.campusos.model.Event;
import com.campusos.model.EventRegistration;
import com.campusos.model.RegistrationStatus;
import com.campusos.service.EventService;
import com.campusos.session.Session;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import com.campusos.util.Fmt;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;

/**
 * Registrations & attendance dialog (Module 4): organizer sees every
 * participant and marks attendance per person.
 */
public class EventParticipantsController {

    private Session session;
    private Event event;
    private final EventService eventService = new EventService();
    private Runnable onChanged = () -> { };

    public static void show(Session session, Event event, Runnable onChanged) {
        Dialog<Void> dialog;
        EventParticipantsController controller;
        try {
            FXMLLoader loader = new FXMLLoader(
                    EventParticipantsController.class.getResource("/fxml/dialog-event-participants.fxml"));
            dialog = new Dialog<>();
            dialog.setDialogPane(loader.load());
            controller = loader.getController();
        } catch (Exception e) {
            Alerts.error("Could not open registrations", e);
            return;
        }
        dialog.setTitle("Registrations - " + event.getTitle());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        controller.setup(session, event, onChanged);
        dialog.showAndWait();
    }

    private void setup(Session session, Event event, Runnable onChanged) {
        this.session = session;
        this.event = event;
        this.onChanged = onChanged;
        reload();
    }

    private void reload() {
        renderHeader();
        Bg.run(() -> eventService.participants(session, event.getId()),
                participants -> participantsTable().setItems(FXCollections.observableList(participants)));
    }

    /* FXML injected fields wired through the loaded pane. */
    @FXML private Label eventTitleLabel;
    @FXML private Label capacityLabel;
    @FXML private Label hintLabel;
    @FXML private TableView<EventRegistration> participantTable;
    @FXML private TableColumn<EventRegistration, Number> colId;
    @FXML private TableColumn<EventRegistration, String> colName;
    @FXML private TableColumn<EventRegistration, String> colEmail;
    @FXML private TableColumn<EventRegistration, String> colStatus;
    @FXML private TableColumn<EventRegistration, String> colRegistered;
    @FXML private Button markAttendedButton;
    @FXML private Button clearAttendanceButton;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("userName"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("userEmail"));
        colRegistered.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getRegisteredAt())));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus().getLabel()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(com.campusos.ui.Ui.badge(item,
                            com.campusos.ui.Ui.badgeClass(getTableRow().getItem().getStatus())));
                }
            }
        });
        participantTable.setPlaceholder(new Label("No registrations for this event yet."));

        participantTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean canMark = sel != null && sel.getStatus() == RegistrationStatus.REGISTERED;
            boolean canClear = sel != null && sel.getStatus() == RegistrationStatus.ATTENDED;
            markAttendedButton.setDisable(!canMark);
            clearAttendanceButton.setDisable(!canClear);
        });
        markAttendedButton.setDisable(true);
        clearAttendanceButton.setDisable(true);
    }

    private TableView<EventRegistration> participantsTable() {
        return participantTable;
    }

    private void renderHeader() {
        eventTitleLabel.setText("#" + event.getId() + "  " + event.getTitle());
        capacityLabel.setText("Venue: " + event.getVenue()
                + "   •   Registered: " + event.getRegisteredCount() + "/" + event.getMaxParticipants()
                + "   •   Attended: " + event.getAttendedCount());
    }

    @FXML
    private void handleMarkAttended() {
        EventRegistration selected = participantTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Bg.run(() -> {
            eventService.markAttendance(session, selected.getId(), true);
            return (Void) null;
        }, result -> refresh());
    }

    @FXML
    private void handleClearAttendance() {
        EventRegistration selected = participantTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Bg.run(() -> {
            eventService.markAttendance(session, selected.getId(), false);
            return (Void) null;
        }, result -> refresh());
    }

    private void refresh() {
        onChanged.run();
        reload();
    }
}
