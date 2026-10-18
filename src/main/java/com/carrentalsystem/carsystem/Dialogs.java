package com.carrentalsystem.carsystem;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import java.net.URL;

/**
 * Creates the pop-up messages ("Are you sure?", errors, confirmations) in the system's dark theme.
 *
 * Use Dialogs.create(...) exactly like new Alert(...); everything else (setTitle, setHeaderText,
 * showAndWait ...) stays the same. The look comes from the ".themed-dialog" rules in style.css.
 */
public final class Dialogs {

    private Dialogs() {}

    public static Alert create(Alert.AlertType type) {
        return style(new Alert(type), type);
    }

    public static Alert create(Alert.AlertType type, String content, ButtonType... buttons) {
        Alert alert = buttons.length == 0 ? new Alert(type, content) : new Alert(type, content, buttons);
        return style(alert, type);
    }

    private static Alert style(Alert alert, Alert.AlertType type) {
        DialogPane pane = alert.getDialogPane();

        URL css = Dialogs.class.getResource("style.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("themed-dialog");

        // Long messages are shown in full instead of being cut with "..."
        pane.setMinHeight(Region.USE_PREF_SIZE);
        pane.setMinWidth(400);
        alert.setResizable(true);

        // The round icon replaces the standard blue/yellow pictures.
        Label icon = new Label(switch (type) {
            case CONFIRMATION -> "?";
            case INFORMATION -> "i";
            default -> "!";
        });
        icon.getStyleClass().addAll("dialog-icon", switch (type) {
            case INFORMATION -> "dialog-icon-info";
            case WARNING -> "dialog-icon-warning";
            default -> "dialog-icon-main";
        });
        alert.setGraphic(icon);

        // Show it over the application window instead of in a corner of the screen.
        Window owner = null;
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w.isFocused()) {
                owner = w;
                break;
            }
        }
        if (owner == null) {
            for (Window w : Window.getWindows()) {
                if (w.isShowing()) {
                    owner = w;
                    break;
                }
            }
        }
        if (owner != null) {
            alert.initOwner(owner);
        }
        return alert;
    }
}