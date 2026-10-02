package com.carrentalsystem.carsystem;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public class DashboardController {

    @FXML private javafx.scene.control.Button vehiclesBtn;
    @FXML private javafx.scene.control.Button accountsBtn;
    @FXML private Label dateLabel;
    @FXML private Label totalVehiclesLabel;
    @FXML private Label availableLabel;
    @FXML private Label rentedLabel;
    @FXML private Label overdueLabel;
    @FXML private Label revenueMonthLabel;
    @FXML private Label revenueTodayLabel;
    @FXML private Label revenueWeekLabel;
    @FXML private BarChart<String, Number> revenueChart;
    @FXML private CategoryAxis revenueXAxis;
    @FXML private VBox dueListContainer;

    @FXML
    public void initialize() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
        String who = Session.username == null ? "Admin" : Session.username;
        dateLabel.setText(LocalDate.now().format(fmt) + " \u00b7 Logged in as " + who);

        applyRoleRestrictions();
        loadStats();
        loadRevenue();
        loadDueList();
    }

    private void loadStats() {
        String sql =
                "SELECT " +
                        "(SELECT COUNT(*) FROM vehicles) AS total, " +
                        "(SELECT COUNT(*) FROM vehicles WHERE status='Available') AS available, " +
                        "(SELECT COUNT(*) FROM vehicles WHERE status='Rented') AS rented, " +
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

    private void loadRevenue() {
        String totalsSql =
                "SELECT " +
                        "(SELECT IFNULL(SUM(total_amount),0) FROM rentals WHERE rent_date = CURDATE()) AS today, " +
                        "(SELECT IFNULL(SUM(total_amount),0) FROM rentals WHERE rent_date >= CURDATE() - INTERVAL 6 DAY) AS week, " +
                        "(SELECT IFNULL(SUM(total_amount),0) FROM rentals WHERE MONTH(rent_date)=MONTH(CURDATE()) AND YEAR(rent_date)=YEAR(CURDATE())) AS month";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(totalsSql)) {

            if (rs.next()) {
                revenueTodayLabel.setText("Today \u20b1" + String.format("%,.0f", rs.getDouble("today")));
                revenueWeekLabel.setText("This week \u20b1" + String.format("%,.0f", rs.getDouble("week")));
                revenueMonthLabel.setText("\u20b1" + String.format("%,.0f", rs.getDouble("month")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            revenueMonthLabel.setText("--");
        }

        // 7-day breakdown for the bar chart
        String dailySql =
                "SELECT rent_date, SUM(total_amount) AS total FROM rentals " +
                        "WHERE rent_date >= CURDATE() - INTERVAL 6 DAY " +
                        "GROUP BY rent_date ORDER BY rent_date ASC";

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(dailySql)) {

            while (rs.next()) {
                LocalDate date = rs.getDate("rent_date").toLocalDate();
                String label = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
                series.getData().add(new XYChart.Data<>(label, rs.getDouble("total")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        revenueChart.getData().clear();
        revenueChart.getData().add(series);
    }

    private void loadDueList() {
        dueListContainer.getChildren().clear();

        String sql =
                "SELECT c.full_name, v.model, r.return_date, r.status " +
                        "FROM rentals r " +
                        "JOIN customers c ON r.customer_id = c.customer_id " +
                        "JOIN vehicles v ON r.vehicle_id = v.vehicle_id " +
                        "WHERE r.status IN ('Active','Overdue') " +
                        "AND (r.status = 'Overdue' OR r.return_date = CURDATE()) " +
                        "ORDER BY r.return_date ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

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

    private void applyRoleRestrictions() {
        boolean admin = Session.isAdmin();
        vehiclesBtn.setVisible(admin);
        vehiclesBtn.setManaged(admin);
        accountsBtn.setVisible(admin);
        accountsBtn.setManaged(admin);
    }

    @FXML
    private void onAccountsClick() {
        if (!Session.isAdmin()) return;
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) dateLabel.getScene().getWindow();
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    HelloApplication.class.getResource("accounts-view.fxml"));
            javafx.scene.Scene scene = new javafx.scene.Scene(loader.load());
            stage.setScene(scene);
            stage.setTitle("Car Rental System - Accounts");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onDashboardClick() {
        // Already here
    }

    @FXML
    private void onVehiclesClick() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) dateLabel.getScene().getWindow();
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    HelloApplication.class.getResource("vehicles-view.fxml"));
            javafx.scene.Scene scene = new javafx.scene.Scene(loader.load());
            stage.setScene(scene);
            stage.setTitle("Car Rental System - Vehicles");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onRentalsClick() {
        goToRentals();
    }

    @FXML
    private void onNewRentalClick() {
        goToRentals();
    }

    private void goToRentals() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) dateLabel.getScene().getWindow();
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    HelloApplication.class.getResource("rentals-view.fxml"));
            javafx.scene.Scene scene = new javafx.scene.Scene(loader.load());
            stage.setScene(scene);
            stage.setTitle("Car Rental System - Rentals");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onLogoutClick() {
        Session.clear();
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) dateLabel.getScene().getWindow();
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    HelloApplication.class.getResource("login-view.fxml"));
            javafx.scene.Scene scene = new javafx.scene.Scene(loader.load());
            stage.setScene(scene);
            stage.setTitle("Car Rental System - Login");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}