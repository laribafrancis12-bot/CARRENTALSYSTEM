package com.carrentalsystem.carsystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Keeps the photos of customers' valid IDs.
 *
 * The photos are copied into a "valid_ids" folder next to the app (the folder the app runs from),
 * and only the file name is saved in the database (customers.id_photo).
 */
public final class IdPhotoStorage {

    public static final Path DIR = Paths.get("valid_ids");

    private IdPhotoStorage() {}

    /** Copies the chosen photo into the valid_ids folder and returns the file name to save in the database. */
    public static String save(File source) throws IOException {
        Files.createDirectories(DIR);
        String ext = source.getName().toLowerCase().endsWith(".png") ? ".png" : ".jpg";
        String stored = "id_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Files.copy(source.toPath(), DIR.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        return stored;
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