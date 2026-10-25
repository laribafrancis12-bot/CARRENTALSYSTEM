package com.carrentalsystem.carsystem;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;

/**
 * An eye button inside a password box: click it to see the password, click again to hide it.
 *
 * The screen has a PasswordField and a normal TextField of the same size on top of each other in a
 * StackPane (the text of the two is kept equal by the screen's own code). This adds the eye at the
 * right end of that StackPane and swaps which of the two boxes is visible.
 */
public final class PasswordEye {

    private static final Color INK = Color.web("#4A6080");

    private PasswordEye() {}

    public static void attach(PasswordField hidden, TextField shown) {
        if (!(hidden.getParent() instanceof StackPane)) {
            return;   // the screen layout is different: leave the boxes as they are
        }
        StackPane holder = (StackPane) hidden.getParent();

        // leave room at the right so the text never runs under the eye
        hidden.getStyleClass().add("has-eye");
        shown.getStyleClass().add("has-eye");

        Button eye = new Button();
        eye.getStyleClass().add("eye-btn");
        eye.setFocusTraversable(false);
        eye.setGraphic(icon(false));
        StackPane.setAlignment(eye, Pos.CENTER_RIGHT);
        StackPane.setMargin(eye, new Insets(0, 8, 0, 0));
        holder.getChildren().add(eye);

        // The icon follows whichever box is visible, so screens that reset the boxes
        // (for example when a popup opens) get the right icon without extra code.
        shown.visibleProperty().addListener((o, was, now) -> eye.setGraphic(icon(now)));

        eye.setOnAction(e -> {
            boolean show = !shown.isVisible();
            hidden.setVisible(!show);
            hidden.setManaged(!show);
            shown.setVisible(show);
            shown.setManaged(show);
            (show ? shown : hidden).requestFocus();
        });
    }

    /** An open eye (click to show) or the same eye crossed out (the password is showing, click to hide). */
    private static Node icon(boolean passwordShowing) {
        Ellipse outline = new Ellipse(10, 6.5);
        outline.setFill(Color.TRANSPARENT);
        outline.setStroke(INK);
        outline.setStrokeWidth(1.8);

        Circle pupil = new Circle(3);
        pupil.setFill(INK);

        Group eye = new Group(outline, pupil);
        if (passwordShowing) {
            Line slash = new Line(-9, 9, 9, -9);
            slash.setStroke(INK);
            slash.setStrokeWidth(1.8);
            eye.getChildren().add(slash);
        }
        return eye;
    }
}