package com.carrentalsystem.carsystem;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DashboardController {

    @FXML private Label loggedInAsLabel;
    @FXML private Label totalVehiclesLabel;
    @FXML private Label availableLabel;
    @FXML private Label rentedLabel;
    @FXML private Label overdueLabel;

    @FXML private TableView<DueRow> dueTable;
    @FXML private TableColumn<DueRow, String> dueCustomerCol;
    @FXML private TableColumn<DueRow, String> dueVehicleCol;
    @FXML private TableColumn<DueRow, String> dueDateCol;
    @FXML private TableColumn<DueRow, String> dueStatusCol;

    @FXML
    public void initialize() {
        dueCustomerCol.setCellValueFactory(data -> data.getValue().customerName);
        dueVehicleCol.setCellValueFactory(data -> data.getValue().vehicleModel);
        dueDateCol.setCellValueFactory(data -> data.getValue().returnDate);
        dueStatusCol.setCellValueFactory(data -> data.getValue().status);

        loadStats();
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

    private void loadDueList() {
        ObservableList<DueRow> rows = FXCollections.observableArrayList();

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

            while (rs.next()) {
                rows.add(new DueRow(
                        rs.getString("full_name"),
                        rs.getString("model"),
                        rs.getDate("return_date").toString(),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        dueTable.setItems(rows);
    }

    @FXML private void onDashboardClick() { /* already here */ }
    @FXML private void onVehiclesClick() { /* TODO: switch to vehicles-view.fxml */ }
    @FXML private void onCustomersClick() { /* TODO: switch to customers-view.fxml */ }
    @FXML private void onRentalsClick() { /* TODO: switch to rentals-view.fxml */ }
    @FXML private void onNewRentalClick() { /* TODO: open new-rental popup */ }
    @FXML private void onLogoutClick() { /* TODO: go back to login-view.fxml */ }

    public static class DueRow {
        final SimpleStringProperty customerName;
        final SimpleStringProperty vehicleModel;
        final SimpleStringProperty returnDate;
        final SimpleStringProperty status;

        DueRow(String customerName, String vehicleModel, String returnDate, String status) {
            this.customerName = new SimpleStringProperty(customerName);
            this.vehicleModel = new SimpleStringProperty(vehicleModel);
            this.returnDate = new SimpleStringProperty(returnDate);
            this.status = new SimpleStringProperty(status);
        }
    }
}