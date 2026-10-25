package com.carrentalsystem.carsystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

/**
 * Keeps the photos of customers' valid IDs.
 *
 * The photos are copied into a "valid_ids" folder next to the app (the folder the app runs from),
 * and only the file name is saved in the database (customers.id_photo).
 *
 * Each file is named after the renter and the date, for example Maria_Santos_2026-10-18.jpg,
 * so it is easy to find in the folder.
 */
public final class IdPhotoStorage {

    public static final Path DIR = Paths.get("valid_ids");

    private IdPhotoStorage() {}

    /**
     * Copies the chosen photo into the valid_ids folder, named after the renter and today's date
     * (Maria_Santos_2026-10-18.jpg; a second photo the same day becomes ..._2.jpg),
     * and returns the file name to save in the database.
     */
    public static String save(File source, String renterName) throws IOException {
        Files.createDirectories(DIR);
        String ext = source.getName().toLowerCase().endsWith(".png") ? ".png" : ".jpg";
        String base = cleanName(renterName) + "_" + LocalDate.now();

        String stored = base + ext;
        int copy = 2;
        while (Files.exists(DIR.resolve(stored))) {
            stored = base + "_" + copy + ext;
            copy++;
        }
        Files.copy(source.toPath(), DIR.resolve(stored));
        return stored;
    }

    /** Makes the renter's name safe for a file name: letters and numbers, words joined by "_". */
    private static String cleanName(String name) {
        String cleaned = name == null ? "" : name.trim().replaceAll("[^\\p{L}\\p{N}]+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "customer";
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
