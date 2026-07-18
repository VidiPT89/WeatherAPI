package com.vidi.weather.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyForecast(
        LocalDate date,
        double temperatureMax,
        double temperatureMin,
        String description,
        LocalDateTime sunrise,
        LocalDateTime sunset,
        double uvIndexMax,
        int precipitationProbabilityMax
) {
}
