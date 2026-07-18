package com.vidi.weather.dto;

import com.vidi.weather.model.MarineResult;

public record MarineConditionsResponse(
        String city,
        String country,
        String units,
        String provider,
        boolean fromCache,
        Double waterTemperature,
        Double waveHeightMeters,
        Double waveDirectionDegrees,
        Double wavePeriodSeconds
) {

    public static MarineConditionsResponse from(MarineResult result) {
        var data = result.data();
        return new MarineConditionsResponse(
                data.city(),
                data.country(),
                data.units().name().toLowerCase(),
                data.provider(),
                result.fromCache(),
                data.waterTemperature(),
                data.waveHeightMeters(),
                data.waveDirectionDegrees(),
                data.wavePeriodSeconds()
        );
    }
}
