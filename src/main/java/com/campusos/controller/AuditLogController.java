package com.campusos.controller;

import com.campusos.dao.AuditLogDAO;
import com.campusos.dao.Db;
import com.campusos.model.AuditLog;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.service.UserService;
import com.campusos.session.Session;
import com.campusos.ui.Navigator;
import com.campusos.ui.ViewController;
import com.campusos.util.Alerts;
import com.campusos.util.Bg;
import com.campusos.util.Fmt;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Audit logs (Module 5, admin): filterable, read-only trail of system
 * activities. There is intentionally no edit or delete UI.
 */
public class AuditLogController implements ViewController {

    @FXML private TextField actionFilter;
    @FXML private ComboBox<User> userFilter;
    @FXML private TableView<AuditLog> auditTable;
    @FXML private TableColumn<AuditLog, Number> colId;
    @FXML private TableColumn<AuditLog, String> colTime;
    @FXML private TableColumn<AuditLog, String> colUser;
    @FXML private TableColumn<AuditLog, String> colAction;
    @FXML private TableColumn<AuditLog, String> colEntity;
    @FXML private TableColumn<AuditLog, Number> colEntityId;
    @FXML private TableColumn<AuditLog, String> colDescription;
    @FXML private Label countLabel;

    private Session session;
    private Navigator navigator;
    private final UserService userService = new UserService();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();
    private final ObservableList<AuditLog> logs = FXCollections.observableArrayList();

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTime.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.datetime(data.getValue().getCreatedAt())));
        colUser.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getUserName() == null ? "(system)" : data.getValue().getUserName()));
        colAction.setCellValueFactory(new PropertyValueFactory<>("action"));
        colEntity.setCellValueFactory(new PropertyValueFactory<>("entityType"));
        colEntityId.setCellValueFactory(new PropertyValueFactory<>("entityId"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        auditTable.setItems(logs);
        auditTable.setPlaceholder(new Label("No audit entries match the current filters."));

        userFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(User u) { return u == null ? "All users" : u.getDisplayLabel(); }
            @Override public User fromString(String s) { return null; }
        });

        Bg.run(() -> userService.search(session, null, null, null), users -> {
            List<User> items = new ArrayList<>();
            items.add(null);
            items.addAll(users);
            userFilter.getItems().setAll(items);
        });
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        User user = userFilter.getValue();
        Bg.run(() -> Db.withConnection(conn -> auditLogDAO.findWithFilters(conn,
                        actionFilter.getText(),
                        user == null ? null : user.getId(),
                        null, 500)),
                found -> {
                    logs.setAll(found);
                    countLabel.setText(found.size() + " entries (newest first, latest 500 shown)");
                });
    }

    @FXML
    private void handleApplyFilters() {
        reload();
    }

    @FXML
    private void handleClearFilters() {
        actionFilter.clear();
        userFilter.getSelectionModel().clearSelection();
        reload();
    }
}
