package com.vidi.weather.model;

import java.time.Instant;

public record WeatherData(
        String city,
        String country,
        double temperature,
        double feelsLike,
        int humidity,
        double windSpeed,
        String description,
        Units units,
        String provider,
        Instant observedAt
) {
}
