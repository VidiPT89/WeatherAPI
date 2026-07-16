package com.vidi.weather.util;

import com.vidi.weather.model.Units;

/**
 * Converts raw Kelvin/meters-per-second readings (OpenWeatherMap's default unit system)
 * into the Celsius/Fahrenheit and km/h/mph values used across the rest of the API.
 */
public final class UnitConverter {

    private static final double KELVIN_TO_CELSIUS_OFFSET = 273.15;
    private static final double MPS_TO_KMH_FACTOR = 3.6;
    private static final double MPS_TO_MPH_FACTOR = 2.23694;

    private UnitConverter() {
    }

    public static double kelvinToRequestedTemperature(double kelvin, Units units) {
        double celsius = kelvin - KELVIN_TO_CELSIUS_OFFSET;
        return units == Units.IMPERIAL ? celsiusToFahrenheit(celsius) : celsius;
    }

    public static double metersPerSecondToRequestedSpeed(double metersPerSecond, Units units) {
        return units == Units.IMPERIAL
                ? metersPerSecond * MPS_TO_MPH_FACTOR
                : metersPerSecond * MPS_TO_KMH_FACTOR;
    }

    private static double celsiusToFahrenheit(double celsius) {
        return celsius * 9.0 / 5.0 + 32.0;
    }
}
