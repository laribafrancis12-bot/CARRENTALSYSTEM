package com.carrentalsystem.carsystem;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Central place for switching screens.
 *
 * - The app opens maximized (full screen) the first time you leave the login screen.
 * - Screens are swapped with scene.setRoot(...), so the window keeps whatever size the
 *   user chose (maximized or resized) instead of resetting on every click.
 * - Below COMPACT_BELOW pixels wide, a "compact" style class is added to the root so
 *   style.css can shrink the sidebar, fonts and padding.
 */
public final class Navigator {

    private static final String SHELL_KEY = "appShellReady";
    private static final double MIN_WIDTH = 900;
    private static final double MIN_HEIGHT = 600;
    private static final double COMPACT_BELOW = 1100;

    /** true = the login screen also opens full screen, with its card centered. */
    private static final boolean LOGIN_MAXIMIZED = true;

    private Navigator() {}

    /** Opens a main app screen and returns its controller. */
    public static <T> T show(Stage stage, String fxml, String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxml));
        Parent root = loader.load();

        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }

        stage.setTitle(title);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        // Maximize only once, when coming from the login screen.
        if (!stage.getProperties().containsKey(SHELL_KEY)) {
            stage.getProperties().put(SHELL_KEY, Boolean.TRUE);
            stage.setMaximized(true);
        }

        installBreakpoints(scene);
        applyBreakpoint(scene.getRoot(), scene.getWidth());
        return loader.getController();
    }

    /** Shows the login screen (used on startup and on log out). */
    public static void showLogin(Stage stage) throws IOException {
        Parent root = new FXMLLoader(HelloApplication.class.getResource("login-view.fxml")).load();

        stage.getProperties().remove(SHELL_KEY);
        stage.setMaximized(false);
        stage.setMinWidth(0);
        stage.setMinHeight(0);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Car Rental System - Login");

        if (LOGIN_MAXIMIZED) {
            stage.setMaximized(true);
        } else {
            stage.sizeToScene();
            stage.centerOnScreen();
        }
        stage.show();
    }

    private static void installBreakpoints(Scene scene) {
        if (scene.getProperties().containsKey("responsiveInstalled")) return;
        scene.getProperties().put("responsiveInstalled", Boolean.TRUE);
        scene.widthProperty().addListener((obs, oldW, newW) ->
                applyBreakpoint(scene.getRoot(), newW.doubleValue()));
    }

    private static void applyBreakpoint(Parent root, double width) {
        boolean compact = width < COMPACT_BELOW;
        boolean has = root.getStyleClass().contains("compact");
        if (compact && !has) {
            root.getStyleClass().add("compact");
        } else if (!compact && has) {
            root.getStyleClass().remove("compact");
        }
    }
}