package com.carrentalsystem.carsystem;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class VehiclesController {

    @FXML private Button vehiclesBtn;
    @FXML private Button accountsBtn;
    @FXML private StackPane addVehicleOverlay;
    @FXML private TextField searchField;
    @FXML private Label countLabel;
    @FXML private Label formMessageLabel;

    @FXML private Button filterAllBtn;
    @FXML private Button filterAvailableBtn;
    @FXML private Button filterRentedBtn;
    @FXML private Button filterOverdueBtn;
    @FXML private Button filterMaintenanceBtn;

    @FXML private TableView<VehicleRow> vehicleTable;
    @FXML private TableColumn<VehicleRow, String> plateCol;
    @FXML private TableColumn<VehicleRow, String> modelCol;
    @FXML private TableColumn<VehicleRow, String> typeCol;
    @FXML private TableColumn<VehicleRow, Double> rateCol;
    @FXML private TableColumn<VehicleRow, String> statusCol;
    @FXML private TableColumn<VehicleRow, Void> actionsCol;

    @FXML private TextField plateField;
    @FXML private TextField modelField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private ComboBox<Integer> seatsCombo;
    @FXML private TextField rateField;
    @FXML private ComboBox<String> statusCombo;

    private String currentFilter = "All";

    @FXML
    public void initialize() {
        if (!Session.isAdmin()) {
            // The scene isn't attached yet during initialize(), so wait until it is.
            Platform.runLater(() -> switchScene("dashboard-view.fxml", "Car Rental System - Dashboard"));
            return;
        }
        accountsBtn.setVisible(true);
        accountsBtn.setManaged(true);

        typeCombo.setItems(FXCollections.observableArrayList(
                "Sedan", "Hatchback", "SUV", "Crossover", "MPV", "Pickup", "Van", "Coupe", "Convertible", "Wagon"));
        seatsCombo.setItems(FXCollections.observableArrayList(2, 4, 5, 7, 12, 15));
        // A new car can only start as Available or in Maintenance. "Rented" is set by the system when a rental is made.
        statusCombo.setItems(FXCollections.observableArrayList("Available", "Maintenance"));
        statusCombo.getSelectionModel().selectFirst();

        plateCol.setCellValueFactory(d -> d.getValue().plate);
        plateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    getStyleClass().remove("plate-text");
                } else {
                    setText(value);
                    if (!getStyleClass().contains("plate-text")) {
                        getStyleClass().add("plate-text");
                    }
                }
            }
        });
        modelCol.setCellValueFactory(d -> d.getValue().model);
        typeCol.setCellValueFactory(d -> d.getValue().type);
        rateCol.setCellValueFactory(d -> d.getValue().rate.asObject());
        rateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : "\u20b1" + String.format("%,.0f", value));
            }
        });
        statusCol.setCellValueFactory(d -> d.getValue().status);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(value);
                switch (value) {
                    case "Available" -> badge.getStyleClass().add("badge-green");
                    case "Rented" -> badge.getStyleClass().add("badge-blue");
                    case "Overdue" -> badge.getStyleClass().add("badge-red");
                    default -> badge.getStyleClass().add("badge-amber");
                }
                setGraphic(badge);
                setText(null);
            }
        });
        actionsCol.setCellFactory(col -> new TableCell<>() {
            // "Rented" is not offered: the system sets it when a rental is created.
            private final ComboBox<String> statusChanger = new ComboBox<>(
                    FXCollections.observableArrayList("Available", "Overdue", "Maintenance"));
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(8, statusChanger, deleteBtn);
            private boolean resetting = false;

            {
                statusChanger.setPromptText("Change status");
                deleteBtn.getStyleClass().add("ghost-button");
                statusChanger.setOnAction(e -> {
                    VehicleRow row = rowAtIndex();
                    String newStatus = statusChanger.getValue();
                    if (resetting || row == null || newStatus == null) return;
                    if (!newStatus.equals(row.status.get())) {
                        changeStatus(row, newStatus);
                    }
                });
                deleteBtn.setOnAction(e -> {
                    VehicleRow row = rowAtIndex();
                    if (row != null) confirmAndDelete(row);
                });
            }

            private VehicleRow rowAtIndex() {
                int i = getIndex();
                return (i >= 0 && i < getTableView().getItems().size()) ? getTableView().getItems().get(i) : null;
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                // Table cells are reused for other rows, so clear the old selection.
                resetting = true;
                statusChanger.setValue(null);
                resetting = false;
                setGraphic(empty ? null : box);
            }
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> loadVehicles());

        DatabaseConnection.refreshOverdue();
        loadVehicles();
    }

    private void loadVehicles() {
        ObservableList<VehicleRow> rows = FXCollections.observableArrayList();

        StringBuilder sql = new StringBuilder(
                "SELECT vehicle_id, plate_no, model, type, rate_per_day, status FROM vehicles " +
                        "WHERE (plate_no LIKE ? OR model LIKE ?) ");
        if (!"All".equals(currentFilter)) {
            sql.append("AND status = ? ");
        }
        sql.append("ORDER BY model ASC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            String like = "%" + searchField.getText() + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);
            if (!"All".equals(currentFilter)) {
                stmt.setString(3, currentFilter);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new VehicleRow(
                            rs.getInt("vehicle_id"),
                            rs.getString("plate_no"),
                            rs.getString("model"),
                            rs.getString("type"),
                            rs.getDouble("rate_per_day"),
                            rs.getString("status")
                    ));
                }
            }
            countLabel.setText(rows.size() + " in your fleet");
        } catch (SQLException e) {
            e.printStackTrace();
            countLabel.setText("Could not load vehicles: " + e.getMessage());
        }

        vehicleTable.setItems(rows);
    }

    /**
     * Changes a vehicle's status. Making a rented or overdue car Available again
     * also closes its open rental as Returned (e.g. the customer brought it back early).
     */
    private void changeStatus(VehicleRow row, String newStatus) {
        boolean wasOut = "Rented".equals(row.status.get()) || "Overdue".equals(row.status.get());
        if ("Available".equals(newStatus) && wasOut) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    row.model.get() + " (" + row.plate.get() + ") is currently "
                            + row.status.get().toLowerCase() + ".\n\n"
                            + "Make it available again? Its open rental will be marked as Returned.",
                    ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.setTitle("Mark as returned");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) {
                loadVehicles();   // puts the dropdown back to normal
                return;
            }
        }
        try {
            DatabaseConnection.setVehicleStatus(row.id, newStatus);
        } catch (SQLException e) {
            e.printStackTrace();
            Alert error = new Alert(Alert.AlertType.ERROR, "Could not change the status. Try again.");
            error.setHeaderText(null);
            error.showAndWait();
        }
        loadVehicles();
    }

    private void confirmAndDelete(VehicleRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete " + row.model.get() + " (" + row.plate.get() + ")?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Delete vehicle");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            String sql = "DELETE FROM vehicles WHERE vehicle_id = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, row.id);
                stmt.executeUpdate();
                loadVehicles();
            } catch (SQLException e) {
                e.printStackTrace();
                Alert error = new Alert(Alert.AlertType.ERROR,
                        "This vehicle may have rental history and can't be deleted.");
                error.setHeaderText(null);
                error.showAndWait();
            }
        }
    }

    // ---- Filter pills ----

    @FXML private void onFilterAll() { setFilter("All", filterAllBtn); }
    @FXML private void onFilterAvailable() { setFilter("Available", filterAvailableBtn); }
    @FXML private void onFilterRented() { setFilter("Rented", filterRentedBtn); }
    @FXML private void onFilterOverdue() { setFilter("Overdue", filterOverdueBtn); }
    @FXML private void onFilterMaintenance() { setFilter("Maintenance", filterMaintenanceBtn); }

    private void setFilter(String filter, Button activeBtn) {
        currentFilter = filter;
        filterAllBtn.getStyleClass().setAll("pill");
        filterAvailableBtn.getStyleClass().setAll("pill");
        filterRentedBtn.getStyleClass().setAll("pill");
        filterOverdueBtn.getStyleClass().setAll("pill");
        filterMaintenanceBtn.getStyleClass().setAll("pill");
        activeBtn.getStyleClass().setAll("pill-active");
        loadVehicles();
    }

    // ---- Add vehicle popup ----

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

            onCancelAddVehicle();
            loadVehicles();

        } catch (SQLException e) {
            e.printStackTrace();
            formMessageLabel.setText("Could not save: " + e.getMessage());
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
            Navigator.show((Stage) countLabel.getScene().getWindow(), fxmlFile, title);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void goToLogin() {
        try {
            Navigator.showLogin((Stage) countLabel.getScene().getWindow());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Backs one row of the vehicles table. */
    public static class VehicleRow {
        final int id;
        final SimpleStringProperty plate;
        final SimpleStringProperty model;
        final SimpleStringProperty type;
        final SimpleDoubleProperty rate;
        final SimpleStringProperty status;

        VehicleRow(int id, String plate, String model, String type, double rate, String status) {
            this.id = id;
            this.plate = new SimpleStringProperty(plate);
            this.model = new SimpleStringProperty(model);
            this.type = new SimpleStringProperty(type);
            this.rate = new SimpleDoubleProperty(rate);
            this.status = new SimpleStringProperty(status);
        }
    }
}