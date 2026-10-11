package com.carrentalsystem.carsystem;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.util.Optional;

public class AccountsController {

    @FXML private StackPane addStaffOverlay;
    @FXML private StackPane changePasswordOverlay;
    @FXML private VBox accountListContainer;
    @FXML private Label countLabel;

    @FXML private TextField newUsernameField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label newUsernameErrorLabel;
    @FXML private Label newPasswordErrorLabel;
    @FXML private Label confirmPasswordErrorLabel;
    @FXML private Label addStaffMessageLabel;

    @FXML private Label changePasswordTitleLabel;
    @FXML private PasswordField changePasswordField;
    @FXML private PasswordField confirmChangePasswordField;
    @FXML private Label changePasswordErrorLabel;
    @FXML private Label confirmChangePasswordErrorLabel;
    @FXML private Label changePasswordMessageLabel;

    /** Which account the change-password popup is currently targeting. */
    private String targetUsername;

    @FXML
    public void initialize() {
        if (!Session.isAdmin()) {
            // The scene isn't attached yet during initialize(), so wait until it is.
            Platform.runLater(() -> switchScene("dashboard-view.fxml", "Car Rental System - Dashboard"));
            return;
        }

        // Warnings are written in code and appear right under the field that has the problem.
        FormValidation.live(newUsernameField, newUsernameErrorLabel,
                c -> FormValidation.username(newUsernameField.getText(), c));
        FormValidation.live(newPasswordField, newPasswordErrorLabel,
                c -> FormValidation.password(newPasswordField.getText(), c));
        FormValidation.live(confirmPasswordField, confirmPasswordErrorLabel,
                c -> FormValidation.confirmPassword(newPasswordField.getText(), confirmPasswordField.getText(), c));
        newPasswordField.textProperty().addListener((o, a, b) -> {
            if (!confirmPasswordField.getText().isEmpty()) {
                FormValidation.show(confirmPasswordField, confirmPasswordErrorLabel,
                        FormValidation.confirmPassword(b, confirmPasswordField.getText(), true));
            }
        });

        FormValidation.live(changePasswordField, changePasswordErrorLabel,
                c -> FormValidation.password(changePasswordField.getText(), c));
        FormValidation.live(confirmChangePasswordField, confirmChangePasswordErrorLabel,
                c -> FormValidation.confirmPassword(changePasswordField.getText(), confirmChangePasswordField.getText(), c));
        changePasswordField.textProperty().addListener((o, a, b) -> {
            if (!confirmChangePasswordField.getText().isEmpty()) {
                FormValidation.show(confirmChangePasswordField, confirmChangePasswordErrorLabel,
                        FormValidation.confirmPassword(b, confirmChangePasswordField.getText(), true));
            }
        });

        loadAccounts();
    }

    private void loadAccounts() {
        accountListContainer.getChildren().clear();

        String sql = "SELECT username, role, active FROM users ORDER BY role DESC, active DESC, username ASC";
        int activeStaff = 0;
        int inactiveStaff = 0;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String username = rs.getString("username");
                String role = rs.getString("role");
                boolean active = rs.getBoolean("active");
                if ("staff".equalsIgnoreCase(role)) {
                    if (active) {
                        activeStaff++;
                    } else {
                        inactiveStaff++;
                    }
                }
                accountListContainer.getChildren().add(buildAccountRow(username, role, active));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showInfo("Error", "Could not load the accounts. Check the database connection.");
        }

        countLabel.setText(activeStaff + " active staff account" + (activeStaff == 1 ? "" : "s")
                + (inactiveStaff > 0 ? " \u00b7 " + inactiveStaff + " inactive" : ""));
    }

    private HBox buildAccountRow(String username, String role, boolean active) {
        Label nameLabel = new Label(username);
        nameLabel.getStyleClass().add("due-name");

        Label roleBadge = new Label(role.substring(0, 1).toUpperCase() + role.substring(1));
        roleBadge.getStyleClass().add("admin".equalsIgnoreCase(role) ? "badge-blue" : "badge-green");

        HBox badges = new HBox(6, roleBadge);
        if (!active) {
            Label inactiveBadge = new Label("Inactive");
            inactiveBadge.getStyleClass().add("badge-gray");
            badges.getChildren().add(inactiveBadge);
            nameLabel.setOpacity(0.55);
        }

        VBox info = new VBox(4, nameLabel, badges);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox row = new HBox(10, info);
        row.getStyleClass().add("due-card");
        row.setAlignment(Pos.CENTER_LEFT);

        // The owner's own admin account can't be edited or deactivated from this screen.
        boolean isSelf = username.equalsIgnoreCase(Session.username);
        boolean isAdminAccount = "admin".equalsIgnoreCase(role);

        if (!isSelf && !isAdminAccount) {
            if (active) {
                Button changePwBtn = new Button("Change password");
                changePwBtn.getStyleClass().add("ghost-button");
                changePwBtn.setOnAction(e -> openChangePassword(username));

                Button deactivateBtn = new Button("Deactivate");
                deactivateBtn.getStyleClass().add("ghost-button");
                deactivateBtn.setOnAction(e -> confirmAndDeactivate(username));

                row.getChildren().addAll(changePwBtn, deactivateBtn);
            } else {
                Button activateBtn = new Button("Activate");
                activateBtn.getStyleClass().add("ghost-button");
                activateBtn.setOnAction(e -> setActive(username, true));
                row.getChildren().add(activateBtn);
            }
        } else if (isSelf) {
            Label note = new Label("This is you");
            note.getStyleClass().add("due-detail");
            row.getChildren().add(note);
        }

        return row;
    }

    /** Simple OK dialog used for success / error messages. */
    private void showInfo(String title, String message) {
        Alert info = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        info.setHeaderText(null);
        info.setTitle(title);
        info.showAndWait();
    }

    // ---- Add staff ----

    @FXML
    private void onAddStaffClick() {
        newUsernameField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
        clearAddStaffErrors();
        addStaffOverlay.setVisible(true);
        addStaffOverlay.setManaged(true);
    }

    @FXML
    private void onCancelAddStaff() {
        addStaffOverlay.setVisible(false);
        addStaffOverlay.setManaged(false);
    }

    private void clearAddStaffErrors() {
        FormValidation.clear(newUsernameField, newUsernameErrorLabel);
        FormValidation.clear(newPasswordField, newPasswordErrorLabel);
        FormValidation.clear(confirmPasswordField, confirmPasswordErrorLabel);
        addStaffMessageLabel.setText("");
    }

    @FXML
    private void onCreateStaffClick() {
        String username = newUsernameField.getText().trim();
        String password = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        boolean ok = FormValidation.show(newUsernameField, newUsernameErrorLabel, FormValidation.username(username, true));
        ok &= FormValidation.show(newPasswordField, newPasswordErrorLabel, FormValidation.password(password, true));
        ok &= FormValidation.show(confirmPasswordField, confirmPasswordErrorLabel,
                FormValidation.confirmPassword(password, confirm, true));
        if (!ok) {
            return;
        }
        addStaffMessageLabel.setText("");

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Checked here in code, so the message is ours and sits under the username field.
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?)")) {
                check.setString(1, username);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        FormValidation.show(newUsernameField, newUsernameErrorLabel,
                                "That username is already taken. Choose a different one.");
                        return;
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users (username, password, role) VALUES (?, ?, 'staff')")) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                stmt.executeUpdate();
            }

            onCancelAddStaff();
            loadAccounts();
            showInfo("Account created", "Staff account \"" + username + "\" was created successfully.");

        } catch (SQLException e) {
            e.printStackTrace();
            addStaffMessageLabel.setText("Could not create the account. Please try again.");
        }
    }

    // ---- Change password ----

    private void openChangePassword(String username) {
        targetUsername = username;
        changePasswordTitleLabel.setText("Change password \u00b7 " + username);
        changePasswordField.clear();
        confirmChangePasswordField.clear();
        FormValidation.clear(changePasswordField, changePasswordErrorLabel);
        FormValidation.clear(confirmChangePasswordField, confirmChangePasswordErrorLabel);
        changePasswordMessageLabel.setText("");
        changePasswordOverlay.setVisible(true);
        changePasswordOverlay.setManaged(true);
    }

    @FXML
    private void onCancelChangePassword() {
        changePasswordOverlay.setVisible(false);
        changePasswordOverlay.setManaged(false);
    }

    @FXML
    private void onSavePasswordClick() {
        String newPassword = changePasswordField.getText();
        String confirmPassword = confirmChangePasswordField.getText();

        boolean ok = FormValidation.show(changePasswordField, changePasswordErrorLabel,
                FormValidation.password(newPassword, true));
        ok &= FormValidation.show(confirmChangePasswordField, confirmChangePasswordErrorLabel,
                FormValidation.confirmPassword(newPassword, confirmPassword, true));
        if (!ok) {
            return;
        }
        changePasswordMessageLabel.setText("");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to change the password for \"" + targetUsername + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Change password");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) {
            return;
        }

        String sql = "UPDATE users SET password = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newPassword);
            stmt.setString(2, targetUsername);
            stmt.executeUpdate();

            onCancelChangePassword();
            showInfo("Password changed",
                    "The password for \"" + targetUsername + "\" was changed successfully.");

        } catch (SQLException e) {
            e.printStackTrace();
            changePasswordMessageLabel.setText("Could not update the password. Please try again.");
        }
    }

    // ---- Deactivate / activate (soft delete) ----

    private void confirmAndDeactivate(String username) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Deactivate the account \"" + username + "\"?\n\n"
                        + "They will no longer be able to log in. Their rental records are kept, "
                        + "and you can activate the account again at any time.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Deactivate account");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            setActive(username, false);
        }
    }

    private void setActive(String username, boolean active) {
        // "AND role = 'staff'" is a safety net so an admin account can never be changed from here.
        String sql = "UPDATE users SET active = ? WHERE username = ? AND role = 'staff'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, active);
            stmt.setString(2, username);
            int changed = stmt.executeUpdate();
            loadAccounts();

            if (changed > 0) {
                showInfo(active ? "Account activated" : "Account deactivated",
                        active ? "The account \"" + username + "\" can log in again."
                                : "The account \"" + username + "\" was deactivated.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showInfo("Error", "Could not update the account. Please try again.");
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
        switchScene("rentals-view.fxml", "Car Rental System - Rentals");
    }

    @FXML
    private void onHistoryClick() {
        switchScene("history-view.fxml", "Car Rental System - History");
    }

    @FXML
    private void onAccountsClick() {
        // Already here
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
}