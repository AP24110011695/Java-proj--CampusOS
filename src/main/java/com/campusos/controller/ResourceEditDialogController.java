package com.campusos.controller;

import com.campusos.model.Resource;
import com.campusos.model.ResourceStatus;
import com.campusos.model.ResourceType;
import com.campusos.service.ResourceService;
import com.campusos.util.Alerts;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.List;

/**
 * Add/Edit resource form inside a modal dialog (admin only).
 */
public class ResourceEditDialogController {

    @FXML private Label formError;
    @FXML private TextField nameField;
    @FXML private ComboBox<ResourceType> typeCombo;
    @FXML private ComboBox<ResourceStatus> statusCombo;
    @FXML private TextField locationField;
    @FXML private TextField capacityField;
    @FXML private TextArea descriptionField;

    /** Result holder for the caller. */
    public record FormOutcome(Resource resource, Alert error) { }

    @FXML
    private void initialize() {
        formError.visibleProperty().bind(formError.textProperty().isNotEmpty());
        formError.managedProperty().bind(formError.textProperty().isNotEmpty());

        typeCombo.getItems().setAll(ResourceType.values());
        typeCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ResourceType type) { return type == null ? "" : type.getLabel(); }
            @Override public ResourceType fromString(String s) { return null; }
        });
        statusCombo.getItems().setAll(ResourceStatus.values());
        statusCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ResourceStatus status) { return status == null ? "" : status.getLabel(); }
            @Override public ResourceStatus fromString(String s) { return null; }
        });
        statusCombo.setValue(ResourceStatus.ACTIVE);
    }

    public void load(Resource existing) {
        if (existing == null) {
            return;
        }
        nameField.setText(existing.getName());
        typeCombo.setValue(existing.getType());
        statusCombo.setValue(existing.getStatus());
        locationField.setText(existing.getLocation());
        capacityField.setText(existing.getCapacity() == null ? "" : String.valueOf(existing.getCapacity()));
        descriptionField.setText(existing.getDescription());
    }

    public FormOutcome buildOutcome(boolean isCreate) {
        Resource resource = new Resource();
        resource.setName(nameField.getText());
        resource.setType(typeCombo.getValue());
        resource.setStatus(statusCombo.getValue() == null ? ResourceStatus.ACTIVE : statusCombo.getValue());
        resource.setLocation(locationField.getText());
        resource.setDescription(descriptionField.getText());

        String capacityText = capacityField.getText();
        if (capacityText != null && !capacityText.isBlank()) {
            try {
                resource.setCapacity(Integer.parseInt(capacityText.trim()));
            } catch (NumberFormatException e) {
                return new FormOutcome(null, Alerts.errorAlert("Invalid capacity",
                        "Capacity must be a whole number."));
            }
        }

        List<String> problems = new java.util.ArrayList<>();
        if (resource.getName() == null || resource.getName().isBlank()) {
            problems.add("Resource name is required.");
        }
        if (resource.getType() == null) {
            problems.add("Resource type is required.");
        }
        if (!problems.isEmpty()) {
            return new FormOutcome(null, Alerts.errorAlert("Please fix the form",
                    String.join("\n", problems)));
        }
        return new FormOutcome(resource, null);
    }
}
