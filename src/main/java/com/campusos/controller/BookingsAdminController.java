package com.campusos.controller;

import com.campusos.model.Booking;
import com.campusos.model.BookingStatus;
import com.campusos.service.BookingService;
import com.campusos.service.CsvExportService;
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
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Booking approvals (Module 2, admin): filter all requests, approve or
 * reject pending ones with remarks, mark approved past bookings completed,
 * and export the full booking history.
 */
public class BookingsAdminController implements ViewController {

    @FXML private ComboBox<BookingStatus> statusFilter;
    @FXML private DatePicker dateFilter;
    @FXML private TextField searchField;
    @FXML private TableView<Booking> bookingTable;
    @FXML private TableColumn<Booking, Number> colId;
    @FXML private TableColumn<Booking, String> colResource;
    @FXML private TableColumn<Booking, String> colUser;
    @FXML private TableColumn<Booking, String> colDate;
    @FXML private TableColumn<Booking, String> colStart;
    @FXML private TableColumn<Booking, String> colEnd;
    @FXML private TableColumn<Booking, String> colPurpose;
    @FXML private TableColumn<Booking, String> colStatus;
    @FXML private TableColumn<Booking, String> colDecidedBy;
    @FXML private TableColumn<Booking, String> colRemarks;
    @FXML private Button approveButton;
    @FXML private Button rejectButton;
    @FXML private Button completeButton;
    @FXML private Label hintLabel;

    private Session session;
    private Navigator navigator;
    private final BookingService bookingService = new BookingService();
    private final CsvExportService csvExportService = new CsvExportService();
    private final ObservableList<Booking> bookings = FXCollections.observableArrayList();

    @Override
    public void initContext(Session session, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colResource.setCellValueFactory(new PropertyValueFactory<>("resourceName"));
        colUser.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getUserName() + " (" + data.getValue().getUserEmail() + ")"));
        colPurpose.setCellValueFactory(new PropertyValueFactory<>("purpose"));
        colDecidedBy.setCellValueFactory(new PropertyValueFactory<>("decidedByName"));
        colRemarks.setCellValueFactory(new PropertyValueFactory<>("decisionRemarks"));
        colDate.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.date(data.getValue().getBookingDate())));
        colStart.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.time(data.getValue().getStartTime())));
        colEnd.setCellValueFactory(data -> new SimpleStringProperty(
                Fmt.time(data.getValue().getEndTime())));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatus().getLabel()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(Ui.badge(item, Ui.badgeClass(getTableRow().getItem().getStatus())));
                }
            }
        });
        bookingTable.setItems(bookings);
        bookingTable.setPlaceholder(new Label("No bookings match the current filters."));

        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(BookingStatus.values());
        statusFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(BookingStatus s) { return s == null ? "All statuses" : s.getLabel(); }
            @Override public BookingStatus fromString(String s) { return null; }
        });

        bookingTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean pending = sel != null && sel.getStatus() == BookingStatus.PENDING;
            boolean approved = sel != null && sel.getStatus() == BookingStatus.APPROVED;
            approveButton.setDisable(!pending);
            rejectButton.setDisable(!pending);
            completeButton.setDisable(!approved);
            hintLabel.setText(sel == null ? "Select a booking to act on it" : "");
        });

        approveButton.setDisable(true);
        rejectButton.setDisable(true);
        completeButton.setDisable(true);
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        LocalDate date = dateFilter.getValue();
        Bg.run(() -> bookingService.listAll(session, statusFilter.getValue(), null, date,
                        searchField.getText()),
                found -> bookings.setAll(found));
    }

    @FXML
    private void handleApplyFilters() {
        reload();
    }

    @FXML
    private void handleClearFilters() {
        statusFilter.getSelectionModel().clearSelection();
        dateFilter.setValue(null);
        searchField.clear();
        reload();
    }

    @FXML
    private void handleApprove() {
        decide(BookingStatus.APPROVED, null);
    }

    @FXML
    private void handleReject() {
        TextInputDialog remarks = new TextInputDialog();
        remarks.setTitle("Reject booking");
        remarks.setHeaderText("Optional rejection remarks");
        remarks.setContentText("Reason shown to the requester:");
        com.campusos.util.Stylesheet.applyTo(remarks.getDialogPane());
        Optional<String> input = remarks.showAndWait();
        decide(BookingStatus.REJECTED, input.orElse(""));
    }

    private void decide(BookingStatus decision, String remarks) {
        Booking selected = bookingTable.getSelectionModel().getSelectedItem();
        if (selected == null || (decision != BookingStatus.APPROVED && decision != BookingStatus.REJECTED)) {
            return;
        }
        String verb = decision == BookingStatus.APPROVED ? "approve" : "reject";
        if (!Alerts.confirm("Confirm " + verb,
                selected.getResourceName() + " - " + Fmt.date(selected.getBookingDate())
                        + " " + Fmt.time(selected.getStartTime()) + "–" + Fmt.time(selected.getEndTime())
                        + "\nfor " + selected.getUserName()
                        + "\n\n" + verb.substring(0, 1).toUpperCase() + verb.substring(1) + " this request?")) {
            return;
        }
        Bg.run(() -> {
            bookingService.decide(session, selected.getId(), decision, remarks);
            return (Void) null;
        }, result -> reload());
    }

    @FXML
    private void handleComplete() {
        Booking selected = bookingTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Bg.run(() -> {
            bookingService.markCompleted(session, selected.getId());
            return (Void) null;
        }, result -> reload());
    }

    @FXML
    private void handleExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export bookings to CSV");
        chooser.setInitialFileName("campusos-bookings.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File target = chooser.showSaveDialog(bookingTable.getScene().getWindow());
        if (target == null) {
            return;
        }
        Bg.run(() -> csvExportService.exportBookings(session), csv -> {
            try {
                Files.writeString(target.toPath(), csv, StandardCharsets.UTF_8);
                Alerts.info("Export complete",
                        "Bookings exported to:\n" + target.getAbsolutePath());
            } catch (Exception ioError) {
                Alerts.error("Could not write the file", ioError);
            }
        });
    }
}
