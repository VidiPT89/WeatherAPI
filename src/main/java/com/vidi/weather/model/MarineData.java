package com.vidi.weather.model;

public record MarineData(
        String city,
        String country,
        Units units,
        String provider,
        Double waterTemperature,
        Double waveHeightMeters,
        Double waveDirectionDegrees,
        Double wavePeriodSeconds
) {
}
