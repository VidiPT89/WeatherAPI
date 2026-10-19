package com.vidi.weather.model;

/**
 * One hour of sea and wind readings at a spot, always in metric units (meters, seconds, km/h,
 * Celsius), which is what the activity scorers work in. Any reading can be {@code null} where
 * Open-Meteo has no value for that hour.
 */
public record CoastConditions(
        Double waveHeightMeters,
        Double wavePeriodSeconds,
        Double swellHeightMeters,
        Double swellPeriodSeconds,
        Double swellDirectionDegrees,
        Double windSpeedKmh,
        Double windDirectionDegrees,
        Double airTemperatureCelsius,
        Integer precipitationProbability,
        Double waterTemperatureCelsius,
        Integer facingDegrees,
        boolean nearTideTurn,
        boolean nearSunriseOrSunset
) {
}
