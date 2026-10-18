package com.carrentalsystem.carsystem;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.util.StringConverter;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

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
    @FXML private Label revenueYearLabel;
    @FXML private Label rentalsCountLabel;
    @FXML private VBox dueListContainer;

    @FXML private PieChart fleetChart;
    @FXML private VBox staffChartCard;
    @FXML private BarChart<String, Number> staffChart;
    @FXML private Label staffChartTitleLabel;
    @FXML private BarChart<String, Number> monthRevenueChart;
    @FXML private Label monthChartTitleLabel;
    @FXML private BarChart<String, Number> yearRevenueChart;
    @FXML private Label yearChartTitleLabel;
    @FXML private BarChart<String, Number> carRankChart;
    @FXML private Label carRankTitleLabel;

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
        loadCharts();
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
                        "(SELECT IFNULL(SUM(total_amount),0) FROM rentals WHERE YEAR(rent_date)=YEAR(CURDATE()) " + staffFilter + ") AS yearTotal, " +
                        "(SELECT COUNT(*) FROM rentals WHERE MONTH(rent_date)=MONTH(CURDATE()) AND YEAR(rent_date)=YEAR(CURDATE()) " + staffFilter + ") AS rentalCount";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (!admin) {
                for (int i = 1; i <= 5; i++) {
                    stmt.setString(i, Session.username);
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    revenueTodayLabel.setText("Today \u20b1" + String.format("%,.0f", rs.getDouble("today")));
                    revenueWeekLabel.setText("This week \u20b1" + String.format("%,.0f", rs.getDouble("week")));
                    revenueYearLabel.setText("This year \u20b1" + String.format("%,.0f", rs.getDouble("yearTotal")));
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

    // ---- Charts ----

    /**
     * Admins see the whole company; staff see only the rentals they made themselves.
     * The staff performance chart is for the admin only (it compares the staff members).
     */
    private void loadCharts() {
        boolean admin = Session.isAdmin();
        loadFleetChart();

        staffChartCard.setVisible(admin);
        staffChartCard.setManaged(admin);
        if (admin) {
            loadStaffPerformanceChart();
        }

        loadMonthRevenueChart(admin);
        loadYearRevenueChart(admin);
        loadCarRankChart(admin);
    }

    /** Pie: how many vehicles are in each status. */
    private void loadFleetChart() {
        // Fixed order so each status always gets the same colour (see style.css).
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Available", 0);
        counts.put("Rented", 0);
        counts.put("Overdue", 0);
        counts.put("Maintenance", 0);

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT status, COUNT(*) AS n FROM vehicles GROUP BY status")) {
            while (rs.next()) {
                String status = rs.getString("status");
                if (status != null) {
                    counts.merge(status, rs.getInt("n"), Integer::sum);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        counts.forEach((status, n) -> data.add(new PieChart.Data(status + " (" + n + ")", n)));
        fleetChart.setData(data);
    }

    /**
     * Admin-only: one bar per STAFF member (the admin's own rentals are not counted),
     * showing how much revenue they brought in this month. Each bar's label also shows
     * how many cars they rented. Active staff with no rentals yet get a bar of zero,
     * so the whole team can be compared.
     */
    private void loadStaffPerformanceChart() {
        staffChartTitleLabel.setText("Staff performance - this month");

        String sql = "SELECT u.username, COUNT(r.rental_id) AS n, IFNULL(SUM(r.total_amount),0) AS amt " +
                "FROM users u " +
                "LEFT JOIN rentals r ON r.created_by = u.username " +
                "AND MONTH(r.rent_date) = MONTH(CURDATE()) AND YEAR(r.rent_date) = YEAR(CURDATE()) " +
                "WHERE u.role = 'staff' " +
                "GROUP BY u.username " +
                "HAVING MAX(u.active) = 1 OR COUNT(r.rental_id) > 0 " +
                "ORDER BY amt DESC, u.username ASC";

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean any = false;
            while (rs.next()) {
                any = true;
                String staff = rs.getString("username");
                int count = rs.getInt("n");
                double amount = rs.getDouble("amt");
                String label = staff + "\n(" + count + (count == 1 ? " rental" : " rentals") + ")";
                series.getData().add(new XYChart.Data<>(label, amount));
            }
            if (!any) {
                // There are no staff accounts yet; show an empty, honest chart.
                series.getData().add(new XYChart.Data<>("No staff yet", 0));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        staffChart.getData().setAll(series);
    }

    /** Bars: revenue of each month of the current year (January to December). */
    private void loadMonthRevenueChart(boolean admin) {
        int year = LocalDate.now().getYear();
        monthChartTitleLabel.setText((admin ? "Revenue by month - " : "Your sales by month - ") + year);

        String sql = "SELECT MONTH(rent_date) AS m, IFNULL(SUM(total_amount),0) AS amt FROM rentals " +
                "WHERE YEAR(rent_date) = YEAR(CURDATE()) " + (admin ? "" : "AND created_by = ? ") +
                "GROUP BY MONTH(rent_date)";

        Map<Integer, Double> byMonth = new HashMap<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (!admin) {
                stmt.setString(1, Session.username);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byMonth.put(rs.getInt("m"), rs.getDouble("amt"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int m = 1; m <= 12; m++) {
            series.getData().add(new XYChart.Data<>(
                    Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH), byMonth.getOrDefault(m, 0.0)));
        }
        monthRevenueChart.getData().setAll(series);
    }

    /**
     * Bars: revenue of each year. It starts at the first year that has rentals (but always
     * shows at least the last 5 years, and at most the last 10), so years can be compared.
     */
    private void loadYearRevenueChart(boolean admin) {
        yearChartTitleLabel.setText(admin ? "Revenue by year" : "Your sales by year");

        String sql = "SELECT YEAR(rent_date) AS y, IFNULL(SUM(total_amount),0) AS amt FROM rentals " +
                (admin ? "" : "WHERE created_by = ? ") +
                "GROUP BY YEAR(rent_date)";

        Map<Integer, Double> byYear = new HashMap<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (!admin) {
                stmt.setString(1, Session.username);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byYear.put(rs.getInt("y"), rs.getDouble("amt"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        int thisYear = LocalDate.now().getYear();
        int firstYear = byYear.keySet().stream().min(Integer::compare).orElse(thisYear);
        int from = Math.max(Math.min(firstYear, thisYear - 4), thisYear - 9);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int y = from; y <= thisYear; y++) {
            series.getData().add(new XYChart.Data<>(String.valueOf(y), byYear.getOrDefault(y, 0.0)));
        }
        yearRevenueChart.getData().setAll(series);
    }

    /**
     * Bars: every car ranked by how many times it was rented, the most rented on the left
     * and the cars that were never rented at the end (up to 20 cars).
     */
    private void loadCarRankChart(boolean admin) {
        carRankTitleLabel.setText(admin ? "Most rented cars" : "Cars you rented out most");

        String sql = "SELECT v.model, v.plate_no, COUNT(r.rental_id) AS n FROM vehicles v " +
                "LEFT JOIN rentals r ON r.vehicle_id = v.vehicle_id " + (admin ? "" : "AND r.created_by = ? ") +
                "GROUP BY v.vehicle_id, v.model, v.plate_no " +
                "ORDER BY n DESC, v.model ASC LIMIT 20";

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (!admin) {
                stmt.setString(1, Session.username);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String model = rs.getString("model");
                    String plate = rs.getString("plate_no");
                    String shortModel = model.length() > 16 ? model.substring(0, 15) + "\u2026" : model;
                    // the plate number keeps every label different, even for two cars of the same model
                    series.getData().add(new XYChart.Data<>(shortModel + "\n" + (plate == null ? "" : plate),
                            rs.getInt("n")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        carRankChart.getData().setAll(series);

        // whole numbers only on the side axis (a car can't be rented half a time)
        NumberAxis axis = (NumberAxis) carRankChart.getYAxis();
        axis.setMinorTickCount(0);
        axis.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number value) {
                return value.doubleValue() % 1 == 0 ? String.valueOf(value.intValue()) : "";
            }

            @Override
            public Number fromString(String text) {
                return 0;
            }
        });
    }

    @FXML
    private void onHistoryClick() {
        switchScene("history-view.fxml", "Car Rental System - History");
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
        Alert confirm = Dialogs.create(Alert.AlertType.CONFIRMATION,
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