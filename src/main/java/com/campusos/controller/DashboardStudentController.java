package com.campusos.controller;

import com.campusos.model.AuditLog;
import com.campusos.model.Booking;
import com.campusos.model.Complaint;
import com.campusos.model.Event;
import com.campusos.model.EventRegistration;
import com.campusos.service.AnalyticsService;
import com.campusos.service.AuditService;
import com.campusos.service.BookingService;
import com.campusos.service.ComplaintService;
import com.campusos.service.EventService;
import com.campusos.session.Session;
import com.campusos.ui.Navigator;
import com.campusos.ui.Ui;
import com.campusos.ui.ViewController;
import com.campusos.util.Bg;
import com.campusos.util.Fmt;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

/**
 * Student dashboard (Module 5): bookings, complaints, upcoming registered
 * events and a personal activity feed - all real database data.
 */
public class DashboardStudentController implements ViewController {

    @FXML private Label welcomeLabel;
    @FXML private HBox kpiRow;
    @FXML private TableView<EventRegistration> eventsTable;
    @FXML private TableColumn<EventRegistration, String> eventTitle;
    @FXML private TableColumn<EventRegistration, String> eventVenue;
    @FXML private TableColumn<EventRegistration, String> eventStart;
    @FXML private TableView<Booking> bookingsTable;
    @FXML private TableColumn<Booking, String> bookingResource;
    @FXML private TableColumn<Booking, String> bookingDate;
    @FXML private TableColumn<Booking, String> bookingStatus;
    @FXML private TableView<Complaint> complaintsTable;
    @FXML private TableColumn<Complaint, String> complaintTitle;
    @FXML private TableColumn<Complaint, String> complaintStatus;
    @FXML private TableColumn<Complaint, String> complaintUpdated;
    @FXML private TableView<AuditLog> activityTable;
    @FXML private TableColumn<AuditLog, String> activityTime;
    @FXML private TableColumn<AuditLog, String> activityAction;

    private Session session;
    private Navigator navigator;
    private final BookingService bookingService = new BookingService();
    private final ComplaintService complaintService = new ComplaintService();
    private final EventService eventService = new EventService();
    private final AnalyticsService analyticsService = new AnalyticsService();
    private final AuditService auditService = new AuditService();

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        eventTitle.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("eventTitle"));
        eventVenue.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("venue"));
        eventStart.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getEventStartTime())));
        eventsTable.setPlaceholder(new Label("No upcoming registered events."));

        bookingResource.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("resourceName"));
        bookingDate.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.date(data.getValue().getBookingDate())));
        bookingStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus().getLabel()));
        bookingStatus.setCellFactory(col -> new TableCell<>() {
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
        bookingsTable.setPlaceholder(new Label("No bookings yet - reserve a resource from Campus Resources."));

        complaintTitle.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("title"));
        complaintStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus().getLabel()));
        complaintStatus.setCellFactory(col -> new TableCell<>() {
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
        complaintUpdated.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getUpdatedAt())));
        complaintsTable.setPlaceholder(new Label("You have not submitted any complaints."));

        activityTime.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getCreatedAt())));
        activityAction.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("action"));
        activityTable.setPlaceholder(new Label("No activity yet."));
    }

    @Override
    public void onShow() {
        welcomeLabel.setText("Welcome back, " + session.currentUser().getFullName());

        Bg.run(() -> analyticsService.studentSummary(session), summary -> kpiRow.getChildren().setAll(
                Ui.statCard(String.valueOf(summary.pendingBookings), "Pending booking requests", "stat-amber"),
                Ui.statCard(String.valueOf(summary.approvedBookings), "Approved bookings", "stat-green"),
                Ui.statCard(String.valueOf(summary.openComplaints), "Open complaints", "stat-red"),
                Ui.statCard(String.valueOf(summary.resolvedComplaints), "Resolved complaints", "stat-green"),
                Ui.statCard(String.valueOf(summary.upcomingRegistrations), "Upcoming events", "stat-indigo")
        ));

        Bg.run(() -> bookingService.listMine(session, null),
                bookings -> bookingsTable.setItems(FXCollections.observableList(
                        bookings.subList(0, Math.min(6, bookings.size())))));
        Bg.run(() -> complaintService.listMine(session),
                complaints -> complaintsTable.setItems(FXCollections.observableList(
                        complaints.subList(0, Math.min(6, complaints.size())))));
        Bg.run(() -> eventService.myRegistrations(session),
                registrations -> eventsTable.setItems(FXCollections.observableList(
                        registrations.stream()
                                .filter(r -> r.occupiesSeat())
                                .limit(6)
                                .toList())));
        Bg.run(() -> auditService.recentByUser(session.currentUserId(), 6),
                logs -> activityTable.setItems(FXCollections.observableList(logs)));
    }
}
