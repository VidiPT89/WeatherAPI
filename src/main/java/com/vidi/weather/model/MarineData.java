package com.vidi.weather.model;

import java.util.List;

public record MarineData(
        String city,
        String country,
        Units units,
        String provider,
        Double waterTemperature,
        Double waveHeightMeters,
        Double waveDirectionDegrees,
        Double wavePeriodSeconds,
        List<TideEvent> tideEvents
) {
}
