package com.carrentalsystem.carsystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Keeps the photos of customers' valid IDs.
 *
 * The photos are copied into a "valid_ids" folder next to the app (the folder the app runs from),
 * and only the file name is saved in the database (customers.id_photo).
 *
 **/

public final class IdPhotoStorage {

    public static final Path DIR = Paths.get("valid_ids");

    private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyyMMdd_HHmmss");

    private IdPhotoStorage() {}

    /**
     * Copies the chosen photo into the valid_ids folder, named after the renter, and returns the
     * file name to save in the database.
     */
    public static String save(File source, String renterName) throws IOException {
        Files.createDirectories(DIR);
        String ext = source.getName().toLowerCase().endsWith(".png") ? ".png" : ".jpg";
        String safeName = sanitizeName(renterName);
        String stamp;
        synchronized (STAMP) {
            stamp = STAMP.format(new Date());
        }

        String stored = safeName + "_" + stamp + ext;
        Path target = DIR.resolve(stored);

        // Extremely unlikely, but guard against two rentals for the same name in the same second.
        int suffix = 2;
        while (Files.exists(target)) {
            stored = safeName + "_" + stamp + "_" + suffix + ext;
            target = DIR.resolve(stored);
            suffix++;
        }

        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return stored;
    }

    /** Keeps only letters, numbers and spaces from the name, then turns spaces into underscores. */
    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        String cleaned = name.trim()
                .replaceAll("[^\\p{L}\\p{N} ]", "")
                .replaceAll("\\s+", "_");
        if (cleaned.isEmpty()) {
            return "unknown";
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    /** The saved photo for a stored file name, or null when there is none. */
    public static File file(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return null;
        }
        return DIR.resolve(storedName).toFile();
    }

    /** Removes a saved photo (used when the rental could not be saved after all). */
    public static void delete(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(DIR.resolve(storedName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}