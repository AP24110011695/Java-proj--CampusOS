package com.campusos.controller;

import com.campusos.model.Resource;
import com.campusos.model.ResourceStatus;
import com.campusos.model.ResourceType;
import com.campusos.model.Role;
import com.campusos.service.ResourceService;
import com.campusos.session.Session;
import com.campusos.ui.Navigator;
import com.campusos.ui.Ui;
import com.campusos.ui.View;
import com.campusos.ui.ViewController;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;

import java.util.Optional;

/**
 * Campus resources (Module 2). Administrators manage the catalog; students
 * and faculty browse and jump straight into the booking dialog.
 */
public class ResourcesController implements ViewController {

    @FXML private TextField searchField;
    @FXML private ComboBox<ResourceType> typeFilter;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button toggleButton;
    @FXML private Button bookButton;
    @FXML private TableView<Resource> resourceTable;
    @FXML private TableColumn<Resource, Number> colId;
    @FXML private TableColumn<Resource, String> colName;
    @FXML private TableColumn<Resource, String> colType;
    @FXML private TableColumn<Resource, String> colLocation;
    @FXML private TableColumn<Resource, Number> colCapacity;
    @FXML private TableColumn<Resource, String> colStatus;
    @FXML private TableColumn<Resource, String> colDescription;
    @FXML private Label hintLabel;

    private Session session;
    private Navigator navigator;
    private final ResourceService resourceService = new ResourceService();
    private final ObservableList<Resource> resources = FXCollections.observableArrayList();
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
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colLocation.setCellValueFactory(new PropertyValueFactory<>("location"));
        colCapacity.setCellValueFactory(new PropertyValueFactory<>("capacity"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colType.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getType() == null ? "" : data.getValue().getType().getLabel()));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus() == null ? "" : data.getValue().getStatus().getLabel()));
        colType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    ResourceStatus status = ResourceStatus.valueOf(
                            item.toUpperCase().replace(" ", "_").replace("UNDER_", ""));
                    setGraphic(Ui.badge(item, Ui.badgeClass(status)));
                }
            }
        });
        resourceTable.setItems(resources);
        resourceTable.setPlaceholder(new Label("No resources found."));

        typeFilter.getItems().add(null);
        typeFilter.getItems().addAll(ResourceType.values());
        typeFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ResourceType type) { return type == null ? "All types" : type.getLabel(); }
            @Override public ResourceType fromString(String s) { return null; }
        });

        resourceTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean hasSelection = sel != null;
            bookButton.setDisable(!hasSelection || !isAdmin && sel != null
                    && sel.getStatus() != ResourceStatus.ACTIVE);
            if (isAdmin) {
                editButton.setDisable(!hasSelection);
                toggleButton.setDisable(!hasSelection);
            }
        });

        // Role-aware toolbar
        addButton.setVisible(isAdmin);
        addButton.setManaged(isAdmin);
        editButton.setVisible(isAdmin);
        editButton.setManaged(isAdmin);
        toggleButton.setVisible(isAdmin);
        toggleButton.setManaged(isAdmin);
        editButton.setDisable(true);
        toggleButton.setDisable(true);
        bookButton.setVisible(!isAdmin);
        bookButton.setManaged(!isAdmin);
        bookButton.setDisable(true);
        hintLabel.setText(isAdmin
                ? "As administrator you can add, edit and set resource status."
                : "Select an active resource and press \"Book selected\" to request a slot.");
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        if (isAdmin) {
            Bg.run(() -> resourceService.search(session, typeFilter.getValue(), searchField.getText()),
                    found -> resources.setAll(found));
        } else {
            Bg.run(resourceService::listBookable, found -> resources.setAll(found));
        }
    }

    @FXML
    private void handleApplyFilters() {
        reload();
    }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        typeFilter.getSelectionModel().clearSelection();
        reload();
    }

    @FXML
    private void handleAdd() {
        showEditDialog(null);
    }

    @FXML
    private void handleEdit() {
        Resource selected = resourceTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showEditDialog(selected);
        }
    }

    @FXML
    private void handleToggleStatus() {
        Resource selected = resourceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        ResourceStatus next = switch (selected.getStatus()) {
            case ACTIVE -> ResourceStatus.INACTIVE;
            case INACTIVE -> ResourceStatus.MAINTENANCE;
            case MAINTENANCE -> ResourceStatus.ACTIVE;
        };
        Bg.run(() -> {
            resourceService.setStatus(session, selected.getId(), next);
            return (Void) null;
        }, result -> reload());
    }

    /** Students/faculty: open the booking dialog for the selected resource. */
    @FXML
    private void handleBookSelected() {
        Resource selected = resourceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        BookingDialogController.showDialog(session, navigator, selected.getId());
    }

    private void showEditDialog(Resource existing) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dialog-resource-edit.fxml"));
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(loader.load());
            dialog.setTitle(existing == null ? "Add resource" : "Edit resource");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.APPLY)).setText("Save");
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancel");

            ResourceEditDialogController form = loader.getController();
            form.load(existing);

            Optional<ButtonType> answer;
            while ((answer = dialog.showAndWait()).isPresent()
                    && answer.get() == ButtonType.APPLY) {
                ResourceEditDialogController.FormOutcome outcome = form.buildOutcome(existing == null);
                if (outcome.error() != null) {
                    outcome.error().showAndWait();
                    continue;
                }
                Bg.run(() -> {
                    if (existing == null) {
                        resourceService.create(session, outcome.resource());
                    } else {
                        outcome.resource().setId(existing.getId());
                        resourceService.update(session, outcome.resource());
                    }
                    return (Void) null;
                }, r -> reload());
                break;
            }
        } catch (Exception e) {
            Alerts.error("Could not open the resource form", e);
        }
    }
}
