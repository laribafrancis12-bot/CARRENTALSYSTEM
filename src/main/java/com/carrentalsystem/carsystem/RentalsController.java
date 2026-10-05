package com.carrentalsystem.carsystem;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RentalsController {

    private static final DateTimeFormatter TABLE_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    /** Which pill is selected: All, Active, Due or Returned. */
    private String currentFilter = "All";

    @FXML private Button vehiclesBtn;
    @FXML private Button accountsBtn;
    @FXML private StackPane newRentalOverlay;
    @FXML private TextField searchField;
    @FXML private Label countLabel;

    @FXML private Button filterAllBtn;
    @FXML private Button filterActiveBtn;
    @FXML private Button filterDueBtn;
    @FXML private Button filterReturnedBtn;

    @FXML private TableView<RentalRow> rentalTable;
    @FXML private TableColumn<RentalRow, Integer> idCol;
    @FXML private TableColumn<RentalRow, String> customerCol;
    @FXML private TableColumn<RentalRow, String> contactCol;
    @FXML private TableColumn<RentalRow, String> vehicleCol;
    @FXML private TableColumn<RentalRow, LocalDate> rentDateCol;
    @FXML private TableColumn<RentalRow, LocalDate> returnDateCol;
    @FXML private TableColumn<RentalRow, Double> totalCol;
    @FXML private TableColumn<RentalRow, String> statusCol;
    @FXML private TableColumn<RentalRow, Void> actionsCol;

    @FXML private TextField customerNameField;
    @FXML private TextField contactField;
    @FXML private TextField licenseField;
    @FXML private ComboBox<VehicleOption> vehicleCombo;
    @FXML private DatePicker rentDatePicker;
    @FXML private DatePicker returnDatePicker;
    @FXML private ComboBox<String> paymentCombo;
    @FXML private Label totalBreakdownLabel;
    @FXML private Label totalAmountLabel;
    @FXML private Label formMessageLabel;

    @FXML
    public void initialize() {
        boolean admin = Session.isAdmin();
        vehiclesBtn.setVisible(admin);
        vehiclesBtn.setManaged(admin);
        accountsBtn.setVisible(admin);
        accountsBtn.setManaged(admin);

        paymentCombo.setItems(FXCollections.observableArrayList("Cash"));
        paymentCombo.getSelectionModel().selectFirst();

        rentDatePicker.setValue(LocalDate.now());
        returnDatePicker.setValue(LocalDate.now().plusDays(1));

        rentDatePicker.valueProperty().addListener((o, a, b) -> updateTotal());
        returnDatePicker.valueProperty().addListener((o, a, b) -> updateTotal());
        vehicleCombo.valueProperty().addListener((o, a, b) -> updateTotal());

        // The search box was never connected before; now the board filters as you type.
        searchField.textProperty().addListener((o, a, b) -> loadBoard(b));

        setUpTable();
        DatabaseConnection.refreshOverdue();
        loadBoard("");
    }

    // ---- Board ----

    /** Gives a column a share of the table width (with a minimum), so the table fills the window. */
    private void sizeColumn(TableColumn<RentalRow, ?> col, double share, double minWidth) {
        col.setMinWidth(minWidth);
        // minus 24px so the vertical scrollbar never forces a horizontal one
        col.prefWidthProperty().bind(rentalTable.widthProperty().subtract(24).multiply(share));
    }

    private void setUpTable() {
        rentalTable.setPlaceholder(new Label("No rentals found"));

        sizeColumn(idCol, 0.065, 72);
        sizeColumn(customerCol, 0.145, 105);
        sizeColumn(contactCol, 0.11, 100);
        sizeColumn(vehicleCol, 0.15, 135);
        sizeColumn(rentDateCol, 0.10, 95);
        sizeColumn(returnDateCol, 0.10, 95);
        sizeColumn(totalCol, 0.09, 80);
        sizeColumn(statusCol, 0.10, 110);
        sizeColumn(actionsCol, 0.14, 190);

        idCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().id));
        idCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : "#" + value);
            }
        });

        customerCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().customer));
        contactCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().contact));
        vehicleCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().vehicle));

        rentDateCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().rentDate));
        rentDateCol.setCellFactory(col -> dateCell());
        returnDateCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().returnDate));
        returnDateCol.setCellFactory(col -> dateCell());

        totalCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().total));
        totalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : "\u20b1" + String.format("%,.0f", value));
            }
        });

        statusCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().status));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(value);
                switch (value) {
                    case "Returned" -> badge.getStyleClass().add("badge-green");
                    case "Overdue" -> badge.getStyleClass().add("badge-red");
                    case "Due today" -> badge.getStyleClass().add("badge-amber");
                    default -> badge.getStyleClass().add("badge-blue");
                }
                setGraphic(badge);
                setText(null);
            }
        });

        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button receiptBtn = new Button("Receipt");
            private final Button returnBtn = new Button("Return");
            private final HBox box = new HBox(6, receiptBtn, returnBtn);

            {
                receiptBtn.getStyleClass().addAll("ghost-button", "table-btn");
                returnBtn.getStyleClass().addAll("ghost-button", "table-btn");
                receiptBtn.setOnAction(e -> {
                    RentalRow row = rowAtIndex();
                    if (row != null) openReceipt(row.id);
                });
                returnBtn.setOnAction(e -> {
                    RentalRow row = rowAtIndex();
                    if (row != null) confirmReturn(row);
                });
            }

            private RentalRow rowAtIndex() {
                int i = getIndex();
                return (i >= 0 && i < getTableView().getItems().size()) ? getTableView().getItems().get(i) : null;
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                RentalRow row = empty ? null : rowAtIndex();
                if (row == null) {
                    setGraphic(null);
                    return;
                }
                // Rentals that are already returned only keep the Receipt button.
                boolean stillOut = !"Returned".equals(row.status);
                returnBtn.setVisible(stillOut);
                returnBtn.setManaged(stillOut);
                setGraphic(box);
            }
        });
    }

    /** Asks for confirmation, then marks the rental Returned and the vehicle Available. */
    private void confirmReturn(RentalRow row) {
        LocalDate today = LocalDate.now();
        String agreed = row.returnDate.format(TABLE_DATE);
        String timing;
        if (today.isBefore(row.returnDate)) {
            timing = "Early return (agreed return date: " + agreed + ").";
        } else if (today.isAfter(row.returnDate)) {
            timing = "Late return (agreed return date: " + agreed + ").";
        } else {
            timing = "Returned on the agreed date (" + agreed + ").";
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                row.vehicle + "\n" + timing + "\n\n"
                        + "The rental will be marked as Returned and the vehicle will be available again. "
                        + "The amount charged stays \u20b1" + String.format("%,.0f", row.total) + ".",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Mark as returned");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) {
            return;
        }

        try {
            DatabaseConnection.markReturned(row.id);
        } catch (SQLException e) {
            e.printStackTrace();
            Alert error = new Alert(Alert.AlertType.ERROR, "Could not mark this rental as returned. Try again.");
            error.setHeaderText(null);
            error.showAndWait();
        }
        loadBoard(searchField.getText());
    }

    private TableCell<RentalRow, LocalDate> dateCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(LocalDate value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : value.format(TABLE_DATE));
            }
        };
    }

    /** Reloads the table from the database, applying the search box and the selected filter pill. */
    private void loadBoard(String search) {
        String sql =
                "SELECT r.rental_id, c.full_name, c.contact_no, v.model, v.plate_no, " +
                        "r.rent_date, r.return_date, r.total_amount, r.status " +
                        "FROM rentals r " +
                        "JOIN customers c ON r.customer_id = c.customer_id " +
                        "JOIN vehicles v ON r.vehicle_id = v.vehicle_id " +
                        "WHERE (c.full_name LIKE ? OR v.model LIKE ? OR v.plate_no LIKE ?) " +
                        "ORDER BY r.rent_date DESC, r.rental_id DESC";

        List<RentalRow> all = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String like = "%" + (search == null ? "" : search.trim()) + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);
            stmt.setString(3, like);

            LocalDate today = LocalDate.now();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String dbStatus = rs.getString("status");
                    LocalDate returnDate = rs.getDate("return_date").toLocalDate();

                    String status;
                    if ("Returned".equals(dbStatus)) {
                        status = "Returned";
                    } else if ("Overdue".equals(dbStatus) || returnDate.isBefore(today)) {
                        status = "Overdue";
                    } else if (returnDate.isEqual(today)) {
                        status = "Due today";
                    } else {
                        status = "Active";
                    }

                    String contact = rs.getString("contact_no");
                    all.add(new RentalRow(
                            rs.getInt("rental_id"),
                            rs.getString("full_name"),
                            contact == null ? "" : contact,
                            rs.getString("model") + " \u00b7 " + rs.getString("plate_no"),
                            rs.getDate("rent_date").toLocalDate(),
                            returnDate,
                            rs.getDouble("total_amount"),
                            status
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            countLabel.setText("Could not load rentals: " + e.getMessage());
            return;
        }

        int active = 0, due = 0, returned = 0;
        ObservableList<RentalRow> visible = FXCollections.observableArrayList();
        for (RentalRow row : all) {
            switch (row.status) {
                case "Returned" -> returned++;
                case "Overdue", "Due today" -> due++;
                default -> active++;
            }
            if (matchesFilter(row)) {
                visible.add(row);
            }
        }

        rentalTable.setItems(visible);
        filterAllBtn.setText("All (" + all.size() + ")");
        filterActiveBtn.setText("Active (" + active + ")");
        filterDueBtn.setText("Due or overdue (" + due + ")");
        filterReturnedBtn.setText("Returned (" + returned + ")");
        countLabel.setText((active + due) + " cars currently out");
    }

    private boolean matchesFilter(RentalRow row) {
        return switch (currentFilter) {
            case "Active" -> row.status.equals("Active");
            case "Due" -> row.status.equals("Overdue") || row.status.equals("Due today");
            case "Returned" -> row.status.equals("Returned");
            default -> true;
        };
    }

    // ---- Filter pills ----

    @FXML private void onFilterAll() { setFilter("All", filterAllBtn); }
    @FXML private void onFilterActive() { setFilter("Active", filterActiveBtn); }
    @FXML private void onFilterDue() { setFilter("Due", filterDueBtn); }
    @FXML private void onFilterReturned() { setFilter("Returned", filterReturnedBtn); }

    private void setFilter(String filter, Button activeBtn) {
        currentFilter = filter;
        filterAllBtn.getStyleClass().setAll("pill");
        filterActiveBtn.getStyleClass().setAll("pill");
        filterDueBtn.getStyleClass().setAll("pill");
        filterReturnedBtn.getStyleClass().setAll("pill");
        activeBtn.getStyleClass().setAll("pill-active");
        loadBoard(searchField.getText());
    }

    // ---- New rental form ----

    private void updateTotal() {
        VehicleOption vehicle = vehicleCombo.getValue();
        LocalDate rent = rentDatePicker.getValue();
        LocalDate ret = returnDatePicker.getValue();

        if (vehicle == null || rent == null || ret == null || !ret.isAfter(rent)) {
            totalBreakdownLabel.setText("Select a vehicle and valid dates");
            totalAmountLabel.setText("\u20b10.00");
            return;
        }

        long days = ChronoUnit.DAYS.between(rent, ret);
        double total = days * vehicle.rate;
        totalBreakdownLabel.setText(days + (days == 1 ? " day" : " days") + " \u00d7 \u20b1" + String.format("%,.0f", vehicle.rate));
        totalAmountLabel.setText("\u20b1" + String.format("%,.2f", total));
    }

    @FXML
    private void onNewRentalClick() {
        customerNameField.clear();
        contactField.clear();
        licenseField.clear();
        rentDatePicker.setValue(LocalDate.now());
        returnDatePicker.setValue(LocalDate.now().plusDays(1));
        paymentCombo.getSelectionModel().selectFirst();
        formMessageLabel.setText("");
        loadAvailableVehicles();   // after clearing the message, so "no vehicles" can show
        newRentalOverlay.setVisible(true);
        newRentalOverlay.setManaged(true);
    }

    @FXML
    private void onCancelNewRental() {
        newRentalOverlay.setVisible(false);
        newRentalOverlay.setManaged(false);
    }

    private void loadAvailableVehicles() {
        ObservableList<VehicleOption> options = FXCollections.observableArrayList();
        String sql = "SELECT vehicle_id, model, plate_no, rate_per_day FROM vehicles " +
                "WHERE status='Available' ORDER BY model ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                options.add(new VehicleOption(
                        rs.getInt("vehicle_id"),
                        rs.getString("model"),
                        rs.getString("plate_no"),
                        rs.getDouble("rate_per_day")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            formMessageLabel.setText("Could not load vehicles: " + e.getMessage());
        }
        vehicleCombo.setItems(options);
        vehicleCombo.getSelectionModel().clearSelection();
        if (options.isEmpty() && formMessageLabel.getText().isEmpty()) {
            formMessageLabel.setText("No vehicles are available right now.");
        }
        updateTotal();
    }

    @FXML
    private void onConfirmRentalClick() {
        String name = customerNameField.getText().trim();
        String contact = contactField.getText().trim();
        String license = licenseField.getText().trim();
        VehicleOption vehicle = vehicleCombo.getValue();
        LocalDate rent = rentDatePicker.getValue();
        LocalDate ret = returnDatePicker.getValue();
        String payment = paymentCombo.getValue();

        if (name.isEmpty() || vehicle == null || rent == null || ret == null || !ret.isAfter(rent)) {
            formMessageLabel.setText("Fill in the customer name, vehicle, and valid dates.");
            return;
        }

        long days = ChronoUnit.DAYS.between(rent, ret);
        double total = days * vehicle.rate;
        int rentalId;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Claim the vehicle first. If someone else already rented it, stop here.
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE vehicles SET status='Rented' WHERE vehicle_id=? AND status='Available'")) {
                    stmt.setInt(1, vehicle.id);
                    if (stmt.executeUpdate() == 0) {
                        conn.rollback();
                        formMessageLabel.setText("That vehicle was just rented out. Pick another one.");
                        loadAvailableVehicles();
                        return;
                    }
                }

                // 2. Customer
                int customerId;
                String customerSql = "INSERT INTO customers (full_name, contact_no, license_no) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(customerSql, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, name);
                    stmt.setString(2, contact);
                    stmt.setString(3, license);
                    stmt.executeUpdate();
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        keys.next();
                        customerId = keys.getInt(1);
                    }
                }

                // 3. Rental (id read straight from the insert)
                String rentalSql = "INSERT INTO rentals (customer_id, vehicle_id, rent_date, return_date, " +
                        "total_amount, payment_method, created_by, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'Active')";
                try (PreparedStatement stmt = conn.prepareStatement(rentalSql, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, customerId);
                    stmt.setInt(2, vehicle.id);
                    stmt.setDate(3, Date.valueOf(rent));
                    stmt.setDate(4, Date.valueOf(ret));
                    stmt.setDouble(5, total);
                    stmt.setString(6, payment);
                    stmt.setString(7, Session.username);
                    stmt.executeUpdate();
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        keys.next();
                        rentalId = keys.getInt(1);
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();   // nothing is half-saved
                throw e;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            // Show the real reason so a schema problem is obvious instead of a generic message.
            formMessageLabel.setText("Could not save this rental: " + e.getMessage());
            return;
        }

        onCancelNewRental();
        openReceipt(rentalId);
    }

    private void openReceipt(int rentalId) {
        try {
            ReceiptController controller = Navigator.show(
                    (Stage) countLabel.getScene().getWindow(),
                    "receipt-view.fxml", "Car Rental System - Receipt");
            controller.setRentalId(rentalId);
        } catch (IOException e) {
            e.printStackTrace();
            Alert error = new Alert(Alert.AlertType.ERROR,
                    "The rental was saved, but the receipt screen could not be opened:\n" + e.getMessage());
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
        switchScene("vehicles-view.fxml", "Car Rental System - Vehicles");
    }

    @FXML
    private void onRentalsClick() {
        // Already here
    }

    @FXML
    private void onAccountsClick() {
        if (!Session.isAdmin()) return;
        switchScene("accounts-view.fxml", "Car Rental System - Accounts");
    }

    @FXML
    private void onLogoutClick() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
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

    /** Backs one row of the rentals table. */
    public static class RentalRow {
        final int id;
        final String customer;
        final String contact;
        final String vehicle;
        final LocalDate rentDate;
        final LocalDate returnDate;
        final double total;
        final String status;

        RentalRow(int id, String customer, String contact, String vehicle,
                  LocalDate rentDate, LocalDate returnDate, double total, String status) {
            this.id = id;
            this.customer = customer;
            this.contact = contact;
            this.vehicle = vehicle;
            this.rentDate = rentDate;
            this.returnDate = returnDate;
            this.total = total;
            this.status = status;
        }
    }

    /** Wraps a vehicle so the ComboBox can show a friendly label but keep the id and rate. */
    private static class VehicleOption {
        final int id;
        final String model;
        final String plate;
        final double rate;

        VehicleOption(int id, String model, String plate, double rate) {
            this.id = id;
            this.model = model;
            this.plate = plate;
            this.rate = rate;
        }

        @Override
        public String toString() {
            return model + " \u00b7 " + plate + " \u00b7 \u20b1" + String.format("%,.0f", rate) + "/day";
        }
    }
}