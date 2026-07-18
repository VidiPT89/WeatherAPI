package com.vidi.weather.dto;

import com.vidi.weather.model.DailyForecast;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyForecastEntry(
        LocalDate date,
        double temperatureMax,
        double temperatureMin,
        String description,
        LocalDateTime sunrise,
        LocalDateTime sunset,
        double uvIndexMax,
        int precipitationProbabilityMax
) {

    public static DailyForecastEntry from(DailyForecast forecast) {
        return new DailyForecastEntry(
                forecast.date(),
                forecast.temperatureMax(),
                forecast.temperatureMin(),
                forecast.description(),
                forecast.sunrise(),
                forecast.sunset(),
                forecast.uvIndexMax(),
                forecast.precipitationProbabilityMax()
        );
    }
}
