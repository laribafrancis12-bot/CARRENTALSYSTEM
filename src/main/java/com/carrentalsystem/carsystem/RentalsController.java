package com.carrentalsystem.carsystem;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class RentalsController {

    @FXML private javafx.scene.control.Button vehiclesBtn;
    @FXML private javafx.scene.control.Button accountsBtn;
    @FXML private StackPane newRentalOverlay;
    @FXML private TextField searchField;
    @FXML private Label countLabel;
    @FXML private Label activeCountLabel;
    @FXML private Label dueCountLabel;
    @FXML private Label returnedCountLabel;
    @FXML private VBox activeColumn;
    @FXML private VBox dueColumn;
    @FXML private VBox returnedColumn;

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

        paymentCombo.setItems(FXCollections.observableArrayList("Cash", "GCash", "Card"));
        paymentCombo.getSelectionModel().selectFirst();

        rentDatePicker.setValue(LocalDate.now());
        returnDatePicker.setValue(LocalDate.now().plusDays(1));

        rentDatePicker.valueProperty().addListener((o, a, b) -> updateTotal());
        returnDatePicker.valueProperty().addListener((o, a, b) -> updateTotal());
        vehicleCombo.valueProperty().addListener((o, a, b) -> updateTotal());

        loadBoard("");
    }

    private void loadBoard(String search) {
        activeColumn.getChildren().clear();
        dueColumn.getChildren().clear();
        returnedColumn.getChildren().clear();

        String sql =
                "SELECT r.rental_id, c.full_name, c.contact_no, v.model, r.rent_date, r.return_date, " +
                        "r.total_amount, r.status " +
                        "FROM rentals r " +
                        "JOIN customers c ON r.customer_id = c.customer_id " +
                        "JOIN vehicles v ON r.vehicle_id = v.vehicle_id " +
                        "WHERE c.full_name LIKE ? OR v.model LIKE ? " +
                        "ORDER BY r.rent_date DESC";

        int active = 0, due = 0, returned = 0;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String like = "%" + (search == null ? "" : search) + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    LocalDate returnDate = rs.getDate("return_date").toLocalDate();
                    boolean dueToday = returnDate.isEqual(LocalDate.now());

                    HBox card = buildRentalCard(
                            rs.getInt("rental_id"),
                            rs.getString("full_name"),
                            rs.getString("contact_no"),
                            rs.getString("model"),
                            rs.getDate("rent_date").toLocalDate(),
                            returnDate,
                            rs.getDouble("total_amount"),
                            status
                    );

                    if ("Returned".equals(status)) {
                        returnedColumn.getChildren().add(card);
                        returned++;
                    } else if ("Overdue".equals(status) || dueToday) {
                        dueColumn.getChildren().add(card);
                        due++;
                    } else {
                        activeColumn.getChildren().add(card);
                        active++;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        activeCountLabel.setText(String.valueOf(active));
        dueCountLabel.setText(String.valueOf(due));
        returnedCountLabel.setText(String.valueOf(returned));
        countLabel.setText((active + due) + " cars currently out");
    }

    private HBox buildRentalCard(int rentalId, String customerName, String contact, String vehicleModel,
                                 LocalDate rentDate, LocalDate returnDate, double total, String status) {
        Label rentalNo = new Label("#" + rentalId);
        rentalNo.getStyleClass().add("due-detail");

        Label customer = new Label(customerName);
        customer.getStyleClass().add("due-name");

        Label contactLabel = new Label(contact == null ? "" : contact);
        contactLabel.getStyleClass().add("due-detail");

        Label vehicle = new Label(vehicleModel);
        vehicle.getStyleClass().add("due-detail");

        Label dates = new Label(rentDate + " \u2192 " + returnDate);
        dates.getStyleClass().add("due-detail");

        Label totalLabel = new Label("\u20b1" + String.format("%,.0f", total));
        totalLabel.getStyleClass().add("due-name");

        VBox info = new VBox(3, rentalNo, customer, contactLabel, vehicle, dates, totalLabel);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label receiptIcon = new Label("\uD83E\uDDFE"); // receipt emoji
        receiptIcon.getStyleClass().add("receipt-icon");
        receiptIcon.setOnMouseClicked(e -> openReceipt(rentalId));

        HBox card = new HBox(10, info, receiptIcon);
        card.getStyleClass().add("due-card");
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

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
        totalBreakdownLabel.setText(days + " days \u00d7 \u20b1" + String.format("%,.0f", vehicle.rate));
        totalAmountLabel.setText("\u20b1" + String.format("%,.2f", total));
    }

    @FXML
    private void onNewRentalClick() {
        loadAvailableVehicles();
        customerNameField.clear();
        contactField.clear();
        licenseField.clear();
        rentDatePicker.setValue(LocalDate.now());
        returnDatePicker.setValue(LocalDate.now().plusDays(1));
        paymentCombo.getSelectionModel().selectFirst();
        formMessageLabel.setText("");
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
        String sql = "SELECT vehicle_id, model, plate_no, rate_per_day FROM vehicles WHERE status='Available'";

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
        }
        vehicleCombo.setItems(options);
        vehicleCombo.getSelectionModel().clearSelection();
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

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

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

            String rentalSql = "INSERT INTO rentals (customer_id, vehicle_id, rent_date, return_date, " +
                    "total_amount, payment_method, status) VALUES (?, ?, ?, ?, ?, ?, 'Active')";
            try (PreparedStatement stmt = conn.prepareStatement(rentalSql)) {
                stmt.setInt(1, customerId);
                stmt.setInt(2, vehicle.id);
                stmt.setDate(3, Date.valueOf(rent));
                stmt.setDate(4, Date.valueOf(ret));
                stmt.setDouble(5, total);
                stmt.setString(6, payment);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE vehicles SET status='Rented' WHERE vehicle_id=?")) {
                stmt.setInt(1, vehicle.id);
                stmt.executeUpdate();
            }

            conn.commit();

            int rentalId;
            try (PreparedStatement idStmt = conn.prepareStatement("SELECT LAST_INSERT_ID()");
                 ResultSet idRs = idStmt.executeQuery()) {
                idRs.next();
                rentalId = idRs.getInt(1);
            }

            onCancelNewRental();
            openReceipt(rentalId);

        } catch (SQLException e) {
            e.printStackTrace();
            formMessageLabel.setText("Could not save this rental. Try again.");
        }
    }

    private void openReceipt(int rentalId) {
        try {
            Stage stage = (Stage) countLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("receipt-view.fxml"));
            Scene scene = new Scene(loader.load());
            ReceiptController controller = loader.getController();
            controller.setRentalId(rentalId);
            stage.setScene(scene);
            stage.setTitle("Car Rental System - Receipt");
        } catch (IOException e) {
            e.printStackTrace();
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
        Session.clear();
        switchScene("login-view.fxml", "Car Rental System - Login");
    }

    private void switchScene(String fxmlFile, String title) {
        try {
            Stage stage = (Stage) countLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxmlFile));
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            stage.setTitle(title);
        } catch (IOException e) {
            e.printStackTrace();
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