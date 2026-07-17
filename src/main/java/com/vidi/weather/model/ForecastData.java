package com.vidi.weather.model;

import java.util.List;

public record ForecastData(
        String city,
        String country,
        Units units,
        String provider,
        List<HourlyForecast> hourly,
        List<DailyForecast> daily
) {
}
