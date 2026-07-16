package com.vidi.weather.dto;

import com.vidi.weather.model.WeatherResult;
import java.time.Instant;

public record WeatherResponse(
        String city,
        String country,
        double temperature,
        double feelsLike,
        int humidity,
        double windSpeed,
        String description,
        String units,
        String provider,
        Instant observedAt,
        boolean fromCache
) {
    public static WeatherResponse from(WeatherResult result) {
        var data = result.data();
        return new WeatherResponse(
                data.city(),
                data.country(),
                data.temperature(),
                data.feelsLike(),
                data.humidity(),
                data.windSpeed(),
                data.description(),
                data.units().name().toLowerCase(),
                data.provider(),
                data.observedAt(),
                result.fromCache()
        );
    }
}
