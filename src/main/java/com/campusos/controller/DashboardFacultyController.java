package com.campusos.controller;

import com.campusos.model.AuditLog;
import com.campusos.model.Complaint;
import com.campusos.model.Event;
import com.campusos.service.AnalyticsService;
import com.campusos.service.AuditService;
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
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * Faculty dashboard (Module 5): assigned complaints, own events, booking
 * counters and a personal recent-activity feed.
 */
public class DashboardFacultyController implements ViewController {

    @FXML private Label welcomeLabel;
    @FXML private HBox kpiRow;
    @FXML private TableView<Complaint> assignedTable;
    @FXML private TableColumn<Complaint, Number> assignedId;
    @FXML private TableColumn<Complaint, String> assignedTitle;
    @FXML private TableColumn<Complaint, String> assignedPriority;
    @FXML private TableColumn<Complaint, String> assignedStatus;
    @FXML private TableColumn<Complaint, String> assignedCreated;
    @FXML private TableView<Event> eventsTable;
    @FXML private TableColumn<Event, String> eventTitle;
    @FXML private TableColumn<Event, String> eventStart;
    @FXML private TableColumn<Event, String> eventSeats;
    @FXML private TableView<AuditLog> activityTable;
    @FXML private TableColumn<AuditLog, String> activityTime;
    @FXML private TableColumn<AuditLog, String> activityAction;
    @FXML private TableColumn<AuditLog, String> activityDetail;

    private Session session;
    private Navigator navigator;
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
        assignedId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("id"));
        assignedTitle.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("title"));
        assignedCreated.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getCreatedAt())));
        assignedPriority.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPriority().getLabel()));
        assignedPriority.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(Ui.badge(item, Ui.badgeClass(getTableRow().getItem().getPriority())));
                }
            }
        });
        assignedStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus().getLabel()));
        assignedStatus.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
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
        assignedTable.setPlaceholder(new Label("No complaints are assigned to you right now."));

        eventTitle.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("title"));
        eventStart.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getStartTime())));
        eventSeats.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getRegisteredCount() + "/" + data.getValue().getMaxParticipants()));
        eventsTable.setPlaceholder(new Label("You have not created any events yet."));

        activityTime.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getCreatedAt())));
        activityAction.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("action"));
        activityDetail.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("description"));
        activityTable.setPlaceholder(new Label("No activity recorded yet."));
    }

    @Override
    public void onShow() {
        welcomeLabel.setText("Welcome back, " + session.currentUser().getFullName());

        Bg.run(() -> analyticsService.facultySummary(session), summary -> kpiRow.getChildren().setAll(
                Ui.statCard(String.valueOf(summary.assignedComplaints), "Assigned complaints", "stat-amber"),
                Ui.statCard(String.valueOf(summary.resolvedComplaints), "Resolved complaints", "stat-green"),
                Ui.statCard(String.valueOf(summary.upcomingEvents), "My upcoming events", "stat-indigo"),
                Ui.statCard(String.valueOf(summary.totalBookings), "My bookings", "stat-slate")
        ));

        Bg.run(() -> complaintService.listAssignedToMe(session),
                complaints -> assignedTable.setItems(FXCollections.observableList(complaints)));
        Bg.run(() -> eventService.listCreatedBy(session, session.currentUserId()),
                events -> eventsTable.setItems(FXCollections.observableList(events)));
        Bg.run(() -> auditService.recentByUser(session.currentUserId(), 8),
                logs -> activityTable.setItems(FXCollections.observableList(logs)));
    }
}
