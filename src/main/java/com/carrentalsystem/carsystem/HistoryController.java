package com.carrentalsystem.carsystem;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import java.io.File;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * History: every rental that has been returned. A rental moves here from the Rentals
 * screen as soon as it is marked as returned.
 */
public class HistoryController {

    /** How many records the table shows on one page. */
    private static final int PAGE_SIZE = 10;

    private static final DateTimeFormatter TABLE_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    @FXML private Button vehiclesBtn;
    @FXML private Button accountsBtn;
    @FXML private TextField searchField;
    @FXML private Label countLabel;

    @FXML private TableView<HistoryRow> historyTable;
    @FXML private TableColumn<HistoryRow, Integer> idCol;
    @FXML private TableColumn<HistoryRow, String> customerCol;
    @FXML private TableColumn<HistoryRow, String> contactCol;
    @FXML private TableColumn<HistoryRow, String> addressCol;
    @FXML private TableColumn<HistoryRow, String> vehicleCol;
    @FXML private TableColumn<HistoryRow, LocalDate> rentDateCol;
    @FXML private TableColumn<HistoryRow, LocalDate> returnedOnCol;
    @FXML private TableColumn<HistoryRow, String> timingCol;
    @FXML private TableColumn<HistoryRow, Double> totalCol;
    @FXML private TableColumn<HistoryRow, String> rentedByCol;
    @FXML private TableColumn<HistoryRow, String> returnedByCol;

    @FXML private Button pagePrevBtn;
    @FXML private Button pageNextBtn;
    @FXML private Label pageLabel;
    @FXML private Label pageInfoLabel;
    private Pager<HistoryRow> pager;
    @FXML private TableColumn<HistoryRow, Void> actionsCol;

    @FXML
    public void initialize() {
        boolean admin = Session.isAdmin();
        vehiclesBtn.setVisible(admin);
        vehiclesBtn.setManaged(admin);
        accountsBtn.setVisible(admin);
        accountsBtn.setManaged(admin);

        pager = new Pager<>(historyTable, pagePrevBtn, pageNextBtn, pageLabel, pageInfoLabel, PAGE_SIZE);
        setUpTable();
        TableTools.fitHeaders(historyTable);    // no column title is cut
        TableTools.lockColumns(historyTable);   // columns can't be dragged wider
        searchField.textProperty().addListener((o, a, b) -> loadHistory(b));
        loadHistory("");
    }

    // ---- Table ----

    private void sizeColumn(TableColumn<HistoryRow, ?> col, double share, double minWidth) {
        // Keep a real content-sized minimum. The table scrolls horizontally on
        // narrow screens instead of squeezing badges, names and action buttons.
        col.setMinWidth(minWidth);
        col.setPrefWidth(minWidth);
    }

    private void setUpTable() {
        historyTable.setPlaceholder(new Label("No completed rentals yet"));

        // Fixed readable widths: FXML prefWidth alone was previously overridden.
        sizeColumn(idCol, 0, 85);
        sizeColumn(customerCol, 0, 165);
        sizeColumn(contactCol, 0, 140);
        sizeColumn(addressCol, 0, 210);
        sizeColumn(vehicleCol, 0, 185);
        sizeColumn(rentDateCol, 0, 135);
        sizeColumn(returnedOnCol, 0, 140);
        sizeColumn(timingCol, 0, 160);
        sizeColumn(totalCol, 0, 115);
        sizeColumn(rentedByCol, 0, 150);
        sizeColumn(returnedByCol, 0, 150);
        sizeColumn(actionsCol, 0, 210);

        idCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().id));
        idCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : "#" + value);
            }
        });

        rentedByCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().rentedBy));
        returnedByCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().returnedBy));
        customerCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().customer));
        contactCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().contact));
        addressCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().address));
        vehicleCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().vehicle));

        rentDateCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().rentDate));
        rentDateCol.setCellFactory(col -> dateCell());
        returnedOnCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().returnedOn));
        returnedOnCol.setCellFactory(col -> dateCell());

        timingCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().timing));
        timingCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isEmpty()) {
                    setGraphic(null);
                    setText(empty ? null : "\u2014");
                    return;
                }
                Label badge = new Label(value);
                badge.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                badge.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                if (value.startsWith("Late")) {
                    badge.getStyleClass().add("badge-red");
                } else if (value.equals("Early")) {
                    badge.getStyleClass().add("badge-blue");
                } else {
                    badge.getStyleClass().add("badge-green");
                }
                setText(null);
                setGraphic(badge);
            }
        });

        totalCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().total));
        totalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : "\u20b1" + String.format("%,.0f", value));
            }
        });

        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button receiptBtn = new Button("Receipt");
            private final Button viewIdBtn = new Button("View ID");
            private final HBox box = new HBox(6, receiptBtn, viewIdBtn);

            {
                receiptBtn.getStyleClass().addAll("ghost-button", "table-btn");
                viewIdBtn.getStyleClass().addAll("ghost-button", "table-btn");
                receiptBtn.setOnAction(e -> {
                    HistoryRow row = rowAtIndex();
                    if (row != null) openReceipt(row.id);
                });
                viewIdBtn.setOnAction(e -> {
                    HistoryRow row = rowAtIndex();
                    if (row != null) showIdPhoto(row);
                });
            }

            private HistoryRow rowAtIndex() {
                int i = getIndex();
                return (i >= 0 && i < getTableView().getItems().size()) ? getTableView().getItems().get(i) : null;
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                HistoryRow row = empty ? null : rowAtIndex();
                if (row == null) {
                    setGraphic(null);
                    return;
                }
                // Older rentals were made before ID photos were uploaded, so they have nothing to view.
                boolean hasPhoto = row.idPhoto != null && !row.idPhoto.isBlank();
                viewIdBtn.setVisible(hasPhoto);
                viewIdBtn.setManaged(hasPhoto);
                setGraphic(box);
            }
        });
    }

    private TableCell<HistoryRow, LocalDate> dateCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(LocalDate value, boolean empty) {
                super.updateItem(value, empty);
                // Older records were returned before the system saved the return date.
                setText(empty ? null : (value == null ? "\u2014" : value.format(TABLE_DATE)));
            }
        };
    }

    private void loadHistory(String search) {
        String sql =
                "SELECT r.rental_id, c.full_name, c.contact_no, c.address, c.id_photo, v.model, v.plate_no, " +
                        "r.rent_date, r.return_date, r.returned_on, r.total_amount, r.created_by, r.returned_by " +
                        "FROM rentals r " +
                        "JOIN customers c ON r.customer_id = c.customer_id " +
                        "JOIN vehicles v ON r.vehicle_id = v.vehicle_id " +
                        "WHERE r.status = 'Returned' " +
                        "AND (c.full_name LIKE ? OR c.contact_no LIKE ? OR v.model LIKE ? OR v.plate_no LIKE ?) " +
                        "ORDER BY COALESCE(r.returned_on, r.return_date) DESC, r.rental_id DESC";

        ObservableList<HistoryRow> rows = FXCollections.observableArrayList();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String like = "%" + (search == null ? "" : search.trim()) + "%";
            for (int i = 1; i <= 4; i++) {
                stmt.setString(i, like);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate agreedReturn = rs.getDate("return_date").toLocalDate();
                    java.sql.Date ro = rs.getDate("returned_on");
                    LocalDate returnedOn = ro == null ? null : ro.toLocalDate();

                    String timing = "";
                    if (returnedOn != null) {
                        if (returnedOn.isBefore(agreedReturn)) {
                            timing = "Early";
                        } else if (returnedOn.isAfter(agreedReturn)) {
                            long late = ChronoUnit.DAYS.between(agreedReturn, returnedOn);
                            timing = "Late (" + late + (late == 1 ? " day)" : " days)");
                        } else {
                            timing = "On time";
                        }
                    }

                    String contact = rs.getString("contact_no");
                    String address = rs.getString("address");
                    rows.add(new HistoryRow(
                            rs.getInt("rental_id"),
                            rs.getString("full_name"),
                            contact == null || contact.isBlank() ? "\u2014" : contact,
                            address == null || address.isBlank() ? "\u2014" : address,
                            rs.getString("model") + " \u00b7 " + rs.getString("plate_no"),
                            rs.getDate("rent_date").toLocalDate(),
                            returnedOn,
                            timing,
                            rs.getDouble("total_amount"),
                            rs.getString("id_photo"),
                            dash(rs.getString("created_by")),
                            dash(rs.getString("returned_by"))
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            countLabel.setText("Could not load the history. Check the database connection.");
            return;
        }

        pager.setItems(rows);
        countLabel.setText(rows.size() + " completed rental" + (rows.size() == 1 ? "" : "s"));
    }

    /** Older records have no staff name saved, so they show a dash. */
    private static String dash(String value) {
        return value == null || value.isBlank() ? "\u2014" : value;
    }

    /** Shows the photo of the customer's valid ID. */
    private void showIdPhoto(HistoryRow row) {
        File file = IdPhotoStorage.file(row.idPhoto);
        Alert dialog;
        if (file == null || !file.exists()) {
            dialog = Dialogs.create(Alert.AlertType.WARNING,
                    "The ID photo could not be found in the valid_ids folder.", ButtonType.OK);
        } else {
            dialog = Dialogs.create(Alert.AlertType.INFORMATION, "", ButtonType.CLOSE);
            dialog.getDialogPane().setContent(new ImageView(new Image(file.toURI().toString(), 640, 440, true, true, false)));
        }
        dialog.setTitle("Valid ID photo");
        dialog.setHeaderText(row.customer + " \u00b7 Valid ID");
        dialog.showAndWait();
    }

    private void openReceipt(int rentalId) {
        try {
            ReceiptController controller = Navigator.show(
                    (Stage) countLabel.getScene().getWindow(),
                    "receipt-view.fxml", "Car Rental System - Receipt");
            controller.setRentalId(rentalId);
            controller.setBackToHistory();
        } catch (IOException e) {
            e.printStackTrace();
            Alert error = Dialogs.create(Alert.AlertType.ERROR, "The receipt screen could not be opened.");
            error.setHeaderText(null);
            error.setTitle("Receipt");
            error.showAndWait();
        }
    }

    // ---- Navigation ----

    @FXML
    private void onDashboardClick() {
        switchScene("dashboard-view.fxml", "Car Rental System - Dashboard");
    }

    @FXML
    private void onVehiclesClick() {
        if (!Session.isAdmin()) return;
        switchScene("vehicles-view.fxml", "Car Rental System - Vehicles");
    }

    @FXML
    private void onRentalsClick() {
        switchScene("rentals-view.fxml", "Car Rental System - Rentals");
    }

    @FXML
    private void onHistoryClick() {
        // Already here
    }

    @FXML
    private void onAccountsClick() {
        if (!Session.isAdmin()) return;
        switchScene("accounts-view.fxml", "Car Rental System - Accounts");
    }

    @FXML
    private void onLogoutClick() {
        Alert confirm = Dialogs.create(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to log out?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Log out");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            Session.clear();
            try {
                Navigator.showLogin((Stage) countLabel.getScene().getWindow());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void switchScene(String fxmlFile, String title) {
        try {
            Navigator.show((Stage) countLabel.getScene().getWindow(), fxmlFile, title);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Backs one row of the history table. */
    public static class HistoryRow {
        final int id;
        final String customer;
        final String contact;
        final String address;
        final String vehicle;
        final LocalDate rentDate;
        final LocalDate returnedOn;   // null for records returned before this date was saved
        final String timing;          // Early, On time, Late (n days) or empty
        final double total;
        final String idPhoto;     // file name in the valid_ids folder, or null
        final String rentedBy;    // staff who made the rental (gets the sale)
        final String returnedBy;  // staff who received the car back

        HistoryRow(int id, String customer, String contact, String address, String vehicle,
                   LocalDate rentDate, LocalDate returnedOn, String timing, double total,
                   String idPhoto, String rentedBy, String returnedBy) {
            this.id = id;
            this.customer = customer;
            this.contact = contact;
            this.address = address;
            this.vehicle = vehicle;
            this.rentDate = rentDate;
            this.returnedOn = returnedOn;
            this.timing = timing;
            this.total = total;
            this.idPhoto = idPhoto;
            this.rentedBy = rentedBy;
            this.returnedBy = returnedBy;
        }
    }
}