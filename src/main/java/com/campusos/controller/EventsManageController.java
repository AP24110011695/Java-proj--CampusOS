package com.campusos.controller;

import com.campusos.model.Event;
import com.campusos.model.EventStatus;
import com.campusos.model.Role;
import com.campusos.service.EventService;
import com.campusos.session.Session;
import com.campusos.ui.Navigator;
import com.campusos.ui.Ui;
import com.campusos.ui.ViewController;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import com.campusos.util.Fmt;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Event management (Module 4, admin + faculty): create, edit and cancel
 * events; open the registrations dialog to track attendance. Faculty see
 * and manage only the events they created (enforced by the service layer).
 */
public class EventsManageController implements ViewController {

    @FXML private Label subtitle;
    @FXML private TextField searchField;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button participantsButton;
    @FXML private Button cancelEventButton;
    @FXML private TableView<Event> eventTable;
    @FXML private TableColumn<Event, Number> colId;
    @FXML private TableColumn<Event, String> colTitle;
    @FXML private TableColumn<Event, String> colVenue;
    @FXML private TableColumn<Event, String> colStart;
    @FXML private TableColumn<Event, String> colDeadline;
    @FXML private TableColumn<Event, String> colCapacity;
    @FXML private TableColumn<Event, String> colOrganizer;
    @FXML private TableColumn<Event, String> colStatus;
    @FXML private Label hintLabel;

    private Session session;
    private Navigator navigator;
    private final EventService eventService = new EventService();
    private final ObservableList<Event> events = FXCollections.observableArrayList();
    private boolean isAdmin;

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
        this.isAdmin = session.currentUser().getRole() == Role.ADMIN;
    }

    @FXML
    private void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colVenue.setCellValueFactory(new PropertyValueFactory<>("venue"));
        colOrganizer.setCellValueFactory(new PropertyValueFactory<>("createdByName"));
        colStart.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getStartTime())));
        colDeadline.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getRegistrationDeadline())));
        colCapacity.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getRegisteredCount() + " / " + data.getValue().getMaxParticipants()));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus().getLabel()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(Ui.badge(item, Ui.badgeClass(getTableRow().getItem().getStatus())));
                }
            }
        });
        eventTable.setItems(events);
        eventTable.setPlaceholder(new Label("No events yet - create the first campus event."));

        eventTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean has = sel != null;
            editButton.setDisable(!has || sel.getStatus() == EventStatus.CANCELLED);
            participantsButton.setDisable(!has);
            cancelEventButton.setDisable(!has || sel.getStatus() != EventStatus.UPCOMING);
            if (has) {
                hintLabel.setText(sel.getTitle() + ": " + sel.getRegisteredCount()
                        + " registered, " + sel.getAttendedCount() + " attended, "
                        + sel.seatsAvailable() + " seats left.");
            } else {
                hintLabel.setText("");
            }
        });

        editButton.setDisable(true);
        participantsButton.setDisable(true);
        cancelEventButton.setDisable(true);
        subtitle.setText(isAdmin
                ? "All campus events - create, edit, cancel and track attendance"
                : "Events you organize - create, edit, cancel and track attendance");
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        Bg.run(() -> eventService.listManaged(session, searchField.getText()),
                found -> events.setAll(found));
    }

    @FXML
    private void handleApplyFilters() {
        reload();
    }

    @FXML
    private void handleAdd() {
        EventEditDialogController.showCreate(session, this::reload);
    }

    @FXML
    private void handleEdit() {
        Event selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            EventEditDialogController.showEdit(session, selected, this::reload);
        }
    }

    @FXML
    private void handleParticipants() {
        Event selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            EventParticipantsController.show(session, selected, this::reload);
        }
    }

    @FXML
    private void handleCancelEvent() {
        Event selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!Alerts.confirm("Cancel event",
                "Cancel \"" + selected.getTitle() + "\"?\nRegistered participants keep their record "
                        + "but the event can no longer be attended.")) {
            return;
        }
        Bg.run(() -> {
            eventService.cancel(session, selected.getId());
            return (Void) null;
        }, result -> reload());
    }
}
