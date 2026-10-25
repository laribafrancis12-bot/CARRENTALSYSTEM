package com.carrentalsystem.carsystem;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.geometry.Pos;
import javafx.scene.control.Label;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

/** The car colors a vehicle can have, with the real color shown as a small round swatch. */
public final class CarColors {

    private static final Map<String, String> HEX = new LinkedHashMap<>();

    static {
        HEX.put("White", "#FFFFFF");
        HEX.put("Black", "#1A1A1A");
        HEX.put("Silver", "#C0C4CC");
        HEX.put("Gray", "#8A8F98");
        HEX.put("Red", "#D32F2F");
        HEX.put("Maroon", "#7B1E2B");
        HEX.put("Blue", "#1E5FD8");
        HEX.put("Navy", "#14284B");
        HEX.put("Green", "#2E8B57");
        HEX.put("Yellow", "#F2C230");
        HEX.put("Orange", "#F28C28");
        HEX.put("Brown", "#7A4B2A");
        HEX.put("Beige", "#D8C3A0");
        HEX.put("Gold", "#C9A24B");
        HEX.put("Purple", "#6A3FA0");
    }

    private CarColors() {}

    public static List<String> names() {
        return List.copyOf(HEX.keySet());
    }

    /** A round swatch in the car's real color (a grey ring is drawn so white cars are visible). */
    public static Circle swatch(String colorName) {
        String hex = colorName == null ? null : HEX.get(colorName);
        Circle dot = new Circle(8);
        dot.setFill(hex == null ? Color.TRANSPARENT : Color.web(hex));
        dot.setStroke(Color.web("#8A93A5"));
        dot.setStrokeWidth(1);
        return dot;
    }

    /** Swatch + name in one row, used in the table. */
    public static HBox badge(String colorName) {
        HBox box = new HBox(8, swatch(colorName), new Label(colorName));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Style for a whole table row: a soft tint of the car's color and a stripe of the real color
     * on the left edge, so a row shows at a glance what color that car is.
     */
    public static String rowStyle(String colorName) {
        String hex = colorName == null ? null : HEX.get(colorName);
        if (hex == null) {
            return "";
        }
        Color c = Color.web(hex);
        String tint;
        String stripe = hex;
        if ("White".equals(colorName)) {
            // a white tint would be invisible on a light page, so white cars get a cool grey tint
            tint = "rgba(176, 190, 210, 0.30)";
            stripe = "#B5C8E0";
        } else {
            tint = String.format(Locale.ROOT, "rgba(%d, %d, %d, 0.20)",
                    (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                    (int) Math.round(c.getBlue() * 255));
        }
        return "-fx-background-color: " + tint + "; "
                + "-fx-border-color: transparent transparent #DCE6F3 " + stripe + "; "
                + "-fx-border-width: 0 0 1 7;";
    }

    /** Makes a color drop-down show the swatch next to each color name. */
    public static void setUp(ComboBox<String> combo) {
        combo.getItems().setAll(names());
        combo.setCellFactory(list -> new SwatchCell(null));
        combo.setButtonCell(new SwatchCell(combo));
    }

    /** A drop-down row (or the box itself) that shows the color's swatch next to its name. */
    private static final class SwatchCell extends ListCell<String> {
        private final ComboBox<String> promptOwner;   // only for the box itself: shows its hint when empty

        SwatchCell(ComboBox<String> promptOwner) {
            this.promptOwner = promptOwner;
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(promptOwner == null ? null : promptOwner.getPromptText());
                setGraphic(null);
            } else {
                setText(item);
                setGraphic(swatch(item));
            }
        }
    }
}