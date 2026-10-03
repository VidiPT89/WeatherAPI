package com.vidi.weather.model;

import java.util.List;

public record ForecastData(
        String city,
        String country,
        Units units,
        String provider,
        List<HourlyForecast> hourly,
        List<DailyForecast> daily,
        // The city's offset from UTC, so clients can compare an absolute instant (e.g. a
        // weather observation) with the local, offset-less hourly/daily/sunrise/sunset times.
        int utcOffsetSeconds
) {
}
