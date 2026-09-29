module com.carrentalsystem.carsystem {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens com.carrentalsystem.carsystem to javafx.fxml;
    exports com.carrentalsystem.carsystem;
}