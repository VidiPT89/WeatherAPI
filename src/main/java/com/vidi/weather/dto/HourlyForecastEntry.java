package com.vidi.weather.dto;

import com.vidi.weather.model.HourlyForecast;
import java.time.LocalDateTime;

public record HourlyForecastEntry(
        LocalDateTime time,
        double temperature,
        String description,
        int precipitationProbability
) {

    public static HourlyForecastEntry from(HourlyForecast forecast) {
        return new HourlyForecastEntry(
                forecast.time(),
                forecast.temperature(),
                forecast.description(),
                forecast.precipitationProbability()
        );
    }
}
