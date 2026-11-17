package com.campusos.controller;

import com.campusos.model.Department;
import com.campusos.service.DepartmentService;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Department management (Module 1, admin): table + inline add/edit form,
 * activate/deactivate and reference-safe delete.
 */
public class DepartmentsController implements ViewController {

    @FXML private TableView<Department> departmentTable;
    @FXML private TableColumn<Department, Number> colId;
    @FXML private TableColumn<Department, String> colName;
    @FXML private TableColumn<Department, String> colCode;
    @FXML private TableColumn<Department, Number> colMembers;
    @FXML private TableColumn<Department, String> colStatus;
    @FXML private TableColumn<Department, String> colDescription;
    @FXML private Button newButton;
    @FXML private Button editButton;
    @FXML private Button toggleButton;
    @FXML private Button deleteButton;
    @FXML private Label formTitle;
    @FXML private Label formError;
    @FXML private TextField nameField;
    @FXML private TextField codeField;
    @FXML private TextArea descriptionField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private Session session;
    private Navigator navigator;
    private final DepartmentService departmentService = new DepartmentService();
    private final ObservableList<Department> departments = FXCollections.observableArrayList();
    private Department editing;

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colMembers.setCellValueFactory(new PropertyValueFactory<>("memberCount"));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isActive() ? "Active" : "Inactive"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(Ui.badge(item, "Active".equals(item) ? "badge-active" : "badge-inactive"));
                }
            }
        });
        departmentTable.setItems(departments);
        departmentTable.setPlaceholder(new Label("No departments yet."));

        departmentTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean hasSelection = sel != null;
            editButton.setDisable(!hasSelection);
            toggleButton.setDisable(!hasSelection);
            deleteButton.setDisable(!hasSelection);
        });
        editButton.setDisable(true);
        toggleButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        Bg.run(() -> departmentService.listAll(session), found -> departments.setAll(found));
    }

    @FXML
    private void handleNew() {
        editing = null;
        formTitle.setText("New department");
        clearFields();
    }

    @FXML
    private void handleEdit() {
        Department selected = departmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        editing = selected;
        formTitle.setText("Edit department: " + selected.getName());
        nameField.setText(selected.getName());
        codeField.setText(selected.getCode());
        descriptionField.setText(selected.getDescription());
        formError.setText("");
    }

    @FXML
    private void handleSave() {
        Department department = editing == null ? new Department() : editing;
        department.setName(nameField.getText());
        department.setCode(codeField.getText());
        department.setDescription(descriptionField.getText());
        department.setActive(true);

        Bg.run(() -> {
            if (editing == null) {
                departmentService.create(session, department);
            } else {
                departmentService.update(session, department);
            }
            return (Void) null;
        }, result -> {
            Alerts.info("Saved",
                    (editing == null ? "Department created: " : "Department updated: ")
                            + department.getName());
            clearFields();
            editing = null;
            formTitle.setText("New department");
            reload();
        });
    }

    @FXML
    private void handleToggleActive() {
        Department selected = departmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        boolean targetActive = !selected.isActive();
        Bg.run(() -> {
            selected.setActive(targetActive);
            departmentService.update(session, selected);
            return (Void) null;
        }, result -> reload());
    }

    @FXML
    private void handleDelete() {
        Department selected = departmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!Alerts.confirm("Delete department",
                "Delete \"" + selected.getName() + "\" permanently?\n"
                        + "This is only possible when no users are attached.")) {
            return;
        }
        Bg.run(() -> {
            departmentService.remove(session, selected.getId());
            return (Void) null;
        }, result -> reload());
    }

    @FXML
    private void handleClear() {
        editing = null;
        formTitle.setText("New department");
        clearFields();
    }

    private void clearFields() {
        nameField.clear();
        codeField.clear();
        descriptionField.clear();
        formError.setText("");
    }
}
