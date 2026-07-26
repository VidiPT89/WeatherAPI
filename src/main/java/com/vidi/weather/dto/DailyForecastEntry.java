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
        int precipitationProbabilityMax,
        double windSpeedMax,
        Double waveHeightMax,
        Double wavePeriodMax,
        boolean rainLikely,
        String uvRiskLabel,
        String outdoorActivityLabel,
        String fishingConditionLabel,
        String surfConditionLabel
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
                forecast.precipitationProbabilityMax(),
                forecast.windSpeedMax(),
                forecast.waveHeightMax(),
                forecast.wavePeriodMax(),
                forecast.rainLikely(),
                forecast.uvRiskLabel(),
                forecast.outdoorActivityLabel(),
                forecast.fishingConditionLabel(),
                forecast.surfConditionLabel()
        );
    }
}
