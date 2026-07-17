package com.vidi.weather.model;

import java.time.LocalDate;

public record DailyForecast(LocalDate date, double temperatureMax, double temperatureMin, String description) {
}
