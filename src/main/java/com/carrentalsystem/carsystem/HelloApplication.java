package com.carrentalsystem.carsystem;

import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        DatabaseConnection.ensureSchema();
        Navigator.showLogin(stage);   // opens full screen, login card centered
    }

    public static void main(String[] args) {
        launch();
    }
}