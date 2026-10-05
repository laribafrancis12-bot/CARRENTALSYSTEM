package com.carrentalsystem.carsystem;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class ReceiptController {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    @FXML private Button vehiclesBtn;
    @FXML private Button accountsBtn;

    /** The white "paper" that gets printed / saved as PDF. */
    @FXML private VBox receiptPaper;

    @FXML private Label receiptNoLabel;
    @FXML private Label issueDateLabel;

    @FXML private Label customerLabel;
    @FXML private Label licenseLabel;
    @FXML private Label contactLabel;

    @FXML private Label vehicleLabel;
    @FXML private Label plateLabel;
    @FXML private Label vehicleTypeLabel;

    @FXML private Label rentedOnLabel;
    @FXML private Label returnByLabel;
    @FXML private Label durationLabel;
    @FXML private Label durationHoursLabel;
    @FXML private Label rateLabel;

    @FXML private Label paymentLabel;
    @FXML private Label paymentNoteLabel;

    @FXML private Label subtotalLabel;
    @FXML private Label serviceFeeLabel;
    @FXML private Label discountLabel;
    @FXML private Label totalPaidLabel;

    @FXML private Label statusMessageLabel;

    /** Used for the default PDF file name. */
    private String receiptNo = "receipt";

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
                "SELECT r.rental_id, c.full_name, c.contact_no, c.license_no, " +
                        "v.model, v.plate_no, v.type, v.rate_per_day, " +
                        "r.rent_date, r.return_date, r.total_amount, r.payment_method " +
                        "FROM rentals r " +
                        "JOIN customers c ON r.customer_id = c.customer_id " +
                        "JOIN vehicles v ON r.vehicle_id = v.vehicle_id " +
                        "WHERE r.rental_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, rentalId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    statusMessageLabel.setText("Receipt not found.");
                    return;
                }

                LocalDate rentDate = rs.getDate("rent_date").toLocalDate();
                LocalDate returnDate = rs.getDate("return_date").toLocalDate();
                long days = ChronoUnit.DAYS.between(rentDate, returnDate);
                double total = rs.getDouble("total_amount");

                // The system doesn't charge a service fee or give discounts yet,
                // so they show as zero and the subtotal equals the total.
                double serviceFee = 0;
                double discount = 0;
                double subtotal = total - serviceFee + discount;

                // Receipt number such as RA-2026-1004-001 (year, month+day, rental id).
                receiptNo = String.format("RA-%d-%02d%02d-%03d",
                        rentDate.getYear(), rentDate.getMonthValue(), rentDate.getDayOfMonth(), rentalId);

                receiptNoLabel.setText(receiptNo);
                issueDateLabel.setText(rentDate.format(LONG_DATE));

                customerLabel.setText(orDash(rs.getString("full_name")));
                licenseLabel.setText(orDash(rs.getString("license_no")));
                contactLabel.setText(orDash(rs.getString("contact_no")));

                vehicleLabel.setText(orDash(rs.getString("model")));
                plateLabel.setText(orDash(rs.getString("plate_no")));
                vehicleTypeLabel.setText(orDash(rs.getString("type")));

                rentedOnLabel.setText(rentDate.format(SHORT_DATE));
                returnByLabel.setText(returnDate.format(SHORT_DATE));
                durationLabel.setText(days + (days == 1 ? " day" : " days"));
                durationHoursLabel.setText((days * 24) + " hours");
                rateLabel.setText(peso(rs.getDouble("rate_per_day"), 0));

                paymentLabel.setText(orDash(rs.getString("payment_method")));
                paymentNoteLabel.setText("Payment received in full on " + rentDate.format(SHORT_DATE)
                        + ". No balance remains.");

                subtotalLabel.setText(peso(subtotal, 0));
                serviceFeeLabel.setText(peso(serviceFee, 0));
                discountLabel.setText(peso(discount, 0));
                totalPaidLabel.setText(peso(total, 0));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            statusMessageLabel.setText("Could not load this receipt: " + e.getMessage());
        }
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "\u2014" : value;
    }

    private static String peso(double amount, int decimals) {
        return "\u20b1" + String.format("%,." + decimals + "f", amount);
    }

    // ---- Save as PDF / Print ----

    @FXML
    private void onSavePdfClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save receipt as PDF");
        chooser.setInitialFileName(receiptNo + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF file", "*.pdf"));

        File file = chooser.showSaveDialog(receiptPaper.getScene().getWindow());
        if (file == null) {
            return;   // cancelled
        }
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getParentFile(), file.getName() + ".pdf");
        }

        try {
            WritableImage image = ReceiptExporter.snapshot(receiptPaper);
            ReceiptExporter.savePdf(image, file);
            statusMessageLabel.setText("Saved: " + file.getAbsolutePath());
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            statusMessageLabel.setText("Could not save the PDF: " + e.getMessage());
        }
    }

    @FXML
    private void onPrintClick() {
        try {
            WritableImage image = ReceiptExporter.snapshot(receiptPaper);
            boolean printed = ReceiptExporter.print(image, receiptPaper.getScene().getWindow());
            statusMessageLabel.setText(printed ? "Sent to the printer." : "");
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            statusMessageLabel.setText(e.getMessage() + " You can use Save as PDF instead.");
        }
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
        goToLogin();
    }

    private void switchScene(String fxmlFile, String title) {
        try {
            Navigator.show((Stage) receiptPaper.getScene().getWindow(), fxmlFile, title);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void goToLogin() {
        try {
            Navigator.showLogin((Stage) receiptPaper.getScene().getWindow());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}