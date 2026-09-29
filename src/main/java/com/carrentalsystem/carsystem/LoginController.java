package com.carrentalsystem.carsystem;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private CheckBox showPasswordCheck;
    @FXML private Label messageLabel;

    @FXML
    public void initialize() {
        // Keep the hidden and visible password fields in sync as the user types
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
    }

    @FXML
    protected void onTogglePasswordVisibility() {
        boolean show = showPasswordCheck.isSelected();
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordVisibleField.setVisible(show);
        passwordVisibleField.setManaged(show);
    }

    @FXML
    protected void onLoginClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage("Enter your username and password", false);

        } else if (username.equals("admin") && password.equals("1234")) {

            showMessage("Login successful", true);

            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("dashboard-view.fxml")
                );

                Parent dashboardRoot = loader.load();

                Stage stage = (Stage) usernameField.getScene().getWindow();

                stage.setScene(new Scene(dashboardRoot));
                stage.setTitle("Car Rental System - Dashboard");

            } catch (Exception e) {
                e.printStackTrace();
                showMessage("Could not open dashboard", false);
            }

        } else {
            showMessage("Invalid username or password", false);
        }
    }

    private void showMessage(String text, boolean success) {
        messageLabel.setText(text);
        messageLabel.setStyle(success ? "-fx-text-fill: #1F5A45;" : "-fx-text-fill: #9A2A12;");
    }
}