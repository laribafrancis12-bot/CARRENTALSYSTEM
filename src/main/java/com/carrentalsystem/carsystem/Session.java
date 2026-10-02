package com.carrentalsystem.carsystem;

/**
 * Holds who is currently logged in for the lifetime of the app.
 * Every controller can check Session.role to decide what to show.
 */
public class Session {
    public static String username;
    public static String role; // "admin" or "staff"

    public static boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }

    public static void clear() {
        username = null;
        role = null;
    }
}