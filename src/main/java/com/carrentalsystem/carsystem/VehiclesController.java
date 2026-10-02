package com.carrentalsystem.carsystem;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class VehiclesController {

    @FXML private Button vehiclesBtn;
    @FXML private Button accountsBtn;
    @FXML private StackPane addVehicleOverlay;
    @FXML private VBox vehicleListContainer;
    @FXML private TextField searchField;
    @FXML private Label countLabel;
    @FXML private Label formMessageLabel;

    @FXML private TextField plateField;
    @FXML private TextField modelField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private ComboBox<Integer> seatsCombo;
    @FXML private TextField rateField;
    @FXML private ComboBox<String> statusCombo;

    @FXML
    public void initialize() {
        if (!Session.isAdmin()) {
            // Staff should never land here, but guard it anyway in case the screen is reached directly.
            switchScene("dashboard-view.fxml", "Car Rental System - Dashboard");
            return;
        }
        accountsBtn.setVisible(true);
        accountsBtn.setManaged(true);

        typeCombo.setItems(FXCollections.observableArrayList("Sedan", "MPV", "Pickup", "Van"));
        seatsCombo.setItems(FXCollections.observableArrayList(4, 5, 7, 12, 15));
        statusCombo.setItems(FXCollections.observableArrayList("Available", "Rented", "Maintenance"));
        statusCombo.getSelectionModel().selectFirst();

        searchField.textProperty().addListener((obs, oldVal, newVal) -> loadVehicles(newVal));

        loadVehicles("");
    }

    private void loadVehicles(String search) {
        vehicleListContainer.getChildren().clear();

        String sql = "SELECT plate_no, model, type, rate_per_day, status FROM vehicles " +
                "WHERE plate_no LIKE ? OR model LIKE ? ORDER BY model ASC";

        int count = 0;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String like = "%" + (search == null ? "" : search) + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    count++;
                    vehicleListContainer.getChildren().add(buildVehicleRow(
                            rs.getString("plate_no"),
                            rs.getString("model"),
                            rs.getString("type"),
                            rs.getDouble("rate_per_day"),
                            rs.getString("status")
                    ));
                }
            }

            if (count == 0) {
                Label empty = new Label("No vehicles found.");
                empty.getStyleClass().add("page-subtitle");
                vehicleListContainer.getChildren().add(empty);
            }
            countLabel.setText(count + " in your fleet");

        } catch (SQLException e) {
            e.printStackTrace();
            countLabel.setText("Could not load vehicles: " + e.getMessage());
        }
    }

    private HBox buildVehicleRow(String plate, String model, String type, double rate, String status) {
        Label modelLabel = new Label(model);
        modelLabel.getStyleClass().add("due-name");

        Label plateLabel = new Label(plate + " \u00b7 " + type);
        plateLabel.getStyleClass().add("due-detail");

        VBox info = new VBox(modelLabel, plateLabel);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label rateLabel = new Label("\u20b1" + String.format("%,.0f", rate) + "/day");
        rateLabel.getStyleClass().add("due-name");

        Label statusBadge = new Label(status);
        switch (status) {
            case "Available" -> statusBadge.getStyleClass().add("badge-green");
            case "Rented" -> statusBadge.getStyleClass().add("badge-blue");
            default -> statusBadge.getStyleClass().add("badge-gray");
        }

        HBox row = new HBox(14, info, rateLabel, statusBadge);
        row.getStyleClass().add("due-card");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    @FXML
    private void onAddVehicleClick() {
        formMessageLabel.setText("");
        addVehicleOverlay.setVisible(true);
        addVehicleOverlay.setManaged(true);
    }

    @FXML
    private void onCancelAddVehicle() {
        plateField.clear();
        modelField.clear();
        rateField.clear();
        typeCombo.getSelectionModel().clearSelection();
        seatsCombo.getSelectionModel().clearSelection();
        statusCombo.getSelectionModel().selectFirst();
        formMessageLabel.setText("");
        addVehicleOverlay.setVisible(false);
        addVehicleOverlay.setManaged(false);
    }

    @FXML
    private void onSaveVehicleClick() {
        String plate = plateField.getText().trim();
        String model = modelField.getText().trim();
        String type = typeCombo.getValue();
        Integer seats = seatsCombo.getValue();
        String rateText = rateField.getText().trim();
        String status = statusCombo.getValue();

        if (plate.isEmpty() || model.isEmpty() || type == null || seats == null || rateText.isEmpty()) {
            formMessageLabel.setText("Please fill in every field.");
            return;
        }

        double rate;
        try {
            rate = Double.parseDouble(rateText);
        } catch (NumberFormatException e) {
            formMessageLabel.setText("Rate must be a number.");
            return;
        }

        String sql = "INSERT INTO vehicles (plate_no, model, type, rate_per_day, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, plate);
            stmt.setString(2, model);
            stmt.setString(3, type);
            stmt.setDouble(4, rate);
            stmt.setString(5, status);
            stmt.executeUpdate();

            onCancelAddVehicle();       // clears fields and hides the popup
            loadVehicles(searchField.getText());

        } catch (SQLException e) {
            e.printStackTrace();
            formMessageLabel.setText("That plate number may already exist.");
        }
    }

    // ---- Navigation ----

    @FXML
    private void onDashboardClick() {
        switchScene("dashboard-view.fxml", "Car Rental System - Dashboard");
    }

    @FXML
    private void onVehiclesClick() {
        // Already here
    }

    @FXML
    private void onAccountsClick() {
        if (!Session.isAdmin()) return;
        switchScene("accounts-view.fxml", "Car Rental System - Accounts");
    }

    @FXML
    private void onRentalsClick() {
        switchScene("rentals-view.fxml", "Car Rental System - Rentals");
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
}