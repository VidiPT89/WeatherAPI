package com.vidi.weather.model;

import java.time.LocalDateTime;

/** One hour of the coast outlook in metric units, with each activity's score for that hour. */
public record CoastHour(
        LocalDateTime time,
        Double waveHeightMeters,
        Double wavePeriodSeconds,
        Double waveDirectionDegrees,
        Double swellHeightMeters,
        Double swellPeriodSeconds,
        Double swellDirectionDegrees,
        Double windSpeedKmh,
        Double windGustsKmh,
        Double windDirectionDegrees,
        String windExposure,
        Double seaLevelMeters,
        Double waterTemperatureCelsius,
        Double airTemperatureCelsius,
        Integer precipitationProbability,
        Integer surfScore,
        Integer fishingScore,
        Integer beachScore
) {
}
