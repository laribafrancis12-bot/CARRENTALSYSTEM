package com.carrentalsystem.carsystem;

import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;

import java.time.LocalDate;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Input checks for the forms.
 *
 * Every message is written here in code (never taken from the database) and is shown
 * right under the field that has the problem, with a red border on that field.
 *
 * Each check returns null when the value is fine, or the message to show.
 * "complete" is false while the person is still typing (only obvious mistakes are reported)
 * and true when they leave the field or press the save button (everything is checked).
 */
public final class FormValidation {

    private static final Pattern NAME_CHARS = Pattern.compile("[\\p{L} ]*");
    private static final Pattern ADDRESS_CHARS = Pattern.compile("[\\p{L}0-9 ,.#-]*");
    private static final Pattern ID_NUMBER_CHARS = Pattern.compile("[A-Za-z0-9-]*");
    private static final Pattern DIGITS = Pattern.compile("\\d*");
    private static final Pattern LICENSE_CHARS = Pattern.compile("[A-Za-z0-9-]*");
    private static final Pattern LICENSE = Pattern.compile("[A-Za-z]\\d{2}-\\d{2}-\\d{6}");
    private static final Pattern PLATE_CHARS = Pattern.compile("[A-Za-z0-9 ]*");
    private static final Pattern PLATE = Pattern.compile("[A-Za-z]{3} ?\\d{4}");
    private static final Pattern MODEL_CHARS = Pattern.compile("[\\p{L}0-9 .\\-/'+]*");
    private static final Pattern USERNAME_CHARS = Pattern.compile("[A-Za-z0-9._]*");
    private static final Pattern RATE_TYPING = Pattern.compile("\\d*\\.?\\d{0,2}");
    private static final Pattern RATE = Pattern.compile("\\d+(\\.\\d{1,2})?");

    private FormValidation() {}

    // ------------------------------------------------------------ showing messages

    /**
     * Shows the message under the field (and marks the field red), or clears it when message is null.
     *
     * @return true when there is no problem
     */
    public static boolean show(Control field, Label errorLabel, String message) {
        boolean ok = message == null;
        errorLabel.setText(ok ? "" : message);
        errorLabel.setVisible(!ok);
        errorLabel.setManaged(!ok);
        if (ok) {
            field.getStyleClass().remove("field-invalid");
        } else if (!field.getStyleClass().contains("field-invalid")) {
            field.getStyleClass().add("field-invalid");
        }
        return ok;
    }

    public static void clear(Control field, Label errorLabel) {
        show(field, errorLabel, null);
    }

    /** Live feedback: obvious mistakes while typing, the full check when the person leaves the field. */
    public static void live(TextInputControl field, Label errorLabel, Function<Boolean, String> check) {
        field.textProperty().addListener((o, a, b) -> show(field, errorLabel, check.apply(false)));
        field.focusedProperty().addListener((o, was, now) -> {
            if (!now) {
                show(field, errorLabel, check.apply(true));
            }
        });
    }

    // ------------------------------------------------------------ blocking wrong characters

    /**
     * Characters that are not allowed simply cannot be typed or pasted. The message is shown
     * under the field so the person sees why nothing appeared.
     */
    private static void restrict(TextInputControl field, Label errorLabel, Pattern allowedText, String message) {
        field.setTextFormatter(new TextFormatter<String>(change -> {
            if (!change.isContentChange() || allowedText.matcher(change.getControlNewText()).matches()) {
                return change;
            }
            show(field, errorLabel, message);
            return null;
        }));
    }

    /** Name: letters and spaces only, 60 characters at most. */
    public static void restrictName(TextInputControl field, Label errorLabel) {
        restrict(field, errorLabel, Pattern.compile("[\\p{L} ]{0,60}"),
                "Name can only contain letters and spaces (60 characters at most).");
    }

    /** Address: letters, numbers, spaces and , . - # only, 150 characters at most. */
    public static void restrictAddress(TextInputControl field, Label errorLabel) {
        restrict(field, errorLabel, Pattern.compile("[\\p{L}0-9 ,.#-]{0,150}"),
                "Address can only contain letters, numbers, spaces and , . - # (150 characters at most).");
    }

    /**
     * Driver's license: fixed format N01-23-456789 (1 letter, 2 digits, 2 digits, 6 digits).
     * Letters are made capital and the hyphens are added automatically while typing.
     */
    public static void restrictLicense(TextInputControl field, Label errorLabel) {
        field.setTextFormatter(new TextFormatter<String>(change -> {
            if (!change.isContentChange()) {
                return change;
            }
            String raw = change.getControlNewText().replace("-", "").toUpperCase();
            if (!raw.matches("[A-Z]?\\d{0,10}")) {
                show(field, errorLabel, "License number must look like N01-23-456789: 1 letter, then numbers only.");
                return null;
            }
            StringBuilder formatted = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                if (i == 3 || i == 5) {
                    formatted.append('-');
                }
                formatted.append(raw.charAt(i));
            }
            change.setRange(0, change.getControlText().length());
            change.setText(formatted.toString());
            change.selectRange(formatted.length(), formatted.length());
            return change;
        }));
    }

    // ------------------------------------------------------------ customer / rental

    public static String personName(String value, boolean complete) {
        String v = trim(value);
        if (v.isEmpty()) return complete ? "Enter the customer's full name." : null;
        if (!NAME_CHARS.matcher(v).matches()) {
            return "Name can only contain letters and spaces.";
        }
        if (v.length() > 60) return "Name is too long (60 characters at most).";
        if (complete && v.length() < 2) return "Name is too short (at least 2 letters).";
        return null;
    }

    public static String phone(String value, boolean complete) {
        String v = trim(value);
        if (v.isEmpty()) return complete ? "Enter the contact number." : null;
        if (!DIGITS.matcher(v).matches()) {
            return "Contact number can only contain digits (no spaces, dashes or letters).";
        }
        if (v.length() >= 2 && !v.startsWith("09")) {
            return "Contact number must start with 09 (e.g. 09171234567).";
        }
        if (v.length() > 11) return "Contact number is too long. Use 11 digits (e.g. 09171234567).";
        if (complete && v.length() != 11) {
            return "Contact number must be 11 digits starting with 09 (e.g. 09171234567).";
        }
        return null;
    }

    public static String address(String value, boolean complete) {
        String v = trim(value);
        if (v.isEmpty()) return complete ? "Enter the customer's address." : null;
        if (!ADDRESS_CHARS.matcher(v).matches()) {
            return "Address can only contain letters, numbers, spaces and , . - #";
        }
        if (v.length() > 150) return "Address is too long (150 characters at most).";
        if (complete && v.length() < 5) return "Address is too short. Include the street and city.";
        return null;
    }

    public static String license(String value, boolean complete) {
        String v = trim(value);
        if (v.isEmpty()) return complete ? "Enter the driver's license number." : null;
        if (!LICENSE_CHARS.matcher(v).matches()) {
            return "License number can only contain letters, numbers and hyphens.";
        }
        if (complete && !LICENSE.matcher(v).matches()) {
            return "License number must look like N01-23-456789 (1 letter, 2 digits, 2 digits, 6 digits).";
        }
        return null;
    }

    public static String rentDate(LocalDate rent) {
        if (rent == null) return "Select the rent date.";
        if (rent.isBefore(LocalDate.now())) return "Rent date can't be in the past.";
        return null;
    }

    public static String returnDate(LocalDate rent, LocalDate ret) {
        if (ret == null) return "Select the return date.";
        if (rent != null && !ret.isAfter(rent)) return "Return date must be after the rent date.";
        return null;
    }

    public static String validIdNumber(String value, boolean complete) {
        String v = trim(value);
        if (v.isEmpty()) return complete ? "Enter the valid ID number." : null;
        if (!ID_NUMBER_CHARS.matcher(v).matches()) {
            return "ID number can only contain letters, numbers and hyphens.";
        }
        if (v.length() > 25) return "ID number is too long (25 characters at most).";
        if (complete && v.length() < 5) return "ID number is too short (at least 5 characters).";
        return null;
    }

    /** Valid ID number: letters, numbers and hyphens only, 25 characters at most. */
    public static void restrictIdNumber(TextInputControl field, Label errorLabel) {
        restrict(field, errorLabel, Pattern.compile("[A-Za-z0-9-]{0,25}"),
                "ID number can only contain letters, numbers and hyphens (25 characters at most).");
    }

    // ------------------------------------------------------------ vehicle

    public static String plate(String value, boolean complete) {
        String v = trim(value);
        if (v.isEmpty()) return complete ? "Enter the plate number." : null;
        if (!PLATE_CHARS.matcher(v).matches()) {
            return "Plate number can only contain letters, numbers and a space.";
        }
        if (complete && !PLATE.matcher(v).matches()) {
            return "Plate number must be 3 letters followed by 4 numbers (e.g. ABC 1234).";
        }
        return null;
    }

    public static String vehicleModel(String value, boolean complete) {
        String v = trim(value);
        if (v.isEmpty()) return complete ? "Enter the vehicle model." : null;
        if (!MODEL_CHARS.matcher(v).matches()) {
            return "Model can only contain letters, numbers, spaces and . - / ' +";
        }
        if (v.length() > 60) return "Model is too long (60 characters at most).";
        if (complete && v.length() < 2) return "Model is too short.";
        return null;
    }

    public static String rate(String value, boolean complete) {
        String v = trim(value);
        if (v.isEmpty()) return complete ? "Enter the daily rate." : null;
        if (!RATE_TYPING.matcher(v).matches()) {
            return "Rate must be a number like 1500 or 1500.50 (digits only, no \u20b1 or commas).";
        }
        if (complete) {
            if (!RATE.matcher(v).matches()) return "Rate must be a number like 1500 or 1500.50.";
            double amount = Double.parseDouble(v);
            if (amount <= 0) return "Rate must be greater than 0.";
            if (amount > 100000) return "Rate is too high. Check the amount.";
        }
        return null;
    }

    // ------------------------------------------------------------ accounts

    public static String username(String value, boolean complete) {
        String v = trim(value);
        if (v.isEmpty()) return complete ? "Enter a username." : null;
        if (!USERNAME_CHARS.matcher(v).matches()) {
            return "Username can only contain letters, numbers, dots and underscores.";
        }
        if (v.length() > 20) return "Username is too long (20 characters at most).";
        if (complete && v.length() < 3) return "Username must be at least 3 characters.";
        return null;
    }

    public static String password(String value, boolean complete) {
        String v = value == null ? "" : value;
        if (v.isEmpty()) return complete ? "Enter a password." : null;
        if (v.length() > 50) return "Password is too long (50 characters at most).";
        if (complete && v.length() < 6) return "Password must be at least 6 characters.";
        return null;
    }

    public static String confirmPassword(String password, String confirm, boolean complete) {
        String p = password == null ? "" : password;
        String c = confirm == null ? "" : confirm;
        if (c.isEmpty()) return complete ? "Re-enter the password to confirm it." : null;
        if (complete ? !c.equals(p) : !p.startsWith(c)) return "Passwords don't match.";
        return null;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}