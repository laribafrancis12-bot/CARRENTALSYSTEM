package com.carrentalsystem.carsystem;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
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

    /** How many vehicles the table shows on one page. */
    private static final int PAGE_SIZE = 10;

    @FXML private Button vehiclesBtn;
    @FXML private Button accountsBtn;
    @FXML private TextField searchField;
    @FXML private Label countLabel;

    @FXML private Button filterAllBtn;
    @FXML private Button filterAvailableBtn;
    @FXML private Button filterRentedBtn;
    @FXML private Button filterOverdueBtn;
    @FXML private Button filterMaintenanceBtn;
    @FXML private Button filterArchivedBtn;

    @FXML private TableView<VehicleRow> vehicleTable;
    @FXML private Button pagePrevBtn;
    @FXML private Button pageNextBtn;
    @FXML private Label pageLabel;
    @FXML private Label pageInfoLabel;
    private Pager<VehicleRow> pager;
    @FXML private TableColumn<VehicleRow, String> plateCol;
    @FXML private TableColumn<VehicleRow, String> modelCol;
    @FXML private TableColumn<VehicleRow, String> typeCol;
    @FXML private TableColumn<VehicleRow, String> colorCol;
    @FXML private TableColumn<VehicleRow, Integer> seatsCol;
    @FXML private TableColumn<VehicleRow, Double> rateCol;
    @FXML private TableColumn<VehicleRow, String> statusCol;
    @FXML private TableColumn<VehicleRow, Void> actionsCol;

    // Add vehicle popup
    @FXML private StackPane addVehicleOverlay;
    @FXML private Label formMessageLabel;
    @FXML private TextField plateField;
    @FXML private TextField modelField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private ComboBox<Integer> seatsCombo;
    @FXML private ComboBox<String> colorCombo;
    @FXML private TextField rateField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Label plateErrorLabel;
    @FXML private Label modelErrorLabel;
    @FXML private Label typeErrorLabel;
    @FXML private Label seatsErrorLabel;
    @FXML private Label colorErrorLabel;
    @FXML private Label rateErrorLabel;

    // Edit vehicle popup (rate and color)
    @FXML private StackPane editRateOverlay;
    @FXML private Label editRateVehicleLabel;
    @FXML private TextField newRateField;
    @FXML private ComboBox<String> editColorCombo;
    @FXML private ComboBox<Integer> editSeatsCombo;
    @FXML private Label newRateErrorLabel;
    @FXML private Label editRateMessageLabel;

    /** All, Available, Rented, Overdue, Maintenance or Archived. */
    private String currentFilter = "All";

    /** The vehicle being edited in the popup. */
    private VehicleRow editTarget;

    @FXML
    public void initialize() {
        if (!Session.isAdmin()) {
            // The scene isn't attached yet during initialize(), so wait until it is.
            Platform.runLater(() -> switchScene("dashboard-view.fxml", "Car Rental System - Dashboard"));
            return;
        }
        accountsBtn.setVisible(true);
        accountsBtn.setManaged(true);

        typeCombo.setItems(FXCollections.observableArrayList(VehicleSpecs.TYPES));
        CarColors.setUp(colorCombo);
        CarColors.setUp(editColorCombo);

        // The seats depend on the body type: a sedan has 4-5 seats, a van 10-18, and so on.
        seatsCombo.setDisable(true);
        seatsCombo.setPromptText("Choose a type first");
        typeCombo.valueProperty().addListener((o, a, type) -> {
            seatsCombo.getSelectionModel().clearSelection();
            seatsCombo.setItems(FXCollections.observableArrayList(VehicleSpecs.seatsFor(type)));
            seatsCombo.setDisable(type == null);
            seatsCombo.setPromptText(type == null ? "Choose a type first" : "Select seats");
            if (type != null) FormValidation.clear(typeCombo, typeErrorLabel);
        });
        seatsCombo.valueProperty().addListener((o, a, b) -> {
            if (b != null) FormValidation.clear(seatsCombo, seatsErrorLabel);
        });
        colorCombo.valueProperty().addListener((o, a, b) -> {
            if (b != null) FormValidation.clear(colorCombo, colorErrorLabel);
        });

        // A new car can only start as Available or in Maintenance. "Rented" is set by the system when a rental is made.
        statusCombo.setItems(FXCollections.observableArrayList("Available", "Maintenance"));
        statusCombo.getSelectionModel().selectFirst();

        // Warnings are written in code and appear right under the field that has the problem.
        FormValidation.live(plateField, plateErrorLabel, c -> FormValidation.plate(plateField.getText(), c));
        FormValidation.restrictPlate(plateField, plateErrorLabel);   // only 3 letters + 4 numbers can be typed
        FormValidation.live(modelField, modelErrorLabel, c -> FormValidation.vehicleModel(modelField.getText(), c));
        FormValidation.live(rateField, rateErrorLabel, c -> FormValidation.rate(rateField.getText(), c));
        FormValidation.live(newRateField, newRateErrorLabel, c -> FormValidation.rate(newRateField.getText(), c));

        pager = new Pager<>(vehicleTable, pagePrevBtn, pageNextBtn, pageLabel, pageInfoLabel, PAGE_SIZE);
        setUpTable();
        TableTools.fitHeaders(vehicleTable);    // no column title is cut
        TableTools.lockColumns(vehicleTable);   // columns can't be dragged wider

        searchField.textProperty().addListener((obs, oldVal, newVal) -> loadVehicles());

        DatabaseConnection.refreshOverdue();
        loadVehicles();
    }

    // ------------------------------------------------------------ table

    private void setUpTable() {
        // Each row is tinted with the color of its car (and has a stripe of that color on the left).
        vehicleTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(VehicleRow row, boolean empty) {
                super.updateItem(row, empty);
                setStyle(empty || row == null ? "" : CarColors.rowStyle(row.color));
            }
        });

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

        // The color column shows a round swatch in the car's real color next to its name.
        colorCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().color));
        colorCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else if (value == null || value.isBlank()) {
                    setGraphic(null);
                    setText("\u2014");
                } else {
                    setText(null);
                    setGraphic(CarColors.badge(value));
                }
            }
        });

        seatsCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().seats));
        seatsCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : (value == null ? "\u2014" : String.valueOf(value)));
            }
        });

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

        // Every action is a button: [status change] [Edit] [Archive]  (an archived car only has [Restore]).
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button statusBtn = new Button();
            private final Button editBtn = new Button("Edit");
            private final Button archiveBtn = new Button("Archive");
            private final Button restoreBtn = new Button("Restore");
            private final HBox box = new HBox(8, statusBtn, editBtn, archiveBtn, restoreBtn);

            {
                statusBtn.getStyleClass().addAll("ghost-button", "table-btn");
                editBtn.getStyleClass().addAll("ghost-button", "table-btn");
                archiveBtn.getStyleClass().addAll("ghost-button", "table-btn");
                restoreBtn.getStyleClass().addAll("ghost-button", "table-btn");

                statusBtn.setOnAction(e -> {
                    VehicleRow row = rowAtIndex();
                    String target = row == null ? null : statusTarget(row.status.get());
                    if (target != null) changeStatus(row, target);
                });
                editBtn.setOnAction(e -> {
                    VehicleRow row = rowAtIndex();
                    if (row != null) openEdit(row);
                });
                archiveBtn.setOnAction(e -> {
                    VehicleRow row = rowAtIndex();
                    if (row != null) confirmAndArchive(row);
                });
                restoreBtn.setOnAction(e -> {
                    VehicleRow row = rowAtIndex();
                    if (row != null) restore(row);
                });
            }

            private VehicleRow rowAtIndex() {
                int i = getIndex();
                return (i >= 0 && i < getTableView().getItems().size()) ? getTableView().getItems().get(i) : null;
            }

            private void show(Button button, boolean visible) {
                button.setVisible(visible);
                button.setManaged(visible);
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                VehicleRow row = empty ? null : rowAtIndex();
                if (row == null) {
                    setGraphic(null);
                    return;
                }
                boolean archived = row.archived;
                show(restoreBtn, archived);
                show(editBtn, !archived);
                show(archiveBtn, !archived);

                String target = statusTarget(row.status.get());
                show(statusBtn, !archived && target != null);
                if (target != null) {
                    statusBtn.setText(statusButtonText(row.status.get()));
                }
                setGraphic(box);
            }
        });
    }

    /** The status the status button changes the car to (null = nothing to offer). */
    private static String statusTarget(String current) {
        if (current == null) return null;
        return switch (current) {
            case "Available" -> "Maintenance";
            case "Maintenance" -> "Available";
            case "Rented" -> "Overdue";
            case "Overdue" -> "Available";      // the car was brought back
            default -> null;
        };
    }

    private static String statusButtonText(String current) {
        return switch (current) {
            case "Available" -> "Maintenance";
            case "Rented" -> "Mark overdue";
            default -> "Set available";          // Maintenance, Overdue
        };
    }

    private void loadVehicles() {
        loadVehicles(false);
    }

    /** keepPage = true after an action (like Archive) so the table stays on the same page. */
    private void loadVehicles(boolean keepPage) {
        ObservableList<VehicleRow> rows = FXCollections.observableArrayList();
        boolean archivedView = "Archived".equals(currentFilter);
        boolean statusFilter = !"All".equals(currentFilter) && !archivedView;

        StringBuilder sql = new StringBuilder(
                "SELECT vehicle_id, plate_no, model, type, color, seats, rate_per_day, status, archived FROM vehicles " +
                        "WHERE (plate_no LIKE ? OR model LIKE ?) AND archived = ? ");
        if (statusFilter) {
            sql.append("AND status = ? ");
        }
        sql.append("ORDER BY model ASC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            String like = "%" + searchField.getText() + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);
            stmt.setInt(3, archivedView ? 1 : 0);
            if (statusFilter) {
                stmt.setString(4, currentFilter);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int seats = rs.getInt("seats");
                    boolean noSeats = rs.wasNull();
                    rows.add(new VehicleRow(
                            rs.getInt("vehicle_id"),
                            rs.getString("plate_no"),
                            rs.getString("model"),
                            rs.getString("type"),
                            rs.getString("color"),
                            noSeats ? null : seats,
                            rs.getDouble("rate_per_day"),
                            rs.getString("status"),
                            rs.getBoolean("archived")
                    ));
                }
            }
            countLabel.setText(archivedView
                    ? rows.size() + " archived vehicle" + (rows.size() == 1 ? "" : "s")
                    : rows.size() + " in your fleet");
        } catch (SQLException e) {
            e.printStackTrace();
            countLabel.setText("Could not load the vehicles. Check the database connection.");
        }

        if (keepPage) {
            pager.refreshItems(rows);
        } else {
            pager.setItems(rows);
        }
    }

    // ------------------------------------------------------------ status, archive

    /**
     * Changes a vehicle's status. Making a rented or overdue car Available again
     * also closes its open rental as Returned (e.g. the customer brought it back early).
     */
    private void changeStatus(VehicleRow row, String newStatus) {
        boolean wasOut = "Rented".equals(row.status.get()) || "Overdue".equals(row.status.get());
        if ("Available".equals(newStatus) && wasOut) {
            Alert confirm = Dialogs.create(Alert.AlertType.CONFIRMATION,
                    row.model.get() + " (" + row.plate.get() + ") is currently "
                            + row.status.get().toLowerCase() + ".\n\n"
                            + "Make it available again? Its open rental will be marked as Returned.",
                    ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.setTitle("Mark as returned");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) {
                return;
            }
        }
        try {
            DatabaseConnection.setVehicleStatus(row.id, newStatus, Session.username);
        } catch (SQLException e) {
            e.printStackTrace();
            Alert error = Dialogs.create(Alert.AlertType.ERROR, "Could not change the status. Try again.");
            error.setHeaderText(null);
            error.showAndWait();
        }
        loadVehicles(true);
    }

    /** Archiving hides a car instead of deleting it, so its rental history is kept. */
    private void confirmAndArchive(VehicleRow row) {
        Alert confirm = Dialogs.create(Alert.AlertType.CONFIRMATION,
                "Archive " + row.model.get() + " (" + row.plate.get() + ")?\n\n"
                        + "It will be hidden from the vehicle list and can't be rented, but its rental history "
                        + "is kept. You can restore it anytime from the Archived tab.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Archive vehicle");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) {
            return;
        }

        // A car that is out with a customer can't be archived.
        String sql = "UPDATE vehicles SET archived = 1 WHERE vehicle_id = ? AND status NOT IN ('Rented','Overdue')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, row.id);
            if (stmt.executeUpdate() == 0) {
                Alert warn = Dialogs.create(Alert.AlertType.WARNING,
                        "This car is rented out right now. Return it first, then archive it.", ButtonType.OK);
                warn.setHeaderText(null);
                warn.setTitle("Can't archive");
                warn.showAndWait();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Alert error = Dialogs.create(Alert.AlertType.ERROR, "Could not archive the vehicle. Try again.");
            error.setHeaderText(null);
            error.showAndWait();
        }
        loadVehicles(true);
    }

    private void restore(VehicleRow row) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE vehicles SET archived = 0 WHERE vehicle_id = ?")) {
            stmt.setInt(1, row.id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            Alert error = Dialogs.create(Alert.AlertType.ERROR, "Could not restore the vehicle. Try again.");
            error.setHeaderText(null);
            error.showAndWait();
        }
        loadVehicles(true);
    }

    // ------------------------------------------------------------ filter pills

    @FXML private void onFilterAll() { setFilter("All", filterAllBtn); }
    @FXML private void onFilterAvailable() { setFilter("Available", filterAvailableBtn); }
    @FXML private void onFilterRented() { setFilter("Rented", filterRentedBtn); }
    @FXML private void onFilterOverdue() { setFilter("Overdue", filterOverdueBtn); }
    @FXML private void onFilterMaintenance() { setFilter("Maintenance", filterMaintenanceBtn); }
    @FXML private void onFilterArchived() { setFilter("Archived", filterArchivedBtn); }

    private void setFilter(String filter, Button activeBtn) {
        currentFilter = filter;
        filterAllBtn.getStyleClass().setAll("pill");
        filterAvailableBtn.getStyleClass().setAll("pill");
        filterRentedBtn.getStyleClass().setAll("pill");
        filterOverdueBtn.getStyleClass().setAll("pill");
        filterMaintenanceBtn.getStyleClass().setAll("pill");
        filterArchivedBtn.getStyleClass().setAll("pill");
        activeBtn.getStyleClass().setAll("pill-active");
        loadVehicles();
    }

    // ------------------------------------------------------------ add vehicle popup

    @FXML
    private void onAddVehicleClick() {
        clearFormErrors();
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
        colorCombo.getSelectionModel().clearSelection();
        statusCombo.getSelectionModel().selectFirst();
        clearFormErrors();
        addVehicleOverlay.setVisible(false);
        addVehicleOverlay.setManaged(false);
    }

    private void clearFormErrors() {
        FormValidation.clear(plateField, plateErrorLabel);
        FormValidation.clear(modelField, modelErrorLabel);
        FormValidation.clear(typeCombo, typeErrorLabel);
        FormValidation.clear(seatsCombo, seatsErrorLabel);
        FormValidation.clear(colorCombo, colorErrorLabel);
        FormValidation.clear(rateField, rateErrorLabel);
        formMessageLabel.setText("");
    }

    @FXML
    private void onSaveVehicleClick() {
        String plate = plateField.getText().trim().toUpperCase();
        String model = modelField.getText().trim();
        String type = typeCombo.getValue();
        Integer seats = seatsCombo.getValue();
        String color = colorCombo.getValue();
        String rateText = rateField.getText().trim();
        String status = statusCombo.getValue();

        // Check every field so all the problems show at once, each under its own field.
        boolean ok = FormValidation.show(plateField, plateErrorLabel, FormValidation.plate(plate, true));
        ok &= FormValidation.show(modelField, modelErrorLabel, FormValidation.vehicleModel(model, true));
        ok &= FormValidation.show(typeCombo, typeErrorLabel, type == null ? "Select a body type." : null);
        ok &= FormValidation.show(seatsCombo, seatsErrorLabel, seats == null
                ? (type == null ? "Select the body type first." : "Select the number of seats.") : null);
        ok &= FormValidation.show(colorCombo, colorErrorLabel, color == null ? "Select the car's color." : null);
        ok &= FormValidation.show(rateField, rateErrorLabel, FormValidation.rate(rateText, true));
        if (!ok) {
            formMessageLabel.setText("Please fix the fields marked in red.");
            return;
        }
        formMessageLabel.setText("");

        // Store plates the same way every time: ABC 1234
        if (plate.length() == 7 && !plate.contains(" ")) {
            plate = plate.substring(0, 3) + " " + plate.substring(3);
        }
        double rate = Double.parseDouble(rateText);

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Checked here in code, so the message is ours and sits under the plate field.
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM vehicles WHERE UPPER(plate_no) = ?")) {
                check.setString(1, plate);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        FormValidation.show(plateField, plateErrorLabel, "That plate number is already registered.");
                        return;
                    }
                }
            }

            String sql = "INSERT INTO vehicles (plate_no, model, type, seats, color, rate_per_day, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, plate);
                stmt.setString(2, model);
                stmt.setString(3, type);
                stmt.setInt(4, seats);
                stmt.setString(5, color);
                stmt.setDouble(6, rate);
                stmt.setString(7, status);
                stmt.executeUpdate();
            }

            onCancelAddVehicle();
            loadVehicles();

        } catch (SQLException e) {
            e.printStackTrace();
            formMessageLabel.setText("Could not save the vehicle. Please try again.");
        }
    }

    // ------------------------------------------------------------ edit vehicle popup (rate + color)

    private void openEdit(VehicleRow row) {
        editTarget = row;
        editRateVehicleLabel.setText(row.model.get() + " (" + row.plate.get() + ") \u00b7 current rate \u20b1"
                + String.format("%,.0f", row.rate.get()) + " / day");
        newRateField.setText(String.format(java.util.Locale.ROOT, "%.2f", row.rate.get()).replaceAll("\\.00$", ""));
        editColorCombo.setValue(row.color);
        editSeatsCombo.setItems(FXCollections.observableArrayList(VehicleSpecs.seatsFor(row.type.get())));
        editSeatsCombo.setValue(row.seats);
        FormValidation.clear(newRateField, newRateErrorLabel);
        editRateMessageLabel.setText("");
        editRateOverlay.setVisible(true);
        editRateOverlay.setManaged(true);
        newRateField.requestFocus();
    }

    @FXML
    private void onCancelEditRate() {
        newRateField.clear();
        editColorCombo.setValue(null);
        editSeatsCombo.setValue(null);
        FormValidation.clear(newRateField, newRateErrorLabel);
        editRateMessageLabel.setText("");
        editRateOverlay.setVisible(false);
        editRateOverlay.setManaged(false);
        editTarget = null;
    }

    @FXML
    private void onSaveRateClick() {
        if (editTarget == null) {
            return;
        }
        String text = newRateField.getText().trim();
        String problem = FormValidation.rate(text, true);
        if (!FormValidation.show(newRateField, newRateErrorLabel, problem)) {
            return;
        }
        double newRate = Double.parseDouble(text);
        String newColor = editColorCombo.getValue();

        boolean rateChanged = Math.abs(newRate - editTarget.rate.get()) >= 0.005;
        boolean colorChanged = newColor != null && !newColor.equals(editTarget.color);
        Integer newSeats = editSeatsCombo.getValue();
        boolean seatsChanged = newSeats != null && !newSeats.equals(editTarget.seats);
        if (!rateChanged && !colorChanged && !seatsChanged) {
            editRateMessageLabel.setText("Nothing was changed.");
            return;
        }

        if (rateChanged) {
            Alert confirm = Dialogs.create(Alert.AlertType.CONFIRMATION,
                    "Change the rate of " + editTarget.model.get() + " (" + editTarget.plate.get() + ")\n"
                            + "from \u20b1" + String.format("%,.2f", editTarget.rate.get())
                            + " to \u20b1" + String.format("%,.2f", newRate) + " per day?\n\n"
                            + "The new rate applies to new rentals only. Rentals already made keep their price.",
                    ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.setTitle("Change rate");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) {
                return;
            }
        }

        String sql = "UPDATE vehicles SET rate_per_day = ?, color = ?, seats = ? WHERE vehicle_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, newRate);
            stmt.setString(2, newColor != null ? newColor : editTarget.color);
            stmt.setObject(3, newSeats != null ? newSeats : editTarget.seats);
            stmt.setInt(4, editTarget.id);
            stmt.executeUpdate();
            onCancelEditRate();
            loadVehicles(true);
        } catch (SQLException e) {
            e.printStackTrace();
            editRateMessageLabel.setText("Could not save the changes. Please try again.");
        }
    }

    // ------------------------------------------------------------ navigation

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
    private void onHistoryClick() {
        switchScene("history-view.fxml", "Car Rental System - History");
    }

    @FXML
    private void onAccountsClick() {
        if (!Session.isAdmin()) return;
        switchScene("accounts-view.fxml", "Car Rental System - Accounts");
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
        final javafx.beans.property.SimpleStringProperty plate;
        final javafx.beans.property.SimpleStringProperty model;
        final javafx.beans.property.SimpleStringProperty type;
        final String color;          // null for cars added before colors existed
        final Integer seats;         // null for cars added before seats were saved
        final javafx.beans.property.SimpleDoubleProperty rate;
        final javafx.beans.property.SimpleStringProperty status;
        final boolean archived;

        VehicleRow(int id, String plate, String model, String type, String color, Integer seats,
                   double rate, String status, boolean archived) {
            this.id = id;
            this.plate = new javafx.beans.property.SimpleStringProperty(plate);
            this.model = new javafx.beans.property.SimpleStringProperty(model);
            this.type = new javafx.beans.property.SimpleStringProperty(type);
            this.color = color;
            this.seats = seats;
            this.rate = new javafx.beans.property.SimpleDoubleProperty(rate);
            this.status = new javafx.beans.property.SimpleStringProperty(status);
            this.archived = archived;
        }
    }
}