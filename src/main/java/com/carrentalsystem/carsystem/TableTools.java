package com.carrentalsystem.carsystem;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/** Small helpers that keep every table of the system tidy. */
public final class TableTools {

    private TableTools() {}

    /** The columns keep the width they were designed with: they can't be dragged wider or moved around. */
    public static void lockColumns(TableView<?> table) {
        for (TableColumn<?, ?> col : table.getColumns()) {
            col.setResizable(false);
            col.setReorderable(false);
        }
    }

    /** Makes every column at least as wide as its title, so no column title is cut with "...". */
    public static void fitHeaders(TableView<?> table) {
        // measured a little bigger than the real header text so there is always room to spare
        Font font = Font.font("Segoe UI", FontWeight.BOLD, 14);
        for (TableColumn<?, ?> col : table.getColumns()) {
            String title = col.getText();
            if (title == null || title.isBlank()) {
                continue;
            }
            Text measure = new Text(title);
            measure.setFont(font);
            double needed = Math.ceil(measure.getLayoutBounds().getWidth()) + 44;   // padding + sort arrow
            if (col.getMinWidth() < needed) {
                col.setMinWidth(needed);
            }
        }
    }
}