package com.carrentalsystem.carsystem;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static final String URL = "jdbc:mysql://localhost:3306/car_rental_db";
    private static final String USER = "root";
    private static final String PASSWORD = ""; // XAMPP's default MySQL has no password

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Runs once at startup: adds missing columns and widens ENUM columns so new values are accepted. */
    public static void ensureSchema() {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            boolean exists;
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, "rentals", "created_by")) {
                exists = rs.next();
            }
            if (!exists) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("ALTER TABLE rentals ADD COLUMN created_by VARCHAR(50) NULL");
                    System.out.println("Added missing column rentals.created_by");
                }
            }

            // If these columns were created as ENUM, values like "Overdue" or "SUV" would be rejected.
            widenIfEnum(conn, meta, "vehicles", "status", "VARCHAR(20) DEFAULT 'Available'");
            widenIfEnum(conn, meta, "vehicles", "type", "VARCHAR(30)");
            widenIfEnum(conn, meta, "rentals", "status", "VARCHAR(20) DEFAULT 'Active'");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void widenIfEnum(Connection conn, DatabaseMetaData meta,
                                    String table, String column, String definition) throws SQLException {
        boolean isEnum;
        try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, table, column)) {
            isEnum = rs.next() && "ENUM".equalsIgnoreCase(rs.getString("TYPE_NAME"));
        }
        if (isEnum) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " MODIFY " + column + " " + definition);
                System.out.println("Changed " + table + "." + column + " from ENUM to text");
            }
        }
    }

    // ------------------------------------------------------------ rental / vehicle status

    /**
     * Rentals whose return date has passed (and were not returned) become Overdue,
     * and their vehicles are shown as Overdue too. Safe to call as often as needed.
     */
    public static void refreshOverdue() {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                run(conn, "UPDATE rentals SET status='Overdue' WHERE status='Active' AND return_date < CURDATE()");
                run(conn, "UPDATE vehicles SET status='Overdue' WHERE status='Rented' "
                        + "AND vehicle_id IN (SELECT vehicle_id FROM rentals WHERE status='Overdue')");
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * The customer brought the car back (on time, early or late): the rental becomes Returned
     * and the vehicle becomes Available again. The amount charged is not changed.
     *
     * @return false if the rental was not found or was already returned
     */
    public static boolean markReturned(int rentalId) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int vehicleId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT vehicle_id FROM rentals WHERE rental_id = ? "
                                + "AND (status IS NULL OR status <> 'Returned') FOR UPDATE")) {
                    ps.setInt(1, rentalId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        vehicleId = rs.getInt(1);
                    }
                }
                run(conn, "UPDATE rentals SET status='Returned' WHERE rental_id = ?", rentalId);
                // Only free it if it is still out; a car sent to Maintenance stays in Maintenance.
                run(conn, "UPDATE vehicles SET status='Available' WHERE vehicle_id = ? "
                        + "AND status IN ('Rented','Overdue')", vehicleId);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Changes a vehicle's status from the Vehicles screen.
     * - Available: any open rental for this vehicle is closed as Returned.
     * - Overdue: any Active rental for this vehicle is flagged Overdue.
     */
    public static void setVehicleStatus(int vehicleId, String newStatus) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                if ("Available".equals(newStatus)) {
                    run(conn, "UPDATE rentals SET status='Returned' "
                            + "WHERE vehicle_id = ? AND status IN ('Active','Overdue')", vehicleId);
                } else if ("Overdue".equals(newStatus)) {
                    run(conn, "UPDATE rentals SET status='Overdue' "
                            + "WHERE vehicle_id = ? AND status = 'Active'", vehicleId);
                }
                run(conn, "UPDATE vehicles SET status = ? WHERE vehicle_id = ?", newStatus, vehicleId);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static void run(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }
}