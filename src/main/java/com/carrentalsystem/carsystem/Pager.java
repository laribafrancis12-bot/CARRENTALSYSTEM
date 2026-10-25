package com.carrentalsystem.carsystem;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a long list in a table a few rows at a time, with arrows to go to the next or
 * previous page (like a website). Used by the Rentals and History screens.
 */
public final class Pager<T> {

    private final TableView<T> table;
    private final Button previousBtn;
    private final Button nextBtn;
    private final Label pageLabel;
    private final Label infoLabel;
    private final int pageSize;

    private List<T> items = new ArrayList<>();
    private int page = 0;

    public Pager(TableView<T> table, Button previousBtn, Button nextBtn, Label pageLabel, Label infoLabel, int pageSize) {
        this.table = table;
        this.previousBtn = previousBtn;
        this.nextBtn = nextBtn;
        this.pageLabel = pageLabel;
        this.infoLabel = infoLabel;
        this.pageSize = pageSize;
        previousBtn.setOnAction(e -> goTo(page - 1));
        nextBtn.setOnAction(e -> goTo(page + 1));
    }

    /** New list (after a search or filter): starts again on page 1. */
    public void setItems(List<T> all) {
        items = new ArrayList<>(all);
        page = 0;
        show();
    }

    /** Refreshed list (after an action such as Return): stays on the same page if it still exists. */
    public void refreshItems(List<T> all) {
        items = new ArrayList<>(all);
        show();
    }

    private int pageCount() {
        return Math.max(1, (int) Math.ceil(items.size() / (double) pageSize));
    }

    private void goTo(int newPage) {
        page = newPage;
        show();
    }

    private void show() {
        int pages = pageCount();
        page = Math.max(0, Math.min(page, pages - 1));

        int from = page * pageSize;
        int to = Math.min(from + pageSize, items.size());
        table.setItems(FXCollections.observableArrayList(items.subList(from, to)));

        pageLabel.setText("Page " + (page + 1) + " of " + pages);
        infoLabel.setText(items.isEmpty() ? "No records" : "Showing " + (from + 1) + "-" + to + " of " + items.size());
        previousBtn.setDisable(page == 0);
        nextBtn.setDisable(page >= pages - 1);
    }
}
