package com.carrentalsystem.carsystem;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private Label messageLabel;

    @FXML
    public void initialize() {
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        PasswordEye.attach(passwordField, passwordVisibleField);   // the eye in the password box
    }

    @FXML
    protected void onLoginClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage("Enter your username and password", false);
            return;
        }

        String sql = "SELECT username, role, active FROM users WHERE BINARY username = ? AND BINARY password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (!rs.getBoolean("active")) {
                        showMessage("This account has been deactivated. Please contact the admin.", false);
                        return;
                    }
                    Session.username = rs.getString("username");   // exactly as saved (capitals included)
                    Session.role = rs.getString("role");
                    goToDashboard();
                } else {
                    showMessage("Invalid username or password", false);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showMessage("Could not reach the database", false);
        }
    }

    private void goToDashboard() {
        try {
            Navigator.show((Stage) usernameField.getScene().getWindow(),
                    "dashboard-view.fxml", "Car Rental System - Dashboard");
        } catch (IOException e) {
            e.printStackTrace();
            showMessage("Could not open the dashboard", false);
        }
    }

    private void showMessage(String text, boolean success) {
        messageLabel.setText(text);
        messageLabel.setStyle(success ? "-fx-text-fill: #1B8A45;" : "-fx-text-fill: #C62828;");
    }
}