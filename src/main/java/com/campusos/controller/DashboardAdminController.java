package com.campusos.controller;

import com.campusos.model.CountStat;
import com.campusos.model.DashboardStats;
import com.campusos.service.AnalyticsService;
import com.campusos.session.Session;
import com.campusos.ui.Navigator;
import com.campusos.ui.Ui;
import com.campusos.ui.View;
import com.campusos.ui.ViewController;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;

import java.util.List;

/**
 * Administrator dashboard (Module 5): live statistics cards, status pie
 * charts, monthly booking trend and a "pending action" panel.
 */
public class DashboardAdminController implements ViewController {

    @FXML private FlowPane statsRow;
    @FXML private PieChart bookingPie;
    @FXML private PieChart complaintPie;
    @FXML private LineChart<String, Number> trendChart;
    @FXML private TableView<CountStat> pendingTable;
    @FXML private TableColumn<CountStat, String> pendingCol;
    @FXML private TableColumn<CountStat, Number> pendingCountCol;

    private Session session;
    private Navigator navigator;
    private final AnalyticsService analyticsService = new AnalyticsService();

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        pendingCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getLabel()));
        pendingCountCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getValue()));
    }

    @Override
    public void onShow() {
        Bg.run(() -> analyticsService.adminStats(session), stats -> {
            renderStats(stats);
            pendingTable.setItems(FXCollections.observableArrayList(
                    new CountStat("Bookings awaiting approval", stats.getPendingBookings()),
                    new CountStat("Complaints awaiting resolution", stats.getPendingComplaints()),
                    new CountStat("Upcoming events to monitor", stats.getUpcomingEvents()),
                    new CountStat("Audit trail entries", stats.getAuditEntries())
            ));
        });
        Bg.run(() -> analyticsService.bookingsByStatus(session),
                stats -> fillPie(bookingPie, stats));
        Bg.run(() -> analyticsService.complaintsByStatus(session),
                stats -> fillPie(complaintPie, stats));
        Bg.run(() -> analyticsService.monthlyBookingTrend(session, 6), this::renderTrend);
    }

    private void fillPie(PieChart pie, List<CountStat> stats) {
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        for (CountStat stat : stats) {
            data.add(new PieChart.Data(stat.getLabel(), stat.getValue()));
        }
        pie.setData(data);
    }

    private void renderStats(DashboardStats s) {
        statsRow.getChildren().setAll(
                Ui.statCard(String.valueOf(s.getTotalStudents()), "Students", "stat-indigo"),
                Ui.statCard(String.valueOf(s.getTotalFaculty()), "Faculty", "stat-slate"),
                Ui.statCard(String.valueOf(s.getTotalUsers()), "Total users", "stat-indigo"),
                Ui.statCard(String.valueOf(s.getTotalDepartments()), "Departments", "stat-slate"),
                Ui.statCard(String.valueOf(s.getTotalResources()), "Campus resources", "stat-slate"),
                Ui.statCard(String.valueOf(s.getTotalBookings()), "Bookings", "stat-slate"),
                Ui.statCard(String.valueOf(s.getPendingBookings()), "Pending bookings", "stat-amber"),
                Ui.statCard(String.valueOf(s.getApprovedBookings()), "Approved bookings", "stat-green"),
                Ui.statCard(String.valueOf(s.getTotalComplaints()), "Complaints", "stat-slate"),
                Ui.statCard(String.valueOf(s.getPendingComplaints()), "Open complaints", "stat-red"),
                Ui.statCard(String.valueOf(s.getResolvedComplaints()), "Resolved complaints", "stat-green"),
                Ui.statCard(String.valueOf(s.getTotalEvents()), "Events", "stat-slate"),
                Ui.statCard(String.valueOf(s.getTotalRegistrations()), "Event registrations", "stat-indigo")
        );
    }

    private void renderTrend(List<CountStat> monthly) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Bookings");
        for (CountStat point : monthly) {
            series.getData().add(new XYChart.Data<>(point.getLabel(), point.getValue()));
        }
        trendChart.getData().setAll(series);
    }
}
