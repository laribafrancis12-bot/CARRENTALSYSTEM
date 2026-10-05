package com.carrentalsystem.carsystem;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
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
    @FXML private Label addStaffMessageLabel;

    @FXML private Label changePasswordTitleLabel;
    @FXML private PasswordField changePasswordField;
    @FXML private PasswordField confirmChangePasswordField;
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
        loadAccounts();
    }

    private void loadAccounts() {
        accountListContainer.getChildren().clear();

        String sql = "SELECT username, role FROM users ORDER BY role DESC, username ASC";
        int count = 0;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String username = rs.getString("username");
                String role = rs.getString("role");
                if ("staff".equalsIgnoreCase(role)) {
                    count++;
                }
                accountListContainer.getChildren().add(buildAccountRow(username, role));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        countLabel.setText(count + " staff account" + (count == 1 ? "" : "s"));
    }

    private HBox buildAccountRow(String username, String role) {
        Label nameLabel = new Label(username);
        nameLabel.getStyleClass().add("due-name");

        Label roleBadge = new Label(role.substring(0, 1).toUpperCase() + role.substring(1));
        roleBadge.getStyleClass().add("admin".equalsIgnoreCase(role) ? "badge-blue" : "badge-green");

        VBox info = new VBox(4, nameLabel, roleBadge);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox row = new HBox(10, info);
        row.getStyleClass().add("due-card");
        row.setAlignment(Pos.CENTER_LEFT);

        // The owner's own admin account can't be edited or deleted from this screen.
        boolean isSelf = username.equalsIgnoreCase(Session.username);
        boolean isAdminAccount = "admin".equalsIgnoreCase(role);

        if (!isSelf && !isAdminAccount) {
            Button changePwBtn = new Button("Change password");
            changePwBtn.getStyleClass().add("ghost-button");
            changePwBtn.setOnAction(e -> openChangePassword(username));

            Button deleteBtn = new Button("Delete");
            deleteBtn.getStyleClass().add("ghost-button");
            deleteBtn.setOnAction(e -> confirmAndDelete(username));

            row.getChildren().addAll(changePwBtn, deleteBtn);
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
        addStaffMessageLabel.setText("");
        addStaffOverlay.setVisible(true);
        addStaffOverlay.setManaged(true);
    }

    @FXML
    private void onCancelAddStaff() {
        addStaffOverlay.setVisible(false);
        addStaffOverlay.setManaged(false);
    }

    @FXML
    private void onCreateStaffClick() {
        String username = newUsernameField.getText().trim();
        String password = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            addStaffMessageLabel.setText("Fill in a username and password.");
            return;
        }
        if (password.length() < 6) {
            addStaffMessageLabel.setText("Password must be at least 6 characters.");
            return;
        }
        if (!password.equals(confirm)) {
            addStaffMessageLabel.setText("Passwords don't match.");
            return;
        }

        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, 'staff')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();

            onCancelAddStaff();
            loadAccounts();
            showInfo("Account created", "Staff account \"" + username + "\" was created successfully.");

        } catch (SQLException e) {
            e.printStackTrace();
            addStaffMessageLabel.setText("That username may already exist.");
        }
    }

    // ---- Change password ----

    private void openChangePassword(String username) {
        targetUsername = username;
        changePasswordTitleLabel.setText("Change password \u00b7 " + username);
        changePasswordField.clear();
        confirmChangePasswordField.clear();
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

        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            changePasswordMessageLabel.setText("Enter and confirm the new password.");
            return;
        }
        if (newPassword.length() < 6) {
            changePasswordMessageLabel.setText("Password must be at least 6 characters.");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            changePasswordMessageLabel.setText("Passwords don't match.");
            return;
        }

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
            changePasswordMessageLabel.setText("Could not update the password.");
        }
    }

    // ---- Delete ----

    private void confirmAndDelete(String username) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete this account?\n\n"
                        + "Username: " + username + "\n"
                        + "This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Delete account");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            deleteAccount(username);
        }
    }

    private void deleteAccount(String username) {
        // "AND role = 'staff'" is a safety net so an admin account can never be deleted from here.
        String sql = "DELETE FROM users WHERE username = ? AND role = 'staff'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            int deleted = stmt.executeUpdate();
            loadAccounts();

            if (deleted > 0) {
                showInfo("Account deleted", "The account \"" + username + "\" was deleted.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showInfo("Error", "Could not delete the account. Try again.");
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
    private void onAccountsClick() {
        // Already here
    }

    @FXML
    private void onLogoutClick() {
        Session.clear();
        goToLogin();
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