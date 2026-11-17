package com.campusos.controller;

import com.campusos.model.Booking;
import com.campusos.model.BookingStatus;
import com.campusos.model.Role;
import com.campusos.service.BookingService;
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
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * My bookings (Module 2, student + faculty): submit new requests through
 * the booking dialog, track statuses and cancel eligible bookings.
 */
public class MyBookingsController implements ViewController {

    @FXML private ComboBox<BookingStatus> statusFilter;
    @FXML private Button newBookingButton;
    @FXML private Button cancelButton;
    @FXML private TableView<Booking> bookingTable;
    @FXML private TableColumn<Booking, Number> colId;
    @FXML private TableColumn<Booking, String> colResource;
    @FXML private TableColumn<Booking, String> colDate;
    @FXML private TableColumn<Booking, String> colStart;
    @FXML private TableColumn<Booking, String> colEnd;
    @FXML private TableColumn<Booking, String> colPurpose;
    @FXML private TableColumn<Booking, String> colStatus;
    @FXML private TableColumn<Booking, String> colRemarks;
    @FXML private Label hintLabel;

    private Session session;
    private Navigator navigator;
    private final BookingService bookingService = new BookingService();
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
        colPurpose.setCellValueFactory(new PropertyValueFactory<>("purpose"));
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
                if (empty || item == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(Ui.badge(item, Ui.badgeClass(getTableRow().getItem().getStatus())));
                }
            }
        });
        bookingTable.setItems(bookings);
        bookingTable.setPlaceholder(new Label(
                "No bookings yet - press \"New booking request\" to reserve a resource."));

        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(BookingStatus.values());
        statusFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(BookingStatus s) { return s == null ? "All statuses" : s.getLabel(); }
            @Override public BookingStatus fromString(String s) { return null; }
        });

        bookingTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean cancellable = sel != null
                    && com.campusos.service.BookingRules.isCancellable(sel, java.time.LocalDateTime.now());
            cancelButton.setDisable(!cancellable);
        });
        cancelButton.setDisable(true);
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        Bg.run(() -> bookingService.listMine(session, statusFilter.getValue()),
                found -> bookings.setAll(found));
    }

    @FXML
    private void handleApplyFilters() {
        reload();
    }

    @FXML
    private void handleNewBooking() {
        BookingDialogController.showDialog(session, navigator, null);
    }

    @FXML
    private void handleCancel() {
        Booking selected = bookingTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!Alerts.confirm("Cancel booking",
                selected.getResourceName() + " on " + Fmt.date(selected.getBookingDate())
                        + " (" + Fmt.time(selected.getStartTime()) + "–" + Fmt.time(selected.getEndTime())
                        + ")\n\nCancel this booking?")) {
            return;
        }
        Bg.run(() -> {
            bookingService.cancel(session, selected.getId());
            return (Void) null;
        }, result -> reload());
    }
}
