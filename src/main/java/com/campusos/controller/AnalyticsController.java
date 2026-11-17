package com.campusos.controller;

import com.campusos.model.CountStat;
import com.campusos.model.DashboardStats;
import com.campusos.service.AnalyticsService;
import com.campusos.service.CsvExportService;
import com.campusos.session.Session;
import com.campusos.ui.Navigator;
import com.campusos.ui.Ui;
import com.campusos.ui.ViewController;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

/**
 * Analytics &amp; reports (Module 5, admin): five database-driven charts
 * plus CSV export for every major dataset.
 */
public class AnalyticsController implements ViewController {

    @FXML private FlowPane summaryRow;
    @FXML private PieChart bookingStatusPie;
    @FXML private PieChart complaintCategoryPie;
    @FXML private LineChart<String, Number> trendChart;
    @FXML private BarChart<String, Number> deptBar;
    @FXML private BarChart<String, Number> attendanceBar;
    @FXML private TableView<CountStat> topEventsTable;
    @FXML private TableColumn<CountStat, String> topEventCol;
    @FXML private TableColumn<CountStat, Number> topEventCount;

    private Session session;
    private Navigator navigator;
    private final AnalyticsService analyticsService = new AnalyticsService();
    private final CsvExportService csvExportService = new CsvExportService();

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        topEventCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLabel()));
        topEventCount.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getValue()));
    }

    @Override
    public void onShow() {
        Bg.run(() -> analyticsService.adminStats(session), this::renderSummary);
        Bg.run(() -> analyticsService.bookingsByStatus(session),
                stats -> fillPie(bookingStatusPie, stats));
        Bg.run(() -> analyticsService.complaintsByCategory(session),
                stats -> fillPie(complaintCategoryPie, stats));
        Bg.run(() -> analyticsService.monthlyBookingTrend(session, 6),
                this::fillTrend);
        Bg.run(() -> analyticsService.usersByDepartment(session),
                this::fillDeptBar);
        Bg.run(() -> analyticsService.eventAttendanceOverview(session),
                this::fillAttendanceBar);
        Bg.run(() -> analyticsService.registrationsByEvent(session, 8),
                stats -> topEventsTable.setItems(FXCollections.observableList(stats)));
    }

    private void renderSummary(DashboardStats s) {
        summaryRow.getChildren().setAll(
                Ui.statCard(String.valueOf(s.getTotalBookings()), "Total bookings", "stat-indigo"),
                Ui.statCard(String.valueOf(s.getPendingBookings()), "Pending bookings", "stat-amber"),
                Ui.statCard(String.valueOf(s.getTotalComplaints()), "Total complaints", "stat-slate"),
                Ui.statCard(String.valueOf(s.getResolvedComplaints()), "Resolved complaints", "stat-green"),
                Ui.statCard(String.valueOf(s.getTotalEvents()), "Campus events", "stat-slate"),
                Ui.statCard(String.valueOf(s.getTotalRegistrations()), "Event registrations", "stat-indigo"),
                Ui.statCard(String.valueOf(s.getTotalUsers()), "Registered users", "stat-slate")
        );
    }

    private void fillPie(PieChart pie, List<CountStat> stats) {
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        for (CountStat stat : stats) {
            data.add(new PieChart.Data(stat.getLabel() + " (" + stat.getValue() + ")", stat.getValue()));
        }
        pie.setData(data);
    }

    private void fillTrend(List<CountStat> monthly) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Bookings");
        for (CountStat point : monthly) {
            series.getData().add(new XYChart.Data<>(point.getLabel(), point.getValue()));
        }
        trendChart.getData().setAll(series);
    }

    private void fillDeptBar(List<CountStat> stats) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Users");
        for (CountStat point : stats) {
            series.getData().add(new XYChart.Data<>(point.getLabel(), point.getValue()));
        }
        deptBar.getData().setAll(series);
    }

    private void fillAttendanceBar(List<CountStat> stats) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Attended");
        for (CountStat point : stats) {
            series.getData().add(new XYChart.Data<>(point.getLabel(), point.getValue()));
        }
        attendanceBar.getData().setAll(series);
    }

    @FXML
    private void handleRefresh() {
        onShow();
    }

    private void export(String dialogTitle, String fileName, java.util.function.Supplier<String> content) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(dialogTitle);
        chooser.setInitialFileName(fileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File target = chooser.showSaveDialog(summaryRow.getScene().getWindow());
        if (target == null) {
            return;
        }
        Bg.run(content::get, csv -> {
            try {
                Files.writeString(target.toPath(), csv, StandardCharsets.UTF_8);
                Alerts.info("Export complete", "Saved to:\n" + target.getAbsolutePath());
            } catch (Exception ioError) {
                Alerts.error("Could not write the file", ioError);
            }
        });
    }

    @FXML
    private void handleExportStudents() {
        export("Export student records", "campusos-students.csv",
                () -> csvExportService.exportStudents(session));
    }

    @FXML
    private void handleExportFaculty() {
        export("Export faculty records", "campusos-faculty.csv",
                () -> csvExportService.exportFaculty(session));
    }

    @FXML
    private void handleExportBookings() {
        export("Export booking records", "campusos-bookings.csv",
                () -> csvExportService.exportBookings(session));
    }

    @FXML
    private void handleExportComplaints() {
        export("Export complaint records", "campusos-complaints.csv",
                () -> csvExportService.exportComplaints(session));
    }

    @FXML
    private void handleExportRegistrations() {
        export("Export event registrations", "campusos-registrations.csv",
                () -> csvExportService.exportEventRegistrations(session));
    }
}
