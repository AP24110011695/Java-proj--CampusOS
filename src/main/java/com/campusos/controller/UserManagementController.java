package com.campusos.controller;

import com.campusos.model.Department;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.service.CsvExportService;
import com.campusos.service.DepartmentService;
import com.campusos.service.UserService;
import com.campusos.session.Session;
import com.campusos.ui.Navigator;
import com.campusos.ui.Ui;
import com.campusos.ui.ViewController;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import com.campusos.util.Stylesheet;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * User management (Module 1, admin): filter by role/department, search,
 * add/edit/deactivate accounts, reset passwords, CSV export.
 */
public class UserManagementController implements ViewController {

    @FXML private TextField searchField;
    @FXML private ComboBox<Role> roleFilter;
    @FXML private ComboBox<Department> departmentFilter;
    @FXML private Button addButton;
    @FXML private Button exportButton;
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, Number> colId;
    @FXML private TableColumn<User, String> colName;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, Role> colRole;
    @FXML private TableColumn<User, String> colIdentifier;
    @FXML private TableColumn<User, String> colDepartment;
    @FXML private TableColumn<User, String> colPhone;
    @FXML private TableColumn<User, String> colStatus;
    @FXML private Button editButton;
    @FXML private Button toggleActiveButton;
    @FXML private Button resetPasswordButton;
    @FXML private Label hintLabel;

    private Session session;
    private Navigator navigator;
    private final UserService userService = new UserService();
    private final DepartmentService departmentService = new DepartmentService();
    private final CsvExportService csvExportService = new CsvExportService();
    private final ObservableList<User> users = FXCollections.observableArrayList();

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colIdentifier.setCellValueFactory(new PropertyValueFactory<>("identifier"));
        colDepartment.setCellValueFactory(new PropertyValueFactory<>("departmentName"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colRole.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getRole()));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isActive() ? "Active" : "Inactive"));

        colRole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Role role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setGraphic(null);
                } else {
                    setGraphic(Ui.badge(role.getLabel(), Ui.badgeClass(role)));
                }
            }
        });
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    boolean active = item.equals("Active");
                    setGraphic(Ui.badge(item, active ? "badge-active" : "badge-inactive"));
                }
            }
        });
        userTable.setItems(users);
        userTable.setPlaceholder(new Label("No users match the current filters."));

        roleFilter.getItems().addAll(null, Role.STUDENT, Role.FACULTY, Role.ADMIN);
        roleFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Role role) { return role == null ? "All roles" : role.getLabel(); }
            @Override public Role fromString(String s) { return null; }
        });
        departmentFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Department d) { return d == null ? "All departments" : d.getName(); }
            @Override public Department fromString(String s) { return null; }
        });

        userTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean hasSelection = sel != null;
            editButton.setDisable(!hasSelection);
            toggleActiveButton.setDisable(!hasSelection);
            resetPasswordButton.setDisable(!hasSelection);
            if (hasSelection) {
                toggleActiveButton.setText(sel.isActive()
                        ? "⏻ Deactivate selected" : "⏻ Activate selected");
            }
            hintLabel.setText(hasSelection ? "" : "Select a row to enable actions");
        });

        editButton.setDisable(true);
        toggleActiveButton.setDisable(true);
        resetPasswordButton.setDisable(true);
        loadDepartments();
    }

    private void loadDepartments() {
        Bg.run(() -> departmentService.listActive(), departments -> {
            List<Department> withAll = new ArrayList<>();
            withAll.add(null);
            withAll.addAll(departments);
            departmentFilter.getItems().setAll(withAll);
        });
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        Role role = roleFilter.getValue();
        Department department = departmentFilter.getValue();
        Bg.run(() -> userService.search(session, role,
                        department == null ? null : department.getId(), searchField.getText()),
                found -> users.setAll(found));
    }

    @FXML
    private void handleApplyFilters() {
        reload();
    }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        roleFilter.getSelectionModel().clearSelection();
        departmentFilter.getSelectionModel().clearSelection();
        reload();
    }

    @FXML
    private void handleAddUser() {
        showEditDialog(null);
    }

    @FXML
    private void handleEditUser() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showEditDialog(selected);
        }
    }

    @FXML
    private void handleToggleActive() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String action = selected.isActive() ? "deactivate" : "activate";
        if (!Alerts.confirm("Confirm " + action,
                selected.getFullName() + " (" + selected.getEmail() + ")\n\n"
                        + action.substring(0, 1).toUpperCase() + action.substring(1) + " this account?")) {
            return;
        }
        boolean targetActive = !selected.isActive();
        Bg.run(() -> {
            userService.setActive(session, selected.getId(), targetActive);
            return (Void) null;
        }, result -> reload());
    }

    @FXML
    private void handleResetPassword() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reset password");
        dialog.setHeaderText("New password for " + selected.getFullName());
        dialog.setContentText("Password (min 8 chars, letters + digits):");
        Stylesheet.applyTo(dialog.getDialogPane());
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) {
            return;
        }
        String newPassword = result.get();
        Bg.run(() -> {
            userService.updateUser(session, copyForUpdate(selected), newPassword);
            return (Void) null;
        }, r -> Alerts.info("Password reset",
                "The password for " + selected.getEmail() + " was reset successfully."));
    }

    private User copyForUpdate(User source) {
        User copy = new User();
        copy.setId(source.getId());
        copy.setFullName(source.getFullName());
        copy.setEmail(source.getEmail());
        copy.setRole(source.getRole());
        copy.setDepartmentId(source.getDepartmentId());
        copy.setIdentifier(source.getIdentifier());
        copy.setPhone(source.getPhone());
        copy.setActive(source.isActive());
        return copy;
    }

    /** Opens the add/edit dialog as a modal JavaFX Dialog with an FXML pane. */
    private void showEditDialog(User existing) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dialog-user-edit.fxml"));
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(loader.load());
            dialog.setTitle(existing == null ? "Add user" : "Edit user");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.APPLY)).setText("Save");
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancel");

            UserEditDialogController form = loader.getController();
            form.load(existing);

            Optional<ButtonType> answer;
            while ((answer = dialog.showAndWait()).isPresent()
                    && answer.get() == ButtonType.APPLY) {
                UserEditDialogController.FormOutcome outcome = form.buildOutcome(existing == null);
                if (outcome.error() != null) {
                    outcome.error().showAndWait();
                    continue; // validation failed - let the user correct the form
                }
                Bg.run(() -> {
                    if (existing == null) {
                        userService.createUser(session, outcome.user(), outcome.password());
                    } else {
                        userService.updateUser(session, outcome.user(), outcome.password());
                    }
                    return (Void) null;
                }, result -> reload());
                break;
            }
        } catch (Exception e) {
            Alerts.error("Could not open the user form", e);
        }
    }

    @FXML
    private void handleExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export user records to CSV");
        chooser.setInitialFileName("campusos-users.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File target = chooser.showSaveDialog(userTable.getScene().getWindow());
        if (target == null) {
            return;
        }
        Bg.run(() -> csvExportService.exportUsers(session), csv -> {
            try {
                Files.writeString(target.toPath(), csv, StandardCharsets.UTF_8);
                Alerts.info("Export complete",
                        "User records exported to:\n" + target.getAbsolutePath());
            } catch (Exception ioError) {
                Alerts.error("Could not write the file", ioError);
            }
        });
    }
}
