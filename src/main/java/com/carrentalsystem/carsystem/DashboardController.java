package com.carrentalsystem.carsystem;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class DashboardController {

    @FXML private Button vehiclesBtn;
    @FXML private Button accountsBtn;
    @FXML private Label dateLabel;
    @FXML private Label totalVehiclesLabel;
    @FXML private Label availableLabel;
    @FXML private Label rentedLabel;
    @FXML private Label overdueLabel;
    @FXML private Label revenueTitleLabel;
    @FXML private Label revenueMonthLabel;
    @FXML private Label revenuePeriodLabel;
    @FXML private Label revenueTodayLabel;
    @FXML private Label revenueWeekLabel;
    @FXML private Label rentalsCountLabel;
    @FXML private VBox dueListContainer;

    @FXML
    public void initialize() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
        String who = Session.username == null ? "Admin" : Session.username;
        dateLabel.setText(LocalDate.now().format(fmt) + " \u00b7 Logged in as " + who);

        applyRoleRestrictions();
        DatabaseConnection.refreshOverdue();   // so the Overdue numbers are right even if Rentals wasn't opened
        loadStats();
        loadRevenueOrSales();
        loadDueList();
    }

    private void applyRoleRestrictions() {
        boolean admin = Session.isAdmin();
        vehiclesBtn.setVisible(admin);
        vehiclesBtn.setManaged(admin);
        accountsBtn.setVisible(admin);
        accountsBtn.setManaged(admin);
    }

    private void loadStats() {
        String sql =
                "SELECT " +
                        "(SELECT COUNT(*) FROM vehicles) AS total, " +
                        "(SELECT COUNT(*) FROM vehicles WHERE status='Available') AS available, " +
                        "(SELECT COUNT(*) FROM vehicles WHERE status IN ('Rented','Overdue')) AS rented, " +
                        "(SELECT COUNT(*) FROM rentals WHERE status='Overdue') AS overdue";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                totalVehiclesLabel.setText(String.valueOf(rs.getInt("total")));
                availableLabel.setText(String.valueOf(rs.getInt("available")));
                rentedLabel.setText(String.valueOf(rs.getInt("rented")));
                overdueLabel.setText(String.valueOf(rs.getInt("overdue")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            totalVehiclesLabel.setText("--");
        }
    }

    /**
     * Admins see the company's total revenue. Staff see only the sales
     * they personally made (rentals where created_by = their username).
     */
    private void loadRevenueOrSales() {
        boolean admin = Session.isAdmin();
        revenueTitleLabel.setText(admin ? "Revenue" : "Your sales");

        String staffFilter = admin ? "" : "AND created_by = ?";

        String sql =
                "SELECT " +
                        "(SELECT IFNULL(SUM(total_amount),0) FROM rentals WHERE rent_date = CURDATE() " + staffFilter + ") AS today, " +
                        "(SELECT IFNULL(SUM(total_amount),0) FROM rentals WHERE rent_date >= CURDATE() - INTERVAL 6 DAY " + staffFilter + ") AS week, " +
                        "(SELECT IFNULL(SUM(total_amount),0) FROM rentals WHERE MONTH(rent_date)=MONTH(CURDATE()) AND YEAR(rent_date)=YEAR(CURDATE()) " + staffFilter + ") AS month, " +
                        "(SELECT COUNT(*) FROM rentals WHERE MONTH(rent_date)=MONTH(CURDATE()) AND YEAR(rent_date)=YEAR(CURDATE()) " + staffFilter + ") AS rentalCount";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (!admin) {
                for (int i = 1; i <= 4; i++) {
                    stmt.setString(i, Session.username);
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    revenueTodayLabel.setText("Today \u20b1" + String.format("%,.0f", rs.getDouble("today")));
                    revenueWeekLabel.setText("This week \u20b1" + String.format("%,.0f", rs.getDouble("week")));
                    revenueMonthLabel.setText("\u20b1" + String.format("%,.0f", rs.getDouble("month")));
                    revenuePeriodLabel.setText("this month");
                    int count = rs.getInt("rentalCount");
                    rentalsCountLabel.setText((admin ? "Rentals this month: " : "Cars you've rented out this month: ") + count);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            revenueMonthLabel.setText("--");
        }
    }

    private void loadDueList() {
        dueListContainer.getChildren().clear();

        boolean admin = Session.isAdmin();
        String staffFilter = admin ? "" : "AND r.created_by = ?";

        String sql =
                "SELECT c.full_name, v.model, r.return_date, r.status " +
                        "FROM rentals r " +
                        "JOIN customers c ON r.customer_id = c.customer_id " +
                        "JOIN vehicles v ON r.vehicle_id = v.vehicle_id " +
                        "WHERE r.status IN ('Active','Overdue') " +
                        "AND (r.status = 'Overdue' OR r.return_date = CURDATE()) " + staffFilter + " " +
                        "ORDER BY r.return_date ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (!admin) {
                stmt.setString(1, Session.username);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    boolean overdue = "Overdue".equals(rs.getString("status"));
                    dueListContainer.getChildren().add(buildDueRow(
                            rs.getString("full_name"),
                            rs.getString("model"),
                            rs.getDate("return_date").toString(),
                            overdue));
                }
                if (!any) {
                    Label empty = new Label("Nothing due today. Nice.");
                    empty.getStyleClass().add("page-subtitle");
                    dueListContainer.getChildren().add(empty);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private HBox buildDueRow(String customerName, String vehicleModel, String returnDate, boolean overdue) {
        Label name = new Label(customerName);
        name.getStyleClass().add("due-name");

        Label detail = new Label(vehicleModel + " \u00b7 " + returnDate);
        detail.getStyleClass().add("due-detail");

        VBox info = new VBox(name, detail);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label badge = new Label(overdue ? "Overdue" : "Due today");
        badge.getStyleClass().add(overdue ? "due-badge-overdue" : "due-badge-today");

        HBox row = new HBox(10, info, badge);
        row.getStyleClass().add("due-card");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    @FXML
    private void onAccountsClick() {
        if (!Session.isAdmin()) return;
        switchScene("accounts-view.fxml", "Car Rental System - Accounts");
    }

    @FXML
    private void onDashboardClick() {
        // Already here
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
    private void onNewRentalClick() {
        switchScene("rentals-view.fxml", "Car Rental System - Rentals");
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
            goToLogin();
        }
    }

    private void switchScene(String fxmlFile, String title) {
        try {
            Navigator.show((Stage) dateLabel.getScene().getWindow(), fxmlFile, title);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void goToLogin() {
        try {
            Navigator.showLogin((Stage) dateLabel.getScene().getWindow());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}