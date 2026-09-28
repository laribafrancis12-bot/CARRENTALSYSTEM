module com.carrentalsystem.carsystem {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.carrentalsystem.carsystem to javafx.fxml;
    exports com.carrentalsystem.carsystem;
}