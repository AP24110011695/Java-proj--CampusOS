package com.campusos.controller;

import com.campusos.model.Event;
import com.campusos.model.EventRegistration;
import com.campusos.service.EventRules;
import com.campusos.model.RegistrationStatus;
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

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Campus events browsing + registration (Module 4). Students register for
 * upcoming events and manage their own registrations; the service layer
 * enforces capacity, deadline and duplicate rules inside a transaction.
 */
public class EventsBrowseController implements ViewController {

    @FXML private TextField searchField;
    @FXML private TableView<Event> eventTable;
    @FXML private TableColumn<Event, Number> colId;
    @FXML private TableColumn<Event, String> colTitle;
    @FXML private TableColumn<Event, String> colVenue;
    @FXML private TableColumn<Event, String> colStart;
    @FXML private TableColumn<Event, String> colDeadline;
    @FXML private TableColumn<Event, String> colSeats;
    @FXML private TableColumn<Event, String> colOrganizer;
    @FXML private Button registerButton;
    @FXML private Label eventHint;
    @FXML private TableView<EventRegistration> registrationTable;
    @FXML private TableColumn<EventRegistration, Number> colRegId;
    @FXML private TableColumn<EventRegistration, String> colEventTitle;
    @FXML private TableColumn<EventRegistration, String> colVenue2;
    @FXML private TableColumn<EventRegistration, String> colEventStart;
    @FXML private TableColumn<EventRegistration, String> colRegStatus;
    @FXML private TableColumn<EventRegistration, String> colRegisteredAt;
    @FXML private Button cancelRegButton;
    @FXML private Label regHint;

    private Session session;
    private Navigator navigator;
    private final EventService eventService = new EventService();
    private final ObservableList<Event> events = FXCollections.observableArrayList();
    private final ObservableList<EventRegistration> registrations = FXCollections.observableArrayList();

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
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
        colSeats.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getRegisteredCount() + " / " + data.getValue().getMaxParticipants()
                        + (data.getValue().seatsAvailable() > 0 ? "" : "  FULL")));
        eventTable.setItems(events);
        eventTable.setPlaceholder(new Label("No upcoming events right now - check back soon."));

        colRegId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colEventTitle.setCellValueFactory(new PropertyValueFactory<>("eventTitle"));
        colVenue2.setCellValueFactory(new PropertyValueFactory<>("venue"));
        colEventStart.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getEventStartTime())));
        colRegisteredAt.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getRegisteredAt())));
        colRegStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus().getLabel()));
        colRegStatus.setCellFactory(col -> new TableCell<>() {
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
        registrationTable.setItems(registrations);
        registrationTable.setPlaceholder(new Label("You have not registered for any events yet."));

        eventTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean student = session.currentUser().getRole() == Role.STUDENT;
            registerButton.setVisible(student);
            registerButton.setManaged(student);
            registerButton.setDisable(true);
            if (sel == null) {
                eventHint.setText("");
                return;
            }
            if (!student) {
                eventHint.setText(sel.getTitle() + ": " + sel.getRegisteredCount() + "/"
                        + sel.getMaxParticipants() + " registered, "
                        + sel.getAttendedCount() + " attended.");
                return;
            }
            eventHint.setText(sel.getVenue() + "  •  " + sel.getRegisteredCount() + "/"
                    + sel.getMaxParticipants() + " seats taken  •  "
                    + (sel.seatsAvailable() > 0 ? sel.seatsAvailable() + " left" : "FULL"));
            // Disable registration unless registration is open AND no active registration exists.
            Bg.run(() -> eventService.myRegistrationFor(session, sel.getId()),
                    existing -> registerButton.setDisable(
                            !registrationOpen(sel) || (existing != null && existing.occupiesSeat())));
        });

        registrationTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean cancellable = sel != null && sel.getStatus() == RegistrationStatus.REGISTERED
                    && sel.getEventStartTime() != null
                    && LocalDateTime.now().isBefore(sel.getEventStartTime());
            cancelRegButton.setDisable(!cancellable);
        });
        cancelRegButton.setDisable(true);

        boolean student = session.currentUser().getRole() == Role.STUDENT;
        registerButton.setVisible(student);
        registerButton.setManaged(student);
    }

    private boolean registrationOpen(Event event) {
        return EventRules.checkRegistration(event, null, LocalDateTime.now()) == null;
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        Bg.run(() -> eventService.listUpcoming(), found -> events.setAll(found));
        Bg.run(() -> eventService.myRegistrations(session), found -> registrations.setAll(found));
    }

    @FXML
    private void handleApplyFilters() {
        reload();
    }

    @FXML
    private void handleRefresh() {
        reload();
    }

    @FXML
    private void handleRegister() {
        Event selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Bg.run(() -> {
            eventService.register(session, selected.getId());
            return (Void) null;
        }, result -> {
            Alerts.info("Registration confirmed",
                    "You are registered for \"" + selected.getTitle() + "\".\n"
                            + "Seats remaining: " + selected.seatsAvailable());
            reload();
        });
    }

    @FXML
    private void handleCancelRegistration() {
        EventRegistration selected = registrationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!Alerts.confirm("Cancel registration",
                "Cancel your registration for \"" + selected.getEventTitle() + "\"?\n"
                        + "Your seat is released for other students. Note: re-registration for the same "
                        + "event is not possible after cancelling.")) {
            return;
        }
        Bg.run(() -> {
            eventService.cancelRegistration(session, selected.getId());
            return (Void) null;
        }, result -> reload());
    }
}
