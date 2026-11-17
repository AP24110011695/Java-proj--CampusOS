package com.campusos.controller;

import com.campusos.model.Complaint;
import com.campusos.model.ComplaintCategory;
import com.campusos.model.ComplaintPriority;
import com.campusos.model.ComplaintStatus;
import com.campusos.model.Department;
import com.campusos.service.ComplaintService;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * My complaints (Module 3, student + faculty): submit new complaints and
 * track their full lifecycle; acknowledge (close) resolved ones.
 */
public class MyComplaintsController implements ViewController {

    @FXML private ComboBox<ComplaintCategory> categoryCombo;
    @FXML private ComboBox<ComplaintPriority> priorityCombo;
    @FXML private ComboBox<Department> departmentCombo;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private Button submitButton;
    @FXML private Label formError;
    @FXML private TableView<Complaint> complaintTable;
    @FXML private TableColumn<Complaint, Number> colId;
    @FXML private TableColumn<Complaint, String> colTitle;
    @FXML private TableColumn<Complaint, String> colCategory;
    @FXML private TableColumn<Complaint, String> colPriority;
    @FXML private TableColumn<Complaint, String> colStatus;
    @FXML private TableColumn<Complaint, String> colAssignedTo;
    @FXML private TableColumn<Complaint, String> colCreated;
    @FXML private TableColumn<Complaint, String> colResolved;
    @FXML private Button detailButton;
    @FXML private Button closeMyButton;
    @FXML private Label hintLabel;

    private Session session;
    private Navigator navigator;
    private final ComplaintService complaintService = new ComplaintService();
    private final DepartmentService departmentService = new DepartmentService();
    private final ObservableList<Complaint> complaints = FXCollections.observableArrayList();

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        formError.visibleProperty().bind(formError.textProperty().isNotEmpty());
        formError.managedProperty().bind(formError.textProperty().isNotEmpty());

        categoryCombo.getItems().setAll(ComplaintCategory.values());
        categoryCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ComplaintCategory c) { return c == null ? "" : c.getLabel(); }
            @Override public ComplaintCategory fromString(String s) { return null; }
        });
        categoryCombo.setValue(ComplaintCategory.OTHER);
        priorityCombo.getItems().setAll(ComplaintPriority.values());
        priorityCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ComplaintPriority p) { return p == null ? "" : p.getLabel(); }
            @Override public ComplaintPriority fromString(String s) { return null; }
        });
        priorityCombo.setValue(ComplaintPriority.MEDIUM);
        departmentCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Department d) { return d == null ? "— none —" : d.getName(); }
            @Override public Department fromString(String s) { return null; }
        });

        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colAssignedTo.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getAssignedToName() == null ? "—" : data.getValue().getAssignedToName()));
        colCreated.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getCreatedAt())));
        colResolved.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getResolvedAt() == null ? "—"
                        : Fmt.datetime(data.getValue().getResolvedAt())));
        colCategory.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCategory().getLabel()));
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
        complaintTable.setPlaceholder(new Label(
                "No complaints yet - use the form above to submit your first one."));

        complaintTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean closable = sel != null && sel.getStatus() == ComplaintStatus.RESOLVED;
            detailButton.setDisable(sel == null);
            closeMyButton.setDisable(!closable);
        });
        detailButton.setDisable(true);
        closeMyButton.setDisable(true);

        Bg.run(() -> departmentService.listActive(), departments -> {
            List<Department> items = new ArrayList<>();
            items.add(null);
            items.addAll(departments);
            departmentCombo.getItems().setAll(items);
        });
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        Bg.run(() -> complaintService.listMine(session), found -> complaints.setAll(found));
    }

    @FXML
    private void handleSubmit() {
        formError.setText("");

        ComplaintCategory category = categoryCombo.getValue();
        ComplaintPriority priority = priorityCombo.getValue();
        Department department = departmentCombo.getValue();

        if (titleField.getText() == null || titleField.getText().isBlank()) {
            formError.setText("Please enter a short title for the complaint.");
            return;
        }
        if (descriptionField.getText() == null || descriptionField.getText().isBlank()) {
            formError.setText("Please describe the problem.");
            return;
        }

        submitButton.setDisable(true);
        Bg.run(() -> complaintService.submit(session, category, titleField.getText(),
                        descriptionField.getText(), priority,
                        department == null ? null : department.getId()),
                complaint -> {
                    submitButton.setDisable(false);
                    Alerts.info("Complaint submitted",
                            "Complaint #" + complaint.getId() + " was submitted. "
                                    + "You can track its progress from the table below.");
                    titleField.clear();
                    descriptionField.clear();
                    reload();
                },
                error -> {
                    submitButton.setDisable(false);
                    formError.setText(error.getMessage());
                });
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
    private void handleCloseMine() {
        Complaint selected = complaintTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getStatus() != ComplaintStatus.RESOLVED) {
            return;
        }
        ComplaintDetailDialogController.show(session, selected.getId(), this::reload);
    }
}
