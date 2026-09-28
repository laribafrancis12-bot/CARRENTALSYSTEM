package com.carrentalsystem.carsystem;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    @FXML
    protected void onLoginClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage("Enter your username and password", false);
        } else if (username.equals("admin") && password.equals("1234")) {
            showMessage("Login successful", true);
            // Later: open the dashboard screen here
        } else {
            showMessage("Invalid username or password", false);
        }
    }

    private void showMessage(String text, boolean success) {
        messageLabel.setText(text);
        messageLabel.setStyle(success ? "-fx-text-fill: #3B6D11;" : "-fx-text-fill: #A32D2D;");
    }
}