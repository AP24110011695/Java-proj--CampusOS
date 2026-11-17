package com.campusos.controller;

import com.campusos.model.Complaint;
import com.campusos.model.ComplaintCategory;
import com.campusos.model.ComplaintPriority;
import com.campusos.model.ComplaintStatus;
import com.campusos.model.Department;
import com.campusos.service.ComplaintService;
import com.campusos.service.CsvExportService;
import com.campusos.service.DepartmentService;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Complaint management (Module 3, admin): rich filtering across every
 * dimension, assignment to faculty and lifecycle closing.
 */
public class ComplaintsAdminController implements ViewController {

    @FXML private ComboBox<ComplaintStatus> statusFilter;
    @FXML private ComboBox<ComplaintCategory> categoryFilter;
    @FXML private ComboBox<ComplaintPriority> priorityFilter;
    @FXML private ComboBox<Department> departmentFilter;
    @FXML private TextField searchField;
    @FXML private TableView<Complaint> complaintTable;
    @FXML private TableColumn<Complaint, Number> colId;
    @FXML private TableColumn<Complaint, String> colTitle;
    @FXML private TableColumn<Complaint, String> colSubmitter;
    @FXML private TableColumn<Complaint, String> colCategory;
    @FXML private TableColumn<Complaint, String> colPriority;
    @FXML private TableColumn<Complaint, String> colStatus;
    @FXML private TableColumn<Complaint, String> colDepartment;
    @FXML private TableColumn<Complaint, String> colAssignedTo;
    @FXML private TableColumn<Complaint, String> colCreated;
    @FXML private Button assignButton;
    @FXML private Button detailButton;
    @FXML private Button closeButton;
    @FXML private Label hintLabel;

    private Session session;
    private Navigator navigator;
    private final ComplaintService complaintService = new ComplaintService();
    private final DepartmentService departmentService = new DepartmentService();
    private final CsvExportService csvExportService = new CsvExportService();
    private final ObservableList<Complaint> complaints = FXCollections.observableArrayList();

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colSubmitter.setCellValueFactory(new PropertyValueFactory<>("userName"));
        colCategory.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCategory().getLabel()));
        colDepartment.setCellValueFactory(new PropertyValueFactory<>("departmentName"));
        colAssignedTo.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getAssignedToName() == null ? "—" : data.getValue().getAssignedToName()));
        colCreated.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getCreatedAt())));
        colPriority.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPriority().getLabel()));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus().getLabel()));

        colPriority.setCellFactory(col -> new TableCell<>() {
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

        complaintTable.setItems(complaints);
        complaintTable.setPlaceholder(new Label("No complaints match the current filters."));

        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(ComplaintStatus.values());
        statusFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ComplaintStatus s) { return s == null ? "All statuses" : s.getLabel(); }
            @Override public ComplaintStatus fromString(String s) { return null; }
        });
        categoryFilter.getItems().add(null);
        categoryFilter.getItems().addAll(ComplaintCategory.values());
        categoryFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ComplaintCategory c) { return c == null ? "All categories" : c.getLabel(); }
            @Override public ComplaintCategory fromString(String s) { return null; }
        });
        priorityFilter.getItems().add(null);
        priorityFilter.getItems().addAll(ComplaintPriority.values());
        priorityFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ComplaintPriority p) { return p == null ? "All priorities" : p.getLabel(); }
            @Override public ComplaintPriority fromString(String s) { return null; }
        });
        departmentFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Department d) { return d == null ? "All departments" : d.getName(); }
            @Override public Department fromString(String s) { return null; }
        });

        complaintTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean has = sel != null;
            detailButton.setDisable(!has);
            closeButton.setDisable(!has || sel.getStatus() != ComplaintStatus.RESOLVED);
            assignButton.setDisable(!has || sel.getStatus() != ComplaintStatus.SUBMITTED);
            hintLabel.setText(has
                    ? "Lifecycle: " + sel.getStatus().getLabel()
                            + (sel.getStatus() == ComplaintStatus.SUBMITTED
                               ? " - assign it to a faculty member next."
                               : sel.getStatus() == ComplaintStatus.RESOLVED
                                 ? " - ready to close."
                                 : ".")
                    : "Select a complaint to act on it");
        });

        detailButton.setDisable(true);
        assignButton.setDisable(true);
        closeButton.setDisable(true);
        loadDepartments();
    }

    private void loadDepartments() {
        Bg.run(() -> departmentService.listActive(), departments -> {
            List<Department> items = new ArrayList<>();
            items.add(null);
            items.addAll(departments);
            departmentFilter.getItems().setAll(items);
        });
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        Department department = departmentFilter.getValue();
        Bg.run(() -> complaintService.listForAdmin(session, statusFilter.getValue(),
                        categoryFilter.getValue(), priorityFilter.getValue(),
                        department == null ? null : department.getId(), searchField.getText()),
                found -> complaints.setAll(found));
    }

    @FXML
    private void handleApplyFilters() {
        reload();
    }

    @FXML
    private void handleAssign() {
        Complaint selected = complaintTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        ComplaintDetailDialogController.show(session, selected.getId(), this::reload);
    }

    @FXML
    private void handleOpenDetail() {
        Complaint selected = complaintTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        ComplaintDetailDialogController.show(session, selected.getId(), this::reload);
    }

    @FXML
    private void handleClose() {
        Complaint selected = complaintTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getStatus() != ComplaintStatus.RESOLVED) {
            return;
        }
        ComplaintDetailDialogController.show(session, selected.getId(), this::reload);
    }

    @FXML
    private void handleExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export complaints to CSV");
        chooser.setInitialFileName("campusos-complaints.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File target = chooser.showSaveDialog(complaintTable.getScene().getWindow());
        if (target == null) {
            return;
        }
        Bg.run(() -> csvExportService.exportComplaints(session), csv -> {
            try {
                Files.writeString(target.toPath(), csv, StandardCharsets.UTF_8);
                Alerts.info("Export complete", "Complaints exported to:\n" + target.getAbsolutePath());
            } catch (Exception ioError) {
                Alerts.error("Could not write the file", ioError);
            }
        });
    }
}
