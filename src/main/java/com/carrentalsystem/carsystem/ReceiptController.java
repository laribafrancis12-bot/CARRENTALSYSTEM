package com.carrentalsystem.carsystem;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ReceiptController {

    @FXML private javafx.scene.control.Button vehiclesBtn;
    @FXML private javafx.scene.control.Button accountsBtn;
    @FXML private Label receiptNoLabel;
    @FXML private Label customerLabel;
    @FXML private Label contactLabel;
    @FXML private Label vehicleLabel;
    @FXML private Label rentedOnLabel;
    @FXML private Label returnByLabel;
    @FXML private Label rateLabel;
    @FXML private Label paymentLabel;
    @FXML private Label totalPaidLabel;
    @FXML private Label statusMessageLabel;

    @FXML
    public void initialize() {
        boolean admin = Session.isAdmin();
        vehiclesBtn.setVisible(admin);
        vehiclesBtn.setManaged(admin);
        accountsBtn.setVisible(admin);
        accountsBtn.setManaged(admin);
    }

    /** Called right after this screen is loaded, before it's shown. */
    public void setRentalId(int rentalId) {
        loadReceipt(rentalId);
    }

    private void loadReceipt(int rentalId) {
        String sql =
                "SELECT r.rental_id, c.full_name, c.contact_no, v.model, v.plate_no, v.rate_per_day, " +
                        "r.rent_date, r.return_date, r.total_amount, r.payment_method " +
                        "FROM rentals r " +
                        "JOIN customers c ON r.customer_id = c.customer_id " +
                        "JOIN vehicles v ON r.vehicle_id = v.vehicle_id " +
                        "WHERE r.rental_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, rentalId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    LocalDate rentDate = rs.getDate("rent_date").toLocalDate();
                    LocalDate returnDate = rs.getDate("return_date").toLocalDate();
                    long days = ChronoUnit.DAYS.between(rentDate, returnDate);

                    receiptNoLabel.setText("Official rental receipt \u00b7 R-" + rentalId);
                    customerLabel.setText(rs.getString("full_name"));
                    contactLabel.setText(rs.getString("contact_no"));
                    vehicleLabel.setText(rs.getString("model") + " \u00b7 " + rs.getString("plate_no"));
                    rentedOnLabel.setText(rentDate.toString());
                    returnByLabel.setText(returnDate.toString());
                    rateLabel.setText("\u20b1" + String.format("%,.0f", rs.getDouble("rate_per_day"))
                            + " \u00d7 " + days + " days");
                    paymentLabel.setText(rs.getString("payment_method"));
                    totalPaidLabel.setText("\u20b1" + String.format("%,.2f", rs.getDouble("total_amount")));
                } else {
                    statusMessageLabel.setText("Receipt not found.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            statusMessageLabel.setText("Could not load this receipt.");
        }
    }

    @FXML
    private void onSavePdfClick() {
        // TODO: export this receipt to a PDF file
        statusMessageLabel.setText("Save as PDF is not wired up yet.");
    }

    @FXML
    private void onPrintClick() {
        // TODO: send this receipt to a printer
        statusMessageLabel.setText("Print is not wired up yet.");
    }

    @FXML
    private void onBackClick() {
        onRentalsClick();
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
        switchScene("rentals-view.fxml", "Car Rental System - Rentals");
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
            Stage stage = (Stage) customerLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxmlFile));
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            stage.setTitle(title);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}