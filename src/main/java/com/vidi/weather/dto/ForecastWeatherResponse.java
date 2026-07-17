package com.vidi.weather.dto;

import com.vidi.weather.model.ForecastResult;
import java.util.List;

public record ForecastWeatherResponse(
        String city,
        String country,
        String units,
        String provider,
        boolean fromCache,
        List<HourlyForecastEntry> hourly,
        List<DailyForecastEntry> daily
) {

    public static ForecastWeatherResponse from(ForecastResult result) {
        var data = result.data();
        return new ForecastWeatherResponse(
                data.city(),
                data.country(),
                data.units().name().toLowerCase(),
                data.provider(),
                result.fromCache(),
                data.hourly().stream().map(HourlyForecastEntry::from).toList(),
                data.daily().stream().map(DailyForecastEntry::from).toList()
        );
    }
}
