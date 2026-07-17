package com.vidi.weather.dto;

import com.vidi.weather.model.DailyForecast;
import java.time.LocalDate;

public record DailyForecastEntry(LocalDate date, double temperatureMax, double temperatureMin, String description) {

    public static DailyForecastEntry from(DailyForecast forecast) {
        return new DailyForecastEntry(
                forecast.date(), forecast.temperatureMax(), forecast.temperatureMin(), forecast.description());
    }
}
