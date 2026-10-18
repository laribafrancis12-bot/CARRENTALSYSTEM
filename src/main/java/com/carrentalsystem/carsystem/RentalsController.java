package com.carrentalsystem.carsystem;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.skin.DatePickerSkin;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Supplier;
import java.io.File;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RentalsController {

    /** The only payment method: the field on the form is fixed and cannot be changed. */
    private static final String PAYMENT_METHOD = "Cash";

    private static final DateTimeFormatter TABLE_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    /** Which pill is selected: All, Active or Due. (Returned rentals live on the History screen.) */
    private String currentFilter = "All";

    @FXML private Button vehiclesBtn;
    @FXML private Button accountsBtn;
    @FXML private StackPane newRentalOverlay;
    @FXML private TextField searchField;
    @FXML private Label countLabel;

    @FXML private Button filterAllBtn;
    @FXML private Button filterActiveBtn;
    @FXML private Button filterDueBtn;

    @FXML private TableView<RentalRow> rentalTable;
    @FXML private TableColumn<RentalRow, Integer> idCol;
    @FXML private TableColumn<RentalRow, String> customerCol;
    @FXML private TableColumn<RentalRow, String> contactCol;
    @FXML private TableColumn<RentalRow, String> vehicleCol;
    @FXML private TableColumn<RentalRow, LocalDate> rentDateCol;
    @FXML private TableColumn<RentalRow, LocalDate> returnDateCol;
    @FXML private TableColumn<RentalRow, Double> totalCol;
    @FXML private TableColumn<RentalRow, String> statusCol;
    @FXML private TableColumn<RentalRow, Void> actionsCol;

    @FXML private TextField customerNameField;
    @FXML private TextField contactField;
    @FXML private TextField licenseField;
    @FXML private ComboBox<VehicleOption> vehicleCombo;
    @FXML private DatePicker rentDatePicker;
    @FXML private DatePicker returnDatePicker;
    @FXML private TextField paymentField;   // always "Cash"
    @FXML private Label totalBreakdownLabel;
    @FXML private Label totalAmountLabel;
    @FXML private Label formMessageLabel;
    @FXML private ScrollPane formScroll;
    @FXML private TextField addressField;
    @FXML private Button idPhotoBtn;
    @FXML private Label idPhotoNameLabel;
    @FXML private ImageView idPhotoPreview;
    @FXML private Label idPhotoErrorLabel;

    /** The valid-ID photo picked on the form. It is copied into the valid_ids folder when the rental is saved. */
    private File idPhotoFile;
    @FXML private Label nameErrorLabel;
    @FXML private Label contactErrorLabel;
    @FXML private Label addressErrorLabel;
    @FXML private Label licenseErrorLabel;
    @FXML private Label vehicleErrorLabel;
    @FXML private Label rentDateErrorLabel;
    @FXML private Label returnDateErrorLabel;

    @FXML
    public void initialize() {
        boolean admin = Session.isAdmin();
        vehiclesBtn.setVisible(admin);
        vehiclesBtn.setManaged(admin);
        accountsBtn.setVisible(admin);
        accountsBtn.setManaged(admin);


        rentDatePicker.setValue(LocalDate.now());
        returnDatePicker.setValue(LocalDate.now().plusDays(1));

        rentDatePicker.valueProperty().addListener((o, a, b) -> updateTotal());
        returnDatePicker.valueProperty().addListener((o, a, b) -> updateTotal());
        vehicleCombo.valueProperty().addListener((o, a, b) -> updateTotal());

        // The search box was never connected before; now the board filters as you type.
        searchField.textProperty().addListener((o, a, b) -> loadBoard(b));

        // Warnings are written in code and appear right under the field that has the problem.
        FormValidation.live(customerNameField, nameErrorLabel,
                c -> FormValidation.personName(customerNameField.getText(), c));
        FormValidation.live(contactField, contactErrorLabel,
                c -> FormValidation.phone(contactField.getText(), c));
        FormValidation.live(addressField, addressErrorLabel,
                c -> FormValidation.address(addressField.getText(), c));
        FormValidation.live(licenseField, licenseErrorLabel,
                c -> FormValidation.license(licenseField.getText(), c));
        vehicleCombo.valueProperty().addListener((o, a, b) -> {
            if (b != null) FormValidation.clear(vehicleCombo, vehicleErrorLabel);
        });
        rentDatePicker.valueProperty().addListener((o, a, b) -> {
            // If the return date is no longer after the new rent date, move it to the next day.
            LocalDate ret = returnDatePicker.getValue();
            if (b != null && (ret == null || !ret.isAfter(b))) {
                returnDatePicker.setValue(b.plusDays(1));
            }
            validateDates();
        });
        returnDatePicker.valueProperty().addListener((o, a, b) -> validateDates());

        // Wrong characters can't be typed at all: letters/spaces for the name, a fixed format for the license.
        FormValidation.restrictName(customerNameField, nameErrorLabel);
        FormValidation.restrictAddress(addressField, addressErrorLabel);
        FormValidation.restrictLicense(licenseField, licenseErrorLabel);
        FormValidation.restrictPhone(contactField, contactErrorLabel);   // 11 digits, starting with 09


        paymentField.setText(PAYMENT_METHOD);
        paymentField.setEditable(false);
        paymentField.setFocusTraversable(false);

        setUpDatePickers();

        setUpTable();
        DatabaseConnection.refreshOverdue();
        loadBoard("");
    }

    // ---- Board ----

    /** Gives a column a share of the table width (with a minimum), so the table fills the window. */
    private void sizeColumn(TableColumn<RentalRow, ?> col, double share, double minWidth) {
        col.setMinWidth(minWidth);
        // minus 24px so the vertical scrollbar never forces a horizontal one
        col.prefWidthProperty().bind(rentalTable.widthProperty().subtract(24).multiply(share));
    }

    private void setUpTable() {
        rentalTable.setPlaceholder(new Label("No rentals found"));

        sizeColumn(idCol, 0.065, 72);
        sizeColumn(customerCol, 0.145, 105);
        sizeColumn(contactCol, 0.11, 100);
        sizeColumn(vehicleCol, 0.15, 135);
        sizeColumn(rentDateCol, 0.10, 95);
        sizeColumn(returnDateCol, 0.10, 95);
        sizeColumn(totalCol, 0.09, 80);
        sizeColumn(statusCol, 0.10, 110);
        sizeColumn(actionsCol, 0.14, 190);

        idCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().id));
        idCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : "#" + value);
            }
        });

        customerCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().customer));
        contactCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().contact));
        vehicleCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().vehicle));

        rentDateCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().rentDate));
        rentDateCol.setCellFactory(col -> dateCell());
        returnDateCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().returnDate));
        returnDateCol.setCellFactory(col -> dateCell());

        totalCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().total));
        totalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : "\u20b1" + String.format("%,.0f", value));
            }
        });

        statusCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().status));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(value);
                switch (value) {
                    case "Returned" -> badge.getStyleClass().add("badge-green");
                    case "Overdue" -> badge.getStyleClass().add("badge-red");
                    case "Due today" -> badge.getStyleClass().add("badge-amber");
                    default -> badge.getStyleClass().add("badge-blue");
                }
                setGraphic(badge);
                setText(null);
            }
        });

        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button receiptBtn = new Button("Receipt");
            private final Button returnBtn = new Button("Return");
            private final HBox box = new HBox(6, receiptBtn, returnBtn);

            {
                receiptBtn.getStyleClass().addAll("ghost-button", "table-btn");
                returnBtn.getStyleClass().addAll("ghost-button", "table-btn");
                receiptBtn.setOnAction(e -> {
                    RentalRow row = rowAtIndex();
                    if (row != null) openReceipt(row.id);
                });
                returnBtn.setOnAction(e -> {
                    RentalRow row = rowAtIndex();
                    if (row != null) confirmReturn(row);
                });
            }

            private RentalRow rowAtIndex() {
                int i = getIndex();
                return (i >= 0 && i < getTableView().getItems().size()) ? getTableView().getItems().get(i) : null;
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                RentalRow row = empty ? null : rowAtIndex();
                if (row == null) {
                    setGraphic(null);
                    return;
                }
                // Rentals that are already returned only keep the Receipt button.
                boolean stillOut = !"Returned".equals(row.status);
                returnBtn.setVisible(stillOut);
                returnBtn.setManaged(stillOut);
                setGraphic(box);
            }
        });
    }

    /** Asks for confirmation, then marks the rental Returned and the vehicle Available. */
    private void confirmReturn(RentalRow row) {
        LocalDate today = LocalDate.now();
        String agreed = row.returnDate.format(TABLE_DATE);
        String timing;
        if (today.isBefore(row.returnDate)) {
            timing = "Early return (agreed return date: " + agreed + ").";
        } else if (today.isAfter(row.returnDate)) {
            timing = "Late return (agreed return date: " + agreed + ").";
        } else {
            timing = "Returned on the agreed date (" + agreed + ").";
        }

        Alert confirm = Dialogs.create(Alert.AlertType.CONFIRMATION,
                row.vehicle + "\n" + timing + "\n\n"
                        + "The rental will move to History and the vehicle will be available again. "
                        + "The amount charged stays \u20b1" + String.format("%,.0f", row.total) + ".",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Mark as returned");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) {
            return;
        }

        try {
            DatabaseConnection.markReturned(row.id);
        } catch (SQLException e) {
            e.printStackTrace();
            Alert error = Dialogs.create(Alert.AlertType.ERROR, "Could not mark this rental as returned. Try again.");
            error.setHeaderText(null);
            error.showAndWait();
        }
        loadBoard(searchField.getText());
    }

    private TableCell<RentalRow, LocalDate> dateCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(LocalDate value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : value.format(TABLE_DATE));
            }
        };
    }

    /** Reloads the table from the database, applying the search box and the selected filter pill. */
    private void loadBoard(String search) {
        String sql =
                "SELECT r.rental_id, c.full_name, c.contact_no, v.model, v.plate_no, " +
                        "r.rent_date, r.return_date, r.total_amount, r.status " +
                        "FROM rentals r " +
                        "JOIN customers c ON r.customer_id = c.customer_id " +
                        "JOIN vehicles v ON r.vehicle_id = v.vehicle_id " +
                        "WHERE (r.status IS NULL OR r.status <> 'Returned') " +
                        "AND (c.full_name LIKE ? OR v.model LIKE ? OR v.plate_no LIKE ?) " +
                        "ORDER BY r.rent_date DESC, r.rental_id DESC";

        List<RentalRow> all = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String like = "%" + (search == null ? "" : search.trim()) + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);
            stmt.setString(3, like);

            LocalDate today = LocalDate.now();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String dbStatus = rs.getString("status");
                    LocalDate returnDate = rs.getDate("return_date").toLocalDate();

                    String status;
                    if ("Returned".equals(dbStatus)) {
                        status = "Returned";
                    } else if ("Overdue".equals(dbStatus) || returnDate.isBefore(today)) {
                        status = "Overdue";
                    } else if (returnDate.isEqual(today)) {
                        status = "Due today";
                    } else {
                        status = "Active";
                    }

                    String contact = rs.getString("contact_no");
                    all.add(new RentalRow(
                            rs.getInt("rental_id"),
                            rs.getString("full_name"),
                            contact == null ? "" : contact,
                            rs.getString("model") + " \u00b7 " + rs.getString("plate_no"),
                            rs.getDate("rent_date").toLocalDate(),
                            returnDate,
                            rs.getDouble("total_amount"),
                            status
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            countLabel.setText("Could not load rentals. Check the database connection.");
            return;
        }

        int active = 0, due = 0;
        ObservableList<RentalRow> visible = FXCollections.observableArrayList();
        for (RentalRow row : all) {
            switch (row.status) {
                case "Overdue", "Due today" -> due++;
                default -> active++;
            }
            if (matchesFilter(row)) {
                visible.add(row);
            }
        }

        rentalTable.setItems(visible);
        filterAllBtn.setText("All (" + all.size() + ")");
        filterActiveBtn.setText("Active (" + active + ")");
        filterDueBtn.setText("Due or overdue (" + due + ")");
        countLabel.setText((active + due) + " cars currently out");
    }

    private boolean matchesFilter(RentalRow row) {
        return switch (currentFilter) {
            case "Active" -> row.status.equals("Active");
            case "Due" -> row.status.equals("Overdue") || row.status.equals("Due today");
            default -> true;
        };
    }

    // ---- Filter pills ----

    @FXML private void onFilterAll() { setFilter("All", filterAllBtn); }
    @FXML private void onFilterActive() { setFilter("Active", filterActiveBtn); }
    @FXML private void onFilterDue() { setFilter("Due", filterDueBtn); }

    private void setFilter(String filter, Button activeBtn) {
        currentFilter = filter;
        filterAllBtn.getStyleClass().setAll("pill");
        filterActiveBtn.getStyleClass().setAll("pill");
        filterDueBtn.getStyleClass().setAll("pill");
        activeBtn.getStyleClass().setAll("pill-active");
        loadBoard(searchField.getText());
    }

    // ---- New rental form ----

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
        totalBreakdownLabel.setText(days + (days == 1 ? " day" : " days") + " \u00d7 \u20b1" + String.format("%,.0f", vehicle.rate));
        totalAmountLabel.setText("\u20b1" + String.format("%,.2f", total));
    }

    @FXML
    private void onNewRentalClick() {
        customerNameField.clear();
        contactField.clear();
        licenseField.clear();
        addressField.clear();
        clearIdPhoto();
        rentDatePicker.setValue(LocalDate.now());
        returnDatePicker.setValue(LocalDate.now().plusDays(1));
        clearErrors();
        loadAvailableVehicles();   // after clearing the message, so "no vehicles" can show
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
        String sql = "SELECT vehicle_id, model, plate_no, rate_per_day FROM vehicles " +
                "WHERE status='Available' ORDER BY model ASC";

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
            formMessageLabel.setText("Could not load the vehicles. Check the database connection.");
        }
        vehicleCombo.setItems(options);
        vehicleCombo.getSelectionModel().clearSelection();
        if (options.isEmpty() && formMessageLabel.getText().isEmpty()) {
            formMessageLabel.setText("No vehicles are available right now.");
        }
        updateTotal();
    }

    @FXML
    private void onConfirmRentalClick() {
        String name = customerNameField.getText().trim();
        String contact = contactField.getText().trim();
        String address = addressField.getText().trim();
        String license = licenseField.getText().trim().toUpperCase();
        VehicleOption vehicle = vehicleCombo.getValue();
        LocalDate rent = rentDatePicker.getValue();
        LocalDate ret = returnDatePicker.getValue();
        String payment = PAYMENT_METHOD;

        // Check every field so all the problems show at once, each under its own field.
        boolean customerOk = FormValidation.show(customerNameField, nameErrorLabel, FormValidation.personName(name, true));
        customerOk &= FormValidation.show(contactField, contactErrorLabel, FormValidation.phone(contact, true));
        customerOk &= FormValidation.show(addressField, addressErrorLabel, FormValidation.address(address, true));
        customerOk &= FormValidation.show(licenseField, licenseErrorLabel, FormValidation.license(license, true));
        customerOk &= FormValidation.show(idPhotoBtn, idPhotoErrorLabel, FormValidation.idPhoto(idPhotoFile));
        boolean rentalOk = FormValidation.show(vehicleCombo, vehicleErrorLabel,
                vehicle == null ? "Select an available vehicle." : null);
        rentalOk &= validateDates();

        if (!customerOk || !rentalOk) {
            formMessageLabel.setText("Please fix the fields marked in red.");
            formScroll.setVvalue(customerOk ? 1.0 : 0.0);
            return;
        }
        formMessageLabel.setText("");

        long days = ChronoUnit.DAYS.between(rent, ret);
        double total = days * vehicle.rate;

        // "Are you sure?" step: nothing is saved until the details are confirmed.
        if (!confirmRental(name, contact, address, license, idPhotoFile, vehicle, rent, ret, days, total)) {
            return;
        }

        // The ID photo is copied into the valid_ids folder only after the rental was confirmed.
        String idPhotoName;
        try {
            idPhotoName = IdPhotoStorage.save(idPhotoFile);
        } catch (IOException e) {
            e.printStackTrace();
            formMessageLabel.setText("Could not save the ID photo. Please try again.");
            return;
        }

        int rentalId;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Claim the vehicle first. If someone else already rented it, stop here.
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE vehicles SET status='Rented' WHERE vehicle_id=? AND status='Available'")) {
                    stmt.setInt(1, vehicle.id);
                    if (stmt.executeUpdate() == 0) {
                        conn.rollback();
                        IdPhotoStorage.delete(idPhotoName);
                        formMessageLabel.setText("That vehicle was just rented out. Pick another one.");
                        loadAvailableVehicles();
                        return;
                    }
                }

                // 2. Customer
                int customerId;
                String customerSql = "INSERT INTO customers (full_name, contact_no, license_no, address, id_photo) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(customerSql, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, name);
                    stmt.setString(2, contact);
                    stmt.setString(3, license);
                    stmt.setString(4, address);
                    stmt.setString(5, idPhotoName);
                    stmt.executeUpdate();
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        keys.next();
                        customerId = keys.getInt(1);
                    }
                }

                // 3. Rental (id read straight from the insert)
                String rentalSql = "INSERT INTO rentals (customer_id, vehicle_id, rent_date, return_date, " +
                        "total_amount, payment_method, created_by, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'Active')";
                try (PreparedStatement stmt = conn.prepareStatement(rentalSql, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, customerId);
                    stmt.setInt(2, vehicle.id);
                    stmt.setDate(3, Date.valueOf(rent));
                    stmt.setDate(4, Date.valueOf(ret));
                    stmt.setDouble(5, total);
                    stmt.setString(6, payment);
                    stmt.setString(7, Session.username);
                    stmt.executeUpdate();
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        keys.next();
                        rentalId = keys.getInt(1);
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();   // nothing is half-saved
                throw e;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            IdPhotoStorage.delete(idPhotoName);
            formMessageLabel.setText("Could not save this rental. Please try again.");
            return;
        }

        onCancelNewRental();
        openReceipt(rentalId);
    }

    @FXML
    private void onChooseIdPhotoClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a photo of the valid ID");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Photos (JPG, PNG)", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(idPhotoBtn.getScene().getWindow());
        if (file == null) {
            return;   // cancelled
        }

        String problem = FormValidation.idPhoto(file);
        Image preview = null;
        if (problem == null) {
            preview = new Image(file.toURI().toString(), 260, 160, true, true, false);
            if (preview.isError()) {
                problem = "That file isn't a valid image. Choose a JPG or PNG photo.";
            }
        }
        if (problem != null) {
            FormValidation.show(idPhotoBtn, idPhotoErrorLabel, problem);
            return;
        }

        idPhotoFile = file;
        idPhotoNameLabel.setText(file.getName());
        idPhotoPreview.setImage(preview);
        idPhotoPreview.setVisible(true);
        idPhotoPreview.setManaged(true);
        idPhotoBtn.setText("Change photo");
        FormValidation.clear(idPhotoBtn, idPhotoErrorLabel);
    }

    private void clearIdPhoto() {
        idPhotoFile = null;
        idPhotoNameLabel.setText("No photo chosen");
        idPhotoPreview.setImage(null);
        idPhotoPreview.setVisible(false);
        idPhotoPreview.setManaged(false);
        idPhotoBtn.setText("Upload photo");
    }

    /** Summary shown before saving. Returns true only when the person confirms and pays. */
    private boolean confirmRental(String name, String contact, String address, String license,
                                  File idPhoto, VehicleOption vehicle,
                                  LocalDate rent, LocalDate ret, long days, double total) {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(7);
        grid.setPadding(new Insets(6, 4, 4, 4));

        int row = 0;
        row = addSummaryRow(grid, row, "Customer", name);
        row = addSummaryRow(grid, row, "Contact", contact);
        row = addSummaryRow(grid, row, "Address", address);
        row = addSummaryRow(grid, row, "Driver's license", license);
        row = addSummaryRow(grid, row, "Valid ID", "Photo attached");
        Label photoKey = new Label("ID photo");
        photoKey.setStyle("-fx-text-fill: #8A8D96;");
        ImageView thumbnail = new ImageView(new Image(idPhoto.toURI().toString(), 220, 130, true, true, false));
        grid.add(photoKey, 0, row);
        grid.add(thumbnail, 1, row++);
        row = addSummaryRow(grid, row, "Vehicle", vehicle.model + " \u00b7 " + vehicle.plate);
        row = addSummaryRow(grid, row, "Rental period", rent.format(TABLE_DATE) + "  to  " + ret.format(TABLE_DATE));
        row = addSummaryRow(grid, row, "Duration", days + (days == 1 ? " day" : " days")
                + " \u00d7 \u20b1" + String.format("%,.0f", vehicle.rate));
        row = addSummaryRow(grid, row, "Payment method", PAYMENT_METHOD);

        Separator line = new Separator();
        grid.add(line, 0, row++, 2, 1);

        Label totalKey = new Label("Total amount due");
        totalKey.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        Label totalValue = new Label("\u20b1" + String.format("%,.2f", total));
        totalValue.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");
        grid.add(totalKey, 0, row);
        grid.add(totalValue, 1, row++);

        Label note = new Label("By confirming, you acknowledge that the cash payment shown above has been received "
                + "and that the vehicle is released to the customer. This will be recorded and the receipt will open.");
        note.setWrapText(true);
        note.setMaxWidth(430);
        note.setStyle("-fx-text-fill: #8A8D96; -fx-font-size: 11.5px;");
        grid.add(note, 0, row, 2, 1);

        Alert dialog = Dialogs.create(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Confirm rental");
        dialog.setHeaderText("Please review the rental details before confirming");
        dialog.getDialogPane().setContent(grid);

        ButtonType confirmBtn = new ButtonType("Confirm and pay", ButtonBar.ButtonData.OK_DONE);
        ButtonType backBtn = new ButtonType("Go back", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getButtonTypes().setAll(confirmBtn, backBtn);

        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == confirmBtn;
    }

    private int addSummaryRow(GridPane grid, int row, String key, String value) {
        Label keyLabel = new Label(key);
        keyLabel.setStyle("-fx-text-fill: #8A8D96;");
        Label valueLabel = new Label(value);
        valueLabel.setWrapText(true);
        valueLabel.setMaxWidth(320);
        valueLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        grid.add(keyLabel, 0, row);
        grid.add(valueLabel, 1, row);
        return row + 1;
    }

    /**
     * Past days are greyed out and can't be picked, so the rent date can't go back to a past
     * month or year. The return date must be after the rent date. Dates are chosen from the
     * calendar (clicking the field opens it), not typed.
     */
    private void setUpDatePickers() {
        rentDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate day, boolean empty) {
                super.updateItem(day, empty);
                boolean blocked = !empty && day != null && (day.isBefore(LocalDate.now()) || day.getYear() > lastYear());
                setDisable(blocked);
                setStyle(blocked ? "-fx-opacity: 0.35;" : "");
            }
        });

        returnDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate day, boolean empty) {
                super.updateItem(day, empty);
                LocalDate rent = rentDatePicker.getValue();
                LocalDate earliest = (rent == null || rent.isBefore(LocalDate.now()) ? LocalDate.now() : rent).plusDays(1);
                boolean blocked = !empty && day != null && (day.isBefore(earliest) || day.getYear() > lastYear());
                setDisable(blocked);
                setStyle(blocked ? "-fx-opacity: 0.35;" : "");
            }
        });

        // The month and year arrows of the calendar can't go back to the past either.
        limitCalendarNavigation(rentDatePicker, LocalDate::now);
        limitCalendarNavigation(returnDatePicker, () -> {
            LocalDate rent = rentDatePicker.getValue();
            return (rent == null || rent.isBefore(LocalDate.now()) ? LocalDate.now() : rent).plusDays(1);
        });

        for (DatePicker picker : new DatePicker[] { rentDatePicker, returnDatePicker }) {
            picker.setEditable(false);
            picker.getEditor().setOnMouseClicked(e -> picker.show());
        }
    }

    /** The last year that can be picked: next year, so a rental can still run over New Year. */
    private static int lastYear() {
        return LocalDate.now().getYear() + 1;
    }

    /**
     * Greys out the calendar's month/year arrows that would go before the earliest allowed date
     * (and past next year). If the calendar's layout is not found, nothing happens: the days that
     * cannot be picked are still greyed out.
     */
    private void limitCalendarNavigation(DatePicker picker, Supplier<LocalDate> earliest) {
        picker.showingProperty().addListener((o, was, showing) -> {
            if (!showing) {
                return;
            }
            Platform.runLater(() -> {
                try {
                    if (!(picker.getSkin() instanceof DatePickerSkin)) {
                        return;
                    }
                    Node content = ((DatePickerSkin) picker.getSkin()).getPopupContent();
                    List<Node> spinners = new ArrayList<>(content.lookupAll(".spinner"));   // month, then year
                    if (spinners.size() < 2) {
                        return;
                    }
                    Node monthSpinner = spinners.get(0);
                    Node yearSpinner = spinners.get(1);
                    Node monthLabel = monthSpinner.lookup(".label");
                    Node yearLabel = yearSpinner.lookup(".label");
                    Node monthLeft = monthSpinner.lookup(".left-button");
                    Node monthRight = monthSpinner.lookup(".right-button");
                    Node yearLeft = yearSpinner.lookup(".left-button");
                    Node yearRight = yearSpinner.lookup(".right-button");
                    if (!(monthLabel instanceof Label) || !(yearLabel instanceof Label)) {
                        return;
                    }
                    Label monthText = (Label) monthLabel;
                    Label yearText = (Label) yearLabel;

                    Runnable update = () -> {
                        try {
                            int year = Integer.parseInt(yearText.getText().trim());
                            int month = monthNumber(monthText.getText());
                            LocalDate min = earliest.get();
                            boolean firstYear = year <= min.getYear();
                            if (yearLeft != null) yearLeft.setDisable(firstYear);
                            if (yearRight != null) yearRight.setDisable(year >= lastYear());
                            if (monthLeft != null) monthLeft.setDisable(firstYear && month > 0 && month <= min.getMonthValue());
                            if (monthRight != null) monthRight.setDisable(year >= lastYear() && month == 12);
                        } catch (RuntimeException ignored) {
                            // unreadable label: leave the arrows as they are
                        }
                    };

                    if (!Boolean.TRUE.equals(picker.getProperties().get("navLimited"))) {
                        picker.getProperties().put("navLimited", Boolean.TRUE);
                        monthText.textProperty().addListener((a, b, c) -> update.run());
                        yearText.textProperty().addListener((a, b, c) -> update.run());
                    }
                    update.run();
                } catch (RuntimeException ignored) {
                    // the calendar looks different on this JavaFX version: keep the default arrows
                }
            });
        });
    }

    private static int monthNumber(String name) {
        for (Month m : Month.values()) {
            if (m.getDisplayName(TextStyle.FULL, Locale.getDefault()).equalsIgnoreCase(name.trim())) {
                return m.getValue();
            }
        }
        return 0;
    }

    /** Checks both dates and shows their messages under the date fields. */
    private boolean validateDates() {
        LocalDate rent = rentDatePicker.getValue();
        LocalDate ret = returnDatePicker.getValue();
        boolean rentOk = FormValidation.show(rentDatePicker, rentDateErrorLabel, FormValidation.rentDate(rent));
        boolean retOk = FormValidation.show(returnDatePicker, returnDateErrorLabel, FormValidation.returnDate(rent, ret));
        return rentOk && retOk;
    }

    private void clearErrors() {
        FormValidation.clear(customerNameField, nameErrorLabel);
        FormValidation.clear(contactField, contactErrorLabel);
        FormValidation.clear(addressField, addressErrorLabel);
        FormValidation.clear(licenseField, licenseErrorLabel);
        FormValidation.clear(idPhotoBtn, idPhotoErrorLabel);
        FormValidation.clear(vehicleCombo, vehicleErrorLabel);
        FormValidation.clear(rentDatePicker, rentDateErrorLabel);
        FormValidation.clear(returnDatePicker, returnDateErrorLabel);
        formMessageLabel.setText("");
    }

    private void openReceipt(int rentalId) {
        try {
            ReceiptController controller = Navigator.show(
                    (Stage) countLabel.getScene().getWindow(),
                    "receipt-view.fxml", "Car Rental System - Receipt");
            controller.setRentalId(rentalId);
        } catch (IOException e) {
            e.printStackTrace();
            Alert error = Dialogs.create(Alert.AlertType.ERROR,
                    "The rental was saved, but the receipt screen could not be opened.");
            error.setHeaderText(null);
            error.setTitle("Receipt");
            error.showAndWait();
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
            try {
                Navigator.showLogin((Stage) countLabel.getScene().getWindow());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void switchScene(String fxmlFile, String title) {
        try {
            Navigator.show((Stage) countLabel.getScene().getWindow(), fxmlFile, title);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Backs one row of the rentals table. */
    public static class RentalRow {
        final int id;
        final String customer;
        final String contact;
        final String vehicle;
        final LocalDate rentDate;
        final LocalDate returnDate;
        final double total;
        final String status;

        RentalRow(int id, String customer, String contact, String vehicle,
                  LocalDate rentDate, LocalDate returnDate, double total, String status) {
            this.id = id;
            this.customer = customer;
            this.contact = contact;
            this.vehicle = vehicle;
            this.rentDate = rentDate;
            this.returnDate = returnDate;
            this.total = total;
            this.status = status;
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