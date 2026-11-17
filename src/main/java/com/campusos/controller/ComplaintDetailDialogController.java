package com.campusos.controller;

import com.campusos.model.Complaint;
import com.campusos.model.ComplaintStatus;
import com.campusos.model.ComplaintUpdate;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.service.ComplaintService;
import com.campusos.service.UserService;
import com.campusos.session.Session;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import com.campusos.util.Fmt;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.util.Callback;

import java.util.List;

/**
 * Shared complaint detail + history dialog used by the admin complaint
 * screen and the "my complaints" screen. Shows the full timeline and
 * offers the actions the current user's role permits (assign, advance,
 * close) exactly as enforced by ComplaintRules.
 */
public class ComplaintDetailDialogController {

    @FXML private Label titleLabel;
    @FXML private Label statusLabel;
    @FXML private Label priorityLabel;
    @FXML private Label categoryLabel;
    @FXML private Label submitterLabel;
    @FXML private Label assignedLabel;
    @FXML private Label createdLabel;
    @FXML private Label descriptionLabel;
    @FXML private ListView<ComplaintUpdate> historyList;
    @FXML private javafx.scene.layout.VBox actionPanel;
    @FXML private javafx.scene.layout.HBox assignPanel;
    @FXML private ComboBox<User> facultyCombo;
    @FXML private Button assignButton;
    @FXML private TextArea remarksField;
    @FXML private Button progressButton;
    @FXML private Label nextStepLabel;

    private Session session;
    private long complaintId;
    private Complaint complaint;
    private final ComplaintService complaintService = new ComplaintService();
    private final UserService userService = new UserService();
    private Runnable onChanged = () -> { };

    /** Opens the modal detail dialog; onChanged refreshes the caller's table. */
    public static void show(Session session, long complaintId, Runnable onChanged) {
        Dialog<Void> dialog = new Dialog<>();
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    ComplaintDetailDialogController.class.getResource("/fxml/dialog-complaint-detail.fxml"));
            dialog.setDialogPane(loader.load());
            ComplaintDetailDialogController controller = loader.getController();
            dialog.setTitle("Complaint details");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            controller.setup(session, complaintId, onChanged);
            dialog.showAndWait();
        } catch (Exception e) {
            Alerts.error("Could not open the complaint", e);
        }
    }

    private void setup(Session session, long complaintId, Runnable onChanged) {
        this.session = session;
        this.complaintId = complaintId;
        this.onChanged = onChanged;

        historyList.setCellFactory(new Callback<>() {
            @Override
            public ListCell<ComplaintUpdate> call(ListView<ComplaintUpdate> list) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(ComplaintUpdate update, boolean empty) {
                        super.updateItem(update, empty);
                        if (empty || update == null) {
                            setText(null);
                            setGraphic(null);
                            return;
                        }
                        String prev = update.getPreviousStatus() == null ? "—"
                                : update.getPreviousStatus().getLabel();
                        setText(Fmt.datetime(update.getCreatedAt()) + "   "
                                + prev + " → " + update.getNewStatus().getLabel()
                                + "\n" + update.getUpdatedByName() + ": " + update.getRemarks());
                        setWrapText(true);
                        getStyleClass().add("timeline-item");
                    }
                };
            }
        });

        facultyCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(User u) {
                return u == null ? "" : u.getFullName() + " (" + u.getDepartmentName() + ")";
            }
            @Override public User fromString(String s) { return null; }
        });

        reload();
    }

    private void reload() {
        Bg.run(() -> complaintService.getDetail(session, complaintId),
                detail -> Platform.runLater(() -> {
                    this.complaint = detail;
                    render();
                }));
        Bg.run(() -> complaintService.getHistory(session, complaintId),
                updates -> Platform.runLater(() -> historyList.setItems(
                        FXCollections.observableList(updates))));
    }

    private void render() {
        boolean isAdmin = session.currentUser().getRole() == Role.ADMIN;
        boolean isSubmitter = session.currentUser().getId().equals(complaint.getUserId());
        boolean isAssignee = session.currentUser().getId().equals(complaint.getAssignedToId());

        titleLabel.setText("#" + complaint.getId() + "  " + complaint.getTitle());
        statusLabel.setText(complaint.getStatus().getLabel());
        priorityLabel.setText(complaint.getPriority().getLabel());
        categoryLabel.setText(complaint.getCategory().getLabel());
        submitterLabel.setText(complaint.getUserName());
        assignedLabel.setText(complaint.getAssignedToName() == null ? "— not assigned —"
                : complaint.getAssignedToName());
        createdLabel.setText(Fmt.datetime(complaint.getCreatedAt()));
        descriptionLabel.setText(complaint.getDescription());

        // ---- role-aware actions ------------------------------------------
        assignPanel.setVisible(false);
        assignPanel.setManaged(false);
        progressButton.setVisible(false);
        progressButton.setManaged(false);
        remarksField.setVisible(false);
        remarksField.setManaged(false);
        nextStepLabel.setText("");

        if (complaint.getStatus() == ComplaintStatus.SUBMITTED && isAdmin) {
            assignPanel.setVisible(true);
            assignPanel.setManaged(true);
            Bg.run(() -> userService.listByRole(session, Role.FACULTY, true),
                    faculty -> facultyCombo.setItems(FXCollections.observableList(faculty)));
        } else if (complaint.getStatus() == ComplaintStatus.ASSIGNED && (isAdmin || isAssignee)) {
            progressButton.setVisible(true);
            progressButton.setManaged(true);
            remarksField.setVisible(true);
            remarksField.setManaged(true);
            progressButton.setText("→ Start working on it (IN_PROGRESS)");
            nextStepLabel.setText("Next lifecycle step: IN_PROGRESS");
        } else if (complaint.getStatus() == ComplaintStatus.IN_PROGRESS && (isAdmin || isAssignee)) {
            progressButton.setVisible(true);
            progressButton.setManaged(true);
            remarksField.setVisible(true);
            remarksField.setManaged(true);
            progressButton.setText("→ Mark resolved (RESOLVED)");
            nextStepLabel.setText("Next lifecycle step: RESOLVED");
        } else if (complaint.getStatus() == ComplaintStatus.RESOLVED && (isAdmin || isSubmitter)) {
            progressButton.setVisible(true);
            progressButton.setManaged(true);
            remarksField.setVisible(true);
            remarksField.setManaged(true);
            progressButton.setText("✔ Close complaint (CLOSED)");
            nextStepLabel.setText("Next lifecycle step: CLOSED");
        } else if (complaint.getStatus() == ComplaintStatus.CLOSED) {
            nextStepLabel.setText("This complaint is closed. The full history is shown above.");
        } else if (!isAdmin && !isSubmitter && !isAssignee) {
            nextStepLabel.setText("");
        }
        if (isAdmin && complaint.getStatus() == ComplaintStatus.ASSIGNED) {
            // Admin may reassign while ASSIGNED
            assignPanel.setVisible(true);
            assignPanel.setManaged(true);
            Bg.run(() -> userService.listByRole(session, Role.FACULTY, true),
                    faculty -> facultyCombo.setItems(FXCollections.observableList(faculty)));
        }
        actionPanel.setVisible(assignPanel.isVisible() || progressButton.isVisible());
        actionPanel.setManaged(actionPanel.isVisible());
    }

    @FXML
    private void handleAssign() {
        User faculty = facultyCombo.getValue();
        if (faculty == null) {
            Alerts.error("Assign", "Please choose a faculty member.");
            return;
        }
        Bg.run(() -> {
            complaintService.assign(session, complaint.getId(), faculty.getId(), remarksField.getText());
            return (Void) null;
        }, result -> {
            onChanged.run();
            reload();
        });
    }

    @FXML
    private void handleAdvance() {
        ComplaintStatus current = complaint.getStatus();
        ComplaintStatus next = switch (current) {
            case ASSIGNED -> ComplaintStatus.IN_PROGRESS;
            case IN_PROGRESS -> ComplaintStatus.RESOLVED;
            case RESOLVED -> ComplaintStatus.CLOSED;
            default -> null;
        };
        if (next == null) {
            return;
        }
        String remarks = remarksField.getText();
        if (remarks == null || remarks.isBlank()) {
            Alerts.error("Remarks required",
                    "Please describe what was done (a remark is stored with every status change).");
            return;
        }
        Bg.run(() -> {
            complaintService.updateStatus(session, complaint.getId(), next, remarks);
            return (Void) null;
        }, result -> {
            onChanged.run();
            reload();
        });
    }
}
