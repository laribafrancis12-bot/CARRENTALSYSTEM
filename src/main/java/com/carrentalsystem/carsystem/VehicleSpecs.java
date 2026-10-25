package com.carrentalsystem.carsystem;

import java.util.List;
import java.util.Map;

/** Body types and how many seats each can realistically have (a sedan has no 15 seats). */
public final class VehicleSpecs {

    public static final List<String> TYPES = List.of(
            "Sedan", "Hatchback", "SUV", "Crossover", "MPV", "Pickup", "Van", "Coupe", "Convertible", "Wagon");

    private static final Map<String, List<Integer>> SEATS = Map.of(
            "Sedan", List.of(4, 5),
            "Hatchback", List.of(4, 5),
            "Coupe", List.of(2, 4),
            "Convertible", List.of(2, 4),
            "Wagon", List.of(5),
            "Crossover", List.of(5, 7),
            "SUV", List.of(5, 7, 8),
            "MPV", List.of(6, 7, 8),
            "Pickup", List.of(2, 4, 5),
            "Van", List.of(10, 12, 15, 18));

    private VehicleSpecs() {}

    /** The seat counts allowed for a body type (empty if the type is unknown). */
    public static List<Integer> seatsFor(String type) {
        return type == null ? List.of() : SEATS.getOrDefault(type, List.of());
    }
}
